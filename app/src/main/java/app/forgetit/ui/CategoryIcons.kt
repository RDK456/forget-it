package app.forgetit.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import app.forgetit.domain.LoanType
import com.composables.icons.lucide.AppWindow
import com.composables.icons.lucide.Car
import com.composables.icons.lucide.Clapperboard
import com.composables.icons.lucide.Cloud
import com.composables.icons.lucide.CreditCard
import com.composables.icons.lucide.Droplets
import com.composables.icons.lucide.Dumbbell
import com.composables.icons.lucide.Flame
import com.composables.icons.lucide.Gamepad2
import com.composables.icons.lucide.GraduationCap
import com.composables.icons.lucide.House
import com.composables.icons.lucide.Landmark
import com.composables.icons.lucide.Lucide
import com.composables.icons.lucide.Milk
import com.composables.icons.lucide.Music
import com.composables.icons.lucide.Newspaper
import com.composables.icons.lucide.PartyPopper
import com.composables.icons.lucide.PiggyBank
import com.composables.icons.lucide.Share2
import com.composables.icons.lucide.Sparkles
import com.composables.icons.lucide.SprayCan
import com.composables.icons.lucide.Tag
import com.composables.icons.lucide.TriangleAlert
import com.composables.icons.lucide.User
import com.composables.icons.lucide.Wheat
import com.composables.icons.lucide.Zap
import com.composables.icons.lucide.Apple
import com.composables.icons.lucide.PawPrint
import com.composables.icons.lucide.Pill
import com.composables.icons.lucide.Receipt
import com.composables.icons.lucide.ShieldCheck
import com.composables.icons.lucide.Smartphone
import com.composables.icons.lucide.Tv
import com.composables.icons.lucide.Waves
import com.composables.icons.lucide.Wifi
import app.forgetit.domain.BillType

/** Each category gets its own icon and colour, so a list can be scanned by shape and colour before reading. */
fun categoryIcon(category: String): ImageVector = when (category) {
    "Streaming" -> Lucide.Clapperboard
    "Music" -> Lucide.Music
    "Software" -> Lucide.AppWindow
    "Gaming" -> Lucide.Gamepad2
    "News" -> Lucide.Newspaper
    "Cloud" -> Lucide.Cloud
    "Fitness" -> Lucide.Dumbbell
    "Utilities" -> Lucide.Zap
    "Dairy" -> Lucide.Milk
    "Produce" -> Lucide.Apple
    "Grocery" -> Lucide.Wheat
    "Household" -> Lucide.SprayCan
    "Toiletries" -> Lucide.Droplets
    "Medicines" -> Lucide.Pill
    "Gas and water" -> Lucide.Waves
    "Pet" -> Lucide.PawPrint
    else -> Lucide.Tag
}

fun categoryColor(category: String): Color = when (category) {
    "Streaming" -> Color(0xFFB3254F)
    "Music" -> Color(0xFF8E5BD0)
    "Software" -> Color(0xFF5560E0)
    "Gaming" -> Color(0xFF4F8A2B)
    "News" -> Color(0xFF5E7371)
    "Cloud" -> Color(0xFF2E86C1)
    "Fitness" -> Color(0xFFB26B00)
    "Utilities" -> Color(0xFF0B8F88)
    "Dairy" -> Color(0xFF2E86C1)
    "Produce" -> Color(0xFF4F8A2B)
    "Grocery" -> Color(0xFFB26B00)
    "Household" -> Color(0xFF8E5BD0)
    "Toiletries" -> Color(0xFF0B8F88)
    "Medicines" -> Color(0xFFB3254F)
    "Gas and water" -> Color(0xFF2E86C1)
    "Pet" -> Color(0xFF8E5BD0)
    else -> Color(0xFF5E7371)
}

fun loanIcon(type: LoanType): ImageVector = when (type) {
    LoanType.HOME -> Lucide.House
    LoanType.CAR -> Lucide.Car
    LoanType.PERSONAL -> Lucide.User
    LoanType.EDUCATION -> Lucide.GraduationCap
    LoanType.CARD_EMI -> Lucide.CreditCard
    LoanType.OTHER -> Lucide.Landmark
}

object FunIcons {
    val Streak: ImageVector get() = Lucide.Flame
    val Sparkle: ImageVector get() = Lucide.Sparkles
    val Party: ImageVector get() = Lucide.PartyPopper
    val Piggy: ImageVector get() = Lucide.PiggyBank
    val Share: ImageVector get() = Lucide.Share2
    val Warning: ImageVector get() = Lucide.TriangleAlert
}

fun billIcon(type: BillType): ImageVector = when (type) {
    BillType.ELECTRICITY -> Lucide.Zap
    BillType.WATER -> Lucide.Droplets
    BillType.GAS -> Lucide.Flame
    BillType.BROADBAND -> Lucide.Wifi
    BillType.MOBILE -> Lucide.Smartphone
    BillType.DTH -> Lucide.Tv
    BillType.INSURANCE -> Lucide.ShieldCheck
    BillType.RENT -> Lucide.House
    BillType.SCHOOL -> Lucide.GraduationCap
    BillType.OTHER -> Lucide.Receipt
}

fun billTypeLabel(type: BillType): String = when (type) {
    BillType.ELECTRICITY -> "Electricity"
    BillType.WATER -> "Water"
    BillType.GAS -> "Gas"
    BillType.BROADBAND -> "Broadband"
    BillType.MOBILE -> "Mobile"
    BillType.DTH -> "DTH or cable"
    BillType.INSURANCE -> "Insurance"
    BillType.RENT -> "Rent"
    BillType.SCHOOL -> "School fees"
    BillType.OTHER -> "Other"
}
