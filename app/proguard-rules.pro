# 手机端 R8 规则（2026-10-18 首次开启压缩）
#
# 原则：**只 keep 真正靠反射/SDK 契约的类**，其余交给 R8。
# Compose / AndroidX / Room / kotlinx.serialization / Health Connect 的 AAR 自带 consumer 规则，
# 这里只补本地 AAR（三星）与需要保留元信息的部分。

# ── 元信息 ──
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
# 崩溃栈要能看：文件名 + 行号（R8 默认会抹掉）
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# ── 三星 Health Data SDK（本地 AAR `libs/samsung-health-data-api-1.1.0.aar`）──
# 它内部大量按类名/字段名做映射，缺 keep 会在**授权或读数据时才崩**，不会在启动时暴露。
-keep class com.samsung.android.sdk.health.** { *; }
-dontwarn com.samsung.android.sdk.health.**

# ── Health Connect（服务绑定 + 部分反射）──
-keep class androidx.health.** { *; }
-dontwarn androidx.health.**

# ── Play Services Wearable（手机 ↔ 手表通道）──
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.**

# ── Room 生成的实现类（反射实例化）──
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ── kotlinx.serialization（公告 JSON）──
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── 枚举（全量模式下 values()/valueOf() 会被裁，序列化/诊断要用）──
-keepclassmembers enum * { *; }

# ── 崩溃日志的落盘/读取（自己的类，名字不能变，取证脚本按名查）──
-keep class com.gemini.healthbridge.phone.CrashReporter { *; }
-dontwarn org.jetbrains.annotations.**

# ── 无线 ADB（2026-10-26）：libadb-android + conscrypt ──────────────────
# ⚠⚠ conscrypt 会去摸**平台内置**的 `com.android.org.conscrypt.*`（隐藏 API），
#   R8 找不到这些类会直接**构建失败**（不是运行期问题）：
#   `Missing class com.android.org.conscrypt.SSLParametersImpl`。
#   ⇒ 只能 dontwarn（那几个类在真机上有、在编译期不可见）。
-dontwarn com.android.org.conscrypt.**
-dontwarn org.apache.harmony.xnet.provider.jsse.**
# conscrypt 靠 native 库 + 反射装配，类名/方法不能裁（它自己的 aar 里也有同一条）
-keep class org.conscrypt.** { *; }
# libadb + 配对用的 SPAKE2 都在这个包下（`spake2` 是 `io.github.muntashirakon.crypto.spake2`）
-keep class io.github.muntashirakon.** { *; }
