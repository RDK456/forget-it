package app.forgetit.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import app.forgetit.domain.AdjustmentKind
import app.forgetit.domain.Loan
import app.forgetit.domain.LoanAdjustment
import app.forgetit.domain.LoanPayment
import app.forgetit.domain.LoanType
import app.forgetit.domain.Offsets
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
    @ColumnInfo(defaultValue = "''") val extraRemind: String = "",
    @ColumnInfo(defaultValue = "''") val payUrl: String = "",
)

fun LoanEntity.toDomain() = Loan(
    id = id, name = name, lender = lender, type = LoanType.valueOf(type), principalMinor = principalMinor, currency = currency,
    annualRatePercent = BigDecimal(annualRatePercent), tenureMonths = tenureMonths, firstEmiDate = LocalDate.ofEpochDay(firstEmiEpochDay),
    emiOverrideMinor = emiOverrideMinor, remindDaysBefore = remindDaysBefore, extraRemindDays = Offsets.parse(extraRemind),
    notes = notes, active = active, payUrl = payUrl,
)

fun Loan.toEntity() = LoanEntity(
    id = id, name = name.trim(), lender = lender.trim(), type = type.name, principalMinor = principalMinor, currency = currency,
    annualRatePercent = annualRatePercent.toPlainString(), tenureMonths = tenureMonths, firstEmiEpochDay = firstEmiDate.toEpochDay(),
    emiOverrideMinor = emiOverrideMinor, remindDaysBefore = remindDaysBefore, notes = notes, active = active,
    extraRemind = Offsets.format(extraRemindDays), payUrl = payUrl.trim(),
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
