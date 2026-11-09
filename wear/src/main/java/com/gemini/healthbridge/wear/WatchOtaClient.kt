package com.gemini.healthbridge.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/**
 * 手表端 OTA 接收器（2026-10-20）。
 *
 * ## 为什么是"通道"而不是"消息"
 * DataLayer 的单条消息有大小上限（约 100KB），而手表包装好接近 5MB ⇒ 必须用
 * [ChannelClient] 的字节流。元数据（版本号 / 字节数 / sha256）走 [MessageClient]，
 * 只有几百字节，正好。
 *
 * ## 一次只跑一套通道
 * [start] 先探测 `connectedNodes`：
 *  - 有节点（GMS 可用）⇒ 注册 DataLayer 监听，**不碰 RFCOMM**；
 *  - 没有节点 ⇒ 释放 DataLayer 监听，交给 RFCOMM 兜底（见 [stopAll] 的说明）。
 * 现状：RFCOMM 兜底尚未实现，此分支只记录原因，不会占资源。
 *
 * ## 安装必然要点一次确认
 * 普通应用（非系统签名）无法静默安装。这里只做到"自动下载 + 校验 + 拉起安装页"。
 */
object WatchOtaClient {

    private const val TAG = "WatchOta"

    /** 最近一次安装动作的结果（给界面/日志看，不参与逻辑）。 */
    @Volatile
    var lastStatus: String = "未开始"
        private set

    const val META_PATH = "/vitalbridge/ota/meta"
    const val CHANNEL_PATH = "/vitalbridge/ota/apk"

    // ⚠⚠ 2026-10-26 **手表端"收包"整块已删**（DataLayer 的 Message/Channel 监听 + RFCOMM 串口 +
    //   `accept` 循环 + VBO* 协议 + `stopAll`）。原因：手机端不再推包了 ——
    //   推包要求"表上已经有脉迹且在监听"，既救不了首次安装（那时没人 accept），
    //   还要靠 `OTA_WINDOW` 帧唤醒监听窗口，链条长、失败点极多。
    //   ⇒ 现在手表只做两件事：① 用 `updateFromWifi()` 自己下载并安装；
    //     ② 跑安装（`install` / `saveToDownloads`）。首次安装由手机端「安装」页走无线 ADB 完成。

