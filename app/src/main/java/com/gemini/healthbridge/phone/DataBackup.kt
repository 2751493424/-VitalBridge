package com.gemini.healthbridge.phone

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.gemini.healthbridge.core.BACKUP_FORMAT
import com.gemini.healthbridge.core.BACKUP_VERSION
import com.gemini.healthbridge.core.BackupFile
import com.gemini.healthbridge.core.BkActivity
import com.gemini.healthbridge.core.BkBloodPressure
import com.gemini.healthbridge.core.BkBodyComposition
import com.gemini.healthbridge.core.BkEcgRecord
import com.gemini.healthbridge.core.BkEcgWave
import com.gemini.healthbridge.core.BkHeartRate
import com.gemini.healthbridge.core.BkHrv
import com.gemini.healthbridge.core.BkCycle
import com.gemini.healthbridge.core.BkApnea
import com.gemini.healthbridge.core.BkEnergyScore
import com.gemini.healthbridge.core.BkMfBia
import com.gemini.healthbridge.core.BkActivityDay
import com.gemini.healthbridge.core.BkDailyVitals
import com.gemini.healthbridge.core.BkSleepNight
import com.gemini.healthbridge.core.BkOdi
import com.gemini.healthbridge.core.BkPpg
import com.gemini.healthbridge.core.BkSpo2
import com.gemini.healthbridge.core.BkTemperature
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.db.ActivityEntity
import com.gemini.healthbridge.core.db.BloodPressureEntity
import com.gemini.healthbridge.core.db.BodyCompositionEntity
import com.gemini.healthbridge.core.db.DailyVitalsEntity
import com.gemini.healthbridge.core.db.EcgRecordEntity
import com.gemini.healthbridge.core.db.EcgWaveEntity
import com.gemini.healthbridge.core.db.HeartRateEntity
import com.gemini.healthbridge.core.db.HrvSessionEntity
import com.gemini.healthbridge.core.db.MenstrualRecordEntity
import com.gemini.healthbridge.core.db.EnergyScoreEntity
import com.gemini.healthbridge.core.db.MfBiaEntity
import com.gemini.healthbridge.core.db.SleepApneaEntity
import com.gemini.healthbridge.core.db.ActivityDayEntity
import com.gemini.healthbridge.core.db.SleepNightEntity
import com.gemini.healthbridge.core.db.SleepOdiEntity
import com.gemini.healthbridge.core.db.PpgWaveEntity
import com.gemini.healthbridge.core.db.SpO2Entity
import com.gemini.healthbridge.core.db.TemperatureEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份的导出与恢复。
 *
 * 存在的理由：本机的数据是**永久保留**的（没有任何定期清理），但"永久"只对"这个 App 一直装着"
 * 成立 —— 数据在内部分区，卸载即整块删除，手表端还显式设了 `allowBackup="false"`，一点后路都没有；
 * 手机端虽然开着系统自动备份，但 Android 的配额只有 25MB，心电波形累积到一千多条就会顶掉，
 * 而且失效时**不会有任何提示**。所以必须有一个用户自己拿得走、看得见的备份。
 *
 * 导出位置固定在 `Download/VitalBridge/`：走系统的分区存储（MediaStore），
 * **不需要任何存储权限**，用户用文件管理器 / 电脑连线都能直接看到并拷走。
 */
object DataBackup {

    /** 导出目录名，位于系统下载目录之下。 */
    const val DIR_NAME = "VitalBridge"

    /** 恢复时给系统文件选择器的类型过滤。 */
    val RESTORE_MIME_TYPES = arrayOf("application/json", "text/plain", "*/*")

    private const val MIME_JSON = "application/json"

    /**
     * 恢复文件的字节上限：64 MB（2026-11-03 加）。
     *
     * 本机全部数据（含心电波形）目前只有几 MB，64 MB 是十倍余量。
     * 上这道闸的原因是 `readBytes()` **没有上限**：用户在文件选择器里误选一个
     * 几百 MB 的文件就会直接 OOM 闪退 —— 而这属于"用户点错了"，不该变成崩溃。
     */
    private const val MAX_RESTORE_BYTES = 64L * 1024 * 1024

    private val json = Json {
        encodeDefaults = true          // 默认值也写出来，备份文件自身可读
        ignoreUnknownKeys = true       // 读未来的、字段更多的备份时不炸
    }

