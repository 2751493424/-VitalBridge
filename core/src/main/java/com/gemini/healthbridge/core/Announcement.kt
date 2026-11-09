package com.gemini.healthbridge.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 远程公告（走静态 JSON，不依赖后端服务）。
 *
 * ## 为什么用静态 JSON 而不是接口
 * 首发免费版定位是「无云」：本地采集 + 本地存储 + 基础算法。联网只有公告与更新检查两处，
 * 为它部署一套后端不划算 —— 一张托管在静态站点上的 JSON 就够了，能发通知、能带下载链接、
 * 改一次立刻生效，且**没有任何服务端要维护**（也就没有可被攻破、会宕机的服务端）。
 *
 * ## 本文件只做纯逻辑
 * - **不做网络**（拉取在 app 侧 `AnnouncementFetcher`）：这样它能在 JVM 上离线跑断言。
 * - **不做存储**（已读与冷却时间在 app 侧 `AnnouncementStore`）。
 * 换来的好处是：所有容易出错的判断（字段缺失、版本约束、冷却到没到、哪条该弹）
 * 都能用合成数据穷举验证，不必装到真机上试。
 *
 * ## 时间为什么全用「字符串」
 * `date` / `hideAfterDate` 都是 `"2026-09-19"` 这种 ISO 日期串，**原样显示、原样比较**：
 * - 显示端不需要解析，也就不会出现「JSON 里写的时间被时区换算成前一天」这种脏问题；
 * - ISO 日期的**字典序与时间序一致**，所以判断过期直接 `today > hideAfterDate` 即可，
 *   一行字符串比较替代一整套日期解析。
 * 排序也不用时间字段：**JSON 数组的顺序就是展示顺序**，作者把最新的写在最前面。
 */

/**
 * 公告 JSON 的顶层只有两个键：
 *
 * ```json
 * { "schemaVersion": 1, "announcements": [ { …一条公告… } ] }
 * ```
 *
 * 除 `announcements` 必须是数组外都很宽松：多写的键会被忽略（旧版 App 不会因为
 * 托管方加了字段就崩），`schemaVersion` 缺失按 1 处理。
 * 完整可托管的样例见仓库根目录的 `announcement.json`。
 */

/** JSON 里的单条公告。字段名与 JSON 一一对应，缺字段走默认值（向后兼容）。 */
@Serializable
data class AnnouncementItem(
    /** 稳定唯一标识。**发布后不能再改**：已读与「上次弹出时间」都按它记录，改了会重复弹。 */
    val id: String,
    val title: String,
    /** 正文，可含换行。界面按原样换行显示。 */
    val body: String = "",
    /** 显示用日期，原样展示（约定 `YYYY-MM-DD`）。 */
    val date: String = "",
    /** [Announcement.LEVEL_NORMAL] 或 [Announcement.LEVEL_IMPORTANT]。 */
    val level: String = Announcement.LEVEL_NORMAL,
    /** 是否出现在公告列表里。false = 整条不显示（相当于下线，但保留 id 记录）。 */
    val show: Boolean = true,
    /** 是否允许开屏自动弹。false = 只在公告列表里能看（用于「知道了就行」的普通公告）。 */
    val autoPopup: Boolean = true,
    /** 下载/详情链接。有它时弹窗上会出现一个跳转按钮。 */
    val url: String? = null,
    /** 跳转按钮的文案，缺省用 [Announcement.DEFAULT_URL_LABEL]。 */
    val urlLabel: String? = null,
    /** 限定平台，如 `["phone"]`。**空列表 = 所有端**（默认，省得每条都写）。 */
    val platforms: List<String> = emptyList(),
    /**
     * 只在 App 版本号**低于**它时显示。
     *
     * 典型用法：「请升级到 v1.1」这类公告，填 v1.1 的 versionCode ——
     * 已经升上去的用户自然不再看到它，不需要你再手动来删。
     */
    val showBelowVersionCode: Int? = null,
    /** 过了这一天就不再显示（约定 `YYYY-MM-DD`，**当天仍显示**，次日起隐藏）。 */
    val hideAfterDate: String? = null,
    /**
     * 配图的**仓库内相对路径**（如 `images/promo.png` 或 `promo.png`），可空（2026-10-05 加）。
     *
     * ## ⚠⚠ 为什么是"相对路径"而不是完整 URL
     *
     * 完整 URL 意味着**公告 JSON 能指挥 App 去访问任意服务器** —— 那不只是"多一处联网"，
     * 而是把「访问哪些域名」的决定权交给远端内容，而远端内容是可被改的
     * （仓库协作者、CDN 缓存、中间人）。
     * ⇒ 现在只接受相对路径，**基址由代码里的地址表给出**（见 [imageCandidates]）：
     *   一张图会自动在 Pages / jsDelivr / raw 三个源上都可用，而 App 仍然只访问那几个域名。
     *
     * ## 文件名：中文可以，但别用 `?` `#` `%` `\` 这类符号
     * 校验（[isValidImagePath]）放行 `A-Za-z0-9`、空格、`_ - . /` 与**非 ASCII**（中文等）。
     * ⚠ 中文名会由 [encodePathForUrl] 做百分号编码 —— 这步**不能省**：
     *   不编码的话 `URI.create` 会直接抛异常（服务器侧其实是好的，挡住它的是我们自己）。
     */
    val image: String? = null,
)

