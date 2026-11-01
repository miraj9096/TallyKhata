package www.jabin24.aitally.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import www.jabin24.aitally.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE userUid = :userUid ORDER BY dateMillis DESC")
    fun getAllTransactionsFlow(userUid: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerUid = :customerUid ORDER BY dateMillis DESC")
    fun getTransactionsForCustomerFlow(customerUid: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE customerUid = :customerUid ORDER BY dateMillis DESC")
    suspend fun getTransactionsForCustomer(customerUid: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE transactionUid = :transactionUid LIMIT 1")
    suspend fun getTransactionByUid(transactionUid: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE userUid = :userUid ORDER BY dateMillis DESC")
    suspend fun getAllTransactions(userUid: String): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE transactionUid = :transactionUid")
    suspend fun deleteTransactionByUid(transactionUid: String)

    @Query("DELETE FROM transactions WHERE customerUid = :customerUid")
    suspend fun deleteTransactionsForCustomer(customerUid: String)
}
