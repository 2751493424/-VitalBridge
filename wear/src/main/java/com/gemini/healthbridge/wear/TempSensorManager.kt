package com.gemini.healthbridge.wear

import com.gemini.healthbridge.core.Temperature
import com.gemini.healthbridge.core.ResText
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 三星 Health Sensor SDK 的皮肤温度**按需单次测量**
 * （`HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND`）。
 *
 * ## 为什么是手表端自己测，而不是读三星健康
 * 三星健康里的皮肤温度**只有睡眠期间**采集，且 App 上展示的是"相对个人基线的偏差"（±0.x ℃），
 * 白天想看一眼"现在多少度"是拿不到的。Sensor SDK 的按需测量给的
 * `OBJECT_TEMPERATURE` 是**当下腕部接触面的绝对温度**（官方文档写明单位 Celsius），
 * 这才是"随时点检"唯一可行的数据来源。
 *
 * ## 三条必须记住的硬限制（来自官方文档）
 *  ① **需要 Galaxy Watch5 或更新**（Watch4 没有温度传感器）。不支持时 [State.supported] = false，
 *     界面要明说"本机不支持"，而不是显示一个永远测不出来的界面。
 *  ② **单次不超过 30 秒**，且 on-demand tracker 同一时刻只允许一个在跑
 *     （所以开始测量前 MainActivity 会先 `stopAllMeasurements()`）。
 *  ③ 必须在**前台**应用里用，不能后台跑。
 *
 * ## 为什么要取多次采样的中位数
 * 腕表刚抬手/刚系紧表带时，接触面温度还在向皮肤温度逼近，最初几个点会偏低或抖动。
 * 单点直取会把这段过渡期的值当成结果。这里累积所有 `STATUS == 0` 的有效样本，
 * 对外给出**中位数** —— 与心电那边"必须用中位数以外的鲁棒量"不同，这里样本量小（个位数）、
 * 分布对称，中位数既抗抖动又不需要额外的分布假设。
 *
 * 连接复用 [SamsungSensorClient]（与心电、血氧共用一个 HealthTrackingService）。
 */
class TempSensorManager(private val client: SamsungSensorClient) {

