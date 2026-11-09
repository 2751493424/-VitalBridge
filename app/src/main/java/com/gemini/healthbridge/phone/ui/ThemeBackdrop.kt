package com.gemini.healthbridge.phone.ui

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * 主题背景层（2026-11-06）：**一整屏的底色 + 会动的光**，外加给玻璃卡片用的坐标系。
 *
 * ## 为什么单独一层
 *
 * 手表端那 4 套动态主题（极光/钛光/暮光/曜金）靠**背景光效**在动，配色其实没变。
 * 手机端第一版只搬了配色、没搬动效，用户一眼就问"那几个怎么不是动态的"。
 * ⇒ 把动效抽成这一层，画在**根节点最底下**，所有界面浮在它上面。
 *
 * ⚠⚠ 配套条件有两个，缺一个就"动不起来"、而且**不报错**：
 *   ① `Scaffold(containerColor = Color.Transparent)` —— 不透明容器会把这一层整块盖掉；
 *   ② 页面自己不能再铺满底色 ⇒ 用 [appPageBg] 替代 `background(AppBg)`。
 *
 * ## 为什么还要 `content` 槽
 *
 * 玻璃卡片画的是"**我这块地方背后的那一截背景**"（见 `AppUi.kt` 的 `glassChrome`）。
 * 那它就得知道三件事：背景层的原点、尺寸、当前相位 —— 只有这一层知道。
 * ⇒ 它除了画 Canvas，还要把这三个值通过 CompositionLocal 往下传。
 *
 * ## 成本
 *
 * 每帧 = 1 个整屏纵向渐变 + 1~3 个径向渐变矩形，**不做模糊、不抓快照**。
 * 静态细噪声是**单独一层 Canvas**（见 [BackdropGrainOverlay]）：不读 phase、只在尺寸
 * 变化时生成一次位图，之后每帧复用已录制的绘制指令。
 * ⚠⚠ 绝不能把噪声塞回 `drawThemeBackdrop`：那个函数每张玻璃卡片、每个主题预览都调一次，
 *   等于每帧对同一张整屏 10MB 半透明位图 blend 好几遍；而且预览层尺寸与根层不同，
 *   单份缓存会每帧来回失效 ⇒ 每帧重建 250 万像素 IntArray + Bitmap（实测主线程 ~2s/帧）。
 * 静态主题（`motion == NONE`）完全不建动画，零额外开销（噪声层本身是静态的）。
 *
 * ⚠ 只让**当前这一页**动（主题页把左右两页也一起组合出来了，10 套动画同时跑等于白烧电）。
 */
@Composable
fun ThemeBackdropLayer(
    modifier: Modifier = Modifier,
    theme: PhoneTheme = ThemeManager.current,
    animated: Boolean = true,
    /** 是否叠静态细噪声。只有根层整屏需要；主题预览小卡不画（省整屏 blend，也避免噪声显脏）。 */
    grain: Boolean = true,
    content: @Composable () -> Unit,
) {
    val moving = animated && theme.motion != ThemeMotion.NONE
    // ⚠⚠⚠ 2026-11-07 **相位改成「单调递增的时间」，永不归零**。
    //   原先用 `rememberInfiniteTransition` + `infiniteRepeatable(tween(0→1))`：
    //     ① 动画框架在每个 iteration 结束处会 hold 终值 / 重启一帧 ⇒ 用户说的「卡一下」；
    //     ② 0→1 回绕那一刻，凡是 `(phase*1.3f)%1f`、`sin(ang*1.4f)` 这类写法，
    //        光会**整组瞬移回起点** ⇒ 用户说的「回到第一帧」。
    //   ⇒ 自己按帧累加时间：没有迭代边界、没有归零，也就没有「播完第一遍」这回事。
    // ⚠⚠ 保留的是 **State**、不是 `Float`：在这里 `.value` 解包的话，相位每帧变 ⇒
    //   每张玻璃卡片**每帧重组一次**；传 State 则在绘制阶段读值，只重绘、不重组。
    val clock = rememberBackdropTurns(theme.motion.periodMs, moving)
    val still = androidx.compose.runtime.rememberUpdatedState(theme.motion.staticPhase)
    val phase: androidx.compose.runtime.State<Float> = if (moving) clock else still

    // 背景层在 Compose 根坐标系里的原点与尺寸。
    // 玻璃卡片用它们把自己的**绝对位置**换算成"背景层内的相对位置"，
    // 再按那个位置取一截背景画进来 —— 这样卡片压在哪团光上，就透出哪团光的颜色。
    val origin = remember { mutableStateOf(Offset.Zero) }
    val sizePx = remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier
            .onGloballyPositioned { origin.value = it.positionInRoot() }
            .onSizeChanged { sizePx.value = it },
    ) {
        Canvas(Modifier.matchParentSize()) { drawThemeBackdrop(theme, phase.value) }
        // ⚠⚠ 噪声**必须单独一层、且不读 phase**：
        //   这个 Canvas 的 lambda 不捕获任何 State ⇒ Compose 只在尺寸变化时录一次绘制指令，
        //   之后每帧只复用该 RenderNode；动态那层重绘不会把它一起重放。
        //   放进 drawThemeBackdrop 则每张玻璃卡 / 每个预览都画一遍整屏位图 ⇒ 秒级卡顿。
        if (grain && !theme.isLight) BackdropGrainOverlay()
        CompositionLocalProvider(
            LocalBackdropOrigin provides origin,
            LocalBackdropSize provides sizePx.value,
            LocalBackdropTheme provides theme,
            LocalBackdropPhase provides phase,
        ) {
            content()
        }
    }
}

