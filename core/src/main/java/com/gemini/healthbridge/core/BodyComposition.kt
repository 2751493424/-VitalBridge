package com.gemini.healthbridge.core

/**
 * 体成分（BIA）的纯逻辑：字段有效域、状态码话术、参考区间、格式化。
 *
 * 刻意做成**无 Android 依赖**的纯 Kotlin：这样能直接进离线 JVM 台架
 * （离线校验台架）跑断言。取值范围全部逐条对齐三星官方文档，
 * 不凭印象写 —— 这个项目在"凭印象写单位/范围"上已经出过事故（血氧 0–100 写成 0–1）。
 *
 * ## 数据来源与口径
 * 手表 `BIA_ON_DEMAND` 给的是**一次测量的一份结果**，不是连续量。数值由三星的
 * 生物电阻抗算法算出，本 App 只做搬运与展示，**不做任何二次推算**（除了 BMI 这种纯算术）。
 */
object BodyComposition {

    // ═══════════════ 取值域（三星官方文档原文） ═══════════════

    /** 体脂率（%），文档写明 0–100。 */
    const val FAT_RATIO_MIN = 0f
    const val FAT_RATIO_MAX = 100f

    /** 体脂量（kg），文档写明 2 ~ 体重-2。 */
    const val FAT_MASS_MIN = 2f

    /** 骨骼肌率（%），0–100。 */
    const val MUSCLE_RATIO_MIN = 0f
    const val MUSCLE_RATIO_MAX = 100f

    /** 基础代谢率（kcal），0–12000。 */
    const val BMR_MAX = 12000f

    /** 身体阻抗模值（Ω），0–9999（SDK 1.3.0 起提供）。 */
    const val IMPEDANCE_MAG_MIN = 0f
    const val IMPEDANCE_MAG_MAX = 9999f

    /** 身体阻抗相角（°），文档写明 **-90 ~ 0**（负值，不是正值）。 */
    const val IMPEDANCE_DEG_MIN = -90f
    const val IMPEDANCE_DEG_MAX = 0f

    /**
     * 资料库（TrackerUserProfile）的取值范围，同样是官方文档原文。
     * 用于在手机端录入时**当场拦住**不合法输入 —— 不合格的资料会让测量直接返回
     * 状态码 18（体脂率超出预期范围），那是个很难自查的失败。
     */
    const val PROFILE_HEIGHT_MIN = 20f
    const val PROFILE_HEIGHT_MAX = 300f
    const val PROFILE_WEIGHT_MIN = 2f
    const val PROFILE_WEIGHT_MAX = 500f
    const val PROFILE_AGE_MIN = 0
    const val PROFILE_AGE_MAX = 200

    /** SDK 的性别取值：0=女 1=男（**不是** 1=男 2=女）。 */
    const val GENDER_FEMALE = 0
    const val GENDER_MALE = 1

    /** 判断一个数值是否落在 [lo, hi] 闭区间内；null 一律为 false（"没有值"不等于"值合法"）。 */
    fun inRange(v: Float?, lo: Float, hi: Float): Boolean = v != null && v >= lo && v <= hi

    /** 资料三项是否齐备且合法。缺任何一项都取不到 BIA tracker。 */
    fun profileComplete(heightCm: Float?, weightKg: Float?, gender: Int?, age: Int?): Boolean =
        inRange(heightCm, PROFILE_HEIGHT_MIN, PROFILE_HEIGHT_MAX) &&
            inRange(weightKg, PROFILE_WEIGHT_MIN, PROFILE_WEIGHT_MAX) &&
            (gender == GENDER_FEMALE || gender == GENDER_MALE) &&
            age != null && age in PROFILE_AGE_MIN..PROFILE_AGE_MAX

    // ═══════════════ 状态码 ═══════════════

    /**
     * 一次测量的状态。
     *
     * @param label 一句话结论（标题用）。状态 0 之外一律是**失败**。
     * @param guidance 给用户的**可执行**动作。三星文档对每个失败码都给了明确指引，
     *   这里逐条翻译过来 —— 笼统地说"测量失败请重试"会让人反复失败却不知道改什么，
     *   而 BIA 的失败绝大多数是**姿势问题**（手指没压在电极键上、两只手互相碰到），
     *   是用户自己能立刻改对的。
     */
    data class StatusInfo(
        val code: Int,
        /** 状态名。**待求值**文案：它会被存进手表的会话状态，存已求值的串会切语言后停在旧语言。 */
        val label: ResText,
        /** 该怎么办。同样**待求值**（理由见 [label]）。 */
        val guidance: ResText,
        val retryable: Boolean,
    )

    /** 取得原始状态读数（未映射到已知码时返回 [UNKNOWN]）。 */
    const val UNKNOWN = -1

