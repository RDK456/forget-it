package app.forgetit.data

import app.forgetit.domain.Rate
import java.util.Currency
import java.util.Locale

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val defaultCurrency: String = currencyFromLocale(),
    val rates: Map<String, Rate> = emptyMap(),
    val reminderMinuteOfDay: Int = 9 * 60,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val biometricLock: Boolean = false,
    val paydayDay: Int = 0,
    val weeklyDigest: Boolean = true,
)

fun currencyFromLocale(): String =
    runCatching { Currency.getInstance(Locale.getDefault()).currencyCode }.getOrDefault("USD")
