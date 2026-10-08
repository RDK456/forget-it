package app.forgetit.data

import app.forgetit.domain.Subscription
import app.forgetit.domain.ValidationError
import app.forgetit.domain.Validator
import app.forgetit.domain.settleTrial
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

sealed interface SaveResult {
    data class Saved(val id: Long) : SaveResult
    data class Invalid(val errors: List<ValidationError>) : SaveResult
}

class SubscriptionRepository(private val dao: SubscriptionDao, private val photos: PhotoRepository) {
    fun observeAll(): Flow<List<Subscription>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun getAll(): List<Subscription> = dao.getAll().map { it.toDomain() }

    suspend fun get(id: Long): Subscription? = dao.get(id)?.toDomain()

    /** Validates first; nothing is written when there are errors. */
    suspend fun save(sub: Subscription): SaveResult {
        val errors = Validator.validate(sub)
        if (errors.isNotEmpty()) return SaveResult.Invalid(errors)
        val rowId = dao.upsert(sub.toEntity())
        return SaveResult.Saved(if (sub.id == 0L) rowId else sub.id)
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        photos.deleteAll(OwnerType.SUBSCRIPTION, id)
    }

    /** Turn trials whose end date has passed into regular subscriptions. */
    suspend fun settleTrials(today: LocalDate) {
        for (s in getAll()) {
            val settled = s.settleTrial(today)
            if (settled != s) dao.upsert(settled.toEntity())
        }
    }
}
