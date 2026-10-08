package app.forgetit.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.forgetit.domain.BillType
import app.forgetit.domain.Cycle
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.billTypeLabel
import app.forgetit.ui.edit.DateField
import app.forgetit.ui.edit.Dropdown
import app.forgetit.ui.edit.ExtraRemindChips

private fun cycleName(c: Cycle) = when (c) {
    Cycle.WEEKLY -> "Weekly"
    Cycle.MONTHLY -> "Monthly"
    Cycle.QUARTERLY -> "Every 3 months"
    Cycle.YEARLY -> "Yearly"
    Cycle.CUSTOM_DAYS -> "Custom (days)"
}

@Composable
fun BillEditScreen(vm: BillEditViewModel, onDone: () -> Unit, onSaved: (Long) -> Unit, photos: @Composable () -> Unit) {
    val f = vm.form
    val err = vm.errors
    LaunchedEffect(vm.savedId) { vm.savedId?.let(onSaved) }

    ScreenScaffold(if (vm.id == 0L) "Add bill" else "Edit bill", onBack = onDone) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                f.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Name (for example Home electricity)") }, singleLine = true,
                isError = "name" in err, supportingText = { err["name"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Dropdown("Type", f.type, BillType.entries, ::billTypeLabel) { t -> vm.update { it.copy(type = t, name = if (it.name.isBlank()) billTypeLabel(t) else it.name) } }
            OutlinedTextField(
                f.currency, { v -> vm.update { it.copy(currency = v.take(3).uppercase()) } }, label = { Text("Currency") }, singleLine = true,
                isError = "currency" in err, supportingText = { err["currency"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Dropdown("How often", f.cycle, Cycle.entries, ::cycleName) { c -> vm.update { it.copy(cycle = c) } }
            if (f.cycle == Cycle.CUSTOM_DAYS) {
                OutlinedTextField(
                    f.customDays, { v -> vm.update { it.copy(customDays = v) } }, label = { Text("Every how many days") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = "customDays" in err, supportingText = { err["customDays"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
                )
            }
            DateField("Next due date", f.anchor, { d -> vm.update { it.copy(anchor = d) } })
            Text("Remind me ${f.remindDays} day(s) before", style = MaterialTheme.typography.labelLarge)
            Slider(f.remindDays.toFloat(), { v -> vm.update { it.copy(remindDays = v.toInt()) } }, valueRange = 0f..30f, steps = 29)
            ExtraRemindChips(f.extraRemind) { l -> vm.update { it.copy(extraRemind = l) } }
            OutlinedTextField(f.notes, { v -> vm.update { it.copy(notes = v) } }, label = { Text("Notes, account number (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active (counts in totals and reminders)", Modifier.weight(1f)); Switch(f.active, { v -> vm.update { it.copy(active = v) } })
            }
            photos()
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Save") }
        }
    }
}
