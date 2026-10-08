package app.forgetit.ui.scan

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import app.forgetit.ui.AppIcons
import app.forgetit.ui.theme.Brushes
import app.forgetit.ui.theme.TrackerBrush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import app.forgetit.domain.BillType
import app.forgetit.domain.CATEGORIES
import app.forgetit.domain.Cycle
import app.forgetit.domain.Detected
import app.forgetit.domain.DocKind
import app.forgetit.domain.DocScan
import app.forgetit.domain.Money
import app.forgetit.domain.STOCK_CATEGORIES
import app.forgetit.domain.ScannedItem
import app.forgetit.grocery.GroceryScanner
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.billTypeLabel
import app.forgetit.ui.edit.Dropdown
import kotlinx.coroutines.launch
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** One editable line of the review list. Text fields stay text until the entry is saved. */
private data class Entry(
    val kind: DocKind,
    val currency: String,
    val name: String = "",
    val amount: String = "",
    val date: String = "",
    val cycle: Cycle = Cycle.MONTHLY,
    val category: String = "Other",
    val billType: BillType = BillType.OTHER,
    val qty: String = "1",
    val unit: String = "pcs",
    val tenure: String = "",
    val rate: String = "",
    val principal: String = "",
    val selected: Boolean = true,
)

private fun plain(minor: Long?, currency: String) = minor?.let { Money.toPlain(it, currency) }.orEmpty()

private fun Detected.toEntries(): List<Entry> =
    if (kind == DocKind.GROCERY) items.map {
        Entry(
            DocKind.GROCERY, currency, it.name, plain(it.priceMinor, currency), "", Cycle.MONTHLY, it.category,
            qty = BigDecimal(it.quantityMilli).movePointLeft(3).stripTrailingZeros().toPlainString(), unit = it.unit,
        )
    } else listOf(
        Entry(
            kind, currency, name, plain(amountMinor, currency), date?.toString().orEmpty(), cycle, category, billType,
            tenure = tenureMonths?.toString().orEmpty(), rate = ratePercent?.toPlainString().orEmpty(), principal = plain(principalMinor, currency),
        ),
    )

private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim()) }.getOrNull()

private fun Entry.amountMinor(): Long? = Money.parseMinor(amount.replace(',', '.').trim(), currency)

private fun Entry.valid(): Boolean = name.isNotBlank() && when (kind) {
    DocKind.GROCERY -> qty.replace(',', '.').toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true
    DocKind.BILL -> true
    DocKind.PAYMENT, DocKind.SUBSCRIPTION, DocKind.EMI -> (amountMinor() ?: 0) > 0
} && (date.isBlank() || parseDate(date) != null)

private fun List<Entry>.toDetected(): List<Detected> {
    val picked = filter { it.selected && it.valid() }
    val groceries = picked.filter { it.kind == DocKind.GROCERY }.map { e ->
        ScannedItem(
            e.name.trim(), e.qty.replace(',', '.').toBigDecimal().movePointRight(3).toLong(), e.unit.trim().ifBlank { "pcs" }.take(12),
            if (e.category in STOCK_CATEGORIES) e.category else "Grocery", e.amountMinor(),
        )
    }
    val others = picked.filter { it.kind != DocKind.GROCERY }.map { e ->
        Detected(
            e.kind, e.name.trim(), e.amountMinor(), e.currency, parseDate(e.date), e.cycle,
            if (e.category in CATEGORIES) e.category else "Other", e.billType,
            tenureMonths = e.tenure.trim().toIntOrNull(), ratePercent = e.rate.replace(',', '.').trim().toBigDecimalOrNull(),
            principalMinor = e.principal.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.let { Money.parseMinor(it, e.currency) },
        )
    }
    return others + if (groceries.isNotEmpty()) listOf(Detected(DocKind.GROCERY, "Groceries", null, first().currency, items = groceries)) else emptyList()
}

