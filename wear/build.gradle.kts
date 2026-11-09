import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/**
 * 签名配置：从仓库根目录的 `keystore.properties` 读（该文件不进 git，见 `.gitignore`）。
 * ⚠ 文件缺失时 `hasReleaseKey = false` ⇒ 一切按老样子用 debug 签名，构建不会因此失败。
 */
val ksProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = ksProps.getProperty("storeFile")?.let { file(it).exists() } == true

android {
    namespace = "com.gemini.healthbridge.wear"
    compileSdk = 36

    defaultConfig {

        /**
         * ⚠⚠⚠ **applicationId 一旦改动 = 变成另一个 App**（2026-10-16 由
         * `com.gemini.healthbridge.phone` 改为 `com.vitalbridge.free.wear`）：
         *   · **不能覆盖安装**，旧版必须卸载 —— 而卸载会带走旧包里的一切
         *     （数据库、`vitaltrace_*` 偏好、导出到下载目录的备份文件不受影响）；
         *   · **三星健康 / Health Connect 的授权要全部重走**；
         *   · 手表端同步不受影响（配对走 `"/gemini/health/hello"` 协议路径 + 设备名，与包名无关）。
         *   ⇒ 装新版前先在旧版里「导出备份」，装完再「恢复」。
         *
         * ⚠ 为什么 `.free`：为将来可能存在的付费版留出**共存**空间
         *   （同一台手机可以同时装免费版与付费版）。
         *
         * ⚠ `namespace` **刻意不动**（仍是 `com.gemini.healthbridge.phone`）：
         *   它只决定 Java 包名与 R/BuildConfig 的位置，与安装身份无关；
         *   跟着改要动 521 处 package/import 并搬整个源码目录，全是风险、零收益。
         *   （全仓把本包名当**字符串**用的地方实测 0 处 —— 所以改 applicationId 只动这一行。）
         */
        applicationId = "com.vitalbridge.free.wear"
        minSdk = 30
        targetSdk = 35
        versionCode = 200
        versionName = "1.200"
    }
    // ── 正式签名（2026-10-16 起）──
    //
    // ⚠ 密钥本体放在**仓库之外**（本机私有目录，路径不要写进构建脚本），
    //   路径与口令从根目录的 `keystore.properties` 读 —— 那个文件**不进 git**（见 .gitignore）。
    //   ⇒ 就算有人 `git add -A`，也不会把密钥或口令提交上去。
    //
    // ⚠⚠ `keystore.properties` 或密钥文件**缺失时自动退回 debug 签名**：
    //   换机器 / CI 上仍能编译（不至于因为缺密钥而构建不了），但那种包**不能覆盖安装**正式包。
    //   ⚠ 正式密钥或口令丢失 = **永远无法覆盖安装**，只能让用户卸载重装 ⇒ 离线备份两份。
    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(ksProps.getProperty("storeFile"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        // ⚠ 两种构建都挂正式签名：出给大家的 debug 包与 release 一致，
        //   否则"debug 包能装、release 包装不上"这种坑会在发版那天才炸。
        if (hasReleaseKey) {
            getByName("debug") { signingConfig = signingConfigs.getByName("release") }
            getByName("release") { signingConfig = signingConfigs.getByName("release") }
        }
        release {
            // ⚠⚠ 2026-10-18 开启压缩：此前 `isMinifyEnabled = false` 且发布的是 **debug 包**，
            //   7 个 dex 共 33 MB（dex 在 APK 里不压缩）⇒ 一个手表 App 35 MB，明显异常。
            //   R8 + 资源裁剪后应降到 1/3 左右；规则见 `wear/proguard-rules.pro`（只 keep 必要项）。
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))
    implementation(files("libs/samsung-health-sensor-api-1.4.1.aar"))

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    // 页面切换动效（AnimatedContent / slideIn / fadeIn）——BOM 统一版本
    implementation("androidx.compose.animation:animation")

    implementation("androidx.wear.compose:compose-material:1.6.2")
    implementation("androidx.wear.compose:compose-foundation:1.6.2")

    implementation("androidx.health:health-services-client:1.1.0-rc02")
    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
