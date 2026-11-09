package com.gemini.healthbridge.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HeartRateDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: HeartRateEntity): Long

    /**
     * 批量写入，返回每个元素的行号（被 `(source, measuredAt)` 唯一索引挡下的重复返回 -1）。
     *
     * 存在的理由：自动同步一次要把**一整天**的三星健康心率（连续记录时上百条）落库，
     * 逐条 insert 就是上百次事务，在 UI 线程之外也会拖长同步时间。批量走一次事务。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<HeartRateEntity>): List<Long>

    @Query("SELECT * FROM heart_rates ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HeartRateEntity>>

    @Query("SELECT * FROM heart_rates ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<HeartRateEntity>

    @Query("SELECT * FROM heart_rates WHERE measuredAt >= :since ORDER BY measuredAt ASC")
    suspend fun since(since: Long): List<HeartRateEntity>

    /**
     * 配对窗口用的**双端**查询。
     *
     * ⚠ 刻意不用 [since]：那是开区间，会把窗口之前的历史全拉出来 ——
     *   心率是本项目里行数最多的表之一（全速率采集），拉全表再在内存里过滤
     *   会让「录一条血压」这种轻操作变成重操作。
     */
    @Query("SELECT * FROM heart_rates WHERE measuredAt BETWEEN :from AND :to ORDER BY measuredAt ASC")
    suspend fun between(from: Long, to: Long): List<HeartRateEntity>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM heart_rates ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<HeartRateEntity>

    @Query("SELECT * FROM heart_rates ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): HeartRateEntity?

    @Query("SELECT * FROM heart_rates WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<HeartRateEntity>

    @Query("SELECT COUNT(*) FROM heart_rates WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE heart_rates SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM heart_rates")
    fun count(): Flow<Int>
}

@Dao
interface BloodPressureDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: BloodPressureEntity): Long

    /** 批量写入，重复行（同一 source + recordedAt）返回 -1。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<BloodPressureEntity>): List<Long>

    @Query("SELECT * FROM blood_pressures ORDER BY recordedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<BloodPressureEntity>>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM blood_pressures ORDER BY recordedAt ASC")
    suspend fun allOnce(): List<BloodPressureEntity>

    @Query("SELECT * FROM blood_pressures WHERE synced=0 ORDER BY recordedAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<BloodPressureEntity>

    @Query("SELECT COUNT(*) FROM blood_pressures WHERE synced=0")
    suspend fun unsyncedCount(): Int

    /**
     * 某个来源在 [sinceMs] 之后写入了多少条。
     *
     * 用途：手动录入的**业务限额**（2026-11-04 用户要求「每天最多 4 组」）。
     * ⚠ 一定要按 `source` 过滤：三星健康导入的历史读数不是「用户今天录入的」，
     *   把它们算进去会让老用户一上来就被限额挡住。
     */
    @Query("SELECT COUNT(*) FROM blood_pressures WHERE source = :source AND recordedAt >= :sinceMs")
    suspend fun countBySourceSince(source: String, sinceMs: Long): Int

    /** 某个来源最近一条的时间戳（毫秒）；没有则 null。用于「间隔至少 30 分钟」。 */
    @Query("SELECT MAX(recordedAt) FROM blood_pressures WHERE source = :source")
    suspend fun lastAtOf(source: String): Long?

    /**
     * 删除一条血压记录（2026-11-04 用户要求「删除重新记录」）。
     *
     * ⚠ 调用方必须**同时**删掉 `bp_calibration_samples` 里 `bpId` 相同的那条 ——
     *   虽然样本查询是 `INNER JOIN blood_pressures`（孤儿行不会进回归），
     *   但留着会污染 `samples.size` 这类计数（界面上「已配对 N 组」虚高），
     *   而且用户删掉的那组会以"孤儿"的形式一直占着位置。
     */
    @Query("DELETE FROM blood_pressures WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Query("UPDATE blood_pressures SET synced=1 WHERE synced=0 AND recordedAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM blood_pressures")
    fun count(): Flow<Int>
}

/**
 * 血压校准的配对样本（v19 新增）。
 *
 * ⚠ **本表刻意不参与手表同步**：`patMs` 已经在 `ecg_records` 里同步，
 * 而校准样本天然产生于手机端（用户在手机上录袖套）。若将来手表端也要引导
 * 配对采集，就必须把它接进那三处同步硬约定。
 */
@Dao
interface BpCalibrationSampleDao {

