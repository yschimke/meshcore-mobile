package ee.schimke.meshcore.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** User-facing light/dark selection. */
enum class ThemeMode { System, Light, Dark }

/**
 * Which palette to draw from. `Meshcore` is the hand-tuned brand
 * palette defined in [MeshcoreTheme]; `Dynamic` uses Android 12+ Material
 * You dynamic colors derived from the user's wallpaper. On older
 * devices `Dynamic` silently falls back to [Meshcore].
 */
enum class ThemePalette { Meshcore, Dynamic }

data class ThemeSettings(
    val mode: ThemeMode = ThemeMode.System,
    val palette: ThemePalette = ThemePalette.Meshcore,
)

private val Context.meshcoreDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "meshcore_prefs",
)

private val ThemeModeKey = stringPreferencesKey("theme_mode")
private val ThemePaletteKey = stringPreferencesKey("theme_palette")

/**
 * Tiny repository over the `meshcore_prefs` Preferences DataStore. Right
 * now it stores a [ThemeMode] and a [ThemePalette]; future UI
 * preferences belong here as additional keys rather than a second
 * DataStore.
 */
class ThemePreferences(context: Context) {
    private val store: DataStore<Preferences> = context.applicationContext.meshcoreDataStore

    val settings: Flow<ThemeSettings> = store.data.map { prefs ->
        ThemeSettings(
            mode = prefs[ThemeModeKey]?.let {
                runCatching { ThemeMode.valueOf(it) }.getOrNull()
            } ?: ThemeMode.System,
            palette = prefs[ThemePaletteKey]?.let {
                runCatching { ThemePalette.valueOf(it) }.getOrNull()
            } ?: ThemePalette.Meshcore,
        )
    }

    // Legacy single-field convenience kept so code that only cares
    // about light/dark doesn't have to destructure ThemeSettings.
    val themeMode: Flow<ThemeMode> = settings.map { it.mode }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[ThemeModeKey] = mode.name }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        store.edit { it[ThemePaletteKey] = palette.name }
    }
}
