package ru.vas1z.walkbreak.data.repository

import kotlinx.coroutines.flow.Flow
import ru.vas1z.walkbreak.data.local.dao.ReminderSlotDao
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity

class ReminderSlotRepository(
    private val dao: ReminderSlotDao
) {
    val nextSlot: Flow<ReminderSlotEntity?> = dao.observeOpen()

    suspend fun getOpenSlot(): ReminderSlotEntity? = dao.getOpen()

    suspend fun getById(id: Long): ReminderSlotEntity? = dao.getById(id)

    /**
     * Заменяет открытый слот новым. Если тот же слот уже запланирован,
     * повторная запись не создаётся.
     */
    suspend fun replaceOpenSlot(
        scheduledAtEpochMillis: Long,
        status: String
    ): ReminderSlotEntity {
        val current = dao.getOpen()
        if (current != null &&
            current.scheduledAtEpochMillis == scheduledAtEpochMillis &&
            current.status == status
        ) {
            return current
        }
        return dao.replaceOpenSlot(scheduledAtEpochMillis, status)
            ?: error("Could not read the inserted reminder slot")
    }

    suspend fun markStatus(slotId: Long, status: String): Boolean =
        dao.updateOpenStatus(slotId, status) > 0

    suspend fun cancelOpenSlots() {
        dao.closeOpen(ReminderSlotEntity.STATUS_CANCELLED)
    }
}
