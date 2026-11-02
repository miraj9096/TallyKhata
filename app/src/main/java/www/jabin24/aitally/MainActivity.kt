package www.jabin24.aitally

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.ui.MainViewModel
import www.jabin24.aitally.ui.components.AddCustomerDialog
import www.jabin24.aitally.ui.components.AddTransactionDialog
import www.jabin24.aitally.ui.components.SmsReminderDialog
import www.jabin24.aitally.ui.screens.AuthScreen
import www.jabin24.aitally.ui.screens.CustomerDetailScreen
import www.jabin24.aitally.ui.screens.DashboardScreen
import www.jabin24.aitally.ui.screens.EmailVerificationGateScreen
import www.jabin24.aitally.ui.screens.SettingsScreen
import www.jabin24.aitally.ui.screens.SyncScreen
import www.jabin24.aitally.ui.screens.TransactionsScreen
import www.jabin24.aitally.ui.theme.TallyKhataTheme
import www.jabin24.aitally.ui.theme.TallyPrimary
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TallyKhataTheme {
                TallyKhataApp()
            }
        }
    }
}

enum class NavigationTab(val title: String) {
    LEDGER("হিসাব খাতা"),
    TRANSACTIONS("লেনদেন"),
    SYNC("সিঙ্ক ও ডাটাবেস"),
    SETTINGS("সেটিংস")
}

