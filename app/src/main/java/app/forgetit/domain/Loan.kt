package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

enum class LoanType { HOME, CAR, PERSONAL, EDUCATION, CARD_EMI, OTHER }

enum class AdjustmentKind { PREPAYMENT_REDUCE_TENURE, PREPAYMENT_REDUCE_EMI, BALANCE_RESET }

data class Loan(
    val id: Long = 0,
    val name: String,
    val lender: String = "",
    val type: LoanType = LoanType.OTHER,
    val principalMinor: Long,
    val currency: String,
    val annualRatePercent: BigDecimal,
    val tenureMonths: Int,
    val firstEmiDate: LocalDate,
    val emiOverrideMinor: Long? = null,
    val remindDaysBefore: Int = 2,
    val notes: String = "",
    val active: Boolean = true,
)

data class LoanPayment(val id: Long = 0, val loanId: Long, val installmentNo: Int, val paidOn: LocalDate, val amountMinor: Long)

/** amountMinor is the prepaid amount, or for BALANCE_RESET the new outstanding balance from the lender statement. */
data class LoanAdjustment(val id: Long = 0, val loanId: Long, val date: LocalDate, val kind: AdjustmentKind, val amountMinor: Long)

enum class RowStatus { PAID, OVERDUE, DUE, UPCOMING }

data class ScheduleRow(
    val no: Int,
    val dueDate: LocalDate,
    val paymentMinor: Long,
    val interestMinor: Long,
    val principalMinor: Long,
    val balanceBeforeMinor: Long,
    val balanceAfterMinor: Long,
    val status: RowStatus,
)

data class LoanSummary(
    val rows: List<ScheduleRow>,
    val emiMinor: Long,
    val outstandingMinor: Long,
    val nextDue: ScheduleRow?,
    val interestRemainingMinor: Long,
    val payoffDate: LocalDate?,
    val completed: Boolean,
)
