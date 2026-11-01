package www.jabin24.aitally.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import www.jabin24.aitally.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    fun getAllPendingFlow(): Flow<List<SyncQueueEntity>>

    @Query("SELECT * FROM sync_queue ORDER BY timestamp ASC")
    suspend fun getAllPending(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue")
    fun getPendingCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun dequeue(id: Long)

    @Query("DELETE FROM sync_queue WHERE entityUid = :entityUid")
    suspend fun dequeueByUid(entityUid: String)

    @Query("DELETE FROM sync_queue")
    suspend fun clearAll()

    @Update
    suspend fun update(item: SyncQueueEntity)
}
