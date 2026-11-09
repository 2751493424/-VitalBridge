package com.gemini.healthbridge.phone

import android.content.Context
import androidx.room.withTransaction
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.AckMessage
import com.gemini.healthbridge.core.Bp
import com.gemini.healthbridge.core.DeviceSource
import com.gemini.healthbridge.core.db.BloodPressureEntity
import com.gemini.healthbridge.core.db.EcgRecordEntity
import com.gemini.healthbridge.core.db.EcgWaveEntity
import com.gemini.healthbridge.core.db.HeartRateEntity
import com.gemini.healthbridge.core.db.SpO2Entity
import com.gemini.healthbridge.core.db.TemperatureEntity
import com.gemini.healthbridge.core.db.toEntity
import com.gemini.healthbridge.core.HealthItemDto
import com.gemini.healthbridge.core.HelloMessage
import com.gemini.healthbridge.core.HrvSource
import com.gemini.healthbridge.core.RecordType
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.SyncPaths
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.Json
import java.time.Instant

/** 手机端共享状态与发送逻辑。数据落库在 [AppDatabase]。 */
object SyncRepository {
    private lateinit var db: AppDatabase
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * 单批最多收多少条（2026-11-03 加）。
     *
     * ⚠ 手表一批通常几条到几十条；这里封顶挡的是**异常/恶意输入**：
     *   蓝牙那条链路是一次帧就调进本函数，一条 JSON 里塞十万个 items 会让手机
     *   连写十万行（几分钟的高频 fsync），表现为发烫、卡顿、同步"永远不结束"。
     *   超出部分**整批截断**（不是随机丢中间几条），并在日志里留痕。
     */
    private const val MAX_ITEMS_PER_BATCH = 200

    /**
     * 手动录入真实血压的**业务限额**（2026-11-04 用户要求）。
     *
     * 为什么要有：预估靠的是「一组一组的配对样本」，一天连着录 8 组不会让预估更准，
     * 反而把当天的心电配对全挤在同一时段，样本在时间上高度相关 —— 对回归毫无增益。
     * 用户原话：「每组间隔 30 分钟以上，否则记录的数据将会无效且对于预估毫无意义」。
     *
     * ⚠ 这是**业务规则**，不是数据合法性：所以走 [ManualBpOutcome.blocked] 拒答，
     *   而不是 `Bp.Check.Invalid`（那个是"定义上不可能"，比如收缩压 ≤ 舒张压）。
     */
    /**
     * 每日手动录入上限（组）· 相邻两组最短间隔（毫秒）。
     *
     * ⚠⚠ **`internal` 而不是 private**：向导文案里要印这两个数（「每天最多 4 组」
     *   「间隔 30 分钟以上」）。文案另写一份字面量的话，改一处就会出现
     *   「说明写 4 组、实际挡在第 3 组」—— 这是这个项目反复踩的"两份真源"坑。
     */
    internal const val MAX_MANUAL_PER_DAY = 4
    internal const val MIN_MANUAL_GAP_MS = 30 * 60 * 1000L

    /**
     * 单条心电波形的 CSV 字符上限（约 6 万个采样点）。
     *
     * 正常 30 秒 @ 500Hz ≈ 1.5 万点。封顶是为了挡住"一个超长 wave 字段"：
     * `split(',')` 会先按逗号切出**上百万个 String**，那一步就 OOM 了
     * （还没走到解析成 Float 的地方）。
     */
    private const val MAX_WAVE_CHARS = 600_000

    private val _device = MutableStateFlow<HelloMessage?>(null)
    val device: StateFlow<HelloMessage?> = _device

    // ⚠⚠ 这里曾经是 `MutableStateFlow<String>`：文案在「事件发生」那一刻就**求好存进去**，
    //   之后没有任何时机重算 —— 切语言后它会一直停在旧语言，直到下一次同步。
    //   用户 2026-10-02 的截图就是同一屏里「一行英文、紧挨着一行中文」。
    //   现在存的是**待求值**的 [ResText]（key + 参数），求值推迟到 Compose 渲染那一刻。
    //   详见 `core/ResText.kt` 与 `LanguageStore.resText`。
    //
    // ⚠ 初值从「等待手表连接…」改成**空**（2026-10-10）：上面那行蓝牙状态已经写着
    //   「请打开手表端进行连接」，再挂一行「等待手表连接…」是**同义重复**，
    //   而且新文案是"请你去做一件事"、旧文案是"我在等" —— 两句并排看着自相矛盾。
    //   空 = 这一行不渲染（界面那边判 `isNotEmpty()`），有真实同步事件时才出现。
    private val _event = MutableStateFlow(ResText.EMPTY)
    val event: StateFlow<ResText> = _event

