package com.gemini.healthbridge.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.gemini.healthbridge.core.Activity
import com.gemini.healthbridge.core.ResText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 三轴加速度采集（模块 #7）。**用户看不到入口，也不需要点任何东西** ——
 * App 在前台时就在采，每分钟落一条"活动片段"。
 *
 * ## 为什么走平台 SensorManager，而不是三星 Health Sensor SDK 的 `ACCELEROMETER_CONTINUOUS`
 * 模块总表把这一项列在三星 SDK 下（"SDK 输出原始数据"），但实测口径下平台传感器是**更正确**的选择：
 *
 *  1. **互不干扰**：本项目的 `SamsungSensorClient` 是**单 owner** 设计，同一时刻只允许一个测量占用
 *     （`MainActivity.stopAllMeasurements` 就是为此存在的）。加速度若也挤进这条连接，
 *     要么抢 owner、要么被迫和测量串行 —— 而它恰恰要在**测量期间**采集才有意义。
 *     `SensorManager` 是独立通道，与三星 SDK 完全无关，怎么跑都不冲突。
 *  2. **单位确定**：平台 `TYPE_ACCELEROMETER` 明确给 m/s²；三星的
 *     `ValueKey.AccelerometerSet` 给的是 `Int`，**官方文档没有写单位**。
 *     这个项目在"凭印象写单位"上出过事故（血氧 0–100 被当成 0–1），不赌。
 *  3. **零权限**：读原始加速度不需要任何权限（`ACTIVITY_RECOGNITION` 只对步数/活动识别 API 必要），
 *     不需要 `BODY_SENSORS`、不需要三星的额外健康数据权限、不需要开发者模式。
 *
 * 采集到的数据经 `core/Activity.kt`（纯逻辑）归一化后落库，落的是**统计量 + 一条降采样包络**，
 * 不是逐样本原始流（理由见 `ActivityEntity` 的注释）。
 *
 * ## 采集窗口
 * 固定 60 秒一段。窗口化而不是"整段前台时间"，是为了让片段足够短、能代表某一时刻的状态 ——
 * HRV/呼吸率要问的是"**这次测量时**人在动还是在静"，一段含走带坐的两小时数据回答不了。
 * 片段自带 `[startedAt, startedAt+durationMs)`，任何一条测量记录都能按时间戳找到覆盖它的片段。
 *
 * ## 后台边界（诚实说明）
 * Wear OS 对后台进程限制很严，息屏/退到后台后本采样器**会停**（由 Activity 的 onPause/onResume 驱动）。
 * 所以它采集的是"打开 App 期间"的活动，而不是 24 小时连续记录 —— 这一点在手机端的活动卡片上
 * 也明确写给用户看，不用"连续监测"的说法糊过去。
 */
class AccelSampler(context: Context) : SensorEventListener {

    /** 一段采集的成品：统计量 + 画图用包络。由调用方落库并同步。 */
    data class Segment(
        val startedAt: Long,
        val durationMs: Long,
        val stats: Activity.Stats,
        val envelope: String,
    )

    data class State(
        /** false = 本机没有加速度传感器（几乎不可能，但要有确定结论而不是空等）。 */
        val supported: Boolean = true,
        val sampling: Boolean = false,
        /** 当前窗口已累计的样本数。 */
        val sampleCount: Int = 0,
        /** 当前窗口已累计的时长（毫秒）。 */
        val elapsedMs: Long = 0,
        /** 最近一个已完成片段的档位；采样中时是当前窗口的实时估计。 */
        val level: String = Activity.Level.UNKNOWN,
        /** 最近一个已完成片段的模长标准差（g），界面用来解释"为什么判成这一档"。 */
        val lastStdG: Float? = null,
        /** 已产出的片段数，用于确认采集真的在跑。 */
        val emitted: Int = 0,
        val error: ResText? = null,
    )

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _state = MutableStateFlow(State(supported = sensor != null))
    val state: StateFlow<State> = _state

    /**
     * 每产出一段就回调一次。
     *
     * 用回调而不是"stop() 返回一个 Segment"，是因为窗口是自动循环的：一次前台会话里
     * 会产出很多段，调用方不该关心"什么时候 flush"，只该关心"有一段好了，去落库"。
     */
    var onSegment: ((Segment) -> Unit)? = null

    private val buffer = ArrayList<Activity.Sample>(WINDOW_SAMPLES)
    private var windowStartAt = 0L
    private var lastSampleAt = 0L
    private var lastLevelAt = 0L

