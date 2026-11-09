package com.gemini.healthbridge.core

/**
 * 三星健康里那些「我们只做搬运、不自己加工」的指标（2026-09-23 接入）。
 *
 * ## 这一批与前几批的性质差别
 *
 * 项目里的指标分两类，这里属于**第三类**：
 *
 * | 类别 | 例子 | 我们做什么 |
 * |---|---|---|
 * | 自研算法 | ODI、呼吸率、压力/疲劳、自研能量评分 | 读原始数据、自己算、**必须有台架** |
 * | 三星算好的成品 | 能量评分、睡眠呼吸暂停 | 只搬运与展示 |
 * | **本文件的这一批** | 步数、活动量、营养、水分、血糖、爬楼、心律不齐、运动记录、各类目标 | 只搬运 + **做展示层的单位换算与枚举翻译** |
 *
 * 所以本文件里**没有算法**，只有格式化与翻译。但它仍然是纯函数、仍然值钱 ——
 * 单位与枚举翻译写错会给出**看着完全合理**的错值（例如把 mmol/L 当 mg/dL 显示，
 * 数值差 18 倍却毫无异样），这类错只能靠台架抓。**所以它有台架**（离线校验台架）。
 *
 * ## ⚠ 每个单位都是查官方文档核实的，不要凭印象改
 *
 * - 血糖 `GLUCOSE_LEVEL`：**mmol/L**（官方 API 文档原文 "The measured blood glucose value
 *   in mmol/L"）。⚠ 美国产品的直觉会以为是 mg/dL，**差约 18 倍**，且数值看起来同样"正常"。
 * - 水量 `AMOUNT`：**毫升**。
 * - 营养 `CALORIES`：**千卡**。
 * - 运动 `duration` 是 `Duration`（类型本身无歧义）；`calories` 千卡；`distance` **米**。
 * - 爬楼 `FLOOR`：层数。
 *
 * 单位一律在这里定死并注明出处，界面上不再各写一遍 —— 与本项目
 * 「单位换算只走 `core/Spo2.kt` 的 `Spo2.percent()`」是同一条纪律。
 */
object Extras {

    // ═══════════════════ 时长 ═══════════════════

    /**
     * 时长格式化。
     *
     * 分档是为了让不同量级都可读：活动时长可能是 8 分钟，也可能是 2 小时 15 分。
     * **不用 `Duration.toString()`** —— 那会给出 `PT2H15M` 这种东西。
     *
     * @param ms 毫秒。负数或 0 都返回「不足 1 分钟」而不是报错：三星的某些字段
     *   （例如目标值还没设过）可能给 0，而"目标 0 分钟"是个无意义的展示，
     *   调用方应当先判空再决定要不要显示，这里只保证**不崩、不显示怪值**。
     */
    fun fmtDuration(ms: Long): String {
        if (ms <= 0L) return Text.of("s_8c17b40a")
        val totalMin = ms / 60_000L
        if (totalMin < 1L) return Text.of("s_8c17b40a")
        val h = totalMin / 60L
        val m = totalMin % 60L
        return when {
            h <= 0L -> Text.fmt("s_86bb9d34", m)
            m == 0L -> Text.fmt("s_86150d1f", h)
            else -> Text.fmt("s_c5ef9b14", h, m)
        }
    }

    /** 时长格式化为 `java.time.Duration` 输入用的便捷重载。 */
    fun fmtDuration(d: java.time.Duration?): String = fmtDuration(d?.toMillis() ?: 0L)

    /**
     * **可空**时长的格式化：null → 占位符。
     *
     * ⚠ 与 [fmtDuration] 的区别很重要，不能合并：
     * · `null` = **三星没给这个值**（那次运动没记时长、或这一项没开放）；
     * · `0` = **确实是 0**。
     * 把 null 显示成「不足 1 分钟」，用户会以为那次运动只持续了几秒 ——
     * 两个含义完全不同的东西被画成同一个样子，正是本项目反复禁止的那种静默误导。
     */
    fun fmtDurationOrNull(ms: Long?): String = if (ms == null) "—" else fmtDuration(ms)

