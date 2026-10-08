package app.forgetit.data

import app.forgetit.domain.Bill
import app.forgetit.domain.BillEntry
import app.forgetit.domain.BillValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

class BillRepository(private val dao: BillDao, private val photos: PhotoRepository) {
    fun observeBills(): Flow<List<Bill>> = dao.observeBills().map { l -> l.map { it.toDomain() } }
    fun observeEntries(): Flow<List<BillEntry>> = dao.observeEntries().map { l -> l.map { it.toDomain() } }
    suspend fun getBills() = dao.getBills().map { it.toDomain() }
    suspend fun getBill(id: Long) = dao.getBill(id)?.toDomain()
    suspend fun getEntries() = dao.getEntries().map { it.toDomain() }

    suspend fun save(bill: Bill): SaveResult {
        val errors = BillValidator.validate(bill)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val rowId = dao.upsertBill(bill.toEntity())
        return SaveResult.Saved(if (bill.id == 0L) rowId else bill.id)
    }

    suspend fun delete(id: Long) {
        dao.deleteBill(id); dao.deleteEntries(id)
        photos.deleteAll(OwnerType.BILL, id)
    }

    /** Records what the bill came to for the cycle due on [due]; replaces an earlier record for the same cycle. */
    suspend fun record(billId: Long, due: LocalDate, amountMinor: Long, paidOn: LocalDate?) {
        dao.deleteEntry(billId, due.toEpochDay())
        dao.insertEntry(BillEntry(billId = billId, dueDate = due, amountMinor = amountMinor, paidOn = paidOn).toEntity())
    }

    suspend fun removeEntry(billId: Long, due: LocalDate) = dao.deleteEntry(billId, due.toEpochDay())

    suspend fun importBundle(b: app.forgetit.domain.BillBundle) {
        val id = dao.upsertBill(b.bill.copy(id = 0).toEntity())
        b.entries.forEach { dao.insertEntry(it.copy(billId = id).toEntity()) }
    }
}
