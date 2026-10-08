package app.forgetit.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.forgetit.ui.theme.TrackerBrush
import com.composables.icons.lucide.Banknote
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Clapperboard
import com.composables.icons.lucide.Coins
import com.composables.icons.lucide.Gift
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.HeartPulse
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Percent
import com.composables.icons.lucide.Plane
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Shapes
import com.composables.icons.lucide.ShoppingBag
import com.composables.icons.lucide.ShoppingCart
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.Undo2
import com.composables.icons.lucide.Utensils

fun txnCategoryIcon(category: String): ImageVector = when (category) {
    "Food and dining" -> Lucide.Utensils
    "Groceries" -> Lucide.ShoppingCart
    "Transport" -> Lucide.Car
    "Shopping" -> Lucide.ShoppingBag
    "Bills and utilities" -> Lucide.Receipt
    "Subscriptions" -> Lucide.RefreshCw
    "EMI and loans" -> Lucide.Landmark
    "Health" -> Lucide.HeartPulse
    "Entertainment" -> Lucide.Clapperboard
    "Education" -> Lucide.GraduationCap
    "Travel" -> Lucide.Plane
    "Rent and home" -> Lucide.House
    "Personal care" -> Lucide.Sparkles
    "Salary" -> Lucide.Banknote
    "Refund" -> Lucide.Undo2
    "Interest" -> Lucide.Percent
    "Gift" -> Lucide.Gift
    "Other income" -> Lucide.Coins
    else -> Lucide.Shapes
}

fun txnCategoryColor(category: String): Color = Color(
    when (category) {
        "Food and dining" -> 0xFFE4572E
        "Groceries" -> 0xFF3FA34D
        "Transport" -> 0xFF2E86C1
        "Shopping" -> 0xFFD1478B
        "Bills and utilities" -> 0xFFE09A00
        "Subscriptions" -> 0xFF0B8F88
        "EMI and loans" -> 0xFF5560E0
        "Health" -> 0xFFE5484D
        "Entertainment" -> 0xFF8E5BD6
        "Education" -> 0xFF1F9BB5
        "Travel" -> 0xFF3B6FD4
        "Rent and home" -> 0xFF8A6B52
        "Personal care" -> 0xFFC95FA8
        "Salary" -> 0xFF2E9E5B
        "Refund" -> 0xFF22A39A
        "Interest" -> 0xFF4C8DD6
        "Gift" -> 0xFFD9578E
        "Other income" -> 0xFF5C8D6B
        else -> 0xFF7C8A89
    },
)

fun txnCategoryBrush(category: String): TrackerBrush = txnCategoryColor(category).let { TrackerBrush(it, lerp(it, Color.White, 0.3f)) }

/** The category's colour and icon in a rounded tile, used wherever a payment or a category is listed. */
@Composable
fun CategoryTile(category: String, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val b = txnCategoryBrush(category)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.34f)).background(Brush.linearGradient(listOf(b.from, b.to))),
        contentAlignment = Alignment.Center,
    ) { Icon(txnCategoryIcon(category), null, Modifier.size(size * 0.55f), tint = b.on) }
}
