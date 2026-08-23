plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.privacykeyboard.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.privacykeyboard.app"
        // Point 1.3: raised from 21 to 23 - EncryptedSharedPreferences (used to
        // encrypt every sensitive file this app writes) needs the Android Keystore
        // APIs that only exist from Android 6.0 (API 23) onward.
        minSdk = 23
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Point 1.3: encrypts every SharedPreferences file this app writes (Dictionary
    // Security Lock PIN hash/salt, recovery email, My Dictionary words) with a key
    // held inside the device's hardware-backed Android Keystore.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    
    // Point 3: AppCompat for SwitchCompat (iOS style toggle in Dictionary Security Lock)
    implementation("androidx.appcompat:appcompat:1.6.1")
}
