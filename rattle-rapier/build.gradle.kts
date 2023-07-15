plugins {
  id("kotlin-conventions")
  id("publishing-conventions")
}

dependencies {
  api(projects.rattleApi)
  implementation(libs.rapier)
  runtimeOnly(libs.rapier.linux.x86)
  runtimeOnly(libs.rapier.linux.aarch64)
  runtimeOnly(libs.rapier.windows.x86)
  runtimeOnly(libs.rapier.macos.x86)

  testRuntimeOnly(libs.rapier.linux.x86)
}
