package com.gemini.healthbridge.wear

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.animation.slideInVertically
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.cos
import kotlin.math.sin
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.TextUnit
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.ScalingLazyListState
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberScalingLazyListState
import com.gemini.healthbridge.core.Activity
import com.gemini.healthbridge.core.ActivitySource
import com.gemini.healthbridge.core.BiaProfileDto
import com.gemini.healthbridge.core.BiaSource
import com.gemini.healthbridge.core.BodyComposition
import com.gemini.healthbridge.core.Bp
import com.gemini.healthbridge.core.Cycle
import com.gemini.healthbridge.core.CycleSummaryMessage
import com.gemini.healthbridge.core.EcgNote
import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.HrvSource
import com.gemini.healthbridge.core.MfBia
import com.gemini.healthbridge.core.Odi
import com.gemini.healthbridge.core.Apnea
import com.gemini.healthbridge.core.Language
import com.gemini.healthbridge.core.ApneaSummaryMessage
import com.gemini.healthbridge.core.DietNotes
import com.gemini.healthbridge.core.Disclaimer
import com.gemini.healthbridge.core.EnergySummaryMessage
import com.gemini.healthbridge.core.Extras
import com.gemini.healthbridge.core.ExtrasSummaryMessage
import com.gemini.healthbridge.core.Ihrn
import com.gemini.healthbridge.core.OdiSummaryMessage
import com.gemini.healthbridge.core.Ppg
import com.gemini.healthbridge.core.PpgSource
import com.gemini.healthbridge.core.Ptt
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.coreText
import com.gemini.healthbridge.core.isBlankText
import com.gemini.healthbridge.core.Resp
import com.gemini.healthbridge.core.SleepNightDto
import com.gemini.healthbridge.core.SleepStageDto
import com.gemini.healthbridge.core.SleepStageKind
import com.gemini.healthbridge.core.Stress
import com.gemini.healthbridge.core.StressSummaryMessage
import com.gemini.healthbridge.core.TempSource
import com.gemini.healthbridge.core.Temperature
import com.gemini.healthbridge.core.db.ActivityEntity
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.BloodPressureEntity
import com.gemini.healthbridge.core.db.BodyCompositionEntity
import com.gemini.healthbridge.core.db.EcgRecordEntity
import com.gemini.healthbridge.core.db.HeartRateEntity
import com.gemini.healthbridge.core.db.HrvSessionEntity
import com.gemini.healthbridge.core.db.MfBiaEntity
import com.gemini.healthbridge.core.db.MfBiaSource
import com.gemini.healthbridge.core.db.PpgWaveEntity
import com.gemini.healthbridge.core.db.SpO2Entity
import com.gemini.healthbridge.core.db.TemperatureEntity
import com.gemini.healthbridge.core.db.EcgWaveEntity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** 三星健康风格配色 */
private val AccentOrange = Color(0xFFA8873F)
private val BarBlue = Color(0xFF7C8794)
private val PillGray = Color(0xFFE7E1D5)
private val SectionLine = Color(0x1A000000)

/**
 * 核心体温**估算**（模块 #10）—— 淡紫。
 *
 * 与手机端那张估算卡用的深紫同族，跨设备一眼能认出"这是推算值，不是测出来的"。
 * 与 [StageRemColor] 取值相同纯属巧合：一个出现在体温页、一个只出现在睡眠页，不会同屏。
 */
private val CoreEstimateColor = Color(0xFF6E5AA8)

/**
 * HRV（模块 #9）—— 青绿。
 *
 * 刻意避开心电那三档结论色（绿/橙/红）：HRV 不是"好/坏"的判定，
 * 用结论色会让人把"RMSSD 偏低"读成"心电异常"。青绿在手机端同款使用。
 */
private val HrvColor = Color(0xFF2F7D73)

/**
 * 脉搏到达时间（PAT，模块 #14）的配色。
 *
 * 与 [HrvColor] 刻意不同色：两者都在心电结果页上、都由**同一次会话**产出，
 * 但一个是心率变异性、一个是脉搏传播时延 —— 颜色一样会让人以为它们是同一个量的两个说法。
 */
private val PatColor = Color(0xFFB4603C)

/**
 * 压力 / 疲劳评分（模块 #12）—— 靛蓝，与手机端同款。
 *
 * 刻意与 [HrvColor]（青绿）分开：两者同源但一个是测量量（ms）、一个是折算量（分），
 * 同色会让人把评分当成又一个 HRV 指标去横向比较。
 */
private val StressColor = Color(0xFF3D4A8F)

/**
 * 呼吸率（模块 #11）—— 蓝绿，与手机端同款。
 *
 * 同样与 [HrvColor] 分开：呼吸率是从同一段光信号/间期里**另算出来**的一个物理量，
 * 共用配色会让人以为它是 HRV 卡的延伸（它甚至可以来自完全不同的那次测量）。
 */
private val RespColor = Color(0xFF1F7A72)

/**
 * 氧减指数 ODI（模块 #11 的睡眠侧）—— 深紫。
 *
 * 与 [RespColor]（呼吸率）刻意不同色：两者都是"呼吸相关"，但一个是**睡眠筛查指标**
 * （一晚一次、带分级），一个是**静息点检的测量值**。同色会让人把它们当成同一个指标的两个数。
 */
private val OdiColor = Color(0xFF5A3F94)

/** 能量评分（深色主题下用亮青，与手机端同一色系但不是同一支）。 */
private val EnergyColor = Color(0xFF1E7F8C)

/** 睡眠呼吸暂停（呼吸相关，与氧减的紫刻意分开）。 */
private val ApneaColor = Color(0xFF2E93A3)

// ── 首页配色（2026-09-19 照参考图 first.png 定）──────────────────────────────
//
// 参考图的盘面是**蓝色渐变**：顶部接近白的浅青，底部中蓝，主入口是浅底深字。
// 这里保留"上亮下深 + 浅底深字"的结构，但把亮度整体压一档 ——
// 参考图顶部那种 #C4E9FC 上压白字基本读不出来（AI 出图的常见不合理处），
// 而且手表是 AMOLED，大面积高亮更费电。
/**
 * 陶瓷皮肤（2026-10-18 改版第一版）。
 * 口径：暖白底 + 墨字 + 金线。装饰（径向底、刻度环、内圈发丝线）全部**一次性静态绘制**。
 */
/**
 * 一套完整配色。
 *
 * ⚠ 加新主题只要在 [ThemeDef.ALL] 里加一条数据，界面**全部自动跟着变**：
 *   所有绘制都走 `Skin.*`（getter 转发到 [Skin.current]），没有任何硬编码颜色。
 */
data class ThemeDef(
    val id: String,
    /** 显示名（直接是中文名，主题名不做翻译） */
    val name: String,
    val bgTop: Color,
    val bgBottom: Color,
    val ink: Color,
    val muted: Color,
    val gold: Color,
    val card: Color,
    val hairline: Color,
    val cardLine: Color,
    val btn: Color,
    val onBtn: Color,
    /**
     * 背景动效（2026-10-25）：0 = 静态（默认，零动画、零额外耗电），其余见 ANIM_* 常量。
     *
     * ⚠ 动效**只作用于背景光效**，按钮/字号/圆角/布局一律不动。
     */
    val anim: Int = ANIM_NONE,
    /** 动效的第二强调色（柔光/高光/弧光用），静态主题用不到。 */
    val accent2: Color = Color.Transparent,
) {
    companion object {
        const val ANIM_NONE = 0
        /** 极光：三团柔光沿环缓慢漂移 */
        const val ANIM_AURORA = 1
        /** 钛光：斜向高光带缓慢扫过 */
        const val ANIM_SHEEN = 2
        /** 暮光：中心柔光呼吸 */
        const val ANIM_BREATHE = 3
        /** 曜金：金色弧光绕圆心旋转 */
        const val ANIM_SWEEP = 4

        /** 陶瓷白：原始主题（默认）。 */
        val CERAMIC = ThemeDef(
            id = "ceramic",
            name = "陶瓷白",
            bgTop = Color(0xFFFDFBF7),
            bgBottom = Color(0xFFE9E4DA),
            ink = Color(0xFF1A1917),
            muted = Color(0xFF8C877C),
            gold = Color(0xFFB99B5E),
            card = Color(0xFFFFFFFF),
            hairline = Color(0x1A000000),
            cardLine = Color(0x1F8C8272),
            btn = Color(0xFF1B1B19),
            onBtn = Color(0xFFF7F4EC),
        )

        /** 深空灰（2026-10-24）：炭灰渐变 + 近白胶囊 + 暖金强调。AMOLED 上最省电。 */
        val GRAPHITE = ThemeDef(
            id = "graphite", name = "深空灰",
            bgTop = Color(0xFF2C2E32), bgBottom = Color(0xFF151618),
            ink = Color(0xFFF2F2F0), muted = Color(0xFF9A9C9F), gold = Color(0xFFC8A96B),
            card = Color(0xFF34373C), hairline = Color(0x18FFFFFF),
            cardLine = Color(0x2EFFFFFF),
            btn = Color(0xFFF2F2F0), onBtn = Color(0xFF1A1B1D),
        )

        /** 午夜蓝（2026-10-24）：深海蓝渐变 + 冷白胶囊 + 亮蓝强调。 */
        val MIDNIGHT = ThemeDef(
            id = "midnight", name = "午夜蓝",
            bgTop = Color(0xFF112440), bgBottom = Color(0xFF071224),
            ink = Color(0xFFE8F0FA), muted = Color(0xFF8FA6C0), gold = Color(0xFF7FB3FF),
            card = Color(0xFF17304F), hairline = Color(0x18FFFFFF),
            cardLine = Color(0x33FFFFFF),
            btn = Color(0xFFE8F0FA), onBtn = Color(0xFF10233F),
        )

        /** 松林绿（2026-10-24）：墨绿渐变 + 冷白胶囊 + 暗金强调。 */
        val FOREST = ThemeDef(
            id = "forest", name = "松林绿",
            bgTop = Color(0xFF14261E), bgBottom = Color(0xFF0A1510),
            ink = Color(0xFFE9F3EC), muted = Color(0xFF8FA99A), gold = Color(0xFFC9A227),
            card = Color(0xFF1D3529), hairline = Color(0x18FFFFFF),
            cardLine = Color(0x2EFFFFFF),
            btn = Color(0xFFE9F3EC), onBtn = Color(0xFF14261E),
        )

        /** 暖砂（2026-10-24）：米黄→驼色渐变 + 深棕胶囊 + 陶土橙强调。 */
        val SAND = ThemeDef(
            id = "sand", name = "暖砂",
            bgTop = Color(0xFFFBF3E6), bgBottom = Color(0xFFE8D6BD),
            ink = Color(0xFF2B2118), muted = Color(0xFF8B7358), gold = Color(0xFFC08A3E),
            card = Color(0xFFFFFDF8), hairline = Color(0x1A000000),
            cardLine = Color(0x268B7358),
            btn = Color(0xFF3A2B1E), onBtn = Color(0xFFFBF3E6),
        )

        /**
         * 全部主题。⚠ **顺序 = 设置页里的显示顺序**，别按字母重排。
         * 加新主题只需在这里追加一条（[ThemeDef] 的 10 个颜色值），
         * 布局/字号/圆角**一律不动**（用户 2026-10-24 明确要求"只改颜色"）。
         */
        /** 极光（动态）：深墨底 + 青碧/靛蓝/品红三团柔光缓慢漂移。 */
        val AURORA = ThemeDef(
            id = "aurora", name = "极光",
            bgTop = Color(0xFF0C1220), bgBottom = Color(0xFF05070C),
            ink = Color(0xFFECF2FA), muted = Color(0xFF929EB0), gold = Color(0xFF4FD1C5),
            card = Color(0xFF162030), hairline = Color(0x16FFFFFF),
            cardLine = Color(0x2AFFFFFF),
            btn = Color(0xFFECF2FA), onBtn = Color(0xFF0C1220),
            anim = ANIM_AURORA, accent2 = Color(0xFF6C8CFF),
        )

        /** 钛光（动态）：暖灰金属底 + 斜向高光带扫过。 */
        val TITANIUM = ThemeDef(
            id = "titanium", name = "钛光",
            bgTop = Color(0xFF34363A), bgBottom = Color(0xFF1A1B1E),
            ink = Color(0xFFF5F2EC), muted = Color(0xFFAAA69E), gold = Color(0xFFC08B52),
            card = Color(0xFF34373C), hairline = Color(0x18FFFFFF),
            cardLine = Color(0x2EFFFFFF),
            btn = Color(0xFFF0EBE1), onBtn = Color(0xFF26272A),
            anim = ANIM_SHEEN, accent2 = Color(0xFFFFF6E8),
        )

        /** 暮光（动态）：深紫→藏蓝，中心柔光呼吸。 */
        val DUSK = ThemeDef(
            id = "dusk", name = "暮光",
            bgTop = Color(0xFF201636), bgBottom = Color(0xFF0A0F26),
            ink = Color(0xFFF0ECFA), muted = Color(0xFFA096B4), gold = Color(0xFFA082FF),
            card = Color(0xFF281E3E), hairline = Color(0x16FFFFFF),
            cardLine = Color(0x2AFFFFFF),
            btn = Color(0xFFF0ECFA), onBtn = Color(0xFF1E1532),
            anim = ANIM_BREATHE, accent2 = Color(0xFF7C5CFF),
        )

        /** 曜金（动态）：纯黑底 + 金色弧光绕圆缓慢旋转。 */
        val OBSIDIAN = ThemeDef(
            id = "obsidian", name = "曜金",
            bgTop = Color(0xFF0C0C0E), bgBottom = Color(0xFF000000),
            ink = Color(0xFFF0EEE8), muted = Color(0xFF8C8A86), gold = Color(0xFFD9B26A),
            card = Color(0xFF18181A), hairline = Color(0x14FFFFFF),
            cardLine = Color(0x26FFFFFF),
            btn = Color(0xFFF0EEE8), onBtn = Color(0xFF0C0C0E),
            anim = ANIM_SWEEP, accent2 = Color(0xFFD9B26A),
        )

        val ALL: List<ThemeDef> = listOf(
            CERAMIC, GRAPHITE, MIDNIGHT, FOREST, SAND,
            AURORA, TITANIUM, DUSK, OBSIDIAN,
        )
        fun byId(id: String?): ThemeDef = ALL.firstOrNull { it.id == id } ?: CERAMIC
    }
}

/** 主题偏好（只在本地存 id；重启后由 [Skin.current] 读出来）。 */
object ThemeStore {
    private const val PREF = "ui_theme"
    private const val KEY = "theme_id"
    fun currentId(ctx: android.content.Context): String =
        ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE).getString(KEY, null)
            ?: ThemeDef.CERAMIC.id
    fun set(ctx: android.content.Context, id: String) {
        ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)
            .edit().putString(KEY, id).apply()
    }
}


/**
 * 配色入口：**所有界面都从这里取色**。
 * 改主题时只替换 [current]，然后 `recreate()` 重绘整棵树（与语言切换同一套做法）。
 */
private object Skin {
    var current: ThemeDef = ThemeDef.CERAMIC
    val BgTop get() = current.bgTop
    val BgBottom get() = current.bgBottom
    val Ink get() = current.ink
    val Muted get() = current.muted
    val Gold get() = current.gold
    val Card get() = current.card
    val Hairline get() = current.hairline
    val CardLine get() = current.cardLine
    /** 按钮底 */
    val Btn get() = current.btn
    /** 按钮上的字 */
    val OnBtn get() = current.onBtn
}

/**
 * 界面缩放系数（基准 227dp 屏宽 = 454px @ 2.0x）。
 *
 * ⚠⚠ 为什么需要它：字号写死 `sp` 时，同一串代码在 480 的表上就比 396 的表上**视觉更小**
 *   （用户 2026-10-21 反馈"450 跟 480 的按钮里字体还能再大一点"就是这件事）。
 *   ⇒ 所有页面字号统一走 [fsp]，按屏宽等比缩放，大屏自然会大一圈。
 *
 * ⚠⚠ 227 **不是任何一代真机的宽度**，它是 `wearos_large_round` 模拟器 profile（454px）——
 *   整套版式就是按它画的，所以它是"设计基准"，不要改成某台真机的值（那等于把所有页面重排）。
 *   真机实际只有这几档（px / dp，density 2.0）：
 *     396/198（Watch4·5 40mm）· 408/204（Ultra2）· 432/216（Watch6·7 40mm）·
 *     438/219（Watch8 40mm）· 450/225（44·45mm）· 480/240（44mm 480 档）
 *   ⇒ 比值区间 **0.872 ~ 1.057**。
 *
 * ⚠⚠⚠ 下限**必须 ≤ 0.87**（198 / 227 = 0.872）。2026-10-27 之前是 0.90：40mm（198dp）上
 *   算得 0.87 却被抬到 0.90 ⇒ 内容按 204dp 排版、硬塞进 198dp，**横向溢出约 3%**
 *   （圆屏两侧先被裁，行尾文字被切）。上下限只做"补偿"、不做"重排"，但不能互相打架。
 *   守卫：静态校验脚本。
 *
 * ⚠ 上限 1.10 够用（240 / 227 = 1.057），不必放宽；再大就该重新量圆屏几何（按钮会撞圆弧）。
 */
private val LocalUiScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** 按屏宽缩放后的字号。**所有新写的字号都应该走这个**（写死 sp 会在大屏偏小）。 */
@Composable
private fun fsp(base: Float): TextUnit = (base * LocalUiScale.current).sp

private const val TWO_PI = 6.2831855f

/**
 * 盘面背景（2026-10-25 起支持**动态渐变**）。
 *
 * - 底色永远是那一层径向渐变（与旧版完全一致，静态主题零额外开销）；
 * - [phase] 0~1 是动画相位，只有 `anim != ANIM_NONE` 的主题才会用到它；
 * - ⚠ 动效**只叠背景光**，不碰任何子内容 —— 所以按钮/字号/圆角/布局都不变。
 * - ⚠ 逐帧成本很低：每次 = 1 个整屏渐变 + 1~3 个径向/线性渐变矩形，不做模糊、不抓快照。
 */
private fun DrawScope.drawDial(phase: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            colors = listOf(Skin.BgTop, Skin.BgBottom),
            center = Offset(w / 2f, h * 0.16f),
            radius = size.maxDimension * 0.78f,
        )
    )
    val accent = Skin.current.accent2
    when (Skin.current.anim) {
        ThemeDef.ANIM_AURORA -> {
            // 三团柔光沿圆环漂移（互相错开 120°），用径向渐变模拟"极光"
            for (i in 0 until 3) {
                val ang = TWO_PI * (phase + i / 3f)
                val cx = w / 2f + cos(ang) * w * 0.22f
                val cy = h / 2f + sin(ang) * h * 0.18f
                val col = if (i == 1) accent else Skin.Gold
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(col.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(cx, cy),
                        radius = size.minDimension * 0.62f,
                    )
                )
            }
        }

        ThemeDef.ANIM_SHEEN -> {
            // 斜 28° 的高光带从左上扫到右下（金属拉丝反光）
            val ang = 0.4887f // 28°
            val dx = cos(ang)
            val dy = sin(ang)
            val span = w * 2f
            val along = (phase % 1f) * span - span / 2f
            val bx = w / 2f + dx * along
            val by = h / 2f + dy * along
            val band = w * 0.22f
            drawRect(
                Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        accent.copy(alpha = 0.26f),
                        Color.Transparent,
                    ),
                    start = Offset(bx - dx * band, by - dy * band),
                    end = Offset(bx + dx * band, by + dy * band),
                )
            )
        }

        ThemeDef.ANIM_BREATHE -> {
            // 中心柔光做半径+亮度呼吸（极慢，像呼吸灯）
            val k = 0.5f + 0.5f * sin(TWO_PI * phase)
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.12f + 0.16f * k), Color.Transparent),
                    center = Offset(w / 2f, h * 0.42f),
                    radius = size.minDimension * (0.42f + 0.10f * k),
                )
            )
        }

        ThemeDef.ANIM_SWEEP -> {
            // ⚠ 2026-10-25 实测修正：原来用「粗描边 drawArc + sweepGradient」做弧光，
            //   在真机/模拟器上渲染成**硬边扇形**（描边宽度超过半径时两端直接变成直边），
            //   看起来像一块遮罩而不是光。改成**绕圆心漂移的柔光**（径向渐变），
            //   观感是"金色光晕缓缓绕圈"，而且更便宜（1 个整屏渐变 vs 弧+扫掠）。
            val ang = TWO_PI * phase
            val cx = w / 2f + cos(ang) * w * 0.20f
            val cy = h / 2f + sin(ang) * h * 0.20f
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.60f,
                )
            )
            // 再叠一层更小更亮的芯，让"光"有层次（像金属反光点）
            drawRect(
                Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.24f,
                )
            )
        }
    }
}

private val HomeBgTop = Color(0xFFFDFBF7)
private val HomeBgBottom = Color(0xFFE9E4DA)

/** 浅色药丸上的深色文字。在蓝色盘面上，主次关系靠**明暗**表达，不靠大小。 */
private val HomeInk = Color(0xFF1A1917)

/** 主入口：不透明浅色药丸（对比度最高 → 最重要的那一个）。 */
private val HomePillLight = Color(0xFFFFFFFF)

/** 次级药丸：半透明白（比主入口弱一档，但底下的蓝色会透上来，不与主入口撞色）。 */
private val HomeChipFill = Color(0x14000000)
private val HomeChipBorder = Color(0x1F8C8272)

/** 心电波形。参考图里是白色/浅青 —— 蓝底上画绿线会显脏，所以不再用"心电绿"。 */
private val HomeStripe = Color(0xFFF0EADF)

/**
 * 多频阻抗（MF-BIA）—— 琥珀色。
 *
 * 与体成分的配色刻意不同：两者是同一次姿势下的**两个不同采集器**，
 * 同色会让人以为那是同一份数据的两种展示（一个是三星算好的体成分，一个是原始阻抗谱）。
 */
private val MfBiaColor = Color(0xFFB8862B)
private val StressRelaxedColor = Color(0xFF4F8A5B)
private val StressNormalColor = Color(0xFF7B8794)
private val StressElevatedColor = Color(0xFFB37A2A)
private val StressHighColor = Color(0xFFB04A46)

private fun stressLevelColor(level: String): Color = when (level) {
    Stress.Level.RELAXED -> StressRelaxedColor
    Stress.Level.ELEVATED -> StressElevatedColor
    Stress.Level.HIGH -> StressHighColor
    else -> StressNormalColor
}

// ── 睡眠阶段配色（与手机端一致：清醒粉红 / REM 淡紫 / 浅睡中紫 / 深睡深蓝）──
private val StageAwakeColor = Color(0xFFB33563)
private val StageRemColor = Color(0xFF7B66B0)
private val StageLightColor = Color(0xFF5B4BB8)
private val StageDeepColor = Color(0xFF2A348F)

// ── 体温配色（暖色系，与血氧的蓝紫、心率的橙明确区分开）──
/** 体温数值的主色。 */
private val TempValueColor = Color(0xFFB4572F)

/** 偏高（偏暖 → 橙红）/ 常见范围（绿）/ 偏低（偏冷 → 蓝）。 */
private val TempHighColor = Color(0xFFB4572F)
private val TempNormalColor = Color(0xFF4F8A5B)
private val TempLowColor = Color(0xFF4A7EA8)

private fun tempLevelColor(c: Float): Color = when (Temperature.level(c)) {
    -1 -> TempLowColor
    1 -> TempHighColor
    else -> TempNormalColor
}

/**
 * 睡眠阶段展示顺序（自上而下）与名称**资源 id**。
 * ⚠ 存 id 而不是字符串：这个表在**类体外**求值，那里既没有 Context 也不在 Composable
 * 作用域，翻译后的文案只能由 UI 层 `stringResource(name)` 现取。
 */
private val SleepStageOrder = listOf(
    SleepStageKind.AWAKE to R.string.stage_awake,
    SleepStageKind.REM to R.string.stage_rem,
    SleepStageKind.LIGHT to R.string.stage_light,
    SleepStageKind.DEEP to R.string.stage_deep,
)

/** 各阶段的典型范围（占总睡眠时间%），仅作参照带，不参与判定。 */
private val SleepStageTypical = mapOf(
    SleepStageKind.AWAKE to (0f to 9f),
    SleepStageKind.REM to (20f to 30f),
    SleepStageKind.LIGHT to (40f to 60f),
    SleepStageKind.DEEP to (10f to 20f),
)

private fun sleepStageColor(kind: String): Color = when (kind) {
    SleepStageKind.AWAKE -> StageAwakeColor
    SleepStageKind.REM -> StageRemColor
    SleepStageKind.LIGHT -> StageLightColor
    SleepStageKind.DEEP -> StageDeepColor
    else -> Color(0xFF9E9E9E)
}

/**
 * 毫秒 → 「5小时37分钟」（省略为 0 的部分）。圆屏空间紧张，所以不加空格。
 *
 * ⚠ 是 `@Composable`：它返回的是给用户看的串，语种跟着界面走。
 * 所有调用点都在 Composable 作用域里（`item` / `items` 的内容块、`Text` 的实参）。
 */
@Composable
private fun wearFmtDuration(ms: Long): String {
    val min = (ms / 60_000L).coerceAtLeast(0L)
    val h = min / 60
    val m = min % 60
    return when {
        h > 0 && m > 0 -> stringResource(R.string.s_90a99aab, h, m)
        h > 0 -> stringResource(R.string.s_dbf0f917, h)
        else -> stringResource(R.string.s_dc97892c, m)
    }
}

/**
 * 心电波形的固定纵轴刻度（±毫伏）。
 * 固定刻度而不是按窗口自动缩放：这样"这一次的 QRS 是不是够高"可以横向比较，
 * 也避免把噪声放大成看似正常的心电（幅度噪声与真实 QRS 的差异正是判定的依据）。
 */
private const val MV_FULL_SCALE = 1.5f

/**
 * **实时波形**的显示满量程（2026-10-16）。
 *
 * ⚠ 为什么和 [MV_FULL_SCALE] 分开：实时波形现在**先过陷波 + 0.5~40 Hz 带通**
 *   （为了去掉那条密锯齿，见 `EcgLiveWave` 的说明），带通会把 R 波的尖峰削掉一截
 *   —— 实测同段记录从 **±1.2 mV 收到 ±0.4 mV**。若仍按 1.5 mV 满量程画，
 *   波形只占三分之一高度、显得很矮。
 * ⚠ 但**不许改成自适应增益**：同一段里振幅忽大忽小会让人误判"这次心跳变弱了"。
 */
private const val MV_LIVE_FULL_SCALE = 1.0f

/** 心电采样率（实测 CSV 头 `fs=503Hz`）。滤波与时间轴都用它，别在别处再写一遍数字。 */
private const val ECG_FS_HZ = 503.0

/**
 * 心电「手指接触判定」的评估窗口（秒）：门控**只看最近这么多秒**。
 *
 * ⚠ 不能用"从按下开始按钮以来的全部数据"：判定必须反映**当前**接触状态，
 *   而且测量中的抽查用的是 `takeLast(fsNow * 8)` —— 同一件事两处口径不同迟早漂移
 *   （`EcgSignal` 类注释里那条"两边各写一套必然漂移"）。
 *   ⚠ 注意实测结论（接触检测台架）：**窗口里残留几秒未接触数据
 *   并不会把 `crest` 压到门限以下**（悬空段是高能量段，而 20 分位仍落在心电段里），
 *   所以本常量解决的是**判定滞后**，不是"手指放上去却测不了"的病根。
 */
private const val CONTACT_WINDOW_SEC = 8.0

/**
 * 在接触页停留超过这么久仍未建立接触 ⇒ 判定为「卡住」，把装饰性的图示换成波形导出口。
 *
 * ⚠ touch 页的内容总高**本来就超出 240dp 圆屏**（历史上为此把装饰心形从 88dp 压到 56dp，
 *   见 `TouchStartArt` 的注释），所以**任何追加到末尾的控件都会被圆形遮罩切掉**。
 *   卡住时用按钮**替换**图示 ⇒ 净高度不变，而且落在最显眼的位置。
 */
private const val CONTACT_STUCK_MS = 30_000L

/** 心跳动画的节拍缩放：一个周期内两次搏动（主搏 + 回搏），模拟真实脉搏手感。 */
private fun beatScale(t: Float): Float = when {
    t < 0.12f -> 1f + 0.14f * (t / 0.12f)
    t < 0.25f -> 1.14f - 0.14f * ((t - 0.12f) / 0.13f)
    t < 0.35f -> 1f + 0.09f * ((t - 0.25f) / 0.10f)
    t < 0.50f -> 1.09f - 0.09f * ((t - 0.35f) / 0.15f)
    else -> 1f
}

/**
 * 一个心动周期（P-QRS-T）的归一化模板，`p ∈ [0,1)` 为周期内相位，返回约 -0.35..1.0 的幅度。
 *
 * 用五个高斯峰叠加，而不是折线：折线在滚动时会有明显棱角，高斯叠加的曲线在任意相位都连续，
 * 首页的滚动心电条看上去才是"在动"而不是"在跳格子"。
 * 各峰中心取在 0.18/0.30/0.35/0.41/0.64，两端（p=0 与 p=1）都衰减到 ~0，因此首尾天然衔接。
 */
private fun ecgTemplate(p: Float): Float {
    fun g(mu: Float, sigma: Float): Float {
        val d = (p - mu) / sigma
        return kotlin.math.exp(-0.5f * d * d)
    }
    return 0.12f * g(0.18f, 0.022f) -   // P 波
        0.18f * g(0.30f, 0.011f) +      // Q 谷
        1.00f * g(0.35f, 0.011f) -      // R 峰
        0.32f * g(0.41f, 0.016f) +      // S 谷
        0.24f * g(0.64f, 0.040f)        // T 波
}

/**
 * 可空数值的占位显示：`null` 时给 `--`。
 *
 * ⚠ 它存在的理由看着很怪但很硬：**字符串模板里不许嵌 ASCII 引号**。
 *   给可空值写兜底显示时，一旦引号出现在模板内部，文案提取器就会在内层引号处
 *   判定字符串结束，抓到**半截**串 —— 译文库里于是只剩「Lowest」加半截表达式
 *   这种垃圾，而编译、运行**都不报错**。
 *   把兜底逻辑抽成函数，模板里就没有引号了。
 * ⚠ `--` 是**语言无关的占位符**，不参与翻译。
 */
private fun dash(v: Any?): String = v?.toString() ?: "--"

/** 可空的补充说明包成括号后缀，没有就给空串（同上：为了不在模板里嵌引号）。 */
@androidx.compose.runtime.Composable
private fun parenSuffix(v: ResText?): String = v?.let { "（${resText(it)}）" } ?: ""

class MainActivity : ComponentActivity() {

    private lateinit var measurer: HealthMeasurer
    private lateinit var sensorClient: SamsungSensorClient
    private lateinit var spo2Sensor: Spo2SensorManager
    private lateinit var tempSensor: TempSensorManager
    private lateinit var biaSensor: BiaSensorManager

    /** 多频阻抗（原始阻抗谱）：与体成分是两个独立采集器，见 `MfBiaSensorManager`。 */
    private lateinit var mfBiaSensor: MfBiaSensorManager
    private lateinit var ecgMeasurer: EcgMeasurer

    /** 传感器能力探测（排障页，见 [SensorProbe]）。只在用户点开时占传感器，不后台常驻。 */
    private lateinit var sensorProbe: SensorProbe

    /** PPG 逐拍间期 → HRV（见 [IbiRecorder]）。2 分钟手动会话，**不需要电极**。 */
    private lateinit var ibiRecorder: IbiRecorder
    private lateinit var ppgRecorder: PpgRecorder
    private lateinit var accelSampler: AccelSampler
    private lateinit var engine: SyncEngine
    private lateinit var bt: BluetoothSync
    private lateinit var db: AppDatabase

    private val timeFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

    /** 当前页面（供返回手势使用） */
    private var screen by androidx.compose.runtime.mutableStateOf("cover")

    /** 首页再次滑动确认退出 */
    internal var exitArmed by androidx.compose.runtime.mutableStateOf(false)

    /**
     * 界面语言（模块 #15）：把用户选的语言套进本 Activity 的 Context。
     *
     * ⚠⚠ **必须在这里、不能改用 `onCreate` 里的 `updateConfiguration`** ——
     * API 24+ 上那边改动会被 `recreate()` 重置（第一版就是这么写的，表现是
     * "选完语言界面一个字不变"）。详见手机端同名方法与 `LanguageStore` 文件头。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashReporter.install(this)
        db = AppDatabase.get(this)
        measurer = HealthMeasurer(this, lifecycleScope)
        // 血氧/心电/体温共用一个三星 HealthTrackingService 连接（多实例并存疑似导致心电闪退）
        sensorClient = SamsungSensorClient(this)
        spo2Sensor = Spo2SensorManager(sensorClient)
        tempSensor = TempSensorManager(sensorClient)
        biaSensor = BiaSensorManager(sensorClient)
        mfBiaSensor = MfBiaSensorManager(sensorClient)
        ecgMeasurer = EcgMeasurer(sensorClient)
        sensorProbe = SensorProbe(sensorClient, lifecycleScope)
        ibiRecorder = IbiRecorder(sensorClient, lifecycleScope) { accelSampler.state.value.level }
        // 结算后落库：与心电路径同一条 —— 只存**未过滤**的间期与测量条件，指标现算
        // （`Hrv.analyze` 要用"约 2 倍长"这条线索做伪迹修正，预先过滤就把它销毁了）。
        ibiRecorder.onSession = { raw, _, level ->
            lifecycleScope.launch {
                runCatching {
                    db.hrvDao().insert(
                        HrvSessionEntity(
                            measuredAt = System.currentTimeMillis(),
                            // 跨度 = 这批间期之和（= 它覆盖了多长时间）。
                            // 心电那条用采集时长，两者含义相同、都只是"跨度"的近似；
                            // 真正决定信度分级的 span 由 `Hrv.analyze` 内部按有效间期算。
                            durationMs = raw.sumOf { it.toLong() },
                            rrCount = raw.size,
                            rrMs = Hrv.encodeRr(raw),
                            // SDK 在同一个包里独立算出的心率（中位数）。只作参照、不参与 HRV 统计，
                            // 但它让手机端也能做那条"独立一致性校验"。
                            refHr = ibiRecorder.state.value.sdkHr,
                            // 没有心电就没有"节律规整度"，同样留 null。
                            ecgRegular = null,
                            activityLevel = level,
                            // ⚠ 与心电**分成两个 source**：RR 是 R 峰间期、IBI 是脉搏间期，
                            //   混进同一序列会让基线互相污染（见 Hrv.kt 类注释）。
                            source = HrvSource.WATCH_PPG,
                        )
                    )
                    bt.syncAuto()
                }
            }
        }

        // PPG 原始光信号（模块 #8）：**搭血氧的车**，不参与单 owner 连接的协商 ——
        // 它自己不 connect/disconnect，只在血氧测量期间去取一个 PPG_CONTINUOUS tracker。
        // 详见 `PpgRecorder` 的类注释（为什么这样切分，以及失败面怎么关在本地）。
        ppgRecorder = PpgRecorder(this, sensorClient, lifecycleScope)
        ppgRecorder.onSession = { s ->
            lifecycleScope.launch {
                runCatching {
                    db.ppgWaveDao().insert(
                        PpgWaveEntity(
                            measuredAt = s.measuredAt,
                            durationMs = s.durationMs,
                            sampleCount = s.sampleCount,
                            sampleRateHz = s.sampleRateHz,
                            channels = s.channels,
                            dcGreen = s.stats.dc,
                            acGreenP2P = s.stats.acP2P,
                            quality = s.stats.quality,
                            estimatedHr = s.stats.estimatedHr,
                            refHr = s.stats.refHr,
                            statusSeen = s.statusSeen,
                            greenWave = s.greenWave,
                            redWave = s.redWave,
                            irWave = s.irWave,
                            source = PpgSource.WATCH,
                        )
                    )
                }
                // 与其它记录一致：写完就试一次自动同步（未连接时静默跳过，等重连后全量补）
                bt.syncAuto()
            }
        }

        // 活动采集（模块 #7）：走平台 SensorManager，不占用三星 SDK 那条单 owner 连接，
        // 所以它可以在任何测量进行期间**同时**采集。App 前台时每 60 秒产出一段。
        accelSampler = AccelSampler(this)
        accelSampler.onSegment = { seg ->
            lifecycleScope.launch {
                runCatching {
                    db.activityDao().insert(
                        ActivityEntity(
                            startedAt = seg.startedAt,
                            durationMs = seg.durationMs,
                            sampleCount = seg.stats.sampleCount,
                            level = seg.stats.level,
                            meanG = seg.stats.meanG,
                            stdG = seg.stats.stdG,
                            p95G = seg.stats.p95G,
                            stillRatio = seg.stats.stillRatio,
                            peakJerkG = seg.stats.peakJerkG,
                            meanXG = seg.stats.meanXG,
                            meanYG = seg.stats.meanYG,
                            meanZG = seg.stats.meanZG,
                            envelope = seg.envelope,
                            source = ActivitySource.WATCH,
                        )
                    )
                }
                // 与其它记录一致：写完就试一次自动同步（未连接时静默跳过，等重连后全量补）
                bt.syncAuto()
            }
        }
        engine = SyncEngine(this, db, lifecycleScope)

        // 单次测量模式：只在会话结束时保存一条平均值记录（见 completeHeartSession），
        // 不再逐样本入库，避免一次测量产生两条数据
        measurer.onSample = null

        engine.start()
        bt = BluetoothSync(this, db, lifecycleScope)
        bt.startAutoRetry()

        // 启动时做一次血氧越界值自愈：早先的单位换算 bug 往库里写过 9700% 这种行，
        // 它们会被 (source, measuredAt) 去重挡住、永远显示错误。App 一启动就清掉，
        // 不必等手机再推一次数据。取值域之外的删除可证明安全（血氧不可能 >100%）。
        lifecycleScope.launch {
            runCatching { db.spo2Dao().deleteOutOfRange() }
        }

        // 启动即套用已保存的主题（默认陶瓷白）
        Skin.current = ThemeDef.byId(ThemeStore.currentId(this))

        // OTA：先尝试 DataLayer 监听（手机推包过来时自动收下并拉起安装）。
        // ⚠ 它内部会先探测有没有已连接节点；没有就不注册监听（不占资源）。
        // ⚠ 2026-10-26 蓝牙收包链路已删 ⇒ 不再需要"打开 App 就开 10 分钟接收窗口"，
        //   也不需要 `WatchOtaClient.start()`（那只注册 DataLayer/RFCOMM 监听）。
        //   手表更新统一走设置页的「检查更新」（`updateFromWifi`）。

        setContent { WearApp() }

        // 左缘右滑（系统返回手势）：上一级；首页首次提示、再次退出
        onBackPressedDispatcher.addCallback(this) { handleBack() }
    }

    /**
     * 活动采集跟随前台状态。
     *
     * Wear OS 不允许后台常驻采样，所以"后台隐式采集"在这里的准确含义是：
     * **用户不需要点任何东西，打开 App 期间自动在采**。`onPause` 里 stop() 会把最后
     * 不满 60 秒的一段也结算掉（够长才结算，见 AccelSampler）。
     */
    override fun onResume() {
        super.onResume()
        accelSampler.start()
        // 万一上一轮"淡出后退出"没走完（被系统拦下 / 又回到前台），把透明度复位，
        // 否则整页会停在 alpha=0 上，看=黑屏（虽然底是陶瓷的）。
        if (!isFinishing) exiting.value = false
    }

