package com.gemini.healthbridge.core

import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

/**
 * 公告的 HTTP 拉取：**有序多地址降级**。
 *
 * ## 为什么放在 core 而不是 app
 * 这里只用 `java.net.HttpURLConnection`，没有任何 Android 依赖 —— 放在 core 就能在
 * **纯 JVM 上用本地 HTTP 服务离线验证**「第一个地址超时了会不会切第二个」
 * 这类行为。降级逻辑恰恰是最容易写错、又最不容易在真机上复现的部分
 * （要么全通、要么全不通，中间那一段很难碰巧遇到）。
 *
 * ## 降级策略
 * 按顺序逐个尝试，**第一个成功就用它**，后面的不再请求：
 * ① 省流量、少打一次对方的服务器；② 避免两个地址内容不同时出现"一会儿一变"。
 *
 * 全局还有一个**总时长预算** [DEFAULT_TOTAL_BUDGET_MS]：地址列表以后可能变长，
 * 没有总预算的话最坏情况是「N 个地址 × 每个 10 秒」，用户在网络很差时要等很久才
 * 走完一轮（虽然不阻塞界面，但会白耗电）。
 *
 * ## 失败一律是"值"，不是异常
 * 全部失败时返回 [FetchResult.AllFailed] 并带上每个地址的失败原因，
 * **调用方据此静默降级**（用上次缓存的 JSON，或什么都不显示）。
 * 联网失败绝不能让用户看到报错 —— 公告是附加信息，不是功能本身。
 */
object AnnouncementHttp {

    /** 连接超时：4 秒。国内到 Vercel 的握手本身就慢，再长就是白等。 */
    const val DEFAULT_CONNECT_TIMEOUT_MS = 4_000

    /** 读取超时：6 秒。公告是一张小 JSON，正常几百毫秒就回来了。 */
    const val DEFAULT_READ_TIMEOUT_MS = 6_000

    /** 一轮降级的总预算：20 秒。超过就不再试后面的地址了。 */
    const val DEFAULT_TOTAL_BUDGET_MS = 20_000L

    /**
     * 响应体积上限：256 KB。
     * 公告是一张小 JSON，正常几 KB；超过这个量说明对方给的不是公告
     * （CDN 错误页、被劫持的广告页、或者干脆是别的文件），直接判失败而不是读进内存。
     */
    const val MAX_BYTES = 256 * 1024

    /**
     * 地址列表里的这条是不是「还没填」的占位。
     *
     * 占位地址在尝试前会被跳过 —— 否则每次启动都对一个不存在的域名做一次 DNS 查询，
     * 白白耗电，还会让日志里堆满看不懂的 UnknownHostException。
     */
    const val PLACEHOLDER_MARK = "REPLACE-ME"

    sealed interface FetchResult {
        /** [index] 是命中的地址下标（从 0 起），[url] 是命中的地址，便于排查与显示「来自哪一路」。 */
        data class Ok(val raw: String, val index: Int, val url: String) : FetchResult

        /**
         * 全部失败。[reasons] 与地址一一对应（跳过占位地址的记 `跳过（占位地址）`）。
         * 不抛异常：调用方只管静默降级。
         */
        data class AllFailed(val reasons: List<String>) : FetchResult
    }

    /**
     * **二进制**拉取的结果（公告配图用，2026-10-05 加）。
     *
     * ⚠ [Ok] 刻意**不是** `data class`：它带 `ByteArray`，而数组的 `equals` 是**引用比较**，
     * 做成 `data class` 会生成一个"看着能用、实际永远不相等"的相等性 —— 这种属性最容易被
     * 后来的人当真的用（比如放进 `Set` 去重）。
     */
    sealed interface BytesResult {
        class Ok(val bytes: ByteArray, val index: Int, val url: String) : BytesResult
        data class AllFailed(val reasons: List<String>) : BytesResult
    }

    /**
     * 图片体积上限：**2 MB**。
     *
     * ⚠ 比公告 JSON 的 [MAX_BYTES] 大得多（4096 倍），因为这俩本来就不是一类东西 ——
     *   JSON 是几 KB 的文本，配图是二进制。
     * ⚠ 但也不能不设：公告页会被一张图**拖住**，而"作者上传了一张 20 MB 的原图"
     *   是完全可能发生的（他可能就是拿手机拍的照直接传了）。
     *   2 MB 足够放一张 1080p 的压过的图。
     */
    const val MAX_IMAGE_BYTES = 2 * 1024 * 1024

