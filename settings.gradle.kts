enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://papermc.io/repo/repository/maven-public/")
    }

    plugins {
        kotlin("jvm") version "1.8.0"
        id("org.jetbrains.dokka") version "1.7.20"

        id("io.papermc.paperweight.userdev") version "1.4.1"
        id("com.github.johnrengelman.shadow") version "7.1.2"
        id("xyz.jpenilla.run-paper") version "1.1.0"
    }
}

rootProject.name = "ignacio"

listOf(
    "core", "paper",
    "jolt", "physx"
).forEach { include("${rootProject.name}-$it") }
