package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Energy
import com.gemini.healthbridge.core.EnergySummaryMessage
import com.gemini.healthbridge.core.Text
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 手表本地缓存的能量评分摘要。
 *
 * 摘要里装着**两个分**：本 App 自己算的（`Energy`）与三星健康的。
 * 手表上没有三星的 Data SDK，也没有那两个分的原始输入（睡眠统计在手机端的库里），
 * 所以两份都只能由**手机端算好推过来**（见 `FrameKinds.ENERGY_SUMMARY`）。
 *
 * ## 为什么存 SharedPreferences 而不是数据库
 * 与 `StressSummaryStore` / `CycleSummaryStore` / `OdiSummaryStore` 同理：它是**单条状态**，
 * 写入即覆盖，而且手表端不展示历史趋势（圆屏放不下一张有意义的趋势图，明细在手机端）。
 * 需要保留历史的是手机端那两张表（`sleep_nights` / `energy_scores`）。
 *
 * ## 兼容性
 * `EnergySummaryMessage` 新增的字段全部带默认值，所以**旧版本写下的缓存 JSON 仍能解析**
 * （`ignoreUnknownKeys` + 默认值两条都要有）。去掉任何一条，用户升级后冷启动时
 * 这一页会直接空白 —— 而且不报错，只是没数据。
 */
object EnergySummaryStore {

    private const val PREF = "vitaltrace_energy"
    private const val KEY_JSON = "summary_json"

    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, msg: EnergySummaryMessage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json.encodeToString(EnergySummaryMessage.serializer(), msg))
            .apply()
    }

    /** 读回摘要；从未收到过时返回 null（界面据此显示"待同步"）。 */
    fun load(context: Context): EnergySummaryMessage? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return null
        return runCatching { json.decodeFromString(EnergySummaryMessage.serializer(), s) }
            .getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** epochDay -> 「9月18日」；非法值返回空串（宁可少显示一行，也不显示 1970 年）。 */
private fun dayText(epochDay: Long): String {
    if (epochDay <= 0L) return ""
    val d = LocalDate.ofEpochDay(epochDay)
    return Text.fmt("s_4577f457", d.monthValue, d.dayOfMonth)
}

/**
 * 「62 分」/「9月18日 62 分」—— **不是今天的必须把日期写出来**。
 *
 * 这个分支的全部意义就在这里：能量评分是"给**今天**用的准备度"，
 * 一个三天前的 62 分和今天的 62 分含义完全不同。手表冷启动时显示的是本地缓存，
 * 所以这个情况一定会遇到 —— 让用户看到「9月18日 62 分」，
 * 比看到「62 分」然后以为是自己今天的状态要好。
 */
private fun scoreWithDay(score: Int, dateEpochDay: Long, todayEpochDay: Long): String {
    if (dateEpochDay == todayEpochDay || dateEpochDay <= 0L) return Text.fmt("s_e687a298", score)
    return Text.fmt("s_5876d5d1", dayText(dateEpochDay), score)
}

/**
 * 首页那一行文案 —— **主显本 App 自己算的分**。
 *
 * 为什么主显它：三星那个分要 Watch7+ 与三星账号、还分区域放开，很多机器上根本没有；
 * 本 App 这个只用本机采到的睡眠数据，**任何机器上都给得出**。
 * 自己那个算不出来时才退回显示三星的（别让这一行白白空着，用户至少知道有个数）。
 *
 * 两边都没有就说「暂无数据」，**不显示 0** —— 0 会被读成"你昨晚状态极差"。
 */
fun EnergySummaryMessage.glanceText(todayEpochDay: Long): String = when {
    ownAvailable -> scoreWithDay(ownScore, ownDateEpochDay, todayEpochDay)
    available -> Text.fmt("s_970e14a6", score)
    else -> Text.of("s_d738ab54")
}

/** 详情页里本 App 那一侧的一行（等级 + 算的是哪一晚）。 */
fun EnergySummaryMessage.ownCaption(todayEpochDay: Long): String {
    if (!ownAvailable) return ownNote.ifEmpty { Text.of("s_a41fbf35") }
    val level = Energy.levelLabel(ownLevel)
    val day = if (ownDateEpochDay == todayEpochDay || ownDateEpochDay <= 0L) {
        Text.of("s_0028aa87")
    } else {
        Text.fmt("s_8ee93988", dayText(ownDateEpochDay))
    }
    return "$level · $day"
}

/** 详情页里三星那一侧的一行。 */
fun EnergySummaryMessage.samsungText(): String = when {
    !available -> Text.of("s_51d9b49c")
    dateEpochDay <= 0L -> Text.fmt("s_e687a298", score)
    else -> Text.fmt("s_12ee6c7e", dayText(dateEpochDay), score)
}
