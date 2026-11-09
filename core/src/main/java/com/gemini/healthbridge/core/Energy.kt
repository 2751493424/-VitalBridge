package com.gemini.healthbridge.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 能量评分（自研，2026-09-19 建立 / 2026-09-24 扩到 10 分量）：把**昨晚的恢复 +
 * 昨天白天的活动**折算成一个 0~100 的分，给"今天"用。
 *
 * ## 为什么自己算一个
 * 三星健康有一个同名的「能量评分」，但它是三星用自己那套模型算的，
 * 口径不公开、要求 Galaxy Watch7 以上 + 三星账号、而且**只在部分国家/地区提供**
 * 。也就是说：在没有这个功能的机器上，用户手里一个数都没有。
 *
 * 更根本的理由是**可解释性**：三星那个分只有一个数字，你不知道它为什么是 62。
 * 这里的分把每个分量、各自的刻度、各自的实际值与基线**全部摊开**，
 * 而且只用**手表确实采到、并且存在本机**的东西 —— 不依赖任何云端服务。
 *
 * ## 分量清单与刻度（**每一项都可倒推**，台架逐条钉住）
 *
 * | # | 分量 | 刻度 | 锚点 | 权重 |
 * |---|---|---|---|---|
 * | 1 | 平均睡眠时间 | 绝对 | 4h→0、7h→50、9h→100 | 18% |
 * | 2 | 睡眠期最低血氧 | 绝对 | 88%→0、95%→100 | 12% |
 * | 3 | 睡眠心率 | 相对自身 | 与基线持平→50，每 6% = ±25 | 12% |
 * | 5 | 睡眠期间皮肤温度 | 绝对 | 偏差 0.3℃→100、1.5℃→0 | 8% |
 * |   | ↳ ⚠ 输入的**口径按量级判**（绝对温度 / 相对偏差），见 [resolveSkinDev] | | | |
 * | 6 | 睡眠时间一致性 | 相对自身 | 与平时差 ≤20min→100，每 20min −25 | 8% |
 * | 7 | 睡眠规律性 | 绝对 | 近 14 晚入睡极差 ≤30min→100，每 30min −25 | 8% |
 * | 8 | 睡眠定时 | 绝对 | ≤24:00→100，之后每 30min −20 | 8% |
 * | 9 | 前一日的活动 | 绝对 | 达到步数目标→100，线性 | 10% |
 * | 10 | 活动持续性 | 绝对 | 每个"活跃"时段 +20，≥5 段满 | 8% |
 *
 * 权重合计 **92%**（各项是工程分配、未做临床标定，见 [WEIGHT_NOTE]）——
 * ⚠ 总分是**参与项**的加权平均（缺席项不进分母），所以合计不等于 100% 不影响结果。
 * 2026-10-13 删掉了原第 4 项「睡眠心率变化」：它的输入是睡眠期心率的**逐点标准差**，
 * 而逐点序列不落库 ⇒ 这一格长期显示「未参与」（纯占位）；改用「整晚最高−最低」代替，
 * 又会被入睡前/清醒段的心率污染（「能拿到」≠「该用」）。用户确认：「如果不行，就删掉」。
 *
 * ### 三种刻度并存，不是随手分的
 *
 * - **绝对**：睡眠时长、最低血氧、皮温偏差、睡眠定时、前一日的活动、活动持续性 ——
 *   它们都有**人群层面的参考线**（7 小时、95%/88%、±0.3℃、24:00、步数目标、时段覆盖），
 *   不随个人而变，所以第一晚就能算。
 * - **相对自身**：睡眠心率、睡眠时间一致性 ——
 *   静息心率 55 与 75 都可能是某个人的常态，只有跟他自己比才有意义。
 * - **睡眠规律性**是个例外：它**本身就是"跨晚的离散度"**，所以虽然要攒历史，
 *   刻度却是绝对的（极差 ≤30 分钟就是规律，与"你平时怎么样"无关）。
 *
 * ### 方向要多看两眼（这几项最容易搞反）
 *
 * - 睡眠心率、心率变化：**低于**基线 = 恢复更好 = 分更高。
 * - 皮温偏差、睡眠时间一致性：**偏离**越小 = 分越高（两侧都扣，早睡晚睡一样扣）。
 * - 睡眠定时：**只对晚睡扣分**，早睡不扣（"太早上床"不是这个分要管的事）。
 * - **与 [Stress] 的分方向相反**：那边心率偏高 = 负荷更大 = 分更高。
 *   两个分的 50 分含义不同，界面必须分别写明。
 *
 * ## 口径变更：2026-09-24 起含白天活动
 *
 * 早期版本只衡量"夜里的恢复"、明确**不含白天活动量**（那时加速度只有打开 App 期间才采）。
 * 现在有七项睡眠相关的量 + 三项活动相关的量，活动那三项来自**三星健康的步数聚合**
 * （不是我们自己的加速度采样），而且实测确认聚合算子**能读历史日期**，
 * 可以按日期回补 —— "漏开一天就永久缺一天"这个限制不存在。
 *
 * 仍然不含主观感受（困不困只有用户自己知道），[DISCLAIMER] 里写明了。
 *
 * ## 分量缺席怎么办
 *
 * **按权重归一，绝不往 50 拉**（与 [Stress.combine] 同一条）：少一个分量就把它的权重
 * 从分母里去掉，而不是凭空塞一个"刚够"的分量进去。每个缺席的分量都出一条
 * [Result.Ok.caveats]，并区分**两种不同的缺席原因**：
 * "还差 N 晚就有"（基线不够）与"这一晚压根没测到"（没有数据）——
 * 前者再多戴两晚就有，后者那一晚补不回来，用户要做的事完全不同。
 *
 * **十个输入全无才拒答。**
 */
object Energy {

    // ═══════════════════════ 输入 ═══════════════════════

