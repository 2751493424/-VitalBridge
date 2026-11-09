package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * 血压预估（方案 C，2026-10-02 用户拍板；设计与红线见项目设计说明）。
 *
 * ## 这是什么，不是什么
 *
 * **是**：用「一次袖套测量 + 同一次会话里采到的腕端信号」做**个人多元回归**，
 * 输出收缩压与舒张压大概在什么范围，以及读数序列自身的趋势。
 *
 * **不是**：一次测量。项目设计说明记录的反对理由仍然成立 ——
 * 标定消得掉**系统性偏移**，消不掉**每次测量的随机 PEP**；
 * 非侵入式血压的行业门槛（ISO 81030-2 / AAMI）是平均误差 ≤5 mmHg、标准差 ≤8 mmHg。
 * 所以本文件的**每个输出都带着自己的残差**，且区间半宽有下限（[MIN_HALF_WIDTH]）。
 *
 * ## 三条不可让步的红线（少一条就退回「只给趋势」）
 *
 * 1. **不给分级**：一律不给「正常/偏高/偏低」—— 分级是医学判断，不是统计输出。
 *    与 [Bp.pulsePressure] 同一态度（只作展示，不做判定）。
 * 2. **跨度不够就判「无预测力」**：标签全在正常范围时，模型学到的只是「这个人整体偏高」。
 *    用户采集指引里「尽量覆盖高低不同的血压区间」**不是建议，是这套算法能不能用的前提**。
 * 3. **数据不足一律拒答**：宁可不给区间，也不给一个看起来能用的窄区间。
 *
 * ## 为什么单独抽成纯函数对象
 *
 * 与 [Bp] / [Ptt] / [Temperature] 同款纪律：**纯 Kotlin、零 Android 引用**，
 * 好让离线校验台架直接编译它，把「样本不足却给了区间」「跨度不够却给了预测」
 * 这类错钉住 —— 它们在界面上**看起来完全正常**。
 */
object BpPredict {

    // ═══════════════════ 阈值（集中在这里，台架逐条断言） ═══════════════════

    /** 给出趋势方向所需的最少袖套读数。 */
    const val MIN_TREND_SAMPLES = 6

    /**
     * 参与回归所需的最少配对组数。
     *
     * ⚠ 从 6 提到 8：8 组是「截距 + 1 个特征」这个最小模型的合理下限。
     * 6 组时残差自由度只有 4，SD 估计噪声大到会**假装**很准。
     */
    const val MIN_PAIRED_GROUPS = 8

    /** SBP 所需跨越的跨度（mmHg）。低于它 = 模型没有预测力。 */
    const val MIN_SPAN_SYS_MMHG = 12

    /**
     * **校准周期（天）** —— 身体会变，旧样本会慢慢不再适用（2026-10-04）。
     *
     * ⚗ 取值依据：**对齐三星健康自家的血压校准规范** ——
     * 用户提供：「每 **28 天（4 周）** 需要重新校准一次以保证精度；
     * 每次重新校准至少需要 **3 次**袖带测量」。
     * 与 [CALIB_CYCLE_TARGET] 一起构成界面上「本轮校准 X/3」的口径。
     *
     * ⚠⚠ **2026-11-04 由 30 改回 28**：向导文案里要向用户写明天数
     *   （「每 N 天再补 3 组」），而那句文案取的是**这个常量**。
     *   原先取整成 30 是为了好记，但用户给定的话术是 28（= 三星的 4 周），
     *   于是"说明写 28、逻辑用 30"会差两天 —— 这类两份口径的账这个项目吃过太多次。
     *   ⇒ 回到规范原值 28，文案与判据从此只有一个来源。
     *
     * ⚠⚠ **但不能照搬三星的「3 次」当门槛** —— 两者校准的东西不是一回事：
     * 三星校的是**自家算法**（多半是偏移/增益这类少数参数），
     * 我们做的是**回归建模**。样本量需求差一个量级：
     *
     * 线性回归里留出第 i 组后重拟合，预测误差 = `残差 / (1 − h_ii)`，
     * 而杠杆值均值 `h ≈ (k+1)/n` ⇒ 误差放大倍数：
     *
     * | n | 放大倍数 |
     * |---|---|
     * | **3** | **3.00** |
     * | 6 | 1.50 |
     * | **8** | **1.33** |
     * | 12 | 1.20 |
     *
     * ⇒ 3 组时留下 1 组只剩 2 个点，直线完全由那 2 点决定，第 3 点几乎必然偏很多。
     *   另外 3 组还过不了 [MIN_SPAN_SYS_MMHG]（3 个点很难自然跨 12 mmHg）
     *   与残差自由度（只有 1，SD 估计不可靠）。
     * ⇒ 所以「3 次」在本文档里的语义是**每个周期建议补充的量**，
     *   **不是**冷启动门槛 —— 冷启动仍是 [MIN_PAIRED_GROUPS]。
     */
    const val CALIB_CYCLE_DAYS = 28

    /**
     * **每个校准周期建议补充的配对数**（对齐三星的「每次重新校准至少 3 次袖带测量」）。
     *
     * ⚠ 它是**建议节奏**，不是硬门槛：达不到照样出数（走 [Window] 的回退），
     * 达到则模型会跟随最近的身体状态（见 [window]）。
     */
    const val CALIB_CYCLE_TARGET = 3

    /**
     * DBP 所需跨越的跨度（mmHg）。
     *
     * 比 [MIN_SPAN_SYS_MMHG] **低 4**：舒张压的生理变异本来就比收缩压小
     * （一天内 10~15 mmHg vs 20~30 mmHg），用同一个阈值会把大多数人的 DBP
     * 一律判成「学不到东西」。⚠ 但 DBP 的**区间不许比 SBP 窄**（见 [Estimate.Ok]）。
     */
    const val MIN_SPAN_DIA_MMHG = 8

    /**
     * 区间半宽的**绝对下限**（mmHg）—— 只在残差**极小**时兜底。
     *
     * ## ⚗⚗⚗ 原来定 15，其实把两件不同的事混成了一个数（2026-10-03 定案）
     *
     * 我原本写 15 的理由是「行业门槛是平均误差 ≤5、标准差 ≤8，比它还窄等于宣称
     * 『这是一次测量』」。**那个理由成立，但 15 是个错的值** ——
     * 它把**合规声明**（不许暗示这是测量）写成了**毫米数**，
     * 于是它在**常态**下压着残差：12 组、1 特征时 `残差 × 1.095 × 2.0 ≤ 15`
     * 等价于 **残差 ≤ 6.85**，而用户实测残差只有 **4.1** ⇒
     * **半宽恒为 15，从不由残差决定** ⇒ 我以为的"残差主导"根本没发生。
     *
     * 数字对照（12 组 1 特征，自由度修正 1.095）：
     * | 残差 SD | 需要的半宽 | 实际显示 |
     * |---|---|---|
     * | 0.3（完美拟合） | 1 | 15 |
     * | 4.1（**用户实测**） | 9 | **15** |
     * | 6.8 | 15 | 15 |
     * | 10 | 22 | 22 |
     *
     * ⇒ 定案：**下限降到 8**（≈行业门槛 SD≤8，仍守着"不是测量"那条），
     *   让残差在 **4~7** 这个真实区间**真正主导**半宽 ——
     *   区间于是变成**有区分力**的判据，而不是恒等于 15 的摆设。
     */
    const val MIN_HALF_WIDTH = 8

    /**
     * 半宽 = 残差 SD × 本系数（**常态主导项**）。
     *
     * ⚗ 取 **2.0** 而不是原来的 1.5 —— 依据是**残差近似正态时 2σ 覆盖约 95%**：
     * 一个 95% 的区间若只取 1.5σ，实际覆盖只有 ~87%，**每 13 次就有一次落在区间外**，
     * 那不叫 95% 区间。
     *
     * ⚠ 与 [MIN_HALF_WIDTH] 的关系是 `max(下限, 本项)`：
     *   残差大时由残差说了算（自动放宽），残差小时由下限兜底（不许宣称是测量）。
     */
    const val HALF_WIDTH_SD_MULT = 2.0

    /** 残差标准差超过它 → 强制放宽到 [WIDE_HALF_WIDTH] 并降为低置信。 */
    const val MAX_RESIDUAL_SD = 12.0

    /** 低置信时用的半宽（比下限更宽一档）。 */
    const val WIDE_HALF_WIDTH = 20

    /**
     * 代入值的**域外容差**（2026-10-03）。
     *
     * ⚠⚠⚠ **三个实现版本都被台架否掉了，判据本身是这段代码里最难定的**：
     *
     * 1. **零容差**（`v < min || v > max`）⇒ 误伤 **8 条**既有断言。
     *    那些用例代入只超 2~21 ms，是域边缘的正常生理波动，不是外推。
     * 2. **纯比例**（0.25 × 域跨度）⇒ 又误伤 9 条。根因是**窄域会把容差压没**：
     *    台架里 HR/HRV 的域只有 11 ms 宽，19 ms 波动 = **1.73 倍跨度**；
     *    而你的病态案例（PAT 域 15 ms、超 68 ms）是 **7.55 倍**。
     *    两者之间**没有干净的倍数分界**（1.73 与 7.55 之间的任何阈值都随数据变）。
     *
     * ⇒ 定案：**绝对下限 + 比例，取两者较大者**。绝对下限认的是"测量本身能有多准"，
     *   比例认的是"这批样本覆盖了多宽" —— 两者缺一不可：
     *   - 绝对下限小 ⇒ 宽域（69 ms）也容得下 21 ms 的正常波动；
     *   - 比例项在窄域上**追加**收紧 ⇒ 15 ms 宽的域只多容 5 ms。
     *
     * ⚗ 依据（两个真实量级）：
     *   - 正常波动：超 2~21 ms，域跨度 50~70 ms ⇒ 应**放行**；
     *   - 你的那一次：超 68 ms，域跨度 15 ms ⇒ 应**拒答**。
     */
    const val DOMAIN_TOLERANCE_ABS = 25.0

