package app.forgetit.data

import app.forgetit.domain.LogKind
import app.forgetit.domain.StockBatch
import app.forgetit.domain.StockEngine
import app.forgetit.domain.StockItem
import app.forgetit.domain.StockLog
import app.forgetit.domain.StockValidator
import app.forgetit.domain.UseResult
import app.forgetit.domain.ValidationError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class StockRepository(private val dao: StockDao, private val photos: PhotoRepository) {
    fun observeItems(): Flow<List<StockItem>> = dao.observeItems().map { l -> l.map { it.toDomain() } }
    fun observeBatches(): Flow<List<StockBatch>> = dao.observeBatches().map { l -> l.map { it.toDomain() } }
    fun observeLogs(): Flow<List<StockLog>> = dao.observeLogs().map { l -> l.map { it.toDomain() } }

    suspend fun getItems() = dao.getItems().map { it.toDomain() }
    suspend fun getItem(id: Long) = dao.getItem(id)?.toDomain()
    suspend fun getBatches() = dao.getBatches().map { it.toDomain() }
    suspend fun getLogs() = dao.getLogs().map { it.toDomain() }

    suspend fun save(item: StockItem): SaveResult {
        val errors = StockValidator.validate(item)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val rowId = dao.upsertItem(item.toEntity())
        return SaveResult.Saved(if (item.id == 0L) rowId else item.id)
    }

    suspend fun delete(id: Long) {
        dao.deleteItem(id); dao.deleteBatches(id); dao.deleteLogs(id)
        photos.deleteAll(OwnerType.STOCK_ITEM, id)
    }

    /** Every stock action restarts the estimate from the real, just-entered numbers. */
    private suspend fun rebase(itemId: Long, today: LocalDate) {
        dao.getItem(itemId)?.let { dao.upsertItem(it.copy(baselineEpochDay = today.toEpochDay())) }
    }

    suspend fun restock(itemId: Long, quantityMilli: Long, expiry: LocalDate?, today: LocalDate, priceMinor: Long? = null): ValidationError? {
        StockValidator.validateBatch(quantityMilli)?.let { return it }
        dao.insertBatch(StockBatch(itemId = itemId, quantityMilli = quantityMilli, addedOn = today, expiry = expiry).toEntity())
        dao.insertLog(StockLog(itemId = itemId, date = today, deltaMilli = quantityMilli, kind = LogKind.RESTOCK, priceMinor = priceMinor).toEntity())
        rebase(itemId, today)
        return null
    }

    suspend fun use(itemId: Long, quantityMilli: Long, today: LocalDate): UseResult {
        val batches = dao.batchesFor(itemId).map { it.toDomain() }
        val r = StockEngine.use(batches, quantityMilli, today)
        r.removedIds.forEach { dao.deleteBatch(it) }
        r.remaining.filter { b -> batches.first { it.id == b.id }.quantityMilli != b.quantityMilli }.forEach { dao.upsertBatch(it.toEntity()) }
        if (r.consumedMilli > 0) {
            dao.insertLog(StockLog(itemId = itemId, date = today, deltaMilli = -r.consumedMilli, kind = LogKind.USED).toEntity())
            rebase(itemId, today)
        }
        return r
    }

    suspend fun discard(batchId: Long, today: LocalDate) {
        val b = dao.getBatch(batchId) ?: return
        dao.deleteBatch(batchId)
        dao.insertLog(StockLog(itemId = b.itemId, date = today, deltaMilli = -b.quantityMilli, kind = LogKind.DISCARD).toEntity())
        rebase(b.itemId, today)
    }

    /** Adds an imported item with its batches and history as a new item. */
    suspend fun importBundle(b: app.forgetit.domain.StockBundle) {
        val id = dao.upsertItem(b.item.copy(id = 0).toEntity())
        b.batches.forEach { dao.insertBatch(it.copy(itemId = id).toEntity()) }
        b.logs.forEach { dao.insertLog(it.copy(itemId = id).toEntity()) }
    }

    /** Uses everything that is still good, so the item shows as finished. */
    suspend fun finish(itemId: Long, today: LocalDate): UseResult {
        val usable = dao.batchesFor(itemId).map { it.toDomain() }
            .filter { it.expiry == null || !it.expiry.isBefore(today) }.sumOf { it.quantityMilli }
        return use(itemId, usable, today)
    }
}
