package ee.schimke.meshcore.catalog

import ee.schimke.composeai.discovery.*
import ee.schimke.meshcore.app.ui.DeviceConnectStatus
import ee.schimke.meshcore.app.ui.LoginDialogState
import ee.schimke.meshcore.app.ui.theme.ThemeMode
import ee.schimke.meshcore.app.ui.theme.ThemePalette
import ee.schimke.meshcore.app.ui.theme.ThemeSettings
import ee.schimke.meshcore.components.ui.ChatMessage
import ee.schimke.meshcore.components.ui.DeviceSettingsUiState
import ee.schimke.meshcore.components.ui.LastMessageInfo
import ee.schimke.meshcore.components.ui.MessageStatus
import ee.schimke.meshcore.components.ui.theme.SectionStates
import ee.schimke.meshcore.core.model.BatteryInfo
import ee.schimke.meshcore.core.model.ChannelInfo
import ee.schimke.meshcore.core.model.Contact
import ee.schimke.meshcore.core.model.ContactType
import ee.schimke.meshcore.core.model.PublicKey
import ee.schimke.meshcore.core.model.RadioSettings
import ee.schimke.meshcore.core.model.SelfInfo
import ee.schimke.meshcore.core.model.byteStringOfHex
import ee.schimke.meshcore.core.model.toHex
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

private const val MODEL = "ee.schimke.meshcore.core.model"
private const val UI = "ee.schimke.meshcore.components.ui"
private const val APP_UI = "ee.schimke.meshcore.app.ui"

/** A key as the app prints it: 64 hex digits (or 12 for a 6-byte prefix). */
val PublicKeyCodec: AdapterValueCodec<PublicKey> =
  AdapterValueCodecs.parsed(
    "$MODEL.PublicKey",
    "$MODEL.PublicKey.fromHex",
    "hex",
    { PublicKey.fromHex(it) },
    { it.toHex() },
  )

/** Raw bytes (a contact's route) as hex, written back through `byteStringOfHex("…")`. */
val ByteStringHexCodec: AdapterValueCodec<ByteString> =
  AdapterValueCodecs.parsed(
    "kotlinx.io.bytestring.ByteString",
    "$MODEL.byteStringOfHex",
    "hex",
    ::byteStringOfHex,
    { it.toHex() },
  )

val ContactTypeCodec = AdapterValueCodecs.enumValue("$MODEL.ContactType", ContactType.entries)
val MessageStatusCodec = AdapterValueCodecs.enumValue("$UI.MessageStatus", MessageStatus.entries)
val ThemeModeCodec = AdapterValueCodecs.enumValue("$APP_UI.theme.ThemeMode", ThemeMode.entries)
val ThemePaletteCodec =
  AdapterValueCodecs.enumValue("$APP_UI.theme.ThemePalette", ThemePalette.entries)

/**
 * The real core model as an object value: every field is required, so a stored radio states all
 * four, and the codec names the class itself, as an export writes `RadioSettings(frequencyHz = …)`.
 */