    /** 比例容差（在 [DOMAIN_TOLERANCE_ABS] 之上**追加**收紧）。 */
    const val DOMAIN_TOLERANCE_REL = 0.35

    /** 趋势读数的最长回溯（天）。再久远的读数与当下的关系已经很弱。 */
    const val MAX_HISTORY_DAYS = 90

    /** 趋势「无变化」的判定阈值（mmHg）。小于它就是噪声。 */
    const val FLAT_DELTA_MMHG = 5

    /** 配对窗口半宽（毫秒）。采集侧用它取 PAT 与心率。 */
    const val PAIR_WINDOW_MS = 5 * 60_000L

    /**
     * 预测「**当前**值」时，取腕端输入的窗口宽度（毫秒）。
     *
     * ⚠ 与 [PAIR_WINDOW_MS] **刻意不同**：训练时窗口必须窄（要与袖套同时刻），
     *   而预测时是「最近一段时间的稳态」，窗口太窄会因一两分钟的抖动而失真。
     * 两个数混用会造成「训练与预测口径不一致」—— 那正是 2026-10-02
     * 「只显示趋势、永远出不来数值」的根因之一。
     */
    const val LATEST_WINDOW_MS = 30 * 60_000L

    /**
     * 腕端数据最多允许有多旧（毫秒）还拿来当「当前值」的特征。
     *
     * ⚠ 超龄就**整条拒答**（返回全空输入 → `Estimate.MissingInput`）。
     *   拿三小时前的 PAT 推「现在的血压」是在编数字，而且**看不出来** ——
     *   数字照常显示，只是它与当下无关。
     */
    const val LATEST_MAX_AGE_MS = 6 * 60 * 60_000L

    /** PAT 离散度上限（毫秒）。超过说明这次测量本身不稳，配出来的样本会污染整条拟合。 */
    const val MAX_PAT_SPREAD_MS = 15

    /**
     * 一个窗口里至少要有这么多条心率样本才算得出均值。
     *
     * ⚠⚠ **训练侧与预测侧共用这一个数**（[quality] 的门控、
     *   `BpCalibration.windowSignals` 的 `hrMinSamples` 默认值都指它）：
     *   一条心率算出来的"均值"方差极大，写进训练数据等于加噪声而不是加信息；
     *   而两侧用不同的门，就会出现「训练时有这一维、预测时没有」→ 永久拒答
     *   （2026-10-02「只显示趋势」的根因之一）。
     */
    const val MIN_HR_SAMPLES = 3

    /**
     * 每个回归参数至少要有多少组样本。
     *
     * ⚗ 特征数**按样本量自适应**：n 组最多用 `n / MIN_SAMPLES_PER_PARAM - 1` 个特征。
     * 样本少就只用 PAT，样本多了才逐步加进心率 / HRV / BMI ——
     * **宁可少用一个特征，也不要让模型参数比数据还多**（那不是个性化，是过拟合噪声）。
     */
    const val MIN_SAMPLES_PER_PARAM = 6

    /**
     * 岭正则系数（相对 `trace(XᵀX)/k`）。
     *
     * ⚠⚠ **不是为了「让数字好看」，是为了防共线性炸掉**：特征之间高度相关
     * （PAT 与心率通常同向变化）时，`XᵀX` 接近奇异，高斯消元会解出**极大且不稳定**的权重 ——
     * 换个样本就翻脸，而残差看起来仍然很小，**完全看不出来**。
     * 岭回归把共线方向上的权重压回去，预测值因此稳定。
     *
     * ⚠ 取值刻意**很小**（1e-3 相对量级）：只够把奇异抹平，几乎不引入偏差。
     * 真的存在无法解释的方差时，正确做法是**减少特征**（[chooseFeatures] 的截断），
     * 而不是靠加大 λ 掩盖。
     */
    const val RIDGE_REL = 1e-3

    // ═══════════════════ 输入 ═══════════════════

    /**
     * 一条袖套读数。
     *
     * @param atMs 测量时刻（不是录入时刻）。
     * @param sourceMarker 只用于台架与日志；**不参与算法**（来源不同不代表值不同）。
     */
    data class Reading(
        val atMs: Long,
        val systolic: Int,
        val diastolic: Int,
        val sourceMarker: String = "",
    )

    /**
     * 一组**校准样本**：一次袖套测量（= 标签）+ 同一次会话窗口里的腕端信号（= 特征）。
     *
     * ⚠ 腕端信号必须真的与这次袖套测量**同时段**（±[PAIR_WINDOW_MS]）。
     * 采集侧跨窗口借用会造出「看起来合法但量错了」的样本 ——
     * 同一类错见项目设计说明的 `rPeaks` 纪律。
     *
     * 特征**全部可空**：缺哪个就少用哪个特征，算法按实际可用的算，
     * 绝不拿 0 或均值去填补（填补会让模型学到假的关联）。
     */
    data class CalibSample(
        val atMs: Long,
        val systolic: Int,
        val diastolic: Int,
        val patMs: Int?,
        val hrMean: Int? = null,
        val hrvRmssdMs: Int? = null,
        val bmi: Double? = null,
        /**
         * 测量体位（`Bp.Posture`）。
         *
         * ⚠⚠ **它刻意不参与回归**（不是 [Feat] 的一员）—— 体位不是"特征"而是**分组键**：
         *   `PAT = PEP + PTT`，体位同时改 PEP 与 PTT，把它当特征会让模型把 PEP 的效应
         *   记成"血压的效应"，且 PEP 不可观测、换不回来。
         *   ⇒ 它的用途是**门控 / 分层**（筛样本、或按体组分组建模）。
         * ⚠ `null` = 老数据或用户没选，**不等于坐姿**。
         */
        val posture: String? = null,
    )

    /**
     * 要估计「**当前**值」时，腕端最新一次的输入。
     *
     * 缺某个模型用到的特征就**不能代入**（见 [Estimate.MissingInput]）——
     * 那时宁可不给数字，也不要用旧值或均值顶替。
     */
    data class LatestInputs(
        val patMs: Int? = null,
        val hrMean: Int? = null,
        val hrvRmssdMs: Int? = null,
        val bmi: Double? = null,
        /**
         * ⚗ **本次心电测得的搏数**（次/分），**只用于界面显示，不是回归目标**。
         *
         * ## 为什么刻意不把它做成第三个拟合目标（2026-10-03 用户问过）
         *
         * 用户问「能不能把预估的脉搏也加上」。查完发现：搏数**心电本来就直接测到**
         * （就是 [hrMean] 那一路 R 峰计数），而 [hrMean] 本身**已经是模型的输入特征**。
         * ⇒ 拿一个已知的量当输入去预测与它几乎同一个量，得到的**是它的复制品** ——
         *   等于「用 79 去估 79」，绕一圈回到已知值，还额外引入了误差。
         *
         * ⇒ 所以正确做法是**显示测量值**，不预测。语义也不同：
         *   预估值带区间（因为它是推断），测量值没有区间（它就是测出来的）。
         *
         * ⚠ 与 [hrMean] 的区别：那个是**窗口内多条记录的均值**（可能跨 ±30 分钟），
         *   这个是**本次心电那一段**的搏数（与那条 PAT 同一时刻）。
         */
        val measuredPulse: Int? = null,
    )

    // ═══════════════════ 输出 ═══════════════════

    /** 趋势方向。⚠ **不是**「偏高/偏低」——那需要基线判定，而基线因人而异到无法给阈值。 */
    enum class Direction { UP, DOWN, FLAT }

    sealed interface Confidence {
        /** 差几条。界面要说「还差 N 次」并给行动入口，而不是只说「数据不足」。 */
        data class Insufficient(val have: Int, val need: Int) : Confidence
        data object Low : Confidence
        data object Medium : Confidence
    }

    sealed interface Trend {

        /**
         * 读数不够。**不给方向、不给区间。**
         *
         * ⚠ 这里是「拒答」而不是「给个大概」的地方：样本 5 条时任何方向都是噪声。
         */
        data class Insufficient(val confidence: Confidence.Insufficient) : Trend

        data class Ok(
            val direction: Direction,
            /** 近窗的稳健中心（收缩压，mmHg）。 */
            val medianSystolic: Int,
            /** 近窗相对**自身全部读数基线**的偏离（mmHg，负=低于基线）。 */
            val deltaVsBaseline: Int,
            /** 区间半宽（mmHg，**保证 ≥ [MIN_HALF_WIDTH]**）。 */
            val halfWidth: Int,
            val samples: Int,
            val confidence: Confidence,
        ) : Trend
    }

    // ═══════════ 个人基线对比（**替代「正常/偏高」分级**） ═══════════

