plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.joltJni)
    implementation(libs.joltJniKotlin)
    runtimeOnly(libs.joltJniNativesLinux)
    //runtimeOnly(libs.joltJniNativesWindows)
    //runtimeOnly(libs.joltJniNativesMacos)
    //runtimeOnly(libs.joltJniNativesMacosArm64)
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
}
