package app.forgetit.domain

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

object Cost {
    val MC = MathContext(20, RoundingMode.HALF_UP)
    private val TWELVE = BigDecimal(12)

    /** Monthly equivalent in minor units of the subscription's own currency, unrounded. */
    fun monthlyExact(sub: Subscription): BigDecimal {
        val a = BigDecimal(sub.amountMinor)
        return when (sub.cycle) {
            Cycle.WEEKLY -> a.multiply(BigDecimal(52)).divide(TWELVE, MC)
            Cycle.MONTHLY -> a
            Cycle.QUARTERLY -> a.divide(BigDecimal(3), MC)
            Cycle.YEARLY -> a.divide(TWELVE, MC)
            Cycle.CUSTOM_DAYS ->
                a.multiply(BigDecimal(365)).divide(BigDecimal(checkNotNull(sub.customDays) * 12), MC)
        }
    }

    fun round(x: BigDecimal): Long = x.setScale(0, RoundingMode.HALF_UP).longValueExact()

    fun monthlyMinor(sub: Subscription): Long = round(monthlyExact(sub))
    fun yearlyMinor(sub: Subscription): Long = round(monthlyExact(sub).multiply(TWELVE))
}
