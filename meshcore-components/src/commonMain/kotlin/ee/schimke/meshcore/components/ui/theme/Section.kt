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
 * Per-device expand/collapse state for the Device screen sections. Pure UI state (the app persists
 * it via datastore; this is the shared domain shape).
 */
data class SectionStates(
  val channelsExpanded: Boolean = true,
  val contactsExpanded: Boolean = true,
  val roomsExpanded: Boolean = true,
  val repeatersExpanded: Boolean = true,
  val sensorsExpanded: Boolean = true,
)
