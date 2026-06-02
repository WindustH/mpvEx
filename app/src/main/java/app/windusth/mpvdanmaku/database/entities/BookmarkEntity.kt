package app.windusth.mpvdanmaku.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val name: String,
  val path: String,
  val type: String, // "LOCAL" or "NETWORK"
  val connectionId: Long? = null, // Only used for "NETWORK" bookmarks
  val addedAt: Long = System.currentTimeMillis()
)
