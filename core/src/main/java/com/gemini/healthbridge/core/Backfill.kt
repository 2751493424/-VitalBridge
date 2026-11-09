package com.gemini.healthbridge.core

/**
 * 数据回填（模块：把本 App 产生的数据写进外部健康平台）的策略层。
 *
 * ## 为什么要有这一层（而不是直接在 IO 代码里 if 一下）
 *
 * 回填有两个**安静出错**的方式，都不会报错：
 *
 * 1. **把别人的数据写回去**。三星健康自己也往 Health Connect 写。
 *    如果我们把从三星读来的记录再写进 HC，结果是**重复计数 + 来源冒充**：
 *    HC 里出现两条同样的心率，一条 dataOrigin 是三星、一条是我们，用户看到翻倍的数据，
 *    而任何一方都没有报错。判据见 [isOurs]。
 * 2. **单位换算写错**。HC 的 `HeightRecord` 要**米**，我们库里存的是**厘米** ——
 *    少除一个 100 就是"身高 17200 米"，而 HC 对身高范围有校验、
 *    越界只是**静默拒绝写入**（用户以为回填成功了）。
 *
 * 所以这两件事都放在 core：**没有 Android / Room 依赖，离线台架能直接测**
 * （离线校验台架），与 `Spo2.percent()` / `Extras.fmt*` 是同一条纪律
 * ——「单位换算只在一处写」。
 *
 * ## 幂等
 *
 * 回填是**可以重复触发**的（用户点两次、或同步后再点一次），所以每条记录要有一个
 * **稳定的** [clientId]。平台侧用同 id 插入不会产生重复记录。
 */
object Backfill {

    // ───────────────────────── 1. 来源判据（最重要的一条） ─────────────────────────

    /**
     * 规范的手表来源前缀（手表本地库、以及手机端规范化之后的落库都用它）。
     *
     * 完整取值：`watch_hsm`（心率）/ `watch_o2`（血氧）/ `watch_temp`（腕温）/
     * `watch_bia`（体成分）/ `watch_manual`（手动录入的血压）/ `watch_ecg`·`watch_ppg`（HRV）/
     * `watch_accel`（活动片段）。
     */
    const val OURS_PREFIX = "watch_"

    /**
     * **不是**我们的来源前缀 —— 从别处读进来、或别处产生的东西，一律不能回填。
     *
     * 三星健康自己也往 Health Connect 写。我们把从三星读来的记录再写回 HC，
     * 结果是**重复计数 + 来源冒充**：HC 里出现两条同样的心率，一条 dataOrigin 是三星、
     * 一条是我们，用户看到翻倍的数据，而**任何一方都不会报错**。
     *
     * ⚠⚠ **新增任何「从外部读进来」的来源，必须往这里登记。**
     * 漏登记的后果是那条链路的数据被默默回填，且没有任何报错 ——
     * 这是本项目里唯一一处「忘了改一个常量就会静默出错」的地方，
     * 所以 [isOurs] 的方向与这份清单一并写在台架里钉着。
     */
    val FOREIGN_PREFIXES = listOf(
        "samsung",        // 三星健康（`samsung` / `samsung_skin` / `samsung_body`）
        "imported",       // 从备份文件导进来的
        "hc_",            // Health Connect 读回来的（防将来做反向导入时回环）
        "health_connect",
    )

