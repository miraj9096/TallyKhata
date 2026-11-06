package www.jabin24.aitally.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.ui.theme.TallyCashIn
import www.jabin24.aitally.ui.theme.TallyCashOut

@Composable
fun AddTransactionDialog(
    customerName: String,
    currentDue: Double,
    initialType: String, // CREDIT_GIVEN or DEBIT_RECEIVED
    onDismiss: () -> Unit,
    onConfirm: (type: String, amount: Double, details: String, dueDateMillis: Long?) -> Unit
) {
    var type by remember { mutableStateOf(initialType) }
    var amountText by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var dueDaysText by remember { mutableStateOf("7") }
    var amountError by remember { mutableStateOf(false) }

    val isCredit = type == TransactionEntity.TYPE_CREDIT_GIVEN
    val accentColor = if (isCredit) TallyCashOut else TallyCashIn

    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val simulatedNewDue = if (isCredit) currentDue + parsedAmount else currentDue - parsedAmount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .background(accentColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Payments,
                            contentDescription = null,
                            tint = accentColor
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (isCredit) "You Gave / বাকি দিলেন" else "You Got / টাকা পেলেন",
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Customer: $customerName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Type selector toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Button(
                        onClick = { type = TransactionEntity.TYPE_CREDIT_GIVEN },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCredit) TallyCashOut else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isCredit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_credit_button")
                    ) {
                        Text("বাকি দিলাম", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { type = TransactionEntity.TYPE_DEBIT_RECEIVED },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isCredit) TallyCashIn else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!isCredit) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("type_debit_button")
                    ) {
                        Text("টাকা পেলাম", fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        amountError = false
                    },
                    label = { Text("Amount / টাকার পরিমাণ (৳) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = amountError,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_amount_input")
                )
                if (amountError) {
                    Text(
                        "Please enter a valid amount greater than 0",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("Item / Memo Description (Encrypted)") },
                    leadingIcon = { Icon(Icons.Default.Description, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("transaction_details_input")
                )

                /*if (isCredit) { 
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = dueDaysText,
                        onValueChange = { dueDaysText = it },
                        label = { Text("Payment Due in Days (দিন পর পরিশোধ)") },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }*/

                Spacer(modifier = Modifier.height(10.dp))

                // Balance Preview Card
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text("Current Due: ", style = MaterialTheme.typography.bodySmall)
                            Text("৳${String.format("%.2f", currentDue)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                            Text("New Due Balance: ", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "৳${String.format("%.2f", simulatedNewDue)}",
                                fontWeight = FontWeight.Bold,
                                color = if (simulatedNewDue > 0) TallyCashOut else TallyCashIn,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            Text(
                                "Secured with AES-256 E2EE. Offline ready.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0) {
                        amountError = true
                        return@Button
                    }
                    val days = dueDaysText.toIntOrNull() ?: 7
                    val dueDate = if (isCredit) System.currentTimeMillis() + (days * 86400000L) else null
                    onConfirm(type, amount, details, dueDate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.testTag("confirm_transaction_button")
            ) {
                Text("Confirm Transaction")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