@Composable
fun TallyKhataApp(
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val activeUser by viewModel.activeUser.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isAuthLoading by viewModel.isAuthLoading.collectAsStateWithLifecycle()
    val isVerifyingEmail by viewModel.isVerifyingEmail.collectAsStateWithLifecycle()
    val verificationStatusMessage by viewModel.verificationStatusMessage.collectAsStateWithLifecycle()
    val lastVerificationCode by viewModel.lastVerificationCode.collectAsStateWithLifecycle()
    val customers by viewModel.customers.collectAsStateWithLifecycle()
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val selectedCustomer by viewModel.selectedCustomer.collectAsStateWithLifecycle()
    val selectedCustomerTransactions by viewModel.selectedCustomerTransactions.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val syncMessage by viewModel.syncMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val defaultSmsTemplate by viewModel.defaultSmsTemplate.collectAsStateWithLifecycle()
    val autoSyncEnabled by viewModel.autoSyncEnabled.collectAsStateWithLifecycle()
    val pinLockEnabled by viewModel.pinLockEnabled.collectAsStateWithLifecycle()
    val smsSignature by viewModel.smsSignature.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val lastSyncParityReport by viewModel.lastSyncParityReport.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(NavigationTab.LEDGER) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }
    var addTxData by remember { mutableStateOf<Triple<CustomerEntity, Double, String>?>(null) }
    var smsCustomerTarget by remember { mutableStateOf<CustomerEntity?>(null) }

    // Display sync messages in snackbar
    LaunchedEffect(syncMessage) {
        if (syncMessage != null) {
            snackbarHostState.showSnackbar(syncMessage!!)
            viewModel.clearSyncMessage()
        }
    }

    // Dialogs

    if (showAddCustomerDialog) {
        AddCustomerDialog(
            onDismiss = { showAddCustomerDialog = false },
            onConfirm = { name, phone, address, due, note ->
                viewModel.addCustomer(name, phone, address, due, note) { success ->
                    if (success) {
                        Toast.makeText(context, "Customer $name added to ledger", Toast.LENGTH_SHORT).show()
                        showAddCustomerDialog = false
                    } else {
                        Toast.makeText(context, "Failed to save customer", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (addTxData != null) {
        val (targetCust, due, type) = addTxData!!
        AddTransactionDialog(
            customerName = targetCust.name,
            currentDue = due,
            initialType = type,
            onDismiss = { addTxData = null },
            onConfirm = { txType, amount, details, dueDate ->
                viewModel.addTransaction(
                    customerUid = targetCust.customerUid,
                    type = txType,
                    amount = amount,
                    details = details,
                    dueDateMillis = dueDate
                ) { success ->
                    if (success) {
                        Toast.makeText(context, "Transaction of ৳$amount recorded!", Toast.LENGTH_SHORT).show()
                        addTxData = null
                    }
                }
            }
        )
    }

    if (smsCustomerTarget != null) {
        val targetCust = smsCustomerTarget!!
        SmsReminderDialog(
            customer = targetCust,
            shopName = activeUser?.shopName ?: "TallyKhata",
            onDismiss = { smsCustomerTarget = null },
            onDirectSmsSent = { msg ->
                coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                smsCustomerTarget = null
            }
        )
    }

    // Main App Flow: Authentication Gate -> Email Verification Gate -> Ledger / Scaffold
    val user = activeUser
    if (user == null) {
        AuthScreen(
            isLoading = isAuthLoading,
            errorMessage = authError,
            onLogin = { email, password ->
                viewModel.login(email, password)
            },
            onRegister = { name, shopName, email, password, phone ->
                viewModel.register(name, shopName, email, password, phone)
            },
            onOfflineDemoAccess = {
                viewModel.register(
                    name = "Demo Merchant",
                    shopName = "Demo Store",
                    email = "demo@jabin24.com",
                    password = "demopassword123",
                    phone = "01700000000"
                )
            }
        )
    } else if (!user.isEmailVerified) {
        EmailVerificationGateScreen(
            user = user,
            demoCode = lastVerificationCode ?: user.verificationCode,
            isLoading = isVerifyingEmail,
            statusMessage = verificationStatusMessage,
            onVerify = { code ->
                viewModel.verifyEmailCode(user.email, code)
            },
            onResend = {
                viewModel.sendEmailVerificationCode(user.email)
            },
            onLogout = {
                viewModel.logout()
            }
        )
    } else if (selectedCustomer != null) {
        val customer = selectedCustomer!!
            CustomerDetailScreen(
                customer = customer,
                transactions = selectedCustomerTransactions,
                onBack = { viewModel.selectCustomer(null) },
                onAddCreditClick = {
                    addTxData = Triple(customer, customer.totalDue, TransactionEntity.TYPE_CREDIT_GIVEN)
                },
                onAddDebitClick = {
                    addTxData = Triple(customer, customer.totalDue, TransactionEntity.TYPE_DEBIT_RECEIVED)
                },
                onSendSmsClick = {
                    smsCustomerTarget = customer
                },
                onDeleteCustomer = {
                    viewModel.deleteCustomer(customer.customerUid) {
                        Toast.makeText(context, "Customer removed", Toast.LENGTH_SHORT).show()
                    }
                },
                onDeleteTransaction = { tx ->
                    viewModel.deleteTransaction(tx) {
                        Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        } else {
            // Main Scaffold with Bottom Navigation
            Scaffold(
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 10.dp,
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = 0.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == NavigationTab.LEDGER,
                                onClick = { currentTab = NavigationTab.LEDGER },
                                icon = { Icon(Icons.Default.Book, contentDescription = "Khata") },
                                label = { Text("হিসাব খাতা", fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = TallyPrimary.copy(alpha = 0.15f),
                                    selectedIconColor = TallyPrimary,
                                    selectedTextColor = TallyPrimary
                                ),
                                modifier = Modifier.testTag("nav_ledger")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavigationTab.TRANSACTIONS,
                                onClick = { currentTab = NavigationTab.TRANSACTIONS },
                                icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Transactions") },
                                label = { Text("লেনদেন", fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = TallyPrimary.copy(alpha = 0.15f),
                                    selectedIconColor = TallyPrimary,
                                    selectedTextColor = TallyPrimary
                                ),
                                modifier = Modifier.testTag("nav_transactions")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavigationTab.SYNC,
                                onClick = { currentTab = NavigationTab.SYNC },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (pendingSyncCount > 0) {
                                                Badge(
                                                    containerColor = Color(0xFFFFC107),
                                                    contentColor = Color.Black
                                                ) {
                                                    Text("$pendingSyncCount")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.CloudSync, contentDescription = "Sync")
                                    }
                                },
                                label = { Text("সিঙ্ক", fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = TallyPrimary.copy(alpha = 0.15f),
                                    selectedIconColor = TallyPrimary,
                                    selectedTextColor = TallyPrimary
                                ),
                                modifier = Modifier.testTag("nav_sync")
                            )

                            NavigationBarItem(
                                selected = currentTab == NavigationTab.SETTINGS,
                                onClick = { currentTab = NavigationTab.SETTINGS },
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("সেটিংস", fontWeight = FontWeight.SemiBold) },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = TallyPrimary.copy(alpha = 0.15f),
                                    selectedIconColor = TallyPrimary,
                                    selectedTextColor = TallyPrimary
                                ),
                                modifier = Modifier.testTag("nav_settings")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        NavigationTab.LEDGER -> {
                            DashboardScreen(
                                shopName = activeUser?.shopName ?: "TallyKhata Store",
                                userEmail = activeUser?.email ?: "",
                                summary = summary,
                                customers = customers,
                                searchQuery = searchQuery,
                                isSyncing = isSyncing,
                                onSearchChange = { viewModel.updateSearchQuery(it) },
                                onCustomerClick = { customer ->
                                    viewModel.selectCustomer(customer.customerUid)
                                },
                                onSendSmsClick = { customer ->
                                    smsCustomerTarget = customer
                                },
                                onAddCustomerClick = {
                                    showAddCustomerDialog = true
                                },
                                onSyncClick = {
                                    viewModel.triggerSync()
                                },
                                onOpenSyncDiagnostics = {
                                    currentTab = NavigationTab.SYNC
                                },
                                onOpenSettings = {
                                    currentTab = NavigationTab.SETTINGS
                                }
                            )
                        }

                        NavigationTab.TRANSACTIONS -> {
                            TransactionsScreen(
                                transactions = allTransactions,
                                customers = customers,
                                onDeleteTransaction = { tx ->
                                    viewModel.deleteTransaction(tx) {
                                        Toast.makeText(context, "Transaction deleted", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        NavigationTab.SYNC -> {
                            SyncScreen(
                                pendingSyncCount = pendingSyncCount,
                                isSyncing = isSyncing,
                                syncMessage = syncMessage,
                                isOnline = isOnline,
                                lastParityReport = lastSyncParityReport,
                                onBack = { currentTab = NavigationTab.LEDGER },
                                onSyncNow = { viewModel.triggerSync() }
                            )
                        }

                        NavigationTab.SETTINGS -> {
                            SettingsScreen(
                                user = activeUser,
                                defaultSmsTemplate = defaultSmsTemplate,
                                autoSyncEnabled = autoSyncEnabled,
                                pinLockEnabled = pinLockEnabled,
                                smsSignature = smsSignature,
                                onBack = { currentTab = NavigationTab.LEDGER },
                                onUpdateProfile = { name, shop, phone ->
                                    viewModel.updateProfile(name, shop, phone) { _, msg ->
                                        if (msg != null) Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onSetDefaultSmsTemplate = { viewModel.setDefaultSmsTemplate(it) },
                                onSetAutoSync = { viewModel.setAutoSyncEnabled(it) },
                                onSetPinLock = { viewModel.setPinLockEnabled(it) },
                                onSetSmsSignature = { viewModel.setSmsSignature(it) },
                                onOpenSyncScreen = { currentTab = NavigationTab.SYNC },
                                onLogout = { viewModel.logout() }
                            )
                        }
                }
            }
        }
    }
}
