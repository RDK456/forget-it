package app.forgetit

import android.content.Context
import androidx.room.Room
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.forgetit.data.AppDatabase
import app.forgetit.data.LoanRepository
import app.forgetit.data.MIGRATION_1_2
import app.forgetit.data.MIGRATION_2_3
import app.forgetit.data.BillRepository
import app.forgetit.data.MIGRATION_3_4
import app.forgetit.data.MIGRATION_4_5
import app.forgetit.data.TxnRepository
import app.forgetit.data.StockRepository
import app.forgetit.data.OwnerType
import app.forgetit.data.PhotoRepository
import app.forgetit.data.SettingsStore
import app.forgetit.data.SubscriptionRepository
import app.forgetit.reminders.DailyCheckWorker
import app.forgetit.reminders.Notifications
import app.forgetit.reminders.Reminders
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class AppContainer(val context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Incremented to fire the app-wide confetti overlay. */
    val confetti = kotlinx.coroutines.flow.MutableStateFlow(0)

    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "forgetit.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
    val settings = SettingsStore(context)
    val photos = PhotoRepository(db.photoDao(), File(context.filesDir, "photos").also { it.mkdirs() })
    val subscriptions = SubscriptionRepository(db.subscriptionDao(), photos)
    val loans = LoanRepository(db.loanDao(), photos)
    val stock = StockRepository(db.stockDao(), photos)
    val txns = TxnRepository(db.txnDao())
    val bills = BillRepository(db.billDao(), photos)
    val reminders by lazy { Reminders(this) }

    /** Called once from Application.onCreate: channels, housekeeping, the single sync collector, daily worker. */
    fun start() {
        Notifications.createChannels(context)
        appScope.launch {
            subscriptions.settleTrials(LocalDate.now(clock))
            photos.sweepOrphans()
            photos.deleteAll(OwnerType.SUBSCRIPTION, 0)
        }
        // Every data or reminder-time change rebuilds alarms through this one path.
        appScope.launch {
            combine(
                subscriptions.observeAll(), loans.observeLoans(), loans.observePayments(), loans.observeAdjustments(),
                settings.flow.map { Triple(it.reminderMinuteOfDay, it.weeklyDigest, it.paydayDay) }.distinctUntilChanged(),
            ) { _, _, _, _, _ -> }
                .collect { reminders.sync() }
        }
        appScope.launch {
            combine(
                bills.observeBills(), bills.observeEntries(), stock.observeItems(), stock.observeBatches(), stock.observeLogs(),
            ) { _, _, _, _, _ -> }
                .collect { reminders.sync() }
        }
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<DailyCheckWorker>(1, TimeUnit.DAYS).build(),
        )
    }
}
