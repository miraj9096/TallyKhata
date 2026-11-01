package www.jabin24.aitally.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "customers",
    indices = [Index(value = ["userUid"]), Index(value = ["phone"])]
)
data class CustomerEntity(
    @PrimaryKey
    val customerUid: String,
    val userUid: String,
    val name: String,
    val phone: String,
    val address: String = "",
    val encryptedNote: String = "",
    val totalDue: Double = 0.0, // Positive: Customer owes you (বাকি/পাবো); Negative: Advance/Overpaid
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = "SYNCED" // "SYNCED", "PENDING_INSERT", "PENDING_UPDATE", "PENDING_DELETE"
)
