package ru.vas1z.walkbreak.reminders.schedule

/**
 * Настройки расписания, не зависящие от Android и Room.
 */
data class ReminderSettings(
    val isEnabled: Boolean,
    val workStartMinute: Int,
    val workEndMinute: Int,
    val intervalMinutes: Int,
    val pauseDuringWalk: Boolean,
    val quietPeriods: List<QuietPeriod>
)

/**
 * Тихий период. [daysMask]: бит 0 — понедельник, бит 6 — воскресенье.
 *
 * Для периода, пересекающего полночь, [daysMask] относится к дню начала:
 * вечерняя часть действует в этот день, утренняя — в следующий.
 */
data class QuietPeriod(
    val id: Long = 0L,
    val startMinute: Int,
    val endMinute: Int,
    val daysMask: Int,
    val isEnabled: Boolean
) {
    fun appliesToDay(dayIndex: Int): Boolean =
        (daysMask and (1 shl dayIndex)) != 0
}