    /**
     * 写入一条配对样本。
     *
     * ⚠ 用 REPLACE 而不是 IGNORE：同一条读数**重新配对**是正常流程
     * （用户先测了袖套、过五分钟又测了一次手表），那时应当覆盖而不是留下旧值。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: BpCalibrationSampleEntity)

    /**
     * 可用于回归的配对样本（`INNER JOIN` 读数，读数被删的孤儿行自然消失）。
     *
     * ⚠ `quality > 0` 与 `patMs IS NOT NULL` 是**两道**过滤，不是一道：
     * 前者挡「质量不合格」、后者挡「数据异常写进来时 patMs 丢了」。少一道的后果是
     * 回归拿到一个 null PAT，而那会在 Kotlin 侧变成一个静默的 0。
     */
    @Query(
        """
        SELECT s.bpId AS bpId, s.pairedAt AS pairedAt, s.patMs AS patMs,
               s.patSpreadMs AS patSpreadMs, s.hrMean AS hrMean,
               s.hrvRmssdMs AS hrvRmssdMs, s.posture AS posture,
               s.windowMs AS windowMs,
               b.systolic AS systolic, b.diastolic AS diastolic
        FROM bp_calibration_samples s
        INNER JOIN blood_pressures b ON b.id = s.bpId
        WHERE s.quality > 0 AND s.patMs IS NOT NULL
        ORDER BY s.pairedAt ASC
        """
    )
    suspend fun usablePairs(): List<BpPairRow>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM bp_calibration_samples ORDER BY pairedAt ASC")
    suspend fun allOnce(): List<BpCalibrationSampleEntity>

    @Query("SELECT COUNT(*) FROM bp_calibration_samples WHERE quality > 0")
    fun usableCount(): Flow<Int>

    /**
     * 清空全部配对样本（「重新校准」用，2026-10-05 用户要求）。
     *
     * ⚠⚠⚠ **只清这一张表，绝不动 `blood_pressures`**：
     *   `blood_pressures` 是用户**自己量的血压记录**（他的真实数据）；
     *   `bp_calibration_samples` 只是"把某条血压与手表信号关联起来"的**派生行**。
     *   两者一起清 = 把他量的血压也删掉了 —— 那是不可接受的数据损失，
     *   而"重新校准"的本意只是**换一批拟合用的样本**。
     *   ⇒ 清空后：预估停止（需要重新攒 [BpPredict.MIN_PAIRED_GROUPS] 组），
     *   但**血压历史一条不少**。
     *
     * ⚠ 只加语句、**不改表结构** ⇒ **不需要 DB 迁移**（`AppDatabase.DB_VERSION` 不动）。
     * ⚠ 清空后"补齐"按钮仍能把**已有的**血压重新配对回来 —— 那是用户的选择，
     *   不是 bug；界面上要说清这一点，否则他会以为清空没生效。
     */
    @Query("DELETE FROM bp_calibration_samples")
    suspend fun deleteAll()

    /**
     * **还没有**配对样本的读数（回填用）。
     *
     * ⚠ 用 `LEFT JOIN ... IS NULL` 而不是「先查全部再在内存里比」：
     *   回填要对每一条历史读数问一次「配过没有」，逐条查会变成 N+1。
     * ⚠ 连 `recordedAt` 与 `posture` 一起返回，而不是只给 id：回填要前者去查配对窗口、
     *   要后者当分组键 —— 只给 id 就得多查一次，那正是这条查询想避免的 N+1。
     */
    @Query(
        """
        SELECT b.id AS id, b.recordedAt AS recordedAt, b.posture AS posture
        FROM blood_pressures b
        LEFT JOIN bp_calibration_samples s ON s.bpId = b.id
        WHERE s.bpId IS NULL AND b.recordedAt >= :since
        ORDER BY b.recordedAt ASC
        LIMIT :limit
        """
    )
    suspend fun unpairedReadings(since: Long, limit: Int): List<UnpairedBpRow>

    /** 读数被删时清掉配对行（正常路径靠 JOIN 自然消失，这条是显式清理用）。 */
    @Query("DELETE FROM bp_calibration_samples WHERE bpId = :bpId")
    suspend fun deleteByBp(bpId: Long)
}

/** [BpCalibrationSampleDao.unpairedReadings] 的一行。 */
data class UnpairedBpRow(val id: Long, val recordedAt: Long, val posture: String? = null)

/** [BpCalibrationSampleDao.usablePairs] 的一行：配对元数据 + 袖套读数。 */
data class BpPairRow(
    val bpId: Long,
    val pairedAt: Long,
    val patMs: Int?,
    val patSpreadMs: Int?,
    val hrMean: Int?,
    val hrvRmssdMs: Int?,
    val systolic: Int,
    val diastolic: Int,
    /**
     * 测量体位。⚠ 取的是**配对样本那一列**（`s.posture`）而不是读数表那一列 ——
     * 两者在写入时是同一次赋值，但配对样本才是"训练输入"的载体，
     * 将来若允许改体位，也只该改配对样本。
     */
    val posture: String? = null,
    /**
     * 配对窗口宽度（毫秒）。⚠ 血压页「校准覆盖」提示要用它判断某次心电**是否已配上对** ——
     * 少了这一列就只能靠"落在同一时刻附近"猜，而**猜错会让界面虚报缺口**。
     */
    val windowMs: Long = 0L,
)

