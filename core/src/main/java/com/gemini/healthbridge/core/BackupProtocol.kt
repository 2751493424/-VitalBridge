package com.gemini.healthbridge.core

import kotlinx.serialization.Serializable

/**
 * 备份文件格式的固定标识。恢复时会先校验它 —— 用户走系统文件选择器时很容易误选别的 json，
 * 有一个明确的标识就能给出"这不是脉迹的备份文件"，而不是抛一个看不懂的解析异常。
 */
const val BACKUP_FORMAT = "vitaltrace-backup"

/** 当前备份格式版本。新增字段时**不需要**改它（缺字段会走默认值），表意变更时才升。 */
const val BACKUP_VERSION = 1

/**
 * 备份文件（v1）的完整内容。
 *
 * 刻意**不复用 Room 实体**当序列化对象：实体是跟着数据库表结构走的，
 * 直接序列化等于把"备份文件的格式"和"表结构"绑死 —— 以后任何一次加列、改列名，
 * 都会让旧备份读不进来或静默丢字段。这里单独定义一层 DTO，
 * 格式版本由 [BackupFile.version] 显式管理，两边各自演化。
 *
 * 所有集合字段都有默认空值：这样旧版本 App 读新备份时，不认识的字段被忽略而不是解析失败。
 */
@Serializable
data class BackupFile(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    /** 导出时刻（毫秒）。仅用于展示，不参与去重。 */
    val exportedAt: Long = 0L,
    val heartRates: List<BkHeartRate> = emptyList(),
    val bloodPressures: List<BkBloodPressure> = emptyList(),
    val ecgRecords: List<BkEcgRecord> = emptyList(),
    val ecgWaves: List<BkEcgWave> = emptyList(),
    val spo2: List<BkSpo2> = emptyList(),
    val temperatures: List<BkTemperature> = emptyList(),
    val bodyCompositions: List<BkBodyComposition> = emptyList(),
    val activities: List<BkActivity> = emptyList(),
    val ppgWaves: List<BkPpg> = emptyList(),
    val hrvSessions: List<BkHrv> = emptyList(),
    val cycleRecords: List<BkCycle> = emptyList(),
    /**
     * 每晚一条的氧减指数摘要。
     *
     * 之所以要单独备份：**过了那一晚，源数据就拿不回来了** ——
     * ODI 的输入是三星健康夜间血氧的分钟级序列，而那个序列刻意不落库（见 `SleepOdiEntity`），
     * 手机端的读取入口又只取"昨晚"。摘要丢了就是永久丢失，不像其他表那样下次同步能补上。
     */
    val sleepOdi: List<BkOdi> = emptyList(),
    /**
     * 多频阻抗测量的**原始值**。
     *
     * 与睡眠摘要同理要备份：原始阻抗只有手表采得到（三星的 Data SDK 不开放任何阻抗数据），
     * 手表刷机或换表之后**再也采不回同一时刻的读数**；而相位角谱、5k/250k 比值这些
     * 派生量全部由这几个原始值现算，原始值丢了就等于整段历史都没了。
     */
    val mfBia: List<BkMfBia> = emptyList(),
    /** 能量评分（三星算的每日准备度）。备份的是三星给的**原值**，不带任何派生量。 */
    val energyScores: List<BkEnergyScore> = emptyList(),
    /** 睡眠呼吸暂停检测记录（来源是三星健康监测器）。 */
    val apneaRecords: List<BkApnea> = emptyList(),
    /**
     * 每晚睡眠统计（自研能量评分的输入）。
     *
     * 必须备份：它是**唯一**存过"这一晚睡了多久"的地方，而读取入口只取"最近一晚" ——
     * 不备份的话，换机之后历史睡眠时长永远取不回来，自研能量评分也就永远建立不起基线。
     */
    val sleepNights: List<BkSleepNight> = emptyList(),
    /**
     * 每天的活动量（能量评分里「前一日的活动」「活动持续性」两项的输入）。
     *
     * 备份理由见 [BkActivityDay]：它不像睡眠时长那样"过期即永失"，
     * 但已经攒下的历史不值得在换机后靠几十次跨进程聚合重新拉一遍。
     */
    val activityDays: List<BkActivityDay> = emptyList(),

    /**
     * 每天的心率/血氧每小时桶（2026-10-13 新增，DB v22）。
     *
     * ⚠ **默认 `emptyList()` 不能省**：老版本的备份文件里没有这个字段，
     *   没有默认值会让**旧备份整份读不进来**（用户换机/恢复当场失败）。
     */
    val dailyVitals: List<BkDailyVitals> = emptyList(),
) {
    /** 备份里的记录总数（波形与结论分开计数，因为它们是两类东西）。 */
    val totalCount: Int
        get() = heartRates.size + bloodPressures.size + spo2.size +
            ecgRecords.size + ecgWaves.size + temperatures.size + bodyCompositions.size +
            activities.size + ppgWaves.size + hrvSessions.size + cycleRecords.size +
            sleepOdi.size + mfBia.size + energyScores.size + apneaRecords.size +
                sleepNights.size + activityDays.size + dailyVitals.size
}

