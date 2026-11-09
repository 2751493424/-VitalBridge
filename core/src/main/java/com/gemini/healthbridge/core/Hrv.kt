package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 心率变异性（HRV）：输入一串**逐拍间期**，输出时域指标（以及时长足够时的频域指标）。
 *
 * ## 为什么它必须先解决"输入够不够"，再谈算法
 * HRV 的每一个指标都对**记录时长**极度敏感：
 *  - RMSSD / pNN50 是逐拍差分，30 秒（约 30~40 拍）就能算，属于文献里的"超短时 HRV"；
 *  - SDNN 随窗口变长单调上升（它统计的是整段的慢变趋势），30 秒的 SDNN **天然偏小**，
 *    只能跟同长度的记录互相比，不能跟"5 分钟标准值"比；
 *  - LF 频带下限 0.04Hz 意味着**一个完整周期就要 25 秒**，想估出功率至少要 4~5 个周期
 *    ≈ 2 分钟；30 秒的记录去算 LF 纯属编数字。
 *
 * 所以本模块把"时长"当成**一等公民**：先算出信度分级 [Tier]，再决定给哪些指标，
 * 给不了的在界面上写明"需要多久"，而不是返回一个看起来正常的数。
 *
 * ## 输入从哪来
 * 目前唯一的来源是手表心电（[Source.WATCH_ECG]）—— 心电是**真正的 R 峰间期**，
 * 而 PPG 给的逐拍间期受脉搏波传导与形态影响，两者不能混在一个序列里。
 * 三星传感器 SDK 的 `ValueKey.HeartRateSet.IBI_LIST` 能直接给出 PPG 的逐拍间期
 * （另一条可接的来源），所以 [Source] 保留枚举、来源要**入库**，将来接进来时
 * 界面能区分"这是心电 RR"还是"这是 PPG IBI"。
 *
 * ## 伪迹处理是**第一位的**，不是预处理里的添头
 * 手腕上 30 秒的逐拍间期里，一次漏检就会造出一个约 2 倍长的 RR，一次多检会造出两个
 * 约 1/2 长的 RR。RMSSD 是差分的平方和，**一个 500ms 的假跳变就能把 RMSSD 从 40 抬到 300**
 * —— 那个数字仍然"长得像 HRV"，但它是伪迹。本模块的做法：
 *  ① 生理区间外 → 丢；
 *  ② 约 2~3 倍参考值 → **拆**（漏检），比丢掉更接近真实；
 *  ③ 相邻两拍之和约等于参考值 → **合**（多检）；
 *  ④ 其余 → 丢；
 *  ⑤ 修正/丢弃的总占比 > [MAX_ARTIFACT_RATIO] → **拒答**。修正占比本身就是
 *     "这段数据有多少是我编出来的"的度量，超过两成就不能当测量结果用。
 */
object Hrv {

    // ═══════════════════════ 来源与分级 ═══════════════════════

    /** RR 间期的来源标记（入库字段，界面据此说明口径）。 */
    object Source {
        /** 手表心电的 R 峰间期（毫秒）。真正的逐拍间期。 */
        const val WATCH_ECG = "watch_ecg"

        /**
         * 手表 PPG 的逐拍间期（毫秒）。**当前未接入**，仅占位：
         * 将来从 `ValueKey.HeartRateSet.IBI_LIST` 取到时用它。
         */
        const val WATCH_PPG = "watch_ppg"
    }

    /** 信度分级。由记录时长决定，界面必须显示 —— 它是这些数字能不能互相比较的前提。 */
    object Tier {
        /** < 1 分钟。只有 RMSSD / pNN50 之类逐拍指标可用，SDNN 必然偏小。 */
        const val ULTRA_SHORT = "ultra_short"

        /** 1~5 分钟。短时 HRV 的常规区间，LF/HF 从 2 分钟起才给。 */
        const val SHORT = "short"

        /** ≥ 5 分钟。文献里的"标准短时 HRV"。 */
        const val STANDARD = "standard"
    }

    fun tierOf(spanMs: Long): String = when {
        spanMs < 60_000L -> Tier.ULTRA_SHORT
        spanMs < 300_000L -> Tier.SHORT
        else -> Tier.STANDARD
    }

    fun tierLabel(tier: String): String = when (tier) {
        Tier.ULTRA_SHORT -> Text.of("s_e7dd54ad")
        Tier.SHORT -> Text.of("s_fcf7ba99")
        Tier.STANDARD -> Text.of("s_9284bf3f")
        else -> Text.of("s_00354263")
    }

    /** 测量时的活动状态（决定这次数字能不能与静息基线比）。 */
    object RestState {
        /** 活动档位为静息 —— 可以用来跟自己的基线比。 */
        const val RESTING = "resting"

        /** 测量时人在轻度活动。数字仍在，但反映的是"边走边测"。 */
        const val MOVING_LIGHT = "moving_light"

        /** 测量时人明显在活动 → [analyze] 直接拒答。 */
        const val MOVING = "moving"

        /** 活动档位拿不到（刚打开 App 不足一个窗口）。**不算静息**。 */
        const val UNKNOWN = "unknown"
    }

