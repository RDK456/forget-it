package app.forgetit.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.forgetit.domain.AdjustmentKind
import app.forgetit.domain.Loan
import app.forgetit.domain.LoanAdjustment
import app.forgetit.domain.LoanPayment
import app.forgetit.domain.LoanType
import java.math.BigDecimal
import java.time.LocalDate

@Entity(tableName = "loan")
data class LoanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val lender: String,
    val type: String,
    val principalMinor: Long,
    val currency: String,
    val annualRatePercent: String,
    val tenureMonths: Int,
    val firstEmiEpochDay: Long,
    val emiOverrideMinor: Long?,
    val remindDaysBefore: Int,
    val notes: String,
    val active: Boolean,
)

fun LoanEntity.toDomain() = Loan(
    id, name, lender, LoanType.valueOf(type), principalMinor, currency, BigDecimal(annualRatePercent), tenureMonths,
    LocalDate.ofEpochDay(firstEmiEpochDay), emiOverrideMinor, remindDaysBefore, notes, active,
)

fun Loan.toEntity() = LoanEntity(
    id, name.trim(), lender.trim(), type.name, principalMinor, currency, annualRatePercent.toPlainString(), tenureMonths,
    firstEmiDate.toEpochDay(), emiOverrideMinor, remindDaysBefore, notes, active,
)

@Entity(tableName = "loan_payment")
data class LoanPaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val installmentNo: Int,
    val paidEpochDay: Long,
    val amountMinor: Long,
)

fun LoanPaymentEntity.toDomain() = LoanPayment(id, loanId, installmentNo, LocalDate.ofEpochDay(paidEpochDay), amountMinor)
fun LoanPayment.toEntity() = LoanPaymentEntity(id, loanId, installmentNo, paidOn.toEpochDay(), amountMinor)

@Entity(tableName = "loan_adjustment")
data class LoanAdjustmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val loanId: Long,
    val epochDay: Long,
    val kind: String,
    val amountMinor: Long,
)

fun LoanAdjustmentEntity.toDomain() = LoanAdjustment(id, loanId, LocalDate.ofEpochDay(epochDay), AdjustmentKind.valueOf(kind), amountMinor)
fun LoanAdjustment.toEntity() = LoanAdjustmentEntity(id, loanId, date.toEpochDay(), kind.name, amountMinor)
