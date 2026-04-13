package ee.schimke.meshcore.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import ee.schimke.meshcore.app.MainActivity
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.R
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import java.io.FileDescriptor
import java.io.PrintWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Contact names allowed to receive destructive or stateful operations
 * (login, send, reboot, …) via the `dumpsys` debug interface. The
 * default empty set makes the dump service strictly read-only.
 *
 * Edit this set locally when you need to script a specific device from
 * adb — e.g. `setOf("HomeRoom", "DevRepeater")`. Keep it small and
 * name-scoped so an accidental typo can't fan out across every contact.
 */
private val ALLOWED_COMMAND_CONTACTS: Set<String> = setOf(
    // Testing Room
    "Kodu Room"
)

/**
 * Foreground service that maintains a persistent notification while a
 * Bluetooth connection is active. The invariant is:
 *
 *   **BT connected ⟺ this service is running ⟺ notification is visible.**
 *
 * - [AppConnectionController] starts this service on connect and stops
 *   it on disconnect.
 * - If the service is destroyed for any other reason (task-swipe, system
 *   reclaim), [onDestroy] triggers a disconnect so no BT connection can
 *   exist without the notification.
 * - The notification includes a "Disconnect" action that stops the
 *   service (which in turn disconnects BT via [onDestroy]).
 * - On Android 14+ users can dismiss foreground-service notifications;
 *   [onTaskRemoved] and [deleteIntent] handle that edge-case by also
 *   stopping the service.
 */
