package app.forgetit.ui.loans

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.forgetit.domain.AdjustmentKind
import app.forgetit.domain.Money

@Composable
fun AmountDialog(
    title: String, hint: String, currency: String, withKind: Boolean,
    onConfirm: (amountMinor: Long, kind: AdjustmentKind) -> Unit, onDismiss: () -> Unit,
    preview: (Long, AdjustmentKind) -> String? = { _, _ -> null },
) {
    var text by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(AdjustmentKind.PREPAYMENT_REDUCE_TENURE) }
    val minor = Money.parseMinor(text, currency)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(hint)
                OutlinedTextField(
                    text, { text = it }, label = { Text("Amount ($currency)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = text.isNotEmpty() && minor == null,
                )
                if (minor != null && minor > 0) preview(minor, kind)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                if (withKind) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(kind == AdjustmentKind.PREPAYMENT_REDUCE_TENURE, { kind = AdjustmentKind.PREPAYMENT_REDUCE_TENURE }, { Text("Shorter loan") })
                    FilterChip(kind == AdjustmentKind.PREPAYMENT_REDUCE_EMI, { kind = AdjustmentKind.PREPAYMENT_REDUCE_EMI }, { Text("Smaller EMI") })
                }
            }
        },
        confirmButton = { TextButton({ onConfirm(minor!!, kind) }, enabled = minor != null && minor >= 0) { Text("Save") } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onConfirm) { Text(confirm) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel") } },
    )
}
