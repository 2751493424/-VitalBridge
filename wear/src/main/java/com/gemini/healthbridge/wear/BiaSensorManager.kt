package com.gemini.healthbridge.wear

import com.gemini.healthbridge.core.BiaProfileDto
import com.gemini.healthbridge.core.BodyComposition
import com.gemini.healthbridge.core.ResText
import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.TrackerUserProfile
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 三星 Health Sensor SDK 的体成分按需测量（HealthTrackerType.BIA_ON_DEMAND）。
 *
 * ## 与前三个测量（心率/血氧/心电）最大的不同：需要**用户资料**
 * BIA 测的是**阻抗**，体脂率/体水量这些是 SDK 用"身高/体重/性别/年龄"反推出来的。
 * 三星为此提供的是另一个重载：
 * `getHealthTracker(HealthTrackerType, TrackerUserProfile)` ——
 * 用单参数的 `getHealthTracker(type)` 取 BIA tracker 会直接失败。
 *
 * 资料来自手机端（手表没有三星健康 Data SDK，读不到用户档案），
 * **没有资料就不开始测量**：宁可明说"请先在手机端同步资料"，也不要拿一份编造的
 * 默认资料去测 —— 那会得到一条看起来很正常的错误结果，比报错难发现得多。
 *
 * ## 取值域与状态码
 * 全部常量与话术集中在 `core/BodyComposition.kt`（纯 Kotlin，可离线断言）。
 * 这里只负责搬运。
 */
class BiaSensorManager(private val client: SamsungSensorClient) {

    /** 一次测量的结果。字段全可空 —— 失败时 SDK 只回状态码，不给数值。 */
    data class BiaResult(
        val bodyFatRatio: Float? = null,
        val bodyFatMass: Float? = null,
        val totalBodyWater: Float? = null,
        val skeletalMuscleRatio: Float? = null,
        val skeletalMuscleMass: Float? = null,
        val basalMetabolicRate: Float? = null,
        val fatFreeRatio: Float? = null,
        val fatFreeMass: Float? = null,
        val impedanceMagnitude: Float? = null,
        val impedanceDegree: Float? = null,
    )

