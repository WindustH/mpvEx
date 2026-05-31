package app.windusth.mpvdanmuku.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.windusth.mpvdanmuku.database.converters.NetworkProtocolConverter
import app.windusth.mpvdanmuku.database.dao.NetworkConnectionDao
import app.windusth.mpvdanmuku.database.dao.PlaybackStateDao
import app.windusth.mpvdanmuku.database.dao.PlaylistDao
import app.windusth.mpvdanmuku.database.dao.RecentlyPlayedDao
import app.windusth.mpvdanmuku.database.dao.VideoMetadataDao
import app.windusth.mpvdanmuku.database.dao.BookmarkDao
import app.windusth.mpvdanmuku.database.entities.BookmarkEntity
import app.windusth.mpvdanmuku.database.entities.PlaybackStateEntity
import app.windusth.mpvdanmuku.database.entities.PlaylistEntity
import app.windusth.mpvdanmuku.database.entities.PlaylistItemEntity
import app.windusth.mpvdanmuku.database.entities.RecentlyPlayedEntity
import app.windusth.mpvdanmuku.database.entities.VideoMetadataEntity
import app.windusth.mpvdanmuku.domain.network.NetworkConnection

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    VideoMetadataEntity::class,
    NetworkConnection::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
    BookmarkEntity::class,
  ],
  version = 10,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class)
abstract class MpvDanmukuDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun playlistDao(): PlaylistDao

  abstract fun bookmarkDao(): BookmarkDao
}