    fun restStateOf(activityLevel: String?): String = when (activityLevel) {
        Activity.Level.REST -> RestState.RESTING
        Activity.Level.LIGHT -> RestState.MOVING_LIGHT
        Activity.Level.MODERATE, Activity.Level.VIGOROUS -> RestState.MOVING
        else -> RestState.UNKNOWN
    }

    fun restLabel(rest: String): String = when (rest) {
        RestState.RESTING -> Text.of("s_004dd2fa")
        RestState.MOVING_LIGHT -> Text.of("s_52d5dc28")
        RestState.MOVING -> Text.of("s_1cc4e238")
        else -> Text.of("s_aedbc92c")
    }

    // ═══════════════════════ 门限（全部有出处） ═══════════════════════

    /** 生理区间（毫秒）：300ms=200bpm，2000ms=30bpm。超出必是伪迹。 */
    const val MIN_RR_MS = 300
    const val MAX_RR_MS = 2000

    /**
     * 接受带：相对参考值的偏离上限。**45% 比文献里常见的 20~30% 宽得多，这是刻意的**：
     *  - 这条窄带要抓的两种伪迹（漏检 = 2 倍长、多检 = 1/2 长）在 [filterArtifacts] 里
     *    是用**比值**判的（k 倍参考值），不依赖这条幅度带；
     *  - 幅度带一旦收窄，第一个被误删的就是**呼吸性窦性心律不齐** —— 深呼吸时 RR 的
     *    摆幅能到 ±25%~30%，而那是这个模块要测的信号本身。
     *
     * 台架里跑过「参考值策略 × 容差」的网格（均值参考/中位数参考/全局中位数 × 30%/45%）：
     * 只有「**全局中位数 + 45%**」这一组同时满足 —— RSA ±200ms 不误删、长序列两值交替
     * 不误删、心率翻倍的阶跃不误删，而漏检/多检/连续漏检/伪迹突发四种伪迹全部识别。
     * 均值参考会**漂移**：两值交替时窗口的奇偶会让参考值跟着晃，把交替的一半判成伪迹。
     */
    const val ARTIFACT_TOLERANCE = 0.45

    /** 修正 + 丢弃的占比上限。超过说明这段数据里「推断」的成分太多。 */
    const val MAX_ARTIFACT_RATIO = 0.20F

    /** 出报告所需的最少有效心搏数。少于此连 RMSSD 都稳不住。 */
    const val MIN_BEATS = 20

    /** 出报告所需的最短跨度（毫秒）。 */
    const val MIN_SPAN_MS = 10_000L

    /**
     * 频域两条时长门限，由**频带边界反推**而不是拍脑袋：
     *  - HF 下限 0.15Hz → 一个周期 6.7 秒，估功率取 8~10 个周期 ≈ 60 秒；
     *  - LF 下限 0.04Hz → 一个周期 25 秒，取 4~5 个周期 ≈ 120 秒。
     */
    const val HF_MIN_SPAN_MS = 60_000L
    const val LF_MIN_SPAN_MS = 120_000L

    /** 频带边界（Hz），Task Force 1996 的标准划分。VLF 不报告（需要 ≥5 分钟）。 */
    const val VLF_LO = 0.0033
    const val LF_LO = 0.04
    const val LF_HI = 0.15
    const val HF_HI = 0.40

    /** 周期图频率栅格的过采样倍数（1 = 恰好 1/时长 一个点）。 */
    private const val LS_OVERSAMPLE = 4

    /**
     * RMSSD 的物理上限（毫秒）。超过它不是"变异大"，而是**伪迹没清干净**，
     * 或者存在没被识别出来的心律失常。真机经验：手腕段数据没做伪迹过滤时，
     * RMSSD 会轻松冲到 500ms 以上。
     */
    const val MAX_RMSSD_MS = 300.0

    // ═══════════════════════ 结果类型 ═══════════════════════

    /**
     * 分析结果。**"给不了"是一种正常结果**，与"给得出"并列，不是一个错误分支：
     * 拿不到 5 分钟的静息数据时，正确的输出是"为什么给不了"，不是给一个默认值。
     */
    sealed interface Result {
        val source: String

        data class Ok(
            override val source: String,
            /** 有效心搏数（= 参与统计的 RR 个数）。 */
            val beats: Int,
            /** 分析跨度（毫秒）= 有效 RR 之和。 */
            val spanMs: Long,
            val meanRrMs: Double,
            /** SDNN：全部 RR 的标准差（样本标准差，除以 N-1）。单位毫秒。 */
            val sdnnMs: Double,
            /** RMSSD：相邻 RR 差值的均方根。单位毫秒。它对伪迹最敏感，所以是主指标。 */
            val rmssdMs: Double,
            /** pNN50：相邻 RR 差值绝对值 > 50ms 的占比，百分数（0~100）。 */
            val pnn50Pct: Double,
            /** 变异系数 = SDNN / 平均 RR，百分数。跨心率可比，是"归一化后的 SDNN"。 */
            val cvPct: Double,
            val hrMean: Double,
            val hrMin: Int,
            val hrMax: Int,
            /** 信度分级，见 [Tier]。 */
            val tier: String,
            /** 活动状态，见 [RestState]。 */
            val rest: String,
            /** 被拆/合修正与直接丢弃的心搏数。 */
            val corrected: Int,
            val removed: Int,
            /** 伪迹占比（0~1）。它的倒数就是"这个 RMSSD 有多可信"。 */
            val artifactRatio: Float,
            /** 有效 RR 序列（毫秒），供画 tachogram 与复查。 */
            val rrMs: List<Int>,
            /** 频域结果；时长不够时为 null（原因见 [Result.Ok.freqNote]）。 */
            val freq: Freq?,
            /** 频域为什么给不了/给了哪些（永远有话说，界面直接显示）。 */
            val freqNote: String,
        ) : Result

