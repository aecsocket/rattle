plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.klam)
    implementation(libs.klamConfigurate)
    implementation(libs.configurateCore)
    //implementation(libs.kotlinxCoroutinesJdk8)
    //implementation(libs.cloudCore)
    //implementation(libs.alexandriaCore)
}
