package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Ppg
import com.gemini.healthbridge.core.ResText
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.PpgType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * PPG 原始光信号采集（模块 #8，`HealthTrackerType.PPG_CONTINUOUS`）。
 *
 * ## 它在整个采集体系里的位置：唯一"搭车"的三星 SDK 采集
 * 其它几个模块与 `SamsungSensorClient` 的关系是"独占"：
 * `stopAllMeasurements` 会先把所有测量停掉再开新的，因为服务是**单 owner** 设计。
 * 本模块**刻意不参与这套协商**，理由与 `AccelSampler` 一样，但落点不同：
 *
 *  - **不 connect、不 disconnect**。连接与断开的责任完全属于血氧测量
 *    （`Spo2SensorManager.startMeasurement/stopMeasurement`）。本类只在
 *    [SamsungSensorClient.isConnected] 为真时去取自己的 tracker。
 *    如果它也去 connect，就会把 `owner` 抢过来，血氧的 `onConnected()` 永远等不到 ——
 *    这个坑在 `SamsungSensorClient` 的类注释里已经记过一次（"重新测量传感器不工作"）。
 *  - **只在血氧测量期间采集**。血氧的 `SPO2_ON_DEMAND` 本身就要点亮 PPG 传感器，
 *    这段时间的原始计数是现成的；反过来说，在没人测量的时候常开 PPG 会一直亮灯耗电，
 *    而总表 #8 要的是"波形输入"，不是"7×24 监护"。
 *
 * 于是采集的起止与血氧会话严格同步：`begin()` 在血氧开始测量时调用，
 * `finish()` 在血氧结束（拿到结果 / 超时 / 用户停止 / 离开页面）时调用。
 *
 * ## 失败面必须被关在本地
 * 本模块是唯一"看不见的采集可能影响看得见的功能"的地方，所以每一层失败都只落到
 * [State.error] 上给人看，绝不向上抛：
 *  - 取 tracker 失败 / 注册监听失败 / 能力不支持 → 只记原因，血氧照常跑；
 *  - tracker 报错（`SDK_POLICY_ERROR` / `PERMISSION_ERROR`）→ 立即摘掉监听并**本会话不再重试**，
 *    避免反复触发同一个错误去干扰正在进行的血氧测量；
 *  - 数据解析异常（SDK 回调线程上抛异常会直接杀进程）→ `runCatching` 兜底。
 *
 * ## 落库的波形是归一化的，摘要与波形同源
 * 每个通道的原始计数交给 `Ppg.normalize`（减 DC 中位数、剔除无效样本），
 * 存下来的是 `(x - dc) / dc`。理由见 `Ppg` 的类注释：原始计数没有物理单位、
 * 受增益/LED 电流/环境光影响，跨会话不可比。
 *
 * ## 采样率与脉率都"估不出来就不给"
 * 三星的 `DataPoint.getTimestamp()` **文档没写单位**，而单位差 1000 倍会让脉率差 1000 倍。
 * 所以时间戳单位由 `Ppg.timestampUnitDivisor` 与墙钟实测时长比对**实测判定**；
 * 判不出来就退回墙钟估算（[Ppg.RateEstimate.fromTimestamps] = false）；
 * 连墙钟都推不出合理采样率（< 8Hz 或 > 2kHz）就 `sampleRateHz = null`，**此时不给脉率估算**。
 */
