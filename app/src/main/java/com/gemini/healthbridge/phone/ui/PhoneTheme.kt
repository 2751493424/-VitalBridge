package com.gemini.healthbridge.phone.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * 手机端主题（2026-11-06）。
 *
 * ## 为什么要有这一层
 *
 * 之前所有颜色都是 `AppUi.kt` 里的**顶层 val 常量**（`AppBg` / `AppCardBg` / …），
 * 编译期就固定了，换个配色要改代码重出包。用户要求「用户页里有主题入口、能左右滑动挑」，
 * 那就必须让这些颜色在**运行时**可变。
 *
 * 做法：颜色从 [ThemeManager.current] 读（`AppUi.kt` 里的 `AppBg` 等已改成 `get()` 属性，
 * 引用点一行都不用改），而 `current` 是 `mutableStateOf` ⇒ 读到它的 Composable 会自动重组。
 *
 * ## 与手表端的关系
 *
 * 手表那 9 套（陶瓷白/深空灰/午夜蓝/松林绿/暖砂/极光/钛光/暮光/曜金）在这里**同名同色**地
 * 搬过来了，两端看起来是一套体系。
 *
 * ⚠ 手表那 4 套动态主题（极光/钛光/暮光/曜金）在手机端**也是动态的** ——
 *   第一版只搬了配色没搬动效，用户当场就问"那几个怎么不是动态的"。
 *   动效由 [motion] 描述，实际画在 [ThemeBackdrop]（根层一整屏，只叠背景光，不碰任何内容）。
 *
 * @param glass 玻璃质感：半透明深蓝卡片 + 上亮下灭的淡蓝描边 + 镜面高光
 *   （见 `AppUi.kt` 的 `glassChrome`），底下必须有角落青蓝光晕可透（`motion = CAUSTIC`），
 *   否则只是一块灰板。
 */
data class PhoneTheme(
    val id: String,
    /** 显示名。⚠ 主题名**不做翻译**（和手表端一致）：它是产品名，不是句子。 */
    val name: String,
    val bg: Color,
    val card: Color,
    val border: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val divider: Color,
    val tile: Color,
    val accent: Color,
    val accentSoft: Color,
    /** 浅色主题：Material 组件要用 `lightColorScheme`，状态栏图标要反过来。 */
    val isLight: Boolean,
    /** 背景动效。`NONE` = 静态（零动画、零额外耗电），见 [ThemeMotion]。 */
    val motion: ThemeMotion = ThemeMotion.NONE,
    /** 动效的第二强调色（极光的第二团光等）。静态主题用不到。 */
    val accent2: Color = Color.Transparent,
    /**
     * **背景角落光晕色**（2026-11-07 深空青蓝方案，只给 `CAUSTIC` 用）。
     *
     * ⚠⚠ 必须与 [accent] 分开：光晕要**极暗**（`#0F3443`）、只负责在左右下角铺一层
     *   几乎看不见的青蓝；accent 是按钮/图例色，要在深底上读得出来。
     *   早期版本直接拿亮青 `#6FE3FF` 当光晕 ⇒ 整屏泛蓝雾、白字对比度掉下来。
     */
    val glow: Color = Color.Transparent,
    /** 玻璃质感：卡片半透明 + 上亮下暗描边 + 镜面高光。 */
    val glass: Boolean = false,
)

/**
 * 背景动效种类（2026-11-06）。
 *
 * ⚠ 与手表端 `ThemeDef.ANIM_*` **一一对应**，两端同名同效果；
 *   唯一新增的是 [CAUSTIC]（玻璃的彩色光晕），手表端没有玻璃主题。
 *
 * ⚠ 动效**只作用于背景光**：按钮、字号、圆角、布局一律不动（与手表同一条纪律）。
 *
 * @param periodMs 一个完整周期的时长。越慢越"高级"，越快越像加载动画 —— 别调快。
 * @param staticPhase 静态（或预览里不参与动画）时用的相位：**不能是 0**，
 *   否则极光三团光会叠在同一个角上、钛光的高光带正好在屏幕外，看着像坏了。
 */
