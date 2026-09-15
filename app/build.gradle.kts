plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.xdarkixx.matrixcamera"
    compileSdk = 35

    defaultConfig {
        applicationId = "de.xdarkixx.matrixcamera"
        minSdk = 26
        targetSdk = 35
        versionCode = 3
        versionName = "2.1"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")
    implementation("io.github.webrtc-sdk:android:150.7871.01")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
