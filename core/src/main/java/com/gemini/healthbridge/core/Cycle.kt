package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 经期预估（模块 #13）：输入一串**经期区间**，输出周期统计与下一次的预测。
 *
 * ## 这个模块的输入只有一种，而且没有任何传感器能给
 * 体温有传感器、HRV 有心电、活动有加速度计 —— 经期没有。它只能来自
 *  ① 用户手动录入；② Health Connect 里已有的记录（三星健康或其它经期 App 写入）。
 * 两个来源都落进同一张表、用 `source` 区分。所以本模块的设计重心不在算法，
 * 而在**输入的可信度**：漏记一次、重复录一次、把点滴出血当成一次经期，
 * 都会让"平均周期长度"这个唯一的输出偏移好几天。
 *
 * ## 为什么周期长度必须用中位数而不是均值
 * 一次漏记会让相邻两次开始日相差 2 个周期（比如 28 天变成 56 天），
 * 一次把点滴出血记成经期会让它变成 10 天。均值会被这两种误操作直接拉飞，
 * 而中位数对"多达一半的记录有误"仍然稳。这条规则与 HRV 的伪迹参考值同源。
 *
 * ## 为什么规整度用极差（天）而不是变异系数
 * 临床上的口径是**绝对天数**：周期长度变化超过 7~9 天即为不规律（ACOG）。
 * 如果改用 CV，一个"稳定 40 天"的多囊卵巢样周期（CV = 0）会被判成"非常规律"，
 * 而"21~28 天之间波动"（CV ≈ 0.14）会被判成"不规律" ——
 * 与临床口径恰好相反。所以这里坚持用 `最大周期长度 − 最小周期长度`。
 *
 * ## 为什么预测只给下一次，不给半年日历
 * 黄体期（排卵到下次经期）相对稳定，约 14 天，所以"下次经期 ≈ 本次 + 平均周期长度"
 * 在**一个周期**内误差可控；但每往后预测一个周期，误差就累积一次，
 * 排三个月以后的日历只是把不确定性画成了确定的格子。本模块**只预测下一次**。
 *
 * ## 排卵日与易孕窗口：给，但必须说清它的边界
 * 由 `下次经期 − 14 天` 反推排卵日（黄体期 14 天是**群体均值**，个体 10~16 天），
 * 窗口取"排卵前 5 天 ~ 排卵后 1 天"（精子在体内可存活约 5 天，卵子约 24 小时）。
 * 这个窗口**不能用于避孕**，也**不能当作受孕指导** —— 见 [DISCLAIMER]。
 * 不写清楚这一点是这类功能最容易出的伦理问题。
 */
object Cycle {

    // ═══════════════════════ 来源与分级 ═══════════════════════

    /** 经期记录的来源（入库字段，界面据此说明口径）。 */
    object Source {
        /** 用户在手机端手动录入。**唯一可控、也是唯一可信赖的兜底来源**。 */
        const val MANUAL = "manual"

        /**
         * 从 Health Connect 导入的 `MenstruationPeriodRecord`。
         *
         * 这是**只读**的：我们自己不往 Health Connect 写经期数据，
         * 所以同一条经期如果用户也在别的 App 里记过，会以两条不同 `source` 的行存在 ——
         * 由 [merge] 在分析时按"相邻开始日 < [MIN_CYCLE_DAYS] 即同一段"合并，导入时也会先挡一道。
         */
        const val HC = "hc_period"
    }

    /** 流量档位。取值与 Health Connect 的 `MenstruationFlowRecord` 对齐，但只保留字符串名。 */
    object Flow {
        const val UNKNOWN = "unknown"
        const val LIGHT = "light"
        const val MEDIUM = "medium"
        const val HEAVY = "heavy"

        fun label(v: String?): String = when (v) {
            LIGHT -> Text.of("s_00005c11")
            MEDIUM -> Text.of("s_00004e2d")
            HEAVY -> Text.of("s_0000591a")
            else -> Text.of("s_1b4b78df")
        }
    }

