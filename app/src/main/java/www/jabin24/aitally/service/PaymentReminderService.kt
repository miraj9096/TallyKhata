package www.jabin24.aitally.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import www.jabin24.aitally.data.local.TallyDatabase
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.util.SmsHelper

/**
 * Result data class for an individual payment reminder SMS dispatch.
 */
data class ReminderSendResult(
    val customerUid: String,
    val customerName: String,
    val phone: String,
    val amount: Double,
    val isSuccess: Boolean,
    val message: String
)

/**
 * Result data class for batch payment reminder SMS dispatches.
 */
data class BatchReminderResult(
    val totalAttempted: Int,
    val successCount: Int,
    val failedCount: Int,
    val results: List<ReminderSendResult>
)

/**
 * Service & Utility that uses Android's SmsManager to send payment reminders
 * to customers stored in the local Room database.
 */
object PaymentReminderService {
    private const val TAG = "PaymentReminderService"

    /**
     * Checks if SEND_SMS permission is granted.
     */
    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Sends payment reminder SMS using Android's SmsManager to a specific customer
     * stored in the Room database, identified by customerUid.
     */
    suspend fun sendPaymentReminderToCustomer(
        context: Context,
        customerUid: String,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD,
        customMessage: String? = null
    ): Result<ReminderSendResult> = withContext(Dispatchers.IO) {
        try {
            val db = TallyDatabase.getInstance(context)
            val customer = db.customerDao().getCustomerByUid(customerUid)
                ?: return@withContext Result.failure(IllegalArgumentException("Customer not found in Room database"))

            val user = db.userDao().getActiveUser()
            val shopName = user?.shopName?.ifBlank { "TallyKhata Store" } ?: "TallyKhata Store"

            val result = sendPaymentReminder(
                context = context,
                customer = customer,
                shopName = shopName,
                templateType = templateType,
                customMessage = customMessage
            )
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up customer in Room database", e)
            Result.failure(e)
        }
    }

    /**
     * Directly sends a payment reminder SMS to the given customer entity using Android's SmsManager.
     */
    fun sendPaymentReminder(
        context: Context,
        customer: CustomerEntity,
        shopName: String,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD,
        customMessage: String? = null
    ): Result<ReminderSendResult> {
        val phone = customer.phone.trim()
        if (phone.isBlank()) {
            return Result.failure(IllegalArgumentException("Customer has no phone number recorded"))
        }

        if (!hasSmsPermission(context)) {
            return Result.failure(SecurityException("android.permission.SEND_SMS permission not granted"))
        }

        val messageText = customMessage?.ifBlank { null } ?: SmsHelper.generateMessage(
            templateType = templateType,
            customerName = customer.name,
            shopName = shopName,
            amount = customer.totalDue
        )

        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
        val carrierName = telephonyManager?.simOperatorName?.ifBlank { telephonyManager.networkOperatorName }?.ifBlank { "Carrier" } ?: "Carrier"

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Split message into multipart SMS if needed (standard SMS is 160 GSM chars or 70 Unicode chars)
            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, messageText, null, null)
            }

            Log.d(TAG, "Payment reminder SMS dispatched via TelephonyManager carrier $carrierName to $phone (${parts.size} parts)")

            Result.success(
                ReminderSendResult(
                    customerUid = customer.customerUid,
                    customerName = customer.name,
                    phone = phone,
                    amount = customer.totalDue,
                    isSuccess = true,
                    message = "Reminder SMS sent to $phone via $carrierName for ৳${customer.totalDue}"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS via SmsManager to $phone", e)
            Result.failure(e)
        }
    }

    /**
     * Queries the Room database for all customers belonging to userUid with outstanding dues,
     * and sends payment reminders to each via Android's SmsManager.
     */
    suspend fun sendBatchPaymentReminders(
        context: Context,
        userUid: String,
        minDueAmount: Double = 1.0,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD
    ): BatchReminderResult = withContext(Dispatchers.IO) {
        val db = TallyDatabase.getInstance(context)
        val dueCustomers = db.customerDao().getCustomersWithDue(userUid)
            .filter { it.totalDue >= minDueAmount && it.phone.isNotBlank() }

        val user = db.userDao().getActiveUser()
        val shopName = user?.shopName?.ifBlank { "TallyKhata Store" } ?: "TallyKhata Store"

        val results = mutableListOf<ReminderSendResult>()
        var successCount = 0
        var failedCount = 0

        for (customer in dueCustomers) {
            val res = sendPaymentReminder(
                context = context,
                customer = customer,
                shopName = shopName,
                templateType = templateType
            )
            res.onSuccess {
                successCount++
                results.add(it)
            }.onFailure { err ->
                failedCount++
                results.add(
                    ReminderSendResult(
                        customerUid = customer.customerUid,
                        customerName = customer.name,
                        phone = customer.phone,
                        amount = customer.totalDue,
                        isSuccess = false,
                        message = err.message ?: "Failed to dispatch SMS"
                    )
                )
            }
        }

        BatchReminderResult(
            totalAttempted = dueCustomers.size,
            successCount = successCount,
            failedCount = failedCount,
            results = results
        )
    }
}

/**
 * Android Service to allow background invocation of payment reminders
 * for Room database customers via system Intents.
 */
class PaymentReminderBackgroundService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val customerUid = intent?.getStringExtra(EXTRA_CUSTOMER_UID)
        val userUid = intent?.getStringExtra(EXTRA_USER_UID)

        serviceScope.launch {
            if (!customerUid.isNullOrBlank()) {
                PaymentReminderService.sendPaymentReminderToCustomer(
                    context = applicationContext,
                    customerUid = customerUid
                )
            } else if (!userUid.isNullOrBlank()) {
                PaymentReminderService.sendBatchPaymentReminders(
                    context = applicationContext,
                    userUid = userUid
                )
            }
            stopSelf(startId)
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val EXTRA_CUSTOMER_UID = "extra_customer_uid"
        const val EXTRA_USER_UID = "extra_user_uid"

        fun startForCustomer(context: Context, customerUid: String) {
            val intent = Intent(context, PaymentReminderBackgroundService::class.java).apply {
                putExtra(EXTRA_CUSTOMER_UID, customerUid)
            }
            context.startService(intent)
        }

        fun startBatch(context: Context, userUid: String) {
            val intent = Intent(context, PaymentReminderBackgroundService::class.java).apply {
                putExtra(EXTRA_USER_UID, userUid)
            }
            context.startService(intent)
        }
    }
}
