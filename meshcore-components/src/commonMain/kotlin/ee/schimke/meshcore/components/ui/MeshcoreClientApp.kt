@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import ee.schimke.meshcore.app.ui.LoginDialog
import ee.schimke.meshcore.app.ui.LoginDialogState
import ee.schimke.meshcore.components.ui.icons.MeshIcons
import ee.schimke.meshcore.components.ui.theme.Section
import ee.schimke.meshcore.components.ui.theme.SectionStates
import ee.schimke.meshcore.core.client.MeshCoreClient
import ee.schimke.meshcore.core.manager.ManagerState
import ee.schimke.meshcore.core.manager.MeshCoreManager
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.MeshEvent
import ee.schimke.meshcore.core.model.PublicKey
import kotlin.time.Clock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private data object DeviceDestination : NavKey

private data class ContactDestination(val key: String, val name: String) : NavKey

private data class ChannelDestination(val index: Int, val name: String) : NavKey

/** Shared application content. Hosts supply permission-aware transport construction. */
@Composable
fun MeshcoreClientApp(
  manager: MeshCoreManager,
  connect: (suspend (String, Int) -> Unit)? = null,
  onOpenThemePicker: () -> Unit = {},
  connectionContent: (@Composable (Boolean) -> Unit)? = null,
) {
  require(connect != null || connectionContent != null) { "A connection panel is required" }
  val state by manager.state.collectAsState()
  val scope = rememberCoroutineScope()
  var error by remember { mutableStateOf<String?>(null) }
  val client = (state as? ManagerState.Connected)?.client
  if (client == null) {
    Scaffold(topBar = { TopAppBar(title = { Text("MeshCore") }) }) { padding ->
      Column(
        Modifier.fillMaxSize().padding(padding).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text("Connect to your companion radio", style = MaterialTheme.typography.headlineSmall)
        if (connectionContent != null) connectionContent(state is ManagerState.Connecting)
        else
          TcpConnectPanel(
            busy = state is ManagerState.Connecting,
            onConnect = { host, port ->
              scope.launch {
                error = null
                try {
                  requireNotNull(connect)(host, port)
                } catch (e: CancellationException) {
                  throw e
                } catch (e: Exception) {
                  error = e.message ?: "Connection failed"
                }
              }
            },
            modifier = Modifier.widthIn(max = 560.dp),
          )
        (error ?: (state as? ManagerState.Failed)?.cause?.message)?.let {
          Text(it, color = MaterialTheme.colorScheme.error)
        }
      }
    }
    return
  }
  key(client) { ConnectedMeshcoreApp(client, manager::requestDisconnect, onOpenThemePicker) }
}

