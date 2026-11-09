package com.gemini.healthbridge.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.gemini.healthbridge.core.BiaResultDto
import com.gemini.healthbridge.core.ActivityDto
import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.HrvDto
import com.gemini.healthbridge.core.MfBiaDto
import com.gemini.healthbridge.core.PpgDto

@Entity(
    tableName = "heart_rates",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class HeartRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bpm: Double,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 0,
)

@Entity(
    tableName = "blood_pressures",
    indices = [
        Index(value = ["source", "recordedAt"], unique = true),
        Index(value = ["recordedAt"]),
        Index(value = ["synced", "recordedAt"]),
    ],
)
data class BloodPressureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val note: String? = null,
    val recordedAt: Long,
    val source: String,
    val synced: Int = 0,
    /**
     * 测量时的**体位**（[com.gemini.healthbridge.core.Bp.Posture]）。
     *
     * ⚠⚠ 这是**"过期即永失"的输入** —— 用户事后不会记得当时是怎么坐的，
     *   所以哪怕分层算法还没做也必须先存下来（2026-10-03 实测：坐↔站让 PAT 差 21 ms，
     *   而逐拍离散度只有 3~7 ms）。理由与用途见 `Bp.Posture` 的类注释。
     * ⚠ 可空：老数据没有；用户没选时为 null。**null ≠ 坐姿。**
     */
    val posture: String? = null,
)

/**
 * 血压校准的**配对样本**（模块 #14 方案 C，2026-10-02）。v19 新增。
 *
 * ## 为什么只存配对元数据、读数不复制
 *
 * 袖套读数在 [BloodPressureEntity] 里**已经有一份**。这里再存一份就是
 * 「同一份数据两处存」—— 本项目为 PAT 与波形表栽过这个跟头（两处迟早漂移）。
 * 所以本表**只存配对那一侧的信息**，读数靠 [bpId] 回去取。
 * 读取时用 `INNER JOIN blood_pressures`，读数被删的孤儿行会自然消失，
 * 因此**刻意不建外键**（本项目所有表都没有外键，别在这里破例）。
 *
 * ## 一行代表什么
 *
 * 一次**袖套测量** + 与它同时段（±[BpPredict.PAIR_WINDOW_MS]）采到的手表数据。
 * ⚠ 窗口内没有 PAT 就**不写这一行** —— 跨窗口借用会造出
 * 「看起来合法但量错了」的样本（同一类错见项目设计说明的 `rPeaks` 纪律）。
 *
 * ## 质量位
 *
 * [quality] 取 [BpPredict.Quality]：0 = 不可用（**不写库**）· 1 = 可用 ·
 * 2 = 用户声明「安静状态下测量」的黄金档。保留 0 这个取值是为了让
 * DAO 的 `quality > 0` 过滤在数据出错时也不会漏。
 */
@Entity(
    tableName = "bp_calibration_samples",
    indices = [Index(value = ["pairedAt"])],
)
data class BpCalibrationSampleEntity(
    /** 对应 [BloodPressureEntity.id]。一个读数最多一条配对样本。 */
    @PrimaryKey val bpId: Long,
    /** 配对窗口中心（毫秒），与那条读数的 `recordedAt` 对齐。 */
    val pairedAt: Long,
    /** 窗口内 PAT 的中位数（毫秒）。窗口内没采到 → 整行不写。 */
    val patMs: Int?,
    /** 窗口内 PAT 的离散度（毫秒，1.4826×MAD）。超过 15 判不可用。 */
    val patSpreadMs: Int?,
    /** 窗口内心率均值（次/分）。 */
    val hrMean: Int?,
    /**
     * 窗口内 HRV RMSSD（毫秒）。回归的**候选特征**，缺值时该维被整个丢掉
     * （`BpPredict` 不会拿 0 或均值去填补）。
     *
     * ⚠ BMI **刻意不入表**：它是档案级量（不随某次测量变），
     * 训练与预测时各读一次档案即可，存进每行只会是同一份数据的 N 个副本。
     */
    val hrvRmssdMs: Int? = null,
    /** 配对窗口宽度（毫秒），默认 ±5 分钟。 */
    val windowMs: Long,
    val quality: Int,
    /**
     * 那条读数记录下来的**测量体位**（[com.gemini.healthbridge.core.Bp.Posture]）。
     *
     * ⚠ 在配对时**从血压记录复制过来**，不是重新问一遍 —— 体位属于"那次测量"，
     *   不属于"配对动作"。
     * ⚠ 可空：老数据没有；用户没选时也为 null。**null ≠ 坐姿**
     *   （分层的前提是"这一组的体位确定是同一个"，未知混进去就破坏了前提）。
     */
    val posture: String? = null,
)

@Entity(
    tableName = "spo2",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class SpO2Entity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val percent: Int,
    val heartRate: Int? = null,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 0,
)

/**
 * 体温记录（腕部皮肤温度）。
 *
 * 这张表刻意**同时容纳两种口径**，靠 [source] 区分，界面上也分开画：
 *  - `watch_temp`  —— 手表 `SKIN_TEMPERATURE_ON_DEMAND` 点检的**绝对**腕部皮肤温度（约 30~36 ℃）；
 *  - `samsung_skin` —— 三星健康睡眠期的 `SKIN_TEMPERATURE`（很可能是相对个人基线的偏差，±0.x）。
 *
 * 为什么不建两张表：`(source, measuredAt)` 这个唯一索引本身就是"同一时刻同一来源只留一条"，
 * 已经能把两者分开去重；而多一张表就要多一套 DAO/备份/同步分支。
 * 真正必须守住的是**界面不能把它们画进同一条曲线** —— 那是 `Temperature.scaleOf()` 的职责。
 *
 * @param celsius 摄氏度。含义随 [source] 变（绝对温度 / 相对偏差），口径判定见 `Temperature.scaleOf`。
 * @param ambientC 环境温度（℃），仅手表点检时三星传感器会一并给出；拿不到时为 null。
 */
@Entity(
    tableName = "temperatures",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class TemperatureEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val celsius: Float,
    val ambientC: Float? = null,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 0,
)

/**
 * ECG 波形记录：一次心电测量的波形与统计。
 *
 * v5 起这张表**参与同步**：手表的测量结果会随 `ecg_records`（结论/心率）一起，
 * 通过 `HealthItemDto.wave` 等字段把降采样波形同步到手机，手机端写入同名的本表。
 * `(source, measuredAt)` 唯一索引用于两端去重——手表与手机写的是同一条记录。
 *
 * @param waveJson 0.5~40Hz 带通后的波形（毫伏，逗号分隔），按约 100Hz 降采样，30 秒约 3000 点。
 *   必须是带通后的信号：原始 ECG_MV 带着约 200mV 电极直流偏置，直接画出来只有噪声。
 * @param sampleRate 实测采样率（Hz），来自 `ValueKey.EcgSet.SEQUENCE` 的序号跨度。
 * @param regular 节律是否整齐：1=整齐 0=不齐 null=无法评估。
 */
