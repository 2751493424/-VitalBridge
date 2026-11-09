package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 活动状态（三轴加速度）的纯逻辑：统计量、动静分级、包络降采样、格式化。
 *
 * ## 这个模块存在的意义
 * 加速度本身不是给人看的指标，它是**其它指标的上下文**：
 *  - HRV 只有在**静息**时才有意义（走动中的 RR 间期反映的是步频，不是自主神经）；
 *  - 呼吸率、压力/疲劳评分都要求知道"这段时间人在动还是在静"；
 *  - 心率读数偏高的第一解释永远是"刚才在动"，不理解活动状态就会把运动心率当心动过速。
 *
 * 所以这里刻意**只产出"运动/静息"这一层结论**，不试图做步数计、姿态识别或睡眠分期。
 *
 * ## 为什么做成无 Android 依赖的纯 Kotlin
 * 与 `BodyComposition.kt` / `Spo2.kt` / `Temperature.kt` 同一条理由：
 * 能直接进离线 JVM 校验台架跑断言。这个项目在"凭印象写阈值/单位"
 * 上已经出过事故（血氧 0–100 被当成 0–1），而分级阈值恰恰是最容易被随手写错的东西。
 *
 * ## 单位：一切都归一到 g，且**不假定输入单位**
 * [normalize] 用**本段数据自身的模长中位数**当 1 g 的基准，而不是写死 9.80665。
 * 原因有两个：
 *  1. 平台 `SensorManager.TYPE_ACCELEROMETER` 给的是 m/s²，而三星 Sensor SDK 的
 *     `ValueKey.AccelerometerSet` 给的是 `Int` 原始计数（单位文档没写）。归一化之后
 *     两者都能走同一套代码，不需要为"单位到底是什么"下注。
 *  2. 静止时加速度模长**就是 1 g**，与手腕朝向无关（重力一直被量到），
 *     所以"本段模长中位数"是一个自校准的 1 g 基准，连传感器的增益误差一起消掉。
 *     即便整段都在运动，重力仍然占主导，中位数照样稳在 1 g 附近。
 */
object Activity {

    /** 标准重力加速度（m/s²）。只在需要把平台传感器读数换成 g 时当参考，分级不依赖它。 */
    const val G = 9.80665f

    // ═══════════════ 分级阈值（以归一化后 |a| 的标准差为准，单位 g）═══════════════

    /**
     * 分级用的是 [Stats.stdG]（模长的标准差），量纲与文献里的 ENMO 近似一致
     * （静止时模长恒为 1 g，运动带来的线性加速度直接体现为标准差）。
     *
     * 阈值取常用的活动强度切点量级：静止 <0.025 g、轻度 <0.1 g、中度 <0.3 g。
     * **这是工程经验阈值，不是经过临床验证的分界**，界面必须按"档位"而非"医学结论"来表述。
     */
    const val REST_STD_MAX = 0.025f
    const val LIGHT_STD_MAX = 0.100f
    const val MODERATE_STD_MAX = 0.300f

    /** 判定"这一瞬间没在动"的逐样本容差：||a| - 1g| 小于它算静止。 */
    const val STILL_TOLERANCE_G = 0.05f

    /** 一段采集至少要有的样本数与时长 —— 不够就不给结论（宁可不显示，也不要给错档位）。 */
    const val MIN_SAMPLES = 50
    const val MIN_DURATION_MS = 8_000L

    /** 包络默认降采样到多少个桶。120 个点足够画出一条能看出"动/静交替"的曲线。 */
    const val DEFAULT_BUCKETS = 120

    // ═══════════════ 档位 ═══════════════

    /**
     * 活动档位。用字符串常量而不是 enum：
     * 它要落库、要进 JSON、要跨版本读回，字符串比枚举序号的兼容性好得多
     * （睡眠阶段 `SleepStageKind` 同一条理由）。
     */
    object Level {
        const val REST = "rest"
        const val LIGHT = "light"
        const val MODERATE = "moderate"
        const val VIGOROUS = "vigorous"

        /** 数据不足/无法判定。**必须与"静息"分开**：把没测到当成静息，会污染 HRV 门控。 */
        const val UNKNOWN = "unknown"

        val ALL = listOf(REST, LIGHT, MODERATE, VIGOROUS)
    }

    fun label(level: String?): String = when (level) {
        Level.REST -> Text.of("s_004dd2fa")
        Level.LIGHT -> Text.of("s_52d5dc28")
        Level.MODERATE -> Text.of("s_92addbee")
        Level.VIGOROUS -> Text.of("s_2869663e")
        else -> Text.of("s_c34c0523")
    }

    /** 档位序号（0=静息 … 3=剧烈），未知返回 -1。用于排序与"至少达到某档"的判断。 */
    fun order(level: String?): Int = Level.ALL.indexOf(level)

    /** 由模长标准差定档。null/NaN 一律 [Level.UNKNOWN]。 */
    fun levelOf(stdG: Float?): String {
        if (stdG == null || stdG.isNaN() || stdG < 0f) return Level.UNKNOWN
        return when {
            stdG < REST_STD_MAX -> Level.REST
            stdG < LIGHT_STD_MAX -> Level.LIGHT
            stdG < MODERATE_STD_MAX -> Level.MODERATE
            else -> Level.VIGOROUS
        }
    }

