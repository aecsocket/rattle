plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(libs.kotlin.reflect)
    api(libs.klam)
    api(libs.configurate.core)

    testImplementation(projects.ignacioRapier)
    testImplementation(projects.ignacioPhysx)
}
