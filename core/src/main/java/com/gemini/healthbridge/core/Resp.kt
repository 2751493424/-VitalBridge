package com.gemini.healthbridge.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 呼吸率估算（模块 #11）：从**我们自己有足够采样率的信号**里提呼吸频率。
 *
 * ## 为什么不是"从三星健康的睡眠数据提"（这一点已经核实过，勿再试）
 * 三星健康 Data SDK 1.1.0 开放的都是**聚合值**，没有任何一路的采样率高到能看见呼吸：
 *   · 心率 `SERIES_DATA`：每点是 `heartRate/min/max` 的**分箱**，≥1 分钟一个；
 *   · 血氧 `SERIES_DATA`：**分钟级**；
 *   · 皮肤温度：夜间慢变序列；睡眠分期：离散段；呼吸率本身**未开放**。
 * 呼吸是 0.2~0.4 Hz，至少要 1 Hz 连续采样。三星在服务端就把信号压成了分箱值，
 * **物理上提不出来** —— 这与算法强弱无关。
 * → 所以 #11 一分为二：`Odi.kt`（拿三星分钟级血氧做氧减指数，睡眠场景）与本文件
 *   （拿我们自己的原始信号做呼吸频率）。
 *
 * ## 本模块的两个独立数据源
 * 1. **PPG 原始光信号**（#8，25 Hz，血氧测量时采集）—— 呼吸会以三种方式调制脉搏波：
 *      · **AM 幅度调制**：胸内压变化改变每搏输出量 → 脉搏幅度随呼吸起伏；
 *      · **FM 频率调制**：呼吸性心律不齐（RSA）→ 心搏间隔随呼吸起伏；
 *      · **BW 基线漂移**：胸腔运动改变静脉回流与传感器耦合 → 波形基线随呼吸起伏。
 *    三路**互相独立**（三种不同的生理机制），可以交叉验证，这是本模块可信度的主要来源。
 * 2. **心电的逐拍 RR 间期**（#9，~1 Hz，心电测量 30 秒）—— 同一套 RSA 机制，
 *    信号的产生路径完全不同（电 vs 光），是真正的第二数据源。
 *
 * ## 为什么用"先检搏、再取每搏序列"，而不是直接对 PPG 做频谱
 * 直观做法是"把 PPG 低通到 0.6 Hz 以下再找峰"。但腕部 PPG 上这个做法有两个硬伤：
 *   ① **运动伪迹的能量在低频**：手一动，基线漂移的能量远大于呼吸调制，低通等于把伪迹留下、
 *      把信息淹掉；② **脉搏基频（1~2 Hz）附近的泄漏**：滤波器的阻带衰减不够时，
 *      幅度比呼吸大两个数量级的脉搏会漏进呼吸频段。
 * 现在的做法先把**每个心搏压缩成一个数**（幅度 / 间期 / 基线），
 * 脉搏频率成分在压缩时就**被构造性地消除了**（一个心搏一个点，不存在 1~2 Hz 的成分），
 * 于是后面只需要对付运动伪迹这一件事。
 *
 * ## 周期估计：自相关 + 抛物线插值（与脉率同一套，不另起炉灶）
 * 呼吸信号是准周期的，自相关在一个完整周期上积分，噪声与个别漏拍被平均掉；
 * 周期是相关峰的**位置**而不是"数出来的个数"，天然免疫多检/漏检 ——
 * 这与 `Ppg.fundamentalLag` 当初放弃"门限+峰计数"的理由完全一样。
 * 这里直接复用它（见 `Ppg.fundamentalLagWithCorr`），只是把 lag 范围换成呼吸频段。
 *
 * ## 融合规则（三路信号到底听谁的）
 * 按自相关值排序，取最可信的那一路；其他路若与它**落在 [AGREE_BRPM] 次/分以内**视为互相印证，
 * 融合值取它们的**相关系数加权平均**。一路都不印证的场合：
 *   · 最强一路的相关 ≥ [STRONG_CORR] → 只报它，置信度降级为"中/低"并说明"只有一路支持"；
 *   · 否则**不给数** —— 三路互相矛盾说明这段信号本来就不含可信的呼吸成分，
 *     硬给一个数就是编的。
 *
 * ## 门控（任一不满足就不给数）
 * 时长 < [MIN_DURATION_MS]、完整度 < [MIN_QUALITY]、覆盖不足 [MIN_CYCLES] 次完整呼吸、
 * PPG 里检不出脉搏、结果不在 [RR_MIN]~[RR_MAX] —— 都返回 [Result.NotApplicable]。
 * **"记录太短"是最常遇到的一种**，文案必须说清"还差多少"，而不是笼统的"数据不足"。
 *
 * ## 刻意不做的事
 * 1. **不给"呼吸是否正常"的临床判断**。成人静息常见 12~20 次/分是**参考区间**，
 *    不是本算法的诊断结论：本实现没有与任何金标准（肺功能/胸带/PSG）做过比对。
 * 2. **不融合 PPG 与心电两路**：它们是**不同次测量**（血氧测量 vs 心电测量），
 *    中间隔着几分钟到几小时，生理状态未必相同。硬把它们平均等于假装是同一次呼吸。
 *    界面上应当**分开显示**，各标各的来源与时刻。
 * 3. **不延长采集**。本模块只用既有记录，与 #8 的"只在血氧测量期间采集"保持一致。
 */
