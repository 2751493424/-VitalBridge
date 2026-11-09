# 手表端 R8 规则（2026-10-18 首次开启压缩）
#
# 目的：debug 包 7 个 dex 共 33 MB，dex 不压缩 ⇒ APK 35 MB。开启 R8 + shrinkResources 后
# 通常能降到 1/3 左右。规则尽量少而准，**不要**整包 keep（那等于没压）。

# ── kotlinx.serialization ──
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.gemini.healthbridge.**$$serializer { *; }

# ── Room（生成的 *_Impl 通过反射实例化）──
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ── 枚举的 values()/valueOf()（R8 全量模式下会被裁掉，序列化/反射会用到）──
-keepclassmembers enum * { *; }

# ── 崩溃栈可读性（保留行号，便于用户截图定位）──
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
