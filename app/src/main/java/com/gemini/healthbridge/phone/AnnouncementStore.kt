package com.gemini.healthbridge.phone

import android.content.Context
import com.gemini.healthbridge.core.Announcement
import com.gemini.healthbridge.core.AnnouncementItem
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * 公告的本地状态：上次拉到的原文、已读 id、每条公告「上次自动弹出的时间」。
 *
 * ## 为什么存 SharedPreferences 而不是 Room
 * 这些都是**界面状态**，不是健康数据：
 * - 与任何一张业务表都没有事务关系，不需要迁移；
 * - 不参与健康数据的导出/备份 —— 换机后最坏的结果只是"公告又弹一次"，这远好于
 *   为了它升一次数据库版本、再维护一段迁移 SQL。
 *
 * 参考 `CycleSettings` / `PpgSettings` 的同类选择。
 *
 * ## 为什么缓存原文而不缓存解析结果
 * 解析规则会随版本演进（加字段、改筛选），缓存解析后的对象就等于把**旧版本的判断**
 * 冻结下来。缓存原文，则每次都用当前版本重新解析 —— 逻辑改了立刻生效，
 * 不需要"清缓存"。
 */
object AnnouncementStore {

    private const val PREF = "announcement"

    private const val KEY_RAW = "raw"
    private const val KEY_RAW_AT = "raw_at"
    private const val KEY_RAW_FROM = "raw_from"
    private const val KEY_READ_IDS = "read_ids"
    private const val KEY_POPUP_AT = "popup_at"
    private const val KEY_POPUP_SIG = "popup_sig"
    private const val KEY_LAST_ATTEMPT = "last_attempt_at"

    /**
     * ⚗⚗ **累积的历史公告**（2026-10-04 用户要求「添加历史公告」）。
     *
     * ## 为什么必须由客户端累积
     *
     * 此前只存 `KEY_RAW` = **最新一份** JSON 原文 ⇒ 服务端把某条公告删掉，
     * App 里就**永远看不到**了。
     * 实测确认：服务端的 `announcement.json` **当前只有 1 条** ⇒
     * 用户看到的列表就只有一条，于是他会问"历史公告呢"。
     *
     * ⇒ 现在每次拉到的公告都**并进本地历史**（按 id 去重），
     *   服务端删了也仍在本地可见（由 [History.retired] 标为已下线）。
     *
     * ⚠⚠ **代价要说清**：若某条公告内容有误、作者想撤回，
     *   `show:false` 或直接删都**不会**让它从本地历史消失。
     *   若将来真需要"可撤回"，得加一条服务端下发的删除名单 ——
     *   但那是新需求，不在本次范围内。
     */
    private const val KEY_HISTORY = "history"

    /** 公告 id -> **本地首次见到**它的毫秒时间戳（用于在没有 `date` 时兜底显示）。 */
    private const val KEY_FIRST_SEEN = "first_seen"

    /**
     * 本地历史的上限。
     *
     * ⚠ 超限时丢**最旧的**（历史的意义是"最近发过什么"，不是"史上全部"）。
     * 与 core 的 `Announcement.MAX_ITEMS`（单次拉取上限 50）取同一个数量级 ——
     * 两个数不一致会让"列表能显示 50 条但历史只留 20 条"这种怪现象。
     */
    private const val MAX_HISTORY = 50

    /**
     * 「上次弹出时间」与「已读 id」的上限。
     *
     * 公告是会被删掉、也会不断新增的，这两个集合如果不封顶就会一直长下去；
     * 超过上限时丢**最旧的**记录，代价只是那条早已下线的公告某天又被加回来时会多弹一次。
     */
    private const val MAX_POPUP_RECORDS = 50
    private const val MAX_READ_IDS = 200

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ── 缓存的原文 ──────────────────────────────────────────────

    fun cachedRaw(context: Context): String? =
        prefs(context).getString(KEY_RAW, null)?.takeIf { it.isNotBlank() }

    fun cachedAt(context: Context): Long = prefs(context).getLong(KEY_RAW_AT, 0L)

    fun cachedFrom(context: Context): String? = prefs(context).getString(KEY_RAW_FROM, null)

    /**
     * 存一份拉取成功的原文。
     *
     * [from] 记的是命中的地址（`AnnouncementHttp.FetchResult.Ok.url`），
     * 只用于排查"现在到底在用哪一路"，不显示在界面上。
     */
    fun saveRaw(context: Context, raw: String, atMs: Long, from: String) {
        prefs(context).edit()
            .putString(KEY_RAW, raw)
            .putLong(KEY_RAW_AT, atMs)
            .putString(KEY_RAW_FROM, from)
            .apply()
    }

