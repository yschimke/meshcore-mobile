package ee.schimke.meshcore.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.discovery.*
import ee.schimke.composeai.uibuilder.renderer.sdk.*
import ee.schimke.meshcore.app.ui.DeviceConnectStatus
import ee.schimke.meshcore.app.ui.DeviceStatusView
import ee.schimke.meshcore.app.ui.LoginDialog
import ee.schimke.meshcore.app.ui.LoginDialogState
import ee.schimke.meshcore.app.ui.theme.ThemeMode
import ee.schimke.meshcore.app.ui.theme.ThemePalette
import ee.schimke.meshcore.app.ui.theme.ThemePickerDialog
import ee.schimke.meshcore.app.ui.theme.ThemeSettings
import ee.schimke.meshcore.components.ui.BatterySection
import ee.schimke.meshcore.components.ui.ChannelList
import ee.schimke.meshcore.components.ui.ChannelRow
import ee.schimke.meshcore.components.ui.ChatBody
import ee.schimke.meshcore.components.ui.ChatBubble
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.ChatMessageList
import ee.schimke.meshcore.components.ui.ContactList
import ee.schimke.meshcore.components.ui.ContactListEmpty
import ee.schimke.meshcore.components.ui.ContactRow
import ee.schimke.meshcore.components.ui.DeviceBody
import ee.schimke.meshcore.components.ui.DeviceSettingsBody
import ee.schimke.meshcore.components.ui.DeviceSettingsUiState
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
import ee.schimke.meshcore.components.ui.LastMessageInfo
import ee.schimke.meshcore.components.ui.RadioLine
import ee.schimke.meshcore.components.ui.TcpConnectPanel
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.components.ui.theme.Section
import ee.schimke.meshcore.components.ui.theme.SectionStates
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo

// Catalog-owned authoring models, one per adapter: each property, event and state change names a
// field here, and the registry below calls the real component with the decoded values, so the
// compiler checks every one against the library's own signature.

data class ChatInputProps(
  val value: String,
  val enabled: Boolean,
  val placeholder: String,
  val onValueChange: (String) -> Unit,
  val onSend: () -> Unit,
)

class ChatInputAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChatInputProps>("meshcore-mobile/chat-input", record) {
  val value = property(ChatInputProps::value, AdapterValueCodecs.String, "Hello mesh")
  val enabled = property(ChatInputProps::enabled, AdapterValueCodecs.Boolean, true)
  val placeholder = property(ChatInputProps::placeholder, AdapterValueCodecs.String, "Message")
  val change = stateChange(ChatInputProps::onValueChange, value)
  val send = event(ChatInputProps::onSend)
}

data class MeshcoreThemeProps(val darkTheme: Boolean)

/** The app's color scheme and type around whatever the design places in its content slot. */
class MeshcoreThemeAdapter(record: ComponentRecord) :
  TypedComponentAdapter<MeshcoreThemeProps>("meshcore-mobile/theme", record) {
  val darkTheme = property(MeshcoreThemeProps::darkTheme, AdapterValueCodecs.Boolean, false)
  val content = slot("content")
}

data class RadioLineProps(val radio: RadioSettings)

/** One object property: the radio parameters line from the device summary card. */
class RadioLineAdapter(record: ComponentRecord) :
  TypedComponentAdapter<RadioLineProps>("meshcore-mobile/radio-line", record) {
  val radio =
    property(
      RadioLineProps::radio,
      RadioSettingsCodec,
      RadioSettings(
        frequencyHz = 869_525_000,
        bandwidthHz = 250_000,
        spreadingFactor = 11,
        codingRate = 5,
      ),
    )
}

data class ChannelListProps(
  val channels: List<ChannelInfo>,
  val onChannelClick: (ChannelInfo) -> Unit,
)

