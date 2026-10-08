package app.forgetit.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriptionEntity::class, PhotoEntity::class, ReminderLogEntity::class,
        LoanEntity::class, LoanPaymentEntity::class, LoanAdjustmentEntity::class,
        StockItemEntity::class, StockBatchEntity::class, StockLogEntity::class,
        TxnEntity::class,
    ],
    version = 4,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun photoDao(): PhotoDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun loanDao(): LoanDao
    abstract fun stockDao(): StockDao
    abstract fun txnDao(): TxnDao
}
