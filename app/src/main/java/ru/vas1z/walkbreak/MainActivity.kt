package ru.vas1z.walkbreak

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import ru.vas1z.walkbreak.reminders.NotificationAccess
import ru.vas1z.walkbreak.reminders.ReminderContract
import ru.vas1z.walkbreak.ui.WalkBreakRoot
import ru.vas1z.walkbreak.ui.home.HomeViewModel
import ru.vas1z.walkbreak.ui.reminders.ReminderSettingsViewModel
import ru.vas1z.walkbreak.ui.theme.WalkBreakTheme

class MainActivity : ComponentActivity() {

    private var notificationAccess by mutableStateOf(
        NotificationAccess(
            permissionGranted = false,
            notificationsEnabled = false,
            channelBlocked = false
        )
    )
    private var startWalkFromNotification by mutableStateOf(false)

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notificationAccess = NotificationAccess.read(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        notificationAccess = NotificationAccess.read(this)

        if (savedInstanceState == null &&
            intent.getBooleanExtra(ReminderContract.EXTRA_START_WALK, false)
        ) {
            startWalkFromNotification = true
            intent.removeExtra(ReminderContract.EXTRA_START_WALK)
        }

        val container = (application as WalkBreakApp).container
        val homeViewModel = ViewModelProvider(
            owner = this,
            factory = HomeViewModel.factory(
                repository = container.walkSessionRepository,
                reminderScheduler = container.reminderScheduler
            )
        )[HomeViewModel::class.java]
        val reminderViewModel = ViewModelProvider(
            owner = this,
            factory = ReminderSettingsViewModel.factory(
                settingsRepository = container.reminderSettingsRepository,
                slotRepository = container.reminderSlotRepository,
                scheduler = container.reminderScheduler
            )
        )[ReminderSettingsViewModel::class.java]

        setContent {
            WalkBreakTheme {
                WalkBreakRoot(
                    homeViewModel = homeViewModel,
                    reminderViewModel = reminderViewModel,
                    notificationAccess = notificationAccess,
                    onRequestNotificationAccess = ::requestNotificationAccess,
                    onOpenNotificationSettings = ::openNotificationSettings,
                    startWalkRequest = startWalkFromNotification,
                    onStartWalkRequestHandled = {
                        startWalkFromNotification = false
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationAccess = NotificationAccess.read(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(ReminderContract.EXTRA_START_WALK, false)) {
            startWalkFromNotification = true
            intent.removeExtra(ReminderContract.EXTRA_START_WALK)
        }
    }

    private fun requestNotificationAccess() {
        val needsPermission = Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (needsPermission) {
            notificationPermissionLauncher.launch(
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            NotificationAccess.openSettings(this)
        }
    }

    private fun openNotificationSettings() {
        NotificationAccess.openSettings(this)
    }
}
