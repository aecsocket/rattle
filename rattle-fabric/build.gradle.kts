plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
    alias(libs.plugins.fabric.loom)
}

repositories {
    sonatype.ossSnapshots()
}

dependencies {
    minecraft(libs.minecraft)
    mappings(loom.officialMojangMappings())
    api(projects.rattleServer)

    api(libs.alexandria.fabric)
}

loom {
    runs {
        get("client").apply {
            vmArgs.add("--enable-preview")
        }
    }
}
