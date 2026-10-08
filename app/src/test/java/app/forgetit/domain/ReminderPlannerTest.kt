package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(s: String) = ZonedDateTime.parse(s + "[Asia/Kolkata]")
    private val nineAm = 9 * 60

    @Test fun remindsLeadDaysBeforeAtChosenTime() {
        val s = sub(start = "2026-10-20", remind = 2)
        val specs = ReminderPlanner.subscriptions(listOf(s), at("2026-10-08T12:00:00+05:30"), nineAm, emptySet())
        assertEquals(1, specs.size)
        assertEquals(ReminderKind.RENEWAL, specs[0].kind)
        assertEquals(d("2026-10-20"), specs[0].eventDate)
        assertEquals(at("2026-10-18T09:00:00+05:30"), specs[0].triggerAt)
    }

    @Test fun inactiveSubscriptionsGetNoReminder() {
        val specs = ReminderPlanner.subscriptions(listOf(sub(active = false)), at("2026-10-08T12:00:00+05:30"), nineAm, emptySet())
        assertTrue(specs.isEmpty())
    }

    @Test fun alreadyNotifiedChargeMovesToTheNextOne() {
        val s = sub(start = "2026-10-20", remind = 2)
        val done = setOf(ReminderPlanner.key(ReminderKind.RENEWAL, s.id, d("2026-10-20")))
        val specs = ReminderPlanner.subscriptions(listOf(s), at("2026-10-08T12:00:00+05:30"), nineAm, done)
        assertEquals(d("2026-11-20"), specs.single().eventDate)
        assertEquals(at("2026-11-18T09:00:00+05:30"), specs.single().triggerAt)
    }

    @Test fun missedTriggerFiresAMinuteFromNowWhileChargeIsAhead() {
        val now = at("2026-10-19T12:00:00+05:30")
        val spec = ReminderPlanner.subscriptions(listOf(sub(start = "2026-10-20", remind = 2)), now, nineAm, emptySet()).single()
        assertEquals(now.plusMinutes(1), spec.triggerAt)
    }

    @Test fun trialEndUsesTrialKind() {
        val s = sub(start = "2026-10-01", trialEnds = "2026-10-20", remind = 3)
        val spec = ReminderPlanner.subscriptions(listOf(s), at("2026-10-08T12:00:00+05:30"), nineAm, emptySet()).single()
        assertEquals(ReminderKind.TRIAL_END, spec.kind)
        assertEquals(at("2026-10-17T09:00:00+05:30"), spec.triggerAt)
    }

    @Test fun zeroLeadRemindsOnTheDay() {
        val spec = ReminderPlanner.subscriptions(listOf(sub(start = "2026-10-20", remind = 0)), at("2026-10-08T12:00:00+05:30"), nineAm, emptySet()).single()
        assertEquals(at("2026-10-20T09:00:00+05:30"), spec.triggerAt)
    }

    @Test fun deletedOrPausedRecordNeverMatchesAFiredKey() {
        val s = sub(start = "2026-10-20")
        val key = ReminderPlanner.subscriptions(listOf(s), at("2026-10-08T12:00:00+05:30"), nineAm, emptySet()).single().key
        val after = ReminderPlanner.subscriptions(emptyList(), at("2026-10-18T09:00:00+05:30"), nineAm, emptySet())
        assertNull(ReminderPlanner.find(after, key))
        val paused = ReminderPlanner.subscriptions(listOf(s.copy(active = false)), at("2026-10-18T09:00:00+05:30"), nineAm, emptySet())
        assertNull(ReminderPlanner.find(paused, key))
    }

    @Test fun firedKeyIsNotShownTwice() {
        val s = sub(start = "2026-10-20")
        val first = ReminderPlanner.subscriptions(listOf(s), at("2026-10-08T12:00:00+05:30"), nineAm, emptySet()).single()
        val again = ReminderPlanner.subscriptions(listOf(s), at("2026-10-18T09:05:00+05:30"), nineAm, setOf(first.key))
        assertNull(ReminderPlanner.find(again, first.key))
    }
}
