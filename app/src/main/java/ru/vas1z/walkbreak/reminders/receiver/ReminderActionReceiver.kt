package ru.vas1z.walkbreak.reminders.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.vas1z.walkbreak.WalkBreakApp
import ru.vas1z.walkbreak.reminders.ReminderNotifications

class ReminderActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SNOOZE) return

        // Убираем текущее уведомление сразу, не дожидаясь Room.
        ReminderNotifications.cancel(context)
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = (context.applicationContext as WalkBreakApp)
                    .container
                    .reminderScheduler
                val result = scheduler.scheduleSnooze()
                Log.i(TAG, "Snooze requested: $result")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to snooze reminder", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_SNOOZE = "ru.vas1z.walkbreak.action.SNOOZE"
        private const val TAG = "ReminderActionReceiver"
    }
}
