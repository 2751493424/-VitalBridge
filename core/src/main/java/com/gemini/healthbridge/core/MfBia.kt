package com.gemini.healthbridge.core

import kotlin.math.abs

/**
 * 多频生物电阻抗（MF-BIA）的纯逻辑：四个频点的阻抗幅值/相位校验、频响形状、
 * 5k/250k 阻抗比、相对自身基线的趋势、展示文案。
 *
 * ## 这个模块为什么存在
 * 三星传感器 SDK（1.4.1）里有两个 BIA 采集器，我们此前只用了其中一个：
 *  · `BIA_ON_DEMAND`（已用，模块 #5）→ `ValueKey.BiaSet`：给的是**三星算好的体成分**
 *    （体脂率/体水量/骨骼肌/BMR…）外加**一个**阻抗幅值 + 一个相位值（频率未公开）。
 *  · `MF_BIA_ON_DEMAND`（本模块）→ `ValueKey.MfBiaSet`：给的是**四个频点的原始阻抗**
 *    （5 / 10 / 50 / 250 kHz，各带幅值与相位）。
 *
 * 也就是说：体成分那条路是"读三星的成品"，而这条路是**我们第一次拿到原始阻抗谱**。
 * 原始量才是自研算法的原料（与 #8 存 PPG 原始波形、#9 存逐拍间期是同一条思路）。
 *
 * ## 我们**不能**做什么（先写清楚，免得被当成"体成分升级"）
 * 不能用多频阻抗**自己算体成分**：三星的体脂率模型是服务端的、没有公开，
 * 照抄一个文献回归式会产出一个"看着正常"但与三星数不一致的数 —— 这正是本项目
 * 在血氧 ratio-of-ratios 上明确拒绝过的做法。所以体成分仍然读 `BiaSet`，本模块只做
 * 阻抗谱本身能自证的量。
 *
 * ## 也**不解释相位的符号**
 * 三星文档只写了 `BODY_IMPEDANCE_PHASE_*`，**没有写符号约定**（是超前还是滞后、
 * 报告的是 +θ 还是 −θ 都无从确认）。与 PPG 的 `*_STATUS` 状态码同一条纪律：
 * 不猜语义。所以这里只做两件与符号无关的事：
 *  · 校验 `|相位| ≤ 90°`（任何真正的相位都必须满足）；
 *  · **原样显示**，并用**绝对值**去和自身基线比较。
 * 绝不产出"你的相位角偏低"这种判断 —— 那需要知道符号与频率口径，而两者都没有文档。
 *
 * ## 主指标为什么是"阻抗比 + 频响形状"而不是单个相位角
 * 生物组织的高频电流能穿过细胞膜、低频只能走细胞外液，所以：
 *  · **|Z| 必然随频率升高而下降**，这条**不需要任何标定**就成立 ——
 *    它是本模块最硬的一条门控（[analyze] 里的单调性检查），
 *    也是"这次测量到底可不可信"的自证手段；
 *  · **5k/250k 阻抗比**反映"细胞外液相对总水量的比重"，比值越大说明细胞外液占比越高。
 *    它的**绝对值**没有公认的临床切点，所以我们学 #12 的做法：**只与你自己比**（[trendOf]）。
 *
 * ## 为什么做成无 Android 依赖的纯 Kotlin
 * 与 `Ppg`/`Hrv`/`Odi`/`Resp` 同一条理由：能直接进离线 JVM 校验台架跑断言。
 * 单位（欧姆/度/千赫兹）与阈值是最容易凭印象写错的东西，而它们的错误都不会抛异常。
 */
object MfBia {

    // ═══════════════ 频点 ═══════════════

    /**
     * 四个频点，单位 **kHz**。
     *
     * 这组频率不是我们挑的：三星 `MfBiaSet` 只提供这四个（5/10/50/250）。
     * 用 Int 令牌而不是枚举：它要落库、要进 JSON、要跨版本读回（与 `Ppg.Channel` 同理）。
     */
    object Freq {
        const val K5 = 5
        const val K10 = 10
        const val K50 = 50
        const val K250 = 250

        /** 固定顺序（低→高）。落库与画图都依赖这个顺序，不要用 map 的遍历顺序。 */
        val ALL = listOf(K5, K10, K50, K250)

        fun label(khz: Int): String = "$khz kHz"
    }

    /** 一个频点的原始阻抗。可空：SDK 给不出时就是 null，**不拿 0 顶替**（0 Ω 是"短路"）。 */
    data class Point(
        val khz: Int,
        val magnitude: Float? = null,
        /** 相位（度）。符号约定未公开，**原样保留**（见类注释）。 */
        val phaseDeg: Float? = null,
    )

