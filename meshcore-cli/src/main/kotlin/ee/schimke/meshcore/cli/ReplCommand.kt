package ee.schimke.meshcore.cli

import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.brightBlue
import com.github.ajalt.mordant.rendering.TextColors.cyan
import com.github.ajalt.mordant.rendering.TextColors.gray
import com.github.ajalt.mordant.rendering.TextColors.green
import com.github.ajalt.mordant.rendering.TextColors.red
import com.github.ajalt.mordant.rendering.TextColors.yellow
import com.github.ajalt.mordant.rendering.TextStyles.bold
import com.github.ajalt.mordant.table.table
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.transport.TransportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock

/**
 * Interactive shell over one connection. Reads commands from stdin, one per
 * line, and writes results to stdout. In `--json` mode each command emits a
 * single JSON object/array line, making it friendly to scripted pipelines.
 */
class ReplCommand : SessionCommand(
    name = "repl",
    help = "Interactive shell — connects once and accepts commands until quit",
) {
    private val jsonMode by option("--json").flag()
        .help("Emit JSON objects to stdout (one per line) instead of formatted text")

    private val json = Json { prettyPrint = false }

    override fun run() = try {
        withClient { client -> runRepl(client) }
    } catch (t: Throwable) {
        val msg = t.message ?: t.toString()
        if (jsonMode) emit(buildJsonObject { put("error", "failed to connect: $msg") })
        else terminal.println(red("failed to connect: $msg"))
    }

    private suspend fun runRepl(client: MeshCoreClient) {
        val watcherScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val watcher: Job = watcherScope.launch {
            // Report mid-session transport transitions so the user notices drops.
            client.connection.drop(1).collect { reportConnectionChange(it) }
        }
        try {
            if (!jsonMode) printBanner(client)
            while (true) {
                if (!jsonMode) {
                    val name = client.selfInfo.value?.name ?: "meshcore"
                    terminal.print("${brightBlue(name)}> ")
                }
                val line = readlnOrNull()?.trim() ?: break
                if (line.isEmpty()) continue
                val done = dispatch(client, line)
                if (done) break
            }
            if (jsonMode) emit(buildJsonObject { put("goodbye", true) })
            else terminal.println(gray("bye."))
        } finally {
            watcher.cancel()
            watcherScope.cancel()
        }
    }

    private fun reportConnectionChange(state: TransportState) {
        val label = describe(state)
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put("connection", label)
                    if (state is TransportState.Error) put("cause", state.cause.message)
                },
            )
        } else {
            val msg = "connection: $label" + if (state is TransportState.Error) " (${state.cause.message})" else ""
            terminal.println(red(msg))
        }
    }

    private fun describe(state: TransportState): String = when (state) {
        TransportState.Connected -> "connected"
        TransportState.Connecting -> "connecting"
        TransportState.Disconnected -> "disconnected"
        is TransportState.Error -> "error"
    }

    private fun printBanner(client: MeshCoreClient) {
        val self = client.selfInfo.value
        val conn = describe(client.connection.value)
        terminal.println(
            bold("MeshCore REPL") + gray(" — ") + (self?.name ?: "(unknown)") + gray(" [$conn]"),
        )
        terminal.println(gray("Type 'help' for commands, 'status' for connection info, 'quit' to exit."))
    }

    private fun status(client: MeshCoreClient) {
        val conn = describe(client.connection.value)
        val self = client.selfInfo.value
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put("connection", conn)
                    put("self_info_received", self != null)
                    put("name", self?.name)
                },
            )
        } else {
            terminal.println("Connection: $conn")
            terminal.println("SelfInfo   : " + if (self != null) "received (${self.name})" else gray("(none yet)"))
        }
    }

    /** Returns true if the REPL should exit. */
    private suspend fun dispatch(client: MeshCoreClient, line: String): Boolean {
        val tokens = tokenize(line)
        if (tokens.isEmpty()) return false
        val cmd = tokens[0].lowercase()
        val args = tokens.drop(1)
        return try {
            when (cmd) {
                "quit", "exit", "q" -> true
                "help", "?" -> { help(); false }
                "status" -> { status(client); false }
                "info", "i" -> { info(client); false }
                "contacts", "c", "ls" -> { contacts(client); false }
                "battery", "bat" -> { battery(client); false }
                "radio" -> { radio(client, args); false }
                "send", "msg" -> { send(client, args); false }
                "login" -> { login(client, args); false }
                "sync", "sync_msgs" -> { sync(client); false }
                else -> { error("unknown command: $cmd — try 'help'"); false }
            }
        } catch (t: Throwable) {
            error(t.message ?: t.toString())
            false
        }
    }

    // ── Commands ────────────────────────────────────────────────────────────

    private fun help() {
        val helpText = """
            |Commands
            |  info, i                         self info, radio, battery
            |  contacts, c, ls                 contact list
            |  battery, bat                    battery + storage
            |  radio                           show radio params
            |  radio set <freqHz> <bwHz> <sf> <cr>
            |                                  update radio params
            |  send <name> <text…>             send DM; <name> matches contact substring
            |  login <name> <password>         login to a repeater/room server
            |  sync                            pull unread messages from the device
            |  help, ?                         this help
            |  quit, exit, q                   leave REPL
        """.trimMargin()
        if (jsonMode) emit(buildJsonObject { put("help", helpText) })
        else terminal.println(helpText)
    }

    private fun info(client: MeshCoreClient) {
        val self = client.selfInfo.value
        val radio = client.radio.value
        val bat = client.battery.value
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put("name", self?.name)
                    put("pubkey", self?.publicKey?.toString())
                    put("latitude", self?.latitude)
                    put("longitude", self?.longitude)
                    put("tx_power_dbm", self?.txPowerDbm)
                    put("max_tx_power_dbm", self?.maxPowerDbm)
                    radio?.let {
                        put("radio_freq_hz", it.frequencyHz)
                        put("radio_bw_hz", it.bandwidthHz)
                        put("radio_sf", it.spreadingFactor)
                        put("radio_cr", it.codingRate)
                    }
                    bat?.let {
                        put("battery_mv", it.millivolts)
                        put("battery_pct", it.estimatePercent())
                        put("storage_used_kb", it.storageUsedKb)
                        put("storage_total_kb", it.storageTotalKb)
                    }
                },
            )
            return
        }
        terminal.println(
            table {
                header { row("Field", "Value") }
                body {
                    row("name", self?.name ?: "(unknown)")
                    row("pubkey", self?.publicKey?.toString() ?: "—")
                    row("location", self?.let { "${it.latitude}, ${it.longitude}" } ?: "—")
                    row("tx power", self?.let { "${it.txPowerDbm} / ${it.maxPowerDbm} dBm" } ?: "—")
                    radio?.let {
                        row("frequency", "${it.frequencyHz / 1_000_000.0} MHz")
                        row("bandwidth", "${it.bandwidthHz / 1_000.0} kHz")
                        row("spreading", "SF${it.spreadingFactor}")
                        row("coding rate", "4/${it.codingRate}")
                    }
                    bat?.let {
                        row("battery", cyan("${it.millivolts} mV (${it.estimatePercent()}%)"))
                        row("storage", "${it.storageUsedKb} / ${it.storageTotalKb} kB")
                    }
                }
            },
        )
    }

    private suspend fun contacts(client: MeshCoreClient) {
        val list = client.getContacts()
        if (jsonMode) {
            emit(
                buildJsonArray {
                    list.forEach { c ->
                        add(
                            buildJsonObject {
                                put("name", c.name)
                                put("type", c.type.name)
                                put("pubkey", c.publicKey.toString())
                                put("is_flood", c.isFlood)
                                put("path_length", c.pathLength)
                            },
                        )
                    }
                },
            )
            return
        }
        terminal.println(bold("${list.size} contacts"))
        if (list.isEmpty()) return
        terminal.println(
            table {
                header { row("name", "type", "path", "pubkey") }
                body {
                    list.forEach { c ->
                        val path = if (c.isFlood) yellow("flood") else green("${c.pathLength} hops")
                        row(c.name, c.type.name, path, c.publicKey.toString().take(12) + "…")
                    }
                }
            },
        )
    }

    private suspend fun battery(client: MeshCoreClient) {
        val b = client.getBatteryAndStorage()
        val pct = b.estimatePercent()
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put("millivolts", b.millivolts)
                    put("percent", pct)
                    put("storage_used_kb", b.storageUsedKb)
                    put("storage_total_kb", b.storageTotalKb)
                },
            )
            return
        }
        val colored = when {
            pct >= 60 -> green("$pct%")
            pct >= 25 -> yellow("$pct%")
            else -> red("$pct%")
        }
        terminal.println("Battery: ${b.millivolts} mV ($colored)")
        terminal.println("Storage: ${b.storageUsedKb} / ${b.storageTotalKb} kB")
    }

    private suspend fun radio(client: MeshCoreClient, args: List<String>) {
        if (args.firstOrNull()?.lowercase() == "set") {
            val rest = args.drop(1)
            if (rest.size != 4) {
                error("usage: radio set <freqHz> <bwHz> <sf> <cr>"); return
            }
            val ints = rest.map { it.toIntOrNull() }
            if (ints.any { it == null }) {
                error("radio set: all four values must be integers"); return
            }
            client.setRadioParams(ints[0]!!, ints[1]!!, ints[2]!!, ints[3]!!)
        }
        val r = client.getRadioSettings()
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put("freq_hz", r.frequencyHz)
                    put("bw_hz", r.bandwidthHz)
                    put("sf", r.spreadingFactor)
                    put("cr", r.codingRate)
                },
            )
            return
        }
        terminal.println("Frequency  : ${r.frequencyHz / 1_000_000.0} MHz")
        terminal.println("Bandwidth  : ${r.bandwidthHz / 1_000.0} kHz")
        terminal.println("Spreading  : SF${r.spreadingFactor}")
        terminal.println("CodingRate : 4/${r.codingRate}")
    }

    private suspend fun send(client: MeshCoreClient, args: List<String>) {
        if (args.size < 2) { error("usage: send <name-substring> <text…>"); return }
        val recipient = resolveContact(client, args[0]) ?: return
        val text = args.drop(1).joinToString(" ")
        val ack = client.sendText(
            recipient = recipient,
            text = text,
            timestamp = Clock.System.now(),
        )
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put("sent", true)
                    put("flood", ack.isFlood)
                    put("timeout_ms", ack.timeoutMs)
                },
            )
        } else {
            terminal.println(green("sent.") + " flood=${ack.isFlood} timeout=${ack.timeoutMs}ms")
        }
    }

    private suspend fun login(client: MeshCoreClient, args: List<String>) {
        if (args.size < 2) { error("usage: login <name-substring> <password>"); return }
        val recipient = resolveContact(client, args[0]) ?: return
        val password = args.drop(1).joinToString(" ")
        client.login(recipient, password)
        if (jsonMode) emit(buildJsonObject { put("login", true) })
        else terminal.println(green("login ok"))
    }

    private suspend fun sync(client: MeshCoreClient) {
        client.syncMessages()
        val direct = client.directMessages.value
        val channel = client.channelMessages.value
        if (jsonMode) {
            emit(
                buildJsonObject {
                    put(
                        "direct",
                        buildJsonArray {
                            direct.forEach { (prefix, msgs) ->
                                msgs.forEach { m ->
                                    add(
                                        buildJsonObject {
                                            put("from_prefix", prefix)
                                            put("text", m.text)
                                            put("timestamp", m.timestamp.toString())
                                        },
                                    )
                                }
                            }
                        },
                    )
                    put(
                        "channel",
                        buildJsonArray {
                            channel.forEach { (idx, msgs) ->
                                msgs.forEach { m ->
                                    add(
                                        buildJsonObject {
                                            put("channel", idx)
                                            put("text", m.text)
                                            put("timestamp", m.timestamp.toString())
                                        },
                                    )
                                }
                            }
                        },
                    )
                },
            )
            return
        }
        val dmTotal = direct.values.sumOf { it.size }
        val chTotal = channel.values.sumOf { it.size }
        terminal.println(bold("Direct ($dmTotal)"))
        direct.forEach { (prefix, msgs) ->
            msgs.forEach { m ->
                terminal.println("  ${gray(prefix.take(12))}  ${m.text}")
            }
        }
        terminal.println(bold("Channels ($chTotal)"))
        channel.forEach { (idx, msgs) ->
            msgs.forEach { m ->
                terminal.println("  ${gray("#$idx")}  ${m.text}")
            }
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    private suspend fun resolveContact(client: MeshCoreClient, needle: String): PublicKey? {
        val n = needle.lowercase()
        val match = client.getContacts().firstOrNull { it.name.lowercase().contains(n) }
        if (match == null) { error("no contact matches '$needle'"); return null }
        return match.publicKey
    }

    private fun error(message: String) {
        if (jsonMode) emit(buildJsonObject { put("error", message) })
        else terminal.println(red(message))
    }

    private fun emit(obj: JsonObject) = println(json.encodeToString(JsonObject.serializer(), obj))
    private fun emit(arr: JsonArray) = println(json.encodeToString(JsonArray.serializer(), arr))

    /** Minimal shell-style tokenizer: whitespace splits, single or double quotes group. */
    private fun tokenize(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (ch in line) {
            when {
                quote != null && ch == quote -> quote = null
                quote != null -> cur.append(ch)
                ch == '"' || ch == '\'' -> quote = ch
                ch.isWhitespace() -> {
                    if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
                }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }
}
