package www.jabin24.aitally.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val userUid: String,
    val name: String,
    val shopName: String,
    val email: String,
    val passwordHash: String,
    val phone: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isLoggedIn: Boolean = true,
    val syncStatus: String = "SYNCED" // "SYNCED", "PENDING"
)