object Resp {

    // ═══════════════ 门限 ═══════════════

    /**
     * 最短可用时长（毫秒）。
     *
     * 呼吸 0.2~0.4 Hz，一个周期 2.5~5 秒。要"看见"周期性至少需要 [MIN_CYCLES] 个周期，
     * 再留一档余量 → 30 秒。低于它即使算出一个数，也只是噪声碰巧对齐。
     */
    const val MIN_DURATION_MS = 30_000L

    /** 至少要有几次完整呼吸。低于它，自相关峰可能是巧合。 */
    const val MIN_CYCLES = 4

    /** 置信度达到"高"所需的呼吸周期数（更多周期 = 更可信）。 */
    const val GOOD_CYCLES = 6

    /** 生理可能的呼吸率范围（次/分）。超出即判为"没算对"，不给数。 */
    const val RR_MIN = 6
    const val RR_MAX = 40

    /** 完整度下界（与 `Ppg.MIN_QUALITY_FOR_HR` 同一条理由：缺样会让时间换算整体偏移）。 */
    const val MIN_QUALITY = 0.80f

    /** 呼吸频带的下界（Hz）：慢于它算基线漂移，不是呼吸。 */
    const val BAND_LOW_HZ = 0.10

    /** 呼吸频带的上界（Hz）：快于它在这个采样率上分不清是呼吸还是伪迹。 */
    const val BAND_HIGH_HZ = 0.60

    /**
     * 重采样网格（Hz）。
     *
     * 每搏序列是**非均匀**的（心搏间隔本就不等长），自相关要在均匀网格上做才有意义。
     * 取 4 Hz：呼吸上界 0.6 Hz 的 Nyquist 是 1.2 Hz，4 Hz 留了三倍余量；
     * 再高只会让插值出来的点更多、把"假想的细节"喂给自相关。
     */
    const val GRID_HZ = 4.0

    /** 一路呼吸信号要被采纳所需的最小自相关。低于它说明这一路基本是噪声。 */
    const val MIN_CORR = 0.40

    /** 只有一路支持时，要强到什么程度才敢报（否则宁可不给）。 */
    const val STRONG_CORR = 0.60

    /** 几路信号相差多少（次/分）以内算"互相印证"。 */
    const val AGREE_BRPM = 2.5

    /** 心电路径至少要有多少拍。30 秒、60 次/分约 30 拍。 */
    const val MIN_BEATS = 25

    // ═══════════════ 来源与置信 ═══════════════

    /** 分量/来源令牌。用字符串（与 `SleepStageKind` 同一条理由：要落库、要跨版本）。 */
    object Source {
        /** PPG 幅度调制（每搏幅度随呼吸起伏）。 */
        const val PPG_AM = "ppg_am"

        /** PPG 频率调制（呼吸性心律不齐导致的每搏间期起伏）。 */
        const val PPG_FM = "ppg_fm"

        /** PPG 基线漂移（每搏舒张期基线随呼吸起伏）。 */
        const val PPG_BW = "ppg_bw"

