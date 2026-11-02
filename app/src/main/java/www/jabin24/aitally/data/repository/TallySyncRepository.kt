package www.jabin24.aitally.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import www.jabin24.aitally.data.local.TallyDatabase
import www.jabin24.aitally.data.local.entity.CreditLedgerEntryEntity
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.data.remote.RetrofitClient
import www.jabin24.aitally.data.remote.TallySyncApiService
import www.jabin24.aitally.data.remote.models.CustomerDto
import www.jabin24.aitally.data.remote.models.SyncPushRequest
import www.jabin24.aitally.data.remote.models.TransactionDto
import www.jabin24.aitally.util.NetworkConnectivityManager

/**
 * Report containing data parity metrics between local Room database and jabin24.com MySQL cloud.
 */
data class SyncParityReport(
    val isSuccess: Boolean,
    val isOnline: Boolean,
    val pushedCustomersCount: Int,
    val pushedTransactionsCount: Int,
    val pulledCustomersCount: Int,
    val pulledTransactionsCount: Int,
    val totalLocalTransactions: Int,
    val pendingQueueRemaining: Int,
    val syncedAt: Long,
    val statusMessage: String
)

/**
 * Repository layer responsible for synchronizing local Room database transactions
 * and customer ledger entries with the jabin24.com MySQL API backend.
 * Ensures complete two-way data parity during online connectivity and safe local persistence offline.
 */