    fun init(database: AppDatabase) {
        db = database
    }

    val database: AppDatabase get() = db

    /** 手表发来的 hello：更新连接状态。 */
    fun onHello(hello: HelloMessage) {
        _device.value = hello
        // ⚠⚠ 这里**不再写事件**「手表已连接：X」（2026-10-13 删）。
        //
        // 它本来想在界面上报一句"连上了"，但 `HELLO` 是**每个同步周期都会发**的
        // （启动 / 连上 / 同步完成三时机，见 `SyncRepository` 的握手说明），于是：
        //   ① 首页那张卡上它**压住了真正的同步结果**（下一次事件一来就被覆盖），
        //   ② 而断开之后这句旧事件**还留在卡上**，与旁边的「蓝牙：请打开手表端进行连接」
        //      直接打架 —— 用户截图里那三句互相矛盾的结论就是这么来的。
        //   ③ 连接状态本来就有**结构化**的地方说：状态卡的 `手表在线：X`（判 `connected`）。
        //
        // ⇒ 判据：**只有"同步结果"才配占这一行**；"连上了"是状态，不是结果。
        //   （核心 key `s_4907d3af` 留在 Text.kt 里没删 —— 删它要连带改生成物，
        //     而它已经没有任何调用点。）
    }

    /**
     * 发一条状态事件。
     *
     * ⚠ 形参类型是 [ResText] 而**不是** `String`：传一个已经求好值的字符串进来，
     *   就等于放弃跟随语言（本类型的全部意义所在）。语言无关的内容用
     *   [ResText.raw]，其余一律 [ResText.of] / [ResText.core]。
     */
    fun setEvent(e: ResText) {
        _event.value = e
    }

    fun onDone(hello: HelloMessage) {
        _device.value = hello
        // 这条串是"同步完成"的**唯一**回执，必须把每一种记录类型都列全：
        // 漏掉某一类时它的待同步数就永远不会出现在界面上，用户只会看到同步一直"没结束"。
        // （活动是 #7 加的类型，这里原先漏了，与 #9 一起补上。）
        _event.value = ResText.core("s_5950ff31", hello.pendingHr, hello.pendingBp) +
            ResText.core("s_4685eacb", hello.pendingEcg, hello.pendingSpo2, hello.pendingTemp) +
            ResText.core("s_bda8a54f", hello.pendingBia, hello.pendingActivity) +
            ResText.core("s_db3f21be", hello.pendingPpg, hello.pendingHrv)
    }

