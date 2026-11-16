import java.security.MessageDigest

plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.chichu.toolbox.core.ksu"
    compileSdk = 37
    defaultConfig {
        minSdk = 31
        externalNativeBuild.cmake.arguments += "-DANDROID_STL=none"
    }
    ndkVersion = libs.versions.ndk.get()
    externalNativeBuild.cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.31.6"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.coroutines.android)
    implementation(libs.gson)
    testImplementation(libs.junit)
}

abstract class VerifyKsuResources : DefaultTask() {
    @get:InputDirectory
    abstract val binaries: DirectoryProperty

    @TaskAction
    fun verify() {
        val expected = mapOf(
            "arm64-v8a" to "8d4668b424fb513bda2de963c4c8e14499c78687b2c60f1eb6fdd49fb22a9122",
            "x86_64" to "7f47621783a7900fd3d94aed93b9e2268a192b7a062cae0eead1f62e2bfa7ed9",
        )
        expected.forEach { (abi, hash) ->
            val file = binaries.file("$abi/libksud.so").get().asFile
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                .joinToString("") { "%02x".format(it) }
            check(actual == hash) { "KernelSU $abi resource digest mismatch: $actual" }
        }
        mapOf(
            "arm64-v8a" to "049491e2b4cc0a6c913d3920cb90b8c94c9e8ca96c0ee92818293304973bfb1b",
            "x86_64" to "5324a1e8f2f3c74dc69624c4cd973fb9bc1d5da3614a2f2e694583f73bd13cbd",
        ).forEach { (abi, hash) ->
            val file = binaries.file("$abi/libksud_v5.so").get().asFile
            val actual = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            check(actual == hash) { "KernelSU UAPI5 $abi resource digest mismatch: $actual" }
        }
    }
}

val verifyKsuResources = tasks.register<VerifyKsuResources>("verifyKsuResources") {
    binaries.set(layout.projectDirectory.dir("src/main/jniLibs"))
}
tasks.named("preBuild").configure { dependsOn(verifyKsuResources) }