    /**
     * 一晚（以**醒来那天**为归属日）+ 这一晚之前那个白天的活动量。
     *
     * 每个量都可空，且**空 = 没测到，不是 0**：三星健康可能只有睡眠时段而没开
     * 血氧/心率连续监测，昨天也可能没带手机出门。用 0 顶替会让"没测到"变成一个
     * 真实的测量值参与评分（"最低血氧 0%"、"睡了 0 小时"、"昨天一步没走"），
     * 那是最难发现的一类错。
     *
     * @param dateEpochDay 醒来那天的 epochDay（与 `SleepOdiEntity` 同一口径）。
     * @param sleepMs 实际睡眠时长（毫秒，已扣除清醒段）。
     * @param hrAvg 睡眠期平均心率（次/分）。
     * @param spo2Min 睡眠期最低血氧（%）。
     * @param bedMs **入睡时刻**（毫秒）。一致性 / 规律性 / 定时三项都只用它，
     *   所以它缺席时那三项一起缺席（不要用起床时刻顶替：两者含义不同）。
     * @param hrSd 睡眠期心率的**标准差**。算它必须有逐点序列，而序列是现读不落库的
     *   （三星只给得到最近一晚），所以在读到那晚顺手落库 —— 见 `sleep_nights.hrSd`。
     * @param skinTempDev 三星健康夜间皮肤温度的**原始读数**（℃）。
     *
     *   ⚠⚠ **它的口径不固定，取决于机型/三星版本**（三星没写明，2026-09-29 实测本机给的是
     *   **绝对温度** 30~36℃ 那一档，而不是假设的"相对基线偏差"）。
     *   所以这里**不再自己猜口径**（旧实现要求 |v| ≤ 8℃ 才算偏差，本机由于给的是绝对温度，
     *   这一项**从来没有参与过评分** —— 界面上永远显示"未参与"，而用户能看到
     *   睡眠页那张"夜间皮肤温度"卡里明明有数据）。
     *   判据交给 [resolveSkinDev]：偏差档直接用、绝对档拿个人基线中位数转成偏差。
     * @param prevDaySteps **前一日白天**的步数（不是这一晚的）。
     * @param prevDaySlots 前一日按 [ACTIVITY_SLOTS] 段切分的步数，用于算活动持续性。
     * @param samsungScore 三星自己的睡眠得分（只用于对照展示，**不参与本模块的计算**）。
     */
    data class Night(
        val dateEpochDay: Long,
        val sleepMs: Long? = null,
        val hrAvg: Double? = null,
        val spo2Min: Int? = null,
        val bedMs: Long? = null,
        val hrSd: Double? = null,
        val skinTempDev: Float? = null,
        val prevDaySteps: Long? = null,
        val prevDaySlots: List<Long>? = null,
        val samsungScore: Int? = null,
    ) {
        /**
         * 睡眠时长（小时）。<= 0 视为**缺席**而不是"睡了 0 小时"：
         * 真实的 0 小时睡眠在这套数据里不可能出现（读不到会话时我们根本不写那一行，
         * 见 `SleepNightEntity` 的说明），出现 0 只可能是脏数据。
         */
        val sleepHours: Double? get() = sleepMs?.takeIf { it > 0L }?.let { it / 3_600_000.0 }

        /** 可用于评分的夜间平均心率。<= 0 视为缺席。 */
        val usableHr: Double? get() = hrAvg?.takeIf { it > 0.0 }

        /**
         * 可用于评分的睡眠期心率标准差。> 0 才算 —— SD 为 0 表示"整晚心率一个值"，
         * 那是采集异常而不是"心率极其稳定"，拿它去比基线会得一个虚高的分。
         */
        val usableHrSd: Double? get() = hrSd?.takeIf { it > 0.0 }

        /**
         * 可用于评分的最低血氧。
         *
         * 超出可信区间（[Odi.MIN_PLAUSIBLE]~[Odi.MAX_PLAUSIBLE]，与 ODI 模块同一组界）
         * 的值**直接剔除**而不是夹到边界：一个 20% 的读数只可能是传感器没贴合，
         * 夹成 50 会让它变成一个"可疑但可用"的值，进而把一个坏读数写进分数。
         */
        val usableSpo2: Int? get() = spo2Min?.takeIf { it in Odi.MIN_PLAUSIBLE..Odi.MAX_PLAUSIBLE }

        /**
         * 可用于评分的皮温原始读数（℃）。**不做口径判断**，只统一挡掉非数与超范围值。
         *
         * 口径判定（绝对 / 偏差）见 [skinKindOf] 与 [resolveSkinDev] ——
         * 因为"绝对温度"那一档要拿个人基线中位数才能转成偏差，而基线不在这个类里。
         *
         * 物理区间用 [SKIN_ABS_MIN_C]~[SKIN_ABS_MAX_C]（宽松）：它要同时容纳
         * 偏差档（±几个度）与绝对档（30~36℃），真正的口径歧义交给 [skinKindOf]。
         */
        val skinRaw: Float? get() = skinTempDev?.takeIf { !it.isNaN() && abs(it) <= SKIN_ABS_MAX_C }

        /**
         * 这个读数是**绝对温度**还是**相对基线的偏差**？
         *
         * 判据是量级（三星官方没写明口径，只能按数值判断）：
         * - 落在 [SKIN_ABS_MIN_C]~[SKIN_ABS_MAX_C]（25~45℃）→ 绝对温度
         * - |v| ≤ [SKIN_DEV_MAX_PLAUSIBLE]（8℃）→ 相对偏差
         * - 其它（如 12.0）→ `null` = **判不出来**，此时该分量**拒答而不是猜**
         *   （12.0 塞进任何一档都会给出一个看着合理的错值，与 `Temperature.scaleOf` 同款）。
         *
         * ⚠ 两档重叠区（25~45℃ 与 |v|≤8 的交集 25~8，实际为空）不存在，所以判据无歧义；
         * 但**必须按"先绝对后偏差"的顺序判**：一个 30.0 的值若先按偏差档看会被判成 30℃ 偏差。
         */
        val skinKind: SkinKind?
            get() {
                val v = skinRaw ?: return null
                return when {
                    v in SKIN_ABS_MIN_C..SKIN_ABS_MAX_C -> SkinKind.ABSOLUTE
                    abs(v) <= SKIN_DEV_MAX_PLAUSIBLE -> SkinKind.DEVIATION
                    else -> null
                }
            }

        /** 可用于评分的入睡时刻（相对 18:00 的分钟数）。 */
        val bedClockMin: Int? get() = bedMs?.let { bedClockOf(it) }
    }

    /**
     * 皮温读数的口径。三星官方没写明，只能按量级判 ——
     * 判不出来时返回 null 并让该分量拒答，**绝不二选一硬塞**。
     */
    enum class SkinKind { ABSOLUTE, DEVIATION }

    // ═══════════════════════ 刻度 ═══════════════════════

    // ── ① 睡眠时长 ──

    /** 睡眠时长：50 分对应的时长（小时）。取多项指南的推荐下限。 */
    const val SLEEP_FAIR_H = 7.0

    /** 睡眠时长：100 分对应的时长（小时）。推荐区间的上端；再长不额外加分。 */
    const val SLEEP_GOOD_H = 9.0

    /** 睡眠时长：低于这个小时数一律 0 分。 */
    const val SLEEP_MIN_H = 4.0

    /** 睡眠时长不足侧：每少 1 小时扣多少分（比充足侧陡，见类注释）。 */
    const val SLEEP_PENALTY_PER_H = 15.0

    /** 睡眠时长充足侧：每多 1 小时加多少分（= (100 − 50) / ([SLEEP_GOOD_H] − [SLEEP_FAIR_H])）。 */
    const val SLEEP_GAIN_PER_H = 25.0

    // ── ② 最低血氧 ──

    /** 最低血氧：这个值及以上记满分。 */
    const val SPO2_FULL_PCT = 95

    /** 最低血氧：这个值及以下记 0 分。 */
    const val SPO2_ZERO_PCT = 88

    // ── ③④ 心率与心率变化 ──

    /** 一个"显著偏离单位"对应多少分。与 [Stress.BAND] 同值，两张表读起来才一致。 */
    const val BAND = 25.0

    /** 夜间平均心率的一个显著偏离单位 = 相对基线变化 6%（个体内日间变异 5~8% 的中间值）。 */
    const val UNIT_HR_NIGHT = 0.06

    /** 夜间心率标准差的一个显著偏离单位 = 相对基线 25%。比均值宽松：SD 本身更不稳。 */

    // ── ⑤ 皮肤温度 ──

    /** 皮温偏差 ≤ 这个值记满分（℃）。0.3℃ 是夜间正常波动范围。 */
    const val SKIN_DEV_FULL_C = 0.3f

    /** 皮温偏差 ≥ 这个值记 0 分（℃）。1.5℃ 已属明显的发热/环境异常。 */
    const val SKIN_DEV_ZERO_C = 1.5f

    /**
     * 皮温偏差的可信上界（℃）。超过它就**不当作偏差**而是口径判不出来
     * （很可能是绝对温度），该分量不参与 —— 见 [Night.skinKind]。
     */
    const val SKIN_DEV_MAX_PLAUSIBLE = 8.0f

