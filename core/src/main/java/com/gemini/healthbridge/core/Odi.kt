package com.gemini.healthbridge.core

/**
 * 氧减指数 ODI（Oxygen Desaturation Index）：从**分钟级血氧序列**里数"掉下去又回来"的次数，
 * 折成每小时几次。
 *
 * ## 为什么做这个（模块 #11 的替代落点）
 * 总表 #11 写的是"从 PPG/加速度波形提取呼吸频率"。但核过三星健康 Data SDK 1.1.0 之后，
 * 才发现**三星开放的数据里没有任何采样率够高的信号**：
 *   · 心率 `SERIES_DATA`：每点是 `heartRate/min/max` 的**分箱**，≥1 分钟一个；
 *   · 血氧 `SERIES_DATA`：**分钟级**；
 *   · 皮肤温度：夜间慢变序列；睡眠分期：离散段；
 *   · 呼吸率本身：**未开放**（1.1.0 的 `entries` 里没有这个类型）。
 * 呼吸是 0.2~0.4 Hz，至少要 1 Hz 连续采样才提得出来 —— 三星在服务端就把原始信号
 * 压成了分箱聚合值，**物理上提不出呼吸频率**，这与算法强弱无关。
 *
 * 但"分钟级"这个粒度**刚好够**做另一件临床上有意义的事：数氧减事件。
 * 一次呼吸暂停/低通气事件持续 10~60 秒，造成的血氧下降会跨到 1~2 个分钟点，
 * 分钟序列足以"看见"它。于是把 #11 落到：
 *   · **ODI（本文件）**：拿三星夜间血氧分钟序列 + 自研算法，做睡眠呼吸紊乱筛查；
 *   · **呼吸率估算**（`Resp.kt`）：用我们自己有足够采样率的信号（PPG 25 Hz / 心电逐拍 RR）来做。
 *
 * ## 什么是"一次氧减事件"（判据与出处）
 * 采用睡眠医学里最常用的定义：
 *   · **下降**：血氧相对此前基线**下降 ≥ [DROP_PCT]%（3%）**；
 *   · **回升**：回到**距基线 1% 以内**（[RECOVER_PCT]）算本次事件结束。
 * 3% 是美国睡眠医学会（AASM）2012 年以来推荐的低通气判据（美国医保口径用 4%），
 * 1% 的回升阈值是"回到基线附近"的常用取法。这里的 3% / 1% 都写成常量，便于核对与改动。
 *
 * ## 基线为什么取"此前 5 分钟的中位数"
 * 这是本算法里唯一一个真正需要斟酌的参数，三种取法比较过：
 *   1. **固定基线（整晚均值/首值）**：血氧整夜本来就会缓慢下移几个百分点，
 *      固定基线会让后半夜"人人都是氧减" —— 系统性虚高。
 *   2. **只升不降的基线**（取历史最大值）：与上一条同样的问题，方向相反。
 *   3. **滚动中位数（本实现）**：短事件（1~2 分钟）只占窗口里的一两个点，
 *      **几乎推不动中位数**，所以下降被完整保留；而持续数分钟的新平台会慢慢成为新基线，
 *      这正是"基线应该跟着走"的情形。用中位数而不是均值，是为了让
 *      **单点伪迹（表松了、手压到）不影响基线** —— 均值会被一个 0 拉低好几个点。
 *
 * 窗口取 [BASELINE_WINDOW_MIN] 分钟、且要求窗口内至少 [MIN_BASELINE_POINTS] 个点：
 * 点数不够（序列开头）就不判事件，宁可漏掉开头几个，也不要拿一两个点当基线。
 *
 * ## 不可信读数按"缺失"处理，不当成事件
 * 低于 [MIN_PLAUSIBLE]% 的读数**直接剔除**（计入 `rejected`，在界面上如实显示剔除了几个）：
 * 腕式光学血氧在这么低的水平上与"表松了/没戴好/环境光"无法区分，
 * 而 SpO2 的取值被 coerce 到 0~100，**一个故障读数会表现为一个"极重度事件"** ——
 * 不剔除的话，一次传感器故障就能把 ODI 刷到重度。宁可少算，不要假警报。
 * 剔除后它前后两点的间隔按 [MAX_GAP_MIN] 封顶计入监测时长（见下）。
 *
 * ## 分母用"实际监测到的分钟数"，不用整段睡眠时长
 * ODI 的标准定义是"每小时睡眠的事件数"。但腕表未必整夜都在记录血氧，
 * 拿整段睡眠时长当分母会把 ODI **系统性算低**（睡 8 小时、只记录了 3 小时，
 * 事件数除以 8 就只剩 37%）。所以分母取**序列自己覆盖到的时长**：
 * 相邻有效点的间隔累加，单个间隔按 [MAX_GAP_MIN] 分钟封顶 ——
 * 超过 5 分钟的空档说明那段时间根本没在监测，不能算进分母（否则分母虚高、ODI 虚低）。
 * 监测不足 [MIN_MONITORED_MIN] 分钟就不给数（一两个小时的片段算出来的 ODI 波动极大）。
 *
 * ## 刻意不做的事
 * 1. **不诊断睡眠呼吸暂停**。ODI 是筛查指标，本实现的数据是**分钟级腕式血氧**，
 *    与多导睡眠监测（PSG）不是一回事：分钟采样会把很短的事件漏掉（低估），
 *    也会把运动伪迹误判成事件（高估）。分级（[bandOf]）只作为**参考**呈现，
 *    并且必须和免责声明一起出现（[DISCLAIMER]）。
 * 2. **不把"没回落"的事件特殊化**。序列末尾正在下降的事件照样计数：
 *    "下降了 ≥3%"本身已经成立，要求看到回升会系统性地漏掉末尾的事件。
 *    代价是传感器在末尾脱落会多算一次 —— 这是**有界的**误差（最多 1 次），
 *    比系统性漏掉末尾事件好。
 * 3. **不估算呼吸率**。这个粒度做不到，见类注释开头。
 *
 * ## 为什么做成无 Android 依赖的纯 Kotlin
 * 与 `Ppg.kt` / `Hrv.kt` / `Stress.kt` 同一条理由：能直接进离线 JVM 台架
 * （离线校验台架）跑断言。阈值、窗口、分母口径都是最容易"凭印象写错"的东西，
 * 而写错的后果是一个**看起来完全正常的数**。
 */
