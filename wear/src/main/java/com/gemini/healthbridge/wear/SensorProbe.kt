package com.gemini.healthbridge.wear

import com.samsung.android.service.health.tracking.HealthTracker
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.data.DataPoint
import com.samsung.android.service.health.tracking.data.HealthTrackerType
import com.samsung.android.service.health.tracking.data.ValueKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 传感器能力探测（排障工具，入口在「更多功能 → 其它」）。
 *
 * ## 为什么要有它
 *
 * 三星 Sensor SDK 里「有某个采集器常量」**不代表这台设备开放了它** —— 最贵的例子是
 * 抗氧化指数：SDK 的 21 个采集器里没有任何一个暴露那条蓝光通道。所以判断"能不能做某个指标"
 * 的唯一可靠依据是 `HealthTrackingService.getTrackingCapability().getSupportHealthTrackerTypes()`
 * —— 它是 **SDK 自带的设备能力查询**，比读文档、比试错都准。**先问能力，再逐项实测。**
 *
 * 四步，结论落到屏幕上（日志形态，**不翻译错误原文**）：
 *
 * 1. **能力清单**：21 个采集器逐个标 `[有]`/`[无]`。
 * 2. **EDA 皮肤电导**：SDK 里只有 `EDA_CONTINUOUS`（唯一没有 on-demand 配对的测量项）。
 * 3. **逐拍间期 `IBI_LIST`**：⚠ 它**不在 `PpgSet` 里**（PPG 只有绿/红/红外 + 状态），
 *    只在 `HeartRateSet` 里，所以要**单独开一个心率会话**来测。
 * 4. **心电与心率并跑**：官方原文只说「on-demand 期间连续 tracker **可能**给出无效值」，
 *    是"可能"——这里实测。同时看 `EcgSet.PPG_GREEN`（心电会话自带一路 PPG；
 *    若它真有值，PTT/PAT 就不必并跑两个 tracker，那个"可能无效"的前提直接消失）。
 *
 * ## 四条实现纪律
 *
 * - **回调里必须 `runCatching` 兜底**：SDK 回调在自己绑定的线程上，未捕获异常直接杀进程。
 * - **每一步的失败只影响自己**：某项不支持就记一行结论往下走，不中断整段脚本。
 * - **会话必须有界**：所有会话固定时长，到点自动注销监听。免费版纪律是
 *   "只手动点一下测一段、不做后台自动采集"，探测页同样遵守。
 * - **中途退出必须立刻释放**：见 [stop] —— 注册过的 tracker 全部注销、连接断开，
 *   否则会话残留、LED 常亮（这是本项目反复踩过的一条）。
 */
