package ee.schimke.meshcore.data.converter

import androidx.room.TypeConverter
import ee.schimke.meshcore.data.entity.MessageDirection
import ee.schimke.meshcore.data.entity.MessageKind
import ee.schimke.meshcore.data.entity.MessageStatus
import ee.schimke.meshcore.data.entity.TransportType

class MeshConverters {
  @TypeConverter fun fromTransportType(v: TransportType): String = v.name

  @TypeConverter fun toTransportType(v: String): TransportType = TransportType.valueOf(v)

  @TypeConverter fun fromMessageKind(v: MessageKind): String = v.name

  @TypeConverter fun toMessageKind(v: String): MessageKind = MessageKind.valueOf(v)

  @TypeConverter fun fromMessageDirection(v: MessageDirection): String = v.name

  @TypeConverter fun toMessageDirection(v: String): MessageDirection = MessageDirection.valueOf(v)

  @TypeConverter fun fromMessageStatus(v: MessageStatus): String = v.name

  @TypeConverter fun toMessageStatus(v: String): MessageStatus = MessageStatus.valueOf(v)
}