    /**
     * 这条记录是不是**我们自己产生的**。
     *
     * ## 为什么不是简单的白名单
     *
     * 理想判据是「`source` 以 [OURS_PREFIX] 开头」。但**手机端库里的心率/血压/血氧
     * 历史上存的是手表型号**（`SyncMessage.deviceId` = `Build.MODEL`，例如 `SM-R900`）
     * —— 那是早期把「设备标识」当成了「来源」。若坚持白名单，那几类在手机端库里
     * **一条都认不出来**，整个回填等于没做，而且界面上只会显示「0 条」、看不出为什么。
     *
     * 所以判据是「非空，且不以 [FOREIGN_PREFIXES] 里的前缀开头」：
     * · `watch_*` → 真（规范形态）
     * · `SM-R900` 之类的手表型号 → 真（历史形态，确实是手表同步过来的）
     * · `samsung*` / `imported*` / `hc_*` → 假
     *
     * ⚠ 代价要说清楚：这是**排除法**，不是白名单 —— 判错的默认方向是「多写」而不是
     * 「少写」。靠 [FOREIGN_PREFIXES] 那条纪律兜（新增外来来源必须登记）。
     * 手机端落库已改为写规范值（见 `SyncRepository` 的 `SourceHr` 等），
     * 所以「型号形态」只会越来越少。
     *
     * @param source 记录来源标记（各表的 `source` 列）。
     */
    fun isOurs(source: String?): Boolean {
        if (source == null || source.isBlank()) return false
        return FOREIGN_PREFIXES.none { source.startsWith(it) }
    }

    /**
     * 把手表型号形态的 `source` 规范化成 [OURS_PREFIX] 形态。
     *
     * 幂等键里带 `source`（见 [clientId]），而同一个来源在新旧两版里写法可能不同 ——
     * 不规范化的话，同一条记录（同一时刻、同一来源）会因为写法不同算出**两个 id**，
     * 于是回填两次就会在平台上留下两条。
     *
     * @return 规范化后的值；不是我们的来源（三星等）原样返回（它本来就不该被回填）。
     */
    fun normalizeSource(source: String): String = when {
        source.startsWith(OURS_PREFIX) -> source
        FOREIGN_PREFIXES.any { source.startsWith(it) } -> source
        else -> "$OURS_PREFIX$source"
    }

    /**
     * 回填用的稳定幂等键。
     *
     * 格式 `vt/<kind>/<source>/<atMs>`（**全 ASCII**，不用中文 —— 平台侧对 id 字符集
     * 有未文档化的限制，而 id 写坏的表现是"记录查不到"）。
     *
     * 为什么不把值也编进去：同一个时刻的同一条记录，值被修正过（比如重算）时
     * **应该覆盖而不是并存**，所以 id 只由"身份"决定、与值无关。
     */
    fun clientId(kind: String, source: String, atMs: Long): String =
        "vt/$kind/$source/$atMs"

    // ───────────────────────── 2. 单位换算（红线） ─────────────────────────

    /** 厘米 → 米。⚠ HC 的 `HeightRecord` 要的是**米**，我们库里是厘米。 */
    fun cmToMeters(cm: Double): Double = cm / 100.0

    /**
     * 体脂率是否在合法域内（**0~100，不是 0~1**）。
     *
     * ⚠ 阈值**引用 `BodyComposition` 的常量、不在这里另写一遍** ——
     * 同一个"合法体脂率"在两处各定义一次，迟早会漂移（改了体成分那边、忘了回填这边），
     * 而漂移的表现是**静默不写**：界面上显示"回填 0 条"，没有报错。
     *
     * 挡这一道的理由与体成分那边不同：回填是**写进别人的数据库**，
     * 越界值平台侧可能直接拒收，而拒收同样是静默的。
     */
    fun isValidPercent(v: Double?): Boolean =
        v != null && v >= BodyComposition.FAT_RATIO_MIN && v <= BodyComposition.FAT_RATIO_MAX

    /** 体重/瘦体重等质量是否在合法域内（kg）。阈值同 `BodyComposition`。 */
    fun isValidMassKg(v: Double?): Boolean =
        v != null && v >= BodyComposition.PROFILE_WEIGHT_MIN && v <= BodyComposition.PROFILE_WEIGHT_MAX

    /**
     * 身高是否在合法域内（**米**，先过 [cmToMeters]）。
     *
     * ⚠ `BodyComposition` 的阈值是**厘米**（20~300），这里必须 /100 —— 这个除法
     * 就是本函数存在的意义：忘了它，172cm 会被当成 172 米，而 172 > 3.0
     * 只会让它**不写**（看不出是哪里错了）。台架里有一条断言专门钉这个。
     */
    fun isValidHeightMeters(m: Double?): Boolean =
        m != null && m >= BodyComposition.PROFILE_HEIGHT_MIN / 100.0 &&
            m <= BodyComposition.PROFILE_HEIGHT_MAX / 100.0

