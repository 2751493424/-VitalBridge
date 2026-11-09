package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 心电会话的**节律复查**（2026-10-10，用户要求「基于 IBI 做简单早搏/心律不齐预警」）。
 *
 * ## ⚠⚠ 先说清楚它**不是**什么
 * 它**不是早搏诊断**，也**不是预警**。名字与文案里都**不许**出现
 * 「早搏 / 室早 / 房早 / 心律失常」—— 判据上做不到（见下面第 2 条），
 * 措辞上是合规红线（与 `BpPredict` 的"只给预估参考"同一条纪律）。
 *
 * ## 它补的是哪一块缺口（为什么单发早搏现在看不见）
 *
 * 手表端 `EcgAnalyzer` 已经有一条节律整齐度判据：
 * `regular = !(cv >= 0.15 && irrRatio >= 0.25)`，其中
 * `cv = 1.4826·MAD/中位RR`、`irrRatio = 相邻 RR 差异>20% 的占比`。
 *
 * ⚠ **34 个间期里只夹着一处提前搏动时，这条判据什么都看不到**：
 * 序列 `[800 … 800, 400, 1200, 800 … 800]` 的中位数仍是 800，
 * ⇒ 34 个偏差里 32 个是 0 ⇒ **MAD = 0 ⇒ cv = 0**；`irrRatio = 3/33 = 9% < 25%`。
 * ⇒ 结论是「窦性心律」。而"整齐节律里夹一次提前搏动"恰恰是**最常见**的那种发现。
 *
 * 本模块就是补这一段：**MAD 系判据对孤立离群点天生免疫，所以它看不见的，这里专门看。**
 *
 * ## ⚠⚠ 为什么判据跑在**原始序列**上、而不能用 `Hrv.filterArtifacts` 的输出
 *
 * `filterArtifacts` 的容差是 `±0.45×中位`，于是真早搏那两拍会被它**按伪迹处理掉**：
 * - 提前的那一拍（≈0.5×中位）→ 落到分支④「认不出来」⇒ **直接丢弃**；
 * - 代偿间歇（≈1.5×中位，刚好超 0.45）→ 落到分支②「漏检」⇒ **拆成两段 0.75×中位**。
 *
 * ⇒ 早搏的指纹在 `clean.rr` 里**已经不存在了**。
 *   ⚠ 这不是它的 bug —— 对 HRV 它就该这么干（那两拍确实不该进 RMSSD）。
 *   这里只是**不能拿它的输出去找早搏**。⇒ 判据用原始序列，
 *   而 `filterArtifacts` 只贡献一个东西：**伪迹占比**（当门控用）。
 *
 * ## 判据：为什么必须是「短 **且** 长 **且** 两拍合计≈2×中位」
 *
 * 同一个「短-长」观测有三种成因，只有一张表能把它们分开：
 *
 * | 成因 | 短的那拍 | 长的那拍 | **两拍合计** |
 * |---|---|---|---|
 * | **提前搏动（完全代偿）** | ≈0.5×中位 | ≈1.5×中位 | **≈2×中位** ✅ |
 * | T 波被当成一次心跳（多检） | ≈0.3×中位 | ≈0.7×中位 | ≈1×中位 ❌ |
 * | 漏检一拍 | 没有"短"那拍 | ≈2×中位 | — ❌ |
 *
 * ⇒ 「合计 ≈ 2×中位」这一条同时排掉了后两种。**这就是本模块的全部判别力所在**，
 *   也是它相对"只看短-长"唯一多出来的东西。
 *
 * ## ⚠ 仍然做不到的（写在这里，免得后来的人高估它）
 * - **分不开真提前搏动 与「一次漏检 + 一次多检相邻发生」**（两者都会造出合格的短-长对）。
 * - **分不开房性 / 室性**（需要 QRS 形态，而存储波形只降到 ~100 Hz = 10 ms）。
 * - **单次 30 秒抓不到偶发早搏**：30 秒 ≈ 35 拍，而 24 小时 Holter 才在 40%~75% 的人里
 *   录到至少一次、且其中 79% 一天不到 24 次 ⇒ 30 秒里的期望是 0.008 次。
 *   ⇒ 所以「没检出」**不能**说成"你没有"，文案必须是「本次未见」而不是"正常"。
 */
