package ee.schimke.meshcore.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.di.LocalAppGraph
import ee.schimke.meshcore.app.connection.ConnectionUiState
import ee.schimke.meshcore.app.ui.theme.Dimens
import androidx.compose.material3.CircularProgressIndicator
import ee.schimke.meshcore.components.ui.verticalScrollbar
import ee.schimke.meshcore.app.ui.theme.Section
import ee.schimke.meshcore.app.ui.theme.SectionStates
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.components.ui.ChannelRow
import ee.schimke.meshcore.components.ui.COMMANDS_CHANNEL_NAME
import ee.schimke.meshcore.components.ui.DeviceBody
import ee.schimke.meshcore.components.ui.LastMessageInfo
import ee.schimke.meshcore.components.ui.ContactListEmpty
import ee.schimke.meshcore.components.ui.ContactRow
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
import kotlinx.coroutines.launch

/** Channel name used for the device commands interface. */

/**
 * Returns the existing commands channel, or creates one on the first
 * empty slot. Returns the [ChannelInfo] on success, null if no empty
 * slot is available or the device rejects the command.
 */
private suspend fun ensureCommandsChannel(
    client: ee.schimke.meshcore.core.client.MeshCoreClient,
    currentChannels: List<ChannelInfo>,
): ChannelInfo? {
    // Already exists?
    currentChannels.firstOrNull { it.name == COMMANDS_CHANNEL_NAME }
        ?.let { return it }

    // Find the first unused slot (channels are indexed 0..maxChannels-1;
    // non-empty ones are in currentChannels).
    val usedIndices = currentChannels.map { it.index }.toSet()
    val maxCh = 8 // safe default; device will reject if out of range
    val freeSlot = (0 until maxCh).firstOrNull { it !in usedIndices } ?: return null

    return runCatching {
        client.setChannel(
            index = freeSlot,
            name = COMMANDS_CHANNEL_NAME,
            psk = kotlinx.io.bytestring.ByteString(), // empty PSK (private channel)
        )
        // After setChannel refreshes the client cache, find it
        client.channels.value.firstOrNull { it.name == COMMANDS_CHANNEL_NAME }
    }.getOrNull()
}

