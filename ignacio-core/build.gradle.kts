plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(libs.cloudCore)
    implementation(libs.configurateCore)
    implementation(libs.alexandriaCore)

    testImplementation(projects.ignacioJolt)
    testImplementation(libs.joltJni)
}
