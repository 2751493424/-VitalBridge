package com.gemini.healthbridge.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Wear Data Layer 消息路径与能力声明。 */
object SyncPaths {
    const val CAPABILITY = "gemini_health_sync"
    const val HELLO = "/gemini/health/hello"
    const val DATA = "/gemini/health/data"
    const val ACK = "/gemini/health/ack"
    const val SYNC_REQUEST = "/gemini/health/sync_req"
    const val DONE = "/gemini/health/done"
    const val HOUR_STATS = "/gemini/health/hour_stats"
    const val SPO2_RECORDS = "/gemini/health/spo2_records"
    const val SLEEP_SUMMARY = "/gemini/health/sleep_summary"
}

/** 记录类型标识。 */
object RecordType {
    const val HR = "hr"

    /**
     * 多频生物电阻抗（手表 MF-BIA 测量）。只在 手表 → 手机 方向上跑。
     *
     * 与 [BIA] 是**两个不同的采集器**：BIA 给的是三星算好的体成分，MF-BIA 给的是
     * 5/10/50/250 kHz 四个频点的**原始阻抗幅值/相位**（见 `core/MfBia.kt`）。
     * 原始值只有手表能采到（手机侧的三星健康 Data SDK 不开放任何阻抗），
     * 所以这条链路是单向的、必须同步 —— 手机端的分析全靠它。
     */
    const val MF_BIA = "mf_bia"
    const val BP = "bp"
    const val ECG = "ecg"
    const val SPO2 = "spo2"

    /** 体温（手表腕部皮肤温度点检）。只在 手表 → 手机 方向上跑。 */
    const val TEMP = "temp"

    /**
     * 体成分（手表 BIA 测量）。只在 手表 → 手机 方向上跑 ——
     * 手机端读到的三星健康体成分是**另一个来源**，直接落库并标 `samsung_body`，
     * 不经过这条同步链路（否则会被去重键挡住，反而丢掉一条）。
     */
    const val BIA = "bia"

    /**
     * 活动片段（手表三轴加速度采集的统计结果）。只在 手表 → 手机 方向上跑。
     *
     * 三星健康没有第三方可读的"逐段活动记录"（只有步数与运动会话），
     * 所以手机端不会自己产生活动数据，这条链路是单向的。
     */
    const val ACTIVITY = "activity"

    /**
     * PPG 原始光信号（手表采集的一段光电容积脉搏波）。只在 手表 → 手机 方向上跑。
     *
     * 这条是**唯一**会随同步携带数百个浮点数的类型（一段 30 秒的会话，绿光约 750 点、
     * 三个通道合计约 2000 点，编码后约 20KB）。所以它与 ECG 一样，批大小被限制为 1
     * （见 `BluetoothSync.BATCH_SIZE`），否则一帧会把蓝牙缓冲撑爆。
     */
    const val PPG = "ppg"

    /**
     * HRV 分析的**输入**（一段心电的逐拍间期 + 测量条件）。只在 手表 → 手机 方向上跑。
     *
     * 注意这条记录里带的是**输入而不是指标**：RMSSD/SDNN/LF/HF 由 `Hrv.analyze` 现算，
     * 理由见 `HrvSessionEntity` 的注释（派生量落库会与算法版本脱节）。
     * 一条记录约 35 个整数，比 PPG 小三个数量级，所以**不需要**单条一批。
     */
    const val HRV = "hrv"

    val ALL = listOf(HR, BP, ECG, SPO2, TEMP, BIA, ACTIVITY, PPG, HRV)
}

/**
 * HRV 记录的来源标记。
 *
 * **两种来源，两套基线，不许混。**
 *
 * `WATCH_ECG` 是心电的 R 峰间期（真正的逐拍间期）；`WATCH_PPG` 是 PPG 的逐拍间期
 * （三星传感器 SDK 的 `ValueKey.HeartRateSet.IBI_LIST`，2026-09-24 接入）。
 * 后者受脉搏波传导与形态影响，**与 RR 不是同一个量** ——
 * 所以来源必须入库、必须随记录同步、界面必须能区分，而且
 * **算基线时必须按来源过滤**（`hrvBaseline` / `StressSummary`），
 * 否则两边会互相污染，且**不会报错**。
 */
object HrvSource {
    /** 手表心电的 R 峰间期。真正的逐拍间期。 */
    const val WATCH_ECG = "watch_ecg"

    /** 手表 PPG 的逐拍间期（`IBI_LIST`，约 2 分钟手动会话）。**另一套基线。** */
    const val WATCH_PPG = "watch_ppg"
}

/** 体温记录的来源标记，两端共用，避免字符串散落各处写错。 */
object TempSource {
    /** 手表 `SKIN_TEMPERATURE_ON_DEMAND` 点检的绝对腕部皮肤温度。 */
    const val WATCH = "watch_temp"

    /** 三星健康睡眠期的皮肤温度（口径可能是相对基线的偏差，见 `Temperature.scaleOf`）。 */
    const val SAMSUNG_SKIN = "samsung_skin"
}

/**
 * 手表采集数据的来源标记，两端共用。
 *
 * ## ⚠ 为什么要有这个对象（2026-09-24 补）
 *
 * 在此之前，手机端落库时用的是 `SyncMessage.deviceId`，而那是**手表型号**
 * （`Build.MODEL`，例如 `SM-R900`）—— 也就是把「设备标识」当成了「来源」。
 * 两者是**不同的东西**：来源要说的是「这条数据从哪来」（手表 / 三星 / 导入），
 * 而型号是设备身份（我们只配对一只表，型号没有区分作用）。
 *
 * 那个做法的代价在 2026-09-24 做数据回填时才暴露出来：回填要判断
 * 「这条记录是不是我们自己产生的」，判据本来可以是干净的白名单
 * （`source` 以 `watch_` 开头），但因为手机端库里存的是型号，
 * 白名单**一条都认不出来**，整个回填等于没做（界面上还只会显示「0 条」）。
 * 现在的判据因此退化成了排除法，见 `Backfill.isOurs` 里那段代价说明。
 *
 * 所以：**新数据一律写这里的规范值**。型号不再进 `source`。
 * 历史记录里的型号形态由 `Backfill.isOurs` / `Backfill.normalizeSource` 兼容。
 */
