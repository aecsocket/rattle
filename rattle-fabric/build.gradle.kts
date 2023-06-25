plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
    alias(libs.plugins.fabric.loom)
}

val minecraft = libs.versions.fabric.asProvider().get()

repositories {
    sonatype.ossSnapshots()
}

dependencies {
    minecraft("com.mojang", "minecraft", minecraft)
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.language.kotlin)
    api(projects.rattleCommon)

    modApi(libs.alexandria.fabric)
    include(libs.alexandria.fabric)
}

loom {
    splitEnvironmentSourceSets()

    mods {
        create("rattle").apply {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        get("client").apply {
            vmArgs.addAll(listOf("--enable-preview", "--enable-native-access=ALL-UNNAMED"))
        }
    }
}

tasks.processResources {
    filesMatching("fabric.mod.json") {
        expand(
            "version" to project.version,
            "group" to project.group,
            "description" to project.description,
            "versions" to mapOf(
                "fabric" to minecraft,
                "fabric_loader" to libs.versions.fabric.loader.get(),
            ),
        )
    }
}
