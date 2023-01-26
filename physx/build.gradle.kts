val lwjglVersion = "3.3.1"
val lwjglNatives = System.getProperty("os.name")!!.let { name ->
    when {
        arrayOf("Linux", "FreeBSD", "SunOS", "Unit").any { name.startsWith(it) } ->
            "natives-linux"
        arrayOf("Mac OS X", "Darwin").any { name.startsWith(it) }                ->
            "natives-macos"
        arrayOf("Windows").any { name.startsWith(it) }                           ->
            "natives-windows"
        else -> throw Error("Unrecognized or unsupported platform. Please set \"lwjglNatives\" manually")
    }
}

dependencies {
    implementation(projects.ignacioCore)

    implementation(libs.physxJni)
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-linux" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-windows" } }
    runtimeOnly(libs.physxJni) { artifact { classifier = "natives-macos" } }

    implementation(platform("org.lwjgl:lwjgl-bom:$lwjglVersion"))
    implementation("org.lwjgl", "lwjgl")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-linux")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-windows")
    runtimeOnly("org.lwjgl", "lwjgl", classifier = "natives-macos")

    implementation(libs.configurateCore)
    implementation(libs.configurateExtraKotlin)

    testImplementation(platform("org.junit:junit-bom:5.9.0"))
}
