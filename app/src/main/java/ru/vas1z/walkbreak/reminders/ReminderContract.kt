package ru.vas1z.walkbreak.reminders

object ReminderContract {
    const val CHANNEL_ID = "walk_reminders"
    const val NOTIFICATION_ID = 2001

    const val EXTRA_SLOT_ID = "ru.vas1z.walkbreak.extra.SLOT_ID"
    const val EXTRA_SCHEDULED_AT = "ru.vas1z.walkbreak.extra.SCHEDULED_AT"
    const val EXTRA_START_WALK = "ru.vas1z.walkbreak.extra.START_WALK"

    const val ALARM_REQUEST_CODE = 3101
    const val CONTENT_REQUEST_CODE = 3102
    const val START_WALK_REQUEST_CODE = 3103
    const val SNOOZE_REQUEST_CODE = 3104
}
