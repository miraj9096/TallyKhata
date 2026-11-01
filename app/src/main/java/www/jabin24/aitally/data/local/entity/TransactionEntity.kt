package www.jabin24.aitally.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["customerUid"]),
        Index(value = ["userUid"]),
        Index(value = ["dateMillis"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    val transactionUid: String,
    val customerUid: String,
    val userUid: String,
    val type: String, // "CREDIT_GIVEN" (বাকি দেওয়া) or "DEBIT_RECEIVED" (টাকা পাওয়া)
    val amount: Double,
    val encryptedDetails: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val dueDateMillis: Long? = null,
    val receiptNumber: String = "",
    val syncStatus: String = "SYNCED" // "SYNCED", "PENDING_INSERT", "PENDING_UPDATE", "PENDING_DELETE"
) {
    companion object {
        const val TYPE_CREDIT_GIVEN = "CREDIT_GIVEN"     // Customer takes credit -> Due increases
        const val TYPE_DEBIT_RECEIVED = "DEBIT_RECEIVED" // Customer pays back -> Due decreases
    }
}