@Serializable
data class BkHeartRate(
    val bpm: Double,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 1,
)

@Serializable
data class BkBloodPressure(
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val note: String? = null,
    val recordedAt: Long,
    val source: String,
    val synced: Int = 1,
    /**
     * 测量姿势（`core/Bp.Posture`）。
     *
     * ⚠ 必须加在**末尾**：导出那一侧用的是**位置参数**，插在中间会让后面的实参全部错位
     *   （写进错的列，而且不报错）。
     * ⚠ 新增**可空字段不需要**升 [BACKUP_VERSION]（注释里那条规矩：缺字段走默认值），
     *   读方 `ignoreUnknownKeys = true` 也不怕字段变多。
     */
    val posture: String? = null,
)

@Serializable
data class BkSpo2(
    val percent: Int,
    val heartRate: Int? = null,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 1,
)

/**
 * 体温记录。两种口径（手表点检的绝对温度 / 三星健康的睡眠期值）走的是同一张表、同一个字段，
 * 靠 [source] 区分 —— 所以备份只需原样搬运，不做任何换算。
 *
 * [ambientC] 是环境温度（仅手表点检有），可能为空。
 */
@Serializable
data class BkTemperature(
    val celsius: Float,
    val ambientC: Float? = null,
    val measuredAt: Long,
    val source: String,
    val synced: Int = 1,
)

/**
 * 体成分记录。字段与 `BodyCompositionEntity` 一一对应（同样**不带 id**，去重靠唯一索引）。
 *
 * 除 [measuredAt]/[source] 外全部可空：测量失败时 SDK 只回状态码、没有数值，
 * 备份要能原样搬运这种"半条记录"，不能因为缺值就丢掉整行。
 *
 * 那四列资料快照（[weightKg]/[heightCm]/[gender]/[age]）必须一起备份 ——
 * 它们才是解读体脂率的依据，丢了资料就没法复核当时算出来的是什么。
 */
@Serializable
data class BkBodyComposition(
    val measuredAt: Long,
    val source: String,
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
    val synced: Int = 1,
)

/**
 * 活动片段。字段与 `ActivityEntity` 一一对应（同样**不带 id**，去重靠唯一索引）。
 *
 * 包络（[envelope]）必须一起备份：它是这条记录唯一的"形状"信息，
 * 只备份统计量会让活动记录退化成一行数字，连"中间动了一下"都看不出来。
 */
@Serializable
data class BkActivity(
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
    /** 降采样包络（g，逗号分隔）。 */
    val envelope: String? = null,
    val source: String,
    val synced: Int = 1,
)

/**
 * PPG 原始光信号会话。字段与 `PpgWaveEntity` 一一对应（同样**不带 id**，去重靠唯一索引）。
 *
 * 三段波形（[greenWave]/[redWave]/[irWave]）必须一起备份 —— 它们**就是**这条记录的本体。
 * 只备份 [estimatedHr]/[dcGreen] 这些摘要，等于把"给自研算法用的波形输入"丢了，
 * 那这本备份也就失去了这条记录的唯一价值。
 */
@Serializable
data class BkPpg(
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
    /** 归一化交流波形（DC 的倍数，6 位小数，逗号分隔）。 */
    val greenWave: String? = null,
    val redWave: String? = null,
    val irWave: String? = null,
    val source: String,
    val synced: Int = 1,
)

/**
 * HRV 记录（见 `HrvSessionEntity`）。
 *
 * **只备份输入（逐拍间期）不备份指标**：指标是 `Hrv.analyze` 的确定性派生量，
 * 恢复后现算即为正确值；存一份算好的指标反而会与恢复时的算法版本脱节。
 */
@Serializable
data class BkHrv(
    val measuredAt: Long,
    val durationMs: Long,
    val rrCount: Int,
    /** 逐拍间期（毫秒，逗号分隔，未经伪迹修正）。 */
    val rrMs: String,
    val refHr: Int? = null,
    /** 1=整齐、0=不齐、null=无法判定。 */
    val ecgRegular: Int? = null,
    val activityLevel: String? = null,
    val source: String,
    val synced: Int = 1,
)

