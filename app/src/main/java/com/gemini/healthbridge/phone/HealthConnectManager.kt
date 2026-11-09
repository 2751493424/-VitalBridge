package com.gemini.healthbridge.phone

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HeartRateVariabilityRmssdRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.records.LeanBodyMassRecord
import androidx.health.connect.client.records.MenstruationPeriodRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Length
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import androidx.health.connect.client.units.Power
import androidx.health.connect.client.units.Pressure
import com.gemini.healthbridge.core.Backfill
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.Text
import java.time.Instant
import kotlin.reflect.KClass

/** Metadata 工厂（该类经 import 引用在 K2 下异常，改用全限定名）。 */
private fun newMetadata(id: String? = null) =
    if (id == null) {
        androidx.health.connect.client.records.metadata.Metadata.manualEntry()
    } else {
        androidx.health.connect.client.records.metadata.Metadata.manualEntryWithId(id)
    }

/**
 * 回填里某一类的结果。
 *
 * 三个「跳过」计数**必须一路带到界面**：回填最坏的形态不是报错，而是
 * 「点了按钮、显示写了 12 条」，然后用户问「我明明有 300 条」。
 *
 * @param notOurs 因为来源不是我们自己产生的而跳过（三星读来的、备份导入的）
 * @param outOfRange 因为值越界而跳过（越界说明读数本身坏了，不该搬进别人的库）
 * @param truncated 因为超过 `Backfill.MAX_PER_KIND` 而没写（保留的是**最近**的）
 * @param failed 平台侧拒收的条数（值域被 SDK 校验拦下、或权限/服务异常）
 * @param noPermission 因为**这一类压根没有写入权限**而没写的条数
 *   （与 [failed] 分开：没权限是"要用户去开"，被拒是"数据本身有问题"，两件事）
 */
data class HcKindResult(
    val kind: String,
    /** 类型名。**待求值**文案 —— 报告会存进 `remember`，存已求值的串会切语言后停在旧语言。 */
    val label: ResText,
    val written: Int,
    val notOurs: Int,
    val outOfRange: Int,
    val truncated: Int,
    val failed: Int,
    val noPermission: Int = 0,
)

/** 一次回填的汇总。 */
data class HcBackfillReport(
    val kinds: List<HcKindResult>,
    /** 见 [HealthConnectManager.lastError]：**待求值**文案。 */
    val error: ResText?,
) {
    val written: Int get() = kinds.sumOf { it.written }
    val failed: Int get() = kinds.sumOf { it.failed }
    val skipped: Int get() = kinds.sumOf { it.notOurs + it.outOfRange }
    val truncated: Int get() = kinds.sumOf { it.truncated }
}

/**
 * Health Connect（安卓官方健康数据中枢）：
 * 写入对所有应用开放（无三星那种白名单限制），
 * 三星健康可通过其 Health Connect 同步设置读取这些数据。
 *
 * ## ⚠⚠ 只写「我们自己产生的」数据
 *
 * 三星健康**也往 Health Connect 写**。把从三星读来的记录再写进 HC，
 * 结果是**重复计数 + 来源冒充**：用户看到翻倍的数据，而任何一方都不报错。
 * 判据在 `core/Backfill.isOurs`（那里有完整的理由与代价说明），本类**不自己判断**。
 *
 * ## 幂等
 *
 * 回填可以重复触发（用户点两次、同步后再点一次），所以每条记录都带一个稳定的
 * `clientRecordId`（`Backfill.clientId`），并且**写入前先查一遍**自己在这个时间窗内
 * 已经写过什么。查重按 `dataOrigin` 过滤，所以只看得到本 App 写的记录 ——
 * 三星写的那些不会被误当成"我已经写过了"。
 *
 * ## 为什么回填是「先算计划、再照单写」
 *
 * 过滤来源、校验值域、换算单位、截断上限这四件事全在 `Backfill.plan`（纯函数、
 * 有离线台架）。本类只负责「把计划里的采样变成平台记录」——**一个 if 都没有**。
 * 这样界面上那个「写了多少 / 跳过多少」的数字才与台架钉住的逻辑是同一份。
 */
object HealthConnectManager {

