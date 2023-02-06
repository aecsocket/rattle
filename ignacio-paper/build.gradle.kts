plugins {
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
    api(projects.ignacioCore)
    implementation(projects.ignacioJolt)
    implementation(projects.ignacioPhysx)
    //implementation(projects.ignacioBullet)
    paperDevBundle("$minecraft-R0.1-SNAPSHOT")

    implementation(libs.adventureSerializerConfigurate)

    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
    implementation(libs.configurateHocon)

    implementation(libs.cloudCore)
    implementation(libs.cloudPaper)
    implementation(libs.cloudMinecraftExtras)

    implementation(libs.packetEventsSpigot)

    // kt-runtime
    compileOnly(libs.kotlinReflect)
    compileOnly(libs.kotlinxCoroutinesJdk8)

    testImplementation(platform("org.junit:junit-bom:5.9.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks {
    shadowJar {
        mergeServiceFiles()

        // kt-runtime
        exclude("kotlin/")
        exclude("kotlinx/")
        exclude("META-INF/versions/19/")

        // LibBulletJme is *not* shaded to prevent issues with native library
        listOf(
            "com.github.benmanes.caffeine",
            "com.google.errorprone",
            "org.checkerframework",
            "io.leangen.geantyref",
            "com.typesafe.config",
            "org.spongepowered.configurate",
            "cloud.commandframework",
            "com.github.retrooper.packetevents",
            "io.github.retrooper.packetevents",
        ).forEach { relocate(it, "${project.group}.ignacio.lib.$it") }
    }

    assemble {
        //dependsOn(shadowJar)
    }

    runServer {
        minecraftVersion(minecraft)
    }
}