@Dao
interface SpO2Dao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: SpO2Entity): Long

    /**
     * 批量写入，重复行返回 -1。
     * 一晚的睡眠血氧是**每分钟一个点**，一次同步就是几百条，必须走批量事务。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<SpO2Entity>): List<Long>

    @Query("SELECT * FROM spo2 ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SpO2Entity>>

    /** 回填用：只要最近若干条（[recent] 是 Flow，那个位置不适合一次性读）。 */
    @Query("SELECT * FROM spo2 ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<SpO2Entity>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM spo2 ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<SpO2Entity>

    @Query("SELECT * FROM spo2 WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<SpO2Entity>

    @Query("SELECT COUNT(*) FROM spo2 WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("SELECT * FROM spo2 ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): SpO2Entity?

    @Query("UPDATE spo2 SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM spo2")
    fun count(): Flow<Int>

    /**
     * 清理物理上不可能的血氧值。
     *
     * 存在的理由：曾经的血氧单位换算 bug（把三星已按 0–100 给出的百分比又乘了 100）
     * 让手机把 `9700%` 这种值当成"三星健康记录"推给手表并落库。修正换算后新数据是对的，
     * 但旧行会被 `(source, measuredAt)` 去重挡住、永远留在库里显示成 9700%。
     * 血氧不可能超过 100%，所以按取值域删是**可证明安全**的自愈，不会误删真实数据。
     */
    @Query("DELETE FROM spo2 WHERE percent > 100 OR percent < 0")
    suspend fun deleteOutOfRange(): Int
}

@Dao
interface TemperatureDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: TemperatureEntity): Long

    /** 批量写入，重复行返回 -1。三星健康一晚的皮肤温度是序列，一次几十上百条。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<TemperatureEntity>): List<Long>

    @Query("SELECT * FROM temperatures ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<TemperatureEntity>>

    @Query("SELECT * FROM temperatures ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): TemperatureEntity?

    /** 按来源取最近一条：体温页要分别显示"最近点检值"与"最近三星夜间值"。 */
    @Query("SELECT * FROM temperatures WHERE source = :source ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latestOf(source: String): TemperatureEntity?

    /**
     * 按来源前缀取时间窗内的记录。
     *
     * 用前缀而不是等值：手表端将来若细分测量方式（点检/连续），
     * 前缀能把它们一起捞出来，而不会被新的 source 值悄悄漏掉。
     */
    @Query(
        "SELECT * FROM temperatures WHERE source LIKE :prefix || '%' AND measuredAt >= :since " +
            "ORDER BY measuredAt ASC"
    )
    suspend fun sinceOfPrefix(prefix: String, since: Long): List<TemperatureEntity>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM temperatures ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<TemperatureEntity>

    @Query("SELECT * FROM temperatures WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<TemperatureEntity>

    @Query("SELECT COUNT(*) FROM temperatures WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE temperatures SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM temperatures")
    fun count(): Flow<Int>
}

/**
 * 体成分记录（一次 BIA 测量）。
 *
 * **刻意不提供"删除越界行"的自愈方法**（对比 [SpO2Dao.deleteOutOfRange]）：
 * 血氧超过 100% 在物理上不可能，删是可证明安全的；体成分则不同 ——
 * 它由算法用用户资料反推，一旦资料填错，正常也可能算出很离谱的体脂率，
 * 那不是"坏数据"而是"真数据配错资料"。这种行要留着（它的资料快照就是排查线索），
 * 界面靠 `BodyComposition.inRange` 判为不可信并显示 "——"，而不是把记录销毁。
 */
@Dao
interface BodyCompositionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: BodyCompositionEntity): Long

    /** 批量写入，重复行返回 -1（三星健康历史 + 「恢复备份」都要批量）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<BodyCompositionEntity>): List<Long>

    @Query("SELECT * FROM body_compositions ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<BodyCompositionEntity>>

    /** 回填用：只要最近若干条。 */
    @Query("SELECT * FROM body_compositions ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<BodyCompositionEntity>

    @Query("SELECT * FROM body_compositions ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): BodyCompositionEntity?

    /** 按来源取最近一条：体成分页要分开显示"手表最近一次"与"三星健康最近一次"。 */
    @Query("SELECT * FROM body_compositions WHERE source = :source ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latestOf(source: String): BodyCompositionEntity?

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM body_compositions ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<BodyCompositionEntity>

    @Query("SELECT * FROM body_compositions WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<BodyCompositionEntity>

    @Query("SELECT COUNT(*) FROM body_compositions WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE body_compositions SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM body_compositions")
    fun count(): Flow<Int>
}

@Dao
interface EcgWaveDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: EcgWaveEntity): Long

    /**
     * 批量写入，重复行返回 -1。
     * 「恢复备份」一次要写回几十上百条波形，逐条 insert 就是几百次事务。
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<EcgWaveEntity>): List<Long>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM ecg_waves ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<EcgWaveEntity>

    @Query("SELECT * FROM ecg_waves ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): EcgWaveEntity?

    /** 按测量时刻取波形：同步时手表用它给 `ecg_records` 附上波形，手机用它给详情页取波形。 */
    @Query("SELECT * FROM ecg_waves WHERE measuredAt = :measuredAt AND source = :source LIMIT 1")
    suspend fun byTime(measuredAt: Long, source: String): EcgWaveEntity?

    /** 手机端详情页：来源按设备型号不同，只按时刻匹配。 */
    @Query("SELECT * FROM ecg_waves WHERE measuredAt = :measuredAt LIMIT 1")
    suspend fun byTimeAny(measuredAt: Long): EcgWaveEntity?

    @Query("SELECT COUNT(*) FROM ecg_waves")
    fun count(): Flow<Int>
}

@Dao
interface EcgRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: EcgRecordEntity): Long

    /** 批量写入，重复行返回 -1（「恢复备份」用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<EcgRecordEntity>): List<Long>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM ecg_records ORDER BY recordedAt ASC")
    suspend fun allOnce(): List<EcgRecordEntity>

    @Query("SELECT * FROM ecg_records ORDER BY recordedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EcgRecordEntity>>

    /** 一次性读取（血压预估取「最近一次有 PAT 的会话」用；[recent] 是 Flow，取不到单值）。 */
    @Query("SELECT * FROM ecg_records ORDER BY recordedAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<EcgRecordEntity>

    /**
     * 配对窗口内**有 PAT** 的心电记录（血压校准取数用）。
     *
     * ⚠ `patMs IS NOT NULL` 写在 SQL 里而不是取回后过滤：`ecg_records` 行数不小，
     *   而绝大多数行都是"这次心电没算出 PAT"（含被质量门控挡掉的会话）。
     */
    @Query(
        """
        SELECT * FROM ecg_records
        WHERE recordedAt BETWEEN :from AND :to AND patMs IS NOT NULL
        ORDER BY recordedAt ASC
        """
    )
    suspend fun withPatBetween(from: Long, to: Long): List<EcgRecordEntity>

    /**
     * 窗口内**带心率**的心电记录。
     *
     * ⚠⚠ 存在的理由（2026-10-03 修的一个"配对永远为 0"）：心电会话**自带心率**
     *   （手表端 `heartRate = analysis.heartRate`），但它**不写 `heart_rate` 表**。
     *   而用户的配对流程恰恰是「手表录血压 → 紧接着测心电」——那个 ±5 分钟窗口里
     *   通常**一条心率记录都没有**。若配对只看心率表，`hrSamples` 恒为 0
     *   ⇒ `BpPredict.quality()` 判 `Unusable` ⇒ 用户按标准流程走完，配对一条都生不出来。
     *   ⇒ 由 `BpCalibration.windowSignals` 把这里的心率并进去。
     */
    @Query(
        """
        SELECT * FROM ecg_records
        WHERE recordedAt BETWEEN :from AND :to AND heartRate IS NOT NULL
        ORDER BY recordedAt ASC
        """
    )
    suspend fun withHrBetween(from: Long, to: Long): List<EcgRecordEntity>

    @Query("SELECT * FROM ecg_records WHERE synced=0 ORDER BY recordedAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<EcgRecordEntity>

    @Query("SELECT COUNT(*) FROM ecg_records WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE ecg_records SET synced=1 WHERE synced=0 AND recordedAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM ecg_records")
    fun count(): Flow<Int>
}

/**
 * 活动片段（一段加速度采集的统计结果）。
 *
 * 没有"删除越界行"的自愈方法：活动档位是由阈值算出来的**相对结论**，
 * 阈值本身随算法版本变化，据此删数据没有可证明的安全性（对比 [SpO2Dao.deleteOutOfRange]，
 * 那条是"血氧不可能 >100%"这种物理上不可能的取值，删才安全）。
 */
@Dao
interface ActivityDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: ActivityEntity): Long

    /** 批量写入，重复行返回 -1（「恢复备份」用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ActivityEntity>): List<Long>

    @Query("SELECT * FROM activities ORDER BY startedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ActivityEntity>>

    @Query("SELECT * FROM activities ORDER BY startedAt DESC LIMIT 1")
    suspend fun latest(): ActivityEntity?

    /** 时间窗内的片段（手机端算"今日活动分布"用），按时间升序。 */
    @Query("SELECT * FROM activities WHERE startedAt >= :since ORDER BY startedAt ASC")
    suspend fun since(since: Long): List<ActivityEntity>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM activities ORDER BY startedAt ASC")
    suspend fun allOnce(): List<ActivityEntity>

    @Query("SELECT * FROM activities WHERE synced=0 ORDER BY startedAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<ActivityEntity>

    @Query("SELECT COUNT(*) FROM activities WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE activities SET synced=1 WHERE synced=0 AND startedAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM activities")
    fun count(): Flow<Int>
}