    /**
     * 本次预估值相对**用户自己的基线**的位置。
     *
     * ## ⚗⚗ 为什么是它，而不是「正常 / 偏高 / 偏低」
     *
     * 用户 2026-10-03 明确要求「加一个显示结果的，比如预估结果为高压」。
     * **本项目拒绝**，理由不是保守，是**技术上做不到**：
     *
     * 1. **医学诊断阈值本身没有唯一答案**。同一个收缩压 112：
     *    国内指南 <140 算正常、ACC/AHA 2017 的 130 就算 1 级高血压。
     *    ⇒ 输出「高压/正常」等于**替用户采用某一套标准**，
     *      而用户在买手机上的 App 时并没有选过标准。
     * 2. **分级是诊断，不是统计**。本 App 的定位是「血压预估参考」。
     * 3. 更实际的问题：**预测本身有 ±9 mmHg 的区间**。
     *    在 130 附近给「高血压」、在 120 附近给「正常」——
     *    这两个结论的差别可能**完全落在区间内**，即**模型根本没有能力区分它们**。
     *    给出分级会让用户以为这个区分是测量出来的。
     *
     * ## 那能给什么
     *
     * 相对**用户自己**的基线。这是**自比较**，不需要任何外部标准，
     * 也不会因为换了标准而失效。实测场景：今天比平时高 8 mmHg ——
     * 这对用户是有意义的信息，而「是否高血压」需要他在医院才能知道。
     *
     * ## ⚠ 基线只取**配对样本**（不是全部读数）
     *
     * 配对样本都是「袖套 + 同时段心电」的静息态测量，天然同质。
     * 而全部读数里可能混了**运动后、紧张时**的值 ⇒ 会把基线拉偏，
     * 于是「今天比平时高」变成「今天比那次跑步后高」——那不是用户想看的对比。
     */
    data class BaselineCompare(
        /** 预估值相对基线的偏离（mmHg，正 = 高于自己的基线）。 */
        val deltaMmHg: Int,
        /** 基线（配对样本收缩压中位数，mmHg）。 */
        val baselineSystolic: Int,
        /** 基线用了多少组。 */
        val groups: Int,
        /**
         * ⚗ 这个偏离**是否值得说**（mmHg）。
         *
         * ⚠⚠ **小于它就不给结论** —— 偏离必须**大于噪声**才有意义。
         * 这里用预估的**区间半宽**做门槛：若 |偏离| ≤ 半宽，
         * 那「比平时高」和「比平时低」在测量误差内**分不出来**，
         * 说出来会让用户以为这个区分是真实存在的。
         */
        val meaningfulIfExceedsMmHg: Int,
    ) {
        /** 偏离够不够大到能说。 */
        val isMeaningful: Boolean get() = kotlin.math.abs(deltaMmHg) > meaningfulIfExceedsMmHg
    }

    /**
     * 把预估值与用户自己的基线比。
     *
     * @param estimate 必须是 [Estimate.Ok]（其他分支没有可比的数值）。
     * @param samples **配对样本**（基线的来源，见 [BaselineCompare] 的说明）。
     * @return 配对样本不够时不返回（`null`）—— **没有基线就没有对比**，不拿别的东西凑。
     */
    fun compareToBaseline(estimate: Estimate.Ok?, samples: List<CalibSample>): BaselineCompare? {
        val ok = estimate ?: return null
        // ⚠ 只收**收缩压在合法值域**的样本（越界的丢掉而不是夹进来，理由同 [trend]）。
        val sys = samples.map { it.systolic }
            .filter { it in Bp.MIN_SYSTOLIC..Bp.MAX_SYSTOLIC }
        if (sys.size < MIN_PAIRED_GROUPS) return null
        return BaselineCompare(
            deltaMmHg = ok.systolic - median(sys),
            baselineSystolic = median(sys),
            groups = sys.size,
            // ⚗ 门槛用**收缩压自己的**半宽（DBP 的不许参与判断 ——
            //   那是另一个量纲，混进来会让门槛无意义）。
            meaningfulIfExceedsMmHg = ok.systolicHalfWidth,
        )
    }

    // ═══════════ 准确度自评（留一交叉验证） ═══════════

    /**
     * 一次留一交叉验证里的一组（**留出的那一组**）。
     *
     * [predictedSys] 为 `null` = 那一组被拒答（域外 / 缺特征）。
     * ⚠ **拒答不是误差**，不该按 0 或"无穷大"计进 ME/SD —— 但要**单独统计**，
     *   因为拒答率直接等于"实际用起来有多少次拿不到数"。
     */
    data class Fold(
        val atMs: Long,
        val actualSys: Int,
        val actualDia: Int,
        val predictedSys: Int? = null,
        val predictedDia: Int? = null,
        val halfWidthSys: Int? = null,
        val halfWidthDia: Int? = null,
        /** 拒答原因（域外 / 缺特征 / 训练集无预测力），给界面说清"为什么这次没有数"。 */
        val reason: String? = null,
    ) {
        /** 收缩压误差（预测 − 实测）。`null` = 该组拒答。 */
        val errorSys: Int? get() = predictedSys?.let { it - actualSys }
        val errorDia: Int? get() = predictedDia?.let { it - actualDia }
    }

    /**
     * **留一交叉验证**的结果 —— 这是唯一对"预估值准不准"有意义的答案。
     *
     * ## ⚗⚗⚗ 为什么必须有它：训练集上的残差是**自欺欺人**的
     *
     * 用户 2026-10-04 问「该怎么测试血压预估准不准」。
     * 此前几次"实测偏差只有 ±3"**全是训练集上的数字** ——
     * 他每次量完血压就录入校准，**那条数据立刻进了训练集**。
     * 模型"见过"这条，残差当然小。**这等于考前看过答案。**
     *
     * 留一交叉验证的做法：**每次留出 1 组**，用其余 N−1 组重新拟合，
     * 然后拿这个模型去预测**那一组它没见过**的数据。N 组就得到 N 个
     * **完全样本外**的预测，再统计误差。这是现有数据能给出的最强证据。
     *
     * ## ⚠ 它**不是**临床验证 —— 措辞必须守住这条线
     *
     * 留一交叉验证回答的是「**在这个人自己的数据上，模型有没有过拟合**」，
     * **不回答**「这台设备能不能替代血压计」。后者需要的独立人群、
     * 标准袖套、盲法、以及监管层面的验证 —— 本 App 结构性做不到。
     * ⇒ 界面上这个区块的标题只能写「**校准质量自评**」之类的措辞，
     *   **不许**出现「准确度」「精度」这种会被读成临床指标的词，
     *   更不许给「合格/不合格」的判断。
     */
    data class Accuracy(
        /** 纳入评估的组数（已按值域过滤）。 */
        val groups: Int,
        /** 成功给出**样本外**预测的组数。 */
        val predicted: Int,
        /** 被拒答的组数（域外 / 缺特征）。 */
        val rejected: Int,
        /** 收缩压平均误差（预测 − 实测；**正 = 估高了**）。 */
        val meanErrorSys: Double,
        /** 收缩压误差的标准差（**离散度**；越小越稳）。 */
        val sdErrorSys: Double,
        val meanErrorDia: Double,
        val sdErrorDia: Double,
        /** 收缩压**平均绝对误差**（不看方向，只看差多少）。 */
        val maeSys: Double,
        val maeDia: Double,
        /** 实测值落在自己的区间内的组数（覆盖率分子）。 */
        val withinSys: Int,
        val withinDia: Int,
        val folds: List<Fold>,
    ) {
        /** 拒答率（0~1）。⚠ 它和误差**同等重要** —— 拿不到数也是一种"不准"。 */
        val rejectionRate: Double get() = if (groups == 0) 0.0 else rejected.toDouble() / groups

        /**
         * 区间覆盖率（0~1）。
         *
         * ⚗⚗ **这是最能暴露"区间是否诚实"的指标**：半宽是按 2σ 算的
         * （理论覆盖 ≈95%）。若实测覆盖率远低于 95% ⇒ **区间偏窄、低估了真实误差**；
         * 若远高于 95%（比如 100%）⇒ 区间偏宽、没有区分力。
         * 用户 v97 之前就是这样：半宽恒为 15、覆盖率恒为 100%。
         */
        val coverageSys: Double get() = if (predicted == 0) 0.0 else withinSys.toDouble() / predicted
        val coverageDia: Double get() = if (predicted == 0) 0.0 else withinDia.toDouble() / predicted
    }

