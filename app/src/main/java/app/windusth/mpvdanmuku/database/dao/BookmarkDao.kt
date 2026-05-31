package app.windusth.mpvdanmuku.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.windusth.mpvdanmuku.database.entities.BookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
  @Query("SELECT * FROM bookmarks ORDER BY addedAt DESC")
  fun getAllBookmarks(): Flow<List<BookmarkEntity>>

  @Query("SELECT * FROM bookmarks WHERE path = :path AND type = :type AND (connectionId = :connectionId OR (connectionId IS NULL AND :connectionId IS NULL)) LIMIT 1")
  suspend fun getBookmark(path: String, type: String, connectionId: Long?): BookmarkEntity?

  @Query("SELECT COUNT(*) > 0 FROM bookmarks WHERE path = :path AND type = :type AND (connectionId = :connectionId OR (connectionId IS NULL AND :connectionId IS NULL))")
  fun isBookmarked(path: String, type: String, connectionId: Long?): Flow<Boolean>

  @Insert(onConflict = OnConflictStrategy.REPLACE)
  suspend fun insertBookmark(bookmark: BookmarkEntity)

  @Delete
  suspend fun deleteBookmark(bookmark: BookmarkEntity)

  @Query("DELETE FROM bookmarks WHERE path = :path AND type = :type AND (connectionId = :connectionId OR (connectionId IS NULL AND :connectionId IS NULL))")
  suspend fun deleteBookmarkByPath(path: String, type: String, connectionId: Long?)
}
