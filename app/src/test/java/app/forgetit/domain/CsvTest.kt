package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvTest {
    @Test fun parsesQuotesCommasNewlinesBomAndCrlf() {
        val text = "﻿a,b\r\n\"x,1\",\"he said \"\"hi\"\"\"\r\n\"line1\nline2\",z\r\n"
        assertEquals(listOf(listOf("a", "b"), listOf("x,1", "he said \"hi\""), listOf("line1\nline2", "z")), Csv.parse(text))
    }

    @Test fun lastRowWithoutTrailingNewlineAndEmptyCells() {
        assertEquals(listOf(listOf("a", "", "c")), Csv.parse("a,,c"))
        assertEquals(emptyList<List<String>>(), Csv.parse(""))
    }

    @Test fun guardsFormulaCellsAndUndoesIt() {
        for (s in listOf("=SUM(A1)", "+1", "-2", "@cmd", "\tx")) {
            val g = Csv.guard(s)
            assertTrue(g.startsWith("'"))
            assertEquals(s, Csv.unguard(g))
        }
        assertEquals("plain", Csv.guard("plain"))
        assertEquals("it's", Csv.unguard("it's"))
    }

    @Test fun subscriptionsRoundTripWithAwkwardText() {
        val s = sub(amount = 1599, currency = "USD").copy(
            name = "=HYPERLINK(\"x\")", notes = "a, b\nc \"q\"", cancelUrl = "https://example.com/x?a=1,2",
            category = "Streaming", isTrial = true, trialEndsAt = d("2026-11-01"), remindDaysBefore = 3, active = false,
        )
        val back = SubscriptionCsv.import(SubscriptionCsv.export(listOf(s)))
        assertTrue(back.errors.toString(), back.errors.isEmpty())
        assertEquals(s.copy(id = 0), back.items.single())
    }

    @Test fun zeroDecimalCurrencyRoundTrips() {
        val s = sub(amount = 500, currency = "JPY")
        assertEquals(500L, SubscriptionCsv.import(SubscriptionCsv.export(listOf(s))).items.single().amountMinor)
    }

    @Test fun badRowsAreReportedGoodRowsKept() {
        val csv = "name,amount,currency,cycle,start_date\r\nGood,9.99,USD,MONTHLY,2026-01-01\r\nBad,abc,USD,MONTHLY,2026-01-01\r\n" +
            "Worse,5,USD,SOMETIMES,2026-01-01\r\nDate,5,USD,MONTHLY,31-01-2026\r\n"
        val r = SubscriptionCsv.import(csv)
        assertEquals(listOf("Good"), r.items.map { it.name })
        assertEquals(listOf("Row 3: invalid amount", "Row 4: unknown cycle", "Row 5: invalid start_date"), r.errors)
    }

    @Test fun missingColumnsAreExplained() {
        assertEquals(listOf("Missing columns: amount, currency, cycle, start_date"), SubscriptionCsv.import("name\r\nx\r\n").errors)
    }
}
