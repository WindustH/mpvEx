package app.windusth.mpvdanmaku.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.windusth.mpvdanmaku.database.entities.DanmakuMatchCacheEntity

@Dao
interface DanmakuMatchCacheDao {
  @Query("SELECT * FROM danmaku_match_cache WHERE filePath = :filePath LIMIT 1")
  suspend fun getByFilePath(filePath: String): DanmakuMatchCacheEntity?

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insert(cache: DanmakuMatchCacheEntity)

  @Query("DELETE FROM danmaku_match_cache")
  suspend fun deleteAll()
}