object DeviceSource {
    /** 手表连续/点检采集的心率（`HEART_RATE`、`HEART_RATE_CONTINUOUS`）。 */
    const val HR = "watch_hsm"

    /** 手表 `SPO2_ON_DEMAND` 点检的血氧。 */
    const val O2 = "watch_o2"

    /**
     * **手动录入**的记录（手机端或手表端输入）：血压、心电结论。
     *
     * 与采集的区别是有意义的：录入的值没有经过传感器，回填时平台侧也应记为
     * `manualEntry`（我们写 HC 用的 Metadata 就是 manualEntry）。
     */
    const val MANUAL = "watch_manual"
}

/**
 * 同步条目：各类记录共用的扁平结构，字段按类型可空。
 * recordedAt 为记录产生时刻的 epoch 毫秒，是两端去重键。
 *
 * ECG 扩展字段（type = "ecg"）：`RecordType.ECG` 的一批里**最多只有一条**带 [wave]
 * （约 16KB 文本），因此 ECG 的批大小固定为 1，见 `BluetoothSync.BATCH_SIZE`。
 * Health Connect 没有 ECG/心律记录类型，所以波形只能在两端本地留存（见 README）。
 */
@Serializable
data class HealthItemDto(
    val recordedAt: Long,
    val bpm: Double? = null,
    val systolic: Int? = null,
    val diastolic: Int? = null,
    val pulse: Int? = null,
    /**
     * 测量体位（`core/Bp.Posture`）。**仅 BP 使用**。
     *
     * ⚠ 字段**只增不改、必须带默认值**：手表可能缓存着旧版本发出去的 JSON，
     *   升级后要用新代码解析它。`null` = 老数据 / 用户没选，**不等于坐姿**。
     */
    val posture: String? = null,
    val spo2: Int? = null,
    val classification: String? = null,
    val note: String? = null,
    // ── 以下仅 ECG 使用 ──
    /** 测量时长（毫秒）。 */
    val durationMs: Long? = null,
    /** 实测采样率（Hz）。 */
    val sampleRate: Int? = null,
    /** 节律是否整齐。 */
    val regular: Boolean? = null,
    /** 检出的 R 峰（心跳）个数。 */
    val beatCount: Int? = null,
    /** 拍模板相干性（0~2，>=0.70 视为电极接触良好）。字段名沿用历史命名，旧语义是频带能量占比。 */
    val bandRatio: Double? = null,
    /** 0.5~40Hz 带通并降采样到约 100Hz 的波形（毫伏，逗号分隔）。 */
    val wave: String? = null,
    // ── 以下仅 ECG 且仅模块 #14（PAT）使用 ──
    /**
     * 脉搏到达时间的中位数（毫秒）。⚠ **不是血压** —— 理由见 `core/Ptt.kt` 的类注释。
     * 字段全部带默认值：手表缓存着旧版本发出去的 JSON，升级后要用新代码解析它。
     */
    val patMs: Int? = null,
    /** 逐拍 PAT 的离散度（毫秒）。 */
    val patSpreadMs: Int? = null,
    /** 逐拍 PAT 序列（毫秒，逗号分隔整数）。 */
    val patSeriesMs: String? = null,
    /** 为什么这次没算出 PAT（算出来时为 null）。手机端据此说明原因，不留空白。 */
    val patNote: String? = null,
    /**
     * 这个 `patMs` 是哪一套口径算的（`Ptt.ALGO_VERSION`）。
     *
     * ⚠ 必须随记录走：手机端的"自身基线"只收同口径的值，否则基线是两个口径的混合
     * （与 `HrvDto.source` 同一条纪律）。老记录没有这个字段 → 解析为 null → 不进基线。
     */
    val patAlgo: Int? = null,
    // ── 以下仅体温使用 ──
    /**
     * 摄氏度。含义随来源变：手表点检是**绝对**腕部皮肤温度，
     * 三星健康那份可能是**相对基线偏差**；口径判定见 `Temperature.scaleOf`。
     * 刻意用 Float 而不是 Int：体温的有效信息就在 0.1 ℃ 上，取整会把趋势抹平。
     */
    val tempC: Float? = null,
    /** 环境温度（℃），手表点检时三星传感器会一并给出。 */
    val ambientC: Float? = null,
    // ── 以下仅体成分使用 ──
    /**
     * BIA 测量结果。**刻意做成一个可空的子对象**，而不是在 [HealthItemDto] 上平铺十几列。
     *
     * 原因：`FrameCodec` 的 Json 配的是 `encodeDefaults = true`，默认值也会被序列化 ——
     * 平铺就意味着**每一条心率/血压/血氧记录**的 JSON 里都会多出十几个 `null` 字段。
     * 一条记录十几字节 × 一次同步上百条，白占蓝牙带宽。包成子对象后，
     * 非体成分的记录只多**一个** `"bia":null`。
     */
    val bia: BiaResultDto? = null,
    // ── 以下仅活动片段使用 ──
    /**
     * 一段加速度采集的统计结果。与 [bia] 同样**包成子对象**而不是平铺十几列：
     * `FrameCodec` 的 Json 配了 `encodeDefaults = true`，平铺会让每一条心率/血压记录
     * 都多带十几个 `null` 字段。包成子对象后非活动记录只多一个 `"activity":null`。
     */
    val activity: ActivityDto? = null,
    // ── 以下仅 PPG 原始光信号使用 ──
    /**
     * 一段 PPG 会话的波形与摘要。与 [bia]/[activity] 同样**包成子对象**而不是平铺十几列：
     * `FrameCodec` 的 Json 配了 `encodeDefaults = true`，平铺会让每一条心率/血压记录
     * 都多带十几个 `null` 字段。包成子对象后非 PPG 记录只多一个 `"ppg":null`。
     */
    val ppg: PpgDto? = null,
    // ── 以下仅 HRV 使用 ──
    /**
     * 一段心电的逐拍间期与测量条件。与 [bia]/[activity]/[ppg] 同样**包成子对象**而不是平铺：
     * `FrameCodec` 的 Json 配了 `encodeDefaults = true`，平铺会让每一条心率/血压记录
     * 都多带几个 `null` 字段。包成子对象后非 HRV 记录只多一个 `"hrv":null`。
     */
    val hrv: HrvDto? = null,
    // ── 以下仅多频阻抗使用 ──
    /**
     * 一次多频阻抗测量（四个频点的原始幅值/相位）。
     *
     * 与 [bia]/[activity]/[ppg]/[hrv] 同样**包成子对象**：`FrameCodec` 的 Json 配了
     * `encodeDefaults = true`，平铺会让每一条心率/血压记录的 JSON 都多带八个 `null`。
     */
    val mfBia: MfBiaDto? = null,
)