/**
 * PPG 原始光信号会话（一段波形 + 摘要）。
 *
 * 与 [EcgWaveDao] 同样是"波形表"：一条记录里带着几百到几千个浮点数（编码成 CSV）。
 * 所以它**必须**有批量写入（「恢复备份」一次要写回几十条波形），
 * 而对它做逐条 insert 的循环是明确的性能坑。
 *
 * 没有"删除越界行"的自愈方法：这里的每个值都是**记录事实**而不是判定结论 ——
 * 归一化波形再"离谱"也是当时真实采到的（比如手指没放好），删掉等于伪造历史。
 * 真正不可能出现的值（NaN）在写入前就被 `Ppg.normalize` 挡掉了。
 */
@Dao
interface PpgWaveDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: PpgWaveEntity): Long

    /** 批量写入，重复行返回 -1（「恢复备份」用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<PpgWaveEntity>): List<Long>

    @Query("SELECT * FROM ppg_waves ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<PpgWaveEntity>>

    @Query("SELECT * FROM ppg_waves ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): PpgWaveEntity?

    /**
     * 取某时间范围内的 PPG（2026-10-05 导出用）。
     *
     * ⚠ 用**范围**而不是等值：心电与 PPG 是同一会话的两条通道，
     *   落库时间戳会差几秒，`measuredAt = :t` 几乎必然落空。
     * ⚠ 取范围内**最新**的一条即可 —— 同一会话本来就只有一条 PPG。
     * ⚠ 只加查询**不改表结构** ⇒ **不需要 DB 迁移**（`AppDatabase.DB_VERSION` 不动）。
     */
    @Query("SELECT * FROM ppg_waves WHERE measuredAt >= :from AND measuredAt <= :to ORDER BY measuredAt DESC LIMIT 1")
    suspend fun inRange(from: Long, to: Long): PpgWaveEntity?

    /**
     * 全表读取，供「导出备份」使用。
     *
     * 注意这里**会带上波形列**（一条几十 KB）：备份本来就要完整搬运，
     * 而"备份时把波形省掉"会让恢复出来的记录只剩摘要、再也补不回波形
     * （波形在手机侧没有第二个来源）。所以宁可慢一点、文件大一点。
     */
    @Query("SELECT * FROM ppg_waves ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<PpgWaveEntity>

    @Query("SELECT * FROM ppg_waves WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<PpgWaveEntity>

    @Query("SELECT COUNT(*) FROM ppg_waves WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE ppg_waves SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM ppg_waves")
    fun count(): Flow<Int>
}

/**
 * HRV 分析记录（一次心电对应的逐拍间期 + 测量条件）。
 *
 * 表里**只有输入没有指标**：RMSSD/SDNN/LF/HF 由 `Hrv.analyze` 现算。理由见 `HrvSessionEntity`。
 * 因此这张表没有"删除越界行"之类的自愈方法：每个值都是**当时的记录事实**，
 * 伪迹该不该修正是分析层的判断，不是存储层的判断 —— 存储层删掉原始值等于销毁证据。
 */
@Dao
interface HrvDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: HrvSessionEntity): Long

    /** 批量写入，重复行返回 -1（「恢复备份」用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<HrvSessionEntity>): List<Long>

    @Query("SELECT * FROM hrv_sessions ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HrvSessionEntity>>

    /** 回填用：只要最近若干条。⚠ 这一类的 RMSSD 是**现算**的（表里只存原始间期）。 */
    @Query("SELECT * FROM hrv_sessions ORDER BY measuredAt DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<HrvSessionEntity>

    @Query("SELECT * FROM hrv_sessions ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): HrvSessionEntity?

    /** 按时间去重键取一条（手机端把 HRV 挂到同一次心电上时用）。 */
    @Query("SELECT * FROM hrv_sessions WHERE measuredAt=:measuredAt LIMIT 1")
    suspend fun byTime(measuredAt: Long): HrvSessionEntity?

    /**
     * 配对窗口内的 HRV 会话（血压校准取数用）。
     *
     * ⚠ 刻意不用 `recent`：那是"最近 N 条"、与时间无关，会把窗口之外的历史拉进来。
     */
    @Query(
        "SELECT * FROM hrv_sessions WHERE measuredAt BETWEEN :from AND :to ORDER BY measuredAt ASC"
    )
    suspend fun between(from: Long, to: Long): List<HrvSessionEntity>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM hrv_sessions ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<HrvSessionEntity>

    @Query("SELECT * FROM hrv_sessions WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<HrvSessionEntity>

    @Query("SELECT COUNT(*) FROM hrv_sessions WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE hrv_sessions SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM hrv_sessions")
    fun count(): Flow<Int>
}

/**
 * 经期记录（模块 #13）。只有手机端读写，不走上行同步。
 *
 * 与 HRV 一样**只存输入不存指标**：周期长度/预测/易孕窗口由 `Cycle.analyze` 现算，
 * 落库的只是"哪天开始、哪天结束、流量多大、从哪来"这几个事实。
 * 因此这张表同样没有"删除越界行"之类的自愈方法 —— 一条记录该不该参与统计
 * 是分析层的判断（漏记的 56 天会被过滤，但记录本身仍保留在列表里供核对）。
 */
@Dao
interface MenstrualRecordDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: MenstrualRecordEntity): Long

    /** 批量写入，重复行返回 -1（「恢复备份」用）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MenstrualRecordEntity>): List<Long>

    /** 全部记录按开始日升序（分析用：`Cycle.analyze` 内部会再排序，但这样列表更直观）。 */
    @Query("SELECT * FROM menstrual_records ORDER BY startEpochDay ASC")
    fun all(): Flow<List<MenstrualRecordEntity>>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM menstrual_records ORDER BY startEpochDay ASC")
    suspend fun allOnce(): List<MenstrualRecordEntity>

    /** 删一条（编辑/删除场景）。 */
    @Query("DELETE FROM menstrual_records WHERE id=:id")
    suspend fun deleteById(id: Long)

    /** 按 (source, startEpochDay) 取一条，供「编辑」时定位与去重判断。 */
    @Query("SELECT * FROM menstrual_records WHERE source=:source AND startEpochDay=:startEpochDay LIMIT 1")
    suspend fun byKey(source: String, startEpochDay: Long): MenstrualRecordEntity?
}

