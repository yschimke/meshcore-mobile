@file:OptIn(
  androidx.compose.material3.ExperimentalMaterial3Api::class,
  androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi::class,
)

package ee.schimke.meshcore.components.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ee.schimke.meshcore.app.ui.LoginDialog
import ee.schimke.meshcore.app.ui.LoginDialogState
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

/**
 * Shared application content. Hosts supply permission-aware transport construction. [developerMode]
 * enables the developer-only tools (the Commands console).
 */
@Composable
fun MeshcoreClientApp(
  manager: MeshCoreManager,
  connect: (suspend (String, Int) -> Unit)? = null,
  developerMode: Boolean = false,
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
  key(client) { ConnectedMeshcoreApp(client, manager::requestDisconnect, developerMode) }
}

@Composable
private fun ConnectedMeshcoreApp(
  client: MeshCoreClient,
  disconnect: () -> Unit,
  developerMode: Boolean,
) {
  val self by client.selfInfo.collectAsState()
  val battery by client.battery.collectAsState()
  val radio by client.radio.collectAsState()
  val contacts by client.contacts.collectAsState()
  val channels by client.channels.collectAsState()
  var sections by remember { mutableStateOf(SectionStates()) }
  val navigator = rememberListDetailPaneScaffoldNavigator<ChatDestination>()
  var error by remember { mutableStateOf<String?>(null) }
  val messageSync = remember(client) { Mutex() }
  val sentByConversation = remember(client) { mutableMapOf<String, MutableList<ChatMessage>>() }
  val authenticated = remember(client) { mutableStateListOf<String>() }
  var loginContact by remember(client) { mutableStateOf<Contact?>(null) }
  var loginState by
    remember(client) { mutableStateOf<LoginDialogState>(LoginDialogState.Prompting()) }
  val scope = rememberCoroutineScope()
  fun openChat(destination: ChatDestination) {
    scope.launch { navigator.openChat(destination) }
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
              openChat(ChatDestination.Contact(contact.publicKey.toHex(), contact.name))
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
      onCommandsClick = {
        val commands = channels.firstOrNull { it.name == COMMANDS_CHANNEL_NAME }
        if (commands == null) error = "The companion radio has no commands channel."
        else openChat(ChatDestination.Channel(commands.index, "Commands"))
      },
      // Device settings (buzzer) are Android-only; there is no desktop/web settings screen.
      onSettingsClick = null,
      onDisconnect = disconnect,
      developerMode = developerMode,
      warnings = listOfNotNull(error),
      onDismissWarning = { error = null },
      onContactClick = { contact ->
        if (
          contact.type in setOf(ContactType.ROOM, ContactType.REPEATER) &&
            contact.publicKey.toHex() !in authenticated
        ) {
          loginContact = contact
          loginState = LoginDialogState.Prompting()
        } else openChat(ChatDestination.Contact(contact.publicKey.toHex(), contact.name))
      },
      onChannelClick = { openChat(ChatDestination.Channel(it.index, it.name)) },
    )
  }
  MeshcoreListDetail(
    navigator = navigator,
    list = device,
    detail = { destination ->
      val closeChat: () -> Unit = { scope.launch { navigator.closeChat() } }
      when (destination) {
        is ChatDestination.Contact ->
          MeshcoreChat(
            client,
            destination.name,
            sendScope = scope,
            contactKey = destination.key,
            sent =
              sentByConversation.getOrPut("contact-${destination.key}") { mutableStateListOf() },
            onBack = closeChat,
          )
        is ChatDestination.Channel ->
          MeshcoreChat(
            client,
            destination.name,
            sendScope = scope,
            channelIndex = destination.index,
            sent =
              sentByConversation.getOrPut("channel-${destination.index}") { mutableStateListOf() },
            onBack = closeChat,
          )
      }
    },
  )
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
  // Cached so a draft keystroke doesn't rebuild and re-sort the whole history: rebuilding also
  // gives every ChatMessage a new identity, so each visible bubble would recompose per keystroke.
  val received =
    remember(channel, direct, channelIndex, contactKey, title) {
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
    }
  val selfName = self?.name
  // derivedStateOf because `sent` is a snapshot list mutated in place: the merge re-runs when it
  // changes, but not when only the draft does.
  val messages by
    remember(received, sent, selfName, channelIndex) {
      derivedStateOf {
        mergeChatHistory(received, sent, selfName = selfName, dedupeEchoes = channelIndex != null)
      }
    }
  ChatBody(
    title = title,
    messages = messages,
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
