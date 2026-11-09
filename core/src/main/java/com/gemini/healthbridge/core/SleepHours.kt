package com.gemini.healthbridge.core

import java.time.Instant
import java.time.ZoneId

/**
 * 睡眠时段的**每小时槽位**划分（睡眠页的血氧 / 心率柱状图用）。
 *
 * ### 为什么不复用「今天 0 点」做原点
 *
 * 血氧页/心率页那套 `bucketByHour` 以**今天 0 点**为原点。那是对的 ——
 * 它们画的是"今日全天"，原点就该是今天 0 点。但睡眠页画的是**昨晚那一觉**，
 * 而睡眠天然跨午夜（23:10 入睡、07:05 起床是常态）。换成 0 点做原点会出两个错，
 * 而且**两个都不报错、只是图上少东西或标签顺序怪**：
 *
 * 1. **负数侧被压缩：两个真实小时挤进同一格。** Kotlin 的整数除法是**向零截断**，
 *    不是 floor —— 以今天 0 点为原点时，`23:10` 算出来是桶 `0`（`-40min / 1h == 0`），
 *    于是 `23:00~23:59` 与 `00:00~00:59` 的采样进了**同一格**，
 *    那根柱子的 min/max 混了两个小时，柱高直接是错的。
 * 2. **更早的格被静默丢弃。** 入睡再早一点（`22:00~22:59`）算出桶 `-1`，
 *    而那套实现里那句 `filterKeys { it in 0..23 }` 会把它**扔掉**。
 *    图上只是少一根柱子，和"那个小时本来就没采样"长得一模一样。
 *
 * （横轴标签同样会错位：装着 `23:10` 数据的那一格，标签是 `0`。）
 *
 * 所以这里改用**入睡时刻**做原点：槽位是"第几个小时"（0,1,2… 严格随时间递增），
 * 钟点标签由 [slotHours] 单独给。跨午夜时标签会出现 `23 → 0` 的跳变 ——
 * 那是真实时间，不是错误。
 *
 * ### 为什么放进 core
 *
 * 上面两条都是**静默**错误，只有断言抓得住，所以判据必须放在能被台架直接编译的位置
 * （见离线校验台架）。这里只有纯计算，不含任何 Android 依赖。
 */
object SleepHours {

    /** 一小时（毫秒）。 */
    const val HOUR_MS = 3_600_000L

    /** 某个时刻所在整点（向下取整）。 */
    fun originOf(tMs: Long): Long = tMs - Math.floorMod(tMs, HOUR_MS)

    /**
     * 入睡→起床覆盖到的整点**个数**。
     *
     * 例：23:10 入睡、07:05 起床 → 覆盖 23、0、1、2、3、4、5、6、7 共 **9** 格。
     * 注意不是「时长 7 小时 55 分 → 7 格」：**两端的半截小时也各占一格**，
     * 只是入睡那一格的柱高由 23:10~23:59 的采样决定，早醒那一格同理。
     *
     * @return 时长为 0 或负（异常输入）返回 0 —— 调用方据此走"没有数据"分支
     */
    fun slotCount(startMs: Long, endMs: Long): Int {
        if (endMs <= startMs) return 0
        return ((originOf(endMs) - originOf(startMs)) / HOUR_MS).toInt() + 1
    }

    /** 第 [slot] 格（整点）的时间戳。 */
    fun slotStartMs(startMs: Long, slot: Int): Long = originOf(startMs) + slot * HOUR_MS

    /**
     * 某个时间戳落在第几格；落在睡眠时段之外返回 `null`。
     *
     * ⚠ 这里**刻意不做** `in 0..23` 那种过滤（那正是上面第 2 条错误）：
     * 只判 `0 until count`，所以"昨天 23 点"这种跨午夜的采样照常有格子。
     * 判定以**整点格**为单位、不比精确时刻，所以起床是 `07:05` 时 `07:59` 仍算最后一格
     * —— 传进来的采样本来就只含睡眠时段，不会混进白天数据；真正越界的是 `08:00`。
     */
    fun slotOf(tMs: Long, startMs: Long, count: Int): Int? {
        if (count <= 0) return null
        val s = Math.floorDiv(tMs - originOf(startMs), HOUR_MS).toInt()
        return if (s in 0 until count) s else null
    }

