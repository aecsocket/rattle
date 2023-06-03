plugins {
    id("net.kyori.indra.publishing.sonatype")
}

indraSonatype {
    useAlternateSonatypeOSSHost("s01")
}

repositories {
    mavenCentral()
}

afterEvaluate {
    tasks.register("printVersionType") {
        doFirst {
            println(if (net.kyori.indra.util.Versioning.isSnapshot(project)) "snapshot" else "release")
        }
    }
}
