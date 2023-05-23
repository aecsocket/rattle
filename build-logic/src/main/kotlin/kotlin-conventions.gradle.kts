import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("java-conventions")
    kotlin("jvm")
}

kotlin {
    jvmToolchain(indra.javaVersions().target().get())
// TODO Kotlin 1.9
//    compilerOptions {
//        freeCompilerArgs.add("-Xcontext-receivers")
//    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks {
    withType<KotlinCompile> {
        compilerOptions.freeCompilerArgs.add("-Xcontext-receivers")
    }
}
