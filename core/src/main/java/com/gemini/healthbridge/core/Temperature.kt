package com.gemini.healthbridge.core

import kotlin.math.ceil
import kotlin.math.floor
import java.util.Locale

/**
 * 体温（腕部皮肤温度）的量纲、取值域与纵轴口径。
 *
 * ## 为什么单独抽成一个纯函数对象
 * 这个项目已经被"量纲/口径搞错"伤过三次（血氧 `*100`、心电幅值判据、频带能量比），
 * 共同特点是**编译期完全看不出来，只在真机上表现为"数据看起来不对"**。
 * 体温这一项的口径比血氧更容易踩：同一个"温度"在这个 App 里有**两种完全不同的量**：
 *
 * | 来源 | 含义 | 量级 |
 * |---|---|---|
 * | 手表的 `OBJECT_TEMPERATURE`（Sensor SDK） | 腕部**皮肤温度的绝对值** | 约 30~36 ℃ |
 * | 三星健康的 `SKIN_TEMPERATURE`（Data SDK） | 三星健康那套**睡眠期口径**（很可能是相对个人基线的偏差） | 约 ±0.1~±1.5 |
 *
 * 两者**绝不能画进同一条曲线** —— 混在一起就会画出"体温 0.3℃"这种图，
 * 而它看起来又很像个正常的低值，极难在界面上一眼识破。所以这里用 [scaleOf] 显式判断口径，
 * 由界面对两种口径**分开标注、分开画**。
 *
 * 手腕皮肤温度本身**不等于体温**：通常比腋下体温低 1~3 ℃，并且明显受环境温度、
 * 佩戴松紧、手臂是否压在身下影响。所以 [level] 只给"偏低 / 常见 / 偏高"的位置感，
 * **不做任何发热判断**，界面文案也必须写明这一点（见 [DISCLAIMER]）。
 */
object Temperature {

    /**
     * 腕部皮肤温度的物理可能区间（℃）。
     *
     * 下界取 10 ℃ 而不是 25 ℃：冬天室外戴着表走一段，腕表接触面报到 15~20 ℃ 是真实的，
     * 把它当噪声丢掉反而会显得"测不出来"。上界 45 ℃ 是硬上限（皮肤接触面不可能更高）。
     * 落在区间外的值只可能是传感器未就绪/解析错误，直接丢弃。
     */
    const val MIN_C = 10f
    const val MAX_C = 45f

    /** 常见范围（仅作参照带，不参与判定）。 */
    const val LOW_C = 30f
    const val HIGH_C = 36f

    /** 纵轴最小跨度（℃）。没有它，一次点检会把轴压成一条线看不出量级。 */
    private const val MIN_SPAN = 4f

    /** 必须写进界面的免责说明。 */
    val DISCLAIMER: String get() = Disclaimer.skinTemp(Text.lang)

    /** 传感器读数是否可用。0 / 负数 / NaN / 超出物理区间一律丢。 */
    fun isValid(c: Float?): Boolean =
        c != null && !c.isNaN() && !c.isInfinite() && c >= MIN_C && c <= MAX_C

    /** 展示用的一位小数（固定 Locale.US，避免某些地区用逗号当小数点）。 */
    fun format(c: Float): String = String.format(Locale.US, "%.1f", c)

    /**
     * 位置感：-1 偏低 / 0 常见范围 / 1 偏高。
     * **不是**医学判断，只用来决定文字颜色的冷暖。
     */
    fun level(c: Float): Int = when {
        c < LOW_C -> -1
        c > HIGH_C -> 1
        else -> 0
    }

    fun levelLabel(c: Float): String = when (level(c)) {
        -1 -> Text.of("s_002967bb")
        1 -> Text.of("s_0029b345")
        else -> Text.of("s_c455a02e")
    }

