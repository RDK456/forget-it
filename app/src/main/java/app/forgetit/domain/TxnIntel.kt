package app.forgetit.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

private fun squash(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

/** Turns the many spellings of a merchant on a bank message into one name ("NETFLIX.COM" and "Netflix India" become "Netflix"). */
object Merchants {
    private val known = PRESETS.filter { it.accountUrl != null }
        .flatMap { p -> listOf(squash(p.key), squash(p.name)).filter { it.length >= 4 }.map { it to p.name } }

    fun canonical(raw: String): String {
        val s = squash(raw)
        return known.firstOrNull { s.contains(it.first) }?.second ?: raw
    }
}

/** A tracked subscription whose latest charge on the phone is for a different amount than the one saved. */
data class PriceChange(
    val subscriptionId: Long,
    val name: String,
    val oldMinor: Long,
    val newMinor: Long,
    val currency: String,
    val seenOn: LocalDate,
    val txnId: Long,
)

object PriceWatch {
    const val WINDOW_DAYS = 45L

    fun changes(txns: List<Txn>, subs: List<Subscription>, today: LocalDate): List<PriceChange> =
        subs.filter { it.active && !it.isTrial }.mapNotNull { s ->
            val key = squash(s.name)
            if (key.length < 3) return@mapNotNull null
            val latest = txns.filter {
                it.direction == TxnDirection.DEBIT && it.status != "IGNORED" && it.currency == s.currency &&
                    !it.date.isBefore(s.startDate) && ChronoUnit.DAYS.between(it.date, today) in 0..WINDOW_DAYS &&
                    it.merchant != null && squash(it.merchant).let { m -> m.contains(key) || (key.contains(m) && m.length >= 3) }
            }.maxByOrNull { it.date } ?: return@mapNotNull null
            if (abs(latest.amountMinor - s.amountMinor) <= maxOf(1L, s.amountMinor / 100)) return@mapNotNull null
            PriceChange(s.id, s.name, s.amountMinor, latest.amountMinor, s.currency, latest.date, latest.id)
        }
}

/** What a lump-sum prepayment today would save on a loan. */
data class PrepayResult(val interestSavedMinor: Long, val installmentsSaved: Int, val newEmiMinor: Long, val newPayoff: LocalDate?)

object PrepayWhatIf {
    fun run(
        loan: Loan, adjustments: List<LoanAdjustment>, payments: List<LoanPayment>, today: LocalDate,
        amountMinor: Long, kind: AdjustmentKind,
    ): PrepayResult {
        val now = Amortization.build(loan, adjustments, payments, today)
        val extra = LoanAdjustment(id = Long.MAX_VALUE, loanId = loan.id, date = today, kind = kind, amountMinor = amountMinor)
        val after = Amortization.build(loan, adjustments + extra, payments, today)
        return PrepayResult(
            interestSavedMinor = (now.interestRemainingMinor - after.interestRemainingMinor).coerceAtLeast(0),
            installmentsSaved = (now.rows.size - after.rows.size).coerceAtLeast(0),
            newEmiMinor = after.emiMinor,
            newPayoff = after.payoffDate,
        )
    }
}
