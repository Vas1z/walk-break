package ru.vas1z.walkbreak.di

import android.content.Context
import ru.vas1z.walkbreak.data.local.WalkBreakDatabase
import ru.vas1z.walkbreak.data.repository.ReminderSettingsRepository
import ru.vas1z.walkbreak.data.repository.ReminderSlotRepository
import ru.vas1z.walkbreak.data.repository.WalkSessionRepository
import ru.vas1z.walkbreak.reminders.ReminderScheduler

class AppContainer(context: Context) {
    private val appContext: Context = context.applicationContext

    private val database: WalkBreakDatabase by lazy {
        WalkBreakDatabase.build(appContext)
    }

    val walkSessionRepository: WalkSessionRepository by lazy {
        WalkSessionRepository(database)
    }

    val reminderSettingsRepository: ReminderSettingsRepository by lazy {
        ReminderSettingsRepository(
            scheduleSettingsDao = database.scheduleSettingsDao(),
            quietPeriodDao = database.quietPeriodDao()
        )
    }

    val reminderSlotRepository: ReminderSlotRepository by lazy {
        ReminderSlotRepository(database.reminderSlotDao())
    }

    val reminderScheduler: ReminderScheduler by lazy {
        ReminderScheduler(
            context = appContext,
            settingsRepository = reminderSettingsRepository,
            slotRepository = reminderSlotRepository,
            walkSessionRepository = walkSessionRepository
        )
    }
}
