plugins {
    id("java-conventions")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(indra.javaVersions().target().get())
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-receivers")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
