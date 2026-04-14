package ee.schimke.meshcore.app.debug

import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.core.model.ContactType
import java.io.PrintWriter

/**
 * Dumpsys argument dispatcher. Called from
 * `MeshcoreConnectionService.dump()` via [DebugBridge].
 *
 * Verb reference lives in [printHelp] — keep it in sync when adding
 * new verbs.
 */
internal object DebugDump {

    fun dispatch(
        app: MeshcoreApp,
        writer: PrintWriter,
        args: Array<String>?,
        events: DebugEventBuffer,
    ) {
        val argv = args ?: emptyArray()
        when (argv.getOrNull(0)) {
            null -> DebugDumpRead.summary(writer, app, events)
            "--help", "-h" -> printHelp(writer)

            // Read-only
            "--contacts" -> DebugDumpRead.contacts(writer, app, null)
            "--rooms" -> DebugDumpRead.contacts(writer, app, ContactType.ROOM)
            "--repeaters" -> DebugDumpRead.contacts(writer, app, ContactType.REPEATER)
            "--chats" -> DebugDumpRead.contacts(writer, app, ContactType.CHAT)
            "--sensors" -> DebugDumpRead.contacts(writer, app, ContactType.SENSOR)
            "--contact" -> argv.getOrNull(1)
                ?.let { DebugDumpRead.contact(writer, app, it) }
                ?: writer.println("usage: --contact <name-or-pubkey-prefix>")
            "--channels" -> DebugDumpRead.channels(writer, app)
            "--channel-messages" -> {
                val idx = argv.getOrNull(1)?.toIntOrNull()
                if (idx == null) {
                    writer.println("usage: --channel-messages <index> [limit]")
                } else {
                    val limit = argv.getOrNull(2)?.toIntOrNull() ?: 20
                    DebugDumpRead.channelMessages(writer, app, idx, limit)
                }
            }
            "--messages" -> argv.getOrNull(1)
                ?.let { DebugDumpRead.directMessages(writer, app, it) }
                ?: writer.println("usage: --messages <name-or-pubkey-prefix>")
            "--events" -> {
                val n = argv.getOrNull(1)?.toIntOrNull() ?: 20
                DebugDumpRead.events(writer, events, n)
            }
            "--saved" -> DebugDumpRead.savedDevices(writer, app)

            // Actions
            "--sync" -> DebugDumpActions.sync(writer, app)
            "--login" -> {
                val q = argv.getOrNull(1)
                if (q == null) {
                    writer.println("usage: --login <contact> [password]")
                } else {
                    DebugDumpActions.login(writer, app, q, argv.getOrNull(2).orEmpty())
                }
            }
            "--send-direct" -> {
                val q = argv.getOrNull(1)
                val text = argv.drop(2).joinToString(" ")
                if (q == null || text.isEmpty()) {
                    writer.println("usage: --send-direct <contact> <text…>")
                } else {
                    DebugDumpActions.sendDirect(writer, app, q, text)
                }
            }
            "--send-channel" -> {
                val q = argv.getOrNull(1)
                val text = argv.drop(2).joinToString(" ")
                if (q == null || text.isEmpty()) {
                    writer.println("usage: --send-channel <channel-name-or-idx> <text…>")
                } else {
                    DebugDumpActions.sendChannel(writer, app, q, text)
                }
            }
            "--send-advert" -> {
                val flood = argv.drop(1).contains("--flood")
                DebugDumpActions.sendAdvert(writer, app, flood)
            }
            "--set-advert-name" -> argv.getOrNull(1)
                ?.let { DebugDumpActions.setAdvertName(writer, app, it) }
                ?: writer.println("usage: --set-advert-name <name>")
            "--reboot" -> {
                val tokenIdx = argv.indexOf("--confirm")
                val token = if (tokenIdx >= 0) argv.getOrNull(tokenIdx + 1) else null
                DebugDumpActions.reboot(writer, app, token)
            }

            else -> {
                writer.println("unknown args: ${argv.toList()}")
                printHelp(writer)
            }
        }
    }

    private fun printHelp(w: PrintWriter) {
        w.println(
            """
            Usage: dumpsys activity service MeshcoreConnectionService [ARGS]

            Read-only
              (no args)                         connection + device summary
              --contacts                        all contacts
              --rooms | --repeaters
              --chats | --sensors               typed contact lists
              --contact <q>                     one contact (name or pubkey prefix)
              --channels                        channel list
              --channel-messages <idx> [N]      last N messages on channel idx (default 20)
              --messages <contact>              direct messages cached for contact
              --events [N]                      last N MeshEvents (default 20)
              --saved                           all devices in the Room DB

            Actions (allowlist-gated; see DebugAllowlists.kt)
              --sync                            drain pending device queue
              --login <q> [pw]                  authenticate to room/repeater
              --send-direct <q> <text…>         send a direct message
              --send-channel <ch> <text…>       send a channel message (name or index)
              --send-advert [--flood]           broadcast a self-advert
              --set-advert-name <name>          rename the local radio
              --reboot --confirm REBOOT_YES     reboot the local radio

              --help                            this text
            """.trimIndent(),
        )
    }
}
