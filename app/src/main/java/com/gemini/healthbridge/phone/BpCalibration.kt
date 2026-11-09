package com.gemini.healthbridge.phone

import com.gemini.healthbridge.core.BpPredict
import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.BpCalibrationSampleEntity
import com.gemini.healthbridge.core.db.rrList
import kotlin.math.abs

/**
 * 血压校准的**配对采集**（模块 #14 方案 C，2026-10-02）。
 *
 * ## 它做什么
 *
 * 每当有一条袖套读数落库，就到它前后 [BpPredict.PAIR_WINDOW_MS] 的窗口里
 * 找**同时段**的手表数据（PAT + 心率），配成一条训练样本。
 * 配不成就**什么都不写** —— 宁可少一条样本，不要一条被污染的。
 *
 * ## 三条纪律
 *
 * 1. ⚠ **窗口内没有 PAT 就不配** —— 不猜、不拿"最近的那次"顶替。
 *    跨窗口借用会造出「看起来合法但量错了」的样本（同一类错见项目设计说明的 `rPeaks` 纪律）。
 * 2. ⚠ **取窗口内离读数最近的那一次心电会话**，不是窗口内所有会话的中位数 ——
 *    用户被要求「同一分钟内」测，两者同时段时最近的那次才是对上的那一次。
 * 3. ⚠ **配对是纯附加动作**：它失败/不成功都**不影响读数本身的保存**。
 *    与 `Bp.validate` 的用户输入纪律同向：录入永远成功，校准是尽力而为。
 */
object BpCalibration {

    /** 一次配对的结果（供调用方判断要不要提示用户）。 */
    enum class Outcome {
        /** 配成了，且用户声明了「安静状态下测量」。 */
        PairedQuiet,

        /** 配成了，但用户没声明安静状态。 */
        Paired,

        /** 窗口内没有可用 PAT —— 绝大多数情况（没在测袖套时测心电）。 */
        NoSignal,

        /** 有 PAT 但质量门控没过（离散度大 / 心率样本不足）。 */
        Rejected,
    }

    /**
     * 试配一条并落库。
     *
     * @param quiet 用户是否声明「安静状态下测量」—— 只影响**质量档**，不影响是否配对。
     * @return 配对结果。**只有 [Outcome.Paired] / [Outcome.PairedQuiet] 时才写了库。**
     */
    suspend fun tryPair(
        db: AppDatabase,
        bpId: Long,
        recordedAt: Long,
        quiet: Boolean,
        /**
         * 那条读数的测量体位（`Bp.Posture`）。
         *
         * ⚠ 是**从读数复制过来**的，不是在配对时重新问一遍 —— 体位属于"那次测量"，
         *   不属于"配对动作"。`null` = 老数据或用户没选，**不等于坐姿**。
         */
        posture: String? = null,
    ): Outcome {
        val half = BpPredict.PAIR_WINDOW_MS
        val ecgs = db.ecgDao().withPatBetween(recordedAt - half, recordedAt + half)
        if (ecgs.isEmpty()) return Outcome.NoSignal

        // ⚠ 取**离读数最近**的那次会话（纪律 2）：同时段采集时它才是对上的那一次。
        val nearest = ecgs.minByOrNull { abs(it.recordedAt - recordedAt) }!!
        // ⚠⚠ 心率**也走共享函数**（不自己算）：`latestInputs` 用的是同一个
        //   `windowSignals`，两处各算一次必然漂移（2026-10-02 的原始 bug 就是
        //   「训练侧有心率、预测侧没有」）。
        val sig = windowSignals(db, recordedAt - half, recordedAt + half)

        val quality = BpPredict.quality(
            patMs = nearest.patMs,
            patSpreadMs = nearest.patSpreadMs,
            hrSamples = sig.hrSamples,
            quiet = quiet,
        )
        // ⚠ HRV 与质量档**无关**：它只是回归的候选特征之一，取不到就让那一维缺着
        //   （`BpPredict` 会自动少用这一维，而不是填补）。别拿它当准入条件 ——
        //   那会让「这次没测 HRV」变成「整条样本作废」，白白丢掉一个本来能用的配对。
        if (quality == BpPredict.Quality.Unusable) return Outcome.Rejected

        db.bpCalibrationSampleDao().upsert(
            BpCalibrationSampleEntity(
                bpId = bpId,
                pairedAt = nearest.recordedAt,
                patMs = nearest.patMs,
                patSpreadMs = nearest.patSpreadMs,
                hrMean = sig.hrMean,
                hrvRmssdMs = sig.hrvRmssdMs,
                windowMs = half * 2,
                quality = quality.ordinal,
                posture = posture,
            )
        )
        return if (quality == BpPredict.Quality.Gold) Outcome.PairedQuiet else Outcome.Paired
    }