        /** 心电逐拍 RR 间期（独立于光信号的一路）。 */
        const val ECG_RR = "ecg_rr"

        fun label(token: String): String = when (token) {
            PPG_AM -> Text.of("s_64b3e2ef")
            PPG_FM -> Text.of("s_ef6e6608")
            PPG_BW -> Text.of("s_b02d6af5")
            ECG_RR -> Text.of("s_e947c4bb")
            else -> token
        }
    }

    object Tier {
        const val HIGH = "high"
        const val MEDIUM = "medium"
        const val LOW = "low"

        fun label(token: String): String = when (token) {
            HIGH -> Text.of("s_00009ad8")
            MEDIUM -> Text.of("s_00004e2d")
            else -> Text.of("s_00004f4e")
        }
    }

    // ═══════════════ 参考区间 ═══════════════

    /** 成人静息呼吸率的常见下界（次/分）。 */
    const val TYPICAL_MIN = 12

    /** 成人静息呼吸率的常见上界（次/分）。 */
    const val TYPICAL_MAX = 20

    object Band {
        const val BELOW = "below"
        const val TYPICAL = "typical"
        const val ABOVE = "above"
    }

    /**
     * 参考区间归属。
     *
     * **这是流行病学上的常见范围，不是本算法的诊断结论** —— 本实现没有与任何金标准比对过。
     * 越界只说明"落在常见静息范围之外"，可能只是刚活动完、体位、说话、情绪，
     * 也可能只是这一段信号没采好。界面必须同时给出 [DISCLAIMER]。
     */
    fun bandOf(brpm: Double): String = when {
        brpm.isNaN() -> Band.TYPICAL
        brpm < TYPICAL_MIN -> Band.BELOW
        brpm <= TYPICAL_MAX -> Band.TYPICAL
        else -> Band.ABOVE
    }

    fun bandLabel(token: String): String = when (token) {
        Band.BELOW -> Text.of("s_7a163a38")
        Band.TYPICAL -> Text.of("s_0c43c180")
        else -> Text.of("s_a632f926")
    }

    fun bandColorArgb(token: String): Long = when (token) {
        Band.TYPICAL -> 0xFF2E7D32L
        Band.BELOW -> 0xFF1565C0L
        else -> 0xFFEF6C00L
    }

    // ═══════════════ 结果 ═══════════════

    sealed interface Result {
        /**
         * @param brpm 呼吸率（次/分，一位小数精度）。
         * @param sources 参与融合的来源（按可信度降序）；只有一路时长度为 1。
         * @param corr 融合所用的自相关（越高越可信）。
         * @param cycles 这段记录覆盖了多少次完整呼吸 —— **它就是这个结果的可信度来源**。
         * @param durationMs 实际参与分析的数据时长。
         * @param tier 置信度分级，见 [Tier]。
         * @param agreed 是否有两路以上互相印证（false = 只有一路支持，置信度必然打折）。
         */
        data class Ok(
            val brpm: Double,
            val sources: List<String>,
            val corr: Double,
            val cycles: Double,
            val durationMs: Long,
            val tier: String,
            val agreed: Boolean,
        ) : Result {
            val band: String get() = bandOf(brpm)
        }

        /** 给不出结论。 [reason] 直接给用户看，必须说清"差多少"而不是笼统的"数据不足"。 */
        data class NotApplicable(val reason: String) : Result
    }

    /** 一路分量信号的估计结果（内部用）。 */
    private data class Est(val source: String, val brpm: Double, val corr: Double)

    // ═══════════════ PPG 路径 ═══════════════

