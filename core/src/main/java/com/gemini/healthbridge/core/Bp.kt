package com.gemini.healthbridge.core

/**
 * 手动录入血压的取值域与校验。
 *
 * ## 为什么单独抽成纯函数对象
 *
 * 与 [Temperature] / `Spo2` / [Backfill] 是同一条纪律：**纯 Kotlin、无 Android 引用**，
 * 好让离线校验台架直接编译它、把"录反了"这类错钉住 —— 那种错
 * **编译、运行、肉眼检查都发现不了**（`120/80` 与 `80/120` 都是合理的两位数）。
 *
 * ## ⚠ 为什么硬拒只有一条
 *
 * 这是**用户自己输入的**值，不是传感器读数。两者的纪律**方向不同**：
 *
 * | | 传感器读数可疑时 | 用户输入可疑时 |
 * |---|---|---|
 * | 做法 | **一律拒答**（给错值比不给更糟，与 PPG 脉率同源） | **只提醒，不拦** |
 *
 * 理由：一个真正异常的血压是**可能存在的**，我们把它拦掉，等于"功能在对它最有用的时候
 * 拒绝工作"。而录进来的就是用户自己血压计上显示的读数，原样保存才是诚实的。
 *
 * 所以：
 *  · **硬拒**：只有"定义上不可能"的那一种 —— 收缩压 ≤ 舒张压（袖套读数的收缩压按定义更高，
 *    出现就是填反了）。
 *  · **提示**：区间外的值给一句话，但**照样允许保存**。
 */
object Bp {

    /**
     * 收缩压的可接受区间（mmHg）。
     *
     * 上下界取得**很宽**（50~300）是刻意的：这不是"正常血压范围"，而是"没输错"的范围。
     * 家用袖套能测到的极端值（严重高血压、休克）本来就在这个区间内，
     * 把区间收窄成"正常范围"会让真实读数存不进去。
     */
    const val MIN_SYSTOLIC = 50
    const val MAX_SYSTOLIC = 300

    /** 舒张压的可接受区间（mmHg）。理由同上。 */
    const val MIN_DIASTOLIC = 30
    const val MAX_DIASTOLIC = 200

    /** 脉搏的可接受区间（次/分）。可空 —— 很多血压计不报脉搏。 */
    const val MIN_PULSE = 20
    const val MAX_PULSE = 250

    /** 脉压（收缩压 − 舒张压）。**只作展示**，不做任何判定。 */
    fun pulsePressure(systolic: Int, diastolic: Int): Int = systolic - diastolic

    // ═══════════════════ 测量姿势（2026-10-03 新增） ═══════════════════

    /**
     * 测血压时的**体位**。
     *
     * ## 为什么必须记（2026-10-03 真机对照实验，15 次）
     *
     * 同一人、**手腕与心脏高度一致**（水静压这个混淆变量已被用户确认排除），只换体位：
     *
     * | 体位 | PAT 中位 | 组内极差 | 逐拍离散度 |
     * |---|---|---|---|
     * | 坐姿 | 216 ms | 8 ms | 3~7 ms |
     * | 站立 | 195 ms | 7 ms | 4~6 ms |
     * | 平躺 | 203 ms（1 次被拒答） | **87 ms** | 10~27 ms |
     *
     * 坐↔站差 **21 ms**，而两组的逐拍离散度只有 3~7 ms
     * ⇒ **体位效应远大于测量噪声**，它是「PAT 与血压关系时变」里最大的一块。
     *
     * ## ⚠⚠ 但它**不能当特征**去"修正"
     *
     * `PAT = PEP + PTT`。体位改变交感张力时 **PEP 与 PTT 同时变**，而 PEP 在两种信号里
     * **都不可观测**。模型若把体位当输入，学到的系数会把"PEP 变短"记成"血压升高"，
     * 而且**换不回来**（与那条红线是同一件事）。
     *
     * ⇒ 正解是**门控 / 分层**：按体组分组建模，或只收标准体位（[SITTING]）的样本。
     *   所以这个字段的用途是**筛选**，不是**特征**。
     *
     * ## ⚠ 姿势是「过期即永失」的输入
     *
     * 用户事后不会记得当时是怎么坐的 ⇒ 哪怕分层算法还没做，也必须**先落库**
     * （与 `sleep_odi` / `sleep_nights` 同一条纪律：只读得到"最近一次"的输入必须存下来）。
     *
     * ⚠ 值用**字符串常量**而不是 enum：它要落库、要进 JSON、要跨版本读回
     * （与 [com.gemini.healthbridge.core.Activity.Level] 同一条理由）。
     */
    object Posture {

        /** 坐姿。**这是我们引导的标准体位** —— 采集文案只推这一个。 */
        const val SITTING = "sitting"

        /**
         * 平躺。
         *
         * ⚠ 实测这一组**最难测**：R 峰检出从 35 一路掉到 15、离散度 10~27 ms（坐/站的 3~4 倍），
         *   还有 1 次因离散度超限被算法正确拒答。判据是**检出率下降**——
         *   体位效应本身不会让检出率掉，掉说明手指接触在变差。
         */
        const val LYING = "lying"

        /** 站立。 */
        const val STANDING = "standing"