    /** 收到手表一批数据：去重入库，返回入库条数（用于 ack）。 */
    suspend fun insertSync(msg: SyncMessage): Int {
        if (msg.items.isEmpty()) return 0
        val items = if (msg.items.size > MAX_ITEMS_PER_BATCH) {
            android.util.Log.w(
                "SyncRepository",
                "一批 ${msg.items.size} 条超过上限 $MAX_ITEMS_PER_BATCH，已截断（type=${msg.type}）",
            )
            msg.items.take(MAX_ITEMS_PER_BATCH)
        } else {
            msg.items
        }
        var inserted = 0
        // ⚠⚠ 整批放进**一个事务**（2026-11-03 改）：原来每条 insert 都是独立事务，
        //   一批 100 条 = 100 次提交 = 100 次 fsync。真机上的表现就是同步时明显卡顿、
        //   掉电快，而且并发写（帧消费）时更容易撞 SQLITE_BUSY。
        db.withTransaction {
            for (item in items) {
                // ⚠ 来源**不能**用 `msg.deviceId` —— 那是手表型号（`Build.MODEL`，
                //    例如 SM-R900），而来源要表达的是「这条数据从哪来」。
                //    混用两者的代价在数据回填上暴露过：回填要判断「这条记录是不是我们
                //    自己产生的」，本来可以是干净的白名单（`source` 以 `watch_` 开头），
                //    但因为库里存的是型号，白名单一条都认不出来，整个回填等于没做。
                //    详见 `DeviceSource` 与 `Backfill.isOurs` 的说明。
                val src = when (msg.type) {
                    RecordType.HR -> com.gemini.healthbridge.core.DeviceSource.HR
                    RecordType.BP -> com.gemini.healthbridge.core.DeviceSource.MANUAL
                    RecordType.SPO2 -> com.gemini.healthbridge.core.DeviceSource.O2
                    // 其余类型（体温 / 体成分）在 `insertItem` 里本来就固定成规范值。
                    else -> msg.deviceId
                }
                if (insertItem(msg.type, src, item)) inserted++
            }
        }
        // 匿名统计（2026-10-28）：**只报「哪一类记录、成没成」，不报任何数值**。
        // 这是「要不要继续维护某一类测量」的唯一依据（例如手表端血氧还有人用吗）。
        // ⚠ 放在这里而不是各页面里：所有测量数据都从这条漏斗进库，
        //   在页面里逐个埋点必然漏掉「手表先存下、下次连上才同步」那一半。
        Stats.event("measure_sync", msg.type, if (inserted > 0) "ok" else "fail")
        // ── 校准配对：手表录的血压同样要配上（2026-10-02）──
        //
        // ⚠⚠ 为什么在这里、而不是只在 `addManualBloodPressure` 里：
        //   那个函数只管**手机端手动录入**。手表端录的血压走的是
        //   「手表 → 蓝牙 → 本函数」这条路，落完库就结束了 ——
        //   结果就是「手表录的血压**永远**配不上腕表信号」，用户按
        //   「手表录血压 → 紧接着测心电」走完流程，配对行一条都不会生成。
        //
        // ⚠⚠ 为什么收到**心电/心率/HRV**时也要试，而不是只在收到血压时：
        //   配对要两边的数据都在。用户先测血压（此时心电还没测 → 配不上），
        //   再测心电 —— 而那条血压**不会再同步一次**（`synced=1` 了）。
        //   所以「后到的那一半」到达时必须再试一次，否则永远差一步。
        if (msg.type == RecordType.BP || msg.type == RecordType.ECG ||
            msg.type == RecordType.HR || msg.type == RecordType.HRV
        ) {
            // ⚠ **尽力而为**：配对失败绝不影响同步本身（同步已经成功了）。
            runCatching {
                BpCalibration.backfill(
                    db = db,
                    since = System.currentTimeMillis() - BpCalibration.AUTO_PAIR_LOOKBACK_MS,
                )
            }
        }
        return inserted
    }

