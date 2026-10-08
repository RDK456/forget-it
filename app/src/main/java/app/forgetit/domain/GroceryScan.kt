package app.forgetit.domain

import java.util.Locale

/** One line found in a grocery photo, ready to review and add to household stock. */
data class ScannedItem(
    val name: String,
    val quantityMilli: Long = 1000,
    val unit: String = "pcs",
    val category: String = "Grocery",
    val priceMinor: Long? = null,
)

/** Turns text read from a receipt, or labels recognised in a photo of groceries, into stock items. Pure logic, no Android. */
object GroceryScan {
    private val SKIP = Regex(
        """\b(total|subtotal|sub total|tax|gst|vat|cgst|sgst|change|cash|card|visa|mastercard|balance|thank|invoice|bill no|receipt|date|time|cashier|discount|saved|round|amount|qty|mrp|items?)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val PRICE = Regex("""(?<![\d.])(\d{1,6}[.,]\d{2})\s*[A-Za-z]?\s*$""")
    private val QTY_FIRST = Regex("""^\s*(\d{1,3})\s*[xX*]?\s+(?=[A-Za-z])""")
    private val SIZE = Regex("""(?<![\d.])(\d+(?:[.,]\d+)?)\s*(kg|kgs|g|gm|gms|ltr|lt|l|ml|pcs|pc|pack|dozen)\b""", RegexOption.IGNORE_CASE)
    private val TOKEN_CODE = Regex("""\b\d{4,}\b|\b[A-Z]{0,2}\d{3,}[A-Z]*\b""")

    private val DAIRY = listOf("milk", "curd", "yogurt", "yoghurt", "cheese", "butter", "paneer", "ghee", "cream", "egg", "eggs", "lassi", "buttermilk")
    private val PRODUCE = listOf(
        "banana", "apple", "onion", "potato", "tomato", "spinach", "carrot", "fruit", "vegetable", "mango", "orange", "lemon", "garlic", "ginger",
        "grape", "cucumber", "broccoli", "cabbage", "capsicum", "pepper", "coriander", "mint", "beans", "peas", "lettuce", "melon", "papaya", "berry",
    )
    private val HOUSEHOLD = listOf("detergent", "dishwash", "tissue", "foil", "garbage", "bin bag", "cleaner", "bleach", "phenyl", "mop", "sponge", "napkin", "matchbox", "candle")
    private val TOILETRIES = listOf("shampoo", "toothpaste", "toothbrush", "soap", "handwash", "lotion", "razor", "deodorant", "conditioner", "sanitary", "facewash")
    private val MEDICINE = listOf("tablet", "paracetamol", "syrup", "capsule", "ointment", "bandage", "vitamin")
    private val PET = listOf("dog", "cat food", "pet", "kibble")
    private val LIQUID = listOf("milk", "oil", "juice", "water", "vinegar", "buttermilk", "lassi", "syrup")
    private val BULK = listOf("rice", "atta", "flour", "dal", "sugar", "salt", "wheat", "lentil", "rava", "sooji", "poha", "oats")

    fun categoryOf(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        fun has(words: List<String>) = words.any { Regex("""\b${Regex.escape(it)}s?\b""").containsMatchIn(n) }
        return when {
            has(MEDICINE) -> "Medicines"
            has(TOILETRIES) -> "Toiletries"
            has(HOUSEHOLD) -> "Household"
            has(PET) -> "Pet"
            has(DAIRY) -> "Dairy"
            has(PRODUCE) -> "Produce"
            else -> "Grocery"
        }
    }

    private fun defaultUnit(name: String): String {
        val n = name.lowercase(Locale.ROOT)
        fun has(words: List<String>) = words.any { Regex("""\b${Regex.escape(it)}s?\b""").containsMatchIn(n) }
        return when {
            has(LIQUID) -> "L"
            has(BULK) -> "kg"
            else -> "pcs"
        }
    }

    /** "500 g" becomes 0.5 kg and "250 ml" becomes 0.25 L, so units stay in the ones the stock screens use. */
    private fun sizeToQuantity(value: Double, unit: String): Pair<Long, String> = when (unit.lowercase(Locale.ROOT)) {
        "kg", "kgs" -> Math.round(value * 1000) to "kg"
        "g", "gm", "gms" -> Math.round(value) to "kg"
        "l", "lt", "ltr" -> Math.round(value * 1000) to "L"
        "ml" -> Math.round(value) to "L"
        "dozen" -> Math.round(value * 12_000) to "pcs"
        else -> Math.round(value * 1000) to "pcs"
    }

    private fun titleCase(s: String) = s.lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
        .joinToString(" ") { w -> w.replaceFirstChar { it.uppercase() } }

    /** Reads receipt text line by line. Lines without a recognisable product name, and total or tax lines, are dropped. */
    fun fromReceipt(text: String, currency: String): List<ScannedItem> {
        val out = mutableListOf<ScannedItem>()
        for (raw in text.lines()) {
            var line = raw.trim()
            if (line.length < 3 || SKIP.containsMatchIn(line)) continue
            val price = PRICE.find(line)?.let { m ->
                line = line.removeRange(m.range).trim()
                Money.parseMinor(m.groupValues[1].replace(',', '.'), currency)
            }
            var quantity: Long? = null
            var unit: String? = null
            QTY_FIRST.find(line)?.let { m ->
                quantity = m.groupValues[1].toLong() * 1000
                unit = "pcs"
                line = line.removeRange(m.range).trim()
            }
            SIZE.find(line)?.let { m ->
                if (quantity == null) {
                    val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
                    if (v != null && v > 0) sizeToQuantity(v, m.groupValues[2]).let { (q, u) -> quantity = q; unit = u }
                }
                line = line.removeRange(m.range).trim()
            }
            val name = titleCase(line.replace(TOKEN_CODE, " ").replace(Regex("""[^A-Za-z&' -]"""), " ").replace(Regex("""\s+"""), " ").trim())
            if (name.count { it.isLetter() } < 3 || (price == null && quantity == null)) continue
            val u = unit ?: defaultUnit(name)
            out += ScannedItem(name.take(60), quantity ?: 1000, u, categoryOf(name), price)
        }
        return out.distinctBy { it.name.lowercase(Locale.ROOT) }
    }

