package ee.schimke.meshcore.components.ui.theme

/** The collapsible sections on the Device screen. */
enum class Section {
  CHANNELS,
  CONTACTS,
  ROOMS,
  REPEATERS,
  SENSORS,
}

/**
 * Per-device expand/filter state for the Device screen sections. Pure UI state (the app persists it
 * via datastore; this is the shared domain shape).
 */
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