@Dao
interface MfBiaDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(e: MfBiaEntity): Long

    /** 批量写入，重复行返回 -1（「恢复备份」也要批量）。 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<MfBiaEntity>): List<Long>

    @Query("SELECT * FROM mf_bia_records ORDER BY measuredAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<MfBiaEntity>>

    /** 最近一次测量（界面首屏用）。 */
    @Query("SELECT * FROM mf_bia_records ORDER BY measuredAt DESC LIMIT 1")
    suspend fun latest(): MfBiaEntity?

    /**
     * 最近 [limit] 次**成功**且四频齐备的记录，按时间升序 —— 供算"相对自身基线"。
     *
     * 在 SQL 里就把条件写死，而不是取回来再过滤：基线要用的是"可比"的记录，
     * 让一条只有两个频点的失败记录混进去，中位数就被拉偏了（与 HRV 基线只收有效记录同源）。
     */
    @Query(
        "SELECT * FROM mf_bia_records WHERE m5 IS NOT NULL AND m10 IS NOT NULL " +
            "AND m50 IS NOT NULL AND m250 IS NOT NULL AND status = 0 " +
            "ORDER BY measuredAt DESC LIMIT :limit"
    )
    suspend fun recentUsable(limit: Int): List<MfBiaEntity>

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM mf_bia_records ORDER BY measuredAt ASC")
    suspend fun allOnce(): List<MfBiaEntity>

    @Query("SELECT * FROM mf_bia_records WHERE synced=0 ORDER BY measuredAt ASC LIMIT :limit")
    suspend fun unsynced(limit: Int): List<MfBiaEntity>

    @Query("SELECT COUNT(*) FROM mf_bia_records WHERE synced=0")
    suspend fun unsyncedCount(): Int

    @Query("UPDATE mf_bia_records SET synced=1 WHERE synced=0 AND measuredAt<=:upTo")
    suspend fun markSyncedUpTo(upTo: Long)

    @Query("SELECT COUNT(*) FROM mf_bia_records")
    fun count(): Flow<Int>
}

