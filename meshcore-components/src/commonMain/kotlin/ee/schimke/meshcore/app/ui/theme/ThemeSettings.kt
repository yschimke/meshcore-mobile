package ee.schimke.meshcore.app.ui.theme

/** User-facing light/dark selection. */
enum class ThemeMode {
  System,
  Light,
  Dark,
}

/**
 * Which palette to draw from. `Meshcore` is the hand-tuned brand palette defined in
 * [MeshcoreTheme]; `Dynamic` uses Android 12+ Material You dynamic colors derived from the user's
 * wallpaper. On older devices `Dynamic` silently falls back to [Meshcore].
 */
enum class ThemePalette {
  Meshcore,
  Dynamic,
}

data class ThemeSettings(
  val mode: ThemeMode = ThemeMode.System,
  val palette: ThemePalette = ThemePalette.Meshcore,
)