    /** 基础代谢率是否在合法域内（kcal/天）。阈值同 `BodyComposition`。 */
    fun isValidBmrKcal(v: Double?): Boolean = v != null && v > 0.0 && v <= BodyComposition.BMR_MAX

    /** HRV 的 RMSSD 是否在合法域内（ms）。负数与 0 都是算错，不是"很低"。 */
    fun isValidRmssdMs(v: Double?): Boolean = v != null && v > 0.0 && v <= 500.0

    // ───────────────────────── 3. 明确**不**回填的东西，及其原因 ─────────────────────────

    /**
     * 平台有这类记录、我们也有数据，但**刻意不写** —— 每条都带原因。
     *
     * 这张表的存在本身就是纪律：**「不做」要能说出来，不能只是没做**。
     * 界面上据它显示「这几项为什么不回填」，以后的人想改也能先看到当初的理由。
     */
    val NOT_WRITTEN: List<Pair<String, String>> get() = listOf(
        Text.of("s_08fe60bf") to
            Text.of("s_5ba9fac6") +
            Text.of("s_09c58a2b") +
            Text.of("s_99230581"),
        Text.of("s_002903ad") to
            Text.of("s_1684bc2b") +
            Text.of("s_aca650a6") +
            Text.of("s_6d6882fc"),
        "静息心率" to
            Text.of("s_e0a58f23") +
            Text.of("s_a7ee00a0"),
        Text.of("s_164af88b") to
            Text.of("s_1517b700") +
            Text.of("s_e3b7b94f"),
        Text.of("s_20567069") to
            Text.of("s_ad32029e") +
            Text.of("s_e79f0a9c") +
            Text.of("s_0b2c9226"),
        Text.of("s_10445e65") to
            Text.of("s_5510c76e") +
            Text.of("s_f692633c"),
    )

    // ───────────────────────── 4. 上限 ─────────────────────────

    /** 心率域（bpm）。与 `Spo2.percent()` 同一类红线：越界说明读数本身坏了。 */
    fun isValidHr(bpm: Double?): Boolean = bpm != null && bpm >= 20.0 && bpm <= 250.0

    /** 血氧域（%）。**不是 0~1** —— 本项目在血氧上出过一次域事故。 */
    fun isValidSpo2Percent(p: Double?): Boolean = p != null && p >= 50.0 && p <= 100.0

    /**
     * 单次回填的记录数上限。
     *
     * 不是性能考虑（几千条也能写），是**防呆**：一次写几万条进用户的健康库，
     * 万一映射错了，用户在平台侧手动删起来是灾难。先写最近的这些，
     * 参数可以调，但**必须是有意的**。
     */
    const val MAX_PER_KIND = 500

    // ───────────────────────── 5. 回填计划（纯函数，全部判据可台架测） ─────────────────────────

    /** 记录种类标记。同时是 [clientId] 的一部分与界面上的分项名。 */
    const val KIND_HR = "hr"
    const val KIND_SPO2 = "spo2"
    const val KIND_HRV = "hrv"
    const val KIND_BODY_FAT = "body_fat"
    const val KIND_LEAN_MASS = "lean_mass"
    const val KIND_WEIGHT = "weight"
    const val KIND_HEIGHT = "height"
    const val KIND_BMR = "bmr"

    /**
     * 一条待回填的采样。
     *
     * @param source 来源标记（各表的 `source` 列），要与 [isOurs] 对得上。
     * @param atMs 记录时刻（epoch 毫秒），与 [source] 一起构成幂等键。
     * @param value **原始单位**的值 —— 换算由 [plan] 负责（身高换成米），
     *   调用方**不要**自己先换一遍（那样这道换算就测不到了）。
     */
    data class Sample(val source: String, val atMs: Long, val value: Double)