    /**
     * 对现有校准样本做**留一交叉验证**，得到样本外误差。
     *
     * ## 实现要点
     *
     * - **每组留出后都重新拟合**（不复用全量拟合的结果）——
     *   复用就等于没留出，那正是要避免的错。
     * - **拒答如实统计**：留出 1 组后，剩余样本的特征域会变窄，
     *   留出那组**可能因此落到域外**。这是**正确行为**（域判据在起作用），
     *   不是 bug ⇒ 记进 [Accuracy.rejected]，**不许**为了"能算出数"而放宽判据。
     * - ⚠ 误差统计的**分母是 [Accuracy.predicted]**（不是 `groups`）：
     *   把拒答当成 0 误差会**让数字变好看**，那是自欺欺人的另一种写法。
     *
     * @return 样本不足以做留出（需要 `> MIN_PAIRED_GROUPS`）时返回 `null`。
     */
    fun crossValidate(samples: List<CalibSample>): Accuracy? {
        val usable = samples
            .filter { it.systolic in Bp.MIN_SYSTOLIC..Bp.MAX_SYSTOLIC }
            .filter { it.diastolic in Bp.MIN_DIASTOLIC..Bp.MAX_DIASTOLIC }
        // ⚠ 留出 1 组后剩 `size-1` 组要给拟合用 ⇒ 至少要比样本门槛多 1 组，
        //   否则每一折都会因"样本不够"而失败，得到的是"全拒答"的假结果。
        if (usable.size <= MIN_PAIRED_GROUPS) return null

        val folds = usable.mapIndexed { i, held ->
            val rest = usable.filterIndexed { j, _ -> j != i }
            // 把留出那组当作"最新一次输入"（与真实使用路径同一套代入逻辑）
            val input = LatestInputs(
                patMs = held.patMs,
                hrMean = held.hrMean,
                hrvRmssdMs = held.hrvRmssdMs,
                bmi = held.bmi,
            )
            when (val e = estimate(rest, input)) {
                is Estimate.Ok -> Fold(
                    atMs = held.atMs, actualSys = held.systolic, actualDia = held.diastolic,
                    predictedSys = e.systolic, predictedDia = e.diastolic,
                    halfWidthSys = e.systolicHalfWidth, halfWidthDia = e.diastolicHalfWidth,
                )
                is Estimate.OutOfDomain -> Fold(
                    held.atMs, held.systolic, held.diastolic,
                    reason = "域外：" + e.features.joinToString("/"),
                )
                is Estimate.MissingInput -> Fold(
                    held.atMs, held.systolic, held.diastolic,
                    reason = "缺特征：" + e.features.joinToString("/"),
                )
                is Estimate.NotEnoughPairs -> Fold(
                    held.atMs, held.systolic, held.diastolic, reason = "剩余样本不足",
                )
                // ⚠ `NoPredictivePower` 是**整张表**的属性（跨度不够），
                //   留一之后更可能触发 ⇒ 每一折都会落到这里。这不代表模型坏了，
                //   而是"样本太少、跨度不够支撑评估" ⇒ 如实记下。
                is Estimate.NoPredictivePower -> Fold(
                    held.atMs, held.systolic, held.diastolic, reason = "跨度不足，学不到关系",
                )
            }
        }

        val done = folds.filter { it.predictedSys != null }
        if (done.isEmpty()) {
            // 一组都没预测出来 ⇒ 仍返回结果（让界面能说"全部拒答"），但不编造误差。
            return Accuracy(
                groups = folds.size, predicted = 0, rejected = folds.size,
                meanErrorSys = 0.0, sdErrorSys = 0.0, meanErrorDia = 0.0, sdErrorDia = 0.0,
                maeSys = 0.0, maeDia = 0.0, withinSys = 0, withinDia = 0, folds = folds,
            )
        }

        val es = done.map { it.errorSys!!.toDouble() }
        val ed = done.map { it.errorDia!!.toDouble() }
        fun mean(v: List<Double>) = v.sum() / v.size
        fun sd(v: List<Double>): Double {
            if (v.size < 2) return 0.0
            val m = mean(v)
            // ⚠ 除 `n-1`（样本标准差）而不是 `n` —— 我们在**估计**总体的离散度，
            //   用 n 会系统性偏小（正是"报喜不报忧"那类错，与 v99 修的那个同源）。
            return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
        }
        return Accuracy(
            groups = folds.size,
            predicted = done.size,
            rejected = folds.size - done.size,
            meanErrorSys = mean(es),
            sdErrorSys = sd(es),
            meanErrorDia = mean(ed),
            sdErrorDia = sd(ed),
            maeSys = mean(es.map { abs(it) }),
            maeDia = mean(ed.map { abs(it) }),
            // ⚠ 写成中间变量：`-it.halfWidthSys!!..it.halfWidthSys!!` 让一元负号
            //   与 `!!` / `..` 三个运算符挤在一行，解析结果不直观也难 review。
            withinSys = done.count { f ->
                val h = f.halfWidthSys!!
                f.errorSys!! in -h..h
            },
            withinDia = done.count { f ->
                val h = f.halfWidthDia!!
                f.errorDia!! in -h..h
            },
            folds = folds,
        )
    }

    sealed interface Estimate {

        /** 配对组数不够。**不给任何数字。** */
        data class NotEnoughPairs(val confidence: Confidence.Insufficient) : Estimate

        /**
         * 有配对，但**模型没有预测力**（读数跨度太窄，或特征完全没有变化）。
         *
         * ⚠ 这一档最容易被糊弄过去：样本数和拟合优度看起来都很好，
         * 但只要收缩压跨度 < [MIN_SPAN_SYS_MMHG]，模型学到的只是「这个人整体偏高」。
         */
        data class NoPredictivePower(
            val groups: Int,
            val systolicSpanMmHg: Int,
            val diastolicSpanMmHg: Int,
            val needSysMmHg: Int,
            val needDiaMmHg: Int,
        ) : Estimate

        /** 最新一次会话缺了模型用到的特征，**代入不了回归式**。 */
        data class MissingInput(val features: List<String>) : Estimate

        /**
         * ⚠⚗⚗ **代入值落在训练域之外** —— 这次不给任何数字。
         *
         * 2026-10-03 用户实测：12 组样本的 PAT 只覆盖 211~226 ms，而某次心电的 PAT
         * 在窗口之外 ⇒ 外推出 137 mmHg，袖套实测 116（**偏 +21**）。反推要得到 137
         * 需代入 PAT ≈ 143 ms —— 比他任何一次正常录制都低 47 ms，**那是直线延伸出来的数**。
         *
         * ⇒ 宁可不给。外推段的误差不是"更不准"，是**没有依据**。
         *
         * @param features 出界的特征名。
         * @param lowsMs `(特征名, 低于**可接受下界**多少)`。
         * @param highsMs `(特征名, 高于**可接受上界**多少)`。
         * @param acceptable 每条出界特征的可接受区间（`[下界, 上界]`，已含容差）——
         *   ⚠ 界面**必须**用它说清"该往哪边补样本"，只说"超出范围"用户无从下手。
         */
        data class OutOfDomain(
            val features: List<String>,
            val lowsMs: List<Pair<String, Double>>,
            val highsMs: List<Pair<String, Double>>,
            val acceptable: List<Pair<String, Pair<Double, Double>>>,
        ) : Estimate

        data class Ok(
            /** 收缩压估计（mmHg，已夹进 [Bp.MIN_SYSTOLIC]..[Bp.MAX_SYSTOLIC]）。 */
            val systolic: Int,
            val systolicHalfWidth: Int,
            val systolicResidualSdMmHg: Double,
            /** 舒张压估计（mmHg，已夹进 [Bp.MIN_DIASTOLIC]..[Bp.MAX_DIASTOLIC]）。 */
            val diastolic: Int,
            val diastolicHalfWidth: Int,
            val diastolicResidualSdMmHg: Double,
            val groups: Int,
            /** 实际用到的特征个数（⚠ 界面上要报出来：它决定了这个估计有多可信）。 */
            val featuresUsed: Int,
            val systolicSpanMmHg: Int,
            val diastolicSpanMmHg: Int,
            val confidence: Confidence,
            /**
             * ⚗ **训练样本的体位一致性**（2026-10-05 加）。
             *
             * ## 为什么要报这个、却**不用它门控**
             *
             * 体位是**已知的偏倚来源**（见 [CalibSample.posture] 的推导：坐↔站 PAT 差 21 ms，
             * 那是"体位效应"被记成"血压效应"），所以必须让用户知道它混没混。
             *
             * 但**不能据此拒答或分层**：
             * - **门控**（只留单一体位的样本）：本项目的冷启动门槛是
             *   [MIN_PAIRED_GROUPS] = 8 组，而用户当前样本远不到这个量级 ——
             *   再按体位切一刀，每一组都不够 8 组 ⇒ **永久拒答**，
             *   比"给一个带提示的数"更糟（用户连趋势都看不到）。
             * - **加特征**：明确禁止（PEP 不可观测、效应换不回来）。
             *
             * ⇒ 唯一正确的落点就是**如实告知**：把"这批样本里体位混了没有"说出来，
             *   让用户自己决定要不要按同一体位重测。这与项目里
             *   「校准质量自评」那条措辞红线同源 —— **给事实，不给结论**。
             */
            val postureConsistency: PostureConsistency = PostureConsistency.UNRECORDED,
            /**
             * ⚗ **本次心电测得的搏数**（次/分）—— **测量值，不是预估值**。
             *
             * ⚠ 界面**必须**与「收缩压/舒张压预估参考」**区分措辞**：
             *   预估值带区间（它是推断出来的），这个没有（它就是测出来的）。
             *   混在同一句式里会让用户以为搏数也是估的。
             *
             * 详见 [LatestInputs.measuredPulse] 里「为什么不预测搏数」的推导。
             */
            val measuredPulse: Int? = null,
        ) : Estimate
    }

    /**
     * 训练样本的**体位一致性**（不是"预估值准不准"的结论，只是一条事实）。
     *
     * ⚠ 命名刻意用 `Consistency` 而不是 `Quality`：后者会被读成"合格/不合格"，
     *   而这四档**没有好坏**（[SINGLE] 甚至是最好的情况），只是"能不能按体位解释差异"。
     */
    enum class PostureConsistency {
        /**
         * 训练样本里**全部**记了同一个体位 ⇒ 体位不可能造成偏倚。
         *
         * ⚗ 这是最理想的一档，但**不代表预估值更准**（样本量、跨度那些门槛照旧）。
         */
        SINGLE,

        /**
         * 训练样本里**混了多个体位** ⇒ 体位效应可能被记成血压效应。
         *
         * ⚠ 这是唯一需要提醒用户重测的一档。**但仍给出预估值**（见 [Ok.postureConsistency]）。
         */
        MIXED,

