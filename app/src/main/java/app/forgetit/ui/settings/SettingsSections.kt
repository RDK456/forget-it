package app.forgetit.ui.settings

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.forgetit.ui.MainViewModel
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.forgetit.data.ThemeMode
import app.forgetit.reminders.Health
import app.forgetit.reminders.NotificationHealth
import app.forgetit.ui.lock.canAuthenticate

@Composable
fun SectionTitle(text: String) =
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))

@Composable
fun ThemeSection(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    SectionTitle("Theme")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { m ->
            FilterChip(current == m, { onPick(m) }, { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
        }
    }
}

@Composable
fun LockSection(enabled: Boolean, onChange: (Boolean) -> Unit) {
    val ctx = LocalContext.current
    val available = canAuthenticate(ctx)
    SectionTitle("Privacy")
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Lock the app")
            Text(
                if (available) "Ask for fingerprint, face or screen lock; hides the app in recents."
                else "Set up a screen lock or fingerprint on this device first.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(enabled, onChange, enabled = available || enabled)
    }
}

private fun open(ctx: Context, intent: Intent) = runCatching { ctx.startActivity(intent) }

@Composable
fun HealthSection(health: Health, onTest: () -> Unit) {
    val ctx = LocalContext.current
    SectionTitle("Notification health")
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (health.notificationsAllowed) "Notifications are allowed" else "Notifications are OFF - reminders cannot show",
                color = if (health.notificationsAllowed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
            )
            if (health.blockedChannels.isNotEmpty()) {
                Text("Blocked channels: ${health.blockedChannels.joinToString()}", color = MaterialTheme.colorScheme.error)
            }
            Text(
                if (health.batteryOptimized) "Battery optimization is on; some phones delay reminders. Consider allowing unrestricted use."
                else "Battery optimization is off for this app",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ open(ctx, NotificationHealth.notificationSettingsIntent(ctx)) }) { Text("Notifications") }
                OutlinedButton({ open(ctx, NotificationHealth.batterySettingsIntent()) }) { Text("Battery") }
            }
            OutlinedButton(onTest) { Text("Send test notification") }
        }
    }
}

@Composable
private fun CsvRow(
    label: String, fileName: String, onMessage: (String) -> Unit,
    export: (android.net.Uri, (String) -> Unit) -> Unit, import: (android.net.Uri, (String) -> Unit) -> Unit,
) {
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> if (uri != null) export(uri, onMessage) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) import(uri, onMessage) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        OutlinedButton({ exportLauncher.launch(fileName) }) { Text("Export") }
        OutlinedButton({ importLauncher.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) { Text("Import") }
    }
}

@Composable
fun BackupSection(vm: MainViewModel) {
    var message by remember { mutableStateOf<String?>(null) }
    SectionTitle("Backup (CSV)")
    Text(
        "Export each tracker as a spreadsheet file and import it again later. Photos are not included, so keep them in your gallery too.",
        style = MaterialTheme.typography.bodySmall,
    )
    CsvRow("Subscriptions", "forgetit-subscriptions.csv", { message = it }, vm::exportSubscriptions, vm::importSubscriptions)
    CsvRow("Loans and EMIs", "forgetit-loans.csv", { message = it }, vm::exportLoans, vm::importLoans)
    CsvRow("Household stock", "forgetit-stock.csv", { message = it }, vm::exportStock, vm::importStock)
    CsvRow("Bills and utilities", "forgetit-bills.csv", { message = it }, vm::exportBills, vm::importBills)
    FullBackupRow(vm) { message = it }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}

@Composable
private fun FullBackupRow(vm: MainViewModel, onMessage: (String) -> Unit) {
    val export = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.exportFull(uri, onMessage)
    }
    val import = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importFull(uri, onMessage)
    }
    var confirm by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
        Text("Full backup (everything, with photos)", style = MaterialTheme.typography.titleSmall)
        Text("One zip with all trackers and photos. Restoring replaces everything on this phone and restarts the app.", style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Button({ export.launch("forgetit-backup.zip") }) { Text("Save backup") }
            androidx.compose.material3.OutlinedButton({ confirm = true }) { Text("Restore") }
        }
    }
    if (confirm) app.forgetit.ui.loans.ConfirmDialog(
        "Restore from a backup?", "Everything on this phone is replaced by the backup, including photos.", "Choose file",
        { confirm = false; import.launch(arrayOf("application/zip", "application/octet-stream")) }, { confirm = false },
    )
}
