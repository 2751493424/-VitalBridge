package com.gemini.healthbridge.wear

import android.app.Application

/**
 * 手表端 Application（模块 #15 语言功能顺带引入，2026-09-29）。
 *
 * ## 为什么之前没有、现在需要
 *
 * 手表端原先**没有自定义 Application** —— 首屏逻辑全在 `MainActivity.onCreate`。
 * 但"首次启动匹配手机语言"必须在界面构建**之前**完成，否则第一帧会用错语言、
 * 再闪一下才切过来。`Application.onCreate` 是进程里最早的钩子，放这里最稳。
 *
 * ## 为什么只做这两件事
 *
 * 其余初始化（数据库、传感器、崩溃上报）现在都挂在 `MainActivity.onCreate` 上，
 * 它们有的依赖 Activity 的 `lifecycleScope`。**不搬动它们** ——
 * 搬迁是有风险的改动，与这两件事无关，不该顺手做。
 *
 * 两件事的共同点是**都必须在任何页面出现之前完成**：语言要在第一帧前定好；
 * 震动器要在任何一次测量结束之前取到（见 [Buzzer]）。
 */
class WearApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 首次启动匹配系统语言（首次会写回偏好，之后只认偏好）。见 LanguageStore 的说明。
        LanguageStore.current(this)
        // 测量结束的震动提示：在这里取一次系统震动器（不持 Activity，见 Buzzer 的说明）。
        // 放 Application.onCreate 是因为所有测量页都在同一个进程里、生命周期都比它短。
        Buzzer.attach(this)
    }
}
