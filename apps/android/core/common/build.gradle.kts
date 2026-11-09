plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.chichu.toolbox.core.common"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    api(libs.coroutines.android)
    testImplementation(libs.junit)
}
