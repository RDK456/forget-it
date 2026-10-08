package app.forgetit.txn

import app.forgetit.AppContainer
import app.forgetit.domain.SmsParser
import app.forgetit.reminders.Notifications
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** One entry point for every automatic read: live SMS, email notifications, and the periodic inbox catch-up. */
object AutoScan {
    /** Parses one message. Returns true when it was a new payment. Does nothing when auto-scan is off. */
    suspend fun ingest(c: AppContainer, text: String, source: String, fallbackMerchant: String? = null): Boolean {
        val s = c.settings.flow.first()
        if (!s.autoScan) return false
        var p = SmsParser.parse(text, LocalDate.now(c.clock), s.defaultCurrency) ?: return false
        if (p.merchant == null && !fallbackMerchant.isNullOrBlank()) p = p.copy(merchant = fallbackMerchant.trim().take(40))
        return c.txns.addIfNew(p, source, text)
    }

    /** Reads the SMS inbox since the last scan (90 days the first time). Returns new payments, or -1 without permission. */
    suspend fun scanDue(c: AppContainer, force: Boolean = false, notify: Boolean = false): Int {
        val s = c.settings.flow.first()
        if (!force && !s.autoScan) return 0
        if (!SmsScanner.hasPermission(c.context)) return -1
        val today = LocalDate.now(c.clock)
        val days = if (s.lastScanDay == 0L) 90L else (today.toEpochDay() - s.lastScanDay + 1).coerceIn(2, 90)
        val n = SmsScanner.scanInbox(c.context, c.txns, today.minusDays(days), s.defaultCurrency, ZoneId.systemDefault())
        if (n >= 0) c.settings.setLastScanDay(today.toEpochDay())
        if (n > 0 && notify) Notifications.showFound(c.context, n)
        return n
    }
}
