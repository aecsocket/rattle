plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(libs.klam)
    api(libs.configurate.core)

    testImplementation(projects.ignacioRapier)
    testRuntimeOnly(libs.rapier.linux.x86)
}
