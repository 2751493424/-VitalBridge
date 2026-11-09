package com.gemini.healthbridge.wear

import com.gemini.healthbridge.core.MfBia
import com.gemini.healthbridge.core.ResText
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.TrackerUserProfile
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 三星 Health Sensor SDK 的多频阻抗测量（`HealthTrackerType.MF_BIA_ON_DEMAND`）。
 *
 * ## 与 [BiaSensorManager] 的关系：**两个不同的采集器，不是一个的升级**
 *  · `BIA_ON_DEMAND` → `ValueKey.BiaSet`：三星**算好的体成分** + 一个阻抗幅值/相位。
 *  · `MF_BIA_ON_DEMAND` → `ValueKey.MfBiaSet`：**四频原始阻抗**（5/10/50/250 kHz，各带幅值与相位）。
 * 所以体成分那条路继续用 `BiaSet`（我们不重算体成分，见 `MfBia` 的类注释），
 * 本类只负责把**原始阻抗谱**采下来。
 *
 * ## 为什么也调 `getHealthTracker(type, profile)`
 * 与 BIA 同样需要 `TrackerUserProfile`（SDK 要用身高/体重等做内部的体水模型），
 * 单参数的 `getHealthTracker(type)` 会直接失败 —— 那个坑 `BiaSensorManager` 里已经记过一次。
 *
 * ## 能力检测必须与"没连上"分开
 * 与 [BiaSensorManager.probeSupport] 同一条纪律：`null` = 还不知道，`false` = 确定不支持。
 * 本机的 `MF_BIA_ON_DEMAND` 支持情况**尚未在真机上验证过**（SDK 1.4.1 里有这个类型，
 * 但不同机型/固件的 `supportHealthTrackerTypes` 未必包含它），所以界面必须
 * **不支持就不显示入口**，而不是显示一个点了会失败的按钮。
 *
 * ## 失败面全部关在本地
 * 取 tracker 失败 / 注册失败 / tracker 报错 → 只写 [State.error]，不影响其它任何功能
 * （BIA、心电、血氧各自独立）。
 */
class MfBiaSensorManager(private val client: SamsungSensorClient) {

    /** 一次测量的原始结果：四个频点的幅值与相位（可空，SDK 给不出就是 null）。 */
    data class MfBiaResult(
        val m5: Float? = null, val p5: Float? = null,
        val m10: Float? = null, val p10: Float? = null,
        val m50: Float? = null, val p50: Float? = null,
        val m250: Float? = null, val p250: Float? = null,
        val status: Int? = null,
    )

