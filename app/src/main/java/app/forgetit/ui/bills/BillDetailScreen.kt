package app.forgetit.ui.bills

import app.forgetit.ui.relativeDayLower
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.BillMath
import app.forgetit.domain.Money
import app.forgetit.ui.AnimatedMoney
import app.forgetit.ui.AppIcons
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.loans.ConfirmDialog
import app.forgetit.ui.relativeDay
import java.time.LocalDate

@Composable
fun BillDetailScreen(vm: MainViewModel, billId: Long, today: LocalDate, onBack: () -> Unit, onEdit: () -> Unit, photos: @Composable () -> Unit) {
    val bills by vm.bills.collectAsStateWithLifecycle()
    val allEntries by vm.billEntries.collectAsStateWithLifecycle()
    val bill = bills.firstOrNull { it.id == billId }
    var dialog by remember { mutableStateOf<String?>(null) }
    if (bill == null) { ScreenScaffold("Bill", onBack) {}; return }

    val entries = allEntries.filter { it.billId == billId }
    val due = BillMath.pendingDue(bill, entries, today) ?: BillMath.recentAndNext(bill, today).last()
    val usual = BillMath.average(entries)
    val last = BillMath.lastEntry(entries)
    val lastChange = last?.let { BillMath.changePercent(it.amountMinor, entries.filter { e -> e.dueDate != last.dueDate }) }
    val cur = bill.currency

    ScreenScaffold(bill.name, onBack) { pad ->
        ListScreen(pad) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Usually", style = MaterialTheme.typography.labelLarge)
                        if (usual != null) AnimatedMoney(usual, cur, MaterialTheme.typography.headlineMedium)
                        else Text("No bills recorded yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (due.isBefore(today)) "Overdue since " else "Next due ") + relativeDayLower(due, today) + " ($due)",
                            color = if (due.isBefore(today)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        )
                        if (lastChange != null && lastChange >= BillMath.HIGH_PERCENT) {
                            Text("Last bill was $lastChange% higher than usual.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            item { Button({ dialog = "record" }, Modifier.fillMaxWidth()) { Text("Record the bill due $due") } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onEdit) { Text("Edit") } } }
            if (entries.isNotEmpty()) {
                item { Text("Last bills", style = MaterialTheme.typography.titleMedium) }
                item { BillBars(entries) }
            }
            item { photos() }
            if (entries.isNotEmpty()) item { Text("History", style = MaterialTheme.typography.titleMedium) }
            items(entries.sortedByDescending { it.dueDate }, key = { "e${it.dueDate}" }) { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${e.dueDate}  ${Money.format(e.amountMinor, cur)}")
                        Text(e.paidOn?.let { "Paid on $it" } ?: "Not paid yet", style = MaterialTheme.typography.bodySmall, color = if (e.paidOn == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton({ vm.removeBillEntry(billId, e.dueDate) }) { Icon(AppIcons.Delete, "Remove record") }
                }
            }
            item { OutlinedButton({ dialog = "delete" }, Modifier.fillMaxWidth()) { Text("Delete this bill") } }
        }
    }
    when (dialog) {
        "record" -> RecordBillDialog(due, cur, entries.filter { it.dueDate != due }, { amount, paid -> vm.recordBill(billId, due, amount, paid); dialog = null }, { dialog = null })
        "delete" -> ConfirmDialog("Delete bill?", "This removes the bill, its history and photos.", "Delete", { vm.deleteBill(billId); dialog = null; onBack() }, { dialog = null })
    }
}
