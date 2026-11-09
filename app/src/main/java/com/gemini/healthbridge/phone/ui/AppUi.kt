package com.gemini.healthbridge.phone.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一的界面基元（2026-10-15 改版，方向见仓库根目录 `ui-preview.html`）。
 *
 * ## 为什么要有这一层
 *
 * 用户 2026-10-15 要求"按 shadcn-vue 的设计规范做 UI 美化，**交互也要做好**"，
 * 并先看预览确认了方向。App 本体是 Compose，所以这里把那份预览的结论落成组件：
 *
 * 1. **语义色 token**：界面里不再散落裸色值，中性色只走这里的几个名字
 *    （数据色仍在 `MainActivity` 顶部分区定义 —— 它们各自带医学/口径理由，不能合并）。
 * 2. **卡片四段式**：标题 + 副标题 + 内容 + 脚注，一屏只讲一件事；
 *    卡片之间 12px 间距分组，不再靠分隔线堆叠。
 * 3. **字阶固定**：主数字 32/700、卡题 14/600、说明 12、注释 11。
 *
 * ⚠ 这一层**只收外观与布局**，不收业务逻辑：卡片不自己取数、不算分、
 *    不判断"正常/异常"（判定口径在 core，别在 UI 再造一份）。
 */

// ══════════════════ 语义色（**运行时可变**，2026-11-06 主题化） ══════════════════

// ⚠⚠ 这里全部是 `get()` 属性，值来自 `ThemeManager.current`（`mutableStateOf`）。
//   为什么这么写：之前是顶层 `val = Color(0x…)`，编译期固定，换配色只能改代码重出包。
//   改成 getter 之后，**所有引用点一行都不用改**，而 Compose 读到 State 会在换主题时自动重组。
//   ⚠ 千万别把某个颜色 `val x = AppBg` 缓存在别处 —— 那会永远停在切换前的值，且不报错。

/** 页面底色。 */
val AppBg: Color get() = ThemeManager.current.bg

/** 卡片底色。玻璃主题下是**半透明白**，底下的页面底色会透出来。 */
val AppCardBg: Color get() = ThemeManager.current.card

/** 卡片描边。⚠ 1px 描边而不是阴影：低端机上阴影糊、且深色下会发灰。 */
val AppCardBorder: Color get() = ThemeManager.current.border

/** 主文字。 */
val AppTextPrimary: Color get() = ThemeManager.current.ink

/** 次级文字（卡题旁的说明、行内标签）。 */
val AppTextSecondary: Color get() = ThemeManager.current.ink2

/** 弱文字（脚注、注释、"暂无数据"）。 */
val AppTextMuted: Color get() = ThemeManager.current.muted

/** 分隔线。 */
val AppDivider: Color get() = ThemeManager.current.divider

/** 图表网格线（比分隔线更淡，免得抢了柱子）。 */
val AppGridLine: Color get() = ThemeManager.current.divider.copy(alpha = 0.45f)

/**
 * 底色块（统计格、Tabs 底色、Skeleton）。比卡片亮/暗一档。
 *
 * ⚠ 与页面底色的差距必须够大：深色下"亮一档"的幅度要比浅色更大，
 *   差 7 个色阶在手机屏上根本看不出边界（2026-11-06 实测过一次）。
 */
val AppMutedFill: Color get() = ThemeManager.current.tile

/** 强调色：主按钮填充、选中态、图例高亮。 */
val AppAccent: Color get() = ThemeManager.current.accent

/** 强调色的深一档（选中胶囊底色 / 按钮按下态）。 */
val AppAccentSoft: Color get() = ThemeManager.current.accentSoft

/**
 * **小控件底**（圆形 / 胶囊按钮的填充，2026-11-07）。
 *
 * ⚠⚠ 玻璃主题下**必须用暗底**：它的背景是明亮的彩色光，`AppMutedFill`（半透明白）叠上去
 *   还是亮的，白字压在上面等于没有（用户：「右上角的按钮都看不清了」）。
 *   其余主题用 `AppMutedFill`（深色主题=深紫灰、浅色=米白），与文字色天然成对。
 *
 * ⚠ 2026-11-07 深空青蓝方案里**刻意保留纯黑半透明**（没换成蓝色）：底色已经是
 *   `#080B12`，纯黑叠 36% 与「深蓝叠 36%」肉眼几乎无差，而这里写 `Color(0x…)` 会
 *   撞上静态校验脚本的「组件体禁止裸色值」门禁；小控件要的是「更暗」不是「更蓝」。
 */
