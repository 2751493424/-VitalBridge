package com.gemini.healthbridge.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log

/**
 * `PackageInstaller` 的会话状态回执（2026-10-21）。
 *
 * 为什么不直接 `startActivity(ACTION_VIEW)`：Android 10+ 禁止后台/前台服务直接拉起 Activity，
 * 会被静默拦截（用户上一次就是这么失败的，只能自己去文件管理器点）。
 * `PackageInstaller.commit()` 是系统自己弹确认框，不受这条限制。
 */
class OtaInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // 系统把"确认安装"的 Intent 交回来：能拉就拉（App 在前台时通常允许），
                // 拉不起也不影响系统自己弹框（很多机型在这里已经弹了）。
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                        .onFailure { Log.w("OtaInstall", "确认页拉起失败：${it.javaClass.simpleName}") }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("OtaInstall", "安装成功")
            else -> Log.w("OtaInstall", "安装未完成（$status）：$msg")
        }
    }
}
