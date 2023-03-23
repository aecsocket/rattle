plugins {
    id("base-conventions")
    id("java-library")
    id("net.kyori.indra")
}

indra {
    javaVersions {
        target(20)
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
