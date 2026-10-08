package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

class DocScanTest {
    private val today = d("2026-10-08")
    private fun classify(text: String, cur: String = "USD", labels: List<ScannedItem> = emptyList()) = DocScan.classify(text, labels, cur, today)

    @Test fun subscriptionScreenshot() {
        val r = classify("Netflix\nYour membership\nPlan: Standard\nNext billing date: 12 Nov 2026\nAmount: \$15.49 per month\nCancel anytime")!!
        assertEquals(DocKind.SUBSCRIPTION, r.kind)
        assertEquals("Netflix", r.name)
        assertEquals(1549L, r.amountMinor)
        assertEquals(d("2026-11-12"), r.date)
        assertEquals(Cycle.MONTHLY, r.cycle)
        assertEquals("Streaming", r.category)
    }

    @Test fun yearlySubscriptionCycle() {
        val r = classify("Spotify Premium\nBilled annually\nRenews on 01/03/2027\nTotal: \$99.00")!!
        assertEquals(DocKind.SUBSCRIPTION, r.kind)
        assertEquals(Cycle.YEARLY, r.cycle)
        assertEquals(d("2027-03-01"), r.date)
    }

    @Test fun loanEmiMessage() {
        val r = classify("HDFC Bank\nYour EMI of Rs 12,450.00 for Home Loan A/c XX1234 is due on 05/11/2026.\nTenure: 240 months. Interest rate 8.5%")!!
        assertEquals(DocKind.EMI, r.kind)
        assertEquals(1_245_000L, r.amountMinor)
        assertEquals("INR", r.currency)
        assertEquals(d("2026-11-05"), r.date)
        assertEquals(240, r.tenureMonths)
        assertEquals(BigDecimal("8.5"), r.ratePercent)
        assertEquals("Hdfc Bank", r.name)
    }

    @Test fun electricityBill() {
        val r = classify("City Power Corporation\nElectricity Bill\nUnits consumed: 320 kWh\nTotal amount due: \$84.20\nDue date: 15/11/2026")!!
        assertEquals(DocKind.BILL, r.kind)
        assertEquals(BillType.ELECTRICITY, r.billType)
        assertEquals(8420L, r.amountMinor)
        assertEquals(d("2026-11-15"), r.date)
        assertEquals("City Power Corporation", r.name)
    }

    @Test fun creditCardStatementIsABillOfTypeCreditCard() {
        val r = classify("Credit Card Statement\nTotal due \$420.50\nMinimum due \$25.00\nPayment due date 20 Nov 2026")!!
        assertEquals(DocKind.BILL, r.kind)
        assertEquals(BillType.CREDIT_CARD, r.billType)
        assertEquals(42_050L, r.amountMinor)
        assertEquals(d("2026-11-20"), r.date)
    }

    @Test fun groceryReceiptGivesItems() {
        val r = classify("FRESH MART\nDate 08/10/2026\nMILK 1L          2.49\n2 x Bananas      1.20\nBasmati Rice 5kg 450.00\nDettol Soap      3.10\nTOTAL            457.79")!!
        assertEquals(DocKind.GROCERY, r.kind)
        assertEquals(4, r.items.size)
    }

    @Test fun plainPaymentFallsBackToAPaymentNote() {
        val r = classify("Paid Rs 250.00 to Corner Cafe on 08-Oct-2026")!!
        assertEquals(DocKind.PAYMENT, r.kind)
        assertEquals(25_000L, r.amountMinor)
        assertEquals(d("2026-10-08"), r.date)
    }

    @Test fun recognisedItemsAloneAreGroceries() {
        val r = classify("", labels = GroceryScan.fromLabels(listOf("Banana" to 0.9f)))!!
        assertEquals(DocKind.GROCERY, r.kind)
        assertNotNull(r.items.firstOrNull())
    }

    @Test fun unrelatedTextIsNothing() {
        assertNull(classify("Hello world, see you tomorrow"))
        assertNull(classify(""))
    }

    @Test fun dateFormats() {
        assertEquals(listOf(d("2026-11-05")), DocScan.datesIn("due 2026-11-05"))
        assertEquals(listOf(d("2026-11-25")), DocScan.datesIn("on 11/25/2026"))
        assertEquals(listOf(d("2026-10-12")), DocScan.datesIn("Oct 12, 2026"))
        assertEquals(listOf(d("2026-10-12")), DocScan.datesIn("12th Oct 2026"))
    }
}
