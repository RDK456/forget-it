package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CostTest {
    @Test fun monthlyEquivalents() {
        assertEquals(4333L, Cost.monthlyMinor(sub(Cycle.WEEKLY, amount = 1000)))
        assertEquals(1000L, Cost.monthlyMinor(sub(Cycle.MONTHLY, amount = 1000)))
        assertEquals(1000L, Cost.monthlyMinor(sub(Cycle.QUARTERLY, amount = 3000)))
        assertEquals(1000L, Cost.monthlyMinor(sub(Cycle.YEARLY, amount = 12000)))
        assertEquals(3042L, Cost.monthlyMinor(sub(Cycle.CUSTOM_DAYS, amount = 1000, customDays = 10)))
    }

    @Test fun yearlyIsComputedFromUnroundedMonthly() {
        // 9999/12 = 833.25 -> monthly 833, but yearly must be exactly 9999, not 833*12 = 9996.
        val s = sub(Cycle.YEARLY, amount = 9999)
        assertEquals(833L, Cost.monthlyMinor(s))
        assertEquals(9999L, Cost.yearlyMinor(s))
    }
}
