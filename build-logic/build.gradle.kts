plugins {
    `kotlin-dsl`
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    maven("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/dev") // Kotlin Beta
}

dependencies {
    val asmVersion = "9.5"
    implementation("org.ow2.asm", "asm", asmVersion)
    implementation("org.ow2.asm", "asm-tree", asmVersion)
    implementation("org.ow2.asm", "asm-util", asmVersion)
    implementation("org.ow2.asm", "asm-analysis", asmVersion)
    implementation(libs.indra.common)
    implementation(libs.indra.publishing.sonatype)
    implementation(libs.kotlin)
}