    @Volatile
    /**
     * 最后一次错误。
     *
     * ⚠ 是**待求值**文案：它既可能是我们自己写的本地化说明（`ResText.of`），
     * 也可能是平台异常原文（`ResText.raw`）。混成一个 `String` 会让本地化那条
     * 在切语言后停在旧语言 —— 而它会被存进回填报告、一直显示在界面上。
     */
    var lastError: ResText? = null
        private set

    /**
     * 写入权限。
     *
     * ⚠ 从 3 项扩到 9 项（2026-09-24）。HC 的授权是**一个页面**（不是一叠系统对话框），
     * 所以一次全请求是可接受的；但界面上要说明"这几项分别是什么"，
     * 否则用户在一个列表里盲点。
     *
     * 刻意**不含**：
     * · `BodyTemperatureRecord` —— 三星那个字段的语义是**核心体温**，
     *   而手表给的是腕部皮温（低 1~3℃），写进去会污染对方的核心体温序列；
     * · `SkinTemperatureRecord` —— HC 的语义是「基线 + 一串偏差」，
     *   我们给的是**单次绝对温度**，把绝对值填进基线字段等于替别人下了一个它没下过的结论；
     * · `BodyWaterMassRecord` —— BIA 给的体水是**升**，HC 要**质量**，
     *   升→公斤是近似换算，而回填的意义是让别处用上真实数据、不是冒充测量值。
     * 完整清单与理由见 `Backfill.NOT_WRITTEN`（界面直接引用它显示）。
     */
    val writePermissions = setOf(
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getWritePermission(HeightRecord::class),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
    )