    /**
     * 一条曲线的纵轴范围（℃）。
     *
     * 规则与血氧那套同源的纪律，但尺度不同：
     *  · 贴数据 → 上下各留 1 ℃ 头顶空间 → 取整到 1 ℃；
     *  · 跨度不足 [MIN_SPAN]（4 ℃）时**向下**扩展补齐。
     *
     * "向下扩展"这个方向是刻意选的，也是血氧那次踩坑留下的教训：
     * 数据集中在区间上沿时往上加不动（会被 [MAX_C] 顶回来），只有往下才能补出跨度。
     * 那次的第一版写成了"往上加"，于是 `hi` 被夹回上限、跨度仍然是 5，
     * 而且那条分支看起来还"处理过了"，靠离线断言才抓出来。
     *
     * @param vals 参与取范围的数值（空列表返回 34..38，即最常见的腕部皮肤温度区间）
     */
    fun bounds(vals: List<Float>): ClosedFloatingPointRange<Float> {
        val ok = vals.filter { isValid(it) }
        if (ok.isEmpty()) return 34f..38f
        val dataLo = ok.min()
        val dataHi = ok.max()

        var lo = floor(dataLo) - 1f
        var hi = ceil(dataHi) + 1f
        // 先夹到物理区间。数据本身贴边时会把头顶空间吃掉，这没关系 ——
        // 下面补跨度那一步还会再处理一次。
        if (lo < MIN_C) lo = MIN_C
        if (hi > MAX_C) hi = MAX_C
        if (hi < dataHi) hi = dataHi
        if (lo > dataLo) lo = dataLo

        if (hi - lo < MIN_SPAN) {
            lo = hi - MIN_SPAN
            if (lo < MIN_C) {
                lo = MIN_C
                hi = (lo + MIN_SPAN).coerceAtMost(MAX_C)
            }
        }
        return lo..hi
    }

    // ─────────────── 三星健康皮肤温度的口径判定 ───────────────

    /** 数值口径。三分支而不是布尔：判不出来时要能**明说判不出来**，而不是硬塞进某一类。 */
    enum class Scale {
        /** 绝对温度（℃）。 */
        ABSOLUTE,

        /** 相对个人基线的偏差（±℃）。 */
        DEVIATION,

        /** 两种都对不上，无法判断 —— 界面按原值展示并注明口径未知。 */
        UNKNOWN,
    }

    /**
     * 判断三星健康 `SKIN_TEMPERATURE` 给的是绝对温度还是相对偏差。
     *
     * 官方 Data SDK 文档对这一项**没有写明口径**（Sensor SDK 侧的 `OBJECT_TEMPERATURE`
     * 是明确写"Celsius"的绝对值，但 Data SDK 读的是三星健康 App 里那份数据，
     * 而三星健康 App 界面上展示的正是"相对基线的偏差"）。
     *
     * 这里**故意不猜一个固定口径**，而是按数值本身落在哪个量级判断：两者量级差了一个数量级
     * 以上（±1.5 对 34.5），不可能混淆。判不出来（例如 12.0）时返回 [Scale.UNKNOWN]，
     * 界面照原值显示并标注"口径未知"，绝不会把它当成 12 ℃ 的皮肤温度去画。
     */
    fun scaleOf(v: Float): Scale = when {
        v.isNaN() -> Scale.UNKNOWN
        v >= 25f && v <= MAX_C -> Scale.ABSOLUTE
        kotlin.math.abs(v) <= 10f -> Scale.DEVIATION
        else -> Scale.UNKNOWN
    }

    /**
     * 差值的展示文本，**总是带正负号**。
     *
     * 为什么不复用 [formatByScale]：那个函数要先判量纲，而"偏移量"在任何口径下都是差值、
     * 都该带符号。固定用一个差值格式，就不会因为某个较大的差值被判成 UNKNOWN 而丢掉正负号。
     */
    fun formatDelta(v: Float): String = String.format(Locale.US, "%+.1f ℃", v)

    /** 按口径格式化成展示文本（偏差带正负号，绝对值带单位）。 */
    fun formatByScale(v: Float): String = when (scaleOf(v)) {
        Scale.ABSOLUTE -> "${format(v)} ℃"
        Scale.DEVIATION -> String.format(Locale.US, "%+.1f ℃", v)
        Scale.UNKNOWN -> format(v)
    }

    // ═══════════════ 核心体温估算（模块 #10）═══════════════
    //
    // 这是**估计**不是测量，所以它的写法与上面那套"口径判定"刻意不同：
    // 上面是"把拿到的数看懂"，这里是"从两个已知量推一个未知量"。
    // 推不出来时必须**明说推不出来**，绝不能拿一个兜底常数凑数 —— 与 PPG 脉率那条纪律同源。

