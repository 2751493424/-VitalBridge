package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Stress
import com.gemini.healthbridge.core.StressSummaryMessage
import com.gemini.healthbridge.core.Text
import kotlinx.serialization.json.Json

/**
 * 手表本地缓存的压力 / 疲劳评分摘要（模块 #12）。
 *
 * 评分由**手机端算好**推过来（见 `FrameKinds.STRESS_SUMMARY` 的说明）：
 * 分数是相对这个人自己的基线，而基线要用全部历史记录 —— 手机端才有一份完整的。
 * 手表自己算会与手机端给出两个不同的分数，而分数没有绝对标尺，用户无从判断哪个对。
 *
 * ## 为什么存 SharedPreferences 而不是数据库
 * 与 `CycleSummaryStore` 同理：它是**单条状态**（"最近一次算出来的分数"），
 * 不是时序记录，写入即覆盖，不需要备份（算法一改，整份摘要的口径就变了，
 * 备份一个旧口径的分数没有意义 —— 与"不落库、现算"是同一条理由）。
 *
 * ## 它过期的方式与其它推送都不同
 * 血氧/睡眠摘要靠 `dateEpochDay` 跨天失效；经期摘要靠"用户又记了一次"被覆盖。
 * 而这份摘要失效的原因是**基线变了** —— 多测一次心电，基线就多一条记录，
 * 之前那个分数就不再成立。跨天不是它的失效条件，所以这里用 [Stress.SUMMARY_FRESH_MS]
 * （按小时）而不是按日期判断，并在界面上标出"算于多久以前"。
 */
object StressSummaryStore {

    private const val PREF = "vitaltrace_stress"
    private const val KEY_JSON = "summary_json"

    private val json = Json { ignoreUnknownKeys = true }

    /** 写入摘要（available=false 也照写：那是"这次算不出来"，必须能被手表知道）。 */
    fun save(context: Context, msg: StressSummaryMessage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json.encodeToString(StressSummaryMessage.serializer(), msg))
            .apply()
    }

    /** 读回摘要；从未收到过时返回 null（界面据此显示"待同步"）。 */
    fun load(context: Context): StressSummaryMessage? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return null
        return runCatching { json.decodeFromString(StressSummaryMessage.serializer(), s) }
            .getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * 首页一行：`压力 68 偏高 · 疲劳 67 偏高`。
 *
 * 疲劳给不出时显示"疲劳 数据不足"而不是省略 —— 省略会让用户以为"疲劳很低"，
 * 而真相是"还没测够次数"。这与 HRV 那处"不适用时也必须出声"是同一条纪律。
 */
fun StressSummaryMessage.glanceText(): String {
    if (!available) return Text.of("s_b1d1e208")
    val s = Text.fmt("s_16ad28e9", stressScore, Stress.levelShort(stressLevel))
    val f = fatigueScore
    return if (f != null) {
        Text.fmt("s_74a50900", s, f, Stress.levelShort(fatigueLevel ?: ""))
    } else {
        Text.fmt("s_d209dafc", s)
    }
}

/**
 * 详情页里那句"算于多久以前"。
 *
 * 超过 [Stress.SUMMARY_FRESH_MS] 时多给一句提示 —— 手表上挂着的分数**随时可能过时**，
 * 而分数本身看不出新旧，不标出来就会被当成"现在的"。
 */
fun StressSummaryMessage.freshnessText(nowMs: Long): String {
    if (!available || computedAt <= 0L) return ""
    val age = Stress.ageText(computedAt, nowMs)
    return if (Stress.isStale(computedAt, nowMs)) {
        Text.fmt("s_bcb1ad4a", age)
    } else {
        Text.fmt("s_6f3d6338", age)
    }
}
