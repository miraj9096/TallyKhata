package www.jabin24.aitally.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import www.jabin24.aitally.data.crypto.CryptoManager
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.ui.theme.TallyCashIn
import www.jabin24.aitally.ui.theme.TallyCashOut
import www.jabin24.aitally.ui.theme.TallyPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    transactions: List<TransactionEntity>,
    customers: List<CustomerEntity>,
    onDeleteTransaction: (TransactionEntity) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") } // ALL, CREDIT, DEBIT
    var selectedReceiptTx by remember { mutableStateOf<TransactionEntity?>(null) }
    val customerMap = remember(customers) { customers.associateBy { it.customerUid } }

    val filteredTransactions = remember(transactions, selectedFilter) {
        when (selectedFilter) {
            "CREDIT" -> transactions.filter { it.type == TransactionEntity.TYPE_CREDIT_GIVEN }
            "DEBIT" -> transactions.filter { it.type == TransactionEntity.TYPE_DEBIT_RECEIVED }
            else -> transactions
        }
    }

    val totalAmount = remember(filteredTransactions) {
        filteredTransactions.sumOf { it.amount }
    }

    if (selectedReceiptTx != null) {
        val tx = selectedReceiptTx!!
        val customer = customerMap[tx.customerUid]
        val isCredit = tx.type == TransactionEntity.TYPE_CREDIT_GIVEN
        val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())

        AlertDialog(
            onDismissRequest = { selectedReceiptTx = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Digital Transaction Receipt", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { selectedReceiptTx = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            },
            text = {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isCredit) "CREDIT GIVEN / বাকি প্রদান" else "PAYMENT RECEIVED / টাকা গ্রহণ",
                            fontWeight = FontWeight.Bold,
                            color = if (isCredit) TallyCashOut else TallyCashIn,
                            style = MaterialTheme.typography.labelMedium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "৳${String.format("%.2f", tx.amount)}",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 28.sp,
                            color = if (isCredit) TallyCashOut else TallyCashIn
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text("Customer: ${customer?.name ?: "Unknown"}", fontWeight = FontWeight.SemiBold)
                        Text("Phone: ${customer?.phone ?: "N/A"}", style = MaterialTheme.typography.bodySmall)
                        Text("Date: ${dateFormat.format(Date(tx.dateMillis))}", style = MaterialTheme.typography.bodySmall)
                        if (tx.receiptNumber.isNotBlank()) {
                            Text("Receipt ID: #${tx.receiptNumber}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }

                        val memo = CryptoManager.decrypt(tx.encryptedDetails)
                        if (memo.isNotBlank()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Memo: $memo", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { selectedReceiptTx = null }) {
                    Text("Close")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Filter Chips Bar
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedFilter == "ALL",
                        onClick = { selectedFilter = "ALL" },
                        label = { Text("All (${transactions.size})") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TallyPrimary.copy(alpha = 0.15f))
                    )
                    FilterChip(
                        selected = selectedFilter == "CREDIT",
                        onClick = { selectedFilter = "CREDIT" },
                        label = { Text("বাকি দেওয়া") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TallyCashOut.copy(alpha = 0.15f))
                    )
                    FilterChip(
                        selected = selectedFilter == "DEBIT",
                        onClick = { selectedFilter = "DEBIT" },
                        label = { Text("টাকা পাওয়া") },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = TallyCashIn.copy(alpha = 0.15f))
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Total for this filter:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "৳${String.format("%.2f", totalAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedFilter == "CREDIT") TallyCashOut else if (selectedFilter == "DEBIT") TallyCashIn else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Transactions List
        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Receipt,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No transactions found", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTransactions, key = { it.transactionUid }) { tx ->
                    val customer = customerMap[tx.customerUid]
                    val isCredit = tx.type == TransactionEntity.TYPE_CREDIT_GIVEN
                    val accentColor = if (isCredit) TallyCashOut else TallyCashIn
                    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
                    val memo = CryptoManager.decrypt(tx.encryptedDetails)

                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(10.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedReceiptTx = tx }
                            .testTag("tx_item_${tx.transactionUid}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(accentColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isCredit) "বাকি" else "জমা",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = customer?.name ?: "Customer Ledger",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                if (memo.isNotBlank()) {
                                    Text(
                                        text = memo,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                Text(
                                    text = dateFormat.format(Date(tx.dateMillis)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (isCredit) "+" else "-"}৳${String.format("%.2f", tx.amount)}",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                                    color = accentColor
                                )

                                IconButton(
                                    onClick = { onDeleteTransaction(tx) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