/**
 * **单调动画时钟**（2026-11-07）：返回「从进入画面起走了多少个周期」的 State。
 *
 * ⚠ 不用 `rememberInfiniteTransition`：它的 `infiniteRepeatable` 是**按周期重启**的，
 *   重启时会顿一帧并把值打回 0 —— 玻璃主题上就是「播完第一遍卡一下，回到第一帧」。
 *   这里用 [withFrameNanos] 逐帧累加：值只增不减，于是所有写成 `sinWave/2πft` 的运动
 *   在数学上**处处连续、永不重复**，也没有任何一帧会被动画框架重复。
 *
 * ⚠ 帧间隔封顶 100ms：从后台回来 / 系统掉帧时不让画面「瞬移」一大步。
 * ⚠ `LaunchedEffect` 的 key 只放 `periodMs`：换主题改的是速度，累计时间不重置。
 */
@Composable
private fun rememberBackdropTurns(periodMs: Int, running: Boolean): androidx.compose.runtime.State<Float> {
    val turns = remember { mutableStateOf(0f) }
    if (running && periodMs > 0) {
        LaunchedEffect(periodMs) {
            var last = -1L
            while (isActive) {
                val now = withFrameNanos { it }
                if (last >= 0L) {
                    val dt = (now - last).coerceIn(0L, 100_000_000L)
                    turns.value += dt / (periodMs * 1_000_000f)
                }
                last = now
            }
        }
    }
    return turns
}

/**
 * 页面底：**静态主题画实色，动效主题留空**（露出根层那片会动的光）。
 *
 * ⚠ 不能一律写 `background(AppBg)`：那等于在动态背景上又糊了一层不透明色，
 *   光被盖住、主题看着和静态的一样（而且完全不报错）。
 */
@Composable
fun Modifier.appPageBg(): Modifier {
    val t = ThemeManager.current
    return if (t.motion == ThemeMotion.NONE) this.background(t.bg) else this
}

// ══════════════════ 给玻璃卡片用的背景坐标系 ══════════════════

/** 背景层在根坐标系里的原点（**State**：卡片在绘制阶段读，避免每帧重组）。 */
internal val LocalBackdropOrigin = compositionLocalOf<androidx.compose.runtime.State<Offset>> {
    androidx.compose.runtime.mutableStateOf(Offset.Zero)
}

/** 背景层（整屏）的像素尺寸。 */
internal val LocalBackdropSize = compositionLocalOf { IntSize.Zero }

/** 背景层当前用的主题 —— 预览里是"被预览的主题"，不一定等于 [ThemeManager.current]。 */
internal val LocalBackdropTheme = compositionLocalOf<PhoneTheme?> { null }

/** 背景层当前的动画相位（**State**，同上）。 */
internal val LocalBackdropPhase = compositionLocalOf<androidx.compose.runtime.State<Float>> {
    androidx.compose.runtime.mutableStateOf(0f)
}

private const val TWO_PI = 6.2831855f

