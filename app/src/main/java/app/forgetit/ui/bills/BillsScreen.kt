package app.forgetit.ui.bills

import app.forgetit.ui.relativeDayLower
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.BillMath
import app.forgetit.domain.Money
import app.forgetit.domain.billMonthlyOutgo
import app.forgetit.ui.AnimatedMoney
import app.forgetit.ui.AppIcons
import app.forgetit.ui.Avatar
import app.forgetit.ui.EmptyState
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.billIcon
import app.forgetit.ui.enterStagger
import app.forgetit.ui.pressScale
import app.forgetit.ui.relativeDay
import app.forgetit.ui.theme.domainColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: MainViewModel, today: LocalDate, onAdd: () -> Unit, onOpen: (Long) -> Unit, onBack: () -> Unit) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val entries by vm.billEntries.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val outgo = billMonthlyOutgo(bills, entries, settings.defaultCurrency, settings.rates.mapValues { it.value.value })
    val tint = domainColors.bill

    Scaffold(
        topBar = { TopAppBar(title = { Text("Bills and utilities") }, navigationIcon = { androidx.compose.material3.IconButton(onBack) { Icon(AppIcons.Back, "Back") } }) },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(AppIcons.Add, "Add bill") } },
    ) { pad ->
        ListScreen(pad) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Bills per month (average)", style = MaterialTheme.typography.labelLarge)
                        AnimatedMoney(outgo.monthlyMinor, settings.defaultCurrency, MaterialTheme.typography.headlineMedium)
                        if (outgo.excluded > 0) Text("${outgo.excluded} excluded - set exchange rates in Settings", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (bills.isEmpty()) item { EmptyState(AppIcons.Bills, "No bills yet", "Add electricity, water, broadband or rent. Record each bill and Forget-it learns what is normal.") }
            items(bills.sortedBy { it.name.lowercase() }, key = { it.id }) { b ->
                val mine = entries.filter { it.billId == b.id }
                val due = BillMath.pendingDue(b, mine, today)
                val usual = BillMath.average(mine)
                val last = BillMath.lastEntry(mine)
                val high = last != null && BillMath.isHigh(last.amountMinor, mine.filter { it.dueDate != last.dueDate })
                OutlinedCard(Modifier.fillMaxWidth().enterStagger(bills.indexOf(b), b.id).pressScale { onOpen(b.id) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(b.name, covers["BILL:${b.id}"], icon = billIcon(b.type), tint = tint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(b.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (due == null) "No due date" else (if (due.isBefore(today)) "Overdue - " else "Due ") + relativeDayLower(due, today),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (due != null && due.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (high) Text("Last bill was higher than usual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(usual?.let { Money.format(it, b.currency) } ?: "-", style = MaterialTheme.typography.titleMedium)
                            Text(if (usual != null) "usually" else "no bills yet", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