    /**
     * 导出结果。[path] 是给用户看的相对路径，失败时为空。
     *
     * ⚠ [message] 是**待求值**文案（[ResText]）而不是 `String`：它会被原样交给
     * `SyncRepository.setEvent`，而那个状态会一直留在界面上 —— 存已求值的串
     * 会让它切语言后停在旧语言（2026-10-02 用户截图实付）。
     */
    data class ExportResult(val ok: Boolean, val path: String, val message: ResText)

    /**
     * 恢复结果。[added] / [skipped] 分别是写入成功与因重复被挡下的条数。
     *
     * ⚠ [message] 同 [ExportResult.message]：**待求值**文案。
     */
    data class RestoreResult(
        val ok: Boolean,
        val message: ResText,
        val added: Int = 0,
        val skipped: Int = 0,
    )

    /**
     * 把本机各张表（心率/血压/血氧/体温/心电结论/心电波形/体成分/活动/PPG 原始光信号）
     * 整体导出成一个 JSON 文件，落到 `Download/VitalBridge/`。
     *
     * 用 suspend + Dispatchers.IO：一晚上的血氧点级记录加上心电波形可以到几 MB，
     * 在主线程拼 JSON 会直接卡住界面。
     */
    suspend fun export(context: Context): ExportResult = withContext(Dispatchers.IO) {
        runCatching {
            val db = AppDatabase.get(context)
            val hr = db.heartRateDao().allOnce()
            val bp = db.bloodPressureDao().allOnce()
            val spo2 = db.spo2Dao().allOnce()
            val records = db.ecgDao().allOnce()
            val waves = db.ecgWaveDao().allOnce()
            val temps = db.temperatureDao().allOnce()
            val bia = db.bodyCompositionDao().allOnce()
            val acts = db.activityDao().allOnce()
            val ppg = db.ppgWaveDao().allOnce()
            val hrv = db.hrvDao().allOnce()
            val cycle = db.menstrualDao().allOnce()
            val odi = db.sleepOdiDao().allOnce()
            val mf = db.mfBiaDao().allOnce()
            val energy = db.energyScoreDao().allOnce()
            val apnea = db.sleepApneaDao().allOnce()
            val sleepNights = db.sleepNightDao().allOnce()
            val activityDays = db.activityDayDao().allOnce()
            // 每天的心率/血氧每小时桶（v22）。⚠ **这张表必须备份**：它的全部意义就是
            // "数据留在本地、以后从本地读" —— 漏了它，换机后更早的历史永远补不回来。
            val dailyVitals = db.dailyVitalsDao().allOnce()

            val file = BackupFile(
                exportedAt = System.currentTimeMillis(),
                heartRates = hr.map { BkHeartRate(it.bpm, it.measuredAt, it.source, it.synced) },
                bloodPressures = bp.map {
                    BkBloodPressure(it.systolic, it.diastolic, it.pulse, it.note, it.recordedAt, it.source, it.synced, it.posture)
                },
                spo2 = spo2.map { BkSpo2(it.percent, it.heartRate, it.measuredAt, it.source, it.synced) },
                temperatures = temps.map {
                    BkTemperature(it.celsius, it.ambientC, it.measuredAt, it.source, it.synced)
                },
                ecgRecords = records.map {
                    BkEcgRecord(
                        it.classification, it.heartRate, it.note, it.recordedAt, it.source, it.synced,
                        it.patMs, it.patSpreadMs, it.patSeriesMs, it.patNote,
                    )
                },
                ecgWaves = waves.map {
                    BkEcgWave(
                        measuredAt = it.measuredAt, durationMs = it.durationMs,
                        sampleCount = it.sampleCount, minMv = it.minMv, maxMv = it.maxMv,
                        avgMv = it.avgMv, waveJson = it.waveJson, source = it.source,
                        classification = it.classification, analyzedHr = it.analyzedHr,
                        sampleRate = it.sampleRate, regular = it.regular,
                        beatCount = it.beatCount, bandRatio = it.bandRatio,
                    )
                },
                bodyCompositions = bia.map {
                    BkBodyComposition(
                        measuredAt = it.measuredAt, source = it.source,
                        bodyFatRatio = it.bodyFatRatio, bodyFatMass = it.bodyFatMass,
                        totalBodyWater = it.totalBodyWater,
                        skeletalMuscleRatio = it.skeletalMuscleRatio,
                        skeletalMuscleMass = it.skeletalMuscleMass,
                        basalMetabolicRate = it.basalMetabolicRate,
                        fatFreeRatio = it.fatFreeRatio, fatFreeMass = it.fatFreeMass,
                        impedanceMagnitude = it.impedanceMagnitude,
                        impedanceDegree = it.impedanceDegree,
                        weightKg = it.weightKg, heightCm = it.heightCm,
                        gender = it.gender, age = it.age, synced = it.synced,
                    )
                },
                activities = acts.map {
                    BkActivity(
                        startedAt = it.startedAt, durationMs = it.durationMs,
                        sampleCount = it.sampleCount, level = it.level,
                        meanG = it.meanG, stdG = it.stdG, p95G = it.p95G,
                        stillRatio = it.stillRatio, peakJerkG = it.peakJerkG,
                        meanXG = it.meanXG, meanYG = it.meanYG, meanZG = it.meanZG,
                        envelope = it.envelope, source = it.source, synced = it.synced,
                    )
                },
                // PPG 原始光信号：三段波形就是这条记录的本体，必须一起带上
                // （只存 estimatedHr/dcGreen 这类摘要等于把"给自研算法用的输入"丢了）。
                ppgWaves = ppg.map {
                    BkPpg(
                        measuredAt = it.measuredAt, durationMs = it.durationMs,
                        sampleCount = it.sampleCount, sampleRateHz = it.sampleRateHz,
                        channels = it.channels, dcGreen = it.dcGreen,
                        acGreenP2P = it.acGreenP2P, quality = it.quality,
                        estimatedHr = it.estimatedHr, refHr = it.refHr,
                        statusSeen = it.statusSeen,
                        greenWave = it.greenWave, redWave = it.redWave, irWave = it.irWave,
                        source = it.source, synced = it.synced,
                    )
                },
                // HRV：备份的是**逐拍间期**（本表只有输入没有指标），恢复后由 Hrv.analyze 现算，
                // 所以文件里不会出现过期的指标值。
                hrvSessions = hrv.map {
                    BkHrv(
                        measuredAt = it.measuredAt, durationMs = it.durationMs,
                        rrCount = it.rrCount, rrMs = it.rrMs, refHr = it.refHr,
                        ecgRegular = it.ecgRegular, activityLevel = it.activityLevel,
                        source = it.source, synced = it.synced,
                    )
                },
                // 经期（模块 #13）：只备份输入（开始/结束/流量/来源），预测现算。
                cycleRecords = cycle.map {
                    BkCycle(
                        startEpochDay = it.startEpochDay, endEpochDay = it.endEpochDay,
                        flow = it.flow, source = it.source,
                    )
                },
                // 氧减指数摘要：这个**必须**备份 —— 过了那一晚，三星的分钟级血氧序列就取不回来了，
                // 它不像其他表那样"下次同步能补上"（详见 `BkOdi` 的注释）。
                sleepOdi = odi.map {
                    BkOdi(
                        dateEpochDay = it.dateEpochDay, startMs = it.startMs, endMs = it.endMs,
                        events = it.events, odiPerHour = it.odiPerHour,
                        monitoredMinutes = it.monitoredMinutes, validPoints = it.validPoints,
                        rejectedPoints = it.rejectedPoints, lowest = it.lowest, mean = it.mean,
                        t90Pct = it.t90Pct, reason = it.reason, computedAt = it.computedAt,
                    )
                },
                // 多频阻抗：备份的必须是**原始值**（相位角谱与阻抗比都由它现算），
                // 所以这里逐字段搬运，不做任何加工。
                mfBia = mf.map {
                    BkMfBia(
                        measuredAt = it.measuredAt,
                        m5 = it.m5, p5 = it.p5, m10 = it.m10, p10 = it.p10,
                        m50 = it.m50, p50 = it.p50, m250 = it.m250, p250 = it.p250,
                        status = it.status, source = it.source, synced = it.synced,
                    )
                },
                // 能量评分：三星给的原值，逐字段搬。
                energyScores = energy.map {
                    BkEnergyScore(
                        dateEpochDay = it.dateEpochDay,
                        score = it.score,
                        measuredAt = it.measuredAt,
                    )
                },
                // 呼吸暂停：同样逐字段搬（sign 原样是字符串）。
                apneaRecords = apnea.map {
                    BkApnea(
                        startMs = it.startMs,
                        endMs = it.endMs,
                        dateEpochDay = it.dateEpochDay,
                        sign = it.sign,
                    )
                },
                // 每晚睡眠统计（自研能量评分的输入）。recordedAt 不备份 ——
                // 它是"这行什么时候写进来的"，属于本机排查信息，换机后没有意义。
                dailyVitals = dailyVitals.map {
                    BkDailyVitals(
                        dateEpochDay = it.dateEpochDay,
                        kind = it.kind,
                        buckets = it.buckets,
                        minValue = it.minValue,
                        maxValue = it.maxValue,
                        avgValue = it.avgValue,
                        sampleCount = it.sampleCount,
                    )
                },
                sleepNights = sleepNights.map {
                    BkSleepNight(
                        dateEpochDay = it.dateEpochDay,
                        startMs = it.startMs,
                        endMs = it.endMs,
                        actualSleepMs = it.actualSleepMs,
                        hrAvg = it.hrAvg,
                        hrMin = it.hrMin,
                        hrSd = it.hrSd,
                        skinTemp = it.skinTemp,
                        spo2Avg = it.spo2Avg,
                        spo2Min = it.spo2Min,
                        samsungScore = it.samsungScore,
                        stagesJson = it.stagesJson,
                    )
                },
                // 每天的活动量。recordedAt 同样不备份（本机排查信息，换机后没意义）。
                activityDays = activityDays.map {
                    BkActivityDay(
                        dateEpochDay = it.dateEpochDay,
                        steps = it.steps,
                        slotSteps = it.slotSteps,
                        activeKcal = it.activeKcal,
                        activeMs = it.activeMs,
                    )
                },
            )

            val text = json.encodeToString(BackupFile.serializer(), file)
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val name = "脉迹备份-$stamp.json"
            val path = writeToDownloads(context, name, text.toByteArray(Charsets.UTF_8))

            ExportResult(
                ok = true,
                path = path,
                message = ResText.of(R.string.s_d47b7442, file.totalCount) +
                    ResText.of(R.string.s_2526abaa, hr.size, bp.size, spo2.size) +
                    ResText.of(R.string.s_1249982c, temps.size, records.size, waves.size) +
                    ResText.of(R.string.s_813e3f17, bia.size, acts.size, ppg.size) +
                    ResText.of(R.string.s_325190e1, hrv.size, cycle.size, odi.size, mf.size) +
                    ResText.of(R.string.s_7b41b134, energy.size, apnea.size, sleepNights.size) +
                    ResText.of(R.string.s_74821d3b, activityDays.size),
            )
        }.getOrElse {
            // ⚠ 别在字符串模板里嵌 ASCII 引号（会让文案提取器在内层引号处截断）。
            // ⚠ 原来是在**拼好之后** `.trim()`；现在改成 trim 动态参数本身 ——
            //   空白只可能来自异常消息（资源串自己首尾没有空白），而文案是待求值的，
            //   拼好之后再 trim 就需要给 ResText 加一个变体，得不偿失。
            val why = (it.message ?: "").trim()
            ExportResult(false, "", ResText.of(R.string.s_c82b041c, it.javaClass.simpleName, why))
        }
    }

