package app.forgetit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Under an item the app added by itself: Keep makes it count, Delete removes it and stops it coming back. */
@Composable
fun ReviewStrip(onHold: Boolean, onKeep: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
        Text(
            if (onHold) "Added on hold. Check it." else "Added automatically. Check it.",
            Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary,
        )
        TextButton(onKeep) { Text("Keep") }
        TextButton(onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
    }
}
