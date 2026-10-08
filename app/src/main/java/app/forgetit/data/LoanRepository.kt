package app.forgetit.data

import app.forgetit.domain.Amortization
import app.forgetit.domain.Loan
import app.forgetit.domain.LoanAdjustment
import app.forgetit.domain.LoanPayment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class LoanRepository(private val dao: LoanDao, private val photos: PhotoRepository) {
    fun observeLoans(): Flow<List<Loan>> = dao.observeLoans().map { l -> l.map { it.toDomain() } }
    fun observePayments(): Flow<List<LoanPayment>> = dao.observePayments().map { l -> l.map { it.toDomain() } }
    fun observeAdjustments(): Flow<List<LoanAdjustment>> = dao.observeAdjustments().map { l -> l.map { it.toDomain() } }

    suspend fun getLoans() = dao.getLoans().map { it.toDomain() }
    suspend fun getLoan(id: Long) = dao.getLoan(id)?.toDomain()
    suspend fun getPayments() = dao.getPayments().map { it.toDomain() }
    suspend fun getAdjustments() = dao.getAdjustments().map { it.toDomain() }

    /** Validates (including the EMI-too-low rule) before writing anything. */
    suspend fun save(loan: Loan): SaveResult {
        val errors = Amortization.validate(loan)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val rowId = dao.upsertLoan(loan.toEntity())
        return SaveResult.Saved(if (loan.id == 0L) rowId else loan.id)
    }

    suspend fun delete(id: Long) {
        dao.deleteLoan(id)
        dao.deletePayments(id)
        dao.deleteAdjustments(id)
        photos.deleteAll(OwnerType.LOAN, id)
    }

    suspend fun markPaid(loanId: Long, installmentNo: Int, paidOn: LocalDate, amountMinor: Long) {
        dao.deletePayment(loanId, installmentNo)
        dao.insertPayment(LoanPayment(loanId = loanId, installmentNo = installmentNo, paidOn = paidOn, amountMinor = amountMinor).toEntity())
    }

    suspend fun unmarkPaid(loanId: Long, installmentNo: Int) = dao.deletePayment(loanId, installmentNo)

    suspend fun addAdjustment(a: LoanAdjustment) { dao.insertAdjustment(a.toEntity()) }

    suspend fun deleteAdjustment(id: Long) = dao.deleteAdjustment(id)

    /** Adds an imported loan with its payments and adjustments as a new loan. */
    suspend fun importBundle(b: app.forgetit.domain.LoanBundle) {
        val id = dao.upsertLoan(b.loan.copy(id = 0).toEntity())
        b.payments.forEach { dao.insertPayment(it.copy(loanId = id).toEntity()) }
        b.adjustments.forEach { dao.insertAdjustment(it.copy(loanId = id).toEntity()) }
    }
}