    /**
     * 模型系数 α 的文献取值下界：躯干（torso）。
     *
     * 来源 John Gammel《High-Precision Temperature Sensing for Core Temperature Monitoring in
     * Wearable Electronics》(ECN, 2016) 的按部位标定表，该表被 Sensors 2018, 18(12):4347 直接引用。
     * 物理含义见 [CORE_MODEL_NOTE]：α = 组织热阻 / 表面对环境热阻，**越远端越大**
     * （直肠 0.0699 < 头 0.3094 < 躯干 0.5067 < 手 0.7665 < 脚 2.1807）。
     */
    const val ALPHA_TORSO = 0.5067f

    /** 模型系数 α 的文献取值上界：手部（hand）。 */
    const val ALPHA_HAND = 0.7665f

    /**
     * 实际用于中心估算的 α。
     *
     * **腕部在原表里没有单列**（这与体温那一处"口径没写明"的教训同型：缺的东西要显式承认，
     * 不能假装表里有）。腕（前臂远端）在解剖上位于躯干与手之间，组织热阻同理，
     * 因此取表中相邻两项的**算术中点**，并以这两项**包络**出估算区间 —— 区间宽度
     * 就是"系数没单列"这件事的诚实代价，而不是给它编一个更窄的假精度。
     */
    const val ALPHA_WRIST = (ALPHA_TORSO + ALPHA_HAND) / 2f

    /** 环境温的可采区间（℃）。超出只可能是传感器未就绪或解析错误。 */
    const val MIN_AMB_C = -20f
    const val MAX_AMB_C = 60f

    /**
     * 皮温−环境温温差的上限（℃）。
     *
     * α 是**线性化**系数（原文明确说是"经验推导"的一次项拟合），温差越大外推越离谱；
     * 而 12 ℃ 在佩戴状态下已经是极端值（刚出冷库、表带没贴皮肤、传感器被衣袖捂热后突然接触冷空气），
     * 此时环境温本身就已经不是"环境"了。宁可拒绝，也不要输出一个看着像发热的数字。
     */
    const val MAX_GRADIENT_C = 12f

    /**
     * 估算值的可信区间（℃）。
     *
     * 超过它说明**模型用错了场景**（不是"这个人真的 40 ℃"）—— 单点皮温模型的误差本来就远大于
     * 体温计，真出现超范围读数时首要嫌疑是环境温被污染。所以这里是**拒绝区间**，不是报警阈值。
     */
    const val CORE_MIN_C = 34.5f
    const val CORE_MAX_C = 39.0f

    /**
     * 估算误差的量级（℃）。文献报告腕部单点模型 MAE 约 0.30±0.08 ℃（且那还是有个人标定的情形），
     * 群体模型更差，故取 0.5 ℃ 作为展示口径，宁可写大不写小。
     */
    const val CORE_UNCERTAINTY_C = 0.5f

    /**
     * 核心体温估算结果。
     *
     * 做成 sealed 而不是"返回 nullable + 另给一个原因函数"：这个项目已经被
     * "同一个判断写两处、然后两处漂移"伤过（QRS 检出），所以**判定与原因必须是同一次计算**。
     */
    sealed interface CoreResult {

        /**
         * 给出估算。[lowC]~[highC] 是系数区间对应的包络，不是置信区间 —— 它表达的是
         * "腕部系数没单列"带来的模型不确定度，与测量噪声无关。
         */
        data class Ok(
            val centralC: Float,
            val lowC: Float,
            val highC: Float,
            val gradientC: Float,
        ) : CoreResult

        /** 本次不给估算。[reason] 是直接给用户看的一句话。 */
        data class NotApplicable(val reason: String) : CoreResult
    }