object Arrhythmia {

    // ═══════════════════════ 阈值（集中在这里，台架逐条断言） ═══════════════════════

    /**
     * 「提前」的判据：这一拍 ≤ 参考值的这个比例才算明显提前。
     *
     * ⚠ 取 **0.60** 是被**呼吸性窦性心律不齐**逼出来的，不是拍的：
     * 深呼吸时的正常摆动可以到 ±25%~35%（台架里有 ±26% 的交替用例），
     * 若阈值放到 0.75，`[0.74, 1.26]` 这种**完全正常**的交替就会同时满足
     * 「短 + 长 + 合计≈2×」而被误报。压到 0.60 之后正常摆动被排除在外。
     * 代价是**联律间期偏长的房性早搏会漏掉** —— 明知取舍，选保守：
     * 宁可说"本次未见"，也不要让一个深呼吸变成一次警报。
     */
    const val SHORT_RATIO = 0.60

    /** 「代偿间歇」的判据：紧随的那一拍 ≥ 参考值的这个比例。 */
    const val LONG_RATIO = 1.15

    /**
     * 两拍合计相对 `2 × 参考值` 的最大允许偏差（比例）。
     *
     * ⚠ 给到 0.25 是为了**同时容纳不完全代偿**：室性早搏多为完全代偿（合计≈2×），
     * 房性早搏常见不完全代偿（合计≈1.6~1.9×）。定太紧会把房性一类整体漏掉。
     * ⚠ 但不能再放宽了：到 0.35 时 T 波多检（合计≈1.0×）就已进入范围边界。
     */
    const val COMPENSATION_TOL = 0.25

    /**
     * 「孤立」判据的**正常带**：一对的前一拍与后两拍必须落在这个区间内。
     *
     * ⚠⚠ 这条挡的是**呼吸性窦性心律不齐（RSA）**：它是**连续摆动**，
     * 前一"对"的长间期 / 后一"对"的短间期会落到带外 ⇒ 整串被拒。
     * （`Hrv` 的注释里把这叫"两值交替"，并说明它正是深呼吸时的正常现象。）
     *
     * ⚠ 代价（明写）：**二联律（每个正常搏后跟一个提前搏动）整段不会被评估** ——
     * 它的序列本身就是 `0.5, 1.5, 0.5, 1.5 …`，每一对的前后都不在带内；
     * 而且**每一对都被伪迹模型改动过**（提前那拍丢弃、代偿那拍拆成两段）
     * ⇒ 伪迹占比 = 100% ⇒ 会先落进 [Result.TooNoisy]。
     *
     * ⚗ **两道防线都在这条路径上，且方向是安全的**：本模块宁可说"这段读不准"，
     * 也不会把它说成"未见节律不规则"。而那种程度的节律不齐**已经被现有的
     * `cv`/`irrRatio` 判据抓到了**（台架里有数字：交替 0.5/1.5 时 cv ≈ 0.74、
     * `irrRatio` = 100%，门分别是 0.15 / 0.25 ⇒ 会走「房颤提示 / 建议就医」那条路）。
     * ⇒ 本模块要补的是**现有判据看不见的那一格**（孤立一处、MAD 为 0），
     *   而不是取代它。
     */
    const val NEIGHBOR_LO = 0.85
    const val NEIGHBOR_HI = 1.15