/** Any photo or screenshot: read on the phone, sorted into subscription, EMI, bill, groceries or a payment note, then reviewed before saving. */
@Composable
fun ScanAnythingDialog(vm: MainViewModel, currency: String, initial: Uri?, preloaded: List<Detected>? = null, note: String? = null, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val entries = remember { mutableStateListOf<Entry>() }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf(note) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    fun scan(uri: Uri) = scope.launch {
        busy = true
        message = null
        try {
            val read = GroceryScanner.read(ctx, uri)
            val found = DocScan.classify(read.text, read.labels, currency, LocalDate.now())
            if (found == null) message = "Could not tell what this is. Try a clearer photo or a closer crop."
            else entries += found.toEntries().filter { n -> entries.none { it.kind == n.kind && it.name.equals(n.name, true) } }
        } catch (e: Exception) {
            message = "Could not read that photo."
        }
        busy = false
    }

    LaunchedEffect(initial) { initial?.let { scan(it) } }
    LaunchedEffect(preloaded) { preloaded?.forEach { entries += it.toEntries() } }
    val sheetPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            message = null
            try {
                val r = vm.loadSheet(uri)
                r.items.forEach { entries += it.toEntries() }
                info = vm.sheetSummary(r)
            } catch (e: Exception) {
                message = e.message ?: "Could not read that file"
            }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) scan(uri) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) scan(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)).invokeOnCompletion { f.delete() } else f?.delete()
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Scan anything", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "A bill, EMI message, subscription receipt, grocery receipt or any payment screenshot. It is read on this phone, sorted into the right tracker, and shown here for you to check.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button({
                        val f = File(ctx.cacheDir, "camera/${UUID.randomUUID()}.jpg").also { it.parentFile?.mkdirs() }
                        cameraFile = f
                        camera.launch(FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f))
                    }, enabled = !busy) { Text(if (entries.isEmpty()) "Take photo" else "Add another") }
                    OutlinedButton({ picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, enabled = !busy) { Text("Photo or screenshot") }
                }
                OutlinedButton(
                    { sheetPicker.launch(arrayOf("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "application/octet-stream", "text/plain")) },
                    Modifier.fillMaxWidth(), enabled = !busy,
                ) {
                    Icon(AppIcons.Sheet, null, Modifier.size(18.dp))
                    Text("  Excel or CSV spreadsheet")
                }
                if (entries.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DocKind.entries.forEach { k ->
                        val n = entries.count { it.kind == k && it.selected }
                        if (n > 0) {
                            val (icon, brush) = kindStyle(k)
                            Row(
                                Modifier.clip(RoundedCornerShape(50)).background(brush.from.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(icon, null, Modifier.size(14.dp), tint = brush.from)
                                Text(" $n", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                info?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (busy) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.padding(4.dp))
                    Text("Reading...")
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    itemsIndexed(entries, key = { i, e -> "$i${e.kind}" }) { i, e -> EntryCard(e) { entries[i] = it } }
                }
                val chosen = entries.toDetected()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onDismiss) { Text("Cancel") }
                    Button({
                        vm.saveDetected(chosen) { summary ->
                            Toast.makeText(ctx, summary, Toast.LENGTH_LONG).show()
                            onDismiss()
                        }
                    }, Modifier.weight(1f), enabled = chosen.isNotEmpty() && !busy) { Text("Add to my trackers") }
                }
            }
        }
    }
}

@Composable
private fun EntryCard(e: Entry, onChange: (Entry) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(e.selected, { onChange(e.copy(selected = it)) })
                KindBadge(e.kind)
                Dropdown("This is a", e.kind, DocKind.entries, { it.label }) { onChange(e.copy(kind = it)) }
            }
            OutlinedTextField(e.name, { onChange(e.copy(name = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Name") })
            val money = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    e.amount, { onChange(e.copy(amount = it)) }, Modifier.weight(1f), singleLine = true,
                    label = { Text(if (e.kind == DocKind.GROCERY) "Paid (${e.currency})" else "Amount (${e.currency})") }, keyboardOptions = money,
                    isError = e.selected && e.kind != DocKind.GROCERY && e.kind != DocKind.BILL && (e.amountMinor() ?: 0) <= 0,
                )
                if (e.kind != DocKind.GROCERY) OutlinedTextField(
                    e.date, { onChange(e.copy(date = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Date (yyyy-mm-dd)") },
                    isError = e.date.isNotBlank() && parseDate(e.date) == null,
                )
            }
            val repeats = listOf(Cycle.WEEKLY, Cycle.MONTHLY, Cycle.QUARTERLY, Cycle.YEARLY)
            val cycleText: (Cycle) -> String = { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }
            when (e.kind) {
                DocKind.SUBSCRIPTION -> {
                    Dropdown("Repeats", e.cycle, repeats, cycleText) { onChange(e.copy(cycle = it)) }
                    Dropdown("Category", e.category.takeIf { it in CATEGORIES } ?: "Other", CATEGORIES, { it }) { onChange(e.copy(category = it)) }
                }
                DocKind.BILL -> {
                    Dropdown("Bill type", e.billType, BillType.entries, ::billTypeLabel) { onChange(e.copy(billType = it)) }
                    Dropdown("Repeats", e.cycle, repeats, cycleText) { onChange(e.copy(cycle = it)) }
                }
                DocKind.EMI -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(e.tenure, { onChange(e.copy(tenure = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Months (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(e.rate, { onChange(e.copy(rate = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Rate % (optional)") }, keyboardOptions = money)
                    }
                    OutlinedTextField(e.principal, { onChange(e.copy(principal = it)) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("Loan amount (optional)") }, keyboardOptions = money)
                    Text("Missing loan terms are estimated from the EMI. Open the loan later to correct them.", style = MaterialTheme.typography.bodySmall)
                }
                DocKind.GROCERY -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(e.qty, { onChange(e.copy(qty = it)) }, Modifier.weight(1f), singleLine = true, label = { Text("Qty") }, keyboardOptions = money)
                        OutlinedTextField(e.unit, { onChange(e.copy(unit = it.take(12))) }, Modifier.weight(1f), singleLine = true, label = { Text("Unit") })
                    }
                    Dropdown("Category", e.category.takeIf { it in STOCK_CATEGORIES } ?: "Grocery", STOCK_CATEGORIES, { it }) { onChange(e.copy(category = it)) }
                }
                DocKind.PAYMENT -> Text("Saved as a transaction note. It can also suggest subscriptions when it repeats.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

private fun kindStyle(k: DocKind): Pair<ImageVector, TrackerBrush> = when (k) {
    DocKind.SUBSCRIPTION -> AppIcons.Subscriptions to Brushes.subscription
    DocKind.EMI -> AppIcons.Loans to Brushes.loan
    DocKind.BILL -> AppIcons.Bills to Brushes.bill
    DocKind.GROCERY -> AppIcons.Stock to Brushes.stock
    DocKind.PAYMENT -> AppIcons.Transactions to Brushes.berry
}

/** The tracker's colour and icon, so a list of mixed records stays easy to read at a glance. */
@Composable
private fun KindBadge(kind: DocKind) {
    val (icon, brush) = kindStyle(kind)
    Box(
        Modifier.padding(horizontal = 6.dp).size(40.dp).clip(RoundedCornerShape(14.dp)).background(Brush.linearGradient(listOf(brush.from, brush.to))),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, Modifier.size(22.dp), tint = brush.on) }
}