/**
 * 一次多频阻抗测量的原始数据（见 `MfBiaEntity` / `core/MfBia.kt`）。
 *
 * 全部可空：**给不出就是 null，绝不用 0 顶替** —— 0 Ω 在电学上是"短路"，
 * 不是一个缺失值，混淆两者会让下游把一次接触不良算成一条真实曲线。
 *
 * 相位字段**不带符号约定**（三星没有公开），所以这里只搬运、不解释。
 * 派生量（比值、趋势）**刻意不放进 DTO**：它们是 `MfBia.analyze` 从这八个原始值现算的，
 * 传过去只会多一份可能与源不一致的副本（与"派生量一律不落库"同一条纪律）。
 */
@Serializable
data class MfBiaDto(
    val m5: Float? = null,
    val p5: Float? = null,
    val m10: Float? = null,
    val p10: Float? = null,
    val m50: Float? = null,
    val p50: Float? = null,
    val m250: Float? = null,
    val p250: Float? = null,
    /** SDK 状态码（0 = 成功）。原样记录，语义不解释。 */
    val status: Int? = null,
)

/**
 * 一次 HRV 分析的输入（见 `HrvSessionEntity`）。
 *
 * **这里刻意没有指标字段**：RMSSD/SDNN/LF/HF 都是 [Hrv.analyze] 的确定性派生量，
 * 两端各算一次（同一份 core 代码）比传一份可能过期的算好的值更可靠 ——
 * 算法一改，历史记录的口径跟着一起改，不会出现"新老口径混在一张表里"。
 */
@Serializable
data class HrvDto(
    /** 这次心电记录的时长（毫秒）。 */
    val durationMs: Long,
    /** [rrMs] 里的间期个数。 */
    val rrCount: Int,
    /** 逐拍间期（毫秒，逗号分隔，**未做伪迹修正**，让接收端按自己的版本重跑）。 */
    val rrMs: String,
    /** 同一次心电给出的心率（次/分），用于交叉核对。 */
    val refHr: Int? = null,
    /** 心电的节律结论：1=整齐、0=不齐、null=无法判定。0 会让分析直接拒答。 */
    val ecgRegular: Int? = null,
    /** 测量时的活动档位（`Activity.Level`）；null = 拿不到，**不等于静息**。 */
    val activityLevel: String? = null,
    /**
     * 这条记录**是什么测出来的**（[HrvSource]）。
     *
     * ⚠ 带默认值是为了兼容：手表端可能还是旧版本、不含这个字段。
     * 默认成 [HrvSource.WATCH_ECG] 是因为在 2026-09-24 之前**只有那一种来源** ——
     * 旧包推来的记录本来就都是心电的。
     *
     * 手机端**必须**用它落库（而不是像以前那样写死 `WATCH_ECG`）：
     * 写死会把 PPG 会话记成心电会话，两条基线就此混在一起。
     */
    val source: String = HrvSource.WATCH_ECG,
)

/**
 * 一段 PPG 会话的波形与摘要（见 `PpgWaveEntity` 的说明）。
 *
 * 波形是**归一化**后的交流分量（`(x - dc) / dc`，无量纲），不是 SDK 给的原始计数 ——
 * 原始计数没有物理单位且跨会话不可比，理由见 `Ppg` 的类注释。
 */
@Serializable
data class PpgDto(
    /** 首个样本到最后一个样本的时间跨度（毫秒）。 */
    val durationMs: Long,
    /** 存入 [greenWave] 的有效点数。 */
    val sampleCount: Int,
    /** 估算采样率（Hz），null = 没估出来。 */
    val sampleRateHz: Int? = null,
    /** 实际采到的通道令牌，如 `"green,red,ir"`。 */
    val channels: String,
    /** 绿光直流电平（原始计数的中位数），用于从归一化波形反推原始值。 */
    val dcGreen: Float? = null,
    /** 绿光交流峰峰值（原始计数，p95-p5）。 */
    val acGreenP2P: Float? = null,
    /** 数据完整度（0~1），有效样本占比。 */
    val quality: Float? = null,
    /** 由波形独立估算的脉率（次/分），null = 门限未过。 */
    val estimatedHr: Int? = null,
    /** 同一次测量的参考心率，用于对照。 */
    val refHr: Int? = null,
    /** 本会话出现过的通道状态码（原样记录，不解释）。 */
    val statusSeen: String? = null,
    /** 绿光归一化交流波形（DC 的倍数，6 位小数，逗号分隔）。 */
    val greenWave: String? = null,
    val redWave: String? = null,
    val irWave: String? = null,
)

/**
 * 一段活动片段的统计结果（见 `ActivityEntity` 的说明）。
 *
 * 单位一律 g（已按本段模长中位数归一化，输入单位不确定时也能共用同一套阈值）。
 */
@Serializable
data class ActivityDto(
    /** 采集时长（毫秒）。 */
    val durationMs: Long,
    /** 参与统计的样本数。 */
    val sampleCount: Int,
    /** 活动档位，取值见 `Activity.Level`。 */
    val level: String,
    val meanG: Float,
    /** 模长标准差（g），分级主判据。 */
    val stdG: Float,
    val p95G: Float,
    /** 静止样本占比（0~1）。 */
    val stillRatio: Float,
    val peakJerkG: Float,
    val meanXG: Float? = null,
    val meanYG: Float? = null,
    val meanZG: Float? = null,
    /** 降采样包络（g，逗号分隔），仅用于画图。 */
    val envelope: String? = null,
)