    /**
     * 由**腕部皮肤温度**与**环境温度**推算核心体温。
     *
     * ## 模型
     * `T_core = T_skin + α × (T_skin − T_ambient)`
     *
     * 它不是拍脑袋的线性拟合，而是**稳态传热解**：体内到皮肤的热流与环境带走的热流平衡，
     * `(T_core − T_skin)/R_组织 = (T_skin − T_amb)/R_环境`，整理即得上式，`α = R_组织/R_环境`。
     * 同一形式也出现在 Exergen 的动脉热平衡专利推导里（`Tc = (h/pc)(Ts − Ta) + Ts`）。
     *
     * ## 输入纪律
     * `skinC` **必须**是绝对值（手表 `watch_temp` 点检）。三星健康那套很可能是相对个人基线的
     * 偏差，量纲不同，代入会得到一个看着像正常体温的错值 —— 这是本函数无法自检的事，
     * 只能由调用方按 `source` 过滤（见 [CORE_INPUT_NOTE]）。
     *
     * @return [CoreResult.Ok] 或 [CoreResult.NotApplicable]，绝不为 null 之外的第三种形态。
     */
    fun estimateCore(skinC: Float?, ambientC: Float?): CoreResult {
        // 用 takeIf 而不是 `if (!isValid(x)) return`：`isValid` 是普通函数，
        // 编译器不会据此对 `Float?` 做智能转换，后面每一处运算都会报"nullable receiver"。
        val skin = skinC?.takeIf { isValid(it) }
            ?: return CoreResult.NotApplicable(Text.of("s_1dcb8d91"))
        val ambient = ambientC?.takeIf {
            !it.isNaN() && !it.isInfinite() && it >= MIN_AMB_C && it <= MAX_AMB_C
        } ?: return CoreResult.NotApplicable(
            Text.of("s_6395a9c0") +
                Text.of("s_ad7685cb")
        )

        val gradient = skin - ambient
        if (gradient <= 0f) {
            // ⚠⚠ 这里曾把因果写错（2026-09-28 实测纠正）。原来的注释是
            //   「环境比皮肤还热（热水澡后、热车里、刚从衣袋取出）」—— 那是**少数**情形。
            //   真机上主流情形是：那个"环境温"传感器贴在表壳背面，佩戴时读到的是
            //   **被手腕加热过的近体空气**，于是它几乎等于皮温。实测：17 次点检里 **14 次**如此，
            //   环境读数 31.4~35.7℃、皮温中位 32.2℃ → 温差中位 **−0.74℃**。
            //   **一句指错方向的理由比没有理由更糟**（与 PAT 那次「找不到上升沿」同类，
            //   那次它把排查带偏了一整轮）。
            //
            // 所以文案要说的是「这是**测法**问题，不是环境极端」，并给出**可以照做的条件** ——
            // 那几条（见 [CORE_PROTOCOL_NOTE]）正是把环境温读数拉回室温的做法。
            // 对照：华为的体温功能也是同一套物理，但它在「测量须知」里直接写死了这些条件
            //   （偏紧佩戴 10 分钟以上 / 室温 25℃ 左右安静 / 手腕无水分 / 环境切换后等 30 分钟），
            //   并在环境 <15℃ 或 >35℃ 时拒绝测量。**它没有绕过物理，它限定了测法。**
            return CoreResult.NotApplicable(
                Text.fmt("s_024cb9fc", format(ambient), format(skin)) +
                    Text.of("s_9a19fb04") +
                    Text.of("s_e35ce677") +
                    Text.of("s_910466d8") +
                    Text.of("s_02877b4a")
            )
        }
        if (gradient > MAX_GRADIENT_C) {
            return CoreResult.NotApplicable(
                Text.fmt("s_65550c3f", format(gradient), format(MAX_GRADIENT_C)) +
                    Text.of("s_87472bd7")
            )
        }

        val central = skin + ALPHA_WRIST * gradient
        if (central < CORE_MIN_C || central > CORE_MAX_C) {
            // ⚠ 这一条原来只写「落在可信区间之外，说明本次场景不适用本模型」——
            //   **它说了"不适用"，但没说"为什么"**，而原因完全可以从两个输入算出来：
            //   `推算值 = 皮温 + α×温差`，所以它离区间多远**只由温差决定**。
            //   2026-09-28 用户实测最常见的一类正是"**温差太小**"：一次点检得到
            //   皮温 33.8℃ / 环境 33.4℃（温差 0.40℃）→ 推算 34.05℃ < 34.5 → 拒答。
            //   此时该说的是"环境温度太接近皮温，算不出热流"，而且要给出**差多少**：
            //   按本次皮温反推所需的最小温差（`(CORE_MIN_C − 皮温) / α`）与对应的环境温上限。
            //   —— 与 `gradient ≤ 0` 那条同源：**指错方向的理由比没有理由更糟**。
            val needGradient = (CORE_MIN_C - skin) / ALPHA_WRIST
            return if (central < CORE_MIN_C) {
                // ⚠ 只描述"数值落在区间哪一侧"，不下关于**人**的结论（不写"偏低"）。
                // ⚠ 而且**不能再给"换房间/紧戴"这类建议**了：2026-09-28 用实测证明这一路
                //   （`AMBIENT_TEMPERATURE`）**不是室温**，而是随手表自身温度变化的量 ——
                //   佩戴时被手腕与机身加热（长期 31~36℃），摘表后按几十分钟的常速才降。
                //   所以"到 25℃ 房间去测"**不可能改善它**，给了会让人白折腾。
                //   （保留 `needGradient` 是为了**解释**为什么：让用户看到差多少，而不是给处方。）
                CoreResult.NotApplicable(
                    Text.fmt("s_e4b7e37c", format(central), format(CORE_MIN_C)) +
                        Text.fmt("s_386e068b", format(skin), format(ambient), format(gradient)) +
                        Text.fmt("s_5d9cb6b0", format(needGradient)) +
                        Text.of("s_43bad093") +
                        Text.of("s_a99055e4")
                        + Text.of("s_13769b90")
                )
            } else {
                CoreResult.NotApplicable(
                    Text.fmt("s_2d6769a9", format(central), format(CORE_MAX_C)) +
                        Text.fmt("s_d75ee59b", format(skin), format(ambient), format(gradient)) +
                        Text.of("s_01fd3d49") +
                        Text.of("s_904df289")
                )
            }
        }

        return CoreResult.Ok(
            centralC = central,
            lowC = skin + ALPHA_TORSO * gradient,
            highC = skin + ALPHA_HAND * gradient,
            gradientC = gradient,
        )
    }