    /**
     * 从归一化 PPG 波形（绿光，AC/DC）估呼吸率。
     *
     * @param wave `Ppg.Ac.norm`：只除过 DC 的归一化交流分量（**未滤波**，呼吸调制还在里面）。
     * @param sampleRateHz 采样率；null 或过低直接拒答。
     * @param quality 完整度（0~1）= 有效样本占比。
     * @param durationMs 这段波形的时长。
     */
    fun fromPpg(
        wave: List<Float>,
        sampleRateHz: Int?,
        quality: Float,
        durationMs: Long,
    ): Result {
        if (durationMs < MIN_DURATION_MS) {
            return tooShort(durationMs)
        }
        val fs = sampleRateHz ?: return Result.NotApplicable(
            Text.of("s_199a541d")
        )
        if (fs < Ppg.MIN_SAMPLE_RATE_HZ) {
            return Result.NotApplicable(Text.fmt("s_0998483c", fs))
        }
        if (quality < MIN_QUALITY) {
            return Result.NotApplicable(
                Text.fmt("s_3dbb2363", (quality * 100).toInt()) +
                    Text.fmt("s_67d9d424", (MIN_QUALITY * 100).toInt())
            )
        }
        if (wave.size < fs * MIN_DURATION_MS / 1000) {
            return tooShort(durationMs)
        }

        // 去趋势：搏动本身必须去掉（它是后面"每搏压缩"的输入，留着会让峰检测不稳）
        val det = Ppg.movingAverageDetrend(wave, Ppg.detrendWindowSamples(fs))

        /**
         * 求脉搏周期**必须在高通掉呼吸段的副本上做**，这是台架逼出来的一条。
         *
         * 呼吸基线在 0.1~0.6 Hz、脉搏基频在 0.5~3.3 Hz，两者频带基本不重叠 ——
         * 但在 PPG 上**它们的幅度可以相当**（基线随呼吸的起伏常常不比搏动小）。
         * 一旦如此，在 [最长脉率, 最短脉率] 这段 lag 上，自相关被慢分量压成一条**单调下降的曲线**，
         * 脉搏峰不再构成"局部极大"，`fundamentalLag` 于是返回 null ——
         * 整段被误判成"检不出脉搏"。台架里 18/24 次每分的用例就是这样全灭的。
         *
         * 所以先用 [BAND_HIGH_HZ] 做一次高通（窗口由频率换算，不写死常数），
         * 只求周期；**幅度与基线仍在 [det] 上取**（那里呼吸调制必须留着，见下面的取法）。
         */
        val hpWin = (0.4429 * fs / BAND_HIGH_HZ).roundToInt().coerceAtLeast(3)
        val pulseBand = Ppg.movingAverageDetrend(wave, hpWin)

        val minLagHr = (fs * 60.0 / Ppg.HR_MAX).roundToInt().coerceAtLeast(Ppg.MIN_LAG)
        val maxLagHr = (fs * 60.0 / Ppg.HR_MIN).roundToInt().coerceAtMost(pulseBand.size / 2)
        val pulse = Ppg.fundamentalLagWithCorr(pulseBand, minLagHr, maxLagHr)
            ?: return Result.NotApplicable(Text.of("s_c591f1b9"))

        /**
         * 自校正：上面那道高通会把**慢脉率的基频**也削掉（心率 30~36 次/分时基频 0.5~0.6 Hz
         * 正好落在削掉的边缘），只剩二次谐波时自相关会把周期算成一半 ——
         * 这不是理论担忧，是那类输入下真实会出现的结果。
         * 用检出的峰间隔中位数回头校正一次：只在明显不一致时才改，避免在正常信号上抖动。
         */
        var period = pulse.lagSamples
        repeat(2) {
            val probe = systolicPeaks(pulseBand, period)
            val med = medianInterval(probe)
            if (med > 0.0 && abs(med - period) > 0.25 * period) period = med
        }

        val peaks = systolicPeaks(pulseBand, period)
        if (peaks.size < 4) {
            return Result.NotApplicable(
                Text.fmt("s_078573c4", peaks.size)
            )
        }
        // 实测间隔比自相关更准（自相关受插值与窗口影响），后面取幅度/基线用它定半周期
        medianInterval(peaks).takeIf { it > 0.0 }?.let { period = it }

        // 每搏三路序列
        val half = (period * 0.5).roundToInt().coerceAtLeast(1)
        val tPeak = ArrayList<Double>(peaks.size)
        val am = ArrayList<Double>(peaks.size)
        val bw = ArrayList<Double>(peaks.size)
        for (i in peaks.indices) {
            val p = peaks[i]
            val lo = (p - half).coerceAtLeast(0)
            var trough = Float.MAX_VALUE
            for (j in lo..p) if (det[j] < trough) trough = det[j]
            tPeak.add(p / fs.toDouble())
            am.add((det[p] - trough).toDouble())
            bw.add(trough.toDouble())
        }
        // 间期序列：取相邻峰的时间差（秒 -> 毫秒，与其它间期量纲一致）
        val tFm = ArrayList<Double>(maxOf(0, peaks.size - 1))
        val fm = ArrayList<Double>(maxOf(0, peaks.size - 1))
        for (i in 1 until peaks.size) {
            tFm.add(tPeak[i])
            fm.add((tPeak[i] - tPeak[i - 1]) * 1000.0)
        }

        val t0 = tPeak.first()
        val t1 = tPeak.last()
        val ests = ArrayList<Est>(3)
        estimate(tPeak, am, t0, t1)?.let { ests.add(Est(Source.PPG_AM, it.first, it.second)) }
        estimate(tPeak, bw, t0, t1)?.let { ests.add(Est(Source.PPG_BW, it.first, it.second)) }
        if (fm.size >= 4) {
            estimate(tFm, fm, tFm.first(), tFm.last())
                ?.let { ests.add(Est(Source.PPG_FM, it.first, it.second)) }
        }
        return fuse(ests, t1 - t0)
    }

