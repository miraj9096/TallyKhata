package www.jabin24.aitally.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import www.jabin24.aitally.data.local.entity.CreditLedgerEntryEntity

/**
 * Room Data Access Object (DAO) for local offline access to credit ledger entries.
 * Returns reactive Kotlin Flow streams for UI observation and suspend methods for CRUD operations.
 */
@Dao
interface CreditLedgerEntryDao {

    @Query("SELECT * FROM credit_ledger_entries WHERE userUid = :userUid ORDER BY createdAtMillis DESC")
    fun getAllEntriesFlow(userUid: String): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger_entries WHERE customerUid = :customerUid ORDER BY createdAtMillis DESC")
    fun getEntriesForCustomerFlow(customerUid: String): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger_entries WHERE userUid = :userUid AND entryType = 'CREDIT_GIVEN' ORDER BY createdAtMillis DESC")
    fun getCreditGivenEntriesFlow(userUid: String): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT * FROM credit_ledger_entries WHERE userUid = :userUid AND entryType = 'PAYMENT_RECEIVED' ORDER BY createdAtMillis DESC")
    fun getPaymentReceivedEntriesFlow(userUid: String): Flow<List<CreditLedgerEntryEntity>>

    @Query("SELECT SUM(amount) FROM credit_ledger_entries WHERE userUid = :userUid AND entryType = 'CREDIT_GIVEN'")
    fun getTotalCreditAmountFlow(userUid: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM credit_ledger_entries WHERE userUid = :userUid AND entryType = 'PAYMENT_RECEIVED'")
    fun getTotalPaymentAmountFlow(userUid: String): Flow<Double?>

    @Query("SELECT * FROM credit_ledger_entries WHERE entryId = :entryId LIMIT 1")
    suspend fun getEntryById(entryId: String): CreditLedgerEntryEntity?

    @Query("SELECT * FROM credit_ledger_entries WHERE userUid = :userUid ORDER BY createdAtMillis DESC LIMIT :limit")
    suspend fun getRecentEntries(userUid: String, limit: Int = 50): List<CreditLedgerEntryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: CreditLedgerEntryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<CreditLedgerEntryEntity>)

    @Update
    suspend fun updateEntry(entry: CreditLedgerEntryEntity)

    @Delete
    suspend fun deleteEntry(entry: CreditLedgerEntryEntity)

    @Query("DELETE FROM credit_ledger_entries WHERE entryId = :entryId")
    suspend fun deleteEntryById(entryId: String)

    @Query("DELETE FROM credit_ledger_entries WHERE customerUid = :customerUid")
    suspend fun deleteEntriesForCustomer(customerUid: String)

    @Query("DELETE FROM credit_ledger_entries WHERE userUid = :userUid")
    suspend fun deleteAllEntriesForUser(userUid: String)
}