        data class NotApplicable(
            override val source: String,
            val reason: String,
        ) : Result
    }

    /**
     * 频域结果。放在频带里的功率单位是 **ms²**（RR 的方差量纲）。
     *
     * [lf] 与 [lfHf] 可空：跨度 60~120 秒时 LF 不足以估出，此时只给 HF，
     * 界面显示"LF 需要 ≥2 分钟"。**刻意不做"凑一个 LF"的兜底。**
     */
    data class Freq(
        val hfMs2: Double,
        val lfMs2: Double?,
        val lfHf: Double?,
        val lfNu: Double?,
        val hfNu: Double?,
        /** 0.04~0.40Hz 的总功率（ms²）。 */
        val totalMs2: Double,
        /** 整段 RR 的方差（ms²），即总功率的上界。 */
        val varianceMs2: Double,
        /** 呼吸性窦性心律不齐（RSA）最强的那个频率（Hz），供"呼吸率粗估"参考。 */
        val hfPeakHz: Double?,
    ) {
        /** LF/HF 是否算出来了（有些记录只有 HF）。 */
        val hasLfHf: Boolean get() = lfHf != null
    }

    // ═══════════════════════ 伪迹处理 ═══════════════════════

    /** 伪迹处理的结果。 */
    data class Clean(
        val rr: List<Int>,
        val corrected: Int,
        val removed: Int,
        val artifactRatio: Float,
    )

    /**
     * 逐拍伪迹修正。**参考值的选择比那几条判据更要紧**，这里是实测选出来的：
     *
     * 参考值 = **全部落在生理区间内的 RR 的中位数**（整段一个值，不随时间变化）。
     *  - 曾经用过"已接受序列最近 5 拍的中位数"，它在**两值交替**的序列上会崩：两值交替时
     *    中位数必然等于其中某一个值，参考值于是跟着交替，把一半的拍判成伪迹 ——
     *    而两值交替恰恰就是深呼吸时的**呼吸性窦性心律不齐**。
     *  - 改成"最近 15 拍的均值"也只是把问题推后：两值交替时窗口里的奇偶会让均值上下晃，
     *    台架里 ±26% 的 15 对交替仍被误删 15 拍。
     *  - 全局中位数不漂移，且对**多达一半**的伪迹免疫（超过一半时本来也该拒答）。
     *    代价是"记录期间心率发生 45% 以上的持变"会被判成伪迹 —— 但那本来就不是静息平稳
     *    节律，拒答才是对的，拒答文案里也写明了这一条。
     *  - 参考值只用生理区间内的值算；被修正/丢弃的拍不会反过来影响它（它本来就已经算完了）。
     */
    // ═══════════════════ 独立一致性校验（2026-09-24 加）═══════════════════
    //
    // ## 为什么需要它
    // PPG 会话与心电会话有一处根本不同：**心电有一个独立的规整度信号**
    // （`ecgRegular`，`analyze` 会拒答不齐的节律），而 PPG 那条路**原本一个都没有**。
    // 于是只能靠伪迹模型自己证明自己 —— 模型漏掉的东西它报不出来。
    //
    // `HeartRateSet` 里除了 `IBI_LIST` 还有一个**独立算出的 `HEART_RATE`**（同一个包里下发、
    // 我们之前收下就扔了）。拿它跟"逐拍间期的中位心率"对一下，就等于多一道**不依赖伪迹模型**
    // 的校验：整体错位（漏拍、重复、单位错误、序列拼接错）会让两者明显分家。
    //
    // ## ⚠ 它不是万能的（别高估）
    // 这个 `HEART_RATE` 也是从同一路光信号推出来的，所以**不能证明逐拍细节正确** ——
    // 它抓的是"整体量级/整体错位"，不是"某一拍是不是伪迹"。该由伪迹门控管的事还是它管。

    /**
     * 逐拍间期序列的中位心率与"独立报出的心率"允许的最大相对偏差（百分数）。
     *
     * 取值依据：两次真机 PPG 会话的实测偏差都约 **5%**（中位 848ms⇒70.8 次/分 vs
     * SDK 报 74，差 4.4%；另一次中位 848 vs 74 同理）；而心率在 2 分钟会话内的自然漂移
     * 本身也有几个百分点。**12% 是"比实测正常偏差宽一倍多、又能抓住明显错位"**，
     * 不是为了卡得紧 —— 卡紧会把正常会话误报成异常。
     */
    const val REF_HR_MAX_DEVIATION = 0.12