    private suspend fun insertItem(type: String, source: String, item: HealthItemDto): Boolean {
        return when (type) {
            RecordType.HR -> {
                val bpm = item.bpm ?: return false
                db.heartRateDao().insert(
                    HeartRateEntity(
                        bpm = bpm, measuredAt = item.recordedAt, source = source,
                    )
                ) > 0
            }
            RecordType.BP -> {
                val sys = item.systolic ?: return false
                val dia = item.diastolic ?: return false
                db.bloodPressureDao().insert(
                    BloodPressureEntity(
                        systolic = sys, diastolic = dia, pulse = item.pulse,
                        note = item.note, recordedAt = item.recordedAt, source = source,
                        // ⚠ 手表录的血压也带体位（手表的录入页会问）。
                        //   老版本手表发来的 JSON 没有这个字段 ⇒ null，**不等于坐姿**。
                        posture = item.posture,
                    )
                ) > 0
            }
            RecordType.SPO2 -> {
                val pct = item.spo2 ?: return false
                com.gemini.healthbridge.core.db.SpO2Entity(
                    percent = pct, heartRate = item.bpm?.toInt(),
                    measuredAt = item.recordedAt, source = source,
                ).let { db.spo2Dao().insert(it) } > 0
            }
            // 体温：手表点检的**绝对**腕部皮肤温度。
            // 值原样落库、不做任何取整 —— 0.1 ℃ 的精度就是这个指标的全部有效信息
            // （与血氧那种整数百分比不同）。
            // 来源**故意固定**成 TempSource.WATCH，而不是用传进来的手表型号（其它类型都是这么做的）：
            // 手机端要靠"来源前缀"把「手表点检的绝对值」和「三星健康的夜间值」干净地分成两组
            // （两者的量纲不同，混进同一条曲线会画出"体温 0.3 ℃"）。这比按前后缀去猜可靠得多。
            RecordType.TEMP -> {
                val c = item.tempC ?: return false
                if (!com.gemini.healthbridge.core.Temperature.isValid(c)) return false
                TemperatureEntity(
                    celsius = c, ambientC = item.ambientC,
                    measuredAt = item.recordedAt,
                    source = com.gemini.healthbridge.core.TempSource.WATCH,
                    synced = 1,
                ).let { db.temperatureDao().insert(it) } > 0
            }
            // 体成分：手表 BIA 测量的结果。
            // 来源**故意固定**成 BiaSource.WATCH，而不是用传进来的手表型号（心率/血压那样做）：
            // 手表端自己落库时写的就是 `watch_bia`，手机端跟着写同一个常量，两端对"这条是谁测的"
            // 才有一致的答案。否则手机端要靠"来源不是 samsung_body"去反推，多一层易错的推断。
            // 固定之后手机端分组只是一次等值比较：== BiaSource.SAMSUNG → 三星健康，其余 → 手表。
            // `toEntity` 默认 synced=1：手机端不参与上行同步，落库即"已同步"。
            RecordType.BIA -> {
                val b = item.bia ?: return false
                db.bodyCompositionDao().insert(
                    b.toEntity(item.recordedAt, com.gemini.healthbridge.core.BiaSource.WATCH)
                ) > 0
            }
            // 活动片段：来源固定成 ActivitySource.WATCH（与手表端落库时写的常量一致），
            // 这样两端对"这条是谁记的"答案相同，手机端不用靠推断。
            // 手机端不产生活动数据（三星健康不提供第三方可读的逐段活动记录），所以这条链路是单向的。
            RecordType.ACTIVITY -> {
                val a = item.activity ?: return false
                // ⚠ 同上：包络也是 CSV 串，超长会在后续解析时爆内存
                if ((a.envelope?.length ?: 0) > MAX_WAVE_CHARS) return false
                db.activityDao().insert(
                    a.toEntity(item.recordedAt, com.gemini.healthbridge.core.ActivitySource.WATCH)
                ) > 0
            }
            // PPG 原始光信号：手表在血氧测量期间顺带采的那一段。
            // 来源固定成 PpgSource.WATCH，而不是用传进来的手表型号（心率/血压那样做）：
            // 手表端自己落库时写的就是 `watch_ppg`，手机端跟着写同一个常量，两端对
            // "这条是谁测的"才有一致的答案。这条链路是单向的 —— 手机端产不出光信号
            // （三星健康不提供第三方可读的原始光信号）。
            // `toEntity` 默认 synced=1：手机端不参与上行同步，落库即"已同步"，
            // 否则待同步计数会永远挂着一个不会消失的数字。
            RecordType.PPG -> {
                val p = item.ppg ?: return false
                // ⚠ 三段波形是 CSV 串，落库前先挡长度：解析侧（`Ppg.decode`）会 split，
                //   一个超长字段就够让那一步 OOM。宁可丢这一条，也不能让进程挂掉。
                if (listOfNotNull(p.greenWave, p.redWave, p.irWave).any { it.length > MAX_WAVE_CHARS }) {
                    android.util.Log.w("SyncRepository", "PPG 波形超长，已丢弃该条")
                    return false
                }
                db.ppgWaveDao().insert(
                    p.toEntity(item.recordedAt, com.gemini.healthbridge.core.PpgSource.WATCH)
                ) > 0
            }
            // HRV 记录：手表心电结束后随同一条链路推过来（同一次测量的 recordedAt 与心电一致，
            // 手机端靠这个时间戳把两者配对）。落库的是**输入**（逐拍间期），
            // 指标由两端各自的 `Hrv.analyze` 现算 —— 所以这里不做任何数值加工。
            RecordType.HRV -> {
                val h = item.hrv ?: return false
                // ⚠ 逐拍间期也是 CSV 串，理由同 PPG：解析侧会 split
                if ((h.rrMs?.length ?: 0) > MAX_WAVE_CHARS) return false
                db.hrvDao().insert(
                    h.toEntity(item.recordedAt)
                ) > 0
            }
            // 多频阻抗：手表测出来的四个频点**原始值**（手机侧的三星健康不开放任何阻抗数据，
            // 所以这条链路是单向的，手机端的分析全靠它）。来源固定成 MfBiaSource.WATCH，
            // 理由与 BIA/PPG 那条相同：两端必须对"这条是谁测的"给出一致的答案。
            // 落库的是原始值而不是派生指标 —— 比值/趋势由 `MfBia.analyze` 现算。
            RecordType.MF_BIA -> {
                val m = item.mfBia ?: return false
                db.mfBiaDao().insert(
                    m.toEntity(item.recordedAt, com.gemini.healthbridge.core.db.MfBiaSource.WATCH)
                ) > 0
            }
            RecordType.ECG -> {
                // 结论进 ecg_records（心电列表），波形进 ecg_waves（详情页画图）。
                // 两者用同一个 recordedAt，配合 (source, recordedAt) 唯一索引保证重复同步不产生重复行。
                val cls = item.classification ?: "无法判定"
                val at = item.recordedAt
                val inserted = db.ecgDao().insert(
                    EcgRecordEntity(
                        classification = cls,
                        heartRate = item.bpm?.toInt(),
                        note = item.note,
                        recordedAt = at,
                        source = source,
                        synced = 1,
                        // #14 PAT：手表在会话内算好随记录一起发过来（它算不了——需要全速率
                        // 的双路波形，这里只有降采样后的心电）。原样存，不在手机端重算。
                        patMs = item.patMs,
                        patSpreadMs = item.patSpreadMs,
                        patSeriesMs = item.patSeriesMs,
                        patNote = item.patNote,
                        patAlgo = item.patAlgo,
                    )
                ) > 0
                val waveCsv = item.wave
                // ⚠ 长度先挡一道再 split：`split(',')` 对超长串会在"切成字符串数组"
                //   那一步就吃掉大量内存（正常心电约 120K 字符，这里给 5 倍余量）。
                //   超限只丢**波形**，结论行照常入库 —— 波形是富余信息，结论才是主数据。
                if (!waveCsv.isNullOrBlank() && waveCsv.length > MAX_WAVE_CHARS) {
                    android.util.Log.w(
                        "SyncRepository",
                        "心电波形 ${waveCsv.length} 字符超过上限 $MAX_WAVE_CHARS，已跳过波形",
                    )
                } else if (!waveCsv.isNullOrBlank()) {
                    runCatching {
                        val values = waveCsv.split(',').mapNotNull { it.trim().toFloatOrNull() }
                        if (values.isNotEmpty()) {
                            db.ecgWaveDao().insert(
                                EcgWaveEntity(
                                    measuredAt = at,
                                    durationMs = item.durationMs ?: 30_000L,
                                    sampleCount = values.size,
                                    minMv = values.min(),
                                    maxMv = values.max(),
                                    avgMv = values.average().toFloat(),
                                    waveJson = waveCsv,
                                    source = source,
                                    classification = cls,
                                    analyzedHr = item.bpm?.toInt(),
                                    sampleRate = item.sampleRate,
                                    regular = item.regular?.let { if (it) 1 else 0 },
                                    beatCount = item.beatCount,
                                    bandRatio = item.bandRatio,
                                )
                            )
                        }
                    }
                }
                inserted
            }
            else -> false
        }
    }

