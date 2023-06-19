plugins {
    id("kotlin-conventions")
    id("publishing-conventions")
}

dependencies {
    api(libs.kotlin.reflect)
    api(libs.klam)
    api(libs.configurate.core)
    api(libs.alexandria.api)
}
