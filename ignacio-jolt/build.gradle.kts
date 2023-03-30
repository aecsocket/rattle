plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    implementation(projects.ignacioApi)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.klam)
    implementation(libs.configurateCore)
    implementation(libs.joltJava)
    //implementation(libs.kotlinxCoroutinesJdk8)
    //implementation(libs.cloudCore)
    //implementation(libs.alexandriaCore)

    testRuntimeOnly(libs.joltJavaNativesLinuxX86)
}

tasks {
    test {
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
    }
}
