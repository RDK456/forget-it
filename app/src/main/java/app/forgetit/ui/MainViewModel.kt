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
    fun muteSender(sender: String) = viewModelScope.launch {
        c.settings.setMuted(settings.value.mutedSenders + sender)
        c.txns.ignoreSender(sender)
    }
    fun unmuteSender(sender: String) = viewModelScope.launch { c.settings.setMuted(settings.value.mutedSenders - sender) }
    fun connectGmail(email: String) = viewModelScope.launch { c.settings.setGmailEmail(email) }
    fun disconnectGmail() = viewModelScope.launch { c.settings.setGmailEmail("") }
    fun syncGmail(onDone: (Int) -> Unit) = viewModelScope.launch { onDone(app.forgetit.gmail.GmailScanner.sync(c)) }
    /** Adds reviewed scan results: restocks an item with the same name, otherwise creates it with a low-stock level of a quarter of the first purchase. */
    fun addScannedItems(items: List<app.forgetit.domain.ScannedItem>, onDone: (Int) -> Unit) = viewModelScope.launch {
        val today = java.time.LocalDate.now(c.clock)
        val existing = c.stock.getItems()
        var added = 0
        for (s in items) {
            val id = existing.firstOrNull { it.name.equals(s.name, ignoreCase = true) }?.id
                ?: (c.stock.save(app.forgetit.domain.StockItem(name = s.name, unit = s.unit, category = s.category, baselineDate = today, lowThresholdMilli = s.quantityMilli / 4)) as? app.forgetit.data.SaveResult.Saved)?.id
                ?: continue
            if (c.stock.restock(id, s.quantityMilli, null, today, s.priceMinor) == null) added++
        }
        onDone(added)
    }
    fun openScan() { c.scan.value = app.forgetit.grocery.ScanRequest(null) }

    /** Saves what the scanner found, each into its own tracker. Returns a short summary for the user. */
    fun saveDetected(list: List<app.forgetit.domain.Detected>, onDone: (String) -> Unit) = viewModelScope.launch {
        val today = java.time.LocalDate.now(c.clock)
        val msgs = mutableListOf<String>()
        for (d in list) {
            when (d.kind) {
                app.forgetit.domain.DocKind.SUBSCRIPTION -> {
                    if (c.subscriptions.getAll().any { it.name.equals(d.name, ignoreCase = true) }) { msgs += "${d.name} is already in subscriptions"; continue }
                    val sub = app.forgetit.domain.Subscription(
                        name = d.name, amountMinor = d.amountMinor ?: continue, currency = d.currency, cycle = d.cycle, startDate = d.date ?: today, category = d.category,
                    )
                    msgs += when (val r = c.subscriptions.save(sub)) {
                        is app.forgetit.data.SaveResult.Saved -> "Added ${d.name} to subscriptions"
                        is app.forgetit.data.SaveResult.Invalid -> "${d.name}: ${r.errors.first().message}"
                    }
                }
                app.forgetit.domain.DocKind.BILL -> {
                    val bill = app.forgetit.domain.Bill(name = d.name, type = d.billType, currency = d.currency, cycle = d.cycle, anchorDate = d.date ?: today.plusDays(7))
                    when (val r = c.bills.save(bill)) {
                        is app.forgetit.data.SaveResult.Saved -> {
                            d.amountMinor?.let { c.bills.record(r.id, bill.anchorDate, it, null) }
                            msgs += "Added ${d.name} to bills"
                        }
                        is app.forgetit.data.SaveResult.Invalid -> msgs += "${d.name}: ${r.errors.first().message}"
                    }
                }
                app.forgetit.domain.DocKind.EMI -> {
                    val emi = d.amountMinor ?: continue
                    val n = d.tenureMonths ?: 12
                    val rate = d.ratePercent ?: java.math.BigDecimal.ZERO
                    val monthly = rate.toDouble() / 1200
                    val principal = d.principalMinor ?: if (monthly == 0.0) emi * n else (Math.floor(emi * (1 - Math.pow(1 + monthly, -n.toDouble())) / monthly) - n).toLong().coerceAtLeast(emi) // rounded down so the last installment is never an extra sliver
                    val loan = app.forgetit.domain.Loan(
                        name = d.name, lender = d.name, principalMinor = principal, currency = d.currency, annualRatePercent = rate, tenureMonths = n,
                        firstEmiDate = d.date ?: today.plusMonths(1), emiOverrideMinor = emi, notes = "Added from a photo. Check the terms.",
                    )
                    msgs += when (val r = c.loans.save(loan)) {
                        is app.forgetit.data.SaveResult.Saved -> "Added ${d.name} to loans"
                        is app.forgetit.data.SaveResult.Invalid -> "${d.name}: ${r.errors.first().message}"
                    }
                }
                app.forgetit.domain.DocKind.GROCERY -> {
                    val existing = c.stock.getItems()
                    var added = 0
                    for (s in d.items) {
                        val id = existing.firstOrNull { it.name.equals(s.name, ignoreCase = true) }?.id
                            ?: (c.stock.save(app.forgetit.domain.StockItem(name = s.name, unit = s.unit, category = s.category, baselineDate = today, lowThresholdMilli = s.quantityMilli / 4)) as? app.forgetit.data.SaveResult.Saved)?.id
                            ?: continue
                        if (c.stock.restock(id, s.quantityMilli, null, today, s.priceMinor) == null) added++
                    }
                    msgs += "Added $added item(s) to household stock"
                }
                app.forgetit.domain.DocKind.PAYMENT -> {
                    val amt = d.amountMinor ?: continue
                    val p = app.forgetit.domain.ParsedTxn(app.forgetit.domain.TxnDirection.DEBIT, amt, d.currency, d.name, null, d.date ?: today)
                    msgs += if (c.txns.addIfNew(p, "PHOTO", "${d.name} ${app.forgetit.domain.Money.format(amt, d.currency)} ${p.date}")) "Saved payment to ${d.name}" else "That payment is already saved"
                }
            }
        }
        onDone(msgs.joinToString("\n").ifEmpty { "Nothing to add" })
    }
    fun exportFull(uri: android.net.Uri, onDone: (String) -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val msg = try {
            val n = c.context.contentResolver.openOutputStream(uri)?.use { app.forgetit.data.FullBackup.write(c, it) } ?: error("no stream")
            "Backup saved ($n files, with photos)"
        } catch (e: Exception) { "Could not save the backup" }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(msg) }
    }

    /** Replaces everything on this phone with the backup, then restarts the app. */
    fun importFull(uri: android.net.Uri, onDone: (String) -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        // restore() returns null on success, so opening the file is checked separately from its result.
        val stream = c.context.contentResolver.openInputStream(uri)
        val error = if (stream == null) "Could not open that file" else try {
            stream.use { app.forgetit.data.FullBackup.restore(c, it) }
        } catch (e: Exception) { "Could not restore from that file" }
        if (error == null) app.forgetit.data.FullBackup.restart(c.context)
        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(error) }
    }
    /** What pull to refresh does everywhere: settle trials, catch up on messages and Gmail, mark matched EMIs, rebuild reminders. */
    suspend fun refreshAll(forceScan: Boolean = false) {
        c.subscriptions.settleTrials(java.time.LocalDate.now(c.clock))
        val s = settings.value
        if (s.autoScan || forceScan) {
            app.forgetit.txn.AutoScan.scanDue(c, force = forceScan)
            if (s.gmailEmail.isNotEmpty()) app.forgetit.gmail.GmailScanner.sync(c)
        }
        app.forgetit.txn.AutoScan.markMatchedEmis(c)
        c.reminders.sync()
    }
    /** Reads an .xlsx or .csv file into records for review. Throws with a message the user can read. */
    suspend fun loadSheet(uri: android.net.Uri): app.forgetit.domain.SheetImportResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val bytes = c.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalArgumentException("Could not open that file")
        val name = c.context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "Sheet"
        app.forgetit.domain.SheetImport.fromBytes(bytes, name, settings.value.defaultCurrency, java.time.LocalDate.now(c.clock))
    }

    fun sheetSummary(r: app.forgetit.domain.SheetImportResult) =
        "Read ${r.rows} row(s), found ${r.items.sumOf { if (it.kind == app.forgetit.domain.DocKind.GROCERY) it.items.size else 1 }} record(s)." +
            if (r.skipped.isEmpty()) "" else "\nSkipped:\n" + r.skipped.joinToString("\n")

    /** Settings entry: pick a spreadsheet, then the review screen opens with everything it found. */
    fun importSheet(uri: android.net.Uri, onError: (String) -> Unit) = viewModelScope.launch {
        try {
            val r = loadSheet(uri)
            if (r.items.isEmpty()) onError("Nothing to import. " + sheetSummary(r))
            else c.scan.value = app.forgetit.grocery.ScanRequest(null, r.items, sheetSummary(r))
        } catch (e: Exception) { onError(e.message ?: "Could not read that file") }
    }

    fun exportExcel(uri: android.net.Uri, template: Boolean, onDone: (String) -> Unit) = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        val msg = try {
            val sheets = app.forgetit.domain.SheetExport.workbook(
                c.subscriptions.getAll(), c.loans.getLoans(), c.bills.getBills(), c.bills.getEntries(), c.stock.getItems(), c.stock.getBatches(),
                java.time.LocalDate.now(c.clock), template,
            )
            c.context.contentResolver.openOutputStream(uri)?.use { it.write(app.forgetit.domain.Xlsx.write(sheets)) } ?: error("no stream")
            if (template) "Template saved. Fill it in and import it." else "Excel file saved with one sheet per tracker."
        } catch (e: Exception) { "Could not save the Excel file" }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDone(msg) }
    }
    fun setBudget(minor: Long) = viewModelScope.launch { c.settings.setBudget(minor) }
    fun setAutoScan(on: Boolean) = viewModelScope.launch { c.settings.setAutoScan(on) }
    fun setAutoMarkEmi(on: Boolean) = viewModelScope.launch { c.settings.setAutoMarkEmi(on) }
    fun applyPriceChange(pc: app.forgetit.domain.PriceChange) = viewModelScope.launch {
        c.subscriptions.get(pc.subscriptionId)?.let { c.subscriptions.save(it.copy(amountMinor = pc.newMinor)) }
    }
    fun markScanPrompted() = viewModelScope.launch { c.settings.setScanPrompted(true) }
    /** Reads the SMS inbox now, whether or not auto-scan is on. */
    fun scanNow(onDone: (Int) -> Unit) = viewModelScope.launch { onDone(app.forgetit.txn.AutoScan.scanDue(c, force = true)) }

    fun finishStock(itemId: Long, onResult: (app.forgetit.domain.UseResult) -> Unit) = viewModelScope.launch {
        onResult(c.stock.finish(itemId, java.time.LocalDate.now(c.clock)))
    }

    val bills = c.bills.observeBills().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val billEntries = c.bills.observeEntries().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun deleteBill(id: Long) = viewModelScope.launch { c.bills.delete(id) }

    fun recordBill(billId: Long, due: java.time.LocalDate, amountMinor: Long, paid: Boolean) = viewModelScope.launch {
        c.bills.record(billId, due, amountMinor, if (paid) java.time.LocalDate.now(c.clock) else null)
    }

    fun removeBillEntry(billId: Long, due: java.time.LocalDate) = viewModelScope.launch { c.bills.removeEntry(billId, due) }

    fun exportBills(uri: android.net.Uri, onDone: (String) -> Unit) = exportWith(uri, "bills", onDone) {
        app.forgetit.domain.BillCsv.export(c.bills.getBills(), c.bills.getEntries())
    }

    fun importBills(uri: android.net.Uri, onDone: (String) -> Unit) = importWith(uri, onDone) { text ->
        val r = app.forgetit.domain.BillCsv.import(text)
        r.items.forEach { c.bills.importBundle(it) }
        summary(r.items.size, r.errors)
    }
}