val AppControlFill: Color
    get() = if (ThemeManager.current.glass) {
        Color.Black.copy(alpha = 0.36f)
    } else {
        ThemeManager.current.tile
    }

/**
 * **强调色上的文字色**（2026-11-06）。
 *
 * ⚠⚠ 不能一律用白色：强调色不是每套都深。玻璃是冷青 `#6FE3FF`、极光是薄荷 `#4FD1C5`，
 *   白字压上去**几乎读不出来**（实测主题页那颗「使用这个主题」按钮，字和底一个亮度）。
 *   ⇒ 按强调色的相对亮度定：亮的用深字（取主题底色），暗的用白字。
 */
val AppOnAccent: Color
    get() = if (ThemeManager.current.accent.luminance() > 0.45f) {
        ThemeManager.current.bg
    } else {
        Color.White
    }

// ══════════════════ 间距 / 圆角 / 高度 ══════════════════

/** 卡片内边距。 */
val AppCardPadding = 16.dp

/** 卡片之间的纵向间距。 */
val AppCardGap = 12.dp

/** 卡片圆角。⚠ 10~12px 之间：再大在手机上显得糊，再小又回到"方块"时代。 */
val AppCardRadius = 10.dp

/** 统计格圆角（比卡片小一档，视觉上从属于卡片）。 */
val AppTileRadius = 8.dp

/**
 * **玻璃外衣**（2026-11-07 按用户给的「深空青蓝」参数重做）。
 *
 * ## 为什么不能只靠「半透明底色」
 * 第一版给卡片一块平色 ⇒ 用户「做得也不好，毛玻璃的质感」。平色 + 纯黑底 = 一块灰板，
 * 四边一样亮，没有厚度、没有光。
 *
 * ## 三层结构
 * ① **透出「我背后那一截背景」**：向 [ThemeBackdropLayer] 问原点 / 整屏尺寸 / 相位，
 *    把自己在屏幕上的绝对位置换算成背景层内坐标，再把那一块背景重画进绘制区。
 *    ⚠ 别用「给卡片叠一层彩色渐变」糊弄：那样每张卡颜色一样，一眼就是假的。
 * ② **玻璃片本身**：`theme.card` = `rgba(22,32,48,0.42)`，一层 42% 的深蓝。
 *    ⚠ 这一层同时是卡内所有白字的**对比度来源**：旧版用半透明白涂层把卡片提亮，
 *    白字在明亮的彩光上直接发飘（用户「按钮都看不清了」）。深蓝玻璃才是对的。
 * ③ **淡蓝描边 + 上沿高光**：`theme.border` = `rgba(140,200,240,0.18)` 是**峰值**，
 *    往下递减到几乎消失，像光从上沿打进来。
 *
 * ⚠⚠ 位置与相位都从 **State** 里读，在**绘制阶段**取值 ⇒ 背景每帧在动但卡片不重组。
 * ⚠ 磨砂 = 对背后细节做 `taps=6` 次黄金角抖动采样，抖动半径 `7.dp`：规格里的
 *   blur 16px 在 1080p 设备上约合 5.8dp，实测 7dp 才糊得开 3~8dp 的光点。
 *   ⚠ 别改成 16 物理像素 —— 比光点半径还小，等于没糊。
 * ⚠ `edge` 只在主题没给 `border` 时兜底（以后浅色主题要玻璃质感时不会画出白边）。
 */
