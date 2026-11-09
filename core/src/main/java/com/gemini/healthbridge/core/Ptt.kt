package com.gemini.healthbridge.core

import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PAT（脉搏到达时间）= 心电 R 峰 → 同一心动周期脉搏波足 的时间。
 *
 * 模块 #14。数据来源：**心电会话本身就带一路光电容积脉搏波**
 * （`ValueKey.EcgSet.PPG_GREEN`，与 `ECG_MV` 在**同一个包里**），所以两路信号
 * 同时间轴、天然对齐 —— 不需要两个 tracker 并跑。
 *
 * ## ⚠⚠ 这一路的真实形态（真机实测；2026-09-24 与 09-27 两次定案）
 *
 * ```
 * 脉搏波 15220 点 · 80% 的包是 -1（"这一包没有脉搏波"）· 有值的 20% ≈ 100 Hz
 * 两场录制：一场读数全为负（-48219~-6424）、一场跨零（-28411~15043）→ **读数有符号**
 * 每拍（以**真实 R 峰**为零点）：极大在 ~+170 ms、极小在 ~+340 ms，
 *   即"降得快的那一段（160 ms）是收缩期上冲"，缓升段（530 ms，约 3 倍长）是舒张期
 * ```
 *
 * 由此三条**判据级**的结论（都是被真机数据逼出来的，代价各一轮返工）：
 *
 * 1. **"是不是读数"只按标记判，不按符号**：`-1` / `0` / [Ppg.MISSING] 是"没有值"，
 *    其余一律是读数（**含负数**）。按符号（`> 0`）判会在"读数全为负"的会话上整段失效。
 *    完整复盘见 [Ppg.MISSING]。
 * 2. **不能要求"窗里一个缺样都没有"**（那等于要求 80% 的时间有数据）。
 *    改为：在**原始下标轴**上跳过缺样的位置（时刻表因此完全不变），
 *    只要求"相邻有值点的间隔 ≤ [MAX_GAP_MS]"，并在**两个相邻有值点之间**
 *    对上升沿做线性插值来定出穿越时刻。
 * 3. **脉搏波的极性不固定**，所以"波足"的判据必须**与极性无关**（见 [steepestUpstroke]）；
 *    而**心脏那一端的参考点必须是真实 QRS**，不是能量包络的峰 ——
 *    后者会整体晚约 100 ms，且逐拍抖动（见 `EcgSignal.snapToQrs`）。
 *
 * ## 关于"插值"这条线划在哪
 *
 * 之前写过"缺样窗整窗作废、绝不插值"，那句话在 80% 缺样下等于功能失效。
 * 现在划的线更准：**不伪造缺样点的数据**（那是"造出一个不存在的波足时刻"），
 * 但**在两个真实测量之间插时刻**是必须的，也是安全的 ——
 * 前提是这两点足够近（[MAX_GAP_MS]），近到"上升沿是直的"这个假设成立。
 * 分辨率由此从"网格 10 ms"降到亚网格（逐拍之间还有 RR 抖动在帮忙抖动采样相位，
 * 取中位数可以再压下去）。
 *
 * ## ⚠ 这里算的是 PAT，不是 PTT，更不是血压
 *
 * 恒等式：
 *
 * ```
 * PAT = PEP + PTT
 * ```
 *
 * `PTT`（真脉搏传导时间）是主动脉瓣打开 → 脉搏波到达腕部的时间，它与血压相关；
 * 但**主动脉瓣打开那一刻在两种信号里都看不见**，所以 `PEP`（等容收缩期）**分不出来**。
 * 而 PEP 随情绪、咖啡因、体位、收缩力变化 **10~20 ms**，且**与血压无关**。
 *
 * 换算成血压：PTT 对血压的灵敏度约 1 ms≈1 mmHg，所以 10~20 ms 的 PEP 残留带
 * 相当于 **±10~20 mmHg**。而非侵入式血压的行业精度门槛（ISO 81060-2 / AAMI）是
 * 平均误差 ≤5 mmHg、标准差 ≤8 mmHg —— **残差带比门槛还宽**。
 *
 * **结论（本模块的设计前提，勿改）**：本类**只输出毫秒**。
 * · 做：PAT 本身（一个物理量）· 与**自身基线**的偏离；
 * · 不做：绝对血压值。标定消得掉系统性偏移，但消不掉"每次测量重新抽一次"的 PEP。
 *
 * 按三层判定表。**任何时候都不要给这个类加一个返回 mmHg 的方法**
 * （离线校验台架用反射扫描方法名，加了就会红）。
 *
 * ## 为什么必须在**会话当时**算，不能事后重算
 *
 * `ecg_waves.waveJson` 存的是带通后**降采样到约 100 Hz** 的展示波形（10 ms 分辨率），
 * 而 PEP/PAT 的中等效应只有 10~20 ms —— 事后拿 100 Hz 的波形重算，分辨率与效应同量级，
 * 算出来的东西没有意义。所以 PAT **只在这里、用会话内的全速率数据算一次**，
 * 落库的是结果（`patMs` / `patSpreadMs` / `patSeriesMs`）。
 *
 * 这与 `Hrv` 存 RR 间期同理由：**全速率的输入本来就没保留**，所以"结果"就是它的输入。
 */
object Ptt {

    // ═══════════════════════ 阈值（全部可被台架反推） ═══════════════════════