    /**
     * 皮温读数的**物理区间下界**（℃）。
     *
     * 为什么下界取 25 而不是 `Temperature.isValid` 那个 10：这里要判的是
     * "这个数是不是绝对温度"。腕部皮温在 25℃ 以下基本只可能是传感器没贴合/被环境拉低，
     * 而放宽到 10 会让 12.0 这种**歧义值**（既可能是偏差、也可能是坏读数）落进绝对档，
     * 从而给出一个看着合理的错值。判不出来就拒答，比猜一个更好。
     */
    const val SKIN_ABS_MIN_C = 25.0f

    /**
     * 皮温读数的**物理区间上界**（℃）。也当 [Night.skinRaw] 的超范围挡板用。
     *
     * 取 45（与 `Temperature.isValid` 的上界一致）：皮肤温度不可能更高，
     * 更高的值只可能是"绝对体温"被误当皮温。
     */
    const val SKIN_ABS_MAX_C = 45.0f

    /** 算绝对温度档的"个人基线"（皮温中位数）至少要几晚。与 [MIN_BASELINE_NIGHTS] 同值。 */
    const val MIN_SKIN_BASELINE_NIGHTS = 3

    // ── ⑥ 睡眠时间一致性 ──

    /** 与平时入睡时刻相差不超过这么多分钟不扣分。 */
    const val CONSIST_FREE_MIN = 20

    /** 超过免罚区后，每多这么多分钟扣 25 分。 */
    const val CONSIST_PENALTY_MIN = 20

    // ── ⑦ 睡眠规律性 ──

    /** 近若干晚入睡时刻极差不超过这么多分钟记满分。 */
    const val REGULAR_FREE_MIN = 30

    /** 超过免罚区后，每多这么多分钟的极差扣 25 分。 */
    const val REGULAR_PENALTY_MIN = 30

    // ── ⑧ 睡眠定时 ──

    /**
     * 入睡时刻的记录原点：**以 18:00 为 0 分**。
     *
     * 这样 23:30 → 330、00:30 → 390，跨午夜不会变成负数 ——
     * 与 `SleepHours` 为什么用"入睡原点"是同一个理由。
     */
    const val BED_ORIGIN_MIN = 18 * 60

    /** 相对 18:00 的分钟数 **≤ 这个值**（= 24:00）不扣分：早睡不受罚。 */
    const val TIMING_FULL_MIN = 6 * 60

    /** 晚于 24:00 之后，每这么多分钟扣 [TIMING_STEP] 分。 */
    const val TIMING_PENALTY_MIN = 30

    /** 晚睡的扣分步长（分/每 [TIMING_PENALTY_MIN] 分钟）。比其它项陡：晚睡影响明确。 */
    const val TIMING_STEP = 20

    // ── ⑨ 前一日活动 ──

    /**
     * 步数目标的最小有效值。
     *
     * ⚠ 我们**不替用户编一个目标**（那会变成"我们觉得你应该走一万步"，是越权）。
     * 目标为空时这一项**缺席**，由 caveat 告诉用户"在三星健康里设个步数目标就能算这一项"。
     * 这个常量只用来挡住 `goal = 0` 这种脏数据导致的除零。
     */
    const val MIN_STEP_GOAL = 1

    // ── ⑩ 活动持续性 ──

    /** 前一日切成几段（每段 4 小时）。6 段足够看出"活动集中在某一时段还是铺开全天"。 */
    const val ACTIVITY_SLOTS = 6

    /** 一段里达到这么多步才算"这个时段有活动"。 */
    const val SLOT_ACTIVE_STEPS = 500L

    /** 每有一个活跃时段加多少分；≥5 段即满分（人不可能 24 小时都在活动）。 */
    const val SLOT_SCORE_PER = 20

    // ═══════════════════════ 权重（工程分配，未经临床标定）═══════════════════════

    const val W_SLEEP = 0.18f
    const val W_SPO2 = 0.12f
    const val W_HR = 0.12f
    const val W_SKIN = 0.08f
    const val W_CONSIST = 0.08f
    const val W_REGULAR = 0.08f
    const val W_TIMING = 0.08f
    const val W_PREV_ACTIVITY = 0.10f
    const val W_ACTIVITY_SPREAD = 0.08f

    // ═══════════════════════ 基线 ═══════════════════════

    /** 构造基线取最近多少晚。两周：够稳，又不至于把上个月的旅行/生病带进"平时"。 */
    const val BASELINE_NIGHTS = 14

    /** 基线成立所需的最少晚数。3 晚是"中位数能抗住 1 个离群值"的下限。 */
    const val MIN_BASELINE_NIGHTS = 3

    /** 基线偏薄（少于这个晚数）时要在界面上说一句。 */
    const val THIN_BASELINE_NIGHTS = 5

    // ═══════════════════════ 分级 ═══════════════════════

    object Level {
        /** < 50：这一夜的恢复明显不够。 */
        const val LOW = "low"

        /** 50~74：刚够。 */
        const val FAIR = "fair"

        /** >= 75：恢复良好。 */
        const val GOOD = "good"
    }

    /** 分级边界。与 [Stress] 的分档**不复用**：那边的 50 是"与基线持平"，这里是"每个分量都刚够"。 */
    const val LEVEL_FAIR_MIN = 50.0
    const val LEVEL_GOOD_MIN = 75.0

    fun levelOf(score: Double): String = when {
        score < LEVEL_FAIR_MIN -> Level.LOW
        score < LEVEL_GOOD_MIN -> Level.FAIR
        else -> Level.GOOD
    }

    fun levelLabel(level: String): String = when (level) {
        Level.LOW -> Text.of("s_002967bb")
        Level.FAIR -> Text.of("s_00286c2c")
        Level.GOOD -> Text.of("s_0043184a")
        else -> Text.of("s_c34c0523")
    }

    /** 分量归属的大类，界面按它分组显示（睡眠那一组 / 白天活动那一组）。 */
    object Kind {
        const val SLEEP = "sleep"
        const val ACTIVITY = "activity"
    }

    /**
     * 十个分量的显示名，**顺序就是界面宫格的顺序**。
     *
     * 为什么要有这个清单：界面那十个格子必须遍历**全部分量**而不是只遍历参与的 ——
     * 缺席的项也要占一格（显示"未参与"，原因在 caveats 里），
     * 否则用户根本不知道这个分本来还有哪几项。**"少了什么"比"有什么"更需要看得见。**
     *
     * ⚠ 这些字符串必须与 [analyze] 里构造 [Part] 时用的名字**逐字一致**：
     * 差一个字，那一格就永远显示"未参与"，而且**不报任何错**。
     * 台架里有一条断言专门比这两个集合 —— 改名字时它会拦下来。
     */
    val COMPONENT_NAMES = listOf(
        "平均睡眠时间", "睡眠期最低血氧",
        "睡眠心率",
        "睡眠期间皮肤温度", "睡眠时间一致性",
        "睡眠规律性", "睡眠定时",
        "前一日的活动", "活动持续性",
    )

