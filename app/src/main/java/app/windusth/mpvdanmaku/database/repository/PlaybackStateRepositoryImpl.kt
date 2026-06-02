package app.windusth.mpvdanmaku.database.repository

import app.windusth.mpvdanmaku.database.entities.PlaybackStateEntity
import app.windusth.mpvdanmaku.database.MpvDanmakuDatabase
import app.windusth.mpvdanmaku.domain.playbackstate.repository.PlaybackStateRepository

class PlaybackStateRepositoryImpl(
  private val database: MpvDanmakuDatabase,
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