    /** 向所有可连接的手表节点发送消息。 */
    suspend fun sendToWatch(context: Context, path: String, bytes: ByteArray): Boolean {
        val nodes = watchNodes(context)
        if (nodes.isEmpty()) {
            _event.value = ResText.of(R.string.s_0b116f6c)
            return false
        }
        val client = Wearable.getMessageClient(context)
        var ok = false
        for (node in nodes) {
            ok = runCatching { client.sendMessage(node.id, path, bytes).await() }.isSuccess
        }
        return ok
    }

    /** 把三星健康当日小时统计推送给手表（手表图表优先使用原生全天数据）。 */
    suspend fun sendHourStats(context: Context) {
        runCatching {
            val buckets = SamsungHealthReader.readTodayHourly(context)
            if (buckets.isEmpty()) return
            val msg = com.gemini.healthbridge.core.HourStatsMessage(
                buckets = buckets,
                dateEpochDay = java.time.LocalDate.now().toEpochDay(),
                sleepAvg = SamsungHealthReader.readSleepAvg(context),
            )
            sendToWatch(
                context, SyncPaths.HOUR_STATS,
                json.encodeToString(
                    com.gemini.healthbridge.core.HourStatsMessage.serializer(), msg
                ).toByteArray(Charsets.UTF_8),
            )
        }
    }

