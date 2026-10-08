package app.forgetit.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
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
import app.forgetit.domain.STOCK_CATEGORIES
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.edit.DateField
import app.forgetit.ui.edit.Dropdown

private val UNITS = listOf("pcs", "L", "ml", "kg", "g", "pack")

@Composable
fun StockEditScreen(vm: StockEditViewModel, onDone: () -> Unit, onSaved: (Long) -> Unit, photos: @Composable () -> Unit) {
    val f = vm.form
    val err = vm.errors
    LaunchedEffect(vm.savedId) { vm.savedId?.let(onSaved) }

    @Composable
    fun num(label: String, value: String, key: String, set: (String) -> Unit) = OutlinedTextField(
        value, set, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = key in err, supportingText = { err[key]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
    )

    ScreenScaffold(if (vm.id == 0L) "Add stock item" else "Edit item", onBack = onDone) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                f.name, { v -> vm.update { it.copy(name = v) } }, label = { Text("Name (for example Milk)") }, singleLine = true,
                isError = "name" in err, supportingText = { err["name"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { UNITS.forEach { u -> AssistChip({ vm.update { it.copy(unit = u) } }, { Text(u) }) } }
            OutlinedTextField(
                f.unit, { v -> vm.update { it.copy(unit = v.take(12)) } }, label = { Text("Unit") }, singleLine = true,
                isError = "unit" in err, supportingText = { err["unit"]?.let { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Dropdown("Category", f.category, STOCK_CATEGORIES, { it }) { c -> vm.update { it.copy(category = c) } }
            num("Warn me when ${f.unit} left is at or below", f.threshold, "threshold") { v -> vm.update { it.copy(threshold = v) } }
            num("Usage per day (optional, else learned from your usage)", f.usage, "usage") { v -> vm.update { it.copy(usage = v) } }
            OutlinedTextField(f.store, { v -> vm.update { it.copy(store = v) } }, label = { Text("Usual store (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(f.brand, { v -> vm.update { it.copy(brand = v) } }, label = { Text("Preferred brand (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            num("Pack size in ${f.unit} (optional, shopping list rounds up to whole packs)", f.pack, "pack") { v -> vm.update { it.copy(pack = v) } }
            Text("Delivery or refill lead time: ${f.leadDays} day(s)", style = MaterialTheme.typography.labelLarge)
            Slider(f.leadDays.toFloat(), { v -> vm.update { it.copy(leadDays = v.toInt()) } }, valueRange = 0f..14f, steps = 13)
            Text("Expiry reminder ${f.expiryAlertDays} day(s) before", style = MaterialTheme.typography.labelLarge)
            Slider(f.expiryAlertDays.toFloat(), { v -> vm.update { it.copy(expiryAlertDays = v.toInt()) } }, valueRange = 0f..30f, steps = 29)
            if (vm.id == 0L) {
                Text("Current stock (optional)", style = MaterialTheme.typography.titleSmall)
                num("How much do you have now?", f.startQty, "startQty") { v -> vm.update { it.copy(startQty = v) } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("It has an expiry date", Modifier.weight(1f)); Switch(f.hasExpiry, { v -> vm.update { it.copy(hasExpiry = v) } })
                }
                if (f.hasExpiry) DateField("Expires", f.startExpiry, { d -> vm.update { it.copy(startExpiry = d) } })
            }
            OutlinedTextField(f.notes, { v -> vm.update { it.copy(notes = v) } }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active (counts in alerts)", Modifier.weight(1f)); Switch(f.active, { v -> vm.update { it.copy(active = v) } })
            }
            photos()
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Save") }
        }
    }
}