    /**
     * PAT **算法口径的版本号**。改任何会改变 `patMs` 含义的东西时必须 +1。
     *
     * ## 为什么需要它（这是"两个口径不许默默混"的 PAT 版）
     *
     * 手机端拿历史 `patMs` 算"自身基线"，再跟最新一次比。如果历史里有**用另一套口径**
     * 算出来的值，那个基线就是两个人群的混合统计量 —— **不报错、不提示**，
     * 只是分母悄悄变了（与 `Hrv` 的两套基线、`Backfill` 的来源冒充同一类错误）。
     *
     * 版本史：
     * · `1`（v60~v94）：R 参考点是**滑窗积分包络的峰**（比真实 QRS 晚 ~100ms、逐拍抖动），
     *   波足判据写死"谷 → 峰"。**这一版的值与后续不可比**（系统偏小 30~80ms）。
     * · `2`（v65/v95 起）：R 参考点吸附回**真实 QRS**（`EcgSignal.snapToQrs`），
     *   波足判据改为**与极性无关**的"最陡上冲段"。
     *
     * 手机端只把 `patAlgo >= ALGO_VERSION` 的记录算进基线；老记录不删（那是用户数据），
     * 只是不进基线，并在界面上说明。
     */
    const val ALGO_VERSION = 2

    /**
     * 逐拍 PAT 的生理下界（毫秒）。
     *
     * 60 次/分时 R 峰到腕部脉搏约 200~300 ms。取 80 是留足余量的下界：
     * 比它更短的「PAT」几乎一定是**在 QRS 里找错了点**（把 R 波本身当成波足），
     * 而那不报错、只会给出一个偏小的数。
     */
    const val PAT_MIN_MS = 80

    /** 逐拍 PAT 的生理上界（毫秒）。超过它多半是把**下一个心搏**的脉搏波当成了这一拍的。 */
    const val PAT_MAX_MS = 500

    /**
     * 波足在上升沿上的定义位置：**波谷之上、峰谷差的这个比例处**。
     *
     * ## 为什么不用「搜索窗里的最低点」
     *
     * 那是我第一版的做法，它在合成信号上一次就露馅了：舒张期是一条从上一拍衰减下来的
     * **平坦/单调下降段**，其中每个点的值都等于（或接近）波足的值 —— 于是 `argmin`
     * 返回的是**窗内最早的那个**下界点，而不是波足。算出来的「PAT」会等于搜索窗起点，
     * 看起来是个很正常的毫秒数。
     *
     * 改成「上冲上的电平穿越」：**从这次上冲的起点往终点走**，第一个越过该电平的点
     * （`Ptt.analyze` 里就是这么做的；两个相邻有值点之间线性插值出穿越时刻）。
     * 这是 PTT 文献里的标准做法，对真实信号也更稳 —— 它锚在**上冲段**上，
     * 而上冲段是整段波形里信噪比最高的地方。
     *
     * ⚠ "起点"不能假定是波谷：这一路的极性是反的（见 [steepestUpstroke] 的注释），
     * 起点的物理含义是"**这次上冲刚开始**"，是谷还是峰由 [steepestUpstroke] 判定。
     *
     * 10% 是个约定，不是生理常数：真正的「波足」取决于血流起始时刻，
     * 所有基于波足的方法都在用某个固定水位近似它 —— 关键是**同一个水位**，
     * 这样不同次测量之间才可比（本模块的用途正是"比自己的基线"）。
     */
    const val FOOT_LEVEL_FRACTION = 0.10

    /**
     * 参与统计的最少心搏数。
     *
     * 比 [Hrv.MIN_BEATS]（20）宽松：HRV 要的是频域跨度，PAT 只取中位数与离散度，
     * 而 30 秒心电在 70 次/分时约 35 拍，扣掉开头结尾的无效窗后通常剩 20~30 拍。
     * 取 8 是"至少能说明多数心搏给出了一致的答案"。
     */
    const val MIN_BEATS = 8

    /**
     * 逐拍离散度上限（毫秒，1.4826×MAD）。
     *
     * 超过它说明**这一批拍之间自己就不一致**，中位数没有代表性 —— 此时给一个数
     * 比拒答更糟。参考：静息状态下逐拍 PAT 的生理波动约 ±5~15 ms。
     */
    const val MAX_SPREAD_MS = 40

    /**
     * 算斜率用的**中心差分时间跨度**（毫秒）。
     *
     * 等价于"逐点差分再平滑"，但一步做完、且与缺样疏密无关（见 [steepestUpstroke]）。
     * 取 60ms：真机这一路的收缩期上冲约 160ms，60ms 的中心差分既压得住噪声，
     * 又不会把上冲的那点曲率抹平。
     */
    const val SLOPE_SPAN_MS = 60.0

    /**
     * "上冲结束"的判据：斜率朝该方向的分量掉到**峰值斜率的**这么多倍以下就算到头。
     *
     * 取 0.25 而不是 0：平台段（舒张期尾、基线）的斜率是 0，用 0 做判据会让
     * "上冲起点"一路滑到窗边；而 0.25 能同时把"平台"与"反向"判成结束。
     */
    const val SLOPE_KNEE_FRACTION = 0.25

    /** R 峰之后从这里开始找脉搏波（毫秒）。比它更早的窗里只有 QRS 本身。 */
    const val SEARCH_FROM_MS = 60

    /**
     * 搜索窗的默认上界（毫秒）。
     *
     * 窗里要放得下**这一拍脉搏的"波足 + 收缩期上升 + 波峰"**，而滤波后的去趋势波形上
     * 这三者跨约 500 ms（真机实测：波谷在 R 后约 350 ms、峰在约 500 ms）。
     * 取 700 ms 留足余量；实际还会被**当前 RR** 收紧（见 [analyze]），
     * 免得高心率时窗越过下一拍。
     *
     * ⚠ 2026-09-27 从 450 提到 700：**R 峰参考点被吸附回真实 QRS 之后整体前移了约 100 ms**
     * （见 `EcgSignal.snapToQrs`），于是原来"刚好够用"的 450 ms 把脉搏挤出了窗外 ——
     * 表现是 41 拍里 22 拍"时延越界"。**改参考点就必须同时复核这个窗。**
     */
    const val SEARCH_TO_MS = 700

