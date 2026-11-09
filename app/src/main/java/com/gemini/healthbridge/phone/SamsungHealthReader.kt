package com.gemini.healthbridge.phone

import android.app.Activity
import android.content.Context
import com.gemini.healthbridge.core.Backfill
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.Energy
import com.gemini.healthbridge.core.BiaProfileDto
import com.gemini.healthbridge.core.BiaSource
import com.gemini.healthbridge.core.HourBucket
import com.gemini.healthbridge.core.Vitals
import com.gemini.healthbridge.core.db.DailyVitalsEntity
import com.gemini.healthbridge.core.db.EnergyScoreEntity
import com.gemini.healthbridge.core.db.SleepApneaEntity
import com.gemini.healthbridge.core.db.ActivityDayEntity
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.SleepNightEntity
import com.gemini.healthbridge.core.Odi
import com.gemini.healthbridge.core.SleepHours
import com.gemini.healthbridge.core.SleepNightDto
import com.gemini.healthbridge.core.SleepStages
import com.gemini.healthbridge.core.SleepStageDto
import com.gemini.healthbridge.core.SleepStageKind
import com.gemini.healthbridge.core.Extras
import com.gemini.healthbridge.core.Glucose
import com.gemini.healthbridge.core.Ihrn
import com.gemini.healthbridge.core.Spo2
import com.gemini.healthbridge.core.Temperature
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.db.BloodPressureEntity
import com.gemini.healthbridge.core.db.BodyCompositionEntity
import com.gemini.healthbridge.core.db.HeartRateEntity
import com.gemini.healthbridge.core.db.SleepOdiEntity
import com.gemini.healthbridge.core.db.SpO2Entity
import com.gemini.healthbridge.core.db.toEntity
import com.gemini.healthbridge.core.db.TemperatureEntity
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.AggregateOperation
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.data.entries.ExerciseSession
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.AggregateRequest
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.ReadDataRequest
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.InstantTimeFilter
import com.samsung.android.sdk.health.data.request.LocalDateFilter
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

/**
 * 手机端睡眠页一次读取的打包结果：睡眠摘要 + 血氧/心率原始序列。
 *
 * 特意绑在一起返回：[SleepNightDto] 里的平均值/最低值本来就是从这些序列算出来的，
 * 分开调用会把同一批三星健康数据读好几遍。
 *
 * @param spo2      **睡眠期**的血氧序列（[night] 的起止区间内）
 * @param spo2Today **当日全天**（今天 00:00 → 现在）的血氧序列，供血氧页画全天小时柱状图。
 *                  它包含 [spo2]，两者来自同一次分页读取，只是时间窗不同。
 * @param hr        睡眠期的心率序列
 */
data class SleepBundle(
    val night: SleepNightDto?,
    val spo2: List<Pair<Long, Int>>,
    val hr: List<Pair<Long, Int>>,
    val spo2Today: List<Pair<Long, Int>> = emptyList(),
    /**
     * 睡眠期心率的**标准差**（心率波动）。自研能量评分里「睡眠心率变化」那一项的输入。
     *
     * 与 [hr] 一样是"必须当场算出来"的量：序列刻意不落库（一晚几百上千个点，
     * 落库只会把「最近记录」列表刷屏），而标准差只能从序列算 ——
     * 所以它在读到的那一次就算好，随 `sleep_nights` 那一行落库。
     * 样本太少时为 null（**不是 0**，见 [hrStdDev]）。
     */
    val hrSd: Double? = null,
    /**
     * 除主睡眠之外的其它会话（午睡 / 补觉）。
     *
     * 为什么要带着它们：本页画的是**主睡眠**那一场，但窗口里可能还有午睡 ——
     * 不显示出来，用户会以为"午睡把整晚顶掉了"或者"午睡压根没记上"。
     * 界面只显示"另外还有 N 段小睡（时长）"，**不把它们并进主睡眠的统计**：
     * 并进去会让"实际睡眠时间"变成两段之和，而睡眠图只画了一段，对不上。
     */
    val naps: List<SleepHours.Span> = emptyList(),
) {
    companion object {
        val EMPTY = SleepBundle(null, emptyList(), emptyList(), emptyList())
    }
}

/**
 * 通过 Samsung Health Data SDK 读取三星健康中的真实数据（含手表同步过来的血压/心率）。
 * 数据类型 ECG 不在 SDK 开放范围内，心电图走手表端结论记录 + 后续波形通道。
 */
object SamsungHealthReader {

    /**
     * 合并睡眠阶段时允许的最大间隙（毫秒）。SDK 在阶段切换处会留几十毫秒空档，
     * 超过这个值就认为是真实的"醒了又睡"，不再合并（见 [mergeSleepStages]）。
     */
    private const val GAP_MS = 60_000L

    private var storeRef: HealthDataStore? = null

    /** 最近一次写入失败的详细原因（用于界面诊断显示）。 */
    @Volatile
    /**
     * 最后一次写入失败的原因。
     *
     * ⚠ **待求值**文案：既可能是我们自己写的本地化说明（`ResText.of`），
     * 也可能是平台异常原文（`ResText.raw`）。混成一个 `String` 会让本地化那条
     * 切语言后停在旧语言，而它会被存进回填报告一直显示在界面上。
     */
    var lastWriteError: ResText? = null
        private set

    /** 最近一次写入授权失败的详细原因（用于界面诊断显示）。 */
    @Volatile
    var lastWriteAuthError: String? = null
        private set

    /**
     * 最近一次**睡眠读权限**申请失败的原因。
     * 该权限是可选的（只为心率页的睡眠分区服务），失败不阻塞其它功能，
     * 但要留下痕迹——静默失败会让"睡眠时段一直没染色"变成一个查不出原因的现象。
     */
    @Volatile
    var lastSleepAuthError: String? = null
        private set

    /**
     * 最近一次**皮肤温度读权限**申请失败的原因。
     *
     * 与睡眠权限同级：只影响「体温」页里那一块"三星健康夜间皮肤温度"，
     * 拿不到时该卡片明说缺权限即可，其它功能不受影响。
     */
    @Volatile
    var lastSkinTempAuthError: String? = null
        private set

    /**
     * 最近一次**体成分 / 用户档案读权限**申请失败的原因。
     *
     * 体成分（BIA）同时需要两项：`BODY_COMPOSITION` 读历史、`USER_PROFILE` 读身高体重性别年龄。
     * 后者尤其关键 —— 手表端测 BIA 必须有资料，而手表自己没有三星健康 Data SDK，
     * 只能靠手机读出来再经蓝牙推过去。所以这项权限失败的后果是"手表上体成分页一直说缺资料"，
     * 必须留下可查的原因，不能静默失败。
     */
    @Volatile
    var lastBiaAuthError: String? = null

    /** 最近一次能量评分**请求权限**失败原因（此项分区域放开，失败是常态）。 */
    var lastEnergyAuthError: String? = null

    /**
     * 最近一次能量评分**读取**失败原因。null = 没失败过。
     *
     * ⚠ 与 [lastEnergyAuthError] 是**两个不同环节**，必须分开记：
     * "权限没拿到"和"权限拿到了但读的时候抛异常"要给出完全不同的处置建议。
     * 2026-09-21 之前 `readEnergyScores` 用 `runCatching{}.getOrElse{ emptyList() }`
     * **把异常整个吞掉**，界面于是把"读取失败"说成"三星还没替你算过分数" ——
     * 正是本项目明令禁止的"分不清读失败与没数据"。
     */
    var lastEnergyReadError: String? = null

    /**
     * 最近一次能量评分读取到的**条数**。
     *
     * `-1` = 根本没走到读取那一步（通常是没权限）；`0` = 读了、返回空。
     * 这两个的区别就是"读失败"与"没数据"的区别，必须让界面看得到。
     */
    var lastEnergyReadCount: Int = -1

    /** 最近一次睡眠呼吸暂停授权失败原因。 */
    var lastApneaAuthError: String? = null
        private set

    // ── 扩展指标（2026-09-23）的失败原因 ──────────────────────────────────
    //
    // 这一批有 15 个数据类型的权限，**每一个都可能因为地区/机型/用户没同意而失败**。
    // 给每一个都写一个 `lastXxxAuthError` 字段会变成 30 个字段的噪音，所以改用两张表按
    // [ExtraId] 查。
    //
    // ⚠ 两张表**必须分开**："没拿到权限"与"拿到权限但读的时候抛异常"要给用户
    // **完全不同的处置建议**（一个去授权、一个去看错误）。这是本项目反复踩过的纪律 ——
    // 一旦合并，界面就只能猜，而它一定会猜错方向（`readEnergyScores` 就有过这个 bug）。

    /** 扩展指标的**授权**失败原因（key 见 [ExtraId]）。 */
    val extraAuthErrors = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** 扩展指标的**读取**失败原因（key 见 [ExtraId]）。 */
    val extraReadErrors = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * 某个扩展指标当前的失败原因；没失败过返回 null（**null 不等于"没有数据"**）。
     *
     * 读取失败优先于授权失败：读取是**更靠后的环节**，它一旦发生说明权限那步已经过了，
     * 用户需要知道的是更靠后的那个原因。
     */
    fun extraError(id: String): String? = extraReadErrors[id] ?: extraAuthErrors[id]

    private fun store(context: Context): HealthDataStore =
        storeRef ?: HealthDataService.getStore(context).also { storeRef = it }

    /**
     * 从三星健康读回来的记录统一用这个 source 标记（与手表同步来的记录区分开）。
     * 落库时靠 `(source, measuredAt)` 唯一索引天然去重，重复同步不会产生重复行。
     */
    const val SOURCE_SAMSUNG = "samsung"

    private const val PREFS = "vitaltrace_samsung"
    private const val KEY_AUTHORIZED = "authorized_once"

    /**
     * 是否**曾经**成功授权过三星健康（持久化）。
     *
     * 这个标记是"授权只需一次"的关键：三星健康侧的授权本身是长期有效的，
     * 但 App 每次冷启动都要重新问一次"现在有哪些权限"。而冷启动瞬间
     * [HealthDataStore] 往往还没连上，这一次查询会失败。如果失败被当成"未授权"，
     * 用户看到的就是"所有数据都不见了、必须再手动授权一次才有记录"。
     * 有标记就能区分「从没授权」和「这次没查成功」，前者才需要打扰用户。
     */
    fun wasAuthorizedBefore(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTHORIZED, false)

