package ee.schimke.meshcore.app.debug

import ee.schimke.meshcore.app.ui.COMMANDS_CHANNEL_NAME

/**
 * Central allowlists for the debug adb surface. Kept in one place so a
 * review can see everything that can be triggered over `adb shell`
 * without touching the UI.
 *
 * Convention: ship each list empty or with a single conservative entry.
 * Broaden locally when running a specific test; don't commit broadening.
 */
internal object DebugAllowlists {

    /**
     * Contact names that can receive destructive or stateful operations
     * — `--login`, `--logout`, `--send-direct`. Name-scoped so a typo
     * can't fan out to every contact.
     */
    val contacts: Set<String> = setOf(
        "Kodu Room",
    )

    /** Channel names that `--send-channel` is permitted to transmit on. */
    val sendChannelNames: Set<String> = setOf(
        COMMANDS_CHANNEL_NAME,
    )

    /** Self/advert names that `--set-advert-name` may assign to the device. */
    val selfNames: Set<String> = emptySet()

    /** BLE identifiers the `DEBUG_CONNECT` broadcast may connect to. */
    val bleIdentifiers: Set<String> = setOf(
        "C7:8D:8C:45:5F:78",
    )

    /** TCP "host:port" targets the `DEBUG_CONNECT` broadcast may connect to. */
    val tcpTargets: Set<String> = emptySet()

    /**
     * Literal confirmation tokens for verbs that wipe or reboot the
     * device. The extra token prevents accidental firings from command
     * history.
     */
    const val REBOOT_TOKEN: String = "REBOOT_YES"
    const val FACTORY_RESET_TOKEN: String = "WIPE_YES"
}