/**
 * 公告 JSON 顶层的 `update` 块（2026-10-16）：告诉客户端"有没有新版本、去哪下"。
 *
 * ## 为什么不单独做一个版本接口
 * App 启动**本来就会拉这份公告 JSON**（全应用仅两处联网之一）。把版本信息挂在这里，
 * 就**不需要第二条请求、也不需要新端点**；而且换下载地址、发新版本**只改 JSON、不用出包**。
 *
 * ## 为什么每个字段都要校验
 * 这个 JSON 是**公开可改**的托管内容（谁拿到仓库权限都能改）⇒ 一律当不可信输入：
 * 版本号不是数字、URL 不是 https、sha256 位数不对 —— 任何一条不满足就**整块作废**
 * （当作"没有更新信息"），绝不能让一个坏字段把用户引到一个可疑的下载地址上。
 *
 * @param url **必须 https**（明文 http 会被系统安装器拒绝，而且中间人可以换包）
 * @param sizeBytes 下载后校验字节数（截断的下载是最常见的"装不上"）
 * @param sha256 小写十六进制、64 字符；下载后必须逐字节对得上
 * @param notes 更新说明（可空）；⚠ 纯文本，不解析 Markdown
 */
data class AnnouncementUpdate(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
    val notes: String? = null,
)

object Announcement {

    /** 本端认识的 JSON 格式版本。加字段不用动它；改语义才 +1。 */
    const val SCHEMA_VERSION = 1

    const val LEVEL_NORMAL = "normal"
    const val LEVEL_IMPORTANT = "important"

    const val PLATFORM_PHONE = "phone"
    const val PLATFORM_WEAR = "wear"

    /**
     * 同一条公告自动弹出的最小间隔：**2 个自然日**（也就是"隔一天"）。
     *
     * 为什么要有间隔：公告 JSON 是一份长期挂在网上的文件，同一条会一直被拉下来。
     * 没有冷却的话，用户每次开 App 都会被同一条公告拦一次 —— 这正是要避免的。
     *
     * ## 为什么是"自然日"而不是"48 小时"（2026-09-21 改）
     * 原实现是 `nowMs - last >= 2 天`，按**经过的毫秒**算。这会让节奏随"上次弹的时刻"漂移：
     * 19 号 23:00 弹过之后，要等到 21 号 23:00 才够 48 小时 —— 于是 21 号早上打开 App
     * **不会弹**，要到 22 号才弹。用户的感受就是"有时候隔一天、有时候隔两天"。
     *
     * 改成**按自然日**：弹过的那天记作第 0 天，次日（差 1）不弹，第三天（差 2）弹。
     * 19 号弹过 → 20 号不弹 → 21 号弹，**与几点弹的无关**。这正是产品要的"隔一天就弹"。
     *
     * 日序用 `floorDiv(毫秒 + 时区偏移, 一天)` 算，所以调用方必须把**设备时区偏移**传进来，
     * 否则日界会按 UTC 划 —— 在东八区就成了"每天早上 8 点换日"。
     *
     * ⚠ **冷却只对"内容没变"的那条生效**：作者改过公告正文后节奏立刻重置，
     * 见 [contentSignature] 与 [popupCandidate]。
     */
    const val POPUP_COOLDOWN_DAYS = 2

    /** 一天的毫秒数。 */
    const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * 某个时刻落在第几个自然日（以 [zoneOffsetSeconds] 为准）。
     *
     * 只保证"两个 dayIndex 相减等于相差的自然日数"，绝对值本身没有意义。
     * 用 [Math.floorDiv] 而不是 `/`：负数时间戳下 `/` 向零取整会算错。
     */
    fun dayIndex(ms: Long, zoneOffsetSeconds: Int = 0): Long =
        Math.floorDiv(ms + zoneOffsetSeconds * 1000L, DAY_MS)

    /**
     * 已读之后还要不要继续按 2 天节奏弹？
     *
     * 当前 **false**（按产品要求：严格 2 天节奏，重要通知即使点过也要再提醒）。
     * 「已读」目前只影响公告列表的未读红点。
     *
     * 若哪天觉得已读后仍被弹太烦，把这里改成 true 即可 —— 判断逻辑已经写好了，
     * 不需要动界面。
     */
    const val READ_SUPPRESSES_POPUP = false

    /** 单份 JSON 最多处理多少条，防止托管方误发一份巨大的文件把解析拖死。 */
    const val MAX_ITEMS = 50

    /** 标题/正文的显示上限：超长会把弹窗撑到屏幕外，截断并加省略号。 */
    const val MAX_TITLE = 60
    const val MAX_BODY = 2000

    val DEFAULT_URL_LABEL: String get() = Text.of("s_0b5cd081")

