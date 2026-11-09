plugins { id("com.android.application") version "9.4.1" }
android {
    namespace = "com.chichu.toolbox.acceptance"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.chichu.toolbox.acceptance"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
dependencies {
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
