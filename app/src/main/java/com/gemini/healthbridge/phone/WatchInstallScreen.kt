package com.gemini.healthbridge.phone

import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.gemini.healthbridge.phone.ui.AppMutedFill
import com.gemini.healthbridge.phone.ui.AppSurface

/**
 * **无线 ADB**（独立页面，从「无线ADB」页的「安装」卡片点进来）。
 *
 * ## 两种装法，各管一件事
 *
 * 1. **选择手机里的安装包**（手动）：用于"我手上有个特定版本的 APK"，
 *    默认打开 `Download/VitalBridge/update/`（手表包平时就落在这儿）。
 * 2. **安装手表端脉迹**（一键）：拉云端 `watch.json` → 下载 → 装进去，用于"正常更新"。
 *
 * ⚠ 两者最终都走同一个 [WatchAdb.installApk]，所以"装完有没有成功"只有一套判据
 *   （`pm install` 输出里出现 `Success`）。
 * ⚠ 选择文件用 SAF（`ACTION_OPEN_DOCUMENT`），不需要存储权限；
 *   默认目录靠 `EXTRA_INITIAL_URI` 指定，目录不存在时系统会忽略（退到上次位置），不会报错。
 * ⚠⚠ 2026-10-27 用户反馈"点安装没有进度条、也看不到装成功没有" ⇒
 *   **进度条和结果必须就地显示在按钮下面**（原来百分比挤在按钮右边、结果显示在页面最底部，
 *   装完根本看不到）。两条装法共用 [InstallProgress] / [InstallResult]。
 */
