dependencies {
    implementation(projects.ignacioCore)
    implementation(libs.libBulletJme)

    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)

    testImplementation(platform("org.junit:junit-bom:5.9.0"))
}
