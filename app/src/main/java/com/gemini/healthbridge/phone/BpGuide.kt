package com.gemini.healthbridge.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gemini.healthbridge.core.BpPredict
import com.gemini.healthbridge.phone.ui.AppOutlineButton

/**
 * 血压预估的**使用说明向导**（2026-11-04 用户要求）。
 *
 * ## 为什么需要它
 *
 * 这个功能的门槛不在界面上，而在**测量纪律**上：要有一组「袖套读数 + 同时段心电」
 * 才算一组样本；一天录太多、间隔太近，录了也白录；量之前还要静坐、别跷腿、袖带同高。
 * 这些规则原来只写在「数据口径」那个折叠块里 —— 用户看不到，就会照着直觉做，
 * 然后发现"录了十几次还是没有预估值"。
 *
 * ## 触发时机（用户明确指定）
 *
 * - **还没攒够 [BpPredict.MIN_PAIRED_GROUPS] 组** ⇒ 第一次进入血压页时自动弹一次；
 * - **已经攒够** ⇒ 改为「本轮（[BpPredict.CALIB_CYCLE_DAYS] 天）需要补
 *   [BpPredict.CALIB_CYCLE_TARGET] 组」时弹，**每个周期最多一次**。
 *
 * ⚠ 两句都在 [BpGuidePrefs.shouldAutoShow] 里，界面只管问它要不要弹 ——
 *   把判据散在界面里，下次改规则一定会漏掉一处。
 * ⚠ 页内始终留一个「使用指南」入口（用户要求能随时回看），不依赖自动弹。
 */
object BpGuidePrefs {

    private const val PREF = "bp_guide"

    /** 未攒够组数时那次"开场说明"是否已经弹过。 */
    private const val K_INTRO = "intro_shown"

    /** 上一次"该补样本了"提醒的时间戳（毫秒）。 */
    private const val K_TOPUP_AT = "topup_shown_at"

    private fun p(ctx: android.content.Context) =
        ctx.getSharedPreferences(PREF, android.content.Context.MODE_PRIVATE)

    /**
     * 现在要不要**自动**弹向导。
     *
     * @param totalPairs 累计配对组数（`BpPredict.Progress.totalPairs`）
     * @param pairsInCycle 本轮周期内的组数（`BpPredict.Progress.pairsInCycle`）
     */
    fun shouldAutoShow(
        ctx: android.content.Context,
        totalPairs: Int,
        pairsInCycle: Int,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val sp = p(ctx)
        if (totalPairs < BpPredict.MIN_PAIRED_GROUPS) {
            // 还没出过任何预估值 ⇒ 讲一次"怎么才能有数"。
            // ⚠ 只讲一次：反复弹会把用户逼成一个"看到就关"的习惯。
            return !sp.getBoolean(K_INTRO, false)
        }
        // 已经会出数了 ⇒ 只在"该补了"的时候提醒。
        if (pairsInCycle >= BpPredict.CALIB_CYCLE_TARGET) return false
        val last = sp.getLong(K_TOPUP_AT, 0L)
        // ⚠ 用一个完整周期做冷却，而不是"每次进入都弹"：
        //   用户没补样本是他的选择，天天弹只会让他关掉这个功能。
        //   一个周期后再提醒一次，节奏和"身体会变"这件事本身对齐。
        return nowMs - last >= BpPredict.CALIB_CYCLE_DAYS * 86_400_000L
    }

    /** 记下"已经弹过了"。⚠ 只在**真的弹出来**之后调用，否则等于把这次提醒吞掉。 */
    fun markShown(ctx: android.content.Context, totalPairs: Int, nowMs: Long = System.currentTimeMillis()) {
        val e = p(ctx).edit().putLong(K_TOPUP_AT, nowMs)
        if (totalPairs < BpPredict.MIN_PAIRED_GROUPS) e.putBoolean(K_INTRO, true)
        e.apply()
    }
}

/**
 * 4 步说明对话框。
 *
 * ⚠ 正文用**真实换行**分行（第 4 步有 5 条要点），别用 `\n` 字面量拼在一个 Text 里；
 *   也**不做**成一段长文 —— 用户是在手表旁边站着看的，扫读优先。
 */
@Composable
fun BpGuideDialog(onDismiss: () -> Unit) {
    var index by rememberSaveable { mutableIntStateOf(0) }

    val pages = listOf(
        stringResource(R.string.s_ebcc53b4) to
            stringResource(R.string.s_1c2c71fb),
        stringResource(R.string.s_a7ee92c9, BpPredict.MIN_PAIRED_GROUPS.toString()) to
            stringResource(
                R.string.s_16b709fb,
                BpPredict.MIN_PAIRED_GROUPS.toString(),
                BpPredict.CALIB_CYCLE_DAYS.toString(),
                BpPredict.CALIB_CYCLE_TARGET.toString(),
            ),
        stringResource(R.string.s_342fea22, SyncRepository.MAX_MANUAL_PER_DAY.toString()) to
            stringResource(
                R.string.s_e58ac8ab,
                SyncRepository.MAX_MANUAL_PER_DAY.toString(),
                (SyncRepository.MIN_MANUAL_GAP_MS / 60_000L).toString(),
            ),
        stringResource(R.string.s_1c789b71) to
            stringResource(R.string.s_f8018281),
    )
    // ⚠ 夹一下：`index` 可能来自旧进程的 `rememberSaveable` 恢复（步骤数以后若减少，
    //   不夹会数组越界 —— 那是崩溃，不是"多一步"）。
    val cur = index.coerceIn(0, pages.lastIndex)
    val (title, body) = pages[cur]
    val isLast = cur == pages.lastIndex

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(
                        R.string.s_214a6718,
                        (cur + 1).toString(),
                        pages.size.toString(),
                    ),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            // ⚠ `AlertDialog` 的 `text` 槽内部是 **Box（堆叠）**，多个子项平级传进去会重叠；
            //   而且第 4 步正文很长，小屏上必须**可滚动**，否则最后一两句永远看不到。
            Text(
                body,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .verticalScroll(rememberScrollState()),
            )
        },
        confirmButton = {
            AppOutlineButton(
                onClick = {
                    if (isLast) onDismiss() else index = cur + 1
                },
            ) {
                Text(
                    if (isLast) stringResource(R.string.s_d278f148)
                    else stringResource(R.string.s_1497f4c8),
                )
            }
        },
        dismissButton = {
            // 第一步给「跳过」（用户可能是误入，逼他看四页只会被强杀），
            // 之后给「上一步」。代价是多一个按钮，好处是两条路都通。
            if (cur > 0) {
                AppOutlineButton(onClick = { index = cur - 1 }) {
                    Text(stringResource(R.string.s_1497b1bf))
                }
            } else {
                AppOutlineButton(onClick = onDismiss) {
                    Text(stringResource(R.string.s_00493320))
                }
            }
        },
    )
}
