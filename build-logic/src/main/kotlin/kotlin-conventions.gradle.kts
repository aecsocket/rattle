plugins {
  id("java-conventions")
  kotlin("jvm")
  id("org.jetbrains.dokka")
}

kotlin {
  jvmToolchain(indra.javaVersions().target().get())
}

dependencies {
  testImplementation(kotlin("test"))
}

spotless {
  kotlin {
    ktfmt()
  }

  kotlinGradle {
    ktfmt()
  }
}
