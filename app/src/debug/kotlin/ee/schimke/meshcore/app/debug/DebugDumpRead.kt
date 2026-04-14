package ee.schimke.meshcore.app.debug

import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import java.io.PrintWriter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Read-only dumpsys verbs. Never touches the radio or the DB. */
internal object DebugDumpRead {

    fun summary(w: PrintWriter, app: MeshcoreApp, events: DebugEventBuffer) {
        val controller = app.connectionController
        val state = controller.state.value
        w.println("ConnectionUiState:   ${state::class.simpleName}")
        w.println("connectedDeviceId:   ${controller.connectedDeviceId.value ?: "-"}")
        controller.warnings.value
            .takeIf { it.isNotEmpty() }
            ?.let { w.println("warnings: $it") }

        val client = (state as? ConnectionUiState.Connected)?.client
        if (client == null) {
            w.println("No active client.")
            return
        }
        client.selfInfo.value?.let {
            w.println("selfInfo: name='${it.name}' pubkey=${it.publicKey.toHex().take(12)} " +
                "tx=${it.txPowerDbm}dBm lat=${it.latitude} lon=${it.longitude}")
        }
        client.device.value?.let {
            w.println("device: proto=${it.protocolVersion} maxContacts=${it.maxContacts} maxChannels=${it.maxChannels}")
        }
        client.radio.value?.let {
            w.println("radio: ${it.frequencyHz}Hz bw=${it.bandwidthHz} sf=${it.spreadingFactor} cr=${it.codingRate}")
        }
        client.battery.value?.let {
            w.println("battery: ${it.millivolts}mV (~${it.estimatePercent()}%) storage=${it.storageUsedKb}/${it.storageTotalKb}kB")
        }
        val counts = client.contacts.value.groupingBy { it.type }.eachCount()
        w.println("contacts: ${client.contacts.value.size} $counts")
        w.println("channels: ${client.channels.value.size}")
        w.println("events (ring): ${events.snapshot().size}")
        w.println("allowlists: contacts=${DebugAllowlists.contacts.size} " +
            "sendChannels=${DebugAllowlists.sendChannelNames.size} " +
            "selfNames=${DebugAllowlists.selfNames.size} " +
            "ble=${DebugAllowlists.bleIdentifiers.size} " +
            "tcp=${DebugAllowlists.tcpTargets.size}")
        w.println()
        w.println("Run with --help for more options.")
    }

    fun contacts(w: PrintWriter, app: MeshcoreApp, filter: ContactType?) {
        val client = requireClient(w, app) ?: return
        val list = client.contacts.value.let { all ->
            if (filter == null) all else all.filter { it.type == filter }
        }
        w.println("${filter?.name ?: "CONTACTS"}: ${list.size}")
        list.sortedBy { it.name.lowercase() }.forEach { w.println("  ${contactLine(it)}") }
    }

    fun contact(w: PrintWriter, app: MeshcoreApp, query: String) {
        val client = requireClient(w, app) ?: return
        val matches = findContacts(client, query)
        when (matches.size) {
            0 -> w.println("no contact matches '$query'")
            1 -> {
                val c = matches[0]
                w.println("name:       ${c.name}")
                w.println("type:       ${c.type}")
                w.println("pubkey:     ${c.publicKey.toHex()}")
                w.println("flags:      0x${c.flags.toString(16)}")
                w.println("path:       ${pathStr(c)}")
                w.println("advertTs:   ${c.advertTimestamp}")
                w.println("lastMod:    ${c.lastModified}")
                w.println("lat/lon:    ${c.latitude}, ${c.longitude}")
            }
            else -> {
                w.println("ambiguous '$query' — ${matches.size} matches:")
                matches.forEach { w.println("  ${contactLine(it)}") }
            }
        }
    }

    fun channels(w: PrintWriter, app: MeshcoreApp) {
        val client = requireClient(w, app) ?: return
        val chs = client.channels.value
        w.println("CHANNELS: ${chs.size}")
        chs.forEach { c ->
            val pskHex = c.psk.toByteArray().take(4).joinToString("") { "%02x".format(it) }
            w.println("  [${c.index}] name='${c.name}' psk=${pskHex}…")
        }
    }

    fun directMessages(w: PrintWriter, app: MeshcoreApp, query: String) {
        val client = requireClient(w, app) ?: return
        val contact = findContacts(client, query).singleOrNull() ?: run {
            w.println("no unique contact for '$query'")
            return
        }
        val key = contact.publicKey.toHex()
        val msgs = client.directMessages.value[key].orEmpty()
        w.println("direct messages for ${contact.name} (${msgs.size}):")
        msgs.forEach { m ->
            w.println("  ${m.timestamp}  snr=${m.snr}  ${m.textType}  '${m.text}'")
        }
    }

    fun channelMessages(w: PrintWriter, app: MeshcoreApp, channelIdx: Int, limit: Int) {
        val deviceId = app.connectionController.connectedDeviceId.value ?: run {
            w.println("not connected to a device")
            return
        }
        val msgs = runBlocking {
            app.repository.observeChannelMessages(deviceId, channelIdx).first()
        }
        val tail = msgs.takeLast(limit)
        w.println("channel[$channelIdx] messages: showing ${tail.size} of ${msgs.size}")
        tail.forEach { m ->
            val dir = m.direction.name.take(4)
            val sender = m.senderName ?: "?"
            w.println("  ${m.timestampEpochMs}  $dir  ${m.status}  <$sender> '${m.text.take(60)}'")
        }
    }

    fun events(w: PrintWriter, events: DebugEventBuffer, limit: Int) {
        val entries = events.snapshot(limit)
        w.println("EVENTS (last ${entries.size}):")
        entries.forEach { e ->
            w.println("  ${e.timestampMs}  ${e.event}")
        }
    }

    fun savedDevices(w: PrintWriter, app: MeshcoreApp) {
        val devices = runBlocking { app.repository.observeDevices().first() }
        w.println("SAVED DEVICES: ${devices.size}")
        devices.forEach { d ->
            val star = if (d.favorite) "★" else " "
            w.println("  $star  ${d.id}  '${d.label}'  via=${d.transport::class.simpleName}  lastSeen=${d.lastConnectedAtMs}")
        }
    }

    // ---- helpers ------------------------------------------------------

    internal fun requireClient(w: PrintWriter, app: MeshcoreApp): MeshCoreClient? {
        val state = app.connectionController.state.value
        if (state !is ConnectionUiState.Connected) {
            w.println("Not connected (state=${state::class.simpleName}).")
            return null
        }
        return state.client
    }

    internal fun findContacts(client: MeshCoreClient, query: String): List<Contact> {
        val byName = client.contacts.value.filter { it.name.equals(query, ignoreCase = true) }
        if (byName.isNotEmpty()) return byName
        val q = query.lowercase()
        return client.contacts.value.filter { it.publicKey.toHex().startsWith(q) }
    }

    internal fun contactLine(c: Contact): String {
        val path = if (c.isFlood) "flood" else "hops=${c.pathLength}"
        return "${c.type.name.padEnd(9)} ${c.publicKey.toHex().take(12)} $path  ${c.name}"
    }

    private fun pathStr(c: Contact): String =
        if (c.isFlood) "flood"
        else "len=${c.pathLength} hex=${c.path.toByteArray().joinToString("") { "%02x".format(it) }}"
}