    // ═══════════════════ 距离 ═══════════════════

    /**
     * 距离格式化（输入**米**）。
     *
     * 1000 米以下显示米、以上显示公里 —— 400 米会显示「400 米」，5 公里显示「5.0 公里」。
     * 阈值处刻意不是"永远显示公里"：上下楼/短距离走路显示「0.4 公里」不如「400 米」直观。
     */
    fun fmtDistance(meters: Float?): String {
        val m = meters ?: return "—"
        if (m.isNaN() || m < 0f) return "—"
        return if (m < 1000f) Text.fmt("s_431a8f0f", m.toInt()) else String.format(java.util.Locale.US, Text.of("s_b993837a"), m / 1000f)
    }

    // ═══════════════════ 其它单位 ═══════════════════

    /**
     * 热量（千卡）。
     *
     * 取整：三星给的是 Float，但**小数千卡没有意义**（传感器精度远达不到），
     * 显示 `312.4 千卡` 会让人误以为它到小数位是准的。
     */
    fun fmtKcal(kcal: Float?): String {
        val k = kcal ?: return "—"
        if (k.isNaN() || k < 0f) return "—"
        return Text.fmt("s_c9325c18", Math.round(k))
    }

    /** 水量（毫升）。大于等于 1000 mL 时同时给出升，便于一眼看懂。 */
    fun fmtVolume(ml: Float?): String {
        val v = ml ?: return "—"
        if (v.isNaN() || v < 0f) return "—"
        val i = Math.round(v)
        return if (i < 1000) "$i mL"
        else String.format(java.util.Locale.US, "%d mL（%.1f L）", i, i / 1000f)
    }

    /**
     * 血糖（**mmol/L**）。
     *
     * ⚠ 单位是官方文档核实的 mmol/L，**不要**按 mg/dL 处理。数值固定一位小数 ——
     * 血糖仪本身就是一位小数的分辨率，多给位数是假的精度。
     */
    fun fmtGlucose(mmol: Float?): String {
        val g = mmol ?: return "—"
        if (g.isNaN() || g <= 0f) return "—"
        return String.format(java.util.Locale.US, "%.1f mmol/L", g)
    }

    /** 爬楼层数。三星给 Float，取整显示（不会有人爬 0.4 层）。 */
    fun fmtFloors(floors: Float?): String {
        val f = floors ?: return "—"
        if (f.isNaN() || f < 0f) return "—"
        return Text.fmt("s_d4c2b227", Math.round(f))
    }

    /** 步数。**步数不需要千分位**（日常量级就是几千到几万，加了反而难读）。 */
    fun fmtSteps(steps: Long?): String = steps?.let { Text.fmt("s_861e53d0", it) } ?: "—"

    /** 核心体温（来自体温计/合作设备，**不是我们测的**）。*/
    fun fmtCoreTemp(c: Float?): String {
        val t = c ?: return "—"
        if (t.isNaN() || t <= 0f) return "—"
        return Temperature.format(t)
    }

    // ═══════════════════ 运动类型翻译 ═══════════════════