@Entity(
    tableName = "ecg_waves",
    // 仅本机表（没有 synced 列）：只补时间序索引，供 `ORDER BY measuredAt DESC LIMIT n` 免排序。
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
    ],
)
data class EcgWaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val minMv: Float,
    val maxMv: Float,
    val avgMv: Float,
    val waveJson: String,
    val source: String,
    /** 节律分析结论（窦性心律/房颤提示/心率偏高/心率偏低/无法判定），v4 新增。 */
    val classification: String? = null,
    /** 分析推算的平均心率（次/分）。 */
    val analyzedHr: Int? = null,
    /** 实测采样率（Hz）。 */
    val sampleRate: Int? = null,
    /** 节律是否整齐（1/0，null = 无法评估）。 */
    val regular: Int? = null,
    /** 检出的 R 峰（心跳）个数。 */
    val beatCount: Int? = null,
    /**
     * 拍模板相干性（0~2），>= 0.70 视为电极接触良好（阈值见 wear 端 EcgSignal.COHERENCE_MIN）。
     * ⚠ 列名沿用历史命名：2026-09-14 之前这里存的是 **QRS 频带能量占比**，
     *    该判据已被真机数据证伪（对白噪声给 0.286、对纯正弦给 0.366，却把真心电配漂移压到 0.020），
     *    现改为存 **叠拍相干性**。未改列名是为了避免再升一次数据库版本。
     */
    val bandRatio: Double? = null,
)

/**
 * 体成分记录（一次 BIA 测量的一份结果，v7 新增）。
 *
 * ## 为什么把"当时用的资料"也存进来
 * BIA 的体脂率/体水量是**算法用资料（身高/体重/性别/年龄）反推出来的**，
 * 同一份阻抗配上不同资料会算出不同结果。所以一条记录不带上当时的资料就无法复核 ——
 * 用户改了体重之后回头看旧记录，会以为"同一时刻测出两个值"。
 * 这四列（[weightKg]/[heightCm]/[gender]/[age]）就是**当时实际传给 SDK 的资料快照**。
 *
 * ## 为什么所有测量字段都可空
 * SDK 在失败时（见 `BodyComposition.statusOf`）只回状态码、不回数值。
 * 全部设成非空就必须编一个 0 填进去，而 0 会在界面上显示成"体脂率 0%"，比"没有值"坏得多。
 *
 * 取值范围一律以 `BodyComposition` 里的常量为准（逐条对齐三星官方文档）。
 *
 * @param totalBodyWater 体水，单位是**升**（官方文档写的是 liters，不是 kg）。
 * @param impedanceDegree 相角，单位是度，**取值为负**（-90 ~ 0）。
 * @param source `BiaSource.WATCH`（手表测量）或 `BiaSource.SAMSUNG`（三星健康既有记录）。
 */
@Entity(
    tableName = "body_compositions",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class BodyCompositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyFatRatio: Float? = null,
    val bodyFatMass: Float? = null,
    val totalBodyWater: Float? = null,
    val skeletalMuscleRatio: Float? = null,
    val skeletalMuscleMass: Float? = null,
    val basalMetabolicRate: Float? = null,
    val fatFreeRatio: Float? = null,
    val fatFreeMass: Float? = null,
    val impedanceMagnitude: Float? = null,
    val impedanceDegree: Float? = null,
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    val gender: Int? = null,
    val age: Int? = null,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 0,
)

/** ECG 记录：v1 存结论元数据（分类/心率/备注），原始波形接入后扩展 wave 字段。 */
@Entity(
    tableName = "ecg_records",
    indices = [
        Index(value = ["source", "recordedAt"], unique = true),
        Index(value = ["recordedAt"]),
        Index(value = ["synced", "recordedAt"]),
    ],
)
data class EcgRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classification: String,
    val heartRate: Int? = null,
    val note: String? = null,
    val recordedAt: Long,
    val source: String,
    val synced: Int = 0,
    /**
     * 逐拍 PAT（脉搏到达时间）的中位数，毫秒。v17 新增。
     *
     * ⚠ **这不是血压**，只是"心电 R 峰 → 手腕脉搏波足"的时间。理由见 `core/Ptt.kt`
     * 的类注释：`PAT = PEP + PTT`，而 PEP 在手表上看不见、且随情绪/体位变化 10~20 ms
     * 与血压无关 —— 所以这个量换算不成 mmHg，本项目只给毫秒。
     *
     * ## 为什么这个"派生量"破了「派生量一律不落库」那条规矩
     *
     * 那条规矩的理由是"落库会产生用旧算法算出的历史值、且无法区分"，前提是
     * **重算所需的输入还在**。这里不成立：PAT 要在**全速率**（约 500 Hz）上算，
     * 而存下来的心电波形已经降采样到约 100 Hz（10 ms 分辨率），
     * 与 PAT 的有效效应（10~20 ms）同量级 —— 事后重算没有意义。
     *
     * 所以这里的处境与 `hrv_sessions.rrMs` **完全一样**：全速率的输入本来就没保留，
     * "结果"就是它的输入。PAT 与 RR 间期同类，是**测量结果**而不是评分/估算，
     * 所以它落库。（另一条好处：手机端不必重算就能画逐拍图。）
     *
     * ## 为什么落在这一张表而不是 `ecg_waves`
     *
     * ⚠ `ecg_waves` **不参与同步**（它没有 `synced` 列），只有本表参与。
     * 落在波形表里的话，手机端永远看不到 PAT —— 而"看长期趋势"正是手机端该干的事。
     * 两处都存又是同一份数据的两个副本，迟早漂移，所以只留这一处。
     */
    val patMs: Int? = null,
    /** 逐拍 PAT 的离散度（毫秒，1.4826×MAD）。v17 新增。 */
    val patSpreadMs: Int? = null,
    /** 逐拍 PAT 序列（毫秒，逗号分隔整数），与 `hrv_sessions.rrMs` 同一做法。v17 新增。 */
    val patSeriesMs: String? = null,
    /**
     * **为什么这次没算出 PAT**（`Ptt.Result.NotApplicable.reason` 原文），算出来时为 null。
     *
     * 存它而不是只存在手表当次的结果页上：用户可能测了十次都没成功，
     * 而手机端只看到一片空白 —— 那正是本项目最防的"静默失败"。
     * 有了这一列，手机端能直接说清是缺样 / 拍数不足 / 波动过大 / 幅度不足 / 两路不一致。
     */
    val patNote: String? = null,
    /**
     * 这个 `patMs` 是**哪一套口径**算出来的（`Ptt.ALGO_VERSION`）。v18 新增。
     *
     * ⚠ 与 `Hrv.source`、`Backfill` 的来源标记同一类东西：**两个口径不许默默混**。
     * 手机端的"自身基线"只收 `patAlgo >= Ptt.ALGO_VERSION` 的记录 ——
     * 收错不会报错，只会让基线变成一个混合统计量。
     * 老记（null/1）**不删**（那是用户数据），只是不进基线。
     */
    val patAlgo: Int? = null,
)

