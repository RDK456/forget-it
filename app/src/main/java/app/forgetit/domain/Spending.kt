package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

object TxnCategories {
    val EXPENSE = listOf(
        "Food and dining", "Groceries", "Transport", "Shopping", "Bills and utilities", "Subscriptions", "EMI and loans",
        "Health", "Entertainment", "Education", "Travel", "Rent and home", "Personal care", "Other",
    )
    val INCOME = listOf("Salary", "Refund", "Interest", "Gift", "Other income")

    fun forDirection(d: TxnDirection) = if (d == TxnDirection.CREDIT) INCOME else EXPENSE
}

/** Puts a payment into a spending category from the merchant and message text. A rule the user taught always wins. */
object Categorizer {
    private val EXPENSE_WORDS: List<Pair<String, List<String>>> = listOf(
        "EMI and loans" to listOf("emi", "loan", "instalment", "installment", "bajaj finance", "home credit"),
        "Subscriptions" to listOf("netflix", "spotify", "prime", "hotstar", "youtube", "disney", "hulu", "apple music", "icloud", "google one", "chatgpt", "subscription", "membership", "renewal", "audible"),
        "Bills and utilities" to listOf("electric", "power", "water", "gas", "broadband", "airtel", "jio", "vodafone", "bsnl", "recharge", "dth", "tata play", "postpaid", "bescom", "utility", "bill pay", "insurance", "premium"),
        "Food and dining" to listOf("swiggy", "zomato", "restaurant", "cafe", "coffee", "starbucks", "mcdonald", "kfc", "domino", "pizza", "burger", "bakery", "dining", "eats", "chai", "biryani"),
        "Groceries" to listOf("bigbasket", "blinkit", "zepto", "instamart", "dmart", "grocery", "supermarket", "kirana", "vegetable", "fruit", "milk", "fresh"),
        "Transport" to listOf("uber", "ola", "rapido", "metro", "fuel", "petrol", "diesel", "indian oil", "bharat petroleum", "fastag", "parking", "toll", "cab", "taxi", "bus"),
        "Travel" to listOf("irctc", "makemytrip", "goibibo", "airline", "indigo", "air india", "hotel", "oyo", "booking", "flight", "train", "airbnb", "ixigo"),
        "Health" to listOf("pharmacy", "medical", "hospital", "clinic", "apollo", "1mg", "pharmeasy", "doctor", "diagnostic", "dental", "medicine"),
        "Entertainment" to listOf("bookmyshow", "pvr", "inox", "cinema", "movie", "steam", "playstation", "xbox", "gaming"),
        "Education" to listOf("school", "college", "tuition", "course", "udemy", "coursera", "byju", "fees", "university"),
        "Rent and home" to listOf("rent", "maintenance", "society", "furniture", "ikea", "repair", "plumber", "electrician"),
        "Personal care" to listOf("salon", "spa", "gym", "cult", "barber", "parlour", "beauty"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "mall", "store", "shop", "mart"),
    )
    private val INCOME_WORDS: List<Pair<String, List<String>>> = listOf(
        "Salary" to listOf("salary", "payroll", "wages", "stipend"),
        "Refund" to listOf("refund", "reversal", "cashback", "reversed"),
        "Interest" to listOf("interest", "dividend"),
        "Gift" to listOf("gift"),
    )

    fun ruleKey(merchant: String?) = merchant.orEmpty().lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun categorize(merchant: String?, text: String, direction: TxnDirection, rules: Map<String, String> = emptyMap()): String {
        val key = ruleKey(merchant)
        if (key.isNotEmpty()) rules[key]?.let { return it }
        val hay = (merchant.orEmpty() + " " + text).lowercase(Locale.ROOT)
        // Short words must be a whole word ("bus" is not "business"); longer ones may continue ("restaurants").
        fun has(w: String) = Regex("""(^|[^a-z0-9])${Regex.escape(w)}""" + (if (w.length <= 4) """($|[^a-z0-9])""" else "")).containsMatchIn(hay)
        if (direction == TxnDirection.CREDIT) return INCOME_WORDS.firstOrNull { (_, ws) -> ws.any(::has) }?.first ?: "Other income"
        return EXPENSE_WORDS.firstOrNull { (_, ws) -> ws.any(::has) }?.first ?: "Other"
    }
}

