package app.forgetit.ui.stock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import app.forgetit.domain.Money
import app.forgetit.ui.edit.DateField
import java.time.LocalDate

/** Asks for a quantity (and an optional expiry date when restocking). */
@Composable
fun QuantityDialog(
    title: String, unit: String, withExpiry: Boolean, today: LocalDate,
    onConfirm: (qtyMilli: Long, expiry: LocalDate?) -> Unit, onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var hasExpiry by remember { mutableStateOf(false) }
    var expiry by remember { mutableStateOf(today.plusDays(7)) }
    val milli = Money.parseMilli(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    text, { text = it }, label = { Text("Amount ($unit)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = text.isNotEmpty() && milli == null,
                )
                if (withExpiry) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Has an expiry date", Modifier.weight(1f)); Switch(hasExpiry, { hasExpiry = it }) }
                    if (hasExpiry) DateField("Expires", expiry, { expiry = it })
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(milli!!, if (hasExpiry) expiry else null) }, enabled = milli != null && milli > 0) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
