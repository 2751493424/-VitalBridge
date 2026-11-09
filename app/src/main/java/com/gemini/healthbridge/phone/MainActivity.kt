package com.gemini.healthbridge.phone

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.drawText
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.gemini.healthbridge.core.Activity
import com.gemini.healthbridge.core.ActivitySource
import com.gemini.healthbridge.core.Backfill
import com.gemini.healthbridge.core.BiaSource
import com.gemini.healthbridge.core.BiaProfileDto
import com.gemini.healthbridge.core.BodyComposition
import com.gemini.healthbridge.core.Bp
import com.gemini.healthbridge.core.BpPredict
import com.gemini.healthbridge.core.Cycle
import com.gemini.healthbridge.core.Frame
import com.gemini.healthbridge.core.FrameKinds
import com.gemini.healthbridge.core.Announcement
import com.gemini.healthbridge.core.Apnea
import com.gemini.healthbridge.core.ActivityNotes
import com.gemini.healthbridge.core.DietNotes
import com.gemini.healthbridge.core.Disclaimer
import com.gemini.healthbridge.core.EcgNote
import com.gemini.healthbridge.core.Energy
import com.gemini.healthbridge.core.Extras
import com.gemini.healthbridge.core.Glucose
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.isBlankText
import com.gemini.healthbridge.core.SleepHours
import com.gemini.healthbridge.core.GoalNotes
import com.gemini.healthbridge.core.Ihrn
import com.gemini.healthbridge.phone.ui.HourDetailDialog
import com.gemini.healthbridge.phone.ui.appColorScheme
import com.gemini.healthbridge.phone.ui.AppStat
import com.gemini.healthbridge.phone.ui.AppCard
import com.gemini.healthbridge.phone.ui.AppHint
import com.gemini.healthbridge.phone.ui.AppSoftBlock
import com.gemini.healthbridge.phone.ui.AppSurface
import com.gemini.healthbridge.phone.ui.OverviewData
import com.gemini.healthbridge.phone.ui.OverviewScreen
import com.gemini.healthbridge.phone.ui.ThemeBackdropLayer
import com.gemini.healthbridge.phone.ui.backdropTopColor
import com.gemini.healthbridge.phone.ui.ThemeScreen
import com.gemini.healthbridge.phone.ui.appPageBg
import com.gemini.healthbridge.phone.ui.AppTile
import com.gemini.healthbridge.phone.ui.AppHairline
import com.gemini.healthbridge.phone.ui.AppTextMuted
import com.gemini.healthbridge.phone.ui.AppLegend
import com.gemini.healthbridge.phone.ui.AppKpi
import androidx.compose.material3.ButtonDefaults
import com.gemini.healthbridge.phone.ui.AppPrimaryButton
import com.gemini.healthbridge.phone.ui.AppOutlineButton
import com.gemini.healthbridge.phone.ui.AppShapes
import com.gemini.healthbridge.phone.ui.AppBg
import com.gemini.healthbridge.phone.ui.AppControlFill
import com.gemini.healthbridge.phone.ui.AppCardBg
import com.gemini.healthbridge.phone.ui.AppCardBorder
import com.gemini.healthbridge.phone.ui.AppDivider
import com.gemini.healthbridge.phone.ui.AppGridLine
import com.gemini.healthbridge.phone.ui.AppTextSecondary
import com.gemini.healthbridge.phone.ui.AppTextPrimary
import com.gemini.healthbridge.phone.ui.AppMutedFill
import com.gemini.healthbridge.core.HourBucket
import com.gemini.healthbridge.core.AnnouncementUpdate
import com.gemini.healthbridge.core.Vitals
import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.HrvSource
import com.gemini.healthbridge.core.Language
import com.gemini.healthbridge.core.Ptt
import com.gemini.healthbridge.core.PttNote
import com.gemini.healthbridge.core.MfBia
import com.gemini.healthbridge.core.Odi
import com.gemini.healthbridge.core.Ppg
import com.gemini.healthbridge.core.Resp
import com.gemini.healthbridge.core.SleepNightDto
import com.gemini.healthbridge.core.sleepStageHistory
import com.gemini.healthbridge.core.SleepStageDto
import com.gemini.healthbridge.core.SleepStageKind
import com.gemini.healthbridge.core.Spo2
import com.gemini.healthbridge.core.Stress
import com.gemini.healthbridge.core.TempSource
import com.gemini.healthbridge.core.Temperature
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.ActivityEntity
import com.gemini.healthbridge.core.db.BloodPressureEntity
import com.gemini.healthbridge.core.db.BodyCompositionEntity
import com.gemini.healthbridge.core.db.EcgRecordEntity
import com.gemini.healthbridge.core.db.EcgWaveEntity
import com.gemini.healthbridge.core.db.DailyVitalsEntity
import com.gemini.healthbridge.core.db.HeartRateEntity
import com.gemini.healthbridge.core.db.HrvSessionEntity
import com.gemini.healthbridge.core.db.MenstrualRecordEntity
import com.gemini.healthbridge.core.Arrhythmia
import com.gemini.healthbridge.core.db.ActivityDayEntity
import com.gemini.healthbridge.core.db.EnergyScoreEntity
import com.gemini.healthbridge.core.db.MfBiaEntity
import com.gemini.healthbridge.core.db.PpgWaveEntity
import com.gemini.healthbridge.core.db.SleepApneaEntity
import com.gemini.healthbridge.core.db.SleepNightEntity
import com.gemini.healthbridge.core.db.SleepOdiEntity
import com.gemini.healthbridge.core.db.TemperatureEntity
import com.gemini.healthbridge.core.db.ecgRegularFlag
import com.gemini.healthbridge.core.db.toEntity
import com.gemini.healthbridge.core.db.toSleepNightDto
import com.gemini.healthbridge.core.db.toEntry
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 心率柱状图配色：清醒（粉红，与三星健康一致）/ 睡眠（靛蓝）。 */
private val HrAwakeColor = Color(0xFFE91E63)
private val HrSleepColor = Color(0xFF5C6BC0)

/**
 * 血氧柱状图配色：清醒 / 睡眠。
 * 用紫色系（与那张血氧折线图同色族）而不是复用心率的粉红+靛蓝，
 * 免得同一屏里两个量的柱子颜色一样、用户以为在比同一个指标。
 */
private val Spo2AwakeColor = Color(0xFFB79BFF)
private val Spo2SleepColor = Color(0xFF4DD0C4)

/**
 * 睡眠页「逐晚记录」趋势图配色（2026-10-10）。
 *
 * 一根柱子由**两段叠成**：深色 = 实际睡眠（总时长 − 清醒段），
 * 浅色 = 中间醒着的那一段。两段相加 = 入睡→起床的总时长。
 *
 * ⚠ 为什么不用一条柱子画总时长、再单独标一个"实际睡眠"数字：
 *   用户真正在意的"睡得够不够"看的是**深色那段**，而"躺了多久"是浅+深。
 *   两段叠在一根柱子上，一眼就能看出"躺了 9 小时但只睡了 6 小时"这种差别，
 *   换成两个数字就得自己在脑子里做减法。
 */
private val SleepActualColor = Color(0xFF5E35B1)
private val SleepAwakeColor = Color(0xFFD1C4E9)

/**
 * 体温配色（暖色系）。
 *
 * 同样刻意不复用心率/血氧的颜色：体温页里会**同时出现两条完全不同的曲线**
 * （手表点检的绝对皮肤温度、三星健康的夜间值），所以这两条线还必须互相区分：
 *  · [TempPointColor] 手表点检（绝对 ℃）—— 橙红，与"温度"的直觉一致；
 *  · [TempNightColor] 三星健康夜间值 —— 灰蓝，明确表示"这是另一套口径"；
 *  · [TempCoreColor] 核心体温**估算**（模块 #10）—— 紫色，既不属于上面任何一路，
 *    也不落在"偏低蓝/正常绿/偏高橙"那套里 —— 它是个推算值，不能用配色暗示"正常/异常"。
 */
private val TempPointColor = Color(0xFFFF7043)
private val TempNightColor = Color(0xFF78909C)
private val TempCoreColor = Color(0xFFB79BFF)

/**
 * 夜间腕温**偏移**的主色（青绿）。
 *
 * 刻意在体温这一族里再补一个没用过的颜色：点检是橙红、三星夜间是蓝灰、核心体温是紫、
 * 偏低蓝 / 常见绿 / 偏高橙。偏移量是**另一种东西**（差值而不是温度），给它自己的颜色，
 * 而且避开红/绿，免得被读成"正常 / 异常"的判定。
 */
private val TempDeviationColor = Color(0xFF00897B)

/** 位置感配色（偏低冷蓝 / 常见范围绿 / 偏高暖橙）。只影响文字与游标颜色，不是医学判定。 */
private val TempLowColor = Color(0xFF42A5F5)
private val TempNormalColor = Color(0xFF43A047)
private val TempHighColor = Color(0xFFFFA94D)

/**
 * HRV（模块 #9）—— 青绿。
 *
 * 刻意用**与心电三档结论色（绿/橙/红）不同族**的颜色：HRV 不是"正常/异常"的判定，
 * 它只有相对自己的高低。用结论色会让人把"RMSSD 偏低"直接读成"心电出问题了"。
 * 手表端同款取值（[HrvColor] 在两个模块里是同一个色号）。
 */
/**
 * 功能宫格里的一个功能（模块 #15 多语言改造，2026-09-29）。
 *
 * ## 为什么要有这个枚举
 *
 * 改造前，功能名（"心率""血压"…）**同时充当三种角色**：
 *   ① 宫格卡片与详情页标题的**展示文字**；
 *   ② `when (openName)` 的**分派键**；
 *   ③ `SYNC_ON_OPEN` 集合的**成员判据**。
 *
 * 角色 ②③ 都是"当值用" —— 一旦把角色 ① 翻译掉，②③ 立刻失配：
 * 宫格点了没反应、`SYNC_ON_OPEN` 永远命中不了（**都不报错**）。
 * 所以把"身份"与"显示名"拆开：枚举常量是身份（永不翻译），
 * [title] 是显示名（走资源系统，随语言变）。
 */
private enum class Feature {
    HR, BP, ECG, SPO2, SLEEP, TEMP, BIA, ENERGY, ACTIVITY, DIET, GLUCOSE, IHRN, CYCLE,
}

/**
 * 功能的**显示名**（随语言变）。身份是枚举常量本身，这里只负责取文案。
 *
 * ⚠ 不要在别处再写一份 `when` 把 Feature 映射成名字 —— 那是"两份定义必然漂移"的老坑。
 */
@androidx.compose.runtime.Composable
private fun Feature.title(): String = androidx.compose.ui.res.stringResource(
    when (this) {
        Feature.HR -> R.string.feat_hr
        Feature.BP -> R.string.feat_bp
        Feature.ECG -> R.string.feat_ecg
        Feature.SPO2 -> R.string.feat_spo2
        Feature.SLEEP -> R.string.feat_sleep
        Feature.TEMP -> R.string.feat_temp
        Feature.BIA -> R.string.feat_bia
        Feature.ENERGY -> R.string.feat_energy
        Feature.ACTIVITY -> R.string.feat_activity
        Feature.DIET -> R.string.feat_diet
        Feature.GLUCOSE -> R.string.feat_glucose
        Feature.IHRN -> R.string.feat_ihrn
        Feature.CYCLE -> R.string.feat_cycle
    }
)

/**
 * 打开这些功能页时补一次三星健康同步（60 秒内刚同步过就跳过）。
 *
 * ⚠ 这里用 [Feature] 而不是名字 —— 名字会随语言变，用它当键**必然**在非中文下失配。
 *
 * ⚠ 「血压」是 2026-09-29 补进来的，它是**修回文档承诺的行为**而不是新增行为：
 *   手机端两处说明（同步诊断页、关于文字）早就写着"心率/血压/血氧/睡眠会自动读取，
 *   不需要手动导入"，`syncToday()` 也确实读血压（近 30 天）—— 但血压页不在这个集合里，
 *   于是用户必须在**别的**页面上停留一次，自己的血压才会出现。
 */
private val SYNC_ON_OPEN = setOf(Feature.BP, Feature.SPO2, Feature.SLEEP, Feature.TEMP, Feature.BIA)

/**
 * 血压页「预估历史」显示最近几条。
 *
 * ⚠ 每一行要查一次配对窗口（3 条 SQL，见 [BpCalibration.historyInputs]），
 *   所以这个数**同时是性能上限**，不是随便定的。
 * ⚗ 20 条 ≈ 一个月内的量级，够用户回看，又不至于让打开这一页变慢。
 */
private const val BP_HISTORY_ROWS = 20

/** 宫格卡片上的短时间格式。**不带年份与秒** —— 卡片只有一行，需要的是"多久以前的"。 */
private val cardFmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())

private val HrvColor = Color(0xFF00897B)

/**
 * 脉搏到达时间（PAT，模块 #14）的配色。与 [HrvColor] 刻意不同色：
 * 两者都由**同一次心电**产出，同色会让人以为它们是同一个量的两种说法。
 */
private val PatColor = Color(0xFFE64A19)

/** HRV 用来提示"测量条件不理想 / 本次没给结果"的警示色（橙，与体温的偏高色同族但语义不同）。 */
private val HrvWarnColor = Color(0xFFFFB74D)

/** 多频阻抗的主色（琥珀）：与体成分的紫色分开 —— 两个独立采集器，不是同一份数据的两种展示。 */
private val MfBiaAccent = Color(0xFFFFB300)

/** 多频阻抗给不出结论时的警示色（与 HRV 警示同色，语义都是"这一条要留意"）。 */
private val MfBiaWarnColor = Color(0xFFFFB74D)

/**
 * 「用户」页里**需要你处理**的值（未授权 / 资料读不到）。
 *
 * 与项目里其它几处警示色同色，语义也一样是「这一条要留意」。
 * 但**只用来标状态，不用来标"重要程度"** —— 后者会让整页变成调色板，
 * 结果反而看不出哪一行真的要处理。
 */
private val SettingsWarnColor = Color(0xFFFFB74D)

/**
 * 能量评分的主色（深青）。
 *
 * 与项目里已有的几个蓝/青刻意拉开：心电是蓝、血氧是天蓝、HRV 是青绿，
 * 再加一个相近的蓝会让人以为它们是同一类指标。
 */
private val EnergyScoreColor = Color(0xFF00838F)

/**
 * 「能量得分因素」宫格的三档配色。边界与总分共用同一套（<50 偏低 / 50~74 一般 / ≥75 良好）——
 * **不另立一套词**，否则用户会看到"总分良好、某一项优秀"这种没法对照的组合。
 */
private val FactorGoodColor = Color(0xFF6BCB77)
private val FactorFairColor = Color(0xFF8E7DA3)

/**
 * 扩展指标（2026-09-23）的配色。
 *
 * 四个都刻意避开已有色：活动绿、饮食棕、血糖玫红、心律不齐红
 * （心律不齐是最需要注意的一项，用最重的颜色 —— 但它与 SettingsWarnColor 的橙分开，
 * 因为"需要注意"与"这里出错了"是两回事）。
 */
private val ActivityColor = Color(0xFF6BCB77)
private val DietColor = Color(0xFF6D4C41)
private val GlucoseColor = Color(0xFFAD1457)
private val IhrnColor = Color(0xFFC62828)

/**
 * 压力 / 疲劳评分（模块 #12）—— 靛蓝，主数字**恒为这个色**。
 *
 * 为什么不复用 HRV 的青绿：两者同源但不是同一个东西 —— HRV 是测量量（ms），
 * 评分是折算量（分）。同色会让人以为"又一个 HRV 指标"而被当成测量值去比较。
 *
 * 分级色只用在**标签文字与进度条**上，主数字不变色：
 * 分数没有"正常 / 异常"的语义，一旦整体变色就会被读成判定结果
 * （与体温那处"刻意不做三档红黄绿评分块"是同一条顾虑）。
 */
private val StressColor = Color(0xFF3949AB)
private val StressRelaxedColor = Color(0xFF6BCB77)
private val StressNormalColor = Color(0xFF546E7A)
private val StressElevatedColor = Color(0xFFFFA94D)
private val StressHighColor = Color(0xFFFF6B6B)

private fun stressLevelColor(level: String): Color = when (level) {
    Stress.Level.RELAXED -> StressRelaxedColor
    Stress.Level.ELEVATED -> StressElevatedColor
    Stress.Level.HIGH -> StressHighColor
    else -> StressNormalColor
}

/**
 * 经期追踪（模块 #13）配色 —— 玫红系。
 *
 * 经期是"录入 + 日历"型功能，配色要**女性化但不带医学判定意味**：
 * 玫红是这类 App 的通用视觉语言（Clue / Flo 都是玫红系），
 * 同时也明确区别于本 App 里其它所有指标的色族（心率粉红偏暖、血氧紫、HRV 青绿），
 * 避免用户在标签栏里把「经期」和其它健康指标混淆。
 */
private val CycleColor = Color(0xFFE91E63)
private val CycleAccentColor = Color(0xFFAD1457)
private val CycleFertileColor = Color(0xFF4DD0C4)

private fun tempLevelColor(c: Float): Color = when (Temperature.level(c)) {
    -1 -> TempLowColor
    1 -> TempHighColor
    else -> TempNormalColor
}

// ── 睡眠阶段配色（对齐三星健康：清醒粉红 / REM 淡紫 / 浅睡中紫 / 深睡深蓝）──
private val StageAwakeColor = Color(0xFFEC407A)
private val StageRemColor = Color(0xFFB39DDB)
private val StageLightColor = Color(0xFF7C6BEA)
private val StageDeepColor = Color(0xFF3040C8)

/**
 * 各阶段的"典型范围"（占总睡眠时间百分比），取自三星健康睡眠阶段说明页。
 * 只用于图上的斜纹参照带与解读文案；**不参与任何判定** ——
 * 超出范围不代表异常，页面文案必须写明这一点。
 */
private val StageTypical = mapOf(
    SleepStageKind.AWAKE to (0f to 9f),
    SleepStageKind.REM to (20f to 30f),
    SleepStageKind.LIGHT to (40f to 60f),
    SleepStageKind.DEEP to (10f to 20f),
)

/**
 * 睡眠阶段的展示顺序（从上到下）。
 *
 * ⚠ **只放 [SleepStageKind] 枚举，不放名字** —— 名字随语言变，
 * 而这里的元素被 `stageShortName` 当**返回值**、被图表当**遍历顺序**用。
 * 改造前它是 `SleepStageKind to "清醒"` 的对，`stageShortName` 从中取 `second` ——
 * 于是"取显示名"与"当数据用"混在一起，一翻译就把阶段名弄成英文（在中文界面下）。
 * 现在名字一律由 [stageLabel] 从资源取。
 */
private val StageOrder = listOf(
    SleepStageKind.AWAKE,
    SleepStageKind.REM,
    SleepStageKind.LIGHT,
    SleepStageKind.DEEP,
)

private fun stageColor(kind: String): Color = when (kind) {
    SleepStageKind.AWAKE -> StageAwakeColor
    SleepStageKind.REM -> StageRemColor
    SleepStageKind.LIGHT -> StageLightColor
    SleepStageKind.DEEP -> StageDeepColor
    else -> Color(0xFF9C8CB0)
}

/** 睡眠阶段的显示名（随语言变）。**仅供显示** —— 判断阶段请直接用 [SleepStageKind]。 */
@Composable
private fun stageLabel(kind: String): String = stringResource(
    when (kind) {
        SleepStageKind.AWAKE -> R.string.stage_awake
        SleepStageKind.REM -> R.string.stage_rem
        SleepStageKind.LIGHT -> R.string.stage_light
        SleepStageKind.DEEP -> R.string.stage_deep
        else -> R.string.stage_unknown
    }
)

/**
 * 可空数值的占位显示：`null` 时给 `--`。
 *
 * ⚠ 它存在的理由看着很怪但很硬：**字符串模板里不许嵌 ASCII 引号**。
 *   写「最低 ${x ?: "--"}%」的话，文案提取器会在内层引号处判定字符串结束，
 *   抓到的是半截的「最低 ${x ?: 」—— 译文库里就只剩「Lowest ${x ?: 」这种垃圾，
 *   而且编译、运行都不报错。
 *   把三元抽成函数，模板里就没有引号了。
 * ⚠ `--` 是**语言无关的占位符**，不参与翻译。
 */
private fun dash(v: Any?): String = v?.toString() ?: "--"

/** 差值前要不要带正号（同上：为了不在模板里嵌引号）。`+` 是符号，不参与翻译。 */
private fun signOf(v: Double): String = if (v >= 0.0) "+" else ""

/**
 * 一位小数（用于误差 / 离散度这类量）。
 *
 * ⚠⚠ **必须在调用点格式化** —— `stringResource` 的 `$x` 生成的是 `%1$s`（String），
 *   直接传 `Double` 会被 `toString` 成 `15.80066493211691` 原样显示。
 *   `Locale.ROOT` 保证小数点在任何区域都是 `.`（否则某些区域会变 `,`）。
 */
private fun fmt1(v: Double): String = String.format(java.util.Locale.ROOT, "%.1f", v)

/**
 * 把毫秒时长写成"5小时37分钟"这种口径（省略为 0 的部分）。
 *
 * ⚠ 是 `@Composable`：单位（小时/分钟）要走多语言，`stringResource` 只能在
 *   Composable 作用域里调。所有调用点都必须在 Composable 里。
 */

/**
 * `h:mm` 短时长（`6:36` / `0:45`）—— 给**每根柱子**标数值用（2026-10-15）。
 *
 * ⚠ 刻意不复用 [fmtDuration]：那个给的是"6 小时 36 分钟"，在趋势图那种
 * 每根柱只有 ~23dp 的地方必然截断或换行。
 * ⚠ 这个写法（数字 + 冒号 + 补零的分钟）在中/英/韩三种界面里都自然，
 *   不需要文案表 —— 与"时刻"同理。
 */
private fun fmtClock(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    return String.format(Locale.US, "%d:%02d", totalMin / 60, totalMin % 60)
}

@Composable
private fun fmtDuration(ms: Long): String {
    val totalMin = (ms / 60_000L).coerceAtLeast(0L)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h > 0 && m > 0 -> stringResource(R.string.s_499cefdb, h, m)
        h > 0 -> stringResource(R.string.s_86150d1f, h)
        else -> stringResource(R.string.s_86bb9d34, m)
    }
}

/** 手机端页面路由。用显式路由而不是嵌套弹窗：详情页要能整屏铺开，且返回路径唯一。 */
/**
 * 法务文档的**线上地址**（2026-10-16 上线，用户手工上传到公告仓库的 GitHub Pages）。
 *
 * ⚠ 指向的是 `legal/index.html`（入口页，里面链到隐私声明与免责声明），
 *   不是单独某一页 —— 用户从「关于」点进去时未必只想看一页。
 * ⚠ 与 App 内 `assets/legal/` 的那份**同源但不自动同步**：
 *   改了文档要**两处一起改**（App 内那份是用户实际读的，这份是给外部/商店审核看的）。
 */
/**
 * 应用内更新的界面状态（2026-10-16）。
 *
 * ⚠ 状态机刻意**只有四态**，且"下载中"的进度是唯一的可变部分 ——
 *   别把"校验失败/网络失败/落盘失败"各开一个态，那会让 UI 与逻辑各写一遍。
 */
private sealed interface UpdateUi {
    data object Idle : UpdateUi
    data object Checking : UpdateUi
    /**
     * 已是最新。
     * @param serverVersion 服务端读到的主版本（null = 没读到，界面不显示）。
     * @param watchLine 手表端的一句话结论（"已是最新 v193" / "有新版本 v194" / "读不到版本"）。
     *   ⚠ 2026-10-28 加：用户点"检查更新"只看到手机端的结论，会误以为手表也查过了。
     */
    data class UpToDate(
        val serverVersion: Int? = null,
        val watchLine: String? = null,
    ) : UpdateUi

    /** **检查**本身失败（拉不到公告 / 公告里没有合法 update 块）。与下载失败分开说。 */
    data class CheckFailed(val detail: String) : UpdateUi
    /** 失败。[detail] 直接显示给人看（网络不可用 / HTTP 状态 / 校验不符…）。 */
    data class Failed(val detail: String) : UpdateUi

    /** 检测到新版本，等用户点「下载并安装」。 */
    data class Available(val info: AnnouncementUpdate) : UpdateUi

    /** 下载中。[progress] 0f~1f；服务端不给长度时为 0（此时界面显示不确定进度）。 */
    data class Downloading(val info: AnnouncementUpdate, val progress: Float = 0f) : UpdateUi

    /** 下载并校验都成功了，等系统安装器返回。 */
    data class Ready(val file: java.io.File) : UpdateUi
}

// ⚠ 2026-10-17：线上法律页从 GitHub Pages 迁到 **Cloudflare R2**（与公告、APK 同一个桶）。
//   对象键：`legal/index.html`、`legal/privacy.html`、`legal/disclaimer.html`（放**桶根**下的 legal/ 目录）。
//   页面之间是相对链接（privacy.html / disclaimer.html），同一前缀下照常可用。
private const val LEGAL_ONLINE_URL =
    "https://REPLACE-ME.invalid/legal/index.html"

/** 手表更新的 UI 状态（2026-10-22）：让"推送"像手机更新一样有进度、有内容。 */
/**
 * 手表更新的界面状态。
 *
 * ⚠ 2026-10-26 **删掉蓝牙传输后只剩这两态**：这里不再"下载 + 推包"，
 *   点确认只是**跳到「安装」页**（无线 ADB 装包）；下载/进度/失败都在那一页里。
 *   原来的 Downloading / Sending / Done 三态已随蓝牙链路一起删除。
 */
private sealed interface WatchUpd {
    data class Offer(val info: WatchOta.Info) : WatchUpd
    /** 只是一句提示（读不到清单 / 已是最新 / 手表未连接），点确定关掉。 */
    data class Failed(val detail: String) : WatchUpd
}

private sealed interface Route {
    data object Tabs : Route

    /** 「更新设置」整屏页（2026-10-21）：手机端 / 手表端各一组开关与手动检查。 */
    data object Update : Route
    data class EcgDetail(val record: EcgRecordEntity) : Route

    /**
     * **「今日概览」整屏页**（2026-11-06）：首页那张三格卡的展开版。
     *
     * ⚠ 它是**二级页**（`route`），不是新标签页 —— 底部导航仍然常驻，
     *   返回走 `MainScreen` 那个统一的 `BackHandler`（route 优先退）。
     */
    data object Overview : Route

    /**
     * **主题页**（2026-11-06）：左右滑动预览 + 应用。
     *
     * ⚠ 它是二级页（`route`），底部导航仍常驻 —— 与「今日概览」同一种承载方式。
     */
    data object Theme : Route

    /** 「最近记录」列表页（心率/血压/血氧共用）。 */
    data class Records(val kind: RecordKind) : Route

    /**
     * 法务文档整屏页（2026-10-16）：隐私声明 / 免责声明。
     *
     * ⚠ 正文**放在 `assets/legal/` 里、按语言各一份**，不走多语言表：
     *   这类文档是**整篇长文**，逐句拆成几百个 key 既难维护、又容易漏译一句
     *   （而漏掉的那一句不会有任何报错）。放进 assets 还有个好处：
     *   **离线可读**，且文档版本跟 App 版本绑在一起（改了文档就得出新包，
     *   不会出现"隐私声明在服务器上被改过、用户看到的和当初同意的不一样"）。
     */
    data class Legal(val which: Int) : Route

    /** 睡眠页各图表点进去的详情页。 */
    data class SleepDetail(val kind: SleepDetailKind) : Route

    /**
     * 某一**历史晚**的详情页（2026-10-10 新增，入口在睡眠页的「逐晚记录」）。
     *
     * ## 为什么不复用 [SleepDetail]
     * [SleepDetail] 的四个页面全部读 `SleepUiState.night` —— 那是**最近一晚**，
     * 而且睡眠期**逐分钟的血氧/心率序列刻意不落库**（一晚几百个点会把
     * 「最近记录」列表刷屏）。⇒ 那四页在历史晚上根本画不出来（没有序列可画）。
     *
     * 这一页只讲**库里那一行能讲清的事**：时长 / 实际睡眠 / 阶段段 / 心率与血氧的统计值。
     * 它**不去读三星健康**，所以点进去是瞬时的，离线、未授权也能看。
     */
    data class SleepNight(val epochDay: Long) : Route

    /**
     * 某一天的心率/血氧记录（2026-10-13 新增，入口 = 两页标题行右侧的「今天 ▾」）。
     *
     * 用户要求「血氧 心率使用跟睡眠一样的查看历史按钮」——
     * 睡眠那套是「日期胶囊 + 月历（有数据着色、没数据置灰）」⇒ 这里复用同一套，
     * 只是选完这天之后落到**这一页**而不是 `SleepNight`。
     *
     * ⚠ 数据**只来自库里的 `heart_rate` / `spo2` 表**，不读三星健康：
     *   "库里有的，界面必须能显示"（同步失败只影响能不能更新，不影响能不能看）。
     */
    data class DayHistory(val kind: RecordKind, val epochDay: Long) : Route

    /** 公告列表页（入口在「用户」页；开屏弹窗也指向它）。 */
    data object Announcements : Route

    /**
     * 授权页（入口在「用户」页的「授权」那一行）。
     *
     * ⚠ 2026-10-07 用户要求：原来它是**在下面展开**的一块面板，用户的原话是
     *   「一件授权点击后是在下面显示出来，我需要的是出现在另一个新的页面里」。
     *   改成整屏二级页不只是版面偏好 —— 这块内容（一键授权 + 三星 8 项 +
     *   HC 9 项 + 三段说明）比「用户」页其它所有内容加起来还长，
     *   挤出在页面中间会把「个人 / 数据 / 关于」的分区读断；
     *   而整屏页还能给出唯一的返回路径（与公告页同一处理）。
     */
    data object PersonAuth : Route
}

/**
 * 记录类型。⚠ `title` 存的是**资源 id**而不是字符串：枚举在类体外求值，
 * 那里拿不到 Context 也不在 Composable 作用域，只能由 UI 层 `stringResource(title)` 解析。
 */
private enum class RecordKind(@StringRes val title: Int) {
    HR(R.string.s_00317450), BP(R.string.s_00460c4b), SPO2(R.string.s_004624e7),
    TEMP(R.string.s_786fa0c0), BIA(R.string.s_14f80321),
    /**
     * 活动片段。**刻意不加进标签页**（模块总表里 #7 就写着"无独立入口"）：
     * 它只是其它指标的上下文，本身不是一个需要单独看的指标。
     * 入口挂在心率页的活动卡片上，明细用同一个 [RecordsScreen]。
     */
    ACTIVITY(R.string.s_003837d9),
    /**
     * PPG 原始光信号会话。同样**刻意不加进标签页**（模块总表 #8 写着"无独立入口，
     * 后台隐式采集"）：它不是一个给用户看的指标，而是留给后续自研算法（呼吸率/压力/PTT）的
     * 波形输入，所以入口挂在血氧页（它就是搭血氧的车采的）的卡片上。
     */
    PPG(R.string.s_15722aab),
}

/**
 * 记录列表页的时间范围胶囊（**值就是天数**，`0` = 全部）。
 *
 * ⚠ 顺序即显示顺序。想加"近 3 天"就插一个 `3L` 进去 ——
 * 状态类型不用改、过滤逻辑不用改（见 `RecordsFilterBar` 的说明）。
 */
private val RECORDS_RANGE_CHOICES = listOf(0L, 7L, 30L)

/**
 * 「深睡占比趋势」最多画多少晚。
 *
 * 取 14 与 `EnergySummary.SERIES_NIGHTS` 一致 —— 两周是"能看出一个习惯"的最短跨度，
 * 而再多就会让每个点挤成不到 30px 宽、看不出晚与晚的差别。
 */
/** 2024-01-01 是**周一**：用它当基准取"第 c 个星期几"的名字（日历的星期头）。 */
private const val MONDAY_REF_EPOCH_DAY = 19723L

private const val STAGE_TREND_NIGHTS = 14

/**
 * 睡眠页「逐晚记录」最多列多少晚（2026-10-10）。
 *
 * 与 [STAGE_TREND_NIGHTS] 取同一个值、理由也一样（两周是"能看出一个习惯"的最短跨度）。
 * ⚠ 上限只影响**显示**：库里按 `keepLatest(400)` 留一年多，
 *   而一次列 400 行会变成一堵数字墙、还得滚很久。
 */
private const val SLEEP_HISTORY_NIGHTS = 14

/**
 * 「活动」页的日期条最多列出多少天（2026-10-10，含"今天"那一个）。
 *
 * 取 14 与 [SLEEP_HISTORY_NIGHTS] 同一个理由：两周是"能看出一个习惯"的最短跨度，
 * 再多会让日期条长得要横向滚很久。⚠ 只影响**能选到几天**，不影响数据本身 ——
 * 库里按 `keepLatest(400)` 留着一年多，改大这个数立刻能选到更早的日子。
 */
private const val ACTIVITY_PICKER_DAYS = 14

/**
 * 「活动」页一次读多少天的运动记录。
 *
 * ⚠ 必须 ≥ [ACTIVITY_PICKER_DAYS]：日期条能选到的每一天，这一页都要拿得出那天的运动记录，
 *   否则"点了 10月4日却说没有运动记录"——而实际上那天你跑过步。
 * 取 90（远大于 14）是因为运动记录**很稀**（一周也没几条），多读几天几乎不花钱；
 * 而少读一天的代价是一个**看不出来的错答案**。
 */
private const val ACTIVITY_EXERCISE_DAYS = 90

private enum class SleepDetailKind(@StringRes val title: Int) {
    STAGE(R.string.s_1e914e32),
    SPO2(R.string.s_004624e7),
    HR(R.string.s_00317450),
    RESP(R.string.s_687096c6),
}

/** 睡眠页各图表的数据容器：摘要在 MainScreen 一次读好，详情页直接复用，避免重复读三星健康。 */
private data class SleepUiState(
    val night: SleepNightDto?,
    val spo2: List<Pair<Long, Int>>,
    val hr: List<Pair<Long, Int>>,
    /** 当日全天血氧序列（今天 00:00 → 现在），血氧页的全天小时柱状图用。 */
    val spo2Today: List<Pair<Long, Int>> = emptyList(),
    /**
     * 除主睡眠之外的其它会话（午睡 / 补觉）。
     *
     * ⚠ 2026-09-29 新增：此前 `readSleepBundle` 取"endTime 最晚的会话"，
     * 有午睡时午睡会顶掉整晚睡眠。现在主睡眠按时长挑（`SleepHours.mainSession`），
     * 午睡作为"另外还有 N 段小睡"单独列出 —— 既不顶掉整晚，也不被丢掉。
     */
    val naps: List<SleepHours.Span> = emptyList(),
    /**
     * 本次同步**是否真的拿到了今日睡眠**（`night` 来自本次读取，而不是库回退）。
     *
     * ⚠ 与 `night != null` **不是一回事**：回退到库里旧数据时 `night` 也非空，
     *   但那是**过去的**一晚。界面必须能区分这两者才能写对"今日数据未同步"那句提示。
     */
    val fresh: Boolean = false,
    /**
     * 回退显示时那一晚的**归属日**（`dateEpochDay`，醒来那天）；本次同步成功则为 null。
     *
     * ⚠ 存 `Long` 而不是格式化好的日期串 —— 状态里**不许存已求值的本地化串**
     *   （切语言不会重算，见 `ResText` 的教训），格式化交给渲染点。
     */
    val fallbackEpochDay: Long? = null,
)

class MainActivity : ComponentActivity() {

    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    /**
     * 界面语言（模块 #15）：把用户选的语言套进本 Activity 的 Context。
     *
     * ⚠⚠ **必须在这里、不能改用 `onCreate` 里的 `updateConfiguration`** ——
     * API 24+ 上 `Resources.updateConfiguration` 只影响当前那个 Resources 实例，
     * 而 `recreate()` 重建时会重新生成一份，改动被丢弃（第一版就是这么写的，
     * 表现是"选完语言界面一个字不变"）。`attachBaseContext` 是 Context 生成的最早
     * 时机，且 `recreate()` 后**会再被调用一次** —— 新语言因此才在重建后仍然生效。
     */
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguageStore.wrap(newBase))
    }

    /**
     * 每次回到前台都试一次「今天的心跳」。
     *
     * ⚠ 为什么不只在 `MainScreen` 的 `LaunchedEffect` 里做：前台服务保活期间
     *   组合不会重建，`LaunchedEffect` 也就不会再跑 —— 跨天第一次打开会漏报。
     *   `Stats.appOpen()` 内部按**北京时间日期**去重，所以这里放心地每次调。
     */
    override fun onResume() {
        super.onResume()
        Stats.appOpen()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ⚠ 必须在 `setContent` **之前**恢复主题：晚一步的话第一帧会用默认配色画出来，
        //   用户会看到"打开 App 闪一下紫夜再变成本来的主题"。
        com.gemini.healthbridge.phone.ui.ThemeManager.restore(this)
        // ⚠⚠ 显式开 edge-to-edge（2026-11-06 主题动效）：
        //   注释里写着"targetSdk=36 系统强制 edge-to-edge"，但那条强制**只在 Android 15+ 生效**。
        //   本项目 minSdk 覆盖到 34 及以下 ⇒ 在老系统上内容仍被状态栏顶下去一块，
        //   于是根层那面**动效背景画不到状态栏后面**，屏幕最上面留一条**纯色横带**，
        //   下面的渐变/光效从带子边缘"齐刷刷"开始，像贴了条胶带（实测钛光最明显）。
        //   ⇒ 主动声明，让所有版本行为一致；状态栏避让已由 topBar 的 `statusBarsPadding` 负责。
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            // ⚠⚠ 全局圆角与配色只在这一处给（2026-10-15 改版）：
            //   `Card` 的 shape / 容器色全部来自 MaterialTheme，改这里等于改全部 80 多张卡，
            //   比逐张改调用点安全得多（也不会漏）。
            // ⚠ `ThemeManager.current` 在这里读一次 ⇒ 换主题时整个界面重组。
            //   `SideEffect` 负责把**系统状态栏图标**翻成与底色相反的颜色：
            //   浅色主题（陶瓷白/暖砂）要深色图标，深色主题要浅色 —— 主题一换就得跟着翻，
            //   否则会出现"米白底上白色时间"，看得见才怪。
            val theme = com.gemini.healthbridge.phone.ui.ThemeManager.current
            androidx.compose.runtime.SideEffect {
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                    .apply {
                        isAppearanceLightStatusBars = theme.isLight
                        isAppearanceLightNavigationBars = theme.isLight
                    }
                // ⚠⚠ 窗口背景也要跟着主题换：XML 里那个 `app_bg` 是**固定深色**，
                //   edge-to-edge 下**状态栏那一块**（Compose 内容之上的 inset 区）露的就是它。
                //   只设 Scaffold 的 containerColor 不够 —— 实测浅色主题顶部仍有一条黑边。
                // ⚠⚠ 取的是**背景层最顶端那一行的颜色**，不是 `theme.bg`：
                //   本应用不是 edge-to-edge，屏幕上沿那条状态栏带露的正是这个窗口底色，
                //   拿 `bg` 填会和下面的渐变接出一条硬边（玻璃主题下像贴了条黑胶带）。
                window.setBackgroundDrawable(
                    android.graphics.drawable.ColorDrawable(
                        theme.backdropTopColor().toArgb()
                    )
                )
            }
            MaterialTheme(shapes = AppShapes, colorScheme = appColorScheme()) {
                // ⚠⚠ 2026-11-06 **主题背景层**：整屏（含状态栏那块）画在最底下。
                //   动态主题的光就在这一层缓慢移动 —— 用户反馈"那几个怎么不是动态的"，
                //   指的就是手表端那 4 套会动的主题在手机端被砍成了静态。
                //   配套两件事，缺一个就"动不起来"且不报错：
                //     ① 下面 `Scaffold(containerColor = Transparent)`；
                //     ② 页面自己不许铺满底色（用 `Modifier.appPageBg()` 替代 `background(AppBg)`）。
                // ⚠ 根层兜底：`LocalContentColor` 一旦变成 Unspecified，所有没写 `color`
                //   的 `Text` 都会按默认黑色画。这层保证"至少有个正确的正文字"。
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides AppTextPrimary,
                ) {
                ThemeBackdropLayer(Modifier.fillMaxSize()) {
                // ⚠⚠ 2026-10-26 **首次运行强制向导**：没有它，用户装不上手表端，
                //   整个 App 全是空数据。向导不可退出（`BackHandler` 在向导里），
                //   **过了才创建 MainScreen** —— 连权限申请、蓝牙服务都等到那时候再起。
                var guideDone by remember { mutableStateOf(GuidePrefs.isDone(this@MainActivity)) }
                /** 过完向导要落在哪一页：1 = 「安装」页（第 3 步讲的就是它） */
                var enterTab by remember { mutableIntStateOf(0) }
                /**
                 * **回看首次引导**（2026-11-06 用户要求：首页标题右边加一个入口）。
                 *
                 * ⚠ 状态提到这一层、而不是塞进 `MainScreen`：首次流程本来就是
                 *   `if (!guideDone) 向导 else 主界面`，回看走同一个切换点，两条路径行为一致。
                 *   代价是主界面会重建一次（列表回到顶部）—— 与"进二级页再回来"的体感相同，可接受。
                 */
                var replayGuide by remember { mutableStateOf(false) }
                /**
                 * **首次主题选择**（2026-11-07）：向导点「完成」/「跳过」后先落到主题页，
                 * 挑完点左上返回才进首页功能页；这一页**不显示底部导航**。
                 * ⚠ 不落盘：只在「本次刚过完向导」这一轮生效，回看引导 / 下次启动都不再走。
                 */
                var themePicker by remember { mutableStateOf(false) }
                if (!guideDone) {
                    OnboardingWizard(onFinish = {
                        GuidePrefs.markDone(this@MainActivity)
                        // ⚠ 2026-10-26 用户要求：向导里已经把手表装好了 ⇒ **直接进首页**，
                        //   不再落到「安装」页（那一页只在"以后想再装/换包"时才去）。
                        enterTab = 0
                        guideDone = true
                        themePicker = true
                    })
                } else if (replayGuide) {
                    // 回看模式（`replay = true`）：返回键与「完成」都放开。
                    // ⚠⚠ 首次流程里"没检测到手表端就不许完成"是**故意**的硬门，
                    //   但回看的人早就装好了、只是没连着手表，照搬那道门会把他卡死在第 13 步。
                    // ⚠ 回看**不**触发主题选择：那是首次装机才有的引导步骤。
                    OnboardingWizard(replay = true, onFinish = { replayGuide = false })
                } else {
                    MainScreen(
                        initialTab = enterTab,
                        onReplayGuide = { replayGuide = true },
                        themePicker = themePicker,
                        onThemePickerDone = { themePicker = false },
                    )
                    // ⚠⚠ 2026-10-28 **首次启动的统计同意**：盖在主界面之上，必须先选一个。
                    //   - 放在向导**之后**：向导是"装不上表就不能用"，比这个更硬；
                    //   - 只问一次（`decided`），之后要改去「用户」页的开关；
                    //   - 用户点「同意」的那一下**立刻**补一条 app_open，否则当天报表里没有他
                    //     （`Stats.appOpen()` 的 `openedOnce` 就是为这件事挪到门内的）。
                    // ⚠ 2026-11-07：首次主题选择期间先别弹 —— 它会盖在主题页上打断挑选，
                    //   等用户点返回进首页再问（`decided` 仍在，不会漏问）。
                    var statsDecided by remember { mutableStateOf(StatsPrefs.decided(this@MainActivity)) }
                    if (!statsDecided && !themePicker) {
                        PrivacyConsentDialog { allow ->
                            StatsPrefs.setEnabled(this@MainActivity, allow)
                            statsDecided = true
                            if (allow) Stats.appOpen()
                        }
                    }
                }
                }
                }
            }
        }
    }

    @Composable
    private fun MainScreen(
        initialTab: Int = 0,
        onReplayGuide: () -> Unit = {},
        themePicker: Boolean = false,
        onThemePickerDone: () -> Unit = {},
    ) {
        val scope = rememberCoroutineScope()
        val device by SyncRepository.device.collectAsState()
        val event by SyncRepository.event.collectAsState()
        val btStatus by PhoneBtServer.status.collectAsState()
        // ⚠⚠ 连接状态取**结构化字段**，不拿 `btStatus` 去猜（见 `PhoneBtServer.connected`
        //   的 KDoc：按文案猜在别的语言下永远为假）。状态卡那张 `手表在线：X` 就用它判。
        val btConnected by PhoneBtServer.connected.collectAsState()

        // 蓝牙通道需要“附近设备”运行时权限；授权后服务端自动开始监听
        val btPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) {
            PhoneBtServer.start(applicationContext)
        }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 31) {
                btPermLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            }
        }

        // 匿名统计的心跳：**进主界面这一刻**算一次「今天打开过」。
        // ⚠ `appOpen()` 内部会先看同意状态与「本进程是否已发过」，
        //   没同意时它是纯空转 —— 不要把判断写在这里，否则将来加别的入口会漏。
        LaunchedEffect(Unit) { Stats.appOpen() }

        /**
         * 底部导航：**0 = 功能，1 = 安装（无线 ADB），2 = 用户**。默认落在「功能」页。
         *
         * ⚠ 2026-10-26 在「功能」与「用户」**中间**插了「安装」页（用户要求）⇒ 用户页从 1 变 2，
         *   凡是 `topTab == 1` 的地方都改成了 2（下面 `screenKey` / 内容分派两处）。
         */
        var topTab by remember { mutableIntStateOf(initialTab) }

        /**
         * 功能宫格里**当前打开的那个功能**（[tabs] 的下标）；**-1 = 停在宫格页**。
         *
         * 为什么不用"再开一个 Route"：宫格与功能页共用同一块内容区，
         * 用一次状态切换比多一条路由更不容易让返回路径分叉
         * （Route.Tabs 的语义保持为"顶层"，所有 `route = Route.Tabs` 的返回都还对）。
         */
        var openFeature by remember { mutableIntStateOf(-1) }
        // 经期追踪开关（默认关）：只有用户主动开启才显示「经期」标签页。
        // 必须在 tabs 之前声明 —— tabs 会根据它决定要不要追加「经期」。
        var cycleEnabled by remember { mutableStateOf(CycleSettings.isEnabled(applicationContext)) }
        // 经期记录（#13）：只有手机端写，一条只是两个 epochDay + 一个流量档位，
        // 全部读进来也毫无压力。指标（周期长度/预测）在页面里由 `Cycle.analyze` 现算。
        val cycleList by SyncRepository.database.menstrualDao().all()
            .collectAsState(initial = emptyList())
        // 夜间氧减指数摘要（#11 的替代落点）：一天一行，读最近 30 晚。
        // 它**必须**从库里读而不是界面现算 —— 输入是三星夜间血氧分钟序列，而那个序列不落库，
        // 界面手里没有源数据（详见 `SleepOdiEntity` 的注释）。
        val odiList by SyncRepository.database.sleepOdiDao().recent(30)
            .collectAsState(initial = emptyList())
        // 多频阻抗（手表 MF-BIA 采的原始阻抗谱）：派生量（比值/趋势）在页面里现算，
        // 库里只有原始值 —— 与"派生量一律不落库"同一条纪律。
        val mfList by SyncRepository.database.mfBiaDao().recent(30)
            .collectAsState(initial = emptyList())

        // 能量评分 / 睡眠呼吸暂停：都是**三星算好的现成值**，库里存的就是原值，展示时不再加工。
        val energyList by SyncRepository.database.energyScoreDao().recent(30)
            .collectAsState(initial = emptyList())
        // 每晚睡眠统计：自研能量评分的输入。200 晚足够给趋势图与基线用。
        val sleepNightList by SyncRepository.database.sleepNightDao().recent(200)
            .collectAsState(initial = emptyList())
        // 「每天同步落本地」的那张表（DB v22）：日历的可用日期要用它 ——
        // 否则用户明明有本地记录却在日历上点不到（2026-10-13 用户报的那一类）。
        val vitalDays by SyncRepository.database.dailyVitalsDao().days(400)
            .collectAsState(initial = emptyList<Long>())

        // 每天的活动量（能量评分里两项的输入）。与睡眠夜同样订阅 ——
        // 补读是后台做的，订阅能让界面在补读完成后自动重算，不需要用户手动刷新。
        val activityDayList by SyncRepository.database.activityDayDao().recent(200)
            .collectAsState(initial = emptyList())
        val apneaList by SyncRepository.database.sleepApneaDao().recent(30)
            .collectAsState(initial = emptyList())
        // 「体温」「体成分」刻意**加在最后**而不是插在「血氧」旁边：标签页路由用的是数字下标
        // （`when (tab)` 与 `LaunchedEffect(tab)` 里都有），插在中间会一次性改掉后面所有
        // 下标，漏改一处就是"点体温打开了睡眠页"这种不报错的错。
        // 「经期」同样追加在末尾，且**只在开关打开时出现**（默认不显示）。
        // ⚠ 这里存的是 **Feature 枚举**（身份，永不翻译），不是中文名字 ——
        // 名字随语言变，拿它当分派键在非中文下必然失配（见 Feature 的说明）。
        // 「经期」追加在末尾，且**只在开关打开时出现**（默认不显示）。
        val tabs = buildList {
            addAll(
                listOf(
                    Feature.HR, Feature.BP, Feature.ECG, Feature.SPO2, Feature.SLEEP,
                    Feature.TEMP, Feature.BIA,
                    // 能量评分是三星健康算的现成指标，2026-09-19 接入。
                    // 它与其它项平级（都是"一个功能"），所以进的是 [tabs] 而不是塞进某一页。
                    Feature.ENERGY,
                    // 扩展指标（2026-09-23）：都是三星健康里的数据，我们只搬运。
                    // 「活动」把步数/活动量/爬楼/运动记录合到一页 —— 它们本就是一件事的不同侧面，
                    // 拆成四张卡会让宫格变成"一堆没人看的格子"（手表首页那次改版的教训）。
                    Feature.ACTIVITY, Feature.DIET, Feature.GLUCOSE, Feature.IHRN,
                )
            )
            if (cycleEnabled) add(Feature.CYCLE)   // 经期按开关出现，默认不显示
        }

        // ═══ 二级页/各功能页的**滚动位置**（2026-10-10 用户报的 bug）═══
        //
        // ## 症状
        // 睡眠页往下滚到「逐晚记录」→ 点开某一晚 → 返回 ⇒ 页面**跳回最顶上**，
        // 得重新滚一遍。用户原话：「不能接着之前的位置」。
        //
        // ## 根因不是"没记住"，而是"整个页面被拆掉了"
        // `route` 优先于标签页：`when (route)` 的每个分支都 `return@Column`，
        // 所以一进详情页，下面那整块（宫格 / 用户页 / 某个功能页）**整棵子树离开组合**。
        // 而 `LazyColumn` 的默认状态是 `rememberLazyListState()` ——
        // 它内部虽然用的是 `rememberSaveable`，但**没有 `SaveableStateHolder` 托管**时，
        // 离开组合就意味着状态被丢弃 ⇒ 回来时新建一个"从 0 开始"的状态。
        //
        // ## 修法
        // `SaveableStateHolder` 正是为这件事设计的（`NavHost` 用它保每个目的地的状态）：
        // 按 key 记住子树里的 `rememberSaveable` 值，子树离开组合时**存起来**，
        // 同 key 再进来时**恢复**。所以只要给每一屏一个稳定的 key 就够了 ——
        // 不用给 15 个功能页逐个加 `state = rememberLazyListState()` 参数
        // （那样既啰嗦，又必然会漏掉一两个）。
        //
        // ⚠⚠ key 必须**按屏不同**：`Records(HR)` 与 `Records(BP)` 是两个页面，
        //   共用一个 key 会让血氧记录页恢复成心率的滚动位置。
        // ⚠⚠ key 必须**稳定**：不能用 `hashCode()` / 递增序号，否则每次重组都换 key，
        //   等于没记住。
        val screenStates = rememberSaveableStateHolder()

        // ⚠⚠ 上次崩溃就弹一次（2026-10-03 加）——**刻意不自动消失**，用户可以慢慢截图。
        //
        // 起因：用户报「进血压页闪退」，而没有 adb / logcat 拿不到栈，靠读代码猜 ——
        // 猜了两轮都没中。这一块就是那次教训的产物：**先让问题能开口，再谈修**。
        // ⚠ 只显示栈本身、按钮复用现成的「知道了」，**不加新文案** ——
        //   栈是诊断信息不是产品文案（与 `ResText.raw("Health Connect")` 同类）。
        // 写侧见 `GeminiApp` 里装的未捕获异常处理器。
        var crashText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { crashText = GeminiApp.takeLastCrash(this@MainActivity) }
        crashText?.let { s ->
            AlertDialog(
                onDismissRequest = { crashText = null },
                title = {
                    Text(
                        s.lineSequence().firstOrNull()?.take(80) ?: "crash",
                        fontSize = 13.sp,
                    )
                },
                text = {
                    Text(
                        s.take(1500),
                        fontSize = 11.sp,
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                    )
                },
                confirmButton = {
                    AppOutlineButton(onClick = { crashText = null }) {
                        Text(stringResource(R.string.s_1faf4f0c))
                    }
                },
            )
        }

        // 「设备」页不再是一个功能入口：它是设置，已拆到「用户」页（见 UserTab），
        // 其中真正属于"状态"的东西（连接/待同步/同步结果）留在功能页顶部的状态条上。

        // 页面路由：详情页/记录列表页整屏铺开，返回路径唯一
        // ⚠ 首次过完向导时直接落在主题选择页（由外层 themePicker 决定），
        //   之后 route 只由返回/导航改，参数变化不会把它重置。
        var route by remember {
            mutableStateOf<Route>(if (themePicker) Route.Theme else Route.Tabs)
        }

        val hrList by SyncRepository.database.heartRateDao().recent(200)
            .collectAsState(initial = emptyList())
        val bpList by SyncRepository.database.bloodPressureDao().recent(200)
            .collectAsState(initial = emptyList())
        val ecgList by SyncRepository.database.ecgDao().recent(200)
            .collectAsState(initial = emptyList())
        val spo2List by SyncRepository.database.spo2Dao().recent(200)
            .collectAsState(initial = emptyList())
        val tempList by SyncRepository.database.temperatureDao().recent(200)
            .collectAsState(initial = emptyList())
        val biaList by SyncRepository.database.bodyCompositionDao().recent(200)
            .collectAsState(initial = emptyList())
        val activityList by SyncRepository.database.activityDao().recent(200)
            .collectAsState(initial = emptyList())
        // PPG 原始光信号：一条最多带三段波形（各 3000 点），200 条最多约 14MB，
        // 与心电波形同一个量级；列表页不解析波形，只展示摘要。
        val ppgList by SyncRepository.database.ppgWaveDao().recent(200)
            .collectAsState(initial = emptyList())
        // HRV 记录（#9）：一条只有约 35 个整数（逐拍间期），200 条不到 40KB ——
        // 与波形表不同，这里把序列整条读进来也毫无压力，指标在卡片里现算。
        val hrvList by SyncRepository.database.hrvDao().recent(200)
            .collectAsState(initial = emptyList())

        var samsungGranted by remember { mutableStateOf<Boolean?>(null) }
        // 睡眠读权限是可选的（只影响心率页的睡眠分区），单独跟踪，
        // 这样即使用户没授权也不会把"三星健康数据"整块标成未授权。
        var samsungSleepOk by remember { mutableStateOf<Boolean?>(null) }
        // 皮肤温度读权限同样是可选的（只影响「体温」页那一块三星健康夜间数据）
        var samsungSkinTempOk by remember { mutableStateOf<Boolean?>(null) }
        // 体成分读权限（「体成分」页里三星健康那一部分）与用户档案读权限
        // （手表测 BIA 的前提：手表没有三星健康 Data SDK，只能靠手机读出来再推过去）
        var samsungBiaOk by remember { mutableStateOf<Boolean?>(null) }
        var samsungProfileOk by remember { mutableStateOf<Boolean?>(null) }
        // ⚠⚠ 「连不上」与「未授权」**必须分开**（2026-10-11 用户报："昨天的明明同步了，
        //   今天这些页面还是没记录"）。
        //
        // `samsungGranted` 只有两个取值，而现实有三种：
        //   ① 已授权          → true
        //   ② 明确未授权      → false（SDK 答了，答的是"没有"）
        //   ③ **问不出来**    → 这里。SDK 连接失败 / 服务被系统回收 / 三星健康在更新。
        // ③ 原来被折叠进 ②：`grantedWithRetry` 返回 `null`（连不上）那一支被写成 `false`，
        // 于是界面显示「尚未授权三星健康」，并把用户指向一个**他做不了也没用的动作**
        // （重新授权）。用户看到的样子和自己真的没授权时**一模一样**，只能反复去点授权。
        //
        // ⇒ ③ 单独记一个标记：文案改成「连接三星健康失败」，动作改成「重试」，
        //   而**不是**催他去重新授权。（判据由静态校验脚本钉住。）
        var samsungUnreadable by remember { mutableStateOf(false) }
        var diag by remember { mutableStateOf("") }

        // 最近一晚的三星健康皮肤温度（含逐点序列，画折线用）。
        // 与 sleepState 同一套纪律：**空的读取结果不许覆盖已有的**，
        // 否则三星健康偶发返回空就会让"打开软件体温曲线就没了"周期性复发。
        // ⚠ 它是**内存态**，所以体温页的"夜间皮肤温度"那张卡有**两级来源**：
        //   ① 这个实况（有逐点序列、能画折线）
        //   ② 库里的 `latestNight`（冷启动时实况还没跑完，走这一支）
        //   ⇒ 体温页**离线不会空白**（第 ② 支兜着），但会少掉折线。
        var skinTempNight by remember {
            mutableStateOf<SamsungHealthReader.SkinTempNight?>(null)
        }

        // ── 三星健康快照：在 MainScreen 一次读好，睡眠页/血氧页/各详情页共用 ──
        var sleepState by remember { mutableStateOf(SleepUiState(null, emptyList(), emptyList())) }
        var sleepLoading by remember { mutableStateOf(false) }

        // ── 统一的自动同步节拍 ──
        // 所有触发点（冷启动 / 回到前台 / 切到血压/血氧/睡眠/腕表温度/体成分页 / 手动刷新）
        // 都只是把这个计数器 +1，
        // 真正的读取只有下面那**一处**。以前是每个页面各写一套读取逻辑，于是出现
        // "血氧睡眠能自动同步、心率要手动导入"这种不一致。
        var syncTick by remember { mutableIntStateOf(0) }
        var lastSyncAt by remember { mutableStateOf(0L) }
        /**
         * 上次自动回填到 Health Connect 的时间。
         *
         * ⚠ 用户 2026-10-27：「回填到健康平台」应该是**自动的**，不再给手动入口。
         *   节流 10 分钟：同步本身会被"回前台/切页"频繁触发，而回填要连 HC 写数据，
         *   每次都写等于每小时几十次无意义的 IPC。
         */
        var lastBackfillAt by remember { mutableStateOf(0L) }
        var syncNote by remember { mutableStateOf("") }
        // 「今天同步过三星健康没有」—— 持久化在 `SamsungHealthReader` 里
        // （跨进程重启有效）。功能页那张卡用它决定显示「今日已同步」还是
        // 「请打开三星健康同步今日数据」，取代原来的「同步」按钮（2026-10-10）。
        var samsungSyncedToday by remember {
            mutableStateOf(SamsungHealthReader.syncedToday(applicationContext))
        }

        // ① 冷启动：先摸清权限，再触发第一次同步。
        LaunchedEffect(Unit) {
            val ctx = applicationContext
            // ⚠⚠ 只**查一次**，不再"5 组各查 4 次"：每次连接都可能拉起三星健康，
            //   而小米/红米的「关联启动」默认拦截 ⇒ 一次刷新就等于二十条"拒绝"记录
            //   （用户看到系统记录里"启动三星健康 拒绝 134 次"就是这么来的）。
            val snap = SamsungHealthReader.snapshot(ctx)
            // ⚠⚠ 2026-11-04：`null` 是"**问不出来**"，绝不能折成 `false`。
            //   三星健康被系统强停 / 清后台时 `snapshot()` 就返回 null，
            //   折成 false 会让用户页显示「还有 1 项未授权」，而他点开三星健康权限又「有了」
            //   —— 同一份授权在两个界面之间反复横跳，用户不知道该信哪个。
            // ⇒ 读到就记住（true/false 都记，false 表示用户真的撤了权限，必须如实反映）；
            //   读不到就回退到上次真实读到的值（见 `HealthMemory` 的说明）。
            if (snap != null) HealthMemory.saveSnapshot(ctx, snap)
            val eff = snap ?: HealthMemory.loadSnapshot(ctx)
            samsungGranted = eff?.granted
            samsungSleepOk = eff?.sleep
            samsungSkinTempOk = eff?.skinTemp
            samsungBiaOk = eff?.bia
            samsungProfileOk = eff?.profile
            // 只有「从来没读到过 + 这次也读不到」才说连接失败；有记忆值时按记忆显示
            samsungUnreadable = snap == null && eff == null
            // ⚠⚠ **不再自动弹授权**（2026-10-17 删）：原来"从未授权过"就在冷启动时 request() 一次，
            //   每次冷启动都会尝试拉起三星健康 —— 在拦「关联启动」的机型上等于每次启动都撞墙，
            //   而且自动弹系统授权页本身就很唐突。现在只由用户在「授权」页主动点。
            syncTick++
        }

        // ② 回到前台就同步一次 —— "早上打开手机软件自动同步"走的就是这条路
        val lifeCtx = LocalContext.current
        DisposableEffect(lifeCtx) {
            val lifecycle = (lifeCtx as? ComponentActivity)?.lifecycle
            val observer = LifecycleEventObserver { _, e ->
                if (e == Lifecycle.Event.ON_RESUME) syncTick++
            }
            lifecycle?.addObserver(observer)
            onDispose { lifecycle?.removeObserver(observer) }
        }

        // ③ 切到「血压」「血氧」「睡眠」「腕表温度」「体成分」页时补一次；60 秒内刚同步过就跳过，
        //    否则每点一次标签页都去读三星健康，白耗电也白等。
        //    ⚠ 这份名单必须与 `SYNC_ON_OPEN` 一致（这里的旧文案曾把「腕表温度」写成「体温」，
        //      那是这个页面改名前的名字 —— 注释与代码漂移就等于没有注释）。
        LaunchedEffect(openFeature) {
            if (tabs.getOrNull(openFeature) !in SYNC_ON_OPEN) return@LaunchedEffect
            if (System.currentTimeMillis() - lastSyncAt > 60_000L) syncTick++
        }

        // ④ 同步本体：一次读齐 心率/血氧/血压/睡眠 并落库
        LaunchedEffect(syncTick) {
            if (syncTick == 0) return@LaunchedEffect
            sleepLoading = true
            val r = runCatching { SamsungHealthReader.syncToday(applicationContext) }
                .getOrElse {
                    SamsungHealthReader.AutoSyncResult(
                        authorized = false, undetermined = true, note = this@MainActivity.getString(R.string.s_d7ea30bd),
                    )
                }
            lastSyncAt = System.currentTimeMillis()

            // ⚠⚠ **"回库兜底"必须是一个函数、且三条分支都要调**（2026-10-11）。
            //
            // 原来它是一段贴在下面对 `else` 分支里的代码，而 `r.undetermined`（连不上）
            // 那一支**什么也没做** ⇒ 只要这次是"问不出来"，库里存着的历史也一律看不见。
            // 而"问不出来"恰恰是最常见的那一支（三星健康在更新 / 被系统回收 / 服务刚重启）。
            //
            // ⚠ 旧守卫当时**没抓住这个洞**：它数的是
            //   `sleepNightDao().latestOnce()` 出现几次（要求 ≥2），而那两处正好分别落在
            //   `r.authorized` 的 else-if 与 `else` 里 —— 计数达标，第三个分支照样裸着。
            //   这是"判据要看**落在哪一条分支**、不能只看总数"的又一个实例。
            //
            // ⚠ 只读**最近一晚**：`latestOnce()` 只回一行，所以它便宜到可以在每次同步失败时都跑。
            suspend fun fallbackToCache() {
                if (sleepState.night != null) return
                val cached = runCatching {
                    SyncRepository.database.sleepNightDao().latestOnce()
                }.getOrNull() ?: return
                sleepState = SleepUiState(
                    night = cached.toSleepNightDto(),
                    spo2 = sleepState.spo2,
                    hr = sleepState.hr,
                    spo2Today = sleepState.spo2Today,
                    naps = emptyList(),
                    fresh = false,
                    fallbackEpochDay = cached.dateEpochDay,
                )
            }

            when {
                r.authorized -> {
                    samsungGranted = true
                    samsungUnreadable = false
                    // ⚠ 这里问的是**持久化的那个标记**（`syncToday` 末尾刚写过），
                    //   不是"这一次有没有新增" —— 理由见 `markSyncedToday` 的 KDoc。
                    samsungSyncedToday = SamsungHealthReader.syncedToday(applicationContext)
                    // 读到了睡眠会话 → 反证睡眠读权限是好的，界面不必再提示缺权限
                    if (r.night != null) samsungSleepOk = true
                    if (r.skinTempNight != null) samsungSkinTempOk = true
                    if (r.cachedBia > 0) samsungBiaOk = true
                    syncNote = r.note
                    // 关键：**空的读取结果不许清掉已有内容**。三星健康偶尔会返回空
                    // （服务刚重启、当次分页失败），直接覆盖会让"打开软件记录就没了"周期性复发。
                    //
                    // ⚠⚠ 2026-10-09：**这一层只保护"同一次运行内"** —— 进程一重启，
                    //    `sleepState` 就回到初值（night = null），页面直接空白，
                    //    哪怕库里存着几百晚。所以本轮把「回退」升级成**两级**：
                    //      ① 内存里已有 → 沿用（`?: sleepState.night`，原逻辑不动）
                    //      ② 内存也没有 → **回库里取最近一晚**（下面的 `else` 分支）
                    val liveNight = r.night
                    if (liveNight != null) {
                        sleepState = SleepUiState(
                            night = liveNight,
                            spo2 = r.spo2Night.takeIf { it.isNotEmpty() } ?: sleepState.spo2,
                            hr = r.hrNight.takeIf { it.isNotEmpty() } ?: sleepState.hr,
                            spo2Today = r.spo2Today.takeIf { it.isNotEmpty() } ?: sleepState.spo2Today,
                            // 小睡列表跟着 `night` 走：`night` 换了才有意义。
                            // 用 `r.naps`（可能为空列表 = 这次窗口里没有小睡）覆盖，而不是沿用旧的 ——
                            // 旧的午睡对应的是**上一场**主睡眠，留着会显示成"这一晚的小睡"。
                            naps = r.naps,
                            fresh = true,
                            fallbackEpochDay = null,
                        )
                    } else if (sleepState.night == null) {
                        // ② 这次没读到、内存里也没有 ⇒ 回库取最近一晚。
                        //    取不到（库里啥都没有）就维持空状态，交给 SleepEmptyHint 说原因。
                        //    ⚠ 与另外两条分支**共用同一份**实现（`fallbackToCache`）——
                        //      复制三遍迟早在某一支上漂移（2026-10-11 就是这么漏掉 undetermined 的）。
                        fallbackToCache()
                    } else {
                        // 内存里有、这次没读到：沿用内存那份，但**标记成"非今日"**吗？
                        // ⚠ 不标。它可能正是今天同步到的那一份（只是这次读取恰好失败），
                        //   把"读取失败"说成"今日未同步"是另一种误报。保持 fresh 不变。
                        sleepState = sleepState.copy(
                            spo2 = r.spo2Night.takeIf { it.isNotEmpty() } ?: sleepState.spo2,
                            hr = r.hrNight.takeIf { it.isNotEmpty() } ?: sleepState.hr,
                            spo2Today = r.spo2Today.takeIf { it.isNotEmpty() } ?: sleepState.spo2Today,
                        )
                    }
                    skinTempNight = r.skinTempNight ?: skinTempNight
                }
                // 连不上 ≠ 未授权：**权限状态不动**（保持原样，不写 false），
                // 只把"问不出来"单独标出来给文案用。⚠ 不催用户去重新授权 ——
                // 连接失败时重新授权同样失败，那个按钮只会把他带进沟里。
                r.undetermined -> {
                    samsungUnreadable = true
                    syncNote = r.note
                    // ⚠⚠ 这一支原来是**空的** ⇒ "问不出来"时库里的历史一律看不见。
                    //   这正是用户报的"昨天的明明同步了、这些页面却还是没记录"的那一支。
                    fallbackToCache()
                }
                else -> {
                    samsungGranted = false
                    samsungUnreadable = false
                    samsungSleepOk = SamsungHealthReader.hasSleepPermission(applicationContext)
                    samsungSkinTempOk = SamsungHealthReader.hasSkinTempPermission(applicationContext)
                    samsungBiaOk = SamsungHealthReader.hasBodyCompositionPermission(applicationContext)
                    samsungProfileOk =
                        SamsungHealthReader.hasUserProfilePermission(applicationContext)
                    syncNote = r.note
                    // ⚠ 未授权时**同样要回库兜底** —— 这正是用户报的场景
                    //   （"不同步三星健康就一个数据都看不到"）。之前这里什么都不做，
                    //   于是 cold start 未授权 = 整页空白。库里有的就必须看得见。
                    fallbackToCache()
                }
            }
            // 同步完把用户档案推给手表一次：手表测 BIA 必须要这份资料，而它自己读不到三星健康。
            // 放在这里而不是只在收到 HELLO 时推 —— 用户可能是在「用户」页点了授权之后才拿到资料，
            // 那一刻手表并没有重新发 HELLO，只推一次就会让它一直停在"缺资料"。
            if (r.authorized) runCatching { PhoneBtServer.pushBiaProfile(applicationContext) }
            sleepLoading = false

            // ── 自动回填到 Health Connect（2026-10-27 起不再需要手点）──
            // ⚠ 只在「HC 可用 + 写权限齐」时才跑：缺权限时 HC 会逐条抛异常，
            //   日志里刷一堆权限错误，而用户没有任何可做的动作。
            if (r.authorized && System.currentTimeMillis() - lastBackfillAt > 10 * 60_000L) {
                lastBackfillAt = System.currentTimeMillis()
                runCatching {
                    val ctx0 = applicationContext
                    if (HealthConnectManager.isAvailable(ctx0) &&
                        HealthConnectManager.missingWritePermissions(ctx0).isEmpty()
                    ) {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            HealthBackfill.toHealthConnect(ctx0, SyncRepository.database)
                        }
                    }
                }.onFailure { android.util.Log.w("AutoBackfill", "自动回填失败", it) }
            }
        }
        // Health Connect（安卓官方健康数据中枢）
        val hcAvailable = remember { HealthConnectManager.isAvailable(applicationContext) }
        // ⚠ 用「**缺几项**」而不是一个布尔值：
        //    用户看到"未授权"是无从下手的（他可能已经点过授权页、也勾了），
        //    而"缺 6 项"能告诉他还有东西要做 —— 这两句的区别就是"能不能自己解决"。
        var hcMissing by remember { mutableStateOf<Int?>(null) }
        val hcGranted = hcMissing == 0

        // ⚠ 授权页**打不开**时的错误要留得住。`launch` 是异步的，它可能同步抛出
        //    （HC 版本不认识某个新增的权限类型），也可能什么都不做 ——
        //    两种表现都同样是"点这一行没反应"，所以必须有一处能说明原因。
        var hcAuthErr by remember { mutableStateOf<String?>(null) }
        val hcLauncher = rememberLauncherForActivityResult(
            androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
        ) {
            scope.launch {
                val miss = HealthConnectManager.missingWritePermissions(applicationContext).size
                hcMissing = miss
                SyncRepository.setEvent(
                    if (miss == 0) ResText.of(R.string.s_c2397adf) +
                        ResText.of(R.string.s_4e59bfd1, HealthConnectManager.writePermissions.size)
                    else ResText.of(R.string.s_11eddd5a, miss)
                )
            }
        }
        LaunchedEffect(hcAvailable) {
            if (hcAvailable) {
                hcMissing = HealthConnectManager.missingWritePermissions(applicationContext).size
            }
        }
        // 经期读权限（模块 #13）：独立于上面的写入权限，单独跟踪、单独申请。
        // 只有用户进了「经期」页、点了「从 Health Connect 导入」才会触发申请，
        // 不冷启动就弹 —— 经期读取对大多数人不是刚需，主动打扰不合适。
        var hcCycleGranted by remember { mutableStateOf(false) }
        val hcCycleLauncher = rememberLauncherForActivityResult(
            androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
        ) {
            scope.launch {
                hcCycleGranted = HealthConnectManager.hasMenstruationReadPermission(applicationContext)
                SyncRepository.setEvent(
                    if (hcCycleGranted) ResText.of(R.string.s_a889e990)
                    else ResText.of(R.string.s_aaf3e488)
                )
            }
        }
        LaunchedEffect(cycleEnabled) {
            if (cycleEnabled && hcAvailable) {
                hcCycleGranted = HealthConnectManager.hasMenstruationReadPermission(applicationContext)
            }
        }

        // ── 数据备份与恢复 ──
        // 本机的数据是永久保留的（没有任何定期清理），但"永久"只对"这个 App 一直装着"成立：
        // 数据在内部分区，卸载即整块删除，手表端还显式设了 allowBackup="false"。
        // 所以必须给用户一个自己拿得走、看得见的备份。
        var backupBusy by remember { mutableStateOf(false) }
        var backupPath by remember { mutableStateOf("") }
        var showExportDialog by remember { mutableStateOf(false) }
        // 升级前快照的份数：数据库版本变化时 AppDatabase 会自动留一份（见 snapshotBeforeUpgrade），
        // 这里只显示份数，让用户知道这层保险是存在的。
        var snapshotCount by remember { mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            snapshotCount = AppDatabase.snapshots(applicationContext).size
        }
        val restoreLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                backupBusy = true
                SyncRepository.setEvent(ResText.of(R.string.s_ad5557a0))
                val r = DataBackup.restore(applicationContext, uri)
                SyncRepository.setEvent(r.message)
                backupBusy = false
                // 恢复完刷一次三星健康快照；各页列表是 Room 的 Flow，会自己跟着库变化更新
                if (r.ok) syncTick++
            }
        }

        // ── 功能宫格：一张卡一个功能 ──────────────────────────────────────
        // 摘要取"最近一条 + 时间"，让宫格本身就是个概览；拿不到就如实说"还没有记录"，
        // 而不是显示 0 —— 0 会被读成"测过但结果是 0"。
        // ⚠⚠ 卡片顺序**必须由 `tabs` 驱动**，绝不能在这里手写顺序：
        //   点击传的是**卡片下标**，而路由用的是 `tabs.getOrNull(那个下标)` ——
        //   两份列表只要顺序有一处不同，就会「点血糖打开了心律不齐」（2026-10-03 用户实测）。
        //   原来是手写 `listOf(...)` + `.filter { it.feature in tabs }`：filter **只过滤、不重排**，
        //   而 `CYCLE` 卡写在中间、`tabs` 把它追加在末尾 ⇒ 开启经期后从第 8 张卡起
        //   **整体错位一位**（点血糖 → 卡片下标 11 → `tabs[11]` = 心律不齐）。
        //   ⇒ 改成 `tabs.map` + **穷尽 `when`**：新增 Feature 时编译器会强制在这里补一张卡，
        //     顺序与身份都不可能与 `tabs` 漂移。
        val featureCards = tabs.map { f ->
            // ⚠ 每个分支里第一个参数都用 `f`（就是 tabs 里那个元素），所以卡片身份与
            //   列表位置**天然一致**，不可能再出现"显示的卡"与"打开的页"错位。
            when (f) {
                // ⚠ 每个分支都先 `let` 住"最近一条"再构造：同一个值要喂给
                //   `value` 和 `note` 两处，各查一次列表既慢、也可能取到**不同**的两条。
                Feature.HR -> hrList.maxByOrNull { it.measuredAt }.let { r ->
                FeatureCard(
                    f, HrAwakeColor, stringResource(R.string.glyph_hr),
                    value = r?.bpm?.toInt()?.toString(),
                    unit = stringResource(R.string.unit_bpm_short),
                    note = r?.let { cardFmt.format(Date(it.measuredAt)) }
                        ?: stringResource(R.string.no_record_yet),
                )
            }
                Feature.BP -> bpList.maxByOrNull { it.recordedAt }.let { r ->
                FeatureCard(
                    f, Color(0xFFFF6B6B), stringResource(R.string.glyph_bp),
                    // 血压的"一个值"本身就是「收缩/舒张」两段，用斜杠拼，不再拆单位
                    value = r?.let { "${it.systolic}/${it.diastolic}" },
                    note = r?.let { cardFmt.format(Date(it.recordedAt)) }
                        ?: stringResource(R.string.no_record_yet),
                )
            }
                Feature.ECG -> ecgList.maxByOrNull { it.recordedAt }.let { r ->
                FeatureCard(
                    f, Color(0xFF64A8FF), stringResource(R.string.glyph_ecg),
                    // ⚠ `classification` 是**落库值**（中文），显示要走 `EcgNote.classLabel`
                    //   —— 直接显示的话英文界面下这一格永远是中文。
                    value = r?.let { EcgNote.classLabel(it.classification) },
                    note = r?.let { cardFmt.format(Date(it.recordedAt)) }
                        ?: stringResource(R.string.no_record_yet),
                )
            }
                Feature.SPO2 -> spo2List.maxByOrNull { it.measuredAt }.let { r ->
                FeatureCard(
                    f, Color(0xFF0288D1), stringResource(R.string.glyph_spo2),
                    // 注意：SpO2Entity.percent 存的**已经是百分比整数**（入库时换算过），
                    // 这里不能再套一次 Spo2.percent() —— 那是对 Health Connect 的 0~1 原始值用的
                    value = r?.percent?.toString(),
                    unit = "%",
                    note = r?.let { cardFmt.format(Date(it.measuredAt)) }
                        ?: stringResource(R.string.no_record_yet),
                )
            }
                Feature.SLEEP -> FeatureCard(
                f, HrSleepColor, stringResource(R.string.glyph_sleep),
                // 主数值给**实际睡眠时长**（不是卧床）：与睡眠页主卡、首页三格同一口径
                value = sleepState.night?.let { fmtClock(it.actualSleepMs()) },
                note = if (sleepState.night != null) stringResource(R.string.night_sleep_synced)
                else stringResource(R.string.no_data_yet),
            )
                Feature.TEMP -> tempList.maxByOrNull { it.measuredAt }.let { r ->
                FeatureCard(
                    f, TempPointColor, stringResource(R.string.glyph_temp),
                    value = r?.let { Temperature.format(it.celsius) },
                    unit = "℃",
                    // ⚠ 来源必须说：**腕温 / 三星夜间值 / 核心估算是三样不同的量**，
                    //   这一格显示的是点检腕温，别让它看起来像"体温"
                    note = r?.let {
                        if (it.source == TempSource.WATCH) stringResource(R.string.temp_src_spot)
                        else stringResource(R.string.temp_src_night)
                    } ?: stringResource(R.string.no_record_yet),
                )
            }
                // 体成分/经期是"记录类"，没有单一数值 —— `value` 留空，只显示条数
                Feature.BIA -> FeatureCard(
                f, Color(0xFFB79BFF), stringResource(R.string.glyph_bia),
                note = if (biaList.isEmpty()) stringResource(R.string.no_record_yet)
                else stringResource(R.string.record_count_short, biaList.size) +
                    (biaList.maxByOrNull { it.id }?.let { " #${it.id}" } ?: ""),
            )
                Feature.CYCLE -> FeatureCard(
                f, CycleColor, stringResource(R.string.glyph_cycle),
                note = if (cycleList.isEmpty()) stringResource(R.string.no_record_yet)
                else stringResource(R.string.cycle_record_count, cycleList.size),
            )
                Feature.ENERGY -> energyList.maxByOrNull { it.dateEpochDay }.let { r ->
                FeatureCard(
                    f, EnergyScoreColor, stringResource(R.string.glyph_energy),
                    value = r?.score?.toString(),
                    unit = stringResource(R.string.unit_score),
                    note = r?.dateEpochDay?.toDateLabel() ?: stringResource(R.string.no_data_yet),
                )
            }
                // ── 扩展指标（2026-09-23）──
                //
                // ⚠ 这四张卡的副标题是**静态说明**，不是别处的"最近一条 + 时间"。
                // 原因：它们的数据**不落库**（进页面时现读三星），宫格这一层要显示真实值
                // 就得在这里读 15 个数据类型 —— 十几次跨进程调用只为一行副标题，不值。
                // 所以副标题改成"这里面有什么"，真实数据点进去才读。
                Feature.ACTIVITY -> FeatureCard(
                f, ActivityColor, stringResource(R.string.glyph_activity),
                note = stringResource(R.string.feat_activity_sub),
            )
                Feature.DIET -> FeatureCard(
                f, DietColor, stringResource(R.string.glyph_diet),
                note = stringResource(R.string.feat_diet_sub),
            )
                Feature.GLUCOSE -> FeatureCard(
                f, GlucoseColor, stringResource(R.string.glyph_glucose),
                note = stringResource(R.string.feat_glucose_sub),
            )
                Feature.IHRN -> FeatureCard(
                f, IhrnColor, stringResource(R.string.glyph_ihrn),
                note = stringResource(R.string.feat_ihrn_sub),
            )
            }
        }

        // ── 远程公告 ────────────────────────────────────────────────────
        // 启动时拉一次。**失败静默**：仓库内部已经兜底（用上次缓存的原文，或什么都不显示），
        // 这里不需要 try/catch，也不该给用户弹任何错误 —— 公告是附加信息。
        val annState by AnnouncementRepository.state.collectAsState()

        // ── 应用内更新（2026-10-16）──
        //
        // ⚠ 版本信息挂在**公告 JSON 的 update 块**上（App 启动本来就会拉它）⇒
        //   **不新增任何请求**；换下载地址、发新版只改 JSON，不用出包。
        AppUpdater.attach(this@MainActivity)
        var updateAuto by remember { mutableStateOf(AppUpdatePrefs.autoCheck(this@MainActivity)) }
        var updateState by remember { mutableStateOf<UpdateUi>(UpdateUi.Idle) }
        /**
         * 手表更新的待弹窗结果。
         *
         * ⚠ 2026-10-28 声明**提到 `checkUpdate` 之前**：用户要求"点一次检查更新，
         *   两端都要有结论"，所以那个 lambda 里要能写它。Kotlin 的局部变量必须先声明后使用。
         */
        var watchUpd by remember { mutableStateOf<WatchUpd?>(null) }

        /** 手动/自动检查：**强制重拉**公告（不用缓存），拿到 update 块后与本地版本比。 */
        val checkUpdate: () -> Unit = {
            updateState = UpdateUi.Checking
            scope.launch {
                runCatching { AnnouncementRepository.refresh(applicationContext, force = true) }
                AppUpdatePrefs.setLastCheckAt(applicationContext, System.currentTimeMillis())
                // ⚠⚠ 三态而不是两态（2026-10-17 改）：原来的写法把"没拉到"和"真的没新版"
                //   都报成「已是最新」—— 用户以为检查过了，其实一条公告都没读到。
                val st = AnnouncementRepository.state.value
                val info = st.update
                val mine = AnnouncementRepository.versionInfo(applicationContext).second

                // ── 顺手把手表端也查了（用户 2026-10-28 要求）──
                // ⚠ 以前只查手机：用户点"检查更新"看到"已是最新"，会以为手表也查过了，
                //   而手表其实还是旧版 —— 这是最容易误导人的地方。
                // ⚠ `latestInfo` 现在取「自带包 / 线上清单」里较大的那个（见 `WatchOta.latestInfo`）。
                val knownWatch = AppUpdatePrefs.watchVersion(applicationContext)
                val watchInfo = WatchOta.latestInfo(applicationContext).getOrNull()
                val watchLine = when {
                    knownWatch <= 0L || watchInfo == null ->
                        getString(R.string.s_1815c211)
                    watchInfo.versionCode.toLong() > knownWatch ->
                        getString(R.string.s_0074f683, "v${watchInfo.versionCode}", "v$knownWatch")
                    else -> getString(R.string.s_5a7693f2, "v$knownWatch")
                }
                val phoneUpToDate = info != null && info.versionCode <= mine
                val watchHasUpdate = watchInfo != null && knownWatch > 0L &&
                    watchInfo.versionCode.toLong() > knownWatch
                // ── 手机发现手表有新版 ⇒ **直接发指令让表自己更新**（2026-10-28）──
                //
                // ⚠ 为什么是"发指令"而不是"推包"：包让表自己从 WiFi 下（4.6MB 走蓝牙又慢又容易断），
                //   手机只负责发现版本差异。前提是**手表上开着脉迹**（蓝牙连接建立在它的同步服务上）。
                // ⚠ 发不出去（表没开 / 未连接）就退回原来的「推送弹窗」，别让用户以为已经通知了。
                var watchLineOut = watchLine
                if (watchHasUpdate) {
                    val sent = runCatching {
                        PhoneBtServer.sendFrame(Frame(FrameKinds.WATCH_UPDATE_REQ, ""))
                    }.getOrDefault(false)
                    watchLineOut = if (sent) {
                        // 不再弹旧的那个"去安装页"弹窗：表会自己下载、自己弹安装通知
                        getString(R.string.s_5a7693f2, "v$knownWatch") +
                            "，已通知手表自动更新（请保持手表上脉迹打开）"
                    } else {
                        getString(R.string.s_0074f683, "v${watchInfo!!.versionCode}", "v$knownWatch") +
                            "（手表未连接，无法自动更新）"
                    }
                    if (!sent && phoneUpToDate) watchUpd = WatchUpd.Offer(watchInfo)
                }

                updateState = when {
                    info == null && st.fetchFailed -> UpdateUi.CheckFailed(
                        st.fetchReasons.joinToString("；").ifBlank { "网络不可用" }
                    )
                    info == null -> UpdateUi.CheckFailed(
                        "公告里没有可用的更新信息（版本字段缺失或格式不合法）"
                    )
                    info.versionCode <= mine -> UpdateUi.UpToDate(info.versionCode, watchLineOut)
                    else -> UpdateUi.Available(info)
                }
            }
        }

        // 启动检测：开关开着、服务端版本**更新** ⇒ 弹窗。
        // ⚠⚠ 同一版本一天最多提醒一次（[AppUpdatePrefs.shouldNotify]）——
        //    否则每次打开应用都弹一次，那是骚扰而不是更新。
        LaunchedEffect(annState.update, updateAuto) {
            if (!updateAuto) return@LaunchedEffect
            val info = annState.update ?: return@LaunchedEffect
            if (info.versionCode <= AnnouncementRepository.versionInfo(applicationContext).second) return@LaunchedEffect
            val now = System.currentTimeMillis()
            if (!AppUpdatePrefs.shouldNotify(applicationContext, info.versionCode, now)) return@LaunchedEffect
            AppUpdatePrefs.markNotified(applicationContext, info.versionCode, now)
            updateState = UpdateUi.Available(info)
        }
        LaunchedEffect(Unit) {
            AnnouncementRepository.refresh(applicationContext)
            // 启动时清理手机端下载目录里的旧手表包（只留版本号最大的那个）
            runCatching { WatchOta.pruneKeepingNewest(applicationContext) }
            // ⚠⚠ 顺手清掉 **手机自己** 的历史安装包（2026-10-28）：
            //   以前只在"下载成功那一刻"清，而那个清理的匹配条件写错了（少了结尾斜杠，
            //   一条都匹配不上）⇒ `Download/VitalBridge/phone-update/` 里堆了四五个 15MB 的包。
            //   现在既修了条件、又补上启动清理：升级到这一版后旧包会被一次性清干净。
            runCatching {
                val vc = AnnouncementRepository.versionInfo(applicationContext).second
                AppUpdater.prunePublicDir(keep = "vitalbridge-$vc.apk")
            }
        }

        // ── 手表端自动检查（2026-10-21）──
        // ⚠ 优先级：**手机端有更新时不动手表**（用户要求"先更新手机，手机最新后再弹手表"）。
        //   手表版本来自 HELLO 里的 watchVersion（未知 = 0 时跳过，不乱猜）。
        // ⚠⚠ 2026-10-22 修"点手机端检查更新却连手表也查了"：
        //   原来是 `LaunchedEffect(updateState, annState.update)` —— **手动检查手机端**也会改
        //   `updateState` ⇒ 这个 effect 重跑 ⇒ 顺带查了手表。
        //   改成：**启动时只做一次**（键是 Unit），给手机端检查 3 秒落定时间；
        //   之后用户在「更新设置」里点哪个按钮，就只查那一端。
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(3_000)
            if (!AppUpdatePrefs.watchAuto(this@MainActivity)) return@LaunchedEffect
            if (updateState is UpdateUi.Available || updateState is UpdateUi.Downloading) return@LaunchedEffect
            val mine = AnnouncementRepository.versionInfo(applicationContext).second
            val info = annState.update
            // ⚠⚠ 2026-10-22：只有**手机端自动检查开着**时，手机才"优先占位"。
            //   原来无条件用手机版本挡住手表 ⇒ 用户**关掉手机端自动检查后**，
            //   手表明明有新版本也不弹（因为手机端还欠着一次更新，可用户并不打算自动更新它）。
            if (updateAuto && info != null && info.versionCode > mine) return@LaunchedEffect
            val known = AppUpdatePrefs.watchVersion(this@MainActivity)
            if (known <= 0L) return@LaunchedEffect
            // ⚠ `latestInfo`：手机自带的包优先（线上清单停在 188 且那版会崩）
            val w = WatchOta.latestInfo(this@MainActivity).getOrNull() ?: return@LaunchedEffect
            if (w.versionCode.toLong() <= known) return@LaunchedEffect
            watchUpd = WatchUpd.Offer(w)
        }

        // 弹窗用「一次性事件」模式：状态里出现候选 -> 取到本地变量 -> 立刻消费掉。
        // 少了"消费"这一步，每次重组都会再弹一次（Compose 的经典坑）。
        var annDialog by remember { mutableStateOf<com.gemini.healthbridge.core.AnnouncementItem?>(null) }
        LaunchedEffect(annState.popup?.id) {
            val item = annState.popup ?: return@LaunchedEffect
            annDialog = item
            // 消费即记账：记的是"弹出时刻"，所以 2 天节奏从这里起算
            AnnouncementRepository.consumePopup(applicationContext)
        }

        // 系统返回键：**逐层退出**（二级页 → 功能页 → 才真的退出 App）。
        //
        // ⚠ 2026-10-08 加，与"底部导航点不动"是同一个根源问题：覆盖层（`route` / `openFeature`）
        //   只进不出。此前**整个工程没有任何 BackHandler** ⇒ 在二级页或功能页按系统返回
        //   **直接 finish() 退出 App** —— 而界面上明明画着「返回」按钮，系统返回却退出应用，
        //   这是自相矛盾的，用户很容易当成"闪退"。
        //
        // ⚠ 顺序必须是「先 route 后 openFeature」：`route` 是更上层的那一页
        //   （它优先于标签页渲染），所以先退它才符合"返回上一层"的直觉。
        // ⚠ `enabled` 为 false 时不拦截 ⇒ 在首页/宫格页/用户页按返回仍然是正常退出 App。
        /**
         * 结束「首次主题选择」：**回首页功能页 + 放回底部导航**。
         * ⚠ 四件事必须一起做：只清 route 会停在「用户」标签且底栏仍隐藏；
         *   只清 themePicker 则路由还停在主题页，用户按返回像没反应。
         */
        val finishThemePicker: () -> Unit = {
            route = Route.Tabs
            topTab = 0
            openFeature = -1
            onThemePickerDone()
        }
        BackHandler(enabled = route != Route.Tabs || openFeature != -1 || themePicker) {
            if (themePicker) finishThemePicker()
            else if (route != Route.Tabs) route = Route.Tabs
            else openFeature = -1
        }

        Scaffold(
            // ⚠⚠⚠ 2026-11-06 主题动效：这里必须是 **Transparent**。
            //   不透明容器会把根层那片会动的背景**整块盖住** —— 主题看着和静态的一模一样，
            //   而且不会报任何错（第一版就是这样，用户直接问"怎么不动"）。
            //   底色改由根层 `ThemeBackdrop` 负责，它同时解决浅色主题顶部黑边的问题。
            //
            // ⚠⚠⚠ 2026-11-07 **`contentColor` 必须显式给**：
            //   M3 推导正文色的链路是 `contentColorFor(containerColor)`，而 `Transparent`
            //   不在配色方案里 ⇒ 推出来是 `Unspecified` ⇒ 一路 provides 下去，
            //   于是**所有没写 `color` 的 `Text` 都按默认黑色画**。深色主题上就是
            //   "标题几乎看不见、只有数字亮"（用户截图：「使用黑色字体会看不清」）。
            //   判据：**只要把 `containerColor` 改成 Transparent，就必须同时给 `contentColor`。**
            containerColor = Color.Transparent,
            contentColor = AppTextPrimary,
            topBar = {
                // targetSdk=36 → 系统强制 edge-to-edge，自定义 topBar 不会自动避让状态栏，
                // 标题会画在通知栏底下（用户截图可见）。statusBarsPadding 把整块标题下移到状态栏之下。
                // ⚠⚠ 2026-10-15 改版：**二级页不再重复显示应用名**。
                //   原来任何页面顶上都有大字「脉迹」，而功能页自己那一行（`‹ 返回 睡眠`）
                //   已经说清在哪了 —— 一屏两行标题既冗余、又白吃掉 40 多 dp 高度。
                //   ⚠ 首页 / 用户页**必须保留**：那就是它们的页面标题。
                //   ⚠⚠ `statusBarsPadding()` 无论如何都要留着 —— `DetailHeader` 自己
                //     不避让状态栏（它靠这一层提供 inset），去掉它标题会滑到状态栏底下。
                val onDetail = openFeature != -1 || route != Route.Tabs
                Column(
                    Modifier
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = if (onDetail) 0.dp else 16.dp),
                ) {
                    if (onDetail) return@Column
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.app_name), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        // ⚠ 2026-11-06 用户要求：标题右边给一个「首次使用引导」的入口。
                        //   引导原来只在装机时走一遍，之后想回看（"开发者模式到底怎么开"）
                        //   只能删数据重装 ⇒ 现在随时点得开。
                        // ⚠ 只放首页：`无线ADB` 标题右边已经是连接状态、「用户」页是资料区，
                        //   再塞一个问号会把标题行挤乱。
                        if (topTab == 0) {
                            Spacer(Modifier.width(8.dp))
                            GuideHelpButton(onClick = onReplayGuide)
                        }
                        // ⚠ 2026-10-26 用户要求：连接状态固定在标题右边，不再单独占一张卡
                        if (topTab == 1) {
                            Spacer(Modifier.width(10.dp))
                            WatchAdbStatusChip()
                        }
                    }
                    // ⚠⚠ 功能页这里**不再有**「手表：… · 待同步 HR0…」与「蓝牙：…」两行（2026-10-13）。
                    //
                    // 它们与首页那张状态卡里的内容**同义重复**，而且经常互相打架 ——
                    // 用户截图里同一屏同时出现「手表在线：SM-L310」「蓝牙：请打开手表端进行连接」
                    // 「手表已连接：SM-L310」三句结论，其中第三句还是**上一次连接留下的旧事件**。
                    // 用户的原话：「重复了删掉，没必要显示」。
                    //
                    // ⇒ 判据：**手表连没连上、同步过没有，只在首页那张状态卡上说一次**。
                    //   ⚠ 待同步条数**一并去掉**（那是给开发者看的诊断量，用户看不懂 HR/BP/ECG/SPO2 这串缩写，
                    //     而每一栏都是 0 时更是纯噪声）；真正需要的是"数据进没进来"，
                    //     它由各功能页自己的内容回答。
                    if (topTab == 2) {
                        Text(stringResource(R.string.tab_user_sub), fontSize = 12.sp, color = AppTextMuted)
                    }
                }
            },
            // 底部导航常驻：功能入口与用户入口永远在下方（用户要求）。
            // 二级页也保留它 —— 这样在任何一个功能里都能一键跳到「用户」。

            bottomBar = {
                // ⚠ 2026-11-07 首次主题选择页**整页不显示底部导航**（用户要求）：
                //   bottomBar 给空内容即不占位，主题页能铺到底。
                //   ⚠ 保持 `MainBottomBar(topTab)` 这个调用形式 —— 静态校验脚本靠它定位。
                if (!themePicker) MainBottomBar(topTab) { idx ->
                    topTab = idx
                    // ⚠⚠ 必须**同时清掉二级页路由**（2026-10-08 修的 bug）。
                    //
                    // `route` **优先于**标签页：下面那个 `when (val r = route)` 里
                    // 每个分支都 `return@Column`。所以只改 `topTab` 时，
                    // 只要还停在二级页（心电详情 / 记录 / 睡眠详情 / 授权 / 公告），
                    // `when` 就照旧把那一页整屏画出来 ——
                    // 表现就是**"点了底部导航，什么都没发生"**
                    // （用户原话：「打开很多功能后，再按用户，并不能到用户页面」）。
                    //
                    // ⚠ 上面那句注释「在任何一个功能里都能一键跳到用户」**本来就是这个意图**，
                    //   只是实现漏了这一行。⇒ 注释与实现不一致时，以用户的体感为准。
                    route = Route.Tabs
                    openFeature = -1      // 切导航时回到宫格/用户页顶层，避免"停在某个功能里"
                }
            },
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize()) {

            // 更新弹窗放在内容 lambda 末尾 ⇒ 任何二级页上都能弹出来。
            val us = updateState
            if (us is UpdateUi.Available || us is UpdateUi.Downloading || us is UpdateUi.Ready) {
                UpdateDialog(
                    state = us,
                    onDismiss = {
                        // ⚠ 下载中不许关：关了用户以为取消了，但流量还在跑
                        if (us !is UpdateUi.Downloading) updateState = UpdateUi.Idle
                    },
                    onDownload = { info ->
                        updateState = UpdateUi.Downloading(info)
                        scope.launch {
                            val r = AppUpdater.download(info) { p ->
                                updateState = UpdateUi.Downloading(info, p)
                            }
                            r.onSuccess { f ->
                                updateState = UpdateUi.Ready(f)
                                runCatching { AppUpdater.install(applicationContext, f) }
                                    .onFailure { updateState = UpdateUi.Failed("调不起系统安装器") }
                            }.onFailure { e ->
                                updateState = UpdateUi.Failed(e.message ?: e.javaClass.simpleName)
                            }
                        }
                    },
                )
            }
            // 手表端有新版：只说"有新版 + 更新内容"，安装动作挪到「安装」页（2026-10-26）
            watchUpd?.let { st ->
                WatchUpdateDialog(
                    state = st,
                    onStart = {
                        watchUpd = null
                        topTab = 1
                    },
                    onDismiss = { watchUpd = null },
                )
            }

            if (us is UpdateUi.Failed) {
                AlertDialog(
                    onDismissRequest = { updateState = UpdateUi.Idle },
                    // ⚠ `s_8abfde32` 是「下载失败：%1$s」——reason 必须作为**格式实参**传进去，
                    //   否则运行期抛 MissingFormatArgumentException（这条被静态校验脚本钉着）。
                    title = { Text(stringResource(R.string.s_8abfde32, us.detail)) },
                    confirmButton = {
                        AppPrimaryButton(onClick = { updateState = UpdateUi.Idle }) {
                            Text(stringResource(R.string.s_1faf4f0c))
                        }
                    },
                )
            }
            if (us is UpdateUi.CheckFailed) {
                AlertDialog(
                    onDismissRequest = { updateState = UpdateUi.Idle },
                    title = { Text(stringResource(R.string.s_95931fef)) },
                    text = { Text(stringResource(R.string.s_7c637542, us.detail), fontSize = 13.sp) },
                    confirmButton = {
                        AppPrimaryButton(onClick = { updateState = UpdateUi.Idle }) {
                            Text(stringResource(R.string.s_1faf4f0c))
                        }
                    },
                )
            }
            // ⚠ 检查中给个反馈：否则点「检查更新」后几秒里界面毫无反应，像坏了。
            //   非可关（检查是瞬时的、会自动跳到下一步），避免关掉后后台还在跑。
            if (us is UpdateUi.Checking) {
                AlertDialog(
                    onDismissRequest = { },
                    title = { Text(stringResource(R.string.s_24aad6d9)) },
                    text = { Text(stringResource(R.string.s_ce6062a0)) },
                    confirmButton = { },
                )
            }
            if (us is UpdateUi.UpToDate) {
                AlertDialog(
                    onDismissRequest = { updateState = UpdateUi.Idle },
                    title = { Text(stringResource(R.string.s_6a35bac9)) },
                    // ⚠ 把服务端读到的主版本显示出来（2026-10-17）：否则"没拉到旧公告"
                    //   与"真的没新版"在界面上完全一样，连排查都没法下手。
                    text = {
                        // ⚠ `AlertDialog` 的 `text` 槽内部是 **Box（堆叠）**，
                        //   多个 `Text` 平级传进去会重叠 ⇒ 必须自己包一层 `Column`。
                        Column {
                            us.serverVersion?.let {
                                Text(stringResource(R.string.s_9ed81518, it), fontSize = 13.sp)
                            }
                            us.watchLine?.let {
                                if (us.serverVersion != null) Spacer(Modifier.height(6.dp))
                                Text(it, fontSize = 13.sp)
                            }
                        }
                    },
                    confirmButton = {
                        AppPrimaryButton(onClick = { updateState = UpdateUi.Idle }) {
                            Text(stringResource(R.string.s_1faf4f0c))
                        }
                    },
                )
            }
                // 三星健康授权（**合并成一次请求**，见 `SamsungHealthReader.request`）。
                //
                // ⚠ 抽成具名 lambda 是因为它有**两个调用方**：用户页那一行单独的「去授权」，
                //   以及「授权」页里的「一键授权」按钮 —— 后者要在它**返回之后**接着弹
                //   Health Connect 的授权页（两个系统授权页不能同时弹，只能串行），
                //   所以这里必须是 **suspend**、调用方能 await 它。
                //   两处共用同一份实现，避免"单独授权"与"一键授权"出现行为差异。
                //
                // ⚠⚠ 定义位置在路由 `when` **之前**（2026-10-07）：`Route.PersonAuth` 分支要用它，
                //   而 Kotlin 的局部 `val` 必须先声明后使用。之前它在 `when` 之后、
                //   只有 `UserTab` 一个调用方时没问题；抽成整屏授权页后就编不过了。
                val doAuthSamsung: suspend () -> Unit = {
                    try {
                        val granted = SamsungHealthReader.request(this@MainActivity)
                        samsungGranted = granted.isNotEmpty()
                        samsungSleepOk =
                            SamsungHealthReader.hasSleepPermissionWithRetry(applicationContext)
                        samsungSkinTempOk = SamsungHealthReader
                            .hasSkinTempPermissionWithRetry(applicationContext)
                        samsungBiaOk = SamsungHealthReader
                            .hasBodyCompositionPermissionWithRetry(applicationContext)
                        samsungProfileOk = SamsungHealthReader
                            .hasUserProfilePermissionWithRetry(applicationContext)
                        val hasWrite = granted.any { it.accessType == AccessType.WRITE }
                        // ⚠ 这几段（msg / sleepNote / biaNote）最终会拼进
                        //   `SyncRepository.setEvent(...)`，所以**必须是 ResText**：
                        //   混进一个已求值的串，切语言后它就会单独停在旧语言。
                        //   平台给的错误文本（`err`）与语言无关，走 `raw`。
                        val msg = if (hasWrite) ResText.of(R.string.s_4bbadcd5)
                        else {
                            val err = SamsungHealthReader.lastWriteAuthError
                            ResText.of(R.string.s_0be630e0) +
                                (err?.let { ResText.raw("（$it）") } ?: ResText.EMPTY)
                        }
                        // 睡眠权限失败要显式说出来：否则"睡眠时段一直没染色"会变成一个查不出原因的现象
                        val sleepNote = if (samsungSleepOk == true) ResText.EMPTY
                        else ResText.of(R.string.s_cdd0fbda) +
                            (SamsungHealthReader.lastSleepAuthError?.let { ResText.raw("（$it）") } ?: ResText.EMPTY)
                        // 用户档案权限失败要说出来：它只影响手表端的体成分测量，
                        // 不说的话表现只是"手表上体成分页一直提示缺资料"，很难查到原因
                        val biaNote = if (samsungProfileOk == true) ResText.EMPTY
                        else ResText.of(R.string.s_ad8803da) +
                            (SamsungHealthReader.lastBiaAuthError?.let { ResText.raw("（$it）") } ?: ResText.EMPTY)
                        SyncRepository.setEvent(msg + sleepNote + biaNote)
                        // 授权完立刻同步一次：不用用户再去找"导入/刷新"按钮
                        syncTick++
                    } catch (e: ResolvablePlatformException) {
                        // 三星健康需要更新/初始化等，可由 SDK 拉起解决界面
                        if (e.hasResolution) {
                            runCatching { e.resolve(this@MainActivity) }
                        }
                        SyncRepository.setEvent(
                            ResText.of(R.string.s_ca81b85f, e.errorMessage)
                        )
                    } catch (e: com.samsung.android.sdk.health.data.error.PlatformInternalException) {
                        SyncRepository.setEvent(
                            ResText.of(R.string.s_2562d1b9, e.errorCode)
                        )
                    } catch (e: Exception) {
                        SyncRepository.setEvent(
                            ResText.of(R.string.s_8ab4a151, e.javaClass.simpleName, e.message)
                        )
                    }
                }

                // 单组授权：只弹**这一组**的系统授权页（「一键授权」仍走 doAuthSamsung）。
                // ⚠ 也要把界面状态重查一遍，否则授权完那几行还显示"未授权"。
                val doAuthSamsungGroup: suspend (Set<Permission>) -> Unit = { perms ->
                    try {
                        SamsungHealthReader.requestOnly(this@MainActivity, perms)
                        SamsungHealthReader.grantedWithRetry(applicationContext)
                            ?.let { samsungGranted = it.isNotEmpty() }
                        samsungSleepOk =
                            SamsungHealthReader.hasSleepPermissionWithRetry(applicationContext)
                        samsungSkinTempOk = SamsungHealthReader
                            .hasSkinTempPermissionWithRetry(applicationContext)
                        samsungBiaOk = SamsungHealthReader
                            .hasBodyCompositionPermissionWithRetry(applicationContext)
                        samsungProfileOk = SamsungHealthReader
                            .hasUserProfilePermissionWithRetry(applicationContext)
                        syncTick++
                    } catch (e: ResolvablePlatformException) {
                        if (e.hasResolution) {
                            runCatching { e.resolve(this@MainActivity) }
                        }
                        SyncRepository.setEvent(
                            ResText.of(R.string.s_ca81b85f, e.errorMessage)
                        )
                    } catch (e: com.samsung.android.sdk.health.data.error.PlatformInternalException) {
                        SyncRepository.setEvent(
                            ResText.of(R.string.s_2562d1b9, e.errorCode)
                        )
                    } catch (e: Exception) {
                        SyncRepository.setEvent(
                            ResText.of(R.string.s_8ab4a151, e.javaClass.simpleName, e.message)
                        )
                    }
                }

                // 路由优先于标签页：详情/记录页整屏铺开，返回后回到原来的标签页
                //
                // ⚠⚠ 这里**不能再用 `return@Column`**（2026-10-10 改）：每个分支现在要把
                //   整屏内容包进 `screenStates.SaveableStateProvider(key)`，而
                //   **在嵌套 lambda 里 `return@Column` 是编不过的**（标签指向的是外层 Column）
                //   —— 为了保住滚动位置，改用"置位 + 统一返回"这个等价写法。
                //   两种写法的语义完全一样：命中任一路由就不渲染标签页那一层。
                var routeHandled = false
                when (val r = route) {
                    is Route.EcgDetail -> {
                        screenStates.SaveableStateProvider("route:ecg-detail") {
                            EcgDetailScreen(
                                record = r.record,
                                hrv = hrvList,
                                temps = tempList,
                                onBack = { route = Route.Tabs },
                            )
                        }
                        routeHandled = true
                    }
                    is Route.Overview -> {
                        // 口径与首页那张三格卡**完全一致**（今天最新一条 / 昨晚实际睡眠），
                        // 否则同一个数在两处长得不一样，用户会以为哪边算错了。
                        val today0 = todayStartMs()
                        val epochDay = java.time.LocalDate.now().toEpochDay()
                        val day = activityDayList.firstOrNull { it.dateEpochDay == epochDay }
                        val hrNow = hrList.filter { it.measuredAt >= today0 }
                            .maxByOrNull { it.measuredAt }
                        val spo2Now = spo2List.filter { it.measuredAt >= today0 }
                            .maxByOrNull { it.measuredAt }
                        val bpNow = bpList.filter { it.recordedAt >= today0 }
                            .maxByOrNull { it.recordedAt }
                        val tempNow = tempList.filter { it.measuredAt >= today0 }
                            .maxByOrNull { it.measuredAt }
                        val lastNight = sleepNightList.maxByOrNull { it.dateEpochDay }
                        // ⚠ 必须包 `SaveableStateProvider`：`route` 命中时标签页那层不再渲染，
                        //   子树被拆掉。不保存的话「返回再进」滚动位置就丢了
                        //   （静态校验脚本会数 provider 与 routeHandled 的个数）。
                        screenStates.SaveableStateProvider("route:overview") {
                            OverviewScreen(
                                data = OverviewData(
                                    steps = day?.steps,
                                    slotSteps = day?.slotSteps
                                        ?.split(",")
                                        ?.mapNotNull { it.trim().toLongOrNull() }
                                        ?: emptyList(),
                                    hr = hrNow?.bpm?.toInt(),
                                    spo2 = spo2Now?.percent?.toInt(),
                                    sleepText = lastNight?.let { fmtClock(it.actualSleepMs) },
                                    tempText = tempNow?.let { Temperature.format(it.celsius) },
                                    // 血压的"一个值"本身就是「收缩/舒张」两段
                                    bpText = bpNow?.let { "${it.systolic}/${it.diastolic}" },
                                ),
                                onBack = { route = Route.Tabs },
                            )
                        }
                        routeHandled = true
                    }
                    is Route.Theme -> {
                        // ⚠ 与其它 route 一样必须包 `SaveableStateProvider`：
                        //   静态校验脚本会数「route 分支数 vs provider 数」，
                        //   少一个就报，而且"返回再进"时横向翻页的位置会丢。
                        screenStates.SaveableStateProvider("route:theme") {
                            ThemeScreen(onBack = {
                                // 首次主题选择：点左上返回 ⇒ 结束 picker、回首页功能页。
                                // 常态（从「用户」页进）：只退这一层，仍留在「用户」标签。
                                if (themePicker) finishThemePicker() else route = Route.Tabs
                            })
                        }
                        routeHandled = true
                    }
                    is Route.Records -> {
                        // ⚠ key 必须带上 `kind`：心率记录页与血氧记录页是**两个页面**，
                        //   共用一个 key 会让后者恢复成前者的滚动位置。
                        screenStates.SaveableStateProvider("route:records:" + r.kind.name) {
                            RecordsScreen(
                                kind = r.kind,
                                hr = hrList, bp = bpList, spo2 = spo2List, temp = tempList,
                                bia = biaList, activity = activityList, ppg = ppgList,
                                onBack = { route = Route.Tabs },
                                // ⚠ 同步结果在这里说（见 RecordsScreen 的注释）
                                event = event,
                            )
                        }
                        routeHandled = true
                    }
                    is Route.SleepDetail -> {
                        screenStates.SaveableStateProvider("route:sleep-detail:" + r.kind.name) {
                            SleepDetailScreen(
                                kind = r.kind,
                                state = sleepState,
                                loading = sleepLoading,
                                // 睡眠历史（含阶段段）——「睡眠阶段」详情页用它画多晚趋势。
                                // ⚠ 只在阶段页用到，但**从这里整条传下去**而不是在页面内部自己订阅：
                                //   页面内部再订阅一次会让"同一份数据"有两个来源，
                                //   而 Compose 里重复订阅同一张表还会多一次无谓的重组。
                                nights = sleepNightList,
                                onBack = { route = Route.Tabs },
                                onRefresh = { syncTick++ },
                            )
                        }
                        routeHandled = true
                    }
                    // 历史某一晚的详情（2026-10-10）：数据全部来自库里那一行，不读三星。
                    // ⚠ 找不到那一行时**退回首页**而不是画一个空页：
                    //   能走到这里说明用户刚在列表上点的那一行存在过，
                    //   而 `sleepNightList` 是 `recent(400)` 的 Flow —— 极端情况下
                    //   （刚被 `keepLatest` 裁掉）会取不到，那时留在空页上会让人以为是 bug。
                    is Route.SleepNight -> {
                        val entity = sleepNightList.firstOrNull { it.dateEpochDay == r.epochDay }
                        if (entity == null) {
                            route = Route.Tabs
                        } else {
                            // ⚠ key 带上 `epochDay`：从 10月9日 进详情、返回、再从 10月8日 进，
                            //   若共用一个 key，第二晚会继承第一晚的滚动位置。
                            screenStates.SaveableStateProvider("route:sleep-night:" + r.epochDay) {
                                SleepNightHistoryScreen(
                                    entity = entity,
                                    onBack = { route = Route.Tabs },
                                    // 这一页顶部的日期条（2026-10-12）：可以直接翻到别的晚上，
                                    // 不用退回列表再点一次。选"今天"就回实时页。
                                    nights = sleepNightList,
                                    onPickDay = { day ->
                                        route = if (day == java.time.LocalDate.now().toEpochDay()) {
                                            Route.Tabs
                                        } else {
                                            Route.SleepNight(day)
                                        }
                                    },
                                )
                            }
                        }
                        routeHandled = true
                    }
                    // 某一天的心率 / 血氧（2026-10-13）。入口是这两页标题行右侧的「今天 ▾」。
                    // ⚠ 与 `Route.SleepNight` 同一套：数据全来自库里，不读三星；
                    //   key 里带上 `kind` 与 `epochDay`，否则从"心率 10月12日"退出来再进
                    //   "血氧 10月10日"会继承上一页的滚动位置（`SaveableStateHolder` 按 key 存）。
                    is Route.DayHistory -> {
                        screenStates.SaveableStateProvider(
                            "route:day-history:" + r.kind.name + ":" + r.epochDay
                        ) {
                            DayHistoryScreen(
                                kind = r.kind,
                                epochDay = r.epochDay,
                                hr = hrList,
                                spo2 = spo2List,
                                // 库里那几晚：日历"有数据"的日期 + 「睡眠期统计」卡
                                nights = sleepNightList,
                                onBack = { route = Route.Tabs },
                                onPickDay = { day ->
                                    // "今天"没有独立的记录页（今天在实时页上看）⇒ 回实时页
                                    route = if (day == java.time.LocalDate.now().toEpochDay()) {
                                        Route.Tabs
                                    } else {
                                        Route.DayHistory(r.kind, day)
                                    }
                                },
                            )
                        }
                        routeHandled = true
                    }
                    is Route.Legal -> {
                        screenStates.SaveableStateProvider("route:legal" + r.which) {
                            LegalScreen(which = r.which, onBack = { route = Route.Tabs })
                        }
                        routeHandled = true
                    }
                    is Route.Announcements -> {
                        screenStates.SaveableStateProvider("route:announcements") {
                            AnnouncementsScreen(
                                state = annState,
                                readIds = annState.readIds,
                                onBack = { route = Route.Tabs },
                                onOpenLink = { url -> openUrl(url) },
                                onRetry = {
                                    scope.launch {
                                        AnnouncementRepository.refresh(applicationContext, force = true)
                                    }
                                },
                                onMarkRead = { id ->
                                    scope.launch { AnnouncementRepository.markRead(applicationContext, id) }
                                },
                            )
                        }
                        routeHandled = true
                    }
                    // 授权页（2026-10-07）：入口是「用户」页「授权」那一行。
                    // 状态与「用户」页用的是**同一批**（从 MainScreen 往下传的那几个），
                    // 所以授权页改完回到列表，入口行的「还有 N 项未授权」当场就是新的。
                    is Route.Update -> {
                        screenStates.SaveableStateProvider("route:update") {
                            UpdateScreen(
                                phoneAuto = updateAuto,
                                onPhoneAuto = { on ->
                                    updateAuto = on
                                    AppUpdatePrefs.setAutoCheck(this@MainActivity, on)
                                },
                                onCheckPhone = { checkUpdate() },
                                // ⚠ 只查**手表**：拉清单比版本，结果统一进弹窗（不写进行内 `value`）
                                onCheckWatch = {
                                    scope.launch {
                                        val known = AppUpdatePrefs.watchVersion(this@MainActivity)
                                        val info = WatchOta.latestInfo(this@MainActivity).getOrNull()
                                        watchUpd = when {
                                            info == null -> WatchUpd.Failed(
                                                getString(R.string.s_97a82e5e),
                                            )
                                            known <= 0L -> WatchUpd.Failed(
                                                getString(R.string.s_9ab316e6),
                                            )
                                            info.versionCode.toLong() <= known -> WatchUpd.Failed(
                                                getString(R.string.s_f46c6090) + "（v$known）",
                                            )
                                            else -> WatchUpd.Offer(info)
                                        }
                                    }
                                },
                                onBack = { route = Route.Tabs },
                            )
                        }
                        // ⚠ 这里原来是 `return@Column`（2026-11-03 修）：它在 provider 的
                        //   嵌套 lambda 里 —— 语义上"早退"是对的，但与其它 8 个分支写法不一致，
                        //   而且这条路径绕过了统一出口，后面再加分支时极易漏。
                        //   改成置位，由 `if (routeHandled) return@Column` 统一处理。
                        routeHandled = true
                    }

                    is Route.PersonAuth -> {
                        screenStates.SaveableStateProvider("route:person-auth") {
                            PersonAuthScreen(
                                samsungGranted = samsungGranted,
                                samsungSleepOk = samsungSleepOk,
                                samsungSkinTempOk = samsungSkinTempOk,
                                samsungBiaOk = samsungBiaOk,
                                samsungProfileOk = samsungProfileOk,
                                hcAvailable = hcAvailable,
                                hcGranted = hcGranted,
                                hcMissing = hcMissing,
                                hcAuthErr = hcAuthErr,
                                onAuthHC = {
                                    hcAuthErr = null
                                    runCatching {
                                        hcLauncher.launch(HealthConnectManager.writePermissions)
                                    }.onFailure {
                                        hcAuthErr = "${it.javaClass.simpleName}: ${it.message}"
                                    }
                                },
                                onAuthSamsungAwait = doAuthSamsung,
                                onAuthSamsungGroup = doAuthSamsungGroup,
                                authScope = scope,
                                onBack = { route = Route.Tabs },
                            )
                        }
                        routeHandled = true
                    }
                    Route.Tabs -> Unit
                }
                if (routeHandled) return@Column
                // ⚠ `openFeature` 是 Feature 枚举，不是中文名字 —— 拿名字当分派键
                // 在非中文界面下必然失配（见 Feature 的说明）。显示名由 `title()` 取。
                val openFeat = tabs.getOrNull(openFeature)

                // ⚠⚠ 这一整块（宫格 / 用户页 / 某个功能页）**包在状态托管里**（2026-10-10）：
                //   它下面每一屏都是 `LazyColumn`，滚动位置靠 `rememberLazyListState` 活
                //   （内部是 `rememberSaveable`）。进二级页时整块离开组合 ⇒ 没有托管就等于
                //   丢掉位置 ⇒ 返回后跳回顶部。
                //   key 按**当前是哪一屏**取（宫格 / 用户 / 功能名），换屏就换 key、
                //   同一个 key 再回来就恢复。
                //   ⚠ `openFeat.name` 是 **Feature 枚举名**（身份，永不翻译），不要用中文标题。
                val screenKey = when {
                    topTab == 1 -> "tab:adb"
                    topTab == 2 -> "tab:user"
                    openFeat == null -> "tab:grid"
                    else -> "feat:" + openFeat.name
                }
                screenStates.SaveableStateProvider(screenKey) {
                    if (topTab == 1) {
                        // 「安装」页（无线 ADB）：整页自带标题，不需要外面再给 header 文案
                        AdbTab()
                    } else if (topTab == 2) {
                        UserTab(
                            samsungGranted = samsungGranted,
                            samsungSleepOk = samsungSleepOk,
                            samsungSkinTempOk = samsungSkinTempOk,
                            samsungBiaOk = samsungBiaOk,
                            samsungProfileOk = samsungProfileOk,
                            samsungUnreadable = samsungUnreadable,
                            syncNote = syncNote,
                            diag = diag,
                            hcAvailable = hcAvailable,
                            hcGranted = hcGranted,
                            hcMissing = hcMissing,
                            hcAuthErr = hcAuthErr,
                            onAuthHC = {
                                hcAuthErr = null
                                runCatching {
                                    hcLauncher.launch(HealthConnectManager.writePermissions)
                                }.onFailure {
                                    hcAuthErr = "${it.javaClass.simpleName}: ${it.message}"
                                }
                            },
                            onOpenLegal = { which -> route = Route.Legal(which) },
                            onOpenTheme = { route = Route.Theme },
                            updateAuto = updateAuto,
                            onUpdateAuto = { updateAuto = it },
                            onCheckUpdate = { checkUpdate() },
                            onOpenUpdate = { route = Route.Update },
                            onDiagnose = {
                                scope.launch {
                                    diag = this@MainActivity.getString(R.string.s_d5fc6090)
                                    diag = SyncRepository.diagnose(applicationContext)
                                }
                            },
                            // 「授权」页的「一键授权」按钮用它：suspend，自己 await 完再接着弹 HC。
                            // 用户页所有授权动作**都走这一个**（单独点某一项也一样），
                            // 于是"单独授权"与"一键授权"不可能出现两套行为。
                            onAuthSamsungAwait = doAuthSamsung,
                            // 「授权」那一行点进去是**整屏的授权页**（2026-10-07 用户要求：
                            // 原来是这一行下面展开的面板，用户说要"另一个新的页面"）。
                            // 与公告页同走 `Route`：返回路径唯一。
                            onOpenAuth = { route = Route.PersonAuth },
                            // 补导历史：自动同步只覆盖今天（心率/血氧）与近 30 天（血压），
                            // 想要更早的历史记录可以走这里。属于可选项，不影响日常使用。
                            onBackfill = {
                                scope.launch {
                                    SyncRepository.setEvent(ResText.of(R.string.s_5b299a2e))
                                    SyncRepository.setEvent(
                                        SamsungHealthReader.importRecent(applicationContext, 30)
                                    )
                                }
                            },
                            backupBusy = backupBusy,
                            backupPath = backupPath,
                            snapshotCount = snapshotCount,
                            // 导出不立刻执行：先弹窗提醒"先把数据凑齐再导"，见 MainScreen 末尾的 AlertDialog
                            onExport = { showExportDialog = true },
                            onRestore = { restoreLauncher.launch(DataBackup.RESTORE_MIME_TYPES) },
                            cycleEnabled = cycleEnabled,
                            onToggleCycle = { on ->
                                cycleEnabled = on
                                CycleSettings.setEnabled(applicationContext, on)
                                // 关掉时若正停在「经期」页，回到宫格页 ——
                                // 那一项已经从 [tabs] 里消失了，不退出去会停在一个不存在的功能上
                                if (!on && openFeat == Feature.CYCLE) openFeature = -1
                            },
                            annUnread = annState.items.count { it.id !in annState.readIds },
                            annError = annState.parseError,
                            onOpenAnnouncements = { route = Route.Announcements },
                            onOpenLink = { url -> openUrl(url) },
                        )
                    } else if (openFeat == null) {
                        // 停在宫格页：全部功能 + 顶部同步状态条
                        // 「今日概览」三格（2026-10-15）：**今天最新一条**，不是日均值 ——
                        // 日均值会把"早上一次 88"和"下午一直在 70"抹成一个数，
                        // 而用户想先知道的是"现在多少"。
                        // ⚠ 睡眠取**昨晚实际睡眠**（不是卧床），与睡眠页主卡同一个口径。
                        val t0 = java.time.LocalDate.now().atStartOfDay(
                            java.time.ZoneId.systemDefault()
                        ).toInstant().toEpochMilli()
                        val hrNow = hrList.filter { it.measuredAt >= t0 }
                            .maxByOrNull { it.measuredAt }
                        val spo2Now = spo2List.filter { it.measuredAt >= t0 }
                            .maxByOrNull { it.measuredAt }
                        val lastNight = sleepNightList.maxByOrNull { it.dateEpochDay }
                        val todayStats = Triple(
                            hrNow?.let { "${it.bpm.toInt()}" } ?: "--",
                            spo2Now?.let { "${it.percent}%" } ?: "--",
                            lastNight?.let { fmtClock(it.actualSleepMs) } ?: "--",
                        )
                        FeatureGrid(
                            cards = featureCards,
                            device = device,
                            btConnected = btConnected,
                            btStatus = btStatus,
                            samsungSyncedToday = samsungSyncedToday,
                            onOpen = { openFeature = it },
                            todayStats = todayStats,
                            onOpenOverview = { route = Route.Overview },
                        )
                    } else {
                        // 打开某个功能：整屏内容 + 一个返回（沿用其它二级页的 DetailHeader 模式）
                        //
                        // ⚠ 「查看历史」的日期胶囊挂在**标题行右边**（不是单独一条横带）——
                        //   理由见 `DetailHeader` 的 KDoc。弹层也放在**这一层**：
                        //   它在各 Tab 的 early return 之外，所以"读不到数据"时照样点得开。
                        //
                        // ⚠⚠ 睡眠 / 心率 / 血氧**共用同一个胶囊与同一个月历**，只有
                        //   "哪天有数据 + 选完去哪一页"这三处不同（2026-10-13 用户要求
                        //   「血氧、心率使用跟睡眠一样的查看历史按钮」）。
                        //   三份各自实现必然漂移，而这个漂移**不报错**。
                        var historyPickerOpen by remember { mutableStateOf(false) }
                        // 腕表温度的选中日（0L = 今天）。⚠ 用 `rememberSaveable` 而不是 `remember`：
                        //   这一页是**原地切换**（不跳路由），切到别的标签页再回来要还在
                        //   （配合外层 `SaveableStateProvider`，与活动页的 `selectedDay` 同一条理由）。
                        var tempDay by rememberSaveable { mutableLongStateOf(0L) }
                        val todayEpochDay = remember { java.time.LocalDate.now().toEpochDay() }
                        // 「有数据的日期」：睡眠看库里落下的那些晚；
                        // 心率/血氧看**库里已有的点测记录 ∪ 有睡眠统计的那几晚**
                        // （规则只写在 `vitalsDays` 一处，按日页那个日历也调它）。
                        // ⚠ 不读三星健康做日历：那要给每个可见月份都发一次范围查询，
                        //   而"哪几天有"这件事从库里就能算出来（且离线也一致）。
                        // ⚠ 三条各自 `remember` 住：否则每次重组都是新 List，
                        //   下面 `HistoryDateSheet` 里 `remember(days)` 就形同虚设。
                        val sleepDays = remember(sleepNightList) { sleepNightList.map { it.dateEpochDay } }
                        val hrDays = remember(hrList, spo2List, sleepNightList, vitalDays) {
                            vitalsDays(RecordKind.HR, hrList, spo2List, sleepNightList, vitalDays)
                        }
                        val spo2Days = remember(hrList, spo2List, sleepNightList, vitalDays) {
                            vitalsDays(RecordKind.SPO2, hrList, spo2List, sleepNightList, vitalDays)
                        }
                        // 腕表温度：本机点检 + 三星皮肤温度**两类都在库里**（同步每天落），
                        // 所以「哪天有数据」从库里算得出来，不必去问三星。
                        val tempDays = remember(tempList) {
                            tempList.map { msToEpochDay(it.measuredAt) }.distinct()
                        }
                        val historyDays: List<Long>? = when (openFeat) {
                            Feature.SLEEP -> sleepDays
                            Feature.HR -> hrDays
                            Feature.SPO2 -> spo2Days
                            Feature.TEMP -> tempDays
                            else -> null
                        }
                        Column(Modifier.fillMaxSize()) {
                            DetailHeader(
                                title = openFeat.title(),
                                onBack = { openFeature = -1 },
                                trailing = if (historyDays != null) {
                                    {
                                        // ⚠ 选中别的日子时，胶囊要显示**那天**而不是「今天」
                                        //   （否则标题写着 10月13日、胶囊写着「今天」，自相矛盾）。
                                        val picked = if (openFeat == Feature.TEMP) {
                                            if (tempDay == 0L) todayEpochDay else tempDay
                                        } else {
                                            todayEpochDay
                                        }
                                        HistoryDatePill(
                                            if (picked == todayEpochDay) {
                                                stringResource(R.string.s_0028aa87)
                                            } else {
                                                rememberShortDateFmt().format(Date(epochDayToMs(picked)))
                                            }
                                        ) { historyPickerOpen = true }
                                    }
                                } else null,
                            )
                            if (historyPickerOpen && historyDays != null) {
                                HistoryDateSheet(
                                    days = historyDays,
                                    selected = if (openFeat == Feature.TEMP) {
                                        if (tempDay == 0L) todayEpochDay else tempDay
                                    } else {
                                        todayEpochDay
                                    },
                                    today = todayEpochDay,
                                    tint = if (openFeat == Feature.SLEEP) sleepDayTint(sleepNightList)
                                    else ({ _: Long -> HistoryDayHasDataColor }),
                                    onPick = { day ->
                                        historyPickerOpen = false
                                        // 点今天 = 回到实时；点别的日子 = 看那一天
                                        if (openFeat == Feature.TEMP) {
                                            // ⚠⚠ 腕表温度是**原地切换**（不跳路由）：
                                            //   这一页卡片少，跳独立页反而要把三张卡各写一遍。
                                            //   0L 代表今天 —— 与活动页 `selectedDay` 同一个约定。
                                            tempDay = if (day == todayEpochDay) 0L else day
                                        } else if (day != todayEpochDay) {
                                            route = when (openFeat) {
                                                Feature.SLEEP -> Route.SleepNight(day)
                                                Feature.HR -> Route.DayHistory(RecordKind.HR, day)
                                                Feature.SPO2 -> Route.DayHistory(RecordKind.SPO2, day)
                                                else -> Route.Tabs
                                            }
                                        }
                                    },
                                    onDismiss = { historyPickerOpen = false },
                                )
                            }
                            Box(Modifier.fillMaxWidth().weight(1f)) {
                                when (openFeat) {
                                Feature.HR -> HeartRateTab(
                            records = hrList,
                            samsungGranted = samsungGranted,
                            samsungUnreadable = samsungUnreadable,
                            samsungSleepOk = samsungSleepOk,
                            onOpenRecords = { route = Route.Records(RecordKind.HR) },
                            onAuth = doAuthSamsung,
                        )
                                Feature.BP -> BpTab(
                            records = bpList,
                            ecgCount = ecgList.size,
                            onOpenRecords = { route = Route.Records(RecordKind.BP) },
                        )
                                Feature.SPO2 -> Spo2Tab(
                            records = spo2List,
                            samsungGranted = samsungGranted,
                            samsungUnreadable = samsungUnreadable,
                            state = sleepState,
                            loading = sleepLoading,
                            onOpenRecords = { route = Route.Records(RecordKind.SPO2) },
                            onOpenDetail = { route = Route.SleepDetail(SleepDetailKind.SPO2) },
                            ppg = ppgList,
                            onOpenPpgRecords = { route = Route.Records(RecordKind.PPG) },
                        )
                                Feature.SLEEP -> SleepTab(
                            apnea = apneaList,
                            state = sleepState,
                            loading = sleepLoading,
                            samsungGranted = samsungGranted,
                            samsungUnreadable = samsungUnreadable,
                            samsungSleepOk = samsungSleepOk,
                            odi = odiList.firstOrNull(),
                            nights = sleepNightList,
                            onOpenNight = { route = Route.SleepNight(it) },
                            onOpenDetail = { route = Route.SleepDetail(it) },
                            onRefresh = { syncTick++ },
                            onAuth = doAuthSamsung,
                        )
                                Feature.ECG -> EcgTab(
                            records = ecgList,
                            hrv = hrvList,
                            temps = tempList,
                            onOpen = { route = Route.EcgDetail(it) },
                        )
                                Feature.TEMP -> TempTab(
                            records = tempList,
                            night = skinTempNight,
                            samsungGranted = samsungGranted,
                            samsungSkinTempOk = samsungSkinTempOk,
                            loading = sleepLoading,
                            onOpenRecords = { route = Route.Records(RecordKind.TEMP) },
                            selectedDay = if (tempDay == 0L) todayEpochDay else tempDay,
                        )
                                Feature.ENERGY -> EnergyTab(
                                    nights = sleepNightList,
                                    samsungRecords = energyList,
                                    activity = activityDayList,
                                )
                                // ⚠ 传入库里那份「每天一行」的活动量：日期条就是按它列的
                                //   （"已有记录数据的日期"），也是历史日步数的来源。
                                //   今天不在里面 —— 今天走实时读取，见 ActivityDayCard。
                                // ⚠ `records` = 加速度档位那份（`activity_days` 是按天聚合的另一张表）——
                                //   2026-10-13「活动状态」卡从**心率页**搬到这里（用户问
                                //   「为什么睡眠里会有活动状态？」：那张卡当时在心率页，而它是"活动"的内容）。
                                Feature.ACTIVITY -> ActivityTab(
                                    activity = activityDayList,
                                    records = activityList,
                                    onOpenRecords = { route = Route.Records(RecordKind.ACTIVITY) },
                                )
                                Feature.DIET -> DietTab()
                                Feature.GLUCOSE -> GlucoseTab()
                                Feature.IHRN -> IhrnTab()
                                Feature.BIA -> BiaTab(
                            records = biaList,
                            mfRecords = mfList,
                            samsungGranted = samsungGranted,
                            samsungBiaOk = samsungBiaOk,
                            samsungProfileOk = samsungProfileOk,
                            loading = sleepLoading,
                            onOpenRecords = { route = Route.Records(RecordKind.BIA) },
                        )
                                Feature.CYCLE -> CycleTab(
                            records = cycleList,
                            hcAvailable = hcAvailable,
                            hcCycleGranted = hcCycleGranted,
                            onRequestHcImport = {
                                scope.launch {
                                    // 先申请权限（点了授权就回到这里继续导入），再读 HC 导入
                                    if (!HealthConnectManager.hasMenstruationReadPermission(applicationContext)) {
                                        hcCycleLauncher.launch(
                                            setOf(
                                                androidx.health.connect.client.permission.HealthPermission
                                                    .getReadPermission(
                                                        androidx.health.connect.client.records
                                                            .MenstruationPeriodRecord::class
                                                    )
                                            )
                                        )
                                        SyncRepository.setEvent(ResText.of(R.string.s_12c4e07d))
                                    } else {
                                        hcCycleGranted = true
                                        val entries = HealthConnectManager.readMenstrualRecords(
                                            applicationContext,
                                            startEpochDay = java.time.LocalDate.now().toEpochDay() - 365 * 3,
                                        )
                                        if (entries.isEmpty()) {
                                            SyncRepository.setEvent(ResText.of(R.string.s_0e2b315f))
                                        } else {
                                            val inserted = SyncRepository.database.menstrualDao()
                                                .insertAll(entries.map { it.toEntity() })
                                                .count { it != -1L }
                                            SyncRepository.setEvent(ResText.of(R.string.s_b6da6315, inserted))
                                        }
                                    }
                                }
                            },
                                )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 开屏公告弹窗。「稍后再说」= 关闭，但**不**标已读（用户没看内容，不该算读过）。
        annDialog?.let { item ->
            AnnouncementDialog(
                item = item,
                onDismiss = { annDialog = null },
                onOpenLink = { url -> openUrl(url) },
                onOpenList = {
                    annDialog = null
                    route = Route.Announcements
                },
            )
        }

        // 导出前的提示弹窗。导出的内容完全取决于"按下导出那一刻本机有什么"，
        // 所以先把两个数据来源催一遍，否则导出的会是一个缺内容的备份 ——
        // 而备份这种东西，等到需要它的时候才发现是空的就太晚了。
        if (showExportDialog) {
            AlertDialog(
                onDismissRequest = { showExportDialog = false },
                title = { Text(stringResource(R.string.s_68ffaaf0)) },
                text = {
                    Text(
                        stringResource(R.string.s_50960879) +
                            stringResource(R.string.s_fb7c4e77) +
                            stringResource(R.string.s_846e1e17) +
                            stringResource(R.string.s_b8e4224e) +
                            stringResource(R.string.s_e31a6b87) +
                            stringResource(R.string.s_88cb5c59) +
                            "Download/${DataBackup.DIR_NAME}/\n\n" +
                            stringResource(R.string.s_eae5b8f5) +
                            stringResource(R.string.s_79d06d74),
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    AppOutlineButton(onClick = {
                        showExportDialog = false
                        scope.launch {
                            backupBusy = true
                            SyncRepository.setEvent(ResText.of(R.string.s_855a6fd9))
                            val r = DataBackup.export(applicationContext)
                            if (r.ok) backupPath = r.path
                            SyncRepository.setEvent(
                                if (r.ok) r.message + ResText.raw(" → ${r.path}") else r.message
                            )
                            backupBusy = false
                        }
                    }) { Text(this@MainActivity.getString(R.string.s_002f63ee)) }
                },
                dismissButton = {
                    AppOutlineButton(onClick = { showExportDialog = false }) { Text(stringResource(R.string.s_002b540a)) }
                },
            )
        }
    }

    // ───────────────────────── 心率页（图表分析） ─────────────────────────

    /**
     * 每小时聚合（最低/最高/平均 + 是否睡眠小时）。
     *
     * 心率页和血氧页共用同一张 [HourRangeChart]，所以这里刻意用中性命名、不带单位语义 ——
     * 心率是「次/分」、血氧是「%」，两者只是量纲不同，柱子形状的含义完全一致。
     * sleep = 该小时主要落在睡眠时段内（由 [overlapsSleep] 判定）。
     */
    private data class HourStat(
        val hour: Int,
        val min: Int,
        val max: Int,
        val avg: Int,
        val sleep: Boolean,
    )

    /**
     * 把一串 `(时间戳, 数值)` 采样点按小时聚合。血氧页用它把三星健康的原始序列变成柱子。
     *
     * 与 [aggregateByHour]（吃的是数据库里的 [HeartRateEntity]）的区别只是数据源头不同，
     * 聚合口径一致：同一小时内取 min/max/平均。
     */
    private fun bucketByHour(
        points: List<Pair<Long, Int>>,
        day0: Long,
        spans: List<Pair<Long, Long>>,
    ): List<HourStat> =
        points.groupBy { ((it.first - day0) / 3_600_000L).toInt() }
            .filterKeys { it in 0..23 }
            .map { (h, pts) ->
                val vals = pts.map { it.second }
                HourStat(
                    hour = h,
                    min = vals.min(),
                    max = vals.max(),
                    avg = vals.average().roundToInt(),
                    sleep = overlapsSleep(day0 + h * 3_600_000L, spans),
                )
            }
            .sortedBy { it.hour }

    /**
     * **睡眠期**序列按「入睡→起床的每一个整点」分桶（睡眠页专用）。
     *
     * 与 [bucketByHour] 的区别**只有原点**：那个以「今天 0 点」为原点 —— 画"今日全天"时
     * 这是对的；这里改用**入睡时刻**做原点，因为画的是"昨晚那一觉"。
     *
     * 为什么不能沿用 0 点：睡眠跨午夜（23:10 入睡、07:05 起床）时，
     * "昨天 23 点"的桶号是 -1，而 [bucketByHour] 里那句 `filterKeys { it in 0..23 }`
     * 会把它**静默丢掉** —— 图上只是少一根柱子，和"那个小时本来没采样"长得一样。
     * 判据在 `core/SleepHours.kt`，由离线校验台架逐条钉住。
     *
     * @return `(每小时统计, 槽位→钟点标签)`。注意统计里的 `hour` 字段是**槽位序号**
     *         （0,1,2… 随时间递增），**不是钟点** —— 钟点在第二个返回值里，
     *         要经 [HourRangeChart] 的 `labelOf` 传给横轴，否则横轴会印成 `0 1 2`
     *         而实际是 `23 0 1`。
     */
    private fun bucketBySleepHours(
        points: List<Pair<Long, Int>>,
        startMs: Long,
        endMs: Long,
    ): Pair<List<HourStat>, Map<Int, String>> {
        val count = SleepHours.slotCount(startMs, endMs)
        if (points.isEmpty() || count <= 0) return emptyList<HourStat>() to emptyMap()
        val labels = SleepHours.slotHours(startMs, count)
            .mapIndexed { slot, clock -> slot to "$clock" }
            .toMap()
        val stats = points
            .groupBy { SleepHours.slotOf(it.first, startMs, count) }
            .mapNotNull { (slot, pts) ->
                if (slot == null) return@mapNotNull null
                val vals = pts.map { it.second }
                HourStat(
                    hour = slot,
                    min = vals.min(),
                    max = vals.max(),
                    avg = vals.average().roundToInt(),
                    // 传进来的 points 本来就只含睡眠时段，所以每一格必然是睡眠格。
                    // 柱色不靠它区分（调用处 sleepDiff = false），这里只是把语义标对。
                    sleep = true,
                )
            }
            .sortedBy { it.hour }
        return stats to labels
    }

    /**
     * 血氧柱状图的纵轴范围。
     *
     * 刻意**不复用** [chartBounds]：那个是给心率用的，会保证至少 50 的跨度，
     * 放到血氧上会画出 70~120 这种"血氧能到 120%"的轴。
     * 真正的规则在 [Spo2.bounds]（纯函数，有离线 JVM 断言）。
     */
    private fun spo2Bounds(vals: List<Int>): IntRange = Spo2.bounds(vals)

    /**
     * 心率页：把原来的一串文本记录换成图表分析页（对齐三星健康的"最小/最大心率每小时"）。
     *
     * 数据优先级：
     *  1. 三星健康**当日小时统计**（[SamsungHealthReader.readTodayHourly]，与手表端同一个数据源）；
     *  2. 拿不到就回退到本 App 入库的心率记录，按小时聚合。
     * 睡眠时段优先读三星健康的 SLEEP 会话；没有该授权时回退到夜间窗口估算，
     * 并在页面上**明确标注是哪一种** —— 估算值不能看起来像实测值。
     *
     * 区块：今日概览 / 全天每小时最小·最大（睡眠换色）/ 极值时段 /
     *      睡眠平均心率（只画睡眠小时）/ 睡眠 vs 清醒对比 / 数据口径 / 最近记录。
     */
    @Composable
    private fun HeartRateTab(
        records: List<HeartRateEntity>,
        samsungGranted: Boolean?,
        /** 「问不出来」（SDK 连不上）—— 与「未授权」分开说，见 `samsungUnreadable` 的说明。 */
        samsungUnreadable: Boolean,
        samsungSleepOk: Boolean?,
        onOpenRecords: () -> Unit,
        onAuth: suspend () -> Unit,
    ) {
        val context = LocalContext.current
        // 点了某一格柱子 → 弹那小时的详情（2026-10-15「交互也要做好」）
        var pickedHour by remember { mutableStateOf<Int?>(null) }
        var hours by remember { mutableStateOf<List<HourStat>>(emptyList()) }
        var sleepAvg by remember { mutableStateOf<Int?>(null) }
        var spans by remember { mutableStateOf<List<Pair<Long, Long>>>(emptyList()) }
        var usedSleepData by remember { mutableStateOf(false) }
        var fromSamsung by remember { mutableStateOf(false) }
        var todayStart by remember { mutableStateOf(0L) }
        var loading by remember { mutableStateOf(true) }
        // ⚠⚠ 这张图现在可能画的**不是今天**（2026-10-11）。
        //
        // 用户报："昨天的明明同步了，这些页面还是没记录"。根因是三星健康读不到，
        // 而这一页从建成起**只认今天**（`aggregateByHour(records, day0)` 的 `day0` 写死今天）
        // ⇒ 库里存着昨天的数据也一个都显示不出来。
        //
        // ⇒ 今天为空时**回退到「库里最近有数据的那一天」**，并把是哪一天显式标出来
        //   （`StaleDataBanner`）。`staleDay == null` 才代表画的是今天。
        var staleDay by remember { mutableStateOf<Long?>(null) }
        // 这张图锚定的那一天 0 点（今天或回退日）。睡眠时长的统计跟着它走 ——
        // 否则会出现"标题写着 10月10日、下面却说今日睡眠 7 小时"。
        var chartAnchor by remember { mutableStateOf(0L) }

        LaunchedEffect(records.size, samsungGranted, samsungSleepOk, samsungUnreadable) {
            loading = true
            val day0 = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val readSpans = if (samsungSleepOk == true) {
                runCatching { SamsungHealthReader.readSleepSpans(context) }.getOrDefault(emptyList())
            } else emptyList()
            usedSleepData = readSpans.isNotEmpty()

            val native = if (samsungGranted == true) {
                runCatching { SamsungHealthReader.readTodayHourly(context) }.getOrDefault(emptyList())
            } else emptyList()
            fromSamsung = native.isNotEmpty()

            // 今天：三星优先，其次本机库里今天那几条
            var anchor = day0
            var base = if (fromSamsung) native else aggregateByHour(records, day0)
            var stale: Long? = null
            // ⚠⚠ 今天就为空 ⇒ 回退到「库里最近有数据的那一天」，而不是直接说"没有记录"。
            //   `records` 已按 `measuredAt DESC`（DAO `ORDER BY measuredAt DESC`），
            //   所以 `first()` 就是最近那条。
            if (base.isEmpty()) {
                val newest = records.firstOrNull()
                if (newest != null) {
                    val d = java.time.Instant.ofEpochMilli(newest.measuredAt)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate().atStartOfDay(java.time.ZoneId.systemDefault())
                        .toInstant().toEpochMilli()
                    if (d != day0) {
                        val older = aggregateByHour(records, d)
                        if (older.isNotEmpty()) {
                            anchor = d
                            base = older
                            stale = java.time.Instant.ofEpochMilli(d)
                                .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                        }
                    }
                }
            }
            staleDay = stale
            chartAnchor = anchor
            // ⚠ `todayStart` 保持"今天 0 点"不变（下面 `s_74f54329` 那句讲的是**今日**睡眠时长）；
            //   回退日不画睡眠底色，理由见下。
            todayStart = day0
            spans = if (readSpans.isNotEmpty()) readSpans else nightWindow(day0)

            hours = base.sortedBy { it.hour }.map { b ->
                HourStat(
                    hour = b.hour,
                    min = b.min,
                    max = b.max,
                    avg = b.avg,
                    // ⚠⚠ 回退到历史日时**不许**画"睡眠时段"：`spans` 只有**最近一晚**的，
                    //   套到另一天上会凭空画出一段不属于那天的睡眠，还会把"睡眠期均值"
                    //   算成一个错的值。宁可不画 —— 与「不拿估算值冒充测量值」同一条。
                    sleep = stale == null &&
                        overlapsSleep(anchor + b.hour * 3_600_000L, spans),
                )
            }
            sleepAvg = if (samsungGranted == true) {
                runCatching { SamsungHealthReader.readSleepAvg(context) }.getOrNull()
            } else null
            loading = false
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.s_fc0d51b0), fontSize = 13.sp, color = AppTextMuted)
            }
            return
        }
        if (hours.isEmpty()) {
            val hintScope = rememberCoroutineScope()
            var busy by remember { mutableStateOf(false) }
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    // ⚠⚠ 连不上时**不许**说成"到用户页去授权"（2026-10-11）：
                    //   那两句话的动作不一样 —— 授权解决不了连接失败，用户只会反复去点。
                    // ⚠⚠ 三页必须**用同一句**说"连不上"（2026-11-03）：`s_861b900f`
                    //   （连接三星健康失败）打头，再接一句"该怎么办"。
                    //   此前三页各写各的（这里两个分支还是同一句，if 等于没写），
                    //   同一台手机在睡眠页/心率页/血氧页会听到三种说法。
                    if (samsungUnreadable) {
                        stringResource(R.string.s_861b900f) + "\n" +
                            stringResource(R.string.s_ed232184)
                    } else {
                        stringResource(R.string.s_ed232184)
                    },
                    fontSize = 14.sp, color = AppTextMuted,
                )
                if (samsungUnreadable || samsungGranted != true) {
                    Spacer(Modifier.height(16.dp))
                    AppOutlineButton(
                        onClick = {
                            hintScope.launch {
                                busy = true
                                runCatching { onAuth() }
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.s_1620386e)) }
                }
            }
            return
        }

        val allMin = hours.minOf { it.min }
        val allMax = hours.maxOf { it.max }
        val allAvg = hours.map { it.avg }.average().roundToInt()
        val sleepHours = hours.filter { it.sleep }
        val sleepOnlyAvg = sleepHours.takeIf { it.isNotEmpty() }?.map { it.avg }?.average()?.roundToInt()
        val peak = hours.maxByOrNull { it.max }
        val dip = hours.minByOrNull { it.min }
        val bounds = chartBounds(hours.flatMap { listOf(it.min, it.max) })
        val byHour = hours.associateBy { it.hour }
        val sleepSlots = hours.filter { it.sleep }.map { it.hour }
        val sleepMsToday = spans.sumOf { (s, e) ->
            val a = max(todayStart, s)
            val b = min(todayStart + 24 * 3_600_000L, e)
            if (b > a) b - a else 0L
        }
        val latest = records.firstOrNull()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 0) 「今日已同步」横幅（2026-10-11）──
            // ⚠ 只在**回退态**出现：说清"这张图画的不是今天"。
            //   没有它的话，标题写着 10月10日、脚注写着"今日睡眠 7 小时"，两句话打架。
            staleDay?.let { day ->
                item { StaleDataBanner(day) }
            }

            // ── 1) 今日概览 ──
            //
            // 2026-10-15 改版：换成统一卡片 + 主数字组件（`AppCard` / `AppKpi`）。
            // 观感变化就三处，但都是"整页看起来不一样"的关键：
            //   ① 卡题与副标题走同一套字阶（14/600 + 12），不再各页一个字号；
            //   ② 主数字统一 32sp + 负字距（`AppKpi` 内固定），数字不再忽大忽小；
            //   ③ 内边距/间距来自 token，与其它页对齐。
            item {
                AppCard(
                    // ⚠ 回退到历史日时标题**必须换成那一天**（`今天` 会是假的）
                    title = if (staleDay == null) {
                        stringResource(R.string.s_0028aa87)
                    } else {
                        rememberShortDateFmt().format(Date(chartAnchor))
                    },
                ) {
                    AppKpi(
                        value = "$allMin-$allMax",
                        unit = stringResource(R.string.s_1c0dd13c),
                        caption = stringResource(R.string.s_63718a14, allAvg, hours.size),
                    )
                    latest?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.s_a4c096d2, it.bpm.toInt()) +
                                timeFmt.format(Date(it.measuredAt)) +
                                "（${sourceLabel(it.source)}）",
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                    }
                }
            }

            // ── 2) 全天每小时 最小/最大 心率 ──
            item {
                AppCard(
                    title = stringResource(R.string.s_acb4c553),
                    subtitle = stringResource(R.string.s_abf2af0f),
                ) {
                    HourRangeChart(
                            slots = (0..23).toList(),
                            hours = byHour,
                            bounds = bounds,
                            labelEvery = 6,
                            sleepDiff = true,
                            onSlot = { pickedHour = it },
                        )
                    Spacer(Modifier.height(6.dp))
                    AppLegend(
                        listOf(
                            HrAwakeColor to stringResource(R.string.stage_awake),
                            HrSleepColor to stringResource(R.string.s_003d8dc3),
                        ),
                    )
                    val ph = pickedHour
                        if (ph != null) {
                            val st = byHour[ph]
                            if (st != null) {
                                HourDetailDialog(
                                    hour = ph, min = st.min, max = st.max, avg = st.avg,
                                    unit = stringResource(R.string.s_1c0dd13c),
                                    onDismiss = { pickedHour = null },
                                )
                            }
                        }
                        if (peak != null && dip != null) {
                            Spacer(Modifier.height(8.dp))
                            // ⚠ 模板里不许嵌 ASCII 引号（提取器会抓到半截串），先算好再插
                            val peakHour = "%02d:00".format(peak.hour)
                            val dipHour = "%02d:00".format(dip.hour)
                            Text(
                                stringResource(R.string.s_3cca6232, peak.max, peakHour) +
                                    stringResource(R.string.s_97f0bdb9, dip.min, dipHour),
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                        }
                }
            }

            // ── 3) 睡眠心率：平均值 + 只画睡眠小时的柱状图 ──
            item {
                AppCard(title = stringResource(R.string.s_1e75792b)) {
                    AppKpi(
                        value = (sleepOnlyAvg ?: sleepAvg)?.toString() ?: "--",
                        unit = stringResource(R.string.s_1c0dd13c),
                        // ⚠ 用户 2026-10-15：「这个应该是平均睡眠心率吧」——
                        //   64 是**睡眠期的平均值**，所以标题下第一句必须写"平均"，
                        //   否则会被读成"某一时刻的睡眠心率"。
                        caption = stringResource(R.string.s_b5daaf4b) + " · " + buildString {
                            if (sleepMsToday > 0) {
                                append(stringResource(R.string.s_74f54329).format(sleepMsToday / 3_600_000.0))
                            } else {
                                append(stringResource(R.string.s_74802428))
                            }
                            if (!usedSleepData) append(stringResource(R.string.s_d35f957d))
                        },
                        valueColor = HrSleepColor,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_04faa661) + if (usedSleepData) stringResource(R.string.s_85611530) else stringResource(R.string.s_ac90812d),
                        fontSize = 11.sp,
                        color = if (usedSleepData) AppTextMuted else Color(0xFFFFA94D),
                    )
                    Spacer(Modifier.height(8.dp))
                    if (sleepSlots.isEmpty()) {
                            Text(stringResource(R.string.s_b5e353e2), fontSize = 12.sp, color = AppTextMuted)
                        } else {
                            HourRangeChart(
                                slots = sleepSlots,
                                hours = byHour,
                                bounds = bounds,
                                labelEvery = 1,
                                sleepDiff = false,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_5193b028),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                        }
                }
            }

            // ── 4) 睡眠 vs 清醒 平均心率对比 ──
            // ⚠ 抽成 `SleepAwakeCompareCard`：**历史按日页也要有这一张**
            //   （用户 2026-10-13：「历史…怎么没有睡眠/清醒平均心率的卡片，
            //   所有的历史卡片都要跟今天的一样」）。两处共用一份实现。
            item { SleepAwakeCompareCard(hours) }

            // ── 5) 数据口径 ──
            item {
                Text(
                    (if (fromSamsung) stringResource(R.string.s_7bff2412) else stringResource(R.string.s_22914ed7)) +
                        stringResource(R.string.s_90ccc526) +
                        stringResource(R.string.s_206537d3),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }

            // ── 7) 最近记录：收进一行入口，不再把原始记录直接铺满整页 ──
            // 直接列出来的问题是：几十条纯文本把上面的图表挤到很远处，
            // 而且翻页时要一路翻过这些数字。明细进独立页面按需查看。
            item {
                EntryRow(stringResource(R.string.s_f329f8be), stringResource(R.string.s_4d34dbfe, records.size), onOpenRecords)
            }
        }
    }

    /**
     * 活动状态卡片（模块 #7 的展示面）。
     *
     * 今日各档累计时长 + 最近一段的细节。三条必须守住的表述纪律：
     *  1. **给依据**：档位旁边一定带上 σ（模长标准差，单位 g），
     *     否则"中度活动"这个结论没有可核对的来源（同 `ActivityCheck` 里的不变式）。
     *  2. **说清边界**：Wear OS 不允许后台常驻采样，采集发生在"手表 App 打开期间"，
     *     不能写成"24 小时连续监测"。
     *  3. **区分"没测到"与"静息"**：数据为空时说"没有记录"，不显示"静息"。
     */
    @Composable
    private fun ActivityCard(records: List<ActivityEntity>, onOpenRecords: () -> Unit) {
        val day0 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        // records 来自 `recent(200)`（按时间倒序），这里只取今天的
        val today = records.filter { it.startedAt >= day0 }
        val totals = Activity.totalsByLevel(today.map { it.level to it.durationMs })
        val latest = today.firstOrNull()

        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.s_b8d2d4c4), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (today.isNotEmpty()) {
                        Text(
                            stringResource(R.string.s_dc125432, Activity.formatDuration(totals.values.sum()), today.size),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (today.isEmpty()) {
                    Text(
                        stringResource(R.string.s_8fe82d81) +
                            stringResource(R.string.s_79c17a82) +
                            stringResource(R.string.s_3fdc5351),
                        fontSize = 12.sp, color = AppTextMuted,
                    )
                } else {
                    // 四档各一行：档位 + 累计时长 + 占比条
                    val total = totals.values.sum().coerceAtLeast(1L)
                    Activity.Level.ALL.forEach { lvl ->
                        val ms = totals[lvl] ?: 0L
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                Activity.label(lvl), fontSize = 12.sp,
                                modifier = Modifier.width(64.dp),
                                color = activityColor(lvl),
                            )
                            Text(
                                Activity.formatDuration(ms),
                                fontSize = 12.sp,
                                modifier = Modifier.width(64.dp),
                            )
                            // 占比条：宽度按比例，最短留 1dp 以便"有数据但很短"也能看见
                            Box(
                                Modifier.weight(1f).height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color(0xFFE8E8E8))
                            ) {
                                if (ms > 0L) {
                                    Box(
                                        Modifier
                                            .fillMaxWidth((ms.toFloat() / total).coerceIn(0.02f, 1f))
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(activityColor(lvl))
                                    )
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                Activity.formatPct(ms.toFloat() / total),
                                fontSize = 11.sp, color = AppTextMuted,
                                modifier = Modifier.width(36.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    latest?.let {
                        Text(
                            stringResource(R.string.s_6f43c732, timeFmt.format(Date(it.startedAt))) +
                                "${Activity.label(it.level)} · ${Activity.formatDuration(it.durationMs)} · " +
                                stringResource(R.string.s_972cc1c4, Activity.formatStd(it.stdG), Activity.formatPct(it.stillRatio)),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    CollapsibleNotes(
                            listOf(
                            stringResource(R.string.s_9ecdb934) +
                            stringResource(R.string.s_c40be840) +
                            stringResource(R.string.s_7bb6c982) +
                            stringResource(R.string.s_57796fa4) +
                            stringResource(R.string.s_ed0b1358),
                            )
                        )
                }
                Spacer(Modifier.height(8.dp))
                // 明细入口用一行文字而不是 EntryRow：EntryRow 本身是一张 Card，
                // 嵌在 Card 里会变成卡中卡。这里只借它"明细不铺开、点进去看"的语义。
                Text(
                    stringResource(R.string.s_8e1a06fa),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenRecords() }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    /**
     * 原始光信号卡片（模块 #8）。
     *
     * ## 落点（2026-10-10 改）
     * 原来挂在**血氧页**（靠"它就是搭血氧测量采的"这个理由），但用户看着它和血氧并排
     * 就问"这是干嘛的、没用就删掉"—— 说明**按"谁采的"分组不成立，得按"这是什么"分组**。
     * 现在挂在「光信号记录」页（`RecordsScreen`，`kind == RecordKind.PPG`）：
     * 那一页讲的就是光信号本身，`onOpenRecords` 传 `null`（链接指向的就是本页）。
     *
     * ⚠ 数据与采集**一样没删**：`ppg_waves` 表是呼吸率（`Resp.fromPpg`）与 PAT 对齐的
     *   输入，心电导出与备份也都带它。撤的只是它在血氧页上的那张卡。
     *
     * ## 为什么"无独立入口"的一项也要有界面
     * 总表 #8 的定位是"无独立入口，后台隐式采集"—— 用户不需要、也不会主动操作它。
     * 但正因为不可见，**它失败时也必须看得见**：手表上它的失败原因（设备不支持 /
     * SDK 策略拒绝 / 与服务连接冲突）只写在手表血氧页，手机端至少要能回答
     * "到底采到了没有、采到的是什么"。
     *
     * ## 为什么这里不画波形
     * 一条记录带三段归一化波形（各最多 3000 个六位小数），几百条铺开几十万个字符。
     * 波形是给**算法**用的输入，不是给人看的曲线 —— 人看的是灌注指数/完整度/脉率这三个
     * 已经算好的摘要。逐条摘要走记录列表，波形留在库里等下游消费。
     */
    @Composable
    private fun PpgCard(records: List<PpgWaveEntity>, onOpenRecords: (() -> Unit)?) {
        val latest = records.firstOrNull()
        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.s_f6c116e3), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    if (records.isNotEmpty()) {
                        Text(stringResource(R.string.s_4d34e052, records.size), fontSize = 11.sp, color = AppTextMuted)
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (latest == null) {
                    Text(
                        stringResource(R.string.s_b1cb7010) +
                            stringResource(R.string.s_095ea5d8) +
                            stringResource(R.string.s_61920e8c),
                        fontSize = 12.sp, color = AppTextMuted,
                    )
                } else {
                    Text(
                        stringResource(R.string.s_184d5ae5, timeFmt.format(Date(latest.measuredAt))),
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    val chans = Ppg.decodeChannels(latest.channels)
                        .joinToString(" / ") { Ppg.Channel.label(it) }
                    Text(
                        stringResource(R.string.s_03725253, Ppg.formatDuration(latest.durationMs), latest.sampleCount) +
                            Ppg.formatRate(latest.sampleRateHz),
                        fontSize = 12.sp, color = AppTextMuted,
                    )
                    Text(
                        stringResource(R.string.s_37ecf385, chans, Ppg.formatQuality(latest.quality)),
                        fontSize = 12.sp, color = AppTextMuted,
                    )
                    Text(
                        stringResource(R.string.s_e7cb8133) +
                            Ppg.formatPi(Ppg.perfusionIndexFromRaw(latest.dcGreen, latest.acGreenP2P)) +
                            stringResource(R.string.s_fad4514e, Ppg.formatHr(latest.estimatedHr)) +
                            (Ppg.hrDeltaLabel(latest.estimatedHr, latest.refHr)?.let { "（$it）" } ?: ""),
                        fontSize = 12.sp, color = AppTextMuted,
                    )
                    // 呼吸率（#11）：本页是 PPG 的"消费端"，波形交给自研算法算出来的第一个结果
                    RespBlock(remember(latest.id, latest.greenWave) { respFromPpg(latest) }, stringResource(R.string.s_15722aab))
                    Spacer(Modifier.height(8.dp))
                    CollapsibleNotes(
                            listOf(
                            stringResource(R.string.s_bef9d45d) +
                            stringResource(R.string.s_e04ef8b8) +
                            stringResource(R.string.s_95534e43) +
                            stringResource(R.string.s_070604db) +
                            stringResource(R.string.s_644c063b),
                            )
                        )
                }
                Spacer(Modifier.height(8.dp))
                // 2026-10-10：这张卡现在有两个落点（血氧页 → 已撤、记录页 → 保留），
                // **在记录页上"查看光信号记录"这个链接指向的就是本页** ⇒ 必须是可空，
                // 传 null 时整行不渲染（否则会多出一个"点了没反应"的链接）。
                if (onOpenRecords != null) {
                    Text(
                        stringResource(R.string.s_6c6eccc0),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenRecords() }
                            .padding(vertical = 8.dp),
                    )
                }
            }
        }
    }

    /**
     * 从已落库的 PPG 记录估呼吸率（模块 #11）。
     *
     * 界面**不另写一套**：直接调 `Resp.fromPpg`，与台架、与手表端是同一份实现。
     * 需要 `remember` 挡住重组 —— 一次要跑自相关与两轮峰检测，放在重组里会明显掉帧。
     */
    private fun respFromPpg(e: PpgWaveEntity): Resp.Result =
        Resp.fromPpg(
            wave = Ppg.decodeWave(e.greenWave),
            sampleRateHz = e.sampleRateHz,
            quality = e.quality ?: 0f,
            durationMs = e.durationMs,
        )

    /**
     * 呼吸率区块（模块 #11），光信号与心电两条路径共用。
     *
     * 两种状态都要如实表达：
     *  · 算出来了 → 数值 + 参考区间 + 置信度 + 依据了哪几路 + 覆盖了几次呼吸；
     *  · 没算出来 → **原样显示原因**（记录太短/检不出脉搏/几路矛盾）。
     * 第二种尤其重要：这个模块最常见的输出就是"给不出数"，
     * 如果只显示一个「--」，用户会以为功能坏了，而不是"这次记录不够长"。
     *
     * 口径说明与免责常驻（与 ODI 卡同一条纪律：不给结论时也要说清这个数是怎么来的）。
     */
    /**
     * 可折叠的说明段落（**默认收起**）。
     *
     * ## 为什么要有它
     * 这些「口径 / 方法 / 免责」段落每张卡都跟着一大坨。常驻展开时**卡片一半高度都是灰字**，
     * 想看数字得先划过很长一段字 —— 2026-09-21 用户提的"影响美观"就是这个。
     *
     * ## 三处刻意的设计
     * - **收藏时留第一段的前两行**（不是一行、也不是全隐）：说明段的第一句通常就是最要紧的
     *   那句（例如"这一次给不出呼吸率：…"），完全不显示会让人以为这里根本没有说明。
     * - **用「展开全部 ▾」文字 + 箭头，不用裸图标**：文字比三角形自明；箭头放在文字右边
     *   （收起 `▾` / 展开 `▴`），朝下表示"还有内容在下面"。
     * - **`rememberSaveable` 而不是 `remember`**：转屏或进程重建后保持展开状态，
     *   用户刚点开又被打断、回来发现全收起来了会很烦。
     *
     * @param lines 每个元素是**独立一段**（展开时段间会自动空一行）。空串会被丢掉 ——
     *   调用方常把 core 里的常量直接塞进来，而那些常量可能因为版本差异为空串。
     */
    @Composable
    private fun CollapsibleNotes(lines: List<String>, fontSize: TextUnit = 11.sp) {
        val paras = remember(lines) { lines.filter { it.isNotBlank() } }
        if (paras.isEmpty()) return
        var expanded by rememberSaveable { mutableStateOf(false) }
        val accent = MaterialTheme.colorScheme.primary
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = if (expanded) paras.joinToString("\n\n") else paras.first(),
                fontSize = fontSize,
                color = AppTextMuted,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .clickable { expanded = !expanded }
                    .padding(top = 4.dp, bottom = 2.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (expanded) stringResource(R.string.s_00345819) else stringResource(R.string.s_78520897), fontSize = 11.sp, color = accent)
                Spacer(Modifier.width(3.dp))
                Text(if (expanded) "▴" else "▾", fontSize = 11.sp, color = accent)
            }
        }
    }

    @Composable
    private fun RespBlock(r: Resp.Result, from: String) {
        val lang = rememberLang()
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.s_c58fa592, from), fontWeight = FontWeight.Bold, fontSize = 13.sp)
        Spacer(Modifier.height(4.dp))
        when (r) {
            is Resp.Result.Ok -> {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(Resp.formatBrpm(r.brpm), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        Resp.bandLabel(r.band),
                        fontSize = 12.sp,
                        color = Color(Resp.bandColorArgb(r.band)),
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                Text(Resp.confidenceLine(r), fontSize = 11.sp, color = AppTextMuted)
            }
            is Resp.Result.NotApplicable -> {
                Text(
                    stringResource(R.string.s_8661f243, r.reason),
                    fontSize = 12.sp, color = AppTextMuted,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        // 3 段说明合并成一个可折叠块（默认收起，2026-09-21）：
        // 常驻展开时这段灰字比上面的数据还长，整张卡一半高度都在讲口径。
        CollapsibleNotes(
            listOf(
                Resp.METHOD_NOTE,
                Resp.WHY_NOT_SLEEP_NOTE,
                Disclaimer.resp(lang),
            ),
            fontSize = 11.sp,
        )
    }

    /** 四档配色：静息=青灰（"没事"）、越活跃越暖，符合直觉。 */
    private fun activityColor(level: String): Color = when (level) {
        Activity.Level.REST -> Color(0xFF5C9EAD)
        Activity.Level.LIGHT -> Color(0xFF4CAF50)
        Activity.Level.MODERATE -> Color(0xFFEF8A17)
        Activity.Level.VIGOROUS -> Color(0xFFFF6B6B)
        else -> AppTextMuted
    }

    /** 一行式入口：左标题、右副标题 + 箭头。可点击的整体行，比小按钮好点。 */
    @Composable
    private fun EntryRow(title: String, subtitle: String?, onClick: () -> Unit) {
        // ⚗ 2026-11-04 视觉对齐：原来这里是**裸 `Card`**（M3 默认 12dp 圆角、默认底色、无描边），
        //   和 `AppCard`（10dp 圆角 + 白底 + 1px 描边）并排时**明显不是一套东西**。
        //   现在改用同一组 token，整页卡片语言统一。
        // ⚗ 副标题也改成**标题下方一行**，不再左右分栏：
        //   左右分栏时两边各占一半、长一点就折行（中文 12 字就折、英文更糟），
        //   而且"标题在左、说明在右"读起来像两个字段。上下排是移动端更自然的信息层级。
        AppSurface(Modifier.fillMaxWidth(), onClick = onClick) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            it,
                            fontSize = 12.sp,
                            color = AppTextMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text("›", fontSize = 18.sp, color = AppTextMuted)
            }
        }
    }

    /** 版本号文案。取不到就返回空串（宁可少显示一行，也不显示一个猜的版本号）。 */
    private fun appVersionLabel(): String = runCatching {
        val pi = packageManager.getPackageInfo(packageName, 0)
        "v${pi.versionName ?: "?"}（${pi.longVersionCode}）"
    }.getOrDefault("")

    /**
     * 用系统浏览器打开公告里的链接。**打不开就静默忽略** ——
     * 公告里的链接是外部内容，不该因为一个坏链接给用户弹错误。
     * 只放行 http/https（公告 JSON 是外部内容，别的 scheme 一律不碰）。
     */
    /**
     * 用系统浏览器打开公告里的链接。**打不开就静默忽略** ——
     * 公告里的链接是外部内容，不该因为一个坏链接给用户弹错误。
     * 只放行 http/https（公告 JSON 是外部内容，别的 scheme 一律不碰）。
     *
     * ⚠ [String.startsWith] 必须带 `ignoreCase`：作者写 `HTTPS://…` 时大小写敏感的
     * 判断会直接 return，链接**点了完全没反应**且不报错。
     */
    private fun openUrl(url: String) {
        if (!url.startsWith("http://", ignoreCase = true) &&
            !url.startsWith("https://", ignoreCase = true)
        ) return
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    /**
     * 公告正文：**可长按复制 + 正文里的链接可直接点**（2026-09-29 加）。
     *
     * ## 两件事分别怎么做的
     *  ① 复制：[SelectionContainer] 把 [Text] 包起来 —— Compose 才会响应长按选择，
     *     弹出系统的复制菜单。**不包就完全没反应**（`Text` 默认不可选中），
     *     这正是用户报的「不能长按复制」。
     *  ② 链接：用 [Announcement.segments] 把正文切成片段，链接段用
     *     [LinkAnnotation.Clickable] 标出来（带下划线 + 主题色），点它走 [openUrl]。
     *
     * ## 为什么用 `LinkAnnotation` 而不是自己算点击坐标
     * 自己按字符偏移算「点到了哪一段」需要拿到 `TextLayoutResult` 并处理多行折行，
     * 容易在换行处点错。`LinkAnnotation` 是这一版 Compose 的原生能力，
     * 由框架负责命中检测，我们只需要给出哪段是链接。
     *
     * ⚠ [SelectionContainer] 的范围**只包正文**，不包整个卡片/对话框：
     * 包住按钮之类的东西会让长按菜单出现在不该出现的地方。
     */
    @Composable
    private fun AnnouncementBody(text: String, fontSize: TextUnit) {
        val linkColor = MaterialTheme.colorScheme.primary
        val styled = remember(text, linkColor) {
            buildAnnotatedString {
                Announcement.segments(text).forEach { seg ->
                    val target = seg.link
                    if (target == null) {
                        append(seg.text)
                    } else {
                        // pushStringAnnotation 那套（旧 API）要自己处理点击，这里用新的 withLink
                        withLink(
                            LinkAnnotation.Clickable(
                                tag = target,
                                styles = TextLinkStyles(
                                    style = SpanStyle(
                                        color = linkColor,
                                        textDecoration = TextDecoration.Underline,
                                    )
                                ),
                                linkInteractionListener = { openUrl(target) },
                            )
                        ) { append(seg.text) }
                    }
                }
            }
        }
        SelectionContainer {
            Text(styled, fontSize = fontSize)
        }
    }

    /**
     * 页面头部：返回按钮 + 标题。详情页/记录页共用。
     *
     * [trailing] 是可选的右槽（2026-10-12 加）：**睡眠页的日期条挂在这里**。
     * ⚠⚠ 为什么不把日期条做成单独一条横带：用户 2026-10-12 连着报两次"内容被遮住"，
     *   两次都是那条横带造成的观感 —— 第一次是 `stickyHeader` **盖住**了滚过去的卡片，
     *   第二次是横带把内容区**顶下去**、顶部多出一条白带，看着仍像有东西压着内容。
     *   挂进标题行之后：**不占额外高度、没有横带**，内容区的顶边就是标题行的下沿
     *   （与其它功能页完全一致，切边落在大家习惯的位置）。
     */
    @Composable
    private fun DetailHeader(
        title: String,
        onBack: () -> Unit,
        trailing: (@Composable () -> Unit)? = null,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppOutlineButton(onClick = onBack) { Text(stringResource(R.string.s_51a8f8fd)) }
            Spacer(Modifier.width(12.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) trailing()
        }
    }

    /**
     * 隐私声明 / 免责声明整屏页（2026-10-16）。
     *
     * ⚠ 正文从 `assets/legal/` 读取，**按当前界面语言取对应文件**，
     *   取不到就退回中文 —— 不能因为缺一个语言文件就显示空白页（那是合规问题）。
     * ⚠ 整页是一个可滚动的 `Column`：文档长度远超一屏，**不能只靠"字变小"塞进去**。
     */
    /**
     * 应用内更新弹窗（2026-10-16）。
     *
     * ⚠⚠ `AlertDialog` 的 `text` 槽内部是 **Box（堆叠）**：平级传多个 Composable 会**重叠**
     *    （这个项目踩过，静态校验脚本钉着）⇒ 只传**一个** `Column`。
     */
    @Composable
    private fun UpdateDialog(
        state: UpdateUi,
        onDismiss: () -> Unit,
        onDownload: (AnnouncementUpdate) -> Unit,
    ) {
        val info = when (state) {
            is UpdateUi.Available -> state.info
            is UpdateUi.Downloading -> state.info
            else -> null
        } ?: return
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.s_77c19f54)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.s_955224e8,
                            AnnouncementRepository.versionInfo(applicationContext).first, info.versionName,
                        ),
                        fontSize = 13.sp, color = AppTextMuted,
                    )
                    // ⚠ 提局部变量：`info.notes` 是**另一个模块**（core）的 public val，
                    //   在别处没法做智能转换（编译期报 "Smart cast is impossible"）。
                    val notes = info.notes
                    if (!notes.isNullOrBlank()) {
                        Text(notes, fontSize = 13.sp, color = AppTextPrimary)
                    }
                    when (state) {
                        is UpdateUi.Downloading -> {
                            if (state.progress > 0f) {
                                LinearProgressIndicator(
                                    progress = { state.progress },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Text(
                                    "${(state.progress * 100).toInt()}%",
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                            } else {
                                // 服务端不给长度时只能显示不确定进度（不能假装有百分比）
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            }
                        }
                        is UpdateUi.Ready -> Text(
                            stringResource(R.string.s_883c6e83),
                            fontSize = 13.sp, color = AppTextSecondary,
                        )
                        else -> Unit
                    }
                }
            },
            dismissButton = {
                AppOutlineButton(onClick = onDismiss) {
                    Text(stringResource(R.string.s_a8818b40))
                }
            },
            confirmButton = {
                if (state is UpdateUi.Available) {
                    AppPrimaryButton(onClick = { onDownload(info) }) {
                        Text(stringResource(R.string.s_41a89130))
                    }
                }
            },
        )
    }

    @Composable
    private fun LegalScreen(which: Int, onBack: () -> Unit) {
        val ctx = this@MainActivity
        val lang = rememberLang()
        // 语言 tag 与 assets 文件名一致：zh / zhHant / en / ko
        val tag = when (lang) {
            Language.ZH_HANT -> "zhHant"
            Language.EN -> "en"
            Language.KO -> "ko"
            else -> "zh"
        }
        val name = if (which == 0) "privacy" else "disclaimer"
        val body = remember(name, tag) {
            runCatching {
                ctx.assets.open("legal/${name}_${tag}.txt").bufferedReader().use { it.readText() }
            }.recoverCatching {
                // 兜底：任何语言文件缺失都退回中文（宁可是外文用户看到中文，也不能空白）
                ctx.assets.open("legal/${name}_zh.txt").bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
        Column(Modifier.fillMaxSize()) {
            DetailHeader(
                title = stringResource(if (which == 0) R.string.s_4cd42cd7 else R.string.s_0a026ab8),
                onBack = onBack,
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    body,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = AppTextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                AppHint(stringResource(R.string.s_be61344e))
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    /** 夜间窗口回退（没拿到三星健康睡眠会话时用）：昨天 23:00 ~ 今天 07:00。 */
    private fun nightWindow(day0: Long): List<Pair<Long, Long>> =
        listOf((day0 - 3_600_000L) to (day0 + 7 * 3_600_000L))

    /** 该小时与任一睡眠区间重叠 ≥30 分钟才算睡眠小时（按多数归属，避免边界小时忽左忽右）。 */
    private fun overlapsSleep(hourStart: Long, spans: List<Pair<Long, Long>>): Boolean {
        val hourEnd = hourStart + 3_600_000L
        var overlap = 0L
        for ((s, e) in spans) {
            val a = max(hourStart, s)
            val b = min(hourEnd, e)
            if (b > a) overlap += b - a
        }
        return overlap >= 1_800_000L
    }

    /** 本地心率记录按小时聚合（只取今天 0 点之后的）。 */
    private fun aggregateByHour(records: List<HeartRateEntity>, day0: Long): List<HourBucket> {
        val cal = Calendar.getInstance()
        val byHour = LinkedHashMap<Int, MutableList<Int>>()
        for (r in records) {
            if (r.measuredAt < day0) continue
            cal.timeInMillis = r.measuredAt
            byHour.getOrPut(cal.get(Calendar.HOUR_OF_DAY)) { mutableListOf() }.add(r.bpm.toInt())
        }
        return byHour.entries.sortedBy { it.key }.map { (h, v) ->
            HourBucket(h, v.min(), v.max(), v.average().toInt())
        }
    }

    /**
     * 纵轴范围：把数据包住并留 10 的余量、量化到 10 的倍数，且保证至少 50 的跨度 ——
     * 否则心率平稳的一天（比如 68~76）会被拉成"满屏柱状起伏"，看着像心律剧烈波动。
     */
    /**
     * 纵轴刻度步长（2026-10-15）。
     *
     * 从 {1,2,5,10,20,25,50,100} 里取第一个让**段数 ≤ 5** 的值 —— 这几个数能整除常见的
     * 范围跨度，所以刻度既是整数、又等距。
     *
     * ⚠⚠ 原来写的是"范围四等分再四舍五入显示"（`lo + span*k/4`）：跨度 10 时会得到
     *   90 / 92.5 / 95 / 97.5 / 100，取整打出来是 90/92/95/97/100 ——
     *   **标签间距不等而网格线等距**，用户对着图读数必然读错
     *   （血氧页 2026-10-15 的反馈就是这个）。
     */
    private fun niceAxisStep(span: Int): Int {
        val want = (span / 5).coerceAtLeast(1)
        for (c in intArrayOf(1, 2, 5, 10, 20, 25, 50, 100)) if (c >= want) return c
        return 100
    }

    private fun chartBounds(vals: List<Int>): IntRange {
        if (vals.isEmpty()) return 50..130
        val lo = (((vals.min() - 10) / 10) * 10).coerceAtLeast(20)
        var hi = ((vals.max() + 10 + 9) / 10) * 10
        if (hi - lo < 50) hi = lo + 50
        return lo..hi
    }

    /**
     * 每小时心率范围柱状图：每小时一根从 min 到 max 的圆角柱。
     *
     * 纵轴刻度与横轴小时都用 Canvas 内 `drawText` 画，保证数值与网格线严格对齐
     * （用外层 Row/Column 摆标签会有十几 dp 的累计偏差，数字和线对不上就很难读）。
     *
     * @param slots      横轴槽位（决定列位置）；只有 [hours] 里存在的槽位才画柱子
     * @param bounds     纵轴范围（含端点）
     * @param labelEvery 横轴每 N 个槽位标一个刻度；1 = 每格都标
     * @param sleepDiff  是否把睡眠小时换成另一种颜色
     * @param awakeColor 清醒小时柱色（默认心率粉红）
     * @param sleepColor 睡眠小时柱色；仅在 [sleepDiff] 为 true 时使用
     * @param labelOf    槽位 → 横轴标签。默认直接印槽位值（血氧页/心率页的槽位就是钟点，
     *                   所以默认够用）。**睡眠页必须传** —— 它的槽位是「第几小时」
     *                   （0,1,2…，见 [bucketBySleepHours]），钟点在另一个映射里，
     *                   不传就会把横轴印成 `0 1 2 3` 而实际是 `23 0 1 2`。
     */
    @Composable
    private fun HourRangeChart(
        slots: List<Int>,
        hours: Map<Int, HourStat>,
        bounds: IntRange,
        labelEvery: Int,
        sleepDiff: Boolean,
        awakeColor: Color = HrAwakeColor,
        sleepColor: Color = HrSleepColor,
        labelOf: (Int) -> String = { "$it" },
        /** 点了某一格（槽位）回调。用于弹该小时的详情。null = 不可点。 */
        onSlot: ((Int) -> Unit)? = null,
    ) {
        if (slots.isEmpty()) return
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 9.sp, color = Color(0xFF9C8CB0))
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
                .then(
                    if (onSlot != null) {
                        Modifier.pointerInput(slots, hours) {
                            detectTapGestures { off ->
                                // 只认绘图区里的点击，且必须落在**有数据**的那一格上
                                val axisW = with(density) { 30.dp.toPx() }
                                val xAxisH = with(density) { 16.dp.toPx() }
                                val plotW = (size.width - axisW).coerceAtLeast(1f)
                                val slotW = plotW / slots.size
                                val idx = ((off.x - axisW) / slotW).toInt()
                                if (off.y <= size.height - xAxisH && idx in slots.indices) {
                                    val h = slots[idx]
                                    if (hours[h] != null) onSlot(h)
                                }
                            }
                        }
                    } else {
                        Modifier
                    }
                ),
        ) {
            val axisW = 30.dp.toPx()
            val xAxisH = 16.dp.toPx()
            val plotW = (size.width - axisW).coerceAtLeast(1f)
            val plotH = (size.height - xAxisH).coerceAtLeast(1f)
            val lo = bounds.first
            val span = (bounds.last - lo).coerceAtLeast(1)

            val tickStep = niceAxisStep(span)
            var tick = ((lo + tickStep - 1) / tickStep) * tickStep
            while (tick <= bounds.last) {
                val y = plotH * (1f - (tick - lo).toFloat() / span)
                drawLine(Color(0x1AFFFFFF), Offset(axisW, y), Offset(size.width, y), strokeWidth = 1f)
                val tl = measurer.measure("$tick", labelStyle)
                drawText(
                    tl,
                    topLeft = Offset(
                        (axisW - 5.dp.toPx() - tl.size.width).coerceAtLeast(0f),
                        (y - tl.size.height / 2f).coerceIn(0f, (plotH - tl.size.height).coerceAtLeast(0f)),
                    ),
                )
                tick += tickStep
            }

            val slotW = plotW / slots.size
            val bandW = (slotW * 0.55f).coerceIn(3f, 16.dp.toPx())

            // ⚠⚠⚠ 2026-10-16 **第三次**改这张图，前两版都被用户否掉，记下来：
            //   ① v134~139：悬空的 [min,max] 柱 → 血氧"最低 93、最高 100"时所有柱子
            //      吊在画布顶端，用户说「这种图都是倒置的」；
            //   ② v140：给柱子加"从轴底垫到最低值"的淡色柱身 + 柱顶数值 →
            //      24 格宽度下**数值挤成一串重叠的数字**、淡色柱身把画面糊成一片，
            //      用户说「根本就看不懂」；
            //   ③ 现在（本版）：**浅色范围带（最低~最高）+ 平均值折线**，
            //      与三星健康血氧图同一套画法 —— 带宽表示波动、折线表示水平。
            //
            //   ⚠ 三条设计约束（都是被否出来的）：
            //   1. **画法必须"立"起来**：带状范围 + 折线天然是从左到右的量值曲线，
            //      不存在"倒挂"观感；
            //   2. ⛔ **不要在每根柱/每格上标数值**：24 格时相邻标签必然重叠成乱码
            //      （极值由下面「极值时段」那张文字卡负责说，那才是给人读的地方）；
            //   3. 折线用**中性深色**、不跟着清醒/睡眠换色 —— 否则"值"和"时段分类"
            //      两种含义会挤在同一个视觉通道上。

            // ① 每小时的范围带（最低~最高）
            slots.forEachIndexed { i, h ->
                val e = hours[h] ?: return@forEachIndexed
                val yHi = plotH * (1f - (e.max - lo).toFloat() / span)
                val yLo = plotH * (1f - (e.min - lo).toFloat() / span)
                val bandColor = if (sleepDiff && e.sleep) sleepColor else awakeColor
                // 直角矩形：圆角会让它看起来像"柱子"，而它是范围背景
                drawRect(
                    color = bandColor.copy(alpha = 0.25f),
                    topLeft = Offset(axisW + i * slotW + (slotW - bandW) / 2f, yHi),
                    // ⚠ 下限 4dp：某个时段**只采到一个点**时 min==max，带宽会是 0 ——
                    //   原来给 1.5px，在手机上等于看不见，用户以为"这段时间没有最低/最高"。
                    //   给一个看得见的短刻度，表示"这里有数据，只是没有范围"。
                    size = Size(bandW, (yLo - yHi).coerceAtLeast(4.dp.toPx())),
                )
            }

            // ② 每小时平均值连成的折线 + 圆点（图的主体：一眼看出"大概多少、有没有掉下去"）
            val lineColor = Color(0xFF37474F)
            val path = Path()
            var dots = 0
            slots.forEachIndexed { i, h ->
                val e = hours[h] ?: return@forEachIndexed
                val x = axisW + i * slotW + slotW / 2f
                val y = plotH * (1f - (e.avg - lo).toFloat() / span)
                if (dots == 0) path.moveTo(x, y) else path.lineTo(x, y)
                dots++
            }
            if (dots > 0) {
                drawPath(
                    path, lineColor,
                    style = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round,
                    ),
                )
                slots.forEachIndexed { i, h ->
                    val e = hours[h] ?: return@forEachIndexed
                    val x = axisW + i * slotW + slotW / 2f
                    val y = plotH * (1f - (e.avg - lo).toFloat() / span)
                    drawCircle(lineColor, radius = 2.5.dp.toPx(), center = Offset(x, y))
                }
            }

            // ③ 横轴小时标签
            slots.forEachIndexed { i, h ->
                if (labelEvery > 0 && i % labelEvery == 0) {
                    val tl = measurer.measure(labelOf(h), labelStyle)
                    val cx = axisW + i * slotW + slotW / 2f
                    drawText(
                        tl,
                        topLeft = Offset(
                            (cx - tl.size.width / 2f).coerceIn(
                                axisW, (size.width - tl.size.width).coerceAtLeast(axisW),
                            ),
                            plotH + 3.dp.toPx(),
                        ),
                    )
                }
            }
            drawLine(
                Color(0x33000000),
                Offset(axisW, plotH), Offset(size.width, plotH),
                strokeWidth = 1f,
            )
        }
    }

    /** 图例：小色块 + 文字。 */
    @Composable
    private fun ChartLegend(items: List<Pair<Color, String>>) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (c, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(c, RoundedCornerShape(5.dp)))
                    Spacer(Modifier.width(5.dp))
                    Text(label, fontSize = 11.sp, color = AppTextMuted)
                }
            }
        }
    }

    /** 平均值对比行：左侧标签、中间按比例长度的横条、右侧数值。 */
    @Composable
    private fun AvgCompareRow(
        label: String,
        value: Int?,
        unit: String,
        color: Color,
        scaleMax: Int,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, modifier = Modifier.width(40.dp))
            Box(Modifier.weight(1f).height(16.dp)) {
                if (value != null) {
                    Box(
                        Modifier
                            .fillMaxWidth((value.toFloat() / scaleMax).coerceIn(0.06f, 1f))
                            .height(16.dp)
                            .background(color, RoundedCornerShape(8.dp)),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                value?.toString() ?: "--",
                fontSize = 14.sp, fontWeight = FontWeight.Bold,
                color = if (value == null) AppTextMuted else color,
            )
            Spacer(Modifier.width(2.dp))
            Text(unit, fontSize = 11.sp, color = AppTextMuted)
        }
    }

    /**
     * 「睡眠 / 清醒 平均心率」对比卡（2026-10-13）。
     *
     * ⚠⚠ **心率页的今日与历史按日页共用这一份**：用户的原话是
     *   「历史…怎么没有睡眠/清醒平均心率的卡片，**所有的历史卡片都要跟今天的一样**」。
     *   两处各写一份必然漂移（这个项目已经踩过多次），所以连"差多少"那句话都在这里。
     *
     * ⚠ 没有睡眠小时（`hours` 里一格睡眠都没有）时，睡眠那一行显示 `--`：
     *   不是 0 —— 0 会被读成"睡眠平均心率是 0"。
     */
    @Composable
    private fun SleepAwakeCompareCard(hours: List<HourStat>) {
        val sleepAvg = hours.filter { it.sleep }.takeIf { it.isNotEmpty() }
            ?.map { it.avg }?.average()?.roundToInt()
        val awakeAvg = hours.filter { !it.sleep }.takeIf { it.isNotEmpty() }
            ?.map { it.avg }?.average()?.roundToInt()
        val unit = stringResource(R.string.s_1c0dd13c)
        AppCard(title = stringResource(R.string.s_aca72a6d)) {
            Column {
                Spacer(Modifier.height(8.dp))
                val scaleMax = maxOf(sleepAvg ?: 0, awakeAvg ?: 0, 1)
                AvgCompareRow(stringResource(R.string.s_003d8dc3), sleepAvg, unit, HrSleepColor, scaleMax)
                Spacer(Modifier.height(6.dp))
                AvgCompareRow(stringResource(R.string.s_0038de21), awakeAvg, unit, HrAwakeColor, scaleMax)
                Spacer(Modifier.height(8.dp))
                if (sleepAvg != null && awakeAvg != null) {
                    val diff = awakeAvg - sleepAvg
                    Text(
                        if (diff >= 0) stringResource(R.string.s_ccc54972, diff)
                        else stringResource(R.string.s_c3296cf8, -diff),
                        fontSize = 12.sp, color = AppTextMuted,
                    )
                }
            }
        }
    }

    // ═══════════════════════ 睡眠 ═══════════════════════

    /**
     * 睡眠标签页（对齐三星健康睡眠页的模块结构）。
     *
     * 区块：昨晚总览 / 睡眠得分因素 / 睡眠阶段（睡眠图 + 占比 + 典型范围）/
     *      血氧 / 心率 / 呼吸频率。每个图表点进去都是独立的详情页。
     * 打鼾与皮肤温度按需求暂不接入，只在末尾一行注明，避免让人以为是漏做。
     *
     * 数据全部来自三星健康 SLEEP / BLOOD_OXYGEN / HEART_RATE，由 MainScreen 统一读好传入。
     */
    /**
     * epochDay -> 「9月18日」。空/非法值返回空串（宁可少显示一行，也不显示 1970 年）。
     *
     * ⚠ 是 `@Composable`：月/日的写法随语言变（英文是 "Sep 18"，韩语是 "9월 18일"）。
     */
    @Composable
    private fun Long.toDateLabel(): String {
        if (this <= 0L) return ""
        val d = java.time.LocalDate.ofEpochDay(this)
        return stringResource(R.string.s_4577f457, d.monthValue, d.dayOfMonth)
    }

    /**
     * 「能量评分」页 —— **两把尺子并排**。
     *
     * 左边是本 App 自己算的分（`core/Energy.kt`），右边是三星健康的「能量评分」。
     * 两者**输入不同、刻度不同、更新时机也不同**，所以这里不把它们合成一个数、
     * 也不说哪个更准 —— 只并排放在一起，让用户手上有两把尺子可以对着看。
     *
     * ## 为什么必须有左边那个
     * 三星那个分要求 Galaxy Watch7 以上 + 三星账号，而且**只在部分国家/地区提供**；
     * 拿不到的时候用户手上一个数都没有。本 App 自己算的这份只用「手表确实采到、
     * 并且存在本机」的东西：睡眠时长、睡眠期最低血氧、睡眠期平均心率 ——
     * 所以**任何机器上都给得出**。
     *
     * ## 为什么这一页要带诊断
     * 三星那一侧「没有数」有三种完全不同的原因（没授权 / 本机不支持这个数据类型 /
     * 授权了但三星健康里确实没有），处理方法也完全不同。第一版把它们都显示成
     * 一句「本机没有这项数据」，用户没法判断该去做什么 —— 所以这里把三者分开，
     * 并把 `lastEnergyAuthError` 原样带出来。
     *
     * @param nights 每晚睡眠统计（自研分的输入，来自本机 `sleep_nights`）。
     * @param samsungRecords 三星自己的能量评分（可能为空 = 本机拿不到）。
     */
    /**
     * 「能量得分因素」的一格：名称 + 等级（+ 该项得分）。
     *
     * @param part 该项这次有没有参与；**null = 没参与**，格子照样显示（写"未参与"），
     *   让"少了哪几项"是看得见的 —— 这与"缺席的分量按权重归一"是配套的两件事：
     *   一个保证分数不被稀释，一个保证缺席不被藏起来。
     */
    @Composable
    private fun FactorTile(name: String, part: Energy.Part?, modifier: Modifier = Modifier) {
        val level = part?.let { Energy.levelOf(it.componentScore.toDouble()) }
        val color = when (level) {
            Energy.Level.GOOD -> FactorGoodColor
            Energy.Level.FAIR -> FactorFairColor
            Energy.Level.LOW -> SettingsWarnColor
            else -> Color(0xFF9C8CB0)
        }
        Column(
            modifier
                .background(color.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // ⚠⚠ [name] 是 `Energy.COMPONENT_NAMES` 里的**中文原名 = 稳定匹配键**，不是给人看的。
            //   必须走 [Energy.componentLabel] —— 直接显示的话切到英文/韩文时这 10 个标签仍是中文，
            //   而 `translate.py --rescan` **扫不出来**（不是源码字面量，是运行期拼的）。
            //   2026-10-02 用户截图实付：宫格这格是中文、紧挨着的等级（`levelLabel` 走 core）
            //   却是英文 —— 正因为 `Text.lang` 是对的，错的只是这一处没换显示名。
            Text(Energy.componentLabel(name), fontSize = 12.sp, color = AppTextMuted, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    if (part == null) stringResource(R.string.s_1b2ec8ce) else Energy.levelLabel(level!!),
                    fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color,
                )
                Spacer(Modifier.weight(1f))
                if (part != null) {
                    Text(
                        "${part.componentScore}",
                        fontSize = 11.sp, color = AppTextMuted,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                }
            }
        }
    }

    @Composable
    private fun EnergyTab(
        nights: List<SleepNightEntity>,
        samsungRecords: List<EnergyScoreEntity>,
        activity: List<ActivityDayEntity> = emptyList(),
    ) {
        val lang = rememberLang()
        val ctx = this@MainActivity
        val today = remember { EnergySummary.todayEpochDay() }

        // 步数目标（「前一日的活动」那一项的满分线）。**用户没设过就是 null**，
        // 那一项因此缺席 —— 我们不会替他编一个"应该走一万步"。
        // 放在下面 remember 之前：它是 key 的一部分，晚一步读会让首次算出的分偏低一档。
        var stepGoal by remember { mutableStateOf<Int?>(null) }
        LaunchedEffect(Unit) {
            stepGoal = runCatching { SamsungHealthReader.readGoals(ctx).steps }.getOrNull()
        }

        // 自研分**现算**（派生量不落库 —— 与压力分、核心体温同一条纪律）。
        // remember 的 key 用「条数 + 最近那一天的日期」当指纹，而不是直接用 List：
        // 每次重组 List 的引用都可能变，但内容没变时不该重跑一遍评分与趋势。
        val nightsKey = nights.size to (nights.maxByOrNull { it.dateEpochDay }?.dateEpochDay ?: 0L)
        val activityKey = activity.size to (activity.maxByOrNull { it.dateEpochDay }?.dateEpochDay ?: 0L)
        val own = remember(nightsKey, activityKey, stepGoal, today) {
            EnergySummary.analyze(nights, activity, stepGoal, today)
        }
        val series = remember(nightsKey, activityKey, stepGoal, today) {
            Energy.series(
                EnergySummary.nights(nights, activity), today, stepGoal, EnergySummary.SERIES_NIGHTS,
            )
        }
        val ok = own as? Energy.Result.Ok
        val samsungLatest = remember(samsungRecords) {
            samsungRecords.maxByOrNull { it.dateEpochDay }
        }

        // 三星侧的授权/可用性诊断：**就地查**（不往 MainScreen 穿参数，它只在这一页用）。
        var samsungGranted by remember { mutableStateOf<Boolean?>(null) }
        var samsungAuthErr by remember { mutableStateOf<String?>(null) }
        var samsungReadErr by remember { mutableStateOf<String?>(null) }
        var samsungReadCount by remember { mutableStateOf(-1) }
        LaunchedEffect(Unit) {
            samsungGranted = runCatching { SamsungHealthReader.hasEnergyScorePermission(ctx) }
                .getOrDefault(false)
            // 这三个字段都是静态的：不查它们的话，"申请被拒"、"用户没同意"、
            // "权限拿到了但读的时候抛异常"看起来一模一样 —— 而三者要做的事完全不同
            samsungAuthErr = SamsungHealthReader.lastEnergyAuthError
            samsungReadErr = SamsungHealthReader.lastEnergyReadError
            samsungReadCount = SamsungHealthReader.lastEnergyReadCount
        }

        /**
         * 三星侧一句话结论（给并排卡片那一格用）。
         *
         * ⚠ 顺序与 [diag] 必须一致，否则两处说法会打架 —— 而这一格才是用户第一眼看的地方。
         */
        val samsungShort = when {
            samsungGranted == null -> stringResource(R.string.s_2607c610)
            samsungLatest != null -> samsungLatest.score.toString()
            samsungReadErr != null -> stringResource(R.string.s_d7e3bc86)
            samsungGranted == false && samsungAuthErr != null -> stringResource(R.string.s_f2592e8d)
            samsungGranted == false -> stringResource(R.string.s_1b36f455)
            else -> stringResource(R.string.s_94a9b62e)
        }
        // ⚠ 分支顺序是**有讲究的**：`samsungReadErr` 必须排在"samsungGranted == true 但没数据"
        // 之前。2026-09-21 之前是反的 —— 于是"读取时抛异常"会先命中"三星还没替你算过分数"，
        // 把真正的错误盖住，把用户指向完全错误的方向（他跑去检查三星健康，而问题在读取）。
        val diag = when {
            samsungGranted == null -> stringResource(R.string.s_ce6062a0)
            samsungReadErr != null ->
                stringResource(R.string.s_9c74e682, samsungReadErr.toString())
            samsungGranted == true && samsungLatest != null ->
                stringResource(R.string.s_8bfd06cd, samsungLatest.dateEpochDay.toDateLabel(), samsungLatest.score)
            samsungGranted == true ->
                stringResource(R.string.s_9ca41fd2, samsungReadCount) +
                    stringResource(R.string.s_0db5f7f3) +
                    stringResource(R.string.s_495e4497)
            samsungAuthErr != null ->
                stringResource(R.string.s_b8b7645d, samsungAuthErr.toString())
            else ->
                stringResource(R.string.s_c5222f2c)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：自研分 vs 三星分 ──
            //
            // 这一页的骨架是「**两个数字对着看** + **这个数字怎么来的**」，
            // 分区就照这条线切：上面是"是多少"，中间是"怎么来的"，下面是
            // "三星那边为什么是空的"和"口径边界"。⚠ 第 2、3 张卡（分量分解、
            // 近 14 晚趋势）都属于"怎么来的"，**共用一条分隔线**，不各画一条 ——
            // 三条线挨在一起会把一页切得太碎。
            item { SectionHeader(stringResource(R.string.s_1868d4d1), isFirstSection = true) }

            // ── 1) 两把尺子并排 ──
            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.s_ee6d2869), fontSize = 11.sp, color = AppTextMuted)
                                Spacer(Modifier.height(4.dp))
                                if (ok != null) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "${ok.score}",
                                            fontSize = 40.sp, fontWeight = FontWeight.Bold,
                                            color = EnergyScoreColor,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.s_00005206), fontSize = 12.sp, color = AppTextMuted,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        )
                                    }
                                    Text(
                                        "${Energy.levelLabel(ok.level)} · " +
                                            stringResource(R.string.s_61dd2de8, ok.dateEpochDay.toDateLabel()),
                                        fontSize = 11.sp, color = AppTextMuted,
                                    )
                                } else {
                                    Text(
                                        "—", fontSize = 40.sp, fontWeight = FontWeight.Bold,
                                        color = AppTextMuted,
                                    )
                                    Text(stringResource(R.string.s_a41fbf35), fontSize = 11.sp, color = SettingsWarnColor)
                                }
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.s_021e6ff8), fontSize = 11.sp, color = AppTextMuted)
                                Spacer(Modifier.height(4.dp))
                                if (samsungLatest != null) {
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "${samsungLatest.score}",
                                            fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            stringResource(R.string.s_00005206), fontSize = 11.sp, color = AppTextMuted,
                                            modifier = Modifier.padding(bottom = 4.dp),
                                        )
                                    }
                                    Text(
                                        samsungLatest.dateEpochDay.toDateLabel(),
                                        fontSize = 11.sp, color = AppTextMuted,
                                    )
                                } else {
                                    Text(
                                        "—", fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                        color = AppTextMuted,
                                    )
                                    // 这一格是用户第一眼看的地方，**必须直接给原因** ——
                                    // 诊断卡在页面下方，用户很可能没滚到那儿（2026-09-21 反馈）。
                                    Text(
                                        samsungShort,
                                        fontSize = 11.sp,
                                        color = if (samsungReadErr != null || samsungGranted == false)
                                            SettingsWarnColor else AppTextMuted,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            Energy.diffNote(ok?.score, samsungLatest?.score),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                        if (ok == null) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                (own as Energy.Result.NotApplicable).reason,
                                fontSize = 12.sp, color = SettingsWarnColor,
                            )
                        }
                    }
                }
            }

            // ── 分区：这个分是怎么来的 ──
            item { SectionHeader(stringResource(R.string.s_04ec4db3)) }

            // ── 2) 这个分是怎么来的（分量分解）──
            if (ok != null) {
                item {
                    AppCard(title = stringResource(R.string.s_12020e89)) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_6ae94112) +
                                    stringResource(R.string.s_50fe5272),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                            Spacer(Modifier.height(10.dp))

                            // 两列宫格。
                            // ⚠ 遍历的是 [Energy.COMPONENT_NAMES]（**全部十项**），
                            //   而不是 `ok.parts`（只有参与的）—— 缺席的项必须占一格，
                            //   否则用户根本不知道这个分本来还有哪几项。
                            for (row in Energy.COMPONENT_NAMES.chunked(2)) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    for (name in row) {
                                        FactorTile(
                                            name = name,
                                            part = ok.parts.firstOrNull { it.name == name },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // 奇数个时补一个等宽空位，免得最后一格被拉成整行宽
                                    if (row.size == 1) Box(Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(8.dp))
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(R.string.s_6696e5d4), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Spacer(Modifier.height(8.dp))
                            for (p in ok.parts) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(Energy.componentLabel(p.name), fontSize = 13.sp)
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                if (p.absolute) stringResource(R.string.s_180f2267) else stringResource(R.string.s_bb514c4a),
                                                fontSize = 10.sp, color = AppTextMuted,
                                            )
                                        }
                                        Text(
                                            stringResource(R.string.s_8543de84, p.currentText) +
                                                (p.baselineText?.let { stringResource(R.string.s_32c15fad, it) } ?: "") +
                                                stringResource(R.string.s_263fe090, Math.round(p.weight * 100f)) +
                                                (p.devUnits?.let { " · ${Energy.devText(it)}" } ?: ""),
                                            fontSize = 11.sp, color = AppTextMuted,
                                        )
                                    }
                                    Text(
                                        "${p.componentScore}",
                                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                                        color = EnergyScoreColor,
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            // 谁没参与、为什么 —— 这段和分数同样重要
                            if (ok.caveats.isNotEmpty()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .background(
                                            SettingsWarnColor.copy(alpha = 0.08f),
                                            RoundedCornerShape(6.dp),
                                        )
                                        .padding(10.dp)
                                ) {
                                    Column {
                                        for (c in ok.caveats) {
                                            Text(
                                                "· $c", fontSize = 11.sp,
                                                color = SettingsWarnColor,
                                            )
                                            Spacer(Modifier.height(4.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 3) 近 14 晚趋势 ──
            if (series.size >= 2) {
                item {
                    AppCard(title = stringResource(R.string.s_73a724dd, series.size)) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            EnergyBars(series)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "${series.first().dateEpochDay.toDateLabel()} → " +
                                    "${series.last().dateEpochDay.toDateLabel()} · " +
                                    stringResource(R.string.s_3164187e) +
                                    stringResource(R.string.s_74fe8276),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 4) 三星那一侧（2026-10-17 重做）──
            //
            // ⚠ 原来标题写「三星那一侧为什么是空的」，而内容第一行却是「已授权，读到 10月17日的分：85」——
            //   标题与内容自相矛盾，一串小字也没有重点。现在：**有分就把分和日期做成主角**，
            //   "为什么有时没有"这类解释全部收进下面的展开里。
            item {
                AppCard(title = stringResource(R.string.s_8fc12af0)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        val latest = samsungLatest
                        if (latest != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${latest.score}",
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                    color = EnergyScoreColor,
                                )
                                Text(
                                    " " + stringResource(R.string.s_00005206),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 5.dp),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    latest.dateEpochDay.toDateLabel(),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 5.dp),
                                )
                            }
                        } else {
                            Text(
                                diag,
                                fontSize = 12.sp,
                                color = SettingsWarnColor,
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        CollapsibleNotes(
                            listOf(
                            stringResource(R.string.s_39ed75ed) +
                            stringResource(R.string.s_6e4510f1) +
                            stringResource(R.string.s_453d4555) +
                            stringResource(R.string.s_028d23f9) +
                            stringResource(R.string.s_91d17a6f) +
                            stringResource(R.string.s_5cf9281e),
                            )
                        )
                    }
                }
            }

            // ── 分区：口径与边界 ──
            item { SectionHeader(stringResource(R.string.s_63fbdf34)) }

            // ── 5) 口径与边界 ──
            //
            // ⚠⚠ 2026-10-17 两处改动（用户要求）：
            //   ① **默认收起**：此前这一页把口径全文直接摊开，其它页（活动/饮食/血糖）都是
            //      `CollapsibleNotes`。用户原话「为什么这个数据口径没做展开？所有数据口径都要有展开」。
            //   ② 删掉「十个分量用了两种刻度…」及其后的说明（权重分配、我们的范围、对比三星），
            //      只留**分数怎么算**（SCALE_NOTE）与**合规免责**（Disclaimer）—— 后者必须留。
            item {
                AppCard(title = stringResource(R.string.s_63fbdf34)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        CollapsibleNotes(
                            listOf(
                                Energy.SCALE_NOTE,
                                Disclaimer.energy(lang),
                            ),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    /**
     * 「今日数据未同步，以下为 X月X日 的数据」的顶部横幅（2026-10-09 新增，睡眠/血氧共用）。
     *
     * ## 为什么要有它
     * 睡眠页 / 血氧页的主卡此前**只认内存里那次同步的结果** ⇒ 今天还没同步、
     * 同步失败、或进程刚重启时整页空白，哪怕库里躺着几百条数据。
     * 现在改为**回退显示库里的旧数据**，但旧数据必须**明确标出自己是哪一天的** ——
     * 不标的话用户会把上周的睡眠当成昨晚的（这正是 `stagesJson` 那条
     * 「没有 ≠ 0」的同一个母题：**"旧" ≠ "今天"**）。
     *
     * ⚠ 传入的是 `epochDay`（`Long`）而不是格式化好的串：格式化在**渲染点**做，
     *   否则语言一换，这条横幅的日期还是旧语言（见 `ResText` 的教训）。
     */
    @Composable
    private fun StaleDataBanner(epochDay: Long) {
        val todayEpochDay = java.time.LocalDate.now().toEpochDay()
        val dateText = remember(epochDay) {
            SimpleDateFormat("M月d日", Locale.getDefault())
                .format(Date(java.time.LocalDate.ofEpochDay(epochDay)
                    .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()))
        }
        // 回退横幅：橙色填充**有意不描边** —— 它是告警，不是内容卡。
        AppSurface(
            Modifier.fillMaxWidth(),
            containerColor = Color(0xFFFFF3E0),
            borderColor = null,
        ) {
            Text(
                // ⚠⚠ 回退日**就是今天**时，不许说「今日数据未同步，以下为 M月d日 的数据」——
                //   那个 M月d日 就是今天，读起来自相矛盾（用户 2026-10-13 的截图正是这句：
                //   横幅写「以下为 10月13日 的数据」，而那天就是 10月13日）。
                //
                //   判据改成按"回退日是不是今天"分两种说法：
                //   · 回退日更早 ⇒ 原句（**必须点名是哪一天**，否则用户会把上周的当今天）；
                //   · 回退日就是今天 ⇒ 只有"没同步到"这一件事可说，数据本身就是今天的。
                //   ⚠ 只能写在**这一处**：三个页面都用这个横幅，各写一份必然漂移。
                if (epochDay == todayEpochDay) {
                    stringResource(R.string.s_b014b051)
                } else {
                    stringResource(R.string.s_c7a2cca7, dateText)
                },
                fontSize = 12.sp,
                color = Color(0xFF8A4B00),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }

    /** 近若干晚的分数柱（纯 Box 拼，不引图表库）。 */
    @Composable
    private fun EnergyBars(points: List<Energy.Point>) {
        if (points.isEmpty()) return
        Row(
            Modifier.fillMaxWidth().height(80.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            for (p in points) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight((p.score / 100f).coerceAtLeast(0.06f))
                        .background(EnergyScoreColor.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                )
            }
        }
    }

    @Composable
    private fun SleepTab(
        state: SleepUiState,
        apnea: List<SleepApneaEntity>,
        loading: Boolean,
        samsungGranted: Boolean?,
        /** 「问不出来」（SDK 连不上）—— 与「未授权」分开说，见 `samsungUnreadable` 的说明。 */
        samsungUnreadable: Boolean,
        samsungSleepOk: Boolean?,
        odi: SleepOdiEntity?,
        /** 库里落下的历史晚（`sleep_nights`，按日期降序）。**这一页的历史区用它，不读三星。** */
        nights: List<SleepNightEntity>,
        onOpenNight: (Long) -> Unit,
        onOpenDetail: (SleepDetailKind) -> Unit,
        onRefresh: () -> Unit,
        /** 重新走一次三星健康授权（`去授权` 按钮）。⚠ 与 `onRefresh` 是**两件事**： */
        /** 重读只是再读一遍，权限不会自己回来。 */
        onAuth: suspend () -> Unit,
    ) {
        val lang = rememberLang()
        // 作息目标（六类目标里属于睡眠的那两个）。
        //
        // ⚠ **必须放在下面的 early return 之前**：Compose 要求 `remember` /
        // `LaunchedEffect` 的调用顺序在每次重组时完全一致，放在返回分支之后
        // 就会在"有数据/没数据"两种情况下走不同的调用序列而报错。
        var sleepGoals by remember { mutableStateOf<DailyGoals?>(null) }
        LaunchedEffect(Unit) {
            sleepGoals = SamsungHealthReader.readGoals(this@MainActivity)
        }

        // ── 历史睡眠（2026-10-10 新增）──
        //
        // ⚠⚠ **必须在 LazyColumn 之前算好**（两个理由）：
        //   ① `LazyColumn { }` 的 content 是 `LazyListScope.() -> Unit`，**不是** @Composable
        //      作用域 —— 在它里面调 `remember` 会直接编译失败。我第一版就写在了里面。
        //   ② 空态那一支（`night == null`）**也要用它**：库里有的就必须看得见，
        //      不能因为"今天没同步到"而整块消失（见下面 `SleepEmptyScreen`）。
        //
        // 取**最近 [SLEEP_HISTORY_NIGHTS] 晚**：库里按 `keepLatest(400)` 留着一年多，
        // 一次全列出来会变成一堵数字墙。
        val history = remember(nights) {
            nights.sortedByDescending { it.dateEpochDay }.take(SLEEP_HISTORY_NIGHTS)
        }

        val night = state.night

        if (loading || samsungGranted == null) {
            // samsungGranted 还没解析出来时也当作"读取中"：否则会在启动瞬间
            // 闪一下"尚未授权三星健康"，而实际上只是权限状态还没查完。
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.s_084e5151), fontSize = 13.sp, color = AppTextMuted)
            }
            return
        }
        if (night == null) {
            // ⚠⚠ 这里**不再是"整页空白 + 一句原因"**（2026-10-11）。
            //
            // 原来的写法是 `SleepEmptyHint(...); return` ⇒ 整页只剩一段说明文字，
            // 而**下面的「历史睡眠」分区一起被 return 掉了**。后果：三星健康一旦读不到
            // （连不上 / 未授权），用户看到的是"什么都没有"，哪怕 `sleep_nights` 里
            // 存着回填的十几晚 —— 他的原话是"昨天的明明同步了可以在软件里看到啊"。
            //
            // 这与 v116 那条不变量是同一件事：**同步失败只影响"能不能更新"，
            // 不影响"能不能看"**。所以空态也必须把库里的历史列出来。
            Column(Modifier.fillMaxSize()) {
                SleepEmptyScreen(
                    samsungGranted = samsungGranted,
                    unreadable = samsungUnreadable,
                    samsungSleepOk = samsungSleepOk,
                    onRefresh = onRefresh,
                    onAuth = onAuth,
                    history = history,
                    onOpenNight = onOpenNight,
                )
            }
            return
        }
        val totals = night.stageTotals()
        val stageSum = totals.values.sum().coerceAtLeast(1L)
        val actual = night.actualSleepMs()
        val awakeMs = totals[SleepStageKind.AWAKE] ?: 0L
        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

        // ── 睡眠期血氧 / 心率的**每小时**汇总（本页两张图的素材）──
        //
        // 按「入睡→起床的每一个整点」分桶，**不是**按今天 0 点：睡眠跨午夜时
        // 后者会有两个错（负数侧混桶 + 更早的格被丢），判据见 `core/SleepHours.kt`，
        // 由离线校验台架逐条钉住。桶里的 `hour` 是**槽位序号**，
        // 钟点标签在第二个返回值里，要经 `labelOf` 传给横轴。
        val spo2Buckets = remember(state.spo2, night.startMs, night.endMs) {
            bucketBySleepHours(state.spo2, night.startMs, night.endMs)
        }
        val hrBuckets = remember(state.hr, night.startMs, night.endMs) {
            bucketBySleepHours(state.hr, night.startMs, night.endMs)
        }

        val spo2Sleep = spo2Buckets.first
        val spo2SleepSlots = spo2Sleep.map { it.hour }
        val spo2SleepByHour = spo2Sleep.associateBy { it.hour }
        val spo2SleepLabels = spo2Buckets.second
        val spo2SleepBounds = spo2Bounds(spo2Sleep.flatMap { listOf(it.min, it.max) })
        // 主数字取「每小时均值的平均」—— 与「血氧」页那张同名卡**同一个口径**。
        // 副文本里的"整晚均值"才是三星自己那条（night.spo2Avg，按所有采样点平均）。
        // 两者口径不同、数值可能差 1%，都摆出来，免得对着两个页面以为哪边写错了。
        val spo2SleepAvg = spo2Sleep.takeIf { it.isNotEmpty() }
            ?.map { it.avg }?.average()?.roundToInt()
        val spo2SleepMin = spo2Sleep.minOfOrNull { it.min }

        val hrSleep = hrBuckets.first
        val hrSleepSlots = hrSleep.map { it.hour }
        val hrSleepByHour = hrSleep.associateBy { it.hour }
        val hrSleepLabels = hrBuckets.second
        val hrSleepBounds = chartBounds(hrSleep.flatMap { listOf(it.min, it.max) })
        val hrSleepAvg = hrSleep.takeIf { it.isNotEmpty() }
            ?.map { it.avg }?.average()?.roundToInt()
        val hrSleepMin = hrSleep.minOfOrNull { it.min }
        val hrSleepMax = hrSleep.maxOfOrNull { it.max }

        // ⚠⚠ 这一页里**不再有任何日期横带**（2026-10-12 第二次返工）：日期条挂在标题行
        //   （见 `DetailHeader` 的 `trailing`），弹层在功能页那一层 —— 所以这一页既不
        //   `stickyHeader` 盖内容、也没有多出来的白带把内容区顶下去。
        //   保留这层 `Column` 只为给列表一个 `weight(1f)` 约束。
        Column(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 0) 未同步横幅（2026-10-09）──
                //
                // 只在**回退态**出现（`fresh == false` 且知道是哪一天）：
                // 说明"库里这份是哪天的"，免得用户把上周的睡眠当成昨晚的。
                // ⚠ 位置在**所有分区之前**：它是"这份数据从哪来"的元信息，
                //   压在某张卡下面会让它看起来只解释那一张卡。
                state.fallbackEpochDay?.let { day ->
                    item { StaleDataBanner(day) }
                }

                // ── 分区 1：本晚概况 ──
                //
                // 分区是**视觉判断**，不是"把所有 Spacer 换成分隔线"——把一句话拆成
                // 两段的分隔线比不画还糟。这里的切法按**这条数据是谁给的**来分：
                //   ① 三星健康直接给的（总览 / 得分因素 / 阶段 / 血氧 / 心率）
                //   ② 我们自己算出来的（ODI、呼吸暂停筛查）
                //   ③ 我们算不了、但要说清"在哪儿能看"的（呼吸率）
                // 不画的一处：`state.naps` 那几行属于①里的总览卡，**留在卡内**。
                //
                // ⚠⚠ **不画"上分隔线"**（2026-10-12）：这一页外壳是「标题行 + 内容」，
                //   第一个分区的上分隔线正好压在标题行下沿上 —— 上方没有内容可分隔，
                //   那根线（连它上下各 10dp）纯粹占位置。理由与判据见 `SectionHeader`。
                // ⚠ 判据要与上面那条横幅**同源**：横幅（`fallbackEpochDay != null`）在时
                //   它就是"上面的内容块"、线要画；只有在没有横幅时这根线才是多余的。
                item {
                    SectionHeader(
                        stringResource(R.string.s_1e7a8c29),
                        isFirstSection = state.fallbackEpochDay == null,
                    )
                }

                // ── 1) 昨晚总览 ──
                item {
                    AppCard(title = stringResource(R.string.s_0034acd2)) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            AppKpi(
                                value = fmtDuration(actual),
                                unit = stringResource(R.string.s_24f6d380),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                // ⚠ 回退态下这里是**过去某一天**的时间段，所以日期必须带上，
                                //   否则"23:41 - 07:12"会被读成昨晚（横幅只说了"以下为 X月X日"，
                                //   卡片自己也该说得清 —— 用户可能直接翻到这张卡）。
                                (state.fallbackEpochDay?.let {
                                    SimpleDateFormat("M月d日 ", Locale.getDefault()).format(
                                        Date(java.time.LocalDate.ofEpochDay(it)
                                            .atStartOfDay(java.time.ZoneId.systemDefault())
                                            .toInstant().toEpochMilli())
                                    )
                                } ?: "") +
                                    "${tsFmt.format(Date(night.startMs))} - ${tsFmt.format(Date(night.endMs))}" +
                                    stringResource(R.string.s_2d3d3088, fmtDuration(night.durationMs)),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                            if (night.score != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_3443773a, night.score.toString()),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                            // 小睡单独列出来，**不并进上面的统计**。
                            // 不显示的话，用户看到"最近一晚"只有 1 小时会以为整晚丢了
                            // （2026-09-29 的截图反馈）；而把它并进"实际睡眠时间"又会让
                            // 那个数与下面的睡眠图（只画主睡眠）对不上。
                            // ── 睡眠阶段占比（2026-10-15 从「睡眠阶段」卡搬来）──
                            // 主卡要能独立回答"昨晚睡得怎么样"，结构占比是最直接的一眼。
                            Spacer(Modifier.height(12.dp))
                            StageBreakdown(night, showTypicalText = false)
                            if (state.naps.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    stringResource(R.string.s_8acdf120, state.naps.size) +
                                        state.naps.joinToString(LanguageStore.current(this@MainActivity).itemSeparator()) {
                                            "${tsFmt.format(Date(it.startMs))}-" +
                                                "${tsFmt.format(Date(it.endMs))}"
                                        },
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(R.string.s_01b0a370) +
                                        stringResource(R.string.s_d4aa67f3),
                                    fontSize = 10.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // ── 2) 睡眠得分因素 ──
                item {
                    AppCard(title = stringResource(R.string.s_c2592e16)) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            SleepFactorRow(
                                listOf(
                                    SleepFactor(
                                        stringResource(R.string.sleep_factor_actual), fmtDuration(actual),
                                        typicalOfDuration(actual / 3_600_000f),
                                    ),
                                    SleepFactor(
                                        stringResource(R.string.stage_deep), fmtDuration(totals[SleepStageKind.DEEP] ?: 0L),
                                        typicalOf(totals[SleepStageKind.DEEP] ?: 0L, stageSum, SleepStageKind.DEEP),
                                    ),
                                ),
                            )
                            Spacer(Modifier.height(10.dp))
                            SleepFactorRow(
                                listOf(
                                    SleepFactor(
                                        stringResource(R.string.stage_rem), fmtDuration(totals[SleepStageKind.REM] ?: 0L),
                                        typicalOf(totals[SleepStageKind.REM] ?: 0L, stageSum, SleepStageKind.REM),
                                    ),
                                    SleepFactor(
                                        stringResource(R.string.stage_awake), fmtDuration(awakeMs),
                                        typicalOf(awakeMs, stageSum, SleepStageKind.AWAKE),
                                    ),
                                ),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                stringResource(R.string.s_2f648a07) +
                                    stringResource(R.string.s_6df6aca8) +
                                    stringResource(R.string.s_042b7679),
                                fontSize = 10.sp, color = AppTextMuted,
                            )
                        }
                    }
                }

                // ── 3) 睡眠阶段（睡眠图 + 占比） ──
                item {
                    AppCard(
                        title = stringResource(R.string.s_1e914e32),
                        onClick = { onOpenDetail(SleepDetailKind.STAGE) },
                        trailing = { Text("›", fontSize = 16.sp, color = AppTextMuted) },
                    ) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            Hypnogram(
                                stages = night.stages,
                                startMs = night.startMs,
                                endMs = night.endMs,
                                height = 120.dp,
                            )
                            Spacer(Modifier.height(2.dp))
                            TimeAxisRow(night.startMs, night.endMs)
                            // ⚠ 占比条与图例**已经搬到上面那张主卡**（2026-10-15）：
                            //   主卡回答"睡了多久 + 睡得怎么样"，所以结构（深睡/浅睡/REM/清醒）
                            //   该在那里；这一张只负责"什么时候睡的"（时序）。
                            //   ⚠ 别在这儿再放一份 —— 同一组数字出现两遍，两处的取整还可能不同。
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(12.dp).background(Color(0x55FFFFFF)))
                                Spacer(Modifier.width(5.dp))
                                Text(stringResource(R.string.s_cddb1820), fontSize = 11.sp, color = AppTextMuted)
                            }
                        }
                    }
                }

                // ── 4) 睡眠期血氧（只画睡眠小时）──
                //
                // 与「血氧」页那张同名卡**同口径、同画法**：横轴是睡眠覆盖到的每个整点，
                // 每根柱是该小时的最低~最高，主数字是只算睡眠小时的平均。
                //
                // 此前这里画的是整晚连续折线 —— 同一份数据在两个页面上长得不一样，
                // 对着看会以为哪边算错了。折线没有丢：点进详情（Spo2Detail）看的还是它，
                // 那里适合看"几点骤降"这种连续走势，卡片这层适合看逐小时的波动幅度。
                item {
                    AppCard(
                        title = stringResource(R.string.s_98155e0f),
                        onClick = { onOpenDetail(SleepDetailKind.SPO2) },
                        trailing = { Text("›", fontSize = 16.sp, color = AppTextMuted) },
                    ) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            if (spo2Sleep.isEmpty()) {
                                Text(stringResource(R.string.s_7ced89b1), fontSize = 13.sp, color = AppTextMuted)
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${spo2SleepAvg ?: "--"}%",
                                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                        color = Spo2SleepColor,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.s_8fc256c1, dash(spo2SleepMin)) +
                                            (night.spo2Avg?.let { stringResource(R.string.s_d82ed4a2, it) } ?: ""),
                                        fontSize = 13.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                HourRangeChart(
                                    slots = spo2SleepSlots,
                                    hours = spo2SleepByHour,
                                    bounds = spo2SleepBounds,
                                    labelEvery = 1,
                                    sleepDiff = false,
                                    awakeColor = Spo2SleepColor,
                                    labelOf = { spo2SleepLabels[it] ?: "$it" },
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_bd58d147) +
                                        stringResource(R.string.s_4e330421),
                                    fontSize = 10.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // ── 分区 2：我们算出来的（不是三星给的）──
                //
                // ⚠ ODI 与呼吸暂停筛查**都建立在三星给的分钟级血氧之上**，所以严格说
                // 它们不是独立测量 —— 但"谁算的"是用户能感知的区别：上面几张卡的数字
                // 三星健康 App 里也有，下面这张没有。标题写"本机计算"，与 `ODI` /
                // `Apnea.VS_ODI_NOTE` 里已有的措辞保持一致。
                item { SectionHeader(stringResource(R.string.s_e530ed80)) }

                // ── 5) 氧减指数 ODI ──
                item { OdiCard(odi) }

                // ── 5.5) 睡眠呼吸暂停（三星健康监测器的筛查信号）──
                // **刻意不另开详情页**：它的信息就三行（结果 / 哪一晚 / 免责），
                // 为三行铺一路路由不划算。完整口径差别写在卡里，和 ODI 挨着正好能对着看。
                item {
                    AppCard(title = stringResource(R.string.s_460052a1)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            val ap = apnea?.maxByOrNull { it.startMs }
                            if (ap == null) {
                                Text(stringResource(R.string.s_51d9b49c), fontSize = 13.sp, color = SettingsWarnColor)
                                Spacer(Modifier.height(4.dp))
                                Text(Apnea.UNAVAILABLE_NOTE, fontSize = 12.sp, color = AppTextMuted)
                            } else {
                                Text(
                                    ap.dateEpochDay.toDateLabel() + stringResource(R.string.s_b9c3b3bd),
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Apnea.label(ap.sign),
                                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                                    color = if (Apnea.isAlarming(ap.sign)) SettingsWarnColor
                                    else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(Apnea.VS_ODI_NOTE, fontSize = 11.sp, color = AppTextMuted)
                            Spacer(Modifier.height(6.dp))
                            Text(Disclaimer.apnea(lang), fontSize = 11.sp, color = AppTextMuted)
                        }
                    }
                }

                // ── 6) 睡眠心率（只画睡眠小时）──
                //
                // 与「心率」页的「睡眠心率」卡同口径、同画法。数据来源固定是三星健康的
                // 睡眠期序列（本机手表记录不参与这一页）—— 所以这里可以直接写明来源，
                // 不需要像心率页那样再分"实测 / 夜间估算"两种情况。
                // 连续波形点进详情（SleepHrDetail）看，那里还有每 10 分钟的最低~最高细柱。
                item {
                    AppCard(
                        title = stringResource(R.string.s_1e75792b),
                        onClick = { onOpenDetail(SleepDetailKind.HR) },
                        trailing = { Text("›", fontSize = 16.sp, color = AppTextMuted) },
                    ) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            if (hrSleep.isEmpty()) {
                                Text(stringResource(R.string.s_11fbcf62), fontSize = 13.sp, color = AppTextMuted)
                            } else {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        "${hrSleepAvg ?: "--"}",
                                        fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                        color = HrSleepColor,
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        stringResource(R.string.s_1c0dd13c),
                                        fontSize = 13.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        stringResource(R.string.s_133e3ed8, dash(hrSleepMin), dash(hrSleepMax)),
                                        fontSize = 12.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                Text(
                                    stringResource(R.string.s_74f54329).format(actual / 3_600_000.0),
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                                Text(
                                    stringResource(R.string.s_bb4cdd31),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                                Spacer(Modifier.height(8.dp))
                                HourRangeChart(
                                    slots = hrSleepSlots,
                                    hours = hrSleepByHour,
                                    bounds = hrSleepBounds,
                                    labelEvery = 1,
                                    sleepDiff = false,
                                    labelOf = { hrSleepLabels[it] ?: "$it" },
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_b2c27402),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // ── 分区 3：其余可看的地方 ──
                //
                // 收尾段刻意只放三样：**呼吸率去向**（我们采不了、但别的地方能给）、
                // **作息目标**（有才显示，没有就整块不出现）、**免责声明**（永远最后）。
                // 睡眠心率**不属于这里** —— 它是三星给的、和血氧同口径，留在分区 1 的末尾
                // 更顺：用户看完血氧自然往下看心率，中间插一条分隔线反而像换了个话题。
                item { SectionHeader(stringResource(R.string.s_0029fe38)) }

                // ── 7) 呼吸（频率 + 氧减）──
                // 三星**没有**开放呼吸频率，这一点到现在也没变；但"没开放"不等于"做不了"：
                // 睡眠场景改由上面的 ODI 承担（分钟级血氧够数氧减事件），
                // 静息点检的呼吸率用我们自己采的原始波形算（血氧页/心电页）。
                // 这一张卡因此从"数据不可用"改成"我们能给什么、分别在哪儿"。
                item {
                    AppCard(
                        title = stringResource(R.string.s_002b8fac),
                        onClick = { onOpenDetail(SleepDetailKind.RESP) },
                        trailing = { Text("›", fontSize = 16.sp, color = AppTextMuted) },
                    ) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_accfeec1) +
                                    stringResource(R.string.s_d2cd5796),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_c18c57a3) +
                                    stringResource(R.string.s_e2d327e9),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }

                sleepHistorySection(history, onOpenNight)


                // ── 作息目标（六类目标里属于睡眠的那两个）──
                // 只在**设过**的时候显示：没设目标时写一行"还没设目标"是噪音，
                // 而这一页已经有六张卡了。目标本身的说明见「活动」页的口径卡。
                item {
                    val g = sleepGoals
                    if (g != null && (g.bedTime != null || g.wakeTime != null)) {
                        Text(
                            stringResource(R.string.s_2b1f9c1a) + listOfNotNull(
                                g.bedTime?.let { stringResource(R.string.s_3ae84ac3, it) },
                                g.wakeTime?.let { stringResource(R.string.s_4bb6c5f2, it) },
                            ).joinToString(" · "),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }

                item {
                    Text(
                        stringResource(R.string.s_d637e712) +
                            stringResource(R.string.s_21bfbf56),
                        fontSize = 11.sp, color = AppTextMuted,
                    )
                }
            }
        }
    }

    /**
     * 「历史睡眠」分区：把 `sleep_nights` 里那些晚画成趋势图 + 逐晚列表。
     *
     * ⚠⚠ **抽成 `LazyListScope` 扩展是刻意的**（2026-10-11）：这份内容有**两个**渲染点 ——
     *   「本晚数据在」的正常页，和「本晚读不到」的空态页。
     *   原来它只写在前者里面，于是三星健康一读不到（连不上 / 未授权 / 服务被回收），
     *   整页 early return 就把历史一起带走了 ⇒ 库里存着的十几晚全看不见。
     *   两份各写一遍迟早漂移成「空态少一段」—— 所以只留一份。
     *
     * ⚠ 数据源必须是**库里那张表**（调用方传进来的 `history`），不是本次同步的内存结果 ——
     *   这样它不受「今天同步成不成功」影响（v116 那条「库里有的，界面必须能显示」）。
     */
    private fun LazyListScope.sleepHistorySection(
        history: List<SleepNightEntity>,
        onOpenNight: (Long) -> Unit,
    ) {
            // ── 分区：历史睡眠（2026-10-10 新增）──
            //
            // 用户报：「打开睡眠后，不能查看历史睡眠记录」。
            // 起因是这一页从建成起只讲**最近一晚**（`state.night`）—— 而"昨晚"之外的每一晚
            // 其实早就落库了（`sleep_nights`，`syncSleepDays` 还会往回补 14 晚），
            // **只是没有任何地方看得到它们**。能量页那条趋势讲的是"评分"，不是
            // "我那天到底睡了多久"，替代不了这一页。
            //
            // ⚠ 数据源必须是**库里那张表**（`nights`），不是本次同步的内存结果 ——
            //   这样它不受"今天同步成不成功"影响（与 v116 那条「库里有的，界面必须能显示」同源）。
            // ⚠ 这一块的渲染**与 `night == null` 无关**：**2026-10-11 已修** ——
            //   本函数现在由「有本晚数据」与「空态」两条路径**共用**（`SleepTab` / `SleepEmptyScreen`），
            //   所以 `night == null` 时历史照样列得出来（原来整页 early return 会把它一起带走）。
            if (history.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.s_4756b767)) }

                // 1) 睡眠时长趋势。**≥2 晚才画**：一晚的"趋势"是一个点，
                //    画出来只会让人以为"很平稳"（与 [StageDetail] 那条深睡趋势同一条纪律）。
                if (history.size >= 2) {
                    item {
                        AppCard(title = stringResource(R.string.s_aeac75fc)) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_9fcd96e2, history.size),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                                Spacer(Modifier.height(10.dp))
                                SleepDurationTrend(history, onOpen = onOpenNight)
                                Spacer(Modifier.height(8.dp))
                                ChartLegend(
                                    listOf(
                                        SleepActualColor to stringResource(R.string.s_6e58e3da),
                                        SleepAwakeColor to stringResource(R.string.s_a129ddf4),
                                    )
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_75a97cae),
                                    fontSize = 10.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // 2) 逐晚记录：一行一晚，点进去看那一晚的详情。
                item {
                    AppCard(title = stringResource(R.string.s_68e10b3f)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            history.forEachIndexed { i, n ->
                                if (i > 0) {
                                    // 用裸 `Box` 而不是 `SettingsDivider()`：后者自带
                                    // `padding(start = 16.dp)`（那是给设置行做缩进用的），
                                    // 放在已经缩进 16dp 的卡里会变成 32dp、看着歪。
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .height(1.dp)
                                            .background(AppGridLine)
                                    )
                                }
                                SleepHistoryRow(n) { onOpenNight(n.dateEpochDay) }
                            }
                        }
                    }
                }
            }
    }

    // ═══════════════════ 睡眠日期选择（2026-10-12）═══════════════════
    //
    // 用户要的是「和三星健康一样的查看历史睡眠记录」：顶部一个「今天 ▾」，
    // 点开是月历；**有数据的日期着色、没数据的置灰**，选一天看那一天。
    //
    // 数据源是 **库里的 `sleep_nights`**（调用方传进来的 `nights`），不是三星健康 ——
    // 与 v116「库里有的，界面必须能显示」同一条：读不到三星时日历照样能用、照样能翻。
    // 库按 `keepLatest(400)` 留着，所以最多能翻约 13 个月。

    /**
     * 有睡眠记录、且有三星得分时，日历格子的底色。
     *
     * ⚠ 只是**视觉辅助**（把"这一晚大概什么水平"摊在一个月里看），
     *   所以**不给文字标签**（不写「良好/一般/注意」）—— 那会变成替三星的评分下结论。
     *   具体分数在下面的「逐晚记录」里逐条写着。
     */
    private fun sleepDayColor(score: Int): Color = when {
        score >= 80 -> Color(0xFF7FC98A)   // 偏绿
        score >= 60 -> Color(0xFFF2CE5C)   // 偏黄
        else -> Color(0xFFF0A868)          // 偏橙
    }

    /** 一个月里，有记录但**没有三星得分**的那些天用什么色（中性蓝灰，不等于"没数据"）。 */
    private val SleepDayNoScoreColor = Color(0xFFBDC9DA)

    /** 「今天」但今天那晚还没记录时的底色。 */
    private val SleepDayTodayEmptyColor = Color(0xFFE6E6EA)

    /**
     * 心率 / 血氧这类**没有分级依据**的日历底色（2026-10-13）。
     *
     * ⚠ 刻意用**一个**中性色而不是像睡眠那样按得分分级：心率/血氧的每日读数没有
     *   "好/中/差"的三分法 —— 给它们上色等于**替用户下结论**（与「不给正常/偏高/偏低分级」
     *   是同一条纪律）。这里颜色只表达一件事：**那天有数据**。
     */
    private val HistoryDayHasDataColor = Color(0xFF8FB3D9)

    /**
     * 睡眠的日历底色：按那一晚的三星得分分级；**没有得分**（三星没算）用 [SleepDayNoScoreColor]。
     *
     * ⚠ 返回的是函数而不是 Map：`HistoryDateSheet` 的 [tint] 是"给定某天要什么颜色"，
     *   网格里会出现哪些天由弹层自己决定（含月份翻页），调用方不该先算一遍。
     */
    private fun sleepDayTint(nights: List<SleepNightEntity>): (Long) -> Color {
        val byDay = nights.associateBy { it.dateEpochDay }
        return { day -> byDay[day]?.samsungScore?.let { sleepDayColor(it) } ?: SleepDayNoScoreColor }
    }

    @Composable
    private fun rememberDayFmt(): SimpleDateFormat {
        val ctx = LocalContext.current
        val lang = rememberLang()
        return remember(lang) {
            val loc = ctx.resources.configuration.locales[0]
            // ⚠ 只取「日」：趋势图的横轴一格只有 ~24dp，`M月d日` 会被裁成「10月」。
            //   月份写在卡片的说明行里（日期区间）。
            SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "d"), loc)
        }
    }

    @Composable
    private fun rememberMonthFmt(): SimpleDateFormat {
        val ctx = LocalContext.current
        val lang = rememberLang()
        return remember(lang) {
            val loc = ctx.resources.configuration.locales[0]
            SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(loc, "yMMMM"), loc,
            )
        }
    }

    /** 星期几的短名（`周一` / `Mon`）。⚠ 走系统 locale，不写死中文。 */
    @Composable
    private fun rememberWeekdayFmt(): SimpleDateFormat {
        val ctx = LocalContext.current
        val lang = rememberLang()
        return remember(lang) {
            val loc = ctx.resources.configuration.locales[0]
            SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(loc, "EEE"), loc)
        }
    }

    /**
     * 顶部那条「今天 ▾」胶囊（仿三星健康睡眠页）。**睡眠 / 心率 / 血氧共用**。
     *
     * ⚠⚠ **挂在 `DetailHeader` 的 `trailing` 槽里**（2026-10-12 第二次返工）：
     *   头一版放进 `LazyColumn` 用 `stickyHeader` 钉，结果它**盖住**了滚过去的卡片；
     *   第二版把它挪到列表外面单独一条，横带又把内容区**顶下去**、顶部多出一条白带 ——
     *   用户两次都说"内容被遮住"。挂进标题行之后既不盖内容、也不占额外高度。
     *
     * ⚠ 名字从 `SleepDatePill` 改成 `HistoryDatePill`（2026-10-13）：它现在服务于三个功能页
     *   （用户要求「血氧、心率使用跟睡眠一样的查看历史按钮」）—— 同一个按钮、同一套语义，
     *   叫 `Sleep*` 会让人以为只有睡眠能用，进而**复制一份**出来（那就是三份会漂移的日历）。
     */
    @Composable
    private fun HistoryDatePill(label: String, onClick: () -> Unit) {
        // ⚠⚠⚠ 2026-11-07 用户「这个按钮看不清」：底色是**写死的近白** `#F0F0F3`
        //   （仿三星健康睡眠页的白胶囊），但两个 Text 原来都吃主题文字色 ——
        //   `Text(label)` 没写 color ⇒ 继承 `LocalContentColor`（深色/玻璃主题下近白），
        //   `▾` 用 `AppTextMuted`（浅灰）⇒ **白底白字**，对比度接近 0。
        //   ⚠ 判据：**底色写死亮色时，字色必须一起写死深色**，绝不能继承主题文字色；
        //     换底色就要换字色（按底色亮度定深/浅，与 `AppOnAccent` 的 `luminance()>0.45` 同理）。
        val pillBg = Color(0xFFF0F0F3)
        val onPill = Color(0xFF1B1B22)
        Row(
            Modifier
                .background(pillBg, RoundedCornerShape(50))
                .clickable { onClick() }
                .padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = onPill)
            Spacer(Modifier.width(6.dp))
            Text("▾", fontSize = 11.sp, color = onPill.copy(alpha = 0.70f))
        }
    }

    /**
     * 「查看历史」的日期选择弹层：一个月一张网格。**睡眠 / 心率 / 血氧共用**（2026-10-13 泛化）。
     *
     * 三态（与三星健康一致）：
     *   · **有记录** → 着色圆底（颜色由 [tint] 给：睡眠按那一晚的三星得分、心率/血氧用统一色），可点
     *   · **今天**   → 始终可点（哪怕今天还没有记录）—— 否则用户会被困在历史里回不到实时页
     *   · **没记录 / 未来** → 灰字、不可点
     *
     * ⚠ 月份只能在 [最早有记录的那个月, 本月] 之间翻：往前翻到没有数据的月份是空网格，
     *   往后翻是未来（永远没有数据）。两头都挡住，省得用户以为软件坏了。
     *
     * ⚠⚠ 形参是 `days`（**有数据的 epochDay 列表**）而不是某一种实体：
     *   用户要求「血氧、心率使用跟睡眠一样的查看历史按钮」，三个页面的**日期语义完全一样**，
     *   差别只有"那天有数据吗"和"圆底什么颜色"。按实体各写一份日历 = 三份会各自漂移的复制品
     *   （改了一份里的"今天恒可点"、另两份没改 —— 这种错不报错、不崩，只是三页行为不一致）。
     *
     * ⚠ [tint] 是**函数**而不是 Map：颜色来源各不相同（睡眠要看那一晚的三星得分），
     *   提前算成 Map 会要求调用方先知道网格里会出现哪些天。
     */
    @OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
    @Composable
    private fun HistoryDateSheet(
        days: List<Long>,
        selected: Long?,
        today: Long,
        tint: (Long) -> Color,
        onPick: (Long) -> Unit,
        onDismiss: () -> Unit,
    ) {
        val daySet = remember(days) { days.toHashSet() }
        val dayFmt = rememberDayFmt()
        val monthFmt = rememberMonthFmt()
        val wdFmt = rememberWeekdayFmt()

        val todayDate = java.time.LocalDate.ofEpochDay(today)
        val anchor = java.time.LocalDate.ofEpochDay(selected ?: today)
        var ym by remember(selected, today) {
            mutableStateOf(java.time.YearMonth.of(anchor.year, anchor.monthValue))
        }
        val minYm = remember(days, today) {
            val earliest = days.minOfOrNull { it } ?: today
            java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(earliest))
        }
        val maxYm = java.time.YearMonth.from(todayDate)

        ModalBottomSheet(onDismissRequest = onDismiss) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .padding(bottom = 28.dp),
            ) {
                // ── 月份头：‹ 2026年10月 › ──
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MonthArrow("‹", enabled = ym.isAfter(minYm)) { ym = ym.minusMonths(1) }
                    Text(
                        monthFmt.format(Date(epochDayToMs(ym.atDay(1).toEpochDay()))),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    )
                    MonthArrow("›", enabled = ym.isBefore(maxYm)) { ym = ym.plusMonths(1) }
                }
                Spacer(Modifier.height(6.dp))

                // ── 星期头（周一起，与三星一致）──
                Row(Modifier.fillMaxWidth()) {
                    for (c in 0 until 7) {
                        Text(
                            wdFmt.format(Date(epochDayToMs(MONDAY_REF_EPOCH_DAY + c))),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                // ── 日期格 ──
                val first = ym.atDay(1)
                val lead = first.dayOfWeek.value - 1          // 周一 = 0
                val len = first.lengthOfMonth()
                val rows = (lead + len + 6) / 7
                for (r in 0 until rows) {
                    Row(Modifier.fillMaxWidth()) {
                        for (c in 0 until 7) {
                            val dnum = r * 7 + c - lead + 1
                            Box(
                                Modifier.weight(1f).height(46.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (dnum < 1 || dnum > len) return@Box
                                val day = first.withDayOfMonth(dnum).toEpochDay()
                                val hasData = day in daySet
                                val isToday = day == today
                                val isSel = day == selected
                                val enabled = hasData || isToday
                                val bg = when {
                                    isSel -> AppTextPrimary
                                    hasData -> tint(day)
                                    isToday -> SleepDayTodayEmptyColor
                                    else -> null
                                }
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .then(
                                            if (bg != null) Modifier.background(bg, CircleShape)
                                            else Modifier
                                        )
                                        .clickable(enabled = enabled) { onPick(day) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        dayFmt.format(Date(epochDayToMs(day))),
                                        fontSize = 13.sp,
                                        color = when {
                                            isSel -> Color.White
                                            enabled -> AppTextPrimary
                                            else -> Color(0xFFBDBDBD)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** 月份头两边的翻页箭头。`enabled = false` 时置灰、不可点。 */
    @Composable
    private fun MonthArrow(symbol: String, enabled: Boolean, onClick: () -> Unit) {
        Box(
            Modifier
                .size(40.dp)
                .clickable(enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                symbol,
                fontSize = 22.sp,
                color = if (enabled) AppTextPrimary else Color(0xFFD0D0D0),
            )
        }
    }

    // ═══════════════════ 历史睡眠（2026-10-10 新增）═══════════════════

    /**
     * 短日期格式器（`10月9日` / `Oct 9` 这种）。
     *
     * ⚠⚠ 用**当前 Context 的 locale**（被 `LanguageStore.wrap` 套过的那一个），
     *   而**不是** `Locale.getDefault()`：本应用切语言走的是 `attachBaseContext + recreate`
     *   （见 `LanguageStore` 的类注释），**不会**改 `Locale.getDefault()` ⇒
     *   用 `getDefault()` 会永远按**系统**语言格式化，界面切成英文后日期还写着"10月9日"。
     *   （`StaleDataBanner` 与睡眠主卡里那两处 `SimpleDateFormat("M月d日")` 有同样的问题；
     *    本轮只保证**新增的**这几处是对的。）
     *
     * ⚠ `remember(lang)` 的键必须带语言：否则切完语言，已经渲染过的日期不会重算。
     */
    @Composable
    private fun rememberShortDateFmt(): SimpleDateFormat {
        val ctx = LocalContext.current
        val lang = rememberLang()
        return remember(lang) {
            val loc = ctx.resources.configuration.locales[0]
            // `getBestDateTimePattern` 让系统给出该语言惯用的"月+日"顺序与写法
            // （中文 `M月d日`、英文 `MMM d`），比自己拼格式串可靠。
            SimpleDateFormat(
                android.text.format.DateFormat.getBestDateTimePattern(loc, "MMMd"),
                loc,
            )
        }
    }

    /** `epochDay` → 当天 0 点的 epoch 毫秒（格式化日期用）。 */
    private fun epochDayToMs(epochDay: Long): Long =
        java.time.LocalDate.ofEpochDay(epochDay)
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()

    /**
     * epoch 毫秒 → **本地**日期的 `epochDay`（心率/血氧这类点测记录按日归组用）。
     *
     * ⚠ 必须走 `ZoneId.systemDefault()`（本机时区）而不是 UTC：`epochDay` 在项目里到处
     *   被当作"用户眼中的哪一天"（`todayStartMs()` 同一条口径）。用 UTC 的话，
     *   东八区凌晨 0-8 点的记录会被归到**前一天**，而界面上写着"今天"。
     */
    private fun msToEpochDay(ms: Long): Long =
        java.time.Instant.ofEpochMilli(ms)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /**
     * 近若干晚的睡眠**时长**柱：深色 = 实际睡眠，浅色 = 中间醒着的那一段。
     * 两段相加 = 入睡→起床的总时长。点某根柱子看那一晚的详情。
     *
     * ## 两条刻意的设计
     * - **纵轴不自适应**（不把窗口内的最小值拉到底）：那样"7h10 与 7h05"会被画成一高一矮
     *   两个极端，看着像睡眠时长剧烈波动。改为按"窗口内最长的那一晚"向上取整到整点，
     *   并设一个 **6 小时的地板** —— 短睡眠的柱子本来就该显得矮。
     * - **日期标签每 3 根标一个**：14 个 `10/9` 挤在一行会连成一片，
     *   只在首/每 3 根/最后一根上标（与 `HourRangeChart` 的 `labelEvery` 同一个做法）。
     */
    @Composable
    private fun SleepDurationTrend(nights: List<SleepNightEntity>, onOpen: (Long) -> Unit) {
        if (nights.isEmpty()) return
        val ordered = remember(nights) { nights.sortedBy { it.dateEpochDay } }
        val maxMs = remember(ordered) {
            val peak = ordered.maxOf { (it.endMs - it.startMs).coerceAtLeast(0L) }
            val floored = maxOf(peak, 6 * 3_600_000L)
            ((floored + 3_599_999L) / 3_600_000L) * 3_600_000L
        }
        // ⚠⚠ 横轴**只写「日」**（2026-10-16）：原来用 `rememberShortDateFmt()`（`M月d日`），
        //   在 ~21dp 宽的格子里放不下 —— 用户截图里 14 个标签全是「10月」，等于没有横轴。
        //   月份由卡片副标题（"最近 14 晚"）与下面的逐晚记录提供。
        //   ⚠ 与 `DeepTrendChart` 早先踩的是同一个坑，那边已改成只写日。
        val dayFmt = rememberDayFmt()
        Row(
            Modifier.fillMaxWidth().height(118.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            ordered.forEachIndexed { i, n ->
                val total = (n.endMs - n.startMs).coerceAtLeast(0L)
                val actual = n.actualSleepMs.coerceIn(0L, total)
                val totalFrac = (total.toFloat() / maxMs).coerceIn(0.02f, 1f)
                val actualFrac = (actual.toFloat() / maxMs).coerceIn(0f, totalFrac)
                Column(
                    Modifier.weight(1f).fillMaxHeight().clickable { onOpen(n.dateEpochDay) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ⚠⚠ 每根柱子上方标**那一晚的总时长**（2026-10-15 用户反馈：
                    //   「这个睡眠时长趋势卡片里没有数据，只有一个图」）。
                    //   这一张卡原来只给形状：14 根颜色相近的柱子 + 每 3 根一个 8sp 的日期，
                    //   用户读不出任何数 —— 看着就是"一张图"。
                    //   ⚠ 用 [fmtClock] 的 `h:mm` 短格式（`6:36`）：每根柱约 23dp，
                    //     8sp 放得下；`fmtDuration`（"6 小时 36 分钟"）在这里必然被截断。
                    Text(
                        fmtClock(total),
                        fontSize = 8.sp, color = Color(0xFF6B6B6B), maxLines = 1,
                    )
                    Spacer(Modifier.height(2.dp))
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        // 先画浅的（总时长 = 实际睡眠 + 中间清醒），再把深的叠在底部
                        Box(
                            Modifier.fillMaxWidth(0.66f).fillMaxHeight(totalFrac)
                                .background(
                                    SleepAwakeColor,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                )
                        )
                        Box(
                            Modifier.fillMaxWidth(0.66f).fillMaxHeight(actualFrac)
                                .background(
                                    SleepActualColor,
                                    RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp),
                                )
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    val show = i % 3 == 0 || i == ordered.lastIndex
                    Text(
                        if (show) dayFmt.format(Date(epochDayToMs(n.dateEpochDay))) else " ",
                        fontSize = 8.sp, color = Color(0xFF9C8CB0), maxLines = 1,
                    )
                }
            }
        }
    }

    /**
     * 「逐晚记录」里的一行。
     *
     * ⚠ 时长走 [fmtDuration]（多语言，`5 小时 37 分钟` 这种），**不自己拼**
     *   `7h42m` —— 那种写法在中文界面上很突兀，而且"分钟"两个字也要跟着语言变。
     */
    @Composable
    private fun SleepHistoryRow(n: SleepNightEntity, onClick: () -> Unit) {
        val dateFmt = rememberShortDateFmt()
        val total = (n.endMs - n.startMs).coerceAtLeast(0L)
        Row(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(dateFmt.format(Date(epochDayToMs(n.dateEpochDay))), fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.s_15412911, fmtDuration(n.actualSleepMs)),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtDuration(total), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                n.samsungScore?.let {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.s_3443773a, it.toString()),
                        fontSize = 11.sp, color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text("›", fontSize = 18.sp, color = AppTextMuted)
        }
    }

    /**
     * 某一**历史晚**的详情页（入口：睡眠页「逐晚记录」的某一行）。
     *
     * ## 这一页为什么不读三星健康
     * 睡眠期**逐分钟**的血氧/心率序列**刻意不落库**（一晚几百个点会把「最近记录」刷屏），
     * 所以历史晚拿不到序列 —— 能讲的只有 `sleep_nights` 那一行里的**统计值**与**阶段段**。
     * 换来的是：点进去**瞬时**、离线/未授权也能看
     * （与 v116 那条「库里有的，界面必须能显示」同源）。
     *
     * ⚠ **不显示"最高心率"**：这张表**没有 `hrMax` 列**（见 `SleepNightEntity` 的 KDoc），
     *   拿 `hrAvg` 顶会凭空造出一个"最高心率"。这是 v116 桥接那条纪律的同一条。
     */
    @Composable
    private fun SleepNightHistoryScreen(
        entity: SleepNightEntity,
        onBack: () -> Unit,
        /** 库里所有的晚（日历用它标出"哪些天有数据"）。 */
        nights: List<SleepNightEntity>,
        /** 在日历上选了某一天。⚠ 选到"今天"由调用方决定回哪一页（这一页不是"今天"）。 */
        onPickDay: (Long) -> Unit,
    ) {
        val night = remember(entity) { entity.toSleepNightDto() }
        val dateFmt = rememberShortDateFmt()
        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        var showDateSheet by remember(entity.dateEpochDay) { mutableStateOf(false) }
        val todayEpoch = remember { java.time.LocalDate.now().toEpochDay() }

        // ── 现读**那一晚**的心率/血氧序列（2026-10-13）──
        //
        // 用户：「历史睡眠里的数据卡片怎么没有…所有的历史卡片都要跟今天的睡眠里所展示的
        // 卡片内容一样啊」。今日睡眠页有两张图（睡眠期血氧 / 睡眠心率），这一页原来只有
        // 一张文字统计卡 —— 因为**逐分钟序列不落库**。
        //
        // ⚠ 但那不等于"拿不到"：三星健康那边一直存着（`syncSleepDays` 的历史回填就是
        //   用同两个读取函数算出的 hrAvg/spo2Avg）。⇒ 这一页改成**按那一晚的窗口现读**，
        //   读到就画与今日页同款的图；读不到才退回文字统计（并说清原因）。
        // ⚠ 现读的读数**不落库**（理由同 `readDayVitals`：一晚 400+ 条会把列表刷屏）。
        var remote by remember(entity.startMs, entity.endMs) {
            mutableStateOf<DayRemote>(DayRemote.Loading)
        }
        LaunchedEffect(entity.startMs, entity.endMs) {
            remote = DayRemote.Loading
            remote = when (
                val r = SamsungHealthReader.readWindowVitals(
                    this@MainActivity, entity.startMs, entity.endMs,
                )
            ) {
                is SamsungHealthReader.DayVitals.Ok -> DayRemote.Ok(r.hr, r.spo2)
                SamsungHealthReader.DayVitals.Unreadable -> DayRemote.Unreadable
            }
        }
        // 按"入睡→起床的每一个整点"分桶（与今日页**同一个** `bucketBySleepHours`）：
        // ⚠ 不能按"当天 0 点"分桶 —— 睡眠跨午夜时 23 点那一格会被静默丢掉（见其 KDoc）。
        val remoteOk = remote as? DayRemote.Ok
        val spo2Buckets = bucketBySleepHours(remoteOk?.spo2 ?: emptyList(), entity.startMs, entity.endMs)
        val hrBuckets = bucketBySleepHours(remoteOk?.hr ?: emptyList(), entity.startMs, entity.endMs)
        val spo2Sleep = spo2Buckets.first
        val hrSleepB = hrBuckets.first

        if (showDateSheet) {
            HistoryDateSheet(
                days = remember(nights) { nights.map { it.dateEpochDay } },
                selected = entity.dateEpochDay,
                today = todayEpoch,
                tint = sleepDayTint(nights),
                onPick = { day ->
                    showDateSheet = false
                    // ⚠ 选了同一晚就不动（否则会用同一个 route 重新进一次，白重排一遍页面）
                    if (day != entity.dateEpochDay) onPickDay(day)
                },
                onDismiss = { showDateSheet = false },
            )
        }

        Column(Modifier.fillMaxSize()) {
            // ── 头部：返回 + **日期本身就是切换入口**（点它开月历）──
            //
            // ⚠ 这一页**不用 `DetailHeader`**：它的标题本来就是那一晚的日期，
            //   再挂一个日期胶囊就是同一件事写两遍。所以直接把标题做成胶囊
            //   （胶囊自带 `▾`，看得出能点）。这样这一页也和其他页一样：
            //   **没有任何横带**，内容区的顶边就是这一行的下沿。
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppOutlineButton(onClick = onBack) { Text(stringResource(R.string.s_51a8f8fd)) }
                Spacer(Modifier.width(12.dp))
                HistoryDatePill(dateFmt.format(Date(epochDayToMs(entity.dateEpochDay)))) {
                    showDateSheet = true
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 概览：与睡眠页主卡**同一套口径与排版** ──
                // 同一份数据在两个地方长得不一样，对着看会以为哪边算错了。
                item {
                    AppSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            AppKpi(
                                value = fmtDuration(night.actualSleepMs()),
                                unit = stringResource(R.string.s_24f6d380),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "${tsFmt.format(Date(night.startMs))} - ${tsFmt.format(Date(night.endMs))}" +
                                    stringResource(R.string.s_2d3d3088, fmtDuration(night.durationMs)),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                            night.score?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_3443773a, it.toString()),
                                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                // ── 睡眠阶段（结构与睡眠页那张阶段卡一致）──
                //
                // ⚠ `stagesJson` 为 NULL（v21 加列**之前**存的行）时 `toSleepNightDto()`
                //   会给出**空列表**。那时**不能**照画 —— `Hypnogram` 空跑会画出一张
                //   **只有网格、没有任何阶段**的图，看着像"那晚几乎没睡"。
                //   这里必须明说"那晚没存过"（与 v116 那条「NULL ≠ 那晚没有阶段」同源）。
                item {
                    AppCard(title = stringResource(R.string.s_1e914e32)) {
                        Column {
                            Spacer(Modifier.height(10.dp))
                            if (night.stages.isEmpty()) {
                                Text(
                                    stringResource(R.string.s_9c69cc36),
                                    fontSize = 13.sp, color = AppTextMuted,
                                )
                            } else {
                                Hypnogram(night.stages, night.startMs, night.endMs, 120.dp)
                                Spacer(Modifier.height(2.dp))
                                TimeAxisRow(night.startMs, night.endMs)
                                Spacer(Modifier.height(12.dp))
                                StageBreakdown(night, showTypicalText = false)
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).background(Color(0x55FFFFFF)))
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        stringResource(R.string.s_cddb1820),
                                        fontSize = 11.sp, color = AppTextMuted,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 睡眠期血氧（只画睡眠小时）—— 与今日睡眠页同款的图 ──
                item {
                    AppCard(title = stringResource(R.string.s_98155e0f)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            when {
                                remote is DayRemote.Loading -> Text(
                                    stringResource(R.string.s_d78fcd06),
                                    fontSize = 13.sp, color = AppTextMuted,
                                )
                                spo2Sleep.isNotEmpty() -> {
                                    val avg = spo2Sleep.map { it.avg }.average().roundToInt()
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "$avg%",
                                            fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                            color = Spo2SleepColor,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            stringResource(
                                                R.string.s_8fc256c1, dash(spo2Sleep.minOf { it.min })
                                            ),
                                            fontSize = 13.sp, color = AppTextMuted,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    HourRangeChart(
                                        slots = spo2Sleep.map { it.hour },
                                        hours = spo2Sleep.associateBy { it.hour },
                                        bounds = spo2Bounds(spo2Sleep.flatMap { listOf(it.min, it.max) }),
                                        labelEvery = 1,
                                        sleepDiff = false,
                                        awakeColor = Spo2SleepColor,
                                        labelOf = { spo2Buckets.second[it] ?: "$it" },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.s_bd58d147),
                                        fontSize = 10.sp, color = AppTextMuted,
                                    )
                                }
                                remote is DayRemote.Unreadable -> Text(
                                    stringResource(R.string.s_dd13920e),
                                    fontSize = 13.sp, color = Color(0xFFFFA94D),
                                )
                                else -> Text(
                                    stringResource(R.string.s_7ced89b1),
                                    fontSize = 13.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // ── 睡眠心率（只画睡眠小时）—— 与今日睡眠页同款的图 ──
                item {
                    AppCard(title = stringResource(R.string.s_1e75792b)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            when {
                                remote is DayRemote.Loading -> Text(
                                    stringResource(R.string.s_d78fcd06),
                                    fontSize = 13.sp, color = AppTextMuted,
                                )
                                hrSleepB.isNotEmpty() -> {
                                    val avg = hrSleepB.map { it.avg }.average().roundToInt()
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "$avg",
                                            fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                            color = HrSleepColor,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.s_1c0dd13c),
                                            fontSize = 13.sp, color = AppTextMuted,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        )
                                        Spacer(Modifier.weight(1f))
                                        Text(
                                            stringResource(
                                                R.string.s_133e3ed8,
                                                dash(hrSleepB.minOf { it.min }),
                                                dash(hrSleepB.maxOf { it.max }),
                                            ),
                                            fontSize = 12.sp, color = AppTextMuted,
                                            modifier = Modifier.padding(bottom = 6.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    HourRangeChart(
                                        slots = hrSleepB.map { it.hour },
                                        hours = hrSleepB.associateBy { it.hour },
                                        bounds = chartBounds(hrSleepB.flatMap { listOf(it.min, it.max) }),
                                        labelEvery = 1,
                                        sleepDiff = false,
                                        // ⚠ 睡眠时段里**全部**柱子都用睡眠色（今日页那张卡也是这么做的）
                                        awakeColor = HrSleepColor,
                                        labelOf = { hrBuckets.second[it] ?: "$it" },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        stringResource(R.string.s_b2c27402),
                                        fontSize = 10.sp, color = AppTextMuted,
                                    )
                                }
                                remote is DayRemote.Unreadable -> Text(
                                    stringResource(R.string.s_dd13920e),
                                    fontSize = 13.sp, color = Color(0xFFFFA94D),
                                )
                                else -> Text(
                                    stringResource(R.string.s_11fbcf62),
                                    fontSize = 13.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // ── 那一晚的统计值 ──
                // ⚠ 每一项**只在有值时才显示**：三星健康可能只开了睡眠而没开血氧/心率
                //   连续监测，拿 0 顶会让"没测"变成一个真实的测量值（见 `SleepNightEntity`）。
                item {
                    AppCard(title = stringResource(R.string.s_9810b0e6)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            entity.hrAvg?.let {
                                StatLine(stringResource(R.string.s_82cbe300, it))
                            }
                            entity.hrMin?.let {
                                StatLine(stringResource(R.string.s_cdf8b91d, it))
                            }
                            // `hrSd` 只在睡得够久（≥60 个采样点）时才算得出来 ⇒ 常常是 null
                            entity.hrSd?.let {
                                StatLine(
                                    stringResource(
                                        R.string.s_b4c804ab,
                                        String.format(Locale.US, "%.1f", it),
                                    )
                                )
                            }
                            if (entity.spo2Avg != null || entity.spo2Min != null) {
                                StatLine(
                                    stringResource(
                                        R.string.s_87785628,
                                        (entity.spo2Avg ?: 0).toString(),
                                        dash(entity.spo2Min),
                                    )
                                )
                            }
                            entity.skinTemp?.let {
                                StatLine(
                                    stringResource(
                                        R.string.s_71e30dce,
                                        String.format(Locale.US, "%.1f", it),
                                    )
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        stringResource(R.string.s_3126c4fa),
                        fontSize = 11.sp, color = AppTextMuted,
                    )
                }
            }
        }
    }

    /** 统计卡里的一行（11sp 灰字，逐条排下来）。 */
    @Composable
    private fun StatLine(text: String) {
        Text(
            text,
            fontSize = 12.sp,
            modifier = Modifier.padding(vertical = 3.dp).fillMaxWidth(),
        )
    }

    /**
     * 「本晚读不到」时的整页（2026-10-11 重写）。
     *
     * ⚠⚠ 它**不再是一个只有一段说明的页面**：说明下面必须跟着**库里的历史睡眠**。
     *   用户 2026-10-11 报的原话是"昨天的明明同步了可以在软件里看到啊"——
     *   三星健康一读不到（连不上 / 未授权 / 服务被回收），原来的整页 early return
     *   会把 `sleep_nights` 里那十几晚**一起藏起来**，看起来就像数据全丢了；
     *   而那几晚一直都在库里（`syncSleepDays` 还会往回补 14 晚）。
     *
     * 判据（v116 起的不变量）：**同步失败只影响「能不能更新」，不影响「能不能看」**。
     * ⇒ 空态与正常页**共用同一份** [sleepHistorySection]。
     */
    @Composable
    private fun SleepEmptyScreen(
        samsungGranted: Boolean?,
        unreadable: Boolean,
        samsungSleepOk: Boolean?,
        onRefresh: () -> Unit,
        onAuth: suspend () -> Unit,
        history: List<SleepNightEntity>,
        onOpenNight: (Long) -> Unit,
    ) {
        // ⚠⚠ 这一页里**没有日期条**（2026-10-12）：它挂在标题行（`DetailHeader` 的
        //   `trailing`）**外面**，所以这里连「读不到数据画不画」的问题都不存在 ——
        //   空态下照样点得开。
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SleepEmptyHint(
                    samsungGranted = samsungGranted,
                    unreadable = unreadable,
                    samsungSleepOk = samsungSleepOk,
                    onRefresh = onRefresh,
                    onAuth = onAuth,
                )
            }
            // ⚠ 这一句是整页存在的理由：读不到今天 ≠ 看不到以前。
            sleepHistorySection(history, onOpenNight)
        }
    }

    /**
     * 睡眠数据缺失时的说明：把「连不上」「没授权」「三星健康里就是没有」分开讲清楚。
     *
     * ⚠ 这三种原因的**动作是不一样的**，所以不能共用一句话：
     *   · 连不上   → 重试（重新授权同样连不上，只会把人带进沟里）
     *   · 未授权   → 去授权
     *   · 确实没有 → 什么都不用做，检查手表有没有戴着睡
     */
    @Composable
    private fun SleepEmptyHint(
        samsungGranted: Boolean?,
        unreadable: Boolean,
        samsungSleepOk: Boolean?,
        onRefresh: () -> Unit,
        onAuth: suspend () -> Unit,
    ) {
        val hintScope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CollapsibleNotes(
                            listOf(
                            when {
                            // ⚠⚠ 「问不出来」排在最前：它**不许**被说成"未授权"。
                            //   说成未授权，用户就会去点"重新授权"—— 那同样连不上，
                            //   于是他只会反复点、每次都被弹回来，最后以为软件坏了。
                            unreadable ->
                            // ⚠ 与心率页/血氧页**同一句**（2026-11-03）：先陈述"连接失败"，
                            //   再说该怎么做。三页口径必须一致，见 check_samsung_read_state。
                            stringResource(R.string.s_861b900f) + "\n" +
                            stringResource(R.string.s_ed232184)
                            samsungGranted != true ->
                            stringResource(R.string.s_52a332f3) +
                            stringResource(R.string.s_abe29abf)
                            samsungSleepOk != true ->
                            stringResource(R.string.s_53e0f3c0) +
                            stringResource(R.string.s_2879b702)
                            else ->
                            stringResource(R.string.s_0dfc3e45) +
                            stringResource(R.string.s_79b5baf8)
                            },
                            )
                        )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppPrimaryButton(onClick = onRefresh) { Text(stringResource(R.string.s_a4558896)) }
                // 「重新读取」解不了"授权丢了"——重读只是再读一遍，权限不会自己回来。
                // ⇒ 缺权限（或问不出来）时**必须**再给一个真能动手的入口，否则这一页
                //   只有"重读"一个按钮，用户点十次也不会有任何变化。
                if (unreadable || samsungGranted != true) {
                    AppOutlineButton(
                        onClick = {
                            hintScope.launch {
                                busy = true
                                runCatching { onAuth() }
                                busy = false
                            }
                        },
                        enabled = !busy,
                    ) { Text(stringResource(R.string.s_1620386e)) }
                }
            }
        }
    }

    /** 一格的睡眠得分因素：上名称、中时长、下评价标签。 */
    private data class SleepFactor(val name: String, val value: String, val label: Typical?)

    @Composable
    private fun SleepFactorRow(items: List<SleepFactor>) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (f in items) {
                AppTile(Modifier.weight(1f)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(f.name, fontSize = 12.sp, color = AppTextMuted)
                        Spacer(Modifier.height(4.dp))
                        Text(f.value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        f.label?.let { tp ->
                            Spacer(Modifier.height(6.dp))
                            Text(
                                tp.label(),
                                fontSize = 10.sp,
                                color = Color.White,
                                modifier = Modifier
                                    .background(
                                        // ⚠ 判据用**枚举身份**，不是文案 —— 改造前是
                                        // `if (it == "范围内")`，翻译后永远走 else（见 [Typical]）。
                                        if (tp == Typical.IN_RANGE) Color(0xFF6BCB77)
                                        else Color(0xFFFFA94D),
                                        RoundedCornerShape(4.dp),
                                    )
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
            // 单数格时补一个空位，保证两列布局不被拉伸
            if (items.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    /**
     * 实测占比相对"典型范围"的位置（模块 #15）。
     *
     * ⚠ 改造前这里返回的是**中文字符串**（"范围内"/"偏低"/"偏高"），
     * 而调用方拿它 `if (it == "范围内")` 决定标签底色 —— 一翻译，底色判断
     * **永远落在 else 分支**（所有阶段都显示成橙色告警），且不报错。
     * 现在返回枚举，"是不是范围内"由身份判断，颜色不再依赖文案。
     */
    private enum class Typical { BELOW, IN_RANGE, ABOVE }

    /** 实测占比相对"典型范围"的位置。纯机械换算，不含医学判定。 */
    private fun typicalOf(ms: Long, sum: Long, kind: String): Typical? {
        val typical = StageTypical[kind] ?: return null
        if (sum <= 0L) return null
        val pct = ms * 100f / sum
        return when {
            pct < typical.first -> Typical.BELOW
            pct > typical.second -> Typical.ABOVE
            else -> Typical.IN_RANGE
        }
    }

    /** [Typical] 的显示名（随语言变）。 */
    @Composable
    private fun Typical.label(): String = stringResource(
        when (this) {
            Typical.BELOW -> R.string.typical_below
            Typical.IN_RANGE -> R.string.typical_in_range
            Typical.ABOVE -> R.string.typical_above
        }
    )

    /** 睡眠时长标签：低于 6 小时提示偏短，其余视为范围内。 */
    private fun typicalOfDuration(hours: Float): Typical =
        if (hours < 6f) Typical.BELOW else Typical.IN_RANGE

    /**
     * 睡眠图（hypnogram）：横轴时间、纵轴四个阶段层级。
     *
     * 每段画成该层级上的一条圆角胶囊，相邻段之间用竖线连接（跨层级时看得出"掉到深睡"）。
     * 用合并后的连续段而不是原始逐条记录，否则一晚上百段会画成锯齿。
     */
    @Composable
    private fun Hypnogram(
        stages: List<SleepStageDto>,
        startMs: Long,
        endMs: Long,
        height: Dp = 120.dp,
        onClick: (() -> Unit)? = null,
    ) {
        if (endMs <= startMs) return
        val mod = Modifier.fillMaxWidth().height(height)
        Canvas(if (onClick != null) mod.clickable { onClick() } else mod) {
            val span = (endMs - startMs).toFloat().coerceAtLeast(1f)
            val levels = listOf(
                SleepStageKind.AWAKE, SleepStageKind.REM,
                SleepStageKind.LIGHT, SleepStageKind.DEEP,
            )
            val rowH = size.height / levels.size
            val barH = (rowH * 0.54f).coerceAtLeast(2f)
            levels.forEachIndexed { i, _ ->
                val cy = rowH * i + rowH / 2f
                drawLine(
                    AppGridLine,
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
                        Color(0x55FFFFFF),
                        Offset(prevX, prevY), Offset(x0, cy), strokeWidth = 2f,
                    )
                }
                drawRoundRect(
                    color = stageColor(s.kind),
                    topLeft = Offset(x0, cy - barH / 2f),
                    size = Size((x1 - x0).coerceAtLeast(1.5f), barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f),
                )
                prevX = x1
                prevY = cy
            }
        }
    }

    /** 横轴时间刻度：起止之间等距 4 格，共 5 个 HH:mm 标签。 */
    @Composable
    private fun TimeAxisRow(startMs: Long, endMs: Long) {
        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (k in 0..4) {
                Text(
                    tsFmt.format(Date(startMs + (endMs - startMs) * k / 4)),
                    fontSize = 9.sp, color = Color(0xFF9C8CB0),
                )
            }
        }
    }

    /** 四阶段占比（含典型范围斜纹带）。 */
    @Composable
    private fun StageBreakdown(night: SleepNightDto, showTypicalText: Boolean) {
        val totals = night.stageTotals()
        val sum = totals.values.sum().coerceAtLeast(1L)
        Column(Modifier.fillMaxWidth()) {
            for (kind in StageOrder) {
                val ms = totals[kind] ?: 0L
                val pct = ms * 100f / sum
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${stageLabel(kind)} ${pct.roundToInt()}%",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = stageColor(kind),
                        modifier = Modifier.weight(1f),
                    )
                    Text(fmtDuration(ms), fontSize = 12.sp, color = AppTextMuted)
                }
                Spacer(Modifier.height(4.dp))
                StageBar(pct, StageTypical[kind] ?: (0f to 0f), stageColor(kind))
                if (showTypicalText) {
                    val t = StageTypical[kind]
                    if (t != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.s_8e28d2d1, t.first.roundToInt(), t.second.roundToInt()),
                            fontSize = 10.sp, color = AppTextMuted,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }

    /**
     * 单阶段占比条：灰底 + 典型范围斜纹带 + 实测实心填充。
     *
     * 斜纹带用 [clipRect] 裁在带区间内再逐条画 45° 斜线 —— 手动画斜线比引入
     * Brush/PathEffect 更可控，且能严格贴合带的两端。
     */
    @Composable
    private fun StageBar(pct: Float, typical: Pair<Float, Float>, color: Color) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val r = CornerRadius(size.height / 2f, size.height / 2f)
            drawRoundRect(AppDivider, Offset.Zero, size, r)
            val lo = (typical.first / 100f).coerceIn(0f, 1f) * size.width
            val hi = (typical.second / 100f).coerceIn(0f, 1f) * size.width
            if (hi > lo) {
                clipRect(left = lo, top = 0f, right = hi, bottom = size.height) {
                    var x = lo - size.height
                    while (x < hi) {
                        drawLine(
                            Color(0x66FFFFFF),
                            Offset(x, size.height), Offset(x + size.height, 0f),
                            strokeWidth = 1.5f,
                        )
                        x += 4.dp.toPx()
                    }
                }
            }
            val w = (pct / 100f).coerceIn(0f, 1f) * size.width
            if (w > 0f) {
                drawRoundRect(color, Offset.Zero, Size(w.coerceAtLeast(size.height), size.height), r)
            }
        }
    }

    /**
     * 睡眠期血氧：**10 分钟一桶**的最低~最高细柱 + 平均折线 + 90% 参考线。
     * 纵轴固定 70~100%（与三星健康一致）—— 自适应纵轴会把 92~96 这种正常波动
     * 拉成满屏起伏，看着像剧烈波动。
     *
     * ## 为什么要改成"分桶"（2026-10-10，用户："不够直观，重做"）
     * 原实现把**每分钟**的原始序列直接连成一条折线。一晚 8 小时 ≈ 480 个点摊在
     * 三百多像素宽的画面里 ⇒ 相邻点只差 0.7px，"折线"糊成一条毛刺带：
     * 既看不出趋势，也看不出到底哪一段真的低。用户原话是"看着像一团噪点"。
     * 现在与 [SleepHrChart] 用**同一套画法**（那套用户从没提过意见）：
     * 桶内画"最低~最高"的细柱（真实波动范围，**不做平滑**），桶均值连成折线（看趋势）。
     *
     * ## 三条刻意的设计
     * - **不做移动平均**：平滑会把"某一分钟掉到 88%"这种事抹掉，
     *   而"夜里有没有掉下去"正是这一页要回答的问题 ⇒ 柱的"最低"取的是**真实最小值**。
     * - **90% 参考线是一条虚线、不是一块填色**：填色会被读成"这一段有问题"这种**结论**，
     *   而本应用一律不给分级（按同一条纪律）。
     * - **最低点单独标出来**（由调用方在图表下方用文字写"最低 88%（02:14）"）：
     *   否则用户得自己在几十根柱子里找最矮的那根。
     */
    @Composable
    private fun Spo2NightChart(
        points: List<Pair<Long, Int>>,
        startMs: Long,
        endMs: Long,
        height: Dp = 120.dp,
        onClick: (() -> Unit)? = null,
    ) {
        if (points.isEmpty() || endMs <= startMs) return
        val mod = Modifier.fillMaxWidth().height(height)
        val bucketMs = 10 * 60 * 1000L
        Canvas(if (onClick != null) mod.clickable { onClick() } else mod) {
            val lo = 70f
            val hi = 100f
            // 纵向留内边距：数值等于上界（100%）时若直接按比例映射会落到 y=0，
            // 整条线正好压在画布边缘被裁掉，图看着是空的。留出 padY 后贴顶的值也画得出来。
            val padY = 8.dp.toPx()
            val plotH = (size.height - padY * 2).coerceAtLeast(1f)
            fun yOf(v: Float) = padY + plotH * (1f - (v - lo) / (hi - lo))

            // 刻度网格：70/80/90/100（90 那条一会儿会被参考虚线盖成实色）
            for (v in intArrayOf(100, 90, 80, 70)) {
                val y = yOf(v.toFloat())
                drawLine(AppGridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            // 90% 参考虚线（睡眠期血氧的常用关注线）
            val dash = 5.dp.toPx()
            val gap = 4.dp.toPx()
            val y90 = yOf(90f)
            var dx = 0f
            while (dx < size.width) {
                drawLine(
                    Color(0x887E57C2),
                    Offset(dx, y90),
                    Offset((dx + dash).coerceAtMost(size.width), y90),
                    strokeWidth = 1.4f,
                )
                dx += dash + gap
            }

            // 分桶：10 分钟一桶。桶数按窗口算（**不是**按数据点算）——
            // 某一段没采到数据时，图上要留出**空白**，而不是把两段拼在一起
            // （拼起来会把"中间有 40 分钟没监测"画成"血氧一直很平稳"）。
            val buckets = HashMap<Long, MutableList<Int>>()
            for ((t, v) in points) {
                buckets.getOrPut(((t - startMs) / bucketMs).coerceAtLeast(0L)) { mutableListOf() }
                    .add(v)
            }
            val totalBuckets = ((endMs - startMs) / bucketMs).coerceAtLeast(1L)
            val slotW = size.width / totalBuckets.toFloat()
            val barW = (slotW * 0.55f).coerceIn(1f, 5.dp.toPx())
            val avgPath = Path()
            var first = true
            for (i in 0 until totalBuckets) {
                val vals = buckets[i] ?: continue
                val mn = vals.min()
                val mx = vals.max()
                val av = vals.average().toFloat()
                val cx = i * slotW + slotW / 2f
                val yTop = yOf(mx.coerceIn(70, 100).toFloat())
                val yBot = yOf(mn.coerceIn(70, 100).toFloat())
                drawRoundRect(
                    color = Color(0x597E57C2),
                    topLeft = Offset(cx - barW / 2f, yTop),
                    size = Size(barW, (yBot - yTop).coerceAtLeast(barW)),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
                val ay = yOf(av.coerceIn(70f, 100f))
                if (first) {
                    avgPath.moveTo(cx, ay); first = false
                } else {
                    avgPath.lineTo(cx, ay)
                }
            }
            drawPath(avgPath, color = Color(0xFFB79BFF), style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
    }

    /**
     * 睡眠期心率：10 分钟一桶的最低~最高细柱 + 平均折线。
     * 桶宽取 10 分钟是因为整晚原始心率点可达上万条，直接画会糊成一片实心块。
     */
    @Composable
    private fun SleepHrChart(
        points: List<Pair<Long, Int>>,
        startMs: Long,
        endMs: Long,
        height: Dp = 130.dp,
        onClick: (() -> Unit)? = null,
    ) {
        if (points.isEmpty() || endMs <= startMs) return
        val mod = Modifier.fillMaxWidth().height(height)
        val bucketMs = 10 * 60 * 1000L
        Canvas(if (onClick != null) mod.clickable { onClick() } else mod) {
            val buckets = LinkedHashMap<Long, MutableList<Int>>()
            for ((t, v) in points) {
                buckets.getOrPut(((t - startMs) / bucketMs)) { mutableListOf() }.add(v)
            }
            val all = points.map { it.second }
            val lo = (((all.min() - 10) / 10) * 10).coerceAtLeast(20)
            var hi = ((all.max() + 10 + 9) / 10) * 10
            if (hi - lo < 50) hi = lo + 50
            val span = (hi - lo).coerceAtLeast(1)
            for (k in 0..4) {
                val y = size.height * (1f - k / 4f)
                drawLine(AppGridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            val totalBuckets = ((endMs - startMs) / bucketMs).coerceAtLeast(1L)
            val slotW = size.width / totalBuckets.toFloat()
            val barW = (slotW * 0.34f).coerceIn(1f, 4.dp.toPx())
            val avgPath = Path()
            var firstAvg = true
            for (i in 0 until totalBuckets) {
                val vals = buckets[i] ?: continue
                val mn = vals.min()
                val mx = vals.max()
                val av = vals.average()
                val cx = i * slotW + slotW / 2f
                val yHi = size.height * (1f - (mx - lo).toFloat() / span)
                val yLo = size.height * (1f - (mn - lo).toFloat() / span)
                drawRoundRect(
                    color = Color(0x99B0B4C0),
                    topLeft = Offset(cx - barW / 2f, yHi),
                    size = Size(barW, (yLo - yHi).coerceAtLeast(barW)),
                    cornerRadius = CornerRadius(barW / 2f, barW / 2f),
                )
                val ay = size.height * (1f - (av.toFloat() - lo) / span)
                if (firstAvg) {
                    avgPath.moveTo(cx, ay); firstAvg = false
                } else avgPath.lineTo(cx, ay)
            }
            drawPath(avgPath, color = HrSleepColor, style = Stroke(width = 2f, cap = StrokeCap.Round))
        }
    }

    // ═══════════════════════ 睡眠详情页 ═══════════════════════

    /** 睡眠页各图表点进去的详情页。数据直接复用 MainScreen 读好的那一份，不再重复读三星健康。 */
    @Composable
    private fun SleepDetailScreen(
        kind: SleepDetailKind,
        state: SleepUiState,
        loading: Boolean,
        nights: List<SleepNightEntity>,
        onBack: () -> Unit,
        onRefresh: () -> Unit,
    ) {
        Column(Modifier.fillMaxSize()) {
            DetailHeader(stringResource(kind.title), onBack)
            val night = state.night
            if (night == null) {
                Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (loading) stringResource(R.string.s_d78fcd06) else stringResource(R.string.s_acef2eb7),
                        fontSize = 14.sp, color = AppTextMuted,
                    )
                    if (!loading) {
                        Spacer(Modifier.height(12.dp))
                        AppPrimaryButton(onClick = onRefresh) { Text(stringResource(R.string.s_a4558896)) }
                    }
                }
                return@Column
            }
            Box(Modifier.weight(1f)) {
                when (kind) {
                    SleepDetailKind.STAGE -> StageDetail(night, nights)
                    SleepDetailKind.SPO2 -> Spo2Detail(night, state.spo2)
                    SleepDetailKind.HR -> SleepHrDetail(night, state.hr)
                    SleepDetailKind.RESP -> RespDetail()
                }
            }
        }
    }

    /** 睡眠阶段详情：睡眠图 + 分段明细（时间端/时长）+ 每阶段典型范围与解读。 */
    @Composable
    private fun StageDetail(night: SleepNightDto, nights: List<SleepNightEntity> = emptyList()) {
        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        // 趋势图的日期区间（`10月5日 – 10月11日`）用它；图里那一格太窄，只放「日」。
        val shortDateFmt = rememberShortDateFmt()
        val totals = night.stageTotals()
        val sum = totals.values.sum().coerceAtLeast(1L)

        // ── 多晚趋势（2026-10-05 加）──
        //
        // 起因：「睡眠阶段」页原先**只有今晚** —— 阶段段以前不落库（现读三星、窗口只有
        // [昨天18:00, 现在]），过了那晚就取不回来。落库之后才能画这条趋势。
        //
        // ⚠ 只收 `stagesJson` **非空**的晚（`sleepStageHistory` 已经这么做了）：
        //   补列之前的历史全是 NULL，把它们当成"占比 0"会把趋势线拽出一个假的零点。
        //   ⇒ 这里额外**报出样本晚数**，让用户知道那条线是基于几晚画的。
        //
        // ⚠ `remember` 的键必须含 `nights.size` 与最新日期：否则同步补进新的一晚后，
        //   趋势不会重算（列表实例变了但键没变时 Compose 会跳过）。
        val stageKey = nights.size to (nights.maxByOrNull { it.dateEpochDay }?.dateEpochDay ?: 0L)
        val history = remember(nights, stageKey) {
            sleepStageHistory(nights).takeLast(STAGE_TREND_NIGHTS)
        }
        // 每晚的「深睡占比」—— 睡眠质量最常被拿来横向比较的一个数。
        // ⚠ 分母用**四类之和**（不含 unknown），与 `stageTotals` 同口径；
        //   未知段既不算进分子也不算进分母，否则占比会随"分类失败的多少"漂移。
        val deepPctSeries = history.map { (day, totals) ->
            val total = totals.values.sum().coerceAtLeast(1L)
            val deep = totals[SleepStageKind.DEEP] ?: 0L
            day to (deep * 100f / total)
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(title = stringResource(R.string.s_1e914e32)) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.s_0f2321aa) +
                                stringResource(R.string.s_13a33585),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                        Spacer(Modifier.height(10.dp))
                        Hypnogram(night.stages, night.startMs, night.endMs, 150.dp)
                        Spacer(Modifier.height(2.dp))
                        TimeAxisRow(night.startMs, night.endMs)
                    }
                }
            }

            // ── 深睡占比的多晚趋势 ──
            //
            // ⚠ **只有 ≥2 晚才画**：一晚的"趋势"是一个点，画出来只会让人
            //   以为趋势很平稳。样本不足时**整块不出现**（而不是画一条直线）。
            if (deepPctSeries.size >= 2) {
                item {
                    AppCard(title = stringResource(R.string.s_eaa81c68)) {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                // ⚠ 说清样本晚数**与日期区间**：一条基于 3 晚的线与基于 30 晚的线
                                //   看起来一样，但可信度完全不同；而横轴为了塞得下只写了「日」，
                                //   月份只能在这里给（2026-10-12 用户报"不知道是哪天的"）。
                                stringResource(
                                    R.string.s_21ef48f8,
                                    deepPctSeries.size,
                                    shortDateFmt.format(Date(epochDayToMs(deepPctSeries.first().first))),
                                    shortDateFmt.format(Date(epochDayToMs(deepPctSeries.last().first))),
                                ),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            DeepTrendChart(deepPctSeries)
                        }
                    }
                }
            }
            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        StageBreakdown(night, showTypicalText = true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(12.dp).background(Color(0x55FFFFFF)))
                            Spacer(Modifier.width(5.dp))
                            Text(stringResource(R.string.s_2c6ea6be), fontSize = 11.sp, color = AppTextMuted)
                        }
                    }
                }
            }
            items(StageOrder) { kind ->
                val ms = totals[kind] ?: 0L
                val pct = ms * 100f / sum
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "${stageLabel(kind)} ${fmtDuration(ms)}",
                            fontWeight = FontWeight.Bold, color = stageColor(kind),
                        )
                        Spacer(Modifier.height(6.dp))
                        StageBar(pct, StageTypical[kind] ?: (0f to 0f), stageColor(kind))
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${pct.roundToInt()}%" +
                                (StageTypical[kind]?.let { stringResource(R.string.s_58d044d6, it.first.roundToInt(), it.second.roundToInt()) } ?: ""),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(stageAdvice(kind), fontSize = 12.sp)
                    }
                }
            }
            item {
                AppCard(title = stringResource(R.string.s_14469863)) {
                    Column {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.s_e733d6bb),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                        Spacer(Modifier.height(8.dp))
                        for (s in night.stages) {
                            if (s.kind == SleepStageKind.UNKNOWN) continue
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Box(
                                    Modifier.padding(top = 4.dp).size(9.dp)
                                        .background(stageColor(s.kind), RoundedCornerShape(5.dp)),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "${tsFmt.format(Date(s.startMs))} - ${tsFmt.format(Date(s.endMs))}",
                                    fontSize = 12.sp, modifier = Modifier.width(108.dp),
                                )
                                Text(
                                    stageLabel(s.kind),
                                    fontSize = 12.sp, color = AppTextMuted,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(fmtDuration(s.durationMs), fontSize = 12.sp, color = AppTextMuted)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.s_7d8779a8) +
                        stringResource(R.string.s_ad05ca73),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
        }
    }

    /**
     * 深睡占比的**多晚趋势**折线（2026-10-05 加）。
     *
     * ## x 轴是"晚的序号"而不是时间戳
     *
     * ⚠ 与其他几张图（`Spo2NightChart` / `SleepHrChart`）**不同**：那几张的 x 是
     * **同晚内**的时刻（所以按毫秒比例铺开），而这一个是**跨晚**的。
     * 用时间戳会有一个陷阱：中间某晚没数据（手表没戴）时，那一段会被拉成很长的平线，
     * 看起来像"深睡占比连续几晚都一样"，而实际上是**那几晚没数据**。
     * ⇒ 按序号等距排布，**缺的那晚直接不出现**（线的跨度自然收窄）。
     * 这与项目里「没有 ≠ 0」是同一条纪律的图形版本。
     *
     * ## 纵轴范围
     *
     * 固定 0~50%，不做自适应：深睡占比的**绝对水平**才是有意义的
     * （成年人的常见区间大致是 13~23%）。自适应会让"从 18% 变到 19%"看起来
     * 像一次剧烈波动，而那是**视觉放大**造出来的假信号。
     * 超出 50% 的值夹到上界，不会画到画布外。
     *
     * @param series `(dateEpochDay, 深睡占比百分数)`，**按日期升序**。
     */
    @Composable
    private fun DeepTrendChart(series: List<Pair<Long, Float>>) {
        if (series.size < 2) return
        // ⚠⚠ 横轴日期（2026-10-12 加）。用户报："这个深睡占比趋势下面没有时间显示，
        //   不知道是哪天的" —— 一条 14 个点的折线，没有横轴就等于只给了一个形状。
        //
        // 两个做法上的选择：
        //   ① **每格只写「日」**（`getBestDateTimePattern(loc, "d")`），不写 `10月9日`
        //      —— 一格只有 ~24dp，四五个汉字会被裁成「10月」（`Text` 默认 Clip，不报错、
        //      只是看着像乱码）。月份由上面的说明行提供。
        //   ② **点的横坐标改成「槽位中心」**（`(i + 0.5) * width / n`），而不是
        //      `i * width / (n-1)`：只有这样，下面那排等宽 `weight(1f)` 的日期格子
        //      才和上面的点**逐一对齐**。首末点也因此不再压在画布边缘上。
        val dayFmt = rememberDayFmt()
        Column(Modifier.fillMaxWidth()) {
            Canvas(Modifier.fillMaxWidth().height(110.dp)) {
                val lo = 0f
                val hi = 50f
                // 与 `Spo2NightChart` 同样的理由：留纵向内边距，否则贴顶/贴底的点会压在
                // 画布边缘上被裁掉一半。
                val padY = 8.dp.toPx()
                val plotH = (size.height - padY * 2).coerceAtLeast(1f)
                fun yOf(v: Float) = padY + plotH * (1f - (v - lo) / (hi - lo))

                // 参考线：0 / 25 / 50%。**不画"典型区间"带** —— 那需要给一个医学口径，
                // 而不同来源的区间差别不小，画上去等于我们替用户选了一套标准。
                for (v in floatArrayOf(0f, 25f, 50f)) {
                    val y = yOf(v)
                    drawLine(AppGridLine, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                }

                val n = series.size
                val slot = size.width / n
                fun xOf(i: Int) = (i + 0.5f) * slot

                val path = Path()
                series.forEachIndexed { i, (_, pct) ->
                    val x = xOf(i)
                    val y = yOf(pct.coerceIn(lo, hi))
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = Color(0xFF64A8FF), style = Stroke(width = 1.8f))
                // 每个点画一个小实心圆：只有线的话，**单点缺数据造成的断点**看不出来，
                // 而"哪些晚有数据"正是这张图最该让人看清的信息。
                series.forEachIndexed { i, (_, pct) ->
                    drawCircle(
                        color = Color(0xFF64A8FF),
                        radius = 2.5f,
                        center = Offset(xOf(i), yOf(pct.coerceIn(lo, hi))),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            // 日期横轴：**等宽一格一个**，与上面点的槽位一一对应。
            // ⚠ 每 3 个显示一个（与 `SleepDurationTrend` 同一个节奏）：14 个「日」挤在一起
            //   会连成一条带子，反而看不清。空格用的是普通空格，保住每格的宽度。
            Row(Modifier.fillMaxWidth()) {
                series.forEachIndexed { i, (day, _) ->
                    val show = i % 3 == 0 || i == series.lastIndex
                    Text(
                        if (show) dayFmt.format(Date(epochDayToMs(day))) else " ",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 9.sp, color = Color(0xFF9C8CB0), maxLines = 1,
                    )
                }
            }
        }
    }

    /**
     * 腕温**点检偏差**折线（2026-10-15）：`(时刻, 与个人基线的差℃)`。
     *
     * ## 为什么不复用现成的那几张
     *
     * - [Spo2NightChart] / [SleepHrChart] 的纵轴是**写死**的（血氧 70~100、心率若干区间），
     *   温度的偏差可能是 ±1.5℃ 这种小数，写死会把整条线压成一条平线；
     * - [DeepTrendChart] 的横轴是"晚的序号"（跨晚），而这里是**同一天内的时刻**。
     *
     * ⇒ 新写一个，但画法约定与那几张**逐条对齐**（不然同一 App 里几张图长得不像一套）：
     *   纵向留 `padY`（贴顶/贴底的值不被裁）· 网格线用同一种淡色 ·
     *   **基线是一条虚线**（虚线表示"参考"，实线会被读成"又一条数据"）。
     *
     * ## 纵轴为什么自适应、还强制包含 0
     *
     * 偏差本身可正可负，**0 必须出现在画面里**（它就是基线，是这张图的参照物）。
     * 上下界再各留 20% 余量，否则全天只差 0.1℃ 时那条线会贴在基线上看不出分开。
     *
     * @param points `(measuredAt, 偏差℃)`，**按时间升序**（调用方已排好）。
     */
    @Composable
    private fun TempDeviationChart(points: List<Pair<Long, Float>>) {
        if (points.size < 2) return
        val pts = remember(points) { points.sortedBy { it.first } }
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val vals = pts.map { it.second }
            var lo = minOf(vals.min(), 0f)
            var hi = maxOf(vals.max(), 0f)
            if (hi - lo < 0.4f) {           // 全天几乎没动：给个最小跨度，别画成一条直线
                lo -= 0.2f; hi += 0.2f
            } else {
                val pad = (hi - lo) * 0.2f
                lo -= pad; hi += pad
            }
            val padY = 8.dp.toPx()
            val plotH = (size.height - padY * 2).coerceAtLeast(1f)
            fun yOf(v: Float) = padY + plotH * (1f - (v - lo) / (hi - lo))

            val t0 = pts.first().first.toFloat()
            val t1 = pts.last().first.toFloat()
            fun xOf(t: Long) = if (t1 <= t0) size.width / 2f
            else size.width * (t - t0) / (t1 - t0)

            // 网格：上界 / 0 / 下界
            for (v in listOf(hi, 0f, lo)) {
                drawLine(
                    AppGridLine,
                    Offset(0f, yOf(v)), Offset(size.width, yOf(v)),
                    strokeWidth = 1f,
                )
            }
            // 基线（0）：**虚线**，比网格线深一档
            run {
                val y = yOf(0f)
                var x = 0f
                while (x < size.width) {
                    drawLine(
                        Color(0x66FFFFFF),
                        Offset(x, y), Offset(minOf(x + 6.dp.toPx(), size.width), y),
                        strokeWidth = 1.5f,
                    )
                    x += 12.dp.toPx()
                }
            }
            // 折线
            val path = Path()
            pts.forEachIndexed { i, (t, v) ->
                val x = xOf(t); val y = yOf(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, TempDeviationColor, style = Stroke(width = 2.dp.toPx()))
            // 每个点画一个小圆点：点检本来就稀，不标出来看不出"只有这几次"
            for ((t, v) in pts) {
                drawCircle(TempDeviationColor, radius = 3.dp.toPx(), center = Offset(xOf(t), yOf(v)))
            }
        }
    }

    /**
     * 各阶段的解读文案（改写自三星健康睡眠阶段说明页，保留要点、去掉营销语气）。
     *
     * ⚠ 是 `@Composable`：整段是展示文案，要走多语言。
     *   `kind` 本身是**语言无关的枚举值**（`SleepStageKind`），不参与翻译。
     */
    @Composable
    private fun stageAdvice(kind: String): String = when (kind) {
        SleepStageKind.AWAKE ->
            stringResource(R.string.s_59032f38) +
                stringResource(R.string.s_70c32106)
        SleepStageKind.REM ->
            stringResource(R.string.s_61fa73b3) +
                stringResource(R.string.s_d80780cb)
        SleepStageKind.LIGHT ->
            stringResource(R.string.s_600de17e) +
                stringResource(R.string.s_7373b22d)
        SleepStageKind.DEEP ->
            stringResource(R.string.s_e862ec20) +
                stringResource(R.string.s_5aa35ced)
        else -> ""
    }

    /** 一行「标签 + 值」（ODI 卡与呼吸率卡共用，避免两处排版漂移）。 */
    @Composable
    private fun OdiFactRow(label: String, value: String) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
            Text(label, fontSize = 13.sp, color = AppTextMuted, modifier = Modifier.weight(1f))
            Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }

    /**
     * 氧减指数卡（模块 #11 的替代落点）。
     *
     * 位置在「血氧」卡下面：它是从**同一份**夜间血氧序列算出来的，放在一起才看得出
     * "这晚血氧长这样"与"这晚掉了几次"是同一件事的两面。
     *
     * 三种状态必须分开表达（这也是数据库里指标列可空的原因）：
     *   · 还没有摘要 → 说明怎么才会有；
     *   · 有摘要但没算出结论 → **原样显示原因**（监测时长不够 / 有效点太少 / 读数全不可信）；
     *   · 算出来了 → 数值 + 分级 + 口径 + 免责。
     *
     * 刻意**不显示最低血氧**：血氧卡上已经有一个"最低"，而那一个包含了被这里判定为
     * 不可信的读数。同页出现两个不同的"最低血氧"只会让人怀疑数据错了 ——
     * 宁可不显示，也不要两边对不上。
     */
    // ═══════════════════ 扩展指标页（2026-09-23）═══════════════════
    //
    // 这四个页面（活动 / 饮食 / 血糖 / 心律不齐）与前面那些页面的**根本差别**：
    // 它们的数据**不落库**，每次进来现读三星健康（`readData` / `aggregateData`）。
    //
    // 之所以能不落库：三星这些数据都支持**按时间范围重读**，不存在项目里那条
    // 「过期即永失」（对比 `sleep_nights`：它必须落库，因为三星的睡眠读取入口
    // 只给得到"最近一晚"）。不落库的代价是每次进页面读一次三星（本地 IPC），
    // 好处是不需要 DB 迁移、不污染备份、也不会留下"用旧口径算出的历史值"。
    //
    // ## 每个页面统一的三件事
    //
    // ① 进页面读一次 + 「重新读取」按钮；
    // ② 读不到时**分开说**是"没授权"还是"读失败"还是"三星里本来就没有" ——
    //    这三者要做到的事完全不同，合并成一句就是本项目反复禁止的那种误导；
    // ③ 「授权读取」按钮**按需**申请这一页要用的那 1~3 项权限。
    //    ⚠ 刻意不跟着冷启动一起弹：新增的 15 项里有血糖仪、体温计这类
    //    "用户压根没有的设备"，一次性弹十几个系统对话框是骚扰。

    /**
     * 扩展指标页共用的「读不到」区块：原因 + 该指标特有的空态说明 + 两个按钮。
     *
     * @param ids 本页涉及到的 [ExtraId]；会取**第一个有失败原因**的那个来显示。
     *   一页可能涉及两三项权限，报最靠前的那个即可 —— 逐条列出来反而看不清重点。
     */
    @Composable
    private fun ExtraFallback(
        ids: List<String>,
        emptyNote: String,
        onReload: () -> Unit,
    ) {
        val act = this@MainActivity
        val scope = rememberCoroutineScope()
        val err = ids.firstNotNullOfOrNull { SamsungHealthReader.extraError(it) }
        Text(
            if (err != null) this@MainActivity.getString(R.string.s_677d11b9, err) else this@MainActivity.getString(R.string.s_6cbb8996),
            fontSize = 13.sp,
            color = if (err != null) SettingsWarnColor else AppTextMuted,
        )
        Spacer(Modifier.height(4.dp))
        Text(emptyNote, fontSize = 12.sp, color = AppTextMuted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppOutlineButton(onClick = {
                scope.launch {
                    // 按需申请：只问这一页要用的那几项，用户知道自己在同意什么
                    SamsungHealthReader.requestExtra(act, ids)
                    onReload()
                }
            }) { Text(this@MainActivity.getString(R.string.s_718e4afa), fontSize = 12.sp) }
            AppOutlineButton(onClick = onReload) { Text(this@MainActivity.getString(R.string.s_a4558896), fontSize = 12.sp) }
        }
    }

    /**
     * 一条「当前值 / 目标」+ 进度条。
     *
     * ⚠ 进度条**不显示百分比的文字**，只显示"值 / 目标"这种原始对照 ——
     * 百分比会诱导用户去比较不同项目（走了 80%、喝了 30%），而那些目标的量纲
     * 根本不可比。真要给数，给原始值最诚实。
     *
     * @param goalText 为空表示**用户没设过目标**。这时不画进度条底槽上的填充，
     *   并在下面写一句"还没设目标" —— 而不是画一条空条让人以为完成度是 0。
     */
    @Composable
    private fun GoalRow(
        label: String,
        valueText: String,
        goalText: String?,
        ratio: Double?,
        color: Color,
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text(
                    if (goalText != null) "$valueText / $goalText" else valueText,
                    fontSize = 13.sp,
                    color = if (goalText != null) MaterialTheme.colorScheme.onSurface else AppTextMuted,
                )
            }
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x1AFFFFFF))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((ratio ?: 0.0).coerceIn(0.0, 1.0).toFloat())
                        .fillMaxHeight()
                        .background(color, RoundedCornerShape(3.dp))
                )
            }
            if (goalText == null) {
                Spacer(Modifier.height(3.dp))
                Text(stringResource(R.string.s_2b6695b2), fontSize = 11.sp, color = AppTextMuted)
            }
        }
    }

    /** 页面顶部的「正在读取」提示（三星 IPC 有几十毫秒延迟，不给提示会像卡住了）。 */
    @Composable
    private fun ReadingHint() {
        Text(stringResource(R.string.s_00960bd6), fontSize = 13.sp, color = AppTextMuted)
    }

    /**
     * 「活动」页：三星健康自己统计的活动量 + 目标 + 运动记录。
     *
     * 数据全部来自三星（手机与手表的计步），**不是本 App 采的** ——
     * 口径说明见 `core/ActivityNotes`，页面上必须写出来，否则用户会拿它
     * 跟本机那条"自研活动记录"（只覆盖 App 前台运行时段）互相印证。
     *
     * ## 按日查看（2026-10-10 用户要求）
     * 顶部一个日期条：选中哪天，下面就显示**那一天**的活动量与运动记录。
     * 可选日期 = **今天** + `activity_days` 里已经有记录的那些天 ——
     * 刻意**不是**"从今天往前推 N 天"，那会列出根本没有数据的日期。
     * 取数来源、以及为什么历史日没有"距离"，见 [ActivityDayCard] 的注释。
     */
    @Composable
    private fun ActivityTab(
        activity: List<ActivityDayEntity>,
        /** 加速度档位记录（「活动状态」卡的数据源）。2026-10-13 从心率页搬来。 */
        records: List<ActivityEntity>,
        onOpenRecords: () -> Unit,
    ) {
        val ctx = this@MainActivity
        var loading by remember { mutableStateOf(true) }
        var totals by remember { mutableStateOf<ActivityTotals?>(null) }
        var goals by remember { mutableStateOf<DailyGoals?>(null) }
        var exercises by remember { mutableStateOf<List<ExerciseEntry>>(emptyList()) }
        var floors by remember { mutableStateOf<List<FloorEntry>>(emptyList()) }
        var tick by remember { mutableStateOf(0) }

        // ⚠⚠ 选中的日期（`epochDay`）。**`0L` 代表"今天"**，刻意**不用 `null`**：
        //   `rememberSaveable` 对可空 `Long?` 的自动保存不做保证，而非空 `Long` 一定存得下来。
        //   ⚠ 用 `rememberSaveable` 而不是 `remember`，是为了配合外层 `SaveableStateProvider`：
        //     切到别的页面再回来，用户挑的那一天还在（否则会静默跳回今天）。
        var selectedDay by rememberSaveable { mutableLongStateOf(0L) }

        LaunchedEffect(tick) {
            loading = true
            totals = SamsungHealthReader.readActivityTotals(ctx)
            goals = SamsungHealthReader.readGoals(ctx)
            // ⚠ 读 [ACTIVITY_EXERCISE_DAYS] 天而不是原来的 30 天：这一页现在能按日回看，
            //   而运动记录本身很稀（一周也没几条）。一次读完 ≠ 浪费：
            //   反过来的做法是"每换一天再读一次"，那是每点一天就一次跨进程 IPC。
            exercises = SamsungHealthReader.readExercises(ctx, days = ACTIVITY_EXERCISE_DAYS)
            floors = SamsungHealthReader.readFloorsClimbed(ctx)
            loading = false
        }

        val reload: () -> Unit = { tick += 1 }

        val today = java.time.LocalDate.now().toEpochDay()
        // 可选日期：今天永远在最前，后面是库里真正有记录的那些天（新的在前）。
        val dayOptions: List<Long> = buildList {
            add(today)
            addAll(
                activity.asSequence()
                    // ⚠ `in 1 until today`：今天已经在上面单独放了；未来日期（时钟被改过）
                    //   与 epochDay ≤ 0 的脏数据一并挡掉。
                    .map { it.dateEpochDay }
                    .filter { it in 1 until today }
                    .distinct()
                    .sortedDescending()
                    .take(ACTIVITY_PICKER_DAYS - 1)
                    .toList()
            )
        }
        // 选的那天若已被裁掉（`keepLatest(400)` 会删最老的），退回今天 ——
        // 不把用户留在一个"已经不在列表里"的日期上（那会让日期条上一个高亮的都没有）。
        val sel = if (selectedDay != 0L && selectedDay in dayOptions) selectedDay else today
        val isToday = sel == today
        // 今天**没有**库里那一行：`syncActivityDays` 刻意不读今天（今天还没过完，读了就是半截数据）。
        val dayRow = activity.firstOrNull { it.dateEpochDay == sel }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：按日查看 ──
            item { SectionHeader(stringResource(R.string.s_601bd0ba), isFirstSection = true) }

            item {
                ActivityDateStrip(
                    days = dayOptions,
                    today = today,
                    selected = sel,
                    onSelect = { selectedDay = it },
                )
            }

            // ── 选中日的活动量 ──
            item {
                ActivityDayCard(
                    isToday = isToday,
                    epochDay = sel,
                    totals = totals,
                    dayRow = dayRow,
                    goals = goals,
                    floors = floors,
                    loading = loading,
                    onReload = reload,
                )
            }

            // ── 分区：运动记录 ──
            item { SectionHeader(stringResource(R.string.s_5b255d3d)) }

            item {
                ActivityExerciseCard(exercises = exercises, epochDay = sel, loading = loading)
            }

            // ── 活动状态（模块 #7）──
            //
            // ⚠⚠ 2026-10-13 从**心率页**搬到这一页（用户原话：「为什么睡眠里会有活动状态？」）。
            //   它当时挂在心率页的理由是"心率读数的上下文"，但那让用户在心率页看到一段
            //   "14:28 轻度活动"的白噪声 —— 而它讲的是**活动**。这里才是它的家：
            //   本页讲的就是活动，档位占比与"最近一段"正好是"这一天活动量"的来源。
            //   ⚠ 数据与「查看活动记录」入口**都没动**，只是换了宿主页。
            item { ActivityCard(records = records, onOpenRecords = onOpenRecords) }

            // ── 分区：口径与边界 ──
            item { SectionHeader(stringResource(R.string.s_63fbdf34)) }

            item {
                AppCard(title = stringResource(R.string.s_002b4cad)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        CollapsibleNotes(
                            listOf(
                                ActivityNotes.HOW,
                                ActivityNotes.VS_OUR_ACTIVITY,
                                GoalNotes.HOW,
                            ),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

        }
    }

    /**
     * 活动页的日期选择条（横向滚动）。
     *
     * ⚠ 用 `horizontalScroll` 而不是 `LazyRow`：最多 [ACTIVITY_PICKER_DAYS] 个条目，
     *   而这一整条本身是 `LazyColumn` 里的一个 item —— 嵌套惰性布局在这里只会
     *   多出一层测量复杂度，换不到任何东西。
     *
     * ⚠ 今天那一格写「今天」而不是日期：它是唯一每天都会变的入口，
     *   写成「10月10日」用户得先换算一下才敢确定那是不是今天。
     */
    @Composable
    private fun ActivityDateStrip(
        days: List<Long>,
        today: Long,
        selected: Long,
        onSelect: (Long) -> Unit,
    ) {
        val fmt = rememberShortDateFmt()
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (d in days) {
                val on = d == selected
                AppOutlineButton(
                    onClick = { onSelect(d) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        if (d == today) stringResource(R.string.s_0028aa87)
                        else fmt.format(Date(epochDayToMs(d))),
                        fontSize = 12.sp,
                        // 选中态用主色 + 加粗、未选中保持灰 —— 与记录页的筛选胶囊同一套观感，
                        // 刻意**不换组件类型**（都是 OutlinedButton）：它们只是同一按钮的两种状态。
                        color = if (on) MaterialTheme.colorScheme.primary else AppTextMuted,
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }

    /**
     * 选中日的活动量卡。
     *
     * ## ⚠⚠ 今天与历史日**取数来源不同**（这页最容易写错的地方）
     * - **今天**：`SamsungHealthReader.readActivityTotals`（实时聚合）。
     *   库里**刻意没有今天那一行** —— `syncActivityDays` 不读今天，因为今天还没过完，
     *   总量与分布都还在变，读了就是半截数据。
     * - **历史日**：`activity_days` 里那一行（每天随同步落库）。
     *   ⇒ 卡片必须同时接受这两种来源，而不是"统一读一处、另一处凑合"。
     *
     * ## ⚠ 历史日**没有「距离」**（库里没这一列）
     * 显示「—」。**不许**拿运动记录里的距离**求和**顶替 —— 那是"运动时的距离"，
     * 与三星那条"一整天的距离"不是同一个量，混起来会让人以为今天走了 8 公里，
     * 其实只是跑了一次步。（同一条纪律："三种体温绝不混"。）
     *
     * ## 爬楼
     * `readFloorsClimbed` 只读最近 7 天 ⇒ 更早的日期**不在覆盖范围内**，
     * 此时显示「—」而不是「0 层」：**"读不到" ≠ "一层没爬"**。
     */
    @Composable
    private fun ActivityDayCard(
        isToday: Boolean,
        epochDay: Long,
        totals: ActivityTotals?,
        dayRow: ActivityDayEntity?,
        goals: DailyGoals?,
        floors: List<FloorEntry>,
        loading: Boolean,
        onReload: () -> Unit,
    ) {
        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        // 今天沿用原来那句「今日活动」；历史日直接写日期。
                        if (isToday) stringResource(R.string.s_a99d3234) else epochDay.toDateLabel(),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.s_8fc12af0), fontSize = 10.sp, color = AppTextMuted)
                }
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    ReadingHint()
                    return@Column
                }

                // ── 今天走实时读数 ──
                if (isToday) {
                    val t = totals
                    val noData = t == null ||
                        (t.steps == null && t.activeKcal == null &&
                            t.activeMs == null && t.distanceM == null)
                    if (noData) {
                        ExtraFallback(
                            ids = listOf(ExtraId.STEPS, ExtraId.ACTIVITY_SUMMARY),
                            emptyNote = ActivityNotes.MAY_BE_EMPTY,
                            onReload = onReload,
                        )
                        return@Column
                    }
                } else if (dayRow == null) {
                    // 理论上走不到（日期条只列库里有行的那些天），但真出现时要说清楚，
                    // 而不是画一张全是「—」的卡（那看起来像这一页坏了）。
                    Text(stringResource(R.string.s_d7dc3fd1), fontSize = 13.sp, color = AppTextMuted)
                    return@Column
                }

                val steps = if (isToday) totals?.steps else dayRow?.steps
                val activeKcal = if (isToday) totals?.activeKcal else dayRow?.activeKcal
                val activeMs = if (isToday) totals?.activeMs else dayRow?.activeMs
                // 只有今天有实时距离；历史日的距离不落库（见上面那条判据）。
                val distanceM = if (isToday) totals?.distanceM else null
                // 总消耗（含基础代谢）同样只有实时那条口径。
                val totalKcal = if (isToday) totals?.totalKcal else null

                val g = goals
                GoalRow(
                    label = stringResource(R.string.s_00375a1f),
                    valueText = Extras.fmtSteps(steps),
                    goalText = g?.steps?.let { stringResource(R.string.s_861e53d0, it) },
                    ratio = Extras.goalRatio(steps?.toDouble(), g?.steps?.toDouble()),
                    color = ActivityColor,
                )
                GoalRow(
                    label = stringResource(R.string.s_b8d04e50),
                    valueText = Extras.fmtKcal(activeKcal),
                    goalText = g?.activeKcal?.let { stringResource(R.string.s_a178dd6b, it) },
                    ratio = Extras.goalRatio(
                        activeKcal?.toDouble(), g?.activeKcal?.toDouble()
                    ),
                    color = ActivityColor,
                )
                GoalRow(
                    label = stringResource(R.string.s_b8cc8402),
                    valueText = Extras.fmtDurationOrNull(activeMs),
                    goalText = g?.activeMs?.let { Extras.fmtDurationOrNull(it) },
                    ratio = Extras.goalRatio(
                        activeMs?.toDouble(), g?.activeMs?.toDouble()
                    ),
                    color = ActivityColor,
                )
                // 距离与爬楼**没有独立目标**（三星只给了步数/活动消耗/活动时长的目标），
                // 所以只显示值、不画进度条 —— 硬凑一个目标就是编数据。
                Spacer(Modifier.height(2.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.s_004911d2), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(Extras.fmtDistance(distanceM), fontSize = 13.sp, color = AppTextMuted)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.s_003ad600), fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(floorsText(floors, epochDay), fontSize = 13.sp, color = AppTextMuted)
                }
                if (totalKcal != null) {
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.s_4b038fb3), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(Extras.fmtKcal(totalKcal), fontSize = 13.sp, color = AppTextMuted)
                    }
                }
            }
        }
    }

    /**
     * 选中日的运动记录卡。
     *
     * ⚠ 按**开始时刻**归日（不是结束时刻）：一次 23:50 开始、次日 00:30 结束的夜跑，
     *   归到"开始的那天"才符合直觉，也让"同一条记录不会出现在两个日期里"。
     */
    @Composable
    private fun ActivityExerciseCard(
        exercises: List<ExerciseEntry>,
        epochDay: Long,
        loading: Boolean,
    ) {
        val dayFrom = epochDayToMs(epochDay)
        val dayTo = epochDayToMs(epochDay + 1)
        val ofDay = exercises.filter { it.startMs in dayFrom until dayTo }

        AppCard(title = stringResource(R.string.s_5b255d3d)) {
            Column {
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    ReadingHint()
                } else if (ofDay.isEmpty()) {
                    Text(
                        stringResource(R.string.s_eb901e6b),
                        fontSize = 13.sp, color = AppTextMuted,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_927340e7),
                        fontSize = 11.sp, color = AppTextMuted,
                    )
                } else {
                    for (e in ofDay.take(20)) {
                        ExerciseRow(e)
                        Spacer(Modifier.height(10.dp))
                    }
                    if (ofDay.size > 20) {
                        Text(
                            stringResource(R.string.s_49733e06, ofDay.size),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
            }
        }
    }

    /**
     * 爬楼的显示文本。
     *
     * ⚠⚠ 判据是**"那一天在不在读取窗口里"**，不是"和是不是 0"：
     *   `readFloorsClimbed` 只覆盖最近 7 天 ⇒ 更早的日子**读不到**，
     *   直接求和会得到 0 → 显示「0 层」，把"我们没有这份数据"说成了"你一层没爬"。
     *   （与 `stagesJson` 那条「没有 ≠ 0」是同一个母题。）
     */
    private fun floorsText(floors: List<FloorEntry>, epochDay: Long): String {
        if (floors.isEmpty()) return "—"
        val dayEnd = epochDayToMs(epochDay + 1)
        // 窗口最早那条记录之后的日子才算"覆盖到"；今天永远算覆盖（0 层是可信的）。
        val covered = dayEnd > floors.minOf { it.atMs }
        if (!covered) return "—"
        val dayFrom = epochDayToMs(epochDay)
        val sum = floors
            .filter { it.atMs in dayFrom until dayEnd }
            .sumOf { it.floors.toDouble() }
        return Extras.fmtFloors(sum.toFloat())
    }

    /** 一条运动记录。 */
    @Composable
    private fun ExerciseRow(e: ExerciseEntry) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    e.customTitle?.takeIf { it.isNotBlank() } ?: e.typeName,
                    fontSize = 14.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    cardFmt.format(Date(e.startMs)),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
            Spacer(Modifier.height(2.dp))
            // 只显示**有值**的那几项：三星对不同的运动记录的内容不一样
            //（走路有步数没距离、泳池游泳有距离没心率），显示一堆「—」比不显示更差。
            val parts = buildList {
                add(Extras.fmtDurationOrNull(e.durationMs))
                if (e.calories > 0f) add(Extras.fmtKcal(e.calories))
                e.distanceM?.let { if (it > 0f) add(Extras.fmtDistance(it)) }
                e.meanHr?.let { if (it > 0f) add(stringResource(R.string.s_7f1f3017, it.toInt())) }
                e.count?.let { if (it > 0) add(stringResource(R.string.s_861e538c, it)) }
            }
            Text(parts.joinToString(" · "), fontSize = 12.sp, color = AppTextMuted)
        }
    }

    /**
     * 「饮食」页：摄入热量与饮水（**都只能由用户自己在三星健康里记**）。
     */
    @Composable
    private fun DietTab() {
        val lang = rememberLang()
        val ctx = this@MainActivity
        var loading by remember { mutableStateOf(true) }
        var nutrition by remember { mutableStateOf<List<NutritionEntry>>(emptyList()) }
        var water by remember { mutableStateOf<List<WaterEntry>>(emptyList()) }
        var goals by remember { mutableStateOf<DailyGoals?>(null) }
        var tick by remember { mutableStateOf(0) }

        LaunchedEffect(tick) {
            loading = true
            nutrition = SamsungHealthReader.readNutrition(ctx)
            water = SamsungHealthReader.readWaterIntake(ctx)
            goals = SamsungHealthReader.readGoals(ctx)
            loading = false
        }
        val reload: () -> Unit = { tick += 1 }

        val todayMs = todayStartMs()
        val kcalToday = nutrition.filter { it.atMs >= todayMs }.sumOf { it.calories.toDouble() }
        val waterToday = water.filter { it.atMs >= todayMs }.sumOf { it.ml.toDouble() }
        val g = goals

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：今日摄入 ──
            item { SectionHeader(stringResource(R.string.s_0028b743), isFirstSection = true) }

            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s_a9989a8c), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.s_8fc12af0), fontSize = 10.sp, color = AppTextMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (loading) {
                            ReadingHint()
                            return@Column
                        }
                        if (nutrition.isEmpty() && water.isEmpty()) {
                            // 两项都没有 → 大概率是没授权或用户根本没在三星健康里记过。
                            // 用摄入那一项的 id 去查原因（两项权限通常是同一次授予的）。
                            ExtraFallback(
                                ids = listOf(ExtraId.NUTRITION, ExtraId.WATER),
                                emptyNote = DietNotes.HOW,
                                onReload = reload,
                            )
                            return@Column
                        }
                        GoalRow(
                            label = stringResource(R.string.s_84f7cfcf),
                            valueText = Extras.fmtKcal(kcalToday.toFloat()),
                            goalText = g?.nutritionKcal?.let { stringResource(R.string.s_1c063a6a, it.toInt()) },
                            ratio = Extras.goalRatio(kcalToday, g?.nutritionKcal?.toDouble()),
                            color = DietColor,
                        )
                        GoalRow(
                            label = stringResource(R.string.s_004eef7e),
                            valueText = Extras.fmtVolume(waterToday.toFloat()),
                            goalText = g?.waterMl?.let { Extras.fmtVolume(it) },
                            ratio = Extras.goalRatio(waterToday, g?.waterMl?.toDouble()),
                            color = DietColor,
                        )
                    }
                }
            }

            // ── 分区：饮食明细 ──
            if (nutrition.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.s_8d09e526)) }
                item {
                    AppCard(title = stringResource(R.string.s_8d09e526)) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            for (n in nutrition.take(20)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            n.title ?: n.mealType,
                                            fontSize = 13.sp,
                                        )
                                        val macro = buildList {
                                            n.carbohydrateG?.let { add(stringResource(R.string.s_7f786756, it.toInt())) }
                                            n.proteinG?.let { add(stringResource(R.string.s_2d76b971, it.toInt())) }
                                            n.fatG?.let { add(stringResource(R.string.s_a42eeab7, it.toInt())) }
                                        }
                                        Text(
                                            if (macro.isEmpty()) cardFmt.format(Date(n.atMs))
                                            else macro.joinToString(" · "),
                                            fontSize = 11.sp, color = AppTextMuted,
                                        )
                                    }
                                    Text(Extras.fmtKcal(n.calories), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ── 分区：口径与边界 ──
            item { SectionHeader(stringResource(R.string.s_63fbdf34)) }

            item {
                AppCard(title = stringResource(R.string.s_002b4cad)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        CollapsibleNotes(
                            listOf(DietNotes.HOW, Disclaimer.dietNotAPlan(lang), GoalNotes.HOW),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    /**
     * 「血糖」页。
     *
     * ⚠ 单位 **mmol/L**（官方文档核实，不是 mg/dL，两者差约 18 倍）。
     * ⚠ **不做任何高低判断** —— 血糖的解读依赖测量情境，本 App 拿不到那些上下文。
     */
    @Composable
    private fun GlucoseTab() {
        val lang = rememberLang()
        val ctx = this@MainActivity
        var loading by remember { mutableStateOf(true) }
        var rows by remember { mutableStateOf<List<GlucoseReading>>(emptyList()) }
        var tick by remember { mutableStateOf(0) }

        LaunchedEffect(tick) {
            loading = true
            rows = SamsungHealthReader.readBloodGlucose(ctx)
            loading = false
        }
        val reload: () -> Unit = { tick += 1 }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：最近一次 ──
            item { SectionHeader(stringResource(R.string.s_f30a737a), isFirstSection = true) }

            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s_00463596), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.s_8fc12af0), fontSize = 10.sp, color = AppTextMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (loading) {
                            ReadingHint()
                            return@Column
                        }
                        if (rows.isEmpty()) {
                            ExtraFallback(
                                ids = listOf(ExtraId.GLUCOSE),
                                emptyNote = Glucose.UNAVAILABLE_NOTE,
                                onReload = reload,
                            )
                            return@Column
                        }
                        val latest = rows.maxByOrNull { it.atMs }
                        if (latest != null) {
                            Text(
                                cardFmt.format(Date(latest.atMs)) + " · " + com.gemini.healthbridge.core.Glucose.mealLabel(latest.meal),
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                Extras.fmtGlucose(latest.mmolPerL),
                                fontSize = 34.sp, fontWeight = FontWeight.Bold,
                                color = GlucoseColor,
                            )
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.s_002b1184)) }
                item {
                    AppCard(title = stringResource(R.string.s_15390a5e, rows.size)) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            for (r in rows.take(30)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(Extras.fmtGlucose(r.mmolPerL), fontSize = 14.sp)
                                        Text(
                                            buildList {
                                                add(com.gemini.healthbridge.core.Glucose.mealLabel(r.meal))
                                                // ⚠ 判"未标注"走原始串（`isUndefined`），
                                                // 不能拿译文比 —— 译文随语言变，比较必然失配（模块 #15）。
                                                if (!com.gemini.healthbridge.core.Glucose.isUndefined(r.source))
                                                    add(com.gemini.healthbridge.core.Glucose.sourceLabel(r.source))
                                                if (!com.gemini.healthbridge.core.Glucose.isUndefined(r.sample))
                                                    add(com.gemini.healthbridge.core.Glucose.sampleLabel(r.sample))
                                            }.joinToString(" · "),
                                            fontSize = 11.sp, color = AppTextMuted,
                                        )
                                    }
                                    Text(cardFmt.format(Date(r.atMs)), fontSize = 11.sp, color = AppTextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // ── 分区：口径与边界 ──
            item { SectionHeader(stringResource(R.string.s_63fbdf34)) }

            item {
                AppCard(title = stringResource(R.string.s_002b4cad)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        CollapsibleNotes(
                            listOf(Disclaimer.glucoseUnit(lang), Disclaimer.glucoseNoJudgement(lang), Glucose.UNAVAILABLE_NOTE),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    /**
     * 「心律不齐」页。
     *
     * ⚠ 措辞红线见 `core/Ihrn.kt`：只有 `DETECTED` / `UNDEFINED` 两个取值，
     * **没有 `NOT_DETECTED`** —— 所以"列表为空"**不能**读成"心脏没问题"。
     * 那句 `EMPTY_IS_NOT_NEGATIVE` 必须和列表同时出现（空态与有数据两种情况下都要）。
     */
    @Composable
    private fun IhrnTab() {
        val ctx = this@MainActivity
        var loading by remember { mutableStateOf(true) }
        var rows by remember { mutableStateOf<List<IhrnEntry>>(emptyList()) }
        var tick by remember { mutableStateOf(0) }

        LaunchedEffect(tick) {
            loading = true
            rows = SamsungHealthReader.readIhrn(ctx)
            loading = false
        }
        val reload: () -> Unit = { tick++ }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：最近一次 ──
            item { SectionHeader(stringResource(R.string.s_f30a737a), isFirstSection = true) }

            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.s_ee1aeeeb), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(stringResource(R.string.s_f7145372), fontSize = 10.sp, color = AppTextMuted)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (loading) {
                            ReadingHint()
                            return@Column
                        }
                        if (rows.isEmpty()) {
                            ExtraFallback(
                                ids = listOf(ExtraId.IHRN),
                                emptyNote = Ihrn.UNAVAILABLE_NOTE,
                                onReload = reload,
                            )
                        } else {
                            val detected = rows.count { Ihrn.isAlarming(it.status) }
                            Text(
                                if (detected > 0) stringResource(R.string.s_9843c93b, rows.size, detected)
                                else stringResource(R.string.s_59d45e26, rows.size),
                                fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                color = if (detected > 0) SettingsWarnColor
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            if (detected > 0) {
                                Spacer(Modifier.height(4.dp))
                                Text(Ihrn.ADVICE, fontSize = 12.sp, color = AppTextMuted)
                            }
                        }
                    }
                }
            }

            if (rows.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.s_002b1184)) }
                item {
                    AppCard(title = stringResource(R.string.s_0047da65)) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            for (r in rows.take(30)) {
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        Ihrn.label(r.status),
                                        fontSize = 13.sp,
                                        color = if (Ihrn.isAlarming(r.status)) SettingsWarnColor
                                        else AppTextMuted,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(cardFmt.format(Date(r.atMs)), fontSize = 11.sp, color = AppTextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // ── 分区：口径与边界 ──
            item { SectionHeader(stringResource(R.string.s_63fbdf34)) }

            item {
                AppCard(title = stringResource(R.string.s_00318dca)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        // ⚠ 这一组**不折叠**（不用 CollapsibleNotes）：它是"空列表不等于没问题"
                        //    这种会造成错误安心感的内容，收起来就等于没写。
                        Text(Ihrn.EMPTY_IS_NOT_NEGATIVE, fontSize = 12.sp, color = SettingsWarnColor)
                        Spacer(Modifier.height(8.dp))
                        CollapsibleNotes(
                            listOf(Ihrn.VS_OUR_ECG, Ihrn.ADVICE, Ihrn.NOT_A_DIAGNOSIS),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    /**
     * 今天 00:00 的毫秒时间戳（本地时区）。
     *
     * 定义已移到 [SamsungHealthReader.todayStartMs] —— `pushExtrasSummary` 推给手表的
     * 「今日活动 / 今日摄入」必须与这一页用**同一个日界**。这里保留薄壳，调用处不动。
     */
    private fun todayStartMs(): Long = SamsungHealthReader.todayStartMs()

    @Composable
    private fun OdiCard(e: SleepOdiEntity?) {
        val lang = rememberLang()
        AppCard(title = stringResource(R.string.s_62585af5)) {
            Column {
                Spacer(Modifier.height(6.dp))
                when {
                    e == null -> {
                        Text(
                            stringResource(R.string.s_abb3d9ee) +
                                stringResource(R.string.s_1326ad27),
                            fontSize = 13.sp, color = AppTextMuted,
                        )
                    }
                    e.reason != null -> {
                        Text(
                            stringResource(R.string.s_5249a536, e.reason.toString()),
                            fontSize = 13.sp, color = AppTextMuted,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.s_5c460754),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                    }
                    else -> {
                        // 取局部变量：跨模块的公开属性不会被智能转换成非空
                        val odi = e.odiPerHour
                        if (odi == null) {
                            Text(stringResource(R.string.s_79f19613), fontSize = 13.sp, color = AppTextMuted)
                            return@Column
                        }
                        val token = Odi.bandOf(odi)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                Odi.formatOdi(odi),
                                fontSize = 28.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                Odi.bandLabel(token),
                                fontSize = 13.sp,
                                color = Color(Odi.bandColorArgb(token)),
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        OdiFactRow(stringResource(R.string.s_938bc3fb), Odi.formatEvents(e.events))
                        OdiFactRow(stringResource(R.string.s_089a088f), Odi.formatMonitored(e.monitoredMinutes))
                        OdiFactRow(stringResource(R.string.s_92ec7735), Odi.formatT90(e.t90Pct))
                        val rej = e.rejectedPoints ?: 0
                        if (rej > 0) OdiFactRow(stringResource(R.string.s_aa8addbc), stringResource(R.string.s_3d5a8aa9, rej))
                        Spacer(Modifier.height(10.dp))
                        // 3 段说明合并成一个可折叠块（默认收起，2026-09-21）：
                        // 常驻展开时这段灰字比上面的数据还长，整张卡一半高度都在讲口径。
                        CollapsibleNotes(
                            listOf(
                                Odi.METHOD_NOTE,
                                Odi.RESOLUTION_NOTE,
                                Disclaimer.odi(lang),
                            ),
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }
    }

    /** 血氧详情：平均/最低 + 折线 + 解读。 */
    @Composable
    private fun Spo2Detail(night: SleepNightDto, points: List<Pair<Long, Int>>) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(title = stringResource(R.string.s_bf089207)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${night.spo2Avg ?: "--"}%",
                                fontSize = 34.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(R.string.s_0dfba084, dash(night.spo2Min)),
                                fontSize = 13.sp, color = AppTextMuted,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (points.isEmpty()) {
                            Text(stringResource(R.string.s_33aadfe9), fontSize = 13.sp, color = AppTextMuted)
                        } else {
                            Spo2NightChart(points, night.startMs, night.endMs, 160.dp)
                            Spacer(Modifier.height(2.dp))
                            TimeAxisRow(night.startMs, night.endMs)
                            Spacer(Modifier.height(6.dp))
                            ChartLegend(
                                listOf(
                                    Color(0xFFB79BFF) to stringResource(R.string.s_0030ac20),
                                    Color(0x597E57C2) to stringResource(R.string.s_a5175f80),
                                )
                            )
                            Spacer(Modifier.height(6.dp))
                            // ⚠ 最低点取**整段序列**的最小值（不是桶均值的），与柱子的
                            //   "最低"是同一个口径 —— 两个数必须对得上，
                            //   否则图上最矮的那根柱子跟文字里的数字不一致，图就白改了。
                            val lowest = remember(points) { points.minByOrNull { it.second } }
                            Text(
                                stringResource(R.string.s_ad578fcc) +
                                    stringResource(R.string.s_97a261e9) +
                                    (lowest?.let {
                                        stringResource(
                                            R.string.s_2ffded78,
                                            it.second,
                                            SimpleDateFormat("HH:mm", Locale.getDefault())
                                                .format(Date(it.first)),
                                        )
                                    } ?: ""),
                                fontSize = 10.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }
            item {
                AppCard(title = stringResource(R.string.s_9ec76ca6)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_7da4442b) +
                                stringResource(R.string.s_b7011fb6) +
                                stringResource(R.string.s_7f8ca56b),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_2c5437da) +
                                stringResource(R.string.s_93036435),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
            }
        }
    }

    /** 睡眠心率详情：平均/最低最高 + 柱状图 + 睡眠期心率说明。 */
    @Composable
    private fun SleepHrDetail(night: SleepNightDto, points: List<Pair<Long, Int>>) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(title = stringResource(R.string.s_bef3e170)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                "${night.hrAvg ?: "--"}",
                                fontSize = 34.sp, fontWeight = FontWeight.Bold,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                stringResource(R.string.s_1c0dd13c),
                                fontSize = 13.sp, color = AppTextMuted,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                stringResource(R.string.s_57ca1bc6, dash(night.hrMin), dash(night.hrMax)),
                                fontSize = 12.sp, color = AppTextMuted,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        if (points.isEmpty()) {
                            Text(stringResource(R.string.s_2d68a622), fontSize = 13.sp, color = AppTextMuted)
                        } else {
                            SleepHrChart(points, night.startMs, night.endMs, 160.dp)
                            Spacer(Modifier.height(2.dp))
                            TimeAxisRow(night.startMs, night.endMs)
                            Spacer(Modifier.height(6.dp))
                            ChartLegend(listOf(HrSleepColor to stringResource(R.string.s_0030ac20)))
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_0af1886c),
                                fontSize = 10.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }
            item {
                AppCard(title = stringResource(R.string.s_c7fca294)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_55da6f9e) +
                                stringResource(R.string.s_2fa5edcd) +
                                stringResource(R.string.s_26719ece) +
                                stringResource(R.string.s_8ffce9ad) +
                                stringResource(R.string.s_747ed3da) +
                                stringResource(R.string.s_ef0a73b7) +
                                stringResource(R.string.s_9bfc03ce) +
                                stringResource(R.string.s_55523da3),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }
    }

    /**
     * 呼吸详情：**三星拿不到，但我们自己算出来了**，这一页就是讲清这两件事的边界。
     *
     * 早期版本这一页写的是"本页暂无数据、等待 SDK 开放"。现在不对了：
     * 呼吸率已经由 `Resp` 从我们自己采的原始信号里算出来（血氧页/心电页），
     * 睡眠场景也改由 ODI 承担。留这一页的价值是**解释边界**，而不是宣告做不到。
     */
    @Composable
    private fun RespDetail() {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard(title = stringResource(R.string.s_b2a15aae)) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_d2ade356) +
                                stringResource(R.string.s_aabe1042) +
                                stringResource(R.string.s_1eb7792f) +
                                stringResource(R.string.s_86c17ef3) +
                                stringResource(R.string.s_bae4bc38) +
                                stringResource(R.string.s_ccb57c49) +
                                stringResource(R.string.s_087afebd),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_51ca4274) +
                                stringResource(R.string.s_0d5bcc52) +
                                stringResource(R.string.s_dbf2522a) +
                                stringResource(R.string.s_26500256) +
                                stringResource(R.string.s_c061e8cd),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            item {
                AppCard(title = stringResource(R.string.s_2ae5ad5d)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_4abbaf04) +
                                stringResource(R.string.s_ea9c2903) +
                                stringResource(R.string.s_eb21b2cc) +
                                stringResource(R.string.s_17aa7af7),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_bf71e176),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
            }
        }
    }

    // ═══════════════════════ 血氧 ═══════════════════════

    /**
     * 血氧页：与心率页**同构**的图表分析页。
     *
     * 区块与心率页一一对应：今日概览 / 全天每小时最小·最大（睡眠换色）/ 极值时段 /
     * 睡眠期血氧（只画睡眠小时）/ 数据口径 / 最近记录。
     * 睡眠期的逐分钟折线留在详情页（[Spo2Detail]）—— 概览页要的是可跨小时比较的柱子。
     *
     * 数据来自三星健康的血氧序列（[SleepUiState.spo2Today] 当日全天、[SleepUiState.spo2] 睡眠期）。
     * 拿不到序列时退回本机测量记录，并**明确标注**口径不同。
     *
     * 一个必须说清的限制：三星健康里的手表血氧是**间歇采样**（主要在睡眠期，白天多为按需单点），
     * 所以白天的小时柱经常很稀疏、甚至只有几根。页面照实呈现，不把稀疏画成"连续监测"。
     */
    @Composable
    private fun Spo2Tab(
        records: List<com.gemini.healthbridge.core.db.SpO2Entity>,
        samsungGranted: Boolean?,
        /** 「问不出来」（SDK 连不上）—— 与「未授权」分开说，见 `samsungUnreadable` 的说明。 */
        samsungUnreadable: Boolean,
        state: SleepUiState,
        loading: Boolean,
        onOpenRecords: () -> Unit,
        onOpenDetail: () -> Unit,
        ppg: List<PpgWaveEntity>,
        onOpenPpgRecords: () -> Unit,
    ) {
        val night = state.night
        val today = state.spo2Today

        // 睡眠时段用于给柱子换色。直接取最近一晚会话的区间，不再去读一趟三星健康 ——
        // 这页的图本来就是按"昨晚"这一个窗口讲的（心率页要跨会话所以另读了 readSleepSpans）。
        val sleepSpans: List<Pair<Long, Long>> =
            if (night != null) listOf(night.startMs to night.endMs) else emptyList()

        val day0 = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        // ⚠⚠ 三星序列拿不到时，**用本机点测记录兜底**（2026-10-13）。
        //
        // 原来这里写死 `bucketByHour(today, …)`（只吃三星序列）：序列一旦拿不到，
        // `hours` 就是空的 ⇒ **整张图卡连着极值卡一起不画**，页面只剩"今日血氧"那张数字卡，
        // 用户看到的是"这一页没有图了"（他的原话：「为什么血氧的今日数据没用图了」）。
        //
        // ⚠ 心率页一直有这条兜底（`aggregateByHour(records, day0)`），**血氧页缺它** ——
        //   这是"同构页面只改了一半"。两页的兜底口径要一致：都用本机记录按小时聚合。
        //
        // ⚠⚠ 口径**必须标注**：本机点测 ≠ 三星连续序列。图上那句提示换成
        //   `s_776d85ad`（…本机测量的最低~最高，不是三星健康连续序列），概览卡也补一行橙字
        //   `s_b5f7e4d9`。**不标注就等于把"一天 13 个点"画成了连续监测**。
        val todayLocal = records.filter { it.measuredAt >= day0 }
            .map { it.measuredAt to it.percent }
            // ⚠ 按时间**升序**：库里 `recent(N)` 给的是倒序（最新在前），而下面
            //   `latest = spo2Series.lastOrNull()` 取的是**最后一条** —— 不排序的话
            //   概览卡那行「最新 HH:mm」会指向当天**最早**的一次测量。
            .sortedBy { it.first }
        val fromLocal = today.isEmpty() && todayLocal.isNotEmpty()
        val spo2Series = if (today.isNotEmpty()) today else todayLocal

        val hours = bucketByHour(spo2Series, day0, sleepSpans)
        val byHour = hours.associateBy { it.hour }
        val sleepSlots = hours.filter { it.sleep }.map { it.hour }
        val bounds = spo2Bounds(hours.flatMap { listOf(it.min, it.max) })

        val allMin = hours.minOfOrNull { it.min }
        val allMax = hours.maxOfOrNull { it.max }
        // ⚠⚠ 2026-10-15 改口径：原来是"**每小时均值的平均**"（各小时等权），
        //   而副文里的「整晚均值」是三星按**所有采样点**平均 —— 两者不等权时会对不上，
        //   用户在同一张卡上看到 99% 与 98% 两个数，只会觉得"数据有问题"。
        //   ⇒ 主数字改成按采样点平均（就是"你自己的平均血氧"），两个口径不再打架。
        //   ⚠ 注意不能拿 `hours.map{it.avg}` 再平均：那是"均值的均值"，
        //     某小时只有 1 个点时它也会占满一格权重。
        val allAvg = spo2Series.takeIf { it.isNotEmpty() }
            ?.map { it.second }?.average()?.roundToInt()
        val sleepHours = hours.filter { it.sleep }
        val sleepOnlyAvg = sleepHours.takeIf { it.isNotEmpty() }
            ?.map { it.avg }?.average()?.roundToInt()
        val sleepOnlyMin = sleepHours.minOfOrNull { it.min }
        val dipHour = hours.minByOrNull { it.min }
        // ⚠ 取的是**这张图实际用的那一串**（三星序列或本机兜底），不是写死 `today`
        val latest = spo2Series.lastOrNull()

        // ⚠⚠ "顶部那根分隔线要不要画"与**下面横幅的 `if` 是同一个条件**（2026-10-12）：
        //   横幅在时它就是"上面的内容块"、线要画；横幅不在时线才是多余的。
        //   所以这里只写**一条**条件、两处共用 —— 免得"改了 `if` 忘了改线"这种静默不一致。
        //   理由与判据见 `SectionHeader`（第一个分区不画上分隔线）。
        val staleBanner = state.spo2Today.isEmpty() && !loading && state.fallbackEpochDay != null

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 0) 未同步横幅（2026-10-09）──
            //
            // 这页的「今日血氧」图（`hours`）只画**今天 0 点起**的实时序列，
            // 拿不到时卡片会退回库里的历史记录（`records` 分支）——
            // 那条路必须**说清那是旧数据**，否则用户会把上个月的血氧当成今天的。
            // 判据：`state.spo2Today` 为空 = 本次没同步到今日序列（与睡眠页 `fresh` 同源）。
            if (staleBanner) {
                state.fallbackEpochDay?.let { day ->
                    item { StaleDataBanner(day) }
                }
            }

            // ── 分区：今日血氧 ──
            item {
                SectionHeader(
                    stringResource(R.string.s_0028b743),
                    isFirstSection = !staleBanner,
                )
            }

            // ── 1) 今日概览（或有数据的替代视图）──
            //
            // 2026-10-15 改版：统一卡片 + 主数字组件。
            // ⚠ 三条信息一个都没少（平均 / 最低~最高 / 有数据的时段 + 最新一条），
            //   只是从"三行各写各的"收成 `AppKpi` 的「大字 + 单位 + 一行说明」。
            item {
                AppCard(title = stringResource(R.string.s_a9ab1f42)) {
                    when {
                        hours.isNotEmpty() -> {
                            AppKpi(
                                value = "${allAvg ?: "--"}",
                                unit = "%",
                                caption = stringResource(
                                    R.string.s_359d7a47, allMin ?: "--", allMax ?: "--",
                                ) + " · " + stringResource(R.string.s_de17fbfd, hours.size) +
                                    (latest?.let {
                                        stringResource(
                                            R.string.s_3431419b,
                                            timeFmt.format(Date(it.first)), it.second,
                                        )
                                    } ?: ""),
                            )
                            // ⚠ 口径警告：这批柱子来自**本机点测**（不是三星连续序列）。
                            //   不标出来 = 把"一天十几次测量"画成了连续监测。
                            if (fromLocal) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_b5f7e4d9, records.size),
                                    fontSize = 12.sp, color = Color(0xFFFFA94D),
                                )
                            }
                        }
                        records.isNotEmpty() -> {
                            // 三星健康序列拿不到，退回本机手表测量记录。
                            val vals = records.map { it.percent }
                            AppKpi(
                                value = "${vals.average().roundToInt()}",
                                unit = "%",
                                caption = stringResource(R.string.s_de1d516f, vals.min(), vals.max()),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_28e444b6, records.size) +
                                    stringResource(R.string.s_18fbc2ff),
                                fontSize = 12.sp, color = Color(0xFFFFA94D),
                            )
                        }
                        else -> {
                            Text(
                                if (loading) stringResource(R.string.s_00960bd6)
                                else stringResource(R.string.s_94dad682) +
                                    stringResource(R.string.s_95cfb7a6),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 分区：全天走势 ──
            //
            // 第 3 张（极值时段）与第 4 张（睡眠期血氧）都是**从上面那张图里读出来的**
            // 「具体哪一段出问题」，与图共用一条分隔线 —— 三张卡讲的是同一件事。
            item { SectionHeader(stringResource(R.string.s_001f2278)) }

            // ── 2) 全天每小时最小·最大（睡眠换色）── 有睡眠会话时可点进详情
            if (hours.isNotEmpty()) {
                // 详情页（[Spo2Detail]）画的是睡眠期逐分钟折线，需要 night 才有意义。
                // 白天只有零散采样、没有睡眠会话时，这张图就不做点击入口 —— 否则会进到一个空页面。
                val canDetail = night != null && state.spo2.isNotEmpty()
                item {
                    AppCard(
                        title = stringResource(R.string.s_66671bc0),
                        // ⚠ 有睡眠会话才可点（点进去是逐分钟折线，没会话就是空页）
                        onClick = if (canDetail) ({ onOpenDetail() }) else null,
                        trailing = if (canDetail) {
                            { Text("›", fontSize = 16.sp, color = AppTextMuted) }
                        } else {
                            null
                        },
                    ) {
                        Column {
                            HourRangeChart(
                                slots = (0..23).toList(),
                                hours = byHour,
                                bounds = bounds,
                                labelEvery = 6,
                                sleepDiff = true,
                                awakeColor = Spo2AwakeColor,
                                sleepColor = Spo2SleepColor,
                            )
                            Spacer(Modifier.height(6.dp))
                            ChartLegend(
                                listOf(
                                    Spo2AwakeColor to stringResource(R.string.stage_awake),
                                    Spo2SleepColor to stringResource(R.string.s_003d8dc3),
                                ),
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                // ⚠ 用本机点测兜底时，提示语必须换 —— 否则这句话在说
                                //   "空白的小时表示三星健康在该时段没有采样"，而这批柱子
                                //   压根不是三星来的（图上每一根都来自本机测量）。
                                if (fromLocal) {
                                    stringResource(R.string.s_776d85ad)
                                } else {
                                    stringResource(R.string.s_dc9ab112) +
                                        stringResource(R.string.s_0d017e58)
                                },
                                fontSize = 10.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 3) 极值时段 ──
            if (dipHour != null) {
                item {
                    AppCard(title = stringResource(R.string.s_f3b996ce)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.s_26b3ffe8, dipHour.min, dipHour.hour) +
                                    (if (dipHour.sleep) stringResource(R.string.s_d3756673) else stringResource(R.string.s_497a7bc4)),
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }

            // ── 4) 睡眠期血氧（只画睡眠小时）──
            if (sleepHours.isNotEmpty()) {
                item {
                    AppCard(title = stringResource(R.string.s_98155e0f)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${sleepOnlyAvg ?: "--"}%",
                                    fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                    color = Spo2SleepColor,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.s_81094311, dash(sleepOnlyMin)) +
                                        (night?.spo2Avg?.let { stringResource(R.string.s_d82ed4a2, it) } ?: ""),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            HourRangeChart(
                                slots = sleepSlots,
                                hours = byHour,
                                bounds = bounds,
                                labelEvery = 1,
                                sleepDiff = false,
                                awakeColor = Spo2SleepColor,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.s_6362e7aa) +
                                    stringResource(R.string.s_e5b8f971),
                                fontSize = 10.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            } else if (night != null) {
                item {
                    AppCard(title = stringResource(R.string.s_98155e0f)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.s_d36be231),
                                fontSize = 13.sp, color = Color(0xFFFFA94D),
                            )
                        }
                    }
                }
            }

            // ── 分区：数据口径与记录入口 ──
            //
            // ⚠ 口径卡**排在这里**（原编号 5，却排在"全天走势"后面）而不是最后：
            // 它讲的是"这张图的血氧是怎么来的"，紧跟图区更合乎阅读顺序；
            // 真正的收尾是下面两行记录入口。分区线只画在「数据口径」之前，
            // 两张入口行**共用这一条**，不再另切。
            //
            // ⚠ 2026-10-10：原先这里还有一张「原始光信号」整卡，已撤（用户要求）——
            //   它跟"血氧"不是一类东西，留在这一页看着莫名其妙。现在只留一行入口，
            //   卡片本体搬到了「光信号记录」页。数据与采集都没删。
            item { SectionHeader(stringResource(R.string.s_b1d4b05b)) }

            // ── 5) 数据口径 ──
            item {
                AppCard(title = stringResource(R.string.s_b1d4b05b)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            when {
                                hours.isNotEmpty() -> stringResource(R.string.s_31b5ce5b)
                                records.isNotEmpty() -> stringResource(R.string.s_b4734435)
                                else -> stringResource(R.string.s_0d59347b)
                            },
                            fontSize = 12.sp,
                        )
                        if (hours.isEmpty() && (samsungUnreadable || samsungGranted != true)) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                // ⚠⚠ 连不上**不许**说成"未授权"（2026-10-11）：三页口径必须一致，
                                //   否则同一台手机在血氧页被告知"去授权"、在睡眠页被告知"连不上"。
                                if (samsungUnreadable) {
                                    // ⚠ 与睡眠页/心率页**同一句**（2026-11-03）
                                    stringResource(R.string.s_861b900f) + "\n" +
                                        stringResource(R.string.s_ed232184)
                                } else {
                                    stringResource(R.string.s_5059d812) +
                                        stringResource(R.string.s_61d6ac71)
                                },
                                fontSize = 11.sp, color = Color(0xFFFFA94D),
                            )
                        }
                    }
                }
            }

            // 原始光信号（模块 #8）：**卡片已撤，只留一行入口**（2026-10-10 用户要求）。
            //
            // ⚠ 数据与采集**都没删**（删了会连带失去呼吸率与 PAT 的那段输入）——
            //   只是不再占血氧页的版面：这一页讲的是"血氧"，而原始光波形是给算法用的
            //   中间品、不是给人看的指标。卡片本体搬到了「光信号记录」页（`RecordsScreen`），
            //   想看"采到了没有、是什么"的人点这一行进去就能看到。
            item {
                EntryRow(
                    stringResource(R.string.s_f6c116e3),
                    stringResource(R.string.s_4d34dbfe, ppg.size),
                    onOpenPpgRecords,
                )
            }

            item { EntryRow(stringResource(R.string.s_f329f8be), stringResource(R.string.s_4d34dbfe, records.size), onOpenRecords) }
            item {
                Text(
                    stringResource(R.string.s_2c5437da),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
        }
    }

    // ═══════════════════════ 体温 ═══════════════════════

    /**
     * 体温页。
     *
     * ## 这一页为什么必须显式区分两种数据
     * 同一个"温度"在这个 App 里有**两套完全不同的量纲**：
     *  ① 手表点检的 `watch_temp` —— 当下腕部皮肤温度的**绝对值**（约 30~36 ℃）；
     *  ② 三星健康的 `samsung_skin` —— 它只有睡眠期才有，而且很可能是**相对个人基线的偏差**（±0.x ℃）。
     *
     * 混进同一条曲线就会画出"体温 0.3 ℃"，而且看起来像个正常的低值、极难一眼识破。
     * 所以这里**画两张独立的图**、各带自己的口径说明，并在概览卡片上分别标注来源。
     * 判定口径的规则集中在 `Temperature.scaleOf()`（纯逻辑，可离线断言）。
     */
    @Composable
    private fun TempTab(
        records: List<TemperatureEntity>,
        night: SamsungHealthReader.SkinTempNight?,
        samsungGranted: Boolean?,
        samsungSkinTempOk: Boolean?,
        loading: Boolean,
        onOpenRecords: () -> Unit,
        /**
         * 选中哪天（`epochDay`，**本地日**）。2026-10-15 加历史：与心率/血氧同一个体验 ——
         * 标题行右侧「今天 ▾」选哪一天，这一页就算哪一天；`0L` 由调用方折算成今天。
         */
        selectedDay: Long,
    ) {
        val lang = rememberLang()
        // 点检 = 绝对值。**按来源过滤**而不是"按数值大小猜"：
        // 猜法在边界上必然出错（例如某个点检值恰好落在偏差区间里）。
        val points = records.filter { it.source.startsWith(TempSource.WATCH) }
        val nightRows = records.filter { it.source.startsWith(TempSource.SAMSUNG_SKIN) }

        // ⚠⚠ 选中日的本地 0 点（不再写死"今天"）：历史功能要它。
        // ⚠ 用 `LocalDate.ofEpochDay(selectedDay)` 而不是"今天 0 点 + N 天"：
        //   后者在夏令时/跨月时会把窗口算偏，而归日口径必须与「今天」的唯一定义一致。
        val zone = java.time.ZoneId.systemDefault()
        val day0 = java.time.LocalDate.ofEpochDay(selectedDay)
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = day0 + 24 * 3_600_000L

        // 只画**选中日**内的点。跨天的进「最近记录」，
        // 否则一天的曲线会被压成一条竖线（与旧版同一个理由，只是窗口换成选中日）。
        val todayPoints = points.filter { it.measuredAt in day0 until dayEnd }
        val bounds = Temperature.bounds(todayPoints.map { it.celsius })

        // ⚠ `maxByOrNull` 而不是 `points.firstOrNull()`：后者取的是**全表最新**那条，
        // 选中历史日时会显示今天的值（"概览讲 10月13日、数字却是今天的"）。
        val latest = todayPoints.maxByOrNull { it.measuredAt }
        // 库里那一行"整晚均值"：live 读取失败时的回退显示。
        // 必须有这个回退 —— 冷启动时 skinTempNight 还是 null（它是内存态），
        // 只认内存储的话卡片会说"三星健康里没有昨晚数据"，而实际上我们早就同步到了。
        val latestNight = nightRows.firstOrNull()
        val todayAvg = todayPoints.takeIf { it.isNotEmpty() }?.map { it.celsius }?.average()?.toFloat()
        val todayMin = todayPoints.minOfOrNull { it.celsius }
        val todayMax = todayPoints.maxOfOrNull { it.celsius }

        // ── 夜间腕温偏移（2026-09-21）──
        // ⚠ 先**按归属日去重**再交给 core：唯一索引是 (source, measuredAt)，而 measuredAt 是
        //   三星报的"睡眠结束时刻"。三星会重新分析睡眠、结束时刻可能挪几分钟 → 同一晚落成两行，
        //   于是那一晚在基线里被算两次（等于偷偷加了权），而用户完全看不出来。
        //   nightRows 已经是 DESC（新的在前），所以同一天遇到的**第一条就是最新的**。
        // 个人基线：近 14 天**本机点检**的中位数（不是全历史 —— 腕温会随季节漂，
        // 用全历史会把"上个月降温"算成"你偏低了"）。不足 14 天也给（至少有 2 个点），
        // 只有 1 个点或没有时返回 null：那时的"基线"就是它自己，差值恒为 0，没有意义。
        val tempBaseline = remember(points, selectedDay) {
            val since = day0 - 14 * 86_400_000L
            val recent = points.filter { it.measuredAt >= since && it.measuredAt < dayEnd }
                .map { it.celsius }
                .sorted()
            when {
                recent.size < 2 -> null
                else -> {
                    val mid = recent.size / 2
                    if (recent.size % 2 == 1) recent[mid]
                    else (recent[mid - 1] + recent[mid]) / 2f
                }
            }
        }

        // ⚠⚠ 只取**选中日及以前**的那些晚：`nightDeviation` 讲的是"最近一晚 vs 基线"，
        //   不过滤的话用户选了 10月13日，卡片还在讲最晚那晚（数字与标题对不上）。
        //   顺带也更诚实 —— 不能用"未来"的夜当参照。
        val nightSeries = remember(nightRows, selectedDay) {
            val byDay = LinkedHashMap<Long, Float>()
            for (r in nightRows) {
                val day = java.time.Instant.ofEpochMilli(r.measuredAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
                if (day > selectedDay) continue
                if (day !in byDay) byDay[day] = r.celsius
            }
            byDay.values.toList()
        }
        val deviation = remember(nightSeries) { Temperature.nightDeviation(nightSeries) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：本机测量（我们采的）──
            //
            // ⚠ 这一页分区**必须按"谁测的、量的什么"切**，不能按"图 / 数字"切 ——
            // 温度在这页有**三套互不相通的量纲**（见类注释）：本机点检腕温绝对值 /
            // 三星夜间相对偏移 / 核心体温估算。把不同量纲的卡放进同一段，
            // 用户会拿它们互相印证，而这正是最危险的地方。
            item { SectionHeader(stringResource(R.string.s_ee6e71a8), isFirstSection = true) }

            // ── 1) 概览 ──
            item {
                AppCard(title = stringResource(R.string.s_47d114b5)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        if (latest != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${Temperature.format(latest.celsius)}℃",
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                    color = tempLevelColor(latest.celsius),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    Temperature.levelLabel(latest.celsius) + stringResource(R.string.s_e723a173),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 7.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                timeFmt.format(Date(latest.measuredAt)) +
                                    latest.ambientC?.let { stringResource(R.string.s_e41c2aa8, Temperature.format(it)) }.orEmpty() +
                                    "  [${sourceLabel(latest.source)}]",
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                            if (todayPoints.size > 1) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_a20c698a, todayPoints.size) +
                                        stringResource(R.string.s_c90b14e0, Temperature.format(todayAvg!!)) +
                                        stringResource(R.string.s_1db037e1, Temperature.format(todayMin!!)) +
                                        stringResource(R.string.s_0d3cd5f8, Temperature.format(todayMax!!)),
                                    fontSize = 12.sp,
                                )
                            }
                        } else {
                            Text(
                                if (loading) stringResource(R.string.s_d78fcd06)
                                else stringResource(R.string.s_fcff5c90) +
                                    stringResource(R.string.s_1edceb53) +
                                    stringResource(R.string.s_6d123adf),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(Disclaimer.skinTemp(lang), fontSize = 11.sp, color = Color(0xFFFFA94D))
                    }
                }
            }

            // ── 1.5) 平均差异（与个人基线对比）──
            //
            // ⚠⚠ 2026-10-15 用户要"类似三星健康那张图"：大字给**与基线的差**、
            //   下面一条折线 + 基线 + 图例。做法上的两点取舍：
            //   ① 基线取**本机点检**的近 14 天中位数（自己的数据，离线也算得出来）；
            //      不拿三星那条"夜间皮肤温度偏差"混进来 —— 那是另一个量纲。
            //   ② 折线画的是**点检偏差**，所以一天通常只有几根 ⇒ 卡片上必须写明"稀疏"，
            //      不能让用户以为手表在连续采集（免费版没有后台自动识别）。
            item {
                AppCard(
                    title = stringResource(R.string.s_bef2dcec),
                    // ⚠ 副标题别丢：机械迁移时它原来是 Row 里的第二个 Text，
                    //   被"只取标题"的规则吃掉了（守卫 `check_temp_page` 立刻抓到）。
                    subtitle = stringResource(R.string.s_b9b497ea),
                ) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        if (tempBaseline == null) {
                            // 基线要攒够 14 天个人点检才有 —— 不说清的话用户会以为坏了
                            Text(
                                stringResource(R.string.s_8e1a31d5) + stringResource(R.string.s_de90eea7),
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                        } else if (todayPoints.isEmpty()) {
                            Text(
                                if (loading) stringResource(R.string.s_d78fcd06)
                                else stringResource(R.string.s_83a9fad3),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                        } else {
                            val diff = (todayAvg!! - tempBaseline).toFloat()
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    Temperature.formatDelta(diff),
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                    color = if (kotlin.math.abs(diff) >= 0.5f) {
                                        TempDeviationColor
                                    } else {
                                        Color(0xFF9FB6C4)
                                    },
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    Temperature.deviationLabel(diff) +
                                        " · " + stringResource(R.string.s_9c75f357, Temperature.format(tempBaseline)),
                                    fontSize = 12.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 7.dp),
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            TempDeviationChart(
                                points = todayPoints.map {
                                    it.measuredAt to (it.celsius - tempBaseline).toFloat()
                                },
                            )
                            Spacer(Modifier.height(4.dp))
                            ChartLegend(
                                listOf(
                                    TempDeviationColor to stringResource(R.string.s_36554a1e),
                                    Color(0xFF9C8CB0) to stringResource(R.string.s_002d83ad),
                                ),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.s_8e1a31d5),
                                fontSize = 10.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 2) 夜间腕温偏移（2026-09-21 新增）──
            //
            // **为什么把它排在「核心体温估算」前面**：核心体温在"戴着手表"这个条件下几乎必然拒答
            // （传感器报的"环境温"就贴在皮肤上、被体温和衣袖污染 → 温差 ≤0 → 模型前提消失），
            // 而这个偏移**任何条件下都算得出来**（只依赖自己的历史），而且这正是三星自己的用法
            // —— 三星健康那一项从来只给"相对个人平均值的偏差"，从不给绝对值。
            //
            // ⚠ 它是**提示**不是判断：「发烧」这个词不许出现在结论里，而且必须写明
            //   "寒战期腕温可能反而偏低"（不写的话用户会推出"腕温不高 = 没发烧"，比不给更危险）。
            //   四条边界文案都在 core 的 Temperature.NIGHT_DEVIATION_* 里，改文案去那里改。
            item {
                AppCard(title = stringResource(R.string.s_28633218)) {
                    Column {
                        Spacer(Modifier.height(8.dp))
                        when (deviation) {
                            is Temperature.DeviationResult.NotApplicable -> {
                                Text(deviation.reason, fontSize = 13.sp, color = AppTextMuted)
                            }
                            is Temperature.DeviationResult.Ok -> {
                                val d = deviation
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        Temperature.formatDelta(d.deviationC),
                                        fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                        color = if (d.notable) TempDeviationColor else Color(0xFF9FB6C4),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        Temperature.deviationLabel(d.deviationC),
                                        fontSize = 13.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_a7a4d190, Temperature.formatByScale(d.latestC)) +
                                        stringResource(R.string.s_60dea771, Temperature.formatByScale(d.baselineC)) +
                                        stringResource(R.string.s_baaa9ae2, d.baselineNights),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        // 四条边界**必须与偏移同时出现**。缺任何一条都可能被误读，
                        // 尤其"寒战期方向相反"那条。默认收起（见 CollapsibleNotes）。
                        CollapsibleNotes(
                            listOf(
                                Temperature.NIGHT_DEVIATION_NOT_FEVER,
                                Temperature.NIGHT_DEVIATION_CHILLS,
                                Disclaimer.nightTempAdvice(lang),
                                Temperature.NIGHT_DEVIATION_HOW,
                            ),
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            // ── 分区：口径与记录入口 ──
            //
            // 温度这页特殊：三套量纲的换算/口径说明**集中在这一张卡**里（不在各卡内），
            // 所以分区标题就取卡自己的标题（「数据口径」），别再起新词。
            // 记录入口与末尾那行小字共用这条线 —— 它们是同一段的收尾。
            item { SectionHeader(stringResource(R.string.s_b1d4b05b)) }

            // ── 5) 数据口径 ──
            item {
                AppCard(title = stringResource(R.string.s_b1d4b05b)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_a2873a2f) +
                                stringResource(R.string.s_bf6bea15) +
                                stringResource(R.string.s_ae105161) +
                                stringResource(R.string.s_21d3c917) +
                                stringResource(R.string.s_f6155b0b),
                            fontSize = 12.sp,
                        )
                        if (samsungGranted != true) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.s_efddb833),
                                fontSize = 11.sp, color = Color(0xFFFFA94D),
                            )
                        }
                    }
                }
            }

            item { EntryRow(stringResource(R.string.s_f329f8be), stringResource(R.string.s_4d34dbfe, records.size), onOpenRecords) }
            item {
                Text(
                    stringResource(R.string.s_b25a18e7),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
        }
    }

    // ───────────────────────── 体成分（BIA） ─────────────────────────

    /**
     * 体成分页：**两种来源分成两张卡**，不合成一条曲线。
     *
     * 为什么不合并：手表 BIA 与三星健康自己的体成分是**两套算法与标定**
     * （电极位置、测量姿态、算法版本都不同）。画在同一条线上会被读成"同一台仪器的趋势"，
     * 于是"换了测量工具"会被解释成"体脂率涨了"。按来源分开，各自给自己的时间与数值。
     *
     * 阻抗只可能来自手表那一边：三星健康的 `BODY_COMPOSITION` 字段表里**没有阻抗**，
     * 所以三星来源的行里阻抗显示 "——" 是正常的，不是没读到。
     */
    @Composable
    private fun BiaTab(
        records: List<BodyCompositionEntity>,
        mfRecords: List<MfBiaEntity>,
        samsungGranted: Boolean?,
        samsungBiaOk: Boolean?,
        samsungProfileOk: Boolean?,
        loading: Boolean,
        onOpenRecords: () -> Unit,
    ) {
        val lang = rememberLang()
        // 按来源分组，而不是"按数值大小猜"：猜法在边界上必然出错（与体温页同一条纪律）
        val watch = records.filter { it.source == BiaSource.WATCH }
        val samsung = records.filter { it.source == BiaSource.SAMSUNG }
        val latestWatch = watch.firstOrNull()
        val latestSamsung = samsung.firstOrNull()

        // 多频阻抗：最近一条；比值与趋势都**现算**（库里只有原始值）
        val mfLatest = mfRecords.firstOrNull()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：手表测的（本页主角）──
            //
            // 第 1 张是体成分结果、第 1.5 张是算它用的**原始阻抗谱** ——
            // 「结果 + 原料」同属一段，中间不画线；真正的分界在"三星那一套"。
            item { SectionHeader(stringResource(R.string.s_4fb29541), isFirstSection = true) }

            // ── 1) 手表最近一次测量（本页的主角）──
            item {
                AppCard(title = stringResource(R.string.s_3b4e84a2)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        if (latestWatch != null) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    biaInt(latestWatch.bodyFatRatio),
                                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB79BFF),
                                )
                                Text(
                                    "%", fontSize = 15.sp, color = Color(0xFFB79BFF),
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    stringResource(R.string.s_66237d8d) + BodyComposition.bodyFatHint(
                                        latestWatch.bodyFatRatio, latestWatch.gender,
                                    ),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 7.dp),
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                timeFmt.format(Date(latestWatch.measuredAt)) +
                                    "  [${sourceLabel(latestWatch.source)}]",
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                            Spacer(Modifier.height(10.dp))
                            BiaMetricRow(
                                stringResource(R.string.s_150818c0), BodyComposition.formatKg(latestWatch.bodyFatMass),
                            )
                            BiaMetricRow(
                                stringResource(R.string.s_e1f929c7), BodyComposition.formatKg(latestWatch.skeletalMuscleMass),
                            )
                            BiaMetricRow(
                                stringResource(R.string.s_e1f90b7f), biaPct(latestWatch.skeletalMuscleRatio),
                            )
                            BiaMetricRow(
                                stringResource(R.string.s_195bd6c0), BodyComposition.formatLiters(latestWatch.totalBodyWater),
                            )
                            BiaMetricRow(
                                stringResource(R.string.s_e9855ae9),
                                BodyComposition.formatKcal(latestWatch.basalMetabolicRate),
                            )
                            BiaMetricRow(
                                stringResource(R.string.s_5a292991), BodyComposition.formatKg(latestWatch.fatFreeMass),
                            )
                            // 阻抗是手表这条独有的（三星健康的字段表里没有），所以单列出来
                            BiaMetricRow(
                                stringResource(R.string.s_004d42c8), BodyComposition.formatOhm(latestWatch.impedanceMagnitude),
                            )
                            BiaMetricRow(
                                stringResource(R.string.s_003d6aba), BodyComposition.formatDegree(latestWatch.impedanceDegree),
                            )
                            // BMI 由资料里的身高体重算得，**不是测量值**，必须标明
                            val bmi = BodyComposition.bmi(
                                latestWatch.weightKg, latestWatch.heightCm,
                            )
                            if (bmi != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "BMI ${BodyComposition.format1(bmi)}（${BodyComposition.bmiLabel(bmi)}）" +
                                        stringResource(R.string.s_51a0f3fd),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                            if (watch.size > 1) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_299e586a, watch.size),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                        } else {
                            Text(
                                if (loading) stringResource(R.string.s_d78fcd06)
                                else stringResource(R.string.s_2dd99001) +
                                    stringResource(R.string.s_a0708ba1) +
                                    stringResource(R.string.s_a490b63b) +
                                    stringResource(R.string.s_6d123adf),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 1.5) 多频阻抗（手表 MF-BIA 采的原始阻抗谱）──
            item {
                AppCard(title = stringResource(R.string.s_f2751474)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        if (mfLatest == null) {
                            Text(
                                stringResource(R.string.s_c6488707) +
                                    stringResource(R.string.s_b01a7cbe),
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                        } else {
                            val res = mfAnalyze(mfLatest)
                            when (res) {
                                is MfBia.Result.NotApplicable -> {
                                    Text(
                                        stringResource(R.string.s_ad74c228, timeFmt.format(Date(mfLatest.measuredAt))),
                                        fontSize = 12.sp, color = MfBiaWarnColor,
                                    )
                                    Text(res.reason, fontSize = 12.sp, color = AppTextMuted)
                                }
                                is MfBia.Result.Ok -> {
                                    Text(
                                        timeFmt.format(Date(mfLatest.measuredAt)) + stringResource(R.string.s_681fd717),
                                        fontSize = 12.sp, color = AppTextMuted,
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    for (pt in res.points) {
                                        BiaMetricRow(
                                            MfBia.Freq.label(pt.khz),
                                            MfBia.formatMagnitude(pt.magnitude) +
                                                stringResource(R.string.s_555028bf) + MfBia.formatPhase(pt.phaseDeg),
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    AppKpi(
                                        value = MfBia.formatRatio(res.ratio),
                                        unit = stringResource(R.string.s_d49aa3f7),
                                        valueColor = MfBiaAccent,
                                    )
                                    // 与自身基线比：阻抗比的绝对水平个体差异很大，只有和自己比才有意义
                                    val base = Hrv.baselineMedian(
                                        mfRecords.drop(1).mapNotNull { r -> mfRatio(r)?.toDouble() }
                                    )
                                    val trend = MfBia.trendOf(res.ratio, base)
                                    if (trend != null) {
                                        Text(
                                            stringResource(R.string.s_6df05f1c, mfRecords.size - 1) +
                                                MfBia.formatRatio(trend.baseMedian.toFloat()) +
                                                stringResource(R.string.s_4fed4295, MfBia.formatDiff(trend.diffPct)) +
                                                MfBia.directionLabel(trend.direction),
                                            fontSize = 12.sp, color = AppTextMuted,
                                        )
                                    } else {
                                        Text(MfBia.BASELINE_NOTE, fontSize = 11.sp, color = AppTextMuted)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    // 4 段说明合并成一个可折叠块（默认收起，2026-09-21）：
                                    // 常驻展开时这段灰字比上面的数据还长，整张卡一半高度都在讲口径。
                                    CollapsibleNotes(
                                        listOf(
                                            MfBia.METHOD_NOTE,
                                            MfBia.WHY_NO_BODY_COMPOSITION,
                                            Disclaimer.phaseSign(lang),
                                            Disclaimer.mfBia(lang),
                                        ),
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 分区：三星健康自己的体成分（独立口径）──
            //
            // ⚠ 这一条线和体温页那条**同一个理由**：两套数字同名同单位（体脂率 %），
            // 来源却不是一回事（手表阻抗 vs 三星自己的设备），混在一段里必然被互相比对。
            // 之后的"为什么需要资料"属于**手表那一段的补充**，本该排在上面，
            // 但它依赖 `samsungProfileOk` —— 位置不动，分隔线也不重画（不切断语义）。
            item { SectionHeader(stringResource(R.string.s_ecb73271)) }

            // ── 2) 三星健康自己的体成分（独立口径，单独一张卡）──
            item {
                AppCard(title = stringResource(R.string.s_80b52e38)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        when {
                            latestSamsung != null -> {
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        biaInt(latestSamsung.bodyFatRatio),
                                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4DD0C4),
                                    )
                                    Text(
                                        "%", fontSize = 14.sp, color = Color(0xFF4DD0C4),
                                        modifier = Modifier.padding(bottom = 5.dp),
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        stringResource(R.string.s_1507fa78),
                                        fontSize = 13.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    timeFmt.format(Date(latestSamsung.measuredAt)),
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                                Spacer(Modifier.height(10.dp))
                                BiaMetricRow(
                                    stringResource(R.string.s_e1f929c7), BodyComposition.formatKg(latestSamsung.skeletalMuscleMass),
                                )
                                BiaMetricRow(
                                    stringResource(R.string.s_e1f90b7f), biaPct(latestSamsung.skeletalMuscleRatio),
                                )
                                BiaMetricRow(
                                    stringResource(R.string.s_195bd6c0), BodyComposition.formatLiters(latestSamsung.totalBodyWater),
                                )
                                BiaMetricRow(
                                    stringResource(R.string.s_e9855ae9),
                                    BodyComposition.formatKcal(latestSamsung.basalMetabolicRate),
                                )
                                BiaMetricRow(
                                    stringResource(R.string.s_00292946), BodyComposition.formatKg(latestSamsung.weightKg),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_2d063500) +
                                        stringResource(R.string.s_bfecc786),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                            samsungBiaOk == false -> Text(
                                stringResource(R.string.s_350bbfbb) +
                                    stringResource(R.string.s_617d2316) +
                                    stringResource(R.string.s_78af0a8f),
                                fontSize = 13.sp, color = Color(0xFFFFA94D),
                            )
                            samsungGranted != true -> Text(
                                stringResource(R.string.s_ea82e42d),
                                fontSize = 13.sp, color = Color(0xFFFFA94D),
                            )
                            loading -> Text(stringResource(R.string.s_00960bd6), fontSize = 13.sp, color = AppTextMuted)
                            else -> Text(
                                stringResource(R.string.s_02afde76) +
                                    stringResource(R.string.s_f4e6e47d),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 3) 手表测量为什么需要"资料" ──
            if (samsungProfileOk != true) {
                item {
                    AppCard(title = stringResource(R.string.s_ae406b76)) {
                        Column {
                            Spacer(Modifier.height(6.dp))
                            CollapsibleNotes(
                            listOf(
                            stringResource(R.string.s_b4fa17c2) +
                            stringResource(R.string.s_c7d75185) +
                            stringResource(R.string.s_fb5697dc) +
                            stringResource(R.string.s_d22722a6) +
                            stringResource(R.string.s_dc9c34c7),
                            )
                        )
                        }
                    }
                }
            }

            // ── 分区：口径与记录入口 ──
            item { SectionHeader(stringResource(R.string.s_b1d4b05b)) }

            // ── 4) 数据口径 ──
            item {
                AppCard(title = stringResource(R.string.s_b1d4b05b)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_8b918f85) +
                                stringResource(R.string.s_229f63b7) +
                                stringResource(R.string.s_1b14fa98) +
                                stringResource(R.string.s_b63646d3) +
                                stringResource(R.string.s_a3fa4fbb) +
                                stringResource(R.string.s_131948b9) +
                                stringResource(R.string.s_b09f53a2),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            item { EntryRow(stringResource(R.string.s_f329f8be), stringResource(R.string.s_4d34dbfe, records.size), onOpenRecords) }
            item {
                Text(
                    stringResource(R.string.s_0813ae42),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
        }
    }

    /** 体成分百分比：null 显示 "——"。**不能**写成 `format1(v) + "%"`，那会得到 "--%"。 */
    private fun biaPct(v: Float?): String =
        if (v == null) "——" else BodyComposition.format1(v) + "%"

    /** 体成分整数位显示（体脂率），null 显示 "——"。 */
    private fun biaInt(v: Float?): String =
        if (v == null) "——" else BodyComposition.format0(v)

    /** 实体 -> 现算的阻抗谱结论（界面与判据同源：都走 `MfBia.analyze`）。 */
    private fun mfAnalyze(e: MfBiaEntity): MfBia.Result = MfBia.analyze(
        MfBia.Input(
            measuredAt = e.measuredAt,
            points = listOf(
                MfBia.Point(MfBia.Freq.K5, e.m5, e.p5),
                MfBia.Point(MfBia.Freq.K10, e.m10, e.p10),
                MfBia.Point(MfBia.Freq.K50, e.m50, e.p50),
                MfBia.Point(MfBia.Freq.K250, e.m250, e.p250),
            ),
            status = e.status,
        )
    )

    /** 只要比值（基线与趋势用）；算不出来就是 null。 */
    private fun mfRatio(e: MfBiaEntity): Float? =
        (mfAnalyze(e) as? MfBia.Result.Ok)?.ratio

    /** 体成分页的一行「标签 …… 数值」。 */
    @Composable
    private fun BiaMetricRow(label: String, value: String) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(label, fontSize = 13.sp, color = AppTextMuted)
            Text(value, fontSize = 13.sp)
        }
    }

    /**
     * 体温折线（带点的散点式折线）。
     *
     * 为什么不复用 [HourRangeChart]（心率/血氧那张柱状图）：体温点检一天只有几次，
     * 按"每小时最低~最高"聚合后大多数小时是空的，整张图看着像"几乎没有数据"，
     * 而实际上每一次测量都是有效信息。所以这里改成把每个点直接画在时间轴上，连成折线。
     *
     * 横轴固定为 [day0] 起的 24 小时（不按数据范围压缩），这样"上午 9 点和下午 5 点各测一次"
     * 在图上确实分居左右，而不是被挤到两头看不出时间关系。
     */
    @Composable
    private fun TempCurve(
        points: List<Pair<Long, Float>>,
        day0: Long,
        bounds: ClosedFloatingPointRange<Float>,
        color: Color,
    ) {
        if (points.isEmpty()) return
        val measurer = rememberTextMeasurer()
        val labelStyle = TextStyle(fontSize = 9.sp, color = Color(0xFF9C8CB0))
        // ⚠ 横轴刻度文案必须在 Canvas 之外取：Canvas 的内容是 DrawScope，不是 @Composable
        //   作用域，在里面调 stringResource 编译不过（改多语言时踩过）。
        val hourLabels = (0..24 step 6).map { hr -> stringResource(R.string.s_dbbf3bfe, hr) }
        Canvas(
            modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 4.dp),
        ) {
            val w = size.width
            val h = size.height
            val leftPad = 34.dp.toPx()
            val bottomPad = 16.dp.toPx()
            val topPad = 8.dp.toPx()
            val plotW = (w - leftPad).coerceAtLeast(1f)
            val plotH = (h - bottomPad - topPad).coerceAtLeast(1f)
            val lo = bounds.start
            val hi = bounds.endInclusive
            val span = (hi - lo).takeIf { it > 0f } ?: 1f

            fun xOf(t: Long): Float =
                (leftPad + ((t - day0).toFloat() / 86_400_000f) * plotW).coerceIn(leftPad, w)

            fun yOf(v: Float): Float =
                // 纵向内边距是必须的：数值等于轴上界时按比例映射会落到 y=0 被裁掉，
                // 整张图看起来就是空的（血氧折线踩过这个坑）。
                (topPad + (1f - (v - lo) / span) * plotH).coerceIn(topPad, topPad + plotH)

            // 纵轴刻度（4 条，量化到整数）
            val ticks = 4
            for (i in 0..ticks) {
                val v = lo + span * (1f - i / ticks.toFloat())
                val y = yOf(v)
                drawLine(
                    color = Color(0x1FFFFFFF),
                    start = Offset(leftPad, y), end = Offset(w, y),
                    strokeWidth = 1f,
                )
                val layout = measurer.measure(
                    Temperature.format(v), style = labelStyle,
                )
                drawText(
                    layout, topLeft = Offset(
                        (leftPad - layout.size.width - 4.dp.toPx()).coerceAtLeast(0f),
                        y - layout.size.height / 2f,
                    ),
                )
            }
            // 横轴刻度：每 6 小时一条
            for (hr in 0..24 step 6) {
                val x = xOf(day0 + hr * 3_600_000L)
                drawLine(
                    color = AppGridLine,
                    start = Offset(x, topPad), end = Offset(x, topPad + plotH),
                    strokeWidth = 1f,
                )
                val layout = measurer.measure(hourLabels[hr / 6], style = labelStyle)
                drawText(
                    layout,
                    topLeft = Offset(
                        (x - layout.size.width / 2f).coerceIn(0f, w - layout.size.width),
                        h - layout.size.height,
                    ),
                )
            }

            // 折线
            var prev: Offset? = null
            for ((t, v) in points) {
                val o = Offset(xOf(t), yOf(v))
                prev?.let {
                    drawLine(color = color, start = it, end = o, strokeWidth = 2.5f,
                        cap = StrokeCap.Round)
                }
                prev = o
            }
            // 点（画在折线之后，保证不被线盖住）
            for ((t, v) in points) {
                drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(xOf(t), yOf(v)))
                drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(xOf(t), yOf(v)))
            }
        }
    }

    // ═══════════════════════ 血压 ═══════════════════════

    /**
     * 血压预估的界面状态。
     *
     * ⚠⚠ **全部现算，一个数都不落库**（沿用项目的派生量铁律）：改算法后历史一起改，
     * 没有 DB 迁移、不进备份。唯一落库的是**输入**（校准配对样本）。
     */
    private data class BpEstimateUi(
        val progress: BpPredict.Progress?,
        val trend: BpPredict.Trend,
        val estimate: BpPredict.Estimate,
        /**
         * **已配对的样本**（时间升序），用于在卡片里逐条展示「哪条袖套读数配上了哪个 PAT」。
         *
         * ⚠ 用户 2026-10-03 反馈「看不到血压与心电的匹配数据，只能自己去心电里打开最新的」——
         *   配对结果原来**只影响那个计数**，界面上看不到任何一条具体的配对关系。
         */
        val samples: List<BpPredict.CalibSample> = emptyList(),
        /**
         * **预估历史**（时刻倒序）。
         *
         * ⚠ 用户 2026-10-03 明确要「每次测心电都留一条可以回看的记录」。
         *   实现是**按需重算**而非落库快照 —— 理由见 [BpPredict.history]：
         *   存快照会在改算法后让新旧两种口径混在同一张表里，而界面无法区分。
         */
        val history: List<BpPredict.HistoryRow> = emptyList(),
        /**
         * **校准覆盖情况**（`null` = 样本还不够，不显示）。
         *
         * ⚠ 显示它的目的**不是"报告用了多少样本"**（那个已有），
         * 而是告诉用户「**哪些现成数据还没被用上**」——
         * 用户缺的不是"去测更多心电"，而是"**把手头已有的心电与血压配上对**"。
         * 实测：他有 15 份心电（PAT 148~235 ms），只有 5 份与血压同时段。
         */
        val coverage: BpPredict.Coverage? = null,
        /**
         * **相对自己基线的偏离**（`null` = 没有可比的基线，或预估不存在）。
         *
         * ⚗⚗ 这是用户 2026-10-03 要的「显示结果为高压」的**替代方案**：
         *   **不给「正常/偏高/偏低」分级**，只给「比你平时高/低 X mmHg」。
         *
         * 理由（技术性的，不是保守）：① 医学诊断阈值**没有唯一标准**，
         * 同一个 112 在国内指南算正常、ACC/AHA 2017 算 1 级高血压；
         * ② **模型的能力就到这里** —— 区间 ±9 mmHg，在 130 判"高血压"、120 判"正常"，
         * 这两个结论之差**完全落在区间内** ⇒ 给分级等于假装这个区分是量出来的。
         */
        val baseline: BpPredict.BaselineCompare? = null,
        /**
         * **校准质量自评**（留一交叉验证，`null` = 样本不够做留出）。
         *
         * ⚗⚗ 2026-10-04 用户问「该怎么测试血压预估准不准」⇒ 这是那个问题的答案：
         * 此前几次「实测偏差只有 ±3」**全是训练集上的数字**（他每次量完血压就录入校准，
         * 那条数据立刻进了训练集 ⇒ **等于考前看过答案**）。
         * 留一交叉验证每次留出 1 组、用其余组重新拟合，给出**样本外**误差。
         *
         * ⚠⚠ **措辞红线**：这**不是临床验证**，只回答「在这个人自己的数据上模型有没有过拟合」。
         * 所以标题只能写「校准质量自评」，
         * **不许**出现「准确度」「精度」这种会被读成临床指标的词，也不给「合格/不合格」判断。
         */
        val accuracy: BpPredict.Accuracy? = null,
        /**
         * **本次拟合用的样本窗口**（新鲜度信息，2026-10-04）。
         *
         * ⚗ 存在的意义是回答用户「我的校准过时了吗、新样本用上了吗」：
         * [BpPredict.Window.stale]（超一个周期）/ [BpPredict.Window.fellBack]（走了回退）。
         */
        val window: BpPredict.Window? = null,
    )

    /**
     * 读出血压预估所需的全部输入并**当场算完**。
     *
     * ⚠ `LaunchedEffect(calibKey)`：只在 [calibKey] 变化时重算。
     * 血压回归要把全部读数与配对样本过一遍，放进重组里每秒重跑是不合适的 ——
     * 但也**不能 `remember` 住结果**（那就变成"存起来的求值结果"，切语言不会重算）。
     *
     * ⚗⚗ [calibKey] 必须覆盖**每一个**输入来源：血压读数条数 · 配对回填 tick · 心电条数。
     *   漏掉任何一个的代价都是**静默的**（界面看着正常，只是新数据不出现）——
     *   2026-10-03 就漏了心电条数，导致"测完心电回到血压页，历史表不动"。
     */
    @Composable
    private fun loadBpEstimate(calibKey: Int): BpEstimateUi? {
        var ui by remember { mutableStateOf<BpEstimateUi?>(null) }
        LaunchedEffect(calibKey) {
            // ⚠⚠ 整段包进 `runCatching`，**不让异常掀掉整个血压页**（2026-10-03 用户实测闪退）。
            //   预估是**附加信息**，而血压记录本身才是用户真正的数据 —— 前者读不出来，
            //   绝不该连后者也看不到。
            // ⚠ 但也**不能静默**：把原因推到顶部事件条。否则"一进血压页就闪退"这类问题
            //   我们只能靠猜，而这一轮已经因猜测耗掉一个来回了。
            runCatching {
                val db = SyncRepository.database
                val readings = db.bloodPressureDao().allOnce()
                    .map { BpPredict.Reading(it.recordedAt, it.systolic, it.diastolic, it.source) }
                // ⚠ BMI 暂不传：它是档案级量，而档案在手表侧，未必有值。
                //   传 null 时 [BpPredict] 会自动少用那一维特征（而不是填补）。
                val samples = BpCalibration.loadSamples(db, bmi = null)
                val latest = BpCalibration.latestInputs(db, bmi = null)
                // 预估历史（2026-10-03 用户要「每次测心电都留一条能回看的」）：
                // 取最近 [BP_HISTORY_ROWS] 次带 PAT 的心电，**用当前样本集**逐条重算。
                // ⚠ 刻意**不设 6 小时时效门限**（latestInputs 有，这里没有）：
                //   回看三周前那次，那次数据就是那一时刻的真值。
                val histInputs = BpCalibration.historyInputs(
                    db, limit = BP_HISTORY_ROWS, bmi = null,
                )
                val now = System.currentTimeMillis()
                // ⚗⚗ 拟合**窗口**（2026-10-04）：优先用最近一个校准周期（30 天）内的样本，
                //   不够则回退到最近的 8 组。理由见 [BpPredict.Window] ——
                //   若不窗口化，用户每 30 天补的 3 组只会**稀释**在几十条老数据里，
                //   模型几乎不动，「定期校准」就成了空话。
                //
                // ⚠⚠ **只算一次、下面全部复用 `fit`** —— 口径漂移是这个项目反复踩的坑
                //   （预估用窗口、自评用全量 ⇒ 两者对不上而用户无从发现）。
                //   ⚠ 但**基线**与**配对列表**刻意**不用**窗口，理由见各自注释。
                val w = BpPredict.window(samples, now)
                val fit = w.fit
                // ⚠ 算**一次**、下面两处复用：界面显示的预估值与基线对比所用的
                //   必须是同一个对象 —— 各算一次的话，将来任何一处改动都会让两者
                //   悄悄对不上（用户看到「比平时高 8」而旁边写的是另一个数）。
                val est = BpPredict.estimate(fit, latest)
                ui = BpEstimateUi(
                    progress = BpPredict.progress(samples, now),
                    window = w,
                    trend = BpPredict.trend(readings, now),
                    estimate = est,
                    // ⚠ **全量**（不是窗口）：用户要看的是"我录过的所有配对"，
                    //   而窗口是"这次拟合用了哪些"——后者已由 [window] 那行说明。
                    samples = samples.sortedByDescending { it.atMs },
                    history = BpPredict.history(fit, histInputs),
                    // 覆盖情况：⚠ **必须用 `fit`** —— 域判据（[BpPredict.Estimate.OutOfDomain]）
                    // 用的就是拟合样本的域，所以"可接受区间"必须按同一批算，
                    // 否则界面显示的域与实际判定用的域不一致。
                    coverage = BpPredict.coverage(
                        fit,
                        BpCalibration.unmatchedEcgPats(db),
                    ),
                    // ⚗ 相对自己基线（替代「高压/正常」分级）。
                    //   ⚠⚠ 这里刻意用**全量** `samples` 而**不是** `fit`：
                    //   「你平时的血压水平」是**长期**概念，不该随校准窗口（30 天）漂动 ——
                    //   否则同一个人隔一个月会看到两个不同的"平时水平"。
                    //   而 `est` 是窗口化拟合的结果，两者本就该是不同的东西。
                    baseline = BpPredict.compareToBaseline(est as? BpPredict.Estimate.Ok, samples),
                    // ⚗ 校准质量自评（留一交叉验证）。⚠ **用 `fit`** ——
                    //   自评的对象必须是**实际生效的那批样本**，
                    //   否则「自评误差」与实际使用的模型对不上。
                    accuracy = BpPredict.crossValidate(fit),
                )
            }.onFailure {
                SyncRepository.setEvent(ResText.raw("血压页读取失败：" + it.toString()))
            }
        }
        return ui
    }

    /**
     * 「用已记录的血压数据补齐配对样本」按钮。
     *
     * ⚠⚠ **为什么必须有它**（2026-10-02 用户实付）：配对原来只在「录血压那一瞬间」
     *   取信号，而用户是**平时**戴着表测血压的 —— 于是绝大多数历史读数永远配不上，
     *   攒很久也攒不够组数，界面只能一直给趋势不给数值。
     *
     * ⚠ 结果提示**必须说清只补上了多少、依据是什么**（"只统计当时同时段有手表数据的那些"），
     *   否则用户会以为「补齐了 = 全部都能用了」。
     */
    @Composable
    private fun BpBackfillButton(onBackfilled: () -> Unit) {
        val scope = rememberCoroutineScope()
        var busy by remember { mutableStateOf(false) }
        AppOutlineButton(
            enabled = !busy,
            onClick = {
                if (busy) return@AppOutlineButton
                busy = true
                scope.launch {
                    val n = BpCalibration.backfill(SyncRepository.database)
                    SyncRepository.setEvent(
                        if (n > 0) ResText.of(R.string.s_d43fdec0, n)
                        else ResText.of(R.string.s_05a8dd91)
                    )
                    onBackfilled()
                    busy = false
                }
            },
        ) {
            Text(
                if (busy) stringResource(R.string.s_9f847eba)
                else stringResource(R.string.s_002dfba2),
                fontSize = 12.sp,
            )
        }
    }

    /**
     * 血压**预估参考**卡。
     *
     * ## 措辞是红线，不只是排版
     *
     * ⚠⚠ 用户明确要求：只能写「血压预估参考」，**不许**写「测量血压 / 精准测血压」，
     *   更不许任何「可诊断高血压」的表述。所以：
     * · 标题用「预估参考」，不用「血压测量」；
     * · **不给「正常/偏高/偏低」分级**（分级是医学判断，`BpPredict` 层面就已经拒绝给）；
     * · 免责 `Disclaimer.bpEstimate` 常驻在卡片里，不是折叠、不是弹窗。
     */
    @Composable
    /**
     * @param onBackfilled 回填完成后通知外层刷新（配对样本数变了、读数条数没变）。
     * ⚠ 这里**没有** `onAddSample`：「补一组校准」那个入口已于 2026-10-05 删除 ——
     *   用户明确说「直接用记录一次血压就行」（顶部那个入口已经够用），
     *   他要的是**重新校准**（清空重来），见 [onRecalibrated]。
     */
    private fun BpEstimateCard(
        calibKey: Int,
        onBackfilled: () -> Unit,
        /**
         * 「重新校准」执行完（清空了配对样本）⇒ 通知外层刷新。
         * ⚠ 与 [onBackfilled] 分开命名而不是复用它：两者都会改配对样本数，
         *   但**语义完全不同**（一个是"补上"，一个是"清空"），
         *   合成一个回调会让将来想区分对待时无从下手。
         */
        onRecalibrated: () -> Unit,
    ) {
        val lang = rememberLang()
        val ui = loadBpEstimate(calibKey)
        // ⚗ 使用说明向导（2026-11-04 用户要求）。
        // ⚠ 用 `rememberSaveable`：这一卡在 LazyColumn 的 item 里，滚出屏幕会被回收；
        //   普通 `remember` 会让"正在看第 3 步"的进度莫名其妙回到第 1 步。
        var showGuide by rememberSaveable { mutableStateOf(false) }
        val guideCtx = LocalContext.current
        // ── 自动弹出的时机（判据全在 `BpGuidePrefs`）──
        // ⚠ key 只取**两个计数**：组数变化（补了样本）或周期内组数变化时才重判。
        //   挂在整个 `ui` 上会每次重组都重跑，而 `markShown` 又改 prefs ——
        //   运气差会走出"弹了又弹"的循环。
        LaunchedEffect(ui?.progress?.totalPairs, ui?.progress?.pairsInCycle) {
            val pr = ui?.progress ?: return@LaunchedEffect
            if (BpGuidePrefs.shouldAutoShow(guideCtx, pr.totalPairs, pr.pairsInCycle)) {
                showGuide = true
                // ⚠ 必须在**真的弹出来之后**才标记，否则这次提醒被自己吞掉。
                BpGuidePrefs.markShown(guideCtx, pr.totalPairs)
            }
        }
        if (showGuide) {
            BpGuideDialog(onDismiss = { showGuide = false })
        }
        // ⚠⚠ 展开状态必须**无条件**定义在函数体开头：写在 `if (ui != null)` 里的话，
        //   条件一旦翻转（重进页面时 ui 短暂为 null）状态会被重置 ——
        //   表现是"点了展开、过一会儿又收起来"，且不报错。
        // ⚠ 用 `rememberSaveable` 而不是 `remember`：这一卡在 LazyColumn 的 item 里，
        //   滚出屏幕会被回收，`remember` 会丢状态。
        var showAllSamples by rememberSaveable { mutableStateOf(false) }
        // ⚗ 「重新校准」的二次确认。⚠ 用 `remember`（不用 Saveable）：
        //   对话框状态**刻意不跨进程重建保留** —— 一个破坏性操作不该在
        //   杀进程重启后还"悬在半空等你点确认"。
        var showRecalibrate by remember { mutableStateOf(false) }
        val recalibScope = rememberCoroutineScope()

        // ⚗⚗ 「重新校准」的二次确认（2026-10-05 用户选择清空旧校准）。
        //
        // ⚠⚠ 这个对话框是**唯一**拦住"误点丢数据"的东西，所以它必须：
        //   ① 说清**具体删多少组**（不是一句"确定吗"）；
        //   ② 说清**删完之后会怎样**（预估停止，直到重新攒够）；
        //   ③ **明确划出不受影响的范围**（血压记录一条不动）——
        //      用户最怕的是"连我量的血压也没了"，不写清楚他会不敢点、或点了才后悔；
        //   ④ 说明**不可撤销**。
        // ⚠ 确认按钮用**警示色**：它的文案是"删除并重新校准"，
        //   视觉上就不该和"取消"长得一样。
        if (showRecalibrate) {
            val n = ui?.samples?.size ?: 0
            AlertDialog(
                onDismissRequest = { showRecalibrate = false },
                title = { Text(stringResource(R.string.s_0e618507)) },
                text = {
                    Text(
                        stringResource(
                            R.string.s_86c6a1e4,
                            n.toString(),
                            BpPredict.MIN_PAIRED_GROUPS.toString(),
                        ),
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    AppOutlineButton(
                        onClick = {
                            showRecalibrate = false
                            recalibScope.launch {
                                // ⚠ **只删 `bp_calibration_samples`**，绝不碰 blood_pressures
                                //   （理由见 DAO 的 deleteAll 注释）。
                                runCatching {
                                    SyncRepository.database
                                        .bpCalibrationSampleDao().deleteAll()
                                }.onSuccess {
                                    SyncRepository.setEvent(
                                        ResText.of(R.string.s_bfcbed29, n.toString())
                                    )
                                    onRecalibrated()
                                }.onFailure {
                                    SyncRepository.setEvent(
                                        ResText.of(R.string.s_cc9fb18b)
                                    )
                                }
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = SettingsWarnColor,
                        ),
                    ) { Text(stringResource(R.string.s_54a8cd5a)) }
                },
                dismissButton = {
                    AppOutlineButton(onClick = { showRecalibrate = false }) {
                        Text(stringResource(R.string.s_002b540a))
                    }
                },
            )
        }

        // ⚗ 2026-11-04：这张卡原来是**裸 `Card`**，容器色取 `surfaceContainerHighest`
        //   （= AppMutedFill），和页面里的白卡不是一套；更糟的是里面的**软底块也是这个色**
        //   ⇒ 实测像素两者都是 #E9E9EE，分组块**完全看不见**。
        //   改成与 `AppCard` 同一组 token（白底 + 1px 描边 + 10dp 圆角），块才浮得出来。
        AppSurface(Modifier.fillMaxWidth()) {
            // ⚠ 间距从 6dp 提到 8dp：项目间距基准是 8pt 网格，6 是唯一一个不贴格的值。
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // ⚗ 卡头改成「标题 + 指南入口」并列（2026-11-04）。
                // ⚠ 入口放这里而不是卡片底部：向导讲的是"这一卡的数字怎么才有"，
                //   看完不懂的人第一眼就想问，不该让他先滑到底。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.s_27e1f6a0),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        stringResource(R.string.s_c5d7eb39),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        // ⚠ 只给文字点击区太窄，加 padding 把热区撑开。
                        modifier = Modifier
                            .clickable { showGuide = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                    )
                }
                if (ui == null) {
                    Text(stringResource(R.string.s_d78fcd06), fontSize = 12.sp, color = AppTextMuted)
                } else {
                    val pr = ui.progress
                    if (pr != null) {
                        // ⚗⚗ 2026-10-04 改：原来这里显示的是「**本月**校准样本 X / 8」，
                        //   而 `estimate` 用**累计**样本 ⇒ 两个口径打架 ——
                        //   用户因此以为"每月必须凑 8 组"，可预估值明明早就在显示了。
                        //   ⇒ 现在把两件事分开：**本轮节奏**（3 次，对齐三星规范）
                        //     与**累计门槛**（8 组，回归的数学下限），各自有名分。
                        // ⚗ 进度条（2026-11-04 用户要求「更符合逻辑和审美」）。
                        // ⚠ 只画「累计 / 门槛」这一条：另一条（本轮 3 次）是**建议节奏**，
                        //   不是门槛 —— 画两根条会让人以为有两个必须同时满足的条件。
                        // ⚠ 模拟器实测（2026-11-04）：6dp + 默认轨道色在**0%**时几乎看不见 ——
                        //   读起来像一条分隔线，看不出"这是进度"。所以加高到 8dp、用圆角、
                        //   轨道色显式取 surfaceVariant（默认轨道在浅色卡片上对比太低）。
                        LinearProgressIndicator(
                            progress = {
                                (pr.totalPairs.toFloat() / pr.needTotal.coerceAtLeast(1))
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            // ⚠ M3 默认会在轨道末端画一个"停靠点"小圆点（0% 时特别像多余的标点）。
                            //   这里关掉：进度条只有「已攒 / 门槛」一条，不需要停靠点。
                            drawStopIndicator = {},
                        )
                        Text(
                            stringResource(
                                R.string.s_6b5029da,
                                pr.pairsInCycle.toString(), pr.needPairs.toString(),
                                pr.totalPairs.toString(), pr.needTotal.toString(),
                            ),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                    }
                    // ⚗ 新鲜度提示（2026-10-04 用户要求）：超期**照旧给数**，但要说清代价。
                    val win = ui.window
                    if (win != null) {
                        if (win.stale) {
                            // ⚠ 用户明确要求：「如果每隔 30 天没有录入新的袖带测量数据，
                            //   可以根据旧样本进行预估，但提示旧样本可能会随身体变化而变化」
                            //   ⇒ 所以这条是**提示**，不是阻止。措辞里也点明"仍照常给预估"。
                            Text(
                                stringResource(R.string.s_7dee8470, win.latestAgeDays.toString()),
                                fontSize = 11.sp, color = SettingsWarnColor,
                            )
                        }
                        if (win.fellBack) {
                            // ⚠ 回退要**明说**：否则用户以为自己新补的样本已经生效了，
                            //   而实际上本次拟合根本没用到（周期内不足 8 组）。
                            Text(
                                stringResource(
                                    R.string.s_c048f166,
                                    BpPredict.CALIB_CYCLE_TARGET.toString(),
                                ),
                                fontSize = 11.sp, color = SettingsWarnColor,
                            )
                        }
                    }
                    // ⚠⚠ **回填按钮是这一卡最要紧的入口**（2026-10-02 用户反馈）：
                    //   用户是平时戴着表测的，录血压那一刻并没有同步测心电 ——
                    //   只靠「当场配对」的话，半年也攒不到 6 组，界面永远只有趋势。
                    //   这个按钮让**已经录过的**血压去找同时段的手表数据。
                    //   ⚠ 只在「样本不足」时露出来：够用了就别引诱用户点它
                    //   （回填样本一律按最低质量档记，理论上不如当场配的准）。
                    when (val e = ui.estimate) {
                        is BpPredict.Estimate.Ok -> {
                            Text(
                                stringResource(R.string.s_2c673e2a, e.systolic, e.systolicHalfWidth),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                stringResource(R.string.s_d97a52bd, e.diastolic, e.diastolicHalfWidth),
                                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            // ⚗ 本次心电**测得**的搏数（2026-10-03 用户要求）。
                            // ⚠⚠ 措辞刻意与上面两行**分开**且标明"心电测得"：
                            //   预估值带区间（推断出来的），这个没有（就是量出来的）。
                            //   若并进同一句式，用户会以为搏数也是估的。
                            // ⚠ 刻意**不预测搏数** —— 心电本来就直接测到它，
                            //   而心率已是回归的输入特征，拿它去估与它同量的东西 = 用 79 估 79。
                            //   推导见 [BpPredict.LatestInputs.measuredPulse]。
                            val pulse = e.measuredPulse
                            if (pulse != null) {
                                Text(
                                    stringResource(R.string.s_f32a02a6, pulse.toString()),
                                    fontSize = 13.sp, color = AppTextMuted,
                                )
                            }
                            // ⚗⚗ 相对**自己基线**的偏离（用户要的「显示结果」的替代方案）
                            //
                            // ⚠⚠ 这里**刻意不给「正常/偏高/偏低」**：
                            //   ① 医学诊断阈值**没有唯一标准** —— 同一个 112，国内指南算正常、
                            //      ACC/AHA 2017 算 1 级高血压。给分级 = 替用户采用某一套标准。
                            //   ② **模型能力就到这里** —— 区间 ±9 mmHg。在 130 判"高血压"、
                            //      120 判"正常"，这两个结论之差**完全落在区间内**
                            //      ⇒ 给分级等于假装这个区分是量出来的。
                            //   ③ "比你平时高 8 mmHg" 是**自比较**，不需要任何外部标准。
                            //
                            // ⚠⚗ 偏离**小于区间半宽时不给结论**，改说"与基线接近" ——
                            //   那时"高"与"低"在测量误差内分不出来，说出来会让人以为分得出来。
                            val bl = ui.baseline
                            if (bl != null) {
                                val d = Math.abs(bl.deltaMmHg)
                                Text(
                                    when {
                                        !bl.isMeaningful -> stringResource(
                                            R.string.s_9dd48283,
                                            bl.baselineSystolic.toString(), d.toString(),
                                        )
                                        bl.deltaMmHg > 0 -> stringResource(
                                            R.string.s_2df356f0,
                                            d.toString(), bl.baselineSystolic.toString(),
                                        )
                                        else -> stringResource(
                                            R.string.s_55a3b11c,
                                            d.toString(), bl.baselineSystolic.toString(),
                                        )
                                    },
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                            }
                            // ⚗ 体位一致性提示（2026-10-05 加）。
                            //
                            // ⚠⚠ **只在 MIXED 时显示** —— 另外三档都**不打扰用户**：
                            //   · SINGLE   = 全记了且同一个体位，体位不可能造成偏倚 ⇒ 没什么可说；
                            //   · UNRECORDED = 一个都没记。**刻意不提示**：用户可能一直坐姿测的，
                            //     只是懒得选。弹一句"你没记体位"是在指责一个可能不存在的问题，
                            //     而且他也没法补（过去的测量改不了）；
                            //   · UNKNOWN = 记了一半，**真的无法判断** ⇒ 更不能下结论。
                            //   ⇒ 只有"**确知混了**"这一档才值得占用界面。
                            //
                            // ⚠ 措辞只说**事实 + 为什么**，**不说"预估值不准"**：
                            //   体位混杂是已知偏倚来源，但它对本条预估的影响**无法定量**
                            //   （我们不知道这次是坐还是站）⇒ 说"所以不准"是我们没依据的结论。
                            //   与「校准质量自评」那条红线同源：给事实，不给结论。
                            if (e.postureConsistency == BpPredict.PostureConsistency.MIXED) {
                                Text(
                                    stringResource(R.string.s_528b2574),
                                    fontSize = 11.sp, color = SettingsWarnColor,
                                )
                            }
                            // ⚠ 三项都摆出来（组数 / 跨度 / 残差）——
                            //   只给一个「置信度：高」是不诚实的，界面要让人自己判断。
                            Text(
                                stringResource(
                                    R.string.s_73b19fdb, e.groups, e.groups,
                                    e.systolic - e.systolicHalfWidth, e.systolic + e.systolicHalfWidth,
                                ) + " · " + stringResource(
                                    R.string.s_1d48f9c8,
                                    // ⚠ 必须**在调用点格式化**：`$sd` 生成的是 `%1$s`（String），
                                    //   直接传 Double 会 toString 成 `15.80066493211691`。
                                    String.format(java.util.Locale.ROOT, "%.1f", e.systolicResidualSdMmHg),
                                ),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                        }
                        // ⚠⚠ 文案必须说「**预估数值**」，不能说「趋势」（2026-10-03 修）。
                        //   原文案是「样本不足，再记 N 次可给出**趋势**」—— 而**趋势本来就是有的**
                        //   （只要 ≥[BpPredict.MIN_TREND_SAMPLES] 条读数，与配对无关，就在这张卡下面
                        //   显示）。于是「样本不足」与「近 N 次读数中位 …」**同屏出现、自相矛盾**，
                        //   用户读成"这个功能只能给趋势，再记 N 次才给趋势"—— 用户的疑问
                        //   「现在只能给趋势吗？不能直接预估吗」正是被这句文案误导出来的。
                        //   ⇒ 缺的是**预估数值**，且必须顺带说清「怎么才算一组」
                        //   （录完袖套血压后**同时段**在手表测心电 —— 只说"再记 N 次"会让用户
                        //   以为记 N 次袖套读数就够了，而没配上的读数**根本不计入**）。
                        // ⚗ 视觉（2026-11-04）：这两句是这张卡里**唯一可行动**的信息
                        //   （还差几组、怎么补），原来只是两行灰字，跟下面的免责一样重。
                        //   收进浅底块后成为一个明确的视线落点 —— 不用加大字号去抢主数字。
                        is BpPredict.Estimate.NotEnoughPairs -> AppSoftBlock {
                            Text(
                                stringResource(R.string.s_4cb68609, e.confidence.need - e.confidence.have),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = AppTextPrimary,
                                lineHeight = 19.sp,
                            )
                            Text(
                                Disclaimer.bpEstimateNotEnough(lang),
                                fontSize = 11.sp, color = AppTextMuted, lineHeight = 16.sp,
                            )
                        }
                        // ⚠ 跨度不足要**说清是为什么**：否则用户只会以为 App 坏了。
                        is BpPredict.Estimate.NoPredictivePower -> Text(
                            stringResource(
                                R.string.s_5a86ad0b,
                                e.systolicSpanMmHg, e.needSysMmHg,
                            ),
                            fontSize = 12.sp, color = SettingsWarnColor,
                        )
                        // ⚠⚠ 必须给出**可执行的下一步**（2026-10-03 修）：预估的输入就是
                        //   「最近 6 小时内那一次心电」的 PAT，所以「没有手表数据」对用户而言
                        //   唯一有效的动作就是**去手表测一次心电**。原文案只说
                        //   「缺少最近一次的手表数据，暂不给出预估」—— 用户不知道
                        //   "手表数据"指什么、更不知道去哪弄，只能干看着。
                        is BpPredict.Estimate.MissingInput -> Text(
                            stringResource(R.string.s_4ce469da),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                        // ⚗⚗⚗ **训练域外：这次不给任何数字**（2026-10-03 用户实付）
                        //
                        // 用户 12 组样本的 PAT 只覆盖 211~226 ms，某次心电的 PAT 在窗口外
                        // ⇒ 外推出 137 mmHg，袖套实测 **116**（偏 +21）。
                        // ⇒ 现在这一刻起明确拒答 —— 外推段的斜率完全由那 15 ms（本身是噪声）决定，
                        //   **再宽的区间也掩盖不了"这个数是编的"**。
                        is BpPredict.Estimate.OutOfDomain -> Column {
                            val acc = e.acceptable.firstOrNull { it.first == "PAT" }?.second
                            Text(
                                if (acc != null) {
                                    // ⚠ 必须给**可接受区间**，否则用户只看到"超出范围"却无从下手。
                                    //   这条文案有两个占位符，分别传区间下界与上界。
                                    stringResource(
                                        R.string.s_1387212d,
                                        acc.first.toInt().toString(),
                                        acc.second.toInt().toString(),
                                    )
                                } else {
                                    // ⚠ 没有区间可报时**只说拒答**，不编一个区间出来。
                                    stringResource(R.string.s_4ce469da)
                                },
                                fontSize = 12.sp, color = SettingsWarnColor,
                            )
                        }
                    }
                    // ⚗ 视觉（2026-11-04）：补配对按钮**移到状态块之后**。
                    //   原来的顺序是「按钮 → 解释为什么还差 N 组」—— 先给动作再给理由，
                    //   用户不知道这个按钮在补什么；现在先读到"还差 8 组、怎么补"，再看到按钮。
                    if (ui.estimate !is BpPredict.Estimate.Ok) {
                        BpBackfillButton(onBackfilled)
                    }
                    // 趋势：只用方向 + 相对自身基线的偏离，**不给分级**。
                    if (ui.trend is BpPredict.Trend.Ok) {
                        val t = ui.trend as BpPredict.Trend.Ok
                        // ⚠⚠ 这里原本复用了 `s_2c673e2a`（「收缩压预估参考 …±… mmHg」）——
                        //   那是**语义错误**：`Trend.halfWidth` 是 MAD 区间半宽，不是预估的残差区间，
                        //   于是「样本不足」与「收缩压预估参考 114 ± 14」会**同屏出现**，
                        //   用户会把它读成"已经有预估结果了"（2026-10-03 用户截图实付）。
                        //   ⇒ 趋势用**自己的**文案，且只给近窗中位数、**刻意不给 ±**。
                        Text(
                            stringResource(R.string.s_f24e877e, t.samples, t.medianSystolic),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
                // ── 校准覆盖提示（2026-10-03）──
                //
                // ⚗⚗ 这一块的措辞是**用真实数据纠正过的**，别按字面直觉改：
                //
                // 我本来要写「缺 PAT 较低/较高的一侧，请在该状态下多测」——
                // 拿用户数据一算，**那个说法是错的**：他有 15 份心电录制（PAT 148~235 ms），
                // 只有 5 份与血压同时段。**低侧 10 份、高侧 2 份，两侧都有大量现成数据**，
                // 只是**没配上对**（配对窗口 ±5 分钟，且要求同段测心电）。
                //
                // ⇒ 所以提示必须说「另有 N 次心电未配上血压」，
                //   **而不是**「请去测更低的 PAT」—— 他早就测过了，说了只会让他白跑。
                if (ui != null) {
                    val cov = ui.coverage
                    if (cov != null && cov.unmatchedTotal > 0) {
                        Spacer(Modifier.height(6.dp))
                        // ⚠ 两侧都有未配对的 ⇒ 不指定方向（说"某一侧"会是假的）。
                        //   只有一侧有才点明方向，因为那时它才是可行动的。
                        val side = when {
                            cov.unmatchedBelow > 0 && cov.unmatchedAbove > 0 -> ""
                            cov.unmatchedBelow > 0 -> stringResource(R.string.s_0049bf57)
                            else -> stringResource(R.string.s_004a0ae1)
                        }
                        Text(
                            if (side.isEmpty()) {
                                // ⚠ 两侧都有 ⇒ 用**不带方向**的那一版。
                                //   指定某一侧会是假的（实测他两侧都有未配对的数据：
                                //   低侧 10 份、高侧 2 份），而谎报方向比不报更糟。
                                stringResource(
                                    R.string.s_f806ace5, cov.patLowMs.toString(),
                                    cov.patHighMs.toString(), cov.groups.toString(),
                                    cov.unmatchedTotal.toString(),
                                )
                            } else {
                                stringResource(
                                    R.string.s_5aab63d9, cov.patLowMs.toString(),
                                    cov.patHighMs.toString(), cov.groups.toString(),
                                    cov.unmatchedTotal.toString(), side,
                                )
                            },
                            fontSize = 11.sp, color = SettingsWarnColor,
                        )
                    }
                }
                // ── 校准质量自评（留一交叉验证，2026-10-04）──
                //
                // ⚗⚗ 用户问「该怎么测试血压预估准不准」⇒ 这就是那个答案。
                //
                // ⚠⚠ **为什么必须用它、而不能用界面已有的「残差 4.1」**：
                //   那个残差是**训练集上的**（拟合时见过那 12 条数据）⇒ 天然乐观。
                //   而用户此前几次「实测偏差只有 ±3」也全是训练集数字 ——
                //   他每次量完血压就录入校准，**那条数据立刻进了训练集 = 考前看过答案**。
                //   留一交叉验证每次留出 1 组、用其余组重新拟合，给出**样本外**误差。
                //   ⚗ 台架实测（虚假关系数据集）：样本外误差 / 训练残差 = **1.05**
                //     （理论 1.066）；而把留一改成"全量拟合"（数据泄漏）后比值掉到 **0.978**。
                //     ⇒ 泄漏会让数字**变好看**，这正是要拦的。
                //
                // ⚠⚠ **措辞红线**：这不是临床验证，只回答「在这人自己的数据上有没有过拟合」。
                //   标题只能写「校准质量自评」，**不许**出现「准确度」「精度」，
                //   也不给「合格/不合格」——那需要一个我们这个 App 结构上给不出的外部验证。
                if (ui != null) {
                    val acc = ui.accuracy
                    if (acc != null) {
                        SectionHeader(stringResource(R.string.s_538aec7a))
                        if (acc.predicted > 0) {
                            Text(
                                stringResource(
                                    R.string.s_7094903f,
                                    // ⚠ 三个都必须 `fmt1`：直接传 Double 会显示
                                    //   `15.80066493211691`（`$x` 生成的是 `%1$s`）。
                                    fmt1(acc.meanErrorSys), fmt1(acc.sdErrorSys), fmt1(acc.maeSys),
                                ),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                            Text(
                                stringResource(
                                    R.string.s_459b7b6f,
                                    acc.withinSys.toString(), acc.predicted.toString(),
                                    (acc.coverageSys * 100).roundToInt().toString(),
                                ),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                        }
                        if (acc.rejected > 0) {
                            // 拒答率用警示色：拿不到数也是一种"不准"，
                            // 而且它比误差更影响日常使用（点开却没有数）。
                            Text(
                                stringResource(R.string.s_f64ef500, acc.rejected.toString()),
                                fontSize = 11.sp, color = SettingsWarnColor,
                            )
                        }
                        if (acc.predicted == 0) {
                            Text(
                                stringResource(R.string.s_71a56aeb, acc.groups.toString()),
                                fontSize = 11.sp, color = SettingsWarnColor,
                            )
                        }
                        Text(
                            stringResource(R.string.s_778a9ecd),
                            fontSize = 10.sp, color = AppTextMuted,
                        )
                    }
                }
                // ── 已配对的样本逐条列出（2026-10-03 用户反馈）──
                //
                // ⚠ 为什么要有这块：配对结果原来**只影响那个计数**，
                //   界面上看不到任何一条具体的配对关系 —— 用户只能自己去心电页
                //   翻「最新的那次」，还得自己猜它跟哪条血压对上了。
                //   这里把「哪条袖套读数 ↔ 哪个 PAT」直接摆出来。
                //
                // ⚠ 只列**已配对**的（`samples` 全部来自 `bp_calibration_samples`）：
                //   未配对的读数**不在这里**出现 —— 它们不是"配对失败"，
                //   而是"当时没有同时段的手表数据"，混进来会让这张表看起来像错误清单。
                if (ui != null) {
                    val sm = ui.samples
                    if (sm.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.s_025691b7, sm.size))
                        val fmt = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }
                        // ⚠⚗ 默认只显示**最近 5 组**（这一卡已经很长），
                        //   但**必须能展开看全部** —— 2026-10-04 用户反馈
                        //   「已配对的校准样本看不了全部的」，而他要看的正是
                        //   全部 20 组的 **PAT 分布**（判断特征变异够不够）。
                        //   原实现是硬编码 `take(5)`，没有出口 ⇒ 等于把数据藏起来了。
                        // ⚠⚠ 这里刻意用语言级 `for` 而不是 `forEach`：lambda 里调用
                        //   `@Composable`（`stringResource` / `postureLabel`）是**踩过的坑**
                        //   （与「`joinToString` 里不能调 `@Composable`」是同一类问题）。
                        //   `for` 不是 lambda，作用域确定，没有任何歧义。
                        val shownSamples = if (showAllSamples) sm else sm.take(5)
                        for (c in shownSamples) {
                            val line = stringResource(
                                R.string.s_fe8c30a0,
                                fmt.format(Date(c.atMs)),
                                // ⚠⚠ **收缩压与舒张压必须是两个参数**：这条文案是
                                //   「$time · 袖套 $sys/$dia · PAT $pat ms」＝ **4 个占位符**。
                                //   原来把两个数拼成一个串只传了 3 个 ⇒ 运行时抛
                                //   `MissingFormatArgumentException: Format specifier '%4$s'`
                                //   （2026-10-03 用户崩溃栈的真凶）。
                                c.systolic.toString(),
                                c.diastolic.toString(),
                                // ⚠ PAT 可能是 null（当时心电算出 PAT 失败）——
                                //   那种行**仍然要显示**，因为它的读数已经进了回归，
                                //   只是少了一维特征。写成「—」而不是隐藏该行。
                                c.patMs?.toString() ?: "—",
                            ) +
                                // 测量姿势（2026-10-03）：让用户**看得见**哪些样本记了姿势 ——
                                // 姿势靠他每次手选，界面上从不回显的话他没法发现自己漏选了。
                                // 老数据 / 没选的显示「未记录」（**不显示成"坐姿"**：
                                // 那会把"没记"伪装成一个真实体位，而分层的前提正是体位确定）。
                                " · " + postureLabel(c.posture)
                            Text(line, fontSize = 11.sp, color = AppTextMuted)
                        }
                        // ⚗ 展开/收起入口（2026-10-04）。⚠ 只在**真有多于 5 组**时出现 ——
                        //   3 组还显示「展开全部 3 组」是纯噪音（而且点了没有变化）。
                        if (sm.size > 5) {
                            AppOutlineButton(
                                onClick = { showAllSamples = !showAllSamples },
                                modifier = Modifier.padding(top = 2.dp),
                            ) {
                                Text(
                                    if (showAllSamples) stringResource(R.string.s_00345819)
                                    else stringResource(R.string.s_c6e129c3, sm.size.toString()),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        // ⚗⚗ 「重新校准」（2026-10-05 用户选择：清空旧校准、重新开始）。
                        //
                        // ⚠⚠ 这是**破坏性操作** ⇒ 放在分区末尾（不占主位）+ 警示配色 +
                        //    **二次确认对话框说清后果**。三点缺一，用户就可能在误点后
                        //    丢掉几十组样本 —— 而那是他花了很多次测量才攒下的。
                        // ⚠ 放在「配对样本」分区里而不是卡片顶部：它与这批样本直接相关，
                        //   而且离顶层入口远一点能降低误触。
                        AppOutlineButton(
                            onClick = { showRecalibrate = true },
                            modifier = Modifier.padding(top = 8.dp),
                            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                contentColor = SettingsWarnColor,
                            ),
                        ) { Text(stringResource(R.string.s_a4432df8)) }
                    } else if (ui.estimate !is BpPredict.Estimate.NotEnoughPairs) {
                        // ⚠ 空态要给**可执行的下一步**，不是一句"暂无数据"。
                        //
                        // ⚠⚠ 但**估算已经是「还差 N 组」时不显示**（2026-11-04 实测后改）：
                        //   上面那个软底块已经把「先录血压、5 分钟内测心电」说完了，
                        //   这里再来一句同义话，等于同一张卡里讲两遍，只会让人以为漏做了步骤。
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_2b95b682),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
                // ── 预估历史（2026-10-03 用户要「每次测心电都留一条能回看的」）──
                //
                // ⚗⚗ 这一块是**现算**的，不是存的快照：每一行都用**当前**校准模型
                //   重算（[BpPredict.history]）。所以改算法后整张表一起变，
                //   不会出现"早期几条是旧模型、后面几条是新模型"却分不出来的情况。
                //
                // ⚠ 刻意放在「已配对样本」**之后**：那一块回答的是"我的血压配上没配上"，
                //   这一块回答的是"当时估出来多少"。顺序别反过来。
                if (ui != null) {
                    val hr = ui.history
                    if (hr.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.s_11594d85, hr.size))
                        Text(
                            stringResource(R.string.s_a1a00a11),
                            fontSize = 10.sp, color = AppTextMuted,
                        )
                        val hfmt = remember {
                            java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                        }
                        // ⚠⚠ 用语言级 `for` 而不是 `forEach`：lambda 里调 `stringResource`
                        //   是**踩过的坑**（与「`joinToString` 里不能调 `@Composable`」同类）。
                        for (row in hr) {
                            val e = row.estimate
                            Text(
                                if (e != null) {
                                    // ⚠ 区间**照给**（历史不比当前那条宽松）：
                                    //   用户回看历史正是为了比较"那天估的是多少、准不准"，
                                    //   只给中心值会让那些点看起来比实际更可信。
                                    hfmt.format(Date(row.atMs)) + " · " +
                                        "${e.systolic} ±${e.systolicHalfWidth} / " +
                                        "${e.diastolic} ±${e.diastolicHalfWidth} mmHg"
                                } else {
                                    // ⚠ 算不出**要说清是哪一种**，两者指引完全相反：
                                    //   域外 → 要在这个状态下多攒样本（重测一次没用）；
                                    //   其他 → 重测一次心电即可。
                                    // ⚗ 混成一句话会让用户反复重测却永远不出数
                                    //   （实测：他那几行 137 全部是域外）。
                                    hfmt.format(Date(row.atMs)) + " · " + stringResource(
                                        if (row.outOfDomain != null) R.string.s_56f393ed
                                        else R.string.s_1d6470de
                                    )
                                },
                                fontSize = 11.sp,
                                color = if (e != null) AppTextMuted else SettingsWarnColor,
                            )
                        }
                    } else if (ui.samples.isNotEmpty()) {
                        // ⚠ 空态给**可执行的下一步**："测一次心电就能看到"，不是"暂无数据"。
                        //
                        // ⚠⚠ 但**样本也为空时不显示这一句**（2026-11-04 模拟器实测后改）：
                        //   那时上面「还没有配成对的记录：录一次血压后…测一次心电」已经把
                        //   同一件事说完了，两句灰字上下叠着，读起来像"有两件事要做"。
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_9c8694c8),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
                // ⚗ 视觉（2026-11-04）：免责从"正文的最后两行"改成**卡片脚注**。
                //   ⚠ 合规要求它必须**常驻可见**（不折叠、不进弹窗），所以只改排版不动内容：
                //   一条发丝线与正文分开、行高放到 16、灰色退到 AppTextMuted ——
                //   该被看到，但不该和"还差 8 组"抢注意力。
                AppHairline(Modifier.padding(top = 12.dp, bottom = 10.dp))
                Text(
                    stringResource(R.string.s_931ce925),
                    fontSize = 11.sp, color = SettingsWarnColor, lineHeight = 16.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    Disclaimer.bpEstimate(lang),
                    fontSize = 11.sp, color = AppTextMuted, lineHeight = 16.sp,
                )
            }
        }
    }

    /**
     * 「最近血压」卡里的一条读数：数值 + 时间/来源 + 删除。
     *
     * ⚠ 删除做成**文字按钮而不是滑动**：手表配对的场景下用户常常一只手操作，
     *   滑动手势在手机上又容易和列表滚动抢事件（这一页整体是 LazyColumn）。
     * ⚠ 颜色用警示色但字重不加粗 —— 它是**可撤销动作的入口**，不是主操作，
     *   抢走"记录一次血压"的视觉权重就本末倒置了。
     */
    @Composable
    private fun BpRecordRow(
        record: BloodPressureEntity,
        onDelete: () -> Unit,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${record.systolic}/${record.diastolic} mmHg",
                    fontSize = 14.sp,
                )
                Text(
                    timeFmt.format(Date(record.recordedAt)) + " · " + sourceLabel(record.source),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
            Text(
                stringResource(R.string.s_002a9cc4),
                fontSize = 12.sp,
                color = SettingsWarnColor,
                // ⚠ 热区靠 padding 撑到 ~48dp 高，否则一行 12sp 文字只有 20px 可点。
                modifier = Modifier
                    .clickable { onDelete() }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            )
        }
    }

    /** 血压页：概览优先（最近一次 + 近 7 天均值），原始记录收进「最近记录」入口。 */
    @Composable
    private fun BpTab(
        records: List<BloodPressureEntity>,
        /**
         * 心电记录条数 —— **只用于重算 key**（见调用处注释）。
         *
         * ⚠ 不是为了显示什么，而是因为「预估历史」的每一行都来自心电记录。
         *   不把它算进 key 的话，用户新测一次心电（但没录血压）之后回到这一页，
         *   读数条数与回填 tick 都没变 ⇒ `LaunchedEffect` 不重跑 ⇒ **历史表纹丝不动**，
         *   而用户刚测的那次根本没出现。这是「重算键漏了输入」的那类静默故障。
         */
        ecgCount: Int,
        onOpenRecords: () -> Unit,
    ) {
        val latest = records.firstOrNull()
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 3_600_000L
        val week = records.filter { it.recordedAt >= weekAgo }
        // 手机端手动录入（2026-09-28）：此前手机端**只能看**血压（三星读来的 / 手表录来的），
        // 自己在手机上量完袖套血压无处可记 —— 空态文案还在把用户赶去手表。
        var showAdd by remember { mutableStateOf(false) }
        /**
         * 录入被**业务限额**挡下时的提示（每天 4 组 / 间隔 30 分钟）。
         *
         * ⚠ 用对话框而不是底部那一行事件：这是一次**被拒绝的保存**，
         *   用户以为记上了但其实没有 —— 必须当面说，不能让他回头才发现。
         */
        var bpBlocked by remember { mutableStateOf<ManualBpOutcome.Blocked?>(null) }
        /**
         * 待删除的那条读数（`null` = 没有待确认的删除）。
         *
         * ⚠ 用 `remember` 而不是 `rememberSaveable`：一个是实体类不能直接存，
         *   更重要的是**破坏性操作不该在杀进程重启后还悬在半空等你点确认**
         *   （与「重新校准」那个对话框同一条理由）。
         */
        var pendingDelete by remember { mutableStateOf<BloodPressureEntity?>(null) }
        /**
         * 刚删掉一条的**就地反馈**（时间戳；`null` = 不显示）。
         *
         * ⚠⚠ 不能只靠 `SyncRepository.setEvent`：那个状态**只在首页渲染**
         *   （「反馈必须就地」）—— 二级页调它等于没有反馈，
         *   用户点完删除只会看到"那一行凭空消失"，心里没底。
         * ⚠ 用时间戳而不是布尔：连续删两条时布尔一直是 true，第二次的提示不会重新计时。
         */
        var deletedAt by remember { mutableStateOf(0L) }
        LaunchedEffect(deletedAt) {
            if (deletedAt > 0L) {
                kotlinx.coroutines.delay(3000)
                deletedAt = 0L
            }
        }
        // ⚠ 回填后**读数条数不变、只有配对样本数变** —— 所以重算的 key 要把两者
        //   都算进去，否则点完「补齐」界面纹丝不动（看起来像按钮坏了）。
        var backfillTick by remember { mutableStateOf(0) }
        val scope = rememberCoroutineScope()
        val ctx = LocalContext.current
        // ⚗⚗ 页面重做（2026-11-04 用户要求「太乱了，按逻辑和审美重排」）。
        //
        // 旧结构是 7 块并列：录入入口 / 预估卡 / 边界声明 / 最近一次 / 近 7 天 / 数据口径 / 最近记录。
        // 其中「最近一次」「近 7 天」「最近记录」讲的是**同一件事**（我量过哪些），
        // 却被拆成三块、中间还夹着一段灰色说明 —— 扫过去就是一串互不相关的卡片。
        //
        // 现在按「做什么 → 结果是什么 → 我录过什么 → 这些数怎么来的」分四块，
        // 一块只回答一个问题。该有的文案（免责、口径、边界）**一条没丢**，只是归位。
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ① 做什么：录入是这一页唯一的主操作，永远在最上面。
            item {
                EntryRow(
                    stringResource(R.string.s_ba999569),
                    stringResource(R.string.s_a3e1efc3),
                    onClick = { showAdd = true },
                )
            }

            // ② 结果：预估卡（内含进度条 + 指南入口 + 常驻免责）。
            item {
                BpEstimateCard(
                    // ⚗ 重算键必须覆盖**全部三个输入来源**：血压读数条数 · 回填 tick ·
                    //   心电条数。少任何一个都会让"新数据来了但界面不动"。
                    //   （心电条数是 2026-10-03 加的：只测心电不录血压时，
                    //     前两项都不变，而「预估历史」恰恰是靠心电驱动的。）
                    calibKey = records.size * 31 + ecgCount * 7 + backfillTick,
                    onBackfilled = { backfillTick++ },
                    // 清空配对样本后同样要让 calibKey 变化，否则界面纹丝不动
                    // （看起来像"点了没反应"—— 与「补齐」那条注释同一个坑）。
                    onRecalibrated = { backfillTick++ },
                )
            }

            // ③ 我录过什么：最近一次 + 近 7 天 + 最近几条（可删）+ 全部记录入口。
            //
            // ⚠ 四件事合成一张卡（2026-11-04）：它们回答的是同一个问题。
            //   原来三张卡并列时，用户要滚三次才明白"哦这些都是我的读数"。
            item {
                AppCard(title = stringResource(R.string.s_f3282aa4)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        if (latest == null) {
                            // ⚠ 这句原来写的是「可在手表上手动记录，或在**「设备」页**从三星健康导入」——
                            //   两处都过时了：① 手机端现在自己就能录（上面那个入口）；
                            //   ② 「设备」页早已拆掉（设置已移到「用户」页）；
                            //   全手机端同型的 10 处已在 2026-09-29 一并改为指向「用户」页的「三星健康」行。
                            //   ⚠ 2026-09-29 起血压也进了 `SYNC_ON_OPEN`，所以"打开这一页会自动读取"
                            //   现在**是真的**（此前不是 —— 血压页不在那个集合里，
                            //   必须在别的页面停留一次才读得到）。
                            Text(
                                stringResource(R.string.s_1d16f837) +
                                    stringResource(R.string.s_a8f762c7) +
                                    stringResource(R.string.s_e0566627),
                                fontSize = 13.sp, color = AppTextMuted,
                            )
                        } else {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    "${latest.systolic}/${latest.diastolic}",
                                    fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "mmHg",
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                                latest.pulse?.let {
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        stringResource(R.string.s_e884439d, it),
                                        fontSize = 13.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                            }
                            // ⚠ 时间行右侧放这一条的「删除」（2026-11-04 模拟器实测后改）：
                            //   原来列表里 records.take(3) 会把大数字这条**再列一遍** ——
                            //   同一个 118/76 同屏出现两次，看着像重复数据。
                            //   现在列表从第 2 条起（`drop(1)`），最新一条的删除挂在摘要行上，
                            //   这样"每条都能删"仍然成立，且不再重复。
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${timeFmt.format(Date(latest.recordedAt))}（${sourceLabel(latest.source)}）",
                                    fontSize = 12.sp, color = AppTextMuted,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.s_002a9cc4),
                                    fontSize = 12.sp,
                                    color = SettingsWarnColor,
                                    modifier = Modifier
                                        .clickable { pendingDelete = latest }
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                )
                            }
                            // ⚗ 删除成功后就地提示 3 秒（理由见 `deletedAt` 的注释）。
                            if (deletedAt > 0L) {
                                Text(
                                    stringResource(R.string.s_18c44046),
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                            }
                            if (week.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                // ⚠ 分区小标题保留「近 7 天」原文案：它是**时间口径**，
                                //   不是装饰 —— 删掉的话下面两个均值就成了来路不明的数字。
                                Text(
                                    stringResource(R.string.s_81e19af9),
                                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(
                                        R.string.s_f63c1c99,
                                        week.map { it.systolic }.average().roundToInt(),
                                    ) + stringResource(
                                        R.string.s_2bd6183a,
                                        week.map { it.diastolic }.average().roundToInt(),
                                        week.size,
                                    ),
                                    fontSize = 13.sp,
                                )
                                Text(
                                    stringResource(
                                        R.string.s_aa1c70f4,
                                        week.minOf { it.systolic }, week.maxOf { it.systolic },
                                    ) + stringResource(
                                        R.string.s_1533ea32,
                                        week.minOf { it.diastolic }, week.maxOf { it.diastolic },
                                    ),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                            // ── 最近几条（可删除，2026-11-04 用户要求）──
                            //
                            // ⚠ 只列 3 条：这一页的主语是"预估"，记录明细有独立入口。
                            //   但要**就地可删** —— 用户说"删了重新记"时，让他先跳页再找那条，
                            //   等于把一次简单操作变成三次。
                            Spacer(Modifier.height(10.dp))
                            // ⚠ `drop(1)`：最新一条已经在大数字那里显示（并带删除入口）了。
                            for (r in records.drop(1).take(3)) {
                                BpRecordRow(
                                    record = r,
                                    onDelete = { pendingDelete = r },
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenRecords() }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(stringResource(R.string.s_f329f8be), fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                Text(
                                    stringResource(R.string.s_4d34dbfe, records.size),
                                    fontSize = 12.sp, color = AppTextMuted,
                                )
                                Text(" ›", fontSize = 16.sp, color = AppTextMuted)
                            }
                        }
                    }
                }
            }

            // ④ 这些数怎么来的：边界声明 + 口径，默认收起。
            //
            // ⚠ 与其它功能页统一：**默认收起**（2026-10-17 用户要求"全部功能的口径都要有展开"）。
            //   内容按原来的 bullet 拆开，别把整段塞成一个字符串 —— 展开后读起来才不糊。
            // ⚠ 原来独立摆在页面中间的「只显示真实测量值」那句话（s_15a17e1f…）**移进这里**：
            //   它本来就是口径的一部分，单独一行灰字夹在卡片之间只是噪音。
            item {
                AppCard(title = stringResource(R.string.s_b1d4b05b)) {
                    Column {
                        Spacer(Modifier.height(6.dp))
                        CollapsibleNotes(
                            listOf(
                                stringResource(R.string.s_15a17e1f) +
                                    stringResource(R.string.s_55407ce1) +
                                    stringResource(R.string.s_560a4732),
                                stringResource(R.string.s_bb3d1059) +
                                    stringResource(R.string.s_fa821080),
                                stringResource(R.string.s_1e66d485) +
                                    stringResource(R.string.s_50d2205e) +
                                    stringResource(R.string.s_0712b073),
                                stringResource(R.string.s_5507a5d7) +
                                    stringResource(R.string.s_085aba11),
                                stringResource(R.string.s_2582f3ef) +
                                    stringResource(R.string.s_f27447da) +
                                    stringResource(R.string.s_c151e5ee),
                                stringResource(R.string.s_b63979fb),
                            ),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        bpBlocked?.let { reason ->
            AlertDialog(
                onDismissRequest = { bpBlocked = null },
                title = {
                    Text(
                        stringResource(
                            if (reason == ManualBpOutcome.Blocked.DAILY_LIMIT) R.string.s_168ca939
                            else R.string.s_cd60c120
                        )
                    )
                },
                text = {
                    Text(
                        stringResource(
                            if (reason == ManualBpOutcome.Blocked.DAILY_LIMIT) R.string.s_615f8b76
                            else R.string.s_dd8212e7
                        ),
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    AppOutlineButton(onClick = { bpBlocked = null }) {
                        Text(stringResource(R.string.s_1faf4f0c))
                    }
                },
            )
        }

        // ── 删除单条记录（2026-11-04 用户要求「删除重新记录」）──
        //
        // ⚠⚠ 必须**二次确认并说清连带后果**：这条读数可能还挂着一条配对样本，
        //   删了等于把那一组校准一起撤掉（预估可能因此掉回"样本不足"）。
        //   只写"确定删除吗"会让用户以为只是从列表里划掉一行。
        // ⚠ 确认按钮用**警示色**，和「取消」视觉上分开。
        pendingDelete?.let { rec ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.s_9917273e)) },
                text = {
                    Text(
                        stringResource(
                            R.string.s_4b837a7d,
                            rec.systolic.toString(),
                            rec.diastolic.toString(),
                            timeFmt.format(Date(rec.recordedAt)),
                        ),
                        fontSize = 13.sp,
                    )
                },
                confirmButton = {
                    AppOutlineButton(
                        onClick = {
                            pendingDelete = null
                            scope.launch {
                                // ⚠ 成功与失败必须分开说：删除失败却说"已删除"，
                                //   用户会以为那条已经没了，之后再看到它只会更困惑。
                                runCatching { SyncRepository.deleteBloodPressure(rec.id) }
                                    .onSuccess {
                                        // ⚠ 就地反馈（见 `deletedAt` 注释）；`setEvent` 保留是因为
                                        //   首页那条也会用到（从别的入口删时才有意义）。
                                        deletedAt = System.currentTimeMillis()
                                        SyncRepository.setEvent(ResText.of(R.string.s_18c44046))
                                    }
                                    .onFailure {
                                        SyncRepository.setEvent(ResText.of(R.string.s_cc9fb18b))
                                    }
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            contentColor = SettingsWarnColor,
                        ),
                    ) { Text(stringResource(R.string.s_002a9cc4)) }
                },
                dismissButton = {
                    AppOutlineButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.s_002b540a))
                    }
                },
            )
        }

        if (showAdd) {
            BpEntryDialog(
                onDismiss = { showAdd = false },
                onSave = { sys, dia, pulse, note, quiet, posture ->
                    showAdd = false
                    scope.launch {
                        val r = SyncRepository.addManualBloodPressure(
                            ctx, sys, dia, pulse, note, quiet, posture,
                        )
                        // ⚠ 被限额挡下 ⇒ 一条都没写进去。这里必须**弹窗**说清楚，
                        //   绝不能让下面那句"已保存"照常出现（那就是假的成功反馈）。
                        if (r.blocked != null) {
                            bpBlocked = r.blocked
                            return@launch
                        }
                        SyncRepository.setEvent(
                            when {
                                // 唯一索引是 (source, recordedAt)：同一秒重复提交会被挡下。
                                // 如实说"没新增"，不假装成功。
                                !r.inserted -> ResText.of(R.string.s_93d66445)
                                r.writeBackNote == null ->
                                    ResText.of(R.string.s_ad5643ff, sys, dia)
                                else -> ResText.of(R.string.s_4a5d9045, sys, dia) + r.writeBackNote
                            } + when (r.calibration) {
                                // ⚠ 配对结果**只加一句**，绝不改写上面那句「已保存」——
                                //   配不配得上腕表数据与这次读数是不是真的无关。
                                BpCalibration.Outcome.Paired, BpCalibration.Outcome.PairedQuiet ->
                                    ResText.raw(" ") + ResText.of(R.string.s_ed0d09bf)
                                // ⚠⚠ 没配上时要给**可执行的下一步**，不能只说「配对成功与否」
                                //   （2026-10-02）：手机端**没有心电传感器**，配对要靠手表的 PAT，
                                //   所以这里唯一能做的就是明确告诉用户「去手表测一次心电」，
                                //   并说清为什么 —— 否则用户只看到一句「没配到」，
                                //   而不知道这影响的是什么。
                                BpCalibration.Outcome.NoSignal, BpCalibration.Outcome.Rejected ->
                                    ResText.raw(" ") + ResText.of(R.string.s_c5dff04c)
                                null -> ResText.EMPTY
                            }
                        )
                    }
                },
            )
        }
    }

    /**
     * 手动录入血压的对话框（手机端，2026-09-28）。
     *
     * ## 校验：同一个函数，不是同一套逻辑写两遍
     *
     * 用的是 `core/Bp.validate` —— 与数据层 `SyncRepository.addManualBloodPressure`
     * 调的是**同一个函数**。数据层仍然会再跑一次（不假设调用方校验过），
     * 但判定逻辑只有一处实现，不会出现"界面放行、库拒绝"这种漂移。
     *
     * ## 非法 = 禁用保存；可疑 = 只提示，**不禁用**
     *
     * `Bp.Check.Invalid` 才禁用保存（目前只有"收缩压 ≤ 舒张压"这一类**定义上不可能**的）；
     * `Bp.Check.Ok(warning)` 里那句提醒**不影响保存** —— 真正异常的血压是可能存在的，
     * 拦掉等于功能在最该用的时候拒绝工作。这两者的区别见 `core/Bp.kt` 的说明。
     */
    /**
     * 体位常量 → 显示串。
     *
     * ⚠ 匹配用的是**常量值本身**（`Bp.Posture.SITTING` 等），**不是翻译后的串** ——
     *   拿显示串去比会在英文/韩文下永远落进 `else`（第 ⑦ 条踩过）。
     */
    @Composable
    private fun postureLabel(p: String?): String = when (p) {
        Bp.Posture.SITTING -> stringResource(R.string.s_002d07ef)
        Bp.Posture.LYING -> stringResource(R.string.s_0030e393)
        Bp.Posture.STANDING -> stringResource(R.string.s_003f57d6)
        else -> stringResource(R.string.s_1b4b78df)
    }

    @Composable
    private fun BpEntryDialog(
        onDismiss: () -> Unit,
        onSave: (
            systolic: Int,
            diastolic: Int,
            pulse: Int?,
            note: String?,
            quiet: Boolean,
            posture: String?,
        ) -> Unit,
    ) {
        val lang = rememberLang()
        var sysText by remember { mutableStateOf("") }
        var diaText by remember { mutableStateOf("") }
        var pulseText by remember { mutableStateOf("") }
        var noteText by remember { mutableStateOf("") }
        // 「安静状态下测量」—— 只影响校准样本的**质量档**，不影响是否保存。
        // ⚠ 默认**不勾**：勾了它就该真的是安静状态，替用户默认勾等于替用户作证。
        var quiet by remember { mutableStateOf(false) }
        // 测量姿势（`Bp.Posture`）。⚠ 默认 **null = 未选**，与上面那个勾同一条理由：
        //   替用户默认成"坐姿"比留空更糟 —— 留空只是缺一格（可识别），
        //   而"躺着测却记成坐姿"会**污染分层**（错误不可识别）。
        //   ⚠ 体位是「过期即永失」的输入（用户事后不记得当时怎么坐），所以宁可缺也不能假。
        var posture by remember { mutableStateOf<String?>(null) }

        // 输入时就只留数字：血压计给的都是整数，让用户根本打不出非法字符，
        // 比事后报错好。脉搏留空是合法的（很多血压计不报脉搏）。
        val digits: (String) -> String = { s -> s.filter { it.isDigit() }.take(3) }

        val sys = sysText.toIntOrNull()
        val dia = diaText.toIntOrNull()
        val pulse = pulseText.toIntOrNull()
        // ⚠ 脉搏"填了但打不成数字"要单独处理：不然它会被当成"留空"而悄悄丢掉，
        //   用户以为记上了脉搏。
        val pulseBad = pulseText.isNotBlank() && pulse == null

        val check: Bp.Check = if (pulseBad) {
            Bp.Check.Invalid(stringResource(R.string.s_0715bbcd))
        } else {
            Bp.validate(sys, dia, pulse)
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.s_ba999569)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(Disclaimer.bpManual(lang), fontSize = 12.sp, color = AppTextMuted)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = sysText,
                        onValueChange = { sysText = digits(it) },
                        label = { Text(stringResource(R.string.s_73370faa)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = diaText,
                        onValueChange = { diaText = digits(it) },
                        label = { Text(stringResource(R.string.s_fc495ef9)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = pulseText,
                        onValueChange = { pulseText = digits(it) },
                        label = { Text(stringResource(R.string.s_d83b5833)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it.take(60) },
                        label = { Text(stringResource(R.string.s_91dc8e41)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    // ── 测量姿势（2026-10-03）──
                    //
                    // 为什么值得多问一句：真机对照实验（卧/坐/站各 5 次，手腕与心脏同高）
                    // 显示坐↔站的 PAT 中位差 **21 ms**，而两组的逐拍离散度只有 3~7 ms
                    // ⇒ 姿势是"PAT 与血压关系时变"里**最大的一块**。
                    // ⚠ 它**不进回归**（不是特征，是分组键）：`PAT = PEP + PTT`，姿势会同时
                    //   改变 PEP 与 PTT，当特征会让模型把 PEP 的效应记成血压的效应。
                    //   所以这里记下来是为了将来**筛选**样本，不是喂给模型。
                    Text(stringResource(R.string.s_cb7468ec), fontSize = 13.sp)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // ⚠ 同样用 `for` 而不是 `forEach`（理由与上面样本列表那处相同）。
                        for (p in Bp.Posture.SELECTABLE) {
                            val on = posture == p
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else Color(0xFFF2F2F2),
                                        RoundedCornerShape(8.dp),
                                    )
                                    // 再点一次取消选择 —— 免得选错了没法回到"未选"。
                                    .clickable { posture = if (on) null else p }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    postureLabel(p),
                                    fontSize = 13.sp,
                                    color = if (on) MaterialTheme.colorScheme.primary else Color(0xFF444444),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.s_c75e348a), fontSize = 11.sp, color = AppTextMuted)
                    Spacer(Modifier.height(10.dp))
                    // ⚠ 校准样本的质量档由这个勾决定，**默认不勾**：
                    //   勾它等于声明「这次是静坐 5 分钟后量的」，替用户默认勾是替用户作证。
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(
                            checked = quiet,
                            onCheckedChange = { quiet = it },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.s_324ed5e0), fontSize = 13.sp)
                            Text(
                                stringResource(R.string.s_474dbe1d),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    when (check) {
                        is Bp.Check.Invalid -> Text(
                            check.reason,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                        )
                        is Bp.Check.Ok -> check.warning?.let {
                            // ⚠ 提醒色不影响保存。用项目里既有的警示橙，别新造一个色。
                            Text(it, fontSize = 12.sp, color = Color(0xFFFFA94D))
                        }
                    }
                }
            },
            confirmButton = {
                AppOutlineButton(
                    onClick = {
                        if (sys != null && dia != null) {
                            onSave(
                                sys, dia, if (pulseBad) null else pulse,
                                noteText.trim().ifEmpty { null }, quiet, posture,
                            )
                        }
                    },
                    enabled = check is Bp.Check.Ok,
                ) { Text(stringResource(R.string.s_0029396f)) }
            },
            dismissButton = { AppOutlineButton(onClick = onDismiss) { Text(stringResource(R.string.s_002b540a)) } },
        )
    }

    // ═══════════════════════ 某一天的心率 / 血氧（2026-10-13）═══════════════════════

    /** 一天里的一条读数（心率 / 血氧拉平成同一种形状，好共用一张卡与一份统计）。 */
    private data class DayReading(
        val atMs: Long,
        /** 主值：心率是 bpm，血氧是百分数。 */
        val value: Double,
        /** 血氧记录自带的伴生心率（心率记录为 null）。 */
        val extraHr: Int?,
        val source: String,
    )

    /**
     * 按日现读三星健康的三种状态（2026-10-13）。
     *
     * ⚠⚠ 「读不到」必须与「那天没有数据」分开说（同 `samsungUnreadable` 那条纪律）：
     *   折成一个空列表，界面就会写「这一天没有记录」，而真相是**连不上 / 没授权** ——
     *   用户会以为"那天就是没数据"，永远不会去做那个能让它出现的动作。
     */
    private sealed interface DayRemote {
        data object Loading : DayRemote
        data class Ok(val hr: List<Pair<Long, Int>>, val spo2: List<Pair<Long, Int>>) : DayRemote
        data object Unreadable : DayRemote

        /**
         * 本地已经有这一天的记录（`daily_vitals`）⇒ **没有去读三星**（2026-10-13）。
         *
         * ⚠ 与 [Ok] 分开是刻意的：界面上要说的话不一样 —— "本机已保存的记录"
         *   与"含三星健康现读数据"是两件事，混成一个状态会让来源说明撒谎。
         */
        data object LocalOnly : DayRemote
    }

    /**
     * 日历上"有数据"的日期（心率 / 血氧用）：**库里那一天的点测记录 ∪ 有睡眠统计的那几晚**。
     *
     * ⚠⚠ 为什么必须把"那几晚"算进来：睡眠期的心率/血氧是三星给的**统计值**，
     *   它们落在 `sleep_nights` 里（`syncSleepDays` 自动补 14 晚），而 `heart_rate` /
     *   `spo2` 两张表**没有对应历史**。不并进来的话，用户在睡眠页能看到 10月6日那一晚，
     *   在心率/血氧的日历上却选不到它 —— 而他正是拿这个来质疑的
     *   （「明明睡眠记录里都还有数据」）。
     *
     * ⚠ 判据要**咬住统计字段本身**（`hrAvg`/`hrMin`、`spo2Avg`/`spo2Min`）：那一晚没存过
     *   心率统计（三星当时没开连续监测）就不该标成"有数据"，否则点进去是空的。
     *
     * ⚠ 这一条规则**只写在这里**：外壳（宫格页那一层给日历喂数据）与按日页（自己那个日历）
     *   都调它 —— 两处各写一份必然漂移，而漂移不报错。
     */
    private fun vitalsDays(
        kind: RecordKind,
        hrList: List<HeartRateEntity>,
        spo2List: List<com.gemini.healthbridge.core.db.SpO2Entity>,
        nights: List<SleepNightEntity>,
        /** `daily_vitals` 里已经落过库的那些天（2026-10-13，DB v22 起）。 */
        localDays: List<Long> = emptyList(),
    ): List<Long> {
        val local = when (kind) {
            RecordKind.HR -> hrList.map { msToEpochDay(it.measuredAt) }
            RecordKind.SPO2 -> spo2List.map { msToEpochDay(it.measuredAt) }
            else -> emptyList()
        }
        val fromNights = nights.filter {
            if (kind == RecordKind.HR) it.hrAvg != null || it.hrMin != null
            else it.spo2Avg != null || it.spo2Min != null
        }.map { it.dateEpochDay }
        // ⚠⚠ 本地**按日桶**（`daily_vitals`）也要算进来：那正是"每天同步落下来的历史"。
        //   漏了它，用户明明有数据却在日历上点不到（这正是用户 2026-10-13 报的那一类）。
        return (local + fromNights + localDays).distinct()
    }

    /**
     * 某一天的心率 / 血氧读数页。
     *
     * 用户要求（2026-10-13）：「血氧 心率使用跟睡眠一样的查看历史按钮」，
     * 随后紧接着又要求：「**根据历史保存记录，做跟今日一样的内容柱形图啊**，
     * 怎么点开以前的历史就变得不一样了，血氧也是」。
     *
     * ⇒ 这一页与今日页**同构**：概览 → 每小时最小/最大**柱形图** → 睡眠期统计 → 当天全部读数。
     *   图的画法、颜色、图例、横轴刻度**全部复用今日页那套**
     *   （[HourRangeChart] / [ChartLegend] / [bucketByHour] / [chartBounds] / `spo2Bounds`），
     *   所以两页画出来是同一张图 —— 只是横轴那一格代表的那一天不同。
     *
     * ## ⚠⚠ 数据来源：本机库 **∪** 三星健康（按需现读）
     *
     * 头一版只读库里的 `heart_rate` / `spo2`，于是用户报：
     * 「为什么切换以前的历史记录就没有对应的更多记录了，明明睡眠记录里都还有数据」。
     *
     * 根因是**那两张表本来就没有历史**：自动同步只覆盖"今天"（血氧更只落 48 小时内的点记录），
     * 手动「补导历史记录」也只导血压与心率。而同一晚的睡眠记录里却有平均/最低心率与血氧
     * （那份在 `sleep_nights`，自动补 14 晚）⇒ 两处对不上。
     *
     * ⇒ **按需现读那一天**（[SamsungHealthReader.readDayVitals]，含睡眠期每分钟的序列），
     *   再与库里已有的记录**合并**：
     *   · 库里有的必须看得见（含手表测的；三星读不到时它就是全部）；
     *   · 三星有的当场读出来 —— 这是"更多记录 / 柱子"的唯一来源；
     *   · ⚠⚠ **读不到要说清原因**，不许装作"这天没有数据"。
     *
     * ⚠ 现读的读数**刻意不落库**：睡眠期血氧一晚 400+ 条，落进 `spo2` 表会把
     *   「最近记录」列表整个刷屏（理由见 [SamsungHealthReader.readDayVitals]）。
     *
     * ⚠ 统计口径：`平均 + 每小时最低~最高 + 条数`，**不做任何分级**（不写"正常/偏高"）——
     *   心率与血氧的单日读数没有我们这个 App 能负责的判定标准。
     */
    @Composable
    private fun DayHistoryScreen(
        kind: RecordKind,
        epochDay: Long,
        hr: List<HeartRateEntity>,
        spo2: List<com.gemini.healthbridge.core.db.SpO2Entity>,
        /** 库里落下的那些晚：日历的"有数据"日期、睡眠时段（给柱子上色）、「睡眠期统计」卡都从它来。 */
        nights: List<SleepNightEntity>,
        onBack: () -> Unit,
        onPickDay: (Long) -> Unit,
    ) {
        val ctx = this@MainActivity
        val dateFmt = rememberShortDateFmt()
        val tsFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
        val today = remember { java.time.LocalDate.now().toEpochDay() }
        var showDateSheet by remember(kind, epochDay) { mutableStateOf(false) }
        // 点了某一格柱子 → 弹那小时的详情（与今日心率页同款交互）
        var pickedDayHour by remember(kind, epochDay) { mutableStateOf<Int?>(null) }

        val isSpo2 = kind == RecordKind.SPO2
        // ⚠ 存储键（`hr`/`spo2`），不是显示名 —— 见 `SamsungHealthReader.VITALS_KIND_*`。
        val kindKey = if (isSpo2) {
            SamsungHealthReader.VITALS_KIND_SPO2
        } else {
            SamsungHealthReader.VITALS_KIND_HR
        }

        // ── ① 先问本地：「每天同步」有没有把这一天落下来（`daily_vitals`，DB v22）──
        //
        // ⚠⚠ 用户 2026-10-13 明确要求：「不该是每天软件跟三星健康同步后数据都保留在了本地，
        //   **以后访问历史都是直接从本地读取**嘛。要是本地也没有数据，再尝试从三星健康读取」。
        //   ⇒ 顺序就是：**本地有 ⇒ 只用本地（不读三星）**；本地没有 ⇒ 才现读。
        //   ⚠「本地有」= 那一行**存在**（哪怕 `sampleCount == 0`，那是"同步过了、那天没有采样"）；
        //     拿"桶是不是空的"当判据会让每个空白的天都被反复现读一遍 —— 白读正是他抱怨的那件事。
        var localDay by remember(kind, epochDay) { mutableStateOf<DailyVitalsEntity?>(null) }
        var localChecked by remember(kind, epochDay) { mutableStateOf(false) }
        LaunchedEffect(kind, epochDay) {
            localChecked = false
            localDay = runCatching {
                SyncRepository.database.dailyVitalsDao().one(epochDay, kindKey)
            }.getOrNull()
            localChecked = true
        }

        // ── ② 本地没有才现读三星（**切日期就重读**）──
        var remote by remember(kind, epochDay) { mutableStateOf<DayRemote>(DayRemote.Loading) }
        LaunchedEffect(kind, epochDay, localChecked, localDay) {
            if (!localChecked) return@LaunchedEffect
            val ld = localDay
            if (ld != null) {
                // 本地已落库 ⇒ **不去读三星**（这正是"从本地读"那条要求）
                remote = DayRemote.LocalOnly
                return@LaunchedEffect
            }
            remote = DayRemote.Loading
            // ⚠ 只读它那一类（见 `readDayVitals` 的 `wantHr`/`wantSpo2`）：
            //   心率页白读一趟血氧，会让打开这一页的等待时间白白翻倍。
            remote = when (
                val r = SamsungHealthReader.readDayVitals(
                    ctx, epochDay,
                    wantHr = kind == RecordKind.HR,
                    wantSpo2 = kind == RecordKind.SPO2,
                )
            ) {
                is SamsungHealthReader.DayVitals.Ok -> DayRemote.Ok(r.hr, r.spo2)
                SamsungHealthReader.DayVitals.Unreadable -> DayRemote.Unreadable
            }
        }

        // 本机库里那一天的点测记录（三星读不到时就是全部）
        val localHr = remember(hr, epochDay) {
            hr.filter { msToEpochDay(it.measuredAt) == epochDay }
                .map { DayReading(it.measuredAt, it.bpm, null, it.source) }
        }
        val localSpo2 = remember(spo2, epochDay) {
            spo2.filter { msToEpochDay(it.measuredAt) == epochDay }
                .map { DayReading(it.measuredAt, it.percent.toDouble(), it.heartRate, it.source) }
        }

        // ⚠ 合并：**库里优先**（它带得出「手表」这类来源），三星那份只补库里没有的时刻。
        //   去重按"60 秒内已有同一条"：同一份数据从两条路进来时，时刻会差几秒。
        val readings = remember(remote, kind, localHr, localSpo2) {
            val out = (if (kind == RecordKind.HR) localHr else localSpo2).toMutableList()
            val taken = out.map { it.atMs }.toMutableList()
            // ⚠ 先取到局部变量再判类型：`remote` 是 `by remember` 的委托属性，
            //   直接在 `when` 分支里用**无法智能转换**（编译期就报 impossible）。
            val r = remote
            val remoteList = if (r is DayRemote.Ok) {
                if (kind == RecordKind.HR) r.hr else r.spo2
            } else emptyList()
            for ((t, v) in remoteList) {
                if (taken.any { kotlin.math.abs(it - t) <= 60_000L }) continue
                taken.add(t)
                out.add(DayReading(t, v.toDouble(), null, SamsungHealthReader.SOURCE_SAMSUNG))
            }
            out.sortedBy { it.atMs }
        }

        // ── 那一天的小时桶（与今日页**同一套**聚合与同一个图表组件）──
        val day0 = remember(epochDay) { epochDayToMs(epochDay) }
        // 那一天涉及的睡眠时段：
        //   ① 本日历日**醒来**的那一晚（`sleep_nights.dateEpochDay` 就是醒来日）；
        //   ② 次日那一晚落在本日历日内的前半段（23:00 之后那段）。
        // ⚠ 没有睡眠会话就传空表 ⇒ 柱子上不了色（`sleepDiff = false`），
        //   **不猜**一个"夜间窗口"：那会把白天的柱子染成睡眠色，画出一张假图。
        val spans = remember(nights, epochDay) {
            buildList {
                nights.firstOrNull { it.dateEpochDay == epochDay }?.let { add(it.startMs to it.endMs) }
                nights.firstOrNull { it.dateEpochDay == epochDay + 1 }?.let { n ->
                    val nextDay0 = epochDayToMs(epochDay + 1)
                    if (n.startMs < nextDay0) add(n.startMs to minOf(n.endMs, nextDay0))
                }
            }
        }
        val points = remember(readings) { readings.map { it.atMs to it.value.roundToInt() } }
        // ⚠⚠ **本地桶优先**（`daily_vitals`）：有本地就用本地，连"逐点→小时"这一步都省了，
        //   而且离线、无授权时也画得出来。本地没有（`localDay == null`）才拿现读/库里的点现算。
        //   ⚠ 睡眠标记**不存库**，在这里按本地 `sleep_nights` 的会话区间现推（口径只有一处）。
        val hours = remember(points, day0, spans, localDay) {
            val ld = localDay
            if (ld != null) {
                Vitals.decodeBuckets(ld.buckets).map { b ->
                    HourStat(
                        hour = b.hour, min = b.min, max = b.max, avg = b.avg,
                        sleep = spans.isNotEmpty() &&
                            overlapsSleep(day0 + b.hour * 3_600_000L, spans),
                    )
                }
            } else {
                bucketByHour(points, day0, spans)
            }
        }
        val byHour = remember(hours) { hours.associateBy { it.hour } }
        // 睡眠小时（给"只画睡眠时段"那张卡用）—— 与今日页同一个判据（`HourStat.sleep`）
        val sleepSlots = remember(hours) { hours.filter { it.sleep }.map { it.hour } }
        val bounds = remember(hours, kind) {
            // ⚠ 心率用 `chartBounds`（保证至少 50 的跨度）、血氧用 `spo2Bounds`
            //   （`Spo2.bounds`，纯函数有离线断言）—— 与今日页**同一对**函数。
            val vals = hours.flatMap { listOf(it.min, it.max) }
            if (kind == RecordKind.SPO2) spo2Bounds(vals) else chartBounds(vals)
        }
        val allMin = hours.minOfOrNull { it.min }
        val allMax = hours.maxOfOrNull { it.max }
        val allAvg = hours.takeIf { it.isNotEmpty() }?.map { it.avg }?.average()?.roundToInt()
        val peak = hours.maxByOrNull { it.max }
        val dip = hours.minByOrNull { it.min }
        val latest = readings.lastOrNull()
        // 那一天在库里有没有对应的"晚"：有就把睡眠期统计一起摆出来 ——
        // 用户正是拿它来质疑的，两处**必须对得上**。
        val night = remember(nights, epochDay) { nights.firstOrNull { it.dateEpochDay == epochDay } }
        // ⚠ 只有**问完了**才敢说"没有"：本地查一次 +（本地没有时）三星读一次，两头都有结论才算查清。
        val settled = localChecked &&
            (localDay != null || remote is DayRemote.Ok || remote is DayRemote.Unreadable)

        if (showDateSheet) {
            HistoryDateSheet(
                days = vitalsDays(kind, hr, spo2, nights),
                selected = epochDay,
                today = today,
                // 心率/血氧没有可分级的东西 ⇒ 一个中性色只表示"那天有数据"。
                tint = { _: Long -> HistoryDayHasDataColor },
                onPick = { day ->
                    showDateSheet = false
                    if (day != epochDay) onPickDay(day)
                },
                onDismiss = { showDateSheet = false },
            )
        }

        Column(Modifier.fillMaxSize()) {
            // ── 头部：返回 + **日期本身就是切换入口**（与历史睡眠详情页同一套）──
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppOutlineButton(onClick = onBack) { Text(stringResource(R.string.s_51a8f8fd)) }
                Spacer(Modifier.width(12.dp))
                HistoryDatePill(dateFmt.format(Date(day0))) {
                    showDateSheet = true
                }
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // ── 0) 这份数据从哪来（读不到时**说清原因**，不装作"没有数据"）──
                item {
                    val ld = localDay
                    val (text, color) = when {
                        // ① 本地命中（含"同步过了但那天没采样"那一行）⇒ 说清这是**本机保存的**
                        ld != null && ld.sampleCount == 0 ->
                            stringResource(R.string.s_bec2c7bf) to AppTextMuted
                        ld != null -> stringResource(R.string.s_59e4e426) to AppTextMuted
                        // ② 本地没有 ⇒ 这一趟是现读来的
                        remote is DayRemote.Unreadable ->
                            stringResource(R.string.s_dd13920e) to Color(0xFFFFA94D)
                        remote is DayRemote.Ok -> stringResource(R.string.s_ece2a01c) to AppTextMuted
                        else -> stringResource(R.string.s_d78fcd06) to AppTextMuted
                    }
                    Text(text, fontSize = 11.sp, color = color)
                }

                // ⚠⚠ 读取中**不许**走空态：v127 的错就是这里 —— 加载态下 `readings` 也为空，
                //   于是屏幕上同时出现「正在读取…」和「这一天没有记录。」，两句话打架。
                //   ⇒ 判据：**空态只在"读完了（Ok 或 Unreadable）且确实没有读数"时出现**。
                if (settled && hours.isEmpty()) {
                    // 理论到不了这里（日历只让点"有数据"的那天），但真到了就如实说 ——
                    // ⚠ 而且要说**两种可能**（本机没记录 / 三星读不到或没采样）：
                    //   只写「这一天没有记录。」会让人以为"那天本来就没数据"，
                    //   而上面那行来源说明往往正写着"三星健康读不到"。
                    item {
                        Text(stringResource(R.string.s_0654ed49), fontSize = 13.sp, color = AppTextMuted)
                    }
                }

                if (hours.isNotEmpty()) {
                    // ── 1) 概览（与今日页同一套排版：大字范围 + 平均 · 有数据的时段数 + 最新一条）──
                    item {
                        AppCard(title = dateFmt.format(Date(day0))) {
                            Column {
                                Spacer(Modifier.height(4.dp))
                                if (isSpo2) {
                                    // 血氧：大字给**平均**（与今日血氧卡同一排版）
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "${allAvg ?: "--"}%",
                                            fontSize = 32.sp, fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Text(
                                            stringResource(
                                                R.string.s_359d7a47, allMin ?: "--", allMax ?: "--",
                                            ),
                                            fontSize = 13.sp, color = AppTextMuted,
                                            modifier = Modifier.padding(bottom = 7.dp),
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        stringResource(R.string.s_de17fbfd, hours.size) +
                                            (latest?.let {
                                                stringResource(
                                                    R.string.s_3431419b,
                                                    tsFmt.format(Date(it.atMs)),
                                                    it.value.roundToInt(),
                                                )
                                            } ?: ""),
                                        fontSize = 12.sp, color = AppTextMuted,
                                    )
                                } else {
                                    // 心率：大字给**范围**（与今日心率卡同一排版）
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            "${allMin ?: "--"}-${allMax ?: "--"}",
                                            fontSize = 30.sp, fontWeight = FontWeight.Bold,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            stringResource(R.string.s_1c0dd13c),
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(bottom = 5.dp),
                                        )
                                    }
                                    Text(
                                        stringResource(R.string.s_63718a14, allAvg ?: "--", hours.size),
                                        fontSize = 13.sp, color = AppTextMuted,
                                    )
                                    latest?.let {
                                        Text(
                                            stringResource(R.string.s_a4c096d2, it.value.roundToInt()) +
                                                tsFmt.format(Date(it.atMs)) +
                                                "（${sourceLabel(it.source)}）",
                                            fontSize = 12.sp, color = AppTextMuted,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── 2) 每小时最小 / 最大（**与今日页同一张图**：同样的槽位、颜色、图例、刻度）──
                    item {
                        AppCard(title = stringResource(if (isSpo2) R.string.s_66671bc0 else R.string.s_acb4c553)) {
                            Column {
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    stringResource(
                                        if (isSpo2) R.string.s_dc9ab112 else R.string.s_abf2af0f,
                                    ),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                                Spacer(Modifier.height(8.dp))
                                HourRangeChart(
                                    slots = (0..23).toList(),
                                    hours = byHour,
                                    bounds = bounds,
                                    labelEvery = 6,
                                    // ⚠ 没有那一晚的睡眠会话时**不上色**（全部同一个颜色），
                                    //   而不是拿"夜间窗口"猜一把 —— 猜出来的睡眠色是假信息。
                                    sleepDiff = spans.isNotEmpty(),
                                    awakeColor = if (isSpo2) Spo2AwakeColor else HrAwakeColor,
                                    sleepColor = if (isSpo2) Spo2SleepColor else HrSleepColor,
                                    onSlot = { pickedDayHour = it },
                                )
                                if (spans.isNotEmpty()) {
                                    Spacer(Modifier.height(6.dp))
                                    ChartLegend(
                                        listOf(
                                            (if (isSpo2) Spo2AwakeColor else HrAwakeColor) to
                                                stringResource(R.string.stage_awake),
                                            (if (isSpo2) Spo2SleepColor else HrSleepColor) to
                                                stringResource(R.string.s_003d8dc3),
                                        ),
                                    )
                                }
                                val pdh = pickedDayHour
                                if (pdh != null) {
                                    val st = byHour[pdh]
                                    if (st != null) {
                                        HourDetailDialog(
                                            hour = pdh, min = st.min, max = st.max, avg = st.avg,
                                            unit = if (isSpo2) "%"
                                            else stringResource(R.string.s_1c0dd13c),
                                            onDismiss = { pickedDayHour = null },
                                        )
                                    }
                                }
                                if (peak != null && dip != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        if (isSpo2) {
                                            stringResource(R.string.s_26b3ffe8, dip.min, dip.hour) +
                                                stringResource(
                                                    if (dip.sleep) R.string.s_d3756673
                                                    else R.string.s_497a7bc4
                                                )
                                        } else {
                                            // ⚠ 模板里不许嵌 ASCII 引号（提取器会抓到半截串），先算好再插
                                            stringResource(
                                                R.string.s_3cca6232, peak.max, "%02d:00".format(peak.hour)
                                            ) + stringResource(
                                                R.string.s_97f0bdb9, dip.min, "%02d:00".format(dip.hour)
                                            )
                                        },
                                        fontSize = 12.sp, color = AppTextMuted,
                                    )
                                }
                            }
                        }
                    }
                }

                // ── 2b) 睡眠时段里的图（只画睡眠小时）—— 与今日页那张同名卡同构 ──
                // ⚠ 今日页有这张（心率「睡眠心率」/ 血氧「睡眠期血氧」），历史日也得有：
                //   用户说的「跟今日一样」指的就是这一整套，不是只有"全天每小时"那一张。
                if (sleepSlots.isNotEmpty()) {
                    val sleepHours = hours.filter { it.sleep }
                    val sleepAvg = sleepHours.map { it.avg }.average().roundToInt()
                    val sleepMin = sleepHours.minOf { it.min }
                    item {
                        AppSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    stringResource(
                                        if (isSpo2) R.string.s_98155e0f else R.string.s_1e75792b
                                    ),
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    Text(
                                        if (isSpo2) "$sleepAvg%" else "$sleepAvg",
                                        fontSize = 28.sp, fontWeight = FontWeight.Bold,
                                        color = if (isSpo2) Spo2SleepColor else HrSleepColor,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        if (isSpo2) stringResource(R.string.s_81094311, dash(sleepMin))
                                        else stringResource(R.string.s_1c0dd13c),
                                        fontSize = 13.sp, color = AppTextMuted,
                                        modifier = Modifier.padding(bottom = 6.dp),
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                HourRangeChart(
                                    slots = sleepSlots,
                                    hours = byHour,
                                    bounds = bounds,
                                    labelEvery = 1,
                                    sleepDiff = false,
                                    awakeColor = if (isSpo2) Spo2SleepColor else HrSleepColor,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_5193b028),
                                    fontSize = 10.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }

                // ── 2c) 睡眠 / 清醒 平均心率对比（**仅心率**：今日血氧页没有这张卡）──
                // ⚠ 与今日心率页共用 `SleepAwakeCompareCard` 一份实现 —— 用户点名的
                //   「历史里没有睡眠/清醒平均心率那张卡」就是它。
                if (!isSpo2 && sleepSlots.isNotEmpty()) {
                    item { SleepAwakeCompareCard(hours) }
                }

                // ── 3) 睡眠期统计（那一晚在库里）──
                // ⚠ 只在**确实存过统计**时出现：`hrAvg`/`hrMin`/`spo2Avg`/`spo2Min` 全空
                //   说明三星当时也没给（没开连续监测），画一张空卡等于编数据。
                if (night != null && listOfNotNull(
                        night.hrAvg, night.hrMin, night.spo2Avg, night.spo2Min
                    ).isNotEmpty()
                ) {
                    item {
                        AppCard(title = stringResource(R.string.s_9810b0e6)) {
                            Column {
                                Spacer(Modifier.height(6.dp))
                                night.hrAvg?.let { StatLine(stringResource(R.string.s_82cbe300, it)) }
                                night.hrMin?.let { StatLine(stringResource(R.string.s_cdf8b91d, it)) }
                                if (night.spo2Avg != null || night.spo2Min != null) {
                                    StatLine(
                                        stringResource(
                                            R.string.s_87785628,
                                            (night.spo2Avg ?: 0).toString(),
                                            dash(night.spo2Min),
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // ⚠ 本地按日桶只有"每小时统计"，**没有逐条读数** —— 这一句必须说，
                //   否则用户看到"有图却没有记录列表"会以为列表坏了。
                if (localDay != null && readings.isEmpty() && hours.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.s_c6afe843)) }
                    item {
                        Text(
                            stringResource(R.string.s_85650929),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                }
                if (readings.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.s_c6afe843)) }
                    item {
                        Text(
                            stringResource(R.string.s_edc42ac7, readings.size.toString()),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    }
                    // ⚠ 必须给 key：不给的话 Compose 按**下标**复用行，
                    //   往列表前部插入一条（换日期/新数据）会让所有行状态错位、整表重组。
                    items(readings, key = { "${it.atMs}:${it.source ?: ""}" }) { r ->
                        AppSurface(Modifier.fillMaxWidth()) {
                            Row(
                                Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(tsFmt.format(Date(r.atMs)), fontSize = 13.sp, color = AppTextMuted)
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    r.value.roundToInt().toString() +
                                        (if (isSpo2) "%"
                                        else " " + stringResource(R.string.s_1c0dd13c)) +
                                        (r.extraHr?.let {
                                            "  ·  $it " + stringResource(R.string.s_1c0dd13c)
                                        } ?: ""),
                                    fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f),
                                )
                                Text("[${sourceLabel(r.source)}]", fontSize = 11.sp, color = AppTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }

    // ═══════════════════════ 最近记录（独立页） ═══════════════════════

    /**
     * 「最近记录」独立页。心率/血压/血氧/体温/体成分共用。
     *
     * 之所以从各标签页里抽出来：原始记录有几十上百条纯文本，直接铺在页面上会把
     * 图表挤到很远处，翻页时要一路划过这些数字。明细属于"按需查看"，不该是默认视图。
     */
    @Composable
    private fun RecordsScreen(
        kind: RecordKind,
        hr: List<HeartRateEntity>,
        bp: List<BloodPressureEntity>,
        spo2: List<com.gemini.healthbridge.core.db.SpO2Entity>,
        temp: List<TemperatureEntity>,
        bia: List<BodyCompositionEntity>,
        activity: List<ActivityEntity>,
        ppg: List<PpgWaveEntity>,
        onBack: () -> Unit,
        /**
         * 最近一次同步的结果（`SyncRepository.event`）。空 = 还没有过同步，这一行不渲染。
         *
         * ⚠⚠ 为什么出口在这里（2026-11-03）：
         *   首页 `SyncStatusStrip` 里那行事件于 **2026-10-22 按用户要求整行删掉**了
         *   （原话：「全是写给开发者看的诊断话术」），但 `SyncRepository.setEvent`
         *   的 8 个调用点**仍在写** —— 于是 `event` 变成一个没有任何消费者的死状态：
         *   用户"测完 → 自动同步 → 界面没有任何确认"，而代码里看起来一切正常。
         *   记录页是**数据的落点**，"刚才同步进来几条"在这里最有意义，
         *   也不占用首页版面（不违背 10-22 的决定）。
         */
        event: ResText,
    ) {
        // ⚠ 类型用**本地 state** 覆盖入参：入参只决定"从哪张卡进来"，
        //   进来之后用户在这一页里直接换类，不用退回再点一次。
        //   ⚠ `remember { mutableStateOf(kind) }` 里的 `kind` 指入参 —— 合法，
        //     之后代码里读到的都是这个 state。
        var kind by remember { mutableStateOf(kind) }
        Column(Modifier.fillMaxSize()) {
            DetailHeader(stringResource(R.string.s_2a9d865c, stringResource(kind.title)), onBack)

            // ⚠ 同步结果（2026-11-03）：有内容才出现，一行小字。
            //   判空用 `isBlankText`（只有 `Raw("")` 算空）—— 用 `isEmpty()` 之类的
            //   字符串判法在这里拿不到值：`event` 是**待求值**的 `ResText`。
            if (!event.isBlankText) {
                Text(
                    resText(event),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    fontSize = 11.sp,
                    color = AppTextSecondary,
                )
            }

            // ── 类型切换（2026-10-15）──
            //
            // ⚠ 只列**给人看的五类**：ACTIVITY / PPG 是"别的指标的上下文"，
            //   模块总表里就写着"无独立入口"—— 放进标签栏等于给它们开了个独立入口，
            //   与既定边界冲突（那条边界有负向断言钉着）。
            // ⚠ 用 `ScrollableTabRow` 的轻量替代：五项 + 搜索框在同一屏，
            //   横向可滚，窄屏也不会挤成一团。
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (k in listOf(
                    RecordKind.HR, RecordKind.BP, RecordKind.SPO2,
                    RecordKind.TEMP, RecordKind.BIA,
                )) {
                    val active = k == kind
                    Box(
                        Modifier
                            .background(
                                if (active) AppTextPrimary else AppMutedFill,
                                RoundedCornerShape(999.dp),
                            )
                            .clickable { kind = k }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            stringResource(k.title),
                            fontSize = 12.sp,
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) Color.White else AppTextSecondary,
                        )
                    }
                }
            }

            val rows: List<String> = when (kind) {
                RecordKind.HR -> hr.map {
                    stringResource(R.string.s_a6c764d6, timeFmt.format(Date(it.measuredAt)), it.bpm.toInt(), sourceLabel(it.source))
                }
                RecordKind.BP -> bp.map {
                    "${timeFmt.format(Date(it.recordedAt))}   ${it.systolic}/${it.diastolic} mmHg" +
                        (it.pulse?.let { p -> stringResource(R.string.s_06c39256, p) } ?: "") + "  [${sourceLabel(it.source)}]"
                }
                RecordKind.SPO2 -> spo2.map {
                    "${timeFmt.format(Date(it.measuredAt))}   ${it.percent}%" +
                        (it.heartRate?.let { h -> "  $h bpm" } ?: "") + "  [${sourceLabel(it.source)}]"
                }
                // 体温：**按口径格式化**（绝对值带单位、偏差带正负号），
                // 否则三星那行 -0.3 会被写成 "-0.3℃" 看着像绝对温度。
                RecordKind.TEMP -> temp.map {
                    "${timeFmt.format(Date(it.measuredAt))}   ${Temperature.formatByScale(it.celsius)}" +
                        (it.ambientC?.let { a -> stringResource(R.string.s_87214169, Temperature.format(a)) } ?: "") +
                        "  [${sourceLabel(it.source)}]"
                }
                // 体成分：只列"能横向比较"的几项。阻抗只在手表来源才有值，
                // 用 `null` 判断而不是"看数值合不合理"，三星那行自然显示 "——"。
                RecordKind.BIA -> bia.map {
                    val parts = mutableListOf<String>()
                    it.bodyFatRatio?.let { v -> parts += stringResource(R.string.s_525fd9f7, BodyComposition.format1(v)) }
                    it.skeletalMuscleMass?.let { v -> parts += stringResource(R.string.s_eb2b984b, BodyComposition.format1(v)) }
                    it.totalBodyWater?.let { v -> parts += stringResource(R.string.s_d9b11e84, BodyComposition.format1(v)) }
                    it.basalMetabolicRate?.let { v -> parts += stringResource(R.string.s_93c783de, BodyComposition.format0(v)) }
                    it.impedanceMagnitude?.let { v -> parts += stringResource(R.string.s_d664cc77, BodyComposition.format0(v)) }
                    val body = if (parts.isEmpty()) stringResource(R.string.s_23ac5286) else parts.joinToString("  ")
                    "${timeFmt.format(Date(it.measuredAt))}   $body  [${sourceLabel(it.source)}]"
                }
                // 活动片段：档位 + 本段统计。**必须同时给出 σ（分级依据）**，
                // 否则用户只看到一个"中度活动"的结论却不知道凭什么 —— 见 ActivityCheck 的不变式。
                RecordKind.ACTIVITY -> activity.map {
                    "${timeFmt.format(Date(it.startedAt))}   ${Activity.label(it.level)}" +
                        "  ${Activity.formatDuration(it.durationMs)}" +
                        stringResource(R.string.s_17c4ead2, Activity.formatStd(it.stdG)) +
                        stringResource(R.string.s_7e40177d, Activity.formatPct(it.stillRatio))
                }
                // PPG 原始光信号：列表页**不解析波形**（一条最多三段、每段 3000 个六位小数，
                // 铺开就是几万个字符），只列摘要 + 一个显式的"已存波形"标记，
                // 让人知道波形确实存下来了、只是没在这里画。
                // 灌注指数走 `Ppg.perfusionIndexFromRaw` 而不是在这里另写一遍 ac/dc×100 ——
                // 同一个公式只该有一处定义，否则界面值与落库值迟早会漂开。
                RecordKind.PPG -> ppg.map {
                    val chans = Ppg.decodeChannels(it.channels).joinToString("/") { c -> Ppg.Channel.label(c) }
                    val waveMark = if (it.greenWave != null) stringResource(R.string.s_e078cac6) else stringResource(R.string.s_1f2eff48)
                    "${timeFmt.format(Date(it.measuredAt))}   " +
                        stringResource(R.string.s_5f835e8b, Ppg.formatDuration(it.durationMs), it.sampleCount) +
                        "PI ${Ppg.formatPi(Ppg.perfusionIndexFromRaw(it.dcGreen, it.acGreenP2P))} · " +
                        "${Ppg.formatRate(it.sampleRateHz)}   [$chans]$waveMark"
                }
            }
            if (rows.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.s_5d41d723, stringResource(kind.title)),
                        fontSize = 14.sp, color = AppTextMuted,
                    )
                }
                return@Column
            }

            // ── 搜索 + 时间范围（2026-10-05 加）──
            //
            // 起因：一页最多 200 条（`recent(200)`），全是「时间 数值 [来源]」的裸文本，
            // 想找"上个月某天那次血压"只能一路滚。数据量一旦上来这一页就没法用了。
            //
            // ⚠⚠ **状态里只存"筛选条件"（纯数字/纯文本输入），绝不存"筛选结果"**。
            //   原因与多语言那条纪律同源：`rows` 里的每一行都是**已求值的本地化串**
            //   （`stringResource` 的结果）。如果把过滤后的 `List<String>` 存进
            //   `remember`，用户切语言后 `rows` 会用新语言重建，而**状态里那份旧的
            //   不会重算** —— 界面就会停在旧语言，且不报错。⇒ 过滤必须在渲染时现做。
            //
            // ⚠ 时间范围也刻意存**天数**（Long）而不是"算好的 cutoff 时间戳"：
            //   存时间戳的话，"近 7 天"会冻在进入页面的那一刻，用户放着不动一夜，
            //   第二天看到的仍是昨天的 7 天窗口。存天数则每次重组都现算，永远是对的。
            var query by remember(kind) { mutableStateOf("") }
            var rangeDays by remember(kind) { mutableStateOf(0L) }   // 0 = 全部

            // 时间范围：`rows` 里没有结构化时间，所以**先按原始实体过滤**再映射成串。
            // ⚠ 这里刻意**不**对 `rows` 做二次过滤 —— 那样就得从字符串里解析时间，
            //   而"从显示串反解数据"是这类界面最经典的一类错误来源（改了格式就静默失效）。
            val cutoffMs = if (rangeDays <= 0L) 0L
            else System.currentTimeMillis() - rangeDays * 86_400_000L

            // 按 kind 各自的**时间字段**过滤（血压是 recordedAt、其余是 measuredAt/startedAt）。
            // ⚠ 用穷尽 `when` 而不是"给每类传一个 lambda"：新增 `RecordKind` 时
            //   编译器会强制在这里补一个分支，不会像 lambda 列表那样静默漏掉一类。
            fun keepHr(t: Long) = t >= cutoffMs
            val shown: List<String> = when (kind) {
                RecordKind.HR -> rows.filterIndexed { i, _ -> keepHr(hr[i].measuredAt) }
                RecordKind.BP -> rows.filterIndexed { i, _ -> keepHr(bp[i].recordedAt) }
                RecordKind.SPO2 -> rows.filterIndexed { i, _ -> keepHr(spo2[i].measuredAt) }
                RecordKind.TEMP -> rows.filterIndexed { i, _ -> keepHr(temp[i].measuredAt) }
                RecordKind.BIA -> rows.filterIndexed { i, _ -> keepHr(bia[i].measuredAt) }
                RecordKind.ACTIVITY -> rows.filterIndexed { i, _ -> keepHr(activity[i].startedAt) }
                RecordKind.PPG -> rows.filterIndexed { i, _ -> keepHr(ppg[i].measuredAt) }
            }.let { list ->
                // ⚠ 搜索是对**已本地化的整行**做子串匹配：好处是"搜任何看得见的东西都能中"
                //   （数值、来源名、时间都能搜），代价是**搜不了没显示在行里的字段**。
                //   这是刻意的取舍 —— 用户想搜的正是他看得见的那些。
                // ⚠ `contains(ignoreCase = true)` 对中文无影响、对来源标签（英文）有用。
                val q = query.trim()
                if (q.isEmpty()) list else list.filter { it.contains(q, ignoreCase = true) }
            }

            // 空态要分两种说清：「本来就是空的」上面已经 return 了，
            // 走到这里的空只可能是"被筛选过滤掉了" —— 得说清是筛没了、并提示怎么恢复，
            // 否则用户会以为"记录丢了"。
            if (shown.isEmpty()) {
                Column(Modifier.fillMaxSize().padding(32.dp)) {
                    RecordsFilterBar(
                        query = query, onQuery = { query = it },
                        rangeDays = rangeDays, onRange = { rangeDays = it },
                    )
                    Text(
                        stringResource(R.string.s_8aa6a681),
                        fontSize = 13.sp, color = AppTextMuted,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                return@Column
            }

            // ⚠⚠⚠ 整页做成**一个** `LazyColumn`（2026-10-15 修 bug）。
            //
            // 原来的结构是：外层 `Column(fillMaxSize)` 里依次摆
            // 「光信号卡 → 筛选条 → 命中数 → `LazyColumn(weight(1f))`」，
            // 于是**只有最后那段列表能滚**，外层自己是死的。
            // PPG 那一类顶部那张卡又特别高（呼吸率块 + 灌注指数 + 一段折叠说明），
            // 固定部分一旦超过屏幕高度，列表被压到 0 高 ⇒
            // **整页纹丝不动**（用户报：「打开原始光信号后页面动不了」）。
            //
            // ⇒ 把卡片/筛选条/命中数一起变成列表的前几个 `item`，整页就是一个可滚的列表。
            // ⚠ 筛选条里有输入框：放在 `item` 里是可用的（惰性布局支持焦点），
            //   但它**不再固定在顶部** —— 滚下去就看不见了。这是"能滚"与"筛选常驻"之间的取舍，
            //   这里选"能滚"：一个点不动的页面是硬伤，而筛选条滚上去还能再滚回来。
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                // ── 「原始光信号」卡片的新落点（2026-10-10）──
                //
                // 它原来挂在血氧页，占一整张卡却和"血氧"不是一回事（血氧是结果，光波形是给
                // 算法用的中间品）。搬到这一页才名正言顺 —— 这一页讲的就是光信号本身。
                // ⚠ `onOpenRecords` **必须传 null**：卡片里那个「查看光信号记录 ›」在这一页上
                //   指向的就是本页，留着就是一个"点了没反应"的入口（这个项目最忌讳那类东西）。
                if (kind == RecordKind.PPG) {
                    item { PpgCard(records = ppg, onOpenRecords = null) }
                    item { Spacer(Modifier.height(8.dp)) }
                }
                item {
                    RecordsFilterBar(
                        query = query, onQuery = { query = it },
                        rangeDays = rangeDays, onRange = { rangeDays = it },
                    )
                }
                item {
                    Text(
                        // ⚠ 有筛选时显示"命中 N 条 / 共 M 条"，让用户知道筛掉了一些 ——
                        //   只显示 N 会让他以为记录变少了。无筛选时保持原来的单句（不改既有观感）。
                        if (shown.size == rows.size) stringResource(R.string.s_a8e75e5d, rows.size)
                        else stringResource(R.string.s_7cf74882, shown.size, rows.size),
                        fontSize = 12.sp, color = AppTextMuted,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                items(shown) { line ->
                    Text(
                        line,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            .fillMaxWidth(),
                    )
                }
            }
        }
    }

    /**
     * 记录列表页的筛选条：一个搜索框 + 一排时间范围胶囊。
     *
     * ## 为什么时间范围用「天数」而不是「预设枚举」
     *
     * 存天数（0 = 全部）而不是 `enum { Week, Month, All }`：
     * 以后想加"近 3 天"只要多一个胶囊，不用改状态类型、不用改过滤逻辑。
     * 而 `0` 表示"全部"是自然的哨兵值 —— 没有"近 0 天"这种需求。
     *
     * ⚠ **这是一个纯展示组件**：它自己**不持有状态**，全部受控于调用方。
     *   原因是过滤条件要跟 `kind` 绑定（切到别的记录类型时应当清空），
     *   而状态放在调用方才能用 `remember(kind)` 做到这一点。
     */
    @Composable
    private fun RecordsFilterBar(
        query: String,
        onQuery: (String) -> Unit,
        rangeDays: Long,
        onRange: (Long) -> Unit,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            // ⚠ 用**全限定名**而不是加 import：本文件其余 6 处 `OutlinedTextField`
            //   都是这么写的（这个组件不在材料的 import 清单里）。
            //   加 import 不冲突，但会让"同一组件两种写法"并存 —— 跟随既有惯例即可。
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.s_0033b776), fontSize = 13.sp)
                },
                trailingIcon = {
                    // 有内容才给清除键 —— 空的时候放一个点了没反应的叉是噪音。
                    if (query.isNotEmpty()) {
                        AppOutlineButton(
                            onClick = { onQuery("") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            Text(stringResource(R.string.s_0038e2f3), fontSize = 12.sp)
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // ⚠ 顺序与 `RANGE_CHOICES` 一致，且**值就是天数**（0 = 全部）。
                for (days in RECORDS_RANGE_CHOICES) {
                    val on = rangeDays == days
                    AppOutlineButton(
                        onClick = { onRange(days) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            if (days <= 0L) stringResource(R.string.s_002a3920)
                            else stringResource(R.string.s_50b77775, days.toInt()),
                            fontSize = 12.sp,
                            // 选中态用主色 + 加粗，未选中保持默认灰。
                            // 刻意**不换组件类型**（都是 OutlinedButton）——
                            // 换类型会让"选中/未选中"看起来像两种不同的东西，
                            // 而它们只是同一个按钮的两种状态。
                            color = if (on) MaterialTheme.colorScheme.primary else AppTextMuted,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    /**
     * 心电列表：手表测量后自动同步过来的结论（点击进入详情看波形）。
     * Health Connect 没有 ECG/心律记录类型，所以心电数据只存在两端本地，
     * 不会写入 Health Connect，也不会出现在三星健康里。
     *
     * 派生卡片（#9 HRV、#12 压力/疲劳、#14 PAT）：它们和心电是**同一次测量**的几个输出
     * （结论 + 逐拍变异 + 由变异折算的评分），分开放在不同页面会让人以为要测三次。
     * ⚠ 但它们排在本页**下半段**（原始记录之后）—— 顺序判据见函数体内那条注释。
     */
    @Composable
    private fun EcgTab(
        records: List<EcgRecordEntity>,
        hrv: List<HrvSessionEntity>,
        temps: List<TemperatureEntity>,
        onOpen: (EcgRecordEntity) -> Unit,
    ) {
        if (records.isEmpty()) {
            Column(
                Modifier.fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.s_40e57a4f),
                    fontSize = 14.sp, color = AppTextMuted,
                )
            }
            return
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── 分区：心电（原始测量）──
            //
            // ⚠⚠ 分区顺序（2026-10-10 用户要求）：**原始心电在上、派生指标在下**。
            //   一进这一页，第一眼该看到的是"我测到的那条心电"，而不是从它推出来的
            //   HRV / 压力 / PAT。此前顺序是反的（派生指标占满首屏，得往下滚才见到记录），
            //   对"打开心电想先看波形/结论"的人是本末倒置。
            //   ⇒ 判据：**先看到测量本身，再看从它推出来的东西**。别再改回去。
            item { SectionHeader(stringResource(R.string.s_003175fe), isFirstSection = true) }

            items(records, key = { "${it.source}:${it.recordedAt}" }) { r ->
                AppSurface(Modifier.fillMaxWidth(), onClick = { onOpen(r) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                // 落库值是中文（判定结果本身），显示名走 EcgNote
                                EcgNote.classLabel(r.classification) ?: r.classification,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = ecgClassColor(r.classification),
                            )
                            Text(
                                timeFmt.format(Date(r.recordedAt)) +
                                    (r.heartRate?.let { stringResource(R.string.s_30a3dfdf, it) } ?: "") +
                                    "   [${sourceLabel(r.source)}]",
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                            // ⚠ `note` 是按**生成那一刻的语言**落的库（老记录多半是中文），
                            //    显示时用 EcgNote.detail 翻成当前语言 —— 不改库、老记录一起受益。
                            EcgNote.detail(r.note)?.let { Text(it, fontSize = 11.sp, color = AppTextMuted) }
                        }
                        // 这次心电有没有伴生的 HRV 记录，在列表行上就给出线索，
                        // 否则用户只能靠"点进去看看"来发现。
                        if (hrvFor(hrv, r.recordedAt) != null) {
                            Text("HRV · ", fontSize = 11.sp, color = HrvColor)
                        }
                        Text(stringResource(R.string.s_b00017e2), fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // ── 分区：最近一次的派生指标 ──
            //
            // HRV / 压力 / PAT 三张卡都是**从心电这一次测量里推出来的**（HRV 需要
            // R 峰间期、PAT 需要心电自带那路脉搏波），与上面的原始记录列表不同类 ——
            // 所以排在记录**之后**（见本函数开头那条顺序判据）。
            // ⚠ HRV 与压力同属 `newest`，共用这个 if —— 只画一条分隔线。
            item { SectionHeader(stringResource(R.string.s_c2381a3d)) }

            // 节律复查（2026-10-10）：只对**心电来源**的逐拍间期做一次描述性复查。
            // ⚠ 排在这一分区的**第一张**：它与上面那条原始记录是**同一路信号**（R 峰间期），
            //   而 HRV / 压力 / PAT 都是再从它推出来的。
            item { RhythmRecheckCard(hrv = hrv) }

            // HRV 卡只在确实有数据时出现 —— 旧版本手表测的心电没有这条伴生记录。
            val newest = hrv.maxByOrNull { it.measuredAt }
            if (newest != null) {
                item {
                    HrvCard(
                        e = newest,
                        baseline = remember(newest.id, hrv) {
                            hrvBaseline(hrv, newest.id, newest.source)
                        },
                    )
                }
                item {
                    StressCard(e = newest, hrv = hrv, temps = temps)
                }
            }
            // #14 PAT：数据在**心电记录本身**上（`ecg_records`），所以放在 HRV 那个 if 之外 ——
            // 旧版本手表测的心电没有 PAT（列为 null），这时卡片会说明"这几次都没算出来"。
            item { PatCard(records = records) }
        }
    }

    /**
     * 「节律复查」卡（2026-10-10）。
     *
     * ## 它回答什么、**不**回答什么
     * 回答：**最近这次心电记录里，有没有出现"间隔明显偏短的搏动"**。
     * 不回答：有没有早搏、属于哪种心律失常 —— 判据上做不到（见 [Arrhythmia] 的类注释），
     * 措辞上也不许（只做筛查、不给诊断）。⇒ 卡片上**不出现「早搏」这个词**。
     *
     * ## ⚠ 只取 `watch_ecg` 来源
     * PPG 的脉搏间期**不能**走这条路：提前搏动常伴**弱脉/无脉**，在 PPG 上表现为"少了一拍"，
     * 与**漏检**完全同形 ⇒ 这个量在 PPG 上结构性判不了。
     * （「两来源两基线不许混」的更强版本：这里不是基线混，是把不可判的当成可判的。）
     *
     * ## ⚠「未检出」不许写成「正常」
     * 单次测量只覆盖约 30 秒（≈35 拍），而 24 小时 Holter 才在 40%~75% 的人里录到至少一次 ——
     * 摊到 30 秒里的期望是 **0.008 次**。所以"没抓到"**根本不构成"没有"**：
     * 未检出那一支必须带一句「单次测量只覆盖约 30 秒」，而不是给一个"正常"。
     *
     * ## ⚠「单次检出」也不提示就医
     * 只有**近几次里重复出现**才升格成"建议就医"（见 `Arrhythmia.repeatVerdict`）——
     * 偶发早搏在健康人群里是常态（24 小时能到 40%~75%），单次抓到就喊，只会制造焦虑。
     */
    @Composable
    private fun RhythmRecheckCard(hrv: List<HrvSessionEntity>) {
        // ⚠ 只取心电来源，且**新的在前**（与 `Arrhythmia.repeatVerdict` 的入参约定一致）。
        val sessions = remember(hrv) {
            hrv.asSequence()
                .filter { it.source == Hrv.Source.WATCH_ECG }
                .sortedByDescending { it.measuredAt }
                .take(Arrhythmia.REPEAT_WINDOW)
                .toList()
        }
        val results = remember(sessions) {
            sessions.map { Arrhythmia.screen(Hrv.decodeRr(it.rrMs)) }
        }
        val verdict = remember(results) { Arrhythmia.repeatVerdict(results) }

        AppCard(title = stringResource(R.string.s_9606d525)) {
            Column {
                Spacer(Modifier.height(6.dp))
                when (val latest = results.firstOrNull()) {
                    // 一次心电记录都没有（或旧版本记录不带逐拍间期）
                    null -> Text(
                        stringResource(R.string.s_c182f86a),
                        fontSize = 13.sp, color = AppTextMuted,
                    )
                    is Arrhythmia.Result.TooFew -> Text(
                        stringResource(R.string.s_d4d3d1df, latest.need),
                        fontSize = 13.sp, color = AppTextMuted,
                    )
                    // ⚠ 这一支是"把伪迹报成提前搏动"那条路的兜底，必须显示出来而不是静默跳过
                    is Arrhythmia.Result.TooNoisy -> Text(
                        stringResource(R.string.s_d9a37aca, (latest.artifactRatio * 100).roundToInt()),
                        fontSize = 13.sp, color = AppTextMuted,
                    )
                    is Arrhythmia.Result.Screened -> if (!latest.found) {
                        Text(stringResource(R.string.s_999db1d2), fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.s_041c196f),
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                    } else {
                        Text(
                            stringResource(R.string.s_933d00fe, latest.pairs.size),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (verdict.persistent) {
                            Text(
                                stringResource(R.string.s_93cc5707, verdict.screened, verdict.hits),
                                fontSize = 13.sp, color = SettingsWarnColor,
                            )
                        } else {
                            Text(
                                stringResource(R.string.s_5003f1f6),
                                fontSize = 11.sp, color = AppTextMuted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.s_53c04381),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
        }
    }

    /**
     * 把 HRV 记录配到同一次心电上。
     *
     * 首选**精确同时间戳**：手表写这两条记录用的是同一个 `now`（心电结论 + 逐拍间期），
     * 同步过来也保持这个时间戳。2 秒的兜底窗口用来容忍极端情况下两条记录被分批推送的抖动。
     * **刻意不做「取最近一条」**：那会让一次没测出 HRV 的心电挂上上一次的间期序列，
     * 属于张冠李戴 —— 宁可这张卡不出现。
     */
    private fun hrvFor(list: List<HrvSessionEntity>, recordedAt: Long): HrvSessionEntity? {
        list.firstOrNull { it.measuredAt == recordedAt }?.let { return it }
        return list.asSequence()
            .filter { abs(it.measuredAt - recordedAt) <= 2_000L }
            .minByOrNull { abs(it.measuredAt - recordedAt) }
    }

    /**
     * 该用户**其他** HRV 记录的 RMSSD 列表，用于"与自己的基线比"。
     *
     * 为什么比的是自己的历史而不是文献里的"正常范围"：RMSSD 随年龄单调下降
     * （20 岁与 70 岁的正常值能差几倍），还受体位、睡眠、咖啡因影响，
     * 拿一个固定区间判读必然误报。见 [Hrv.INTERPRET_NOTE]。
     *
     * 剔除当前这条（`excludeId`）—— 把自己算进基线里会让"本次 vs 基线"显得更接近。
     */
    private fun hrvBaseline(
        list: List<HrvSessionEntity>,
        excludeId: Long,
        source: String,
    ): List<Double> =
        list.asSequence()
            // ⚠ **必须按来源过滤**：RR（R 峰间期）与 IBI（脉搏间期）是两套基线，
            //   混在一起算会让两边都失去意义 —— 而且**不会报错**，
            //   只是那个"相对你自己基线"的分母悄悄变成了两个人群的混合统计量。
            .filter { it.id != excludeId && it.source == source }
            .take(20)
            .mapNotNull { e ->
                (
                    Hrv.analyze(
                        rawRrMs = Hrv.decodeRr(e.rrMs),
                        source = e.source,
                        activityLevel = e.activityLevel,
                        ecgRegular = e.ecgRegularFlag(),
                    ) as? Hrv.Result.Ok
                    )?.rmssdMs
            }
            .toList()

    /**
     * HRV 卡片（模块 #9）。
     *
     * 表里只有**输入**（逐拍间期 + 测量条件），所有指标在这里现算：
     * 算法一改，历史记录的口径跟着一起改，不会出现"新旧口径混在一张表里"。
     */
    /**
     * 脉搏到达时间（PAT，模块 #14）卡片。
     *
     * ## ⚠ 三条界面红线（离线校验台架里有断言钉住其中的措辞）
     *
     * 1. **单位只能是 ms**，任何地方不得出现 mmHg；
     * 2. 必须写出「它不是血压」以及为什么 —— 直接用 `Ptt.NOT_BP`，
     *    **不在界面里另写一段**（措辞只有一处定义，改口径时才不会漏）；
     * 3. **绝不与袖套读数同屏** —— 所以这张卡在**心电页**，不在血压页。
     *    同屏同单位会让读者分不清哪个才是真实测量值。
     *
     * ## 为什么展示"与你自己中位数的差"而不是"正常范围"
     *
     * 与 HRV、压力分同一条纪律：**不给正常范围**。PAT 没有公认的人群参考线
     * （它同时含 PEP，个体差异远大于人群规律），只说"相对你自己平时的变化"。
     */
    @Composable
    private fun PatCard(
        records: List<EcgRecordEntity>,
        modifier: Modifier = Modifier,
    ) {
        val withPat = records.filter { it.patMs != null }
        val newest = withPat.maxByOrNull { it.recordedAt }
        // ⚠⚠ 基线**只收同一套口径**的记录（`Ptt.ALGO_VERSION`）。
        //
        // PAT 的参考点在 v65/v95 改过一次（从"能量包络的峰"改成"吸附回真实 QRS"），
        // 于是老记录系统性偏小 30~80ms —— 把两套口径一起喂进中位数，**不报错、不提示**，
        // 只是"相对你自己基线"的分母变成了两个口径的混合统计量。
        // 这与 `Hrv` 的两套基线、`Backfill` 的来源冒充是同一类错误，用同一条办法：
        // **口径随记录走，不合口径的不进基线**（老记录仍保留，那是用户数据）。
        val comparable = withPat.filter { (it.patAlgo ?: 0) >= Ptt.ALGO_VERSION }
        val staleCount = withPat.size - comparable.size
        val newestComparable = comparable.maxByOrNull { it.recordedAt }
        val history = comparable.mapNotNull { it.patMs }
        val base = Ptt.baseline(history)
        AppSurface(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.s_617f45eb),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(stringResource(R.string.s_906b1bd1), fontSize = 11.sp, color = AppTextMuted)
                }
                Spacer(Modifier.height(8.dp))

                if (newest == null) {
                    // ⚠ 一次都没算出来时**不能只留空白**：用户测了 N 次却什么都没有，
                    //    必须说清是"这几次都没成功"以及**最近一次的原因**（patNote）。
                    Text(
                        stringResource(R.string.s_961a921f, records.size),
                        fontSize = 13.sp, color = AppTextMuted,
                    )
                    records.maxByOrNull { it.recordedAt }?.patNote?.let { PttNote.reason(it) }?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(R.string.s_4ed1a4a0, it), fontSize = 12.sp, color = SettingsWarnColor)
                    }
                } else {
                    // ⚠ 必须先取局部变量：`patMs` 声明在 core 模块（另一个编译单元），
                    //   Kotlin **不给跨模块属性做智能转换**，直接 `newest.patMs - base`
                    //   会报 "Smart cast to 'Int' is impossible"。
                    val patMs = newest.patMs!!
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "$patMs",
                            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = PatColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "ms",
                            fontSize = 13.sp, color = AppTextMuted,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        newest.patSpreadMs?.let {
                            Spacer(Modifier.weight(1f))
                            Text(
                                stringResource(R.string.s_25321381, it),
                                fontSize = 13.sp, color = AppTextMuted,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                    }
                    Text(
                        Ptt.WHAT_IT_IS,
                        fontSize = 12.sp, color = AppTextMuted,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    val isCurrentAlgo = newestComparable != null && newestComparable.id == newest.id
                    if (base != null && isCurrentAlgo) {
                        Text(
                            stringResource(R.string.s_f30643bb, history.size, base) +
                                Ptt.deviationLabel(patMs - base),
                            fontSize = 12.sp, color = AppTextMuted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        Text(
                            stringResource(R.string.s_63954c39, Ptt.BASELINE_MIN_SESSIONS) +
                                stringResource(R.string.s_a9c03c36),
                            fontSize = 12.sp, color = AppTextMuted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    // ⚠ 老口径的记录**必须说出来**，不能默默过滤：用户会看到"我明明测过 N 次，
                    //   怎么还说没有历史"。原因（参考点改过一次）比数字更值得他知道。
                    if (staleCount > 0) {
                        Text(
                            stringResource(R.string.s_edc3390c, staleCount) +
                                stringResource(R.string.s_be604e96),
                            fontSize = 11.sp, color = AppTextMuted,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    // 最近一次没算出来时也要说一声 —— 否则用户会奇怪"为什么新的一次没更新"
                    records.maxByOrNull { it.recordedAt }?.let { latest ->
                        if (latest.patMs == null && latest.id != newest.id) {
                            latest.patNote?.let { PttNote.reason(it) }?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_76b15655, cardFmt.format(Date(latest.recordedAt)), it),
                                    fontSize = 11.sp, color = SettingsWarnColor,
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                // ⚠ 这段**不折叠**：它是本模块最重要的一句话。引用常量而不是就地重写，
                //   是因为一旦被某处简化成"血压趋势"，整个功能就变成了一个假的测量值。
                Text(Ptt.NOT_BP, fontSize = 11.sp, color = AppTextMuted)
            }
        }
    }

    @Composable
    private fun HrvCard(
        e: HrvSessionEntity,
        baseline: List<Double>,
        modifier: Modifier = Modifier,
    ) {
        val res = remember(e.id, e.rrMs, e.activityLevel, e.ecgRegular) {
            Hrv.analyze(
                rawRrMs = Hrv.decodeRr(e.rrMs),
                source = e.source,
                activityLevel = e.activityLevel,
                ecgRegular = e.ecgRegularFlag(),
            )
        }
        AppSurface(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.s_f6afd749),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    if (res is Hrv.Result.Ok) {
                        Text(Hrv.tierLabel(res.tier), fontSize = 11.sp, color = AppTextMuted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (res) {
                    is Hrv.Result.Ok -> {
                        // 一行总体结论：只看**这次测得准不准**，不评价好坏（HRV 没有通用正常范围）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(HrvColor.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(Hrv.plainSummary(res), fontSize = 12.sp, lineHeight = 17.sp)
                        }
                        SectionRule()
                        // 三个指标并排：RMSSD 是主指标（逐拍差分，对自主神经最直接），
                        // SDNN 单独看容易被误解（时长越短越小），所以把时长写在它旁边。
                        // 每一格下面附一句人话（2026-09-29 加）：专业缩写对普通用户是黑箱。
                        Row(Modifier.fillMaxWidth()) {
                            HrvMetric("RMSSD", "${Hrv.ms(res.rmssdMs)}", "ms", HrvColor,
                                Modifier.weight(1f), Hrv.PLAIN_RMSSD)
                            HrvMetric("pNN50", "${res.pnn50Pct.roundToInt()}", "%", HrvColor,
                                Modifier.weight(1f), Hrv.PLAIN_PNN50)
                            HrvMetric("SDNN", "${Hrv.ms(res.sdnnMs)}", "ms", Color(0xFF6B7280),
                                Modifier.weight(1f), Hrv.PLAIN_SDNN)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_82cbe300, res.hrMean.roundToInt()) +
                                stringResource(R.string.s_60372ec7, res.hrMin, res.hrMax, Hrv.pct1(res.cvPct)) +
                                stringResource(R.string.s_f73b4405, res.beats, Hrv.sec1(res.spanMs)),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                        // 与自身基线对比：比"绝对值好不好"更可靠的口径
                        val base = Hrv.baselineMedian(baseline)
                        if (base != null && base > 0) {
                            val diff = (res.rmssdMs - base) / base * 100.0
                            Text(
                                stringResource(R.string.s_3135d6b8, baseline.size, Hrv.ms(base)) +
                                    stringResource(R.string.s_656f9514, signOf(diff), Hrv.pct1(diff)),
                                fontSize = 12.sp, color = AppTextMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        } else {
                            Text(
                                stringResource(R.string.s_4b26614c) +
                                    stringResource(R.string.s_5f2a9d6c),
                                fontSize = 12.sp, color = AppTextMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        // 测量条件：不是静息时必须明说，否则数字会被与基线错误地比较
                        if (res.rest != Hrv.RestState.RESTING) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                when (res.rest) {
                                    Hrv.RestState.MOVING_LIGHT ->
                                        stringResource(R.string.s_b813727f) +
                                            stringResource(R.string.s_544fb9a3)
                                    Hrv.RestState.UNKNOWN ->
                                        stringResource(R.string.s_40ae087d) +
                                            stringResource(R.string.s_611d4ae0)
                                    else -> stringResource(R.string.s_ddf52245)
                                },
                                fontSize = 12.sp, color = HrvWarnColor,
                            )
                        }
                        SectionRule()
                        Text(stringResource(R.string.s_ff3aa8e1), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        TachogramChart(res.rrMs)
                        Text(
                            stringResource(R.string.s_936f8081) +
                                stringResource(R.string.s_5ff49aae),
                            fontSize = 10.sp, color = AppTextMuted,
                        )
                        SectionRule()
                        // 伪迹统计：不显示"我改了几拍"，用户就不知道这个 RMSSD 有多可信
                        Text(
                            stringResource(R.string.s_722a28e3, res.corrected, res.removed) +
                                stringResource(R.string.s_dd0c1b3a, Hrv.pct1(res.artifactRatio * 100.0)) +
                                "${(Hrv.MAX_ARTIFACT_RATIO * 100).roundToInt()}%）",
                            fontSize = 11.sp, color = AppTextMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        val fr = res.freq
                        if (fr != null) {
                            Text(stringResource(R.string.s_004e6a12), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            // 取局部变量再判空：lfMs2 是跨模块的 public 属性，Kotlin 不给智能转换
                            val lf = fr.lfMs2
                            Text(
                                buildString {
                                    if (lf != null) {
                                        append("LF ${lf.roundToInt()} ms²")
                                        fr.lfHf?.let { append(" · LF/HF ${Hrv.pct1(it)}") }
                                        fr.lfNu?.let { append(" · LFnu ${it.roundToInt()}%") }
                                        append("\n")
                                    }
                                    append("HF ${fr.hfMs2.roundToInt()} ms²")
                                    fr.hfPeakHz?.let {
                                        append(stringResource(R.string.s_5f0dedc6, Hrv.pct1(it * 100)))
                                    }
                                    append(stringResource(R.string.s_bfdb1db2, fr.totalMs2.roundToInt()) +
                                        stringResource(R.string.s_cbf36832, fr.varianceMs2.roundToInt()))
                                },
                                fontSize = 12.sp,
                            )
                            // 频域里的 LF/HF 是普通用户最容易看到却最看不懂的一格
                            if (fr.hasLfHf) {
                                Text(
                                    Hrv.PLAIN_LFHF,
                                    fontSize = 10.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                            }
                        }
                        Text(
                            res.freqNote,
                            fontSize = 11.sp, color = AppTextMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        SectionRule()
                        HrvNotes()
                    }
                    is Hrv.Result.NotApplicable -> {
                        // 不给结果时，**原因就是这张卡的主体**：用户需要知道下一步做什么
                        Text(
                            stringResource(R.string.s_ff16ec7a),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = HrvWarnColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(res.reason, fontSize = 13.sp)
                        SectionRule()
                        HrvNotes()
                    }
                }
                // ⚠⚠ 心电这一路的「呼吸率」**已删**（2026-10-16）：
                //
                //   它用的是"第一个搏动 → 最后一个搏动"的跨度（`Hrv.decodeRr` 的时间范围），
                //   而手表端的测量时长**写死 30 秒** ⇒ 那个跨度永远只有 27~28 秒，
                //   恒小于模型要求的 30 秒。用户看到的「只有 27.5 秒，至少需要 30 秒」
                //   不是"这次数据不好"，而是**这条路永远走不通**（实测 30.3 秒 → 27.5 秒）。
                //   ⇒ 一张永远只讲"我给不出"的卡占着位置，删掉。
                //
                //   ⚗ **光信号那一路保留**：`Resp.fromPpg` 用的是**波形本身的时长**（整段 30 秒），
                //     能过 ≥30 秒那道门 ⇒ 血氧页/记录页里的呼吸率仍会出结果。
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.s_c70dd727, e.rrCount, Hrv.sec1(e.durationMs)) +
                        stringResource(R.string.s_225d80d3) + when (e.ecgRegular) {
                            1 -> stringResource(R.string.s_003489ac); 0 -> stringResource(R.string.s_00288ff7); else -> stringResource(R.string.s_1b2e0280)
                        } + stringResource(R.string.s_1de509d2) + Activity.label(e.activityLevel) +
                        stringResource(R.string.s_1c6c1b04) + sourceLabel(e.source),
                    fontSize = 10.sp, color = AppTextMuted,
                )
            }
        }
    }

    /**
     * 卡片里的一格指标（数值 + 单位 + 单位说明）。
     *
     * [plain] 是这一格的**说人话**注释（2026-09-29 加）：专业缩写下面补一句人话，
     * 专业数值原样保留。要占额外两行高度，所以三格并排时整体会变高 —— 这是刻意的，
     * 原本这三个缩写对普通用户就是三个黑箱。
     */
    @Composable
    private fun HrvMetric(
        name: String,
        value: String,
        unit: String,
        color: Color,
        modifier: Modifier = Modifier,
        plain: String? = null,
    ) {
        Column(modifier) {
            Text(name, fontSize = 11.sp, color = AppTextMuted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = color)
                Text(" $unit", fontSize = 11.sp, color = AppTextMuted)
            }
            if (plain != null) {
                Spacer(Modifier.height(2.dp))
                Text(plain, fontSize = 10.sp, color = AppTextMuted, lineHeight = 13.sp)
            }
        }
    }

    /** 说明区块：口径、伪迹、分级、免责。刻意都写出来 —— 这四项决定了数字能不能用。 */
    @Composable
    private fun HrvNotes() {
        val lang = rememberLang()
        Column {
            Text(stringResource(R.string.s_1dac0c14), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            // 4 段说明合并成一个可折叠块（默认收起，2026-09-21）：
            // 常驻展开时这段灰字比上面的数据还长，整张卡一半高度都在讲口径。
            CollapsibleNotes(
                listOf(
                    Hrv.TIER_NOTE,
                    Hrv.ARTIFACT_NOTE,
                    Hrv.INTERPRET_NOTE,
                    Disclaimer.hrv(lang),
                ),
                fontSize = 10.sp,
            )
        }
    }

    // ═══════════════════ 压力 / 疲劳评分（模块 #12）══════════════════

    /**
     * 压力 / 疲劳评分卡（模块 #12）。
     *
     * 与 [HrvCard] 并排放在心电页：评分的输入就是这一次 HRV，
     * 分开两页会让人以为要测两次，而它其实只是同一份数据的另一种读法。
     *
     * 表里**不落库**：分数是确定性派生量，落库会产生"用旧版本算法算出的历史分"，
     * 与新值混在一张表里且无法区分 —— 与 #9 HRV 只存间期、#10 核心体温不落库同一条理由。
     */
    @Composable
    private fun StressCard(
        e: HrvSessionEntity,
        hrv: List<HrvSessionEntity>,
        temps: List<TemperatureEntity>,
        modifier: Modifier = Modifier,
    ) {
        // 换算走 StressSummary（与推给手表的那份**同一份实现**）：
        // 分数没有绝对标尺，两端若各算各的，用户看不出哪个是对的。
        val obs = remember(e.id, e.rrMs, e.activityLevel, e.ecgRegular) { StressSummary.toObs(e) }
            // 这次 HRV 本身不成立（伪迹过多 / 节律不齐 / 活动中）时整块不显示：
            // 原因已经由上面那张 HRV 卡说清了，这里再来一句"不适用"只是重复。
            ?: return
        // 这两步都很重（各跑几十次 Hrv.analyze），必须用 remember 挡住重组
        val history = remember(hrv.size, hrv.lastOrNull()?.id) { StressSummary.historyOf(hrv) }
        val tObs = remember(temps.size, temps.lastOrNull()?.id) { StressSummary.toTemps(temps) }
        val res = remember(obs.measuredAt, history, tObs) {
            Stress.analyze(current = obs, history = history, temps = tObs)
        }

        AppSurface(modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ⚠ 同 `EntryRow`：两个 Text 里必须至少有一个能压缩，
                    //   否则英文长文案会把另一个挤成 0 宽、竖排显示。
                    Text(
                        stringResource(R.string.s_e8a852d4),
                        fontWeight = FontWeight.Bold,
                        maxLines = 3, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        stringResource(R.string.s_1f35ad1f), fontSize = 11.sp, color = AppTextMuted,
                        textAlign = TextAlign.End, maxLines = 2,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                when (res) {
                    is Stress.Result.Ok -> {
                        StressScoreRow(stringResource(R.string.s_002b12bc), res.stress)
                        Spacer(Modifier.height(10.dp))
                        // 取局部变量再判空：fatigue 是跨模块的 public 属性，Kotlin 不给智能转换
                        val fatigue = res.fatigue
                        if (fatigue != null) {
                            StressScoreRow(stringResource(R.string.s_003c8cc9), fatigue)
                            Text(
                                res.fatigueNote,
                                fontSize = 11.sp, color = AppTextMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.s_003c8cc9), fontSize = 13.sp, color = AppTextMuted)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.s_b1d1e208), fontSize = 13.sp, color = HrvWarnColor)
                            }
                            Text(
                                res.fatigueNote,
                                fontSize = 11.sp, color = AppTextMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.s_8f170467), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.height(4.dp))
                        res.stress.parts.forEach { p ->
                            StressPartRow(p)
                            Spacer(Modifier.height(6.dp))
                        }
                        CollapsibleNotes(
                            listOf(
                            stringResource(R.string.s_ddd15752, res.baseline.count) +
                            stringResource(R.string.s_2934b5fe, Stress.fmt1(res.baseline.spanMs / 86_400_000.0)) +
                            "（RMSSD ${Hrv.ms(res.baseline.rmssdMed)} ms · " +
                            stringResource(R.string.s_3a0da6ff, res.baseline.hrMed.roundToInt()),
                            )
                        )

                        if (res.caveats.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            res.caveats.forEach {
                                Text("⚠ $it", fontSize = 11.sp, color = HrvWarnColor)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        StressNotes()
                    }
                    is Stress.Result.NotApplicable -> {
                        Text(
                            stringResource(R.string.s_9d9d9ce8),
                            fontWeight = FontWeight.Bold, fontSize = 15.sp, color = HrvWarnColor,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(res.reason, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        StressNotes()
                    }
                }
            }
        }
    }

    /** 一行分数：名称 + 大数字 + 分级 + 进度条（条上标出 50 = 与平时持平的位置）。 */
    @Composable
    private fun StressScoreRow(name: String, s: Stress.Score) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(name, fontSize = 13.sp, color = AppTextMuted)
            Spacer(Modifier.width(8.dp))
            Text(
                "${s.value}",
                fontSize = 26.sp, fontWeight = FontWeight.Bold, color = StressColor,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                s.levelText,
                fontSize = 13.sp, color = stressLevelColor(s.level),
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = size.height / 2
            drawRoundRect(
                AppDivider, Offset.Zero, size,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            val w = (size.width * s.value / 100f).coerceAtLeast(2f)
            drawRoundRect(
                stressLevelColor(s.level), Offset.Zero,
                androidx.compose.ui.geometry.Size(w, size.height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
            )
            // 50 分处画一条竖线：没有它，"68 分"看不出是偏高还是偏低
            val x50 = size.width * 0.5f
            drawLine(
                Color(0x66FFFFFF), Offset(x50, 0f), Offset(x50, size.height),
                strokeWidth = 1.5f,
            )
        }
    }

    /** 一个分量的分解：本次值 vs 基线值、权重、偏离了几个单位。 */
    @Composable
    private fun StressPartRow(p: Stress.Part) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Stress.componentLabel(p.name), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.s_bce74d78, (p.weight * 100).roundToInt()),
                    fontSize = 10.sp, color = AppTextMuted,
                )
            }
            Text(
                stringResource(R.string.s_32608d45, p.currentText, p.baselineText),
                fontSize = 11.sp, color = AppTextMuted,
            )
            Text(
                stringResource(R.string.s_be294bf7, Stress.devText(p.devUnits), p.componentScore),
                fontSize = 11.sp, color = stressLevelColor(Stress.levelOf(p.componentScore.toDouble())),
            )
        }
    }

    /** 说明区块：刻度、权重来源、基线、疲劳口径、活动量边界、免责。 */
    @Composable
    private fun StressNotes() {
        val lang = rememberLang()
        Column {
            Text(stringResource(R.string.s_1d848ff6), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            // 5 段说明合并成一个可折叠块（默认收起，2026-09-21）：
            // 常驻展开时这段灰字比上面的数据还长，整张卡一半高度都在讲口径。
            CollapsibleNotes(
                listOf(
                    Stress.SCALE_NOTE,
                    Stress.WEIGHT_NOTE,
                    Stress.BASELINE_NOTE,
                    Stress.FATIGUE_NOTE,
                    Stress.ACTIVITY_NOTE,
                ),
                fontSize = 10.sp,
            )
            Text(
                Disclaimer.stress(lang),
                fontSize = 10.sp,
                color = HrvWarnColor,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }

    /**
     * 逐拍间期序列（tachogram）。
     *
     * 纵轴**至少 50ms 的跨度**（与心率/血氧图同一条规则）：一段 40 拍、RR 只在 780~800
     * 之间波动的记录，自适应纵轴会把它画成满屏起伏，看着像"心律剧烈波动"。
     * 上下各留内边距：数值正好等于上界时按比例映射会落到 y=0 被裁掉，整张图看着是空的。
     */
    @Composable
    private fun TachogramChart(rr: List<Int>, modifier: Modifier = Modifier) {
        if (rr.size < 2) {
            Text(stringResource(R.string.s_781112ca), fontSize = 11.sp, color = AppTextMuted)
            return
        }
        Canvas(modifier.fillMaxWidth().height(110.dp)) {
            val padY = 8.dp.toPx()
            val lo0 = rr.min().toFloat()
            val hi0 = rr.max().toFloat()
            val mid = (lo0 + hi0) / 2f
            val half = max(25f, (hi0 - lo0) / 2f)
            val lo = mid - half
            val hi = mid + half
            val usable = size.height - 2f * padY
            val stepX = size.width / (rr.size - 1)
            fun yOf(v: Int) = padY + (hi - v) / (hi - lo) * usable
            // 平均线：让"偏离均值多少"一眼可见
            val meanY = yOf(rr.average().roundToInt())
            drawLine(
                Color(0x22338877), Offset(0f, meanY), Offset(size.width, meanY),
                strokeWidth = 1f,
            )
            val path = Path()
            rr.forEachIndexed { i, v ->
                val x = i * stepX
                val y = yOf(v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = HrvColor, style = Stroke(width = 1.6f, cap = StrokeCap.Round))
            // 每一拍画一个点：让人看出这是**逐拍**序列而不是连续采样
            rr.forEachIndexed { i, v ->
                drawCircle(HrvColor, radius = 1.6f, center = Offset(i * stepX, yOf(v)))
            }
        }
    }

    /**
     * 心电详情页里「导出 CSV / 打开保存位置」这一次操作的结局（2026-10-05）。
     *
     * ⚠ 为什么用 `sealed` 而不是"一个可空字符串槽位"：去重之后有**五种互斥结局**，
     *   而旧写法把「落盘失败」和「打开文件夹失败」都塞进 `""` ⇒
     *   打开文件夹失败时会显示「导出失败，请重试」，可**重试根本不是问题所在**
     *   （用户会去反复点导出，而真正的原因是机器上没有文件管理器）。
     *   ⚗ 这与本项目那条"文案要写在**不会自相矛盾**的位置"是同一类问题。
     */
    private sealed interface EcgExportUi {
        /** 这次录制没有波形数据（不是操作错了）。 */
        data object Empty : EcgExportUi

        /** 落盘报错 ⇒ 可以重试。 */
        data object SaveFailed : EcgExportUi

        /** 没有能打开文件夹的应用 ⇒ **不是**导出的问题，文件其实在。 */
        data object FolderFailed : EcgExportUi

        /** 新写入了一份。 */
        data class Saved(val path: String) : EcgExportUi

        /** 这份内容之前已导出过（**没有**写第二份）。 */
        data class Duplicate(val path: String) : EcgExportUi
    }

    /** 心电详情：结论 + 指标 + 波形。 */
    @Composable
    private fun EcgDetailScreen(
        record: EcgRecordEntity,
        hrv: List<HrvSessionEntity>,
        temps: List<TemperatureEntity>,
        onBack: () -> Unit,
    ) {
        var wave by remember { mutableStateOf<EcgWaveEntity?>(null) }
        // ⚗ 同时段的 PPG（2026-10-05 导出用）。范围 ±[EcgExport.PPG_MATCH_MS]：
        //   心电与 PPG 是同一会话的两条通道，落库时间戳差几秒，等值匹配必然落空。
        // ⚠ 单独一个 LaunchedEffect：它失败不该影响波形本身（波形才是主体）。
        var ppg by remember { mutableStateOf<PpgWaveEntity?>(null) }
        // ⚗⚗ 导出结果用**本页的弹窗**显示（2026-10-05 用户反馈"点了没有提示"）。
        //
        // ⚠⚠ 原来走的是 `SyncRepository.setEvent` —— 那个事件条只在**首页**
        //   （`SyncStatusStrip`）渲染，而导出按钮在**心电详情页** ⇒ 用户什么都看不到。
        //   `setEvent` 是"跨页面的同步状态"，**不是**"当前操作的反馈"，
        //   这两者混用就会出现"操作有执行、用户没感知"。
        // ⚠ 值 = 要显示的**完整路径**；`null` = 没弹窗。
        // ⚗ 一次只可能处于一种结局 ⇒ 一个槽位就够（见 `EcgExportUi` 的说明）
        var exportUi by remember { mutableStateOf<EcgExportUi?>(null) }
        LaunchedEffect(record.recordedAt) {
            wave = runCatching {
                SyncRepository.database.ecgWaveDao().byTimeAny(record.recordedAt)
            }.getOrNull()
            ppg = runCatching {
                val from = record.recordedAt - EcgExport.PPG_MATCH_MS
                val to = record.recordedAt + EcgExport.PPG_MATCH_MS
                SyncRepository.database.ppgWaveDao().inRange(from, to)
            }.getOrNull()
        }
        val w = wave
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppOutlineButton(onClick = onBack) { Text(stringResource(R.string.s_18458212)) }
            }

            // ── 分区：本次结论 ──
            item { SectionHeader(stringResource(R.string.s_f30a737a)) }

            item {
                Text(
                    EcgNote.classLabel(record.classification) ?: record.classification,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = ecgClassColor(record.classification),
                )
                Text(timeFmt.format(Date(record.recordedAt)), fontSize = 13.sp, color = AppTextMuted)
            }
            item {
                AppCard(title = stringResource(R.string.s_cb791d4c)) {
                    Column {
                        Text(
                            buildString {
                                record.heartRate?.let { append(stringResource(R.string.s_014d5fa9, it)) }
                                w?.let {
                                    when (it.regular) {
                                        1 -> append(stringResource(R.string.s_1243cf51))
                                        0 -> append(stringResource(R.string.s_ef7da8f4))
                                    }
                                    it.beatCount?.let { b -> append(stringResource(R.string.s_f3c622db, b)) }
                                    it.sampleRate?.let { s -> append(stringResource(R.string.s_f66cb7aa, s)) }
                                    val durSec = "%.1f".format(Locale.US, it.durationMs / 1000.0)
                                    append(stringResource(R.string.s_48bf839d, durSec))
                                    append(stringResource(R.string.s_5a09d91b, it.sampleCount))
                                    // 该列现存"拍模板相干性"（0~2）；旧的频带能量比已被真机数据证伪
                                    it.bandRatio?.let { r -> append(stringResource(R.string.s_67390b77).format(Locale.US, r)) }
                                } ?: run {
                                    append(stringResource(R.string.s_76e7886e))
                                }
                                // ⚠ PAT 要**随这条记录**显示：此前只有心电首页那张卡显示"最新一次"，
                                //   用户翻历史记录时一条 PAT 都看不到，会以为"这个值没存下来"
                                //   （2026-10-17 反馈）。没算出时给出原因，比留空白有用。
                                record.patMs?.let {
                                    append("\n" + stringResource(R.string.s_617f45eb) + " $it ms")
                                }
                                if (record.patMs == null) {
                                    record.patNote?.let { PttNote.reason(it) }?.let {
                                        append("\n" + stringResource(R.string.s_617f45eb) + " · " + it)
                                    }
                                }
                            },
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            // ── 分区：波形 ──
            item { SectionHeader(stringResource(R.string.s_f3d6b6f6)) }

            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.s_f3d6b6f6), fontWeight = FontWeight.Bold)
                        EcgStrip(wave = w?.waveJson)
                        Text(
                            stringResource(R.string.s_c7158428),
                            fontSize = 10.sp, color = AppTextMuted,
                        )
                    }
                }
            }
            EcgNote.detail(record.note)?.let { note ->
                item {
                    AppCard(title = stringResource(R.string.s_18f0ec77)) {
                        Column {
                            Text(note, fontSize = 13.sp)
                        }
                    }
                }
            }
            // ── 分区：派生指标（HRV / 压力）──
            //
            // ⚠ 与列表页的分区**同名同义**（`s_0c9bd4a7`）—— 用户从一个页面走到另一个
            // 页面，同名分区标题能让他立刻认出自已还在看同一类东西。
            // 详情页没有 PAT 卡（PAT 是跨记录的趋势量），所以这段可能整块不出现。
            item { SectionHeader(stringResource(R.string.s_c2381a3d)) }

            // 同一次测量的 HRV 由表端落库（只存逐拍间期），这里现算。找不到就整块不显示，
            // 而不是显示"不适用"——详情页是从心电记录进来的，没有 HRV 行说明是旧记录。
            hrvFor(hrv, record.recordedAt)?.let { e ->
                item {
                    // remember 不能省：基线要对近 20 条记录各跑一次 Hrv.analyze（含 Lomb-Scargle），
                    // 每次重组重算等于把 20 次频谱分析压在帧里。
                    HrvCard(
                        e = e,
                        baseline = remember(e.id, hrv) { hrvBaseline(hrv, e.id, e.source) },
                    )
                }
                item {
                    StressCard(e = e, hrv = hrv, temps = temps)
                }
            }
            // ── 分区：导出 ──
            //
            // ⚗ 分区线放在这里**不只是好看**：它把"看的数据"与"取走数据"分开，
            //   正好呼应上面那条"先看后操作"的排序理由。
            item { SectionHeader(stringResource(R.string.s_002f63ee)) }

            // ── 导出模块（2026-10-05 用户要求「给每个心电记录打开后增加导出模块」）──
            //
            // ⚗ 为什么放在**最后**：这一页上面全是"看"的内容（结论/波形/HRV/压力），
            //   导出是"取走数据"的动作 ⇒ 按「先看后操作」排。
            //
            // ⚠ 与手表端导出**不是同一份数据**：手表端那份带 `rpeaks`（它的 QRS 检出结果）
            //   与 `ppg_raw`（原始整数量）；手机端这份只有波形 + **归一化**绿光。
            //   ⇒ 列名必须如实标注，见 [EcgExport] 文件的说明。
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    // ⚗ 两个按钮并排（2026-10-05 用户要求"在导出 CSV 按钮旁边加一个打开保存位置"）。
                    //   ⚠ 导出后文件落在 `下载/VitalBridge/CSV`，而**不是**用户手机相册/
                    //     最近文件里 —— 不给他一个直达入口，他就得自己在文件管理器里翻。
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppOutlineButton(
                        onClick = {
                            val csv = EcgExport.buildCsv(
                                waveJson = w?.waveJson ?: "",
                                greenWave = ppg?.greenWave,
                                sampleRateHz = w?.sampleRate,
                            )
                            // ⚠ **三种结果分开告知**：成功要说清落在哪；
                            //   失败要区分"本来就没有波形"与"落盘出错" ——
                            //   合成一句"导出失败"会让用户以为是自己操作错了。
                            if (csv.isEmpty()) {
                                exportUi = EcgExportUi.Empty
                            } else {
                                // ⚗ 落盘**含内容去重**：同一份内容不再写第二份，
                                //   而是把**已有那份的路径**告诉用户（[EcgExport.SaveResult]）。
                                exportUi = when (
                                    val r = EcgExport.saveCsv(
                                        this@MainActivity, csv,
                                        EcgExport.PHONE_NAME_PREFIX, record.recordedAt,
                                    )
                                ) {
                                    is EcgExport.SaveResult.Created -> EcgExportUi.Saved(r.path)
                                    is EcgExport.SaveResult.Duplicate -> EcgExportUi.Duplicate(r.path)
                                    EcgExport.SaveResult.Failed -> EcgExportUi.SaveFailed
                                }
                            }
                        }
                    ) { Text(stringResource(R.string.s_9c7b3bd8)) }
                    // ⚗ 旁边就是"去看文件"（2026-10-05 用户要求）。
                    // ⚠ 失败时**必须明说文件在哪**：不能点了没反应 ——
                    //   那正是这个按钮要解决的问题（用户找不到文件）。
                    AppOutlineButton(
                        onClick = {
                            if (!EcgExport.openFolder(this@MainActivity)) {
                                // ⚠ 单独一态：这不是"导出失败"，而是机器上没有能打开文件夹的应用
                                exportUi = EcgExportUi.FolderFailed
                            }
                        }
                    ) { Text(stringResource(R.string.s_9312b105)) }
                    }
                }
            }
            item {
                Text(
                    stringResource(R.string.s_2cd50deb) +
                        stringResource(R.string.s_456b6daa),
                    fontSize = 11.sp, color = AppTextMuted,
                )
            }
        }
        // ⚗ 导出结果弹窗（2026-10-05 用户要求"告诉我文件在哪个位置"）。
        //
        // ⚠ **五种结局都要有反馈、且都要说清后果或位置**：
        //   · 新写成 ⇒ 给出**完整路径**（他拿它去文件管理器找）
        //   · 之前导过 ⇒ 说清"没有新文件"，并给出**那份已有的路径**
        //     （2026-10-05 用户要求去重后新增；⚠ 绝不能报成失败 —— 文件确实在）
        //   · 没有波形 ⇒ 说清是"这次录制本身没波形"，不是他的操作错了
        //   · 落盘失败 ⇒ 说清可以重试（**只有这一态**该说"重试"）
        //   · 打不开文件夹 ⇒ 说清是缺文件管理器应用，并给出目录
        // ⚠ 用 `AlertDialog` 而不是顶部事件条：事件条只在首页渲染（见上面的注释），
        //   在详情页用它等于**没有反馈**。
        val exportMsg: String? = when (val u = exportUi) {
            null -> null
            EcgExportUi.Empty -> stringResource(R.string.s_84aaf388)
            EcgExportUi.SaveFailed -> stringResource(R.string.s_b876f225)
            EcgExportUi.FolderFailed -> stringResource(
                R.string.s_9516527d, EcgExport.relativeDir(),
            )
            is EcgExportUi.Saved -> stringResource(R.string.s_e462b441, u.path)
            is EcgExportUi.Duplicate -> stringResource(R.string.s_38682199, u.path)
        }
        // ⚠ 只有"新写成"给标题：其余各态的正文已经说清，
        //   再顶一个标题反而显得像"成功了但你运气不好"。
        val exportTitle: String? = if (exportUi is EcgExportUi.Saved) {
            stringResource(R.string.s_69010812)
        } else null
        if (exportMsg != null) {
            AlertDialog(
                onDismissRequest = { exportUi = null },
                title = if (exportTitle != null) {
                    { Text(exportTitle) }
                } else null,
                text = { Text(exportMsg, fontSize = 13.sp) },
                confirmButton = {
                    AppOutlineButton(onClick = { exportUi = null }) {
                        Text(stringResource(R.string.s_1faf4f0c))
                    }
                },
            )
        }
    }

    /** 波形条：整段压缩到一屏，逐像素列取 min/max 包络，避免抽点漏掉 QRS 尖峰。 */
    @Composable
    private fun EcgStrip(wave: String?) {
        val samples: List<Float> = remember(wave) {
            wave?.split(',')?.mapNotNull { it.trim().toFloatOrNull() } ?: emptyList()
        }
        if (samples.size < 10) {
            Text(
                stringResource(R.string.s_cdb00975),
                fontSize = 12.sp, color = AppTextMuted,
                modifier = Modifier.padding(vertical = 24.dp),
            )
            return
        }
        // 自适应纵轴，但设 ±0.5mV 下限：信号弱时不会被放大成噪声，
        // 信号强时也不会因为个别尖峰把整条波形压平。
        val half = max(0.5f, max(abs(samples.min()), abs(samples.max())))
        Canvas(Modifier.fillMaxWidth().height(150.dp).padding(vertical = 8.dp)) {
            val w = size.width
            val h = size.height
            val base = h * 0.5f
            val scale = (h * 0.45f) / half
            val cols = w.toInt().coerceAtLeast(1)
            val perCol = (samples.size.toFloat() / cols).coerceAtLeast(1f)
            val path = Path()
            for (col in 0 until cols) {
                val a = (col * perCol).toInt().coerceIn(0, samples.size - 1)
                val b = ((col + 1) * perCol).toInt().coerceIn(a + 1, samples.size)
                var lo = Float.MAX_VALUE
                var hi = -Float.MAX_VALUE
                for (k in a until b) {
                    val v = samples[k]
                    if (v < lo) lo = v
                    if (v > hi) hi = v
                }
                val x = w * col / cols
                val yHi = base - hi.coerceIn(-half, half) * scale
                val yLo = base - lo.coerceIn(-half, half) * scale
                if (col == 0) path.moveTo(x, yHi)
                path.lineTo(x, yHi)
                path.lineTo(x, yLo)
            }
            drawPath(
                path,
                color = Color(0xFFE53935),
                style = Stroke(width = 1.8f, cap = StrokeCap.Round),
            )
            drawLine(
                Color(0x22000000),
                Offset(0f, base), Offset(w, base),
                strokeWidth = 1f,
            )
        }
    }

    /** 结论配色：与手表端保持一致。 */
    private fun ecgClassColor(cls: String) = when (cls) {
        this@MainActivity.getString(R.string.s_88992735) -> Color(0xFF6BCB77)
        this@MainActivity.getString(R.string.s_52524593) -> Color(0xFFFF6B6B)
        this@MainActivity.getString(R.string.s_f357ba15), this@MainActivity.getString(R.string.s_f3576e8b) -> Color(0xFFFFA94D)
        else -> Color(0xFF8E7DA3)
    }

    /**
     * 数据来源 -> 展示标签。
     *
     * ⚠ 是 `@Composable`：返回值是给界面看的，要走多语言。
     *   入参 `source` 是**存储值**（`samsung` / `watch_ecg` / `watch_manual` …），
     *   是语言无关的标记，**绝不参与翻译** —— 翻了 `when` 就匹配不上，
     *   界面上会静默退化成 `else -> source`（把存储值直接显示给用户）。
     */
    @Composable
    private fun sourceLabel(source: String) = when (source) {
        "samsung" -> stringResource(R.string.s_8fc12af0)
        "watch_ecg" -> stringResource(R.string.s_4fb29541)
        "watch_hsm" -> stringResource(R.string.s_4fb29541)
        // PPG 逐拍间期（2026-09-24）：与心电**分成两个来源**，标签也要分开 ——
        // 都写"手表测量"的话，用户没法判断这个 HRV 是哪条路的，
        // 而两条路的基线本来就不能互相比较。
        HrvSource.WATCH_PPG -> stringResource(R.string.s_4fa41bb7)
        // ⚠ 存储值是 `watch_manual`，但标签**不能**写「手表手动」：这个来源的 KDoc 本来就定义为
        //   「手动录入（手机端或手表端输入）」，而 2026-09-28 起手机端也能录血压 ——
        //   用户明明在手机上录的，界面却显示"手表手动"，是界面在撒谎（与「拿设备型号当来源」
        //   那次同型）。`watch_` 前缀在这个项目里表达的是「**我们自己产生的**（相对三星/导入）」，
        //   不是"来自手表"，见 `Backfill.isOurs`。
        "watch_manual" -> stringResource(R.string.s_41980c35)
        TempSource.WATCH -> stringResource(R.string.s_4fb42d7c)
        TempSource.SAMSUNG_SKIN -> stringResource(R.string.s_9c3581b8)
        BiaSource.WATCH -> stringResource(R.string.s_4fb29541)
        BiaSource.SAMSUNG -> stringResource(R.string.s_8fc12af0)
        Cycle.Source.MANUAL -> stringResource(R.string.s_41980c35)
        Cycle.Source.HC -> "Health Connect"
        else -> source
    }

    // ───────────────────────── 经期追踪（模块 #13） ─────────────────────────

    /**
     * 经期页：预测 + 录入 + 周期长度图 + 口径说明。
     *
     * 页面结构自上而下：
     *  ① 预测卡（下一次经期 / 周期第几天 / 阶段 / 易孕窗口）—— 数据不足时换成"为什么算不出"；
     *  ② 「记录经期」按钮 + 「从 Health Connect 导入」按钮；
     *  ③ 周期长度柱状图（每次周期多长，让"规律/不规律"看得见）；
     *  ④ 各次记录列表（手动/导入混排，含被生理区间过滤掉的，供核对）；
     *  ⑤ 数据口径 + 免责（不能用于避孕）。
     *
     * 所有指标都由 `Cycle.analyze` 现算 —— 落库的只有"哪天开始/结束"这几个事实。
     */
    @Composable
    private fun CycleTab(
        records: List<MenstrualRecordEntity>,
        hcAvailable: Boolean,
        hcCycleGranted: Boolean,
        onRequestHcImport: () -> Unit,
    ) {
        val lang = rememberLang()
        val scope = rememberCoroutineScope()
        val today = remember { java.time.LocalDate.now().toEpochDay() }
        val entries = remember(records) { records.map { it.toEntry() } }
        val result = remember(records) { Cycle.analyze(entries, today) }

        // 录入对话框状态
        var showEntryDialog by remember { mutableStateOf(false) }

        LazyColumn(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── 分区：预测 ──
            //
            // ⚠ 「录入 / 导入按钮」**不另起分区** —— 它是对预测卡的补充输入，
            // 两者物理上邻近、语义上连续（先看结果、再补数据）。给它单起标题
            // 会让这个只有两个按钮的行显得比实际重要。
            item { SectionHeader(stringResource(R.string.s_004e78d7), isFirstSection = true) }

            // ① 预测卡
            item {
                AppSurface(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.s_6ab598e6), fontWeight = FontWeight.Bold, color = CycleAccentColor)
                        when (result) {
                            is Cycle.Result.NotApplicable -> {
                                Text(result.reason, fontSize = 13.sp)
                            }
                            is Cycle.Result.Ok -> {
                                val pred = result.prediction
                                val fertile = result.inFertileWindow
                                Text(
                                    "${Cycle.tierLabel(result.tier)}",
                                    fontSize = 12.sp,
                                    color = CycleAccentColor,
                                )
                                if (pred != null) {
                                    Text(
                                        stringResource(R.string.s_a53da952, Cycle.predictText(pred)),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                                Text(
                                    buildString {
                                        append(stringResource(R.string.s_3c2e6bc9, result.cycleDay))
                                        result.phase?.let { append(" · ${Cycle.phaseLabel(it)}") }
                                    },
                                    fontSize = 14.sp,
                                )
                                result.medianPeriodDays?.let {
                                    Text(stringResource(R.string.s_1a18f234, it), fontSize = 12.sp, color = AppTextMuted)
                                }
                                Text(
                                    stringResource(R.string.s_046b3999, result.medianCycleDays) +
                                        Cycle.regularityLabel(result.regularity),
                                    fontSize = 12.sp,
                                    color = AppTextMuted,
                                )
                                if (result.overdueDays > 0) {
                                    Text(
                                        stringResource(R.string.s_6573c7fd, result.overdueDays),
                                        fontSize = 13.sp,
                                        color = HrvWarnColor,
                                    )
                                }
                                if (pred != null) {
                                    Text(
                                        if (fertile == true)
                                            stringResource(R.string.s_c659ca8c)
                                        else stringResource(R.string.s_06d440f6),
                                        fontSize = 12.sp,
                                        color = if (fertile == true)
                                            CycleFertileColor else AppTextMuted,
                                    )
                                }
                                if (result.tierNote.isNotEmpty()) {
                                    Text(result.tierNote, fontSize = 11.sp, color = AppTextMuted)
                                }
                            }
                        }
                    }
                }
            }

            // ② 录入 / 导入按钮
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppPrimaryButton(onClick = { showEntryDialog = true }) {
                        Text(stringResource(R.string.s_d0f06799))
                    }
                    if (hcAvailable) {
                        AppOutlineButton(onClick = onRequestHcImport) {
                            Text(if (hcCycleGranted) stringResource(R.string.s_3ccc4fd0) else stringResource(R.string.s_0c76685d))
                        }
                    }
                }
            }

            // ── 分区：周期长度 ──
            item { SectionHeader(stringResource(R.string.s_3fff43ef)) }

            // ③ 周期长度柱状图（有周期长度才画）
            (result as? Cycle.Result.Ok)?.takeIf { it.lengths.isNotEmpty() }?.let { ok ->
                item {
                    AppCard(title = stringResource(R.string.s_3fff43ef)) {
                        Column {
                            CycleLengthChart(ok.lengths)
                            Text(
                                Cycle.REGULARITY_NOTE,
                                fontSize = 11.sp,
                                color = AppTextMuted,
                            )
                        }
                    }
                }
            }

            // ── 分区：记录列表 ──
            if (records.isNotEmpty()) {
                item { SectionHeader(stringResource(R.string.s_0047da65)) }
                item {
                    Text(
                        stringResource(R.string.s_a01f2cc3, records.size),
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(records, key = { "${it.startEpochDay}:${it.source}" }) { r ->
                    AppSurface(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    buildString {
                                        append(Cycle.dateText(r.startEpochDay))
                                        r.endEpochDay?.let {
                                            append(" ~ ${Cycle.dateText(it)}")
                                        }
                                    },
                                    fontSize = 14.sp,
                                )
                                Text(
                                    buildString {
                                        append(sourceLabel(r.source))
                                        r.flow?.let { append(" · ${Cycle.Flow.label(it)}") }
                                        r.endEpochDay?.let {
                                            append(stringResource(R.string.s_faef795b, (it - r.startEpochDay + 1).toInt()))
                                        }
                                    },
                                    fontSize = 12.sp,
                                    color = AppTextMuted,
                                )
                            }
                            AppOutlineButton(
                                onClick = {
                                    scope.launch {
                                        SyncRepository.database.menstrualDao().deleteById(r.id)
                                    }
                                }
                            ) { Text(stringResource(R.string.s_002a9cc4)) }
                        }
                    }
                }
            }

            // ── 分区：数据口径 ──
            item { SectionHeader(stringResource(R.string.s_b1d4b05b)) }

            // ⑤ 数据口径 + 免责
            item {
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(stringResource(R.string.s_b1d4b05b), fontWeight = FontWeight.Bold)
                    // 5 段说明合并成一个可折叠块（默认收起，2026-09-21）：
                    // 常驻展开时这段灰字比上面的数据还长，整张卡一半高度都在讲口径。
                    CollapsibleNotes(
                        listOf(
                            Cycle.INTERPRET_NOTE,
                            Cycle.DATA_NOTE,
                            Cycle.NO_END_NOTE,
                            Cycle.FERTILE_NOTE,
                            Cycle.HC_NOTE,
                        ),
                        fontSize = 12.sp,
                    )
                    Text(
                        Disclaimer.cycle(lang),
                        fontSize = 12.sp,
                        color = CycleAccentColor,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // 录入对话框
        if (showEntryDialog) {
            CycleEntryDialog(
                today = today,
                existing = records,
                onDismiss = { showEntryDialog = false },
                onSave = { start, end, flow ->
                    scope.launch {
                        SyncRepository.database.menstrualDao().insert(
                            MenstrualRecordEntity(
                                startEpochDay = start,
                                endEpochDay = end,
                                flow = flow,
                                source = Cycle.Source.MANUAL,
                            )
                        )
                        SyncRepository.setEvent(ResText.of(R.string.s_b242c093, Cycle.dateTextRes(start)))
                    }
                    showEntryDialog = false
                },
            )
        }
    }

    /**
     * 周期长度柱状图：每根柱子是一次周期，高度 = 天数。
     *
     * 纵轴保证至少 20 天的跨度（与心率柱状图"至少 50"同理）：
     * 否则"每次都是 28 天"这种稳定周期会被拉成满屏起伏。
     */
    @Composable
    private fun CycleLengthChart(lengths: List<Cycle.CycleLength>) {
        val minD = lengths.minOf { it.days }.coerceAtMost(Cycle.MIN_CYCLE_DAYS)
        val maxD = lengths.maxOf { it.days }.coerceAtLeast(Cycle.MAX_CYCLE_DAYS)
        val lo = (minD - 5).coerceAtLeast(0)
        val hi = maxD + 5
        val span = (hi - lo).coerceAtLeast(20)

        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
        ) {
            val w = size.width / lengths.size.coerceAtLeast(1)
            val barW = w * 0.6f
            for ((i, len) in lengths.withIndex()) {
                val h = ((len.days - lo).toFloat() / span) * size.height
                val x = i * w + (w - barW) / 2
                drawRoundRect(
                    color = CycleColor,
                    topLeft = Offset(x, size.height - h),
                    size = Size(barW, h),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            // 中位数参照线
            val med = Cycle.medianDays(lengths.map { it.days })
            val medY = size.height - ((med - lo).toFloat() / span) * size.height
            drawLine(
                color = CycleAccentColor,
                start = Offset(0f, medY),
                end = Offset(size.width, medY),
                strokeWidth = 1.5f,
            )
        }
        Text(
            stringResource(R.string.s_66a72df7, Cycle.medianDays(lengths.map { it.days })),
            fontSize = 10.sp,
            color = AppTextMuted,
        )
    }

    /**
     * 录入对话框：开始日（必填）、结束日（可空）、流量（可空）。
     *
     * 日期用三个下拉（年/月/日）还是 DatePicker？这里用**文本输入 + 校验**：
     * 经期录入的日期范围是"今天往前几年"，DatePicker 的年月日三级滚轮在竖屏上太占地方，
     * 且用户更常输入"上个月 12 号"这种相对日期。用 `Cycle.checkInput` 做校验，
     * 输入非法时在按钮旁直接提示原因，不落库。
     */
    @Composable
    private fun CycleEntryDialog(
        today: Long,
        existing: List<MenstrualRecordEntity>,
        onDismiss: () -> Unit,
        onSave: (start: Long, end: Long?, flow: String?) -> Unit,
    ) {
        var startText by remember { mutableStateOf("") }
        var endText by remember { mutableStateOf("") }
        var flow by remember { mutableStateOf<String?>(null) }
        var error by remember { mutableStateOf<String?>(null) }

        fun parse(text: String): Long? = runCatching {
            val parts = text.trim().split("-", "/", ".")
            val y = parts[0].toInt()
            val m = parts[1].toInt()
            val d = parts[2].toInt()
            java.time.LocalDate.of(y, m, d).toEpochDay()
        }.getOrNull()

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.s_d0f06799)) },
            text = {
                Column {
                    androidx.compose.material3.OutlinedTextField(
                        value = startText,
                        onValueChange = { startText = it },
                        label = { Text(stringResource(R.string.s_805214be)) },
                        singleLine = true,
                    )
                    androidx.compose.material3.OutlinedTextField(
                        value = endText,
                        onValueChange = { endText = it },
                        label = { Text(stringResource(R.string.s_aaace14c)) },
                        singleLine = true,
                    )
                    Text(stringResource(R.string.s_e9367195), fontSize = 12.sp, color = AppTextMuted,
                        modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            null to stringResource(R.string.s_1b4b78df),
                            Cycle.Flow.LIGHT to stringResource(R.string.s_00005c11),
                            Cycle.Flow.MEDIUM to stringResource(R.string.s_00004e2d),
                            Cycle.Flow.HEAVY to stringResource(R.string.s_0000591a),
                        ).forEach { (v, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = flow == v,
                                onClick = { flow = v },
                                label = { Text(label) },
                            )
                        }
                    }
                    error?.let {
                        Text(it, fontSize = 12.sp, color = HrvWarnColor,
                            modifier = Modifier.padding(top = 8.dp))
                    }
                }
            },
            confirmButton = {
                AppOutlineButton(onClick = {
                    val start = parse(startText)
                    if (start == null) {
                        error = this@MainActivity.getString(R.string.s_a802102b)
                        return@AppOutlineButton
                    }
                    val end = if (endText.isBlank()) null else parse(endText)
                    if (endText.isNotBlank() && end == null) {
                        error = this@MainActivity.getString(R.string.s_3da1b600)
                        return@AppOutlineButton
                    }
                    val check = Cycle.checkInput(
                        startEpochDay = start,
                        endEpochDay = end,
                        todayEpochDay = today,
                        others = existing.map { it.toEntry() },
                    )
                    when (check) {
                        is Cycle.InputCheck.Invalid -> error = check.reason
                        is Cycle.InputCheck.Ok -> onSave(start, end, flow)
                    }
                }) { Text(stringResource(R.string.s_0029396f)) }
            },
            dismissButton = {
                AppOutlineButton(onClick = onDismiss) { Text(stringResource(R.string.s_002b540a)) }
            },
        )
    }

    // ───────────────────── 用户页的排版零件（2026-09-19 重排） ─────────────────────
    //
    // 这一页原来最乱的地方是「一行一张大卡片」：八张卡各带一个加粗标题 + 一大段说明 +
    // 几个按钮，扫过去看不出哪些是同一类东西、也不知道哪一行能点。
    // 改成系统设置那种排版：**分组标题 + 一个圆角容器装多行 + 行间细分隔线**，
    // 每行只有「标签 …… 值 ›」三样；长的解释挪进弹窗，不再摊在页面上。

    /**
     * 「授权」面板里的一行（2026-10-07）：标签 + 状态（+ 未授权时的「去授权」）。
     *
     * ⚠ 状态判据是**结构化的 `Boolean?`**：`null` = 还没查出来（连接中）、`true` = 已授权、
     *   `false` = 未授权。**不拿显示文案去比**（模块 #15 的纪律：译文一变判据就静默失效）。
     *
     * ⚠ [notSupportedWhenFalse]：能量评分 / 睡眠呼吸暂停这两项**分区域、分机型放开**，
     *   `false` 的绝大多数情况是"本机根本没有这个数据类型"，不是用户没勾。两者都写
     *   「未授权」并配一个「去授权」按钮，等于怂恿用户去系统设置里找一个根本不存在的开关。
     */
    @Composable
    private fun AuthDetailRow(
        label: String,
        ok: Boolean?,
        onAuth: () -> Unit,
        notSupportedWhenFalse: Boolean = false,
        /** true = 本机根本授权不了 ⇒ **不给按钮**（给了只会让人反复点）。 */
        authDisabled: Boolean = false,
        /**
         * true = 这次**问不到**三星健康（服务被系统冻结，9004 Remote exception）。
         * 此时 `ok == null` 不能写「检测中…」——那会**永久卡住**，要直接说清原因。
         */
        unknownHint: Boolean = false,
    ) {
        // ⚠⚠ 2026-11-06 修排版：原来是 `Row { Text(标签, weight(1f)); Text(状态) }`，
        //   而**状态那一侧不带 weight** ⇒ Row 先按内容宽度量它，长状态把标签那一侧挤到 0 宽，
        //   中文于是变成 **一字一列竖排**（真机截图：「读取权限」竖着写、右边压着一行小字）。
        //   判据与 `EntryRow` 同一条：**两个 Text 至少要有一个能压缩**。
        //   ⇒ 但这里不能简单给右边也加 weight：那会变成左右各占一半，
        //     "已授权"跑到半屏位置、跟标签离得老远。
        //     ⇒ 长的那一种（`unknownHint` 的整句解释，26 个字）**换到标签下一行整行显示**，
        //       短状态（已授权/检测中/本机不支持）留在右侧 —— 它们本来就只有 4~6 字。
        Row(
            Modifier.fillMaxWidth().padding(vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
            when {
                ok == null -> Text(
                    // ⚠⚠ 问不到 ≠ 检测中：`unknownHint` 是"三星健康服务被冻结、问不出来"，
                    //   写成"检测中…"会**永久卡住**（用户以为在转圈，其实永远不会回来）。
                    // ⚠ 完整解释（26 字）已提到「三星健康」分区标题下**只说一次** ——
                    //   原来 5 行各显示一遍、共 130 字，把这一页撑长了一倍。
                    stringResource(if (unknownHint) R.string.s_24cfdfaa else R.string.s_2607c610),
                    fontSize = 12.sp, color = AppTextMuted,
                )
                    ok -> Text(
                        stringResource(R.string.s_18ccf95d), fontSize = 12.sp, color = AppTextMuted,
                    )
                    notSupportedWhenFalse -> Text(
                        stringResource(R.string.s_f2592e8d), fontSize = 12.sp, color = AppTextMuted,
                    )
                    else -> {
                        Text(
                            stringResource(R.string.s_1b36f455),
                            fontSize = 12.sp, color = SettingsWarnColor,
                        )
                        if (!authDisabled) {
                            Spacer(Modifier.width(8.dp))
                            AppOutlineButton(onClick = onAuth) {
                                Text(stringResource(R.string.s_1620386e), fontSize = 12.sp)
                            }
                        }
                    }
                }
        }
    }

    // ───────────────────── 授权页（2026-10-07） ─────────────────────
    //
    // ⚠ 这一页以前是「用户」页里**在下面展开**的一块面板（`showAuth` 布尔 + 内联 Card）。
    //   用户的原话：「一件授权点击后是在下面显示出来，我需要的是出现在另一个新的页面里」。
    //
    // 形态与公告页/记录页一致：顶部「返回」+ 标题，下面整屏铺开，返回路径唯一。
    // 内容本身一字未改（一键授权 + 三星 8 项 + HC 9 项 + 三段说明），只换了承载方式 ——
    // 这块内容比「用户」页其它所有内容加起来还长，挤在页面中间会把
    // 「个人 / 数据 / 关于」的分区读断。
    /**
     * 整屏的授权页。
     *
     * ⚠ 逐项明细里保留「去授权」的每一项都走**同一个** [onAuthSamsungAwait]：单项的
     *   「去授权」也发同一次**合并请求**（三星的授权页是一屏列出全部，没法只带一项去）。
     *   这不是偷懒 —— 分项申请正是"一个个点"的来源（见 `SamsungHealthReader.request`）；
     *   用户在那一屏里勾他要的就行。
     *
     * ⚠ 在途状态 [busy] 就地放这一页：授权页是整屏的，跑流程时用户看得见按钮变灰。
     *   放在父层反而要往上传两个回调。
     */
    @Composable
    private fun PersonAuthScreen(
        samsungGranted: Boolean?,
        samsungSleepOk: Boolean?,
        samsungSkinTempOk: Boolean?,
        samsungBiaOk: Boolean?,
        samsungProfileOk: Boolean?,
        hcAvailable: Boolean,
        hcGranted: Boolean,
        hcMissing: Int?,
        hcAuthErr: String?,
        onAuthHC: () -> Unit,
        onAuthSamsungAwait: suspend () -> Unit,
        /** 只申请**指定那一组**权限：每行的「去授权」用它（2026-10-17）。 */
        onAuthSamsungGroup: suspend (Set<Permission>) -> Unit,
        /**
         * 跑授权流程用的作用域，**由 MainScreen 传入**。
         *
         * ⚠⚠ 不能用 `rememberCoroutineScope()`（2026-10-17 修）：那个作用域绑定在**本页的
         *   composition** 上。点「去授权」会拉起三星的授权 Activity，本页一旦离场，
         *   作用域就被销毁 ⇒ 协程直接抛 `ForgottenCoroutineScopeException`，
         *   而 `busy` 永远停在 true（界面卡在「正在请求授权…」、之后再点都被 `if (!busy)` 挡掉，
         *   表现为"点了没反应"）。MainScreen 的作用域全程在组合里，不受二级页进出影响。
         */
        authScope: CoroutineScope,
        onBack: () -> Unit,
    ) {
        val ctx = this@MainActivity

        // 「HC 缺失集合」不是 MainScreen 穿进来的参数，这一页要逐项列出，就在这里查。
        // ⚠ 「写入权限」那一行**已删**（2026-10-17）：三星策略上不允许第三方写健康数据
        //   （`2003 Permission is not allowed due to SDK policy`），永远显示"未授权"只会让人白点。
        var hcMissingPerms by remember { mutableStateOf<Set<String>?>(null) }

        // 能量评分 / 睡眠呼吸暂停：**分区域、分机型放开**，false 绝大多数是"本机根本没有这个
        // 数据类型"。状态就地查（只在这一页显示），不往 MainScreen 穿两个参数。
        var energyOk by remember { mutableStateOf<Boolean?>(null) }
        var apneaOk by remember { mutableStateOf<Boolean?>(null) }
        // 本机能不能用三星健康数据接口（非三星机型 / 没装三星健康 ⇒ 全都授不了）
        var samsungEnv by remember { mutableStateOf(SamsungHealthReader.environment(ctx)) }

        // 在途状态：授权流程跑着的时候按钮要禁用、并显示「正在请求授权…」。
        // ⚠ **没有**"一次性结果消息"这个状态 —— 页面下方那一行结论是**由授权状态推出来的**。
        //   两套（消息 + 状态）并存迟早会对不上，而对不上时用户只会相信消息。
        var busy by remember { mutableStateOf(false) }

        // 重查一次授权现状。⚠ 只在这几个时机调用：进页面、HC 结果回来（`hcMissing` 变）、
        //   以及每次授权动作跑完 —— **不做成"随每次同步重查"**，那会白白多连三星 SDK。
        val refreshAuth: suspend () -> Unit = {
            hcMissingPerms = runCatching {
                HealthConnectManager.missingWritePermissions(ctx)
            }.getOrNull()
        }
        // 首次进入 + `hcMissing` 变化（= HC 授权页回来了，或首次查完）。
        LaunchedEffect(hcMissing) {
            refreshAuth()
            if (samsungGranted == true) {
                energyOk = SamsungHealthReader.hasEnergyScorePermission(ctx)
                apneaOk = SamsungHealthReader.hasSleepApneaPermission(ctx)
            } else {
                // ⚠ 不查也要给个明确值：留 null 会让这两行**永久显示「检测中…」**
                //   （2026-10-17 截图里就是卡在那）。它们是"基础读取授上了才谈得上"的项。
                energyOk = null
                apneaOk = null
            }
        }

        // 这一页的汇总口径与入口行**完全一致**（只数用户能解决的项目）—— 见 `UserTab` 里
        // [authMissing] 的说明。两处口径必须一样，否则"进去前显示 3 项、进去后显示 5 项"。
        val authMissing = listOf(
            samsungGranted, samsungSleepOk, samsungSkinTempOk, samsungBiaOk, samsungProfileOk,
        ).count { it == false } + (if (hcAvailable) (hcMissing ?: 0) else 0)

        // 单项的「去授权」：**只发这一组**，回来重查一遍。
        // ⚠ 此前这里发的是合并请求，用户点某一项却弹出整页全部条目（2026-10-17 反馈）。
        val authOne: (Set<Permission>) -> Unit = { perms ->
            if (!busy) {
                busy = true
                authScope.launch {
                    // ⚠ `finally` 里复位：协程被取消/抛异常时也必须解锁，否则界面永久卡在
                    //   「正在请求授权…」，之后再点全部被 `if (!busy)` 挡掉（"点了没反应"）。
                    try {
                        onAuthSamsungGroup(perms)
                    } finally {
                        runCatching { refreshAuth() }
                        // 用户可能刚去装了/启用了三星健康 ⇒ 重探一次再渲染
                        runCatching { samsungEnv = SamsungHealthReader.environment(ctx) }
                        busy = false
                    }
                }
            }
        }
        // 「一键授权」：三星 → **等它返回** → Health Connect。
        // ⚠ 必须串行：两个系统授权页不能同时弹（后弹的那个会被吞掉，
        //   表现是"第二个来源的授权页根本没出来"）。
        val authAll: () -> Unit = {
            if (!busy) {
                busy = true
                authScope.launch {
                    try {
                        onAuthSamsungAwait()
                    } finally {
                        runCatching { refreshAuth() }
                        busy = false
                    }
                    if (hcAvailable) onAuthHC()
                }
            }
        }

        Column(Modifier.fillMaxSize()) {
            DetailHeader(stringResource(R.string.s_003355db), onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                item {
                    AppSurface(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            // ⚠⚠ 2026-11-06 用户要求：这段说明**整段删掉**（原 `s_59859e9f`：
                            //   「一次把三星健康与 Health Connect 用到的权限都问一遍。两个来源的
                            //   授权页不能同时打开，所以会先后弹两次。」）
                            //   —— 用户原话「用户没必要看到这些」。他要的只是"点这个按钮"，
                            //   "为什么会弹两次"是开发者视角的解释。
                            //   ⇒ 卡片第一行直接就是按钮，不再有引导文字。
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AppPrimaryButton(enabled = !busy, onClick = authAll) {
                                    Text(stringResource(R.string.s_9ae8d879))
                                }
                                Spacer(Modifier.width(10.dp))
                                // 这一行的结论文案**由授权状态推出来**，不是一次性消息 ——
                                // 授权完状态一变它自己就跟着变，不会出现"消息与下面的明细打架"。
                                Text(
                                    when {
                                        busy -> stringResource(R.string.s_48192f37)
                                        authMissing == 0 -> stringResource(R.string.s_21d47d8d)
                                        else -> stringResource(R.string.s_99c47e37, authMissing)
                                    },
                                    fontSize = 12.sp,
                                    color = if (!busy && authMissing > 0) SettingsWarnColor
                                    else AppTextMuted,
                                )
                            }

                            // ⚠⚠ 2026-11-06 删掉「设备事实行」（原样是
                            //   `unknown Android SDK built for x86_64 · 三星健康 未安装`）。
                            //   用户没必要看 Build 型号、系统版本和三星健康版本号。
                            //   ⚠ 只删**显示**，判据 `samsungEnv.canTry` 照旧生效 ——
                            //     下面那句橙字（"未检测到可用的三星健康数据接口…"）
                            //     已经把"没装 / 本机用不了"说清楚了，信息没有丢。
                            Spacer(Modifier.height(10.dp))
                            if (!samsungEnv.canTry) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_5771366a),
                                    fontSize = 11.sp, color = SettingsWarnColor,
                                )
                            } else if (samsungGranted == false) {
                                // ⚠⚠ 2026-10-18 更正：**"非三星手机"不是根因**。
                                //   实测 OPPO 机上三星健康 7.0 也能**全部授权成功**，前提是
                                //   手机端三星健康开了「开发者模式」、手表端开了 Dev mode。
                                //   没开时授权页根本不弹 —— 用户看到的就是"点了没反应 / 全部未授权"。
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_fc7a2786),
                                    fontSize = 11.sp, color = SettingsWarnColor,
                                )
                            }

                            // ── 三星健康 ──
                            Spacer(Modifier.height(14.dp))
                            Text(
                                stringResource(R.string.s_8fc12af0),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                            // ⚠⚠ 2026-11-06：「问不到」是**全局**状态（三星健康服务被冻结 ⇒
                            //   下面 8 项一个都问不出来），不是每行各自的毛病 ⇒ 这里**说一次**，
                            //   行内只标「读不到」。原来 5 行各显示一遍完整解释（共 130 字）。
                            // ⚠ 只在 `canTry` 时显示：没装三星健康的情况由下面那句橙字负责，
                            //   否则会把"未安装"说成"服务未运行"，指向用户去点一个不存在的开关。
                            if (samsungEnv.canTry && samsungGranted == null) {
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.s_8e634cbc),
                                    fontSize = 11.sp, lineHeight = 15.sp, color = AppTextMuted,
                                )
                            }
                            // ⚠ 每一行只申请**自己那一组**（2026-10-17）：
                            //   点「写入权限」的「去授权」时，弹出的授权页只列写入项。
                            AuthDetailRow(stringResource(R.string.s_d7eea218), samsungGranted,
                                { authOne(SamsungHealthReader.readPermissions) },
                                authDisabled = !samsungEnv.canTry,
                                unknownHint = samsungGranted == null)
                            // ⚠ 「写入权限」行已删：三星按策略不让第三方写（2003），点了也没用。

                            AuthDetailRow(stringResource(R.string.s_8f9ad5a8), samsungSleepOk,
                                { authOne(setOf(SamsungHealthReader.sleepPermission)) },
                                authDisabled = !samsungEnv.canTry,
                                unknownHint = samsungGranted == null)
                            AuthDetailRow(stringResource(R.string.s_e48b58fe), samsungSkinTempOk,
                                { authOne(setOf(SamsungHealthReader.skinTempPermission)) },
                                authDisabled = !samsungEnv.canTry,
                                unknownHint = samsungGranted == null)
                            AuthDetailRow(stringResource(R.string.s_a5d1b470), samsungBiaOk,
                                { authOne(setOf(SamsungHealthReader.bodyCompositionPermission)) },
                                authDisabled = !samsungEnv.canTry,
                                unknownHint = samsungGranted == null)
                            AuthDetailRow(stringResource(R.string.s_e0c5f91f), samsungProfileOk,
                                { authOne(setOf(SamsungHealthReader.userProfilePermission)) },
                                authDisabled = !samsungEnv.canTry,
                                unknownHint = samsungGranted == null)
                            // 这两项**分区域、分机型放开**：false 绝大多数是"本机没有这个
                            // 数据类型"，所以显示「本机不支持」且**不给去授权按钮** ——
                            // 给按钮等于怂恿用户去系统设置里找一个不存在的开关。
                            // ⚠ 这两项只有在**基础读取已授上**时才有意义：否则要么卡「检测中…」，
                            //   要么被误报成「本机不支持」。没授上就整行不显示（2026-10-17 修）。
                            if (samsungGranted == true && samsungEnv.canTry) {
                                AuthDetailRow(
                                    stringResource(R.string.s_fd7b5a4f), energyOk,
                                    { authOne(setOf(SamsungHealthReader.energyScorePermission)) },
                                    notSupportedWhenFalse = true,
                                )
                                AuthDetailRow(
                                    stringResource(R.string.s_8beafff0), apneaOk,
                                    { authOne(setOf(SamsungHealthReader.sleepApneaPermission)) },
                                    notSupportedWhenFalse = true,
                                )
                            }
                            // ⚠ 把 SDK 的原话露出来：没有它，"全是未授权"这种反馈只能靠猜。
                            val sdkErrs = listOfNotNull(
                                SamsungHealthReader.lastSleepAuthError,
                                SamsungHealthReader.lastBiaAuthError,
                                SamsungHealthReader.lastSkinTempAuthError,
                                SamsungHealthReader.lastEnergyAuthError,
                                SamsungHealthReader.lastApneaAuthError,
                            ).distinct()
                            if (sdkErrs.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_46b6dd1a, sdkErrs.joinToString("；")),
                                    fontSize = 10.sp, color = AppTextMuted,
                                )
                            }
                            // ⚠⚠ 2026-11-06 删：「能量评分与睡眠呼吸暂停是分区域、分机型放开的，
                            //   显示「本机不支持」通常与你的设置无关。」—— 同样是开发者视角的解释。
                            //   ⚠ 那两行的状态词本身就是「本机不支持」（`s_f2592e8d`），
                            //     删掉这段不影响理解，只是不再额外解释"为什么"。

                            // ── Health Connect ──
                            Spacer(Modifier.height(14.dp))
                            Text("Health Connect", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            val hcMiss = hcMissingPerms
                            when {
                                !hcAvailable -> Text(
                                    stringResource(R.string.s_f2592e8d),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                hcMiss == null -> Text(
                                    stringResource(R.string.s_2607c610),
                                    fontSize = 13.sp, color = AppTextMuted,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                                else -> {
                                    for ((perm, name) in HealthConnectManager.writePermissionLabels) {
                                        AuthDetailRow(name, ok = perm !in hcMiss, onAuth = onAuthHC)
                                    }
                                    if (hcMiss.isNotEmpty()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            stringResource(R.string.s_11eddd5a, hcMiss.size) +
                                                stringResource(R.string.s_72299396),
                                            fontSize = 11.sp, color = SettingsWarnColor,
                                        )
                                    }
                                }
                            }
                            // 授权页打不开时的原因（原来挂在入口行的 note 里，收进来后要跟着搬）
                            if (hcAuthErr != null) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_22674666, hcAuthErr) +
                                        stringResource(R.string.s_b2cf431e) +
                                        stringResource(R.string.s_04c47c3c),
                                    fontSize = 11.sp, color = SettingsWarnColor,
                                )
                            } else if (hcAvailable && !hcGranted) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_452d4754) +
                                        stringResource(R.string.s_cf103fee, HealthConnectManager.writePermissions.size) +
                                        stringResource(R.string.s_8cefef4a) +
                                        stringResource(R.string.s_04c47c3c),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            } else if (hcGranted) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    stringResource(R.string.s_42d22100),
                                    fontSize = 11.sp, color = AppTextMuted,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** 分组标题（个人 / 数据 / 功能开关 / 其他）。 */
    @Composable
    private fun SettingsSection(title: String) {
        Text(
            title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 6.dp),
        )
    }

    /** 一个圆角容器，装同一组的多行。 */
    @Composable
    private fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 2.dp), content = content)
        }
    }

    /**
     * **分区标题**（2026-10-05 用户反馈「这么多内容堆一起看起来很乱」）。
     *
     * ⚠⚠ 为什么需要它：血压预估那一卡里，进度、提示、自评、配对样本、预估历史
     *   原来**全是同样字号的段落**，没有层级 ⇒ 读者分不出"哪几行是一件事"。
     *   用户的原话是「给不同内容做分区处理……所有功能都一样」。
     *
     * ⇒ 每个分区现在有：**一条分隔线 + 一个加粗小标题**，把彼此切开。
     * ⚠ 不引入子卡片（那会在大卡里再套一层边框，视觉更碎）；
     *   分隔线 + 标题是**最轻**的分区手段。
     *
     * ---
     *
     * [isFirstSection] = 这是**整页第一个分区**（2026-10-12）。
     *
     * ⚠⚠ 为什么必须能关掉那根线：所有功能页的外壳都是「`DetailHeader` 标题行 +
     *   内容」，于是**这一页第一个 `SectionHeader` 的上分隔线正好压在标题行的下沿**
     *   —— 线的上方根本没有内容可分隔，纯粹占位置。用户原话：
     *   「很多功能的最上面都有一根线，最上面没必要有线，占位置」。
     *
     * ⇒ 判据：**线只画在两个内容块之间**。上方是标题行（不是内容）⇒ 不画。
     *   ⚠ 不只影响好看：那根线上下各带 10dp 的 `Spacer`，一屏内白扔掉约 21dp。
     *
     * ⚠⚠ 页面里**前面还有条件渲染的横幅**时（睡眠页 / 血氧页的 `StaleDataBanner`），
     *   要把那个条件一起传进来，例如 `isFirstSection = state.fallbackEpochDay == null`：
     *   横幅在时它**就是**"上面的内容块"，线要画；横幅不在时才该省掉。
     *   写死成 `true` 会让"有横幅"那一态下标题直接贴在卡片底下。
     */
    @Composable
    private fun SectionHeader(title: String, isFirstSection: Boolean = false) {
        // 第一个分区：连 Spacer 一起省掉（省掉线却留着 10dp 空档，照样是白占位置）。
        if (!isFirstSection) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color(0x1FFFFFFF))
            )
            Spacer(Modifier.height(10.dp))
        }
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }

    /**
     * 模块之间的**纯分隔线**（2026-10-05）。
     *
     * ⚠ 与 [SectionHeader] 的分工：
     *   · `SectionHeader` = **分隔线 + 标题**（"这里开始一个新分区"）
     *   · `SectionRule`   = **只有分隔线**（模块各有标题、只是视觉上粘在一起时用）
     *
     * ⚠⚠ 为什么必须有它：心电详情页那些模块（指标 / 逐拍间期 / 伪迹 / 说明 / 呼吸率）
     *   原来**只用 `Spacer` 分隔** —— 而 Spacer 在浅色背景上**看不见**，
     *   几个模块的灰字连成一片。用户的原话是「这么多内容堆一起看起来很乱」。
     */
    @Composable
    private fun SectionRule() {
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1FFFFFFF))
        )
        Spacer(Modifier.height(10.dp))
    }

    /** 行间分隔线：从文字左边缘开始（与系统设置一致），右侧通到底。 */
    @Composable
    private fun SettingsDivider() {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .height(1.dp)
                .background(Color(0x1FFFFFFF))
        )
    }

    /**
     * 分组里的一行：左边标签，右边值 + 箭头，**整行可点**。
     *
     * [valueColor] 只用来表达状态（见 [SettingsWarnColor] 的说明）。
     */
    @Composable
    private fun SettingsRow(
        label: String,
        value: String = "",
        valueColor: Color? = null,
        chevron: Boolean = true,
        note: String? = null,
        onClick: (() -> Unit)? = null,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (value.isNotEmpty()) {
                    Text(
                        value,
                        fontSize = 13.sp,
                        color = valueColor ?: AppTextMuted,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(end = if (chevron) 6.dp else 0.dp),
                    )
                }
                if (chevron) Text("›", fontSize = 20.sp, color = AppTextMuted)
            }
            if (note != null) {
                Text(
                    note,
                    fontSize = 11.sp,
                    color = AppTextMuted,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }

    /**
     * 开关的统一配色。
     *
     * ⚠ 必须**显式给色**：主题是 `lightColorScheme`（primary 近黑、surfaceVariant 极浅），
     *   Material3 默认的未选中滑块色在浅色卡片上几乎与底色同色 ——
     *   实测用户看到的是一颗"全白的开关，根本看不出这里有个按钮"（2026-10-17 反馈经期追踪）。
     */
    @Composable
    private fun AppSwitchColors() = SwitchDefaults.colors(
        checkedThumbColor = Color.White,
        checkedTrackColor = AppTextPrimary,
        checkedBorderColor = AppTextPrimary,
        uncheckedThumbColor = Color(0xFF9C8CB0),
        uncheckedTrackColor = AppMutedFill,
        uncheckedBorderColor = Color(0xFF9C8CB0),
    )

    /** 带开关的一行。**整行可点**（点行 = 切开关）—— 命中区比光去点那个小 Switch 大得多。 */
    @Composable
    private fun SettingsSwitchRow(
        label: String,
        checked: Boolean,
        note: String? = null,
        onCheckedChange: (Boolean) -> Unit,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 15.sp, modifier = Modifier.weight(1f))
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    colors = AppSwitchColors(),
                )
            }
            if (note != null) {
                Text(
                    note,
                    fontSize = 11.sp,
                    color = AppTextMuted,
                    modifier = Modifier.padding(top = 2.dp, end = 56.dp),
                )
            }
        }
    }

    /**
     * 语言选择弹窗（模块 #15）。
     *
     * 每一项显示该语言**用自己的写法**（简体中文 / 繁體中文 / English / 한국어），
     * 选中项前面打勾。这不是"没翻译"，而是**语言选择器就该这么显示** ——
     * 让看不懂当前语言的用户也能认出自己要选哪一行。
     */
    @Composable
    private fun LanguagePickerDialog(
        current: Language,
        onPick: (Language) -> Unit,
        onDismiss: () -> Unit,
    ) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.language_title), fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Language.entries.forEach { lang ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onPick(lang) }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                if (lang == current) "✓" else "",
                                fontSize = 15.sp,
                                color = Color(0xFF1976D2),
                                modifier = Modifier.padding(end = 10.dp),
                            )
                            Text(lang.displayName, fontSize = 15.sp)
                        }
                    }
                }
            },
            confirmButton = { AppOutlineButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }

    /** 只有一段说明的弹窗 —— 长文案集中在这里，页面上只留一行。 */
    @Composable
    private fun InfoDialog(title: String, body: String, onDismiss: () -> Unit) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(title, fontWeight = FontWeight.Bold) },
            text = { Text(body, fontSize = 13.sp) },
            confirmButton = { AppOutlineButton(onClick = onDismiss) { Text(stringResource(R.string.s_1faf4f0c)) } },
        )
    }

    /**
     * 跳到**手机端**三星健康的「抗氧化指数」页。
     *
     * ## ⚗⚗⚗ 为什么是「跳过去」而不是「读取数值存本地」
     *
     * 用户 2026-10-04 要求「读取三星健康里的抗氧化指数并保存本地，可以看历史」。
     * **这条路被 SDK 层面堵死了**，而且是**从 SDK 里查出来的**、不是凭印象：
     *
     * 1. **Data SDK 的 25 个数据类型里没有抗氧化指数**（`DataType$*Type` 全类枚举）。
     * 2. **`BodyCompositionType` 的 13 个字段里也没有** —— 那些是
     *    WEIGHT / BODY_FAT / FAT_FREE / MUSCLE_MASS / SKELETAL_MUSCLE / BMI /
     *    BASAL_METABOLIC_RATE / TOTAL_BODY_WATER / HEIGHT / FAT_FREE_MASS /
     *    BODY_FAT_MASS / SKELETAL_MUSCLE_MASS / DATA_TYPE_NAME。
     *    ⇒ **没有可读的对象**，"读取"这一步本身就无从下手。
     * 3. 就算有，**手表端也测不到**：那个指数测皮肤类胡萝卜素对**蓝光**的吸收，
     *    而 Sensor SDK 21 个采集器**没有蓝光通道**（只有绿/红/红外）——
     *    通道与数据**两头都没开放**。
     *
     * ⚠⚠ **别用"体脂率换算抗氧化"或"从 HRV 推类胡萝卜素"来"实现"它** ——
     *   那是**编数字**。同一个道理本项目拒绝过一次血压分级（因为模型能力到不了）。
     *
     * ## 所以本 App 能做的最实在的事
     *
     * **别让用户自己翻三星健康**（那个页面很深），并**明说清数据不在我们这里**。
     *
     * ## ⚠ 手机端与手表端包名不同
     *
     * 手表端是 `com.samsung.android.wear.shealth`，手机端是 `com.samsung.android.health` ——
     * **写错会静默跳到"应用未找到"**。两处清单都加了 `<queries>`（Android 11+
     * 不写则运行期抛 "Package not visible"，而**编译完全正常**）。
     *
     * ## 三级兜底（与手表端同一套）
     *
     * 精确跳转 → 只打开三星健康（**必须 Toast 说明**，否则用户以为按钮坏了）→
     * 明说找不到。**绝不能让入口变成点了没反应的死键。**
     */
    private fun openSamsungAntioxidantPhone() {
        val pkg = "com.samsung.android.health"
        // ① 精确跳转（组件名来自用户在活动启动器里看到的；未公开，随版本可能变）
        val direct = android.content.Intent()
            .setComponent(android.content.ComponentName(pkg, "$pkg.app.antioxidant.AntioxidantActivity"))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(direct) }.isSuccess) return
        // ② 退化：只打开三星健康，用户至少能自己在里面找。
        //    **这里必须说一句** —— 静默落到首页，用户会以为按钮点错了，
        //    而"精确跳转失败"本身是需要反馈的信号（组件名可能已变）。
        packageManager.getLaunchIntentForPackage(pkg)?.let { home ->
            home.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { startActivity(home) }.isSuccess) {
                // ⚠ 手机端**用 setEvent 而不是 Toast**（与本页其它提示一致），
                //   且它收的是 [ResText] 不是 String ⇒ 必须 `ResText.of`。
                //   ⚠ 别用 `getString`：那是本地化后的 String，**切语言不会重算**。
                SyncRepository.setEvent(ResText.of(R.string.s_a40e6158))
                return
            }
        }
        // ③ 都不行：说清楚，别让按钮"点了没反应"
        SyncRepository.setEvent(ResText.of(R.string.s_7008037e))
    }

    /**
     * 「用户」页。原本是「设备」页里那一堆设置，2026-09-19 重排成系统设置的样式。
     *
     * 状态类的东西（手表在不在线、待同步几条）**刻意不在这里** ——
     * 它们留在了功能页顶部的状态条上，因为要修「同步不上去」的人正是先看到那一条的人。
     */
    /**
     * 「更新设置」整屏页（2026-10-21）。
     *
     * 分两组：**手机端** / **手表端**，各含「自动检查更新（默认开）」+「检查更新（手动）」。
     * 优先级规则：手机端有更新时先更新手机；手机已是最新后才提示手表端。
     */
    /** 手表更新弹窗：内容（notes）+ 进度条 + 结果。 */
    @Composable
    private fun WatchUpdateDialog(
        state: WatchUpd,
        onStart: () -> Unit,
        onDismiss: () -> Unit,
    ) {
        // ⚠ 这是**手表**更新弹窗 ⇒ "当前"必须取**手表**的版本（HELLO 里读到的），
        //   不是手机自己的版本（之前取错了，会显示成"当前 v188 → v183"）。
        val curWatchVer = remember { AppUpdatePrefs.watchVersion(this@MainActivity) }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Text(
                    if (state is WatchUpd.Failed) stringResource(R.string.s_e8b7c532)
                    else stringResource(R.string.s_8ba513e1)
                )
            },
            text = {
                Column {
                    if (state is WatchUpd.Offer) {
                        Text(
                            stringResource(R.string.s_668867a9) + " v" + curWatchVer +
                                " → v" + state.info.versionCode,
                            fontSize = 14.sp,
                        )
                        if (state.info.notes.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.s_ade091fa),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(state.info.notes, fontSize = 13.sp)
                        }
                        Spacer(Modifier.height(10.dp))
                        // 说清楚"怎么装"：不再走蓝牙，去「安装」页用无线 ADB 装
                        Text(
                            stringResource(R.string.s_c9c8de46),
                            fontSize = 12.sp,
                            color = AppTextMuted,
                        )
                    } else if (state is WatchUpd.Failed) {
                        Text(state.detail)
                    }
                }
            },
            confirmButton = {
                if (state is WatchUpd.Failed) {
                    AppPrimaryButton(onClick = onDismiss) { Text(stringResource(R.string.s_1faf4f0c)) }
                } else {
                    AppPrimaryButton(onClick = onStart) { Text(stringResource(R.string.s_002f5fe0)) }
                }
            },
            dismissButton = {
                if (state is WatchUpd.Offer) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.s_1faf4f0c)) }
                }
            },
        )
    }

    /** 简易进度条（不依赖 Material3 的 LinearProgressIndicator 版本差异）。 */
    @Composable
    private fun Bar(pct: Float) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0x1FFFFFFF)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(pct.coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0xFF6B8AFD)),
            )
        }
    }

    @Composable
    private fun UpdateScreen(
        phoneAuto: Boolean,
        onPhoneAuto: (Boolean) -> Unit,
        onCheckPhone: () -> Unit,
        onCheckWatch: () -> Unit,
        onBack: () -> Unit,
    ) {
        val ctx = this@MainActivity
        var watchAuto by remember { mutableStateOf(AppUpdatePrefs.watchAuto(ctx)) }
        val watchVer = remember { AppUpdatePrefs.watchVersion(ctx) }

        Column(
            Modifier
                .fillMaxSize()
                // ⚠ 状态栏避让由 `Scaffold` 的 topBar 负责，这里再加一次就是双份（实测多出 24dp）。
                .verticalScroll(rememberScrollState()),
        ) {
            DetailHeader(title = stringResource(R.string.s_25e52e0d), onBack = onBack)

            // ── 手机端 ──
            SettingsSection(stringResource(R.string.s_68534d38))
            SettingsGroup {
                SettingsSwitchRow(
                    label = stringResource(R.string.s_0d0b8357),
                    checked = phoneAuto,
                    note = stringResource(R.string.s_b1ef9598),
                    onCheckedChange = onPhoneAuto,
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.s_4d36128b),
                    value = "",
                    chevron = true,
                    onClick = onCheckPhone,
                )
            }

            // ── 手表端 ──
            //
            // ⚠⚠ 2026-10-22：这里原来把"检查/推送结果"（含长错误串）写进 `value`，
            //   而 `value` 是**右对齐单行** ⇒ 把左边的「检查更新」挤成一列竖排单字（用户截图）。
            //   现在这行只做**触发**：点一下 → 走统一的 `WatchUpdateDialog`（内容/进度/错误都在弹窗里）。
            Spacer(Modifier.height(6.dp))
            SettingsSection(stringResource(R.string.s_f1f8cf6b))
            SettingsGroup {
                SettingsSwitchRow(
                    label = stringResource(R.string.s_0d0b8357),
                    checked = watchAuto,
                    note = stringResource(R.string.s_b1ef9598),
                    onCheckedChange = { on ->
                        watchAuto = on
                        AppUpdatePrefs.setWatchAuto(ctx, on)
                    },
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.s_4d36128b),
                    // 只放极短的值：当前手表版本（未知显示空）
                    value = if (watchVer > 0L) "v$watchVer" else "",
                    chevron = true,
                    onClick = onCheckWatch,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    @Composable
    private fun UserTab(
        samsungGranted: Boolean?,
        samsungSleepOk: Boolean?,
        samsungSkinTempOk: Boolean?,
        samsungBiaOk: Boolean?,
        samsungProfileOk: Boolean?,
        /**
         * 三星健康**连不上**（`snapshot()` 返回 null，且没有历史记忆）。
         *
         * ⚠ 与「未授权」是两种状态：「未授权」是 SDK 明确答了没有；
         *   这个只是问不出来（三星健康被强停/系统回收）。后者不该催用户去授权，
         *   也不该让他看到「还有 1 项未授权」。
         */
        samsungUnreadable: Boolean,
        syncNote: String,
        diag: String,
        hcAvailable: Boolean,
        hcGranted: Boolean,
        hcMissing: Int?,
        hcAuthErr: String?,
        onAuthHC: () -> Unit,
        onDiagnose: () -> Unit,
        /** 打开法务文档（0 = 隐私声明，1 = 免责声明）。 */
        onOpenLegal: (Int) -> Unit,
        /** 打开主题页（2026-11-06）。 */
        onOpenTheme: () -> Unit,
        /** 「软件更新」分区：自动检查开关（默认开）与其变更回调。 */
        updateAuto: Boolean,
        onUpdateAuto: (Boolean) -> Unit,
        /** 手动「检查更新」。 */
        onCheckUpdate: () -> Unit,
        /** 打开整屏的「更新设置」页。 */
        onOpenUpdate: () -> Unit,
        /**
         * 三星健康授权（**suspend**，调用方能 await）。
         *
         * ⚠ 为什么必须可 await：「授权」页的「一键授权」要**等三星授权页返回之后**
         * 再弹 Health Connect 的授权页 —— 两个系统授权页不能同时弹，只能串行。
         *
         * ⚠ 用户页**所有**授权动作都走它（含下面逐项的「去授权」），所以
         * "单独授权某一项"与"一键授权"不可能出现两套行为。
         */
        onAuthSamsungAwait: suspend () -> Unit,
        /** 打开整屏的授权页（[PersonAuthScreen]）。入口是「个人」段里「授权」那一行。 */
        onOpenAuth: () -> Unit,
        onBackfill: () -> Unit,
        backupBusy: Boolean,
        backupPath: String,
        snapshotCount: Int,
        onExport: () -> Unit,
        onRestore: () -> Unit,
        cycleEnabled: Boolean,
        onToggleCycle: (Boolean) -> Unit,
        annUnread: Int,
        annError: String?,
        onOpenAnnouncements: () -> Unit,
        onOpenLink: (String) -> Unit,
    ) {
        val ctx = this@MainActivity

        // ⚠ 2026-10-27：「数据回填」的界面状态（showBackfill / bfBusy / 报告 / NOT_WRITTEN 展开）
        //   已随面板一起删除 —— 回填现在是自动的，不在这一页留任何入口。

        // ── 推送手表更新（2026-10-20）──
        // -1 空闲 / 0 下载 / 1 推送 / 2 完成 / 3 失败
        var otaStage by remember { mutableIntStateOf(-1) }
        var otaPct by remember { mutableIntStateOf(0) }
        var otaErr by remember { mutableStateOf("") }
        val otaScope = rememberCoroutineScope()

        // 「写入权限检查」的结果**就地显示在这一行**。
        // ⚠ 原来它写进 `SyncRepository.setEvent(...)`，而那个事件只显示在页面**底部**的
        //    「最近一次同步」一行 —— 用户点完看到的是"什么都没发生"（要滚到底才看得到）。
        //    这类"反馈跑到看不见的地方"就是一次假的"功能坏了"。
        var permCheck by remember { mutableStateOf<String?>(null) }
        var permDetail by remember { mutableStateOf<String?>(null) }

        // 用户资料（身高/体重/性别/年龄）来自三星健康：手表端测体成分**必须**用到它。
        // 读不到就如实说，不给默认值 —— 一个假的身高会让手表把体成分算错，而用户不会知道。
        var profile by remember { mutableStateOf<BiaProfileDto?>(null) }
        var profileChecked by remember { mutableStateOf(false) }
        LaunchedEffect(samsungGranted) {
            // 先把上次读到过的资料亮出来 —— 资料几乎不会变，没必要等三星健康连上才显示。
            // ⚠ 这正是用户 2026-11-04 报的：「三星健康一旦不在后台，用户资料就读不到」。
            HealthMemory.loadProfile(ctx)?.let { profile = it }
            if (samsungGranted == true) {
                val fresh = runCatching { SamsungHealthReader.readBiaProfile(ctx) }.getOrNull()
                if (fresh != null) {
                    // 只有真的读到了才覆盖缓存；读不到就继续用旧的
                    HealthMemory.saveProfile(ctx, fresh)
                    profile = fresh
                }
            }
            profileChecked = true
        }

        var showBackup by remember { mutableStateOf(false) }
        var showUsage by remember { mutableStateOf(false) }
        var showAbout by remember { mutableStateOf(false) }
        var showLanguage by remember { mutableStateOf(false) }
        // 匿名使用统计开关（2026-10-28）：首启弹窗只问一次，之后改主意就在这里改。
        // ⚠ 状态存**偏好**、页面只读它的镜像：切页回来必须还是关的（不能回弹成默认）。
        var statsOn by remember { mutableStateOf(StatsPrefs.enabled(ctx)) }

        // 写入权限：它是「授权」入口那一行汇总（[authMissing]）的输入之一，所以**必须留在这里**。
        // ⚠ 逐项明细（三星 8 项 / HC 9 项 / 在途状态 / 缺失集合）都已搬进 [PersonAuthScreen] ——
        //   那些只在授权页用得到，留着就是"两个地方各存一份"，迟早对不上。
        //   这里只查它自己需要的那一项，够算 [authMissing] 就行。
        // ⚠ 写权限**已从授权流程移除**（三星按策略拒绝，见 SamsungHealthReader.allPermissions）——
        //   这里若还统计它，入口行会永远显示「还有 1 项未授权」，而点进去每一项都是绿的（2026-10-17 反馈）。
        var writeOk by remember { mutableStateOf<Boolean?>(null) }
        // 重查时机：进页面、以及 HC 授权页回来（`hcMissing` 变）—— 与授权页保持同一套时机。
        // ⚠ **不做成"随每次同步重查"**，那会白白多连三星 SDK。
        val refreshWriteOk: suspend () -> Unit = {
            writeOk = runCatching { SamsungHealthReader.hasWritePermission(ctx) }.getOrNull()
        }
        LaunchedEffect(hcMissing) { refreshWriteOk() }

        // 「授权」入口那一行的汇总：**只数用户能解决的项目**。
        //
        // ⚠⚠ 能量评分与睡眠呼吸暂停**不计入**：它们分区域、分机型放开，本机不支持时
        //   读到 false 与"用户没授权"长得一模一样，但用户**无法通过授权解决** ——
        //   计进去会让这一行永远显示"还有 2 项未授权"，指向一个他做不到的动作。
        //   它们照旧列在授权页里，只是标成「本机不支持」（沿用既有口径，见 `s_f2592e8d`）。
        // ⚠ Health Connect 不可用时也不计入（同理：那不是"没授权"）。
        val authMissing = listOf(
            samsungGranted, samsungSleepOk, samsungSkinTempOk, samsungBiaOk, samsungProfileOk,
        ).count { it == false } + (if (hcAvailable) (hcMissing ?: 0) else 0)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // ── 个人 ──
            item { SettingsSection(stringResource(R.string.s_00284e38)) }
            item {
                val p = profile
                SettingsGroup {
                    SettingsRow(
                        label = stringResource(R.string.s_ccd47f8c),
                        value = when {
                            !profileChecked -> stringResource(R.string.s_d7e155ac)
                            p != null -> "${p.heightCm.toInt()}cm · " +
                                BodyComposition.formatKg(p.weightKg)
                            else -> stringResource(R.string.s_24cfdfaa)
                        },
                        chevron = false,
                        valueColor = if (profileChecked && p == null) SettingsWarnColor else null,
                        note = if (profileChecked && p == null)
                            stringResource(R.string.s_3e8fd745)
                        else null,
                    )
                    SettingsDivider()
                    // ── 授权（2026-10-07 用户要求：收成单独一个入口）──
                    //
                    // 这里原来是 5 行（三星健康 / 读取权限 / 能量评分读取 / 睡眠呼吸暂停读取 /
                    // Health Connect），每一项都要单独点一次；而且点「三星健康」还会**连弹多次
                    // 授权页**（见 `SamsungHealthReader.request` 的说明）。用户的原话是
                    // 「一个个手动点授权，麻烦」。
                    //
                    // 现在只留一行入口，点进去是**整屏的授权页**（`Route.PersonAuth`）。
                    // ⚠ 2026-10-07 用户第二次调整：那块内容原来是在这一行**下面展开**的
                    //   （`showAuth` 布尔 + 内联 Card），用户原话「我需要的是出现在另一个新的页面里」。
                    //   逐项明细与「一键授权」都搬到 [PersonAuthScreen] 里去了。
                    // ⚠ 值里**只数用户能解决的那些项**，理由见 [authMissing] 的定义。
                    SettingsRow(
                        label = stringResource(R.string.s_003355db),
                        // ⚠ 第三种状态：**从来没读到过、这次也连不上**。
                        //   不能落到「已全部授权」（会让用户以为好了），也不该说「未授权」
                        //   （那不是事实，而且会催他去做一个没用的动作）。
                        value = if (samsungUnreadable) stringResource(R.string.s_861b900f)
                        else if (authMissing == 0) stringResource(R.string.s_21d47d8d)
                        else stringResource(R.string.s_99c47e37, authMissing),
                        valueColor = if (authMissing > 0) SettingsWarnColor else null,
                        chevron = true,
                        onClick = onOpenAuth,
                    )
                    // ⚠ 2026-10-17 用户要求**删掉「抗氧化指数」入口**：App 读不到这个值
                    //   （Data SDK 没有该数据类型、手表也物理测不到），留着只会让人白点一下。
                    //   `openSamsungAntioxidantPhone()` 仍保留（手表端的提示还指向它），
                    //   只是不再有用户页入口。
                }
            }

            // ── 数据 ──
            // ── 关于（2026-10-16）：隐私声明 / 免责声明 ──
            //
            // ⚠ 放在「数据」之前、「个人」之后：这两份文档是**装机即可读**的东西
            //   （正文在 assets 里，离线也能看），不该埋在页面最底下找不着。
            item { SettingsSection(stringResource(R.string.s_0029fc67)) }
            item {
                SettingsGroup {
                    // ⚠ 主题放这一组**最前**：它是"外观类"设置，与下面两份文档不是一类，
                    //   但单独给它一个分区标题（"外观"）会让这一页多出第四条分隔，
                    //   而它只有一行 —— 按"一屏只讲一件事"那条取舍。
                    SettingsRow(
                        label = stringResource(R.string.s_0028a0c9),
                        // 右侧显示**当前主题名**：进页面之前就知道现在用的是哪套
                        value = com.gemini.healthbridge.phone.ui.ThemeManager.current.name,
                        chevron = true,
                        onClick = onOpenTheme,
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.s_4cd42cd7),
                        value = "",
                        chevron = true,
                        onClick = { onOpenLegal(0) },
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.s_0a026ab8),
                        value = "",
                        chevron = true,
                        onClick = { onOpenLegal(1) },
                    )
                    SettingsDivider()
                    // ⚠ 这一行**要联网**（跳浏览器打开线上那份），与 App"数据全在本机"的调性不同，
                    //   所以 note 里要写清"需要联网 + 内容与 App 内一致" —— 不让用户以为
                    //   点进去才能看到完整版。
                    SettingsRow(
                        label = stringResource(R.string.s_cf21e029),
                        value = "",
                        chevron = true,
                        note = stringResource(R.string.s_d7372def),
                        onClick = { openUrl(LEGAL_ONLINE_URL) },
                    )
                }
            }

            // ── 软件更新（2026-10-21 改版）──
            //
            // 用户要求：**这里只留一个入口**，点进去是整屏的「更新设置」，
            // 里面分「手机端 / 手表端」两组（各含自动检查开关 + 手动检查）。
            item { SettingsSection(stringResource(R.string.s_4d1b7867)) }
            item {
                SettingsGroup {
                    SettingsRow(
                        label = stringResource(R.string.s_25e52e0d),
                        value = "",
                        note = stringResource(R.string.s_b1ef9598),
                        chevron = true,
                        onClick = onOpenUpdate,
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.s_24aad6d9),
                        value = "",
                        chevron = true,
                        onClick = onCheckUpdate,
                    )
                }
            }

            item { SettingsSection(stringResource(R.string.s_00344bbe)) }
            item {
                SettingsGroup {
                    SettingsRow(
                        label = stringResource(R.string.s_69702517),
                        value = when {
                            backupBusy -> stringResource(R.string.s_0c0de0cf)
                            backupPath.isNotEmpty() -> stringResource(R.string.s_50ba9e95)
                            else -> stringResource(R.string.s_9ef9f864)
                        },
                        onClick = { showBackup = true },
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.s_71f62324),
                        value = stringResource(R.string.s_5274601d),
                        note = stringResource(R.string.s_0fe571f2),
                        onClick = onBackfill,
                    )
                    // ⚠⚠ 2026-10-27 用户要求删掉这四项（原来在「数据」区里）：
                    //   「连接诊断」「写入权限检查」「回填到健康平台」「最近一次同步」。
                    //   理由：**这些本来就该自动做**，摆成入口等于让用户手动去点一件
                    //   他自己没法判断该不该点的事（回填已改成同步后自动跑，见 `syncTick` 那段）。
                }
            }

            // ⚠ 2026-10-27：「回填到健康平台」这块面板**整块删掉** ——
            //   用户要求回填改成**自动**（每次同步完跑一次，见上面 `syncTick` 那段），
            //   留着入口只会让人以为"还得我手动点一下"。

            // ── 功能开关 ──
            //
            // ⚠ 2026-10-10：「活动与光信号采集」那一行（含它点的说明弹窗）**已删**（用户要求）。
            //   它本来就是"解释手表端的采集开关在哪"的一段纯文字，用户看完觉得没用；
            //   而光信号本身的数据与采集**都没动**（`ppg_waves` 仍在采、仍在用）。
            //   删掉后这里只剩经期追踪一个开关 —— 所以那条分隔线也一并去掉
            //   （单行组里挂一条分隔线是纯噪声）。
            item { SettingsSection(stringResource(R.string.s_3421879d)) }
            item {
                SettingsGroup {
                    SettingsSwitchRow(
                        label = stringResource(R.string.s_193a040d),
                        checked = cycleEnabled,
                        note = stringResource(R.string.s_3a94e4a5),
                        onCheckedChange = onToggleCycle,
                    )
                }
            }

            // ── 其他 ──
            item { SettingsSection(stringResource(R.string.section_other)) }
            item {
                SettingsGroup {
                    SettingsRow(
                        label = stringResource(R.string.language),
                        value = LanguageStore.current(ctx).displayName,
                        onClick = { showLanguage = true },
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.announcements),
                        value = if (annUnread > 0) stringResource(R.string.ann_unread, annUnread)
                        else stringResource(R.string.ann_all_read),
                        valueColor = if (annUnread > 0) Color(0xFFFF6B6B) else null,
                        note = annError,
                        onClick = onOpenAnnouncements,
                    )
                    SettingsDivider()
                    SettingsRow(label = stringResource(R.string.usage), onClick = { showUsage = true })
                    SettingsDivider()
                    // 匿名使用统计（2026-10-28）：与隐私声明**放在同一组**里，
                    // 用户看到开关时"去哪看全文"就在上面两行。
                    SettingsSwitchRow(
                        label = stringResource(R.string.s_f3f01375),
                        checked = statsOn,
                        note = stringResource(R.string.s_04ea0b5a),
                        onCheckedChange = { on ->
                            StatsPrefs.setEnabled(ctx, on)
                            statsOn = on
                            // 打开时就地补一次心跳：不然要等到明天报表才看得到这个人
                            if (on) Stats.appOpen()
                        },
                    )
                    SettingsDivider()
                    SettingsRow(
                        label = stringResource(R.string.about),
                        value = appVersionLabel(),
                        onClick = { showAbout = true },
                    )
                }
            }
        }

        if (showLanguage) {
            LanguagePickerDialog(
                current = LanguageStore.current(ctx),
                onPick = { lang ->
                    LanguageStore.set(ctx, lang)
                    showLanguage = false
                    // 就地重造 Activity 让整棵树用上新语言。
                    // ⚠ 只写偏好、**不再调 updateConfiguration** —— 那在 API 24+ 上
                    // 会被 recreate 重置（见 LanguageStore 文件头）。真正生效的是
                    // `attachBaseContext` → `LanguageStore.wrap`，它在重建时重新读偏好。
                    ctx.recreate()
                },
                onDismiss = { showLanguage = false },
            )
        }

        if (showBackup) {
            AlertDialog(
                onDismissRequest = { showBackup = false },
                title = { Text(stringResource(R.string.s_69702517), fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.s_24556aef) +
                                stringResource(R.string.s_d67124e3),
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.s_f748714c, DataBackup.DIR_NAME) +
                                stringResource(R.string.s_1d4fb1cf),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.s_46f899a1),
                            fontSize = 12.sp, color = AppTextMuted,
                        )
                        if (backupPath.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.s_099d32f7), fontSize = 12.sp, color = AppTextMuted)
                            Text(
                                backupPath, fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (snapshotCount > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.s_5e2af3a1, snapshotCount),
                                fontSize = 12.sp, color = AppTextMuted,
                            )
                        }
                        if (backupBusy) {
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.s_0c0de0cf), fontSize = 12.sp, color = AppTextMuted)
                        }
                    }
                },
                confirmButton = {
                    AppPrimaryButton(onClick = onExport, enabled = !backupBusy) { Text(stringResource(R.string.s_68ffaaf0)) }
                },
                dismissButton = {
                    AppOutlineButton(onClick = onRestore, enabled = !backupBusy) { Text(stringResource(R.string.s_fd294ac4)) }
                },
            )
        }
        if (showUsage) {
            InfoDialog(
                title = stringResource(R.string.s_c5ecef37),
                body = stringResource(R.string.s_bbabe2e7) +
                    stringResource(R.string.s_b9b2a82e) +
                    stringResource(R.string.s_df05a6af) +
                    stringResource(R.string.s_76c27a85) +
                    stringResource(R.string.s_a8213e57) +
                    stringResource(R.string.s_485c8e77),
                onDismiss = { showUsage = false },
            )
        }
        if (showAbout) {
            InfoDialog(
                title = stringResource(R.string.s_fecb6c33),
                body = stringResource(R.string.s_59deeff9) +
                    stringResource(R.string.s_b15711d7) +
                    stringResource(R.string.s_6925d03b) +
                    stringResource(R.string.s_6e139d65) +
                    stringResource(R.string.s_92d7156d) +
                    stringResource(R.string.s_71441f4f, appVersionLabel()),
                onDismiss = { showAbout = false },
            )
        }
        // ⚠ 2026-10-10：「活动与光信号采集」的说明弹窗（`showCollection`）随那一行一起删。
        //   它的 5 段文字里有一段是"光信号的开关在手表血氧页 —— 原始光信号下方"，
        //   那个指引现在也一并消失；若将来又需要，记得改到别处
        //   （⚠ 别直接搬回来：这段文字讲的开关位置在手表上，用户已经明确说不需要）。
    }

    // ═══════════════════ 底部导航 / 功能宫格 / 公告（2026-09-19 重构） ═══════════════════

    /**
     * 宫格里的一个功能。
     *
     * [color] 与 [glyph] 由每张卡自带，和项目里"新增指标要自带配色常量"是同一条纪律：
     * 宫格是**共用**组件，颜色写死在里面，以后加功能就得到处改。
     */
    private data class FeatureCard(
        val feature: Feature,
        val color: Color,
        val glyph: String,
        /**
         * 主数值（大号显示）。`null` = 这类指标没有"最近一条"可给（活动/饮食/血糖/心律不齐
         * 的数据不落库，宫格这一层读不到）⇒ 卡片退化成纯入口，不编数字。
         */
        val value: String? = null,
        /** 数值单位（跟在数值同基线，小字号）。 */
        val unit: String? = null,
        /** 底部小字：时间 / 来源 / 静态说明。 */
        val note: String? = null,
    )

    /** 底部导航的图标种类。图案用几个小方块拼，不引入图标库（见 [NavIcon]）。 */
    private enum class NavIconKind { GRID, DOWNLOAD, PERSON }

    /**
     * 底部导航图标：纯 Compose 基础组件拼出来的。
     *
     * 为什么不引 `material-icons-extended`：那是一个几百 KB 的库，为了两个图标不值得
     * （项目至今零图标依赖）。用几个小方块/圆点拼，尺寸可控、颜色跟随主题，
     * 而且和设计稿里那两个图标是一回事。
     */
    @Composable
    private fun NavIcon(kind: NavIconKind, selected: Boolean) {
        val c = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant
        when (kind) {
            NavIconKind.GRID -> Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(2) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(c, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
            // 「安装」：一条竖线 + 底部横线（下载/落盘的简写），不引图标库
            NavIconKind.DOWNLOAD -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(Modifier.size(width = 2.dp, height = 8.dp).background(c, RoundedCornerShape(1.dp)))
                Box(Modifier.size(width = 13.dp, height = 2.dp).background(c, RoundedCornerShape(1.dp)))
            }
            NavIconKind.PERSON -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(7.dp).background(c, RoundedCornerShape(50)))
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .size(width = 15.dp, height = 6.dp)
                        .background(c, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                )
            }
        }
    }

    /**
     * 标题右侧的「首次使用引导」入口：细描边圆 + 问号。
     *
     * ⚠ 不引图标库（项目至今零图标依赖，理由见 [NavIcon]）：圆圈用 `border`、问号用 `Text`，
     *   两行就够，颜色全跟着主题走。
     * ⚠ 28dp 是量过的：22sp 粗体标题的行高约 30dp，圆圈再大就把整行撑高、标题看着偏上；
     *   再小则点击热区不足 48dp 的推荐值（这里靠外层 `Row` 的高度兜住，仍够点）。
     * ⚠ 描边用 `AppCardBorder` 而不是 `primary`：它是**次要入口**，不该和主按钮抢注意力。
     */
    @Composable
    private fun GuideHelpButton(onClick: () -> Unit) {
        // 无障碍：图形按钮没有文字，读屏靠这一句念出"这里是干什么的"
        val label = stringResource(R.string.s_b4ea3adb)
        Box(
            Modifier
                .size(28.dp)
                // ⚠⚠ 2026-11-07：原来只有 1dp 描边 + 次级灰问号，压在玻璃主题那种明亮背景上
                //   **几乎看不见**（用户：「右上角的按钮都看不清了」）。
                //   ⇒ 实底色 `AppControlFill`（玻璃主题下是暗底）+ 主文字色问号。
                .background(AppControlFill, CircleShape)
                .border(1.dp, AppCardBorder, CircleShape)
                // ⚠ 先 `clip` 再 `clickable`：水波纹按圆裁掉，否则方角波纹会溢出圆外
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Text("?", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = AppTextPrimary)
        }
    }

    /** 底部导航：功能 / 用户。 */
    @Composable
    private fun MainBottomBar(selected: Int, onSelect: (Int) -> Unit) {
        // ⚠ 玻璃主题：底部导航是**半透明**的，内容会从下面透过去 —— 没有分隔线时
        //   "导航栏"和"最后一张卡"连成一片（实测像页面没画完）。一条 1px 发丝线最省事。
        val glass = com.gemini.healthbridge.phone.ui.ThemeManager.current.glass
        Column {
            if (glass) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppTextPrimary.copy(alpha = 0.10f)),
                )
            }
            NavigationBar(
                containerColor = if (glass) AppCardBg else MaterialTheme.colorScheme.surfaceContainer,
            ) {
            NavigationBarItem(
                selected = selected == 0,
                onClick = { onSelect(0) },
                icon = { NavIcon(NavIconKind.GRID, selected == 0) },
                label = { Text(stringResource(R.string.s_002ac85a)) },
            )
            // 「安装」页（2026-10-26）：手表端只能靠它装 —— 蓝牙推包已删（表是空的时根本没人 accept）
            NavigationBarItem(
                selected = selected == 1,
                onClick = { onSelect(1) },
                icon = { NavIcon(NavIconKind.DOWNLOAD, selected == 1) },
                // 用户 2026-10-27：这一页叫「无线ADB」（它管的是"连手表 + 装包"，不只安装）
                label = { Text(stringResource(R.string.s_40c8d4dc)) },
            )
            NavigationBarItem(
                selected = selected == 2,
                onClick = { onSelect(2) },
                icon = { NavIcon(NavIconKind.PERSON, selected == 2) },
                label = { Text(stringResource(R.string.s_003c55af)) },
            )
            }
        }
    }

    /**
     * 功能页顶部的状态卡（**只讲两件事**，2026-10-13 精简）。
     *
     * ## ① 手表连没连上 —— 一条，且必须是**结构化判据**
     *
     * 这张卡原本有 5 行：`手表在线：X` / `待同步 HR0 BP0 ECG0 SPO2…` /
     * `蓝牙：…` / 事件行 / 三星同步状态。用户截图里前三行同时给出**互相打架**的结论
     * （在线 + 请打开手表端连接 + 已连接），他的原话是
     * 「待同步…蓝牙…手表已连接…删掉」。
     *
     * ⇒ 现在只剩一条：**连上了报设备名，没连上报"为什么/该怎么办"**。
     *
     * ⚠⚠ 判据必须用 [PhoneBtServer.connected]（结构化布尔），**不能**看 `device != null`：
     *   `SyncRepository.device` 是**上一次 HELLO 的缓存，断开时不清空**
     *   ⇒ 只看它会长期显示「手表在线」，而这恰恰是用户截图里的矛盾来源。
     *   （同一条纪律见 `PhoneBtServer.connected` 的 KDoc：状态用字段判、文案只用于显示。）
     *
     * ⚠ 没连上时直接显示蓝牙状态文案（`蓝牙：未开启` / `请打开手表端进行连接` / `重试中…`）
     *   而不是自己拼一句「未连接手表」：**不同的失败原因要用户做的事不一样**
     *   （开蓝牙 ≠ 去开手表端 App），合并成一句会把人指向错误的动作。
     *
     * ## ② 三星健康今天同步过没有 —— **只在没同步时出现**
     *
     * 「同步」按钮撤于 2026-10-10（那件事本来就是自动的，见 `MainScreen` 的三处 `syncTick++`），
     * 换成了状态句。⚠ 2026-10-13 用户进一步要求：**同步过就整行隐藏**——
     * 「今日已同步」是一句"什么都没发生"的陈述，天天占着一行没有任何信息量；
     * 只有没同步时才需要催他去做一件事（那句用橙色）。
     *
     * ⚠ 这两件事**不能合并成一句**：手表连不连得上由蓝牙决定，三星有没有数据由三星健康
     *   有没有在后台采到决定 —— 两件独立的事，只是用户最容易混为一对。
     */
    @Composable
    private fun SyncStatusStrip(
        device: com.gemini.healthbridge.core.HelloMessage?,
        btConnected: Boolean,
        btStatus: ResText,
        samsungSyncedToday: Boolean,
    ) {
        AppSurface(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val name = device?.deviceName
                Text(
                    if (btConnected && name != null) stringResource(R.string.s_d322507d, name)
                    else resText(btStatus),
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    color = if (btConnected && name != null) MaterialTheme.colorScheme.primary
                    else AppTextMuted,
                )
                // ⚠⚠ 2026-10-22 用户要求：**这一行整行删掉**。
                //   原来它显示"同步 1 条，三星健康只读（无写入权限），HC失败：未知"——
                //   全是写给开发者看的诊断话术：用户既不知道"只读"是什么意思（写权限本来就拿不到），
                //   也不知道"HC 失败"要他去做什么。⇒ 卡片只留「手表在线：X」这一条结论；
                //   同步结果仍写进 `SyncRepository.event`（记录页/诊断里能看到），只是首页不再展示。
                // 三星健康那条：**没同步才说**，且用橙色（要用户去做一件事）。
                if (!samsungSyncedToday) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_e0b08614),
                        fontSize = 11.sp, color = Color(0xFFFFA94D),
                    )
                }
            }
        }
    }

    /**
     * 宫格中的一张卡（2026-11-06 改版）：**图标 + 名称 / 大号数值 / 时间** 三段式。
     *
     * ## 为什么改成这样
     *
     * 用户拿跑步 App 的截图要求"功能卡片做成这种大一些的格子"。原来这一格是
     * 「彩色字块 + 名称 + 一行灰字」，**数值被埋在灰字里**（`72 次/分 · 11-06 08:56` 一行），
     * 一屏八张卡扫下去根本看不出"现在多少"。
     * ⇒ 数值单独成一行、放大到 30sp，时间和来源下沉成 11sp 的灰字。
     *
     * ⚠ 圆角传 18dp（`AppCardRadius` 是 10dp）：深色底上 10dp 显得方，
     *   而"格子感"很大程度来自圆角。**只给宫格卡**放大 —— 设置组、表格行仍是 10dp，
     *   那些地方一行套一行，圆太大反而像一堆胶囊。
     *
     * ⚠ `fillMaxHeight()`：与 `FeatureGrid` 里 Row 的 `height(IntrinsicSize.Min)` 配套，
     *   让同一行的两张卡**等高**（有数值的卡比没数值的高一截，不等高就参差不齐）。
     */
    @Composable
    private fun FeatureCardView(card: FeatureCard, modifier: Modifier, onClick: () -> Unit) {
        // ⚠ 卡片底统一交给 `AppSurface`（`category` 参数）：它按**卡片自身位置**取背景那团光，
        //   所以换主题、换光位都自动跟着走；玻璃主题则自动切成毛玻璃（见 `cardSurfaceBg` 的 KDoc）。
        AppSurface(
            modifier = modifier.fillMaxHeight(),
            radius = 18.dp,
            category = card.color,
            onClick = onClick,
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                // ⚠ 没有数值的卡（活动/饮食/血糖/心律不齐）会被同行那张有数值的卡**撑高**，
                //   内容全贴在顶部、下半张卡空着。居中排布让空白分摊到上下，看着才不"缺一块"。
                verticalArrangement = if (card.value == null) Arrangement.Center
                else Arrangement.Top,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(card.color.copy(alpha = 0.26f), RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            card.glyph, fontSize = 14.sp,
                            fontWeight = FontWeight.Bold, color = card.color,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        card.feature.title(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AppTextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (card.value != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            card.value,
                            // ⚠ 字号按长度自适应：`窦性心律`（4 字）能撑 30sp，
                            //   `119/77`（6 字符）就得收一档，否则会把单位挤出去或折行
                            fontSize = when {
                                card.value.length <= 4 -> 30.sp
                                card.value.length <= 6 -> 24.sp
                                else -> 19.sp
                            },
                            fontWeight = FontWeight.Bold,
                            color = AppTextPrimary,
                            maxLines = 1,
                        )
                        card.unit?.let {
                            Spacer(Modifier.width(3.dp))
                            Text(
                                it,
                                fontSize = 10.sp,
                                color = AppTextMuted,
                                modifier = Modifier.padding(bottom = 5.dp),
                                maxLines = 1,
                            )
                        }
                    }
                }
                card.note?.let {
                    Spacer(Modifier.height(if (card.value != null) 4.dp else 10.dp))
                    Text(
                        it,
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = AppTextMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    /** 功能宫格页：顶部同步状态条 + 2 列功能卡。 */
    @Composable
    private fun FeatureGrid(
        cards: List<FeatureCard>,
        /**
         * 首页「今日概览」三格：心率 / 血氧 / 睡眠（2026-10-15）。
         *
         * ⚠ 传**已格式化好的字符串**而不是原始列表：宫格这一层不该会算均值
         *   （口径要在数据侧定，UI 只负责显示）。没有数据时传 `--`。
         */
        todayStats: Triple<String, String, String>,
        /** 点「今日概览」这张卡：进整屏概览页（2026-11-06）。 */
        onOpenOverview: () -> Unit,
        device: com.gemini.healthbridge.core.HelloMessage?,
        btConnected: Boolean,
        btStatus: ResText,
        samsungSyncedToday: Boolean,
        onOpen: (Int) -> Unit,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { SyncStatusStrip(device, btConnected, btStatus, samsungSyncedToday) }
            // ── 今日概览（2026-10-15 改版新增）──
            //
            // 原来首页只有宫格，用户要看「今天怎么样」必须逐个点进去。
            // 这一张卡把三个最常看的量并排放在最上面，一屏能扫完。
            // ⚠ 三格都只是**今天的值**，不做任何分级（不写「正常/偏高」）——
            //   本 App 一律不给判定（按同一条纪律）。
            item {
                // ⚠ 2026-11-06：这张卡改成**可点**（进整屏「今日概览」）。
                //   它回答"现在多少"，点进去回答"今天怎么样、离目标还差多少"。
                AppCard(
                    title = stringResource(R.string.s_a99b81a9),
                    onClick = onOpenOverview,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppStat(
                            stringResource(R.string.s_00317450),
                            todayStats.first,
                            Modifier.weight(1f),
                            HrAwakeColor,
                        )
                        AppStat(
                            stringResource(R.string.s_004624e7),
                            todayStats.second,
                            Modifier.weight(1f),
                            Spo2SleepColor,
                        )
                        AppStat(
                            stringResource(R.string.s_0034acd2),
                            todayStats.third,
                            Modifier.weight(1f),
                            SleepActualColor,
                        )
                    }
                }
            }
            // 用 chunked 手工排两列，而不是 LazyVerticalGrid：
            // 卡片只有七八张，不值得为此再引一套 grid API（还和 lazy 的 items 撞名）
            //
            // ⚠ `height(IntrinsicSize.Min)` 不能省：`Row` 默认让每个子项**各自按内容**
            //   决定高度，于是副标题两行的卡会比一行的卡高一截 —— 「体成分 / 经期」那一排
            //   看着就是两张不等高的卡（用户 2026-09-29 截图指出）。加上它以后一行里的
            //   所有卡取行内最大高度，卡面才是齐的。配套：卡自己要用 `fillMaxHeight()`。
            items(cards.withIndex().chunked(2)) { row ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.height(IntrinsicSize.Min),
                ) {
                    row.forEach { iv ->
                        FeatureCardView(iv.value, Modifier.weight(1f)) { onOpen(iv.index) }
                    }
                    // 最后一行只有一张时补个空位，否则那一张会被拉伸成整行宽
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    /**
     * 公告列表里的一张卡（2026-10-04 改：**点击弹窗**，不再原地展开）。
     *
     * ## 为什么改
     *
     * 原来点一下就把正文摊开在这张卡里 ⇒ 同一个列表里既有一行摘要的、又有整篇摊开的，
     * 长短参差；而且正文里的链接**只在展开后才出现按钮**，收起时完全看不到。
     * 改成弹窗后：列表始终是整齐的「一条一行」时间线，详情统一在弹窗里看 ——
     * 与开屏那条公告的呈现方式也统一了（**复用同一个 [AnnouncementDialog]**，
     * 不另写一套，否则两处的正文渲染规则会漂移）。
     *
     * @param timeText 已算好的时间文案（`date` 优先、缺失则用**本地首次见到**时间）。
     *   ⚠ 由调用方算而不是在这里算：时间兜底要用到本地存储里的 firstSeen 表，
     *   在这里读会让这个纯展示组件依赖存储层。
     *   空串 = 两个时间都没有，那一行整体不显示（不留空行）。
     * @param retired 服务端已下架。⚠ **必须标出来** ——
     *   否则用户会以为它还在生效，而它可能已被作者撤回。
     */
    @Composable
    private fun AnnouncementCard(
        item: com.gemini.healthbridge.core.AnnouncementItem,
        read: Boolean,
        timeText: String,
        retired: Boolean,
        onOpen: () -> Unit,
    ) {
        val flat = item.body.replace("\n", " ")
        AppSurface(Modifier.fillMaxWidth(), onClick = onOpen) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!read) {
                        Box(Modifier.size(8.dp).background(Color(0xFFFF6B6B), RoundedCornerShape(50)))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        item.title, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (Announcement.isImportant(item)) {
                        Box(
                            Modifier
                                .background(Color(0x1AD32F2F), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                Announcement.levelLabel(item.level),
                                fontSize = 10.sp, color = Color(0xFFFF6B6B),
                            )
                        }
                    } else if (read) {
                        Text(this@MainActivity.getString(R.string.s_00309ed1), fontSize = 10.sp, color = AppTextMuted)
                    }
                }
                if (timeText.isNotEmpty()) {
                    Text(
                        timeText, fontSize = 11.sp, color = AppTextMuted,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    if (flat.length > 60) flat.take(60) + "\u2026" else flat,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ⚠ 原来这句只在**收起时**显示（展开后换成链接按钮）。
                    //   现在永远显示 —— 它是"点进去能看全文"的唯一提示，
                    //   而卡片整体可点这件事在视觉上没有别的线索。
                    Text(
                        this@MainActivity.getString(R.string.s_659e31e6),
                        fontSize = 10.sp, color = AppTextMuted,
                    )
                    if (retired) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            this@MainActivity.getString(R.string.s_18c211e2),
                            fontSize = 10.sp, color = AppTextMuted,
                        )
                    }
                }
            }
        }
    }

    /**
     * 公告列表页。空态分三种：**拉取失败** / **内容格式不对** / **确实没有公告**。
     *
     * ⚗⚗ **列表数据用 `state.history` 而不是 `state.items`**（2026-10-04）：
     *   `items` 是"服务端**现在**公布着什么"，`history` 是"**本地见过什么**"。
     *   用户要能回看以前发过的公告 ⇒ 列表必须用 history。
     *   ⚠ 而别处（未读红点、开屏弹窗）仍用 `items` —— 那是"当前有效"的语义，
     *   把已下线的公告算进未读数会让红点永远消不掉。
     */
    @Composable
    private fun AnnouncementsScreen(
        state: AnnouncementRepository.State,
        readIds: Set<String>,
        onBack: () -> Unit,
        onOpenLink: (String) -> Unit,
        onRetry: () -> Unit,
        onMarkRead: (String) -> Unit,
    ) {
        // ⚗ 详情弹窗的状态就地放在这一页：只有这里会点开某一条，
        //   没必要把它穿到 MainScreen 去（与「回填面板」同一处理）。
        var detail by remember { mutableStateOf<AnnouncementRepository.HistoryEntry?>(null) }
        Column(Modifier.fillMaxSize()) {
            DetailHeader(stringResource(R.string.s_0029fe8e), onBack)
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.history.isEmpty()) {
                    item {
                        AppSurface(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    state.parseError
                                        ?: if (state.fetchFailed) Announcement.EMPTY_FETCH_FAILED
                                        else Announcement.EMPTY_NO_DATA,
                                    fontSize = 13.sp,
                                )
                                if (state.parseError != null || state.fetchFailed) {
                                    AppOutlineButton(
                                        onClick = onRetry,
                                        modifier = Modifier.padding(top = 10.dp),
                                    ) { Text(stringResource(R.string.s_a4407400)) }
                                }
                            }
                        }
                    }
                }
                items(state.history, key = { it.item.id }) { entry ->
                    AnnouncementCard(
                        item = entry.item,
                        read = entry.item.id in readIds,
                        timeText = announcementTimeText(entry),
                        retired = entry.retired,
                        onOpen = {
                            // ⚠ 点开就算看过（原来是"展开才算"）。改弹窗后
                            //   "展开"这个动作不存在了，仍以"是否点开"为准最直观。
                            onMarkRead(entry.item.id)
                            detail = entry
                        },
                    )
                }
                item {
                    Spacer(Modifier.height(4.dp))
                    Text(Announcement.CADENCE_NOTE, fontSize = 11.sp, color = AppTextMuted)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.s_c784818b),
                        fontSize = 11.sp, color = AppTextMuted,
                    )
                }
            }
        }
        // ⚗ 详情弹窗**复用开屏那个组件**：正文渲染（链接可点、可长按复制、
        //   不做截断）两处必须一致，各写一套必然漂移。
        detail?.let { e ->
            AnnouncementDialog(
                item = e.item,
                firstSeenAt = e.firstSeenAt,
                retired = e.retired,
                onDismiss = { detail = null },
                onOpenLink = onOpenLink,
                // ⚠ null = 不显示"查看全部公告"：用户**已经在**列表页了，
                //   再给一个跳到本页的按钮是纯噪音。
                onOpenList = null,
            )
        }
    }

    /**
     * 公告的时间文案：`date`（发布方填的「发出时间」）优先；
     * 没有则退回**本地首次见到**的日期。
     *
     * ⚠⚠ 兜底是必要的：`date` 在格式里是**可空**的（见 announcement.json 的 `_fields`），
     *   而列表里出现一条"完全没有时间"的公告，用户就无法判断新旧 ——
     *   历史列表里那个缺失尤其显眼（它就是用来排时间线的）。
     * ⚠ 两者都没有时返回空串，调用方据此**整行不显示**（不留空行）。
     */
    @Composable
    private fun announcementTimeText(e: AnnouncementRepository.HistoryEntry): String {
        if (e.item.date.isNotEmpty()) return e.item.date
        if (e.firstSeenAt <= 0L) return ""
        val d = remember(e.firstSeenAt) {
            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                .format(java.util.Date(e.firstSeenAt))
        }
        return stringResource(R.string.s_6d1d400a, d)
    }

    /**
     * 公告配图（2026-10-05 用户要求「公告中加入图片」）。
     *
     * ## ⚠⚠ 使用时必须放在 `Column` 里，且不能与别的 Composable 平级
     *
     * 症状（2026-10-08 用户报「公告跟文字重叠了」）：图与正文**叠在一起**，文字压在图片上。
     *
     * 根因不在本组件，而在**容器的布局语义**：`AlertDialog` 的 `text` 槽内部是**一个 `Box`**
     * （堆叠布局）⇒ 平级传两个 Composable 进去，它们会画在同一位置。
     *
     * ⚠ 因此本组件对外**返回 Unit 而不返回任何可见尺寸**这件事本身是危险的：
     *   没图时它**一个节点都不画** ⇒ 上面那个错法在"没有配图的公告"上看起来完全正常，
     *   于是能一路通过目测与真机试用，只在**带图的公告**上现形，且不报任何错。
     *   ⇒ 正确写法永远是：
     *   ```
     *   Column {
     *       AnnouncementImageBlock(item.image)
     *       AnnouncementBody(item.body, 13.sp)
     *   }
     *   ```
     *   ⚠ 顺手用静态校验脚本钉住这条（见该脚本注释）。
     *
     * ## 三条行为约定
     *
     * 1. **没图 / 还没加载好 / 加载失败 ⇒ 整块不画**（`bmp == null` 直接 return）：
     *    不留破图、不显示"加载失败"、也没有 loading 占位。
     *    ⚗ 不做占位是有意的 —— 公告弹窗是**瞬时**出现的，闪一下灰块比"晚半秒出现图"更难看。
     *    ⚠ 它贡献高度为 0，所以**有图 / 没图时正文的起点是一样的**（差别只是下面多了张图）。
     *    ⚠⚠ 但**不要**因此以为"有图没图都不影响版式"—— 在 Box 那类堆叠容器里，
     *      多一个孩子就是从"并排/堆叠"变成"重叠"，与高度无关。
     * 2. 宽度铺满、按比例缩放，**高度上限 220dp**：图再大也不会把弹窗顶出屏幕。
     * 3. 加载走 [AnnouncementImages]（内存 + 磁盘缓存），**图只下载一次**，之后离线也能看。
     *
     * ⚠ 地址表由这里传给 [AnnouncementImages]（而不是在那边读 `AnnouncementRepository.URLS`）：
     *   图片基址必须与公告 JSON **同一套**，而"它们是一套"这件事只有调用点看得见。
     *
     * @param image 相对路径；`null`/空 ⇒ 直接不画（**绝大多数公告就是这种情况**）。
     */
    @Composable
    private fun AnnouncementImageBlock(image: String?) {
        if (image.isNullOrEmpty()) return
        val ctx = LocalContext.current
        val bmp by produceState<Bitmap?>(initialValue = null, image) {
            value = withContext(Dispatchers.IO) {
                AnnouncementImages.load(ctx, image, AnnouncementRepository.URLS)
            }
        }
        val b = bmp ?: return
        Image(
            bitmap = b.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                // ⚠ 顺序有讲究：`padding` 在 `clip` **之前** ⇒ 留白在图之外，
                //   圆角只裁图片本身（反过来会把留白也一起裁，图上会出现缺角）。
                .padding(bottom = 10.dp)
                .heightIn(max = 220.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
    }

    /**
     * 公告弹窗（**开屏与列表详情共用同一个**，2026-10-04 扩展）。
     *
     * ⚗ 共用的理由：正文渲染规则（链接可点、可长按复制、不做 400 字截断）两处必须一致，
     *   各写一套必然漂移 —— 而"截断把链接切断"这个 bug 正是这么来的（2026-09-29）。
     *
     * @param onOpenList `null` = 不显示「查看全部公告」。从**列表页**点开时要传 null：
     *   用户已经在那一页了，再给个跳回本页的按钮是纯噪音。
     * @param firstSeenAt 本地首次见到这条的时间（`date` 缺失时**兜底显示**）。
     *   0 = 没有记录。⚠ 没有它会显示出一条"没有时间"的公告，用户无法判断新旧。
     * @param retired 已从服务端下架 ⇒ meta 里标出来，避免用户以为它还在生效。
     */
    @Composable
    private fun AnnouncementDialog(
        item: com.gemini.healthbridge.core.AnnouncementItem,
        onDismiss: () -> Unit,
        onOpenLink: (String) -> Unit,
        // ⚠ 改成可空；新参数一律**带默认值**放最后 ⇒ 原有调用点不必改。
        onOpenList: (() -> Unit)?,
        firstSeenAt: Long = 0L,
        retired: Boolean = false,
    ) {
        // 弹窗里的正文**不做 400 字截断**（2026-09-29 改）：截断会把一条链接从中间切断，
        // 变成一段看着像链接、点下去却打不开的文字 —— 正是用户报的那个问题。
        // 长文靠 Dialog 自己滚动（AlertDialog 的 text 槽本来就是可滚动的）。
        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    // ⚗ 时间：发布方的 `date` 优先；缺失则退回**本地首次见到**的日期。
                    //   ⚠ `date` 在格式里可空，两个都可能没有 ⇒ 那时整段不显示（不留空行）。
                    val timeText = item.date.ifEmpty {
                        if (firstSeenAt > 0L) remember(firstSeenAt) {
                            java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT)
                                .format(java.util.Date(firstSeenAt))
                        } else ""
                    }
                    val meta = listOf(
                        timeText,
                        Announcement.levelLabel(item.level),
                        if (retired) stringResource(R.string.s_18c211e2) else "",
                    ).filter { it.isNotEmpty() }.joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(meta, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }
            },
            // 正文可长按复制 + 链接可点（2026-09-29 加）
            text = {
                // ⚠⚠ 2026-10-16：**长公告要能滚**。原来这里信了"AlertDialog 的 text 槽
                //   本来就是可滚动的"（上面那行注释），实际上 Material3 **不会**给它加滚动 ——
                //   正文一长就被直接裁掉，用户看不完后半段（配图 + 三段正文的公告必现）。
                //   ⇒ 给一个高度上限 + 纵向滚动：内容再多也能读完，且弹窗不会顶满整屏。
                //
                // ⚠⚠ 这一层 `Column` **不能省**（2026-10-08 修）：
                //   `AlertDialog` 的 `text` 槽内部是**一个 `Box`**（Material3 的实现），
                //   而 Box 是**堆叠**布局 —— 平级传两个 Composable 进去，它们会**重叠**在同一位置。
                //
                //   原先这里直接写了两行（`AnnouncementImageBlock(...)` + `AnnouncementBody(...)`），
                //   于是配图恰好盖在正文上：图片先画在下、正文后画在上，看起来就是
                //   「公告的图跟文字叠在一起」（用户 2026-10-08 报的正是这个）。
                //
                //   ⚗ 为什么一直没被发现：**绝大多数公告没有配图**，而
                //   `AnnouncementImageBlock` 在没有图时**一个节点都不画**（直接 return）——
                //   于是 Box 里只有正文一个孩子，版式完全正常。
                //   ⇒ 这个 bug 只在"公告带图"时现形，且**不报任何错**。
                Column(
                    Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    AnnouncementImageBlock(item.image)
                    AnnouncementBody(item.body, 13.sp)
                }
            },
            confirmButton = {
                val link = item.url
                if (link != null) {
                    AppPrimaryButton(onClick = { onOpenLink(link) }) {
                        Text(Announcement.urlLabelOf(item))
                    }
                } else {
                    AppOutlineButton(onClick = onDismiss) { Text(this@MainActivity.getString(R.string.s_1faf4f0c)) }
                }
            },
            // ⚠ 可空 ⇒ 必须判空：`onOpenList` 为 null 时整个 dismissButton 也要省掉
            //   （传一个点击无效果的按钮比不显示更糟）。
            dismissButton = if (onOpenList != null) {
                { AppOutlineButton(onClick = onOpenList) { Text(stringResource(R.string.s_e722a008)) } }
            } else null,
        )
    }
}
