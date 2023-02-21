plugins {
    id("kotlin-conventions")
}

dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.libBulletJme)
    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)
}
