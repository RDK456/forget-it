package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class LoanCsvTest {
    private val loan = Loan(
        id = 5, name = "=Car, \"new\"", lender = "Bank\nof X", type = LoanType.CAR, principalMinor = 500_000, currency = "USD",
        annualRatePercent = BigDecimal("8.5"), tenureMonths = 24, firstEmiDate = d("2026-11-05"), emiOverrideMinor = 22_800,
        remindDaysBefore = 3, notes = "a,b", active = true,
    )
    private val pay = listOf(LoanPayment(loanId = 5, installmentNo = 1, paidOn = d("2026-11-05"), amountMinor = 22_800))
    private val adj = listOf(
        LoanAdjustment(loanId = 5, date = d("2026-12-01"), kind = AdjustmentKind.PREPAYMENT_REDUCE_EMI, amountMinor = 100_000),
        LoanAdjustment(loanId = 5, date = d("2027-01-01"), kind = AdjustmentKind.BALANCE_RESET, amountMinor = 350_000),
    )

    @Test fun roundTripKeepsLoanPaymentsAndAdjustments() {
        val r = LoanCsv.import(LoanCsv.export(listOf(loan), pay, adj))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val b = r.items.single()
        assertEquals(loan.copy(id = 0), b.loan)
        assertEquals(listOf(1), b.payments.map { it.installmentNo })
        assertEquals(22_800L, b.payments.single().amountMinor)
        assertEquals(adj.map { it.kind to it.amountMinor }, b.adjustments.map { it.kind to it.amountMinor })
    }

    @Test fun twoLoansKeepTheirOwnRows() {
        val other = loan.copy(id = 9, name = "Phone", emiOverrideMinor = null)
        val op = LoanPayment(loanId = 9, installmentNo = 2, paidOn = d("2026-12-05"), amountMinor = 10_000)
        val r = LoanCsv.import(LoanCsv.export(listOf(loan, other), pay + op, adj))
        assertEquals(listOf(1), r.items[0].payments.map { it.installmentNo })
        assertEquals(listOf(2), r.items[1].payments.map { it.installmentNo })
        assertEquals(2, r.items[0].adjustments.size)
        assertEquals(0, r.items[1].adjustments.size)
    }

    @Test fun badRowsAreReportedAndOrphansRejected() {
        val csv = "record_type,loan_key,name,principal,currency,rate_percent,tenure_months,first_emi_date,installment_no,paid_on,amount\r\n" +
            "LOAN,1,Good,1000.00,USD,10,12,2026-01-01,,,\r\n" +
            "LOAN,2,Bad,abc,USD,10,12,2026-01-01,,,\r\n" +
            "LOAN,3,TooLowEmi,1000.00,USD,10,12,2026-01-01,,,\r\n" +
            "PAYMENT,99,,,,,,,1,2026-02-01,10.00\r\n" +
            "PAYMENT,1,,,,,,,1,2026-02-01,88.00\r\n"
        val r = LoanCsv.import(csv)
        assertEquals(listOf("Good", "TooLowEmi"), r.items.map { it.loan.name })
        assertEquals(1, r.items[0].payments.size)
        assertEquals(listOf("Row 3: invalid principal", "Row 5: no valid loan with loan_key 99"), r.errors)
    }

    @Test fun missingColumnsAreExplained() {
        assertEquals(listOf("Missing columns: record_type, loan_key"), LoanCsv.import("name\r\nx\r\n").errors)
    }
}
