plugins {
  alias(libs.plugins.kotlinJvm)
  alias(libs.plugins.composeMultiplatform)
  alias(libs.plugins.composeCompiler)
}

kotlin { jvmToolchain(21) }

// This opt-in desktop specimen uses the native UID renderer's Material vocabulary.
// It does not change the Android app's dependencies or branded theme.
dependencies {
  implementation(libs.compose.uiToolingPreview)
  implementation(compose.desktop.currentOs)
  implementation(libs.uidPilot.material3)
  implementation(libs.compose.adaptive)
  implementation(libs.compose.adaptive.layout)
  implementation(libs.compose.adaptive.navigation)
  testImplementation(kotlin("test"))
  testImplementation(libs.compose.uiTest)
  testImplementation(libs.compose.uiTestJunit4)
}

tasks.register<JavaExec>("renderPilot") {
  group = "verification"
  description = "Render the independent app screen at every UID reference size and state."
  classpath = sourceSets["main"].runtimeClasspath
  mainClass = "ee.schimke.adaptivepilot.RenderPilotKt"
  args(layout.buildDirectory.dir("pilot/previews").get().asFile.absolutePath)
  systemProperty("java.awt.headless", "true")
}
