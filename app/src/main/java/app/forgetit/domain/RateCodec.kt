package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

/** Manual exchange rate: units of the default currency per 1 unit of the rate currency. */
data class Rate(val value: BigDecimal, val editedOn: LocalDate)

/** Compact text form stored in settings, for example EUR|1.10|2026-10-08 entries joined by semicolons. */
object RateCodec {
    fun encode(rates: Map<String, Rate>): String =
        rates.entries.joinToString(";") { (c, r) -> "$c|${r.value.toPlainString()}|${r.editedOn}" }

    fun decode(text: String): Map<String, Rate> {
        val out = linkedMapOf<String, Rate>()
        for (part in text.split(";")) {
            val f = part.split("|")
            if (f.size != 3 || !Money.isValidCurrency(f[0])) continue
            val value = runCatching { BigDecimal(f[1]) }.getOrNull()?.takeIf { it.signum() > 0 } ?: continue
            val date = runCatching { LocalDate.parse(f[2]) }.getOrNull() ?: continue
            out[f[0]] = Rate(value, date)
        }
        return out
    }
}
