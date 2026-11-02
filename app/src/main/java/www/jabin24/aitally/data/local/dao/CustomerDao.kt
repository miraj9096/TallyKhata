package www.jabin24.aitally.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import www.jabin24.aitally.data.local.entity.CustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE userUid = :userUid ORDER BY updatedAt DESC")
    fun getCustomersFlow(userUid: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE userUid = :userUid ORDER BY updatedAt DESC")
    suspend fun getCustomers(userUid: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE customerUid = :customerUid LIMIT 1")
    fun getCustomerByUidFlow(customerUid: String): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE customerUid = :customerUid LIMIT 1")
    suspend fun getCustomerByUid(customerUid: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE userUid = :userUid AND (name LIKE '%' || :query || '%' OR phone LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchCustomersFlow(userUid: String, query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Query("UPDATE customers SET totalDue = :newDue, updatedAt = :updatedAt WHERE customerUid = :customerUid")
    suspend fun updateCustomerDue(customerUid: String, newDue: Double, updatedAt: Long)

    @Query("DELETE FROM customers WHERE customerUid = :customerUid")
    suspend fun deleteCustomerByUid(customerUid: String)

    @Query("SELECT COUNT(*) FROM customers WHERE userUid = :userUid")
    suspend fun getCustomerCount(userUid: String): Int

    @Query("SELECT * FROM customers WHERE userUid = :userUid AND totalDue > 0 ORDER BY totalDue DESC")
    suspend fun getCustomersWithDue(userUid: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE userUid = :userUid AND totalDue > 0 ORDER BY totalDue DESC")
    fun getCustomersWithDueFlow(userUid: String): Flow<List<CustomerEntity>>
}
