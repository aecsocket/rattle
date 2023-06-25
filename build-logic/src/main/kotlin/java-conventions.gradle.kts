import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    id("base-conventions")
    id("java-library")
    id("net.kyori.indra")
}

indra {
    javaVersions {
        target(19)
        previewFeaturesEnabled(true)
    }
}

repositories {
    if (!ci.get()) mavenLocal()
    mavenCentral()
    sonatype.s01Snapshots()
}

tasks.test {
    jvmArgs(
        "--enable-preview",
        "--enable-native-access=ALL-UNNAMED",
    )
    testLogging.exceptionFormat = TestExceptionFormat.FULL
}