    /**
     * 序列中位心率相对 [refHr] 的偏差（百分数）。
     *
     * [refHr] 拿不到（null / ≤0）或序列为空时返回 **null**，而不是 0 ——
     * 0 的含义是"完全一致"，那是最强的正面结论，不能拿它冒充"没校验过"。
     */
    fun hrDeviationPct(rrMs: List<Int>, refHr: Int?): Double? {
        if (refHr == null || refHr <= 0 || rrMs.isEmpty()) return null
        val med = median(rrMs)
        if (med <= 0.0) return null
        val hr = 60_000.0 / med
        return kotlin.math.abs(hr - refHr) / refHr * 100.0
    }

    /**
     * 只要一个 RMSSD（毫秒），不要完整报告。
     *
     * ## 为什么不直接调 [analyze]
     *
     * 血压配对窗口要的是「最近一次会话的 HRV 数值」这一**个标量**，而 [analyze]
     * 返回的是带文案（[Result.NotApplicable] 里全是已求值的多语言串）的报告对象 ——
     * 拿它当特征入库，等于把**界面文案**写进训练数据（换语言后旧样本的"特征"就变了）。
     *
     * ## 返回 null 的四种情况（全部是「这一维缺」，不许用 0 或均值顶替）
     *
     * 1. 原始间期为空
     * 2. 伪迹修正后有效搏数 < [MIN_BEATS]（连 RMSSD 都稳不住）
     * 3. 伪迹占比 > [MAX_ARTIFACT_RATIO]（差分指标对伪迹极敏感，一个 500ms 假跳变
     *    就能把 RMSSD 从 40 抬到 300）
     * 4. RMSSD > [MAX_RMSSD_MS]（那不是"变异大"，是伪迹没清干净）
     *
     * ⚠ 4 与 3 是**两道独立的门**：一段全是伪迹的序列可能算出不算离谱的 RMSSD，
     *   只查 3 会放它过去。
     */
    fun quickRmssdMs(rawRrMs: List<Int>): Int? {
        if (rawRrMs.isEmpty()) return null
        val clean = filterArtifacts(rawRrMs)
        if (clean.rr.size < MIN_BEATS) return null
        if (clean.artifactRatio > MAX_ARTIFACT_RATIO) return null
        var dsq = 0.0
        for (k in 1 until clean.rr.size) {
            val d = (clean.rr[k] - clean.rr[k - 1]).toDouble()
            dsq += d * d
        }
        val m = (clean.rr.size - 1).coerceAtLeast(1)
        val rmssd = sqrt(dsq / m)
        if (rmssd > MAX_RMSSD_MS) return null
        return rmssd.roundToInt()
    }

    fun filterArtifacts(raw: List<Int>): Clean {
        if (raw.isEmpty()) return Clean(emptyList(), 0, 0, 0f)
        val inRange = raw.filter { it in MIN_RR_MS..MAX_RR_MS }
        if (inRange.isEmpty()) return Clean(emptyList(), 0, raw.size, 1f)
        val ref = median(inRange)
        val out = ArrayList<Int>(raw.size)
        var corrected = 0
        var removed = 0
        var i = 0
        while (i < raw.size) {
            val cur = raw[i]
            // ① 落在生理区间内、且贴着参考值 → 直接接受。
            if (cur in MIN_RR_MS..MAX_RR_MS &&
                abs(cur - ref) <= ARTIFACT_TOLERANCE * ref
            ) {
                out.add(cur)
                i++
                continue
            }
            // ② 漏检：cur ≈ k × ref（k = 2 或 3）。拆成 k 段而不是丢掉 ——
            //    丢掉会让 tachogram 少一段、并让相邻差分凭空变大。
            var split = 0
            if (cur > ref) {
                for (k in 2..3) {
                    val part = cur.toDouble() / k
                    if (abs(part - ref) <= ARTIFACT_TOLERANCE * ref &&
                        part >= MIN_RR_MS && part <= MAX_RR_MS
                    ) {
                        split = k
                        break
                    }
                }
            }
            if (split > 0) {
                val base = cur / split
                repeat(split) { out.add(base) }
                corrected++
                i++
                continue
            }
            // ③ 多检：cur 偏短，且与**紧随其后**的一拍合起来才是真实的一拍。
            //    两个约 1/2 长的 RR 相加 ≈ ref，是"一个 R 波被检出两次"的指纹。
            if (cur < ref && i + 1 < raw.size) {
                val sum = cur + raw[i + 1]
                if (sum in MIN_RR_MS..MAX_RR_MS &&
                    abs(sum - ref) <= ARTIFACT_TOLERANCE * ref
                ) {
                    out.add(sum)
                    corrected++
                    i += 2
                    continue
                }
            }
            // ④ 认不出来 → 丢弃（绝不"猜"一个值放进去）。
            removed++
            i++
        }
        val events = corrected + removed
        val ratio = if (raw.isEmpty()) 0f else events.toFloat() / raw.size
        return Clean(out, corrected, removed, ratio)
    }

    /** 中位数（偶数个取中间两个的均值）。**用中位数不用均值**：一次漏检就能把均值拉飞。 */
    fun median(values: List<Int>): Double {
        if (values.isEmpty()) return 0.0
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2].toDouble() else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    // ═══════════════════════ 主入口 ═══════════════════════