/** A list of objects: the device screen's channel list, one row per stored channel. */
class ChannelListAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChannelListProps>("meshcore-mobile/channel-list", record) {
  val channels =
    property(
      ChannelListProps::channels,
      AdapterValueCodecs.list(ChannelInfoCodec),
      listOf(ChannelInfo(index = 0, name = "Public"), ChannelInfo(index = 1, name = "Hiking")),
    )
  val channelClick = event(ChannelListProps::onChannelClick)
}

data class ChannelRowProps(val channel: ChannelInfo, val onClick: () -> Unit)

class ChannelRowAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChannelRowProps>("meshcore-mobile/channel-row", record) {
  val channel = property(ChannelRowProps::channel, ChannelInfoCodec, MeshcoreSamples.channels[0])
  val click = event(ChannelRowProps::onClick)
}

data class DeviceSummaryCardProps(
  val self: SelfInfo?,
  val radio: RadioSettings?,
  val battery: BatteryInfo?,
)

/**
 * Each detail is nullable and defaults to null, the not-yet-reported state: a stored null is the
 * property left unset, and the component's own parameters default to null, so a new card is the
 * placeholder shown before a device answers until the design fills in its identity, radio or
 * battery.
 */
class DeviceSummaryCardAdapter(record: ComponentRecord) :
  TypedComponentAdapter<DeviceSummaryCardProps>("meshcore-mobile/device-summary", record) {
  val self =
    property(DeviceSummaryCardProps::self, AdapterValueCodecs.nullable(SelfInfoCodec), null)
  val radio =
    property(DeviceSummaryCardProps::radio, AdapterValueCodecs.nullable(RadioSettingsCodec), null)
  val battery =
    property(DeviceSummaryCardProps::battery, AdapterValueCodecs.nullable(BatteryInfoCodec), null)
}

data class BatterySectionProps(val battery: BatteryInfo)

class BatterySectionAdapter(record: ComponentRecord) :
  TypedComponentAdapter<BatterySectionProps>("meshcore-mobile/battery", record) {
  val battery = property(BatterySectionProps::battery, BatteryInfoCodec, MeshcoreSamples.battery)
}

data class ContactRowProps(val contact: Contact, val onClick: () -> Unit)

class ContactRowAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ContactRowProps>("meshcore-mobile/contact-row", record) {
  val contact = property(ContactRowProps::contact, ContactCodec, MeshcoreSamples.contacts[1])
  val click = event(ContactRowProps::onClick)
}

data class ContactListProps(val contacts: List<Contact>, val onContactClick: (Contact) -> Unit)

class ContactListAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ContactListProps>("meshcore-mobile/contact-list", record) {
  val contacts =
    property(
      ContactListProps::contacts,
      AdapterValueCodecs.list(ContactCodec),
      MeshcoreSamples.contacts,
    )
  val contactClick = event(ContactListProps::onContactClick)
}

data class ChatBubbleProps(val message: ChatMessage, val onRetry: () -> Unit)

class ChatBubbleAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChatBubbleProps>("meshcore-mobile/chat-bubble", record) {
  val message = property(ChatBubbleProps::message, ChatMessageCodec, MeshcoreSamples.channelMessage)
  val retry = event(ChatBubbleProps::onRetry)
}

data class ChatMessageListProps(
  val messages: List<ChatMessage>,
  val onRetry: (ChatMessage) -> Unit,
)

class ChatMessageListAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChatMessageListProps>("meshcore-mobile/chat-message-list", record) {
  val messages =
    property(
      ChatMessageListProps::messages,
      AdapterValueCodecs.list(ChatMessageCodec),
      MeshcoreSamples.contactMessages,
    )
  val retry = event(ChatMessageListProps::onRetry)
}

data class ChatBodyProps(
  val title: String,
  val subtitle: String?,
  val messages: List<ChatMessage>,
  val draft: String,
  val inputEnabled: Boolean,
  val inputPlaceholder: String,
  val onDraftChange: (String) -> Unit,
  val onSend: () -> Unit,
  val onBack: () -> Unit,
  val onRetry: (ChatMessage) -> Unit,
)

