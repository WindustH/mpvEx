package app.windusth.mpvdanmaku.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import app.windusth.mpvdanmaku.database.converters.NetworkProtocolConverter
import app.windusth.mpvdanmaku.database.dao.NetworkConnectionDao
import app.windusth.mpvdanmaku.database.dao.PlaybackStateDao
import app.windusth.mpvdanmaku.database.dao.PlaylistDao
import app.windusth.mpvdanmaku.database.dao.RecentlyPlayedDao
import app.windusth.mpvdanmaku.database.dao.VideoMetadataDao
import app.windusth.mpvdanmaku.database.dao.BookmarkDao
import app.windusth.mpvdanmaku.database.dao.DanmakuMatchCacheDao
import app.windusth.mpvdanmaku.database.dao.DanmakuCommentCacheDao
import app.windusth.mpvdanmaku.database.entities.BookmarkEntity
import app.windusth.mpvdanmaku.database.entities.DanmakuMatchCacheEntity
import app.windusth.mpvdanmaku.database.entities.DanmakuCommentCacheEntity
import app.windusth.mpvdanmaku.database.entities.PlaybackStateEntity
import app.windusth.mpvdanmaku.database.entities.PlaylistEntity
import app.windusth.mpvdanmaku.database.entities.PlaylistItemEntity
import app.windusth.mpvdanmaku.database.entities.RecentlyPlayedEntity
import app.windusth.mpvdanmaku.database.entities.VideoMetadataEntity
import app.windusth.mpvdanmaku.domain.network.NetworkConnection

@Database(
  entities = [
    PlaybackStateEntity::class,
    RecentlyPlayedEntity::class,
    VideoMetadataEntity::class,
    NetworkConnection::class,
    PlaylistEntity::class,
    PlaylistItemEntity::class,
    BookmarkEntity::class,
    DanmakuMatchCacheEntity::class,
    DanmakuCommentCacheEntity::class,
  ],
  version = 11,
  exportSchema = true,
)
@TypeConverters(NetworkProtocolConverter::class)
abstract class MpvDanmakuDatabase : RoomDatabase() {
  abstract fun videoDataDao(): PlaybackStateDao

  abstract fun recentlyPlayedDao(): RecentlyPlayedDao

  abstract fun videoMetadataDao(): VideoMetadataDao

  abstract fun networkConnectionDao(): NetworkConnectionDao

  abstract fun playlistDao(): PlaylistDao

  abstract fun bookmarkDao(): BookmarkDao

  abstract fun danmakuMatchCacheDao(): DanmakuMatchCacheDao

  abstract fun danmakuCommentCacheDao(): DanmakuCommentCacheDao
}