    /**
     * 分析一串逐拍间期。
     *
     * @param rawRrMs 原始逐拍间期（毫秒），**不要预先过滤** —— 过滤是这一步的职责，
     *   预先丢掉"看起来太长"的那一拍，恰好就是把"漏检"这条线索销毁掉。
     * @param source 见 [Source]。
     * @param activityLevel 测量时的活动档位（见 [Activity.Level]），null = 拿不到。
     * @param ecgRegular 心电的节律结论（`EcgAnalyzer.Result.regular`）。
     *   **false 时必须拒答**：房颤/早搏的 RR 变异来自心律失常本身，不是自主神经张力，
     *   这时 RMSSD 会很大却毫无 HRV 的意义。
     */
    fun analyze(
        rawRrMs: List<Int>,
        source: String = Source.WATCH_ECG,
        activityLevel: String? = null,
        ecgRegular: Boolean? = null,
    ): Result {
        val rest = restStateOf(activityLevel)
        if (rest == RestState.MOVING) {
            return Result.NotApplicable(
                source,
                Text.fmt("s_e68f7923", Activity.label(activityLevel)) +
                    Text.of("s_98e5c4fc") +
                    Text.of("s_c869cb06"),
            )
        }
        if (ecgRegular == false) {
            return Result.NotApplicable(
                source,
                Text.of("s_c6e8c7a7") +
                    Text.of("s_70fd5793") +
                    Text.of("s_e69fba24"),
            )
        }

        val clean = filterArtifacts(rawRrMs)
        val rr = clean.rr
        // 先判伪迹比例再判拍数：拍数不足往往是"全被当成伪迹丢光了"的结果，
        // 这时说"有效心搏不足"是误导，真正的原因在伪迹那一句里。
        if (clean.artifactRatio > MAX_ARTIFACT_RATIO) {
            return Result.NotApplicable(
                source,
                Text.fmt("s_494665c5", (clean.artifactRatio * 100).roundToInt(), clean.corrected) +
                    Text.fmt("s_1097ca05", clean.removed, (MAX_ARTIFACT_RATIO * 100).roundToInt()) +
                    Text.of("s_38ceba63") +
                    Text.of("s_d63e0580") +
                    Text.fmt("s_6208c22f", (ARTIFACT_TOLERANCE * 100).roundToInt()) +
                    Text.of("s_bfb7987f"),
            )
        }
        if (rr.size < MIN_BEATS) {
            return Result.NotApplicable(
                source,
                Text.fmt("s_f4ed055b", rr.size, MIN_BEATS) +
                    Text.of("s_5295c44b"),
            )
        }

        val spanMs = rr.sumOf { it.toLong() }
        if (spanMs < MIN_SPAN_MS) {
            return Result.NotApplicable(
                source,
                Text.fmt("s_c323366d", sec1(spanMs)) +
                    Text.fmt("s_e9be9c18", MIN_SPAN_MS / 1000),
            )
        }

        val n = rr.size
        val meanRr = rr.average()
        // SDNN 用样本标准差（除以 N-1）：N-1 是无偏估计的分母，Task Force 1996 的口径。
        var sq = 0.0
        for (v in rr) {
            val d = v - meanRr
            sq += d * d
        }
        val sdnn = sqrt(sq / (n - 1))
        // RMSSD：M = N-1 个相邻差值。
        var dsq = 0.0
        var nn50 = 0
        for (k in 1 until n) {
            val d = (rr[k] - rr[k - 1]).toDouble()
            dsq += d * d
            if (abs(d) > 50.0) nn50++
        }
        val m = (n - 1).coerceAtLeast(1)
        val rmssd = sqrt(dsq / m)
        val pnn50 = nn50 * 100.0 / m
        if (rmssd > MAX_RMSSD_MS) {
            return Result.NotApplicable(
                source,
                Text.fmt("s_0c67abd7", rmssd.roundToInt(), MAX_RMSSD_MS.roundToInt()) +
                    Text.of("s_75a559a6") +
                    Text.of("s_c9d15a8e"),
            )
        }

        val tier = tierOf(spanMs)
        val freqPlan = planFreq(spanMs)
        val freq = freqPlan?.let { computeFreq(rr, it) }
        val note = when {
            spanMs >= LF_MIN_SPAN_MS -> Text.of("s_0d8a73ea")
            spanMs >= HF_MIN_SPAN_MS ->
                Text.of("s_f1123087") +
                    Text.fmt("s_c97204d2", LF_MIN_SPAN_MS / 1000) +
                    Text.fmt("s_8beb368e", sec0(spanMs))
            else ->
                Text.fmt("s_d96d556d", HF_MIN_SPAN_MS / 1000) +
                    Text.of("s_d4297276") +
                    Text.of("s_5451ff6d")
        }

        return Result.Ok(
            source = source,
            beats = n,
            spanMs = spanMs,
            meanRrMs = meanRr,
            sdnnMs = sdnn,
            rmssdMs = rmssd,
            pnn50Pct = pnn50,
            cvPct = if (meanRr > 0) sdnn / meanRr * 100.0 else 0.0,
            hrMean = if (meanRr > 0) 60000.0 / meanRr else 0.0,
            hrMin = (60000.0 / rr.max()).roundToInt(),
            hrMax = (60000.0 / rr.min()).roundToInt(),
            tier = tier,
            rest = rest,
            corrected = clean.corrected,
            removed = clean.removed,
            artifactRatio = clean.artifactRatio,
            rrMs = rr,
            freq = freq,
            freqNote = note,
        )
    }

