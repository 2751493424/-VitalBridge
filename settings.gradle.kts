pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 无线 ADB（2026-10-26）：`libadb-android` 与 `sun-security-android` 只在 JitPack 上
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "VitalBridge"
include(":app", ":wear", ":core")
