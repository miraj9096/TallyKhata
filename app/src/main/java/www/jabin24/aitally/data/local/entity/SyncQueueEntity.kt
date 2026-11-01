package www.jabin24.aitally.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val entityType: String, // "USER", "CUSTOMER", "TRANSACTION"
    val entityUid: String,
    val action: String,     // "INSERT", "UPDATE", "DELETE"
    val payloadJson: String,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
