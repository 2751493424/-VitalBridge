package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 压力 / 疲劳评分（模块 #12）：把 HRV、静息心率、皮温−环境温梯度**相对这个人自己的基线**
 * 折算成两个 0~100 的分数。
 *
 * ## 为什么"相对自身基线"是唯一可辩护的口径
 * RMSSD 随年龄单调下降（20 岁与 70 岁能差几倍），还受体位、睡眠、咖啡因、体质影响。
 * 拿一组固定阈值去判"压力偏高"，对一部分人恒为真、对另一部分人恒为假 ——
 * 那不是评分，是把个体差异读成了结论。所以本模块与 `Hrv` 完全同源：
 * **没有"正常范围"，只有"你这次比你平时怎么样"**。
 *
 * 代价是必须先有基线：可比记录不足时直接拒答（见 [MIN_BASELINE] 与 [MIN_BASELINE_SPAN_MS]），
 * 界面要明说"还差几次"，而不是给一个数。
 *
 * ## 压力与疲劳是**两个**分数，不是同一个数的两种叫法
 * | 分数 | 时间尺度 | 输入 | 含义 |
 * |---|---|---|---|
 * | 压力 [Result.Ok.stress] | 即时（这一次） | 本次 RMSSD/心率/皮温梯度 vs 自身基线 | 此刻的自主神经负荷 |
 * | 疲劳 [Result.Ok.fatigue] | 趋势（近几周） | 近期窗口 vs 更早窗口的基线漂移 | 累积的恢复不足 |
 *
 * 两者用同一批信号，但**问的问题不同**：一次低 HRV 是"现在紧张"，连续一周低 HRV 是"没缓过来"。
 * 应对方式完全不同，合成一个数就等于把这两件事抹平。
 *
 * ## 为什么活动量只做门控、不进加权
 * 总表把"活动量"列在输入里，但加速度只有**打开 App 期间**才采（Wear OS 不允许后台常驻采样），
 * 用它算"今天的累积活动量"会系统性低估 —— 用户今天走了多少路，这张表根本不知道。
 * 拿一个已知不完整的量去打分，等于把"没采到"当成"没动"。
 * 所以活动量在这里只做两件事：**活动中直接拒答**（走动中的 RR 反映步频）、
 * **非静息时标注**（见 [Result.Ok.caveats]）。这是 `Activity` 那个模块自身的边界。
 *
 * ## 分量的刻度：25 分 = 一个"显著偏离单位"
 * 每个分量先算出一个相对偏离 `dev`（正 = 朝"负荷大"的方向），再映射成
 * `50 + [BAND] × dev`，裁剪到 0~100。**50 = 与自己的基线持平**，
 * 每 [BAND]（25）分对应一个显著偏离单位。这个线性映射是刻意选的：
 * 分段线性比 tanh/logistic 好在**能倒推**（"75 分 = 偏离了一个单位"），
 * 而可解释性在这个模块里比曲线平滑更重要。
 *
 * 各分量的"一个单位"取多少，是对**日间变异量级**的估计，不是拍脑袋：
 *  - RMSSD：个体内日间 CV 约 20~30%（短记录更大）→ [UNIT_HRV] = 0.30；
 *  - 静息心率：日间 CV 约 4~6% → [UNIT_HR] = 0.08（宁可保守，心率受咖啡因/体位影响大）；
 *  - 皮温−环境温梯度：受佩戴与衣袖影响，短时波动约 ±1℃ → [UNIT_TEMP_C] = 1.0；
 *  - 趋势（疲劳）：窗口**中位数**已经把日间噪声平滑掉约 √5 ≈ 2.2 倍（30%/2.2 ≈ 14%）
 *    → [UNIT_TREND] = 0.15。
 *
 * ## 权重是工程分配，不是临床标定
 * [W_HRV] / [W_HR] / [W_TEMP] 的取值依据是"哪个量更直接、更不易被无关因素污染"：
 * HRV 是自主神经对心率的直接调节 → 最重；心率受体位咖啡因影响 → 次之；
 * 皮温受环境与佩戴影响最大 → 最轻。**没有经过任何临床标定**，
 * 界面必须写清这一点（见 [WEIGHT_NOTE]），不能让用户以为 60 分是某个权威量表的 60 分。
 */
object Stress {

    // ═══════════════════════ 输入 ═══════════════════════