enum class ThemeMotion(val periodMs: Int, val staticPhase: Float) {
    NONE(0, 0f),
    /** 极光：三团柔光沿椭圆轨道漂移（青碧 + 靛蓝 + 品红）。 */
    AURORA(26000, 0.18f),
    /** 钛光：斜 28° 的高光带缓慢扫过（金属拉丝反光）。 */
    SHEEN(7200, 0.34f),
    /** 暮光：中心柔光呼吸。 */
    BREATHE(9000, 0.42f),
    /** 曜金：金色光晕绕圆心旋转 + 更亮的芯。 */
    SWEEP(20000, 0.12f),
    /** 深空青蓝：左右下角两团青蓝 Mesh 柔光极慢漂移（毛玻璃必须有东西可透）。 */
    CAUSTIC(34000, 0.22f),
}

object PhoneThemes {

    /** 「紫夜」：2026-11-06 深色化时的旧默认，2026-11-07 起默认改为 [GLASS]。 */
    val NIGHT = PhoneTheme(
        id = "night", name = "紫夜",
        bg = Color(0xFF0F0A17), card = Color(0xFF1D1329), border = Color(0xFF332545),
        ink = Color(0xFFF3EDF9), ink2 = Color(0xFFC4B8D4), muted = Color(0xFF9C8CB0),
        divider = Color(0xFF3A2C4E), tile = Color(0xFF2A1F3A),
        accent = Color(0xFFE45CFF), accentSoft = Color(0xFF4A2C63),
        isLight = false,
    )

    /**
     * **深空青蓝**（2026-11-07 用户指定「案 1」，医疗科技感；显示名原为「玻璃」）。
     *
     * ## 规格（⛔ 别再凭印象调）
     * · 底色 `#080B12` 极深黑蓝；
     * · 角落**微弱**青蓝光晕 `#0F3443`，**只在左右下角**做柔和 Mesh 渐变、**无杂色**
     *   （旧版右侧那团玫瑰 `#FF7AC6` 已删 —— 它就是"杂色"，且和血氧蓝抢色）；
     * · 玻璃卡片 `rgba(22,32,48,0.42)`、边框 `rgba(140,200,240,0.18)`、约 16px 模糊。
     *
     * ⚠⚠ 为什么这套比旧版"稳"：光全在**下半屏**，卡片主要落在中上部 ⇒ 卡片背后基本是
     *   极深黑蓝，白字读数对比度最高；心率红 / 血氧蓝 / 心电紫这几个数据色也不会被
     *   背景里的第二色相干扰。旧版四团光铺满边缘（含玫瑰）会把整屏染成灰紫。
     *
     * ⚠ `card` 在玻璃主题下由 `glassChrome` 画成 42% 的深蓝玻璃层（不是白）；
     *   `border` 是描边渐变的**峰值**，往下递减，不是一条均匀的线。
     * ⚠ `id` 保持 `"glass"` 不动：用户已存的主题偏好、文案 key 都认这个 id，
     *   改 id 等于「换了一套新主题」，旧设置会被静默重置成紫夜。
     *   `name` 只是显示名（不做翻译），改产品名只动 `name`；两者别混。
     */
    val GLASS = PhoneTheme(
        id = "glass", name = "深空青蓝",
        bg = Color(0xFF080B12),                 // #080B12
        card = Color(0x6B162030),               // rgba(22,32,48,0.42)  0.42*255=107=0x6B
        border = Color(0x2E8CC8F0),             // rgba(140,200,240,0.18) 0.18*255=46=0x2E
        ink = Color(0xFFF6F7FB), ink2 = Color(0xFFCFD3DE), muted = Color(0xFF9AA0AE),
        // 分割线 / 底色块也统一到青蓝系：旧版是半透明白（0x1F..FF），在极深蓝底上发灰
        divider = Color(0x1A8CC8F0), tile = Color(0xFF111A26),
        accent = Color(0xFF6FE3FF), accentSoft = Color(0x2E6FE3FF),
        isLight = false,
        motion = ThemeMotion.CAUSTIC,
        // 同色系稍亮的青蓝（不再有玫瑰/品红）；CAUSTIC 主要用 [glow]，这里只作微调
        accent2 = Color(0xFF1A4A5B),
        glow = Color(0xFF0F3443),               // #0F3443 左右下角光晕
        glass = true,
    )

