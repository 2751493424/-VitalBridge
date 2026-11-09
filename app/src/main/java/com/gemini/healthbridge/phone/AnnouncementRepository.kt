package com.gemini.healthbridge.phone

import android.content.Context
import com.gemini.healthbridge.core.Announcement
import com.gemini.healthbridge.core.AnnouncementHttp
import com.gemini.healthbridge.core.AnnouncementItem
import com.gemini.healthbridge.core.AnnouncementUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 公告的唯一入口：拉取 → 缓存 → 解析 → 算出「该显示什么」与「该弹哪条」。
 *
 * ## 三条必须守住的性质
 * ① **绝不抛异常**。公告是附加信息；它出任何问题都不能影响 App 本身。
 * ② **绝不阻塞启动**。整个流程在 [Dispatchers.IO] 上跑，界面拿到的是 StateFlow。
 * ③ **失败静默**。拉不到就用上次缓存的原文；连缓存都没有就显示"没有公告"，
 *    不弹错误、不提示重试 —— 用户不需要知道公告服务器今天通不通。
 *
 * ## 为什么缓存原文而不是缓存结果
 * 见 [AnnouncementStore] 的注释：解析规则会随版本演进，缓存解析结果等于冻结旧判断。
 */
object AnnouncementRepository {

    /**
     * 公告地址，**有序**：从上到下依次尝试，第一个成功的就用它（见 [AnnouncementHttp]）。
     *
     * ## 为什么是列表而不是一个地址
     * 公告是唯一"必须在国内能拿到"的东西，而国内能稳定访问的静态托管既不是 Vercel
     * 也不是 GitHub Pages（Vercel 官方文档自己写明 `.vercel.app` 会被 GFW 屏蔽/限速、
     * 大陆无节点）。所以留了第三条给国内可达的方案。**改这里不需要动任何别的代码**。
     *
     * 顺序按用户的选择定：Vercel 优先、GitHub Pages 备用。第三条等自建域名或
     * EdgeOne Pages / 帽子云上线后填上。
     *
     * ⚠ 全部是 HTTPS。Android 9+ 默认禁止明文 HTTP，往里填 http:// 会静默失败。
     * ⚠ 含 `REPLACE-ME` 的地址会被当作占位跳过（不会去查 DNS 白耗电）。
     */
    val URLS: List<String> = listOf(
        // ① **Cloudflare R2（主）** —— 2026-10-17 起公告改为由 CF 托管，与 APK 同一个桶：
        //    `apk/vitalbridge-latest.apk` 与 `announcement.json` 都在 `vitalbridge-free` 里。
        //    ⚠ 对象必须放在**桶根目录**（键名就是 `announcement.json`）：配图地址是按
        //      「把 JSON 地址最后一段换成图片相对路径」推出来的，放子目录会让 `images/` 也跟着换层级。
        //    ⚠ 当前用的是托管方提供的"开发用"域名（有限速），量上来后应绑自定义域名，
        //      届时只改这一行即可（解析与配图逻辑都跟地址走，不用动代码）。
        "https://REPLACE-ME.invalid/announcement.json",
        // ② jsDelivr 的 GitHub 镜像：**兜底**。R2 挂了/桶被改权限时还能出公告。
        //    它会缓存，发公告后有几分钟延迟 —— 正因如此才不该当主源。
        "https://REPLACE-ME-cdn.invalid/announcement.json",
        // ③ GitHub raw：最后兜底（国内常被墙）。
        "https://REPLACE-ME-raw.invalid/announcement.json",
    )

