val lwjglVersion = "3.3.1"

plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.physxJni)
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-linux" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-windows" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-macos" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-macos-arm64" } }
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl", "lwjgl")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-linux")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-macos")
}