/**
 * 体成分记录 -> 同步 DTO（手表发出去时用）。
 *
 * 放在实体旁边、而不是放进 `core/BodyComposition.kt`：
 * 那个文件要保持**无 Room 依赖**，否则只编译它一个文件的离线台架就跑不起来
 * （台架是纯 Kotlin + JDK，没有 androidx.room）。
 */
fun BodyCompositionEntity.toSyncDto(): BiaResultDto = BiaResultDto(
    bodyFatRatio = bodyFatRatio,
    bodyFatMass = bodyFatMass,
    totalBodyWater = totalBodyWater,
    skeletalMuscleRatio = skeletalMuscleRatio,
    skeletalMuscleMass = skeletalMuscleMass,
    basalMetabolicRate = basalMetabolicRate,
    fatFreeRatio = fatFreeRatio,
    fatFreeMass = fatFreeMass,
    impedanceMagnitude = impedanceMagnitude,
    impedanceDegree = impedanceDegree,
    weightKg = weightKg,
    heightCm = heightCm,
    gender = gender,
    age = age,
)

/**
 * 同步 DTO -> 体成分记录（手机收到手表推送时用）。
 *
 * [synced] 固定为 1：手机端本就不参与这条链路的上行同步，
 * 落库即"已同步"，否则待同步计数会永远挂着一个不会消失的数字。
 */
fun BiaResultDto.toEntity(measuredAt: Long, source: String, synced: Int = 1): BodyCompositionEntity =
    BodyCompositionEntity(
        bodyFatRatio = bodyFatRatio,
        bodyFatMass = bodyFatMass,
        totalBodyWater = totalBodyWater,
        skeletalMuscleRatio = skeletalMuscleRatio,
        skeletalMuscleMass = skeletalMuscleMass,
        basalMetabolicRate = basalMetabolicRate,
        fatFreeRatio = fatFreeRatio,
        fatFreeMass = fatFreeMass,
        impedanceMagnitude = impedanceMagnitude,
        impedanceDegree = impedanceDegree,
        weightKg = weightKg,
        heightCm = heightCm,
        gender = gender,
        age = age,
        measuredAt = measuredAt,
        source = source,
        synced = synced,
    )

/**
 * 活动片段（一段加速度采集的统计结果，v8 新增）。
 *
 * ## 为什么存"片段"而不是原始样本
 * 逐样本存会失控：50 Hz × 3 轴 × Float = **600 字节/秒**，一小时就是 2 MB。
 * 而这张表的用途只有两个 —— 给 HRV/呼吸率/压力评分提供"这段时间人在动还是在静"的上下文，
 * 以及在手机上画一条活动曲线。两者需要的都是**每段的统计量 + 一条降采样包络**，
 * 原始样本在采集结束时就没有用了（时间轴上没有第二种用途）。
 * 一条片段 ≈ 0.6 KB（其中包络占大半），量级完全可控。
 *
 * 原始样本刻意**只在内存里活过一次采集**，落库时已经变成 [stdG]/[stillRatio]/[envelope]。
 *
 * ## 单位一律是 g
 * 模长与各分量都归一到 g（以本段模长中位数为 1 g 基准，见 `Activity.normalize`），
 * 因为输入单位本身是**不确定的**：平台传感器给 m/s²，三星 Sensor SDK 给的是文档没写单位的
 * `Int` 原始计数。归一化之后两边共用同一张表、同一套阈值。
 *
 * @param startedAt 片段起始时刻（epoch 毫秒），与 [source] 组成去重键。
 * @param stdG 模长标准差（g），**分级主判据**，见 `Activity.levelOf`。
 * @param stillRatio 满足 ||a|-1g| < 0.05 的样本占比（0~1）。
 * @param peakJerkG 相邻样本模长差的最大值（g），描述瞬时冲击强度。
 * @param meanXG/meanYG/meanZG 平均重力向量（g），即姿态方向，留给后续姿态/佩戴分析。
 * @param envelope 降采样包络（g，逗号分隔，见 `Activity.envelope`），只用于画图。
 */
@Entity(
    tableName = "activities",
    indices = [
        Index(value = ["source", "startedAt"], unique = true),
        Index(value = ["startedAt"]),
        Index(value = ["synced", "startedAt"]),
    ],
)
data class ActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAt: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val level: String,
    val meanG: Float,
    val stdG: Float,
    val p95G: Float,
    val stillRatio: Float,
    val peakJerkG: Float,
    val meanXG: Float? = null,
    val meanYG: Float? = null,
    val meanZG: Float? = null,
    val envelope: String? = null,
    val source: String,
    val synced: Int = 0,
)

/** 活动片段 -> 同步 DTO（手表发出去时用）。 */
fun ActivityEntity.toSyncDto(): ActivityDto = ActivityDto(
    durationMs = durationMs,
    sampleCount = sampleCount,
    level = level,
    meanG = meanG,
    stdG = stdG,
    p95G = p95G,
    stillRatio = stillRatio,
    peakJerkG = peakJerkG,
    meanXG = meanXG,
    meanYG = meanYG,
    meanZG = meanZG,
    envelope = envelope,
)

/**
 * 同步 DTO -> 活动片段（手机收到手表推送时用）。
 *
 * [synced] 固定为 1：手机端不参与这条链路的上行同步，落库即"已同步"，
 * 否则待同步计数会永远挂着一个不会消失的数字。
 */
fun ActivityDto.toEntity(startedAt: Long, source: String, synced: Int = 1): ActivityEntity =
    ActivityEntity(
        startedAt = startedAt,
        durationMs = durationMs,
        sampleCount = sampleCount,
        level = level,
        meanG = meanG,
        stdG = stdG,
        p95G = p95G,
        stillRatio = stillRatio,
        peakJerkG = peakJerkG,
        meanXG = meanXG,
        meanYG = meanYG,
        meanZG = meanZG,
        envelope = envelope,
        source = source,
        synced = synced,
    )