        /**
         * 训练样本**全都是"未记录"** ⇒ 不知道该不该担心。
         *
         * ⚠ 与 [MIXED] 区分开：那一个是"知道混了"，这一个是"不知道"。
         *   合成一档会让用户以为"没记录=混了"，而他其实可能一直坐姿测的。
         */
        UNRECORDED,

        /**
         * 部分记了、部分没记 ⇒ **无法判断一致性**。
         *
         * ⚗ 刻意不把它归进 [MIXED]：那几个没记的可能是同一个体位，
         *   贸然说"混了"是**替用户下了他没有依据的结论**。
         */
        UNKNOWN,
    }

    // ═══════════════════ 趋势（不依赖配对，任何时候都能算） ═══════════════════

    /**
     * 读数序列的趋势与区间。
     *
     * @param nowMs 计算时刻；未来时刻的读数会被剔除（时钟回拨时不该用）。
     */
    fun trend(readings: List<Reading>, nowMs: Long): Trend {
        val usable = readings
            .filter { it.atMs <= nowMs && it.atMs >= nowMs - MAX_HISTORY_DAYS * 86_400_000L }
            // ⚠ 越界的值**丢掉**而不是夹进来（与 Ptt 同一纪律）：夹进来会造出一个
            //   不存在的读数，而血压值域宽（50~300），夹错一次就是几十 mmHg。
            .filter { it.systolic in Bp.MIN_SYSTOLIC..Bp.MAX_SYSTOLIC }
            .sortedBy { it.atMs }
        if (usable.size < MIN_TREND_SAMPLES) {
            return Trend.Insufficient(
                Confidence.Insufficient(usable.size, MIN_TREND_SAMPLES),
            )
        }

        val systolic = usable.map { it.systolic }
        val baseline = median(systolic)
        // ⚠⚠ 「近窗」必须按**条数**取最近 1/3，不能按「最近 N 天」——
        //   全部读数都在 30 天内时（绝大多数用户最初几个月都是这样），
        //   按时间切窗会让「近窗」与「基线」**完全相同**，delta 恒为 0 → 永远判「持平」。
        //   这个错是 bpcheck 第 7 节抓出来的（2026-10-02），界面上的表现是
        //   「读数明明在涨，趋势却一直说平稳」。
        val recentN = max(MIN_TREND_SAMPLES / 2, usable.size / 3)
        val window = usable.takeLast(recentN).map { it.systolic }
        val local = median(window)

        // ⚠ 基线取**全部读数**的中位数，所以「早年跳变一次、之后一直平稳」会读成「持平」。
        //   这是**有意的**：用户问的是「现在相对自己的常态有没有变」，不是「跟去年比」。
        //   台架第 7 节把这条行为钉死了，别后来的人当成 bug 改掉。
        val delta = local - baseline
        val direction = when {
            abs(delta) < FLAT_DELTA_MMHG -> Direction.FLAT
            delta > 0 -> Direction.UP
            else -> Direction.DOWN
        }

        // 区间用 **MAD**（中位绝对偏差）而不是标准差：一次录错 180 的读数
        // 会把标准差拉爆、把区间撑成无用，而 MAD 对离群几乎免疫。
        val mad = median(systolic.map { abs(it - baseline) })
        val halfWidth = max(MIN_HALF_WIDTH, ceil(mad * 1.4826 * 1.5).toInt())

        val lowConfidence = usable.size < MIN_TREND_SAMPLES * 2 ||
            window.size < MIN_TREND_SAMPLES / 2
        return Trend.Ok(
            direction = direction,
            medianSystolic = local,
            deltaVsBaseline = delta,
            halfWidth = halfWidth,
            samples = usable.size,
            confidence = if (lowConfidence) Confidence.Low else Confidence.Medium,
        )
    }

    // ═══════════════════ 绝对估计（双目标多元回归） ═══════════════════

    /**
     * 由校准样本给出**收缩压 + 舒张压**估计。
     *
     * ## ⚠⚠ 它按**传入的全部样本**拟合 —— 应用层必须先用 [window] 选样本
     *
     * 2026-10-04 起，拟合样本要按**新鲜度**筛过（见 [Window]：身体会变，
     * 老样本该淡出，否则用户每周期补的 3 组只会稀释在几十条旧数据里，
     * 「定期校准」变成空话）。
     *
     * ⇒ **应用层的正确调用形状是 `estimate(window(samples, now).fit, latest)`**，
     *   `nowMs <= 0` 时 [window] 会原样返回全部样本（台架与离线复算路径）。
     *
     * ⚠ 这里**刻意不做窗口化**（也没有 `nowMs` 参数）：本函数不知道"现在几点"，
     *   而台架的上百条断言用的都是**合成时间戳**，若默认窗口化会被成批判成
     *   "样本不足" ⇒ **静默失去意义**。把时间判定留在调用点，是**故意的**。
     *
     * ## 模型
     *
     * 对 SBP 与 DBP **各拟合一次**多元线性回归（正规方程 + 高斯消元）：
     * `目标 = 截距 + Σ wᵢ·(特征ᵢ − 特征均值)`。
     *
     * ⚠ **两个目标各带各的残差与区间** —— DBP 的误差通常大于 SBP，区间通常更宽。
     * ⚠ **绝不许把 DBP 的区间画得比 SBP 窄**：那会让界面看起来「舒张压测得更准」，
     * 而那不成立（见 [Estimate.Ok.diastolicHalfWidth]）。
     *
     * ## 特征怎么选
     *
     * 可用特征 = `[PAT, 心率, HRV RMSSD, BMI]` 中**每条样本都有值**的那些，
     * 再去掉方差为 0 的（常量特征会让正规方程奇异）。
     * ⚗ 然后按样本量**截断**到 `n / [MIN_SAMPLES_PER_PARAM] - 1` 个 ——
     * 宁可少用一个特征，也不要让参数比数据还多。
     */
    fun estimate(samples: List<CalibSample>, latest: LatestInputs): Estimate {
        val usable = samples
            .filter { it.systolic in Bp.MIN_SYSTOLIC..Bp.MAX_SYSTOLIC }
            .filter { it.diastolic in Bp.MIN_DIASTOLIC..Bp.MAX_DIASTOLIC }
            .filter { it.patMs == null || it.patMs in Ptt.PAT_MIN_MS..Ptt.PAT_MAX_MS }
            .sortedBy { it.atMs }
        if (usable.size < MIN_PAIRED_GROUPS) {
            return Estimate.NotEnoughPairs(
                Confidence.Insufficient(usable.size, MIN_PAIRED_GROUPS),
            )
        }

        val sysSpan = usable.maxOf { it.systolic } - usable.minOf { it.systolic }
        val diaSpan = usable.maxOf { it.diastolic } - usable.minOf { it.diastolic }
        // ⚠ 两个目标的跨度**分开判**：舒张压的生理变异本来就小，用同一个阈值
        //   会把大多数人的 DBP 一律判成「学不到东西」。
        if (sysSpan < MIN_SPAN_SYS_MMHG || diaSpan < MIN_SPAN_DIA_MMHG) {
            return Estimate.NoPredictivePower(
                groups = usable.size,
                systolicSpanMmHg = sysSpan,
                diastolicSpanMmHg = diaSpan,
                needSysMmHg = MIN_SPAN_SYS_MMHG,
                needDiaMmHg = MIN_SPAN_DIA_MMHG,
            )
        }

        val chosen = chooseFeatures(usable)
        if (chosen.isEmpty()) {
            // 所有特征都没变化 —— 与「跨度不足」是同一类：模型没有预测力。
            return Estimate.NoPredictivePower(
                usable.size, sysSpan, diaSpan, MIN_SPAN_SYS_MMHG, MIN_SPAN_DIA_MMHG,
            )
        }

        // ── 代入前先确认「最新输入」够不够 ──
        val missing = chosen.filter { f -> f.ofLatest(latest) == null }
        if (missing.isNotEmpty()) {
            return Estimate.MissingInput(missing.map { it.label })
        }

        val xs = usable.map { s -> chosen.map { f -> f.of(s)!!.toDouble() } }
        val xNew = chosen.map { f -> f.ofLatest(latest)!!.toDouble() }

        // ⚗⚗⚗ **代入值必须落在训练域内，否则一律拒答**（2026-10-03 加）
        //
        // ## 真实案例
        //
        // 用户 12 组配对样本的 PAT 覆盖只有 **211~226 ms（跨度 15 ms）**，
        // 另一次心电的 PAT 落在窗口之外 → 代入时是**纯外推** →
        // 算出 **137 mmHg**，而袖套实测 **116 mmHg**（偏 +21）。
        // 反推可知要让模型给出 137，代入的 PAT 得是 **143 ms** ——
        // 比他**任何一次**正常质量的录制（最低 190 ms）都低 47 ms，
        // **那不是测量能到的位置，是直线被延伸出来的**。
        //
        // ## 为什么必须拒答而不是"给个宽区间"
        //
        // 外推段的误差不是"更不准"，而是**没有依据** —— 那里的斜率完全由
        // 训练域内那 15 ms 决定，而那 15 ms 本身就只是噪声。
        // 再宽的区间也掩盖不了"这个数是编的"这件事。
        //
        // ## ⚗ 容差口径见 [DOMAIN_TOLERANCE_ABS]（零容差与纯比例都试过，都误伤）
        val outside = chosen.indices.filter { j ->
            val tol = domainTolerance(xs, j)
            val v = xNew[j]
            v < colMin(xs, j) - tol || v > colMax(xs, j) + tol
        }
        if (outside.isNotEmpty()) {
            return Estimate.OutOfDomain(
                features = outside.map { chosen[it].label },
                // ⚠ 报**超出容差多少**，界面才能说清"你这次的 PAT 特别短/长"。
                //   只说"超出范围"用户不知道该往哪边补样本。
                //   ⚠ 减掉容差：报的是"超出**可接受范围**多少"，不是"超出训练域多少" ——
                //   后者在容差很大的宽域上会报出一个吓人的数字，而那其实还在可接受范围内。
                lowsMs = outside.map { j ->
                    chosen[j].label to
                        (colMin(xs, j) - domainTolerance(xs, j) - xNew[j])
                },
                highsMs = outside.map { j ->
                    chosen[j].label to
                        (xNew[j] - colMax(xs, j) - domainTolerance(xs, j))
                },
                acceptable = outside.map { j ->
                    chosen[j].label to
                        (colMin(xs, j) - domainTolerance(xs, j) to colMax(xs, j) + domainTolerance(xs, j))
                },
            )
        }

        val sys = fit(xs, usable.map { it.systolic }, xNew) ?: return Estimate.NoPredictivePower(
            usable.size, sysSpan, diaSpan, MIN_SPAN_SYS_MMHG, MIN_SPAN_DIA_MMHG,
        )
        val dia = fit(xs, usable.map { it.diastolic }, xNew) ?: return Estimate.NoPredictivePower(
            usable.size, sysSpan, diaSpan, MIN_SPAN_SYS_MMHG, MIN_SPAN_DIA_MMHG,
        )

        val wide = sys.residualSd > MAX_RESIDUAL_SD || dia.residualSd > MAX_RESIDUAL_SD        // ⚗⚗ 区间半宽**必须按自由度放大**，否则系统性偏窄（2026-10-03 加）
        //
        // ## 为什么
        //
        // 残差 SD 是在**同一批数据**上拟合出来的，参数本身是从这批数据里学到的
        // ⇒ 它天然偏乐观。12 组 1 维时自由度只有 10，偏乐观的幅度不小。
        //
        // ## 真实代价（用户 2026-10-03 实测）
        //
        // 袖套实测 116，界面显示 `137 ±15` ⇒ **偏 +21，落在区间之外**。
        // 也就是说那个 ±15 **连真实误差都盖不住** —— 一个"报喜不报忧"的区间
        // 比没有区间更危险：它让人以为"差 21 属于合理误差"，于是**不��去复核**。
        //
        // ⚗ 修正用 `√(n/(n−k−1))`（残差方差的自由度修正）：
        //   12 组 1 维 → `√(12/10) = 1.095`，看起来小；但真实误差是 21，
        //   说明**问题不主要在自由度，而在训练域太窄**（见域外判据）。
        //   两处一起做：① 域外拒答 ② 区间按自由度放大。
        fun width(sd: Double): Int {
            val dof = (usable.size - chosen.size - 1).coerceAtLeast(1)
            val corrected = sd * sqrt(usable.size.toDouble() / dof)
            // ⚗⚗ **残差项现在是常态主导**（系数见 [HALF_WIDTH_SD_MULT] = 2.0）。
            // 原来用 1.5 且下限 15 是常态值 ⇒ 用户实测偏差仅 +3/−1 时区间仍显示 ±15，
            // 宽了一倍多，「落在区间内」几乎恒真 ⇒ 那个判据没有区分力了。
            //   现在的形态：残差小 → 由 [MIN_HALF_WIDTH] 兜底（不许宣称是测量）；
            //   残差大 → 由残差说了算（自动放宽，且 2σ ≈ 95% 覆盖）。
            return max(
                if (wide) WIDE_HALF_WIDTH else MIN_HALF_WIDTH,
                ceil(corrected * HALF_WIDTH_SD_MULT).toInt(),
            )
        }
        val sysWidth = width(sys.residualSd)
        return Estimate.Ok(
            systolic = sys.value.roundToInt().coerceIn(Bp.MIN_SYSTOLIC, Bp.MAX_SYSTOLIC),
            systolicHalfWidth = sysWidth,
            systolicResidualSdMmHg = sys.residualSd,
            diastolic = dia.value.roundToInt().coerceIn(Bp.MIN_DIASTOLIC, Bp.MAX_DIASTOLIC),
            // ⚠ DBP 的区间**不许比 SBP 窄** —— 那会让界面看起来「舒张压测得更准」。
            diastolicHalfWidth = max(width(dia.residualSd), sysWidth),
            diastolicResidualSdMmHg = dia.residualSd,
            groups = usable.size,
            featuresUsed = chosen.size,
            systolicSpanMmHg = sysSpan,
            diastolicSpanMmHg = diaSpan,
            confidence = if (wide) Confidence.Low else Confidence.Medium,
            postureConsistency = postureConsistencyOf(usable),
            // ⚗ 测量值原样带出（不是预测、不夹值域）：心电测到多少就是多少。
            //   ⚠ 刻意**不给它兜一个默认值** —— 缺失时界面就当"没测到"不显示，
            //   补一个 0 或训练均值会让用户以为表上那个数是量出来的。
            measuredPulse = latest.measuredPulse,
        )
    }