@Composable
fun Modifier.glassChrome(edge: Color, radius: Dp): Modifier {
    val theme = LocalBackdropTheme.current ?: ThemeManager.current
    val phaseState = LocalBackdropPhase.current
    val originState = LocalBackdropOrigin.current
    val bgSize = LocalBackdropSize.current
    val shape = RoundedCornerShape(radius)
    // ⚠⚠ 直接读主题规格色，别在函数里现调白 / 黑：那套旧配方在 #080B12 上发灰，
    //   也和 rgba(22,32,48,0.42) / rgba(140,200,240,0.18) 对不上。
    val glassFill = theme.card
    val glassEdge = if (theme.border == Color.Transparent) edge.copy(alpha = 0.18f) else theme.border
    // 卡片自己在根坐标系里的位置。⚠ 只能在**绘制阶段**读：
    //   滚动时它每帧都在变，若在组合期读 ⇒ 列表每滚一帧整屏重组。
    val pos = remember { mutableStateOf(Offset.Unspecified) }

    return this
        .onGloballyPositioned { pos.value = it.positionInRoot() }
        .clip(shape)
        .drawBehind {
            val p = pos.value
            if (p != Offset.Unspecified && bgSize.width > 0) {
                // 把「整屏背景」按卡片位置平移进来 —— 画出来的正好是卡片背后那一截。
                // ⚠ taps=6 + 半径 7dp：只糊细节层，底下的柔光本来平滑，多采是白烧 GPU。
                drawThemeBackdrop(
                    t = theme,
                    phase = phaseState.value,
                    canvas = Size(bgSize.width.toFloat(), bgSize.height.toFloat()),
                    shift = Offset(p.x - originState.value.x, p.y - originState.value.y),
                    taps = 6,
                    tapRadius = 7.dp.toPx(),
                    vignette = false,
                )
            }
            // ① 玻璃片：42% 的深蓝（22,32,48）。既是「材质」，也是白字的暗底。
            drawRect(glassFill)
            // ② 上沿高光：淡蓝、只有描边峰值的一半多，45% 高度内衰减掉。
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(glassEdge.copy(alpha = 0.10f), Color.Transparent),
                    endY = size.height * 0.45f,
                )
            )
            // ③ 左上镜面高光：让人相信「这块东西是硬的」
            drawRect(
                Brush.linearGradient(
                    colors = listOf(glassEdge.copy(alpha = 0.06f), Color.Transparent),
                    start = Offset.Zero,
                    end = Offset(size.width * 0.8f, size.height * 1.1f),
                )
            )
            // ④ 卡片自身的一道光缓慢扫过：玻璃是活在光里的材质，静止高光像贴纸。
            //   ⚠ 与背景共用同一个相位，所以和背景不会各走各的。
            run {
                val ph = phaseState.value
                // ⚠⚠ 正弦往复、行程两端都在卡片外 ⇒ 掉头看不见；⛔ 不许改回取模
                //   （`(ph*1.6f)%1f` 在时间回绕时会把这道光瞬移回起点）。
                val sweep = 0.5f + 0.5f * sinWave(ph, 0.75f, 0.13f)
                val bandHalf = size.width * 0.42f
                val travel = (size.width + size.height) * 1.6f
                val along = sweep * travel - travel * 0.35f
                val diag = 0.62f
                val cx = along * diag
                val cy = along * (1f - diag) * 0.6f
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            glassEdge.copy(alpha = 0.054f),
                            Color.Transparent,
                        ),
                        start = Offset(cx - bandHalf, cy - bandHalf * 0.35f),
                        end = Offset(cx + bandHalf, cy + bandHalf * 0.35f),
                    )
                )
            }
            // ⑤ 底部压暗：不透光的那一侧要沉下去，卡片才有厚度
            drawRect(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                    startY = size.height * 0.62f,
                )
            )
        }
        // ⑥ 描边：上亮（峰值恰为 0.18）→ 中淡 → 底几乎消失
        .border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                listOf(
                    glassEdge,
                    glassEdge.copy(alpha = 0.08f),
                    glassEdge.copy(alpha = 0.025f),
                ),
            ),
            shape = shape,
        )
}