/**
 * 一次 BIA 测量的全部结果。
 *
 * 全部字段可空：测量失败时（见 `BodyComposition.statusOf`）SDK 只回状态码、不给数值，
 * 而"没有值"必须能与"值是 0"区分开。
 *
 * 单位全部照抄三星官方文档：体水是**升**、相角是**负的度**、BMR 是 kcal。
 */
@Serializable
data class BiaResultDto(
    /** 体脂率（%），0–100。 */
    val bodyFatRatio: Float? = null,
    /** 体脂量（kg），2 ~ 体重-2。 */
    val bodyFatMass: Float? = null,
    /** 总体水（**升**）。 */
    val totalBodyWater: Float? = null,
    /** 骨骼肌率（%），0–100。 */
    val skeletalMuscleRatio: Float? = null,
    /** 骨骼肌量（kg）。 */
    val skeletalMuscleMass: Float? = null,
    /** 基础代谢率（kcal），0–12000。 */
    val basalMetabolicRate: Float? = null,
    /** 去脂体重率（%），0–100。 */
    val fatFreeRatio: Float? = null,
    /** 去脂体重（kg）。 */
    val fatFreeMass: Float? = null,
    /** 身体阻抗模值（Ω），0–9999。SDK 1.3.0 起提供。 */
    val impedanceMagnitude: Float? = null,
    /** 身体阻抗相角（°），**取值 -90 ~ 0**。 */
    val impedanceDegree: Float? = null,
    /** 测量时实际使用的资料快照（见 `BodyCompositionEntity` 的说明）。 */
    val weightKg: Float? = null,
    val heightCm: Float? = null,
    /** SDK 的性别取值：0=女 1=男。 */
    val gender: Int? = null,
    val age: Int? = null,
)

/** 手表 -> 手机 的一批记录。upTo 为本批中最大的 recordedAt，手机确认后手表据此标记已同步。 */
@Serializable
data class SyncMessage(
    val type: String,
    val deviceId: String,
    val items: List<HealthItemDto>,
    val upTo: Long,
)

/** 手表 -> 手机 上线问候，携带各类型待同步数量。 */
@Serializable
data class HelloMessage(
    val deviceId: String,
    val deviceName: String,
    val pendingHr: Int = 0,
    val pendingBp: Int = 0,
    val pendingEcg: Int = 0,
    val pendingSpo2: Int = 0,
    val pendingTemp: Int = 0,
    val pendingBia: Int = 0,
    val pendingActivity: Int = 0,
    /** 待同步的 PPG 原始光信号会话数。一条就是一段波形，不是点。 */
    val pendingPpg: Int = 0,
    /** 待同步的 HRV 记录数。一条对应一次心电（逐拍间期序列）。 */
    val pendingHrv: Int = 0,
    /** 待同步的多频阻抗记录数。一条对应一次测量（四个频点）。 */
    val pendingMf: Int = 0,

    /**
     * 手表 App 的 `versionCode`（2026-10-21 加）。
     *
     * 用途：手机端据此判断「手表是不是最新」，从而决定要不要弹手表更新、要不要通知手表开接收窗口。
     * ⚠ **必须带默认值 0**：老版本手表不发这个字段，反序列化不能失败（0 表示"不知道"）。
     */
    val watchVersion: Long = 0,
)

/** 手机 -> 手表 对某批数据的确认。 */
@Serializable
data class AckMessage(
    val type: String,
    val upTo: Long,
    val inserted: Int,
)

/** 蓝牙直连通道（不依赖 Google 服务的备用传输层）。 */
object BtLink {
    const val UUID_STRING = "7d4a09f2-53b1-4c8e-a5d6-2f1b0c9e8a71"
}

/** 蓝牙链路上的消息帧：kind 区分类型，data 为对应消息的 JSON。 */
@Serializable
data class Frame(val kind: String, val data: String)

object FrameKinds {
    const val HELLO = "hello"
    const val DATA = "data"
    const val ACK = "ack"
    const val SYNC_REQ = "sync_req"
    const val DONE = "done"
    const val HOUR_STATS = "hour_stats"
    const val SPO2_RECORDS = "spo2_records"
    const val ECG_WAVE = "ecg_wave"

    // ⚠ 2026-10-26 `OTA_WINDOW` 已删（连同手机端 `sendOtaWindow`）：
    //   它唯一的用途是让手表**临时开 RFCOMM 监听**来收手机推的安装包。
    //   那条链路已整体移除 —— 首次安装走「安装」页的无线 ADB，更新靠表自己下载。


    /**
     * 手机 -> 手表：`ECG_WAVE` 的**落盘回执**（2026-10-05 加）。
     *
     * ## 为什么需要它
     *
     * `ECG_WAVE` 的落盘发生在**手机端**（手表只负责发），所以手表在发完之后
     * **只知道"发出去了"，不知道"存下来了没有"** —— 用户的原话是
     * 「每次点了都不知道导出成功没有」。
     * ⇒ 没有回执时，手表能给出的最好信息就是"已发送"，那既不是成功也不是失败。
     *
     * ## 载荷
     *
     * - 成功：**落盘后的完整路径**（`Download/VitalBridge/CSV/<文件名>`）——
     *   直接把它显示给用户，比让手表再拼一次路径更可靠（拼法只有手机端一份）。
     * - 失败：**空串**（`""`）。
     *
     * ⚠ 不要往这里塞更多语义（比如"点数不符"）：回执只回答一个问题
     *   「这个文件**在不在**」，多塞内容会让手表端的展示逻辑跟着长。
     */
    const val ECG_WAVE_ACK = "ecg_wave_ack"