    // ── 手表端那 9 套（同名同色）──
    val CERAMIC = PhoneTheme(
        id = "ceramic", name = "陶瓷白",
        bg = Color(0xFFF4F1EA), card = Color(0xFFFFFFFF), border = Color(0xFFDDD6C8),
        ink = Color(0xFF1A1917), ink2 = Color(0xFF4A463E), muted = Color(0xFF8C877C),
        divider = Color(0xFFE5DFD2), tile = Color(0xFFEDE8DE),
        accent = Color(0xFFB99B5E), accentSoft = Color(0xFFEFE3CB),
        isLight = true,
    )

    val GRAPHITE = PhoneTheme(
        id = "graphite", name = "深空灰",
        bg = Color(0xFF1B1C1F), card = Color(0xFF2C2E32), border = Color(0xFF3E4147),
        ink = Color(0xFFF2F2F0), ink2 = Color(0xFFC9C9C6), muted = Color(0xFF9A9C9F),
        divider = Color(0xFF3A3D42), tile = Color(0xFF34373C),
        accent = Color(0xFFC8A96B), accentSoft = Color(0xFF4A4232),
        isLight = false,
    )

    val MIDNIGHT = PhoneTheme(
        id = "midnight", name = "午夜蓝",
        bg = Color(0xFF0A1728), card = Color(0xFF17304F), border = Color(0xFF2A4A75),
        ink = Color(0xFFE8F0FA), ink2 = Color(0xFFB6C9DE), muted = Color(0xFF8FA6C0),
        divider = Color(0xFF24405F), tile = Color(0xFF1F3C5E),
        accent = Color(0xFF7FB3FF), accentSoft = Color(0xFF23456E),
        isLight = false,
    )

    val FOREST = PhoneTheme(
        id = "forest", name = "松林绿",
        bg = Color(0xFF0C1A14), card = Color(0xFF1D3529), border = Color(0xFF2E5240),
        ink = Color(0xFFE9F3EC), ink2 = Color(0xFFB7CBBE), muted = Color(0xFF8FA99A),
        divider = Color(0xFF274435), tile = Color(0xFF234034),
        accent = Color(0xFFC9A227), accentSoft = Color(0xFF3A3520),
        isLight = false,
    )

    val SAND = PhoneTheme(
        id = "sand", name = "暖砂",
        bg = Color(0xFFF6EDDE), card = Color(0xFFFFFDF8), border = Color(0xFFDFD0B6),
        ink = Color(0xFF2B2118), ink2 = Color(0xFF5C4A38), muted = Color(0xFF8B7358),
        divider = Color(0xFFE8DAC4), tile = Color(0xFFEFE3CE),
        accent = Color(0xFFC08A3E), accentSoft = Color(0xFFF3E1C4),
        isLight = true,
    )

    val AURORA = PhoneTheme(
        id = "aurora", name = "极光",
        bg = Color(0xFF05070C), card = Color(0xFF162030), border = Color(0xFF23324A),
        ink = Color(0xFFECF2FA), ink2 = Color(0xFFB9C6D6), muted = Color(0xFF929EB0),
        divider = Color(0xFF1F2C3F), tile = Color(0xFF1D2939),
        accent = Color(0xFF4FD1C5), accentSoft = Color(0xFF1D4148),
        isLight = false, motion = ThemeMotion.AURORA, accent2 = Color(0xFF6C8CFF),
    )

