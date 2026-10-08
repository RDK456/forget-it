package app.forgetit.ui.bills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.forgetit.domain.BillEntry
import app.forgetit.domain.BillMath
import app.forgetit.domain.Money
import java.time.LocalDate

/** Records what this cycle bill came to, and warns right away when it is far above the usual amount. */
@Composable
fun RecordBillDialog(
    due: LocalDate, currency: String, earlier: List<BillEntry>,
    onConfirm: (amountMinor: Long, paid: Boolean) -> Unit, onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var paid by remember { mutableStateOf(true) }
    val minor = Money.parseMinor(text, currency)
    val change = minor?.let { BillMath.changePercent(it, earlier) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record the bill due $due") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it }, label = { Text("Amount ($currency)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = text.isNotEmpty() && minor == null,
                )
                if (change != null && change >= BillMath.HIGH_PERCENT) {
                    Text("That is $change% higher than usual. Worth checking the meter or the statement.", color = MaterialTheme.colorScheme.error)
                } else if (change != null && change < 0) {
                    Text("${-change}% lower than usual.", color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Already paid", Modifier.weight(1f)); Switch(paid, { paid = it }) }
            }
        },
        confirmButton = { TextButton({ onConfirm(minor!!, paid) }, enabled = minor != null && minor > 0) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
