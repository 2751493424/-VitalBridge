package com.gemini.healthbridge.phone

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 手机端：把**手表安装包**推给手表（2026-10-20）。
 *
 * ## 为什么不做"手表自己去下"
 * 手表没有网络（Wear 上要单独授权、且很多表没有 WiFi/蜂窝）⇒ 由手机从 CF 拉包，
 * 再经 DataLayer 通道推过去。手表只负责"收 → 校验 → 拉起安装"。
 *
 * ## 通道选择（只跑一套）
 * 优先 **DataLayer Channel**（GMS 可用时）；
 * 没有 GMS / 没有已连接节点 ⇒ 返回失败并提示（RFCOMM 兜底为下一步）。
 *
 * ## 安装确认
 * 普通应用无法静默安装，推完仍需要用户在手表上点一次「安装」。
 */
object WatchOta {

    /**
     * ⚠ 不要用裸 `Dispatchers.IO`：默认上限 64 线程，而蓝牙 connect/socket 读会长时间占用线程。
     *   这里限制成 2，保证不会因为推送把线程池打爆（真机上 pthread_create 有硬上限）。
     */
    // ⚠ 这个项目的协程版本没有 `limitedParallelism` ⇒ 用一个固定 2 线程的调度器代替
    private val IO_LIMITED: kotlinx.coroutines.CoroutineDispatcher =
        java.util.concurrent.Executors.newFixedThreadPool(2).asCoroutineDispatcher()

    private const val TAG = "WatchOta"

    /** 与公告同一个 R2 桶；手表包放在 `apk/` 下。 */
    private const val BASE = "https://REPLACE-ME.invalid/"

    /**
     * 信任的下载主机（2026-11-03 加）。清单里的 `url` **必须**是这些主机之一。
     *
     * ## 为什么需要白名单
     * `watch.json` 是**远端可改的内容**（R2 桶的写权限、CDN 缓存被污染都算）。
     * 此前 `download()` 直接 `URL(info.url)`：谁能改这份清单，谁就能让 App
     * 去下载任意地址的 APK 并交给手表安装器 —— 而 `sha256` 也在同一份清单里，
     * 一起改掉，校验就等于不存在。
     * ⇒ 现在：scheme 必须 https、host 必须命中这里，否则整份清单作废。
     *
     * ⚠ 换托管方时**只改这两行**（BASE 与 HOSTS 要一起改）。
     */
    private val TRUSTED_HOSTS = setOf("REPLACE-ME.invalid")

    /**
     * 单个包的字节上限：64 MB。
     *
     * 手表包目前 4.4 MB。封顶挡的是"清单给了一个超大文件"导致下载把
     * `cacheDir` 写满（分区存储下用户还得自己去清）。
     */
    private const val MAX_APK_BYTES = 64L * 1024 * 1024

    /** 自带包信息的缓存键（避免每次冷启动都解压 + 哈希 4.6 MB）。 */
    private const val SP = "watch_ota"
    private const val K_MTIME = "bundled_mtime"
    private const val K_LEN = "bundled_len"
    private const val K_CODE = "bundled_code"
    private const val K_SHA = "bundled_sha"

    private const val INDEX = "apk/watch.json"
    private const val META_PATH = "/vitalbridge/ota/meta"
    private const val CHANNEL_PATH = "/vitalbridge/ota/apk"
    private const val PUBLIC_DIR = "VitalBridge/watch-update"

    /** DataLayer 阶段最多等这么久，超时立刻转蓝牙串口（本机 Wear 通道常常不回调）。 */
    private const val DATA_LAYER_TIMEOUT_MS = 8_000L

    /**
     * ⚠⚠ 单飞锁（2026-10-20 修 `OutOfMemoryError: pthread_create`）：
     * 用户连点「推送手表更新」会一层层叠加协程，而每个协程都阻塞在 `BluetoothSocket.connect()`
     * 上（单次最长十几秒）⇒ 线程数暴涨，最终 pthread_create 失败直接崩。
     * 现在只允许一个推送任务在跑，其余直接返回"正在推送中"。
     */
    private val busy = java.util.concurrent.atomic.AtomicBoolean(false)
    @Volatile private var busySince = 0L

    /** 超过这个时长还占着锁，就认为上一轮已经卡死（防止界面永久"正在推送中"）。 */
    private const val LOCK_STALE_MS = 180_000L

