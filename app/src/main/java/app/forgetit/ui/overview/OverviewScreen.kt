package app.forgetit.ui.overview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Amortization
import app.forgetit.domain.Money
import app.forgetit.domain.Renewal
import app.forgetit.domain.StockEngine
import app.forgetit.ui.stock.qty
import app.forgetit.domain.RowStatus
import app.forgetit.domain.Totals
import app.forgetit.domain.computeTotals
import app.forgetit.domain.isTrialActive
import app.forgetit.domain.loanMonthlyOutgo
import app.forgetit.ui.Avatar
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.TotalsCard
import app.forgetit.ui.relativeDay
import java.io.File
import java.time.LocalDate

/** One line in the coming-up list, whatever tracker it comes from. */
data class Upcoming(
    val key: String, val title: String, val detail: String, val date: LocalDate,
    val amount: String?, val photo: File?, val overdue: Boolean = false,
)

const val UPCOMING_DAYS = 14L

@Composable
fun OverviewScreen(vm: MainViewModel, today: LocalDate) {
    val subs by vm.subs.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val pay by vm.loanPayments.collectAsStateWithLifecycle()
    val adj by vm.loanAdjustments.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val stockItems by vm.stockItems.collectAsStateWithLifecycle()
    val stockBatches by vm.stockBatches.collectAsStateWithLifecycle()
    val stockLogs by vm.stockLogs.collectAsStateWithLifecycle()
    val rates = settings.rates.mapValues { it.value.value }
    val cur = settings.defaultCurrency
    val subTotals = computeTotals(subs, today, cur, rates)
    val emi = loanMonthlyOutgo(loans, adj, pay, today, cur, rates)
    val totals = Totals(subTotals.monthlyMinor + emi.monthlyMinor, subTotals.yearlyMinor + emi.monthlyMinor * 12, subTotals.excluded + emi.excluded)
    val horizon = today.plusDays(UPCOMING_DAYS)

    val subItems = subs.filter { it.active }.mapNotNull { s ->
        val next = Renewal.next(s, today)
        if (next.isAfter(horizon)) return@mapNotNull null
        Upcoming(
            "sub:${s.id}", s.name, if (s.isTrialActive(today)) "Free trial ends - charge starts" else "Renews",
            next, Money.format(s.amountMinor, s.currency), covers["SUBSCRIPTION:${s.id}"],
        )
    }
    val emiItems = loans.filter { it.active }.mapNotNull { l ->
        val row = Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today).nextDue ?: return@mapNotNull null
        if (row.dueDate.isAfter(horizon)) return@mapNotNull null
        Upcoming(
            "emi:${l.id}", l.name, "EMI ${row.no}", row.dueDate, Money.format(row.paymentMinor, l.currency),
            covers["LOAN:${l.id}"], overdue = row.status == RowStatus.OVERDUE,
        )
    }
    val stockUp = stockItems.filter { it.active }.flatMap { i ->
        val mine = stockBatches.filter { it.itemId == i.id }
        val myLogs = stockLogs.filter { it.itemId == i.id }
        val st = StockEngine.status(i, mine, myLogs, today)
        val cover = covers["STOCK_ITEM:${i.id}"]
        buildList {
            if (st.low && (mine.isNotEmpty() || myLogs.isNotEmpty())) {
                add(Upcoming("low:${i.id}", i.name, "Running low", today, qty(st.estimatedMilli, i.unit), cover, overdue = true))
            }
            mine.filter { it.quantityMilli > 0 && it.expiry != null && !it.expiry.isAfter(horizon) }.forEach { b ->
                val e = b.expiry!!
                add(Upcoming("exp:${b.id}", i.name, if (e.isBefore(today)) "Expired" else "Expires", e, qty(b.quantityMilli, i.unit), cover, overdue = e.isBefore(today)))
            }
        }
    }
    val upcoming = (subItems + emiItems + stockUp).sortedBy { it.date }

    ScreenScaffold("Overview", onBack = null) { pad ->
        ListScreen(pad) {
            item { TotalsCard("Monthly outgo (subscriptions + EMIs)", totals, cur) }
            item { Text("Coming up in $UPCOMING_DAYS days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (upcoming.isEmpty()) item { Text("Nothing due soon.", style = MaterialTheme.typography.bodyMedium) }
            items(upcoming, key = { it.key }) { u ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(u.title, u.photo)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(u.title, style = MaterialTheme.typography.titleMedium)
                            Text(u.detail, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (u.amount != null) Text(u.amount, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (u.overdue) "Overdue - " + relativeDay(u.date, today) else relativeDay(u.date, today),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (u.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
