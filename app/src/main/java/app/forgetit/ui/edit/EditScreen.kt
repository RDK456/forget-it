package app.forgetit.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.forgetit.domain.CATEGORIES
import app.forgetit.domain.Cycle
import app.forgetit.domain.PRESETS
import app.forgetit.domain.Validator
import app.forgetit.ui.ScreenScaffold

private fun cycleName(c: Cycle) = when (c) {
    Cycle.WEEKLY -> "Weekly"
    Cycle.MONTHLY -> "Monthly"
    Cycle.QUARTERLY -> "Every 3 months"
    Cycle.YEARLY -> "Yearly"
    Cycle.CUSTOM_DAYS -> "Custom (days)"
}

@Composable
fun EditScreen(vm: EditViewModel, onDone: () -> Unit, onSaved: () -> Unit = onDone, photos: @Composable () -> Unit = {}) {
    val f = vm.form
    val err = vm.errors
    val uri = LocalUriHandler.current
    LaunchedEffect(vm.savedId) { if (vm.savedId != null) onSaved() }

    ScreenScaffold(if (vm.id == 0L) "Add subscription" else "Edit subscription", onBack = onDone) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.id == 0L) {
                Text("Quick pick", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(PRESETS, key = { it.key }) { p -> AssistChip(onClick = { vm.applyPreset(p) }, label = { Text(p.name) }) }
                }
            }
            OutlinedTextField(
                f.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Name") }, singleLine = true,
                isError = "name" in err, supportingText = { err["name"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    f.amount, { v -> vm.update { it.copy(amount = v) } }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    isError = "amount" in err, supportingText = { err["amount"]?.let { Text(it) } }, modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    f.currency, { v -> vm.update { it.copy(currency = v.take(3).uppercase()) } }, label = { Text("Currency") },
                    singleLine = true, isError = "currency" in err, supportingText = { err["currency"]?.let { Text(it) } },
                    modifier = Modifier.weight(0.6f),
                )
            }
            Dropdown("Billing cycle", f.cycle, Cycle.entries, ::cycleName) { c -> vm.update { it.copy(cycle = c) } }
            if (f.cycle == Cycle.CUSTOM_DAYS) {
                OutlinedTextField(
                    f.customDays, { v -> vm.update { it.copy(customDays = v) } }, label = { Text("Every how many days") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = "customDays" in err, supportingText = { err["customDays"]?.let { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DateField("First billing date", f.startDate, { d -> vm.update { it.copy(startDate = d) } })
            Dropdown("Category", f.category, CATEGORIES, { it }) { c -> vm.update { it.copy(category = c) } }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Free trial", Modifier.weight(1f)); Switch(f.isTrial, vm::setTrial)
            }
            if (f.isTrial) {
                DateField("Trial ends", f.trialEndsAt, { d -> vm.update { it.copy(trialEndsAt = d) } })
                err["trialEndsAt"]?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
            Text("Remind me ${f.remindDays} day(s) before", style = MaterialTheme.typography.labelLarge)
            Slider(f.remindDays.toFloat(), { v -> vm.update { it.copy(remindDays = v.toInt()) } }, valueRange = 0f..30f, steps = 29)
            ExtraRemindChips(f.extraRemind) { l -> vm.update { it.copy(extraRemind = l) } }

            OutlinedTextField(
                f.cancelUrl, { v -> vm.update { it.copy(cancelUrl = v) } }, label = { Text("Cancel or account page (optional)") },
                singleLine = true, isError = "cancelUrl" in err, supportingText = { err["cancelUrl"]?.let { Text(it) } },
                modifier = Modifier.fillMaxWidth(),
            )
            if (f.cancelUrl.isNotBlank() && Validator.isHttpUrl(f.cancelUrl)) {
                OutlinedButton(onClick = { uri.openUri(f.cancelUrl.trim()) }) { Text("Open cancel page") }
            }
            OutlinedTextField(
                f.paymentMethod, { v -> vm.update { it.copy(paymentMethod = v) } }, label = { Text("Paid with (optional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                f.notes, { v -> vm.update { it.copy(notes = v) } }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth(),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active (counts in totals and reminders)", Modifier.weight(1f)); Switch(f.active, { v -> vm.update { it.copy(active = v) } })
            }
            photos()
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Save") }
        }
    }
}