/**
 * PPG 原始光信号会话（v9 新增）。
 *
 * ## 这张表存的是"波形"，不是"指标"
 * 总表 #8 的产出物就是原始波形本身 —— 它要喂给后续的自研算法（#11 呼吸率、#12 压力评分、
 * #14 的 PPG 侧）。所以记录的主体是 [greenWave]（以及可选的 [redWave]/[irWave]），
 * 其余列只是让界面**不解析波形也能显示**的摘要。
 *
 * ## 波形一律是**归一化**的，且这个归一化是不可逆的（除 DC 外）
 * [greenWave] 存的是 `(x - dc) / dc`（无量纲，DC 的倍数），**不是** SDK 给的原始计数。
 * 原因见 `Ppg` 的类注释：原始计数没有物理单位、且受增益/LED 电流/环境光影响，
 * 跨会话不可比，直接存下来等于存了一堆不可解读的数字。
 * 反推回原始计数需要 [dcGreen]（`raw ≈ norm × dc + dc`），所以这一列必须一起存 ——
 * 它同时也是"当时 LED 亮度/贴合程度"的记录，日后排查灌注指数异常时要用。
 *
 * ## 刻意不解释 SDK 的状态码
 * [statusSeen] 只是把本会话出现过的 `GREEN_STATUS/IR_STATUS/RED_STATUS` 原样列出来（如 `"0"`、`"-1,0"`）。
 * 三星没有公开这几个码的语义，而这里的数据会被显示给用户、会被写进备份 ——
 * 靠猜出来的状态语义去写"信号良好/不佳"，一旦猜错就是**看起来很正常**的错误结论。
 * "数据好不好"只由 [quality]（有效样本占比）这个可自证的量表达。
 *
 * ## 刻意不从红光/红外推血氧
 * 比例-比值法的标定系数逐机型不同，没有厂商标定就是编数；血氧本来就有三星自己的
 * SPO2 通道在供（走 `spo2` 表）。这里存红光/红外波形，是为了将来真有标定数据时可用。
 *
 * @param measuredAt 会话起始时刻（**首个有效样本**的时刻，epoch 毫秒），与 [source] 组成去重键。
 *   刻意用首个样本时刻而不是"按下测量的时刻"：前者才能与波形的时间轴对齐。
 * @param durationMs 首个样本到最后一个样本的时间跨度（不含连接与收尾的等待）。
 * @param sampleCount 存入 [greenWave] 的**有效**点数（已剔除 ADC 计数为 0 的无效样本）。
 * @param sampleRateHz 估算采样率（Hz），由数据点时间戳判定单位后算出（退化为墙钟推算）。
 *   null = 没能估出来，此时**不给脉率估算**。
 * @param channels 本会话实际采到的通道令牌，固定顺序（见 `Ppg.encodeChannels`），如 `"green,red,ir"`。
 * @param dcGreen 绿光通道直流电平（原始计数的中位数）。
 * @param acGreenP2P 绿光通道交流分量稳健峰峰值（原始计数，p95-p5）。
 * @param quality 数据完整度（0~1）= 有效样本 / 总样本。**不是临床意义的信号质量**。
 * @param estimatedHr 仅由本段波形独立估算的脉率（次/分），null = 门限未过。
 * @param refHr 同一次测量由心率/血氧通道给出的参考心率，用于与 [estimatedHr] 对照。
 * @param statusSeen 本会话出现过的通道状态码（原样记录，不解释），仅供排查。
 */
@Entity(
    tableName = "ppg_waves",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class PpgWaveEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val sampleRateHz: Int? = null,
    val channels: String,
    val dcGreen: Float? = null,
    val acGreenP2P: Float? = null,
    val quality: Float? = null,
    val estimatedHr: Int? = null,
    val refHr: Int? = null,
    val statusSeen: String? = null,
    /** 绿光归一化交流波形（DC 的倍数，6 位小数，逗号分隔）。 */
    val greenWave: String? = null,
    /** 红光归一化交流波形；设备/会话未采集时为 null。 */
    val redWave: String? = null,
    /** 红外归一化交流波形；设备/会话未采集时为 null。 */
    val irWave: String? = null,
    val source: String,
    val synced: Int = 0,
)

/** PPG 会话 -> 同步 DTO（手表发出去时用）。 */
fun PpgWaveEntity.toSyncDto(): PpgDto = PpgDto(
    durationMs = durationMs,
    sampleCount = sampleCount,
    sampleRateHz = sampleRateHz,
    channels = channels,
    dcGreen = dcGreen,
    acGreenP2P = acGreenP2P,
    quality = quality,
    estimatedHr = estimatedHr,
    refHr = refHr,
    statusSeen = statusSeen,
    greenWave = greenWave,
    redWave = redWave,
    irWave = irWave,
)

/**
 * 同步 DTO -> PPG 会话（手机收到手表推送时用）。
 *
 * [synced] 固定为 1：手机端不参与这条链路的上行同步，落库即"已同步"，
 * 否则待同步计数会永远挂着一个不会消失的数字。
 */
fun PpgDto.toEntity(measuredAt: Long, source: String, synced: Int = 1): PpgWaveEntity =
    PpgWaveEntity(
        measuredAt = measuredAt,
        durationMs = durationMs,
        sampleCount = sampleCount,
        sampleRateHz = sampleRateHz,
        channels = channels,
        dcGreen = dcGreen,
        acGreenP2P = acGreenP2P,
        quality = quality,
        estimatedHr = estimatedHr,
        refHr = refHr,
        statusSeen = statusSeen,
        greenWave = greenWave,
        redWave = redWave,
        irWave = irWave,
        source = source,
        synced = synced,
    )

/**
 * 一次 HRV 分析的输入（模块 #9）：**一串逐拍间期**，加上"这次是在什么条件下测的"。
 *
 * ## 为什么只存输入、不存指标
 * RMSSD / SDNN / LF / HF 全都是 [Hrv.analyze] 的**确定性派生量**：同一个 RR 序列
 * 只会算出一组值。把指标也落库就多了一份会和算法版本脱节的数据（改了门限或标定，
 * 历史行还是旧口径，而且看不出哪一行是旧口径）。所以这里只存**记录事实**，
 * 指标在手表结果页与手机卡片上都现算 —— 与 #10 核心体温"不落库、现算"是同一条理由。
 *
 * ## 为什么存的是**未经伪迹修正**的原始间期
 * 伪迹修正是分析的一部分（含门限与容差），而且它会随算法版本变。存原始序列，
 * 旧记录在新版本下能重新按新规则分析；存修正后的序列就把旧算法的判断永久固化了。
 *
 * ## 为什么时长/拍数这两个"能算出来的"还是存了
 * 它们不是判定结论而是**记录事实**（这段心电图本身有多长、检出多少拍），
 * 且列表页要在不解析 CSV 的前提下显示一行摘要 —— 与 `PpgWaveEntity.sampleCount` 同理。
 *
 * @param measuredAt 与同一次心电记录**同一时间戳**（配对靠它），也是两端去重键。
 * @param durationMs 这次心电记录的时长（毫秒）。
 * @param rrCount 存进 [rrMs] 的间期个数。
 * @param rrMs 逐拍间期（毫秒，逗号分隔，**未修正**）。30 秒的记录约 35 个值、不到 200 字节。
 * @param refHr 同一次心电给出的心率（次/分），仅用于与 HRV 结果交叉核对。
 * @param ecgRegular 心电的节律结论：1=整齐、0=不齐、null=无法判定。
 *   **0 时 [Hrv.analyze] 会直接拒答** —— 节律不齐时的 RR 变异来自心律失常本身，
 *   不是自主神经张力。所以它是分析必需的输入，必须跟着记录一起存。
 * @param activityLevel 测量时的活动档位（见 `Activity.Level`），#7 的结论。
 *   拿不到时为 null —— **null 不等于静息**（见 `Hrv.RestState`）。
 */
