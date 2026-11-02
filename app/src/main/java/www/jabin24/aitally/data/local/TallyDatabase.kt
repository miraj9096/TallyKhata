package www.jabin24.aitally.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import www.jabin24.aitally.data.local.dao.CreditLedgerEntryDao
import www.jabin24.aitally.data.local.dao.CustomerDao
import www.jabin24.aitally.data.local.dao.SyncQueueDao
import www.jabin24.aitally.data.local.dao.TransactionDao
import www.jabin24.aitally.data.local.dao.UserDao
import www.jabin24.aitally.data.local.entity.CreditLedgerEntryEntity
import www.jabin24.aitally.data.local.entity.CustomerEntity
import www.jabin24.aitally.data.local.entity.SyncQueueEntity
import www.jabin24.aitally.data.local.entity.TransactionEntity
import www.jabin24.aitally.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        CustomerEntity::class,
        TransactionEntity::class,
        SyncQueueEntity::class,
        CreditLedgerEntryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class TallyDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun transactionDao(): TransactionDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun creditLedgerEntryDao(): CreditLedgerEntryDao

    companion object {
        @Volatile
        private var INSTANCE: TallyDatabase? = null

        fun getInstance(context: Context): TallyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TallyDatabase::class.java,
                    "tallykhata.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
