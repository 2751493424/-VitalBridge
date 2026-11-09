package com.gemini.healthbridge.phone

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.gemini.healthbridge.core.AnnouncementUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 应用内更新（2026-10-16）：从公告 JSON 的 `update` 块拿地址，下载、校验、调起系统安装器。
 *
 * ## 三个设计决定
 * 1. **校验不做完不许装**：先比字节数、再逐字节算 sha256，两项都与公告 JSON 里给的对不上
 *    ⇒ 直接删文件、返回失败。宁可不更新，也不能装一个来源不明的包
 *    （与项目里"报「装不上」先核验包"是同一条纪律）。
 * 2. **下载落在 `cacheDir/update/`**：cache 由系统回收，装完/失败都不用自己清理；
 *    ⚠ 且 `file_paths.xml` 只开放这一个子目录给 FileProvider。
 * 3. **安装必然跳系统确认页**：Android 8+ 强制要求用户点一次「允许安装未知应用」，
 *    这一步绕不过去，也不该绕过。
 */
object AppUpdater {

    /**
     * 单次下载的字节上限：128 MB（2026-11-03 加）。
     *
     * 手机包目前约 40 MB。`info.sizeBytes` 来自公告 JSON（不可信内容），
     * 给一个天文数字就能把 `cacheDir` 写满 —— 这道闸门兜住它。
     */
    private const val MAX_APK_BYTES = 128L * 1024 * 1024

    /** 下载/校验失败的分类。[detail] 是给人看的短句（可直接显示）。 */
    /**
     * ⚠ 必须是 [Exception] 子类：`download()` 里用它来中断流程
     *   （不是 Throwable 的话编译期就过不去 —— 这是上一步实际踩到的）。
     */
    sealed class Fail(val detail: String) : Exception(detail) {
        /** 没网、或连上了但拿不到内容。 */
        class Network(detail: String) : Fail(detail)

        /** 字节数或 sha256 对不上 —— 说明下载不完整或内容被换过。 */
        class Mismatch(detail: String) : Fail(detail)
    }

