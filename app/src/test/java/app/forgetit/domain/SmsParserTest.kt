package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmsParserTest {
    private val day = d("2026-10-08")
    private fun parse(s: String, cur: String = "USD") = SmsParser.parse(s, day, cur)

    @Test fun cardSpendAtMerchant() {
        val t = parse("Rs 499.00 spent on HDFC Bank Card ending 1234 at NETFLIX on 08-Oct-26. Avl bal Rs 5,000")!!
        assertEquals(TxnDirection.DEBIT, t.direction)
        assertEquals(49_900L, t.amountMinor)
        assertEquals("INR", t.currency)
        assertEquals("Netflix", t.merchant)
        assertEquals("1234", t.accountHint)
        assertEquals(day, t.date)
    }

    @Test fun upiDebitWithThousandsSeparator() {
        val t = parse("Your a/c XX1234 is debited by INR 1,299.00 on 05-10-2026 for UPI to Swiggy ref 123456")!!
        assertEquals(129_900L, t.amountMinor)
        assertEquals("Swiggy", t.merchant)
    }

    @Test fun dollarAmountAndCurrencyCodes() {
        val t = parse("Spent USD 15.99 at SPOTIFY on your card ending 4455.")!!
        assertEquals(1599L, t.amountMinor)
        assertEquals("USD", t.currency)
        assertEquals("Spotify", t.merchant)
        assertEquals(1299L, parse("You paid $12.99 to Hulu on 10/08")!!.amountMinor)
    }

    @Test fun amountBeforeCurrencyCode() {
        assertEquals(50_000L, parse("Payment of 500 INR charged for Gym membership.")!!.amountMinor)
    }

    @Test fun creditsAndRefunds() {
        val salary = parse("Salary of INR 50,000.00 credited to your a/c XX1234 on 01-Oct")!!
        assertEquals(TxnDirection.CREDIT, salary.direction)
        assertNull(salary.merchant)
        assertEquals(TxnDirection.CREDIT, parse("Refund of Rs 299 processed for Amazon.")!!.direction)
    }

    @Test fun ignoresOtpPromoAndBalanceMessages() {
        assertNull(parse("Your OTP for payment of Rs 499 is 123456. Do not share."))
        assertNull(parse("Get 20% cashback on shopping above Rs 500 this weekend!"))
        assertNull(parse("Avl bal in a/c XX1234 is Rs 5,000.00"))
        assertNull(parse("Hello, see you at 5pm"))
    }

    @Test fun usesDefaultCurrencyWhenNoSymbolIsGiven() {
        val t = SmsParser.parse("Payment of 250 charged at Cafe Coffee", day, "EUR")
        assertNull(t)
    }
}