    /** 系数区间包络的展示文本，例如 `35.5~37.3℃`。 */
    fun coreIntervalText(r: CoreResult.Ok): String =
        "${format(r.lowC)}~${format(r.highC)}℃"

    /** 模型形式与**系数来源**，必须能出现在界面上（可解释性要求，不比公式更少）。 */
    val CORE_MODEL_NOTE: String get() =
        Text.of("s_95ccdd38") +
            Text.of("s_46b7635c") +
            Text.of("s_f779d324") +
            Text.of("s_a8818b13") +
            Text.of("s_d350fbc7")

    /** 输入纪律：哪一路温度能进模型。 */
    val CORE_INPUT_NOTE: String get() =
        Text.of("s_ac64abd5") +
            Text.of("s_e4d2df73")

    /**
     * **测量条件** —— 想让核心体温估算真的出数，必须满足这几条。
     *
     * ## 为什么要把"测法"单独写成一条常量
     *
     * 2026-09-28 用实测数据查清：这项在佩戴随意时**只有 6% 能出数**
     * （17 次点检里 14 次卡在"环境温不低于皮温"）。原因不是算法，而是
     * **表壳背面的环境温传感器读到的是被手腕加热的近体空气**（实测环境读数 31.4~35.7℃，
     * 与皮温几乎相同）。要让它读到接近室温的值，只能靠**限定测法**：
     * 表带偏紧、静置、房间温度适中、手腕干燥、避开热源与气流。
     *
     * ⚠ 这几条**不是照抄某个厂商的话术**，每一条都对应一个实测或已知的失效模式：
     * · 偏紧 + 静置 10 分钟 ← 实测同一场 6 分钟内皮温跨 **10.74℃**（表带松/在动），
     *   而这 10℃ 与生理无关；
     * · 室温约 25℃ ← 模型的分辨力全部来自 `皮温 − 环境温`，环境读数偏高时温差塌掉；
     * · 手腕干燥 ← 水/酒精蒸发会让皮温偏低（偏差方向确定，且不小）；
     * · 环境切换后等 30 分钟 ← 体表回稳有滞后（核心到腕部的热延迟文献值 60~120 分钟）。
     *
     * ⚠ **不满足这些条件时不给数**是设计行为（见 [estimateCore] 的门控），
     * 但"给不出"必须说清是**测法**问题，并把这些条件摆给用户 ——
     * 否则用户只会觉得功能坏了。
     */
    val CORE_PROTOCOL_NOTE: String get() =
        Text.of("s_411d3b89") +
            Text.of("s_0ef2fb0b") +
            Text.of("s_2ff9a9ef") +
            Text.of("s_9d9e79e5") +
            Text.of("s_9322a64e") +
            Text.of("s_8a322511") +
            Text.of("s_b172e221")

