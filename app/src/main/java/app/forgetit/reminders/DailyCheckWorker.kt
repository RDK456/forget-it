package app.forgetit.reminders

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.forgetit.ForgetItApp
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/** Daily safety net: settle trials, clean photo files, rebuild alarms that the system may have dropped. */
class DailyCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as ForgetItApp).container
        c.subscriptions.settleTrials(LocalDate.now(c.clock))
        c.photos.sweepOrphans()
        app.forgetit.txn.AutoScan.scanDue(c, notify = true)
        if (c.settings.flow.first().autoScan) app.forgetit.gmail.GmailScanner.sync(c, notify = true)
        val s = c.settings.flow.first()
        if (s.autoUpdateCheck) {
            c.updater.check()
            (c.updater.state.value as? app.forgetit.update.UpdateState.Available)?.plan?.let { plan ->
                if (plan.version != s.notifiedUpdate) {
                    Notifications.showInfo(c.context, 7200, "Forget-it update available", "Version ${plan.version} is ready. Open Settings to install it.", Notifications.CH_UPDATE)
                    c.settings.setNotifiedUpdate(plan.version)
                }
            }
        }
        BudgetAlerts.check(c)
        c.reminders.sync()
        return Result.success()
    }
}
