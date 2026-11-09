package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Odi
import com.gemini.healthbridge.core.OdiSummaryMessage
import com.gemini.healthbridge.core.Text
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * 手表本地缓存的氧减指数摘要（模块 #11）。
 *
 * ODI 由**手机端算好**推过来（见 `FrameKinds.ODI_SUMMARY` 的说明）：它的输入是三星健康
 * 睡眠期间的分钟级血氧序列，而手表上没有三星健康 Data SDK —— 手表**没有原始数据可算**，
 * 与呼吸率（两端都能从本地已落库的波形现算）不同。
 *
 * ## 为什么存 SharedPreferences 而不是数据库
 * 与 `StressSummaryStore` / `CycleSummaryStore` 同理：它是**单条状态**（"最近一晚"），
 * 写入即覆盖。ODI 虽然是一晚一份的既成事实、理论上值得存历史，但手表端**不展示历史趋势**
 * （圆屏放不下一张有意义的趋势图，明细都在手机端），存多份只是占地方。
 * 真正需要保留历史的是手机端那张 `sleep_odi` 表，它也进了备份。
 *
 * ## 它过期的方式：按"哪一晚"，不按"多久以前"
 * 压力摘要的失效原因是**基线变了**（多测一次就变），所以它按时长判断时效；
 * 而 ODI 是那一晚的既成事实，**不会因为后来多记录几晚而失真**。
 * 所以这里不标"算于多久以前"，而是让界面如实说出**这是哪一晚**：
 * 当天的显示「昨晚」，更早的直接显示日期（[nightLabel]）。
 * 宁可让用户看到「9月14日」，也不要让三天前的数看起来像今天早上的。
 */
object OdiSummaryStore {

    private const val PREF = "vitaltrace_odi"
    private const val KEY_JSON = "summary_json"

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 写入摘要。
     *
     * 注意调用方 `PhoneBtServer.pushOdiSummary` 在**手上一条记录都没有时不推** ——
     * 所以"手机从没同步过"不会走到这里把已有摘要清掉，与血氧/睡眠那条"空读取不许清屏"同源。
     */
    fun save(context: Context, msg: OdiSummaryMessage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json.encodeToString(OdiSummaryMessage.serializer(), msg))
            .apply()
    }

    /** 读回摘要；从未收到过时返回 null（界面据此显示"待同步"）。 */
    fun load(context: Context): OdiSummaryMessage? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return null
        return runCatching { json.decodeFromString(OdiSummaryMessage.serializer(), s) }
            .getOrNull()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * 这是哪一晚：当天的[dateEpochDay]显示「昨晚」，更早显示日期（如「9月14日」）。
 *
 * 用归属日与手表自己的"今天"比较，**不依赖手机端的推送时刻** ——
 * 用户在第二天打开手表时，与昨天收到的同一份摘要会被正确地标成「9月16日」。
 */
fun OdiSummaryMessage.nightLabel(todayEpochDay: Long): String {
    if (dateEpochDay <= 0L) return ""
    return if (dateEpochDay == todayEpochDay) {
        Text.of("s_0034acd2")
    } else {
        val d = Date(TimeUnit.DAYS.toMillis(dateEpochDay))
        SimpleDateFormat(Text.of("s_254ca7f8"), Locale.getDefault()).format(d)
    }
}

/**
 * 首页那一行：`氧减 昨晚 3.2 次/小时 · 轻度`。
 *
 * 拿不到结论时**说原因**（"数据不足"），不许显示一个 0 ——
 * "没监测到"与"没有氧减"是两件事，后者会让用户以为自己很健康。
 */
fun OdiSummaryMessage.glanceText(todayEpochDay: Long): String {
    val whenText = nightLabel(todayEpochDay)
    val prefix = if (whenText.isEmpty()) "" else "$whenText "
    if (!available) return Text.fmt("s_71bb0e68", prefix)
    val band = Odi.bandLabel(band)
    return "$prefix${Odi.formatOdi(odiPerHour)} · $band"
}

/**
 * 详情页的一行口径：睡眠时段 + 分子分母。
 *
 * 把"事件数 / 监测时长"一起给出来是刻意的：ODI 是**比值**，只给比值的话，
 * 一个"监测了 2 小时、4 次事件"的 ODI 2.0 与"整夜 8 小时、16 次事件"的 ODI 2.0
 * 在手表上看起来完全一样，而它们的证据强度差很远。
 */
fun OdiSummaryMessage.nightDetailText(): String {
    if (startMs <= 0L || endMs <= 0L) return ""
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    val span = "${fmt.format(Date(startMs))}–${fmt.format(Date(endMs))}"
    if (!available) return Text.fmt("s_74d2f008", span)
    return Text.fmt("s_27769dd8", span, Odi.formatEvents(events)) +
        Odi.formatMonitored(monitoredMinutes) + " · T90 " + Odi.formatT90(t90Pct)
}

/** 今天（本地日期）的 epochDay，与手机端写库时用的口径一致。 */
fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