    /**
     * 下载并校验。[onProgress] 取值 0f~1f（按字节数算；服务端不给长度时只能给 0）。
     *
     * ⚠ 全程在 [Dispatchers.IO]；文件先写 `.tmp`，**校验通过才改名**成最终文件 ——
     *   这样即使失败也只会留下一个 .tmp，不会被误当成可安装的包。
     */
    suspend fun download(
        info: AnnouncementUpdate,
        onProgress: (Float) -> Unit = {},
    ): Result<File> = withContext(Dispatchers.IO) {
        val dir = File(context().cacheDir, "update").apply { mkdirs() }
        val out = File(dir, "vitalbridge-%d.apk".format(info.versionCode))
        val tmp = File(dir, out.name + ".tmp")
        runCatching {
            // ⚠ 防御纵深（2026-11-03）：`Announcement.parseUpdate` 已经要求 https，
            //   这里再判一次 —— 下载器不该依赖"上游校验过了"，将来若从别处构造
            //   AnnouncementUpdate（备份、调试入口），这一处仍然挡得住明文下载。
            if (!info.url.startsWith("https://", ignoreCase = true)) {
                throw Fail.Network("下载地址不是 https")
            }
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(info.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                    requestMethod = "GET"
                    // ⚠ 不要 gzip：要按字节数校验，压缩传输会拿到"对不上"的流
                    setRequestProperty("Accept-Encoding", "identity")
                }
                conn.connect()
                val code = conn.responseCode
                if (code !in 200..299) {
                    throw Fail.Network("HTTP %d".format(code))
                }
                val digest = MessageDigest.getInstance("SHA-256")
                var total = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(tmp).use { fos ->
                        val buf = ByteArray(64 * 1024)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > MAX_APK_BYTES) {
                                throw Fail.Mismatch("包体超过 $MAX_APK_BYTES 字节")
                            }
                            fos.write(buf, 0, n)
                            digest.update(buf, 0, n)
                            if (info.sizeBytes > 0) onProgress(
                                (total.toFloat() / info.sizeBytes.toFloat()).coerceIn(0f, 1f)
                            )
                        }
                        fos.flush()
                    }
                }
                if (total != info.sizeBytes) {
                    tmp.delete()
                    throw Fail.Mismatch("%d/%d 字节".format(total, info.sizeBytes))
                }
                val hex = digest.digest().joinToString("") { "%02x".format(it) }
                if (hex != info.sha256.lowercase()) {
                    tmp.delete()
                    throw Fail.Mismatch("sha256 不符")
                }
                // ⚠ 签名必须与**本机正在运行的 App** 一致（2026-11-03 加）。
                //   系统安装器也会挡（不同签名的包无法覆盖安装），但那时用户看到的是
                //   "解析包时出现问题"，根本不知道是签名不对；这里提前判、给准确原因。
                val expected = selfSignerDigest()
                if (expected != null) {
                    val got = apkSignerDigest(tmp)
                    if (got == null || got != expected) {
                        tmp.delete()
                        throw Fail.Mismatch("安装包签名与本机应用不一致")
                    }
                }
                if (out.exists()) out.delete()
                if (!tmp.renameTo(out)) {
                    tmp.delete()
                    throw Fail.Network("落盘失败")
                }
                // ⚠ 2026-10-21 用户要求：手机版安装包统一落到
                //   `Download/VitalBridge/phone-update/`，且**只保留最新一版**（旧的删掉）。
                runCatching { exportToDownloads(out) }
                out
            } finally {
                conn?.disconnect()
            }
        }.onFailure { tmp.delete() }
    }

    /** 手机版安装包目录（用户要求的位置）。 */
    private const val PUBLIC_DIR = "VitalBridge/phone-update"

    /** 另存一份到 `Download/VitalBridge/phone-update/`，并清掉旧版本。 */
    private fun exportToDownloads(src: File) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val ctx = context()
        val resolver = ctx.contentResolver
        val coll = android.provider.MediaStore.Downloads.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, src.name)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download/$PUBLIC_DIR")
            put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(coll, values) ?: return
        resolver.openOutputStream(uri)?.use { os -> src.inputStream().use { it.copyTo(os) } }
        values.clear()
        values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
        resolver.update(uri, values, null, null)

        prunePublicDir(keep = src.name)
    }

    /**
     * 清掉 `Download/VitalBridge/phone-update/` 里除 [keep] 以外的 `vitalbridge*.apk`。
     *
     * ⚠⚠ 2026-10-28 修：原来条件是 `RELATIVE_PATH = 'Download/VitalBridge/phone-update'`，
     *   而 MediaStore 实际存的是**带结尾斜杠**的 `…/phone-update/` ⇒ **一条都匹配不上**，
     *   所以旧包永远不会被清（用户截图里 197/209/210/212 四份并存就是这么来的）。
     *   手表端 `WatchOtaClient.pruneOldDownloads` 犯过同一个错、早一步修好了，这处是漏网的副本。
     *   ⇒ 一律用 `LIKE '…/phone-update%'`，斜杠与否都能命中。
     *
     * @param keep 要保留的文件名（null = 全清）
     */
    fun prunePublicDir(keep: String?) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val ctx = context()
        val resolver = ctx.contentResolver
        val coll = android.provider.MediaStore.Downloads.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val proj = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
        )
        val sel = "${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        runCatching {
            resolver.query(coll, proj, sel, arrayOf("Download/$PUBLIC_DIR%"), null)?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    if (keep != null && name == keep) continue
                    if (!name.startsWith("vitalbridge")) continue
                    runCatching {
                        resolver.delete(
                            android.content.ContentUris.withAppendedId(coll, c.getLong(0)), null, null,
                        )
                    }
                }
            }
        }
    }

    /**
     * 把下载好的 APK 交给系统安装器。
     *
     * ⚠ 必须走 FileProvider 的 `content://`：Android 7+ 直接给 `file://` 会抛
     *   `FileUriExposedException`（这是崩溃，不是警告）。
     */
    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context, context.packageName + ".fileprovider", file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** 本机已安装应用的签名摘要（更新包的签名必须与它一致）。 */
    private fun selfSignerDigest(): String? = runCatching {
        val ctx = context()
        val modern = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        val flags = if (modern) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else 0
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, flags)
        val bytes: ByteArray? = if (modern) {
            pi.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pi.signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { digestHex(it) }
    }.getOrNull()

    /** 读一个 APK 文件的签名摘要（不安装）。 */
    private fun apkSignerDigest(f: File): String? = runCatching {
        val ctx = context()
        val modern = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P
        val flags = if (modern) android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES else 0
        val pi = ctx.packageManager.getPackageArchiveInfo(f.absolutePath, flags) ?: return null
        val bytes: ByteArray? = if (modern) {
            pi.signingInfo?.apkContentsSigners?.firstOrNull()?.toByteArray()
        } else {
            @Suppress("DEPRECATION")
            pi.signatures?.firstOrNull()?.toByteArray()
        }
        bytes?.let { digestHex(it) }
    }.getOrNull()

    private fun digestHex(b: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    /** 供诊断用：算一个已下载文件的 sha256（与 [AnnouncementUpdate.sha256] 同算法）。 */
    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * 进程内上下文。更新只在 MainActivity 活着的时候发起，所以这里不需要注册 Application。
     * ⚠ 这是刻意的取舍：为一个 40MB 的下载引入 Application 级上下文不值得。
     */
    @Volatile
    private var appCtx: Context? = null

    fun attach(context: Context) {
        appCtx = context.applicationContext
    }

    private fun context(): Context =
        appCtx ?: error("AppUpdater.attach(context) 还没调用")
}

