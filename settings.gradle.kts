enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") // Kotlin Beta
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net")
    }
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

rootProject.name = "ignacio"

include("ignacio-api")
include("ignacio-rapier")
include("ignacio-physx")
include("ignacio-paper")