object Odi {

    // ═══════════════ 门限 ═══════════════

    /** 判定一次氧减事件所需的下降幅度（百分点）。AASM 推荐的低通气判据。 */
    const val DROP_PCT = 3

    /** 事件结束：回升到距基线多少个百分点以内。 */
    const val RECOVER_PCT = 1

    /** 基线窗口（分钟）：取**当前点之前**这段时间内的有效读数的中位数。理由见类注释。 */
    const val BASELINE_WINDOW_MIN = 5

    /** 基线窗口内至少要有几个有效点，否则这一刻不判事件。 */
    const val MIN_BASELINE_POINTS = 3

    /**
     * 可信读数下界（%）。低于它的读数按**缺失**处理（不进序列、不算事件）。
     * 理由见类注释：腕式光学血氧在这个水平上与"没戴好"无法区分，而故障读数会被
     * coerce 成一个"极重度事件"。
     */
    const val MIN_PLAUSIBLE = 50

    /** 可信读数上界（%）。血氧不可能超过 100，超过说明是脏数据。 */
    const val MAX_PLAUSIBLE = 100

    /**
     * 相邻有效点的间隔超过它（分钟）就认为"那段时间没在监测"：
     * 计时长时按它封顶，避免一段空档把分母撑大。
     */
    const val MAX_GAP_MIN = 5

