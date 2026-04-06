package ee.schimke.meshcore.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.MeshcoreApp
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.app.ui.theme.Dimens
import ee.schimke.meshcore.app.ui.theme.MeshcoreTheme
import androidx.compose.material3.CircularProgressIndicator
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.mobile.ui.ChannelRow
import ee.schimke.meshcore.mobile.ui.ContactListEmpty
import ee.schimke.meshcore.mobile.ui.ContactRow
import ee.schimke.meshcore.mobile.ui.DeviceSummaryCard
import ee.schimke.meshcore.mobile.ui.verticalScrollbar
import kotlinx.coroutines.launch
import kotlinx.io.bytestring.ByteString
import kotlin.time.Instant

@Composable
fun DeviceScreen(
    onDisconnected: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
    onNavigateToContact: (Contact) -> Unit = {},
    onNavigateToChannel: (ChannelInfo) -> Unit = {},
) {
    val controller = MeshcoreApp.get().connectionController
    val uiState by controller.state.collectAsState()

    // Navigate back to the scanner on Idle. The controller is the
    // one holding Failed states now, so we don't need a "hasEngaged"
    // latch — the controller never flashes Idle during a connect
    // attempt, it drives Connecting→Failed→Idle in order.
    LaunchedEffect(uiState) {
        if (uiState is ConnectionUiState.Idle) onDisconnected()
    }

    when (val s = uiState) {
        is ConnectionUiState.Connected -> ConnectedDevice(
            client = s.client,
            onDisconnect = { controller.cancel() },
            onOpenThemePicker = onOpenThemePicker,
            onNavigateToContact = onNavigateToContact,
            onNavigateToChannel = onNavigateToChannel,
        )
        is ConnectionUiState.Connecting -> DeviceStatusView(
            title = "Connecting",
            onCancel = { controller.cancel() },
            onOpenThemePicker = onOpenThemePicker,
            status = DeviceConnectStatus.Connecting(
                startedAtMs = s.startedAtMs,
                timeoutMs = s.timeoutMs,
            ),
        )
        is ConnectionUiState.Failed -> DeviceStatusView(
            title = "Connection failed",
            onCancel = { controller.dismissError() },
            onOpenThemePicker = onOpenThemePicker,
            status = DeviceConnectStatus.Failed(s.cause),
        )
        ConnectionUiState.Idle -> {
            // Render the connecting shell transiently while we're
            // about to be popped by the LaunchedEffect above, so the
            // user never sees a flash of blank screen.
            DeviceStatusView(
                title = "Disconnecting",
                onCancel = { onDisconnected() },
                onOpenThemePicker = onOpenThemePicker,
                status = DeviceConnectStatus.Connecting(
                    startedAtMs = System.currentTimeMillis() - 20_000L,
                    timeoutMs = 20_000L,
                ),
            )
        }
    }
}

@Composable
private fun ConnectedDevice(
    client: ee.schimke.meshcore.core.client.MeshCoreClient,
    onDisconnect: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onNavigateToContact: (Contact) -> Unit,
    onNavigateToChannel: (ChannelInfo) -> Unit,
) {
    val self by client.selfInfo.collectAsState()
    val battery by client.battery.collectAsState()
    val radio by client.radio.collectAsState()
    val contacts by client.contacts.collectAsState()
    val channels by client.channels.collectAsState()
    val scope = rememberCoroutineScope()
    var lastMessage by remember { mutableStateOf<String?>(null) }
    var contactsLoading by remember { mutableStateOf(true) }
    var channelsLoading by remember { mutableStateOf(true) }

    // Auto-fetch contacts then channels on connect
    LaunchedEffect(client) {
        contactsLoading = true
        channelsLoading = true
        runCatching { client.getContacts() }
        contactsLoading = false
        runCatching { client.getChannels() }
        channelsLoading = false
    }

    // Listen for incoming messages
    LaunchedEffect(client) {
        client.events.collect { ev ->
            when (ev) {
                is MeshEvent.DirectMessage ->
                    lastMessage = "${ev.message.text} (SNR ${ev.message.snr})"
                is MeshEvent.ChannelMessage ->
                    lastMessage = "#${ev.message.channelIndex} ${ev.message.body}"
                else -> Unit
            }
        }
    }

    DeviceBody(
        self = self,
        battery = battery,
        radio = radio,
        contacts = contacts,
        contactsLoading = contactsLoading,
        channels = channels,
        channelsLoading = channelsLoading,
        lastMessage = lastMessage,
        onRefreshContacts = {
            scope.launch {
                contactsLoading = true
                runCatching { client.getContacts() }
                contactsLoading = false
            }
        },
        onContactClick = onNavigateToContact,
        onChannelClick = onNavigateToChannel,
        onDisconnect = onDisconnect,
        onOpenThemePicker = onOpenThemePicker,
    )
}

