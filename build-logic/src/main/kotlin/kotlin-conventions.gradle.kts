import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

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

afterEvaluate {
    tasks {
        withType<KotlinCompile> {
            //kotlinOptions.languageVersion = "2.0"
            kotlinOptions.freeCompilerArgs += listOf("-Xcontext-receivers")
        }
    }
}
