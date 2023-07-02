enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://maven.fabricmc.net")
    }
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version("0.4.0")
}

rootProject.name = "rattle-parent"

include("rattle-api")
include("rattle-rapier")
include("rattle-common")
include("rattle-paper")
// don't include fabric build in the CI because it eats too much RAM and crashes
// TODO: make this work lol
if (!providers.environmentVariable("CI").map { it.toBoolean() }.getOrElse(false)) {
    include("rattle-fabric")
}