    data class State(
        val measuring: Boolean = false,
        /** 测量进度 0~1。SDK 给的是"当前完成度"，用来驱动进度环。 */
        val progress: Float = 0f,
        /** null = 还没探测过；false = 本机没有 BIA 传感器（Watch4 及更早）。 */
        val supported: Boolean? = null,
        val result: BiaResult? = null,
        /** 最后一次收到的原始状态码，诊断用。 */
        val lastStatus: Int? = null,
        val statusText: ResText = ResText.EMPTY,
        val receivedCount: Int = 0,
        val error: ResText? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var tracker: HealthTracker? = null

    /** 本次会话使用的资料。startMeasurement 时记下，onConnected 回调里要用。 */
    private var pendingProfile: BiaProfileDto? = null

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

    private companion object {
        /** 至少测这么久才认结果（挡住"一秒出结果"的早期帧）。 */
        const val MIN_MEASURE_MS = 4_000L
        /** 超过这么久还没有可信结果 ⇒ 判定接触不良，别让用户干等。 */
        const val MAX_MEASURE_MS = 30_000L
    }

    private val trackerListener = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(points: List<com.samsung.android.service.health.tracking.data.DataPoint>) {
            // 回调在 SDK 的绑定线程上：任何未捕获异常都会直接杀进程（闪退），必须整段兜底
            runCatching {
                val st0 = _state.value
                _state.value = st0.copy(receivedCount = st0.receivedCount + points.size)

                for (dp in points) {
                    val status = dp.getValue(ValueKey.BiaSet.STATUS)
                    val progress = dp.getValue(ValueKey.BiaSet.PROGRESS)
                    _state.value = _state.value.copy(
                        lastStatus = status,
                        progress = normalizeProgress(progress),
                    )

                    // 状态 0 才是成功（见 BodyComposition.statusOf）。
                    // 注意不能只判"结果字段非空就成功"：失败时 SDK 也可能带上
                    // 上一次的部分值，那样会把一次失败显示成一次成功的测量。
                    if (status == 0) {
                        val r = BiaResult(
                            bodyFatRatio = dp.getValue(ValueKey.BiaSet.BODY_FAT_RATIO),
                            bodyFatMass = dp.getValue(ValueKey.BiaSet.BODY_FAT_MASS),
                            totalBodyWater = dp.getValue(ValueKey.BiaSet.TOTAL_BODY_WATER),
                            skeletalMuscleRatio = dp.getValue(ValueKey.BiaSet.SKELETAL_MUSCLE_RATIO),
                            skeletalMuscleMass = dp.getValue(ValueKey.BiaSet.SKELETAL_MUSCLE_MASS),
                            basalMetabolicRate = dp.getValue(ValueKey.BiaSet.BASAL_METABOLIC_RATE),
                            fatFreeRatio = dp.getValue(ValueKey.BiaSet.FAT_FREE_RATIO),
                            fatFreeMass = dp.getValue(ValueKey.BiaSet.FAT_FREE_MASS),
                            impedanceMagnitude = dp.getValue(ValueKey.BiaSet.BODY_IMPEDANCE_MAGNITUDE),
                            impedanceDegree = dp.getValue(ValueKey.BiaSet.BODY_IMPEDANCE_DEGREE),
                        )
                        val elapsed = System.currentTimeMillis() - startedAtMs
                        // ⚠⚠ 先判"可不可信"再判"有没有值"（2026-10-22）：
                        //   原来只要 status==0 且有任何数值就立刻当成功 ⇒ 1 秒的垃圾帧被当真，
                        //   阻抗 4446 Ω 那种也过了，体脂自然离谱。
                        if (hasAnyValue(r) && !isPlausible(r, elapsed)) {
                            rejected++
                            _state.value = _state.value.copy(
                                statusText = ResText.core("s_b59e7dca"),
                            )
                            // 明显是接触没建立：立刻结束，让用户重测（别继续等 30 秒）
                            if (elapsed >= MAX_MEASURE_MS || (r.impedanceMagnitude ?: 0f) > 1500f) {
                                _state.value = _state.value.copy(
                                    measuring = false,
                                    error = ResText.core("s_2e87d857"),
                                )
                                stopMeasurement()
                            }
                            continue
                        }
                        // 一个有效值都没有 → 不当作成功，否则界面会显示一整屏 "——"
                        if (hasAnyValue(r)) {
                            _state.value = _state.value.copy(
                                result = r,
                                progress = 1f,
                                statusText = BodyComposition.statusOf(0).label,
                            )
                            stopMeasurement()
                        } else {
                            _state.value = _state.value.copy(
                                measuring = false,
                                error = ResText.core("s_b6bdaabb", _state.value.receivedCount),
                            )
                            stopMeasurement()
                        }
                    } else if (status != null) {
                        // 中间态：三星在测量过程中会推非 0 状态（例如手指还没放好），
                        // 这些**不代表这次测量已经失败**，只更新提示文字、继续等。
                        // 只有当测量循环结束仍是非 0 时，才由界面判定为失败。
                        _state.value = _state.value.copy(
                            statusText = BodyComposition.statusOf(status).label,
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
     * 探测本机是否支持体成分测量。
     *
     * 返回 `null` 表示**还不知道**（服务没连上 / 能力列表为空），必须与 `false`
     * （确定不支持）分开 —— 没连上就报"不支持"，会在一台完全支持的 Watch6 上
     * 显示"本机不支持"，而用户毫无办法。体温页踩过这个坑，
     * 见 `TempSensorManager.probeSupport` 的注释。真正的结论由 [startTracking]
     * 里那次能力查询给出（那时服务一定已连上）。
     */
    fun probeSupport(): Boolean? {
        if (!client.isConnected) return null
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrDefault(emptyList())
        if (types.isEmpty()) return null
        val support = types.contains(HealthTrackerType.BIA_ON_DEMAND) ||
            // 1.4.1 里 BIA（无后缀）已标记 deprecated，但旧机型可能只报它。
            // 它用的是同一个 BiaSet，所以可以顶替。
            types.contains(HealthTrackerType.BIA)
        _state.value = _state.value.copy(supported = support)
        return support
    }

    /**
     * 开始一次体成分测量。
     *
     * [profile] 必须齐备且合法；缺失时**不发起测量**，直接把原因写进 [State.error]。
     */
    fun startMeasurement(profile: BiaProfileDto?) {
        if (profile == null || !profile.isValid()) {
            _state.value = _state.value.copy(
                measuring = false,
                error = ResText.core("s_768c0cef"),
            )
            return
        }
        pendingProfile = profile
        startedAtMs = System.currentTimeMillis()
        rejected = 0
        _state.value = _state.value.copy(
            measuring = true, progress = 0f, result = null,
            statusText = ResText.core("s_6ac70683"), error = null,
        )
        val ok = client.connect(connectionOwner)
        if (!ok) {
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_933c0c84"))
        }
    }

    /**
     * 停止测量并断开共享服务。
     *
     * 必须断开：BIA 的 on-demand tracker 不停会残留会话，与血氧同理
     * （反复断连重连会让 ECG 会话进废状态，所以这里是"用完即断"而不是长连）。
     */
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
        val type = when {
            types.contains(HealthTrackerType.BIA_ON_DEMAND) -> HealthTrackerType.BIA_ON_DEMAND
            types.contains(HealthTrackerType.BIA) -> HealthTrackerType.BIA
            else -> {
                _state.value = _state.value.copy(
                    measuring = false,
                    supported = false,
                    error = ResText.core("s_0b2c570b", types.joinToString { it.name }),
                )
                return
            }
        }

        // 资料先过一遍取值域，避免把不合法值送进 SDK 换来一个状态码 18（那是个很难自查的失败）
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
            client.service.getHealthTracker(type, userProfile)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                measuring = false,
                error = ResText.core("s_db925254", e.javaClass.simpleName, e.message),
            )
            return
        }
        tracker = t
        runCatching { t.setEventListener(trackerListener) }.onFailure { e ->
            _state.value = _state.value.copy(measuring = false, error = ResText.core("s_84c4ef03", e.message))
            return
        }
        _state.value = _state.value.copy(
            measuring = true,
            statusText = ResText.core("s_b59e7dca"),
        )
    }

    /**
     * 把 SDK 的进度值归一化到 0~1。
     *
     * 官方文档只写了"Measurement progress."，**没有写量纲**。实测两种写法都可能出现
     * （0~1 的小数、0~100 的百分数），所以这里按值域判断而不是假定一种：
     * 直接 `progress / 100` 会把小数写法压成 0，而直接 `progress` 会把百分数写法顶出 1。
     * 归一化只影响进度环的观感，不会污染任何结果值。
     */
    private fun normalizeProgress(p: Float?): Float {
        if (p == null || p.isNaN()) return 0f
        val v = if (p > 1f + 1e-3f) p / 100f else p
        return v.coerceIn(0f, 1f)
    }

    /** 本次测量开始时刻：用来挡住"1 秒就返回"的早期垃圾值。 */
    @Volatile private var startedAtMs = 0L
    /** 被判定为不可信的次数（接触不良时会连续出现）。 */
    @Volatile private var rejected = 0

    /**
     * 结果是否**可信**（2026-10-22 新增）。
     *
     * ⚠ 起因：用户反馈"放上去一秒就出结果，而且和三星健康差很多"。
     *   原始记录里同一分钟三次阻抗是 4446 / 1202 / 3271 Ω，还有一次 429 Ω ——
     *   成人腕部 BIA 的阻抗量级就在几百欧，四千多欧只能是**电极接触没建立起来**时的读数。
     *   而 SDK 的 `status == 0` 只代表"这一帧算完了"，**不代表接触良好**；
     *   我们原来见到第一帧 status 0 就直接当结果 ⇒ 把垃圾值落库了。
     */
    private fun isPlausible(r: BiaResult, elapsedMs: Long): Boolean {
        // 太早返回的一律不信（正常一次测量至少要几秒稳定过程）
        if (elapsedMs < MIN_MEASURE_MS) return false
        val z = r.impedanceMagnitude
        val fat = r.bodyFatRatio
        val water = r.totalBodyWater
        // 阻抗量级：150–1500 Ω（超出基本都是没握稳/干皮肤/只贴了一根手指）
        if (z != null && (z < 150f || z > 1500f)) return false
        // 体脂率：3%–55%（0 或 60+ 都是模型外推失控）
        if (fat != null && (fat < 3f || fat > 55f)) return false
        // 体水：20%–70%
        if (water != null && (water < 20f || water > 70f)) return false
        return true
    }

    private fun hasAnyValue(r: BiaResult): Boolean =
        r.bodyFatRatio != null || r.bodyFatMass != null || r.totalBodyWater != null ||
            r.skeletalMuscleRatio != null || r.skeletalMuscleMass != null ||
            r.basalMetabolicRate != null || r.fatFreeRatio != null || r.fatFreeMass != null ||
            r.impedanceMagnitude != null || r.impedanceDegree != null
}
