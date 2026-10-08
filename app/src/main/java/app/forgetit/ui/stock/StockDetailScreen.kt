package app.forgetit.ui.stock

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.LogKind
import app.forgetit.domain.Money
import app.forgetit.domain.StockEngine
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.loans.ConfirmDialog
import app.forgetit.ui.relativeDay
import java.time.LocalDate

@Composable
fun StockDetailScreen(vm: MainViewModel, itemId: Long, today: LocalDate, onBack: () -> Unit, onEdit: () -> Unit, photos: @Composable () -> Unit) {
    val items by vm.stockItems.collectAsStateWithLifecycle()
    val allBatches by vm.stockBatches.collectAsStateWithLifecycle()
    val allLogs by vm.stockLogs.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.id == itemId }
    var dialog by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    if (item == null) { ScreenScaffold("Item", onBack) {}; return }

    val batches = allBatches.filter { it.itemId == itemId }.sortedWith(compareBy(nullsLast()) { it.expiry })
    val logs = allLogs.filter { it.itemId == itemId }.sortedByDescending { it.date }
    val s = StockEngine.status(item, batches, logs, today)
    val u = item.unit

    ScreenScaffold(item.name, onBack) { pad ->
        ListScreen(pad) {
            item {
                OutlinedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Estimated now", style = MaterialTheme.typography.labelLarge)
                        Text(qty(s.estimatedMilli, u), style = MaterialTheme.typography.headlineMedium, color = if (s.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Text("Counted in batches: ${qty(s.storedMilli, u)}")
                        val rate = s.ratePerDayMilli
                        Text(if (rate != null) "Usage about ${Money.milliToPlain(rate.toLong())} $u per day" else "No usage rate yet (log a few uses to learn it)")
                        s.runOut?.let { Text("Runs out ${relativeDay(it, today)}") }
                        if (s.low) Text("Running low", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({ dialog = "use" }) { Text("Used") }
                    Button({ dialog = "restock" }) { Text("Restock") }
                    OutlinedButton(onEdit) { Text("Edit") }
                }
            }
            message?.let { m -> item { Text(m, color = MaterialTheme.colorScheme.error) } }
            item { photos() }
            item { Text("Batches", style = MaterialTheme.typography.titleMedium) }
            if (batches.isEmpty()) item { Text("No stock recorded. Tap Restock when you buy more.") }
            items(batches, key = { "b${it.id}" }) { b ->
                val expired = b.expiry != null && b.expiry.isBefore(today)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(qty(b.quantityMilli, u))
                        Text(
                            b.expiry?.let { (if (expired) "Expired " else "Expires ") + relativeDay(it, today) } ?: "No expiry",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (expired) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton({ vm.discardBatch(b.id) }) { Text(if (expired) "Throw away" else "Discard") }
                }
            }
            if (logs.isNotEmpty()) item { Text("Recent activity", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            items(logs.take(10), key = { "l${it.id}" }) { l ->
                val what = when (l.kind) { LogKind.USED -> "Used"; LogKind.RESTOCK -> "Restocked"; LogKind.DISCARD -> "Discarded"; LogKind.ADJUST -> "Adjusted" }
                Text("${l.date}  $what ${Money.milliToPlain(kotlin.math.abs(l.deltaMilli))} $u", style = MaterialTheme.typography.bodySmall)
            }
            item { OutlinedButton({ dialog = "delete" }, Modifier.fillMaxWidth()) { Text("Delete this item") } }
        }
    }
    when (dialog) {
        "use" -> QuantityDialog("How much did you use?", u, false, today, { q, _ ->
            vm.useStock(itemId, q) { r -> message = if (r.shortfallMilli > 0) "Only ${qty(r.consumedMilli, u)} was available in unexpired batches." else null }
            dialog = null
        }, { dialog = null })
        "restock" -> QuantityDialog("Restock", u, true, today, { q, e -> vm.restock(itemId, q, e); dialog = null }, { dialog = null })
        "delete" -> ConfirmDialog("Delete item?", "This removes the item, its batches, history and photos.", "Delete", { vm.deleteStockItem(itemId); dialog = null; onBack() }, { dialog = null })
    }
}