    override fun onPause() {
        super.onPause()
        accelSampler.stop()
    }

    /**
     * 功能页返回时应回到哪个菜单（basic / pro）。
     *
     * ⚠ 2026-10-20 用户反馈：功能页里的「返回」有的回到入口页(Measure/Pro/Settings)、
     *   有的回到测量菜单 —— 因为所有 `onBack` 都写死 `screen = "home"`。
     *   现在由 [WearApp] 在进入 basic / pro 时记下来，返回统一走这里。
     */
    internal var backTarget: String = "basic"

    /**
     * 正在「淡出后退出」。
     *
     * ⚠⚠ 2026-10-21：封面右滑退出时原来是 `finish()` **立刻**执行 —— 窗口在一帧里
     *   消失，用户看到的是"黑一下 / 闪屏"。现在先让内容 130ms 淡到全透明
     *   （底下露出的不是黑，是 `themes.xml` 里同色陶瓷的 `windowBackground`），再 finish。
     */
    private val exiting = androidx.compose.runtime.mutableStateOf(false)

    private fun requestFinish() {
        if (exiting.value) return
        exiting.value = true
        // 比淡出时长多留 40ms，保证动画至少走完一帧再摘窗口
        window.decorView.postDelayed({ if (!isFinishing) finish() }, 170)
    }

    private fun handleBack() {
        // ⚠ 2026-10-20（第二次修）：之前漏了大量屏幕 id，它们落到 `else -> "home"`，
        //   而 "home" 已不是有效 id ⇒ AnimatedContent 的 else 分支 = HubScreen（入口页）。
        //   这正是"左往右滑有的回测量、有的回入口页"的根因。
        //   规则：**所有功能页一律回「基础测量」**；菜单层之间才走 cover/hub 关系。
        when (screen) {
            // 封面：再滑一次就是「退出」（Wear 习惯：首页再返回 = 退到表盘）
            "cover" -> requestFinish()
            "hub" -> screen = "cover"
            "basic", "pro", "more" -> screen = "hub"
            "settings" -> screen = "hub"

            // 有传感器会话要收尾的页面
            "offbody" -> {
                if (measurer.state.value.measuring) measurer.stop()
                screen = backTarget
            }
            "measure" -> {
                if (measurer.state.value.measuring) measurer.stop()
                screen = backTarget
            }
            "spo2" -> {
                runCatching { ppgRecorder.finish(null) }
                if (spo2Sensor.state.value.measuring) spo2Sensor.stopMeasurement()
                screen = backTarget
            }
            "ecg" -> {
                if (ecgMeasurer.state.value.measuring) ecgMeasurer.stopMeasurement()
                screen = backTarget
            }
            "temp" -> {
                if (tempSensor.state.value.measuring) tempSensor.stopMeasurement()
                screen = backTarget
            }
            "bia" -> {
                if (biaSensor.state.value.measuring) biaSensor.stopMeasurement()
                screen = backTarget
            }
            "mfbia" -> {
                if (mfBiaSensor.state.value.measuring) mfBiaSensor.stopMeasurement()
                screen = "bia"
            }
            "ibi" -> {
                ibiRecorder.discard()
                screen = backTarget
            }
            "probe" -> {
                sensorProbe.stop()
                screen = backTarget
            }
            "sleep_stage" -> screen = "sleep"

            // 其余（stress / energy / apnea / odi / cycle / extras_* / add_bp / add_ecg / sleep…）
            // 全部回「基础测量」菜单
            else -> screen = backTarget
        }
    }

    /** 开始任一测量前调用：停掉全部进行中的测量，避免传感器占用冲突与残留。 */
    internal fun stopAllMeasurements() {
        runCatching { if (measurer.state.value.measuring) measurer.stop() }
        runCatching { if (spo2Sensor.state.value.measuring) spo2Sensor.stopMeasurement() }
        runCatching { if (tempSensor.state.value.measuring) tempSensor.stopMeasurement() }
        runCatching { if (biaSensor.state.value.measuring) biaSensor.stopMeasurement() }
        runCatching { if (mfBiaSensor.state.value.measuring) mfBiaSensor.stopMeasurement() }
        runCatching { if (ecgMeasurer.state.value.measuring) ecgMeasurer.stopMeasurement() }
        // 探测页会自己 connect 共享服务并注册监听，属于"占用中"，一并停掉
        runCatching { sensorProbe.stop() }
        // HRV 光测同理：它自己 connect 并注册心率监听
        runCatching { ibiRecorder.discard() }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.stop()
        measurer.stop()
        // 先结算光信号（此时共享服务可能还连着，tracker 能干净释放），再断开传感器服务
        ppgRecorder.finish(null)
        spo2Sensor.stopMeasurement()
        tempSensor.stopMeasurement()
        biaSensor.stopMeasurement()
        mfBiaSensor.stopMeasurement()
        ecgMeasurer.stopMeasurement()
        sensorProbe.stop()
        ibiRecorder.discard()
        accelSampler.stop()
        bt.close()
    }

    @Composable
    private fun WearApp() {
        val scope = rememberCoroutineScope()
        val hrState by measurer.state.collectAsState()
        val spo2SensorState by spo2Sensor.state.collectAsState()
        val tempSensorState by tempSensor.state.collectAsState()
        val biaState by biaSensor.state.collectAsState()
        val ecgState by ecgMeasurer.state.collectAsState()
        val btState by bt.state.collectAsState()
        // 「上传波形」的结果（2026-10-05）。⚠ 与上面的 `btState` **不是同一件事**：
        //   那个渲染在首页状态条上，而导出按钮在接触页/结果页 —— 用户点完根本看不到。
        val btEcgExport by bt.ecgExport.collectAsState()
        // ⚠ 状态用结构化字段，别拿显示文案去 contains("已连接")（模块 #15）
        val btConnected by bt.connected.collectAsState()
        val cycleSummary by bt.cycleSummary.collectAsState()
        val stressSummary by bt.stressSummary.collectAsState()
        val odiSummary by bt.odiSummary.collectAsState()
        val energySummary by bt.energySummary.collectAsState()
        val apneaSummary by bt.apneaSummary.collectAsState()
        val probeState by sensorProbe.state.collectAsState()
        val extrasSummary by bt.extrasSummary.collectAsState()
        val ibiState by ibiRecorder.state.collectAsState()

        // 字号缩放（见 [LocalUiScale] 注释）：按屏宽相对 227dp 基准等比放大/缩小。
        // ⚠ 下限 0.86 而不是 0.90 —— 真机最窄一档是 198dp（40mm），198/227 = 0.872，
        //   抬到 0.90 会让内容按 204dp 排版挤进 198dp（横向溢出 ~3%）。守卫静态校验脚本。
        val uiScale = (LocalConfiguration.current.screenWidthDp / 227f).coerceIn(0.86f, 1.10f)

        // 退出时的淡出（见 [requestFinish]）：内容先淡到 0（露出陶瓷窗口底色、不是黑），
        // 再真正 finish()。不这么做就是"窗口一帧消失"，用户会以为闪屏。
        val exitAlpha by animateFloatAsState(
            targetValue = if (exiting.value) 0f else 1f,
            animationSpec = tween(if (exiting.value) 130 else 0),
            label = "exitAlpha",
        )

        // 运行时权限：蓝牙直连 + 心率传感器 + 三星额外健康数据（血氧必需）
        val permLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            bt.connectAsync()
        }
        LaunchedEffect(Unit) {
            val perms = buildList {
                if (Build.VERSION.SDK_INT >= 31) add(Manifest.permission.BLUETOOTH_CONNECT)
                if (Build.VERSION.SDK_INT >= 29) add(Manifest.permission.ACTIVITY_RECOGNITION)
                add(Manifest.permission.BODY_SENSORS)
                add("com.samsung.android.hardware.sensormanager.permission.READ_ADDITIONAL_HEALTH_DATA")
            }
            permLauncher.launch(perms.toTypedArray())
        }

        // 测量期间保持屏幕常亮（息屏会暂停心率/血氧/体温/体成分采样）
        LaunchedEffect(
            hrState.measuring, spo2SensorState.measuring,
            tempSensorState.measuring, ecgState.measuring, biaState.measuring,
            probeState.running, ibiState.measuring,
        ) {
            if (hrState.measuring || spo2SensorState.measuring ||
                tempSensorState.measuring || ecgState.measuring || biaState.measuring ||
                probeState.running || ibiState.measuring
            ) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        // 未佩戴/传感器不可用检测：持续约 2.5 秒 → 提示页；恢复后自动返回测量页
        LaunchedEffect(hrState.measuring, hrState.availability) {
            // ⚠ `availability` 是**待求值**文案，这里却要拿它当**判据**（SDK 给的
            //   `UNAVAILABLE_*` 码）。用 `coreText()` 求值再比 —— 与改造前逐字等价，
            //   且不依赖 Compose 作用域（`LaunchedEffect` 里调不了 `resText`）。
            val off = hrState.measuring && hrState.availability.coreText().startsWith("UNAVAILABLE")
            if (off) {
                delay(2_500)
                if (measurer.state.value.measuring &&
                    measurer.state.value.availability.coreText().startsWith("UNAVAILABLE") &&
                    screen == "measure"
                ) {
                    screen = "offbody"
                }
            } else if (screen == "offbody") {
                screen = if (measurer.state.value.measuring) "measure" else "home"
            }
        }

        // 未佩戴检测：持续约 2.5 秒 → 提示页；戴好后自动返回测量页
        LaunchedEffect(hrState.measuring, hrState.availability) {
            val off = hrState.measuring &&
                hrState.availability.coreText().startsWith("UNAVAILABLE")
            if (off) {
                delay(2_500)
                if (hrState.measuring &&
                    hrState.availability.coreText() == "UNAVAILABLE_DEVICE_OFF_BODY" &&
                    screen == "measure"
                ) {
                    screen = "offbody"
                }
            } else if (screen == "offbody") {
                screen = "measure"
            }
        }

        // 「刚从录血压跳来」的标记：EcgScreen 据此显示配对引导（见那里）。
        var ecgCalibHint by remember { mutableStateOf(false) }
        var pendingHr by remember { mutableIntStateOf(0) }
        var pendingBp by remember { mutableIntStateOf(0) }
        var pendingEcg by remember { mutableIntStateOf(0) }
        var pendingTemp by remember { mutableIntStateOf(0) }
        var pendingBia by remember { mutableIntStateOf(0) }
        // 上次崩溃报告（CrashReporter 记录，首页显示便于拍屏反馈）
        var crashText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { crashText = CrashReporter.last(this@MainActivity) }

        val listState: ScalingLazyListState = rememberScalingLazyListState()
        // 「更多功能」页单独一份滚动状态。与首页共用会让两页的滚动位置互相干扰
        // （在首页滚到一半、点进"更多"，结果那一页也停在中间 —— 看着像错位）。
        val moreListState: ScalingLazyListState = rememberScalingLazyListState()
        // 新导航：基础测量 / 专业测量各一份滚动状态
        val basicListState = rememberScalingLazyListState()
        val proListState = rememberScalingLazyListState()
        // ⚠ 2026-10-20 用户反馈："从测量里往下翻、点开功能再退出，回到的是列表最上面"
        //   ⇒ 滚动位置必须提到 WearApp（页面销毁后仍保留），不能放在页面内部的 remember。
        val basicScroll = rememberScrollState()
        val proScroll = rememberScrollState()
        val settingsScroll = rememberScrollState()

        // 语言选择弹窗（模块 #15）。
        var showLanguage by remember { mutableStateOf(false) }
        var showTheme by remember { mutableStateOf(false) }
        // 功能页返回回到"来的那个菜单"（基础测量 / 专业测量）
        var gridReturn by remember { mutableStateOf("basic") }

        suspend fun refreshCounts() {
            pendingHr = db.heartRateDao().unsyncedCount()
            pendingBp = db.bloodPressureDao().unsyncedCount()
            pendingEcg = db.ecgDao().unsyncedCount()
            pendingTemp = db.temperatureDao().unsyncedCount()
            pendingBia = db.bodyCompositionDao().unsyncedCount()
        }

        LaunchedEffect(Unit) {
            refreshCounts()
            while (true) {
                delay(10_000)
                refreshCounts()
            }
        }

        Scaffold(
            // ⚠⚠ 2026-10-19 去掉系统 TimeText：它用主题里的**浅色字**，在陶瓷奶白底上几乎看不见
            //   （真机截图里顶部 "12:35" 就是白字压白底），而且会与页面标题重叠。
            //   现在时间由封面自己画（深色、可读）。
            timeText = {},
            // ⚠ 2026-10-20 去掉系统 PositionIndicator：它是**竖直条**，和我们的圆弧滚动条重复
            //   （用户截图里心率页同时出现一弧一竖）。所有滚动提示统一走 ArcScrollBar。
            positionIndicator = {},
        ) {
            // 页面切换动效：轻推 + 交叉淡入淡出。
            // 原来是 `when (screen)` 直接硬切 —— 时间文本、滚动指示条、整页内容在同一帧里
            // 全部换掉，视觉上像"闪一下"，用户反馈的"滑动要流畅"主要指这里。
            // ⚠⚠ 2026-10-18 改版：
            //   ① 导航动画全部关闭（原来 220ms 推入 + 淡入淡出）。老表每次翻页要重新合成
            //      两层整屏内容，是最明显的「卡一下」；OLED 上硬切反而更利落。
            //   ② 陶瓷底挪到根容器：原来只有首页/更多页自己画底，其它页没有背景 ⇒ 窗口默认
            //      深色底，配上反色后的墨字就是「黑字压黑底」（用户实测截图反馈）。
            // ⚠⚠ 2026-10-20 白字总根因：Wear Material 默认主题是**深色**，`onBackground` 是白字，
            //   凡是没显式写 color 的 Text 都会变白（在奶白底上看不见）。这里统一改成墨色。
            // ⚠ 2026-10-20：所有页面共用一层 Box，给"用 ScalingLazyColumn 的页面"补圆弧滚动条
            //   （这类页面没有 ScrollState，只能从 listState.layoutInfo 推位置）。
            // ⚠⚠ 2026-10-21 陶瓷底挪到 **AnimatedContent 之外**（原来挂在 AnimatedContent 的
            //   modifier 上）：翻页是两层内容交叉淡入淡出，底色若跟着一起淡，中间那几帧
            //   两层都半透明 ⇒ 露出 Scaffold/窗口底色 ⇒ 用户看到的"黑一下"。
            //   底色固定在最外层，只有内容参与动画，才可能"连贯"。
            // 背景动画相位：**只有动态主题才启动 ticker**（静态主题零开销）。
            // ⚠ 用 11fps 的低帧率驱动：这么慢的光效，60fps 与 11fps 肉眼看不出差别，
            //   但耗电差一倍以上（手表上每一帧都是真金白银）。
            val animPhase = androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableFloatStateOf(0f)
            }
            if (Skin.current.anim != ThemeDef.ANIM_NONE) {
                androidx.compose.runtime.LaunchedEffect(Skin.current.id) {
                    // ⚠ 2026-10-25 用户反馈"有点卡顿"：11fps(90ms) 提不起来，改 **25fps(40ms)**。
                    //   步长同步改成 0.002 ⇒ 一圈仍然是 **20 秒**（只变顺滑，不变快）。
                    while (true) {
                        kotlinx.coroutines.delay(40)
                        animPhase.value = (animPhase.value + 0.002f) % 1f
                    }
                }
            }
            Box(Modifier.fillMaxSize().drawBehind { drawDial(animPhase.value) }) {
            androidx.compose.runtime.CompositionLocalProvider(
                // 字号按屏宽缩放（见 [LocalUiScale]）
                LocalUiScale provides uiScale,
                androidx.wear.compose.material.LocalContentColor provides Skin.Ink,
            ) {
            AnimatedContent(
                targetState = screen,
                // 退出淡出：只淡**内容**，陶瓷底留在原地（淡到全透明时看到的是陶瓷，不是黑）
                modifier = Modifier.fillMaxSize().graphicsLayer { alpha = exitAlpha },
                transitionSpec = {
                    // 2026-10-20 用户反馈"返回太直接"：不再硬切。
                    // ⚠⚠ 2026-10-21 用户反馈"点一下先黑一下再跳页"+"要连贯的过渡"：
                    //   ① 黑一下的根因**不是**动效，是封面那个**整屏 clickable 的默认按压指示**
                    //      （`DefaultDebugIndication` = 全屏 12% 黑）—— 手指按下就铺满整块屏。
                    //      已在 [CoverScreen] 里改成 `indication = null` + 自定义按压缩放。
                    //   ② 动效改成**同一方向的推页**：走的时候一起走、来的时候一起进，
                    //      时长/缓动一致 ⇒ 视觉上是一条连续运动，而不是两层各淡各的。
                    val dur = 300
                    val ease = FastOutSlowInEasing
                    when {
                        // 封面 → 入口：整体往上推（封面上移退出，入口从下方接上来）
                        targetState == "hub" && initialState == "cover" ->
                            (slideInVertically(tween(dur, easing = ease)) { it / 2 } + fadeIn(tween(160)))
                                .togetherWith(
                                    slideOutVertically(tween(dur, easing = ease)) { -it / 4 } +
                                        fadeOut(tween(220)),
                                )
                        // 入口 → 封面：反过来往下推（与上面严格对称，来回都像同一套动作）
                        targetState == "cover" && initialState == "hub" ->
                            (slideInVertically(tween(dur, easing = ease)) { -it / 4 } + fadeIn(tween(160)))
                                .togetherWith(
                                    slideOutVertically(tween(dur, easing = ease)) { it / 2 } +
                                        fadeOut(tween(220)),
                                )
                        else ->
                            (fadeIn(tween(170)) + slideInHorizontally(tween(170)) { it / 16 }) togetherWith
                                (fadeOut(tween(130)) + slideOutHorizontally(tween(170)) { -it / 24 })
                    }
                },
                label = "screen",
            ) { current ->
                LaunchedEffect(current) {
                    if (current == "basic" || current == "pro") {
                        gridReturn = current
                        this@MainActivity.backTarget = current
                    }
                }
                when (current) {
                    "measure" -> MeasureScreen(onBack = { screen = gridReturn })

                    "spo2" -> Spo2Screen(onBack = { screen = gridReturn })

                    "ecg" -> EcgScreen(
                        // ⚠ 2026-10-20：这里写死过 "basic"，从「专业测量」进心电再返回就跳到了基础测量。
                        //   多行 lambda 不会被单行替换命中 —— 这类必须按 `screen = "basic"` 全量搜。
                        onBack = {
                            ecgCalibHint = false
                            screen = gridReturn
                        },
                        calibHint = ecgCalibHint,
                    )

                    // 体温点检：手表端本地测绝对腕部皮肤温度（三星健康只有睡眠期、且只给 ±0.x 偏差）
                    "temp" -> TempScreen(onBack = { screen = gridReturn })

                    // 体成分（BIA）：需要手机推来的用户资料，资料缺失时本页会明说去哪儿补
                    "bia" -> BiaScreen(
                        onBack = { screen = gridReturn },
                        onOpenMf = { screen = "mfbia" },
                    )

                    // 多频阻抗：与体成分同一个姿势（手指盖侧键），但采的是原始阻抗谱
                    "mfbia" -> MfBiaScreen(onBack = { screen = "bia" })

                    // 睡眠：数据由手机从三星健康读取后经蓝牙推送（手表没有三星健康 Data SDK）
                    "sleep" -> SleepScreen(
                        onBack = { screen = gridReturn },
                        onOpenStage = { screen = "sleep_stage" },
                    )

                    "sleep_stage" -> SleepStageScreen(onBack = { screen = "sleep" })

                    // 经期（模块 #13）：数据由手机录入/导入后经蓝牙推一行摘要（手表只读不写）
                    "cycle" -> CycleScreen(
                        summary = cycleSummary,
                        onBack = { screen = gridReturn },
                    )

                    // 压力/疲劳（模块 #12）：手机算好推过来，手表只显示结果，明细在手机端
                    "stress" -> StressScreen(
                        summary = stressSummary,
                        onBack = { screen = gridReturn },
                    )

                    // 氧减指数（模块 #11）：同样是手机算好推过来 ——
                    // 手表没有三星健康 Data SDK，读不到夜间分钟级血氧，本地没有原料可算。
                    "energy" -> EnergyScreen(
                        summary = energySummary,
                        onBack = { screen = gridReturn },
                    )
                    "apnea" -> ApneaScreen(
                        summary = apneaSummary,
                        onBack = { screen = gridReturn },
                    )
                    "odi" -> OdiScreen(
                        summary = odiSummary,
                        onBack = { screen = gridReturn },
                    )

                    // HRV 光测（2026-09-24）：PPG 逐拍间期，**不需要电极**。
                    "ibi" -> IbiScreen(
                        state = ibiState,
                        onStart = { ibiRecorder.startMeasurement() },
                        onFinish = { ibiRecorder.finish() },
                        onDiscard = { ibiRecorder.discard() },
                        onBack = {
                            ibiRecorder.discard()
                            screen = gridReturn
                        },
                    )

                    // 三星数据四页（活动/饮食/血糖/心律不齐）：纯展示，数据由手机推。
                    "extras_activity" -> ExtraActivityScreen(
                        summary = extrasSummary,
                        onBack = { screen = "more" },
                    )
                    "extras_diet" -> ExtraDietScreen(
                        summary = extrasSummary,
                        onBack = { screen = "more" },
                    )
                    "extras_glucose" -> ExtraGlucoseScreen(
                        summary = extrasSummary,
                        onBack = { screen = "more" },
                    )
                    "extras_ihrn" -> ExtraIhrnScreen(
                        summary = extrasSummary,
                        onBack = { screen = "more" },
                    )

                    // 传感器能力探测（排障）：查「这台表支持哪些采集器」
                    // + 实测 EDA 皮肤电导 / 逐拍间期 IBI / 心电与心率并跑。
                    "probe" -> ProbeScreen(
                        state = probeState,
                        onStart = { sensorProbe.start() },
                        onStop = { sensorProbe.stop() },
                        onBack = {
                            sensorProbe.stop()
                            screen = "more"
                        },
                    )

                    "offbody" -> OffBodyPage(
                        onConfirm = {
                            // 确定 = 放弃本次测量，回到待机（需要手动再点测量）
                            if (measurer.state.value.measuring) measurer.stop()
                            screen = "measure"
                        }
                    )

                    "add_bp" -> AddBpScreen(
                        onSave = { sys, dia, pulse, posture ->
                            scope.launch {
                                runCatching {
                                    db.bloodPressureDao().insert(
                                        BloodPressureEntity(
                                            systolic = sys, diastolic = dia, pulse = pulse,
                                            recordedAt = System.currentTimeMillis(),
                                            source = "watch_manual",
                                            // ⚠ 手表录的也必须记体位：它经蓝牙进手机时会把这一列一起带过去
                                            //   （`SyncRepository.insertItem` 的 BP 分支），
                                            //   配对样本那一侧才拿得到这个分组键。漏在这里 = 手表录的样本永远"未记录"。
                                            posture = posture,
                                        )
                                    )
                                }
                                bt.syncAuto()
                                refreshCounts()
                                // ⚠⚠ **保存后直接进心电测量页**（2026-10-02 用户定）：
                                //   血压预估算样本需要「袖套读数 + 同时段的腕表 PAT」，
                                //   而 PAT 只有测心电才拿得到。原来保存后回首页，
                                //   用户要再点一次「心电」——那一步没人会自己想到，
                                //   于是绝大多数读数永远配不上。
                                // ⚠ 跳转 ≠ 自动开测：进页后仍要点「开始」+ 手指按住电极，
                                //   符合「部分功能手动点击测量」的免费版纪律。
                                ecgCalibHint = true
                                screen = "ecg"
                            }
                        },
                        onCancel = { screen = gridReturn },
                    )

                    "add_ecg" -> AddEcgScreen(
                        currentBpm = hrState.bpm?.toInt(),
                        onSave = { cls, hr ->
                            scope.launch {
                                runCatching {
                                    db.ecgDao().insert(
                                        EcgRecordEntity(
                                            classification = cls, heartRate = hr,
                                            recordedAt = System.currentTimeMillis(),
                                            source = "watch_manual",
                                        )
                                    )
                                }
                                bt.syncAuto()
                                refreshCounts()
                                screen = "home"
                            }
                        },
                        onCancel = { screen = gridReturn },
                    )

                    // ── 「更多功能」页：首页放不下的入口都在这儿 ──
                    // 旧「更多功能」页已下线：入口合并进「测量 / 专业测量」
                    "more" -> FeatureGridScreen(
                        title = stringResource(R.string.s_45ea78e4),
                        listState = basicListState,
                        scrollState = basicScroll,
                        items = listOf(
                            stringResource(R.string.s_e041c8e1) to { screen = "measure" },
                            stringResource(R.string.s_ead30cb0) to { screen = "spo2" },
                            stringResource(R.string.s_00429ca8) to { screen = "temp" },
                            stringResource(R.string.s_8b4dce7d) to { screen = "ibi" },
                            stringResource(R.string.s_ca5d3c1d) to { screen = "stress" },
                            stringResource(R.string.s_6f2a56c8) to { screen = "energy" },
                            stringResource(R.string.s_939655e9) to { screen = "odi" },
                            stringResource(R.string.s_003837d9) to { screen = "extras_activity" },
                            stringResource(R.string.s_004f1c29) to { screen = "extras_diet" },
                            stringResource(R.string.s_00463596) to { screen = "extras_glucose" },
                            stringResource(R.string.s_8af3f95f) to { screen = "extras_ihrn" },
                            stringResource(R.string.s_03f85b96) to { screen = "apnea" },
                            stringResource(R.string.s_1e76d394) to { screen = "sleep" },
                        ),
                        onBack = { screen = "hub" },
                    )
                    // ── 首页（2026-09-19 照参考图 first.png 精简）──
                    // 只剩 4 件事：应用名 / 心电波形 / 连接状态 / 三个入口。
                    // 其余全部下沉到 MoreScreen —— 圆屏 240dp 装不下 13 个入口，
                    // 想让首页"一眼看得清"就得分层。
                    // ── 2026-10-18 新导航 ──
                    "cover" -> CoverScreen(
                        connected = btConnected,
                        onOpenHub = { screen = "hub" },
                    )

                    "hub" -> HubScreen(
                        onBasic = { screen = "basic" },
                        onPro = { screen = "pro" },
                        onSettings = { screen = "settings" },
                        onBackToCover = { screen = "cover" },
                    )

                    "basic" -> FeatureGridScreen(
                        title = stringResource(R.string.s_45ea78e4),
                        listState = basicListState,
                        scrollState = basicScroll,
                        items = listOf(
                            stringResource(R.string.s_e041c8e1) to { screen = "measure" },
                            stringResource(R.string.s_ead30cb0) to { screen = "spo2" },
                            stringResource(R.string.s_00429ca8) to { screen = "temp" },
                            stringResource(R.string.s_8b4dce7d) to { screen = "ibi" },
                            stringResource(R.string.s_ca5d3c1d) to { screen = "stress" },
                            stringResource(R.string.s_6f2a56c8) to { screen = "energy" },
                            stringResource(R.string.s_939655e9) to { screen = "odi" },
                            stringResource(R.string.s_003837d9) to { screen = "extras_activity" },
                            stringResource(R.string.s_004f1c29) to { screen = "extras_diet" },
                            stringResource(R.string.s_00463596) to { screen = "extras_glucose" },
                            stringResource(R.string.s_8af3f95f) to { screen = "extras_ihrn" },
                            stringResource(R.string.s_03f85b96) to { screen = "apnea" },
                            stringResource(R.string.s_1e76d394) to { screen = "sleep" },
                        ),
                        onBack = { screen = "hub" },
                    )

                    "pro" -> FeatureGridScreen(
                        title = stringResource(R.string.s_5b775380),
                        listState = proListState,
                        scrollState = proScroll,
                        items = listOf(
                            stringResource(R.string.s_14f80321) to { screen = "bia" },
                            stringResource(R.string.s_24fd46fa) to { openSamsungAntioxidant() },
                            stringResource(R.string.s_00414b0c) to { screen = "cycle" },
                            stringResource(R.string.s_98bb63ba) to { screen = "ecg" },
                            stringResource(R.string.s_2bd6bf20) to { screen = "add_bp" },
                        ),
                        onBack = { screen = "hub" },
                    )

                    "settings" -> SettingsScreen(
                        versionText = runCatching {
                            packageManager.getPackageInfo(packageName, 0).versionName ?: "-"
                        }.getOrDefault("-"),
                        onProbe = { screen = "probe" },
                        onLanguage = { showLanguage = true },
                        // 主题切换：目前只有「陶瓷白」，先做成可点（后续接主题列表）
                        settingsScroll = settingsScroll,
                        onTheme = { showTheme = true },
                        onBack = { screen = "hub" },
                    )

                    // ⚠ 任何"老的 home/more/未知页面"一律回落到 hub ——
                    //   否则功能页的 `onBack = { screen = gridReturn }` 会把用户送回**旧首页**
                    //   （2026-10-18 用户实测：点进功能再返回，界面变回旧布局）。
                    else -> HubScreen(
                        onBasic = { screen = "basic" },
                        onPro = { screen = "pro" },
                        onSettings = { screen = "settings" },
                        onBackToCover = { screen = "cover" },
                    )
                }
            }
            }   // ← CompositionLocalProvider 闭合（提供默认墨色）
            // 滚动条现在由各页面自带：Column 页面用 ScrollBarRight，ScalingLazy 页面用 SclPage 内置，
            // 根层不再画（否则会叠两条）。
            }

        }

        // 「上传波形」的结果弹窗（2026-10-05）。同样放在 Scaffold 之外。
        // ⚠ `bt.ecgExport` 为 null 时什么都不显示 ⇒ 不占位、不影响任何页面。
        btEcgExport?.let { r ->
            EcgExportDialog(text = resText(r), onDismiss = { bt.dismissEcgExport() })
        }

        // 语言选择弹窗（模块 #15）。放在 Scaffold 之外，避免被圆屏的定位/时间文本文案影响。
        // 主题选择弹窗（2026-10-20）。后续加主题背景：往 themes 里加一项即可。
        if (showTheme) {
            ThemePickerDialog(
                current = Skin.current.id,
                themes = ThemeDef.ALL,
                onPick = { t ->
                    showTheme = false
                    if (t.id != Skin.current.id) {
                        ThemeStore.set(this@MainActivity, t.id)
                        Skin.current = t
                        // 与语言切换同一套做法：重建 Activity 让整棵树用新配色
                        this@MainActivity.recreate()
                    }
                },
                onDismiss = { showTheme = false },
            )
        }