    /**
     * 分量名 → `Text.kt` 的 key。**与 [COMPONENT_NAMES] 一一对应**（台架会断言这个集合）。
     *
     * ## 为什么名字与显示名要分开
     *
     * [Part.name] 存的是**中文原名**，它是**稳定键**：`analyze` 里构造 `Part` 用的名字
     * 必须与 [COMPONENT_NAMES] 逐字一致（差一个字那一格就永远显示"未参与"，且**不报错**），
     * 台架也靠它对齐。所以**不能直接改中文**让它多语言 —— 那会同时改掉匹配键。
     * 显示时走 [componentLabel]，把中文原名换成当前语言。
     *
     * ⚠⚠ 界面**必须**走 [componentLabel]，不能直接显示 `part.name` ——
     * 直接显示的话切到英文/韩文时这 10 个标签仍是中文，而
     * `translate.py --rescan` **扫不出来**（它不是源码里的字面量，是运行期拼的）。
     */
    private val COMPONENT_LABEL_KEYS: Map<String, String> = mapOf(
        "平均睡眠时间" to "s_b5ddfdd1",
        "睡眠期最低血氧" to "s_7482300d",
        "睡眠心率" to "s_1e75792b",
        "睡眠期间皮肤温度" to "s_1525ee2b",
        "睡眠时间一致性" to "s_6162c32e",
        "睡眠规律性" to "s_a112e43d",
        "睡眠定时" to "s_1e734a9f",
        "前一日的活动" to "s_fb10b6bb",
        "活动持续性" to "s_8fd626da",
    )

    /**
     * 分量的**显示名**（当前语言）。认不出来的名字原样返回 ——
     * 宁可显示中文原名，也不要在界面上出现一个空白格。
     */
    fun componentLabel(name: String): String =
        COMPONENT_LABEL_KEYS[name]?.let { Text.of(it) } ?: name

    /** [componentLabel] 用到的全部 key（**台架**：核对与 [COMPONENT_NAMES] 一一对应）。 */
    val COMPONENT_LABEL_KEYS_FOR_TEST: Map<String, String> get() = COMPONENT_LABEL_KEYS

    // ═══════════════════════ 结果 ═══════════════════════

    /** 一个分量的完整分解（界面据此说明"这个分数是怎么来的"）。 */
    data class Part(
        val name: String,
        /** 归一化后的权重（参与的分量相加 = 1）。 */
        val weight: Float,
        /** 这一晚的实际值，已带单位。 */
        val currentText: String,
        /** 参照值。**绝对刻度的分量为 null**（它没有基线，参照是一条固定的线）。 */
        val baselineText: String?,
        /** 该分量单独的得分（0~100）。 */
        val componentScore: Int,
        /** 偏离了几个显著单位；绝对口径的分量为 null（没有"单位"这个概念）。 */
        val devUnits: Double?,
        /** true = 绝对刻度（睡眠时长 / 最低血氧 / 定时 …）；false = 相对自身基线。 */
        val absolute: Boolean,
        /** [Kind.SLEEP] 或 [Kind.ACTIVITY]，界面分组用。 */
        val kind: String = Kind.SLEEP,
    )

    /** 基线：这个人"平时晚上是什么样"。 */
    data class Baseline(
        /** 参与构造的晚数（== 有记录的最近 [BASELINE_NIGHTS] 晚）。 */
        val count: Int,
        /** 睡眠心率的中位数；不足 [MIN_BASELINE_NIGHTS] 晚则为 null。 */
        val hrMed: Double?,
        /** 有睡眠期**心率**的晚数（判断"还差几晚"要用它，不是 [count]）。 */
        val hrCount: Int,
        /** 睡眠期**心率标准差**的中位数。 */
        val hrSdMed: Double?,
        /** 有睡眠期**心率标准差**的晚数。与 [hrCount] 分开判：旧记录没有这一项。 */
        val hrSdCount: Int,
        /** 入睡时刻（相对 18:00 的分钟）的中位数。 */
        val bedClockMed: Int?,
        /** 有入睡时刻的晚数。 */
        val bedCount: Int,
        /**
         * 最近若干晚入睡时刻的**极差**（分钟）。用来算"睡眠规律性"。
         *
         * 用极差而不是标准差：与 `Cycle` 用"周期长度极差"判规律是同一条理由 ——
         * 极差**可倒推、可解释**（"你最早 22:40 睡、最晚 01:10 睡，差 2.5 小时"），
         * 而 SD 说不成人话。
         */
        val bedClockRange: Int?,
        /** 睡眠时长的中位数（小时）。**只用于展示对照，不参与分数**。 */
        val sleepMedHours: Double?,
        /** 最低血氧的中位数（%）。同样只用于展示对照。 */
        val spo2Med: Int?,
        /**
         * **绝对温度档**下的皮温个人基线中位数（℃）。2026-09-29 新增。
         *
         * 为什么要它：本机实测三星给的是**绝对皮温**（30~36℃），而评分要的是
         * "相对你自己平时的偏差"。给不出基线时这一档**拒答**（不用 0 或某个常数顶替 ——
         * 那会让"比平时高 0.5℃"变成一句没有依据的话）。
         * 只统计[SkinKind.ABSOLUTE]档的那些夜，不与偏差档混（两套口径不许混）。
         */
        val skinMedC: Double?,
        /** 有绝对温度档皮温的晚数（判断"还差几晚"用它）。 */
        val skinCount: Int,
    )

    sealed interface Result {

        /**
         * 给出评分。
         *
         * @param dateEpochDay **算的是哪一晚**（醒来那天）。界面必须把它显示出来 ——
         *   用户手上只有一个数字时，最容易犯的错就是把它当成"现在的状态"。
         * @param staleDays 距今几天（0 = 就是今天早上那一晚）。负数 = 记录日期在未来
         *   （时区或系统时间异常），界面要按日期显示而不是说"0 天前"。
         * @param samsungScore 三星自己的睡眠得分（原样带出，可能为 null）。
         *   注意它**与本分数不是同一把尺子**（三星的是睡眠得分，界面别把它当"另一个能量评分"）。
         */
        data class Ok(
            val dateEpochDay: Long,
            val staleDays: Long,
            val score: Int,
            val level: String,
            val parts: List<Part>,
            val baseline: Baseline,
            val samsungScore: Int?,
            /** 这次谁没参与、为什么、以及需要注意的事。**永远有话说**。 */
            val caveats: List<String>,
        ) : Result

        /** 算不出来。[reason] 是直接给用户看的一句话。 */
        data class NotApplicable(val reason: String) : Result
    }

    // ═══════════════════════ 主入口 ═══════════════════════

