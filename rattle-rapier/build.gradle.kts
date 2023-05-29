plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.rattleApi)
    implementation(libs.rapier)
}