    /**
     * 手机 -> 手表：**让手表自己去检查并安装更新**（2026-10-28）。
     *
     * ## 为什么是"指令"而不是"推包"
     * 手机在蓝牙上只能比对版本号，**包还是表自己从 WiFi 下**（`WatchOtaClient.updateFromWifi`）：
     * 推 4.6MB 过蓝牙既慢又容易断，而且 2026-10-26 那条推包链路已经整块删除。
     * 手机在这里只做一件事：**发现版本差异 → 发一条空载荷指令**。
     *
     * ## 前置条件（用户已知并接受）
     * 手表上必须**开着脉迹**：蓝牙连接本身建立在手表 App 的同步服务上，
     * 表没开 ⇒ 手机 `sendFrame` 直接失败，界面会如实说「手表未连接」。
     *
     * ⚠ 载荷为空串。**不要**往里塞 URL/版本号 —— 手表自己会读 `apk/watch.json`，
     *   两边各存一份"该装哪一版"必然出现分歧（这个项目已经为此返工过一次）。
     */
    const val WATCH_UPDATE_REQ = "watch_update_req"

    /**
     * 手表 -> 手机：[WATCH_UPDATE_REQ] 的回执，载荷 `状态|文本`。
     *
     * - `start|` 已开始（下载/安装进行中，进度在手表屏幕上）
     * - `busy|` 手表上已经有一个更新任务在跑（互斥，见 `WatchUpdateRunner.running`）
     * - `ok|<结果文本>` 结束（成功与否看文本，可能含失败原因）
     *
     * ⚠ 文本是**手表端已经拼好的人话**，手机原样显示即可；不要在这里做解析判断。
     */
    const val WATCH_UPDATE_ACK = "watch_update_ack"
    const val SLEEP_SUMMARY = "sleep_summary"

    /**
     * 手机 -> 手表：BIA 测量所需的用户资料（身高/体重/性别/年龄）。
     *
     * 为什么资料放在手机侧：手表上没有三星健康 Data SDK，拿不到用户档案；
     * 而手机端能从三星健康的 `USER_PROFILE` 直接读出来，不必让用户在手表上
     * 用旋转表冠一个数字一个数字地输入身高体重。
     */
    const val BIA_PROFILE = "bia_profile"

    /**
     * 手机 -> 手表：经期追踪的一行摘要（模块 #13）。
     *
     * 经期数据只在手机端录入/导入（没有传感器能测），手表端没有三星健康 Data SDK、
     * 也不做录入交互，所以手机算好摘要后经蓝牙推一个紧凑的概览过去，
     * 手表只负责显示一行（第 N 天 / 距下次 / 是否易孕）。
     */
    const val CYCLE_SUMMARY = "cycle_summary"

    /**
     * 手机 -> 手表：压力 / 疲劳评分的一行摘要（模块 #12）。
     *
     * ## 为什么评分由**手机算好再推**，而不是手表自己算
     * 分数是"相对这个人自己的基线"，而基线要用到**全部**历史记录 —— 手机端才有完整的一份
     * （手表只知道自己测的那些）。两端各算各的就会出现同一个人在两块屏幕上看到两个分数，
     * 而分数本身又没有绝对标尺可对照，用户没有任何办法判断哪个是对的。
     * 推结果而不是推输入，是让"只有一个分数"成立的最直接办法。
     *
     * ## 代价：推过去的是**会过期**的
     * 基线每多一条记录就变一次，所以这份摘要只在"手机端最后一次计算"的那一刻成立。
     * 帧里带 [StressSummaryMessage.computedAt]，手表据此显示"算于多久以前"，
     * 超过 [Stress.SUMMARY_FRESH_MS] 就明说可能过时 —— 与其偷偷挂一个旧分数，不如标出来。
     */
    const val STRESS_SUMMARY = "stress_summary"

    /**
     * 手机 -> 手表：一晚的氧减指数摘要（模块 #11 的替代落点）。
     *
     * ## 为什么这一条也必须由手机推
     * ODI 的输入是**三星健康睡眠期间的分钟级血氧序列**，而手表上没有三星健康 Data SDK
     * （Wear OS 侧不提供），那份序列只有手机读得到 —— 手表**没有原始数据可算**，
     * 与呼吸率（两端都能从本地波形算）不同。所以这里推的是结果，不是输入。
     *
     * ## 与压力摘要的差别：它不会"越放越不准"
     * 压力分是相对"不断增长的基线"算的，放久了就代表不了现在；而 ODI 是**一晚一份的既成事实** ——
     * 那一晚的事件数不会因为后来多记录了几晚而改变。所以这里不做"时效衰减"的标注，
     * 而是靠 [OdiSummaryMessage.dateEpochDay] 让手表如实标出**这是哪一晚**：
     * 当天的显示"昨晚"，更早的直接显示日期。宁可让用户看到"9月14日"，
     * 也不要让一个三天前的数看起来像今天早上的。
     */
    const val ODI_SUMMARY = "odi_summary"

    /** 手机 -> 手表：能量评分一行摘要（三星健康算的每日身心准备度）。 */
    const val ENERGY_SUMMARY = "energy_summary"

    /**
     * 手机 -> 手表：三星**扩展指标**摘要（活动 / 饮食 / 血糖 / 心律不齐）。
     * 四块合一帧，理由见 [ExtrasSummaryMessage] 的类注释。
     */
    const val EXTRAS_SUMMARY = "extras_summary"

    /**
     * 手机 -> 手表：睡眠呼吸暂停摘要（三星健康监测器的筛查信号）。
     *
     * ⚠ 与其它摘要**相反**：没有记录时**不推**这一帧。它是"某一晚的既成事实"，
     * 手表上已有一份是有效的，推空会把它抹掉（与 `odi_summary` 完全同一条纪律）。
     */
    const val APNEA_SUMMARY = "apnea_summary"
}

/** 每小时心率聚合桶。 */
@Serializable
data class HourBucket(val hour: Int, val min: Int, val max: Int, val avg: Int)

/** 手机推送给手表的三星健康当日小时统计（dateEpochDay 用于过期校验；sleepAvg 为昨晚睡眠时段平均心率）。 */
@Serializable
data class HourStatsMessage(
    val buckets: List<HourBucket>,
    val dateEpochDay: Long,
    val sleepAvg: Int? = null,
)