    /**
     * 算某一晚的能量分。
     *
     * @param latest 要评的那一晚（通常 = 最近一晚，且应带上它前一日的活动量）。
     * @param history **全部**历史晚（可以包含 [latest]，内部按日期排除）。
     *   只用**比它更早**的晚做基线 —— 拿"未来"的夜当基线是一种隐蔽的错误
     *   （用户回看旧记录时会发现那个分数变了）。
     * @param todayEpochDay 今天（判断这个分数是不是"今天的"）。
     * @param stepGoal 三星健康里的**步数目标**。为 null 表示用户没设过 ——
     *   那么"前一日的活动"这一项**缺席**（我们不会替他编一个目标）。
     */
    fun analyze(
        latest: Night,
        history: List<Night>,
        todayEpochDay: Long,
        stepGoal: Int? = null,
    ): Result {
        val past = history.asSequence()
            .filter { it.dateEpochDay < latest.dateEpochDay }
            .sortedByDescending { it.dateEpochDay }
            .distinctBy { it.dateEpochDay }
            .take(BASELINE_NIGHTS)
            .toList()
        val baseline = buildBaseline(past)

        val caveats = ArrayList<String>()
        val raw = ArrayList<Part>(10)

        // ── ① 平均睡眠时间（绝对）──
        val hours = latest.sleepHours
        if (hours != null) {
            raw += Part(
                name = "平均睡眠时间",
                weight = W_SLEEP,
                currentText = Text.fmt("s_c6cc9e85", fmt1(hours)),
                baselineText = baseline.sleepMedHours?.let { Text.fmt("s_590b6b5b", fmt1(it)) },
                componentScore = sleepScore(hours),
                devUnits = null,
                absolute = true,
            )
        } else if (latest.sleepMs != null) {
            caveats += Text.fmt("s_164bcab3", latest.sleepMs)
        }

        // ── ② 睡眠期最低血氧（绝对）──
        val spo2 = latest.usableSpo2
        if (spo2 != null) {
            raw += Part(
                name = "睡眠期最低血氧",
                weight = W_SPO2,
                currentText = "$spo2%",
                baselineText = baseline.spo2Med?.let { "$it%" },
                componentScore = spo2Score(spo2),
                devUnits = null,
                absolute = true,
            )
        } else if (latest.spo2Min != null) {
            caveats += Text.fmt("s_7d3bc632", latest.spo2Min) +
                Text.fmt("s_bfafc867", Odi.MIN_PLAUSIBLE, Odi.MAX_PLAUSIBLE)
        }

        // ── ③ 睡眠心率（相对自身）──
        val hr = latest.usableHr
        val baseHr = baseline.hrMed
        if (hr != null && baseHr != null) {
            raw += Part(
                name = "睡眠心率",
                weight = W_HR,
                currentText = Text.fmt("s_5af5d5d1", hr.roundToInt()),
                baselineText = Text.fmt("s_d5d781ee", baseHr.roundToInt()),
                componentScore = hrScore(hr, baseHr),
                devUnits = (baseHr - hr) / baseHr / UNIT_HR_NIGHT,
                absolute = false,
            )
        } else if (hr != null) {
            val need = (MIN_BASELINE_NIGHTS - baseline.hrCount).coerceAtLeast(1)
            caveats += Text.fmt("s_d1fb6d13", need) +
                Text.fmt("s_142e4e32", MIN_BASELINE_NIGHTS, baseline.hrCount)
        } else {
            caveats += Text.of("s_03f2666a") +
                Text.of("s_0bfd6c0a")
        }

        // ── ⑤ 睡眠期间皮肤温度（绝对刻度：偏差 0.3℃→100、1.5℃→0）──
        //
        // ⚠⚠ 2026-09-29 修：本机三星给的是**绝对皮温**（30~36℃），旧的
        //   `usableSkinDev`（要求 |v| ≤ 8）把它全判成"量纲判不出来"，于是这一项
        //   **从来没有参与过评分** —— 界面永远显示"未参与"，而体温页那张夜间皮温卡里
        //   明明有数据（用户截图反馈）。现在按口径分开处理：
        //     · 偏差档 → 直接就是偏差，用；
        //     · 绝对档 → 拿**个人基线中位数**转成偏差，用；
        //     · 判不出档（如 12.0）→ 拒答这句照旧。
        when (val skin = resolveSkinDev(latest, baseline)) {
            is SkinResolved.Ok -> {
                raw += Part(
                    name = "睡眠期间皮肤温度",
                    weight = W_SKIN,
                    currentText = skinTempText(skin.devC.toFloat()),
                    // 绝对档才给参照值（"你自己的基线 35.2℃"）；偏差档没有绝对值可写。
                    baselineText = skin.baselineText,
                    componentScore = skinTempScore(skin.devC.toFloat()),
                    devUnits = null,
                    absolute = true,
                )
            }
            is SkinResolved.Missing -> {
                if (skin.note != null) caveats += skin.note
            }
        }

        // ── ⑥⑦⑧ 睡眠时间一致性 / 规律性 / 定时（都只用入睡时刻）──
        val bedClock = latest.bedClockMin
        if (bedClock == null) {
            caveats += Text.of("s_c0fe32cf")
        } else {
            // ⑥ 一致性：这一晚 vs 你平时
            val bedMed = baseline.bedClockMed
            if (bedMed != null) {
                raw += Part(
                    name = "睡眠时间一致性",
                    weight = W_CONSIST,
                    currentText = clockText(bedClock),
                    baselineText = clockText(bedMed),
                    componentScore = consistencyScore(bedClock - bedMed),
                    devUnits = null,
                    absolute = false,
                )
            } else {
                caveats += Text.of("s_6f73fa57") +
                    Text.fmt("s_ad6219cf", (MIN_BASELINE_NIGHTS - baseline.bedCount).coerceAtLeast(1))
            }

            // ⑦ 规律性：跨晚的极差（绝对刻度，见类注释）
            val range = baseline.bedClockRange
            if (range != null) {
                raw += Part(
                    name = "睡眠规律性",
                    weight = W_REGULAR,
                    currentText = Text.fmt("s_c72ec742", baseline.bedCount, minutesText(range)),
                    baselineText = null,
                    componentScore = regularityScore(range),
                    devUnits = null,
                    absolute = true,
                )
            } else {
                caveats += Text.fmt("s_59efdef3", MIN_BASELINE_NIGHTS) +
                    Text.fmt("s_5c520706", baseline.bedCount)
            }

            // ⑧ 定时：绝对窗口，只看晚不晚
            raw += Part(
                name = "睡眠定时",
                weight = W_TIMING,
                currentText = clockText(bedClock),
                baselineText = null,
                componentScore = timingScore(bedClock),
                devUnits = null,
                absolute = true,
            )
        }

        // ── ⑨ 前一日的活动（绝对，相对步数目标）──
        val steps = latest.prevDaySteps
        if (steps != null && stepGoal != null && stepGoal >= MIN_STEP_GOAL) {
            raw += Part(
                name = "前一日的活动",
                weight = W_PREV_ACTIVITY,
                currentText = Text.fmt("s_ea86aeb8", steps),
                baselineText = Text.fmt("s_3e6bc4e9", stepGoal),
                componentScore = prevActivityScore(steps, stepGoal),
                devUnits = null,
                absolute = true,
                kind = Kind.ACTIVITY,
            )
        } else if (steps != null) {
            caveats += Text.of("s_d24ab246") +
                Text.of("s_1946bb13")
        } else {
            caveats += Text.of("s_7e561ca2")
        }

        // ── ⑩ 活动持续性（绝对，看活动覆盖了几个时段）──
        val slots = latest.prevDaySlots
        if (slots != null && slots.size == ACTIVITY_SLOTS) {
            raw += Part(
                name = "活动持续性",
                weight = W_ACTIVITY_SPREAD,
                currentText = Text.fmt("s_66456820", slots.count { it >= SLOT_ACTIVE_STEPS }, ACTIVITY_SLOTS),
                baselineText = null,
                componentScore = activitySpreadScore(slots),
                devUnits = null,
                absolute = true,
                kind = Kind.ACTIVITY,
            )
        } else if (slots != null) {
            caveats += Text.fmt("s_45f04e6b", slots.size, ACTIVITY_SLOTS)
        }

        if (raw.isEmpty()) {
            return Result.NotApplicable(
                Text.fmt("s_10cfc4bb", dateLabel(latest.dateEpochDay)) +
                    Text.of("s_4c446b6a") +
                    Text.of("s_0106b64c") +
                    Text.of("s_6f4833e9") +
                    Text.of("s_9d3b0aad"),
            )
        }

        // 分量缺席时按权重归一（与 Stress.combine 同一条）：不能因为少了一个分量
        // 就把总分往 50 拉 —— 那等于凭空塞了一个"刚够"的分量进去。
        val wSum = raw.sumOf { it.weight.toDouble() }
        val parts = raw.map { it.copy(weight = (it.weight / wSum).toFloat()) }
        val score = parts.sumOf { it.componentScore * it.weight.toDouble() }
            .roundToInt().coerceIn(0, 100)

        // 分数一旦看起来"太平"，往往是因为只有少数分量在撑着 —— 必须说出来
        if (parts.size <= 2) {
            caveats += Text.fmt("s_0f5978a1", parts.size) +
                Text.fmt("s_bf3b4434", parts.joinToString(Text.lang.itemSeparator()) { it.name }) +
                Text.of("s_6ce3380e")
        } else if (parts.size < 6) {
            caveats += Text.fmt("s_db0bbd48", parts.size)
        }
        if (baseline.hrMed != null && baseline.hrCount < THIN_BASELINE_NIGHTS) {
            caveats += Text.fmt("s_4faccfa0", baseline.hrCount) +
                Text.fmt("s_6066f410", THIN_BASELINE_NIGHTS)
        }
        if (baseline.count in 1 until MIN_BASELINE_NIGHTS) {
            caveats += Text.fmt("s_b0e1efbe", baseline.count) +
                Text.of("s_1aa2fc0e")
        }

        val staleDays = todayEpochDay - latest.dateEpochDay
        when {
            staleDays > 0L -> caveats +=
                Text.fmt("s_a16f134c", dateLabel(latest.dateEpochDay)) +
                    Text.fmt("s_0dd201af", staleDays) +
                    Text.of("s_33b31c74")
            staleDays < 0L -> caveats +=
                Text.fmt("s_6a28c484", dateLabel(latest.dateEpochDay)) +
                    Text.of("s_39d32e2b")
        }

        return Result.Ok(
            dateEpochDay = latest.dateEpochDay,
            staleDays = staleDays,
            score = score,
            level = levelOf(score.toDouble()),
            parts = parts,
            baseline = baseline,
            samsungScore = latest.samsungScore,
            caveats = caveats,
        )
    }

