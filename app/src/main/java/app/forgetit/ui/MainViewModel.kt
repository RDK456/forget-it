package app.forgetit.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.forgetit.AppContainer
import app.forgetit.data.Settings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

/** Shared state for the tab screens: every tracker's records plus settings and cover photos. */
class MainViewModel(val c: AppContainer) : ViewModel() {
    val subs = c.subscriptions.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<Settings> = c.settings.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    /** First photo of each record, keyed by "OWNERTYPE:id". */
    val covers: StateFlow<Map<String, File>> = c.photos.observeAll()
        .map { list ->
            list.groupBy { "${it.ownerType}:${it.ownerId}" }
                .mapValues { c.photos.file(it.value.first().fileName) }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun deleteSubscription(id: Long) = viewModelScope.launch { c.subscriptions.delete(id) }

    fun setCurrency(code: String) = viewModelScope.launch { c.settings.setDefaultCurrency(code) }
    fun setRate(code: String, value: java.math.BigDecimal) =
        viewModelScope.launch { c.settings.setRate(code, value, java.time.LocalDate.now(c.clock)) }
    fun removeRate(code: String) = viewModelScope.launch { c.settings.removeRate(code) }
    fun setReminderMinute(minute: Int) = viewModelScope.launch { c.settings.setReminderMinute(minute) }
    fun setTheme(mode: app.forgetit.data.ThemeMode) = viewModelScope.launch { c.settings.setTheme(mode) }
    fun setBiometric(on: Boolean) = viewModelScope.launch { c.settings.setBiometric(on) }

    private val maxCsvBytes = 5_000_000

    fun exportSubscriptions(uri: android.net.Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val text = app.forgetit.domain.SubscriptionCsv.export(c.subscriptions.getAll())
                c.context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                "Exported subscriptions"
            }.getOrElse { "Export failed: ${it.message}" }
        }
        onDone(msg)
    }

    fun importSubscriptions(uri: android.net.Uri, onDone: (String) -> Unit) = viewModelScope.launch {
        val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bytes = c.context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (bytes.size > maxCsvBytes) return@runCatching "That file is too large to import"
                val r = app.forgetit.domain.SubscriptionCsv.import(bytes.toString(Charsets.UTF_8))
                r.items.forEach { c.subscriptions.save(it) }
                val more = if (r.errors.size > 5) "\n..." else ""
                "${r.items.size} imported, ${r.errors.size} skipped" +
                    (if (r.errors.isNotEmpty()) ":\n" + r.errors.take(5).joinToString("\n") + more else "")
            }.getOrElse { "Import failed: ${it.message}" }
        }
        onDone(msg)
    }

    val loans = c.loans.observeLoans().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loanPayments = c.loans.observePayments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val loanAdjustments = c.loans.observeAdjustments().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteLoan(id: Long) = viewModelScope.launch { c.loans.delete(id) }

    fun markPaid(loanId: Long, no: Int, amountMinor: Long) =
        viewModelScope.launch { c.loans.markPaid(loanId, no, java.time.LocalDate.now(c.clock), amountMinor) }

    fun unmarkPaid(loanId: Long, no: Int) = viewModelScope.launch { c.loans.unmarkPaid(loanId, no) }

    fun addAdjustment(a: app.forgetit.domain.LoanAdjustment) = viewModelScope.launch { c.loans.addAdjustment(a) }

    fun deleteAdjustment(id: Long) = viewModelScope.launch { c.loans.deleteAdjustment(id) }
}
