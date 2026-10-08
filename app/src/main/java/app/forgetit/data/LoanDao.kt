package app.forgetit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface LoanDao {
    @Query("SELECT * FROM loan") fun observeLoans(): Flow<List<LoanEntity>>
    @Query("SELECT * FROM loan") suspend fun getLoans(): List<LoanEntity>
    @Query("SELECT * FROM loan WHERE id = :id") suspend fun getLoan(id: Long): LoanEntity?
    @Upsert suspend fun upsertLoan(e: LoanEntity): Long
    @Query("DELETE FROM loan WHERE id = :id") suspend fun deleteLoan(id: Long)

    @Query("SELECT * FROM loan_payment") fun observePayments(): Flow<List<LoanPaymentEntity>>
    @Query("SELECT * FROM loan_payment") suspend fun getPayments(): List<LoanPaymentEntity>
    @Insert suspend fun insertPayment(e: LoanPaymentEntity): Long
    @Query("DELETE FROM loan_payment WHERE loanId = :loanId AND installmentNo = :no") suspend fun deletePayment(loanId: Long, no: Int)
    @Query("DELETE FROM loan_payment WHERE loanId = :loanId") suspend fun deletePayments(loanId: Long)

    @Query("SELECT * FROM loan_adjustment") fun observeAdjustments(): Flow<List<LoanAdjustmentEntity>>
    @Query("SELECT * FROM loan_adjustment") suspend fun getAdjustments(): List<LoanAdjustmentEntity>
    @Insert suspend fun insertAdjustment(e: LoanAdjustmentEntity): Long
    @Query("DELETE FROM loan_adjustment WHERE id = :id") suspend fun deleteAdjustment(id: Long)
    @Query("DELETE FROM loan_adjustment WHERE loanId = :loanId") suspend fun deleteAdjustments(loanId: Long)
}
