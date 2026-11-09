plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.gorilla.companion"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.gorilla.companion"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
