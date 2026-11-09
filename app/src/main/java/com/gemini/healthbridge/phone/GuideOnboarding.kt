package com.gemini.healthbridge.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.Card
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.healthbridge.phone.ui.AppSurface

/**
 * 首次运行**强制向导**（2026-10-26）。
 *
 * ## 为什么必须"过不去就不能用"
 *
 * 手表端装不上，整个 App 全是空数据。这一页就是把"让手表能装"的两件前置条件
 * （手机三星健康开发者模式、手表健康平台 Dev mode）讲清楚，然后告诉用户
 * 还得把表连到同一 WiFi 并开「无线调试」——之后才进「安装」页。
 *
 * ## 结构（11 步 = 6 + 4 + 1）
 *
 * 手机三星健康 6 步 → 手表健康平台 4 步 → 无线调试说明 1 步（点「完成」进安装页）。
 * ⚠ 第一步**没有截图**（就是"打开三星健康"，没什么可截的）——所以图片是 `null`。
 *
 * ⚠⚠ **不能退出**：`BackHandler {}` 把返回键吃掉；也没有"跳过"按钮。
 *   否则用户一按返回就进主界面、手表永远装不上，反而更难查。
 *
 * ⚠ OneUI 8 的开发者模式教程是**另一个页面**（`GuideOneUi8`），从第 11 步的按钮进，
 *   看完点「返回」回到第 11 步（用 `showOneUi` 控制，不新增路由）。
 */
/**
 * 「首次引导是否已完成」的落点。
 *
 * ⚠ key 里带 `_v1`：将来引导内容**大改**（比如多了新前置条件）时可以换个 key 重来一遍，
 *   而不必让老用户也重看一遍。现在是 v1。
 */
object GuidePrefs {
    private const val SP = "guide"
    private const val K = "onboarded_v1"

    fun isDone(ctx: android.content.Context): Boolean =
        ctx.getSharedPreferences(SP, android.content.Context.MODE_PRIVATE).getBoolean(K, false)

    fun markDone(ctx: android.content.Context) {
        ctx.getSharedPreferences(SP, android.content.Context.MODE_PRIVATE)
            .edit().putBoolean(K, true).apply()
    }
}

private data class GStep(val section: Int, val image: Int?, val body: Int)

private val GUIDE_STEPS: List<GStep> = listOf(
    // ── 第一步：手机三星健康（6 步，5 张图）──
    GStep(0, null, R.string.s_54ff7def),
    GStep(0, R.drawable.guide_p1, R.string.s_b18fb481),
    GStep(0, R.drawable.guide_p2, R.string.s_453ff4cf),
    GStep(0, R.drawable.guide_p3, R.string.s_78feb65f),
    // ⚠ 2026-11-04 用户要求插入这一步：三星健康 7.0 起，"关于三星健康"页里的
    //   Developer mode 入口**默认不显示**，要先连续点版本号那一块把它点出来。
    //   原来直接跳到「点击 Developer mode」，用户照着做找不到按钮。
    GStep(0, R.drawable.guide_p3b, R.string.s_6f029cfb),
    GStep(0, R.drawable.guide_p4, R.string.s_04be4f39),
    GStep(0, R.drawable.guide_p5, R.string.s_7cd33e13),
    // ── 第二步：手表健康平台（4 步）──
    GStep(1, R.drawable.guide_w1, R.string.s_8c1ae30f),
    GStep(1, R.drawable.guide_w2, R.string.s_132158c4),
    GStep(1, R.drawable.guide_w3, R.string.s_5be87d32),
    GStep(1, R.drawable.guide_w4, R.string.s_a569add7),
    // ── 第三步：无线调试（1 步，无图，带 OneUI8 教程按钮）──
    GStep(2, null, R.string.s_fc1cc66c),
    // ── 第四步：连上手表并把它装上（2026-10-26 新增；**装不上就不能完成**）──
    GStep(3, null, R.string.s_ba706249),
)

private val ONEUI_STEPS = listOf(
    R.drawable.guide_o1 to R.string.s_c9d6b38f,
    R.drawable.guide_o2 to R.string.s_82557bee,
    R.drawable.guide_o3 to R.string.s_d8e40e2b,
    R.drawable.guide_o4 to R.string.s_e8c1979d,
    R.drawable.guide_o5 to R.string.s_a33a136c,
)

