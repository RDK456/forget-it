package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorTest {
    private fun fields(s: Subscription) = Validator.validate(s).map { it.field }

    @Test fun validSubscriptionHasNoErrors() = assertTrue(Validator.validate(sub()).isEmpty())

    @Test fun nameRules() {
        assertEquals(listOf("name"), fields(sub().copy(name = "  ")))
        assertEquals(listOf("name"), fields(sub().copy(name = "x".repeat(61))))
    }

    @Test fun amountRules() {
        assertEquals(listOf("amount"), fields(sub(amount = -1)))
        assertEquals(listOf("amount"), fields(sub(amount = Validator.MAX_AMOUNT_MINOR + 1)))
        assertTrue(Validator.validate(sub(amount = 0)).isEmpty())
    }

    @Test fun currencyRule() = assertEquals(listOf("currency"), fields(sub(currency = "XX1")))

    @Test fun customDaysRules() {
        assertEquals(listOf("customDays"), fields(sub(Cycle.CUSTOM_DAYS, customDays = null)))
        assertEquals(listOf("customDays"), fields(sub(Cycle.CUSTOM_DAYS, customDays = 0)))
        assertEquals(listOf("customDays"), fields(sub(Cycle.CUSTOM_DAYS, customDays = 3651)))
        assertTrue(Validator.validate(sub(Cycle.CUSTOM_DAYS, customDays = 30)).isEmpty())
    }

    @Test fun trialRequiresEndDate() =
        assertEquals(listOf("trialEndsAt"), fields(sub().copy(isTrial = true, trialEndsAt = null)))

    @Test fun cancelUrlMustBeHttp() {
        assertEquals(listOf("cancelUrl"), fields(sub().copy(cancelUrl = "ftp://x.com")))
        assertEquals(listOf("cancelUrl"), fields(sub().copy(cancelUrl = "javascript:alert(1)")))
        assertEquals(listOf("cancelUrl"), fields(sub().copy(cancelUrl = "not a url")))
        assertTrue(Validator.validate(sub().copy(cancelUrl = "https://example.com/cancel")).isEmpty())
    }

    @Test fun reminderAndCategoryRules() {
        assertEquals(listOf("remindDaysBefore"), fields(sub(remind = 31)))
        assertEquals(listOf("remindDaysBefore"), fields(sub(remind = -1)))
        assertEquals(listOf("category"), fields(sub().copy(category = "Bogus")))
    }
}
