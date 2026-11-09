package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * PPG（光电容积脉搏波）原始光信号的纯逻辑：AC/DC 归一化、灌注指数、数据完整度、
 * 脉率估算、通道令牌编解码、波形 CSV 往返与格式化。
 *
 * ## 这个模块存在的意义
 * 总表 #8 的产出物是**原始波形本身** —— 它的消费者不是人眼，而是后续的自研算法
 * （#11 呼吸率从 PPG 波形提、#12 压力评分、#14 的 PPG 侧）。所以这里的首要职责是
 * **把 SDK 给的原始计数变成可跨会话比较、可安全落库的波形**，其次才是顺带给出
 * 两个不需要复杂算法就能算的数（灌注指数、脉率估算）让界面有东西可显示。
 *
 * ## 为什么必须做 AC/DC 归一化（这是本模块最关键的一条）
 * 三星 SDK 的 `ValueKey.PpgSet.PPG_GREEN/PPG_RED/PPG_IR` 都是 `Integer`，
 * 是**光敏二极管的原始 ADC 计数**。它没有物理单位，也**不是跨会话可比的**：
 * 同一根手指，换个佩戴松紧、换个环境光、换个皮肤温度，同一秒的绝对值就能差几倍；
 * 三星也没有给出满量程或 LED 驱动电流。直接把这种数存下来，"波形"这件事就废了 ——
 * 明天再看今天的记录，连"振幅是变大还是变小"都说不出来。
 *
 * 而 PPG 真正携带信息的量是 [Ac.norm]：**相对该通道 DC 电平的交流分量**
 * （`(x - dc) / dc`，无量纲、DC 的倍数）。它正是脉搏波的标准表示，
 * 也是所有后续算法（波峰检测、呼吸调制、PTT）实际使用的量。
 * 归一化把"增益、LED 电流、环境光"这些**乘性**的未知因素一起消掉了。
 *
 * ## 存储的波形"只除 DC、不做任何滤波"，而 PI/脉率在**去趋势副本**上算
 * 这两件事需要的东西不一样，台架上验证过差别：
 *  - **落库的波形**要尽可能忠实。PPG 的基线会被呼吸（0.2~0.4 Hz）和血管舒缩调制，
 *    而那正是模块 #11 要提取的信号 —— 在这里先滤一道等于替下游做了决定，而且滤掉的东西补不回来。
 *    所以 [Ac.norm] 只减了整段的 DC 中位数，不做带通、不去趋势。
 *  - **灌注指数与脉率**要的只是"搏动幅度有多大 / 峰在哪"。基线漂移必须去掉：
 *    腕部 PPG 的基线在 30 秒里漂 10% 是常态，而搏动本身只有 DC 的千分之几 ——
 *    不去趋势的话，一次缓慢漂移就能把灌注指数从 1% 算成 10%（离线校验台架里有这条断言）。
 *
 * 于是 [acPeakToPeak] 与 [estimatePulseRate] 都在 [movingAverageDetrend] 处理后的副本上工作，
 * 窗口取 [DETREND_WINDOW_SEC]（见该常量的说明）。
 *
 * ## 为什么做成无 Android 依赖的纯 Kotlin
 * 与 `Activity.kt` / `BodyComposition.kt` / `Spo2.kt` / `Temperature.kt` 同一条理由：
 * 能直接进离线 JVM 校验台架跑断言。这个项目在"凭印象写阈值/单位"上
 * 已经出过事故（血氧 0–100 被当成 0–1），而**归一化的量纲和各条门限正是最容易随手写错的东西**。
 *
 * ## 刻意不做的两件事（别再加回来）
 * 1. **不解释 SDK 的 `GREEN_STATUS/IR_STATUS/RED_STATUS` 状态码**。三星没有公开这几个码的语义，
 *    而本模块的结论要显示给用户、要落库。靠猜出来的状态语义去判"信号好坏"，
 *    一旦猜错就是**看起来很正常**的错误结论。这里只把出现过的状态码原样记下来（由手表端收集成
 *    `statusSeen`），供排查用；"数据好不好"只由 [Ac.quality] 这个**可自证**的量表达（有效样本占比）。
 * 2. **不从红光/红外推血氧**。比例-比值法(ratio-of-ratios)的标定系数逐机型/逐 LED 组合不同，
 *    没有厂商标定就是编数；而血氧本来就有三星自己的 SPO2 通道在供，走 `spo2` 表。
 *    这里把红光/红外的波形存下来，是为了将来真有标定数据时可用。
 */
object Ppg {

    // ═══════════════ 通道 ═══════════════

    /**
     * 通道令牌。用字符串常量而不是枚举：它要落库、要进 JSON、要跨版本读回，
     * 字符串比枚举序号的兼容性好得多（睡眠阶段 `SleepStageKind`、活动档位 `Activity.Level` 同一条理由）。
     */
    object Channel {
        const val GREEN = "green"
        const val RED = "red"
        const val IR = "ir"

        val ALL = listOf(GREEN, RED, IR)

        fun label(token: String?): String = when (token) {
            GREEN -> Text.of("s_00414dc6")
            RED -> Text.of("s_00411e2f")
            IR -> Text.of("s_004125fc")
            else -> token ?: Text.of("s_f2898c1c")
        }
    }

    /** 通道列表 -> 存储令牌（`"green,red,ir"`）。顺序固定为绿/红/红外，与传入顺序无关。 */
    fun encodeChannels(channels: List<String>): String =
        Channel.ALL.filter { channels.contains(it) }.joinToString(",")

    /** 存储令牌 -> 通道列表。无法识别的令牌直接丢弃（截断/写入脏值都算）。 */
    fun decodeChannels(csv: String?): List<String> {
        if (csv.isNullOrBlank()) return emptyList()
        val parts = csv.split(',').map { it.trim().lowercase() }
        return Channel.ALL.filter { parts.contains(it) }
    }

    /** 校验：一个通道名是否被认识（写入前用，避免脏令牌进库）。 */
    fun isKnownChannel(token: String?): Boolean = token != null && Channel.ALL.contains(token)

    // ═══════════════ 门限 ═══════════════

    /**
     * 一段采集至少要有的**有效**样本数与时长 —— 不够就不给结论。
     *
     * 与 `Activity.analyze` 同样的态度：宁可界面显示"数据不足"，
     * 也不要拿 2 秒数据算出一个看起来正常的脉率。
     */
    const val MIN_SAMPLES = 150
    const val MIN_DURATION_MS = 5_000L

    /** 单次会话最多存多少个采样点/通道。25 Hz × 120 秒 = 3000；超过即截断（防内存与库无限膨胀）。 */
    const val MAX_STORED_SAMPLES = 3_000

