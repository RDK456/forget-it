package app.forgetit.ui.loans

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
import app.forgetit.domain.LoanType
import app.forgetit.domain.Money
import app.forgetit.ui.ScreenScaffold
import app.forgetit.ui.edit.DateField
import app.forgetit.ui.edit.Dropdown

@Composable
fun LoanEditScreen(vm: LoanEditViewModel, onDone: () -> Unit, onSaved: (Long) -> Unit, photos: @Composable () -> Unit) {
    val f = vm.form
    val err = vm.errors
    LaunchedEffect(vm.savedId) { vm.savedId?.let(onSaved) }

    fun field(label: String, value: String, key: String, kb: KeyboardType = KeyboardType.Text, modifier: Modifier = Modifier.fillMaxWidth(), set: (String) -> Unit) =
        @Composable {
            OutlinedTextField(
                value, set, label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = kb),
                isError = key in err, supportingText = { err[key]?.let { Text(it) } }, modifier = modifier,
            )
        }

    ScreenScaffold(if (vm.id == 0L) "Add loan or EMI" else "Edit loan", onBack = onDone) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            field("Name (for example Car loan)", f.name, "name") { v -> vm.update { it.copy(name = v) } }()
            field("Lender (optional)", f.lender, "lender") { v -> vm.update { it.copy(lender = v) } }()
            Dropdown("Type", f.type, LoanType.entries, { it.name.lowercase().replaceFirstChar { c -> c.uppercase() } }) { t -> vm.update { it.copy(type = t) } }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Loan amount", f.principal, "principal", KeyboardType.Decimal, Modifier.weight(1f)) { v -> vm.update { it.copy(principal = v) } }()
                field("Currency", f.currency, "currency", modifier = Modifier.weight(0.6f)) { v -> vm.update { it.copy(currency = v.take(3).uppercase()) } }()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                field("Interest % per year", f.rate, "rate", KeyboardType.Decimal, Modifier.weight(1f)) { v -> vm.update { it.copy(rate = v) } }()
                field("Months", f.tenure, "tenure", KeyboardType.Number, Modifier.weight(0.7f)) { v -> vm.update { it.copy(tenure = v) } }()
            }
            DateField("First EMI date", f.firstEmi, { d -> vm.update { it.copy(firstEmi = d) } })
            vm.estimate()?.let { (emi, cur) -> Text("Estimated EMI: ${Money.format(emi, cur)} per month", style = MaterialTheme.typography.titleSmall) }
            field("EMI from your lender (optional, overrides the estimate)", f.emiOverride, "emi", KeyboardType.Decimal) { v -> vm.update { it.copy(emiOverride = v) } }()
            Text("Remind me ${f.remindDays} day(s) before each EMI", style = MaterialTheme.typography.labelLarge)
            Slider(f.remindDays.toFloat(), { v -> vm.update { it.copy(remindDays = v.toInt()) } }, valueRange = 0f..30f, steps = 29)
            OutlinedTextField(f.notes, { v -> vm.update { it.copy(notes = v) } }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Active (counts in totals and reminders)", Modifier.weight(1f)); Switch(f.active, { v -> vm.update { it.copy(active = v) } })
            }
            photos()
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { Text("Save") }
        }
    }
}
