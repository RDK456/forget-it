package app.forgetit.ui

import androidx.compose.ui.graphics.vector.ImageVector
import com.composables.icons.lucide.ArrowLeft
import com.composables.icons.lucide.Bell
import com.composables.icons.lucide.Calendar
import com.composables.icons.lucide.Camera
import com.composables.icons.lucide.ChartPie
import com.composables.icons.lucide.Check
import com.composables.icons.lucide.ChevronLeft
import com.composables.icons.lucide.ChevronRight
import com.composables.icons.lucide.Ellipsis
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Image
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Plus
import com.composables.icons.lucide.ReceiptText
import com.composables.icons.lucide.RefreshCw
import com.composables.icons.lucide.Search
import com.composables.icons.lucide.Settings
import com.composables.icons.lucide.ShoppingBasket
import com.composables.icons.lucide.Trash2
import com.composables.icons.lucide.X

/** Every icon the app draws, in one place so the set stays consistent (Lucide, 24 dp, 2 px stroke). */
object AppIcons {
    val Home: ImageVector get() = Lucide.House
    val Subscriptions: ImageVector get() = Lucide.RefreshCw
    val Loans: ImageVector get() = Lucide.Landmark
    val Stock: ImageVector get() = Lucide.ShoppingBasket
    val More: ImageVector get() = Lucide.Ellipsis
    val Add: ImageVector get() = Lucide.Plus
    val Delete: ImageVector get() = Lucide.Trash2
    val Check: ImageVector get() = Lucide.Check
    val Calendar: ImageVector get() = Lucide.Calendar
    val Insights: ImageVector get() = Lucide.ChartPie
    val Settings: ImageVector get() = Lucide.Settings
    val Bell: ImageVector get() = Lucide.Bell
    val Transactions: ImageVector get() = Lucide.ReceiptText
    val Back: ImageVector get() = Lucide.ArrowLeft
    val Previous: ImageVector get() = Lucide.ChevronLeft
    val Next: ImageVector get() = Lucide.ChevronRight
    val Camera: ImageVector get() = Lucide.Camera
    val Gallery: ImageVector get() = Lucide.Image
    val Close: ImageVector get() = Lucide.X
    val Search: ImageVector get() = Lucide.Search
}