val RadioSettingsCodec: AdapterObjectCodec<RadioSettings> =
  AdapterValueCodecs.obj("$MODEL.RadioSettings") {
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
  AdapterValueCodecs.obj("$MODEL.ChannelInfo") {
    val index = field(ChannelInfo::index, AdapterValueCodecs.Int)
    val name = field(ChannelInfo::name, AdapterValueCodecs.String, default = "")
    construct { ChannelInfo(index = it[index], name = it[name]) }
  }

/**
 * The device's own identity. Its name, key and radio are what the app draws, so they are required;
 * the advert, power, location and policy fields default to zero (a chat node, type 1).
 */
val SelfInfoCodec: AdapterObjectCodec<SelfInfo> =
  AdapterValueCodecs.obj("$MODEL.SelfInfo") {
    val name = field(SelfInfo::name, AdapterValueCodecs.String)
    val publicKey = field(SelfInfo::publicKey, PublicKeyCodec)
    val radio = field(SelfInfo::radio, RadioSettingsCodec)
    val advertType = field(SelfInfo::advertType, AdapterValueCodecs.Int, default = 1)
    val txPowerDbm = field(SelfInfo::txPowerDbm, AdapterValueCodecs.Int, default = 0)
    val maxPowerDbm = field(SelfInfo::maxPowerDbm, AdapterValueCodecs.Int, default = 0)
    val latitude = field(SelfInfo::latitude, AdapterValueCodecs.Double, default = 0.0)
    val longitude = field(SelfInfo::longitude, AdapterValueCodecs.Double, default = 0.0)
    val multiAcks = field(SelfInfo::multiAcks, AdapterValueCodecs.Int, default = 0)
    val advertLocationPolicy =
      field(SelfInfo::advertLocationPolicy, AdapterValueCodecs.Int, default = 0)
    val telemetryFlags = field(SelfInfo::telemetryFlags, AdapterValueCodecs.Int, default = 0)
    val manualAddContacts = field(SelfInfo::manualAddContacts, AdapterValueCodecs.Int, default = 0)
    construct {
      SelfInfo(
        advertType = it[advertType],
        txPowerDbm = it[txPowerDbm],
        maxPowerDbm = it[maxPowerDbm],
        publicKey = it[publicKey],
        latitude = it[latitude],
        longitude = it[longitude],
        multiAcks = it[multiAcks],
        advertLocationPolicy = it[advertLocationPolicy],
        telemetryFlags = it[telemetryFlags],
        manualAddContacts = it[manualAddContacts],
        radio = it[radio],
        name = it[name],
      )
    }
  }

/** Battery voltage and storage use; the storage sizes are `Long` kilobytes. */
val BatteryInfoCodec: AdapterObjectCodec<BatteryInfo> =
  AdapterValueCodecs.obj("$MODEL.BatteryInfo") {
    val millivolts = field(BatteryInfo::millivolts, AdapterValueCodecs.Int)
    val storageUsedKb = field(BatteryInfo::storageUsedKb, AdapterValueCodecs.Long, default = 0L)
    val storageTotalKb = field(BatteryInfo::storageTotalKb, AdapterValueCodecs.Long, default = 0L)
    construct {
      BatteryInfo(
        millivolts = it[millivolts],
        storageUsedKb = it[storageUsedKb],
        storageTotalKb = it[storageTotalKb],
      )
    }
  }

/** When a sample contact last advertised, if a design does not say: 2023-11-14T22:13:20Z. */
private val EPOCH_SAMPLE = Instant.fromEpochSeconds(1_700_000_000)

/**
 * A contact as the app's rows show it: name, kind and hop count. Its key is required (lists key
 * their rows by it); the route, flags, location and timestamps default to a flood-free direct
 * contact with no route bytes.
 */
val ContactCodec: AdapterObjectCodec<Contact> =
  AdapterValueCodecs.obj("$MODEL.Contact") {
    val name = field(Contact::name, AdapterValueCodecs.String)
    val publicKey = field(Contact::publicKey, PublicKeyCodec)
    val type = field(Contact::type, ContactTypeCodec, default = ContactType.CHAT)
    val pathLength = field(Contact::pathLength, AdapterValueCodecs.Int, default = 0)
    val path = field(Contact::path, ByteStringHexCodec, default = ByteString())
    val flags = field(Contact::flags, AdapterValueCodecs.Int, default = 0)
    val advertTimestamp = field(Contact::advertTimestamp, AdapterValueCodecs.Instant, EPOCH_SAMPLE)
    val lastModified = field(Contact::lastModified, AdapterValueCodecs.Instant, EPOCH_SAMPLE)
    val latitude = field(Contact::latitude, AdapterValueCodecs.Double, default = 0.0)
    val longitude = field(Contact::longitude, AdapterValueCodecs.Double, default = 0.0)
    construct {
      Contact(
        publicKey = it[publicKey],
        type = it[type],
        flags = it[flags],
        pathLength = it[pathLength],
        path = it[path],
        name = it[name],
        advertTimestamp = it[advertTimestamp],
        latitude = it[latitude],
        longitude = it[longitude],
        lastModified = it[lastModified],
      )
    }
  }

/**
 * One chat message: a sender name and SNR only on received ones, a delivery status on sent ones.
 */
val ChatMessageCodec: AdapterObjectCodec<ChatMessage> =
  AdapterValueCodecs.obj("$UI.ChatMessage") {
    val id = field(ChatMessage::id, AdapterValueCodecs.String)
    val text = field(ChatMessage::text, AdapterValueCodecs.String)
    val timestamp = field(ChatMessage::timestamp, AdapterValueCodecs.Instant)
    val isMine = field(ChatMessage::isMine, AdapterValueCodecs.Boolean, default = false)
    val senderName =
      field(ChatMessage::senderName, AdapterValueCodecs.nullable(AdapterValueCodecs.String), null)
    val snr = field(ChatMessage::snr, AdapterValueCodecs.nullable(AdapterValueCodecs.Int), null)
    val status = field(ChatMessage::status, MessageStatusCodec, default = MessageStatus.Sent)
    construct {
      ChatMessage(
        id = it[id],
        senderName = it[senderName],
        text = it[text],
        timestamp = it[timestamp],
        snr = it[snr],
        isMine = it[isMine],
        status = it[status],
      )
    }
  }

/** Which Device-screen sections are open and filtered; every flag has the class's own default. */
val SectionStatesCodec: AdapterObjectCodec<SectionStates> =
  AdapterValueCodecs.obj("$UI.theme.SectionStates") {
    val defaults = SectionStates()
    val channelsExpanded =
      field(SectionStates::channelsExpanded, AdapterValueCodecs.Boolean, defaults.channelsExpanded)
    val channelsShowAll =
      field(SectionStates::channelsShowAll, AdapterValueCodecs.Boolean, defaults.channelsShowAll)
    val contactsExpanded =
      field(SectionStates::contactsExpanded, AdapterValueCodecs.Boolean, defaults.contactsExpanded)
    val contactsShowAll =
      field(SectionStates::contactsShowAll, AdapterValueCodecs.Boolean, defaults.contactsShowAll)
    val roomsExpanded =
      field(SectionStates::roomsExpanded, AdapterValueCodecs.Boolean, defaults.roomsExpanded)
    val roomsShowAll =
      field(SectionStates::roomsShowAll, AdapterValueCodecs.Boolean, defaults.roomsShowAll)
    val repeatersExpanded =
      field(
        SectionStates::repeatersExpanded,
        AdapterValueCodecs.Boolean,
        defaults.repeatersExpanded,
      )
    val repeatersShowAll =
      field(SectionStates::repeatersShowAll, AdapterValueCodecs.Boolean, defaults.repeatersShowAll)
    val sensorsExpanded =
      field(SectionStates::sensorsExpanded, AdapterValueCodecs.Boolean, defaults.sensorsExpanded)
    construct {
      SectionStates(
        channelsExpanded = it[channelsExpanded],
        channelsShowAll = it[channelsShowAll],
        contactsExpanded = it[contactsExpanded],
        contactsShowAll = it[contactsShowAll],
        roomsExpanded = it[roomsExpanded],
        roomsShowAll = it[roomsShowAll],
        repeatersExpanded = it[repeatersExpanded],
        repeatersShowAll = it[repeatersShowAll],
        sensorsExpanded = it[sensorsExpanded],
      )
    }
  }

/** The latest message banner on the Device screen: a direct message or a channel post. */
val LastMessageInfoCodec: AdapterValueCodec<LastMessageInfo> =
  AdapterValueCodecs.union("$UI.LastMessageInfo") {
    case(
      "dm",
      AdapterValueCodecs.obj<LastMessageInfo.Dm>("$UI.LastMessageInfo.Dm") {
        val contactKeyHex = field(LastMessageInfo.Dm::contactKeyHex, AdapterValueCodecs.String)
        val contactName =
          field(
            LastMessageInfo.Dm::contactName,
            AdapterValueCodecs.nullable(AdapterValueCodecs.String),
          )
        val text = field(LastMessageInfo.Dm::text, AdapterValueCodecs.String)
        val snr = field(LastMessageInfo.Dm::snr, AdapterValueCodecs.Int, default = 0)
        construct {
          LastMessageInfo.Dm(
            contactKeyHex = it[contactKeyHex],
            contactName = it[contactName],
            text = it[text],
            snr = it[snr],
          )
        }
      },
    )
    case(
      "channel",
      AdapterValueCodecs.obj<LastMessageInfo.Channel>("$UI.LastMessageInfo.Channel") {
        val channelIndex = field(LastMessageInfo.Channel::channelIndex, AdapterValueCodecs.Int)
        val channelName =
          field(
            LastMessageInfo.Channel::channelName,
            AdapterValueCodecs.nullable(AdapterValueCodecs.String),
          )
        val sender =
          field(
            LastMessageInfo.Channel::sender,
            AdapterValueCodecs.nullable(AdapterValueCodecs.String),
          )
        val text = field(LastMessageInfo.Channel::text, AdapterValueCodecs.String)
        val snr = field(LastMessageInfo.Channel::snr, AdapterValueCodecs.Int, default = 0)
        construct {
          LastMessageInfo.Channel(
            channelIndex = it[channelIndex],
            channelName = it[channelName],
            sender = it[sender],
            text = it[text],
            snr = it[snr],
          )
        }
      },
    )
  }

/** The device-settings screen's discovery state: probing, failed, or ready with its buzzer. */
val DeviceSettingsUiStateCodec: AdapterValueCodec<DeviceSettingsUiState> =
  AdapterValueCodecs.union("$UI.DeviceSettingsUiState") {
    singleton(
      "discovering",
      DeviceSettingsUiState.Discovering,
      "$UI.DeviceSettingsUiState.Discovering",
    )
    case(
      "error",
      AdapterValueCodecs.obj<DeviceSettingsUiState.Error>("$UI.DeviceSettingsUiState.Error") {
        val message = field(DeviceSettingsUiState.Error::message, AdapterValueCodecs.String)
        construct { DeviceSettingsUiState.Error(message = it[message]) }
      },
    )
    case(
      "ready",
      AdapterValueCodecs.obj<DeviceSettingsUiState.Ready>("$UI.DeviceSettingsUiState.Ready") {
        val hasBuzzer = field(DeviceSettingsUiState.Ready::hasBuzzer, AdapterValueCodecs.Boolean)
        val buzzerMode =
          field(
            DeviceSettingsUiState.Ready::buzzerMode,
            AdapterValueCodecs.nullable(AdapterValueCodecs.String),
            default = null,
          )
        val buzzerLoading =
          field(DeviceSettingsUiState.Ready::buzzerLoading, AdapterValueCodecs.Boolean, false)
        construct {
          DeviceSettingsUiState.Ready(
            hasBuzzer = it[hasBuzzer],
            buzzerMode = it[buzzerMode],
            buzzerLoading = it[buzzerLoading],
          )
        }
      },
    )
  }

/** The room/repeater login dialog: prompting (perhaps after a failed attempt) or authenticating. */
val LoginDialogStateCodec: AdapterValueCodec<LoginDialogState> =
  AdapterValueCodecs.union("$APP_UI.LoginDialogState") {
    case(
      "prompting",
      AdapterValueCodecs.obj<LoginDialogState.Prompting>("$APP_UI.LoginDialogState.Prompting") {
        val errorMessage =
          field(
            LoginDialogState.Prompting::errorMessage,
            AdapterValueCodecs.nullable(AdapterValueCodecs.String),
            default = null,
          )
        construct { LoginDialogState.Prompting(errorMessage = it[errorMessage]) }
      },
    )
    singleton(
      "authenticating",
      LoginDialogState.Authenticating,
      "$APP_UI.LoginDialogState.Authenticating",
    )
  }

/**
 * The connecting status a design can hold. `DeviceConnectStatus.Failed` carries a `Throwable`,
 * which no stored value can describe, so it is not a case: the failure card stays unadapted.
 */
val DeviceConnectStatusCodec: AdapterValueCodec<DeviceConnectStatus> =
  AdapterValueCodecs.union("$APP_UI.DeviceConnectStatus") {
    case(
      "connecting",
      AdapterValueCodecs.obj<DeviceConnectStatus.Connecting>(
        "$APP_UI.DeviceConnectStatus.Connecting"
      ) {
        val startedAtMs =
          field(DeviceConnectStatus.Connecting::startedAtMs, AdapterValueCodecs.Long)
        val timeoutMs = field(DeviceConnectStatus.Connecting::timeoutMs, AdapterValueCodecs.Long)
        construct {
          DeviceConnectStatus.Connecting(startedAtMs = it[startedAtMs], timeoutMs = it[timeoutMs])
        }
      },
    )
  }

/** The theme picker's current selection; both default as [ThemeSettings] itself does. */
val ThemeSettingsCodec: AdapterObjectCodec<ThemeSettings> =
  AdapterValueCodecs.obj("$APP_UI.theme.ThemeSettings") {
    val mode = field(ThemeSettings::mode, ThemeModeCodec, default = ThemeMode.System)
    val palette = field(ThemeSettings::palette, ThemePaletteCodec, default = ThemePalette.Meshcore)
    construct { ThemeSettings(mode = it[mode], palette = it[palette]) }
  }