/** 单条血氧记录（时间戳 + 血氧%）。 */
@Serializable
data class Spo2RecordDto(
    val t: Long,
    val pct: Int,
)

/** 手机推送给手表的三星健康血氧记录列表。 */
@Serializable
data class Spo2RecordsMessage(
    val records: List<Spo2RecordDto>,
)

// ───────────────────────── 睡眠 ─────────────────────────

/**
 * 睡眠阶段标识。取值与三星健康 Data SDK 的 `SleepType.StageType` 一一对应，
 * 但**只保留字符串名**：手机侧读 SDK 枚举、手表侧不依赖 SDK 也能解析。
 */
object SleepStageKind {
    const val AWAKE = "awake"

    /** 浅睡（SDK 的 LIGHT）。三星界面叫"浅睡"。 */
    const val LIGHT = "light"
    const val DEEP = "deep"
    const val REM = "rem"

    /** 未分类（SDK 的 UNDEFINED），既不进占比统计也不画色块。 */
    const val UNKNOWN = "unknown"
}

/**
 * 一段连续的同类型睡眠阶段（epoch 毫秒）。
 *
 * 刻意在手机侧**先合并再传输**：一晚的原始阶段记录有上百条，逐条画会变成锯齿、
 * 逐条列表也读不完；合并成连续段后通常只剩十几段，既能画阶梯图也能逐段列出时间段。
 */
@Serializable
data class SleepStageDto(
    val startMs: Long,
    val endMs: Long,
    val kind: String,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

/**
 * 一晚睡眠的完整摘要：会话区间 + 阶段段 + 睡眠期心率/血氧统计。
 *
 * 所有统计字段都可空 —— 三星健康里可能只有睡眠而没开血氧/心率连续监测，
 * 界面必须能区分"没有这项数据"与"这项数据是 0"。
 */
@Serializable
data class SleepNightDto(
    val startMs: Long,
    val endMs: Long,
    /** 三星健康睡眠得分；部分机型/版本不提供。 */
    val score: Int? = null,
    val stages: List<SleepStageDto> = emptyList(),
    val spo2Avg: Int? = null,
    val spo2Min: Int? = null,
    val hrAvg: Int? = null,
    val hrMin: Int? = null,
    val hrMax: Int? = null,
) {
    val durationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)

    /** 各阶段累计时长（毫秒），键为 [SleepStageKind] 取值。 */
    fun stageTotals(): Map<String, Long> {
        val out = LinkedHashMap<String, Long>()
        for (s in stages) {
            if (s.kind == SleepStageKind.UNKNOWN) continue
            out[s.kind] = (out[s.kind] ?: 0L) + s.durationMs
        }
        return out
    }

    /** 实际睡眠时长 = 总时长 - 清醒段（三星健康的"实际睡眠时间"口径）。 */
    fun actualSleepMs(): Long {
        val awake = stageTotals()[SleepStageKind.AWAKE] ?: 0L
        val total = stageTotals().values.sum()
        return if (total > 0L) (total - awake).coerceAtLeast(0L) else durationMs
    }
}

/** 手机推送给手表的睡眠摘要（dateEpochDay 用于过期校验，避免第二天还显示前晚）。 */
@Serializable
data class SleepSummaryMessage(
    val dateEpochDay: Long,
    val night: SleepNightDto? = null,
)

/**
 * BIA 测量所需的用户资料。字段与传感器 SDK 的 `TrackerUserProfile` 一一对应：
 * 身高 cm、体重 kg、性别（0=女 1=男）、年龄。
 *
 * 取值范围见 `BodyComposition.PROFILE_*`（20–300 / 2–500 / 0–200），
 * 两端都在写入前校验，避免把不合法资料送到 SDK 后换来一个状态码 18。
 */
@Serializable
data class BiaProfileDto(
    val heightCm: Float,
    val weightKg: Float,
    val gender: Int,
    val age: Int,
) {
    /** 是否在 SDK 允许的取值范围内。手表端收到后**先校验再用**。 */
    fun isValid(): Boolean =
        BodyComposition.profileComplete(heightCm, weightKg, gender, age)
}

/**
 * 手机推送给手表的资料帧。
 *
 * `profile` 为 null 表示"手机端也没有资料"——手表据此显示"请先在手机端同步资料"，
 * 而不是拿一份编出来的默认资料去测（那会得到一条看起来很正常的错误结果）。
 */
@Serializable
data class BiaProfileMessage(
    val profile: BiaProfileDto? = null,
)

/**
 * 手机推送给手表的经期一行摘要（模块 #13）。
 *
 * `available` 为 false 时其余字段都无意义：手表据此显示"未开启/无数据"，
 * 而不是拿一份编出来的周期出来 —— 与 [BiaProfileMessage.profile] 为 null 同一纪律。
 *
 * 所有日期都是 epochDay（本地日期），由手表侧用自己的"今天"重算第几天，
 * 所以这里**不推 cycleDay**，只推"最近一次开始日 + 预测日 + 半宽"，手表现算。
 */
@Serializable
data class CycleSummaryMessage(
    /** 手机端经期追踪是否已开启且有有效数据。false = 手表显示"未开启"。 */
    val available: Boolean = false,
    /** 最近一次经期开始日（epochDay）。available 时非空。 */
    val lastStartEpochDay: Long? = null,
    /** 预测的下次经期开始日（epochDay）。数据不足时为 null。 */
    val nextStartEpochDay: Long? = null,
    /** 预测区间半宽（天）。 */
    val spreadDays: Int = 0,
    /** 是否处在易孕窗口内。 */
    val inFertileWindow: Boolean? = null,
    /** 平均周期长度（天）。 */
    val medianCycleDays: Int = 0,
)