    val TITANIUM = PhoneTheme(
        id = "titanium", name = "钛光",
        bg = Color(0xFF1A1B1E), card = Color(0xFF34373C), border = Color(0xFF4A4E54),
        ink = Color(0xFFF5F2EC), ink2 = Color(0xFFCFCAC0), muted = Color(0xFFAAA69E),
        divider = Color(0xFF40444A), tile = Color(0xFF3E4147),
        accent = Color(0xFFC08B52), accentSoft = Color(0xFF4A3B2A),
        isLight = false, motion = ThemeMotion.SHEEN, accent2 = Color(0xFFFFF6E8),
    )

    val DUSK = PhoneTheme(
        id = "dusk", name = "暮光",
        bg = Color(0xFF0A0F26), card = Color(0xFF281E3E), border = Color(0xFF3E3060),
        ink = Color(0xFFF0ECFA), ink2 = Color(0xFFCBC2DE), muted = Color(0xFFA096B4),
        divider = Color(0xFF342A4E), tile = Color(0xFF332A4A),
        accent = Color(0xFFA082FF), accentSoft = Color(0xFF3B2C63),
        isLight = false, motion = ThemeMotion.BREATHE, accent2 = Color(0xFF7C5CFF),
    )

    val OBSIDIAN = PhoneTheme(
        id = "obsidian", name = "曜金",
        bg = Color(0xFF08080A), card = Color(0xFF18181A), border = Color(0xFF2E2E31),
        ink = Color(0xFFF0EEE8), ink2 = Color(0xFFC8C5BE), muted = Color(0xFF8C8A86),
        divider = Color(0xFF2A2A2C), tile = Color(0xFF222224),
        accent = Color(0xFFD9B26A), accentSoft = Color(0xFF3D3421),
        isLight = false, motion = ThemeMotion.SWEEP, accent2 = Color(0xFFD9B26A),
    )

    /**
     * 全部主题，顺序 = 主题页里左右滑动的顺序。
     *
     * ⚠ **紫夜放第一**（历史顺序，⛔ 换顺序会打乱用户已记住的主题位置），
     *   深空青蓝放第二（2026-11-07 起是默认主题），
     *   其余按"深色 → 浅色"排，滑起来色调过渡不会忽明忽暗。
     */
    val ALL: List<PhoneTheme> = listOf(
        // 前 5 套：紫夜（默认）+ **动态的几套**（深空青蓝/极光/钛光/暮光/曜金）排在一起 ——
        // 主题页里它们的预览自己在动，一眼能看出"这套是会动的"。
        NIGHT, GLASS, AURORA, TITANIUM, DUSK, OBSIDIAN,
        // 其余：静态深色 → 浅色（滑过去色调渐变，不忽明忽暗）
        GRAPHITE, MIDNIGHT, FOREST, CERAMIC, SAND,
    )

    /** ⚠ 找不到/没存过偏好时回落到默认主题（2026-11-07 起 = 深空青蓝）。 */
    fun byId(id: String?): PhoneTheme = ALL.firstOrNull { it.id == id } ?: GLASS
}

/**
 * 当前主题（全局单例）。
 *
 * ⚠ `current` 必须是 `mutableStateOf`：`AppUi.kt` 里的 `AppBg` 等都是
 *   `get() = ThemeManager.current.bg`，Compose 读到这个 State 才会在换主题时重组。
 *   写成普通 `var` 的话，颜色**不会刷新**（要重启 App 才生效），而且**不报错**。
 */
object ThemeManager {
    private const val SP = "phone_theme"
    private const val KEY = "current_id"

    var current by mutableStateOf(PhoneThemes.GLASS)
        private set

    /** 启动时调一次：把上次选的主题读回来。 */
    fun restore(ctx: Context) {
        val id = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE).getString(KEY, null)
        current = PhoneThemes.byId(id)
    }

    fun select(ctx: Context, theme: PhoneTheme) {
        current = theme
        ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
            .edit().putString(KEY, theme.id).apply()
    }
}
