package com.gemini.healthbridge.wear

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 心电信号处理工具（纯 Kotlin，无依赖）。
 *
 * ## 为什么需要它
 * 三星 SDK 的 `ValueKey.EcgSet.ECG_MV` 上报的是**带电极直流偏置的原始电位**
 * （实测约 200mV 量级），不是已去偏置的差分波形，所以任何"幅值判定"都会失效：
 * 悬空电极的幅值噪声（2.0~10.5mV）反而比真实心电（1.7~1.8mV）还大。
 *
 * ## 曾经走错的路（务必不要再回去）
 * 最初用 **`E(5~15Hz) / E(0.5~40Hz)` 频带能量占比**做接触判据（阈值 0.25）。
 * 2026-09-14 用用户真机导出的 30 秒 CSV 复核，证明这个判据**在原理上就是错的**：
 *
 *   | 输入            | 旧比值 | 阈值 0.25 判定 |
 *   |-----------------|--------|----------------|
 *   | 合成干净心电     | 0.446  | 接触 ✓         |
 *   | 纯白噪声        | 0.286  | 接触 ✓ ← 误判  |
 *   | 纯 4.3Hz 正弦   | 0.366  | 接触 ✓ ← 误判  |
 *   | 真心电 + 0.3Hz 漂移 | 0.020 | 不接触 ✗ ← 这才是真实工况 |
 *
 * 根因：RBJ 二阶带通的裙边太缓（1Hz 处仅 -17dB），`0.5~40Hz` 那个又几乎全通，
 * 于是该比值只是**带宽占比的粗糙度量**（平坦谱恒为 10/39.5 ≈ 0.253）。
 * **任何"频带能量比"都无法区分心电与噪声，因为心电能量本来就是宽带的。**
 *
 * ## 现在用的判据：时域形态，三个互补特征
 * 1. **相干性 coherence** —— 检出所有 R 拍的 ±(200ms 或 0.35·RR) 窗口按各自峰值归一化后
 *    叠加平均，取平均模板的峰峰值（理论 0~2）。心电波形一致 → 叠出清晰 QRS；
 *    噪声相位随机 → 互相抵消。
 * 2. **锐度 sharpness** —— 模板中 R 峰 ±20ms 的能量占整窗能量之比。心电是**窄脉冲**，
 *    正弦是**宽波**。
 * 3. **峰值因子 crest** —— QRS 能量包络的 gmax 与 **20 分位基线**之比。心电是**稀疏瞬态串**
 *    （每个 RR 只有 150~230ms 有能量，其余等电位），连续波/窄带噪声包络平稳，二者量级不同。
 *
 * ⚠ 三个特征里**只有 crest 是当前的主力判据**。第 1、2 项在 2026-09-14 的重做后已不再具备
 * 区分力：极性一致化对齐（见下）会让伪迹的模板也叠得很整齐，实测阴性的相干性最高可达 2.000
 * （> 任何真心电），锐度也与阳性重叠。保留它们是为了挡住"完全无结构"的输入并给出可解释的
 * 中间量，判据实质是 `crest >= 12.0 && coherence >= 0.70 && sharpness >= 0.25 && beats >= 4`。
 *
 * 实测分离度（离线回归台架，3 个采样率 × 32 个工况，PASS=63 FAIL=0）：
 *  - crest：阳性（真实录制 / 45~150bpm / 房颤 / 含漂移肌电）最低 **15.60**
 *          阴性（漂移 / 工频 / 白噪声 / 粉噪声 / 正弦 / 平坦）最高 **7.62**
 *          ⚠ 2026-10-03 加入工频陷波（[notchMains]）后**重新标定**：阳性 16.67→15.60、
 *            阴性 9.61→7.62（分离度反而更宽），门限 12.0 不变。
 *            **改滤波链必须重跑心电回归台架并复核这两个数。**
 *
 * ## 已知弱点（记录在案，勿当已解决）
 * **等间隔的电极摩擦伪迹**可以同时拿到高相干性（1.676）与高锐度（0.511）而被放行。
 * 真实手指打滑是**不规则**的（实测该场景相干性 0.000，能正确拒绝）；
 * 只有机械式等间隔摩擦才会骗过判据。这类信号下游的 RR 合理性检查仍会给出"无法判定"。
 * 另外 **0.5mV 粉噪声**在 fs=500 下 crest 已到 9.61，与阈值 12.0 只差 1.25 倍余量，
 * 是当前最贴近边界的阴性样本，后续调参时优先复核它。
 */
object EcgSignal {

    // ───────────────────────── 流式 AC 耦合 ─────────────────────────

    /**
     * 流式去直流：输出 `x[n] - mean(最近 window 个样本)`。
     * 去掉几百毫伏的电极电位偏置，保留 QRS 的毫伏级成分。
     * 用环形缓冲 + 累计和，O(1)/样本，SDK 回调线程上可直接调用。
     */
    class AcCoupler(window: Int) {
        private var ring = FloatArray(window.coerceAtLeast(2))
        private var cap = ring.size
        private var idx = 0
        private var filled = 0
        private var sum = 0.0

        /** 采样率确定后（由 SEQUENCE 实测）调整窗口长度，尽量保持 1 秒。 */
        fun resizeWindow(newWindow: Int) {
            val w = newWindow.coerceAtLeast(2)
            if (w == cap) return
            // 保留尾部 min(filled, w) 个样本，避免窗口突变造成阶跃
            val keep = minOf(filled, w)
            val tail = FloatArray(keep)
            for (i in 0 until keep) tail[i] = ring[(idx - keep + i + cap * 2) % cap]
            ring = FloatArray(w)
            for (i in 0 until keep) ring[i] = tail[i]
            cap = w
            idx = keep % cap
            filled = keep
            sum = 0.0
            for (i in 0 until keep) sum += tail[i]
        }