    /**
     * 相邻两个**有值点**之间的最大间隔（毫秒）。超过它就认为"这两点之间没有脉搏波"，
     * 不许跨着它做任何推断（[analyze] 据此把整段切成"可用段"）。
     *
     * ## 为什么需要它（真机实测）
     *
     * 心电自带的这一路 **每 5 个包才 1 个值**（15220 点里只有 3044 点有值，
     * 其余 80% 是 `-1`＝"这一包没有脉搏波"）—— 也就是说它的**真实采样率约 100 Hz**，
     * 不是最初以为的 500 Hz。所以"窗里不能有任何缺样"这种要求永远不可能满足，
     * 只能改成"窗里要有足够多、且够密的有值点"。
     *
     * 取 40 ms 的依据：它对应 **≥25 Hz 的有效采样率**，与另一条 PPG 通道
     * （`PPG_CONTINUOUS`）同量级；再稀就分辨不出上升沿（收缩期上升段本身只有几十毫秒）。
     * 实测间隔约 10 ms（5 个包），留有 4 倍余量。
     */
    const val MAX_GAP_MS = 40

    /**
     * 一个搜索窗里至少要有的**有值点**个数。
     *
     * 一个窗约 640 ms（[SEARCH_FROM_MS] → 典型上界），在 100 Hz 下约 64 个点。
     * 取 6 是下限中的下限：够放下波谷、波峰与上升沿上至少一个中间点。
     * 比它更少时"峰谷"是猜出来的，宁可不给（丢弃计数里单独有一类）。
     */
    const val MIN_WINDOW_SAMPLES = 6

    /**
     * "可用块"的最短时长（毫秒）：至少要放得下**一个完整的搜索窗**
     * （[SEARCH_FROM_MS] → [SEARCH_TO_MS] 是 640 ms，取 700 ms 留余量）。
     *
     * ⚠ 与上一版（`MIN_SEGMENT_MS = 8 拍`）不同：那时只取**最长的一段**，
     * 所以一段必须够长；现在**所有块都参与**（见 [analyze]），
     * 短块也能贡献一两拍，于是下限降到"能放下一个窗"。
     *
     * 真机上这一路是**分块**的（诊断行实测：有值点间隔中位 5 包、**最大 2150 包 ≈ 4.3 秒**），
     * 只取一段会把其余块整段浪费掉 —— 46 拍里有 34 拍就是这么被丢的。
     */
    const val MIN_BLOCK_MS = 700

    /** 建立"自身基线"所需的最少会话数。不足时界面必须说"基线还没建立"，而不是拿一次当基线。 */
    const val BASELINE_MIN_SESSIONS = 3

    /**
     * **去趋势的门槛**：拟合直线在窗内的总变化，达到"残差摆幅"的这么多倍时，
     * 才认为这一窗是被**慢漂移主导**的、应当把那条直线减掉。
     *
     * ## 为什么要"有条件地"去趋势，而不是无条件
     *
     * 这一路真机实测混着**比脉搏大好几倍的慢漂移**（每拍窗内是一条约 2 万计数的单调摆动，
     * 而脉搏只有约 4000 计数）。不去掉它，窗内的 argmax/argmin 找到的是漂移而不是脉搏 ——
     * 实测 41 拍里只剩 5 拍能算出时延。
     *
     * 但**无条件减掉一条直线会把脉搏自己也削掉**：当窗内的脉搏本身就是主要成分时
     * （没有漂移、或漂移很小时），拟合出的那条线主要由脉搏自己的斜率决定，
     * 减掉之后幅度会塌掉（合成波形上实测：幅度从 0.010 掉到 0，直接变成"起伏太小"）。
     *
     * 所以判据是「**谁占主导**」：趋势的窗内变化 ≥ [DETREND_TREND_RATIO] × 残差摆幅
     * ⇒ 漂移占主导 ⇒ 减掉它；否则保持原样。
     *
     * 取 1.5 的依据（两组数据实测）：
     * · 纯脉搏（合成）：比值恒为 **1.00** → 不低于 1.5 ⇒ 不去趋势 ✓ 幅度不受损；
     * · 真机那一路：比值跨 0.42~2.79（中位 1.28）→ 1.5 时 **41/41 拍**算出、
     *   中位 274 ms；而取 2.0 会多丢 5 拍（该去趋势的没去）、取 3.0 丢 8 拍。
     *
     * ⚠ 它是**无量纲比值**，不是绝对阈值 —— 换设备/换量级都不用改。
     */
    const val DETREND_TREND_RATIO = 1.5

    // ═══════════════════════ 对外口径（界面直接引用，措辞只有一处定义） ═══════════════════════

    /** 这个量是什么。 */
    val WHAT_IT_IS: String get() =
        Text.of("s_4667ccf9")

    /**
     * ⚠ 为什么它不是血压。**这段必须在界面上，不折叠。**
     *
     * 写成常量而不是散在界面里，是因为它是本模块最重要的一句话 ——
     * 一旦被某处简化成「血压趋势」，整个功能就变成了一个假的测量值。
     */
    val NOT_BP: String get() =
        Text.of("s_56e188da") +
            Text.of("s_fb432e41") +
            Text.of("s_0f7492a6") +
            Text.of("s_b515b90c")

    /** 与自身基线比较时的措辞（不给正常范围 —— 与 HRV、压力分同一条纪律）。 */
    fun deviationLabel(deviationMs: Int): String = when {
        deviationMs <= -15 -> Text.fmt("s_e7cd15b2", -deviationMs)
        deviationMs < -5 -> Text.fmt("s_6a480f63", -deviationMs)
        deviationMs <= 5 -> Text.of("s_e61a6cab")
        deviationMs < 15 -> Text.fmt("s_1c5cb884", deviationMs)
        else -> Text.fmt("s_1a407d1a", deviationMs)
    }