@Entity(
    tableName = "hrv_sessions",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class HrvSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: Long,
    val durationMs: Long,
    val rrCount: Int,
    val rrMs: String,
    val refHr: Int? = null,
    val ecgRegular: Int? = null,
    val activityLevel: String? = null,
    val source: String,
    val synced: Int = 0,
)

/** 逐拍间期 <-> CSV：编解码与上限都在 `Hrv` 里（纯逻辑层，离线台架能直接测）。 */
fun HrvSessionEntity.rrList(): List<Int> = Hrv.decodeRr(rrMs)

/** HRV 会话 -> 同步 DTO（手表发出去时用）。 */
fun HrvSessionEntity.toSyncDto(): HrvDto = HrvDto(
    durationMs = durationMs,
    rrCount = rrCount,
    rrMs = rrMs,
    refHr = refHr,
    ecgRegular = ecgRegular,
    activityLevel = activityLevel,
    // ⚠ 来源必须跟着走：心电与 PPG 是两套基线，落库时认错来源就再也分不开了。
    source = source,
)

/**
 * 同步 DTO -> HRV 会话（手机收到手表推送时用）。
 *
 * [synced] 固定为 1：手机端不参与这条链路的上行同步，落库即"已同步"，
 * 否则待同步计数会永远挂着一个不会消失的数字。
 */
fun HrvDto.toEntity(measuredAt: Long, synced: Int = 1): HrvSessionEntity =
    HrvSessionEntity(
        measuredAt = measuredAt,
        durationMs = durationMs,
        rrCount = rrCount,
        rrMs = rrMs,
        refHr = refHr,
        ecgRegular = ecgRegular,
        activityLevel = activityLevel,
        // 来源**从 DTO 里取**，不再由调用方传 —— 以前调用方写死 `WATCH_ECG`，
        // PPG 会话会被记成心电会话（两条基线混在一起，且不报错）。
        source = source,
        synced = synced,
    )

/**
 * 节律结论转成 [Hrv.analyze] 的入参：1→true、0→false、null→null。
 *
 * 两端都必须走这个函数（而不是各自写 `ecgRegular == 1`）：
 * 写成 `== 1` 会把"无法判定"当成"不齐"，让一次本来有效的心电被 HRV 拒答。
 */
fun HrvSessionEntity.ecgRegularFlag(): Boolean? = when (ecgRegular) {
    1 -> true
    0 -> false
    else -> null
}

/**
 * 经期记录（模块 #13）。
 *
 * `startEpochDay`/`endEpochDay` 是**本地日期**的 epochDay（不是毫秒时间戳）——
 * 经期是"哪天"的事，跟几点几分无关，用天数彻底躲开时区与夏令时。
 *
 * `endEpochDay == null` 表示"只记了开始日，还没记结束"。
 *
 * 这张表**只有手机端写**（手动录入 + Health Connect 导入），
 * 因此**没有 `synced` 字段**：它不参与手表→手机的上行同步。经期数据的方向是
 * 手机 → 手表（推送一行摘要），走 `FrameKinds.CYCLE_SUMMARY` 而非 `RecordType`。
 */
@Entity(
    tableName = "menstrual_records",
    indices = [Index(value = ["source", "startEpochDay"], unique = true)],
)
data class MenstrualRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val flow: String? = null,
    val source: String,
)

/** 实体 <-> `Cycle.Entry`。缺结束日为 null 时直接透传，不做任何猜测。 */
fun MenstrualRecordEntity.toEntry(): com.gemini.healthbridge.core.Cycle.Entry =
    com.gemini.healthbridge.core.Cycle.Entry(
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        flow = flow,
        source = source,
    )

/** `Cycle.Entry` -> 实体（录入/导入落库时用）。 */
fun com.gemini.healthbridge.core.Cycle.Entry.toEntity(): MenstrualRecordEntity =
    MenstrualRecordEntity(
        startEpochDay = startEpochDay,
        endEpochDay = endEpochDay,
        flow = flow,
        source = source,
    )

// ═══════════════════════ 多频生物电阻抗（MF-BIA） ═══════════════════════

/**
 * 一次多频阻抗测量的**原始值**：四个频点（5/10/50/250 kHz）各带阻抗幅值与相位。
 *
 * ## 为什么落库的是原始值而不是派生指标
 * 落库的就该是"只有手表能采到、错过就没了"的东西 —— 原始阻抗正是这一类
 * （手机侧的三星健康 Data SDK 不开放任何阻抗数据，手表上一次测量的原始值不同步上来就永远丢了）。
 * 而相位角、5k/250k 阻抗比、与基线的偏差全是**确定性派生量**，
 * 每次从这几个原始值现算即可（`MfBia.analyze`），改算法时历史口径会一起改 ——
 * 与 #10 核心体温、#12 压力评分"派生量不落库"是同一条纪律。
 *
 * ## 相位列不带符号约定
 * 三星没有公开相位的符号含义（报告的是超前还是滞后、以哪个频率为基准），
 * 所以这里**原样存**。任何"相位偏低"的结论都缺少依据，不要在这里加。
 *
 * @param status SDK 状态码（0 = 成功）。原样记录，不解释语义。
 * @param source 来源常量（见 `MfBiaSource`）。
 */
@Entity(
    tableName = "mf_bia_records",
    indices = [
        Index(value = ["source", "measuredAt"], unique = true),
        Index(value = ["measuredAt"]),
        Index(value = ["synced", "measuredAt"]),
    ],
)
data class MfBiaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val measuredAt: Long,
    /** 5 kHz 阻抗幅值（Ω）。 */
    val m5: Float? = null,
    /** 5 kHz 相位（度，符号未定义，原样存）。 */
    val p5: Float? = null,
    val m10: Float? = null,
    val p10: Float? = null,
    val m50: Float? = null,
    val p50: Float? = null,
    val m250: Float? = null,
    val p250: Float? = null,
    val status: Int? = null,
    val source: String,
    val synced: Int = 0,
)

/** MF-BIA 会话 -> 同步 DTO（手表发出去时用）。 */
fun MfBiaEntity.toSyncDto(): MfBiaDto = MfBiaDto(
    m5 = m5, p5 = p5,
    m10 = m10, p10 = p10,
    m50 = m50, p50 = p50,
    m250 = m250, p250 = p250,
    status = status,
)

/**
 * 同步 DTO -> 实体（手机收到手表推送时用）。
 *
 * [synced] 固定为 1：手机端不参与这条链路的上行同步，落库即"已同步"，
 * 否则待同步计数会永远挂着一个不会消失的数字（与 `PpgDto.toEntity` 同一条）。
 */
