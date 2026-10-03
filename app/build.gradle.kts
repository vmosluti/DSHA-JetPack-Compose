@file:Suppress("UnstableApiUsage")

plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.lsplugin.apksign)
    id("kotlin-parcelize")
}

val androidCompileSdkVersion: Int by rootProject.extra
val androidCompileSdkVersionMinor: Int by rootProject.extra
val androidBuildToolsVersion: String by rootProject.extra
val androidMinSdkVersion: Int by rootProject.extra
val androidTargetSdkVersion: Int by rootProject.extra
val androidSourceCompatibility: JavaVersion by rootProject.extra
val androidTargetCompatibility: JavaVersion by rootProject.extra
val managerVersionCode: Int by rootProject.extra
val managerVersionName: String by rootProject.extra

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

// ---- DSHA 资产流水线 ----
// offline-rootfs.bin / dsh-runtime.bin 不在仓库里（DSHA 原工程同样由 tools/*.py 构建期生成）。
//  - 未放入 app/src/main/assets/offline-rootfs.bin：生成精简资产；
//  - 放入 offline-rootfs.bin + dsh-runtime.bin(+.inputs.json)：自动走完整离线 + 应急流水线与校验。
val dshaPython: String = System.getenv("DSHA_PYTHON") ?: "python3"
val dshaAssetsSrc = file("src/main/assets")
val dshaFullOffline = file("src/main/assets/offline-rootfs.bin").isFile
val generatedStandardAssets = layout.buildDirectory.dir("generated/standardAssets")
val generatedRecoveryAssets = layout.buildDirectory.dir("generated/recoveryAssets")
val generatedUiLanguage = layout.buildDirectory.dir("generated/uiLanguage")