/**
 * 经期记录（模块 #13）。字段与 `MenstrualRecordEntity` 一一对应（同样**不带 id**，
 * 去重靠唯一索引）。日期都是 epochDay（本地日期）。
 *
 * 只备份输入（开始/结束/流量/来源），不备份算好的周期长度/预测 ——
 * 那些由 `Cycle.analyze` 恢复后现算，与算法版本不脱节。
 */
@Serializable
data class BkCycle(
    val startEpochDay: Long,
    val endEpochDay: Long? = null,
    val flow: String? = null,
    val source: String,
)

/**
 * 一晚的氧减指数摘要（指标列全可空：null 表示"那晚没算出来"，与"算出 0 次"不同）。
 */
@Serializable
data class BkOdi(
    val dateEpochDay: Long,
    val startMs: Long,
    val endMs: Long,
    val events: Int? = null,
    val odiPerHour: Double? = null,
    val monitoredMinutes: Double? = null,
    val validPoints: Int? = null,
    val rejectedPoints: Int? = null,
    val lowest: Int? = null,
    val mean: Double? = null,
    val t90Pct: Double? = null,
    val reason: String? = null,
    val computedAt: Long = 0L,
)

/**
 * 一次多频阻抗测量的原始数据（四个频点的幅值/相位 + SDK 状态码）。
 * 相位不带符号约定（三星未公开），原样备份。
 */
@Serializable
data class BkMfBia(
    val measuredAt: Long,
    val m5: Float? = null,
    val p5: Float? = null,
    val m10: Float? = null,
    val p10: Float? = null,
    val m50: Float? = null,
    val p50: Float? = null,
    val m250: Float? = null,
    val p250: Float? = null,
    val status: Int? = null,
    val source: String,
    val synced: Int = 1,
)

@Serializable
data class BkEcgRecord(
    val classification: String,
    val heartRate: Int? = null,
    val note: String? = null,
    val recordedAt: Long,
    val source: String,
    val synced: Int = 1,
    /**
     * PAT 三列（v17）。含义见 `EcgRecordEntity.patMs` 的注释 —— **它不是血压**。
     *
     * ⚠ 加列必须同步进备份：`ecg_records` 本来就在备份里，
     * 漏掉这三列的话"导出→恢复"一圈之后 PAT 会**静默消失**（不报错、界面只是没数据）。
     */
    val patMs: Int? = null,
    val patSpreadMs: Int? = null,
    val patSeriesMs: String? = null,
    /** 为什么这次没算出 PAT（算出来时为 null）。含义见 `EcgRecordEntity.patNote`。 */
    val patNote: String? = null,
)

/**
 * 心电波形。字段与 `EcgWaveEntity` 一一对应，但**不带 id** ——
 * id 是各台设备本地的自增值，跨设备没有意义；去重完全靠 `(source, measuredAt)` 唯一索引。
 */
@Serializable
data class BkEcgWave(
    val measuredAt: Long,
    val durationMs: Long,
    val sampleCount: Int,
    val minMv: Float,
    val maxMv: Float,
    val avgMv: Float,
    /** 0.5~40Hz 带通后、按约 100Hz 降采样的波形（毫伏，逗号分隔），30 秒约 3000 点。 */
    val waveJson: String,
    val source: String,
    val classification: String? = null,
    val analyzedHr: Int? = null,
    val sampleRate: Int? = null,
    val regular: Int? = null,
    val beatCount: Int? = null,
    /** 拍模板相干性（0~2）。列名沿用历史命名，含义见 `EcgWaveEntity.bandRatio` 的注释。 */
    val bandRatio: Double? = null,
)

/**
 * 能量评分（三星健康算的每日准备度）。
 *
 * 与 ODI 摘要同理要备份：读取入口只取"最近 30 天"，**今天不备份、过些天就取不回那一天的数了**。
 */
@Serializable
data class BkEnergyScore(
    val dateEpochDay: Long,
    val score: Int,
    val measuredAt: Long,
)

/**
 * 睡眠呼吸暂停检测记录（来源是「三星健康监测器」）。
 *
 * `sign` 原样是字符串：我们**不认自己的枚举**，只当它是三星给的一个取值
 * （见 `Apnea.normalize` 的说明）。
 */
@Serializable
data class BkApnea(
    val startMs: Long,
    val endMs: Long? = null,
    val dateEpochDay: Long,
    val sign: String,
)