/**
 * **静态细噪声 / grain**（2026-11-07）：消除深色平滑渐变上的「规则小格子」。
 *
 * ⚠⚠⚠ 起因与判据（用户第二次反馈，上一轮误判为「只是 JPG 压缩」，这里更正）：
 *   · 用 `adb exec-out screencap -p` 抓的**无损帧缓冲**里，深色渐变仍然存在
 *     **2px 周期的规则抖动**：行/列均值去趋势后自相关 lag=4/8/12/16 都到 0.95~0.99，
 *     偶数 lag 是峰、奇数 lag 是谷 ⇒ 8-bit 量化 / 有序抖动留下的棋盘图案。
 *     上一轮只按 mod 4/8/16 分组看**梯度**，比值 1.8 就以为干净，漏掉了它。
 *   · 模拟器窗口把 1080×2340 按非整数比例缩到桌面显示时，这个 2px 图案与
 *     窗口像素网格**拍频**，放大成 ~38px 的方格 —— 用户原话「在模拟器上看起来就有小格子」。
 *     用户发的窗口 PNG 在 34/39-40/74/113px 有强自相关峰（≈37 的 1/2/3 倍），
 *     且缩小后**没有** 8px 块峰 ⇒ 不是 JPEG 宏块放大，是拍频。
 *   · ⇒ 真正的解法不是改压缩、也不是调窗口，而是**把规则图案打散成非周期噪声**：
 *     随机噪声没有单一频率，缩放时不会拍频出整齐方格；顺带也减轻真机上的色阶断层。
 *
 * ⚠⚠ 三条硬约束：
 *   ① **静态**：固定种子、只生成一次。噪声若随时间变 ⇒ 满屏闪烁（用户极敏感）。
 *   ② **非周期**：噪声图 = 整屏尺寸一次生成，⛔ 不许平铺小图 —— 平铺周期会变成新格子。
 *   ③ **极轻**：alpha 只有 1~4/255，肉眼不可见；大了就成「脏噪点」，
 *      之前玻璃背景的圆点被判「像水渍」就是幅度给多了。
 *
 * ⚠ 只画在**根层整屏**：玻璃卡片取的是自己那一截背景，卡片不该自带噪点；
 *   浅色主题也不画（浅底上噪点更容易显脏）；主题预览小卡同样不画。
 *
 * ⚠⚠ 2026-11-07 **性能事故复盘**（用户「点击主题后很卡」）：
 *   最初把 `drawImage` 写在 `drawThemeBackdrop` 末尾 ⇒ 该函数每张玻璃卡、每个预览
 *   都调一次，且预览卡尺寸与根层整屏不同，而缓存只有**一份按尺寸索引**的位图 ⇒
 *   每帧在两种尺寸间来回失效，每帧重建 250 万像素 IntArray + 10MB Bitmap + 上传纹理，
 *   实测 `Choreographer: Skipped 120 frames`（约 2 秒/帧）。修法见 [BackdropGrainOverlay]。
 */

/**
 * **静态噪声层**：独立于会动的背景，lambda 不读 phase ⇒ 只录制一次、每帧复用。
 *
 * ⚠ 必须是独立 Composable / 独立 Canvas 节点，不能在 `drawThemeBackdrop` 的
 *   DrawScope 里顺手画 —— 那里每帧被多个调用者重放，画的就是多份整屏位图。
 */
@Composable
private fun BoxScope.BackdropGrainOverlay() {
    Canvas(Modifier.matchParentSize()) {
        val iw = size.width.toInt()
        val ih = size.height.toInt()
        if (iw > 0 && ih > 0) drawImage(BackdropGrain.get(iw, ih))
    }
}

private object BackdropGrain {
    // ⚠⚠ 按尺寸分别缓存：单份缓存遇到两种尺寸交替请求会每帧重建整屏位图（上面的事故）。
    //   真实场景最多 2~3 种尺寸（整屏 / 分屏 / 旋屏），上限 4 防止 unbounded 增长。
    private val cache = HashMap<Long, ImageBitmap>(4)

    fun get(w: Int, h: Int): ImageBitmap {
        val key = (w.toLong() shl 32) or (h.toLong() and 0xffffffffL)
        cache[key]?.let { return it }
        if (cache.size >= 4) cache.clear()
        val n = w * h
        val px = IntArray(n)
        // 一次 nextInt 同时给出「亮度档」和「白/黑」，250 万像素也只调 250 万次随机
        val rnd = java.util.Random(0x5EEDBACCFL)
        for (i in 0 until n) {
            val v = rnd.nextInt()
            val a = 1 + ((v ushr 28) and 3)        // 1..4 / 255
            // ⚠ 括号不能省：`and` 是中缀函数，`v and 1 == 0` 会被解析成 `v and (1 == 0)`
            px[i] = if ((v and 1) == 0) (a shl 24) or 0x00FFFFFF else (a shl 24)
        }
        val bmp = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
        val image = bmp.asImageBitmap()
        cache[key] = image
        return image
    }
}

/**
 * **连续波**（2026-11-07）：把单调时间 `t` 映射成 [-1, 1]。
 *
 * 频率 `freq` 与相位偏移 `offset` 都可以是任意实数 ⇒ 多路波叠加后**没有公共周期**，
 * 画面永远在缓慢变化、且**永远不会精确回到某一帧**。这就是「一直很流畅」的来源。
 *
 * ⚠⚠ 动态背景里的**运动只允许经过这两个函数**；`(t*k) % 1f`、`sin(ang*1.4f)`
 *   （对角度乘非整系数）都会在某一刻跳变，是「回到第一帧」的直接成因。
 */