    // ═══════════════════════ 结果 ═══════════════════════

    /** 一拍的结果（下标是采样点序号；PAT 是毫秒）。 */
    data class Beat(val rIndex: Int, val footIndex: Int, val patMs: Int, val amp: Double)

    sealed interface Result {
        /** 算出来了。 */
        data class Ok(
            /** 逐拍 PAT 的中位数（毫秒）。**用中位数不用均值**：一次找错点就能把均值拉飞。 */
            val medianMs: Int,
            /** 逐拍离散度（毫秒，1.4826×MAD）。 */
            val spreadMs: Int,
            /** 逐拍序列（毫秒），落库并画图用。 */
            val seriesMs: List<Int>,
            /** 参与统计的拍数。 */
            val beats: Int,
            /** 因**幅度不足**被丢的拍数（界面据此区分"表没戴好"与"信号形态不对"）。 */
            val droppedLowAmp: Int,
            /**
             * 因**搜索窗里有值点太少**被丢的拍数（真机上这一路只有约 20% 的包带脉搏波）。
             *
             * ⚠ 与 [droppedLowAmp] 分开计数是**必需的**，不能合成一句"没找到波足"：
             * 这两件事对用户、对排查完全是两回事 —— 一个是"表没戴好"，一个是"这一路信号本来就稀
             * / 这段没有数据"。2026-09-24 的线上故障就是被这类合计数掩盖的
             * （详见 [Ppg.MISSING] 的注释）。
             */
            val droppedSparse: Int,
            /** 因**取不到搜索窗**被丢的拍数（波形首尾越界，或相邻两拍挤得太近）。 */
            val droppedNoWindow: Int,
            /** 因**算出的时延不在生理值域内**被丢的拍数（多半是在波形里找错了点）。 */
            val droppedPatRange: Int,
            /**
             * 逐拍峰谷幅度的**中位数**（归一化，无量纲）。
             *
             * 它是这次结论的可信度证据，界面照实显示 —— 幅度 0.002 的会话与幅度 0.02 的
             * 会话给出的 PAT 不是同等可信，而这个差别只有这个数能说。
             */
            val ampP2P: Double,
            /** 心电一路的心率（次/分），来自调用方。null = 没给。 */
            val ecgHr: Int?,
            /** PPG 一路独立估出的心率（次/分）。null = 没给。 */
            val ppgHr: Int?,
            /** 两路心率相对偏差（0~1）。null = 两路里至少一路没有值。 */
            val hrDeviation: Double?,
        ) : Result

        /**
         * 不给结论。**拒答是合法输出** —— 比一个自信的错数好。
         *
         * @param reason 面向用户的一句话，必须说清是"哪一类"不行
         *   （稀疏 / 拍数不足 / 波动过大 / 幅度不足 / 两路不一致），
         *   而不是笼统一句"无法测量"。
         * @param detail 给**排查**看的数字证据（可选）。
         *
         * ## 为什么要带 detail
         *
         * 2026-09-24 那次"一次都算不出来"卡了两轮，就是因为界面上只有一句**措辞**
         * （而那句还指向一个不可达分支）。第二次能定案，靠的是手表端落库的
         * **原始值分位数 + 有值点间隔**。所以：**拒答时能带数字就带数字** ——
         * 它会被存进 `patNote`、显示在手机上，是下一次改动的唯一依据。
         * 措辞给人看，数字给排查看，两者不混在一句里。
         */
        data class NotApplicable(val reason: String, val detail: String? = null) : Result
    }

    // ═══════════════════════ 主入口 ═══════════════════════

