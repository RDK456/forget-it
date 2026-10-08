package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SheetImportTest {
    private val today = d("2026-10-08")

    @Test fun xlsxWriteThenReadKeepsEveryCell() {
        val rows = listOf(listOf("Name", "Amount", "Note"), listOf("Café & <Bar>", "15.49", "line\nbreak \"quoted\""), listOf("Rent", "1200", ""))
        val back = Xlsx.read(Xlsx.write(listOf(Sheet("One", rows), Sheet("Two", listOf(listOf("x", "y"))))))
        assertEquals(listOf("One", "Two"), back.map { it.name })
        assertEquals(rows[0], back[0].rows[0])
        assertEquals(rows[1], back[0].rows[1])
        assertEquals(listOf("Rent", "1200"), back[0].rows[2].take(2))
    }

    @Test fun xlsxFromExcelUsesSharedStringsAndCellRefs() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(n: String, t: String) { z.putNextEntry(ZipEntry(n)); z.write(t.toByteArray()); z.closeEntry() }
            put("xl/workbook.xml", """<workbook xmlns:r="x"><sheets><sheet name="Subscriptions" sheetId="1" r:id="rId1"/></sheets></workbook>""")
            put("xl/_rels/workbook.xml.rels", """<Relationships><Relationship Id="rId1" Target="worksheets/sheet1.xml"/></Relationships>""")
            put("xl/sharedStrings.xml", """<sst><si><t>Name</t></si><si><t>Amount</t></si><si><r><t>Net</t></r><r><t>flix</t></r></si></sst>""")
            put("xl/worksheets/sheet1.xml", """<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row><row r="2"><c r="A2" t="s"><v>2</v></c><c r="C2"><v>15.99</v></c></row></sheetData></worksheet>""")
        }
        val s = Xlsx.read(out.toByteArray()).single()
        assertEquals(listOf("Name", "", "Amount"), s.rows[0])
        assertEquals(listOf("Netflix", "", "15.99"), s.rows[1])
    }

    @Test fun notAnXlsxIsRejected() {
        val e = runCatching { Xlsx.read("hello".toByteArray()) }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
    }

    @Test fun sheetsAreSortedIntoTheirTrackers() {
        val r = SheetImport.parse(
            listOf(
                Sheet("Subscriptions", listOf(listOf("Name", "Amount", "Cycle", "Start date", "Category"), listOf("Spotify", "\$9.99", "monthly", "2026-11-01", "Music"))),
                Sheet("Loans", listOf(listOf("Name", "Principal", "Rate %", "Tenure (months)", "First EMI date", "EMI"), listOf("Car", "500,000", "9.5", "60", "46000", "10,500.00"))),
                Sheet("Bills", listOf(listOf("Name", "Bill type", "Usual amount", "Next due"), listOf("Power", "electricity", "84.2", "15/11/2026"))),
                Sheet("Stock", listOf(listOf("Item", "Qty", "Unit"), listOf("Milk", "2", "L"), listOf("Rice", "5", "kg"))),
                Sheet("How to use", listOf(listOf("anything"))),
            ),
            "USD", today,
        )
        assertTrue(r.skipped.isEmpty())
        val by = r.items.groupBy { it.kind }
        by.getValue(DocKind.SUBSCRIPTION).single().let { assertEquals(999L, it.amountMinor); assertEquals("Music", it.category); assertEquals(d("2026-11-01"), it.date) }
        by.getValue(DocKind.EMI).single().let {
            assertEquals(1_050_000L, it.amountMinor); assertEquals(50_000_000L, it.principalMinor); assertEquals(60, it.tenureMonths)
            assertEquals(BigDecimal("9.5"), it.ratePercent); assertEquals(d("2025-12-09"), it.date)
        }
        by.getValue(DocKind.BILL).single().let { assertEquals(BillType.ELECTRICITY, it.billType); assertEquals(8420L, it.amountMinor); assertEquals(d("2026-11-15"), it.date) }
        assertEquals(listOf("Milk", "Rice"), by.getValue(DocKind.GROCERY).single().items.map { it.name })
    }

    @Test fun badRowsAreSkippedWithAReason() {
        val r = SheetImport.parse(listOf(Sheet("Subs", listOf(listOf("Name", "Amount"), listOf("A", ""), listOf("", "5"), listOf("OK", "3")))), "USD", today)
        assertEquals(1, r.items.size)
        assertEquals(2, r.skipped.size)
        assertTrue(r.skipped.any { "no amount" in it })
    }

    @Test fun csvFileWorksToo() {
        val r = SheetImport.fromBytes("﻿Name,Amount,Type\nNetflix,15.49,subscription\nElectricity,80,bill\n".toByteArray(), "my.csv", "USD", today)
        assertEquals(setOf(DocKind.SUBSCRIPTION, DocKind.BILL), r.items.map { it.kind }.toSet())
    }

    @Test fun oldXlsIsRefusedWithAdvice() {
        val e = runCatching { SheetImport.fromBytes(byteArrayOf(0xD0.toByte(), 0xCF.toByte(), 0x11, 0xE0.toByte(), 0), "a.xls", "USD", today) }.exceptionOrNull()
        assertTrue(e!!.message!!.contains(".xlsx"))
    }

    @Test fun exportedWorkbookImportsBackToTheSameRecords() {
        val sub = Subscription(id = 1, name = "Netflix", amountMinor = 1599, currency = "USD", cycle = Cycle.MONTHLY, startDate = d("2026-11-12"), category = "Streaming")
        val loan = Loan(id = 1, name = "Car", lender = "Bank", principalMinor = 100_000_00, currency = "USD", annualRatePercent = BigDecimal("10"), tenureMonths = 24, firstEmiDate = d("2026-11-05"))
        val bill = Bill(id = 1, name = "Water", type = BillType.WATER, currency = "USD", anchorDate = d("2026-11-10"))
        val item = StockItem(id = 1, name = "Milk", unit = "L", category = "Dairy", baselineDate = today)
        val sheets = SheetExport.workbook(listOf(sub), listOf(loan), listOf(bill), emptyList(), listOf(item), listOf(StockBatch(itemId = 1, quantityMilli = 2000, addedOn = today)), today, template = false)
        val r = SheetImport.fromBytes(Xlsx.write(sheets), "backup.xlsx", "USD", today)
        assertTrue(r.skipped.isEmpty())
        assertEquals(setOf(DocKind.SUBSCRIPTION, DocKind.EMI, DocKind.BILL, DocKind.GROCERY), r.items.map { it.kind }.toSet())
        assertEquals(1599L, r.items.first { it.kind == DocKind.SUBSCRIPTION }.amountMinor)
        assertEquals(24, r.items.first { it.kind == DocKind.EMI }.tenureMonths)
        assertEquals(BillType.WATER, r.items.first { it.kind == DocKind.BILL }.billType)
        assertEquals(2000L, r.items.first { it.kind == DocKind.GROCERY }.items.single().quantityMilli)
    }

    @Test fun templateHasOneExampleRowPerTracker() {
        val r = SheetImport.parse(SheetExport.workbook(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), today, template = true), "USD", today)
        assertEquals(4, r.items.size)
    }
}
