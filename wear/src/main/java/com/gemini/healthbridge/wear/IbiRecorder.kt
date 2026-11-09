package com.gemini.healthbridge.wear

import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.ResText
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * PPG 逐拍间期会话（`IBI_LIST`）—— 让 HRV **不必测心电**。
 *
 * ## 为什么值得做
 * 测心电要 30 秒 + 手指按住表冠电极 + 贴紧 + 保持不动，条件很挑；
 * 而 PPG 的逐拍间期是**戴着表就能拿**的。会话做成 2 分钟固定时长，
 * 用户点一下、保持静止即可，**不需要任何电极接触**。
 *
 * ## 三个已查实的事实（都是 `SensorProbe` 真机测出来的，别凭印象改）
 * 1. **`IBI_LIST` 在 `HeartRateSet` 里，不在 `PpgSet` 里** —— `PpgSet` 只有绿/红/红外 +
 *    三路状态，**没有 IBI**。所以必须单开一个**心率**会话，不能指望搭 PPG 采集的车。
 * 2. **投递率约 1 Hz**：静止 60 秒得 60 个包、约 67 个间期（≈1.2 拍/秒），**覆盖是完整的**。
 * 3. ⚠ **`IBI_STATUS_LIST` 的语义至今未定**：静止 60 秒里状态 -1 有 56 个（值域跨全范围）、
 *    状态 0 只有 11 个且**全落在 790~882 ms 的窄带**。两种解释都说得通
 *    （"0 = 有效"则只有 16% 可用、不够 HRV；"-1 = 未知"则可用），**所以不能拿它过滤**。
 *    这里只把状态直方图**记下来备查**（[State.statusHistogram]），
 *    过滤一律交给 `Hrv` 那套（生理值域 + 中位比例 + 伪迹占比超 20% 拒答）——
 *    它在静止条件下把伪迹判成 5%，是经得起看的。
 *
 * ## 与心电那条路的关系：**两条独立的基线**
 * `Hrv.Source` 刻意分开（`WATCH_ECG` / `WATCH_PPG`）：RR 是真正的 R 峰间期，
 * 而 IBI 是脉搏间期，受脉搏波传导与形态影响。**两者不能混进同一序列、基线不能互相污染**
 * （`Hrv.kt` 的类注释已写明）。所以本会话一律写 `source = watch_ppg`。
 *
 * ## 存的是**未过滤**的间期
 * 与心电路径同一条理由：伪迹修正要用"约 2 倍长"这条线索，预先过滤就把它销毁了。
 * 过滤在 `Hrv.analyze` 内部做，界面拿到的已经是结论。
 *
 * ## 免费版纪律
 * 手动点一下、固定时长、到点自动注销监听 —— 与现有测量完全一致，不做后台常驻采集。
 */