    // ═══════════════════════ 频域：Lomb-Scargle ═══════════════════════

    /**
     * 为什么用 Lomb-Scargle 而不是 FFT：
     * RR 序列天然是**非均匀采样**的（每个点间隔就是 RR 本身），要上 FFT 就得先插值到
     * 固定网格，而插值会平滑掉高频、把 HF 功率压低。Lomb-Scargle 直接处理非均匀时间点，
     * 且对少量缺拍不敏感 —— 这正是逐拍数据的常态。
     */
    private object FreqPlan {
        val HF_ONLY = 1
        val FULL = 2
    }

    private fun planFreq(spanMs: Long): Int? = when {
        spanMs >= LF_MIN_SPAN_MS -> FreqPlan.FULL
        spanMs >= HF_MIN_SPAN_MS -> FreqPlan.HF_ONLY
        else -> null
    }

    private fun computeFreq(rr: List<Int>, plan: Int): Freq {
        val n = rr.size
        // 时间轴：第 k 个 R 峰出现在 Σrr[0..k] 处（秒）。整体平移不影响频谱。
        val t = DoubleArray(n)
        var acc = 0.0
        for (k in 0 until n) {
            acc += rr[k]
            t[k] = acc / 1000.0
        }
        val spanSec = t[n - 1] - t[0]
        // 去均值 + 线性去趋势：慢趋势（体温、体位、缓慢的自主神经漂移）会在最低频堆出
        // 一堆能量，把 LF 抬起来。线性项只影响 f < 1/(2T)，不会啃掉 HF。
        val y = detrend(t, DoubleArray(n) { rr[it].toDouble() })
        val mean = y.average()
        val x = DoubleArray(n) { y[it] - mean }
        val variance = if (n > 1) x.sumOf { it * it } / (n - 1) else 0.0

        // 频率栅格：下限取 1/时长（比它更低的分量在这么短的记录里分不出来）。
        val fMin = (1.0 / spanSec).coerceAtLeast(VLF_LO)
        val df = 1.0 / (LS_OVERSAMPLE * spanSec)
        val count = ((0.5 - fMin) / df).toInt().coerceAtLeast(4)
        val freqs = DoubleArray(count + 1) { fMin + it * df }
        val power = lombScargle(t, x, freqs)

        // 归一化：让 ∫P df 恰好等于序列方差。
        // 为什么可以这样定义：Lomb-Scargle 的**绝对**标定依赖采样密度，非均匀采样下
        // 没有唯一的物理放大器；而 HRV 要的所有频域量（LF/HF、归一化单位）都只用到
        // **相对**分布，只要能保证"全带积分 = 总方差"这个守恒关系，
        // 频带功率的单位就是 ms²、且 LF+HF 不会超过总方差。这个守恒关系在台架里被断言。
        val area = trapz(freqs, power)
        val scale = if (area > 0) variance / area else 0.0
        val psd = DoubleArray(power.size) { power[it] * scale }

        val hfLoIdx = indexAt(freqs, LF_HI)
        val full = plan == FreqPlan.FULL
        val hf = bandPower(freqs, psd, LF_HI, HF_HI)
        val lf = if (full) bandPower(freqs, psd, LF_LO, LF_HI) else null
        val total = bandPower(freqs, psd, LF_LO, HF_HI)
        val lfHf = if (lf != null && hf > 0) lf / hf else null
        val lfNu = if (lf != null && (lf + hf) > 0) lf / (lf + hf) * 100.0 else null
        val hfNu = if (lfNu != null) 100.0 - lfNu else null
        // HF 带的峰值频率 = 呼吸性窦性心律不齐最强处。**不做呼吸率换算**
        // （要由呼吸率反推还得知道呼吸对 RSA 的传递函数），只作为"峰值在哪"的事实。
        var peakHz: Double? = null
        var peak = -1.0
        for (k in hfLoIdx until freqs.size) {
            if (freqs[k] > HF_HI) break
            if (psd[k] > peak) {
                peak = psd[k]
                peakHz = freqs[k]
            }
        }
        return Freq(
            hfMs2 = hf,
            lfMs2 = lf,
            lfHf = lfHf,
            lfNu = lfNu,
            hfNu = hfNu,
            totalMs2 = total,
            varianceMs2 = variance,
            hfPeakHz = peakHz,
        )
    }