    // ═══════════════ 采样与归一化 ═══════════════

    /** 一个三轴样本（单位随来源，见 [normalize]）。 */
    data class Sample(val x: Float, val y: Float, val z: Float)

    /** 模长（与单位同源）。 */
    fun magnitude(s: Sample): Float = sqrt(s.x * s.x + s.y * s.y + s.z * s.z)

    /**
     * 模长中位数，作为"1 g 对应多少输入单位"的基准。空集合返回 1（调用方应先判空）。
     *
     * **必须取真正的（偶数长度取中间两个的平均）中位数**，不能取 `sorted[size/2]`：
     * 中度以上运动时模长会在 1g 上下大幅摆动，`sorted[size/2]` 恰好落在上半段，
     * 于是基准被抬高成 (1+a)g，归一化后整段被"按比例缩小"，**方差被系统性低估**，
     * 一个明明在跑步的片段会被算成轻度活动。取正中间两个的平均则对对称摆动免疫。
     *
     * 前提是线性加速度不会把模长推到 0 以下（|Δ| < 1g）—— 除跌落/撞击外都成立，
     * 而那种极端情况本来也不属于我们要区分的"动/静"。
     */
    fun referenceUnit(samples: List<Sample>): Float {
        if (samples.isEmpty()) return 1f
        val mags = samples.map { magnitude(it) }.sorted()
        val mid = if (mags.size % 2 == 1) {
            mags[mags.size / 2]
        } else {
            (mags[mags.size / 2 - 1] + mags[mags.size / 2]) / 2f
        }
        // 基准只用来做除法，必须为正；中位数为 0 说明整段是零（传感器没出数），按 1 处理
        return if (mid > 0f && !mid.isNaN()) mid else 1f
    }

    /**
     * 把一段样本归一到 g。
     *
     * 返回的 `Pair.first` 是逐样本模长（g），`second` 是平均重力向量（g，用于姿态）。
     * 单独返回平均向量是因为**三轴各自的均值**就是重力方向（交流分量互相抵消），
     * 后面做睡眠姿态/佩戴方向分析时要用，而那时再去遍历原始样本已经不现实了。
     */
    fun normalize(samples: List<Sample>): Pair<List<Float>, Triple<Float, Float, Float>> {
        if (samples.isEmpty()) return emptyList<Float>() to Triple(0f, 0f, 0f)
        val ref = referenceUnit(samples)
        val mags = ArrayList<Float>(samples.size)
        var sx = 0.0
        var sy = 0.0
        var sz = 0.0
        for (s in samples) {
            mags.add(magnitude(s) / ref)
            sx += s.x
            sy += s.y
            sz += s.z
        }
        val n = samples.size.toFloat()
        return mags to Triple((sx / n / ref).toFloat(), (sy / n / ref).toFloat(), (sz / n / ref).toFloat())
    }

    // ═══════════════ 统计 ═══════════════

    /**
     * 一段采集的统计结果。
     *
     * @param meanG 平均模长（g）。静止时应≈1；明显偏离 1 说明增益标定异常或被包络检波影响。
     * @param stdG 模长标准差（g），**分级主判据**。
     * @param p95G 第 95 百分位模长（g）。比峰值稳，用来描述"动作幅度"。
     * @param stillRatio 满足 ||a|-1g| < [STILL_TOLERANCE_G] 的样本占比（0~1）。
     * @param peakJerkG 相邻样本模长差的最大值（g），描述冲击/抖动强度（拿杯子、敲桌面这类）。
     * @param meanXG 平均重力向量 X 分量（g），姿态用。
     */
    data class Stats(
        val sampleCount: Int,
        val durationMs: Long,
        val meanG: Float,
        val stdG: Float,
        val p95G: Float,
        val stillRatio: Float,
        val peakJerkG: Float,
        val meanXG: Float,
        val meanYG: Float,
        val meanZG: Float,
        val level: String,
    )

    /**
     * 分析一段采集。样本数或时长不够时返回 null —— **不返回一个"勉强算出来"的档位**。
     *
     * 为什么坚持返回 null：一段 2 秒的采集恰好静止的概率很高，但它不能证明"用户处于静息"，
     * 而 HRV 门控会拿这个结论去决定要不要出数。宁可界面显示"数据不足"。
     */
    fun analyze(samples: List<Sample>, durationMs: Long): Stats? {
        if (samples.size < MIN_SAMPLES || durationMs < MIN_DURATION_MS) return null
        val (mags, meanVec) = normalize(samples)
        if (mags.isEmpty()) return null

        var sum = 0.0
        var still = 0
        for (m in mags) {
            sum += m
            if (abs(m - 1f) < STILL_TOLERANCE_G) still++
        }
        val mean = (sum / mags.size).toFloat()

        var sq = 0.0
        for (m in mags) {
            val d = (m - mean).toDouble()
            sq += d * d
        }
        val std = sqrt(sq / mags.size).toFloat()

        var peakJerk = 0f
        for (i in 1 until mags.size) {
            val d = abs(mags[i] - mags[i - 1])
            if (d > peakJerk) peakJerk = d
        }

        val sorted = mags.sorted()
        val p95 = sorted[((sorted.size - 1) * 95 / 100).coerceIn(0, sorted.size - 1)]

        return Stats(
            sampleCount = samples.size,
            durationMs = durationMs,
            meanG = mean,
            stdG = std,
            p95G = p95,
            stillRatio = still.toFloat() / mags.size,
            peakJerkG = peakJerk,
            meanXG = meanVec.first,
            meanYG = meanVec.second,
            meanZG = meanVec.third,
            level = levelOf(std),
        )
    }