internal fun sinWave(t: Float, freq: Float, offset: Float): Float =
    sin(TWO_PI * (freq * t + offset))

internal fun cosWave(t: Float, freq: Float, offset: Float): Float =
    cos(TWO_PI * (freq * t + offset))

/**
 * **只给「扫过型」光带**把单调时间折成 0..1 的行程。
 * 两端由 `sheenBand` / 卡片扫光保证落在屏幕外，所以折返那一帧看不见。
 * ⛔ 绝不要拿它做圆周、漂移、呼吸这类运动 —— 折返点就是瞬移点。
 */
private fun fract(x: Float): Float = x - kotlin.math.floor(x)

/**
 * 背景**最顶端**那一行的颜色。
 *
 * ⚠⚠ `MainActivity` 必须拿它去设窗口底色（`window.setBackgroundDrawable`）：
 *   本应用不是 edge-to-edge，屏幕上沿那条**状态栏带**露的是窗口底色、不是 Compose 内容。
 *   两者不一致时，状态栏下面会有一条**硬边**（上面是纯深色、下面是渐变）——
 *   玻璃主题下特别明显（实测：像给屏幕贴了一条黑胶带）。
 */
fun PhoneTheme.backdropTopColor(): Color =
    lerp(bg, Color.White, if (isLight) 0.55f else 0.06f)

/**
 * 画一整屏背景。
 *
 * @param phase **单调时间**（单位 = `motion.periodMs` 的个数），只增不回绕。
 *   ⚠⚠ 不是「0→1 的循环相位」：运动一律走 [sinWave] / [cosWave] 这类时间的连续函数，
 *   ⛔ 不许 `(phase*k) % 1f`、不许对角变量乘非整系数 —— 那些会在「周期结束」处瞬移。
 * @param canvas 画布尺寸（默认 = 当前 DrawScope 尺寸）。玻璃卡片里传**整屏尺寸**，
 *   因为光的位置要按屏幕坐标算，而不是按卡片大小算。
 * @param shift 坐标平移：卡片传入"自己在背景层里的位置"，于是画出来的就是
 *   **该位置上那一截背景** —— 这是"毛玻璃"能成立的全部秘密。
 * @param taps 细节层（细光带 / 星点）的采样次数：`1` = 清晰原图；`>1` = 用**抖动多次采样**
 *   近似高斯模糊（见 [drawDetail]）。玻璃卡片靠它把背后的锐利细节"糊"成磨砂质感。
 * @param tapRadius 抖动半径（px）。越大越糊；配合 `taps` 一起放大。
 * @param vignette 画不画暗角。真机整屏要画；卡片里不画（区域太小，暗角反把卡片压灰）。
 */
