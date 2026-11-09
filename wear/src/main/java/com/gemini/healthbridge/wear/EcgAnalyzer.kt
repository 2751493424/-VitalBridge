package com.gemini.healthbridge.wear

import kotlin.math.abs
import kotlin.math.roundToInt
import com.gemini.healthbridge.core.Text

/**
 * 经典 Pan-Tompkins QRS 检测（1985 原论文的流水线，纯 Kotlin 无依赖）：
 * 去电极偏置 → 5-15Hz 带通（**两级级联=四阶**，见下）→ **微分** → 逐点平方 → 150ms 滑窗积分
 * → SPKI/NPKI 自适应双阈值检测 R 峰 → RR 间期统计 → 按官方四分类输出。
 *
 * ⚠ 2026-09-14 修正：流水线里**微分**这一步曾被漏写（注释有、代码无），
 * 后果是积分峰变宽平顶、峰位在 ±15 样本间抖动，RR 抖动抬高 cv，整齐节律可能被误报为房颤；
 * 同时叠拍模板被抹平，[EcgSignal.assess] 的相干性崩塌（1.71 → 0.47），真心电被判"无接触"。
 *
 * 输入约定：`samples` 可以是**已 AC 耦合的信号**（EcgMeasurer 的输出）或原始 ECG_MV。
 * 后者带着约 200mV 电极偏置，会自动被本类的去偏置步骤处理。
 *
 * ## 前置质量闸门（2026-09-14 重做）
 * 闸门现在由 [EcgSignal.assess] 提供：**拍模板相干性 + R 峰锐度** 双特征。
 * 旧的 `E(5~15Hz)/E(0.5~40Hz)` 频带能量占比已被真机数据证伪——它对白噪声给 0.286、
 * 对纯正弦给 0.366（都越过 0.25），却把真心电配漂移压到 0.020。详见 [EcgSignal] 的类注释。
 *
 * 一个重要后果：新闸门**不再拦截"真心电 + 漂移"**（那里面确实有相干心电，拦掉才是 bug）。
 * 因此下游检测器必须自己能抗住基线漂移，故 5-15Hz 由二阶改为两级级联（四阶）——
 * 二阶裙边在 1Hz 处仅 -17dB，漂移会漏进来生成假峰。此改动经离线回归验证。
 *
 * 仅为个人健康筛查参考，不构成医疗诊断。
 */
object EcgAnalyzer {

    data class Result(
        /** 窦性心律 / 房颤提示 / 心率偏高 / 无法判定 */
        val classification: String,
        /** 由 RR 间期中位数推算的平均心率（次/分），无法判定时可能为 null */
        val heartRate: Int?,
        /** 节律是否整齐（null = 无法评估） */
        val regular: Boolean?,
        /** 检出的 R 峰（心跳）个数 */
        val beatCount: Int,
        /** 一句话依据 */
        val detail: String,
        /** QRS 频带（5-15Hz）峰峰值幅度，mV；用于判断信号强弱。 */
        val amplitudeMv: Float = 0f,
        /** 拍模板相干性（0~2），>= [EcgSignal.COHERENCE_MIN] 视为电极接触良好。 */
        val coherence: Double = 0.0,
        /** 模板 R 峰锐度（0~1），窄脉冲形态判据。 */
        val sharpness: Double = 0.0,
        /** QRS 能量峰值因子 gmax/p20(integ)；2026-09-14 起是**实际起作用的**接触判据。 */
        val crest: Double = 0.0,
        /** 相邻 RR 间期相对差异 >20% 的占比（0~1），房颤的关键指标。 */
        val irregularRatio: Float = 0f,
        /** 鲁棒变异系数（1.4826*MAD/中位RR），对孤立漏检不敏感。 */
        val rrCv: Float = 0f,
        /** 有效 RR 间期个数。 */
        val rrCount: Int = 0,
        /** 实测采样率（Hz），来自 SEQUENCE 序号跨度。 */
        val sampleRateHz: Int = 0,
        /**
         * **未经过滤**的相邻 R 峰间期（毫秒），供模块 #9 HRV 使用。
         *
         * 与上面那个参与节律分类的 `rr`（已剔掉 300~2000ms 之外的值）刻意分开：
         * "一次漏检"留下的约 2 倍长间期正是 HRV 伪迹修正要识别的**线索**，
         * 分类层把它预先丢掉是对的（那会让 cv 虚高），但 HRV 层需要看到原样的序列
         * —— 预先过滤等于把线索销毁，只能把整拍丢弃，反而更失真。
         * 节律分类**仍然用过滤后的 `rr`**，不要改成用这个。
         */
        val rrAllMs: List<Int> = emptyList(),
        /**
         * 最终采用的 R 峰**采样点下标**（升序），模块 #14（PAT）用。
         *
         * ⚠ 它是「包络峰（`peaksF`，已经过 T 波防护与幅度过滤）**再经
         * [EcgSignal.snapToQrs] 吸附回真实 QRS**」之后的那一批。两级都不能省：
         * 拿被过滤掉的假峰去找脉搏波足，只会得到一堆越界的时延；而**不做吸附**，
         * 参考点就整体晚 93~107ms、且逐拍抖动 ±20~25ms（见 `snapToQrs` 的注释）。
         *
         * ⚠ 为什么由这里给出、而不是让 `Ptt` 自己再检一遍：
         * **项目设计说明明确记过「两边各写一套必然漂移」**。QRS 检出只有这一处实现
         * （`EcgSignal.detectQrsPeaks` + `snapToQrs` 那条链路），PAT 只是它的下游消费者。
         */
        val rPeaks: List<Int> = emptyList(),
    )

