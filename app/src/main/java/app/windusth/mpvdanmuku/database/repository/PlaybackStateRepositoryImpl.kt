package app.windusth.mpvdanmuku.database.repository

import app.windusth.mpvdanmuku.database.entities.PlaybackStateEntity
import app.windusth.mpvdanmuku.database.MpvDanmukuDatabase
import app.windusth.mpvdanmuku.domain.playbackstate.repository.PlaybackStateRepository

class PlaybackStateRepositoryImpl(
  private val database: MpvDanmukuDatabase,
) : PlaybackStateRepository {
  override suspend fun upsert(playbackState: PlaybackStateEntity) {
    database.videoDataDao().upsert(playbackState)
  }

  override suspend fun getVideoDataByTitle(mediaTitle: String): PlaybackStateEntity? =
    database.videoDataDao().getVideoDataByTitle(mediaTitle)

  override suspend fun clearAllPlaybackStates() {
    database.videoDataDao().clearAllPlaybackStates()
  }

  override suspend fun deleteByTitle(mediaTitle: String) {
    database.videoDataDao().deleteByTitle(mediaTitle)
  }

  override suspend fun updateMediaTitle(
    oldTitle: String,
    newTitle: String,
  ) {
    database.videoDataDao().updateMediaTitle(oldTitle, newTitle)
  }
}