class MeshcoreConnectionService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            Log.d(TAG, "Disconnect action received")
            disconnect()
            return START_NOT_STICKY
        }

        val deviceLabel = intent?.getStringExtra(EXTRA_DEVICE_LABEL) ?: "MeshCore device"
        val notification = buildNotification(deviceLabel)
        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away — tear down BT to maintain the invariant.
        disconnect()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Exposes live device state to `adb shell dumpsys activity service
     * ee.schimke.meshcore/.service.MeshcoreConnectionService`. Runs on
     * a binder thread; suspend work uses `runBlocking` with a bounded
     * timeout so AMS's dumpsys timer doesn't trip.
     *
     * Destructive verbs (currently `--login`) are gated by
     * [ALLOWED_COMMAND_CONTACTS] — by default the dump is strictly
     * read-only.
     */
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<String>?) {
        val app = applicationContext as? MeshcoreApp
        if (app == null) {
            writer.println("MeshcoreApp unavailable")
            return
        }
        try {
            when (args?.getOrNull(0)) {
                null -> dumpSummary(writer, app)
                "--help", "-h" -> printHelp(writer)
                "--contacts" -> dumpContacts(writer, app, null)
                "--rooms" -> dumpContacts(writer, app, ContactType.ROOM)
                "--repeaters" -> dumpContacts(writer, app, ContactType.REPEATER)
                "--chats" -> dumpContacts(writer, app, ContactType.CHAT)
                "--sensors" -> dumpContacts(writer, app, ContactType.SENSOR)
                "--contact" -> {
                    val q = args.getOrNull(1)
                    if (q == null) writer.println("usage: --contact <name-or-pubkey-prefix>")
                    else dumpContact(writer, app, q)
                }
                "--channels" -> dumpChannels(writer, app)
                "--messages" -> {
                    val q = args.getOrNull(1)
                    if (q == null) writer.println("usage: --messages <name-or-pubkey-prefix>")
                    else dumpMessages(writer, app, q)
                }
                "--sync" -> doSync(writer, app)
                "--login" -> {
                    val name = args.getOrNull(1)
                    if (name == null) {
                        writer.println("usage: --login <contact-name> [password]")
                        return
                    }
                    val password = args.getOrNull(2).orEmpty()
                    doLogin(writer, app, name, password)
                }
                else -> {
                    writer.println("unknown args: ${args.toList()}")
                    printHelp(writer)
                }
            }
        } catch (t: Throwable) {
            writer.println("error: ${t::class.simpleName}: ${t.message}")
        }
        writer.flush()
    }

    // ------------------------------------------------------------------

    private fun disconnect() {
        Log.d(TAG, "Service stopping — disconnecting BT")
        val app = applicationContext as? MeshcoreApp ?: return
        app.connectionController.cancel()
        stopSelf()
    }

    private fun buildNotification(deviceLabel: String): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val disconnectIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MeshcoreConnectionService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        // deleteIntent fires if the user dismisses the notification
        // (possible on Android 14+ for foreground services).
        val deleteIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MeshcoreConnectionService::class.java).apply {
                action = ACTION_DISCONNECT
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_bluetooth_connected)
            .setContentTitle("Connected to $deviceLabel")
            .setContentText("MeshCore — tap to open")
            .setContentIntent(openAppIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    null, "Disconnect", disconnectIntent,
                ).build(),
            )
            .build()
    }

    // ---- dump helpers ------------------------------------------------

    private fun printHelp(w: PrintWriter) {
        w.println(
            """
            Usage: dumpsys activity service MeshcoreConnectionService [ARGS]

              (no args)               connection + device summary
              --contacts              list all contacts
              --rooms                 list rooms only
              --repeaters             list repeaters only
              --chats                 list direct-chat contacts only
              --sensors               list sensor contacts only
              --contact <q>           full detail for one contact (name or pubkey prefix)
              --channels              list channels
              --login <q> [pw]        authenticate to a room/repeater contact
                                      (q = name or pubkey prefix; only contacts
                                      whose name is in ALLOWED_COMMAND_CONTACTS)
              --help                  this text
            """.trimIndent(),
        )
    }

    private fun requireClient(w: PrintWriter, app: MeshcoreApp): MeshCoreClient? {
        val state = app.connectionController.state.value
        if (state !is ConnectionUiState.Connected) {
            w.println("Not connected (state=${state::class.simpleName}).")
            return null
        }
        return state.client
    }

    private fun dumpSummary(w: PrintWriter, app: MeshcoreApp) {
        val controller = app.connectionController
        val state = controller.state.value
        w.println("ConnectionUiState: ${state::class.simpleName}")
        w.println("connectedDeviceId: ${controller.connectedDeviceId.value ?: "-"}")
        val warnings = controller.warnings.value
        if (warnings.isNotEmpty()) w.println("warnings: $warnings")

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
        w.println("allowedCommandContacts: $ALLOWED_COMMAND_CONTACTS")
        w.println()
        w.println("Run with --help for more options.")
    }

    private fun dumpContacts(w: PrintWriter, app: MeshcoreApp, filter: ContactType?) {
        val client = requireClient(w, app) ?: return
        val list = client.contacts.value.let { all ->
            if (filter == null) all else all.filter { it.type == filter }
        }
        w.println("${filter?.name ?: "CONTACTS"}: ${list.size}")
        list.sortedBy { it.name.lowercase() }.forEach { w.println("  ${contactLine(it)}") }
    }

    private fun dumpContact(w: PrintWriter, app: MeshcoreApp, query: String) {
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
                w.println("path:       ${if (c.isFlood) "flood" else "len=${c.pathLength} hex=${c.path.toByteArray().joinToString("") { "%02x".format(it) }}"}")
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

    private fun dumpMessages(w: PrintWriter, app: MeshcoreApp, query: String) {
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

    private fun doSync(w: PrintWriter, app: MeshcoreApp) {
        val client = requireClient(w, app) ?: return
        w.println("syncMessages: draining …")
        val result = runCatching {
            runBlocking(Dispatchers.Default) {
                withTimeout(LOGIN_DUMP_TIMEOUT_MS) { client.syncMessages() }
            }
        }
        if (result.isSuccess) w.println("syncMessages: done")
        else w.println("syncMessages: ${result.exceptionOrNull()?.javaClass?.simpleName}: ${result.exceptionOrNull()?.message}")
    }

    private fun dumpChannels(w: PrintWriter, app: MeshcoreApp) {
        val client = requireClient(w, app) ?: return
        val chs = client.channels.value
        w.println("CHANNELS: ${chs.size}")
        chs.forEach { c ->
            val pskHex = c.psk.toByteArray().take(4).joinToString("") { "%02x".format(it) }
            w.println("  [${c.index}] name='${c.name}' psk=${pskHex}…")
        }
    }

    private fun doLogin(w: PrintWriter, app: MeshcoreApp, query: String, password: String) {
        val client = requireClient(w, app) ?: return
        val matches = findContacts(client, query)
        val contact = when (matches.size) {
            0 -> { w.println("no contact matches '$query'"); return }
            1 -> matches[0]
            else -> {
                w.println("ambiguous '$query' — ${matches.size} matches; use a longer pubkey prefix:")
                matches.forEach { w.println("  ${contactLine(it)}") }
                return
            }
        }
        if (contact.name !in ALLOWED_COMMAND_CONTACTS) {
            w.println("refused: '${contact.name}' is not in ALLOWED_COMMAND_CONTACTS.")
            w.println("Add the contact name to ALLOWED_COMMAND_CONTACTS in MeshcoreConnectionService.kt,")
            w.println("rebuild, and try again. Default is empty (read-only dump).")
            return
        }
        if (contact.type != ContactType.ROOM && contact.type != ContactType.REPEATER) {
            w.println("contact '${contact.name}' is ${contact.type}; login only applies to ROOM/REPEATER")
            return
        }
        w.println("login → ${contact.name} (${contact.type}) pubkey=${contact.publicKey.toHex().take(12)}…")
        val startMs = System.currentTimeMillis()
        val result = runCatching {
            runBlocking(Dispatchers.Default) {
                withTimeout(LOGIN_DUMP_TIMEOUT_MS) {
                    client.login(contact.publicKey, password, timeoutMs = LOGIN_DUMP_TIMEOUT_MS)
                }
            }
        }
        val elapsed = System.currentTimeMillis() - startMs
        if (result.isSuccess) {
            w.println("LoginSuccess (${elapsed}ms)")
        } else {
            val e = result.exceptionOrNull()
            w.println("LoginFail (${elapsed}ms): ${e?.javaClass?.simpleName}: ${e?.message}")
        }
    }

    private fun findContacts(client: MeshCoreClient, query: String): List<Contact> {
        val byName = client.contacts.value.filter { it.name.equals(query, ignoreCase = true) }
        if (byName.isNotEmpty()) return byName
        val q = query.lowercase()
        return client.contacts.value.filter { it.publicKey.toHex().startsWith(q) }
    }

    private fun contactLine(c: Contact): String {
        val path = if (c.isFlood) "flood" else "hops=${c.pathLength}"
        return "${c.type.name.padEnd(9)} ${c.publicKey.toHex().take(12)} $path  ${c.name}"
    }

    // ------------------------------------------------------------------

    companion object {
        private const val TAG = "MeshcoreConnSvc"

        // Stay well under AMS's ~10s dumpsys watchdog so the timeout
        // path has time to print a result instead of the caller seeing
        // "IOException: Timeout".
        private const val LOGIN_DUMP_TIMEOUT_MS = 4_000L
        private const val CHANNEL_ID = "meshcore_connection"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_DISCONNECT = "ee.schimke.meshcore.DISCONNECT"
        private const val EXTRA_DEVICE_LABEL = "device_label"

        fun start(context: Context, deviceLabel: String) {
            val intent = Intent(context, MeshcoreConnectionService::class.java).apply {
                putExtra(EXTRA_DEVICE_LABEL, deviceLabel)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshcoreConnectionService::class.java))
        }

        private fun ensureNotificationChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Active Connection",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shown while connected to a MeshCore device over Bluetooth"
            }
            nm.createNotificationChannel(channel)
        }
    }
}