    /**
     * 三星的 `PredefinedExerciseType` 枚举翻译。
     *
     * ## 为什么要自己翻（113 个值全列）
     *
     * SDK 只给枚举名（`STATIONARY_BIKING`），界面上直接显示会是这个样子。
     * 三星自己的界面有本地化文案，但**那不在 SDK 里** —— 我们不能像读数值那样把它读出来。
     * 所以只能自己建一张表。**113 个值全列，不留"常用的一小部分"**：
     * 漏掉的那个正好是用户做的运动时，界面上就会出现一个英文常量，比翻错更显眼。
     *
     * ## 未知名怎么办
     *
     * 三星以后加新枚举时这里不会同步 —— 所以 [label] 有兜底：把下划线换成空格、
     * 首字母大写，并**保留原文**。宁可显示 `New Sport` 让用户知道"这是个我们还不认识的运动"，
     * 也不要瞎猜一个中文名或者吞掉不显示。
     */
    private val EXERCISE_CN: Map<String, String> = mapOf(
        "UNDEFINED" to "s_1b36a6a9", "OTHER" to "s_002a0ae5",
        // ── 走跑爬 ──
        "WALKING" to "s_0048ee3f", "RUNNING" to "s_0048fd58", "TRACK_RUNNING" to "s_1ee11d0d",
        "STAIR_CLIMBING" to "s_1e1beaaf", "HIKING" to "s_0031531b", "BACKPACKING" to "s_0f44df15",
        // 枚举里**没有** MOUNTAIN_CLIMBING（我第一版凭印象加过一条，是死条目）——
        // 徒步/负重徒步/攀岩/定向越野才是实际存在的四个。
        "ROCK_CLIMBING" to "s_00340ba9", "ORIENTEERING" to "s_5c852f43",
        // ── 球类 ──
        "BASEBALL" to "s_00361779", "SOFTBALL" to "s_002d43b9", "CRICKET" to "s_00356a00", "GOLF" to "s_28bb70ff",
        "BOWLING" to "s_153bba54",
        // ⚠ HOCKEY 与 ICE_HOCKEY 是**两个不同的枚举值**：前者是草地曲棍球，后者才是冰球。
        //    我第一版把 HOCKEY 写成「冰球（草地）」、且**整条漏了 ICE_HOCKEY** ——
        //    靠"拿 javap 的 113 个名字与翻译表做集合比对"才发现的（见离线校验台架的说明）。
        "HOCKEY" to "s_1b2b096c", "ICE_HOCKEY" to "s_002a4113", "RUGBY" to "s_1c09faf3",
        "BASKETBALL" to "s_003fdecd", "SOCCER" to "s_0048f69c", "HANDBALL" to "s_0032c064",
        "AMERICAN_FOOTBALL" to "s_30b42c1d", "VOLLEYBALL" to "s_003367b9",
        "BEACH_VOLLEYBALL" to "s_aa7c0e8d", "SQUASH" to "s_002ddec6", "TENNIS" to "s_00419a76",
        "BADMINTON" to "s_21aa99b9", "TABLE_TENNIS" to "s_14aabf5e", "RACQUETBALL" to "s_16e9e122",
        "FLYING_DISC" to "s_004eb072",
        // ── 格斗与体操 ──
        "BOXING" to "s_0032f454", "MARTIAL_ARTS" to "s_00375c61", "BALLET" to "s_00436405",
        "DANCING" to "s_00492577", "BALLROOM_DANCING" to "s_14dfdb00", "PILATES" to "s_1b055489",
        "YOGA" to "s_003bda51", "STRETCHING" to "s_0032dc13", "ZUMBA" to "s_002f7712", "AEROBICS" to "s_154eed04",
        // ── 有氧动作 ──
        "JUMP_ROPE" to "s_0049224c", "HULA_HOOPING" to "s_164b7696", "JUMPING_JACKS" to "s_190be50b",
        "BURPEES" to "s_1cbab561", "MOUNTAIN_CLIMBERS" to "s_1f3636f7", "HIGH_KNEES" to "s_28bef91b",
        "SKATERS" to "s_1d2ef35c", "CIRCUIT_TRAINING" to "s_f026e45f",
        // ── 力量 ──
        "PUSH_UPS" to "s_15196ead", "PULL_UPS" to "s_d28db85f", "SIT_UPS" to "s_a9ed9a24",
        "CRUNCH" to "s_002b375e", "PLANK" to "s_c335d2b6", "LEG_RAISES" to "s_00288b39",
        "BENCH_PRESS" to "s_002b115d", "SQUATS" to "s_0038d0c5", "LUNGES" to "s_191cd3cc",
        "LEG_PRESSES" to "s_0042923b", "LEG_EXTENSIONS" to "s_22185287", "LEG_CURLS" to "s_2219cdc2",
        "BACK_EXTENSIONS" to "s_21e2d129", "LAT_PULLDOWNS" to "s_d48cfbe7", "DEADLIFTS" to "s_003e020d",
        "SHOULDER_PRESSES" to "s_00334d36", "FRONT_RAISES" to "s_15bdaecc", "LATERAL_RAISES" to "s_150c24f6",
        "ARM_CURLS" to "s_0031034b", "ARM_EXTENSIONS" to "s_2229dde2",
        "WEIGHT_MACHINE" to "s_37fb9008",
        // ── 骑行与轮滑 ──
        "BIKING" to "s_004fa07f", "MOUNTAIN_BIKING" to "s_7a2c369a", "STATIONARY_BIKING" to "s_2cf84eb4",
        "INLINE_SKATING" to "s_0ad3f3f9", "ROLLER_SKATING" to "s_1d39145c", "ICE_SKATING" to "s_003906a3",
        "ICE_DANCING" to "s_9ea373bd", "HANG_GLIDING" to "s_1d4650f3",
        // ── 水上 ──
        "POOL_SWIMMING" to "s_b5e6d11c", "OPEN_WATER_SWIMMING" to "s_2547b572",
        "AQUA_AEROBICS" to "s_e8aeffb7", "CANOEING" to "s_1d9234c9", "SAILING" to "s_00309f4b",
        "SCUBA_DIVING" to "s_a1a3c44e", "SNORKELING" to "s_00386ea6", "KAYAKING" to "s_1f3e379b",
        "KITESURFING" to "s_9a26637f", "RAFTING" to "s_00393b47", "ROWING" to "s_002a816f",
        "WINDSURFING" to "s_00308491", "YACHTING" to "s_0038e8ef", "WATER_SKIING" to "s_00392127",
        // ── 器械 ──
        "STEP_MACHINE" to "s_256a4770", "ROWING_MACHINE" to "s_15c0a307", "TREADMILL" to "s_255a0b42",
        "ELLIPTICAL" to "s_1bb768e1", "STAIR_CLIMBING_MACHINE" to "s_1e1be93a",
        // ── 雪上 ──
        "CROSS_COUNTRY_SKIING" to "s_1da38aa9", "SKIING" to "s_00394bdd", "ALPINE_SKIING" to "s_d80ed79e",
        "SNOWBOARDING" to "s_45e031cb", "SNOWSHOEING" to "s_60c8c2e5",
        // ── 马术与射箭 ──
        "ARCHERY" to "s_002f91b9", "HORSEBACK_RIDING" to "s_004fb29f",
        // ── 混合项目 ──
        "TRIATHLON" to "s_03801779", "DUATHLON" to "s_0380254a", "AQUATHLON" to "s_a73756d7",
        "AQUABIKE" to "s_f0fa9406", "CROSS_TRIATHLON" to "s_daabb0a5",
        "CROSS_DUATHLON" to "s_daabbe76",
        // ── 训练阶段（三星会单独记这三段）──
        // ⚠⚠ 这里的 value 必须是**key 字符串**，不能写成 `Text.of("s_xxx")` ——
        //    map 是 `val`，只会在对象初始化时求值一次，用户切语言后**永远不更新**。
        //    （改造时这 4 条因为离声明行刚好 60 行、超出当时 `decl_of` 的回溯上限，
        //      被当成普通文案替换掉了 —— 台架 extrascheck 以 `Text: 未登记的 key=休息`
        //      崩在这里，才发现的。）
        "WARM_UP" to "s_003a57f2", "COOL_DOWN" to "s_00343638", "BREAK" to "s_0028d622", "TRANSITION" to "s_0033739f",
    )