/**
 * 手机推送给手表的压力 / 疲劳评分摘要（模块 #12）。
 *
 * 只推**结果**（分数与分级）和它的时效，不推基线明细：手表的圆屏只放得下一行，
 * 明细本来也在手机端看。 `available == false` 时其余字段都无意义，
 * 手表显示 [reason] —— 与 [CycleSummaryMessage] 同一条纪律：
 * "算不出来"是一个必须被传递的状态，不能让手表一直挂着上一次的旧分数。
 *
 * @param computedAt 这份摘要算出来的时刻（epoch 毫秒），手表据此显示时效。
 * @param measuredAt 它对应的是哪一次测量（epoch 毫秒）。
 * @param glanceText 手机上算好的一行文本（手机端复用，手表端另拼，故不是必填）。
 */
@Serializable
data class StressSummaryMessage(
    val available: Boolean = false,
    val computedAt: Long = 0L,
    val measuredAt: Long = 0L,
    /** 压力分（0~100）。available 时有效。 */
    val stressScore: Int = 0,
    val stressLevel: String = "",
    /** 疲劳分。null = 趋势数据不足（不是"疲劳很低"）。 */
    val fatigueScore: Int? = null,
    val fatigueLevel: String? = null,
    /** available = false 时给手表显示的原因（基线不足 / 本次不适用 …）。 */
    val reason: String? = null,
    /** 基线里有多少条记录参与 —— 手表上要能看出"这个分数基于几次测量"。 */
    val baselineCount: Int = 0,
)

/**
 * 手机推送给手表的一晚氧减指数摘要（模块 #11）。
 *
 * 只推**结果**与"它属于哪一晚"，不推血氧序列：手表的圆屏放不下一整晚的曲线，
 * 而原始序列本来就该在手机端看（项目设计说明里说明了为什么那份序列不落库）。
 *
 * `available == false` 有两种截然不同的来路，**必须分开**：
 *  · [reason] 非空 —— 手机端算了但算不出来（监测时长不够 / 那一晚没有血氧）；
 *  · 手机端根本没有这一晚的记录 —— 这时**不推**（见 `PhoneBtServer.pushOdiSummary`），
 *    手表保留上一次的摘要并按 [dateEpochDay] 标明日期，而不是被清成"无数据"。
 *
 * @param dateEpochDay 归属日（醒来那天），手表据此决定显示"昨晚"还是具体日期。
 * @param computedAt 手机端算出它的时刻（epoch 毫秒）。ODI 不会因为后来多记录几晚而失真，
 *   所以它只用于排查，不用于时效衰减。
 */
@Serializable
data class OdiSummaryMessage(
    /** 有没有可显示的结论。false 时看 [reason]（为空则整条不该被推过来）。 */
    val available: Boolean = false,
    /** 归属日（epochDay，醒来那天）。 */
    val dateEpochDay: Long = 0L,
    /** 这一晚睡眠会话的起止（epoch 毫秒），手表上显示"22:58–06:41"这类区间。 */
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    /** ODI（次/小时）。available 时有效。 */
    val odiPerHour: Double = 0.0,
    /** 氧减事件次数。 */
    val events: Int = 0,
    /** 分母：实际监测到的分钟数。 */
    val monitoredMinutes: Double = 0.0,
    /** T90：血氧 <90% 的时间占比（0~100）。 */
    val t90Pct: Double = 0.0,
    /** 参考分级令牌（见 `Odi.Band`）。手表用 `Odi.bandLabel` 翻译。 */
    val band: String = "",
    /** available = false 时给手表显示的原因。 */
    val reason: String? = null,
    val computedAt: Long = 0L,
)

/**
 * 手机 -> 手表：能量评分摘要 —— **同时装两个分**：三星自己的，与本 App 自算的。
 *
 * 两个分并排展示、互不替代（口径差别见 `Energy.VS_SAMSUNG_NOTE`）。
 *
 * 它是「**今天**的当前状态」，所以**读不到也要推**（`available=false`）——
 * 推空是正确的更新，手表据此显示"本机没有这项数据 + 原因"，
 * 而不是继续挂着一个几天前的分数冒充今天（与压力/经期同一条，与 ODI 相反）。
 */
@Serializable
data class EnergySummaryMessage(
    /** 有没有数据。false = 这次读不到（没权限 / 区域不支持），手表据此显示原因。 */
    val available: Boolean = false,
    /** 分数（0~100）。`available=false` 时**不要显示**（0 会被读成"状态极差"）。 */
    val score: Int = 0,
    /** 这条分数属于哪一天（epochDay）。 */
    val dateEpochDay: Long = 0L,
    /** 最近若干天的分数，**旧 → 新**，手表可画一个小趋势；可以为空。 */
    val recent: List<Int> = emptyList(),
    /** 读于时刻，用于时效校验（与压力分数同理：放久了该提示"可能已过时"）。 */
    val atMs: Long = 0L,

    // ══════════ 本 App 自己算的那一份（2026-09-19 新增）══════════
    //
    // 上面那几个字段说的是**三星的**能量评分，下面这些是本 App 用 `core/Energy.kt`
    // 从睡眠时长 / 睡眠期最低血氧 / 睡眠期平均心率算出来的。两者**并排展示、互不替代**：
    // 三星那个口径不公开、要 Watch7+ 与三星账号、分区域放开（很多机器上根本没有）；
    // 我们那个只用手表确实采到、存在本机的东西，所以**任何机器上都给得出**。
    //
    // ⚠ 全部带默认值、且**只增不改**：手表端把上一份摘要缓存在 SharedPreferences 里，
    // 升级后冷启动时会用新代码去解析**旧版本写下的 JSON**。缺字段必须能解析成默认值，
    // 否则那次升级会让手表上的能量简页直接空白（而且不会报错，只是没数据）。

    /** 本 App 的分算出来了没。false = 那一晚三个输入一个都没有（见 [ownNote]）。 */
    val ownAvailable: Boolean = false,
    /** 本 App 的分（0~100，越高表示**这一夜的恢复**越好）。 */
    val ownScore: Int = 0,
    /** 分级：`Energy.Level.LOW` / `FAIR` / `GOOD`，手表用 `Energy.levelLabel` 翻译。 */
    val ownLevel: String = "",
    /**
     * 本 App 这个分算的是**哪一晚**（醒来那天）。
     *
     * 与三星侧的 [dateEpochDay] **可能不是同一天**：三星按天重算它那一份，
     * 而我们算的是"最近一次有完整数据的睡眠"。所以两块要各显示各的日期，
     * 不能共用一个 —— 共用的那天必然有一边在说谎。
     */
    val ownDateEpochDay: Long = 0L,
    /** 算不出来时的原因；算出来时为空串。**必须短**（手表的圆屏只放得下一两行）。 */
    val ownNote: String = "",
)
// 展示文案不放在这里，而是放在手表侧的 `EnergySummaryStore.kt`（扩展函数）。
// 原因：它需要"是不是今天"这个参数，而"今天"只有显示的那一刻才知道。
// 放这里会变成成员函数，**把同名的扩展函数遮蔽掉** —— 这是个安静的坑。