/** The whole chat screen; its top-bar `actions` slot keeps the component's empty default. */
class ChatBodyAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChatBodyProps>("meshcore-mobile/chat-body", record) {
  val title = property(ChatBodyProps::title, AdapterValueCodecs.String, "alice")
  val subtitle =
    property(ChatBodyProps::subtitle, AdapterValueCodecs.nullable(AdapterValueCodecs.String), null)
  val messages =
    property(
      ChatBodyProps::messages,
      AdapterValueCodecs.list(ChatMessageCodec),
      MeshcoreSamples.contactMessages,
    )
  val draft = property(ChatBodyProps::draft, AdapterValueCodecs.String, "")
  val inputEnabled = property(ChatBodyProps::inputEnabled, AdapterValueCodecs.Boolean, true)
  val inputPlaceholder =
    property(ChatBodyProps::inputPlaceholder, AdapterValueCodecs.String, "Message")
  val draftChange = stateChange(ChatBodyProps::onDraftChange, draft)
  val send = event(ChatBodyProps::onSend)
  val back = event(ChatBodyProps::onBack)
  val retry = event(ChatBodyProps::onRetry)
}

data class TcpConnectPanelProps(
  val busy: Boolean,
  val defaultHost: String,
  val defaultPort: Int,
  val onConnect: (String, Int) -> Unit,
)

class TcpConnectPanelAdapter(record: ComponentRecord) :
  TypedComponentAdapter<TcpConnectPanelProps>("meshcore-mobile/tcp-connect", record) {
  val busy = property(TcpConnectPanelProps::busy, AdapterValueCodecs.Boolean, false)
  val defaultHost =
    property(TcpConnectPanelProps::defaultHost, AdapterValueCodecs.String, "192.168.1.10")
  val defaultPort = property(TcpConnectPanelProps::defaultPort, AdapterValueCodecs.Int, 5000)
  val connect = event(TcpConnectPanelProps::onConnect)
}

data class DeviceSettingsBodyProps(
  val state: DeviceSettingsUiState,
  val onToggleBuzzer: (Boolean) -> Unit,
  val onBack: () -> Unit,
)

class DeviceSettingsBodyAdapter(record: ComponentRecord) :
  TypedComponentAdapter<DeviceSettingsBodyProps>("meshcore-mobile/device-settings", record) {
  val state =
    property(
      DeviceSettingsBodyProps::state,
      DeviceSettingsUiStateCodec,
      DeviceSettingsUiState.Ready(hasBuzzer = true, buzzerMode = "rtttl"),
    )
  val toggleBuzzer = event(DeviceSettingsBodyProps::onToggleBuzzer)
  val back = event(DeviceSettingsBodyProps::onBack)
}

data class ThemePickerDialogProps(
  val current: ThemeSettings,
  val dynamicColorAvailable: Boolean,
  val onModeSelect: (ThemeMode) -> Unit,
  val onPaletteSelect: (ThemePalette) -> Unit,
  val onDismiss: () -> Unit,
)

class ThemePickerDialogAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ThemePickerDialogProps>("meshcore-mobile/theme-picker", record) {
  val current = property(ThemePickerDialogProps::current, ThemeSettingsCodec, ThemeSettings())
  val dynamicColorAvailable =
    property(ThemePickerDialogProps::dynamicColorAvailable, AdapterValueCodecs.Boolean, true)
  val modeSelect = event(ThemePickerDialogProps::onModeSelect)
  val paletteSelect = event(ThemePickerDialogProps::onPaletteSelect)
  val dismiss = event(ThemePickerDialogProps::onDismiss)
}

data class LoginDialogProps(
  val contactName: String,
  val contactType: String,
  val state: LoginDialogState,
  val onLogin: (String) -> Unit,
  val onDismiss: () -> Unit,
)