    /**
     * 运动类型的中文名。
     *
     * 未知名**返回原文的宽松形式**（下划线换空格），不返回空串、也不返回「其它」——
     * 「其它」会把"我们不认识"伪装成"三星说是其它"，那是两回事。
     */
    fun exerciseLabel(name: String?): String {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return Text.of("s_0049ea18")
        EXERCISE_CN[n]?.let { return Text.of(it) }
        return n.lowercase().split('_')
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    /** 翻译条目数。台架用它兜住"以后往表里加值但忘了加测试"这件事。 */
    val exerciseMapSize: Int get() = EXERCISE_CN.size

    // ═══════════════════ 目标达成进度 ═══════════════════

    /**
     * **营养记录**的餐次翻译（`NutritionType.MealType`，7 个取值）。
     *
     * ⚠ 与 [Glucose.mealLabel] **不是同一个枚举** —— 那个是血糖的 `MealStatus`
     * （空腹/餐前/餐后那 14 个），这个是吃饭记录本身的餐次（早/午/晚/加餐）。
     * 两个枚举里都有 `UNDEFINED` 且都返回「未标注」，混用不会报错、只会**静默串味**，
     * 所以刻意分成两个函数、两个名字。
     */
    private val MEAL_CN: Map<String, String> = mapOf(
        "UNDEFINED" to "s_1b3946f7", "BREAKFAST" to "s_0034bf4b", "LUNCH" to "s_002b36e8", "DINNER" to "s_0034f91e",
        "MORNING_SNACK" to "s_8af5e886", "AFTERNOON_SNACK" to "s_8b183621",
        "EVENING_SNACK" to "s_de6a0d02",
    )

    /** 营养记录的餐次中文名。未知名不崩、返回「未标注」。 */
    fun mealLabel(raw: String?): String = MEAL_CN[raw?.uppercase()]?.let { Text.of(it) } ?: Text.of("s_1b3946f7")

    /** 餐次翻译条目数（台架用）。 */
    val mealMapSize: Int get() = MEAL_CN.size

    /**
     * 目标达成率（0f~1f+）。任一为空或目标 <= 0 时返回 null。
     *
     * ⚠ **不夹到 1f**：超额完成应当能看出来（界面上画进度条时自己再夹）。
     * 目标为 0 时返回 null 而不是 0% 或 Infinity —— "没设目标"和"完成了 0%"不是一回事。
     */
    fun goalRatio(current: Double?, goal: Double?): Double? {
        val g = goal ?: return null
        val c = current ?: return null
        if (g <= 0.0 || g.isNaN() || c.isNaN()) return null
        return c / g
    }

    /** 目标达成率的一句话描述。null 时说明还没设目标。 */
    fun fmtGoal(current: Double?, goal: Double?, unit: (Double) -> String): String {
        if (goal == null || goal <= 0.0) return Text.of("s_275a48f6")
        val c = current ?: return Text.fmt("s_6c4e3a3d", unit(goal))
        val r = goalRatio(c, goal) ?: return Text.fmt("s_6c4e3a3d", unit(goal))
        val pct = Math.round(r * 100).toInt()
        return Text.fmt("s_94c0fd95", unit(c), unit(goal), pct)
    }
}

/**
 * 「心律不齐通知」（Irregular Heart Rhythm Notification，IHRN）。
 *
 * ## 它与我们已有指标的关系
 *
 * 这是三星健康**监测器**（Samsung Health Monitor）那条线上的筛查信号，与
 * [SleepApnea] 同源 —— 两个都是「监测器在后台跑、发现可疑就记一条」，
 * 而不是"用户主动测一次"。它基于手表**持续心率**做节律分析，不是心电波形诊断。
 *
 * ## ⚠ 措辞红线（与 [Apnea] 完全同一条）
 *
 * 只有两个取值：`DETECTED` / `UNDEFINED`。**没有 `NOT_DETECTED`** ——
 * 这一点与呼吸暂停**不同**，别照抄那边的三态。
 *
 * 所以：
 * - `DETECTED` = 检测到不规则节律的迹象 → **建议去做一次心电**，不是"你有房颤"；
 * - `UNDEFINED` = 没判定出来 → **更不能当成阴性**（"没检测到"与"没判定"是两回事）；
 * - **不存在**"这次没问题"这个结论 —— 没有这一条记录只说明**没记录**，
 *   不代表心脏没问题。界面上必须写出来，否则用户会从"列表是空的"推出错误结论。
 *
 * 文案一律带「迹象」，不许出现「确诊」「房颤」「有病」这类词。
 */
object Ihrn {

