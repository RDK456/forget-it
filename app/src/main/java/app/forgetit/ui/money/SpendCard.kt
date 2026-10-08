package app.forgetit.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.forgetit.domain.BudgetLevel
import app.forgetit.domain.Money
import app.forgetit.domain.MonthSummary
import app.forgetit.domain.SpendStats
import app.forgetit.ui.AnimatedMoney
import app.forgetit.ui.AnimatedProgress
import app.forgetit.ui.pressScale
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** Overview card: what was spent this month, how it sits against the budget, and where most of it went. Tap to open Money. */
@Composable
fun SpendCard(summary: MonthSummary, budgetMinor: Long, currency: String, today: LocalDate, onClick: () -> Unit) {
    val level = SpendStats.level(summary.spentMinor, budgetMinor)
    val color = when (level) {
        BudgetLevel.OK -> MaterialTheme.colorScheme.primary
        BudgetLevel.WARN -> Color(0xFFE09A00)
        BudgetLevel.OVER -> MaterialTheme.colorScheme.error
    }
    OutlinedCard(Modifier.fillMaxWidth().pressScale(onClick)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Spent in " + YearMonth.from(today).month.getDisplayName(TextStyle.FULL, Locale.getDefault()), style = MaterialTheme.typography.labelLarge)
                    AnimatedMoney(summary.spentMinor, currency, MaterialTheme.typography.headlineSmall)
                }
                if (summary.incomeMinor > 0) Column(horizontalAlignment = Alignment.End) {
                    Text("Income", style = MaterialTheme.typography.labelMedium)
                    Text(Money.format(summary.incomeMinor, currency), style = MaterialTheme.typography.titleSmall)
                }
            }
            if (budgetMinor > 0) {
                AnimatedProgress((summary.spentMinor.toFloat() / budgetMinor).coerceIn(0f, 1f), Modifier.fillMaxWidth(), color)
                Text(
                    if (summary.spentMinor > budgetMinor) "Over budget by ${Money.format(summary.spentMinor - budgetMinor, currency)}"
                    else "${Money.format(budgetMinor - summary.spentMinor, currency)} left, about ${Money.format(SpendStats.dailyAllowance(budgetMinor, summary.spentMinor, today), currency)} a day",
                    style = MaterialTheme.typography.bodyMedium, color = color,
                )
            } else {
                Text("No budget yet. Tap to set one and see what is left each day.", style = MaterialTheme.typography.bodySmall)
            }
            val top = summary.byCategory.take(3)
            if (top.isEmpty()) {
                Text("Nothing spent yet. Add a transaction or let Forget-it scan your messages.", style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Highest: ${top.first().category} (${top.first().percent}%)", style = MaterialTheme.typography.titleSmall)
                top.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTile(c.category, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.category, style = MaterialTheme.typography.bodySmall)
                            AnimatedProgress(c.percent / 100f, Modifier.fillMaxWidth(), txnCategoryColor(c.category))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(Money.format(c.minor, currency), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}
