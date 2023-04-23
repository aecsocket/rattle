plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
    id("io.papermc.paperweight.userdev")
    id("com.github.johnrengelman.shadow")
    id("xyz.jpenilla.run-paper")
}

val minecraft = libs.versions.minecraft.get()

repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-snapshots/")
}

dependencies {
    paperweight.foliaDevBundle("$minecraft-R0.1-SNAPSHOT")
    api(projects.ignacioJolt)
    api(libs.alexandriaPaper)
    runtimeOnly(libs.cpuFeaturesJavaNativesLinuxX86)
    runtimeOnly(libs.cpuFeaturesJavaNativesWindowsX86)
    runtimeOnly(libs.cpuFeaturesJavaNativesMacosX86)
    runtimeOnly(libs.joltJavaNativesLinuxX86)
    runtimeOnly(libs.joltJavaNativesWindowsX86)
    //runtimeOnly(libs.joltJavaNativesMacosX86)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraft)
    }
}
