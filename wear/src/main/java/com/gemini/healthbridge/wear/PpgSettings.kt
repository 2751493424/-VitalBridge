package com.gemini.healthbridge.wear

import android.content.Context

/**
 * PPG 原始光信号采集的开关（模块 #8）。
 *
 * ## 为什么这一项**需要**一个开关（其它模块都没有）
 * 前七个模块的采集要么是用户主动点的（心率/血氧/心电/体温/体成分），要么是零成本搭车的
 * （活动：平台传感器、不占三星 SDK 那条单 owner 连接）。PPG 两者都不是：
 * 它**对用户不可见**（总表 #8 写着"无独立入口，后台隐式采集"），却要占用同一个
 * 光学传感器 —— 而血氧测量用的也是这个传感器。
 *
 * 也就是说，这一项是唯一一处"看不见的采集可能影响看得见的功能"。所以：
 *  1. 默认**开启**（模块总表要求后台隐式采集，用户不该为了它做任何操作）；
 *  2. 但必须留一个出口 —— 万一在真机上它影响了血氧读数，用户能自己关掉，
 *     而不是只能卸载或降版本；
 *  3. 采集失败/被 SDK 拒绝时，血氧那条链路**不受影响**，原因只显示在血氧页上。
 *
 * ## 为什么存 SharedPreferences 而不是数据库
 * 与 `BiaProfileStore` 同一条理由：它是**单条配置**，只有一个当前值、写入即覆盖、没有历史。
 * 放进 Room 就要为它建表、写迁移、写 DAO、加进备份，成本远大于收益。
 */
object PpgSettings {

    private const val PREF = "vitaltrace_ppg"
    private const val KEY_ENABLED = "raw_enabled"

    /**
     * 是否在血氧测量期间顺带采集原始光信号。
     *
     * 默认 `true`：模块总表 #8 定位是"后台隐式"，让用户先去找开关就等于没有隐式采集。
     * 这里返回的只是**用户意愿**，真正的可采集性还要看设备能力与 SDK（见 `PpgRecorder.State.supported`）。
     */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }
}
