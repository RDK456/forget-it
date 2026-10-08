package app.forgetit.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SubscriptionEntity::class, PhotoEntity::class, ReminderLogEntity::class,
        LoanEntity::class, LoanPaymentEntity::class, LoanAdjustmentEntity::class,
    ],
    version = 2,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun photoDao(): PhotoDao
    abstract fun reminderLogDao(): ReminderLogDao
    abstract fun loanDao(): LoanDao
}