    /**
     * 从心电 R 峰与同会话的 PPG 波形算 PAT。
     *
     * @param ppg 归一化 PPG 波形，**必须是用 [Ppg.normalizeAligned] 得到的**（下标与心电一一对应）。
     *   传 `Ppg.normalize` 的结果是**错的**：那个会剔除无效样本，下标一移 PAT 就整体偏小。
     * @param rPeaks 心电 R 峰的**采样点下标**（升序）。来自 `EcgSignal.detectQrsPeaks` 那条链路，
     *   不要在这里再检一遍 —— 「两边各写一套必然漂移」。
     * @param sampleRateHz 实测采样率。两路信号同率，所以只有一个。
     * @param ecgHr 心电一路的心率，用于交叉校验。
     * @param ppgHr PPG 一路独立估出的心率，用于交叉校验。
     */
    fun analyze(
        ppg: Ppg.Aligned,
        rPeaks: List<Int>,
        sampleRateHz: Double,
        ecgHr: Int? = null,
        ppgHr: Int? = null,
    ): Result {
        if (sampleRateHz <= 0.0 || sampleRateHz.isNaN()) {
            return Result.NotApplicable(Text.of("s_9e978bdb"))
        }
        val norm = ppg.norm
        val n = norm.size
        if (n < 2) return Result.NotApplicable(Text.of("s_9c2b2915"))
        if (rPeaks.size < MIN_BEATS) {
            return Result.NotApplicable(
                Text.fmt("s_65744483", rPeaks.size, MIN_BEATS)
            )
        }

        // ① 交叉校验：两路**独立**得出的心率必须一致。
        //    ⚠ 这是唯一能抓住"两路时间轴没对齐"的检查 —— 对齐错了心率就必然对不上。
        //    复用 Hrv 的同一个常量（同一条"独立一致性校验"的纪律，不另立阈值）。
        val dev: Double? = if (ecgHr != null && ecgHr > 0 && ppgHr != null) {
            abs(ppgHr - ecgHr).toDouble() / ecgHr
        } else {
            null
        }
        if (dev != null && dev > Hrv.REF_HR_MAX_DEVIATION) {
            return Result.NotApplicable(
                Text.fmt("s_22250a66", ppgHr, ecgHr, (dev * 100).roundToInt()) +
                    Text.of("s_1cfc6dcd")
            )
        }

        // ② 先在**原始下标轴**上找"可用段"，再逐拍在其中找波足
        //
        //   ⚠ 真机上这一路每 5 个包才 1 个有值（80% 是 -1）。所以：
        //   · **不许压缩掉缺样**（压缩 = 后面所有点的时刻整体前移 = 每个 PAT 都偏小、静默）；
        //   · 也**不许要求窗里没有缺样**（那等于功能永远失效）；
        //   · 正确做法是保留原下标、跳过缺样位置，只要求"相邻有值点的间隔 ≤ MAX_GAP_MS"。
        val gapLimit = (MAX_GAP_MS / 1_000.0 * sampleRateHz).roundToInt().coerceAtLeast(1)
        val validIdx = ArrayList<Int>(n / 2)
        for (i in 0 until n) if (!norm[i].isNaN()) validIdx.add(i)
        // ⚠ **所有**够长的块都参与，而不是只取最长的那一段。
        //   真机实测这一路是**分块**的（有值点间隔中位 5 包、最大 2150 包 ≈ 4.3 秒），
        //   只取一段会把其余块整段浪费 —— 46 拍里有 34 拍就是这么被丢掉的。
        //   块之间隔开没关系：PAT 是**拍内**的相对时延，不依赖块与块之间的连续性。
        val minBlockSamples = (MIN_BLOCK_MS / 1_000.0 * sampleRateHz).roundToInt().coerceAtLeast(2)
        val blocks = usableBlocks(validIdx, gapLimit, minBlockSamples)
        if (blocks.isEmpty()) {
            return Result.NotApplicable(
                Text.fmt("s_b8e28369", validIdx.size) +
                    Text.fmt("s_353d9c43", MIN_BLOCK_MS),
                detail = Text.fmt("s_91426bc6", longestSpanMs(validIdx, gapLimit, sampleRateHz)) +
                    Text.fmt("s_21ac845b", (ppg.quality * 100).roundToInt()),
            )
        }
        val blockSpanSec = blocks.maxOf { it.last - it.first } / sampleRateHz

        // 逐拍找波足
        val beats = ArrayList<Beat>(rPeaks.size)
        var lowAmp = 0
        var sparse = 0
        var noWindow = 0
        var patRange = 0
        // 排查用：越界那批拍算出来的时延（只用于 detail，不参与结论）
        val patRangeMs = ArrayList<Int>(8)
        var bi = 0
        for (i in rPeaks.indices) {
            val r = rPeaks[i]
            // 块与 R 峰都升序 → 单次扫描即可定位这一拍落在哪个块里
            while (bi < blocks.size && blocks[bi].last < r) bi++
            val blk = blocks.getOrNull(bi)
            if (blk == null || r < blk.first) {
                noWindow++
                continue
            }
            // RR 用采样点数表示；后面几处都换成毫秒再比
            val rrSamples = if (i + 1 < rPeaks.size) rPeaks[i + 1] - r else 0
            val rrMs = if (rrSamples > 0) rrSamples / sampleRateHz * 1_000.0 else 0.0

            // 搜索窗上界随 RR 收紧：高心率时窗不能越过下一拍的脉搏。
            //
            // ⚠ 系数是 **1.0 而不是 0.6**：窗里要放得下"波足 + 收缩期上升 + 波峰"，
            //   而这一路真机实测波足就在 R 后约 350 ms —— 0.6×RR 在 RR≈690 ms 时
            //   只有 414 ms，正好把波峰切掉（2026-09-27 实测：41 拍里 22 拍越界）。
            //   而"越过下一拍"的担心并不成立：下一拍的**波足**在 RR+PAT ≈ RR+350 ms 处，
            //   跑到 1.0×RR 还差得远。
            val toMs = if (rrMs > 0) {
                min(SEARCH_TO_MS.toDouble(), rrMs)
            } else {
                SEARCH_TO_MS.toDouble()
            }
            if (toMs <= SEARCH_FROM_MS) {
                noWindow++
                continue
            }
            val from = r + (SEARCH_FROM_MS / 1_000.0 * sampleRateHz).roundToInt()
            // ⚠ 窗的上界还要夹在**本块**内：越过块尾就是越过一段没有脉搏波的时段
            val to = min(r + (toMs / 1_000.0 * sampleRateHz).roundToInt(), blk.last)
            if (from < 0 || from < blk.first || to >= n || to <= from) {
                noWindow++
                continue
            }

            // 窗内**有值点**（跳过缺样位置，不为它们编造任何东西）
            val idx = ArrayList<Int>(64)
            for (k in from..to) if (!norm[k].isNaN()) idx.add(k)
            if (idx.size < MIN_WINDOW_SAMPLES) {
                sparse++
                continue
            }

            // ── 先去掉这一拍自己的**线性趋势**（这一路混着比脉搏大好几倍的慢漂移）──
            //
            // 真机实测：每拍窗内的原始值是一条**单调大幅摆动**（~2 万计数 / 400 ms），
            // 而脉搏只有 ~4000 计数（按 R 峰对齐做 41 拍平均才看得出来）。
            // 不去掉它，窗内 argmax/argmin 找到的是漂移而不是脉搏 —— 实测那会让
            // 41 拍里只剩 5 拍能算出时延。去掉之后 41/41 拍全部算出，
            // 中位 267 ms（生理上合理的腕部 PAT）。
            //
            // 为什么用**每拍局部**的最小二乘直线而不是全局高通：漂移在 400 ms 尺度上
            // 近似直线，局部拟合把脉搏那点斜率一起减掉的代价很小；而全局滤波
            // （滑动均值）对"0.2~0.5 Hz 的漂移"抑制不足（实测仍剩 3~6 拍算不出来）。
            val fit = localDetrend(norm, idx)
            // 残差摆幅 vs 趋势幅度：漂移占主导才减（理由与实测依据见 [DETREND_TREND_RATIO]）
            var resLo = fit.values[0]
            var resHi = fit.values[0]
            for (v in fit.values) {
                if (v < resLo) resLo = v
                if (v > resHi) resHi = v
            }
            val det = if (fit.trendExcursion >= DETREND_TREND_RATIO * (resHi - resLo)) {
                fit.values
            } else {
                DoubleArray(idx.size) { norm[idx[it]].toDouble() }
            }

            // ── 波足 = 「收缩期上冲」的起点 ──
            //
            // ⚠⚠ 判据必须**与极性无关**。这一路 PPG 的极性不固定，取决于 LED/接触状态：
            //   真机两场录制里，**血容量上升对应的是计数下降**（每拍极大在 R 后 ~170ms、
            //   极小在 ~340ms），也就是"降得快的那一段（160ms）是收缩期上冲"，
            //   而缓升的那一段（530ms，约 3 倍长）才是舒张期。
            //   旧版写死"先找谷、再找谷之后的峰"，在这份数据上等于把**舒张期当成了收缩期**，
            //   于是"波足"落在一个与脉搏无关的位置上 —— 数值看着很稳（离散度 22ms），
            //   其实量的不是 PAT。**"稳定"不等于"对"，这是 2026-09-27 最大的教训。**
            //
            // 生理上唯一不变的性质是**不对称**：收缩期上冲又短又陡，舒张期又长又缓。
            // 所以判据是"**相邻极值之间最陡的那一段**"，它的早端就是波足：
            // · 正常极性（升得快）：早端是谷 —— 与旧版"谷 → 峰"等价，不改变已有行为
            //   （合成台架那一支就是这么构造的，断言值不变）；
            // · 反极性（降得快）：早端是峰 —— 这正是旧版漏掉的那一半。
            val seg = steepestUpstroke(det, idx, sampleRateHz)
            if (seg == null) {
                // 窗内找不出两个像样的极值 → 这一段没有可辨的搏动
                lowAmp++
                continue
            }
            val vEarly = det[seg.first]
            val vLate = det[seg.second]
            val amp = abs(vLate - vEarly)
            if (amp < Ppg.MIN_PULSE_AMP) {
                // 幅度不足是**最常见**的一种失败，单独计数，好让拒答理由说得准
                lowAmp++
                continue
            }

            // 波足 = 从**早端**沿着这一段走 [FOOT_LEVEL_FRACTION] 处的电平穿越点。
            // （与旧版同一套约定：不取极值点本身，而是上冲了 10% 的地方 ——
            //   极值点对噪声与去趋势残差都敏感，10% 电平稳得多。）
            //
            // ⚠ 电平**一定**在这一段之内被越过（早端在电平一侧、晚端在另一侧且更远），
            //   所以不存在"找不到"这个分支。旧版写成"找不到就丢这一拍"，那个分支
            //   永远不执行，却让人以为失败原因是"找不到上升沿" —— 2026-09-24 排查时
            //   正是这句话把方向带偏了一整轮。**不可达的分支会变成一句骗人的理由。**
            val rising = vLate > vEarly
            val level = vEarly + (vLate - vEarly) * FOOT_LEVEL_FRACTION
            var p = seg.first
            while (p < seg.second && (if (rising) det[p] < level else det[p] > level)) p++

            // 穿越时刻：在 idx[p] 与前一个**有值点** idx[p-1] 之间线性插值。
            // ⚠ 这两点都是真实测量，插的只是**它们之间的时刻**；而 [MAX_GAP_MS]
            //   保证了它们足够近，近到"这一段是直的"成立。绝不跨过缺样去做这件事。
            val cross = if (p > seg.first) {
                val i0 = idx[p - 1]
                val v0 = det[p - 1]
                val i1 = idx[p]
                val v1 = det[p]
                val denom = (v1 - v0)
                if (denom == 0.0) i1.toDouble() else i0 + (i1 - i0) * (level - v0) / denom
            } else {
                idx[seg.first].toDouble()
            }
            val patMs = ((cross - r) / sampleRateHz * 1_000.0).roundToInt()
            if (patMs < PAT_MIN_MS || patMs > PAT_MAX_MS) {
                patRange++
                patRangeMs.add(patMs)
                continue
            }
            beats.add(Beat(rIndex = r, footIndex = cross.roundToInt(), patMs = patMs, amp = amp))
        }

        if (beats.size < MIN_BEATS) {
            // ⚠ 理由必须**按类分开说**，不能合成一句"没定出波足"：
            //   「起伏太小」是表没戴好、「有效点太少」是这一路本来就稀、「时延越界」是形态不对，
            //   三件事对用户和排查都是不同的动作。合起来说就会变成一句**指错方向**的话
            //   （这条是 2026-09-24 那次线上故障换来的：界面说"找不到上升沿"，
            //   真因是"搜索窗里有缺样"，于是白猜了一轮）。
            val causes = ArrayList<String>(4)
            if (lowAmp > 0) {
                causes.add(Text.fmt("s_59607bc4", lowAmp))
            }
            if (sparse > 0) {
                causes.add(
                    Text.fmt("s_0628cf70", sparse) +
                        Text.fmt("s_1bf06354", (ppg.quality * 100).roundToInt())
                )
            }
            if (patRange > 0) {
                causes.add(Text.fmt("s_19a64c8e", patRange, PAT_MIN_MS, PAT_MAX_MS))
            }
            if (noWindow > 0) {
                causes.add(Text.fmt("s_05d7346b", noWindow))
            }
            // 数字证据（给排查，不给人）：越界那批到底算出多少毫秒、块结构如何。
            // 2026-09-24 那次两轮才定案，就是因为只有措辞没有数字。
            val detail = buildString {
                append(Text.fmt("s_dd7052b7", blocks.size))
                append(String.format(java.util.Locale.US, "%.1f", blockSpanSec))
                append(Text.fmt("s_421f28b5", (ppg.quality * 100).roundToInt()))
                if (patRangeMs.isNotEmpty()) {
                    val sorted = patRangeMs.sorted()
                    append(Text.fmt("s_2ccfbf11", patRangeMs.size))
                    append("${sorted.first()}~${sorted.last()} ms")
                    append(Text.fmt("s_56ab5d19", sorted[sorted.size / 2]))
                }
                if (beats.isNotEmpty()) {
                    append(Text.fmt("s_a892c77b", beats.size))
                }
            }
            return Result.NotApplicable(
                Text.fmt("s_c18d2520", beats.size, MIN_BEATS) +
                    Text.fmt("s_d05d6449", rPeaks.size - beats.size) +
                    causes.joinToString(Text.lang.itemSeparator()) + Text.of("s_5a303dec"),
                detail = detail,
            )
        }

        val series = beats.map { it.patMs }
        val med = Hrv.median(series)
        val spread = robustSpread(series, med)
        if (spread > MAX_SPREAD_MS) {
            return Result.NotApplicable(
                Text.fmt("s_48c84e5c", spread.roundToInt(), MAX_SPREAD_MS) +
                    Text.of("s_16400509")
            )
        }

        return Result.Ok(
            medianMs = med.roundToInt(),
            spreadMs = spread.roundToInt(),
            seriesMs = series,
            beats = beats.size,
            droppedLowAmp = lowAmp,
            droppedSparse = sparse,
            droppedNoWindow = noWindow,
            droppedPatRange = patRange,
            // 幅度的中位数：整数微单位往返一下，复用 Hrv.median（它只收 List<Int>），
            // 比在这里再写一个 Double 版中位数更省 —— 幅度只用于显示与量级判断。
            ampP2P = Hrv.median(beats.map { (it.amp * 1_000_000).roundToInt() }) / 1_000_000.0,
            ecgHr = ecgHr,
            ppgHr = ppgHr,
            hrDeviation = dev,
        )
    }