    /** 必须与估算值同时出现的免责说明。 */
    val CORE_DISCLAIMER: String get() = Disclaimer.coreTemp(Text.lang)

    // ═══════════════════ 夜间腕温偏移（2026-09-21）═══════════════════
    //
    // ## 这是本指标真正的用途，也是"能不能测出发烧"那个问题的答案
    //
    // 用**绝对腕温**判断发烧做不到，三条理由都是物理的、不是精度的：
    //  ① 腕部皮温是**被调节量**，不是核心体温的被动映射（通常比核心低 1~3℃）；
    //  ② ⚠ **寒战期方向相反** —— 发烧起病时下丘脑把设定点调高，身体"觉得冷"，
    //     于是外周血管收缩保热，**手/腕反而变凉**。所以腕温下降不能排除发烧、
    //     腕温上升也不能证明发烧 —— 连"升还是降"都分不出来；
    //  ③ 室温/被褥/袖口/表带松紧各自就能造成 ±2~3℃，比发烧在腕部的信号还大。
    //
    // 而「**夜间**」+「**相对你自己**」这两个限定把混淆项压住了：同一张床、相近的室温与被褥、
    // 同一时段、手臂不动；睡眠期又正是外周血管扩张、皮温高而稳的时候。
    // **三星健康自己就是这么用的** —— 它那一项从不给绝对值，只给"相对个人平均值的偏差"。
    // 所以我们跟它对齐，而不是去造一个"体温计"。

    /** 基线至少需要几晚（**不含**被评估的那一晚）。少于它就给不出偏移。 */
    const val BASELINE_MIN_NIGHTS = 3

    /** 基线最多取最近几晚。 */
    const val BASELINE_MAX_NIGHTS = 14

    /**
     * 偏移多大才值得多写一句话（℃）。
     *
     * ⚠ **这是提示门槛，不是医学阈值。** 0.5℃ 取的是"比日常夜间波动略大"的量级，
     * 目的是压掉噪声，**不是**"超过它就发烧了"。界面上不许把它写成结论。
     */
    const val DEVIATION_NOTABLE_C = 0.5f

    /** 夜间腕温偏移的结果。sealed：判定与原因在**同一次计算**里一起返回（与 [CoreResult] 同款）。 */
    sealed interface DeviationResult {
        /**
         * 算出来了。
         *
         * ⚠ 这些字段**只描述"那一晚与你自己平时差多少"**，不承载任何医学含义。
         */
        data class Ok(
            /** 被评估那一晚的值（口径随数据本身的 [scaleOf]）。 */
            val latestC: Float,
            /** 基线中位数（与 [latestC] 同口径）。 */
            val baselineC: Float,
            /** 偏移 = latest − baseline。正 = 比平时高。 */
            val deviationC: Float,
            /** 真正参与基线的晚数。 */
            val baselineNights: Int,
            /** 是否够得着 [DEVIATION_NOTABLE_C]。只决定"要不要多写一句"，不是结论。 */
            val notable: Boolean,
        ) : DeviationResult

        /** 给不出，并说明为什么。界面把 [reason] 原样显示。 */
        data class NotApplicable(val reason: String) : DeviationResult
    }

    /** 中位数。**不用均值**：一晚被被子焐热就能把均值拉偏，中位数对少数异常晚稳健。 */
    private fun medianOf(xs: List<Float>): Float {
        val t = xs.sorted()
        val m = t.size / 2
        return if (t.size % 2 == 1) t[m] else (t[m - 1] + t[m]) / 2f
    }

