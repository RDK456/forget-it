package app.forgetit.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.ChargeCalendar
import app.forgetit.domain.EntryType
import app.forgetit.domain.loanCalendarEntries
import app.forgetit.domain.stockCalendarEntries
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

private fun dotColor(t: EntryType) = when (t) {
    EntryType.SUBSCRIPTION -> Color(0xFF3D5AFE)
    EntryType.EMI -> Color(0xFFE53935)
    EntryType.EXPIRY -> Color(0xFFFB8C00)
}

@Composable
fun CalendarScreen(vm: MainViewModel, today: LocalDate, onBack: () -> Unit) {
    val subs by vm.subs.collectAsStateWithLifecycle()
    val loans by vm.loans.collectAsStateWithLifecycle()
    val loanPay by vm.loanPayments.collectAsStateWithLifecycle()
    val loanAdj by vm.loanAdjustments.collectAsStateWithLifecycle()
    val stockItems by vm.stockItems.collectAsStateWithLifecycle()
    val stockBatches by vm.stockBatches.collectAsStateWithLifecycle()
    var monthIndex by rememberSaveable { mutableStateOf(today.year * 12 + today.monthValue - 1) }
    var selected by rememberSaveable { mutableStateOf<Long?>(today.toEpochDay()) }
    val month = YearMonth.of(monthIndex / 12, monthIndex % 12 + 1)
    val subEntries = ChargeCalendar.subscriptionEntries(subs, month)
    val emiEntries = loanCalendarEntries(loans, loanAdj, loanPay, month, today)
    val expiryEntries = stockCalendarEntries(stockItems, stockBatches, month)
    val entries = (subEntries.keys + emiEntries.keys + expiryEntries.keys).associateWith { subEntries[it].orEmpty() + emiEntries[it].orEmpty() + expiryEntries[it].orEmpty() }
    val first = WeekFields.of(Locale.getDefault()).firstDayOfWeek
    val lead = (month.atDay(1).dayOfWeek.value - first.value + 7) % 7

    ScreenScaffold("Calendar", onBack) { pad ->
        ListScreen(pad) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton({ monthIndex-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous month") }
                    Text("${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}", style = MaterialTheme.typography.titleLarge)
                    IconButton({ monthIndex++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next month") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until 7) Text(
                        first.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            val cells = lead + month.lengthOfMonth()
            for (row in 0 until (cells + 6) / 7) item(key = "row$row") {
                Row(Modifier.fillMaxWidth()) {
                    for (col in 0 until 7) {
                        val day = row * 7 + col - lead + 1
                        if (day < 1 || day > month.lengthOfMonth()) { Box(Modifier.weight(1f)) ; continue }
                        val date = month.atDay(day)
                        DayCell(date, entries[date].orEmpty().map { it.type }.distinct(), date == today, date.toEpochDay() == selected, Modifier.weight(1f)) {
                            selected = date.toEpochDay()
                        }
                    }
                }
            }
            val sel = selected?.let(LocalDate::ofEpochDay)
            if (sel != null) {
                item { Text(sel.toString(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                val day = entries[sel].orEmpty()
                if (day.isEmpty()) item { Text("Nothing on this day.", style = MaterialTheme.typography.bodyMedium) }
                for (e in day) item(key = "${e.date}${e.title}") {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(dotColor(e.type)))
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text(e.detail, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, types: List<EntryType>, isToday: Boolean, isSelected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isToday -> MaterialTheme.colorScheme.surfaceVariant
        else -> Color.Transparent
    }
    Column(
        modifier.aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(12.dp)).background(bg).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
        Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            types.forEach { Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor(it))) }
        }
    }
}
