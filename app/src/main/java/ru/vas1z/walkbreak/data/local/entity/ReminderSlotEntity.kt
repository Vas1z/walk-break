package ru.vas1z.walkbreak.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_slots",
    indices = [Index(value = ["scheduledAtEpochMillis"])]
)
data class ReminderSlotEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val scheduledAtEpochMillis: Long,
    val status: String,
    val sessionId: Long?
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SNOOZED = "SNOOZED"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_EXPIRED = "EXPIRED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}
