repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation(projects.ignacioCore)

    implementation(libs.joltJni)
    implementation(libs.joltJniKotlin)
    runtimeOnly(libs.joltJni) { artifact { classifier = "natives-linux" } }
    runtimeOnly(libs.joltJni) { artifact { classifier = "natives-windows" } }
    runtimeOnly(libs.joltJni) { artifact { classifier = "natives-macos" } }
    runtimeOnly(libs.joltJni) { artifact { classifier = "natives-macos-arm64" } }

    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)

    testImplementation(kotlin("test"))
}