        fun push(x: Float): Float {
            val mean = if (filled == 0) x.toDouble() else sum / filled
            val out = x - mean.toFloat()
            if (filled == cap) sum -= ring[idx]
            ring[idx] = x
            sum += x
            idx = (idx + 1) % cap
            if (filled < cap) filled++
            return out
        }

        fun reset() {
            java.util.Arrays.fill(ring, 0f)
            idx = 0; filled = 0; sum = 0.0
        }
    }

    // ───────────────────────── 工频抑制（2026-10-03）─────────────────────────

    /**
     * 工频陷波（50 Hz + 60 Hz 各一次），**零相位**：前向滤一遍、反向再滤一遍。
     *
     * ## ⚠⚠ 为什么必须有它（2026-10-03 真机定案）
     *
     * 用户报告：**三星原生 App 在同一台手表上一放手指就能测**，而我们的判据只给 crest 7~9、
     * 卡在 24% 进不去。用真机导出的 CSV 复算（接触检测台架）：
     *
     * | | 未陷波 | **先过 50 Hz 陷波** | 60 Hz 陷波 |
     * |---|---|---|---|
     * | crest | 7~9 | **106 ~ 164** | 8~9（无变化）|
     * | score | 38% | **100%** | 38% |
     *
     * **⇒ 心电一直都在，是被 50 Hz 工频压住的。**
     *
     * 为什么带通挡不住：实测 `cascadeBandpass(5-15Hz, stages=2)` 在 50 Hz 处只有
     * **−28.4 dB**（≈1/26），而环境工频可以强到心电带的 **10 倍以上**（真机 0.24 mV
     * vs 心电带 20 µV）—— 残留约 9 µV，其 `integ` 仍比心电等电位段高约 **16 倍**，
     * 于是 `crest = 候选峰 / p20` 的分母被抬高 16 倍、判据崩塌。
     * ⚠ 2026-09-14 那两次成功录制只是**当时环境工频小**（0.022 mV，差 11 倍），
     * 所以问题一直没暴露 —— **别再拿那两次的 crest 当"环境没问题"的证据**。
     *
     * ## 为什么用零相位而不是流式 IIR
     *
     * IIR 陷波在通带内仍有群延迟，会让 R 峰位置整体平移 ⇒ **污染 PAT**（#14 靠
     * "R 峰下标 − 脉搏波足下标"）。`assess` 与 `EcgAnalyzer.analyze` 拿到的都是**整段**
     * 数据，所以可以前后各滤一次，**净相位延迟为 0**，下游的 rPeaks / RR / PAT 位置不受影响。
     *
     * @param q 品质因数。取 20 ⇒ −3 dB 带宽 = f0/20 = 2.5 Hz，覆盖电网频率 ±0.5 Hz 的漂移。
     */
    fun notchMains(x: FloatArray, fs: Double, q: Double = 20.0): FloatArray {
        if (x.size < 8 || fs <= 0.0) return x
        var y = biquadNotch(x, fs, 50.0, q)
        y.reverse()
        y = biquadNotch(y, fs, 50.0, q)
        y.reverse()
        // 60 Hz 再走一遍：对 50 Hz 电网无害，但出口到 60 Hz 地区就靠它
        y = biquadNotch(y, fs, 60.0, q)
        y.reverse()
        y = biquadNotch(y, fs, 60.0, q)
        y.reverse()
        return y
    }

    /** RBJ cookbook 二阶陷波，**单向前向**（零相位由 [notchMains] 前后各跑一次实现）。 */
    private fun biquadNotch(x: FloatArray, fs: Double, f0: Double, q: Double): FloatArray {
        val w0 = 2.0 * PI * f0 / fs
        if (w0 <= 0.0 || w0 >= PI) return x.copyOf()
        val alpha = sin(w0) / (2.0 * q)
        val cw = cos(w0)
        val a0 = 1.0 + alpha
        val b0 = 1.0 / a0
        val b1 = -2.0 * cw / a0
        val b2 = 1.0 / a0
        val a1 = -2.0 * cw / a0
        val a2 = (1.0 - alpha) / a0
        val y = FloatArray(x.size)
        var x1 = 0.0
        var x2 = 0.0
        var y1 = 0.0
        var y2 = 0.0
        for (i in x.indices) {
            val v = x[i].toDouble()
            val o = b0 * v + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = v
            y2 = y1
            y1 = o
            y[i] = o.toFloat()
        }
        return y
    }

    // ───────────────────────── 基础滤波与统计 ─────────────────────────

    /** 去直流（1 秒滑动均值）：保护性再处理，防止上游未耦合或存在残余基线漂移。 */
    fun acCouple(x: FloatArray, fs: Double): FloatArray {
        val n = x.size
        if (n == 0) return x
        val half = (fs * 0.5).toInt().coerceAtLeast(2)
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + x[i]
        val y = FloatArray(n)
        for (i in 0 until n) {
            val a = maxOf(0, i - half)
            val b = minOf(n - 1, i + half)
            y[i] = (x[i] - (prefix[b + 1] - prefix[a]) / (b - a + 1)).toFloat()
        }
        return y
    }

    /**
     * RBJ 二阶恒定带宽带通。`skipSec` 秒的起始暂态会被**置零**（而不是截断），
     * 这样输出与输入等长，索引可以直接对应到时间轴。
     *
     * ⚠ 单级二阶的裙边只有约 6dB/倍频程（实测 5-15Hz 档在 1Hz 处仅 -17dB），
     * 做显示波形够用，做**门控/检测**请用 [cascadeBandpass]。
     */
    fun bandpass(x: FloatArray, fs: Double, fLo: Double, fHi: Double, skipSec: Double = 1.0): FloatArray {
        val n = x.size
        val y = FloatArray(n)
        if (n == 0 || fs <= 0.0) return y
        val f0 = sqrt(fLo * fHi)
        val w0 = 2.0 * PI * f0 / fs
        val q = f0 / (fHi - fLo)
        val alpha = sin(w0) / (2.0 * q)
        val b0 = alpha; val b2 = -alpha
        val a0 = 1.0 + alpha; val a1 = -2.0 * cos(w0); val a2 = 1.0 - alpha
        var x1 = 0.0; var x2 = 0.0; var y1 = 0.0; var y2 = 0.0
        val skip = (fs * skipSec).toInt()
        for (i in 0 until n) {
            val x0 = x[i].toDouble()
            val out = (b0 * x0 + b2 * x2 - a1 * y1 - a2 * y2) / a0
            x2 = x1; x1 = x0
            y2 = y1; y1 = out
            y[i] = if (i < skip) 0f else out.toFloat()
        }
        return y
    }

