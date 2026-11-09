import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.apksign)
}

apksign {
    storeFileProperty = "KEYSTORE_FILE"
    storePasswordProperty = "KEYSTORE_PASSWORD"
    keyAliasProperty = "KEY_ALIAS"
    keyPasswordProperty = "KEY_PASSWORD"
}

android {
    namespace = "com.chichu.toolbox"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.chichu.toolbox"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "com.chichu.toolbox.M3Instrumentation"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging.jniLibs.useLegacyPackaging = true
    dependenciesInfo {
        // AGP's 0x504b4453 dependency block is also rejected by KernelSU's v2-only parser.
        includeInApk = false
        includeInBundle = false
    }
    // KernelSU 730f6446 rejects v3/v3.1 APK signing blocks, even with a matching certificate.
    signingConfigs.configureEach {
        enableV1Signing = false
        enableV2Signing = true
        enableV3Signing = false
        enableV4Signing = false
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

abstract class GenerateOpenSourceNotices : DefaultTask() {
    @get:InputDirectory
    abstract val licenses: DirectoryProperty
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val directory = outputDirectory.get().asFile.apply { mkdirs() }
        directory.resolve("OPEN_SOURCE_NOTICES.txt").writeText(buildString {
            appendLine("彳亍工具箱 · 开源来源")
            appendLine("KernelSU 730f644615969cfd4a84b45d7f62a667b7b95a50 — GPL-3.0-only；JNI/UAPI 派生实现与原版 ksud")
            appendLine("Shizuku b844bc491f1790c72328e1a8e5b2349f8978f0ea — Apache-2.0")
            appendLine("Shizuku-API a27f6e4151ba7b39965ca47edb2bf0aeed7102e5；api/provider 13.1.5 — MIT")
            appendLine("miuix 0.9.4 — Apache-2.0；compose-miuix-ui contributors")
            appendLine("AndroidX、Compose、Kotlin、coroutines、Gson — Apache-2.0")
            appendLine("AndroidHiddenApiBypass 6.1 — Apache-2.0；Copyright (C) 2021-2025 LSPosed")
            appendLine("Bouncy Castle 1.80、BoringSSL 和 libcxx 的许可及第三方声明见以下原文。")
            appendLine("本项目修改：管理器／进程／配置隔离、仅 Shell 入口、一次性授权确认、无线连接取消与日志保护。")
            licenses.get().asFile.listFiles()?.sortedBy { it.name }?.forEach { file ->
                appendLine("\n—— ${file.name} ——\n")
                appendLine(file.readText())
            }
        })
    }
}

val generateOpenSourceNotices = tasks.register<GenerateOpenSourceNotices>("generateOpenSourceNotices") {
    licenses.set(rootProject.layout.projectDirectory.dir("../../third_party/licenses"))
    outputDirectory.set(layout.buildDirectory.dir("generated/openSourceNotices"))
}

abstract class GenerateRootFixtureAssets : DefaultTask() {
    @get:InputDirectory abstract val fixture: DirectoryProperty
    @get:OutputDirectory abstract val output: DirectoryProperty
    @TaskAction fun generate() {
        val root = fixture.get().asFile
        val directory = output.get().asFile.resolve("root-fixture").apply { mkdirs() }
        for (version in 1..2) {
            ZipOutputStream(directory.resolve("chichu-m3-v$version.zip").outputStream()).use { zip ->
                root.walkTopDown().filter { it.isFile && it.name != "README.md" }.forEach { file ->
                    zip.putNextEntry(ZipEntry(file.relativeTo(root).invariantSeparatorsPath))
                    val bytes = if (file.name == "module.prop") file.readText().replace("version=1.0", "version=$version.0")
                        .replace("versionCode=1", "versionCode=$version").toByteArray() else file.readBytes()
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }
    }
}
val generateRootFixtureAssets = tasks.register<GenerateRootFixtureAssets>("generateRootFixtureAssets") {
    fixture.set(rootProject.layout.projectDirectory.dir("testing/root-module"))
    output.set(layout.buildDirectory.dir("generated/rootFixtureAssets"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateOpenSourceNotices, GenerateOpenSourceNotices::outputDirectory)
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(generateRootFixtureAssets, GenerateRootFixtureAssets::output)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:shizuku"))
    implementation(project(":core:ghostlock"))
    implementation(project(":core:ksu"))
    implementation(libs.coroutines.android)
    compileOnly(libs.hidden.stub)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
    implementation(libs.miuix.icons)
    implementation(libs.androidx.webkit)
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
}
