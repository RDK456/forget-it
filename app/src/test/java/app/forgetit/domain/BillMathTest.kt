package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillMathTest {
    private val bill = Bill(id = 1, name = "Electricity", type = BillType.ELECTRICITY, currency = "USD", anchorDate = d("2026-09-10"))
    private fun e(due: String, amount: Long, paid: String? = null) = BillEntry(billId = 1, dueDate = d(due), amountMinor = amount, paidOn = paid?.let(::d))

    @Test fun pendingDueIsTheNextOneWhenThePastOneIsPaid() {
        val paid = listOf(e("2026-10-10", 5000, "2026-10-09"))
        assertEquals(d("2026-11-10"), BillMath.pendingDue(bill, paid, d("2026-10-20")))
    }

    @Test fun anUnpaidPastDueDateIsStillPending() {
        assertEquals(d("2026-10-10"), BillMath.pendingDue(bill, emptyList(), d("2026-10-20")))
        val recordedButUnpaid = listOf(e("2026-10-10", 5000))
        assertEquals(d("2026-10-10"), BillMath.pendingDue(bill, recordedButUnpaid, d("2026-10-20")))
    }

    @Test fun aBillThatHasNotStartedYetIsPendingOnItsFirstDate() {
        assertEquals(d("2026-09-10"), BillMath.pendingDue(bill, emptyList(), d("2026-09-01")))
    }

    @Test fun averageUsesTheMostRecentBills() {
        val h = listOf(e("2026-07-10", 9000), e("2026-08-10", 6000), e("2026-09-10", 3000))
        assertEquals(6000L, BillMath.average(h))
        assertNull(BillMath.average(emptyList()))
        val many = (1..8).map { e("2026-0$it-10", 1000L * it) }
        assertEquals(5500L, BillMath.average(many, 6))
    }

    @Test fun highBillNeedsHistoryAndTwentyFivePercent() {
        val h = listOf(e("2026-07-10", 10000), e("2026-08-10", 10000), e("2026-09-10", 10000))
        assertEquals(30, BillMath.changePercent(13000, h))
        assertTrue(BillMath.isHigh(12500, h))
        assertFalse(BillMath.isHigh(12400, h))
        assertEquals(-20, BillMath.changePercent(8000, h))
        assertNull(BillMath.changePercent(13000, h.take(2)))
        assertFalse(BillMath.isHigh(99999, h.take(2)))
    }

    @Test fun monthlyEquivalentFollowsTheCycle() {
        val h = listOf(e("2026-09-10", 12000))
        assertEquals(12000L, BillMath.monthlyEquivalentMinor(bill, h))
        assertEquals(4000L, BillMath.monthlyEquivalentMinor(bill.copy(cycle = Cycle.QUARTERLY), h))
        assertEquals(1000L, BillMath.monthlyEquivalentMinor(bill.copy(cycle = Cycle.YEARLY), h))
        assertNull(BillMath.monthlyEquivalentMinor(bill, emptyList()))
    }
}

class BillAheadTest {
    private val bill = Bill(id = 1, name = "Power", currency = "USD", anchorDate = d("2026-10-15"))
    private fun paid(due: String) = BillEntry(billId = 1, dueDate = d(due), amountMinor = 100, paidOn = d("2026-10-01"))

    @Test fun keepsLookingPastCyclesThatArePaidInAdvance() {
        val entries = listOf(paid("2026-10-15"), paid("2026-11-15"), paid("2026-12-15"))
        assertEquals(d("2027-01-15"), BillMath.pendingDue(bill, entries, d("2026-10-08")))
    }
}
