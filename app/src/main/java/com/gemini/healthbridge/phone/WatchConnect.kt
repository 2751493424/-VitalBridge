package com.gemini.healthbridge.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.gemini.healthbridge.phone.ui.AppSurface

/**
 * 「看起来就是个按钮」的次要按钮。
 *
 * ⚠⚠ 这套主题里 `colorScheme.outline` 与卡片/页面底色几乎同色，
 * M3 默认的 `OutlinedButton` 边框**肉眼看不见** ⇒ 用户反馈"全是文字按钮"。
 * 所以这里显式给描边（`onSurface` 38%），尺寸和主按钮一致。
 */
@Composable
fun AdbOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        border = androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
        ),
        content = content,
    )
}

/** 输入框配色：默认 M3 的未聚焦边框在浅色主题下几乎看不见（像一行文字）。 */
@Composable
fun adbFieldColors() = OutlinedTextFieldDefaults.colors(
    // ⚠ `outline` 在这套浅色主题里与卡片底色几乎同色 ⇒ 直接压深
    unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

/**
 * 标题右边的**连接状态**（未连接 / 已连接）。
 *
 * ⚠ 2026-10-26 用户要求：状态**不要单独占一张卡**，固定在「脉迹」标题右边 ——
 *   "有没有连上"是全局状态，不是某一页的内容，做成卡片会把页面撑得很长。
 */
@Composable
fun WatchAdbStatusChip(modifier: Modifier = Modifier) {
    val phase by WatchAdb.phase.collectAsState()
    val remain by WatchAdb.remainMin.collectAsState()
    val connected = phase == WatchAdb.Phase.Connected || phase == WatchAdb.Phase.Busy
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .background(
                    if (connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.outline,
                    androidx.compose.foundation.shape.CircleShape,
                )
        )
        Spacer(Modifier.width(5.dp))
        Text(
            // ⚠ 连着时把**剩余分钟**一起显示：连接是 10 分钟租约（见 `WatchAdb.startKeeper`），
            //   不写出来用户会以为"又莫名其妙断了"。
            if (connected && remain > 0) stringResource(R.string.s_38b56ead, remain)
            else stringResource(if (connected) R.string.s_18e3a5c1 else R.string.s_1b4da0b9),
            fontSize = 12.sp,
            color = if (connected) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * **连接手表**这一块（扫描 + 手动连接），安装页与首次引导**共用同一份**。
 *
 * ⚠ 抽出来的原因：两处都要"扫不到就手输"，逻辑一样、文案一样；
 *   复制两份必然会漂移（这个项目已经在"同构页面必须成组改"上踩过）。
 *
 * ⚠⚠ 连接成功后**不做任何页面跳转**：调用方自己读 `WatchAdb.phase` 决定要不要往下走。
 *   向导靠它决定"能不能点完成"，安装页靠它决定"能不能点安装"。
 */
@Composable
fun WatchConnectBlock(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val phase by WatchAdb.phase.collectAsState()
    val devices by WatchAdb.devices.collectAsState()
    val err by WatchAdb.errorDetail.collectAsState()
    val pairingHint by WatchAdb.pairingHint.collectAsState()
    val notice by WatchAdb.notice.collectAsState()

    val rememberedHost by WatchAdb.lastHost.collectAsState()
    val rememberedPort by WatchAdb.lastPort.collectAsState()

    // 手动连接：IP / 端口 / 配对码
    // ⚠ 默认填**上次连成功的**地址：端口每次会变，但省掉重新敲 IP 这一步（用户要求记住设备）
    var mIp by remember { mutableStateOf(rememberedHost) }
    var mPort by remember { mutableStateOf(if (rememberedPort > 0) rememberedPort.toString() else "") }
    var code by remember { mutableStateOf("") }
    var pairedHint by remember { mutableStateOf(false) }
    /** 「配对」弹窗要对哪台设备（`null` = 没开） */
    var pairFor by remember { mutableStateOf<WatchAdb.Device?>(null) }
    var pairCode by remember { mutableStateOf("") }

    // ⚠ 用户 2026-10-27：「第一次配对时能不能自动把 IP/端口/配对码填进去」
    //   ⚠ 配对码是**手表屏幕上随机生成**的，手机读不到（Android 没有这个接口）⇒ 只能人来抄；
    //     但 IP 和**配对端口**都是 mDNS 能拿到的 ⇒ 这里自动填好，用户只剩"输 6 位数"一步。
    LaunchedEffect(devices) {
        val d = devices.firstOrNull { (it.pairingPort ?: 0) > 0 } ?: return@LaunchedEffect
        if (mIp.isBlank()) mIp = d.host
        if (mPort.isBlank()) {
            val p = d.connectPort.takeIf { it > 0 } ?: d.pairingPort ?: 0
            if (p > 0) mPort = p.toString()
        }
    }

    LaunchedEffect(rememberedHost, rememberedPort) {
        if (mIp.isBlank() && rememberedHost.isNotBlank()) mIp = rememberedHost
        if (mPort.isBlank() && rememberedPort > 0) mPort = rememberedPort.toString()
    }

    // 进页面就开扫（用户多半是先开这一页、再去手表上打开无线调试）
    // ⚠ 已经连着就别扫：`startScan` 会把 phase 打回 Scanning ⇒ 切页回来会显示"未连接"
    LaunchedEffect(Unit) {
        // ⚠ 先在 IO 上把 ADB 身份准备好（首次会生成 RSA 密钥，几十~几百毫秒）——
        //   否则这一步会落在 `startScan` 里、跑在主线程，表现为"点扫描卡一下"。
        WatchAdb.prepare(ctx)
        if (!WatchAdb.isConnected()) WatchAdb.startScan(ctx)
    }
    // ⚠ 离开只停**扫描**（mDNS 一直跑是白耗电），**绝不动连接**：
    //   连接是 10 分钟租约（`WatchAdb.startKeeper`），切页/切到后台都不许断 ——
    //   用户 2026-10-27 明确要求"只要后台还在运行就一直连着"。
    DisposableEffect(Unit) { onDispose { WatchAdb.stopScan() } }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // ── 扫描结果 ──
        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                when (phase) {
                    WatchAdb.Phase.Scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.s_260df3f2),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    WatchAdb.Phase.Connecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.s_d89c6fee), fontSize = 12.sp)
                    }
                    else -> if (devices.isEmpty()) {
                        Text(
                            stringResource(R.string.s_8767dcec),
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                devices.forEach { d ->
                    Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                if (d.connectPort > 0) "${d.host}:${d.connectPort}"
                                else "${d.host}:${d.pairingPort}",
                                fontSize = 13.sp, modifier = Modifier.weight(1f),
                            )
                            Button(
                                onClick = { scope.launch { WatchAdb.connectTo(d) } },
                                enabled = phase != WatchAdb.Phase.Connecting,
                            ) {
                                Text(stringResource(R.string.s_004a023f), fontSize = 12.sp)
                            }
                        }
                        // 扫到配对口 ⇒ 单独给一行「配对」：IP 和端口都自动带上，
                        // 用户只需要抄手表上那 6 位数字（这是唯一拿不到的东西）
                        val pp = d.pairingPort ?: 0
                        if (pp > 0) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    stringResource(R.string.s_584a62a3, pp),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                AdbOutlineButton(
                                    onClick = { pairCode = ""; pairFor = d },
                                    enabled = phase != WatchAdb.Phase.Connecting,
                                ) {
                                    Text(stringResource(R.string.s_004ab660), fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                err?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.s_cd027e34, it),
                        fontSize = 12.sp, color = Color(0xFFD32F2F),
                    )
                }
                // 连接到期 / 中途掉线：**不是错误**，用灰字说清"为什么断了"
                if (notice != 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(notice),
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(10.dp))
                // ⚠ 用户要求：这里不能是"只有文字"的按钮 —— 全部改成有边框的实体按钮
                AdbOutlineButton(
                    onClick = { WatchAdb.startScan(ctx) },
                    enabled = phase != WatchAdb.Phase.Connecting,
                ) {
                    Text(stringResource(R.string.s_a44053df), fontSize = 12.sp)
                }
            }
        }

        // ── 手动连接（扫不到时用）──
        // ⚠ 一直显示、不做折叠：mDNS 收不到很常见（AP 隔离/关多播），而手表屏幕上本来就写着 IP 和端口
        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    stringResource(R.string.s_7acf245d),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.s_541bcb07),
                    fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // 端口字段里现在是**配对端口**时，明说一句（否则用户会拿它当数据口点「连接」）
                val pairingPortNow = devices.firstOrNull {
                    (it.pairingPort ?: 0) > 0 && it.host == mIp.trim()
                }?.pairingPort ?: 0
                if (pairingPortNow > 0 && mPort.toIntOrNull() == pairingPortNow) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_c7f5da3e),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = mIp,
                        onValueChange = { mIp = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.s_0c569379), fontSize = 11.sp) },
                        colors = adbFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = mPort,
                        onValueChange = { mPort = it.filter { c -> c.isDigit() }.take(5) },
                        label = { Text(stringResource(R.string.s_003f3c30), fontSize = 11.sp) },
                        colors = adbFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(96.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { v -> code = v.filter { it.isDigit() }.take(6) },
                    label = { Text(stringResource(R.string.s_263bcb21), fontSize = 11.sp) },
                    colors = adbFieldColors(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                val portNum = mPort.toIntOrNull() ?: 0
                val ready = mIp.isNotBlank() && portNum in 1..65535 && phase != WatchAdb.Phase.Connecting
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = {
                            pairedHint = false
                            scope.launch {
                                val r = WatchAdb.manual(mIp, portNum, code)
                                // ⚠ 配对成功后**立刻清空配对码**：配对只需一次，
                                //   之后"连接"只用 IP+端口；留着会让人误以为还得再配一次
                                //   （用户明确要求，也别再自动带进下一次连接）。
                                if (r != WatchAdb.Manual.Failed) code = ""
                                pairedHint = r == WatchAdb.Manual.Paired
                            }
                        },
                        enabled = ready && code.length == 6,
                    ) {
                        Text(stringResource(R.string.s_004ab660), fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            pairedHint = false
                            scope.launch { WatchAdb.manual(mIp, portNum, null) }
                        },
                        enabled = ready,
                    ) {
                        Text(stringResource(R.string.s_004a023f), fontSize = 12.sp)
                    }
                }
                // ── 配对弹窗：IP + 配对端口都已经自动带上，用户只填 6 位码 ──
                pairFor?.let { dev ->
                    AlertDialog(
                        onDismissRequest = { pairFor = null },
                        title = { Text(stringResource(R.string.s_4b8963cb)) },
                        text = {
                            Column {
                                Text(stringResource(R.string.s_646afea5), fontSize = 13.sp)
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "${dev.host}:${dev.pairingPort}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(
                                    value = pairCode,
                                    onValueChange = { v ->
                                        pairCode = v.filter { it.isDigit() }.take(6)
                                    },
                                    label = { Text(stringResource(R.string.s_4b8963cb), fontSize = 11.sp) },
                                    colors = adbFieldColors(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.NumberPassword,
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val port = dev.pairingPort ?: return@Button
                                    val codeNow = pairCode
                                    pairFor = null
                                    scope.launch {
                                        val r = WatchAdb.manual(dev.host, port, codeNow)
                                        pairedHint = r == WatchAdb.Manual.Paired
                                    }
                                },
                                enabled = pairCode.length == 6,
                            ) {
                                Text(stringResource(R.string.s_004ab660), fontSize = 13.sp)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { pairFor = null }) {
                                Text(stringResource(R.string.cancel), fontSize = 13.sp)
                            }
                        },
                    )
                }

                if (pairedHint) {
                    // ⚠ 用户要求：配对成功必须"弹窗"，行内一行小字容易被划过去
                    AlertDialog(
                        onDismissRequest = { pairedHint = false },
                        title = { Text(stringResource(R.string.s_908e0a2f)) },
                        text = { Text(stringResource(R.string.s_86f5e38d), fontSize = 14.sp) },
                        confirmButton = {
                            TextButton(onClick = { pairedHint = false }) {
                                Text(stringResource(R.string.s_1faf4f0c))
                            }
                        },
                    )
                }
            }
        }
    }
}
