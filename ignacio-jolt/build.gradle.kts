plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.ignacioApi)
    api(libs.jolt)
    runtimeOnly(libs.jolt.linux.x86)
    runtimeOnly(libs.jolt.windows.x86)
    runtimeOnly(libs.jolt.macos.x86)
}
