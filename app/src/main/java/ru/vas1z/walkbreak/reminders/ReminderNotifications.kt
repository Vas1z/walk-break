package ru.vas1z.walkbreak.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ru.vas1z.walkbreak.MainActivity
import ru.vas1z.walkbreak.R
import ru.vas1z.walkbreak.reminders.receiver.ReminderActionReceiver

object ReminderNotifications {
    private const val TAG = "ReminderNotifications"
    private const val CHANNEL_NAME = "Напоминания о прогулке"
    private const val CHANNEL_DESCRIPTION =
        "Короткие напоминания пройтись в выбранные часы."
    private const val TITLE = "Пора пройтись"
    private const val TEXT =
        "Пять минут движения — и можно возвращаться к делам."
    private const val ACTION_START = "Начать прогулку"
    private const val ACTION_SNOOZE = "Напомнить позже"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(ReminderContract.CHANNEL_ID) != null) {
            return
        }

        val channel = NotificationChannel(
            ReminderContract.CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = CHANNEL_DESCRIPTION
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        NotificationAccess.read(context).canPost

    fun showReminder(context: Context): Boolean {
        if (!canPost(context)) {
            return false
        }
        ensureChannel(context)

        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderContract.CONTENT_REQUEST_CODE,
            openAppIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val startWalkIntent = PendingIntent.getActivity(
            context,
            ReminderContract.START_WALK_REQUEST_CODE,
            openAppIntent(context).apply {
                putExtra(ReminderContract.EXTRA_START_WALK, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            ReminderContract.SNOOZE_REQUEST_CODE,
            Intent(context, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_SNOOZE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(
            context,
            ReminderContract.CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_walk_notification)
            .setContentTitle(TITLE)
            .setContentText(TEXT)
            .setStyle(NotificationCompat.BigTextStyle().bigText(TEXT))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .addAction(0, ACTION_START, startWalkIntent)
            .addAction(0, ACTION_SNOOZE, snoozeIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context)
                .notify(ReminderContract.NOTIFICATION_ID, notification)
            true
        } catch (error: SecurityException) {
            Log.e(TAG, "Notification permission was revoked", error)
            false
        }
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context)
            .cancel(ReminderContract.NOTIFICATION_ID)
    }

    private fun openAppIntent(context: Context): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
}
