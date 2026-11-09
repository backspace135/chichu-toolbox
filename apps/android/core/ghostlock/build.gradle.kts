plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.chichu.toolbox.core.ghostlock"
    compileSdk = 37
    defaultConfig { minSdk = 31 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

dependencies {
    implementation(project(":core:common"))
    testImplementation(libs.junit)
}
