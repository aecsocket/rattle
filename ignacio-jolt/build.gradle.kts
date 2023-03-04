plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.joltJava)
    implementation(libs.joltJavaKotlin)
    runtimeOnly(libs.joltJavaNativesLinuxX86)
    //runtimeOnly(libs.joltJniNativesWindows)
    //runtimeOnly(libs.joltJniNativesMacos)
    //runtimeOnly(libs.joltJniNativesMacosArm64)
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
}
