package com.gemini.healthbridge.phone

import com.gemini.healthbridge.core.Energy
import com.gemini.healthbridge.core.EnergySummaryMessage
import com.gemini.healthbridge.core.Text
import com.gemini.healthbridge.core.db.ActivityDayEntity
import com.gemini.healthbridge.core.db.EnergyScoreEntity
import com.gemini.healthbridge.core.db.SleepNightEntity

/**
 * 自研能量评分的**取数与摘要构造**，手机端界面与蓝牙推送共用。
 *
 * ## 为什么必须只有一份
 * 这个分要被两处用到：手机端能量评分页（要完整分解）与推给手表的一行摘要（只要一个数）。
 * 两处各写一遍"实体 -> [Energy.Night]"的换算，就等于给了它们漂移的空间 ——
 * 手表上的 71 分与手机上的 73 分**用户看不出哪个是对的**，因为分数没有绝对标尺。
 * 换算只有这一份，两边都调它。这与 `StressSummary` 是同一个理由、同一个形态。
 *
 * ## 这里只做换算，不做展示
 * 展示（卡片、柱状图、文案）在 `MainActivity`；序列化与发送在 `PhoneBtServer`。
 */
object EnergySummary {

    /**
     * 参与评分的历史晚数上限。
     *
     * 一晚一行、算法是加减乘除（不像 HRV 那样要跑频谱），所以这里可以比
     * `StressSummary.HISTORY_MAX` 宽松得多：一年 400 晚也就几百次算术。
     * 但**不无限**：`sleepNightDao` 自己保留 400 晚，这里取 200 是给趋势图留够余量，
     * 同时避免把"两年前那套已经与现在无关的作息"算进基线。
     */
    const val NIGHTS_MAX = 200

    /** 趋势图取多少晚。与 `EnergyTab` 画的那张柱状图一致。 */
    const val SERIES_NIGHTS = 14

    /**
     * 睡眠夜行 + 它的"前一日活动" -> 评分输入。逐字段搬，不做任何解释。
     *
     * ⚠ [prev] 是**醒来日的前一天**（`dateEpochDay - 1`）那条活动记录。
     *    两张表的归属日口径不同：`sleep_nights` 按**醒来那天**、
     *    `activity_days` 按**自然日**。所以"这一晚之前的那个白天"是 `dateEpochDay - 1` ——
     *    这个减法**只写在 [nights] 那一处**，不散落在别处：
     *    藏起来的日期换算必然错一天，而错一天的后果是"昨晚的分用了前天的活动量"，
     *    分数照样算得出来、看起来也正常。
     */
    fun toNight(e: SleepNightEntity, prev: ActivityDayEntity? = null): Energy.Night = Energy.Night(
        dateEpochDay = e.dateEpochDay,
        sleepMs = e.actualSleepMs,
        hrAvg = e.hrAvg?.toDouble(),
        spo2Min = e.spo2Min,
        // 入睡时刻：一致性 / 规律性 / 定时三项都只用它，所以缺了就是三项一起缺席。
        bedMs = e.startMs,
        hrSd = e.hrSd,
        // 皮温原样传：量纲（相对基线的偏差 / 绝对温度）由 `Energy` 自己把关，
        // 这里替它判断一次就等于把同一个判断写两遍，两边迟早不一致。
        skinTempDev = e.skinTemp,
        prevDaySteps = prev?.steps,
        prevDaySlots = parseSlots(prev?.slotSteps),
        samsungScore = e.samsungScore,
    )

    /**
     * 把落库的 `"1200,3400,0,…"` 解析成段数组。
     *
     * 解析**绝不抛异常、段数不对就返回 null**：这一列是"当时读到了什么"的快照，
     * 它可能被手改、可能在将来的版本里换了分隔符 —— 那种情况下正确反应是
     * 让「活动持续性」那一项**缺席**，而不是让整页崩掉或拿一个缺了几段的数组硬算。
     */
    private fun parseSlots(s: String?): List<Long>? {
        if (s.isNullOrBlank()) return null
        val v = s.split(',').mapNotNull { it.trim().toLongOrNull() }
        return v.takeIf { it.size == Energy.ACTIVITY_SLOTS }
    }

