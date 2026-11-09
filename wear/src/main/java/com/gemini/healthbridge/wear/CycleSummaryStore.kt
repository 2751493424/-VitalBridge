package com.gemini.healthbridge.wear

import android.content.Context
import com.gemini.healthbridge.core.Cycle
import com.gemini.healthbridge.core.CycleSummaryMessage
import com.gemini.healthbridge.core.Text
import kotlinx.serialization.json.Json

/**
 * 手表本地缓存的经期一行摘要（模块 #13）。
 *
 * 经期数据只在手机端录入/导入，手机算好摘要后经蓝牙推一个 `CYCLE_SUMMARY` 帧过来，
 * 手表只显示一行（第 N 天 / 距下次 / 是否易孕），**不做录入交互**。
 *
 * ## 为什么存 SharedPreferences 而不是数据库
 * 与 `BiaProfileStore` 同理：它是**单条状态**（"最近一次经期 + 下一次预测"），
 * 不是时序记录，写入即覆盖，没有历史可查，也不需要备份。
 *
 * ## 过期处理
 * 与睡眠摘要/小时统计的 dateEpochDay 校验**不同**：经期摘要里没有 dateEpochDay，
 * 因为经期预测是"向前看"的（下一次经期在几周后），不存在"第二天就过期"的问题。
 * 它过期的方式是"用户又记了一次新经期"——那时手机会推一个新的摘要覆盖旧的。
 */
object CycleSummaryStore {

    private const val PREF = "vitaltrace_cycle"
    private const val KEY_JSON = "summary_json"

    private val json = Json { ignoreUnknownKeys = true }

    /** 写入摘要（available=false 也照写，表示"未开启/无数据"，与 `profile=null` 同纪律）。 */
    fun save(context: Context, msg: CycleSummaryMessage) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_JSON, json.encodeToString(CycleSummaryMessage.serializer(), msg))
            .apply()
    }

    /** 读回摘要；从未收到过时返回 null（界面据此显示"待同步"）。 */
    fun load(context: Context): CycleSummaryMessage? {
        val s = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY_JSON, null)
            ?: return null
        return runCatching { json.decodeFromString(CycleSummaryMessage.serializer(), s) }
            .getOrNull()
    }

    /** 清空（手机推来 available=false 时会先 save 再覆盖，这里只用于测试/重置）。 */
    fun clear(context: Context) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().clear().apply()
    }
}

/**
 * 手表端把摘要渲染成一行文字。
 *
 * 用手表自己的"今天"重算第几天（`Cycle.glanceText` 需要 cycleDay/daysToNext/overdue），
 * 而不是让手机推一个算好的字符串 —— 手表和手机可能不在同一天，且这样摘要过期后
 * 手表能自己算出"已超预计 N 天"。
 */
fun CycleSummaryMessage.glanceText(todayEpochDay: Long): String {
    if (!available) return Text.of("s_1b348fa9")
    val last = lastStartEpochDay ?: return Text.of("s_1ae1809e")
    val cycleDay = (todayEpochDay - last + 1).toInt()
    val next = nextStartEpochDay
    return if (next != null) {
        val daysToNext = (next - todayEpochDay).toInt()
        val overdue = (todayEpochDay - next).toInt().coerceAtLeast(0)
        val base = Cycle.glanceText(cycleDay, daysToNext, overdue)
        if (inFertileWindow == true) Text.fmt("s_16d12029", base) else base
    } else {
        // 数据不足：只有"第几天"，没有"距下次"
        Text.fmt("s_92933427", cycleDay)
    }
}
