package ee.schimke.meshcore.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.unit.dp
import ee.schimke.composeai.discovery.*
import ee.schimke.composeai.uibuilder.renderer.sdk.*
import ee.schimke.meshcore.components.ui.ChannelList
import ee.schimke.meshcore.components.ui.ChatInput
import ee.schimke.meshcore.components.ui.ContactListEmpty
import ee.schimke.meshcore.components.ui.DeviceSummaryCard
import ee.schimke.meshcore.components.ui.RadioLine
import ee.schimke.meshcore.components.ui.theme.MeshcoreTheme
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.RadioSettings

/** Catalog-owned authoring model; real component calls below check the library API. */
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

/**
 * The real core model as an object value: every field is required, so a stored radio states all
 * four, and the codec names the class itself, as an export writes `RadioSettings(frequencyHz = …)`.
 */
val RadioSettingsCodec: AdapterObjectCodec<RadioSettings> =
  AdapterValueCodecs.obj("ee.schimke.meshcore.core.model.RadioSettings") {
    val frequencyHz = field(RadioSettings::frequencyHz, AdapterValueCodecs.Int)
    val bandwidthHz = field(RadioSettings::bandwidthHz, AdapterValueCodecs.Int)
    val spreadingFactor = field(RadioSettings::spreadingFactor, AdapterValueCodecs.Int)
    val codingRate = field(RadioSettings::codingRate, AdapterValueCodecs.Int)
    construct {
      RadioSettings(
        frequencyHz = it[frequencyHz],
        bandwidthHz = it[bandwidthHz],
        spreadingFactor = it[spreadingFactor],
        codingRate = it[codingRate],
      )
    }
  }

/**
 * A channel as the list shows it: its slot [ChannelInfo.index] and name. The pre-shared key is
 * never displayed, so it is not a field and takes the class's own empty default.
 */
val ChannelInfoCodec: AdapterObjectCodec<ChannelInfo> =
  AdapterValueCodecs.obj("ee.schimke.meshcore.core.model.ChannelInfo") {
    val index = field(ChannelInfo::index, AdapterValueCodecs.Int)
    val name = field(ChannelInfo::name, AdapterValueCodecs.String, default = "")
    construct { ChannelInfo(index = it[index], name = it[name]) }
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

data class ChannelListProps(val channels: List<ChannelInfo>)

/** A list of objects: the device screen's channel list, one row per stored channel. */
class ChannelListAdapter(record: ComponentRecord) :
  TypedComponentAdapter<ChannelListProps>("meshcore-mobile/channel-list", record) {
  val channels =
    property(
      ChannelListProps::channels,
      AdapterValueCodecs.list(ChannelInfoCodec),
      listOf(ChannelInfo(index = 0, name = "Public"), ChannelInfo(index = 1, name = "Hiking")),
    )
}

class MeshcoreCatalog(records: ComponentRecordFile) {
  val input = ChatInputAdapter(records.components.single { it.symbol.name == "ChatInput" })
  val emptyContacts =
    TypedComponentAdapter<Unit>(
      "meshcore-mobile/empty-contacts",
      records.components.single { it.symbol.name == "ContactListEmpty" },
    )
  val theme = MeshcoreThemeAdapter(records.components.single { it.symbol.name == "MeshcoreTheme" })
  // Every detail left null: the placeholder card shown before a device reports itself.
  val deviceSummary =
    TypedComponentAdapter<Unit>(
      "meshcore-mobile/device-summary",
      records.components.single { it.symbol.name == "DeviceSummaryCard" },
    )
  val radio = RadioLineAdapter(records.components.single { it.symbol.name == "RadioLine" })
  val channels = ChannelListAdapter(records.components.single { it.symbol.name == "ChannelList" })
  val definitions = listOf(input, emptyContacts, theme, deviceSummary, radio, channels)

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
      DeviceSummaryCard(self = null, radio = null, battery = null, modifier = modifier)
    }
    // RadioLine takes no modifier either; the box carries the node's.
    register(radio) {
      Box(modifier, propagateMinConstraints = true) { RadioLine(radio = value(radio.radio)) }
    }
    // ChannelList keys its lazy rows by slot index, so a design that repeats an index draws the
    // first channel in that slot rather than failing composition. It is a lazy column, so it is
    // bounded here: a document column measures its children with unbounded height.
    register(channels) {
      ChannelList(
        channels = value(channels.channels).distinctBy { it.index },
        modifier = modifier.heightIn(max = CHANNEL_LIST_MAX_HEIGHT),
      )
    }
  }
}

/** The tallest the channel list draws on the canvas: about eight rows, then it scrolls. */
private val CHANNEL_LIST_MAX_HEIGHT = 480.dp