    private fun saveToDownloads(ctx: Context, src: File): android.net.Uri? {
        val rel = "Download/VitalBridge/update"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val resolver = ctx.contentResolver
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, src.name)
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/vnd.android.package-archive")
                put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, rel)
                put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val coll = android.provider.MediaStore.Downloads.getContentUri(
                android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
            )
            val uri = resolver.insert(coll, values) ?: return null
            resolver.openOutputStream(uri)?.use { os2 -> src.inputStream().use { it.copyTo(os2) } }
            values.clear()
            values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            // ⚠ 2026-10-21 用户指出"会不会存两份"：会。
            //   原来这里为了给安装器一个 File，又把 Download 里的读回私有目录 —— 等于两份 4.7MB。
            //   现在**不回抄**：直接拿 Download 的 content uri 去装（PackageInstaller 能读流）。
            runCatching { src.delete() }
            pruneOldDownloads(ctx, keepName = src.name)
            return uri
        }
        // 老系统（<Q）：只能落到公共目录的 File
        val pub = File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "VitalBridge/update").apply { mkdirs() }
        val dst = File(pub, src.name)
        runCatching { src.copyTo(dst, overwrite = true) }
        return android.net.Uri.fromFile(dst)
    }


    // ─────────────────── WiFi 自更新（2026-10-21）───────────────────

    /** 与手机端同一个 R2 桶。 */
    private const val BASE = "https://REPLACE-ME.invalid/"
    private const val INDEX = "apk/watch.json"

    fun localVersionCode(ctx: Context): Long = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).longVersionCode
    }.getOrDefault(0L)

    /**
     * 手表自己去云端拉更新（不经过手机）。
     * 只在用户点「检查更新」时跑一次；不做后台定时（Wear 熄屏会冻结网络，白耗电）。
     * @return 给界面看的一句话
     */
    fun updateFromWifi(ctx: Context, onStage: (String, Float) -> Unit): String = runCatching {
        onStage("正在检查更新…", 0f)
        val o = JSONObject(httpGet(BASE + INDEX, 15_000))
        val code = o.optInt("version", 0)
        val size = o.optLong("size", 0L)
        val sha = o.optString("sha256", "").lowercase()
        val file = o.optString("file", "vitalbridge-watch-v$code.apk")
        val url = o.optString("url", BASE + "apk/" + file)
        val local = localVersionCode(ctx)
        if (code <= 0) return "清单里没有版本号"
        if (code.toLong() <= local) return "已是最新版本（v$local）"

        onStage("正在下载 v$code…", 0f)
        val dir = File(ctx.cacheDir, "ota-wifi").apply { mkdirs() }
        val tmp = File(dir, file + ".part")
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("Accept-Encoding", "identity")
            }
            conn.connect()
            if (conn.responseCode !in 200..299) return "下载失败 HTTP ${conn.responseCode}"
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            var total = 0L
            conn.inputStream.use { input ->
                java.io.FileOutputStream(tmp).use { fos ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        fos.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        total += n
                        if (size > 0) onStage("正在下载 v$code…", (total.toFloat() / size).coerceIn(0f, 1f))
                    }
                    fos.flush()
                }
            }
            if (size > 0 && total != size) { tmp.delete(); return "下载不完整（$total/$size）" }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            if (sha.isNotEmpty() && hex != sha) { tmp.delete(); return "校验失败（sha256 不符）" }

            onStage("正在安装…", 1f)
            val uri = runCatching { saveToDownloads(ctx, tmp) }.getOrNull()
            // ⚠⚠ 2026-10-28：**只有真正存进了 Download 才允许删缓存**。
            //   原来无条件删：一旦 MediaStore 写入失败（uri == null），唯一那份安装包
            //   就在这一步被自己删掉了 ⇒ 用户看到"已下载并提交安装"，但既没弹安装页、
            //   文件管理器里也找不到包（用户 2026-10-28 报的就是这个）。
            val r = install(ctx, uri, tmp.name, if (uri == null) tmp else null, size, code)
            if (uri != null) {
                runCatching { File(ctx.cacheDir, "ota-wifi").listFiles()?.forEach { it.delete() } }
            }
            // ⚠ 返回**真实结果**，别再写死一句"已下载并提交安装"：
            //   写死等于把失败也说成成功，用户在界面上永远看不到发生了什么。
            val where = if (uri != null) "已存到 Download/VitalBridge/update/${tmp.name}" else "包在应用私有目录（文件管理器看不到）"
            "$r（v$code，$where）"
        } finally {
            conn?.disconnect()
        }
    }.getOrElse { "更新失败：${it.message ?: it.javaClass.simpleName}" }

    private fun httpGet(url: String, timeoutMs: Int): String {
        var conn: java.net.HttpURLConnection? = null
        try {
            conn = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
            }
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * 交给系统安装器（2026-10-21 重做）。
     *
     * ⚠⚠ 关键限制：Android 10+ **禁止后台（含前台服务）直接 `startActivity`**，会被静默拦截，
     *   所以用户之前只能自己去文件管理器点安装包。
     *   现在的做法：
     *    ① 先尝试直接拉起（App 刚用过时常被允许，等于"自动弹出"）；
     *    ② 不行就发一条**高优先级通知**，点一下即进安装页（比翻文件管理器省事得多）；
     *    ③ 若本 App 没有"安装未知应用"权限，通知改成引导去开权限。
     */
    /**
     * 删掉 `Download/VitalBridge/update/` 里同前缀的旧包，只留 [keepName]（2026-10-22 修）。
     *
     * ⚠⚠ 原实现用 `RELATIVE_PATH = 'Download/VitalBridge/update'` **精确匹配**，
     *   而 MediaStore 存的是带结尾斜杠的 `Download/VitalBridge/update/` ⇒ **一条都匹配不上**，
     *   所以旧包永远不会被删（用户看到的三个包就是这么攒下来的）。
     *   现在只按 DISPLAY_NAME 前缀查，再用 `path.contains("VitalBridge")` 粗筛，斜杠与否都能命中。
     * 同时清掉 `.part` 残留。
     */
    /**
     * 找 `Download/VitalBridge/update/vitalbridge-watch-v<code>.apk`（2026-10-22）。
     * @return (uri, 文件名, 字节数)；没有则 null
     */
    private fun findLocalApk(
        ctx: Context,
        code: Long,
    ): Triple<android.net.Uri, String, Long>? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return null
        val name = "vitalbridge-watch-v$code.apk"
        val coll = android.provider.MediaStore.Downloads.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val proj = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.SIZE,
        )
        val sel = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        ctx.contentResolver.query(coll, proj, sel, arrayOf("vitalbridge-watch%"), null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val dn = c.getString(1) ?: continue
                val size = c.getLong(2)
                // 名字必须**完全等于**这个版本，避免拿错包
                if (dn == name && size > 0) {
                    return Triple(android.content.ContentUris.withAppendedId(coll, id), name, size)
                }
            }
        }
        return null
    }

    private fun pruneOldDownloads(ctx: Context, keepName: String) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) return
        val coll = android.provider.MediaStore.Downloads.getContentUri(
            android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val proj = arrayOf(
            android.provider.MediaStore.MediaColumns._ID,
            android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
            android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
        )
        val sel = "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayOf("vitalbridge-watch%")
        val doomed = mutableListOf<android.net.Uri>()
        ctx.contentResolver.query(coll, proj, sel, args, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(1) ?: continue
                val path = c.getString(2) ?: ""
                if (!path.contains("VitalBridge", ignoreCase = true)) continue
                val isPart = name.endsWith(".part", ignoreCase = true)
                if (name == keepName && !isPart) continue
                doomed += android.content.ContentUris.withAppendedId(coll, id)
            }
        }
        doomed.forEach { uri -> runCatching { ctx.contentResolver.delete(uri, null, null) } }
        Log.i(TAG, "pruneOldDownloads: keep=$keepName, removed=${doomed.size}")
    }


    private fun install(
        ctx: Context,
        uri: android.net.Uri?,
        name: String,
        fallback: File?,
        sizeBytes: Long = 0L,
        versionCode: Int = 0,
    ): String {
        // ③ 权限自检
        val canInstall = runCatching { ctx.packageManager.canRequestPackageInstalls() }.getOrDefault(true)
        if (!canInstall) {
            notifyInstall(ctx, name, "需要允许「安装未知应用」", settingsIntent(ctx))
            lastStatus = "需要允许「安装未知应用」，已给出通知入口"
            return lastStatus
        }

        // ① 首选：PackageInstaller 会话（直接从 uri 读流写进会话，**不落第二份**）
        val viaSession = runCatching { installViaSession(ctx, uri, fallback, sizeBytes) }
            .onFailure {
                // ⚠ 别吞掉失败原因：这就是"只弹通知、不弹安装页"的排查线索
                Log.w(TAG, "安装会话失败：${it.javaClass.simpleName} ${it.message}", it)
            }
            .isSuccess

        val target = installerIntent(ctx, uri, fallback)
        // ② 顺手直接拉起一次：App 就在前台，这一步在多数机型上直接弹安装页
        runCatching { ctx.startActivity(target) }

        // ③ ⚠⚠ **无论上面成不成，都留一条能点的通知**（2026-10-28）。
        //   原来只在"会话失败"时才发通知 ⇒ 会话提交成功但系统没弹确认框时，
        //   界面上就只剩一句"已提交安装"，用户完全无处可点（Wear 上常见）。
        //   多一条通知的代价，远小于"看起来装完了、其实什么都没发生"。
        val title = if (versionCode > 0) "安装包已就绪，点此安装 v$versionCode" else "安装包已就绪，点此安装"
        notifyInstall(ctx, name, title, target)

        lastStatus = if (viaSession) "已提交安装，若没看到安装页请点通知" else "安装会话失败，点通知重试"
        return lastStatus
    }

    /** 用 `PackageInstaller` 提交安装（自更新）。失败抛异常，由 [install] 退回通知方案。 */
    private fun installViaSession(
        ctx: Context,
        uri: android.net.Uri?,
        fallback: File?,
        sizeBytes: Long,
    ) {
        val installer = ctx.packageManager.packageInstaller
        val params = android.content.pm.PackageInstaller.SessionParams(
            android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL,
        ).apply { setAppPackageName(ctx.packageName) }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            // ⚠ 优先直接用 Download 的 content uri 读流 —— 这样**不需要**再复制一份到私有目录
            val opened = uri?.let { runCatching { ctx.contentResolver.openInputStream(it) }.getOrNull() }
            val input = opened ?: (fallback ?: error("没有可安装的文件")).inputStream()
            // ⚠⚠ 2026-10-22 关键：`openWrite(name, offset, length)` 的 length **必须给真实字节数**。
            //   之前从 uri 读流时传了 0（想表达"未知"），结果写入阶段校验失败 ⇒ 整个会话失败
            //   ⇒ 退回成"发一条通知"，也就是用户看到的"不自动安装、只弹通知"。
            val len = when {
                sizeBytes > 0 -> sizeBytes
                fallback != null -> fallback.length()
                else -> 0L
            }
            input.use { ins ->
                session.openWrite("base.apk", 0, len).use { out ->
                    ins.copyTo(out)
                    session.fsync(out)
                }
            }
            val cb = android.app.PendingIntent.getBroadcast(
                ctx, sessionId,
                Intent(ctx, OtaInstallReceiver::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
            )
            session.commit(cb.intentSender)
        }
    }

    private fun installerIntent(ctx: Context, uri: android.net.Uri?, fallback: File?): Intent {
        val target: Uri = uri ?: FileProvider.getUriForFile(
            ctx, ctx.packageName + ".fileprovider",
            fallback ?: error("没有可安装的文件"),
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(target, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun settingsIntent(ctx: Context): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${ctx.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 一条可点的通知（安装 / 去开权限）。 */
    private fun notifyInstall(ctx: Context, apkName: String, title: String, target: Intent) {
        val nm = ctx.getSystemService(android.app.NotificationManager::class.java)
        val chId = "ota_install"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            nm?.createNotificationChannel(
                android.app.NotificationChannel(
                    chId, "安装更新", android.app.NotificationManager.IMPORTANCE_HIGH,
                ),
            )
        }
        val pi = android.app.PendingIntent.getActivity(
            ctx, 9001, target,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        val b = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
            android.app.Notification.Builder(ctx, chId) else
            @Suppress("DEPRECATION") android.app.Notification.Builder(ctx)
        val n = b.setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(apkName)
            .setAutoCancel(true)
            .setCategory(android.app.Notification.CATEGORY_EVENT)
            .setContentIntent(pi)
            // ⚠⚠ 2026-10-22：加 fullScreenIntent —— 会话通道万一失败，这条通知会**直接拉起安装页**
            //   （全屏意图不受后台启动限制），而不是像现在这样只躺在通知栏等用户点。
            .setFullScreenIntent(pi, true)
            .build()
        runCatching { nm?.notify(9001, n) }
    }
}
