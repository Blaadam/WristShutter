plugins {
    id("com.android.application")
}

android {
    namespace = "com.wristshutter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wristshutter"
        minSdk = 34
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Sideload-only personal tool: sign release with the debug key so it installs without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Verified on Maven Central (2026-10): latest is 2.2.0.
    implementation("com.garmin.connectiq:ciq-companion-app-sdk:2.2.0@aar")
}