    /**
     * 一次可用于评分的观测：一次心电 HRV 的结果摘要。
     *
     * 刻意只收**已经算好的 RMSSD 与平均心率**，而不是逐拍间期 ——
     * 逐拍间期是 `Hrv` 的输入，让调用方先跑 `Hrv.analyze` 再喂进来，
     * 才能保证"评分用的 RMSSD"与"卡片上显示的 RMSSD"是同一个数（界面值与判据同源那条纪律）。
     *
     * @param measuredAt 测量时刻（epoch 毫秒）。与历史记录去重、与时间窗口排序都靠它。
     * @param rmssdMs 本次 RMSSD（毫秒）。<= 0 视为无效。
     * @param hrMean 本次平均心率（次/分）。<= 0 视为无效。
     * @param activityLevel 测量时的活动档位（`Activity.Level.*`），null = 拿不到。
     */
    data class Obs(
        val measuredAt: Long,
        val rmssdMs: Double,
        val hrMean: Double,
        val activityLevel: String? = null,
    ) {
        /** 静息状态（[Hrv.RestState]）。由档位派生，不单独存，避免两处漂移。 */
        val rest: String get() = Hrv.restStateOf(activityLevel)

        /** 是否可以参与统计（数值有效且当时没在明显活动）。 */
        val usable: Boolean get() = rmssdMs > 0 && hrMean > 0 && rest != Hrv.RestState.MOVING
    }

    /**
     * 一次皮温点检的**梯度**（皮温 − 环境温，℃）。
     *
     * ## 为什么用梯度而不是皮温本身
     * 腕部皮温的绝对值几乎完全被环境温度决定（20℃ 的房间里和 28℃ 的房间里能差好几度），
     * 直接拿绝对值当"应激指标"首先测到的是"今天空调开得低"。
     * 梯度 `(皮温 − 环境温)` 消掉环境温度的一阶影响 —— 与 `Temperature.estimateCore`
     * 用的是同一个量，本模块不另立一套物理口径。
     * 急性应激时外周血管收缩 → 皮温下降 → **梯度变小**，所以梯度下降对应分数上升。
     *
     * ## 为什么只收有环境温的点检
     * 没有 `ambientC` 就凑不出梯度，而"用 25℃ 当默认环境温度"等于编一个输入 ——
     * 与核心体温那处"环境温拿不到就不给估算"是同一条纪律。
     */
    data class TempObs(
        val measuredAt: Long,
        /** 皮温 − 环境温（℃）。必须 > 0 且 <= [Temperature.MAX_GRADIENT_C] 才有效。 */
        val gradientC: Float,
    ) {
        /** 是否落在模型可接受的区间（与 `Temperature.estimateCore` 的门控一致）。 */
        val usable: Boolean
            get() = !gradientC.isNaN() && gradientC > 0f &&
                gradientC <= Temperature.MAX_GRADIENT_C
    }

    // ═══════════════════════ 基线 ═══════════════════════

    /**
     * 参与基线构造的最多条数。与 `Hrv` 卡片"近 20 条"同源：
     * 再多了会把很久以前的状态也算进"平时"，再少了中位数不稳。
     */
    const val BASELINE_SIZE = 20

    /**
     * 构造基线所需的最少可比记录数。
     *
     * 4 条是"中位数能抗住 1 个离群值"的下限（3 条时中位数就是正中间那条，
     * 再多一条坏数据才会把它挤走）。比 HRV 卡片严格：那张卡只是"给个对比"，
     * 而当**基线本身错了，相对分数整体就错了**，所以评分要多要一条。
     */
    const val MIN_BASELINE = 4

    /**
     * 基线记录必须覆盖的最短**时间跨度**（毫秒）：36 小时。
     *
     * 只卡条数不够：一天之内连测 5 次，条数够了，但那 5 次全在同一个昼夜相位上
     * （比如都是晚饭后坐着测的），它刻画的是"你那个时段"而不是"你平时"。
     * HRV 有明显的昼夜节律（晨间与睡前能差 20~30%），基线若只取自一个相位，
     * 换个时间测就会被判成"偏离" —— 那是钟点造成的假信号。
     * 36 小时保证至少跨过两个不同的昼夜相位，但又不至于让用户等上一周。
     */
    const val MIN_BASELINE_SPAN_MS = 36 * 60 * 60 * 1000L

    /** 皮温基线的最少条数与最多条数。点检本来就稀疏，比心率侧宽松。 */
    const val MIN_TEMP_BASELINE = 3
    const val TEMP_BASELINE_SIZE = 10

    /**
     * 本次观测可接受的最旧皮温点检：6 小时。
     *
     * 点检与心电**不是同一次测量**（点检是想起才按一下），所以必须配对。
     * 超过 6 小时就不配 —— 用昨晚的皮温去解释今天下午的心率，
     * 得到的是"看起来有道理"的错误归因，比不配对更糟。
     */
    const val MAX_TEMP_AGE_MS = 6 * 60 * 60 * 1000L

    /** 疲劳趋势：每个窗口取多少条、至少要有几条。 */
    const val TREND_WINDOW = 5
    const val TREND_MIN_N = 3

