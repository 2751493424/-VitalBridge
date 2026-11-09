package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Extras
import com.gemini.healthbridge.core.ExtrasSummaryMessage
import com.gemini.healthbridge.core.Text
import kotlinx.serialization.json.Json
import java.time.LocalDate

/**
 * 手表本地缓存的「三星扩展指标」摘要（活动 / 饮食 / 血糖 / 心律不齐）。
 *
 * ## 为什么存 SharedPreferences 而不是数据库
 * 与 `EnergySummaryStore` / `StressSummaryStore` / `CycleSummaryStore` / `OdiSummaryStore` 同理：
 * 它是**一帧当前状态**，写入即覆盖；手表端也不展示历史（圆屏放不下有意义的列表，明细在手机端）。
 *
 * ## 兼容性
 * `ExtrasSummaryMessage` 新增字段全部带默认值，所以**旧版本写下的缓存 JSON 仍能解析**
 * （`ignoreUnknownKeys` + 默认值两条都要有）。去掉任何一条，用户升级后冷启动时
 * 这四页会直接空白 —— 而且不报错，只是没数据。
 */
object ExtrasSummaryStore {

    private const val PREF = "vitaltrace_extras"
    private const val KEY_JSON = "summary_json"

    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, msg: ExtrasSummaryMessage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json.encodeToString(ExtrasSummaryMessage.serializer(), msg))
            .apply()
    }

    /** 读回摘要；从未收到过时返回 null（界面据此显示"待同步"）。 */
    fun load(context: Context): ExtrasSummaryMessage? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return null
        return runCatching { json.decodeFromString(ExtrasSummaryMessage.serializer(), s) }
            .getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/** epochDay -> 「9月18日」；非法值返回空串（宁可少显示一行，也不显示 1970 年）。 */
private fun dayLabel(epochDay: Long): String {
    if (epochDay <= 0L) return ""
    val d = LocalDate.ofEpochDay(epochDay)
    return Text.fmt("s_4577f457", d.monthValue, d.dayOfMonth)
}

/**
 * 「今日」/「9月18日」—— **只有确实是今天的才写「今日」**。
 *
 * 手表冷启动时显示的是本地缓存，可能已经是两三天前的那一份。
 * 那时候还写「今日 8123 步」，用户会以为是自己今天的活动量 ——
 * 与 `EnergySummaryStore` 里那条"不是今天的必须把日期写出来"是同一条纪律。
 */
fun ExtrasSummaryMessage.todayLabel(todayEpochDay: Long): String =
    if (dateEpochDay == todayEpochDay) Text.of("s_0028b743") else dayLabel(dateEpochDay).ifEmpty { Text.of("s_00285a3f") }

/** 「更多功能」入口标签：活动。没有数据时只留名字，**不显示 0 步**。 */
fun ExtrasSummaryMessage.activityGlance(todayEpochDay: Long): String {
    val s = steps
    return if (s == null) Text.of("s_0034da66") else Text.fmt("s_d012fdee", todayLabel(todayEpochDay), s)
}

/** 「更多功能」入口标签：饮食。 */
fun ExtrasSummaryMessage.dietGlance(todayEpochDay: Long): String {
    val k = kcalToday
    return if (k == null) Text.of("s_0034da66") else Text.fmt("s_df267302", todayLabel(todayEpochDay), k.toInt())
}

/** 「更多功能」入口标签：血糖。走 core 的 `fmtGlucose`（单位红线：mmol/L）。 */
fun ExtrasSummaryMessage.glucoseGlance(): String {
    val g = glucoseMmol
    return if (g == null) Text.of("s_0034da66") else Extras.fmtGlucose(g)
}

/**
 * 「更多功能」入口标签：心律不齐。
 *
 * ⚠ 用措辞「有记录 / 无记录」而不是「正常 / 异常」—— 它只有 `DETECTED` / `UNDEFINED`
 * 两个取值，**没有 `NOT_DETECTED`**，所以"无记录"根本不等于"没问题"。
 */
fun ExtrasSummaryMessage.ihrnGlance(): String = when {
    !ihrnReadable -> Text.of("s_24cfdfaa")
    ihrnAtMs <= 0L -> Text.of("s_1af50f45")
    else -> Text.of("s_1b42d4b6")
}
