package com.gemini.healthbridge.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HeartRateEntity::class, BloodPressureEntity::class,
        EcgRecordEntity::class, SpO2Entity::class, EcgWaveEntity::class,
        TemperatureEntity::class, BodyCompositionEntity::class, ActivityEntity::class,
        PpgWaveEntity::class, HrvSessionEntity::class, MenstrualRecordEntity::class,
        SleepOdiEntity::class, MfBiaEntity::class,
        EnergyScoreEntity::class, SleepApneaEntity::class,
        SleepNightEntity::class, ActivityDayEntity::class,
        BpCalibrationSampleEntity::class, DailyVitalsEntity::class,
    ],
    version = 23,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun heartRateDao(): HeartRateDao
    abstract fun bloodPressureDao(): BloodPressureDao
    abstract fun ecgDao(): EcgRecordDao
    abstract fun spo2Dao(): SpO2Dao
    abstract fun ecgWaveDao(): EcgWaveDao
    abstract fun temperatureDao(): TemperatureDao
    abstract fun bodyCompositionDao(): BodyCompositionDao
    abstract fun activityDao(): ActivityDao
    abstract fun ppgWaveDao(): PpgWaveDao
    abstract fun hrvDao(): HrvDao
    abstract fun menstrualDao(): MenstrualRecordDao
    abstract fun sleepOdiDao(): SleepOdiDao
    abstract fun mfBiaDao(): MfBiaDao
    abstract fun energyScoreDao(): EnergyScoreDao
    abstract fun sleepApneaDao(): SleepApneaDao
    abstract fun sleepNightDao(): SleepNightDao
    abstract fun activityDayDao(): ActivityDayDao
    abstract fun bpCalibrationSampleDao(): BpCalibrationSampleDao
    abstract fun dailyVitalsDao(): DailyVitalsDao

    companion object {
        /** v1 -> v2：新增血氧表（保留既有数据）。 */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `spo2` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`percent` INTEGER NOT NULL, `heartRate` INTEGER, `measuredAt` INTEGER NOT NULL, " +
                        "`source` TEXT NOT NULL, `synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_spo2_source_measuredAt` ON `spo2` (`source`, `measuredAt`)"
                )
            }
        }

        /** v2 -> v3：新增心电波形表（仅本地）。 */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ecg_waves` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`measuredAt` INTEGER NOT NULL, `durationMs` INTEGER NOT NULL, `sampleCount` INTEGER NOT NULL, " +
                        "`minMv` REAL NOT NULL, `maxMv` REAL NOT NULL, `avgMv` REAL NOT NULL, " +
                        "`waveJson` TEXT NOT NULL, `source` TEXT NOT NULL)"
                )
            }
        }

        /** v3 -> v4：心电波形表新增节律分析结论字段（保留既有数据）。 */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ecg_waves` ADD COLUMN `classification` TEXT")
                db.execSQL("ALTER TABLE `ecg_waves` ADD COLUMN `analyzedHr` INTEGER")
            }
        }

        /** v4 -> v5：心电波形表接入同步——补元数据列并加 (source, measuredAt) 唯一索引用于两端去重。 */
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 先去掉历史重复行（旧版本没有唯一约束，理论上同源同毫秒可能重复），
                // 否则下面的唯一索引会创建失败、整次迁移回滚。
                db.execSQL(
                    "DELETE FROM `ecg_waves` WHERE `id` NOT IN " +
                        "(SELECT MIN(`id`) FROM `ecg_waves` GROUP BY `source`, `measuredAt`)"
                )
                db.execSQL("ALTER TABLE `ecg_waves` ADD COLUMN `sampleRate` INTEGER")
                db.execSQL("ALTER TABLE `ecg_waves` ADD COLUMN `regular` INTEGER")
                db.execSQL("ALTER TABLE `ecg_waves` ADD COLUMN `beatCount` INTEGER")
                db.execSQL("ALTER TABLE `ecg_waves` ADD COLUMN `bandRatio` REAL")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ecg_waves_source_measuredAt` " +
                        "ON `ecg_waves` (`source`, `measuredAt`)"
                )
            }
        }

        /**
         * v5 -> v6：新增体温表（腕部皮肤温度点检 + 三星健康夜间皮肤温度）。
         *
         * SQL 必须与 Room 由 [TemperatureEntity] 生成的建表语句逐字一致，
         * 否则打开库时的 schema 校验会失败（表现为崩溃，而不是静默）。
         * 加表是纯增量操作，不动任何已有表，因此**不会丢数据**。
         */
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `temperatures` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `celsius` REAL NOT NULL, " +
                        "`ambientC` REAL, `measuredAt` INTEGER NOT NULL, `source` TEXT NOT NULL, " +
                        "`synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_temperatures_source_measuredAt` " +
                        "ON `temperatures` (`source`, `measuredAt`)"
                )
            }
        }

        /**
         * v6 -> v7：新增体成分表（手表 BIA 测量 + 三星健康既有体成分记录）。
         *
         * 与 [MIGRATION_5_6] 同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [BodyCompositionEntity] 的声明逐字一致**：
         * Room 打开库时会拿磁盘上的表结构跟它由实体生成的建表语句做比对，
         * 不一致会直接抛异常（宁可当场炸，也不要静默重建 —— 见下面的注释）。
         */
        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `body_compositions` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `bodyFatRatio` REAL, " +
                        "`bodyFatMass` REAL, `totalBodyWater` REAL, `skeletalMuscleRatio` REAL, " +
                        "`skeletalMuscleMass` REAL, `basalMetabolicRate` REAL, `fatFreeRatio` REAL, " +
                        "`fatFreeMass` REAL, `impedanceMagnitude` REAL, `impedanceDegree` REAL, " +
                        "`weightKg` REAL, `heightCm` REAL, `gender` INTEGER, `age` INTEGER, " +
                        "`measuredAt` INTEGER NOT NULL, `source` TEXT NOT NULL, `synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_body_compositions_source_measuredAt` " +
                        "ON `body_compositions` (`source`, `measuredAt`)"
                )
            }
        }

        /**
         * v7 -> v8：新增活动片段表（三轴加速度采集的统计结果）。
         *
         * 与前两次同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [ActivityEntity] 的声明逐字一致** ——
         * Room 打开库时会拿磁盘上的表结构跟它由实体生成的建表语句做比对，不一致直接抛异常。
         */
        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activities` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startedAt` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `sampleCount` INTEGER NOT NULL, " +
                        "`level` TEXT NOT NULL, `meanG` REAL NOT NULL, `stdG` REAL NOT NULL, " +
                        "`p95G` REAL NOT NULL, `stillRatio` REAL NOT NULL, `peakJerkG` REAL NOT NULL, " +
                        "`meanXG` REAL, `meanYG` REAL, `meanZG` REAL, `envelope` TEXT, " +
                        "`source` TEXT NOT NULL, `synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activities_source_startedAt` " +
                        "ON `activities` (`source`, `startedAt`)"
                )
            }
        }

        /**
         * v8 -> v9：新增 PPG 原始光信号表（一段波形 + 摘要）。
         *
         * 与前三次同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [PpgWaveEntity] 的声明逐字一致** ——
         * Room 打开库时会拿磁盘上的表结构跟它由实体生成的建表语句做比对，不一致直接抛异常。
         *
         * 验证办法（每次加表都该跑一遍）：把这段 SQL 与
         * `core/build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt` 里 Room 自己生成的
         * `CREATE TABLE IF NOT EXISTS \`ppg_waves\` …` 做逐字节比对。
         */
        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ppg_waves` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `measuredAt` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `sampleCount` INTEGER NOT NULL, " +
                        "`sampleRateHz` INTEGER, `channels` TEXT NOT NULL, `dcGreen` REAL, " +
                        "`acGreenP2P` REAL, `quality` REAL, `estimatedHr` INTEGER, `refHr` INTEGER, " +
                        "`statusSeen` TEXT, `greenWave` TEXT, `redWave` TEXT, `irWave` TEXT, " +
                        "`source` TEXT NOT NULL, `synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_ppg_waves_source_measuredAt` " +
                        "ON `ppg_waves` (`source`, `measuredAt`)"
                )
            }
        }

        /**
         * v9 -> v10：新增 HRV 表（一次心电的逐拍间期 + 测量条件）。
         *
         * 与 [MIGRATION_8_9] 同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [HrvSessionEntity] 的声明逐字一致** ——
         * Room 打开库时会拿磁盘上的表结构跟它由实体生成的建表语句做比对，不一致直接抛异常。
         *
         * 验证办法：把这段 SQL 与 `core/build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt`
         * 里 Room 自己生成的 `CREATE TABLE IF NOT EXISTS \`hrv_sessions\` …` 做逐字节比对。
         */
        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `hrv_sessions` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `measuredAt` INTEGER NOT NULL, " +
                        "`durationMs` INTEGER NOT NULL, `rrCount` INTEGER NOT NULL, `rrMs` TEXT NOT NULL, " +
                        "`refHr` INTEGER, `ecgRegular` INTEGER, `activityLevel` TEXT, " +
                        "`source` TEXT NOT NULL, `synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_hrv_sessions_source_measuredAt` " +
                        "ON `hrv_sessions` (`source`, `measuredAt`)"
                )
            }
        }

        /**
         * v10 -> v11：新增经期记录表（只有手机端写，不走上行同步）。
         *
         * 与 [MIGRATION_9_10] 同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [MenstrualRecordEntity] 的声明逐字一致** ——
         * Room 打开库时会拿磁盘上的表结构跟它由实体生成的建表语句做比对，不一致直接抛异常。
         *
         * 验证办法：把这段 SQL 与 `core/build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt`
         * 里 Room 自己生成的 `CREATE TABLE IF NOT EXISTS \`menstrual_records\` …` 做逐字节比对。
         */
        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `menstrual_records` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `startEpochDay` INTEGER NOT NULL, " +
                        "`endEpochDay` INTEGER, `flow` TEXT, `source` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_menstrual_records_source_startEpochDay` " +
                        "ON `menstrual_records` (`source`, `startEpochDay`)"
                )
            }
        }

        /**
         * v11 -> v12：新增夜间氧减指数摘要表（只有手机端写，不走上行同步）。
         *
         * 与 [MIGRATION_10_11] 同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [SleepOdiEntity] 的声明逐字一致** ——
         * Room 打开库时会拿磁盘上的表结构跟它由实体生成的建表语句做比对，不一致直接抛异常。
         *
         * 指标列刻意可空：区分"没算出来"与"算出来是 0 次"（见该实体的注释）。
         *
         * 验证办法：迁移比对脚本 11 12` 逐字节比对这段 SQL 与
         * `core/build/generated/ksp/debug/kotlin/.../AppDatabase_Impl.kt` 里 Room 生成的建表语句。
         */
        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_odi` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `dateEpochDay` INTEGER NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `endMs` INTEGER NOT NULL, `events` INTEGER, " +
                        "`odiPerHour` REAL, `monitoredMinutes` REAL, `validPoints` INTEGER, " +
                        "`rejectedPoints` INTEGER, `lowest` INTEGER, `mean` REAL, `t90Pct` REAL, " +
                        "`reason` TEXT, `computedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_odi_dateEpochDay` " +
                        "ON `sleep_odi` (`dateEpochDay`)"
                )
            }
        }

        /**
         * v12 -> v13：新增多频阻抗表（`mf_bia_records`）。
         *
         * 与 [MIGRATION_11_12] 同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * 列的**顺序与可空性必须与 [MfBiaEntity] 的声明逐字一致**。
         *
         * 验证办法：迁移比对脚本 12 13`。
         */
        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `mf_bia_records` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `measuredAt` INTEGER NOT NULL, " +
                        "`m5` REAL, `p5` REAL, `m10` REAL, `p10` REAL, `m50` REAL, `p50` REAL, " +
                        "`m250` REAL, `p250` REAL, `status` INTEGER, `source` TEXT NOT NULL, " +
                        "`synced` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_mf_bia_records_source_measuredAt` " +
                        "ON `mf_bia_records` (`source`, `measuredAt`)"
                )
            }
        }

        /**
         * v13 → v14：加两张表（能量评分、睡眠呼吸暂停）。
         *
         * 与 [MIGRATION_12_13] 一样是**纯加表**，不动任何已有表，因此不会丢数据。
         *
         * 表结构与 SQL 的顺序必须与 Room 编译期生成的建表语句**完全一致**（列顺序也一样），
         * 否则运行期会报"迁移后的表结构与实体不匹配"。改完跑
         * `迁移比对脚本 13 14` 逐字节比对。
         */
        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `energy_scores` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `score` INTEGER NOT NULL, " +
                        "`measuredAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_energy_scores_dateEpochDay` " +
                        "ON `energy_scores` (`dateEpochDay`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_apnea_records` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startMs` INTEGER NOT NULL, `endMs` INTEGER, " +
                        "`dateEpochDay` INTEGER NOT NULL, `sign` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_apnea_records_startMs` " +
                        "ON `sleep_apnea_records` (`startMs`)"
                )
            }
        }

        /**
         * v14 → v15：加一张表（每晚睡眠统计 `sleep_nights`，自研能量评分的输入）。
         *
         * 与 [MIGRATION_12_13] / [MIGRATION_13_14] 一样是**纯加表**，不动任何已有表。
         *
         * 列顺序必须与实体声明顺序**一字不差**（Room 生成的建表语句按声明顺序排），
         * 否则运行期报"迁移后的表结构与实体不匹配"。改完跑
         * `迁移比对脚本 14 15` 逐字节比对。
         */
        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `sleep_nights` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `startMs` INTEGER NOT NULL, " +
                        "`endMs` INTEGER NOT NULL, `actualSleepMs` INTEGER NOT NULL, " +
                        "`hrAvg` INTEGER, `hrMin` INTEGER, `spo2Avg` INTEGER, " +
                        "`spo2Min` INTEGER, `samsungScore` INTEGER, " +
                        "`recordedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_sleep_nights_dateEpochDay` " +
                        "ON `sleep_nights` (`dateEpochDay`)"
                )
            }
        }

        /**
         * v15 → v16：**本项目第一次给已有表加列**（`sleep_nights.hrSd`），
         * 另外加一张表（`activity_days`）。
         *
         * ### 加列为什么用 ALTER TABLE 而不是"重建表"
         * SQLite 的 `ALTER TABLE ADD COLUMN` 是**原地**操作、不重写任何数据，
         * 对已有的一百多行睡眠记录零风险。而"建新表 → 拷贝 → 删旧表 → 改名"那套
         * 一旦中途失败就是丢数据，为了一列不值得。
         *
         * ⚠ **新列会加在表的末尾**，而 Kotlin 实体里 `hrSd` 声明在 `hrMin` 后面 ——
         * 两边列顺序不一致**没关系**：Room 的 `onValidateSchema` 按**列名**比对
         * （`TableInfo` 内部是 map，不看顺序）。这条是本项目第一次遇到，记在这里：
         * **别为了"顺序对齐"去做危险的表重建。**
         *
         * ⚠ 旧行（迁移前写入的）`hrSd` 是 NULL —— 这是**正确的**：那些晚本来就没算过心率波动。
         * 评分的「睡眠心率变化」会因为基线不够而缺席，而**不是**把 NULL 当成"波动为 0"
         * （那会给一个虚高的分）。
         *
         * ⚠ `compare_migration.py` **只比 CREATE TABLE**，所以这一条 `ALTER TABLE`
         * 不在它的校验范围内 —— 加列的期望值要另看 Room 生成物
         * `AppDatabase_Impl.kt` 里 `sleep_nights` 的 `TableInfo`（列类型 `REAL`、可空）。
         */
        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sleep_nights` ADD COLUMN `hrSd` REAL")
                db.execSQL("ALTER TABLE `sleep_nights` ADD COLUMN `skinTemp` REAL")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_days` " +
                        "(`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`dateEpochDay` INTEGER NOT NULL, `steps` INTEGER, " +
                        "`slotSteps` TEXT, `activeKcal` REAL, `activeMs` INTEGER, " +
                        "`recordedAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_activity_days_dateEpochDay` " +
                        "ON `activity_days` (`dateEpochDay`)"
                )
            }
        }

        /**
         * v16 → v17：`ecg_records` 加 PAT 三列（模块 #14 脉搏到达时间）。
         *
         * ⚠ 加在 `ecg_records`（**同步表**）而不是 `ecg_waves`（波形表）：
         * `ecg_waves` 没有 `synced` 列、不参与同步，PAT 落那儿手机端永远看不到。
         *
         * ⚠ **加列一律用 `ALTER TABLE ADD COLUMN`**（原地、不重写数据）——
         * 列会加在表末尾、与实体声明顺序不一致，**没关系**：Room 按列名比对、不看顺序。
         * 别为了"顺序对齐"去做危险的表重建。
         */
        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ecg_records` ADD COLUMN `patMs` INTEGER")
                db.execSQL("ALTER TABLE `ecg_records` ADD COLUMN `patSpreadMs` INTEGER")
                db.execSQL("ALTER TABLE `ecg_records` ADD COLUMN `patSeriesMs` TEXT")
                db.execSQL("ALTER TABLE `ecg_records` ADD COLUMN `patNote` TEXT")
            }
        }

        /**
         * v17→v18：给 `ecg_records` 加 `patAlgo`（这个 `patMs` 是哪一套口径算的）。
         *
         * 起因：PAT 的**参考点**从"滑窗积分包络的峰"改成了"吸附回真实 QRS"
         * （见 `EcgSignal.snapToQrs`），量出来的值系统性变了 —— 老值与新值**不可比**。
         * 手机端的自身基线若不区分，就会把两套口径混成一个中位数。
         * 老行情（`patAlgo` 为 NULL）留在库里不进基线。
         */
        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `ecg_records` ADD COLUMN `patAlgo` INTEGER")
            }
        }

        /**
         * v18→v19：新增 `bp_calibration_samples`（血压校准的配对样本）。
         *
         * 与 [MIGRATION_5_6] 同样是**纯加表**，不动任何已有表，因此不会丢数据。
         * ⚠ 列名用**驼峰**（与本项目其余表一致），不是下划线 ——
         *   Room 按列名比对，写成 `pat_ms` 会与实体对不上而**运行时才报错**。
         * ⚠ `bpId` 是主键（一个读数最多一条配对样本），所以不再单独建索引。
         */
        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `bp_calibration_samples` (" +
                        "`bpId` INTEGER NOT NULL, " +
                        "`pairedAt` INTEGER NOT NULL, " +
                        "`patMs` INTEGER, " +
                        "`patSpreadMs` INTEGER, " +
                        "`hrMean` INTEGER, " +
                        "`hrvRmssdMs` INTEGER, " +
                        "`windowMs` INTEGER NOT NULL, " +
                        "`quality` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`bpId`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_bp_calibration_samples_pairedAt` " +
                        "ON `bp_calibration_samples` (`pairedAt`)"
                )
            }
        }

        /**
         * v19 → v20：血压记录与配对样本各加一列**测量体位**（2026-10-03）。
         *
         * ## 为什么两列都要加
         *
         * 体位属于**"那次测量"**，所以真源在 `blood_pressures`；
         * 而配对样本要在"训练时按体位筛选"，自己也得有一份 —— 配对是**事后**发生的
         * （可能几天后回填），届时再去 join 读数表只会多一次查询、不解决任何问题。
         *
         * ⚠ 两列都**可空**：老数据没有；用户没选时也为 null。**null 不等于坐姿** ——
         *   分层的前提是"这一组体位确定是同一个"，未知混进去就破坏了前提。
         *
         * ⚠ 用 `ALTER TABLE ADD COLUMN` **原地加**，不重建表：可空、无默认值，
         *   SQLite 允许直接追加到表尾。列落在表尾、与实体声明顺序不一致**没关系**
         *   （Room 按**列名**比对，不按位置）。
         * ⚠ 列名必须是**驼峰** `posture`。写成别的形式时**编译期一切正常**，
         *   要到**运行期**才因"找不到列"报错。
         */
        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `blood_pressures` ADD COLUMN `posture` TEXT")
                db.execSQL("ALTER TABLE `bp_calibration_samples` ADD COLUMN `posture` TEXT")
            }
        }

        /**
         * v20 → v21：`sleep_nights` 加一列**睡眠阶段段**（2026-10-05）。
         *
         * ## 为什么原来没加、现在必须加
         *
         * `SleepNightEntity` 的 KDoc 原先写着"阶段段每次现读即可，没有落库的必要"。
         * 那条判断**只对"读得到"成立**，而阶段段读不到 ——
         * `readSleepBundle` 的窗口是 `[昨天18:00, 现在]`，**过了今晚就永远取不回来**。
         * 后果：「睡眠阶段」详情页永远只能看今晚，历史趋势一晚素材都没有。
         *
         * 判据与 `sleep_odi` 完全同源（见 `SamsungHealthReader` 第 ⑧ 步）：
         * **"过期即永失"的输入必须落库**。
         *
         * ⚠ **旧行留 NULL**（不是空串）：NULL 明确表示"那晚没存过阶段"，
         *   界面要据此区分"历史没记"与"那晚没睡好"。用空串会让两者混成一个。
         *
         * ⚠ 用 `ALTER TABLE ADD COLUMN` **原地加**，可空、无默认值 ⇒ 不重写数据。
         *   列落在表尾、与实体声明顺序不一致**没关系**（Room 按**列名**比对）。
         * ⚠ 列名必须是**驼峰** `stagesJson`。写错形式时**编译期一切正常**，
         *   要到**运行期**才因"找不到列"报错。
         */
        private val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sleep_nights` ADD COLUMN `stagesJson` TEXT")
            }
        }

        /**
         * v21 -> v22：新增「每天的每小时心率/血氧桶」表（2026-10-13）。
         *
         * ⚠ 与 [MIGRATION_5_6] 等一样是**纯加表**，不动任何已有表，因此不会丢数据。
         *
         * 为什么要有它：用户要求「每天跟三星同步后数据都留在本地，访问历史先从本地读，
         * 本地没有再去读三星」。此前心率/血氧两张点表只覆盖"今天"，翻历史几乎全靠现读。
         *
         * ⚠⚠ **复合主键**（`dateEpochDay` + `kind`）⇒ 建表语句里没有 `AUTOINCREMENT`，
         *   末句是 `PRIMARY KEY(\`dateEpochDay\`, \`kind\`)`。列顺序必须与实体声明顺序一字不差，
         *   改完跑 `迁移比对脚本 21 22` 逐字节比对。
         */
        private val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `daily_vitals` (" +
                        "`dateEpochDay` INTEGER NOT NULL, `kind` TEXT NOT NULL, " +
                        "`buckets` TEXT NOT NULL, `minValue` INTEGER, " +
                        "`maxValue` INTEGER, `avgValue` INTEGER, " +
                        "`sampleCount` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`dateEpochDay`, `kind`))"
                )
            }
        }

        /**
         * v22 → v23：给 11 张表补**时间序索引**与**待同步索引**（2026-11-03，性能修复）。
         *
         * ## 为什么必须补
         * 此前每张表只有 `(source, measuredAt)` 复合唯一索引。而 DAO 里最热的查询是
         * `ORDER BY measuredAt DESC LIMIT 1`（每次开页取最新）与
         * `WHERE synced=0 ORDER BY measuredAt ASC LIMIT n`（同步时取待上传）——
         * 这两类查询**都不带 `source` 等值条件**，复合索引的第一列用不上 ⇒
         * SQLite 只能**全表扫描 + 临时 B 树排序**。心率/血氧是按小时落的，
         * 一年就是几十万行，开页延迟随数据量线性增长。
         *
         * 一个时间序索引同时服务"取最新"和"按区间读历史"；`(synced, 时间列)`
         * 服务"待同步"（等值 + 排序两段都能走索引，不需要再排序）。
         *
         * ## 安全性
         * `CREATE INDEX` 只读表、只写索引页，**不动任何行**（不用重建表）；
         * `IF NOT EXISTS` 保证重复执行幂等。索引会让写入略慢一点点，
         * 但这些表的写入频率是"分钟级"，可以忽略。
         *
         * ⚠ 索引名必须与 Room 的生成规则一致（`index_<表>_<列,下划线连接>`），
         *   否则它的 schema 校验会发现"实体声明的索引在库里不存在"而抛异常。
         */
        private val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 表名 -> 时间列名。列名用**驼峰**（与本项目全部表一致）。
                val timeColumns = listOf(
                    "heart_rates" to "measuredAt",
                    "blood_pressures" to "recordedAt",
                    "spo2" to "measuredAt",
                    "temperatures" to "measuredAt",
                    "ecg_waves" to "measuredAt",
                    "body_compositions" to "measuredAt",
                    "ecg_records" to "recordedAt",
                    "activities" to "startedAt",
                    "ppg_waves" to "measuredAt",
                    "hrv_sessions" to "measuredAt",
                    "mf_bia_records" to "measuredAt",
                )
                for ((table, col) in timeColumns) {
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_${table}_$col` ON `$table` (`$col`)"
                    )
                    // `ecg_waves` 是纯本机表（没有 synced 列），跳过待同步索引
                    if (table != "ecg_waves") {
                        db.execSQL(
                            "CREATE INDEX IF NOT EXISTS `index_${table}_synced_$col` " +
                                "ON `$table` (`synced`, `$col`)"
                        )
                    }
                }
            }
        }

        const val DB_NAME = "gemini_health_bridge.db"

        /**
         * 必须与上面的 `@Database(version = ...)` 保持一致 —— 改版本号时**两处一起改**。
         *
         * 之所以复制一份常量而不是读注解：要在**打开数据库之前**拿它跟磁盘上的真实版本比对，
         * 注解值在那个时点拿不到（Room 的注解处理在编译期，运行期只能自己声明）。
         */
        const val DB_VERSION = 23

        /** 升级前快照的存放目录：`filesDir/db_snapshots/`。 */
        const val SNAPSHOT_DIR = "db_snapshots"

        /** 最多保留几份快照，超出按时间从最旧的开始删。 */
        private const val SNAPSHOT_KEEP = 5

        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: run {
                    // 必须赶在 databaseBuilder 之前跑：一旦 Room 开始打开库，迁移就已经执行完了。
                    snapshotBeforeUpgrade(context.applicationContext)
                    Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DB_NAME,
                    )
                        .addMigrations(
                            MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                            MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                            MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                            MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                            MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20, MIGRATION_20_21,
                            MIGRATION_21_22, MIGRATION_22_23,
                        )
                        // 只保留**降级**方向的破坏性兜底（用户装回旧版本 APK 时，磁盘上的表反而更新，
                        // 无法迁移，只能重建）。
                        //
                        // 升级方向**刻意不兜底**。这里原来写的是无参数的 fallbackToDestructiveMigration()，
                        // 含义是：以后任何一次升版本只要漏写一个 Migration，Room 就**静默删库重建** ——
                        // 不报错、不提示，用户只会发现"记录全没了"。宁可当场抛异常（立刻能发现、能修），
                        // 也不接受无声地毁掉全部历史数据。再叠加 snapshotBeforeUpgrade() 留的实物快照。
                        .fallbackToDestructiveMigrationOnDowngrade()
                        .build()
                        .also { instance = it }
                }
            }

        /**
         * 若磁盘上的数据库版本与代码里的 [DB_VERSION] 不同（意味着这次启动会执行迁移），
         * 先把数据库文件整体复制一份出来，再做别的事。
         *
         * 为什么需要：迁移是会失败的 —— 漏写 Migration、迁移 SQL 写错、迁移途中进程被杀。
         * 有了快照，最坏情况也只是丢最近几条，实物一定还在。
         *
         * 整体包在 runCatching 里：快照只是"保险"，**绝不能因为它自己出错而让 App 打不开**。
         */
        private fun snapshotBeforeUpgrade(context: Context) {
            runCatching {
                val dir = context.getDatabasePath(DB_NAME).parentFile ?: return@runCatching
                val dbFile = java.io.File(dir, DB_NAME)
                if (!dbFile.exists()) return@runCatching                    // 全新安装，没有可备份的东西
                val onDisk = readUserVersion(dbFile)
                if (onDisk <= 0 || onDisk == DB_VERSION) return@runCatching // 版本一致 → 不会触发迁移
                val outDir = java.io.File(context.filesDir, SNAPSHOT_DIR).apply { mkdirs() }
                val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                    .format(java.util.Date())
                // 时间戳放最前面：这样按文件名字典序排就等于按时间排，裁剪快照时不会排错。
                val tag = "$stamp-from-v$onDisk"
                // 必须连 -wal / -shm 一起复制：只拷主文件会丢掉还在 WAL 里、尚未回写的已提交事务。
                for (suffix in listOf("", "-wal", "-shm")) {
                    val src = java.io.File(dir, DB_NAME + suffix)
                    if (src.exists()) {
                        src.copyTo(java.io.File(outDir, "$DB_NAME.$tag$suffix"), overwrite = true)
                    }
                }
                pruneSnapshots(outDir)
            }
        }

        /**
         * 读 SQLite 文件头里的 `user_version`（偏移 60，4 字节大端）。
         *
         * 只读前 100 字节的文件头，**刻意不打开数据库** —— 打开就会触发迁移，
         * 那样就失去了"迁移前快照"的全部意义。读不到（空文件/损坏/非 SQLite）统一按 0 处理，
         * 0 会在上面被当成"无从判断"而跳过快照，绝不会误判成"需要迁移"。
         */
        private fun readUserVersion(dbFile: java.io.File): Int = runCatching {
            java.io.RandomAccessFile(dbFile, "r").use { raf ->
                if (raf.length() < 64L) return@use 0
                raf.seek(60)
                val b = ByteArray(4)
                raf.readFully(b)
                ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or
                    ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
            }
        }.getOrDefault(0)

        /** 快照按"标签"分组，只保留最近 [SNAPSHOT_KEEP] 份，其余连 -wal / -shm 一起删。 */
        private fun pruneSnapshots(dir: java.io.File) {
            runCatching {
                val files = dir.listFiles() ?: return@runCatching
                val tags = files.mapNotNull { f ->
                    val rest = f.name.removePrefix("$DB_NAME.")
                    if (rest == f.name) null                                  // 不是快照文件，不动
                    else rest.removeSuffix("-wal").removeSuffix("-shm")
                }.distinct().sortedDescending()
                if (tags.size <= SNAPSHOT_KEEP) return@runCatching
                for (t in tags.drop(SNAPSHOT_KEEP)) {
                    for (suffix in listOf("", "-wal", "-shm")) {
                        java.io.File(dir, "$DB_NAME.$t$suffix").delete()
                    }
                }
            }
        }

        /** 列出已有的升级前快照（最新在前），供「数据备份」页展示。 */
        fun snapshots(context: Context): List<java.io.File> =
            runCatching {
                java.io.File(context.filesDir, SNAPSHOT_DIR).listFiles()
                    ?.filter {
                        it.name.startsWith("$DB_NAME.") &&
                            !it.name.endsWith("-wal") && !it.name.endsWith("-shm")
                    }
                    ?.sortedByDescending { it.name }
                    ?: emptyList()
            }.getOrDefault(emptyList())
    }
}
