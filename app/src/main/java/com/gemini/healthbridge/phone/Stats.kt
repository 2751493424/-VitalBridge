package com.gemini.healthbridge.phone

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.UUID

/**
 * 匿名使用统计的**同意状态与安装 ID**（2026-10-28）。
 *
 * ## 三条不能退让的性质
 * ① **默认关**。用户没在首次启动的弹窗里点「同意并继续」之前，一个字节都不发。
 * ② **随机 ID，不是设备标识**。ID 由 `UUID.randomUUID()` 生成，与 IMEI / Android ID /
 *   MAC / 手机号**无关**，卸载重装即变；关闭统计时**立即删除**（见 [clearId]）。
 * ③ **决定只做一次**。弹窗只弹一次；之后要改只能去「用户」页，不再骚扰。
 *
 * ⚠ key 里带 `_v1`：将来若统计口径大改（比如要新增一类字段），换 key 重问一遍，
 *   而不是悄悄把老用户算进新口径。
 */
object StatsPrefs {
    private const val SP = "stats_v1"
    private const val K_DECIDED = "decided"
    private const val K_ON = "on"
    private const val K_ID = "install_id"
    private const val K_LAST_OPEN_DAY = "last_open_day"

    private fun p(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)

    /**
     * `enabled` 的内存缓存（2026-11-03 加）。
     *
     * ⚠ `event()` 可能在**主线程**被调用，而 `getSharedPreferences()` 首次访问要读盘 + 解析 XML。
     *   缓存之后主线程只做一次 volatile 读；值只在 [setEnabled] 里变，且那之后立刻同步缓存，
     *   所以不会出现"关了还在发"的窗口。
     */
    @Volatile private var cachedEnabled: Boolean? = null

    /** 心跳节流用的日期，同样缓存（`appOpen` 在 `onResume` 里读）。 */
    @Volatile private var cachedLastOpenDay: String? = null

    /** 弹过窗了吗（**不论同意还是拒绝**都算决定过）。 */
    fun decided(ctx: Context): Boolean = p(ctx).getBoolean(K_DECIDED, false)

    /** 现在是否真的在上报：决定过 **且** 开关是开的。 */
    fun enabled(ctx: Context): Boolean = cachedEnabled ?: p(ctx).let { sp ->
        val v = sp.getBoolean(K_DECIDED, false) && sp.getBoolean(K_ON, false)
        cachedEnabled = v
        v
    }

    /** 首次弹窗 / 用户页开关的统一落点。关掉时顺手删掉安装 ID。 */
    fun setEnabled(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean(K_DECIDED, true).putBoolean(K_ON, on).apply()
        cachedEnabled = on
        if (!on) clearId(ctx)
    }

    /**
     * 安装 ID：**只有真的开启统计时才会生成**。
     *
     * ⚠ 用 `commit()` 而不是 `apply()`：两个事件同时进来时，`apply()` 的异步落盘
     *   可能让第二个线程读到「还没写进去」的状态，于是同一台机器生成两个 ID
     *   —— UV 直接算多。这里一秒钟最多几十次，同步写的代价可以忽略。
     */
    fun installId(ctx: Context): String {
        val old = p(ctx).getString(K_ID, null)
        if (old != null) return old
        val fresh = UUID.randomUUID().toString()
        p(ctx).edit().putString(K_ID, fresh).commit()
        return fresh
    }

    fun clearId(ctx: Context) {
        p(ctx).edit().remove(K_ID).apply()
    }

    /**
     * 最后一次上报「打开」的日期（**北京时间**，与 Worker 的 `dayCN()` 同口径）。
     *
     * ⚠⚠ 这个字段是「心跳节流」的唯一依据，见 [Stats.appOpen] 的说明。
     */
    fun lastOpenDay(ctx: Context): String =
        cachedLastOpenDay
            ?: (p(ctx).getString(K_LAST_OPEN_DAY, "") ?: "").also { cachedLastOpenDay = it }

    fun setLastOpenDay(ctx: Context, day: String) {
        cachedLastOpenDay = day
        p(ctx).edit().putString(K_LAST_OPEN_DAY, day).apply()
    }
}

/**
 * 匿名使用统计的**发送端**（2026-10-28）。
 *
 * ## 只发这些字段
 * 应用版本 / 版本号 · 手机型号 · 系统版本与 API · 界面语言 · 事件名 + 一个短标签 ·
 * 安装 ID（随机 UUID）。**没有任何健康数值** —— 这条不是洁癖，是合规红线：
 * 一旦血压/心率这些数值出境，本应用就从「匿名使用统计」变成「健康数据处理」，
 * 与「最大风险是医疗器械宣称」这条红线直接相邻。
 *
 * ## 为什么不用第三方 SDK
 * 统计 SDK 基本都带广告/画像能力，而且会把设备标识当默认字段。这里的全部需求只是
 * 「哪一版、什么机器、点了什么」—— 一个自建的 60 行发送端就够了，还省掉一个隐私政策条款。
 *
 * ## 三条实现纪律
 * ① **绝不阻塞**：事件先入内存队列，独立协程批量 POST；主线程只做 `trySend`。
 * ② **绝不抛异常**：网络失败、JSON 写错、Worker 挂了 —— 全部静默，最多重试一次。
 * ③ **绝不刷爆**：窗口内事件数超上限就丢（见 [MAX_EVENTS_PER_WINDOW]）。
 */
