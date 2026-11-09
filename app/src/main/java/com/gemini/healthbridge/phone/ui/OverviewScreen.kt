package com.gemini.healthbridge.phone.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.healthbridge.phone.R

/**
 * 「今日概览」整屏页（2026-11-06）。
 *
 * ## 长什么样
 *
 * 用户拿了一张渐变卡截图（橙红底、左上「• 心率」、下面 `72 次/分`）要求：
 * **今日所有数据都用这种渐变卡展示，没数据的整张不出现**。
 * ⇒ 这一屏没有"深色卡片 + 描边"那套 —— 全是**无边框的纯渐变块**，
 *   每块的渐变取该指标的类别色（与首页宫格同一色系）。
 *
 * ## 口径（全部由调用方算好传进来）
 *
 * - 步数取 `activity_days` 今天那一行；
 * - 心率 / 血氧 / 血压取今天**最新一条**；
 * - 睡眠取**昨晚实际睡眠**（不是卧床）；
 * - 体温取**最近一次腕温**（⚠ 不是三星夜间值、也不是核心估算 —— 三样不同的量）。
 *
 * ⚠ `null` 一律表示"没数据"，页面**整张卡不画**（用户明确要求）。
 *   不写 `0`、不写 `--` 占位 —— 一张空卡比没有卡更让人困惑。
 */
data class OverviewData(
    /** 今日步数；`null` = 今天还没有任何活动数据。 */
    val steps: Long? = null,
    /** 6 段（每 4 小时）步数，用来画主卡底部那条曲线。空 = 不画。 */
    val slotSteps: List<Long> = emptyList(),
    /** 今天最新一条心率（bpm）。 */
    val hr: Int? = null,
    /** 今天最新一条血氧（%）。 */
    val spo2: Int? = null,
    /** 昨晚实际睡眠时长（已格式化，如 `7:12`）。 */
    val sleepText: String? = null,
    /** 最近一次腕温（已格式化，如 `36.4`）。 */
    val tempText: String? = null,
    /** 今天最新一条血压（`119/77`）。 */
    val bpText: String? = null,
) {
    /** 一条数据都没有 —— 用来说明"为什么空"。 */
    val isEmpty: Boolean
        get() = steps == null && hr == null && spo2 == null && sleepText == null &&
            tempText == null && bpText == null

    /** 除步数以外还有没有别的 —— 决定主卡下面要不要留间距。 */
    val hasSecondary: Boolean
        get() = hr != null || spo2 != null || sleepText != null ||
            tempText != null || bpText != null
}

// ── 各指标的渐变色（与首页宫格的类别色同一套色系）──
private val GradSteps = listOf(Color(0xFF8B5CF6), Color(0xFFE45CFF))
private val GradHr = listOf(Color(0xFFFF7A45), Color(0xFFFF4D6A))
private val GradSpo2 = listOf(Color(0xFF3D6BFF), Color(0xFF5AA9FF))
private val GradSleep = listOf(Color(0xFF6E57FF), Color(0xFFB18CFF))
private val GradTemp = listOf(Color(0xFFFFA94D), Color(0xFFFF7442))
private val GradBp = listOf(Color(0xFFFF5C8A), Color(0xFFD9439B))

@Composable
fun OverviewScreen(data: OverviewData, onBack: () -> Unit) {
    // ⚠ 用 `appPageBg()` 而不是 `background(AppBg)`：动态主题下这一层必须留空，
    //   否则会把根层那片会动的光整块盖掉（页面看着是静态的，还不报错）。
    Box(Modifier.fillMaxSize().appPageBg()) {
        OverviewGlow()
        Column(
            Modifier
                .fillMaxSize()
                // ⚠ 状态栏/导航栏的避让由 `Scaffold` 的 topBar / bottomBar 负责，
                //   这里再各加一次就是**双份内边距**（实测标题被推下去 24dp）。
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            OverviewHeader(onBack)
            Spacer(Modifier.height(14.dp))

            if (data.steps != null) {
                StepsHeroCard(data)
                if (data.hasSecondary) Spacer(Modifier.height(12.dp))
            } else if (data.isEmpty) {
                // 一条都没有：说清"为什么空"和"怎么才有"，但不画假卡
                EmptyCard()
                Spacer(Modifier.height(12.dp))
            }

            // ── 其余指标：两列渐变网格，**没数据的整张不画** ──
            val cards = listOfNotNull(
                data.hr?.let {
                    GradCardSpec(
                        stringResource(R.string.s_00317450), it.toString(),
                        stringResource(R.string.s_1c0dd13c), GradHr,
                    )
                },
                data.spo2?.let {
                    GradCardSpec(stringResource(R.string.s_004624e7), it.toString(), "%", GradSpo2)
                },
                data.sleepText?.let {
                    GradCardSpec(stringResource(R.string.s_0034acd2), it, null, GradSleep)
                },
                data.tempText?.let {
                    GradCardSpec(stringResource(R.string.s_786fa0c0), it, "℃", GradTemp)
                },
                data.bpText?.let {
                    GradCardSpec(stringResource(R.string.s_00460c4b), it, null, GradBp)
                },
            )
            cards.chunked(2).forEach { row ->
                Row(
                    Modifier.height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    row.forEach { spec ->
                        GradientStatCard(spec, Modifier.weight(1f).fillMaxHeight())
                    }
                    // 最后一行只有一张时补空位，否则那一张会被拉成整行宽
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 一张渐变卡的呈现数据（组件只负责画，不算口径）。 */
private data class GradCardSpec(
    val label: String,
    val value: String,
    val unit: String?,
    val colors: List<Color>,
)

/** 顶部两团光晕：整屏的"氛围"几乎全来自这里。 */
@Composable
private fun OverviewGlow() {
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            brush = Brush.radialGradient(
                listOf(AppAccent.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(size.width * 0.85f, size.height * 0.02f),
                radius = size.width * 0.9f,
            ),
            radius = size.width * 0.9f,
            center = Offset(size.width * 0.85f, size.height * 0.02f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFFFF4D9D).copy(alpha = 0.12f), Color.Transparent),
                center = Offset(size.width * 0.05f, size.height * 0.33f),
                radius = size.width * 0.75f,
            ),
            radius = size.width * 0.75f,
            center = Offset(size.width * 0.05f, size.height * 0.33f),
        )
    }
}

/** 返回按钮 + 标题。系统返回键由 `MainScreen` 的 `BackHandler` 兜住。 */
@Composable
private fun OverviewHeader(onBack: () -> Unit) {
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
            stringResource(R.string.s_a99b81a9),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = AppTextPrimary,
        )
    }
}