/** The category a transaction is shown under: the one saved on it, or a guess from its text. */
fun Txn.categoryOr(rules: Map<String, String> = emptyMap()): String =
    category.ifBlank { Categorizer.categorize(merchant, snippet.ifBlank { note }, direction, rules) }

data class CategorySpend(val category: String, val minor: Long, val percent: Int, val previousMinor: Long)

data class MonthSummary(
    val month: YearMonth,
    val spentMinor: Long,
    val incomeMinor: Long,
    val byCategory: List<CategorySpend>,
    val excluded: Int,
) {
    val netMinor get() = incomeMinor - spentMinor
    val top: CategorySpend? get() = byCategory.firstOrNull()
}

enum class BudgetLevel { OK, WARN, OVER }

object SpendStats {
    private fun inMonth(t: Txn, m: YearMonth) = YearMonth.from(t.date) == m

    /** Money moved in [month] in [currency]; payments in a currency with no exchange rate are counted in [MonthSummary.excluded]. */
    fun summary(
        txns: List<Txn>, month: YearMonth, currency: String, rates: Map<String, BigDecimal>, rules: Map<String, String> = emptyMap(),
    ): MonthSummary {
        var excluded = 0
        fun amount(t: Txn): Long? = convertMinor(BigDecimal(t.amountMinor), t.currency, currency, rates)?.let { Cost.round(it) }
        fun spendBy(m: YearMonth): Map<String, Long> {
            val out = mutableMapOf<String, Long>()
            for (t in txns) {
                if (t.status == "IGNORED" || t.direction != TxnDirection.DEBIT || !inMonth(t, m)) continue
                val a = amount(t)
                if (a == null) { if (m == month) excluded++ } else out.merge(t.categoryOr(rules), a, Long::plus)
            }
            return out
        }
        val now = spendBy(month)
        val before = spendBy(month.minusMonths(1))
        val spent = now.values.sum()
        val income = txns.filter { it.status != "IGNORED" && it.direction == TxnDirection.CREDIT && inMonth(it, month) }
            .sumOf { amount(it) ?: 0L }
        val rows = now.entries.sortedByDescending { it.value }.map { (c, v) ->
            CategorySpend(c, v, if (spent > 0) Math.round(v * 100.0 / spent).toInt() else 0, before[c] ?: 0)
        }
        return MonthSummary(month, spent, income, rows, excluded)
    }

    /** Total spent in each of the last [months] months ending at [end], oldest first. */
    fun trend(txns: List<Txn>, end: YearMonth, months: Int, currency: String, rates: Map<String, BigDecimal>, rules: Map<String, String> = emptyMap()): List<Pair<YearMonth, Long>> =
        (months - 1 downTo 0).map { i -> end.minusMonths(i.toLong()).let { m -> m to summary(txns, m, currency, rates, rules).spentMinor } }

    /** What the month will cost at the pace so far. Past months return what was spent. */
    fun projected(spentMinor: Long, today: LocalDate, month: YearMonth): Long {
        val cur = YearMonth.from(today)
        if (month != cur) return spentMinor
        return Math.round(spentMinor.toDouble() / today.dayOfMonth * month.lengthOfMonth())
    }

    /** What can be spent each remaining day (today included) without passing [budgetMinor]; 0 once it is passed. */
    fun dailyAllowance(budgetMinor: Long, spentMinor: Long, today: LocalDate): Long {
        val left = budgetMinor - spentMinor
        val days = YearMonth.from(today).lengthOfMonth() - today.dayOfMonth + 1
        return if (left <= 0) 0 else left / days
    }

    fun level(spentMinor: Long, limitMinor: Long): BudgetLevel = when {
        limitMinor <= 0 -> BudgetLevel.OK
        spentMinor >= limitMinor -> BudgetLevel.OVER
        spentMinor * 100 >= limitMinor * 80 -> BudgetLevel.WARN
        else -> BudgetLevel.OK
    }
}
