package app.forgetit.data

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.forgetit.domain.Rate
import app.forgetit.domain.RateCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.LocalDate

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val currency = stringPreferencesKey("default_currency")
        val rates = stringPreferencesKey("rates")
        val reminderMinute = intPreferencesKey("reminder_minute")
        val theme = stringPreferencesKey("theme")
        val biometric = booleanPreferencesKey("biometric_lock")
        val payday = intPreferencesKey("payday")
        val digest = booleanPreferencesKey("weekly_digest")
        val autoScan = booleanPreferencesKey("auto_scan")
        val gmailEmail = stringPreferencesKey("gmail_email")
        val gmailLast = longPreferencesKey("gmail_last_sync")
        val muted = stringSetPreferencesKey("muted_senders")
        val budget = longPreferencesKey("budget_minor")
        val autoEmi = booleanPreferencesKey("auto_mark_emi")
        val scanPrompted = booleanPreferencesKey("scan_prompted")
        val lastScan = longPreferencesKey("last_scan_day")
    }

    val flow: Flow<Settings> = context.dataStore.data.map { p ->
        val base = Settings()
        Settings(
            defaultCurrency = p[Keys.currency] ?: base.defaultCurrency,
            rates = RateCodec.decode(p[Keys.rates].orEmpty()),
            reminderMinuteOfDay = (p[Keys.reminderMinute] ?: base.reminderMinuteOfDay).coerceIn(0, 1439),
            theme = p[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: base.theme,
            biometricLock = p[Keys.biometric] ?: base.biometricLock,
            paydayDay = (p[Keys.payday] ?: base.paydayDay).coerceIn(0, 31),
            weeklyDigest = p[Keys.digest] ?: base.weeklyDigest,
            autoScan = p[Keys.autoScan] ?: base.autoScan,
            gmailEmail = p[Keys.gmailEmail].orEmpty(),
            gmailLastSync = p[Keys.gmailLast] ?: 0L,
            mutedSenders = p[Keys.muted] ?: emptySet(),
            budgetMinor = (p[Keys.budget] ?: 0L).coerceAtLeast(0),
            autoMarkEmi = p[Keys.autoEmi] ?: base.autoMarkEmi,
            scanPrompted = p[Keys.scanPrompted] ?: base.scanPrompted,
            lastScanDay = p[Keys.lastScan] ?: base.lastScanDay,
        )
    }

    private suspend fun update(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setDefaultCurrency(code: String) = update { it[Keys.currency] = code }
    suspend fun setReminderMinute(minute: Int) = update { it[Keys.reminderMinute] = minute.coerceIn(0, 1439) }
    suspend fun setTheme(mode: ThemeMode) = update { it[Keys.theme] = mode.name }
    suspend fun setBiometric(on: Boolean) = update { it[Keys.biometric] = on }
    suspend fun setPayday(day: Int) = update { it[Keys.payday] = day.coerceIn(0, 31) }
    suspend fun setWeeklyDigest(on: Boolean) = update { it[Keys.digest] = on }
    suspend fun setAutoScan(on: Boolean) = update { it[Keys.autoScan] = on }
    suspend fun setGmailEmail(email: String) = update { it[Keys.gmailEmail] = email; if (email.isEmpty()) it[Keys.gmailLast] = 0L }
    suspend fun setGmailLastSync(sec: Long) = update { it[Keys.gmailLast] = sec }
    suspend fun setMuted(senders: Set<String>) = update { it[Keys.muted] = senders }
    suspend fun setBudget(minor: Long) = update { it[Keys.budget] = minor.coerceAtLeast(0) }
    suspend fun setAutoMarkEmi(on: Boolean) = update { it[Keys.autoEmi] = on }
    suspend fun setScanPrompted(done: Boolean) = update { it[Keys.scanPrompted] = done }
    suspend fun setLastScanDay(epochDay: Long) = update { it[Keys.lastScan] = epochDay }

    suspend fun setRate(currency: String, value: BigDecimal, today: LocalDate) = update {
        val rates = RateCodec.decode(it[Keys.rates].orEmpty()) + (currency to Rate(value, today))
        it[Keys.rates] = RateCodec.encode(rates)
    }

    suspend fun removeRate(currency: String) = update {
        it[Keys.rates] = RateCodec.encode(RateCodec.decode(it[Keys.rates].orEmpty()) - currency)
    }
}