@Dao
interface SleepOdiDao {
    /**
     * 写入（同一天重复写则覆盖）。
     *
     * 用 REPLACE 而不是 IGNORE：摘要是"每次同步重算一遍"的，IGNORE 会让**昨天的旧结论一直挂着**
     * （这正是血氧坏行那次事故的同一类问题 —— 覆盖不掉）。一天只有一行，覆盖的代价可以忽略。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: SleepOdiEntity): Long

    /** 最近若干晚，按归属日降序（界面最新一晚在最上面）。 */
    @Query("SELECT * FROM sleep_odi ORDER BY dateEpochDay DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SleepOdiEntity>>

    /** 某一晚（按归属日）。 */
    @Query("SELECT * FROM sleep_odi WHERE dateEpochDay=:day LIMIT 1")
    suspend fun byDay(day: Long): SleepOdiEntity?

    /**
     * 最近一晚（推给手表用）。
     *
     * 刻意取"最近一晚"而不是"今天":手机端可能几天才同步一次，
     * 取今天会一直推不出东西，手表只能挂着一份更旧的摘要 —— 而两者都带着归属日，
     * 手表本来就会如实标出是哪一晚。**不返回 null 就推**，
     * 于是永远不会把手表上已有的摘要清成"无数据"（与睡眠/血氧那条纪律同源）。
     */
    @Query("SELECT * FROM sleep_odi ORDER BY dateEpochDay DESC LIMIT 1")
    suspend fun latestOnce(): SleepOdiEntity?

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM sleep_odi ORDER BY dateEpochDay ASC")
    suspend fun allOnce(): List<SleepOdiEntity>

    /** 批量写回（「恢复备份」用）。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SleepOdiEntity>): List<Long>

    /** 只保留最近 [keep] 晚，更早的删掉（摘要是派生的，长期堆积没有意义）。 */
    @Query(
        "DELETE FROM sleep_odi WHERE dateEpochDay NOT IN " +
            "(SELECT dateEpochDay FROM sleep_odi ORDER BY dateEpochDay DESC LIMIT :keep)"
    )
    suspend fun keepLatest(keep: Int)
}

/**
 * 能量评分（三星每天一条）。
 *
 * 写入用 REPLACE 而不是 IGNORE：同一天三星会重算，重算后的值应当**覆盖**旧值，
 * 而不是被唯一索引挡在外面留一条过期的（这与血氧那种"同一时刻同一来源只留一条"同源，
 * 但语义不同 —— 那边重复读到的值是一样的，这边会变）。
 */
@Dao
interface EnergyScoreDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EnergyScoreEntity>): List<Long>

    /** 最近若干天，新的在前（界面画趋势用）。 */
    @Query("SELECT * FROM energy_scores ORDER BY dateEpochDay DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<EnergyScoreEntity>>

    /** 最近一条（推给手表的摘要用；没有就是 null，不返回 0）。 */
    @Query("SELECT * FROM energy_scores ORDER BY dateEpochDay DESC LIMIT 1")
    suspend fun latestOnce(): EnergyScoreEntity?

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM energy_scores ORDER BY dateEpochDay ASC")
    suspend fun allOnce(): List<EnergyScoreEntity>

    /** 只保留最近 [keep] 天。 */
    @Query(
        "DELETE FROM energy_scores WHERE dateEpochDay NOT IN " +
            "(SELECT dateEpochDay FROM energy_scores ORDER BY dateEpochDay DESC LIMIT :keep)"
    )
    suspend fun keepLatest(keep: Int)
}

