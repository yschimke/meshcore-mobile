package ee.schimke.meshcore.core.protocol

/** Command codes sent App → Device. */
enum class CommandCode(val raw: Byte) {
  AppStart(0x01),
  SendTextMessage(0x02),
  SendChannelTextMessage(0x03),
  GetContacts(0x04),
  GetDeviceTime(0x05),
  SetDeviceTime(0x06),
  SendSelfAdvert(0x07),
  SetAdvertName(0x08),
  AddUpdateContact(0x09),
  SyncNextMessage(0x0A),
  SetRadioParams(0x0B),
  SetRadioTxPower(0x0C),
  ResetPath(0x0D),
  SetAdvertLatLon(0x0E),
  RemoveContact(0x0F),
  ShareContact(0x10),
  ExportContact(0x11),
  ImportContact(0x12),
  Reboot(0x13),
  GetBatteryAndStorage(0x14),
  DeviceQuery(0x16),
  SendLogin(0x1A),
  Logout(0x1D),
  GetContactByKey(0x1E),
  GetChannel(0x1F),
  SetChannel(0x20),
  FactoryReset(0x33),
  GetRadioSettings(0x39);

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun fromRaw(raw: Byte): CommandCode? = byRaw[raw]
  }
}

/** Response codes sent Device → App (synchronous replies). */
enum class ResponseCode(val raw: Byte) {
  Ok(0x00),
  Err(0x01),
  ContactsStart(0x02),
  Contact(0x03),
  EndOfContacts(0x04),
  SelfInfo(0x05),
  Sent(0x06),
  ContactMessageV1(0x07),
  ChannelMessageV1(0x08),
  CurrentTime(0x09),
  NoMoreMessages(0x0A),
  ExportContact(0x0B),
  BatteryAndStorage(0x0C),
  DeviceInfo(0x0D),
  PrivateKey(0x0E),
  Disabled(0x0F),
  ContactMessageV3(0x10),
  ChannelMessageV3(0x11),
  ChannelInfo(0x12),
  RadioSettings(0x19);

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun fromRaw(raw: Byte): ResponseCode? = byRaw[raw]
  }
}

/** Push codes sent Device → App asynchronously (top bit set). */
enum class PushCode(val raw: Byte) {
  Advert(0x80.toByte()),
  PathUpdated(0x81.toByte()),
  SendConfirmed(0x82.toByte()),
  MessagesWaiting(0x83.toByte()),
  RawData(0x84.toByte()),
  LoginSuccess(0x85.toByte()),
  LoginFail(0x86.toByte()),
  StatusResponse(0x87.toByte()),
  LogRxData(0x88.toByte()),
  TraceData(0x89.toByte()),
  NewAdvert(0x8A.toByte());

  companion object {
    private val byRaw = entries.associateBy { it.raw }

    fun fromRaw(raw: Byte): PushCode? = byRaw[raw]
  }
}
