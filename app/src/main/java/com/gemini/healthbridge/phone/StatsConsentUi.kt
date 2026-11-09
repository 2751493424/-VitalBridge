package com.gemini.healthbridge.phone

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 首次启动的「匿名使用统计」同意弹窗（2026-10-28）。
 *
 * ## 为什么必须是"先问再采"
 * 应用里全是血压/心率这类数据，用户对"这个 App 会往外发什么"天然敏感。
 * 与其把字段塞进隐私政策里等他去读，不如**第一次启动就问**，并且把"发什么/不发什么"
 * 一次讲完 —— 同意率反而更高，日后也不用为"偷偷采集"解释。
 *
 * ## 三个交互约束
 * ① **点遮罩/返回不关**（`onDismissRequest = {}`）：必须明确选一个，
 *   否则"关掉再说"等于没同意，而代码里没同意就是不采 —— 用户会以为已经在采了。
 * ② 正文**可滚动**：这段文字在小屏上会超过弹窗高度（M3 的 `text` 槽不自动滚）。
 * ③ ⚠⚠ `text` 槽内部是 **Box（堆叠）不是 Column**：这里必须自己包一层 `Column`，
 *   直接把多个 `Text` 平级传进去会**重叠**（静态校验脚本记过这个坑）。
 */
@Composable
fun PrivacyConsentDialog(onDecide: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { /* 刻意不关：必须选「同意并继续」或「暂不」 */ },
        title = { Text(stringResource(R.string.s_f3f01375), fontSize = 17.sp) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    stringResource(R.string.s_9aeadf5e),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDecide(true) }) {
                Text(stringResource(R.string.s_6cdf0a29), fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecide(false) }) {
                Text(stringResource(R.string.s_0034c293), fontSize = 14.sp)
            }
        },
    )
}
