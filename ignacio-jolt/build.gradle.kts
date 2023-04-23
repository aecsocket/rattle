plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(projects.ignacioApi)
    implementation(libs.joltJava)

    testRuntimeOnly(libs.cpuFeaturesJavaNativesLinuxX86)
    testRuntimeOnly(libs.cpuFeaturesJavaNativesWindowsX86)
    testRuntimeOnly(libs.cpuFeaturesJavaNativesMacosX86)
    testRuntimeOnly(libs.joltJavaNativesLinuxX86)
    //testRuntimeOnly(libs.joltJavaNativesWindowsX86)
    //testRuntimeOnly(libs.joltJavaNativesMacosX86)
}

tasks {
    test {
        jvmArgs("--enable-preview", "--enable-native-access=ALL-UNNAMED")
    }
}
