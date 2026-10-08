package app.forgetit.txn

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.forgetit.AppContainer
import app.forgetit.ForgetItApp
import app.forgetit.MainActivity
import app.forgetit.R
import app.forgetit.data.SaveResult
import app.forgetit.domain.AutoTrack
import app.forgetit.domain.LoanGuess
import app.forgetit.domain.Money
import app.forgetit.domain.AutoSub
import app.forgetit.domain.ParsedTxn
import app.forgetit.domain.SubGuess
import app.forgetit.domain.TxnDirection
import app.forgetit.domain.PRESETS
import app.forgetit.domain.RecurringSuggestion
import app.forgetit.domain.Subscription
import app.forgetit.reminders.Notifications
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/**
 * Adds subscriptions and loans on its own from the payments the phone has seen, then says what it did with an Undo button.
 * Undoing removes the record and remembers the choice, so the same thing is never added again.
 */
object AutoTracker {
    private val lock = Mutex()

    /** After each new payment message: an EMI message that states amount and tenure becomes a loan; repeats become subscriptions or loans. */
    suspend fun onNewMessage(c: AppContainer, text: String, receivedOn: LocalDate = LocalDate.now(c.clock), source: String = "SMS", sender: String = "") = lock.withLock {
        if (!looksFinancial(text)) return@withLock
        val s = c.settings.flow.first()
        if (s.autoCreateLoans) {
            AutoTrack.loanGuessFromMessage(text, s.defaultCurrency, receivedOn, c.loans.getLoans(), s.autoDismissed)?.let { createLoan(c, it) }
        }
        if (s.autoAddSubs) {
            val tracked = c.subscriptions.getAll().map { it.name } + c.loans.getLoans().map { it.name }
            AutoSub.fromMessage(text, s.defaultCurrency, receivedOn, tracked, s.autoDismissed)?.let { addFromConfirmation(c, it, text, source, sender) }
        }
        trackHistory(c)
    }

    /** A cheap check before the heavier reading: a money amount and a word that belongs to a subscription, receipt or loan. */
    private val MONEY_AND_TOPIC = Regex(
        "(\\d[\\d,]*[.]?\\d*)\\s*(rs|inr|usd|eur|gbp|aed)\\b|(rs[.]?|inr|usd|eur|gbp|aed|[$\u20B9\u20AC\u00A3])\\s*\\d",
        RegexOption.IGNORE_CASE,
    )
    private val TOPIC = Regex("\\b(emi|loan|subscription|membership|receipt|invoice|renew|renewed|renews|billed|plan)\\b", RegexOption.IGNORE_CASE)
    private fun looksFinancial(text: String) = MONEY_AND_TOPIC.containsMatchIn(text) && TOPIC.containsMatchIn(text)

    /** After a batch of payments (inbox catch-up, Gmail, a payment you typed in, pull to refresh). */
    suspend fun onNewPayments(c: AppContainer) = lock.withLock { trackHistory(c) }

    private suspend fun trackHistory(c: AppContainer) {
        val s = c.settings.flow.first()
        if (!s.autoAddSubs && !s.autoCreateLoans) return
        val txns = c.txns.observeAll().first()
        val rules = c.txns.observeRules().first()
        if (s.autoAddSubs) {
            val tracked = c.subscriptions.getAll().map { it.name } + c.loans.getLoans().map { it.name }
            for (g in AutoTrack.subscriptionGuesses(txns, tracked, s.autoDismissed, rules)) addSubscription(c, g)
        }
        if (s.autoCreateLoans) {
            for (g in AutoTrack.loanGuessesFromHistory(txns, c.loans.getLoans(), s.autoDismissed, rules)) createLoan(c, g)
        }
    }