class IbiRecorder(
    private val client: SamsungSensorClient,
    private val scope: CoroutineScope,
    /** 活动档位提供者（`AccelSampler` 的当前档位）。null = 未判定，**不等于静息**。 */
    private val activityLevel: () -> String?,
) {

    data class State(
        val measuring: Boolean = false,
        /** 还剩多少毫秒（倒计时用）。 */
        val remainingMs: Long = 0L,
        /** 0f~1f，画进度用。 */
        val progress: Float = 0f,
        /** 已收到的逐拍间期个数（实时给用户看，证明"它在工作"）。 */
        val beats: Int = 0,
        val statusText: ResText = ResText.EMPTY,
        val error: ResText? = null,
        /** 会话结束后的结论；未结束或已放弃时为 null。 */
        val result: Hrv.Result? = null,
        /**
         * SDK 在同一个包里**独立算出**的心率（`HeartRateSet.HEART_RATE`）中位数。
         *
         * 它与逐拍间期来自同一路光信号，所以**不能证明逐拍细节正确** ——
         * 但能抓住"整体错位"（漏拍、重复、序列拼接错）。见 [Hrv.hrDeviationPct]。
         */
        val sdkHr: Int? = null,
        /** 中位心率相对 [sdkHr] 的偏差（百分数）；拿不到时为 null（**不是 0**）。 */
        val hrDeviationPct: Double? = null,
        /**
         * `IBI_STATUS_LIST` 的直方图（状态码 → 出现次数）。
         * **不参与过滤**，只为把那个未定的语义留证据（见类注释第 3 条）。
         */
        val statusHistogram: Map<Int, Int> = emptyMap(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tracker: HealthTracker? = null
    private var job: Job? = null

    /**
     * 会话结算回调（原始间期、结论、活动档位）。
     *
     * 落库留在 `MainActivity` —— 与心电路径同一个做法：本类只管采集与判据，
     * 不碰数据库，这样它不依赖 `AppDatabase`，也更好读。
     */
    var onSession: ((raw: List<Int>, result: Hrv.Result, activityLevel: String?) -> Unit)? = null

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

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(points: List<DataPoint>) {
            // ⚠ SDK 回调在自己的线程上，**未捕获异常直接杀进程**，必须兜底。
            runCatching {
                var beats = _state.value.beats
                var hist = _state.value.statusHistogram
                for (dp in points) {
                    // 收下 SDK 独立算出的心率（**以前收下就扔了**）：它是 PPG 这条路上
                    // 唯一的"不依赖伪迹模型"的校验量，见 `Hrv.hrDeviationPct`。
                    dp.getValue(ValueKey.HeartRateSet.HEART_RATE)?.let { hr ->
                        if (hr > 0) synchronized(hrSeen) { hrSeen.add(hr) }
                    }
                    dp.getValue(ValueKey.HeartRateSet.IBI_LIST)?.let { list ->
                        if (list.isNotEmpty()) {
                            synchronized(rawIntervals) { rawIntervals.addAll(list) }
                            beats += list.size
                        }
                    }
                    // 状态只统计、不过滤（语义未定，见类注释第 3 条）。
                    dp.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)?.forEach { st ->
                        hist = hist + (st to (hist[st] ?: 0) + 1)
                    }
                }
                _state.value = _state.value.copy(beats = beats, statusHistogram = hist)
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_034c12b9", e.javaClass.simpleName, e.message),
                )
            }
        }

        override fun onFlushCompleted() {}

        override fun onError(error: HealthTracker.TrackerError) {
            // 出错不立刻判死：心率连续型偶发报错后还能继续出数，让它跑完这一轮。
            runCatching {
                _state.value = _state.value.copy(statusText = ResText.core("s_2dd87159", error))
            }
        }
    }

    /**
     * 未过滤的逐拍间期（毫秒，按到达顺序）。
     *
     * 只有 SDK 回调线程写、主线程结算时读，所以两处都加锁 —— 这个类不像
     * `EcgMeasurer` 那样能假设"全程都在主线程"，多一层保护换的是**不必推理线程**
     * （而推理错了的后果是丢一批间期，且不报错）。
     */
    private val rawIntervals = ArrayList<Int>()

    /** 会话中见过的 SDK 心率（同一个包里独立算出的）。与 [rawIntervals] 同样加锁。 */
    private val hrSeen = ArrayList<Int>()

    companion object {
        /**
         * 会话时长：**2 分钟**。
         *
         * 不是随手定的：`Hrv.LF_MIN_SPAN_MS = 120_000` —— 频域的 LF 段至少要 2 分钟跨度
         * 才给得出（HF 只要 60 秒）。取 2 分钟让**一次会话就能拿到 LF/HF**，
         * 而不是"再测一次才够"。60 秒虽然够时域，但频域会明说"时长不够"。
         */
        const val SESSION_MS = 120_000L

        /** 倒计时刷新间隔。250ms 足够让秒数跟手，又不至于每帧都重组。 */
        private const val TICK_MS = 250L
    }

    /** 开始一次 PPG 逐拍间期会话（自动连接共享服务）。 */
    fun startMeasurement() {
        if (_state.value.measuring) return
        synchronized(rawIntervals) { rawIntervals.clear() }
        synchronized(hrSeen) { hrSeen.clear() }
        _state.value = State(
            measuring = true,
            remainingMs = SESSION_MS,
            statusText = ResText.core("s_6ac70683"),
        )
        val ok = client.connect(connectionOwner)
        if (!ok) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_933c0c84"))
        }
    }

    /**
     * 停止采集并断开共享服务（必须断开，否则传感器会话残留）。
     *
     * **不结算、不产结论** —— 结算走 [finish]，放弃走 [discard]。
     */
    private fun stopTracking() {
        job?.cancel()
        job = null
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        client.disconnect()
        _state.value = _state.value.copy(measuring = false)
    }

    /** 收尾并结算（倒计时归零时自动调用；用户提前按停也走它，照样出结果）。 */
    fun finish() {
        stopTracking()
        val raw = synchronized(rawIntervals) { ArrayList(rawIntervals) }
        val hrs = synchronized(hrSeen) { ArrayList(hrSeen) }
        // 用中位数而不是均值：SDK 心率在会话首尾可能偏低（刚戴上/手臂在动），
        // 均值会被那几拍拽走，而这里要的是"这段的典型心率"。
        val sdkHr = if (hrs.isEmpty()) null else Hrv.median(hrs).roundToInt()
        val devPct = Hrv.hrDeviationPct(raw, sdkHr)
        val level = runCatching { activityLevel() }.getOrNull()
        if (raw.isEmpty()) {
            _state.value = _state.value.copy(
                statusText = ResText.core("s_b6bea6ee"),
                error = ResText.core("s_15d3ed9f") +
                    ResText.core("s_4a0d8965"),
            )
            // ── 测量结束的震动提示（2026-10-07）：一个间期都没收到 ⇒ 两短震 ──
            Buzzer.fail()
            return
        }
        // 存**未过滤**的间期（理由见类注释）。过滤与拒答全交给 core。
        val result = Hrv.analyze(
            rawRrMs = raw,
            source = Hrv.Source.WATCH_PPG,
            activityLevel = level,
            // 没有心电，也就没有"节律规整度"这个指标 —— 传 null 而不是编一个。
            ecgRegular = null,
        )
        _state.value = _state.value.copy(
            measuring = false,
            remainingMs = 0L,
            progress = 1f,
            statusText = ResText.core("s_12d7163e", raw.size),
            result = result,
            sdkHr = sdkHr,
            hrDeviationPct = devPct,
        )
        runCatching { onSession?.invoke(raw, result, level) }
        // ── 测量结束的震动提示（2026-10-07）──
        // 这个会话固定 2 分钟、全程要求静坐不看屏幕，是最需要提示的一个。
        // 出得了指标（`Ok`）一次短震；`NotApplicable`（时长短/伪迹太多等拒答）两短震 ——
        // ⚠ 拒答**也是**一次"结束了但没结果"，不震的话用户会一直等下去。
        // ⚠ 只在这里震：`discard()`（用户主动放弃）**不震**，那是他自己的动作。
        if (result is Hrv.Result.Ok) Buzzer.ok() else Buzzer.fail()
    }

    /** 放弃本次会话（返回时用）：不结算、不留结果。 */
    fun discard() {
        stopTracking()
        synchronized(rawIntervals) { rawIntervals.clear() }
        synchronized(hrSeen) { hrSeen.clear() }
        _state.value = State()
    }

    private fun startTracking() {
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())
        // ⚠ IBI 只在 HeartRateSet 里，所以只能用心率采集器（不是 PPG 那个）。
        val type = when {
            types.contains(HealthTrackerType.HEART_RATE_CONTINUOUS) ->
                HealthTrackerType.HEART_RATE_CONTINUOUS
            types.contains(HealthTrackerType.HEART_RATE) -> HealthTrackerType.HEART_RATE
            else -> {
                _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_d81c2912"),
                )
                return
            }
        }
        val t = try {
            client.service.getHealthTracker(type)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_ef129119", e.message)
            )
            return
        }
        tracker = t
        runCatching { t.setEventListener(trackerListener) }.onFailure { e ->
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_84c4ef03", e.message))
            return
        }
        _state.value = _state.value.copy(measuring = true, statusText = ResText.core("s_170baf92"))
        startCountdown()
    }

    /**
     * 倒计时。
     *
     * ⚠ 进度与剩余时间都从「**开始时刻 + 当前时刻**」算，**不在循环里累加**：
     * 累加会随丢帧/挂起漂移，最后显示的那一秒是假的 —— 而"还剩多久"正是用户唯一的依据
     * （他得据此决定还能不能动）。
     */
    private fun startCountdown() {
        job?.cancel()
        job = scope.launch {
            val startAt = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startAt
                val left = (SESSION_MS - elapsed).coerceAtLeast(0L)
                _state.value = _state.value.copy(
                    remainingMs = left,
                    progress = (elapsed.toFloat() / SESSION_MS).coerceIn(0f, 1f),
                )
                if (left <= 0L) break
                delay(TICK_MS)
            }
            runCatching { finish() }
        }
    }
}