    data class Info(
        val versionCode: Int,
        val sizeBytes: Long,
        val sha256: String,
        val url: String,
        val fileName: String,
        /** 这一版更新了什么（`watch.json` 的 `notes`）；没有就空串。 */
        val notes: String = "",
        /**
         * `true` = 这个包是**手机自带**的（`assets/apk/watch.apk`），不是从网上下来的。
         *
         * ⚠⚠ 2026-10-27 加：线上 `apk/watch.json` 一直停在 **188**，而 188 这版手表端在
         *   Android 14+ 上**一启动就闪退**（`OtaWindowService` 以前台服务身份 `startForeground`，
         *   此时 `BLUETOOTH_CONNECT` 还没授予 ⇒ `SecurityException`）。
         *   手机端"安装手表端脉迹"于是把**同一个会崩的 188** 又装了一遍，用户看到"安装完成"
         *   但手表照旧闪退。⇒ 改成**优先用手机自带的包**：版本确定、离线可用、
         *   不会因为线上清单没更新而装回旧版。
         */
        val bundled: Boolean = false,
    )

    /** 供界面显示的分类错误。 */
    sealed class Fail(val detail: String) : Exception(detail) {
        class Network(detail: String) : Fail(detail)
        class Mismatch(detail: String) : Fail(detail)
        class NoWatch(detail: String) : Fail(detail)

        /** 手表已经是这个版本（或更新）⇒ **不该传包**，也不是错误。 */
        class UpToDate(detail: String) : Fail(detail)
    }

    // ───────────────────────── 1. 版本清单 ─────────────────────────

    /**
     * 读 `apk/watch.json`。字段容错：
     * `{ "version": 166, "size": 4684606, "sha256": "...", "file": "vitalbridge-watch-v166.apk" }`
     */
    suspend fun fetchInfo(): Result<Info> = withContext(Dispatchers.IO) {
        runCatching {
            val text = httpGet(BASE + INDEX, 15_000)
            val o = JSONObject(text)
            val code = o.optInt("version", o.optInt("versionCode", 0))
            if (code <= 0) throw Fail.Network("清单里没有版本号")
            // ── 以下每一条都是"不可信输入"的闸门（2026-11-03）──
            // 清单挂在公开可改的静态存储上，所以它给的每一个字段都不能直接拿去用：
            // 一旦放开，改清单的人就能让 App 下载任意地址的任意包并推给手表。
            val size = o.optLong("size", o.optLong("sizeBytes", 0L))
            if (size <= 0L) throw Fail.Mismatch("清单里没有文件大小")
            if (size > MAX_APK_BYTES) throw Fail.Mismatch("清单里的包超过 $MAX_APK_BYTES 字节")
            val sha = o.optString("sha256", "").trim().lowercase()
            // ⚠ 没有 sha256 就**不能用这份清单**：宁可不提示更新，也不能装一个没法校验的包
            if (!isSha256(sha)) throw Fail.Mismatch("清单里的 sha256 不合法")
            val rawName = o.optString("file", "").trim()
            val file = rawName.ifEmpty { "vitalbridge-watch-v$code.apk" }
            if (!isSafeFileName(file)) throw Fail.Mismatch("清单里的文件名不合法")
            val url = o.optString("url", BASE + "apk/" + file).trim()
            if (!isTrustedUrl(url)) throw Fail.Mismatch("清单里的下载地址不在信任列表内")
            val notes = o.optString("notes", "")
            Info(
                versionCode = code, sizeBytes = size, sha256 = sha,
                url = url, fileName = file, notes = notes,
            )
        }
    }

    /** 小写十六进制、64 位。 */
    private fun isSha256(s: String): Boolean =
        s.length == 64 && s.all { it in "0123456789abcdef" }

