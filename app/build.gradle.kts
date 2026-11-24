plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Kunci tanda tangan hanya dibaca dari environment saat build di CI.
// Kunci tidak pernah disimpan di repository.
val hasReleaseKey = System.getenv("KEYSTORE_PATH") != null

android {
    namespace = "com.example.wacleaner"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.wacleaner"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        val path = System.getenv("KEYSTORE_PATH")
        if (path != null) {
            create("release") {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }

    lint {
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Aplikasi ini sengaja tanpa library pihak ketiga (tidak ada blok dependencies).
