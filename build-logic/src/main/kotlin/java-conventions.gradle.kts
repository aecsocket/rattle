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

    github("aecsocket", "ignacio")
    mitLicense()

    configurePublications {
        pom {
            developers {
                developer {
                    name.set("aecsocket")
                    email.set("aecsocket@tutanota.com")
                    url.set("https://github.com/aecsocket")
                }
            }
        }
    }
}

signing {
    val signingKey: String? by project
    val signingPassword: String? by project
    if (signingKey != null) {
        useInMemoryPgpKeys(signingKey, signingPassword)
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
