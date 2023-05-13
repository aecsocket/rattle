plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") // Kotlin Beta
}

dependencies {
    implementation(libs.indra.common)
    implementation(libs.indra.publishing.sonatype)
    implementation(libs.kotlin)
}