fun DrawScope.drawThemeBackdrop(
    t: PhoneTheme,
    phase: Float,
    canvas: Size? = null,
    shift: Offset = Offset.Zero,
    taps: Int = 1,
    tapRadius: Float = 0f,
    vignette: Boolean = true,
    /** 光效强度系数（0~1）。卡片里用它把"主题光"压淡后叠在实色底上。 */
    strength: Float = 1f,
    /**
     * **只画光、不画底**（2026-11-07）。功能宫格的卡片用它取"自己这块位置背后的那团光"，
     * 叠在 `card` 实底上 —— 卡片于是和背景同一个色温，而不是被塞一个固定渐变。
     * ⚠ 这种模式下自动跳过暗角与细节层（那些是整屏才成立的）。
     */
    glowOnly: Boolean = false,
) {
    val c = canvas ?: size
    val w = c.width
    val h = c.height
    val m = c.maxDimension

    // 屏幕坐标 → 当前画布坐标
    fun at(x: Float, y: Float) = Offset(x - shift.x, y - shift.y)

    // ── 底：顶部略亮、底部沉下去 ──
    // ⚠ 深色主题只提亮 6%：再多就是"脏灰"，AMOLED 上也费电；
    //   浅色主题要提亮得多（暖白 → 近白），否则整屏平得像一张纸。
    val top = t.backdropTopColor()
    if (!glowOnly) {
        drawRect(
            Brush.verticalGradient(
                colors = listOf(top, t.bg, t.bg),
                startY = -shift.y,
                endY = -shift.y + h,
            )
        )
    }

    val a1 = t.accent
    val a2 = if (t.accent2 == Color.Transparent) t.accent else t.accent2

    when (t.motion) {
        ThemeMotion.NONE -> Unit

        // ── 极光：三团柔光沿椭圆轨道漂移（互相错开 120°）──
        ThemeMotion.AURORA -> {
            // 卡片里只取光，不画极光帘之外的细节（帘本身是光，保留）
            // ⚠ 幅度（0.34w / 0.24h）与 alpha（0.34）都是**为大屏放大过**的：
            //   照搬手表那套参数时，整屏上光只挪了很小一段，肉眼几乎看不出"它在动"。
            // ⚠ 2026-11-07：x / y 用**不同频率** ⇒ 轨迹是永不闭合的李萨如曲线，
            //   不再有「26 秒一循环、回到起点」的那一刻（同频时那个循环很明显）。
            for (i in 0 until 3) {
                val cx = w / 2f + cosWave(phase, 1f, i / 3f) * w * 0.34f
                val cy = h / 2f + sinWave(phase, 0.77f, i / 3f) * h * 0.24f
                bloom(at(cx, cy), m * 0.54f, if (i == 1) a2 else a1, 0.34f * strength)
                // 第一团带**小亮芯**：三团大光圈只给出"颜色"，没有亮芯时整屏像蒙了层色纸
                if (i == 0) bloom(at(cx, cy), m * 0.16f, a1, 0.34f * strength)
            }
            // 一道**竖向极光帘**：明暗沿横向正弦起伏，比圆形光斑更像"极光"；
            // 摆动用第三个频率，和上面三团光不同步 ⇒ 整体永不重复
            run {
                val sway = sinWave(phase, 0.61f, 0f) * w * 0.12f
                drawRect(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            a2.copy(alpha = 0.10f * strength),
                            Color.Transparent,
                            a1.copy(alpha = 0.08f * strength),
                            Color.Transparent,
                        ),
                        start = at(w * 0.15f + sway, 0f),
                        end = at(w * 0.95f + sway, 0f),
                    )
                )
            }
        }

        // ── 钛光：斜 28° 的高光带扫过（金属拉丝反光）──
        // ⚠ 手机上要两条：只一条时扫过去之后整屏是死的，观感像"闪了一下就没动静了"。
        ThemeMotion.SHEEN -> {
            // ⚠ `fract` 只在这里用：三条光带的行程两端都在屏幕外（见 sheenBand 的 span），
            //   折返那一帧看不见；其余运动一律连续波，⛔ 不许拿取模做漂移。
            sheenBand(w, h, fract(phase), a2, 0.26f * strength, 0.32f, 28f, shift)
            sheenBand(w, h, fract(phase + 0.34f), a2, 0.10f * strength, 0.16f, 28f, shift)
            // 第三条：又细又快，模拟金属面上"唰"一下的反光（性能放开后才敢加）
            sheenBand(w, h, fract(phase * 2.6f), a2, 0.14f * strength, 0.06f, 28f, shift)
        }

        // ── 暮光：中心柔光呼吸（半径 + 亮度一起变，极慢）──
        ThemeMotion.BREATHE -> {
            // ⚠ 两个不通约频率叠在一起 ⇒ 明暗起伏永不精确重复，也永远有东西在动
            val k = 0.5f + 0.5f * sinWave(phase, 1f, 0f)
            val k2 = 0.5f + 0.5f * sinWave(phase, 0.53f, 0.21f)
            bloom(at(w / 2f, h * 0.42f), m * (0.40f + 0.12f * k), a1, (0.10f + 0.18f * k) * strength)
            // 反向呼吸的一团：与主光**相位相反**，于是整屏像"有东西在缓慢起伏"，
            //   而不是一盏灯简单明灭（2026-11-07 加）
            bloom(at(w * 0.5f, h * 0.30f), m * (0.30f + 0.10f * (1f - k)), a2, (0.08f + 0.10f * (1f - k)) * strength)
            // 偏下再补一团更暗更宽的紫，让"呼吸"有纵深，不像一盏灯在闪
            bloom(at(w * 0.5f, h * 0.78f), m * 0.55f, a2, 0.08f + 0.06f * k2)
        }

        // ── 曜金：金色光晕绕圆心缓慢旋转 + 更亮的芯 ──
        ThemeMotion.SWEEP -> {
            val ang = TWO_PI * phase
            // ⚠ 公转之外再叠一个更慢的「半径呼吸」⇒ 轨迹不闭合，不会 20 秒回到同一帧
            val orbit = 1f + 0.12f * sinWave(phase, 0.37f, 0.30f)
            val cx = w / 2f + cos(ang) * w * 0.26f * orbit
            val cy = h / 2f + sin(ang) * h * 0.22f * orbit
            // ⚠ 光晕**别铺满整屏**：半径 0.5×屏时纯黑底被染成一片古铜色，
            //   看着像"旧照片滤镜"而不是"金色弧光在转"（实测）。收到 0.34 才有明暗对比。
            bloom(at(cx, cy), m * 0.34f, a1, 0.20f * strength)
            bloom(at(cx, cy), m * 0.16f, a1, 0.24f * strength)
            // 彗尾：主光后面拖三级递减的小光斑，绕圈才像"有质量的东西在转"
            for (i in 1..3) {
                val ta = ang - i * 0.18f
                val tx = w / 2f + cos(ta) * w * 0.26f * orbit
                val ty = h / 2f + sin(ta) * h * 0.22f * orbit
                bloom(at(tx, ty), m * (0.12f - i * 0.02f), a1, (0.06f - i * 0.012f) * strength)
            }
        }

        // ── 玻璃 / 深空青蓝（2026-11-07 用户指定「案 1」）──
        // 规格：底色 #080B12；**只在左右下角**铺微弱青蓝 #0F3443 的柔和 Mesh 渐变；无杂色。
        //
        // ⚠⚠ 这一层仍是毛玻璃能否成立的关键（卡片半透明，底下没东西可透就只剩一块灰），
        //   但与旧版相反：光池要**大而柔、彼此连成一片**，而不是几团分开的小亮点 ——
        //   Mesh 渐变的特征就是看不出单团的边界；同时光全部压在下半屏，上半屏保持近黑
        //   ⇒ 卡片里的白字读数对比度最高，心率红 / 血氧蓝 / 心电紫也不被背景第二色相干扰。
        // ⚠⛔ 不许再引入第二色相：旧版右上玫瑰 + 底部青紫混色被判「杂色」，且和血氧蓝抢色。
        //   全部只用 [PhoneTheme.glow]（#0F3443）及其同色系亮芯。
        ThemeMotion.CAUSTIC -> {
            // ⛔ 别拿 t.accent（#6FE3FF 亮青）当光晕：那是按钮 / 图例色，
            //   直接铺开会整屏泛蓝雾、白字对比度掉下来（早期版本实测）。
            val glowBase = if (t.glow == Color.Transparent) t.accent else t.glow
            // 同色系提亮 30% 做亮芯，仍然是青蓝，不引入杂色。
            // ⚠ 22% 时实拍左下只有 rgb(27,44,58)，比参考图那抹青暗一档、几乎看不出光；
            //   30% 才够让两角在极深底上「有光」，但仍是同色相、不刺眼。
            val glowLite = lerp(glowBase, Color.White, 0.30f)

            // x, y, 半径系数, 颜色(0=glow / 1=亮芯), alpha
            // ⚠ y 全部 ≥ 0.88：光只在**下沿**；半径给到 0.6×屏且互相重叠 ⇒ 边界糊掉，
            //   看起来是「一片柔和的 Mesh」，不是「两个彩色气球」。
            // ⚠⚠ 2026-11-07 实拍校正：第一版把「下中连接团」给到 alpha 0.30 / 半径 0.70×屏，
            //   结果整个下半屏被填成一片均匀青蓝 —— 看着像底部渐变，不像「左右下角两个光源」。
            //   参考图里下中明显比左下暗 ⇒ 连接团压到 0.14、两角各自加强，才有两处峰 + 中间凹。
            val pools = listOf(
                floatArrayOf(0.02f, 1.02f, 0.55f, 0f, 1.00f),   // 左下：主光源（对应参考图那抹青）
                floatArrayOf(1.00f, 1.04f, 0.52f, 0f, 0.80f),   // 右下：次光源
                floatArrayOf(0.50f, 1.14f, 0.60f, 1f, 0.14f),   // 下沿中间：只留一丝过渡，别填平
                floatArrayOf(0.18f, 0.88f, 0.42f, 1f, 0.18f),   // 左下往上的过渡
            )
            // ⚠⚠ 单调时间 + 互不通约频率的连续波（见 rememberBackdropTurns / sinWave）：
            //   没有迭代边界、没有取模瞬移，也就没有「播完第一遍卡一下、回到第一帧」。
            //   深空青蓝要「冷静、低刺激」⇒ 位移幅度比上一版收一半、亮度呼吸也更弱。
            val drift = arrayOf(
                floatArrayOf(0.83f, 0.61f, 0.11f, 0.37f, 0.43f),
                floatArrayOf(0.97f, 0.73f, 0.41f, 0.09f, 0.51f),
                floatArrayOf(1.13f, 0.89f, 0.68f, 0.53f, 0.37f),
                floatArrayOf(0.71f, 1.07f, 0.24f, 0.79f, 0.59f),
            )
            pools.forEachIndexed { i, p ->
                val d = drift[i]
                val cx = w * p[0] + cosWave(phase, d[0], d[2]) * w * 0.025f
                val cy = h * p[1] + sinWave(phase, d[1], d[3]) * h * 0.018f
                // 亮度按自己的频率轻轻呼吸：即便某一刻位移接近 0，画面仍在变
                val pulse = 0.94f + 0.06f * sinWave(phase, d[4], i * 0.17f)
                val col = if (p[3].toInt() == 0) glowBase else glowLite
                bloom(at(cx, cy), m * p[2], col, p[4] * pulse * strength)
                // 两个下角各给一个更小的亮芯（左强右弱），中间那团不给：
                //   ⚠ 只给一处时右下会塌成一片糊，连团都给又变回「几个彩色气球」。
                if (i == 0) bloom(at(cx, cy), m * 0.20f, glowLite, 0.38f * pulse * strength)
                if (i == 1) bloom(at(cx, cy), m * 0.16f, glowLite, 0.24f * pulse * strength)
            }
            // ⚠⚠ 细节层只在被采样时画（taps > 1 = 玻璃卡片在取背后的磨砂质感）：
            //   整屏画就是一颗颗圆盘。颜色跟着 [glow] 走，⛔ 不许出现粉色光点。
            if (taps > 1) {
                drawDetail(taps, tapRadius) { k ->
                    causticSpecks(phase, w, h, glowBase, glowLite, k) { x, y -> at(x, y) }
                }
            }

            // ⚠ 原来这里写的是 `if (glowOnly || !vignette) return` —— 提前 return 会
            //   跳过函数后续所有绘制（包括曾经放在末尾的静态噪声层）。改成条件块。
            if (!glowOnly && vignette) {
                // ⚠⚠ 暗角必须**很轻**：这版光晕本身就在下角，旧版 0.34 的黑会把
                //   左右下角整片压没，等于白画。中心上移、半径放大、alpha 减半。
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f)),
                        center = at(w / 2f, h * 0.40f),
                        radius = m * 0.98f,
                    )
                )
            }
        }
    }

    // ⚠⚠ 静态细噪声**不在这里画**：见 ThemeBackdropLayer 里的 [BackdropGrainOverlay]。
    //   本函数每张玻璃卡片 / 每个主题预览都会重放一次，在这里 drawImage 整屏位图 =
    //   每帧重复 blend 好几遍 10MB 位图（实测 2 秒/帧）。噪声只属于根层那一层。
}