/**
 * 一晚睡眠统计（自研能量评分的输入）。
 *
 * 与 `BkEnergyScore` 同理要备份：读取入口只取"最近一晚"，
 * **今天不备份、过了今晚就取不回那一晚的睡眠时长**，而它正是评分里权重最大的那个分量。
 */
/**
 * 一天的活动量（能量评分里「前一日的活动」「活动持续性」两项的输入）。
 *
 * ## 与 `BkSleepNight` 的备份理由略有不同
 *
 * 睡眠时长是"**过期即永失**"（读取入口只给最近一晚），必须备份。
 * 活动量**理论上能回读** —— 三星的聚合算子接受任意时间区间（2026-09-24 实测
 * 昨天 6000 步、前天 3120 步都读得回来），所以严格说换机后能重建。
 *
 * 但仍然要备份，两条实际理由：
 * 1. 聚合能读的**只有"最近若干天"**（更早的要么被三星自己的保留策略清了、
 *    要么要逐天试才知道边界在哪），备份能把已经攒下的全部带过去；
 * 2. 不备份的话，换机后要重跑几十次跨进程聚合才能把趋势图重新拼出来 ——
 *    而趋势图是这张表存在的主要目的之一。
 *
 * @param slotSteps 6 段（每 4 小时）步数，逗号分隔。**原样搬运字符串**，
 *   不在备份层解析成数组再拼回来 —— 那一进一出会多出"格式非法怎么办"的分支，
 *   而这一层应该只负责搬字节。
 */
@Serializable
data class BkActivityDay(
    val dateEpochDay: Long,
    val steps: Long? = null,
    val slotSteps: String? = null,
    val activeKcal: Float? = null,
    val activeMs: Long? = null,
)

/**
 * 一天的**每小时心率/血氧桶**（`daily_vitals`，DB v22）。
 *
 * ⚠ **必须备份**：这张表的全部意义就是"数据留在本地、以后从本地读" ——
 *   不备份的话，换机之后历史又变回"只有最近 14 天（靠同步重新回填）"，
 *   而更早的那些天永远补不回来（三星那边读过一次就不再保证能给你）。
 */
@Serializable
data class BkDailyVitals(
    val dateEpochDay: Long,
    /** `hr` / `spo2`。⚠ 存储键，不是显示名。 */
    val kind: String,
    /** `h:min:max:avg` 用 `;` 隔开（见 `Vitals.encodeBuckets`）。 */
    val buckets: String,
    /** ⚠ 可空，理由见 `DailyVitalsEntity`：这一天"读过了但没有采样"时它们是 null。 */
    val minValue: Int? = null,
    val maxValue: Int? = null,
    val avgValue: Int? = null,
    val sampleCount: Int,
)

@Serializable
data class BkSleepNight(
    val dateEpochDay: Long,
    val startMs: Long,
    val endMs: Long,
    val actualSleepMs: Long,
    val hrAvg: Int? = null,
    val hrMin: Int? = null,
    /**
     * 睡眠期心率的标准差。
     *
     * **必须备份**：它同样只能从"读取那一刻还在手上的心率序列"算出来，
     * 而序列不落库、过了那晚就取不回来 —— 丢了它，「睡眠心率变化」这一项
     * 在换机后的历史里永远是空的（而 NULL 会被正确地当成"没有"，
     * 所以界面只会显示缺席，不会显示错值；但那一项就白做了）。
     */
    val hrSd: Double? = null,
    /**
     * 夜间皮肤温度原始值。同样**必须备份**：它也只能在读取那一刻拿到
     * （三星只在睡眠期采集，读取窗口是"昨天 18:00 → 现在"），
     * 过了那晚就取不回来 —— 而「睡眠期间皮肤温度」那一项正是用它算的。
     */
    val skinTemp: Float? = null,
    val spo2Avg: Int? = null,
    val spo2Min: Int? = null,
    val samsungScore: Int? = null,
    /**
     * 睡眠阶段段（编码见 [SleepStages]）。
     *
     * **必须备份**，理由与 [hrSd] / [skinTemp] **完全一样且更硬**：
     * 它同样只能从"读取那一刻还在手上的那一晚"拿到（窗口是"昨天 18:00 → 现在"），
     * 过了那晚就永远取不回来。不备份的话，用户换机后
     * **过去所有的睡眠阶段全部消失**，只剩一个总时长 ——
     * 而"深睡占比随时间怎么变"恰恰是这一列唯一的用途。
     */
    val stagesJson: String? = null,
)