    /** 一次多频测量的原始输入。 */
    data class Input(
        val measuredAt: Long,
        val points: List<Point>,
        /** SDK 的 STATUS（0 = 成功）；null = 没收到。 */
        val status: Int? = null,
    )

    // ═══════════════ 门限 ═══════════════

    /** STATUS 为 0 才算成功（与 `BodyComposition.statusOf` 同一套）。 */
    const val STATUS_OK = 0

    /**
     * 幅值可用区间（欧姆）。
     *
     * 上体（手到手，手表测量）在 5 kHz 下通常是 400~700 Ω、250 kHz 下降一两百欧。
     * 这里**刻意放得很宽**（100~3000 Ω）：它不是"正常范围"，而是"物理上不可能"的边界 ——
     * 把门槛收紧只会把真实的极端体型判成失败。真正的质量门是单调性（见 [analyze]）。
     */
    const val MIN_MAGNITUDE = 100f
    const val MAX_MAGNITUDE = 3000f

    /** 相位绝对值上界（度）。任何真实相位都必须满足，与符号约定无关。 */
    const val MAX_ABS_PHASE_DEG = 90f

    /**
     * 单调性容差（相对）。
     *
     * |Z| 必须随频率升高而下降，但实测会有噪声与个体差异，所以允许 [MONOTONIC_TOL] 的
     * 反向余量；超过它就不是噪声，而是**这一次测量本身不成立**（电极接触不良、
     * 姿势不对、手指没放好）。取 0.05 相当于"允许 5% 的抖动"。
     */
    const val MONOTONIC_TOL = 0.05f

    /** 至少要有几个频点可用才给结论。缺一个还能看趋势，缺两个就不成"谱"了。 */
    const val MIN_USABLE_FREQS = 3

    // ═══════════════ 结果 ═══════════════

    sealed interface Result {
        /**
         * @param points 可用的频点（按频率升序），已剔除无效项。
         * @param ratio 5k/250k 阻抗比。两个端点有一端不可用时为 null —— **不给替代值**。
         * @param phaseAt50 50 kHz 处的相位（原样，符号不解释）。缺失时为 null。
         * @param monotonic |Z| 是否随频率单调下降（门控已保证为 true，留着是为了可解释性展示）。
         */
        data class Ok(
            val points: List<Point>,
            val ratio: Float?,
            val phaseAt50: Float?,
            val monotonic: Boolean,
        ) : Result

        /** 给不出结论。[reason] 直接给用户看。 */
        data class NotApplicable(val reason: String) : Result
    }

    // ═══════════════ 主流程 ═══════════════

    /**
     * 校验并汇总一次多频测量。
     *
     * 三道门，任何一道不过就返回 [Result.NotApplicable]：
     *  ① STATUS 非 0（SDK 明确的失败）；
     *  ② 可用频点不足 [MIN_USABLE_FREQS]，或幅值落在物理不可能区间外；
     *  ③ **|Z| 违反"随频率下降"**（超出 [MONOTONIC_TOL] 的容差）。
     *
     * 第 ③ 条是本模块的核心：它不依赖任何标定、任何临床阈值，只依赖电学常识，
     * 因此既能挡住"接触不良"这种假数据，也**不会**误伤真实的高阻抗个体。
     */
    fun analyze(input: Input): Result {
        val status = input.status
        if (status != null && status != STATUS_OK) {
            return Result.NotApplicable(
                Text.fmt("s_592f3cd0", status)
            )
        }

        // 按固定频率顺序取，缺哪个就是哪个为 null（不补 0）
        val byFreq = input.points.associateBy { it.khz }
        val usable = Freq.ALL.mapNotNull { khz ->
            val p = byFreq[khz] ?: return@mapNotNull null
            val m = p.magnitude
            if (m == null || m.isNaN() || m <= 0f) return@mapNotNull null
            if (m < MIN_MAGNITUDE || m > MAX_MAGNITUDE) return@mapNotNull null
            val ph = p.phaseDeg?.takeIf { !it.isNaN() && abs(it) <= MAX_ABS_PHASE_DEG }
            Point(khz, m, ph)
        }
        if (usable.size < MIN_USABLE_FREQS) {
            return Result.NotApplicable(
                Text.fmt("s_08deb072", usable.size, MIN_USABLE_FREQS) +
                    Text.of("s_cc189cd0")
            )
        }

        // 单调性：|Z| 必须随频率下降（生物组织的高频通路更多，这是电学常识，不需要标定）
        var monotonic = true
        for (i in 1 until usable.size) {
            val prev = usable[i - 1].magnitude!!
            val cur = usable[i].magnitude!!
            if (cur > prev * (1f + MONOTONIC_TOL)) {
                monotonic = false
                break
            }
        }
        if (!monotonic) {
            return Result.NotApplicable(
                Text.of("s_3a960d46") +
                    Text.of("s_991f45ca")
            )
        }

        return Result.Ok(
            points = usable,
            ratio = ratioOf(usable),
            phaseAt50 = usable.firstOrNull { it.khz == Freq.K50 }?.phaseDeg,
            monotonic = true,
        )
    }

