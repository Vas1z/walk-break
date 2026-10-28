package ru.vas1z.walkbreak.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "schedule_settings")
data class ScheduleSettingsEntity(
    @PrimaryKey
    val id: Int = 1,
    val isEnabled: Boolean,
    val workStartMinute: Int,
    val workEndMinute: Int,
    val intervalMinutes: Int = 60,
    @ColumnInfo(defaultValue = "1")
    val pauseDuringWalk: Boolean = true,
    val walkDurationMinutes: Int = 5,
    val updatedAtEpochMillis: Long
)