    /**
     * 从用户选中的文件恢复，**增量合并**（不是覆盖）。
     *
     * 靠各表自己的 `(source, measuredAt)` 唯一索引去重：已存在的行被 `OnConflictStrategy.IGNORE`
     * 挡下并返回 -1，所以同一个备份反复导入也是安全的、幂等的。
     */
    suspend fun restore(context: Context, uri: Uri): RestoreResult = withContext(Dispatchers.IO) {
        runCatching {
            val text = readCapped(context, uri, MAX_RESTORE_BYTES)
                ?: return@runCatching RestoreResult(false, ResText.of(R.string.s_ee9ebe3d))

            val file = runCatching { json.decodeFromString(BackupFile.serializer(), text) }
                .getOrElse { return@runCatching RestoreResult(false, ResText.of(R.string.s_8a72c908)) }

            if (file.format != BACKUP_FORMAT) {
                return@runCatching RestoreResult(false, ResText.of(R.string.s_baeb638a))
            }
            if (file.version > BACKUP_VERSION) {
                return@runCatching RestoreResult(
                    false, ResText.of(R.string.s_9c937654, file.version)
                )
            }

            val db = AppDatabase.get(context)
            var added = 0
            var skipped = 0
            // 事务外声明、事务内赋值：结果要在 withTransaction 之后拼进提示文案
            var purged = 0
            // ⚠⚠ 整段导入放进**一个事务**（2026-11-03）：
            //   ① 性能：否则每张表的每条 insert 都是独立事务，几千行 = 几千次 fsync；
            //   ② 一致性：中途失败（文件读一半、某表约束不满足）时不会留下
            //      "只恢复了前几张表"的半截状态，与界面报的条数也对得上。
            //   ⚠ 块内**不能**出现 `return@runCatching` —— 非内联 lambda 里不允许非局部返回。
            db.withTransaction {
            // insertAll 返回每个元素的行号，>0 表示真的插进去了，-1 表示被唯一索引挡下（重复）
            fun tally(ids: List<Long>) {
                for (id in ids) if (id > 0) added++ else skipped++
            }

            tally(
                db.heartRateDao().insertAll(
                    file.heartRates.map {
                        HeartRateEntity(bpm = it.bpm, measuredAt = it.measuredAt,
                            source = it.source, synced = it.synced)
                    }
                )
            )
            tally(
                db.bloodPressureDao().insertAll(
                    file.bloodPressures.map {
                        BloodPressureEntity(systolic = it.systolic, diastolic = it.diastolic,
                            pulse = it.pulse, note = it.note, recordedAt = it.recordedAt,
                            source = it.source, synced = it.synced, posture = it.posture)
                    }
                )
            )
            tally(
                db.spo2Dao().insertAll(
                    file.spo2.map {
                        SpO2Entity(percent = it.percent, heartRate = it.heartRate,
                            measuredAt = it.measuredAt, source = it.source, synced = it.synced)
                    }
                )
            )
            tally(
                db.temperatureDao().insertAll(
                    file.temperatures.map {
                        TemperatureEntity(celsius = it.celsius, ambientC = it.ambientC,
                            measuredAt = it.measuredAt, source = it.source, synced = it.synced)
                    }
                )
            )
            tally(
                db.ecgDao().insertAll(
                    file.ecgRecords.map {
                        EcgRecordEntity(classification = it.classification, heartRate = it.heartRate,
                            note = it.note, recordedAt = it.recordedAt, source = it.source,
                            synced = it.synced, patMs = it.patMs, patSpreadMs = it.patSpreadMs,
                            patSeriesMs = it.patSeriesMs, patNote = it.patNote)
                    }
                )
            )
            tally(
                db.ecgWaveDao().insertAll(
                    file.ecgWaves.map {
                        EcgWaveEntity(
                            measuredAt = it.measuredAt, durationMs = it.durationMs,
                            sampleCount = it.sampleCount, minMv = it.minMv, maxMv = it.maxMv,
                            avgMv = it.avgMv, waveJson = it.waveJson, source = it.source,
                            classification = it.classification, analyzedHr = it.analyzedHr,
                            sampleRate = it.sampleRate, regular = it.regular,
                            beatCount = it.beatCount, bandRatio = it.bandRatio,
                        )
                    }
                )
            )
            tally(
                db.bodyCompositionDao().insertAll(
                    file.bodyCompositions.map {
                        BodyCompositionEntity(
                            bodyFatRatio = it.bodyFatRatio, bodyFatMass = it.bodyFatMass,
                            totalBodyWater = it.totalBodyWater,
                            skeletalMuscleRatio = it.skeletalMuscleRatio,
                            skeletalMuscleMass = it.skeletalMuscleMass,
                            basalMetabolicRate = it.basalMetabolicRate,
                            fatFreeRatio = it.fatFreeRatio, fatFreeMass = it.fatFreeMass,
                            impedanceMagnitude = it.impedanceMagnitude,
                            impedanceDegree = it.impedanceDegree,
                            weightKg = it.weightKg, heightCm = it.heightCm,
                            gender = it.gender, age = it.age,
                            measuredAt = it.measuredAt, source = it.source, synced = it.synced,
                        )
                    }
                )
            )
            // 兜底自愈：备份可能来自血氧单位 bug 修复之前的版本，里面会带着 >100% 的坏值
            purged = runCatching { db.spo2Dao().deleteOutOfRange() }.getOrDefault(0)

            tally(
                db.activityDao().insertAll(
                    file.activities.map {
                        ActivityEntity(
                            startedAt = it.startedAt, durationMs = it.durationMs,
                            sampleCount = it.sampleCount, level = it.level,
                            meanG = it.meanG, stdG = it.stdG, p95G = it.p95G,
                            stillRatio = it.stillRatio, peakJerkG = it.peakJerkG,
                            meanXG = it.meanXG, meanYG = it.meanYG, meanZG = it.meanZG,
                            envelope = it.envelope, source = it.source, synced = it.synced,
                        )
                    }
                )
            )
            tally(
                db.ppgWaveDao().insertAll(
                    file.ppgWaves.map {
                        PpgWaveEntity(
                            measuredAt = it.measuredAt, durationMs = it.durationMs,
                            sampleCount = it.sampleCount, sampleRateHz = it.sampleRateHz,
                            channels = it.channels, dcGreen = it.dcGreen,
                            acGreenP2P = it.acGreenP2P, quality = it.quality,
                            estimatedHr = it.estimatedHr, refHr = it.refHr,
                            statusSeen = it.statusSeen,
                            greenWave = it.greenWave, redWave = it.redWave, irWave = it.irWave,
                            source = it.source, synced = it.synced,
                        )
                    }
                )
            )
            tally(
                db.hrvDao().insertAll(
                    file.hrvSessions.map {
                        HrvSessionEntity(
                            measuredAt = it.measuredAt, durationMs = it.durationMs,
                            rrCount = it.rrCount, rrMs = it.rrMs, refHr = it.refHr,
                            ecgRegular = it.ecgRegular, activityLevel = it.activityLevel,
                            source = it.source, synced = it.synced,
                        )
                    }
                )
            )
            tally(
                db.menstrualDao().insertAll(
                    file.cycleRecords.map {
                        MenstrualRecordEntity(
                            startEpochDay = it.startEpochDay, endEpochDay = it.endEpochDay,
                            flow = it.flow, source = it.source,
                        )
                    }
                )
            )
            // 氧减摘要用 REPLACE 语义（一天一行），所以这里"新增"计数按写入条数算，
            // 不是按"跳过了多少重复" —— 同一天重复恢复只会覆盖成同一份内容。
            tally(
                db.sleepOdiDao().insertAll(
                    file.sleepOdi.map {
                        SleepOdiEntity(
                            dateEpochDay = it.dateEpochDay, startMs = it.startMs, endMs = it.endMs,
                            events = it.events, odiPerHour = it.odiPerHour,
                            monitoredMinutes = it.monitoredMinutes, validPoints = it.validPoints,
                            rejectedPoints = it.rejectedPoints, lowest = it.lowest, mean = it.mean,
                            t90Pct = it.t90Pct, reason = it.reason, computedAt = it.computedAt,
                        )
                    }
                )
            )
            // 多频阻抗：按 (source, measuredAt) 去重，重复恢复不会产生重复行。
            tally(
                db.mfBiaDao().insertAll(
                    file.mfBia.map {
                        MfBiaEntity(
                            measuredAt = it.measuredAt,
                            m5 = it.m5, p5 = it.p5, m10 = it.m10, p10 = it.p10,
                            m50 = it.m50, p50 = it.p50, m250 = it.m250, p250 = it.p250,
                            status = it.status, source = it.source, synced = it.synced,
                        )
                    }
                )
            )

            // 能量评分：按 dateEpochDay 去重（唯一索引），REPLACE 语义。
            tally(
                db.energyScoreDao().insertAll(
                    file.energyScores.map {
                        EnergyScoreEntity(
                            dateEpochDay = it.dateEpochDay,
                            score = it.score,
                            measuredAt = it.measuredAt,
                        )
                    }
                )
            )
            // 呼吸暂停：按 startMs 去重。
            tally(
                db.sleepApneaDao().insertAll(
                    file.apneaRecords.map {
                        SleepApneaEntity(
                            startMs = it.startMs,
                            endMs = it.endMs,
                            dateEpochDay = it.dateEpochDay,
                            sign = it.sign,
                        )
                    }
                )
            )

            // 每晚睡眠统计：按 dateEpochDay 去重（唯一索引），REPLACE 语义。
            // recordedAt 用"现在"：它是排查信息，备份里没有，也不该假装是原来的时刻。
            val restoreNow = System.currentTimeMillis()
            tally(
                db.sleepNightDao().insertAll(
                    file.sleepNights.map {
                        SleepNightEntity(
                            dateEpochDay = it.dateEpochDay,
                            startMs = it.startMs,
                            endMs = it.endMs,
                            actualSleepMs = it.actualSleepMs,
                            hrAvg = it.hrAvg,
                            hrMin = it.hrMin,
                            spo2Avg = it.spo2Avg,
                            spo2Min = it.spo2Min,
                            hrSd = it.hrSd,
                            skinTemp = it.skinTemp,
                            samsungScore = it.samsungScore,
                            stagesJson = it.stagesJson,
                            recordedAt = restoreNow,
                        )
                    }
                )
            )

            // 每天的心率/血氧每小时桶：复合主键是 (dateEpochDay, kind)，REPLACE 语义。
            // ⚠ `updatedAt` 用"现在"：备份里没有它，也不该假装是当时写的那一刻。
            tally(
                db.dailyVitalsDao().insertAll(
                    file.dailyVitals.map {
                        DailyVitalsEntity(
                            dateEpochDay = it.dateEpochDay,
                            kind = it.kind,
                            buckets = it.buckets,
                            minValue = it.minValue,
                            maxValue = it.maxValue,
                            avgValue = it.avgValue,
                            sampleCount = it.sampleCount,
                            updatedAt = restoreNow,
                        )
                    }
                )
            )

            // 每天的活动量：同样按 dateEpochDay 去重（唯一索引），REPLACE 语义。
            tally(
                db.activityDayDao().insertAll(
                    file.activityDays.map {
                        ActivityDayEntity(
                            dateEpochDay = it.dateEpochDay,
                            steps = it.steps,
                            slotSteps = it.slotSteps,
                            activeKcal = it.activeKcal,
                            activeMs = it.activeMs,
                            recordedAt = restoreNow,
                        )
                    }
                )
            )
            }   // end withTransaction

            RestoreResult(
                ok = true,
                message = ResText.of(R.string.s_ef9ff7dd, added, skipped) +
                    if (purged > 0) ResText.of(R.string.s_e668755d, purged) else ResText.EMPTY,
                added = added,
                skipped = skipped,
            )
        }.getOrElse {
            // ⚠ 同 export 的说明：原先是拼好之后 `.trim()`，改成 trim 动态参数。
            val why = (it.message ?: "").trim()
            RestoreResult(false, ResText.of(R.string.s_24202109, it.javaClass.simpleName, why))
        }
    }

