package app.windusth.mpvdanmaku.ui.browser.recentlyplayed

import app.windusth.mpvdanmaku.database.entities.PlaylistEntity
import app.windusth.mpvdanmaku.domain.media.model.Video

sealed class RecentlyPlayedItem {
  abstract val timestamp: Long

  data class VideoItem(
    val video: Video,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()

  data class PlaylistItem(
    val playlist: PlaylistEntity,
    val videoCount: Int,
    val mostRecentVideoPath: String,
    override val timestamp: Long,
  ) : RecentlyPlayedItem()
}
