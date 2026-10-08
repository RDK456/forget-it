package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

data class Totals(val monthlyMinor: Long, val yearlyMinor: Long, val excluded: Int)

/** Converts [minor] (minor units of [from]) into minor units of [to]; null when [from] has no rate. */
fun convertMinor(minor: BigDecimal, from: String, to: String, rates: Map<String, BigDecimal>): BigDecimal? {
    if (from == to) return minor
    val rate = rates[from] ?: return null
    return minor.movePointLeft(Money.fractionDigits(from))
        .multiply(rate)
        .movePointRight(Money.fractionDigits(to))
}

/** Exact monthly cost in minor units of [default]; null when the currency has no rate. Active trials cost 0. */
fun monthlyInDefault(sub: Subscription, today: LocalDate, default: String, rates: Map<String, BigDecimal>): BigDecimal? {
    if (sub.isTrialActive(today)) return BigDecimal.ZERO
    return convertMinor(Cost.monthlyExact(sub), sub.currency, default, rates)
}

fun computeTotals(subs: List<Subscription>, today: LocalDate, default: String, rates: Map<String, BigDecimal>): Totals {
    var sum = BigDecimal.ZERO
    var excluded = 0
    for (s in subs) {
        if (!s.active) continue
        val m = monthlyInDefault(s, today, default, rates)
        if (m == null) excluded++ else sum += m
    }
    return Totals(Cost.round(sum), Cost.round(sum.multiply(BigDecimal(12))), excluded)
}