    /**
     * 级联带通：把 [bandpass] 串 `stages` 次。两级即四阶，裙边约 12dB/倍频程，
     * 足以把 <2Hz 的基线漂移压到门控检测看不见。
     * `skipSec` 传 0（不置零），由调用方用 guard 排除起振暂态。
     */
    fun cascadeBandpass(
        x: FloatArray, fs: Double, fLo: Double, fHi: Double,
        stages: Int = 2, skipSec: Double = 0.0,
    ): FloatArray {
        var y = x
        repeat(stages) { y = bandpass(y, fs, fLo, fHi, skipSec) }
        return y
    }

    fun meanSquare(x: FloatArray, from: Int = 0): Double {
        if (from >= x.size) return 0.0
        var s = 0.0
        for (i in from until x.size) s += x[i].toDouble() * x[i]
        return s / (x.size - from)
    }

    /** 峰峰值（mV）。 */
    fun peakToPeak(x: FloatArray, from: Int = 0): Float {
        if (from >= x.size) return 0f
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (i in from until x.size) {
            if (x[i] < lo) lo = x[i]
            if (x[i] > hi) hi = x[i]
        }
        return hi - lo
    }

    // ───────────────────────── 信号质量评估 ─────────────────────────

    /** 通过门控所需的最低模板相干性。 */
    const val COHERENCE_MIN = 0.70

    /** 通过门控所需的最低 R 峰锐度（形态必须是窄脉冲而不是宽波）。 */
    const val SHARPNESS_MIN = 0.25

    /**
     * 通过门控所需的最低 QRS 能量**峰值因子**（crest = gmax / p20(integ)）。
     *
     * 心电是**稀疏瞬态串**：每个 RR 只有约 150~230ms 的 QRS 能量，其余时间是等电位段，
     * 所以 integ 的"安静基线"远小于 gmax（QRS 段）→ crest 很大。
     * 连续波（正弦 / 工频 / 窄带噪声）的 integ 包络平稳，基线接近 gmax → crest 小。
     *
     * ⚠ 基线必须用 **20 分位**，不能用中位数。心率升高时 QRS 占空比上升，
     *   150bpm 时中位数已经被 QRS 尾巴污染，crest 从 15.6 掉到 3.9 —— 足以误拒一段
     *   26 拍、cv=0.000 的整齐心动过速（实测）。20 分位始终落在等电位段，对心率不敏感。
     *   也不能取得更低（如 p10）：伪迹的 crest 会同步抬高，反而缩小分离度。
     *
     * 这一项专治**落在通带内的周期性伪迹**——它们能骗过相干性与锐度：
     *   | 工况                         | 相干性 | 锐度 | crest(p20) | 仅前两项 |
     *   |-----------------------------|--------|------|------------|----------|
     *   | 8Hz 纯正弦 (fs=100/500)      | ~2.00  | 0.31 | 1.2~1.3    | 放行 ✗   |
     *   | 4.3Hz 纯正弦                 | 1.96   | 0.23 | 1.4        | 拒绝     |
     *   | 工频干扰 (fs=100)            | 1.32   | 0.47 | 8.0        | 放行 ✗   |
     *   | 1mV 白噪声                   | 1.25   | 0.54 | 8.6        | 放行 ✗   |
     *   | 0.5mV 粉噪声（带通后窄带）    | 1.33   | 0.56 | 5.9        | 放行 ✗   |
     *   | 真心电最低（150bpm）          | 1.52   | 0.45 | 15.6       | 放行 ✓   |
     *
     * 阈值取 12.0：真心电最低 **15.60** vs 伪迹最高 **7.62**，两侧余量分别 1.30× / 1.57×。
     * ⚠ 这两个数是 2026-10-03 加入工频陷波（[notchMains]）后**重新标定**的
     *   （原为 16.67 / 9.61）。**改滤波链就必须重跑** 心电回归台架
     *   并复核这一行。
     */
    const val CREST_MIN = 12.0

    /** 至少这么多检出拍才评估：3 拍随机对齐也可能偶然相干。 */
    const val MIN_BEATS = 4

    /** 检测阶段至少要有这么多秒的数据才评估（保证足够拍数）。 */
    const val MIN_EVAL_SEC = 5.0

    /** 单次评估最多回看的秒数，限制手表上的计算量（门控每 600ms 跑一次）。 */
    private const val MAX_ANALYSIS_SEC = 12.0

    /** 模板窗口上限（秒）；高心率时按 0.35·RR 收窄，避免吃到相邻心搏。 */
    private const val TEMPLATE_HALF_MAX = 0.20

    /** 模板窗口下限（秒）。 */
    private const val TEMPLATE_HALF_MIN = 0.10

    /** R 峰精细化搜索半径（秒）。 */
    private const val REFINE_HALF = 0.06

    /** 去重：两拍间隔小于此值视为同一拍被检两次。 */
    private const val MIN_BEAT_GAP = 0.20

    /** 锐度统计半径（秒）。 */
    private const val SHARP_HALF = 0.020

    /** 不应期（秒）：两峰最小间隔。0.25s → 上限 240 次/分，覆盖生理量程。 */
    private const val REFRACTORY_SEC = 0.25

