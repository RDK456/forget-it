package app.forgetit.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import app.forgetit.R

/** Manrope variable font bundled in the app (no network): clear numerals and a friendly geometric shape. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Manrope = FontFamily(
    listOf(400, 500, 600, 700, 800).map { w ->
        Font(R.font.manrope, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
    },
)

private fun TextStyle.app(weight: FontWeight? = null) =
    copy(fontFamily = Manrope, fontFeatureSettings = "tnum", fontWeight = weight ?: fontWeight)

private val base = Typography()

val AppTypography = Typography(
    displayLarge = base.displayLarge.app(FontWeight.ExtraBold),
    displayMedium = base.displayMedium.app(FontWeight.ExtraBold),
    displaySmall = base.displaySmall.app(FontWeight.ExtraBold),
    headlineLarge = base.headlineLarge.app(FontWeight.ExtraBold),
    headlineMedium = base.headlineMedium.app(FontWeight.ExtraBold),
    headlineSmall = base.headlineSmall.app(FontWeight.Bold),
    titleLarge = base.titleLarge.app(FontWeight.Bold),
    titleMedium = base.titleMedium.app(FontWeight.SemiBold),
    titleSmall = base.titleSmall.app(FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.app(),
    bodyMedium = base.bodyMedium.app(),
    bodySmall = base.bodySmall.app(),
    labelLarge = base.labelLarge.app(FontWeight.SemiBold),
    labelMedium = base.labelMedium.app(FontWeight.SemiBold),
    labelSmall = base.labelSmall.app(FontWeight.Medium),
)