    /** 检测到不规则节律的迹象。 */
    const val DETECTED = "DETECTED"

    /** 未判定。 */
    const val UNDEFINED = "UNDEFINED"

    /**
     * 归一化：认不出的取值一律归到 [UNDEFINED]。
     *
     * 与 [Apnea.normalize] 同款：三星以后加取值时，我们只是多一种读不懂的值，
     * **不会崩在解析上**。
     */
    fun normalize(raw: String?): String {
        val s = raw?.trim()?.uppercase().orEmpty()
        return if (s == DETECTED) DETECTED else UNDEFINED
    }

    /** 结果文案。⚠ 只描述"三星记了什么"，不描述"你的心脏怎么样"。 */
    fun label(raw: String?): String = when (normalize(raw)) {
        DETECTED -> Text.of("s_e696069a")
        else -> Text.of("s_1b2e0280")
    }

    /** 是否值得用警示色。只有 [DETECTED] 算。 */
    fun isAlarming(raw: String?): Boolean = normalize(raw) == DETECTED

    /** 建议做什么。**把用户导向正确的检查**，而不是让我们下结论。 */
    val ADVICE: String get() = Disclaimer.irregularAdvice(Text.lang)

    /** 为什么"列表为空"不能推出"没问题"—— 这条必须和列表同时出现。 */
    val EMPTY_IS_NOT_NEGATIVE: String get() = Disclaimer.irregularEmptyIsNotNegative(Text.lang)

