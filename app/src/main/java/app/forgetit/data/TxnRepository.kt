package app.forgetit.data

import app.forgetit.domain.Categorizer
import app.forgetit.domain.ParsedTxn
import app.forgetit.domain.TxnDirection
import java.time.LocalDate
import java.util.UUID
import app.forgetit.domain.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class TxnRepository(private val dao: TxnDao) {
    fun observeAll(): Flow<List<Txn>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    private fun hash(source: String, day: Long, body: String): String =
        MessageDigest.getInstance("SHA-256").digest("$source|$day|$body".toByteArray()).joinToString("") { "%02x".format(it) }

    /** Stores a parsed payment unless the same message was already stored. Only a short snippet of the text is kept. */
    suspend fun addIfNew(p: ParsedTxn, source: String, body: String, sender: String = ""): Boolean {
        val snippet = body.replace(Regex("""\s+"""), " ").trim().take(160)
        val id = dao.insert(
            TxnEntity(
                direction = p.direction.name, amountMinor = p.amountMinor, currency = p.currency, merchant = p.merchant,
                accountHint = p.accountHint, epochDay = p.date.toEpochDay(), source = source, status = "NEW", snippet = snippet,
                dedupe = hash(source, p.date.toEpochDay(), body), sender = sender.take(40),
            ),
        )
        return id != -1L
    }

    fun observeRules(): Flow<Map<String, String>> = dao.observeRules().map { l -> l.associate { it.merchantKey to it.category } }
    fun observeBudgets(): Flow<Map<String, Long>> = dao.observeBudgets().map { l -> l.associate { it.category to it.limitMinor } }

    /** A payment or income the user typed in. It is kept like any other and counts in every total. */
    suspend fun addManual(direction: TxnDirection, amountMinor: Long, currency: String, merchant: String?, category: String, date: LocalDate, note: String) {
        dao.insert(
            TxnEntity(
                direction = direction.name, amountMinor = amountMinor, currency = currency, merchant = merchant?.trim()?.ifBlank { null }?.take(60),
                accountHint = null, epochDay = date.toEpochDay(), source = "MANUAL", status = "NEW", snippet = "", dedupe = "manual:" + UUID.randomUUID(),
                category = category, note = note.trim().take(200),
            ),
        )
    }

    /** Saves edits to any payment, typed in or detected. */
    suspend fun update(t: Txn) {
        val old = dao.get(t.id) ?: return
        dao.upsert(
            old.copy(
                direction = t.direction.name, amountMinor = t.amountMinor, currency = t.currency, merchant = t.merchant?.trim()?.ifBlank { null },
                epochDay = t.date.toEpochDay(), category = t.category, note = t.note.trim().take(200),
            ),
        )
    }

    /** Moves one payment to a category and remembers it for the same merchant from now on. */
    suspend fun setCategory(t: Txn, category: String) {
        dao.get(t.id)?.let { dao.upsert(it.copy(category = category)) }
        val key = Categorizer.ruleKey(t.merchant)
        if (key.isNotEmpty()) dao.putRule(CategoryRuleEntity(key, category))
    }

    suspend fun setBudget(category: String, limitMinor: Long) =
        if (limitMinor <= 0) dao.deleteBudget(category) else dao.putBudget(CategoryBudgetEntity(category, limitMinor))

    suspend fun ignoreSender(sender: String) = dao.ignoreSender(sender)
    suspend fun setStatus(id: Long, status: String) = dao.setStatus(id, status)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
}