    /**
     * 判断这批训练样本的**体位一致性**（只报事实，不做门控 —— 理由见 [Estimate.Ok.postureConsistency]）。
     *
     * ⚠ 四档的判据**必须互斥且穷尽**，而且 `UNRECORDED` 与 `UNKNOWN` 不能合并：
     * 前者是"一个都没记"（可能是好事 —— 用户一直坐姿测、只是懒得记），
     * 后者是"记了一半"（**真的无法判断**）。合成一档会让前者被误读成"有问题"。
     *
     * ⚠ 这里**只用 `usable`（已过值域过滤的那批）**，而不是原始 `samples`：
     * 被剔除的样本不参与拟合，它们的体位也就不该影响"训练集体位一致性"这个判断。
     */
    private fun postureConsistencyOf(usable: List<CalibSample>): PostureConsistency {
        if (usable.isEmpty()) return PostureConsistency.UNKNOWN
        val recorded = usable.mapNotNull { it.posture }.distinct()
        val missing = usable.count { it.posture == null }
        return when {
            // 一条都没记 ⇒ 不知道（**不是**"混了"）。
            recorded.isEmpty() -> PostureConsistency.UNRECORDED
            // 记了一部分、其余没记 ⇒ 无法判断。
            missing > 0 -> PostureConsistency.UNKNOWN
            // 全记了，且只有一个取值 ⇒ 体位不可能造成偏倚。
            recorded.size == 1 -> PostureConsistency.SINGLE
            // 全记了，且有多个取值 ⇒ 混了，需要提醒。
            else -> PostureConsistency.MIXED
        }
    }

    /**
     * **预估历史** —— 历史上每一次心电，按**当前**校准模型重算出的估计。
     *
     * ## ⚗⚗ 为什么是「重算」而不是「存快照」
     *
     * 用户要的是「每次测心电都留一条可以回看的记录」。最直接的实现是加一张表把
     * 估计值存下来 —— **但那会造出一个没法回答的问题**：改了算法之后，
     * 历史里 early 那几条是旧模型算的、后面几条是新模型算的，
     * **两者混在同一个列表里，界面无法区分，用户也无从知道**。
     *
     * 而存快照**没有任何优势**：估计的输入（PAT / 心率 / HRV）本来就**全部在
     * 心电记录里**，任何时候都能重放。于是「存下来」唯一的实际效果就是把
     * 一个纯函数的结果抄一份，并给未来的自己埋一个"这些数怎么来的"的坑。
     *
     * ⇒ **不落库**（沿用派生量不落库纪律）：输入是历史的一部分，随时可重放。
     * ⇒ 改算法后**全历史一起变**，且每一行都是当前模型下的口径。
     *
     * ## 逐行独立判定
     *
     * ⚠ **每一行单独调 [estimate]**，任何一行失败（那次心电缺特征）都**只影响那一行**。
     *   不能"整体失败就不给" —— 那样用户会看到一张空表，
     *   而事实是「有 5 次能算、2 次不能算」，那 5 条正是他要看的。
     *
     * ## ⚠ 不在这里做「过期」判断
     *
     * 6 小时那条限制（[LATEST_MAX_AGE_MS]）属于 **latestInputs**，即"用最近那次
     * 心电算**现在**的血压"。历史每条记录的是**它自己那一刻**的输入 ——
     * 三周前的心电无论多旧都该显示，因为用户回看的就是三周前。
     * 拿 LatestInputs 那条 6 小时门限去砍历史，会把整张表清空。
     */
    fun history(
        samples: List<CalibSample>,
        /** 每一次心电：(时刻, 该时刻的输入)。调用方负责按时刻倒序。 */
        rows: List<Pair<Long, LatestInputs>>,
    ): List<HistoryRow> = rows.map { (atMs, inputs) ->
        val e = estimate(samples, inputs)
        HistoryRow(
            atMs = atMs,
            // ⚠ 只留 `Ok`。`NotEnoughPairs` / `NoPredictivePower` 是**整张表**的属性
            //   （对所有行给出同一个答案），重复显示 N 遍纯噪声。
            estimate = (e as? Estimate.Ok),
            missing = (e as? Estimate.MissingInput)?.features ?: emptyList(),
            // ⚗⚗ **域外要单独标出来**，不能与「缺特征」混成一片。
            //   两者界面要给的指引完全相反：
            //   - 缺特征 → "去手表测一次心电"（**立刻能做**）；
            //   - 域外   → "这个状态下才测过 N 次"（要攒样本，不是重测一次的事）。
            //   混起来会让用户反复重测却永远不出数（实测：他的 137 那几行全是域外）。
            outOfDomain = (e as? Estimate.OutOfDomain),
        )
    }

