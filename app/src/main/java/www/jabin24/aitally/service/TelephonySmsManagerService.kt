package www.jabin24.aitally.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.TelephonyManager
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
 * Diagnostic carrier information provided by Android TelephonyManager.
 */
data class TelephonyStatus(
    val isSimReady: Boolean,
    val simStateDescription: String,
    val carrierName: String,
    val countryIso: String,
    val isNetworkRoaming: Boolean
)

/**
 * Result data class for an individual payment reminder SMS dispatch.
 */
data class TelephonySmsResult(
    val customerUid: String,
    val customerName: String,
    val phone: String,
    val amount: Double,
    val isSuccess: Boolean,
    val carrierUsed: String,
    val message: String
)

/**
 * Result data class for batch reminder dispatches using TelephonyManager.
 */
data class TelephonyBatchResult(
    val totalAttempted: Int,
    val successCount: Int,
    val failedCount: Int,
    val carrierName: String,
    val results: List<TelephonySmsResult>
)

/**
 * SMS Manager Service that utilizes Android's TelephonyManager
 * to verify carrier readiness and dispatch payment reminder templates
 * to customers stored in the Room database.
 */
object TelephonySmsManagerService {
    private const val TAG = "TelephonySmsManager"

    /**
     * Inspects carrier and SIM status using Android TelephonyManager.
     */
    fun getTelephonyStatus(context: Context): TelephonyStatus {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        if (tm == null) {
            return TelephonyStatus(
                isSimReady = false,
                simStateDescription = "Telephony Service Unavailable",
                carrierName = "Unknown",
                countryIso = "",
                isNetworkRoaming = false
            )
        }

        val simStateDesc = when (tm.simState) {
            TelephonyManager.SIM_STATE_READY -> "SIM Ready"
            TelephonyManager.SIM_STATE_ABSENT -> "No SIM Card"
            TelephonyManager.SIM_STATE_PIN_REQUIRED -> "SIM PIN Required"
            TelephonyManager.SIM_STATE_PUK_REQUIRED -> "SIM PUK Required"
            TelephonyManager.SIM_STATE_NETWORK_LOCKED -> "Network Locked"
            else -> "SIM Initializing"
        }

        val carrier = tm.simOperatorName.ifBlank { tm.networkOperatorName }.ifBlank { "Mobile Operator" }
        val country = tm.simCountryIso.ifBlank { tm.networkCountryIso }.ifBlank { "bd" }
        val isRoaming = tm.isNetworkRoaming

        return TelephonyStatus(
            isSimReady = tm.simState == TelephonyManager.SIM_STATE_READY,
            simStateDescription = simStateDesc,
            carrierName = carrier,
            countryIso = country,
            isNetworkRoaming = isRoaming
        )
    }

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
     * Sends payment reminder template to a customer stored in Room database by customerUid.
     */
    suspend fun sendPaymentReminderToCustomer(
        context: Context,
        customerUid: String,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD,
        customMessage: String? = null
    ): Result<TelephonySmsResult> = withContext(Dispatchers.IO) {
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
     * Sends payment reminder template for the specified customer entity.
     * Uses TelephonyManager to determine network carrier and SmsManager to send.
     */
    fun sendPaymentReminder(
        context: Context,
        customer: CustomerEntity,
        shopName: String,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD,
        customMessage: String? = null
    ): Result<TelephonySmsResult> {
        val phone = customer.phone.trim()
        if (phone.isBlank()) {
            return Result.failure(IllegalArgumentException("Customer has no phone number recorded"))
        }

        if (!hasSmsPermission(context)) {
            return Result.failure(SecurityException("SEND_SMS permission not granted"))
        }

        val telephonyStatus = getTelephonyStatus(context)
        val carrierName = telephonyStatus.carrierName

        val messageText = customMessage?.ifBlank { null } ?: SmsHelper.generateMessage(
            templateType = templateType,
            customerName = customer.name,
            shopName = shopName,
            amount = customer.totalDue
        )

        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(messageText)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phone, null, messageText, null, null)
            }

            Log.d(TAG, "Payment reminder SMS sent via $carrierName to $phone: $messageText")

            Result.success(
                TelephonySmsResult(
                    customerUid = customer.customerUid,
                    customerName = customer.name,
                    phone = phone,
                    amount = customer.totalDue,
                    isSuccess = true,
                    carrierUsed = carrierName,
                    message = "Reminder SMS sent to $phone via $carrierName"
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS via SmsManager with TelephonyManager carrier $carrierName", e)
            Result.failure(e)
        }
    }

    /**
     * Sends payment reminder templates to all customers stored in Room database with pending dues.
     */
    suspend fun sendBatchReminders(
        context: Context,
        userUid: String,
        minDueAmount: Double = 1.0,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD
    ): TelephonyBatchResult = withContext(Dispatchers.IO) {
        val db = TallyDatabase.getInstance(context)
        val dueCustomers = db.customerDao().getCustomersWithDue(userUid)
            .filter { it.totalDue >= minDueAmount && it.phone.isNotBlank() }

        val user = db.userDao().getActiveUser()
        val shopName = user?.shopName?.ifBlank { "TallyKhata Store" } ?: "TallyKhata Store"

        val telephonyStatus = getTelephonyStatus(context)
        val carrier = telephonyStatus.carrierName

        val results = mutableListOf<TelephonySmsResult>()
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
                    TelephonySmsResult(
                        customerUid = customer.customerUid,
                        customerName = customer.name,
                        phone = customer.phone,
                        amount = customer.totalDue,
                        isSuccess = false,
                        carrierUsed = carrier,
                        message = err.message ?: "Failed to dispatch SMS"
                    )
                )
            }
        }

        TelephonyBatchResult(
            totalAttempted = dueCustomers.size,
            successCount = successCount,
            failedCount = failedCount,
            carrierName = carrier,
            results = results
        )
    }
}

/**
 * Android Background Service to execute payment reminders via TelephonyManager
 * for Room database customers in the background.
 */
class TelephonyReminderService : Service() {
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val customerUid = intent?.getStringExtra(EXTRA_CUSTOMER_UID)
        val userUid = intent?.getStringExtra(EXTRA_USER_UID)

        serviceScope.launch {
            if (!customerUid.isNullOrBlank()) {
                TelephonySmsManagerService.sendPaymentReminderToCustomer(
                    context = applicationContext,
                    customerUid = customerUid
                )
            } else if (!userUid.isNullOrBlank()) {
                TelephonySmsManagerService.sendBatchReminders(
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
            val intent = Intent(context, TelephonyReminderService::class.java).apply {
                putExtra(EXTRA_CUSTOMER_UID, customerUid)
            }
            context.startService(intent)
        }

        fun startBatch(context: Context, userUid: String) {
            val intent = Intent(context, TelephonyReminderService::class.java).apply {
                putExtra(EXTRA_USER_UID, userUid)
            }
            context.startService(intent)
        }
    }
}
