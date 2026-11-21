plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ru.svoe.tv"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.svoe.tv"
        minSdk = 24
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
        targetSdk = 34
        versionCode = 10
        versionName = "1.0"
    }
    signingConfigs {
        create("shared") {
            storeFile = rootProject.file("svoe.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("shared") }
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // IPTV-плеер

    // Видеоплеер для IPTV: движок VLC
    implementation("org.videolan.android:libvlc-all:3.6.0")

    // QR-коды на экране привязки телефона
    implementation("com.google.zxing:core:3.5.3")
}
