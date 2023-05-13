plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.ignacioApi)
    api(libs.rapier)
    runtimeOnly(libs.rapier.linux.x86)
    //runtimeOnly(libs.rapier.windows.x86)
    //runtimeOnly(libs.rapier.macos.x86)
}