/** 一条斜向高光带。[progress] 0~1 = 从画面左上扫到右下。 */
private fun DrawScope.sheenBand(
    w: Float,
    h: Float,
    progress: Float,
    color: Color,
    alpha: Float,
    halfWidthRatio: Float,
    angleDeg: Float,
    shift: Offset,
) {
    val ang = angleDeg / 180f * Math.PI.toFloat()
    val dx = cos(ang)
    val dy = sin(ang)
    val span = (w + h) * 1.2f
    val along = progress * span - span / 2f
    val bx = w / 2f + dx * along
    val by = h / 2f + dy * along
    val band = w * halfWidthRatio
    drawRect(
        Brush.linearGradient(
            colors = listOf(Color.Transparent, color.copy(alpha = alpha), Color.Transparent),
            start = Offset(bx - dx * band - shift.x, by - dy * band - shift.y),
            end = Offset(bx + dx * band - shift.x, by + dy * band - shift.y),
        )
    )
}

/** 一团柔光：中心 alpha 向外衰减到 0。 */
private fun DrawScope.bloom(center: Offset, radius: Float, color: Color, alpha: Float) {
    drawRect(
        Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius,
        )
    )
}

/**
 * **抖动多次采样 = 近似模糊**（2026-11-07，用户放开性能限制后加的）。
 *
 * ⚠ 为什么不用 `Modifier.blur` / `RenderEffect`：
 *   它们模糊的是**自身内容**，而毛玻璃要模糊的是"卡片背后那一层"。
 *   在 Compose 里把"背后那层"取过来做真模糊，需要 `GraphicsLayer` 抓快照 +
 *   逐帧重录（还没进稳定 API）——对一张会动的全屏背景代价反而更大，还容易踩版本差异。
 *
 * ⚠ 这里改用**多次偏移采样**：把同一层细节按黄金角螺旋在半径 [tapRadius] 内采 [taps] 次、
 *   每次 alpha = 1/taps。采 9~13 次时视觉上就是一层柔和的糊 ——
 *   因为被糊的东西是**细线/星点**，多次抖动叠加后自然糊开。
 *   ⚠ 只糊"细节层"、**不糊**底下的柔光：柔光本来就是平滑的，多采只是白烧 GPU。
 */
