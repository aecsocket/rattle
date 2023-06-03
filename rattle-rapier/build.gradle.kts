plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.rattleApi)
    implementation(libs.rapier)

    testRuntimeOnly(libs.rapier.linux.x86)
}
