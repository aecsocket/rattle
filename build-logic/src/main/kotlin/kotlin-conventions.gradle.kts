plugins {
    id("java-conventions")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(indra.javaVersions().target().get())
}

dependencies {
    testImplementation(kotlin("test"))
}