    /**
     * 伪迹占比的**门控**：超过它 → 整段不评估。
     *
     * ⚗ 取值推导（不是拍的）：**一次孤立提前搏动自己就会贡献约 6% 的伪迹占比** ——
     * 提前那拍被分支④丢弃、代偿间歇被分支②拆成两段，共 2 个事件 / 34 个间期 ≈ 5.9%。
     * ⇒ 门槛必须**大于 6%**，否则真事件会被它自己的伪迹计数挡在门外；
     * 取 12% ≈ "允许再多两次其他伪迹"。再宽就会把漏检/多检造出的短-长对放进来。
     */
    const val MAX_ARTIFACT_RATIO = 0.12f

    /** 参与判据的最少间期数。与 `Hrv.MIN_BEATS` 同一个量级（30 秒 @ 70 次/分 ≈ 34 个）。 */
    const val MIN_INTERVALS = Hrv.MIN_BEATS

    /** 跨会话判定的窗口：看最近几次。 */
    const val REPEAT_WINDOW = 5

    /** 窗口里要有几次**可评估**、且其中几次**检出**，才算"重复出现"。 */
    const val REPEAT_MIN_HITS = 2

    // ═══════════════════════ 输出 ═══════════════════════

    /**
     * 一处「短 + 长 + 合计≈2×参考」的配对。
     *
     * ⚠ 它**不是**"一次早搏"的结论，只是"这里有一对符合该模式的间期"。
     * 界面文案必须按这个语义写。
     */
    data class Pair(
        /** 在**原始序列**（只做过生理区间过滤）里的下标。 */
        val atIndex: Int,
        val shortMs: Int,
        val longMs: Int,
        /** 全段参考值（生理区间内 RR 的中位数），毫秒。 */
        val refMs: Int,
    )

    /**
     * 单次会话的复查结果。
     *
     * ⚠ **只带结构化数据，不带任何已求值的本地化串** ——
     * 文案一律在渲染点取（`ResText` 那条教训：存已求值的串会让切语言永不重算）。
     */
    sealed interface Result {
        /** 可用的间期太少，连参考值都稳不住。 */
        data class TooFew(val intervals: Int, val need: Int) : Result

        /**
         * 伪迹占比过高 ⇒ **不评估**。
         *
         * ⚠ 这一支存在的意义就是"把伪迹报成提前搏动"这条最可能的路堵死 ——
         * 见 [MAX_ARTIFACT_RATIO] 的推导。
         */
        data class TooNoisy(val artifactRatio: Float, val limit: Float) : Result

        /** 评估完成。[pairs] 为空 = 本次**未见**符合该模式的间期。 */
        data class Screened(val pairs: List<Pair>, val refMs: Int) : Result {
            val found: Boolean get() = pairs.isNotEmpty()
        }
    }

    /**
     * 跨会话的「重复出现」判定。
     *
     * @param screened 窗口里**可评估**（`Screened`）的次数。
     * @param hits 其中**检出 ≥1 处**的次数。
     * @param persistent 是否够格提示。⚠ 判据见 [repeatVerdict]。
     */
    data class Verdict(val screened: Int, val hits: Int, val persistent: Boolean)

    // ═══════════════════════ 单次复查 ═══════════════════════

    /** 这一拍是否落在"正常带"内（[NEIGHBOR_LO], [NEIGHBOR_HI] × ref）。 */
    private fun nearNormal(rr: Int, ref: Double): Boolean =
        rr >= NEIGHBOR_LO * ref && rr <= NEIGHBOR_HI * ref