/** 睡眠呼吸暂停检测记录（三星健康监测器同步过来的）。 */
@Dao
interface SleepApneaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SleepApneaEntity>): List<Long>

    /** 最近若干条，新的在前。 */
    @Query("SELECT * FROM sleep_apnea_records ORDER BY startMs DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SleepApneaEntity>>

    /**
     * 最近一条。
     *
     * ⚠ 取不到时返回 null，调用方**不要推空帧给手表** —— 这是"某一晚的既成事实"，
     * 手表上已有的一份是有效的，推空会把它抹掉（与 ODI 同一条纪律）。
     */
    @Query("SELECT * FROM sleep_apnea_records ORDER BY startMs DESC LIMIT 1")
    suspend fun latestOnce(): SleepApneaEntity?

    /** 全表读取，供「导出备份」使用。 */
    @Query("SELECT * FROM sleep_apnea_records ORDER BY startMs ASC")
    suspend fun allOnce(): List<SleepApneaEntity>

    /** 只保留最近 [keep] 条。 */
    @Query(
        "DELETE FROM sleep_apnea_records WHERE startMs NOT IN " +
            "(SELECT startMs FROM sleep_apnea_records ORDER BY startMs DESC LIMIT :keep)"
    )
    suspend fun keepLatest(keep: Int)
}

/**
 * 每晚一行睡眠统计（自研能量评分的输入）。
 *
 * 写入用 REPLACE：**同一天重复同步会覆盖那一行**。这不是可选项 ——
 * 三星会在你补录/修正睡眠之后给出不同的阶段划分，而"实际睡眠时长"随之变；
 * 用 IGNORE 的话第一版（往往是最粗的那版）会永久留下，
 * 评分就一直按一个早该被修正的值在算，且没有任何地方看得出这件事。
 */