@Composable
fun DeviceScreen(
    onDisconnected: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
    onNavigateToContact: (Contact) -> Unit = {},
    onNavigateToChannel: (ChannelInfo) -> Unit = {},
    onNavigateToCommands: (ChannelInfo) -> Unit = {},
    onNavigateToSettings: (ChannelInfo) -> Unit = {},
) {
    val controller = LocalAppGraph.current.connectionController
    val uiState by controller.state.collectAsState()

    // Track whether we've seen an active connection attempt so we
    // don't navigate to the scanner on the brief initial Idle before
    // auto-reconnect kicks in.
    var hasEngaged by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is ConnectionUiState.Connecting,
            is ConnectionUiState.Retrying,
            is ConnectionUiState.Connected -> hasEngaged = true
            is ConnectionUiState.Idle -> if (hasEngaged) onDisconnected()
            else -> {}
        }
    }

    when (val s = uiState) {
        is ConnectionUiState.Connected -> ConnectedDevice(
            client = s.client,
            onDisconnect = { controller.cancel() },
            onOpenThemePicker = onOpenThemePicker,
            onNavigateToContact = onNavigateToContact,
            onNavigateToChannel = onNavigateToChannel,
            onNavigateToCommands = onNavigateToCommands,
            onNavigateToSettings = onNavigateToSettings,
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
        is ConnectionUiState.Retrying -> DeviceStatusView(
            title = "Retrying (${s.attempt}/${s.maxAttempts})",
            onCancel = { controller.cancel() },
            onOpenThemePicker = onOpenThemePicker,
            status = DeviceConnectStatus.Connecting(
                startedAtMs = System.currentTimeMillis(),
                timeoutMs = s.nextRetryAtMs - System.currentTimeMillis(),
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

/** Info about the most recent message, for the banner on the device screen. */
@Composable
private fun ConnectedDevice(
    client: ee.schimke.meshcore.core.client.MeshCoreClient,
    onDisconnect: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onNavigateToContact: (Contact) -> Unit,
    onNavigateToChannel: (ChannelInfo) -> Unit,
    onNavigateToCommands: (ChannelInfo) -> Unit,
    onNavigateToSettings: (ChannelInfo) -> Unit,
) {
    val app = LocalAppGraph.current
    val controller = app.connectionController
    val repository = app.repository
    val prefs = app.themePreferences
    val self by client.selfInfo.collectAsState()
    val battery by client.battery.collectAsState()
    val radio by client.radio.collectAsState()
    val contacts by client.contacts.collectAsState()
    val channels by client.channels.collectAsState()
    val warnings by controller.warnings.collectAsState()
    val scope = rememberCoroutineScope()
    val deviceId by controller.connectedDeviceId.collectAsState()
    val sectionStates by remember(deviceId) {
        deviceId?.let { prefs.sectionStates(it) }
            ?: kotlinx.coroutines.flow.flowOf(SectionStates())
    }.collectAsState(initial = SectionStates())
    // Track which contacts we've exchanged messages with
    val contactedKeys by remember(deviceId) {
        deviceId?.let { repository.observeContactedKeys(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())
    // Track which channels have messages
    val contactedChannelIndices by remember(deviceId) {
        deviceId?.let { repository.observeContactedChannelIndices(it) }
            ?: kotlinx.coroutines.flow.flowOf(emptyList())
    }.collectAsState(initial = emptyList())

    // If the client was seeded with cached data, contacts won't be empty
    // even before the fresh fetch — show a "refreshing" indicator instead
    // of a full loading spinner in that case.
    var contactsRefreshing by remember { mutableStateOf(true) }

    // Observe the latest message from the DB. This is populated immediately
    // (surviving navigation) and updates whenever the persister inserts a
    // new message, so no debounce or event-mapping needed.
    val latestEntity by remember(deviceId) {
        deviceId?.let { repository.observeLatestMessage(it) }
            ?: kotlinx.coroutines.flow.flowOf(null)
    }.collectAsState(initial = null)

    val lastMessage = latestEntity?.let { entity ->
        when (entity.kind) {
            ee.schimke.meshcore.data.entity.MessageKind.DM -> {
                val keyHex = entity.contactPublicKeyHex ?: ""
                val contact = contacts.firstOrNull { it.publicKey.toHex() == keyHex }
                LastMessageInfo.Dm(
                    contactKeyHex = keyHex,
                    contactName = contact?.name,
                    text = entity.text,
                    snr = entity.snr ?: 0,
                )
            }
            ee.schimke.meshcore.data.entity.MessageKind.CHANNEL -> {
                val ch = channels.firstOrNull { it.index == entity.channelIndex }
                LastMessageInfo.Channel(
                    channelIndex = entity.channelIndex ?: 0,
                    channelName = ch?.name?.ifBlank { null },
                    sender = entity.senderName,
                    text = entity.text,
                    snr = entity.snr ?: 0,
                )
            }
        }
    }

    LaunchedEffect(client) {
        contactsRefreshing = true
        runCatching { client.getContacts() }
        contactsRefreshing = false
        runCatching { client.syncMessages() }
    }

    // Handle MessagesWaiting immediately to trigger sync.
    LaunchedEffect(client) {
        client.events.collect { ev ->
            if (ev is MeshEvent.MessagesWaiting) {
                scope.launch { runCatching { client.syncMessages() } }
            }
        }
    }

    DeviceBody(
        self = self,
        battery = battery,
        radio = radio,
        contacts = contacts,
        contactsLoading = contactsRefreshing && contacts.isEmpty(),
        contactsRefreshing = contactsRefreshing && contacts.isNotEmpty(),
        channels = channels,
        contactedKeys = contactedKeys.toSet(),
        contactedChannelIndices = contactedChannelIndices.toSet(),
        sectionStates = sectionStates,
        onSectionExpandedChange = { section, expanded ->
            val id = deviceId ?: return@DeviceBody
            scope.launch { prefs.setSectionExpanded(id, section, expanded) }
        },
        onSectionShowAllChange = { section, showAll ->
            val id = deviceId ?: return@DeviceBody
            scope.launch { prefs.setSectionShowAll(id, section, showAll) }
        },
        lastMessage = lastMessage,
        onLastMessageClick = { info ->
            when (info) {
                is LastMessageInfo.Dm -> {
                    val contact = contacts.firstOrNull { it.publicKey.toHex() == info.contactKeyHex }
                    if (contact != null) onNavigateToContact(contact)
                }
                is LastMessageInfo.Channel -> {
                    val ch = channels.firstOrNull { it.index == info.channelIndex }
                    if (ch != null) onNavigateToChannel(ch)
                }
            }
        },
        onContactClick = onNavigateToContact,
        onChannelClick = onNavigateToChannel,
        onCommandsClick = {
            scope.launch {
                val ch = ensureCommandsChannel(client, channels)
                if (ch != null) onNavigateToCommands(ch)
            }
        },
        onSettingsClick = {
            scope.launch {
                val ch = ensureCommandsChannel(client, channels)
                if (ch != null) onNavigateToSettings(ch)
            }
        },
        onDisconnect = onDisconnect,
        onOpenThemePicker = onOpenThemePicker,
        warnings = warnings,
        onDismissWarning = { controller.dismissWarning(it) },
    )
}
