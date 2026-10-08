package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

class ReminderUpgradeTest {
    private fun at(s: String) = ZonedDateTime.parse(s + "[Asia/Kolkata]")
    private val nine = 9 * 60

    private fun plan(s: Subscription, now: String, notified: Set<String> = emptySet()) =
        ReminderPlanner.subscriptions(listOf(s), at(now), nine, notified)

    @Test fun severalLeadTimesEachGetTheirOwnReminder() {
        val s = sub(start = "2026-10-20", remind = 0).copy(extraRemindDays = listOf(7, 3))
        val specs = plan(s, "2026-10-08T12:00:00+05:30")
        assertEquals(listOf(at("2026-10-13T09:00:00+05:30"), at("2026-10-17T09:00:00+05:30"), at("2026-10-20T09:00:00+05:30")), specs.map { it.triggerAt })
        assertEquals(3, specs.map { it.key }.distinct().size)
        assertTrue(specs.any { it.key == ReminderPlanner.key(ReminderKind.RENEWAL, s.id, d("2026-10-20")) })
    }

    @Test fun leadTimesAlreadyPastAreSkippedQuietly() {
        val s = sub(start = "2026-10-20", remind = 0).copy(extraRemindDays = listOf(7, 3))
        val specs = plan(s, "2026-10-15T12:00:00+05:30")
        assertEquals(listOf(at("2026-10-17T09:00:00+05:30"), at("2026-10-20T09:00:00+05:30")), specs.map { it.triggerAt })
    }

    @Test fun eventFinishesOnlyWhenTheClosestReminderWasShown() {
        val s = sub(start = "2026-10-20", remind = 0).copy(extraRemindDays = listOf(7, 3))
        val early = setOf(ReminderPlanner.key(ReminderKind.RENEWAL, s.id, d("2026-10-20"), 7))
        assertEquals(2, plan(s, "2026-10-14T12:00:00+05:30", early).size)
        val all = early + ReminderPlanner.key(ReminderKind.RENEWAL, s.id, d("2026-10-20"))
        assertEquals(d("2026-11-20"), plan(s, "2026-10-20T10:00:00+05:30", all).first().eventDate)
    }

    @Test fun ifEveryLeadTimeWasMissedTheClosestFiresOnceNow() {
        val s = sub(start = "2026-10-20", remind = 2).copy(extraRemindDays = listOf(7))
        val now = at("2026-10-19T12:00:00+05:30")
        val specs = ReminderPlanner.subscriptions(listOf(s), now, nine, emptySet())
        assertEquals(listOf(now.plusMinutes(1)), specs.map { it.triggerAt })
    }

    private val bill = Bill(id = 4, name = "Electricity", type = BillType.ELECTRICITY, currency = "USD", anchorDate = d("2026-10-20"), remindDaysBefore = 3)

    @Test fun billRemindersMentionTheUsualAmountAndMoveOnWhenPaid() {
        val entries = listOf(
            BillEntry(billId = 4, dueDate = d("2026-08-20"), amountMinor = 4000, paidOn = d("2026-08-19")),
            BillEntry(billId = 4, dueDate = d("2026-09-20"), amountMinor = 6000, paidOn = d("2026-09-19")),
        )
        val spec = ReminderPlanner.bills(listOf(bill), entries, at("2026-10-08T12:00:00+05:30"), nine, emptySet()).single()
        assertEquals(ReminderKind.BILL_DUE, spec.kind)
        assertEquals(d("2026-10-20"), spec.eventDate)
        assertEquals(at("2026-10-17T09:00:00+05:30"), spec.triggerAt)
        assertTrue(spec.text.startsWith("Usually "))
        val paidNow = entries + BillEntry(billId = 4, dueDate = d("2026-10-20"), amountMinor = 5000, paidOn = d("2026-10-18"))
        assertEquals(d("2026-11-20"), ReminderPlanner.bills(listOf(bill), paidNow, at("2026-10-19T12:00:00+05:30"), nine, emptySet()).single().eventDate)
        assertTrue(ReminderPlanner.bills(listOf(bill.copy(active = false)), entries, at("2026-10-08T12:00:00+05:30"), nine, emptySet()).isEmpty())
    }

    private val quiet = DigestSummary(0, 0, 0, 0, emptyList())

    @Test fun digestGoesOutNextMondayAtTheReminderTime() {
        val thursday = at("2026-10-08T12:00:00+05:30")
        val spec = ReminderPlanner.digest(thursday, nine, emptySet(), quiet).single()
        assertEquals(ReminderKind.DIGEST, spec.kind)
        assertEquals(at("2026-10-12T09:00:00+05:30"), spec.triggerAt)
    }

    @Test fun aMondayWhoseTimeHasPassedOrWasSentWaitsAWeek() {
        val mondayEvening = at("2026-10-12T20:00:00+05:30")
        assertEquals(d("2026-10-19"), ReminderPlanner.digest(mondayEvening, nine, emptySet(), quiet).single().eventDate)
        val sent = setOf(ReminderPlanner.key(ReminderKind.DIGEST, 0, d("2026-10-12")))
        assertEquals(d("2026-10-19"), ReminderPlanner.digest(at("2026-10-12T07:00:00+05:30"), nine, sent, quiet).single().eventDate)
    }

    @Test fun digestTextSummarisesTheWeek() {
        assertEquals("Nothing is due this week.", quiet.text())
        assertEquals("This week: 2 renewals, 1 EMI, 1 bill.", DigestSummary(2, 1, 1, 0, emptyList()).text())
        assertEquals(
            "This week: 1 item expiring. Buy: rice, soap, milk and 2 more.",
            DigestSummary(0, 0, 0, 1, listOf("rice", "soap", "milk", "eggs", "salt")).text(),
        )
    }

    @Test fun billValidatorRules() {
        assertTrue(BillValidator.validate(bill).isEmpty())
        assertEquals(listOf("name"), BillValidator.validate(bill.copy(name = " ")).map { it.field })
        assertEquals(listOf("customDays"), BillValidator.validate(bill.copy(cycle = Cycle.CUSTOM_DAYS, customDays = null)).map { it.field })
        assertEquals(listOf("extraRemind"), BillValidator.validate(bill.copy(extraRemindDays = listOf(45))).map { it.field })
    }
}