class LoginDialogAdapter(record: ComponentRecord) :
  TypedComponentAdapter<LoginDialogProps>("meshcore-mobile/login-dialog", record) {
  val contactName =
    property(LoginDialogProps::contactName, AdapterValueCodecs.String, "common-room")
  val contactType = property(LoginDialogProps::contactType, AdapterValueCodecs.String, "room")
  val state = property(LoginDialogProps::state, LoginDialogStateCodec, LoginDialogState.Prompting())
  val login = event(LoginDialogProps::onLogin)
  val dismiss = event(LoginDialogProps::onDismiss)
}

data class DeviceStatusViewProps(
  val title: String,
  val status: DeviceConnectStatus,
  val onCancel: () -> Unit,
  val onOpenThemePicker: () -> Unit,
)

/**
 * The connecting screen. Only `Connecting` is a stored case (see [DeviceConnectStatusCodec]); the
 * canvas pins the card's clock [CANVAS_CONNECT_ELAPSED_MS] after the stored start, as the app's
 * `DeviceStatusConnectingPreview` does, so a design draws one deterministic moment.
 */
class DeviceStatusViewAdapter(record: ComponentRecord) :
  TypedComponentAdapter<DeviceStatusViewProps>("meshcore-mobile/device-status", record) {
  val title = property(DeviceStatusViewProps::title, AdapterValueCodecs.String, "Connecting")
  val status =
    property(
      DeviceStatusViewProps::status,
      DeviceConnectStatusCodec,
      DeviceConnectStatus.Connecting(startedAtMs = 0L, timeoutMs = 20_000L),
    )
  val cancel = event(DeviceStatusViewProps::onCancel)
  val openThemePicker = event(DeviceStatusViewProps::onOpenThemePicker)
}

data class DeviceBodyProps(
  val self: SelfInfo?,
  val battery: BatteryInfo?,
  val radio: RadioSettings?,
  val contacts: List<Contact>,
  val contactsLoading: Boolean,
  val contactsRefreshing: Boolean,
  val channels: List<ChannelInfo>,
  val contactedKeys: Set<String>,
  val contactedChannelIndices: Set<Int>,
  val sectionStates: SectionStates,
  val lastMessage: LastMessageInfo?,
  val warnings: List<String>,
  val onDisconnect: () -> Unit,
  val onSectionExpandedChange: (Section, Boolean) -> Unit,
  val onSectionShowAllChange: (Section, Boolean) -> Unit,
  val onLastMessageClick: (LastMessageInfo) -> Unit,
  val onContactClick: (Contact) -> Unit,
  val onChannelClick: (ChannelInfo) -> Unit,
  val onCommandsClick: () -> Unit,
  val onSettingsClick: () -> Unit,
  val onOpenThemePicker: () -> Unit,
  val onDismissWarning: (String) -> Unit,
)

/**
 * The Device screen. `self`, `battery` and `radio` are required, nullable parameters (null is the
 * still-loading state), so they insert as the previews' sample device rather than null. The
 * sections list only contacted peers and channels unless "show all" is on, so `contactedKeys` (hex
 * keys) and `contactedChannelIndices` are [SetCodec] lists, seeded as the preview seeds them.
 */
