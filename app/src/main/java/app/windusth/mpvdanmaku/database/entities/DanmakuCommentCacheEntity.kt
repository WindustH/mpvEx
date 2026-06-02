package app.windusth.mpvdanmaku.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "danmaku_comment_cache")
data class DanmakuCommentCacheEntity(
  @PrimaryKey
  val episodeId: Long,
  val commentsJson: String,
  val cachedAt: Long = System.currentTimeMillis(),
)