private inline fun DrawScope.drawDetail(
    taps: Int,
    tapRadius: Float,
    crossinline draw: DrawScope.(alphaScale: Float) -> Unit,
) {
    if (taps <= 1 || tapRadius <= 0f) {
        draw(1f)
        return
    }
    val step = 1f / taps
    for (i in 0 until taps) {
        // 黄金角螺旋：采样点分布均匀，不会出现"十字条纹"
        val ang = i * 2.39996f
        val r = tapRadius * kotlin.math.sqrt((i + 0.5f) / taps)
        translate(cos(ang) * r, sin(ang) * r) { draw(step) }
    }
}

/**
 * 玻璃背后的**可糊细节**：一圈大小不一的光点（玻璃卡片会把它抖动采样 N 次）。
 *
 * ⚠ 2026-11-07 深空青蓝：传入的 `a1 / a2` 是 `glow` 及其**同色系亮芯**两档青蓝，
 *   ⛔ 不再是亮青 + 玫瑰 —— 光点带第二色相就是用户说的「杂色」。整屏不画这层，
 *   它只活在玻璃卡片背后，被 6 次抖动采样糊成磨砂微粒。
 * ⚠⚠ 这里**绝对不要用线**：1024~1440 宽的屏上，1~2dp 的斜线看起来就是**屏幕划痕**，
 *   5~11dp 的"宽光柱"是两道更粗的划痕（2026-11-07 实测，用户原话"看起来太垃圾"）。
 *   圆点糊开之后才是"玻璃里挡不住的亮点"。
 * ⚠ 点要**够小**（5~14dp 半径）才是细节；再大就只是光斑，糊不糊看不出来。
 * ⚠ 数量控制在 9 个：这一层是**乘以 taps** 画的（6×9 = 54 次小圆，可接受）。
 */