    // ═══════════════ 心电逐拍路径 ═══════════════

    /**
     * 从心电的逐拍 RR 间期（毫秒）估呼吸率 —— 呼吸性心律不齐（RSA）路径。
     *
     * 它与 PPG 是**真正独立**的两路：一个是电、一个是光，采集链路与噪声来源都不同。
     * 但两者衡量的都是"心跳被呼吸调制的程度"，所以能互相印证。
     *
     * 注意采样率的天然上限：心搏 ~1 Hz，Nyquist ~0.5 Hz，而呼吸上界 0.6 Hz ——
     * 呼吸偏快时这条路会低估，所以 [RR_MAX] 之外的结果一律不给数。
     */
    fun fromRr(rrMs: List<Int>, durationMs: Long): Result {
        if (durationMs < MIN_DURATION_MS) return tooShort(durationMs)
        val beats = rrMs.filter { it in 250..2000 }
        if (beats.size < MIN_BEATS) {
            return Result.NotApplicable(
                Text.fmt("s_68435e9e", beats.size, MIN_BEATS) +
                    Text.of("s_7af20d18")
            )
        }

        // 累积时间轴（秒）与逐拍间期序列
        val t = ArrayList<Double>(beats.size)
        val v = ArrayList<Double>(beats.size)
        var acc = 0.0
        for (b in beats) {
            acc += b / 1000.0
            t.add(acc)
            v.add(b.toDouble())
        }
        val span = acc - beats.first() / 1000.0
        if (span * 1000 < MIN_DURATION_MS) return tooShort((span * 1000).toLong())

        val est = estimate(t, v, t.first(), t.last())
            ?: return Result.NotApplicable(
                Text.of("s_1df0d975")
            )
        return fuse(listOf(Est(Source.ECG_RR, est.first, est.second)), span)
    }

    // ═══════════════ 核心：一路信号 -> 呼吸率 ═══════════════

    /**
     * 把一条**非均匀的每搏序列**变成均匀网格、带通到呼吸频段，再用自相关求周期。
     *
     * @return (呼吸率次/分, 自相关)，失败返回 null。
     */
    private fun estimate(
        t: List<Double>,
        v: List<Double>,
        t0: Double,
        t1: Double,
    ): Pair<Double, Double>? {
        val grid = resample(t, v, t0, t1, GRID_HZ)
        if (grid.size < 16) return null

        // 带通：先两级滑动均值低通（平滑噪声、防混叠），再减长窗均值去掉慢漂移。
        // 窗口由频带边界换算而来，采样率变了也能保持一致（避免"抄一份常数"的坑）。
        val lowWin = (0.4429 * GRID_HZ / BAND_HIGH_HZ).roundToInt().coerceAtLeast(1)
        val highWin = (0.4429 * GRID_HZ / BAND_LOW_HZ).roundToInt().coerceAtLeast(lowWin + 1)
        var y = Ppg.movingAverage(grid, lowWin)
        y = Ppg.movingAverage(y, lowWin)
        y = Ppg.movingAverageDetrend(y, highWin)

        // 呼吸频段对应的 lag 范围
        val minLag = (GRID_HZ * 60.0 / RR_MAX).roundToInt().coerceAtLeast(2)
        val maxLag = (GRID_HZ * 60.0 / RR_MIN).roundToInt().coerceAtMost(y.size / 2)
        if (maxLag <= minLag) return null

        val est = Ppg.fundamentalLagWithCorr(y, minLag, maxLag, minCorr = MIN_CORR.toFloat())
            ?: return null
        val periodSec = est.lagSamples / GRID_HZ
        if (periodSec <= 0.0) return null
        val brpm = 60.0 / periodSec
        if (brpm.isNaN() || brpm < RR_MIN || brpm > RR_MAX) return null
        return brpm to est.corr
    }