    /** 数据充分度。由**已有的周期长度个数**决定，界面必须显示（它是预测能不能用的前提）。 */
    object Tier {
        /** 连一次完整周期都算不出来（少于 2 次经期记录）。不给任何预测。 */
        const val INSUFFICIENT = "insufficient"

        /** 1~2 个周期长度。给预测，但必须标注"初步"。 */
        const val PRELIMINARY = "preliminary"

        /** ≥3 个周期长度。预测相对稳定。 */
        const val ESTABLISHED = "established"
    }

    /** 周期内的阶段。**数据不足时不给阶段** —— 那等于编一个周期出来。 */
    object Phase {
        const val MENSTRUATION = "menstruation"
        const val FOLLICULAR = "follicular"
        const val OVULATION = "ovulation"
        const val LUTEAL = "luteal"
    }

    fun phaseLabel(phase: String): String = when (phase) {
        Phase.MENSTRUATION -> Text.of("s_00414b0c")
        Phase.FOLLICULAR -> Text.of("s_1612ac5f")
        Phase.OVULATION -> Text.of("s_1a3dd220")
        Phase.LUTEAL -> Text.of("s_29bbdf7c")
        else -> Text.of("s_00354263")
    }

    fun tierLabel(tier: String): String = when (tier) {
        Tier.INSUFFICIENT -> Text.of("s_b1d1e208")
        Tier.PRELIMINARY -> Text.of("s_fd0804d6")
        Tier.ESTABLISHED -> Text.of("s_11e83ed2")
        else -> Text.of("s_00354263")
    }

    fun tierShort(tier: String): String = when (tier) {
        Tier.INSUFFICIENT -> Text.of("s_b1d1e208")
        Tier.PRELIMINARY -> Text.of("s_002a703c")
        Tier.ESTABLISHED -> Text.of("s_0030c06c")
        else -> "?"
    }

    /** 规整度。口径是**绝对天数**极差（见文件头说明）。 */
    object Regularity {
        const val UNKNOWN = "unknown"
        const val REGULAR = "regular"
        const val SLIGHTLY_IRREGULAR = "slightly_irregular"
        const val IRREGULAR = "irregular"
    }

    fun regularityLabel(v: String): String = when (v) {
        Regularity.REGULAR -> Text.of("s_0046ded7")
        Regularity.SLIGHTLY_IRREGULAR -> Text.of("s_d63379c6")
        Regularity.IRREGULAR -> Text.of("s_14b7044c")
        else -> Text.of("s_c34c0523")
    }

    // ═══════════════════════ 门限（全部有出处） ═══════════════════════

    /**
     * 周期长度的生理区间（天）。
     * 下界 15：ACOG 把周期 < 21 天定义为"频发"（polymenorrhea），而 15 天以下
     * 更可能是"把一次经期拆成了两次"或点滴出血。上界 50：> 35 天为"稀发"
     * （oligomenorrhea），但 50 天以上更可能是**漏记了一次**（真实周期约 28 天，
     * 漏记一次就变成约 56 天 = 2×周期，必须挡在统计之外）。
     * ⚠ 上界卡在 50 是有意为之：既要**保留**真实的长周期（多囊卵巢 PCOS 常见 40 天），
     * 又要**挡掉**漏记产生的 56 天。再往上（如 60）会让 56 天漏网 —— 两个值里
     * 一个 56 一个 28，中位数 (56+28)/2=42，中位数就失效了（离群值占了一半）。
     * 这两个界是**参与统计的门槛**，不是"正常范围" —— 落在里面不代表正常。
     */
    const val MIN_CYCLE_DAYS = 15
    const val MAX_CYCLE_DAYS = 50

    /** 经期长度（出血天数）的合法区间。上界照抄 Health Connect 的 `MAX_DURATION = 31 天`。 */
    const val MIN_PERIOD_DAYS = 1
    const val MAX_PERIOD_DAYS = 31

    /**
     * 相邻两次开始日小于这个天数 → 视为**同一段经期**，合并（取较早的开始、较晚的结束）。
     *
     * 为什么需要它：一个用户的同一段经期可能同时存在于手动记录与 Health Connect 导入里
     * （两条不同 `source` 的行），也可能因为"记了一次开始、隔天又记一次"而重复。
     * 若不合并，这些记录会算出 0~2 天的"周期长度"，把中位数彻底带偏。
     */
    const val MERGE_GAP_DAYS = MIN_CYCLE_DAYS

