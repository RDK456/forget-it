package app.forgetit.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.forgetit.domain.BudgetLevel
import app.forgetit.domain.CategoryDetail
import app.forgetit.domain.Money
import app.forgetit.domain.SpendStats
import app.forgetit.domain.Txn
import app.forgetit.domain.payee
import app.forgetit.ui.AnimatedMoney
import app.forgetit.ui.AnimatedProgress
import app.forgetit.ui.AppIcons
import app.forgetit.ui.pressScale
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** One category in one month: how much, which merchants, which days and weeks, and every payment, each one editable. */
@Composable
fun CategoryDetailDialog(
    d: CategoryDetail, currency: String, limit: Long?,
    onDismiss: () -> Unit, onEdit: (Txn) -> Unit, onSetLimit: () -> Unit, onShowInLedger: () -> Unit,
) {
    var merchant by remember { mutableStateOf<String?>(null) }
    val shown = d.txns.filter { merchant == null || it.payee() == merchant }
    val tint = txnCategoryColor(d.category)
    val monthName = d.month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + d.month.year
    val dayFormat = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
    val shortMonth = d.month.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    val days = d.month.lengthOfMonth()
    val maxDay = (d.byDay.values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val maxWeek = (d.byWeek.maxOfOrNull { it.minor } ?: 0L).coerceAtLeast(1L)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onDismiss) { Icon(AppIcons.Back, "Back") }
                        Text(d.category, style = MaterialTheme.typography.headlineSmall)
                    }
                }
                item {
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f))) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CategoryTile(d.category, 56.dp)
                                Spacer(Modifier.width(14.dp))
                                Column {
                                    Text(monthName, style = MaterialTheme.typography.labelMedium)
                                    AnimatedMoney(d.totalMinor, currency, MaterialTheme.typography.headlineMedium)
                                    Text("${d.txns.size} payment(s) to ${d.byMerchant.size} place(s)", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            if (d.previousMinor > 0) {
                                val pct = Math.round((d.totalMinor - d.previousMinor) * 100.0 / d.previousMinor).toInt()
                                Text((if (pct >= 0) "+" else "") + "$pct% vs last month (${Money.format(d.previousMinor, currency)})", style = MaterialTheme.typography.labelLarge, color = tint)
                            } else if (d.totalMinor > 0) Text("Nothing in this category last month", style = MaterialTheme.typography.labelLarge, color = tint)
                            if (d.excluded > 0) Text("${d.excluded} payment(s) in other currencies are left out.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            if (limit != null) {
                                val level = SpendStats.level(d.totalMinor, limit)
                                val color = when (level) { BudgetLevel.OK -> MaterialTheme.colorScheme.primary; BudgetLevel.WARN -> Color(0xFFE09A00); BudgetLevel.OVER -> MaterialTheme.colorScheme.error }
                                AnimatedProgress((d.totalMinor.toFloat() / limit).coerceIn(0f, 1f), Modifier.fillMaxWidth(), color)
                                Text(
                                    if (d.totalMinor > limit) "Over the ${Money.format(limit, currency)} limit by ${Money.format(d.totalMinor - limit, currency)}"
                                    else "${Money.format(limit - d.totalMinor, currency)} left of the ${Money.format(limit, currency)} limit",
                                    style = MaterialTheme.typography.bodyMedium, color = color,
                                )
                            }
                            TextButton(onSetLimit) { Text(if (limit != null) "Edit limit" else "Set a monthly limit") }
                        }
                    }
                }
                if (d.txns.isEmpty()) item { Text("Nothing was spent here in $monthName.", style = MaterialTheme.typography.bodyMedium) } else {
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("When", style = MaterialTheme.typography.titleMedium)
                                Row(Modifier.fillMaxWidth().height(92.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.Bottom) {
                                    for (day in 1..days) {
                                        val v = d.byDay[day] ?: 0L
                                        Box(
                                            Modifier.weight(1f).height((v.toFloat() / maxDay * 84f).coerceAtLeast(2f).dp).clip(RoundedCornerShape(3.dp))
                                                .background(if (v > 0) tint else tint.copy(alpha = 0.18f)),
                                        )
                                    }
                                }
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    listOf(1, 8, 15, 22, days).distinct().forEach { Text("$it", style = MaterialTheme.typography.labelSmall) }
                                }
                                d.biggestDay?.let { (day, v) ->
                                    Text("Biggest day: ${d.month.atDay(day).format(dayFormat)}, ${Money.format(v, currency)}", style = MaterialTheme.typography.bodyMedium)
                                }
                                d.byWeek.forEach { w ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("$shortMonth ${w.fromDay}-${w.toDay}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(78.dp))
                                        AnimatedProgress(w.minor.toFloat() / maxWeek, Modifier.weight(1f), tint)
                                        Text(Money.format(w.minor, currency), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(86.dp), textAlign = androidx.compose.ui.text.style.TextAlign.End)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Where", style = MaterialTheme.typography.titleMedium)
                                d.byMerchant.forEach { m ->
                                    Column(Modifier.fillMaxWidth().pressScale { merchant = if (merchant == m.merchant) null else m.merchant }) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) {
                                                Text(m.merchant, style = MaterialTheme.typography.titleSmall, fontWeight = if (merchant == m.merchant) FontWeight.Bold else FontWeight.Normal)
                                                Text("${m.count} payment(s), ${m.percent}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Text(Money.format(m.minor, currency), style = MaterialTheme.typography.titleSmall)
                                        }
                                        AnimatedProgress(m.percent / 100f, Modifier.fillMaxWidth().padding(top = 4.dp), tint)
                                    }
                                }
                                Text("Tap a place to list only its payments.", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Every payment" + (merchant?.let { ", $it" } ?: ""), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            if (merchant != null) TextButton({ merchant = null }) { Text("Show all") }
                        }
                    }
                    items(shown, key = { "p${it.id}" }) { t ->
                        Row(Modifier.fillMaxWidth().pressScale { onEdit(t) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.payee(), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(t.date.format(dayFormat) + (if (t.note.isNotBlank() && t.merchant != null) ", ${t.note}" else ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Text(Money.format(t.amountMinor, t.currency), style = MaterialTheme.typography.titleSmall)
                        }
                    }
                }
                item { OutlinedButton(onShowInLedger, Modifier.fillMaxWidth()) { Text("Show in the main ledger") } }
            }
        }
    }
}