        if (showLanguage) {
            LanguagePickerDialog(
                current = LanguageStore.current(this@MainActivity),
                onPick = { lang ->
                    LanguageStore.set(this@MainActivity, lang)
                    showLanguage = false
                    // 就地重造 Activity 让整棵树用上新语言。
                    // ⚠ 只写偏好、**不再调 updateConfiguration**（API 24+ 会被重置）；
                    // 真正生效的是 `attachBaseContext` → `LanguageStore.wrap`。
                    this@MainActivity.recreate()
                },
                onDismiss = { showLanguage = false },
            )
        }
    }

    // ─────────────────────── HRV 光测（PPG 逐拍间期，2026-09-24）───────────────────────
    //
    // 它解决的是**心电那条路的可用性**：测心电要 30 秒 + 手指按住表冠 + 贴紧 + 保持不动，
    // 条件很挑；而 PPG 的逐拍间期**戴着表就能拿**。这里做成 2 分钟固定会话。
    //
    // ⚠ 三条必须显示出来的边界（少一条就会被误读）：
    //  ① **来源不同**：这是脉搏间期不是 R 峰间期，与心电那条路的 HRV **是两套基线**，
    //     分开存放（`Hrv.Source`）、界面也要分开说，不能放在一起比大小。
    //  ② **不给正常范围**：只说"相对你自己的基线"，不出"正常/不正常"的结论。
    //  ③ **状态码只是备查**：`IBI_STATUS_LIST` 的语义至今未定（-1/0 分布极不均匀，
    //     两种解释都说得通），所以不过滤、只在结果页把分布列出来 —— 留着以后定它。

    /** 会话中：`m:ss` 倒计时 + 已收间期数 + 进度。 */
    @Composable
    private fun IbiScreen(
        state: IbiRecorder.State,
        onStart: () -> Unit,
        onFinish: () -> Unit,
        onDiscard: () -> Unit,
        onBack: () -> Unit,
    ) {
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item { Text(stringResource(R.string.s_3eb3f1ef), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            val err = state.error
            val res = state.result
            when {
                state.measuring -> {
                    val totalSec = state.remainingMs / 1000
                    item {
                        Text(
                            "%d:%02d".format(totalSec / 60, totalSec % 60),
                            fontSize = 28.sp, fontWeight = FontWeight.Bold, color = HrvColor,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.s_3b3681de, state.beats),
                            fontSize = 11.sp, color = Skin.Muted,
                        )
                    }
                    item {
                        Text(
                            resText(state.statusText),
                            fontSize = 10.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.s_12df4d82),
                            fontSize = 9.sp, color = AccentOrange,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                    item {
                        PillButton(onClick = onFinish) {
                            Text(stringResource(R.string.s_9cba01de), fontSize = 10.sp)
                        }
                    }
                }

                err != null -> {
                    item {
                        Text(
                            stringResource(R.string.s_803bfb01),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                        )
                    }
                    item {
                        Text(
                            resText(err),
                            fontSize = 10.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                    item { PillButton(onClick = onStart) { Text(stringResource(R.string.s_004b27bc), fontSize = 11.sp) } }
                    item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
                }

                res == null -> {
                    item {
                        Text(
                            stringResource(R.string.s_571954f4),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HrvColor,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.s_0527f7e7) +
                                stringResource(R.string.s_cbf631ee, IbiRecorder.SESSION_MS / 60_000),
                            fontSize = 10.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.s_608fed64) +
                                stringResource(R.string.s_0693ef9d),
                            fontSize = 9.sp, color = AccentOrange,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.88f),
                        )
                    }
                    item {
                        PillButton(onClick = onStart) {
                            Text(stringResource(R.string.s_c99a112c, IbiRecorder.SESSION_MS / 60_000), fontSize = 11.sp)
                        }
                    }
                    item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
                }

                else -> {
                    if (res is Hrv.Result.Ok) {
                        item {
                            Text(
                                Hrv.summaryLine(res),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HrvColor,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.78f),
                            )
                        }
                        item {
                            InfoRow(stringResource(R.string.s_00293ec9), Hrv.tierShort(res.tier))
                        }
                        item { InfoRow(stringResource(R.string.s_b8d2d4c4), Hrv.restLabel(res.rest)) }
                        item { InfoRow(stringResource(R.string.s_e924e3d3), stringResource(R.string.s_274b5ffb, res.beats)) }
                        item {
                            InfoRow(
                                stringResource(R.string.s_00291277),
                                stringResource(R.string.s_9371bf0b, (res.artifactRatio * 100).toInt(), res.corrected, res.removed),
                            )
                        }
                        item {
                            Text(
                                res.freqNote,
                                fontSize = 9.sp, color = Skin.Muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.88f),
                            )
                        }
                        // 独立一致性校验：拿 SDK 在同一个包里**独立算出**的心率
                        // 跟"逐拍间期的中位心率"对一下 —— 这是 PPG 这条路上
                        // 唯一**不依赖伪迹模型**的校验（心电那条有 `ecgRegular`）。
                        val sdkHr = state.sdkHr
                        val dev = state.hrDeviationPct
                        if (sdkHr != null && dev != null) {
                            val over = dev > Hrv.REF_HR_MAX_DEVIATION * 100.0
                            item {
                                // ⚠ 模板里不许嵌 ASCII 引号（提取器会抓到半截串），先算好再插
                                val devPct = "%.1f".format(dev)
                                InfoRow(
                                    stringResource(R.string.s_9fc561cc),
                                    stringResource(R.string.s_3d9476a0, sdkHr) +
                                        stringResource(R.string.s_cf42df99, (60_000.0 / Hrv.median(res.rrMs)).roundToInt()) +
                                        stringResource(R.string.s_ff4b6632, devPct),
                                    valueColor = if (over) AccentOrange else Skin.Ink,
                                )
                            }
                            if (over) {
                                item {
                                    Text(
                                        stringResource(R.string.s_f1d1f137) +
                                            stringResource(R.string.s_46e42bf7),
                                        fontSize = 8.5.sp, color = AccentOrange,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth(0.78f),
                                    )
                                }
                            }
                        }
                        item {
                            Text(
                                stringResource(R.string.s_9d834e69) +
                                    stringResource(R.string.s_da4dce96),
                                fontSize = 8.5.sp, color = AccentOrange,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.78f),
                            )
                        }
                    } else if (res is Hrv.Result.NotApplicable) {
                        item {
                            Text(
                                stringResource(R.string.s_f92b7afa),
                                fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                            )
                        }
                        item {
                            Text(
                                Hrv.shortReason(res.reason),
                                fontSize = 10.sp, color = Skin.Muted,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.86f),
                            )
                        }
                    }
                    // 状态码分布：不过滤、只留证据（语义还没定，见 IbiRecorder 类注释）。
                    if (state.statusHistogram.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.s_a2152004) + state.statusHistogram.entries
                                    .joinToString(" · ") { "${it.key}×${it.value}" },
                                fontSize = 8.5.sp, color = Color(0xFF8A8F98),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.78f),
                            )
                        }
                    }
                    item { PillButton(onClick = onDiscard) { Text(stringResource(R.string.s_002f3ab4), fontSize = 11.sp) } }
                    item {
                        PillButton(onClick = onStart) {
                            Text(stringResource(R.string.s_0a399723), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    // ───────────────────── 三星数据简页（2026-09-24，模块：扩展指标）─────────────────────
    //
    // 四页的共同纪律（与手机端同名页一致，只是手表放不下明细）：
    //
    // · **null 表示"没有"，绝不显示 0** —— 0 步会被读成"今天一步没走"，
    //   0 千卡会被读成"今天没吃东西"。这是本项目反复强调的那类静默误导。
    // · **不是今天的必须把日期写出来**（冷启动时看到的是缓存）。见 `todayLabel`。
    // · **读失败与"本来没有"分开说**：每块一个 note，"未授权 / 读取出错 / 三星里没有"
    //   是三件要做的事完全不同的事。
    // · 全部只读不采，与免费版纪律一致（依赖三星健康搬运、不做后台采集）。

    /** 简页里的一行「标签 —— 值」。四个三星数据页共用。 */
    @Composable
    private fun InfoRow(label: String, value: String, valueColor: Color = Skin.Ink) {
        Row(
            modifier = Modifier.fillMaxWidth(0.86f),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 10.sp, color = Skin.Muted)
            Text(value, fontSize = 12.sp, color = valueColor)
        }
    }

    /**
     * 还没收到摘要时的统一提示 + 「从手机获取」。
     *
     * ⚠ 它**不能自己调用 `item {}`** —— `item` 是 `LazyListScope` 的成员，
     * 只在 `ScalingLazyColumn { }` 的 lambda 里可见。所以这里只画内容，
     * 由调用处包一层 `item { }`。
     * （第一版就是把它写成普通 `@Composable` 却调了 `item`，编译报
     * `Unresolved reference 'item'` 加一串 "can only happen from the context of a
     * @Composable function" —— 后者完全指向别处，是个误导性很强的报错。）
     *
     * 另外：`"还没有收到$what摘要"` 里 `$what摘要` 会被当成**一个标识符**，
     * 必须写 `${what}摘要`。这也踩了一次。
     */
    @Composable
    private fun ExtraWaiting(what: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.s_d49e35d1, what) +
                    stringResource(R.string.s_a03a4c67),
                fontSize = 10.sp, color = Skin.Muted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.85f),
            )
            PillButton(
                onClick = { bt.refreshFromPhone() },
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
            }
        }
    }

    /**
     * 每块的读取说明（空串 = 读成功）。
     *
     * 同样**不自己调 `item {}`**；调用处先判空再包 `item { }` ——
     * 判空放在调用处，是为了避免在列表里多出一个空 item 的间距。
     */
    @Composable
    private fun ExtraNote(label: String, note: String) {
        Text(
            "$label：$note",
            fontSize = 9.sp, color = AccentOrange,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.86f),
        )
    }

    /** 目标进度一行。**没设过目标就明说没设**，不替用户编一个"应该走一万步"。 */
    private fun goalLine(current: Double?, goal: Double?, unit: String): String? {
        if (goal == null || goal <= 0.0) return null
        val c = current ?: return this@MainActivity.getString(R.string.s_cd75f505, goal.toInt(), unit)
        val pct = (c * 100.0 / goal).toInt()
        return this@MainActivity.getString(R.string.s_23af2d9a, goal.toInt(), unit, pct)
    }

    /**
     * 「活动」简页。
     *
     * ⚠ 与手机端「活动」页**同一个日界、同一次读取**（手机在 `pushExtrasSummary` 里算好，
     * 那里的日界走 `SamsungHealthReader.todayStartMs`）。两端各算一次必然漂移。
     */
    @Composable
    private fun ExtraActivityScreen(summary: ExtrasSummaryMessage?, onBack: () -> Unit) {
        val today = remember { java.time.LocalDate.now().toEpochDay() }
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item { Text(stringResource(R.string.s_003837d9), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            if (summary == null) {
                item { ExtraWaiting(stringResource(R.string.s_003837d9)) }
            } else {
                val steps = summary.steps
                item { Text(summary.todayLabel(today) + stringResource(R.string.s_00375a1f), fontSize = 10.sp, color = Skin.Muted) }
                item {
                    Text(
                        Extras.fmtSteps(steps),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784),
                    )
                }
                item {
                    Text(
                        goalLine(steps?.toDouble(), summary.stepGoal?.toDouble(), stringResource(R.string.s_00006b65))
                            ?: stringResource(R.string.s_08eb28c5),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                }
                item { InfoRow(stringResource(R.string.s_b8d04e50), Extras.fmtKcal(summary.activeKcal)) }
                item { InfoRow(stringResource(R.string.s_b8cc8402), Extras.fmtDurationOrNull(summary.activeMs)) }
                item { InfoRow(stringResource(R.string.s_004911d2), Extras.fmtDistance(summary.distanceM)) }
                item { InfoRow(stringResource(R.string.s_003ad600), Extras.fmtFloors(summary.floors)) }
                val exName = summary.lastExerciseName
                if (exName != null) {
                    item {
                        InfoRow(
                            stringResource(R.string.s_f32c0871),
                            exName + " · " + Extras.fmtDurationOrNull(summary.lastExerciseMs),
                        )
                    }
                } else {
                    item { InfoRow(stringResource(R.string.s_f32c0871), stringResource(R.string.s_6535eafa)) }
                }
                if (summary.activityNote.isNotEmpty()) {
                    item { ExtraNote(stringResource(R.string.s_b8dfb6e8), summary.activityNote) }
                }
                if (summary.goalNote.isNotEmpty()) {
                    item { ExtraNote(stringResource(R.string.s_0b2f0aa0), summary.goalNote) }
                }
                item {
                    Text(
                        stringResource(R.string.s_1b33583b),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.86f),
                    )
                }
            }
            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /**
     * 「饮食」简页。
     *
     * ⚠ 摄入热量与饮水**只能你自己在三星健康里记** —— 这两个量没有任何传感器能测出来。
     * 这句话必须写在页面上：不写的话"没有数据"会被读成"我们没读到"。
     */
    @Composable
    private fun ExtraDietScreen(summary: ExtrasSummaryMessage?, onBack: () -> Unit) {
        val today = remember { java.time.LocalDate.now().toEpochDay() }
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item { Text(stringResource(R.string.s_004f1c29), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            if (summary == null) {
                item { ExtraWaiting(stringResource(R.string.s_004f1c29)) }
            } else {
                val kcal = summary.kcalToday
                val water = summary.waterMlToday
                item { Text(summary.todayLabel(today) + stringResource(R.string.s_0033a031), fontSize = 10.sp, color = Skin.Muted) }
                item {
                    Text(
                        if (kcal == null) "—" else Extras.fmtKcal(kcal),
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFBCAAA4),
                    )
                }
                item {
                    Text(
                        goalLine(kcal?.toDouble(), summary.kcalGoal?.toDouble(), stringResource(R.string.s_002aeeaa))
                            ?: stringResource(R.string.s_d246d674),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                }
                item { InfoRow(summary.todayLabel(today) + stringResource(R.string.s_004eef7e), Extras.fmtVolume(water)) }
                val wg = summary.waterGoalMl
                item {
                    Text(
                        goalLine(water?.toDouble(), wg?.toDouble(), stringResource(R.string.s_00378c88)) ?: stringResource(R.string.s_35d7461c),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                }
                if (summary.dietNote.isNotEmpty()) {
                    item { ExtraNote(stringResource(R.string.s_b76eadb8), summary.dietNote) }
                }
                item {
                    Text(
                        DietNotes.HOW,
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.86f),
                    )
                }
            }
            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /**
     * 「血糖」简页。
     *
     * ⚠ 单位**必须**走 `Extras.fmtGlucose`（mmol/L 红线）—— 手写数字会丢掉单位，
     * 而 mmol/L 与 mg/dL 差约 18 倍、两个数看起来都"正常"。
     */
    @Composable
    private fun ExtraGlucoseScreen(summary: ExtrasSummaryMessage?, onBack: () -> Unit) {
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item { Text(stringResource(R.string.s_00463596), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            if (summary == null) {
                item { ExtraWaiting(stringResource(R.string.s_00463596)) }
            } else {
                val g = summary.glucoseMmol
                if (g == null) {
                    item {
                        Text(
                            stringResource(R.string.s_0323f5d7),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                        )
                    }
                    item {
                        Text(
                            stringResource(R.string.s_bdba0696) +
                                stringResource(R.string.s_4377bcf6),
                            fontSize = 10.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                } else {
                    item { Text(stringResource(R.string.s_f30a737a), fontSize = 10.sp, color = Skin.Muted) }
                    item {
                        Text(
                            Extras.fmtGlucose(g),
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB33563),
                        )
                    }
                    if (summary.glucoseMeal.isNotEmpty()) {
                        item { InfoRow(stringResource(R.string.s_0031dd52), summary.glucoseMeal) }
                    }
                    item { InfoRow(stringResource(R.string.s_24c5103d), btFmt(summary.glucoseAtMs)) }
                }
                if (summary.glucoseNote.isNotEmpty()) {
                    item { ExtraNote(stringResource(R.string.s_62c6198d), summary.glucoseNote) }
                }
                item {
                    Text(
                        stringResource(R.string.s_6090320e),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.86f),
                    )
                }
            }
            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /**
     * 「心律不齐」简页。
     *
     * ⚠⚠ 措辞全部走 core 的 [Ihrn]：它只有 `DETECTED` / `UNDEFINED` 两个取值，
     * **没有 `NOT_DETECTED`** —— 所以「无记录」**不等于**「心脏没问题」，
     * 而"没有记录"恰恰是最容易被读成"安全"的状态。这条边界**在界面上不折叠**。
     */
    @Composable
    private fun ExtraIhrnScreen(summary: ExtrasSummaryMessage?, onBack: () -> Unit) {
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            item { Text(stringResource(R.string.s_ee1aeeeb), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            if (summary == null) {
                item { ExtraWaiting(stringResource(R.string.s_ee1aeeeb)) }
            } else {
                val raw = summary.ihrnStatus
                val hasRow = summary.ihrnAtMs > 0L
                val alarming = Ihrn.isAlarming(raw)
                if (hasRow) {
                    item {
                        Text(
                            Ihrn.label(raw),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            color = if (alarming) Color(0xFFB04A46) else Skin.Ink,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                    item { InfoRow(stringResource(R.string.s_24c5103d), btFmt(summary.ihrnAtMs)) }
                    item {
                        Text(
                            Ihrn.ADVICE,
                            fontSize = 9.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.86f),
                        )
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.s_a9930731),
                            fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                        )
                    }
                    // ⚠ 这一句**不折叠**：它是本页最容易被误读的地方。
                    item {
                        Text(
                            Ihrn.EMPTY_IS_NOT_NEGATIVE,
                            fontSize = 10.sp, color = Color(0xFFB04A46),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.88f),
                        )
                    }
                    item {
                        Text(
                            if (summary.ihrnReadable) {
                                stringResource(R.string.s_6a5b1aee)
                            } else {
                                stringResource(R.string.s_12243560) +
                                    stringResource(R.string.s_10b892ce)
                            },
                            fontSize = 9.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.88f),
                        )
                    }
                }
                if (summary.ihrnNote.isNotEmpty()) {
                    item { ExtraNote(stringResource(R.string.s_0047f547), summary.ihrnNote) }
                }
            }
            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /** 简页里的时间行：`MM-dd HH:mm`；无值时给占位符而不是 1970 年。 */
    private fun btFmt(atMs: Long): String =
        if (atMs <= 0L) "—" else timeFmt.format(java.util.Date(atMs))

    /**
     * 传感器能力探测页（排障）。
     *
     * 界面刻意做成**纯日志**：这一页要回答的是"这台设备到底支持什么、实测拿到几个值"，
     * 任何"友好化"措辞都会把有用的信息磨掉。所以错误原文照登、`[有]`/`[无]` 照登，
     * 用户截图发回来我就能直接判断，不必再问型号。
     *
     * 圆屏上字号压到 9sp 是不得已 —— 一行要放得下 `[无] SKIN_TEMPERATURE_CONTINUOUS`
     * 这种长名字。宁可字小也要一行一条，折行会让日志读不成表。
     */
    @Composable
    private fun ProbeScreen(
        state: SensorProbe.State,
        onStart: () -> Unit,
        onStop: () -> Unit,
        onBack: () -> Unit,
    ) {
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            item { Text(stringResource(R.string.s_656182c8), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            if (state.log.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.s_594035a4) +
                            stringResource(R.string.s_1f8da0e4) +
                            stringResource(R.string.s_f880975a),
                        fontSize = 10.sp,
                        color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.86f),
                    )
                }
            }

            items(state.log.size) { i ->
                val entry = state.log[i]
                Text(
                    entry.text,
                    fontSize = 9.sp,
                    color = when {
                        // ⚠ 用结构化级别，**不再** `line.contains("→ 结论")` / `startsWith("✗")`
                        // —— 那是拿文案当判据，翻译后静默失效（模块 #15）。
                        entry.error -> Color(0xFFB04A46)
                        entry.conclusion -> AccentOrange
                        else -> Skin.Ink
                    },
                    modifier = Modifier
                        .fillMaxWidth(0.94f)
                        .padding(horizontal = 4.dp),
                )
            }

            if (state.running) {
                item {
                    PillButton(onClick = onStop) { Text(stringResource(R.string.s_00298a76), fontSize = 11.sp) }
                }
            } else {
                item {
                    PillButton(onClick = onStart) {
                        Text(if (state.done) stringResource(R.string.s_a440fc80) else stringResource(R.string.s_d2833754), fontSize = 11.sp)
                    }
                }
                item {
                    PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) }
                }
            }
        }
    }

    /** 每小时聚合（复刻三星健康"最小/最大心率每小时"）。 */
    private data class HourStat(val hour: Int, val min: Int, val max: Int, val avg: Int)
    private data class ChartBar(val lo: Int, val hi: Int, val current: Boolean)

    /**
     * 心率页那一行活动状态文案。
     *
     * 刻意把"没采到"与"静止"分开说：把采集中断显示成"静息"，会让人拿一份没有依据的
     * 结论去解释心率（这正是心电模块踩过的坑 —— 派生展示值与真实门控必须同源）。
     */
    // ⚠ 加 `@Composable` 是为了能对 `s.error`（待求值文案）调 `resText`。
    //   这个函数**每次重组都会重算**，所以它自己的文案本来就是新鲜的；
    //   要修的只是它**吃进来的**那个 `error` 字段别再是已求值的串。
    @Composable
    private fun activityLine(s: AccelSampler.State): String = when {
        !s.supported -> stringResource(R.string.s_0e711041)
        s.sampling -> stringResource(R.string.s_c74a27bf, Activity.label(s.level), s.elapsedMs / 1000)
        s.lastStdG != null -> stringResource(R.string.s_0f0cce7f, Activity.label(s.level), Activity.formatStd(s.lastStdG))
        s.error != null -> stringResource(R.string.s_c268146a, resText(s.error))
        else -> stringResource(R.string.s_cb18a659)
    }

    /**
     * 血氧页那一行"原始光信号"文案（模块 #8）。
     *
     * 与 `activityLine` 同一态度：**"没采到"绝不能显示成"正常"**。
     * 这一项本来对用户不可见，如果采集失败却只显示一个含糊的"已就绪"，
     * 那就成了一个永远查不出来的静默故障 —— 而它的失败原因（能力不支持 / SDK 策略 /
     * 与服务连接冲突）恰恰都是需要用户或开发者动手才能解决的。
     */
    @Composable
    private fun ppgStatusLine(s: PpgRecorder.State): String = when {
        !s.enabled -> stringResource(R.string.s_3df7ca5a)
        !s.supported -> stringResource(R.string.s_3a03c309, parenSuffix(s.error))
        s.recording && s.full -> stringResource(R.string.s_d84347dd)
        s.recording -> stringResource(R.string.s_05be7fb1, s.sampleCount, s.elapsedMs / 1000)
        s.error != null -> stringResource(R.string.s_4708d026, resText(s.error))
        // 取到局部 val 而不是直接 `s.lastStats!!`：它是 data class 的 val，智能转换本来就能用，
        // 多写一个 `!!` 只是噪音（编译器也会提醒 "Unnecessary non-null assertion"）。
        s.lastStats != null -> {
            val st = s.lastStats
            stringResource(R.string.s_5f06409e, Ppg.formatDuration(st.durationMs), st.sampleCount)
        }
        else -> stringResource(R.string.s_e35181e9)
    }

    /**
     * 心率页（复刻三星健康原生风格）：
     * 顶部小橙心 + 最后测量时间 + 大数字(次/分) + 药丸测量按钮，向下滑动：
     * 最小/最大心率每小时（柱状图）｜静息心率范围｜今日平均值｜提示
     */
    @Composable
    private fun MeasureScreen(onBack: () -> Unit) {
        val hrState by measurer.state.collectAsState()
        val sleepAvg by bt.nativeSleepAvg.collectAsState()
        // 活动采集状态（模块 #7）：心率的**上下文** —— "心率偏高"最常见的原因就是刚才在动，
        // 把活动状态和心率放在同一页，用户才不用自己猜。
        val accelState by accelSampler.state.collectAsState()
        val scope = rememberCoroutineScope()

        var stats by remember { mutableStateOf<List<HourStat>>(emptyList()) }
        var lastRec by remember { mutableStateOf<HeartRateEntity?>(null) }
        var recentList by remember { mutableStateOf(listOf<HeartRateEntity>()) }
        var nowHour by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) }

        // 手机推送的三星健康当日小时统计（原生全天数据，非空时图表优先使用）
        val nativeHourStats by bt.nativeHourStats.collectAsState()
        val chartStats: List<HourStat> = if (nativeHourStats.isNotEmpty())
            nativeHourStats.map { HourStat(it.hour, it.min, it.max, it.avg) } else stats

        LaunchedEffect(Unit) {
            while (true) {
                val cal = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }
                val list = runCatching {
                    db.heartRateDao().since(cal.timeInMillis)
                }.getOrDefault(emptyList())
                lastRec = runCatching { db.heartRateDao().latest() }.getOrNull()
                recentList = runCatching { db.heartRateDao().recentOnce(5) }.getOrDefault(emptyList())
                nowHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                stats = list.groupBy {
                    val c = Calendar.getInstance(); c.timeInMillis = it.measuredAt
                    c.get(Calendar.HOUR_OF_DAY)
                }.map { (h, items) ->
                    val v = items.map { it.bpm.toInt() }
                    HourStat(h, v.min(), v.max(), v.average().toInt())
                }.sortedBy { it.hour }
                delay(30_000)
            }
        }

        // 单次测量会话：进度 / 结果 / 会话样本
        var sessionProgress by remember { mutableStateOf(0f) }
        var sessionResult by remember { mutableStateOf<Int?>(null) }
        var sessionNoData by remember { mutableStateOf(false) }
        val sessionSamples = remember { mutableStateListOf<Pair<Long, Int>>() }

        LaunchedEffect(Unit) {
            measurer.samples.collect { bpm ->
                sessionSamples.add(System.currentTimeMillis() to bpm.toInt())
                if (sessionSamples.size > 240) sessionSamples.removeAt(0)
            }
        }
        // 测量会话：满 5 秒且已有 3 个读数即完成；最长 15 秒（晃动/佩戴松自动延长）
        LaunchedEffect(hrState.measuring) {
            if (hrState.measuring) {
                sessionProgress = 0f
                val start = System.currentTimeMillis()
                while (measurer.state.value.measuring) {
                    val elapsed = System.currentTimeMillis() - start
                    sessionProgress = (elapsed / 150f).coerceIn(0f, 100f)
                    if ((elapsed >= 5_000 && sessionSamples.size >= 3) || elapsed >= 15_000) break
                    delay(80)
                }
                if (measurer.state.value.measuring) {
                    val avg = completeHeartSession(sessionSamples.toList())
                    // ── 测量结束的震动提示（2026-10-09 补）──
                    //
                    // ⚠⚠ 这一处**漏了很久**：本项目 2026-10-07 给所有"要用户等一段"的会话加了
                    //   震动，当时把心率归到「**连续实时流，根本没有"结束"这回事（只能手动停）**」
                    //   那一类、**刻意不震**。
                    //
                    //   但**那个理由在本页早就不成立了**：本页现在是「**单次测量模式**」
                    //   （见 `completeHeartSession` 的说明与上面的循环条件）——
                    //   满 5 秒且已有 3 个读数就完成、最长 15 秒 ⇒ **有明确的自然结束**，
                    //   与体温 / 血氧是同一类会话。用户报「心率测量完之后没有震动」正是它。
                    //
                    // ⚠ 落点放在**这里**、而不是 `measurer.stop()` 里：`stop()` 同时服务
                    //   自然结束 / 手动停止 / `stopAllMeasurements()` 三种语义，
                    //   放进去会让"开始测别的把心率停掉"也震一下（误震）。
                    //   ⚠ 外面这层 `if (measurer.state.value.measuring)` 恰好就是"自然结束"的判据：
                    //     手动按「停止」时 `measuring` 已经是 false，不会走到这里。
                    // ⚠ 成功一短震 / 失败两短震，与其它会话同一口径
                    //   （只震一次的话，"震了"对用户就等于"测好了"，失败时反而更糟）。
                    if (avg != null) {
                        sessionResult = avg
                        Buzzer.ok()
                    } else {
                        sessionNoData = true
                        Buzzer.fail()
                    }
                }
            }
        }

        val shownBpm = sessionResult ?: lastRec?.bpm?.toInt()
        val lastTimeStr = lastRec?.let { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.measuredAt)) } ?: "--:--"

        // ⚠ 2026-10-18 恢复：心率页的爱心按节拍缩放（用户要求"以前有动画，改回来"）。
        //   只有这一页的一个小图元在动，不影响首页与老表常驻开销。
        val hrInfinite = rememberInfiniteTransition(label = "heart")
        val pulse by hrInfinite.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(850, easing = LinearEasing)),
            label = "pulse",
        )

        val measuring = hrState.measuring

        val scroll = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
        ScrollBarRight(scroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(6.dp))

            // 小橙心（随测量轻微搏动）
            Canvas(modifier = Modifier.size(26.dp)) {
                val c = center
                val s = 1f + (beatScale(pulse) - 1f) * 0.7f
                withTransform({ scale(s, s, pivot = c) }) {
                    val w = size.width; val h = size.height
                    val heart = Path().apply {
                        moveTo(w * 0.50f, h * 0.86f)
                        cubicTo(w * 1.06f, h * 0.46f, w * 0.85f, h * 0.04f, w * 0.50f, h * 0.30f)
                        cubicTo(w * 0.15f, h * 0.04f, w * -0.06f, h * 0.46f, w * 0.50f, h * 0.86f)
                    }
                    drawPath(heart, AccentOrange)
                }
            }
            Spacer(Modifier.height(2.dp))

            if (!measuring && sessionResult != null) {
                // ── 结果页：结果对比静息心率范围（按钮：返回） ──
                val result = sessionResult!!
                val minima = chartStats.map { it.min }
                val restLow = if (minima.isNotEmpty()) minima.min() else 61
                val restHigh = if (minima.isNotEmpty()) maxOf(minima.min(), minima.max()) else 76
                val cmpText = when {
                    result > restHigh -> stringResource(R.string.s_41249899)
                    result < restLow -> stringResource(R.string.s_8ab3687f)
                    else -> stringResource(R.string.s_e9bb677a)
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$result", fontSize = 42.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.s_1c0dd13c), fontSize = 18.sp, color = AccentOrange, modifier = Modifier.padding(bottom = 5.dp))
                }
                Spacer(Modifier.height(12.dp))
                Text(cmpText, fontSize = 15.sp, color = Skin.Ink)
                Text(stringResource(R.string.s_fb5000cc, restLow, restHigh), fontSize = 15.sp, color = AccentOrange)
                Spacer(Modifier.height(22.dp))
                Button(
                    // ⚠⚠ **结果页按钮：文案「返回」→「确定」，作用改成"退出回首页"**（2026-10-08）。
                    //
                    // 改之前这里是 `{ sessionResult = null }` + 文案「返回」——
                    // **文案写着"返回"、实际只清结果**（点了还在这一页），
                    // 而页脚那个真「返回」也在 ⇒ 同一屏两个"返回"、一个能走一个不能走，
                    // 最容易被读成"这两个按钮作用一样"（用户这次报的就是这个体感）。
                    // ⇒ 统一成语义自洽的「确定 = 我看完了，退出」，与体温 / 血氧 / 体成分 / 心电一致。
                    onClick = { sessionResult = null; onBack() },
                    colors = ButtonDefaults.buttonColors(
                backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
            ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) {
                    Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp)
                }
            } else if (measuring) {
                // 测量中：进度 + 真实值驱动波形
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${sessionProgress.toInt()}",
                        fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                    )
                    Text("%", fontSize = 16.sp, color = Skin.Ink, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(2.dp))
                SampleWaveform(samples = sessionSamples.toList(), phase = pulse)
                Spacer(Modifier.height(8.dp))
                if (sessionSamples.isEmpty()) {
                    Text(stringResource(R.string.s_c8548a3c), fontSize = 13.sp, color = Skin.Muted)
                } else {
                    Text(stringResource(R.string.s_746d954a), fontSize = 11.sp, color = Skin.Muted)
                }
                // 活动状态（模块 #7）：手腕在动时心率一定会被带高。
                // 与其让用户事后怀疑"是不是心动过速"，不如当场把"你正在动"标出来。
                Text(activityLine(accelState), fontSize = 10.sp, color = Skin.Muted)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        measurer.stop()
                        if (sessionSamples.isNotEmpty()) {
                            sessionResult = sessionSamples.map { it.second }.average().toInt()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) {
                    Text(stringResource(R.string.s_00298a76), fontSize = 15.sp)
                }
            } else if (sessionNoData) {
                // 本次测量未取到任何读数：明确显示失败，不回退显示旧数据
                Text(stringResource(R.string.s_cb7439e8), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.s_791da583),
                    fontSize = 12.sp,
                    color = Skin.Muted,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        sessionNoData = false
                        sessionSamples.clear()
                        stopAllMeasurements()
                        measurer.start()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) {
                    Text(stringResource(R.string.s_a446127f), fontSize = 15.sp)
                }
            } else {
                // 待机：最后测量（无波形）
                Text(lastTimeStr, fontSize = 15.sp, color = AccentOrange)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        shownBpm?.toString() ?: "--",
                        fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.s_1c0dd13c), fontSize = 20.sp, color = Skin.Ink, modifier = Modifier.padding(bottom = 6.dp))
                }
                if (sessionResult != null) {
                    Text(stringResource(R.string.s_cb754364), fontSize = 11.sp, color = AccentOrange)
                }
                Text(activityLine(accelState), fontSize = 10.sp, color = Skin.Muted)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        sessionResult = null
                        sessionNoData = false
                        sessionSamples.clear()
                        stopAllMeasurements()
                        measurer.start()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) {
                    Text(stringResource(R.string.s_00387f30), fontSize = 15.sp)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ─── 最小/最大心率每小时 ───
            SectionStart {
                Text(stringResource(R.string.s_acb4c553), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
            }
            Text(
                if (nativeHourStats.isNotEmpty()) stringResource(R.string.s_69c5c5a1) else stringResource(R.string.s_645a4de8),
                fontSize = 9.sp, color = Color.DarkGray,
                modifier = Modifier.fillMaxWidth(),
            )
            if (chartStats.isEmpty()) {
                Text("--", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                    modifier = Modifier.fillMaxWidth())
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${chartStats.minOf { it.min }}-${chartStats.maxOf { it.max }}",
                        fontSize = 26.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.s_1c0dd13c), fontSize = 15.sp, color = AccentOrange, modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            val minMaxBars = (0..23).map { h ->
                chartStats.find { it.hour == h }?.let { ChartBar(it.min, it.max, current = h == nowHour) }
            }
            val scaleMax = ((chartStats.maxOfOrNull { it.max } ?: 110).coerceAtLeast(110) + 9) / 10 * 10
            HrBarChart(bars = minMaxBars, lo = 30, hi = scaleMax)
            Text(
                "$scaleMax",
                fontSize = 9.sp, color = Skin.Muted,
                modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
            XLabels(listOf("0", "6", "12", "18", stringResource(R.string.s_003ea673)))
            SectionLineBox()

            // ─── 静息心率范围 ───
            Spacer(Modifier.height(10.dp))
            if (chartStats.isNotEmpty()) {
                val minima = chartStats.map { it.min }
                Text(stringResource(R.string.s_2dc0e567), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        "${minima.min()}-${maxOf(minima.min(), minima.max())}",
                        fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.s_1c0dd13c), fontSize = 13.sp, color = AccentOrange, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            SectionLineBox()

            // ─── 睡眠（昨晚平均心率，数据来自三星健康） ───
            if (sleepAvg != null) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.s_003d8dc3), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                    modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.s_18e8649c), fontSize = 15.sp, color = AccentOrange)
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "$sleepAvg",
                        fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(stringResource(R.string.s_1c0dd13c), fontSize = 15.sp, color = AccentOrange, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(
                    stringResource(R.string.s_4a75448b),
                    fontSize = 10.sp, color = Color.DarkGray,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLineBox()
            }

            // ─── 今日平均值 + 柱状图 ───
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.s_0028b743), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stringResource(R.string.s_18e8649c), fontSize = 15.sp, color = AccentOrange)
                Spacer(Modifier.size(4.dp))
                Text(
                    chartStats.ifEmpty { null }?.let { s -> s.map { it.avg }.average().toInt() }?.toString() ?: "--",
                    fontSize = 30.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                )
                Spacer(Modifier.size(4.dp))
                Text(stringResource(R.string.s_1c0dd13c), fontSize = 15.sp, color = AccentOrange, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(6.dp))
            val avgBars = (0..23).map { h ->
                chartStats.find { it.hour == h }?.let { ChartBar(0, it.avg, current = h == nowHour) }
            }
            HrBarChart(bars = avgBars, lo = 0, hi = 100)
            XLabels(listOf("00", "06", "12", "18", "24"))
            SectionLineBox()

            // ─── 最近测量（本应用单次测量记录） ───
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.s_f31a9d89), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                modifier = Modifier.fillMaxWidth())
            if (recentList.isEmpty()) {
                Text(
                    stringResource(R.string.s_d74c39fb),
                    fontSize = 12.sp, color = Color.DarkGray,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                recentList.forEach { r ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(r.measuredAt)),
                            fontSize = 12.sp, color = Skin.Muted,
                        )
                        Text(
                            "${r.bpm.toInt()} bpm",
                            fontSize = 12.sp, color = Skin.Ink,
                        )
                    }
                }
            }
            SectionLineBox()

            // ─── 提示 ───
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.s_00338caa), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.s_c405f7f5),
                fontSize = 13.sp, color = Skin.Ink,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(22.dp))
            // ⚠⚠ 结果页**不画**这个页脚「返回」（2026-10-08）：那一屏已经有「确定」（= 退出回首页）。
            //   其余状态（测量中 / 待机 / 失败）照旧保留 —— 它们的按钮是"停止 / 测量 / 重新测量"，
            //   与返回不重复，而且去掉就出不去了。
            if (sessionResult == null) {
                Button(
                    onClick = {
                        if (measurer.state.value.measuring) measurer.stop()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) {
                    Text(stringResource(R.string.s_0049f05a), fontSize = 15.sp)
                }
            }
                Spacer(Modifier.height(10.dp))
            }
            // 右缘滚动位置指示（原生样式细条）
        }
    }

    /** 脉搏波形函数：主搏 + 重搏切迹，模拟桡动脉容积波形。 */
    private fun pulseShape(p: Float): Float = when {
        p < 0.10f -> p / 0.10f
        p < 0.22f -> 1f - (p - 0.10f) / 0.12f * 0.55f
        p < 0.32f -> 0.45f + (p - 0.22f) / 0.10f * 0.20f
        p < 0.48f -> 0.65f - (p - 0.32f) / 0.16f * 0.65f
        p < 0.70f -> (p - 0.48f) / 0.22f * 0.06f
        else -> 0.06f * (1f - (p - 0.70f) / 0.30f)
    }

    /** 真实采样驱动的滚动波形：每个读数一个脉冲，高度随数值高低，随时间向左滚动。 */
    @Composable
    private fun SampleWaveform(samples: List<Pair<Long, Int>>, phase: Float) {
        Canvas(modifier = Modifier.fillMaxWidth().height(74.dp)) {
            // 读取 phase 触发每帧重绘，并做亚帧平滑
            val now = System.currentTimeMillis() - (phase * 16.6f).toLong()
            val w = size.width; val h = size.height
            val base = h * 0.72f
            val windowMs = 8000f
            val brush = Brush.horizontalGradient(listOf(Color(0xFFFF4D6B), Color(0xFFFFB35C)))
            val path = Path()
            path.moveTo(0f, base)
            var prevX = 0f
            for ((t, bpm) in samples.sortedBy { it.first }) {
                val age = now - t
                if (age < 0f || age > windowMs) continue
                val x = w * (1f - age / windowMs)
                val norm = ((bpm - 50f) / 100f).coerceIn(0.12f, 1f)
                val peak = base - h * 0.80f * norm
                val sw = w * 0.035f
                val x0 = (x - sw).coerceAtLeast(prevX)
                if (x0 > prevX) path.lineTo(x0, base)
                path.quadraticBezierTo(x, peak, x + sw, base)
                prevX = x + sw
                if (prevX >= w) break
            }
            if (prevX < w) path.lineTo(w, base)
            drawPath(path, brush, style = Stroke(width = 4f, cap = StrokeCap.Round))
            drawLine(SectionLine, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
        }
    }

    /** 结束一次测量会话：停止传感器，平均值入库并同步，返回结果（无样本返回 null）。 */
    private suspend fun completeHeartSession(samples: List<Pair<Long, Int>>): Int? {
        measurer.stop()
        val avg = if (samples.isNotEmpty()) samples.map { it.second }.average().toInt() else null
        if (avg != null) {
            runCatching {
                db.heartRateDao().insert(
                    HeartRateEntity(
                        bpm = avg.toDouble(),
                        measuredAt = System.currentTimeMillis(),
                        source = "watch_hsm",
                    )
                )
            }
            bt.syncAuto()
        }
        return avg
    }

    /**
     * 血氧页（真实测量，三星 Health Sensor SDK SPO2_ON_DEMAND）：
     * 待机 = 水滴O2 + 最后测量时间 + 大数字(%) + 测量按钮 + 如何测量分区；
     * 测量中 = 气泡动画 + 进度% + 请保持不动 + 佩戴异常提示；
     * 结果 = 蓝色大数字(%) + 当时心率 + 滑条 + 确定。
     */
    @Composable
    private fun Spo2Screen(onBack: () -> Unit) {
        val spo2SensorState by spo2Sensor.state.collectAsState()
        // 原始光信号采集状态（模块 #8）：它搭在血氧会话上，所以状态显示在这一页
        val ppgState by ppgRecorder.state.collectAsState()
        var sessionProgress by remember { mutableStateOf(0f) }
        var sessionResult by remember { mutableStateOf<Int?>(null) }
        var resultHr by remember { mutableStateOf<Int?>(null) }
        var sessionNoData by remember { mutableStateOf(false) }
        var lastSpo2 by remember { mutableStateOf<com.gemini.healthbridge.core.db.SpO2Entity?>(null) }

        LaunchedEffect(Unit) {
            lastSpo2 = runCatching { db.spo2Dao().latest() }.getOrNull()
        }
        // 离开页面时停止测量并断开传感器服务（灭灯）。光信号先结算（幂等，重复调用无副作用）
        DisposableEffect(Unit) {
            onDispose {
                runCatching { ppgRecorder.finish(null) }
                if (spo2Sensor.state.value.measuring) spo2Sensor.stopMeasurement()
            }
        }

        // 测量会话守护（单实例循环；勿以 measuring 为 key——停止瞬间协程被取消，失败页会随机丢失）：
        // 拿到首个有效血氧读数即完成；65 秒无读数判定失败；结束（成功/超时/手动停止）一律断开服务灭灯
        LaunchedEffect(Unit) {
            while (true) {
                while (!spo2Sensor.state.value.measuring) delay(100)
                runCatching {
                    sessionNoData = false
                    sessionResult = null
                    resultHr = null
                    sessionProgress = 0f
                    // 光信号采集随血氧会话一起开（开关关闭 / 本机不支持时内部直接返回）。
                    // 它自己会等服务连上再取 tracker，所以这里不必等连接完成。
                    ppgRecorder.begin()
                    val start = System.currentTimeMillis()
                    var lastShown = -1
                    while (spo2Sensor.state.value.measuring) {
                        val elapsed = System.currentTimeMillis() - start
                        // 进度封顶 99%：真正的 100% 只在拿到结果时出现
                        val p = (elapsed / 650f).coerceIn(0f, 99f)
                        // 仅在整数变化时更新状态，减少重组次数
                        if (p.toInt() != lastShown) {
                            lastShown = p.toInt()
                            sessionProgress = p
                        }
                        if (spo2Sensor.state.value.percent != null) break
                        if (elapsed >= 65_000) break
                        delay(200)
                    }
                    // 先结算光信号，再停血氧：血氧的 stopMeasurement() 会断开共享服务，
                    // 那时 PPG 的 tracker 就失效了（缓冲里的数据还在，但干净地摘监听只有现在能做）。
                    // 参考心率取血氧通道给出的值，存进 PPG 记录里供对照（拿不到就是 null）。
                    runCatching { ppgRecorder.finish(spo2Sensor.state.value.heartRate) }
                    if (spo2Sensor.state.value.measuring) spo2Sensor.stopMeasurement()
                    val result = spo2Sensor.state.value.percent
                    val hr = spo2Sensor.state.value.heartRate
                    if (result != null) {
                        db.spo2Dao().insert(
                            com.gemini.healthbridge.core.db.SpO2Entity(
                                percent = result, heartRate = hr,
                                measuredAt = System.currentTimeMillis(), source = "watch_o2",
                            )
                        )
                        runCatching { bt.syncAuto() }
                        sessionResult = result
                        resultHr = hr
                        lastSpo2 = runCatching { db.spo2Dao().latest() }.getOrNull()
                    } else {
                        sessionNoData = true
                    }
                    // ── 测量结束的震动提示（2026-10-07）──
                    // 拿到值一次短震、没拿到两短震。用户静坐等了最多 65 秒、多半没在看屏幕。
                    // ⚠ 落点必须在这里而**不是** `stopMeasurement()`：那个方法同时服务
                    //   "开始别的测量时停掉本会话"，放进它必然误震（详见 Buzzer 的说明）。
                    if (result != null) Buzzer.ok() else Buzzer.fail()
                }.onFailure {
                    sessionNoData = true
                    // 异常也是一次"结束了但没结果" —— 不震的话，用户会一直等下去
                    Buzzer.fail()
                }
            }
        }

        val measuring = spo2SensorState.measuring
        val scroll = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
            ScrollBarRight(scroll)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))

                if (measuring) {
                    // 测量中：进度 + 气泡动画
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${sessionProgress.toInt()}",
                            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                        )
                        Text("%", fontSize = 16.sp, color = Skin.Ink, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Spo2Pulse(measuring)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (spo2SensorState.percent == null) stringResource(R.string.s_bcf57fec) else stringResource(R.string.s_941394e0),
                        fontSize = 13.sp, color = Skin.Ink,
                    )
                    if (spo2SensorState.error != null) {
                        Text(resText(spo2SensorState.error!!), fontSize = 11.sp, color = AccentOrange)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { spo2Sensor.stopMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_00298a76), fontSize = 15.sp)
                    }
                } else if (sessionNoData) {
                    // 测量失败：不回退显示旧数据，并给出诊断信息
                    val diag = buildString {
                        append(spo2SensorState.error?.let { resText(it) } ?: stringResource(R.string.s_6ab46443))
                        if (spo2SensorState.lastStatus != null) {
                            append(stringResource(R.string.s_42a206a7, spo2SensorState.lastStatus.toString()))
                        }
                        if (spo2SensorState.receivedCount > 0) {
                            append(stringResource(R.string.s_31b6dd0d, spo2SensorState.receivedCount))
                        }
                    }
                    Text(stringResource(R.string.s_cb7439e8), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        diag,
                        fontSize = 13.sp, color = Skin.Muted,
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { stopAllMeasurements(); spo2Sensor.startMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_a446127f), fontSize = 15.sp)
                    }
                } else if (sessionResult != null) {
                    // 结果页
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$sessionResult",
                            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2979FF),
                        )
                        Text("%", fontSize = 18.sp, color = Color(0xFF2979FF), modifier = Modifier.padding(bottom = 8.dp))
                    }
                    resultHr?.let { hr ->
                        Text(stringResource(R.string.s_884f45ea, hr), fontSize = 16.sp, color = Skin.Ink)
                    }
                    Spacer(Modifier.height(10.dp))
                    ResultSlider(1f)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        // ⚠⚠ **结果页的「确定」= 退出回首页**（2026-10-08）—— 理由与体温页同一条，
                        //   这里是与它一起统一的（原来只 `sessionResult = null`，留在本页回待机，
                        //   与页脚「返回」在用户看来是同一件事）。
                        onClick = { sessionResult = null; onBack() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp)
                    }
                } else {
                    // 待机：最后测量（无波形）
                    O2DropletIcon()
                    Spacer(Modifier.height(2.dp))
                    val lastTimeStr = lastSpo2?.let {
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it.measuredAt))
                    } ?: "--:--"
                    Text(lastTimeStr, fontSize = 15.sp, color = AccentOrange)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            lastSpo2?.percent?.toString() ?: "--",
                            fontSize = 46.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2979FF),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("%", fontSize = 20.sp, color = Skin.Ink, modifier = Modifier.padding(bottom = 8.dp))
                    }
                    lastSpo2?.heartRate?.let { hr ->
                        Text(stringResource(R.string.s_884f45ea, hr), fontSize = 16.sp, color = Skin.Ink)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { stopAllMeasurements(); spo2Sensor.startMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_00387f30), fontSize = 15.sp)
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ─── 如何测量 ───
                SectionStart {
                    Text(stringResource(R.string.s_1364d3e3), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
                }
                HowMeasureArt()
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.s_28164748),
                    fontSize = 13.sp, color = Skin.Ink,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLineBox()

                // ─── 原始光信号（模块 #8）───
                // 这一项**没有独立入口**（总表 #8 定位就是"后台隐式采集"）：它搭在血氧测量上，
                // 顺手把 PPG 的绿/红/红外原始光计数记成波形，给后续自研算法（呼吸率/压力/PTT）当输入。
                // 之所以还是给它一块界面：它是本 App 唯一的"看不见的采集"，
                // 必须让人能关掉、也能一眼看出它到底采到没有 —— 否则出问题只能靠卸载来排查。
                SectionStart {
                    Text(stringResource(R.string.s_f6c116e3), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
                }
                Text(
                    ppgStatusLine(ppgState),
                    fontSize = 12.sp, color = Skin.Ink,
                    modifier = Modifier.fillMaxWidth(),
                )
                ppgState.lastStats?.let { st ->
                    Text(
                        stringResource(R.string.s_5c4f55ed, Ppg.formatPi(st.perfusionIndex), Ppg.formatQuality(st.quality)) +
                            Ppg.formatRate(st.sampleRateHz),
                        fontSize = 11.sp, color = Skin.Muted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        stringResource(R.string.s_a04fe0db, Ppg.formatHr(st.estimatedHr)) +
                            (Ppg.hrDeltaLabel(st.estimatedHr, st.refHr)?.let { "（$it）" } ?: ""),
                        fontSize = 11.sp, color = Skin.Muted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                // ⚠⚠ 手表端的「呼吸率」**已删**（2026-10-16，用户：「要删」）。
                //
                //   它算的是**光信号**那一路（`ppgState.lastResp` ← `Resp.fromPpg`），
                //   与手机端心电页那条"逐拍间期"的路**不是一回事** ——
                //   光信号用的是波形本身的时长，理论上能过 ≥30 秒那道门。
                //   ⚠ 但代码里原本就写着「这个模块**最常见的输出就是"给不出"**（记录不够长）」
                //     —— 也就是绝大多数时候它只占一块地方说"我给不出"，与用户删它的理由是同一个。
                //   ⚗ 手机端血氧页/记录页里的呼吸率是**同一条光信号路径**、仍然保留，
                //     所以这个能力并没有消失，只是不在表盘上占位了。
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = { ppgRecorder.setEnabled(!ppgState.enabled) },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.62f),
                ) {
                    Text(if (ppgState.enabled) stringResource(R.string.s_11846391) else stringResource(R.string.s_d122f742), fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.s_e3afccd4) +
                        stringResource(R.string.s_45bc97cd),
                    fontSize = 10.sp, color = Skin.Muted,
                    modifier = Modifier.fillMaxWidth(),
                )
                SectionLineBox()

                // ⚠⚠ 结果页**不画**这个页脚「返回」（2026-10-08）：那一屏已经有「确定」（= 退出回首页）。
                //   其余状态（测量中 / 待机 / 无 PPG 等）照旧保留 —— 它们的按钮是"停止 / 测量"，
                //   与返回不重复，去掉就出不去了。
                //   ⚠ 上面那个 PPG 采集开关**不受影响**：它是设置件，与"离开本页"无关。
                if (sessionResult == null) {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_0049f05a), fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            // 佩戴提示独立页（测量灯已灭，确定后回到待机手动重测）
            spo2SensorState.prompt?.let { promptText ->
                OffBodyPage(
                    text = resText(promptText),
                    onConfirm = { spo2Sensor.clearPrompt() },
                )
            }
        }
    }

    /**
     * 体温点检页。
     *
     * 存在理由：三星健康里的温度**只有睡眠期间**采集，而且它给的是"相对个人基线的偏差"（±0.x ℃），
     * 白天想看一眼"现在多少度"是拿不到的。手表自己的温度传感器可以随时按需测一次，
     * 给出的是**当下的绝对腕部皮肤温度**（°C），这才是"随时点检"。
     *
     * 页面状态机（与血氧页同构，含单实例会话守护）：
     *  · 不支持 → 明说本机不支持（Watch4 及更早没有温度传感器），不给一个永远测不出的界面；
     *  · 待机   → 显示最近一次点检；
     *  · 测量中 → **直接显示正在收敛的读数**（比转圈有用得多），并给已采样次数；
     *  · 结果   → 大数字 + 环境温度 + 位置感 + 确定；
     *  · 失败   → 诊断信息 + 重新测量。
     */
    @Composable
    private fun TempScreen(onBack: () -> Unit) {
        val st by tempSensor.state.collectAsState()
        var sessionResult by remember { mutableStateOf<Float?>(null) }
        var sessionAmbient by remember { mutableStateOf<Float?>(null) }
        var sessionNoData by remember { mutableStateOf(false) }
        var elapsedSec by remember { mutableIntStateOf(0) }
        var lastRec by remember { mutableStateOf<TemperatureEntity?>(null) }

        LaunchedEffect(Unit) {
            lastRec = runCatching { db.temperatureDao().latestOf(TempSource.WATCH) }.getOrNull()
        }
        // 离开页面时停止测量并断开传感器服务（on-demand tracker 不停会残留会话）
        DisposableEffect(Unit) {
            onDispose { if (tempSensor.state.value.measuring) tempSensor.stopMeasurement() }
        }

        // 测量会话守护（单实例循环；**勿以 measuring 为 key** ——
        // 停止的那一瞬间协程会被取消，失败页会随机丢失，血氧页踩过这个坑）
        LaunchedEffect(Unit) {
            while (true) {
                while (!tempSensor.state.value.measuring) delay(100)
                runCatching {
                    sessionNoData = false
                    sessionResult = null
                    sessionAmbient = null
                    elapsedSec = 0
                    val start = System.currentTimeMillis()
                    var firstValidAt = 0L
                    while (tempSensor.state.value.measuring) {
                        val elapsed = System.currentTimeMillis() - start
                        val sec = (elapsed / 1000L).toInt()
                        if (sec != elapsedSec) elapsedSec = sec
                        // 拿到第一个有效读数后再多收几秒：腕表接触面还在向皮肤温度收敛，
                        // 头几个点会偏低，中位数取样越多越稳。4 秒够收 4~8 个点。
                        if (firstValidAt == 0L && tempSensor.state.value.celsius != null) {
                            firstValidAt = System.currentTimeMillis()
                        }
                        if (firstValidAt > 0 &&
                            System.currentTimeMillis() - firstValidAt >= 4_000L
                        ) break
                        // 30 秒是 SDK 对 on-demand tracker 的硬上限，同时也是本页总超时
                        if (elapsed >= 30_000L) break
                        delay(200)
                    }
                    if (tempSensor.state.value.measuring) tempSensor.stopMeasurement()
                    val v = tempSensor.state.value.celsius
                    val amb = tempSensor.state.value.ambientC
                    if (v != null) {
                        db.temperatureDao().insert(
                            TemperatureEntity(
                                celsius = v, ambientC = amb,
                                measuredAt = System.currentTimeMillis(),
                                source = TempSource.WATCH,
                            )
                        )
                        runCatching { bt.syncAuto() }
                        sessionResult = v
                        sessionAmbient = amb
                        lastRec = runCatching {
                            db.temperatureDao().latestOf(TempSource.WATCH)
                        }.getOrNull()
                    } else {
                        sessionNoData = true
                    }
                    // ── 测量结束的震动提示（2026-10-07）：拿到值一次短震、没拿到两短震 ──
                    if (v != null) Buzzer.ok() else Buzzer.fail()
                }.onFailure {
                    sessionNoData = true
                    Buzzer.fail()
                }
            }
        }

        val measuring = st.measuring
        val scroll = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
        ScrollBarRight(scroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))

            when {
                // ── ① 本机没有温度传感器 ──
                st.supported == false -> {
                    Text(stringResource(R.string.s_f2592e8d), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s_4a2f2151),
                        fontSize = 12.sp, color = Skin.Ink,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    st.error?.let {
                        Text(resText(it), fontSize = 9.sp, color = Skin.Muted)
                    }
                    // ⚠ 这里原来还有一个「返回」按钮（2026-10-08 删）：它与页脚那个
                    //   **同款、同文案、同作用**（都是 `onBack`），同一屏出现两个"返回"是纯冗余。
                    //   ⚠ 页脚那个在本状态下**照常显示**（条件是 `sessionResult == null`，
                    //     而本状态根本没测过、必定为 null）⇒ 出口不受影响。
                }

                // ── ② 测量中：直接显示正在收敛的读数 ──
                measuring -> {
                    val live = st.celsius
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            live?.let { Temperature.format(it) } ?: "-.-",
                            fontSize = 44.sp, fontWeight = FontWeight.Bold,
                            color = live?.let { tempLevelColor(it) } ?: Color(0xFF8A8F99),
                        )
                        Text(
                            "℃", fontSize = 17.sp, color = Skin.Ink,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (st.statusText.isBlankText) stringResource(R.string.s_cfa65f41) else resText(st.statusText),
                        fontSize = 12.sp, color = Skin.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (st.validSamples == 0) stringResource(R.string.s_3ba302ef)
                        else stringResource(R.string.s_323e66f2, st.validSamples, elapsedSec),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                    st.ambientC?.let {
                        Text(stringResource(R.string.s_9864a455, Temperature.format(it)), fontSize = 10.sp, color = Skin.Muted)
                    }
                    st.error?.let { Text(resText(it), fontSize = 10.sp, color = AccentOrange) }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { tempSensor.stopMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_00298a76), fontSize = 15.sp) }
                }

                // ── ③ 失败 ──
                sessionNoData -> {
                    Text(stringResource(R.string.s_cb7439e8), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        buildString {
                            append(st.error?.let { resText(it) } ?: stringResource(R.string.s_e83470cd))
                            st.lastStatus?.let { append(stringResource(R.string.s_3a4024bf, it)) }
                            if (st.receivedCount > 0) append(stringResource(R.string.s_99a6058f, st.receivedCount))
                        },
                        fontSize = 11.sp, color = Skin.Muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s_0b59712f),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = { stopAllMeasurements(); tempSensor.startMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_a446127f), fontSize = 15.sp) }
                }

                // ── ④ 结果 ──
                sessionResult != null -> {
                    val v = sessionResult!!
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            Temperature.format(v),
                            fontSize = 44.sp, fontWeight = FontWeight.Bold, color = tempLevelColor(v),
                        )
                        Text(
                            "℃", fontSize = 17.sp, color = Skin.Ink,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        Temperature.levelLabel(v) + stringResource(R.string.s_e723a173),
                        fontSize = 12.sp, color = tempLevelColor(v),
                    )
                    sessionAmbient?.let {
                        Text(stringResource(R.string.s_9864a455, Temperature.format(it)), fontSize = 11.sp, color = Skin.Ink)
                    }
                    // 核心体温估算（模块 #10）：输入与手机端**同源** —— 皮温取本次点检的绝对值，
                    // 环境温是传感器一并给出的那一个。240dp 圆屏放不下模型说明，所以这里只给
                    // 中心值与系数区间，完整依据在手机端体温页。
                    when (val cr = Temperature.estimateCore(v, sessionAmbient)) {
                        is Temperature.CoreResult.Ok -> Text(
                            stringResource(R.string.s_8f507432, Temperature.format(cr.centralC)) +
                                stringResource(R.string.s_d41e377e, Temperature.coreIntervalText(cr)),
                            fontSize = 10.sp, color = CoreEstimateColor,
                        )
                        // 拒绝也要说一句，而且要说到**能照做**：原来只写"原因见手机端体温页"，
                        // 用户拿着手表、看不到手机，等于没给办法。240dp 圆屏放不下 [CORE_PROTOCOL_NOTE]
                        // 四条，就只给**最管用的那一条**（表带 + 静置），其余指向手机端。
                        is Temperature.CoreResult.NotApplicable -> Text(
                            stringResource(R.string.s_955e30ae) +
                                stringResource(R.string.s_309af912),
                            fontSize = 9.sp, color = Skin.Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    TempLevelBar(v)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.s_522fda79),
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        // ⚠⚠ **结果页的「确定」= 退出回首页**（2026-10-08，与心电页 2026-10-02 的定案统一）。
                        //
                        // 改之前这里是 `{ sessionResult = null }` —— 只清掉本次结果、**留在本页**回待机，
                        // 而页脚还有一个真正能退出的「返回」⇒ 结果页上**两个按钮都在"结束"这件事上生效**，
                        // 用户滚动时同时看到它们，判断成"两个按钮作用一样"
                        // （用户原话：「有确定和返回两个按钮，实际他们的作用都一致」）。
                        //
                        // ⚗ 定案依据是心电页那次的经验：那里原来也把「确定」设成"回待机"，
                        //   用户点完落到待机页，**感受是"没退出"**（见 EcgScreen 结果页的注释）。
                        //   本页唯一的出口就该是「我看完了，结束」。
                        //   想再测一次的用户从首页体温入口进来（一次点击），
                        //   不必为此在圆屏底部再挤一个同级按钮。
                        onClick = { sessionResult = null; onBack() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp) }
                }

                // ── ⑤ 待机：最近一次点检 ──
                else -> {
                    // ⚠ 2026-10-20 用户要求：标题**再往下挪一点**，并让 "check" 换到第二行**居中**
                    //   （圆屏顶部弦宽很窄，整行铺满时左边缘一定越过圆弧被切）。
                    Spacer(Modifier.height(22.dp))
                    Text(
                        stringResource(R.string.s_7338fe2b),
                        // ⚠ `fillMaxWidth(f)` 是相对**已扣掉页面左右内边距**的列宽，
                        //   0.72 实际只有 ~111dp，仍会把 "Watch" 单独挤一行 ⇒ 用 0.95。
                        //   结果恰好两行："Watch temperature" / "check"，整体居中。
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Skin.Ink,
                        textAlign = TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth(0.95f),
                    )
                    Spacer(Modifier.height(2.dp))
                    val lastTimeStr = lastRec?.let {
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.measuredAt))
                    } ?: stringResource(R.string.s_729bb368)
                    Text(lastTimeStr, fontSize = 11.sp, color = AccentOrange)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            lastRec?.let { Temperature.format(it.celsius) } ?: "--.-",
                            fontSize = 42.sp, fontWeight = FontWeight.Bold,
                            color = lastRec?.let { tempLevelColor(it.celsius) } ?: Color(0xFF8A8F99),
                        )
                        Text(
                            "℃", fontSize = 16.sp, color = Skin.Ink,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                    lastRec?.let { r ->
                        Text(
                            Temperature.levelLabel(r.celsius) +
                                (r.ambientC?.let { stringResource(R.string.s_ba5920d2, Temperature.format(it)) } ?: ""),
                            fontSize = 11.sp, color = tempLevelColor(r.celsius),
                        )
                        // 待机页只补一行"估算值"，不适用时**什么都不显示**：
                        // 这一页是概览，常见的不适用原因（上一条点检没带环境温）不值得占一屏。
                        (Temperature.estimateCore(r.celsius, r.ambientC)
                            as? Temperature.CoreResult.Ok)?.let { cr ->
                            Text(
                                stringResource(R.string.s_36db4a82, Temperature.format(cr.centralC)),
                                fontSize = 10.sp, color = CoreEstimateColor,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { stopAllMeasurements(); tempSensor.startMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_00387f30), fontSize = 15.sp) }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.s_87de2895) +
                            stringResource(R.string.s_53c93968) +
                            stringResource(R.string.s_cde639a1),
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLineBox()
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.s_35961854),
                fontSize = 9.sp, color = Skin.Muted,
            )
            // ⚠⚠ 结果页**不画**这个页脚「返回」（2026-10-08）：那一屏已经有「确定」（= 退出回首页），
            //   两个都能离开本页 ⇒ 用户会问"这俩有什么区别"（他这次就是这么报的）。
            //   其余状态（不支持 / 测量中 / 失败 / 待机）**照旧有它** ——
            //   那些状态的按钮是"停止 / 重新测量 / 测量"，与返回不重复，且**去掉它就出不去了**。
            if (sessionResult == null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) { Text(stringResource(R.string.s_0049f05a), fontSize = 15.sp) }
            }
            Spacer(Modifier.height(10.dp))
        }
        }
    }

    /**
     * BIA 姿势示意：在表壳右侧标出 2 点钟与 4 点钟两个电极键的位置。
     *
     * 刻意画出示意图而不是只写"2 点钟方向"：这个说法对很多人不直观，
     * 而手指按错位置正是最常见的失败原因（对应状态码 7/8/9）。
     *
     * 角度直接写成常数而不是调 `cos/sin`：2 点钟 = 从 12 点顺时针 60°，
     * 换算到 Compose 的角度约定（0° 指向 3 点方向、顺时针为正）就是 -30°，
     * 于是 `cos(-30°)=0.866`、`sin(-30°)=-0.5`；4 点钟对称，取 +30°。
     * 打表比调三角函数少一层"圆心/半径算错"的可能。
     */
    @Composable
    private fun BiaPostureGuide() {
        Canvas(modifier = Modifier.size(60.dp)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = size.minDimension / 2f - 3.dp.toPx()
            drawCircle(
                color = Skin.Muted, radius = r, center = c,
                style = Stroke(width = 1.5f),
            )
            val kx = 0.866f * r
            val ky = 0.5f * r
            val dot = 5.dp.toPx()
            // 2 点钟方向（偏上）
            drawCircle(color = Color(0xFF4DD0E1), radius = dot, center = Offset(c.x + kx, c.y - ky))
            // 4 点钟方向（偏下）
            drawCircle(color = Color(0xFF4DD0E1), radius = dot, center = Offset(c.x + kx, c.y + ky))
        }
    }

    /**
     * 多频阻抗测量（MF-BIA）：采四个频点的原始阻抗，落库后由手机端做分析。
     *
     * ## 与体成分页的关系
     * 同一个姿势（手指盖侧键）、同一个资料前提，但**是两个独立采集器**：
     * 体成分读三星算好的成品，这里拿的是原始阻抗谱。所以本页只显示**原始量**
     * （各频点的 |Z| 与相位 + 5k/250k 比值），复杂的口径说明在手机端 ——
     * 圆屏放不下，也不该在这里看。
     *
     * ## 手表上刻意不给"正常/异常"
     * 阻抗比的绝对水平个体差异很大（体型、水合状态、测量时刻都会影响），
     * 单看一次没有可比性。所以这里只报数，趋势留到手机端与自身基线比。
     */
    @Composable
    private fun MfBiaScreen(onBack: () -> Unit) {
        val st by mfBiaSensor.state.collectAsState()
        val ctx = this@MainActivity

        var profile by remember { mutableStateOf<BiaProfileDto?>(null) }
        var result by remember { mutableStateOf<MfBiaSensorManager.MfBiaResult?>(null) }
        var savedShape by remember { mutableStateOf<String?>(null) }
        var sessionError by remember { mutableStateOf<ResText?>(null) }
        var elapsedSec by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) { profile = BiaProfileStore.load(ctx) }

        DisposableEffect(Unit) {
            onDispose { if (mfBiaSensor.state.value.measuring) mfBiaSensor.stopMeasurement() }
        }

        // 测量会话守护（单实例循环；勿以 measuring 为 key —— 停止瞬间协程被取消，结果页会丢）
        LaunchedEffect(Unit) {
            while (true) {
                while (!mfBiaSensor.state.value.measuring) delay(100)
                runCatching {
                    result = null
                    sessionError = null
                    savedShape = null
                    elapsedSec = 0
                    val start = System.currentTimeMillis()
                    while (mfBiaSensor.state.value.measuring) {
                        val sec = ((System.currentTimeMillis() - start) / 1000L).toInt()
                        if (sec != elapsedSec) elapsedSec = sec
                        if (sec >= 30) break
                        delay(200)
                    }
                    if (mfBiaSensor.state.value.measuring) mfBiaSensor.stopMeasurement()
                    val stv = mfBiaSensor.state.value
                    val r = stv.result
                    if (r != null) {
                        // 落库的是**原始值**：派生量（比值/趋势）由两端各自的 MfBia.analyze 现算，
                        // 存派生量会出现"同一份原始数据、两个不同口径的结论"。
                        db.mfBiaDao().insert(
                            MfBiaEntity(
                                measuredAt = System.currentTimeMillis(),
                                m5 = r.m5, p5 = r.p5,
                                m10 = r.m10, p10 = r.p10,
                                m50 = r.m50, p50 = r.p50,
                                m250 = r.m250, p250 = r.p250,
                                status = r.status,
                                source = MfBiaSource.WATCH,
                            )
                        )
                        runCatching { bt.syncAuto() }
                        result = r
                        // 顺带把这次的自证结论算出来显示（四频齐备 + 单调性通过）
                        savedShape = when (val a = MfBia.analyze(
                            MfBia.Input(
                                measuredAt = 0L,
                                points = listOf(
                                    MfBia.Point(MfBia.Freq.K5, r.m5, r.p5),
                                    MfBia.Point(MfBia.Freq.K10, r.m10, r.p10),
                                    MfBia.Point(MfBia.Freq.K50, r.m50, r.p50),
                                    MfBia.Point(MfBia.Freq.K250, r.m250, r.p250),
                                ),
                                status = r.status,
                            )
                        )) {
                            is MfBia.Result.Ok -> MfBia.formatRatio(a.ratio)
                            is MfBia.Result.NotApplicable -> null
                        }
                    } else {
                        // ⚠ 模板里不许嵌 ASCII 引号（提取器会抓到半截串），先算好再插
                        // ⚠ 这段在 `onSuccess` 回调里（**非 Composable**），所以回落判断用
                        //   结构判空 `isBlankText`，不能 `resText`。
                        val mfStatus = mfBiaSensor.state.value.statusText
                        val mfBiaWhy = if (mfStatus.isBlankText) ResText.of(R.string.s_45f23c4c) else mfStatus
                        sessionError = stv.error ?: ResText.of(R.string.s_55100b1f, mfBiaWhy)
                    }
                    // ── 测量结束的震动提示（2026-10-07）：拿到结果一次短震、没拿到两短震 ──
                    // 用户手按电极等了十几秒、眼睛看不了屏幕（这正是它最需要提示的场景）。
                    if (r != null) Buzzer.ok() else Buzzer.fail()
                }.onFailure { e ->
                    sessionError = ResText.of(R.string.s_e88c4114, e.javaClass.simpleName, e.message)
                    Buzzer.fail()
                }
            }
        }

        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_18b7579f), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            if (st.supported == false) {
                item {
                    Text(
                        stringResource(R.string.s_6793e6f9),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            } else if (profile == null) {
                item {
                    Text(
                        stringResource(R.string.s_be5710ee),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            } else if (st.measuring) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.s_6aeb98a5, elapsedSec), fontSize = 13.sp, color = Skin.Ink)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (st.statusText.isBlankText) stringResource(R.string.s_b59e7dca) else resText(st.statusText),
                            fontSize = 9.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.78f),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.s_a2ea4dc7, (st.progress.coerceIn(0f, 1f) * 100).toInt()),
                            fontSize = 10.sp, color = MfBiaColor,
                        )
                    }
                }
            } else {
                val r = result
                if (r == null) {
                    item {
                        Text(
                            resText(
                                sessionError ?: (ResText.of(R.string.s_e3af5510) +
                                    ResText.of(R.string.s_67b88bc4))
                            ),
                            fontSize = 10.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.78f),
                        )
                    }
                } else {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.s_2789b889), fontSize = 10.sp, color = Skin.Muted)
                            Text(
                                "|Z| " + MfBia.formatMagnitude(r.m5) + " → " +
                                    MfBia.formatMagnitude(r.m250),
                                fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MfBiaColor,
                            )
                            Text(
                                stringResource(R.string.s_9e963ad3),
                                fontSize = 9.sp, color = Skin.Muted,
                            )
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                stringResource(R.string.s_554cfbc5),
                                fontSize = 10.sp, color = Skin.Muted,
                            )
                            Text(
                                "5k ${MfBia.formatPhase(r.p5)} · 10k ${MfBia.formatPhase(r.p10)}",
                                fontSize = 10.sp, color = Skin.Ink,
                            )
                            Text(
                                "50k ${MfBia.formatPhase(r.p50)} · 250k ${MfBia.formatPhase(r.p250)}",
                                fontSize = 10.sp, color = Skin.Ink,
                            )
                        }
                    }
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val ratio = savedShape
                            if (ratio != null) {
                                Text(stringResource(R.string.s_f7c452d1), fontSize = 10.sp, color = Skin.Muted)
                                Text(
                                    ratio,
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MfBiaColor,
                                )
                            } else {
                                Text(
                                    stringResource(R.string.s_abbf3431) +
                                        stringResource(R.string.s_2c5627e2),
                                    fontSize = 9.sp, color = AccentOrange,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth(0.78f),
                                )
                            }
                        }
                    }
                    item {
                        Text(
                            stringResource(R.string.s_7920fccf),
                            fontSize = 9.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.78f),
                        )
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!st.measuring && st.supported != false && profile != null) {
                        Button(
                            onClick = { stopAllMeasurements(); mfBiaSensor.startMeasurement(profile) },
                            colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        ) { Text(if (result == null) stringResource(R.string.s_d2884d53) else stringResource(R.string.s_0a399723), fontSize = 13.sp) }
                    }
                    PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) }
                }
            }
        }
    }

    @Composable
    /**
     * 体成分（BIA）测量页。
     *
     * 与体温页的两处关键差别：
     *  ① **必须先有用户资料**（身高/体重/性别/年龄，由手机读三星健康后经蓝牙推来并缓存在本地）。
     *     资料缺失时直接说明去哪儿补，而不是给一个必然失败的测量按钮。
     *  ② 失败几乎全是**姿势问题**，所以每种状态码都给出**具体动作**
     *     （话术集中在 `BodyComposition.statusOf`）。只说"测量失败请重试"，
     *     用户会反复失败却始终不知道要改什么 —— BIA 的失败恰恰是用户自己能立刻改对的。
     */
    private fun BiaScreen(onBack: () -> Unit, onOpenMf: () -> Unit) {
        val st by biaSensor.state.collectAsState()
        val ctx = this@MainActivity

        var profile by remember { mutableStateOf<BiaProfileDto?>(null) }
        var profileAt by remember { mutableStateOf(0L) }
        var sessionResult by remember { mutableStateOf<BiaSensorManager.BiaResult?>(null) }
        var sessionStatus by remember { mutableStateOf<BodyComposition.StatusInfo?>(null) }
        var sessionError by remember { mutableStateOf<ResText?>(null) }
        var elapsedSec by remember { mutableIntStateOf(0) }
        var lastRec by remember { mutableStateOf<BodyCompositionEntity?>(null) }
        // null = 还没探测出来（服务未连），只有明确 false 才藏入口
        var mfSupported by remember { mutableStateOf<Boolean?>(null) }

        // 资料是手机推来的，可能刚在后台更新过，所以每次回到本页都重读一次
        LaunchedEffect(Unit) {
            profile = BiaProfileStore.load(ctx)
            profileAt = BiaProfileStore.savedAt(ctx)
            lastRec = runCatching { db.bodyCompositionDao().latestOf(BiaSource.WATCH) }.getOrNull()
            // 多频阻抗的能力探测。null（还没连上服务）时按"可能支持"处理：
            // 探测要等服务连上，而进这一页时通常还没连，报 false 会把入口误藏。
            mfSupported = mfBiaSensor.probeSupport()
        }

        // 离开页面收尾：BIA 是 on-demand tracker，不停会残留会话
        DisposableEffect(Unit) {
            onDispose { if (biaSensor.state.value.measuring) biaSensor.stopMeasurement() }
        }

        // 测量会话守护（单实例循环）。**勿以 measuring 为 key** ——
        // 停止的那一瞬间协程会被取消，结果页会随机丢失，血氧页与体温页都踩过这个坑。
        LaunchedEffect(Unit) {
            while (true) {
                while (!biaSensor.state.value.measuring) delay(100)
                runCatching {
                    sessionResult = null
                    sessionStatus = null
                    sessionError = null
                    elapsedSec = 0
                    val start = System.currentTimeMillis()
                    // 官方给的时长量级是十几秒；30 秒留足余量，同时作为本次的总超时。
                    while (biaSensor.state.value.measuring) {
                        val sec = ((System.currentTimeMillis() - start) / 1000L).toInt()
                        if (sec != elapsedSec) elapsedSec = sec
                        if (sec >= 30) break
                        delay(200)
                    }
                    // 超时那一刻管理器的 measuring 还是 true，必须先停掉再取值
                    if (biaSensor.state.value.measuring) biaSensor.stopMeasurement()
                    val stv = biaSensor.state.value
                    val r = stv.result
                    if (r != null) {
                        // 资料快照必须一起落库：体脂率是算法用资料反推出来的，
                        // 不记下当时的资料，用户改了体重之后回头看就会以为"同一时刻测出两个值"
                        val p = BiaProfileStore.load(ctx)
                        db.bodyCompositionDao().insert(
                            BodyCompositionEntity(
                                bodyFatRatio = r.bodyFatRatio,
                                bodyFatMass = r.bodyFatMass,
                                totalBodyWater = r.totalBodyWater,
                                skeletalMuscleRatio = r.skeletalMuscleRatio,
                                skeletalMuscleMass = r.skeletalMuscleMass,
                                basalMetabolicRate = r.basalMetabolicRate,
                                fatFreeRatio = r.fatFreeRatio,
                                fatFreeMass = r.fatFreeMass,
                                impedanceMagnitude = r.impedanceMagnitude,
                                impedanceDegree = r.impedanceDegree,
                                weightKg = p?.weightKg,
                                heightCm = p?.heightCm,
                                gender = p?.gender,
                                age = p?.age,
                                measuredAt = System.currentTimeMillis(),
                                source = BiaSource.WATCH,
                            )
                        )
                        runCatching { bt.syncAuto() }
                        sessionResult = r
                        lastRec = runCatching {
                            db.bodyCompositionDao().latestOf(BiaSource.WATCH)
                        }.getOrNull()
                    } else {
                        // 拿最后一次收到的状态码去查话术：失败时它往往就是"手指没放上"这种
                        // 能立刻纠正的原因，比笼统的"测量失败"有用得多
                        sessionStatus = BodyComposition.statusOf(stv.lastStatus)
                        sessionError = stv.error
                    }
                    // ── 测量结束的震动提示（2026-10-07）：拿到结果一次短震、没拿到两短震 ──
                    // 用户手按电极等了十几秒、眼睛看不了屏幕（这正是它最需要提示的场景）。
                    if (r != null) Buzzer.ok() else Buzzer.fail()
                }.onFailure { e ->
                    sessionError = ResText.of(R.string.s_e88c4114, e.javaClass.simpleName, e.message)
                    Buzzer.fail()
                }
            }
        }

        val measuring = st.measuring
        val scroll = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
        ScrollBarRight(scroll)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 22.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(4.dp))

            when {
                // ── ① 本机没有 BIA 电极（Watch4 及更早） ──
                st.supported == false -> {
                    Text(stringResource(R.string.s_f2592e8d), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s_9dc1fa2a),
                        fontSize = 12.sp, color = Skin.Ink,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    st.error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(resText(it), fontSize = 9.sp, color = Skin.Muted)
                    }
                }

                // ── ② 结果 ──
                sessionResult != null -> {
                    val r = sessionResult!!
                    val fat = r.bodyFatRatio
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            BodyComposition.format0(fat),
                            fontSize = 44.sp, fontWeight = FontWeight.Bold,
                            color = if (BodyComposition.inRange(
                                    fat, BodyComposition.FAT_RATIO_MIN, BodyComposition.FAT_RATIO_MAX,
                                )
                            ) Skin.Ink else Color(0xFF8A8F99),
                        )
                        Text(
                            "%", fontSize = 17.sp, color = Skin.Ink,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.s_66237d8d) + BodyComposition.bodyFatHint(fat, profile?.gender) + stringResource(R.string.s_42910a3c),
                        fontSize = 12.sp, color = Skin.Ink,
                    )
                    Spacer(Modifier.height(8.dp))
                    BiaMetricRow(stringResource(R.string.s_150818c0), BodyComposition.formatKg(r.bodyFatMass))
                    BiaMetricRow(stringResource(R.string.s_28cf16a8), BodyComposition.formatKg(r.skeletalMuscleMass))
                    BiaMetricRow(stringResource(R.string.s_195bd6c0), BodyComposition.formatLiters(r.totalBodyWater))
                    BiaMetricRow(stringResource(R.string.s_e9855ae9), BodyComposition.formatKcal(r.basalMetabolicRate))
                    BiaMetricRow(stringResource(R.string.s_004d42c8), BodyComposition.formatOhm(r.impedanceMagnitude))
                    BiaMetricRow(stringResource(R.string.s_003d6aba), BodyComposition.formatDegree(r.impedanceDegree))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.s_015699e6),
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        // ⚠⚠ **结果页的「确定」= 退出回首页**（2026-10-08）—— 与体温 / 血氧页一起统一，
                        //   理由同那两处（原来只清结果、留在本页，与页脚「返回」在用户看来是一回事）。
                        onClick = { sessionResult = null; onBack() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp) }
                }

                // ── ③ 失败：给出可执行的动作，而不是一句"请重试" ──
                sessionStatus != null || sessionError != null -> {
                    Text(stringResource(R.string.s_cb7439e8), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    sessionStatus?.let { s ->
                        Text(
                            resText(s.label), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            resText(s.guidance), fontSize = 11.sp, color = Skin.Muted,
                            modifier = Modifier.padding(horizontal = 4.dp),
                        )
                    }
                    sessionError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(resText(it), fontSize = 9.sp, color = Skin.Muted)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.s_35045c02), fontSize = 10.sp, color = Skin.Muted)
                    Spacer(Modifier.height(4.dp))
                    BiaPostureGuide()
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { stopAllMeasurements(); biaSensor.startMeasurement(profile) },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.6f),
                    ) { Text(stringResource(R.string.s_a446127f), fontSize = 15.sp) }
                }

                // ── ④ 测量中：进度环 + 姿势提醒 ──
                measuring -> {
                    Canvas(modifier = Modifier.size(92.dp)) {
                        val sw = 8.dp.toPx()
                        val d = size.minDimension - sw
                        val tl = Offset(sw / 2f, sw / 2f)
                        val sz = Size(d, d)
                        drawArc(
                            color = Color(0x22FFFFFF), startAngle = 0f, sweepAngle = 360f,
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(width = sw, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = AccentOrange, startAngle = -90f,
                            sweepAngle = 360f * st.progress.coerceIn(0f, 1f),
                            useCenter = false, topLeft = tl, size = sz,
                            style = Stroke(width = sw, cap = StrokeCap.Round),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${(st.progress.coerceIn(0f, 1f) * 100).toInt()}%",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                    )
                    Text(
                        if (st.statusText.isBlankText) stringResource(R.string.s_cfa65f41) else resText(st.statusText),
                        fontSize = 11.sp, color = Skin.Ink,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.s_a7f567b1, elapsedSec),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    BiaPostureGuide()
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { biaSensor.stopMeasurement() },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_00298a76), fontSize = 15.sp) }
                }

                // ── ⑤ 缺资料：说清去哪儿补，不给一个必然失败的按钮 ──
                profile == null -> {
                    Text(stringResource(R.string.s_844f8c5d), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s_56828c1b),
                        fontSize = 11.sp, color = Skin.Ink,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.s_5a8d8193),
                        fontSize = 11.sp, color = Skin.Muted,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                    st.error?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(resText(it), fontSize = 9.sp, color = Skin.Muted)
                    }
                }

                // ── ⑥ 待机：最近一次结果 + 开始 ──
                else -> {
                    Text(stringResource(R.string.s_14f80321), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        lastRec?.let {
                            SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.measuredAt))
                        } ?: stringResource(R.string.s_729bb368),
                        fontSize = 11.sp, color = AccentOrange,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            lastRec?.let { BodyComposition.format0(it.bodyFatRatio) } ?: "--",
                            fontSize = 42.sp, fontWeight = FontWeight.Bold,
                            color = lastRec?.let { Skin.Ink } ?: Color(0xFF8A8F99),
                        )
                        Text(
                            "%", fontSize = 16.sp, color = Skin.Ink,
                            modifier = Modifier.padding(bottom = 7.dp),
                        )
                    }
                    lastRec?.let { r ->
                        Text(
                            stringResource(R.string.s_f21e8fb5) + BodyComposition.formatKg(r.skeletalMuscleMass),
                            fontSize = 11.sp, color = Skin.Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = { stopAllMeasurements(); biaSensor.startMeasurement(profile) },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) { Text(stringResource(R.string.s_00387f30), fontSize = 15.sp) }
                    Spacer(Modifier.height(6.dp))
                    // 多频阻抗入口：同一个姿势、同一个页面。
                    // **支持才显示** —— 本机是否支持 MF_BIA_ON_DEMAND 要在真机上验证，
                    // 不支持时显示一个点了就失败的按钮比不显示更糟。
                    if (mfSupported != false) {
                        Button(
                            onClick = { onOpenMf() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3A3F4A)),
                            modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                        ) { Text(stringResource(R.string.s_18b7579f), fontSize = 13.sp) }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.s_2c3c174f),
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                    if (profileAt > 0L) {
                        Text(
                            stringResource(R.string.s_2532bfc9) + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                                .format(Date(profileAt)) + stringResource(R.string.s_0033cea9),
                            fontSize = 9.sp, color = Skin.Muted,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLineBox()
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.s_5c0db82f),
                fontSize = 9.sp, color = Skin.Muted,
            )
            // ⚠⚠ 结果页**不画**这个页脚「返回」（2026-10-08）：那一屏已经有「确定」（= 退出回首页）。
            //   其余状态照旧保留（其余状态的按钮是"停止 / 重新测量 / 测量 / 多频阻抗"）。
            if (sessionResult == null) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onBack,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) { Text(stringResource(R.string.s_0049f05a), fontSize = 15.sp) }
            }
            Spacer(Modifier.height(10.dp))
        }
        }
    }

    /** 体成分结果里的一行"标签 —— 数值"，值为空时显示占位符。 */
    @Composable
    private fun BiaMetricRow(label: String, value: String) {
        Row(
            modifier = Modifier.fillMaxWidth(0.8f).padding(vertical = 1.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 11.sp, color = Skin.Muted)
            Text(value, fontSize = 11.sp, color = Skin.Ink)
        }
    }

    /**
     * 位置感指示条：把当前值放在 [Temperature.MIN_C]~[Temperature.MAX_C] 上的相对位置。
     *
     * 刻意**不做**三档"红黄绿"评分块 —— 那会被读成"偏红就是发烧"。
     * 这里只画一根轴和一个游标，让用户看到"它在哪儿"，判断留给用户与医生。
     */
    @Composable
    private fun TempLevelBar(c: Float) {
        val lo = Temperature.MIN_C
        val hi = Temperature.MAX_C
        val frac = ((c - lo) / (hi - lo)).coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxWidth(0.72f).height(14.dp)) {
            val w = size.width
            val h = size.height
            val y = h * 0.5f
            val barH = h * 0.30f
            // 底轴
            drawRoundRect(
                color = Skin.Hairline,
                topLeft = Offset(0f, y - barH / 2f),
                size = Size(w, barH),
                cornerRadius = CornerRadius(barH / 2f),
            )
            // 常见范围参照带
            val a = ((Temperature.LOW_C - lo) / (hi - lo)) * w
            val b = ((Temperature.HIGH_C - lo) / (hi - lo)) * w
            drawRoundRect(
                color = Color(0x3355C878),
                topLeft = Offset(a, y - barH / 2f),
                size = Size((b - a).coerceAtLeast(1f), barH),
                cornerRadius = CornerRadius(barH / 2f),
            )
            // 游标
            val x = frac * w
            drawCircle(color = Skin.Ink, radius = h * 0.34f, center = Offset(x, y))
            drawCircle(color = tempLevelColor(c), radius = h * 0.22f, center = Offset(x, y))
        }
    }

    /** 彩色气泡动画（血氧测量中）：铺满表盘的大气泡群，低透明光晕 + 实心主体，漂移范围大。 */
    /**
     * 血氧测量动画：**只在测量中运行**，三圈向外扩散的涟漪。
     *
     * ⚠ 两个反馈一起修：
     *   ① 原来那版彩色大气泡"太 low"，且用户把它们做成了静态（因为我把无限动画全删了）；
     *   ② 有用户反馈**超出屏幕** —— 旧实现用 `height(210.dp)` + 画布外圆心，圆屏上必然溢出。
     *   现在画布用 `aspectRatio(1f)` 且所有半径都按 `size.minDimension` 算，天然不越界。
     */
    @Composable
    private fun Spo2Pulse(measuring: Boolean) {
        if (!measuring) {
            // 静止时连 transition 都不创建：停在血氧页不会持续重绘（老表友好）
            Canvas(Modifier.fillMaxWidth(0.40f).aspectRatio(1f)) {
                drawCircle(
                    Color(0xFF2F9BFF).copy(alpha = 0.10f),
                    radius = size.minDimension * 0.42f,
                    center = Offset(size.width / 2f, size.height / 2f),
                )
            }
            return
        }
        val t = rememberInfiniteTransition(label = "spo2pulse")
        val ph by t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1900, easing = LinearEasing)),
            label = "spo2phase",
        )
        val scan by t.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label = "spo2scan",
        )
        Canvas(Modifier.fillMaxWidth(0.56f).aspectRatio(1f)) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val maxR = size.minDimension * 0.46f
            val blue = Color(0xFF2F9BFF)
            val red = Color(0xFFFF5368)
            val breath = 0.94f + 0.06f * sin(ph * 2f * Math.PI).toFloat()
            drawCircle(blue.copy(alpha = 0.08f), radius = maxR * 0.78f * breath, center = c)
            drawCircle(blue.copy(alpha = 0.18f), radius = maxR * 0.47f * breath, center = c)
            drawCircle(blue, radius = maxR * 0.18f * breath, center = c)
            drawArc(red.copy(alpha = 0.82f), scan - 42f, 78f, false, Offset(c.x - maxR * 0.82f, c.y - maxR * 0.82f), Size(maxR * 1.64f, maxR * 1.64f), style = Stroke(width = size.minDimension * 0.018f, cap = StrokeCap.Round))
            repeat(3) { i ->
                val p = (ph + i / 3f) % 1f
                drawCircle(
                    color = blue.copy(alpha = (1f - p) * 0.38f),
                    radius = maxR * (0.30f + 0.68f * p),
                    center = c,
                    style = Stroke(width = size.minDimension * 0.022f),
                )
            }
        }
    }
    /** "如何测量"示意图：桌面 + 屈臂 + 手表。 */
    @Composable
    private fun HowMeasureArt() {
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val w = size.width; val h = size.height
            drawLine(
                Color(0xFF8A97A5),
                start = Offset(0f, h * 0.82f), end = Offset(w, h * 0.82f),
                strokeWidth = 7f, cap = StrokeCap.Round,
            )
            withTransform({ rotate(-38f, pivot = Offset(w * 0.34f, h * 0.55f)) }) {
                drawRoundRect(
                    color = Color(0xFFD8DDE3),
                    topLeft = Offset(w * 0.20f, h * 0.28f),
                    size = Size(w * 0.26f, h * 0.54f),
                    cornerRadius = CornerRadius(26f, 26f),
                )
            }
            drawRoundRect(
                color = Color(0xFFE7EBEF),
                topLeft = Offset(w * 0.24f, h * 0.60f),
                size = Size(w * 0.52f, h * 0.22f),
                cornerRadius = CornerRadius(30f, 30f),
            )
            drawCircle(Color(0xFF2F9BFF), radius = w * 0.075f, center = Offset(w * 0.68f, h * 0.71f))
        }
    }

    /** 结果进度条（滑条 + 圆点）。 */
    @Composable
    private fun ResultSlider(progress: Float) {
        Canvas(Modifier.fillMaxWidth(0.86f).height(10.dp)) {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(Skin.Hairline, cornerRadius = r)
            val done = size.width * progress.coerceIn(0f, 1f)
            drawRoundRect(
                brush = Brush.horizontalGradient(listOf(Color(0xFF63B3F5), Color(0xFF1746A2))),
                topLeft = Offset(0f, 0f),
                size = Size(done, size.height),
                cornerRadius = r,
            )
            drawCircle(
                Color(0xFFE9EEF5),
                radius = size.height * 0.55f,
                center = Offset((done - size.height * 0.4f).coerceAtLeast(size.height * 0.4f), size.height / 2f),
            )
        }
    }

    /** 水滴 O2 图标。 */
    @Composable
    private fun O2DropletIcon() {
        Box(contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.size(26.dp)) {
                val w = size.width; val h = size.height
                val path = Path().apply {
                    moveTo(w * 0.5f, 0f)
                    cubicTo(w * 0.92f, h * 0.42f, w * 0.86f, h * 0.92f, w * 0.5f, h * 0.92f)
                    cubicTo(w * 0.14f, h * 0.92f, w * 0.08f, h * 0.42f, w * 0.5f, 0f)
                    close()
                }
                drawPath(path, Color(0xFF2F9BFF))
            }
            Text("O₂", fontSize = 8.sp, color = Skin.Ink, fontWeight = FontWeight.Bold)
        }
    }

    /**
     * 心电页（真实测量，三星 Health Sensor SDK ECG_ON_DEMAND，固定 30 秒）：
     * 测量中 = 实时波形 + 进度% + 电极提示；完成 = 波形 + 统计入本地库（仅手表保存）。
     */
    /**
     * 解析当前采样率：优先用 EcgMeasurer 从 `SEQUENCE` 序号跨度实测的值；
     * 还没测出来（测量刚开始）时用"点数 / 墙钟时长"兜底；都拿不到返回 0（调用方应等待）。
     *
     * 采样率必须准确：Pan-Tompkins 的 5~15Hz 频带、150ms 积分窗和 200ms 不应期全部依赖它。
     * 旧实现用 `slice.size / durSec` 反推，SDK 一丢包就偏小，物理含义整体失准。
     */
    private fun resolveSampleRate(sampleCount: Int, sinceMs: Long): Int {
        val measured = ecgMeasurer.state.value.sampleRateHz
        if (measured > 0) return measured
        if (sinceMs <= 0L || sampleCount < 50) return 0
        val sec = (System.currentTimeMillis() - sinceMs) / 1000.0
        if (sec < 0.5) return 0
        val est = (sampleCount / sec).toInt()
        return if (est in 50..500) est else 0
    }

    /**
     * 「这次心电要和刚记的血压配对」的提示条。
     *
     * ⚠⚠ **顺序与时限必须写出来** —— 配对窗口就是 ±5 分钟，而用户**不可能自己知道这个数**；
     *   超时就白测一次，而且他不会知道为什么白测。
     *
     * ⚠ 待机页与引导页**都要显示**：从录血压跳过来时先看到的是**待机页**
     *   （`stage` 初值 `idle` 走链尾的 `else`），只放引导页等于没说。
     */
    @Composable
    private fun CalibHintBox() {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(AccentOrange.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                stringResource(R.string.s_deeecc00),
                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
            )
            Text(
                stringResource(R.string.s_ab31de53),
                fontSize = 11.sp, color = Skin.Muted,
            )
            Text(
                stringResource(R.string.s_91ac369b),
                fontSize = 11.sp, color = Skin.Muted,
            )
        }
    }

    @Composable
    private fun EcgScreen(onBack: () -> Unit, calibHint: Boolean = false) {
        val ecgState by ecgMeasurer.state.collectAsState()
        // 页面阶段：idle 待机 / intro 引导 / touch 轻触开始（识别手指）/ measuring 采集 / result / failed
        var stage by remember { mutableStateOf("idle") }
        var sessionDone by remember { mutableStateOf<EcgWaveEntity?>(null) }
        var sessionFailed by remember { mutableStateOf(false) }
        var failText by remember { mutableStateOf<ResText>(ResText.EMPTY) }
        var sessionProgress by remember { mutableStateOf(0f) }
        var sessionWave by remember { mutableStateOf<List<Float>>(emptyList()) }
        var sessionAnalysis by remember { mutableStateOf<EcgAnalyzer.Result?>(null) }
        // #9 HRV：**每次现算**，不落库（表里只存逐拍间期这个输入）。见 HrvSessionEntity。
        var sessionHrv by remember { mutableStateOf<Hrv.Result?>(null) }
        // #14 脉搏到达时间（PAT）。与 sessionHrv 同一条纪律：**不适用时也要出声**
        // （写明是缺样 / 拍数不足 / 波动过大 / 幅度不足 / 两路不一致），不能留空。
        var sessionPat by remember { mutableStateOf<Ptt.Result?>(null) }
        var wristSide by remember { mutableStateOf(0) }  // 手腕位置 0=左 1=右
        var buttonSide by remember { mutableStateOf(1) } // 按钮方向 0=左 1=右（Watch7 表冠默认右）
        var fingerDetected by remember { mutableStateOf(false) } // 是否已检测到手指接触
        var touchSince by remember { mutableStateOf(0L) }      // 进入识别页的时刻
        var detectQuality by remember { mutableStateOf(0f) }   // 接触页实时信号质量 0~1
        var detectRatio by remember { mutableStateOf(0.0) }    // 接触页实时拍模板相干性（0~2）
        var detectCrest by remember { mutableStateOf(0.0) }    // 接触页实时 QRS 能量峰值因子（实际判据）
        var detectSharp by remember { mutableStateOf(0.0) }    // 接触页实时 R 峰锐度（窄脉冲形态判据）
        var measQuality by remember { mutableStateOf(1f) }     // 测量中抽查的信号质量 0~1
        // 「卡在接触页多久」与「是否已导出当前缓冲」—— 见 touch 页那个后置的导出口。
        var stuckSince by remember { mutableStateOf(0L) }
        val samples = remember { mutableListOf<Float>() }
        // 模块 #14：与 samples **等长**的 PPG 原始计数（缺值处为 0）。
        // ⚠ 必须与 samples 一起增删，长度永远相等 —— Ptt 靠下标对齐算时延。
        val ppgSamples = remember { mutableListOf<Int>() }
        var lastEcg by remember { mutableStateOf<EcgWaveEntity?>(null) }
        var lastRaw by remember { mutableStateOf<List<Float>?>(null) }
        // 与 lastRaw 同一会话的 PPG 原始计数（缺值处为哨兵）与实测采样率 ——
        // 「上传波形」要把**两路并排**导出去（PAT 的形态问题只能这样查清）。
        var lastPpgRaw by remember { mutableStateOf<List<Int>>(emptyList()) }
        var lastRate by remember { mutableStateOf(0) }
        // 本会话生产代码检出的 R 峰（供离线复算，避免离线另写一套 QRS 检测）
        var lastRPeaks by remember { mutableStateOf<List<Int>>(emptyList()) }

        LaunchedEffect(Unit) {
            lastEcg = runCatching { db.ecgWaveDao().latest() }.getOrNull()
        }
        LaunchedEffect(Unit) {
            ecgMeasurer.onSample = { mv, ppgRaw ->
                synchronized(samples) {
                    samples.add(mv)
                    // ⚠ 缺值必须**占位**（跳过不写会让后面所有点整体前移、两路错位），
                    //   而占位值用 `Ppg.MISSING` 而不是 0 —— **这一路是有符号的**
                    //   （实测值域 -42351 ~ 179709），0 与负数都是合法读数。
                    //   拿 0 当"缺样"会把整个舒张期判成没采到（2026-09-24 的故障，
                    //   详见 `Ppg.MISSING` 的注释）。
                    ppgSamples.add(ppgRaw ?: Ppg.MISSING)
                    // 上限按最坏采样率留足 40 秒；旧值 9000 在 500Hz 下只够 18 秒，
                    // 会把 30 秒测量的前半段丢掉（波形与分析都不完整）
                    if (samples.size > 24_000) {
                        samples.subList(0, 4_000).clear()
                        // 两路必须一起裁剪，否则长度不再相等（下标对齐就断了）
                        ppgSamples.subList(0, 4_000).clear()
                    }
                }
            }
        }
        // 离开页面时停止测量并断开传感器服务（灭灯）
        DisposableEffect(Unit) {
            onDispose { if (ecgMeasurer.state.value.measuring) ecgMeasurer.stopMeasurement() }
        }

        /** 从"轻触开始"页启动/重启传感器（先停其它测量，防占用冲突）。 */
        val startSensor: () -> Unit = {
            stopAllMeasurements()
            ecgMeasurer.startMeasurement()
        }

        // 测量会话守护（单实例循环，key=Unit）：
        // 手指接触判定（信号质量判据，识别到即自动开始）→ 预热 2 秒 → 30 秒采集
        // → 分析 → 写入本地库（波形 + 结论两条）→ 自动同步到手机。
        LaunchedEffect(Unit) {
            var recoverTries = 0
            while (true) {
                if (!ecgMeasurer.state.value.measuring) {
                    delay(100)
                    continue
                }
                runCatching {
                    // ── 阶段 1：手指接触判定 ──
                    // 判据是 EcgSignal.assess 的三特征时域形态：拍模板相干性 + R 峰锐度 + QRS 能量峰值因子，
                    // 不是幅值、也不是频带能量比。
                    // 为什么推翻频带能量比：它对白噪声给 0.286、对纯正弦给 0.366（都越过 0.25 阈值），
                    // 却把真心电配 0.3Hz 漂移压到 0.020 —— 原理上就不成立（详见 EcgSignal 类注释）。
                    // 实际起分离作用的是**峰值因子**（心电是稀疏瞬态串，伪迹包络平稳）：
                    // 真心电最低 16.7 vs 伪迹最高 9.6，阈值 12.0。
                    // 真机 30 秒录制实测峰值因子 26.7，远高于阈值。
                    synchronized(samples) {
                        samples.clear()
                        // ⚠ 两路必须一起清：只清一路会让长度不等，
                        //   而长度不等就是下标错位（PAT 会静默偏小）。
                        ppgSamples.clear()
                    }
                    touchSince = System.currentTimeMillis()
                    // 卡住计时与导出状态在**每次进入接触页**时归零（含停滞自愈重连后的重进）
                    stuckSince = touchSince
                    var passStreak = 0
                    fingerDetected = false
                    detectQuality = 0f
                    detectRatio = 0.0
                    detectCrest = 0.0
                    detectSharp = 0.0
                    recoverTries = 0
                    while (ecgMeasurer.state.value.measuring) {
                        val s = ecgMeasurer.state.value
                        if (s.error != null) break
                        // 停滞自愈：measuring 但没有新数据 —— tracker 没建起来或会话已废。
                        // 典型场景：上一次测量结束后再次点测量（旧实现因为服务已连接、
                        // SDK 不再回调 onConnected，导致 startTracking 永不执行）。
                        if (ecgMeasurer.isStalled() && recoverTries < 3) {
                            recoverTries++
                            ecgMeasurer.recoverFromStall()
                            synchronized(samples) {
                        samples.clear()
                        // ⚠ 两路必须一起清：只清一路会让长度不等，
                        //   而长度不等就是下标错位（PAT 会静默偏小）。
                        ppgSamples.clear()
                    }
                            touchSince = System.currentTimeMillis()
                            delay(600)
                            continue
                        }
                        val snap = synchronized(samples) { samples.toList() }
                        val fsNow = resolveSampleRate(snap.size, touchSince)
                        // 至少 5 秒数据：保证最小拍数，3 拍随机对齐可能偶然相干
                        if (fsNow > 0 && snap.size >= (fsNow * EcgSignal.MIN_EVAL_SEC).toInt()) {
                            // ⚠⚠ 评估**只看最近 [CONTACT_WINDOW_SEC] 秒**，不是"从按下开始按钮
                            //   以来的全量"：
                            //   ① 口径与测量中的抽查（那边是 `snap.takeLast(fsNow * 8)`）保持一致
                            //      —— 同一件事两处各写一套口径，迟早漂移（`EcgSignal` 类注释里
                            //      那条"两边各写一套必然漂移"就是这么踩出来的）；
                            //   ② `assess` 内部本就有 12 秒回看上限（`MAX_ANALYSIS_SEC`），
                            //      显式取 8 秒让判定只反映**当前**接触状态，收敛更快。
                            //
                            //   ⚠ 关于"窗口里残留未接触数据会不会拖低 crest"：**已经实测过**
                            //      （接触检测台架，扫了噪声强度 ×
                            //      未接触秒数）—— 悬空段的 `p20(integ)` 确实比心电等电位段高
                            //      50 倍以上（1.7e-4 vs 3.2e-6），但它是**高能量**段，而心电段里
                            //      有大量能量更低的等电位点，20 分位仍然落在心电段里；
                            //      所以只混几秒未接触数据**不会**把 crest 压到门限以下
                            //      （真机量级噪声实测 397 → 234，仍远高于 12）。
                            //      只有悬空噪声强到真心电的 ~6 倍（SNR 极低的极限工况）
                            //      才会把心电挤掉（beats 归零）—— 那是"本来就没接触好"，
                            //      不是窗口残留造成的。
                            //      ⇒ 窗口残留**不是**「手指放上去却测不了」的病根，别再往那个
                            //        方向改（曾经据此写过"整段作废"的逻辑，已回退：那会让
                            //        信号边缘的用户在「清空 → 又攒不满 5 秒」之间死循环）。
                            val evalN = (fsNow * CONTACT_WINDOW_SEC).toInt()
                                .coerceAtLeast((fsNow * EcgSignal.MIN_EVAL_SEC).toInt())
                            val tail = if (snap.size > evalN) {
                                snap.subList(snap.size - evalN, snap.size)
                            } else snap
                            val q = EcgSignal.assess(tail.toFloatArray(), fsNow.toDouble())
                            detectQuality = q.score
                            detectRatio = q.coherence
                            detectCrest = q.crest
                            detectSharp = q.sharpness
                            if (q.contact) {
                                passStreak++
                                if (passStreak >= 2) {
                                    fingerDetected = true
                                    break
                                }
                            } else {
                                passStreak = 0
                            }
                        }
                        delay(600)
                    }
                    var st = ecgMeasurer.state.value
                    if (!st.measuring) {
                        if (st.error != null) {
                            sessionFailed = true
                            failText = st.error
                            stage = "failed"
                        }
                        return@runCatching // 被用户取消
                    }
                    if (st.error != null) {
                        ecgMeasurer.stopMeasurement()
                        sessionFailed = true
                        failText = st.error
                        stage = "failed"
                        return@runCatching
                    }

                    // ── 阶段 2：预热（对齐原生"心率传感器预热中，请稍候…"）：
                    // 不重启会话——识别通过后同会话连续录制，重启反而可能废掉会话。
                    // 顺带清空 UI 缓冲，让接下来的 30 秒就是送去分析的那一段。
                    stage = "warmup"
                    synchronized(samples) {
                        samples.clear()
                        // ⚠ 两路必须一起清：只清一路会让长度不等，
                        //   而长度不等就是下标错位（PAT 会静默偏小）。
                        ppgSamples.clear()
                    }
                    val warmStart = System.currentTimeMillis()
                    while (ecgMeasurer.state.value.measuring) {
                        if (ecgMeasurer.state.value.error != null) break
                        if (System.currentTimeMillis() - warmStart >= 2_000) break
                        delay(100)
                    }
                    st = ecgMeasurer.state.value
                    if (!st.measuring || st.error != null) {
                        sessionFailed = true
                        failText = st.error ?: ResText.of(R.string.s_5a2ad901)
                        stage = "failed"
                        return@runCatching
                    }

                    // ── 阶段 3：30 秒采集 ──
                    synchronized(samples) {
                        samples.clear()
                        // ⚠ 两路必须一起清：只清一路会让长度不等，
                        //   而长度不等就是下标错位（PAT 会静默偏小）。
                        ppgSamples.clear()
                    }
                    sessionProgress = 0f
                    sessionWave = emptyList()
                    sessionDone = null
                    sessionFailed = false
                    failText = ResText.EMPTY
                    sessionAnalysis = null
                    sessionHrv = null
                    sessionPat = null
                    stage = "measuring"
                    val start = System.currentTimeMillis()
                    var leadSince = -1L
                    var leadOffHit = false
                    var lastQualityCheck = 0L
                    while (ecgMeasurer.state.value.measuring) {
                        val elapsed = System.currentTimeMillis() - start
                        if (elapsed >= 30_000) break
                        val s = ecgMeasurer.state.value
                        if (s.leadOff) {
                            if (leadSince < 0) leadSince = System.currentTimeMillis()
                            if (System.currentTimeMillis() - leadSince >= 1_000) {
                                leadOffHit = true
                                break
                            }
                        } else {
                            leadSince = -1
                        }
                        if (s.error != null) break
                        // 每 2 秒抽查一次信号质量：手指中途滑开时立刻提示，
                        // 不然用户会白等 30 秒拿到一段无法判定的噪声。
                        if (elapsed - lastQualityCheck >= 2_000 && elapsed >= 4_000) {
                            lastQualityCheck = elapsed
                            val snap = synchronized(samples) { samples.toList() }
                            val fsNow = resolveSampleRate(snap.size, start)
                            if (fsNow > 0 && snap.size >= fsNow * 5) {
                                val tail = snap.takeLast(fsNow * 8)
                                measQuality = EcgSignal
                                    .assess(tail.toFloatArray(), fsNow.toDouble()).score
                            }
                        }
                        sessionProgress = (elapsed / 300f).coerceIn(0f, 100f)
                        delay(100)
                    }
                    if (leadOffHit) {
                        // SDK 明确报电极离体：停止 → 回到轻触开始页重新识别手指
                        if (ecgMeasurer.state.value.measuring) ecgMeasurer.stopMeasurement()
                        synchronized(samples) {
                        samples.clear()
                        // ⚠ 两路必须一起清：只清一路会让长度不等，
                        //   而长度不等就是下标错位（PAT 会静默偏小）。
                        ppgSamples.clear()
                    }
                        startSensor()
                        stage = "touch"
                        return@runCatching
                    }
                    delay(300) // 缓冲收尾采样（stopMeasurement 内部会先 flush）
                    if (ecgMeasurer.state.value.measuring) ecgMeasurer.stopMeasurement()
                    lastRaw = synchronized(samples) { samples.toList() } // 供"上传波形"调试
                    lastPpgRaw = synchronized(samples) { ppgSamples.toList() }
                    if (stage == "idle") return@runCatching // 用户手动停止：不保存
                    st = ecgMeasurer.state.value
                    if (st.error != null) {
                        sessionFailed = true
                        failText = st.error
                        stage = "failed"
                        // ── 测量结束的震动提示（2026-10-07）──
                        // 这是"30 秒等完了、但拿不到可用波形"，两短震。
                        // ⚠ 本页只有**4 个终态**该震（这一处 + 结尾的 result/failed/onFailure）；
                        //   前面"轻触识别手指失败"与 lead-off 重来**不震** —— 那时用户正看着
                        //   屏幕按着表冠，而且重来会反复震（详见 Buzzer 的说明）。
                        Buzzer.fail()
                        return@runCatching
                    }
                    val slice = synchronized(samples) { samples.toList() }
                    val elapsedSec = ((System.currentTimeMillis() - start) / 1000f).coerceAtLeast(1f)
                    // 采样率优先取 SEQUENCE 实测值（内部已用序号跨度消除丢包导致的低估）
                    val fsReal = resolveSampleRate(slice.size, start)
                    // 供"上传波形"把实测采样率一并写进 CSV 文件头（离线复算要用它）
                    lastRate = fsReal
                    if (slice.size >= 50 && fsReal > 0) {
                        val analysis = EcgAnalyzer.analyze(slice, fsReal.coerceIn(50, 500))
                        lastRPeaks = analysis.rPeaks

                        // ── #14 PAT（脉搏到达时间）：**必须在会话内**用全速率数据算 ──
                        //
                        // 事后重算不出来：下面存进 `ecg_waves` 的是降采样到约 100Hz 的展示波形
                        // （10ms 分辨率），而 PAT 的有效效应只有 10~20ms —— 同量级，重算无意义。
                        // 所以这里算一次、把结果落库（理由与 HRV 存 RR 间期完全一样）。
                        //
                        // 数据来源：心电会话**自带**的那一路 PPG（`EcgSet.PPG_GREEN`，
                        // 与 ECG_MV 在同一个包里，所以同采样率、同时间轴、天然对齐）。
                        // 不需要两个 tracker 并跑，也就绕开了「on-demand 期间连续型可能无效」那条警告。
                        val rawPpg = synchronized(samples) { ppgSamples.toList() }
                        val ppgAligned = runCatching { Ppg.normalizeAligned(rawPpg) }.getOrNull()
                        // 去掉 NaN 占位的紧凑序列：**只用于估心率**（自相关碰到 NaN 会全废），
                        // 不能拿它算 PAT —— 那会把下标对齐丢掉。
                        val ppgClean = ppgAligned?.norm?.filter { !it.isNaN() }
                        val ppgHr = if (ppgClean != null && ppgAligned != null) {
                            runCatching {
                                // ⚠ 采样率要按**完整度**折一下：缺样点被剔掉之后，这张紧凑表
                                //   的"每点时间"不再等于 1/fsReal（幸存样本的时刻没变，
                                //   所以点少了、等效采样率按比例降低）。直接传 fsReal 会让
                                //   周期（以点数计）偏大 → 估出的心率**偏低**，且是静默的。
                                //   而 ppgHr 正是唯一能抓住"两路时间轴没对齐"的交叉校验。
                                val hrRate = (fsReal * ppgAligned.quality).roundToInt()
                                    .coerceAtLeast(1)
                                // ⚠⚠ 第三个数（quality）这里**刻意传 1f**，不是偷懒：
                                //   `Ppg.MIN_QUALITY_FOR_HR` 那道门是给"缺样会让时间换算整体偏移"
                                //   定的，而这里采样率已经按完整度折算过（hrRate），
                                //   紧凑序列本身就是一条**均匀**序列。
                                //   这一路真机上只有约 20% 的包带脉搏波（每 5 包 1 值），
                                //   那是**常态**而不是数据缺陷 —— 照搬那个门会让这条唯一的
                                //   交叉校验永远不执行，等于把防"假心率"的网拆掉。
                                Ppg.estimatePulseRate(ppgClean, hrRate, 1f)
                            }.getOrNull()
                        } else {
                            null
                        }
                        val patResult: Ptt.Result = runCatching {
                            if (ppgAligned == null) {
                                Ptt.Result.NotApplicable(
                                    this@MainActivity.getString(R.string.s_8035af21)
                                )
                            } else {
                                Ptt.analyze(
                                    ppg = ppgAligned,
                                    rPeaks = analysis.rPeaks,
                                    sampleRateHz = fsReal.toDouble(),
                                    ecgHr = analysis.heartRate,
                                    ppgHr = ppgHr,
                                )
                            }
                        }.getOrElse {
                            Ptt.Result.NotApplicable(
                                this@MainActivity.getString(R.string.s_fe02b6d2, it.javaClass.simpleName, it.message)
                            )
                        }

                        // 一行诊断：这一路脉搏波"长什么样"。只在算不出 PAT 时随 patNote
                        // 一起落库 —— 排障时最需要的正是它。2026-09-24 晚正是靠它一行字
                        // 定案：`p5=-1 中位=-1 p95=80575 · ≤0 的 12176 点` ⇒ **80% 的包
                        // 根本没有脉搏波值**（SDK 给 -1），有值的 20% ≈ 每 5 包 1 值。
                        // 所以这里把"间隔结构"一并打出来：它才是判断形态的关键。
                        val ppgDiag = runCatching {
                            val n = rawPpg.size
                            // ⚠ 口径必须与 `Ppg.isReading` 一致（不是 `> 0`！读数**有符号**，
                            //   真机实测有一整场读数全为负）。四处各写一遍迟早漂，所以引用它。
                            val vals = rawPpg.filter { Ppg.isReading(it) }.sorted()
                            val negOne = rawPpg.count { it == -1 }
                            val zero = rawPpg.count { it == 0 }
                            val sb = StringBuilder()
                            sb.append(this@MainActivity.getString(R.string.s_835392b1, n, vals.size))
                            sb.append("（${(vals.size * 100.0 / n.coerceAtLeast(1)).roundToInt()}%）")
                            sb.append(this@MainActivity.getString(R.string.s_566de4e7, negOne, zero))
                            if (vals.isNotEmpty()) {
                                fun q(p: Int) =
                                    vals[((vals.size - 1) * p / 100).coerceIn(0, vals.size - 1)]
                                sb.append(this@MainActivity.getString(R.string.s_2105b160, q(5), q(50), q(95)))
                                // 有值点之间的间隔（以**包**为单位）：等间隔就说明是"每 N 包 1 值"，
                                // 那意味着这一路的真实采样率比心电低 N 倍 —— 决定我们怎么算波足。
                                var prev = -1
                                var maxGap = 0
                                val gaps = ArrayList<Int>(vals.size)
                                for (i in 0 until n) {
                                    if (Ppg.isReading(rawPpg[i])) {
                                        if (prev >= 0) {
                                            val g = i - prev
                                            gaps.add(g)
                                            if (g > maxGap) maxGap = g
                                        }
                                        prev = i
                                    }
                                }
                                if (gaps.isNotEmpty()) {
                                    val med = gaps.sorted()[gaps.size / 2]
                                    val hz = if (med > 0) fsReal / med.toDouble() else 0.0
                                    sb.append(this@MainActivity.getString(R.string.s_523fc407, med, maxGap))
                                    sb.append(
                                        "（≈${String.format(java.util.Locale.US, "%.0f", hz)} Hz）"
                                    )
                                }
                            }
                            val sc = ppgAligned?.scale
                            sb.append(this@MainActivity.getString(R.string.s_499ff082, if (sc != null) sc.roundToInt() else -1))
                            sb.append(" · ${fsReal} Hz")
                            sb.toString()
                        }.getOrElse { this@MainActivity.getString(R.string.s_0165e8b6, it.javaClass.simpleName) }

                        // 存档与同步用的波形：先 0.5~40Hz 带通（去基线漂移与工频干扰、保留 QRS 形态），
                        // 再降采样到约 100Hz —— 30 秒约 3000 点，够画出可辨认的 QRS。
                        // 旧实现存的是 240 点的原始 ECG_MV：既带 200mV 电极偏置（画出来只有噪声），
                        // 又只有 8Hz 采样（QRS 不到 1 个点，形态完全丢失）。
                        val display = EcgSignal.bandpass(
                            slice.toFloatArray(), fsReal.toDouble(), 0.5, 40.0,
                        )
                        val step = (fsReal / 100.0).roundToInt().coerceAtLeast(1)
                        val wave = display.filterIndexed { i, _ -> i % step == 0 }

                        val now = System.currentTimeMillis()
                        val entity = EcgWaveEntity(
                            measuredAt = now,
                            durationMs = (elapsedSec * 1000).toLong(),
                            sampleCount = slice.size,
                            minMv = wave.min(), maxMv = wave.max(), avgMv = wave.average().toFloat(),
                            waveJson = wave.joinToString(",") { String.format(Locale.US, "%.2f", it) },
                            source = "watch_ecg",
                            classification = analysis.classification,
                            analyzedHr = analysis.heartRate,
                            sampleRate = fsReal,
                            regular = analysis.regular?.let { if (it) 1 else 0 },
                            beatCount = analysis.beatCount,
                            // 该列原先存"QRS 频带能量占比"，已被证伪；现改存**拍模板相干性**（0~2）。
                            // 未改列名是为了避免再升一次数据库版本。
                            bandRatio = analysis.coherence,
                        )
                        runCatching { db.ecgWaveDao().insert(entity) }
                        // 关键修复：同时写 ecg_records —— 这是**唯一参与同步**的心电表。
                        // 旧实现只写 ecg_waves（当时没有 synced 字段），所以手表量完手机上永远看不到。
                        // 两条用同一时间戳，同步时手表据此把波形挂到结论上。
                        runCatching {
                            db.ecgDao().insert(
                                EcgRecordEntity(
                                    classification = entity.classification ?: this@MainActivity.getString(R.string.s_c34c0523),
                                    heartRate = analysis.heartRate,
                                    note = EcgNote.canonicalDetail(analysis.detail),
                                    recordedAt = now,
                                    source = "watch_ecg",
                                    // #14：PAT 落**这一张表**（同步表）。
                                    // `ecg_waves` 没有 synced 列、不参与同步 ——
                                    // 落波形表里手机端永远看不到，而"看趋势"正是手机端该干的事。
                                    // `NotApplicable` 时三列都留 null：界面按 sessionPat 显示
                                    // 原因，库里**不写 0**（0 会被读成一次有效测量）。
                                    patMs = (patResult as? Ptt.Result.Ok)?.medianMs,
                                    patSpreadMs = (patResult as? Ptt.Result.Ok)?.spreadMs,
                                    patSeriesMs = (patResult as? Ptt.Result.Ok)?.seriesMs
                                        ?.joinToString(","),
                                    // 口径版本随记录走：手机端的"自身基线"只收同口径的值。
                                    // 不写它，基线就会把两套参考点算出来的毫秒数混成一个中位数。
                                    patAlgo = Ptt.ALGO_VERSION,
                                    // 没算出来时把**原因原文 + 一行诊断**存下来：只在手表当次
                                    // 结果页显示的话，用户测十次都失败、手机上还是一片空白，
                                    // 而"为什么失败"正是这时候唯一有用的信息。
                                    patNote = (patResult as? Ptt.Result.NotApplicable)?.let {
                                        // 措辞给人看、数字给排查看（`detail` 是 v63 加的：
                                        // 前两次返工都是"只有措辞、没有数字"，各耗一轮）。
                                        buildString {
                                            append(it.reason)
                                            it.detail?.let { d -> append(this@MainActivity.getString(R.string.s_79e02c58)).append(EcgNote.detail(d)) }
                                            append(this@MainActivity.getString(R.string.s_83f05d84)).append(ppgDiag)
                                        }
                                    },
                                )
                            )
                        }
                        sessionDone = entity
                        sessionWave = wave
                        sessionAnalysis = analysis
                        lastEcg = entity
                        // ── #9 HRV：只存**输入**（逐拍间期 + 测量条件），指标现算 ──
                        // 活动档位取 AccelSampler 的当前值：档位每秒刷新、覆盖最近几秒的样本，
                        // 所以"心电刚结束这一刻"的档位最能代表刚才那 30 秒人是静是动。
                        // 拿不到时为 UNKNOWN —— 它**不等于静息**，界面会标注"活动状态未判定"。
                        val actLevel = accelSampler.state.value.level
                        runCatching {
                            db.hrvDao().insert(
                                HrvSessionEntity(
                                    measuredAt = now,
                                    durationMs = entity.durationMs,
                                    // 存**未过滤**的相邻间期（见 EcgAnalyzer.Result.rrAllMs）：
                                    // 伪迹修正要用"约 2 倍长"这条线索，预先过滤就把它销毁了。
                                    rrCount = analysis.rrAllMs.size,
                                    rrMs = Hrv.encodeRr(analysis.rrAllMs),
                                    refHr = analysis.heartRate,
                                    ecgRegular = analysis.regular?.let { if (it) 1 else 0 },
                                    activityLevel = actLevel,
                                    source = HrvSource.WATCH_ECG,
                                )
                            )
                        }
                        sessionHrv = Hrv.analyze(
                            rawRrMs = analysis.rrAllMs,
                            source = HrvSource.WATCH_ECG,
                            activityLevel = actLevel,
                            ecgRegular = analysis.regular,
                        )
                        sessionPat = patResult

                        // ── #14 的旁证：把这一路 PPG 也存进 `ppg_waves` ──
                        //
                        // 它是**诊断与显示**用的，不是 PAT 的输入（PAT 上面已经算完了）：
                        // · 手机端可据此画"心电 + 脉搏"两路对齐图，直观说明时延是怎么来的；
                        // · 幅度 / 质量 / 推算心率 vs 参考心率 这三项留下"这次 PPG 通道到底
                        //   可不可用"的证据 —— 以后有人问"为什么我的表算不出 PAT"，
                        //   看这一行就知道是通道没给、还是信号太弱。
                        //
                        // ⚠ `PpgSource.WATCH_ECG` 与搭血氧车的 `WATCH` **刻意分开**：
                        //   两者采样率差 20 倍、通道数不同，混成一个 source 之后
                        //   "按来源过滤"的查询会把两批数据当成同一批 —— 而且不报错。
                        runCatching {
                            val stats = Ppg.analyze(
                                rawGreen = rawPpg,
                                durationMs = (elapsedSec * 1000).toLong(),
                                sampleRateHz = fsReal,
                                rateFromTimestamps = false,
                                refHr = analysis.heartRate,
                            )
                            if (stats != null && ppgClean != null && ppgClean.isNotEmpty()) {
                                // 抽稀到约 100Hz（与心电展示波形同一口径）：脉搏波只有几 Hz，
                                // 100Hz 足够画出形态；500Hz 全存会让这一行涨到 120KB，
                                // 而它对 PAT 没有增量（PAT 已在全速率上算完）。
                                val step = (fsReal / 100.0).roundToInt().coerceAtLeast(1)
                                val thin = ppgClean.filterIndexed { i, _ -> i % step == 0 }
                                    .take(Ppg.MAX_STORED_SAMPLES)
                                db.ppgWaveDao().insert(
                                    PpgWaveEntity(
                                        measuredAt = now,
                                        durationMs = stats.durationMs,
                                        sampleCount = stats.sampleCount,
                                        sampleRateHz = stats.sampleRateHz,
                                        channels = Ppg.encodeChannels(listOf(Ppg.Channel.GREEN)),
                                        dcGreen = stats.dc,
                                        acGreenP2P = stats.acP2P,
                                        quality = stats.quality,
                                        estimatedHr = stats.estimatedHr,
                                        refHr = stats.refHr,
                                        statusSeen = null,
                                        greenWave = Ppg.encodeWave(thin),
                                        redWave = null,
                                        irWave = null,
                                        source = PpgSource.WATCH_ECG,
                                    )
                                )
                            }
                        }
                        stage = "result"
                        // ── 测量结束的震动提示（2026-10-07）：拿到结果，一次短震 ──
                        Buzzer.ok()
                        bt.syncAuto()
                    } else {
                        sessionFailed = true
                        failText = st.error ?: if (slice.size < 50) {
                            ResText.of(R.string.s_b86ff73d)
                        } else {
                            ResText.of(R.string.s_fa1587a4)
                        }
                        stage = "failed"
                        Buzzer.fail()
                    }
                }.onFailure { e ->
                    sessionFailed = true
                    failText = ResText.of(R.string.s_2d802be5, e.javaClass.simpleName, e.message)
                    stage = "failed"
                    Buzzer.fail()
                }
            }
        }

        // ⚠ 心电波形 120ms 定时重绘已移除：波形位置由真实样本序号决定，
        //   去掉后只在数据到达时重绘（EcgWaveCanvas 里 phase 只当重绘驱动）。
        val phase = 0f

        // ── 测量中：全屏密集波形（占满表盘）──
        if (stage == "measuring" && ecgState.measuring) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "${sessionProgress.toInt()}%",
                    fontSize = 13.sp, color = Color(0xFF4DD0E1),
                )
                Spacer(Modifier.height(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    EcgLiveWave(
                        samples = synchronized(samples) { samples.toList() },
                        tick = phase,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        ecgState.leadOff -> stringResource(R.string.s_ab9f68d7)
                        measQuality < 0.5f -> stringResource(R.string.s_c4f1beb3)
                        else -> stringResource(R.string.s_ccd5b64a)
                    },
                    fontSize = 12.sp,
                    color = if (measQuality < 0.5f && !ecgState.leadOff) AccentOrange else Skin.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Button(
                    onClick = {
                        stage = "idle"
                        ecgMeasurer.stopMeasurement()
                    },
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                    modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                ) {
                    Text(stringResource(R.string.s_00298a76), fontSize = 14.sp)
                }
            }
            return
        }

        // ── 轻触开始页：固定不滚动（保证箭头位置稳定），无返回/取消按钮，退出用右滑手势 ──
        if (stage == "touch") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // ⚠⚠ 卡住时把**装饰性图示换成可操作的按钮**，而不是在末尾追加：
                //   touch 页总高本来就超出 240dp 圆屏（`TouchStartArt` 的注释里记着上一次
                //   为此把心形 88→56dp），**末尾的控件一律会被圆形遮罩切掉** ——
                //   2026-10-03 用户截图证实：连底部那行提示都只露出一半。
                //   标题与三项实测数值都保留（它们是分诊依据），只让装饰让位。
                val stuckTooLong = stuckSince > 0L &&
                    System.currentTimeMillis() - stuckSince > CONTACT_STUCK_MS
                if (stuckTooLong) {
                    // 卡住超过 30 秒：装饰让位给「该怎么做」+ 波形导出口。
                    // ⚠ 为什么是这两句：真机复算（接触检测台架）显示
                    //   失败那份信号里只有高阻抗拾取的工频、心电带里没有 QRS 能量
                    //   ⇒ 唯一有效的动作就是"让手指与电极形成回路"：指腹按实 + 表底贴紧。
                    //   ⚠ 原先引导页写的是"将指尖**轻轻**放在…"——按这次的数据看这条引导是错的，
                    //     已一并改成"指腹平贴、轻按但贴实"。
                    Text(
                        stringResource(R.string.s_a9f3f76d),
                        fontSize = 10.sp,
                        color = AccentOrange,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.92f),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        stringResource(R.string.s_bf55f8a4),
                        fontSize = 12.sp,
                        color = Color(0xFF4DD0E1),
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(PillGray)
                            .clickable {
                                val raw = synchronized(samples) { samples.toList() }
                                // ⚠ 两路必须等长地一起给（PAT 靠下标对齐），
                                //   缺值处已是 `Ppg.MISSING` 占位，不要再过滤。
                                val pg = synchronized(samples) { ppgSamples.toList() }
                                if (raw.isNotEmpty()) {
                                    bt.sendEcgWave(raw, pg, resolveSampleRate(raw.size, touchSince))
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                    // ⚠ 导出结果**不在这里显示**（2026-10-05 改）：
                    //   这一处原先是一行 9sp 小字，位于圆屏底部 —— 本项目已实测
                    //   「touch 页末尾的控件会被圆形遮罩切掉」（2026-10-03 用户截图连
                    //   底部提示都只露一半）⇒ 写在这里等于没写。
                    //   结果改由 `EcgExportDialog`（不受页面位置影响）给出。
                    Spacer(Modifier.height(4.dp))
                } else {
                    TouchStartArt(pointRight = buttonSide == 1)
                }
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.s_860f2b95), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
                Spacer(Modifier.height(3.dp))
                Text(
                    when {
                        fingerDetected -> stringResource(R.string.s_e7a5c9cf)
                        detectQuality >= 0.5f -> stringResource(R.string.s_5ca005f3)
                        ecgState.received > 0 -> stringResource(R.string.s_02b9f009)
                        else -> stringResource(R.string.s_770adced)
                    },
                    fontSize = 11.sp,
                    color = if (detectQuality >= 0.5f || fingerDetected) Color(0xFF5AD469) else AccentOrange,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(3.dp))
                // 质量条：信号质量分的直观呈现。
                // 判据是叠拍相干性 + R 峰锐度 + QRS 能量峰值因子（时域形态），不是幅值、也不是频带能量比 ——
                // 悬空电极的幅值噪声比真实心电还大；频带能量比对白噪声也能给高分（详见 EcgSignal 类注释）。
                // 分数取**三项里最差的一项**（见 Quality.score），所以「≥50%」与「真的过闸门」
                // 完全等价：手指没放上去时它不会再跳到 100%。
                Box(
                    Modifier.fillMaxWidth(0.7f).height(6.dp)
                        .background(Skin.Hairline, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(detectQuality.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(
                                if (detectQuality >= 0.5f) Color(0xFF5AD469) else AccentOrange,
                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp),
                            ),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    stringResource(R.string.s_1ada4437, (detectQuality * 100).toInt()) +
                        (if (ecgState.sampleRateHz > 0) " · ${ecgState.sampleRateHz}Hz" else ""),
                    fontSize = 10.sp, color = Color(0xFFB8BEC5),
                )
                Spacer(Modifier.height(2.dp))
                // 三项判据逐一亮出「实测值/阈值」，但**排成三列**而不是拼成一行。
                // 原来写成 "相干 x/y · 锐度 x/y · 峰值 x/y" 一行，在 240dp 圆屏上比可用宽度还长，
                // 两侧字符被圆形遮罩裁掉（真机截图里"相干"和"/12"都缺了一半）。
                // 三列各自占位后互不挤压，也让"哪一项在拖后腿"一眼可见。
                // 为什么必须逐项显示：只给一个总百分比的话，v51 真机出现过相干性 1.65 达标、
                // 峰值因子 2.6 远低于 12，却被相干性单独把总分拉到 100%。
                Row(
                    modifier = Modifier.fillMaxWidth(0.96f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    DetectMetric(stringResource(R.string.s_003d3f5a), "%.2f/%.2f".format(detectRatio, EcgSignal.COHERENCE_MIN))
                    DetectMetric(stringResource(R.string.s_004ca5d6), "%.2f/%.2f".format(detectSharp, EcgSignal.SHARPNESS_MIN))
                    DetectMetric(stringResource(R.string.s_002fdf0c), "%.1f/%.0f".format(detectCrest, EcgSignal.CREST_MIN))
                }
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(34.dp)) {
                    EcgLiveWave(
                        samples = synchronized(samples) { samples.toList() },
                        tick = phase,
                    )
                }
                // 原来这里有两行说明（"↑ 实时信号（±1.5mV 刻度…）" + "电极是右侧上面那颗按钮（原生指引的位置）…"），
                // 第二行会折成两行、挤到圆屏外并和上一行重叠。合并成一行短提示。
                Text(
                    stringResource(R.string.s_f9189045),
                    fontSize = 9.sp, color = Color(0xFF8A8F98),
                )
                if (ecgState.dropped > 0) {
                    Text(stringResource(R.string.s_c3d48c4c, ecgState.dropped), fontSize = 9.sp, color = Color(0xFF8A8F98))
                }
            }
            return
        }

        // ── 预热页：手指已就位，重启会话后等待传感器稳定（对齐原生"心率传感器预热中"）──
        if (stage == "warmup") {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                TouchStartArt(pointRight = buttonSide == 1, showArrow = false)
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.s_5422e17c), fontSize = 15.sp, color = Skin.Ink)
            }
            return
        }

        val scroll = rememberScrollState()

        Box(Modifier.fillMaxSize()) {
            ScrollBarRight(scroll)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll)
                    .padding(horizontal = 26.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(6.dp))

                // ⚠⚠ 这里**只能**判 `intro`，不能把 `idle` 并进来（2026-10-02 回退过一次）：
                //   `idle` 走的是链尾的 `else` —— 那是**待机页**（上次心电信息 + 测量按钮）。
                //   当时只 grep 了 `if (stage == ` 就断定「idle 没有渲染分支 = 空页」，
                //   **漏看了 else**，于是把 idle 并进本分支 ——
                //   结果页的「确定」（`stage = "idle"`）跟着变成回引导页，
                //   用户看完成绩点确定**又看到引导页**，以为「没退出／是不是没测成功」。
                //   ⇒ 判「某个状态有没有渲染」时**必须找全 else / when-else**，别只 grep 显式比较。
                if (stage == "intro") {
                    // ── 引导页：工作原理 + 免责声明 + 手腕位置/按钮方向 ──
                    if (calibHint) {
                        CalibHintBox()
                        Spacer(Modifier.height(10.dp))
                    }
                    Text(stringResource(R.string.s_a9d3d746), fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
                    Spacer(Modifier.height(8.dp))
                    EcgHowArt()
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(stringResource(R.string.s_ca74f117), fontSize = 12.sp, color = Skin.Ink)
                        Text(stringResource(R.string.s_77987392), fontSize = 12.sp, color = Skin.Ink)
                        Text(stringResource(R.string.s_8e1af6d4), fontSize = 12.sp, color = Skin.Ink)
                        Text(stringResource(R.string.s_ab438a1f), fontSize = 12.sp, color = Skin.Ink)
                        Text(stringResource(R.string.s_a7711760), fontSize = 12.sp, color = Skin.Ink)
                    }
                    Spacer(Modifier.height(10.dp))
                    SectionLineBox()
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.s_119b6eb8), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_1c241f15),
                        fontSize = 12.sp, color = Skin.Muted,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    SectionLineBox()
                    Spacer(Modifier.height(8.dp))
                    OptionRow(
                        stringResource(R.string.s_4dc8f13b),
                        listOf(stringResource(R.string.s_00005de6), stringResource(R.string.s_000053f3)),
                        wristSide,
                    ) { wristSide = it }
                    Spacer(Modifier.height(6.dp))
                    OptionRow(
                        stringResource(R.string.s_6c5ad14d),
                        listOf(stringResource(R.string.s_00005de6), stringResource(R.string.s_000053f3)),
                        buttonSide,
                    ) { buttonSide = it }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            startSensor()
                            stage = "touch"
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_00416f22), fontSize = 15.sp)
                    }
                } else if (stage == "result" && sessionDone != null) {
                    // 结果页：四分类结论 + 心率 + 波形 + 依据 + 质量指标
                    val e = sessionDone!!
                    // ⚠⚠ 拿**落库值**（中文常量）比，不能用显示名。
                    //    这里原本是四个中文字面量，被搬进 strings.xml 后变成
                    //    `stringResource(...)` —— 而 `e.classification` 仍是中文，
                    //    英文界面下永远落 `else`（颜色全灰），**不报错**。
                    val clsColor = when (e.classification) {
                        EcgNote.SINUS -> Color(0xFF5AD469)
                        EcgNote.AFIB -> Color(0xFFFF6B6B)
                        EcgNote.HR_HIGH, EcgNote.HR_LOW -> AccentOrange
                        else -> Color(0xFF9AA0A6)
                    }
                    Text(
                        EcgNote.classLabel(e.classification) ?: stringResource(R.string.s_c34c0523),
                        fontSize = 24.sp, fontWeight = FontWeight.Bold, color = clsColor,
                    )
                    Spacer(Modifier.height(2.dp))
                    val infoLine = buildString {
                        val hr = e.analyzedHr
                        if (hr != null) append(stringResource(R.string.s_884f45ea, hr))
                        val reg = sessionAnalysis?.regular
                        if (reg != null) {
                            if (isNotEmpty()) append(" · ")
                            append(if (reg) stringResource(R.string.s_960d6545) else stringResource(R.string.s_96016b90))
                        }
                        if (isEmpty()) append(stringResource(R.string.s_affd084a, e.sampleCount))
                    }
                    Text(infoLine, fontSize = 15.sp, color = Skin.Ink)
                    Spacer(Modifier.height(6.dp))
                    EcgWaveCanvas(samples = sessionWave, phase = phase, windowSamples = Int.MAX_VALUE)
                    // ⚠ 模板里不许嵌 ASCII 引号（提取器会抓到半截串），先算好再插
                    val ampMv = "%.2f".format(e.maxMv - e.minMv)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        EcgNote.detail(sessionAnalysis?.detail)
                            ?: stringResource(R.string.s_1e93822e, ampMv, e.sampleCount),
                        fontSize = 12.sp, color = Skin.Muted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(3.dp))
                    // #9 HRV：一行摘要。**不适用时也必须出声**（写明原因而不是留空），
                    // 否则用户只会看到一条"心电正常"却不知道还有一项没给。
                    val hrvRes = sessionHrv
                    if (hrvRes is Hrv.Result.Ok) {
                        Text(
                            "HRV·${Hrv.tierShort(hrvRes.tier)}  ${Hrv.summaryLine(hrvRes)}",
                            fontSize = 11.sp, color = HrvColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (hrvRes is Hrv.Result.NotApplicable) {
                        Text(
                            stringResource(R.string.s_bc4f86fa, Hrv.shortReason(hrvRes.reason)),
                            fontSize = 9.sp, color = Color(0xFF8A8F98),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    // #14 PAT（脉搏到达时间）。⚠ 单位只能是 ms，**任何地方不出现 mmHg** ——
                    // 理由见 `Ptt.NOT_BP`：PAT = PEP + PTT，而 PEP 在手表上看不见、
                    // 还随情绪/体位变化 10~20ms 且与血压无关。换算不成血压。
                    val patRes = sessionPat
                    if (patRes is Ptt.Result.Ok) {
                        Text(
                            stringResource(R.string.s_22e54c87, patRes.medianMs, patRes.spreadMs),
                            fontSize = 11.sp, color = PatColor,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            stringResource(R.string.s_3ca2704d, patRes.beats),
                            fontSize = 9.sp, color = Color(0xFF8A8F98),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else if (patRes is Ptt.Result.NotApplicable) {
                        // 与 HRV 同一条纪律：**不适用也必须出声**，且说清是哪一类原因
                        // （缺样 / 拍数不足 / 波动过大 / 幅度不足 / 两路不一致），
                        // 而不是笼统一句"无法测量" —— 用户据此才知道要不要重测、怎么调整。
                        Text(
                            stringResource(R.string.s_7a78306d, patRes.reason),
                            fontSize = 9.sp, color = Color(0xFF8A8F98),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    // 质量指标：让"为什么判成无法判定"可自查，也便于现场排查接触问题
                    Text(
                        buildString {
                            append(stringResource(R.string.s_f57e7036, e.sampleCount, e.sampleRate ?: 0))
                            sessionAnalysis?.let {
                                // 注意写"检出 N 拍"而不是"心跳 N"：这是**拍数**不是心率，
                                // 结果页标题里已经有"心率 NN 次/分"，两个数字并列容易被读成互相矛盾。
                                append(stringResource(R.string.s_d21ce894, it.beatCount))
                                append(stringResource(R.string.s_d64c865c).format(it.amplitudeMv))
                                append(stringResource(R.string.s_2d2dbdf5).format(it.coherence))
                                append(stringResource(R.string.s_74f94c1a).format(it.sharpness))
                                append(stringResource(R.string.s_46764c81).format(it.crest))
                            }
                        },
                        fontSize = 9.sp, color = Color(0xFF8A8F98),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_97ea17e6),
                        fontSize = 10.sp, color = Color(0xFF8A8F98),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            // ⚠⚠ **结果页的「确定」= 退出回首页**（2026-10-02 用户实付）。
                            //   原来设成 `stage = "idle"`（回待机），而本页
                            //   **没有返回按钮**（链尾那句 `if (stage != "result")` 把结果页排除了）——
                            //   于是用户看完成绩点「确定」，落到待机页，但**感受是"没退出"**。
                            //   本页唯一的出口应该是「我看完了，结束」。
                            //   ⚠ 想再测一次的用户走首页的心电入口（一次点击），
                            //     不必为此在圆屏底部再挤第三个同级按钮（那正是当初去掉返回的原因）。
                            sessionDone = null
                            onBack()
                        },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp)
                    }
                    lastRaw?.let { raw ->
                        Spacer(Modifier.height(8.dp))
                        // 与上方"确定"完全同款：同样的 Button / PillGray / fillMaxWidth(0.5f)。
                        // 原本用的是 CompactButton + 深灰底，尺寸与圆角都跟"确定"对不上，在圆屏上看着像两套控件。
                        Button(
                            onClick = { bt.sendEcgWave(raw, lastPpgRaw, lastRate, lastRPeaks) },
                            colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                            modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                        ) {
                            Text(stringResource(R.string.s_bf55f8a4), fontSize = 15.sp)
                        }
                    }
                } else if (stage == "failed") {
                    // 失败页（只保留确定，回到待机）
                    Text(stringResource(R.string.s_cb7439e8), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AccentOrange)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        resText(failText),
                        fontSize = 13.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            stage = "idle"
                            ecgMeasurer.stopMeasurement()
                        },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp)
                    }
                    lastRaw?.let { raw ->
                        Spacer(Modifier.height(8.dp))
                        // 与"确定"同款药丸按钮（失败页保留这个调试入口，样式与结果页保持一致）
                        Button(
                            onClick = { bt.sendEcgWave(raw, lastPpgRaw, lastRate, lastRPeaks) },
                            colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                            modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                        ) {
                            Text(stringResource(R.string.s_bf55f8a4), fontSize = 15.sp)
                        }
                    }
                } else {
                    // 待机：上次心电信息 + 测量按钮
                    //
                    // ⚠ **从「录血压」跳过来看到的正是这一页**（`stage` 初值 `idle` 落这里），
                    //   所以校准提示要在这页也说一遍 —— 只放在引导页等于没说。
                    if (calibHint) {
                        CalibHintBox()
                        Spacer(Modifier.height(8.dp))
                    }
                    val lastTimeStr = lastEcg?.let {
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it.measuredAt))
                    } ?: stringResource(R.string.s_a8121899)
                    Text(lastTimeStr, fontSize = 15.sp, color = AccentOrange)
                    lastEcg?.classification?.let { cls ->
                        Text(
                            buildString {
                                append(stringResource(R.string.s_27f94085, EcgNote.classLabel(cls) ?: cls))
                                lastEcg?.analyzedHr?.let { append(stringResource(R.string.s_f9d3f1d6, it)) }
                            },
                            fontSize = 12.sp, color = Skin.Muted,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            sessionDone = null
                            sessionFailed = false
                            failText = ResText.EMPTY
                            stage = "intro"
                        },
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_00387f30), fontSize = 15.sp)
                    }
                }

                // 结果页不再提供"返回"：该页已有"确定"（回到待机）与"上传波形数据"，
                // 三个同级按钮挤在圆屏底部既冗余又难按，且容易和"确定"混淆。
                // 其它阶段（引导/待机/失败）保留返回，否则会困在页面里出不去。
                if (stage != "result") {
                    Spacer(Modifier.height(20.dp))
                    Button(
                        onClick = onBack,
                        colors = ButtonDefaults.buttonColors(
                        backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
                    ),
                        modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
                    ) {
                        Text(stringResource(R.string.s_0049f05a), fontSize = 15.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    /**
     * 首页**主入口**：设计图里最醒目的那条大药丸（♥ + 文字 + →）。
     * 用 Canvas 手绘心形与箭头，而不是引入 material-icons —— 只为一个图标多加一个依赖不值得。
     */
    @Composable
    private fun PrimaryEntry(label: String, onClick: () -> Unit) {
        Row(
            modifier = Modifier
                // 0.82f（197dp）：主入口落在 y≈117~159，那里弦宽 ≥230dp，197dp 绰绰有余；
                // 收窄是为了给下面那一行腾出宽度余量，不是为了它自己。
                .fillMaxWidth(0.82f)
                .height(42.dp)
                .clip(RoundedCornerShape(percent = 50))
                // 参考图里主入口是**浅底深字**：在蓝色盘面上它靠明暗对比跳出来，
                // 而不是靠比别的大一圈（旧版是深底白字 + 渐变，在蓝底上反而糊成一片）
                .background(HomePillLight)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 浅粉圆底 + 实心心（与手机端"心率"那张卡的粉色同源）
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color(0xFFFFD9E4)),
                contentAlignment = Alignment.Center,
            ) {
                HeartGlyph(Modifier.size(13.dp), Color(0xFFE91E63))
            }
            Spacer(Modifier.width(9.dp))
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HomeInk,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            ArrowGlyph(Modifier.size(13.dp), HomeInk)
        }
    }

    /**
     * 首页**次级入口**：两个一排的短药丸（心电/血氧、体温/体成分、睡眠报告/血压记录）。
     *
     * ## 为什么固定两格，不做"任意格数"的通用排
     * 240dp 圆屏上 `fillMaxWidth(0.88f)` 是 211dp：
     *  - 两格：每格约 104dp，四个汉字的标签有充裕余量；
     *  - 三格：每格约 66dp，刚好卡住"体成分"这种三字标签；
     *  - **四格：每格约 49dp，而四个汉字就要约 48dp** —— 零余量，必然贴到圆屏遮罩上被切。
     * 结论：入口数量变多时应该**加行**，而不是把同一行加宽到四格。
     * （这里原本还有一个可传任意格数的 `EntryRow`，体成分接进来后只能一并删掉：
     * 它按三格算的间距，四格时标签必被裁 —— 把一个已知会坏的布局留在代码里，
     * 迟早会被下一个人照着用。）
     */
    /**
     * 手表端的**统一按钮：椭圆（胶囊）形**（2026-09-24 定的界面约定）。
     *
     * ## 为什么统一成椭圆
     * 换之前项目里有**两套按钮**：`CompactButton`（圆形，37 处）与
     * `Button` + `PillGray`（椭圆，十多处）。同一个应用里两种形状并存，
     * 用户没法从形状上判断含义（本来也没有"圆的次要、椭圆的主要"这层设计）。
     * 现在一律椭圆，**新增功能直接用这个组件**。
     *
     * ## 为什么做成 `CompactButton` 的**签名替身**
     * `onClick` 排第一、`content` 保持无参 lambda —— 这样 37 处调用**原样可用**，
     * 只把函数名换掉即可（实际替换时就是这么做的）。刻意**不**在这里收编字号与文案：
     * 那些是各页面自己的排版决定，收进来只会让组件长出一堆参数，
     * 而这次的目的只是**形状统一**。
     *
     * ⚠ 唯一的例外是 [StepperRow] 的 `+`/`-`：它们必须**窄**，否则一个 Row 里
     * 放两个半屏宽的按钮会挤爆。所以 [modifier] 留了默认值供调用处覆盖。
     *
     * ⚠ **别改回圆形**：wear 的 `Button` 默认 shape 就是胶囊
     * （`RoundedCornerShape(percent = 50)`），所以只要不显式设 shape，
     * `fillMaxWidth` 出来的就是椭圆。
     */
    @Composable
    private fun PillButton(
        onClick: () -> Unit,
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit,
    ) {
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Skin.Btn, contentColor = Skin.OnBtn,
            ),
            // ⚠⚠ 宽度必须在这里**强制**加上，不能只写在参数默认值里：
            //   调用方一旦传 `Modifier.padding(...)`，默认值就被整体替换 ⇒ 宽度约束丢失
            //   ⇒ 按钮退化成"内容宽度"、50% 圆角变圆球、文字被挤出边界（用户多次反馈的就是这个）。
            modifier = modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
        ) {
            content()
        }
    }

    @Composable
    private fun EntryPair(
        left: String, onLeft: () -> Unit,
        right: String, onRight: () -> Unit,
        leftAccent: Color? = null, rightAccent: Color? = null,
        widthFraction: Float = 0.88f,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(widthFraction),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            SecondaryChip(left, onLeft, Modifier.weight(1f), accent = leftAccent)
            SecondaryChip(right, onRight, Modifier.weight(1f), accent = rightAccent)
        }
    }

    @Composable
    private fun SecondaryChip(
        label: String,
        onClick: () -> Unit,
        modifier: Modifier,
        accent: Color? = null,
    ) {
        Box(
            modifier = modifier
                .height(34.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(HomeChipFill)
                .border(1.dp, HomeChipBorder, RoundedCornerShape(percent = 50))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // 左侧小色点：一眼区分"哪个是哪个"（与手机端宫格卡片同一套配色）。
            // 色点存在时文字跟着轻微右移，否则会与圆点贴上。
            if (accent != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 11.dp)
                        .size(7.dp)
                        .clip(RoundedCornerShape(percent = 50))
                        .background(accent)
                )
            }
            Text(
                label,
                fontSize = 12.sp,
                color = HomeInk,
                maxLines = 1,
                modifier = Modifier.padding(start = if (accent != null) 9.dp else 0.dp),
            )
        }
    }

    /** 心形图标（实心）。 */
    @Composable
    private fun HeartGlyph(modifier: Modifier, color: Color) {
        Canvas(modifier) {
            val w = size.width; val h = size.height
            val p = Path().apply {
                moveTo(w * 0.50f, h * 0.90f)
                cubicTo(w * 0.02f, h * 0.55f, w * 0.10f, h * 0.05f, w * 0.50f, h * 0.26f)
                cubicTo(w * 0.90f, h * 0.05f, w * 0.98f, h * 0.55f, w * 0.50f, h * 0.90f)
                close()
            }
            drawPath(p, color)
        }
    }

    /** 右向箭头（"进入下一层"语义）。 */
    @Composable
    private fun ArrowGlyph(modifier: Modifier, color: Color) {
        Canvas(modifier) {
            val w = size.width; val h = size.height
            val sw = h * 0.17f
            drawLine(color, Offset(w * 0.12f, h * 0.50f), Offset(w * 0.86f, h * 0.50f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.54f, h * 0.20f), Offset(w * 0.86f, h * 0.50f), sw, StrokeCap.Round)
            drawLine(color, Offset(w * 0.54f, h * 0.80f), Offset(w * 0.86f, h * 0.50f), sw, StrokeCap.Round)
        }
    }

    /**
     * 首页顶部的心电条（设计图里那条绿线）。
     *
     * 注意：这是**合成模板**，不是真实测量值 —— 首页并没有在采集心电，
     * 画真实数据会让人误以为"已经在测了"。它只承担品牌视觉，节奏跟随最近一次心率。
     *
     * 无缝滚动：波形在 x 方向每 `w/beats` 重复一次，而一轮动画恰好平移 `w/beats`，
     * 所以 phase 从 1 回到 0 的那一帧图案完全重合，看不出跳变。
     */
    @Composable
    private fun HomeEcgStrip(bpm: Double?) {
        val periodMs = (60_000.0 / (bpm ?: 72.0).coerceIn(45.0, 150.0))
            .toInt().coerceIn(700, 2000)
        // ⚠⚠ 首页迷你心电的永久循环已移除 —— 这是「一开机就卡」的元凶：
        //   首页常开，它按心率周期连续重绘整条波形。现在画一条静态波形。
        val phase = 0f
        // 0.92 屏宽 / 42dp 高：参考图里这条波形是整屏的视觉中心，旧版的 0.86f/44dp
        // 淹在一堆药丸里看不出重点。高度不取更大，是为了让整页**恰好**落在
        // 圆屏的可见区里（见 HomeScreen 里那段弦宽计算）；横向则尽量用满。
        Canvas(modifier = Modifier.fillMaxWidth(0.92f).height(42.dp)) {
            val w = size.width; val h = size.height
            val base = h * 0.70f
            val amp = h * 0.54f
            val beats = 2.5f
            val steps = 240
            val path = Path()
            for (i in 0..steps) {
                val x = w * i / steps
                val p = (((x / w) * beats) + phase) % 1f
                val y = base - amp * ecgTemplate(p)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            // 先画一层宽而透明的"光晕"，再压一条细而亮的线：
            // 蓝色渐变底上这样才有"发光"的观感（参考图的波形也是发亮的白青光）。
            drawPath(
                path,
                Color(0x33D8F3FF),
                style = Stroke(width = 7.5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawPath(
                path,
                HomeStripe,
                style = Stroke(width = 2.8f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }

    /**
     * 接触页的单列判据读数：上行小字标签、下行"实测值/阈值"。
     * 三项并排成三列（而不是拼成一行），既避开圆屏左右裁切，也让不达标的那一项一眼可见。
     */
    @Composable
    private fun DetectMetric(label: String, value: String) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 9.sp, color = Color(0xFF8A8F98))
            Text(value, fontSize = 10.sp, color = Color(0xFFB8BEC5))
        }
    }

    /** 心电引导页的单选行（手腕位置/按钮方向）。 */
    @Composable
    private fun OptionRow(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(label, fontSize = 13.sp, color = Skin.Ink)
            options.forEachIndexed { i, opt ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onSelect(i) },
                ) {
                    Canvas(Modifier.size(14.dp)) {
                        if (i == selected) {
                            drawCircle(Color(0xFF4C86E8))
                            drawCircle(Skin.Ink, radius = size.minDimension * 0.22f)
                        } else {
                            drawCircle(Color(0xFF9AA0A8), style = Stroke(width = 2f))
                        }
                    }
                    Spacer(Modifier.size(4.dp))
                    Text(opt, fontSize = 13.sp, color = Skin.Ink)
                }
            }
        }
    }

    /** 轻触开始页：橙色心形 + 白色箭头指向心电电极按钮（默认右偏上）。showArrow=false 时只画心形。 */
    @Composable
    private fun TouchStartArt(pointRight: Boolean, showArrow: Boolean = true) {
        // 高度 88 → 56：接触页内容总高度超出 240dp 圆屏，截图里底部按钮被圆形遮罩切掉。
        // 心形是纯装饰，压缩它比压缩实测数值更划算。
        Canvas(modifier = Modifier.fillMaxWidth(0.52f).height(56.dp)) {
            val w = size.width; val h = size.height
            val heartCx = w * 0.34f; val heartCy = h * 0.48f; val r = h * 0.24f
            val heart = Path().apply {
                moveTo(heartCx, heartCy + r * 0.95f)
                cubicTo(
                    heartCx - r * 1.5f, heartCy - r * 0.2f,
                    heartCx - r * 0.75f, heartCy - r * 1.15f,
                    heartCx, heartCy - r * 0.4f,
                )
                cubicTo(
                    heartCx + r * 0.75f, heartCy - r * 1.15f,
                    heartCx + r * 1.5f, heartCy - r * 0.2f,
                    heartCx, heartCy + r * 0.95f,
                )
                close()
            }
            drawPath(heart, AccentOrange, style = Stroke(width = 6f, cap = StrokeCap.Round))
            // 白色箭头：指向表冠位置，右偏上约 14°（左腕反向）
            val dir = if (pointRight) 1f else -1f
            val ax = heartCx + dir * r * 1.9f
            val ay = heartCy - r * 0.7f
            withTransform({ rotate(if (pointRight) -14f else 14f, pivot = Offset(ax, ay)) }) {
                val len = r * 1.15f
                val s = r * 0.5f
                drawLine(Skin.Ink, Offset(ax - dir * len, ay), Offset(ax, ay), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Skin.Ink, Offset(ax - dir * s, ay - s), Offset(ax, ay), strokeWidth = 5f, cap = StrokeCap.Round)
                drawLine(Skin.Ink, Offset(ax - dir * s, ay + s), Offset(ax, ay), strokeWidth = 5f, cap = StrokeCap.Round)
            }
        }
    }

    /**
     * 实时波形（最近约 1000 个采样，逐点绘制）。
     *
     * 用**固定 ±1.5mV 刻度**，不按窗口 min/max 自动缩放。
     * 输入已是去电极偏置的毫伏值，固定刻度才能一眼看出"有没有出现 QRS"；
     * 旧的自动缩放会把 200mV 偏置上的 1mV QRS 等比例放大成噪声（正是用户看到的"全是噪声"）。
     */
    @Composable
    private fun EcgLiveWave(samples: List<Float>, tick: Float) {
        // ⚠⚠⚠ **波形必须铺满整个宽度**（2026-10-05 用户第二次反馈纠正了我）：
        //
        //   他第一次说的是「表的左右两边还有一部分屏幕没显示」，我理解成"波形被圆角裁掉了"
        //   ⇒ 加了 `padding(horizontal = 26.dp)`。**理解反了**：他要的是"左右还有一部分屏幕
        //   **没被波形用上**"（即左右两道黑边），而我的改动恰恰**造出了**那两道黑边
        //   （第二次反馈：「现在显示范围怎么变更小了……我要的是那个波形图能铺满，
        //   能从手表显示屏的最左边到右边都能显示，而不是黑边」）。
        //
        // ⇒ 正确的做法是 `fillMaxSize()` **不加任何内边距**：
        //   ⚗ 波形是连续滚动的，圆屏把两端**上下方向**的角落裁掉不影响判读；
        //     而"左右各留一道黑边"是**主动减少**可用宽度 —— 那才是真的看不清节律。
        //   ⚠ 教训：**「某处没显示」有方向相反两种含义**（内容被裁 / 空间没用上），
        //     不确定时**问一句**，别按自己以为的那种改（这次就是照自己的理解改了、还写了注释）。
        Canvas(modifier = Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION") tick // 仅作重绘驱动，波形位置由样本序号决定
            val w = size.width; val h = size.height
            val base = h * 0.55f
            if (samples.isEmpty()) {
                drawLine(SectionLine, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
                return@Canvas
            }
            // ⚠⚠⚠ 2026-10-16：显示前**必须先滤波**（用户："手表上波形很密集，不像正常心电图"）。
            //
            //   实测（用户导出的那段 30 秒记录，503 Hz、15250 点）：
            //   **相邻采样点平均跳变 0.224 mV、95 分位 0.41 mV** —— 这种斜率只有 QRS 才有，
            //   而它出现在整段里 ⇒ 原始波形里裹着大量 **50 Hz 工频**。
            //   分析链路一直是 `notchMains` → `bandpass`，**只有"实时显示"这一路漏了**：
            //   2 秒窗口里塞约 100 个工频周期，画在 ~400px 上就是一条密锯齿带。
            //
            //   两处改动：
            //   ① **先滤波**：取"可视窗口 + 1 秒预热余量"（`bandpass` 的 `skipSec` 会把
            //      前 1 秒置零，必须先垫足），陷波 50/60 Hz 再带通 0.5~40 Hz；
            //   ② **窗口 1000 → 600 点**（≈1.2 秒）：QRS 之间更疏、形态更好认。
            val visible = minOf(samples.size, 600)
            val warm = ECG_FS_HZ.toInt()                       // 带通预热 1 秒
            val fromRaw = maxOf(0, samples.size - (visible + warm))
            val rawTail = FloatArray(samples.size - fromRaw) { samples[fromRaw + it] }
            val filteredStart = (samples.size - fromRaw) - visible
            val filtered = if (rawTail.size >= 8) {
                EcgSignal.bandpass(
                    EcgSignal.notchMains(rawTail, ECG_FS_HZ),
                    ECG_FS_HZ, 0.5, 40.0,
                )
            } else {
                rawTail
            }
            val scale = (h * 0.44f) / MV_LIVE_FULL_SCALE
            val path = Path()
            var i = 0
            while (i < visible && filteredStart + i < filtered.size) {
                val x = i * w / visible
                val v = filtered[filteredStart + i]
                val y = base - v.coerceIn(-MV_LIVE_FULL_SCALE, MV_LIVE_FULL_SCALE) * scale
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                i += 1
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(listOf(Color(0xFFFF4D6B), Color(0xFFFFB35C))),
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )
            // 基线
            drawLine(SectionLine, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
        }
    }

    /** 心电教学图：表带 + 手表 + 表冠电极 + 按压的手指（提示手指按住表冠）。 */
    @Composable
    private fun EcgHowArt() {
        Canvas(modifier = Modifier.fillMaxWidth(0.86f).height(120.dp)) {
            val w = size.width; val h = size.height
            val cx = w * 0.44f; val cy = h * 0.55f; val r = h * 0.36f
            // 表带
            drawRoundRect(
                color = Color(0xFF2A2C31),
                topLeft = Offset(cx - r * 0.42f, 0f),
                size = Size(r * 0.84f, h),
                cornerRadius = CornerRadius(18f, 18f),
            )
            // 表体 + 表盘
            drawCircle(Color(0xFF3A3D43), radius = r * 1.14f, center = Offset(cx, cy))
            drawCircle(Color(0xFF101113), radius = r, center = Offset(cx, cy))
            // 表盘上的心电波形
            val midY = cy
            val wave = Path().apply {
                moveTo(cx - r * 0.52f, midY)
                lineTo(cx - r * 0.18f, midY)
                lineTo(cx - r * 0.06f, midY + r * 0.30f)
                lineTo(cx + r * 0.10f, midY - r * 0.42f)
                lineTo(cx + r * 0.22f, midY + r * 0.12f)
                lineTo(cx + r * 0.30f, midY)
                lineTo(cx + r * 0.52f, midY)
            }
            drawPath(wave, AccentOrange, style = Stroke(width = 4.5f, cap = StrokeCap.Round))
            // 表冠电极（表体右侧凸起）
            drawRoundRect(
                color = Color(0xFF9AA0A8),
                topLeft = Offset(cx + r * 1.06f, cy - r * 0.24f),
                size = Size(r * 0.30f, r * 0.48f),
                cornerRadius = CornerRadius(7f, 7f),
            )
            // 按压的手指
            drawCircle(
                Color(0xFFF3C6A5).copy(alpha = 0.30f),
                radius = r * 0.46f,
                center = Offset(cx + r * 1.62f, cy + r * 0.42f),
            )
            drawCircle(
                Color(0xFFF3C6A5),
                radius = r * 0.32f,
                center = Offset(cx + r * 1.62f, cy + r * 0.42f),
            )
        }
    }

    /** 结果页心电波形（米白→橙色描边）。固定 ±1.5mV 刻度，便于比较不同次测量的幅度。 */
    @Composable
    private fun EcgWaveCanvas(samples: List<Float>, phase: Float, windowSamples: Int) {
        // ⚠⚠ 同上：**铺满整个宽度、不留内边距**（理由见 `EcgLiveWave` 那段注释）。
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val w = size.width; val h = size.height
            val base = h * 0.62f
            drawLine(SectionLine, Offset(0f, base), Offset(w, base), strokeWidth = 1f)
            if (samples.isEmpty()) return@Canvas
            // 采样相位：phase 只作为重绘驱动，波形位置由真实样本序号决定
            val visible = minOf(samples.size, windowSamples)
            val from = samples.size - visible
            val slice = samples.subList(from, samples.size)
            val scale = (h * 0.55f) / MV_FULL_SCALE
            val path = Path()
            // 每像素列取 min/max 包络：整段 30 秒压缩到一屏时，
            // 单纯抽点会把 QRS 尖峰漏掉（看起来像一条平线）
            val cols = w.toInt().coerceAtLeast(1)
            val perCol = (visible.toFloat() / cols).coerceAtLeast(1f)
            var col = 0
            while (col < cols) {
                val a = (col * perCol).toInt().coerceIn(0, visible - 1)
                val b = ((col + 1) * perCol).toInt().coerceIn(a + 1, visible)
                var lo = Float.MAX_VALUE; var hi = -Float.MAX_VALUE
                for (k in a until b) {
                    val v = slice[k]
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                val x = w * col / cols
                val yHi = base - hi.coerceIn(-MV_FULL_SCALE, MV_FULL_SCALE) * scale
                val yLo = base - lo.coerceIn(-MV_FULL_SCALE, MV_FULL_SCALE) * scale
                if (col == 0) path.moveTo(x, yHi)
                path.lineTo(x, yHi)
                path.lineTo(x, yLo)
                col++
            }
            drawPath(
                path,
                brush = Brush.horizontalGradient(listOf(Color(0xFFFF4D6B), Color(0xFFFFB35C))),
                style = Stroke(width = 2f, cap = StrokeCap.Round),
            )
        }
    }

    @Composable
    private fun OffBodyPage(text: String = stringResource(R.string.s_9736345d), onConfirm: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WristGraphic()
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.s_f52f1238),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = AccentOrange,
            )
            Spacer(Modifier.height(4.dp))
            // 文字区占满剩余空间，内容多时独立上下滚动；确定按钮固定底部
            Text(
                text,
                fontSize = 13.sp,
                color = Skin.Ink,
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2962FF)),
                modifier = Modifier.fillMaxWidth(0.70f).heightIn(min = 46.dp),
            ) {
                Text(stringResource(R.string.s_003dfbe4), fontSize = 15.sp)
            }
        }
    }

    /** 原生风格佩戴示意图：白色药丸 + 深色表盘 + 蓝色向下箭头。 */
    @Composable
    private fun WristGraphic() {
        Canvas(modifier = Modifier.size(width = 48.dp, height = 72.dp)) {
            val w = size.width; val h = size.height
            drawRoundRect(
                color = Color(0xFFF2F2F2),
                cornerRadius = CornerRadius(w / 2f, w / 2f),
            )
            drawCircle(Color(0xFF3A3F44), radius = w * 0.30f, center = Offset(w * 0.5f, h * 0.30f))
            drawCircle(Color(0xFF585E63), radius = w * 0.22f, center = Offset(w * 0.5f, h * 0.30f))
            val ax = w * 0.5f
            drawLine(
                Color(0xFF2962FF),
                start = Offset(ax, h * 0.44f),
                end = Offset(ax, h * 0.70f),
                strokeWidth = w * 0.12f,
                cap = StrokeCap.Round,
            )
            val tri = Path().apply {
                moveTo(ax, h * 0.86f)
                lineTo(ax - w * 0.17f, h * 0.68f)
                lineTo(ax + w * 0.17f, h * 0.68f)
                close()
            }
            drawPath(tri, Color(0xFF2962FF))
        }
    }

    @Composable
    private fun SectionStart(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxWidth()) { content() }
        Spacer(Modifier.height(2.dp))
    }

    @Composable
    private fun SectionLineBox() {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().height(0.7.dp).background(SectionLine)
        )
        Spacer(Modifier.height(10.dp))
    }

    @Composable
    private fun XLabels(labels: List<String>) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            labels.forEach { Text(it, fontSize = 9.sp, color = Skin.Muted) }
        }
    }

    /** 三星健康风格柱状图：蓝色历史时段，橙色当前时段，右上角为刻度上限。 */
    @Composable
    private fun HrBarChart(bars: List<ChartBar?>, lo: Int, hi: Int) {
        Box(Modifier.fillMaxWidth()) {
            Text(
                "$hi",
                fontSize = 9.sp, color = Skin.Muted,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Canvas(modifier = Modifier.fillMaxWidth().height(84.dp)) {
                val n = bars.size.coerceAtLeast(1)
                val slot = size.width / n
                val barW = slot * 0.46f
                val range = (hi - lo).toFloat().coerceAtLeast(1f)
                val radius = CornerRadius(barW / 2f, barW / 2f)
                bars.forEachIndexed { i, bar ->
                    if (bar != null) {
                        val yHi = size.height * (1f - (bar.hi - lo) / range)
                        val yLo = size.height * (1f - (bar.lo - lo) / range)
                        drawRoundRect(
                            color = if (bar.current) AccentOrange else BarBlue,
                            topLeft = Offset(i * slot + (slot - barW) / 2f, yHi),
                            size = Size(barW, (yLo - yHi).coerceAtLeast(barW)),
                            cornerRadius = radius,
                        )
                    }
                }
                // 底部基线
                drawLine(
                    color = SectionLine,
                    start = Offset(0f, size.height - 0.5f),
                    end = Offset(size.width, size.height - 0.5f),
                    strokeWidth = 1f,
                )
            }
        }
    }

    /**
     * 跳到三星健康手表端的「抗氧化指数」页。
     *
     * ## 为什么是"跳过去"而不是"自己测"
     * 那个指数测的是**皮肤类胡萝卜素对蓝光的吸收**（再混入活动/睡眠/压力等因子合成 0~100）。
     * 而公开的 Health Sensor SDK 21 个 tracker 里**没有任何一个含蓝光通道**
     * （我们只有绿/红/红外），Data SDK 的 32 个数据类型里也没有它 ——
     * **通道与数据两头都没开放**。所以本 App 能做的最实在的事，就是别让用户自己翻三星健康。
     *
     * ## 为什么必须带兜底
     * 这是三星健康**未公开的内部 Activity**，三种情况都会让精确跳转失败：
     *  ① 组件名随三星健康版本改（用户能给到名字是因为在活动启动器里看到的，那是当前版本）；
     *  ② 区域 / 三星账号门槛可能让它在某些机器上根本不可用（这个功能在中国区就被藏了入口）；
     *  ③ Android 11+ 的包可见性要求先在清单里声明（见 AndroidManifest 的 `<queries>`）。
     * 所以失败就退化成"只把三星健康打开"，再不行就**明说一句** ——
     * 这个入口绝不能变成一个点了没反应的死键。
     */
    private fun openSamsungAntioxidant() {
        val pkg = "com.samsung.android.wear.shealth"
        // ① 精确跳转（组件名来自用户在活动启动器里看到的）
        val direct = Intent()
            .setComponent(ComponentName(pkg, "$pkg.app.antioxidant.AntioxidantActivity"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(direct) }.isSuccess) return
        // ② 退化：只打开三星健康，用户至少能自己在里面找。
        //    **这里必须说一句** —— 静默落到三星健康的首页，用户会以为这个按钮点错了，
        //    而实际上"精确跳转失败"是个需要反馈给开发者的信号（组件名可能已经变了）。
        packageManager.getLaunchIntentForPackage(pkg)?.let { home ->
            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(home) }.isSuccess) {
                Toast.makeText(
                    this,
                    this@MainActivity.getString(R.string.s_7280bc8e),
                    Toast.LENGTH_LONG,
                ).show()
                return
            }
        }
        // ③ 都不行：说清楚，别让按钮"点了没反应"
        Toast.makeText(
            this,
            this@MainActivity.getString(R.string.s_5be1899f),
            Toast.LENGTH_LONG,
        ).show()
    }
    /**
     * 每分钟刷新的时钟文本（封面用）。
     * ⚠ 用 15s 轮询而不是"整分对齐"：实现简单、开销可忽略，且不会因为休眠错过更新。
     */
    @Composable
    private fun rememberClockText(): String {
        val fmt = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
        var text by remember { mutableStateOf(fmt.format(java.util.Date())) }
        LaunchedEffect(Unit) {
            while (true) {
                text = fmt.format(java.util.Date())
                kotlinx.coroutines.delay(15_000)
            }
        }
        return text
    }

    /**
     * 品牌标：**每个主题一张素材**。
     *
     * ⚠⚠ 2026-10-26（终）这里的素材不是"染色"出来的，而是**离线按主题各生成一张**
     *   （品牌图生成脚本，源图 = 用户给的线稿 `分层图片 (5).png`）：
     *     · 主笔画 = 该主题的 `ink`（浅底深线 / 深底浅线），笔心掺一点 `gold`；
     *     · 圆弧内的竖向细纹 = 该主题的 `gold`。
     *   ⇒ 所以颜色跟着主题走，但**没有任何运行时混色**，外观完全可预期。
     *
     * ⚠ 走过的弯路（别再来一遍，这一轮来回改了 8 次）：
     *   · 只描外轮廓 → 内部结构全丢，缩到 168px 只剩"两块馒头"；
     *   · 三色扁平化 → 白边没了但立体感没了；
     *   · 运行期 `ColorFilter.tint` 染色 → 纯色填充，用户觉得"太难看"。
     *   · 用"笔触浓淡"当 alpha → 原稿的浅色笔心变成半透明，深色主题上整笔看着像**空心管**。
     * ⚠ 布局不变：等比适配进调用方给的方框（等价 ContentScale.Fit）。
     */
    private fun markRes(id: String): Int = when (id) {
        "graphite" -> R.drawable.ic_mark_graphite
        "midnight" -> R.drawable.ic_mark_midnight
        "forest" -> R.drawable.ic_mark_forest
        "sand" -> R.drawable.ic_mark_sand
        "aurora" -> R.drawable.ic_mark_aurora
        "titanium" -> R.drawable.ic_mark_titanium
        "dusk" -> R.drawable.ic_mark_dusk
        "obsidian" -> R.drawable.ic_mark_obsidian
        else -> R.drawable.ic_mark_ceramic
    }

    @Composable
    private fun BrandMark(modifier: Modifier = Modifier) {
        val res = androidx.compose.ui.platform.LocalContext.current.resources
        // ⚠ 用主题 id 当 key：切主题走 recreate()，这里跟着换素材；万一将来改成不重建也能生效。
        val img = androidx.compose.runtime.remember(Skin.current.id) {
            android.graphics.BitmapFactory.decodeResource(res, markRes(Skin.current.id))
                .asImageBitmap()
        }
        androidx.compose.foundation.Canvas(modifier) {
            val k = minOf(size.width / img.width, size.height / img.height)
            val dw = (img.width * k).toInt().coerceAtLeast(1)
            val dh = (img.height * k).toInt().coerceAtLeast(1)
            drawImage(
                image = img,
                dstOffset = androidx.compose.ui.unit.IntOffset(
                    ((size.width - dw) / 2f).toInt(),
                    ((size.height - dh) / 2f).toInt(),
                ),
                dstSize = androidx.compose.ui.unit.IntSize(dw, dh),
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
            )
        }
    }

    /** 封面：无按钮。logo / 蓝牙状态 / 一行艺术字；上滑（或点一下）进入。 */
    @Composable
    private fun CoverScreen(connected: Boolean, onOpenHub: () -> Unit) {
        var dragged by remember { mutableStateOf(0f) }
        // ⚠⚠⚠ 2026-10-21 用户反馈"首页点一下屏幕会黑一下，然后才到下拉页"——
        //   根因就是下面那个**整屏** `clickable` 用了**默认按压指示**：
        //   Compose 在没人提供 `LocalIndication` 时用的是 `DefaultDebugIndication`，
        //   表现是按下的瞬间给**整个节点**铺 12% 黑。按钮只有几平方厘米时不明显，
        //   而这里的节点是**整块屏幕** ⇒ 看上去就是"黑一下 / 闪屏"。
        //   （同一个原因也解释了"右滑退出时黑一下"：手指一碰就先铺一层黑，紧接着 App 退掉。）
        //   ⇒ 指示器必须显式给 null，按压反馈改由我们自己画（下面那层轻微缩放）。
        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        val pressScale by animateFloatAsState(
            targetValue = if (pressed) 0.965f else 1f,
            animationSpec = tween(120, easing = FastOutSlowInEasing),
            label = "coverPress",
        )
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragged < -50f) onOpenHub()
                            dragged = 0f
                        },
                        onDragCancel = { dragged = 0f },
                    ) { _, dy -> dragged += dy }
                }
                .clickable(interactionSource = press, indication = null) { onOpenHub() },
            contentAlignment = Alignment.Center,
        ) {
            // 按压反馈：整块内容轻微缩一下（0.965），松手/翻页时自然回弹。
            // ⚠ 只缩内容、不缩底色 —— 缩放对象带底色会在边缘露出缝隙。
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = pressScale
                        scaleY = pressScale
                    },
                contentAlignment = Alignment.Center,
            ) {
            // ⚠⚠ 2026-10-19：封面原来用固定 dp，在 396×396（40mm 表）上标语会被圆边切掉。
            //   改成按屏宽等比缩放（基准 227dp）：小屏缩、大屏略放。
            // ⚠⚠ BoxWithConstraints 的默认 contentAlignment 是 **TopStart**（不是 Center）——
            //   不加这一行，封面内容会整体偏左上、左边缘被圆弧切掉（真机截图踩过）。
            BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val u = (maxWidth.value / 227f).coerceIn(0.78f, 1.15f)
                // ⚠ Column 的隐式接收者是 ColumnScope，取 maxWidth 必须先存成局部变量
                // ⚠ 0.38 会把底部「上滑进入」顶出圆边（实测被切）⇒ 取 **0.355**：
                //   比原来的 0.34 略大（更清楚），又不挤掉底部那行。
                val logoSize = maxWidth * 0.355f
                // ⚠ 圆屏的**可用纵向高度**远小于直径：内容必须缩进，否则顶部时钟、底部「上滑进入」
                //   都会被圆弧切掉（396 / 480 两种尺寸都实测踩过）。
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(top = (20 * u).dp, bottom = (8 * u).dp),
                ) {
                    Text(
                        text = rememberClockText(),
                        fontSize = (11 * u).sp,
                        color = Skin.Muted,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(bottom = (6 * u).dp),
                    )
                    BrandMark(Modifier.size(logoSize))
                    Spacer(Modifier.height((6 * u).dp))
                    Text(
                        stringResource(R.string.s_00429794),
                        fontSize = (15 * u).sp, color = Skin.Ink, letterSpacing = 2.sp,
                    )
                    Spacer(Modifier.height((10 * u).dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size((6 * u).dp)
                                .background(
                                    if (connected) Color(0xFF4F8A5B) else Skin.Muted,
                                    CircleShape,
                                )
                        )
                        Spacer(Modifier.width((6 * u).dp))
                        Text(
                            stringResource(if (connected) R.string.s_66bd8f30 else R.string.s_af1d1f5c),
                            fontSize = (11 * u).sp, color = Skin.Muted,
                        )
                    }
                    Spacer(Modifier.height((10 * u).dp))
                    Text(
                        stringResource(R.string.s_66ede444),
                        fontSize = (11 * u).sp,
                        color = Skin.Gold,
                        letterSpacing = 1.sp,
                        lineHeight = (15 * u).sp,
                        textAlign = TextAlign.Center,
                        // ⚠ 单行在 396 屏上必然被圆弧切（弦宽不够）⇒ 收窄到 0.62 让它**自动换行**，
                        //   两行都落在较宽的弦内。实测 396/450/480 都不再切。
                        modifier = Modifier.fillMaxWidth(0.62f),
                    )
                    Spacer(Modifier.height((8 * u).dp))
                    Text(
                        stringResource(R.string.s_e286fe43),
                        fontSize = (9 * u).sp,
                        color = Skin.Muted.copy(alpha = 0.8f),
                        letterSpacing = 1.sp,
                    )
                }
            }
            }   // 按压缩放层
        }
    }

    @Composable
    private fun HubScreen(
        onBasic: () -> Unit,
        onPro: () -> Unit,
        onSettings: () -> Unit,
        onBackToCover: () -> Unit = {},
    ) {
        var dragged by remember { mutableStateOf(0f) }
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (dragged > 90f) onBackToCover()
                            dragged = 0f
                        },
                        onDragCancel = { dragged = 0f },
                    ) { _, dy -> dragged += dy }
                },
        ) {
        Column(
            Modifier
                .fillMaxSize()
                // ⚠ 圆屏装不下"标题 + 三个按钮"时，最后一个会被裁掉（用户反馈"设置下面显示不全"）
                //   ⇒ 整页可滚。
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 26.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(R.string.s_00429794),
                fontSize = fsp(13f), color = Skin.Muted, letterSpacing = 2.sp,
            )
            Spacer(Modifier.height(10.dp))
            HubButton(stringResource(R.string.s_00387f30), gold = true, onClick = onBasic)
            Spacer(Modifier.height(8.dp))
            HubButton(stringResource(R.string.s_5b775380), onClick = onPro)
            Spacer(Modifier.height(8.dp))
            HubButton(stringResource(R.string.s_b5c7b82d), onClick = onSettings)
        }
        }
    }

    @Composable
    private fun HubButton(label: String, gold: Boolean = false, onClick: () -> Unit) {
        Box(
            Modifier
                // ⚠ 圆屏几何：227dp 屏幕上，宽 0.72f(≈327px) 的按钮只能落在 y∈[72,382]；
                //   之前 0.86 宽 + 42dp 高，最下面那个必然被圆边切掉（真机截图实测）。
                .fillMaxWidth(0.72f)
                .height(38.dp)
                .clip(RoundedCornerShape(19.dp))
                // ⚠ 2026-10-19 统一按钮样式：**墨黑底 + 奶白字**（用户指定的预览图配色）。
                //   「测量」用金色描边做强调，但仍保持深底，避免一块亮色面板。
                .background(Skin.Btn)
                .border(
                    1.dp,
                    // 2026-10-20 用户要求：三个入口按钮统一金色描边
                    Skin.Gold.copy(alpha = 0.75f),
                    RoundedCornerShape(19.dp),
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠ 2026-10-21 用户："450 / 480 的表上按钮里的字还能再大一点"。
            //   字号统一走 [fsp]：基准 15.5sp，480px 屏（240dp）上自动到 ≈16.4sp。
            Text(label, fontSize = fsp(15.5f), color = Skin.OnBtn, letterSpacing = 0.4.sp, maxLines = 1)
        }
    }

    /**
     * 右侧滚动指示器：圆弧轨道 + 金色滑块，按 `ScrollState` 实时反映位置。
     * `maxValue <= 0`（内容没超一屏）时不画。
     */
    /** 圆弧轨道的公共画法：`frac` 是滚动比例，`thumbFrac` 是滑块占比。
     *  ⚠ 这是 DrawScope 扩展、**不能标 @Composable**（否则在 Canvas{} 里调用会报"Composable invocations..."）。 */
    private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArcBar(frac: Float, thumbFrac: Float) {
        val stroke = 4.dp.toPx()
        val inset = stroke / 2f + 7.dp.toPx()
        val r = (size.minDimension / 2f) - inset
        if (r <= 0f) return
        val topLeft = Offset(size.width / 2f - r, size.height / 2f - r)
        val arcSize = Size(r * 2f, r * 2f)
        val total = 74f
        val startAngle = -total / 2f
        drawArc(
            color = Skin.Hairline,
            startAngle = startAngle, sweepAngle = total, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val thumbSweep = total * thumbFrac
        val thumbStart = startAngle + (total - thumbSweep) * frac
        drawArc(
            color = Skin.Gold.copy(alpha = 0.80f),
            startAngle = thumbStart, sweepAngle = thumbSweep, useCenter = false,
            topLeft = topLeft, size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }

    /**
     * 圆弧滚动条（[ScalingLazyListState] 版本）。
     * `ScalingLazyColumn` 没有 ScrollState，只能从 layoutInfo 推：中心项 / 总项数 / 可见项数。
     */
    @Composable
    private fun ArcScrollBarScaling(state: ScalingLazyListState, modifier: Modifier = Modifier) {
        val info = state.layoutInfo
        val total = info.totalItemsCount
        if (total <= 1) return
        val visible = info.visibleItemsInfo.size.coerceAtLeast(1)
        val center = state.centerItemIndex.coerceIn(0, total - 1)
        val frac = center.toFloat() / (total - 1).toFloat()
        val thumbFrac = (visible.toFloat() / total.toFloat()).coerceIn(0.18f, 0.62f)
        Canvas(modifier.fillMaxSize()) { drawArcBar(frac, thumbFrac) }
    }

    @Composable
    private fun ScrollBarRight(state: ScrollState, modifier: Modifier = Modifier) {
        // ⚠ 2026-10-20 改成**沿圆边的圆弧**（原来是一条竖直条，看着不像圆表该有的东西）。
        //   轨道 74°、从 3 点钟方向上下各 37°，半径取 min(宽,高)/2 再往里收 7dp + 半个线宽。
        val max = state.maxValue
        if (max <= 0) return
        val viewport = state.viewportSize.toFloat().coerceAtLeast(1f)
        val frac = (state.value.toFloat() / max).coerceIn(0f, 1f)
        val thumbFrac = (viewport / (max + viewport)).coerceIn(0.18f, 0.62f)
        Canvas(modifier.fillMaxSize()) { drawArcBar(frac, thumbFrac) }
    }

    /**
     * `ScalingLazyColumn` + **右侧圆弧滚动条** 的封装（2026-10-20）。
     *
     * ⚠ 之前只有根层那一根 bar，且它读的是根 `listState`；而大多数页面内部自己
     *   `remember` 了 state ⇒ 根 bar 拿不到数据，于是"很多页面没有滑动条"。
     *   把 state 建在封装里、bar 也画在这里，页面只要用 `SclPage` 就一定有。
     */
    @Composable
    private fun SclPage(
        modifier: Modifier = Modifier,
        horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
        verticalArrangement: Arrangement.Vertical = Arrangement.Top,
        content: androidx.wear.compose.material.ScalingLazyListScope.() -> Unit,
    ) {
        val st = rememberScalingLazyListState()
        Box(Modifier.fillMaxSize()) {
            ScalingLazyColumn(
                modifier = modifier,
                state = st,
                horizontalAlignment = horizontalAlignment,
                verticalArrangement = verticalArrangement,
                content = content,
            )
            ArcScrollBarScaling(st)
        }
    }

    @Composable
    private fun FeatureGridScreen(
        title: String,
        items: List<Pair<String, () -> Unit>>,
        listState: ScalingLazyListState,
        scrollState: ScrollState,
        onBack: () -> Unit,
    ) {
        // ⚠ 2026-10-19 改用普通滚动列：`ScalingLazyColumn` 会把靠近边缘的项**缩小+淡化**，
        //   在真机截图里第二排按钮看起来像"不可点"（灰掉）。网格页不需要这种弧形效果。
        Box(Modifier.fillMaxSize()) {
        ScrollBarRight(scrollState)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 18.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(title, fontSize = fsp(14.5f), fontWeight = FontWeight.Bold, color = Skin.Ink)
            Spacer(Modifier.height(10.dp))
            items.chunked(2).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(0.84f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    row.forEach { (label, onClick) ->
                        GridButton(label, Modifier.weight(1f), onClick)
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
            }
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.44f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Skin.Btn)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.s_0049f05a), fontSize = fsp(12.5f), color = Skin.OnBtn)
            }
            // 圆屏底部要留够：否则最后一行永远滚不进安全区（截图里第四排被切）
            Spacer(Modifier.height(44.dp))
        }
        }
    }

    @Composable
    private fun GridButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
        Box(
            modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Skin.Btn)
                .clickable(onClick = onClick)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠ 英文标签比中文长得多（"Pro measurements" vs "专业测量"），
            //   硬性 maxLines=1 会被裁掉 ⇒ 允许两行 + 居中 + 缩小字号。
            Text(
                text = label,
                fontSize = fsp(13f),       // 2026-10-20 用户要求：按钮尺寸不变，字调大
                color = Skin.OnBtn,
                maxLines = 2,
                textAlign = TextAlign.Center,
                lineHeight = fsp(16f),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    /** 设置页：传感器能力探测 / 语言 / 软件版本。 */
    @Composable
    private fun SettingsScreen(
        versionText: String,
        onProbe: () -> Unit,
        onLanguage: () -> Unit,
        onTheme: () -> Unit,
        onBack: () -> Unit,
        settingsScroll: ScrollState,
    ) {
        Box(Modifier.fillMaxSize()) {
        ScrollBarRight(settingsScroll)
        Column(
            Modifier
                .fillMaxSize()
                // ⚠ 之前标题与系统时间（TimeText）**重叠**，返回键又被圆边裁掉 ⇒ 顶部留 34dp + 整页可滚。
                .verticalScroll(settingsScroll)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(stringResource(R.string.s_b5c7b82d), fontSize = fsp(14.5f), fontWeight = FontWeight.Bold, color = Skin.Ink)
            Spacer(Modifier.height(10.dp))
            SettingRow(stringResource(R.string.s_656182c8), null, onProbe)
            Spacer(Modifier.height(8.dp))
            SettingRow(stringResource(R.string.s_2a34c50a), null, onLanguage)
            Spacer(Modifier.height(8.dp))
            // 2026-10-20 新增：主题（当前只有「陶瓷白」，后续会加更多背景）
            SettingRow(
                label = stringResource(R.string.s_12055c4b),
                // ⚠⚠ 2026-10-25 修：这里原来写死 `stringResource(R.string.s_a74acec0)`
                //   —— 那是"陶瓷白"这个**字面量**，所以切到任何主题后这一行永远显示陶瓷白
                //   （用户截图就是这样：弹窗里 ✓ 在极光，设置行还写着陶瓷白）。
                value = Skin.current.name,
                onClick = onTheme,
            )
            Spacer(Modifier.height(8.dp))
            // ── 检查更新（WiFi，2026-10-21）──
            //   手动入口只留这一个：用户点一下 → 直接连云端拉包（不经过手机）。
            //   ⚠ 监听窗口不再手动开：手机发现有新版本会通过蓝牙下发"开窗"指令（10 分钟）。
            run {
                val ctx = LocalContext.current
                // ⚠⚠ 状态改从 `WatchUpdateRunner` 读（2026-10-28）：
                //   更新现在有**两个触发源** —— 本行点击，和手机通过蓝牙下发的
                //   `WATCH_UPDATE_REQ`。原来状态是本地的 `remember`，手机远程触发时
                //   这一行什么都看不见（用户会以为"手机上点了没生效"）。
                val busy by WatchUpdateRunner.running.collectAsState()
                val msg by WatchUpdateRunner.status.collectAsState()
                val checking = stringResource(R.string.s_4ba20579)
                SettingRow(
                    label = stringResource(R.string.s_4d36128b),
                    value = msg,
                    onClick = {
                        if (busy) return@SettingRow
                        // 先把提示语种进去，再交给 runner（它内部有互斥，不会重复跑）
                        WatchUpdateRunner.status.value = checking
                        WatchUpdateRunner.start(ctx)
                    },
                )
            }
            Spacer(Modifier.height(14.dp))
            // 版本放在最下面（用户要求），样式与上面一致
            SettingRow(stringResource(R.string.s_664250e7), versionText, null)
            Spacer(Modifier.height(16.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.44f)
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Skin.Btn)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.s_0049f05a), fontSize = fsp(12.5f), color = Skin.OnBtn)
            }
        }
        }
    }

    @Composable
    private fun SettingRow(label: String, value: String?, onClick: (() -> Unit)?) {
        // ⚠ 2026-10-20：原来"标签居中 + 值靠右"同一行会挤成一句（"Theme Ceramic White"）。
        //   改成**两行式**：标签居中，值放下一行小字居中。
        Row(
            Modifier
                .fillMaxWidth(0.78f)
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Skin.Btn)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = fsp(14.5f), color = Skin.OnBtn, textAlign = TextAlign.Center)
                if (value != null) {
                    Text(
                        value, fontSize = fsp(12f), color = Skin.OnBtn.copy(alpha = 0.70f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }

    @Composable
    private fun EcgExportDialog(text: String, onDismiss: () -> Unit) {
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .background(Skin.Card, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.s_69131af3),
                        fontSize = 12.sp, color = Skin.Muted,
                    )
                    Spacer(Modifier.height(6.dp))
                    // ⚠ 正文里可能是**四种不同的东西**（正在导出 / 成功+路径 /
                    //   手机端没存下 / 没有回执 / 蓝牙未连接），所以这里**不加工**，
                    //   原样显示 —— 任何"美化"都可能把状态说错。
                    Text(
                        text,
                        fontSize = 10.sp,
                        color = Skin.Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.s_1faf4f0c),
                        fontSize = 12.sp,
                        color = Color(0xFF4DD0E1),
                        modifier = Modifier
                            .clip(RoundedCornerShape(percent = 50))
                            .background(PillGray)
                            .clickable { onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    /**
     * 语言选择弹窗（模块 #15，手表端）。
     *
     * ## 为什么用 `ui.window.Dialog` 自绘而不是 Wear 的 AlertDialog
     * 手表端目前没引 Wear material3 的对话框（只用 material 1.x），
     * 而 `androidx.wear.compose.material.AlertDialog` 的按钮布局在 240dp 圆屏上要
     * 额外打磨。这里只需要"四行可点文字"，自绘反而更可控、且不引新依赖。
     *
     * ## 语言名用各自语言的写法
     * 简体中文 / 繁體中文 / English / 한국어 —— 这不是"没翻译"，
     * 而是语言选择器的通行做法：看不懂当前语言的用户也能认出自己要选哪一行。
     */
    /**
     * 主题列表弹窗。目前只有「陶瓷白」一项，**后续加主题背景时只往 `themes` 里加**即可。
     * 样式与语言弹窗一致：圆角卡片 + 0.86 宽，完全收在圆内。
     */
    @Composable
    private fun ThemePickerDialog(
        current: String,
        themes: List<ThemeDef>,
        onPick: (ThemeDef) -> Unit,
        onDismiss: () -> Unit,
    ) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            // ⚠⚠ 2026-10-25 修：主题变成 9 项后，这个弹窗**超出圆屏且不能滚动**
            //   （用户截图：列表被圆边切掉、上滑无效）。
            //   现在：整屏限高（屏幕高度的 0.72）＋**内容可滚动**，标题固定在上方。
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier.fillMaxSize(),
                // ⚠ 这个容器必须**居中**：漏掉 contentAlignment 时卡片贴顶，被圆屏上缘切掉
                //   （用户截图"超出屏幕"的一半原因）。
                contentAlignment = Alignment.Center,
            ) {
                // ⚠ 2026-10-25（三改）：用户觉得卡片太大 ⇒ 宽度 0.86→0.72、限高 0.66→0.58，
                //   内边距与行距同步收紧；列表仍可滚动（9 项放不下）。
                val maxH = maxHeight * 0.58f
                Box(
                    Modifier
                        .fillMaxWidth(0.72f)
                        .heightIn(max = maxH)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                        .background(Skin.Card)
                        .border(
                            1.dp, Skin.CardLine,
                            androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(stringResource(R.string.s_12055c4b), fontSize = 10.sp, color = Skin.Muted)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // ⚠ 底部渐隐：卡片高度受限时最后一行会被**从中间切断**（看着像 bug），
                        //   用一层到背景色的渐变把它盖掉，视觉上变成"还有更多，可上滑"。
                        //   注意这层要画在滚动内容**之后**、且不吃点击（Box 不设 clickable）。
                    // ⚠ 动态主题后面标「（动态）」：用户看不出哪几款会动（4 款动效混在 9 项里）
                    val dynamicTag = stringResource(R.string.s_7d3deb06)
                    themes.forEach { t ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(t) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                if (t.id == current) "✓" else " ",
                                fontSize = 11.sp, color = Skin.Gold,
                                modifier = Modifier.padding(end = 4.dp),
                            )
                            Text(t.name, fontSize = 11.sp, color = Skin.Ink, maxLines = 1)
                            // 动态标记做成**次级小字**（比拼进名字里更清楚，也不用靠空格对齐）
                            if (t.anim != ThemeDef.ANIM_NONE) {
                                Text(
                                    dynamicTag,
                                    fontSize = 9.sp,
                                    color = Skin.Muted,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 3.dp),
                                )
                            }
                        }
                    }
                    }
                }
                // ⚠ 底部渐隐：卡片限高时最后一行会被从中间切断（像 bug）。用一层到卡片色的
                //   渐变把它盖住 ⇒ 读作"还有更多，可上滑"。Box 不设 clickable，不吃点击。
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(14.dp)
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Skin.Card)),
                        ),
                )
                }
            }
        }
    }

    @Composable
    private fun LanguagePickerDialog(
        current: Language,
        onPick: (Language) -> Unit,
        onDismiss: () -> Unit,
    ) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    // ⚠ 2026-10-19：默认 Dialog 宽度会**超出圆屏**（直角卡片被圆边切）。
                    //   改成 0.86f + 圆角 + clip，完全收在圆内。
                    .fillMaxWidth(0.86f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(22.dp))
                    .background(Skin.Card)
                    .border(
                        1.dp,
                        Skin.CardLine,
                        androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
                    )
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(stringResource(R.string.language_title), fontSize = 10.sp, color = Skin.Muted)
                    Language.entries.forEach { lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(lang) }
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                if (lang == current) "✓" else " ",
                                fontSize = 11.sp,
                                color = Skin.Gold,
                                modifier = Modifier.padding(end = 5.dp),
                            )
                            Text(lang.displayName, fontSize = 10.sp, color = Skin.Ink)
                        }
                    }
                }
            }
        }
    }

    /**
     * 「更多功能」页的分组小标题（测量 / 报告 / 今天 / 其它）。
     *
     * **居中而不是左对齐**：主页那一列在 y≈30~50dp 处圆屏弦宽只有 130 出头，
     * 左对齐的分组标题（起点在 x=14dp 附近）会被圆切掉左半边。
     * 居中的两三个字则稳稳在可见区内。
     */
    @Composable
    private fun GroupLabel(text: String) {
        Text(
            text,
            fontSize = 9.5.sp,
            color = Skin.Muted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f).padding(top = 3.dp),
        )
    }

    // ───────────────────────── 压力 / 疲劳评分（模块 #12） ─────────────────────────

    /**
     * 压力 / 疲劳评分概览（手表端，只读）。
     *
     * 手表端**只显示结果**，不做任何计算 —— 分数由手机端算好推过来：
     * 它是"相对这个人自己的基线"，基线要用全部历史记录，而手表只知道自己在表上测的那部分。
     * 两端各算各的就会出现两个分数，而分数没有绝对标尺，用户看不出哪个是对的。
     *
     * ## 必须显示时效
     * 与其它推送都不同，这份摘要失效的原因不是"跨天"，而是**基线变了**（多测一次就变）。
     * 所以这里一定写出"算于多久以前"，超过 [Stress.SUMMARY_FRESH_MS] 还要明说可能过时 ——
     * 分数本身看不出新旧，不标出来就会被当成"现在的"。
     *
     * ## 什么时候都不给"压力很低"这种措辞
     * 分数低 = "比你自己平时低"，不等于"没问题"；分数高也不等于"有问题"。
     * 这里只说"低于平时 / 与平时相当 / 偏高 / 明显偏高"，与手机端同一套措辞。
     */
    @Composable
    private fun StressScreen(summary: StressSummaryMessage?, onBack: () -> Unit) {
        val now = remember { System.currentTimeMillis() }
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_e70e75f2), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            if (summary == null) {
                item {
                    Text(
                        stringResource(R.string.s_1a3810e3) +
                            stringResource(R.string.s_a03a4c67),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
                item {
                    PillButton(onClick = { bt.refreshFromPhone() }) {
                        Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
                    }
                }
            } else if (!summary.available) {
                item {
                    Text(
                        stringResource(R.string.s_1d4e6988),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                    )
                }
                item {
                    Text(
                        summary.reason.orEmpty(),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.84f),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.s_7fe5317f),
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                }
            } else {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.s_002b12bc), fontSize = 10.sp, color = Skin.Muted)
                        Text(
                            "${summary.stressScore}",
                            fontSize = 26.sp, fontWeight = FontWeight.Bold, color = StressColor,
                        )
                        Text(
                            Stress.levelLabel(summary.stressLevel),
                            fontSize = 11.sp, color = stressLevelColor(summary.stressLevel),
                        )
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.s_003c8cc9), fontSize = 10.sp, color = Skin.Muted)
                        val f = summary.fatigueScore
                        if (f != null) {
                            Text(
                                "$f",
                                fontSize = 20.sp, fontWeight = FontWeight.Bold, color = StressColor,
                            )
                            Text(
                                Stress.levelLabel(summary.fatigueLevel ?: ""),
                                fontSize = 10.sp,
                                color = stressLevelColor(summary.fatigueLevel ?: ""),
                            )
                        } else {
                            // 不给数时**必须出声**：写"待累积"而不是省略，
                            // 省略会被读成"疲劳很低"。
                            Text(stringResource(R.string.s_1943b3a9), fontSize = 14.sp, color = Skin.Muted)
                            Text(
                                stringResource(R.string.s_1d6af4f2),
                                fontSize = 9.sp, color = Skin.Muted,
                            )
                        }
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            stringResource(R.string.s_4599acab, summary.baselineCount),
                            fontSize = 9.sp, color = Skin.Muted,
                        )
                        Text(
                            summary.freshnessText(now),
                            fontSize = 9.sp,
                            color = if (Stress.isStale(summary.computedAt, now)) {
                                AccentOrange
                            } else {
                                Skin.Muted
                            },
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.s_c37c0ff2) +
                            stringResource(R.string.s_0ced40df),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.s_87b3163a),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            }

            item {
                PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) }
            }
        }
    }

    /**
     * 氧减指数（模块 #11）：一晚一次，睡眠呼吸相关的那个数。
     *
     * ## 只放结果，口径说明只留最关键的几句
     * 圆屏放不下"3% 判据 + 滚动基线 + 分母口径 + 分辨率边界 + 免责"那一整套，
     * 完整说明在手机端睡眠页的 ODI 卡上（用户原话：手表端不能只显示结果吗，详细的在手机端）。
     * 但**有几句话必须在**：
     *  · 这是哪一晚（ODI 只对那一晚成立）；
     *  · 它是筛查参考、不是诊断（分级叫"轻度/中度"很容易被读成诊断结论）；
     *  · 给不出时要说原因（"没监测到"与"没有氧减"是两件事）。
     */
    /**
     * 能量评分简页 —— **两个分并排**：本 App 自己算的（主）与三星的（对照）。
     *
     * 手表上**只读结果**，趋势与完整口径在手机端 —— 圆屏放不下一张有意义的趋势图，
     * 也不该在这里看。两份分都是手机端算好推过来的（手表上没有输入数据）。
     *
     * 初值来自本地缓存，所以「不是今天的」必须由日期文案带出来
     * （见 `EnergySummaryStore.scoreWithDay`）—— 这一页最容易被误读的就是
     * "把一个几天前的分数当成今天的状态"。
     */
    @Composable
    private fun EnergyScreen(summary: EnergySummaryMessage?, onBack: () -> Unit) {
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_6f2a56c8), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            if (summary == null) {
                item {
                    Text(
                        stringResource(R.string.s_5cc1ab3d) +
                            stringResource(R.string.s_a03a4c67),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
                item {
                    PillButton(onClick = { bt.refreshFromPhone() }) {
                        Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
                    }
                }
            } else {
                // ── 主：本 App 自己算的 ──
                if (summary.ownAvailable) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(stringResource(R.string.s_0d10df55), fontSize = 9.sp, color = Skin.Muted)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${summary.ownScore}",
                                    fontSize = 36.sp, fontWeight = FontWeight.Bold,
                                    color = EnergyColor,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.s_00005206), fontSize = 11.sp, color = Skin.Muted,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            Text(
                                summary.ownCaption(todayEpochDay()),
                                fontSize = 10.sp, color = Skin.Muted,
                            )
                        }
                    }
                    if (summary.ownNote.isNotEmpty()) {
                        item {
                            Text(
                                summary.ownNote,
                                fontSize = 9.sp, color = AccentOrange,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(0.78f),
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            stringResource(R.string.s_e3f52c9e),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                        )
                    }
                    item {
                        Text(
                            summary.ownNote.ifEmpty { stringResource(R.string.s_72f8d6a1) },
                            fontSize = 9.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.78f),
                        )
                    }
                }

                // ── 副：三星那一侧（对照，不求一致）──
                item {
                    Text(
                        stringResource(R.string.s_15937be8) + summary.samsungText(),
                        fontSize = 10.sp,
                        color = if (summary.available) Skin.Muted else Skin.Muted,
                    )
                }
                if (!summary.available) {
                    item {
                        Text(
                            stringResource(R.string.s_31cc61e2),
                            fontSize = 9.sp, color = Skin.Muted,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(0.78f),
                        )
                    }
                }
                item {
                    Text(
                        stringResource(R.string.s_39893596),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            }

            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /**
     * 睡眠呼吸暂停简页。
     *
     * ⚠ 措辞由 `Apnea`（core）统一维护：**只说"迹象"，不下"有/没有"的结论**。
     * 手机与手表共用同一套文案，就是为了避免同一份数据在两个地方说法不同。
     */
    @Composable
    private fun ApneaScreen(summary: ApneaSummaryMessage?, onBack: () -> Unit) {
        val lang = rememberLang()
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_460052a1), fontSize = 13.sp, fontWeight = FontWeight.Bold) }

            if (summary == null) {
                item {
                    Text(
                        stringResource(R.string.s_43d1a87b) +
                            stringResource(R.string.s_3ca514cd),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.84f),
                    )
                }
                item {
                    PillButton(onClick = { bt.refreshFromPhone() }) {
                        Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
                    }
                }
            } else {
                item {
                    Text(summary.glanceText(), fontSize = 11.sp, color = Skin.Muted)
                }
                item {
                    Text(
                        summary.resultText(),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = if (Apnea.isAlarming(summary.sign)) AccentOrange else Skin.Ink,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
                item {
                    Text(
                        Disclaimer.apnea(lang),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            }

            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    @Composable
    private fun OdiScreen(summary: OdiSummaryMessage?, onBack: () -> Unit) {
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                // 长标题（"Oxygen desaturation index (ODI)"）必须限宽 + 居中，
                // 否则靠近圆顶时左右都被切掉（用户实测截图）。
                Text(
                    stringResource(R.string.s_62585af5),
                    fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Skin.Ink,
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp,
                    modifier = Modifier.fillMaxWidth(0.86f),
                )
            }

            if (summary == null) {
                item {
                    Text(
                        stringResource(R.string.s_956b0204) +
                            stringResource(R.string.s_b2c8a436),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
                item {
                    PillButton(onClick = { bt.refreshFromPhone() }) {
                        Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
                    }
                }
            } else if (!summary.available) {
                item {
                    Text(
                        stringResource(R.string.s_b5ba063f),
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentOrange,
                    )
                }
                item {
                    Text(
                        summary.reason.orEmpty(),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.84f),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.s_64e54e00),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            } else {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val night = summary.nightLabel(todayEpochDay())
                        if (night.isNotEmpty()) {
                            Text(night, fontSize = 10.sp, color = Skin.Muted)
                        }
                        Text(
                            Odi.formatOdi(summary.odiPerHour),
                            fontSize = 22.sp, fontWeight = FontWeight.Bold, color = OdiColor,
                        )
                        Text(
                            Odi.bandLabel(summary.band),
                            fontSize = 11.sp, color = Color(Odi.bandColorArgb(summary.band)),
                        )
                    }
                }
                item {
                    Text(
                        summary.nightDetailText(),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
                item {
                    Text(
                        stringResource(R.string.s_8f18001f) +
                            stringResource(R.string.s_b2201a50),
                        fontSize = 9.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.78f),
                    )
                }
            }

            item {
                PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) }
            }
        }
    }

    // ───────────────────────── 经期（模块 #13） ─────────────────────────

    /**
     * 经期概览（手表端，只读）。
     *
     * 经期数据只在手机端录入/导入（没有传感器能测），手机算好摘要经蓝牙推一行，
     * 手表只显示：周期第几天 / 距下次 / 是否易孕 / 平均周期。**不做录入交互**，
     * 也不显示周期长度图（那是手机端的事，圆屏放不下）。
     *
     * 没有数据时给的是"去手机端开启"，而不是"手表没测到" —— 与睡眠页同一纪律。
     */
    @Composable
    private fun CycleScreen(summary: CycleSummaryMessage?, onBack: () -> Unit) {
        val lang = rememberLang()
        val today = remember { java.time.LocalDate.now().toEpochDay() }
        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_00414b0c), fontSize = 14.sp, fontWeight = FontWeight.Bold) }

            if (summary == null || !summary.available) {
                item {
                    Text(
                        stringResource(R.string.s_89c8e318) +
                            stringResource(R.string.s_773a33e9),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
                item {
                    PillButton(onClick = { bt.refreshFromPhone() }) {
                        Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
                    }
                }
            } else {
                val last = summary.lastStartEpochDay
                val cycleDay = last?.let { (today - it + 1).toInt() }
                val next = summary.nextStartEpochDay
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (cycleDay != null) {
                            Text(stringResource(R.string.s_92933427, cycleDay), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(summary.glanceText(today), fontSize = 12.sp, color = Skin.Muted)
                        if (next != null) {
                            Text(
                                stringResource(R.string.s_31e9f876, Cycle.dateShort(next)) +
                                    stringResource(R.string.s_fca1407d, summary.spreadDays),
                                fontSize = 11.sp,
                                color = Color(0xFFB33563),
                            )
                        }
                    }
                }
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (summary.medianCycleDays > 0) {
                            Text(stringResource(R.string.s_31a07823, summary.medianCycleDays),
                                fontSize = 10.sp, color = Skin.Muted)
                        }
                        if (summary.inFertileWindow == true) {
                            Text(stringResource(R.string.s_4768cbc4),
                                fontSize = 10.sp, color = Color(0xFF4DD0E1))
                        }
                    }
                }
                item {
                    Text(
                        Disclaimer.cycle(lang),
                        fontSize = 8.sp,
                        color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
            }

            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    // ───────────────────────── 睡眠 ─────────────────────────

    /**
     * 睡眠报告（简化版，适配圆屏）。
     *
     * 手表端**不自己读三星健康** —— Wear OS 侧没有三星健康 Data SDK，
     * 这份数据全部由手机读取后经蓝牙推送（见 `BluetoothSync.nativeSleepNight`）。
     * 所以没有数据时要给出的是"去手机端授权/连接"，而不是"手表没测到"。
     *
     * 保留的基本内容：实际睡眠时长、入睡-起床、睡眠得分、睡眠阶段图与四阶段占比、
     * 睡眠期心率与血氧。更细的解读放在手机端（手表屏太小）。
     */
    @Composable
    private fun SleepScreen(onBack: () -> Unit, onOpenStage: () -> Unit) {
        val nightState by bt.nativeSleepNight.collectAsState()
        val night = nightState
        if (night == null) {
            SclPage(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Text(stringResource(R.string.s_1e76d394), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                item {
                    Text(
                        stringResource(R.string.s_0b1f15a8) +
                            stringResource(R.string.s_8de2e15a),
                        fontSize = 10.sp, color = Skin.Muted,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(0.85f),
                    )
                }
                item {
                    PillButton(onClick = { bt.refreshFromPhone() }) {
                        Text(stringResource(R.string.s_94a2ff1c), fontSize = 11.sp)
                    }
                }
                item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
            }
            return
        }

        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        val totals = night.stageTotals()
        val sum = totals.values.sum().coerceAtLeast(1L)
        val actual = night.actualSleepMs()

        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_1e76d394), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(wearFmtDuration(actual), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.s_24f6d380), fontSize = 9.sp, color = Skin.Muted)
                    Text(
                        "${tsFmt.format(Date(night.startMs))}-${tsFmt.format(Date(night.endMs))}" +
                            " · ${wearFmtDuration(night.durationMs)}",
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                    night.score?.let {
                        Text(
                            stringResource(R.string.s_6babf7d9, it),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            color = Color(0xFF4DD0E1),
                        )
                    }
                }
            }

            // 睡眠阶段图：点进详情看时间段明细
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(0.78f).clickable { onOpenStage() },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s_1e914e32), fontSize = 11.sp, color = Skin.Muted, modifier = Modifier.weight(1f))
                        Text("›", fontSize = 14.sp, color = Skin.Muted)
                    }
                    Spacer(Modifier.height(4.dp))
                    SleepHypnogram(night.stages, night.startMs, night.endMs, 60.dp)
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tsFmt.format(Date(night.startMs)), fontSize = 8.sp, color = Skin.Muted)
                        Text(tsFmt.format(Date(night.endMs)), fontSize = 8.sp, color = Skin.Muted)
                    }
                }
            }

            items(SleepStageOrder.size) { i ->
                val (kind, name) = SleepStageOrder[i]
                val ms = totals[kind] ?: 0L
                val pct = ms * 100f / sum
                Column(Modifier.fillMaxWidth(0.78f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(sleepStageColor(kind)))
                        Spacer(Modifier.size(5.dp))
                        Text(stringResource(name), fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${pct.roundToInt()}%",
                            fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            color = sleepStageColor(kind),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(wearFmtDuration(ms), fontSize = 9.sp, color = Skin.Muted)
                    }
                    Spacer(Modifier.height(3.dp))
                    SleepStageBar(pct, sleepStageColor(kind))
                    SleepStageTypical[kind]?.let { t ->
                        Text(
                            stringResource(R.string.s_8e28d2d1, t.first.roundToInt(), t.second.roundToInt()),
                            fontSize = 8.sp, color = Skin.Muted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            item {
                Column(Modifier.fillMaxWidth(0.78f)) {
                    Text(
                        stringResource(R.string.s_3f4a311c, dash(night.hrAvg), dash(night.hrMin)),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                    Text(
                        stringResource(R.string.s_87639689, dash(night.spo2Avg), dash(night.spo2Min)),
                        fontSize = 10.sp, color = Skin.Muted,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.s_a666bb6b),
                    fontSize = 8.sp, color = Skin.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }
            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /**
     * 睡眠阶段详情（手表版）：睡眠图 + 四阶段占比 + 分段明细（每段的时间端与时长）。
     *
     * "分段明细"就是点睡眠图想看的东西：几点到几点是深睡、几点醒过，
     * 这些在图上只能看出大概位置，列出时间段才可核对。
     */
    @Composable
    private fun SleepStageScreen(onBack: () -> Unit) {
        val nightState by bt.nativeSleepNight.collectAsState()
        val night = nightState
        if (night == null) {
            SclPage(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                item { Text(stringResource(R.string.s_1e914e32), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                item { Text(stringResource(R.string.s_d2a3eedf), fontSize = 11.sp, color = Skin.Muted) }
                item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
            }
            return
        }

        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        val totals = night.stageTotals()
        val sum = totals.values.sum().coerceAtLeast(1L)
        val segments = night.stages.filter { it.kind != SleepStageKind.UNKNOWN }

        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_1e914e32), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    SleepHypnogram(night.stages, night.startMs, night.endMs, 84.dp)
                    Spacer(Modifier.height(2.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (k in 0..3) {
                            Text(
                                tsFmt.format(Date(night.startMs + (night.endMs - night.startMs) * k / 3)),
                                fontSize = 8.sp, color = Skin.Muted,
                            )
                        }
                    }
                }
            }
            items(SleepStageOrder.size) { i ->
                val (kind, name) = SleepStageOrder[i]
                val ms = totals[kind] ?: 0L
                val pct = ms * 100f / sum
                Column(Modifier.fillMaxWidth(0.78f)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(sleepStageColor(kind)))
                        Spacer(Modifier.size(5.dp))
                        Text(
                            stringResource(name),
                            fontSize = 11.sp, fontWeight = FontWeight.Bold, color = sleepStageColor(kind),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(wearFmtDuration(ms), fontSize = 10.sp, modifier = Modifier.weight(1f))
                        Text("${pct.roundToInt()}%", fontSize = 10.sp, color = Skin.Muted)
                    }
                    Spacer(Modifier.height(3.dp))
                    SleepStageBar(pct, sleepStageColor(kind))
                    SleepStageTypical[kind]?.let { t ->
                        Text(
                            stringResource(R.string.s_8e28d2d1, t.first.roundToInt(), t.second.roundToInt()),
                            fontSize = 8.sp, color = Skin.Muted,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
            item {
                Text(
                    stringResource(R.string.s_2136daa8),
                    fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(0.78f),
                )
            }
            // 上限 80 段：正常一晚合并后是十几到几十段，真超出说明数据异常，
            // 与其把圆屏拉成无底洞，不如截断并说明。
            items(segments.size.coerceAtMost(80)) { i ->
                val s = segments[i]
                Row(
                    Modifier.fillMaxWidth(0.78f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(7.dp).background(sleepStageColor(s.kind)))
                    Spacer(Modifier.size(5.dp))
                    Text(
                        "${tsFmt.format(Date(s.startMs))}-${tsFmt.format(Date(s.endMs))}",
                        fontSize = 9.sp, color = Skin.Muted,
                    )
                    Spacer(Modifier.size(5.dp))
                    val stageName = SleepStageOrder.firstOrNull { it.first == s.kind }?.second
                    Text(
                        if (stageName != null) stringResource(stageName) else "-",
                        fontSize = 9.sp, color = Skin.Muted, modifier = Modifier.weight(1f),
                    )
                    Text(wearFmtDuration(s.durationMs), fontSize = 9.sp, color = Skin.Muted)
                }
            }
            if (segments.size > 80) {
                item {
                    Text(
                        stringResource(R.string.s_3c45a26d, segments.size),
                        fontSize = 8.sp, color = Skin.Muted,
                    )
                }
            }
            item {
                Text(
                    stringResource(R.string.s_0b8fb1d3),
                    fontSize = 8.sp, color = Skin.Muted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.8f),
                )
            }
            item { PillButton(onClick = onBack) { Text(stringResource(R.string.s_0049f05a), fontSize = 11.sp) } }
        }
    }

    /**
     * 睡眠图（hypnogram）：横轴时间、纵轴四个阶段层级。
     * 与手机端同一套画法，只是尺寸更小、不含斜纹参照。
     */
    @Composable
    private fun SleepHypnogram(
        stages: List<SleepStageDto>,
        startMs: Long,
        endMs: Long,
        height: androidx.compose.ui.unit.Dp,
    ) {
        if (endMs <= startMs) return
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val span = (endMs - startMs).toFloat().coerceAtLeast(1f)
            val levels = listOf(
                SleepStageKind.AWAKE, SleepStageKind.REM,
                SleepStageKind.LIGHT, SleepStageKind.DEEP,
            )
            val rowH = size.height / levels.size
            val barH = (rowH * 0.55f).coerceAtLeast(1.5f)
            levels.forEachIndexed { i, _ ->
                val cy = rowH * i + rowH / 2f
                drawLine(
                    SectionLine,
                    Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1f,
                )
            }
            var prevX = -1f
            var prevY = -1f
            for (s in stages) {
                val li = levels.indexOf(s.kind)
                if (li < 0) continue
                val x0 = ((s.startMs - startMs) / span) * size.width
                val x1 = ((s.endMs - startMs) / span) * size.width
                val cy = rowH * li + rowH / 2f
                if (prevY >= 0f) {
                    drawLine(
                        Skin.Muted,
                        Offset(prevX, prevY), Offset(x0, cy), strokeWidth = 1.5f,
                    )
                }
                drawRoundRect(
                    color = sleepStageColor(s.kind),
                    topLeft = Offset(x0, cy - barH / 2f),
                    size = Size((x1 - x0).coerceAtLeast(1.2f), barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f),
                )
                prevX = x1
                prevY = cy
            }
        }
    }

    /** 单阶段占比条（手表版）：只有实测填充，典型范围改用文字标注。 */
    @Composable
    private fun SleepStageBar(pct: Float, color: Color) {
        Canvas(Modifier.fillMaxWidth().height(7.dp)) {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(Skin.Hairline, Offset.Zero, size, r)
            val w = (pct / 100f).coerceIn(0f, 1f) * size.width
            if (w > 0f) {
                drawRoundRect(color, Offset.Zero, Size(w.coerceAtLeast(size.height), size.height), r)
            }
        }
    }

    @Composable
    private fun AddBpScreen(
        onSave: (Int, Int, Int, String?) -> Unit,
        onCancel: () -> Unit,
    ) {
        var sys by remember { mutableIntStateOf(120) }
        var dia by remember { mutableIntStateOf(80) }
        var pulse by remember { mutableIntStateOf(70) }
        // 测量姿势（`Bp.Posture`）。⚠ 默认 **null = 未选**：
        //   替用户默认成"坐姿"比留空更糟 —— 留空只是缺一格（可识别），
        //   而"躺着测却忘改"会**污染分层**（错误不可识别）。理由见 `Bp.Posture` 的类注释。
        var posture by remember { mutableStateOf<String?>(null) }

        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_0efee0b1), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            // ── 测量姿势（2026-10-03）──
            //
            // ⚠⚠ **必须在第一屏就看得见**。原来这里是「竖排三个全宽 chip、放在步进器之后」，
            //   结果用户输完血压直接点保存、**根本没滚到它** ⇒ 样本一律「未记录」
            //   （2026-10-03 用户实测：明明录了血压也测了心电，配对样本里显示的却是未记录）。
            //   ⇒ 改成**一行三个**、上移到标题正下方 —— 三个双字词的宽度圆屏放得下。
            // ⚠ 仍**默认不选**（`null`）：替用户默认成"坐姿"会制造"躺着测却记成坐姿"的
            //   假数据，而分层的前提是"这一组体位确定是同一个" —— 缺值可识别，错值不可识别。
            item { Text(stringResource(R.string.s_cb7468ec), fontSize = 10.sp, color = Skin.Muted) }
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    for (p in Bp.Posture.SELECTABLE) {
                        val on = posture == p
                        Box(
                            modifier = Modifier
                                .background(
                                    // ⚠ 未选中原来是**近黑底 + 墨字**（黑底黑字，看不见）。
                                    //   现在：未选中＝奶白底 + 墨字；选中＝金底 + 奶白字。
                                    if (on) Skin.Gold else Skin.Btn,
                                    RoundedCornerShape(percent = 50),
                                )
                                .border(
                                    1.dp,
                                    if (on) Skin.Gold else Skin.Btn,
                                    RoundedCornerShape(percent = 50),
                                )
                                .clickable { posture = if (on) null else p }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                postureLabel(p),
                                fontSize = 11.sp,
                                color = Skin.OnBtn,
                            )
                        }
                    }
                }
            }
            item { StepperRow(stringResource(R.string.s_1ac2166c), sys) { if (it in 60..250) sys = it } }
            item { StepperRow(stringResource(R.string.s_2240498d), dia) { if (it in 40..150) dia = it } }
            item { StepperRow(stringResource(R.string.s_00426baa), pulse) { if (it in 30..220) pulse = it } }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSave(sys, dia, pulse, posture) }) { Text(stringResource(R.string.s_0029396f)) }
                    PillButton(onClick = onCancel) { Text(stringResource(R.string.s_002b540a)) }
                }
            }
        }
    }

    /**
     * 体位常量 → 显示串。
     *
     * ⚠ 匹配用**常量值本身**，不是翻译后的串 —— 拿显示串去比会在英文/韩文下永远落 `else`
     *   （第 ⑦ 条踩过）。
     */
    @Composable
    private fun postureLabel(p: String?): String = when (p) {
        Bp.Posture.SITTING -> stringResource(R.string.s_002d07ef)
        Bp.Posture.LYING -> stringResource(R.string.s_0030e393)
        Bp.Posture.STANDING -> stringResource(R.string.s_003f57d6)
        else -> stringResource(R.string.s_1b4b78df)
    }

    @Composable
    private fun StepperRow(label: String, value: Int, onChange: (Int) -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 步进器要窄：一个 Row 里放两个半屏宽的按钮会挤爆（见 PillButton 的注释）。
            PillButton(
                onClick = { onChange(value - 1) },
                modifier = Modifier.size(54.dp, 44.dp),
            ) { Text("-") }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, fontSize = 10.sp, color = Skin.Muted)
                Text("$value", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Skin.Ink)
            }
            PillButton(
                onClick = { onChange(value + 1) },
                modifier = Modifier.size(54.dp, 44.dp),
            ) { Text("+") }
        }
    }

    @Composable
    private fun AddEcgScreen(
        currentBpm: Int?,
        onSave: (String, Int?) -> Unit,
        onCancel: () -> Unit,
    ) {
        val options = listOf("窦性心律", "房颤（AFib）", "不确定")
        var selected by remember { mutableIntStateOf(-1) }

        SclPage(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text(stringResource(R.string.s_1e80f996), fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            item { Text(stringResource(R.string.s_15bb2641), fontSize = 11.sp, color = Skin.Muted) }
            items(options.size) { i ->
                Chip(
                    onClick = { selected = i },
                    label = {
                        Text(
                            (if (selected == i) "✓ " else "") + options[i],
                            color = if (selected == i) Color(0xFF4DD0E1) else Skin.Ink,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(0.85f),
                )
            }
            item {
                Text(
                    currentBpm?.let { stringResource(R.string.s_64d90958, it) } ?: stringResource(R.string.s_8bc99a1a),
                    fontSize = 10.sp, color = Skin.Muted,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { if (selected >= 0) onSave(options[selected], currentBpm) },
                        modifier = Modifier.background(Color.Transparent),
                    ) { Text(stringResource(R.string.s_0029396f)) }
                    PillButton(onClick = onCancel) { Text(stringResource(R.string.s_002b540a)) }
                }
            }
        }
    }
}
