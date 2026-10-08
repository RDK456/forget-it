package app.forgetit.txn

import app.forgetit.AppContainer
import app.forgetit.domain.SmsParser
import app.forgetit.domain.TxnFilter
import app.forgetit.domain.TxnMatching
import app.forgetit.reminders.Notifications
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** One entry point for every automatic read: live SMS, email notifications, and the periodic inbox catch-up. */
object AutoScan {
    /** Parses one message. Returns true when it was a new payment. Does nothing when auto-scan is off. */
    suspend fun ingest(c: AppContainer, text: String, source: String, fallbackMerchant: String? = null, sender: String = ""): Boolean {
        val s = c.settings.flow.first()
        if (!s.autoScan || sender in s.mutedSenders) return false
        val today = LocalDate.now(c.clock)
        var added = false
        (if (TxnFilter.accept(text, sender)) SmsParser.parse(text, today, s.defaultCurrency) else null)?.let { parsed ->
            val p = if (parsed.merchant == null && !fallbackMerchant.isNullOrBlank()) parsed.copy(merchant = fallbackMerchant.trim().take(40)) else parsed
            added = c.txns.addIfNew(p, source, text, sender)
        }
        // Messages that are not a plain payment (an EMI reminder, a subscription receipt) can still describe a loan or a subscription.
        AutoTracker.onNewMessage(c, text, today, source, sender)
        if (added) { markMatchedEmis(c); app.forgetit.reminders.BudgetAlerts.check(c) }
        return added
    }

    /** Removes entries read from messages that are ads, forwards, reminders or personal numbers. Typed-in and edited entries stay. Returns how many went. */
    suspend fun cleanUp(c: AppContainer): Int {
        val junk = c.txns.observeAll().first().filter {
            it.source in READ_SOURCES && it.category.isBlank() && it.note.isBlank() && !TxnFilter.accept(it.snippet, it.sender, strict = false)
        }
        junk.forEach { c.txns.delete(it.id) }
        return junk.size
    }

    private val READ_SOURCES = setOf("SMS", "EMAIL", "GMAIL")

    /** Marks an EMI paid when a debit of the right amount landed near its due date. Returns how many were marked. */
    suspend fun markMatchedEmis(c: AppContainer): Int {
        if (!c.settings.flow.first().autoMarkEmi) return 0
        val loans = c.loans.getLoans()
        if (loans.isEmpty()) return 0
        val txns = c.txns.observeAll().first()
        val matches = TxnMatching.emiMatches(txns, loans, c.loans.getAdjustments(), c.loans.getPayments(), LocalDate.now(c.clock))
            .distinctBy { it.txnId }
        for (m in matches) {
            val t = txns.first { it.id == m.txnId }
            c.loans.markPaid(m.loanId, m.installmentNo, t.date, t.amountMinor)
            Notifications.showInfo(c.context, 7100 + m.loanId.toInt(), "EMI marked paid", "Installment ${m.installmentNo} of ${m.loanName} was marked paid from your payment message.")
        }
        return matches.size
    }

    /** Reads the SMS inbox since the last scan (90 days the first time). Returns new payments, or -1 without permission. */
    suspend fun scanDue(c: AppContainer, force: Boolean = false, notify: Boolean = false): Int {
        val s = c.settings.flow.first()
        if (!force && !s.autoScan) return 0
        if (!SmsScanner.hasPermission(c.context)) return -1
        val today = LocalDate.now(c.clock)
        val days = if (s.lastScanDay == 0L) 90L else (today.toEpochDay() - s.lastScanDay + 1).coerceIn(2, 90)
        val n = SmsScanner.scanInbox(c.context, c.txns, today.minusDays(days), s.defaultCurrency, ZoneId.systemDefault(), s.mutedSenders) { body, day -> AutoTracker.onNewMessage(c, body, day, "SMS") }
        if (n >= 0) c.settings.setLastScanDay(today.toEpochDay())
        if (n > 0) { markMatchedEmis(c); app.forgetit.reminders.BudgetAlerts.check(c) }
        if (n > 0 && notify) Notifications.showFound(c.context, n)
        return n
    }
}