    /** 把三星健康的近期血氧记录推送给手表（手表血氧页显示）。 */
    suspend fun sendSpo2Records(context: Context) {
        runCatching {
            val records = SamsungHealthReader.readSpo2Records(context)
            if (records.isEmpty()) return
            val msg = com.gemini.healthbridge.core.Spo2RecordsMessage(records = records)
            sendToWatch(
                context, SyncPaths.SPO2_RECORDS,
                json.encodeToString(
                    com.gemini.healthbridge.core.Spo2RecordsMessage.serializer(), msg
                ).toByteArray(Charsets.UTF_8),
            )
        }
    }

    suspend fun requestSync(context: Context): Boolean {
        val ok = sendToWatch(context, SyncPaths.SYNC_REQUEST, System.currentTimeMillis().toString().toByteArray())
        if (ok) _event.value = ResText.of(R.string.s_91892a5f)
        return ok
    }

    suspend fun sendAck(context: Context, nodeId: String, ack: AckMessage) {
        runCatching {
            Wearable.getMessageClient(context).sendMessage(
                nodeId, SyncPaths.ACK,
                json.encodeToString(AckMessage.serializer(), ack).toByteArray(Charsets.UTF_8),
            ).await()
        }
    }

    suspend fun watchNodes(context: Context): List<Node> {
        return runCatching {
            val cap = Wearable.getCapabilityClient(context)
                .getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            cap.nodes.toList()
        }.getOrElse {
            runCatching {
                Wearable.getNodeClient(context).connectedNodes.await().toList()
            }.getOrDefault(emptyList())
        }
    }