/**
 * 统一卡片：**标题 + 副标题 + 内容 + 脚注** 四段式。
 *
 * ⚠ 与裸 `Card { Column(padding(16)) }` 的区别不只是少写几行：
 *   · 间距、圆角、描边、字阶全部收在这里，改一处全 App 生效；
 *   · `onClick` 存在时整卡可点（带水波纹），用于"点进去看详情"那类卡；
 *   · 没有标题时会自动退化成"纯内容卡"（统计格那类），不会留一片空白。
 *
 * @param onClick 传了才可点；不传就是静态卡（避免误触）。
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    footer: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    // 与 `AppSurface` 同一套：玻璃走毛玻璃，其余走"实底 + 本卡位置上的主题光"
    val glass = ThemeManager.current.glass
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (glass) Modifier.glassChrome(AppTextPrimary, AppCardRadius)
                else Modifier.cardSurfaceBg(AppCardRadius, null),
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(AppCardRadius),
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent,
            // 同上：透明容器不给 contentColor ⇒ 卡内未指定颜色的文字变黑
            contentColor = AppTextPrimary,
        ),
        border = if (glass) null else BorderStroke(1.dp, AppCardBorder),
    ) {
        Column(Modifier.padding(AppCardPadding)) {
            if (title != null || trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (title != null) {
                        Text(
                            title,
                            // ⚠ 2026-11-04 由 14 提到 15：卡片标题与正文（13/12）只差 1~2sp，
                            //   扫读时几乎分不出层级；15/600 与正文 13/400 的对比才够。
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = AppTextPrimary,
                            // ⚠ 允许折两行：英文标题（`Overnight wrist temperature deviation`）
                            //   一行放不下，早先那种 `maxLines = 3` 的卡就是这么来的。
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (trailing != null) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                            trailing()
                        }
                    } else if (title != null) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = AppTextMuted)
            }
            if (title != null || subtitle != null) {
                Spacer(Modifier.height(10.dp))
            }
            content()
            if (footer != null) {
                Spacer(Modifier.height(8.dp))
                Text(footer, fontSize = 11.sp, color = AppTextMuted)
            }
        }
    }
}

/**
 * **统一容器**（2026-11-04）：所有"卡"的唯一正确来源。
 *
 * ## 为什么需要它 —— 不只是省几行
 * `AppCard` 解决了"有标题的卡"，但它强制四段式结构（标题/副标题/内容/脚注），
 * 于是那些**自带标题排版**的卡（表格行、KPI、图表面板）只能写裸 `Card {}`。
 * 裸 `Card` 在 Material 3 里读的是 `surfaceContainerHighest` ——
 * 本项目该 token 是 `#E9E9EE` 灰底、**无描边**；而 `AppCard` 是白底 + 1px 描边。
 * 两者并排就是"灰方块和白卡片混着用"，一屏里能同时出现七八张。
 *
 * ⇒ 这里把 `AppCard` 的**外观部分**单独抽出来，标题结构留给 `AppCard`。
 *   判据：**只要画的是"卡"（浮在页面底上、有边界），就必须走这两个之一**；
 *   裸 `Card` 一律视为遗漏。
 *
 * @param containerColor 底色。默认白；填色提示卡（如回退横幅）自己给色。
 * @param borderColor 描边色。**有底色时传 `null`** —— 深色填充配白描边会显脏。
 * @param onClick 传了整卡可点（水波纹会被圆角裁切）；不传就是静态卡。
 */
@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    containerColor: Color = AppCardBg,
    borderColor: Color? = AppCardBorder,
    /** 圆角。⚠ 默认 `AppCardRadius`(10dp)；**功能宫格那种"大格子"传 18dp** —— 深色底上 10dp 显方。 */
    radius: Dp = AppCardRadius,
    /** 类别色（功能卡用）：只在卡片一角留一丝，用来区分同类卡片。普通卡不传。 */
    category: Color? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    // ⚠ 只有"用默认卡片底"的卡才动底：填色提示卡（回退横幅、警告块）自己传了 `containerColor`，
    //   那层颜色本身带语义（"这是提醒"），改底会把语义抹掉、还会和底下的光撞色。
    val custom = containerColor != AppCardBg
    val glass = ThemeManager.current.glass && !custom
    Card(
        modifier = modifier
            .then(
                when {
                    glass -> Modifier.glassChrome(AppTextPrimary, radius)
                    custom -> Modifier          // 自定义底色的卡：原样
                    else -> Modifier.cardSurfaceBg(radius, category)
                },
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(radius),
        colors = CardDefaults.cardColors(
            containerColor = if (glass || !custom) Color.Transparent else containerColor,
            // ⚠⚠ 透明容器必须同时给 `contentColor`：`contentColorFor(Transparent)` = Unspecified
            //   ⇒ 卡内所有没写 `color` 的 `Text` 会画成黑色（深色主题上等于消失）。
            contentColor = AppTextPrimary,
        ),
        border = if (glass) null else borderColor?.let { BorderStroke(1.dp, it) },
        content = content,
    )
}

