package app.forgetit.ui

import androidx.lifecycle.ViewModel
import app.forgetit.domain.import
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
        viewModelScope.launch { c.loans.markPaid(loanId, no, java.time.LocalDate.now(c.clock), amountMinor); c.confetti.value++ }

    fun unmarkPaid(loanId: Long, no: Int) = viewModelScope.launch { c.loans.unmarkPaid(loanId, no) }

    fun addAdjustment(a: app.forgetit.domain.LoanAdjustment) = viewModelScope.launch { c.loans.addAdjustment(a) }

    fun deleteAdjustment(id: Long) = viewModelScope.launch { c.loans.deleteAdjustment(id) }

    val stockItems = c.stock.observeItems().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stockBatches = c.stock.observeBatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stockLogs = c.stock.observeLogs().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteStockItem(id: Long) = viewModelScope.launch { c.stock.delete(id) }

    fun restock(itemId: Long, qtyMilli: Long, expiry: java.time.LocalDate?, priceMinor: Long? = null) =
        viewModelScope.launch { c.stock.restock(itemId, qtyMilli, expiry, java.time.LocalDate.now(c.clock), priceMinor) }

    fun useStock(itemId: Long, qtyMilli: Long, onResult: (app.forgetit.domain.UseResult) -> Unit) = viewModelScope.launch {
        onResult(c.stock.use(itemId, qtyMilli, java.time.LocalDate.now(c.clock)))
    }

    fun discardBatch(batchId: Long) = viewModelScope.launch { c.stock.discard(batchId, java.time.LocalDate.now(c.clock)) }

    val txns = c.txns.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun scanSms(days: Long, onDone: (Int) -> Unit) = viewModelScope.launch {
        val cur = settings.value.defaultCurrency
        val since = java.time.LocalDate.now(c.clock).minusDays(days)
        onDone(app.forgetit.txn.SmsScanner.scanInbox(c.context, c.txns, since, cur, java.time.ZoneId.systemDefault()))
    }

    fun setTxnStatus(id: Long, status: String) = viewModelScope.launch { c.txns.setStatus(id, status) }
    fun deleteTxn(id: Long) = viewModelScope.launch { c.txns.delete(id) }
    fun clearTxns() = viewModelScope.launch { c.txns.deleteAll() }

    /** Saves a detected recurring charge as a subscription, started on its latest charge date. */
    fun addSubscriptionFrom(s: app.forgetit.domain.RecurringSuggestion, onDone: (String) -> Unit) = viewModelScope.launch {
        val sub = app.forgetit.domain.Subscription(
            name = s.merchant, amountMinor = s.amountMinor, currency = s.currency, cycle = s.cycle, startDate = s.lastDate,
            category = app.forgetit.domain.PRESETS.firstOrNull { it.name.equals(s.merchant, ignoreCase = true) }?.category ?: "Other",
        )
        onDone(when (val r = c.subscriptions.save(sub)) {
            is app.forgetit.data.SaveResult.Saved -> "Added ${s.merchant} to subscriptions"
            is app.forgetit.data.SaveResult.Invalid -> r.errors.first().message
        })
    }

    fun markEmiFrom(m: app.forgetit.domain.EmiMatch, t: app.forgetit.domain.Txn) = viewModelScope.launch {
        c.loans.markPaid(m.loanId, m.installmentNo, t.date, t.amountMinor)
        c.confetti.value++
    }

    fun addManualTxn(p: app.forgetit.domain.ParsedTxn, source: String, text: String, onDone: () -> Unit) = viewModelScope.launch {
        c.txns.addIfNew(p, source, text)
        onDone()
    }

    private fun summary(imported: Int, errors: List<String>): String {
        val more = if (errors.size > 5) "\n..." else ""
        return "$imported imported, ${errors.size} skipped" + (if (errors.isNotEmpty()) ":\n" + errors.take(5).joinToString("\n") + more else "")
    }

    private fun exportWith(uri: android.net.Uri, label: String, onDone: (String) -> Unit, produce: suspend () -> String) = viewModelScope.launch {
        val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val text = produce()
                c.context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                "Exported $label"
            }.getOrElse { "Export failed: ${it.message}" }
        }
        onDone(msg)
    }

    private fun importWith(uri: android.net.Uri, onDone: (String) -> Unit, handle: suspend (String) -> String) = viewModelScope.launch {
        val msg = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                val bytes = c.context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (bytes.size > maxCsvBytes) "That file is too large to import" else handle(bytes.toString(Charsets.UTF_8))
            }.getOrElse { "Import failed: ${it.message}" }
        }
        onDone(msg)
    }

    fun exportLoans(uri: android.net.Uri, onDone: (String) -> Unit) = exportWith(uri, "loans", onDone) {
        app.forgetit.domain.LoanCsv.export(c.loans.getLoans(), c.loans.getPayments(), c.loans.getAdjustments())
    }

    fun importLoans(uri: android.net.Uri, onDone: (String) -> Unit) = importWith(uri, onDone) { text ->
        val r = app.forgetit.domain.LoanCsv.import(text)
        r.items.forEach { c.loans.importBundle(it) }
        summary(r.items.size, r.errors)
    }

    fun exportStock(uri: android.net.Uri, onDone: (String) -> Unit) = exportWith(uri, "stock", onDone) {
        app.forgetit.domain.StockCsv.export(c.stock.getItems(), c.stock.getBatches(), c.stock.getLogs())
    }

    fun importStock(uri: android.net.Uri, onDone: (String) -> Unit) = importWith(uri, onDone) { text ->
        val r = app.forgetit.domain.StockCsv.import(text)
        r.items.forEach { c.stock.importBundle(it) }
        summary(r.items.size, r.errors)
    }

    fun setPayday(day: Int) = viewModelScope.launch { c.settings.setPayday(day) }
    fun setWeeklyDigest(on: Boolean) = viewModelScope.launch { c.settings.setWeeklyDigest(on) }

    fun finishStock(itemId: Long, onResult: (app.forgetit.domain.UseResult) -> Unit) = viewModelScope.launch {
        onResult(c.stock.finish(itemId, java.time.LocalDate.now(c.clock)))
    }
}
