package ee.schimke.meshcore.core.model

/**
 * Anything that can arrive from the device after it has been parsed.
 * Unparsed frames are carried through as [Raw] so that callers can
 * inspect/extend behavior without the library needing to know every
 * opcode.
 */
sealed class MeshEvent {
    data class SelfInfoEvent(val info: SelfInfo) : MeshEvent()
    object ContactsStart : MeshEvent()
    data class ContactEvent(val contact: Contact) : MeshEvent()
    object EndOfContacts : MeshEvent()
    data class Battery(val info: BatteryInfo) : MeshEvent()
    data class Device(val info: DeviceInfo) : MeshEvent()
    data class Radio(val settings: RadioSettings) : MeshEvent()
    data class ChannelInfoEvent(val info: ChannelInfo) : MeshEvent()
    data class CurrentTime(val time: kotlin.time.Instant) : MeshEvent()
    object NoMoreMessages : MeshEvent()
    object Ok : MeshEvent()
    data class Err(val code: Int) : MeshEvent()
    data class Sent(val ack: SendAck) : MeshEvent()
    data class DirectMessage(val message: ReceivedDirectMessage) : MeshEvent()
    data class ChannelMessage(val message: ReceivedChannelMessage) : MeshEvent()

    // Pushes
    data class Advert(val info: AdvertPushInfo) : MeshEvent()
    data class NewAdvert(val info: AdvertPushInfo) : MeshEvent()
    data class PathUpdated(val publicKey: PublicKey) : MeshEvent()
    data class SendConfirmedEvent(val confirmed: SendConfirmed) : MeshEvent()
    object MessagesWaiting : MeshEvent()
    data class LoginSuccess(val publicKey: PublicKey) : MeshEvent()
    data class LoginFail(val publicKey: PublicKey) : MeshEvent()

    /** Catch-all for frames we haven't modelled yet. */
    data class Raw(val code: Byte, val body: kotlinx.io.bytestring.ByteString) : MeshEvent()
}
