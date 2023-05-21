enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
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

rootProject.name = "rattle"

include("rattle-api")
include("rattle-rapier")
include("rattle-server")
include("rattle-paper")
//include("rattle-fabric")
