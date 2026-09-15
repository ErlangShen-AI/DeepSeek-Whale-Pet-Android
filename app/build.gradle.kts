plugins {
    id("com.android.application")
}

/** 发布签名从环境变量读取，缺少密钥时构建不带签名的发布包。 */
val keystoreFile: File? = System.getenv("WHALE_KEYSTORE_PATH")
    ?.takeIf { it.isNotEmpty() }
    ?.let { file(it) }
    ?.takeIf { it.exists() }

android {
    namespace = "com.whalepet"

    compileSdk = 36

    defaultConfig {
        applicationId = "com.whalepet"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("WHALE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("WHALE_KEY_ALIAS")
                keyPassword = System.getenv("WHALE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            isDebuggable = false
            if (keystoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
    }

    lint {
        checkReleaseBuilds = true
        abortOnError = true
        checkDependencies = false
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-service:2.11.0")
    implementation("com.google.android.material:material:1.14.0")

    add("testImplementation", "junit:junit:4.13.2")
    add("testImplementation", "org.json:json:20240303")
}