/**
 * Stateless device screen body. Scaffold + TopAppBar own the header
 * and insets; the content column handles its own scroll so the send
 * form is reachable on short devices.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceBody(
    self: SelfInfo?,
    battery: BatteryInfo?,
    radio: RadioSettings?,
    contacts: List<Contact>,
    contactsLoading: Boolean = false,
    channels: List<ChannelInfo> = emptyList(),
    channelsLoading: Boolean = false,
    lastMessage: String?,
    onRefreshContacts: () -> Unit,
    onContactClick: (Contact) -> Unit = {},
    onChannelClick: (ChannelInfo) -> Unit = {},
    onDisconnect: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
) {
    val scroll = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (self != null) {
                        Text(
                            text = self.name,
                            style = MaterialTheme.typography.titleLarge,
                        )
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.size(Dimens.S))
                            Text(
                                text = "Loading\u2026",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenThemePicker) {
                        Icon(
                            imageVector = Icons.Rounded.Contrast,
                            contentDescription = "Theme",
                        )
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = "Disconnect",
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScrollbar(scroll)
                .verticalScroll(scroll)
                .padding(horizontal = Dimens.ScreenPadding, vertical = Dimens.S),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            DeviceSummaryCard(self = self, radio = radio, battery = battery)

            lastMessage?.let { LastMessageBanner(it) }

            // Split contacts by type
            val chatContacts = contacts.filter { it.type == ContactType.CHAT }
            val rooms = contacts.filter { it.type == ContactType.ROOM }
            val repeaters = contacts.filter { it.type == ContactType.REPEATER }
            val sensors = contacts.filter { it.type == ContactType.SENSOR }

            // --- Contacts (DM-able peers) ---
            SectionHeader(
                text = if (contactsLoading) "Contacts" else "Contacts (${chatContacts.size})",
                action = {
                    FilledTonalButton(
                        onClick = onRefreshContacts,
                        enabled = !contactsLoading,
                    ) {
                        if (contactsLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Spacer(Modifier.size(Dimens.XS))
                        Text("Refresh")
                    }
                },
            )
            if (contactsLoading) {
                LoadingPlaceholder("Fetching contacts\u2026")
            } else if (chatContacts.isEmpty()) {
                ContactListEmpty()
            } else {
                chatContacts.forEach { contact ->
                    ContactRow(contact, onClick = { onContactClick(contact) })
                }
            }

            // --- Rooms ---
            if (contactsLoading || rooms.isNotEmpty()) {
                SectionHeader(text = if (contactsLoading) "Rooms" else "Rooms (${rooms.size})")
                if (!contactsLoading) {
                    rooms.forEach { contact ->
                        ContactRow(contact, onClick = { onContactClick(contact) })
                    }
                }
            }

            // --- Repeaters (not tappable for chat) ---
            if (!contactsLoading && repeaters.isNotEmpty()) {
                SectionHeader(text = "Repeaters (${repeaters.size})")
                repeaters.forEach { contact ->
                    ContactRow(contact)
                }
            }

            // --- Sensors ---
            if (!contactsLoading && sensors.isNotEmpty()) {
                SectionHeader(text = "Sensors (${sensors.size})")
                sensors.forEach { contact ->
                    ContactRow(contact)
                }
            }

            // --- Channels ---
            SectionHeader(
                text = if (channelsLoading) "Channels" else "Channels (${channels.size})",
            )
            if (channelsLoading) {
                LoadingPlaceholder("Fetching channels\u2026")
            } else if (channels.isEmpty()) {
                Text(
                    text = "No channels configured",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                channels.forEach { channel ->
                    ChannelRow(channel, onClick = { onChannelClick(channel) })
                }
            }

            Spacer(Modifier.size(Dimens.L))
        }
    }
}

@Composable
private fun LoadingPlaceholder(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String, action: @Composable (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun LastMessageBanner(text: String) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Message,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(Dimens.S))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Last message",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// --- Connecting / Failed status view -------------------------------------

sealed class DeviceConnectStatus {
    /**
     * [startedAtMs] is a wall-clock timestamp (System.currentTimeMillis).
     * The connecting card animates its own progress against the current
     * time — it does NOT need periodic elapsed updates from the caller.
     */
    data class Connecting(val startedAtMs: Long, val timeoutMs: Long) : DeviceConnectStatus()
    data class Failed(val cause: Throwable) : DeviceConnectStatus()
}