    /** 监测时长不足它（分钟）就不给 ODI —— 太短的片段算出来的指数波动极大。 */
    const val MIN_MONITORED_MIN = 120

    /** 至少要有这么多个**有效**点才谈得上"一段监测"。 */
    const val MIN_POINTS = 30

    /** T90 的阈值（%）：血氧低于它的时间占比，是临床上与 ODI 并列的常用指标。 */
    const val T90_THRESHOLD = 90

    private const val MAX_GAP_MS = MAX_GAP_MIN * 60_000L
    private const val BASELINE_WINDOW_MS = BASELINE_WINDOW_MIN * 60_000L

    // ═══════════════ 分级 ═══════════════

    /** 分级令牌（与 `SleepStageKind`、`Activity.Level` 同一条理由：用字符串，方便落库与跨版本）。 */
    object Band {
        const val NORMAL = "normal"
        const val MILD = "mild"
        const val MODERATE = "moderate"
        const val SEVERE = "severe"
    }

    /** 轻度下界：ODI ≥ 5 次/小时。 */
    const val MILD_MIN = 5.0

    /** 中度下界：ODI ≥ 15 次/小时。 */
    const val MODERATE_MIN = 15.0

    /** 重度下界：ODI ≥ 30 次/小时。 */
    const val SEVERE_MIN = 30.0

    /**
     * ODI 分级。区间是睡眠医学里**常用**的那一套（<5 正常 / 5~15 轻度 / 15~30 中度 / ≥30 重度），
     * 它是**流行病学与临床常用的参考切点，不是诊断标准** —— 本实现的输入是分钟级腕式血氧，
     * 与 PSG 的金标准不可互换（见类注释）。
     */
    fun bandOf(odiPerHour: Double): String = when {
        odiPerHour.isNaN() || odiPerHour < MILD_MIN -> Band.NORMAL
        odiPerHour < MODERATE_MIN -> Band.MILD
        odiPerHour < SEVERE_MIN -> Band.MODERATE
        else -> Band.SEVERE
    }

    fun bandLabel(token: String): String = when (token) {
        Band.NORMAL -> Text.of("s_7ca32566")
        Band.MILD -> Text.of("s_0049ca97")
        Band.MODERATE -> Text.of("s_00285fad")
        Band.SEVERE -> Text.of("s_004afa8d")
        else -> "--"
    }

    /** 分级对应的配色说明（界面传色时用，core 不依赖 Compose）。 */
    fun bandColorArgb(token: String): Long = when (token) {
        Band.NORMAL -> 0xFF2E7D32L
        Band.MILD -> 0xFFF9A825L
        Band.MODERATE -> 0xFFEF6C00L
        else -> 0xFFC62828L
    }

    // ═══════════════ 输入与结果 ═══════════════

    /** 一个血氧读数（epoch 毫秒 + 百分比整数）。 */
    data class Sample(val tMs: Long, val spo2: Int)

    sealed interface Result {
        /** 算出来了。 */
        data class Ok(
            /** 氧减事件次数。 */
            val events: Int,
            /** ODI：每小时的事件数。 */
            val odiPerHour: Double,
            /** 分母：实际监测到的分钟数（相邻有效点间隔累加，长空档封顶）。 */
            val monitoredMinutes: Double,
            /** 有效读数个数。 */
            val validPoints: Int,
            /** 被剔除的不可信读数个数（< [MIN_PLAUSIBLE] 或 > [MAX_PLAUSIBLE]）。 */
            val rejectedPoints: Int,
            /** 监测到的最低血氧（有效读数里的最小值，未经时间加权）。 */
            val lowest: Int,
            /** 有效读数的算术平均（%）。 */
            val mean: Double,
            /** T90：血氧 < [T90_THRESHOLD]% 的时间占监测时长的百分比（0~100）。 */
            val t90Pct: Double,
            /** 序列覆盖的时间跨度（毫秒，首点到末点）。 */
            val spanMs: Long,
        ) : Result {
            val band: String get() = bandOf(odiPerHour)
        }