class SensorProbe(
    private val client: SamsungSensorClient,
    private val scope: CoroutineScope,
) {

    /**
     * 日志的一条。
     *
     * ⚠ 改造前 [log] 是 `List<String>`，界面靠 `line.contains("→ 结论")` /
     * `line.startsWith("✗")` 判高亮与配色 —— 这是**拿显示文案当判据**（模块 #15）。
     * 那些前缀一旦翻译（或将来被改动），手表端的高亮会**静默失效**。
     * 现在把"级别"提升为结构化字段，[text] 只管显示。
     */
    data class Entry(
        val text: String,
        /** 出错行（界面用红系色）。 */
        val error: Boolean = false,
        /** 结论行（界面用强调色）。 */
        val conclusion: Boolean = false,
    )

    data class State(
        val running: Boolean = false,
        val log: List<Entry> = emptyList(),
        val done: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state

    private var job: Job? = null

    /**
     * 已注册监听的 tracker。**唯一的用途是让 [stop] 能把它们注销掉** ——
     * 协程被取消时 `session()` 的收尾代码不一定跑得到，所以不能只靠它自己清理。
     * 所有读写都在主线程（lifecycleScope 默认调度器 + Compose 点击回调），无需加锁。
     */
    private val activeTrackers = ArrayList<HealthTracker>()

    /**
     * 本次探测是不是**被主动停掉的**（[stop]，含 `stopAllMeasurements()` 的连带调用）。
     *
     * 只用于决定"跑完要不要震"：用户自己按停时他正看着屏幕（多余），而开始别的测量
     * 会连带停掉本探测（那时震一下会莫名其妙）。见 [Buzzer] 的接入纪律。
     *
     * ⚠ 由 [start] 复位。只在 `stop()` 里置 true 而不复位的话，**第二次探测跑完就不会震了**
     * —— 而且要等到下次真的点一次"停止"才看得出来，属于极难察觉的一类 bug。
     */
    private var cancelledByUser = false

    /** 全程约 75 秒（不含连接）。分项时长集中在这里，改动一处生效。 */
    private val edaSeconds = 20

    /**
     * 逐拍间期会话时长。
     *
     * 20 秒只有约 23 个间期 —— **不够两件事**：① HRV 本身要 60 秒以上才站得住；
     * ② 判「状态码语义」需要足够样本才能看出分组差异。所以取 60 秒。
     */
    private val ibiSeconds = 60
    private val concurrentBaselineSeconds = 6
    private val concurrentSeconds = 20

    private fun say(line: String = "") {
        // 兼容旧调用：普通行。带级别的走下面两个专用入口。
        _state.value = _state.value.copy(log = _state.value.log + Entry(line))
    }

    /** 结论行 —— 界面用强调色（对应 [Entry.conclusion]）。 */
    private fun sayConclusion(line: String) {
        _state.value = _state.value.copy(log = _state.value.log + Entry(line, conclusion = true))
    }

    /** 出错行 —— 界面用红系色（对应 [Entry.error]）。 */
    private fun sayError(line: String) {
        _state.value = _state.value.copy(log = _state.value.log + Entry(line, error = true))
    }

    fun start() {
        if (_state.value.running) return
        cancelledByUser = false
        _state.value = State(running = true)
        job = scope.launch {
            var crashed = false
            try {
                runCatching { script() }.onFailure { e ->
                    crashed = true
                    sayError("✗ 脚本异常：${e.javaClass.simpleName}: ${e.message}")
                }
            } finally {
                // 正常跑完 / 抛异常 / 被取消，三条路都要走这里
                releaseAll()
                _state.value = _state.value.copy(running = false, done = true)
                // ── 探测结束的震动提示（2026-10-07）──
                // 这一段全程约 75 秒（还要连着静坐按住表冠），是**最长**的一个会话，
                // 用户几乎不可能一直盯着圆屏看。脚本抛异常时两短震 ——
                // 那是"结束了但没拿到完整结论"，与正常出结论区分开（详见 [Buzzer]）。
                if (!cancelledByUser) {
                    if (crashed) Buzzer.fail() else Buzzer.ok()
                }
            }
        }
    }

    /**
     * 中途退出：取消脚本并**立刻**释放全部传感器资源。
     *
     * ⚠ 没在跑时**直接返回、不碰状态**：本方法会被 `stopAllMeasurements()` 无条件调用
     * （每开始一个测量都会调一次）。若不守卫，会把 `done` 翻成 true ——
     * 用户还没探测过，探测页却显示"重新探测"。
     *
     * ⚠ 同时把 [cancelledByUser] 置位：**用户自己停的、以及"开始别的测量被连带停掉的"，
     * 都不该震**。前者的用户正看着屏幕（多余），后者更莫名其妙（他正要测别的东西，
     * 却因为探测被停而震一下）。
     */
    fun stop() {
        if (!_state.value.running) return
        cancelledByUser = true
        job?.cancel()
        job = null
        releaseAll()
        _state.value = _state.value.copy(running = false, done = true)
    }

    private fun releaseAll() {
        for (t in activeTrackers.toList()) runCatching { t.unsetEventListener() }
        activeTrackers.clear()
        runCatching { client.disconnect() }
    }

    private suspend fun script() {
        say("传感器能力探测")
        say("全程约 95 秒，请戴好手表、保持静止")
        say("最后一步需要把手指按住表冠电极")
        say("")

        ensureConnected()?.let {
            sayError("✗ $it")
            return
        }

        step1Capability()
        step2Eda()
        step3Ibi()
        step4Concurrent()

        say("")
        say("—— 探测结束，请把以上内容截图 ——")
    }

    /**
     * 等连接就绪。
     *
     * 用 `suspendCancellableCoroutine` 把 [SamsungSensorClient.Owner] 的回调桥成挂起函数 ——
     * 比"回调里往下接着写"简单得多，也不用自己维护状态机。
     * 三种结果都返回非 null（当作错误文本），成功返回 null。
     */
    private suspend fun ensureConnected(): String? = suspendCancellableCoroutine { cont ->
        val owner = object : SamsungSensorClient.Owner {
            override fun onConnected() {
                if (cont.isActive) cont.resume(null)
            }

            override fun onConnectionEnded() {
                if (cont.isActive) cont.resume("服务连接被断开")
            }

            override fun onConnectionFailed(e: HealthTrackerException) {
                if (cont.isActive) cont.resume("服务连接失败(${e.errorCode}): ${e.message}")
            }
        }
        if (!client.connect(owner) && cont.isActive) {
            cont.resume("client.connect 返回 false（SDK 调用抛异常）")
        }
    }

    // ───────────────────────── 1) 能力清单 ─────────────────────────

    /** 设备能力查询。**这是判断"能不能做"的唯一可靠依据**，先跑它。 */
    private fun step1Capability() {
        say("1) 设备支持的采集器")
        val types = runCatching {
            client.service.getTrackingCapability().supportHealthTrackerTypes
        }.getOrElse { e ->
            sayError("  ✗ 能力查询失败：${e.javaClass.simpleName}: ${e.message}")
            return
        }
        val all = runCatching { HealthTrackerType.values().toList() }.getOrDefault(emptyList())
        say("  SDK 共 ${all.size} 个，本机支持 ${types?.size ?: 0} 个")
        for (t in all) {
            say("  ${if (types?.contains(t) == true) "[有]" else "[无]"} ${t.name}")
        }
        say("")
    }

    // ───────────────────────── 2) EDA 皮肤电导 ─────────────────────────

    private suspend fun step2Eda() {
        say("2) EDA 皮肤电导（${edaSeconds} 秒）")
        if (!isSupported(HealthTrackerType.EDA_CONTINUOUS)) {
            say("  跳过：本机不支持 EDA_CONTINUOUS")
            say("")
            return
        }
        var samples = 0
        var lastStatus: Int? = null
        val values = ArrayList<Float>()
        val err = session(HealthTrackerType.EDA_CONTINUOUS, edaSeconds) { dp ->
            dp.getValue(ValueKey.EdaSet.STATUS)?.let { lastStatus = it }
            dp.getValue(ValueKey.EdaSet.SKIN_CONDUCTANCE)?.let { v ->
                samples++
                if (values.size < 300) values.add(v)
            }
        }
        if (err != null) {
            sayError("  ✗ $err")
            say("")
            return
        }
        say("  收到 $samples 个 SKIN_CONDUCTANCE，最后 STATUS=$lastStatus")
        if (values.isEmpty()) {
            sayConclusion("  → 结论：会话起来了但一个值都没有，不可用")
        } else {
            say("  值域 ${fmt(values.min())} ~ ${fmt(values.max())}")
            say("  前 3 个：${values.take(3).joinToString { fmt(it) }}")
            sayConclusion("  → 结论：可用（$samples 个样本）")
        }
        say("")
    }

    // ───────────────────────── 3) 逐拍间期 IBI ─────────────────────────

    /**
     * `IBI_LIST` 只在 `HeartRateSet` 里（`PpgSet` 没有），所以要开一个**心率会话**来测。
     * 优先 `HEART_RATE_CONTINUOUS`（连续型、点数密），不支持则退回 `HEART_RATE`。
     */
    private suspend fun step3Ibi() {
        say("3) 逐拍间期 IBI_LIST（${ibiSeconds} 秒）")
        val type = hrTrackerType()
        if (type == null) {
            say("  跳过：本机不支持 HEART_RATE / HEART_RATE_CONTINUOUS")
            say("")
            return
        }
        say("  用 ${type.name}，请保持静止（移动会造出假的间期）")
        var points = 0
        var pointsWithIbi = 0
        var lastHr: Int? = null
        val firstIbi = ArrayList<Int>()

        // 状态码 → 该码下出现的全部间期。**这是用来反推状态码语义的**：
        // 若某个码下的间期集中在一个窄带、另一个码下满天飞，含义基本就清楚了。
        val byStatus = LinkedHashMap<Int, MutableList<Int>>()
        val unpaired = ArrayList<Int>()

        val err = session(type, ibiSeconds) { dp ->
            points++
            dp.getValue(ValueKey.HeartRateSet.HEART_RATE)?.let { lastHr = it }
            val ibi = dp.getValue(ValueKey.HeartRateSet.IBI_LIST)
            val sts = dp.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST)
            if (!ibi.isNullOrEmpty()) {
                pointsWithIbi++
                for (i in ibi.indices) {
                    val v = ibi[i]
                    if (firstIbi.size < 10) firstIbi.add(v)
                    // ⚠ 两个列表**不保证等长**，只在长度一致时才配对 ——
                    //   错位配对会把状态安到错误的间期上，得到一个"看起来合理但错"的结论，
                    //   而这正是本项目最防的那类错误。
                    val st = if (sts != null && sts.size == ibi.size) sts[i] else null
                    if (st != null) byStatus.getOrPut(st) { ArrayList() }.add(v) else unpaired.add(v)
                }
            }
        }
        if (err != null) {
            sayError("  ✗ $err")
            say("")
            return
        }

        val all = byStatus.values.flatten() + unpaired
        say("  数据点 $points 个，其中带 IBI 的 $pointsWithIbi 个，间期共 ${all.size} 个")
        say("  最后心率（SDK 给的）$lastHr 次/分")
        if (all.isEmpty()) {
            sayConclusion("  → 结论：拿不到逐拍间期，HRV 这条路走不通")
            say("")
            return
        }
        say("  前几个间期(ms)：${firstIbi.joinToString()}")

        val med = median(all)
        say("  间期值域 min=${all.min()} 中位=$med max=${all.max()}")
        say("  按 IBI_STATUS_LIST 分组（用来反推状态码语义）：")
        for ((st, vs) in byStatus) {
            say("    状态 $st：${vs.size} 个，min=${vs.min()} 中位=${median(vs)} max=${vs.max()}")
        }
        if (unpaired.isNotEmpty()) {
            say("    无状态（两列表长度不等）：${unpaired.size} 个")
        }

        // ⚠ 合理性判据：**数量够不等于质量够**。
        //   第一版探测只数了数量就判"可用"，而实测值里出现了 2300ms / 308ms
        //   这种生理上不可能的心搏间期（对应 26 与 195 次/分）——结论是错的。
        //   这里改判两件事：① 中位数本身落在 40~150 次/分内；② 伪迹占比（偏离中位 2 倍）。
        val bad = all.count { it < med / 2 || it > med * 2 }
        val badPct = bad * 100 / all.size
        val medOk = med in 400..1500
        say("  合理性：中位 $med ms（${if (medOk) "在 40~150 次/分内" else "**超出 40~150 次/分**"}）" +
            "，偏离中位 2 倍的伪迹 $bad/${all.size} = $badPct%")
        sayConclusion(
            when {
                !medOk -> "  → 结论：值不可信（中位数本身就不生理）"
                badPct > 20 -> "  → 结论：通道通，但本次伪迹 $badPct% —— 静止后复测"
                else -> "  → 结论：可用（伪迹 $badPct%，$med ms ⇒ 约 ${60000 / med} 次/分）"
            }
        )
        say("")
    }

    // ───────────────────────── 4) 心电与连续型并跑 ─────────────────────────

    /**
     * 官方原文：`Do not use more than one on-demand tracker at the same time.`
     * 以及 `During the on-demand tracker type data measurement, tracking a continuous tracker
     * type can give invalid values.` —— 是"**可能**无效"，所以实测。
     *
     * 做法：先只开心率连续型 [concurrentBaselineSeconds] 秒做**对照**，再在它存活的同时
     * 开 `ECG_ON_DEMAND` [concurrentSeconds] 秒，比较两段的点数。
     * 没有对照段的话，"并跑段点数 0"无法区分"被心电挤掉"和"心率本来就没出数"。
     */
    private suspend fun step4Concurrent() {
        say("4) 心电与心率并跑（对照 ${concurrentBaselineSeconds}s，并跑 ${concurrentSeconds}s）")
        val hrType = hrTrackerType()
        if (hrType == null || !isSupported(HealthTrackerType.ECG_ON_DEMAND)) {
            say("  跳过：缺少心率或心电采集器")
            say("")
            return
        }
        say("  ⚠ 接下来 ${concurrentBaselineSeconds + concurrentSeconds} 秒请把手指按住表冠电极")

        var hrPoints = 0
        var hrBefore = 0
        var ecgPoints = 0
        var ecgPpgGreen = 0
        var ecgFirstSeq: Int? = null
        var ecgLastSeq: Int? = null
        var ecgSeqSeen = false
        var ecgSeqMin: Int? = null
        var ecgSeqMax: Int? = null
        var ecgPpgMin: Int? = null
        var ecgPpgMax: Int? = null
        val leadOff = LinkedHashSet<Int>()

        val hrTracker = runCatching { client.service.getHealthTracker(hrType) }.getOrNull()
        if (hrTracker == null) {
            say("  跳过：取心率 Tracker 失败")
            say("")
            return
        }
        if (runCatching { hrTracker.setEventListener(listenerOf { hrPoints++ }) }.isFailure) {
            say("  跳过：注册心率监听失败")
            say("")
            return
        }
        activeTrackers.add(hrTracker)

        repeat(concurrentBaselineSeconds) { delay(1000) }
        hrBefore = hrPoints

        val ecgTracker = runCatching {
            client.service.getHealthTracker(HealthTrackerType.ECG_ON_DEMAND)
        }.getOrNull()
        var ecgRegistered = false
        if (ecgTracker != null) {
            ecgRegistered = runCatching {
                ecgTracker.setEventListener(listenerOf { dp ->
                    ecgPoints++
                    dp.getValue(ValueKey.EcgSet.LEAD_OFF)?.let { leadOff.add(it) }
                    // SEQUENCE 的序号跨度是**唯一可信的采样率依据**
                    // （本项目"采样率必须实测，不能照抄文档"那条）。
                    dp.getValue(ValueKey.EcgSet.SEQUENCE)?.let { sq ->
                        ecgSeqMin = if (ecgSeqMin == null) sq else minOf(ecgSeqMin!!, sq)
                        ecgSeqMax = if (ecgSeqMax == null) sq else maxOf(ecgSeqMax!!, sq)
                        // ⚠ **只接受非负序号**。本机固件会给 -1（未知）——
                        //   生产代码 `EcgMeasurer` 早就守着 `seq >= 0`（并把采样率降级成
                        //   "包数/墙钟"），探测页第一版漏了这一条，于是把 -1 当序号用，
                        //   算出"跨度 -1"、采样率打问号。**又一个"生产代码知道、新代码不知道"**。
                        if (sq >= 0) {
                            ecgSeqSeen = true
                            if (ecgFirstSeq == null) ecgFirstSeq = sq
                            ecgLastSeq = sq
                        }
                    }
                    dp.getValue(ValueKey.EcgSet.PPG_GREEN)?.let { v ->
                        ecgPpgGreen++
                        ecgPpgMin = if (ecgPpgMin == null) v else minOf(ecgPpgMin!!, v)
                        ecgPpgMax = if (ecgPpgMax == null) v else maxOf(ecgPpgMax!!, v)
                    }
                })
            }.isSuccess
            if (ecgRegistered) activeTrackers.add(ecgTracker)
        }

        hrPoints = 0
        val ecgStartMs = System.currentTimeMillis()
        repeat(concurrentSeconds) { delay(1000) }
        val ecgWallMs = (System.currentTimeMillis() - ecgStartMs).coerceAtLeast(1L)

        say("  对照段（只开心率 ${concurrentBaselineSeconds}s）：心率 $hrBefore 点")
        say("  心电 Tracker ${if (ecgRegistered) "注册成功" else "注册失败"}")
        say("  并跑段（${concurrentSeconds}s）：心率 $hrPoints 点，心电 $ecgPoints 点")
        say("  心电 LEAD_OFF 出现过的值：${leadOff.ifEmpty { listOf(-999) }.joinToString()}")
        say("  心电里 PPG_GREEN 有值 $ecgPpgGreen 点（值域 $ecgPpgMin ~ $ecgPpgMax）")

        // 采样率：按 SEQUENCE 序号跨度算（权威），再按包数算一次做对照。
        val wallS = ecgWallMs / 1000.0
        val span = if (ecgSeqSeen && ecgFirstSeq != null && ecgLastSeq != null) {
            (ecgLastSeq!! - ecgFirstSeq!!).coerceAtLeast(0)
        } else 0
        val fsBySeq = if (span > 0) f1(span / wallS) else "?"
        val fsByPkt = f1(ecgPoints / wallS)
        say("  墙钟 ${f1(wallS)} 秒；心电包数 $ecgPoints")
        say("  SEQUENCE 可用=${if (span > 0) "是" else "否"}，跨度 $span" +
            "（本次见过 min=$ecgSeqMin max=$ecgSeqMax）")
        say("  → 按序号算 ${fsBySeq} Hz，按包数算 ${fsByPkt} Hz")
        if (span <= 0) {
            say("     ⚠ 序号不可用 ⇒ 只能用包数。但「包数」≠「采样率」")
            say("       （一个包可能含多个采样），这个数是偏乐观的口径")
        }

        sayConclusion(
            if (hrPoints == 0) "  → 结论：并跑时连续型完全停摆（$hrBefore 点 → 0 点）"
            else "  → 结论：连续型仍有 $hrPoints 点（对照段 $hrBefore 点）—— " +
                "密度 $hrPoints/${concurrentSeconds}s vs $hrBefore/${concurrentBaselineSeconds}s，一致即未衰减"
        )
        if (ecgPpgGreen == 0) {
            sayConclusion("  → ECG 里 PPG 无值，PTT 仍需另想办法")
        } else {
            say("  → ⚠ 心电会话自带 PPG —— PTT/PAT 不必并跑两个 Tracker")
            val fs = if (span > 0) span / wallS else ecgPoints / wallS
            val basis = if (span > 0) "按 SEQUENCE 序号" else "按包数（序号不可用，口径偏乐观）"
            if (fs > 0) {
                val stepMs = 1000.0 / fs
                // PTT 对血压的灵敏度约 1ms ≈ 1mmHg（文献 0.5~1.5，见 2026-09-20.md）。
                // 判据：一个采样间隔要明显小于「10 mmHg 对应的 5~10 ms」才有意义。
                say("     $basis：${f1(fs)} Hz ⇒ 1 个采样 = ${f2(stepMs)} ms")
                say("     10 mmHg ≈ 7 ms ≈ ${f1(7.0 / stepMs)} 个采样")
                say(
                    if (fs >= 200) "  → PTT 的精度天花板被打通（原 PPG_CONTINUOUS 只有 25 Hz）"
                    else "  → 采样率仍偏低，PTT 精度受限"
                )
            }
        }
        say("")
    }

    // ───────────────────────── 公共件 ─────────────────────────

    private fun isSupported(type: HealthTrackerType): Boolean = runCatching {
        client.service.getTrackingCapability().supportHealthTrackerTypes?.contains(type) == true
    }.getOrDefault(false)

    private fun hrTrackerType(): HealthTrackerType? = when {
        isSupported(HealthTrackerType.HEART_RATE_CONTINUOUS) -> HealthTrackerType.HEART_RATE_CONTINUOUS
        isSupported(HealthTrackerType.HEART_RATE) -> HealthTrackerType.HEART_RATE
        else -> null
    }

    private fun listenerOf(onPoint: (DataPoint) -> Unit) = object : HealthTracker.TrackerEventListener {
        override fun onDataReceived(points: List<DataPoint>) {
            // ⚠ SDK 回调在自己绑定的线程上，**未捕获异常会直接杀进程**，必须兜底。
            // 注意也要包住 onPoint 本身 —— 它里面是 getValue，同样可能抛。
            runCatching { for (dp in points) onPoint(dp) }
        }

        override fun onFlushCompleted() {}

        override fun onError(error: HealthTracker.TrackerError) {
            runCatching { say("  ⚠ Tracker 错误：$error") }
        }
    }

    /**
     * 开一个**有界**会话：注册监听 → 固定时长 → 注销。
     *
     * 返回 null 表示正常跑完；非 null 是给界面看的错误文本（**不翻译**，原文带出）。
     *
     * ⚠ SDK 里没有 `start()`/`stop()` —— 会话的开始与结束就是 `setEventListener` /
     * `unsetEventListener`。所以"连续型"也完全可以只跑一段，这正是免费版
     * "手动点一下测一段"的做法。
     */
    private suspend fun session(
        type: HealthTrackerType,
        seconds: Int,
        onPoint: (DataPoint) -> Unit,
    ): String? {
        val tracker = runCatching { client.service.getHealthTracker(type) }.getOrElse { e ->
            return "取 Tracker 失败：${e.javaClass.simpleName}: ${e.message}"
        }
        runCatching { tracker.setEventListener(listenerOf(onPoint)) }.onFailure { e ->
            return "注册监听失败：${e.message}"
        }
        activeTrackers.add(tracker)
        try {
            repeat(seconds) { delay(1000) }
        } finally {
            // 协程被取消时也要走到这里（否则 [releaseAll] 得兜底，两处都要有）
            runCatching { tracker.unsetEventListener() }
            activeTrackers.remove(tracker)
        }
        return null
    }

    private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.3f", v)

    private fun f1(v: Double): String = String.format(java.util.Locale.US, "%.1f", v)
    private fun f2(v: Double): String = String.format(java.util.Locale.US, "%.2f", v)
    private fun f1(v: Int): String = f1(v.toDouble())

    /**
     * 中位数。**刻意用中位数而不是均值**：伪迹（如 2300 ms）会把均值拽走，
     * 而中位数不受影响 —— 判"这批间期可信吗"要看的是典型值，不是平均。
     */
    private fun median(v: List<Int>): Int {
        if (v.isEmpty()) return 0
        val a = v.sorted()
        return if (a.size % 2 == 1) a[a.size / 2] else (a[a.size / 2 - 1] + a[a.size / 2]) / 2
    }
}
