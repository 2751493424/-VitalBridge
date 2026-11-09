package com.gemini.healthbridge.wear

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 测量结束的震动提示（2026-10-07 用户要求：「功能检测完后会有震动提醒」）。
 *
 * ## 为什么要有它
 *
 * 手表上「**需要用户等一段、且中途不该看屏幕**」的会话有 7 个：心电（按住表冠 30 秒）、
 * 血氧、体温、体成分、多频阻抗、逐拍间期（HRV 光测 2 分钟）、传感器能力探测（约 75 秒）。
 * 它们的共同问题是**跑完没有任何提示** —— 用户要么一直盯着屏幕等（心电还要求手指别乱动），
 * 要么走开再回来，既不知道结束没有、也不知道成功没有。
 *
 * ## 两种模式（⚠ 区分是刻意的，不是装饰）
 *
 * - [ok]   一次短震（约 120ms）：会话跑完**且拿到了结果**。
 * - [fail] 两短震（120ms + 100ms 间隔 + 120ms）：会话结束但**没拿到结果**
 *          （超时 / 信号不足 / 电极离体 / SDK 报错）。
 *
 * ⚠⚠ **为什么必须区分**：只震一次的话，"震了"对用户就等于"测好了"。而失败时他走回来
 * 看到的是一个失败页 —— 那比不震更糟（白等了 30 秒，还以为成功了）。手表屏幕小、用户
 * 多半是抬手瞥一眼，所以「有没有结果」这件事**必须编码进震动本身**。
 * 单次 / 双次比"长震 / 短震"更容易在手腕上分辨，所以选它。
 *
 * ## 接入纪律（⚠ 这是本项目的一处「集合」，加新会话时**别漏**）
 *
 * 只有「需要用户等待」的会话才震。**刻意不震的三类**：
 * - **心率**：连续实时流，根本没有"结束"这回事（只能手动停）；
 * - **三轴活动**：60 秒窗口的后台循环，用户不在界面上等；
 * - **PPG 原始光信号**：搭血氧的车、没有独立界面 —— 血氧那一次震动已经覆盖了用户感知。
 *
 * ⚠⚠ **绝不能放进各 Manager 的 `stopMeasurement()`**：那个方法同时服务三种语义 ——
 * 自然结束、用户手动停止、以及 `stopAllMeasurements()`（开始任一测量前会停掉全部其它会话）。
 * 放进去的后果是：**用户开始测血氧，会因为"心率被停掉"而震一下**。所以落点必须在
 * **各页自己判定"本次会话结束了"的地方**。
 *
 * ⚠ **权限**：`AndroidManifest.xml` 必须声明 `android.permission.VIBRATE`。它是 normal
 * 权限、不需要运行期申请，但**不声明时 `vibrate()` 静默无效**（不报错、不崩溃、什么都不发生）。
 * 与本项目 HC 权限那次的坑同类 —— 表现都是"代码看着没问题，功能就是不工作"。
 */
object Buzzer {

    /** 单次震动的时长。够在手腕上察觉，又不至于打扰。 */
    private const val SHORT_MS = 120L

    /** 两短震之间的间隔：太短会糊成一次长震。 */
    private const val GAP_MS = 100L

    /**
     * Application context 取来的 Vibrator。
     *
     * ⚠ 在 [WearApp.onCreate] 里注入（不持有 Activity —— 那会泄漏，而且这个对象是全局单例、
     * 生命周期比任何页面都长）。
     */
    @Volatile
    private var vibrator: Vibrator? = null

    /**
     * 取系统震动器。幂等：重复调用不重复取。
     *
     * ⚠ 全程 `runCatching`：`getSystemService` 在某些裁剪过的系统上会抛，
     * 而这只是提示功能 —— 绝不能因为"想震一下"把启动搞崩。
     */
    fun attach(context: Context) {
        if (vibrator != null) return
        vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31 起 Vibrator 要从 VibratorManager 取，直接 getSystemService(VIBRATOR_SERVICE)
                // 拿到的是同一个对象但已废弃；这里走新 API，避免以后某版直接返回 null。
                (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                    ?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
    }

    /** 会话跑完**且拿到结果**：一次短震。 */
    fun ok() = buzz(longArrayOf(0L, SHORT_MS))

    /** 会话结束但**没拿到结果**（超时 / 信号不足 / 失败）：两短震。 */
    fun fail() = buzz(longArrayOf(0L, SHORT_MS, GAP_MS, SHORT_MS))

    /**
     * ⚠ 三层兜底，任何一层不满足都**静默跳过**而不是抛：
     * ① 没 attach（不该发生，但别让它崩）；② 设备没有马达（连 `hasVibrator()` 本身都可能抛）；
     * ③ 系统拒绝（权限没声明 / 用户关掉了触感 / 勿扰模式策略）。
     *
     * `repeat = -1`：不循环，只播一次。缺这个参数会变成**一直震**。
     */
    private fun buzz(pattern: LongArray) {
        runCatching {
            val v = vibrator ?: return
            @Suppress("DEPRECATION")
            if (!v.hasVibrator()) return
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        }
    }
}
