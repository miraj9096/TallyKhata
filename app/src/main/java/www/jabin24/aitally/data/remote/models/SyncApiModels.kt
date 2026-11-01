package www.jabin24.aitally.data.remote.models

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CustomerDto(
    @Json(name = "customer_uid") val customerUid: String,
    @Json(name = "user_uid") val userUid: String,
    @Json(name = "name") val name: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "address") val address: String? = "",
    @Json(name = "encrypted_note") val encryptedNote: String? = "",
    @Json(name = "total_due") val totalDue: Double = 0.0
)

@JsonClass(generateAdapter = true)
data class TransactionDto(
    @Json(name = "transaction_uid") val transactionUid: String,
    @Json(name = "customer_uid") val customerUid: String,
    @Json(name = "user_uid") val userUid: String,
    @Json(name = "type") val type: String,
    @Json(name = "amount") val amount: Double,
    @Json(name = "encrypted_details") val encryptedDetails: String? = "",
    @Json(name = "date_millis") val dateMillis: Long,
    @Json(name = "due_date_millis") val dueDateMillis: Long? = null,
    @Json(name = "receipt_number") val receiptNumber: String? = ""
)

@JsonClass(generateAdapter = true)
data class UserDto(
    @Json(name = "user_uid") val userUid: String,
    @Json(name = "name") val name: String,
    @Json(name = "shop_name") val shopName: String,
    @Json(name = "email") val email: String,
    @Json(name = "phone") val phone: String,
    @Json(name = "password_hash") val passwordHash: String? = null
)

@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "action") val action: String = "register",
    @Json(name = "user_uid") val userUid: String,
    @Json(name = "name") val name: String,
    @Json(name = "shop_name") val shopName: String,
    @Json(name = "email") val email: String,
    @Json(name = "password_hash") val passwordHash: String,
    @Json(name = "phone") val phone: String
)

@JsonClass(generateAdapter = true)
data class LoginRequest(
    @Json(name = "action") val action: String = "login",
    @Json(name = "email") val email: String,
    @Json(name = "password_hash") val passwordHash: String
)

@JsonClass(generateAdapter = true)
data class SyncPushRequest(
    @Json(name = "action") val action: String = "sync",
    @Json(name = "user_uid") val userUid: String,
    @Json(name = "customers") val customers: List<CustomerDto>,
    @Json(name = "transactions") val transactions: List<TransactionDto>
)

@JsonClass(generateAdapter = true)
data class ApiResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "message") val message: String? = null,
    @Json(name = "items_synced") val itemsSynced: Int? = null,
    @Json(name = "synced_at") val syncedAt: Long? = null,
    @Json(name = "user") val user: UserDto? = null,
    @Json(name = "customers") val customers: List<CustomerDto>? = null,
    @Json(name = "transactions") val transactions: List<TransactionDto>? = null
)