    /**
     * 局部归一化的滑窗半宽（秒）：门限参考的是"邻域能量基线"。
     * 必须**远大于一个 RR**（否则 QRS 自己会把基线抬起来），又**小于典型伪迹时长的量级**才谈得上
     * 把缓变瞬态归一化掉。2 秒（即 4 秒窗）在 45~150bpm 全量程都满足。
     */
    private const val NORM_HALF_SEC = 2.0

    /**
     * 候选峰所需的**局部突出度**：QRS 段能量 / 4 秒邻域均值。
     *
     * 台架实测（3 采样率 × 32 工况 + 2 段真机录制 + 瞬态探针 probe_outlier.kt）：
     *   | 类别                                  | 突出度          |
     *   |--------------------------------------|----------------|
     *   | 真心电（45~150bpm / 房颤 / 含漂移）      | **2.6 ~ 9.1**（150bpm 最低） |
     *   | 缓变瞬态（200mV 阶跃 / 300ms 运动伪迹）  | **1.1 ~ 1.4**  |
     *   | 白/粉噪声、工频、悬空电极                | 2.4 ~ 4.5      |
     *
     * 阈值 2.0 落在"真心电最低 2.6"与"瞬态最高 1.4"之间（两侧各约 1.9 倍余量）：
     * 真心电全量程都能成为候选峰，而**瞬态一条候选峰都产不出来** —— 这正是它不会再把
     * crest 分子顶高的原因（见下面 crest 的注释）。
     *
     * ⚠ 噪声类伪迹的突出度（2.4~4.5）与真心电重叠，所以这一项**不是**区分判据，
     *   它只负责"把候选峰选出来"。真正挡噪声的仍然是 crest，别搞混。
     *   也别为了"拒得更干净"把阈值往上抬：抬到 3.0 就会把 130/150bpm 的真心电一起拒掉
     *   （高心率时 QRS 占空比高，局部均值被自己抬高，突出度天然降到 2.6~3.3）。
     */
    private const val NORM_THR = 2.0f

    /**
     * 候选峰能量的**截尾倍数**：能量超过"候选峰能量中位数"这么多倍的候选峰视为离群伪迹，
     * 从叠拍与 crest 分子中剔除（见 assess 里 1b 的注释）。
     *
     * 取 4.0：真心电各拍能量差异远小于 4 倍（含早搏、呼吸调制），而运动伪迹动辄高出几十倍。
     * 干净心电下截尾不生效，因此这一项**不改变已有标定**。
     */
    private const val CAND_TRIM_K = 4.0

    /**
     * @param contact     是否判定"手指已接触且存在可用心电信号"
     * @param coherence   模板相干性（0~2）。**不具区分力**：极性对齐后连伪迹也能叠到 1.3~2.0，
     *                    比真心电还高。保留它只作可解释量与兜底，别拿它单独判接触。
     * @param sharpness   模板 R 峰锐度（0~1），击落周期性宽波伪迹
     * @param crest       QRS 能量峰值因子 gmax/p20(integ)。**实际起分离作用的就是它**
     * @param beats       参与叠加的拍数
     * @param amplitudeMv QRS 带峰峰值（mV），仅展示
     * @param heartRate   由叠拍 RR 中位数推算（可为 null）
     * @param rrCv        鲁棒变异系数 1.4826·MAD/中位RR（可为 null）
     */
    data class Quality(
        val contact: Boolean,
        val coherence: Double,
        val sharpness: Double,
        val crest: Double,
        val beats: Int,
        val amplitudeMv: Float,
        val heartRate: Int?,
        val rrCv: Float?,
        /** 局部突出度最大值。诊断用：区分"能量被瞬态拉高"与"真的没有信号"。 */
        val envMax: Double = 0.0,
    ) {
        /**
         * 0~1 直观质量分：取**三项判据中最差的一项**相对各自阈值的余量。
         *
         * 恒有 `score >= 0.5f  ⟺  contact == true`（拍数不足时 score 直接为 0）。
         * 界面上的"信号良好 / 质量条 / 测量中的信号变差提示"全部依赖这个等价关系。
         *
         * ⚠ 不能用单项给分，**尤其不能用相干性**：极性一致化后伪迹的叠拍模板同样能对齐，
         * 真机 v51 实测"手指没放上电极"时相干性 1.65（>阈值 0.70）而峰值因子只有 2.6
         * —— 只按相干性算分就会显示"信号良好，正在确认…"，用户以为程序坏了。
         * 峰值因子 2.6 vs 阈值 12.0 只有最差项能反映出来。
         */
        val score: Float
            get() {
                if (beats < MIN_BEATS) return 0f
                val worst = minOf(
                    (coherence - COHERENCE_MIN) / COHERENCE_MIN,
                    (sharpness - SHARPNESS_MIN) / SHARPNESS_MIN,
                    (crest - CREST_MIN) / CREST_MIN,
                ).coerceIn(-1.0, 1.0)
                return (0.5 + 0.5 * worst).toFloat()
            }

        companion object {
            val EMPTY = Quality(false, 0.0, 0.0, 0.0, 0, 0f, null, null)
        }
    }

    /**
     * [detectQrsPeaks] 的结果。
     *
     * @param positions QRS 峰位置（**积分域**索引，升序）
     * @param envMax    局部突出度最大值。诊断用：它能把"能量被瞬态拉高"与"真的没有信号"分开
     *                  ——真机接触页三项全 0 时，看这个数就知道是哪一种。
     * @param trimmed   因离群被截尾掉的候选峰个数（诊断用）
     */
    data class Peaks(
        val positions: IntArray,
        val envMax: Float,
        val trimmed: Int,
    ) {
        val size: Int get() = positions.size
    }

