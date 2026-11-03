package www.jabin24.aitally.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import www.jabin24.aitally.data.local.entity.CreditLedgerEntryEntity
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.data.local.entity.UserEntity
import www.jabin24.aitally.data.repository.SyncParityReport
import www.jabin24.aitally.data.repository.SyncResult
import www.jabin24.aitally.data.repository.TallyRepository
import www.jabin24.aitally.util.SmsHelper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DashboardSummary(
    val totalReceivable: Double = 0.0,  // You will get (পাবেন)
    val totalPayable: Double = 0.0,     // You have to give (দিতে হবে)
    val totalCustomers: Int = 0,
    val pendingSyncCount: Int = 0
)

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {
    val repository = TallyRepository(application)

    val isOnline: StateFlow<Boolean> = repository.syncRepository.isOnlineFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _lastSyncParityReport = MutableStateFlow<SyncParityReport?>(null)
    val lastSyncParityReport: StateFlow<SyncParityReport?> = _lastSyncParityReport.asStateFlow()

    val activeUser: StateFlow<UserEntity?> = repository.activeUserFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val pendingSyncCount: StateFlow<Int> = repository.pendingSyncCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _selectedCustomerUid = MutableStateFlow<String?>(null)
    val selectedCustomerUid: StateFlow<String?> = _selectedCustomerUid.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _pendingVerificationEmail = MutableStateFlow<String?>(null)
    val pendingVerificationEmail: StateFlow<String?> = _pendingVerificationEmail.asStateFlow()

    private val _isVerifyingEmail = MutableStateFlow(false)
    val isVerifyingEmail: StateFlow<Boolean> = _isVerifyingEmail.asStateFlow()

    private val _verificationStatusMessage = MutableStateFlow<String?>(null)
    val verificationStatusMessage: StateFlow<String?> = _verificationStatusMessage.asStateFlow()

    private val _lastVerificationCode = MutableStateFlow<String?>(null)
    val lastVerificationCode: StateFlow<String?> = _lastVerificationCode.asStateFlow()

    // Customers Flow based on active user and search
    val customers: StateFlow<List<CustomerEntity>> = activeUser.flatMapLatest { user ->
        if (user == null) {
            flowOf(emptyList())
        } else {
            combine(_searchQuery) { (query) ->
                query
            }.flatMapLatest { query ->
                if (query.isBlank()) {
                    repository.getCustomersFlow(user.userUid)
                } else {
                    repository.searchCustomersFlow(user.userUid, query.trim())
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All transactions for current user
    val allTransactions: StateFlow<List<TransactionEntity>> = activeUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else repository.getAllTransactionsFlow(user.userUid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dedicated Credit Ledger Entries StateFlow stored in Room for offline access
    val creditLedgerEntries: StateFlow<List<CreditLedgerEntryEntity>> = activeUser.flatMapLatest { user ->
        if (user == null) flowOf(emptyList()) else repository.getAllCreditLedgerEntriesFlow(user.userUid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Selected customer
    val selectedCustomer: StateFlow<CustomerEntity?> = _selectedCustomerUid.flatMapLatest { uid ->
        if (uid == null) flowOf(null) else repository.getCustomerByUidFlow(uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Transactions for selected customer
    val selectedCustomerTransactions: StateFlow<List<TransactionEntity>> =
        _selectedCustomerUid.flatMapLatest { uid ->
            if (uid == null) flowOf(emptyList()) else repository.getTransactionsForCustomerFlow(uid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Summary calculations
    val summary: StateFlow<DashboardSummary> = combine(customers, pendingSyncCount) { custList, syncCount ->
        var receivable = 0.0
        var payable = 0.0
        for (c in custList) {
            if (c.totalDue > 0) {
                receivable += c.totalDue
            } else if (c.totalDue < 0) {
                payable += (-c.totalDue)
            }
        }
        DashboardSummary(
            totalReceivable = receivable,
            totalPayable = payable,
            totalCustomers = custList.size,
            pendingSyncCount = syncCount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardSummary())

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectCustomer(customerUid: String?) {
        _selectedCustomerUid.value = customerUid
    }

    fun clearSyncMessage() {
        _syncMessage.value = null
    }

    fun clearAuthError() {
        _authError.value = null
    }

    fun clearVerificationStatusMessage() {
        _verificationStatusMessage.value = null
    }

    // ==================== AUTHENTICATION & EMAIL VERIFICATION ====================

    fun register(
        name: String,
        shopName: String,
        email: String,
        password: String,
        phone: String,
        onSuccess: () -> Unit = {}
    ) {
        if (name.isBlank() || email.isBlank() || password.isBlank() || phone.isBlank()) {
            _authError.value = "Please fill in all fields / সকল তথ্য পূরণ করুন"
            return
        }
        _isAuthLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val result = repository.registerUser(name, shopName, email, password, phone)
            _isAuthLoading.value = false
            result.onSuccess { createdUser ->
                _pendingVerificationEmail.value = createdUser.email
                _lastVerificationCode.value = createdUser.verificationCode
                _verificationStatusMessage.value = "Verification code sent to ${createdUser.email}."
                onSuccess()
            }.onFailure { err ->
                _authError.value = err.message ?: "Registration failed"
            }
        }
    }

    fun login(email: String, password: String, onSuccess: () -> Unit = {}) {
        if (email.isBlank() || password.isBlank()) {
            _authError.value = "Please enter email and password / ইমেইল ও পাসওয়ার্ড লিখুন"
            return
        }
        _isAuthLoading.value = true
        _authError.value = null
        viewModelScope.launch {
            val result = repository.loginUser(email, password)
            _isAuthLoading.value = false
            result.onSuccess { user ->
                _pendingVerificationEmail.value = user.email
                _lastVerificationCode.value = user.verificationCode
                _authError.value = null
                onSuccess()
            }.onFailure { err ->
                val msg = err.message ?: "Authentication failed"
                if (msg.startsWith("EMAIL_NOT_VERIFIED:")) {
                    val cleanMsg = msg.removePrefix("EMAIL_NOT_VERIFIED:")
                    _pendingVerificationEmail.value = email.trim()
                    _verificationStatusMessage.value = cleanMsg
                    _authError.value = cleanMsg
                } else {
                    _authError.value = msg
                }
            }
        }
    }

    fun sendEmailVerificationCode(email: String, onSent: (String) -> Unit = {}) {
        val targetEmail = email.ifBlank { _pendingVerificationEmail.value ?: activeUser.value?.email ?: "" }
        if (targetEmail.isBlank()) {
            _verificationStatusMessage.value = "Email address cannot be empty"
            return
        }
        _isVerifyingEmail.value = true
        _verificationStatusMessage.value = "Sending 6-digit verification code to $targetEmail..."
        viewModelScope.launch {
            val user = activeUser.value
            val res = repository.sendVerificationCode(targetEmail, user?.userUid)
            _isVerifyingEmail.value = false
            res.onSuccess { code ->
                _lastVerificationCode.value = code
                _pendingVerificationEmail.value = targetEmail
                _verificationStatusMessage.value = "Verification code sent to $targetEmail. Check your inbox"
                onSent(code)
            }.onFailure { err ->
                _verificationStatusMessage.value = err.message ?: "Failed to send code"
            }
        }
    }

    fun verifyEmailCode(email: String, code: String, onSuccess: () -> Unit = {}) {
        val targetEmail = email.ifBlank { _pendingVerificationEmail.value ?: activeUser.value?.email ?: "" }
        val trimmedCode = code.trim()
        if (trimmedCode.length != 6) {
            _verificationStatusMessage.value = "Please enter the 6-digit code / ৬-ডিজিটের কোড লিখুন"
            return
        }
        _isVerifyingEmail.value = true
        _verificationStatusMessage.value = "Verifying OTP code in..."
        viewModelScope.launch {
            val res = repository.verifyEmailCode(targetEmail, trimmedCode)
            _isVerifyingEmail.value = false
            res.onSuccess {
                _verificationStatusMessage.value = "Email verified successfully!"
                onSuccess()
            }.onFailure { err ->
                _verificationStatusMessage.value = err.message ?: "Verification failed"
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            repository.logout()
            _selectedCustomerUid.value = null
            _pendingVerificationEmail.value = null
        }
    }

    // ==================== CUSTOMERS ====================

    fun addCustomer(
        name: String,
        phone: String,
        address: String = "",
        initialDue: Double = 0.0,
        note: String = "",
        onComplete: (Boolean) -> Unit = {}
    ) {
        val user = activeUser.value ?: return
        if (name.isBlank() || phone.isBlank()) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val res = repository.addCustomer(
                userUid = user.userUid,
                name = name,
                phone = phone,
                address = address,
                initialDue = initialDue,
                note = note
            )
            onComplete(res.isSuccess)
        }
    }

    fun deleteCustomer(customerUid: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteCustomer(customerUid)
            if (_selectedCustomerUid.value == customerUid) {
                _selectedCustomerUid.value = null
            }
            onComplete()
        }
    }

    // ==================== TRANSACTIONS ====================

    fun addTransaction(
        customerUid: String,
        type: String, // CREDIT_GIVEN or DEBIT_RECEIVED
        amount: Double,
        details: String = "",
        dueDateMillis: Long? = null,
        onComplete: (Boolean) -> Unit = {}
    ) {
        val user = activeUser.value ?: return
        if (amount <= 0) {
            onComplete(false)
            return
        }
        viewModelScope.launch {
            val res = repository.addTransaction(
                customerUid = customerUid,
                userUid = user.userUid,
                type = type,
                amount = amount,
                details = details,
                dueDateMillis = dueDateMillis
            )
            onComplete(res.isSuccess)
        }
    }

    fun deleteTransaction(transaction: TransactionEntity, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
            onComplete()
        }
    }

    // ==================== SYNC ====================

    fun triggerSync() {
        val user = activeUser.value ?: return
        if (_isSyncing.value) return
        _isSyncing.value = true
        _syncMessage.value = "Connecting to database..."
        viewModelScope.launch {
            val report = repository.synchronizeWithParity(user.userUid)
            _lastSyncParityReport.value = report
            _isSyncing.value = false
            if (report.isSuccess) {
                _syncMessage.value = "✅ ${report.statusMessage}"
            } else {
                _syncMessage.value = "⚡ ${report.statusMessage}"
            }
        }
    }

    // ==================== SMS REMINDER ====================

    fun sendSmsReminder(
        customer: CustomerEntity,
        templateType: SmsHelper.TemplateType,
        directSms: Boolean,
        onResult: (String) -> Unit
    ) {
        val user = activeUser.value
        val shopName = user?.shopName ?: "TallyKhata"
        val message = SmsHelper.generateMessage(
            templateType = templateType,
            customerName = customer.name,
            shopName = shopName,
            amount = customer.totalDue
        )

        if (directSms) {
            val res = SmsHelper.sendDirectSms(
                context = getApplication(),
                phoneNumber = customer.phone,
                message = message
            )
            res.onSuccess {
                onResult(it)
            }.onFailure {
                // If direct SMS fails (e.g. permission or device limits), fallback to launch SMS app
                val launched = SmsHelper.launchSmsApp(getApplication(), customer.phone, message)
                if (launched) {
                    onResult("Opened SMS Messenger with reminder")
                } else {
                    onResult("Could not send SMS: ${it.localizedMessage}")
                }
            }
        } else {
            val launched = SmsHelper.launchSmsApp(getApplication(), customer.phone, message)
            if (launched) {
                onResult("Opened SMS Messenger with reminder")
            } else {
                onResult("Unable to open messaging app")
            }
        }
    }

    // ==================== USER SETTINGS ====================

    private val _defaultSmsTemplate = MutableStateFlow(SmsHelper.TemplateType.BENGALI_STANDARD)
    val defaultSmsTemplate: StateFlow<SmsHelper.TemplateType> = _defaultSmsTemplate.asStateFlow()

    private val _autoSyncEnabled = MutableStateFlow(true)
    val autoSyncEnabled: StateFlow<Boolean> = _autoSyncEnabled.asStateFlow()

    private val _pinLockEnabled = MutableStateFlow(false)
    val pinLockEnabled: StateFlow<Boolean> = _pinLockEnabled.asStateFlow()

    private val _smsSignature = MutableStateFlow("")
    val smsSignature: StateFlow<String> = _smsSignature.asStateFlow()

    fun setDefaultSmsTemplate(template: SmsHelper.TemplateType) {
        _defaultSmsTemplate.value = template
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        _autoSyncEnabled.value = enabled
    }

    fun setPinLockEnabled(enabled: Boolean) {
        _pinLockEnabled.value = enabled
    }

    fun setSmsSignature(sig: String) {
        _smsSignature.value = sig
    }

    fun updateProfile(
        name: String,
        shopName: String,
        phone: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (name.isBlank() || phone.isBlank()) {
            onComplete(false, "Name and mobile number cannot be empty")
            return
        }
        viewModelScope.launch {
            val res = repository.updateUserProfile(name, shopName, phone)
            res.onSuccess {
                onComplete(true, "Profile updated successfully")
            }.onFailure {
                onComplete(false, it.message ?: "Failed to update profile")
            }
        }
    }

    fun changePassword(
        oldPass: String,
        newPass: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        if (oldPass.isBlank() || newPass.isBlank()) {
            onComplete(false, "Please enter both old and new password")
            return
        }
        viewModelScope.launch {
            val res = repository.changePassword(oldPass, newPass)
            res.onSuccess {
                onComplete(true, "Password changed successfully")
            }.onFailure {
                onComplete(false, it.message ?: "Failed to change password")
            }
        }
    }

    /**
     * Uses Android SmsManager via PaymentReminderService to send payment reminders
     * to a customer stored in Room database.
     */
    fun sendPaymentReminderViaSmsManager(
        customerUid: String,
        templateType: SmsHelper.TemplateType = SmsHelper.TemplateType.BENGALI_STANDARD,
        onComplete: (Boolean, String) -> Unit = { _, _ -> }
    ) {
        viewModelScope.launch {
            val result = www.jabin24.aitally.service.PaymentReminderService.sendPaymentReminderToCustomer(
                context = getApplication(),
                customerUid = customerUid,
                templateType = templateType
            )
            result.onSuccess {
                onComplete(true, it.message)
            }.onFailure {
                onComplete(false, it.message ?: "Failed to send SMS")
            }
        }
    }

    /**
     * Uses Android SmsManager via PaymentReminderService to send bulk reminders
     * to all due customers stored in Room database.
     */
    fun sendBatchRemindersViaSmsManager(
        minDue: Double = 1.0,
        onComplete: (www.jabin24.aitally.service.BatchReminderResult) -> Unit = {}
    ) {
        viewModelScope.launch {
            val user = activeUser.value ?: return@launch
            val result = www.jabin24.aitally.service.PaymentReminderService.sendBatchPaymentReminders(
                context = getApplication(),
                userUid = user.userUid,
                minDueAmount = minDue
            )
            onComplete(result)
        }
    }
}
