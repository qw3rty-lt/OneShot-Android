plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.oneshot"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.oneshot"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0-alpha"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