    /** 单次会话的最长时长。超过后停止收集（不结束会话），避免手表被一条会话占住。 */
    const val MAX_SESSION_MS = 120_000L

    /** 采样率低于它就无法分辨脉搏（Nyquist 要 2 倍，这里再留一档余量）。 */
    const val MIN_SAMPLE_RATE_HZ = 8

    /** 采样率高于它必定是时间戳单位判错（本设备上 PPG 不可能跑到千赫兹）。 */
    const val MAX_SAMPLE_RATE_HZ = 2_000

    /**
     * 去趋势（滑动均值）窗口，**秒**。这是本模块精度最关键的一个参数，理由分三层：
     *
     * 1. **不会削掉脉搏**：窗口 T=4 s 时，均值滤波对频率 f 的增益是 `sinc(fT)`，
     *    在 f = k/4 Hz（k=1,2,… 即 15/30/45/60/75… 次/分）处**恰好为 0** ——
     *    这些心率的脉搏会被完整保留。其余心率的最大衰减也在 10% 以内
     *    （实测 40 次/分衰减 ~10%、50 次/分 ~8%、72 次/分 ~6%），对显示量级没有影响。
     * 2. **能去掉基线漂移**：漂移与呼吸性调制都比 0.25 Hz 慢得多，会被减掉大半，
     *    这正是灌注指数需要的（见类注释）。
     * 3. **不能太短**：1 秒窗口只对 0.5 Hz 以上有效，30 秒里漂 10% 的基线会被原样留下，
     *    灌注指数直接失真十倍。也不能太长：长到几十秒就退化成"减去整段均值"，
     *    等于没去趋势。
     */
    const val DETREND_WINDOW_SEC = 4f

    /** 采样率未知时去趋势窗口的兜底点数（按 25 Hz 的典型 PPG 采样率折出 4 秒）。 */
    private const val FALLBACK_RATE_HZ = 25

    /**
     * 脉率估算的门限。搜索范围由 [HR_MIN]~[HR_MAX] 直接折成自相关的 lag 区间，不另设门限。
     */
    const val HR_MIN = 30
    const val HR_MAX = 200

    /**
     * 判定"这段里确实存在周期性搏动"所需的自相关下界。
     *
     * 归一化自相关在 lag = 周期 处，对**干净周期性信号**接近 1.0、对纯噪声约 0.1 量级、
     * 对平直信号恒为 0（分母为零时按 0 返回）。取 0.5 相当于"至少有一半的能量是可解释的周期性"，
     * 这是工程门限，**不是临床界值**。
     */
    const val MIN_CORR = 0.50f

    /**
     * 选取基波周期时对"全局最大相关"的宽容度。
     *
     * 自相关在周期的整数倍处都有峰（正弦信号在 T、2T、3T 处都等于 1.0），
     * 所以不能直接取全局最大 —— 必须取**最小**的那个显著峰，它才是基波。
     * 判据是"达到全局最大值的 [CORR_PROMINENCE] 倍"，这样在 2T 处也等于 1.0 的正弦上，
     * 第一个（也就是 T）会被选中；而在真实脉搏（有谐波，T 处强于 2T）上同样选中 T。
     */
    const val CORR_PROMINENCE = 0.80f

    /**
     * 至少要有几个周期才认这个结果。
     *
     * 一个只覆盖两个周期的相关峰可能只是偶然；而用它去反推的脉率会看起来完全正常。
     */
    const val MIN_CYCLES = 3

    /** 自相关 lag 的下界守卫（低于它连一个完整波峰都放不下）。 */
    const val MIN_LAG = 2

    /**
     * 判定"有可辨脉搏"所需的归一化幅度下界。
     *
     * 归一化幅度就是 AC/DC，也就是**灌注指数**（零点几到几个百分点）。取 0.0015
     * 相当于 PI ≈ 0.15%：再低的交流成分在这种逐点计数的原始信号上已经淹没在量化噪声里，
     * 检出的一定是噪声。这是工程门限，**不是临床界值**。
     *
     * 它是自相关之外的**前置筛**：自相关对"幅度很小但很规则"的噪声也可能给出高相关，
     * 而"搏动幅度本来就不可辨"这件事只能由幅度自己说。
     *
     * ⚠ 在**贴零/跨零**的那一路（心电自带的 PPG）上，归一化尺度不是 DC，所以
     * `幅度/尺度` 不再是灌注比、这个门限因此偏松。那边的兜底是另外三条：
     * 逐拍离散度上限（[Ptt.MAX_SPREAD_MS]）、时延生理值域（[Ptt.PAT_MIN_MS] 起）、
     * 以及**两路心率的独立一致性校验** —— 不要靠加严这个常数去补。
     */
    const val MIN_PULSE_AMP = 0.0015f

    /** 数据完整度低于它就不给脉率估算 —— 缺样会让"样本序号 / 采样率"的时间换算整体偏移。 */
    const val MIN_QUALITY_FOR_HR = 0.80f

    // ═══════════════ 原始计数的取值语义 ═══════════════

    /**
     * 「SDK 这个包里没有这一路」的**哨兵值**（本 App 自己写入，用于长度占位）。
     *
     * PAT 那条链路（`Ptt`）靠「心电 R 峰下标 − 脉搏波足下标」算时延，所以两路
     * **每个下标都必须一一对应**。而 `DataPoint.getValue()` 在包里没有这个键时
     * **直接抛异常**（反编译 aar 可证：`((Value) map.get(key)).value`，键缺失即 NPE），
     * 所以手表端必须自己决定"缺了怎么占位"。占位不能跳过（跳过就错位），
     * 于是必须写进去一个值。
     *
     * ## ⚠⚠ 「哪些值算读数」——四次判断、两次反转，最后**由真机数据定案**
     *
     * 判据错一次就整条功能失效，而且症状是"一次都算不出来"或"算出一片假心率"。
     * 四次都记在这里，**别再反转**：
     *
     * | 版本 | 判据 | 真机结果 |
     * |---|---|---|
     * | v60 | `> 0`（判据对）+ 但"窗里只要有缺样就整窗作废" | 每一拍都作废 → **0 拍** |
     * | v61 | `!= 哨兵`（把 `-1` 也当读数） | **假心率 195 次/分**（被两路一致校验拦住） |
     * | v62 | 回到 `> 0` | 仍然 0 拍（那一场的读数**全为负**） |
     * | **v64** | **`-1` 与 `0` 是标记，其余（含负数）都是读数** | **41/41 拍、中位 267 ms** |
     *
     * 定案依据是手表导出的双路 CSV（PTT 分析脚本直接读）：
     * ```
     * -1(标记)  12192 (80.0%)
     * 0(标记)       0
     * 读数       3048 (20.0%)   ← 值域 -48219 ~ -6424，**全负**
     * ```
     * 另一场会话里读数是正的（1468 ~ 30286+）—— 也就是说**这一路的读数有符号**，
     * 符号取决于当时的 LED / 接触状态，而 `-1` 始终是"没有值"。所以
     * **拿任何"按符号"的判据（`> 0`）都必然会在某些会话上失效**，只能按**标记**判。
     *
     * `0` 一并当标记：真机上从未作为读数出现过，且 ADC 计数恰好为 0 意味着没有光。
     *
     * ## 另一条通道（`PPG_CONTINUOUS`，25 Hz）的判据不同 —— 那也是**实测**的
     *
     * 那一支的读数是**全正**的、`0` 表示"这一拍没读到光"，所以 `Ppg.normalize` 用 `> 0`
     * （见它的注释）。**两条通道的标记不同，这不是笔误。** 若哪天那一支也出现 `-1`，
     * 分析脚本的"最高频取值"那行会立刻把它显示出来。
     */
    const val MISSING = Int.MIN_VALUE

