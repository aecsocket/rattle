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
    implementation(projects.ignacioApi)
    implementation(projects.ignacioJolt)
    testRuntimeOnly(libs.joltJavaNativesLinuxX86)
    testRuntimeOnly(libs.cpuFeaturesJavaNativesLinuxX86)
    paperweight.paperDevBundle("$minecraft-R0.1-SNAPSHOT")
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.klam)
    implementation(libs.adventureSerializerConfigurate)
    implementation(libs.cloudCore)
    implementation(libs.cloudPaper)
    implementation(libs.cloudMinecraftExtras)
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
    implementation(libs.configurateYaml)
    implementation(libs.glossaApi)
    implementation(libs.glossaConfigurate)
    implementation(libs.alexandriaApi)
    implementation(libs.alexandriaApiPaper)
    implementation(libs.packetEventsSpigot)
}

tasks {
    assemble {
        dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraft)
    }
}