    /** 预估历史的一行。[estimate] 为 `null` 时读 [missing] / [outOfDomain] 说明原因。 */
    data class HistoryRow(
        val atMs: Long,
        val estimate: Estimate.Ok?,
        val missing: List<String> = emptyList(),
        /** 非 `null` = 这次因**训练域外**而不给数值（与 [missing] 是不同的原因）。 */
        val outOfDomain: Estimate.OutOfDomain? = null,
    )

    /**
     * 选特征：全员有值 → 去掉常量 → 按样本量截断。
     *
     * ⚠ 顺序**不能换**：先按样本量截断再剔除常量，可能截完只剩一个常量特征，
     * 而常量特征留着只会让正规方程奇异。
     */
    private fun chooseFeatures(samples: List<CalibSample>): List<Feat> {
        val candidates = Feat.entries.filter { f -> samples.all { f.of(it) != null } }
        val maxFeatures = max(1, samples.size / MIN_SAMPLES_PER_PARAM - 1)
        val out = ArrayList<Feat>()
        for (f in candidates) {
            if (out.size >= maxFeatures) break
            val col = samples.map { f.of(it)!!.toDouble() }
            val m = col.average()
            // ⚠ 方差为 0 的特征**丢掉**：它对预测没有贡献，却会让正规方程奇异
            //   （高斯消元遇到 0 主元 → 除零 → NaN 悄悄传播成一个看起来正常的数字）。
            if (col.any { abs(it - m) > 1e-9 }) out.add(f)
        }
        return out
    }

    /** 一个候选特征。取值一律返回**可空**：缺值不许填补。 */
    private enum class Feat(val label: String) {
        PAT("PAT") {
            override fun of(s: CalibSample) = s.patMs
            override fun ofLatest(l: LatestInputs) = l.patMs
        },
        HR("心率") {
            override fun of(s: CalibSample) = s.hrMean
            override fun ofLatest(l: LatestInputs) = l.hrMean
        },
        HRV("HRV") {
            override fun of(s: CalibSample) = s.hrvRmssdMs
            override fun ofLatest(l: LatestInputs) = l.hrvRmssdMs
        },
        BMI("BMI") {
            override fun of(s: CalibSample) = s.bmi?.toInt()
            override fun ofLatest(l: LatestInputs) = l.bmi?.toInt()
        },
        ;

        abstract fun of(s: CalibSample): Number?
        abstract fun ofLatest(l: LatestInputs): Number?
    }

    private class Fit(val value: Double, val residualSd: Double)

    /**
     * 一次多元最小二乘拟合（正规方程 + 高斯消元）。
     *
     * ## ⚗⚗⚗ 中心化：**矩阵、残差、代入三者必须用同一套坐标**
     *
     * 这里的模型是 `ŷ = ȳ + Σ wⱼ·(xⱼ − x̄ⱼ)`，也就是**截距由 [yMean] 承担**。
     * 要让这个式子成立，`XᵀX` 与 `Xᵀ(y−ȳ)` 都必须由**中心化后**的 X 构造。
     *
     * ### 曾经的 bug（2026-10-03 由 bpcheck 第 9 节抓出）
     *
     * 原来建矩阵用的是**未中心化**的 `xs`，而代入时用 `(xNew − means)` ——
     * **两套坐标**。后果：解出的 `w` 会去吸收那个本该由截距承担的均值项，
     * 斜率被压到原来的 **1/50**（0.799 → 0.0159）。于是在一份完美线性的合成数据上，
     * 代入 PAT=206 期望 144.6，实际返回 **135** —— 恰好是训练集的 SBP 均值，
     * **与输入完全无关**。而残差 SD 仍然很小（因为拟合得很好），
     * **界面上看不出任何异常**：区间又窄、又有数值、还会随样本变。
     *
     * ⇒ 这类错有一个共同特征：**输出「看起来正常」，只有拿已知答案的数据代进去才发现不对**。
     *   所以凡是写完"代入"类算法，都要用一份**手算期望值**的合成数据回归一遍
     *   （`bpcheck` 第 9 节的 ③ 就是这条断言）。
     *
     * @return `null` = 正规方程奇异。调用方据此拒答，**绝不返回一个 NaN 的估计值** ——
     *   NaN 会一路传到界面上变成「NaN mmHg」，而那比不给更糟。
     */
    private fun fit(xs: List<List<Double>>, ys: List<Int>, xNew: List<Double>): Fit? {
        val n = xs.size
        val k = xs[0].size
        val means = DoubleArray(k) { j -> xs.map { it[j] }.average() }
        val yMean = ys.average()

        // ⚗⚗ **必须先中心化再建方程**（2026-10-03 修，见下方「中心化」注释）。
        val xc = Array(n) { i -> DoubleArray(k) { j -> xs[i][j] - means[j] } }
        val xn = DoubleArray(k) { j -> xNew[j] - means[j] }

        // 正规方程：XᵀX · w = Xᵀ(y − ȳ)。X 已中心化，截距项由 yMean 承担。
        val a = Array(k) { i -> DoubleArray(k) { j -> xc.sumOf { it[i] * it[j] } } }
        // 岭正则：给对角加一点点，见 [RIDGE_REL] 的说明（共线性会让解爆掉）
        val lambda = RIDGE_REL * (0 until k).sumOf { a[it][it] } / k
        for (j in 0 until k) a[j][j] += lambda
        val rhs = DoubleArray(k) { j ->
            var acc = 0.0
            for (i in 0 until n) acc += xc[i][j] * (ys[i] - yMean)
            acc
        }
        val w = solve(a, rhs) ?: return null

        var value = yMean
        for (j in 0 until k) value += w[j] * xn[j]
        var ss = 0.0
        for (i in 0 until n) {
            var pred = yMean
            // ⚠ 用**同一个** `xc`：残差必须与拟合、代入三者同口径（见下方「中心化」注释）
            for (j in 0 until k) pred += w[j] * xc[i][j]
            val e = ys[i] - pred
            ss += e * e
        }
        val sd = sqrt(ss / max(1, n - k - 1))
        // ⚠⚠ **非有限值一律拒答**：NaN / Inf 一路传到界面上会显示成「NaN mmHg」，
        //   而那比不给更糟 —— 用户会以为程序坏了，或者更糟：以为那是个数。
        if (!value.isFinite() || !sd.isFinite()) return null
        return Fit(value, sd)
    }