    /**
     * 从能量积分序列里**稳健**检出 QRS 峰。门控（[assess]）与最终分析（`EcgAnalyzer`）
     * 共用这一个实现 —— 两边各写一套必然会漂移，而且这个坑两边都踩过。
     *
     * 两步，缺一不可：
     *  1. **局部归一化门限**：候选峰要求 `integ / 4 秒邻域均值 > [NORM_THR]`。
     *     不用 `0.35·gmax` 这类全局门限：窗口里任何一个远强于 QRS 的瞬态都会把门限抬到
     *     真实 QRS 之上，整段检 0 拍。
     *  2. **截尾剔除离群峰**：能量超过候选峰能量中位数 [CAND_TRIM_K] 倍的候选峰视为伪迹剔除。
     *     局部归一化压得住"持续数秒"的瞬态（它自己进了分母），但压不住**短促**的
     *     （300ms 伪迹只占 4 秒窗的 7.5%，拉不高自己的局部均值），不剔除就会把
     *     crest 分子顶到天上 → "悬空电极 + 一次运动伪迹"被误判为已接触。
     *
     * 为什么不沿用 Pan-Tompkins 的 spki/npki 自适应阈值：那套写法里 `spki` **只被推高、不会回落**，
     * 一次瞬态就把后面整段心拍当成噪声丢掉。台架实测：30 秒真机录制里插一句 300ms / 20mV 运动伪迹，
     * 检出拍数从 39 掉到 2，直接判"无法判定"（probe_outlier.kt 的 C 组）。
     *
     * @param integ        能量积分序列（微分→平方→150ms 滑窗积分）
     * @param guard        起振暂态长度（样本数），其之前不参与检出
     * @param refractorySec 不应期（秒）：两峰最小间隔
     */
    fun detectQrsPeaks(
        integ: FloatArray,
        fs: Double,
        guard: Int,
        refractorySec: Double = REFRACTORY_SEC,
    ): Peaks {
        val n = integ.size
        if (n <= 0 || fs <= 0.0) return Peaks(IntArray(0), 0f, 0)

        // ── 稳健基线（中位数）：只用来防 0 除，保证平线信号的突出度恒为 0 ──
        val sorted = integ.copyOf()
        sorted.sort()
        val p50 = sorted[(sorted.size * 0.5).toInt().coerceIn(0, sorted.size - 1)]
        val floor = (0.5 * p50).toDouble().coerceAtLeast(1e-12)

        // ── 局部归一化突出度 env = integ / 4 秒滑动均值 ──
        val halfW = (fs * NORM_HALF_SEC).toInt().coerceAtLeast(2)
        val pre = DoubleArray(n + 1)
        for (i in 0 until n) pre[i + 1] = pre[i] + integ[i]
        val env = FloatArray(n)
        var envMax = 0f
        for (i in 0 until n) {
            val a = maxOf(0, i - halfW)
            val b = minOf(n - 1, i + halfW)
            val m = (pre[b + 1] - pre[a]) / (b - a + 1)
            val e = (integ[i] / maxOf(m, floor)).toFloat()
            env[i] = e
            if (i >= guard && e > envMax) envMax = e
        }

        // ── 检出候选峰 ──
        val refractory = (fs * refractorySec).toInt().coerceAtLeast(3)
        val raw = ArrayList<Int>(64)
        var last = -refractory
        var i = guard + 1
        while (i < n - 1) {
            val v = env[i]
            if (v > NORM_THR && v >= env[i - 1] && v > env[i + 1] && i - last >= refractory) {
                raw.add(i); last = i
            }
            i++
        }
        if (raw.isEmpty()) return Peaks(IntArray(0), envMax, 0)

        // ── 截尾：剔除能量离群的候选峰 ──
        val energies = DoubleArray(raw.size) { integ[raw[it]].toDouble() }
        val eSorted = energies.copyOf()
        eSorted.sort()
        val eMed = eSorted[eSorted.size / 2]
        val lim = (CAND_TRIM_K * eMed).toFloat()
        val kept = ArrayList<Int>(raw.size)
        for (p in raw) if (integ[p] <= lim) kept.add(p)
        return Peaks(kept.toIntArray(), envMax, raw.size - kept.size)
    }