    /**
     * 读一个 content URI 的全部文本，最多 [max] 字节。超限或读失败返回 null。
     *
     * ⚠ 不直接用 `readBytes()`：它会把整个文件读进内存，用户误选一个大文件就 OOM。
     *   这里先问 provider 长度（多数支持），再边读边数 —— 不支持长度的那种 provider
     *   也不会绕过上限。
     */
    private fun readCapped(context: Context, uri: Uri, max: Long): String? {
        val declared = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull() ?: -1L
        if (declared > max) return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val initial = if (declared in 1..max) declared.toInt() else 64 * 1024
                val out = java.io.ByteArrayOutputStream(initial)
                val buf = ByteArray(64 * 1024)
                var total = 0L
                var overflow = false
                while (true) {
                    val n = stream.read(buf)
                    if (n < 0) break
                    total += n
                    if (total > max) { overflow = true; break }
                    out.write(buf, 0, n)
                }
                if (overflow) null else out.toString(Charsets.UTF_8.name())
            }
        }.getOrNull()
    }

    /**
     * 写入系统下载目录并返回**用户可见的相对路径**。
     *
     * 用 MediaStore 而不是直接 File 写入：Android 10+ 的分区存储下，直接写公共目录需要
     * `WRITE_EXTERNAL_STORAGE`（且在新系统上形同虚设），而声明 `RELATIVE_PATH` 就够了，
     * **不需要任何权限**。
     */
    private fun writeToDownloads(context: Context, name: String, bytes: ByteArray): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, MIME_JSON)
            put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/" + DIR_NAME,
            )
        }
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values) ?: error(LanguageStore.string(context, R.string.s_a21a3841))
        resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error(LanguageStore.string(context, R.string.s_adec3c40))

        // 重名时系统会自动追加序号改名，所以回查一次真实文件名 ——
        // 否则提示给用户的路径可能根本不存在。一秒内导出两次就能碰到这种情况。
        val real = runCatching {
            resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull() ?: name

        return "${Environment.DIRECTORY_DOWNLOADS}/$DIR_NAME/$real"
    }
}