    /** QRS 频带峰峰值低于此值视为信号过弱，不做分类。 */
    private const val MIN_AMPLITUDE_MV = 0.15f

    private fun inconclusive(
        detail: String,
        hr: Int? = null,
        regular: Boolean? = null,
        beats: Int = 0,
        amplitudeMv: Float = 0f,
        coherence: Double = 0.0,
        sharpness: Double = 0.0,
        crest: Double = 0.0,
        irrRatio: Float = 0f,
        fs: Int = 0,
        cv: Float = 0f,
        rrCount: Int = 0,
    ) = Result("无法判定", hr, regular, beats, detail, amplitudeMv, coherence, sharpness,
        crest, irrRatio, cv, rrCount, fs)

    fun analyze(samples: List<Float>, sampleRateHz: Int): Result {
        val fs = sampleRateHz.coerceIn(50, 500)
        val n = samples.size
        if (n < fs * 5) return inconclusive(Text.of("s_8b6b05b4"), fs = fs)

        // ── 0) 去电极直流偏置 / 慢漂移 ──
        // 带通滤波器在零初态下把几百毫伏的 DC 偏置当阶跃输入，会产生巨大起始暂态并毒化
        // 自适应阈值（实测使整段检测完全失效）。若上游已 AC 耦合（均值近似 0）则跳过，
        // 避免重复高通把 QRS 也削掉。
        var mean = 0.0
        for (v in samples) mean += v
        mean /= n
        val ac = FloatArray(n)
        if (abs(mean) > 1.0) {
            val half = (fs / 2).coerceAtLeast(2)
            val pref = DoubleArray(n + 1)
            for (i in 0 until n) pref[i + 1] = pref[i] + samples[i]
            for (i in 0 until n) {
                val a = maxOf(0, i - half)
                val b = minOf(n - 1, i + half)
                ac[i] = (samples[i] - ((pref[b + 1] - pref[a]) / (b - a + 1))).toFloat()
            }
        } else {
            for (i in 0 until n) ac[i] = samples[i]
        }

        // ── 1) 信号质量闸门：拍模板相干性 + R 峰锐度 + QRS 能量峰值因子 ──
        // 为什么不用频带能量占比：它对白噪声给 0.286、对纯正弦给 0.366（都越过 0.25），
        // 却把真心电配漂移压到 0.020 —— 详见 EcgSignal 类注释里的实测表。
        // 现在的主力判据是 crest（心电是稀疏瞬态串，伪迹包络平稳）；
        // 相干性与锐度保留为可解释的中间量（极性一致化对齐后它们已不再有区分力）。
        val q = EcgSignal.assess(ac, fs.toDouble())
        if (!q.contact) {
            return inconclusive(
                Text.fmt("s_29600ac3", "%.2f".format(q.coherence)) +
                    Text.fmt("s_62814298", "%.2f".format(q.sharpness)) +
                    Text.fmt("s_843d374b", "%.1f".format(q.crest), q.beats) +
                    Text.of("s_a2a7026f"),
                beats = q.beats, amplitudeMv = q.amplitudeMv,
                coherence = q.coherence, sharpness = q.sharpness, crest = q.crest, fs = fs,
            )
        }

        // ── 2) 工频陷波 → 带通 5-15Hz（两级级联＝四阶）──
        // ⚠⚠ **顺序不可换**：带通在 50 Hz 处实测只有 −28.4 dB，强工频环境（真机实测
        //   工频 0.24 mV、心电带 20 µV 量级）下的残留会污染 R 峰检出与节律分类。
        //   实测：同一段真机数据先陷波再走这条链路，crest 从 8 → 106~164。
        //   详见 `EcgSignal.notchMains` 的说明。陷波是**零相位**的（前后各滤一次），
        //   所以输出的 rPeaks 位置不受影响 —— PAT（#14）依赖它。
        // 新闸门会放行"真心电 + 漂移"，所以检测器必须自己压得住漂移：
        // 二阶 RBJ 在 1Hz 处仅 -17dB，漂移漏进来会生成假峰。skipSec=1.0 把起振暂态置零。
        val acNotched = EcgSignal.notchMains(ac, fs.toDouble())
        val filtered = EcgSignal.cascadeBandpass(acNotched, fs.toDouble(), 5.0, 15.0, stages = 2, skipSec = 1.0)

        // 起始 1 秒是滤波器暂态，统计与峰值检测都要跳过
        val guard = fs.coerceAtMost(n / 4)
        val amplitude = EcgSignal.peakToPeak(filtered, guard)
        if (amplitude < MIN_AMPLITUDE_MV) {
            return inconclusive(
                Text.fmt("s_0772dd89", "%.2f".format(amplitude)) +
                    Text.of("s_36929cac"),
                beats = q.beats, amplitudeMv = amplitude,
                coherence = q.coherence, sharpness = q.sharpness, fs = fs,
            )
        }

        // ── 3) 微分 → 逐点平方 → 4) 150ms 滑窗积分 ──
        // 微分不可省：漏掉它会让积分峰变成宽平顶，峰位对数值扰动敏感、在 ±15 样本间跳动，
        // 于是 RR 间期抖动、cv 被抬高，整齐节律可能被误报成房颤。详见 EcgSignal.assess 的注释。
        val diff = FloatArray(n)
        for (i in 1 until n) diff[i] = filtered[i] - filtered[i - 1]
        val intWin = (fs * 0.15f).toInt().coerceIn(3, 100)
        val integ = FloatArray(n)
        var run = 0.0
        for (i in 0 until n) {
            val s = diff[i] * diff[i]
            run += s
            if (i >= intWin) run -= diff[i - intWin] * diff[i - intWin]
            integ[i] = (run / intWin).toFloat()
        }

        var globalMax = 0f
        for (i in guard until n) if (integ[i] > globalMax) globalMax = integ[i]
        if (globalMax <= 0f) {
            return inconclusive(
                Text.of("s_0b0a6764"),
                beats = q.beats, amplitudeMv = amplitude,
                coherence = q.coherence, sharpness = q.sharpness, fs = fs,
            )
        }

        // ── 5) QRS 检出：与门控共用同一个**稳健**检测器 ──
        //
        // ⚠ 这里原来是自己一套 Pan-Tompkins SPKI/NPKI 自适应阈值，看着更"标准"，但有个致命缺陷：
        //   `spki` 只被推高、**不会回落**（0.875·spki + 0.125·v），一次瞬态就把它顶到真实 QRS 之上，
        //   而 spki 不再下降 → 门限高高挂起 → 后面整段心拍全被当成噪声丢掉。
        //   台架实测（离群点台架的 C 组）：30 秒真机录制中间插一句
        //   300ms / 20mV 的运动伪迹，检出拍数从 39 掉到 2，直接判"无法判定" ——
        //   用户只是测量中途动了一下手，就会白等 30 秒拿一个"无法判定"。
        //   （对称地，检测门限也不能用 0.35·gmax：那是同一个瞬态问题的另一种表现。）
        // 现在改为共用 [EcgSignal.detectQrsPeaks]：局部归一化突出度 + 离群候选峰截尾。
        val det = EcgSignal.detectQrsPeaks(integ, fs.toDouble(), guard)
        val tWaveGuard = (fs * 0.30f).toInt().coerceAtLeast(
            (fs * 0.25f).toInt().coerceAtLeast(5),
        )
        // T 波二次检出防护：R 波后约 200~300ms 的 T 波经平方积分后也会成峰，若当成一次心跳，
        // RR 就会交替"短-长"，把整齐的高心率（实测 130bpm）误判成"节律不齐 → 房颤提示"。
        // 只在"距上一拍过近 **且** 幅度远弱于运行 R 电平"时才丢弃，因此不会误伤真实心动过速
        // （180bpm 的 RR 仍有 333ms > 300ms）。
        // R 电平用**已接受拍能量的泄漏均值**估计 —— 不能用全局最大：一个瞬态就会把它顶飞，
        // 而"只推不回落"正是上一版整段丢失心拍的成因。
        val peaks = ArrayList<Int>(det.size)
        var rLevel = 0f
        for (p in det.positions) {
            val v = integ[p]
            if (peaks.isNotEmpty() && p - peaks[peaks.size - 1] < tWaveGuard) {
                val ref = if (rLevel > 0f) rLevel else v
                if (v < 0.6f * ref) continue // 视作 T 波丢弃
            }
            peaks.add(p)
            rLevel = if (rLevel <= 0f) v else 0.75f * rLevel + 0.25f * v
        }

        val durSec = (n - guard).toFloat() / fs
        if (peaks.size < 5) {
            return inconclusive(
                Text.of("s_81fe396e"),
                beats = peaks.size, amplitudeMv = amplitude, coherence = q.coherence,
                sharpness = q.sharpness, fs = fs,
            )
        }

        // 轻度幅度过滤（个别漏检的噪声小峰）
        val amps = peaks.map { integ[it] }.sorted()
        val medAmp = amps[amps.size / 2].coerceAtLeast(1e-9f)
        val peaksF = peaks.filter { integ[it] >= 0.3f * medAmp }
        if (peaksF.size < 4) {
            return inconclusive(
                Text.of("s_72a90bae"),
                beats = peaks.size, amplitudeMv = amplitude, coherence = q.coherence,
                sharpness = q.sharpness, fs = fs,
            )
        }
        // 心率上限 210 次/分：超过说明检出的多数是噪声峰
        if (peaksF.size > durSec * 3.5f) {
            return inconclusive(
                Text.of("s_c18d7cc3"),
                beats = peaksF.size, amplitudeMv = amplitude, coherence = q.coherence,
                sharpness = q.sharpness, fs = fs,
            )
        }

        // ── 6) ⚠⚠ 把包络峰吸附回**真实 QRS**，之后的 RR 与时延参考点一律用它 ──
        //
        // 见 `EcgSignal.snapToQrs` 的注释：`peaksF` 是 **150ms 因果滑窗积分包络**的峰，
        // 比真实 QRS **晚 93~107ms 且逐拍抖动 ±20~25ms**（2026-09-27 用两份真机录制
        // 逐采样复刻确认）。心率不受影响（RR 是差值，固定偏移相消），但：
        // · PAT（模块 #14）算的是「R 峰 → 脉搏波足」，参考点直接错 100ms —— 这是它
        //   "一次都算不出来"的根因之一；
        // · HRV（模块 #9）拿到的 RR 序列被这个抖动污染，伪迹率虚增。
        val qrsPeaks = EcgSignal.snapToQrs(ac, peaksF.toIntArray(), fs.toDouble()).toList()

        // ── 6b) RR 间期（ms），剔除生理范围外的值（漏检/重检） ──
        val rr = ArrayList<Int>()
        for (k in 1 until qrsPeaks.size) {
            val ms = (qrsPeaks[k] - qrsPeaks[k - 1]) * 1000 / fs
            if (ms in 300..2000) rr.add(ms)
        }
        // 同一批相邻间隔的**未过滤**副本，只给 #9 HRV（见 Result.rrAllMs 的注释）。
        val rrAll = ArrayList<Int>(qrsPeaks.size - 1)
        for (k in 1 until qrsPeaks.size) {
            rrAll.add((qrsPeaks[k] - qrsPeaks[k - 1]) * 1000 / fs)
        }
        val gaps = qrsPeaks.size - 1
        if (rr.size < 3 || rr.size < gaps / 2) {
            return inconclusive(
                Text.of("s_76e736e1"),
                beats = peaksF.size, amplitudeMv = amplitude, coherence = q.coherence,
                sharpness = q.sharpness, fs = fs,
            )
        }

        val medianRr = rr.sorted()[rr.size / 2]
        val hr = (60000.0 / medianRr).toInt()

        // ── 7) 节律整齐度 ──
        // 主判据：相邻 RR 差异 >20% 的占比。它对"个别漏检"不敏感（一个孤立漏检最多贡献 2 对）。
        var irrPairs = 0
        for (k in 1 until rr.size) {
            val a = rr[k - 1].toFloat()
            val b = rr[k].toFloat()
            if (abs(a - b) / minOf(a, b) > 0.20f) irrPairs++
        }
        val irrRatio = irrPairs.toFloat() / (rr.size - 1).coerceAtLeast(1)

        // 辅助判据：变异系数必须用**鲁棒**版本（1.4826*MAD/中位RR）。
        // 之前用标准差/均值，只要漏检 2 拍（RR 翻倍）就把 cv 从 0.03 抬到 0.17，
        // 于是 130bpm 的整齐节律被误报成"房颤提示"（离线回归实测复现）。
        // MAD 几乎不受孤立离群值影响，实测判别力远强于 irrRatio：
        //   整齐节律 cv = 0.000~0.085   房颤 cv = 0.209~0.262
        // 因此把 cv 作为**主判据**、irrRatio 作为**佐证**，两者同时成立才报房颤，
        // 避免把峰值检测的抖动当成人体的节律异常。
        val devs = rr.map { abs(it - medianRr).toFloat() }.sorted()
        val mad = devs[devs.size / 2].toFloat()
        val cv = (1.4826f * mad / medianRr)
        val regular = !(cv >= 0.15f && irrRatio >= 0.25f)

        // 可判定范围按三星官方 ECG 的量程放宽；
        // 高心率下（>=100）要求**更强的节律不齐证据**才推翻"心率偏高"：
        // 峰值检测的抖动在短 RR 上被放大，实测整齐的 130bpm 在 100Hz 采样下 cv 会到 0.161，
        // 而真实房颤的 cv 在 0.209 以上，所以取 0.20 作为门限。
        // 这与三星健康原生 ECG 的行为一致：心率过高时优先提示心率，不轻易报房颤。
        val (cls, detail) = when {
            hr > 180 || hr < 40 ->
                "无法判定" to Text.fmt("s_19ce3b05", hr)
            !regular && rr.size >= 5 && !(hr >= 100 && cv < 0.20f) ->
                "房颤提示" to Text.fmt("s_7c50fd86", (cv * 100).roundToInt()) +
                    Text.fmt("s_55365a28", (irrRatio * 100).roundToInt()) +
                    Text.of("s_2b45c431")
            hr >= 100 ->
                "心率偏高" to Text.fmt("s_bdcc99a8", hr)
            !regular ->
                "无法判定" to Text.of("s_f2ee522f")
            hr < 50 ->
                "心率偏低" to Text.fmt("s_f083cd00", hr) +
                    Text.of("s_0dafe869")
            else ->
                "窦性心律" to Text.fmt("s_bf0f539a", hr)
        }
        return Result(
            classification = cls,
            heartRate = hr,
            regular = regular,
            beatCount = peaksF.size,
            detail = detail,
            amplitudeMv = amplitude,
            coherence = q.coherence,
            sharpness = q.sharpness,
            crest = q.crest,
            irregularRatio = irrRatio,
            rrCv = cv,
            rrCount = rr.size,
            sampleRateHz = fs,
            rrAllMs = rrAll,
            // 模块 #14：把**吸附到真实 QRS 之后**的下标交出去（PAT 靠它算时延）
            rPeaks = qrsPeaks,
        )
    }
}