fun MfBiaDto.toEntity(measuredAt: Long, source: String, synced: Int = 1): MfBiaEntity =
    MfBiaEntity(
        measuredAt = measuredAt,
        m5 = m5, p5 = p5,
        m10 = m10, p10 = p10,
        m50 = m50, p50 = p50,
        m250 = m250, p250 = p250,
        status = status,
        source = source,
        synced = synced,
    )

/**
 * 多频阻抗记录的来源标记，两端共用。
 *
 * 只有手表一种来源：三星健康（手机侧）不开放任何阻抗数据。
 * 与 `PpgSource` 同理，**不为"三星来源"预留假值** —— 预留了就会有人填。
 */
object MfBiaSource {
    const val WATCH = "watch_mf"
}

// ═══════════════════════ 夜间氧减指数（模块 #11 的替代落点） ═══════════════════════

/**
 * **一晚一条**的氧减指数摘要（ODI），只在手机端写。
 *
 * ## 为什么这张表必须存在（与「派生量不落库」那条纪律不冲突）
 * #12 的压力/疲劳是**不落库**的，因为它是从已落库记录现算的派生量。但 ODI 不一样：
 * 它的输入是三星健康夜间血氧的**分钟级序列，而那个序列刻意不落库**（一晚 400+ 点会把
 * 「最近血氧记录」刷屏，见项目设计说明）。也就是说源数据每次同步现读即用即弃，
 * **不存摘要就等于这个数只存在于内存里、下次读取失败就永远消失**。
 * 落库的是"一晚一行"的摘要，不是序列本身 —— 大小可以忽略。
 *
 * ## 为什么指标列是可空的
 * "没算出来"与"算出 0 次"是两件事，必须能区分：
 *   · 算出来了 → 指标列有值、[reason] 为 null；
 *   · 没算出来 → 指标列全为 null、[reason] 写明原因（监测时长不够 / 有效点太少 / 全不可信）。
 * 用可空列而不是"塞 0"：ODI=0 会被读成"一夜没有氧减"，而真实情况可能是"只监测了 40 分钟"。
 *
 * @param dateEpochDay 归属日（`LocalDate.now().toEpochDay()`，与推给手表的睡眠摘要同口径：
 *   即"醒来那天"，不是入睡那天）。
 * @param startMs / endMs 这一晚睡眠会话的起止（来自三星睡眠会话，不是血氧序列的首末点）。
 * @param monitoredMinutes 分母：实际监测到的分钟数（见 `Odi` 类注释里"分母用监测时长"）。
 * @param rejectedPoints 被剔除的不可信读数个数，界面如实显示（不剔除会变成假的重度事件）。
 */
@Entity(
    tableName = "sleep_odi",
    indices = [Index(value = ["dateEpochDay"], unique = true)],
)
data class SleepOdiEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val startMs: Long,
    val endMs: Long,
    /** 氧减事件次数；没算出来为 null。 */
    val events: Int? = null,
    /** ODI（次/小时）；没算出来为 null。 */
    val odiPerHour: Double? = null,
    /** 实际监测分钟数；没算出来为 null。 */
    val monitoredMinutes: Double? = null,
    /** 有效读数个数；没算出来为 null。 */
    val validPoints: Int? = null,
    /** 被剔除的不可信读数个数；没算出来为 null。 */
    val rejectedPoints: Int? = null,
    /** 监测到的最低血氧；没算出来为 null。 */
    val lowest: Int? = null,
    /** 有效读数的均值（%）；没算出来为 null。 */
    val mean: Double? = null,
    /** T90（血氧<90% 的时间占比，0~100）；没算出来为 null。 */
    val t90Pct: Double? = null,
    /** 没算出来时的原因（算出来时为 null）。 */
    val reason: String? = null,
    /** 这次计算的时刻，用于判断摘要是否过期。 */
    val computedAt: Long,
)

// ═══════════════ 三星健康的两个"现成指标"（2026-09-19） ═══════════════
//
// 这两张表**故意不带 `source` / `synced`**（与 `sleep_odi` 同款，而不是与 `spo2` 同款）：
// 它们只从三星读、只往手表推**摘要帧**（不推逐条记录），所以既不需要来源区分、
// 也没有"待同步的原始记录"这回事。加两列是噪音。

/**
 * 能量评分（三星健康自己的 Galaxy AI 功能）。
 *
 * 官方说明：**每日**测量用户的身心准备度，由睡眠 + 活动 + 心率综合算出，"给接下来这一天用"。
 * 所以它天然是**一天一条**，读请求也是按日期（`LocalDateBuilder`）而不是按时间点。
 *
 * @param dateEpochDay 归属日（`LocalDate.toEpochDay()`）。唯一索引就在它上面 ——
 *   同一天重复读到的值**覆盖**（三星会重算，我们不该留两条互相矛盾的分）。
 * @param score 分值（0~100）。三星自己的界面显示的就是整数分，所以按 Int 存，
 *   **不做归一化、不做分档**：它不是我们算的，凭印象给它编一个"高/中/低"的切点是越界。
 * @param measuredAt 三星给的这一天起点（startTime），留着便于与三星自身对齐、也便于排查。
 */
@Entity(
    tableName = "energy_scores",
    indices = [Index(value = ["dateEpochDay"], unique = true)],
)
data class EnergyScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val score: Int,
    val measuredAt: Long,
)

/**
 * 睡眠呼吸暂停检测记录（**来源是「三星健康监测器」，不是三星健康**）。
 *
 * 官方说明：三星健康监测器用 Galaxy Watch 监测**重度阻塞性睡眠呼吸暂停**的迹象；
 * 用户在监测器里授权把数据同步给三星健康之后，这里才读得到。
 * 给的是一个**三态枚举** `DETECTED_SIGN`，不是严重程度、也不是次数。
 *
 * ⚠ 所以界面上**不能**把它说成"你有没有呼吸暂停"：
 *   · `NOT_DETECTED` 只是"这次没检测到迹象"，**不等于"没有病"**；
 *   · `UNDEFINED` 是"没判定出来"，更不能当成阴性。
 *
 * @param sign 原样存字符串（`DETECTED` / `NOT_DETECTED` / `UNDEFINED`）。**不转成枚举**：
 *   三星以后加一个取值时，存字符串只是多一种读不懂的值，存枚举会直接崩在解析上。
 * @param startMs 唯一索引：一条检测记录只留一条，重复读到就覆盖。
 * @param dateEpochDay 按记录自身的时区偏移算出的归属日，与睡眠同一套归日口径。
 */
@Entity(
    tableName = "sleep_apnea_records",
    indices = [Index(value = ["startMs"], unique = true)],
)
data class SleepApneaEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    val dateEpochDay: Long,
    val sign: String,
)

