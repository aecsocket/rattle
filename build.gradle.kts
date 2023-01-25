plugins {
    kotlin("jvm")
    id("org.jetbrains.dokka")
    id("maven-publish")
}

allprojects {
    group = "io.gitlab.aecsocket"
    version = "0.1.0"
    description = "API for integrating physics engines into Minecraft"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.dokka")
    apply(plugin = "maven-publish")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    kotlin {
        jvmToolchain(17)
    }

    tasks {
        test {
            useJUnitPlatform()
        }

        processResources {
            filteringCharset = Charsets.UTF_8.name()
            filter { it
                .replace("@version@", project.version.toString())
                .replace("@description@", project.description.toString())
                .replace("@group@", project.group.toString())
            }
        }
    }

    publishing {
        repositories {
            maven {
                url = uri("${System.getenv("CI_API_V4_URL")}/projects/${System.getenv("CI_PROJECT_ID")}/packages/maven")
                credentials(HttpHeaderCredentials::class) {
                    name = "Job-Token"
                    value = System.getenv("CI_JOB_TOKEN")
                }
                authentication {
                    create<HttpHeaderAuthentication>("header")
                }
            }
        }

        publications {
            create<MavenPublication>("maven") {
                from(components["java"])
            }
        }
    }
}
