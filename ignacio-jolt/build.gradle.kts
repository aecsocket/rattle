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
}

tasks {
    test {
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
    }
}
