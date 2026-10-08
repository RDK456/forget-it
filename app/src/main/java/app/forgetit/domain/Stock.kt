package app.forgetit.domain

import java.time.LocalDate

val STOCK_CATEGORIES = listOf("Dairy", "Produce", "Grocery", "Household", "Toiletries", "Other")

/** Quantities everywhere are thousandths of the unit (1 L = 1000). */
data class StockItem(
    val id: Long = 0,
    val name: String,
    val unit: String = "pcs",
    val category: String = "Grocery",
    val lowThresholdMilli: Long = 0,
    val dailyUsageMilli: Long? = null,
    val expiryAlertDays: Int = 2,
    val baselineDate: LocalDate,
    val notes: String = "",
    val active: Boolean = true,
)

data class StockBatch(val id: Long = 0, val itemId: Long, val quantityMilli: Long, val addedOn: LocalDate, val expiry: LocalDate? = null)

enum class LogKind { USED, RESTOCK, DISCARD, ADJUST }

data class StockLog(val id: Long = 0, val itemId: Long, val date: LocalDate, val deltaMilli: Long, val kind: LogKind)
