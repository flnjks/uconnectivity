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

// Build the macOS Swift helper executable that owns NSStatusItem.
// Skipped on non-macOS hosts.
val buildMacStatusBarHelper by tasks.registering(Exec::class) {
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    workingDir = rootDir.resolve("desktop-helper-macos")
    commandLine("swift", "build", "-c", "release")
    inputs.files(fileTree(workingDir) { include("Package.swift", "Sources/**") })
    outputs.file(workingDir.resolve(".build/arm64-apple-macosx/release/uconnectivity-statusbar"))
}

// macOS code-signing + notarization: configured by these env vars (or
// gradle.properties). All are optional — if MACOS_SIGN_IDENTITY is unset, the
// build produces an unsigned DMG fine for local testing.
//   MACOS_SIGN_IDENTITY        e.g. "Developer ID Application: Jane Doe (ABCD12EFGH)"
//   MACOS_SIGN_TEAM_ID         the 10-char team id, e.g. "ABCD12EFGH"
//   MACOS_NOTARIZATION_USER    Apple ID email
//   MACOS_NOTARIZATION_PASS    app-specific password (xcrun notarytool)
//   MACOS_NOTARIZATION_KEY_ID  optional: App Store Connect API key id
//   MACOS_NOTARIZATION_KEY     optional: path to the .p8 API key file
//   MACOS_NOTARIZATION_KEY_ISSUER optional: API key issuer uuid
val macSignIdentity: String? = (findProperty("MACOS_SIGN_IDENTITY") as? String) ?: System.getenv("MACOS_SIGN_IDENTITY")
val macTeamId: String?       = (findProperty("MACOS_SIGN_TEAM_ID") as? String) ?: System.getenv("MACOS_SIGN_TEAM_ID")
val notaryUser: String?      = (findProperty("MACOS_NOTARIZATION_USER") as? String) ?: System.getenv("MACOS_NOTARIZATION_USER")
val notaryPass: String?      = (findProperty("MACOS_NOTARIZATION_PASS") as? String) ?: System.getenv("MACOS_NOTARIZATION_PASS")

compose.desktop {
    application {
        mainClass = "app.ucon.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "uConnectivity"
            packageVersion = "1.0.0"

            macOS {
                bundleID = "app.ucon.desktop"

                // Bundle the Swift helper into the .app under Contents/Resources/.
                appResourcesRootDir.set(rootDir.resolve("desktop-helper-macos/.build/macos-app-resources"))

                // Hardened runtime + entitlements that allow JNI + the spawned helper.
                entitlementsFile.set(rootDir.resolve("composeApp/entitlements/macos.entitlements"))
                runtimeEntitlementsFile.set(rootDir.resolve("composeApp/entitlements/macos.entitlements"))

                // Signing: only enable when an identity is supplied — local dev builds
                // stay unsigned and skip this whole branch.
                if (!macSignIdentity.isNullOrBlank()) {
                    signing {
                        sign.set(true)
                        identity.set(macSignIdentity)
                    }
                    if (!notaryUser.isNullOrBlank() && !notaryPass.isNullOrBlank()) {
                        notarization {
                            appleID.set(notaryUser)
                            password.set(notaryPass)
                            if (!macTeamId.isNullOrBlank()) {
                                teamID.set(macTeamId)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Sign the bundled Swift helper with the same Developer ID before Compose
// Desktop seals the .app. Runs only when a signing identity is provided.
val helperBin = rootDir.resolve("desktop-helper-macos/.build/macos-app-resources/macos/uconnectivity-statusbar")
val helperEntitlements = rootDir.resolve("composeApp/entitlements/macos-helper.entitlements")
val isMacHost = System.getProperty("os.name").lowercase().contains("mac")
val signIdentityCaptured = macSignIdentity
val signMacStatusBarHelper by tasks.registering(Exec::class) {
    enabled = isMacHost && !signIdentityCaptured.isNullOrBlank()
    inputs.file(helperBin)
    commandLine(
        "/usr/bin/codesign",
        "--force",
        "--options", "runtime",
        "--sign", signIdentityCaptured ?: "-",
        "--entitlements", helperEntitlements.absolutePath,
        helperBin.absolutePath,
    )
}

// Stage the helper into the app-resources dir Compose Desktop reads from.
val stageMacAppResources by tasks.registering(Copy::class) {
    enabled = isMacHost
    dependsOn(buildMacStatusBarHelper)
    from(rootDir.resolve("desktop-helper-macos/.build/arm64-apple-macosx/release/uconnectivity-statusbar"))
    into(rootDir.resolve("desktop-helper-macos/.build/macos-app-resources/macos"))
    fileMode = 0b111_101_101   // rwxr-xr-x — needed so the JVM can exec() it.
    finalizedBy(signMacStatusBarHelper)
}

tasks.matching { it.name == "prepareAppResources" || it.name == "run" }
    .configureEach { dependsOn(stageMacAppResources) }

// jpackage strips the executable bit from files inside Contents/app/resources.
// Restore it after createDistributable so packageDmg ships an executable helper.
val fixHelperPermsInApp by tasks.registering(Exec::class) {
    enabled = isMacHost
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/uConnectivity.app").get().asFile
    inputs.dir(appDir)
    commandLine(
        "/bin/chmod",
        "+x",
        "$appDir/Contents/app/resources/uconnectivity-statusbar",
    )
    // Best effort — the file may not exist on non-mac builds where the task is disabled.
    isIgnoreExitValue = false
}
tasks.matching { it.name == "createDistributable" }.configureEach { finalizedBy(fixHelperPermsInApp) }
tasks.matching { it.name == "packageDmg" || it.name == "runDistributable" }
    .configureEach { dependsOn(fixHelperPermsInApp) }

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
