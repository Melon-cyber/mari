import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.mari.reader"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = "app.mari.reader"
        minSdk = 26
        targetSdk = 36
        versionCode = 3
        versionName = "0.3.0"
    }
    // One signing key for every build, so updates install over the app with its data.
    // keystore.properties (not in Git) points to the key; without it the standard debug key of this computer is used.
    val signingProps = Properties().apply {
        val source = rootProject.file("keystore.properties")
        if (source.exists()) source.inputStream().use { stream -> load(stream) }
    }
    signingConfigs {
        create("mari") {
            storeFile = signingProps.getProperty("storeFile")?.let { path -> rootProject.file(path) } ?: file("${System.getProperty("user.home")}/.android/debug.keystore")
            storePassword = signingProps.getProperty("storePassword") ?: "android"
            keyAlias = signingProps.getProperty("keyAlias") ?: "androiddebugkey"
            keyPassword = signingProps.getProperty("keyPassword") ?: "android"
        }
    }
    buildTypes {
        getByName("debug") { signingConfig = signingConfigs.getByName("mari") }
        getByName("release") {
            signingConfig = signingConfigs.getByName("mari")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.code.gson:gson:2.13.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jsoup:jsoup:1.18.3")
    implementation("io.coil-kt:coil-compose:2.7.0")
}