    data class State(
        val measuring: Boolean = false,
        /** 测量进度 0~1（SDK 给的是完成度，用来驱动进度环）。 */
        val progress: Float = 0f,
        /** null = 还没探测过；false = 本机不支持多频阻抗。 */
        val supported: Boolean? = null,
        val result: MfBiaResult? = null,
        val lastStatus: Int? = null,
        val statusText: ResText = ResText.EMPTY,
        val receivedCount: Int = 0,
        val error: ResText? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tracker: HealthTracker? = null
    private var pendingProfile: com.gemini.healthbridge.core.BiaProfileDto? = null

    private val connectionOwner = object : SamsungSensorClient.Owner {
        override fun onConnected() = startTracking()

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
        override fun onDataReceived(
            points: List<com.samsung.android.service.health.tracking.data.DataPoint>,
        ) {
            // 回调在 SDK 的绑定线程上：未捕获异常会直接杀进程，整段必须兜底
            runCatching {
                val st0 = _state.value
                _state.value = st0.copy(receivedCount = st0.receivedCount + points.size)

                for (dp in points) {
                    val status = dp.getValue(ValueKey.MfBiaSet.STATUS)
                    val progress = dp.getValue(ValueKey.MfBiaSet.PROGRESS)
                    _state.value = _state.value.copy(
                        lastStatus = status,
                        progress = normalizeProgress(progress),
                    )

                    // 状态 0 才算成功（与 BodyComposition.statusOf 同一套）。
                    // 与 BIA 那处同样的理由：失败时 SDK 也可能带上部分旧值，
                    // 只判"字段非空就成功"会把一次失败显示成一次成功的测量。
                    if (status == MfBia.STATUS_OK) {
                        val r = MfBiaResult(
                            m5 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_5K),
                            p5 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_5K),
                            m10 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_10K),
                            p10 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_10K),
                            m50 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_50K),
                            p50 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_50K),
                            m250 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_MAGNITUDE_250K),
                            p250 = dp.getValue(ValueKey.MfBiaSet.BODY_IMPEDANCE_PHASE_250K),
                            status = status,
                        )
                        // 一个幅值都没有 → 不当成成功，否则界面会显示一整屏 "——"
                        if (hasAnyMagnitude(r)) {
                            _state.value = _state.value.copy(result = r, progress = 1f)
                            stopMeasurement()
                        } else {
                            _state.value = _state.value.copy(
                                measuring = false,
                                error = ResText.core("s_5cda2491", _state.value.receivedCount),
                            )
                            stopMeasurement()
                        }
                    } else if (status != null) {
                        // 中间态（手指还没放好等）：只更新提示、继续等。
                        // 是否失败由界面在测量循环结束时判定。
                        _state.value = _state.value.copy(
                            statusText = mfStatusText(status),
                        )
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
                    measuring = false, error = ResText.core("s_9df4ec16"),
                )
                else -> _state.value = _state.value.copy(
                    measuring = false, error = ResText.core("s_30499470", error),
                )
            }
        }
    }

    /**
     * 探测本机是否支持多频阻抗。
     *
     * `null` = 还不知道（服务没连上 / 能力列表为空），必须与 `false`（确定不支持）分开 ——
     * 没连上就报"不支持"会在支持的机型上显示错的结论，而用户毫无办法
     * （这个坑 `TempSensorManager.probeSupport` 里记过一次）。
     */
    fun probeSupport(): Boolean? {
        if (!client.isConnected) return null
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())
        if (types.isEmpty()) return null
        val support = types.contains(HealthTrackerType.MF_BIA_ON_DEMAND)
        _state.value = _state.value.copy(supported = support)
        return support
    }

    /** 开始一次多频阻抗测量。[profile] 缺失时**不发起测量**，直接说明原因。 */
    fun startMeasurement(profile: com.gemini.healthbridge.core.BiaProfileDto?) {
        if (profile == null || !profile.isValid()) {
            _state.value = _state.value.copy(
                measuring = false,
                error = ResText.core("s_768c0cef"),
            )
            return
        }
        pendingProfile = profile
        _state.value = _state.value.copy(
            measuring = true, progress = 0f, result = null,
            statusText = ResText.core("s_6ac70683"), error = null,
        )
        if (!client.connect(connectionOwner)) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_933c0c84"))
        }
    }

    /** 停止测量并断开共享服务（与 BIA 同理：on-demand tracker 不停会残留会话）。 */
    fun stopMeasurement() {
        runCatching { tracker?.unsetEventListener() }
        tracker = null
        client.disconnect()
        _state.value = _state.value.copy(measuring = false)
    }

    private fun startTracking() {
        val profile = pendingProfile
        if (profile == null) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_7c0f90fb"))
            return
        }
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())
        if (!types.contains(HealthTrackerType.MF_BIA_ON_DEMAND)) {
            _state.value = _state.value.copy(
                measuring = false,
                supported = false,
                error = ResText.core("s_a182ad9e"),
            )
            return
        }

        // 资料先过一遍取值域，避免把不合法值送进 SDK 换来一个很难自查的状态码
        val userProfile = try {
            TrackerUserProfile.Builder()
                .setHeight(profile.heightCm)
                .setWeight(profile.weightKg)
                .setGender(profile.gender)
                .setAge(profile.age)
                .build()
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false,
                error = ResText.core("s_eb871cd6", e.message, profile.heightCm, profile.weightKg),
            )
            return
        }

        val t = try {
            // 必须用两参重载（同 BIA）：单参数会让 MF_BIA 取不到 tracker
            client.service.getHealthTracker(HealthTrackerType.MF_BIA_ON_DEMAND, userProfile)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false,
                error = ResText.core("s_c4f53144", e.javaClass.simpleName, e.message),
            )
            return
        }
        tracker = t
        runCatching { t.setEventListener(trackerListener) }.onFailure { e ->
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_84c4ef03", e.message))
            return
        }
        _state.value = _state.value.copy(measuring = true, statusText = ResText.core("s_b59e7dca"))
    }

    /**
     * 进度归一化（与 `BiaSensorManager.normalizeProgress` 同一处理）。
     *
     * 官方只写了 "Measurement progress."，**没写量纲**；两种写法都出现过，
     * 所以按值域判断而不是假定一种。只影响进度环观感，不污染任何结果值。
     */
    private fun normalizeProgress(p: Float?): Float {
        if (p == null || p.isNaN()) return 0f
        val v = if (p > 1f + 1e-3f) p / 100f else p
        return v.coerceIn(0f, 1f)
    }

    private fun hasAnyMagnitude(r: MfBiaResult): Boolean =
        r.m5 != null || r.m10 != null || r.m50 != null || r.m250 != null

    /** 状态码文案。**不猜语义**（三星没有公开 MF-BIA 的状态码表），按 BIA 的口径给通用提示。 */
    private fun mfStatusText(status: Int): ResText = when (status) {
        MfBia.STATUS_OK -> ResText.core("s_cb754364")
        else -> ResText.core("s_72d535b9", status)
    }
}