    /**
     * 依次尝试 [urls]，返回第一个成功的结果。
     *
     * [userAgent] 会带在请求头上：方便托管方在日志里认出这是 App 的请求（而不是爬虫），
     * 出问题时也好排查是哪一版。**不携带任何设备标识**。
     *
     * ⚠⚠ `jsonOnly = true`（2026-10-10 加）：公告**必须**是一份 JSON。
     *   HTTP 200 不等于"拿到了公告" —— 运营商劫持页、门户页（captive portal）、
     *   托管方的错误页都可能返回 `200 + text/html`。不挡的话，那一整页 HTML 会被
     *   当成"拿到公告了"、写进缓存，然后永远解析失败 ⇒ 用户看到的现象正是
     *   "每次改完公告手机都不弹窗"，而公告页上写着"格式不认识"。
     */
    fun fetch(
        urls: List<String>,
        userAgent: String,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        totalBudgetMs: Long = DEFAULT_TOTAL_BUDGET_MS,
        maxBytes: Int = MAX_BYTES,
        nowMs: () -> Long = { System.currentTimeMillis() },
    ): FetchResult =
        when (val r = fetchRaw(urls, userAgent, connectTimeoutMs, readTimeoutMs, totalBudgetMs, maxBytes, ACCEPT_TEXT, jsonOnly = true, nowMs = nowMs)) {
            // 不做编码猜测：公告 JSON 按 UTF-8 存（见 announcement.json），
            // 猜错会得到乱码标题，而乱码很难被当成本地化的 bug 报上来
            is Raw.Ok -> FetchResult.Ok(String(r.bytes, Charsets.UTF_8), r.index, r.url)
            is Raw.Bad -> FetchResult.AllFailed(r.reasons)
        }

    /**
     * 依次尝试 [urls]，返回第一个成功的**二进制**结果（公告配图用，2026-10-05 加）。
     *
     * ⚗ 与 [fetch] **共用同一份降级逻辑**（[fetchRaw]）：多地址、总预算、占位跳过、
     *   体积上限这一整套判据只写一遍。各写一套的话，两边必然漂移成
     *   "图片会重试、JSON 不会"这种没人能解释的差异。
     *
     * ⚠ `jsonOnly = false`：这里是**二进制**（图片），做"首字符是不是 `{`"的检查
     *   会把所有图片一起挡掉。JSON 那道检查只属于 [fetch]。
     *
     * ⚠ 调用方拿到的 `ByteArray` 要当**不可信输入**：解码放在 [AnnouncementImages] 里，
     *   且解不出来（或不是图片）就当加载失败静默处理 —— 绝不让它抛到界面上。
     */
    fun fetchBytes(
        urls: List<String>,
        userAgent: String,
        connectTimeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = DEFAULT_READ_TIMEOUT_MS,
        totalBudgetMs: Long = DEFAULT_TOTAL_BUDGET_MS,
        maxBytes: Int = MAX_IMAGE_BYTES,
        nowMs: () -> Long = { System.currentTimeMillis() },
    ): BytesResult =
        when (val r = fetchRaw(urls, userAgent, connectTimeoutMs, readTimeoutMs, totalBudgetMs, maxBytes, ACCEPT_IMAGE, jsonOnly = false, nowMs = nowMs)) {
            is Raw.Ok -> BytesResult.Ok(r.bytes, r.index, r.url)
            is Raw.Bad -> BytesResult.AllFailed(r.reasons)
        }

    /** 公告 JSON 的 `Accept`。 */
    private const val ACCEPT_TEXT = "application/json, text/plain, */*"

    /**
     * 图片的 `Accept`。
     * ⚠ 仍要**带上通配兜底**：有些 CDN 对不认识的文件扩展名干脆不返回 `Content-Type`，
     *   只声明图片类型会把它们一起挡掉。
     *
     * ⚠⚠ 这里**不能把通配写成字面量**（星号+斜杠+星号）：那三个字符里的后两个是
     *   **注释结束符**，会把这段 KDoc 提前截断 ⇒ 后面的文字变成代码 ⇒ 满屏语法错误，
     *   而报错行号指向的是 KDoc 中间那一行，**看不出跟注释有关**。
     *   （2026-10-05 实测踩过：报 `Syntax error: Expecting member declaration` 十几条。）
     */
    private const val ACCEPT_IMAGE = "image/*, */*"

    private sealed interface Raw {
        class Ok(val bytes: ByteArray, val index: Int, val url: String) : Raw
        data class Bad(val reasons: List<String>) : Raw
    }

    /** 多地址降级的**唯一一份实现**：[fetch] 与 [fetchBytes] 都走它。 */
    private fun fetchRaw(
        urls: List<String>,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        totalBudgetMs: Long,
        maxBytes: Int,
        accept: String,
        jsonOnly: Boolean,
        nowMs: () -> Long,
    ): Raw {
        if (urls.isEmpty()) return Raw.Bad(listOf("没有配置公告地址"))

        val reasons = ArrayList<String>(urls.size)
        val start = nowMs()
        var attempted = 0

        for ((i, url) in urls.withIndex()) {
            if (url.isBlank() || url.contains(PLACEHOLDER_MARK)) {
                reasons.add("跳过（占位地址）")
                continue
            }
            if (attempted > 0 && nowMs() - start >= totalBudgetMs) {
                reasons.add(Text.of("s_db4c8e39"))
                continue
            }
            when (val r = fetchOne(url, userAgent, connectTimeoutMs, readTimeoutMs, maxBytes, accept, jsonOnly)) {
                is One.Ok -> return Raw.Ok(r.bytes, i, url)
                is One.Bad -> {
                    attempted++
                    reasons.add(r.reason)
                }
            }
        }
        return Raw.Bad(reasons)
    }

