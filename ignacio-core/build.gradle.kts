dependencies {
    compileOnly(libs.kotlinReflect)
    compileOnly(libs.kotlinxCoroutines)

    compileOnly(libs.cloudCore)
    compileOnly(libs.configurateCore)

    testImplementation(kotlin("test"))
    testImplementation(projects.ignacioJolt)
    testImplementation(projects.ignacioPhysx)
}