class PpgRecorder(
    context: Context,
    private val client: SamsungSensorClient,
    private val scope: CoroutineScope,
) {

    /**
     * @param enabled 用户开关（见 `PpgSettings`）。
     * @param supported 本机是否具备连续 PPG 能力。false = 本会话不必再尝试（能力缺失或 SDK 策略拒绝）。
     * @param recording 当前是否真的在收数（会话进行中且 tracker 在手）。
     * @param sampleCount 本会话已收的绿光样本数（含将被剔除的无效样本）。
     * @param elapsedMs 本会话已收的时长（毫秒，首末有效样本之间）。
     * @param channels 本会话已见到的通道。
     * @param full 已达 [Ppg.MAX_STORED_SAMPLES] / [Ppg.MAX_SESSION_MS]，不再继续收
     *   （会话不结束，已收内容保留）。
     * @param sessions 已产出的会话数，用来确认采集真的在跑。
     * @param lastStats 最近一次已结算会话的摘要（供界面显示，不必回查数据库）。
     * @param lastResp 最近一次会话的呼吸率估算（模块 #11）。在**结算时**算、随状态带出来，
     *   而不是让界面自己去库里捞波形重算 —— 那样界面算的与落库的波形未必是同一份输入
     *   （截断、编码有损都可能让两边不同），而"界面值与落库值同源"是这个项目的一条纪律。
     * @param error 最近一次失败原因（只影响本模块，不影响血氧）。
     */
    data class State(
        val enabled: Boolean = true,
        val supported: Boolean = true,
        val recording: Boolean = false,
        val sampleCount: Int = 0,
        val elapsedMs: Long = 0,
        val channels: List<String> = emptyList(),
        val full: Boolean = false,
        val sessions: Int = 0,
        val lastStats: Ppg.Stats? = null,
        val lastResp: com.gemini.healthbridge.core.Resp.Result? = null,
        val error: ResText? = null,
    )

    /**
     * 一次采集的成品：已归一化的波形 + 摘要。由调用方落库并触发同步。
     *
     * @param measuredAt 首个**有效绿光样本**的墙钟时刻（epoch 毫秒）。刻意不用"按下测量的时刻"：
     *   只有前者才与波形的时间轴对齐（`PpgWaveEntity.measuredAt` 的注释同一理由）。
     * @param greenWave 绿光归一化波形（逗号分隔，6 位小数）。
     * @param redWave 红光归一化波形；本会话没有足够的红光有效样本时为 null。
     * @param irWave 红外归一化波形；同上。
     * @param statusSeen 本会话出现过的通道状态码（原样记录、不解释语义），供排查。
     */
    data class Session(
        val measuredAt: Long,
        val durationMs: Long,
        val sampleCount: Int,
        val sampleRateHz: Int?,
        val rateFromTimestamps: Boolean,
        val channels: String,
        val stats: Ppg.Stats,
        /** 呼吸率估算（模块 #11）；null = 计算本身异常（正常"算不出"是 NotApplicable，不是 null）。 */
        val resp: com.gemini.healthbridge.core.Resp.Result?,
        val greenWave: String,
        val redWave: String?,
        val irWave: String?,
        val statusSeen: String?,
    )

    /** 每结算一段就回调一次（与 `AccelSampler.onSegment` 同一个模式）。 */
    var onSession: ((Session) -> Unit)? = null

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(State(enabled = PpgSettings.isEnabled(appContext)))
    val state: StateFlow<State> = _state

    private var tracker: HealthTracker? = null
    private var attachJob: Job? = null

    /**
     * 会话是否"活着"（缓冲里有一段可结算的数据）。
     *
     * 与 [State.recording] 刻意分开：tracker 报错后 `recording` 要立刻变 false（界面要如实说
     * "没在收"），但**已经收到的那些样本仍然值得结算落库** —— 那是真实的测量数据，
     * 只是这次没采完。用一个标志位把"是否在收"与"是否有东西可结算"解耦。
     */
    private var sessionActive = false

    // ─── 采集缓冲 ───
    private val green = ArrayList<Int>(Ppg.MAX_STORED_SAMPLES)
    private val red = ArrayList<Int>(Ppg.MAX_STORED_SAMPLES)
    private val ir = ArrayList<Int>(Ppg.MAX_STORED_SAMPLES)
    private var firstTs = 0L
    private var lastTs = 0L
    private var firstWallAt = 0L
    private var lastWallAt = 0L
    private var received = 0
    private val statuses = LinkedHashSet<Int>()
    private var full = false

    private val listener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(points: List<DataPoint>) {
            // 回调在 SDK 绑定线程上执行：任何未捕获异常都会直接杀进程（闪退），必须兜底
            runCatching { ingest(points) }.onFailure { e ->
                detach(ResText.of(R.string.s_3a313110, e.javaClass.simpleName, e.message))
            }
        }

        override fun onFlushCompleted() {}

        override fun onError(error: HealthTracker.TrackerError) {
            // 只摘掉自己的监听并记下原因，**不碰**血氧那条链路
            // `TrackerError` 只有这两个值（枚举已穷尽），不要写 else —— 编译器会提醒它是冗余的。
            // 但**不要**因此以为"只有这两种失败"：`getHealthTracker`/`setEventListener` 抛的异常
            // 走的是 acquire() 里的 runCatching，不经过这里。
            val msg = when (error) {
                HealthTracker.TrackerError.SDK_POLICY_ERROR ->
                    ResText.of(R.string.s_f7b279ee)
                HealthTracker.TrackerError.PERMISSION_ERROR -> ResText.of(R.string.s_9df4ec16)
            }
            // 策略类错误是本机/本包就过不去，本会话不必再试（避免反复触发拖累血氧测量）
            if (error == HealthTracker.TrackerError.SDK_POLICY_ERROR) {
                _state.value = _state.value.copy(supported = false)
            }
            detach(msg)
        }
    }

    // ═══════════════ 开关 ═══════════════

    /** 用户拨动开关。关闭时把**已经采到的一段**结算掉（那是真实数据，不该丢）。 */
    fun setEnabled(enabled: Boolean) {
        PpgSettings.setEnabled(appContext, enabled)
        _state.value = _state.value.copy(enabled = enabled)
        if (enabled) {
            // 重新允许尝试：上次的"不支持"可能是能力读取的瞬时失败
            _state.value = _state.value.copy(supported = true, error = null)
        } else {
            finish(null)
        }
    }

    // ═══════════════ 会话起止 ═══════════════

    /**
     * 血氧测量开始时调用。**不连接服务** —— 只等服务被血氧那边连上，再取自己的 tracker。
     *
     * 为什么要等而不是立刻取：`getHealthTracker` 在服务未连接时会失败。
     * 而连接的完成是异步的（`ConnectionListener.onConnectionSuccess`），
     * 血氧的 `state.measuring` 却在 `startMeasurement()` 里**同步**就置 true 了。
     * 所以这里必须自己等 [SamsungSensorClient.isConnected]。
     */
    fun begin() {
        if (!_state.value.enabled) return
        if (sessionActive || attachJob?.isActive == true) return
        if (!_state.value.supported) return

        attachJob = scope.launch {
            var waited = 0L
            while (!client.isConnected && waited < ATTACH_WAIT_MS) {
                delay(ATTACH_POLL_MS)
                waited += ATTACH_POLL_MS
            }
            if (!client.isConnected) {
                _state.value = _state.value.copy(
                    recording = false,
                    error = ResText.core("s_05eb16ac"),
                )
                return@launch
            }
            acquire()
        }
    }

    /**
     * 血氧结束时调用（拿到结果 / 超时 / 用户停止 / 离开页面 / 退出 App）。
     * 幂等：没有活着的会话时直接返回，不会产出空记录。
     *
     * @param refHr 同一次测量由血氧/心率通道给出的参考心率，存进记录里供对照；null = 没拿到。
     */
    fun finish(refHr: Int? = null) {
        attachJob?.cancel()
        attachJob = null

        val t = tracker
        tracker = null
        runCatching { t?.unsetEventListener() }
        _state.value = _state.value.copy(recording = false, sampleCount = 0, elapsedMs = 0, full = false)

        if (!sessionActive) {
            reset()
            return
        }
        sessionActive = false

        // 先取出局部副本，reset() 会把缓冲清空
        val g = ArrayList(green)
        val r = ArrayList(red)
        val i = ArrayList(ir)
        val measuredAt = firstWallAt
        val first = firstTs
        val last = lastTs
        val duration = (lastWallAt - firstWallAt).coerceAtLeast(0L)
        val seen = statuses.sorted().joinToString(",").ifBlank { null }
        reset()

        if (g.isEmpty() || measuredAt <= 0L) return
        if (Ppg.isTooShort(g.size, duration)) {
            _state.value = _state.value.copy(
                error = ResText.core("s_2ac2539f", g.size, duration),
            )
            return
        }

        val rate = Ppg.estimateSampleRate(first, last, g.size, duration)
        val stats = Ppg.analyze(
            rawGreen = g,
            durationMs = duration,
            sampleRateHz = rate?.hz,
            rateFromTimestamps = rate?.fromTimestamps == true,
            refHr = refHr,
        ) ?: return

        // 第二遍归一化：与 `AccelSampler` 同一个理由 —— 波形与摘要把同一个公开 API 走两次，
        // 不会出现"摘要把某些样本算进去了、波形没算进去"的口径漂移。
        // 另外 `Ppg.normalize` 会剔除无效样本，所以波形与 `stats.sampleCount` 天然同源。
        val ac = Ppg.normalize(g) ?: return
        val cap = Ppg.clampStored(ac.norm.size)
        val greenWave = Ppg.encodeWave(ac.norm.subList(0, cap))

        val redWave = Ppg.normalize(r)?.norm?.let { n ->
            n.subList(0, Ppg.clampStored(n.size)).let { Ppg.encodeWave(it) }
        }
        val irWave = Ppg.normalize(i)?.norm?.let { n ->
            n.subList(0, Ppg.clampStored(n.size)).let { Ppg.encodeWave(it) }
        }

        val channels = Ppg.encodeChannels(
            buildList {
                add(Ppg.Channel.GREEN)
                if (redWave != null) add(Ppg.Channel.RED)
                if (irWave != null) add(Ppg.Channel.IR)
            }
        )

        // 呼吸率（模块 #11）：**用落库的那条归一化波形**（ac.norm）算，而不是另跑一遍归一化 ——
        // 波形要截断（clampStored）时，这里也按同样的截断长度算，保证"界面显示的呼吸率"
        // 与"库里那段波形"是同一份输入。算不出来（记录太短/检不出脉搏）是常态，不是异常：
        // `Resp` 会返回带原因的 NotApplicable，界面原样显示原因即可。
        val resp = runCatching {
            com.gemini.healthbridge.core.Resp.fromPpg(
                wave = ac.norm.subList(0, cap),
                sampleRateHz = stats.sampleRateHz,
                quality = stats.quality,
                durationMs = duration,
            )
        }.getOrNull()

        val session = Session(
            measuredAt = measuredAt,
            durationMs = duration,
            sampleCount = stats.sampleCount,
            sampleRateHz = stats.sampleRateHz,
            rateFromTimestamps = stats.rateFromTimestamps,
            channels = channels,
            stats = stats,
            resp = resp,
            greenWave = greenWave,
            redWave = redWave,
            irWave = irWave,
            statusSeen = seen,
        )

        _state.value = _state.value.copy(
            sessions = _state.value.sessions + 1,
            lastStats = stats,
            lastResp = resp,
            channels = Ppg.decodeChannels(channels),
            error = null,
        )
        runCatching { onSession?.invoke(session) }.onFailure { e ->
            _state.value = _state.value.copy(error = ResText.core("s_be9365e8", e.message))
        }
    }

    // ═══════════════ 取 tracker ═══════════════

    private fun acquire() {
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())

        if (!types.contains(HealthTrackerType.PPG_CONTINUOUS)) {
            _state.value = _state.value.copy(
                supported = false,
                recording = false,
                error = ResText.core("s_847dcbdc", types.joinToString { it.name }),
            )
            return
        }

        // 三个通道都要：绿光用于灌注/脉率（最常用的 PPG 通道），
        // 红光/红外先存着 —— 将来若有标定数据可用于血氧研究（见 `Ppg` 类注释）。
        val t = runCatching {
            client.service.getHealthTracker(
                HealthTrackerType.PPG_CONTINUOUS,
                setOf(PpgType.GREEN, PpgType.RED, PpgType.IR),
            )
        }.getOrElse { e ->
            _state.value = _state.value.copy(
                recording = false, error = ResText.core("s_e1953c06", e.message),
            )
            return
        }

        runCatching { t.setEventListener(listener) }.onFailure { e ->
            _state.value = _state.value.copy(
                recording = false, error = ResText.core("s_d74093ee", e.message),
            )
            return
        }

        tracker = t
        reset()
        sessionActive = true
        _state.value = _state.value.copy(
            recording = true, sampleCount = 0, elapsedMs = 0,
            channels = emptyList(), full = false, error = null,
        )
    }

    /** 摘掉监听并记录原因（**不结束会话** —— 已收到的样本仍然会在 [finish] 里结算）。 */
    private fun detach(message: ResText) {
        val t = tracker
        tracker = null
        runCatching { t?.unsetEventListener() }
        _state.value = _state.value.copy(recording = false, error = message)
    }

    private fun reset() {
        green.clear()
        red.clear()
        ir.clear()
        firstTs = 0L
        lastTs = 0L
        firstWallAt = 0L
        lastWallAt = 0L
        received = 0
        statuses.clear()
        full = false
    }

    // ═══════════════ 收数 ═══════════════

    private fun ingest(points: List<DataPoint>) {
        if (tracker == null || !sessionActive) return
        val now = System.currentTimeMillis()
        var added = false

        for (dp in points) {
            val g = dp.getValue(ValueKey.PpgSet.PPG_GREEN)
            val r = dp.getValue(ValueKey.PpgSet.PPG_RED)
            val i = dp.getValue(ValueKey.PpgSet.PPG_IR)

            // 状态码原样收着，**不解释语义**（三星没有公开这几个码的含义，而猜错的后果是
            // "看起来很正常"的错误结论 —— 见 `Ppg` 类注释）
            dp.getValue(ValueKey.PpgSet.GREEN_STATUS)?.let { statuses.add(it) }
            dp.getValue(ValueKey.PpgSet.RED_STATUS)?.let { statuses.add(it) }
            dp.getValue(ValueKey.PpgSet.IR_STATUS)?.let { statuses.add(it) }

            val ts = runCatching { dp.getTimestamp() }.getOrDefault(0L)
            received++

            if (g != null) {
                // 无效样本（<= 0）**照样进缓冲**：`Ppg.normalize` 要拿它当完整度的分母，
                // 在这里先滤掉会把"丢了多少样"这个信息一起丢掉。
                green.add(g)
                if (firstWallAt == 0L) {
                    firstWallAt = now
                    firstTs = ts
                }
                lastWallAt = now
                if (ts > 0L) lastTs = ts
                added = true
            }
            if (r != null) red.add(r)
            if (i != null) ir.add(i)

            if (green.size >= Ppg.MAX_STORED_SAMPLES) {
                full = true
                break
            }
        }

        if (!added) return

        val elapsed = (lastWallAt - firstWallAt).coerceAtLeast(0L)
        // 到达上限后停止收数（**不结束会话**）：已收的部分要留给 finish() 结算。
        // 否则一条开着的会话会把手表占住，而 120 秒的波形对下游算法已经足够。
        if (!full && Ppg.isSessionFull(elapsed)) full = true
        if (full) {
            val t = tracker
            tracker = null
            runCatching { t?.unsetEventListener() }
        }

        _state.value = _state.value.copy(
            recording = !full,
            sampleCount = green.size,
            elapsedMs = elapsed,
            channels = Ppg.decodeChannels(
                Ppg.encodeChannels(
                    buildList {
                        if (green.isNotEmpty()) add(Ppg.Channel.GREEN)
                        if (red.isNotEmpty()) add(Ppg.Channel.RED)
                        if (ir.isNotEmpty()) add(Ppg.Channel.IR)
                    }
                )
            ),
            full = full,
        )
    }

    private companion object {
        /** 等血氧那边把共享服务连上的最长时间。超过就放弃本次采集（不影响血氧）。 */
        const val ATTACH_WAIT_MS = 3_000L

        /** 等待连接的轮询间隔。 */
        const val ATTACH_POLL_MS = 100L
    }
}