    /**
     * Lomb-Scargle 周期图（Scargle 1982 的经典式，去掉了 σ² 归一化 ——
     * 单位是 "ms² × 频率"，绝对标定由 [computeFreq] 的守恒归一化统一负责）。
     */
    private fun lombScargle(t: DoubleArray, x: DoubleArray, freqs: DoubleArray): DoubleArray {
        val n = t.size
        val out = DoubleArray(freqs.size)
        for (k in freqs.indices) {
            val w = 2.0 * Math.PI * freqs[k]
            var s2t = 0.0
            var c2t = 0.0
            for (i in 0 until n) {
                val a = 2.0 * w * t[i]
                s2t += sin(a)
                c2t += cos(a)
            }
            // τ 让正弦/余弦基在这一频率上正交于常数项（这就是 LS 比"直接最小二乘拟合"稳的原因）。
            val tau = if (abs(s2t) < 1e-12 && abs(c2t) < 1e-12) 0.0
            else atan2(s2t, c2t) / (2.0 * w)
            var sc = 0.0
            var ss = 0.0
            var cc = 0.0
            var sss = 0.0
            for (i in 0 until n) {
                val a = w * (t[i] - tau)
                val c = cos(a)
                val s = sin(a)
                sc += x[i] * c
                ss += x[i] * s
                cc += c * c
                sss += s * s
            }
            out[k] = 0.5 * ((sc * sc) / cc.coerceAtLeast(1e-12) + (ss * ss) / sss.coerceAtLeast(1e-12))
        }
        return out
    }

    /** 去均值 + 最小二乘线性去趋势。 */
    private fun detrend(t: DoubleArray, y: DoubleArray): DoubleArray {
        val n = t.size
        if (n < 3) return y
        val mt = t.average()
        val my = y.average()
        var num = 0.0
        var den = 0.0
        for (i in 0 until n) {
            val dt = t[i] - mt
            num += dt * (y[i] - my)
            den += dt * dt
        }
        if (den <= 0) return DoubleArray(n) { y[it] - my }
        val slope = num / den
        return DoubleArray(n) { y[it] - (my + slope * (t[it] - mt)) }
    }

    /** 梯形积分。 */
    private fun trapz(x: DoubleArray, y: DoubleArray): Double {
        var s = 0.0
        for (k in 1 until x.size) s += (y[k] + y[k - 1]) / 2.0 * (x[k] - x[k - 1])
        return s
    }

    /**
     * 频带功率：梯形积分。带内保留全部栅格点，两端各按线性插值补一个**正好落在带边界上**
     * 的点 —— 否则带的边缘会被截到最近一个栅格点，短记录上栅格稀疏，误差能到百分之几。
     */
    private fun bandPower(freqs: DoubleArray, psd: DoubleArray, lo: Double, hi: Double): Double {
        if (hi <= lo) return 0.0
        val fs = ArrayList<Double>(freqs.size + 2)
        val ps = ArrayList<Double>(freqs.size + 2)
        fs.add(lo)
        ps.add(interp(freqs, psd, lo))
        for (k in freqs.indices) {
            if (freqs[k] > lo && freqs[k] < hi) {
                fs.add(freqs[k])
                ps.add(psd[k])
            }
        }
        fs.add(hi)
        ps.add(interp(freqs, psd, hi))
        var s = 0.0
        for (k in 1 until fs.size) s += (ps[k] + ps[k - 1]) / 2.0 * (fs[k] - fs[k - 1])
        return s
    }

    private fun interp(freqs: DoubleArray, psd: DoubleArray, f: Double): Double {
        if (f <= freqs.first()) return psd.first()
        if (f >= freqs.last()) return psd.last()
        for (k in 1 until freqs.size) {
            if (freqs[k] >= f) {
                val f0 = freqs[k - 1]
                val f1 = freqs[k]
                val w = if (f1 > f0) (f - f0) / (f1 - f0) else 0.0
                return psd[k - 1] + (psd[k] - psd[k - 1]) * w
            }
        }
        return psd.last()
    }

    private fun indexAt(freqs: DoubleArray, f: Double): Int {
        for (k in freqs.indices) if (freqs[k] >= f) return k
        return freqs.size - 1
    }

    // ═══════════════════════ RR 序列的编解码 ═══════════════════════

    /**
     * 一条记录最多携带多少个间期。约 30 分钟 @60bpm 的余量 —— 远超当前任何一条采集路径
     * （手表心电固定 30 秒），但必须有个上限：同步帧是一行 JSON，没有上限的话
     * 一条异常记录就能把蓝牙缓冲撑爆。
     */
    const val MAX_VALUES = 2000

    /** 逐拍间期 → CSV（整数毫秒，**不需要小数位**：`peaks[k] * 1000 / fs` 本身就是整数）。 */
    fun encodeRr(rr: List<Int>): String =
        rr.asSequence().take(MAX_VALUES).joinToString(",") { it.toString() }