    /**
     * 两个趋势窗口的**中位时刻**至少相隔多久：3 天。
     *
     * 用中位时刻而不是首尾：后者会被一端的离群记录拉偏。
     * 3 天是"能叫趋势"的下限 —— 相隔 3 小时的两个窗口，差的只是昼夜相位，不是恢复状态。
     */
    const val TREND_MIN_GAP_MS = 3 * 24 * 60 * 60 * 1000L

    // ═══════════════════════ 刻度与权重 ═══════════════════════

    /**
     * 一个"显著偏离单位"对应多少分。
     *
     * 取 25 而不是 50：后者会让"偏离一个单位"就顶到 100 分，
     * 而日间波动本就能到 1 个单位左右，那等于每天都在满分区附近抖动。
     */
    const val BAND = 25.0

    /** 一个显著偏离单位 = RMSSD 相对变化 30%（个体内日间 CV 的量级）。 */
    const val UNIT_HRV = 0.30

    /** 一个显著偏离单位 = 静息心率相对变化 8%（日间 CV 约 4~6%，取 8% 保守）。 */
    const val UNIT_HR = 0.08

    /** 一个显著偏离单位 = 皮温−环境温梯度变化 1.0℃（受佩戴与衣袖影响的短时波动量级）。 */
    const val UNIT_TEMP_C = 1.0f

    /** 一个显著偏离单位 = 窗口**中位数**之间漂移 15%（已平滑掉大部分日间噪声）。 */
    const val UNIT_TREND = 0.15

    const val W_HRV = 0.60f
    const val W_HR = 0.25f
    const val W_TEMP = 0.15f

    /** 疲劳趋势只用 HRV 与心率（皮温点检没有可比的历史趋势），权重按前两项归一化。 */
    const val W_TREND_HRV = 0.70f
    const val W_TREND_HR = 0.30f

    // ═══════════════════════ 分级 ═══════════════════════

    /** 分级。按"偏离了几个单位"切，见 [levelOf]。 */
    object Level {
        /** < 0.5 个单位：低于自己平时。 */
        const val RELAXED = "relaxed"

        /** ±0.5 个单位内：与平时相当。**这一档是默认状态，不是"没问题"的保证**。 */
        const val NORMAL = "normal"

        /** 0.5~1.2 个单位：偏高。 */
        const val ELEVATED = "elevated"

        /** > 1.2 个单位：明显偏高。 */
        const val HIGH = "high"
    }

    /**
     * 分级边界（分数）：以 50（= 与基线持平）为中心，按 [BAND] 的倍数切。
     * 建成字面量而不是在 [levelOf] 里写 `50 + 0.5 * BAND`：边界要在台架与界面上
     * 被单独引用（例如"这一档到多少分"），写死在函数体里就会有两处各算各的。
     */
    const val RELAXED_MAX = 37.5   // 50 − 0.5 个单位：低于它才算"低于平时"
    const val NORMAL_MAX = 62.5    // 50 + 0.5 个单位
    const val HIGH_MIN = 80.0      // 50 + 1.2 个单位

    fun levelOf(score: Double): String = when {
        score < RELAXED_MAX -> Level.RELAXED
        score < NORMAL_MAX -> Level.NORMAL
        score < HIGH_MIN -> Level.ELEVATED
        else -> Level.HIGH
    }

    fun levelLabel(level: String): String = when (level) {
        Level.RELAXED -> Text.of("s_b5292207")
        Level.NORMAL -> Text.of("s_e624a4f0")
        Level.ELEVATED -> Text.of("s_0029b345")
        Level.HIGH -> Text.of("s_c7bb85ed")
        else -> Text.of("s_c34c0523")
    }

    /** 圆屏/一行摘要用的短标签。 */
    fun levelShort(level: String): String = when (level) {
        Level.RELAXED -> Text.of("s_00343638")
        Level.NORMAL -> Text.of("s_002e2bbe")
        Level.ELEVATED -> Text.of("s_0029b345")
        Level.HIGH -> Text.of("s_00317d70")
        else -> "—"
    }

    // ═══════════════════════ 结果 ═══════════════════════

    /** 基线：一组"这个人平时是多少"的中位数。 */
    data class Baseline(
        /** 参与构造的记录条数。 */
        val count: Int,
        /** 这些记录覆盖的时间跨度（毫秒）。 */
        val spanMs: Long,
        val rmssdMed: Double,
        val hrMed: Double,
        /** 皮温梯度的中位数（℃）。null = 点检不足，该分量不参与。 */
        val gradientMedC: Float?,
        val gradientCount: Int,
        /** 基线里活动状态未判定（[Hrv.RestState.UNKNOWN]）的条数 —— 要在界面上说出来。 */
        val unknownRestCount: Int,
    )