/**
 * 一晚睡眠的**统计行**（自研能量评分的输入，2026-09-19）。
 *
 * ## 为什么要新建这张表
 * 在此之前，睡眠只以两种形式存在：① 手机端每次同步现读最近一晚、打包成
 * `SleepSummaryMessage` 推给手表（**只推不存**）；② `sleep_odi` 存了那一晚
 * 与氧减相关的那几个量。也就是说**谁都没存过"这一晚睡了多久"** ——
 * 而"睡了多久"是次日状态最有解释力的单一输入。自研能量评分要能算历史趋势、
 * 要能构造"你自己的基线"，就必须先有每晚一行的历史。
 *
 * ## 为什么存的是"统计行"而不是原始序列
 * 睡眠期的**血氧/心率序列**每次同步都从三星现读即可，
 * **没有落库的必要** —— 与血氧/皮肤温度那两处"只落点级/整晚均值，不落序列"同一条判断。
 * 一晚 400+ 个序列点只会把设备上的库撑大、把「最近记录」列表刷屏。
 *
 * ⚠⚠ **但睡眠阶段段不一样，必须落库**（2026-10-05 补，推翻上面的原始判断）：
 * 上面那条"现读即可"的前提是**读得到**，而阶段段读不到 ——
 * `readSleepBundle` 的窗口写死在 `[昨天18:00, 现在]`，**过了今晚就再也取不回来**。
 * 不存的话「睡眠阶段」详情页永远只能看今晚，历史趋势没有任何素材。
 * 判据与 [SleepOdiEntity] 完全同源（见 `SamsungHealthReader` 第 ⑧ 步的注释）：
 * **"过期即永失"的输入必须落库**，不管它是不是"画图素材"。
 * 落的是**合并后的连续段**（十几段），不是上百条原始记录，量很小。
 *
 * ## 归日口径
 * [dateEpochDay] 是**醒来那天**（不是入睡那天），与 [SleepOdiEntity] 完全一致：
 * 23:00 入睡、07:00 醒来的那一晚，属于醒来那一天。两处口径不一致的话，
 * 睡眠页上两张卡会在跨零点的夜里指向不同的日子。
 *
 * ## 为什么没有 `source` / `synced`
 * 与 [SleepOdiEntity] 同款（而不是与 `spo2` 同款）：只从三星读、只往手表推**摘要帧**，
 * 没有"待同步的原始记录"这回事，加两列是噪音。
 *
 * ## 每个统计量都可空
 * 三星健康可能只有睡眠时段而没开血氧/心率连续监测。**拿不到就是 null，不拿 0 顶** ——
 * 0 会被下游当成一个真实的测量值参与评分（"最低血氧 0%"），而它其实只是"没测"。
 *
 * @param actualSleepMs 实际睡眠时长 = 总时长 − 清醒段（三星健康的"实际睡眠时间"口径）。
 *   单独存而不是每次用阶段现算：阶段段没有落库，现算就必须再读一次三星。
 * @param hrAvg 睡眠期平均心率（次/分）。
 * @param spo2Min **三星自己那一趟序列**的最低血氧（%）。
 *   注意它与 [SleepOdiEntity.lowest] **不是同一个数**：ODI 那个来自它自己
 *   剔除过不可信读数的那套序列，而这里要的是"这一行自身可复现"的值 ——
 *   评分不该依赖另一张表"恰好算出来了"。
 * @param hrSd 睡眠期心率的**标准差**（心率波动）。算它必须有逐点序列，而序列是现读不落库的
 *   （三星只给得到最近一晚），所以只能在读到那一晚顺手把 SD 存下来 ——
 *   否则"睡眠心率变化"这一项永远只有最近一晚能算，趋势图与历史评分全都没有它。
 *   **旧行是 NULL**：那些晚本来就没算过，NULL 表示"没有"而不是"波动为 0"。
 * @param samsungScore 三星自己的睡眠得分（部分机型/版本不提供）。存着只为对照展示。
 * @param recordedAt 这一行是什么时候写进来的（排查"某天为什么没数据"用）。
 */
@Entity(
    tableName = "sleep_nights",
    indices = [Index(value = ["dateEpochDay"], unique = true)],
)
data class SleepNightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val startMs: Long,
    val endMs: Long,
    val actualSleepMs: Long,
    val hrAvg: Int? = null,
    val hrMin: Int? = null,
    val hrSd: Double? = null,
    /**
     * 三星健康给的**夜间皮肤温度原始值**（℃）。
     *
     * 刻意与 `TemperatureEntity` 里那一行冗余：体温表按 `measuredAt` 索引、
     * 没有"这是哪一晚"的概念，而评分要的正是"**那一晚**的皮温"。
     * 靠时间去猜（"落在那一晚区间内"）在跨午夜与补录时会出错，
     * 而"顺手把当晚的值写进当晚那一行"没有这个问题。
     *
     * ⚠ 存**原始值**，口径（相对基线的偏差 / 绝对温度）**留到评分时判**：
     * 三星那一侧官方没写明，落库时判会把一个可能被推翻的判断固化成历史值。
     */
    val skinTemp: Float? = null,
    val spo2Avg: Int? = null,
    val spo2Min: Int? = null,
    val samsungScore: Int? = null,
    /**
     * 这一晚的**睡眠阶段段**（合并后的连续段），编码见 [SleepStages]。
     *
     * 存的是**相对 [startMs] 的偏移量**而不是绝对毫秒：绝对时间戳一个就要 13 位，
     * 一晚十几段就是两百多个字符，而偏移量普遍只有 6~7 位。
     * 取用时用 [startMs] 还原，所以**读取时必须把 startMs 一起给它**。
     *
     * ⚠ **旧行是 NULL**：那些晚上当初没存（这一列是 v21 才加的）。
     *   NULL 表示"那晚没记"，不是"那晚没有阶段" —— 界面必须区分这两者，
     *   否则会把"历史没存"显示成"你那晚没睡好"。
     * @see SleepStages
     */
    val stagesJson: String? = null,
    val recordedAt: Long,
)