    private fun markAuthorizedOnce(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_AUTHORIZED, true).apply()
    }

    private const val KEY_SYNCED_DAY = "synced_epoch_day"

    /**
     * 记下"**今天**已经成功同步过一次三星健康"（持久化）。
     *
     * ## 为什么需要它（2026-10-10）
     * 功能页那张卡上原来有一个「同步」按钮（`onSyncNow = { syncTick++ }`）。
     * 用户看穿了它："这个同步是不是会自动实现？"—— **是的**：冷启动、回到前台、
     * 切到血压/血氧/睡眠/腕表温度/体成分页都会自动同步一次（见 `MainActivity` 的三处触发点）。
     * 一个"其实用不着点"的按钮，最坏的作用是让人以为**不点就不会同步**。
     *
     * 撤掉按钮之后，那里必须换上一句**状态**，否则用户会失去"今天到底同步过没有"的判断依据。
     * 判据就落在这里：**今天成功跑完过一次同步**（拿到了读权限、各步都执行了）。
     *
     * ⚠ 刻意**不**用"本次新增了多少条"当判据：那个数在当天第二次同步时必然归 0
     *   （`insertAll` 只统计新插入的行），于是"今日已同步"会在当天反复横跳。
     */
    fun markSyncedToday(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(KEY_SYNCED_DAY, LocalDate.now().toEpochDay()).apply()
    }

    /** 今天是否已经成功同步过一次三星健康（跨进程重启有效）。 */
    fun syncedToday(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SYNCED_DAY, -1L) == LocalDate.now().toEpochDay()

    /**
     * 查询已授权权限；**连不上返回 null**，而不是返回空集合。
     *
     * null 与 emptySet 的语义必须分开：
     *   · emptySet = 问到了，确实没授权 → 该提示用户去授权；
     *   · null     = 没问到（store 未就绪/被系统回收）→ 不能下结论，应当重试。
     * 历史上两者被混为一谈，于是"启动时一次连接失败"被显示成"未授权"。
     */
    private suspend fun grantedOrNull(context: Context, perms: Set<Permission>): Set<Permission>? =
        runCatching { store(context).getGrantedPermissions(perms) }
            .getOrElse {
                // ⚠⚠ 2026-10-22：返回 9004 `PlatformInternalException: Remote exception occurs`
                //   多半是**三星健康进程被系统冻结/回收**（用户实测：三星健康不在后台时必现，
                //   放到后台就正常）。这时**不能只把引用置空**——那条 IPC 已经半死，
                //   必须真正 disconnect，下一次才会新建一条干净的连接。
                // ⚠ 这版 SDK 的 `HealthDataStore` 是接口、**没有 disconnect**（javap 确认过），
                //   所以只能丢掉引用：下次 `getStore()` 会拿一条新的。
                storeRef = null
                null
            }

    /** 读取权限（主授权，历史上最稳定）。 */
    val readPermissions: Set<Permission> = setOf(
        Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.READ),
        Permission.of(DataTypes.HEART_RATE, AccessType.READ),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.READ),
    )

    /**
     * 写入权限（用于把同步来的手表数据写进三星健康；部分版本需要单独授权）。
     *
     * ⚠ 从 2 项扩到 4 项（2026-09-24）：加了血氧与体成分。
     * `AccessType.WRITE` 与 `READ` 是**两套独立权限**，读得到不等于写得进。
     *
     * 刻意**不含** `BODY_TEMPERATURE`：三星那个字段的语义是**核心体温**
     * （给体温贴/体温计用的），而手表给的是**腕部皮温**（低 1~3℃）。
     * 写进去会污染对方的核心体温序列 —— 与「不拿估算值冒充测量值」是同一条纪律。
     */
    val writePermissions: Set<Permission> = setOf(
        Permission.of(DataTypes.BLOOD_PRESSURE, AccessType.WRITE),
        Permission.of(DataTypes.HEART_RATE, AccessType.WRITE),
        Permission.of(DataTypes.BLOOD_OXYGEN, AccessType.WRITE),
        Permission.of(DataTypes.BODY_COMPOSITION, AccessType.WRITE),
    )

    /**
     * 睡眠时段读权限（心率页用来把睡眠小时画成另一种颜色、并单独统计睡眠心率）。
     *
     * 刻意**不**并进 [readPermissions]：血压/心率/血氧这套读权限历史上最稳定，
     * 把睡眠混进去一旦被拒，会把整批读权限一起拖失败。它单独申请、单独容忍失败。
     */
    val sleepPermission: Permission = Permission.of(DataTypes.SLEEP, AccessType.READ)

    /**
     * 皮肤温度读权限（「体温」页用来显示三星健康睡眠期的皮肤温度）。
     *
     * 同样**不并进 [readPermissions]**，理由与睡眠权限完全一致：血压/心率/血氧那套读权限
     * 历史上最稳定，把新增的可选权限混进同一批，一旦被拒就会把整批读权限一起拖失败。
     *
     * 需要留意的一点：三星健康的皮肤温度**只有睡眠期间**才采集，而且它给的是
     * "相对个人基线的偏差"（±0.x ℃），不是绝对体温。想要"随时点检一个真实温度"，
     * 只能靠手表端的传感器（见 wear 模块的 TempSensorManager）。
     */
    val skinTempPermission: Permission = Permission.of(DataTypes.SKIN_TEMPERATURE, AccessType.READ)

    /**
     * 体成分读权限（「体成分」页读三星健康自己的体成分历史）。
     *
     * 同样**不并进 [readPermissions]**，理由与睡眠/皮肤温度完全一致。
     *
     * ⚠ 注意**不要**把这一项加进 [permissions]：
     * [permissions] 是 `granted()` 的参数，而 `granted()` 是心率/血压/血氧/睡眠**所有读取**
     * 的前置门（`granted(context).none { READ }` 就直接返回空）。体成分是最新、最可能不被
     * 平台支持的一项，一旦 `getGrantedPermissions` 对它抛异常，就会把整批读取一起拖成空 ——
     * 表现为"打开软件所有数据都没了"。所以本项一律走下面的**定点查询**（`hasXxxPermission`），
     * 从不参与那个总闸。
     */
    val bodyCompositionPermission: Permission =
        Permission.of(DataTypes.BODY_COMPOSITION, AccessType.READ)

    /**
     * 用户档案读权限（身高/体重/性别/生日）。
     *
     * 存在的唯一理由是**手表端测 BIA 需要资料**：三星的 `getHealthTracker(BIA_ON_DEMAND, profile)`
     * 必须带 `TrackerUserProfile`，而 Wear OS 侧没有 Data SDK，手表自己读不到档案。
     * 所以手机读出来后经蓝牙 [com.gemini.healthbridge.core.FrameKinds.BIA_PROFILE] 推给手表。
     *
     * 同样不并进 [permissions]，理由同上。
     */
    val userProfilePermission: Permission =
        Permission.of(DataTypes.USER_PROFILE, AccessType.READ)

    /**
     * 能量评分读权限（三星健康自己算的「每日身心准备度」）。
     *
     * 同样不并进 [permissions]，理由同睡眠/皮肤温度/体成分。
     *
     * ⚠ 这个类型**分区域、分机型放开**（要 Galaxy Watch7 以上 + 三星账号）。
     * 申请不到是常态、不是故障 —— 界面上要说明原因，不能显示成 0 分。
     */
    val energyScorePermission: Permission =
        Permission.of(DataTypes.ENERGY_SCORE, AccessType.READ)

    /**
     * 睡眠呼吸暂停读权限。
     *
     * ⚠ 数据的**来源是「三星健康监测器」**（另一个 App）：用户在那边开启呼吸暂停检测、
     * 并授权把数据同步给三星健康之后，这里才读得到。
     * 所以「没有数据」是预期结果（区域不支持 / 没开启 / 没授权都会是空），不是故障。
     */
    val sleepApneaPermission: Permission =
        Permission.of(DataTypes.SLEEP_APNEA, AccessType.READ)

    /**
     * 「一键授权」一次要的全部基础权限（2026-10-07）。
     *
     * ⚠ 与 [permissions]（`granted()` 那个总闸）**不是一回事，别合并**：
     * 那个只含"必须有才能用"的四项；这个含全部八组，是**申请**用的清单。
     * 把两者合成一个的后果见 [permissions] 自己的注释（一项被拒会把整批读取拖成空）。
     *
     * ⚠ 这里**不含**扩展指标那 15 个：它们的使用时机是"用户想看那类数据的那一刻"，
     * 见 [requestExtra]。
     *
     * ⚠ 声明位置**必须在它引用的所有字段之后**（Kotlin 实例属性按声明顺序初始化）——
     * 往这个类里加新的权限字段时，注意别把它排到这里之前。
     */
    /**
     * ⚠⚠ **不含 [writePermissions]**（2026-10-17 改）。
     *
     * 三星对第三方 App 的 WRITE（血压/心率/血氧/体成分）**按策略直接拒绝**，
     * SDK 抛 `AuthorizationException: 2003: Permission is not allowed due to SDK policy`。
     * 把它放进合并请求里，结果是**一键授权整体失败并弹错**，而用户什么也做不到。
     * ⇒ 不再申请；`writeHeartRate` / `writeBloodPressure` 会因 `hasWritePermission == false` 自动跳过
     *   （它们本来就是"可选"的中转写入，不影响任何读取与展示）。
     */
    val allPermissions: Set<Permission> =
        readPermissions + setOf(
            sleepPermission,
            skinTempPermission,
            bodyCompositionPermission,
            userProfilePermission,
            energyScorePermission,
            sleepApneaPermission,
        )

    // ═══════════ 扩展指标（2026-09-23）：15 个可选读权限 ═══════════
    //
    // 全部**不并进 [permissions]**，理由与上面几项完全一致：`permissions` 是
    // `granted()` 的参数，而 `granted()` 是所有基础读取的前置**总闸** ——
    // 把"分机型 / 分地区放开"的新权限混进去，一旦某一项被拒就会把整批读取拖成空，
    // 表现为"打开软件所有数据都没了"。所以一律走定点查询（[hasReadPermission]）。

    /** 血糖读权限。**需血糖仪/CGM 把数据写进三星健康**才有数（手表测不出）。 */
    val bloodGlucosePermission: Permission =
        Permission.of(DataTypes.BLOOD_GLUCOSE, AccessType.READ)

    /**
     * 核心体温读权限。
     *
     * ⚠ 与 [skinTempPermission] **不是一回事**：这个是**核心体温**（合作体温计/体温贴
     * 或用户手动录入），那个是腕部皮温。两者差 1~3℃，不能混在一张卡里画。
     */
    val bodyTemperaturePermission: Permission =
        Permission.of(DataTypes.BODY_TEMPERATURE, AccessType.READ)

    /** 运动记录读权限。 */
    val exercisePermission: Permission = Permission.of(DataTypes.EXERCISE, AccessType.READ)

    /** 营养记录读权限（数据由**用户自己**在三星健康里记）。 */
    val nutritionPermission: Permission = Permission.of(DataTypes.NUTRITION, AccessType.READ)

    /** 饮水记录读权限（同上，用户自己记）。 */
    val waterIntakePermission: Permission = Permission.of(DataTypes.WATER_INTAKE, AccessType.READ)

    /** 爬楼记录读权限。 */
    val floorsClimbedPermission: Permission =
        Permission.of(DataTypes.FLOORS_CLIMBED, AccessType.READ)

    /** 心律不齐通知读权限（数据来自**「三星健康监测器」**）。 */
    val ihrnPermission: Permission =
        Permission.of(DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION, AccessType.READ)

    // ── 以下是**聚合取数**的类型（它们没有 `readDataRequestBuilder`，但同样要授权）──

    /** 步数（聚合 `TOTAL`）。 */
    val stepsPermission: Permission = Permission.of(DataTypes.STEPS, AccessType.READ)

    /** 活动汇总（聚合 4 个算子：活动/总消耗、活动时长、距离）。 */
    val activitySummaryPermission: Permission =
        Permission.of(DataTypes.ACTIVITY_SUMMARY, AccessType.READ)

    /** 六类每日目标（聚合 `LAST` / `LAST_CALORIES` / `LAST_*_TIME`）。 */
    val stepsGoalPermission: Permission = Permission.of(DataTypes.STEPS_GOAL, AccessType.READ)
    val sleepGoalPermission: Permission = Permission.of(DataTypes.SLEEP_GOAL, AccessType.READ)
    val activeCaloriesGoalPermission: Permission =
        Permission.of(DataTypes.ACTIVE_CALORIES_BURNED_GOAL, AccessType.READ)
    val activeTimeGoalPermission: Permission =
        Permission.of(DataTypes.ACTIVE_TIME_GOAL, AccessType.READ)
    val waterIntakeGoalPermission: Permission =
        Permission.of(DataTypes.WATER_INTAKE_GOAL, AccessType.READ)
    val nutritionGoalPermission: Permission =
        Permission.of(DataTypes.NUTRITION_GOAL, AccessType.READ)

    /**
     * `ExtraId` → 权限的对应表，供 [request] 里**逐个申请、逐个容忍失败**。
     *
     * 写成表而不是 15 段复制粘贴，理由有两条：
     * ① "忘了 catch" 在结构上就不可能发生（循环体只有一个）；
     * ② 失败原因自动带上正确的 [ExtraId] —— 手写 15 遍必然有一遍抄错 key，
     *    而抄错的后果是**界面上永远查不到那个错误**（表现为"没有数据"）。
     */
    private val extraPermissions: List<Pair<String, Permission>> = listOf(
        ExtraId.GLUCOSE to bloodGlucosePermission,
        ExtraId.CORE_TEMP to bodyTemperaturePermission,
        ExtraId.EXERCISE to exercisePermission,
        ExtraId.NUTRITION to nutritionPermission,
        ExtraId.WATER to waterIntakePermission,
        ExtraId.FLOORS to floorsClimbedPermission,
        ExtraId.IHRN to ihrnPermission,
        ExtraId.STEPS to stepsPermission,
        ExtraId.ACTIVITY_SUMMARY to activitySummaryPermission,
        ExtraId.GOAL_STEPS to stepsGoalPermission,
        ExtraId.GOAL_SLEEP to sleepGoalPermission,
        ExtraId.GOAL_ACTIVE_CAL to activeCaloriesGoalPermission,
        ExtraId.GOAL_ACTIVE_TIME to activeTimeGoalPermission,
        ExtraId.GOAL_WATER to waterIntakeGoalPermission,
        ExtraId.GOAL_NUTRITION to nutritionGoalPermission,
    )

    /**
     * 本机能不能用三星健康数据接口。
     *
     * ⚠⚠ 三星 Health Data SDK **只在三星手机上可用**，且要求三星健康已安装并启用。
     *   非三星机型上，[request] 要么抛异常、要么授权页根本不弹 —— 用户看到的就是
     *   "点了「去授权」没反应 / 全部未授权"，而且**换谁来点都一样**。
     *   之前没有这个判断，界面只能给出"去授权"按钮，把用户引到一个不存在的地方
     *   （2026-10-17 多位测试者反馈"有人能授权、有人全都授不了"）。
     */
    data class Env(
        val isSamsungDevice: Boolean,
        val healthInstalled: Boolean,
        val healthVersion: String?,
        /** 厂商 + 型号，直接显示给用户看（"到底是不是三星"不该由我们下结论，要给事实）。 */
        val deviceLabel: String,
    ) {
        /**
         * 值不值得尝试授权。
         *
         * ⚠ 判据只认**三星健康是否存在**（2026-10-17 改）：SDK 依赖的是 SH 本体，
         *   而"厂商不是 samsung"只是**常见的**失败原因，不是充分证据 —— 之前拿它当硬门禁，
         *   会把"能授权"的机型也一起挡住（用户反馈"根本没弹"）。
         *   厂商不符时**保留按钮**，只在顶部加一句提示，让用户自己试一次。
         */
        val canTry: Boolean get() = healthInstalled
    }

    fun environment(context: Context): Env {
        val pm = context.packageManager
        val installed = runCatching {
            pm.getPackageInfo(SAMSUNG_HEALTH_PKG, 0)
        }.getOrNull()
        val brand = android.os.Build.MANUFACTURER ?: "?"
        val model = android.os.Build.MODEL ?: "?"
        return Env(
            isSamsungDevice = brand.equals("samsung", ignoreCase = true),
            healthInstalled = installed != null,
            healthVersion = installed?.versionName,
            deviceLabel = "$brand $model",
        )
    }

    /** 三星健康的包名（判断是否安装用；**不要**用它去跳转，入口在系统里）。 */
    const val SAMSUNG_HEALTH_PKG = "com.sec.android.app.shealth"

    /** 兼容旧调用：全部权限（含可选的睡眠 / 皮肤温度读权限）。 */
    val permissions: Set<Permission> =
        readPermissions + writePermissions + sleepPermission + skinTempPermission

    /**
     * 查询全部权限。失败时**先重试一次**再返回空集合。
     *
     * 单次失败就返回空会让所有读取函数（它们都用 `granted(...)` 做前置检查）
     * 一起返回空数据，表现为"整页空白"。多等 400ms 重建一次 store，
     * 代价只在失败路径上，正常路径无感。
     */
    suspend fun granted(context: Context): Set<Permission> =
        grantedOrNull(context, permissions) ?: run {
            delay(400)
            grantedOrNull(context, permissions) ?: emptySet()
        }

    /** 对外判断"是否已授权"用这个：null 表示无法确定（连接失败），不要当成未授权。 */
    suspend fun grantedWithRetry(context: Context, attempts: Int = 4): Set<Permission>? {
        repeat(attempts) { i ->
            grantedOrNull(context, permissions)?.let { return it }
            if (i < attempts - 1) delay(600L * (i + 1))
        }
        return null
    }

    /**
     * 授权流程。**先试「一次要全部」，整批失败再回退逐组申请**（2026-10-07 用户要求一键授权）。
     *
     * ## 为什么现在合并
     *
     * 原实现逐组串行 7 次 `requestPermissions`（读 / 睡眠 / 写 / 皮温 / 体成分+档案 /
     * 能量评分 / 呼吸暂停），**每一次都会弹一次三星健康授权页** —— 用户得点 7 次，
     * 再加 Health Connect 最多 8 次。用户 2026-10-07 的原话：「一个个手动点授权，麻烦」。
     *
     * 合并成一次之后，授权页会把所有条目列在**同一屏**，用户确认一次就完事。
     *
     * ## ⚠ 为什么必须保留逐组回退
     *
     * [requestByGroup] 里那个顺序是**刻意**的：越靠后越"可能不被本机 / 本区域支持"
     * （能量评分要 Watch7+ 与三星账号；呼吸暂停的数据来自「三星健康监测器」；
     * 皮温部分机型没有）。分开申请的意义是**某一项不被支持时，不会把前面已授上的拖垮**。
     *
     * 合并成一整批就没了这条保障 —— 有些机型会**因为批里夹着一个不认识的数据类型而整批抛异常**。
     * 所以策略是：**先合并试一次；抛异常了，用原来的逐组路径重来一遍**。
     * 常见机型 1 次搞定；异常机型退化成 7 次，也就是回到改之前的样子，**不会更差**。
     *
     * ⚠⚠ 判据**只认抛异常，不认「返回空集」**：全部权限本来就已授权时，SDK 可能返回空集
     * 或只含新增项。若把空集当失败去回退，用户每次点授权都会被弹 7 次 —— 比改之前更糟。
     */
    suspend fun request(context: Activity): Set<Permission> {
        val merged = runCatching { requestWithRetry(context, allPermissions) }
        val granted = merged.getOrNull()
        // 整批失败（多半是"夹了本机不支持的类型"）⇒ 回退逐组，让不支持项只影响它自己
        if (granted == null) return requestByGroup(context)

        // 拿到任何一项就算"授权过"：冷启动时即使一次连接失败，也不会再被当成未授权、
        // 更不会弹第二次授权界面（与逐组路径同一语义）。
        if (granted.isNotEmpty()) markAuthorizedOnce(context)
        // 合并请求**不会逐组抛异常**，所以"哪一组授上了"只能**按实际授予集合反推**。
        // ⚠ 不能用"没抛异常就算成功"：那会把"用户没勾皮温"记成成功，
        //   而这几条原因是要显示给用户看的（授权提示与能量页的诊断都用它）。
        noteGroup(context, granted, setOf(sleepPermission)) { lastSleepAuthError = it }
        noteGroup(context, granted, writePermissions) { lastWriteAuthError = it }
        noteGroup(context, granted, setOf(skinTempPermission)) { lastSkinTempAuthError = it }
        noteGroup(
            context, granted, setOf(bodyCompositionPermission, userProfilePermission)
        ) { lastBiaAuthError = it }
        noteGroup(context, granted, setOf(energyScorePermission)) { lastEnergyAuthError = it }
        noteGroup(context, granted, setOf(sleepApneaPermission)) { lastApneaAuthError = it }
        // 刚授权完，权限缓存必须作废，否则界面还是按"没授权"渲染（2 秒内的缓存）
        permCacheAt = 0L
        return granted
    }

    /**
     * 只申请**指定那一组**权限（授权页里每行的「去授权」用它）。
     *
     * ⚠ 为什么需要这条路径（2026-10-17 用户反馈）：原来每一行都调 [request]，
     *   于是点「写入权限」的「去授权」也会弹出**整页全部条目** —— 想补一项被迫重看全部，
     *   而且合并批次里只要夹了一个本机不支持的类型，整批就可能失败（见 [request] 的说明）。
     *   按组申请时，SDK 的授权页只列这一组，失败也只影响这一组。
     *
     * ⚠ 与 [request] 一样**不吞异常**：调用方要能区分"用户没勾"与"本机不支持"。
     */
    suspend fun requestOnly(context: Activity, perms: Set<Permission>): Set<Permission> {
        // ⚠⚠ 异常必须**记进这一组**再抛（2026-10-17 补）：`request()`（合并路径）有 noteGroup 兜底，
        //   而按组申请这条路径原先**什么都不记** ⇒ 非三星机型上"点了没反应"，
        //   界面上连原因都没有，只能靠猜。现在把 SDK 的原话留下来。
        val granted = try {
            requestWithRetry(context, perms)
        } catch (t: Throwable) {
            val msg = t.javaClass.simpleName + (t.message?.let { "：$it" } ?: "")
            when {
                perms == writePermissions -> lastWriteAuthError = msg
                perms == setOf(sleepPermission) -> lastSleepAuthError = msg
                perms == setOf(skinTempPermission) -> lastSkinTempAuthError = msg
                perms.any { it == bodyCompositionPermission || it == userProfilePermission } ->
                    lastBiaAuthError = msg
                perms == setOf(energyScorePermission) -> lastEnergyAuthError = msg
                perms == setOf(sleepApneaPermission) -> lastApneaAuthError = msg
                // 读权限没有自己的错误字段：统一记到 energy 那条"总"错误上，界面照样能看到
                else -> lastEnergyAuthError = msg
            }
            throw t
        }
        if (granted.isNotEmpty()) markAuthorizedOnce(context)
        // 权限缓存必须作废，否则界面还是按"没授权"渲染（2 秒内的缓存）
        permCacheAt = 0L
        // 这一组全授上了 ⇒ 清掉它上次的错误，免得界面上还挂着旧原因
        if (granted.containsAll(perms)) {
            when {
                perms == writePermissions -> lastWriteAuthError = null
                perms == setOf(sleepPermission) -> lastSleepAuthError = null
                perms == setOf(skinTempPermission) -> lastSkinTempAuthError = null
                perms == setOf(bodyCompositionPermission, userProfilePermission) ||
                    perms == setOf(bodyCompositionPermission) ||
                    perms == setOf(userProfilePermission) -> lastBiaAuthError = null
                perms == setOf(energyScorePermission) -> lastEnergyAuthError = null
                perms == setOf(sleepApneaPermission) -> lastApneaAuthError = null
            }
        }
        return granted
    }

    /**
     * 记一组权限的申请结果：**全都授到了就清掉上次的错误**，否则记一句通用原因。
     *
     * ⚠ 通用原因复用 `s_a443263b`（「用户未授予该权限」）而不是记具体异常 ——
     * 合并请求里根本没有"这一组"的异常可记（整批是一起发出去的）。
     * 这一句对"用户没勾"与"本机不支持"是同样诚实的说法：两种情况下他看到的都是没授上。
     */
    private fun noteGroup(
        context: Context,
        granted: Set<Permission>,
        need: Set<Permission>,
        put: (String?) -> Unit,
    ) = put(
        if (granted.containsAll(need)) null else LanguageStore.string(context, R.string.s_a443263b)
    )

    /**
     * **回退路径**（也是合并请求出现之前的老实现）：三步，越靠前越关键 ——
     * ① 先请求读取权限（历史上最稳定的一步，失败会抛异常由调用方提示）；
     * ② 再单独、静默地尝试睡眠读权限——只为心率页的睡眠分区服务，失败不影响其它功能；
     * ③ 最后静默尝试写入权限——部分机型/版本对写权限限制更严，失败不影响读取和导入。
     */
    private suspend fun requestByGroup(context: Activity): Set<Permission> {
        val granted = mutableSetOf<Permission>()
        granted += requestWithRetry(context, readPermissions)
        // 主读权限一旦拿到就记下"授权过"。之后冷启动即使一次连接失败，
        // 也不会再被当成未授权、更不会弹第二次授权界面。
        if (granted.isNotEmpty()) markAuthorizedOnce(context)
        try {
            granted += requestWithRetry(context, setOf(sleepPermission))
            if (granted.any { it.dataType == DataTypes.SLEEP }) markAuthorizedOnce(context)
            lastSleepAuthError = null
        } catch (e: Exception) {
            lastSleepAuthError = "${e.javaClass.simpleName}: ${e.message}"
        }
        try {
            granted += requestWithRetry(context, writePermissions)
            lastWriteAuthError = null
        } catch (e: Exception) {
            lastWriteAuthError = "${e.javaClass.simpleName}: ${e.message}"
        }
        // 皮肤温度读权限放在**最后**单独申请：它是最新、最可能不被平台支持的一项
        // （部分机型/地区没有皮肤温度数据），放最后意味着它失败也不会影响前面任何一步。
        try {
            granted += requestWithRetry(context, setOf(skinTempPermission))
            if (granted.any { it.dataType == DataTypes.SKIN_TEMPERATURE }) markAuthorizedOnce(context)
            lastSkinTempAuthError = null
        } catch (e: Exception) {
            lastSkinTempAuthError = "${e.javaClass.simpleName}: ${e.message}"
        }
        // ⑤ 体成分 + 用户档案，放**最后**：这是最新、最可能不被平台支持的一组，
        //    排在末尾意味着它失败也不会影响前面任何一步。
        //    用户档案是手表端测 BIA 的前提（手表读不到三星健康，只能靠手机推），
        //    所以这一项失败要显式记下原因：否则手表那边的表现只是"一直提示缺资料"。
        try {
            granted += requestWithRetry(
                context, setOf(bodyCompositionPermission, userProfilePermission)
            )
            lastBiaAuthError = null
        } catch (e: Exception) {
            lastBiaAuthError = "${e.javaClass.simpleName}: ${e.message}"
        }
        // ⑥ 能量评分 / 睡眠呼吸暂停，放**最最最后**：这两个是"分区域、分机型放开"的类型
        //    （能量评分要 Watch7+ 与三星账号；呼吸暂停的数据来自「三星健康监测器」），
        //    在多数机器/地区根本不存在。排在末尾意味着它们被拒也不会影响前面任何一步 ——
        //    这正是本文件一以贯之的纪律：可选的、可能不被支持的权限，单独申请、单独容忍失败。
        try {
            granted += requestWithRetry(context, setOf(energyScorePermission))
            lastEnergyAuthError = null
        } catch (e: Exception) {
            lastEnergyAuthError = "${e.javaClass.simpleName}: ${e.message}"
        }
        try {
            granted += requestWithRetry(context, setOf(sleepApneaPermission))
            lastApneaAuthError = null
        } catch (e: Exception) {
            lastApneaAuthError = "${e.javaClass.simpleName}: ${e.message}"
        }
        // ⚠ 扩展指标（2026-09-23）的 15 个权限**故意不在这里申请**。
        //
        // 原因：`request()` 是**冷启动 / 用户页那个"授权"按钮**走的路径，一次调起
        // 十几个系统对话框是骚扰 —— 用户根本不知道自己正在同意什么，
        // 而且这一批里多数是"他压根没有那些设备"（血糖仪、体温计），批了也用不上。
        //
        // 正确时机是**用户想看那类数据的那一刻**：见 [requestExtra]，由各功能页的
        // "去授权"按钮触发，一次只问那一页要用的 1~3 项。
        //
        // ⚠ 2026-10-07 与合并路径对齐，同样作废权限缓存：`hasReadPermission` 走的是
        //   2 秒记忆化（见 [grantedMemo]），刚授权完不清就会"点了授权但页面还显示未授权"。
        //   （`hasSleepPermission*` 那一批走的是 `grantedOrNull`，不受影响 —— 但既然
        //   [request] 的合并路径清了，回退路径不清会让两条路行为不一致，更难排查。）
        permCacheAt = 0L
        return granted
    }

    /**
     * 按需申请**扩展指标**的权限（由各功能页的"去授权"按钮触发）。
     *
     * 与 `request()` 里那批基础权限分开走，是因为**时机不同**：
     * 基础权限是"用这个 App 就需要"，扩展权限是"想看这一类数据才需要"。
     *
     * 每一项**单独申请、单独容忍失败** —— 被拒只影响自己那一项，
     * 失败原因记进 [extraAuthErrors]（key 用 [ExtraId]），界面据此显示具体原因。
     *
     * @param ids 要申请的 [ExtraId] 列表；认不出的 id 直接忽略（不会崩）。
     * @return 这次实际拿到的权限集合。
     */
    suspend fun requestExtra(context: Activity, ids: List<String>): Set<Permission> {
        var got = emptySet<Permission>()
        for (id in ids) {
            val perm = extraPermissions.firstOrNull { it.first == id }?.second ?: continue
            runCatching { requestWithRetry(context, setOf(perm)) }
                .onSuccess {
                    got += it
                    if (it.contains(perm)) extraAuthErrors.remove(id)
                    else extraAuthErrors[id] = LanguageStore.string(context, R.string.s_a443263b)
                }
                .onFailure { extraAuthErrors[id] = it.brief() }
        }
        // 刚授权完，权限缓存必须作废，否则界面还是按"没授权"渲染（2 秒内的缓存）
        permCacheAt = 0L
        return got
    }

    /** 某个扩展指标有没有被用户拒绝过（界面据此决定要不要显示"去授权"按钮）。 */
    fun extraAuthFailed(id: String): Boolean = extraAuthErrors.containsKey(id)

    private suspend fun requestWithRetry(context: Activity, perms: Set<Permission>): Set<Permission> {
        var lastError: Exception? = null
        repeat(3) {
            try {
                return store(context).requestPermissions(perms, context)
            } catch (e: Exception) {
                lastError = e
                storeRef = null
                kotlinx.coroutines.delay(2500)
            }
        }
        throw lastError ?: IllegalStateException(LanguageStore.string(context, R.string.s_832690c0))
    }

    /**
     * 一次查询得出**全部授权状态**。
     *
     * ⚠⚠ 为什么要合并（2026-10-17，用户反馈"系统记录里启动三星健康被拒 134 次"）：
     *   原来界面要调 `grantedWithRetry` + 4 个 `has*PermissionWithRetry`，**每个都最多连 4 次**
     *   ⇒ 一次刷新最多发起 **20 次** `HealthDataStore` 连接；而每次连接都可能尝试**拉起三星健康**。
     *   在小米/红米上，"关联启动"默认被拦 ⇒ 一次刷新就是二十条"拒绝"记录，
     *   既打不开授权页，也让系统把本应用当成滥用。
     *   ⇒ 合并成**一次**查询，把需要的标志全部算出来。
     *
     * @return `null` = 连不上（**问不出来**，不能当成"未授权"）。
     */
    data class Snapshot(
        val granted: Boolean,
        val sleep: Boolean,
        val skinTemp: Boolean,
        val bia: Boolean,
        val profile: Boolean,
    )

    suspend fun snapshot(context: Context, attempts: Int = 5): Snapshot? {
        val all = readPermissions + sleepPermission + skinTempPermission +
            bodyCompositionPermission + userProfilePermission
        val g = grantedOrNull(context, all) ?: run {
            repeat(attempts - 1) { i ->
                delay(500L * (i + 1))
                grantedOrNull(context, all)?.let { return snapshotOf(it) }
            }
            return null
        }
        return snapshotOf(g)
    }

    private fun snapshotOf(g: Set<Permission>) = Snapshot(
        granted = g.any { it.accessType == AccessType.READ },
        sleep = g.any { it.dataType == DataTypes.SLEEP && it.accessType == AccessType.READ },
        skinTemp = g.any {
            it.dataType == DataTypes.SKIN_TEMPERATURE && it.accessType == AccessType.READ
        },
        bia = g.any { it.dataType == DataTypes.BODY_COMPOSITION && it.accessType == AccessType.READ },
        profile = g.any { it.dataType == DataTypes.USER_PROFILE && it.accessType == AccessType.READ },
    )

    /** 是否已授予写入权限（未授予时中转写入会跳过）。 */
    /**
     * **读**权限是否至少给了一条（2026-10-22）。
     *
     * 用途：`relayWriteWithCount` 返回 -1 只代表"没有写权限"，而三星的 WRITE 权限
     * 按平台策略本来就拿不到 ⇒ 常态就是 -1。这时如果直接说"三星健康未授权"，
     * 就会和「授权」页的"已全部授权"**互相打架**（用户多次反馈的那条）。
     */
    suspend fun hasReadAccess(context: Context): Boolean =
        runCatching { granted(context).any { it.accessType == AccessType.READ } }.getOrDefault(false)

    suspend fun hasWritePermission(context: Context): Boolean =
        runCatching {
            granted(context).any { it.accessType == AccessType.WRITE }
        }.getOrDefault(false)

    /** 写入一条心率到三星健康（无写权限返回 false）。 */
    suspend fun writeHeartRate(context: Context, bpm: Int, at: Instant): Boolean {
        if (!hasWritePermission(context)) {
            lastWriteError = ResText.of(R.string.s_cee11d83)
            return false
        }
        return runCatching {
            val offset = ZoneId.systemDefault().rules.getOffset(at)
            val point = HealthDataPoint.builder()
                .setStartTime(at.minusSeconds(30), offset)
                .setEndTime(at, offset)
                .addFieldData(DataType.HeartRateType.HEART_RATE, bpm.toFloat())
                .build()
            store(context).insertData(
                DataTypes.HEART_RATE.insertDataRequestBuilder.addData(point).build()
            )
            true
        }.getOrElse {
            storeRef = null
            lastWriteError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    /** 写入一条血压到三星健康（无写权限返回 false）。 */
    suspend fun writeBloodPressure(
        context: Context, sys: Int, dia: Int, pulse: Int?, at: Instant,
    ): Boolean {
        if (!hasWritePermission(context)) return false
        return runCatching {
            val offset = ZoneId.systemDefault().rules.getOffset(at)
            var b = HealthDataPoint.builder()
                .setStartTime(at.minusSeconds(30), offset)
                .setEndTime(at, offset)
                .addFieldData(DataType.BloodPressureType.SYSTOLIC, sys.toFloat())
                .addFieldData(DataType.BloodPressureType.DIASTOLIC, dia.toFloat())
            if (pulse != null && pulse > 0) {
                b = b.addFieldData(DataType.BloodPressureType.PULSE_RATE, pulse)
            }
            store(context).insertData(
                DataTypes.BLOOD_PRESSURE.insertDataRequestBuilder.addData(b.build()).build()
            )
            true
        }.getOrElse {
            storeRef = null
            false
        }
    }

    /** 写入一条血氧到三星健康（无写权限返回 false）。 */
    suspend fun writeOxygenSaturation(context: Context, percent: Int, at: Instant): Boolean {
        if (!hasWritePermission(context)) {
            lastWriteError = ResText.of(R.string.s_cee11d83)
            return false
        }
        return runCatching {
            val offset = ZoneId.systemDefault().rules.getOffset(at)
            val point = HealthDataPoint.builder()
                .setStartTime(at.minusSeconds(30), offset)
                .setEndTime(at, offset)
                .addFieldData(DataType.BloodOxygenType.OXYGEN_SATURATION, percent.toFloat())
                .build()
            store(context).insertData(
                DataTypes.BLOOD_OXYGEN.insertDataRequestBuilder.addData(point).build()
            )
            true
        }.getOrElse {
            storeRef = null
            lastWriteError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    /**
     * 把一条体成分写进三星健康。
     *
     * ## ⚠ 与 Health Connect 那边的结构**不同**
     *
     * HC 的体成分是「每类一条记录」（体脂一条、体重一条、身高一条）；
     * 三星的 `BodyComposition` 是**一条记录装所有字段**。
     * 所以这里**不能**照 HC 那套「按 kind 拆」的做法，只能把一条实体里
     * 所有非空字段一次写出去。
     *
     * ## ⚠ 字段的必填要求没有文档
     *
     * 官方只写了「Mandatory fields should be included in all the input data points」，
     * 没写**哪几个**是必填。所以：
     * · 只写**非空**的字段（拿不到就不写，不拿 0 顶上 —— 0 的体脂率是个有意义的值，
     *   会真的被当成一次测量）；
     * · 失败时把**错误原文**带出来（`lastWriteError`），界面显示它而不是「写了 0 条」。
     *   这一点与手表端那个传感器能力探测页是同一条纪律：
     *   **不确定的事就让用户看到原始错误，不要自己翻译成一句像是结论的话。**
     */
    suspend fun writeBodyComposition(
        context: Context,
        e: BodyCompositionEntity,
        at: Instant,
    ): Boolean {
        if (!hasWritePermission(context)) {
            lastWriteError = ResText.of(R.string.s_cee11d83)
            return false
        }
        return runCatching {
            val offset = ZoneId.systemDefault().rules.getOffset(at)
            var b = HealthDataPoint.builder()
                .setStartTime(at.minusSeconds(30), offset)
                .setEndTime(at, offset)
            e.bodyFatRatio?.let { b = b.addFieldData(DataType.BodyCompositionType.BODY_FAT, it) }
            e.bodyFatMass?.let {
                b = b.addFieldData(DataType.BodyCompositionType.BODY_FAT_MASS, it)
            }
            e.skeletalMuscleRatio?.let {
                b = b.addFieldData(DataType.BodyCompositionType.SKELETAL_MUSCLE, it)
            }
            e.skeletalMuscleMass?.let {
                b = b.addFieldData(DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS, it)
            }
            e.totalBodyWater?.let {
                b = b.addFieldData(DataType.BodyCompositionType.TOTAL_BODY_WATER, it)
            }
            // ⚠ 基础代谢率在三星侧是 **Int**（其它体成分字段是 Float）——
            //    这是编译器告诉我们的，文档没写。用 roundToInt 而不是 toInt：
            //    1250.6 kcal 截断成 1250 会往下偏，而这不是"精度取舍"，是白丢一位。
            e.basalMetabolicRate?.let {
                b = b.addFieldData(
                    DataType.BodyCompositionType.BASAL_METABOLIC_RATE, it.roundToInt(),
                )
            }
            e.fatFreeRatio?.let {
                b = b.addFieldData(DataType.BodyCompositionType.FAT_FREE, it)
            }
            e.fatFreeMass?.let {
                b = b.addFieldData(DataType.BodyCompositionType.FAT_FREE_MASS, it)
            }
            e.weightKg?.let { b = b.addFieldData(DataType.BodyCompositionType.WEIGHT, it) }
            e.heightCm?.let { b = b.addFieldData(DataType.BodyCompositionType.HEIGHT, it) }
            store(context).insertData(
                DataTypes.BODY_COMPOSITION.insertDataRequestBuilder.addData(b.build()).build()
            )
            true
        }.getOrElse {
            storeRef = null
            lastWriteError = ResText.raw("${it.javaClass.simpleName}: ${it.message}")
            false
        }
    }

    /** 中转写入：把手表同步来的记录写进三星健康（跳过三星来源，防止回环）。 */
    suspend fun relayWrite(context: Context, msg: SyncMessage) {
        relayWriteWithCount(context, msg)
    }

    /** 中转写入并返回成功条数；无写权限返回 -1。 */
    suspend fun relayWriteWithCount(context: Context, msg: SyncMessage): Int {
        if (!hasWritePermission(context)) {
            lastWriteError = ResText.of(R.string.s_cee11d83)
            return -1
        }
        var n = 0
        for (item in msg.items) {
            val at = Instant.ofEpochMilli(item.recordedAt)
            val ok = when (msg.type) {
                "hr" -> item.bpm?.let { writeHeartRate(context, it.toInt(), at) } ?: false
                "bp" -> {
                    val sys = item.systolic ?: continue
                    val dia = item.diastolic ?: continue
                    writeBloodPressure(context, sys, dia, item.pulse, at)
                }
                // 血氧与体成分（2026-09-24 补）：都是手表自己测的，回填给别人用是合理的。
                // ⚠ 仍然**不写**体温 —— 手表那份是腕部皮温，三星那个字段要的是核心体温。
                "spo2" -> item.spo2?.let { writeOxygenSaturation(context, it, at) } ?: false
                "bia" -> {
                    val b = item.bia ?: continue
                    writeBodyComposition(
                        context,
                        b.toEntity(
                            measuredAt = item.recordedAt,
                            source = com.gemini.healthbridge.core.BiaSource.WATCH,
                            synced = 1,
                        ),
                        at,
                    )
                }
                else -> false
            }
            if (ok) n++
        }
        return n
    }

    // ═══════════════════════ 三星侧回填（2026-09-24） ═══════════════════════

    /**
     * 三星侧某一类的回填结果。
     *
     * @param skippedFiltered 因为来源不是我们的、或值越界而没写
     * @param skippedExisted 因为该时刻**三星里已经有记录**而跳过（去重）
     * @param failed 三星拒收的条数（`lastWriteError` 里是最后一条的原始错误）
     */
    data class SamWriteResult(
        /** 类型名。**待求值**文案 —— 报告会存进 `remember`，存已求值的串会切语言后停在旧语言。 */
        val label: ResText,
        val written: Int,
        val skippedExisted: Int,
        val skippedFiltered: Int,
        val failed: Int,
    )

    /** 三星侧回填的汇总。 */
    data class SamBackfillReport(val items: List<SamWriteResult>, val error: ResText?) {
        val written: Int get() = items.sumOf { it.written }
        val failed: Int get() = items.sumOf { it.failed }
        val skipped: Int get() = items.sumOf { it.skippedExisted + it.skippedFiltered }
    }

    /**
     * 把手表测的**心率 / 血氧 / 体成分**补写进三星健康。
     *
     * ## ⚠⚠ 为什么这里的去重是「同刻已有记录就跳过」，而不是像 HC 那样的幂等键
     *
     * Health Connect 有 `clientRecordId`，可以用**稳定的 id** 精确判重。
     * 三星的 `insertData` **没有**这个概念（没有 id、没有 version），
     * 而且读回来的 `HealthDataPoint` **不带"谁写的"信息** ——
     * 我们无法区分"这条是我们上次写的"和"这条是三星自己测的"。
     *
     * 所以判据只能是「该时刻附近已有同类型记录 → 跳过」。
     * 这个判据的方向是**漏写而不是重复写**：极端情况下（用户恰好在同一分钟
     * 也在三星健康里记了一次）我们会少写一条，但**不会写出两条**。
     * 重复写的危害大得多 —— 用户看到翻倍的记录，却没有任何一方会报错。
     *
     * ## 只写手表自己测的
     *
     * 过滤走 `Backfill.isOurs`（与 HC 那边**同一个判据**，值域校验也用同一批函数）——
     * 判据只有一处定义，两边不会漂移。
     * ⚠ 刻意**不写体温**：三星那个字段的语义是核心体温，手表给的是腕部皮温。
     *
     * @param limit 每类最多看多少条本机记录（从最近的开始）。
     */
    suspend fun backfill(context: Context, db: AppDatabase, limit: Int = 200): SamBackfillReport {
        if (!hasWritePermission(context)) {
            lastWriteError = ResText.of(R.string.s_f153f993)
            return SamBackfillReport(emptyList(), lastWriteError)
        }
        val items = ArrayList<SamWriteResult>(3)

        // ① 心率
        run {
            val all = db.heartRateDao().recentOnce(limit)
            val keep = all.filter { Backfill.isOurs(it.source) && Backfill.isValidHr(it.bpm) }
            val filtered = all.size - keep.size
            if (keep.isEmpty()) {
                items.add(SamWriteResult(ResText.of(R.string.s_00317450), 0, 0, filtered, 0))
            } else {
                val existed = hrMoments(context, keep.minOf { it.measuredAt }, keep.maxOf { it.measuredAt })
                var w = 0
                var ex = 0
                var fail = 0
                for (e in keep) {
                    if (nearAny(e.measuredAt, existed)) { ex++; continue }
                    val ok = writeHeartRate(context, e.bpm.toInt(), Instant.ofEpochMilli(e.measuredAt))
                    if (ok) w++ else fail++
                }
                items.add(SamWriteResult(ResText.of(R.string.s_00317450), w, ex, filtered, fail))
            }
        }

        // ② 血氧
        run {
            val all = db.spo2Dao().recentOnce(limit)
            val keep = all.filter {
                Backfill.isOurs(it.source) && Backfill.isValidSpo2Percent(it.percent.toDouble())
            }
            val filtered = all.size - keep.size
            if (keep.isEmpty()) {
                items.add(SamWriteResult(ResText.of(R.string.s_004624e7), 0, 0, filtered, 0))
            } else {
                val existed = spo2Moments(context, keep.minOf { it.measuredAt }, keep.maxOf { it.measuredAt })
                var w = 0
                var ex = 0
                var fail = 0
                for (e in keep) {
                    if (nearAny(e.measuredAt, existed)) { ex++; continue }
                    val ok = writeOxygenSaturation(context, e.percent, Instant.ofEpochMilli(e.measuredAt))
                    if (ok) w++ else fail++
                }
                items.add(SamWriteResult(ResText.of(R.string.s_004624e7), w, ex, filtered, fail))
            }
        }

        // ③ 体成分
        //    ⚠ 与 HC 不同：三星这边是**一条记录装所有字段**，所以按"实体"逐条写，
        //      不能像 HC 那样按字段拆成 5 类。
        run {
            val all = db.bodyCompositionDao().recentOnce(limit)
            val keep = all.filter { Backfill.isOurs(it.source) && it.hasAnyField() }
            val filtered = all.size - keep.size
            if (keep.isEmpty()) {
                items.add(SamWriteResult(ResText.of(R.string.s_14f80321), 0, 0, filtered, 0))
            } else {
                val existed = biaMoments(context, keep.minOf { it.measuredAt }, keep.maxOf { it.measuredAt })
                var w = 0
                var ex = 0
                var fail = 0
                for (e in keep) {
                    if (nearAny(e.measuredAt, existed)) { ex++; continue }
                    val ok = writeBodyComposition(context, e, Instant.ofEpochMilli(e.measuredAt))
                    if (ok) w++ else fail++
                }
                items.add(SamWriteResult(ResText.of(R.string.s_14f80321), w, ex, filtered, fail))
            }
        }

        return SamBackfillReport(items, if (items.sumOf { it.failed } > 0) lastWriteError else null)
    }

    /** 三星健康里已有的**心率**时刻（用于回填去重，见 [backfill] 的说明）。 */
    private suspend fun hrMoments(context: Context, fromMs: Long, toMs: Long): Set<Long> =
        runCatching {
            val resp = store(context).readData(
                DataTypes.HEART_RATE.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(Instant.ofEpochMilli(fromMs - 60_000)))
                    .setLimit(MOMENT_SCAN_LIMIT)
                    .build()
            )
            resp.dataList.map { it.startTime.toEpochMilli() }
                .filter { it <= toMs + 60_000 }
                .toSet()
        }.getOrDefault(emptySet())

    /** 三星健康里已有的**血氧**时刻。 */
    private suspend fun spo2Moments(context: Context, fromMs: Long, toMs: Long): Set<Long> =
        runCatching {
            val resp = store(context).readData(
                DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(Instant.ofEpochMilli(fromMs - 60_000)))
                    .setLimit(MOMENT_SCAN_LIMIT)
                    .build()
            )
            resp.dataList.map { it.startTime.toEpochMilli() }
                .filter { it <= toMs + 60_000 }
                .toSet()
        }.getOrDefault(emptySet())

    /** 三星健康里已有的**体成分**时刻。 */
    private suspend fun biaMoments(context: Context, fromMs: Long, toMs: Long): Set<Long> =
        runCatching {
            val resp = store(context).readData(
                DataTypes.BODY_COMPOSITION.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(Instant.ofEpochMilli(fromMs - 60_000)))
                    .setLimit(MOMENT_SCAN_LIMIT)
                    .build()
            )
            resp.dataList.map { it.startTime.toEpochMilli() }
                .filter { it <= toMs + 60_000 }
                .toSet()
        }.getOrDefault(emptySet())

    /** 该时刻附近（±60 秒）是否已有记录。 */
    private fun nearAny(atMs: Long, existed: Set<Long>): Boolean =
        existed.any { kotlin.math.abs(it - atMs) < 60_000 }

    /** 一条体成分里至少有一个非空字段（全空的话写过去是条空记录，会被拒）。 */
    private fun BodyCompositionEntity.hasAnyField(): Boolean = listOf(
        bodyFatRatio, bodyFatMass, totalBodyWater, skeletalMuscleRatio, skeletalMuscleMass,
        basalMetabolicRate, fatFreeRatio, fatFreeMass, weightKg, heightCm,
    ).any { it != null }

    /** 去重时最多扫多少条已有记录（回填本身有 limit，这里够用）。 */
    private const val MOMENT_SCAN_LIMIT = 1000

    /** 昨晚睡眠时段（昨 22:00 ~ 今 08:00）的平均心率，供手表"睡眠平均值"显示。 */
    suspend fun readSleepAvg(context: Context): Int? {
        if (granted(context).none { it.accessType == AccessType.READ }) return null
        return runCatching {
            val startCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 20); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val winStart = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 22); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val winEnd = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            var token: String? = null
            val values = mutableListOf<Int>()
            do {
                var builder = DataTypes.HEART_RATE.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(startCal.toInstant()))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val t = p.startTime.toEpochMilli()
                    val f = p.getValueOrDefault(DataType.HeartRateType.HEART_RATE, 0f)
                    if (f > 0f && t in winStart until winEnd) values.add(f.toInt())
                }
                token = resp.pageToken
            } while (token != null)
            if (values.isEmpty()) null else values.average().toInt()
        }.getOrNull()
    }

    /** 是否已授权读取睡眠时段（心率页换色用）。 */
    suspend fun hasSleepPermission(context: Context): Boolean =
        grantedOrNull(context, setOf(sleepPermission))
            ?.any { it.dataType == DataTypes.SLEEP && it.accessType == AccessType.READ }
            ?: false

    /**
     * 同上，但连接失败时会重建 store 重试。
     * 冷启动瞬间的判定用它——否则一次连接失败会让「睡眠」页直接显示"未授权"。
     */
    suspend fun hasSleepPermissionWithRetry(context: Context, attempts: Int = 4): Boolean {
        repeat(attempts) { i ->
            val g = grantedOrNull(context, setOf(sleepPermission))
            if (g != null) {
                return g.any { it.dataType == DataTypes.SLEEP && it.accessType == AccessType.READ }
            }
            if (i < attempts - 1) delay(600L * (i + 1))
        }
        return false
    }

    // ─────────────────── 皮肤温度（体温页用） ───────────────────

    /** 是否已授权读取皮肤温度。 */
    suspend fun hasSkinTempPermission(context: Context): Boolean =
        grantedOrNull(context, setOf(skinTempPermission))
            ?.any { it.dataType == DataTypes.SKIN_TEMPERATURE && it.accessType == AccessType.READ }
            ?: false

    /** 同上，但连接失败时会重建 store 重试（冷启动判定用，与睡眠权限同一套纪律）。 */
    suspend fun hasSkinTempPermissionWithRetry(context: Context, attempts: Int = 4): Boolean {
        repeat(attempts) { i ->
            val g = grantedOrNull(context, setOf(skinTempPermission))
            if (g != null) {
                return g.any { it.dataType == DataTypes.SKIN_TEMPERATURE && it.accessType == AccessType.READ }
            }
            if (i < attempts - 1) delay(600L * (i + 1))
        }
        return false
    }

    /**
     * 取某时间窗内的皮肤温度序列（epoch 毫秒 → 原始值）。
     *
     * **原始值不做任何换算**，也不放进 `float` 之外的字段：因为三星健康这一项的口径
     * 与手表传感器完全不同（很可能是"相对个人基线的偏差"而不是绝对温度），
     * 换算/判定统一交给 `Temperature.scaleOf()`，避免在这里又埋一个"量纲假设"。
     *
     * 与血氧一样优先读 `SERIES_DATA`（睡眠期是分钟级序列，只读点级字段整晚只会得到一个值），
     * 拿不到才退回数据点自身的 `SKIN_TEMPERATURE`。
     */
    suspend fun readSkinTempBetween(
        context: Context, startMs: Long, endMs: Long,
    ): List<Pair<Long, Float>> {
        if (!hasSkinTempPermission(context)) return emptyList()
        return runCatching {
            val since = Instant.ofEpochMilli(startMs).minus(Duration.ofMinutes(5))
            val out = mutableListOf<Pair<Long, Float>>()
            var token: String? = null
            do {
                var builder = DataTypes.SKIN_TEMPERATURE.readDataRequestBuilder
                    // ⚠⚠ 上界必须写进**请求**里，不能只在下面 `t in startMs..endMs` 过滤：
                    //   只给下界的话，分页会一路读到"现在"才停 —— 补读 13 天前那一晚时，
                    //   一晚上要塞几十页才有机会收摊（回填 7~14 晚会慢到不可接受）。
                    //   台架里曾把这条写成注释「只有下界没有上界」，是**错的**：
                    //   `InstantTimeFilter.of(from, to)` 一直都在（已用 `javap` 从 SDK 核实）。
                    .setInstantTimeFilter(InstantTimeFilter.of(since, Instant.ofEpochMilli(endMs)))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val series = p.getValue(DataType.SkinTemperatureType.SERIES_DATA)
                    if (!series.isNullOrEmpty()) {
                        for (s in series) {
                            val t = s.startTime.toEpochMilli()
                            val v = s.skinTemperature
                            if (!v.isNaN() && t in startMs..endMs) out.add(t to v)
                        }
                    } else {
                        val t = p.startTime.toEpochMilli()
                        val v = p.getValueOrDefault(
                            DataType.SkinTemperatureType.SKIN_TEMPERATURE, Float.NaN,
                        )
                        if (!v.isNaN() && t in startMs..endMs) out.add(t to v)
                    }
                }
                token = resp.pageToken
            } while (token != null)
            out.sortedBy { it.first }
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    /**
     * 最近一晚的三星健康皮肤温度。
     *
     * 取自"昨天 18:00 → 现在"这个窗口（三星健康只在睡眠期间采集皮肤温度，
     * 所以这个窗口里的点就是最近一晚的），并算出均值与极值。
     *
     * @param samples 逐点原始值；界面画折线用
     */
    data class SkinTempNight(
        val startMs: Long,
        val endMs: Long,
        val avg: Float,
        val min: Float,
        val max: Float,
        val samples: List<Pair<Long, Float>>,
    )

    suspend fun readSkinTempNight(context: Context): SkinTempNight? {
        if (!hasSkinTempPermission(context)) return null
        val since = LocalDate.now().minusDays(1).atTime(18, 0)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val until = System.currentTimeMillis()
        val pts = readSkinTempBetween(context, since, until)
        if (pts.isEmpty()) return null
        return SkinTempNight(
            startMs = pts.first().first,
            endMs = pts.last().first,
            avg = pts.map { it.second }.average().toFloat(),
            min = pts.minOf { it.second },
            max = pts.maxOf { it.second },
            samples = pts,
        )
    }

    // ─────────────────── 体成分 / 用户档案（体成分页 + 手表 BIA） ───────────────────

    /**
     * 是否已授权读取三星健康的**体成分历史**。
     *
     * 走定点查询而不是 [granted]：见 [bodyCompositionPermission] 的注释 ——
     * 这一项绝不能参与那个"所有读取的总闸"。
     */
    suspend fun hasBodyCompositionPermission(context: Context): Boolean =
        grantedOrNull(context, setOf(bodyCompositionPermission))
            ?.any { it.dataType == DataTypes.BODY_COMPOSITION && it.accessType == AccessType.READ }
            ?: false

    /** 同上，但连接失败会重建 store 重试（冷启动判定用，与睡眠权限同一套纪律）。 */
    suspend fun hasBodyCompositionPermissionWithRetry(
        context: Context, attempts: Int = 4,
    ): Boolean {
        repeat(attempts) { i ->
            val g = grantedOrNull(context, setOf(bodyCompositionPermission))
            if (g != null) {
                return g.any {
                    it.dataType == DataTypes.BODY_COMPOSITION && it.accessType == AccessType.READ
                }
            }
            if (i < attempts - 1) delay(600L * (i + 1))
        }
        return false
    }

    /** 是否已授权读取**用户档案**（身高/体重/性别/生日）。手表测 BIA 的前提。 */
    suspend fun hasUserProfilePermission(context: Context): Boolean =
        grantedOrNull(context, setOf(userProfilePermission))
            ?.any { it.dataType == DataTypes.USER_PROFILE && it.accessType == AccessType.READ }
            ?: false

    /** 同上，带重试。 */
    suspend fun hasUserProfilePermissionWithRetry(context: Context, attempts: Int = 4): Boolean {
        repeat(attempts) { i ->
            val g = grantedOrNull(context, setOf(userProfilePermission))
            if (g != null) {
                return g.any {
                    it.dataType == DataTypes.USER_PROFILE && it.accessType == AccessType.READ
                }
            }
            if (i < attempts - 1) delay(600L * (i + 1))
        }
        return false
    }

    /**
     * 是否已授予能量评分读权限。
     *
     * ⚠ `grantedOrNull` 返回 null 表示**连不上服务**（不是"没授权"）。
     * 那种情况这里同样返回 false，所以**调用方不能只凭这个 false 就说"你没开这个功能"**
     * —— 还要看 [lastEnergyAuthError] 是不是有值。这是"读失败"与"没数据"必须分开的又一处。
     */
    suspend fun hasEnergyScorePermission(context: Context): Boolean =
        grantedOrNull(context, setOf(energyScorePermission))
            ?.any { it.dataType == DataTypes.ENERGY_SCORE && it.accessType == AccessType.READ }
            ?: false

    /** 是否已授予睡眠呼吸暂停读权限（注意事项同 [hasEnergyScorePermission]）。 */
    suspend fun hasSleepApneaPermission(context: Context): Boolean =
        grantedOrNull(context, setOf(sleepApneaPermission))
            ?.any { it.dataType == DataTypes.SLEEP_APNEA && it.accessType == AccessType.READ }
            ?: false

    /**
     * 读三星健康的用户档案，转成手表测 BIA 需要的 [BiaProfileDto]。
     *
     * 四项**缺一不可**：三星的 `getHealthTracker(BIA_ON_DEMAND, profile)` 要一整套
     * （身高/体重/性别/年龄），少一项就取不到 tracker。所以任何一项拿不到就返回 `null` ——
     * 手表端据此明确提示「请先在手机端同步资料」，而**不是**拿一份编造的默认资料去测：
     * 那会得到一条看起来完全正常的错误结果，比直接报错难发现得多。
     *
     * 年龄由生日算得。三星给的是 `DATE_OF_BIRTH` **字符串**，格式在不同版本/地区不完全统一
     * （见过 `yyyy-MM-dd` / `yyyyMMdd` / `yyyy/MM/dd`），所以逐个格式尝试；
     * 全都解析不出来就返回 null 让整份资料作废 —— 不猜一个"大概的年龄"。
     */
    suspend fun readBiaProfile(context: Context): BiaProfileDto? {
        if (!hasUserProfilePermission(context)) return null
        return runCatching {
            val resp = store(context)
                .readData(DataTypes.USER_PROFILE.readDataRequestBuilder.build())
            val p = resp.dataList.firstOrNull() ?: return@runCatching null

            val h = p.getValueOrDefault(DataType.UserProfileDataType.HEIGHT, 0f)
            val w = p.getValueOrDefault(DataType.UserProfileDataType.WEIGHT, 0f)
            val gender = when (
                p.getValueOrDefault(
                    DataType.UserProfileDataType.GENDER,
                    DataType.UserProfileDataType.Gender.GENDER_UNKNOWN,
                )
            ) {
                DataType.UserProfileDataType.Gender.GENDER_MALE ->
                    com.gemini.healthbridge.core.BodyComposition.GENDER_MALE
                DataType.UserProfileDataType.Gender.GENDER_FEMALE ->
                    com.gemini.healthbridge.core.BodyComposition.GENDER_FEMALE
                // GENDER_UNKNOWN：三星自己也不知道 → 不能替它猜，整份资料作废
                else -> null
            } ?: return@runCatching null

            val birth = (p.getValueOrDefault(DataType.UserProfileDataType.DATE_OF_BIRTH, "") ?: "")
            val age = ageFromBirthDate(birth) ?: return@runCatching null

            BiaProfileDto(heightCm = h, weightKg = w, gender = gender, age = age)
                .takeIf { it.isValid() }
        }.getOrElse {
            storeRef = null
            null
        }
    }

    /** 生日字符串 → 周岁。三种已知格式都试一遍，都不成才返回 null（不猜）。 */
    private fun ageFromBirthDate(s: String): Int? {
        val raw = s.trim()
        if (raw.isEmpty()) return null
        val date = listOf("yyyy-MM-dd", "yyyyMMdd", "yyyy/MM/dd").firstNotNullOfOrNull { pat ->
            runCatching {
                LocalDate.parse(raw, java.time.format.DateTimeFormatter.ofPattern(pat))
            }.getOrNull()
        } ?: return null
        val years = java.time.Period.between(date, LocalDate.now()).years
        return years.takeIf { it in 0..150 }
    }

    /**
     * 取某时间窗内的体成分记录（已带 [SOURCE_SAMSUNG]，可直接批量落库）。
     *
     * 三星的体成分很稀疏（一次测量就是一条），所以窗口给得宽（默认 30 天，与血压同理）。
     *
     * **阻抗读不到**：三星健康的 `BODY_COMPOSITION` 字段表里没有阻抗，
     * 只有体脂/骨骼肌/体水/基础代谢这些**成品值**。手表 BIA 那条才有阻抗，
     * 所以列表里三星来源的行阻抗列显示 "——" 属正常，不是数据缺了。
     *
     * 落库条件：体脂率与骨骼肌量**至少有一个非空**。只有体重/身高这种档案字段的数据点
     * 一律跳过 —— 否则记录列表里会出现一行全是 "——" 的条目。
     */
    suspend fun readBodyCompositionBetween(
        context: Context, startMs: Long, endMs: Long,
    ): List<BodyCompositionEntity> {
        if (!hasBodyCompositionPermission(context)) return emptyList()
        return runCatching {
            val since = Instant.ofEpochMilli(startMs).minus(Duration.ofMinutes(5))
            val out = mutableListOf<BodyCompositionEntity>()
            var token: String? = null
            do {
                var builder = DataTypes.BODY_COMPOSITION.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(since))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val t = p.startTime.toEpochMilli()
                    if (t < startMs || t > endMs) continue
                    val fat = p.getValue(DataType.BodyCompositionType.BODY_FAT)
                    val skmMass = p.getValue(DataType.BodyCompositionType.SKELETAL_MUSCLE_MASS)
                    if (fat == null && skmMass == null) continue
                    out.add(
                        BodyCompositionEntity(
                            bodyFatRatio = fat,
                            bodyFatMass = p.getValue(DataType.BodyCompositionType.BODY_FAT_MASS),
                            totalBodyWater = p.getValue(
                                DataType.BodyCompositionType.TOTAL_BODY_WATER
                            ),
                            skeletalMuscleRatio = p.getValue(
                                DataType.BodyCompositionType.SKELETAL_MUSCLE
                            ),
                            skeletalMuscleMass = skmMass,
                            // 三星这一项是 Int（kcal），实体是 Float?，转一下即可
                            basalMetabolicRate = p.getValue(
                                DataType.BodyCompositionType.BASAL_METABOLIC_RATE
                            )?.toFloat(),
                            fatFreeRatio = p.getValue(DataType.BodyCompositionType.FAT_FREE),
                            fatFreeMass = p.getValue(DataType.BodyCompositionType.FAT_FREE_MASS),
                            // 三星不给阻抗（字段表里就没有），留空而不是填 0
                            impedanceMagnitude = null,
                            impedanceDegree = null,
                            // 体重/身高顺带当"资料快照"存下，与手表来源的行保持一致的口径
                            weightKg = p.getValue(DataType.BodyCompositionType.WEIGHT)
                                ?.takeIf { it > 0f },
                            heightCm = p.getValue(DataType.BodyCompositionType.HEIGHT)
                                ?.takeIf { it > 0f },
                            gender = null,
                            age = null,
                            measuredAt = t,
                            source = BiaSource.SAMSUNG,
                        )
                    )
                }
                token = resp.pageToken
            } while (token != null)
            out
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    /**
     * 读取昨晚 18:00 至今的睡眠时段（`startMs to endMs`，epoch 毫秒），
     * 供心率页把睡眠小时画成另一种颜色、并单独统计睡眠心率。
     *
     * 没有授权、或三星健康里没有睡眠记录时返回**空列表** —— 调用方据此回退到
     * 固定的夜间窗口（23:00~07:00）。这个回退是必须的：睡眠数据依赖用户额外授权，
     * 拿不到时图表仍要能画出来，只是"睡眠分区"变成估算。
     */
    suspend fun readSleepSpans(context: Context): List<Pair<Long, Long>> {
        if (!hasSleepPermission(context)) return emptyList()
        return runCatching {
            val sinceLdt = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, 18); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
            val out = mutableListOf<Pair<Long, Long>>()
            var token: String? = null
            do {
                var builder = DataTypes.SLEEP.readDataRequestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.since(sinceLdt))
                    .setOrdering(Ordering.DESC)
                    .setLimit(50)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val s = p.startTime.toEpochMilli()
                    // endTime 是 Instant?（区间型数据点才带结束时间），没有就跳过这条
                    val end = p.endTime?.toEpochMilli() ?: continue
                    if (end > s) out.add(s to end)
                }
                token = resp.pageToken
            } while (token != null)
            out
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    /** 读取三星健康当日心率，按小时聚合（推送给手表绘制全天图表）。 */
    suspend fun readTodayHourly(context: Context): List<HourBucket> {
        if (granted(context).none { it.accessType == AccessType.READ }) return emptyList()
        return runCatching {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val since = cal.toInstant()
            val points = mutableListOf<HealthDataPoint>()
            var token: String? = null
            do {
                var builder = DataTypes.HEART_RATE.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(since))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                points.addAll(resp.dataList)
                token = resp.pageToken
            } while (token != null)

            points.groupBy {
                val c = Calendar.getInstance(); c.timeInMillis = it.startTime.toEpochMilli()
                c.get(Calendar.HOUR_OF_DAY)
            }.map { (h, items) ->
                val v = items.mapNotNull { p ->
                    val f = p.getValueOrDefault(DataType.HeartRateType.HEART_RATE, 0f)
                    if (f > 0f) f.toInt() else null
                }
                if (v.isEmpty()) null
                else HourBucket(h, v.min(), v.max(), v.average().toInt())
            }.filterNotNull().sortedBy { it.hour }
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    // ───────────────── 自动同步（一次读齐并落库） ─────────────────

    /** 一次自动同步的结果：界面要的睡眠/血氧快照 + 本次新落库的条数。 */
    data class AutoSyncResult(
        /** 是否拿到了三星健康读权限；false 时其余字段全空。 */
        val authorized: Boolean,
        /** 权限状态**无法确定**（连不上三星健康）—— 与"确实未授权"要分开提示，别催用户重新授权。 */
        val undetermined: Boolean = false,
        val night: SleepNightDto? = null,
        val spo2Night: List<Pair<Long, Int>> = emptyList(),
        val hrNight: List<Pair<Long, Int>> = emptyList(),
        val spo2Today: List<Pair<Long, Int>> = emptyList(),
        /**
         * 同一窗口里**除了主睡眠之外**的睡眠会话（小睡），2026-09-29 加。
         *
         * 主睡眠按**时长**挑（`SleepHours.mainSession`），小睡不计入任何统计 ——
         * 界面只是把它们单列出来，否则用户看到"只显示了午睡、晚上那段不见了"。
         */
        val naps: List<SleepHours.Span> = emptyList(),
        val cachedHr: Int = 0,
        val cachedSpo2: Int = 0,
        val cachedBp: Int = 0,
        /** 最近一晚的三星健康皮肤温度（拿不到权限/没有数据时为 null）。 */
        val skinTempNight: SkinTempNight? = null,
        /** 本次新落的体温记录条数（含三星夜间那一行）。 */
        val cachedTemp: Int = 0,
        /** 本次新落的体成分记录条数（三星健康自己的体成分历史）。 */
        val cachedBia: Int = 0,
        val note: String = "",
    ) {
        val hasNightData: Boolean get() = night != null || spo2Today.isNotEmpty()
    }

    /**
     * 自动同步：把三星健康里的六类数据（心率 / 血氧 / 血压 / 睡眠 / 皮肤温度 / 体成分）
     * **一次读齐并落库**。
     *
     * 这是"打开就有数据、不用手动导入"的唯一入口。它同时修掉三个历史问题：
     *  ① 原来每类数据各页面自己读：血氧/睡眠直连三星健康，心率读本地库（而本地库
     *     只有手动「导入三星健康」才会写）→ 观感就是"血氧睡眠能自动同步、心率必须手动导入"。
     *     现在统一走这里，几类一起。
     *  ② 读到的数据以前只存在内存里画图、从不落库 → 下次读取一失败页面就空白，
     *     看起来像"记录丢了"。现在写进 Room，页面先显示缓存、后台再刷新。
     *  ③ 血氧序列原先寄生在睡眠读取里（`readSleepBundle` 没有睡眠权限就整包返回空），
     *     于是"没授权睡眠"会连带把血氧页也清空。现在解耦：血氧只依赖血氧权限。
     *
     * 落库统一带 [SOURCE_SAMSUNG] 且 `(source, measuredAt)` 是唯一索引，
     * 所以反复调用是幂等的，不会产生重复行。
     */
    suspend fun syncToday(context: Context, bpDays: Long = 30): AutoSyncResult {
        val g = grantedWithRetry(context)
            ?: return AutoSyncResult(
                authorized = false, undetermined = true, note = LanguageStore.string(context, R.string.s_861b900f)
            )
        if (g.none { it.accessType == AccessType.READ }) {
            return AutoSyncResult(authorized = false, note = LanguageStore.string(context, R.string.s_75a9e7d5))
        }
        markAuthorizedOnce(context)

        val now = System.currentTimeMillis()
        val day0 = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // ① 睡眠（含睡眠期的血氧/心率序列）。没有睡眠权限时返回 EMPTY，不影响下面几步。
        val bundle = runCatching { readSleepBundle(context) }.getOrDefault(SleepBundle.EMPTY)

        // ② 当日血氧序列。有睡眠权限时第 ① 步已经顺手带回来了（同一次分页读出的两个时间窗），
        //    就不再读第二遍；没有睡眠权限才单独读一趟。
        val spo2Today = if (bundle.spo2Today.isNotEmpty()) bundle.spo2Today
        else runCatching { readSpo2SeriesBetween(context, day0, now) }.getOrDefault(emptyList())

        // ③ 当日心率序列。起点取「今天 0 点」与「昨晚入睡时刻」里更早的那个 ——
        //    凌晨的睡眠心率归属昨天，只从 0 点读会把整段睡眠的开头截掉。
        val hrSince = minOf(day0, bundle.night?.startMs ?: day0)
        val hrToday = runCatching { readHrSeriesBetween(context, hrSince, now) }
            .getOrDefault(emptyList())

        // ④ 近期血压。血压在三星健康里很稀疏（一次读 30 天也就几条到几十条）。
        val bp = runCatching { readBloodPressureBetween(context, now - bpDays * 86_400_000L, now) }
            .getOrDefault(emptyList())

        // ⑤ 落库（批量事务：一晚血氧是每分钟一个点，逐条写就是几百次事务）
        val db = SyncRepository.database
        val cachedHr = runCatching {
            db.heartRateDao().insertAll(
                hrToday.map {
                    HeartRateEntity(
                        bpm = it.second.toDouble(), measuredAt = it.first, source = SOURCE_SAMSUNG
                    )
                }
            ).count { it > 0 }
        }.getOrDefault(0)
        // 血氧落库只存**点级**记录（readSpo2Records），刻意不存睡眠期那种"每分钟一个点"的序列：
        // 一晚 400+ 条会把「最近血氧记录」列表整个刷屏，真记录被挤到 200 条之外；
        // 而序列本身只是画图的素材，每次同步现读即可，没有落库的必要。
        val cachedSpo2 = runCatching {
            db.spo2Dao().insertAll(
                readSpo2Records(context, hours = 48).map {
                    SpO2Entity(percent = it.pct, measuredAt = it.t, source = SOURCE_SAMSUNG)
                }
            ).count { it > 0 }
        }.getOrDefault(0)
        val cachedBp = runCatching {
            db.bloodPressureDao().insertAll(bp).count { it > 0 }
        }.getOrDefault(0)

        // ⑥ 最近一晚的三星健康皮肤温度。
        //    三星只在睡眠期采集，而且很可能是"相对个人基线的偏差"（±0.x ℃），
        //    所以**只落一行"整晚均值"**，不落那几十上百个序列点：
        //      · 落序列会把「最近记录」刷屏（与血氧那条判断同理）；
        //      · 曲线的素材每次同步现读即可，没有落库的必要。
        //    口径判定交给 Temperature.scaleOf，这里不假设它是绝对值。
        //    measuredAt 取**夜间结束时刻**而不是"现在"：这样同一次同步反复执行时
        //    时间戳是稳定的，`(source, measuredAt)` 的去重才真的幂等。
        val skinNight = runCatching { readSkinTempNight(context) }.getOrNull()
        val cachedTemp = if (skinNight != null) {
            runCatching {
                db.temperatureDao().insert(
                    TemperatureEntity(
                        celsius = skinNight.avg,
                        ambientC = null,
                        measuredAt = skinNight.endMs,
                        source = com.gemini.healthbridge.core.TempSource.SAMSUNG_SKIN,
                    )
                )
            }.getOrDefault(0L).let { if (it > 0) 1 else 0 }
        } else 0

        // ⑦ 三星健康自己的体成分历史（近 30 天，与血压同一档稀疏度：一次测量就是一条）。
        //    与手表 BIA 的测量结果**按来源分开存**（BiaSource.SAMSUNG vs BiaSource.WATCH）：
        //    两者的算法与标定不同，混在一条曲线/一个列表里会被读成"同一台仪器的趋势"。
        //    没有体成分读权限时返回空列表，`count { it > 0 }` 自然是 0，不影响其它任何一步。
        val cachedBia = runCatching {
            db.bodyCompositionDao().insertAll(
                readBodyCompositionBetween(context, now - 30 * 86_400_000L, now)
            ).count { it > 0 }
        }.getOrDefault(0)

        // ⑧ 夜间氧减指数 ODI（模块 #11 的替代落点）。
        //    **这一条必须落库**：它的输入是三星夜间血氧的分钟级序列，而那个序列刻意不落库
        //    （一晚 400+ 点会把「最近血氧记录」刷屏）。不存摘要 = 这个数只活在内存里，
        //    下次同步一失败就永远消失，而且**过了今晚就再也取不回来**（读取入口只取"昨晚"）。
        //    只在真的拿到序列时才写：空的读取结果不许覆盖掉已有的结论（与睡眠同一条纪律）。
        val odiOk = runCatching {
            val night = bundle.night
            if (night != null && bundle.spo2.isNotEmpty()) {
                val res = Odi.analyze(bundle.spo2.map { Odi.Sample(it.first, it.second) })
                val day = java.time.LocalDate.now().toEpochDay()
                db.sleepOdiDao().insert(
                    when (res) {
                        is Odi.Result.Ok -> SleepOdiEntity(
                            dateEpochDay = day, startMs = night.startMs, endMs = night.endMs,
                            events = res.events, odiPerHour = res.odiPerHour,
                            monitoredMinutes = res.monitoredMinutes, validPoints = res.validPoints,
                            rejectedPoints = res.rejectedPoints, lowest = res.lowest,
                            mean = res.mean, t90Pct = res.t90Pct, computedAt = now,
                        )
                        is Odi.Result.NotApplicable -> SleepOdiEntity(
                            // 指标列全为 null + reason：明确区分"没算出来"与"算出 0 次"
                            dateEpochDay = day, startMs = night.startMs, endMs = night.endMs,
                            reason = res.reason, computedAt = now,
                        )
                    }
                )
                // 摘要是派生的，长期堆积没有意义 —— 只留最近 30 晚
                db.sleepOdiDao().keepLatest(30)
                true
            } else false
        }.getOrDefault(false)

        // ⑧.5 每晚睡眠统计落库（自研能量评分的输入）。
        //    **与 ODI 的顺序无关**，但故意紧挨着它放：两者都以"最近一晚"为对象、
        //    都用"醒来那天"做归日，放在一起才看得出它们必须口径一致。
        //
        //    为什么必须落库：`readSleepBundle` 只读"最近一晚"（窗口从昨天 18:00 起），
        //    **过了今晚就再也读不回来** —— 睡眠时长这种历史一旦不存，
        //    自研能量评分永远只能算今天这一晚，基线与趋势都无从谈起。
        //    只存统计行（时长 + 心率/血氧统计），不存阶段段与序列（与血氧/皮温同一条判断）。
        //
        //    拿不到 `night` 时**什么都不写**：空读取不许覆盖已有的那一晚。
        val nightOk = runCatching {
            val night = bundle.night ?: return@runCatching false
            // 归日口径与 `sleep_odi` 用**同一个表达式**，保证两张卡永远指向同一天。
            // （读取窗口决定了这个会话必然落在 [昨天18:00, 现在] 内，
            //   而"会话结束时刻所在的那一天"与"同步运行的那一天"在窗口约束下是同一值。）
            val day = java.time.LocalDate.now().toEpochDay()
            db.sleepNightDao().insertAll(
                listOf(
                    SleepNightEntity(
                        dateEpochDay = day,
                        startMs = night.startMs,
                        endMs = night.endMs,
                        actualSleepMs = night.actualSleepMs(),
                        hrAvg = night.hrAvg,
                        hrMin = night.hrMin,
                        hrSd = bundle.hrSd,
                        skinTemp = skinNight?.avg,
                        spo2Avg = night.spo2Avg,
                        spo2Min = night.spo2Min,
                        samsungScore = night.score,
                        // 睡眠阶段段（2026-10-05 起落库）。
                        // ⚠ 必须在这里存：`readSleepBundle` 的窗口是 [昨天18:00, 现在]，
                        //   **过了今晚就再也读不回来** ⇒ 不存 = 历史趋势永远没有素材。
                        // 判据与上面的 `sleep_odi` 同源（"过期即永失"的输入必须落库）。
                        // ⚠ `encode` 对空列表返回 **null**，这里**不能**用 `?: ""` 兜成空串 ——
                        //   空串会让下游分不清"没存过"与"那晚没有阶段"（见实体的 KDoc）。
                        stagesJson = SleepStages.encode(night.stages, night.startMs),
                        recordedAt = now,
                    )
                )
            )
            // 一晚一行，留一年多足够算基线与趋势；再多了没有意义（没有人回头看两年前的睡眠）
            db.sleepNightDao().keepLatest(400)
            true
        }.getOrDefault(false)

        // ⑧.7 睡眠历史回填（2026-10-09 新增）。
        //      第 ① 步的 `readSleepBundle` **只读"最近一晚"**（窗口写死 [昨天18:00, 现在]），
        //      于是"那天没打开过 App" ⇒ 那一晚**永久缺失**，之后再也读不回来。
        //      这里按 `dateEpochDay` 逐天往前补齐。与第 ⑧.5 步是**同一张表、同一套归日口径**，
        //      所以紧挨着它放（改归日口径时两处必须一起改）。
        //      读不到就什么都不写（见 `syncSleepDays`），全部失败也不影响上面任何一步。
        val cachedSleepDays = runCatching { syncSleepDays(context) }.getOrDefault(0)

        // ⑧.8 按日的每小时心率/血氧桶回填（2026-10-13 新增，DB v22）。
        //      ⚠ 与第 ⑧.7 步是**同一个思路的另一半**：睡眠那 14 晚早就落本地了，
        //        而心率/血氧一直没有 —— 于是历史页只能现读三星。
        //      用户明确要求「每天同步后数据留在本地，访问历史先从本地读」。
        //      已有那天的行就跳过 ⇒ 稳态下每次只读 1 天（首次会把 14 天补齐）。
        val cachedDailyVitals = runCatching { syncDailyVitals(context) }.getOrDefault(0)

        // ⑩ 最近几天的活动量（能量评分里「前一日的活动」「活动持续性」两项的输入）。
        //
        //     放在这里而不是各页面各读一次：聚合能读历史，所以**第一次运行会把最近 7 天一起补齐**，
        //     之后每天只重读昨天（三星常在次日回填修正）加补缺的天数。
        //     全部失败也不影响上面任何一步 —— 评分那边会因为缺输入而让那两项缺席，
        //     而不是拿 0 顶上。
        val cachedActivityDays = runCatching { syncActivityDays(context) }.getOrDefault(0)

        // ⑨ 能量评分（三星每日算一次）。
        //    写入用 REPLACE：同一天三星会重算，重算后的值应当**覆盖**旧值。
        //    读不到时返回空列表 → **一条都不写**（不许拿空去覆盖已有那几天）。
        val energyOk = runCatching {
            val rows = readEnergyScores(context, days = 30)
            if (rows.isEmpty()) 0
            else {
                val n = db.energyScoreDao().insertAll(rows).count { it > 0 }
                db.energyScoreDao().keepLatest(90)
                n
            }
        }.getOrDefault(0)

        // ⑩ 睡眠呼吸暂停检测记录（来源是「三星健康监测器」）。
        //    同样：读不到就一条都不写。这两个在多数机器/地区根本没数据，
        //    「没数据」必须在界面上说清原因，而不是显示成 0 或空白。
        val apneaOk = runCatching {
            val rows = readSleepApnea(context, days = 180)
            if (rows.isEmpty()) 0
            else {
                val n = db.sleepApneaDao().insertAll(rows).count { it > 0 }
                db.sleepApneaDao().keepLatest(60)
                n
            }
        }.getOrDefault(0)

        // ⑪ 记下"今天同步过了"——供功能页那张卡显示「今日已同步」。
        //    放在最后：这一步之前所有读取都已经跑过（各自容忍失败），
        //    所以"走到这里"就等于"今天确实同步过一次"。
        runCatching { markSyncedToday(context) }

        return AutoSyncResult(
            authorized = true,
            night = bundle.night,
            spo2Night = bundle.spo2,
            hrNight = bundle.hr,
            spo2Today = spo2Today,
            // 小睡跟着 `night` 一起传出（2026-09-29）：界面要单列「另外还有 N 段小睡」，
            // 漏了这一处不会报错，只是睡眠页永远看不到小睡。
            naps = bundle.naps,
            cachedHr = cachedHr,
            cachedSpo2 = cachedSpo2,
            cachedBp = cachedBp,
            skinTempNight = skinNight,
            cachedTemp = cachedTemp,
            cachedBia = cachedBia,
            note = LanguageStore.string(context, R.string.s_ab97e667, cachedHr, cachedSpo2, cachedBp) +
                (if (cachedTemp > 0) LanguageStore.string(context, R.string.s_ed42e755) else "") +
                (if (cachedSleepDays > 0) LanguageStore.string(context, R.string.s_d31b97c8, cachedSleepDays) else "") +
                (if (cachedActivityDays > 0) LanguageStore.string(context, R.string.s_ea0e1887, cachedActivityDays) else "") +
                (if (cachedBia > 0) LanguageStore.string(context, R.string.s_457d3124, cachedBia) else "") +
                (if (energyOk > 0) LanguageStore.string(context, R.string.s_dc0024c5, energyOk) else "") +
                (if (apneaOk > 0) LanguageStore.string(context, R.string.s_96d390b4, apneaOk) else "") +
                (if (odiOk) LanguageStore.string(context, R.string.s_d1976883) else ""),
        )
    }

    /**
     * 取某时间窗内的血压记录（已带好 [SOURCE_SAMSUNG] 标记，可直接批量落库）。
     *
     * 本地仍再过滤一次上界（`t > endMs` 就丢）：请求里已经写了上界
     * （`InstantTimeFilter.of(since, endMs)`，2026-10-09 核实过这个重载一直存在），
     * 但**本地这一道不能删** —— 它同时兜住"SDK 的实现改动"与"区间端点是否含端点"。
     * ⚠ 原来这里写的是「`since` 只有"不早于"、没有上界」，**那句话是错的**；
     *   只给下界的真正代价是**分页一路读到"现在"**（补历史时慢到不可接受），
     *   见 `readSkinTempBetween` 的注释。
     */
    suspend fun readBloodPressureBetween(
        context: Context, startMs: Long, endMs: Long,
    ): List<BloodPressureEntity> {
        if (granted(context).none { it.accessType == AccessType.READ }) return emptyList()
        return runCatching {
            val since = Instant.ofEpochMilli(startMs).minus(Duration.ofMinutes(5))
            val out = mutableListOf<BloodPressureEntity>()
            var token: String? = null
            do {
                var builder = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(since))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val sys = p.getValueOrDefault(DataType.BloodPressureType.SYSTOLIC, 0f)
                    val dia = p.getValueOrDefault(DataType.BloodPressureType.DIASTOLIC, 0f)
                    if (sys <= 0f || dia <= 0f) continue
                    val t = p.startTime.toEpochMilli()
                    if (t < startMs || t > endMs) continue
                    val pulse = p.getValueOrDefault(DataType.BloodPressureType.PULSE_RATE, 0)
                    out.add(
                        BloodPressureEntity(
                            systolic = sys.toInt(),
                            diastolic = dia.toInt(),
                            pulse = pulse.takeIf { it > 0 },
                            recordedAt = t,
                            source = SOURCE_SAMSUNG,
                        )
                    )
                }
                token = resp.pageToken
            } while (token != null)
            out
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    // ───────────────────────── 睡眠 ─────────────────────────

    /**
     * 读取最近一晚的完整睡眠结构：会话区间、合并后的阶段段、睡眠得分，
     * 以及睡眠期的心率/血氧统计。
     *
     * 取"最近一晚"的口径：只看昨晚 18:00 至今的 SLEEP 数据点，取其中 `endTime` 最晚的那个会话。
     * 不用"今天 0 点起"是因为睡眠是跨零点的 —— 02:05~07:42 这种会话起始于昨天，
     * 用自然日切会把一整晚切碎甚至整晚丢掉。
     *
     * 没有睡眠读权限、或三星健康里没有睡眠记录时返回 null（界面据此区分"没授权/没数据"）。
     */
    // ═══════════ 扩展指标（2026-09-23）：本 SDK 的三种取数方式 ═══════════
    //
    // | 方式 | 入口 | 类型特征 | 这里的实现 |
    // |---|---|---|---|
    // | 时间序列 | `readData` | 实现 `DataType.Readable<…, DualTimeBuilder>` | [readSeries] |
    // | 聚合     | `aggregateData` | **只**暴露 `AggregateOperation` 常量 | [aggregate] |
    // | 写入     | `insertData` | 实现 `DataType.Writeable` | **本文件没做** |
    //
    // ⚠ 最容易踩的一点：`Steps` / `ActivitySummary` / 六类 Goal **没有** `readDataRequestBuilder`
    // （它们 `extends DataType` 就完了）。第一次看到时会以为"这类型不可用"，
    // 其实是**支持另一种取数方式** —— 走聚合。别把它们当成不支持。
    //
    // 这一段**不落库**：三星这些数据都能按时间范围重读，不存在"过期即永失"，
    // 所以不需要表、不需要迁移、也不进备份（对比 `sleep_odi` / `sleep_nights`：
    // 那两张表必须落库是因为**读取入口只取"最近一晚"**（`readSleepBundle` 的窗口写死
    // [昨天18:00, 现在]）—— 但 SDS 本身**能按区间读历史**（`DataTypes.SLEEP` 是
    // `LocalTimeBuilder`），所以 2026-10-09 给睡眠加了 `syncSleepDays` 历史回填：
    // "必须落库"的是**每晚的统计结果**（阶段段/得分/心率统计这些读回来就要当场算的量），
    // 不是"读不到历史"。

    /**
     * 权限查询的**短时缓存**（2 秒）。
     *
     * 为什么需要：进一次页面要为十几个类型各问一句"授没授权"，而
     * `getGrantedPermissions` 是一次**跨进程调用** —— 十几次叠加就是几百毫秒的纯等待，
     * 用户感觉到的是"这一页打开要卡一下"。
     *
     * 2 秒的窗口足够覆盖"一次页面加载"，又短到不会让用户"刚在设置里授了权、回来还不生效"。
     */
    private var permCacheAt = 0L
    private var permCacheFor: Set<Permission> = emptySet()
    private var permCacheVal: Set<Permission> = emptySet()

    private suspend fun grantedMemo(context: Context, perms: Set<Permission>): Set<Permission> {
        val now = System.currentTimeMillis()
        if (now - permCacheAt < 2_000L && permCacheFor.containsAll(perms)) return permCacheVal
        val g = grantedOrNull(context, perms) ?: emptySet()
        permCacheAt = now
        permCacheFor = perms
        permCacheVal = g
        return g
    }

    /** 某个类型是否已授予读权限。 */
    private suspend fun hasReadPermission(context: Context, type: DataType): Boolean {
        val p = Permission.of(type, AccessType.READ)
        return grantedMemo(context, setOf(p)).any {
            it.dataType == type && it.accessType == AccessType.READ
        }
    }

    /**
     * 扩展指标读取的**统一外壳**：权限 → 读 → 记失败原因。
     *
     * 这三件事必须绑在一起做。单独写十几遍的话，总有一遍会忘了记错误 ——
     * 而那就正好复现本项目最忌讳的那个 bug：**分不清"读失败"与"没数据"**
     * （`readEnergyScores` 此前就是这么错的：异常被 `runCatching` 吞掉，
     * 界面于是把"读取抛异常"说成"三星还没替你算过分数"，把用户指向了错误方向）。
     *
     * @param empty 读不到时返回什么（空列表 / 全空的 DTO）。调用方**不能**拿 0 当空。
     */
    private suspend fun <R> readExtra(
        context: Context,
        id: String,
        type: DataType,
        empty: R,
        block: suspend () -> R,
    ): R {
        extraReadErrors.remove(id)
        if (!hasReadPermission(context, type)) {
            // 没权限 = **没走到读取这一步**，不能记成"读取失败"。
            // 只在没有更具体的原因时才写一句兜底（`request()` 阶段记下的异常更有价值）。
            extraAuthErrors.putIfAbsent(id, LanguageStore.string(context, R.string.s_a23b99d2))
            return empty
        }
        return runCatching { block() }.fold(
            onSuccess = { it },
            onFailure = { e ->
                extraReadErrors[id] = e.brief()
                // 连接可能已经失效（三星健康升级/被卸载），丢掉缓存下次重连
                storeRef = null
                empty
            },
        )
    }

    /**
     * 通用的**时间序列**读取（7 个区间型类型共用一份实现）。
     *
     * 参数类型写成 `DataType.Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>`
     * 是有意的：这些类型的 `readDataRequestBuilder` 都是同一个 `DualTimeBuilder`，
     * 所以一份实现能全覆盖；而**每个类型的权限仍然各自单独申请**。
     */
    private suspend fun readSeries(
        context: Context,
        type: DataType.Readable<HealthDataPoint, ReadDataRequest.DualTimeBuilder<HealthDataPoint>>,
        since: Instant,
        limit: Int = 200,
    ): List<HealthDataPoint> {
        val out = mutableListOf<HealthDataPoint>()
        var token: String? = null
        do {
            var b = type.readDataRequestBuilder
                .setInstantTimeFilter(InstantTimeFilter.since(since))
                .setOrdering(Ordering.DESC)
                .setLimit(limit)
            if (token != null) b = b.setPageToken(token)
            val resp = store(context).readData(b.build())
            out += resp.dataList
            token = resp.pageToken
        } while (token != null)
        return out
    }

    /**
     * 通用的**聚合**读取。
     *
     * ⚠ `AggregateRequest.Builder<T>` 这个基接口上**只有 `build()`** —— 时间过滤的 setter
     * 全在具体 builder（`LocalTimeBuilder` / `LocalDateBuilder` / `AllSourceLocalDateBuilder`）上。
     * 所以过滤条件**必须由调用方用 lambda 配**：
     *
     * ```
     * aggregate(context, DataType.StepsType.TOTAL) {
     *     it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.since(todayStart()))
     * }
     * ```
     *
     * 这里 `it` 的静态类型是 `LocalTimeBuilder<Long>`（由 `S` 推出来），setter 才解析得到。
     * 想在函数内部统一写过滤是不可能的 —— **这不是绕路，是类型系统在提醒
     * "不同聚合算子的时间口径本来就不一样"**（步数是"今天到此刻"，目标是"最近一次设定"）。
     */
    private suspend fun <T : Any, S : AggregateRequest.Builder<T>> aggregate(
        context: Context,
        op: AggregateOperation<T, S>,
        configure: (S) -> S,
    ): List<AggregatedData<T>> {
        val out = mutableListOf<AggregatedData<T>>()
        var token: String? = null
        do {
            val b = configure(op.requestBuilder)
            val resp = store(context).aggregateData(b.build())
            out += resp.dataList
            token = resp.pageToken
        } while (token != null)
        return out
    }

    /** 聚合结果的第一个值。**没有结果就 null** —— 别拿 0 顶（0 步 ≠ 没数据）。 */
    private fun <T : Any> firstValue(rows: List<AggregatedData<T>>): T? = rows.firstOrNull()?.value

    private fun sinceDays(days: Int): Instant = Instant.now().minusSeconds(days * 86_400L)

    private fun todayStart(): LocalDateTime = LocalDate.now().atStartOfDay()

    /** 异常压成一句短原因（与其它 `lastXxxError` 的记录格式保持一致）。 */
    private fun Throwable.brief(): String = "${javaClass.simpleName}: $message"

    /**
     * 今天 00:00 的毫秒时间戳（本地时区）。
     *
     * ## ⚠「今天」的定义**只此一处**
     * 手机端各页的「今日摄入 / 今日饮水 / 今日爬楼」，以及推给手表的
     * [com.gemini.healthbridge.core.ExtrasSummaryMessage]，全部走它。
     *
     * 它原先只活在 `MainActivity` 里（私有）。手表那条路也要"今天"，
     * 如果就地再写一遍 `LocalDate.now().atStartOfDay()`，**两处早晚会漂移**
     * （一处改了时区处理、另一处没改，差一天，而且不报错）。
     *
     * 实现刻意**由 [todayStart] 派生**而不是重写一遍 —— `todayStart()` 是喂给
     * 三星聚合过滤器的那个 `LocalDateTime`，两者必须是同一个瞬间。
     */
    fun todayStartMs(): Long =
        todayStart().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /** 浮点字段的空值包装：**NaN 一律转成 null**，绝不让它流到界面上当数字用。 */
    private fun Float?.orNull(): Float? = if (this == null || this.isNaN()) null else this

    // ─────────────── 血糖 ───────────────

    /**
     * 读最近 [days] 天的血糖。⚠ 单位 **mmol/L**（官方文档核实，不是 mg/dL）。
     *
     * 手表的 PPG **测不出血糖**，所以"本机没有数据"是常态、不是故障。
     */
    suspend fun readBloodGlucose(context: Context, days: Int = 90): List<GlucoseReading> =
        readExtra(context, ExtraId.GLUCOSE, DataTypes.BLOOD_GLUCOSE, emptyList()) {
            readSeries(context, DataTypes.BLOOD_GLUCOSE, sinceDays(days)).mapNotNull { p ->
                val v = p.getValueOrDefault(DataType.BloodGlucoseType.GLUCOSE_LEVEL, Float.NaN)
                // 无效值直接丢掉：0 或负数不是"低血糖"，是"没测到"
                if (v.isNaN() || v <= 0f) null else GlucoseReading(
                    atMs = p.startTime.toEpochMilli(),
                    mmolPerL = v,
                    // ⚠ 存**原始枚举名**，不在这里翻译 —— 译文随语言变，
                    // 存了译文就没法用 `isUndefined` 判"未标注"了（模块 #15）。
                    // 显示时由界面调 `Glucose.xxxLabel(raw)` 取译文。
                    meal = p.getValueOrDefault(
                        DataType.BloodGlucoseType.MEAL_STATUS,
                        DataType.BloodGlucoseType.MealStatus.UNDEFINED,
                    ).name,
                    source = p.getValueOrDefault(
                        DataType.BloodGlucoseType.SAMPLE_SOURCE_TYPE,
                        DataType.BloodGlucoseType.SampleSourceType.UNDEFINED,
                    ).name,
                    sample = p.getValueOrDefault(
                        DataType.BloodGlucoseType.MEASUREMENT_TYPE,
                        DataType.BloodGlucoseType.MeasurementType.UNDEFINED,
                    ).name,
                )
            }
        }

    // ─────────────── 核心体温（来自体温计/合作设备，**不是我们测的**）───────────────

    /**
     * 读最近 [days] 天的**核心体温**（`BODY_TEMPERATURE`）。
     *
     * ⚠ 这与手表测的 `SKIN_TEMPERATURE`（腕部皮温）**不是一回事**，两者差 1~3℃。
     * 手表自己产不出核心体温，这里读的是三星健康里**已有的**记录 ——
     * 来源是合作设备（体温计/体温贴）或用户手动录入。**有数说明用户有那些设备**，
     * 没数也不是故障。
     */
    suspend fun readCoreBodyTemperature(context: Context, days: Int = 30): List<CoreTempReading> =
        readExtra(context, ExtraId.CORE_TEMP, DataTypes.BODY_TEMPERATURE, emptyList()) {
            readSeries(context, DataTypes.BODY_TEMPERATURE, sinceDays(days)).mapNotNull { p ->
                val v = p.getValueOrDefault(DataType.BodyTemperatureType.BODY_TEMPERATURE, Float.NaN)
                // 生理上不可能的值直接丢（三星写入时会校验，但读出来仍要防一手）
                if (v.isNaN() || v < Temperature.MIN_C || v > Temperature.MAX_C) null
                else CoreTempReading(p.startTime.toEpochMilli(), v)
            }
        }

    // ─────────────── 运动记录 ───────────────

    /**
     * 读最近 [days] 天的运动记录。
     *
     * ⚠ 一个三星 `ExerciseType` 数据点里可能装**多个** session
     * （铁人三项 = 游泳+骑车+跑步三段），所以这里用 `flatMap` **摊平**成多条，
     * 并保留 [ExerciseEntry.groupStartMs] 便于认出"同一场"。
     */
    suspend fun readExercises(context: Context, days: Int = 30): List<ExerciseEntry> =
        readExtra(context, ExtraId.EXERCISE, DataTypes.EXERCISE, emptyList()) {
            readSeries(context, DataTypes.EXERCISE, sinceDays(days)).flatMap { p ->
                val sessions = p.getValueOrDefault(
                    DataType.ExerciseType.SESSIONS, emptyList<ExerciseSession>()
                )
                val custom = p.getValueOrDefault(DataType.ExerciseType.CUSTOM_TITLE, "")
                sessions.map { s ->
                    ExerciseEntry(
                        groupStartMs = p.startTime.toEpochMilli(),
                        startMs = s.startTime.toEpochMilli(),
                        endMs = s.endTime.toEpochMilli(),
                        typeName = Extras.exerciseLabel(s.exerciseType.name),
                        customTitle = custom.ifBlank { null } ?: s.customTitle,
                        durationMs = s.duration.toMillis(),
                        calories = s.calories,
                        distanceM = s.distance,
                        meanHr = s.meanHeartRate,
                        maxHr = s.maxHeartRate,
                        count = s.count,
                    )
                }
            }
        }

    // ─────────────── 营养 ───────────────

    /** 读最近 [days] 天的营养记录（**用户自己在三星健康里记的**，我们测不出来）。 */
    suspend fun readNutrition(context: Context, days: Int = 7): List<NutritionEntry> =
        readExtra(context, ExtraId.NUTRITION, DataTypes.NUTRITION, emptyList()) {
            readSeries(context, DataTypes.NUTRITION, sinceDays(days)).mapNotNull { p ->
                val kcal = p.getValueOrDefault(DataType.NutritionType.CALORIES, Float.NaN)
                if (kcal.isNaN()) null else NutritionEntry(
                    atMs = p.startTime.toEpochMilli(),
                    mealType = Extras.mealLabel(
                        p.getValueOrDefault(
                            DataType.NutritionType.MEAL_TYPE,
                            DataType.NutritionType.MealType.UNDEFINED,
                        ).name
                    ),
                    title = p.getValueOrDefault(DataType.NutritionType.TITLE, "").ifBlank { null },
                    calories = kcal,
                    carbohydrateG = p.getValueOrDefault(
                        DataType.NutritionType.CARBOHYDRATE, Float.NaN
                    ).orNull(),
                    proteinG = p.getValueOrDefault(DataType.NutritionType.PROTEIN, Float.NaN).orNull(),
                    fatG = p.getValueOrDefault(DataType.NutritionType.TOTAL_FAT, Float.NaN).orNull(),
                )
            }
        }

    // ─────────────── 水分 ───────────────

    /** 读最近 [days] 天的饮水记录。单位 **mL**。 */
    suspend fun readWaterIntake(context: Context, days: Int = 7): List<WaterEntry> =
        readExtra(context, ExtraId.WATER, DataTypes.WATER_INTAKE, emptyList()) {
            readSeries(context, DataTypes.WATER_INTAKE, sinceDays(days)).mapNotNull { p ->
                val ml = p.getValueOrDefault(DataType.WaterIntakeType.AMOUNT, Float.NaN)
                if (ml.isNaN() || ml <= 0f) null
                else WaterEntry(p.startTime.toEpochMilli(), ml)
            }
        }

    // ─────────────── 爬楼 ───────────────

    /** 读最近 [days] 天的爬楼记录。单位 **层**。 */
    suspend fun readFloorsClimbed(context: Context, days: Int = 7): List<FloorEntry> =
        readExtra(context, ExtraId.FLOORS, DataTypes.FLOORS_CLIMBED, emptyList()) {
            readSeries(context, DataTypes.FLOORS_CLIMBED, sinceDays(days)).mapNotNull { p ->
                val f = p.getValueOrDefault(DataType.FloorsClimbedType.FLOOR, Float.NaN)
                if (f.isNaN() || f <= 0f) null else FloorEntry(p.startTime.toEpochMilli(), f)
            }
        }

    // ─────────────── 心律不齐通知 ───────────────

    /**
     * 读最近 [days] 天的心律不齐通知。
     *
     * ⚠ 只有 `DETECTED` / `UNDEFINED` 两个取值，**没有 `NOT_DETECTED`** ——
     * 所以"列表为空"**不能**读成"心脏没问题"。这条纪律见 `core/Ihrn.kt`。
     */
    suspend fun readIhrn(context: Context, days: Int = 180): List<IhrnEntry> =
        readExtra(
            context, ExtraId.IHRN, DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION, emptyList()
        ) {
            readSeries(
                context, DataTypes.IRREGULAR_HEART_RHYTHM_NOTIFICATION, sinceDays(days)
            ).map { p ->
                IhrnEntry(
                    atMs = p.startTime.toEpochMilli(),
                    status = p.getValueOrDefault(
                        DataType.IrregularHeartRhythmNotificationType.STATUS,
                        DataType.IrregularHeartRhythmNotificationType
                            .IrregularHeartRhythmStatus.UNDEFINED,
                    ).name,
                )
            }
        }

    // ─────────────── 活动汇总（**聚合取数**）───────────────

    /**
     * 今天的活动量汇总。
     *
     * 三星把"活动消耗"与"总消耗（含基础代谢）"**分开给**，两个都取 ——
     * 只给一个的话用户会误以为自己在做无氧运动时没有任何消耗。
     *
     * ⚠ 所有字段都可能为 null：手机没带、没开权限、当天没数据。
     * 界面上要如实说"没有数据"，**0 步会被读成"今天一步没走"**。
     */
    suspend fun readActivityTotals(context: Context): ActivityTotals {
        val stepsOk = hasReadPermission(context, DataTypes.STEPS)
        val sumOk = hasReadPermission(context, DataTypes.ACTIVITY_SUMMARY)
        extraReadErrors.remove(ExtraId.STEPS)
        extraReadErrors.remove(ExtraId.ACTIVITY_SUMMARY)
        if (!stepsOk) extraAuthErrors.putIfAbsent(ExtraId.STEPS, LanguageStore.string(context, R.string.s_a23b99d2))
        if (!sumOk) extraAuthErrors.putIfAbsent(ExtraId.ACTIVITY_SUMMARY, LanguageStore.string(context, R.string.s_a23b99d2))

        val steps = if (!stepsOk) null else runCatching {
            firstValue(
                aggregate(context, DataType.StepsType.TOTAL) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.since(todayStart()))
                }
            )
        }.onFailure { extraReadErrors[ExtraId.STEPS] = it.brief() }.getOrNull()

        // 活动汇总的 4 个算子在三星不同版本上开放的先后不一样，**各自容忍失败**：
        // 一个算子不被支持时不该把另外三个也一起丢掉。
        val activeKcal = if (!sumOk) null else runCatching {
            firstValue(
                aggregate(context, DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.since(todayStart()))
                }
            )
        }.onFailure { extraReadErrors[ExtraId.ACTIVITY_SUMMARY] = it.brief() }.getOrNull()

        val totalKcal = if (!sumOk) null else runCatching {
            firstValue(
                aggregate(context, DataType.ActivitySummaryType.TOTAL_CALORIES_BURNED) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.since(todayStart()))
                }
            )
        }.onFailure { extraReadErrors[ExtraId.ACTIVITY_SUMMARY] = it.brief() }.getOrNull()

        val activeTime = if (!sumOk) null else runCatching {
            firstValue(
                aggregate(context, DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.since(todayStart()))
                }
            )
        }.onFailure { extraReadErrors[ExtraId.ACTIVITY_SUMMARY] = it.brief() }.getOrNull()

        val distance = if (!sumOk) null else runCatching {
            firstValue(
                aggregate(context, DataType.ActivitySummaryType.TOTAL_DISTANCE) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.since(todayStart()))
                }
            )
        }.onFailure { extraReadErrors[ExtraId.ACTIVITY_SUMMARY] = it.brief() }.getOrNull()

        return ActivityTotals(
            activeKcal = activeKcal,
            totalKcal = totalKcal,
            activeMs = activeTime?.toMillis(),
            distanceM = distance,
            steps = steps,
        )
    }


    /** 算标准差至少要有这么多个点（约一小时的分钟级采样）。 */
    private const val MIN_HR_SD_SAMPLES = 60

    /**
     * 心率序列的标准差。
     *
     * - **两遍算法**（先均值、再方差），不用 `E[x²] − E[x]²`：心率的均值远大于它的波动
     *   （60 上下浮动几个点），那个式子在浮点上会因大数相减丢掉有效位，
     *   极端情况下算出负数再开根号得到 NaN —— 而 NaN 流到界面上就是"±NaN 次/分"。
     * - **样本太少返回 null，不返回 0**：0 的含义是"整晚心率一个值"，
     *   那是采集异常而不是"极其稳定"；拿 0 去比基线会得到一个虚高的分。
     */
    private fun hrStdDev(points: List<Pair<Long, Int>>): Double? {
        if (points.size < MIN_HR_SD_SAMPLES) return null
        val mean = points.sumOf { it.second.toDouble() } / points.size
        val variance = points.sumOf { (it.second - mean) * (it.second - mean) } / points.size
        val sd = kotlin.math.sqrt(variance)
        return if (sd > 0.0) sd else null
    }

    /** 某一天的活动量（能量评分里「前一日的活动」「活动持续性」两项的输入）。 */
    data class ActivityDayReading(
        val epochDay: Long,
        /** 全天步数。null = 读不到。 */
        val steps: Long?,
        /** 6 段（每 4 小时）步数；任一段**读取出错**时为 null（不是补 0）。 */
        val slots: List<Long>?,
        val activeKcal: Float?,
        val activeMs: Long?,
    )

    /**
     * 读**某一天**的活动量（含 6 段分布）。
     *
     * 为什么能读历史：聚合算子接受任意时间区间 —— 2026-09-24 实测
     * "昨天 6000 步 / 前天 3120 步"都读得回来（`syncActivityDays` 每天就是这么补的）。
     * 这条性质让"活动持续性"从"通道缺失"变成了可做：把一天切 6 段分别读即可拼出分布。
     *
     * @param epochDay 目标日（自然日，非"醒来那天"）。
     */
    suspend fun readActivityDay(context: Context, epochDay: Long): ActivityDayReading {
        val day0 = java.time.LocalDate.ofEpochDay(epochDay).atStartOfDay()
        val now = java.time.LocalDateTime.now()
        // 今天的话终点取"现在"：把区间伸到未来没有意义，三星也可能直接回空。
        val dayEnd = day0.plusDays(1).let { if (it.isAfter(now)) now else it }

        val steps = readStepsBetween(context, day0, dayEnd)
        val kcal = runCatching {
            firstValue(
                aggregate(context, DataType.ActivitySummaryType.TOTAL_ACTIVE_CALORIES_BURNED) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.of(day0, dayEnd))
                }
            )
        }.getOrNull()
        val activeMs = runCatching {
            firstValue(
                aggregate(context, DataType.ActivitySummaryType.TOTAL_ACTIVE_TIME) {
                    it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.of(day0, dayEnd))
                }
            )
        }.getOrNull()

        return ActivityDayReading(
            epochDay = epochDay,
            steps = steps,
            slots = readActivitySlots(context, day0, dayEnd),
            activeKcal = kcal,
            activeMs = activeMs?.toMillis(),
        )
    }

    /** 某个区间的步数合计。读不到返回 null。 */
    private suspend fun readStepsBetween(
        context: Context,
        from: java.time.LocalDateTime,
        to: java.time.LocalDateTime,
    ): Long? = runCatching {
        firstValue(
            aggregate(context, DataType.StepsType.TOTAL) {
                it.setPageSize(1).setLocalTimeFilter(LocalTimeFilter.of(from, to))
            }
        )
    }.getOrNull()

    /**
     * 把 [day0, dayEnd) 切成 [Energy.ACTIVITY_SLOTS] 段分别读步数。
     *
     * 两处判断需要留意：
     * - 聚合对"区间内没有任何记录"返回的是**空列表**（`firstValue` → null），
     *   那不是错误，就是"这 4 小时没走路" → 记 **0**。
     *   真正的读取错误会抛异常 → 整个 slots 返回 **null**（那一项缺席），
     *   绝不把"读失败"混成"没活动"。
     * - 区间还没走完（今天）时，剩下的段记 0 —— 只对今天成立，历史日期一定走得完。
     */
    private suspend fun readActivitySlots(
        context: Context,
        day0: java.time.LocalDateTime,
        dayEnd: java.time.LocalDateTime,
    ): List<Long>? {
        val out = ArrayList<Long>(Energy.ACTIVITY_SLOTS)
        for (i in 0 until Energy.ACTIVITY_SLOTS) {
            val s = day0.plusHours((i * 4).toLong())
            if (!s.isBefore(dayEnd)) {
                out += 0L
                continue
            }
            val e = day0.plusHours(((i + 1) * 4).toLong())
                .let { if (it.isAfter(dayEnd)) dayEnd else it }
            val v = runCatching { readStepsBetween(context, s, e) }.getOrElse { return null }
            out += v ?: 0L
        }
        return out
    }

    /**
     * 把最近 [days] 天的活动量读进来落库。
     *
     * 两条策略值得说明：
     *
     * 1. **昨天总是重读**，更早的只在库里没有时才补。三星常在**次日**回填修正当天的活动量，
     *    而"前一日的活动"正是评分用到的那一天 —— 缓存一次就固定下来会一直用错值。
     *    （更早的天数修正的概率低得多，而每天重读 7 天是 49 次跨进程调用，不值得。）
     * 2. **不读今天**：今天还没过完，总量与分布都还在变，读了就是半截数据，
     *    而评分要的是"整天的活动"。活动页那边有自己的实时读取，不受影响。
     *
     * @return 本次新写进去的行数
     */
    suspend fun syncActivityDays(context: Context, days: Int = 7): Int {
        val db = SyncRepository.database
        val today = java.time.LocalDate.now().toEpochDay()
        val have = runCatching {
            db.activityDayDao().recentOnce(days + 1).map { it.dateEpochDay }.toSet()
        }.getOrDefault(emptySet())
        val now = System.currentTimeMillis()
        var written = 0
        for (d in 1..days) {
            val day = today - d
            if (d > 1 && day in have) continue
            val r = runCatching { readActivityDay(context, day) }.getOrNull() ?: continue
            // 一整行都是空的话就别写：写一行"什么都没有"的记录，与"没有这一行"
            // 在界面上的表现一样，但会让"补读是否生效"变得无法判断。
            if (r.steps == null && r.slots == null) continue
            written += runCatching {
                db.activityDayDao().insertAll(
                    listOf(
                        ActivityDayEntity(
                            dateEpochDay = r.epochDay,
                            steps = r.steps,
                            slotSteps = r.slots
                                ?.takeIf { it.size == Energy.ACTIVITY_SLOTS }
                                ?.joinToString(","),
                            activeKcal = r.activeKcal,
                            activeMs = r.activeMs,
                            recordedAt = now,
                        )
                    )
                )
            }.getOrDefault(emptyList()).count { it > 0 }
        }
        runCatching { db.activityDayDao().keepLatest(400) }
        return written
    }

    // ═══════════ 睡眠历史回填（2026-10-09 新增）═══════════
    //
    // 为什么需要它：`readSleepBundle` 只读**最近一晚**（窗口写死 [昨天18:00, 现在]），
    // 于是"那天没打开过 App" ⇒ 那一晚**永久缺失**（第二天再读已经读不到）。
    // 症状是能量评分的趋势图隔三差五一个洞，而且**看不出是漏读还是那晚没睡**。
    //
    // 与 `syncActivityDays` 完全同构（同一个理由：三星的读取入口按时间区间取数、能读历史）；
    // 差别只在"一晚"与"一天"的边界 —— **睡眠跨零点**，所以窗口必须跨零点、
    // 归日判据必须落在**醒来那一刻**上（细则见 `readSleepNightForDay`）。

    /** 某一个**醒来日**读回来的主睡眠。 */
    private data class SleepNightReading(
        val epochDay: Long,
        val startMs: Long,
        val endMs: Long,
        val score: Int?,
        val stages: List<SleepStageDto>,
    )

    /**
     * 读**某个醒来日** [epochDay] 的主睡眠会话（含阶段段与三星睡眠得分）。
     *
     * ## 窗口「前一天 12:00 → 当天 24:00」是怎么定的
     * 归日口径是**醒来那天**（与 `sleep_odi`、与实时同步第 ⑧.5 步同源），
     * 所以判据必须落在 **`endMs` 所在的自然日**上，而**不是**"起点落在读取窗口内"。
     * 窗口只要**覆盖**就够了，多读回来的会话由 `endMs` 的归日过滤掉。
     *
     * 起点取「前一天 **12:00**」而不是 18:00：给"睡得早"（20:00 上床）与
     * "睡得久"（下午就睡）各留出余量，避免把一整晚切在窗口外。
     *
     * ⚠ 典型会话是 `[D-1 22:00, D 07:00]`：**起点在 D-1、终点在 D**。
     *   ⇒ 只按起点判会把它归到 D-1，只按"今天 0 点起"又会把整晚切碎甚至整晚丢掉。
     *   窗口跨零点 + `endMs` 归日，两条一起才对。
     *
     * @return 没有睡眠记录、或所有会话时长非法时返回 **null** ——
     *   调用方据此**不写行**，而不是写一行"睡了 0 小时"（与 `readActivityDay` 里
     *   "整行都空就不写"是同一条纪律）。
     */
    private suspend fun readSleepNightForDay(context: Context, epochDay: Long): SleepNightReading? {
        val day = LocalDate.ofEpochDay(epochDay)
        val zone = ZoneId.systemDefault()
        val since = day.minusDays(1).atTime(12, 0)
        // 上界取"当天 24:00"（= 次日 0 点）：再往后就属于**下一晚**了（D+1 的那一晚）。
        val until = day.plusDays(1).atStartOfDay()
        var token: String? = null
        // 窗口内**所有**会话（主睡眠要跟午睡比时长才知道是谁），只留 endMs 落在 D 的。
        val candidates = ArrayList<SleepHours.Span>()
        val scoreByStart = HashMap<Long, Int?>()
        val stagesByStart = HashMap<Long, List<SleepSession.SleepStage>>()
        do {
            var builder = DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(LocalTimeFilter.of(since, until))
                .setOrdering(Ordering.DESC)
                .setLimit(50)
            if (token != null) builder = builder.setPageToken(token)
            val resp = store(context).readData(builder.build())
            for (p in resp.dataList) {
                val sessions = p.getValue(DataType.SleepType.SESSIONS) ?: continue
                // 一个数据点里可能有多个会话，全都收下（与 `readSleepBundle` 同一处理）
                for (s in sessions) {
                    val sMs = s.startTime.toEpochMilli()
                    val eMs = s.endTime.toEpochMilli()
                    if (eMs <= sMs) continue
                    // ⚠ 归日判据 = **醒来时刻所在的那一天**。
                    //   漏了这一条就会把 D+1 凌晨结束的那一段也算进 D（两天各有一行、
                    //   且两行的区间重叠 —— 评分会把同一段睡眠算两次）。
                    val wakeDay = Instant.ofEpochMilli(eMs).atZone(zone).toLocalDate().toEpochDay()
                    if (wakeDay != epochDay) continue
                    candidates.add(SleepHours.Span(sMs, eMs))
                    scoreByStart[sMs] = p.getValue(DataType.SleepType.SLEEP_SCORE)
                    stagesByStart[sMs] = s.stages.orEmpty()
                }
            }
            token = resp.pageToken
        } while (token != null)

        // ⚠ 挑主睡眠，而不是"结束最晚"的那一场 —— 否则午睡会顶掉整晚
        //   （判据见 `SleepHours.mainSession`，那是 2026-09-29 修过的同一个 bug）。
        val chosen = SleepHours.mainSession(candidates) ?: return null
        return SleepNightReading(
            epochDay = epochDay,
            startMs = chosen.startMs,
            endMs = chosen.endMs,
            score = scoreByStart[chosen.startMs],
            stages = mergeSleepStages(stagesByStart[chosen.startMs]),
        )
    }

    /**
     * 把最近 [days] 晚的睡眠**补齐落库**（`sleep_nights` + `sleep_odi`）。
     *
     * ## 两条策略，与 `syncActivityDays` 逐字同构
     * 1. **昨天总是重读**：三星会在**次日**回填/修正昨晚的阶段段与得分，
     *    缓存一次就固定下来会一直用一个早就该被修正的值。更早的天数修正概率低得多，
     *    而每晚要额外读 3 趟（心率/血氧/皮温），重读 7 晚就是二十多次跨进程调用，不值得。
     * 2. **不读今天**：今天的"昨晚"由实时同步（`syncToday` 第 ①、⑧.5 步）负责，
     *    这里从**昨天**起算 ⇒ 两条链路**不重叠**、也就不会互相覆盖出两个口径。
     *
     * ## 幂等
     * `sleep_nights` 与 `sleep_odi` 的 `dateEpochDay` 上都有**唯一索引**
     * （见各自的 `@Entity(indices = ...)`），写入是 `OnConflictStrategy.REPLACE`
     * ⇒ 重跑只**覆盖**、不产生第二行。所以"补装新版再点一次"是安全的，
     * 不需要额外的去重表或判重查询。
     *
     * ## 读不到就什么都不写
     * 没有睡眠权限、那一晚没有记录、或主会话时长非法 → **跳过那一晚**，不写空行。
     * 写一行"睡了 0 小时"与"没有这一行"在界面上表现几乎一样，
     * 但会让"回填到底有没有生效"变成一件无法判断的事。
     *
     * ## 为什么默认 **14 晚**（而 `syncActivityDays` 是 7 天）
     * 两者"读一次的代价"同构，但**下游要的东西不一样**：
     * 活动量只喂「前一日的活动」「活动持续性」两项 —— 看的是**前一天**，7 天足够；
     * 而睡眠喂的是能量评分的**基线与趋势**（`Energy.BASELINE_NIGHTS = 14`、
     * 「睡眠规律性」要"近 14 晚入睡极差"）。只补 7 晚的话，那两项**头一周永远是缺席的**，
     * 而用户看不出是"刚装的新版"还是"评分坏了"。
     *
     * ⚠ 代价只发生在**首次**：之后每天只重读昨天（`d == 1` 那一次），
     *   其余天数因为 `day in have` 直接跳过。
     *
     * @param days 往前补多少晚（默认 14，见上）
     * @return 本次新写进去的**晚数**（`sleep_nights` 的行数）。
     */
    /** 按日桶回填往前补几天（与 `syncSleepDays` 的 14 晚对齐）。 */
    const val DAILY_VITALS_DAYS = 14

    /** `daily_vitals` 保留多少天（约一年多，与 `sleep_nights` 的 400 晚同量级）。 */
    const val DAILY_VITALS_KEEP_DAYS = 400

    /** `daily_vitals.kind` 的取值。⚠ **存储键**，不许写成中文（界面语言一变就匹配不上）。 */
    const val VITALS_KIND_HR = "hr"
    const val VITALS_KIND_SPO2 = "spo2"

    /**
     * 把最近 [days] 天的**每小时心率/血氧桶**补齐落库（`daily_vitals`，2026-10-13，DB v22）。
     *
     * ## 为什么要它
     *
     * 用户的原话：「**不该是每天软件跟三星健康同步后数据都保留在了本地，以后访问历史
     * 都是直接从本地读取嘛**。要是本地也没有数据，再尝试从三星健康读取」。
     *
     * 在那之前，`heart_rate` / `spo2` 两张**点表**只覆盖"今天"（血氧只有 48 小时内的点记录），
     * 于是历史页几乎每条都得现读三星：慢、离线看不到、而同一晚的睡眠页里明明有统计值。
     * 这一步就是把"每天读到的序列"压成每小时桶留在本地 —— 第 ⑧.7 步（`syncSleepDays`）
     * 已经在用同一个思路处理睡眠，这里是它缺的那一半。
     *
     * ## 三条判据
     *
     * 1. ⚠ **今天总是重读**（它还在长）；更早的天只要两类都有行就跳过 ⇒ 幂等，
     *    且稳态下每次同步只读 **1 天**（首次装这版会把 14 天补齐）。
     * 2. ⚠⚠ **读过了但那天没有采样**也要写一行（`buckets` 空串、统计值 `null`）：
     *    那一行记的是"同步过了、那天没有"，与"本地压根没同步过"必须分得开 ——
     *    否则历史页每次都要去读一遍空白的天，而"白读"正是用户抱怨的那件事。
     * 3. ⚠ **不写点表**：睡眠期血氧一晚 400+ 条，落进 `spo2` 会把「最近记录」列表刷屏
     *    （`syncToday` 里早就定下的一条判断）。这里只留每小时的最低/最高/平均。
     *
     * 任何一天读失败都只是跳过那一天（读不到就不写），不影响同步的其它步骤。
     *
     * @return 写进去的行数（一天最多两行），用于事件文案。
     */
    suspend fun syncDailyVitals(context: Context, days: Int = DAILY_VITALS_DAYS): Int {
        val db = SyncRepository.database
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone).toEpochDay()
        var written = 0
        for (day in (today - days + 1)..today) {
            val day0 = LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = LocalDate.ofEpochDay(day + 1).atStartOfDay(zone)
                .toInstant().toEpochMilli() - 1
            // 判据 1：更早的天已写过就跳过（"今天"永远重读）
            if (day < today) {
                val hasHr = db.dailyVitalsDao().one(day, VITALS_KIND_HR) != null
                val hasSpo2 = db.dailyVitalsDao().one(day, VITALS_KIND_SPO2) != null
                if (hasHr && hasSpo2) continue
            }
            val r = runCatching { readWindowVitals(context, day0, dayEnd) }.getOrNull() ?: continue
            val ok = when (r) {
                is DayVitals.Ok -> r
                DayVitals.Unreadable -> continue   // 读不到就什么都不写（下次同步再试）
            }
            val now = System.currentTimeMillis()
            val rows = ArrayList<DailyVitalsEntity>(2)
            for ((kind, points) in listOf(
                VITALS_KIND_HR to ok.hr,
                VITALS_KIND_SPO2 to ok.spo2,
            )) {
                val buckets = Vitals.bucketsOf(points, day0)
                rows += DailyVitalsEntity(
                    dateEpochDay = day,
                    kind = kind,
                    buckets = Vitals.encodeBuckets(buckets),
                    // 判据 2：没有桶就是 null（不是 0）
                    minValue = Vitals.minOf(buckets),
                    maxValue = Vitals.maxOf(buckets),
                    avgValue = Vitals.avgOf(buckets),
                    sampleCount = Vitals.sampleCountOf(points, day0),
                    updatedAt = now,
                )
            }
            written += runCatching { db.dailyVitalsDao().insertAll(rows).count { it > 0 } }
                .getOrDefault(0)
        }
        runCatching { db.dailyVitalsDao().keepLatest(DAILY_VITALS_KEEP_DAYS) }
        return written
    }

    suspend fun syncSleepDays(context: Context, days: Int = 14): Int {
        if (!hasSleepPermission(context)) return 0
        val db = SyncRepository.database
        val today = LocalDate.now().toEpochDay()
        val have = runCatching {
            db.sleepNightDao().allOnce().map { it.dateEpochDay }.toSet()
        }.getOrDefault(emptySet())
        var written = 0
        for (d in 1..days) {
            val day = today - d
            // 昨天（d == 1）总是重读；更早的只在库里**没有**时才补（见上面第 1 条）。
            if (d > 1 && day in have) continue
            val r = runCatching { readSleepNightForDay(context, day) }.getOrNull() ?: continue
            written += runCatching { writeSleepNight(context, db, r) }.getOrDefault(0)
        }
        // 与实时同步同一套保留上限：一晚一行，一年多足够算基线与趋势。
        runCatching { db.sleepNightDao().keepLatest(400) }
        runCatching { db.sleepOdiDao().keepLatest(30) }
        return written
    }

    /**
     * 把读回来的一晚落库：`sleep_nights` 一行 ＋ `sleep_odi` 一行（**能算才写**）。
     *
     * 口径与实时同步（`syncToday` 第 ⑧.5、⑧ 步）**必须一致**，否则同一天的两次写入
     * 会给出两个不同的数（用户看到的是"我那天到底睡了多久"两个答案）：
     * 归日是"醒来那天" · 阶段段走 `SleepStages.encode`（**空 → null，不是空串**）·
     * `skinTemp` 取当晚序列的均值 · ODI 只在真的拿到血氧序列时才写。
     *
     * ⚠ 心率/血氧/皮温三趟读数**各自容忍失败**：读不到就是 `null`，
     *   **绝不拿 0 顶**（0 会被下游当成一个真实的测量值参与评分）。
     */
    private suspend fun writeSleepNight(
        context: Context,
        db: AppDatabase,
        r: SleepNightReading,
    ): Int {
        val now = System.currentTimeMillis()
        val hr = runCatching { readHrSeriesBetween(context, r.startMs, r.endMs) }
            .getOrDefault(emptyList())
        val spo2 = runCatching { readSpo2SeriesBetween(context, r.startMs, r.endMs) }
            .getOrDefault(emptyList())
        val skin = runCatching { readSkinTempBetween(context, r.startMs, r.endMs) }
            .getOrDefault(emptyList())

        val dto = SleepNightDto(
            startMs = r.startMs,
            endMs = r.endMs,
            score = r.score,
            stages = r.stages,
            spo2Avg = spo2.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt(),
            spo2Min = spo2.minOfOrNull { it.second },
            hrAvg = hr.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt(),
            hrMin = hr.minOfOrNull { it.second },
            hrMax = hr.maxOfOrNull { it.second },
        )
        val rows = db.sleepNightDao().insertAll(
            listOf(
                SleepNightEntity(
                    dateEpochDay = r.epochDay,
                    startMs = r.startMs,
                    endMs = r.endMs,
                    actualSleepMs = dto.actualSleepMs(),
                    hrAvg = dto.hrAvg,
                    hrMin = dto.hrMin,
                    hrSd = hrStdDev(hr),
                    skinTemp = skin.takeIf { it.isNotEmpty() }
                        ?.map { it.second }?.average()?.toFloat(),
                    spo2Avg = dto.spo2Avg,
                    spo2Min = dto.spo2Min,
                    samsungScore = r.score,
                    // ⚠ `encode` 对空列表返回 **null**（不是空串）—— 空串会让下游
                    //   分不清"没存过"与"那晚没有阶段"（见 `SleepNightEntity` 的 KDoc）。
                    stagesJson = SleepStages.encode(r.stages, r.startMs),
                    recordedAt = now,
                )
            )
        )
        val n = rows.count { it > 0 }

        // ODI 与实时同步同源：**只有真拿到血氧序列才算**。
        // 拿不到就别写一行"没有数据" —— 那会把"回填没读到"伪装成一个结论。
        if (spo2.isNotEmpty()) {
            runCatching {
                db.sleepOdiDao().insert(
                    when (val res = Odi.analyze(spo2.map { Odi.Sample(it.first, it.second) })) {
                        is Odi.Result.Ok -> SleepOdiEntity(
                            dateEpochDay = r.epochDay, startMs = r.startMs, endMs = r.endMs,
                            events = res.events, odiPerHour = res.odiPerHour,
                            monitoredMinutes = res.monitoredMinutes, validPoints = res.validPoints,
                            rejectedPoints = res.rejectedPoints, lowest = res.lowest,
                            mean = res.mean, t90Pct = res.t90Pct, computedAt = now,
                        )
                        is Odi.Result.NotApplicable -> SleepOdiEntity(
                            // 指标列全为 null + reason：明确区分"没算出来"与"算出 0 次"
                            dateEpochDay = r.epochDay, startMs = r.startMs, endMs = r.endMs,
                            reason = res.reason, computedAt = now,
                        )
                    }
                )
            }
        }
        return n
    }

    // ─────────────── 六类每日目标（**聚合取数**）───────────────

    /**
     * 六类每日目标。
     *
     * 每个目标都是一次**独立的**聚合调用、**各自容忍失败**：
     * 六类目标在三星不同版本上开放的先后不一样，一个不支持不该拖垮其它五个。
     *
     * ⚠ 目标为空 = 用户**没设过**。我们不会替他编一个默认值
     * （那会变成"我们觉得你应该走一万步"，是越权）。
     */
    suspend fun readGoals(context: Context): DailyGoals {
        /**
         * 取某类目标的**最近一次设定值**。
         *
         * 目标的算子都是「最后一次设定值」（`LAST` / `LAST_CALORIES` / `LAST_*_TIME`），
         * 所以按日期**倒序**取第一条。区间给一年 —— 目标可能设完很久没改过。
         */
        // ⚠ `S` **刻意写实成 `AllSourceLocalDateBuilder`**，不用泛型 `S`。
        //    因为泛型 `S` 只有上界 `AggregateRequest.Builder<T>` 的成员可见，
        //    而基接口上只有 `build()` —— 于是 `setPageSize` / `setOrdering` /
        //    `setLocalDateFilter` 全都"解析不到"（我第一版就是这么错的，报错只说
        //    Unresolved reference 'setPageSize'，完全不提示是类型参数的问题）。
        //    六类目标的算子恰好**全部**用这个 builder（按算子表），所以写实是安全的。
        suspend fun <T : Any> lastOf(
            id: String,
            type: DataType,
            op: AggregateOperation<T, AggregateRequest.AllSourceLocalDateBuilder<T>>,
        ): T? {
            extraReadErrors.remove(id)
            if (!hasReadPermission(context, type)) {
                extraAuthErrors.putIfAbsent(id, LanguageStore.string(context, R.string.s_a23b99d2))
                return null
            }
            return runCatching {
                firstValue(
                    aggregate(context, op) {
                        it.setPageSize(1)
                            .setOrdering(Ordering.DESC)
                            .setLocalDateFilter(
                                LocalDateFilter.since(LocalDate.now().minusDays(365))
                            )
                    }
                )
            }.onFailure { extraReadErrors[id] = it.brief() }.getOrNull()
        }

        // 就寝/起床两个时间点来自**同一个**数据类型的两个算子，
        // 共用 ExtraId.GOAL_SLEEP 这一个失败原因（它们是同一次权限）。
        val bed = lastOf(
            ExtraId.GOAL_SLEEP, DataTypes.SLEEP_GOAL, DataType.SleepGoalType.LAST_BED_TIME
        )
        val wake = lastOf(
            ExtraId.GOAL_SLEEP, DataTypes.SLEEP_GOAL, DataType.SleepGoalType.LAST_WAKE_UP_TIME
        )
        return DailyGoals(
            steps = lastOf(
                ExtraId.GOAL_STEPS, DataTypes.STEPS_GOAL, DataType.StepsGoalType.LAST
            ),
            activeKcal = lastOf(
                ExtraId.GOAL_ACTIVE_CAL, DataTypes.ACTIVE_CALORIES_BURNED_GOAL,
                DataType.ActiveCaloriesBurnedGoalType.LAST,
            ),
            activeMs = lastOf(
                ExtraId.GOAL_ACTIVE_TIME, DataTypes.ACTIVE_TIME_GOAL,
                DataType.ActiveTimeGoalType.LAST,
            )?.toMillis(),
            waterMl = lastOf(
                ExtraId.GOAL_WATER, DataTypes.WATER_INTAKE_GOAL, DataType.WaterIntakeGoalType.LAST
            ),
            nutritionKcal = lastOf(
                ExtraId.GOAL_NUTRITION, DataTypes.NUTRITION_GOAL,
                DataType.NutritionGoalType.LAST_CALORIES,
            ),
            bedTime = bed?.let { hhmm(it) },
            wakeTime = wake?.let { hhmm(it) },
        )
    }

    /** `LocalTime` → `HH:mm`（固定两位，避免 `9:5` 这种读不出来的写法）。 */
    private fun hhmm(t: java.time.LocalTime): String =
        String.format(java.util.Locale.US, "%02d:%02d", t.hour, t.minute)

    // ═══════════ 能量评分 / 睡眠呼吸暂停（三星的现成指标，2026-09-19） ═══════════
    //
    // 这两个是三星健康**自己算好的成品**，我们不做任何再加工：读到什么就存什么、显示什么。
    // 所以这一块**没有 core 层算法、也没有离线台架** —— 与 #10 核心体温那种
    // "确定性派生量"不同，这两个分数不是我们算的，我们只是搬运工。
    // （项目里凡是有自研算法的模块必有台架；这里是"没有算法可测"，不是"忘了写"。）

    /**
     * 读最近 [days] 天的能量评分。
     *
     * 这个类型是**按日期**的（`LocalDateBuilder`），一天一条，所以时间过滤用
     * `LocalTimeFilter.since(...)` 而不是 `InstantTimeFilter`（与睡眠同一个用法）。
     *
     * 没权限 / 区域不支持 / 一条都没有 → 都返回**空列表**。
     * 调用方**绝不能把空当成"0 分"** —— 那会被读成"你今天状态极差"。
     */
    suspend fun readEnergyScores(context: Context, days: Int = 30): List<EnergyScoreEntity> {
        lastEnergyReadError = null
        if (!hasEnergyScorePermission(context)) {
            // 没权限 = 没走到读取这一步，记 -1（**不是 0 条**，两者含义完全不同）
            lastEnergyReadCount = -1
            return emptyList()
        }
        return runCatching {
            // 注意：这个类型的 builder 是 LocalDateBuilder，它的过滤 setter 叫
            // **setLocalDateFilter**（不是 setLocalTimeFilter —— 那是 DualTimeBuilder 才有的；
            // 睡眠用的那个 builder 两者都有，所以别照着睡眠那段抄）。
            val sinceDate = java.time.LocalDate.now().minusDays((days - 1).toLong())
            val out = mutableListOf<EnergyScoreEntity>()
            var token: String? = null
            do {
                var builder = DataTypes.ENERGY_SCORE.readDataRequestBuilder
                    .setLocalDateFilter(LocalDateFilter.since(sinceDate))
                    .setOrdering(Ordering.ASC)
                    .setLimit(100)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val v = p.getValueOrDefault(
                        DataType.EnergyScoreType.ENERGY_SCORE, Float.NaN
                    )
                    if (v.isNaN()) continue          // 没值就跳过，不拿 0 顶
                    // 归属日按**数据点自己的时区偏移**算：跨时区或跨日边界时用本机时区会归错天
                    val zone = p.zoneOffset ?: ZoneId.systemDefault()
                    out.add(
                        EnergyScoreEntity(
                            dateEpochDay = p.startTime.atZone(zone).toLocalDate().toEpochDay(),
                            // 三星界面显示的就是整数分；四舍五入取整，并夹到 0~100
                            score = Math.round(v).coerceIn(0, 100),
                            measuredAt = p.startTime.toEpochMilli(),
                        )
                    )
                }
                token = resp.pageToken
            } while (token != null)
            out
        }.fold(
            onSuccess = { rows ->
                lastEnergyReadCount = rows.size
                rows
            },
            onFailure = { e ->
                // ⚠ 一定要留下痕迹。原来这里只 `emptyList()`，异常被吞掉之后
                // 界面只能猜 —— 而它猜成了"三星还没算过"，把用户指向了完全错误的方向。
                lastEnergyReadError = "${e.javaClass.simpleName}: ${e.message}"
                lastEnergyReadCount = 0
                storeRef = null
                emptyList()
            },
        )
    }

    /**
     * 读最近 [days] 天内的睡眠呼吸暂停检测记录。
     *
     * 这个类型是**区间型**（`DualTimeBuilder`），所以时间过滤用 `InstantTimeFilter`，
     * 与心率/血氧/体成分那一类一致。
     *
     * ⚠ 数据来源是**「三星健康监测器」**（另一个 App）：用户在那边开启检测、
     * 并授权同步给三星健康之后才有。**空列表是预期结果**，不是故障。
     */
    suspend fun readSleepApnea(context: Context, days: Int = 180): List<SleepApneaEntity> {
        if (!hasSleepApneaPermission(context)) return emptyList()
        return runCatching {
            val since = java.time.Instant.now().minusSeconds(days * 86_400L)
            val out = mutableListOf<SleepApneaEntity>()
            var token: String? = null
            do {
                var builder = DataTypes.SLEEP_APNEA.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(since))
                    .setOrdering(Ordering.DESC)
                    .setLimit(100)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val sign = p.getValueOrDefault(
                        DataType.SleepApneaType.DETECTED_SIGN,
                        DataType.SleepApneaType.DetectedSign.UNDEFINED,
                    )
                    val zone = p.zoneOffset ?: ZoneId.systemDefault()
                    out.add(
                        SleepApneaEntity(
                            startMs = p.startTime.toEpochMilli(),
                            endMs = p.endTime?.toEpochMilli(),
                            dateEpochDay = p.startTime.atZone(zone).toLocalDate().toEpochDay(),
                            // 原样存枚举名，不转成我们自己的枚举：三星以后加取值只是
                            // 多一种读不懂的字符串，不会在解析处崩掉
                            sign = sign.name,
                        )
                    )
                }
                token = resp.pageToken
            } while (token != null)
            out
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    suspend fun readSleepNight(context: Context): SleepNightDto? = readSleepBundle(context).night

    /**
     * 一次读齐睡眠页需要的全部内容：睡眠摘要 + 睡眠期的血氧/心率序列。
     *
     * 取"最近一晚"的口径：只看昨晚 18:00 至今的 SLEEP 数据点，在窗口内的若干会话里
     * 挑**主睡眠**那一场（判据见 [SleepHours.mainSession]）。
     *
     * ⚠ **不是"endTime 最晚的那一场"**（2026-09-29 修）：那样一旦有午睡，
     *   午睡的 endTime 更晚 → 它被当成"最近一晚"，整晚睡眠**凭空消失**。
     *   改成按时长挑主睡眠后，"睡了一晚 + 中午小睡"看到的仍是那一整夜。
     *
     * 不用"今天 0 点起"是因为睡眠是跨零点的 —— 02:05~07:42 这种会话起始于昨天，
     * 用自然日切会把一整晚切碎甚至整晚丢掉。
     *
     * 没有睡眠读权限、或三星健康里没有睡眠记录时 [SleepBundle.night] 为 null
     * （界面据此区分"没授权/没数据"）。
     */
    suspend fun readSleepBundle(context: Context): SleepBundle {
        if (!hasSleepPermission(context)) return SleepBundle.EMPTY
        return runCatching {
            val since = java.time.LocalDate.now().minusDays(1).atTime(18, 0)
            val until = java.time.LocalDateTime.now()
            var token: String? = null
            // 窗口内**所有**会话（而不是边走边只留一个）：主睡眠要跟午睡比时长才知道是谁。
            val candidates = ArrayList<SleepHours.Span>()
            val scoreByStart = HashMap<Long, Int?>()
            // 每个会话各自的阶段段（key = 会话 startTime）：只取主睡眠那一场的，
            // 午睡的阶段段不属于"这一晚"，混进来会把睡眠图拉成不连续的两段。
            val stagesByStart = HashMap<Long, List<SleepSession.SleepStage>>()
            do {
                var builder = DataTypes.SLEEP.readDataRequestBuilder
                    .setLocalTimeFilter(LocalTimeFilter.of(since, until))
                    .setOrdering(Ordering.DESC)
                    .setLimit(50)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val sessions = p.getValue(DataType.SleepType.SESSIONS) ?: continue
                    // 一个数据点里可能有多个会话，全都收下（原先这里取 max，只留一个）
                    for (s in sessions) {
                        val sMs = s.startTime.toEpochMilli()
                        val eMs = s.endTime.toEpochMilli()
                        if (eMs <= sMs) continue
                        candidates.add(SleepHours.Span(sMs, eMs))
                        scoreByStart[sMs] = p.getValue(DataType.SleepType.SLEEP_SCORE)
                        stagesByStart[sMs] = s.stages.orEmpty()
                    }
                }
                token = resp.pageToken
            } while (token != null)

            // ⚠ 挑主睡眠，而不是挑"最晚结束"的那一场 —— 后者会把午睡当整晚（见上文）。
            val chosen = SleepHours.mainSession(candidates) ?: return@runCatching SleepBundle.EMPTY
            val startMs = chosen.startMs
            val endMs = chosen.endMs
            val score = scoreByStart[startMs]

            val stages = mergeSleepStages(stagesByStart[startMs])

            // 血氧序列一次读两个窗口（睡眠期 + 今日全天），但只发一趟分页请求：
            //   · 起点取「今天 0 点」与「入睡时刻」里更早的那个 —— 23:00 入睡的会话起点在昨天，
            //     只从今天 0 点读会把整晚的前半段截掉；
            //   · 终点取**现在**而不是入睡结束时刻 —— 这样同一趟读取顺带拿到了今天的全天数据，
            //     血氧页的全天小时柱状图不必再去读第二遍三星健康。
            val day0 = java.time.LocalDate.now()
                .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            val spo2All = readSpo2SeriesBetween(context, minOf(day0, startMs), System.currentTimeMillis())
            val spo2 = spo2All.filter { it.first in startMs..endMs }
            val spo2Today = spo2All.filter { it.first >= day0 }
            val hr = readHrSeriesBetween(context, startMs, endMs)
            val hrSd = hrStdDev(hr)

            val night = SleepNightDto(
                startMs = startMs,
                endMs = endMs,
                score = score,
                stages = stages,
                spo2Avg = spo2.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt(),
                spo2Min = spo2.minOfOrNull { it.second },
                hrAvg = hr.takeIf { it.isNotEmpty() }?.map { it.second }?.average()?.toInt(),
                hrMin = hr.minOfOrNull { it.second },
                hrMax = hr.maxOfOrNull { it.second },
            )
            SleepBundle(
                night = night, spo2 = spo2, hr = hr, spo2Today = spo2Today, hrSd = hrSd,
                // 除主睡眠外的会话（午睡），界面显示"另外还有 N 段小睡"
                naps = SleepHours.otherSessions(candidates, chosen),
            )
        }.getOrElse {
            storeRef = null
            SleepBundle.EMPTY
        }
    }

    /**
     * 把 SDK 逐条阶段记录合并成**连续段**。
     *
     * 两条相邻记录若阶段相同、且间隙不超过 [GAP_MS]（SDK 会在阶段切换处留几十毫秒空档），
     * 就并成一段。不合并的话一晚会有上百段：阶梯图会画成锯齿、详情页列表也读不完。
     * 间隙超过阈值的**同类型**段故意不合并 —— 那是"中间睡熟了又醒、随后回到浅睡"，
     * 合并会把真实的中断藏起来。
     */
    private fun mergeSleepStages(raw: List<SleepSession.SleepStage>?): List<SleepStageDto> {
        val sorted = raw.orEmpty()
            .filter { it.endTime.isAfter(it.startTime) }
            .sortedBy { it.startTime }
        val out = ArrayList<SleepStageDto>(sorted.size)
        for (st in sorted) {
            val kind = when (st.stage) {
                DataType.SleepType.StageType.AWAKE -> SleepStageKind.AWAKE
                DataType.SleepType.StageType.LIGHT -> SleepStageKind.LIGHT
                DataType.SleepType.StageType.DEEP -> SleepStageKind.DEEP
                DataType.SleepType.StageType.REM -> SleepStageKind.REM
                else -> SleepStageKind.UNKNOWN
            }
            val s = st.startTime.toEpochMilli()
            val e = st.endTime.toEpochMilli()
            val last = out.lastOrNull()
            if (last != null && last.kind == kind && s - last.endMs <= GAP_MS) {
                out[out.size - 1] = last.copy(endMs = e)
            } else {
                out.add(SleepStageDto(s, e, kind))
            }
        }
        return out
    }

    /**
     * 取某时间窗内的血氧序列（epoch 毫秒 → 血氧%）。
     *
     * 优先用 `SERIES_DATA`（三星健康睡眠期血氧是**每分钟一个点**的序列），
     * 拿不到才退回数据点自身的 `OXYGEN_SATURATION`。这是睡眠页那张细密折线的唯一来源，
     * 只读单点的话整晚只会得到一个值。
     *
     * 单位换算统一走 [com.gemini.healthbridge.core.Spo2.percent]，
     * **不要**在这里再写 `* 100`（那个 bug 的完整解释见 Spo2 的类注释）。
     */
    suspend fun readSpo2SeriesBetween(
        context: Context, startMs: Long, endMs: Long,
        /**
         * ⚠ 读**出错**时回调（区别于"读到空列表"）。2026-10-13 加：按日历史那一路要
         *   把「三星健康读不到」与「那一天确实没有数据」分开说（"连不上"≠"没有"，
         *   见 `SamsungHealthReader` 里 `grantedWithRetry` 的三态说明）。
         *   ⚠ 默认 `null` ⇒ 老调用点行为**一字不变**（出错仍然静默返回空表）。
         */
        onError: ((Throwable) -> Unit)? = null,
    ): List<Pair<Long, Int>> {
        if (granted(context).none {
                it.accessType == AccessType.READ && it.dataType == DataTypes.BLOOD_OXYGEN
            }
        ) return emptyList()
        return runCatching {
            val since = Instant.ofEpochMilli(startMs).minus(Duration.ofMinutes(5))
            val out = mutableListOf<Pair<Long, Int>>()
            var token: String? = null
            do {
                var builder = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                    // 上界进请求（理由同 `readSkinTempBetween`：只给下界会一路读到"现在"）
                    .setInstantTimeFilter(InstantTimeFilter.of(since, Instant.ofEpochMilli(endMs)))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val series = p.getValue(DataType.BloodOxygenType.SERIES_DATA)
                    if (!series.isNullOrEmpty()) {
                        for (o in series) {
                            val t = o.startTime.toEpochMilli()
                            if (t in startMs..endMs) out.add(t to Spo2.percent(o.oxygenSaturation))
                        }
                    } else {
                        val t = p.startTime.toEpochMilli()
                        val v = p.getValueOrDefault(DataType.BloodOxygenType.OXYGEN_SATURATION, 0f)
                        if (v > 0f && t in startMs..endMs) {
                            out.add(t to Spo2.percent(v))
                        }
                    }
                }
                token = resp.pageToken
            } while (token != null)
            out.sortedBy { it.first }
        }.getOrElse { e ->
            storeRef = null
            onError?.invoke(e)
            emptyList()
        }
    }

    /** 取某时间窗内的心率序列（epoch 毫秒 → 次/分）。睡眠页心率柱状图与详情页用。 */
    suspend fun readHrSeriesBetween(
        context: Context, startMs: Long, endMs: Long,
        /** 读**出错**时回调（区别于"读到空列表"），说明见 `readSpo2SeriesBetween`。 */
        onError: ((Throwable) -> Unit)? = null,
    ): List<Pair<Long, Int>> {
        if (granted(context).none { it.accessType == AccessType.READ }) return emptyList()
        return runCatching {
            val since = Instant.ofEpochMilli(startMs).minus(Duration.ofMinutes(5))
            val out = mutableListOf<Pair<Long, Int>>()
            var token: String? = null
            do {
                var builder = DataTypes.HEART_RATE.readDataRequestBuilder
                    // 上界进请求（理由同 `readSkinTempBetween`：只给下界会一路读到"现在"）
                    .setInstantTimeFilter(InstantTimeFilter.of(since, Instant.ofEpochMilli(endMs)))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val t = p.startTime.toEpochMilli()
                    val v = p.getValueOrDefault(DataType.HeartRateType.HEART_RATE, 0f)
                    if (v > 0f && t in startMs..endMs) out.add(t to v.roundToInt())
                }
                token = resp.pageToken
            } while (token != null)
            out.sortedBy { it.first }
        }.getOrElse { e ->
            storeRef = null
            onError?.invoke(e)
            emptyList()
        }
    }

    /**
     * 按日现读的结果。
     *
     * ⚠⚠ **必须能区分「读不到」与「那天没有数据」**：前者要如实说原因（未授权 / 连不上），
     *   后者才是"这一天没有记录"。把两者折成一个空列表，用户会以为"那天就是没数据"，
     *   而这正是 `samsungUnreadable`（v121）踩过的那个坑。
     */
    sealed interface DayVitals {
        data class Ok(
            val hr: List<Pair<Long, Int>>,
            val spo2: List<Pair<Long, Int>>,
        ) : DayVitals

        /** 未授权 / 三星健康连不上 —— 界面说清原因，**不能**当作"那天没有数据"。 */
        data object Unreadable : DayVitals
    }

    /**
     * 按需现读**某一天**（本地日）的心率与血氧读数（2026-10-13）。
     *
     * ## 为什么需要它
     *
     * `heart_rate` / `spo2` 两张表里**根本没有历史**：
     *   · 自动同步只覆盖「今天」（血氧只落 48 小时内的**点记录**）；
     *   · 手动「补导历史记录」`importRecent` 只导血压与心率 —— **血氧一条都不导**。
     *
     * 于是把日历往前拨几天，心率/血氧几乎什么都看不到，而**同一晚的睡眠记录里明明有**
     * 平均/最低心率与血氧（那份存在 `sleep_nights`，`syncSleepDays` 自动补 14 晚）。
     * 用户 2026-10-13 的原话：「为什么切换以前的历史记录就没有对应的更多记录了，
     * 明明睡眠记录里都还有数据」。
     *
     * ⇒ 这一页改成**按日现读三星健康**：三星那边有的（含睡眠期每分钟的血氧/心率序列）
     *   当场读出来显示，与库里已有的记录合并。
     *
     * ⚠⚠ **刻意不落库**（与 `syncToday` 里那条既定判断一致）：睡眠期血氧是**每分钟一个点**，
     *   一晚 400+ 条；落进 `spo2` 表会把「最近记录」列表整个刷屏、真记录被挤到 200 条之外。
     *   序列只是"画图/看那一天的素材"，现读即用，离开这一页就没了。
     *
     * ⚠ 时间窗用**本地日**的 `[当天 0 点, 次日 0 点)`（与「今天」的唯一口径 `todayStartMs()` 一致）；
     *   `readSpo2SeriesBetween` / `readHrSeriesBetween` 内部会各自往前留 5 分钟重叠。
     */
    suspend fun readDayVitals(
        context: Context,
        epochDay: Long,
        /**
         * 要不要读这两路。⚠ 按日页**只要它那一类**（心率页不读血氧、血氧页不读心率）：
         * 一次范围读是**分页**的（每页 200 条，一晚睡眠心率要翻好几页），
         * 白读另一路会把打开这一页的等待时间翻倍。
         */
        wantHr: Boolean = true,
        wantSpo2: Boolean = true,
    ): DayVitals {
        val zone = ZoneId.systemDefault()
        val startMs = LocalDate.ofEpochDay(epochDay).atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = LocalDate.ofEpochDay(epochDay + 1).atStartOfDay(zone)
            .toInstant().toEpochMilli() - 1
        return readWindowVitals(context, startMs, endMs, wantHr, wantSpo2)
    }

    /**
     * 按**任意时间窗**现读心率 / 血氧（2026-10-13）。
     *
     * ⚠ 与 [readDayVitals] 是同一件事，只是窗口不同：那一头按"本地日"，
     *   而**历史晚详情页**要按"那一晚"（`sleep_nights.startMs..endMs`，跨零点）。
     *   两份窗口规则写在各自的入口里，读取与三态判定**只写这一份**。
     *
     * 三态、`onError`、"两条路都空且出过错才算读不到"这些判据见 [readDayVitals] 的说明。
     */
    suspend fun readWindowVitals(
        context: Context,
        startMs: Long,
        endMs: Long,
        wantHr: Boolean = true,
        wantSpo2: Boolean = true,
    ): DayVitals {
        // ⚠ 三态判据用 `grantedWithRetry`（`null` = **问不出来**），与 `syncToday` 一致：
        //   只判"有没有权限"的话，三星健康连不上会被当成"未授权"，
        //   把用户指向一个他做不了也没用的动作（重新授权）。
        val g = grantedWithRetry(context) ?: return DayVitals.Unreadable
        val canHr = wantHr && g.any { it.accessType == AccessType.READ }
        val canSpo2 = wantSpo2 && g.any {
            it.accessType == AccessType.READ && it.dataType == DataTypes.BLOOD_OXYGEN
        }
        if (!canHr && !canSpo2) return DayVitals.Unreadable

        var failed = false
        val hr = if (canHr) {
            readHrSeriesBetween(context, startMs, endMs) { failed = true }
        } else emptyList()
        val spo2 = if (canSpo2) {
            readSpo2SeriesBetween(context, startMs, endMs) { failed = true }
        } else emptyList()

        // ⚠ 只有"出过错 **且** 一条都没读回来"才算读不到：
        //   两条路里有一条真的读回来了，就说明通道是通的（另一条可能只是那天没有）。
        if (failed && hr.isEmpty() && spo2.isEmpty()) return DayVitals.Unreadable
        return DayVitals.Ok(hr, spo2)
    }

    /** 读取三星健康的近期血氧记录（推送给手表显示）。 */
    suspend fun readSpo2Records(context: Context, hours: Long = 48): List<com.gemini.healthbridge.core.Spo2RecordDto> {
        if (granted(context).none {
                it.accessType == AccessType.READ && it.dataType == DataTypes.BLOOD_OXYGEN
            }
        ) return emptyList()
        return runCatching {
            val since = Instant.now().minus(Duration.ofHours(hours))
            val out = mutableListOf<com.gemini.healthbridge.core.Spo2RecordDto>()
            var token: String? = null
            do {
                var builder = DataTypes.BLOOD_OXYGEN.readDataRequestBuilder
                    .setInstantTimeFilter(InstantTimeFilter.since(since))
                    .setOrdering(Ordering.DESC)
                    .setLimit(200)
                if (token != null) builder = builder.setPageToken(token)
                val resp = store(context).readData(builder.build())
                for (p in resp.dataList) {
                    val raw = p.getValueOrDefault(DataType.BloodOxygenType.OXYGEN_SATURATION, 0f)
                    if (raw > 0f) {
                        out.add(
                            com.gemini.healthbridge.core.Spo2RecordDto(
                                t = p.startTime.toEpochMilli(),
                                // 单位换算走 Spo2.percent：这里原来写的是 (raw * 100).toInt()，
                                // 且**漏了 coerce**——真实 98% 会被推成 9800 送进手表。
                                pct = Spo2.percent(raw),
                            )
                        )
                    }
                }
                token = resp.pageToken
            } while (token != null)
            out
        }.getOrElse {
            storeRef = null
            emptyList()
        }
    }

    /** 导入最近 days 天的血压与心率，返回可读结果描述。 */
    /**
     * 补导最近 [days] 天的心率与血压。
     *
     * ⚠ 返回值是**待求值**文案（[ResText]）而不是 `String`：它会被交给
     * `SyncRepository.setEvent`，而那个状态会一直留在界面上 —— 存已求值的串
     * 会让它切语言后停在旧语言（2026-10-02 用户截图实付）。
     */
    suspend fun importRecent(context: Context, days: Long = 30): ResText {
        return runCatching {
            val since = Instant.now().minus(Duration.ofDays(days))
            val bp = importBloodPressure(context, since)
            val hr = importHeartRate(context, since)
            ResText.of(R.string.s_fc7d487c, bp, hr, days)
        }.getOrElse { ResText.of(R.string.s_acc1a249, it.javaClass.simpleName, it.message) }
    }

    private suspend fun importBloodPressure(context: Context, since: Instant): Int {
        val dao = SyncRepository.database.bloodPressureDao()
        var total = 0
        var token: String? = null
        do {
            var builder = DataTypes.BLOOD_PRESSURE.readDataRequestBuilder
                .setInstantTimeFilter(InstantTimeFilter.since(since))
                .setOrdering(Ordering.DESC)
                .setLimit(200)
            if (token != null) builder = builder.setPageToken(token)
            val resp = store(context).readData(builder.build())
            for (p in resp.dataList) {
                val sys = p.getValueOrDefault(DataType.BloodPressureType.SYSTOLIC, 0f)
                val dia = p.getValueOrDefault(DataType.BloodPressureType.DIASTOLIC, 0f)
                if (sys <= 0f || dia <= 0f) continue
                val pulse = p.getValueOrDefault(DataType.BloodPressureType.PULSE_RATE, 0)
                dao.insert(
                    BloodPressureEntity(
                        systolic = sys.toInt(), diastolic = dia.toInt(),
                        pulse = pulse.takeIf { it > 0 },
                        recordedAt = p.startTime.toEpochMilli(),
                        source = "samsung",
                    )
                )
                total++
            }
            token = resp.pageToken
        } while (token != null)
        return total
    }

    private suspend fun importHeartRate(context: Context, since: Instant): Int {
        val dao = SyncRepository.database.heartRateDao()
        var total = 0
        var token: String? = null
        do {
            var builder = DataTypes.HEART_RATE.readDataRequestBuilder
                .setInstantTimeFilter(InstantTimeFilter.since(since))
                .setOrdering(Ordering.DESC)
                .setLimit(200)
            if (token != null) builder = builder.setPageToken(token)
            val resp = store(context).readData(builder.build())
            for (p in resp.dataList) {
                val bpm = p.getValueOrDefault(DataType.HeartRateType.HEART_RATE, 0f)
                if (bpm <= 0f) continue
                dao.insert(
                    HeartRateEntity(
                        bpm = bpm.toDouble(),
                        measuredAt = p.startTime.toEpochMilli(),
                        source = "samsung",
                    )
                )
                total++
            }
            token = resp.pageToken
        } while (token != null)
        return total
    }
}