    /**
     * 收缩期主峰检测。
     *
     * 这是"门限 + 峰计数"在**周期已知**时的正确用法：`Ppg` 当初放弃那套方法，
     * 根因是"周期本身是未知量，任何固定窗宽都必然在一端失效"（详见 `Ppg.fundamentalLag`）。
     * 而这里周期已经由自相关求出来了，搜索窗取 0.6T~1.4T 就同时满足
     * "够大以覆盖下一个峰"和"够小以不并拍" —— 两个矛盾要求不再冲突。
     */
    fun systolicPeaks(det: List<Float>, periodSamples: Double): List<Int> {
        if (det.isEmpty() || periodSamples <= 0.0) return emptyList()
        val out = ArrayList<Int>()
        val first = argMax(det, 0, minOf(det.size, periodSamples.roundToInt().coerceAtLeast(1)))
        if (first < 0) return emptyList()
        var i = first
        while (true) {
            out.add(i)
            val lo = i + (periodSamples * 0.6).roundToInt()
            val hi = minOf(det.size, i + (periodSamples * 1.4).roundToInt())
            if (lo >= hi) break
            val next = argMax(det, lo, hi)
            if (next < 0) break
            i = next
        }
        return out
    }

    /**
     * 相邻峰间隔的**中位数**（样本数）。不足两个峰返回 0。
     *
     * 用中位数而不是均值：漏检一个峰会让均值偏大一倍，而中位数只挪半个位置。
     */
    fun medianInterval(peaks: List<Int>): Double {
        if (peaks.size < 2) return 0.0
        val d = ArrayList<Int>(peaks.size - 1)
        for (i in 1 until peaks.size) d.add(peaks[i] - peaks[i - 1])
        d.sort()
        val mid = d.size / 2
        return if (d.size % 2 == 1) d[mid].toDouble() else (d[mid - 1] + d[mid]) / 2.0
    }

    private fun argMax(x: List<Float>, from: Int, until: Int): Int {
        val lo = from.coerceAtLeast(0)
        val hi = until.coerceAtMost(x.size)
        if (hi <= lo) return -1
        var best = lo
        for (i in lo + 1 until hi) if (x[i] > x[best]) best = i
        return best
    }

    /** 线性插值重采样到 [t0, t1] 上的均匀网格（[fs] Hz）。 */
    fun resample(
        t: List<Double>,
        v: List<Double>,
        t0: Double,
        t1: Double,
        fs: Double,
    ): List<Float> {
        if (t.size < 2 || fs <= 0.0) return emptyList()
        val n = ((t1 - t0) * fs).toInt() + 1
        if (n < 8) return emptyList()
        val out = ArrayList<Float>(n)
        var j = 0
        for (i in 0 until n) {
            val x = t0 + i / fs
            while (j < t.size - 2 && t[j + 1] < x) j++
            when {
                x <= t.first() -> out.add(v.first().toFloat())
                x >= t.last() -> out.add(v.last().toFloat())
                else -> {
                    val a = t[j]
                    val b = t[j + 1]
                    val r = if (b > a) (x - a) / (b - a) else 0.0
                    out.add((v[j] + r * (v[j + 1] - v[j])).toFloat())
                }
            }
        }
        return out
    }

    // ═══════════════ 融合与门控 ═══════════════

