package com.gemini.healthbridge.wear

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.gemini.healthbridge.core.AckMessage
import com.gemini.healthbridge.core.BiaProfileMessage
import com.gemini.healthbridge.core.BtLink
import com.gemini.healthbridge.core.CycleSummaryMessage
import com.gemini.healthbridge.core.Frame
import com.gemini.healthbridge.core.FrameCodec
import com.gemini.healthbridge.core.FrameKinds
import com.gemini.healthbridge.core.HealthItemDto
import com.gemini.healthbridge.core.HelloMessage
import com.gemini.healthbridge.core.HourBucket
import com.gemini.healthbridge.core.HourStatsMessage
import com.gemini.healthbridge.core.RecordType
import com.gemini.healthbridge.core.ApneaSummaryMessage
import com.gemini.healthbridge.core.EnergySummaryMessage
import com.gemini.healthbridge.core.ExtrasSummaryMessage
import com.gemini.healthbridge.core.OdiSummaryMessage
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.Spo2RecordDto
import com.gemini.healthbridge.core.SleepSummaryMessage
import com.gemini.healthbridge.core.SleepNightDto
import com.gemini.healthbridge.core.Spo2RecordsMessage
import com.gemini.healthbridge.core.StressSummaryMessage
import com.gemini.healthbridge.core.SyncMessage
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.SpO2Entity
import com.gemini.healthbridge.core.db.toSyncDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.OutputStream
import java.io.InputStreamReader
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 蓝牙直连同步（主通道）：手机 Google 服务的 Wearable API 不可用时，
 * 通过系统级蓝牙配对直接 RFCOMM 通信，不依赖任何 Google 服务。
 */