    /** 基线的构造结果：要么 [ok]，要么 [issue] 说明差在哪（[issue] 直接给用户看）。 */
    data class BaselineBuild(val ok: Baseline?, val issue: String?)

    /** 一个分量的完整分解（界面据此说明"这个分数是怎么来的"）。 */
    data class Part(
        val name: String,
        /** 归一化后的权重（全部相加 = 1）。 */
        val weight: Float,
        val currentText: String,
        val baselineText: String,
        /** 该分量单独的得分（0~100，50 = 与基线持平）。 */
        val componentScore: Int,
        /** 偏离了几个显著单位（可正可负），界面上比裸分数更有信息量。 */
        val devUnits: Double,
    )

    // ⚠⚠ 以下三个是**稳定匹配键**：`analyze` 里构造 `Part` 用的名字，`when (name)` 也靠它分派。
    // **中文必须原样保留**，翻了会让 `when` 全部落到 `else` —— 而且**不报错**，只是"当前值"
    // 那一列全部显示成皮温格式。显示一律走 [componentLabel]。
    // ⚠ 抽成常量是刻意的：[MATCHING_KEYS_FOR_TEST] 拿它给台架逐一核对
    // 「每个键都有显示名、且英文显示名不是中文」—— 别再写第二份字面量（两处必然漂移）。
    private const val K_HRV = "HRV（RMSSD）"
    private const val K_HR = "静息心率"
    private const val K_TREND_HRV = "近期 vs 更早 · RMSSD"

    /**
     * 分量名 → `Text.kt` 的 key。
     *
     * ⚠ [Part.name] 是**匹配键**：`analyze` 里 `when (name)` 靠它分派（[K_HRV] /
     * [K_HR] / 皮温那条）。**中文必须原样保留**，翻了会让 `when` 全部落到 `else`
     * —— 而且**不报错**，只是"当前值"那一列全部显示成皮温格式。所以显示时走
     * [componentLabel] 换语言。
     *
     * ⚠ 皮温那条**不在这张表里**：它在构造时就是 `Text.of("s_51ebed62")`（已是当前语言），
     * 落进 [componentLabel] 的 `else` 分支原样返回即可 —— 别把它也塞进来，
     * 那会拿"当前语言的串"去查"中文键"，永远查不中。
     */
    private val COMPONENT_LABEL_KEYS: Map<String, String> = mapOf(
        K_HRV to "s_2a1c4318",
        K_HR to "s_61254d1a",
        // ⚠ 这一条 2026-10-02 才补上：它也是**匹配键**（`analyze` 里构造 `Part` 用的名字），
        //   但此前没进这张表 → `componentLabel` 落进 `else` 原样返回中文，
        //   英文/韩文界面下这一行一直是中文（与 Energy 宫格同一类错）。
        K_TREND_HRV to "s_f6b76756",
    )

    /** [componentLabel] 用到的全部 key（**台架**：核对与 [MATCHING_KEYS_FOR_TEST] 一一对应）。 */
    val COMPONENT_LABEL_KEYS_FOR_TEST: Map<String, String> get() = COMPONENT_LABEL_KEYS

    /** 全部**匹配键**（**台架用**）。与 [analyze] 里 `Triple` 的第一个元素必须一致。 */
    val MATCHING_KEYS_FOR_TEST: Set<String> get() = setOf(K_HRV, K_HR, K_TREND_HRV)

    /** 分量的**显示名**（当前语言）；认不出来的原样返回。 */
    fun componentLabel(name: String): String =
        COMPONENT_LABEL_KEYS[name]?.let { Text.of(it) } ?: name

    /** 一个分数（压力或疲劳）。 */
    data class Score(
        val value: Int,
        val level: String,
        /** 参与这个分数的分量分解。 */
        val parts: List<Part>,
    ) {
        val levelText: String get() = levelLabel(level)
    }

    /** 本次各量的实际值（界面显示"输入"用）。 */
    data class Current(
        val rmssdMs: Double,
        val hrMean: Double,
        /** 本次配到的皮温梯度（℃），null = 没配上。 */
        val gradientC: Float?,
        /** 配到的那次点检距本次观测多久（毫秒）。null = 没配上。 */
        val gradientAgeMs: Long?,
        val rest: String,
    )

    sealed interface Result {

        /**
         * 给出评分。
         *
         * @param fatigue null = 疲劳给不出（窗口不足），原因在 [fatigueNote] 里。
         *   **压力与疲劳分别给**：疲劳需要跨周的趋势，刚用几天时只能给压力 ——
         *   不给压力会浪费掉已有的信息，硬给疲劳就是拿 3 天数据外推"累积"，那是编。
         */
        data class Ok(
            val current: Current,
            val baseline: Baseline,
            val stress: Score,
            val fatigue: Score?,
            /** 疲劳为什么给不出 / 给了的话用了几个窗口（永远有话说）。 */
            val fatigueNote: String,
            /** 需要在界面上明说的注意事项（非静息、基线含未判定记录、皮温未纳入…）。 */
            val caveats: List<String>,
        ) : Result

