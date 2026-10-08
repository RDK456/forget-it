package app.forgetit.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.forgetit.ui.GradientHeader
import app.forgetit.ui.AppIcons
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.theme.Brushes
import app.forgetit.update.UpdateState

/** Version, the automatic-check switch, and the whole update flow in one card at the top of Settings. */
@Composable
fun UpdateSection(vm: MainViewModel, autoCheck: Boolean) {
    val updater = vm.c.updater
    val state by updater.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val available = state as? UpdateState.Available
    GradientHeader(
        AppIcons.Download, if (available != null) "Update available" else "Forget-it", Brushes.scan, bigText = "Version " + (available?.plan?.version ?: updater.currentVersion),
        supporting = when (val s = state) {
            is UpdateState.Available -> "You have ${updater.currentVersion}. " + (s.plan.delta?.let { "Small patch: ${Formatter.formatShortFileSize(ctx, s.plan.downloadSize)} instead of ${Formatter.formatShortFileSize(ctx, s.plan.target.size)}" }
                ?: "Download ${Formatter.formatShortFileSize(ctx, s.plan.downloadSize)}")
            is UpdateState.UpToDate -> "You are on the latest version."
            is UpdateState.Checking -> "Checking..."
            else -> "Updates come from this app's GitHub releases."
        },
    )
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        available?.plan?.notes?.takeIf { it.isNotBlank() }?.let { Text(it.take(500), style = MaterialTheme.typography.bodySmall) }
        when (val s = state) {
            is UpdateState.Working -> {
                Text(s.label, style = MaterialTheme.typography.bodyMedium)
                if (s.fraction >= 0) LinearProgressIndicator({ s.fraction }, Modifier.fillMaxWidth()) else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            is UpdateState.NeedsPermission -> {
                Text("Android needs your permission to let Forget-it install updates.", style = MaterialTheme.typography.bodyMedium)
                Button({ ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Allow installs") }
                OutlinedButton({ updater.checkAsync() }) { Text("Done, check again") }
            }
            is UpdateState.Failed -> {
                Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                OutlinedButton({ updater.checkAsync() }) { Text("Try again") }
            }
            is UpdateState.Available -> Button({ updater.installAsync(s.plan) }, Modifier.fillMaxWidth()) { Text("Update to ${s.plan.version}") }
            is UpdateState.Checking -> {}
            else -> OutlinedButton({ updater.checkAsync() }, Modifier.fillMaxWidth()) { Text("Check for updates") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Check automatically every day", Modifier.weight(1f))
            Switch(autoCheck, vm::setAutoUpdateCheck)
        }
    }
}