class BluetoothSync(
    private val context: Context,
    private val db: AppDatabase,
    private val scope: CoroutineScope,
) {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }
    private val pending = mutableMapOf<String, CompletableDeferred<AckMessage>>()
    private val connectMutex = kotlinx.coroutines.sync.Mutex()
    private val syncMutex = kotlinx.coroutines.sync.Mutex()
    private val autoRetryStarted = java.util.concurrent.atomic.AtomicBoolean(false)

    @Volatile private var socket: BluetoothSocket? = null
    @Volatile private var out: OutputStream? = null
    @Volatile private var open = false

    private val _state = MutableStateFlow(ResText.of(R.string.s_8ad681c3))
    val state: StateFlow<ResText> = _state

    /**
     * 「上传波形」的结果（**手表端的弹窗内容**）。`null` = 不显示弹窗。
     *
     * ## ⚗⚗ 为什么需要一个新的状态，而不复用 [state]
     *
     * [state] 是首页那条**同步状态条**的内容（连接/同步进度），它渲染在**首页**；
     * 而导出按钮在**接触页/结果页**。⇒ 用户点完导出根本不在那一页，
     * 状态条上写什么都不可能被看到（2026-10-05 用户原话：
     * 「每次点了都不知道导出成功没有」）。
     *
     * ⚠⚠ 更根本的一点：落盘发生在**手机端**，手表**结构上不可能**从
     *   `sendFrame` 的返回值推出"存下来了没有"。所以这个状态的值要等
     *   [FrameKinds.ECG_WAVE_ACK] 回来才定，期间显示"正在导出…"。
     *   没有这条回执时，最诚实的内容只能是"已发送" —— 那既不是成功也不是失败。
     */
    private val _ecgExport = MutableStateFlow<ResText?>(null)
    val ecgExport: StateFlow<ResText?> = _ecgExport

    /**
     * 最近一次导出请求的序号。
     *
     * ⚗ 用途：让**超时判定失效**。发出后起一个延时任务，到期时若序号还是自己那一次
     *   ⇒ 说明回执没来 ⇒ 报"已发送，但手机没有回执"。
     *   收到回执（或又发起一次导出）就 +1 ⇒ 旧任务的判定条件不成立，自动作废。
     * ⚠ 不能用"当前状态是否还是『正在导出』"来判断：那样**两次导出**之间
     *   若有回执晚到，会把新一次的状态误改成超时文案。
     */
    private var exportSeq = 0

    /**
     * 等待手机端落盘回执的时间上限（毫秒）。
     *
     * ⚠ 取值依据：一帧波形约 750~3750 点、UTF-8 十几 KB，蓝牙直连下是**秒级**；
     *   8 秒足够覆盖"发出→手机落盘→回执"整个往返（含手机端 UI 线程排队）。
     *   ⚠ 取太短会把正常情况误报成"没有回执"，取太长用户会以为卡住。
     * ⚠ 超时后**只提示、不重发**：重发会造成同一个文件出现两份，
     *   而用户重按一次按钮的语义比"后台偷偷补发"清楚得多。
     */
    // ⚠ 用 `val` 而不是 `const val`：本类不是 object/companion，`const` 在这里**编译不过**
    //   （报 "Const 'val' is only allowed on top level, in named objects, or in companion objects"）。
    private val EXPORT_ACK_TIMEOUT_MS = 8_000L

    /** 关掉导出结果弹窗（用户点了「知道了」或点弹窗外）。 */
    fun dismissEcgExport() {
        _ecgExport.value = null
    }

    /**
     * 是否已连上手机（模块 #15，2026-09-29）。
     *
     * ⚠ 与手机端  同一条纪律：**状态用结构化字段判，
     * 不要拿显示文案去 ** —— 文案会随语言变，
     * 匹配它在非中文界面下永远为假（表现是状态条颜色不对，且不报错）。
     */
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    /** 手机推送的三星健康当日小时统计（原生全天数据，非空时图表优先使用）。 */
    private val _nativeHourStats = MutableStateFlow<List<HourBucket>>(emptyList())
    val nativeHourStats: StateFlow<List<HourBucket>> = _nativeHourStats
    private var statsEpochDay = -1L

    /** 昨晚睡眠时段平均心率（手机从三星健康计算后推送）。 */
    private val _nativeSleepAvg = MutableStateFlow<Int?>(null)
    val nativeSleepAvg: StateFlow<Int?> = _nativeSleepAvg

    /**
     * 手机推送的最近一晚睡眠摘要（阶段 + 得分 + 睡眠期心率/血氧）。
     * watch 端没有三星健康 Data SDK，这份数据只能由手机转发。
     */
    private val _nativeSleepNight = MutableStateFlow<SleepNightDto?>(null)
    val nativeSleepNight: StateFlow<SleepNightDto?> = _nativeSleepNight

    /** 手机推送的经期一行摘要（模块 #13）。null = 尚未收到；available=false = 未开启。 */
    private val _cycleSummary = MutableStateFlow<CycleSummaryMessage?>(null)
    val cycleSummary: StateFlow<CycleSummaryMessage?> = _cycleSummary

    /** 手机推送的压力/疲劳评分（模块 #12）。null = 尚未收到；available=false = 这次算不出来。 */
    private val _stressSummary = MutableStateFlow<StressSummaryMessage?>(null)
    val stressSummary: StateFlow<StressSummaryMessage?> = _stressSummary

    /**
     * 手机推送的最近一晚氧减指数（模块 #11）。
     *
     * null = 尚未收到。**与其它摘要不同的一点**：手机端在"一条记录都没有"时根本不推，
     * 所以这里收到的一定是真实算过的一晚（要么有结论，要么有原因）。
     */
    /**
     * 初值直接读本地缓存（不像其它摘要那样从 null 开始）：
     * ODI 是**一晚一份的既成事实**，重启后立刻显示"上次那一晚"是有意义的，
     * 而且界面会如实标出是哪一晚（当天写"昨晚"，更早写日期）。
     * 压力分数就不能这么干 —— 它会随基线变化，旧的必须被标成"可能已过时"，
     * 那时"先显示旧值"反而误导。
     */
    private val _odiSummary = MutableStateFlow(OdiSummaryStore.load(context))
    val odiSummary: StateFlow<OdiSummaryMessage?> = _odiSummary

    /**
     * 能量评分摘要（手机读三星健康后推来）。
     *
     * 初值**读本地缓存**（与 ODI 同款）：重启后立刻显示上次那个分数是有意义的，
     * 但界面**必须把"不是今天的"标出来** —— 见 `EnergySummaryStore.glanceText(today)`。
     * 能量评分是"给今天用的准备度"，三天前的 62 分与今天的 62 分不是一回事。
     */
    private val _energySummary = MutableStateFlow(EnergySummaryStore.load(context))
    val energySummary: StateFlow<EnergySummaryMessage?> = _energySummary

    /**
     * 三星扩展指标（活动/饮食/血糖/心律不齐）。**一帧装四块**：
     * 它们的口径都是"三星记了什么"，手表上没有 Data SDK，只能由手机读好推过来。
     */
    private val _extrasSummary = MutableStateFlow(ExtrasSummaryStore.load(context))
    val extrasSummary: StateFlow<ExtrasSummaryMessage?> = _extrasSummary

    /**
     * 睡眠呼吸暂停摘要（手机读三星健康后推来）。
     *
     * 初值读缓存是安全的：手机端在**一条记录都没有时不推这一帧**，
     * 所以不会出现"空推送把已有摘要清掉"（空读取不许清屏）。
     */
    private val _apneaSummary = MutableStateFlow(ApneaSummaryStore.load(context))
    val apneaSummary: StateFlow<ApneaSummaryMessage?> = _apneaSummary

    /** 收到手机推送的血氧记录后自增，UI 据此刷新列表。 */
    val spo2RefreshTick = MutableStateFlow(0)

    /** 请求手机推送三星健康数据（小时统计 + 血氧记录）。 */
    fun refreshFromPhone() {
        scope.launch { runCatching { sendHelloFrame() } }
    }

    fun connectAsync() {
        scope.launch { connect() }
    }

    /**
     * 自动重连：App 存活期间每 20 秒检查一次，断线自动恢复。
     * 已连接时只做一次布尔判断，几乎零开销。
     */
    fun startAutoRetry() {
        if (!autoRetryStarted.compareAndSet(false, true)) return
        scope.launch {
            while (true) {
                if (!open && hasPermission()) {
                    runCatching { connect() }
                }
                kotlinx.coroutines.delay(20_000)
            }
        }
    }

    fun close() {
        open = false
        runCatching { socket?.close() }
    }

    private fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < 31 || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private suspend fun connect(): Boolean {
        if (open) return true
        if (!hasPermission()) {
            _state.value = ResText.of(R.string.s_64c4fbf4)
            return false
        }
        return connectMutex.withLock {
            if (open) return@withLock true
            if (!hasPermission()) {
                _state.value = ResText.of(R.string.s_64c4fbf4)
                return@withLock false
            }
            val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
            if (adapter == null) {
                _state.value = ResText.of(R.string.s_b75c0ab4)
                return@withLock false
            }
            if (!adapter.isEnabled) {
                _state.value = ResText.of(R.string.s_8abd70b3)
                return@withLock false
            }
            // 只挑经典蓝牙/双模设备（耳机等也会列出，逐个限时尝试，连上手机即停）
            val candidates = runCatching {
                adapter.bondedDevices.filter {
                    it.type != BluetoothDevice.DEVICE_TYPE_LE
                }
            }.getOrDefault(emptyList())
            if (candidates.isEmpty()) {
                _state.value = ResText.of(R.string.s_803fd0e1)
                return@withLock false
            }
            for (device in candidates) {
                if (open) return@withLock true
                val name = runCatching { device.name }.getOrDefault("?")
                _state.value = ResText.of(R.string.s_dc33a651, name)
                // 连接失败/超时不抛出，跳过该设备继续下一个
                val s = runCatching { connectOne(device) }.getOrNull()
                if (s != null) {
                    socket = s
                    out = s.outputStream
                    open = true
                    _state.value = ResText.of(R.string.s_c60f3406, name)
                    _connected.value = true
                    startReadLoop(s)
                    // 连接建立即打招呼 -> 手机自动回同步请求 -> 全量自动同步
                    runCatching { sendHelloFrame() }
                    return@withLock true
                }
            }
            _state.value = ResText.of(R.string.s_eac308b3)
            false
        }
    }

    /** 限时 RFCOMM 连接。超时/失败返回 null；绝不向已取消的协程恢复结果（否则会崩溃）。 */
    private suspend fun connectOne(device: BluetoothDevice): BluetoothSocket? =
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                val holder = arrayOfNulls<BluetoothSocket>(1)
                val thread = Thread {
                    try {
                        val s = device.createRfcommSocketToServiceRecord(
                            UUID.fromString(BtLink.UUID_STRING)
                        )
                        holder[0] = s
                        s.connect()
                        if (cont.isActive) cont.resume(s) else runCatching { s.close() }
                    } catch (e: Exception) {
                        runCatching { holder[0]?.close() }
                        if (cont.isActive) cont.resumeWithException(e)
                    }
                }
                cont.invokeOnCancellation { runCatching { holder[0]?.close() } }
                thread.isDaemon = true
                thread.start()
            }
        }

    private fun startReadLoop(s: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(s.inputStream, Charsets.UTF_8))
                while (open) {
                    val line = reader.readLine() ?: break
                    val frame = FrameCodec.decode(line) ?: continue
                    onFrame(frame)
                }
            } catch (_: Exception) {
            }
            open = false
            _connected.value = false
            _state.value = ResText.of(R.string.s_74701fdc)
        }
    }

    private suspend fun onFrame(frame: Frame) {
        when (frame.kind) {
            FrameKinds.SYNC_REQ -> syncAll()

            // ⚠ 2026-10-21 全自动 OTA：手机发现有手表新版本 ⇒ 让本机开 10 分钟接收窗口；
            //   装完且手机确认已是最新 ⇒ 让本机立刻关（不必等满 10 分钟）。
            // ⚠ 2026-10-26 `OTA_WINDOW` 已废：它只为"开蓝牙收包窗口"服务，链路已删。

            // 手机 -> 手表：让本机**自己去检查并安装更新**（2026-10-28）。
            //
            // ⚠ 这条不是"推包"：包仍然由本机从 WiFi 下载（`WatchOtaClient.updateFromWifi`），
            //   手机只负责"发现版本不一样"这一个判断 —— 4.6MB 走蓝牙既慢又容易断。
            // ⚠ 必须给回执：否则用户手机上点完只能猜「到底通知到手表没有」。
            FrameKinds.WATCH_UPDATE_REQ -> runCatching {
                val started = WatchUpdateRunner.start(context) { text ->
                    // ⚠ 这个回调在 IO 线程；`sendFrame` 已加锁，跨线程写是安全的
                    sendFrame(Frame(FrameKinds.WATCH_UPDATE_ACK, "ok|$text"))
                }
                sendFrame(
                    Frame(
                        FrameKinds.WATCH_UPDATE_ACK,
                        if (started) "start|" else "busy|",
                    )
                )
            }

            FrameKinds.ACK -> runCatching {
                val ack = json.decodeFromString(AckMessage.serializer(), frame.data)
                pending.remove("${ack.type}:${ack.upTo}")?.complete(ack)
            }

            FrameKinds.HOUR_STATS -> runCatching {
                val msg = json.decodeFromString(HourStatsMessage.serializer(), frame.data)
                // 只接受当天的统计，避免过期数据
                if (msg.dateEpochDay == java.time.LocalDate.now().toEpochDay()) {
                    statsEpochDay = msg.dateEpochDay
                    _nativeHourStats.value = msg.buckets
                    _nativeSleepAvg.value = msg.sleepAvg
                }
            }

            FrameKinds.SPO2_RECORDS -> runCatching {
                val msg = json.decodeFromString(Spo2RecordsMessage.serializer(), frame.data)
                scope.launch {
                    // 先清掉历史遗留的越界值（>100%）。这些行是早先血氧单位换算 bug 的产物，
                    // 因为 (source, measuredAt) 去重，修正后重推也覆盖不掉它们。
                    runCatching { db.spo2Dao().deleteOutOfRange() }
                    for (r in msg.records) {
                        runCatching {
                            db.spo2Dao().insert(
                                SpO2Entity(
                                    percent = r.pct, heartRate = null,
                                    measuredAt = r.t, source = "samsung", synced = 1,
                                )
                            )
                        }
                    }
                    // 无条件刷新：即便这批全被去重挡住（inserted=0），上面的清理也可能刚删掉了坏行，
                    // 界面仍然需要重读。
                    spo2RefreshTick.value += 1
                }
            }

            // 最近一晚睡眠摘要：只接受"今天"推送的，避免第二天还挂着前晚的数据。
            // 与小时统计同一套过期策略（dateEpochDay 校验）。
            FrameKinds.SLEEP_SUMMARY -> runCatching {
                val msg = json.decodeFromString(SleepSummaryMessage.serializer(), frame.data)
                if (msg.dateEpochDay == java.time.LocalDate.now().toEpochDay()) {
                    _nativeSleepNight.value = msg.night
                }
            }

            // 用户档案（身高/体重/性别/年龄）：手表测 BIA 的前提。
            //
            // ⚠ 这里**没有** dateEpochDay 之类的过期校验，而且是三者中唯一"空也要处理"的一帧：
            //    · 档案是**配置**不是时序数据，不存在"过期"；它只有一个当前值，写入即覆盖。
            //    · `profile == null` 表示"手机端也没有资料" —— 这时**必须**让手表知道，
            //      所以这里要主动把本地缓存**清掉**，否则手表会一直拿着上次同步到的旧资料去测量，
            //      而用户在三星健康里把体重改错/清空之后，测出来的是一份基于旧资料的错误结果，
            //      却完全看不出哪里不对。
            FrameKinds.BIA_PROFILE -> runCatching {
                val msg = json.decodeFromString(BiaProfileMessage.serializer(), frame.data)
                val p = msg.profile
                if (p != null && p.isValid()) {
                    BiaProfileStore.save(context, p)
                } else {
                    BiaProfileStore.clear(context)
                }
            }

            // 经期一行摘要（模块 #13）：手机算好推过来，手表只显示一行。
            // available=false 也照收 —— 那表示"手机端没开启经期追踪"，
            // 手表要显示"未开启"，而不是一直显示上一次的旧摘要。
            FrameKinds.CYCLE_SUMMARY -> runCatching {
                val msg = json.decodeFromString(CycleSummaryMessage.serializer(), frame.data)
                CycleSummaryStore.save(context, msg)
                _cycleSummary.value = msg
            }

            // 压力/疲劳评分（模块 #12）：由手机算好推过来（基线要用全部历史记录，
            // 手表只有自己测的那部分）。available=false 也照收，理由同上。
            FrameKinds.STRESS_SUMMARY -> runCatching {
                val msg = json.decodeFromString(StressSummaryMessage.serializer(), frame.data)
                StressSummaryStore.save(context, msg)
                _stressSummary.value = msg
            }

            // 氧减指数（模块 #11）：手表没有三星健康 Data SDK，读不到夜间分钟级血氧，
            // 所以只有"手机算好推过来"这一条路。
            FrameKinds.ENERGY_SUMMARY -> runCatching {
                val msg = json.decodeFromString(EnergySummaryMessage.serializer(), frame.data)
                EnergySummaryStore.save(context, msg)
                _energySummary.value = msg
            }
            FrameKinds.APNEA_SUMMARY -> runCatching {
                val msg = json.decodeFromString(ApneaSummaryMessage.serializer(), frame.data)
                ApneaSummaryStore.save(context, msg)
                _apneaSummary.value = msg
            }
            FrameKinds.EXTRAS_SUMMARY -> runCatching {
                val msg = json.decodeFromString(ExtrasSummaryMessage.serializer(), frame.data)
                ExtrasSummaryStore.save(context, msg)
                _extrasSummary.value = msg
            }
            FrameKinds.ODI_SUMMARY -> runCatching {
                val msg = json.decodeFromString(OdiSummaryMessage.serializer(), frame.data)
                OdiSummaryStore.save(context, msg)
                _odiSummary.value = msg
            }
            // 波形落盘回执（2026-10-05）：**这才是唯一能说明"存下来了"的信号**。
            FrameKinds.ECG_WAVE_ACK -> runCatching {
                // ⚠ 先作废超时判定（序号 +1）—— 否则回执刚到、
                //   那边延时任务醒来把成功覆盖成"没有回执"。
                exportSeq++
                val data = frame.data
                _ecgExport.value = when {
                    // ⚠⚠ **旧格式兼容**（手机端 v108 之前只发裸路径）：
                    //   那时"空串 = 失败、非空 = 成功"。升级途中两端版本可能不一致，
                    //   不认这个分支的话，旧手机端回的成功会被读成"已导出过"。
                    '|' !in data -> if (data.isEmpty()) {
                        ResText.of(R.string.s_9ef3938a)
                    } else {
                        ResText.of(R.string.s_456f29d5, data)
                    }
                    else -> {
                        // 新格式 `<状态>|<路径>`，状态 = `new` / `dup` / `fail`
                        val tag = data.substringBefore('|')
                        val path = data.substringAfter('|')
                        when (tag) {
                            // ⚗⚗ "之前已经导出过"（2026-10-05 用户要求去重后新增）：
                            //   **没有写新文件**，但文件确实在 ⇒ 必须报成"已有"而不是失败，
                            //   并且要给出路径（否则他会以为又失败了）。
                            "dup" -> ResText.of(R.string.s_38682199, path)
                            "fail" -> ResText.of(R.string.s_9ef3938a)
                            // ⚠ 路径**由手机端给**，手表不自己拼：
                            //   拼法只有手机端那一份（`EcgExport.relativeDir`），
                            //   两边各拼一遍就会出现"提示写 A、文件在 B"。
                            else -> ResText.of(R.string.s_456f29d5, path)
                        }
                    }
                }
            }
        }
    }

    /**
     * 写一帧。
     *
     * ⚠⚠ 必须 `synchronized`（2026-10-28 加）：更新任务在**另一个线程**上跑，
     *   完成后要发 `WATCH_UPDATE_ACK` 回执，而同期同步流程可能正在发 HELLO/DATA。
     *   帧是**换行分隔**的 JSON，两个线程各自 write 半行就会拼出一行非法 JSON，
     *   对端 `FrameCodec.decode` 返回 null 后**整帧丢弃**（不报错，只表现为"丢了一条"）。
     */
    private val writeLock = Any()

    private fun sendFrame(frame: Frame): Boolean = synchronized(writeLock) {
        val o = out ?: return false
        return try {
            o.write(FrameCodec.encode(frame).toByteArray(Charsets.UTF_8))
            o.flush()
            true
        } catch (_: Exception) {
            open = false
            false
        }
    }

    private suspend fun sendHelloFrame() {
        val hello = helloMessage()
        sendFrame(Frame(FrameKinds.HELLO, json.encodeToString(HelloMessage.serializer(), hello)))
    }

    /**
     * 自动同步（静默）：仅在已有连接时推数据。
     * 未连接时不做任何事——自动重连成功后会通过 hello->sync_req 全量补同步。
     */
    fun syncAuto() {
        scope.launch {
            if (open) {
                val result = runCatching { syncAll() }.getOrElse { LanguageStore.string(context, R.string.s_3cedee7e, it.message) }
                _state.value = ResText.of(R.string.s_9d46794f, result)
            }
        }
    }

    /**
     * 上传**本次会话的双路原始波形**（调试用，手机端存为 CSV）。
     *
     * ## ⚠ 为什么必须带上那一路 PPG（2026-09-24 立）
     *
     * PAT 的两次返工都卡在同一件事上：**不知道这一路的真实形态**，
     * 只能从"算出来的结论"反推，两次都推错（先是以为 500 Hz，后是以为有符号）。
     * 唯一的解法是把**同一时刻的两路原始值**摆到一起看 —— 而它们本来就同一个包、
     * 天然对齐，所以这里并排导出不需要任何额外对齐工作。
     *
     * 载荷格式：`ecg:<csv>|ppg:<csv>|fs:<采样率Hz>`。
     * 手机端把它写成 `mv,ppg` 两列（缺样处留空）+ 一行 `#` 元信息。
     * ⚠ 保留对**旧格式**（纯 CSV，无前缀）的兼容：升级途中两端版本可能不一致。
     *
     * @param ecg 心电毫伏（已 AC 耦合）
     * @param ppg 同会话的 PPG 原始计数（**与 ecg 等长**；没有值的位置是 0/负）
     * @param sampleRateHz 实测采样率（Hz），写进文件头供离线复算
     * @param rPeaks 生产代码检出的 R 峰下标，一并写进文件头 ——
     *   ⚠ 离线分析**不要自己再写一个 QRS 检测器**（本项目那条"两边各写一套必然漂移"）：
     *   把生产检出结果带出去，离线复算才和线上是同一套输入。
     */
    fun sendEcgWave(
        ecg: List<Float>,
        ppg: List<Int> = emptyList(),
        sampleRateHz: Int = 0,
        rPeaks: List<Int> = emptyList(),
    ) {
        if (ecg.isEmpty()) return
        scope.launch {
            if (!open) {
                _state.value = ResText.of(R.string.s_445b0ac8)
                // ⚠ 也必须弹窗：这正是"点了没反应"的一种（用户在导出按钮上点，
                //   而结果只写进了首页那条状态条 —— 他看不到）。
                _ecgExport.value = ResText.of(R.string.s_445b0ac8)
                return@launch
            }
            val ecgCsv = ecg.joinToString(",") { String.format(java.util.Locale.US, "%.2f", it) }
            val ppgCsv = ppg.joinToString(",") { it.toString() }
            val rpCsv = rPeaks.joinToString(",")
            val payload = "ecg:$ecgCsv|ppg:$ppgCsv|fs:$sampleRateHz|rp:$rpCsv"
            val ok = sendFrame(Frame(FrameKinds.ECG_WAVE, payload))
            _state.value = if (ok) {
                ResText.of(R.string.s_fec9b030, ecg.size, ppg.count { it > 0 }) +
                    ResText.of(R.string.s_5e223654, rPeaks.size)
            } else {
                ResText.of(R.string.s_21795d62)
            }
            // ── 导出结果弹窗（2026-10-05）──
            if (ok) {
                // ⚠ 这里只能到"正在导出…"为止：落盘在手机端，还没回执。
                //   等 [FrameKinds.ECG_WAVE_ACK] 回来才定成功/失败。
                _ecgExport.value = ResText.of(R.string.s_cafd6c7b)
                val my = ++exportSeq
                scope.launch {
                    kotlinx.coroutines.delay(EXPORT_ACK_TIMEOUT_MS)
                    // ⚠ 序号没变 ⇒ 这一次的回执始终没来。
                    //   绝**不**把它显示成成功，也不显示成失败 —— 我们确实不知道。
                    if (exportSeq == my) {
                        _ecgExport.value = ResText.of(R.string.s_df152dd6)
                    }
                }
            } else {
                // 帧都没发出去（蓝牙断了）⇒ 失败是**确定的**，直接说。
                _ecgExport.value = ResText.of(R.string.s_21795d62)
            }
        }
    }

    private suspend fun syncAll(): String = syncMutex.withLock {
        var sent = 0
        for (type in RecordType.ALL) {
            while (true) {
                val batch = loadBatch(type, batchSizeFor(type)) ?: break
                if (batch.isEmpty()) break
                val upTo = batch.maxOf { it.recordedAt }
                val deferred = CompletableDeferred<AckMessage>()
                pending["$type:$upTo"] = deferred
                val msg = SyncMessage(type, Build.MODEL, batch, upTo)
                val ok = sendFrame(
                    Frame(
                        FrameKinds.DATA,
                        json.encodeToString(SyncMessage.serializer(), msg),
                    )
                )
                if (!ok) {
                    pending.remove("$type:$upTo")
                    return@withLock LanguageStore.string(context, R.string.s_fccad655, sent)
                }
                val ack = withTimeoutOrNull(15_000) { deferred.await() }
                if (ack == null) {
                    pending.remove("$type:$upTo")
                    return@withLock LanguageStore.string(context, R.string.s_b219625c, sent)
                }
                markSyncedUpTo(type, upTo)
                sent += ack.inserted
            }
        }
        sendHelloFrame()
        LanguageStore.string(context, R.string.s_a5774233, sent)
    }

    private suspend fun helloMessage() = HelloMessage(
        deviceId = Build.MODEL,
        deviceName = Build.MODEL,
        watchVersion = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(0L),
        pendingHr = db.heartRateDao().unsyncedCount(),
        pendingBp = db.bloodPressureDao().unsyncedCount(),
        pendingEcg = db.ecgDao().unsyncedCount(),
        pendingSpo2 = db.spo2Dao().unsyncedCount(),
        pendingTemp = db.temperatureDao().unsyncedCount(),
        pendingBia = db.bodyCompositionDao().unsyncedCount(),
        pendingActivity = db.activityDao().unsyncedCount(),
        pendingPpg = db.ppgWaveDao().unsyncedCount(),
        pendingHrv = db.hrvDao().unsyncedCount(),
        pendingMf = db.mfBiaDao().unsyncedCount(),
    )

    private suspend fun loadBatch(type: String, limit: Int): List<HealthItemDto>? {
        return when (type) {
            RecordType.HR -> db.heartRateDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, bpm = it.bpm)
            }
            RecordType.BP -> db.bloodPressureDao().unsynced(limit).map {
                HealthItemDto(
                    recordedAt = it.recordedAt, systolic = it.systolic,
                    diastolic = it.diastolic, pulse = it.pulse, note = it.note,
                )
            }
            // 心电：结论来自 ecg_records（唯一有 synced 标记的心电表），
            // 波形按同一时间戳从 ecg_waves 取出一起带上，手机端才能画出波形。
            RecordType.ECG -> db.ecgDao().unsynced(limit).map { r ->
                val wave = runCatching {
                    db.ecgWaveDao().byTime(r.recordedAt, r.source)
                }.getOrNull()
                HealthItemDto(
                    recordedAt = r.recordedAt,
                    classification = r.classification,
                    bpm = r.heartRate?.toDouble(),
                    note = r.note,
                    durationMs = wave?.durationMs,
                    sampleRate = wave?.sampleRate,
                    regular = wave?.regular?.let { it != 0 },
                    beatCount = wave?.beatCount,
                    bandRatio = wave?.bandRatio,
                    wave = wave?.waveJson,
                    // #14：PAT 来自**同步表**本身（不在 waveform 里）—— 它是结论的一部分
                    patMs = r.patMs,
                    patSpreadMs = r.patSpreadMs,
                    patSeriesMs = r.patSeriesMs,
                    patNote = r.patNote,
                    patAlgo = r.patAlgo,
                )
            }
            RecordType.SPO2 -> db.spo2Dao().unsynced(limit).map {
                HealthItemDto(
                    recordedAt = it.measuredAt,
                    bpm = it.heartRate?.toDouble(),
                    spo2 = it.percent,
                )
            }
            // 体温：摄氏度用 Float 原样带过去。取整会把这个指标的全部有效信息抹平
            // （腕部皮肤温度的日常波动就在 0.1~1 ℃ 量级）。
            RecordType.TEMP -> db.temperatureDao().unsynced(limit).map {
                HealthItemDto(
                    recordedAt = it.measuredAt, tempC = it.celsius, ambientC = it.ambientC,
                )
            }
            // 体成分：一条记录只有十几个 Float，体积和血压一个量级，用默认批大小即可。
            //
            // ⚠ 这里曾经漏了这一支（`markSyncedUpTo` 却有），后果是**手表测的体成分永远
            //    传不到手机**：`loadBatch` 返回 null → 外层 `?: break` 直接跳过该类型，
            //    既不报错也不显示失败，只是手机上永远看不到这条记录。而 Data Layer 那条备用
            //    通道又不常走，所以现象是"体成分页只有三星健康的数据，手表测的一条都没有"。
            RecordType.BIA -> db.bodyCompositionDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, bia = it.toSyncDto())
            }
            // 活动片段：统计量 + 一条降采样包络（约 0.6KB/条），比心电波形小两个数量级。
            // 一分钟一条，量级与血压同档，用默认批大小即可。
            RecordType.ACTIVITY -> db.activityDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.startedAt, activity = it.toSyncDto())
            }
            // PPG 原始光信号：一条要带三段波形（绿/红/红外，各最多 3000 点 × 8 字符 ≈ 24KB），
            // 比心电波形还大，所以和 ECG 一样固定单条一批（见 batchSizeFor）。
            //
            // ⚠ 新增记录类型时**必须**同时改这里与 markSyncedUpTo：
            //    漏这里 → 外层 `?: break` 静默跳过该类型，手表采的数据永远传不到手机；
            //    漏那里 → `while(true)` 里标记不掉，无限重发同一批。两处都是静默故障。
            RecordType.PPG -> db.ppgWaveDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, ppg = it.toSyncDto())
            }
            // HRV 记录：一条只有约 35 个整数（逐拍间期），比 PPG 小三个数量级 ——
            // **刻意不放进 batchSizeFor 的单条一批**，用默认批大小即可。
            // 带上的是**输入而不是指标**（指标两端现算），见 HrvSessionEntity。
            RecordType.HRV -> db.hrvDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, hrv = it.toSyncDto())
            }
            // 多频阻抗：一条只有 8 个 Float，比血压还小，用默认批大小即可。
            // ⚠ 与 PPG 那处同一个坑：漏了这一支时外层 `?: break` 会**静默跳过整个类型** ——
            //    手表上测出来的阻抗谱永远到不了手机，而手机上的分析全靠这条数据。
            RecordType.MF_BIA -> db.mfBiaDao().unsynced(limit).map {
                HealthItemDto(recordedAt = it.measuredAt, mfBia = it.toSyncDto())
            }
            else -> null
        }
    }

    private suspend fun markSyncedUpTo(type: String, upTo: Long) {
        when (type) {
            RecordType.HR -> db.heartRateDao().markSyncedUpTo(upTo)
            RecordType.BP -> db.bloodPressureDao().markSyncedUpTo(upTo)
            RecordType.ECG -> db.ecgDao().markSyncedUpTo(upTo)
            RecordType.SPO2 -> db.spo2Dao().markSyncedUpTo(upTo)
            RecordType.TEMP -> db.temperatureDao().markSyncedUpTo(upTo)
            RecordType.BIA -> db.bodyCompositionDao().markSyncedUpTo(upTo)
            RecordType.ACTIVITY -> db.activityDao().markSyncedUpTo(upTo)
            RecordType.PPG -> db.ppgWaveDao().markSyncedUpTo(upTo)
            RecordType.HRV -> db.hrvDao().markSyncedUpTo(upTo)
            RecordType.MF_BIA -> db.mfBiaDao().markSyncedUpTo(upTo)
        }
    }

    companion object {
        private const val BATCH_SIZE = 40

        /**
         * ECG 的一条记录要携带约 16KB 的波形文本（30 秒 × 100Hz）。
         * 若沿用 40 条一批，一帧会到 640KB，RFCOMM 的单帧传输非常容易中断；
         * 心电测量一天也就几次，固定单条一批最稳。
         *
         * PPG 同理（甚至更大：三段波形 × 最多 3000 点 × 8 字符 ≈ 24KB/段）。
         */
        private fun batchSizeFor(type: String): Int =
            if (type == RecordType.ECG || type == RecordType.PPG) 1 else BATCH_SIZE
    }
}