@Dao
interface SleepNightDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SleepNightEntity>): List<Long>

    /** 最近若干晚，新的在前。 */
    @Query("SELECT * FROM sleep_nights ORDER BY dateEpochDay DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<SleepNightEntity>>

    /** 最近一晚。没有就是 null —— **不要拿它当"睡了 0 小时"**。 */
    @Query("SELECT * FROM sleep_nights ORDER BY dateEpochDay DESC LIMIT 1")
    suspend fun latestOnce(): SleepNightEntity?

    /**
     * 全部（新的在前），供基线与趋势使用。
     *
     * 不做时间过滤而是全读：保留策略已经把这张表压在 [keepLatest] 的上限之内
     * （一晚一行，一百多行而已），而基线要的正是"最近若干晚"。
     */
    @Query("SELECT * FROM sleep_nights ORDER BY dateEpochDay DESC")
    suspend fun allOnce(): List<SleepNightEntity>

    /** 只保留最近 [keep] 晚。 */
    @Query(
        "DELETE FROM sleep_nights WHERE dateEpochDay NOT IN " +
            "(SELECT dateEpochDay FROM sleep_nights ORDER BY dateEpochDay DESC LIMIT :keep)"
    )
    suspend fun keepLatest(keep: Int)
}
/**
 * 每天的活动量（能量评分里「前一日的活动」「活动持续性」两项的输入）。
 *
 * 写入用 REPLACE，与 `sleep_nights` 同理：三星会在**回填/修正**之后改掉某天的步数，
 * 用 IGNORE 会把第一版（往往是最粗的那版）永久留下，
 * 评分就一直按一个早该被修正的值在算，且没有任何地方看得出这件事。
 */
@Dao
interface ActivityDayDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ActivityDayEntity>): List<Long>

    /** 某一天。没有就是 null —— **不要拿它当"那天一步没走"**。 */
    @Query("SELECT * FROM activity_days WHERE dateEpochDay = :epochDay LIMIT 1")
    suspend fun byDay(epochDay: Long): ActivityDayEntity?

    /** 最近若干天，新的在前。界面订阅它（与 `sleep_nights` 同一个形态）。 */
    @Query("SELECT * FROM activity_days ORDER BY dateEpochDay DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<ActivityDayEntity>>

    /** 一次性读取（补读时用来判断"哪些天已经有了"）。 */
    @Query("SELECT * FROM activity_days ORDER BY dateEpochDay DESC LIMIT :limit")
    suspend fun recentOnce(limit: Int): List<ActivityDayEntity>

    /**
     * 全部（新的在前）。备份用 —— 与 `sleep_nights` 同理：不做时间过滤而是全读，
     * 保留策略已经把这张表压在上限之内（一天一行）。
     */
    @Query("SELECT * FROM activity_days ORDER BY dateEpochDay DESC")
    suspend fun allOnce(): List<ActivityDayEntity>

    /** 只保留最近 [keep] 天。与 `sleep_nights` 同一套保留纪律。 */
    @Query(
        "DELETE FROM activity_days WHERE dateEpochDay NOT IN " +
            "(SELECT dateEpochDay FROM activity_days ORDER BY dateEpochDay DESC LIMIT :keep)"
    )
    suspend fun keepLatest(keep: Int)
}

/**
 * 每天的心率/血氧**每小时桶**（`daily_vitals`，DB v22）。
 *
 * ⚠ 读写判据：「库里有没有这一天」一律用 [one] / [days] 问，**不要拿"列表为空"当判据** ——
 * 空列表既可能是"那天没有采样"，也可能是"那天还没同步过"，
 * 而这两件事在界面上要说不同的话（见 `DayHistoryScreen` 的来源说明行）。
 */
@Dao
interface DailyVitalsDao {

    /** 写一天的两类桶。`REPLACE`：同一天重读会**覆盖**，不会积累重复行。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DailyVitalsEntity>): List<Long>

    /** 某一天的某一类。没有就是 null —— **不要拿它当"那天是 0"**。 */
    @Query("SELECT * FROM daily_vitals WHERE dateEpochDay = :day AND kind = :kind LIMIT 1")
    suspend fun one(day: Long, kind: String): DailyVitalsEntity?

    /**
     * 本地**有数据的那些天**（新的在前）—— 日历用它决定"哪些天可点"。
     *
     * ⚠ `DISTINCT`：一天两行，不去重会把日历的可用日期算成两倍。
     */
    @Query(
        "SELECT DISTINCT dateEpochDay FROM daily_vitals ORDER BY dateEpochDay DESC LIMIT :limit"
    )
    fun days(limit: Int): Flow<List<Long>>

    @Query("SELECT * FROM daily_vitals ORDER BY dateEpochDay DESC")
    suspend fun allOnce(): List<DailyVitalsEntity>

    /**
     * 只保留最近 [keep] **天**。
     *
     * ⚠ 子查询里必须 `DISTINCT`：一天两行，按行取 LIMIT 会把保留天数算成一半。
     */
    @Query(
        "DELETE FROM daily_vitals WHERE dateEpochDay NOT IN " +
            "(SELECT DISTINCT dateEpochDay FROM daily_vitals ORDER BY dateEpochDay DESC LIMIT :keep)"
    )
    suspend fun keepLatest(keep: Int)
}