    /**
     * 归一化尺度的分位点（**百分位**，作用在「绝对值」上）。
     *
     * 用 90 而不是中位数：真机那一路上「舒张期」很长一段压在基线附近（甚至就是 0），
     * 若拿中位数当尺度，**半数以上样本是 0 时尺度会塌成 0** —— 那就又变成"没有信号"，
     * 而它其实是有脉搏的。90 分位落在搏动那一侧，对这种"长平底 + 窄脉冲"的形状稳。
     *
     * 为什么不能取 100（最大值）：单点毛刺会定义整个尺度，一次运动伪迹就能把
     * 所有拍的归一化幅度压小 —— 与 [acPeakToPeak] 用 p95 而不是 min-max 是同一条理由。
     */
    const val SCALE_PERCENTILE = 90

    // ═══════════════ AC/DC 归一化 ═══════════════

    /**
     * 中位数 —— DC 口径的**唯一实现**。
     *
     * [normalize] 与 [normalizeAligned] 都必须走这里：它们的差别只在"哪些样本算有效"，
     * 而中位数怎么算**只能有一处定义**（本项目在"同源"上吃过亏：心电的界面质量分与门控
     * 一度不同源）。偶数长度取中间两个的平均 —— 直接取 `sorted[size/2]` 会偏向一侧。
     */
    /**
     * 这一路（心电自带的那支 PPG）的"是不是读数"判据：**不是标记就是读数**。
     *
     * 标记 = `-1`（SDK 说"这一包没有脉搏波"）、`0`（意味着没有光）、[MISSING]（我们自己占位）。
     * 理由与四次判断的复盘见 [MISSING]。**只此一处定义** —— 有效样本的筛选、
     * 归一化的 NaN 占位、以及手表端那行诊断的计数都引用它，避免三处各写一遍。
     */
    fun isReading(v: Int): Boolean = v != MISSING && v != -1 && v != 0