    /**
     * 逐晚算一遍（界面画趋势用）。
     *
     * **每一晚都用它自己之前的晚做基线**，不看未来 —— 所以同一份数据算多少次结果都一样，
     * 也不会因为"后来攒了更多基线"而让历史点跳来跳去（历史点只在**算法口径**变化时才变，
     * 那是 `Energy` 不落库这条纪律的必然结果：所有历史点与新点用的是同一份代码）。
     *
     * 算不出来的晚**直接跳过**（不回填 0）：折线上一个假的 0 会被读成"那晚恢复极差"，
     * 而事实是"那晚没有数据"。界面把缺口的日期列出来更好。
     */
    fun series(
        nights: List<Night>,
        todayEpochDay: Long,
        stepGoal: Int? = null,
        limit: Int = 14,
    ): List<Point> {
        val asc = nights.distinctBy { it.dateEpochDay }.sortedBy { it.dateEpochDay }
        return asc.mapNotNull { n ->
            when (val r = analyze(n, asc, todayEpochDay, stepGoal)) {
                is Result.Ok -> Point(n.dateEpochDay, r.score, r.level, r.samsungScore)
                is Result.NotApplicable -> null
            }
        }.takeLast(limit)
    }

    /** 趋势上的一个点。 */
    data class Point(
        val dateEpochDay: Long,
        val score: Int,
        val level: String,
        val samsungScore: Int?,
    )

    // ═══════════════════════ 分量换算 ═══════════════════════
    //
    // 全部是**分段线性、可倒推**（与 Stress 的 BAND 映射同一取向：可解释性优先于曲线平滑）。
    // 台架里每一项都从两端锚点反推验证过。

    /**
     * ① 睡眠时长 → 分量分。`4h→0`、`7h→50`、`9h→100`，9 小时以上封顶。
     */
    fun sleepScore(hours: Double): Int {
        if (hours.isNaN()) return 0
        return when {
            hours <= SLEEP_MIN_H -> 0
            hours < SLEEP_FAIR_H ->
                (50.0 - (SLEEP_FAIR_H - hours) * SLEEP_PENALTY_PER_H).roundToInt().coerceIn(0, 50)
            hours < SLEEP_GOOD_H ->
                (50.0 + (hours - SLEEP_FAIR_H) * SLEEP_GAIN_PER_H).roundToInt().coerceIn(50, 100)
            else -> 100
        }
    }

    /**
     * ② 睡眠期最低血氧 → 分量分。线性：`88%→0`、`95%→100`。**只看最低读到多少**，
     * 不数次数、不算时长 —— 那些是 [Odi] 的活（两个量在界面上分开显示）。
     */
    fun spo2Score(minPct: Int): Int =
        ((minPct - SPO2_ZERO_PCT).toDouble() * 100.0 / (SPO2_FULL_PCT - SPO2_ZERO_PCT))
            .roundToInt().coerceIn(0, 100)

    /**
     * ③ 睡眠心率 → 分量分。`50 分 = 与基线持平`，每偏 6% 折 ±25 分。
     *
     * **方向**：低于基线 → dev 为正 → 分更高（恢复更好）。与 [Stress] 相反。
     */
    fun hrScore(hr: Double, baselineHr: Double): Int {
        if (baselineHr <= 0.0) return 50
        val dev = (baselineHr - hr) / baselineHr / UNIT_HR_NIGHT
        return (50.0 + BAND * dev).roundToInt().coerceIn(0, 100)
    }


    /**
     * ⑤ 皮温偏差 → 分量分。`0.3℃→100`、`1.5℃→0`，**两侧对称**（取绝对值）。
     *
     * 为什么对称：三星给的是"相对个人基线的偏差"，偏高偏低都表示偏离了常态。
     * 偏高的临床含义更明确（发热、饮酒、环境过热），但我们没有依据把偏低当成"无所谓"，
     * 所以只报"偏得远不远"，不替用户解读方向。
     */
    fun skinTempScore(devC: Float): Int {
        val d = abs(devC)
        if (d <= SKIN_DEV_FULL_C) return 100
        val span = (SKIN_DEV_ZERO_C - SKIN_DEV_FULL_C).toDouble()
        val over = (d - SKIN_DEV_FULL_C).toDouble()
        return (100.0 - over * 100.0 / span).roundToInt().coerceIn(0, 100)
    }

    /** [resolveSkinDev] 的结果：要么给出偏差，要么说明为什么给不出。 */
    sealed interface SkinResolved {
        /**
         * 拿到了可用的偏差（℃）。
         *
         * @param baselineText 绝对档转出来的那个参照值（"你自己的基线 35.2℃"）；
         *   偏差档本身没有绝对参照，为 null。
         */
        data class Ok(val devC: Double, val baselineText: String?) : SkinResolved

        /**
         * 给不出。
         *
         * @param note 要写进 caveats 的说明；**为空表示不用出声**
         *   （这一晚压根没有皮温数据时，那是常态、不是异常，不必刷一条 caveat）。
         */
        data class Missing(val note: String?) : SkinResolved
    }

