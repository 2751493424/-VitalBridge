package com.gemini.healthbridge.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.healthbridge.phone.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch

/**
 * **主题**页（2026-11-06）。
 *
 * ## 交互
 *
 * 左右滑动切换预览，点「使用这个主题」才真正应用 —— **滑动不直接改全局配色**。
 * 理由：10 套主题一路滑过去会把整个 App 反复重染，用户滑到一半松手就停在一个
 * 他没想选的主题上；而且背景色突变在滑动过程中特别刺眼。
 *
 * ## 预览为什么不用图片
 *
 * 预览是**用真实组件画出来的**（同一套 `Card`/文字/按钮的尺寸与圆角），
 * 不是截图。好处：以后改卡片样式，预览自动跟着对；坏处：预览里那些数字是写死的假数据 ——
 * 所以只画形状与配色，不标"这是真实数据"。
 */
@Composable
fun ThemeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val themes = PhoneThemes.ALL
    val current = ThemeManager.current
    // ⚠ 停在「当前生效」那套：首次引导后进主题选择页时默认就落在深空青蓝，
    //   从用户页进来也直接看到自己正在用的主题，不必每次从第一套滑过去。
    val pager = rememberPagerState(
        initialPage = themes.indexOfFirst { it.id == current.id }.coerceAtLeast(0),
        pageCount = { themes.size },
    )

    Column(
        Modifier
            .fillMaxSize()
            .appPageBg()
            // ⚠ 这里**不要**再 `statusBarsPadding()`：这是 `Scaffold` 里的二级页，
            //   状态栏高度已经由 `topBar` 那段 `statusBarsPadding()` 占掉了，
            //   这里再加一次 = 标题被推下去 24dp（实测）。底部同理（`bottomBar` 已占）。
    ) {
        // ── 顶栏 ──
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                stringResource(R.string.s_0028a0c9),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AppTextPrimary,
            )
        }

        // ── 预览（左右滑动）──
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { index ->
            // ⚠ 2026-11-07：**所有页都动**（用户明确说性能不用在意）。
            //   翻页时左右两页也在缓慢流动，滑动过程才连贯；只动中间那页会看到"隔壁卡是死的"。
            //   `HorizontalPager` 默认只组合当前页 ± 1 页，所以实际最多 3 套动画在跑。
            ThemePreview(themes[index], animated = true)
        }

        // ── 名称 + 指示点 ──
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shown = themes[pager.currentPage]
            Text(
                shown.name,
                textAlign = TextAlign.Center,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTextPrimary,
            )
            // ⚠ 「动态」标在名字右边：用户这次就是**看不出哪几套会动**才问的
            //   （"那几个怎么不是动态的"）—— 预览页把它们排在最前面，再加这个标签才闭环。
            if (shown.motion != ThemeMotion.NONE) {
                Spacer(Modifier.width(7.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(AppAccent.copy(alpha = 0.16f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        stringResource(R.string.s_002aabf9),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppAccent,
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            themes.forEachIndexed { i, _ ->
                val on = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (on) 16.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(if (on) AppAccent else AppCardBorder),
                )
            }
        }

        // ── 应用按钮 ──
        Spacer(Modifier.height(16.dp))
        val picked = themes[pager.currentPage]
        val applied = picked.id == current.id
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(50))
                .background(if (applied) AppMutedFill else AppAccent)
                .clickable(enabled = !applied) { ThemeManager.select(ctx, picked) }
                .padding(vertical = 15.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(if (applied) R.string.s_150d4a1c else R.string.s_776f285b),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (applied) AppTextMuted else AppOnAccent,
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

/**
 * 一页预览：**用被预览主题自己的颜色**画一个小号首页。
 *
 * ⚠ 这里**不能**读 `AppBg`/`AppCardBg` 那些 token —— 那些是"当前生效主题"的颜色，
 *   画出来 10 页全一样，等于没预览。所有颜色都从 `t`（被预览主题）取。
 */
@Composable
private fun ThemePreview(t: PhoneTheme, animated: Boolean) {
    // ⚠ 预览卡**高度由内容决定**、在页内垂直居中。
    //   直接 `fillMaxSize()` 会让卡片被拉伸到整页高 —— 内容只占上面一半，
    //   下半张是一大片空白（实测过，像没画完）。
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .border(1.dp, t.border, RoundedCornerShape(26.dp)),
    ) {
    // ⚠⚠ 预览必须**用被预览主题自己的背景层现画**，不能是 `background(t.bg)` 一块平色：
    //   ① 动态主题的预览得会动，否则用户根本看不出它"动态"（这次就是为这个改的）；
    //   ② 玻璃主题的预览没有底下那层彩色柔光，就只是一块灰板。
    // ⚠ 这里**不能**传 `Modifier.matchParentSize()`：那个修饰符不参与尺寸计算，
    //   背景层自己会塌成 0 高，连带里面的预览内容一起消失（实测：整页只剩一片空白）。
    //   不传 modifier ⇒ 背景层按内容撑开，里面的 Canvas 自己 matchParentSize 铺满。
    // ⚠⚠ `grain = false`：预览小卡不需要那层肉眼不可见的细噪声；更重要的是
    //   整屏噪声位图是静态根层专用的，这里画一遍就等于每帧多一份整屏 blend。
    ThemeBackdropLayer(theme = t, animated = animated, grain = false) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(14.dp),
    ) {
        // 标题
        Text("脉迹", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = t.ink)
        Spacer(Modifier.height(10.dp))

        // 状态条（卡片底 + 描边）
        Column(
            Modifier
                .fillMaxWidth()
                .previewSurface(t, 12.dp)
                .padding(10.dp),
        ) {
            Text("手表在线：SM-L310", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = t.accent)
            Spacer(Modifier.height(3.dp))
            Text("今天已同步", fontSize = 10.sp, color = t.muted)
        }
        Spacer(Modifier.height(10.dp))

        // 两张并排的指标卡（模拟新的宫格卡：图标 + 名称 + 大数值 + 时间）
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewStatCard(t, "心率", "72", "次/分", Color(0xFFFF6B6B), Modifier.weight(1f))
            PreviewStatCard(t, "血氧", "97", "%", Color(0xFF5AA9FF), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))

        // 主按钮
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(t.accent)
                .padding(vertical = 11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "一键授权",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                // ⚠ 强调色可能是浅青（玻璃/极光），白字读不出来 —— 按亮度选深/浅字
                color = if (t.accent.luminance() > 0.45f) t.bg else Color.White,
            )
        }
        Spacer(Modifier.height(10.dp))

        // 底部导航的示意（选中态胶囊）
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf("功能", "无线ADB", "用户").forEachIndexed { i, s ->
                val on = i == 0
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 34.dp, height = 16.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (on) t.accentSoft else Color.Transparent),
                    )
                    Text(
                        s,
                        fontSize = 9.sp,
                        color = if (on) t.accent else t.muted,
                    )
                }
            }
        }
    }
    }
    }
    }
}

@Composable
private fun PreviewStatCard(
    t: PhoneTheme,
    label: String,
    value: String,
    unit: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .previewSurface(t, 14.dp)
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(5.dp)).background(accent.copy(alpha = 0.26f)))
            Spacer(Modifier.width(5.dp))
            Text(label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = t.ink)
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = t.ink)
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 8.sp, color = t.muted, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(3.dp))
        Text("11-06 08:56", fontSize = 8.sp, color = t.muted)
    }
}

/**
 * 预览里的小卡片外衣。
 *
 * ⚠ 玻璃主题必须走 `glassChrome`（上浓下淡 + 上亮下灭的边），
 *   不能只给 `t.card` 一块半透明白 —— 那样在预览小尺寸上看就是"没有卡"。
 */
@Composable
private fun Modifier.previewSurface(t: PhoneTheme, radius: Dp): Modifier {
    if (t.glass) return this.glassChrome(t.ink, radius)
    val shape = RoundedCornerShape(radius)
    return this
        .clip(shape)
        .background(t.card, shape)
        .border(1.dp, t.border, shape)
}