    /**
     * 把库里的配对样本读成 core 的输入。
     *
     * ⚠ **BMI 不在这里取**：它是档案级量（不随某次测量变），
     * 由调用方从档案读一次传进来 —— 存进每行只会是同一份数据的 N 个副本。
     */
    suspend fun loadSamples(
        db: AppDatabase,
        bmi: Double? = null,
    ): List<BpPredict.CalibSample> = db.bpCalibrationSampleDao().usablePairs().map { r ->
        BpPredict.CalibSample(
            atMs = r.pairedAt,
            systolic = r.systolic,
            diastolic = r.diastolic,
            patMs = r.patMs,
            hrMean = r.hrMean,
            hrvRmssdMs = r.hrvRmssdMs,
            bmi = bmi,
            // ⚠ 体位**不进回归**（不是 `BpPredict.Feat` 的成员），它只作**分组的键**。
            //   2026-10-05 起它有了实际用途：`BpPredict.estimate` 会用它判断
            //   **训练样本的体位一致性**（`Estimate.Ok.postureConsistency`），
            //   混了就在界面提醒用户按同一体位重测。
            //   ⚠ 刻意仍**不做门控/分层**：门槛是 8 组，按体位再切一刀会永久拒答。
            posture = r.posture,
        )
    }

    /**
     * 读「最近一次」腕端输入，用来代入回归式求当前值。
     *
     * ## ⚠⚠ 这里的口径必须与 [tryPair] **完全一致**
     *
     * 2026-10-02 用户反馈「只能看趋势、看不到数值」，根因就在这里：
     * 原实现只给了 `patMs`，而 `hrMean` / `hrvRmssdMs` **写死 null**。
     * 于是只要训练样本里**出现过**心率（`chooseFeatures` 的判据是「全员有值」），
     * 预测时就会命中 `Estimate.MissingInput` —— **永远出不来数值**，
     * 而界面只显示一句「缺少最近一次的手表数据」，看不出是这个原因。
     *
     * ⇒ 训练与预测走**同一个窗口函数**（[windowSignals]），口径不可能漂移。
     *
     * @param maxAgeMs 腕端数据最多允许有多旧。**超龄一律返回全 null** ——
     *   拿三天前的 PAT 去推「当前血压」是在编数字。
     */
    suspend fun latestInputs(
        db: AppDatabase,
        bmi: Double? = null,
        maxAgeMs: Long = BpPredict.LATEST_MAX_AGE_MS,
    ): BpPredict.LatestInputs {
        val latest = db.ecgDao().recentOnce(400).firstOrNull { it.patMs != null } ?: return EMPTY
        val age = System.currentTimeMillis() - latest.recordedAt
        if (age > maxAgeMs) return EMPTY
        val half = BpPredict.LATEST_WINDOW_MS
        val sig = windowSignals(db, latest.recordedAt - half, latest.recordedAt + half)
        return BpPredict.LatestInputs(
            patMs = latest.patMs,
            hrMean = sig.hrMean,
            hrvRmssdMs = sig.hrvRmssdMs,
            bmi = bmi,
            // ⚗ 本次心电**测得**的搏数（原样带出，不预测、不夹值域）。
            //   ⚠ 与 `hrMean` 刻意分开：那个是 ±30min 窗口内多条记录的均值，
            //   这个是**这一段心电**的搏数，与那条 PAT 同一时刻 —— 语义更贴近"这次测的"。
            //   缺失就留 null，界面不显示（**不兜默认值**，见 core 里的说明）。
            measuredPulse = latest.heartRate,
        )
    }

