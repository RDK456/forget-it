package app.forgetit.domain

import java.math.BigDecimal
import java.time.LocalDate

data class CategorySlice(val label: String, val monthlyMinor: Long)

data class InsightsData(
    val slices: List<CategorySlice>,
    val monthlyMinor: Long,
    val yearlyMinor: Long,
    val perDayMinor: Long,
    val top: List<Pair<Subscription, Long>>,
    val activeCount: Int,
    val trialCount: Int,
    val excluded: Int,
)

/** [extraSlices] lets other trackers (loans) add their monthly outgo to the breakdown. */
fun computeInsights(
    subs: List<Subscription>, today: LocalDate, default: String, rates: Map<String, BigDecimal>,
    extraSlices: List<CategorySlice> = emptyList(),
): InsightsData {
    val active = subs.filter { it.active }
    val perSub = active.map { it to monthlyInDefault(it, today, default, rates) }
    val priced = perSub.mapNotNull { (s, m) -> m?.let { s to it } }
    val subTotals = computeTotals(subs, today, default, rates)
    val extra = extraSlices.sumOf { it.monthlyMinor }
    val slices = (priced.groupBy({ it.first.category }, { it.second })
        .map { (cat, list) -> CategorySlice(cat, Cost.round(list.fold(BigDecimal.ZERO, BigDecimal::add))) } + extraSlices)
        .filter { it.monthlyMinor > 0 }
        .sortedByDescending { it.monthlyMinor }
    val monthly = subTotals.monthlyMinor + extra
    val yearly = subTotals.yearlyMinor + extra * 12
    return InsightsData(
        slices = slices, monthlyMinor = monthly, yearlyMinor = yearly, perDayMinor = Math.round(yearly / 365.0),
        top = priced.map { (s, m) -> s to Cost.round(m) }.sortedByDescending { it.second }.take(3),
        activeCount = active.size, trialCount = active.count { it.isTrialActive(today) },
        excluded = subTotals.excluded,
    )
}
