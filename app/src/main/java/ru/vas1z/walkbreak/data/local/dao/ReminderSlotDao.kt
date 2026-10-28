package ru.vas1z.walkbreak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity

@Dao
abstract class ReminderSlotDao {

    @Query(
        "SELECT * FROM reminder_slots " +
            "WHERE status IN ('PENDING', 'SNOOZED') " +
            "ORDER BY scheduledAtEpochMillis ASC LIMIT 1"
    )
    abstract fun observeOpen(): Flow<ReminderSlotEntity?>

    @Query(
        "SELECT * FROM reminder_slots " +
            "WHERE status IN ('PENDING', 'SNOOZED') " +
            "ORDER BY scheduledAtEpochMillis ASC LIMIT 1"
    )
    abstract suspend fun getOpen(): ReminderSlotEntity?

    @Query("SELECT * FROM reminder_slots WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: Long): ReminderSlotEntity?

    @Insert
    abstract suspend fun insert(slot: ReminderSlotEntity): Long

    @Query(
        "UPDATE reminder_slots SET status = :status " +
            "WHERE status IN ('PENDING', 'SNOOZED')"
    )
    abstract suspend fun closeOpen(status: String): Int

    @Query(
        "UPDATE reminder_slots SET status = :status " +
            "WHERE id = :id AND status IN ('PENDING', 'SNOOZED')"
    )
    abstract suspend fun updateOpenStatus(id: Long, status: String): Int

    @Transaction
    open suspend fun replaceOpenSlot(
        scheduledAtEpochMillis: Long,
        status: String
    ): ReminderSlotEntity? {
        closeOpen(ReminderSlotEntity.STATUS_CANCELLED)
        val id = insert(
            ReminderSlotEntity(
                scheduledAtEpochMillis = scheduledAtEpochMillis,
                status = status,
                sessionId = null
            )
        )
        return getById(id)
    }
}