    /**
     * 高斯消元（部分主元）。
     *
     * @return `null` 表示矩阵奇异 —— 调用方据此拒答。
     */
    private fun solve(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val n = b.size
        val m = Array(n) { i -> a[i].copyOf() + b[i] }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (abs(m[r][col]) > abs(m[pivot][col])) pivot = r
            if (abs(m[pivot][col]) < 1e-12) return null
            val t = m[col]; m[col] = m[pivot]; m[pivot] = t
            for (r in 0 until n) {
                if (r == col) continue
                val f = m[r][col] / m[col][col]
                for (c in col..n) m[r][c] -= f * m[col][c]
            }
        }
        return DoubleArray(n) { m[it][n] / m[it][it] }
    }

    // ═══════════════════ 采集侧（配对窗口） ═══════════════════

    /**
     * 配对质量档。
     *
     * ⚠ [Usable] 不等于「测得好」：它只表示**技术门控通过**（PAT 离散度够小）。
     * 界面上「黄金」那一档还要看用户是否勾选了「安静状态测量」。
     */
    enum class Quality { Unusable, Usable, Gold }

    /**
     * 判定一次配对的质量。
     *
     * @param quiet 用户是否声明「安静状态下测量」。
     * @return [Quality.Unusable] 时**不写库** —— 宁可少一条样本，不要一条被污染的。
     */
    fun quality(patMs: Int?, patSpreadMs: Int?, hrSamples: Int, quiet: Boolean): Quality {
        if (patMs == null || patSpreadMs == null) return Quality.Unusable
        if (patMs !in Ptt.PAT_MIN_MS..Ptt.PAT_MAX_MS) return Quality.Unusable
        if (patSpreadMs > MAX_PAT_SPREAD_MS) return Quality.Unusable
        if (hrSamples < MIN_HR_SAMPLES) return Quality.Unusable
        return if (quiet) Quality.Gold else Quality.Usable
    }

    // ═══════════════════ 校准进度（界面用） ═══════════════════

    data class Progress(
        /** 本轮（最近一个 [CALIB_CYCLE_DAYS] 周期内）新增的配对数。 */
        val pairsInCycle: Int,
        /** 本轮建议补充量 = [CALIB_CYCLE_TARGET]。⚠ 是**节奏**，不是门槛。 */
        val needPairs: Int,
        /** 累计可用配对数。⚠ 这个才是**门槛**的分母。 */
        val totalPairs: Int,
        /** 拟合门槛 = [MIN_PAIRED_GROUPS]。 */
        val needTotal: Int,
    ) {
        /** 本轮的节奏达成（♻ 保持校准，但不影响"能不能出数"）。 */
        val cycleDone: Boolean get() = pairsInCycle >= needPairs
        /** **能不能拟合** —— 界面上"有没有预估值"由它决定。 */
        val enoughToFit: Boolean get() = totalPairs >= needTotal
    }

    /**
     * **用于拟合的样本窗口**（2026-10-04）—— 让「每周期校准」真正起作用。
     *
     * ## ⚗⚗⚗ 为什么需要它
     *
     * 用户此前看到的界面是「**本月**校准样本 3 / 8」，而 `estimate` 用的是**累计全部**样本
     * —— **两个口径不一致**，于是他以为"每月要凑 8 组"，而实际预估早就在正常工作。
     * （那四个字段里 `readingsThisMonth`/`needReadings`/`readingsDone`/`pairsDone`
     * 经查**一处都没被引用**，是死字段。）
     *
     * ## ⚠ 但改动不止是"改文案"：样本不新鲜，校准就没有意义
     *
     * 若拟合一直用**全部**样本，那么用户每 30 天新补的 3 组只会**稀释**在几十条老数据里，
     * 模型几乎不动 —— 「定期校准」变成一句空话。
     * ⇒ 所以拟合优先用**周期内**的样本，让新样本真正说话。
     *
     * ## 选择规则（两步，第二步是保底）
     *
     * 1. **优先**取最近 [CALIB_CYCLE_DAYS] 天内的样本；够 [MIN_PAIRED_GROUPS] 组就用这些。
     * 2. 不够则**向前扩展**，按时间倒序取最近的 [MIN_PAIRED_GROUPS] 组。
     *    ⚠ 这一步是**必需的**：否则用户忘了补样本就"点开没有数"，
     *      而他并没有做错什么（[CALIB_CYCLE_DAYS] 只是**建议节奏**，不是硬性失效）。
     *      ⇒ 退回旧样本**照常给数**，但由 [stale] 如实告诉他样本可能已过时。
     *
     * @param samples 全部可用配对样本。
     * @param nowMs 当前时刻（判定新鲜度用）。`<= 0` = 不做窗口化（**台架与离线复算**用，
     *   那些场景的合成数据时间戳不满足真实周期，窗口化会让它们全部退化）。
     */
    data class Window(
        /** 实际用于拟合的样本（已按时间升序）。 */
        val fit: List<CalibSample>,
        /** 其中落在 [CALIB_CYCLE_DAYS] 周期内的条数。 */
        val freshCount: Int,
        /** 全部可用配对数。 */
        val totalCount: Int,
        /** 最新一条样本距 [nowMs] 多少天（无样本时 `-1`）。 */
        val latestAgeDays: Int,
    ) {
        /** 样本是否已超过一个校准周期（界面据此提示"可能不再适用"，但**照旧给数**）。 */
        val stale: Boolean get() = latestAgeDays > CALIB_CYCLE_DAYS
        /**
         * 是否走了**回退**（周期内样本不够，被迫用了更早的）。
         * ⚠ 界面要能说清这件事 —— 否则用户不知道"我的新样本其实没被用上"。
         */
        val fellBack: Boolean get() = freshCount < MIN_PAIRED_GROUPS && totalCount > freshCount
    }

    /**
     * 按新鲜度选出拟合样本。规则见 [Window]。
     *
     * ⚠ 返回值里的 [Window.fit] 可能**少于** `samples.size`（旧的被丢掉）——
     * 这正是"让新样本生效"的实现方式。
     */
    fun window(samples: List<CalibSample>, nowMs: Long): Window {
        val asc = samples.sortedBy { it.atMs }
        val latest = asc.lastOrNull()?.atMs
        val ageDays = if (latest == null || nowMs <= 0L) -1
        else ((nowMs - latest) / 86_400_000L).toInt()
        if (nowMs <= 0L) {
            // 不做窗口化：全部样本参与（台架/离线复算路径）。
            return Window(asc, asc.size, asc.size, -1)
        }
        val cutoff = nowMs - CALIB_CYCLE_DAYS * 86_400_000L
        val fresh = asc.filter { it.atMs >= cutoff }
        // ① 周期内够用 ⇒ 只用这些（老样本自然淡出，模型跟随身体变化）
        if (fresh.size >= MIN_PAIRED_GROUPS) {
            return Window(fresh, fresh.size, asc.size, ageDays)
        }
        // ② 不够 ⇒ 回退到"最近 N 组"（按时间倒序取，再转回升序）。
        //   ⚠ 取最近 `MIN_PAIRED_GROUPS` 组而不是"全部"：宁可只退一点，
        //     不要把几十条远古数据全拉回来（那等于没做窗口化）。
        val fallback = asc.takeLast(MIN_PAIRED_GROUPS)
        return Window(fallback, fresh.size, asc.size, ageDays)
    }

    /**
     * 校准进度（2026-10-04 重做）。
     *
     * ## ⚗ 改了什么、为什么
     *
     * 原来只有「**本月**校准样本 X / 8」一个数，而 8 是**累计**门槛
     * ⇒ 两个口径打架（用户因此以为"每月必须 8 组"）。
     *
     * ⇒ 现在把两件事**分开命名**：
     *   - [pairsInCycle] / [needPairs]：**本轮（一个校准周期内）补充了几组**，
     *     目标 [CALIB_CYCLE_TARGET]（3 组，对齐三星规范）—— 这是**节奏**；
     *   - [totalPairs] / [needTotal]：**累计够不够拟合** —— 这是**门槛**。
     *
     * ⚠ 删掉了四个死字段（`readingsThisMonth` / `needReadings` / `readingsDone` / `pairsDone`）
     *   —— 经查一处引用都没有。留着只会让下一个人以为它们有意义。
     */
    fun progress(samples: List<CalibSample>, nowMs: Long): Progress {
        val cutoff = nowMs - CALIB_CYCLE_DAYS * 86_400_000L
        return Progress(
            pairsInCycle = if (nowMs <= 0L) samples.size else samples.count { it.atMs >= cutoff },
            needPairs = CALIB_CYCLE_TARGET,
            totalPairs = samples.size,
            needTotal = MIN_PAIRED_GROUPS,
        )
    }

    // ═══════════════════ 内部 ═══════════════════

    /**
     * **校准覆盖情况** —— 供界面提示「还缺哪一侧」（2026-10-03）。
     *
     * ## ⚠ 数据纠正了一个想当然的结论
     *
     * 我本来想提示「缺 PAT 较低/较高的一侧」。拿用户真实数据一算，**那个说法是错的**：
     * 他的 15 份心电录制 PAT 跨 148~235 ms，而 12 组配对样本只覆盖 217~226 ms ——
     * **低侧有 10 份、高侧有 2 份**。两侧都大量存在，只是**没与袖套血压同时段**。
     *
     * ⇒ 所以缺口不是"某一侧没有数据"，而是「**有数据但没配上对**」。
     *   提示必须这么说，否则用户会去"在更低的 PAT 时测"——而他已经测过了。
     *
     * @param unmatchedOutside 落在 [patLowMs, patHighMs] 之外的**未配对**心电数。
     *   ⚠ 这是"有多少现成数据可用"的量，不是"缺多少"的量 —— 方向正好相反，
     *   界面的措辞必须跟着它走。
     */
    data class Coverage(
        val patLowMs: Int,
        val patHighMs: Int,
        val groups: Int,
        val unmatchedBelow: Int,
        val unmatchedAbove: Int,
    ) {
        val unmatchedTotal: Int get() = unmatchedBelow + unmatchedAbove
    }

    /**
     * 算覆盖情况。[samples] 取自配对样本，**必须与 [estimate] 用的是同一份**
     * （否则界面显示的域与实际判定用的域会不一致，而用户无法发现）。
     *
     * @param unmatched 未配对心电的 **PAT 列表**（只有没配上血压的那些）。
     *   ⚠ 传 PAT 而不是时刻：判断"落在域外"需要的是 PAT 本身。
     */
    fun coverage(samples: List<CalibSample>, unmatchedPat: List<Int>): Coverage? {
        val pats = samples.mapNotNull { it.patMs }
        if (pats.size < MIN_PAIRED_GROUPS) return null
        val lo = pats.min()
        val hi = pats.max()
        return Coverage(
            patLowMs = lo,
            patHighMs = hi,
            groups = pats.size,
            // ⚠ 闭区间：[lo, hi] 内算已覆盖。与 [estimate] 的域判据用同一个边界口径，
            //   否则同一个 PAT 会在"提示"与"判定"里落到不同侧。
            unmatchedBelow = unmatchedPat.count { it < lo },
            unmatchedAbove = unmatchedPat.count { it > hi },
        )
    }

    /** 中位数。偶数个取中间两个的均值（不需要插值库，且这个精度够用）。 */
    private fun median(values: List<Int>): Int {
        if (values.isEmpty()) return 0
        val s = values.sorted()
        val mid = s.size / 2
        return if (s.size % 2 == 1) s[mid] else ((s[mid - 1].toLong() + s[mid].toLong()) / 2L).toInt()
    }

    /** 第 [j] 维在训练集里的最小值（域外判据用）。 */
    private fun colMin(xs: List<List<Double>>, j: Int): Double = xs.minOf { it[j] }

    /** 第 [j] 维在训练集里的最大值（域外判据用）。 */
    private fun colMax(xs: List<List<Double>>, j: Int): Double = xs.maxOf { it[j] }

    /**
     * 第 [j] 维的域外容差。**三处必须共用这一个函数** ——
     * 判据与报数若各算一遍，两者会漂移（而漂移表现为"界面说超出 3 ms、实际拒了 68 ms"）。
     *
     * 口径见 [DOMAIN_TOLERANCE_ABS]：绝对下限与比例**取较大者**。
     */
    private fun domainTolerance(xs: List<List<Double>>, j: Int): Double {
        val span = (colMax(xs, j) - colMin(xs, j)).coerceAtLeast(0.0)
        return max(DOMAIN_TOLERANCE_ABS, span * DOMAIN_TOLERANCE_REL)
    }
}
