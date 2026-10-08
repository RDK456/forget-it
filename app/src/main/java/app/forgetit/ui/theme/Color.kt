package app.forgetit.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Calm ledger palette: cool paper, deep teal, and one colour per tracker so every list and chart reads the same way. */
val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0B6E6A), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFBDEEE8), onPrimaryContainer = Color(0xFF00201E),
    secondary = Color(0xFF4A6361), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E4), onSecondaryContainer = Color(0xFF05201E),
    tertiary = Color(0xFF8A5F00), onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDA0), onTertiaryContainer = Color(0xFF2B1A00),
    error = Color(0xFFB3254F), onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD9E1), onErrorContainer = Color(0xFF3F0017),
    background = Color(0xFFF5F8F7), onBackground = Color(0xFF0F1A19),
    surface = Color(0xFFF5F8F7), onSurface = Color(0xFF0F1A19),
    surfaceVariant = Color(0xFFDCE5E3), onSurfaceVariant = Color(0xFF3F4948),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEDF2F1), surfaceContainerHigh = Color(0xFFE6ECEB), surfaceContainerHighest = Color(0xFFDFE6E5),
    outline = Color(0xFF6F7978), outlineVariant = Color(0xFFC9D3D1),
)

val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF5ED3C8), onPrimary = Color(0xFF003733),
    primaryContainer = Color(0xFF00504B), onPrimaryContainer = Color(0xFFBDEEE8),
    secondary = Color(0xFFB1CCC8), onSecondary = Color(0xFF1C3533),
    secondaryContainer = Color(0xFF334B49), onSecondaryContainer = Color(0xFFCCE8E4),
    tertiary = Color(0xFFF2BE5B), onTertiary = Color(0xFF452B00),
    tertiaryContainer = Color(0xFF634100), onTertiaryContainer = Color(0xFFFFDDA0),
    error = Color(0xFFFFB1C4), onError = Color(0xFF660022),
    errorContainer = Color(0xFF7A0F33), onErrorContainer = Color(0xFFFFD9E1),
    background = Color(0xFF0D1514), onBackground = Color(0xFFDDE6E4),
    surface = Color(0xFF0D1514), onSurface = Color(0xFFDDE6E4),
    surfaceVariant = Color(0xFF3B4947), onSurfaceVariant = Color(0xFFB7C4C2),
    surfaceContainerLowest = Color(0xFF080F0E), surfaceContainerLow = Color(0xFF131D1C),
    surfaceContainer = Color(0xFF172120), surfaceContainerHigh = Color(0xFF1F2B2A), surfaceContainerHighest = Color(0xFF283534),
    outline = Color(0xFF869391), outlineVariant = Color(0xFF3B4947),
)

/** One colour per tracker, used for calendar dots, the outgo bar and chart slices. */
data class DomainColors(val subscription: Color, val loan: Color, val stock: Color, val bill: Color)

val LightDomain = DomainColors(Color(0xFF0B8F88), Color(0xFF5560E0), Color(0xFFD98E04), Color(0xFF2E86C1))
val DarkDomain = DomainColors(Color(0xFF4FD1C5), Color(0xFF9AA3FF), Color(0xFFF2BE5B), Color(0xFF6FB6E8))
