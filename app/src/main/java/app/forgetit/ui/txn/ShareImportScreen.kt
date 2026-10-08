package app.forgetit.ui.txn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.forgetit.domain.Money
import app.forgetit.domain.ParsedTxn
import app.forgetit.domain.SmsParser
import app.forgetit.domain.TxnDirection
import app.forgetit.ui.MainViewModel
import app.forgetit.ui.ScreenScaffold
import java.time.LocalDate

/** Text shared from another app (an email, a receipt): read it like an SMS, or let the user fill in the amount. */
@Composable
fun ShareImportScreen(vm: MainViewModel, text: String, today: LocalDate, onDone: () -> Unit) {
    val currency = vm.settings.value.defaultCurrency
    val parsed = remember(text) { SmsParser.parse(text, today, currency) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    val manualMinor = Money.parseMinor(amount, currency)

    ScreenScaffold("Add from shared text", onBack = onDone) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (parsed != null) {
                Text("Found a payment", style = MaterialTheme.typography.titleMedium)
                Text("${if (parsed.direction == TxnDirection.DEBIT) "Paid" else "Received"} ${Money.format(parsed.amountMinor, parsed.currency)}" +
                    (parsed.merchant?.let { " - $it" } ?: ""))
                Button({ vm.addManualTxn(parsed, "SHARED", text, onDone) }, Modifier.fillMaxWidth()) { Text("Save transaction") }
            } else {
                Text("No payment found automatically. Enter the amount to save it.", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    amount, { amount = it }, label = { Text("Amount ($currency)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = amount.isNotEmpty() && manualMinor == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(
                    {
                        val p = ParsedTxn(TxnDirection.DEBIT, manualMinor!!, currency, merchant.trim().ifEmpty { null }, null, today)
                        vm.addManualTxn(p, "SHARED", text, onDone)
                    },
                    enabled = manualMinor != null && manualMinor > 0, modifier = Modifier.fillMaxWidth(),
                ) { Text("Save as expense") }
            }
            Text("Shared text", style = MaterialTheme.typography.labelLarge)
            Text(text.take(600), style = MaterialTheme.typography.bodySmall)
        }
    }
}
