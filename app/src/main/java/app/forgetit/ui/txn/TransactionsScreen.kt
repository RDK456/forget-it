package app.forgetit.ui.txn

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Money
import app.forgetit.domain.TxnDirection
import app.forgetit.domain.TxnMatching
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.loans.ConfirmDialog
import app.forgetit.txn.SmsScanner
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun TransactionsScreen(vm: MainViewModel, today: LocalDate, onBack: () -> Unit) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val subs by vm.subs.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val pay by vm.loanPayments.collectAsStateWithLifecycle()
    val adj by vm.loanAdjustments.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val settings by vm.settings.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    val live = txns.filter { it.status != "IGNORED" }
    val month = YearMonth.from(today)
    val thisMonth = live.filter { YearMonth.from(it.date) == month }
    val suggestions = TxnMatching.recurring(live, subs.map { it.name } + loans.map { it.name })
    val priceChanges = app.forgetit.domain.PriceWatch.changes(live, subs, today)
    val emi = TxnMatching.emiMatches(live, loans, adj, pay, today)

    ScreenScaffold("Transactions", onBack) { pad ->
        ListScreen(pad) {
            item { AutoScanSection(vm, settings) { message = it } }
            message?.let { m -> item { Text(m, style = MaterialTheme.typography.bodySmall) } }
            val spent = thisMonth.filter { it.direction == TxnDirection.DEBIT }.groupBy { it.currency }.mapValues { e -> e.value.sumOf { it.amountMinor } }
            if (spent.isNotEmpty()) item {
                Text("Spent this month: " + spent.entries.joinToString(", ") { Money.format(it.value, it.key) }, style = MaterialTheme.typography.titleMedium)
            }
            if (priceChanges.isNotEmpty()) item { Text("Price changes", style = MaterialTheme.typography.titleMedium) }
            items(priceChanges, key = { "pc${it.txnId}" }) { pc ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${pc.name} now charges ${Money.format(pc.newMinor, pc.currency)}", style = MaterialTheme.typography.titleMedium)
                            Text("You track ${Money.format(pc.oldMinor, pc.currency)}. Seen on ${pc.seenOn}.", style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Button({ vm.applyPriceChange(pc) }) { Text("Update") }
                            TextButton({ vm.setTxnStatus(pc.txnId, "IGNORED") }) { Text("Ignore") }
                        }
                    }
                }
            }
            if (suggestions.isNotEmpty()) item { Text("Looks like subscriptions", style = MaterialTheme.typography.titleMedium) }
            items(suggestions, key = { "sg${it.merchant}${it.currency}" }) { s ->
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.merchant, style = MaterialTheme.typography.titleMedium)
                            Text("${Money.format(s.amountMinor, s.currency)} ${s.cycle.name.lowercase()} - seen ${s.count} times", style = MaterialTheme.typography.bodySmall)
                        }
                        Button({ vm.addSubscriptionFrom(s) { message = it } }) { Text("Add") }
                    }
                }
            }
            items(emi, key = { "emi${it.txnId}" }) { m ->
                val t = txns.first { it.id == m.txnId }
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${Money.format(t.amountMinor, t.currency)} on ${t.date} looks like EMI ${m.installmentNo} of ${m.loanName}", Modifier.weight(1f))
                        Button({ vm.markEmiFrom(m, t) }) { Text("Mark paid") }
                    }
                }
            }
            item { Text("All transactions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (txns.isEmpty()) item { Text("Nothing yet. Scan your messages or share a receipt.") }
            items(txns, key = { "t${it.id}" }) { t ->
                val ignored = t.status == "IGNORED"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.merchant ?: "Unknown", style = MaterialTheme.typography.titleSmall)
                        Text("${t.date}  ${t.snippet.take(70)}", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val sign = if (t.direction == TxnDirection.DEBIT) "-" else "+"
                        Text(
                            sign + Money.format(t.amountMinor, t.currency),
                            color = if (ignored) MaterialTheme.colorScheme.onSurfaceVariant
                            else if (t.direction == TxnDirection.DEBIT) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
                        )
                        TextButton({ vm.setTxnStatus(t.id, if (ignored) "NEW" else "IGNORED") }) { Text(if (ignored) "Restore" else "Ignore") }
                    }
                }
            }
            if (txns.isNotEmpty()) item { OutlinedButton({ confirmClear = true }, Modifier.fillMaxWidth()) { Text("Delete all transactions") } }
        }
    }
    if (confirmClear) ConfirmDialog("Delete all transactions?", "This only removes the list here, not your messages.", "Delete", { vm.clearTxns(); confirmClear = false }, { confirmClear = false })
}
