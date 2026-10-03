@file:Suppress("UnstableApiUsage")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 构建环境（IDE/init 脚本）若在项目级注入仓库，默认会让这里的配置整体失效（jitpack 被忽略）。
    // PREFER_SETTINGS：始终以本文件为准。
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        mavenLocal()
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")
        google()
        mavenCentral()
        // DSHA 终端：com.termux.termux-app:terminal-view / terminal-emulator 只在 JitPack 发布
        maven("https://jitpack.io") {
            content {
                includeGroupByRegex("com\\.termux.*")
                includeGroupByRegex("com\\.github\\..*")
            }
        }
    }
}

rootProject.name = "KernelSUStyleUIKit"
include(":app")