    /**
     * 预测区间的最小半宽（天）。
     * 只有 2 次记录时极差可能是 0，直接给出"就是 12 月 1 日"是**假精确** ——
     * 至少给 ±2 天，让界面上的预测天然带着不确定性。
     */
    const val MIN_SPREAD_DAYS = 2

    /** 排卵日 = 下次经期开始 − 这个天数。黄体期 14 天是群体均值，个体 10~16 天。 */
    const val LUTEAL_DAYS = 14

    /** 排卵日推算的不确定度（天）。个体黄体期差异就这么大，不要再收窄。 */
    const val OVULATION_TOLERANCE_DAYS = 2

    /** 易孕窗口：排卵日前 5 天开始（精子存活），到排卵日后 1 天结束（卵子存活约 24h）。 */
    const val FERTILE_BEFORE_DAYS = 5
    const val FERTILE_AFTER_DAYS = 1

    /**
     * 长于这个天数没有新记录，且预测日已过 → 提示"预测已过期，可能漏记"。
     * 用两个最大周期长度：到那时"还没有新记录"就基本不可能是周期本身长，
     * 而是漏记了。**不拒答** —— "该记了"这个提示本身就是有用的输出。
     */
    const val STALE_AFTER_DAYS = 2 * MAX_CYCLE_DAYS

    /** 未记录结束日时，用于判断"今天还在不在经期里"的默认经期长度（天）。 */
    const val DEFAULT_PERIOD_DAYS = 5

    // ═══════════════════════ 输入与结果类型 ═══════════════════════

    /**
     * 一条经期记录。`startEpochDay`/`endEpochDay` 都是**本地日期**的 epochDay
     * （不是时间戳）—— 经期是"哪天"的事，跟几点几分无关，用天数可以彻底躲开时区与夏令时。
     * 这与血氧/心率那套"毫秒时间戳"刻意不同。
     *
     * `endEpochDay == null` 表示"只知道开始了，还没记结束"。
     * 这**不猜**：经期平均长度只统计有结束日的记录。
     */
    data class Entry(
        val startEpochDay: Long,
        val endEpochDay: Long? = null,
        val flow: String? = null,
        val source: String = Source.MANUAL,
    ) {
        /** 出血天数。缺结束日时为 null（**不拿默认值顶替**）。 */
        val periodDays: Int?
            get() = endEpochDay?.let { (it - startEpochDay + 1).toInt() }
    }

    /** 一次统计出来的周期长度（天）及其起始日，供画柱状图与逐条列出。 */
    data class CycleLength(
        /** 这次经期的开始日（epochDay）。 */
        val startEpochDay: Long,
        /** 与**上一次**经期开始日的间隔（天）。 */
        val days: Int,
    )

    /** 预测结果。所有日期都是 epochDay。 */
    data class Prediction(
        /** 预测的下次经期开始日。 */
        val nextStartEpochDay: Long,
        /** 预测区间半宽（天），**不小于 [MIN_SPREAD_DAYS]**。 */
        val spreadDays: Int,
        /** 排卵日（= 下次经期 − [LUTEAL_DAYS]）。 */
        val ovulationEpochDay: Long,
        /** 易孕窗口起（含）。 */
        val fertileFromEpochDay: Long,
        /** 易孕窗口止（含）。 */
        val fertileToEpochDay: Long,
    )

    /**
     * 分析结果。
     *
     * **[Result.Ok] 分两个层次**：即使算不出预测，也一定给出"为什么算不出"
     * （`insufficientReason`），而不是一个空页面。这与 HRV 把"给不了"当成正常结果同一条纪律。
     */
    sealed interface Result {
        /** 合并去重后的有效经期次数（= 参与统计的记录数）。 */
        val periodCount: Int