    fun start() {
        if (_state.value.sampling) return
        val sm = sensorManager
        val s = sensor
        if (sm == null || s == null) {
            _state.value = _state.value.copy(
                supported = false, sampling = false,
                error = ResText.core("s_ccf663a0"),
            )
            return
        }
        buffer.clear()
        windowStartAt = 0L
        lastSampleAt = 0L
        lastLevelAt = 0L
        // SENSOR_DELAY_GAME ≈ 50Hz：活动分级只需要到这个量级，
        // SENSOR_DELAY_FASTEST 会白烧电，UI 档又低到看不出走动与静息的区别。
        val ok = runCatching {
            sm.registerListener(this, s, SensorManager.SENSOR_DELAY_GAME)
        }.getOrDefault(false)
        _state.value = _state.value.copy(
            sampling = ok, sampleCount = 0, elapsedMs = 0,
            error = if (ok) null else ResText.core("s_f047b24e"),
        )
    }

    /**
     * 停止采集并把最后一段不满 60 秒的数据也结算掉（前提是够 [Activity.MIN_DURATION_MS]）。
     *
     * 用 `lastSampleAt` 而不是"现在"算时长：从最后一个样本到 onPause 之间可能隔了很久
     * （比如用户看着结果页发呆），把这段空白算进去会把时长虚增、把标准差拉低。
     */
    fun stop() {
        if (!_state.value.sampling) return
        runCatching { sensorManager?.unregisterListener(this) }
        val end = if (lastSampleAt > 0L) lastSampleAt else System.currentTimeMillis()
        if (buffer.isNotEmpty()) flush(end)
        _state.value = _state.value.copy(sampling = false, sampleCount = 0, elapsedMs = 0)
    }

    override fun onSensorChanged(event: SensorEvent) {
        // 传感器回调在注册线程（主线程）上执行：任何未捕获异常都会直接杀进程
        runCatching {
            if (!_state.value.sampling) return
            val v = event.values
            if (v.size < 3) return
            val now = System.currentTimeMillis()
            if (buffer.isEmpty()) windowStartAt = now
            buffer.add(Activity.Sample(v[0], v[1], v[2]))
            lastSampleAt = now

            if (now - windowStartAt >= WINDOW_MS || buffer.size >= MAX_SAMPLES) {
                flush(now)
                return
            }
            // 实时档位：每秒算一次。逐样本算没有意义（界面上根本读不过来），
            // 但完全不显示又会让用户以为采集没跑。一次 sort ≤6000 个 Float，代价可忽略。
            if (now - lastLevelAt >= LEVEL_REFRESH_MS) {
                lastLevelAt = now
                val stats = Activity.analyze(ArrayList(buffer), now - windowStartAt)
                _state.value = _state.value.copy(
                    sampleCount = buffer.size,
                    elapsedMs = now - windowStartAt,
                    level = stats?.level ?: Activity.Level.UNKNOWN,
                    lastStdG = stats?.stdG,
                )
            } else {
                _state.value = _state.value.copy(
                    sampleCount = buffer.size,
                    elapsedMs = now - windowStartAt,
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    /**
     * 结算当前窗口并交给调用方落库。
     *
     * 数据不够（`Activity.analyze` 返回 null）时**什么也不产出**：
     * 一段 3 秒的采集恰好静止的概率很高，它证明不了"用户处于静息"，
     * 而 HRV 门控会拿这个结论决定要不要出数。宁可这段丢掉。
     */
    private fun flush(now: Long) {
        val start = windowStartAt
        val dur = (now - start).coerceAtLeast(0L)
        if (buffer.isEmpty()) {
            windowStartAt = 0L
            return
        }
        val samples = ArrayList(buffer)
        buffer.clear()
        windowStartAt = 0L
        lastLevelAt = 0L

        val stats = Activity.analyze(samples, dur)
        if (stats == null) {
            _state.value = _state.value.copy(sampleCount = 0, elapsedMs = 0)
            return
        }
        // 注意这里是第二遍归一化（analyze 内部已经算过一次）。重复一次换来的好处是
        // 包络与统计量走的是同一个公开 API，不会出现"统计按 A 口径、曲线按 B 口径"的漂移。
        val env = Activity.encodeEnvelope(
            Activity.envelope(Activity.normalize(samples).first)
        )
        val seg = Segment(startedAt = start, durationMs = dur, stats = stats, envelope = env)
        _state.value = _state.value.copy(
            sampleCount = 0, elapsedMs = 0,
            level = stats.level, lastStdG = stats.stdG,
            emitted = _state.value.emitted + 1, error = null,
        )
        runCatching { onSegment?.invoke(seg) }
            .onFailure { _state.value = _state.value.copy(error = ResText.core("s_f716d88f", it.message)) }
    }

    private companion object {
        /** 一个活动窗口的长度。 */
        const val WINDOW_MS = 60_000L

        /** 实时档位刷新间隔。 */
        const val LEVEL_REFRESH_MS = 1_000L

        /** 缓冲区硬上限（≈ 200 秒 @50Hz）。超了强制结算，避免长时间前台时内存无限增长。 */
        const val MAX_SAMPLES = 10_000

        /** 缓冲区初始容量估算（60 秒 @50Hz）。 */
        const val WINDOW_SAMPLES = 3_000
    }
}
