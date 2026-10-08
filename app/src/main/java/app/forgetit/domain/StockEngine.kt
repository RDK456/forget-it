package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class UseResult(val remaining: List<StockBatch>, val removedIds: List<Long>, val consumedMilli: Long, val shortfallMilli: Long)

data class StockStatus(
    val storedMilli: Long,
    val estimatedMilli: Long,
    val ratePerDayMilli: BigDecimal?,
    val runOut: LocalDate?,
    val low: Boolean,
    val expiredBatches: List<StockBatch>,
    val nextExpiry: LocalDate?,
)

object StockEngine {
    const val AUTO_WINDOW_DAYS = 30L
    const val MIN_AUTO_LOGS = 2
    const val MIN_AUTO_SPAN_DAYS = 7L
    const val LOW_WARN_DAYS = 2L

    fun stored(batches: List<StockBatch>): Long = batches.sumOf { it.quantityMilli }

    /** Milli-units per day: the manual rate, else the recent average from USED logs when there is enough history. */
    fun usageRate(item: StockItem, logs: List<StockLog>, today: LocalDate): BigDecimal? {
        item.dailyUsageMilli?.let { return BigDecimal(it) }
        val used = logs.filter { it.kind == LogKind.USED }
        if (used.size < MIN_AUTO_LOGS) return null
        val first = used.minOf { it.date }
        val last = used.maxOf { it.date }
        if (ChronoUnit.DAYS.between(first, last) < MIN_AUTO_SPAN_DAYS) return null
        val observed = minOf(AUTO_WINDOW_DAYS, ChronoUnit.DAYS.between(first, today)).coerceAtLeast(1)
        val total = used.filter { it.date.isAfter(today.minusDays(AUTO_WINDOW_DAYS)) }.sumOf { -it.deltaMilli }
        return BigDecimal(total).divide(BigDecimal(observed), Cost.MC)
    }

    fun estimated(item: StockItem, storedMilli: Long, rate: BigDecimal?, today: LocalDate): Long {
        if (rate == null) return storedMilli
        val days = ChronoUnit.DAYS.between(item.baselineDate, today).coerceAtLeast(0)
        val left = BigDecimal(storedMilli).subtract(rate.multiply(BigDecimal(days)))
        return left.max(BigDecimal.ZERO).toLong()
    }

    fun runOut(estimatedMilli: Long, rate: BigDecimal?, today: LocalDate): LocalDate? {
        if (rate == null || rate.signum() <= 0) return null
        val days = BigDecimal(estimatedMilli).divide(rate, 0, java.math.RoundingMode.FLOOR).toLong()
        return today.plusDays(days)
    }

    fun isLow(item: StockItem, estimatedMilli: Long, runOut: LocalDate?, today: LocalDate): Boolean =
        estimatedMilli <= item.lowThresholdMilli || (runOut != null && !runOut.isAfter(today.plusDays(LOW_WARN_DAYS)))

    /** Date the estimate drops to the threshold, for a reminder ahead of time; null without a usage rate. */
    fun crossesThresholdOn(item: StockItem, storedMilli: Long, rate: BigDecimal?): LocalDate? {
        if (rate == null || rate.signum() <= 0) return null
        val above = (storedMilli - item.lowThresholdMilli).coerceAtLeast(0)
        val days = BigDecimal(above).divide(rate, 0, java.math.RoundingMode.CEILING).toLong()
        return item.baselineDate.plusDays(days)
    }

    fun status(item: StockItem, batches: List<StockBatch>, logs: List<StockLog>, today: LocalDate): StockStatus {
        val stored = stored(batches)
        val rate = usageRate(item, logs, today)
        val est = estimated(item, stored, rate, today)
        val run = runOut(est, rate, today)
        val live = batches.filter { it.quantityMilli > 0 }
        return StockStatus(
            stored, est, rate, run, isLow(item, est, run, today),
            expiredBatches = live.filter { it.expiry != null && it.expiry.isBefore(today) },
            nextExpiry = live.mapNotNull { it.expiry }.filter { !it.isBefore(today) }.minOrNull(),
        )
    }

    /** Takes [amountMilli] from batches that expire first; expired batches are never used; no-expiry batches go last. */
    fun use(batches: List<StockBatch>, amountMilli: Long, today: LocalDate): UseResult {
        val order = batches.filter { it.expiry == null || !it.expiry.isBefore(today) }
            .sortedWith(compareBy<StockBatch, LocalDate?>(nullsLast()) { it.expiry }.thenBy { it.addedOn }.thenBy { it.id })
        var need = amountMilli
        val changed = mutableMapOf<Long, StockBatch>()
        val removed = mutableListOf<Long>()
        for (b in order) {
            if (need <= 0) break
            val take = minOf(need, b.quantityMilli)
            need -= take
            if (take == b.quantityMilli) removed += b.id else changed[b.id] = b.copy(quantityMilli = b.quantityMilli - take)
        }
        val remaining = batches.filter { it.id !in removed }.map { changed[it.id] ?: it }
        return UseResult(remaining, removed, amountMilli - need, need)
    }
}