/**
 * 主卡：今日步数 —— **整块渐变**。
 *
 * ⚠ 用户 2026-11-06 要求"去掉外面的小格子"：原来是深底 + 1px 描边，
 *   套在深色页里像一块「文本框」。渐变块本身就有边界感，**不需要描边**。
 */
@Composable
private fun StepsHeroCard(data: OverviewData) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(GradSteps))
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
            Spacer(Modifier.width(7.dp))
            Text(
                stringResource(R.string.s_00375a1f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                data.steps.toString(),
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                stringResource(R.string.s_00006b65),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 9.dp),
            )
        }
        if (data.slotSteps.size >= 2) {
            Spacer(Modifier.height(6.dp))
            SlotCurve(
                values = data.slotSteps,
                modifier = Modifier.fillMaxWidth().height(78.dp),
            )
        }
    }
}

/**
 * 一张指标渐变卡：左上「• 标签」，下面 `数值 + 单位`。
 *
 * ⚠ 数值字号按长度自适应（`119/77` 比 `97` 长一倍），否则血压那格会把单位挤出去。
 * ⚠ 渐变卡上的文字**只能纯白**：`AppTextPrimary` 是近白紫，压在橙底上会发灰。
 */
@Composable
private fun GradientStatCard(spec: GradCardSpec, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(spec.colors))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.9f)))
            Spacer(Modifier.width(7.dp))
            Text(
                spec.label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                spec.value,
                fontSize = when {
                    spec.value.length <= 4 -> 30.sp
                    spec.value.length <= 6 -> 25.sp
                    else -> 20.sp
                },
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
            )
            spec.unit?.let {
                Spacer(Modifier.width(4.dp))
                Text(
                    it,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 5.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** 一条数据都没有时的说明卡：说清"为什么空"，别只留一片黑。 */
@Composable
private fun EmptyCard() {
    AppSurface(Modifier.fillMaxWidth(), radius = 18.dp) {
        Column(Modifier.padding(AppCardPadding)) {
            Text(
                stringResource(R.string.s_1ed34e70),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppTextPrimary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.s_41bd9c79),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = AppTextMuted,
            )
        }
    }
}

/**
 * 6 段步数曲线：**虚线** + 起点大圆 + 末端一截亮线。
 *
 * ⚠ 虚线不用实线：虚线天然表达"这是采样点之间的插值"，
 *   不会让人以为中间每一刻都测过。⚠ 不做平滑插值，只连直线。
 * ⚠ 卡片底是紫粉渐变，线必须用**白色**（`AppAccent` 在紫底上等于隐形）。
 */
@Composable
private fun SlotCurve(values: List<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = values.max().coerceAtLeast(1L).toFloat()
        val stepX = size.width / (values.size - 1)
        val padY = 8.dp.toPx()
        val h = size.height - padY * 2
        val line = Path()
        val fill = Path()
        values.forEachIndexed { i, v ->
            val x = i * stepX
            val y = padY + h * (0.95f - (v / max) * 0.85f)
            if (i == 0) {
                line.moveTo(x, y); fill.moveTo(x, size.height); fill.lineTo(x, y)
            } else {
                line.lineTo(x, y); fill.lineTo(x, y)
            }
        }
        fill.lineTo(size.width, size.height)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.20f), Color.Transparent)))
        drawPath(
            path = line,
            color = Color.White.copy(alpha = 0.85f),
            style = Stroke(
                width = 1.8.dp.toPx(),
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)),
            ),
        )
        val y0 = padY + h * (0.95f - (values.first() / max) * 0.85f)
        drawCircle(Color.White, radius = 5.5.dp.toPx(), center = Offset(6.dp.toPx(), y0))
        val yN = padY + h * (0.95f - (values.last() / max) * 0.85f)
        drawLine(
            color = Color.White,
            start = Offset(size.width - 20.dp.toPx(), yN),
            end = Offset(size.width, yN),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
