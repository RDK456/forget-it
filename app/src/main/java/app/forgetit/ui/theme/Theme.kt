package app.forgetit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalDomainColors = staticCompositionLocalOf { LightDomain }

/** Brand colours on purpose: the app looks the same on every phone instead of following the wallpaper. */
@Composable
fun ForgetItTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalDomainColors provides if (dark) DarkDomain else LightDomain) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            typography = AppTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(6.dp), small = RoundedCornerShape(10.dp), medium = RoundedCornerShape(16.dp),
                large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp),
            ),
            content = content,
        )
    }
}

val domainColors: DomainColors @Composable get() = LocalDomainColors.current
