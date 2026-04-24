val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    rootProject.file("local.properties").exists()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

if (hasAndroidSdk) {
    apply(plugin = libs.plugins.android.library.get().pluginId)
}

kotlin {
    jvmToolchain(21)

    jvm()
    if (hasAndroidSdk) {
        androidTarget {
            publishLibraryVariants("release")
        }
    }
    iosArm64()
    iosSimulatorArm64()
    iosX64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
    }
}

if (hasAndroidSdk) {
    extensions.configure<com.android.build.gradle.LibraryExtension>("android") {
        namespace = "app.ucon.api"
        compileSdk = 35
        defaultConfig {
            minSdk = 26
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
    }
}