    private fun fuse(ests: List<Est>, spanSec: Double): Result {
        val usable = ests.filter { it.corr >= MIN_CORR }
        if (usable.isEmpty()) {
            return Result.NotApplicable(
                Text.of("s_ded293c7") +
                    Text.of("s_3e1bbbfe")
            )
        }
        val sorted = usable.sortedByDescending { it.corr }
        val best = sorted.first()
        val agreeing = sorted.filter { abs(it.brpm - best.brpm) <= AGREE_BRPM }

        val brpm: Double
        val sources: List<String>
        val corr: Double
        val agreed: Boolean
        if (agreeing.size >= 2) {
            // 相关系数加权平均：更可信的那一路权重更大，而不是简单平均
            var num = 0.0
            var den = 0.0
            for (e in agreeing) {
                num += e.brpm * e.corr
                den += e.corr
            }
            brpm = if (den > 0.0) num / den else best.brpm
            sources = agreeing.map { it.source }
            corr = agreeing.maxOf { it.corr }
            agreed = true
        } else {
            if (best.corr < STRONG_CORR) {
                return Result.NotApplicable(
                    Text.fmt("s_bdf3849f", fmt2(best.corr)) +
                        Text.of("s_effc0521")
                )
            }
            brpm = best.brpm
            sources = listOf(best.source)
            corr = best.corr
            agreed = false
        }

        if (brpm < RR_MIN || brpm > RR_MAX) {
            return Result.NotApplicable(
                Text.fmt("s_b664210f", fmt1(brpm), RR_MIN, RR_MAX)
            )
        }

        val cycles = spanSec * brpm / 60.0
        if (cycles < MIN_CYCLES) {
            return Result.NotApplicable(
                Text.fmt("s_f0b7f01b", fmt1(spanSec), fmt1(cycles)) +
                    Text.fmt("s_cce00d68", MIN_CYCLES)
            )
        }

        val tier = when {
            !agreed -> if (cycles >= GOOD_CYCLES) Tier.MEDIUM else Tier.LOW
            cycles >= GOOD_CYCLES && corr >= STRONG_CORR -> Tier.HIGH
            cycles >= MIN_CYCLES + 1 -> Tier.MEDIUM
            else -> Tier.LOW
        }

        return Result.Ok(
            brpm = brpm,
            sources = sources,
            corr = corr,
            cycles = cycles,
            durationMs = (spanSec * 1000).toLong(),
            tier = tier,
            agreed = agreed,
        )
    }

    private fun tooShort(durationMs: Long): Result.NotApplicable =
        Result.NotApplicable(
            Text.fmt("s_3ebccb9e", fmt1(durationMs / 1000.0)) +
                Text.fmt("s_9ea6ba57", MIN_DURATION_MS / 1000)
        )

    /**
     * 这么长的一段记录**最快能看到多慢的呼吸**（次/分）。
     *
     * 由 [MIN_CYCLES] 个周期决定：`MIN_CYCLES × 60 / 秒数`。
     * 30 秒 → 8 次/分 —— 也就是说**慢于 8 次/分的呼吸在 30 秒记录里本来就不可能被验证**
     * （覆盖不到 4 个周期），它会走"覆盖不够"那条拒答分支而不是硬给一个数。
     * 这不是缺陷，是"数据长度决定能力下限"的直接后果，所以把它做成可查的函数：
     * 界面可以用它把"还差多少"说清楚，台架用它守住 `MIN_DURATION_MS` 与 `MIN_CYCLES` 的自洽。
     */
    fun minMeasurableBrpm(durationMs: Long): Double =
        if (durationMs <= 0L) Double.MAX_VALUE
        else MIN_CYCLES * 60.0 / (durationMs / 1000.0)

    // ═══════════════ 展示辅助 ═══════════════