class DeviceBodyAdapter(record: ComponentRecord) :
  TypedComponentAdapter<DeviceBodyProps>("meshcore-mobile/device-body", record) {
  val self =
    property(
      DeviceBodyProps::self,
      AdapterValueCodecs.nullable(SelfInfoCodec),
      MeshcoreSamples.self,
    )
  val battery =
    property(
      DeviceBodyProps::battery,
      AdapterValueCodecs.nullable(BatteryInfoCodec),
      MeshcoreSamples.battery,
    )
  val radio =
    property(
      DeviceBodyProps::radio,
      AdapterValueCodecs.nullable(RadioSettingsCodec),
      MeshcoreSamples.radio,
    )
  val contacts =
    property(
      DeviceBodyProps::contacts,
      AdapterValueCodecs.list(ContactCodec),
      MeshcoreSamples.contacts,
    )
  val contactsLoading =
    property(DeviceBodyProps::contactsLoading, AdapterValueCodecs.Boolean, false)
  val contactsRefreshing =
    property(DeviceBodyProps::contactsRefreshing, AdapterValueCodecs.Boolean, false)
  val channels =
    property(
      DeviceBodyProps::channels,
      AdapterValueCodecs.list(ChannelInfoCodec),
      MeshcoreSamples.channels,
    )
  val contactedKeys =
    property(
      DeviceBodyProps::contactedKeys,
      SetCodec(AdapterValueCodecs.String),
      setOf(
        MeshcoreSamples.contacts[0].publicKey.toHex(),
        MeshcoreSamples.contacts[2].publicKey.toHex(),
      ),
    )
  val contactedChannelIndices =
    property(DeviceBodyProps::contactedChannelIndices, SetCodec(AdapterValueCodecs.Int), setOf(0))
  val sectionStates = property(DeviceBodyProps::sectionStates, SectionStatesCodec, SectionStates())
  val lastMessage =
    property(DeviceBodyProps::lastMessage, AdapterValueCodecs.nullable(LastMessageInfoCodec), null)
  val warnings =
    property(
      DeviceBodyProps::warnings,
      AdapterValueCodecs.list(AdapterValueCodecs.String),
      emptyList(),
    )
  val disconnect = event(DeviceBodyProps::onDisconnect)
  val sectionExpandedChange = event(DeviceBodyProps::onSectionExpandedChange)
  val sectionShowAllChange = event(DeviceBodyProps::onSectionShowAllChange)
  val lastMessageClick = event(DeviceBodyProps::onLastMessageClick)
  val contactClick = event(DeviceBodyProps::onContactClick)
  val channelClick = event(DeviceBodyProps::onChannelClick)
  val commandsClick = event(DeviceBodyProps::onCommandsClick)
  val settingsClick = event(DeviceBodyProps::onSettingsClick)
  val openThemePicker = event(DeviceBodyProps::onOpenThemePicker)
  val dismissWarning = event(DeviceBodyProps::onDismissWarning)
}

class MeshcoreCatalog(records: ComponentRecordFile) {
  private val byName = records.components.associateBy { it.symbol.name }

  private fun record(name: String): ComponentRecord =
    requireNotNull(byName[name]) { "no discovered record for $name" }

  val input = ChatInputAdapter(record("ChatInput"))
  val emptyContacts =
    TypedComponentAdapter<Unit>("meshcore-mobile/empty-contacts", record("ContactListEmpty"))
  val theme = MeshcoreThemeAdapter(record("MeshcoreTheme"))
  val deviceSummary = DeviceSummaryCardAdapter(record("DeviceSummaryCard"))
  val radio = RadioLineAdapter(record("RadioLine"))
  val channels = ChannelListAdapter(record("ChannelList"))
  val channelRow = ChannelRowAdapter(record("ChannelRow"))
  val battery = BatterySectionAdapter(record("BatterySection"))
  val contactRow = ContactRowAdapter(record("ContactRow"))
  val contacts = ContactListAdapter(record("ContactList"))
  val bubble = ChatBubbleAdapter(record("ChatBubble"))
  val messages = ChatMessageListAdapter(record("ChatMessageList"))
  val chat = ChatBodyAdapter(record("ChatBody"))
  val tcp = TcpConnectPanelAdapter(record("TcpConnectPanel"))
  val settings = DeviceSettingsBodyAdapter(record("DeviceSettingsBody"))
  val themePicker = ThemePickerDialogAdapter(record("ThemePickerDialog"))
  val login = LoginDialogAdapter(record("LoginDialog"))
  val status = DeviceStatusViewAdapter(record("DeviceStatusView"))
  val device = DeviceBodyAdapter(record("DeviceBody"))
  val definitions: List<TypedComponentAdapter<*>> =
    listOf(
      input,
      emptyContacts,
      theme,
      deviceSummary,
      radio,
      channels,
      channelRow,
      battery,
      contactRow,
      contacts,
      bubble,
      messages,
      chat,
      tcp,
      settings,
      themePicker,
      login,
      status,
      device,
    )

