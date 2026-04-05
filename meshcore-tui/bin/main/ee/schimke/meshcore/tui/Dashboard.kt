package ee.schimke.meshcore.tui

import com.github.ajalt.mordant.animation.textAnimation
import com.github.ajalt.mordant.rendering.TextColors.brightBlue
import com.github.ajalt.mordant.rendering.TextColors.brightGreen
import com.github.ajalt.mordant.rendering.TextColors.brightMagenta
import com.github.ajalt.mordant.rendering.TextColors.brightYellow
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.transport.TcpTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Live terminal dashboard for a connected MeshCore device. Repaints on
 * a 500ms tick using Mordant's text animation; keeps a rolling log of
 * the most recent [historySize] events parsed from the device.
 */
class Dashboard(
    private val host: String,
    private val port: Int,
    private val historySize: Int,
) {
    private val terminal = Terminal()
    private val events = ArrayDeque<String>()

    fun run(): Unit = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val transport = TcpTransport(host, port)
        try {
            transport.connect()
        } catch (t: Throwable) {
            terminal.println("Failed to connect to $host:$port — ${t.message}")
            return@runBlocking
        }
        val client = MeshCoreClient(transport, scope)
        client.start()

        val animation = terminal.textAnimation<Unit> { render(client) }
        val collectorJob: Job = scope.launch { collectEvents(client) }
        try {
            while (true) {
                animation.update(Unit)
                delay(500)
            }
        } finally {
            collectorJob.cancel()
            animation.stop()
            client.stop()
            transport.close()
            scope.cancel()
        }
    }

    private suspend fun collectEvents(client: MeshCoreClient) {
        client.events.collect { ev ->
            val line = when (ev) {
                is MeshEvent.DirectMessage -> "${brightGreen("DM")} ${ev.message.senderPrefix} ${ev.message.text}"
                is MeshEvent.ChannelMessage -> "${brightMagenta("CH${ev.message.channelIndex}")} ${ev.message.body}"
                is MeshEvent.Advert -> "${brightYellow("ADV")} ${ev.info.publicKey}"
                is MeshEvent.NewAdvert -> "${brightYellow("NEW")} ${ev.info.publicKey}"
                is MeshEvent.SendConfirmedEvent -> "${brightGreen("ACK")} rtt=${ev.confirmed.tripTimeMs}ms"
                MeshEvent.MessagesWaiting -> gray("messages waiting…")
                else -> null
            } ?: return@collect
            events.addLast(line)
            while (events.size > historySize) events.removeFirst()
        }
    }

    private fun render(client: MeshCoreClient): String {
        val self = client.selfInfo.value
        val battery = client.battery.value
        val radio = client.radio.value
        val contacts = client.contacts.value

        val header = (bold + brightBlue)("MeshCore — ${self?.name ?: "(connecting)"}")
        val statusTable = table {
            header { row("field", "value") }
            body {
                row("pubkey", self?.publicKey?.toString()?.take(12)?.plus("…") ?: "—")
                row("radio", radio?.let { "${it.frequencyHz / 1_000_000.0} MHz SF${it.spreadingFactor} BW${it.bandwidthHz / 1_000}" } ?: "—")
                row("battery", battery?.let { cyan("${it.estimatePercent()}% (${it.millivolts} mV)") } ?: "—")
                row("contacts", contacts.size.toString())
            }
        }
        val log = if (events.isEmpty()) gray("(no events yet)") else events.joinToString("\n")
        return buildString {
            appendLine(header)
            appendLine(terminal.render(statusTable))
            appendLine(bold("Events"))
            append(log)
        }
    }
}
