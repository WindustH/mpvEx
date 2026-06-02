package app.windusth.mpvdanmaku.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.windusth.mpvdanmaku.database.entities.DanmakuCommentCacheEntity

@Dao
interface DanmakuCommentCacheDao {
  @Query("SELECT * FROM danmaku_comment_cache WHERE episodeId = :episodeId LIMIT 1")
  suspend fun getByEpisodeId(episodeId: Long): DanmakuCommentCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(cache: DanmakuCommentCacheEntity)

  @Query("DELETE FROM danmaku_comment_cache")
  suspend fun deleteAll()
}
