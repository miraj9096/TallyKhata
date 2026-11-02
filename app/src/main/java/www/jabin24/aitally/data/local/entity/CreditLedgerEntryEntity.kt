package www.jabin24.aitally.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room Entity representing a credit ledger entry stored locally for offline access.
 * Tracks credit given (বাকি দেওয়া) and payment received (টাকা পাওয়া / জমা)
 * along with customer contact details, running balance, and payment due dates.
 */
@Entity(
    tableName = "credit_ledger_entries",
    indices = [
        Index(value = ["userUid"]),
        Index(value = ["customerUid"]),
        Index(value = ["createdAtMillis"]),
        Index(value = ["entryType"])
    ]
)
data class CreditLedgerEntryEntity(
    @PrimaryKey
    val entryId: String,
    val userUid: String,
    val customerUid: String,
    val customerName: String,
    val customerPhone: String,
    val entryType: String, // ENTRY_TYPE_CREDIT (বাকি) or ENTRY_TYPE_PAYMENT (জমা)
    val amount: Double,
    val balanceAfter: Double = 0.0,
    val description: String = "",
    val dueDateMillis: Long? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val receiptNumber: String = "",
    val syncStatus: String = "SYNCED" // "SYNCED", "PENDING_INSERT", "PENDING_UPDATE", "PENDING_DELETE"
) {
    companion object {
        const val ENTRY_TYPE_CREDIT = "CREDIT_GIVEN"       // You gave credit / বাকি দেওয়া
        const val ENTRY_TYPE_PAYMENT = "PAYMENT_RECEIVED"   // Payment received / টাকা পাওয়া
    }
}
