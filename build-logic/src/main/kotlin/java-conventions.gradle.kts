plugins {
    id("base-conventions")
    id("java-library")
    id("net.kyori.indra")
    id("net.kyori.indra.publishing")
}

indra {
    javaVersions {
        target(19)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
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