    /**
     * 每一格的**钟点**标签（0~23），与 [slotCount] 的结果一一对应。
     *
     * 走 `ZoneId` 而不是自己从毫秒里除时分秒：必须用系统时区，
     * 否则跨夏令时切换的那一晚整条横轴会错一小时。
     */
    fun slotHours(startMs: Long, count: Int, zone: ZoneId = ZoneId.systemDefault()): List<Int> {
        val o = originOf(startMs)
        return (0 until count).map { s ->
            Instant.ofEpochMilli(o + s * HOUR_MS).atZone(zone).hour
        }
    }

    // ═══════════════════ 主睡眠会话的挑选 ═══════════════════

    /**
     * 一段候选睡眠会话。
     *
     * @param startMs 入睡时刻
     * @param endMs 醒来时刻（必须 > [startMs]，调用方先过滤）
     */
    data class Span(val startMs: Long, val endMs: Long) {
        val durationMs: Long get() = endMs - startMs
    }

    /**
     * 从窗口内的若干睡眠会话里挑出**主睡眠**（"最近一晚"）那一场。
     *
     * ## 为什么不能取"endTime 最晚的那个"（2026-09-29 修）
     *
     * 原实现取 `endTime` 最晚的会话。这在"一夜睡到天亮"时没问题，
     * 但**白天午睡之后它就错了**：昨晚 23:10~07:05 睡了一觉，中午 13:00~14:00 又小睡，
     * 午睡的 `endTime` 更晚 → 它被当成了"最近一晚"。用户看到的是
     * **整晚睡眠凭空消失**、只剩一段 1 小时的"睡眠"（截图反馈就是这个）。
     * 而这件事**不报错**：睡眠页照样有数、只是那个数不是他想看的。
     *
     * ## 判据：先按时长，再按"更像夜间"
     *
     * 主睡眠与午睡在**时长**上差一个数量级（7 小时 vs 1 小时），所以：
     * 1. **最长的那一场就是主睡眠** —— 这一条能覆盖绝大多数情况，且不依赖时刻；
     * 2. 时长相同时（罕见），取**开始得更早**的那一场（夜间睡眠通常早于午睡）；
     * 3. 全都拿不到时长信息时，退化为"开始最早"的那一场。
     *
     * ⚠ 刻意**不**用"是否跨午夜"或"是否落在 22:00~08:00"这类时钟判据做主导：
     * 夜班人群的"主睡眠"就在白天，用钟点判会把他们的整晚睡眠丢掉 ——
     * 那正是本函数要修的同一个错误，只是换了个方向。
     *
     * @return 主睡眠会话；[spans] 为空时返回 null（调用方据此走"没有睡眠数据"）
     */
    fun mainSession(spans: List<Span>): Span? {
        val valid = spans.filter { it.endMs > it.startMs }
        if (valid.isEmpty()) return null
        // maxByOrNull 在并列时返回**第一个**最大值，所以先用"最早的在前"排好序，
        // 并列时自然落到更早开始的那一场（上面的第 2 条判据）。
        return valid.sortedBy { it.startMs }.maxByOrNull { it.durationMs }
    }

    /**
     * 主睡眠之外的**其它**会话（午睡、补觉），按开始时间升序。
     *
     * 界面用它显示"另外还有 N 段小睡"，让用户知道本页看的是主睡眠、
     * 而午睡没有被丢掉（只是不计入"昨晚"这一场）。
     */
    fun otherSessions(spans: List<Span>, main: Span): List<Span> =
        spans.filter { it.endMs > it.startMs && it != main }.sortedBy { it.startMs }
}
