plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nnazuna.nnastamina"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.nnazuna.nnastamina"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // Fixed signing key so new versions install over previous ones.
    signingConfigs {
        create("stable") {
            storeFile = file("signing/nnastamina.keystore")
            storePassword = "J9AMCluPi1e3CjxVW0kK"
            keyAlias = "nnastamina"
            keyPassword = "J9AMCluPi1e3CjxVW0kK"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("stable")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("stable")
        }
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
    testImplementation("junit:junit:4.13.2")
}
