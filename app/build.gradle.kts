import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

val keystoreProps = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

android {
    namespace = "com.jlees.mibrowser"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jlees.mibrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 111
        versionName = "1.1.1"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
            storePassword = keystoreProps.getProperty("storePassword")
            keyAlias = keystoreProps.getProperty("keyAlias")
            keyPassword = keystoreProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        // installs next to the release app: separate package, name and data
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

// Name build outputs "mi-browser-<version>-<buildType>.apk" (e.g. mi-browser-1.0.0-release.apk)
base.archivesName.set("mi-browser-${android.defaultConfig.versionName}")

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha29")
    implementation("com.materialkolor:material-kolor:3.0.0-beta01")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.core:core-splashscreen:1.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.camera:camera-camera2:1.6.2")
    implementation("androidx.camera:camera-lifecycle:1.6.2")
    implementation("androidx.camera:camera-view:1.6.2")
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.vanniktech:android-image-cropper:4.7.0")
    testImplementation("junit:junit:4.13.2")
}