    /** 与我们的心电的关系（口径说明）。 */
    val VS_OUR_ECG: String get() =
        Text.of("s_c4b98638") +
            Text.of("s_bc4c3531") +
            Text.of("s_61744b9a")

    /** 读不到时的说明。 */
    val UNAVAILABLE_NOTE: String get() =
        Text.of("s_7ed62678") +
            Text.of("s_e896af1e")

    /** 免责。 */
    val NOT_A_DIAGNOSIS: String get() = Disclaimer.irregularDisclaimer(Text.lang)
}

/**
 * 血糖。
 *
 * ## ⚠ 单位是 mmol/L（官方文档核实），不是 mg/dL
 *
 * 这一条差别约 18 倍，而且**两个数值看起来都很"正常"**（5.6 vs 101），
 * 写错不会报错、只会静默给错值。所以单位在 [Extras.fmtGlucose] 里定死一处。
 *
 * ## 我们不做任何判读
 *
 * 不给"偏高/偏低"、不做箭头、不画参考带 —— 血糖的判读依赖测量情境
 * （空腹/餐后多久/是否用药），本 App 拿不到足够的上下文，凭一个数下判断是越界。
 * 界面上只如实显示数值 + 情境（三星给了 `MEAL_STATUS` 就一起显示）。
 *
 * ## 数据来源决定"有没有数"
 *
 * 手表的 PPG **测不出血糖**。要有数据，用户得用血糖仪（或 CGM）测，并且
 * 那个 App 往三星健康里写。**所以本机没有数据是常态** —— 必须如实说明，不能显示 0。
 */
object Glucose {

    /** 情境（`MEAL_STATUS`）翻译。未知名走宽松形式（见 [Extras.exerciseLabel] 的同款做法）。 */
    private val MEAL_CN: Map<String, String> = mapOf(
        "UNDEFINED" to "s_1b3946f7",
        "FASTING" to "s_003f2de7",
        "GENERAL" to "s_004d7267",
        "BEFORE_MEAL" to "s_004ea57d",
        "AFTER_MEAL" to "s_004ea73e",
        "BEFORE_BREAKFAST" to "s_1afe35ae", "AFTER_BREAKFAST" to "s_1afe376f",
        "BEFORE_LUNCH" to "s_161d6b05", "AFTER_LUNCH" to "s_161d6cc6",
        "BEFORE_DINNER" to "s_1b1bcca7", "AFTER_DINNER" to "s_1b1bce68",
        "AFTER_SNACK" to "s_15f16ede", "AFTER_BED_TIME" to "s_003d68f0",
        "BEFORE_SLEEP" to "s_003d68f0",
    )