    data class State(
        /** 当前可见的公告（已按平台/版本/过期筛过，顺序 = JSON 顺序 = 最新在前）。 */
        val items: List<AnnouncementItem> = emptyList(),
        /**
         * **累积的历史公告**（最新在前），含服务端已删掉的那些（2026-10-04）。
         *
         * ⚗ 为什么要与 [items] 分开：两者回答的是**不同的问题** ——
         *   [items] 是"服务端现在公布着什么"（受 `show`/`hideAfterDate`/版本门控），
         *   [history] 是"**本地见过什么**"（只增不减）。
         *   公告列表页显示的是 `history`（用户要能回看），
         *   而 [popup] / 未读计数仍只看 [items]（那是"当前有效"的语义）。
         */
        val history: List<HistoryEntry> = emptyList(),
        /** 内容拿到了但解析不了时的说明；不影响其它功能，只在公告页显示。 */
        val parseError: String? = null,
        /** 这次是不是连一次都没连上（用于区分"没拉到"与"拉到了但没有公告"）。 */
        val fetchFailed: Boolean = false,
        /** 上次成功拉取的时间，0 = 从未成功过（此时显示的是"没有公告"，不是"离线"）。 */
        val lastSuccessAt: Long = 0L,
        /** 该自动弹的那一条。界面弹完调 [consumePopup] 清掉，保证一次会话只弹一次。 */
        val popup: AnnouncementItem? = null,
        /** 首次加载中（界面据此避免闪一下"没有公告"）。 */
        val loading: Boolean = false,
        /**
         * 已读的公告 id。
         *
         * 放在状态里而不是每次去读 SharedPreferences：未读红点要跟着"刚点开一条"立刻变，
         * 读文件不会触发重组 —— 那样用户会看到红点不动，以为点击没生效。
         */
        val readIds: Set<String> = emptySet(),
        /**
         * 服务端公告 JSON 顶层的 `update` 块（2026-10-16，应用内更新用）。
         *
         * ⚠ 为 null 的三种情况：还没拉过、没拉成功、或者 JSON 里没有/写得不合法
         *   （`parseUpdate` 会逐字段校验，一条不过就整块作废）。
         * ⚠ **与公告互不相干**：更新提示不该依赖公告解析成功。
         */
        val update: AnnouncementUpdate? = null,
        /**
         * 这次公告是**从哪条地址**拉到的（`null` = 没拉到/用了缓存）。
         *
         * ⚗ 2026-10-17 加：主源从 GitHub 换到 R2 之后，一旦降级到兜底源，
         *   用户看到的公告就是 GitHub 上那份**没人维护的旧文件** ——
         *   而界面完全看不出来。把来源显示出来，这类问题一眼可辨。
         */
        val sourceUrl: String? = null,
        /** 三条地址**各自**的失败原因（只在全部失败时非空），用于把"检查失败"说清楚。 */
        val fetchReasons: List<String> = emptyList(),
    )

