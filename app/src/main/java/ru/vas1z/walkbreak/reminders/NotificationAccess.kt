package ru.vas1z.walkbreak.reminders

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Текущее состояние разрешений и системных настроек уведомлений.
 */
data class NotificationAccess(
    val permissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val channelBlocked: Boolean
) {
    val canPost: Boolean
        get() = permissionGranted && notificationsEnabled && !channelBlocked

    companion object {
        private const val TAG = "NotificationAccess"

        fun read(context: Context): NotificationAccess {
            val permissionGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED

            val manager = NotificationManagerCompat.from(context)
            val notificationsEnabled = manager.areNotificationsEnabled()
            val channel = context
                .getSystemService(NotificationManager::class.java)
                .getNotificationChannel(ReminderContract.CHANNEL_ID)
            val channelBlocked = channel != null &&
                channel.importance == NotificationManager.IMPORTANCE_NONE

            return NotificationAccess(
                permissionGranted = permissionGranted,
                notificationsEnabled = notificationsEnabled,
                channelBlocked = channelBlocked
            )
        }

        fun openSettings(context: Context) {
            val notificationManager =
                context.getSystemService(NotificationManager::class.java)
            val channelBlocked = notificationManager
                .getNotificationChannel(ReminderContract.CHANNEL_ID)
                ?.importance == NotificationManager.IMPORTANCE_NONE

            val intent = if (channelBlocked) {
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    putExtra(Settings.EXTRA_CHANNEL_ID, ReminderContract.CHANNEL_ID)
                }
            } else {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            runCatching { context.startActivity(intent) }
                .onFailure { Log.e(TAG, "Could not open notification settings", it) }
        }
    }
}
