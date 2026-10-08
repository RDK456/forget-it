package app.forgetit.data

import app.forgetit.domain.ParsedTxn
import app.forgetit.domain.Txn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.security.MessageDigest

class TxnRepository(private val dao: TxnDao) {
    fun observeAll(): Flow<List<Txn>> = dao.observeAll().map { l -> l.map { it.toDomain() } }

    private fun hash(source: String, day: Long, body: String): String =
        MessageDigest.getInstance("SHA-256").digest("$source|$day|$body".toByteArray()).joinToString("") { "%02x".format(it) }

    /** Stores a parsed payment unless the same message was already stored. Only a short snippet of the text is kept. */
    suspend fun addIfNew(p: ParsedTxn, source: String, body: String): Boolean {
        val snippet = body.replace(Regex("""\s+"""), " ").trim().take(160)
        val id = dao.insert(
            TxnEntity(
                direction = p.direction.name, amountMinor = p.amountMinor, currency = p.currency, merchant = p.merchant,
                accountHint = p.accountHint, epochDay = p.date.toEpochDay(), source = source, status = "NEW", snippet = snippet,
                dedupe = hash(source, p.date.toEpochDay(), body),
            ),
        )
        return id != -1L
    }

    suspend fun setStatus(id: Long, status: String) = dao.setStatus(id, status)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteAll() = dao.deleteAll()
}