        /** 本次不给评分。[reason] 是直接给用户看的一句话。 */
        data class NotApplicable(val reason: String) : Result
    }

    // ═══════════════════════ 主入口 ═══════════════════════

    /**
     * 计算压力与疲劳评分。
     *
     * @param current 本次观测（由 `Hrv.analyze` 的 [Hrv.Result.Ok] 摘出来）。
     *   调用方**必须先跑过 HRV**：`Hrv.Result.NotApplicable` 时不要调用本函数，
     *   直接把 HRV 的拒答原因显示出来 —— 那份原因比这里能给的更具体。
     * @param history 该用户**全部**历史观测（可以包含 [current]，内部按时刻排除）。
     * @param temps 全部皮温点检（含环境温的）。没有就传空，皮温分量自动缺席。
     * @param nowMs 参考时刻（默认取 [current] 的时刻）。基线只收它之前的记录 ——
     *   用"未来"的记录当基线是一种隐蔽的错误（用户回看旧记录时会觉得分数变了）。
     */
    fun analyze(
        current: Obs,
        history: List<Obs>,
        temps: List<TempObs> = emptyList(),
        nowMs: Long = current.measuredAt,
    ): Result {
        // 门控顺序有讲究：先说"这次测得不行"，再说"历史不够"。
        if (current.rmssdMs <= 0 || current.hrMean <= 0) {
            return Result.NotApplicable(
                Text.of("s_efa90637") +
                    Text.of("s_7bed1055"),
            )
        }
        if (current.rest == Hrv.RestState.MOVING) {
            return Result.NotApplicable(
                Text.fmt("s_a1ed4110", Activity.label(current.activityLevel)) +
                    Text.of("s_8cb906db") +
                    Text.of("s_c869cb06"),
            )
        }

        val build = buildBaseline(
            history = history,
            excludeAt = current.measuredAt,
            uptoMs = nowMs,
            temps = temps,
        )
        val base = build.ok
            ?: return Result.NotApplicable(
                Text.of("s_7030b86e") + (build.issue ?: Text.of("s_4a5f2a58")) +
                    Text.of("s_387b3920") +
                    Text.of("s_8c988480")
            )

        // 皮温：先配对本次，再在"它之前"的点检里取基线。
        val curTemp = pickTemp(temps, current.measuredAt)
        val curGrad = curTemp?.gradientC
        val ageMs = curTemp?.let { abs(it.measuredAt - current.measuredAt) }
        val tempUsable = curGrad != null && base.gradientMedC != null

        val devHrv = (base.rmssdMed - current.rmssdMs) / base.rmssdMed / UNIT_HRV
        val devHr = (current.hrMean - base.hrMed) / base.hrMed / UNIT_HR
        val devTemp = if (tempUsable) {
            ((base.gradientMedC!! - curGrad!!) / UNIT_TEMP_C).toDouble()
        } else {
            null
        }

        val raw = ArrayList<Triple<String, Float, Double>>(3)
        raw.add(Triple(K_HRV, W_HRV, devHrv))
        raw.add(Triple(K_HR, W_HR, devHr))
        if (devTemp != null) {
            raw.add(Triple(Text.of("s_51ebed62"), W_TEMP, devTemp))
        }
        val stressScore = combine(raw)
        val stressLevel = levelOf(stressScore)
        val stressParts = raw.map { (name, w, dev) ->
            val wNorm = (w / raw.sumOf { (_, ww, _) -> ww.toDouble() }).toFloat()
            Part(
                name = name,
                weight = wNorm,
                currentText = when (name) {
                    K_HRV -> "${Hrv.ms(current.rmssdMs)} ms"
                    K_HR -> Text.fmt("s_d7a73d8b", current.hrMean.roundToInt())
                    else -> "${Temperature.format(curGrad!!)} ℃"
                },
                baselineText = when (name) {
                    K_HRV -> "${Hrv.ms(base.rmssdMed)} ms"
                    K_HR -> Text.fmt("s_e9acf174", base.hrMed.roundToInt())
                    else -> "${Temperature.format(base.gradientMedC!!)} ℃"
                },
                componentScore = scoreOf(dev),
                devUnits = dev,
            )
        }

        val (fatigue, fatigueNote) = computeFatigue(history, nowMs)

        val caveats = ArrayList<String>()
        when (current.rest) {
            Hrv.RestState.MOVING_LIGHT -> caveats.add(
                Text.of("s_f1af72a9")
            )
            Hrv.RestState.UNKNOWN -> caveats.add(
                Text.of("s_43b9e868")
            )
        }
        if (base.unknownRestCount > 0) {
            caveats.add(
                Text.fmt("s_e7ecf9be", base.unknownRestCount) +
                    Text.of("s_982e4a33")
            )
        }
        if (!tempUsable) {
            caveats.add(
                if (curGrad == null) {
                    Text.of("s_e4fbd45e") +
                        Text.fmt("s_12dbc408", MAX_TEMP_AGE_MS / 3600_000)
                } else {
                    Text.fmt("s_a0321705", MIN_TEMP_BASELINE) +
                        Text.of("s_b86ffef2")
                }
            )
        } else if (ageMs != null && ageMs > 3600_000L) {
            caveats.add(
                Text.fmt("s_679e78f3", Hrv.sec0(ageMs), ageMs / 3600_000) +
                    Text.of("s_795f8409")
            )
        }

        return Result.Ok(
            current = Current(
                rmssdMs = current.rmssdMs,
                hrMean = current.hrMean,
                gradientC = curGrad,
                gradientAgeMs = ageMs,
                rest = current.rest,
            ),
            baseline = base,
            stress = Score(stressScore.roundToInt(), stressLevel, stressParts),
            fatigue = fatigue,
            fatigueNote = fatigueNote,
            caveats = caveats,
        )
    }

