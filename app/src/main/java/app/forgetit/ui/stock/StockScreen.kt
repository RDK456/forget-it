package app.forgetit.ui.stock

import app.forgetit.ui.enterStagger
import app.forgetit.ui.EmptyState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Money
import app.forgetit.domain.StockEngine
import app.forgetit.domain.StockItem
import app.forgetit.domain.StockStatus
import app.forgetit.ui.Avatar
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.relativeDay
import java.time.LocalDate

fun qty(milli: Long, unit: String) = Money.milliToPlain(milli) + " " + unit

private enum class Group(val title: String) { EXPIRED("Has expired batches"), LOW("Running low"), OK("In stock") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockScreen(vm: MainViewModel, today: LocalDate, onAdd: () -> Unit, onOpen: (Long) -> Unit) {
    val items by vm.stockItems.collectAsStateWithLifecycle()
    val batches by vm.stockBatches.collectAsStateWithLifecycle()
    val logs by vm.stockLogs.collectAsStateWithLifecycle()
    val covers by vm.covers.collectAsStateWithLifecycle()
    val rows = items.filter { it.active }.map { i ->
        i to StockEngine.status(i, batches.filter { it.itemId == i.id }, logs.filter { it.itemId == i.id }, today)
    }
    val grouped = rows.groupBy { (_, s) ->
        when { s.expiredBatches.isNotEmpty() -> Group.EXPIRED; s.low -> Group.LOW; else -> Group.OK }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Household stock") }) },
        floatingActionButton = {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.SmallFloatingActionButton(onClick = { vm.openScan() }) { Icon(AppIcons.Camera, "Scan groceries") }
                FloatingActionButton(onClick = onAdd) { Icon(AppIcons.Add, "Add item") }
            }
        },
    ) { pad ->
        ListScreen(pad) {
            item { ShoppingCard(app.forgetit.domain.shoppingList(items, batches, logs, today), today, onBought = { vm.restock(it.itemId, it.buyMilli, null) }) }
            if (items.isEmpty()) item { EmptyState(AppIcons.Stock, "Your shelf is empty", "Add milk, rice or anything you keep running out of. Forget-it will warn you before it does.") }
            for (g in Group.entries) {
                val list = grouped[g].orEmpty().sortedBy { it.first.name.lowercase() }
                if (list.isEmpty()) continue
                item(key = "h${g.name}") { Text(g.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(list, key = { "i${it.first.id}" }) { (i, s) -> StockCard(i, s, today, covers["STOCK_ITEM:${i.id}"]) { onOpen(i.id) } }
            }
        }
    }
}

@Composable
private fun StockCard(i: StockItem, s: StockStatus, today: LocalDate, cover: java.io.File?, onClick: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().enterStagger(0, "stock${i.id}").pressScale(onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(i.name, cover, icon = categoryIcon(i.category), tint = categoryColor(i.category))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(i.name, style = MaterialTheme.typography.titleMedium)
                val expiry = s.nextExpiry?.let { "expires ${relativeDay(it, today)}" }
                val runOut = s.runOut?.let { "runs out ${relativeDay(it, today)}" }
                Text(listOfNotNull(i.category, expiry, runOut).joinToString(" - "), style = MaterialTheme.typography.bodySmall)
                if (s.expiredBatches.isNotEmpty()) Text("${s.expiredBatches.size} expired batch(es)", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(qty(s.estimatedMilli, i.unit), style = MaterialTheme.typography.titleMedium, color = if (s.low) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                if (s.ratePerDayMilli != null && s.estimatedMilli != s.storedMilli) Text("counted: ${qty(s.storedMilli, i.unit)}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