/**
 * 更新相关的偏好（2026-10-16）。
 *
 * ⚠ 文件名用新品牌 `vitalbridge_update`：**不要**复用旧的 `vitaltrace_*` 文件
 *   （那批是历史数据，混进去只会让备份/迁移更乱）。
 */
object AppUpdatePrefs {
    private const val FILE = "vitalbridge_update"

    /** 自动检查：**默认开**（用户 2026-10-16 要求"默认打开"）。 */
    fun autoCheck(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("auto", true)

    /** 手表端「自动检查更新」：**默认开**（2026-10-21 用户要求）。 */
    fun watchAuto(ctx: Context): Boolean =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("watchAuto", true)

    fun setWatchAuto(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("watchAuto", on).apply()
    }

    /** 手机最近一次从手表 HELLO 里读到的手表 versionCode（0 = 还不知道）。 */
    fun watchVersion(ctx: Context): Long =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("watchVer", 0L)

    fun setWatchVersion(ctx: Context, v: Long) {
        if (v <= 0) return
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putLong("watchVer", v).apply()
    }

    fun setAutoCheck(ctx: Context, on: Boolean) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putBoolean("auto", on).apply()
    }

    /**
     * 同一个版本**一天最多提醒一次**（否则每次打开都弹，非常烦）。
     * @return true = 可以弹
     */
    fun shouldNotify(ctx: Context, versionCode: Int, nowMs: Long): Boolean {
        val p = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val v = p.getInt("notifiedVer", 0)
        val at = p.getLong("notifiedAt", 0L)
        if (v == versionCode && nowMs - at < 86_400_000L) return false
        return true
    }

    fun markNotified(ctx: Context, versionCode: Int, nowMs: Long) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit()
            .putInt("notifiedVer", versionCode)
            .putLong("notifiedAt", nowMs)
            .apply()
    }

    fun lastCheckAt(ctx: Context): Long =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getLong("lastCheck", 0L)

    fun setLastCheckAt(ctx: Context, ms: Long) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putLong("lastCheck", ms).apply()
    }
}