    /**
     * 某一类的回填计划。
     *
     * 三个计数**必须留着并显示出来**：回填最坏的形态不是报错，而是
     * 「点了按钮、显示写了 12 条」然后用户问"为什么我明明有 300 条"。
     * 有了这三个数，界面就能回答"另有 288 条因为不是本机产生的被跳过"。
     *
     * @param notOurs 因为来源不是我们自己的而跳过的条数
     * @param outOfRange 因为值越界而跳过的条数（越界说明读数坏了）
     * @param truncated 因为超过 [MAX_PER_KIND] 而没有写入的条数（保留的是**最近**的）
     */
    data class KindPlan(
        val kind: String,
        val samples: List<Sample>,
        val notOurs: Int,
        val outOfRange: Int,
        val truncated: Int,
    ) {
        val total: Int get() = samples.size
    }

    /**
     * 从本机库里读出来的原始数据（**纯数据，不带 DB 类型**）。
     *
     * 之所以不直接传 Room 实体：`plan` 要能在离线台架里跑，
     * 而台架没有 androidx.room。这条与本项目「策略层不依赖平台」的做法一致。
     */
    data class BackfillData(
        val heartRate: List<Sample> = emptyList(),
        val spo2: List<Sample> = emptyList(),
        val hrvRmssd: List<Sample> = emptyList(),
        val bodyFat: List<Sample> = emptyList(),
        val leanMass: List<Sample> = emptyList(),
        val weight: List<Sample> = emptyList(),
        val height: List<Sample> = emptyList(),
        val bmr: List<Sample> = emptyList(),
    )

    /**
     * 把本机数据算成**可以照着写**的计划：过滤来源、校验值域、换算单位、截断上限。
     *
     * 这是回填里唯一有判断的部分，所以它被写成纯函数 —— IO 层只负责
     * 「把 [KindPlan.samples] 变成平台记录」，没有任何 if。
     *
     * ⚠ 顺序是有意的：**先换算、再校验**。身高必须先换成米才能用米制的域去判，
     * 反过来（先按厘米判、再换算）会让 172 通过校验然后写成 172 米。
     */
    fun plan(data: BackfillData): List<KindPlan> {
        fun one(
            kind: String,
            raw: List<Sample>,
            ok: (Double) -> Boolean,
            conv: (Double) -> Double = { it },
        ): KindPlan {
            var notOurs = 0
            var outOfRange = 0
            val kept = ArrayList<Sample>(raw.size)
            val seen = HashSet<Long>()
            for (s in raw) {
                if (!isOurs(s.source)) {
                    notOurs++
                    continue
                }
                val v = conv(s.value)
                if (!ok(v)) {
                    outOfRange++
                    continue
                }
                // 同一类里同一个时刻只留一条：幂等键是 (kind, source, atMs)，
                // 而平台上同一类同一时刻的重复会在界面上叠成一个点。
                if (!seen.add(s.atMs)) continue
                kept.add(Sample(s.source, s.atMs, v))
            }
            kept.sortByDescending { it.atMs }
            val over = (kept.size - MAX_PER_KIND).coerceAtLeast(0)
            // 超出上限时保留**最近**的：回填的目的是让别处看到"现在的数据"，
            // 而不是把三年前的旧读数搬一遍。
            return KindPlan(kind, kept.take(MAX_PER_KIND).sortedBy { it.atMs }, notOurs, outOfRange, over)
        }

        return listOf(
            one(KIND_HR, data.heartRate, ::isValidHr),
            one(KIND_SPO2, data.spo2, ::isValidSpo2Percent),
            one(KIND_HRV, data.hrvRmssd, ::isValidRmssdMs),
            one(KIND_BODY_FAT, data.bodyFat, ::isValidPercent),
            one(KIND_LEAN_MASS, data.leanMass, ::isValidMassKg),
            one(KIND_WEIGHT, data.weight, ::isValidMassKg),
            // ⚠ 身高：库里的值**是厘米**，这里换一次；域校验按**米**。
            one(KIND_HEIGHT, data.height, ::isValidHeightMeters) { cmToMeters(it) },
            one(KIND_BMR, data.bmr, ::isValidBmrKcal),
        )
    }
}