    /**
     * **未与袖套血压配对**的心电 PAT 列表（血压页「校准覆盖」提示用）。
     *
     * ## ⚠ 与「未配对的血压」是**两件事**，别弄反
     *
     * - [BpCalibrationSampleDao.unpairedReadings] 给的是**血压没配上心电**；
     * - 这里给的是**心电没配上血压**。
     *
     * 用户的真实数据里，缺的是**后者**：他有 15 份心电录制（PAT 148~235 ms），
     * 只有 5 份与血压同时段 ⇒ **两侧都有大量现成数据可用**，只是没配上对。
     * ⇒ 界面的措辞必须是「另有 N 次心电未配上血压」，
     *   **不能**说「请在更低的 PAT 时测」——他早就测过了。
     *
     * ## 口径
     *
     * 落在**任一**已配对窗口 [pairedAt ± windowMs] 内的都算"已配对"，
     * 不重复计数。用窗口而不是"同一天"，因为配对的实际条件就是窗口。
     */
    suspend fun unmatchedEcgPats(
        db: AppDatabase,
        limit: Int = 200,
    ): List<Int> {
        val pairs = db.bpCalibrationSampleDao().usablePairs()
        if (pairs.isEmpty()) {
            // ⚠ 没有配对样本时**全部**算未配对（而不是返回空）——
            //   返回空会被界面读成"没有可用的心电"，那是反的。
            return db.ecgDao().recentOnce(limit).mapNotNull { it.patMs }
        }
        val windows = pairs.map { it.pairedAt to it.windowMs }
        return db.ecgDao().recentOnce(limit).mapNotNull { e ->
            val pat = e.patMs ?: return@mapNotNull null
            val hit = windows.any { (center, w) ->
                // ⚠⚠ `windowMs` 是**窗口总宽**（如 ±5min = 10 分钟），
                //   而落点要跟**半宽**比 —— 差一个 2 倍会让"已配对"判成"未配对"，
                //   于是界面虚报一大片缺口（而用户其实都配过了）。
                // ⚠ `w <= 0` 兜成 [BpPredict.PAIR_WINDOW_MS]：
                //   老数据或手写 POJO 可能给 0，那时"零宽窗口"会把所有心电都算成未配对。
                val half = (if (w > 0) w else BpPredict.PAIR_WINDOW_MS) / 2
                kotlin.math.abs(e.recordedAt - center) <= half
            }
            if (hit) null else pat
        }
    }

    /**
     * **预估历史**的输入：取最近 [limit] 次**带 PAT** 的心电，逐次配好它**那一刻**的信号。
     *
     * ## ⚠⚗ 与 [latestInputs] 的三处**刻意不同**
     *
     * | | latestInputs（算"现在"） | 这里（回看历史） |
     * |---|---|---|
     * | 时效门限 | [BpPredict.LATEST_MAX_AGE_MS] 6 小时 | **无** |
     * | 取几条 | 只要最近 1 条 | 最近 [limit] 条 |
     * | 窗口 | ±[BpPredict.LATEST_WINDOW_MS] | **同一个**（±30 分钟） |
     *
     * - **无时效门限**：用户回看的就是三周前那次，那次的数据再旧也是那一刻的真值。
     *   拿 6 小时去砍历史，会把整张表清空 —— 而清空的理由（"不能拿三天前的 PAT 推
     *   **当前**血压"）对历史**不成立**：这里推的是**那一时刻**的血压。
     * - **窗口必须与训练侧一致**（±30min）—— 否则同一维特征在训练与回看时口径不同。
     *
     * ## ⚠ 成本
     *
     * 每行要查一次窗口（3 条 SQL），[limit] 行就是 [limit] 次。
     * 所以 [limit] 由**界面**决定（只显示最近若干条），不在这里写死一个"全部"。
     */
    suspend fun historyInputs(
        db: AppDatabase,
        limit: Int,
        bmi: Double? = null,
    ): List<Pair<Long, BpPredict.LatestInputs>> {
        val ecgs = db.ecgDao().recentOnce(400)
            .filter { it.patMs != null }
            .take(limit)
        if (ecgs.isEmpty()) return emptyList()
        val half = BpPredict.LATEST_WINDOW_MS
        // ⚠ 顺序：调用方 [BpPredict.history] 要求"按时刻倒序"，而 `recentOnce` 已是 DESC。
        return ecgs.map { e ->
            val sig = windowSignals(db, e.recordedAt - half, e.recordedAt + half)
            e.recordedAt to BpPredict.LatestInputs(
                patMs = e.patMs,
                hrMean = sig.hrMean,
                hrvRmssdMs = sig.hrvRmssdMs,
                bmi = bmi,
                // 同 [latestInputs]：历史每一行也带上**那一次**的测得搏数。
                measuredPulse = e.heartRate,
            )
        }
    }