object Stats {

    /**
     * 匿名统计服务的地址（自行部署后填这里）。
     *
     * ⚠ **留空 = 整个模块静默不工作**：一条请求都不会发，队列直接丢弃。
     *   这样未部署时把包发出去也不会在用户机器上疯狂重试。
     */
    private const val BASE_URL = ""

    private const val PATH = "/v1/ev"

    /** 一批最多几条：Worker 侧按 16 条封顶，这里留一半余量。 */
    private const val MAX_BATCH = 8

    /** 普通事件攒批最长等多久。 */
    private const val FLUSH_WAIT_MS = 8_000L

    /**
     * `app_open` 单独的最短等待：**1.2 秒就走**。
     *
     * ⚠⚠ 为什么心跳不能和别的共用 8 秒：用户点开 App 往往看两眼就切走，
     *   15 秒的攒批窗口意味着"这次打开"可能永远发不出去（进程被回收）。
     *   而心跳恰恰是 DAU 的唯一依据 —— 宁愿多一次请求，也不能丢这一次打开。
     */
    private const val APP_OPEN_FLUSH_MS = 1_200L

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_000

    /** 请求体上限：超过就整批丢弃（正常一批 8 条约 600 字节）。 */
    private const val MAX_BODY_BYTES = 8 * 1024

    /** 60 秒窗口内的事件上限。防的是"某处代码在循环里调 event()"把队列打爆。 */
    private const val MAX_EVENTS_PER_WINDOW = 40
    private const val WINDOW_MS = 60_000L

    /**
     * 允许上报的事件名**白名单**。
     *
     * ⚠ 白名单在客户端也留一份：服务端还有一份（它的
     *   `EVENTS`）。两边都要改 —— 只改一边的表现是「加了埋点但报表里永远没有」，
     *   而它不会报错，只会静默为空。
     */
    private val EVENTS = setOf(
        "app_open",
        "measure_sync",
        "watch_install",
        "screen",
    )

    /**
     * 队列元素：**带上事件名**。
     * ⚠ 只放 JSON 串就没法在攒批时判断"这一条是不是心跳"，也就没法给它开小灶。
     */
    private data class Pending(val name: String, val json: String)

    private val queue = Channel<Pending>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val window = ArrayDeque<Long>()

    @Volatile private var app: Context? = null
    @Volatile private var started = false
    @Volatile private var versionCode = 0
    @Volatile private var versionName = ""

    /**
     * 绑定 Application（在 `GeminiApp.onCreate` 里调一次）。
     *
     * ⚠ 只做捆绑与版本号缓存，**不生成安装 ID、不发请求** —— 同意之前不能有任何副作用。
     */
    fun attach(ctx: Context) {
        if (app != null) return
        val a = ctx.applicationContext
        app = a
        runCatching {
            val pm = a.packageManager
            val info = pm.getPackageInfo(a.packageName, 0)
            versionCode = info.versionCode
            versionName = info.versionName ?: ""
        }
        start()
    }

    /**
     * 「今天打开过」的心跳（含版本/机型/语言）。**按天去重，不按进程**。
     *
     * ⚠⚠⚠ 为什么从「每进程一次」改成「每天一次」（2026-10-28 真机踩出来的）：
     *   `PhoneBtServer` 是**前台服务**，系统会长期保活这个进程 ——
     *   实测装完 App 后进程活了几个小时，而 `openedOnce` 一直是 true，
     *   于是「今天打开过」只上报了第一次，之后用户再怎么点都静默
     *   ⇒ DAU 会退化成"开机以来装了几台"，而不是"今天有多少人在用"。
     *
     * 判据：**北京时间**的日期变了就再发一次（与 Worker 的 `dayCN()` 同口径）。
     * 调用点有两处：`MainScreen` 首次组合、`MainActivity.onResume`（每天第一次进前台）。
     */
    fun appOpen() {
        val ctx = app ?: return
        if (!StatsPrefs.enabled(ctx)) return
        val today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString()
        if (StatsPrefs.lastOpenDay(ctx) == today) return
        StatsPrefs.setLastOpenDay(ctx, today)
        event("app_open")
    }

