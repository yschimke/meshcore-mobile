package ee.schimke.meshcore.app.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import ee.schimke.meshcore.app.data.proto.AppPreferencesPb
import ee.schimke.meshcore.app.data.proto.DeviceSectionStatesPb
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Section / SectionStates moved to :meshcore-components (shared); re-exported so
// existing app.ui.theme call-sites keep resolving.
typealias Section = ee.schimke.meshcore.components.ui.theme.Section

typealias SectionStates = ee.schimke.meshcore.components.ui.theme.SectionStates

// --- Domain types ---

// --- Wire proto ↔ domain mapping ---
//
// Proto3 defaults bools to false, but we want "true" as the UI
// default for expansion (sections start expanded). The proto stores
// the *inverted* sense: a field being true means the section is
// collapsed. When no entry exists for a device, expansion defaults to true.

private fun DeviceSectionStatesPb.toDomain(): SectionStates =
  SectionStates(
    channelsExpanded = !channels_expanded,
    contactsExpanded = !contacts_expanded,
    roomsExpanded = !rooms_expanded,
    repeatersExpanded = !repeaters_expanded,
    sensorsExpanded = !sensors_expanded,
  )

private fun SectionStates.toProto(): DeviceSectionStatesPb =
  DeviceSectionStatesPb(
    channels_expanded = !channelsExpanded,
    contacts_expanded = !contactsExpanded,
    rooms_expanded = !roomsExpanded,
    repeaters_expanded = !repeatersExpanded,
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

private val Context.appPrefsDataStore: DataStore<AppPreferencesPb> by
  dataStore(
    fileName = "app_preferences.pb",
    serializer = AppPreferencesSerializer,
  )

// --- Repository ---

/**
 * Typed DataStore repository backed by Wire-generated proto classes. The developer-options flag is
 * global; section collapse states are per-device.
 */
class ThemePreferences(context: Context) {
  private val store: DataStore<AppPreferencesPb> = context.applicationContext.appPrefsDataStore

  /** Developer options (unlocked from Settings): extra transports, tools and diagnostics. */
  val developerMode: Flow<Boolean> = store.data.map { it.developer_mode }

  suspend fun setDeveloperMode(enabled: Boolean) {
    store.updateData { it.copy(developer_mode = enabled) }
  }

  // --- Per-device section states ---

  fun sectionStates(deviceId: String): Flow<SectionStates> =
    store.data.map { prefs -> prefs.device_sections[deviceId]?.toDomain() ?: SectionStates() }

  suspend fun setSectionExpanded(deviceId: String, section: Section, expanded: Boolean) {
    store.updateData { prefs ->
      val current = prefs.device_sections[deviceId]?.toDomain() ?: SectionStates()
      val updated =
        when (section) {
          Section.CHANNELS -> current.copy(channelsExpanded = expanded)
          Section.CONTACTS -> current.copy(contactsExpanded = expanded)
          Section.ROOMS -> current.copy(roomsExpanded = expanded)
          Section.REPEATERS -> current.copy(repeatersExpanded = expanded)
          Section.SENSORS -> current.copy(sensorsExpanded = expanded)
        }
      prefs.copy(device_sections = prefs.device_sections + (deviceId to updated.toProto()))
    }
  }
}