    fun statusOf(code: Int?): StatusInfo = when (code) {
        0 -> StatusInfo(0, ResText.core("s_cb754364"), ResText.EMPTY, false)
        4 -> StatusInfo(
            4, ResText.core("s_dd672ac0"),
            ResText.core("s_7da1abeb"),
            true,
        )
        7 -> StatusInfo(
            7, ResText.core("s_e1a34dbd"),
            ResText.core("s_0f402dc7"),
            true,
        )
        8 -> StatusInfo(
            8, ResText.core("s_1f3478b3"),
            ResText.core("s_6a241335"),
            true,
        )
        9 -> StatusInfo(
            9, ResText.core("s_b23f39ed"),
            ResText.core("s_84817695"),
            true,
        )
        10 -> StatusInfo(
            10, ResText.core("s_5fff284a"),
            ResText.core("s_d3210a14"),
            true,
        )
        11 -> StatusInfo(
            11, ResText.core("s_a5f911f6"),
            ResText.core("s_a8256090"),
            true,
        )
        13 -> StatusInfo(
            13, ResText.core("s_f86b7836"),
            ResText.core("s_64083297"),
            true,
        )
        14 -> StatusInfo(
            14, ResText.core("s_74643f37"),
            ResText.core("s_b27cbba3"),
            true,
        )
        15 -> StatusInfo(
            15, ResText.core("s_0ebf7268"),
            ResText.core("s_eabedb17"),
            true,
        )
        17 -> StatusInfo(
            17, ResText.core("s_5f1ca040"),
            ResText.core("s_ca9bb45b"),
            true,
        )
        18 -> StatusInfo(
            18, ResText.core("s_4cd880bc"),
            ResText.core("s_ae1ee2c1"),
            false,
        )
        else -> StatusInfo(
            UNKNOWN, ResText.core("s_20094b3e"),
            ResText.core("s_47f96278")
                + ResText.core("s_5acb4bf1"),
            true,
        )
    }

    // ═══════════════ 参考区间 ═══════════════

    /**
     * BMI 由**用户自填的身高体重**算得，不是测量值 —— 界面必须标明这一点，
     * 否则会被当成仪器算出来的指标。资料缺失时返回 null，不硬凑。
     */
    fun bmi(weightKg: Float?, heightCm: Float?): Float? {
        if (!inRange(weightKg, PROFILE_WEIGHT_MIN, PROFILE_WEIGHT_MAX)) return null
        if (!inRange(heightCm, PROFILE_HEIGHT_MIN, PROFILE_HEIGHT_MAX)) return null
        val m = heightCm!! / 100f
        if (m <= 0f) return null
        return weightKg!! / (m * m)
    }

    /** BMI 分级（中国成人标准）：<18.5 偏低 / 18.5–23.9 正常 / 24–27.9 超重 / ≥28 肥胖。 */
    fun bmiLabel(bmi: Float?): String = when {
        bmi == null -> "——"
        bmi < 18.5f -> Text.of("s_002967bb")
        bmi < 24f -> Text.of("s_003751e1")
        bmi < 28f -> Text.of("s_0048fcdc")
        else -> Text.of("s_00425545")
    }

    /**
     * 体脂率的参考区间（成年人常用口径，按性别分）。
     *
     * 刻意只给"偏低/常见/偏高"三个词、不给"健康/不健康"的判断：
     * 体脂率的合理区间随年龄与训练状态差很多，写死一个"健康范围"会误导。
     * 性别未知时用两性区间的并集，宁可比实际宽也不要误报。
     */
    fun bodyFatHint(ratio: Float?, gender: Int?): String {
        if (!inRange(ratio, FAT_RATIO_MIN, FAT_RATIO_MAX)) return "——"
        val r = ratio!!
        return when (gender) {
            GENDER_MALE -> when {
                r < 10f -> Text.of("s_002967bb")
                r <= 20f -> Text.of("s_c455a02e")
                else -> Text.of("s_0029b345")
            }
            GENDER_FEMALE -> when {
                r < 18f -> Text.of("s_002967bb")
                r <= 28f -> Text.of("s_c455a02e")
                else -> Text.of("s_0029b345")
            }
            else -> when {           // 性别未知：用并集，宁可宽
                r < 10f -> Text.of("s_002967bb")
                r <= 28f -> Text.of("s_c455a02e")
                else -> Text.of("s_0029b345")
            }
        }
    }

    // ═══════════════ 格式化 ═══════════════

    /**
     * 保留一位小数。
     *
     * 用 Java 的 `String.format` 走的是**四舍五入**（HALF_UP），
     * 而 `"%.1f".format()` 同理。这里不自己写 `(v*10).roundToInt()/10f` ——
     * 那条路在 1.05 这类值上会因 Float 二进制表示给出意外的进位。
     */
    fun format1(v: Float?): String = v?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "--"

    /** 整数显示（体脂率、骨骼肌率、心率这类天然按整数读的量）。 */
    fun format0(v: Float?): String = v?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--"

    /** 阻抗模值（Ω）：本身是几百量级，取整显示。 */
    fun formatOhm(v: Float?): String = v?.let { String.format(java.util.Locale.US, "%.0f Ω", it) } ?: "--"

    /** 相角（°），保留一位小数。 */
    fun formatDegree(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.1f°", it) } ?: "--"

    /** 基础代谢（kcal）。 */
    fun formatKcal(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.0f kcal", it) } ?: "--"

    /** 体水（**升**，不是公斤），保留一位小数。 */
    fun formatLiters(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.1f L", it) } ?: "--"

    /** 体重等质量（kg），保留一位小数。 */
    fun formatKg(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.1f kg", it) } ?: "--"
}

/** 体成分记录的来源标记，两端共用。 */
object BiaSource {
    /** 手表 `BIA_ON_DEMAND` 测量的体成分。 */
    const val WATCH = "watch_bia"

    /**
     * 三星健康里既有的体成分记录（由三星健康自己的身体成分功能产生）。
     * 与手表测量**分开展示**：两者的算法与标定不同，混在一条线上会被误读成同一台仪器的趋势。
     */
    const val SAMSUNG = "samsung_body"
}