    // ═══════════════════════ 基线（相对自己） ═══════════════════════

    /**
     * 从历史会话的中位 PAT 求"自身基线"。
     *
     * 不足 [BASELINE_MIN_SESSIONS] 次返回 null —— 界面必须据此显示"基线还没建立"，
     * **不能**拿一次的值当基线（那会让第二次测量的"偏离"完全没有意义）。
     */
    fun baseline(historyMedianMs: List<Int>): Int? =
        if (historyMedianMs.size < BASELINE_MIN_SESSIONS) {
            null
        } else {
            Hrv.median(historyMedianMs).roundToInt()
        }

    // ═══════════════════════ 内部工具 ═══════════════════════

    /**
     * 找"**收缩期上冲**"这一段，返回它的两端在 `det` 里的下标（早端, 晚端）。
     *
     * ## 为什么不能写死"先找谷、再找峰"
     *
     * **这一路 PPG 的极性不固定**（取决于 LED/接触状态）。真机两场录制里，
     * 血容量上升对应的是**计数下降**：每拍极大在 R 后 ~170ms、极小在 ~340ms，
     * 即"降得快的那一段（160ms）才是收缩期上冲"，而缓升的那一段（530ms，约 3 倍长）
     * 才是舒张期。写死"谷 → 峰"会把**舒张期当成收缩期**，于是"波足"落在与脉搏无关的
     * 位置上 —— 数值看着很稳（离散度 22ms），其实量的不是 PAT。
     * **"稳定"不等于"对"，这是 2026-09-27 最大的教训。**
     *
     * ## 为什么也不能"找相邻极值里最陡的一段"（那是我上一版，已被证伪）
     *
     * 波足常常**落在平台或慢段上**（舒张期末尾是一条平缓的基线），
     * 于是它**不是**严格局部极值 → "上冲"这一段根本不进候选 → 算法会退而选中**衰减段**，
     * 把波足定到峰上（合成台架实测：注入 200ms 复原成 275ms，正好多出一个"峰 + 10% 衰减"）。
     *
     * ## 现在用的判据（只依赖两个生理不变量）
     *
     * 1. **收缩期上冲最陡**（舒张期又长又缓）→ 先找窗内**斜率绝对值最大**的位置；
     * 2. 从那里**往两端走**，直到"不再朝这个方向走"为止 —— 那两个端点就是上冲的起止。
     *
     * 斜率用**固定时间跨度的中心差分**（[SLOPE_SPAN_MS]）算，等于"逐点差分 + 平滑"一步做完，
     * 且与缺样疏密无关（稀疏时不会因为点少而算出假斜率）。
     * 端点判据是"斜率朝该方向的分量掉到峰值斜率的 [SLOPE_KNEE_FRACTION] 以下"——
     * 对"平台"（斜率 0）与"反向"都成立，所以两种极性都落在正确的段上：
     * · 正常极性：早端是谷 —— 与旧版"谷 → 峰"等价（合成台架断言值不变）；
     * · 反极性：早端是峰 —— 这正是旧版漏掉的那一半。
     *
     * @return 段太短（不足两点）时返回 null。
     */
    private fun steepestUpstroke(
        det: DoubleArray,
        idx: List<Int>,
        sampleRateHz: Double,
    ): Pair<Int, Int>? {
        val n = det.size
        if (n < 3) return null
        val spanSamples = SLOPE_SPAN_MS / 1_000.0 * sampleRateHz
        val slope = DoubleArray(n)
        for (k in 0 until n) {
            var m = 0
            while (k + m + 1 < n && k - m - 1 >= 0 &&
                (idx[k + m + 1] - idx[k - m - 1]) <= spanSamples
            ) {
                m++
            }
            val a = (k - m).coerceAtLeast(0)
            val b = (k + m).coerceAtMost(n - 1)
            val dt = (idx[b] - idx[a]).toDouble()
            slope[k] = if (dt > 0.0) (det[b] - det[a]) / dt else 0.0
        }
        var kSteep = 0
        var bestAbs = 0.0
        for (k in 0 until n) {
            val v = abs(slope[k])
            if (v > bestAbs) {
                bestAbs = v
                kSteep = k
            }
        }
        if (bestAbs <= 0.0) return null
        val dir = if (slope[kSteep] > 0.0) 1.0 else -1.0
        val knee = bestAbs * SLOPE_KNEE_FRACTION
        var a = kSteep
        while (a > 0 && slope[a - 1] * dir > knee) a--
        var b = kSteep
        while (b < n - 1 && slope[b + 1] * dir > knee) b++
        return if (b > a) (a to b) else null
    }

