package com.gemini.healthbridge.wear

import android.os.Handler
import android.os.Looper
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import com.gemini.healthbridge.core.ResText

/**
 * 心电单次测量（HealthTrackerType.ECG_ON_DEMAND）：
 * 手指按住表冠电极，保持手腕静止；逐点输出 AC 耦合后的心电波形（mV）。
 *
 * ## 与旧实现的关键差别
 * 1. **输出 AC 耦合后的真实毫伏值**。SDK 的 `ECG_MV` 带约 200mV 电极直流偏置，
 *    旧代码把它原样喂给 UI，而 UI 按窗口 min/max 自动缩放 —— 结果屏幕上永远是噪声。
 * 2. **不再因 `mv == 0f` 丢点**。按值丢弃会破坏采样等间隔性，让频带与不应期失准；
 *    现在只在 `LEAD_OFF == 5`（SDK 明确的离体语义）时丢弃。
 * 3. **采样率改为实测**。用 `ValueKey.EcgSet.SEQUENCE` 的序号跨度换算，
 *    并同时给出丢包数（旧代码用墙钟反推，SDK 一丢包就低估）。
 *
 * ## 会话生命周期（2026-09-14 修的重启 bug，务必不要改回去）
 * `stopMeasurement()` 刻意保持服务连接（反复断连重连会让 ECG 会话进入废状态）。
 * 但 [SamsungSensorClient.connect] 在"已连接"时不会再有 SDK 回调，
 * 所以必须由它**主动**通知 [connectionOwner.onConnected] 才会走到 [startTracking]。
 * 除此之外再加一道**停滞看门狗**：`measuring` 但连续 [STALL_TIMEOUT_MS] 没有新数据，
 * 说明 tracker 没建起来或会话已废，用整条链路重连自愈（见 [recoverFromStall]）。
 */
class EcgMeasurer(private val client: SamsungSensorClient) {

    data class State(
        val measuring: Boolean = false,
        /** 最近一次 LEAD_OFF 原始上报值（null=SDK 未上报过）。本机固件实测恒 -1（未知），
         *  只有 ==5 才是 SDK 明确的"电极未接触"。 */
        val leadRaw: Int? = null,
        /** true = leadRaw==5（SDK 语义的未接触）。 */
        val leadOff: Boolean = false,
        /** 本次测量累计收到的数据点数。 */
        val received: Int = 0,
        val statusText: ResText = ResText.EMPTY,
        val error: ResText? = null,
        /** 由 SEQUENCE 序号跨度实测出的采样率（Hz），0 = 尚未测出。 */
        val sampleRateHz: Int = 0,
        /** 由 SEQUENCE 跳号算出的丢包采样点数。 */
        val dropped: Int = 0,
        /** 是否已上报过 SEQUENCE（false 时采样率退化为"点数/墙钟时长"估算）。 */
        val seqAvailable: Boolean = false,
        /** 因长时间收不到数据而触发过自愈重连的次数（诊断用）。 */
        val recoveries: Int = 0,
        /**
         * 收到过 PPG 值的包数（模块 #14 的通道可用性证据）。
         *
         * 它为 0 且 [received] 很大 ⇒ **本机不开放 `EcgSet.PPG_GREEN`**，
         * 界面要据此说"这台设备不做脉搏到达时间"，而不是笼统显示"无法测量"。
         */
        val ppgSeen: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** 每个有效心电采样回调（**已 AC 耦合、单位 mV**，UI 直接绘制与统计）。 */
    /**
     * 单个采样点回调：`(心电毫伏, 同一包里那路 PPG 的原始计数)`。
     *
     * ## ⚠ 为什么这两路必须**在同一个回调**里给出，而不是各给一个回调
     *
     * `EcgSet.PPG_GREEN` 与 `ECG_MV` 在**同一个包里**，所以两路天然同采样率、同时间轴。
     * 而 PAT（模块 #14）正是靠「心电 R 峰下标 − 脉搏波足下标」算出来的，
     * **下标必须一一对应**。若拆成两个回调，调用方（或以后的人）只要在某一路里
     * 多跳过、多追加一个点，两边的下标就永久错开 —— 而且**不报错**，
     * 只会让每个 PAT 都偏小一点点、偏移量还随机。
     * 合成一个回调之后，这种错位在结构上就不可能发生。
     *
     * `ppgRaw` 为 null 表示这个包里没有 PPG 值（本机不支持该通道，或 SDK 这次没给）。
     * ⚠ 调用方仍要**占位**，不能跳过 —— 跳过同样会让两路错位。
     */
    var onSample: ((Float, Int?) -> Unit)? = null

    private var tracker: HealthTracker? = null

    private val handler = Handler(Looper.getMainLooper())

    /** 流式去电极直流偏置，窗口随实测采样率自适应为 1 秒。 */
    private val coupler = EcgSignal.AcCoupler(250)

    /** 采样时序统计（SDK 线程写、UI 线程读，用 @Volatile + 局部快照保证可见性）。 */
    @Volatile private var firstSampleAt = 0L
    @Volatile private var firstSeq = -1L
    @Volatile private var lastSeq = -1L
    @Volatile private var sampleCount = 0L
    @Volatile private var sampleRateHz = 0
    @Volatile private var seqSeen = false
    @Volatile private var lastDataAt = 0L
    @Volatile private var recoveries = 0
    @Volatile private var ppgSeen = 0

    private val connectionOwner = object : SamsungSensorClient.Owner {
        override fun onConnected() {
            startTracking()
        }

        override fun onConnectionEnded() {
            _state.value = _state.value.copy(measuring = false)
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_10cf7950", e.errorCode, e.message)
            )
        }
    }

