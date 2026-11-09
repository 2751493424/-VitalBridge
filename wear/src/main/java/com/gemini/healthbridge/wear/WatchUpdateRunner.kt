package com.gemini.healthbridge.wear

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * 手表端「检查并安装更新」的**唯一执行者**（2026-10-28）。
 *
 * ## 为什么必须抽成一个对象
 * 触发有两条路：
 * ① 用户在手表设置页点「检查更新」；
 * ② 手机端发现新版本后通过蓝牙下发 [com.gemini.healthbridge.core.FrameKinds.WATCH_UPDATE_REQ]。
 *
 * 两条路必须跑**同一份逻辑、共用同一份状态**，否则会出现两种坏情况：
 * - 手机上点了、手表界面毫无反应（用户会以为没生效）；
 * - 两个更新同时跑，`updateFromWifi` 里"清缓存"和"写 .part"互相踩（真会删掉对方的包）。
 *
 * ## `running` 同时是互斥锁
 * 第二次触发直接返回 `false`（蓝牙那条会回 `busy|`，手动点的那条按着不动），
 * **不排队**：更新是"要么现在装、要么下次再说"，排队只会让用户等两次下载。
 */
object WatchUpdateRunner {

    /** 是否正在更新（手表界面据此禁用按钮 / 显示进度）。 */
    val running = MutableStateFlow(false)

    /** 最近一次（或正在进行中）的状态文案，直接显示在设置页那一行。 */
    val status = MutableStateFlow("")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 开始一次更新。
     *
     * @param onDone 结束后回调**一句人话**（可能成功也可能是失败原因）。
     *   ⚠ 回调在 **IO 线程**触发；调用方若要写蓝牙，必须确保发送是线程安全的
     *     （`BluetoothSync.sendFrame` 已加锁，见那里的注释）。
     * @return `false` = 已有任务在跑，本次没有启动。
     */
    fun start(ctx: Context, onDone: (String) -> Unit = {}): Boolean {
        if (running.value) return false
        running.value = true
        scope.launch {
            val r = runCatching {
                WatchOtaClient.updateFromWifi(ctx) { st, _ -> status.value = st }
            }.getOrElse { "更新失败：${it.message ?: it.javaClass.simpleName}" }
            status.value = r
            running.value = false
            runCatching { onDone(r) }
        }
        return true
    }
}