    /**
     * 文件名白名单：只允许 `[A-Za-z0-9._-]` 且必须以 `.apk` 结尾、不含 `..`。
     *
     * ⚠ 这是**路径穿越**闸门：`file` 会被 `File(dir, file)` 拼成落盘路径，
     *   传 `../../files/xxx` 就能写到 `cacheDir` 之外（并让 `delete()` 删到别处）。
     */
    private fun isSafeFileName(name: String): Boolean {
        if (name.length !in 5..128) return false
        if (name.contains("..")) return false
        if (!name.endsWith(".apk", ignoreCase = true)) return false
        return name.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }
    }

    /**
     * 下载地址必须是 **https** 且 host 命中 [TRUSTED_HOSTS]。
     *
     * ⚠ 只判 `startsWith("https://")` 不够：`https://attacker.example/?x=trusted.host` 也能过；
     *   这里解析出 host 再比对。解析失败（非法 URL）一律判不可信。
     */
    private fun isTrustedUrl(u: String): Boolean = runCatching {
        val uri = java.net.URI(u)
        val host = uri.host?.lowercase()
        uri.scheme.equals("https", ignoreCase = true) && host != null && host in TRUSTED_HOSTS
    }.getOrDefault(false)

    // ───────────────────── 1.5 手机自带的手表包（优先） ─────────────────────

    /** 手机自带的手表安装包（打包时从 `wear/build/outputs/apk/release` 拷进来）。 */
    private const val BUNDLED_ASSET = "apk/watch.apk"

    /**
     * 现在该装哪一版：**取「手机自带包」与「线上清单」里版本号更大的那个**。
     *
     * ⚠⚠ 2026-10-28 改成"取较大者"（原来是"自带包无条件优先"）：
     *   线上清单确实不可信（曾长期停在 188，而 188 一启动就闪退，所以当年要自带优先），
     *   但反过来也出过问题 —— 自带包停在 192、线上已发 193 时，这里仍然返回 192，
     *   于是「检查手表更新」永远显示"已是最新 v192"（用户 2026-10-28 报的就是这个）。
     *   判据：**两边都读、谁大用谁**；读不到线上就退回自带，两个都读不到才报错。
     */
    suspend fun latestInfo(ctx: Context): Result<Info> = withContext(Dispatchers.IO) {
        val bundled = bundledInfo(ctx)
        val remoteRes = fetchInfo()
        val remote = remoteRes.getOrNull()
        when {
            bundled == null && remote == null -> remoteRes          // 把失败原因原样带出去
            remote == null -> Result.success(bundled!!)
            bundled == null -> Result.success(remote)
            remote.versionCode >= bundled.versionCode -> Result.success(remote)
            else -> Result.success(bundled)
        }
    }

    /** 读自带包的信息（`versionCode` / 大小 / sha256）。没有自带包就 null。 */
    fun bundledInfo(ctx: Context): Info? {
        val f = extractBundled(ctx) ?: return null
        val mtime = f.lastModified()
        val len = f.length()
        // ⚠ 缓存（2026-11-03）：这个函数在**每次冷启动的自动检查**里都会被调到，
        //   而算一次 sha256 要读满 4.6 MB。用 (mtime, len) 作为缓存键 ——
        //   包变了（换版本后重新解压）这两个值一定会变，不会读到过期结果。
        val sp = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
        if (sp.getLong(K_MTIME, -1L) == mtime && sp.getLong(K_LEN, -1L) == len) {
            val code = sp.getInt(K_CODE, 0)
            val sha = sp.getString(K_SHA, "").orEmpty()
            if (code > 0 && isSha256(sha)) {
                return Info(
                    versionCode = code, sizeBytes = len, sha256 = sha,
                    url = "", fileName = "vitalbridge-watch-v$code.apk",
                    notes = "", bundled = true,
                )
            }
        }
        val code = apkVersionCode(ctx, f) ?: return null
        val sha = runCatching { sha256Hex(f) }.getOrDefault("")
        if (isSha256(sha)) {
            sp.edit().putLong(K_MTIME, mtime).putLong(K_LEN, len)
                .putInt(K_CODE, code).putString(K_SHA, sha).apply()
        }
        return Info(
            versionCode = code,
            sizeBytes = f.length(),
            sha256 = sha,
            url = "",
            fileName = "vitalbridge-watch-v$code.apk",
            notes = "",
            bundled = true,
        )
    }

    /** 把自带的 apk 解到缓存（长度一样就复用）。 */
    private fun extractBundled(ctx: Context): File? = runCatching {
        val dir = File(ctx.cacheDir, "watch-apk").apply { mkdirs() }
        val out = File(dir, "watch.apk")
        // ⚠ `openFd()` 只对**未压缩**的 asset 有效；万一被压了会抛异常 ⇒ 拿不到长度就走拷贝。
        //   （构建里已 `noCompress += "apk"`，这里的兜底只是为了别因为打包方式变化就整条挂掉。）
        val expect = runCatching { ctx.assets.openFd(BUNDLED_ASSET).use { it.length } }.getOrDefault(-1L)
        if (expect > 0 && out.isFile && out.length() == expect) return out
        ctx.assets.open(BUNDLED_ASSET).use { input ->
            FileOutputStream(out).use { o -> input.copyTo(o, 64 * 1024) }
        }
        out.takeIf { it.length() > 0 }
    }.getOrNull()

    /** 从 apk 文件读 versionCode（不需要安装）。 */
    fun apkVersionCode(ctx: Context, f: File): Int? = runCatching {
        ctx.packageManager.getPackageArchiveInfo(f.absolutePath, 0)?.longVersionCode?.toInt()
    }.getOrNull()

    // ───────────────────────── 2. 下载 + 校验 ─────────────────────────

    /**
     * 下载到缓存（先 `.part`，校验通过才改名），并**另存一份到公共目录**
     * `Download/VitalBridge/watch-update/`（用户在文件管理器里能看到）。
     */
    suspend fun download(
        ctx: Context,
        info: Info,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            // 自带包：解出来就能装，不走网络、也不校验 sha256（它就在安装包里，来源可信）
            if (info.bundled) {
                val f = extractBundled(ctx) ?: throw Fail.Network("自带的安装包读不出来")
                onProgress(1f)
                return@withContext Result.success(f)
            }
            val dir = File(ctx.cacheDir, "watch-ota").apply { mkdirs() }
            // ⚠ 落盘路径必须落在 dir 之内（见 isSafeFileName）：`File(dir, name)` 本身
            //   不拦 `../`，一个来自远端清单的文件名就能写到缓存目录之外。
            val out = safeJoin(dir, info.fileName)
                ?: throw Fail.Mismatch("文件名越出缓存目录")
            val tmp = File(dir, info.fileName + ".part")

            // ⚠ 2026-10-20 用户反馈两点：
            //   ① 已经下载过还每次重下 ⇒ 命中缓存直接用；
            //   ② 用户能在「下载/VitalBridge/watch-update」里看到包 ⇒ **那也要算"已有"**。
            // 顺序：公共下载目录 → 应用缓存 → 都没有才联网。
            locateLocal(ctx, info)?.let { hit ->
                onProgress(1f)
                // 命中缓存时也顺手清旧包：用户可能很久不再下载，旧包就一直躺着
                runCatching { pruneInDownloads(ctx, keepName = info.fileName) }
                return@withContext Result.success(hit)
            }
            if (out.isFile) out.delete()   // 残留但校验不过 ⇒ 清掉重下
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    // ⚠ 不要 gzip：要按字节数 + sha 校验，压缩流会"对不上"
                    setRequestProperty("Accept-Encoding", "identity")
                }
                conn.connect()
                if (conn.responseCode !in 200..299) throw Fail.Network("HTTP ${conn.responseCode}")
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            // ⚠ 超过上限立刻中止，别等磁盘写满（清单声称的大小不可信）
                            if (total > MAX_APK_BYTES) {
                                throw Fail.Mismatch("包体超过 $MAX_APK_BYTES 字节")
                            }
                            fos.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            if (info.sizeBytes > 0) {
                                onProgress((total.toFloat() / info.sizeBytes).coerceIn(0f, 1f))
                            }
                        }
                        fos.flush()
                    }
                }
                if (info.sizeBytes > 0 && total != info.sizeBytes) {
                    tmp.delete(); throw Fail.Mismatch("$total/${info.sizeBytes} 字节")
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (info.sha256.isNotEmpty() && hex != info.sha256) {
                    tmp.delete(); throw Fail.Mismatch("sha256 不符")
                }
                // ⚠⚠ 签名校验（2026-11-03）：sha256 只能证明"字节与清单说的一致"，
                //   而清单本身是可改的 —— 清单和包一起被换掉时，那道校验形同虚设。
                //   这里用手机自带的官方手表包作**信任锚**：签名证书必须一致。
                //   拿不到锚（构建没塞包）就跳过，此时仍受 sha256 与 https 白名单保护。
                val expected = bundledSignerDigest(ctx)
                if (expected != null) {
                    val got = signerDigest(ctx, tmp)
                    if (got == null || got != expected) {
                        tmp.delete()
                        throw Fail.Mismatch("安装包签名与官方包不一致")
                    }
                }
                if (out.exists()) out.delete()
                if (!tmp.renameTo(out)) { tmp.delete(); throw Fail.Network("落盘失败") }
                // 另存公共目录（失败不影响推送：推送用的是缓存文件）
                runCatching { exportToDownloads(ctx, out, info.fileName) }
                out
            } finally {
                conn?.disconnect()
            }
        }
    }

    /**
     * 找"已经下过的同一版本包"。
     *
     * 两个位置都认：
     *  1) `Download/VitalBridge/watch-update/<file>`（用户能在文件管理器里看到的那份，MediaStore 查询）；
     *  2) 应用缓存 `cacheDir/watch-ota/<file>`。
     * 只有**字节数 + sha256 都对得上**才算命中 —— 宁可重下，也不能装一个来路不明的包。
     */
    private fun locateLocal(ctx: Context, info: Info): File? {
        // ① 公共下载目录
        val public = findInDownloads(ctx, info.fileName)
        if (public != null && (info.sizeBytes <= 0 || public.length() == info.sizeBytes)) {
            if (info.sha256.isEmpty() || sha256Hex(public) == info.sha256) return public
        }
        // ② 应用缓存
        val cached = File(File(ctx.cacheDir, "watch-ota"), info.fileName)
        if (cached.isFile && (info.sizeBytes <= 0 || cached.length() == info.sizeBytes)) {
            if (info.sha256.isEmpty() || sha256Hex(cached) == info.sha256) return cached
        }
        return null
    }

    /** 在 MediaStore 的 Downloads 里按文件名找 `Download/VitalBridge/watch-update/` 下的那份。 */
    private fun findInDownloads(ctx: Context, name: String): File? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val f = File(
                File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), PUBLIC_DIR),
                name,
            )
            return if (f.isFile) f else null
        }
        val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val proj = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.SIZE)
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val args = arrayOf(name, Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIR)
        ctx.contentResolver.query(coll, proj, sel, args, null)?.use { c ->
            if (c.moveToFirst()) {
                val id = c.getLong(0)
                val uri = android.content.ContentUris.withAppendedId(coll, id)
                // MediaStore 的条目直接读 InputStream 校验；推送时也支持 File，
                // 所以这里落一个缓存副本，保证后续 push 能按 File 读。
                return runCatching {
                    val dst = File(File(ctx.cacheDir, "watch-ota").apply { mkdirs() }, name)
                    ctx.contentResolver.openInputStream(uri)?.use { input ->
                        dst.outputStream().use { out -> input.copyTo(out) }
                    }
                    dst
                }.getOrNull()
            }
        }
        return null
    }

    /** 复制一份到 `Download/VitalBridge/watch-update/`（API 29+ 用 MediaStore，无需权限）。 */    private fun exportToDownloads(ctx: Context, src: File, name: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                PUBLIC_DIR,
            ).apply { mkdirs() }
            src.copyTo(File(dir, name), overwrite = true)
            // 同一套"只留最新"策略
            runCatching { dir.listFiles()?.forEach { f -> if (f.name != name) f.delete() } }
            return
        }
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/" + PUBLIC_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri: Uri = resolver.insert(coll, values) ?: return
        resolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        // ⚠⚠ 2026-10-22 用户实测：手机端 `Download/VitalBridge/watch-update/` 里堆了 6 个旧包
        //   （v180…v187）—— 因为这里**原来根本没有清理逻辑**（只给手表端加了）。
        pruneInDownloads(ctx, keepName = name)
    }

    /**
     * 手机端 `Download/VitalBridge/watch-update/` **只保留 [keepName]**（2026-10-22）。
     *
     * ⚠ 查询**不能**用 `RELATIVE_PATH = 'Download/VitalBridge/watch-update'` 精确匹配：
     *   MediaStore 里存的是**带结尾斜杠**的 `…/watch-update/`，一条都匹配不上（手表端踩过同一个坑）。
     *   这里按 `DISPLAY_NAME LIKE 'vitalbridge-watch%'` 查，再用 `path.contains("VitalBridge")` 粗筛。
     */
    fun pruneInDownloads(ctx: Context, keepName: String?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = ctx.contentResolver
        val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("vitalbridge-watch%")
        val doomed = mutableListOf<Uri>()
        resolver.query(coll, proj, sel, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val dn = c.getString(1) ?: continue
                val path = c.getString(2) ?: ""
                if (!path.contains("VitalBridge", ignoreCase = true)) continue
                val isPart = dn.endsWith(".part", ignoreCase = true)
                if (keepName != null && dn == keepName && !isPart) continue
                doomed += android.content.ContentUris.withAppendedId(coll, id)
            }
        }
        doomed.forEach { u -> runCatching { resolver.delete(u, null, null) } }
        Log.i(TAG, "pruneInDownloads: keep=$keepName, removed=${doomed.size}")
    }


    /**
     * 启动时的一次性清理：**只留版本号最大的那个包**（2026-10-22）。
     *
     * 用户设备上已经攒了 6 个（v180…v187），光靠"下次下载时清理"要等很久；
     * 这里在启动时扫一遍，按文件名里的 `v<code>` 比大小，只留最大的一个。
     */
    fun pruneKeepingNewest(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val resolver = ctx.contentResolver
        val coll = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val proj = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val sel = "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        data class Cand(val uri: Uri, val name: String, val ver: Long)
        val cands = mutableListOf<Cand>()
        resolver.query(coll, proj, sel, arrayOf("vitalbridge-watch%"), null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val dn = c.getString(1) ?: continue
                val path = c.getString(2) ?: ""
                if (!path.contains("VitalBridge", ignoreCase = true)) continue
                if (dn.endsWith(".part", ignoreCase = true)) continue
                val ver = Regex("v(\\d+)").find(dn)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                cands += Cand(android.content.ContentUris.withAppendedId(coll, id), dn, ver)
            }
        }
        if (cands.size <= 1) return
        val keep = cands.maxByOrNull { it.ver } ?: return
        var removed = 0
        cands.filter { it.name != keep.name }.forEach {
            runCatching { resolver.delete(it.uri, null, null) }.onSuccess { removed++ }
        }
        Log.i(TAG, "pruneKeepingNewest: keep=${keep.name}, removed=$removed")
    }

    // ⚠ 2026-10-26 **「推包」整段已删**（`push` / `pushLatest` / `pushWithFallback`）：
    //   蓝牙串口推包与 DataLayer 通道都要求"手表上已经有脉迹且在监听"，
    //   不但慢且不稳，还救不了"表是空的"这一首次安装场景。
    //   ⇒ 现在：**首次安装走「安装」页的无线 ADB**，之后每次更新靠表自己 HTTP 下载
    //   （`watch/WatchOtaClient.updateFromWifi`）。本文件只保留"查清单 / 下载 / 清理旧包"。

    /** 一次 GET（清单只有几百字节，够用）。 */
    private fun httpGet(url: String, timeoutMs: Int): String {
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            if (conn.responseCode !in 200..299) throw Fail.Network("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 把 [name] 拼到 [dir] 下，并确认结果**仍在 dir 之内**；越界返回 null。
     *
     * ⚠ `File(dir, "../x")` 是合法的 `File`，`canonicalPath` 会把它解析到父目录 ——
     *   所以必须比对解析后的路径，而不是只检查字符串里有没有 `..`。
     */
    private fun safeJoin(dir: File, name: String): File? = runCatching {
        val f = File(dir, name)
        val base = dir.canonicalPath + File.separator
        if (f.canonicalPath.startsWith(base)) f else null
    }.getOrNull()

    /** 官方包的签名摘要缓存（一次会话内不变）。 */
    @Volatile private var cachedBundledSigner: String? = null

    /** 信任锚：手机自带手表包的签名摘要。拿不到（没塞包/解析失败）就是 null。 */
    private fun bundledSignerDigest(ctx: Context): String? {
        cachedBundledSigner?.let { return it }
        val f = extractBundled(ctx) ?: return null
        val d = signerDigest(ctx, f) ?: return null
        cachedBundledSigner = d
        return d
    }

    /**
     * 读 APK 签名证书的 SHA-256（十六进制小写）。读不到返回 null。
     *
     * ⚠ 用 `getPackageArchiveInfo` 而不是解压：它只读清单与签名块，不装包。
     * ⚠ API 28 起 `signatures` 被 `signingInfo` 取代，两个分支都要留。
     */
    private fun signerDigest(ctx: Context, f: File): String? = runCatching {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            0
        }
        val pi = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, flags)
            ?: return null
        val bytes: ByteArray? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pi.signatures?.firstOrNull()?.toByteArray()
        }
        val b = bytes ?: return null
        MessageDigest.getInstance("SHA-256").digest(b)
            .joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun sha256Hex(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                d.update(buf, 0, n)
            }
        }
        return d.digest().joinToString("") { "%02x".format(it) }
    }

    /** Google Play Services 的 Task → suspend（不额外引依赖）。 */
    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }
}