    /** 最近 [NIGHTS_MAX] 晚（新的在前），转成评分输入（含各自的前一日活动）。 */
    fun nights(
        list: List<SleepNightEntity>,
        activity: List<ActivityDayEntity> = emptyList(),
    ): List<Energy.Night> {
        val byDay = activity.associateBy { it.dateEpochDay }
        return list.asSequence()
            .sortedByDescending { it.dateEpochDay }
            .take(NIGHTS_MAX)
            .map { toNight(it, byDay[it.dateEpochDay - 1]) }
            .toList()
    }

    /** 今天（本地时区的 epochDay）。**只有这一处**算它，界面与推送不会算出两个"今天"。 */
    fun todayEpochDay(): Long = java.time.LocalDate.now().toEpochDay()

    /**
     * 算最近一晚的分。
     *
     * 没有睡眠记录时 [Energy.analyze] 收到的是一个三项全空的 `Night`，会给出拒答理由 ——
     * 这里**不提前返回**，让拒答理由始终由 `Energy` 那一处产生：
     * 理由只有一份，界面上写的和手表上看到的才不会是两句话。
     */
    fun analyze(
        list: List<SleepNightEntity>,
        activity: List<ActivityDayEntity> = emptyList(),
        stepGoal: Int? = null,
        todayEpochDay: Long = todayEpochDay(),
    ): Energy.Result {
        val all = nights(list, activity)
        val latest = all.maxByOrNull { it.dateEpochDay } ?: Energy.Night(0L)
        return Energy.analyze(latest, all, todayEpochDay, stepGoal)
    }

    /**
     * 组装推给手表的摘要 —— **两个分装在同一个帧里**：三星的与本 App 的。
     *
     * 与压力/经期同一条纪律：**算不出来也返回一个消息**（`ownAvailable = false` + 原因），
     * 而不是返回 null。能量评分是"今天的当前状态"，推空是**正确**的更新；
     * 返回 null 会让手表继续挂着上一次的旧分数冒充今天。
     */
    fun build(
        nights: List<SleepNightEntity>,
        samsung: EnergyScoreEntity?,
        activity: List<ActivityDayEntity> = emptyList(),
        stepGoal: Int? = null,
        nowMs: Long = System.currentTimeMillis(),
        todayEpochDay: Long = todayEpochDay(),
    ): EnergySummaryMessage {
        val res = analyze(nights, activity, stepGoal, todayEpochDay)
        val ok = res as? Energy.Result.Ok
        return EnergySummaryMessage(
            // ── 三星那一份 ──
            available = samsung != null,
            score = samsung?.score ?: 0,
            dateEpochDay = samsung?.dateEpochDay ?: 0L,
            recent = emptyList(),
            atMs = nowMs,
            // ── 本 App 那一份 ──
            ownAvailable = ok != null,
            ownScore = ok?.score ?: 0,
            ownLevel = ok?.level.orEmpty(),
            ownDateEpochDay = ok?.dateEpochDay ?: 0L,
            ownNote = ok?.let { shortNote(it) }
                ?: truncate((res as Energy.Result.NotApplicable).reason),
        )
    }

    /**
     * 手表上那一行提示：**必须短**。
     *
     * 完整的分量分解与口径说明在手机端；手表只有一个 1.4 英寸的圆屏，
     * 把 `Energy.Ok.caveats` 整段推过去只会被截断成半句话。
     * 所以这里只挑出**最需要在手表上说清的一件事**：
     *  ① 不是今天的（会导致用户把旧分数当成今天的状态）优先级最高；
     *  ② 其次说"只有部分分量参与"；
     *  ③ 都没有就留空（手表显示分数就够了）。
     */
    private fun shortNote(r: Energy.Result.Ok): String = when {
        r.staleDays > 0L ->
            Text.fmt("s_32ae46fc", Energy.dateLabel(r.dateEpochDay))
        r.staleDays < 0L -> Text.fmt("s_cac415ca", Energy.dateLabel(r.dateEpochDay))
        // 阈值跟着分量总数走：3 个分量时"少于 3"就是部分参与，
        // 现在有 10 个，少于 6 个才算 —— 写死 3 会让"只有 4 项参与"被当成完整评分。
        r.parts.size < 6 -> Text.fmt("s_d1aa82e3", r.parts.size)
        else -> ""
    }

    /** 手表圆屏放不下的原因文本一律截断，并**明确加上省略号**（不假装说完了）。 */
    private fun truncate(s: String, max: Int = 60): String =
        if (s.length <= max) s else s.take(max) + "…"
}
