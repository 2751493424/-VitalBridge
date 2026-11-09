package com.gemini.healthbridge.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.healthbridge.phone.R

/**
 * **捐赠入口卡**（2026-11-09 用户要求）：放在「用户」页「个人」段**上面**。
 *
 * 用户原话：「做一个捐赠入口，这个入口放到软件的用户的个人前面，这样更醒目」。
 * ⇒ 它是 `LazyColumn` 的**第一项**，在「个人」这个分组标题之前 —— 不塞进设置组里，
 *   否则会和「用户资料 / 授权」混成同一组，失去"独立入口"的辨识度。
 *
 * ⚠ 文案里的「自愿」不是客套：捐赠页把三条边界写全（自愿 / 不换取权益 / 不退款），
 *   见 [DonateScreen]。入口这一层只给最短的副标，避免首页出现一段法律文字。
 */
@Composable
fun DonateEntryCard(onClick: () -> Unit) {
    AppCard(onClick = onClick, modifier = Modifier.padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(AppAccent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                // ⚠ 用项目既有的单字图标语言（`glyph_*`），不要用 ♥ 这类字符：
                //   它会被系统渲染成**彩色 emoji**，颜色不受 `color` 控制 ——
                //   在「深空青蓝」这种明确要求无杂色的主题里会突兀地出现一颗红心。
                Text(
                    stringResource(R.string.glyph_donate),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppAccent,
                )
            }
            Spacer(Modifier.width(12.dp))
            // ⚠ 左边这列必须 `weight(1f)`：中文副标在窄屏会折行，
            //   不给权重时 Row 会把它压成一条竖排（这毛病在项目里复发过三次）。
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    stringResource(R.string.s_a919bc12),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTextPrimary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.s_38bf2702),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = AppTextMuted,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("›", fontSize = 18.sp, color = AppTextMuted)
        }
    }
}

/**
 * **捐赠页**（2026-11-09）：整屏二级页，入口是「用户」页顶部的 [DonateEntryCard]。
 *
 * ## 为什么是页面、不是弹窗
 * 用户要求「点击后会弹出另一个新的页面」——与「授权」页同样是 `route`（不是新标签页）：
 * 底部导航仍常驻，返回由 `MainScreen` 的统一 `BackHandler` 退这一层。
 *
 * ## 这一屏只做「说明 + 一个出口」
 * 收款二维码**不放进 App**：图片会随包分发、改一次码就得出一次包，而且二维码属于
 * 支付工具自己的界面。按钮只负责用系统浏览器打开捐赠页（见 `MainActivity.DONATE_URL`）。
 *
 * ## 文案边界（照 [DonateScreen] 的三段写）
 * - 自愿：不捐赠也能用全部功能，没有功能限制、没有广告；
 * - 不构成购买：不换取商品、服务或功能特权；
 * - 不支持退款、不提供发票。
 *
 * ⚠ 这三条不是免责套话，而是"捐赠"与"收费"的分界 —— 少写一条，性质就变了。
 */
@Composable
fun DonateScreen(onBack: () -> Unit, onDonate: () -> Unit) {
    // ⚠ 用 `appPageBg()`：动态主题下这层必须留空，否则会盖掉根层那片会动的光。
    Box(Modifier.fillMaxSize().appPageBg()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            // 返回键 + 标题：与「今日概览」「授权」同一套（系统返回由 MainScreen 兜住）。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .border(1.dp, AppCardBorder, CircleShape)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("‹", fontSize = 18.sp, color = AppTextPrimary)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.s_a919bc12),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTextPrimary,
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 说明 ──
            AppCard {
                Text(
                    stringResource(R.string.s_40759f08),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = AppTextPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.s_a30868b3),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = AppTextPrimary,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.s_a725b039),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = AppTextMuted,
                )
            }

            Spacer(Modifier.height(12.dp))

            // ── 下一步怎么做 ──
            AppCard {
                Text(
                    stringResource(R.string.s_12393f02),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = AppTextMuted,
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── 唯一出口：用户点过这一下，才算"知道是自愿的" ──
            AppPrimaryButton(
                onClick = onDonate,
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(
                    stringResource(R.string.s_17b106fa),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.s_30641112),
                modifier = Modifier.fillMaxWidth(),
                fontSize = 11.sp,
                lineHeight = 15.sp,
                color = AppTextMuted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
