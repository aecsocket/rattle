plugins {
  id("kotlin-conventions")
  id("publishing-conventions")
}

dependencies {
  api(projects.rattleApi)
  api(projects.rattleRapier)
  api(libs.alexandria.common)
}
