package app.forgetit.reminders

import app.forgetit.AppContainer
import app.forgetit.domain.BudgetLevel
import app.forgetit.domain.Money
import app.forgetit.domain.SpendStats
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/** Warns once at 80 percent and once at 100 percent of the monthly budget and of each category limit. */
object BudgetAlerts {
    suspend fun check(c: AppContainer) {
        val s = c.settings.flow.first()
        val month = YearMonth.from(LocalDate.now(c.clock))
        val budgets = c.txns.observeBudgets().first()
        if (s.budgetMinor <= 0 && budgets.isEmpty()) return
        val summary = SpendStats.summary(
            c.txns.observeAll().first(), month, s.defaultCurrency, s.rates.mapValues { it.value.value }, c.txns.observeRules().first(),
        )
        val before = s.budgetAlerts
        val sent = before.filter { it.startsWith("$month:") }.toMutableSet()
        fun fire(key: String, name: String, spent: Long, limit: Long) {
            val level = SpendStats.level(spent, limit)
            if (level == BudgetLevel.OK) return
            val id = "$month:$key:${level.name}"
            if (id in sent) return
            val title = if (level == BudgetLevel.OVER) "Over your $name budget" else "80% of your $name budget used"
            Notifications.showInfo(
                c.context, 7300 + (key.hashCode() and 0xFF), title,
                "Spent ${Money.format(spent, s.defaultCurrency)} of ${Money.format(limit, s.defaultCurrency)} this month.", Notifications.CH_BUDGET,
            )
            sent += id
        }
        if (s.budgetMinor > 0) fire("all", "monthly", summary.spentMinor, s.budgetMinor)
        for ((category, limit) in budgets) fire(category, category, summary.byCategory.firstOrNull { it.category == category }?.minor ?: 0, limit)
        // Keeps only this month's marks, so old months do not pile up.
        if (sent != before) c.settings.setBudgetAlerts(sent)
    }
}