    /**
     * 对一组**有值点**（`idx` 是它们在 `norm` 里的下标）做**最小二乘线性去趋势**，
     * 返回逐个有值点的残差（顺序与 `idx` 一致）。
     *
     * 输入 `norm` 里缺样的位置是 NaN，所以只能拿 `idx` 指定的点去拟合 ——
     * **不能**直接对整个列表做滑动均值：NaN 会把窗口里的均值污染掉，
     * 而且那样也分不出"这一拍自己的趋势"。
     */
    /** [localDetrend] 的结果：残差 + 拟合直线在窗内的总变化（判断"漂移是否占主导"用）。 */
    private class Detrend(val values: DoubleArray, val trendExcursion: Double)

    private fun localDetrend(norm: List<Float>, idx: List<Int>): Detrend {
        val n = idx.size
        if (n < 3) {
            return Detrend(DoubleArray(n) { norm[idx[it]].toDouble() }, 0.0)
        }
        // 用**相对第一个点的偏移**做自变量：真机上的下标是 5 位数，直接平方会掉精度
        val x0 = idx[0]
        var sx = 0.0
        var sy = 0.0
        var sxx = 0.0
        var sxy = 0.0
        for (p in 0 until n) {
            val x = (idx[p] - x0).toDouble()
            val y = norm[idx[p]].toDouble()
            sx += x
            sy += y
            sxx += x * x
            sxy += x * y
        }
        val denom = n * sxx - sx * sx
        val b = if (abs(denom) < 1e-9) 0.0 else (n * sxy - sx * sy) / denom
        val a = (sy - b * sx) / n
        val out = DoubleArray(n)
        for (p in 0 until n) {
            out[p] = norm[idx[p]] - (a + b * (idx[p] - x0))
        }
        val span = (idx[n - 1] - x0).toDouble()
        return Detrend(out, abs(b) * span)
    }

