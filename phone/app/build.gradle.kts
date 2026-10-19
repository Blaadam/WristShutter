import java.util.Properties

plugins {
    id("com.android.application")
}

// Your own release key, kept out of git: phone/keystore.properties with storeFile, storePassword,
// keyAlias and keyPassword (see README). Without it, release falls back to the debug key.
val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

android {
    namespace = "com.wristshutter"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.wristshutter"
        minSdk = 34
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.0"
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (!keystoreProps.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
