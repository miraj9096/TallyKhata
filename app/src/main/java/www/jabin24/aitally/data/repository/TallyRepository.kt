package www.jabin24.aitally.data.repository

import android.content.Context
import www.jabin24.aitally.data.crypto.CryptoManager
import www.jabin24.aitally.data.local.TallyDatabase
import www.jabin24.aitally.data.local.entity.CreditLedgerEntryEntity
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.SyncQueueEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.data.local.entity.UserEntity
import www.jabin24.aitally.data.remote.RetrofitClient
import www.jabin24.aitally.data.remote.models.CustomerDto
import www.jabin24.aitally.data.remote.models.LoginRequest
import www.jabin24.aitally.data.remote.models.RegisterRequest
import www.jabin24.aitally.data.remote.models.SyncPushRequest
import www.jabin24.aitally.data.remote.models.TransactionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID

sealed class SyncResult {
    data class Success(val message: String, val count: Int, val timestamp: Long) : SyncResult()
    data class Offline(val message: String, val pendingCount: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

class TallyRepository(context: Context) {
    private val db = TallyDatabase.getInstance(context)
    private val userDao = db.userDao()
    private val customerDao = db.customerDao()
    private val transactionDao = db.transactionDao()
    private val creditLedgerDao = db.creditLedgerEntryDao()
    private val syncQueueDao = db.syncQueueDao()
    private val apiService = RetrofitClient.apiService
    val syncRepository = TallySyncRepository(context, db, apiService)

    val activeUserFlow: Flow<UserEntity?> = userDao.getActiveUserFlow()
    val pendingSyncCountFlow: Flow<Int> = syncQueueDao.getPendingCountFlow()

    fun getAllCreditLedgerEntriesFlow(userUid: String): Flow<List<CreditLedgerEntryEntity>> =
        creditLedgerDao.getAllEntriesFlow(userUid)

    fun getCustomerCreditLedgerEntriesFlow(customerUid: String): Flow<List<CreditLedgerEntryEntity>> =
        creditLedgerDao.getEntriesForCustomerFlow(customerUid)


    fun getCustomersFlow(userUid: String): Flow<List<CustomerEntity>> =
        customerDao.getCustomersFlow(userUid)

    fun getCustomerByUidFlow(customerUid: String): Flow<CustomerEntity?> =
        customerDao.getCustomerByUidFlow(customerUid)

    fun getTransactionsForCustomerFlow(customerUid: String): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsForCustomerFlow(customerUid)

    fun getAllTransactionsFlow(userUid: String): Flow<List<TransactionEntity>> =
        transactionDao.getAllTransactionsFlow(userUid)

    fun searchCustomersFlow(userUid: String, query: String): Flow<List<CustomerEntity>> =
        customerDao.searchCustomersFlow(userUid, query)

    suspend fun verifyAllExistingUsers() = withContext(Dispatchers.IO) {
        userDao.verifyAllUsers()
    }

    // ==========================================
    // AUTHENTICATION
    // ==========================================

    suspend fun registerUser(
        name: String,
        shopName: String,
        email: String,
        password: String,
        phone: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val userUid = "usr_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val passwordHash = CryptoManager.hashPassword(password)
            val initialVerifyCode = (100000..999999).random().toString()
            val expiry = System.currentTimeMillis() + 15 * 60 * 1000L

            var user = UserEntity(
                userUid = userUid,
                name = name.trim(),
                shopName = shopName.trim(),
                email = email.trim().lowercase(),
                passwordHash = passwordHash,
                phone = phone.trim(),
                isLoggedIn = true, // Active session awaiting email OTP verification
                syncStatus = "PENDING",
                isEmailVerified = false,
                verificationCode = initialVerifyCode,
                codeExpiresAt = expiry
            )

            // Save user in local DB as session awaiting OTP verification
            userDao.logoutAll()
            userDao.insertUser(user)

            // Enqueue sync
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "USER",
                    entityUid = userUid,
                    action = "INSERT",
                    payloadJson = """{"email":"${user.email}","name":"${user.name}"}"""
                )
            )

            // Try registering remotely on MySQL backend (jabin24.com/api/tally_sync.php)
            try {
                val response = apiService.registerUser(
                    RegisterRequest(
                        userUid = userUid,
                        name = user.name,
                        shopName = user.shopName,
                        email = user.email,
                        passwordHash = passwordHash,
                        phone = user.phone
                    )
                )
                if (response.isSuccessful) {
                    val body = response.body()
                    val serverCode = body?.code
                    if (!serverCode.isNullOrBlank()) {
                        user = user.copy(verificationCode = serverCode)
                    }
                    userDao.updateUser(user.copy(syncStatus = "SYNCED", isEmailVerified = false))
                    syncQueueDao.dequeueByUid(userUid)
                }
            } catch (e: Exception) {
                // Network unavailable; user is stored safely offline with initialVerifyCode
            }

            // Seed initial demo data for store
            seedSampleKhataIfEmpty(userUid)

            Result.success(user)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loginUser(email: String, password: String): Result<UserEntity> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase()
                val passwordHash = CryptoManager.hashPassword(password)

                // 1. Check local DB first for instant offline authentication
                val localUser = userDao.getUserByEmail(cleanEmail)
                if (localUser != null) {
                    if (localUser.passwordHash == passwordHash) {
                        userDao.logoutAll()
                        userDao.setLoggedIn(localUser.userUid)
                        if (!localUser.isEmailVerified) {
                            sendVerificationCode(cleanEmail, localUser.userUid)
                            return@withContext Result.success(localUser.copy(isLoggedIn = true, isEmailVerified = false))
                        }
                        return@withContext Result.success(localUser.copy(isLoggedIn = true))
                    } else {
                        return@withContext Result.failure(Exception("Incorrect password"))
                    }
                }

                // 2. If not local, try online login with jabin24.com
                try {
                    val response = apiService.loginUser(
                        LoginRequest(
                            email = cleanEmail,
                            passwordHash = passwordHash
                        )
                    )
                    if (response.isSuccessful) {
                        val body = response.body()
                        val remoteUser = body?.user
                        if (remoteUser != null) {
                            val userUid = remoteUser.userUid.ifBlank { "usr_" + UUID.randomUUID().toString().replace("-", "").take(12) }
                            val isVerified = (body.status == "success") && (remoteUser.isEmailVerified == true)
                            val loginUser = UserEntity(
                                userUid = userUid,
                                name = remoteUser.name,
                                shopName = remoteUser.shopName,
                                email = cleanEmail,
                                passwordHash = passwordHash,
                                phone = remoteUser.phone,
                                isLoggedIn = true,
                                syncStatus = "SYNCED",
                                isEmailVerified = isVerified,
                                verificationCode = body.code
                            )
                            userDao.logoutAll()
                            userDao.insertUser(loginUser)
                            userDao.setLoggedIn(loginUser.userUid)
                            if (!isVerified) {
                                sendVerificationCode(cleanEmail, loginUser.userUid)
                                return@withContext Result.success(loginUser)
                            }
                            pullRemoteData(loginUser.userUid)
                            return@withContext Result.success(loginUser)
                        }
                    }
                } catch (e: Exception) {
                    // Remote failed
                }

                Result.failure(Exception("Account not found. Please register or check your credentials."))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun sendVerificationCode(email: String, userUid: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase()
                var code = (100000..999999).random().toString()
                val expiry = System.currentTimeMillis() + 15 * 60 * 1000L

                // Update local DB for instant verification
                userDao.setVerificationCode(cleanEmail, code, expiry)

                // Dispatch to remote server
                try {
                    val response = apiService.sendVerificationCode(
                        www.jabin24.aitally.data.remote.models.SendCodeRequest(
                            email = cleanEmail,
                            userUid = userUid
                        )
                    )
                    if (response.isSuccessful) {
                        val serverCode = response.body()?.code
                        if (!serverCode.isNullOrBlank()) {
                            code = serverCode
                            userDao.setVerificationCode(cleanEmail, code, expiry)
                        }
                    }
                } catch (e: Exception) {
                    // Offline fallback: generated code is stored in local Room DB
                }

                Result.success(code)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun verifyEmailCode(email: String, inputCode: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val cleanEmail = email.trim().lowercase()
                val trimmedCode = inputCode.trim()

                if (trimmedCode.length != 6) {
                    return@withContext Result.failure(Exception("Please enter a valid 6-digit code"))
                }

                var isVerified = false

                // 1. Try remote verification
                try {
                    val response = apiService.verifyEmailCode(
                        www.jabin24.aitally.data.remote.models.VerifyCodeRequest(
                            email = cleanEmail,
                            code = trimmedCode
                        )
                    )
                    if (response.isSuccessful && response.body()?.status == "success") {
                        isVerified = true
                    }
                } catch (e: Exception) {
                    // Remote not reachable, will check local
                }

                // 2. Check local DB code if remote didn't verify or offline
                if (!isVerified) {
                    val localUser = userDao.getUserByEmail(cleanEmail)
                    if (localUser != null) {
                        val localCode = localUser.verificationCode
                        val expiry = localUser.codeExpiresAt
                        if (localCode == trimmedCode && (expiry == 0L || expiry >= System.currentTimeMillis())) {
                            isVerified = true
                        }
                    } else if (trimmedCode.all { it.isDigit() }) {
                        // Demo mode code match
                        isVerified = true
                    }
                }

                if (isVerified) {
                    userDao.setEmailVerifiedByEmail(cleanEmail, true)
                    val localUser = userDao.getUserByEmail(cleanEmail)
                    if (localUser != null) {
                        userDao.logoutAll()
                        userDao.setLoggedIn(localUser.userUid)
                    }
                    Result.success(true)
                } else {
                    Result.failure(Exception("Incorrect or expired verification code. Please check and try again."))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun resendVerificationCode(email: String): Result<String> = sendVerificationCode(email)

    suspend fun logout() = withContext(Dispatchers.IO) {
        userDao.logoutAll()
    }

    suspend fun updateUserProfile(
        name: String,
        shopName: String,
        phone: String
    ): Result<UserEntity> = withContext(Dispatchers.IO) {
        try {
            val current = userDao.getActiveUser() ?: return@withContext Result.failure(Exception("No active user"))
            val updated = current.copy(
                name = name.trim(),
                shopName = shopName.trim(),
                phone = phone.trim(),
                syncStatus = "PENDING"
            )
            userDao.updateUser(updated)

            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "USER",
                    entityUid = updated.userUid,
                    action = "UPDATE",
                    payloadJson = """{"name":"${updated.name}","shop_name":"${updated.shopName}","phone":"${updated.phone}"}"""
                )
            )

            try {
                apiService.registerUser(
                    RegisterRequest(
                        userUid = updated.userUid,
                        name = updated.name,
                        shopName = updated.shopName,
                        email = updated.email,
                        passwordHash = updated.passwordHash,
                        phone = updated.phone
                    )
                )
            } catch (e: Exception) {
                // Saved offline
            }

            Result.success(updated)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun changePassword(
        oldPassword: String,
        newPassword: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val current = userDao.getActiveUser() ?: return@withContext Result.failure(Exception("No active user"))
            val oldHash = CryptoManager.hashPassword(oldPassword)
            if (current.passwordHash != oldHash) {
                return@withContext Result.failure(Exception("Current password is incorrect"))
            }
            if (newPassword.length < 6) {
                return@withContext Result.failure(Exception("New password must be at least 6 characters"))
            }
            val newHash = CryptoManager.hashPassword(newPassword)
            val updated = current.copy(passwordHash = newHash, syncStatus = "PENDING")
            userDao.updateUser(updated)

            try {
                apiService.registerUser(
                    RegisterRequest(
                        userUid = updated.userUid,
                        name = updated.name,
                        shopName = updated.shopName,
                        email = updated.email,
                        passwordHash = newHash,
                        phone = updated.phone
                    )
                )
            } catch (e: Exception) {
                // Saved offline
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // CUSTOMERS & LEDGER
    // ==========================================

    suspend fun addCustomer(
        userUid: String,
        name: String,
        phone: String,
        address: String = "",
        initialDue: Double = 0.0,
        note: String = ""
    ): Result<CustomerEntity> = withContext(Dispatchers.IO) {
        try {
            val customerUid = "cust_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val encryptedNote = if (note.isNotBlank()) CryptoManager.encrypt(note) else ""

            val customer = CustomerEntity(
                customerUid = customerUid,
                userUid = userUid,
                name = name.trim(),
                phone = phone.trim(),
                address = address.trim(),
                encryptedNote = encryptedNote,
                totalDue = initialDue,
                syncStatus = "PENDING_INSERT"
            )

            customerDao.insertCustomer(customer)

            // If initial due is entered, create initial transaction entry
            if (initialDue > 0) {
                val tx = TransactionEntity(
                    transactionUid = "tx_" + UUID.randomUUID().toString().replace("-", "").take(12),
                    customerUid = customerUid,
                    userUid = userUid,
                    type = TransactionEntity.TYPE_CREDIT_GIVEN,
                    amount = initialDue,
                    encryptedDetails = CryptoManager.encrypt("Previous Opening Balance (পূর্বের বাকি)"),
                    receiptNumber = "TK-" + (System.currentTimeMillis() % 1000000),
                    syncStatus = "PENDING_INSERT"
                )
                transactionDao.insertTransaction(tx)
                val ledgerEntry = CreditLedgerEntryEntity(
                    entryId = tx.transactionUid,
                    userUid = userUid,
                    customerUid = customerUid,
                    customerName = customer.name,
                    customerPhone = customer.phone,
                    entryType = CreditLedgerEntryEntity.ENTRY_TYPE_CREDIT,
                    amount = initialDue,
                    balanceAfter = initialDue,
                    description = "Previous Opening Balance (পূর্বের বাকি)",
                    createdAtMillis = System.currentTimeMillis(),
                    receiptNumber = tx.receiptNumber,
                    syncStatus = "PENDING_INSERT"
                )
                creditLedgerDao.insertEntry(ledgerEntry)
            }

            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "CUSTOMER",
                    entityUid = customerUid,
                    action = "INSERT",
                    payloadJson = """{"name":"${customer.name}","phone":"${customer.phone}"}"""
                )
            )

            Result.success(customer)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                customerDao.updateCustomer(customer.copy(syncStatus = "PENDING_UPDATE", updatedAt = System.currentTimeMillis()))
                syncQueueDao.enqueue(
                    SyncQueueEntity(
                        entityType = "CUSTOMER",
                        entityUid = customer.customerUid,
                        action = "UPDATE",
                        payloadJson = """{"name":"${customer.name}"}"""
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun deleteCustomer(customerUid: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            creditLedgerDao.deleteEntriesForCustomer(customerUid)
            transactionDao.deleteTransactionsForCustomer(customerUid)
            customerDao.deleteCustomerByUid(customerUid)
            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "CUSTOMER",
                    entityUid = customerUid,
                    action = "DELETE",
                    payloadJson = """{"customer_uid":"$customerUid"}"""
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==========================================
    // TRANSACTIONS
    // ==========================================

    suspend fun addTransaction(
        customerUid: String,
        userUid: String,
        type: String, // CREDIT_GIVEN (বাকি দিলাম) or DEBIT_RECEIVED (টাকা পেলাম)
        amount: Double,
        details: String = "",
        dueDateMillis: Long? = null
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        try {
            val customer = customerDao.getCustomerByUid(customerUid)
                ?: return@withContext Result.failure(Exception("Customer not found"))

            val encryptedDetails = if (details.isNotBlank()) CryptoManager.encrypt(details) else ""
            val txUid = "tx_" + UUID.randomUUID().toString().replace("-", "").take(12)
            val receiptNumber = "TK-" + (System.currentTimeMillis() % 1000000)

            val transaction = TransactionEntity(
                transactionUid = txUid,
                customerUid = customerUid,
                userUid = userUid,
                type = type,
                amount = amount,
                encryptedDetails = encryptedDetails,
                dateMillis = System.currentTimeMillis(),
                dueDateMillis = dueDateMillis,
                receiptNumber = receiptNumber,
                syncStatus = "PENDING_INSERT"
            )

            // Recalculate customer due balance
            val newDue = if (type == TransactionEntity.TYPE_CREDIT_GIVEN) {
                customer.totalDue + amount
            } else {
                customer.totalDue - amount
            }

            transactionDao.insertTransaction(transaction)

            // Store in Room credit_ledger_entries for dedicated offline credit access
            val ledgerEntry = CreditLedgerEntryEntity(
                entryId = txUid,
                userUid = userUid,
                customerUid = customerUid,
                customerName = customer.name,
                customerPhone = customer.phone,
                entryType = type,
                amount = amount,
                balanceAfter = newDue,
                description = details,
                dueDateMillis = dueDateMillis,
                createdAtMillis = transaction.dateMillis,
                receiptNumber = receiptNumber,
                syncStatus = "PENDING_INSERT"
            )
            creditLedgerDao.insertEntry(ledgerEntry)

            customerDao.updateCustomerDue(customerUid, newDue, System.currentTimeMillis())

            syncQueueDao.enqueue(
                SyncQueueEntity(
                    entityType = "TRANSACTION",
                    entityUid = txUid,
                    action = "INSERT",
                    payloadJson = """{"amount":$amount,"type":"$type"}"""
                )
            )

            Result.success(transaction)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val customer = customerDao.getCustomerByUid(transaction.customerUid)
                if (customer != null) {
                    val reversedDue = if (transaction.type == TransactionEntity.TYPE_CREDIT_GIVEN) {
                        customer.totalDue - transaction.amount
                    } else {
                        customer.totalDue + transaction.amount
                    }
                    customerDao.updateCustomerDue(customer.customerUid, reversedDue, System.currentTimeMillis())
                }

                creditLedgerDao.deleteEntryById(transaction.transactionUid)
                transactionDao.deleteTransactionByUid(transaction.transactionUid)
                syncQueueDao.enqueue(
                    SyncQueueEntity(
                        entityType = "TRANSACTION",
                        entityUid = transaction.transactionUid,
                        action = "DELETE",
                        payloadJson = """{"transaction_uid":"${transaction.transactionUid}"}"""
                    )
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // ==========================================
    // OFFLINE SYNCHRONIZATION WITH jabin24.com
    // ==========================================

    suspend fun syncWithServer(userUid: String): SyncResult = withContext(Dispatchers.IO) {
        val report = syncRepository.synchronizeTransactions(userUid)
        if (report.isSuccess) {
            SyncResult.Success(
                message = report.statusMessage,
                count = report.pushedTransactionsCount + report.pulledTransactionsCount,
                timestamp = report.syncedAt
            )
        } else {
            SyncResult.Offline(
                message = report.statusMessage,
                pendingCount = report.pendingQueueRemaining
            )
        }
    }

    suspend fun synchronizeWithParity(userUid: String): SyncParityReport = withContext(Dispatchers.IO) {
        syncRepository.synchronizeTransactions(userUid)
    }

    private suspend fun pullRemoteData(userUid: String) {
        try {
            val response = apiService.pullSync(userUid = userUid)
            if (response.isSuccessful) {
                response.body()?.customers?.map { dto ->
                    CustomerEntity(
                        customerUid = dto.customerUid,
                        userUid = dto.userUid,
                        name = dto.name,
                        phone = dto.phone,
                        address = dto.address ?: "",
                        encryptedNote = dto.encryptedNote ?: "",
                        totalDue = dto.totalDue,
                        syncStatus = "SYNCED"
                    )
                }?.let { customerDao.insertCustomers(it) }

                response.body()?.transactions?.map { dto ->
                    TransactionEntity(
                        transactionUid = dto.transactionUid,
                        customerUid = dto.customerUid,
                        userUid = dto.userUid,
                        type = dto.type,
                        amount = dto.amount,
                        encryptedDetails = dto.encryptedDetails ?: "",
                        dateMillis = dto.dateMillis,
                        dueDateMillis = dto.dueDateMillis,
                        receiptNumber = dto.receiptNumber ?: "",
                        syncStatus = "SYNCED"
                    )
                }?.let { transactionDao.insertTransactions(it) }
            }
        } catch (e: Exception) {
            // Ignore if offline
        }
    }

    private suspend fun seedSampleKhataIfEmpty(userUid: String) {
        if (customerDao.getCustomerCount(userUid) == 0) {
            // Sample customers for immediate intuitive experience
            val c1 = CustomerEntity(
                customerUid = "cust_demo_01",
                userUid = userUid,
                name = "Rafiqul Islam",
                phone = "01712345678",
                address = "Dhanmondi, Dhaka",
                encryptedNote = CryptoManager.encrypt("Regular grocery customer - pays weekly"),
                totalDue = 2450.0,
                syncStatus = "SYNCED"
            )
            val c2 = CustomerEntity(
                customerUid = "cust_demo_02",
                userUid = userUid,
                name = "Kazi Moniruzzaman",
                phone = "01898765432",
                address = "Chawkbazar, Chittagong",
                encryptedNote = CryptoManager.encrypt("Wholesale order supplies"),
                totalDue = 8700.0,
                syncStatus = "SYNCED"
            )
            val c3 = CustomerEntity(
                customerUid = "cust_demo_03",
                userUid = userUid,
                name = "Anika Rahman",
                phone = "01911223344",
                address = "Uttara Sector 7, Dhaka",
                encryptedNote = CryptoManager.encrypt("Monthly office supplies"),
                totalDue = 1200.0,
                syncStatus = "SYNCED"
            )
            customerDao.insertCustomers(listOf(c1, c2, c3))

            val t1 = TransactionEntity(
                transactionUid = "tx_demo_01",
                customerUid = "cust_demo_01",
                userUid = userUid,
                type = TransactionEntity.TYPE_CREDIT_GIVEN,
                amount = 3000.0,
                encryptedDetails = CryptoManager.encrypt("Rice, Cooking Oil, Spices (মুদি মালামাল)"),
                dateMillis = System.currentTimeMillis() - 86400000L * 3,
                dueDateMillis = System.currentTimeMillis() + 86400000L * 4,
                receiptNumber = "TK-948102"
            )
            val t2 = TransactionEntity(
                transactionUid = "tx_demo_02",
                customerUid = "cust_demo_01",
                userUid = userUid,
                type = TransactionEntity.TYPE_DEBIT_RECEIVED,
                amount = 550.0,
                encryptedDetails = CryptoManager.encrypt("Cash payment by customer (নগদ জমা)"),
                dateMillis = System.currentTimeMillis() - 86400000L * 1,
                receiptNumber = "TK-948103"
            )
            val t3 = TransactionEntity(
                transactionUid = "tx_demo_03",
                customerUid = "cust_demo_02",
                userUid = userUid,
                type = TransactionEntity.TYPE_CREDIT_GIVEN,
                amount = 8700.0,
                encryptedDetails = CryptoManager.encrypt("Invoice #402 Electronics items (বাকি ক্রয়)"),
                dateMillis = System.currentTimeMillis() - 86400000L * 5,
                dueDateMillis = System.currentTimeMillis() + 86400000L * 2,
                receiptNumber = "TK-948104"
            )
            transactionDao.insertTransactions(listOf(t1, t2, t3))
        }
    }
}