    private fun medianOf(values: List<Int>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2].toFloat()
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2f
        }
    }

    /**
     * 已排序序列的分位数（`percent` 取 0~100，取最近的秩）。
     *
     * 抽出来是为了让 [acPeakToPeak] 的 p5/p95 与 [normalizeAligned] 的尺度**同一口径** ——
     * 分位数的取法（`(n-1)*p/100` 取整、越界夹住）本身就是个容易写歪的东西，
     * 有两处实现迟早会漂。
     */
    private fun percentileOf(sorted: List<Float>, percent: Int): Float =
        if (sorted.isEmpty()) {
            Float.NaN
        } else {
            sorted[((sorted.size - 1) * percent / 100).coerceIn(0, sorted.size - 1)]
        }

    /**
     * 一个通道的归一化结果。
     *
     * @param dc 直流电平（原始计数的**中位数**，见 [normalize]）。存下来才能从归一化波形反推原始值
     *   （`raw ≈ norm × dc + dc`），它同时也是"当时 LED 亮度/贴合程度"的记录。
     * @param norm 归一化交流分量（DC 的倍数，无量纲，均值≈0）。这就是"波形"。
     *   **刻意不做任何滤波**（不帯通、不去趋势），理由见类注释。
     * @param valid 有效样本数（值 > 0）。
     * @param total 参与统计的总样本数（含无效样本）。
     */
    data class Ac(
        val dc: Float,
        val norm: List<Float>,
        val valid: Int,
        val total: Int,
    ) {
        /** 数据完整度（0~1）= 有效样本占比。**这是"有没有采到数"，不是临床意义的信号质量**。 */
        val quality: Float get() = if (total <= 0) 0f else valid.toFloat() / total
    }

    /**
     * 把一个通道的原始计数归一化成交流分量。
     *
     * ## 为什么 DC 用**中位数**而不是均值
     * 均值会被一小段极端值拉走：手一动、表带一松，原始计数会整体跳一次，
     * 那个跳变足以把均值抬高百分之几十（PPG 的有效交流成分只有 DC 的千分之几），
     * 于是整段波形的幅度被系统性压小。中位数对这种"少数样本被污染"是稳健的。
     *
     * ## 无效样本（<= 0）先剔除
     * ADC 计数为 0 表示这一拍根本没读到光（LED 未点亮 / 采样失败），它不是"很小的信号"，
     * 而是"没有信号"。留着会把中位数往下拉。剔除后计入 [Ac.total] 以便算完整度。
     *
     * ⚠ **这条判据只对 `PPG_CONTINUOUS`（25 Hz、四路一起下发）成立**。
     * **心电会话自带的那一路是有符号的**（实测值域 -42351 ~ 179709），对它有另一套
     * 取值语义 —— 见 [MISSING] 与 [normalizeAligned]。**不要拿这个函数处理那一路。**
     *
     * 返回 null 表示整段没有一个有效样本，或有效样本不足以定出中位数。
     */
    fun normalize(raw: List<Int>): Ac? {
        if (raw.isEmpty()) return null
        val valid = raw.filter { it > 0 }
        if (valid.size < MIN_SAMPLES) return null

        // 中位数的算法走 medianOf（唯一实现），这里不再自己写一遍
        val mid = medianOf(valid) ?: return null
        if (mid <= 0f || mid.isNaN()) return null

        val norm = ArrayList<Float>(valid.size)
        for (v in valid) norm.add((v - mid) / mid)

        return Ac(dc = mid, norm = norm, valid = valid.size, total = raw.size)
    }

    /**
     * 保持**下标对齐**的归一化结果（PAT 用）。
     *
     * 与 [Ac] 的差别只在 `norm` 的长度语义：这里 `norm.size == 输入长度`，
     * 缺样位置是 [Float.NaN] 而不是被删掉 —— 于是**时刻表不变**，
     * 调用方在原始下标轴上跳过它们即可（见 [normalizeAligned] 与 [Ptt]）。
     */
    data class Aligned(
        /**
         * 归一化的**中心**：有效样本（`> 0`）的中位数。
         *
         * ⚠ 它不一定等于"直流电平"，也不保证为正（信号跨零时可能 ≤ 0）；
         * 归一化的分母是 [scale] 而不是它。
         */
        val dc: Float,
        /**
         * 归一化的**尺度**（恒 > 0）= `max(|dc|, |值| 的 [SCALE_PERCENTILE] 分位)`。
         *
         * 为什么这么取：一侧有直流基准时（正常腕部 PPG，DC 远大于交流分量），
         * `|dc|` 就是那个 DC，于是 `幅度/尺度` 可以直接读成灌注比；
         * 若中位数远小于整体量级（信号跨零），由「绝对值的 90 分位」顶上 ——
         * 对"长平底 + 窄脉冲"的形状是稳的，也不会因为中位数落在 0 就整段作废。
         *
         * **对 PAT 来说这个选择不影响结果**：波足的定义是「拍内峰谷之间的 10% 电平」，
         * 是**按拍内幅度比例**定的，与整段的尺度无关。尺度只影响幅度门控的量级。
         */
        val scale: Float,
        /** 与输入**等长**的归一化波形；缺样（`<= 0`，含 [MISSING]）处为 [Float.NaN]。 */
        val norm: List<Float>,
        /** 缺样个数（**非正样本数**）。真机上这一路实测约占 **80%**。 */
        val invalid: Int,
    ) {
        val total: Int get() = norm.size

        /** 数据完整度（0~1）。与 [Ac.quality] 同含义，但分母是输入长度。 */
        val quality: Float get() = if (total <= 0) 0f else (total - invalid).toFloat() / total
    }

    /**
     * 归一化，且**保留全部下标**。[Ptt] 必须用这个，不能用 [normalize]。
     *
     * ## ⚠⚠ 为什么不能直接用 [normalize]
     *
     * [normalize] 会把 `<= 0` 的样本**剔除**。剔除本身是对的（那些就是"这一包没有脉搏波"），
     * 但剔除之后 `norm[i]` 对应的时刻**不再等于**输入第 `i` 个样本的时刻 —— 后面所有点整体前移。
     *
     * 而 PAT 恰恰是「心电 R 峰的下标 − 脉搏波足的下标」除以采样率算出来的。
     * 少一个样本，之后所有点的时刻就前移 1/fs，**每个 PAT 都会偏小一点点、且偏移量随机**
     * —— 不报错、不出 NaN，只是数值整体错位。这正是本项目最防的那类静默错误。
     *
     * 所以这里保留下标：**缺样的位置记 `Float.NaN`、时刻表完全不变**，
     * 由 [Ptt] 在**原始下标轴**上跳过这些位置（不是压缩掉它们）。
     *
     * ## 判据与尺度
     *
     * - **有效样本 = 不是标记**：`-1`、`0`、[MISSING] 是"没有值"，
     *   **其余一律是读数 —— 包括负数**（真机实测一场读数全负、另一场全正；
     *   完整的四次判断复盘见 [MISSING] 的注释）；
     * - 中心 = 有效样本的中位数；
     * - 尺度 = `max(|中心|, |值| 的 [SCALE_PERCENTILE] 分位)`，恒 > 0。
     *
     * ⚠ **稀疏（实测每 5 个包只有 1 个有值）不是这里的问题**：这里只负责把下标轴摆正。
     * "怎么在只有 20% 有值的波形上定出波足"是 [Ptt] 的事（可用块 + 跳跃扫描 + 上升沿插值 +
     * **每拍去线性趋势**，那条通道混着比脉搏大好几倍的慢漂移）。
     *
     * @return 有效样本不足、或整段没有任何幅度可归一（尺度为 0）时返回 null。
     */
    fun normalizeAligned(raw: List<Int>): Aligned? {
        if (raw.isEmpty()) return null
        // ⚠ 判据是「**不是标记**」，**不是**「`> 0`」：这一路的读数**有符号**
        //   （真机实测一场全负 -48219~-6424、另一场全正）。`-1`（与 `0`）才是"没有值"。
        //   按符号判会在"读数全负"的会话上整段失效；把 `-1` 当读数则会算出假心率。
        //   四次判断的完整复盘见 [MISSING]。
        val valid = raw.filter { isReading(it) }
        if (valid.size < MIN_SAMPLES) return null

        val center = medianOf(valid) ?: return null
        if (center.isNaN()) return null
        // 稳健量级 = |值| 的 90 分位（不是中位数：见 [SCALE_PERCENTILE]）
        val magnitude = percentileOf(valid.map { abs(it).toFloat() }.sorted(), SCALE_PERCENTILE)
        // 一侧有直流基准时 `|center|` 就是那个 DC（于是「幅度/尺度」仍可读成灌注比）；
        // 若中位数远小于整体量级，由 magnitude 顶上。取大者，恒 >= 0。
        val scale = maxOf(abs(center), magnitude)
        if (scale.isNaN() || scale <= 0f) return null

        var invalid = 0
        val out = ArrayList<Float>(raw.size)
        for (v in raw) {
            if (isReading(v)) {
                out.add((v - center) / scale)
            } else {
                out.add(Float.NaN)
                invalid++
            }
        }
        return Aligned(dc = center, scale = scale, norm = out, invalid = invalid)
    }

    // ═══════════════ 去趋势 ═══════════════

    /** 去趋势窗口对应的点个数。采样率未知时按 [FALLBACK_RATE_HZ] 折。 */
    fun detrendWindowSamples(sampleRateHz: Int?): Int {
        val rate = sampleRateHz?.takeIf { it > 0 } ?: FALLBACK_RATE_HZ
        return (rate * DETREND_WINDOW_SEC).roundToInt().coerceAtLeast(3)
    }

    /**
     * 居中滑动均值去趋势（O(n)，前缀和实现）。
     *
     * 窗口**居中**：单边窗口会带来整体相移，虽然不影响"峰间隔"这个我们真正要的量，
     * 但居中更好解释，也不会在首尾造出畸变。边界处窗口自动收缩（只用可用的那几个点），
     * **不补零** —— 补零会在首尾造出两个人造凹陷。
     *
     * 窗口参数的选取理由见 [DETREND_WINDOW_SEC]。
     */
    fun movingAverageDetrend(x: List<Float>, window: Int): List<Float> {
        if (x.isEmpty()) return emptyList()
        val w = window.coerceIn(1, x.size)
        if (w <= 1) return x.toList()
        return subtract(x, movingAverage(x, w))
    }

    /**
     * 居中滑动均值（O(n)，前缀和实现）。
     *
     * 单独暴露出来（而不是只作为 [movingAverageDetrend] 的内部步骤）是因为下游模块需要
     * **低通本身**：`Resp` 要先用它把呼吸信号平滑掉再减长窗均值做带通 ——
     * 那里要的是"滤波后的信号"，不是"残差"。
     *
     * 窗口**居中**：单边窗口会带来整体相移；边界处窗口自动收缩（只用可用的那几个点），
     * **不补零** —— 补零会在首尾造出两个人造凹陷。
     */
    fun movingAverage(x: List<Float>, window: Int): List<Float> {
        if (x.isEmpty()) return emptyList()
        val w = window.coerceIn(1, x.size)
        if (w <= 1) return x.toList()
        val half = w / 2

        // prefix[i] = x[0..i-1] 的和
        val prefix = DoubleArray(x.size + 1)
        for (i in x.indices) prefix[i + 1] = prefix[i] + x[i]

        val out = ArrayList<Float>(x.size)
        for (i in x.indices) {
            val lo = (i - half).coerceAtLeast(0)
            val hi = (i + half).coerceAtMost(x.size - 1)
            val mean = (prefix[hi + 1] - prefix[lo]) / (hi - lo + 1)
            out.add(mean.toFloat())
        }
        return out
    }

    /** 逐点相减（长度以较短者为准，正常调用两者等长）。 */
    private fun subtract(a: List<Float>, b: List<Float>): List<Float> {
        val n = minOf(a.size, b.size)
        val out = ArrayList<Float>(n)
        for (i in 0 until n) out.add(a[i] - b[i])
        return out
    }

    /**
     * 搏动幅度：去趋势后波形的稳健峰峰值（**归一化单位**，即 AC/DC）。
     *
     * 用 p95-p5 而不是 min-max：单点尖峰不该定义一个片段的振幅
     * （真机上偶发的采样毛刺会被算成"搏动很大"）。
     *
     * 返回负值/NaN 时按 0 处理，调用方靠 [perfusionIndex] 的 null 判别"算不出来"。
     */
    fun acPeakToPeak(norm: List<Float>, window: Int): Float {
        if (norm.isEmpty()) return 0f
        val det = movingAverageDetrend(norm, window).sorted()
        val v = percentileOf(det, 95) - percentileOf(det, 5)
        return if (v.isNaN() || v < 0f) 0f else v
    }

    // ═══════════════ 灌注指数 ═══════════════

    /**
     * 灌注指数 PI = 交流分量 / 直流分量 × 100（%）。
     *
     * 输入 [acP2PNormalized] 是**归一化**的搏动幅度（[acPeakToPeak] 的结果，已经是 AC/DC），
     * 所以 PI 就是它乘 100。[dc] 只用于守卫 —— DC 非正说明没有可算的基准，
     * 此时返回 null 而**不是 0**："没有可算的直流电平"与"没有搏动"是两件事。
     */
    fun perfusionIndex(dc: Float, acP2PNormalized: Float): Float? {
        if (dc <= 0f || dc.isNaN()) return null
        if (acP2PNormalized.isNaN() || acP2PNormalized < 0f) return null
        return acP2PNormalized * 100f
    }

    /**
     * 从**已落库的原始计数**反算灌注指数：`(AC 峰峰值 / DC) × 100`。
     *
     * 存在的理由：数据库里存的是 [Ac.dc] 与搏动峰峰值的**原始计数**形式
     * （`PpgWaveEntity.dcGreen` / `acGreenP2P`），而 PI 的公式只该有一处定义。
     * 界面若要显示 PI 就得自己除一次 —— 那就是第二处定义，两边迟早会漂
     * （本项目在"同源"上吃过亏：心电的界面质量分与门控一度不同源）。
     *
     * 任一输入缺失或 DC 非正时返回 null（"算不出来"），**不是 0**：
     * `dcGreen` 为 null 的记录（早期/异常写入）不该被显示成"灌注指数 0%"。
     */
    fun perfusionIndexFromRaw(dc: Float?, acP2PRaw: Float?): Float? {
        if (dc == null || acP2PRaw == null) return null
        if (dc <= 0f || dc.isNaN()) return null
        if (acP2PRaw.isNaN() || acP2PRaw < 0f) return null
        return perfusionIndex(dc, acP2PRaw / dc)
    }

    // ═══════════════ 时间戳单位与采样率 ═══════════════

    /** 采样率估算结果。 */
    data class RateEstimate(
        /** 估算采样率（Hz）。 */
        val hz: Int,
        /** true = 由数据点自带时间戳算出（更准）；false = 由墙钟时长与点数推算。 */
        val fromTimestamps: Boolean,
        /** 时间戳单位标签（`ns`/`µs`/`ms`），仅 [fromTimestamps] 为 true 时有值。 */
        val unitLabel: String? = null,
    )

    /**
     * 时间戳单位是**实测判定**出来的，不是假定的。
     *
     * 三星 `DataPoint.getTimestamp()` 只声明返回 `long`，**没有在文档里写单位**
     * （ECG 那边靠 `SEQUENCE` 序号跨度绕开了这件事，PPG 没有 SEQUENCE）。
     * 单位差 1000 倍，采样率就差 1000 倍，而采样率是脉率换算的分母 ——
     * 猜错的后果是"脉率 30"和"脉率 30000"，且**看起来都像个数字**。
     *
     * 判定办法：时间戳按其 1/1e6（ns）、1/1e-3（µs）、1/1（ms）三种解释换算成毫秒，
     * 看哪一种与**墙钟实测时长**吻合。三者相差 1000 倍，不可能同时吻合，
     * 所以"唯一吻合者"就是答案；一个都不吻合（时钟跳变/单位不在候选里）就返回 null，
     * 让调用方退回墙钟估算 —— **宁可用一个粗的估计，也不要一个自信的错值**。
     *
     * @param tolerance 允许的比值区间。取 0.30~1.50：批量下发会让墙钟跨度**长于**数据跨度
     *   （同一批 10 个点在同一毫秒到达），所以比值通常略小于 1；反过来进程被抢占时可能略大于 1。
     */
    fun timestampUnitDivisor(
        firstTs: Long,
        lastTs: Long,
        elapsedMs: Long,
        tolerance: ClosedFloatingPointRange<Float> = 0.30f..1.50f,
    ): Float? {
        if (lastTs <= firstTs || elapsedMs <= 0L) return null
        val rawSpan = (lastTs - firstTs).toDouble()
        val candidates = listOf(1.0e6f to "ns", 1.0e3f to "µs", 1.0f to "ms")
        val matched = candidates.filter { (div, _) ->
            val ms = rawSpan / div
            ms > 0.0 && (ms / elapsedMs).toFloat() in tolerance
        }
        if (matched.size != 1) return null
        return matched[0].first
    }

    /** [timestampUnitDivisor] 的标签版本（用于诊断展示）。 */
    fun timestampUnitLabel(divisor: Float?): String? = when (divisor) {
        null -> null
        1.0e6f -> "ns"
        1.0e3f -> "µs"
        1.0f -> "ms"
        else -> "?"
    }

    /**
     * 估算采样率。
     *
     * 优先用数据点时间戳（[timestampUnitDivisor] 判定出单位后，
     * `(n-1) / 时间跨度`）；判定不出来就退化为 `n / 墙钟时长`。
     *
     * 为什么优先时间戳：墙钟测的是"回调到达的时刻"，批量下发会把一批点记成同一时刻，
     * 于是低估采样率（ECG 那边同样踩过）。时间戳测的是**采集时刻**，不受调度抖动影响。
     */
    fun estimateSampleRate(
        firstTs: Long?,
        lastTs: Long?,
        sampleCount: Int,
        elapsedMs: Long,
    ): RateEstimate? {
        if (sampleCount < 2 || elapsedMs <= 0L) return null

        if (firstTs != null && lastTs != null) {
            val div = timestampUnitDivisor(firstTs, lastTs, elapsedMs)
            if (div != null) {
                val spanMs = (lastTs - firstTs) / div
                if (spanMs > 0f) {
                    val hz = ((sampleCount - 1).toDouble() * 1000.0 / spanMs).roundToInt()
                    if (hz in MIN_SAMPLE_RATE_HZ..MAX_SAMPLE_RATE_HZ) {
                        return RateEstimate(hz, fromTimestamps = true, unitLabel = timestampUnitLabel(div))
                    }
                }
            }
        }

        val hz = (sampleCount.toDouble() * 1000.0 / elapsedMs).roundToInt()
        if (hz !in MIN_SAMPLE_RATE_HZ..MAX_SAMPLE_RATE_HZ) return null
        return RateEstimate(hz, fromTimestamps = false)
    }

    // ═══════════════ 脉率估算 ═══════════════

    /**
     * 从归一化交流波形估算脉率（次/分）。返回 null 表示"不够判定"，而不是一个凑出来的数。
     *
     * ## 为什么不用 ECG 那套 QRS 检测
     * `EcgSignal.detectQrsPeaks()` 是为**尖锐、双相**的 QRS 波群做的（带微分步骤、统一极性、
     * 局部归一化突出度）。PPG 的收缩期主峰是**单相、圆钝**的，而且逐点计数下只有 25 Hz 左右，
     * 微分会把信号打成噪声。硬套那套流水线只会得到一堆假峰。
     *
     * ## 也不用"门限 + 峰计数"
     * 这条路上连着踩了两次坑（多检 / 漏检），根因是**周期本身是未知量**，
     * 而任何固定窗宽的门限法都必须同时满足"够大以覆盖波峰"和"够小以不并拍"这两个矛盾要求。
     * 现在改成**自相关求周期**，详细推导与实测数据见 [fundamentalLag] 的注释。
     *
     * ## 每一道门都要过，缺一个就返回 null
     * 采样率未知 / 样本不足 / 幅度低于 [MIN_PULSE_AMP] / 完整度低于 [MIN_QUALITY_FOR_HR] /
     * 自相关峰值低于 [MIN_CORR] / 覆盖不足 [MIN_CYCLES] 个周期 /
     * 结果不在 [HR_MIN]~[HR_MAX] —— 任一不满足就不给数。
     * 这个值是**独立于心率通道的交叉验证**，给错比不给坏得多。
     */
    fun estimatePulseRate(norm: List<Float>, sampleRateHz: Int?, quality: Float): Int? {
        if (sampleRateHz == null || sampleRateHz < MIN_SAMPLE_RATE_HZ) return null
        if (norm.size < MIN_SAMPLES) return null
        if (quality < MIN_QUALITY_FOR_HR) return null

        val window = detrendWindowSamples(sampleRateHz)
        val det = movingAverageDetrend(norm, window)

        val sorted = det.sorted()
        val p5 = sorted[((sorted.size - 1) * 5 / 100).coerceIn(0, sorted.size - 1)]
        val p95 = sorted[((sorted.size - 1) * 95 / 100).coerceIn(0, sorted.size - 1)]
        val amp = p95 - p5
        if (amp.isNaN() || amp < MIN_PULSE_AMP) return null

        /**
         * 跳过滤波瞬态区：去趋势窗口在首尾无法居中，窗口会被截断成半宽，
         * 于是首尾各 [half] 个样本上是**另一条曲线**（不是在原信号上减掉基线）。
         * 那里不是真实心拍，参与相关运算只会把周期带偏。代价是可用时长两头各少 [half] 个样本。
         */
        val half = (window / 2).coerceAtLeast(1)
        if (det.size <= window) return null
        val usable = det.subList(half, det.size - half)

        val minLag = (sampleRateHz * 60.0 / HR_MAX).roundToInt().coerceAtLeast(MIN_LAG)
        val maxLag = minOf((sampleRateHz * 60.0 / HR_MIN).roundToInt(), usable.size / 2)
        if (maxLag <= minLag || usable.size < MIN_CYCLES * minLag) return null

        val period = fundamentalLag(usable, minLag, maxLag) ?: return null
        if (usable.size < MIN_CYCLES * period) return null

        val bpm = 60.0 * sampleRateHz / period
        if (bpm.isNaN() || bpm < HR_MIN || bpm > HR_MAX) return null
        return bpm.roundToInt()
    }

    /**
     * 用**自相关**求基波周期（单位：样本数，带亚样本精度）。返回 null = 这段没有可辨的周期性。
     *
     * ## 为什么最后落在自相关上（这是三次踩坑之后的选择）
     * 前两版都是"门限 + 峰计数"：
     *  1. 第一版：局部极大 + 不应期合并。正弦形的脉搏在一次搏动里有近六成时间高于
     *     `p5 + 40%·幅度`，这段区间比不应期还长 —— 噪声在里面的涟漪各自成峰，
     *     实测把 66 次/分算成 **72.8**。
     *  2. 第二版：改成"越过门限后在前方一个不应期内找顶点"，再改用峰间隔中位数。
     *     峰计数的问题解决了，但**固定窗宽同时被两个方向夹住**：窗宽要够大才够得到顶点
     *     （45 次/分时顶点在越限点后 9 个样本，而窗宽只有 8，于是**一拍检出两个峰**，
     *     45 被算成 83）；窗宽又要够小才不会把两拍并成一拍。周期本身是未知量，
     *     任何固定窗宽都必然在其中一端失效。
     *  3. 现在：不用门限、不数峰。自相关把整段信号与自己的平移做相关，
     *     **在一个完整周期上积分**，所以噪声和个别漏点都平均掉了；
     *     周期是相关峰的**位置**而不是"数出来的个数"，天然对多检/漏检免疫。
     *     代价是一次 O(可用点数 × lag 范围) 的运算 —— 750 点、lag 8~50 约 3 万次乘法，
     *     在手表上可以忽略。
     *
     * ## 亚样本精度靠抛物线插值
     * lag 是整数，直接取整会在高脉率上放大误差（90 次/分对应周期 16.67 个样本，
     * 取 17 会得到 88 —— 差 2 次/分）。在相关峰及其左右各一点上做抛物线插值，
     * 可以把周期精确到小数位。
     *
     * ## 为什么取"最小的显著峰"而不是全局最大
     * 自相关在周期的整数倍处都有峰，正弦信号在 T、2T、3T 处**都等于 1.0**。
     * 直接取全局最大就可能取到 2T（把脉率算成一半）。所以从短 lag 往长扫，
     * 取第一个"局部极大且达到全局最大 [CORR_PROMINENCE] 倍"的位置。
     */
    /**
     * 周期估计结果：位置（样本数，带亚样本精度）+ 该处的归一化自相关。
     *
     * 把相关系数一并返回：下游要用它当**置信度**。`Resp` 有三路呼吸信号（幅度/间期/基线），
     * 需要按"哪一路最可信"来融合 —— 只拿周期、拿不到相关值就没法挑。
     */
    data class LagEstimate(
        /** 基波周期（样本数，抛物线插值后的小数值）。 */
        val lagSamples: Double,
        /** 该 lag 处的归一化自相关（0~1）。就是"这个周期有多可信"。 */
        val corr: Double,
    )

    fun fundamentalLag(x: List<Float>, minLag: Int, maxLag: Int): Double? =
        fundamentalLagWithCorr(x, minLag, maxLag)?.lagSamples

    fun fundamentalLagWithCorr(
        x: List<Float>,
        minLag: Int,
        maxLag: Int,
        minCorr: Float = MIN_CORR,
        prominence: Float = CORR_PROMINENCE,
    ): LagEstimate? {
        val n = x.size
        if (n < 2 || maxLag <= minLag || minLag < 1) return null
        val mean = x.average()
        val centered = DoubleArray(n) { x[it] - mean }

        // 多算两端各一格，供抛物线插值使用
        val lo = (minLag - 1).coerceAtLeast(1)
        val hi = (maxLag + 1).coerceAtMost(n - 1)
        if (hi <= lo) return null

        val corr = HashMap<Int, Double>(hi - lo + 1)
        for (lag in lo..hi) {
            var num = 0.0
            var d1 = 0.0
            var d2 = 0.0
            val end = n - lag
            for (i in 0 until end) {
                val a = centered[i]
                val b = centered[i + lag]
                num += a * b
                d1 += a * a
                d2 += b * b
            }
            // 重叠区长度不同会让长 lag 处的相关系数虚高，归一化正好抵消掉这个效应
            corr[lag] = if (d1 > 0.0 && d2 > 0.0) num / kotlin.math.sqrt(d1 * d2) else 0.0
        }

        var globalMax = 0.0
        for (lag in minLag..maxLag) {
            val c = corr[lag] ?: continue
            if (c > globalMax) globalMax = c
        }
        if (globalMax < minCorr) return null

        val bar = globalMax * prominence
        for (lag in minLag..maxLag) {
            val c = corr[lag] ?: continue
            val prev = corr[lag - 1] ?: continue
            val next = corr[lag + 1] ?: continue
            if (c < prev || c < next) continue            // 只看局部极大
            if (c < bar) continue
            // 抛物线插值（三点定顶点），结果限制在半格以内，避免插到邻居峰上去
            val y0 = prev
            val y1 = c
            val y2 = next
            val denom = y0 - 2.0 * y1 + y2
            val delta = if (abs(denom) < 1e-12) 0.0 else 0.5 * (y0 - y2) / denom
            return LagEstimate(lag + delta.coerceIn(-0.5, 0.5), c)
        }
        return null
    }

    // ═══════════════ 汇总 ═══════════════

    /**
     * 一次 PPG 会话的统计结果（不含波形本身）。
     *
     * @param sampleRateHz 估算采样率（Hz），null = 没能估出来（此时不给脉率）。
     * @param rateFromTimestamps 采样率是否来自数据点时间戳（false = 墙钟推算，精度更差）。
     * @param acP2P 去趋势后的搏动稳健峰峰值（**原始计数**，= 归一化幅度 × DC）。
     *   存原始计数是为了让"当时搏动有多大"有个不受归一化影响的绝对参照。
     * @param estimatedHr 由波形独立估算的脉率；null = 门限未过。
     * @param refHr 同一次测量由心率/血氧通道给出的参考心率，用于对照。null = 拿不到。
     */
    data class Stats(
        val sampleCount: Int,
        val durationMs: Long,
        val sampleRateHz: Int?,
        val rateFromTimestamps: Boolean,
        val dc: Float,
        val acP2P: Float,
        val perfusionIndex: Float?,
        val quality: Float,
        val estimatedHr: Int?,
        val refHr: Int?,
    )

    /**
     * 汇总一次会话。
     *
     * [rawGreen] 传**全部**样本（含无效的 0），因为完整度要用总数当分母。
     * 样本数或时长不足 [MIN_SAMPLES] / [MIN_DURATION_MS] 时返回 null。
     */
    fun analyze(
        rawGreen: List<Int>,
        durationMs: Long,
        sampleRateHz: Int?,
        rateFromTimestamps: Boolean = false,
        refHr: Int? = null,
    ): Stats? {
        if (durationMs < MIN_DURATION_MS) return null
        val ac = normalize(rawGreen) ?: return null

        val p2pNorm = acPeakToPeak(ac.norm, detrendWindowSamples(sampleRateHz))

        return Stats(
            sampleCount = ac.valid,
            durationMs = durationMs,
            sampleRateHz = sampleRateHz,
            rateFromTimestamps = rateFromTimestamps,
            dc = ac.dc,
            acP2P = p2pNorm * ac.dc,
            perfusionIndex = perfusionIndex(ac.dc, p2pNorm),
            quality = ac.quality,
            estimatedHr = estimatePulseRate(ac.norm, sampleRateHz, ac.quality),
            refHr = refHr,
        )
    }

    // ═══════════════ 波形编解码 ═══════════════

    /**
     * 归一化波形的小数位数。
     *
     * **必须是 6 位，不能沿用 ECG 的 3 位。** ECG 存的是毫伏（幅度 ~1 mV），3 位小数足够；
     * 而归一化 PPG 的幅度就是 AC/DC，正常灌注下只有 **0.005~0.02**（PI 0.5%~2%），
     * 3 位小数会把整条波形压成 `0.000/0.010` 这样两三个台阶，波形直接消失。
     * 这是"抄上一版参数"会踩的坑，离线校验台架里对小数位数有专门断言。
     */
    const val WAVE_DECIMALS = 6

    /** 归一化波形 -> CSV。空集合返回空串（不是 null），调用方少一层判空。 */
    fun encodeWave(norm: List<Float>): String =
        norm.joinToString(",") { String.format(java.util.Locale.US, "%.${WAVE_DECIMALS}f", it) }

    /** CSV -> 归一化波形。非法值（截断的尾巴、空段）直接丢弃。 */
    fun decodeWave(csv: String?): List<Float> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(',').mapNotNull { it.trim().toFloatOrNull() }
    }

    // ═══════════════ 纵轴与格式化 ═══════════════

    /**
     * 波形纵轴建议上界（归一化单位，对称轴为 0）。
     *
     * **必须显著小于"正常搏动的幅度"**（PI 1% 对应峰值约 0.005），否则正常信号的波形
     * 只占图高的一小半，看着像没测到。它的唯一作用是防止全零输入把坐标轴退化成 0
     * （那会让绘制时的比例换算直接爆掉）。`Ppg.MIN_AXIS <= 0.005` 这条不变式由台架守着。
     */
    const val MIN_AXIS = 1.0e-3f

    /** 纵轴上界。按 1/2/5 的十进制阶梯量化，保证刻度是"好看的数"。 */
    fun axisBound(norm: List<Float>): Float {
        if (norm.isEmpty()) return MIN_AXIS
        var peak = 0f
        for (v in norm) {
            val a = abs(v)
            if (a > peak) peak = a
        }
        if (peak.isNaN() || peak <= 0f) return MIN_AXIS
        val want = peak * 1.10f                       // 留 10% 头部余量，顶点不会被裁掉
        var decade = 1.0e-6f
        while (decade * 10f <= want) decade *= 10f
        // 在 1/2/5 × 10^k 里挑第一个 >= want 的阶梯
        for (m in listOf(1f, 2f, 5f, 10f)) {
            val cand = decade * m
            if (cand >= want) return cand.coerceAtLeast(MIN_AXIS)
        }
        return (decade * 10f).coerceAtLeast(MIN_AXIS)
    }

    /** 灌注指数：两位小数（数值本身就在 0.1~3 之间，取整会抹掉全部信息）。 */
    fun formatPi(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.2f%%", it) } ?: "--"

    /** 数据完整度：整数百分比。 */
    fun formatQuality(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.0f%%", it * 100f) } ?: "--%"

    /** 采样率：整数 Hz。 */
    fun formatRate(hz: Int?): String = hz?.let { "$it Hz" } ?: "--"

    /** 脉率：整数 次/分。 */
    fun formatHr(v: Int?): String = v?.let { Text.fmt("s_a6eefdff", it) } ?: "--"

    /** DC 电平：原始计数，整数即可（它本身就是 ADC 计数）。 */
    fun formatDc(v: Float?): String =
        v?.let { String.format(java.util.Locale.US, "%.0f", it) } ?: "--"

    /** 时长：不足 1 分钟按秒，超过按 "M 分 S 秒"（与 `Activity.formatDuration` 同一口径）。 */
    fun formatDuration(ms: Long): String {
        val total = (ms / 1000L).coerceAtLeast(0L)
        val m = total / 60
        val s = total % 60
        return when {
            m <= 0L -> Text.fmt("s_8887c3b1", s)
            s == 0L -> Text.fmt("s_888609af", m)
            else -> Text.fmt("s_b4d0624e", m, s)
        }
    }

    /**
     * 估算脉率与参考心率的偏差描述。
     *
     * 刻意**不下"吻合/不吻合"的结论**：两者相差大既可能是光信号被运动污染，
     * 也可能只是采样窗口不同（心率通道是即时值，PPG 是整段平均）。
     * 界面需要的只是"差多少"，判断留给用户。
     */
    fun hrDeltaLabel(estimated: Int?, ref: Int?): String? {
        if (estimated == null || ref == null || ref <= 0) return null
        val d = estimated - ref
        return when {
            d == 0 -> Text.of("s_cf202fbb")
            d > 0 -> Text.fmt("s_36db3b3d", d)
            else -> Text.fmt("s_4cd89620", -d)
        }
    }

    /** 会话时长是否已达上限（到达后手表端停止继续收集，但会话本身不结束）。 */
    fun isSessionFull(durationMs: Long): Boolean = durationMs >= MAX_SESSION_MS

    /** 每个通道最多存多少个点。 */
    fun clampStored(size: Int): Int = size.coerceAtMost(MAX_STORED_SAMPLES)

    /** 一段采集是否短到不值得落库。 */
    fun isTooShort(sampleCount: Int, durationMs: Long): Boolean =
        sampleCount < MIN_SAMPLES || durationMs < MIN_DURATION_MS

    /** 时间格式化里用到的向上取整（保留给界面做刻度用，与 `Activity.envelopeBound` 同款思路）。 */
    internal fun niceCeil(v: Float, step: Float): Float =
        if (step <= 0f) v else ceil((v / step).toDouble()).toFloat() * step
}

/**
 * PPG 记录的来源标记，两端共用。
 *
 * 只有手表一种来源：三星健康的数据 SDK 不提供第三方可读的原始光信号
 * （它只给算好的血氧/心率），所以这里不为"三星来源"预留假值。
 */
object PpgSource {
    /** **搭血氧/点检的车**采到的那一路（`PPG_CONTINUOUS`，25 Hz，绿/红/红外三路）。 */
    const val WATCH = "watch_ppg"

    /**
     * **心电会话顺带采到的那一路**（`EcgSet.PPG_GREEN`，约 500 Hz，只有绿光）。
     *
     * ⚠ 与 [WATCH] **刻意分成两个 source**，理由与 HRV 的 `watch_ecg`/`watch_ppg` 那条一样：
     * 两者采样率差 20 倍、通道数不同（一个三路一个单路）、用途也不同
     * （那个做脉率与灌注、这个做 PAT 的时延）。混成一个 source 之后，
     * "按来源过滤"的查询会把两批数据当成同一批 —— 而且**不会报错**。
     */
    const val WATCH_ECG = "watch_ppg_ecg"
}