    /**
     * 写入权限 → 中文名。
     *
     * 界面用它回答「**还缺哪几项**」。只说一句"未授权"是不够的 ——
     * 用户可能已经点过授权页、也勾了，但因为 HC 的版本或某项不被支持而仍然拿不到，
     * 这时他需要知道缺的是哪一个才可能自己解决。
     */
    val writePermissionLabels: Map<String, String> get() = linkedMapOf(
        HealthPermission.getWritePermission(HeartRateRecord::class) to Text.of("s_00317450"),
        HealthPermission.getWritePermission(BloodPressureRecord::class) to Text.of("s_00460c4b"),
        HealthPermission.getWritePermission(OxygenSaturationRecord::class) to Text.of("s_52996d20"),
        HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class) to Text.of("s_86b2f535"),
        HealthPermission.getWritePermission(BodyFatRecord::class) to Text.of("s_1507fa78"),
        HealthPermission.getWritePermission(LeanBodyMassRecord::class) to Text.of("s_5a292991"),
        HealthPermission.getWritePermission(WeightRecord::class) to Text.of("s_00292946"),
        HealthPermission.getWritePermission(HeightRecord::class) to Text.of("s_00499c59"),
        HealthPermission.getWritePermission(BasalMetabolicRateRecord::class) to Text.of("s_7f3df8c2"),
    )

    /**
     * 清单里**漏声明**的写入权限。
     *
     * ⚠⚠ 这是本项目最容易静默失效的一类：**HC 只显示应用在 AndroidManifest 里
     * 声明过的权限**。代码里 `requestPermissions(...)` 传了 9 项、清单只声明 3 项时：
     * · 编译通过、运行不报错；
     * · HC 的「应用访问权限」页里**只出现那 3 项**；
     * · `launch` 后页面一闪而过或压根不弹（因为"没有需要请求的"）——
     *   用户看到的是"点授权没反应"+"手动也找不到"。
     * 2026-09-24 正是这个：写入从 3 项扩到 9 项时只改了代码、没改清单。
     *
     * ⚠ 这是 **App 自己的 bug**：用户无论怎么点、去哪个设置页翻，都解决不了。
     * 所以界面必须把它**显眼地报出来**，而不是与"没授权"混在一起显示。
     *
     * @return 缺的权限集合；空集 = 清单齐了。
     */
    @Suppress("DEPRECATION")
    fun missingManifestPermissions(context: Context): Set<String> = runCatching {
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            android.content.pm.PackageManager.GET_PERMISSIONS,
        )
        val declared = info.requestedPermissions?.toSet().orEmpty()
        writePermissions - declared
    }.getOrDefault(emptySet())

    /**
     * **实际持有**的写入权限（哪几项真的能写）。
     *
     * ⚠ 这是回填该用的判据，不是 [hasWritePermission] —— 见那个函数的说明。
     */
    suspend fun grantedWritePermissions(context: Context): Set<String> {
        if (!isAvailable(context)) return emptySet()
        return runCatching {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            writePermissions.filter { it in granted }.toSet()
        }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            emptySet()
        }
    }

    /**
     * 已授予的里面**缺哪几项**（空集 = 全都有）。
     *
     * ⚠ 与 [hasWritePermission] 的分工：那个只回答"能不能写"，这个回答"缺的是哪几个"。
     * 两者都要有 —— 前者用来决定要不要拦，后者用来告诉用户下一步做什么。
     *
     * 取不到授权列表时**返回全部**（当作都没授权），方向是保守的：
     * 不会把"查不到"误报成"已授权"。
     */
    suspend fun missingWritePermissions(context: Context): Set<String> {
        if (!isAvailable(context)) return writePermissions
        return runCatching {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            writePermissions - granted
        }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            writePermissions
        }
    }

    fun isAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    /**
     * 是否**全部 9 项**写入权限都拿到了。
     *
     * ⚠ 用 `containsAll` 是有意的 —— 但**只用于界面显示"是否齐全"**，
     * `backfill` **不能**用它当门禁。原因：某些 record 类型是较新的 HC 版本才支持的
     * （`HeartRateVariabilityRmssdRecord` 等）。如果设备上的 HC 版本不认识其中一项，
     * 用户**永远拿不到那一项** —— 那么"要求全部"就等于"回填永远不能执行"，
     * 而且界面上只会显示一句"未授权"，用户完全无从下手。
     *
     * 所以回填走 [grantedWritePermissions] **逐类判断**：有权限的类照写，
     * 没权限的类按 [HcKindResult.noPermission] 报出来。
     * 这与本项目「能拿到多少做多少，并把少了什么说出来」是同一条纪律。
     */
    suspend fun hasWritePermission(context: Context): Boolean {
        if (!isAvailable(context)) {
            lastError = ResText.of(R.string.s_c782d49c)
            return false
        }
        return runCatching {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            granted.containsAll(writePermissions)
        }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    // ───────────────────────── 记录构造（一处定义，单条写入与回填共用） ─────────────────────────

    private fun hrRecord(atMs: Long, bpm: Long, id: String): HeartRateRecord =
        HeartRateRecord(
            startTime = Instant.ofEpochMilli(atMs).minusSeconds(30),
            startZoneOffset = null,
            endTime = Instant.ofEpochMilli(atMs),
            endZoneOffset = null,
            samples = listOf(
                HeartRateRecord.Sample(
                    time = Instant.ofEpochMilli(atMs).minusSeconds(1),
                    beatsPerMinute = bpm,
                )
            ),
            metadata = newMetadata(id),
        )

    private fun spo2Record(atMs: Long, percent: Double, id: String): OxygenSaturationRecord =
        OxygenSaturationRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            percentage = Percentage(percent),
            metadata = newMetadata(id),
        )

    private fun hrvRecord(atMs: Long, rmssdMs: Double, id: String): HeartRateVariabilityRmssdRecord =
        HeartRateVariabilityRmssdRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            heartRateVariabilityMillis = rmssdMs,
            metadata = newMetadata(id),
        )

    private fun bodyFatRecord(atMs: Long, percent: Double, id: String): BodyFatRecord =
        BodyFatRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            percentage = Percentage(percent),
            metadata = newMetadata(id),
        )

    private fun leanMassRecord(atMs: Long, kg: Double, id: String): LeanBodyMassRecord =
        LeanBodyMassRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            mass = Mass.kilograms(kg),
            metadata = newMetadata(id),
        )

    private fun weightRecord(atMs: Long, kg: Double, id: String): WeightRecord =
        WeightRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            weight = Mass.kilograms(kg),
            metadata = newMetadata(id),
        )

    /** ⚠ 入参 [meters] 必须是**米** —— 换算在 `Backfill.plan` 里做，这里不再除。 */
    private fun heightRecord(atMs: Long, meters: Double, id: String): HeightRecord =
        HeightRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            height = Length.meters(meters),
            metadata = newMetadata(id),
        )

    private fun bmrRecord(atMs: Long, kcalPerDay: Double, id: String): BasalMetabolicRateRecord =
        BasalMetabolicRateRecord(
            time = Instant.ofEpochMilli(atMs),
            zoneOffset = null,
            basalMetabolicRate = Power.kilocaloriesPerDay(kcalPerDay),
            metadata = newMetadata(id),
        )

    /** 一类记录在平台侧的「样子」：类型 + 界面标签 + 它需要的**那一项**权限。 */
    private class Spec(
        val type: KClass<out Record>,
        /** 类型名。**待求值**（会随 `HcKindResult` 存进界面状态，见 `ResText`）。 */
        val label: ResText,
        val permission: String,
        val build: (atMs: Long, value: Double, id: String) -> Record,
    )

    private fun specOf(kind: String): Spec? = when (kind) {
        Backfill.KIND_HR -> Spec(
            HeartRateRecord::class, ResText.core("s_00317450"),
            HealthPermission.getWritePermission(HeartRateRecord::class),
        ) { t, v, id -> hrRecord(t, v.toLong(), id) }
        Backfill.KIND_SPO2 -> Spec(
            OxygenSaturationRecord::class, ResText.core("s_004624e7"),
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
        ) { t, v, id -> spo2Record(t, v, id) }
        Backfill.KIND_HRV -> Spec(
            HeartRateVariabilityRmssdRecord::class, ResText.core("s_86b2f535"),
            HealthPermission.getWritePermission(HeartRateVariabilityRmssdRecord::class),
        ) { t, v, id -> hrvRecord(t, v, id) }
        Backfill.KIND_BODY_FAT -> Spec(
            BodyFatRecord::class, ResText.core("s_1507fa78"),
            HealthPermission.getWritePermission(BodyFatRecord::class),
        ) { t, v, id -> bodyFatRecord(t, v, id) }
        Backfill.KIND_LEAN_MASS -> Spec(
            LeanBodyMassRecord::class, ResText.core("s_5a292991"),
            HealthPermission.getWritePermission(LeanBodyMassRecord::class),
        ) { t, v, id -> leanMassRecord(t, v, id) }
        Backfill.KIND_WEIGHT -> Spec(
            WeightRecord::class, ResText.core("s_00292946"),
            HealthPermission.getWritePermission(WeightRecord::class),
        ) { t, v, id -> weightRecord(t, v, id) }
        Backfill.KIND_HEIGHT -> Spec(
            HeightRecord::class, ResText.core("s_00499c59"),
            HealthPermission.getWritePermission(HeightRecord::class),
        ) { t, v, id -> heightRecord(t, v, id) }
        Backfill.KIND_BMR -> Spec(
            BasalMetabolicRateRecord::class, ResText.core("s_7f3df8c2"),
            HealthPermission.getWritePermission(BasalMetabolicRateRecord::class),
        ) { t, v, id -> bmrRecord(t, v, id) }
        else -> null
    }

    // ───────────────────────── 幂等写入的公共部分 ─────────────────────────

    /**
     * 我们已经在这个时间窗内写过的幂等键。
     *
     * ⚠ `dataOriginFilter` 是**必须的**：不加它就会连三星写的记录一起读回来，
     * 于是"三星已经写过这条了"被误判成"我已经写过"，**我们这边就永远不写**。
     */
    private suspend fun existingIds(
        context: Context,
        client: HealthConnectClient,
        recordType: KClass<out Record>,
        from: Instant,
        to: Instant,
    ): Set<String> = client.readRecords(
        ReadRecordsRequest(
            recordType = recordType,
            timeRangeFilter = TimeRangeFilter.between(from, to),
            dataOriginFilter = setOf(DataOrigin(context.packageName)),
        )
    ).records.mapNotNull { it.metadata.clientRecordId }.toSet()

    /**
     * 写一条（幂等）。已经写过就**算成功**（回填的本质是"把数据弄上去"，
     * 不是"再插一遍"）。
     *
     * @param at 记录时刻，用来圈查重窗口（±60 秒）。
     */
    private suspend fun writeOne(
        context: Context,
        type: KClass<out Record>,
        permission: String,
        id: String,
        at: Instant,
        record: Record,
    ): Boolean {
        // ⚠ 精确检查**这一项**的权限，不是"是否全部 9 项都有" ——
        //    否则某一项新类型不被设备上的 HC 版本支持时，连心率都写不进去。
        if (permission !in grantedWritePermissions(context)) {
            lastError = ResText.of(R.string.s_325391a8)
            return false
        }
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val existing = existingIds(
                context, client, type, at.minusSeconds(60), at.plusSeconds(60),
            )
            if (id in existing) return@runCatching true
            client.insertRecords(listOf(record))
            true
        }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    // ───────────────────────── 单条写入（手表同步时实时中转） ─────────────────────────

    /** 写入一条心率到 Health Connect（无权限返回 false）。 */
    suspend fun writeHeartRate(
        context: Context, bpm: Int, at: Instant, source: String? = null,
    ): Boolean {
        val src = Backfill.normalizeSource(source ?: SYNC_SOURCE)
        val id = Backfill.clientId(Backfill.KIND_HR, src, at.toEpochMilli())
        return writeOne(
            context, HeartRateRecord::class,
            HealthPermission.getWritePermission(HeartRateRecord::class),
            id, at, hrRecord(at.toEpochMilli(), bpm.toLong(), id),
        )
    }

    /** 写入一条血压到 Health Connect（脉搏作为单独的心率记录一并写入）。 */
    suspend fun writeBloodPressure(
        context: Context, sys: Int, dia: Int, pulse: Int?, at: Instant, source: String? = null,
    ): Boolean {
        val src = Backfill.normalizeSource(source ?: SYNC_SOURCE)
        val atMs = at.toEpochMilli()
        val bpId = Backfill.clientId(BP_KIND, src, atMs)
        val bpOk = writeOne(
            context, BloodPressureRecord::class,
            HealthPermission.getWritePermission(BloodPressureRecord::class),
            bpId, at,
            BloodPressureRecord(
                time = at,
                zoneOffset = null,
                metadata = newMetadata(bpId),
                systolic = Pressure.millimetersOfMercury(sys.toDouble()),
                diastolic = Pressure.millimetersOfMercury(dia.toDouble()),
                bodyPosition = BloodPressureRecord.BODY_POSITION_UNKNOWN,
                measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_UNKNOWN,
            ),
        )
        if (!bpOk) return false
        if (pulse != null && pulse > 0) {
            // 脉搏用**独立的 kind**：它和心率是不同的记录类型（一个属于血压记录、
            // 一个属于心率记录），共用一个 kind 会让两者的幂等键对撞。
            val hrId = Backfill.clientId(BP_PULSE_KIND, src, atMs)
            writeOne(
                context, HeartRateRecord::class,
                HealthPermission.getWritePermission(HeartRateRecord::class),
                hrId, at, hrRecord(atMs, pulse.toLong(), hrId),
            )
        }
        return true
    }

    /** 写入一条血氧到 Health Connect（无权限返回 false）。 */
    suspend fun writeOxygenSaturation(
        context: Context, percent: Int, at: Instant, source: String? = null,
    ): Boolean {
        val src = Backfill.normalizeSource(source ?: SYNC_SOURCE)
        val id = Backfill.clientId(Backfill.KIND_SPO2, src, at.toEpochMilli())
        return writeOne(
            context, OxygenSaturationRecord::class,
            HealthPermission.getWritePermission(OxygenSaturationRecord::class),
            id, at, spo2Record(at.toEpochMilli(), percent.toDouble(), id),
        )
    }

    // ───────────────────────── 回填（把本机已有数据补写上去） ─────────────────────────

    /** 分批大小：太大可能超 binder 事务上限，太小则 IPC 次数过多。 */
    private const val CHUNK = 100

    /**
     * 照 [Backfill.plan] 算出的计划写上去。
     *
     * 每类一次查重（整段范围）+ 分批插入；某一批被拒则**退化成逐条**——
     * 平台侧会校验值域，一条越界不该拖掉整批。
     */
    suspend fun backfill(context: Context, plans: List<Backfill.KindPlan>): HcBackfillReport {
        if (!isAvailable(context)) {
            lastError = ResText.of(R.string.s_c782d49c)
            return HcBackfillReport(emptyList(), lastError)
        }
        // ⚠ 门禁是"**至少有一项**"，不是"全部 9 项"。
        //    设备上的 HC 版本可能不认识某些新类型（那几项永远授权不到），
        //    用 containsAll 当门禁会让回填**完全无法执行**。
        //    逐类是否有权限在下面的循环里判断，没有的那类会被报成 noPermission。
        val granted = grantedWritePermissions(context)
        if (granted.isEmpty()) {
            lastError = ResText.of(R.string.s_b6d7496f)
            return HcBackfillReport(emptyList(), lastError)
        }
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            return HcBackfillReport(emptyList(), lastError)
        }

        val results = ArrayList<HcKindResult>(plans.size)
        for (p in plans) {
            val spec = specOf(p.kind)
            if (spec == null || p.samples.isEmpty()) {
                results.add(
                    HcKindResult(p.kind, spec?.label ?: ResText.raw(p.kind), 0, p.notOurs, p.outOfRange, p.truncated, 0)
                )
                continue
            }
            // 这一类没有权限 → 记下来继续下一类（不是整体失败）
            if (spec.permission !in granted) {
                results.add(
                    HcKindResult(
                        p.kind, spec.label, 0, p.notOurs, p.outOfRange, p.truncated,
                        failed = 0, noPermission = p.samples.size,
                    )
                )
                continue
            }
            val from = Instant.ofEpochMilli(p.samples.first().atMs - 1000)
            val to = Instant.ofEpochMilli(p.samples.last().atMs + 1000)
            val res = runCatching {
                val existing = existingIds(context, client, spec.type, from, to)
                // 幂等键用**规范化后**的来源算 —— 新旧两种写法（型号 / watch_*）要收敛到同一个 id，
                // 否则同一条记录会被写两次（`Backfill.normalizeSource` 的台架断言钉着这条）。
                val fresh = p.samples.filter { s ->
                    val id = Backfill.clientId(p.kind, Backfill.normalizeSource(s.source), s.atMs)
                    id !in existing
                }
                var written = 0
                for (chunk in fresh.chunked(CHUNK)) {
                    val records = chunk.map { s ->
                        Backfill.clientId(p.kind, Backfill.normalizeSource(s.source), s.atMs) to
                            spec.build(
                                s.atMs, s.value,
                                Backfill.clientId(p.kind, Backfill.normalizeSource(s.source), s.atMs),
                            )
                    }
                    val batch = runCatching { client.insertRecords(records.map { it.second }) }
                    if (batch.isSuccess) {
                        written += records.size
                        continue
                    }
                    // 整批被拒 → 逐条重试，把坏的那几条挑出来而不是全丢
                    lastError = batch.exceptionOrNull()?.let { ResText.raw("${it.javaClass.simpleName}: ${it.message}") }
                    for ((_, r) in records) {
                        if (runCatching { client.insertRecords(listOf(r)) }.isSuccess) written++
                    }
                }
                HcKindResult(
                    p.kind, spec.label, written, p.notOurs, p.outOfRange, p.truncated,
                    failed = fresh.size - written,
                )
            }.getOrElse { e ->
                lastError = ResText.raw("${e.javaClass.simpleName}: ${e.message}")
                HcKindResult(
                    p.kind, spec.label, 0, p.notOurs, p.outOfRange, p.truncated,
                    failed = p.samples.size,
                )
            }
            results.add(res)
        }
        return HcBackfillReport(results, null)
    }

    // ───────────────────────── 手表同步时的实时中转 ─────────────────────────

    /** 中转写入：把手表同步来的记录写进 Health Connect，返回成功条数。 */
    suspend fun relayWriteWithCount(context: Context, msg: SyncMessage): Int {
        var n = 0
        for (item in msg.items) {
            val at = Instant.ofEpochMilli(item.recordedAt)
            // ⚠ 来源用 `msg.deviceId`（手表型号）传进去，而不是写死一个常量 ——
            //   这样它与"回填时从库里读到的 source"是**同一个值**，
            //   两条路径算出的幂等键一致，同一条记录不会被写两次。
            val ok = when (msg.type) {
                "hr" -> item.bpm?.let { writeHeartRate(context, it.toInt(), at, msg.deviceId) } ?: false
                "bp" -> {
                    val sys = item.systolic ?: continue
                    val dia = item.diastolic ?: continue
                    writeBloodPressure(context, sys, dia, item.pulse, at, msg.deviceId)
                }
                "spo2" -> item.spo2?.let { writeOxygenSaturation(context, it, at, msg.deviceId) } ?: false
                // Health Connect 的记录类型里没有 ECG / 心律条目，心电波形与结论无法写入，
                // 只能留在手表和手机本地（见 README 的"ECG 与 Health Connect"）。
                // 显式写明原因，避免上层把它误报成"权限不足"。
                "ecg" -> {
                    lastError = ResText.of(R.string.s_33d32e36)
                    false
                }
                else -> false
            }
            if (ok) n++
        }
        return n
    }

    // ───────────────────────── 经期（模块 #13） ─────────────────────────

    /**
     * 是否有读经期的权限（`READ_MENSTRUATION`）。
     *
     * 这个权限**独立**于上面的写入权限：写入的是心率/血压/血氧等，读取的是经期。
     * 用户可能只开了写入、没开经期读取，也可能反过来，所以单独判断、单独申请。
     */
    suspend fun hasMenstruationReadPermission(context: Context): Boolean {
        if (!isAvailable(context)) return false
        return runCatching {
            val granted = HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
            granted.contains(
                HealthPermission.getReadPermission(MenstruationPeriodRecord::class)
            )
        }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    /**
     * 从 Health Connect 读经期记录，转成 `Cycle.Entry`（epochDay 本地日期）。
     *
     * 只读 `MenstruationPeriodRecord`（区间型，有开始/结束日），
     * 不读 `MenstruationFlowRecord`（点型，只有某天的流量档位，没有区间）——
     * 周期预测只需要"每次经期的开始日"，流量是次要信息，从区间记录里拿不到就置 null。
     *
     * 时区转换：HC 存的是 `Instant` + `ZoneOffset`，经期是"哪天"的事，
     * 必须按记录自带的时区偏移还原成**本地日期**的 epochDay，而不是拿设备的默认时区
     * 去套（用户跨时区旅行时两者会差一天）。
     *
     * @param startEpochDay 只读这个日期（含）之后开始的记录，避免一次导入拖几年前的。
     * @return 转换好的经期记录列表（未合并去重，由 `Cycle.merge` 在分析时处理）。
     */
    suspend fun readMenstrualRecords(
        context: Context,
        startEpochDay: Long,
    ): List<com.gemini.healthbridge.core.Cycle.Entry> {
        if (!hasMenstruationReadPermission(context)) return emptyList()
        return runCatching {
            val client = HealthConnectClient.getOrCreate(context)
            val startInstant = Instant.ofEpochSecond(startEpochDay * 86_400L)
            val resp = client.readRecords(
                androidx.health.connect.client.request.ReadRecordsRequest(
                    recordType = MenstruationPeriodRecord::class,
                    timeRangeFilter = TimeRangeFilter.after(startInstant),
                )
            )
            resp.records.mapNotNull { r ->
                val sd = localEpochDay(r.startTime, r.startZoneOffset)
                val ed = r.endTime?.let { localEpochDay(it, r.endZoneOffset) }
                if (sd == null) null
                else com.gemini.healthbridge.core.Cycle.Entry(
                    startEpochDay = sd,
                    endEpochDay = ed,
                    flow = null,
                    source = com.gemini.healthbridge.core.Cycle.Source.HC,
                )
            }
        }.getOrElse {
            lastError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            emptyList()
        }
    }

    /** `Instant` + 时区偏移 → 本地日期 epochDay。偏移为 null 时退回系统默认时区。 */
    private fun localEpochDay(instant: Instant?, zoneOffset: java.time.ZoneOffset?): Long? {
        if (instant == null) return null
        val zone = zoneOffset ?: java.time.ZoneOffset.systemDefault().rules
            .getOffset(instant)
        return instant.atOffset(zone).toLocalDate().toEpochDay()
    }

    /** 手表同步来的记录没有来源字段时的兜底标记（正常路径一定带 `msg.deviceId`）。 */
    private const val SYNC_SOURCE = "watch_sync"

    /** 血压记录的 kind（`Backfill` 里没有它，因为血压的回填由三星那条路负责）。 */
    private const val BP_KIND = "bp"

    /** 血压里那一路脉搏的 kind —— 与心率分开，否则幂等键对撞。 */
    private const val BP_PULSE_KIND = "bp_pulse"
}