/**
 * Full-screen status view shown when the device screen is visible but
 * we don't yet have a live client — either because the transport is
 * still connecting or because it failed. The failure variant surfaces
 * the exception message *and* a scrollable stack trace so the user can
 * diagnose why their radio didn't respond.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceStatusView(
    title: String,
    status: DeviceConnectStatus,
    onCancel: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                actions = {
                    IconButton(onClick = onOpenThemePicker) {
                        Icon(Icons.Rounded.Contrast, contentDescription = "Theme")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(Dimens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.CardGap),
        ) {
            when (status) {
                is DeviceConnectStatus.Connecting -> ConnectingCard(
                    startedAtMs = status.startedAtMs,
                    timeoutMs = status.timeoutMs,
                    onCancel = onCancel,
                )
                is DeviceConnectStatus.Failed -> FailureCard(cause = status.cause, onRetry = onCancel)
            }
        }
    }
}

@Composable
private fun ConnectingCard(
    startedAtMs: Long,
    timeoutMs: Long,
    onCancel: () -> Unit,
) {
    // Drive the progress bar from inside the composable itself using
    // withFrameMillis — the caller only has to provide the wall-clock
    // start time. In preview mode the produceState coroutine returns
    // immediately so the bar freezes at its initial value and the
    // renderer can settle.
    val inPreview = androidx.compose.ui.platform.LocalInspectionMode.current
    val elapsedMs by androidx.compose.runtime.produceState(
        initialValue = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L),
        startedAtMs,
    ) {
        if (inPreview) return@produceState
        while (true) {
            androidx.compose.runtime.withFrameMillis {
                value = (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
            }
        }
    }
    val progress = (elapsedMs.toFloat() / timeoutMs.toFloat()).coerceIn(0f, 1f)
    val remainingS = ((timeoutMs - elapsedMs).coerceAtLeast(0) / 1000).toInt()
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Contrast,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(Dimens.M))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connecting…",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Waiting for the radio to respond — ${remainingS}s",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            androidx.compose.material3.LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
            )
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Cancel") }
        }
    }
}

@Composable
private fun FailureCard(cause: Throwable, onRetry: () -> Unit) {
    val scroll = rememberScrollState()
    val stack = remember(cause) {
        buildString {
            val messages = generateSequence<Throwable>(cause) { it.cause }.toList()
            messages.forEachIndexed { i, t ->
                if (i > 0) append("Caused by: ")
                append(t::class.simpleName ?: "Throwable")
                append(": ")
                append(t.message ?: "(no message)")
                append('\n')
            }
            append('\n')
            append(cause.stackTraceToString())
        }
    }
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.size(Dimens.S))
                Text(
                    text = "Connection failed",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            Text(
                text = cause.message ?: "Unknown error",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            OutlinedButton(onClick = onRetry) { Text("Back to scanner") }
        }
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(Dimens.XS))
            Text(
                text = stack,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(scroll),
            )
        }
    }
}

// --- Previews -------------------------------------------------------------

private fun previewPubKey(fill: Byte): PublicKey =
    PublicKey.fromBytes(ByteString(*ByteArray(32) { fill }))

private fun previewContact(name: String, pathLen: Int, fill: Byte, type: ContactType = ContactType.CHAT): Contact =
    Contact(
        publicKey = previewPubKey(fill),
        type = type,
        flags = 0,
        pathLength = pathLen,
        path = ByteString(),
        name = name,
        advertTimestamp = Instant.fromEpochSeconds(1_700_000_000),
        latitude = 0.0,
        longitude = 0.0,
        lastModified = Instant.fromEpochSeconds(1_700_000_000),
    )

private fun previewSelf(name: String = "node-peak") = SelfInfo(
    advertType = 1,
    txPowerDbm = 14,
    maxPowerDbm = 22,
    publicKey = previewPubKey(0xAB.toByte()),
    latitude = 53.0,
    longitude = -1.5,
    multiAcks = 0,
    advertLocationPolicy = 0,
    telemetryFlags = 0,
    manualAddContacts = 0,
    radio = RadioSettings(869_525_000, 125_000, 10, 5),
    name = name,
)

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — populated",
)
@Composable
fun DeviceBodyPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3980, 512, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = listOf(
                previewContact("alice", -1, 0x11),
                previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
                previewContact("common-room", 0, 0x33, ContactType.ROOM),
                previewContact("soil-sensor-1", 3, 0x44, ContactType.SENSOR),
            ),
            lastMessage = "hey — are you on tonight? (SNR 6)",
            onRefreshContacts = {},

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — loading",
)
@Composable
fun DeviceBodyLoadingPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = null,
            battery = null,
            radio = null,
            contacts = emptyList(),
            contactsLoading = true,
            lastMessage = null,
            onRefreshContacts = {},

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — contacts loading",
)
@Composable
fun DeviceBodyNoContactsPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3980, 512, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = emptyList(),
            contactsLoading = true,
            lastMessage = null,
            onRefreshContacts = {},

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — low battery",
)
@Composable
fun DeviceBodyLowBatteryPreview() {
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3210, 3800, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = listOf(
                previewContact("alice", -1, 0x11),
                previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
            ),
            lastMessage = null,
            onRefreshContacts = {},

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — many contacts (scrolls)",
)
@Composable
fun DeviceBodyManyContactsPreview() {
    val types = listOf(
        ContactType.CHAT,
        ContactType.REPEATER,
        ContactType.ROOM,
        ContactType.SENSOR,
    )
    val contacts = (0 until 12).map { i ->
        previewContact(
            name = listOf(
                "alice", "bob-repeater", "charlie", "dana",
                "eve-hq", "frank", "common-room", "garden-sensor",
                "hiker-ian", "julia-summit", "kappa-repeater", "lighthouse",
            )[i],
            pathLen = i % 4,
            fill = (0x11 + i).toByte(),
            type = types[i % types.size],
        )
    }
    MeshcoreTheme {
        DeviceBody(
            self = previewSelf("base-station"),
            battery = BatteryInfo(4050, 900, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = contacts,
            lastMessage = "eve-hq: weather check in 5 (SNR 8)",
            onRefreshContacts = {},

            onDisconnect = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — status connecting",
)
@Composable
fun DeviceStatusConnectingPreview() {
    MeshcoreTheme {
        DeviceStatusView(
            title = "Connecting",
            status = DeviceConnectStatus.Connecting(
                startedAtMs = System.currentTimeMillis() - 7_000L,
                timeoutMs = 20_000L,
            ),
            onCancel = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    name = "Device — status failed",
)
@Composable
fun DeviceStatusFailedPreview() {
    MeshcoreTheme {
        val cause = IllegalStateException(
            "BLE GATT timed out after 20s (device XX:XX:XX:XX:5F:78)",
            java.util.concurrent.TimeoutException("no LoginSuccess within 5000ms"),
        )
        DeviceStatusView(
            title = "Connection failed",
            status = DeviceConnectStatus.Failed(cause),
            onCancel = {},
        )
    }
}

@Preview(
    showBackground = true,
    showSystemUi = true,
    device = Devices.PIXEL_7,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
    name = "Device — dark",
)
@Composable
fun DeviceBodyDarkPreview() {
    MeshcoreTheme(darkTheme = true) {
        DeviceBody(
            self = previewSelf(),
            battery = BatteryInfo(3980, 512, 4096),
            radio = RadioSettings(869_525_000, 125_000, 10, 5),
            contacts = listOf(
                previewContact("alice", -1, 0x11),
                previewContact("bob-repeater", 2, 0x22, ContactType.REPEATER),
                previewContact("common-room", 0, 0x33, ContactType.ROOM),
            ),
            lastMessage = "hey — are you on tonight? (SNR 6)",
            onRefreshContacts = {},

            onDisconnect = {},
        )
    }
}