    private suspend fun addSubscription(c: AppContainer, g: RecurringSuggestion) {
        val sub = Subscription(
            name = g.merchant, amountMinor = g.amountMinor, currency = g.currency, cycle = g.cycle, startDate = g.lastDate,
            category = PRESETS.firstOrNull { it.name.equals(g.merchant, ignoreCase = true) }?.category ?: "Other",
            notes = "Added automatically from your payments.",
        )
        val saved = c.subscriptions.save(sub) as? SaveResult.Saved ?: return
        notify(c.context, 7400 + (saved.id.toInt() and 0xFFF), "Subscription added: ${g.merchant}",
            "${Money.format(g.amountMinor, g.currency)} ${g.cycle.name.lowercase()}, found in your payments.", "sub", saved.id, AutoTrack.subKey(g.merchant))
    }

    private suspend fun addFromConfirmation(c: AppContainer, g: SubGuess, text: String, source: String, sender: String) {
        val sub = Subscription(
            name = g.name, amountMinor = g.amountMinor, currency = g.currency, cycle = g.cycle, startDate = g.startDate, category = g.category,
            notes = g.notes, cancelUrl = g.cancelUrl, presetKey = g.presetKey, paymentMethod = g.paymentMethod,
        )
        val saved = c.subscriptions.save(sub) as? SaveResult.Saved ?: return
        // The payment itself goes into the Money ledger, unless the same payment was already stored from this message.
        val already = c.txns.observeAll().first().any { it.direction == TxnDirection.DEBIT && it.amountMinor == g.amountMinor && it.currency == g.currency && it.date == g.paidOn }
        if (!already) c.txns.addIfNew(ParsedTxn(TxnDirection.DEBIT, g.amountMinor, g.currency, g.name, null, g.paidOn), source, text, sender)
        notify(c.context, 7400 + (saved.id.toInt() and 0xFFF), "Subscription added: ${g.name}",
            "${Money.format(g.amountMinor, g.currency)} ${g.cycle.name.lowercase()}, paid ${g.paidOn}, next ${g.nextDate}.", "sub", saved.id, AutoTrack.subKey(g.name))
    }

    private suspend fun createLoan(c: AppContainer, g: LoanGuess) {
        val saved = c.loans.save(AutoTrack.toLoan(g)) as? SaveResult.Saved ?: return
        // Instalments already seen in the payments are marked paid, so the schedule starts from today's real position.
        g.paidDates.forEachIndexed { i, day -> c.loans.markPaid(saved.id, i + 1, day, g.emiMinor) }
        notify(c.context, 7400 + (saved.id.toInt() and 0xFFF) + 0x1000, "Loan added: ${g.name}",
            "EMI ${Money.format(g.emiMinor, g.currency)} a month. The amount, rate and length are estimates, so check the terms.", "loan", saved.id, AutoTrack.loanKey(g.name))
    }

    @SuppressLint("MissingPermission")
    private fun notify(context: Context, id: Int, title: String, text: String, kind: String, entityId: Long, key: String) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context, 1, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val undo = PendingIntent.getBroadcast(
            context, id,
            Intent(context, UndoReceiver::class.java).setAction("app.forgetit.UNDO_AUTO").putExtra("kind", kind).putExtra("id", entityId).putExtra("key", key).putExtra("nid", id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, Notifications.CH_FOUND).setSmallIcon(R.drawable.ic_stat_forgetit)
            .setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open).setAutoCancel(true).addAction(0, "Undo", undo).build()
        try { nm.notify(id, n) } catch (_: SecurityException) {}
    }

    /** Undo button on the notification: deletes what was added and stops it coming back. */
    class UndoReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val c = (context.applicationContext as ForgetItApp).container
            val kind = intent.getStringExtra("kind") ?: return
            val id = intent.getLongExtra("id", 0)
            val key = intent.getStringExtra("key").orEmpty()
            val pending = goAsync()
            c.appScope.launch {
                try {
                    if (kind == "sub") c.subscriptions.delete(id) else c.loans.delete(id)
                    if (key.isNotEmpty()) c.settings.setAutoDismissed(c.settings.flow.first().autoDismissed + key)
                    NotificationManagerCompat.from(context).cancel(intent.getIntExtra("nid", 0))
                } finally { pending.finish() }
            }
        }
    }
}