/**
 * 把库里那一行 [SleepNightEntity] 还原成界面用的 [SleepNightDto]（2026-10-09 加）。
 *
 * ## 为什么需要它
 * 睡眠页的主卡此前**只认内存里那次同步的结果**（`SleepUiState.night`），
 * 于是"今天还没同步 / 同步失败 / 进程刚重启"时整页空白 ——
 * 哪怕库里躺着几百晚数据。有了这个桥，界面就能在拿不到实时数据时
 * **回退显示库里最近一晚**（回退时界面要标明那是哪一天，见 `SleepUiState.fresh`）。
 *
 * ## ⚠ 三个必须保住的语义
 * 1. **`stagesJson` 为 NULL ≠ "那晚没有阶段"**：那是 v21 加列**之前**存的行，
 *    表示"当初没存"。⇒ 这里返回**空列表**，由调用方决定怎么呈现（不能画成占比 0）。
 * 2. **`hrMin` / `hrMax` 在库里不存在**：本表只存 `hrAvg` / `hrMin` / `hrSd`
 *    （没有 `hrMax`），所以还原出的 DTO 里 `hrMax` 恒为 null ⇒ 界面那几处
 *    "睡眠期心率最高"的行**在回退态下要能接受 null**（显示成"—"而不是 0）。
 * 3. **`actualSleepMs` 是落库时算好的**，不在这里重算：重算要靠 `stages`，
 *    而旧行没有 `stages` ⇒ 重算会把"实际睡眠"退化成"总时长"，**看起来更对、其实更错**。
 *    这里保留 `actualSleepMs` 只是为了将来对齐，DTO 本身仍按 `stages` 现算（见其 `actualSleepMs()`）。
 */
fun SleepNightEntity.toSleepNightDto(): com.gemini.healthbridge.core.SleepNightDto =
    com.gemini.healthbridge.core.SleepNightDto(
        startMs = startMs,
        endMs = endMs,
        score = samsungScore,
        stages = stagesJson
            ?.let { com.gemini.healthbridge.core.SleepStages.decode(it, startMs) }
            .orEmpty(),
        spo2Avg = spo2Avg,
        spo2Min = spo2Min,
        hrAvg = hrAvg,
        hrMin = hrMin,
        // 本表没有 hrMax 这一列（见上面第 2 条），刻意留 null 而不是拿 hrAvg 顶。
        hrMax = null,
    )
/**
 * 一天的活动量（自研能量评分里「前一日的活动」「活动持续性」两项的输入）。
 *
 * ## 为什么落库，而不是评分时现读
 * 三星的聚合算子**能读历史日期**（2026-09-24 实测：昨天 6000 步、前天 3120 步），
 * 所以每次评分都现读是可行的。但评分要画**趋势**（每晚一个点、最多 14 个），
 * 每晚还要问 6 个时段的步数 —— 现读意味着画一次趋势就调几十次跨进程聚合。
 * 落库之后一天只读一次，之后随便查；而且"能回补最近几天"这条也靠它落地。
 *
 * ## 与 `sleep_nights` 是两条独立的记录流
 * - `sleep_nights`：**一晚一行**，归属日是**醒来那天**（与 `SleepOdiEntity` 同口径）。
 * - `activity_days`：**一天一行**，归属日是**自然日**。
 *
 * **刻意不合并成一张表**：两者的归属日不同，合起来必然要在某一侧做日期加减 ——
 * 而"默默 +1 天"的那类换算正是最容易错一天且看不出来的。
 * 评分时由调用方显式取 `activity_days[dateEpochDay - 1]`，这个减法写在明面上。
 *
 * @param steps 全天步数。**null = 没读到，不是 0**（那天可能没带手机/手表，
 *   或三星还没算出当天合计）。用 0 顶替会让"没测到"变成一个真实的测量值参与评分。
 * @param slotSteps 按 6 段（每 4 小时）切分的步数，逗号分隔，
 *   如 `1200,3400,0,800,5200,900`。段数不足或读不到时留 null ——
 *   「活动持续性」那一项会因此**缺席**，而不是拿一个缺了几段的数组硬算。
 * @param activeKcal 活动消耗（千卡）。评分不用，存着是为了将来算"活动强度"
 *   （活动消耗 ÷ 活动时长）—— 那是"活动持续性"的诚实替代品，因为
 *   持续性需要的**分布信息**在总量里已经不存在了。
 * @param activeMs 活动时长。同上，备查。
 * @param recordedAt 这一行是什么时候写进来的（排查"某天为什么没数据"用）。
 */
@Entity(
    tableName = "activity_days",
    indices = [Index(value = ["dateEpochDay"], unique = true)],
)
data class ActivityDayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateEpochDay: Long,
    val steps: Long? = null,
    val slotSteps: String? = null,
    val activeKcal: Float? = null,
    val activeMs: Long? = null,
    val recordedAt: Long,
)

/**
 * 一天的**每小时**心率/血氧桶（2026-10-13，DB v22）。
 *
 * ## 这张表存在的理由
 *
 * 用户的原话：「**不该是每天软件跟三星健康同步后数据都保留在了本地，以后访问历史
 * 都是直接从本地读取嘛**。要是本地也没有数据，再尝试从三星健康读取」。
 *
 * 在那之前：`heart_rate` / `spo2` 两张**点表**只覆盖"今天"（血氧只有 48 小时内的点记录），
 * 翻历史时几乎每条都得现读三星 —— 慢、离线看不到，而同一晚的睡眠页里明明有统计值。
 * 这张表就是"每天同步时把头一天读到的序列压成每小时桶、留在本地"。
 *
 * ⚠ 一天**两行**（`kind` = `hr` / `spo2`），不是一天一行：两者的量纲与刻度都不同，
 *   合成一行会让"这一天有没有心率"变成一个要靠 null 判断的隐式状态。
 *
 * ⚠ 桶里**不存**"这一小时算不算睡眠"：那是显示时按本地 `sleep_nights` 的会话区间推的
 *   （口径只有一处）。存进来就成了第二处，而它俩迟早不一致。
 *
 * @param dateEpochDay **本地**日（`LocalDate.toEpochDay()`），与"今天"的唯一口径一致。
 * @param kind `hr` / `spo2`。⚠ 是**存储键**，不许写成中文 —— 界面语言一变就再也匹配不上。
 * @param buckets `h:min:max:avg` 用 `;` 隔开、按 hour 升序（见 `Vitals.encodeBuckets`）。
 * @param minValue 全天最低（概览卡用，免得每次都解析字符串）。
 *   ⚠⚠ **可空**：一天**读过了但确实没有采样**时 `buckets` 为空串、这三个值给 `null`。
 *   这一行存在的意义是记下「**同步过了、那天没有**」—— 与"本地压根没同步过这一天"
 *   必须能分开：前者不该再去读三星（白读一趟），后者才该去读。
 *   若这里写 0，界面会把"那天最低心率 0"当成一个真实的测量值（同 `hrMin`/`spo2Min` 的纪律）。
 * @param maxValue 全天最高（可空理由同 [minValue]）。
 * @param avgValue 全天平均（可空理由同 [minValue]）。
 * @param sampleCount 那天一共采到多少个点（解释"这根柱为什么这么细"）。0 = 那天没有采样。
 * @param updatedAt 这一行什么时候写的（排查"某天为什么没有"用）。
 */
@Entity(
    tableName = "daily_vitals",
    primaryKeys = ["dateEpochDay", "kind"],
)
data class DailyVitalsEntity(
    val dateEpochDay: Long,
    val kind: String,
    val buckets: String,
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val avgValue: Int? = null,
    val sampleCount: Int,
    val updatedAt: Long,
)
