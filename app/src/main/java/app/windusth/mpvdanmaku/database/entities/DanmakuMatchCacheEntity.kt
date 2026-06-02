package app.windusth.mpvdanmaku.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "danmaku_match_cache")
data class DanmakuMatchCacheEntity(
  @PrimaryKey
  val filePath: String,
  val episodeId: Long,
  val animeTitle: String,
  val episodeTitle: String,
  val cachedAt: Long = System.currentTimeMillis(),
)
