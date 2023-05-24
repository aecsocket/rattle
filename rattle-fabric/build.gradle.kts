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
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)
    modImplementation(libs.fabric.language.kotlin)
    api(projects.rattleServer)

    //api(libs.alexandria.fabric)
}

loom {
    runs {
        get("client").apply {
            // TODO: this gets ignored apparently (not java 19?)
            vmArgs.add("--enable-preview")
        }
    }
}