        /** 给不出结论（原因直接给用户看）。 */
        data class NotApplicable(val reason: String) : Result
    }

    // ═══════════════ 主流程 ═══════════════

    /**
     * 从分钟级血氧序列算 ODI。
     *
     * [samples] 顺序无所谓（内部按时间排序）；重复时间戳与乱序都会被整理掉。
     * 给不出结论时返回 [Result.NotApplicable]，**绝不返回"看着正常"的 0**：
     * "没有监测到"与"没有事件"是两件事，混同会让用户以为自己很健康。
     */
    fun analyze(samples: List<Sample>): Result {
        val pts = samples
            .filter { it.spo2 in MIN_PLAUSIBLE..MAX_PLAUSIBLE }
            .sortedBy { it.tMs }
        val rejected = samples.size - pts.size

        if (pts.isEmpty()) {
            return Result.NotApplicable(
                if (samples.isEmpty()) Text.of("s_9bcba0a0")
                else Text.fmt("s_3d155d9d", samples.size, MIN_PLAUSIBLE, MAX_PLAUSIBLE)
            )
        }
        if (pts.size < MIN_POINTS) {
            return Result.NotApplicable(
                Text.fmt("s_65cd2d9e", pts.size, MIN_POINTS) +
                    Text.of("s_9bda4e04")
            )
        }

        // 监测时长与 T90 用同一套时间权重：每个点代表"到下一个点为止"的那段时间，
        // 这样两者的分母天然一致（不这样做会出现"T90 的分母和 ODI 的分母不是同一个数"）。
        // 最后一个点没有"下一段"，用它与前一个点的间隔代表它覆盖的时长 ——
        // 否则 n 个每分钟的点只算出 n-1 分钟，监测时长被系统性少算一段。
        var monitoredMs = 0L
        var belowMs = 0L
        for (i in 0 until pts.size - 1) {
            val dt = pts[i + 1].tMs - pts[i].tMs
            if (dt <= 0) continue
            val w = minOf(dt, MAX_GAP_MS)
            monitoredMs += w
            if (pts[i].spo2 < T90_THRESHOLD) belowMs += w
        }
        if (pts.size >= 2) {
            val dt = pts[pts.size - 1].tMs - pts[pts.size - 2].tMs
            if (dt > 0) {
                val w = minOf(dt, MAX_GAP_MS)
                monitoredMs += w
                if (pts[pts.size - 1].spo2 < T90_THRESHOLD) belowMs += w
            }
        }
        val monitoredMin = monitoredMs / 60_000.0
        if (monitoredMin < MIN_MONITORED_MIN) {
            return Result.NotApplicable(
                Text.fmt("s_c81c235e", formatMonitored(monitoredMin)) +
                    Text.fmt("s_3351fb99", MIN_MONITORED_MIN / 60) +
                    Text.of("s_52422292")
            )
        }

        var events = 0
        var inEvent = false
        var lowest = Int.MAX_VALUE
        var sum = 0.0
        for (i in pts.indices) {
            val x = pts[i].spo2
            if (x < lowest) lowest = x
            sum += x

            val base = baselineBefore(pts, i) ?: continue
            if (!inEvent) {
                if (x <= base - DROP_PCT) inEvent = true
            } else {
                // 事件进行中：回升到距基线 [RECOVER_PCT]% 以内即结束本次事件
                if (x >= base - RECOVER_PCT) {
                    inEvent = false
                    events++
                }
            }
        }
        // 末尾仍在下降的事件照样计数（理由见类注释"刻意不做的事"第 2 条）
        if (inEvent) events++

        val hours = monitoredMs / 3_600_000.0
        val odi = if (hours > 0.0) events / hours else 0.0
        val t90 = if (monitoredMs > 0L) belowMs * 100.0 / monitoredMs else 0.0

        return Result.Ok(
            events = events,
            odiPerHour = odi,
            monitoredMinutes = monitoredMin,
            validPoints = pts.size,
            rejectedPoints = rejected,
            lowest = lowest,
            mean = sum / pts.size,
            t90Pct = t90,
            spanMs = pts.last().tMs - pts.first().tMs,
        )
    }

