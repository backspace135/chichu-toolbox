plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
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
            appendLine("彳亍工具箱 · M1 开源来源")
            appendLine("Shizuku b844bc491f1790c72328e1a8e5b2349f8978f0ea — Apache-2.0")
            appendLine("Shizuku-API a27f6e4151ba7b39965ca47edb2bf0aeed7102e5；api/provider 13.1.5 — MIT")
            appendLine("miuix 0.9.4 — Apache-2.0；compose-miuix-ui contributors")
            appendLine("AndroidX、Compose、Kotlin、coroutines、Gson — Apache-2.0")
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
    licenses.set(rootProject.layout.projectDirectory.dir("third_party/licenses"))
    outputDirectory.set(layout.buildDirectory.dir("generated/openSourceNotices"))
}
androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(generateOpenSourceNotices, GenerateOpenSourceNotices::outputDirectory)
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
    debugImplementation(libs.compose.tooling)
    testImplementation(libs.junit)
}