  fun generate(records: ComponentRecordFile): GeneratedAdapterCatalog =
    TypedAdapterCatalog.generate(
      records,
      UiBuilderCatalogs.CoverSheet("meshcore-mobile", "MeshCore live components"),
      UiBuilderPolicyFile.Builder(UI_BUILDER_POLICY_SCHEMA, "mobile").build(),
      definitions,
    )

  fun registry(): CanvasAdapterRegistry = canvasAdapterRegistry {
    register(input) {
      ChatInput(
        value = value(input.value),
        onValueChange = callback(input.change),
        onSend = callback(input.send),
        enabled = value(input.enabled),
        placeholder = value(input.placeholder),
        modifier = modifier,
      )
    }
    register(emptyContacts) { ContactListEmpty(modifier = modifier) }
    // MeshcoreTheme takes no modifier: a constraint-transparent box carries the node's own.
    register(theme) {
      Box(modifier, propagateMinConstraints = true) {
        MeshcoreTheme(darkTheme = value(theme.darkTheme)) { Slot(theme.content) }
      }
    }
    register(deviceSummary) {
      DeviceSummaryCard(
        self = value(deviceSummary.self),
        radio = value(deviceSummary.radio),
        battery = value(deviceSummary.battery),
        modifier = modifier,
      )
    }
    register(radio) { RadioLine(radio = value(radio.radio), modifier = modifier) }
    // The lists key their lazy rows (a channel by slot index, a contact by key, a message by id),
    // so a design that repeats a key draws its first row rather than failing composition. Each is a
    // lazy column, so it is bounded here: a document column measures children with unbounded
    // height.
    register(channels) {
      ChannelList(
        channels = value(channels.channels).distinctBy { it.index },
        onChannelClick = callback(channels.channelClick),
        modifier = modifier.heightIn(max = LIST_MAX_HEIGHT),
      )
    }
    register(channelRow) {
      ChannelRow(
        channel = value(channelRow.channel),
        onClick = callback(channelRow.click),
        modifier = modifier,
      )
    }
    register(battery) { BatterySection(battery = value(battery.battery), modifier = modifier) }
    register(contactRow) {
      ContactRow(
        contact = value(contactRow.contact),
        onClick = callback(contactRow.click),
        modifier = modifier,
      )
    }
    register(contacts) {
      ContactList(
        contacts = value(contacts.contacts).distinctBy { it.publicKey.hashCode() },
        onContactClick = callback(contacts.contactClick),
        modifier = modifier.heightIn(max = LIST_MAX_HEIGHT),
      )
    }
    register(bubble) {
      ChatBubble(
        message = value(bubble.message),
        onRetry = callback(bubble.retry),
        modifier = modifier,
      )
    }
    register(messages) {
      ChatMessageList(
        messages = value(messages.messages).distinctBy { it.id },
        onRetry = callback(messages.retry),
        modifier = modifier.heightIn(max = LIST_MAX_HEIGHT),
      )
    }
    // The screens are scaffolds that fill their height, so they are bounded like the lists.
    register(chat) {
      ChatBody(
        title = value(chat.title),
        subtitle = value(chat.subtitle),
        messages = value(chat.messages).distinctBy { it.id },
        draft = value(chat.draft),
        onDraftChange = callback(chat.draftChange),
        onSend = callback(chat.send),
        onBack = callback(chat.back),
        inputEnabled = value(chat.inputEnabled),
        inputPlaceholder = value(chat.inputPlaceholder),
        onRetry = callback(chat.retry),
        modifier = modifier.heightIn(max = SCREEN_MAX_HEIGHT),
      )
    }
    register(tcp) {
      TcpConnectPanel(
        busy = value(tcp.busy),
        onConnect = callback(tcp.connect),
        defaultHost = value(tcp.defaultHost),
        defaultPort = value(tcp.defaultPort),
        modifier = modifier,
      )
    }
    register(settings) {
      DeviceSettingsBody(
        state = value(settings.state),
        onToggleBuzzer = callback(settings.toggleBuzzer),
        onBack = callback(settings.back),
        modifier = modifier.heightIn(max = SCREEN_MAX_HEIGHT),
      )
    }
    // The dialogs take no modifier; the box carries the node's while the dialog opens over it.
    register(themePicker) {
      Box(modifier, propagateMinConstraints = true) {
        ThemePickerDialog(
          current = value(themePicker.current),
          onModeSelect = callback(themePicker.modeSelect),
          onPaletteSelect = callback(themePicker.paletteSelect),
          onDismiss = callback(themePicker.dismiss),
          dynamicColorAvailable = value(themePicker.dynamicColorAvailable),
        )
      }
    }
    register(login) {
      Box(modifier, propagateMinConstraints = true) {
        LoginDialog(
          contactName = value(login.contactName),
          contactType = value(login.contactType),
          state = value(login.state),
          onLogin = callback(login.login),
          onDismiss = callback(login.dismiss),
        )
      }
    }
    register(status) {
      val connecting = value(status.status)
      DeviceStatusView(
        title = value(status.title),
        status = connecting,
        onCancel = callback(status.cancel),
        onOpenThemePicker = callback(status.openThemePicker),
        nowMs = { canvasNowMs(connecting) },
        modifier = modifier.heightIn(max = SCREEN_MAX_HEIGHT),
      )
    }
    register(device) {
      DeviceBody(
        self = value(device.self),
        battery = value(device.battery),
        radio = value(device.radio),
        contacts = value(device.contacts).distinctBy { it.publicKey.hashCode() },
        onDisconnect = callback(device.disconnect),
        contactsLoading = value(device.contactsLoading),
        contactsRefreshing = value(device.contactsRefreshing),
        channels = value(device.channels).distinctBy { it.index },
        contactedKeys = value(device.contactedKeys),
        contactedChannelIndices = value(device.contactedChannelIndices),
        sectionStates = value(device.sectionStates),
        onSectionExpandedChange = callback(device.sectionExpandedChange),
        onSectionShowAllChange = callback(device.sectionShowAllChange),
        lastMessage = value(device.lastMessage),
        onLastMessageClick = callback(device.lastMessageClick),
        onContactClick = callback(device.contactClick),
        onChannelClick = callback(device.channelClick),
        onCommandsClick = callback(device.commandsClick),
        onSettingsClick = callback(device.settingsClick),
        onOpenThemePicker = callback(device.openThemePicker),
        warnings = value(device.warnings),
        onDismissWarning = callback(device.dismissWarning),
        modifier = modifier.heightIn(max = SCREEN_MAX_HEIGHT),
      )
    }
  }
}

/** The tallest a lazy list draws on the canvas: about eight rows, then it scrolls. */
private val LIST_MAX_HEIGHT = 480.dp

/** The tallest a full screen draws on the canvas: a phone's height. */
private val SCREEN_MAX_HEIGHT = 800.dp

/** How far into a stored connection attempt the canvas draws it, as the app's preview does. */
const val CANVAS_CONNECT_ELAPSED_MS = 7_000L

/** The pinned clock for [status]: [CANVAS_CONNECT_ELAPSED_MS] after a connection's start. */
fun canvasNowMs(status: DeviceConnectStatus): Long =
  when (status) {
    is DeviceConnectStatus.Connecting -> status.startedAtMs + CANVAS_CONNECT_ELAPSED_MS
    is DeviceConnectStatus.Failed -> 0L
  }
