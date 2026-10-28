package ru.vas1z.walkbreak.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import ru.vas1z.walkbreak.reminders.NotificationAccess
import ru.vas1z.walkbreak.ui.home.HomeScreen
import ru.vas1z.walkbreak.ui.home.HomeViewModel
import ru.vas1z.walkbreak.ui.reminders.ReminderSettingsScreen
import ru.vas1z.walkbreak.ui.reminders.ReminderSettingsViewModel
import ru.vas1z.walkbreak.ui.reminders.buildReminderSummary

@Composable
fun WalkBreakRoot(
    homeViewModel: HomeViewModel,
    reminderViewModel: ReminderSettingsViewModel,
    notificationAccess: NotificationAccess,
    onRequestNotificationAccess: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    startWalkRequest: Boolean,
    onStartWalkRequestHandled: () -> Unit
) {
    var showReminders by rememberSaveable { mutableStateOf(false) }
    val reminderUiState by reminderViewModel.uiState.collectAsState()
    val reminderSummary = remember(reminderUiState, notificationAccess) {
        buildReminderSummary(reminderUiState, notificationAccess)
    }

    LaunchedEffect(notificationAccess.canPost) {
        reminderViewModel.syncSchedule()
    }

    LaunchedEffect(startWalkRequest) {
        if (startWalkRequest) {
            showReminders = false
            homeViewModel.startWalk()
            onStartWalkRequestHandled()
        }
    }

    BackHandler(enabled = showReminders) {
        showReminders = false
    }

    if (showReminders) {
        ReminderSettingsScreen(
            uiState = reminderUiState,
            notificationAccess = notificationAccess,
            onBack = { showReminders = false },
            onRetryLoad = reminderViewModel::retryLoad,
            onSetEnabled = reminderViewModel::setEnabled,
            onSetInterval = reminderViewModel::setInterval,
            onSetWorkStart = reminderViewModel::setWorkStart,
            onSetWorkEnd = reminderViewModel::setWorkEnd,
            onSetQuietEnabled = reminderViewModel::setQuietEnabled,
            onSetQuietStart = reminderViewModel::setQuietStart,
            onSetQuietEnd = reminderViewModel::setQuietEnd,
            onSetPauseDuringWalk = reminderViewModel::setPauseDuringWalk,
            onDismissMessage = reminderViewModel::dismissMessage,
            onRequestNotificationAccess = onRequestNotificationAccess,
            onOpenNotificationSettings = onOpenNotificationSettings
        )
    } else {
        HomeScreen(
            viewModel = homeViewModel,
            reminderSummary = reminderSummary,
            onOpenReminders = { showReminders = true }
        )
    }
}
