plugins {
    id("base-conventions")
    id("java-library")
    id("net.kyori.indra")
}

indra {
    javaVersions {
        target(19)
    }
}

repositories {
    if (!ci.get()) mavenLocal()
    mavenCentral()
    sonatype.s01Snapshots()
}

afterEvaluate {
    tasks.processResources {
        filesMatching("**/*.yml") {
            expand(
                "version" to project.version,
                "group" to project.group,
                "description" to project.description
            )
        }
    }
}
