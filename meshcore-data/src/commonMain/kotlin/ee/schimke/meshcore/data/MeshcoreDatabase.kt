package ee.schimke.meshcore.data

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import ee.schimke.meshcore.data.converter.MeshConverters
import ee.schimke.meshcore.data.dao.ChannelDao
import ee.schimke.meshcore.data.dao.ContactDao
import ee.schimke.meshcore.data.dao.DeviceDao
import ee.schimke.meshcore.data.dao.DeviceStateDao
import ee.schimke.meshcore.data.dao.MessageDao
import ee.schimke.meshcore.data.entity.ChannelEntity
import ee.schimke.meshcore.data.entity.ContactEntity
import ee.schimke.meshcore.data.entity.DeviceEntity
import ee.schimke.meshcore.data.entity.DeviceStateEntity
import ee.schimke.meshcore.data.entity.MessageEntity

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object MeshcoreDatabaseConstructor : RoomDatabaseConstructor<MeshcoreDatabase> {
  override fun initialize(): MeshcoreDatabase
}

@Database(
  entities =
    [
      DeviceEntity::class,
      DeviceStateEntity::class,
      ContactEntity::class,
      ChannelEntity::class,
      MessageEntity::class,
    ],
  version = 1,
  exportSchema = true,
)
@ConstructedBy(MeshcoreDatabaseConstructor::class)
@TypeConverters(MeshConverters::class)
abstract class MeshcoreDatabase : RoomDatabase() {
  abstract fun deviceDao(): DeviceDao

  abstract fun deviceStateDao(): DeviceStateDao

  abstract fun contactDao(): ContactDao

  abstract fun channelDao(): ChannelDao

  abstract fun messageDao(): MessageDao
}