        data class Ok(
            override val periodCount: Int,
            /** 平均周期长度（**中位数**）。 */
            val medianCycleDays: Int,
            /** 周期长度极差（最大−最小），即 [Prediction.spreadDays] 的来源。 */
            val spreadDays: Int,
            /** 用于统计的周期长度列表（按时间升序）。 */
            val lengths: List<CycleLength>,
            /** 各次平均经期长度（天，仅统计有结束日的记录）；无一条有结束日时为 null。 */
            val medianPeriodDays: Int?,
            val regularity: String,
            val tier: String,
            /** 最近一次经期开始日。 */
            val lastStartEpochDay: Long,
            /** 最近一次经期结束日（未记录时为 null）。 */
            val lastEndEpochDay: Long?,
            /** 今天（epochDay），由调用方传入以免与本模块的时间源不一致。 */
            val todayEpochDay: Long,
            /** 预测；[Tier.INSUFFICIENT] 时为 null。 */
            val prediction: Prediction?,
            /** 当前周期第几天（从最近一次开始日起算，含当天）。 */
            val cycleDay: Int,
            /** 当前阶段；没有预测时为 null。 */
            val phase: String?,
            /** 是否处在易孕窗口内；没有预测时为 null。 */
            val inFertileWindow: Boolean?,
            /** 预测日已过多久（天）；没超过为 0。>0 时界面提示"可能漏记"。 */
            val overdueDays: Int,
            /** 数据充分度的说明句（永远有话说，界面直接显示）。 */
            val tierNote: String,
        ) : Result

        /** 连一条有效经期都没有。 */
        data class NotApplicable(
            override val periodCount: Int,
            val reason: String,
        ) : Result
    }

    // ═══════════════════════ 合并与统计 ═══════════════════════

