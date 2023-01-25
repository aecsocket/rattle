dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.physxJni)
    runtimeOnly(libs.physxJni) {
        artifact { classifier = "natives-linux" }
    }
    runtimeOnly(libs.physxJni) {
        artifact { classifier = "natives-windows" }
    }
    runtimeOnly(libs.physxJni) {
        artifact { classifier = "natives-macos" }
    }

    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)

    testImplementation(platform("org.junit:junit-bom:5.9.0"))
}