    // ═══════════════════════ 基线构造 ═══════════════════════

    /**
     * 构造"这个人平时是多少"的基线。
     *
     * 只收 [Obs.usable] 的记录（数值有效且当时没在明显活动）。
     * **中心量一律用中位数**：一次漏检的伪迹、一次没按住表冠的测量，
     * 都能把均值拉走一大截，而中位数对"多达一半记录有问题"仍稳 —— 与 `Cycle` 用中位数
     * 而不是均值算周期长度是同一条理由。
     */
    fun buildBaseline(
        history: List<Obs>,
        excludeAt: Long,
        uptoMs: Long,
        temps: List<TempObs> = emptyList(),
    ): BaselineBuild {
        val past = history
            .asSequence()
            .filter { it.usable && it.measuredAt != excludeAt && it.measuredAt <= uptoMs }
            .sortedByDescending { it.measuredAt }
            .take(BASELINE_SIZE)
            .toList()

        if (past.size < MIN_BASELINE) {
            return BaselineBuild(
                null,
                Text.fmt("s_e70c48f1", past.size, MIN_BASELINE),
            )
        }
        val span = past.first().measuredAt - past.last().measuredAt
        if (span < MIN_BASELINE_SPAN_MS) {
            val hours = span / 3600_000.0
            return BaselineBuild(
                null,
                Text.fmt("s_cba0d69c", past.size, fmt1(hours)) +
                    Text.fmt("s_02b69cea", MIN_BASELINE_SPAN_MS / 3600_000) +
                    Text.of("s_ca8789cf"),
            )
        }

        val rmssdMed = Hrv.baselineMedian(past.map { it.rmssdMs })
        val hrMed = Hrv.baselineMedian(past.map { it.hrMean })
        if (rmssdMed == null || rmssdMed <= 0 || hrMed == null || hrMed <= 0) {
            return BaselineBuild(null, Text.of("s_19d91ee5"))
        }

        // 皮温基线单独判条数：不够只是这个分量缺席，不拖累整张卡。
        // 取"本次配到的那次点检"**之前**的记录 —— 用之后的点检当基线等于把未来算进"平时"。
        val curTemp = pickTemp(temps, excludeAt)
        val grads = temps.asSequence()
            .filter { it.usable && it.measuredAt <= uptoMs }
            .filter { curTemp == null || it.measuredAt < curTemp.measuredAt }
            .sortedByDescending { it.measuredAt }
            .take(TEMP_BASELINE_SIZE)
            .map { it.gradientC }
            .toList()
        val gradMed = if (grads.size >= MIN_TEMP_BASELINE) {
            Hrv.baselineMedian(grads.map { it.toDouble() })?.toFloat()
        } else {
            null
        }

        return BaselineBuild(
            Baseline(
                count = past.size,
                spanMs = span,
                rmssdMed = rmssdMed,
                hrMed = hrMed,
                gradientMedC = gradMed,
                gradientCount = grads.size,
                unknownRestCount = past.count { it.rest == Hrv.RestState.UNKNOWN },
            ),
            null,
        )
    }

