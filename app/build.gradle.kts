plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// CI sets GITHUB_RUN_NUMBER, so every build gets a higher version and installs over the last one.
val build = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "dev.daniel.scathechat"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.daniel.scathechat"
        minSdk = 26
        targetSdk = 35
        versionCode = build
        versionName = "1.$build"
        buildConfigField("String", "UPDATE_REPO", "\"andersondaniel48-dot/scathechat\"")
    }

    // One fixed key so each new APK updates the app in place instead of needing a reinstall.
    signingConfigs {
        create("scathe") {
            storeFile = file("scathe.keystore")
            storePassword = "scathechat"
            keyAlias = "scathe"
            keyPassword = "scathechat"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("scathe") }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("scathe")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources { excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*", "/META-INF/NOTICE*") }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-sse:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
}