    /**
     * 把**能量包络上的峰位**吸附回**真实 QRS** 在该信号里的极值点。
     *
     * ## ⚠⚠ 为什么必须做这一步（2026-09-27 定位到的 PAT 根因）
     *
     * [detectQrsPeaks] 是在 **150ms 因果滑窗积分**（`diff²` 的滑动均值）所得的包络上找峰的
     * —— 这是 Pan-Tompkins 的标准做法，用来回答"哪里有心搏"非常可靠。但**那个位置不是 R 峰的时刻**：
     *
     * - 滑窗积分是**因果**的（窗口 `[i-150ms, i]`）。对一次 QRS 而言，积分的"平顶"出现在
     *   QRS **之后约 60ms 到 150ms** 之间，峰位由该区间内的噪声随机决定
     *   → 峰位比 R 峰**晚约 75ms，且在平顶上逐拍抖动**；
     * - 上游 [cascadeBandpass]（`stages=2` 的 RBJ）本身还有几十毫秒的**群延迟**。
     *
     * 两者相加，实测（2026-09-27 两份真机录制：把生产检测链离线逐采样复刻，
     * 复刻出的峰位与导出的 `rpeaks` 中位偏移 **0 采样**、41/41 与 43/43 全部对上）
     * —— 包络峰比真实 QRS **晚 93~107ms**，逐拍抖动 ±20~25ms。
     *
     * 对**心率**无害（RR 是差值，固定偏移相消），但对两条下游都有害：
     * - 模块 #14（PAT）：「R 峰 → 脉搏波足」的毫秒时延，**参考点直接错 100ms**；
     * - 模块 #9（HRV）：逐拍抖动直接进 RR 序列，**伪迹率被虚增**。
     *
     * ## 做法
     *
     * 在每个包络峰的邻域 `[p-before, p+after]` 里找**本信号自己的极值点**当作 QRS 位置。
     *
     * - 窗口必须**向前**留够：包络峰恒晚于 QRS（见上），`before` 要 ≥ 滑窗长度 + 滤波器群延迟。
     * - **极性（R 波还是 S 波主导）必须整段统一**，不能逐拍各取 `|·|` 最大 —— R 与 S 相距
     *   20~40ms，逐拍在两者之间跳会把这个抖动原样带进 RR 与 PAT。这里先用**中位数**定出
     *   整段的主导极性，再逐拍取该极性的极值。
     * - 结果**保证严格递增**（万一两拍吸附到同一点，后一个顺延一个采样）：否则会出现
     *   0ms 的 RR 间期，被 HRV 的伪迹修正误当成"线索"。
     *
     * ⚠ 本函数**幂等**：把已经吸附过的位置再传进来，结果不变（极值点仍在窗口内且极性相同）。
     * 所以调用方不必区分"这批下标是包络峰还是已经吸附过"。
     *
     * @param signal 已去直流的心电（`EcgAnalyzer` 传的是 `ac`）。**不要传 `filtered`** ——
     *   带通自身还有群延迟，用它吸附等于把延迟留在里面。
     * @param envelopePeaks [detectQrsPeaks] 给出的包络峰下标（升序）
     * @return 吸附后的下标（升序，长度与输入相同）
     */
    fun snapToQrs(
        signal: FloatArray,
        envelopePeaks: IntArray,
        fs: Double,
        beforeSec: Double = 0.25,
        afterSec: Double = 0.05,
    ): IntArray {
        val n = signal.size
        if (n == 0 || envelopePeaks.isEmpty() || fs <= 0.0) return envelopePeaks.copyOf()
        val before = (fs * beforeSec).toInt().coerceAtLeast(1)
        val after = (fs * afterSec).toInt().coerceAtLeast(1)
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + signal[i]

        val hi = IntArray(envelopePeaks.size)
        val lo = IntArray(envelopePeaks.size)
        val upDev = DoubleArray(envelopePeaks.size)
        val downDev = DoubleArray(envelopePeaks.size)
        for (k in envelopePeaks.indices) {
            val p = envelopePeaks[k]
            val a = maxOf(0, p - before)
            val b = minOf(n - 1, p + after)
            if (b - a < 3) {
                hi[k] = p; lo[k] = p; upDev[k] = 0.0; downDev[k] = 0.0
                continue
            }
            val mean = (prefix[b + 1] - prefix[a]) / (b - a + 1)
            var iHi = a
            var iLo = a
            var vHi = signal[a] - mean
            var vLo = vHi
            for (i in a + 1..b) {
                val v = signal[i] - mean
                if (v > vHi) { vHi = v; iHi = i }
                if (v < vLo) { vLo = v; iLo = i }
            }
            hi[k] = iHi
            lo[k] = iLo
            upDev[k] = vHi
            downDev[k] = -vLo
        }
        val positive = medianOf(upDev) >= medianOf(downDev)
        val out = IntArray(envelopePeaks.size) { if (positive) hi[it] else lo[it] }
        // 严格递增：左界被上一拍顶住（见 KDoc）
        for (k in 1 until out.size) {
            if (out[k] <= out[k - 1]) out[k] = out[k - 1] + 1
        }
        return out
    }

    /** 中位数（[snapToQrs] 判极性用；不改动入参）。 */
    private fun medianOf(v: DoubleArray): Double {
        if (v.isEmpty()) return 0.0
        val s = v.copyOf()
        s.sort()
        return s[s.size / 2]
    }

    /**
     * 对一段 **已 AC 耦合** 的样本做信号质量评估。
     *
     * @param acSamples 必须是 [AcCoupler] 或 [acCouple] 的输出（已去电极偏置）
     */
    fun assess(acSamples: FloatArray, fs: Double): Quality = assessTraced(acSamples, fs, null)