    /** 是否可判定为"处于静息"：档位是静息**且**绝大多数样本都没在动。 */
    fun isResting(stats: Stats?): Boolean =
        stats != null && stats.level == Level.REST && stats.stillRatio >= 0.80f

    // ═══════════════ 包络（画图用） ═══════════════

    /**
     * 把逐样本模长压成 [buckets] 个桶，每桶取**模长相对 1 g 的最大偏离**（g，≥0）。
     *
     * 取"最大偏离"而不是"均值"：均值会把一个瞬时冲击（拿杯子）平摊成看不出来的一小段，
     * 而画这条曲线正是为了让用户看见"中间动了一下"。静止段的桶值自然趋于 0，
     * 所以曲线的基线就是 0，不需要再额外找基准。
     */
    fun envelope(mags: List<Float>, buckets: Int = DEFAULT_BUCKETS): List<Float> {
        if (mags.isEmpty() || buckets <= 0) return emptyList()
        val n = buckets.coerceAtMost(mags.size)
        val out = ArrayList<Float>(n)
        for (b in 0 until n) {
            val from = (b.toLong() * mags.size / n).toInt()
            val to = (((b + 1).toLong() * mags.size / n).toInt()).coerceAtLeast(from + 1)
            var peak = 0f
            for (i in from until to.coerceAtMost(mags.size)) {
                val d = abs(mags[i] - 1f)
                if (d > peak) peak = d
            }
            out.add(peak)
        }
        return out
    }

    /** 包络 -> CSV（三位小数，g）。空集合返回空串，**不返回 null**，调用方少一层判空。 */
    fun encodeEnvelope(env: List<Float>): String =
        env.joinToString(",") { String.format(java.util.Locale.US, "%.3f", it) }

    /** CSV -> 包络。任何非法值直接丢弃（截断的尾巴、空串都算）。 */
    fun decodeEnvelope(csv: String?): List<Float> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(',').mapNotNull { it.trim().toFloatOrNull() }
    }

    // ═══════════════ 汇总（手机端展示） ═══════════════

    /**
     * 把若干 (档位, 时长) 汇总成每档累计时长（毫秒），键为 [Level] 取值。
     * 未知档位**不进统计**（它代表"没测到"，混进去会把静息时长算多）。
     */
    fun totalsByLevel(segments: List<Pair<String, Long>>): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for ((lvl, ms) in segments) {
            if (lvl == Level.UNKNOWN || ms <= 0L) continue
            out[lvl] = (out[lvl] ?: 0L) + ms
        }
        return out
    }

    /** 时长显示：不足 1 分钟按秒，超过按 "M 分 S 秒"，整分只写分。 */
    fun formatDuration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val m = total / 60
        val s = total % 60
        return when {
            m <= 0L -> Text.fmt("s_8887c3b1", s)
            s == 0L -> Text.fmt("s_888609af", m)
            else -> Text.fmt("s_b4d0624e", m, s)
        }
    }

    /** 标准差显示（分级依据），保留三位小数 —— 阈值本身就在 0.025 这个量级。 */
    fun formatStd(g: Float?): String =
        g?.let { String.format(java.util.Locale.US, "%.3f", it) } ?: "--"

    /** 比重（0~1）显示成百分比整数。 */
    fun formatPct(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.0f%%", it * 100f) } ?: "--%"

    /**
     * 包络曲线纵轴建议上界（g）。至少 0.05，避免静止段把坐标轴压成一条直线看不出刻度；
     * 按 0.05 的整数倍量化，保证刻度值是"好看的数"。
     */
    fun envelopeBound(env: List<Float>): Float {
        if (env.isEmpty()) return 0.05f
        val peak = env.max()
        if (peak <= 0f || peak.isNaN()) return 0.05f
        val step = 0.05f
        val raw = kotlin.math.ceil((peak * 1.10f) / step) * step   // 留 10% 头部余量
        return raw.coerceAtLeast(step)
    }
}

/**
 * 活动记录的来源标记，两端共用。
 *
 * 目前只有手表一种来源 —— 三星健康不提供第三方可读的逐段活动记录
 * （只有步数与运动会话），所以这里不为"三星来源"预留假值。
 */
object ActivitySource {
    const val WATCH = "watch_accel"
}