    private fun fmt1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)
    private fun fmt2(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)

    /** 呼吸率：整数 次/分（这个精度与算法能力匹配，显示小数会显得比实际更准）。 */
    fun formatBrpm(v: Double?): String =
        v?.let { Text.fmt("s_0a1457be", it.roundToInt()) } ?: "--"

    /** 置信度 + 依据（一行）。 */
    fun confidenceLine(r: Result.Ok): String {
        val src = r.sources.joinToString(Text.lang.itemSeparator()) { Source.label(it) }
        val base = Text.fmt("s_447443f6", Tier.label(r.tier), src, fmt1(r.cycles))
        return if (r.agreed) base else Text.fmt("s_6bfb51a6", base)
    }

    /**
     * 方法说明（界面常驻）。
     */
    val METHOD_NOTE: String get() =
        Text.of("s_bf222df1") +
            Text.of("s_5f77a5e3") +
            Text.of("s_5bd1b808")

    /**
     * 为什么不给"睡眠中的呼吸率"（这一条必须写清楚，否则会被当成没做到位）。
     */
    val WHY_NOT_SLEEP_NOTE: String get() =
        Text.of("s_33af02f6") +
            Text.of("s_ffd110bc") +
            Text.of("s_9eba88e8")

    /** 免责声明。 */
    val DISCLAIMER: String get() = Disclaimer.resp(Text.lang)

    // ═══════════════ 台架用：合成信号 ═══════════════

    /**
     * 合成一段带呼吸调制的 PPG（**仅供离线台架与自检使用**，生产代码不调用）。
     *
     * 三种调制可以分别打开，这样才能验证"每一路单独都能提对"，
     * 而不只是"合在一起看起来对" —— 合在一起时某一路错了会被另一路掩盖。
     *
     * @param brpm 真实呼吸率（次/分）
     * @param hrBpm 心率（次/分）
     * @param ampMod 幅度调制深度（0~1，相对脉搏幅度）
     * @param freqMod 间期调制深度（0~1，相对 RR）
     * @param baseMod 基线调制幅度（归一化单位，相对脉搏幅度）
     */
    fun synthPpg(
        seconds: Double,
        fs: Int,
        brpm: Double,
        hrBpm: Double,
        ampMod: Double = 0.25,
        freqMod: Double = 0.04,
        baseMod: Double = 0.30,
        noise: Double = 0.0,
        seed: Long = 1L,
    ): List<Float> {
        val n = (seconds * fs).toInt()
        val out = ArrayList<Float>(n)
        val fResp = brpm / 60.0
        var phase = 0.0        // 心搏相位（0~1）
        var rr = 60.0 / hrBpm
        var rnd = seed
        for (i in 0 until n) {
            val t = i / fs.toDouble()
            val resp = sin(2 * PI * fResp * t)
            // 频率调制：本拍的 RR 随呼吸变化
            rr = (60.0 / hrBpm) * (1.0 + freqMod * resp)
            phase += (1.0 / rr) / fs
            if (phase >= 1.0) phase -= 1.0
            val wave = pulseShape(phase)
            val amp = 1.0 + ampMod * resp
            val base = baseMod * resp
            rnd = (rnd * 1103515245L + 12345L) and 0x7fffffffL
            val nz = if (noise > 0.0) ((rnd / 0x7fffffffL) - 0.5) * 2.0 * noise else 0.0
            out.add((base + amp * wave + nz).toFloat())
        }
        return out
    }

    /**
     * 一个心搏的波形（收缩期主峰 + 舒张期小峰），相位 0~1，归一化到 ~0..1.3。
     *
     * 用**高斯**而不是"指数余弦"：后者在 25 Hz、60~90 次/分下只有 1~2 个采样点宽，
     * 窄到自相关峰不成其为"局部极大"（台架里真的出现过：因此检不出脉搏而全线拒答）。
     * 真实腕部 PPG 的收缩期主峰大约占心搏周期的 10~20%，这里 σ=0.13 周期是同一个量级。
     */
    private fun pulseShape(phase: Double): Double {
        val sys = kotlin.math.exp(-sq((phase - 0.30) / 0.13))
        val dic = 0.35 * kotlin.math.exp(-sq((phase - 0.58) / 0.16))
        return sys + dic
    }

    private fun sq(v: Double): Double = v * v

    /** 合成一段带呼吸调制的逐拍 RR（毫秒）。 */
    fun synthRr(beats: Int, brpm: Double, hrBpm: Double, freqMod: Double = 0.05): List<Int> {
        val fResp = brpm / 60.0
        val out = ArrayList<Int>(beats)
        var t = 0.0
        for (i in 0 until beats) {
            val rr = (60.0 / hrBpm) * (1.0 + freqMod * sin(2 * PI * fResp * t))
            out.add((rr * 1000).roundToInt())
            t += rr
        }
        return out
    }
}