    data class State(
        val measuring: Boolean = false,
        /** 腕部皮肤温度（℃），已按 [Temperature.isValid] 过滤并取中位数。 */
        val celsius: Float? = null,
        /** 环境温度（℃），三星一并给出；某些场景下可能为 null。 */
        val ambientC: Float? = null,
        /** 本次已收到的有效样本数（诊断用，也用来给用户"正在收敛"的反馈）。 */
        val validSamples: Int = 0,
        val statusText: ResText = ResText.EMPTY,
        val error: ResText? = null,
        /** 最近一次的 SDK STATUS 原值（0 正常 / -1 错误），用于诊断显示。 */
        val lastStatus: Int? = null,
        /** 收到过的数据点总数（含无效），用于区分"没数据"与"数据全无效"。 */
        val receivedCount: Int = 0,
        /** 能力探测结果：null = 还没探测，false = 本机不支持。 */
        val supported: Boolean? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tracker: HealthTracker? = null

    /** 本次测量的有效样本（累积后取中位数）。 */
    private val valid = mutableListOf<Float>()
    private val ambient = mutableListOf<Float>()

    private val connectionOwner = object : SamsungSensorClient.Owner {
        override fun onConnected() {
            startTracking()
        }

        override fun onConnectionEnded() {
            _state.value = _state.value.copy(measuring = false)
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_10cf7950", e.errorCode, e.message),
            )
        }
    }

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(points: List<DataPoint>) {
            // 回调在 SDK 绑定线程：任何未捕获异常都会直接杀进程（闪退），必须兜底。
            // 这里尤其重要 —— 温度是本项目里唯一用 Float 承载健康值的通道，
            // 一旦 SDK 在某一版里给出 null，直接拆箱就会崩。
            runCatching {
                val st0 = _state.value
                _state.value = st0.copy(receivedCount = st0.receivedCount + points.size)
                for (dp in points) {
                    val status = dp.getValue(ValueKey.SkinTemperatureSet.STATUS)
                    val obj = dp.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE)
                    val amb = dp.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE)

                    if (_state.value.lastStatus != status) {
                        _state.value = _state.value.copy(lastStatus = status)
                    }

                    // 官方文档：STATUS 0 = 正常，-1 = 错误。只有 0 才代表这次读数可信。
                    if (status != null && status != 0) {
                        _state.value = _state.value.copy(statusText = ResText.core("s_b33cf0af", status))
                        continue
                    }

                    // obj 本来就是 Float?（ValueKey<Float>），不需要 toFloat() 转换。
                    // 注意 `c == null` 必须先判：Temperature.isValid 是普通函数调用、不带断言，
                    // 编译器不会据此把 c 智能转换成非空，后面 valid.add(c) 就编译不过。
                    val c: Float? = obj
                    if (c == null || !Temperature.isValid(c)) {
                        // 丢弃但不静默：把原始值放进状态文字，否则"一直测不出来"会变成查不出原因的现象。
                        if (c != null) {
                            _state.value = _state.value.copy(
                                statusText = ResText.core("s_ef841427", Temperature.format(c)),
                            )
                        }
                        continue
                    }
                    valid.add(c)
                    if (amb != null && !amb.isNaN()) ambient.add(amb)
                    _state.value = _state.value.copy(
                        celsius = medianOf(valid),
                        ambientC = medianOf(ambient),
                        validSamples = valid.size,
                        statusText = if (valid.size == 1) ResText.core("s_43c2a678") else ResText.core("s_a4741644", valid.size),
                    )
                }
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_034c12b9", e.javaClass.simpleName, e.message),
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
                    measuring = false, error = ResText.core("s_9df4ec16"),
                )
                else -> _state.value = _state.value.copy(
                    measuring = false, error = ResText.core("s_30499470", error),
                )
            }
        }
    }

    /** 开始一次体温按需测量（自动连接共享服务）。 */
    fun startMeasurement() {
        valid.clear()
        ambient.clear()
        _state.value = _state.value.copy(
            measuring = true, celsius = null, ambientC = null, validSamples = 0,
            statusText = ResText.core("s_6ac70683"), error = null, lastStatus = null,
            // supported 保留上一次的探测结果，避免重新测量时闪一下"不支持"
            supported = _state.value.supported,
            receivedCount = 0,
        )
        val ok = client.connect(connectionOwner)
        if (!ok) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_933c0c84"))
        }
    }

    /**
     * 停止测量并断开共享服务。
     *
     * 与血氧同理**必须断开**：on-demand tracker 不 `unsetEventListener()` 的话，
     * 传感器会话会残留（LED/功耗都还在）。官方文档也要求单次测量结束后停掉 tracker。
     */
    fun stopMeasurement() {
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        client.disconnect()
        _state.value = _state.value.copy(measuring = false)
    }

    /**
     * 探测本机是否支持皮肤温度测量。
     *
     * 返回 `null` = **现在还不知道**（服务尚未连接）。这一点必须严格守住：
     * `getTrackingCapability()` 在服务连上之前会返回空列表，若把它当成"支持的能力就是空的"，
     * 就会在一台完全支持的 Watch6 上显示"本机不支持"，而用户毫无办法。
     * 所以没连上时只报"不知道"，真正的结论等 `startTracking()` 里那次能力查询给出。
     */
    fun probeSupport(): Boolean? {
        if (!client.isConnected) return null
        val types = supportedTypes()
        if (types.isEmpty()) return null
        val hasOnDemand = types.contains(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND)
        // 1.4.1 里 SKIN_TEMPERATURE（无后缀）已标记 deprecated，但旧机型可能只报它。
        val hasLegacy = types.contains(HealthTrackerType.SKIN_TEMPERATURE)
        val support = hasOnDemand || hasLegacy
        _state.value = _state.value.copy(supported = support)
        return support
    }

    private fun supportedTypes(): List<HealthTrackerType> = runCatching {
        client.service.getTrackingCapability().supportHealthTrackerTypes
    }.getOrDefault(emptyList())

    private fun startTracking() {
        val types = supportedTypes()
        val type = when {
            types.contains(HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND) ->
                HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND
            types.contains(HealthTrackerType.SKIN_TEMPERATURE) ->
                HealthTrackerType.SKIN_TEMPERATURE
            else -> {
                _state.value = _state.value.copy(
                    measuring = false,
                    supported = false,
                    error = ResText.core("s_57be7f04") +
                        ResText.core("s_ee98fed6", types.joinToString { it.name }),
                )
                return
            }
        }
        _state.value = _state.value.copy(supported = true)
        val t = try {
            client.service.getHealthTracker(type)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_db9aed4f", e.message),
            )
            return
        }
        tracker = t
        runCatching { t.setEventListener(trackerListener) }.onFailure { e ->
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_84c4ef03", e.message))
            return
        }
        _state.value = _state.value.copy(measuring = true, statusText = ResText.core("s_cfa65f41"))
    }

    private fun medianOf(src: List<Float>): Float? {
        if (src.isEmpty()) return null
        val s = src.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2f
    }
}