@Composable
fun WatchInstallScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val phase by WatchAdb.phase.collectAsState()
    val ver by WatchAdb.watchVersion.collectAsState()
    // ⚠ 必须 collectAsState：直接读 `WatchAdb.deviceName.value` 不会触发重组（连上后标题不刷新）
    val devName by WatchAdb.deviceName.collectAsState()
    val writePct by WatchAdb.progress.collectAsState()

    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    /** 选好之后**已经拷到缓存**的文件（装的时候不再重复拷） */
    var pickedFile by remember { mutableStateOf<File?>(null) }
    /** 从 apk 里读出来的图标 / 应用名 / 版本（读不出来就是 null） */
    var pickedIcon by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    var pickedLabel by remember { mutableStateOf<String?>(null) }
    var pickedVer by remember { mutableStateOf<String?>(null) }
    /** 正在拷包 + 读图标（4~5 MB 的包要几百毫秒） */
    var reading by remember { mutableStateOf(false) }

    // 本地包安装的进度/结果
    var localBusy by remember { mutableStateOf(false) }
    var localPct by remember { mutableFloatStateOf(0f) }
    var localResult by remember { mutableStateOf<String?>(null) }
    var localFailed by remember { mutableStateOf(false) }

    val connected = phase == WatchAdb.Phase.Connected || phase == WatchAdb.Phase.Busy

    BackHandler(enabled = true) { if (!localBusy) onBack() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        val nm = queryName(ctx, uri) ?: uri.lastPathSegment ?: "selected.apk"
        pickedUri = uri
        pickedName = nm
        pickedFile = null; pickedIcon = null; pickedLabel = null; pickedVer = null
        localResult = null
        // ⚠ 用户 2026-10-27：选完要能**看到图标**，确认自己选的是哪个 App。
        //   图标只能从 apk 文件里读（`PackageManager.getPackageArchiveInfo`）⇒ 先拷到缓存再解析；
        //   顺便把"装的时候要拷的那一份"提前备好，点安装时不再读一次 content uri。
        scope.launch {
            reading = true
            val f = copyToCache(ctx, uri, nm)
            pickedFile = f
            if (f != null) {
                val meta = readApkMeta(ctx, f)
                pickedIcon = meta.icon
                pickedLabel = meta.label
                pickedVer = meta.version
            }
            reading = false
        }
    }

    fun openPicker() {
        val initial = runCatching {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                // 手表包平时落在这里（`WatchOta.exportToDownloads` / 手表自己下载）
                "primary:Download/VitalBridge/update",
            )
        }.getOrNull()
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            // ⚠ 用 `*/*` + EXTRA_MIME_TYPES：部分 ROM 不认识 apk 的 MIME，直接给 apk 会"无应用可打开"
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/vnd.android.package-archive", "application/octet-stream"))
            initial?.let { putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
        runCatching { picker.launch(i) }.onFailure {
            localResult = ctx.getString(R.string.s_cd027e34, it.message ?: "cannot open picker")
            localFailed = true
        }
    }

    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxWidth().vScrollbar(
                rememberScrollState(),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
            )
        ) {
            // ── 顶部：返回 + 标题 ──
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdbOutlineButton(onClick = { if (!localBusy) onBack() }) {
                    Text("‹  " + stringResource(R.string.s_0049f05a), fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                // ⚠ 用户 2026-10-27：**返回下面这行标题改成「无线ADB」**（原来写的是"安装手表端"，
                //   和底部标签页重名，进来看不出自己在哪一层）
                Text(
                    stringResource(R.string.s_40c8d4dc),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (connected) stringResource(R.string.s_d11f4227, devName)
                    else stringResource(R.string.s_518a9efc),
                    fontSize = 12.sp,
                    color = if (connected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (connected) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (ver > 0) stringResource(R.string.s_e5ef9d2a, "v$ver")
                        else stringResource(R.string.s_d9882205),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(14.dp))

                // ── ① 选本地安装包 ──
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.s_f91c1c6a),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (reading) {
                            Text(
                                stringResource(R.string.s_18a9a635),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else if (pickedName != null) {
                            // 图标 + 应用名 + 版本：光看文件名分不清是哪个 App
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (pickedIcon != null) {
                                    Image(
                                        bitmap = pickedIcon!!,
                                        contentDescription = null,
                                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        pickedLabel ?: pickedName!!,
                                        fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        buildString {
                                            pickedVer?.takeIf { it.isNotBlank() }?.let { append("v").append(it).append("  ·  ") }
                                            append(pickedName)
                                        },
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        } else {
                            Text(
                                stringResource(R.string.s_4c9122f9),
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // ⚠ 用户要求：不能只有文字（看起来像说明而非可点），统一成有边框的按钮
                            AdbOutlineButton(onClick = { if (!localBusy) openPicker() }) {
                                Text(stringResource(R.string.s_66f6a2af), fontSize = 13.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Button(
                                onClick = {
                                    if (!connected) {
                                        localResult = ctx.getString(R.string.s_518a9efc)
                                        localFailed = true
                                        return@Button
                                    }
                                    scope.launch {
                                        localBusy = true; localResult = null; localPct = 0f
                                        // 已提前拷好就用那份；没有（比如拷失败）再补拷一次
                                        val f = pickedFile
                                            ?: pickedUri?.let { copyToCache(ctx, it, pickedName) }
                                        if (f == null) {
                                            localResult = ctx.getString(R.string.s_cd027e34, "读取文件失败")
                                            localFailed = true; localBusy = false; return@launch
                                        }
                                        WatchAdb.progress.value = 0f
                                        val bad = WatchAdb.installApk(f)
                                        localFailed = bad != null
                                        // ⚠⚠ 这里装的是**用户自己选的包**（可能是任何 App）⇒
                                        //   文案只能是「安装成功」，绝不能写"请打开手表上的「脉迹」"、
                                        //   也不能显示手表上脉迹的版本（2026-10-27 真机反馈：装了"抬腕看图"，
                                        //   界面却说"安装完成，请打开脉迹... 手表上已安装 v192"）。
                                        localResult = if (bad == null) ctx.getString(R.string.s_67f47faf)
                                        else ctx.getString(R.string.s_cd027e34, bad)
                                        localBusy = false
                                    }
                                },
                                // ⚠ 没连上手表/还在读包都不该能点
                                enabled = pickedFile != null && !reading && !localBusy && connected
                                    && phase != WatchAdb.Phase.Busy,
                            ) {
                                Text(stringResource(R.string.s_002f5fe0), fontSize = 13.sp)
                            }
                        }
                        // 进度条 + 结果**就地**显示（本地包这条也会走 `installApk`，进度同样有）
                        if (localBusy) {
                            InstallProgress(
                                label = stringResource(R.string.s_21011a74),
                                pct = writePct,
                            )
                        }
                        // ⚠ 本地包这条**不显示"手表上已安装 vX"**：那个版本是脉迹的，跟这个包无关
                        localResult?.let { InstallResult(it, ok = !localFailed, showWatchVersion = false) }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── ② 一键安装（云端清单 → 下载 → 装）──
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            // ⚠ 2026-10-28 起**手机不再内置手表包**（内置会让手机包白胖 15MB，
                            //   而且"内置版本 vs 线上版本"两份真源必然打架 —— v192/193 那次就是）。
                            //   现在这条路固定是"下载最新版 → 无线 ADB 装进手表"。
                            stringResource(R.string.s_a5fa676b),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        OneClickInstallBlock()
                    }
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/**
 * **一键安装手表端脉迹**：查云端清单 → 下载 → 装进手表。
 *
 * ⚠ 抽成组件的理由：**向导第 12 步和「无线ADB」页用的是同一个动作**，两处各写一份必然漂移
 *   （用户上一轮就是在向导里点了安装、既没进度也没结果）。
 *
 * ⚠ 进度分两段（下载 35% + 写入 65%）：`WatchOta.download` 给的是下载进度，
 *   `WatchAdb.progress` 给的是**写入**进度 —— 直接串起来会让进度条从 40% 跳回 0%，
 *   看着像"重来了"。
 */
@Composable
fun OneClickInstallBlock(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val phase by WatchAdb.phase.collectAsState()
    val ver by WatchAdb.watchVersion.collectAsState()
    val writePct by WatchAdb.progress.collectAsState()

    /** 0 空闲 / 1 下载 / 2 写入 */
    var stage by remember { mutableIntStateOf(0) }
    var dlPct by remember { mutableFloatStateOf(0f) }
    var result by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    /** 云端最新版（用来提示"有新版"，不强制） */
    var latest by remember { mutableIntStateOf(0) }

    val connected = phase == WatchAdb.Phase.Connected || phase == WatchAdb.Phase.Busy
    val busy = stage != 0

    Column(modifier.fillMaxWidth()) {
        Button(
            onClick = {
                if (!connected) {
                    result = ctx.getString(R.string.s_518a9efc)
                    failed = true
                    return@Button
                }
                scope.launch {
                    stage = 1; dlPct = 0f; result = null
                    val info = WatchOta.latestInfo(ctx).getOrElse {
                        result = ctx.getString(R.string.s_cd027e34, it.message ?: "?")
                        failed = true; stage = 0; return@launch
                    }
                    latest = info.versionCode
                    if (info.versionCode.toLong() <= ver) {
                        result = ctx.getString(R.string.s_0fc42223, info.versionCode)
                        failed = false; stage = 0; return@launch
                    }
                    val file = WatchOta.download(ctx, info) { p -> dlPct = p }
                        .getOrElse {
                            result = ctx.getString(R.string.s_cd027e34, it.message ?: "?")
                            failed = true; stage = 0; return@launch
                        }
                    stage = 2
                    val bad = WatchAdb.installApk(file)
                    failed = bad != null
                    // 匿名统计（2026-10-28）：无线 ADB 装表**在真机上通不通**只有这条能回答。
                    // ⚠ 只报成败，不报 `bad` 里的失败原因（那是本地化文案，且可能含路径）。
                    Stats.event("watch_install", if (bad == null) "ok" else "fail")
                    result = if (bad == null) ctx.getString(R.string.s_6eb615d2)
                    else ctx.getString(R.string.s_cd027e34, bad)
                    // ⚠ 成功要**发系统通知**，不能只在页面上显示一句话（2026-10-28）：
                    //   用户点完一键安装通常就抬头去看手表了，回来时这行结果早就被顶掉了。
                    if (bad == null) notifyWatchInstalled(ctx, info.versionCode)
                    stage = 0
                }
            },
            enabled = !busy && connected,
        ) {
            Text(stringResource(R.string.s_cf746614), fontSize = 13.sp)
        }

        when (stage) {
            1 -> InstallProgress(stringResource(R.string.s_e31f1d3c), dlPct * 0.35f)
            2 -> InstallProgress(
                stringResource(R.string.s_21011a74),
                0.35f + writePct.coerceIn(0f, 1f) * 0.65f,
            )
        }

        result?.let { InstallResult(it, ok = !failed) }

        // ⚠ 必须 `ver > 0`：读不到手表版本时 ver 是 0，那样"0 < 188"会误报
        //   「检测到新版 v188」——用户手表本来就是 188（2026-10-27 真机反馈）。
        if (ver > 0 && latest.toLong() > ver) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.s_bd4184b0, "v$latest"),
                fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** 进度条（标签 + 百分比 + 条）。⚠ 三条安装路径共用，别各画一份。 */
@Composable
fun InstallProgress(label: String, pct: Float) {
    val p = pct.coerceIn(0f, 1f)
    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(
                "${(p * 100).toInt()}%",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 有总长度就用确定进度；这里始终有（本地包/云端包都先落到文件）
        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
    }
}

/**
 * 安装结果（成功 / 失败都在**按钮下面**说清楚）。
 *
 * ⚠ 用户 2026-10-27：「装完看不到成不成」⇒ 不再用一行小灰字，改成带图标的卡片块；
 *   并且在下面补一句"手表上装的是哪一版"，让"装成功了"这件事可核对。
 */
@Composable
fun InstallResult(text: String, ok: Boolean, showWatchVersion: Boolean = true) {
    val installedVer by WatchAdb.watchVersion.collectAsState()
    val c = if (ok) MaterialTheme.colorScheme.primary else Color(0xFFD32F2F)
    // 灰底脚注条：与 `AdbTab` 的前提条同一处理（不描边）。
    AppSurface(
        Modifier.fillMaxWidth().padding(top = 10.dp),
        containerColor = AppMutedFill,
        borderColor = null,
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ok) "✓" else "✕",
                fontSize = 18.sp, fontWeight = FontWeight.Bold, color = c,
            )
            Column(Modifier.padding(start = 10.dp)) {
                Text(text, fontSize = 13.sp, color = c, fontWeight = FontWeight.Bold)
                if (ok && showWatchVersion) {
                    if (installedVer > 0) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.s_e5ef9d2a, "v$installedVer"),
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** apk 里能读到的展示信息（图标/应用名/版本）。 */
private class ApkMeta(
    val icon: androidx.compose.ui.graphics.ImageBitmap?,
    val label: String?,
    val version: String?,
)

/**
 * 从 apk 文件读图标 / 应用名 / 版本。
 *
 * ⚠ `loadIcon` / `loadLabel` 前必须把 `sourceDir` 指到文件路径，否则它们会去
 *   "已安装的那个包"里找资源（拿到的是手机自己装的版本，甚至直接返回 null）。
 */
private fun readApkMeta(ctx: android.content.Context, f: File): ApkMeta {
    val pm = ctx.packageManager
    val pi = runCatching { pm.getPackageArchiveInfo(f.absolutePath, 0) }.getOrNull()
        ?: return ApkMeta(null, null, null)
    val ai = pi.applicationInfo
    ai?.sourceDir = f.absolutePath
    ai?.publicSourceDir = f.absolutePath
    val icon = runCatching { ai?.loadIcon(pm)?.toBitmap()?.asImageBitmap() }.getOrNull()
    val label = runCatching { ai?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } }.getOrNull()
    val ver = runCatching { pi.versionName?.takeIf { it.isNotBlank() } }.getOrNull()
    return ApkMeta(icon, label, ver)
}

/** 从 SAF 的 uri 里问出文件名（问不到就返回 null，调用方退到 lastPathSegment）。 */
private fun queryName(ctx: android.content.Context, uri: Uri): String? = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull()

/** 把选中的安装包拷到私有缓存再装（`pm install -S` 要读长度，content uri 拿不到可靠长度）。 */
private suspend fun copyToCache(ctx: android.content.Context, uri: Uri, name: String?): File? =
    withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(ctx.cacheDir, "adb-install").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, name?.takeIf { it.endsWith(".apk") } ?: "watch.apk")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { o -> input.copyTo(o, 64 * 1024) }
            } ?: return@runCatching null
            out.takeIf { it.length() > 0 }
        }.getOrNull()
    }

/**
 * 「手表端已装好」的系统通知（2026-10-28）。
 *
 * ⚠ 为什么不用 Toast：一键安装要几十秒到几分钟，用户点完就去看手表了，
 *   Toast 等他回来早没了 —— 通知留在状态栏里，回头还能确认"到底装成没装上"。
 * ⚠ 自建渠道 + IMPORTANCE_HIGH：默认渠道是静默的，用户根本注意不到。
 */
private fun notifyWatchInstalled(context: android.content.Context, versionCode: Int) {
    val nm = context.getSystemService(android.app.NotificationManager::class.java) ?: return
    val chId = "watch_install"
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        nm.createNotificationChannel(
            android.app.NotificationChannel(
                chId, "手表安装", android.app.NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }
    val b = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        android.app.Notification.Builder(context, chId)
    } else {
        @Suppress("DEPRECATION")
        android.app.Notification.Builder(context)
    }
    val text = if (versionCode > 0) "v$versionCode" else context.getString(R.string.s_6eb615d2)
    val n = b.setSmallIcon(android.R.drawable.stat_sys_download_done)
        .setContentTitle(context.getString(R.string.s_91484ee4))
        .setContentText(text)
        .setAutoCancel(true)
        .build()
    runCatching { nm.notify(9101, n) }
}
