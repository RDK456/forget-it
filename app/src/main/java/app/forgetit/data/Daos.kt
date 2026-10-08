package app.forgetit.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscription") fun observeAll(): Flow<List<SubscriptionEntity>>
    @Query("SELECT * FROM subscription") suspend fun getAll(): List<SubscriptionEntity>
    @Query("SELECT * FROM subscription WHERE id = :id") suspend fun get(id: Long): SubscriptionEntity?
    @Upsert suspend fun upsert(e: SubscriptionEntity): Long
    @Query("DELETE FROM subscription WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photo ORDER BY sortOrder, id") fun observeAll(): Flow<List<PhotoEntity>>
    @Query("SELECT * FROM photo WHERE ownerType = :type AND ownerId = :id ORDER BY sortOrder, id")
    suspend fun listFor(type: String, id: Long): List<PhotoEntity>
    @Insert suspend fun insert(e: PhotoEntity): Long
    @Query("SELECT * FROM photo WHERE id = :id") suspend fun get(id: Long): PhotoEntity?
    @Query("DELETE FROM photo WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM photo WHERE ownerType = :type AND ownerId = :id") suspend fun deleteFor(type: String, id: Long)
    @Query("SELECT fileName FROM photo") suspend fun allFileNames(): List<String>
}

@Dao
interface ReminderLogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: ReminderLogEntity): Long
    @Query("SELECT reminderKey FROM reminder_log") suspend fun allKeys(): List<String>
}
