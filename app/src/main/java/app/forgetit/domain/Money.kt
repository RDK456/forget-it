package app.forgetit.domain

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    private val PLAIN_NUMBER = Regex("""\d+([.,]\d+)?""")

    fun isValidCurrency(code: String): Boolean =
        code.length == 3 && runCatching { Currency.getInstance(code) }.isSuccess

    fun fractionDigits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    /** "9.99" or "9,99" -> minor units. Null for anything that is not a plain non-negative amount. */
    fun parseMinor(text: String, currency: String): Long? = parseScaled(text, fractionDigits(currency))

    /** Quantity text -> thousandths (1 L = 1000). Up to 3 decimals; null when invalid. */
    fun parseMilli(text: String): Long? = parseScaled(text, 3)

    private fun parseScaled(text: String, digits: Int): Long? {
        val t = text.trim()
        if (!PLAIN_NUMBER.matches(t)) return null
        return try {
            val value = BigDecimal(t.replace(',', '.'))
            if (value.scale() > digits) null else value.movePointRight(digits).longValueExact()
        } catch (e: ArithmeticException) {
            null
        }
    }

    fun format(minor: Long, currency: String, locale: Locale = Locale.getDefault()): String {
        val nf = NumberFormat.getCurrencyInstance(locale)
        nf.currency = Currency.getInstance(currency)
        return nf.format(BigDecimal.valueOf(minor, fractionDigits(currency)))
    }

    /** Plain decimal text for editing, e.g. 999 USD -> "9.99". */
    fun toPlain(minor: Long, currency: String): String =
        BigDecimal.valueOf(minor, fractionDigits(currency)).toPlainString()

    /** Thousandths -> "0.5", "2", "1.25" (no trailing zeros). */
    fun milliToPlain(milli: Long): String =
        BigDecimal.valueOf(milli, 3).stripTrailingZeros().toPlainString()
}
