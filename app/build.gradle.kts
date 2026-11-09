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
    namespace = "com.gemini.healthbridge.phone"
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
        applicationId = "com.vitalbridge.free.phone"
        minSdk = 29
        targetSdk = 36
        // ⚠ 与出包序号保持一致（`脉迹-phone-vNNN.apk`）：用户页「关于」显示 `v1.NNN（NNN）`，
        //   出问题时第一句就能问清"你装的是哪一版"。**applicationId 绝不动**。
        versionCode = 216
        versionName = "1.216"

        /**
         * ⚠⚠ **只打 arm64-v8a**（2026-10-26，随无线 ADB 一起加的）。
         *
         * 原因：`conscrypt-android` 带 4 个 ABI 的 `libconscrypt_jni.so`（共 7.9 MB 未压缩），
         * 而本机是真机 arm64。x86/x86_64 只有模拟器用得上 ⇒ 不打进去，省 5.9 MB。
         * ⚠ 代价：**x86_64 模拟器上「安装」页用不了**（JNI 缺失），真机不受影响。
         */
        ndk { abiFilters += listOf("arm64-v8a") }
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

    androidResources {
        // ⚠ 自带的**手表安装包**（`assets/apk/watch.apk`，4.6 MB）必须**不压缩**：
        //   · `AssetManager.openFd()` 对压缩过的条目直接抛异常；
        //   · 装包时要原样取出字节，压缩一层只是多耗 CPU。
        noCompress += "apk"
    }

    packaging {
        /**
         * 仅**本机 UI 预览**用：`gradle :app:assembleDebug -PpreviewNoNative` 会剔除所有 `.so`。
         *
         * ⚠ 为什么需要：三星 SDK 的 AAR（以及 conscrypt / libadb）只带 **arm64-v8a** 的库，
         *   而本机只有 x86_64 模拟器 ⇒ 正常包装不上（`INSTALL_FAILED_NO_MATCHING_ABIS`），
         *   也就没法在模拟器里看界面。预览只走本地数据库 + Compose，用不到这些 JNI。
         * ⛔ **不传 `-PpreviewNoNative` 时行为与原来完全一致**，出包绝不会带这个开关。
         */
        if (project.hasProperty("previewNoNative")) {
            jniLibs {
                excludes += "**/*.so"
            }
        }
        resources {
            // ⚠ bouncycastle 的 PQC 参数表是**数据文件**，R8 裁不掉，共约 1.2 MB。
            //   本项目的 SPAKE2 只走 P-256，用不到 PQC ⇒ 直接排除。
            excludes += listOf(
                "org/bouncycastle/pqc/**",
                "META-INF/versions/**",
                "META-INF/*.kotlin_module",
            )
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
            // ⚠⚠ 2026-10-18 开启压缩：此前 debug 包 32 MB（多 dex、dex 不压缩）。
            //   R8 + 资源裁剪后约 1/3；规则见 `app/proguard-rules.pro`。
            //   ⚠ 三星 SDK 是本地 AAR、靠类名映射，必须整包 keep —— 否则**授权/读数据时才崩**。
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
    implementation(files("libs/samsung-health-data-api-1.1.0.aar"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation(platform("androidx.compose:compose-bom:2025.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.android.gms:play-services-wearable:20.0.1")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("org.jetbrains.kotlin:kotlin-parcelize-runtime:2.3.21")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // ── 无线 ADB（2026-10-26）────────────────────────────────────────────
    // 用来把**手表**的安装包经 WiFi 装进手表（首次安装只能这么走：表上还没有脉迹，
    // 蓝牙传输也要求表上有 App 在监听，所以两条旧通道都救不了"表是空的"）。
    // · libadb-android：纯 Java 的 ADB 客户端，含 `pair()`（Android 11+ 无线调试配对）
    // · conscrypt-android：配对用的 TLSv1.3（系统 Conscrypt 在 API 28 起是隐藏 API）
    // ⚠ 证书**不用库生成**：Android 上没有公开 API，而 `sun-security-android` 要整包搬
    //   `sun.security.x509` ⇒ 改成**内置一份固定自签证书**（`assets/adb/{adbkey,adbcert}.pem`），
    //   配对身份全局一致，反而更省事。
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
