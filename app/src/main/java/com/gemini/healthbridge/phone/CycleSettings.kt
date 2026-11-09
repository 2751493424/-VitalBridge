package com.gemini.healthbridge.phone

import android.content.Context

/**
 * 经期追踪（模块 #13）的开关持久化。
 *
 * 为什么默认关闭、要用户手动开：经期追踪不是每个用这个 App 的人都需要，
 * 健康类 App 默认把它摆在首页不合适。所以「经期」标签页默认不显示，
 * 用户在「设备」页主动开启后才出现（与 PPG 采集的默认开不同 —— 那个是后台隐式行为，
 * 这个是用户可见的一整个标签页，可见性必须由用户决定）。
 *
 * 为什么存 SharedPreferences 而不是数据库：这只是一个布尔开关，
 * 与业务数据（经期记录在 `menstrual_records` 表里）没有事务关系，
 * 不需要 Room 的迁移/备份语义。参考 `PpgSettings` / `BiaProfileStore` 的同类选择。
 */
object CycleSettings {
    private const val PREF = "cycle_settings"
    private const val KEY_ENABLED = "enabled"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, enabled).apply()
    }
}
