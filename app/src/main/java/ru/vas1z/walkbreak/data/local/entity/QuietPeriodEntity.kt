package ru.vas1z.walkbreak.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * daysMask: бит 0 — понедельник, бит 6 — воскресенье.
 */
@Entity(tableName = "quiet_periods")
data class QuietPeriodEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val daysMask: Int,
    val isEnabled: Boolean
)
