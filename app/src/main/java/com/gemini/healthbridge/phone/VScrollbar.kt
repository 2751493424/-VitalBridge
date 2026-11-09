package com.gemini.healthbridge.phone

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * `verticalScroll` + **右侧一条极细的滚动条**（叠在内容上，不占宽度）。
 *
 * ## 为什么必须有（用户 2026-10-27）
 *
 * 引导最后一步内容比屏幕高，「安装手表端脉迹」被挤到屏幕外；底部「完成」是灰的
 * （没装手表端不给点）⇒ 用户**以为有 bug**。
 * Android 的 `Modifier.verticalScroll` **不画任何滚动指示**（只有到边界的回弹），
 * 所以"下面还有内容"这件事得自己说出来。
 *
 * ## 为什么不用官方的 `VerticalScrollbar`
 *
 * 那个组件在 **compose-desktop 的 foundation** 里，Android 侧没有（`androidx.compose.foundation`
 * 的 Android 产物里查不到这个类）。所以这里按 `ScrollState` 自己画。
 *
 * ## 为什么用 `drawBehind` 而不是套一层 `Box`
 *
 * `verticalScroll` 节点的**大小就是视口**（内容被测量成无限高、再偏移绘制），
 * 所以在它身上 `drawBehind` 拿到的坐标空间正好是"可见区域"，
 * 画一条按比例定位的细条即可 —— 不用改布局层级（四处调用点都只是改一个修饰符）。
 *
 * ⚠ 读 `state.value` 是在 draw 阶段读的，Compose 会把它当依赖 ⇒ 滚动时自动重绘。
 * ⚠ 内容短到不用滚（`maxValue == 0`）时**什么都不画**，免得骗人。
 */
fun Modifier.vScrollbar(
    state: ScrollState,
    color: Color,
    thumbWidth: Dp = 4.dp,
    /** 距右边缘的距离（让开内容的自定义留白） */
    inset: Dp = 2.dp,
): Modifier = this
    // ⚠⚠ **顺序不能反**：修饰符里**先写的在外层**。`drawBehind` 必须在 `verticalScroll` **之前**，
    //   否则它画在**内容节点**（高度 = 全部内容）上 —— `size.height` 会变成内容总高，
    //   细条被算成"几乎不占屏"并跑到屏幕外，看着像"根本没画"。
    .drawBehind {
        val viewport = state.viewportSize
        val max = state.maxValue
        if (viewport <= 0 || max <= 0) return@drawBehind

        val w = thumbWidth.toPx()
        val h = (size.height * (viewport / (viewport + max).toFloat())).coerceAtLeast(40.dp.toPx())
        val travel = (size.height - h).coerceAtLeast(0f)
        val y = travel * (state.value.toFloat() / max).coerceIn(0f, 1f)
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - w - inset.toPx(), y),
            size = Size(w, h),
            cornerRadius = CornerRadius(w / 2f, w / 2f),
        )
    }
    .verticalScroll(state)
