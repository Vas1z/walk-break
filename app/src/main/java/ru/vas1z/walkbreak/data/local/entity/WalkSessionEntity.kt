package ru.vas1z.walkbreak.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "walk_sessions",
    indices = [Index(value = ["startedAtEpochMillis"])]
)
data class WalkSessionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startedAtEpochMillis: Long,
    val finishedAtEpochMillis: Long?,
    val plannedMinutes: Int,
    val source: String,
    val status: String
) {
    companion object {
        const val SOURCE_MANUAL = "manual"
        const val SOURCE_REMINDER = "reminder"

        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_INTERRUPTED = "INTERRUPTED"

        const val DEFAULT_PLANNED_MINUTES = 5
    }
}
