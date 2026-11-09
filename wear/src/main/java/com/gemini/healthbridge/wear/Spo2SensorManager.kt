package com.gemini.healthbridge.wear

import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.gemini.healthbridge.core.ResText

/**
 * 三星 Health Sensor SDK 的血氧单次按需测量（HealthTrackerType.SPO2_ON_DEMAND）。
 * 前提：手表上开启 Health Sensor Service 开发者模式（Debug 包自用调试）。
 * 数据点 STATUS：2=测量完成（SPO2/HEART_RATE 有效）；-6=测量超时（该次被取消，需重启 Tracker 重试）；
 * -5=信号质量低；-4=设备移动。
 * 连接复用 SamsungSensorClient（与心电共用一个 HealthTrackingService）。
 */
class Spo2SensorManager(private val client: SamsungSensorClient) {

    data class State(
        val measuring: Boolean = false,
        val percent: Int? = null,
        val heartRate: Int? = null,
        val prompt: ResText? = null,
        val statusText: ResText = ResText.EMPTY,
        val error: ResText? = null,
        val lastStatus: Int? = null,
        val receivedCount: Int = 0,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var usingOnDemand = true
    private var attempts = 0
    private var sixCount = 0

    private var tracker: HealthTracker? = null

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
        override fun onDataReceived(points: List<com.samsung.android.service.health.tracking.data.DataPoint>) {
            // 回调在 SDK 绑定线程：任何未捕获异常都会直接杀进程（闪退），必须兜底
            runCatching {
                val st0 = _state.value
                _state.value = st0.copy(receivedCount = st0.receivedCount + points.size)
                for (dp in points) {
                    val status = dp.getValue(ValueKey.SpO2Set.STATUS)
                    val spo2 = dp.getValue(ValueKey.SpO2Set.SPO2)
                    val hr = dp.getValue(ValueKey.SpO2Set.HEART_RATE)
                    if (_state.value.lastStatus != status) {
                        _state.value = _state.value.copy(lastStatus = status)
                    }

                    if (usingOnDemand) {
                        when {
                            // 测量完成：血氧值有效
                            status == 2 && spo2 != null && spo2 in 1..100 -> {
                                _state.value = _state.value.copy(
                                    percent = spo2,
                                    heartRate = hr ?: _state.value.heartRate,
                                )
                            }
                            // 平台测量超时：该次测量已被服务取消，自动重试（最多 2 次）
                            status == -6 -> {
                                sixCount++
                                if (sixCount >= 8) {
                                    attempts++
                                    if (attempts > 2) {
                                        _state.value = _state.value.copy(
                                            prompt = ResText.core("s_ad4226c2"),
                                            error = ResText.core("s_7f4ea8ac"),
                                        )
                                        stopMeasurement()
                                    } else {
                                        _state.value = _state.value.copy(statusText = ResText.core("s_f169a62d", attempts))
                                        restartTracker()
                                    }
                                    return
                                }
                            }
                            status == -4 -> _state.value = _state.value.copy(
                                statusText = ResText.core("s_910cc97c")
                            )
                            status == -5 -> _state.value = _state.value.copy(
                                statusText = ResText.core("s_eef6b041")
                            )
                        }
                    } else {
                        // 连续型：每个有效值都可用
                        if (spo2 != null && spo2 in 1..100) {
                            _state.value = _state.value.copy(percent = spo2)
                        }
                    }
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
                    measuring = false, error = ResText.core("s_9df4ec16")
                )
                else -> _state.value = _state.value.copy(
                    measuring = false, error = ResText.core("s_30499470", error)
                )
            }
        }
    }

    /** 开始一次血氧按需测量（自动连接共享服务）。 */
    fun startMeasurement() {
        attempts = 0
        sixCount = 0
        _state.value = _state.value.copy(
            measuring = true, percent = null, heartRate = null,
            prompt = null, statusText = ResText.core("s_6ac70683"), error = null,
        )
        val ok = client.connect(connectionOwner)
        if (!ok) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_933c0c84"))
        }
    }

    /** 停止测量并断开共享服务（必须断开，否则传感器会话残留、LED 常亮）。 */
    fun stopMeasurement() {
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        client.disconnect()
        _state.value = _state.value.copy(measuring = false)
    }

    /** 清除佩戴提示（用户点确定后）。 */
    fun clearPrompt() {
        _state.value = _state.value.copy(prompt = null)
    }

    private fun startTracking() {
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())
        val type = when {
            types.contains(HealthTrackerType.SPO2_ON_DEMAND) -> HealthTrackerType.SPO2_ON_DEMAND
            types.contains(HealthTrackerType.SPO2) -> HealthTrackerType.SPO2
            else -> {
                _state.value = _state.value.copy(
                    measuring = false,
                    error = ResText.core("s_f79a6375", types.joinToString { it.name }),
                )
                return
            }
        }
        usingOnDemand = type == HealthTrackerType.SPO2_ON_DEMAND
        val t = try {
            client.service.getHealthTracker(type)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_5907b6be", e.message)
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

    /** 重新获取 Tracker（-6 超时后该次测量已被服务取消，需重试）。 */
    private fun restartTracker() {
        tracker?.unsetEventListener()
        tracker = null
        val t = try {
            client.service.getHealthTracker(HealthTrackerType.SPO2_ON_DEMAND)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false, error = ResText.core("s_b458407c", e.message)
            )
            return
        }
        tracker = t
        runCatching { t.setEventListener(trackerListener) }.onFailure { e ->
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_47dfe3a0", e.message))
            return
        }
    }
}