    /** 连接诊断：逐层检查 Google 服务、能力发现、直连节点。 */
    suspend fun diagnose(context: Context): String {
        val sb = StringBuilder()
        val gmsCode = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        sb.append(LanguageStore.string(context, R.string.s_05522527)).append(
            if (gmsCode == ConnectionResult.SUCCESS) LanguageStore.string(context, R.string.s_003751e1) else LanguageStore.string(context, R.string.s_93892b9d, gmsCode)
        )
        try {
            val cap = Wearable.getCapabilityClient(context)
                .getCapability(SyncPaths.CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .await()
            sb.append(LanguageStore.string(context, R.string.s_a3713c6d)).append(cap.nodes.size).append(LanguageStore.string(context, R.string.s_00006450))
            cap.nodes.forEach {
                sb.append("\n  · ").append(it.displayName)
                    .append(" nearby=").append(it.isNearby)
            }
            if (cap.nodes.isEmpty()) sb.append(LanguageStore.string(context, R.string.s_2ac3fe71))
        } catch (e: Exception) {
            sb.append(LanguageStore.string(context, R.string.s_2dbae693)).append(e.javaClass.simpleName)
                .append(": ").append(e.message)
        }
        try {
            val nodes = Wearable.getNodeClient(context).connectedNodes.await()
            sb.append(LanguageStore.string(context, R.string.s_3e42de11)).append(nodes.size).append(LanguageStore.string(context, R.string.s_00006450))
            nodes.forEach {
                sb.append("\n  · ").append(it.displayName)
                    .append(" nearby=").append(it.isNearby)
            }
            if (nodes.isEmpty()) sb.append(LanguageStore.string(context, R.string.s_bfbf6959))
        } catch (e: Exception) {
            sb.append(LanguageStore.string(context, R.string.s_69897d86)).append(e.javaClass.simpleName)
                .append(": ").append(e.message)
        }
        // ⚠ 用结构化的 `connected` 判，**不要** `status.value.contains("已连接")` ——
        // status 是给人看的文案，会随语言变，匹配它在非中文界面下永远为假（模块 #15）。
        if (PhoneBtServer.connected.value) {
            sb.append(LanguageStore.string(context, R.string.s_67fd2aa5))
        }
        return sb.toString()
    }

    // ═══════════════════ 手机端手动录入血压（2026-09-28） ═══════════════════

    /**
     * 手机端手动录入一条血压：落库 + 尝试回填到三星健康与 Health Connect。
     *
     * ## 为什么收在数据层而不是写在界面里
     *
     * 落库与两条回填是**一次操作的三个步骤**。分散在界面里就会出现"只落了库、
     * 忘了回填"这类**只在数据上看得出的差异**；收在一处也方便将来加通道时只改一个地方。
     *
     * ## 三条纪律
     *
     * 1. **校验在这里再跑一次**（界面也会跑）：数据层不能假设调用方校验过了。
     *    判定只有一处实现（[Bp.validate]），两处调的是**同一个函数**，
     *    不构成本项目最忌的"同一个判断两处实现然后漂移"。
     * 2. ⚠ **录入成功与回填成功是两件事**：回填失败**绝不能**让录入失败 ——
     *    否则用户未授权时这个功能**永远不可用**，而他只想把读数记下来
     *    （门禁纪律，见 `Bp.writeBackFailedNote`）。
     * 3. **重复提交如实报告**：唯一索引是 `(source, recordedAt)`，DAO 的 `IGNORE`
     *    会把重复行挡成 -1 —— 那时 [ManualBpOutcome.inserted] 为 false，
     *    界面据此说"已经记录过了"，而不是假装成功。
     *
     * ⚠ 来源用 [DeviceSource.MANUAL]（存储值 `watch_manual`）。它不是"手表录的"的意思 ——
     * 那个常量的 KDoc 本来就定义成「手动录入（手机端或手表端输入）」，而它的 `watch_`
     * 前缀是这个项目表达"**我们自己产生的**（相对三星/导入）」的规范写法
     * （见 `Backfill.isOurs`）。
     */
    suspend fun addManualBloodPressure(
        context: Context,
        systolic: Int,
        diastolic: Int,
        pulse: Int?,
        note: String?,
        quiet: Boolean = false,
        /**
         * 测量体位（`Bp.Posture`）。`null` = 用户没选，**不等于坐姿**。
         *
         * ⚠ 刻意**不给默认值**（除了这个 `null`）：多一个"忘了传"的调用点，
         *   就多一处静默失效 —— 体位是"过期即永失"的输入，漏一次就永久缺一格。
         */
        posture: String? = null,
    ): ManualBpOutcome {
        if (Bp.validate(systolic, diastolic, pulse) is Bp.Check.Invalid) {
            return ManualBpOutcome(inserted = false, writeBackNote = null, calibration = null)
        }

        // ── 业务限额：每天最多 4 组、每组间隔 ≥ 30 分钟（2026-11-04）──
        // ⚠ 只数 [DeviceSource.MANUAL] 这一来源：从三星健康导入的历史读数不是"今天录入的"。
        // ⚠ 超限必须**拒答**并告诉用户原因，不能静默丢弃 —— 静默丢弃会让他以为已经记上了，
        //   然后照着"已记录"去配对，最后发现预估不动，无从排查。
        val atMs = System.currentTimeMillis()
        val bpDao = db.bloodPressureDao()
        val todayCount = bpDao.countBySourceSince(
            DeviceSource.MANUAL,
            SamsungHealthReader.todayStartMs(),
        )
        if (todayCount >= MAX_MANUAL_PER_DAY) {
            return ManualBpOutcome(
                inserted = false, writeBackNote = null, calibration = null,
                blocked = ManualBpOutcome.Blocked.DAILY_LIMIT,
            )
        }
        val lastAt = bpDao.lastAtOf(DeviceSource.MANUAL)
        if (lastAt != null && atMs - lastAt < MIN_MANUAL_GAP_MS) {
            return ManualBpOutcome(
                inserted = false, writeBackNote = null, calibration = null,
                blocked = ManualBpOutcome.Blocked.TOO_SOON,
            )
        }
        val rowId = db.bloodPressureDao().insert(
            BloodPressureEntity(
                systolic = systolic,
                diastolic = diastolic,
                pulse = pulse,
                note = note,
                recordedAt = atMs,
                source = DeviceSource.MANUAL,
                posture = posture,
            )
        )
        if (rowId <= 0) return ManualBpOutcome(inserted = false, writeBackNote = null, calibration = null)

        val at = Instant.ofEpochMilli(atMs)
        // 两条通道**各自独立**：一条失败不影响另一条，也都不影响本机记录。
        val samsungOk = runCatching {
            SamsungHealthReader.writeBloodPressure(context, systolic, diastolic, pulse, at)
        }.getOrDefault(false)
        val hcOk = runCatching {
            HealthConnectManager.writeBloodPressure(
                context, systolic, diastolic, pulse, at, DeviceSource.MANUAL,
            )
        }.getOrDefault(false)

        val failed = mutableListOf<ResText>()
        if (!samsungOk) failed.add(ResText.of(R.string.s_8fc12af0))
        if (!hcOk) failed.add(ResText.raw("Health Connect"))
        // 校准配对：**尽力而为**，失败绝不影响读数本身的保存（`BpCalibration` 的纪律 3）。
        // ⚠ 只在读数真的插进去时才配 —— `rowId <= 0` 那条路径（重复提交）不配，
        //   否则会拿一条不属于这次的读数去配对。
        val outcome = BpCalibration.tryPair(db, rowId, atMs, quiet, posture)
        return ManualBpOutcome(
            inserted = true,
            writeBackNote = if (failed.isEmpty()) null else Bp.writeBackFailedNote(failed),
            calibration = outcome,
        )
    }

    /**
     * 删除一条血压记录（连同它的配对样本）。
     *
     * ## 为什么两处一起删
     *
     * 回归用的样本查询是 `INNER JOIN blood_pressures`，所以删了读数之后那条配对
     * **技术上**已经进不了训练集了。但 `bp_calibration_samples` 里会留一条孤儿行，
     * 而界面上的「已配对 N 组」用的是 `samples.size`（来自 `loadSamples`）——
     * 留着孤儿会让计数**虚高**，用户删了三条却看到还写着 12 组，只会以为删除没生效。
     *
     * ⚠ 返回 `false` 表示这条记录**已经不在了**（别的地方删过 / id 过期）。
     *   界面据此给"已删除"就行 —— 目标状态已经达成，不需要报错。
     *
     * @return 是否真的删掉了一行 `blood_pressures`。
     */
    suspend fun deleteBloodPressure(id: Long): Boolean =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val db = database
            val n = db.bloodPressureDao().deleteById(id)
            // ⚠ 先删样本再判 n：即使读数已被别处删掉，孤儿样本也该顺手清掉（幂等）。
            runCatching { db.bpCalibrationSampleDao().deleteByBp(id) }
            n > 0
        }
}

