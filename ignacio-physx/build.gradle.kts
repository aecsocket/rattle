plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.ignacioApi)
    api(libs.physx)
    runtimeOnly(libs.physx) { artifact { classifier = "natives-linux" } }
    runtimeOnly(libs.physx) { artifact { classifier = "natives-windows" } }
    runtimeOnly(libs.physx) { artifact { classifier = "natives-macos" } }
}
