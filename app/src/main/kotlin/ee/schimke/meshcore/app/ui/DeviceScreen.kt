package ee.schimke.meshcore.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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

@OptIn(FlowPreview::class)
@Composable
private fun ConnectedDevice(
    client: ee.schimke.meshcore.core.client.MeshCoreClient,
    onDisconnect: () -> Unit,
    onOpenThemePicker: () -> Unit,
    onNavigateToContact: (Contact) -> Unit,
    onNavigateToChannel: (ChannelInfo) -> Unit,
) {
    val controller = MeshcoreApp.get().connectionController
    val self by client.selfInfo.collectAsState()
    val battery by client.battery.collectAsState()
    val radio by client.radio.collectAsState()
    val contacts by client.contacts.collectAsState()
    val channels by client.channels.collectAsState()
    val warnings by controller.warnings.collectAsState()
    val scope = rememberCoroutineScope()
    var lastMessage by remember { mutableStateOf<LastMessageInfo?>(null) }
    // If the client was seeded with cached data, contacts won't be empty
    // even before the fresh fetch — show a "refreshing" indicator instead
    // of a full loading spinner in that case.
    var contactsRefreshing by remember { mutableStateOf(true) }

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

    // Debounce last-message banner: waits for messages to settle,
    // then shows the most recent one. During rapid sync drains the
    // banner stays still; once the burst ends it updates after 500ms.
    LaunchedEffect(client) {
        client.events
            .mapNotNull { ev ->
                when (ev) {
                    is MeshEvent.DirectMessage -> {
                        val msg = ev.message
                        val prefix = msg.senderPrefix.toHex()
                        val contact = contacts.firstOrNull { it.publicKey.toHex().startsWith(prefix) }
                        LastMessageInfo.Dm(
                            contactKeyHex = contact?.publicKey?.toHex() ?: prefix,
                            contactName = contact?.name,
                            text = msg.text,
                            snr = msg.snr,
                        )
                    }
                    is MeshEvent.ChannelMessage -> {
                        val msg = ev.message
                        val ch = channels.firstOrNull { it.index == msg.channelIndex }
                        LastMessageInfo.Channel(
                            channelIndex = msg.channelIndex,
                            channelName = ch?.name?.ifBlank { null },
                            sender = msg.sender,
                            text = msg.text,
                            snr = msg.snr,
                        )
                    }
                    else -> null
                }
            }
            .debounce(500.milliseconds)
            .collect { lastMessage = it }
    }

    DeviceBody(
        self = self,
        battery = battery,
        radio = radio,
        contacts = contacts,
        contactsLoading = contactsRefreshing && contacts.isEmpty(),
        contactsRefreshing = contactsRefreshing && contacts.isNotEmpty(),
        channels = channels,
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
        onRefreshContacts = {
            scope.launch {
                contactsRefreshing = true
                runCatching { client.getContacts(delta = true) }
                contactsRefreshing = false
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
    lastMessage: LastMessageInfo? = null,
    onLastMessageClick: (LastMessageInfo) -> Unit = {},
    onRefreshContacts: () -> Unit,
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

            AnimatedVisibility(
                visible = lastMessage != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                lastMessage?.let { LastMessageBanner(it, onClick = { onLastMessageClick(it) }) }
            }

            // Split contacts by type
            val chatContacts = contacts.filter { it.type == ContactType.CHAT }
            val rooms = contacts.filter { it.type == ContactType.ROOM }
            val repeaters = contacts.filter { it.type == ContactType.REPEATER }
            val sensors = contacts.filter { it.type == ContactType.SENSOR }

            val isRefreshing = contactsLoading || contactsRefreshing

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

            // --- Contacts (DM-able peers) ---
            SectionHeader(
                text = if (contactsLoading) "Contacts" else "Contacts (${chatContacts.size})",
                action = {
                    if (!isRefreshing) {
                        IconButton(onClick = onRefreshContacts) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
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
            SectionHeader(text = "Channels (${channels.size})")
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
