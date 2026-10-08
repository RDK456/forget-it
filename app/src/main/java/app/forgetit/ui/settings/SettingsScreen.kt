package app.forgetit.ui.settings

import app.forgetit.ui.AppIcons
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.domain.Money
import app.forgetit.reminders.NotificationHealth
import app.forgetit.ui.ListScreen
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import java.math.BigDecimal

@Composable
fun SettingsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val subs by vm.subs.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var health by remember { mutableStateOf(NotificationHealth.check(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { health = NotificationHealth.check(ctx) }
    var currencyText by rememberSaveable(s.defaultCurrency) { mutableStateOf(s.defaultCurrency) }
    var rateCode by rememberSaveable { mutableStateOf("") }
    var rateValue by rememberSaveable { mutableStateOf("") }
    var timeDialog by remember { mutableStateOf(false) }
    val needed = subs.map { it.currency }.toSet() - s.defaultCurrency - s.rates.keys

    ScreenScaffold("Settings", onBack) { pad ->
        ListScreen(pad) {
            item { SectionTitle("Default currency") }
            item {
                OutlinedTextField(
                    currencyText,
                    { v ->
                        currencyText = v.take(3).uppercase()
                        if (Money.isValidCurrency(currencyText)) vm.setCurrency(currencyText)
                    },
                    label = { Text("Currency code, for example USD or INR") }, singleLine = true,
                    isError = !Money.isValidCurrency(currencyText), modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SectionTitle("Monthly budget") }
            item {
                var b by rememberSaveable(s.budgetMinor) {
                    mutableStateOf(if (s.budgetMinor > 0) java.math.BigDecimal(s.budgetMinor).movePointLeft(Money.fractionDigits(s.defaultCurrency)).toPlainString() else "")
                }
                val parsed = if (b.isBlank()) 0L else Money.parseMinor(b, s.defaultCurrency)
                OutlinedTextField(
                    b, { v -> b = v; val m = if (v.isBlank()) 0L else Money.parseMinor(v, s.defaultCurrency); if (m != null && m >= 0) vm.setBudget(m) },
                    label = { Text("Total per month in ${s.defaultCurrency}, empty for none") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = parsed == null, modifier = Modifier.fillMaxWidth(),
                )
            }
            item { SectionTitle("Exchange rates") }
            item { Text("Rates convert other currencies into ${s.defaultCurrency} for totals. They are entered by hand and never fetched online.") }
            if (needed.isNotEmpty()) item { Text("Missing a rate for: ${needed.joinToString()}", color = MaterialTheme.colorScheme.error) }
            for ((code, r) in s.rates) item(key = "rate$code") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("1 $code = ${r.value.toPlainString()} ${s.defaultCurrency}  (set ${r.editedOn})", Modifier.weight(1f))
                    IconButton({ vm.removeRate(code) }) { Icon(AppIcons.Delete, "Remove rate for $code") }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(rateCode, { rateCode = it.take(3).uppercase() }, label = { Text("From") }, singleLine = true, modifier = Modifier.weight(0.7f))
                    OutlinedTextField(
                        rateValue, { rateValue = it }, label = { Text("Rate") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f),
                    )
                    val parsed = rateValue.replace(',', '.').toBigDecimalOrNull()
                    Button(
                        onClick = { vm.setRate(rateCode, parsed!!); rateCode = ""; rateValue = "" },
                        enabled = Money.isValidCurrency(rateCode) && rateCode != s.defaultCurrency && parsed != null && parsed > BigDecimal.ZERO,
                    ) { Text("Add") }
                }
            }
            item { SectionTitle("Auto-scan messages") }
            item { var m by remember { mutableStateOf<String?>(null) }; Column { app.forgetit.ui.txn.AutoScanSection(vm, s) { m = it }; m?.let { Text(it, style = MaterialTheme.typography.bodySmall) } } }
            item { SectionTitle("Reminder time") }
            item {
                val m = s.reminderMinuteOfDay
                OutlinedButton({ timeDialog = true }, Modifier.fillMaxWidth()) { Text("Remind me at %02d:%02d".format(m / 60, m % 60)) }
            }
            item { SectionTitle("Payday and weekly summary") }
            item {
                app.forgetit.ui.edit.Dropdown("Payday (snooze a reminder until then)", s.paydayDay, (0..31).toList(), { if (it == 0) "Not set" else "Day $it of the month" }) { vm.setPayday(it) }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Weekly summary every Monday", Modifier.weight(1f)); androidx.compose.material3.Switch(s.weeklyDigest, vm::setWeeklyDigest)
                }
            }
            item { ThemeSection(s.theme, vm::setTheme) }
            item { LockSection(s.biometricLock, vm::setBiometric) }
            item { HealthSection(health, onTest = { vm.c.reminders.sendTest() }) }
            item { BackupSection(vm) }
        }
    }
    if (timeDialog) TimeDialog(s.reminderMinuteOfDay, { vm.setReminderMinute(it); timeDialog = false }, { timeDialog = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeDialog(minute: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    val st = rememberTimePickerState(minute / 60, minute % 60, DateFormat.is24HourFormat(LocalContext.current))
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton({ onPick(st.hour * 60 + st.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
        text = { TimePicker(st) },
    )
}
