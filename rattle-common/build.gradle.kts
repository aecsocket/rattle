plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.rattleApi)
    api(projects.rattleRapier)
    runtimeOnly(libs.rapier.linux.x86)
    //runtimeOnly(libs.rapier.windows.x86)
    //runtimeOnly(libs.rapier.macos.x86)
    api(libs.alexandria.common)
}