@Composable
private fun ConnectedMeshcoreApp(
  client: MeshCoreClient,
  disconnect: () -> Unit,
  onOpenThemePicker: () -> Unit,
) {
  val self by client.selfInfo.collectAsState()
  val battery by client.battery.collectAsState()
  val radio by client.radio.collectAsState()
  val contacts by client.contacts.collectAsState()
  val channels by client.channels.collectAsState()
  var sections by remember {
    mutableStateOf(
      SectionStates(
        channelsShowAll = true,
        contactsShowAll = true,
        roomsShowAll = true,
        repeatersShowAll = true,
      )
    )
  }
  val backStack = remember { mutableStateListOf<NavKey>(DeviceDestination) }
  var error by remember { mutableStateOf<String?>(null) }
  var notice by remember { mutableStateOf<String?>(null) }
  fun openChat(route: NavKey) {
    // Replacing the active conversation keeps wide master/detail navigation bounded.
    while (backStack.size > 1) backStack.removeLastOrNull()
    backStack.add(route)
  }
  val messageSync = remember(client) { Mutex() }
  val sentByConversation = remember(client) { mutableMapOf<String, MutableList<ChatMessage>>() }
  val authenticated = remember(client) { mutableStateListOf<String>() }
  var loginContact by remember(client) { mutableStateOf<Contact?>(null) }
  var loginState by
    remember(client) { mutableStateOf<LoginDialogState>(LoginDialogState.Prompting()) }
  val scope = rememberCoroutineScope()
  notice?.let { message ->
    AlertDialog(
      onDismissRequest = { notice = null },
      title = { Text("Device settings") },
      text = { Text(message) },
      confirmButton = { TextButton(onClick = { notice = null }) { Text("OK") } },
    )
  }
  loginContact?.let { contact ->
    key(contact.publicKey) {
      LoginDialog(
        contactName = contact.name,
        contactType = if (contact.type == ContactType.ROOM) "room" else "repeater",
        state = loginState,
        onLogin = { password ->
          loginState = LoginDialogState.Authenticating
          scope.launch {
            try {
              client.login(contact.publicKey, password)
              authenticated.add(contact.publicKey.toHex())
              loginContact = null
              openChat(ContactDestination(contact.publicKey.toHex(), contact.name))
              messageSync.withLock { client.syncMessages() }
            } catch (e: CancellationException) {
              throw e
            } catch (e: Exception) {
              if (loginContact != null)
                loginState =
                  LoginDialogState.Prompting("Login failed. Check the password and try again.")
              else error = "Could not refresh messages."
            }
          }
        },
        onDismiss = { loginContact = null },
      )
    }
  }
  LaunchedEffect(client) {
    try {
      client.getContacts()
      client.getChannels()
      messageSync.withLock { client.syncMessages() }
      client.getBatteryAndStorage()
      client.getRadioSettings()
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      error = e.message ?: "Could not refresh radio"
    }
  }
  LaunchedEffect(client) {
    // Buffer after filtering so draining a large queue never blocks the event reader.
    client.events
      .filter { it == MeshEvent.MessagesWaiting }
      .buffer(Channel.CONFLATED)
      .collect {
        try {
          messageSync.withLock { client.syncMessages() }
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          error = e.message ?: "Could not receive messages"
        }
      }
  }
  val device: @Composable () -> Unit = {
    DeviceBody(
      self = self,
      battery = battery,
      radio = radio,
      contacts = contacts,
      channels = channels,
      sectionStates = sections,
      onSectionExpandedChange = { section, expanded ->
        sections =
          when (section) {
            Section.CHANNELS -> sections.copy(channelsExpanded = expanded)
            Section.CONTACTS -> sections.copy(contactsExpanded = expanded)
            Section.ROOMS -> sections.copy(roomsExpanded = expanded)
            Section.REPEATERS -> sections.copy(repeatersExpanded = expanded)
            Section.SENSORS -> sections.copy(sensorsExpanded = expanded)
          }
      },
      onSectionShowAllChange = { section, all ->
        sections =
          when (section) {
            Section.CHANNELS -> sections.copy(channelsShowAll = all)
            Section.CONTACTS -> sections.copy(contactsShowAll = all)
            Section.ROOMS -> sections.copy(roomsShowAll = all)
            Section.REPEATERS -> sections.copy(repeatersShowAll = all)
            Section.SENSORS -> sections
          }
      },
      onCommandsClick = {
        val commands = channels.firstOrNull { it.name == COMMANDS_CHANNEL_NAME }
        if (commands == null) error = "The companion radio has no commands channel."
        else openChat(ChannelDestination(commands.index, "Commands"))
      },
      onSettingsClick = {
        notice =
          "Device settings are currently available in the Android app. Use Commands for radio configuration."
      },
      onDisconnect = disconnect,
      warnings = listOfNotNull(error),
      onDismissWarning = { error = null },
      onOpenThemePicker = onOpenThemePicker,
      onContactClick = { contact ->
        if (
          contact.type in setOf(ContactType.ROOM, ContactType.REPEATER) &&
            contact.publicKey.toHex() !in authenticated
        ) {
          loginContact = contact
          loginState = LoginDialogState.Prompting()
        } else openChat(ContactDestination(contact.publicKey.toHex(), contact.name))
      },
      onChannelClick = { openChat(ChannelDestination(it.index, it.name)) },
    )
  }
  NavigationSuiteScaffold(
    navigationSuiteItems = {
      item(
        selected = true,
        onClick = {
          backStack.clear()
          backStack.add(DeviceDestination)
        },
        icon = { Icon(MeshIcons.Settings, contentDescription = null) },
        label = { Text("Radio") },
      )
    }
  ) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
      val showMaster = maxWidth >= 840.dp && backStack.lastOrNull() != DeviceDestination
      Row(Modifier.fillMaxSize()) {
        if (showMaster) {
          Box(Modifier.width(360.dp).fillMaxHeight()) { device() }
          VerticalDivider()
        }
        NavDisplay(
          modifier = Modifier.weight(1f),
          backStack = backStack,
          onBack = { if (backStack.size > 1) backStack.removeLastOrNull() },
          entryProvider =
            entryProvider {
              entry<DeviceDestination> { device() }
              entry<ContactDestination> { route ->
                MeshcoreChat(
                  client,
                  route.name,
                  sendScope = scope,
                  contactKey = route.key,
                  sent =
                    sentByConversation.getOrPut("contact-${route.key}") { mutableStateListOf() },
                  onBack = { backStack.removeLastOrNull() },
                )
              }
              entry<ChannelDestination> { route ->
                MeshcoreChat(
                  client,
                  route.name,
                  sendScope = scope,
                  channelIndex = route.index,
                  sent =
                    sentByConversation.getOrPut("channel-${route.index}") { mutableStateListOf() },
                  onBack = { backStack.removeLastOrNull() },
                )
              }
            },
        )
      }
    }
  }
}

