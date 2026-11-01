package www.jabin24.aitally.ui.components

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.ui.theme.TallyCashOut
import www.jabin24.aitally.ui.theme.TallyPrimary
import www.jabin24.aitally.util.SmsHelper

@Composable
fun SmsReminderDialog(
    customer: CustomerEntity,
    shopName: String,
    onDismiss: () -> Unit,
    onDirectSmsSent: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedTemplate by remember { mutableStateOf(SmsHelper.TemplateType.BENGALI_STANDARD) }
    var useDirectSms by remember { mutableStateOf(true) }

    val messageText = remember(selectedTemplate, customer.totalDue, customer.name, shopName) {
        SmsHelper.generateMessage(
            templateType = selectedTemplate,
            customerName = customer.name,
            shopName = shopName,
            amount = customer.totalDue
        )
    }

    // Permission launcher for SEND_SMS
    val smsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val result = SmsHelper.sendDirectSms(context, customer.phone, messageText)
            result.onSuccess {
                Toast.makeText(context, "SMS sent to ${customer.phone}", Toast.LENGTH_LONG).show()
                onDirectSmsSent("SMS sent successfully")
                onDismiss()
            }.onFailure {
                // Fallback to launching messaging app
                SmsHelper.launchSmsApp(context, customer.phone, messageText)
                onDismiss()
            }
        } else {
            // Permission denied -> launch external SMS app seamlessly
            Toast.makeText(context, "Opening SMS app with reminder text", Toast.LENGTH_SHORT).show()
            SmsHelper.launchSmsApp(context, customer.phone, messageText)
            onDismiss()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(TallyPrimary.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Icon(Icons.Default.Message, contentDescription = null, tint = TallyPrimary)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Payment Reminder SMS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${customer.name} • ৳${String.format("%.2f", customer.totalDue)} Due",
                        style = MaterialTheme.typography.bodySmall,
                        color = TallyCashOut,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Select Reminder Template:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                SmsHelper.TemplateType.values().forEach { template ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTemplate = template }
                            .padding(vertical = 3.dp)
                    ) {
                        RadioButton(
                            selected = selectedTemplate == template,
                            onClick = { selectedTemplate = template }
                        )
                        Text(
                            text = template.title,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // SMS Live Preview Card
                Text(
                    "SMS Message Preview:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = messageText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.height(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "To: ${customer.phone}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Delivery Options
                Text(
                    "Delivery Method:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Surface(
                        color = if (useDirectSms) TallyPrimary.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (useDirectSms) TallyPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { useDirectSms = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = useDirectSms, onClick = { useDirectSms = true })
                            Text("Direct SIM SMS", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Surface(
                        color = if (!useDirectSms) TallyPrimary.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!useDirectSms) TallyPrimary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { useDirectSms = false }
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = !useDirectSms, onClick = { useDirectSms = false })
                            Text("Open SMS App", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // WhatsApp quick button
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        SmsHelper.shareViaWhatsApp(context, customer.phone, messageText)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send via WhatsApp")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (useDirectSms) {
                        if (SmsHelper.hasSmsPermission(context)) {
                            val res = SmsHelper.sendDirectSms(context, customer.phone, messageText)
                            res.onSuccess {
                                Toast.makeText(context, "SMS sent to ${customer.phone}", Toast.LENGTH_SHORT).show()
                                onDirectSmsSent("SMS sent successfully")
                                onDismiss()
                            }.onFailure {
                                SmsHelper.launchSmsApp(context, customer.phone, messageText)
                                onDismiss()
                            }
                        } else {
                            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        }
                    } else {
                        SmsHelper.launchSmsApp(context, customer.phone, messageText)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TallyPrimary),
                modifier = Modifier.testTag("send_sms_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.height(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Send SMS Reminder")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
