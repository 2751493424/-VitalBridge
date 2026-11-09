package com.gemini.healthbridge.phone

import android.app.Application
import android.content.Context
import android.os.Looper
import android.util.Log
import com.gemini.healthbridge.core.db.AppDatabase

class GeminiApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // ⚠⚠ **崩溃要能"说话"**（2026-10-03 加）。
        //
        // 起因：用户报「进血压页闪退」，但没有 adb / logcat 可以拿栈 —— 只能读代码猜，
        // 而**猜了两轮都没猜中**（先怀疑 forEach 里调 @Composable，再把整段包 runCatching，
        // 用户复现时照崩）。没有栈的排查就是在掷骰子。
        //
        // 做法：把最后一次未捕获异常的栈写进 SharedPreferences，下次启动时提示用户，
        // 他截个图就能定位 —— 这是没有调试器的场景下唯一可行的取证方式。
        //
        // ⚠ 必须用 **commit() 同步写**：崩溃后进程立刻被杀，apply() 的异步落盘来不及。
        // ⚠ 只存最近一次（覆盖写）：我们关心的是"刚刚那次怎么崩的"，不是崩溃史。
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            // ⚠⚠ 三星 Health Data SDK 会在**它自己的线程池**里抛空指针
            //   （`com.samsung.android.sdk.health.data.c.a` → `Consumer.accept` on null）。
            //   这类异常不在我们的调用栈里，`try/catch` 接不住；而默认处理是**杀掉整个进程**，
            //   于是用户看到的就是"有时候一打开 App 就弹崩溃"。
            //   处理：只放行「非主线程 + 栈里带三星 SDK 包名」这一类 —— 记日志、不落崩溃记录、不杀进程。
            //   代价是这次三星读取会失败（界面已按"读失败"分支处理），远好于一开就崩。
            val fromSamsungSdk = e.stackTrace.any {
                it.className?.startsWith("com.samsung.android.sdk") == true
            }
            if (fromSamsungSdk && t != Looper.getMainLooper().thread) {
                Log.w(TAG, "已拦截三星 SDK 后台线程异常，App 继续运行", e)
                return@setDefaultUncaughtExceptionHandler
            }
            runCatching {
                getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE).edit()
                    .putString(KEY_LAST_CRASH, Log.getStackTraceString(e).take(4000))
                    .commit()
            }
            prev?.uncaughtException(t, e)
        }

        // 界面语言（模块 #15）：首次启动匹配手机语言并写回偏好，之后只认偏好。
        // ⚠ 放在最前 —— 必须在任何界面构建之前定下语言，否则第一帧会用错语言再闪一下。
        LanguageStore.current(this)
        SyncRepository.init(AppDatabase.get(this))
        // 匿名使用统计的发送端：只捆绑上下文与版本号，**不生成 ID、不发请求**
        // （同意之前不能有任何副作用，见 `Stats` 的注释）。
        Stats.attach(this)
        PhoneBtServer.start(this)
    }

    companion object {
        private const val TAG = "GeminiApp"
        private const val PREF_CRASH = "vitaltrace_crash"
        private const val KEY_LAST_CRASH = "last_crash"

        /**
         * 读出上次崩溃栈并**清掉**（读一次即消费，不会每次启动都提示）。
         *
         * @return 崩溃栈原文；没崩过则 null。
         */
        fun takeLastCrash(ctx: Context): String? {
            val sp = ctx.getSharedPreferences(PREF_CRASH, Context.MODE_PRIVATE)
            val s = sp.getString(KEY_LAST_CRASH, null) ?: return null
            sp.edit().remove(KEY_LAST_CRASH).commit()
            return s
        }
    }
}