    /** 全空的输入 —— 「什么都没有」的**唯一**表示，别用 `LatestInputs()` 随手造一个。 */
    private val EMPTY = BpPredict.LatestInputs()

    /**
     * **回填**历史配对：把过去那些「当时没测手表、但事后腕表有数据」的读数补上。
     *
     * ## 为什么需要它（2026-10-02 用户反馈触发）
     *
     * 原来只有「录血压的那一瞬间」才取信号。用户是**平时**戴着表测的，
     * 录血压时并没有同步测心电 —— 于是绝大多数历史读数永远配不上，
     * 攒半年也攒不到 6 组，界面只能一直显示趋势。
     *
     * ⇒ 事后按同一个 ±[BpPredict.PAIR_WINDOW_MS] 窗口回查一次：
     *   窗口里有 PAT 就补上，没有就**真的没有**（纪律 1 不放宽）。
     *
     * ## ⚠⚠ 仍然只承诺「同时段」，不承诺「同时刻」
     *
     * 回查的窗口与当初配对用**同一个**窗口宽度，所以配上的样本与当场配的
     * 是**同一种口径**。但用户是**事后**补的，这只保证「那一刻附近有数据」，
     * 不保证「那一刻的血压与此刻的血压相同」。
     *
     * ⚠ 所以回填出来的样本**一律按最低质量档（[Quality.Usable]）记**，
     *   不给 Gold —— 界面上的「本月校准样本」进度**只数当场配的**。
     *   这一条别当成保守：把两种口径混在一个计数里，用户看到的进度
     *   就不代表「我在正确条件下测了 N 次」。
     *
     * ## ⚠⚠ `since` 不是可选的优化，是**必需**的
     *
     * [BpCalibrationSampleDao.unpairedReadings] 按 `recordedAt ASC` 排序。若同步完成后
     * 不带 `since` 地跑一次，那些**当初就没测心电**的老读数会永远排在最前面、
     * 每次都把 limit 占满 ⇒ **新记录永远轮不到**，而且每轮同步都白烧一堆窗口查询。
     *
     * ⇒ 同步后自动调用时**必须**传本次同步的时间下界；
     *   只有用户**手动点「补齐」**时才用 `since = 0`（那是有意从头清一遍）。
     *
     * @param since 只处理 `recordedAt >= since` 的读数。0 = 全部（手动补齐全量时用）。
     * @return 新补上的条数（已存在的不重复计数）。
     */
    suspend fun backfill(
        db: AppDatabase,
        since: Long = 0L,
        limit: Int = BACKFILL_LIMIT,
    ): Int {
        val pending = db.bpCalibrationSampleDao().unpairedReadings(since, limit)
        var added = 0
        for (row in pending) {
            val id = row.id
            val at = row.recordedAt
            val half = BpPredict.PAIR_WINDOW_MS
            val ecgs = db.ecgDao().withPatBetween(at - half, at + half)
            if (ecgs.isEmpty()) continue
            val nearest = ecgs.minByOrNull { abs(it.recordedAt - at) }!!
            val sig = windowSignals(db, at - half, at + half)
            val quality = BpPredict.quality(
                patMs = nearest.patMs,
                patSpreadMs = nearest.patSpreadMs,
                hrSamples = sig.hrSamples,
                // ⚠ 事后回填**一律不按 Gold**：见 KDoc 的口径警告。
                quiet = false,
            )
            if (quality == BpPredict.Quality.Unusable) continue
            db.bpCalibrationSampleDao().upsert(
                BpCalibrationSampleEntity(
                    bpId = id,
                    pairedAt = nearest.recordedAt,
                    patMs = nearest.patMs,
                    patSpreadMs = nearest.patSpreadMs,
                    hrMean = sig.hrMean,
                    hrvRmssdMs = sig.hrvRmssdMs,
                    windowMs = half * 2,
                    quality = quality.ordinal,
                    // ⚠ 体位从**读数那一行**取（`unpairedReadings` 返回整行正是为了这个）：
                    //   回填是事后发生的，此刻再问用户"当时什么姿势"没人答得出来。
                    //   老读数没有这一列 ⇒ null，宁可记"未知"也不猜成坐姿。
                    posture = row.posture,
                )
            )
            added++
        }
        return added
    }

