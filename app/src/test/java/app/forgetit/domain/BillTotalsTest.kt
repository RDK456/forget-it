package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.YearMonth

class BillTotalsTest {
    private fun bill(id: Long, currency: String = "USD", cycle: Cycle = Cycle.MONTHLY, active: Boolean = true) =
        Bill(id = id, name = "B$id", currency = currency, cycle = cycle, anchorDate = d("2026-10-05"), active = active)
    private fun e(billId: Long, due: String, amount: Long, paid: String? = null) = BillEntry(billId = billId, dueDate = d(due), amountMinor = amount, paidOn = paid?.let(::d))

    @Test fun outgoAveragesRecordedBillsAndSkipsInactiveOrEmpty() {
        val bills = listOf(bill(1), bill(2, cycle = Cycle.QUARTERLY), bill(3), bill(4, active = false))
        val entries = listOf(e(1, "2026-09-05", 6000), e(1, "2026-10-05", 8000), e(2, "2026-09-05", 9000), e(4, "2026-09-05", 5000))
        assertEquals(BillOutgo(7000 + 3000, 0), billMonthlyOutgo(bills, entries, "USD", emptyMap()))
    }

    @Test fun missingRatesAreCountedAndRatesConvert() {
        val bills = listOf(bill(1, "EUR"))
        val entries = listOf(e(1, "2026-10-05", 5000))
        assertEquals(BillOutgo(0, 1), billMonthlyOutgo(bills, entries, "USD", emptyMap()))
        assertEquals(BillOutgo(10_000, 0), billMonthlyOutgo(bills, entries, "USD", mapOf("EUR" to BigDecimal("2"))))
    }

    @Test fun calendarShowsPaidRecordedUsualAndUnknown() {
        val bills = listOf(bill(1), bill(2))
        val entries = listOf(e(1, "2026-10-05", 6500, "2026-10-03"), e(2, "2026-09-05", 4000))
        val m = billCalendarEntries(bills, entries, YearMonth.of(2026, 10), d("2026-10-08"))
        val day = m.getValue(d("2026-10-05")).associateBy { it.title }
        assertEquals("Paid ${Money.format(6500, "USD")}", day.getValue("B1").detail)
        assertEquals("Usually ${Money.format(4000, "USD")}", day.getValue("B2").detail)
        assertTrue(day.values.all { it.type == EntryType.BILL })
        val unknown = billCalendarEntries(listOf(bill(9)), emptyList(), YearMonth.of(2026, 10), d("2026-10-08"))
        assertEquals("Due", unknown.getValue(d("2026-10-05")).single().detail)
    }
}
