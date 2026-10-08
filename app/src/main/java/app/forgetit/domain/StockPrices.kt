package app.forgetit.domain

import java.time.LocalDate

data class PricePoint(val date: LocalDate, val priceMinor: Long)

object StockPrices {
    /** Prices paid at each restock, oldest first. */
    fun history(logs: List<StockLog>): List<PricePoint> =
        logs.filter { it.kind == LogKind.RESTOCK && it.priceMinor != null }.sortedBy { it.date }.map { PricePoint(it.date, it.priceMinor!!) }

    /** Percent change of the latest price against the one before it; null with fewer than two prices. */
    fun trendPercent(history: List<PricePoint>): Int? {
        if (history.size < 2) return null
        val last = history.last().priceMinor
        val before = history[history.size - 2].priceMinor
        if (before <= 0) return null
        return Math.round((last - before) * 100.0 / before).toInt()
    }
}
