package com.gemini.healthbridge.phone

import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.Stress
import com.gemini.healthbridge.core.TempSource
import com.gemini.healthbridge.core.HrvSource
import com.gemini.healthbridge.core.Text
import com.gemini.healthbridge.core.db.HrvSessionEntity
import com.gemini.healthbridge.core.db.TemperatureEntity
import com.gemini.healthbridge.core.db.ecgRegularFlag

/**
 * 压力 / 疲劳评分（模块 #12）的**取数与摘要构造**，手机端界面与蓝牙推送共用。
 *
 * ## 为什么要单独一个文件
 * 评分要被两处用到：手机端心电页的卡片（要完整结果）和推给手表的一行摘要（只要分数）。
 * 两处各写一遍"实体 -> [Stress.Obs]"的换算，就等于给了它们漂移的空间 ——
 * 手表上显示的分数与手机端卡片上的分数可能来自两套略有不同的输入，
 * 而分数**没有绝对标尺**，用户看不出哪个是对的。换算只有这一份，两边都调它。
 *
 * ## 这里只做换算，不做展示
 * 展示（卡片、进度条、文案）在 `MainActivity`；序列化与发送在 `PhoneBtServer`。
 */
object StressSummary {

    /**
     * 参与评分的历史记录上限。
     *
     * 每条都要跑一次 `Hrv.analyze`（含 Lomb-Scargle 频谱），不封顶就是把几百次频谱分析
     * 压进一次重组 / 一次蓝牙会话 —— 与 HRV 卡片基线只取 20 条同一条纪律。
     * 基线用 20 条、趋势用两个窗口各 5 条，40 条留足了"排除本次 + 排除未来记录"的余量。
     */
    const val HISTORY_MAX = 40

    /**
     * HRV 会话 -> 评分用的观测。**复用 [Hrv.analyze] 的输出**，不另算 RMSSD：
     * 界面上显示的 RMSSD 与评分用的必须是同一个数（界面值与判据同源）。
     *
     * 分析不出结果（伪迹过多 / 活动中 / 节律不齐）返回 null ——
     * 那条记录既不进历史，也不能当"本次"。
     */
    fun toObs(e: HrvSessionEntity): Stress.Obs? {
        val r = Hrv.analyze(
            rawRrMs = Hrv.decodeRr(e.rrMs),
            source = e.source,
            activityLevel = e.activityLevel,
            ecgRegular = e.ecgRegularFlag(),
        ) as? Hrv.Result.Ok ?: return null
        return Stress.Obs(e.measuredAt, r.rmssdMs, r.hrMean, e.activityLevel)
    }

    /**
     * 体温记录 -> 皮温−环境温梯度。
     *
     * **只收 `watch_temp`（绝对值）**：三星健康那一项是相对个人基线的偏差，量纲不同，
     * 混进来会得到一个看着正常、实际毫无意义的梯度 —— 与核心体温那处的输入纪律同一条。
     * 没有 `ambientC` 的记录直接跳过：凑不出的输入不拿默认值顶。
     */
    fun toTemps(list: List<TemperatureEntity>): List<Stress.TempObs> =
        list.mapNotNull { t ->
            if (t.source != TempSource.WATCH) return@mapNotNull null
            val amb = t.ambientC ?: return@mapNotNull null
            Stress.TempObs(t.measuredAt, t.celsius - amb)
        }

    /**
     * 只保留最近的 [HISTORY_MAX] 条并转成观测（先截断再换算，避免跑无用的频谱分析）。
     *
     * ⚠ **只用 `WATCH_ECG` 来源**：PPG 的逐拍间期（`WATCH_PPG`）是**另一套基线**
     * （脉搏间期 vs R 峰间期）。混进来会让这个既有的分数悄悄换掉口径 ——
     * 而"新数据源不得改动已有分数的含义"是本项目的一条硬纪律。
     *
     * 代价要说清：**只测 PPG、从不测心电的用户拿不到压力分**。
     * 让 PPG 参与需要给它**单独一套基线**（两套基线各自成立再谈并列展示），
     * 那是独立的一步，不在本次范围内。这条边界同样成立。
     */
    fun historyOf(hrv: List<HrvSessionEntity>): List<Stress.Obs> =
        hrv.asSequence()
            .filter { it.source == HrvSource.WATCH_ECG }
            .sortedByDescending { it.measuredAt }
            .take(HISTORY_MAX)
            .mapNotNull { toObs(it) }
            .toList()

    /**
     * 用最近一次可评分的记录算一份摘要。
     *
     * **总是返回一个消息**（而不是 null）：`available = false` 时带 [reason]，
     * 这样手表收到的是"当前状态"，而不是"没收到帧" —— 与经期摘要、BIA 档案同一条纪律：
     * "算不出来"必须能被传递，否则手表会一直挂着上一次的旧分数。
     */
    fun build(
        hrv: List<HrvSessionEntity>,
        temps: List<TemperatureEntity>,
        nowMs: Long = System.currentTimeMillis(),
    ): com.gemini.healthbridge.core.StressSummaryMessage {
        val newest = hrv.asSequence()
            // 与 [historyOf] 同一条：压力分只认心电来源（见那里的注释）。
            .filter { it.source == HrvSource.WATCH_ECG }
            .sortedByDescending { it.measuredAt }
            .mapNotNull { toObs(it) }
            .firstOrNull()
            ?: return com.gemini.healthbridge.core.StressSummaryMessage(
                available = false,
                computedAt = nowMs,
                reason = Text.of("s_c4caa988"),
            )
        // history 里要含"本次之外的其它记录"，Stress.analyze 会按时刻排除本次
        val history = historyOf(hrv)
        val tObs = toTemps(temps)
        return when (val r = Stress.analyze(newest, history, tObs, nowMs)) {
            is Stress.Result.Ok -> com.gemini.healthbridge.core.StressSummaryMessage(
                available = true,
                computedAt = nowMs,
                measuredAt = newest.measuredAt,
                stressScore = r.stress.value,
                stressLevel = r.stress.level,
                fatigueScore = r.fatigue?.value,
                fatigueLevel = r.fatigue?.level,
                baselineCount = r.baseline.count,
            )
            is Stress.Result.NotApplicable -> com.gemini.healthbridge.core.StressSummaryMessage(
                available = false,
                computedAt = nowMs,
                measuredAt = newest.measuredAt,
                reason = r.reason,
            )
        }
    }
}
