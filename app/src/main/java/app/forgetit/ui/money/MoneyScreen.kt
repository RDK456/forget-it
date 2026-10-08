package app.forgetit.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.BudgetLevel
import app.forgetit.domain.CategoryBreakdown
import app.forgetit.domain.CategorySpend
import app.forgetit.domain.Money
import app.forgetit.domain.SpendStats
import app.forgetit.domain.Txn
import app.forgetit.domain.TxnCategories
import app.forgetit.domain.TxnDirection
import app.forgetit.domain.categoryOr
import app.forgetit.ui.AnimatedProgress
import app.forgetit.ui.AppIcons
import app.forgetit.ui.EmptyState
import app.forgetit.ui.GradientHeader
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.edit.Dropdown
import app.forgetit.ui.pressScale
import app.forgetit.ui.relativeDay
import app.forgetit.ui.theme.Brushes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private const val PAGE = 50
private val AMBER = Color(0xFFE09A00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(vm: MainViewModel, today: LocalDate, openAdd: Boolean, onOpenDetected: () -> Unit) {
    val txns by vm.txns.collectAsStateWithLifecycle()
    val rules by vm.categoryRules.collectAsStateWithLifecycle()
    val budgets by vm.categoryBudgets.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val cur = settings.defaultCurrency
    val rates = remember(settings.rates) { settings.rates.mapValues { it.value.value } }

    var monthIdx by rememberSaveable { mutableIntStateOf(today.year * 12 + today.monthValue - 1) }
    val month = YearMonth.of(monthIdx / 12, monthIdx % 12 + 1)
    val isCurrent = month == YearMonth.from(today)
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var catFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by rememberSaveable { mutableStateOf(openAdd) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var limitFor by rememberSaveable { mutableStateOf<String?>(null) }
    var detailCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var visible by rememberSaveable { mutableIntStateOf(PAGE) }
    val addRequest by vm.addTxnRequest.collectAsStateWithLifecycle()
    LaunchedEffect(addRequest) { if (addRequest) { adding = true; vm.addTxnRequest.value = false } }

    val summary = remember(txns, month, cur, rates, rules) { SpendStats.summary(txns, month, cur, rates, rules) }
    val trend = remember(txns, month, cur, rates, rules) { SpendStats.trend(txns, month, 6, cur, rates, rules) }
    val rows = summary.byCategory + budgets.keys.filter { c -> summary.byCategory.none { it.category == c } }.map { CategorySpend(it, 0, 0, 0) }
    val inMonth = remember(txns, month) { txns.filter { it.status != "IGNORED" && YearMonth.from(it.date) == month } }
    val shown = remember(inMonth, filter, catFilter, query, rules) { inMonth.filter { t ->
        (filter == "All" || (filter == "Out" && t.direction == TxnDirection.DEBIT) || (filter == "In" && t.direction == TxnDirection.CREDIT)) &&
            (catFilter == null || t.categoryOr(rules) == catFilter) &&
            (query.isBlank() || listOf(t.merchant.orEmpty(), t.note, t.snippet, t.categoryOr(rules)).any { it.contains(query, ignoreCase = true) })
    }.sortedWith(compareByDescending<Txn> { it.date }.thenByDescending { it.id }) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Money") }) },
        floatingActionButton = { FloatingActionButton(onClick = { adding = true }) { Icon(AppIcons.Add, "Add a transaction") } },
    ) { pad ->
        ListScreen(pad, onRefresh = { vm.refreshAll(forceScan = true) }) {
            item("month") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton({ monthIdx--; catFilter = null; visible = PAGE }) { Icon(AppIcons.Previous, "Previous month") }
                    TextButton({ monthIdx = today.year * 12 + today.monthValue - 1 }) {
                        Text(month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + month.year, style = MaterialTheme.typography.titleMedium)
                    }
                    IconButton({ monthIdx++; catFilter = null; visible = PAGE }, enabled = !isCurrent) { Icon(AppIcons.Next, "Next month") }
                }
            }
            item("hero") {
                GradientHeader(
                    AppIcons.Wallet, "Spent in " + month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()), Brushes.berry,
                    valueMinor = summary.spentMinor, currency = cur,
                    supporting = "Income ${Money.format(summary.incomeMinor, cur)}, " + (if (summary.netMinor >= 0) "left " else "short ") + Money.format(Math.abs(summary.netMinor), cur),
                    warning = if (summary.excluded > 0) "${summary.excluded} payment(s) in other currencies are left out. Set exchange rates in Settings." else null,
                )
            }
            item("budget") { BudgetCard(summary.spentMinor, settings.budgetMinor, cur, today, month) { limitFor = "" } }
            summary.top?.let { top ->
                item("top") {
                    val tint = txnCategoryColor(top.category)
                    Card(Modifier.fillMaxWidth().pressScale { detailCategory = top.category }, colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CategoryTile(top.category, 52.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Highest spending", style = MaterialTheme.typography.labelMedium)
                                Text(top.category, style = MaterialTheme.typography.titleMedium)
                                Text("${Money.format(top.minor, cur)}, ${top.percent}% of everything you spent", style = MaterialTheme.typography.bodySmall)
                                deltaText(top)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = tint) }
                            }
                        }
                    }
                }
            }
            item("catTitle") { Text("Where it went", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            if (rows.isNotEmpty()) item("catHint") { Text("Tap a category to see where, when and how much.", style = MaterialTheme.typography.bodySmall) }
            if (rows.isEmpty()) item("catEmpty") {
                EmptyState(AppIcons.Wallet, "Nothing spent yet", "Add what you spend with the plus button, or let Forget-it find payments in your messages.")
            } else items(rows, key = { "c${it.category}" }) { c ->
                val limit = budgets[c.category]
                val level = limit?.let { SpendStats.level(c.minor, it) } ?: BudgetLevel.OK
                val levelColor = when (level) { BudgetLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant; BudgetLevel.WARN -> AMBER; BudgetLevel.OVER -> MaterialTheme.colorScheme.error }
                Column(Modifier.fillMaxWidth().pressScale { detailCategory = c.category }.padding(vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryTile(c.category, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.category, style = MaterialTheme.typography.titleSmall, fontWeight = if (catFilter == c.category) FontWeight.Bold else FontWeight.Normal)
                            deltaText(c)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(Money.format(c.minor, cur), style = MaterialTheme.typography.titleSmall)
                            Text("${c.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    AnimatedProgress(c.percent / 100f, Modifier.fillMaxWidth().padding(top = 6.dp), txnCategoryColor(c.category))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (limit != null) "Limit ${Money.format(limit, cur)}: " + when (level) {
                                BudgetLevel.OK -> "${Money.format(limit - c.minor, cur)} left"
                                BudgetLevel.WARN -> "close to the limit"
                                BudgetLevel.OVER -> "over by ${Money.format(c.minor - limit, cur)}"
                            } else "No limit",
                            style = MaterialTheme.typography.bodySmall, color = levelColor, modifier = Modifier.weight(1f),
                        )
                        TextButton({ limitFor = c.category }) { Text(if (limit != null) "Edit limit" else "Set limit") }
                    }
                }
            }
            item("trend") { TrendCard(trend.map { it.first.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()) to it.second }, trend.indexOfFirst { it.first == month }, cur) }

            item("ledgerTitle") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    Text("All transactions" + (catFilter?.let { ", $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        listOf("All", "Out", "In").forEach { f -> FilterChip(filter == f, { filter = f; visible = PAGE }, { Text(f) }) }
                        if (catFilter != null) TextButton({ catFilter = null }) { Text("Clear category") }
                    }
                    OutlinedTextField(
                        query, { query = it; visible = PAGE }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Search merchant, note or category") },
                    )
                }
            }
            if (shown.isEmpty()) item("ledgerEmpty") {
                Text(if (inMonth.isEmpty()) "No transactions this month." else "Nothing matches.", style = MaterialTheme.typography.bodyMedium)
            }
            val page = shown.take(visible)
            page.groupBy { it.date }.forEach { (day, list) ->
                item("d$day") { Text(relativeDay(day, today), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp)) }
                items(list, key = { "t${it.id}" }) { t ->
                    val cat = t.categoryOr(rules)
                    val credit = t.direction == TxnDirection.CREDIT
                    Row(Modifier.fillMaxWidth().pressScale { editingId = t.id }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        CategoryTile(cat, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.merchant ?: t.note.ifBlank { "Unknown" }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            Text(cat + ", " + sourceLabel(t.source) + (if (t.note.isNotBlank() && t.merchant != null) ", ${t.note}" else ""), style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            (if (credit) "+" else "-") + Money.format(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall,
                            color = if (credit) Color(0xFF2E9E5B) else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            // Lazy loading: the next page is added when the list is scrolled to this row.
            if (visible < shown.size) item("more") {
                LaunchedEffect(visible) { visible += PAGE }
                Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.padding(4.dp)) }
            }
            item("detected") {
                OutlinedButton(onOpenDetected, Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Detected payments, suggestions and scan settings") }
            }
        }
    }

    detailCategory?.let { cat ->
        CategoryDetailDialog(
            CategoryBreakdown.detail(txns, month, cat, cur, rates, rules), cur, budgets[cat],
            onDismiss = { detailCategory = null }, onEdit = { editingId = it.id }, onSetLimit = { limitFor = cat },
            onShowInLedger = { catFilter = cat; detailCategory = null },
        )
    }
    if (adding) TxnDialog(null, cur, today, rules, { t, _ -> vm.saveTxn(t, true, false); adding = false }, null) { adding = false }
    editingId?.let { id ->
        val t = txns.firstOrNull { it.id == id }
        if (t != null) TxnDialog(t, cur, today, rules, { u, learn -> vm.saveTxn(u, false, learn); editingId = null }, { vm.deleteTxn(t.id); editingId = null }) { editingId = null }
    }
    limitFor?.let { cat ->
        LimitDialog(
            if (cat.isEmpty()) "Monthly budget" else "Monthly limit for $cat", cur, if (cat.isEmpty()) settings.budgetMinor else budgets[cat] ?: 0,
            { minor -> if (cat.isEmpty()) vm.setBudget(minor) else vm.setCategoryBudget(cat, minor); limitFor = null },
        ) { limitFor = null }
    }
}

private fun sourceLabel(source: String) = when (source) {
    "SMS" -> "SMS"
    "EMAIL", "GMAIL" -> "Email"
    "PHOTO" -> "Photo"
    "MANUAL" -> "Added by you"
    else -> source
}

private fun deltaText(c: CategorySpend): String? = when {
    c.minor == 0L -> null
    c.previousMinor == 0L -> "New this month"
    else -> Math.round((c.minor - c.previousMinor) * 100.0 / c.previousMinor).toInt().let { (if (it >= 0) "+" else "") + it + "% vs last month" }
}

@Composable
private fun BudgetCard(spent: Long, budget: Long, currency: String, today: LocalDate, month: YearMonth, onSet: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (budget <= 0) {
                Text("Monthly budget", style = MaterialTheme.typography.titleMedium)
                Text("Set a limit and Forget-it shows what is left each day and warns you at 80 percent.", style = MaterialTheme.typography.bodySmall)
                Button(onSet) { Text("Set a budget") }
                return@Column
            }
            val level = SpendStats.level(spent, budget)
            val color = when (level) { BudgetLevel.OK -> MaterialTheme.colorScheme.primary; BudgetLevel.WARN -> AMBER; BudgetLevel.OVER -> MaterialTheme.colorScheme.error }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Monthly budget", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onSet) { Text("Change") }
            }
            AnimatedProgress((spent.toFloat() / budget).coerceIn(0f, 1f), Modifier.fillMaxWidth(), color)
            Text(
                if (spent > budget) "Over by ${Money.format(spent - budget, currency)} (budget ${Money.format(budget, currency)})"
                else "${Money.format(budget - spent, currency)} left of ${Money.format(budget, currency)}",
                color = color, style = MaterialTheme.typography.bodyMedium,
            )
            if (month == YearMonth.from(today) && spent < budget) {
                Text("About ${Money.format(SpendStats.dailyAllowance(budget, spent, today), currency)} a day for the rest of the month.", style = MaterialTheme.typography.bodySmall)
                val pace = SpendStats.projected(spent, today, month)
                if (pace > budget) Text("At this pace you will spend ${Money.format(pace, currency)}, ${Money.format(pace - budget, currency)} over.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TrendCard(values: List<Pair<String, Long>>, selected: Int, currency: String) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Last six months", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().height(110.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                values.forEachIndexed { i, (label, v) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.width(30.dp).height((v.toFloat() / max * 78f).coerceAtLeast(4f).dp).clip(RoundedCornerShape(8.dp))
                                .background(if (i == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            values.getOrNull(selected)?.let { Text("This month: " + Money.format(it.second, currency), style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun TxnDialog(
    initial: Txn?, currency: String, today: LocalDate, rules: Map<String, String>,
    onSave: (Txn, Boolean) -> Unit, onDelete: (() -> Unit)?, onDismiss: () -> Unit,
) {
    var direction by remember { mutableStateOf(initial?.direction ?: TxnDirection.DEBIT) }
    var amount by remember { mutableStateOf(initial?.let { Money.toPlain(it.amountMinor, it.currency) }.orEmpty()) }
    var merchant by remember { mutableStateOf(initial?.merchant.orEmpty()) }
    var category by remember { mutableStateOf(initial?.categoryOr(rules) ?: TxnCategories.EXPENSE.first()) }
    var date by remember { mutableStateOf((initial?.date ?: today).toString()) }
    var note by remember { mutableStateOf(initial?.note.orEmpty()) }
    val cur = initial?.currency ?: currency
    val minor = Money.parseMinor(amount.replace(',', '.').trim(), cur)
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val options = TxnCategories.forDirection(direction)
    LaunchedEffect(direction) { if (category !in options) category = options.first() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a transaction" else "Edit transaction") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(direction == TxnDirection.DEBIT, { direction = TxnDirection.DEBIT }, { Text("Spent") })
                    FilterChip(direction == TxnDirection.CREDIT, { direction = TxnDirection.CREDIT }, { Text("Received") })
                }
                OutlinedTextField(
                    amount, { amount = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Amount ($cur)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amount.isNotEmpty() && (minor ?: 0) <= 0,
                )
                OutlinedTextField(merchant, { merchant = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text(if (direction == TxnDirection.CREDIT) "From" else "Paid to") })
                Dropdown("Category", category, options, { it }) { category = it }
                OutlinedTextField(date, { date = it }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Date (yyyy-mm-dd)") }, isError = parsedDate == null)
                OutlinedTextField(note, { note = it }, Modifier.fillMaxWidth(), label = { Text("Note (optional)") })
                if (initial != null && initial.source != "MANUAL") Text("Found in ${sourceLabel(initial.source)}: ${initial.snippet.take(110)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(
                {
                    onSave(
                        Txn(
                            id = initial?.id ?: 0, direction = direction, amountMinor = minor!!, currency = cur, merchant = merchant.trim().ifBlank { null },
                            accountHint = initial?.accountHint, date = parsedDate!!, source = initial?.source ?: "MANUAL", status = initial?.status ?: "NEW",
                            snippet = initial?.snippet.orEmpty(), sender = initial?.sender.orEmpty(), category = category, note = note,
                        ),
                        initial != null && category != initial.categoryOr(rules),
                    )
                },
                enabled = (minor ?: 0) > 0 && parsedDate != null,
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(it) { Text("Delete") } }
                TextButton(onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun LimitDialog(title: String, currency: String, current: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (current > 0) Money.toPlain(current, currency) else "") }
    val minor = if (text.isBlank()) 0L else Money.parseMinor(text.replace(',', '.').trim(), currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("You are warned at 80 percent and again when the limit is passed. Leave it empty to remove the limit.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    text, { text = it }, singleLine = true, label = { Text("Per month ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = minor == null,
                )
            }
        },
        confirmButton = { TextButton({ onSave(minor ?: 0) }, enabled = minor != null) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