    /** 开始心电测量（自动连接共享服务）。 */
    fun startMeasurement() {
        resetTiming()
        lastDataAt = System.currentTimeMillis()
        _state.value = State(
            measuring = true,
            statusText = ResText.core("s_6ac70683"),
            recoveries = recoveries,
        )
        val ok = client.connect(connectionOwner)
        if (!ok) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_933c0c84"))
        }
    }

    /** 停止测量：先 flush 出缓冲区剩余数据，再摘监听器。
     *  服务保持连接（见类注释：反复断开重连会让 ECG 会话进入废状态）。 */
    fun stopMeasurement() {
        handler.removeCallbacksAndMessages(null)
        val t = tracker
        runCatching { t?.flush() }
        runCatching { t?.unsetEventListener() }
        tracker = null
        _state.value = _state.value.copy(measuring = false)
    }

    /**
     * 停滞看门狗：`measuring` 为 true 但已超过 [STALL_TIMEOUT_MS] 没有任何新数据。
     * 正常采集时数据是连续流，4 秒空窗必然异常（tracker 没建起来 / 会话已废）。
     */
    fun isStalled(nowMs: Long = System.currentTimeMillis()): Boolean =
        _state.value.measuring && lastDataAt > 0 && nowMs - lastDataAt > STALL_TIMEOUT_MS

    /**
     * 自愈：整条链路断开重连。这是 tracker 没建起来 / 会话进入废状态时唯一能恢复的手段。
     * 由 UI 在检出 [isStalled] 后调用（调用方负责限制重试次数，避免无限重连）。
     */
    suspend fun recoverFromStall() {
        recoveries++
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        client.disconnect()
        // 等 SDK 把 onConnectionEnded 走完，否则紧接着的 connect 会被"已连接"挡掉
        delay(400)
        startMeasurement()
    }

    private fun resetTiming() {
        firstSampleAt = 0L
        firstSeq = -1L
        lastSeq = -1L
        sampleCount = 0L
        sampleRateHz = 0
        seqSeen = false
        lastDataAt = System.currentTimeMillis()
        ppgSeen = 0
        coupler.reset()
    }

    /**
     * 取得 tracker 并注册监听。
     * @param attempt 重试次数：ECG 会话收尾是异步的，紧接着重建偶发失败，退避重试 2 次。
     */
    private fun startTracking(attempt: Int = 0) {
        // 能力检查 + 类型回退（heartlink 同款）：优先 ECG_ON_DEMAND，无则用连续型 ECG
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())
        val type = when {
            types.contains(HealthTrackerType.ECG_ON_DEMAND) -> HealthTrackerType.ECG_ON_DEMAND
            types.contains(HealthTrackerType.ECG) -> HealthTrackerType.ECG
            else -> {
                _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_7b0246e9", types.joinToString { it.name }),
                )
                return
            }
        }
        val t = try {
            client.service.getHealthTracker(type)
        } catch (e: Exception) {
            if (retry(attempt, ResText.core("s_9ac84de3", e.message))) return
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_9ac84de3", e.message)
            )
            return
        }
        tracker = t
        resetTiming()
        val reg = runCatching { t.setEventListener(trackerListener) }
        if (reg.isFailure) {
            tracker = null
            val msg = reg.exceptionOrNull()?.message ?: ResText.core("s_f26a5fb8")
            if (retry(attempt, ResText.core("s_c9660eea", msg))) return
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_c9660eea", msg))
            return
        }
        _state.value = _state.value.copy(measuring = true, statusText = ResText.core("s_778ebdad"))
    }

    /** 退避重试：返回 true 表示已排入重试，调用方不要再报错。 */
    private fun retry(attempt: Int, reason: ResText): Boolean {
        if (attempt >= MAX_START_RETRY) return false
        _state.value = _state.value.copy(
            measuring = true,
            statusText = ResText.core("s_6ef1c1ee", reason),
        )
        handler.postDelayed({ if (_state.value.measuring) startTracking(attempt + 1) }, 300L * (attempt + 1))
        return true
    }

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(points: List<com.samsung.android.service.health.tracking.data.DataPoint>) {
            // 回调在 SDK 绑定线程：任何未捕获异常都会直接杀进程（闪退），必须兜底
            runCatching {
                val now = System.currentTimeMillis()
                if (firstSampleAt == 0L) firstSampleAt = now
                lastDataAt = now

                var leadRaw = _state.value.leadRaw
                for (dp in points) {
                    val seq = runCatching { dp.getValue(ValueKey.EcgSet.SEQUENCE) }.getOrNull()
                    if (seq != null && seq >= 0) {
                        seqSeen = true
                        if (firstSeq < 0) firstSeq = seq.toLong()
                        lastSeq = seq.toLong()
                    }
                    val lo = runCatching { dp.getValue(ValueKey.EcgSet.LEAD_OFF) }.getOrNull()
                    if (lo != null) leadRaw = lo
                    val mv = runCatching { dp.getValue(ValueKey.EcgSet.ECG_MV) }.getOrNull() ?: continue
                    // 心电会话自带的那一路 PPG（约 500 Hz，原始 ADC 计数）。
                    // ⚠ 它**不能**走后端那条 `PPG_CONTINUOUS`（25 Hz、三路）的链路：
                    //   采样率差 20 倍、通道数也不同，`PpgSource.WATCH_ECG` 是独立的一支。
                    //   读不到就是 null（本机不开放该通道时），**不影响心电本身**。
                    val pg = runCatching { dp.getValue(ValueKey.EcgSet.PPG_GREEN) }.getOrNull()
                    sampleCount++
                    if (pg != null) ppgSeen++
                    // 只在 SDK 明确报"离体"时丢弃；不再按 mv==0f 丢点（见类注释）
                    if (lo != null && lo == 5) continue
                    onSample?.invoke(coupler.push(mv), pg)
                }
                estimateRate(now)
                val dropped = if (seqSeen && lastSeq >= firstSeq) {
                    ((lastSeq - firstSeq + 1) - sampleCount).coerceAtLeast(0).toInt()
                } else 0
                val st = _state.value
                _state.value = st.copy(
                    received = st.received + points.size,
                    leadRaw = leadRaw,
                    leadOff = leadRaw == 5,
                    sampleRateHz = sampleRateHz,
                    dropped = dropped,
                    seqAvailable = seqSeen,
                    ppgSeen = ppgSeen,
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_be05dd60", e.javaClass.simpleName, e.message),
                )
            }
        }

        override fun onFlushCompleted() {}

        override fun onError(error: HealthTracker.TrackerError) {
            when (error) {
                HealthTracker.TrackerError.SDK_POLICY_ERROR -> _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_f7b279ee"),
                )
                HealthTracker.TrackerError.PERMISSION_ERROR -> _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_f84ce9c2"),
                )
                else -> _state.value = _state.value.copy(
                    measuring = false, error = ResText.core("s_30499470", error)
                )
            }
        }
    }

    /**
     * 用 SEQUENCE 序号跨度 / 墙钟时长实测采样率；没有 SEQUENCE 时退化为 点数/时长。
     * 得到采样率后把去直流窗口调整到 1 秒，保证去趋势不吞掉 QRS。
     */
    private fun estimateRate(nowMs: Long) {
        if (firstSampleAt == 0L) return
        val elapsed = (nowMs - firstSampleAt) / 1000.0
        if (elapsed < 1.5 || sampleCount < 150) return
        val span = if (seqSeen && lastSeq > firstSeq) (lastSeq - firstSeq).toDouble() else sampleCount.toDouble()
        val fs = span / elapsed
        if (fs < 40.0 || fs > 1500.0) return
        val r = fs.roundToInt()
        if (r != sampleRateHz) {
            sampleRateHz = r
            coupler.resizeWindow(r)
        }
    }

    companion object {
        /** 连续无数据的判定门限：正常采集是连续流，4 秒空窗必然异常。 */
        const val STALL_TIMEOUT_MS = 4_000L

        /** tracker 建立失败的最大重试次数。 */
        private const val MAX_START_RETRY = 2
    }
}
