import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val hasAndroidSdk = System.getenv("ANDROID_HOME") != null ||
    rootProject.file("local.properties").exists()

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}

if (hasAndroidSdk) {
    apply(plugin = libs.plugins.android.application.get().pluginId)
}

kotlin {
    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    jvm()
    if (hasAndroidSdk) {
        androidTarget {
            @OptIn(ExperimentalKotlinGradlePluginApi::class)
            compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        }
    }
    listOf(iosArm64(), iosSimulatorArm64(), iosX64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
            binaryOption("bundleId", "app.ucon.composeapp")
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.sharedApi)
            implementation(projects.shared)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
        iosMain.dependencies {}
    }

    if (hasAndroidSdk) {
        sourceSets.named("androidMain").configure {
            dependencies {
                implementation(libs.androidx.activity.compose)
                implementation(libs.androidx.work.runtime.ktx)
                implementation(libs.androidx.glance.appwidget)
                implementation(libs.androidx.glance.material3)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.ucon.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "uConnectivity"
            packageVersion = "1.0.0"
        }
    }
}

if (hasAndroidSdk) {
    extensions.configure<com.android.build.gradle.internal.dsl.BaseAppModuleExtension>("android") {
        namespace = "app.ucon.android"
        compileSdk = 35
        defaultConfig {
            applicationId = "app.ucon"
            minSdk = 26
            targetSdk = 35
            versionCode = 1
            versionName = "1.0.0"
        }
        compileOptions {
            sourceCompatibility = JavaVersion.VERSION_21
            targetCompatibility = JavaVersion.VERSION_21
        }
        sourceSets.named("main") {
            manifest.srcFile("src/androidMain/AndroidManifest.xml")
            res.setSrcDirs(listOf("src/androidMain/res"))
        }
        buildFeatures {
            compose = true
        }
    }
}
