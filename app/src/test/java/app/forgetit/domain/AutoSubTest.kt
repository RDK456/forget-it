package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoSubTest {
    private val received = d("2026-10-09")

    private val claudeReceipt = """
        Anthropic, PBC
        Receipt from Anthropic
        Claude Pro (annual)
        Amount paid ${'$'}200.00
        Date paid Oct 8, 2026
        Billing period Oct 8, 2026 - Oct 8, 2027
        Paid with Visa ending 4242
        Thank you for subscribing
    """.trimIndent()

    @Test fun anAnnualConfirmationEmailBecomesASubscriptionWithEveryDetail() {
        val g = AutoSub.fromMessage(claudeReceipt, "USD", received, emptyList(), emptySet())!!
        assertEquals("Claude Pro", g.name)
        assertEquals(20_000L, g.amountMinor)
        assertEquals("USD", g.currency)
        assertEquals(Cycle.YEARLY, g.cycle)
        assertEquals("Software", g.category)
        assertEquals(d("2026-10-08"), g.paidOn)
        assertEquals(d("2026-10-08"), g.startDate)
        assertEquals(d("2027-10-08"), g.nextDate)
        assertEquals("Card ending 4242", g.paymentMethod)
        assertTrue(g.notes.contains("Paid \$200.00 on 2026-10-08"))
        assertTrue(g.notes.contains("2027-10-08"))
    }

    @Test fun aSingleLineNotificationWithAnExplicitRenewalDate() {
        val text = "Anthropic. Your receipt: Claude Pro yearly subscription, \$200.00 paid on Oct 8, 2026. Renews on Oct 8, 2027."
        val g = AutoSub.fromMessage(text, "USD", received, emptyList(), emptySet())!!
        assertEquals(Cycle.YEARLY, g.cycle)
        assertEquals(d("2027-10-08"), g.nextDate)
        assertEquals(d("2026-10-08"), g.startDate)
    }

    @Test fun withoutADateTheReceiptDayAndTheCycleGiveTheNextPayment() {
        val text = "Netflix\nReceipt\nYour monthly membership\nTotal: \$15.49 charged to your card ending 4455"
        val g = AutoSub.fromMessage(text, "USD", d("2026-10-08"), emptyList(), emptySet())!!
        assertEquals(Cycle.MONTHLY, g.cycle)
        assertEquals(d("2026-10-08"), g.paidOn)
        assertEquals(d("2026-11-08"), g.nextDate)
        assertEquals("Card ending 4455", g.paymentMethod)
    }

    @Test fun twelveMonthsAndOneYearCountAsYearly() {
        assertEquals(Cycle.YEARLY, AutoSub.fromMessage("Spotify receipt. 12 months of Premium, billed \$99.00 on Oct 8, 2026.", "USD", received, emptyList(), emptySet())!!.cycle)
        assertEquals(Cycle.YEARLY, AutoSub.fromMessage("Notion receipt. 1 year plan, charged \$96.00 on Oct 8, 2026. Your subscription is active.", "USD", received, emptyList(), emptySet())!!.cycle)
    }

    @Test fun promotionsTrackedAndTurnedDownAreIgnored() {
        assertNull(AutoSub.fromMessage("Get Claude Pro with 20% off, limited time offer. Subscription from \$160.00 per year", "USD", received, emptyList(), emptySet()))
        assertNull(AutoSub.fromMessage(claudeReceipt, "USD", received, listOf("Claude Pro"), emptySet()))
        assertNull(AutoSub.fromMessage(claudeReceipt, "USD", received, emptyList(), setOf(AutoTrack.subKey("Claude Pro"))))
        assertNull(AutoSub.fromMessage("See you at the meeting tomorrow, thanks", "USD", received, emptyList(), emptySet()))
    }

    @Test fun lenderNameIsFoundInASingleLineEmiText() {
        assertEquals("HDFC Bank", DocDates.lenderIn("Your EMI of Rs 12,450.00 for Home Loan A/c XX1234 is due. HDFC Bank"))
        assertNull(DocDates.lenderIn("nothing here"))
    }

    @Test fun aPlanPaidTodayRenewsOneCycleLaterNotToday() {
        val g = AutoSub.fromMessage(claudeReceipt, "USD", d("2026-10-08"), emptyList(), emptySet())!!
        assertEquals(d("2027-10-08"), g.startDate)
        assertEquals(d("2026-10-08"), g.paidOn)
        assertEquals(d("2027-10-08"), g.nextDate)
    }
}
