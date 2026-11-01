package www.jabin24.aitally.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import androidx.core.content.ContextCompat

object SmsHelper {

    enum class TemplateType(val title: String) {
        POLITE_EN("Polite Reminder (English)"),
        URGENT_EN("Due Date Reminder (English)"),
        BENGALI_STANDARD("সাধারণ তাগাদা (বাংলা)"),
        BENGALI_FRIENDLY("বিনম্র অনুরোধ (বাংলা)")
    }

    fun generateMessage(
        templateType: TemplateType,
        customerName: String,
        shopName: String,
        amount: Double,
        dueDate: String? = null
    ): String {
        val formattedAmount = String.format("%.2f", amount)
        val cleanShop = if (shopName.isNotBlank()) shopName else "TallyKhata Store"

        return when (templateType) {
            TemplateType.POLITE_EN ->
                "Dear $customerName, your current outstanding balance at $cleanShop is ৳$formattedAmount. Kindly clear this dues when possible. Thank you!"

            TemplateType.URGENT_EN ->
                "Payment Reminder: Dear $customerName, an amount of ৳$formattedAmount is pending at $cleanShop${if (dueDate != null) " due on $dueDate" else ""}. Please make payment promptly."

            TemplateType.BENGALI_STANDARD ->
                "সম্মানিত $customerName, $cleanShop-এ আপনার মোট বাকি ৳$formattedAmount টাকা। অনুগ্রহ করে বকেয়া পরিশোধ করার জন্য অনুরোধ করা হলো। ধন্যবাদ।"

            TemplateType.BENGALI_FRIENDLY ->
                "আসসালামু আলাইকুম $customerName ভাই, $cleanShop থেকে বকেয়া ৳$formattedAmount টাকার হিসাব পাঠানো হলো। সুবিধামতো পরিশোধ করবেন। ভালো থাকবেন।"
        }
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
     * Sends SMS directly using system SmsManager if permission granted.
     */
    fun sendDirectSms(
        context: Context,
        phoneNumber: String,
        message: String
    ): Result<String> {
        return try {
            if (!hasSmsPermission(context)) {
                return Result.failure(SecurityException("SEND_SMS permission not granted"))
            }

            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            Result.success("SMS sent successfully to $phoneNumber")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Launches external SMS messenger application with pre-filled phone & message.
     */
    fun launchSmsApp(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val uri = Uri.parse("smsto:${phoneNumber.trim()}")
            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Shares reminder message via WhatsApp or other messaging apps.
     */
    fun shareViaWhatsApp(context: Context, phoneNumber: String, message: String): Boolean {
        return try {
            val cleanPhone = phoneNumber.replace("+", "").replace("-", "").replace(" ", "")
            val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }
}
