val lwjglVersion = "3.3.1"

dependencies {
    implementation(projects.ignacioCore)

    implementation(libs.physxJni)
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-linux" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-windows" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-macos" } }

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl", "lwjgl")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-linux")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-macos")

    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)

    testImplementation(platform("org.junit:junit-bom:5.9.0"))
}