    private val LABEL_NAMES = mapOf(
        "banana" to "Banana", "apple" to "Apple", "orange" to "Orange", "tomato" to "Tomato", "potato" to "Potato", "onion" to "Onion",
        "carrot" to "Carrot", "broccoli" to "Broccoli", "cucumber" to "Cucumber", "lemon" to "Lemon", "mango" to "Mango", "grape" to "Grapes",
        "bread" to "Bread", "egg" to "Eggs", "milk" to "Milk", "cheese" to "Cheese", "butter" to "Butter", "yogurt" to "Yogurt",
        "rice" to "Rice", "pasta" to "Pasta", "juice" to "Juice", "cabbage" to "Cabbage", "garlic" to "Garlic", "strawberry" to "Strawberries",
        "pineapple" to "Pineapple", "watermelon" to "Watermelon", "capsicum" to "Capsicum", "bell pepper" to "Capsicum", "lettuce" to "Lettuce",
        "cereal" to "Cereal", "coffee" to "Coffee", "tea" to "Tea", "chocolate" to "Chocolate",
    )

    /** Keeps confident labels from an image labeller that name a specific grocery item. Generic labels such as Food are ignored. */
    fun fromLabels(labels: List<Pair<String, Float>>, minConfidence: Float = 0.6f): List<ScannedItem> =
        labels.filter { it.second >= minConfidence }
            .mapNotNull { LABEL_NAMES[it.first.lowercase(Locale.ROOT)] }
            .distinct()
            .map { n -> ScannedItem(n, 1000, defaultUnit(n).let { u -> if (u == "L" || u == "kg") u else "pcs" }, categoryOf(n)) }

    /** A receipt with several priced lines is trusted on its own; otherwise recognised items are added to whatever text was found. */
    fun combine(receipt: List<ScannedItem>, labels: List<ScannedItem>): List<ScannedItem> {
        if (receipt.size >= 2) return receipt
        return (receipt + labels).distinctBy { it.name.lowercase(Locale.ROOT) }
    }
}
