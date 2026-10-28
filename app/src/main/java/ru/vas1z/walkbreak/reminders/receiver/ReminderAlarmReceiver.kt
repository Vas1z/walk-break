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
import ru.vas1z.walkbreak.reminders.ReminderContract

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_REMIND) return

        val slotId = intent.getLongExtra(ReminderContract.EXTRA_SLOT_ID, -1L)
        val scheduledAt = intent.getLongExtra(
            ReminderContract.EXTRA_SCHEDULED_AT,
            -1L
        )
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val scheduler = (context.applicationContext as WalkBreakApp)
                    .container
                    .reminderScheduler
                val result = scheduler.handleAlarm(slotId, scheduledAt)
                Log.i(TAG, "Reminder alarm handled: $result")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to handle reminder alarm", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_REMIND = "ru.vas1z.walkbreak.action.REMIND"
        private const val TAG = "ReminderAlarmReceiver"
    }
}
