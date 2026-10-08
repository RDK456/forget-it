package app.forgetit.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryScanTest {
    private val receipt = """
        FRESH MART
        Date 08/10/2026
        MILK 1L          2.49
        2 x Bananas      1.20
        Basmati Rice 5kg 450.00
        Dettol Soap      3.10
        SUBTOTAL         7.00
        TAX              0.50
        TOTAL            7.50
        Thank you
    """.trimIndent()

    @Test fun receiptLinesBecomeItemsWithQuantityUnitCategoryAndPrice() {
        val items = GroceryScan.fromReceipt(receipt, "USD").associateBy { it.name }
        assertEquals(setOf("Milk", "Bananas", "Basmati Rice", "Dettol Soap"), items.keys)
        items.getValue("Milk").let { assertEquals(1000L, it.quantityMilli); assertEquals("L", it.unit); assertEquals("Dairy", it.category); assertEquals(249L, it.priceMinor) }
        items.getValue("Bananas").let { assertEquals(2000L, it.quantityMilli); assertEquals("pcs", it.unit); assertEquals("Produce", it.category) }
        items.getValue("Basmati Rice").let { assertEquals(5000L, it.quantityMilli); assertEquals("kg", it.unit); assertEquals("Grocery", it.category) }
        assertEquals("Toiletries", items.getValue("Dettol Soap").category)
    }

    @Test fun gramsAndMillilitresConvertToKgAndLitres() {
        val i = GroceryScan.fromReceipt("Sugar 500g 22.00\nCooking Oil 250 ml 3.00", "USD").associateBy { it.name }
        assertEquals(500L, i.getValue("Sugar").quantityMilli)
        assertEquals("kg", i.getValue("Sugar").unit)
        assertEquals(250L, i.getValue("Cooking Oil").quantityMilli)
        assertEquals("L", i.getValue("Cooking Oil").unit)
    }

    @Test fun headersTotalsAndGarbageAreDropped() {
        assertTrue(GroceryScan.fromReceipt("TOTAL 10.00\n****\n12\nVisa 1234", "USD").isEmpty())
        assertTrue(GroceryScan.fromReceipt("", "USD").isEmpty())
    }

    @Test fun categoriesByKeyword() {
        assertEquals("Dairy", GroceryScan.categoryOf("Fresh Paneer"))
        assertEquals("Medicines", GroceryScan.categoryOf("Paracetamol tablets"))
        assertEquals("Pet", GroceryScan.categoryOf("Dog food"))
        assertEquals("Grocery", GroceryScan.categoryOf("Something odd"))
    }

    @Test fun onlyConfidentSpecificLabelsAreKept() {
        val l = GroceryScan.fromLabels(listOf("Food" to 0.95f, "Banana" to 0.9f, "Apple" to 0.4f, "Milk" to 0.8f, "banana" to 0.7f))
        assertEquals(listOf("Banana", "Milk"), l.map { it.name })
        assertEquals("L", l.last().unit)
        assertNull(l.first().priceMinor)
    }

    @Test fun aRealReceiptBeatsLabelsButASparseOneIsTopedUp() {
        val full = GroceryScan.fromReceipt(receipt, "USD")
        val labels = GroceryScan.fromLabels(listOf("Apple" to 0.9f))
        assertEquals(full, GroceryScan.combine(full, labels))
        assertEquals(listOf("Apple"), GroceryScan.combine(emptyList(), labels).map { it.name })
    }
}