    /**
     * 复查一段逐拍间期。
     *
     * @param rrMs **心电来源（`Hrv.Source.WATCH_ECG`）**的 R 峰间期序列。
     *   ⚠ 调用方有义务先按来源过滤：PPG 的脉搏间期**不能**走这里 ——
     *   提前搏动常伴**弱脉/无脉**，在 PPG 上表现为"少了一拍"，
     *   与漏检**完全同形**。（"两来源两基线不许混"的更强版本：这里不是基线混，
     *   是把一个结构性不可判的量当成可判的。）
     */
    fun screen(rrMs: List<Int>): Result {
        if (rrMs.size < MIN_INTERVALS) return Result.TooFew(rrMs.size, MIN_INTERVALS)

        val inRange = rrMs.filter { it in Hrv.MIN_RR_MS..Hrv.MAX_RR_MS }
        if (inRange.size < MIN_INTERVALS) return Result.TooFew(inRange.size, MIN_INTERVALS)

        // 伪迹门控：只用 filterArtifacts 的**占比**，不用它的输出序列（理由见类注释）。
        val clean = Hrv.filterArtifacts(rrMs)
        if (clean.artifactRatio > MAX_ARTIFACT_RATIO) {
            return Result.TooNoisy(clean.artifactRatio, MAX_ARTIFACT_RATIO)
        }

        val ref = Hrv.median(inRange)
        if (ref <= 0.0) return Result.TooFew(0, MIN_INTERVALS)

        val pairs = ArrayList<Pair>()
        for (i in 0 until inRange.size - 1) {
            val s = inRange[i]
            val l = inRange[i + 1]
            if (s > SHORT_RATIO * ref) continue
            if (l < LONG_RATIO * ref) continue
            // 「合计 ≈ 2×参考」——把 T 波多检与孤立漏检排掉的那一条
            if (abs((s + l).toDouble() - 2.0 * ref) > COMPENSATION_TOL * ref) continue
            // 「孤立」——把呼吸性窦性心律不齐（连续摆动）整串排掉
            if (i > 0 && !nearNormal(inRange[i - 1], ref)) continue
            if (i + 2 < inRange.size && !nearNormal(inRange[i + 2], ref)) continue
            pairs.add(Pair(atIndex = i, shortMs = s, longMs = l, refMs = ref.roundToInt()))
        }
        return Result.Screened(pairs, ref.roundToInt())
    }

    // ═══════════════════════ 跨会话 ═══════════════════════

    /**
     * 「近几次里重复出现才算数」（纪律 3）。
     *
     * @param recent 最近的会话结果，**新的在前**（与库里 `ORDER BY measuredAt DESC` 一致）。
     *   只取前 [REPEAT_WINDOW] 个。
     *
     * ## ⚠⚠ 判据的核心：`hits` 只数「**可评估**且检出」的那些次
     * 拍数不足 / 伪迹过多的会话**既不进分子也不进分母** ——
     * 它们的含义是"**这段读不准**"，不是"这次没有"。
     * 把它们算进分母，等于把"没测到"当成"测到了没发现"，那是两件事。
     *
     * ## ⚠ 一条**被删掉**的多余条件（记下来，别再写回去）
     * 我一开始还写了 `screened >= REPEAT_MIN_HITS && hits >= REPEAT_MIN_HITS`，
     * 并在注释里举了个例子说它挡的是"最近 5 次里只有 1 次可评估而它检出了"。
     * 负向验证时发现**它不可能被任何用例区分出来**：`hits ≤ screened` 恒成立，
     * 所以 `hits >= 2` 已经蕴含"至少两次可评估"。
     * ⇒ 一条永远为真的条件不是防线，是**误导**（后来的人会以为它挡了什么）。
     *   已删；下面只留 `hits`。
     *
     * ## 为什么"偶发"能被滤掉
     * 偶发早搏在 30 秒里的期望是 0.008 次，要连续两次都撞上概率极低 ⇒
     * 真能连撞两次的，多半是**频发**，而"频发才有临床意义"正是临床口径。
     */
    fun repeatVerdict(recent: List<Result>): Verdict {
        val window = recent.take(REPEAT_WINDOW)
        val screened = window.count { it is Result.Screened }
        val hits = window.count { it is Result.Screened && it.found }
        return Verdict(screened = screened, hits = hits, persistent = hits >= REPEAT_MIN_HITS)
    }
}