    /**
     * 当前点**之前** [BASELINE_WINDOW_MIN] 分钟内有效读数的中位数。
     *
     * 严格"之前"（不含当前点）：下降幅度本来就是相对"此前水平"而言的，
     * 把当前这个已经掉下去的点算进自己的基线，等于自己抵消自己。
     * 点数不足 [MIN_BASELINE_POINTS] 返回 null（调用方据此跳过该点的判定）。
     */
    fun baselineBefore(pts: List<Sample>, index: Int): Int? {
        if (index <= 0 || index >= pts.size) return null
        val t = pts[index].tMs
        var from = index
        while (from > 0 && t - pts[from - 1].tMs <= BASELINE_WINDOW_MS) from--
        val count = index - from
        if (count < MIN_BASELINE_POINTS) return null
        val values = ArrayList<Int>(count)
        for (j in from until index) values.add(pts[j].spo2)
        values.sort()
        val mid = values.size / 2
        return if (values.size % 2 == 1) values[mid] else (values[mid - 1] + values[mid]) / 2
    }

    // ═══════════════ 展示辅助 ═══════════════

    /** ODI：一位小数 次/小时。 */
    fun formatOdi(v: Double?): String =
        v?.let { String.format(java.util.Locale.US, Text.of("s_6d7bdaff"), it) } ?: "--"

    /** 事件次数。 */
    fun formatEvents(n: Int?): String = n?.let { Text.fmt("s_861e538c", it) } ?: "--"

    /** 监测时长：按"X 小时 Y 分"。 */
    fun formatMonitored(minutes: Double?): String {
        if (minutes == null || minutes.isNaN()) return "--"
        val total = minutes.toLong().coerceAtLeast(0L)
        val h = total / 60
        val m = total % 60
        return when {
            h <= 0L -> Text.fmt("s_dc97892c", m)
            m == 0L -> Text.fmt("s_dbf0f917", h)
            else -> Text.fmt("s_eb9bac04", h, m)
        }
    }

    /** T90：一位小数百分比。 */
    fun formatT90(pct: Double?): String =
        pct?.let { String.format(java.util.Locale.US, "%.1f%%", it) } ?: "--"

    /**
     * 结果的一行摘要（列表/卡片副标题用）。
     * 刻意**不写"正常/异常"** —— 那会把一个筛查指标读成诊断结论，判断留给看的人。
     */
    fun summaryLine(r: Result.Ok): String =
        Text.fmt("s_8ff59bb6", formatEvents(r.events), formatOdi(r.odiPerHour), formatMonitored(r.monitoredMinutes))

    /** 方法说明（界面常驻，解释这数字是怎么来的）。 */
    val METHOD_NOTE: String get() =
        Text.of("s_a127948f") +
            Text.of("s_7854f345") +
            Text.of("s_8e2841fb")

    /** 为什么这里是分钟级、以及它意味着什么。 */
    val RESOLUTION_NOTE: String get() =
        Text.of("s_2566f88a") +
            Text.of("s_0a53a9a3") +
            Text.of("s_0b5a9d34")

    /** 免责声明（与分级一起出现，不能单独给分级）。 */
    val DISCLAIMER: String get() = Disclaimer.odi(Text.lang)

    /** 剔除说明：只在真的剔除了读数时显示（没有剔除时不该凭空出现）。 */
    fun rejectedNote(count: Int): String? =
        if (count <= 0) null
        else Text.fmt("s_0993f376", count, MIN_PLAUSIBLE, MAX_PLAUSIBLE) +
            Text.of("s_85f4f51c")
}
