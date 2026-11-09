package com.gemini.healthbridge.wear

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.samsung.android.service.health.tracking.ConnectionListener
import com.samsung.android.service.health.tracking.HealthTrackerException
import com.samsung.android.service.health.tracking.HealthTrackingService

/**
 * 血氧/心电共用的 HealthTrackingService 连接。
 * 三星示例 App 的用法是同一进程只建一个 HealthTrackingService、按需取不同 Tracker；
 * 之前血氧与心电各自持有一个实例，连接状态互相干扰疑似导致心电闪退，现统一为一个连接。
 * 同一时间只允许一个测量在跑（MainActivity.stopAllMeasurements 保证），回调按发起方分发。
 *
 * ## 为什么必须自己记 `connected`（2026-09-14 修的 bug）
 * SDK 的 [ConnectionListener.onConnectionSuccess] **只在连接状态真正发生变化时回调一次**。
 * 而 `EcgMeasurer.stopMeasurement()` 刻意**保持服务连接**（反复断连重连会让 ECG 会话进入废状态）。
 * 于是第二次测量时 `connectService()` 是空操作 → 不再回调 → `EcgMeasurer.startTracking()`
 * 永远不执行 → tracker 没建、监听没注册、一个采样点都不会来。
 * 现象就是"第一次测量结束或中断后重新测量，传感器不工作"（`measuring` 是 true 但数据恒空）。
 *
 * 修法：自己维护 `connected`，`connect()` 发现已连接时**主动**通知发起方去建 tracker。
 */
class SamsungSensorClient(context: Context) {

    interface Owner {
        fun onConnected() {}
        fun onConnectionEnded() {}
        fun onConnectionFailed(e: HealthTrackerException) {}
    }

    @Volatile
    private var owner: Owner? = null

    /** 服务当前是否已连接。见类注释：不能依赖 SDK 再次回调。 */
    @Volatile
    private var connected = false

    private val mainHandler = Handler(Looper.getMainLooper())

    private val connectionListener = object : ConnectionListener {
        override fun onConnectionSuccess() {
            connected = true
            runCatching { owner?.onConnected() }
        }

        override fun onConnectionEnded() {
            connected = false
            runCatching { owner?.onConnectionEnded() }
        }

        override fun onConnectionFailed(e: HealthTrackerException) {
            connected = false
            runCatching { owner?.onConnectionFailed(e) }
        }
    }

    val service = HealthTrackingService(connectionListener, context.applicationContext)

    val isConnected: Boolean get() = connected

    /**
     * 以 activeOwner 的身份发起连接。
     *
     * - 尚未连接：走正常的 `connectService()`，由 [ConnectionListener] 回调 `onConnected()`。
     * - **已连接**：`connectService()` 不会再次回调，必须主动 post 一次 `onConnected()`，
     *   否则发起方永远不会去建 tracker（这就是重启测量失效的根因）。
     *
     * SDK 调用本身抛异常时返回 false（不杀进程）。
     */
    fun connect(activeOwner: Owner): Boolean = runCatching {
        owner = activeOwner
        if (connected) {
            // post 到主线程，与正常回调路径的线程保持一致；
            // 执行时再确认 owner 没被换掉，避免误唤醒已经放弃的会话。
            mainHandler.post {
                if (owner === activeOwner) runCatching { activeOwner.onConnected() }
            }
        } else {
            service.connectService()
        }
        true
    }.getOrDefault(false)

    fun disconnect() {
        runCatching { service.disconnectService() }
        // 主动置 false：disconnectService() 的回调是异步的，
        // 若紧接着又要 reconnect，不能靠回调及时把标志复位。
        connected = false
        owner = null
    }
}