        /**
         * 没记录（老数据、或用户没选）。
         *
         * ⚠ **必须与三种真实体位分开** —— 把"没记"当成"坐姿"会污染分层：
         *   分层的前提是"这一组的体位确定是同一个"，未知混进去就破坏了前提。
         */
        const val UNKNOWN = "unknown"

        /** 界面上真正可选的三种（不含 [UNKNOWN]）。顺序即界面顺序。 */
        val SELECTABLE = listOf(SITTING, LYING, STANDING)

        /**
         * 是不是我们引导的标准体位。
         *
         * 给将来的**门控**用：只收标准体位的样本，比"用体位当特征"安全得多
         * （理由见类注释）。⚠ 现在**还没有任何调用方** —— 校准样本刚起步，
         * 门控要等样本量够、能看出分层有没有用时再上。
         */
        fun isStandard(posture: String?): Boolean = posture == SITTING
    }

    /**
     * 校验结果。做成 sealed 而不是"布尔 + 另给一个原因函数"：
     * **判定与原因必须是同一次计算**（与 [Temperature.CoreResult] 同款）。
     */
    sealed interface Check {

        /**
         * 可以保存。
         *
         * [warning] 非空时界面显示一句提醒，但**不阻断保存** ——
         * 界面的保存按钮不能因为 warning 而禁用（那是把提醒变成了门禁）。
         */
        data class Ok(val warning: String? = null) : Check

        /** 不能保存。[reason] 是直接给用户看的一句话。 */
        data class Invalid(val reason: String) : Check
    }

    /**
     * 校验一次手动录入。
     *
     * @return [Check.Ok]（可能带提醒）或 [Check.Invalid]，绝不为 null 之外的第三种形态。
     */
    fun validate(systolic: Int?, diastolic: Int?, pulse: Int?): Check {
        if (systolic == null || diastolic == null) {
            return Check.Invalid(Text.of("s_0f089134"))
        }
        if (systolic <= 0 || diastolic <= 0) {
            return Check.Invalid(Text.of("s_13178dc3"))
        }

        // ⚠⚠ 唯一的一处硬拒，也是这个文件存在的主要理由。
        //    录反了是最常见的一种手误，而且它**看起来完全正常** ——
        //    `120/80` 与 `80/120` 都是合理的两位数，存进去之后没有任何下游能发现。
        //    这里不做"自动纠正成高/低"：那是在替用户改数据，宁可让他自己看一眼。
        if (systolic <= diastolic) {
            return Check.Invalid(
                Text.of("s_9c79d94a") +
                    Text.fmt("s_5313a347", systolic, diastolic)
            )
        }
        if (pulse != null && pulse <= 0) {
            return Check.Invalid(Text.of("s_5e6053dd"))
        }

        val warnings = mutableListOf<String>()
        if (systolic < MIN_SYSTOLIC || systolic > MAX_SYSTOLIC ||
            diastolic < MIN_DIASTOLIC || diastolic > MAX_DIASTOLIC
        ) {
            warnings += Text.fmt("s_e523a885", MIN_SYSTOLIC, MAX_SYSTOLIC) +
                Text.fmt("s_ae118c8b", MIN_DIASTOLIC, MAX_DIASTOLIC)
        }
        if (pulse != null && (pulse < MIN_PULSE || pulse > MAX_PULSE)) {
            warnings += Text.fmt("s_7558cf39", pulse, MIN_PULSE, MAX_PULSE)
        }
        val w = warnings.joinToString("；")
        return Check.Ok(if (w.isEmpty()) null else w)
    }

    /** 录入页必须出现的那句说明（不是判定、不是诊断）。 */
    val MANUAL_ENTRY_NOTE: String get() = Disclaimer.bpManual(Text.lang)

    /**
     * **回填**失败的说明文案：录入成功与写入外部平台是**两件事**，必须分开告诉用户。
     *
     * ⚠ 门禁纪律（判据④）：不能因为"没授权 / 写入失败"就让录入本身失败。
     * 那会让整个功能在未授权时**永远不可用**，而用户其实只想把读数记下来。
     *
     * 为什么是函数而不是常量：三星健康与 Health Connect 是**两条独立通道**，
     * 可能只有一条失败。写成"没写进去"会让人以为两条都没成，排查时白走一趟。
     *
     * ⚠⚠ **形参是 [ResText] 而不是 `String`，返回值也是 [ResText]** ——
     * 这条文案的去处是「一次性事件状态」（`SyncRepository.event`），
     * 存已求值的字符串会让它切语言后停在旧语言（2026-10-02 用户截图实付）。
     * 语言无关的通道名用 [ResText.raw]（如 `Health Connect`），
     * 本地化的用 [ResText.of]/[ResText.core]（如「三星健康」）。
     *
     * ⚠ 离线校验台架要拿到字符串时用 `.coreText()`，
     * 但**只能传 core 侧能求值的内容**（`Raw`/`Core`/`Sep`）—— 传 `Res` 会抛异常。
     */
    fun writeBackFailedNote(channels: List<ResText>): ResText =
        ResText.core("s_cdc4a484") + ResText.join(channels) + ResText.core("s_35b9c7af")
}