private fun DrawScope.causticSpecks(
    phase: Float,
    w: Float,
    h: Float,
    a1: Color,
    a2: Color,
    alphaScale: Float,
    at: (Float, Float) -> Offset,
) {
    val m = maxOf(w, h)
    // 固定的一组位置（避免每帧随机导致闪烁），由 phase 让它们缓慢漂移
    // ⚠⚠ 半径必须**小于抖动半径**才糊得开：抖动 7dp 时，12dp 的圆糊出来还是圆（像水渍）。
    //   这里统一收到 3~8dp，并把数量提到 14 —— 糊开之后是一片均匀的微粒感，看不出单个点。
    val specks = listOf(
        floatArrayOf(0.10f, 0.05f, 7f, 0f), floatArrayOf(0.24f, 0.13f, 4f, 0f),
        floatArrayOf(0.34f, 0.22f, 5f, 0f), floatArrayOf(0.18f, 0.31f, 3f, 1f),
        floatArrayOf(0.86f, 0.14f, 6f, 1f), floatArrayOf(0.95f, 0.24f, 4f, 1f),
        floatArrayOf(0.78f, 0.33f, 5f, 1f), floatArrayOf(0.66f, 0.26f, 3f, 2f),
        floatArrayOf(0.92f, 0.42f, 6f, 1f), floatArrayOf(0.46f, 0.90f, 7f, 2f),
        floatArrayOf(0.60f, 0.86f, 4f, 1f), floatArrayOf(0.30f, 0.95f, 5f, 2f),
        floatArrayOf(0.04f, 0.70f, 6f, 0f), floatArrayOf(0.12f, 0.84f, 3f, 0f),
    )
    specks.forEachIndexed { i, sp ->
        // ⚠⚠ 2026-11-07：原来 `(phase*1.3f + i*0.11f) % 1f` + `sin(ang*1.2f)`
        //   在相位回绕时让 14 个点整组跳回起点 —— 这就是「回到第一帧」最显眼的一处。
        //   现在每个点 x / y 各用自己的频率，⛔ 不取模：全程连续、永不整组归位。
        val fx = 0.77f + i * 0.031f
        val fy = 0.53f + i * 0.047f
        val cx = w * sp[0] + cosWave(phase, fx, i * 0.137f) * w * 0.03f
        val cy = h * sp[1] + sinWave(phase, fy, i * 0.211f) * h * 0.025f
        val col = when (sp[3].toInt()) {
            0 -> a1
            1 -> a2
            else -> lerp(a1, a2, 0.5f)
        }
        // 半径按"以 1080 宽为基准的 dp 值"换算，跨设备等比
        val r = sp[2].dp.toPx() * (m / 1080f)
        // ⚠ 幅度压到最低：深空青蓝要求「无杂色、低刺激」，这些点只用来给抖动采样
        //   一点可糊的东西，不能在玻璃上留下一颗颗可见的白点（旧版 0.20/0.08 会）。
        drawCircle(color = col.copy(alpha = 0.14f * alphaScale), radius = r, center = at(cx, cy))
        drawCircle(
            color = Color.White.copy(alpha = 0.04f * alphaScale),
            radius = r * 0.42f,
            center = at(cx, cy),
        )
    }
}
