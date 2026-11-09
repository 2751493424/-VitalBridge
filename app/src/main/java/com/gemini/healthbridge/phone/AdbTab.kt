package com.gemini.healthbridge.phone

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.healthbridge.phone.ui.AppMutedFill
import com.gemini.healthbridge.phone.ui.AppSurface

/**
 * 底部导航第二页：**手表安装（无线 ADB）**。
 *
 * ## 结构（2026-10-26 按用户反馈收敛过一轮）
 *
 * - **连接状态不再单独占一张卡** —— 它固定在顶部「脉迹」标题右边（见 `MainActivity` 的
 *   `WatchAdbStatusChip`），因为"有没有连上"是**全局**状态，不是这一页的内容。
 * - 页面只剩三块：前提说明 → 连接（扫描/手动，和首导共用 [WatchConnectBlock]）→ 安装入口卡片。
 * - 安装动作挪到**独立页面** [WatchInstallScreen]（选本地包 / 一键装），这里只留一个入口。
 *
 * ⚠ 这一页**不再有一段"为什么不用蓝牙"的解释**（用户要求删掉）：那是实现理由，不是用户要读的信息。
 */
@Composable
fun AdbTab(modifier: Modifier = Modifier) {
    var showInstall by remember { mutableStateOf(false) }
    val ver by WatchAdb.watchVersion.collectAsState()
    val phase by WatchAdb.phase.collectAsState()

    if (showInstall) {
        WatchInstallScreen(onBack = { showInstall = false }, modifier = modifier.fillMaxSize())
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .vScrollbar(
                rememberScrollState(),
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.30f),
            )
            // ⚠ 底部要留出底部导航条的高度，否则最后一张「安装」卡片会被导航栏盖住
            .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        // ⚠ 页内大标题已撤（2026-10-27）：底部导航项就叫「无线ADB」，
        //   顶部再写一遍同样的字纯属噪音（用户要求"把安装功能改为无线ADB"）。
        // ── 前提 ──
        // 灰底提示条：**有意**不描边 —— 它讲的是"前提"，不是内容卡。
        AppSurface(
            Modifier.fillMaxWidth(),
            containerColor = AppMutedFill,
            borderColor = null,
        ) {
            Text(
                stringResource(R.string.s_d951a387),
                fontSize = 12.sp,
                modifier = Modifier.padding(12.dp),
            )
        }

        // ── 连接 ──
        WatchConnectBlock()

        // ── 安装入口 ──
        AppSurface(
            Modifier.fillMaxWidth(),
            onClick = { showInstall = true },
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            // 用户 2026-10-27：这张卡片叫「安装」（详情页标题才叫「无线ADB」）
                            stringResource(R.string.s_002f5fe0),
                            fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            when {
                                phase != WatchAdb.Phase.Connected && phase != WatchAdb.Phase.Busy ->
                                    stringResource(R.string.s_518a9efc)
                                ver > 0 -> stringResource(R.string.s_e5ef9d2a, "v$ver")
                                else -> stringResource(R.string.s_d9882205)
                            },
                            fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("›", fontSize = 22.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}