    /**
     * 合并被视为同一段经期的记录，并按时间升序返回。
     *
     * 判定只有一条：相邻两条的开始日相差 < [MIN_CYCLE_DAYS] 天。
     * 合并后的属性取值规则（**刻意偏向"信息更多"的那一条，而不是最近的那一条**）：
     *  - `startEpochDay` 取**较早**的（经期从最早那一天算起）；
     *  - `endEpochDay` 取**较晚**的非空值（结束日比开始日更容易漏记）；
     *  - `flow` 取非空值里**较重**的一档（少 < 中 < 多）—— 同一段经期被记成两个档位时，
     *    较重的那次更可能是用户认真记的；取较轻会系统性地低估出血量；
     *  - `source` 取**手动**（用户自己的录入优先于导入数据）。
     *
     * 超过 [MAX_PERIOD_DAYS] 的合并结果会被截断到 [MAX_PERIOD_DAYS] 天 ——
     * 那通常意味着"两次经期被误合并"，而不是"一次经期 45 天"。
     */
    fun merge(entries: List<Entry>): List<Entry> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedBy { it.startEpochDay }
        val out = ArrayList<Entry>(sorted.size)
        for (e in sorted) {
            val prev = out.lastOrNull()
            if (prev != null && e.startEpochDay - prev.startEpochDay < MERGE_GAP_DAYS) {
                val mergedEnd = listOfNotNull(prev.endEpochDay, e.endEpochDay).maxOrNull()
                val start = prev.startEpochDay
                val capped = mergedEnd?.coerceAtMost(start + MAX_PERIOD_DAYS - 1)?.coerceAtLeast(start)
                out[out.size - 1] = Entry(
                    startEpochDay = start,
                    endEpochDay = capped,
                    flow = heavierFlow(prev.flow, e.flow),
                    source = if (prev.source == Source.MANUAL || e.source == Source.MANUAL) {
                        Source.MANUAL
                    } else {
                        prev.source
                    },
                )
            } else {
                out.add(e)
            }
        }
        return out
    }

    /** 取较重的一档流量（少 < 中 < 多）。两边都有值时才有意义。 */
    private fun heavierFlow(a: String?, b: String?): String? {
        val rank = mapOf(Flow.LIGHT to 1, Flow.MEDIUM to 2, Flow.HEAVY to 3)
        val ra = rank[a] ?: 0
        val rb = rank[b] ?: 0
        return when {
            ra == 0 && rb == 0 -> a ?: b
            rb > ra -> b
            else -> a
        }
    }

    /**
     * 中位数（偶数个取中间两值均）。**用中位数不用均值** —— 见文件头说明。
     * 这是整个模块唯一被允许用来代表"平均周期长度"的统计量。
     */
    fun medianDays(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        val n = s.size
        return if (n % 2 == 1) s[n / 2] else ((s[n / 2 - 1] + s[n / 2]) / 2.0).roundToInt()
    }

    /** 规整度：用**绝对天数**极差，口径见文件头。 */
    fun regularityOf(lengths: List<Int>): String {
        if (lengths.size < 2) return Regularity.UNKNOWN
        val spread = lengths.max() - lengths.min()
        return when {
            spread <= 7 -> Regularity.REGULAR
            spread <= 14 -> Regularity.SLIGHTLY_IRREGULAR
            else -> Regularity.IRREGULAR
        }
    }

    // ═══════════════════════ 主入口 ═══════════════════════

    /**
     * 分析一串经期记录（**未合并、未排序都可以**，本函数自己做）。
     *
     * @param entries 原始记录，可来自手动与导入两个来源。
     * @param todayEpochDay 今天（本地日期 epochDay）。**由调用方传入**：
     *   手表端拿到的是手机推来的预测参数，要按手表自己的"今天"重算，
     *   两边各自调用时都传自己的今天，避免"预测是昨天算的"。
     */
    fun analyze(entries: List<Entry>, todayEpochDay: Long): Result {
        val merged = merge(entries)
        if (merged.isEmpty()) {
            return Result.NotApplicable(
                0,
                Text.of("s_b5cecb8f") +
                    Text.of("s_1cd5b7f7"),
            )
        }

        val last = merged.last()
        val cycleDay = (todayEpochDay - last.startEpochDay + 1).toInt()

        // 相邻开始日的间隔，只保留落在生理区间的那些参与统计。
        val lengths = ArrayList<CycleLength>(merged.size)
        for (i in 1 until merged.size) {
            val d = (merged[i].startEpochDay - merged[i - 1].startEpochDay).toInt()
            if (d in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS) {
                lengths.add(CycleLength(merged[i].startEpochDay, d))
            }
        }

        // 经期长度：**只统计有结束日的记录**。缺结束日不猜（猜了会系统性偏短）。
        val periodDays = merged.mapNotNull { it.periodDays }
            .filter { it in MIN_PERIOD_DAYS..MAX_PERIOD_DAYS }
        val medianPeriodDays = if (periodDays.isEmpty()) null else medianDays(periodDays)

        if (lengths.isEmpty()) {
            // 有一条记录但算不出周期长度：可能是刚记了第一次，也可能是两次记录都被
            // 生理区间挡掉了（漏记导致间隔 > 60 天，或重复导致 < 15 天）。
            val reason = if (merged.size == 1) {
                Text.of("s_b49c9c6b") +
                    Text.of("s_2261972f")
            } else {
                Text.fmt("s_4f7ccb9f", merged.size) +
                    Text.fmt("s_fcce8df3", MIN_CYCLE_DAYS, MAX_CYCLE_DAYS) +
                    Text.of("s_9ade3097")
            }
            return Result.NotApplicable(merged.size, reason)
        }

        val medianCycle = medianDays(lengths.map { it.days })
        if (medianCycle !in MIN_CYCLE_DAYS..MAX_CYCLE_DAYS) {
            // 中位数落在区间外只可能是合并/去重没兜住。宁可拒答也不给一个明显错的预测。
            return Result.NotApplicable(
                merged.size,
                Text.fmt("s_3fd7469e", medianCycle, MIN_CYCLE_DAYS, MAX_CYCLE_DAYS) +
                    Text.of("s_100f80e8") +
                    Text.of("s_459b1e9f"),
            )
        }

        val spread = (lengths.maxOf { it.days } - lengths.minOf { it.days })
            .coerceAtLeast(MIN_SPREAD_DAYS)
        // 走到这里 lengths 至少有一个元素（空的情况在上面已经拒答了），所以只分两档。
        val tier = if (lengths.size <= 2) Tier.PRELIMINARY else Tier.ESTABLISHED

        val nextStart = last.startEpochDay + medianCycle
        val ovulation = nextStart - LUTEAL_DAYS
        val pred = Prediction(
            nextStartEpochDay = nextStart,
            spreadDays = spread,
            ovulationEpochDay = ovulation,
            fertileFromEpochDay = ovulation - FERTILE_BEFORE_DAYS,
            fertileToEpochDay = ovulation + FERTILE_AFTER_DAYS,
        )

        val phase = phaseOf(
            cycleDay = cycleDay,
            periodDays = medianPeriodDays ?: DEFAULT_PERIOD_DAYS,
            ovulationCycleDay = (ovulation - last.startEpochDay).toInt() + 1,
            hasPrediction = true,
        )
        val inFertile = todayEpochDay in pred.fertileFromEpochDay..pred.fertileToEpochDay
        val overdue = (todayEpochDay - nextStart).toInt().coerceAtLeast(0)

        return Result.Ok(
            periodCount = merged.size,
            medianCycleDays = medianCycle,
            spreadDays = spread,
            lengths = lengths,
            medianPeriodDays = medianPeriodDays,
            regularity = regularityOf(lengths.map { it.days }),
            tier = tier,
            lastStartEpochDay = last.startEpochDay,
            lastEndEpochDay = last.endEpochDay,
            todayEpochDay = todayEpochDay,
            prediction = pred,
            cycleDay = cycleDay,
            phase = phase,
            inFertileWindow = inFertile,
            overdueDays = overdue,
            tierNote = tierNote(tier, lengths.size, spread),
        )
    }

    /**
     * 当前阶段。**没有预测就不给阶段**（`hasPrediction == false` 时返回 null）——
     * 没有预测意味着不知道下次经期在哪，也就无从知道排卵日，"卵泡期/黄体期"会变成编的。
     */
    fun phaseOf(
        cycleDay: Int,
        periodDays: Int,
        ovulationCycleDay: Int,
        hasPrediction: Boolean,
    ): String? {
        if (!hasPrediction) return null
        return when {
            cycleDay <= periodDays -> Phase.MENSTRUATION
            abs(cycleDay - ovulationCycleDay) <= 1 -> Phase.OVULATION
            cycleDay < ovulationCycleDay -> Phase.FOLLICULAR
            else -> Phase.LUTEAL
        }
    }

    private fun tierNote(tier: String, cycleCount: Int, spread: Int): String = when (tier) {
        Tier.PRELIMINARY ->
            Text.fmt("s_6cab37a7", cycleCount) +
                Text.fmt("s_da1e9d3c", spread)
        Tier.ESTABLISHED ->
            Text.fmt("s_8375c02b", cycleCount, spread) +
                Text.of("s_22d706ec")
        else -> Text.of("s_7b64e99a")
    }

    // ═══════════════════════ 手动录入的校验 ═══════════════════════

    /**
     * 手动录入的校验结果。**录入这一侧也要有门控**，理由与算法侧一致：
     * 一条离谱的记录会污染此后所有预测，而且用户在界面上看不出是哪一条的问题。
     */
    sealed interface InputCheck {
        data object Ok : InputCheck
        data class Invalid(val reason: String) : InputCheck
    }

    /**
     * 校验一次手动录入。
     *
     * @param startEpochDay 经期开始日
     * @param endEpochDay 经期结束日；null = 只记开始（允许）
     * @param todayEpochDay 今天
     * @param others **除本次之外**已存在的记录，用于查"与已有记录重叠/过近"
     */
    fun checkInput(
        startEpochDay: Long,
        endEpochDay: Long?,
        todayEpochDay: Long,
        others: List<Entry>,
    ): InputCheck {
        if (startEpochDay > todayEpochDay) {
            return InputCheck.Invalid(Text.of("s_a62c0fbd"))
        }
        if (endEpochDay != null) {
            if (endEpochDay < startEpochDay) {
                return InputCheck.Invalid(Text.of("s_43357054"))
            }
            val d = (endEpochDay - startEpochDay + 1).toInt()
            if (d > MAX_PERIOD_DAYS) {
                return InputCheck.Invalid(
                    Text.fmt("s_a691b493", d, MAX_PERIOD_DAYS),
                )
            }
        }
        // 与已有记录的冲突：这是**提示而非拒绝**最合适的地方，但这里只做拒绝 ——
        // 界面在提交前就把冲突说出来，用户改一个日期即可，比"存进去之后预测莫名偏了"好查。
        val conflict = others.firstOrNull {
            abs(it.startEpochDay - startEpochDay) < MERGE_GAP_DAYS &&
                // 同一条记录自己不算冲突（编辑场景由调用方把这条剔除）
                !(it.startEpochDay == startEpochDay && it.endEpochDay == endEpochDay)
        }
        if (conflict != null) {
            return InputCheck.Invalid(
                Text.fmt("s_e26dff6c", dateText(conflict.startEpochDay), MERGE_GAP_DAYS) +
                    Text.of("s_6f0802d8"),
            )
        }
        return InputCheck.Ok
    }

    // ═══════════════════════ 文案与格式化 ═══════════════════════

    /**
     * 界面必须显示的说明。**四项都不能省**：
     * ① 预测只是历史外推；② 哪些情况会让它失准；③ 什么时候不该信它；④ 免责。
     */
    val INTERPRET_NOTE: String get() =
        Text.of("s_dd47f6a4") +
            Text.of("s_19c08973") +
            Text.of("s_04e694e6")

    val DATA_NOTE: String get() =
        Text.fmt("s_d7255444", MIN_CYCLE_DAYS, MAX_CYCLE_DAYS) +
            Text.of("s_9b050f0e") +
            Text.of("s_a7c347bd")

    val REGULARITY_NOTE: String get() =
        Text.of("s_e02a8d93") +
            Text.of("s_fdddefe4") +
            Text.of("s_afef74b5")

    val FERTILE_NOTE: String get() =
        Text.of("s_1d72fec0") +
            Text.of("s_9aaa0474")

    val DISCLAIMER: String get() = Disclaimer.cycle(Text.lang)

    /** 从 Health Connect 导入时的说明（放在「数据口径」里）。 */
    val HC_NOTE: String get() =
        Text.of("s_9a9c8c51")

    /** 缺结束日时的说明。 */
    val NO_END_NOTE: String get() =
        Text.of("s_853419c2") +
            Text.of("s_2cd16ee1")

    /**
     * 日期文案（如 `2026年10月2日`）。
     *
     * ⚠ 实现走 [dateTextRes]：合成逻辑**只写一份**，避免「界面一个日期、事件里另一个日期」
     * 那种两处各自演化的问题。
     */
    fun dateText(epochDay: Long): String = dateTextRes(epochDay).coreText()

    /**
     * 同 [dateText]，但返回**待求值**文案。
     *
     * ⚠ 给「会存进状态 / 一次性事件」的链路用（如手机端
     * `已记录经期：%s` 的 `%s`）：那里若塞一个已经求好值的日期串，
     * 切语言后日期会停在旧语言 —— 这正是 `core/ResText.kt` 要根治的那类错。
     */
    fun dateTextRes(epochDay: Long): ResText {
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return ResText.core("s_bd36935e", d.year, d.monthValue, d.dayOfMonth)
    }

    fun dateShort(epochDay: Long): String {
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return "%d/%d".format(d.monthValue, d.dayOfMonth)
    }

    /** 预测区间文案：`11月28日 ±2天`。半宽为 0 时不会出现（[MIN_SPREAD_DAYS] 兜住了）。 */
    fun predictText(p: Prediction): String = Text.fmt("s_2eb5c82c", dateText(p.nextStartEpochDay), p.spreadDays)

    /** 一行摘要（手表端用），例如 `第 12 天 · 距下次 16 天`。 */
    fun glanceText(cycleDay: Int, daysToNext: Int, overdueDays: Int): String = when {
        overdueDays > 0 -> Text.fmt("s_a07347b2", cycleDay, overdueDays)
        else -> Text.fmt("s_2ad6b743", cycleDay, daysToNext)
    }
}