    /**
     * 把某一晚的皮温读数**统一成"相对个人基线的偏差"**，供 ⑤ 分量使用。
     *
     * ## 这是本函数存在的唯一理由
     * 三星的皮温口径**没写明**，实测有两种：绝对温度（30~36℃）与相对偏差（±0.x）。
     * 评分那一项要的是偏差，所以绝对档必须拿"你自己平时的皮温"去减 ——
     * 那个基线只有 [Baseline] 里才有，所以换算只能在这里做。
     *
     * ## 三条分支，互不混淆
     * | 情况 | 结果 |
     * |---|---|
     * | 偏差档（\|v\| ≤ 8℃） | 原样用；`baselineText = null` |
     * | 绝对档 + 有基线（≥ [MIN_SKIN_BASELINE_NIGHTS] 晚） | `偏差 = 本次 − 基线中位数` |
     * | 绝对档 + 基线不够 | **拒答**并说明"还差几晚"（不用常数顶替） |
     * | 判不出档（如 12.0） | **拒答**并说明口径判不出来 |
     * | 这一晚没有皮温 | 拒答但不发声（常态） |
     *
     * ⚠ 绝对档转出偏差后，**仍要过一遍 [SKIN_DEV_MAX_PLAUSIBLE] 的合理性检查**：
     *   某个"绝对"读数若离基线远得离谱（如基线 35℃ 而本次 26℃），
     *   更可能是那晚传感器没贴合，而不是"皮温降了 9℃"。放它进去会让这一项直接 0 分。
     */
    fun resolveSkinDev(night: Night, baseline: Baseline): SkinResolved {
        val raw = night.skinRaw
            ?: return SkinResolved.Missing(null)   // 没有皮温 = 常态，不必出声
        return when (night.skinKind) {
            SkinKind.DEVIATION -> SkinResolved.Ok(raw.toDouble(), null)

            SkinKind.ABSOLUTE -> {
                val med = baseline.skinMedC
                    ?: return SkinResolved.Missing(
                        Text.of("s_c88c88e4") +
                            Text.fmt("s_9782bb29", MIN_SKIN_BASELINE_NIGHTS) +
                            Text.fmt("s_38cba5ec", baseline.skinCount)
                    )
                val dev = raw - med
                if (abs(dev) > SKIN_DEV_MAX_PLAUSIBLE) {
                    SkinResolved.Missing(
                        Text.fmt("s_60c88a9b", raw, fmt1(med)) +
                            Text.fmt("s_55d12c60", fmt1(dev))
                    )
                } else {
                    SkinResolved.Ok(dev, Text.fmt("s_fd4301cb", fmt1(med)))
                }
            }

            null -> SkinResolved.Missing(
                Text.fmt("s_dc74c5c9", raw) +
                    Text.of("s_ad27eb8f")
            )
        }
    }

    /**
     * ⑥ 睡眠时间一致性 → 分量分。输入是**这一晚入睡时刻与平时之差**（分钟，可正可负）。
     *
     * `|差| ≤ 20 分钟 → 100`，之后每多 20 分钟 −25，`≥ 100 分钟 → 0`。
     * 两侧对称：比平时早睡 1 小时同样算"不一致"（作息被打乱就是被打乱了）。
     */
    fun consistencyScore(devMin: Int): Int {
        val d = abs(devMin)
        if (d <= CONSIST_FREE_MIN) return 100
        return (100 - (d - CONSIST_FREE_MIN) * BAND.toInt() / CONSIST_PENALTY_MIN)
            .coerceIn(0, 100)
    }

    /**
     * ⑦ 睡眠规律性 → 分量分。输入是最近若干晚入睡时刻的**极差**（分钟）。
     *
     * `≤ 30 分钟 → 100`，之后每多 30 分钟 −25，`≥ 150 分钟 → 0`。
     */
    fun regularityScore(rangeMin: Int): Int {
        if (rangeMin <= REGULAR_FREE_MIN) return 100
        return (100 - (rangeMin - REGULAR_FREE_MIN) * BAND.toInt() / REGULAR_PENALTY_MIN)
            .coerceIn(0, 100)
    }

    /**
     * ⑧ 睡眠定时 → 分量分。输入是入睡时刻**相对 18:00 的分钟数**（见 [BED_ORIGIN_MIN]）。
     *
     * `≤ 24:00（即 360）→ 100`，之后每 30 分钟 −20，`≥ 02:30 → 0`。
     *
     * ⚠ **只罚晚睡，不罚早睡**：22:00 睡和 23:30 睡都算 100 分。
     * "太早上床"不是这个分要管的事，硬去罚它没有依据。
     */
    fun timingScore(clockMin: Int): Int {
        if (clockMin <= TIMING_FULL_MIN) return 100
        return (100 - (clockMin - TIMING_FULL_MIN) * TIMING_STEP / TIMING_PENALTY_MIN)
            .coerceIn(0, 100)
    }

    /**
     * ⑨ 前一日活动 → 分量分。线性：**达到步数目标 = 100**，超出不再加分。
     *
     * ⚠ 目标由调用方从三星健康读出；用户没设过时这一项**缺席**（不要传一个默认值进来）。
     */
    fun prevActivityScore(steps: Long, goal: Int): Int {
        if (goal < MIN_STEP_GOAL) return 0
        return (steps * 100.0 / goal).roundToInt().coerceIn(0, 100)
    }

    /**
     * ⑩ 活动持续性 → 分量分。输入是前一日按 [ACTIVITY_SLOTS] 段切分的步数。
     *
     * `每有一个时段达到 [SLOT_ACTIVE_STEPS] 加 [SLOT_SCORE_PER] 分`，≥5 段即满分 ——
     * 人不可能 24 小时都在活动，要求"6 段全有"等于永远拿不到满分。
     *
     * @return 段数 ≠ [ACTIVITY_SLOTS] 时返回 0（调用方应先判段数，见 [analyze]）
     */
    fun activitySpreadScore(slots: List<Long>): Int {
        if (slots.size != ACTIVITY_SLOTS) return 0
        val active = slots.count { it >= SLOT_ACTIVE_STEPS }
        return (active * SLOT_SCORE_PER).coerceIn(0, 100)
    }

    // ═══════════════════════ 基线构造 ═══════════════════════

    /**
     * 构造"这个人平时晚上是什么样"。
     *
     * 中心量一律用**中位数**（复用 [Hrv.baselineMedian]）：一晚没戴好、
     * 一晚喝了酒，都能把均值拉走一大截，而中位数对"多达一半记录有问题"仍稳 ——
     * 与 `Cycle` 用中位数而不是均值算周期长度是同一条理由。
     *
     * 每个量**各自判条数**：心率的晚数不够只是那个分量缺席，不拖累整张卡
     * （与 [Stress] 里皮温分量单独判条数同款）。
     */
    fun buildBaseline(past: List<Night>): Baseline {
        val hrs = past.mapNotNull { it.usableHr }
        val hrSds = past.mapNotNull { it.usableHrSd }
        val beds = past.mapNotNull { it.bedClockMin }
        val sleeps = past.mapNotNull { it.sleepHours }
        val spo2s = past.mapNotNull { it.usableSpo2 }
        // ⚠ 皮温基线**只收绝对温度档**的那几晚（不与偏差档混 —— 两套口径混起来
        //    会得到一个既不是"偏差"也不是"绝对温度"的数，而且不报错）。
        val skinAbs = past.asSequence()
            .filter { it.skinKind == SkinKind.ABSOLUTE }
            .mapNotNull { it.skinRaw }
            .toList()
        return Baseline(
            count = past.size,
            hrMed = if (hrs.size >= MIN_BASELINE_NIGHTS) Hrv.baselineMedian(hrs) else null,
            hrCount = hrs.size,
            hrSdMed = if (hrSds.size >= MIN_BASELINE_NIGHTS) Hrv.baselineMedian(hrSds) else null,
            hrSdCount = hrSds.size,
            bedClockMed = if (beds.size >= MIN_BASELINE_NIGHTS) {
                Hrv.baselineMedian(beds.map { it.toDouble() })?.roundToInt()
            } else {
                null
            },
            bedCount = beds.size,
            bedClockRange = if (beds.size >= MIN_BASELINE_NIGHTS) beds.max() - beds.min() else null,
            sleepMedHours = if (sleeps.size >= MIN_BASELINE_NIGHTS) Hrv.baselineMedian(sleeps) else null,
            spo2Med = if (spo2s.size >= MIN_BASELINE_NIGHTS) {
                Hrv.baselineMedian(spo2s.map { it.toDouble() })?.roundToInt()
            } else {
                null
            },
            skinMedC = if (skinAbs.size >= MIN_SKIN_BASELINE_NIGHTS) {
                Hrv.baselineMedian(skinAbs.map { it.toDouble() })
            } else {
                null
            },
            skinCount = skinAbs.size,
        )
    }