/**
 * **卡片底**（2026-11-07，所有卡共用）。
 *
 * ## 问题
 * 用户原话：「这些功能卡片的背景应该也一起跟随主题变化，不要一直是这样的」。
 * 因为卡片底一直读 `AppCardBg`，而**深色主题的 `card` 全是"低饱和暗板岩"**——
 * 极光 `#162030`、曜金 `#18181A`、深空灰 `#2C2E32`，彼此只差几个色阶；背景已经变成
 * 青碧/玫瑰了，卡片还是那块暗色，看着就是"没跟着换"。
 *
 * ## 做法：卡片取"自己这块位置背后的那团光"
 * 不做"给每套主题配一个卡片渐变"——那要维护 11 份，而且**固定渐变会和类别色打架**
 * （试过强调色对角渐变：极光下卡片变紫、压在青底上比原来更难看）。
 * ⇒ 复用玻璃那套坐标：把背景的**光**（不画底）按卡片位置重画一遍，压在 `card` 实底上，
 *   强度 0.55。卡片于是自动跟背景同一个色温、同一个方向，换主题/换光位都不用改代码。
 *
 * ⚠ 静态主题（紫夜/深空灰/午夜蓝/松林绿/陶瓷白/暖砂）**没有光可采** ——
 *   它们的 `card` 本来就是该主题的色（松林绿是绿的、午夜蓝是蓝的），留实底即可。
 * ⚠ 玻璃主题整段跳过：那张卡在透背景（`glassChrome`），再叠一次是重复染色。
 * ⚠ 必须在 `drawBehind` 之前 `clip`：不然底色和光会溢出圆角。
 *
 * @param category 类别色（心率红 / 血氧蓝…）。只在一角留一丝，用来区分同类卡片；
 *   给 `null` 就是普通卡。⚠ 强度按明暗主题分档：浅色白底上 0.07 的红已经很明显了。
 */
@Composable
private fun Modifier.cardSurfaceBg(radius: Dp, category: Color?): Modifier {
    val t = ThemeManager.current
    val theme = LocalBackdropTheme.current ?: t
    val phaseState = LocalBackdropPhase.current
    val originState = LocalBackdropOrigin.current
    val bgSize = LocalBackdropSize.current
    // 卡片在根坐标系里的位置：只能在**绘制阶段**读（滚动时每帧都变，组合期读会整层重组）
    val pos = remember { mutableStateOf(Offset.Unspecified) }
    val catAlpha = if (t.isLight) 0.035f else 0.07f

    return this
        .onGloballyPositioned { pos.value = it.positionInRoot() }
        .clip(RoundedCornerShape(radius))
        .drawBehind {
            // ① 主题给的实底
            drawRect(t.card)
            // ② 叠"我这块位置背后"的主题光（静态主题没有光，跳过）
            val p = pos.value
            if (theme.motion != ThemeMotion.NONE && p != Offset.Unspecified && bgSize.width > 0) {
                drawThemeBackdrop(
                    t = theme,
                    phase = phaseState.value,
                    canvas = Size(bgSize.width.toFloat(), bgSize.height.toFloat()),
                    shift = Offset(p.x - originState.value.x, p.y - originState.value.y),
                    strength = 0.55f,
                    glowOnly = true,
                )
            }
            // ③ 上沿一抹极淡的高光：卡片和背景同色温之后，边界只靠描边撑着会显平；
            //   这一层是"光从上沿打进来"，卡片立刻有"面"的实感（浅色主题不需要，白底已经很亮）
            if (!t.isLight) {
                drawRect(
                    Brush.verticalGradient(
                        colors = listOf(t.ink.copy(alpha = 0.055f), Color.Transparent),
                        endY = size.height * 0.55f,
                    )
                )
            }
            // ④ 类别色只留一丝
            if (category != null) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(category.copy(alpha = catAlpha), Color.Transparent),
                        center = Offset(size.width * 0.88f, size.height * 1.05f),
                        radius = size.width * 0.95f,
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width * 0.88f, size.height * 1.05f),
                )
            }
        }
}