android {
    namespace = "com.luti.dshlauncher"
    val isPrBuild = project.findProperty("IS_PR_BUILD")?.toString()?.toBoolean() ?: false

    buildTypes {
        release {
            // DSHA 依赖反射（Shizuku Binder、PTY JNI、RootShellMain 入口），原工程未启用 minify。
            isMinifyEnabled = false
            isShrinkResources = false
            vcsInfo.include = false
            if (isPrBuild) applicationIdSuffix = ".dev"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
        // build-tools 的 aidl 是 x86-64 二进制，Termux(arm64) 无法执行；
        // IShellService 改为手写的生成物 src/main/java/com/deepseekharness/app/IShellService.java。
        aidl = false
        resValues = true
    }

    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
            excludes += "lib/*/libandroidx.graphics.path.so"
            // 应急启动器按签名 APK 中的完整字节校验，禁止打包阶段改写这两份 ELF。
            keepDebugSymbols += listOf("**/libproot.so", "**/libprootloader.so")
            // 使用自带的 16 KB 对齐 libtermux.so，覆盖 terminal-emulator AAR 中的旧库。
            pickFirsts += "**/libtermux.so"
            excludes += listOf("**/x86/**", "**/x86_64/**", "**/armeabi/**", "**/armeabi-v7a/**")
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        generateLocaleConfig = true
        // 'bin' 是离线 rootfs：保持 STORED，避免 aapt 展开 gzip 或二次压缩。
        noCompress += listOf("gz", "xz", "bin", "ja", "tar", "whl")
    }
    compileSdk = androidCompileSdkVersion
    compileSdkMinor = androidCompileSdkVersionMinor
    buildToolsVersion = androidBuildToolsVersion

    defaultConfig {
        applicationId = "com.luti.dshlauncher"
        minSdk = androidMinSdkVersion
        targetSdk = androidTargetSdkVersion
        versionCode = managerVersionCode
        versionName = managerVersionName

        buildConfigField("boolean", "IS_PR_BUILD", isPrBuild.toString())
        // DSHA 标准版（非 Gecko 兼容版）。
        buildConfigField("boolean", "LOW_ANDROID", "false")

        ndk {
            // proot / termux JNI 只提供 arm64。
            abiFilters += "arm64-v8a"
        }
    }

    sourceSets {
        getByName("main") {
            // DSHA 的 XML 资源独立放置；与 Compose 同名的 7 项已从 dsha/res 剔除。
            res.srcDir("src/dsha/res")
            // AGP 9 禁止向 SourceSet 传 Provider；与 DSHA 原工程一致解析为 File。
            // 任务依赖由下方 preBuild / merge*Assets 的 dependsOn 显式保证。
            java.srcDir(generatedUiLanguage.get().asFile)
            assets.setSrcDirs(
                listOf(generatedStandardAssets.get().asFile, generatedRecoveryAssets.get().asFile)
            )
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = false
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = androidSourceCompatibility
        targetCompatibility = androidTargetCompatibility
    }
}

val prepareUiLanguages = tasks.register<Exec>("prepareUiLanguages") {
    inputs.file(rootProject.file("tools/prepare-ui-languages.py"))
    inputs.file(rootProject.file("tools/i18n/messages.json"))
    outputs.dir(generatedUiLanguage)
    commandLine(
        dshaPython, "-B", rootProject.file("tools/prepare-ui-languages.py").absolutePath,
        "--output", generatedUiLanguage.get().asFile.absolutePath,
    )
}

val prepareStandardAssets = tasks.register<Exec>("prepareStandardAssets") {
    inputs.dir(dshaAssetsSrc)
    inputs.file(rootProject.file("tools/prepare-standard-assets.py"))
    inputs.file(rootProject.file("tools/generated_asset_directory.py"))
    outputs.dir(generatedStandardAssets)
    commandLine(
        dshaPython, "-B", rootProject.file("tools/prepare-standard-assets.py").absolutePath,
        "--source", dshaAssetsSrc.absolutePath,
        "--output", generatedStandardAssets.get().asFile.absolutePath,
    )
}

// 应急运行时只在提供离线 rootfs 时生成（需要锁定的 recovery 归档）。
val prepareRecoveryAssets = tasks.register<Exec>("prepareRecoveryAssets") {
    dependsOn(prepareStandardAssets)
    onlyIf { dshaFullOffline }
    isIgnoreExitValue = true
    inputs.file(rootProject.file("tools/prepare-recovery-assets.py"))
    inputs.file(rootProject.file("tools/recovery-runtime/lock.json"))
    outputs.dir(generatedRecoveryAssets)
    commandLine(
        dshaPython, "-B", rootProject.file("tools/prepare-recovery-assets.py").absolutePath,
        "--output", generatedRecoveryAssets.get().asFile.absolutePath,
        "--shared-assets", generatedStandardAssets.get().asFile.absolutePath,
    )
    doLast {
        if (executionResult.get().exitValue != 0) {
            logger.warn("prepareRecoveryAssets 失败（多半是提取的 0.1.5 归档对不上 0.1.7 应急锁）。跳过应急舱，标准离线环境仍会打进 APK。")
        }
    }
}

if (dshaFullOffline) {
    // 从已发布 APK 提取的离线资产（无 dsh-runtime.inputs.json）无法通过 --check。
    // 仍生成证明/描述符，不把 --check 当构建门禁，避免提取包无法打包。
    val prepareBackupAssets = tasks.register<Exec>("prepareBackupAssets") {
        commandLine(dshaPython, "-B", rootProject.file("tools/prepare-backup-assets.py").absolutePath, "--write")
    }
    val prepareRuntimeDescriptor = tasks.register<Exec>("prepareRuntimeDescriptor") {
        commandLine(dshaPython, "-B", rootProject.file("tools/prepare-runtime-descriptor.py").absolutePath, "--write")
    }
    prepareStandardAssets.configure { dependsOn(prepareBackupAssets, prepareRuntimeDescriptor) }
}

tasks.named("preBuild").configure { dependsOn(prepareUiLanguages, prepareStandardAssets, prepareRecoveryAssets) }
tasks.configureEach {
    if (name.matches(Regex("merge.*Assets"))) dependsOn(prepareStandardAssets, prepareRecoveryAssets)
}

androidComponents {
    onVariants(selector().withBuildType("release")) {
        it.packaging.resources.excludes.addAll(listOf("META-INF/**", "kotlin/**"))
    }
}

base {
    archivesName.set("DshLauncher_${managerVersionName}_${managerVersionCode}")
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)

    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigationevent.compose)

    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.commonmark)
    implementation(libs.commonmark.ext.gfm.tables)
    implementation(libs.commonmark.ext.gfm.strikethrough)
    implementation(libs.commonmark.ext.autolink)
    implementation(libs.commonmark.ext.task.list.items)

    implementation(libs.androidx.webkit)

    implementation(libs.hiddenapibypass)

    implementation(libs.miuix.ui)
    implementation(libs.miuix.icons)
    implementation(libs.miuix.shader)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.blur)

    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)

    implementation(libs.material.kolor)

    // ---- DSHA 引擎依赖（版本与 DSHA-main/app/build.gradle 一致） ----
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("com.termux.termux-app:terminal-view:0.118.0")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.bouncycastle:bcprov-jdk15to18:1.85.2")
    testImplementation("junit:junit:4.13.2")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}