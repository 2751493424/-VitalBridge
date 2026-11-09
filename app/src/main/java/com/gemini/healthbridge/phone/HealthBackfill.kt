package com.gemini.healthbridge.phone

import android.content.Context
import com.gemini.healthbridge.core.Backfill
import com.gemini.healthbridge.core.ResText
import com.gemini.healthbridge.core.Hrv
import com.gemini.healthbridge.core.db.AppDatabase
import com.gemini.healthbridge.core.db.BodyCompositionEntity
import com.gemini.healthbridge.core.db.rrList

/**
 * 数据回填：把本机库里**我们自己产生的**数据补写进外部健康平台。
 *
 * ## 分工
 *
 * ```
 * AppDatabase ──collect()──▶ Backfill.BackfillData   （纯数据，无 DB 类型）
 *                                  │
 *                         Backfill.plan()              （纯函数：过滤来源 / 校验值域 / 换算单位 / 截断）
 *                                  │
 *                                  ▼
 *                         HealthConnectManager          （只负责变成平台记录并写，没有任何 if）
 * ```
 *
 * 这一层的职责只有一件：**从 DAO 读出来、组装成纯数据**。
 * 所有判断都在 `core/Backfill`（有 95 条离线断言），所有 IO 都在
 * `HealthConnectManager`。夹在中间的这里**不该有任何业务判断** ——
 * 一旦这里出现 `if (值合法)` 之类，那些判断就离开了台架的覆盖范围。
 *
 * ## 为什么一次回填是「可以重复点」的
 *
 * 每条记录带稳定的幂等键（`Backfill.clientId`），写入前查重。
 * 所以用户点两次不会写两份，补装了新版再点一次也只补新的那些。
 */
object HealthBackfill {

    /**
     * 每类最多读这么多条进来做计划。
     *
     * 比 `Backfill.MAX_PER_KIND`（真正会写的上限）大若干倍，是为了让界面上的
     * 「跳过 N 条」有意义 —— 读太少的话跳过计数只反映"我读了这么点"，
     * 而不是"库里有多少不属于我们"。
     */
    private const val SCAN_LIMIT = 2000

    /**
     * 从本机库读数据，组装成 [Backfill.BackfillData]。
     *
     * ⚠ 这里**不做任何过滤**（连三星来源也不滤）—— 过滤是 `Backfill.plan` 的职责，
     * 而且它要把"过滤掉多少"报上来给界面。在这一层先滤一遍会让那个计数恒为 0，
     * 界面就再也没法回答"我明明有 300 条，为什么只写了 12 条"。
     */
    suspend fun collect(db: AppDatabase): Backfill.BackfillData {
        val hrs = db.heartRateDao().recentOnce(SCAN_LIMIT)
        val spo2 = db.spo2Dao().recentOnce(SCAN_LIMIT)

        // HRV：表里**只存原始逐拍间期**（派生量不落库的纪律），所以 RMSSD 要**现算**。
        // 现算的好处正是这条纪律想要的：界面上显示的数与回填上去的数是同一套算法算的，
        // 不会出现"库里存着用旧算法算出的历史值"。
        // ⚠ `analyze` 会拒答（有效心搏不足 / 伪迹过多 / 信号本身不达标），拒答时**不写** ——
        //   不能拿 0 顶上（0 的含义是"整段心率一个值"，那是采集异常）。
        val hrv = db.hrvDao().recentOnce(SCAN_LIMIT).mapNotNull { e ->
            val r = Hrv.analyze(
                rawRrMs = e.rrList(),
                source = e.source,
                activityLevel = e.activityLevel,
                ecgRegular = e.ecgRegular?.let { it != 0 },
            )
            (r as? Hrv.Result.Ok)?.let { Backfill.Sample(e.source, e.measuredAt, it.rmssdMs) }
        }

        val bia = db.bodyCompositionDao().recentOnce(SCAN_LIMIT)

        fun biaSamples(sel: (BodyCompositionEntity) -> Float?): List<Backfill.Sample> =
            bia.mapNotNull { e ->
                sel(e)?.let { Backfill.Sample(e.source, e.measuredAt, it.toDouble()) }
            }

        // ⚠ 身高**只取最新一条**。它是用户资料、不是每次测量都变的量，
        //   而 BIA 每次测量都会把当时的身高带上（`heightCm`）——
        //   照原样全写会在 HC 里堆出一长串"同一身高"的记录，
        //   在平台的"身高"曲线上表现为一串完全重合的点。
        //   （体重不同：它确实会变，每次写一条是对的。）
        val height = bia.firstOrNull { (it.heightCm ?: 0f) > 0f }
            ?.let { listOf(Backfill.Sample(it.source, it.measuredAt, it.heightCm!!.toDouble())) }
            .orEmpty()

        return Backfill.BackfillData(
            heartRate = hrs.map { Backfill.Sample(it.source, it.measuredAt, it.bpm) },
            spo2 = spo2.map { Backfill.Sample(it.source, it.measuredAt, it.percent.toDouble()) },
            hrvRmssd = hrv,
            bodyFat = biaSamples { it.bodyFatRatio },
            leanMass = biaSamples { it.fatFreeMass },
            weight = biaSamples { it.weightKg },
            height = height,
            bmr = biaSamples { it.basalMetabolicRate },
        )
    }

    /**
     * 回填到 Health Connect。
     *
     * @return 逐类结果（含"跳过多少、为什么跳过"）。平台不可用或没授权时
     *   [HcBackfillReport.error] 非空、[HcBackfillReport.kinds] 为空 ——
     *   调用方**必须把 error 显示出来**，不能当成"没有数据可写"。
     */
    suspend fun toHealthConnect(context: Context, db: AppDatabase): HcBackfillReport {
        val data = runCatching { collect(db) }.getOrElse {
            return HcBackfillReport(emptyList(), ResText.of(R.string.s_b04441b5, it.javaClass.simpleName, it.message))
        }
        return HealthConnectManager.backfill(context, Backfill.plan(data))
    }
}
