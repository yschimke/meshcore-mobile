package ee.schimke.meshcore.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import ee.schimke.meshcore.app.data.proto.AppPreferencesPb
import ee.schimke.meshcore.app.data.proto.DeviceSectionStatesPb
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.InputStream
import java.io.OutputStream

// --- Domain types ---

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

enum class Section { CHANNELS, CONTACTS, ROOMS, REPEATERS, SENSORS }

data class SectionStates(
    val channelsExpanded: Boolean = true,
    val channelsShowAll: Boolean = false,
    val contactsExpanded: Boolean = true,
    val contactsShowAll: Boolean = false,
    val roomsExpanded: Boolean = true,
    val roomsShowAll: Boolean = false,
    val repeatersExpanded: Boolean = true,
    val repeatersShowAll: Boolean = false,
    val sensorsExpanded: Boolean = true,
)

// --- Wire proto ↔ domain mapping ---
//
// Proto3 defaults bools to false, but we want "true" as the UI
// default for expansion (sections start expanded). The proto stores
// the *inverted* sense: a field being true means the section is
// collapsed / filtered. When no entry exists for a device, expansion
// defaults to true; showAll defaults to false (filtered view).

private fun DeviceSectionStatesPb.toDomain(): SectionStates = SectionStates(
    channelsExpanded = !channels_expanded,
    channelsShowAll = !channels_show_all,
    contactsExpanded = !contacts_expanded,
    contactsShowAll = !contacts_show_all,
    roomsExpanded = !rooms_expanded,
    roomsShowAll = !rooms_show_all,
    repeatersExpanded = !repeaters_expanded,
    repeatersShowAll = !repeaters_show_all,
    sensorsExpanded = !sensors_expanded,
)

private fun SectionStates.toProto(): DeviceSectionStatesPb = DeviceSectionStatesPb(
    channels_expanded = !channelsExpanded,
    channels_show_all = !channelsShowAll,
    contacts_expanded = !contactsExpanded,
    contacts_show_all = !contactsShowAll,
    rooms_expanded = !roomsExpanded,
    rooms_show_all = !roomsShowAll,
    repeaters_expanded = !repeatersExpanded,
    repeaters_show_all = !repeatersShowAll,
    sensors_expanded = !sensorsExpanded,
)

// --- DataStore serializer ---

object AppPreferencesSerializer : Serializer<AppPreferencesPb> {
    override val defaultValue: AppPreferencesPb = AppPreferencesPb()

    override suspend fun readFrom(input: InputStream): AppPreferencesPb =
        AppPreferencesPb.ADAPTER.decode(input)

    override suspend fun writeTo(t: AppPreferencesPb, output: OutputStream) {
        AppPreferencesPb.ADAPTER.encode(output, t)
    }
}

private val Context.appPrefsDataStore: DataStore<AppPreferencesPb> by dataStore(
    fileName = "app_preferences.pb",
    serializer = AppPreferencesSerializer,
)

// --- Repository ---

/**
 * Typed DataStore repository backed by Wire-generated proto classes.
 * Theme settings are global; section collapse/filter states are
 * per-device.
 */
class ThemePreferences(context: Context) {
    private val store: DataStore<AppPreferencesPb> = context.applicationContext.appPrefsDataStore

    val settings: Flow<ThemeSettings> = store.data.map { prefs ->
        ThemeSettings(
            mode = runCatching { ThemeMode.valueOf(prefs.theme_mode) }.getOrDefault(ThemeMode.System),
            palette = runCatching { ThemePalette.valueOf(prefs.theme_palette) }.getOrDefault(ThemePalette.Meshcore),
        )
    }

    val themeMode: Flow<ThemeMode> = settings.map { it.mode }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.updateData { it.copy(theme_mode = mode.name) }
    }

    suspend fun setThemePalette(palette: ThemePalette) {
        store.updateData { it.copy(theme_palette = palette.name) }
    }

    // --- Per-device section states ---

    fun sectionStates(deviceId: String): Flow<SectionStates> = store.data.map { prefs ->
        prefs.device_sections[deviceId]?.toDomain() ?: SectionStates()
    }

    suspend fun setSectionExpanded(deviceId: String, section: Section, expanded: Boolean) {
        store.updateData { prefs ->
            val current = prefs.device_sections[deviceId]?.toDomain() ?: SectionStates()
            val updated = when (section) {
                Section.CHANNELS -> current.copy(channelsExpanded = expanded)
                Section.CONTACTS -> current.copy(contactsExpanded = expanded)
                Section.ROOMS -> current.copy(roomsExpanded = expanded)
                Section.REPEATERS -> current.copy(repeatersExpanded = expanded)
                Section.SENSORS -> current.copy(sensorsExpanded = expanded)
            }
            prefs.copy(device_sections = prefs.device_sections + (deviceId to updated.toProto()))
        }
    }

    suspend fun setSectionShowAll(deviceId: String, section: Section, showAll: Boolean) {
        store.updateData { prefs ->
            val current = prefs.device_sections[deviceId]?.toDomain() ?: SectionStates()
            val updated = when (section) {
                Section.CHANNELS -> current.copy(channelsShowAll = showAll)
                Section.CONTACTS -> current.copy(contactsShowAll = showAll)
                Section.ROOMS -> current.copy(roomsShowAll = showAll)
                Section.REPEATERS -> current.copy(repeatersShowAll = showAll)
                else -> return@updateData prefs
            }
            prefs.copy(device_sections = prefs.device_sections + (deviceId to updated.toProto()))
        }
    }
}
