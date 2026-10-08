package app.forgetit.ui.overview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Amortization
import app.forgetit.domain.BillMath
import app.forgetit.domain.billMonthlyOutgo
import app.forgetit.ui.billIcon
import app.forgetit.ui.theme.domainColors
import app.forgetit.domain.Money
import app.forgetit.domain.Renewal
import app.forgetit.domain.RowStatus
import app.forgetit.domain.StockEngine
import app.forgetit.domain.Totals
import app.forgetit.domain.computeTotals
import app.forgetit.domain.isTrialActive
import app.forgetit.domain.loanMonthlyOutgo
import app.forgetit.ui.Avatar
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.Mood
import app.forgetit.ui.OutgoHero
import app.forgetit.ui.RemyHeader
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.categoryColor
import app.forgetit.ui.categoryIcon
import app.forgetit.ui.enterStagger
import app.forgetit.ui.loanIcon
import app.forgetit.ui.pulse
import app.forgetit.ui.relativeDay
import app.forgetit.ui.stock.qty
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

/** One line in the coming-up list, whatever tracker it comes from. */
data class Upcoming(
    val key: String, val title: String, val detail: String, val date: LocalDate, val amount: String?, val photo: File?,
    val overdue: Boolean = false, val icon: ImageVector? = null, val tint: Color? = null,
    val emiLoanId: Long? = null, val emiNo: Int = 0, val emiMinor: Long = 0, val trialSoon: Boolean = false,
)

const val UPCOMING_DAYS = 14L
private val LOAN_TINT = Color(0xFF5560E0)

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
    val bills by vm.bills.collectAsStateWithLifecycle()
    val billEntries by vm.billEntries.collectAsStateWithLifecycle()
    val billTint = domainColors.bill
    val haptic = LocalHapticFeedback.current

    val rates = settings.rates.mapValues { it.value.value }
    val cur = settings.defaultCurrency
    val subTotals = computeTotals(subs, today, cur, rates)
    val emi = loanMonthlyOutgo(loans, adj, pay, today, cur, rates)
    val billOut = billMonthlyOutgo(bills, billEntries, cur, rates)
    val monthlyAll = subTotals.monthlyMinor + emi.monthlyMinor + billOut.monthlyMinor
    val totals = Totals(monthlyAll, subTotals.yearlyMinor + (emi.monthlyMinor + billOut.monthlyMinor) * 12, subTotals.excluded + emi.excluded + billOut.excluded)
    val horizon = today.plusDays(UPCOMING_DAYS)

    val subItems = subs.filter { it.active }.mapNotNull { s ->
        val next = Renewal.next(s, today)
        if (next.isAfter(horizon)) return@mapNotNull null
        val trial = s.isTrialActive(today)
        Upcoming(
            "sub:${s.id}", s.name, if (trial) "Free trial ends - charge starts" else "Renews", next,
            Money.format(s.amountMinor, s.currency), covers["SUBSCRIPTION:${s.id}"],
            icon = categoryIcon(s.category), tint = categoryColor(s.category), trialSoon = trial && !next.isAfter(today.plusDays(3)),
        )
    }
    val emiItems = loans.filter { it.active }.mapNotNull { l ->
        val row = Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today).nextDue ?: return@mapNotNull null
        if (row.dueDate.isAfter(horizon)) return@mapNotNull null
        Upcoming(
            "emi:${l.id}", l.name, "EMI ${row.no}", row.dueDate, Money.format(row.paymentMinor, l.currency), covers["LOAN:${l.id}"],
            overdue = row.status == RowStatus.OVERDUE, icon = loanIcon(l.type), tint = LOAN_TINT,
            emiLoanId = l.id, emiNo = row.no, emiMinor = row.paymentMinor,
        )
    }
    val stockUp = stockItems.filter { it.active }.flatMap { i ->
        val mine = stockBatches.filter { it.itemId == i.id }
        val myLogs = stockLogs.filter { it.itemId == i.id }
        val st = StockEngine.status(i, mine, myLogs, today)
        val cover = covers["STOCK_ITEM:${i.id}"]
        buildList {
            if (st.low && (mine.isNotEmpty() || myLogs.isNotEmpty())) {
                add(Upcoming("low:${i.id}", i.name, "Running low", today, qty(st.estimatedMilli, i.unit), cover, overdue = true, icon = categoryIcon(i.category), tint = categoryColor(i.category)))
            }
            mine.filter { it.quantityMilli > 0 && it.expiry != null && !it.expiry.isAfter(horizon) }.forEach { b ->
                val e = b.expiry!!
                add(Upcoming("exp:${b.id}", i.name, if (e.isBefore(today)) "Expired" else "Expires", e, qty(b.quantityMilli, i.unit), cover, overdue = e.isBefore(today), icon = categoryIcon(i.category), tint = categoryColor(i.category)))
            }
        }
    }
    val billItems = bills.filter { it.active }.mapNotNull { b ->
        val mine = billEntries.filter { it.billId == b.id }
        val due = BillMath.pendingDue(b, mine, today) ?: return@mapNotNull null
        if (due.isAfter(horizon)) return@mapNotNull null
        val usual = BillMath.average(mine)
        Upcoming(
            "bill:${b.id}", b.name, "Bill due", due, usual?.let { "~" + Money.format(it, b.currency) }, covers["BILL:${b.id}"],
            overdue = due.isBefore(today), icon = billIcon(b.type), tint = billTint,
        )
    }
    val upcoming = (subItems + emiItems + stockUp + billItems).sortedBy { it.date }

    val overdue = upcoming.firstOrNull { it.overdue }
    val soon = upcoming.firstOrNull { !it.overdue && !it.date.isAfter(today.plusDays(3)) }
    val mood = if (overdue != null) Mood.WORRIED else if (soon != null) Mood.ALERT else Mood.HAPPY
    val message = when {
        overdue != null -> "${overdue.title}: ${overdue.detail} - overdue."
        soon != null -> "${soon.title} ${soon.detail.lowercase()} ${relativeDay(soon.date, today).lowercase()}."
        else -> "All clear for the next $UPCOMING_DAYS days. Nice."
    }
    val hour = LocalTime.now().hour
    val greeting = if (hour < 12) "Good morning" else if (hour < 17) "Good afternoon" else "Good evening"

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold("Overview", onBack = null) { pad ->
            ListScreen(pad) {
                item { RemyHeader(mood, greeting, message) }
                item { OutgoHero(subTotals.monthlyMinor, emi.monthlyMinor, billOut.monthlyMinor, totals.yearlyMinor, cur, totals.excluded) }
                item { Text("Coming up in $UPCOMING_DAYS days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                if (upcoming.isEmpty()) item { Text("Nothing due soon. Enjoy the quiet.", style = MaterialTheme.typography.bodyMedium) }
                itemsIndexed(upcoming, key = { _, u -> u.key }) { i, u ->
                    OutlinedCard(Modifier.fillMaxWidth().enterStagger(i, u.key)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u.title, u.photo, icon = u.icon, tint = u.tint)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(u.title, style = MaterialTheme.typography.titleMedium)
                                Text(u.detail, style = MaterialTheme.typography.bodySmall, modifier = Modifier.pulse(u.trialSoon))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (u.amount != null) Text(u.amount, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    if (u.overdue) "Overdue - " + relativeDay(u.date, today) else relativeDay(u.date, today),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (u.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (u.emiLoanId != null) {
                                    FilledTonalButton({
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        vm.markPaid(u.emiLoanId, u.emiNo, u.emiMinor)
                                    }, Modifier.padding(top = 4.dp)) { Text("Paid") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