    // ═══════════════════════ 时刻换算 ═══════════════════════

    /**
     * 入睡时刻 → **相对 18:00 的分钟数**（0~1439）。
     *
     * ⚠ 这个换算不是装饰：入睡天然跨午夜，直接用 `hour*60+minute` 的话
     * 23:30 → 1410、00:30 → 30，两者的差会算成 **1380 分钟**（23 小时），
     * 于是"每天 23:30 睡"和"每天 00:30 睡"这两份同样规律的作息会得到一个荒谬的极差，
     * 而**它看起来只是个很大的数**（规律性得 0 分），不会报错。
     * 以 18:00 为原点后两者分别是 330 与 390，差 60 分钟 —— 正确。
     *
     * 为什么原点取 18:00 而不是 0 点：绝大多数人的入睡落在 18:00~06:00 之间，
     * 这个窗口以 18:00 起算就完全落在正数侧。台架里钉了跨午夜的用例。
     */
    fun bedClockOf(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): Int {
        val t = Instant.ofEpochMilli(epochMs).atZone(zone)
        val minute = t.hour * 60 + t.minute
        return if (minute >= BED_ORIGIN_MIN) {
            minute - BED_ORIGIN_MIN
        } else {
            minute + (24 * 60 - BED_ORIGIN_MIN)
        }
    }

    // ═══════════════════════ 展示辅助 ═══════════════════════

    /** epochDay → 「9月18日」。非法值返回空串（宁可少显示一行，也不显示 1970 年）。 */
    fun dateLabel(epochDay: Long): String {
        if (epochDay <= 0L) return ""
        val d = java.time.LocalDate.ofEpochDay(epochDay)
        return Text.fmt("s_4577f457", d.monthValue, d.dayOfMonth)
    }

    /** 一位小数（固定 Locale.US，免得某些地区用逗号当小数点）。 */
    fun fmt1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)

    /** 偏离量的展示：`+0.8 个单位` / `-0.3 个单位`。 */
    fun devText(units: Double): String =
        Text.fmt("s_76782b5c", if (units >= 0) "+" else "", fmt1(units))

    /** 圆屏/一行摘要用的短标签。 */
    fun levelShort(level: String): String = when (level) {
        Level.LOW -> Text.of("s_002967bb")
        Level.FAIR -> Text.of("s_00286c2c")
        Level.GOOD -> Text.of("s_0043184a")
        else -> "—"
    }

    /** 相对 18:00 的分钟数 → 「23:30」。用于把入睡时刻显示给人看。 */
    fun clockText(clockMin: Int): String {
        val abs0 = ((clockMin + BED_ORIGIN_MIN) % (24 * 60) + 24 * 60) % (24 * 60)
        return String.format(java.util.Locale.US, "%02d:%02d", abs0 / 60, abs0 % 60)
    }

    /** 分钟数 → 「1 小时 10 分」/ 「45 分钟」。 */
    fun minutesText(min: Int): String {
        if (min < 60) return Text.fmt("s_c282191f", min)
        val h = min / 60
        val m = min % 60
        return if (m == 0) Text.fmt("s_86150d1f", h) else Text.fmt("s_c5ef9b14", h, m)
    }

    /** 皮温偏差的展示：带上符号，让"偏高还是偏低"一眼可见。 */
    fun skinTempText(devC: Float): String =
        (if (devC >= 0) "+" else "") +
            String.format(java.util.Locale.US, "%.1f", devC) + "℃"

    /**
     * 自研分与三星分的差值说明（并排展示时的一句话）。
     *
     * 刻意**只描述差多少，不评判谁对**：两者输入不同、刻度不同、更新时机也不同
     * （三星那个是按天重算的成品，我们这个是读到哪一晚就算哪一晚），
     * 说"哪个更准"没有任何依据。
     */
    fun diffNote(own: Int?, samsung: Int?): String = when {
        own == null && samsung == null -> Text.of("s_c30afee6")
        own == null -> Text.of("s_d4956122")
        samsung == null -> Text.of("s_2cf6c6c7")
        else -> {
            val d = own - samsung
            when {
                abs(d) <= 10 -> Text.fmt("s_2d31f588", abs(d))
                d > 0 -> Text.fmt("s_aba77fb7", d)
                else -> Text.fmt("s_405fab2a", -d)
            }
        }
    }

    // ═══════════════════════ 文案常量（必须出现在界面上） ═══════════════════════

    val SCALE_NOTE: String get() =
        Text.of("s_e4ded133") +
            Text.of("s_ad55fadf") +
            Text.of("s_4f52088e") +
            Text.of("s_32647f95") +
            Text.of("s_d6d8d272") +
            Text.of("s_6fe79626") +
            Text.of("s_8aaf0652") +
            Text.of("s_1244c4e1") +
            Text.of("s_6c6715f2")

    val BASIS_NOTE: String get() =
        Text.of("s_b3c4090e") +
            Text.of("s_f0ae92be") +
            Text.of("s_85a16834") +
            Text.of("s_82cd2d7c") +
            Text.of("s_2a0d6978") +
            Text.of("s_0807a04c") +
            Text.of("s_e3a0222a")

    val HR_BASELINE_NOTE: String get() =
        Text.of("s_0ba331e8") +
            Text.of("s_d53ec54f") +
            Text.of("s_9db1c090") +
            Text.of("s_a5ed15ac") +
            Text.of("s_b6d907e1") +
            Text.of("s_795fb8f1")

    val TIMING_NOTE: String get() =
        Text.of("s_29592a3c") +
            Text.of("s_ecf14dfb") +
            Text.of("s_4bf49945") +
            Text.of("s_7a97d0e5") +
            Text.of("s_87d2f224")

    val ACTIVITY_NOTE: String get() =
        Text.of("s_668d3da0") +
            Text.of("s_992d0ea3") +
            Text.of("s_b0587a54") +
            Text.of("s_f60c0fc9") +
            Text.of("s_46e5cca5") +
            Text.of("s_669f9294")

    val WEIGHT_NOTE: String get() =
        Text.of("s_a1c02dd1") +
            Text.of("s_1fa6fc3a") +
            Text.of("s_7cb34856") +
            Text.of("s_df5eebe4") +
            Text.of("s_d41d10be") +
            Text.of("s_725e51b2")

    val OUR_SCOPE_NOTE: String get() =
        Text.of("s_5cf300a5") +
            Text.of("s_b8680c60") +
            Text.of("s_7482759f") +
            Text.of("s_cc66f413") +
            Text.of("s_ee4e46ec")

    val VS_SAMSUNG_NOTE: String get() =
        Text.of("s_d4cbd33d") +
            Text.of("s_1388a03b") +
            Text.of("s_0d707fd1") +
            Text.of("s_ffa0de79") +
            Text.of("s_54435864")

    val DISCLAIMER: String get() = Disclaimer.energy(Text.lang)
}
