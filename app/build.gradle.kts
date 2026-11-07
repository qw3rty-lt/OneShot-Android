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
        versionCode = 4
        versionName = "0.3.1-alpha"
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
