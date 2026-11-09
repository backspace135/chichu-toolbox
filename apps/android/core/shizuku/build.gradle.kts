plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.refine)
}

android {
    namespace = "com.chichu.toolbox.core.shizuku"
    compileSdk = 37
    defaultConfig {
        minSdk = 31
        externalNativeBuild.cmake.arguments += "-DANDROID_STL=none"
    }
    ndkVersion = libs.versions.ndk.get()
    buildFeatures { prefab = true }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.31.6"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.androidx.annotation)
    implementation(libs.coroutines.android)
    api(libs.shizuku.api)
    implementation(libs.shizuku.provider)
    implementation(libs.hidden.compat)
    implementation(libs.hiddenapibypass)
    compileOnly(libs.hidden.stub)
    implementation(libs.refine.runtime)
    implementation(libs.gson)
    api(libs.parcelablelist)
    implementation(libs.boringssl)
    implementation(libs.libcxx)
    implementation(libs.bouncycastle.pkix)
    testImplementation(libs.junit)
}