/**
 * **卡内格**（[AppSurface] 的从属变体）：白卡**里面**的一方小格。
 *
 * ⚠⚠ 必须用灰底、**不能**用 `AppSurface`：白卡里再放白卡 ⇒ 边框消失、
 *   只剩两条白边叠在一起，用户看到的是"这里怎么空了一块"。
 *   （睡眠得分因素那四格就是这种情况：它们在 `AppCard` 内部。）
 *
 * 圆角比卡片小一档（[AppTileRadius]），视觉上从属于宿主卡片，不制造新层级。
 */
@Composable
fun AppTile(
    modifier: Modifier = Modifier,
    containerColor: Color = AppMutedFill,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    // ⚠ 玻璃主题下卡内格也要有边：卡本身是半透明白，格子只是"更亮一点的半透明白"，
    //   两者 alpha 只差一点，没边就分不出"这是一格"（实测像一片糊）。
    val glass = ThemeManager.current.glass && containerColor == AppMutedFill
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(AppTileRadius),
        // `AppMutedFill` 也不在配色方案里 ⇒ 同样要把正文色显式传下去
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = AppTextPrimary,
        ),
        border = if (glass) BorderStroke(1.dp, AppTextPrimary.copy(alpha = 0.07f)) else null,
        content = content,
    )
}

/**
 * **软底块**：把一段"要看见、但不该抢眼"的内容收进浅灰底。
 *
 * ## 为什么用它而不是再加一张卡
 * 卡片套卡片会立刻变成"框里的框"（本项目《UI》里那条『一屏只讲一件事』的反面）。
 * 软底块只是**同一张卡内的分组**：底色比页面底深一档、圆角比卡片小一档
 * （[AppTileRadius]），视觉上从属于卡片，不制造新的层级。
 *
 * 典型用途：一句结论 + 一句解释（如"还差 8 组配对"、空态引导），
 * 让它在满屏灰字里成为一个清晰的落点。
 */
@Composable
fun AppSoftBlock(
    modifier: Modifier = Modifier,
    background: Color = AppMutedFill,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(AppTileRadius))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        content = content,
    )
}

/**
 * **发丝线**：1px 分隔。
 *
 * ⚠ 用 1dp 的 `Box` 而不是 `Divider()`：Material 的 `Divider` 默认 1dp 但带主题色，
 *   在浅色卡片上偏深、像一条"实线"，这里要的是"几乎看不见的分组线"。
 */
@Composable
fun AppHairline(modifier: Modifier = Modifier, color: Color = AppDivider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** [AppCard] 内容作用域的别名（避免调用方 import ColumnScope）。 */
typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

/**
 * 主数字（KPI）：大数字 + 单位 + 一行说明。
 *
 * ⚠ 数字用 `FontWeight.Bold` + 负字距（`letterSpacing`），并配 `tabular-nums`
 *   —— 刷新时数字不跳宽，否则"心率从 67 变 88"会看着抖一下。
 */
@Composable
fun AppKpi(
    value: String,
    unit: String,
    caption: String? = null,
    valueColor: Color = AppTextPrimary,
) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor,
                letterSpacing = (-0.5).sp,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                unit,
                fontSize = 13.sp,
                color = AppTextMuted,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        if (caption != null) {
            Spacer(Modifier.height(4.dp))
            Text(caption, fontSize = 12.sp, color = AppTextSecondary)
        }
    }
}

/**
 * 小统计格（三格一排那类）。
 *
 * ⚠ 底色块而不是描边块：一排三个描边小方块会显得碎；
 *   底色块在视觉上从属于卡片，也更容易扫读。
 */
@Composable
fun AppStat(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = AppTextPrimary,
) {
    Column(
        modifier
            .background(AppMutedFill, RoundedCornerShape(AppTileRadius))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, fontSize = 10.sp, color = AppTextMuted)
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
        )
    }
}

/** 三个统计格横排（最常用的一排）。 */
@Composable
fun AppStatRow(
    a: Triple<String, String, Color>,
    b: Triple<String, String, Color>,
    c: Triple<String, String, Color>,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        AppStat(a.first, a.second, Modifier.weight(1f), a.third)
        AppStat(b.first, b.second, Modifier.weight(1f), b.third)
        AppStat(c.first, c.second, Modifier.weight(1f), c.third)
    }
}

