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
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.WarningAmber
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
import ee.schimke.meshcore.app.MeshcoreApp
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
import ee.schimke.meshcore.components.ui.ContactListEmpty
import ee.schimke.meshcore.components.ui.ContactRow
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
import kotlinx.coroutines.launch

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
sealed class LastMessageInfo {
    abstract val text: String
    abstract val snr: Int

    data class Dm(
        val contactKeyHex: String,
        val contactName: String?,
        override val text: String,
        override val snr: Int,
    ) : LastMessageInfo()

    data class Channel(
        val channelIndex: Int,
        val channelName: String?,
        val sender: String?,
        override val text: String,
        override val snr: Int,
    ) : LastMessageInfo()
}

@Composable
private fun ConnectedDevice(
    client: ee.schimke.meshcore.core.client.MeshCoreClient,
    onDisconnect: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onNavigateToContact: (Contact) -> Unit,
    onNavigateToChannel: (ChannelInfo) -> Unit,
) {
    val app = MeshcoreApp.get()
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
        onDisconnect = onDisconnect,
        onOpenThemePicker = onOpenThemePicker,
        warnings = warnings,
        onDismissWarning = { controller.dismissWarning(it) },
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
    contactsRefreshing: Boolean = false,
    channels: List<ChannelInfo> = emptyList(),
    contactedKeys: Set<String> = emptySet(),
    sectionStates: SectionStates = SectionStates(),
    onSectionExpandedChange: (Section, Boolean) -> Unit = { _, _ -> },
    onSectionShowAllChange: (Section, Boolean) -> Unit = { _, _ -> },
    lastMessage: LastMessageInfo? = null,
    onLastMessageClick: (LastMessageInfo) -> Unit = {},
    onContactClick: (Contact) -> Unit = {},
    onChannelClick: (ChannelInfo) -> Unit = {},
    onDisconnect: () -> Unit,
    onOpenThemePicker: () -> Unit = {},
    warnings: List<String> = emptyList(),
    onDismissWarning: (String) -> Unit = {},
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

            warnings.forEach { warning ->
                WarningBanner(warning, onDismiss = { onDismissWarning(warning) })
            }

            lastMessage?.let { LastMessageBanner(it, onClick = { onLastMessageClick(it) }) }

            // Split contacts by type
            val chatContacts = contacts.filter { it.type == ContactType.CHAT }
            val rooms = contacts.filter { it.type == ContactType.ROOM }
            val repeaters = contacts.filter { it.type == ContactType.REPEATER }
            val sensors = contacts.filter { it.type == ContactType.SENSOR }

            // Subtle progress bar while refreshing with cached data visible
            AnimatedVisibility(
                visible = contactsRefreshing,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
            }

            // --- Channels ---
            CollapsibleSectionHeader(
                text = "Channels (${channels.size})",
                expanded = sectionStates.channelsExpanded,
                onToggle = { onSectionExpandedChange(Section.CHANNELS, !sectionStates.channelsExpanded) },
            )
            AnimatedVisibility(
                visible = sectionStates.channelsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                    if (channels.isEmpty()) {
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
                }
            }

            // --- Contacts (DM-able peers) ---
            val messagedContacts = if (sectionStates.contactsShowAll) chatContacts
                else chatContacts.filter { it.publicKey.toHex() in contactedKeys }
            CollapsibleSectionHeader(
                text = if (contactsLoading) "Contacts" else "Contacts (${messagedContacts.size})",
                expanded = sectionStates.contactsExpanded,
                onToggle = { onSectionExpandedChange(Section.CONTACTS, !sectionStates.contactsExpanded) },
            ) {
                if (!contactsLoading && chatContacts.isNotEmpty()) {
                    FilterChip(
                        selected = !sectionStates.contactsShowAll,
                        onClick = { onSectionShowAllChange(Section.CONTACTS, !sectionStates.contactsShowAll) },
                        label = { Text(if (sectionStates.contactsShowAll) "All" else "Messaged") },
                    )
                }
            }
            AnimatedVisibility(
                visible = sectionStates.contactsExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                    if (contactsLoading) {
                        LoadingPlaceholder("Fetching contacts\u2026")
                    } else if (messagedContacts.isEmpty()) {
                        ContactListEmpty()
                    } else {
                        messagedContacts.forEach { contact ->
                            ContactRow(contact, onClick = { onContactClick(contact) })
                        }
                    }
                }
            }

            // --- Rooms ---
            if (contactsLoading || rooms.isNotEmpty()) {
                val visibleRooms = if (sectionStates.roomsShowAll) rooms
                    else rooms.filter { it.publicKey.toHex() in contactedKeys }
                CollapsibleSectionHeader(
                    text = if (contactsLoading) "Rooms" else "Rooms (${visibleRooms.size})",
                    expanded = sectionStates.roomsExpanded,
                    onToggle = { onSectionExpandedChange(Section.ROOMS, !sectionStates.roomsExpanded) },
                ) {
                    if (!contactsLoading && rooms.isNotEmpty()) {
                        FilterChip(
                            selected = !sectionStates.roomsShowAll,
                            onClick = { onSectionShowAllChange(Section.ROOMS, !sectionStates.roomsShowAll) },
                            label = { Text(if (sectionStates.roomsShowAll) "All" else "Joined") },
                        )
                    }
                }
                AnimatedVisibility(
                    visible = sectionStates.roomsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                        if (!contactsLoading) {
                            if (visibleRooms.isEmpty()) {
                                Text(
                                    text = "No rooms joined yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp),
                                )
                            } else {
                                visibleRooms.forEach { contact ->
                                    ContactRow(contact, onClick = { onContactClick(contact) })
                                }
                            }
                        }
                    }
                }
            }

            // --- Repeaters ---
            if (!contactsLoading && repeaters.isNotEmpty()) {
                val visibleRepeaters = if (sectionStates.repeatersShowAll) repeaters
                    else repeaters.filter { it.publicKey.toHex() in contactedKeys }
                CollapsibleSectionHeader(
                    text = "Repeaters (${visibleRepeaters.size})",
                    expanded = sectionStates.repeatersExpanded,
                    onToggle = { onSectionExpandedChange(Section.REPEATERS, !sectionStates.repeatersExpanded) },
                ) {
                    FilterChip(
                        selected = !sectionStates.repeatersShowAll,
                        onClick = { onSectionShowAllChange(Section.REPEATERS, !sectionStates.repeatersShowAll) },
                        label = { Text(if (sectionStates.repeatersShowAll) "All" else "Joined") },
                    )
                }
                AnimatedVisibility(
                    visible = sectionStates.repeatersExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                        if (visibleRepeaters.isEmpty()) {
                            Text(
                                text = "No repeaters joined yet",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        } else {
                            visibleRepeaters.forEach { contact ->
                                ContactRow(contact)
                            }
                        }
                    }
                }
            }

            // --- Sensors ---
            if (!contactsLoading && sensors.isNotEmpty()) {
                CollapsibleSectionHeader(
                    text = "Sensors (${sensors.size})",
                    expanded = sectionStates.sensorsExpanded,
                    onToggle = { onSectionExpandedChange(Section.SENSORS, !sectionStates.sensorsExpanded) },
                )
                AnimatedVisibility(
                    visible = sectionStates.sensorsExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Dimens.CardGap)) {
                        sensors.forEach { contact ->
                            ContactRow(contact)
                        }
                    }
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
private fun CollapsibleSectionHeader(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = Dimens.XS),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
        Icon(
            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LastMessageBanner(info: LastMessageInfo, onClick: () -> Unit) {
    val origin = when (info) {
        is LastMessageInfo.Dm -> info.contactName ?: info.contactKeyHex.take(12)
        is LastMessageInfo.Channel -> buildString {
            append("#")
            append(info.channelName ?: info.channelIndex.toString())
            info.sender?.let { append(" · $it") }
        }
    }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Message,
                contentDescription = "New message",
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(Dimens.S))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = origin,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = info.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String, onDismiss: () -> Unit) {
    androidx.compose.material3.Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.size(Dimens.S))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Dismiss",
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
