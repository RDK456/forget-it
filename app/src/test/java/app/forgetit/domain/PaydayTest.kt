package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PaydayTest {
    @Test fun laterThisMonthOrNextMonth() {
        assertEquals(d("2026-10-25"), Payday.next(d("2026-10-08"), 25))
        assertEquals(d("2026-11-01"), Payday.next(d("2026-10-08"), 1))
        assertEquals(d("2026-10-08"), Payday.next(d("2026-10-08"), 8))
    }

    @Test fun shortMonthsUseTheirLastDay() {
        assertEquals(d("2026-02-28"), Payday.next(d("2026-02-01"), 31))
        assertEquals(d("2026-02-28"), Payday.next(d("2026-01-31"), 30))
        assertEquals(d("2026-11-30"), Payday.next(d("2026-11-02"), 31))
    }
}