@Composable
private fun MeshcoreChat(
  client: MeshCoreClient,
  title: String,
  sent: MutableList<ChatMessage>,
  sendScope: CoroutineScope,
  contactKey: String? = null,
  channelIndex: Int? = null,
  onBack: () -> Unit,
) {
  val direct by client.directMessages.collectAsState()
  val channel by client.channelMessages.collectAsState()
  val self by client.selfInfo.collectAsState()
  var draft by rememberSaveable(contactKey, channelIndex) { mutableStateOf("") }
  val sending = sent.any { it.status == MessageStatus.Sending }
  val received =
    if (channelIndex != null) {
      channel[channelIndex].orEmpty().mapIndexed { index, m ->
        ChatMessage("channel-$index-${m.timestamp}", m.sender, m.text, m.timestamp, m.snr, false)
      }
    } else {
      direct.entries
        .filter { (key, _) ->
          contactKey?.startsWith(key) == true || key.startsWith(contactKey.orEmpty())
        }
        .flatMap { it.value }
        .mapIndexed { index, m ->
          ChatMessage("direct-$index-${m.timestamp}", title, m.text, m.timestamp, m.snr, false)
        }
    }
  ChatBody(
    title = title,
    messages =
      mergeChatHistory(
        received,
        sent,
        selfName = self?.name,
        dedupeEchoes = channelIndex != null,
      ),
    draft = draft,
    onDraftChange = { draft = it },
    onBack = onBack,
    inputEnabled = !sending,
    onSend = {
      if (draft.isNotBlank() && !sending) {
        val text = draft.trim()
        val timestamp = Clock.System.now()
        val message =
          ChatMessage(
            "sent-$timestamp",
            null,
            text,
            timestamp,
            isMine = true,
            status = MessageStatus.Sending,
          )
        sent.add(message)
        draft = ""
        sendScope.launch {
          try {
            if (channelIndex != null) client.sendChannelText(channelIndex, text, timestamp)
            else client.sendText(PublicKey.fromHex(requireNotNull(contactKey)), text, timestamp)
            val index = sent.indexOfFirst { it.id == message.id }
            if (index >= 0) sent[index] = message.copy(status = MessageStatus.Sent)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Exception) {
            val index = sent.indexOfFirst { it.id == message.id }
            if (index >= 0) sent[index] = message.copy(status = MessageStatus.Failed)
          }
        }
      }
    },
  )
}
