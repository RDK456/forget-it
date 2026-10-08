package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class NewFieldsCsvTest {
    @Test fun subscriptionKeepsExtraReminders() {
        val s = sub().copy(extraRemindDays = listOf(7, 3, 0))
        assertEquals(listOf(7, 3, 0), SubscriptionCsv.import(SubscriptionCsv.export(listOf(s))).items.single().extraRemindDays)
    }

    @Test fun loanKeepsExtraReminders() {
        val l = Loan(
            id = 1, name = "L", principalMinor = 100_000, currency = "USD", annualRatePercent = BigDecimal("10"), tenureMonths = 12,
            firstEmiDate = d("2026-11-01"), extraRemindDays = listOf(5, 1),
        )
        assertEquals(listOf(5, 1), LoanCsv.import(LoanCsv.export(listOf(l), emptyList(), emptyList())).items.single().loan.extraRemindDays)
    }

    @Test fun stockKeepsPackLeadBrandStoreAndPrices() {
        val item = StockItem(id = 2, name = "Rice", unit = "kg", baselineDate = d("2026-10-01"), packSizeMilli = 5000, leadDays = 2, brand = "Daawat", store = "Mart")
        val log = StockLog(itemId = 2, date = d("2026-10-01"), deltaMilli = 5000, kind = LogKind.RESTOCK, priceMinor = 45_000)
        val b = StockCsv.import(StockCsv.export(listOf(item), emptyList(), listOf(log))).items.single()
        assertEquals(item.copy(id = 0), b.item)
        assertEquals(45_000L, b.logs.single().priceMinor)
    }

    private val bill = Bill(
        id = 3, name = "=Power, \"main\"", type = BillType.ELECTRICITY, currency = "USD", cycle = Cycle.QUARTERLY,
        anchorDate = d("2026-10-20"), remindDaysBefore = 4, extraRemindDays = listOf(10), notes = "a\nb", active = true,
    )

    @Test fun billsRoundTripWithEntries() {
        val entries = listOf(
            BillEntry(billId = 3, dueDate = d("2026-10-20"), amountMinor = 7_550, paidOn = d("2026-10-18")),
            BillEntry(billId = 3, dueDate = d("2027-01-20"), amountMinor = 8_000),
        )
        val r = BillCsv.import(BillCsv.export(listOf(bill), entries))
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val out = r.items.single()
        assertEquals(bill.copy(id = 0), out.bill)
        assertEquals(entries.map { Triple(it.dueDate, it.amountMinor, it.paidOn) }, out.entries.map { Triple(it.dueDate, it.amountMinor, it.paidOn) })
    }

    @Test fun billBadRowsAreReported() {
        val csv = "record_type,bill_key,name,type,currency,cycle,anchor_date,due_date,amount\r\n" +
            "BILL,1,Water,WATER,USD,MONTHLY,2026-10-05,,\r\n" +
            "BILL,2,Bad,WATER,USD,MONTHLY,notadate,,\r\n" +
            "ENTRY,9,,,,,,2026-10-05,10.00\r\n" +
            "ENTRY,1,,,,,,2026-10-05,abc\r\n"
        val r = BillCsv.import(csv)
        assertEquals(listOf("Water"), r.items.map { it.bill.name })
        assertEquals(listOf("Row 3: invalid anchor_date", "Row 4: no valid bill with bill_key 9", "Row 5: invalid entry"), r.errors)
    }
}