    /** CSV → 逐拍间期。任何解析不出来的片段直接跳过，不抛异常（坏数据不应该让页面崩）。 */
    fun decodeRr(csv: String?): List<Int> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(',').asSequence()
            .mapNotNull { it.trim().toIntOrNull() }
            .take(MAX_VALUES)
            .toList()
    }

    // ═══════════════════════ 展示辅助（两端共用，避免各写一套） ═══════════════════════

    /** 毫秒数值：整数显示（RMSSD/SDNN 的有效位数到 1ms 就是极限了）。 */
    fun ms(v: Double): String = "${v.roundToInt()}"

    /** 百分比，保留一位。 */
    fun pct1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    /** 毫秒 → 秒，保留一位小数（文案里到处要用，统一口径免得出现逗号小数点）。 */
    fun sec1(spanMs: Long): String = String.format(java.util.Locale.US, "%.1f", spanMs / 1000.0)

    /** 毫秒 → 秒，取整。 */
    fun sec0(spanMs: Long): String = "${spanMs / 1000}"

    /** 摘要一行（手表用；手机端卡片另有版式，但主指标口径必须与此一致）。 */
    fun summaryLine(r: Result.Ok): String =
        "RMSSD ${ms(r.rmssdMs)} ms · pNN50 ${r.pnn50Pct.roundToInt()}%"

    /** 分级短标签（手表的圆屏放不下 [tierLabel] 的整句）。 */
    fun tierShort(tier: String): String = when (tier) {
        Tier.ULTRA_SHORT -> Text.of("s_254c8cea")
        Tier.SHORT -> Text.of("s_003dc43d")
        Tier.STANDARD -> Text.of("s_00358d5b")
        else -> "—"
    }

    /**
     * 把长长的拒答理由压成一句话（手表圆屏用）。
     *
     * **只截取不住删**：取第一个句号/分号之前的内容，仍超 [max] 字符才截断加省略号。
     * 手表上宁可显示得短一点，也不能显示成"不适用"三个字 —— 用户得知道下一步该做什么，
     * 完整理由在手机端同一张卡片上原样给出。
     */
    fun shortReason(reason: String, max: Int = 24): String {
        val head = reason.split('。', '；', '\n').firstOrNull()?.trim().orEmpty()
        return if (head.length <= max) head else head.take(max) + "…"
    }

    /** 与自身近期基线对比的中位数（供界面显示"比你近期的中位数高/低"）。 */
    fun baselineMedian(values: List<Double>): Double? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else (s[n / 2 - 1] + s[n / 2]) / 2.0
    }

    /**
     * 为什么不给"正常范围"：RMSSD 随年龄单调下降（20 岁与 70 岁的"正常"能差 3 倍），
     * 还受体位、咖啡因、睡眠影响，拿一个固定区间去判读必然误报。
     * 所以界面只给**指标 + 信度分级 + 与你自己近期值的中位数对比**。
     */
    val INTERPRET_NOTE: String get() =
        Text.of("s_177b7417") +
            Text.of("s_94cb17ba") +
            Text.of("s_7e273884")

    val ARTIFACT_NOTE: String get() =
        Text.of("s_6c4a8adc") +
            Text.of("s_7706b535") +
            Text.of("s_751897a0")

    val TIER_NOTE: String get() =
        Text.of("s_47724116") +
            Text.of("s_14e60291") +
            Text.of("s_502eff02")

    val DISCLAIMER: String get() = Disclaimer.hrv(Text.lang)

    // ═══════════════════ 说人话（2026-09-29 加）═══════════════════
    //
    // ## 为什么要有这一组
    // 用户原话：「HRV 心率变异性给出的这些数值太专业，在保留这些专业数值的情况下，
    // 添加一些普通人能看懂的意思」。RMSSD / pNN50 / SDNN / LF/HF 是给做过功课的人看的，
    // 随手点进来的用户看到的是四个不知道在说什么的缩写。
    //
    // ## 三条纪律（别好心办坏事）
    //  ① **不评价好坏**：不说"你的 HRV 偏低/不错"。HRV 没有通用正常范围（见 [INTERPRET_NOTE]），
    //     任何"偏高/偏低"都是在编。所以这里的每句话只解释**这个数字在说什么**，
    //     以及**这次测量本身可不可信**，不判读生理优劣。
    //  ② **不改动专业数值**：这些文案只是**附加**在数值旁边，专业指标原样保留。
    //  ③ **一句话就是一句话**：界面是一行一行排的，写长了会挤成一段没人看的灰字。

    /** 几个指标分别是什么（每个一句话）。 */
    val PLAIN_RMSSD: String get() = Text.of("s_dd4aafb9")
    val PLAIN_PNN50: String get() = Text.of("s_03a4f643")
    val PLAIN_SDNN: String get() = Text.of("s_1c7effca")
    val PLAIN_LFHF: String get() = Text.of("s_bd746bd2")

    /**
     * 一行总体结论：**只看这次测量本身的质量**，不评价生理优劣。
     *
     * 判据的顺序刻意是「可信度 → 活动状态 → 时长」，因为这三者里任何一个出问题，
     * 后面的数字都白算 —— 先说数据能不能用，再说它算的是什么。
     */
    fun plainSummary(r: Result.Ok): String {
        // ① 伪迹占比高于一半上限时提醒：数字仍然给出，但要明说"里面推断的成分不少"
        val ratio = r.artifactRatio
        val half = MAX_ARTIFACT_RATIO / 2f
        val conds = when {
            r.rest == RestState.MOVING_LIGHT ->
                Text.of("s_493055f7")
            ratio > half ->
                Text.fmt("s_486f9243", (ratio * 100).roundToInt()) +
                    Text.of("s_741d9380")
            r.tier == Tier.ULTRA_SHORT ->
                Text.of("s_a98a53c0") +
                    Text.of("s_7005f5ed")
            r.rest == RestState.UNKNOWN ->
                Text.of("s_df2bb2de") +
                    Text.of("s_126417f4")
            else -> Text.of("s_33f97ee3")
        }
        return conds + Text.of("s_600eb0b3") +
            Text.of("s_504cfacb")
    }
}
