package app.windusth.mpvdanmaku.domain.playbackstate.repository

import app.windusth.mpvdanmaku.database.entities.PlaybackStateEntity

interface PlaybackStateRepository {
  suspend fun upsert(playbackState: PlaybackStateEntity)

  suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity?

  suspend fun clearAllPlaybackStates()

  suspend fun deleteByTitle(mediaTitle: String)

  suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  )
}