    /** 单次回填最多处理多少条读数。⚠ 别一次全扫：每条都要查三个窗口查询（心电/心率/HRV）。 */
    const val BACKFILL_LIMIT = 200

    /**
     * 同步完成后**自动**配对时回看多久（毫秒）。
     *
     * ## 为什么是固定的一段，而不是「本批次的时间范围」
     *
     * 手表会攒着数据后一次性上传 —— 一批里可能混着好几天前的记录。
     * 用「本批最早时间」当 `since` 会让它落得很早，于是 [unpairedReadings] 的
     * `ORDER BY recordedAt ASC` 又回到最老那批未配对记录上**卡住**（见 [backfill] 的说明）。
     *
     * ## 为什么是 3 天
     *
     * 这套流程的用法是「先测袖套、紧接着测心电」，两件事必然**紧邻**（配对窗口只有 ±5 分钟）。
     * 所以自动配对只需覆盖最近这段时间；三天已经非常宽裕，
     * 同时把待处理量压到「用户最近三天测了几次血压」这个量级（几次，不是几百条）。
     *
     * ⚠ 更老的历史读数靠用户手动点「补齐」（`since = 0` 全量）——
     *   那种读数是「当初就没测心电」，时间差也早就超过配对窗口，配不配得上不由这个常量决定。
     */
    const val AUTO_PAIR_LOOKBACK_MS = 3 * 86_400_000L