/**
 * 图例（色块 + 文字，横排）。
 *
 * ⚠ 图例一律置底、不用"图上直接标文字"：柱子密的时候图上文字会互相压，
 *   而图例是恒定不动的参照。
 */
@Composable
fun AppLegend(items: List<Pair<Color, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        for ((color, label) in items) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(8.dp)
                        .height(8.dp)
                        .background(color, RoundedCornerShape(2.dp))
                )
                Spacer(Modifier.width(4.dp))
                Text(label, fontSize = 11.sp, color = AppTextMuted)
            }
        }
    }
}

/** 卡内说明行（12sp 次级文字），用于"这条线为什么稀疏"这类必须说清的限制。 */
@Composable
fun AppHint(text: String) {
    Text(text, fontSize = 11.sp, color = AppTextMuted)
}

/** 卡内一行"标签 —— 值"。 */
@Composable
fun AppRowLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = AppTextMuted)
        Text(value, fontSize = 12.sp, color = AppTextSecondary)
    }
}

/** 可点的入口行（"最近记录 ›"那类）。 */
@Composable
fun AppEntryRow(text: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, color = AppTextPrimary, modifier = Modifier.weight(1f))
        Text("›", fontSize = 16.sp, color = AppTextMuted)
    }
}

/**
 * 点某一格柱子弹出的小详情（2026-10-15）。
 *
 * ⚠⚠ `AlertDialog` 的 `text` 槽内部是 **Box（堆叠）**，不是 `Column` ——
 *    平级传多个 Composable 会**重叠**（这个项目踩过，静态校验脚本钉着）。
 *    所以这里只传**一个** `Column`，里面再排三行。
 *
 * @param unit 单位（心率「次/分」/ 血氧「%」）。由调用方给，这里不做任何判定。
 */
@Composable
fun HourDetailDialog(
    hour: Int,
    min: Int,
    max: Int,
    avg: Int,
    unit: String,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("%02d:00".format(hour), fontSize = 16.sp, fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                DetailLine("最低", "$min $unit")
                DetailLine("最高", "$max $unit")
                DetailLine("平均", "$avg $unit")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = AppTextMuted)
        Text(value, fontSize = 13.sp, color = AppTextPrimary, fontWeight = FontWeight.Medium)
    }
}

// ══════════════════ 全局主题（一处改、全 App 生效） ══════════════════

/**
 * 全局圆角：卡片 10dp（[AppCardRadius]），其余按 Material3 的阶梯往下排。
 *
 * ⚠ 之所以能"一处改全生效"：`MainActivity.setContent` 里那个
 *   `MaterialTheme(shapes = …)` 是所有 `Card` 的 shape 来源。
 *   在此之前每张卡都用默认 12dp，改一处要动 80 多个调用点。
 */
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(AppCardRadius),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * 全局配色方案（**按当前主题构造**，2026-11-06 主题化）。
 *
 * ⚠⚠ 不能再用顶层 `val AppColorScheme = darkColorScheme(...)`：那样只在首次组合时求一次值，
 *   换主题后 `Material` 组件（底部导航、弹窗、TextField）**不会跟着变**，
 *   而自己画的卡片却变了 —— 表现为"换了主题，导航栏还是旧颜色"，且**不报错**。
 *
 * ⚠ 浅色主题（陶瓷白/暖砂）必须走 `lightColorScheme`：否则输入框、下拉、波纹的默认色
 *   仍是深色版的那套，压在米白底上像是坏的。
 */