    /**
     * 把所有**可用块**找出来：块内相邻有值点的间隔都不超过 [gapLimit]，
     * 且块的跨度（末 − 首）不小于 [minSpan]。返回按时间升序的区间列表。
     *
     * 为什么要有"块"这一层：真机上这一路 80% 的包没有值、而且**不保证连续**
     * （实测有值点间隔中位 5 包、**最大 2150 包 ≈ 4.3 秒**）。若不做这一层，
     * 那些"整段没有脉搏波"的时段会被当成"窗里缺样"，既说不清原因，
     * 也会把可用时段一起拖没。
     *
     * 为什么返回**全部**块而不是最长那一段：PAT 是**拍内**的相对时延，
     * 不依赖块与块之间的连续性；只取一段会把其余块整段浪费
     * （真机实测 46 拍里有 34 拍就是这么被丢掉的）。
     */
    private fun usableBlocks(valid: List<Int>, gapLimit: Int, minSpan: Int): List<IntRange> {
        if (valid.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>(4)
        var from = valid[0]
        var prev = valid[0]
        for (k in 1 until valid.size) {
            val v = valid[k]
            if (v - prev > gapLimit) {
                if (prev - from >= minSpan) out.add(from..prev)
                from = v
            }
            prev = v
        }
        if (prev - from >= minSpan) out.add(from..prev)
        return out
    }

    /** 诊断用：按 [gapLimit] 切分后，最长那一块的跨度（毫秒）。 */
    private fun longestSpanMs(valid: List<Int>, gapLimit: Int, sampleRateHz: Double): Int {
        if (valid.isEmpty()) return 0
        var best = 0
        var from = valid[0]
        var prev = valid[0]
        for (k in 1 until valid.size) {
            val v = valid[k]
            if (v - prev > gapLimit) {
                if (prev - from > best) best = prev - from
                from = v
            }
            prev = v
        }
        if (prev - from > best) best = prev - from
        return (best / sampleRateHz * 1_000.0).roundToInt()
    }

    /**
     * 稳健离散度：1.4826 × MAD（中位数绝对偏差）。
     *
     * 用 MAD 而不是标准差：一次"找错点"（比如把下一个心搏的波足当成本拍的）
     * 会留下一个远离中位数的值，标准差被它严重拉大，MAD 几乎不动 ——
     * 这与 `Hrv.rrCv` 选择 MAD 的理由是同一条。
     *
     * 1.4826 是正态分布下"让 MAD 与标准差可比"的换算系数。
     */
    private fun robustSpread(values: List<Int>, median: Double): Double {
        if (values.isEmpty()) return 0.0
        val abs2 = values.map { abs(it - median) }
        val sorted = abs2.sorted()
        val n = sorted.size
        val mad = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0
        return 1.4826 * mad
    }
}