    /** 上次**尝试**拉取的时间（无论成败）。用于 [AnnouncementHttp] 那个最小间隔。 */
    fun lastAttemptAt(context: Context): Long = prefs(context).getLong(KEY_LAST_ATTEMPT, 0L)

    fun setLastAttemptAt(context: Context, atMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_ATTEMPT, atMs).apply()
    }

    // ── 累积的历史公告 ──────────────────────────────────────────

    private val historySerializer = ListSerializer(AnnouncementItem.serializer())
    private val seenSerializer = MapSerializer(String.serializer(), Long.serializer())

    /** 本地累积的公告（**最新在前**）。含服务端已删掉的那些。 */
    fun history(context: Context): List<AnnouncementItem> {
        val raw = prefs(context).getString(KEY_HISTORY, null) ?: return emptyList()
        return runCatching { Announcement.json.decodeFromString(historySerializer, raw) }
            .getOrDefault(emptyList())
    }

    /** 公告 id -> **本地首次见到**的时间戳。没有 `date` 字段时用它兜底显示时间。 */
    fun firstSeen(context: Context): Map<String, Long> {
        val raw = prefs(context).getString(KEY_FIRST_SEEN, null) ?: return emptyMap()
        return runCatching { Announcement.json.decodeFromString(seenSerializer, raw) }
            .getOrDefault(emptyMap())
            .filterValues { it > 0 }
    }

    /**
     * 把这次拉到的公告**并进**历史。
     *
     * ## 合并规则（顺序很重要）
     *
     * `新的一批（保持 JSON 顺序，最新在前）` + `历史里不在新批次中的（已下线的，保持原顺序）`。
     * ⇒ 效果是"最新的永远在最前，下线的沉到后面"，与用户对一条时间线的直觉一致。
     *
     * ⚠ **幂等**：同一条公告反复合并不会产生重复（按 id 去重），
     *   所以"拉取失败时用缓存重新解析"这条路径也安全。
     * ⚠ `firstSeen` **只在第一次见到时写入**（不覆盖）——
     *   否则每次刷新都会把"首次见到的时间"推后，那个时间就失去意义了。
     * ⚠ 两个键**一次 `apply()` 一起写**：分开写若中途被打断，会留下
     *   "有历史条目、没有首次时间"的状态，而时间显示就永久性地少一批。
     */
    fun mergeHistory(context: Context, items: List<AnnouncementItem>, atMs: Long) {
        if (items.isEmpty()) return
        val old = history(context)
        val seen = LinkedHashMap(firstSeen(context))
        val oldIds = old.mapTo(HashSet()) { it.id }

        // ⚗ 只为**新见到的**记时间戳（已见过的保留原值）
        for (it in items) if (it.id !in seen) seen[it.id] = atMs

        // ⚗ 合并规则走 core 的**纯函数** [Announcement.mergeHistory] ——
        //   那样它才在离线校验里能直接断言（本文件依赖 SharedPreferences，跑不了离线用例）。
        val merged = Announcement.mergeHistory(old, items, MAX_HISTORY)

        // ⚠ 时间戳表也跟着裁剪，且**以合并后的结果为准** —— 否则历史裁掉了、
        //   时间戳表却越来越大（两表不同步，将来谁读谁困惑）。
        val keepIds = merged.mapTo(HashSet()) { it.id }
        val trimmedSeen = LinkedHashMap<String, Long>().apply {
            for ((k, v) in seen) if (k in keepIds) put(k, v)
        }

        val hText = runCatching {
            Announcement.json.encodeToString(historySerializer, merged)
        }.getOrNull() ?: return
        val sText = runCatching {
            Announcement.json.encodeToString(seenSerializer, trimmedSeen)
        }.getOrNull() ?: return
        prefs(context).edit()
            .putString(KEY_HISTORY, hText)
            .putString(KEY_FIRST_SEEN, sText)
            .apply()
    }

    // ── 已读 ────────────────────────────────────────────────────

    fun readIds(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_READ_IDS, emptySet())?.toSet() ?: emptySet()

    fun markRead(context: Context, id: String) {
        val cur = readIds(context)
        if (id in cur) return
        val next = LinkedHashSet(cur).apply { add(id) }
        prefs(context).edit().putStringSet(KEY_READ_IDS, trim(next, MAX_READ_IDS)).apply()
    }

    fun markAllRead(context: Context, ids: Collection<String>) {
        if (ids.isEmpty()) return
        val next = LinkedHashSet(readIds(context)).apply { addAll(ids) }
        prefs(context).edit().putStringSet(KEY_READ_IDS, trim(next, MAX_READ_IDS)).apply()
    }

    /** 保留最后 [max] 个（StringSet 本身无序，这里只保证总数可控）。 */
    private fun trim(set: Set<String>, max: Int): Set<String> =
        if (set.size <= max) set else set.toList().takeLast(max).toSet()

    // ── 「上次自动弹出」时间 ────────────────────────────────────

    /** id -> 时间戳 的映射序列化器（只在这个文件里用）。 */
    private val popupMapSerializer =
        MapSerializer(String.serializer(), Long.serializer())

    /**
     * 公告 id -> 上次自动弹出的毫秒时间戳。
     *
     * 存成一小段 JSON（复用 [Announcement.json]）而不是给每条公告开一个 prefs 键：
     * 公告 id 是外部内容，键名会随公告增删无限增长，清理起来很麻烦。
     */
    fun popupAt(context: Context): Map<String, Long> {
        val raw = prefs(context).getString(KEY_POPUP_AT, null) ?: return emptyMap()
        return runCatching { Announcement.json.decodeFromString(popupMapSerializer, raw) }
            .getOrDefault(emptyMap())
            .filterValues { it > 0 }
    }

    /** 记下"这条刚刚自动弹过"。时间戳取**弹出时刻**而不是关闭时刻 —— 用户在弹窗上停留多久与冷却无关。 */
    fun markPopped(context: Context, id: String, atMs: Long) {
        val next = LinkedHashMap(popupAt(context))
        next[id] = atMs
        val trimmed = if (next.size <= MAX_POPUP_RECORDS) next
        else next.entries.sortedBy { it.value }.takeLast(MAX_POPUP_RECORDS)
            .associateTo(LinkedHashMap()) { it.key to it.value }
        val text = runCatching {
            Announcement.json.encodeToString(popupMapSerializer, trimmed)
        }.getOrNull() ?: return
        prefs(context).edit().putString(KEY_POPUP_AT, text).apply()
    }

    // ── 「上次弹出时这条公告长什么样」────────────────────────────
    //
    // 与 KEY_POPUP_AT 分两个键、各自序列化：它们是两张独立的表（时间戳 / 字符串），
    // 塞进同一个 JSON 就要定义嵌套结构，而两边都由 `trim` 决定淘汰谁 —— 分开写更简单，
    // 且**淘汰口径可以各自独立**（下面 markPoppedWithSig 让它们保持同一批 key）。

    /** id -> 内容指纹 的映射序列化器。见 [Announcement.contentSignature]。 */
    private val sigMapSerializer =
        MapSerializer(String.serializer(), String.serializer())

    /**
     * 公告 id -> **上次自动弹出那一刻**的内容指纹。
     *
     * 拿它跟"现在这条的内容指纹"比：不同 = 作者改过公告 = 冷却不算数，直接重新弹。
     * 没有这条记录（首次弹 / 从旧版本升级上来 / 存储被清）时按"没变"处理 ——
     * 宁可漏弹一次，也不能让升级当天把历史上所有公告全弹一遍。
     */
    fun popupSig(context: Context): Map<String, String> {
        val raw = prefs(context).getString(KEY_POPUP_SIG, null) ?: return emptyMap()
        return runCatching { Announcement.json.decodeFromString(sigMapSerializer, raw) }
            .getOrDefault(emptyMap())
            .filterValues { it.isNotEmpty() }
    }

    /**
     * 一次写入「弹出时间」与「弹出时的内容指纹」。
     *
     * **必须一起写**：分开两个方法的话，中间被打断（进程被杀、并发调用）会留下
     * "有时间戳、没指纹"的状态 —— 那条公告从此**永远**判定"内容没变"，改公告再也弹不出来，
     * 而且不报任何错。合成一次 `edit().apply()` 是原子的。
     *
     * 淘汰规则与 [markPopped] 一致（按时间戳留最新的 N 条），且**两张表用同一个 cut 结果**，
     * 不会出现"时间戳留了这条、指纹却把它丢了"的错位。
     */
    fun markPoppedWithSig(context: Context, id: String, atMs: Long, sig: String) {
        val at = LinkedHashMap(popupAt(context)).apply { this[id] = atMs }
        val sigs = LinkedHashMap(popupSig(context)).apply { this[id] = sig }
        val keep: Map<String, Long> = if (at.size <= MAX_POPUP_RECORDS) at
        else at.entries.sortedBy { it.value }.takeLast(MAX_POPUP_RECORDS)
            .associateTo(LinkedHashMap()) { it.key to it.value }
        val atText = runCatching {
            Announcement.json.encodeToString(popupMapSerializer, keep)
        }.getOrNull() ?: return
        val sigText = runCatching {
            Announcement.json.encodeToString(sigMapSerializer, LinkedHashMap(
                sigs.filterKeys { it in keep }
            ))
        }.getOrNull() ?: return
        prefs(context).edit()
            .putString(KEY_POPUP_AT, atText)
            .putString(KEY_POPUP_SIG, sigText)
            .apply()
    }
}
