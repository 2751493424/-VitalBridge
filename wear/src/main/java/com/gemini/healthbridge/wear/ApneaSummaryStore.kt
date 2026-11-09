package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Apnea
import com.gemini.healthbridge.core.ApneaSummaryMessage
import com.gemini.healthbridge.core.Text
import kotlinx.serialization.json.Json

/**
 * 手表本地缓存的睡眠呼吸暂停摘要。
 *
 * 数据链是：**三星健康监测器**（在手表上检测）→ 用户授权同步 → 三星健康 →
 * 手机端（Data SDK 读）→ 蓝牙推给手表。
 * 手表自己没有三星健康 Data SDK，所以**读不到原始记录，只能显示手机推来的这一条**。
 *
 * ## 缓存的初值可以直接用
 * 与 `OdiSummaryStore` 一样，手机在**一条记录都没有时不推这一帧**，
 * 所以"手机从没同步过"不会走到这里把已有摘要清掉（空读取不许清屏）。
 */
object ApneaSummaryStore {

    private const val PREF = "vitaltrace_apnea"
    private const val KEY_JSON = "summary_json"

    private val json = Json { ignoreUnknownKeys = true }

    fun save(context: Context, msg: ApneaSummaryMessage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json.encodeToString(ApneaSummaryMessage.serializer(), msg))
            .apply()
    }

    fun load(context: Context): ApneaSummaryMessage? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return null
        return runCatching { json.decodeFromString(ApneaSummaryMessage.serializer(), s) }
            .getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * 详情页里那一句结果。
 *
 * ⚠ **只报"迹象"，不下结论**：`NOT_DETECTED` 不等于"没有睡眠呼吸暂停"，
 * `UNDEFINED` 更不能当成阴性。这一条措辞的边界由 `Apnea`（core）统一维护，
 * 手机与手表共用同一套文案，不各写一遍。
 */
fun ApneaSummaryMessage.resultText(): String = when (Apnea.normalize(sign)) {
    Apnea.DETECTED -> Text.of("s_42dc3e3e")
    Apnea.NOT_DETECTED -> Text.of("s_5d3beb60")
    else -> Text.of("s_7ebfb967")
}

/** 手表首页那一行：`9月18日 检测到迹象`。 */
fun ApneaSummaryMessage.glanceText(): String = "${shortDate()} ${Apnea.label(sign)}"

/** 短日期（如「9月18日」），归属日无效时返回空串。 */
fun ApneaSummaryMessage.shortDate(): String {
    if (dateEpochDay <= 0L) return ""
    val d = java.time.LocalDate.ofEpochDay(dateEpochDay)
    return Text.fmt("s_4577f457", d.monthValue, d.dayOfMonth)
}