/**
 * 手机 -> 手表：睡眠呼吸暂停摘要。
 *
 * ⚠ 与能量评分**相反**：这是「**某一晚的既成事实**」，所以**一条记录都没有时不推这一帧** ——
 * 手表上已有一份是有效的，推空会把它抹掉（与 `odi_summary` 完全同一条纪律）。
 */
@Serializable
data class ApneaSummaryMessage(
    /** `DETECTED` / `NOT_DETECTED` / `UNDEFINED`，**原样传**，手表用 `Apnea.label` 翻译。 */
    val sign: String = Apnea.UNDEFINED,
    /** 这是哪一晚（epochDay）。 */
    val dateEpochDay: Long = 0L,
    /** 检测记录的起止（epoch 毫秒）。 */
    val startMs: Long = 0L,
    val endMs: Long = 0L,
    val atMs: Long = 0L,
) {
}
// 展示文案同样在手表侧（`ApneaSummaryStore.kt` 的扩展函数）。

object FrameCodec {
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    fun encode(frame: Frame): String =
        json.encodeToString(Frame.serializer(), frame) + "\n"

    fun decode(line: String): Frame? =
        runCatching { json.decodeFromString(Frame.serializer(), line) }.getOrNull()
}


/**
 * 手机 -> 手表：三星扩展指标摘要（活动 / 饮食 / 血糖 / 心律不齐）。
 *
 * ## 为什么是"一帧装四块"而不是四个帧
 * 这四块在手机端是同一次 `syncToday` 里一起读出来的，手表侧也只是**四张小简页**
 * （圆屏放不下明细，明细在手机端）。合起来一帧省掉三次握手，也避免出现
 * "四帧里推成功了两帧"这种半截状态。
 *
 * ## ⚠ 全部字段可空、且**只增不改**
 * 手表端把上一份摘要缓存在 SharedPreferences 里，升级后冷启动会**用新代码解析旧 JSON**。
 * 缺字段必须能解析成默认值，否则那次升级会让手表上这四页直接空白 ——
 * 而且不报错，只是没数据。
 *
 * **用 `null` 而不是 0/空串表示"没有"**：0 步会被读成"今天一步没走"。
 *
 * ## ⚠ 每块一个 note：不把三件事混成一句
 * "未授权" / "读取出错" / "三星健康里本来就没有这类数据"是**三件要做的事完全不同的事**。
 * 空串 = 这一块读成功；非空 = 简短原因（原文，不翻译）。
 *
 * ## ⚠ 它是"今天的当前状态"，所以**读不到也要推**
 * 与能量/压力/经期同一条纪律；与 ODI / 呼吸暂停**相反**（那两个是"既成事实"，
 * 推空会抹掉手表上有效的一份）。
 */
@Serializable
data class ExtrasSummaryMessage(
    val atMs: Long = 0L,
    /** 这些数属于哪一天（epochDay）。手表据此做过期校验 —— 隔天就不该再标「今日」。 */
    val dateEpochDay: Long = 0L,

    // ───────────────────────── 活动 ─────────────────────────
    val steps: Long? = null,
    val stepGoal: Int? = null,
    val activeKcal: Float? = null,
    val activeKcalGoal: Int? = null,
    val activeMs: Long? = null,
    val activeMsGoalMs: Long? = null,
    /** **米**。 */
    val distanceM: Float? = null,
    /** 今日爬楼层数。 */
    val floors: Float? = null,
    /** 最近一条运动。**只推一条** —— 圆屏放不下列表，明细在手机端。 */
    val lastExerciseName: String? = null,
    val lastExerciseMs: Long? = null,
    val lastExerciseAtMs: Long = 0L,

    // ───────────────────────── 饮食 ─────────────────────────
    val kcalToday: Float? = null,
    val kcalGoal: Float? = null,
    val waterMlToday: Float? = null,
    val waterGoalMl: Float? = null,

    // ───────────────────────── 血糖 ─────────────────────────
    /**
     * ⚠ 单位是 **mmol/L**，不是 mg/dL —— 两者**差约 18 倍**，而 5.6 与 101 看起来都"正常"。
     * 换算一律走 `SamsungExtras` 的 `Extras.fmtGlucose`，别在别处手写。
     */
    val glucoseMmol: Float? = null,
    val glucoseAtMs: Long = 0L,
    /** 情境（空腹/餐后…），已翻译。 */
    val glucoseMeal: String = "",

    // ─────────────────────── 心律不齐 ───────────────────────
    /**
     * 这个类型**读成功过**没有。
     *
     * ⚠ 它只有 `DETECTED` / `UNDEFINED` 两个取值，**没有 `NOT_DETECTED`** ——
     * 所以"能读、但没有记录"**不等于**"心脏没问题"。这条边界在界面上**不折叠**
     * （与「睡眠呼吸暂停只说迹象、不下结论」同源）。
     */
    val ihrnReadable: Boolean = false,
    val ihrnStatus: String = "",
    val ihrnAtMs: Long = 0L,

    // ────────────── 各块的读取结果说明（空串 = 成功）──────────────
    val activityNote: String = "",
    val goalNote: String = "",
    val dietNote: String = "",
    val glucoseNote: String = "",
    val ihrnNote: String = "",
)
