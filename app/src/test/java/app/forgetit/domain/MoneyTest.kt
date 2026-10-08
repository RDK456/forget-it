package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun fractionDigitsPerCurrency() {
        assertEquals(2, Money.fractionDigits("USD"))
        assertEquals(0, Money.fractionDigits("JPY"))
        assertEquals(3, Money.fractionDigits("KWD"))
    }

    @Test fun parsesDotAndCommaDecimals() {
        assertEquals(999L, Money.parseMinor("9.99", "USD"))
        assertEquals(999L, Money.parseMinor("9,99", "USD"))
        assertEquals(1000L, Money.parseMinor("10", "USD"))
        assertEquals(1000L, Money.parseMinor(" 10 ", "USD"))
    }

    @Test fun parsesZeroAndThreeDecimalCurrencies() {
        assertEquals(500L, Money.parseMinor("500", "JPY"))
        assertNull(Money.parseMinor("500.5", "JPY"))
        assertEquals(1234L, Money.parseMinor("1.234", "KWD"))
    }

    @Test fun rejectsBadInput() {
        for (bad in listOf("", "abc", "-5", "1.234", "1,000", "9.", ".5", "1.2.3", "99999999999999999999")) {
            assertNull("'$bad' should be rejected", Money.parseMinor(bad, "USD"))
        }
    }

    @Test fun parseQuantityMilli() {
        assertEquals(500L, Money.parseMilli("0.5"))
        assertEquals(2000L, Money.parseMilli("2"))
        assertEquals(1250L, Money.parseMilli("1,25"))
        assertNull(Money.parseMilli("1.2345"))
        assertNull(Money.parseMilli("x"))
    }

    @Test fun validatesCurrencyCode() {
        assertTrue(Money.isValidCurrency("EUR"))
        assertFalse(Money.isValidCurrency("XX1"))
        assertFalse(Money.isValidCurrency(""))
    }

    @Test fun formatsPlainDecimalWithoutLocaleSymbolDependence() {
        assertTrue(Money.format(999, "USD", java.util.Locale.US).contains("9.99"))
        assertTrue(Money.format(500, "JPY", java.util.Locale.JAPAN).contains("500"))
    }
}