    /**
     * 窗口内的腕端信号（心率均值 + HRV RMSSD）。
     *
     * ⚠ **训练与预测共用**：任何一侧改了口径而另一侧没跟上，就会出现
     *   「训练时这一维有值、预测时没有」→ 永久 `MissingInput`（2026-10-02 实付）。
     *
     * @param hrMinSamples 心率样本少于这个数就当「没有」——一个样本算出来的均值
     *   方差极大，写进训练数据等于加噪声。
     */
    suspend fun windowSignals(
        db: AppDatabase,
        from: Long,
        to: Long,
        hrMinSamples: Int = BpPredict.MIN_HR_SAMPLES,
    ): Signals {
        // ⚠ `bpm` 在库里是 **Double**（Health Connect 那边给的是原始精度），
        //   但全项目一律 `.toInt()` 落成整数次/分（MainActivity / HealthConnectManager
        //   都是这么读的）。这里跟着走 —— 特征口径必须与界面显示的那个数一致，
        //   否则界面写 72 而模型吃 72.4，两边对不上还看不出来。
        val fromHrTable = db.heartRateDao().between(from, to).map { it.bpm.toInt() }
        // ⚠⚠ **心电会话自带的心率必须一并计入**（2026-10-03 修）。
        //
        //   它落在 `ecg_records.heartRate`（手表端 `heartRate = analysis.heartRate`），
        //   而**不写 `heart_rate` 表**。可是用户的标准配对流程恰恰是
        //   「手表录血压 → 紧接着测心电」——那个 ±5 分钟窗口里往往**一条心率记录都没有**。
        //   只数心率表的话 `hrSamples` 恒为 0 ⇒ `BpPredict.quality()` 判 `Unusable`
        //   ⇒ **配对永远是 0**（用户实测「本月校准样本 0/8」正是这个）。
        //
        //   ⚠ 两侧（`tryPair` / `backfill` 与预测侧的 `latestInputs`）**共用本函数**，
        //     所以训练与预测的口径天然一致 —— 这正是当初把窗口计算收在这里的原因，
        //     别在调用点各加一次。
        val fromEcg = db.ecgDao().withHrBetween(from, to).mapNotNull { it.heartRate }
        val allHr = fromHrTable + fromEcg
        // ⚠⚠ **心电会话自带的心率是「会话级代表值」，不是「时点样本」，样本数门槛不能
        //   一视同仁**（2026-10-03 补）。
        //
        //   `hrMinSamples` 的理由是「一条**时点样本**算出的均值方差极大，写进训练数据
        //   等于加噪声」—— 而心电会话那一条本身就覆盖整段 30 秒、是这段的平均心率，
        //   它一条就够（且与血压同刻，比外部心率表的值更对口）。
        //
        //   ⚠ 不区分会让**用户被引导的标准流程**继续失败：那个 ±5 分钟窗口里往往只有
        //     这 1 条心电心率（三星健康的心率序列约 10 分钟一个点，±5 分钟内常常 0~1 条）
        //     ⇒ `allHr.size = 1 < 3` ⇒ 仍判 `Unusable` ⇒ **配对仍然为 0**。
        //     v88 只把心电心率「计入」了，没处理这个门槛 —— 那是修得不够。
        //   ⚠ `hrMean` 的**算法一字未动**（仍是全量平均），只改「样本够不够」这一条判据，
        //     所以已落库的历史样本口径不受影响。
        val hrSamples = if (fromEcg.isNotEmpty()) maxOf(allHr.size, hrMinSamples) else allHr.size
        val hrMean = if (hrSamples < hrMinSamples) null else allHr.sum() / allHr.size
        // ⚠ HRV 取窗口内**离中点最近**的那一次会话，而不是平均：
        //   平均两个不同时刻的 RMSSD 没有生理意义（同一条记录里 `tryPair` 也守这条）。
        // ⚠⚠ **只收心电来源**（2026-10-03 补）：`watch_ecg` 是 R 峰间期、`watch_ppg` 是脉搏
        //   间期，**不是一个量** —— 混进同一维特征会让模型学到一个混合统计量，而且**不报错**
        //   （与 `StressSummary` 只收心电来源同一条红线）。取不到就让那一维缺着，
        //   `BpPredict` 会自动少用一个特征，而不是填补。
        val mid = (from + to) / 2
        val hrvRmssdMs = db.hrvDao().between(from, to)
            .filter { it.source == Hrv.Source.WATCH_ECG }
            .minByOrNull { abs(it.measuredAt - mid) }
            ?.let { Hrv.quickRmssdMs(it.rrList()) }
        return Signals(hrMean = hrMean, hrvRmssdMs = hrvRmssdMs, hrSamples = hrSamples)
    }

    /**
     * [windowSignals] 的结果。
     *
     * ⚠ [hrSamples] 是**等效样本数**，不是「窗口里原始记录的条数」：窗口内只要有**心电会话**，
     *   它自带的那一条会话级心率就按 [BpPredict.MIN_HR_SAMPLES] 计（理由见 [windowSignals]）。
     *   把它当原始条数用会出错。
     */
    data class Signals(
        val hrMean: Int?,
        val hrvRmssdMs: Int?,
        val hrSamples: Int,
    )

}