    private sealed interface One {
        class Ok(val bytes: ByteArray) : One
        data class Bad(val reason: String) : One
    }

    private fun fetchOne(
        url: String,
        userAgent: String,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        maxBytes: Int,
        accept: String,
        jsonOnly: Boolean,
    ): One {
        var conn: HttpURLConnection? = null
        return try {
            // 用 URI 再 toURL：`URL(String)` 构造函数在新 JDK 上已废弃
            val u = URI.create(url).toURL()
            conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                this.connectTimeout = connectTimeoutMs
                this.readTimeout = readTimeoutMs
                instanceFollowRedirects = true
                useCaches = false                     // 要的是最新一份公告，别吃代理缓存
                setRequestProperty("Accept", accept)
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("User-Agent", userAgent)
            }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) return One.Bad("HTTP $code")

            // 先看长度头，超限直接放弃（避免把一个大文件读进内存）
            val declared = conn.contentLengthLong
            if (declared > maxBytes) return One.Bad(Text.fmt("s_d1628c8f", declared))

            val bytes = readCapped(conn.inputStream, maxBytes)
                ?: return One.Bad(Text.fmt("s_a972db7c", maxBytes))
            if (bytes.isEmpty()) return One.Bad(Text.of("s_55a26212"))

            // ⚠⚠ 只有 JSON 那一路上这道检查（见 [fetch] 的注释）：200 + HTML
            //   必须算**这一路失败**、继续降级，而不是"拿到了"。
            if (jsonOnly && !looksLikeJson(bytes)) return One.Bad("不是 JSON（返回的很可能是网页）")

            // ⚠ 这里**只取字节、不做解码**：`fetch` 负责按 UTF-8 解成文本，
            //   `fetchBytes` 直接把字节交给图片解码器 —— 共用这一层才能让两者
            //   的降级/超时/体积判据完全一致。
            One.Ok(bytes)
        } catch (e: Exception) {
            One.Bad(e.javaClass.simpleName + (e.message?.let { "：$it" } ?: ""))
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    /**
     * 这段字节看起来像不像一份 JSON 文本？
     *
     * 判据刻意**只取"第一个非空白字符是 `{` 或 `[`"**：
     * - 不在这里做完整解析 —— 那是 [Announcement.parse] 的职责，网络层只挡
     *   "明显不是 JSON"的那一类（HTML 错误页 / 门户页 / 二进制）。
     * - 不做更严的检查（比如必须含 `announcements`）：合法但**内容不对**的 JSON
     *   属于"拿到了、解析不了"，那条路径有专门的提示文案；在这里挡掉会把它
     *   说成"连不上"，反而更难查。
     * - 跳过的空白包含 **UTF-8 BOM**（`\uFEFF`）：有些编辑器保存时会带上，
     *   而它不影响 JSON 的合法性。
     */
    private fun looksLikeJson(bytes: ByteArray): Boolean {
        val s = String(bytes, Charsets.UTF_8)
        for (c in s) {
            if (c == '\uFEFF' || c.isWhitespace()) continue
            return c == '{' || c == '['
        }
        return false
    }

    /** 最多读 [max] 字节；超过则返回 null（不允许把超大响应整个读进来）。 */
    private fun readCapped(stream: java.io.InputStream, max: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream(minOf(max, 16 * 1024))
        val buf = ByteArray(8 * 1024)
        var total = 0
        stream.use { input ->
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                total += n
                if (total > max) return null
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }

    /**
     * 两次拉取之间的最小间隔。
     *
     * 用户可能频繁开关 App（看一眼就退），每次冷启动都打一轮网络没有必要，
     * 也把托管方的流量当免费。15 分钟足够让新公告"尽快出现"，
     * 又不至于把一次会话里的反复切换都变成请求。
     */
    val MIN_FETCH_INTERVAL_MS: Long = TimeUnit.MINUTES.toMillis(15)

    /** 请求头里的 User-Agent。只带应用名与版本，不带任何设备标识。 */
    fun userAgent(versionName: String, versionCode: Int): String =
        "VitalBridge/$versionName($versionCode) (Android)"

    /** 托管方返回了内容但解析不了时的说明（与网络失败区分开，两者含义不同）。 */
    val BAD_CONTENT_NOTE: String get() = Text.of("s_72a6f25c")
}
