package app.forgetit.ui.stock

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.forgetit.domain.Money
import app.forgetit.domain.ShoppingLine
import app.forgetit.domain.shoppingText
import app.forgetit.ui.AppIcons
import app.forgetit.ui.FunIcons
import app.forgetit.ui.relativeDay
import java.time.LocalDate

/** What to buy next, grouped by store, ready to share. "Bought" restocks the suggested amount in one tap. */
@Composable
fun ShoppingCard(lines: List<ShoppingLine>, today: LocalDate, onBought: (ShoppingLine) -> Unit, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    AnimatedVisibility(lines.isNotEmpty(), modifier, enter = fadeIn() + expandVertically(), exit = shrinkVertically()) {
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(AppIcons.Stock, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Shopping list (${lines.size})", style = MaterialTheme.typography.titleMedium)
                }
                val groups = lines.groupBy { it.store.ifBlank { "Anywhere" } }
                groups.forEach { (store, list) ->
                    if (groups.size > 1) Text(store, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
                    list.forEach { l ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${l.name}  -  ${Money.milliToPlain(l.buyMilli)} ${l.unit}")
                                Text(
                                    l.reason + (l.buyBy?.let { "  -  buy by ${relativeDay(it, today).lowercase()}" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error,
                                )
                            }
                            TextButton({ onBought(l) }) { Text("Bought") }
                        }
                    }
                }
                OutlinedButton({
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, shoppingText(lines))
                    ctx.startActivity(Intent.createChooser(send, "Share shopping list"))
                }, Modifier.padding(top = 4.dp)) {
                    Icon(FunIcons.Share, null, Modifier.size(18.dp)); Text("  Share list")
                }
            }
        }
    }
}