    /**
     * 夜间腕温偏移：把「最近一晚」与**你自己之前若干晚**比。
     *
     * @param nights 每晚一个值，**新的在前**（与 `TemperatureDao.recent()` 的 DESC 顺序一致）。
     *   ⚠ 调用方必须先**按归属日去重**：三星会重新分析睡眠、结束时刻可能挪几分钟，
     *   同一晚落成两行的话，那一晚会在基线里被算两次（等于偷偷加了权），而用户完全看不出来。
     *
     * 三道门控，任一条不满足就拒答（`reason` 直接给用户看）：
     *  ① 每一晚都要能判出口径（[scaleOf] 不为 [Scale.UNKNOWN]）；
     *  ② **所有晚必须同一口径** —— 绝对值（33.2）与偏差（0.3）混进同一个基线会算出垃圾，
     *     这是本文件顶部那条红线①的机器化执行；
     *  ③ 除待评估那一晚外，至少还要有 [BASELINE_MIN_NIGHTS] 晚做基线。
     *
     * ⚠ 本函数**不做任何医学判断**，也不产出"是否发烧"的结论。
     */
    fun nightDeviation(nights: List<Float>): DeviationResult {
        val vals = nights.filter { !it.isNaN() }
        val need = BASELINE_MIN_NIGHTS + 1
        if (vals.size < need) {
            return DeviationResult.NotApplicable(
                Text.fmt("s_7ac010b0", need - vals.size, need, BASELINE_MIN_NIGHTS) +
                    Text.fmt("s_5112833e", vals.size)
            )
        }
        val scale = scaleOf(vals.first())
        if (scale == Scale.UNKNOWN) {
            return DeviationResult.NotApplicable(
                Text.fmt("s_8b2589ef", format(vals.first()))
            )
        }
        if (vals.any { scaleOf(it) != scale }) {
            return DeviationResult.NotApplicable(
                Text.of("s_50573d28") +
                    Text.of("s_c2111bfd")
            )
        }
        val latest = vals.first()
        val base = medianOf(vals.drop(1).take(BASELINE_MAX_NIGHTS))
        val dev = latest - base
        return DeviationResult.Ok(
            latestC = latest,
            baselineC = base,
            deviationC = dev,
            baselineNights = minOf(vals.size - 1, BASELINE_MAX_NIGHTS),
            notable = kotlin.math.abs(dev) >= DEVIATION_NOTABLE_C,
        )
    }

    /**
     * 偏移的一句话标签。
     *
     * ⚠ **措辞上限就是这一句。** 不许出现「发烧」「感染」「生病」这类结论词 ——
     * 见 [NIGHT_DEVIATION_NOT_FEVER] 的理由。
     */
    fun deviationLabel(dev: Float): String = when {
        dev >= DEVIATION_NOTABLE_C -> Text.of("s_8bc84021")
        dev <= -DEVIATION_NOTABLE_C -> Text.of("s_8bc7f497")
        else -> Text.of("s_8989f03b")
    }

    /** 这一路的口径：跟谁比、为什么要加"夜间"和"相对自己"两个限定。 */
    val NIGHT_DEVIATION_HOW: String get() =
        Text.of("s_d762d6ad") +
            Text.of("s_f55a91ff") +
            Text.of("s_fd9b38a1") +
            Text.of("s_62df67b1")

    /** 必须与偏移同时显示的第一条边界。 */
    val NIGHT_DEVIATION_NOT_FEVER: String get() =
        Text.of("s_fd93232e") +
            Text.of("s_686b5d69")

    /** 必须与偏移同时显示的第二条边界 ——**防的是"腕温不高就以为没发烧"**。 */
    val NIGHT_DEVIATION_CHILLS: String get() =
        Text.of("s_4b1b71aa") +
            Text.of("s_745fd0da")

    /** 该做什么（把用户导向正确的工具，而不是让我们冒充体温计）。 */
    val NIGHT_DEVIATION_ADVICE: String get() = Disclaimer.nightTempAdvice(Text.lang)
}
