rootProject.name = "uconnectivity"

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":shared-api")
include(":server")
include(":shared")
include(":desktopApp")

// Gate mobile modules on SDK availability so the JVM-only builds work out of the box.
if (System.getenv("ANDROID_HOME") != null || rootDir.resolve("local.properties").exists()) {
    include(":androidApp")
}
