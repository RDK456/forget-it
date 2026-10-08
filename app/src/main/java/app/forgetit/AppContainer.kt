package app.forgetit

import android.content.Context
import androidx.room.Room
import app.forgetit.data.AppDatabase
import app.forgetit.data.PhotoRepository
import app.forgetit.data.SettingsStore
import app.forgetit.data.SubscriptionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Clock

class AppContainer(val context: Context) {
    val clock: Clock = Clock.systemDefaultZone()
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase = Room.databaseBuilder(context, AppDatabase::class.java, "forgetit.db").build()
    val settings = SettingsStore(context)
    val photos = PhotoRepository(db.photoDao(), File(context.filesDir, "photos").also { it.mkdirs() })
    val subscriptions = SubscriptionRepository(db.subscriptionDao(), photos)
}
