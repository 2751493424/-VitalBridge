package com.gemini.healthbridge.phone

import android.content.Context
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 「这份内容已经导出过」的台账 —— 同一份波形**不重复落盘**（2026-10-05 用户要求）。
 *
 * ## 用户的需求（原话）
 *
 * > 另外，现在如果一个文件多次导出，则会显示此文件已经重复导出的提示，不再导出一份多余的文件。
 * > 例如手表上已经导出过一次了，手机上就不会再导出了，又或者是手机上多次点击了导出。
 *
 * ## ⚗⚗ 判据是「**内容**」而不是「点了哪个按钮」
 *
 * 必须按内容判，才同时满足他说的两件事，又不误伤正常用途：
 *
 * | 场景 | 指纹 | 结果 |
 * |---|---|---|
 * | 手表同一会话点两次「上传波形」 | 同一份波形 ⇒ 同指纹 | **挡住**，提示已导出过 ✓ |
 * | 手机端同一个心电记录点两次「导出 CSV」 | 同一份 CSV ⇒ 同指纹 | **挡住** ✓ |
 * | **新测一次心电**再导出 | 内容变了 ⇒ 新指纹 | **正常导出** ✓（这条必须保住） |
 *
 * ⚠ 若改成按"会话/记录 id"去重，用户想再导一次就要先删记录 —— 而按内容既挡住重复，
 *   又不会把新数据误判成重复。所以不要图省事换成 id。
 *
 * ## 为什么存 SharedPreferences 而不是 Room
 *
 * 同 `AnnouncementStore`：这是**界面/运维状态**，不是健康数据 ——
 * 与任何业务表都没有事务关系、不参与导出/备份，换机后最坏的结果只是"又能重复导一次"。
 * 为它升一次 DB 版本、再维护一段迁移 SQL 不划算。
 */
object EcgExportStore {

    private const val PREF = "ecg_export"

    /** 值 = `指纹` 映射到 `"<导出时间>\\t<路径>"`。⚠ 时间戳要存：裁剪时要按它保最近的。 */
    private const val KEY_INDEX = "index"

    /**
     * 上限（条）。⚠ **必须有**：不裁剪的话每导出一次就塞一条，几年后是个无界集合。
     * 指纹约 40 字节、路径约 80 ⇒ 300 条约 36KB。
     */
    private const val MAX_ENTRIES = 300

    private const val FIELD_SEP = "\t"

    private val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), String.serializer())

    /**
     * 内容指纹 = `MD5(文本) + "-" + 文本长度`。
     *
     * ⚠ 为什么要带长度：单用 MD5 在这里其实够（我们不需要抗碰撞，只要"同一份内容"的判据），
     *   但加上长度能让"两份长度差很远却哈希碰巧相同"直接被排除，零成本。
     * ⚠ 摘不到 MD5 时退回 `hashCode + 长度`：**宁可漏挡一次重复，
     *   也不能因为算不出指纹就干脆不导出** —— 导出本身比去重重要。
     * ⚠ 指纹是**内容**的函数，所以它对"文件类型"无感：手机端那份 CSV 与手表传来的
     *   列名/取值都不同 ⇒ 指纹天然不会互撞，两类导出共用一个台账是安全的。
     */
    fun fingerprint(csv: String): String = runCatching {
        val md = java.security.MessageDigest.getInstance("MD5")
        val hex = md.digest(csv.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        "$hex-${csv.length}"
    }.getOrElse { "${csv.hashCode()}-${csv.length}" }

    private fun read(context: Context): Map<String, String> {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY_INDEX, null) ?: return emptyMap()
        return runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyMap())
    }

    /** 这份内容之前导出到哪了；没导过返回 `null`。 */
    fun find(context: Context, fingerprint: String): String? =
        read(context)[fingerprint]
            ?.substringAfter(FIELD_SEP, "")
            ?.takeIf { it.isNotEmpty() }

    /**
     * 记一笔（同指纹覆盖）。
     *
     * ⚠ 写盘**一次 `apply()` 写完**：与项目里「时间戳与内容必须一起写」是同一条纪律 ——
     *   分两次写的话，进程在中间被杀就会留下"有指纹没时间戳"的条目（裁剪时排序全乱）。
     */
    fun remember(context: Context, fingerprint: String, path: String, atMs: Long) {
        val next = read(context).toMutableMap()
        next[fingerprint] = "$atMs$FIELD_SEP$path"
        val trimmed = if (next.size <= MAX_ENTRIES) {
            next
        } else {
            // 裁剪：按导出时间保**最近**的 MAX_ENTRIES 条
            next.entries
                .sortedByDescending { it.value.substringBefore(FIELD_SEP).toLongOrNull() ?: 0L }
                .take(MAX_ENTRIES)
                .associate { it.key to it.value }
        }
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putString(KEY_INDEX, json.encodeToString(serializer, trimmed))
            .apply()
    }
}