/**
 * 手机端手动录入血压的结果。
 *
 * ⚠ [writeBackNote] 非空**不代表录入失败** —— 本机记录已经存好了，只是没写进外部平台。
 * 两者必须分开呈现（理由见 `Bp.writeBackFailedNote`）。
 * ⚠ 它是**待求值**文案（[ResText]）而不是 `String`：这条消息会存进
 *   `SyncRepository.event` 那个状态里，存已求值的串会切语言后停在旧语言。
 */
data class ManualBpOutcome(
    val inserted: Boolean,
    val writeBackNote: ResText?,
    /**
     * 校准配对的结果（`null` = 读数压根没插进去）。
     *
     * ⚠ 界面**只用来给一句提示**，不许因为它是 [BpCalibration.Outcome.NoSignal]
     * 就阻止保存或显示错误 —— 那次读数是真的，配不配得上是另一件事。
     */
    val calibration: BpCalibration.Outcome? = null,
    /**
     * 被**业务规则**挡下的原因；`null` = 正常走了插入流程。
     *
     * ⚠ 与 `Bp.Check.Invalid`（数据非法）分开：那是"这个值不可能是血压"，
     *   这里是"这个值可以，但现在不该记"。两者给用户的文案和可执行动作完全不同，
     *   合成一个"保存失败"等于什么都没说。
     */
    val blocked: Blocked? = null,
) {
    /** 被挡下的两种原因，界面据此给不同文案。 */
    enum class Blocked {
        /** 今天已经录够 4 组。 */
        DAILY_LIMIT,

        /** 离上一组手动录入不到 30 分钟。 */
        TOO_SOON,
    }
}
