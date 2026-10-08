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
import androidx.compose.material3.Card
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
    Card(Modifier.fillMaxWidth()) {
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
fun BackupSection(vm: MainViewModel) {
    var message by remember { mutableStateOf<String?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) vm.exportSubscriptions(uri) { message = it }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importSubscriptions(uri) { message = it }
    }
    SectionTitle("Backup (CSV)")
    Text("Export or import subscriptions as a spreadsheet file. Photos are not included, so keep them in your gallery too.", style = MaterialTheme.typography.bodySmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton({ export.launch("forgetit-subscriptions.csv") }) { Text("Export") }
        OutlinedButton({ import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel")) }) { Text("Import") }
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
}