    /**
     * 与 [at] 时刻配对一次点检：取时间上最近的一次，且必须落在 [MAX_TEMP_AGE_MS] 内。
     *
     * **超过时限就不配**（返回 null），而不是"取最近的一条凑上"：
     * 用几小时前的皮温解释现在的心率，是把两个不同条件下的测量当成了一次。
     * 不参与只让皮温分量缺席，其余分量按权重归一 —— 少一个证据好过一个错证据。
     */
    fun pickTemp(temps: List<TempObs>, at: Long): TempObs? {
        var best: TempObs? = null
        var bestAbs = Long.MAX_VALUE
        for (t in temps) {
            if (!t.usable) continue
            val d = abs(t.measuredAt - at)
            if (d > MAX_TEMP_AGE_MS) continue
            val cur = best
            // **等距时取较新的那条**，不能留给列表顺序决定：
            // 库里的返回顺序不保证（DAO 没写 ORDER BY 时尤其如此），
            // 而"本次配到哪次点检"直接进分数 —— 顺序一变，同一份数据会算出两个分数。
            // 取较新的一条也更贴近本次测量的状态。
            val take = when {
                cur == null -> true
                d < bestAbs -> true
                d == bestAbs && t.measuredAt > cur.measuredAt -> true
                else -> false
            }
            if (take) {
                bestAbs = d
                best = t
            }
        }
        return best
    }

    // ═══════════════════════ 疲劳：双窗口趋势 ═══════════════════════

    /**
     * 疲劳（累积恢复不足）= **近期窗口相对更早窗口的漂移**。
     *
     * 为什么用两个窗口的中位数比，而不是"对全部历史做个线性回归"：
     * 回归会把顺序错位的记录（用户补测、时区问题）也当成趋势的一部分，
     * 而中位数之差只回答一个问题 —— "最近这段时间，比之前那段时间差了多少"。
     * 每个窗口 [TREND_WINDOW] 条，中位数的噪声被平滑掉约 √5 ≈ 2.2 倍，
     * 所以 [UNIT_TREND] 取 0.15（≈ 日间 CV 30% / 2.2）。
     */
    fun computeFatigue(history: List<Obs>, uptoMs: Long): Pair<Score?, String> {
        val sorted = history
            .asSequence()
            .filter { it.usable && it.measuredAt <= uptoMs }
            .sortedByDescending { it.measuredAt }
            .toList()

        val recent = sorted.take(TREND_WINDOW)
        val earlier = sorted.drop(TREND_WINDOW).take(TREND_WINDOW)

        if (recent.size < TREND_MIN_N || earlier.size < TREND_MIN_N) {
            return null to (
                Text.fmt("s_817dd14b", TREND_MIN_N) +
                    Text.fmt("s_54b70b5d", recent.size, earlier.size) +
                    Text.of("s_b199bd1f")
                )
        }

        val gap = medianAt(recent) - medianAt(earlier)
        if (gap < TREND_MIN_GAP_MS) {
            val days = gap / 86_400_000.0
            return null to (
                Text.fmt("s_e29fc615", fmt1(days)) +
                    Text.fmt("s_d216a0c6", TREND_MIN_GAP_MS / 86_400_000) +
                    Text.of("s_88e58e1d")
                )
        }

        val rRmssd = Hrv.baselineMedian(recent.map { it.rmssdMs }) ?: return null to Text.of("s_d3083c11")
        val eRmssd = Hrv.baselineMedian(earlier.map { it.rmssdMs }) ?: return null to Text.of("s_2f9c1094")
        val rHr = Hrv.baselineMedian(recent.map { it.hrMean }) ?: return null to Text.of("s_7377e23a")
        val eHr = Hrv.baselineMedian(earlier.map { it.hrMean }) ?: return null to Text.of("s_877710eb")
        if (eRmssd <= 0 || eHr <= 0) return null to Text.of("s_a5625475")

        val devRmssd = (eRmssd - rRmssd) / eRmssd / UNIT_TREND
        val devHr = (rHr - eHr) / eHr / UNIT_TREND
        val raw = listOf(
            Triple(K_TREND_HRV, W_TREND_HRV, devRmssd),
            Triple(Text.of("s_c759e8f9"), W_TREND_HR, devHr),
        )
        val value = combine(raw)
        val parts = raw.map { (name, w, dev) ->
            Part(
                name = name,
                weight = (w / raw.sumOf { (_, ww, _) -> ww.toDouble() }).toFloat(),
                currentText = if (name.contains("RMSSD")) "${Hrv.ms(rRmssd)} ms" else Text.fmt("s_d9208031", rHr.roundToInt()),
                baselineText = if (name.contains("RMSSD")) "${Hrv.ms(eRmssd)} ms" else Text.fmt("s_4cf063d4", eHr.roundToInt()),
                componentScore = scoreOf(dev),
                devUnits = dev,
            )
        }
        val days = gap / 86_400_000.0
        return Score(value.roundToInt(), levelOf(value), parts) to
            Text.fmt("s_68ea5192", recent.size, earlier.size, fmt1(days))
    }

