package ru.vas1z.walkbreak.reminders

/**
 * Значения расписания по умолчанию.
 *
 * Напоминания по умолчанию выключены: приложение не отправляет уведомления
 * без явного включения пользователем и без разрешения Android 13+.
 */
object ReminderDefaults {
    const val WORK_START_MINUTE = 9 * 60
    const val WORK_END_MINUTE = 21 * 60
    const val INTERVAL_MINUTES = 120
    const val QUIET_START_MINUTE = 22 * 60
    const val QUIET_END_MINUTE = 8 * 60

    /** Бит 0 — понедельник, бит 6 — воскресенье. */
    const val DAYS_EVERY_DAY = 0b1111111

    const val PAUSE_DURING_WALK = true
    const val WALK_DURATION_MINUTES = 5
    const val SNOOZE_MINUTES = 15L
    const val IS_ENABLED_BY_DEFAULT = false

    val ALLOWED_INTERVALS = listOf(30, 60, 90, 120)
}