@Composable
fun appColorScheme(): ColorScheme {
    val t = ThemeManager.current
    val accent = t.accent
    // ⚠ 按**强调色的亮度**定文字色（与 `AppOnAccent` 同一条判据），不要按主题深浅：
    //   玻璃/极光的强调色本身很亮，压白字等于没字；反过来浅色主题的金色强调也偏暗。
    val onAccent = if (accent.luminance() > 0.45f) t.bg else Color.White
    return if (t.isLight) {
        androidx.compose.material3.lightColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = t.accentSoft,
            onPrimaryContainer = t.ink,
            secondary = t.ink2,
            onSecondary = Color.White,
            secondaryContainer = t.tile,
            onSecondaryContainer = t.ink,
            background = t.bg,
            onBackground = t.ink,
            surface = t.card,
            onSurface = t.ink,
            surfaceVariant = t.tile,
            onSurfaceVariant = t.muted,
            // ⚠ `surfaceContainer*` 阶梯必须逐个给：底部导航栏用 `surfaceContainer`、
            //   默认 Card 用 `surfaceContainerHighest`。漏一个就回退到 M3 基线色板。
            surfaceContainerLowest = t.card,
            surfaceContainerLow = t.bg,
            surfaceContainer = t.tile,
            surfaceContainerHigh = t.card,
            surfaceContainerHighest = t.tile,
            outline = t.border,
            outlineVariant = t.divider,
        )
    } else {
        androidx.compose.material3.darkColorScheme(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = t.accentSoft,
            onPrimaryContainer = t.ink,
            secondary = t.ink2,
            onSecondary = t.bg,
            secondaryContainer = t.tile,
            onSecondaryContainer = t.ink,
            background = t.bg,
            onBackground = t.ink,
            surface = t.card,
            onSurface = t.ink,
            surfaceVariant = t.tile,
            onSurfaceVariant = t.muted,
            // 阶梯语义：Lowest 最暗 → Highest 最亮（与浅色**相反**，别照抄）。
            surfaceContainerLowest = t.bg,
            surfaceContainerLow = t.bg,
            surfaceContainer = t.tile,
            surfaceContainerHigh = t.tile,
            surfaceContainerHighest = t.tile,
            outline = t.border,
            outlineVariant = t.divider,
        )
    }
}

// ══════════════════ 按钮（统一外观） ══════════════════

/** 描边按钮的描边色：比卡片描边深一档，**看得见**才算按钮。 */
private val AppButtonOutline = Color(0xFF4A3A60)

/**
 * 统一按钮（2026-10-15）。
 *
 * ## 为什么要有它
 *
 * 用户截图里「授权读取 / 重新读取」两个**确实是按钮**（`OutlinedButton`），
 * 但画出来像两段普通文字 —— Material3 的 outline 用 `outline` token（很浅），
 * 在浅色卡片上几乎看不见描边。同一屏里「‹ 返回」也是 `OutlinedButton`，
 * 两处看着却不像同一种东西。
 *
 * ⇒ 全 App 只留这两个入口：**填充（主动作）/ 描边（次动作）**，
 *   形状、高度、字号、内边距、描边色全部一致。
 *
 * ⚠ 参数顺序刻意与 M3 的 `Button` / `OutlinedButton` 相同
 *   （`onClick` → `modifier` → `enabled` → content），这样调用点的替换是**纯机械替换**，
 *   不用改任何一处实参写法。
 *
 * @param content 用 `RowScope` 接收，与 M3 一致 —— 老的 `Button { Text(...) }` 体原样可用。
 */
@Composable
fun AppPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    // ⚠ 这几个参数名与 M3 的 `Button` **完全同名**：调用点里已经写着
    //   `colors = …` / `contentPadding = …` 的地方才能原样搬过来（机械替换的前提）。
    colors: ButtonColors = ButtonDefaults.buttonColors(
        // ⚠⚠ 2026-11-06 深色化：原来这里是 `AppTextPrimary`（当时是近黑）。
        //   翻转成深色后 `AppTextPrimary` 变近白 ⇒ 近白底 + 白字 = **按钮整块消失**。
        //   ⇒ 主按钮改用强调色 `AppAccent`。
        containerColor = AppAccent,
        // ⚠ 用 `AppOnAccent` 而不是 `Color.White`：强调色可能是**浅青**（玻璃/极光），
        //   白字压上去读不出来。`AppOnAccent` 按强调色亮度自动选深/浅字。
        // ⚠ 用 `Color.White` 而不是 `Color(0xFFFFFFFF)`：色值一样，但这样
        //   `check_ui_tokens` 才能对"组件体里写死颜色"做**零容忍**（不留例外）。
        contentColor = AppOnAccent,
    ),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = colors,
        contentPadding = contentPadding,
        content = content,
    )
}

/** 描边按钮（次动作）。见 [AppPrimaryButton] 的说明。 */
@Composable
fun AppOutlineButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.outlinedButtonColors(contentColor = AppTextPrimary),
    border: BorderStroke? = BorderStroke(1.dp, AppButtonOutline),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = colors,
        border = border,
        contentPadding = contentPadding,
        content = content,
    )
}
