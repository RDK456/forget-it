package app.forgetit.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.forgetit.ForgetItApp
import java.time.LocalDate

/** Daily safety net: settle trials, clean photo files, rebuild alarms that the system may have dropped. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ForgetItApp).container
        c.subscriptions.settleTrials(LocalDate.now(c.clock))
        c.photos.sweepOrphans()
        c.reminders.sync()
        return Result.success()
    }
}
