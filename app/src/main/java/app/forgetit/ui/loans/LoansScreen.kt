package app.forgetit.ui.loans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
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
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add loan") } },
    ) { pad ->
        ListScreen(pad) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("EMIs per month", style = MaterialTheme.typography.labelLarge)
                        Text(Money.format(outgo.monthlyMinor, settings.defaultCurrency), style = MaterialTheme.typography.headlineMedium)
                        if (outgo.excluded > 0) Text("${outgo.excluded} excluded - set exchange rates in Settings", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (loans.isEmpty()) item { Text("No loans yet. Tap + to add a loan or EMI.") }
            items(loans, key = { it.id }) { l ->
                val s = Amortization.build(l, adj.filter { it.loanId == l.id }, pay.filter { it.loanId == l.id }, today)
                val paidCount = s.rows.count { it.status == RowStatus.PAID }
                Card(Modifier.fillMaxWidth().clickable { onOpen(l.id) }) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Avatar(l.name, covers["LOAN:${l.id}"])
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(l.name, style = MaterialTheme.typography.titleMedium)
                                Text(listOf(l.type.name.lowercase().replaceFirstChar { it.uppercase() }, l.lender).filter { it.isNotBlank() }.joinToString(" - "), style = MaterialTheme.typography.bodySmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(s.outstandingMinor, l.currency), style = MaterialTheme.typography.titleMedium)
                                Text("outstanding", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        LinearProgressIndicator(
                            progress = { if (s.rows.isEmpty()) 1f else paidCount / s.rows.size.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                        val next = s.nextDue
                        Text(
                            if (next == null) "All installments paid" else
                                "$paidCount of ${s.rows.size} paid - next ${Money.format(next.paymentMinor, l.currency)} ${relativeDay(next.dueDate, today)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (next?.status == RowStatus.OVERDUE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