    /**
     * 5k/250k 阻抗比。
     *
     * 两端缺任何一个就返回 null —— **不用邻近频点替代**：比值要跨的频率跨度正是它的含义所在，
     * 拿 10k 顶 5k 得到的数看着一样，含义已经变了。
     */
    fun ratioOf(points: List<Point>): Float? {
        val lo = points.firstOrNull { it.khz == Freq.K5 }?.magnitude ?: return null
        val hi = points.firstOrNull { it.khz == Freq.K250 }?.magnitude ?: return null
        if (lo <= 0f || hi <= 0f) return null
        return lo / hi
    }

    // ═══════════════ 相对自身基线 ═══════════════

    /**
     * 与自身近期基线的对比说明（学 #12 的做法：**只与你自己比**）。
     *
     * 阻抗比这类量没有公认的临床切点，但同一个人在同一天同一姿势下的重复性不错，
     * 所以"比你自己平时高/低"是可说清的，而"正常/异常"不可说。
     *
     * @param base 历史比值的中位数（由调用方用 [Hrv.baselineMedian] 算，界面与判据同源）。
     * @return null 表示基线不可用（记录太少）—— 此时**不要**编一句话，让调用方明说"还在积累"。
     */
    fun trendOf(current: Float?, base: Double?): Trend? {
        if (current == null || base == null || base <= 0.0 || current <= 0f) return null
        val diff = (current - base) / base * 100.0
        return Trend(
            baseMedian = base,
            diffPct = diff,
            direction = when {
                diff > TREND_FLAT_PCT -> Direction.HIGHER
                diff < -TREND_FLAT_PCT -> Direction.LOWER
                else -> Direction.STEADY
            },
        )
    }

    /** 判定"与平时相当"的死区（百分比）。测量重复性有限，几分之一的差别不该被说成趋势。 */
    const val TREND_FLAT_PCT = 5.0

    object Direction {
        const val LOWER = "lower"
        const val STEADY = "steady"
        const val HIGHER = "higher"
    }

    data class Trend(
        val baseMedian: Double,
        val diffPct: Double,
        /** 见 [Direction]。 */
        val direction: String,
    )

    fun directionLabel(token: String): String = when (token) {
        Direction.LOWER -> Text.of("s_b5292207")
        Direction.HIGHER -> Text.of("s_d4636095")
        else -> Text.of("s_e624a4f0")
    }

    // ═══════════════ 展示辅助 ═══════════════

    /** 阻抗幅值：整数欧姆（0.1 Ω 的精度没有意义）。 */
    fun formatMagnitude(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.0f Ω", it) } ?: "--"

    /**
     * 相位：一位小数 + 度。**原样输出**（符号不解释，见类注释）。
     */
    fun formatPhase(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.1f°", it) } ?: "--°"

    /** 阻抗比：两位小数（典型值在 1.1~1.6 之间，一位小数会抹掉大半信息）。 */
    fun formatRatio(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "--"

    /** 相对基线的差异：带符号一位小数百分比。 */
    fun formatDiff(pct: Double): String =
        String.format(java.util.Locale.US, "%+.1f%%", pct)

    /** 方法说明（界面常驻）。 */
    val METHOD_NOTE: String get() =
        Text.of("s_8d9018d3") +
            Text.of("s_49fb8329") +
            Text.of("s_13bf0427")

    /** 为什么不能自己算体成分（这一条必须写清楚，否则会被读成"多频版体成分"）。 */
    val WHY_NO_BODY_COMPOSITION: String get() =
        Text.of("s_3ee2f8a1") +
            Text.of("s_93331838") +
            Text.of("s_46332262")

    /** 相位符号的免责：我们没有依据解释它。 */
    val PHASE_SIGN_NOTE: String get() = Disclaimer.phaseSign(Text.lang)

    /** 免责声明。 */
    val DISCLAIMER: String get() = Disclaimer.mfBia(Text.lang)

    /** 基线不足时的说明（不要编一句话，明确说还在积累）。 */
    val BASELINE_NOTE: String get() =
        Text.of("s_94fa0205") +
            Text.of("s_469ff754")
}
