package app.forgetit.ui.loans

import app.forgetit.ui.enterStagger
import app.forgetit.ui.EmptyState
import app.forgetit.ui.AnimatedProgress
import app.forgetit.ui.AnimatedMoney
import app.forgetit.ui.categoryColor
import app.forgetit.ui.categoryIcon
import app.forgetit.ui.loanIcon
import app.forgetit.ui.pressScale
import app.forgetit.ui.AppIcons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Amortization
import app.forgetit.domain.LoanOutgo
import app.forgetit.domain.Money
import app.forgetit.domain.RowStatus
import app.forgetit.domain.loanMonthlyOutgo
import app.forgetit.ui.Avatar
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.relativeDay
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(vm: MainViewModel, today: LocalDate, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val loans by vm.loans.collectAsStateWithLifecycle()
    val pay by vm.loanPayments.collectAsStateWithLifecycle()
    val adj by vm.loanAdjustments.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val rates = settings.rates.mapValues { it.value.value }
    val outgo: LoanOutgo = loanMonthlyOutgo(loans, adj, pay, today, settings.defaultCurrency, rates)

    Scaffold(
        topBar = { TopAppBar(title = { Text("Loans and EMIs") }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(AppIcons.Add, "Add loan") } },
    ) { pad ->
        ListScreen(pad, onRefresh = { vm.refreshAll() }) {
            item {
                app.forgetit.ui.GradientHeader(
                    AppIcons.Loans, "EMIs per month", app.forgetit.ui.theme.Brushes.loan, valueMinor = outgo.monthlyMinor, currency = settings.defaultCurrency,
                    warning = if (outgo.excluded > 0) "${outgo.excluded} excluded - set exchange rates in Settings" else null,
                )
            }
            if (loans.isEmpty()) item { EmptyState(AppIcons.Loans, "No loans yet", "Add a loan or EMI and Forget-it will count down every installment for you.") }
            items(loans, key = { it.id }) { l ->
                val s = Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today)
                val paidCount = s.rows.count { it.status == RowStatus.PAID }
                OutlinedCard(Modifier.animateItem().fillMaxWidth().enterStagger(loans.indexOf(l), l.id).pressScale { onOpen(l.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        if (!l.active) Text("On hold: not counted, no reminders", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(l.name, covers["LOAN:${l.id}"], icon = loanIcon(l.type), tint = androidx.compose.ui.graphics.Color(0xFF5560E0))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(l.type.name.lowercase().replaceFirstChar { it.uppercase() }, l.lender).filter { it.isNotBlank() }.joinToString(" - "), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                AnimatedMoney(s.outstandingMinor, l.currency, MaterialTheme.typography.titleMedium)
                                Text("outstanding", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        AnimatedProgress(
                            target = if (s.rows.isEmpty()) 1f else paidCount / s.rows.size.toFloat(),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        val next = s.nextDue
                        Text(
                            if (next == null) "All installments paid" else
                                "$paidCount of ${s.rows.size} paid - next ${Money.format(next.paymentMinor, l.currency)} ${relativeDay(next.dueDate, today)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (next?.status == RowStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (app.forgetit.domain.AutoTrack.isUnreviewed(l.notes)) app.forgetit.ui.ReviewStrip(!l.active, { vm.keepAuto("loan", l.id) }, { vm.deleteLoan(l.id) })
                    }
                }
            }
        }
    }
}
