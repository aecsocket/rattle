plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.joltJava)
    runtimeOnly(libs.joltJavaNativesLinuxX86)
    //runtimeOnly(libs.joltJniNativesWindows)
    //runtimeOnly(libs.joltJniNativesMacos)
    //runtimeOnly(libs.joltJniNativesMacosArm64)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
}