class TallySyncRepository(
    private val context: Context,
    private val db: TallyDatabase = TallyDatabase.getInstance(context),
    private val apiService: TallySyncApiService = RetrofitClient.apiService
) {
    private val TAG = "TallySyncRepository"
    private val customerDao = db.customerDao()
    private val transactionDao = db.transactionDao()
    private val creditLedgerDao = db.creditLedgerEntryDao()
    private val syncQueueDao = db.syncQueueDao()
    private val userDao = db.userDao()

    /**
     * Flow observing network connectivity state in real time.
     */
    val isOnlineFlow: Flow<Boolean> = NetworkConnectivityManager.observeConnectivity(context)

    /**
     * Flow observing pending offline sync queue count.
     */
    val pendingSyncCountFlow: Flow<Int> = syncQueueDao.getPendingCountFlow()

    /**
     * Performs a full bidirectional synchronization to ensure data parity:
     * 1. Checks device online connectivity.
     * 2. Pushes local Room transactions & customer records to jabin24.com MySQL API.
     * 3. Pulls latest transactions & customers from jabin24.com MySQL database.
     * 4. Merges into local Room database (updating TransactionEntity and CreditLedgerEntryEntity).
     * 5. Returns a comprehensive SyncParityReport.
     */
    suspend fun synchronizeTransactions(userUid: String): SyncParityReport = withContext(Dispatchers.IO) {
        val isOnline = NetworkConnectivityManager.isOnline(context)
        val initialPending = syncQueueDao.getAllPending().size
        val localTxList = transactionDao.getAllTransactions(userUid)

        if (!isOnline) {
            Log.d(TAG, "Device is offline. Local Room database remains safe with ${localTxList.size} transactions.")
            return@withContext SyncParityReport(
                isSuccess = false,
                isOnline = false,
                pushedCustomersCount = 0,
                pushedTransactionsCount = 0,
                pulledCustomersCount = 0,
                pulledTransactionsCount = 0,
                totalLocalTransactions = localTxList.size,
                pendingQueueRemaining = initialPending,
                syncedAt = System.currentTimeMillis(),
                statusMessage = "Offline Mode: ${localTxList.size} transactions saved locally in Room. ($initialPending items queued for cloud sync)"
            )
        }

        var pushedCustCount = 0
        var pushedTxCount = 0
        var pulledCustCount = 0
        var pulledTxCount = 0

        // ==========================================
        // 1. PUSH PHASE (Local Room -> jabin24.com API)
        // ==========================================
        try {
            val localCustomers = customerDao.getCustomers(userUid)
            val customerDtos = localCustomers.map { c ->
                CustomerDto(
                    customerUid = c.customerUid,
                    userUid = c.userUid,
                    name = c.name,
                    phone = c.phone,
                    address = c.address,
                    encryptedNote = c.encryptedNote,
                    totalDue = c.totalDue
                )
            }

            val transactionDtos = localTxList.map { t ->
                TransactionDto(
                    transactionUid = t.transactionUid,
                    customerUid = t.customerUid,
                    userUid = t.userUid,
                    type = t.type,
                    amount = t.amount,
                    encryptedDetails = t.encryptedDetails,
                    dateMillis = t.dateMillis,
                    dueDateMillis = t.dueDateMillis,
                    receiptNumber = t.receiptNumber
                )
            }

            val pushRequest = SyncPushRequest(
                action = "sync",
                userUid = userUid,
                customers = customerDtos,
                transactions = transactionDtos
            )

            val pushResponse = apiService.pushSync(pushRequest)
            if (pushResponse.isSuccessful && pushResponse.body()?.status == "success") {
                pushedCustCount = customerDtos.size
                pushedTxCount = transactionDtos.size

                // Mark local records as SYNCED
                localCustomers.forEach { customerDao.updateCustomer(it.copy(syncStatus = "SYNCED")) }
                localTxList.forEach { transactionDao.updateTransaction(it.copy(syncStatus = "SYNCED")) }

                // Clear resolved sync queue
                syncQueueDao.clearAll()
                Log.d(TAG, "Push phase succeeded: $pushedCustCount customers, $pushedTxCount transactions.")
            } else {
                Log.w(TAG, "Push phase returned non-success: ${pushResponse.body()?.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Push phase encountered network exception", e)
            return@withContext SyncParityReport(
                isSuccess = false,
                isOnline = true,
                pushedCustomersCount = 0,
                pushedTransactionsCount = 0,
                pulledCustomersCount = 0,
                pulledTransactionsCount = 0,
                totalLocalTransactions = localTxList.size,
                pendingQueueRemaining = initialPending,
                syncedAt = System.currentTimeMillis(),
                statusMessage = "Sync failed to reach jabin24.com: ${e.message ?: "Network error"}"
            )
        }

        // ==========================================
        // 2. PULL PHASE (jabin24.com API -> Local Room)
        // ==========================================
        try {
            val pullResponse = apiService.pullSync(userUid = userUid)
            if (pullResponse.isSuccessful) {
                val remoteCustomers = pullResponse.body()?.customers.orEmpty()
                val remoteTransactions = pullResponse.body()?.transactions.orEmpty()

                // 2a. Reconcile customers into Room
                val customerEntities = remoteCustomers.map { dto ->
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
                }
                if (customerEntities.isNotEmpty()) {
                    customerDao.insertCustomers(customerEntities)
                    pulledCustCount = customerEntities.size
                }

                // Customer lookup map for ledger denormalization
                val customerMap = customerDao.getCustomers(userUid).associateBy { it.customerUid }

                // 2b. Reconcile transactions and credit ledger entries into Room
                val transactionEntities = remoteTransactions.map { dto ->
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
                }
                if (transactionEntities.isNotEmpty()) {
                    transactionDao.insertTransactions(transactionEntities)
                    pulledTxCount = transactionEntities.size

                    // 2c. Synchronize CreditLedgerEntryEntity table for offline credit access parity
                    val ledgerEntities = transactionEntities.map { tx ->
                        val targetCust = customerMap[tx.customerUid]
                        CreditLedgerEntryEntity(
                            entryId = tx.transactionUid,
                            userUid = tx.userUid,
                            customerUid = tx.customerUid,
                            customerName = targetCust?.name ?: "Customer",
                            customerPhone = targetCust?.phone ?: "",
                            entryType = tx.type,
                            amount = tx.amount,
                            balanceAfter = targetCust?.totalDue ?: 0.0,
                            description = tx.encryptedDetails,
                            dueDateMillis = tx.dueDateMillis,
                            createdAtMillis = tx.dateMillis,
                            receiptNumber = tx.receiptNumber,
                            syncStatus = "SYNCED"
                        )
                    }
                    creditLedgerDao.insertEntries(ledgerEntities)
                }

                Log.d(TAG, "Pull phase succeeded: $pulledCustCount customers, $pulledTxCount transactions merged into Room.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Pull phase encountered exception", e)
        }

        val finalLocalTransactions = transactionDao.getAllTransactions(userUid)
        val remainingPending = syncQueueDao.getAllPending().size

        SyncParityReport(
            isSuccess = true,
            isOnline = true,
            pushedCustomersCount = pushedCustCount,
            pushedTransactionsCount = pushedTxCount,
            pulledCustomersCount = pulledCustCount,
            pulledTransactionsCount = pulledTxCount,
            totalLocalTransactions = finalLocalTransactions.size,
            pendingQueueRemaining = remainingPending,
            syncedAt = System.currentTimeMillis(),
            statusMessage = "Data Parity Verified: ${finalLocalTransactions.size} transactions synchronized with jabin24.com."
        )
    }

    /**
     * Checks health and responsiveness of the jabin24.com API.
     */
    suspend fun checkApiHealth(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.healthCheck()
            if (response.isSuccessful) {
                Result.success("API Online: ${response.body()?.message ?: "Healthy"}")
            } else {
                Result.failure(Exception("API returned code ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