    /** 采样来源翻译。`WHOLE_BLOOD` 等是**样本类型**，与"从哪里取的"是两个字段。 */
    private val SOURCE_CN: Map<String, String> = mapOf(
        "UNDEFINED" to "s_1b3946f7", "VENOUS" to "s_004df394", "CAPILLARY" to "s_003308ab",
    )

    private val SAMPLE_CN: Map<String, String> = mapOf(
        "UNDEFINED" to "s_1b3946f7", "WHOLE_BLOOD" to "s_002a3078", "PLASMA" to "s_00462606", "SERUM" to "s_004626c5",
    )

    fun mealLabel(raw: String?): String = MEAL_CN[raw?.uppercase()]?.let { Text.of(it) } ?: Text.of("s_1b3946f7")

    fun sourceLabel(raw: String?): String = SOURCE_CN[raw?.uppercase()]?.let { Text.of(it) } ?: Text.of("s_1b3946f7")

    fun sampleLabel(raw: String?): String = SAMPLE_CN[raw?.uppercase()]?.let { Text.of(it) } ?: Text.of("s_1b3946f7")

    /**
     * 三星的原始串是不是"未标注"（模块 #15）。
     *
     * ⚠⚠ **判"未标注"必须走这个，不能拿翻译后的结果去 `!= "未标注"`** ——
     * 翻译后那个值是"Not specified"/"未標註"等，比较**永远为真**，
     * 于是「未标注」会被当普通值显示出来（不报错，只是多出一段没意义的前缀）。
     * 判据认的是**原始串**（`UNDEFINED`），它与语言无关。
     */
    fun isUndefined(raw: String?): Boolean = raw.isNullOrBlank() || raw.equals("UNDEFINED", ignoreCase = true)

    /** 读不到时的说明（本机没有数据是常态，要说清为什么）。 */
    val UNAVAILABLE_NOTE: String get() =
        Text.of("s_89452404") +
            Text.of("s_866396c9") +
            Text.of("s_f85da64a")

    /** 单位说明（防止有人按 mg/dL 读）。 */
    val UNIT_NOTE: String get() = Disclaimer.glucoseUnit(Text.lang)

    /** 为什么不做高/低判断。 */
    val NO_JUDGEMENT: String get() = Disclaimer.glucoseNoJudgement(Text.lang)
}

/**
 * 活动与运动相关指标的口径说明。
 *
 * 「步数 / 活动卡路里 / 活动时长 / 距离 / 爬楼」这一组在三星健康里是**聚合量**
 * （通过 `aggregateData` 取，不是逐条记录），而且**大部分来自手机自身的计步**、
 * 手表只是其中一个来源 —— 所以它的口径与"我们手表采的"完全不是一回事。
 */
object ActivityNotes {

    val HOW: String get() =
        Text.of("s_a023d6a6") +
            Text.of("s_a71c8b43")

    val VS_OUR_ACTIVITY: String get() =
        Text.of("s_c8f8fff9") +
            Text.of("s_b0ef0e1d")

    val MAY_BE_EMPTY: String get() =
        Text.of("s_23dd6bc3") +
            Text.of("s_092fe929")
}

/**
 * 饮食（营养 / 水分）的口径说明。
 *
 * ⚠ 这两个**必须用户自己在三星健康里记**才有数据。手表测不出吃了多少、喝了多少。
 */
object DietNotes {

    val HOW: String get() =
        Text.of("s_07a3f009") +
            Text.of("s_29f56f80")

    val NOT_A_PLAN: String get() = Disclaimer.dietNotAPlan(Text.lang)
}

/**
 * 六类「目标」的口径说明。
 *
 * 三星健康的这些目标都是**聚合算子取回来的单值**（`LAST` / `LAST_CALORIES` /
 * `LAST_BED_TIME` 等），不是逐条记录。目标值为空说明用户没设过。
 */
object GoalNotes {

    val HOW: String get() =
        Text.of("s_da047b4f") +
            Text.of("s_a962e6c5")
}