    /**
     * 历史公告的一条（2026-10-04）。
     *
     * @param item 公告本体（含 `date`，发布方填的「发出时间」）。
     * @param firstSeenAt 本地**首次见到**它的时间。⚠ `date` 可空 ⇒ 那时用它兜底显示，
     *   否则列表里会有一条"没有时间"的公告，用户无从判断新旧。
     * @param retired 服务端的最新 JSON 里**已经没有它了**（下架/删除）。
     *   ⚠ 界面要标出来：否则用户会以为这条还在生效，而它可能已经被撤回了。
     */
    data class HistoryEntry(
        val item: AnnouncementItem,
        val firstSeenAt: Long,
        val retired: Boolean,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    /** 同一时刻只允许一轮刷新（启动时 + 回到前台可能同时触发）。 */
    private val fetching = AtomicBoolean(false)

    /** SharedPreferences 并发写的保护（读-改-写不是原子的）。 */
    private val ioLock = Mutex()
    /** 刷新串行锁（见 [refresh] 的说明）。 */
    private val refreshMutex = Mutex()

    /**
     * 拉取并刷新状态。
     *
     * [force] = true 时忽略最小间隔（公告页的下拉刷新/手动重试用它）。
     *
     * 最小间隔的意义：用户可能频繁开关 App，每次冷启动都打一轮网络既没必要、
     * 也是在白用托管方的流量。15 分钟内直接用缓存。
     */
    suspend fun refresh(context: Context, force: Boolean = false) {
        // ⚠⚠ 用 Mutex 而不是 `AtomicBoolean.compareAndSet`（2026-10-17 改）：
        //   原来的写法在"启动那一轮还没跑完"时**直接 return**，用户这时点「检查更新」
        //   读到的还是旧状态 —— 界面表现为"已是最新"，而服务端明明有新版本。
        //   Mutex 会**排队**：第二轮在本轮结束后自己再拉一次，语义才对。
        refreshMutex.withLock { refreshLocked(context, force) }
    }

    private suspend fun refreshLocked(context: Context, force: Boolean) {
        try {
            val app = context.applicationContext
            val now = System.currentTimeMillis()
            val cached = AnnouncementStore.cachedRaw(app)
            val lastAttempt = AnnouncementStore.lastAttemptAt(app)

            _state.value = _state.value.copy(loading = _state.value.items.isEmpty())

            val freshEnough =
                cached != null && now - lastAttempt < AnnouncementHttp.MIN_FETCH_INTERVAL_MS
            if (!force && freshEnough) {
                applyRaw(app, cached, fetchFailed = false, atMs = AnnouncementStore.cachedAt(app))
                return
            }

            val (versionName, versionCode) = versionInfo(app)
            AnnouncementStore.setLastAttemptAt(app, now)

            val result = withContext(Dispatchers.IO) {
                AnnouncementHttp.fetch(
                    urls = URLS,
                    userAgent = AnnouncementHttp.userAgent(versionName, versionCode),
                )
            }

            when (result) {
                is AnnouncementHttp.FetchResult.Ok -> {
                    withContext(Dispatchers.IO) {
                        ioLock.withLock { AnnouncementStore.saveRaw(app, result.raw, now, result.url) }
                    }
                    applyRaw(app, result.raw, fetchFailed = false, atMs = now)
                    _state.value = _state.value.copy(
                        sourceUrl = result.url, fetchReasons = emptyList(),
                    )
                }
                is AnnouncementHttp.FetchResult.AllFailed -> {
                    // 拉不到不是错误：用上次缓存的原文。连缓存都没有才算"什么都没拿到"
                    if (cached != null) {
                        applyRaw(app, cached, fetchFailed = true,
                            atMs = AnnouncementStore.cachedAt(app))
                    } else {
                        _state.value = State(fetchFailed = true)
                    }
                    // 把"三条地址各自怎么失败的"带上，界面能原样显示出来
                    _state.value = _state.value.copy(
                        sourceUrl = null, fetchReasons = result.reasons,
                    )
                }
            }
        } catch (t: Throwable) {
            // 兜底：这里出任何问题都不该影响 App。宁可没有公告。
            _state.value = _state.value.copy(loading = false)
        }
    }

    /**
     * 组装历史条目（2026-10-04）。
     *
     * @param onServer 服务端**这次解析到的全部**条目（`parsed.items`，含 `show:false`）。
     *   ⚠ 用它判 `retired` 而**不是**用 `visible`：一条被 `hideAfterDate` 拦下的公告
     *   仍在服务端文件里，只是"过期不显示了"——那不是下架，
     *   标成「已下线」会误导用户以为作者撤回了它。
     *   ⚠ 解析失败时传 `emptyList()` ⇒ 全部标成 retired。此时"服务端有没有"是
     *   **未知**而不是"没有"，所以这个标记会偏保守（宁可说"可能已下线"）。
     *   该分支的界面本来就在显示解析错误，两条信息一起看是自洽的。
     */
    private fun historyEntries(
        app: Context,
        onServer: List<AnnouncementItem>,
    ): List<HistoryEntry> {
        val hist = AnnouncementStore.history(app)
        if (hist.isEmpty()) return emptyList()
        val seen = AnnouncementStore.firstSeen(app)
        val alive = onServer.mapTo(HashSet()) { it.id }
        return hist.map { item ->
            HistoryEntry(
                item = item,
                firstSeenAt = seen[item.id] ?: 0L,
                retired = item.id !in alive,
            )
        }
    }

    /** 解析原文并算出状态。**永不让异常逃出去**。 */
    private fun applyRaw(app: Context, raw: String, fetchFailed: Boolean, atMs: Long) {
        val (_, versionCode) = versionInfo(app)
        val today = runCatching { LocalDate.now().toString() }.getOrDefault("")
        val readIds = AnnouncementStore.readIds(app)

        when (val parsed = Announcement.parse(raw)) {
            is Announcement.Parse.Ok -> {
                val visible = Announcement.visible(
                    parsed.items, today, versionCode, Announcement.PLATFORM_PHONE,
                )
                // ⚗⚗ 把这次**已发布**的公告并进本地历史（2026-10-04）。
                //
                // ⚠ 传 `visible` 而不是 `parsed.items`：后者含 `show:false` 的条目，
                //   而那个标记的语义是「**内容保留但不发布**」——
                //   把它们也累积进去，用户会在历史里看到**从未发布过**的草稿。
                //
                // ⚠ 这一处的读-改-写**不需要** `ioLock`：三处调用都在 refresh 的
                //   `refreshMutex` 串行区内（2026-10-17 起是 Mutex，不是早期的
                //   `fetching.compareAndSet` —— 语义都是"同一时刻只有一轮刷新"）。
                //   ⇒ 这里是单线程读-改-写。这也是 [AnnouncementStore.saveRaw] 要显式加锁、
                //   而这里不用的原因。
                //   ⚠ 以后若把 `applyRaw` 挪到别处调用（脱离 refreshMutex），**必须**补 ioLock。
                AnnouncementStore.mergeHistory(app, visible, atMs)
                val history = historyEntries(app, parsed.items)
                val candidate = Announcement.popupCandidate(
                    items = parsed.items,
                    today = today,
                    versionCode = versionCode,
                    platform = Announcement.PLATFORM_PHONE,
                    nowMs = System.currentTimeMillis(),
                    lastPopupAt = AnnouncementStore.popupAt(app),
                    // 必须传真实时区偏移，否则日界按 UTC 划（东八区 = 每天早上 8 点换日），
                    // "隔一天"就会在早 8 点前后表现得不一样
                    zoneOffsetSeconds = java.util.TimeZone.getDefault()
                        .getOffset(System.currentTimeMillis()) / 1000,
                    readIds = readIds,
                    // 内容指纹：作者改过公告就无视冷却直接弹（见 Announcement.contentSignature）。
                    // 少了这个实参不会报错，只是"改了公告却弹不出来" —— 静默故障。
                    lastPopupSig = AnnouncementStore.popupSig(app),
                )
                _state.value = State(
                    items = visible,
                    // ⚠ 与公告互不相干：公告解析失败时也要带上更新信息
                    //   （`parseUpdate` 自己会逐字段挡掉坏数据）。
                    update = parsed.update,
                    history = history,
                    parseError = null,
                    fetchFailed = fetchFailed,
                    lastSuccessAt = atMs,
                    popup = candidate,
                    loading = false,
                    readIds = readIds,
                )
            }
            is Announcement.Parse.Bad -> {
                // 内容坏了：保留"连不上"的语义但把原因说清楚，公告页显示它
                //
                // ⚗ 历史照常带上：解析失败只是"这次的内容读不了"，
                //   本地已累积的公告**不该跟着一起消失** —— 否则作者写坏一次 JSON，
                //   用户就看不到任何公告了（而本地明明存着）。
                _state.value = State(
                    items = emptyList(),
                    history = historyEntries(app, emptyList()),
                    parseError = parsed.reason,
                    fetchFailed = fetchFailed,
                    lastSuccessAt = atMs,
                    loading = false,
                    readIds = readIds,
                )
            }
        }
    }

    /**
     * 弹过了：清掉候选并记下时间与**当刻的内容指纹**。
     *
     * **必须在弹窗显示的那一刻调用**，否则一次会话里可能弹第二次（重组、回到前台等
     * 都会重新 sweep 一遍候选）。记的是"弹出时刻"而不是"关闭时刻"。
     *
     * 指纹要跟时间戳**一起**写（[AnnouncementStore.markPoppedWithSig]）：它是下次判断
     * "作者改过没有"的基准，漏存会让这条公告的更新永远弹不出来（见该函数注释）。
     */
    suspend fun consumePopup(context: Context) {
        val item = _state.value.popup ?: return
        val app = context.applicationContext
        val sig = Announcement.contentSignature(item)
        withContext(Dispatchers.IO) {
            ioLock.withLock {
                AnnouncementStore.markPoppedWithSig(app, item.id, System.currentTimeMillis(), sig)
            }
        }
        _state.value = _state.value.copy(popup = null)
    }

    suspend fun markRead(context: Context, id: String) {
        val app = context.applicationContext
        withContext(Dispatchers.IO) { ioLock.withLock { AnnouncementStore.markRead(app, id) } }
        // 立刻反映到状态上：未读红点要跟着点击马上变，不能等下次刷新
        _state.value = _state.value.copy(readIds = _state.value.readIds + id)
    }

    /** 进入公告页时把当前可见的全部标为已读（用户已经看到列表了）。 */
    suspend fun markAllVisibleRead(context: Context) {
        val app = context.applicationContext
        val ids = _state.value.items.map { it.id }
        if (ids.isEmpty()) return
        withContext(Dispatchers.IO) {
            ioLock.withLock { AnnouncementStore.markAllRead(app, ids) }
        }
        _state.value = _state.value.copy(readIds = _state.value.readIds + ids)
    }

    fun unreadCount(context: Context): Int {
        val s = _state.value
        if (s.items.isEmpty()) return 0
        val read = AnnouncementStore.readIds(context.applicationContext)
        return s.items.count { it.id !in read }
    }

    // ⚠ 提为 internal：更新模块也要用它比「本地版本 vs 服务端版本」，复用同一份取版本逻辑。
    internal fun versionInfo(context: Context): Pair<String, Int> = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        (pi.versionName ?: "1.0") to pi.longVersionCode.toInt()
    }.getOrDefault("1.0" to 1)
}
