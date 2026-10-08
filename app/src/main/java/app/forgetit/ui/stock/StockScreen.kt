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

    val ctx = androidx.compose.ui.platform.LocalContext.current
    fun toast(m: String) = android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_LONG).show()
    val speak = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val said = r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        val item = said?.let(app.forgetit.domain.GroceryScan::fromSpeech)
        if (item == null) { if (said != null) toast("Did not catch an item in \"$said\"") }
        else vm.saveDetected(listOf(app.forgetit.domain.Detected(app.forgetit.domain.DocKind.GROCERY, "Groceries", null, vm.settings.value.defaultCurrency, items = listOf(item)))) { toast(it) }
    }
    Scaffold(
        topBar = { TopAppBar(title = { Text("Household stock") }) },
        floatingActionButton = {
            androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.End, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.SmallFloatingActionButton(onClick = {
                    try {
                        speak.launch(
                            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Say, for example, 2 litres milk"),
                        )
                    } catch (e: android.content.ActivityNotFoundException) { toast("Voice input is not available on this phone") }
                }) { Icon(AppIcons.Mic, "Add by voice") }
                androidx.compose.material3.SmallFloatingActionButton(onClick = { vm.openScan() }) { Icon(AppIcons.Camera, "Scan groceries") }
                FloatingActionButton(onClick = onAdd) { Icon(AppIcons.Add, "Add item") }
            }
        },
    ) { pad ->
        ListScreen(pad, onRefresh = { vm.refreshAll() }) {
            item {
                app.forgetit.ui.GradientHeader(
                    AppIcons.Stock, "Household stock", app.forgetit.ui.theme.Brushes.stock, bigText = "${rows.size} item" + if (rows.size == 1) "" else "s",
                    supporting = "${grouped[Group.LOW].orEmpty().size} running low, ${grouped[Group.EXPIRED].orEmpty().size} expired",
                )
            }
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
