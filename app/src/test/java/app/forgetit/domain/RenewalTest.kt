package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenewalTest {
    @Test fun weeklyTodayCountsAsUpcoming() {
        val s = sub(Cycle.WEEKLY, start = "2026-01-01")
        assertEquals(d("2026-01-01"), Renewal.next(s, d("2026-01-01")))
        assertEquals(d("2026-01-08"), Renewal.next(s, d("2026-01-02")))
    }

    @Test fun futureStartIsTheNextRenewal() {
        assertEquals(d("2026-12-01"), Renewal.next(sub(start = "2026-12-01"), d("2026-10-08")))
    }

    @Test fun monthlyClampsToMonthEndWithoutDrifting() {
        val s = sub(Cycle.MONTHLY, start = "2026-01-31")
        assertEquals(d("2026-02-28"), Renewal.next(s, d("2026-02-01")))
        assertEquals(d("2026-03-31"), Renewal.next(s, d("2026-03-01")))
        assertEquals(d("2026-04-30"), Renewal.next(s, d("2026-04-01")))
    }

    @Test fun monthlyLeapYear() {
        assertEquals(d("2024-02-29"), Renewal.next(sub(start = "2024-01-31"), d("2024-02-01")))
    }

    @Test fun yearlyLeapDayAnchor() {
        val s = sub(Cycle.YEARLY, start = "2024-02-29")
        assertEquals(d("2025-02-28"), Renewal.next(s, d("2025-01-01")))
        assertEquals(d("2028-02-29"), Renewal.next(s, d("2028-01-01")))
    }

    @Test fun quarterly() {
        assertEquals(d("2026-04-15"), Renewal.next(sub(Cycle.QUARTERLY, start = "2026-01-15"), d("2026-02-01")))
    }

    @Test fun customDays() {
        val s = sub(Cycle.CUSTOM_DAYS, start = "2026-01-01", customDays = 10)
        assertEquals(d("2026-01-21"), Renewal.next(s, d("2026-01-12")))
    }

    @Test fun trialChargesOnTrialEndThenContinuesFromThere() {
        val s = sub(Cycle.MONTHLY, start = "2026-10-01", trialEnds = "2026-10-20")
        assertEquals(d("2026-10-20"), Renewal.next(s, d("2026-10-08")))
        assertEquals(d("2026-11-20"), Renewal.next(s, d("2026-10-21")))
    }

    @Test fun trialActiveUntilEndDateInclusive() {
        val s = sub(trialEnds = "2026-10-20")
        assertTrue(s.isTrialActive(d("2026-10-20")))
        assertFalse(s.isTrialActive(d("2026-10-21")))
    }

    @Test fun settleTrialConvertsExpiredTrialToRegularSub() {
        val s = sub(start = "2026-10-01", trialEnds = "2026-10-20")
        assertEquals(s, s.settleTrial(d("2026-10-20")))
        val settled = s.settleTrial(d("2026-10-21"))
        assertFalse(settled.isTrial)
        assertEquals(d("2026-10-20"), settled.startDate)
        assertNull(settled.trialEndsAt)
    }

    @Test fun betweenListsAllChargesInRange() {
        val s = sub(Cycle.WEEKLY, start = "2026-10-01")
        assertEquals(
            listOf(d("2026-10-01"), d("2026-10-08"), d("2026-10-15"), d("2026-10-22"), d("2026-10-29")),
            Renewal.between(s, d("2026-10-01"), d("2026-10-31")),
        )
    }
}
