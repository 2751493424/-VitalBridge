package com.gemini.healthbridge.phone

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Health Connect 权限说明页。
 * 声明此页后，应用才会正确出现在「健康数据共享」（Health Connect）的应用列表中。
 *
 * ⚠ 文案全部走资源（模块 #15）—— 它出现在**系统权限页**里，用户切了语言后
 * 这里也必须跟着变，否则是"英文用户看到一页中文授权说明"。
 * ⚠ 中文引号一律用「」，不用 ASCII 直引号（改之前这里是 `\"...\"`，会原样显示直引号）。
 */
class HCRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
            ) {
                Spacer(Modifier.height(40.dp))
                Text(
                    stringResource(R.string.hc_rationale_title),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.hc_rationale_body),
                    fontSize = 15.sp,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.hc_rationale_privacy),
                    fontSize = 13.sp,
                    color = Color(0xFF666666),
                )
            }
        }
    }
}
