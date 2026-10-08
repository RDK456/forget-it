package app.forgetit.ui.txn

import android.Manifest
import android.content.Context
import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.forgetit.data.Settings
import app.forgetit.txn.SmsScanner
import app.forgetit.ui.MainViewModel

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

private fun mailAccess(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

private fun openMailAccessSettings(ctx: Context) =
    ctx.startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))

private fun scanMessage(n: Int) = when {
    n < 0 -> "SMS permission is missing"
    n == 0 -> "Scan done. Nothing new found."
    else -> "$n new transaction(s) found"
}

/** Re-checks the two Android permissions each time the screen comes back to the front. */
@Composable
private fun rememberAccess(): Triple<Boolean, Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var sms by remember { mutableStateOf(SmsScanner.hasPermission(ctx)) }
    var mail by remember { mutableStateOf(mailAccess(ctx)) }
    val refresh = { sms = SmsScanner.hasPermission(ctx); mail = mailAccess(ctx) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val o = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh() }
        owner.lifecycle.addObserver(o)
        onDispose { owner.lifecycle.removeObserver(o) }
    }
    return Triple(sms, mail, refresh)
}

/** Auto-scan switch, the two permissions it needs, and a Scan now button. Shared by Settings and Transactions. */
@Composable
fun AutoScanSection(vm: MainViewModel, s: Settings, onMessage: (String) -> Unit) {
    val ctx = LocalContext.current
    val (sms, mail, refresh) = rememberAccess()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        refresh()
        vm.scanNow { onMessage(scanMessage(it)) }
    }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Scan automatically", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Finds payments in new SMS and email alerts, and catches up on your inbox once a day. Everything stays on this phone; only payments are kept, with a short snippet.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(s.autoScan, vm::setAutoScan)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("SMS", style = MaterialTheme.typography.titleSmall)
                    Text(if (sms) "Allowed" else "Not allowed yet", style = MaterialTheme.typography.bodySmall)
                }
                if (!sms) Button({ ask.launch(SMS_PERMISSIONS) }) { Text("Allow") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Email (gmail, outlook and others)", style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (mail) "Reading new-mail notifications" else "Needs notification access. Android may ask you to allow restricted settings first (App info, three-dot menu).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                OutlinedButton({ openMailAccessSettings(ctx) }) { Text(if (mail) "Manage" else "Allow") }
            }
            if (sms) OutlinedButton({ vm.scanNow { onMessage(scanMessage(it)) } }, Modifier.fillMaxWidth()) { Text("Scan now") }
            Text("Emails can also be shared: open one, tap Share, choose Forget-it.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Shown once on the first launch so that scanning starts without the user hunting for it. */
@Composable
fun ScanSetupDialog(vm: MainViewModel, onDone: () -> Unit) {
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.scanNow { }
        onDone()
    }
    AlertDialog(
        onDismissRequest = { vm.markScanPrompted(); onDone() },
        title = { Text("Find your subscriptions and EMIs?") },
        text = {
            Text(
                "Forget-it can read payment messages on this phone and add what it finds to Transactions, then suggest subscriptions and mark EMIs paid. Nothing leaves your phone. Email alerts can be turned on later in Settings.",
            )
        },
        confirmButton = { TextButton({ vm.markScanPrompted(); ask.launch(SMS_PERMISSIONS) }) { Text("Turn on") } },
        dismissButton = { TextButton({ vm.markScanPrompted(); onDone() }) { Text("Not now") } },
    )
}
