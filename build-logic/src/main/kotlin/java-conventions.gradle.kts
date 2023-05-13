import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("base-conventions")
    id("java-library")
    id("net.kyori.indra")
}

indra {
    javaVersions {
        target(20)
        previewFeaturesEnabled(true)
    }
}

repositories {
    if (!ci.get()) mavenLocal()
    mavenCentral()
    sonatype.s01Snapshots()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") // Kotlin Beta
}

tasks {
    processResources {
        filesMatching("**/*.yml") {
            expand(
                "version" to project.version,
                "group" to project.group,
                "description" to project.description
            )
        }
    }

    test {
        jvmArgs(
            "--enable-preview",
            "--enable-native-access=ALL-UNNAMED",
        )
        testLogging.exceptionFormat = TestExceptionFormat.FULL
    }
}
