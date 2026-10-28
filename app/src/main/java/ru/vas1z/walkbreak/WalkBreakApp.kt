package ru.vas1z.walkbreak

import android.app.Application
import ru.vas1z.walkbreak.di.AppContainer
import ru.vas1z.walkbreak.reminders.ReminderNotifications

class WalkBreakApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.ensureChannel(this)
        container = AppContainer(this)
    }
}