    /**
     * 同 [assess]，但可把中间量写入 `trace`。
     * 用途：离线回归里当 Kotlin 结果与参考镜像不一致时定位分歧点（见心电回归台架）。
     */
    fun assessTraced(acSamples: FloatArray, fs: Double, trace: StringBuilder?): Quality {
        if (fs <= 0.0) return Quality.EMPTY

        // 只回看最近 12 秒，控制计算量
        val maxN = (fs * MAX_ANALYSIS_SEC).toInt()
        val x = if (acSamples.size > maxN) {
            acSamples.copyOfRange(acSamples.size - maxN, acSamples.size)
        } else acSamples
        if (x.size < (fs * 3.0).toInt()) return Quality.EMPTY

        // ⚠⚠ 顺序不可换：**先工频陷波，再带通**。带通在 50 Hz 只有 −28.4 dB，
        //   强工频环境下的残留足以把 p20 抬高一个数量级（见 notchMains 的实测表）。
        val xNotched = notchMains(x, fs)

        // 5~15Hz 用两级级联（四阶）：二阶裙边太缓，基线漂移会漏进来
        val bp = cascadeBandpass(xNotched, fs, 5.0, 15.0, stages = 2)
        val n = bp.size
        // 起振暂态必须排除：一个 1.5mV 的起始阶跃会生成巨大暂态，
        // 影响这一段（包括 env 的邻域均值）都不可信
        val guard = min((fs * 1.0).toInt(), n / 4)
        if (guard >= n - 3) return Quality.EMPTY
        trace?.append("n=$n guard=$guard ")

        val amp = peakToPeak(bp, guard)

        // 微分 → 平方 → 150ms 滑动积分（Pan-Tompkins 标准流水线）
        //
        // ⚠ 微分这一步**不可省**。曾经漏掉它（直接平方 bp），后果是积分峰变成**宽平顶**
        //   （QRS 本身 60~100ms 宽，再被 150ms 窗积分一起抹宽），平顶上任意一点都近乎相等，
        //   于是 argmax 对 1e-7 级数值扰动极其敏感 —— 检测位置在 ±15 样本（±60ms）间跳动。
        //   叠拍时相位对不齐，模板被抹平，相干性从 1.71 崩到 0.47（fs=250 / 60bpm 实测复现），
        //   真心电因此被误判为"无接触"（屏幕上就是 11% / 0 拍 / 0.00mV）。
        //   微分把 QRS 的陡沿放大、把宽缓的 T 波压平，积分峰变成尖顶，argmax 稳定。
        //   离线对照见心电对照脚本（两种实现分别精确复现两条 cand 序列）。
        val diff = FloatArray(n)
        for (i in 1 until n) diff[i] = bp[i] - bp[i - 1]
        val integ = FloatArray(n)
        val win = (fs * 0.15).toInt().coerceIn(3, 400)
        var run = 0.0
        for (i in 0 until n) {
            val s = diff[i].toDouble() * diff[i]
            run += s
            if (i >= win) run -= diff[i - win].toDouble() * diff[i - win]
            integ[i] = (run / win).toFloat()
        }

        // 全局峰只在 guard 之后取（首秒的滤波起振暂态不可信）
        var gmax = 0f
        for (i in guard until n) if (integ[i] > gmax) gmax = integ[i]
        if (gmax <= 0f) return Quality.EMPTY.copy(amplitudeMv = amp)
        trace?.append("win=$win gmax=$gmax ")

        // ── QRS 能量峰值因子的**基线**：20 分位（落在等电位段）──
        // 不用中位数：心率高时中位数会被 QRS 尾巴污染（150bpm 时 crest 掉到 3.9 会误拒整齐心动过速）。
        val seg = FloatArray(n - guard)
        System.arraycopy(integ, guard, seg, 0, n - guard)
        seg.sort()
        val p20 = seg[(seg.size * 0.20).toInt().coerceIn(0, seg.size - 1)]
        trace?.append("p20=$p20 p50=${seg[seg.size / 2]} ")

        val refractory = (fs * 0.25).toInt().coerceAtLeast(3)
        val refW = (fs * REFINE_HALF).toInt().coerceAtLeast(1)
        val maxHalf = (fs * TEMPLATE_HALF_MAX).toInt().coerceAtLeast(2)

        // ── 1) 检出候选峰：走 [detectQrsPeaks] 的**局部归一化 + 截尾**稳健检测 ──
        // 为什么不能用 0.35·gmax 这种全局门限、也不能用 spki/npki 那套自适应阈值，
        // 见 detectQrsPeaks 的注释（一句话：一个瞬态就能让整段检 0 拍）。
        val det = detectQrsPeaks(integ, fs, guard)
        val cand = ArrayList<Int>(det.size)
        for (p in det.positions) cand.add(p)
        trace?.append("envMax=${det.envMax} trimmed=${det.trimmed} cand=${cand.take(24)} ")
        if (cand.size < MIN_BEATS) {
            return Quality.EMPTY.copy(amplitudeMv = amp, envMax = det.envMax.toDouble())
        }

        // ── QRS 能量峰值因子 crest = (候选峰能量) / p20 ──
        //
        // 分子取**已检出候选峰**里的最大能量，不取全局最大值：远强于 QRS 的瞬态若混进来，
        // gmax/p20 会被顶到天上，crest 判据随之形同虚设（悬空电极 + 一次运动伪迹就会
        // "相干性过关 + 峰值因子虚高"而被误判为已接触）。瞬态已在上一步被截尾剔除，
        // 所以这里取最大是安全的；干净心电下 QRS 峰本身就是全局最强，口径不变。
        var candMax = 0f
        for (p in cand) if (integ[p] > candMax) candMax = integ[p]
        val crest = if (p20 > 1e-12f) (candMax / p20).toDouble() else 999.0
        trace?.append("candMax=$candMax crest=$crest ")

        // ── 2) 精细化到 bp 的局部极值作 R 基准（**必须统一极性**）──
        // 不能取 |bp| 的最大值：QRS 的 R 峰与 S 谷是一对**符号相反**的大偏转，
        // 二者相距约 40~50ms。低采样率下（fs=100，1 样本=10ms）细化窗只有 ±6 样本，
        // 噪声会让不同心搏分别锁定到 R 或 S → 叠拍时符号相反的模板互相抵消，
        // 相干性从 1.0 崩到 0.48（实测 fs=100 / 70bpm 含漂移）。
        // 正确做法：先用全局最强偏转定出 QRS 极性，再让每一拍都对齐到**同一符号**的极值。
        var refSign = 0f
        var refAbsMax = -1f
        for (p in cand) {
            if (p - maxHalf - refW < guard || p + maxHalf + refW >= n) continue
            val lo = maxOf(guard, p - refW)
            val hi = minOf(n - 1, p + refW)
            var bv = 0f
            var bs = 0f
            for (k in lo..hi) {
                val a = abs(bp[k])
                if (a > bv) { bv = a; bs = bp[k] }
            }
            if (bv > refAbsMax) { refAbsMax = bv; refSign = if (bs >= 0f) 1f else -1f }
        }
        if (refSign == 0f) return Quality.EMPTY.copy(amplitudeMv = amp)
        trace?.append("refSign=$refSign ")

        val refined = IntArray(cand.size)
        var m = 0
        for (p in cand) {
            if (p - maxHalf - refW < guard || p + maxHalf + refW >= n) continue
            val lo = maxOf(guard, p - refW)
            val hi = minOf(n - 1, p + refW)
            var best = lo
            var bv = refSign * bp[lo]
            for (k in lo..hi) {
                val a = refSign * bp[k]
                if (a > bv) { best = k; bv = a }
            }
            refined[m++] = best
        }
        trace?.append("refined=$m ")
        if (m < MIN_BEATS) return Quality.EMPTY.copy(amplitudeMv = amp)

        // ── 3) 去重（同一拍被相邻候选重复命中）──
        val rPts = ArrayList<Int>(m)
        val minGap = (fs * MIN_BEAT_GAP).toInt().coerceAtLeast(1)
        for (k in 0 until m) {
            val r = refined[k]
            if (rPts.isEmpty() || r - rPts[rPts.size - 1] >= minGap) rPts.add(r)
        }
        trace?.append("rPts=${rPts.size} R=${rPts.take(20)} ")
        if (rPts.size < MIN_BEATS) return Quality.EMPTY.copy(amplitudeMv = amp)

        // ── 4) 模板半窗随 RR 自适应 ──
        // 固定 200ms 在 130bpm（RR 462ms）下会吃到相邻心搏，使每拍的归一化系数漂移，
        // 相干性从 ~1.7 掉到 0.76（实测）。改为 min(200ms, max(100ms, 0.35·RR))。
        val rr0 = ArrayList<Int>(rPts.size)
        for (k in 1 until rPts.size) {
            val ms = ((rPts[k] - rPts[k - 1]) * 1000.0 / fs).toInt()
            if (ms in 250..2200) rr0.add(ms)
        }
        // ⚠ 必须排序后再取中间元素：rr0 是**检出顺序**不是升序，
        // 直接取 rr0[size/2] 在有漏检/多检时与中位 RR 相差很大，
        // 会把模板窗口宽度算错（实测让同一段 60bpm 的相干性从 1.71 掉到 0.47）。
        val medRr0 = if (rr0.isEmpty()) 800.0 else {
            val s = rr0.sorted()
            s[s.size / 2].toDouble()
        }
        val half = ((minOf(TEMPLATE_HALF_MAX, maxOf(TEMPLATE_HALF_MIN, 0.35 * medRr0 / 1000.0))) * fs)
            .toInt().coerceAtLeast(2)
        trace?.append("rr0=${rr0.size} medRr0=$medRr0 half=$half ")

        // 窗口收窄后重新确认边界可用
        val pts = ArrayList<Int>(rPts.size)
        for (r in rPts) {
            if (r - half - refW >= guard && r + half + refW < n) pts.add(r)
        }
        if (pts.size < MIN_BEATS) return Quality.EMPTY.copy(amplitudeMv = amp)

        // ── 5) 幅度一致性过滤：抛弃远低于中位数的假峰 ──
        val amps = FloatArray(pts.size)
        for (k in pts.indices) {
            var mx = 0f
            for (j in pts[k] - half until pts[k] + half) {
                val a = abs(bp[j])
                if (a > mx) mx = a
            }
            amps[k] = mx
        }
        val sortedAmp = amps.clone()
        sortedAmp.sort()
        val medAmp = sortedAmp[sortedAmp.size / 2]
        trace?.append("medAmp=$medAmp ")
        if (medAmp <= 1e-9f) return Quality.EMPTY.copy(amplitudeMv = amp)
        val kept = ArrayList<Int>(pts.size)
        for (k in pts.indices) if (amps[k] >= 0.4f * medAmp) kept.add(pts[k])
        trace?.append("kept=${kept.size} K=${kept.take(20)} ")
        if (kept.size < MIN_BEATS) return Quality.EMPTY.copy(amplitudeMv = amp)

        // ── 6) 归一化叠加 → 相干性 ──
        val acc = DoubleArray(2 * half)
        var used = 0
        for (r in kept) {
            var mx = 0f
            for (j in r - half until r + half) {
                val a = abs(bp[j])
                if (a > mx) mx = a
            }
            if (mx <= 1e-9f) continue
            val inv = 1.0 / mx
            for (k in 0 until 2 * half) acc[k] += bp[r - half + k] * inv
            used++
        }
        trace?.append("used=$used ")
        if (used < MIN_BEATS) return Quality.EMPTY.copy(amplitudeMv = amp)

        var tMax = -Double.MAX_VALUE
        var tMin = Double.MAX_VALUE
        var tPeakIdx = 0
        var tPeakAbs = -1.0
        for (k in acc.indices) {
            val v = acc[k] / used
            if (v > tMax) tMax = v
            if (v < tMin) tMin = v
            val a = abs(v)
            if (a > tPeakAbs) { tPeakAbs = a; tPeakIdx = k }
        }
        val coherence = tMax - tMin

        // ── 7) 锐度：R 峰 ±20ms 能量 / 整窗能量 ──
        val sh = (fs * SHARP_HALF).toInt().coerceAtLeast(1)
        var ePeak = 0.0
        var eTot = 0.0
        for (k in acc.indices) {
            val v = acc[k] / used
            eTot += v * v
            if (k >= tPeakIdx - sh && k <= tPeakIdx + sh) ePeak += v * v
        }
        val sharpness = if (eTot > 0.0) ePeak / eTot else 0.0

        // ── 8) RR 统计（只用保留下来的拍）──
        val rr = ArrayList<Int>(kept.size)
        for (k in 1 until kept.size) {
            val ms = ((kept[k] - kept[k - 1]) * 1000.0 / fs).toInt()
            if (ms in 250..2200) rr.add(ms)
        }
        val hr: Int?
        val cv: Float?
        if (rr.size >= 3) {
            val sr = rr.sorted()
            val med = sr[sr.size / 2]
            hr = if (med > 0) (60000.0 / med).toInt() else null
            val dev = sr.map { abs(it - med) }.sorted()
            val mad = dev[dev.size / 2]
            cv = (1.4826f * mad / med).toFloat()
        } else {
            hr = null; cv = null
        }

        val contact = coherence >= COHERENCE_MIN && sharpness >= SHARPNESS_MIN &&
            crest >= CREST_MIN && used >= MIN_BEATS
        return Quality(contact, coherence, sharpness, crest, used, amp, hr, cv, det.envMax.toDouble())
    }
}