    /** 一个窗口的"中位时刻"：按时间倒序后的中间元素的时刻。 */
    private fun medianAt(list: List<Obs>): Long {
        if (list.isEmpty()) return 0L
        return list[list.size / 2].measuredAt
    }

    // ═══════════════════════ 分数合成 ═══════════════════════

    /** 相对偏离 → 分量分（0~100，50 = 持平）。线性映射，可倒推。 */
    fun scoreOf(devUnits: Double): Int =
        (50.0 + BAND * devUnits).roundToInt().coerceIn(0, 100)

    /**
     * 按权重合成总分。**权重先归一化**：分量缺席（比如没有皮温）时，
     * 剩下那些按比例放大到总和为 1，而不是让总分被拉向 50（那样等于凭空加了个"正常"分量）。
     */
    fun combine(parts: List<Triple<String, Float, Double>>): Double {
        if (parts.isEmpty()) return 50.0
        val wSum = parts.sumOf { (_, w, _) -> w.toDouble() }
        if (wSum <= 0) return 50.0
        var acc = 0.0
        for ((_, w, dev) in parts) {
            acc += w * (50.0 + BAND * dev)
        }
        return (acc / wSum).coerceIn(0.0, 100.0)
    }

    // ═══════════════════════ 推给手表的摘要 ═══════════════════════

    /**
     * 推给手表的摘要还能算"新鲜"的时长：24 小时。
     *
     * 与其它推送（血氧/睡眠）不同，那份摘要**不会因为跨天就失效** ——
     * 它的失效来自"基线变了"，而一天之内多测几次是很常见的。
     * 取 24 小时是折中：短了会频繁显示"可能过时"，长了会让人拿昨天早上的分数当现在的状态。
     */
    const val SUMMARY_FRESH_MS = 24 * 60 * 60 * 1000L

    /** 摘要是否可能已经过时（超过 [SUMMARY_FRESH_MS]）。 */
    fun isStale(computedAt: Long, nowMs: Long): Boolean = nowMs - computedAt > SUMMARY_FRESH_MS

    /**
     * 时效文本：`刚刚` / `N 分钟前` / `N 小时前` / `N 天前`。
     *
     * 未来时刻（时钟被改过）显示"刚刚"而不是负数 —— 负数的时长在界面上没有任何意义，
     * 而"刚刚"至少不会让人以为算错了。
     */
    fun ageText(computedAt: Long, nowMs: Long): String {
        val d = nowMs - computedAt
        return when {
            d < 60_000L -> Text.of("s_002a5568")
            d < 3_600_000L -> Text.fmt("s_ec82e730", d / 60_000L)
            d < 86_400_000L -> Text.fmt("s_6a08d742", d / 3_600_000L)
            else -> Text.fmt("s_6da66927", d / 86_400_000L)
        }
    }

    // ═══════════════════════ 展示辅助 ═══════════════════════

    /** 一位小数（固定 Locale.US，免得某些地区用逗号当小数点）。 */
    fun fmt1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    /** 偏离量的展示：`+0.8 个单位` / `-0.3 个单位`。 */
    fun devText(units: Double): String =
        Text.fmt("s_76782b5c", if (units >= 0) "+" else "", fmt1(units))

    /** 分数 → 一行摘要（手表用）。 */
    fun summaryLine(stress: Score, fatigue: Score?): String = buildString {
        append(Text.fmt("s_31585db9", stress.value, levelShort(stress.level)))
        if (fatigue != null) append(Text.fmt("s_d8f57b8d", fatigue.value, levelShort(fatigue.level)))
        else append(Text.of("s_b983c14a"))
    }

    // ═══════════════════════ 文案常量（必须出现在界面上） ═══════════════════════

    val SCALE_NOTE: String get() =
        Text.of("s_29176bba") +
            Text.of("s_ea2a394b") +
            Text.of("s_5fbcb1c4")

    val WEIGHT_NOTE: String get() =
        Text.of("s_8e6f0199") +
            Text.of("s_76ec48cc") +
            Text.of("s_1c8de7dc")

    val BASELINE_NOTE: String get() =
        Text.of("s_36c9fb9f") +
            Text.of("s_18e5ec4c")

    val FATIGUE_NOTE: String get() =
        Text.of("s_60b82276") +
            Text.of("s_d15f6a55") +
            Text.of("s_3c79d300")

    val ACTIVITY_NOTE: String get() =
        Text.of("s_66a98cff") +
            Text.of("s_db4c78de")

    val DISCLAIMER: String get() = Disclaimer.stress(Text.lang)
}