@Composable
fun OnboardingWizard(replay: Boolean = false, onFinish: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var showOneUi by remember { mutableStateOf(false) }

    // ⚠ 返回键：**首次**吃掉（必须走完，否则一按返回就进空数据的主界面）；
    //   **回看**时按返回 = 关闭向导回主界面。
    // ⚠⚠ 回看**不能**写成 `enabled = !replay`：那样返回键会落到 Activity 的默认行为
    //   = finish()，**直接把 App 退到桌面**（实测踩到：点问号进向导后按返回，回到了桌面）。
    //   拦下来自己处理，才是"关闭这一层"。
    BackHandler(enabled = true) {
        if (replay) onFinish()
    }

    // ⚠⚠ 权限**在向导第一步就要**（用户要求：别再等进了主界面才弹）：
    //   蓝牙是连手表的前提，通知是安装完成后提示要用；两者都不影响本页渲染，拒绝也能继续。
    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { }
    LaunchedEffect(Unit) {
        val need = buildList {
            if (android.os.Build.VERSION.SDK_INT >= 31)
                add(android.Manifest.permission.BLUETOOTH_CONNECT)
            if (android.os.Build.VERSION.SDK_INT >= 33)
                add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (need.isNotEmpty()) runCatching { permLauncher.launch(need.toTypedArray()) }
    }

    if (showOneUi) {
        GuideOneUi8(onBack = { showOneUi = false })
        return
    }

    // ── 第 4 步要用：连没连上、手表上装没装、云端最新版是多少 ──
    val phase by WatchAdb.phase.collectAsState()
    val watchVer by WatchAdb.watchVersion.collectAsState()
    var latest by remember { mutableIntStateOf(0) }
    var showUpdateTip by remember { mutableStateOf(false) }
    /** 手表上装了脉迹（任意版本）才允许完成向导 */
    val installed = watchVer > 0 && (phase == WatchAdb.Phase.Connected || phase == WatchAdb.Phase.Busy)

    // 连上之后查一次"该装哪一版"：只用来"提示有新版"，**不强制更新**
    // ⚠ 用 `latestInfo`（**手机自带的优先**），不是线上清单 —— 线上那份停在 188，
    //   而 188 一启动就闪退（见 `WatchOta.Info.bundled` 的说明）。
    val appCtx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(phase) {
        if (phase == WatchAdb.Phase.Connected && latest == 0) {
            latest = WatchOta.latestInfo(appCtx).getOrNull()?.versionCode ?: 0
        }
    }

    val total = GUIDE_STEPS.size
    val step = GUIDE_STEPS[index]
    val sectionTitle = when (step.section) {
        0 -> R.string.s_a43ab565
        1 -> R.string.s_cb64549e
        2 -> R.string.s_f8424c3f
        else -> R.string.s_9db19b2f
    }

    if (showUpdateTip) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUpdateTip = false; onFinish() },
            title = { Text(stringResource(R.string.s_8ba513e1)) },
            text = {
                Column {
                    Text(stringResource(R.string.s_bd4184b0, "v$latest"), fontSize = 14.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s_e5ef9d2a, "v$watchVer"),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showUpdateTip = false }) {
                    Text(stringResource(R.string.s_1faf4f0c))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showUpdateTip = false; onFinish() }) {
                    Text(stringResource(R.string.s_002f3ab4))
                }
            },
        )
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // ⚠⚠ 必须 `statusBarsPadding()`：不加的话标题会顶到状态栏下面被时钟压住
        //   （用户真机截图：14:07 直接盖在「首次使用引导」上）。
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // ── 顶部：进度条 + 计数 ──
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.s_b4ea3adb),
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    // ⚠ 纯数字不做资源（`%d / %d` 这种"全是占位符"的条目会被译文库的
                    //   格式转换改写 key，取不稳）⇒ 直接拼。
                    Text(
                        "${index + 1} / $total",
                        fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((index + 1f) / total)
                            .height(4.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                }
            }

            // ── 分区标题 + 正文 + 图（可滚动，图不会挤掉文字）──
            Column(
                Modifier
                    .weight(1f)
                    // ⚠ 带滚动条：最后一步内容比屏幕高，「安装手表端脉迹」在屏幕外，
                    //   不给指示用户会以为"下面没东西了、完成却点不了 = bug"
                    .vScrollbar(
                        rememberScrollState(),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                    )
                    .padding(horizontal = 20.dp),
                // ⚠ 一律**居中**：内容矮时（横幅截图、无图那步）不会在下面留一大片空白；
                //   内容高时（竖屏长截图）`verticalScroll` 照旧从头滚起，居中不起作用。
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    stringResource(sectionTitle),
                    fontSize = 18.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(10.dp))
                if (step.section == 3) {
                    // ⚠ 这一步**不能只放文字**：必须真的连上、真的装好，否则不让完成。
                    //   连接块与「安装」页共用同一个组件（扫不到时的手动连接也一样）。
                    // ⚠ 必须在这里单独显示连接状态：向导是全屏的，**看不到**标题栏那个状态点
                    //   （用户反馈"引导页面里的安装看不到是否已经连接"）。
                    val devName by WatchAdb.deviceName.collectAsState()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WatchAdbStatusChip()
                        if (installed || phase == WatchAdb.Phase.Connected) {
                            Spacer(Modifier.width(8.dp))
                            Text(devName, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    WatchConnectBlock()
                    Spacer(Modifier.height(12.dp))
                    // ⚠ 安装动作和「无线ADB」页**共用同一个组件**（`OneClickInstallBlock`）：
                    //   上一轮这里是自己抄的一份，于是"页面上有进度条、向导里没有"
                    //   （用户 2026-10-27 反馈"点了安装没进度、也看不到成不成"就是向导这一步）。
                    AppSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                if (installed) stringResource(R.string.s_e5ef9d2a, "v$watchVer")
                                else stringResource(R.string.s_d9882205),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                color = if (installed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            OneClickInstallBlock()
                        }
                    }
                    // ⚠ 说清"为什么完成是灰的"：不说的话用户只会觉得按钮坏了
                    if (!installed) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            stringResource(R.string.s_25194f20),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    return@Column
                }
                Text(
                    stringResource(step.body),
                    fontSize = 14.sp, lineHeight = 21.sp,
                )
                step.image?.let { res ->
                    Spacer(Modifier.height(14.dp))
                    Image(
                        painter = painterResource(res),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            // ⚠ 不能给固定 aspectRatio：第 2 步那张是**横幅**（1046×231），
                            //   塞进竖屏框会变成一大片灰底。按图自身比例排、只封顶高度。
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(14.dp),
                            )
                            .padding(6.dp),
                    )
                }
                if (step.section == 2) {
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { showOneUi = true }) {
                        Text(stringResource(R.string.s_4d28e489), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // ── 底部按钮 ──
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { if (index > 0) index-- },
                    enabled = index > 0,
                ) {
                    Text(stringResource(R.string.s_1497b1bf), fontSize = 14.sp)
                }
                Spacer(Modifier.width(6.dp))
                // 「跳过」：**任何一步都能点**，直接结束向导进首页。
                // ⚠ 跳过也要 markDone（由 onFinish 的调用方负责），否则下次启动又被拦在这里。
                //   代价是"手表可能还没装"——用户明确要求给这条退路。
                AdbOutlineButton(onClick = { onFinish() }) {
                    Text(stringResource(R.string.s_00493320), fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
                val isLast = index == total - 1
                Button(
                    onClick = {
                        if (!isLast) {
                            index++
                        } else if (watchVer > 0 && latest.toLong() > watchVer) {
                            // ⚠ `watchVer > 0` 不能省：读不到版本时是 0，会误报"有新版"
                            // 已装但不是最新：提示一下（可跳过，不强制）
                            showUpdateTip = true
                        } else {
                            onFinish()
                        }
                    },
                    // ⚠⚠ 最后一步**装不上就不能完成**（用户要求：没检测到手表端就一直停在无线 ADB 这里）
                    // ⚠ 回看例外：那时手表早就装好了，只是可能没连着手表 ——
                    //   照搬这道门会让人在第 13 步看到一颗灰按钮，以为软件坏了。
                    enabled = !isLast || installed || replay,
                ) {
                    Text(
                        stringResource(if (!isLast) R.string.s_1497f4c8 else R.string.s_002f3ab4),
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

/** OneUI 8 打开开发者模式的教程页（5 步，带图）。 */
@Composable
private fun GuideOneUi8(onBack: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // ⚠⚠ 必须 `statusBarsPadding()`：不加的话标题会顶到状态栏下面被时钟压住
        //   （用户真机截图：14:07 直接盖在「首次使用引导」上）。
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ⚠ 用户 2026-10-27：这个「返回」只有文字、看着不像按钮 ⇒ 换成带描边的按钮
                AdbOutlineButton(onClick = onBack) {
                    Text("‹  " + stringResource(R.string.s_0049f05a), fontSize = 14.sp)
                }
                Spacer(Modifier.weight(1f))
            }
            Column(
                Modifier
                    .weight(1f)
                    .vScrollbar(
                        rememberScrollState(),
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
                    )
                    .padding(horizontal = 20.dp)
            ) {
                Text(
                    stringResource(R.string.s_a4749c1b),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                ONEUI_STEPS.forEachIndexed { i, (img, body) ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            "${i + 1}",
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(20.dp),
                        )
                        Text(stringResource(body), fontSize = 14.sp, lineHeight = 21.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    // 手表截图是**方形圆屏**，拉满整行两边会空一大片 ⇒ 限宽居中
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Image(
                            painter = painterResource(img),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth(0.62f)
                                .heightIn(max = 300.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(8.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}