    /**
     * 记一个事件。
     *
     * @param name 必须在 [EVENTS] 里，否则直接丢（防止把用户内容当事件名传进来）。
     * @param key  短标签，例如 `ECG` / `ok` / `fail`。**不能放数值、不能放自由文本。**
     * @param detail 第二个短标签（可空），用法同上。
     */
    fun event(name: String, key: String? = null, detail: String? = null) {
        val ctx = app ?: return
        if (BASE_URL.isEmpty()) return
        if (name !in EVENTS) return
        if (!StatsPrefs.enabled(ctx)) return
        if (!allowNow()) return
        queue.trySend(Pending(name, buildEvent(name, key, detail)))
    }

    /** 滑动窗口限流：超过就丢弃，**不排队**（排队等于把问题推迟到后面爆发）。 */
    private fun allowNow(): Boolean = synchronized(window) {
        val now = System.currentTimeMillis()
        while (window.isNotEmpty() && now - window.first() > WINDOW_MS) window.removeFirst()
        if (window.size >= MAX_EVENTS_PER_WINDOW) return false
        window.addLast(now)
        return true
    }

    private fun start() {
        if (started) return
        started = true
        scope.launch {
            // 预热：把首次 SharedPreferences 读盘挪到 IO 线程。
            // ⚠ 主线程（`onResume` → `appOpen`）第一次判开关时就直接命中缓存，不做磁盘 IO。
            app?.let { c ->
                runCatching {
                    StatsPrefs.enabled(c)
                    StatsPrefs.lastOpenDay(c)
                }
            }
            while (isActive) {
                val first = runCatching { queue.receive() }.getOrNull() ?: continue
                val batch = ArrayList<Pending>(MAX_BATCH)
                batch += first
                // 攒批：够 8 条就走；心跳只等 1.2 秒，其它事件等 8 秒
                val wait = if (first.name == "app_open") APP_OPEN_FLUSH_MS else FLUSH_WAIT_MS
                withTimeoutOrNull(wait) {
                    while (batch.size < MAX_BATCH) batch += queue.receive()
                }
                runCatching { post(batch.map { it.json }) }
            }
        }
    }

    /**
     * 真正发出去。
     *
     * ⚠ 这里刻意**不做「补发」**：失败就丢。理由是「今天打开过 App」这种事件迟到
     *   几天到达反而污染归日统计；而丢掉的量级对趋势判断没有影响。
     */
    private fun post(batch: List<String>) {
        if (BASE_URL.isEmpty()) return
        val ctx = app ?: return
        if (!StatsPrefs.enabled(ctx)) return

        val body = buildString {
            append('{')
            append("\"id\":\"").append(StatsPrefs.installId(ctx)).append("\",")
            append("\"v\":").append(versionCode).append(',')
            append("\"vn\":\"").append(esc(versionName)).append("\",")
            append("\"sdk\":").append(Build.VERSION.SDK_INT).append(',')
            append("\"os\":\"").append(esc(Build.VERSION.RELEASE ?: "")).append("\",")
            append("\"m\":\"").append(esc(Build.MODEL ?: "")).append("\",")
            append("\"lang\":\"").append(esc(Locale.getDefault().language)).append("\",")
            append("\"ev\":[").append(batch.joinToString(",")).append(']')
            append('}')
        }
        if (body.length > MAX_BODY_BYTES) return

        val payload = body.toByteArray(Charsets.UTF_8)
        var attempt = 0
        while (attempt < 2) {
            attempt++
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(BASE_URL + PATH).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    // 一个显式的自定义头：挡住扫描器/爬虫的顺手 POST（不是安全措施，是降噪）
                    setRequestProperty("x-vb", "1")
                }
                conn.outputStream.use { it.write(payload) }
                val code = conn.responseCode
                // 204 = 收到；其余（含 4xx）都不重试 —— 重试一个被拒绝的请求没有意义
                if (code in 200..299) return
                if (code in 400..499) return
            } catch (_: Throwable) {
                // 网络抖动：最多再试一次
            } finally {
                runCatching { conn?.disconnect() }
            }
        }
    }

    private fun buildEvent(name: String, key: String?, detail: String?): String = buildString {
        append("{\"n\":\"").append(esc(name)).append('"')
        if (!key.isNullOrEmpty()) append(",\"k\":\"").append(esc(key)).append('"')
        if (!detail.isNullOrEmpty()) append(",\"d\":\"").append(esc(detail)).append('"')
        append('}')
    }

    /**
     * JSON 字符串转义。
     *
     * ⚠ 手写而不是用序列化库：这里只有 5 个短字段，而 `kotlinx.serialization` 需要一个
     *   带 `@Serializable` 的 DTO —— 为一次 600 字节的 POST 加一个数据类不划算。
     *   但**必须把控制字符一起转掉**：型号里真出现过奇怪字符（个别山寨机的 `Build.MODEL`）。
     */
    private fun esc(s: String): String {
        val sb = StringBuilder(s.length + 8)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append(' ') else sb.append(c)
            }
        }
        return sb.toString()
    }
}