    /**
     * 解析顶层的 `update` 块。**不合法一律返回 null**（= 当没有更新信息）。
     *
     * ⚠ 校验清单（每一条都对应一种"能害到用户"的情况）：
     *   · `versionCode` 不是正整数 ⇒ 没法比较版本；
     *   · `url` 不是 **https** ⇒ 系统会拒装，且中间人能换包；
     *   · `sizeBytes` ≤ 0 ⇒ 没法校验下载是否完整；
     *   · `sha256` 不是 64 位十六进制 ⇒ 没法校验包的完整性（宁可不提示更新，也不用没校验的包）；
     *   · `versionName` 为空 ⇒ 弹窗里没得显示。
     */
    private fun parseUpdate(el: JsonElement?): AnnouncementUpdate? {
        val o = el as? JsonObject ?: return null
        val vc = (o["versionCode"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return null
        if (vc <= 0) return null
        val name = (o["versionName"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (name.isEmpty()) return null
        val url = (o["url"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (!url.startsWith("https://", ignoreCase = true)) return null
        val size = (o["sizeBytes"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
        if (size <= 0L) return null
        val sha = (o["sha256"] as? JsonPrimitive)?.content?.trim()?.lowercase().orEmpty()
        if (sha.length != 64 || sha.any { it !in "0123456789abcdef" }) return null
        val notes = (o["notes"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        return AnnouncementUpdate(vc, name, url, size, sha, notes)
    }

    val json: Json = Json {
        ignoreUnknownKeys = true   // 托管方加字段时，旧版 App 不能崩
        isLenient = true
        coerceInputValues = true   // null 落到默认值，而不是抛异常
    }

    // ═══════════════════════ 解析 ═══════════════════════

    sealed interface Parse {
        /**
         * 解析成功。[items] 已剔除无效条目、去重、截断并清洗过。可能为空（= 没有公告）。
         * [dropped] 是被丢掉的条目数，只用于诊断（例如"作者有 1 条写错了"）。
         */
        data class Ok(
            val items: List<AnnouncementItem>,
            val dropped: Int = 0,
            /** 顶层的 `update` 块；没有/不合法时为 null（= 不做更新提示）。 */
            val update: AnnouncementUpdate? = null,
        ) : Parse

        /** 解析失败。[reason] 是给人看的短句，直接可显示。 */
        data class Bad(val reason: String) : Parse
    }

    /**
     * 解析一份公告 JSON。**任何输入都不抛异常**（网络内容一律当作不可信输入）。
     *
     * ## 为什么是「逐条解码」而不是整个 Feed 一次解
     * 一次解整份时，**任何一条**缺字段或类型写错都会让整份作废 —— 作者手滑漏写一个 title，
     * 全量公告就都发不出去了，而且线上表现是"公告页空白"，极难看出原因。
     * 所以这里先按 JsonObject/JsonArray 走一遍结构，再对**每个元素单独** try/catch 解码：
     * 坏的丢掉、好的留下。
     *
     * 只有三种情况整份作废：不是 JSON、顶层不是对象、没有 `announcements` 数组 ——
     * 这三种都不是"作者手滑"，而是托管内容整个错了（比如 CDN 返回了 HTML 错误页）。
     */
    fun parse(raw: String): Parse {
        if (raw.isBlank()) return Parse.Bad(Text.of("s_3659827b"))

        val root = runCatching { json.parseToJsonElement(raw) }.getOrElse { e ->
            return Parse.Bad(
                Text.fmt("s_2e1b6077", e.javaClass.simpleName) +
                    Text.of("s_058cce79")
            )
        }
        val obj = root as? JsonObject
            ?: return Parse.Bad(
                if (root is JsonArray) Text.of("s_c5ffb718")
                else Text.of("s_a5ce4fba")
            )

        val ver = (obj["schemaVersion"] as? JsonPrimitive)?.content?.toIntOrNull()
            ?: SCHEMA_VERSION
        if (ver > SCHEMA_VERSION) {
            return Parse.Bad(
                Text.fmt("s_367069d0", ver)
            )
        }

        // ⚠ 整块解析包在 runCatching 里：任何异常都退化成"没有更新信息"，
        //   绝不能让一个坏字段把公告本身也一起废掉（两者互不牵连）。
        val update = runCatching { parseUpdate(obj["update"]) }.getOrNull()

        val arr = obj["announcements"] as? JsonArray
            ?: return Parse.Bad(Text.of("s_98ad3db3"))

        val seen = HashSet<String>()
        val items = ArrayList<AnnouncementItem>(minOf(arr.size, MAX_ITEMS))
        var dropped = 0
        for (el in arr) {
            if (items.size >= MAX_ITEMS) break
            val a = runCatching {
                json.decodeFromJsonElement(AnnouncementItem.serializer(), el)
            }.getOrNull()
            if (a == null) { dropped++; continue }

            val id = a.id.trim()
            val title = a.title.trim()
            // id 与 title 是唯二必需的：没有 id 就没法记已读与冷却（会每次重弹），
            // 没有标题的弹窗是一块没有信息的空白
            if (id.isEmpty() || title.isEmpty()) { dropped++; continue }
            if (!seen.add(id)) { dropped++; continue }   // 重复 id 只保留第一条
            items.add(
                a.copy(
                    id = id,
                    title = clip(title, MAX_TITLE),
                    body = clip(a.body.replace("\r\n", "\n"), MAX_BODY),
                    platforms = a.platforms.map { it.trim().lowercase() }.filter { it.isNotEmpty() },
                    url = a.url?.trim()?.takeIf { it.isNotEmpty() },
                    urlLabel = a.urlLabel?.trim()?.takeIf { it.isNotEmpty() },
                    // ⚠ 非法路径**静默丢弃**（变成"这条公告没有配图"），不报错也不显示破图。
                    //   与"网络内容当不可信输入、解析绝不抛异常"是同一条纪律：
                    //   公告是附加信息，一条路径写错不该影响它能不能被读到。
                    image = a.image?.trim()?.takeIf { isValidImagePath(it) },
                )
            )
        }
        // ⚠⚠ 必须把 `update` 传进去（2026-10-17 修）：此前这里只传了 items/dropped，
        //   而 `update` 有默认值 null ⇒ **编译通过、运行不报错、永远没有更新提示**。
        //   现象就是"服务端明明写了新版本，App 永远说已是最新"（查了三轮才定位）。
        return Parse.Ok(items, dropped, update)
    }

    private fun clip(s: String, max: Int): String =
        if (s.length <= max) s else s.take(max - 1).trimEnd() + "…"

    // ═══════════════════════ 配图 ═══════════════════════

    /** 配图相对路径的长度上限。够长，又挡住"拿路径字段当内容载体灌数据"。 */
    const val MAX_IMAGE_PATH = 200

    /**
     * 配图相对路径是否合法。
     *
     * ## ⚠⚠ 这是**安全边界**，不是格式洁癖
     *
     * 图片地址 = 「**代码里固定的基址**」+「公告给的相对路径」。若允许路径里塞完整 URL，
     * 就等于**远端 JSON 能指挥 App 去访问任意服务器**（见 [AnnouncementItem.image] 的说明）。
     *
     * ⇒ **拒绝**：任何带 scheme 的（`http:` / `https:` / `//host`，它会覆盖掉基址）、
     *   以 `/` 开头的（绝对路径，跳出仓库）、含 `..` 的（目录穿越）、
     *   以及白名单之外的字符（`A-Za-z0-9`、空格、`_ - . /` 与**非 ASCII**）。
     *   ⚗ 空格也放行（"第一张 图.png" 是很自然的命名）：它在拼 URL 时会被编成 `%20`
     *   —— 见 [encodePathForUrl] 里为什么必须把 `+` 换回来。
     * ⚠ 同时拒绝**结尾是 `/`**（那是目录，不是文件）与**层级过深**（`/` 超过 4 个）。
     *
     * ## ⚗ 为什么放行非 ASCII（中文文件名）
     * 作者上传的第一张图就叫「豆包.png」。实测：**服务器那边完全没问题**
     * （curl 用未编码的中文 URL 也能取到 200 + `image/png`）。
     * ⇒ 真正会挡住它的不是网络，是**我们自己**：`AnnouncementHttp.fetchOne` 用
     *   `URI.create(url).toURL()`，而 `URI.create` 遇到非 ASCII 会直接抛
     *   `IllegalArgumentException` ⇒ 图永远加载不出来，且错误会被当成"网络失败"吞掉。
     * ⇒ 所以放行的**同时必须**配上百分号编码（见 [encodePathForUrl]）—— 两者是一个整体，
     *   只做一半（放行不编码）等于让中文名的图静默失效。
     * ⚠ 危险字符仍被白名单挡在外面：`?` `#` `%` `\` `:` `"` 等一个都不放行。
     */
    fun isValidImagePath(p: String): Boolean {
        if (p.isEmpty() || p.length > MAX_IMAGE_PATH) return false
        if (p.startsWith("/") || p.endsWith("/")) return false
        if (p.contains("..")) return false
        if (p.contains("://") || p.startsWith("//")) return false
        if (p.count { it == '/' } > 4) return false
        return p.all { c ->
            c.code >= 0x80 || c == ' ' ||
                (c in 'a'..'z') || (c in 'A'..'Z') || (c in '0'..'9') ||
                c == '_' || c == '-' || c == '.' || c == '/'
        }
    }

    /**
     * 把相对路径编码成能放进 URL 的形式（**逐段**百分号编码）。
     *
     * ## ⚠ 为什么不能整体用 `URLEncoder.encode`
     * 它会把 `/` 一起编码成 `%2F` ⇒ 整条路径被当成**一个文件名** ⇒ 必然 404。
     * 它还会把空格编成 `+`（那是表单编码的规则，不是路径的规则）⇒ 要换回 `%20`。
     * ⇒ 所以按 `/` 切段、逐段编码，再拼回去。
     *
     * ⚠ 已经是纯 ASCII 可打印的段**原样保留** ⇒ **不会二次编码**
     *   （作者若手写 `%E8%B1%86.png`，它保持原样而不是变成 `%25E8...`）。
     * ⚠ 编码失败（理论上不会）就退回原串：宁可交给网络层去失败，
     *   也不要因为编码器出错就干脆不给候选地址。
     */
    private fun encodePathForUrl(path: String): String =
        path.split('/').joinToString("/") { seg ->
            if (seg.all { it.code in 0x21..0x7E }) seg
            else runCatching {
                java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
            }.getOrDefault(seg)
        }

    /**
     * 配图的**多源候选地址**：把公告 JSON 每个地址的**最后一段**换成配图相对路径。
     *
     * ## 为什么这样推就对
     * 三个源都满足「图片与 `announcement.json` 在同一个仓库里」：
     * - `https://<cdn-a>/<repo>/announcement.json` → `.../<repo>/<image>`
     * - `https://<cdn-b>/<owner>/<repo>@<ref>/announcement.json` → 同目录
     * - `https://<cdn-c>/<owner>/<repo>/<ref>/announcement.json` → 同目录
     * ⇒ 「换掉最后一段」对三者都成立，不必为每个源各写一条规则。
     *
     * ## 顺序**跟随公告源顺序**（2026-10-17 改）
     * 早先图片把 jsDelivr 提到最前，理由是"GitHub Pages 没开启，照搬顺序会每张图白等一次超时"。
     * 现在主源换成了 Cloudflare R2（见 `AnnouncementRepository.URLS`）：**公告 JSON 能从哪条地址读出来，
     * 就说明那台主机是通的**，图片跟着同序即可，既不多等超时，也保证"JSON 与图片同源"——
     * 否则会出现"公告读的是 R2 的新版、图片却先去 GitHub 取旧仓库"的错位。
     */
    fun imageCandidates(image: String, jsonUrls: List<String>): List<String> {
        if (!isValidImagePath(image)) return emptyList()
        val out = ArrayList<String>(jsonUrls.size)
        for (u in jsonUrls) {
            if (u.contains(AnnouncementHttp.PLACEHOLDER_MARK)) continue
            val base = u.substringBeforeLast('/', "")
            if (base.isEmpty() || !base.startsWith("http")) continue
            // ⚠ 用**编码后**的路径拼（中文名能被 `URI.create` 接受；纯 ASCII 的段不受影响）
            out.add("$base/${encodePathForUrl(image)}")
        }
        return out
    }

    // ═══════════════════════ 内容签名 ═══════════════════════

    /**
     * 一条公告的**内容指纹**：只要作者改动了用户能看到的东西，它就变。
     *
     * ## 它解决什么问题（2026-09-29 加）
     * 冷却原本只认 `id`。于是出现这个死角：**作者改了公告 `A` 的正文，但 `A` 正好落在
     * "今天不该弹"的那一天，改动就白改了** —— 要等到冷却走完用户才看得到新内容。
     * 作者越勤快（天天改）越容易撞上，且**完全不影响 id**，所以按 id 的冷却看不出任何异常。
     *
     * 有了签名后，「同一公告 2 天一次」的准确含义变成
     * **「同一份内容 2 天一次」**：改过 = 是"新公告" = 立刻值得再看一眼，
     * 这正是用户要的「**就算我天天修改公告，那就是天天都弹**」。
     *
     * ## 参与指纹的字段：只取用户能看见的正文
     * `title` + `body` + `url` + `urlLabel`。刻意**不含** `id`（改 id 是另一回事，反而会
     * 让已读与冷却失去连续性）、**不含** `date` / `level`（只影响列表页上的标签，
     * 不能因为作者顺手调了个日期就把用户再拦一次）、**不含** `hideAfterDate` /
     * `showBelowVersionCode` / `platforms`（这些改的是"给不给看"，真放出来了自然该弹）。
     *
     * ⚠ 传入的条目**应当已经过 [parse] 清洗**（去空白、截断）。直接用未经清洗的原始条目
     * 也算得出签名，但与下次解析后的结果可能不一致 —— 作者多打一个空格就会多弹一次。
     *
     * ## 实现：FNV-1a 64 位，不引哈希库
     * 这里只需要"变了能看出来"，不需要抗碰撞的密码学强度，所以不需要 `MessageDigest`
     * （它还要处理 `NoSuchAlgorithmException`）。**必须是稳定算法**：签名会被存进
     * SharedPreferences 跨版本读回，换成另一个算法等于把所有公告都当成"改过了"、全员重弹一次。
     * 所以自己写 FNV-1a，且用 `Long` 而不是 `Int` —— 32 位下几十条公告就有可见的碰撞概率。
     *
     * 用**十六进制字符串**返回而不是 `Long`：存储端是 JSON，`Long` 的负值/溢出语义
     * 在序列化两边容易出岔子，字符串最省心。
     */
    fun contentSignature(a: AnnouncementItem): String {
        val sb = StringBuilder(64)
        // 字段之间加分隔符：不加的话 ("ab","c") 与 ("a","bc") 会算出同一个指纹
        sb.append(a.title).append('\u0000')
            .append(a.body).append('\u0000')
            .append(a.url ?: "").append('\u0000')
            .append(a.urlLabel ?: "")
        // ⚗ 配图也参与签名（2026-10-05）：作者**换了一张图**和"改了正文"是同一件事 ——
        //   用户看到的东西变了，就该被当成新公告、该再弹一次。
        // ⚠⚠ **仅在非空时才追加**：FNV-1a 是逐字节累积的，无条件追一个分隔符会让
        //   **所有现存公告的签名一起变**，后果是升级后**全员重弹一次**（这条注释上面那句
        //   "换算法会全员重弹"的警告同样适用于"改输入"）。
        //   ⇒ 条件追加 ⇒ 没有配图的公告签名**逐字节不变**，只有真正带图的才参与。
        a.image?.takeIf { it.isNotEmpty() }?.let { sb.append('\u0000').append(it) }
        var h = -0x340d631b7bdddcdbL   // FNV-1a 64 位偏移基准
        for (b in sb.toString().toByteArray(Charsets.UTF_8)) {
            h = h xor (b.toLong() and 0xFF)
            h *= 0x100000001b3L        // FNV 质数
        }
        return java.lang.Long.toHexString(h)
    }

    // ═══════════════════════ 筛选 ═══════════════════════

    /**
     * 当前该显示哪些（按 JSON 原顺序）。四个条件全过才显示：
     * `show`、平台匹配、未过 [AnnouncementItem.hideAfterDate]、版本低于
     * [AnnouncementItem.showBelowVersionCode]。
     *
     * [today] 为本地日期串（`YYYY-MM-DD`），由调用方传入而不是在这里取当前时间 ——
     * 纯逻辑不碰时钟，才好在台架里把「过期那天」这种事直接构造出来。
     */
    fun visible(
        items: List<AnnouncementItem>,
        today: String,
        versionCode: Int,
        platform: String,
    ): List<AnnouncementItem> = items.filter { ok(it, today, versionCode, platform) }

    /**
     * **累积历史**：把这次拉到的公告并进已有的历史（2026-10-04 用户要求「添加历史公告」）。
     *
     * ## 为什么要有它
     *
     * 客户端此前只缓存**最新一份** JSON 原文 ⇒ 服务端删掉某条公告，App 里就**永远看不到**了。
     * 而实测服务端的 `announcement.json` **当前只有 1 条** ⇒ 用户自然会问"历史公告呢"。
     *
     * ## 合并规则（顺序是刻意的）
     *
     * `incoming（保持 JSON 顺序 = 最新在前）` + `old 里不在 incoming 中的（已下线的）`
     * ⇒ 最新永远在最前，已下线的沉到后面 —— 与用户对一条时间线的直觉一致。
     *
     * ⚠ **幂等**：同一条反复合并不会重复（按 id 去重）⇒
     *   "拉取失败时用缓存重新解析"那条路径也安全。
     * ⚠⚠ **它是纯函数**（不碰存储、不碰时钟）⇒ 能在 `anncheck` 里直接构造用例。
     *   与 [visible] 同一条纪律：**纯逻辑不碰时钟**，否则台架没法把
     *   "服务端删了这条"这种情形直接造出来。
     *
     * @param max 保留条数上限。超限丢**最旧的** —— 历史的意义是"最近发过什么"，
     *   不是"史上全部"。
     */
    fun mergeHistory(
        old: List<AnnouncementItem>,
        incoming: List<AnnouncementItem>,
        max: Int = MAX_ITEMS,
    ): List<AnnouncementItem> {
        // ⚠ 空 incoming **不能**把历史清掉：它表示"这次没拉到新的"，
        //   而历史是只增不减的（服务端删了下线的，本地仍要能看）。
        if (incoming.isEmpty()) return old.take(max)
        val incomingIds = incoming.mapTo(HashSet()) { it.id }
        return (incoming + old.filter { it.id !in incomingIds }).take(max)
    }

    private fun ok(a: AnnouncementItem, today: String, versionCode: Int, platform: String): Boolean {
        if (!a.show) return false
        // 平台比较**在这里**做大小写无关，而不是依赖 parse() 已经把 platforms 规整过 ——
        // 那些字段是公开的，用手工构造的条目调用本函数完全合法，不该因此判错。
        if (a.platforms.isNotEmpty() &&
            a.platforms.none { it.equals(platform, ignoreCase = true) }
        ) return false
        // ISO 日期串的字典序 = 时间序，所以不用解析日期
        val hide = a.hideAfterDate
        if (hide != null && hide.isNotBlank() && today > hide) return false
        val below = a.showBelowVersionCode
        if (below != null && versionCode >= below) return false
        return true
    }

    /**
     * 这次启动该自动弹哪一条？没得弹返回 null。
     *
     * 规则（按产品要求定的）：
     *  ① 只在**当前可见**且 `autoPopup` 的条目里挑；
     *  ② 挑**第一条**——JSON 数组顺序由作者控制，把最新的放最前就是"优先弹最新"，
     *     不需要 App 解析时间；这也避免了"老公告一直挂在文件里、于是每 2 天轮流骚扰用户"；
     *  ③ 距它**上次自动弹出**不足 [POPUP_COOLDOWN_DAYS] 个自然日就不弹
     *     （"隔一天"：19 号弹过 → 20 号不弹 → 21 号弹）；
     *  ④ **内容改过就无视冷却**（2026-09-29 加）：见 [contentSignature]。
     *     判据是「当前内容的指纹 ≠ 上次弹出时的指纹」，改过 = 新内容 = 立刻该看；
     *  ⑤ [READ_SUPPRESSES_POPUP] 为 true 时，已读的也不再弹。
     *
     * [lastPopupAt] 是「公告 id -> 上次自动弹出的毫秒时间戳」，来自本地存储。
     * [lastPopupSig] 是「公告 id -> 上次弹出**那一刻**的内容指纹」，同源存储。
     * 缺记录（旧版本升级上来、或存储被清）时**按"内容没变"处理** —— 宁可漏弹一次，
     * 也不能让升级当天把历史上所有公告全弹一遍。
     */
    fun popupCandidate(
        items: List<AnnouncementItem>,
        today: String,
        versionCode: Int,
        platform: String,
        nowMs: Long,
        lastPopupAt: Map<String, Long>,
        readIds: Set<String>,
        /** 设备时区偏移（秒），只用来把日界划在本地午夜。见 [POPUP_COOLDOWN_DAYS]。 */
        zoneOffsetSeconds: Int = 0,
        /** 公告 id -> 上次弹出时的内容指纹。见 [contentSignature]。 */
        lastPopupSig: Map<String, String> = emptyMap(),
    ): AnnouncementItem? {
        val first = visible(items, today, versionCode, platform)
            .firstOrNull { it.autoPopup } ?: return null
        if (READ_SUPPRESSES_POPUP && first.id in readIds) return null

        // ④ 内容改过 -> 冷却作废。要先于 ③ 判断，否则"改过但还在冷却里"会被 ③ 拦掉。
        val prevSig = lastPopupSig[first.id]
        if (prevSig != null && prevSig != contentSignature(first)) return first

        val last = lastPopupAt[first.id]
        // 按**自然日**比，不按经过的毫秒比 —— 这样"隔一天"不会因为上次弹的时刻而漂。
        if (last != null &&
            dayIndex(nowMs, zoneOffsetSeconds) - dayIndex(last, zoneOffsetSeconds) <
            POPUP_COOLDOWN_DAYS
        ) return null
        return first
    }

    // ═══════════════════════ 展示辅助 ═══════════════════════

    /** 未读条数（列表页红点用）。 */
    fun unreadCount(
        items: List<AnnouncementItem>,
        today: String,
        versionCode: Int,
        platform: String,
        readIds: Set<String>,
    ): Int = visible(items, today, versionCode, platform).count { it.id !in readIds }

    /** 「重要」标签；普通公告不显示标签。 */
    fun levelLabel(level: String): String = when (level.lowercase()) {
        LEVEL_IMPORTANT -> Text.of("s_004b2568")
        else -> ""
    }

    fun isImportant(a: AnnouncementItem): Boolean =
        a.level.lowercase() == LEVEL_IMPORTANT

    /** 跳转按钮文案。 */
    fun urlLabelOf(a: AnnouncementItem): String = a.urlLabel ?: DEFAULT_URL_LABEL

    /** 列表页空态文案。**分开讲「没拉到」和「拉到了但没有」**——两者对用户含义不同。 */
    val EMPTY_NO_DATA: String get() = Text.of("s_3b55155d")
    val EMPTY_FETCH_FAILED: String get() =
        Text.of("s_934ed295") +
            Text.of("s_4e79e13e")

    // ═══════════════════════ 正文里的链接（2026-09-29 加）═══════════════════════
    //
    // ## 为什么要在正文里认链接
    // 用户原话：「公告里的内容不能长按复制，而且链接不能点击，我需要能复制，
    // 且链接点击后能自动跳转浏览器」。公告正文里贴 URL 是最自然的写法，
    // 但 Compose 的 `Text` 只会把它当普通字符画出来 —— 看着像链接，点下去没反应。
    //
    // ## 为什么放在 core 而不是界面里
    // 「哪一段算链接」是纯字符串判断，放进 core 就能在 JVM 台架上把
    // 「句中/行尾/带标点/大小写/裸域名」这些形态一条条摆出来验，
    // 而不必装到真机上用眼睛找。界面只负责把切好的结果画出来。

    /** 正文的一个片段：[text] 是原文，[link] 非空表示这一段是可点链接。 */
    data class Segment(val text: String, val link: String? = null)

    /**
     * 链接的字符类：允许出现在 URL 里的字符。
     *
     * **刻意保守**：只收 [A-Za-z0-9] 与 `-._~:/?#[]@!$&'()*+,;=%`（RFC 3986 的
     * unreserved + reserved 去掉几个容易误伤的）。中文字符**不在**其中 ——
     * 中文句号、顿号、书名号紧跟 URL 时（「详见 https://example.org。」）要能在这里断开，
     * 否则末尾的「。」会被吞进链接、点开变成 404。
     */
    private const val URL_CHARS =
        "A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%"

    /**
     * 匹配 http/https 链接。两种形态：
     *  ① 带 scheme 的 `https://…`（公告里推荐这种，也最不容易误认）；
     *  ② 裸域名 `www.example.com/…` 或 `example.com/…` —— 作者常这么写。
     *
     * 裸域名那条**必须**要求「点号 + 至少两位的顶级域名」：
     * 否则 `版本 1.0`、`第 3.5 节` 这种小数会被当成域名。
     * 顶级域名要求是字母（`com` / `cn` / `io`），排除 `1.0`（`0` 不是字母）。
     *
     * ⚠ **[RegexOption.IGNORE_CASE] 不能省**：`HTTPS://A.CN/X` 这种大写形态
     * 台架里抓到过 —— 大小写敏感时整个匹配不上，用户看到链接点不动，而代码里
     * 到处是 `startsWith("https://")` 恰好也漏掉它（两边都错、且都静默）。
     * 顶级域名列表也跟着忽略大小写，所以 `EXAMPLE.COM` 一样能认出来。
     */
    private val URL_REGEX = Regex(
        "(?:https?://[$URL_CHARS]+)" +
            "|(?:(?:www\\.)?[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?" +
            "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9\\-]*[A-Za-z0-9])?)*" +
            "\\.(?:com|cn|net|org|io|dev|app|me|co|top|xyz|info|edu|gov)" +
            "(?::\\d+)?(?:/[$URL_CHARS]*)?)",
        RegexOption.IGNORE_CASE,
    )

    /**
     * 把一段正文按链接切成若干片段，供界面渲染成可点文字。
     *
     * 保证 **`segments.joinToString("") { it.text } == 原文`**（台架断言这条）——
     * 切分不能吃掉或改动任何字符，否则「复制出来的正文」就和作者写的对不上了。
     *
     * 链接的**点击目标**（[Segment.link]）会自动补 `https://`：裸域名直接交给
     * 浏览器是解析不了的（会被当成相对路径），这也是台架钉住的一条。
     *
     * 末尾的收尾清理：URL 字符集里含 `,`、`;`、`(` 等**同时也是中文/英文标点**的字符
     * （`，。；）` 是全角所以天然断了，但半角的 `,)` 会连着）。要去掉**尾部**这些
     * 不可能是域名一部分的字符，并把它们还给后面的文字片段。
     */
    fun segments(body: String): List<Segment> {
        if (body.isEmpty()) return emptyList()
        val out = ArrayList<Segment>()
        var cursor = 0
        for (m in URL_REGEX.findAll(body)) {
            var start = m.range.first
            var end = m.range.last + 1
            // 尾部标点回吐：`,` `;` `:` `!` `?` `)` `.` 收尾都不是 URL 的一部分。
            // 用 while 而不是 if —— `https://example.org/).` 这种要连着剥掉多个。
            while (end > start && body[end - 1] in ".,;:!?)]}'\"") end--
            if (end <= start) continue
            // 剥完可能不再是合法链接（例如整段只是 `www.`），跳过这次匹配
            val raw = body.substring(start, end)
            if (!isLikelyUrl(raw)) continue

            if (start > cursor) out.add(Segment(body.substring(cursor, start)))
            out.add(Segment(raw, normalizeTarget(raw)))
            cursor = end
        }
        if (cursor < body.length) out.add(Segment(body.substring(cursor)))
        return if (out.isEmpty()) listOf(Segment(body)) else out
    }

    /**
     * 剥掉尾部标点后，剩下的是不是还像个链接。
     *
     * 两个必要条件：① 有 host（`://` 之后要有东西，或裸域名本身）；② 不含空白。
     * 这是防「`www.` 后面跟着换行」这种剥完只剩壳的情况。
     */
    private fun isLikelyUrl(s: String): Boolean {
        if (s.any { it.isWhitespace() }) return false
        val afterScheme = s.substringAfter("://", "")
        return if (s.contains("://")) afterScheme.isNotEmpty() && afterScheme.contains('.')
        else s.contains('.')
    }

    /**
     * 裸域名补上 `https://`，否则浏览器会把它当相对路径。
     *
     * ⚠ 判断要用 [String.startsWith] 的 `ignoreCase`：作者写 `HTTPS://…` 时
     * 大小写敏感的判断会认不出来，于是补成 `https://HTTPS://…` —— 一个必然打不开的
     * 地址，而界面上它看起来完全正常。台架钉住了这条。
     */
    fun normalizeTarget(url: String): String = when {
        url.startsWith("http://", ignoreCase = true) -> url
        url.startsWith("https://", ignoreCase = true) -> url
        else -> "https://$url"
    }

    /** 列表页底部的口径说明。
     *
     * 写出来是因为「为什么这条公告又弹了」是必然会有的疑问：2 天节奏是**刻意**的，
     * 不说明的话用户只会觉得这 App 老弹同一个窗。后半句解释"内容更新会立刻再弹"——
     * 否则用户看到一条**内容变了**的通知被再弹一次，会以为是节奏坏了。
     */
    val CADENCE_NOTE: String get() =
        Text.of("s_3a4ebfe7") +
            Text.of("s_1cc68da0")
}
