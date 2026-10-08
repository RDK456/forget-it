package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Loan schedule engine. Pure and deterministic; see the spec section on loans for every rule. */
object Amortization {
    const val MAX_ROWS = 1200
    private val MC = Cost.MC
    private const val MAX_PRINCIPAL_MINOR = 100_000_000_000L

    private fun monthlyRate(loan: Loan): BigDecimal = loan.annualRatePercent.divide(BigDecimal(1200), MC)

    private fun emiFor(balance: Long, n: Int, r: BigDecimal): Long {
        if (n <= 0) return balance
        val b = BigDecimal(balance)
        if (r.signum() == 0) return Cost.round(b.divide(BigDecimal(n), MC))
        val pow = BigDecimal.ONE.add(r).pow(n, MC)
        return Cost.round(b.multiply(r).multiply(pow).divide(pow.subtract(BigDecimal.ONE), MC))
    }

    private fun interestOn(balance: Long, r: BigDecimal): Long = Cost.round(BigDecimal(balance).multiply(r))

    fun baseEmi(loan: Loan): Long = loan.emiOverrideMinor ?: emiFor(loan.principalMinor, loan.tenureMonths, monthlyRate(loan))

    fun validate(loan: Loan): List<ValidationError> = buildList {
        if (loan.name.isBlank()) add(ValidationError("name", "Enter a name"))
        else if (loan.name.length > 60) add(ValidationError("name", "Name is too long (max 60)"))
        if (loan.principalMinor !in 1..MAX_PRINCIPAL_MINOR) add(ValidationError("principal", "Amount is out of range"))
        if (!Money.isValidCurrency(loan.currency)) add(ValidationError("currency", "Unknown currency code"))
        if (loan.annualRatePercent.signum() < 0 || loan.annualRatePercent > BigDecimal(100)) {
            add(ValidationError("rate", "Interest rate must be 0 to 100"))
        }
        if (loan.tenureMonths !in 1..600) add(ValidationError("tenure", "Tenure must be 1 to 600 months"))
        if (loan.emiOverrideMinor != null && loan.emiOverrideMinor <= 0) add(ValidationError("emi", "EMI must be above zero"))
        if (loan.remindDaysBefore !in 0..30) add(ValidationError("remindDaysBefore", "Choose 0 to 30 days"))
        if (loan.extraRemindDays.any { it !in 0..30 }) add(ValidationError("extraRemind", "Extra reminders must be 0 to 30 days"))
        if (isEmpty() && loan.tenureMonths > 1 && baseEmi(loan) <= interestOn(loan.principalMinor, monthlyRate(loan))) {
            add(ValidationError("emi", "EMI is too low to ever repay the loan"))
        }
    }

    fun build(loan: Loan, adjustments: List<LoanAdjustment>, payments: List<LoanPayment>, today: LocalDate): LoanSummary {
        val r = monthlyRate(loan)
        val sorted = adjustments.sortedWith(compareBy({ it.date }, { it.id }))
        val paid = payments.map { it.installmentNo }.toSet()
        // A fixed last installment only exists while nothing can change the length of the loan.
        var fixedEnd: Int? = if (loan.emiOverrideMinor == null) loan.tenureMonths else null
        var emi = baseEmi(loan)
        var balance = loan.principalMinor
        var next = 0
        val rows = mutableListOf<ScheduleRow>()

        var k = 1
        while (balance > 0 && k <= MAX_ROWS) {
            val due = loan.firstEmiDate.plusMonths((k - 1).toLong())
            while (next < sorted.size && sorted[next].date.isBefore(due)) {
                val a = sorted[next++]
                when (a.kind) {
                    AdjustmentKind.BALANCE_RESET -> { balance = a.amountMinor; fixedEnd = null }
                    AdjustmentKind.PREPAYMENT_REDUCE_TENURE -> balance = (balance - a.amountMinor).coerceAtLeast(0)
                    AdjustmentKind.PREPAYMENT_REDUCE_EMI -> {
                        balance = (balance - a.amountMinor).coerceAtLeast(0)
                        if (balance > 0) emi = emiFor(balance, ((fixedEnd ?: loan.tenureMonths) - (k - 1)).coerceAtLeast(1), r)
                    }
                }
            }
            if (balance <= 0) break

            val interest = interestOn(balance, r)
            val isLast = balance + interest <= emi || (fixedEnd != null && k >= fixedEnd)
            val payment = if (isLast) balance + interest else emi
            val principal = payment - interest
            val after = balance - principal
            val status = when {
                k in paid -> RowStatus.PAID
                due.isBefore(today) -> RowStatus.OVERDUE
                due == today -> RowStatus.DUE
                else -> RowStatus.UPCOMING
            }
            rows += ScheduleRow(k, due, payment, interest, principal, balance, after, status)
            balance = after
            k++
        }

        val unpaid = rows.filter { it.status != RowStatus.PAID }
        val highestPaid = rows.lastOrNull { it.status == RowStatus.PAID }
        return LoanSummary(
            rows = rows,
            emiMinor = unpaid.firstOrNull()?.paymentMinor ?: 0,
            outstandingMinor = highestPaid?.balanceAfterMinor ?: rows.firstOrNull()?.balanceBeforeMinor ?: loan.principalMinor,
            nextDue = unpaid.firstOrNull(),
            interestRemainingMinor = unpaid.sumOf { it.interestMinor },
            payoffDate = rows.lastOrNull()?.dueDate,
            completed = rows.isNotEmpty() && unpaid.isEmpty(),
        )
    }
}
