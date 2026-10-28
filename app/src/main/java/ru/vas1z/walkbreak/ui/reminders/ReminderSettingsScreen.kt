package ru.vas1z.walkbreak.ui.reminders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity
import ru.vas1z.walkbreak.reminders.NotificationAccess
import ru.vas1z.walkbreak.reminders.ReminderDefaults
import ru.vas1z.walkbreak.reminders.schedule.QuietPeriod
import ru.vas1z.walkbreak.reminders.schedule.ReminderSettings

private enum class TimeField {
    WorkStart,
    WorkEnd,
    QuietStart,
    QuietEnd
}

@Composable
fun ReminderSettingsScreen(
    uiState: ReminderSettingsUiState,
    notificationAccess: NotificationAccess,
    onBack: () -> Unit,
    onRetryLoad: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onSetInterval: (Int) -> Unit,
    onSetWorkStart: (Int) -> Unit,
    onSetWorkEnd: (Int) -> Unit,
    onSetQuietEnabled: (Boolean) -> Unit,
    onSetQuietStart: (Int) -> Unit,
    onSetQuietEnd: (Int) -> Unit,
    onSetPauseDuringWalk: (Boolean) -> Unit,
    onDismissMessage: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    var editingField by remember { mutableStateOf<TimeField?>(null) }
    val settings = uiState.settings
    val scrollState = rememberScrollState()

    LaunchedEffect(uiState.errorMessage) {
        if (uiState.errorMessage != null) {
            scrollState.animateScrollTo(0)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onBack) {
                    Text("Назад")
                }
            }

            Text(
                text = "Напоминания",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Настройте, когда приложение будет предлагать короткую прогулку.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))

            when {
                uiState.isLoading -> LoadingReminderSettings()

                uiState.loadFailed || settings == null -> LoadErrorReminderSettings(
                    onRetry = onRetryLoad
                )

                else -> {
                    if (uiState.errorMessage != null) {
                        ErrorMessageCard(
                            message = uiState.errorMessage,
                            onDismiss = onDismissMessage
                        )
                        Spacer(Modifier.height(16.dp))
                    }

                    MasterReminderCard(
                        settings = settings,
                        notificationAccess = notificationAccess,
                        isSaving = uiState.isSaving,
                        onSetEnabled = onSetEnabled,
                        onRequestNotificationAccess = onRequestNotificationAccess
                    )

                    if (settings.isEnabled && !notificationAccess.canPost) {
                        Spacer(Modifier.height(12.dp))
                        PermissionWarningCard(
                            access = notificationAccess,
                            onRequest = onRequestNotificationAccess,
                            onOpenSettings = onOpenNotificationSettings
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    ActivePeriodCard(
                        settings = settings,
                        enabled = !uiState.isSaving,
                        onEditStart = { editingField = TimeField.WorkStart },
                        onEditEnd = { editingField = TimeField.WorkEnd }
                    )

                    Spacer(Modifier.height(16.dp))
                    IntervalCard(
                        intervalMinutes = settings.intervalMinutes,
                        enabled = !uiState.isSaving,
                        onSetInterval = onSetInterval
                    )

                    Spacer(Modifier.height(16.dp))
                    QuietHoursCard(
                        quiet = settings.quietPeriods.firstOrNull(),
                        enabled = !uiState.isSaving,
                        onSetQuietEnabled = onSetQuietEnabled,
                        onEditStart = { editingField = TimeField.QuietStart },
                        onEditEnd = { editingField = TimeField.QuietEnd }
                    )

                    Spacer(Modifier.height(16.dp))
                    SettingsCard {
                        SettingsSwitchRow(
                            title = "Не напоминать во время прогулки",
                            subtitle = "После завершения прогулки напоминания возобновятся.",
                            checked = settings.pauseDuringWalk,
                            enabled = !uiState.isSaving,
                            onCheckedChange = onSetPauseDuringWalk
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    StatusCard(
                        settings = settings,
                        nextSlot = uiState.nextSlot,
                        notificationAccess = notificationAccess
                    )

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Android может доставить напоминание с задержкой " +
                            "из-за энергосбережения. Это ограничение системы, " +
                            "а не расписания.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }

        val field = editingField
        if (settings != null && field != null) {
            val quiet = settings.quietPeriods.firstOrNull()
            val initialMinute = when (field) {
                TimeField.WorkStart -> settings.workStartMinute
                TimeField.WorkEnd -> settings.workEndMinute
                TimeField.QuietStart ->
                    quiet?.startMinute ?: ReminderDefaults.QUIET_START_MINUTE

                TimeField.QuietEnd ->
                    quiet?.endMinute ?: ReminderDefaults.QUIET_END_MINUTE
            }
            val title = when (field) {
                TimeField.WorkStart -> "Начало периода"
                TimeField.WorkEnd -> "Окончание периода"
                TimeField.QuietStart -> "Начало тихих часов"
                TimeField.QuietEnd -> "Окончание тихих часов"
            }

            ReminderTimePickerDialog(
                title = title,
                initialHour = initialMinute / 60,
                initialMinute = initialMinute % 60,
                onDismiss = { editingField = null },
                onConfirm = { hour, minute ->
                    val totalMinutes = hour * 60 + minute
                    when (field) {
                        TimeField.WorkStart -> onSetWorkStart(totalMinutes)
                        TimeField.WorkEnd -> onSetWorkEnd(totalMinutes)
                        TimeField.QuietStart -> onSetQuietStart(totalMinutes)
                        TimeField.QuietEnd -> onSetQuietEnd(totalMinutes)
                    }
                    editingField = null
                }
            )
        }
    }
}

@Composable
private fun LoadingReminderSettings() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Загружаем настройки…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LoadErrorReminderSettings(onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Не удалось загрузить настройки",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Повторите попытку. Настройки хранятся на устройстве.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRetry) {
            Text("Повторить")
        }
    }
}

@Composable
private fun MasterReminderCard(
    settings: ReminderSettings,
    notificationAccess: NotificationAccess,
    isSaving: Boolean,
    onSetEnabled: (Boolean) -> Unit,
    onRequestNotificationAccess: () -> Unit
) {
    val subtitle = when {
        !settings.isEnabled -> "Расписание не активно."
        notificationAccess.canPost -> "Расписание активно."
        else -> "Нужно разрешение на уведомления."
    }

    SettingsCard {
        SettingsSwitchRow(
            title = "Напоминания",
            subtitle = subtitle,
            checked = settings.isEnabled,
            enabled = !isSaving,
            onCheckedChange = { enabled ->
                onSetEnabled(enabled)
                if (enabled && !notificationAccess.canPost) {
                    onRequestNotificationAccess()
                }
            }
        )
    }
}

@Composable
private fun PermissionWarningCard(
    access: NotificationAccess,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Уведомления заблокированы",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (!access.permissionGranted) {
                    "Android не разрешает показывать уведомления. " +
                        "Без разрешения напоминания не придут."
                } else {
                    "Уведомления отключены в настройках Android " +
                        "или для канала «Напоминания о прогулке»."
                },
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!access.permissionGranted) {
                    TextButton(
                        onClick = onRequest,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Разрешить")
                    }
                }
                TextButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Настройки")
                }
            }
        }
    }
}

@Composable
private fun ActivePeriodCard(
    settings: ReminderSettings,
    enabled: Boolean,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    SettingsCard {
        Text(
            text = "Период напоминаний",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Напоминания планируются внутри периода. " +
                "Время окончания не включается.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Расписание действует каждый день.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        SettingsTimeRow(
            label = "Начало",
            value = formatMinuteOfDay(settings.workStartMinute),
            enabled = enabled,
            onClick = onEditStart
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsTimeRow(
            label = "Окончание",
            value = formatMinuteOfDay(settings.workEndMinute),
            enabled = enabled,
            onClick = onEditEnd
        )
        if (settings.workStartMinute > settings.workEndMinute) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Период переходит через полночь.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IntervalCard(
    intervalMinutes: Int,
    enabled: Boolean,
    onSetInterval: (Int) -> Unit
) {
    SettingsCard {
        Text(
            text = "Интервал между напоминаниями, минут",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Напоминания будут приходить ${formatInterval(intervalMinutes)}.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        val options = ReminderDefaults.ALLOWED_INTERVALS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.take(2).forEach { minutes ->
                IntervalChip(
                    minutes = minutes,
                    selected = intervalMinutes == minutes,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetInterval(minutes) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.drop(2).forEach { minutes ->
                IntervalChip(
                    minutes = minutes,
                    selected = intervalMinutes == minutes,
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onClick = { onSetInterval(minutes) }
                )
            }
        }
    }
}

@Composable
private fun IntervalChip(
    minutes: Int,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = minutes.toString(),
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        modifier = modifier
    )
}

@Composable
private fun QuietHoursCard(
    quiet: QuietPeriod?,
    enabled: Boolean,
    onSetQuietEnabled: (Boolean) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    val quietEnabled = quiet?.isEnabled ?: true
    val startMinute = quiet?.startMinute ?: ReminderDefaults.QUIET_START_MINUTE
    val endMinute = quiet?.endMinute ?: ReminderDefaults.QUIET_END_MINUTE

    SettingsCard {
        SettingsSwitchRow(
            title = "Тихие часы",
            subtitle = "В это время уведомления не приходят.",
            checked = quietEnabled,
            enabled = enabled,
            onCheckedChange = onSetQuietEnabled
        )
        Spacer(Modifier.height(12.dp))
        SettingsTimeRow(
            label = "Начало",
            value = formatMinuteOfDay(startMinute),
            enabled = enabled,
            onClick = onEditStart
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SettingsTimeRow(
            label = "Окончание",
            value = formatMinuteOfDay(endMinute),
            enabled = enabled,
            onClick = onEditEnd
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Период может переходить через полночь. " +
                "Тихие часы имеют приоритет над остальным расписанием.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Тихие часы действуют каждый день.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatusCard(
    settings: ReminderSettings,
    nextSlot: ReminderSlotEntity?,
    notificationAccess: NotificationAccess
) {
    val status = when {
        !settings.isEnabled -> "Напоминания выключены."
        !notificationAccess.canPost ->
            "Уведомления заблокированы системой, поэтому напоминания не будут показаны."

        nextSlot != null -> formatNextReminder(nextSlot)
        else -> "Расписание не создано. Проверьте период и тихие часы."
    }

    SettingsCard {
        Text(
            text = "Состояние",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Период ${formatPeriod(settings.workStartMinute, settings.workEndMinute)}, " +
                "интервал ${settings.intervalMinutes} мин.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ErrorMessageCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Не удалось выполнить действие",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Скрыть")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsTimeRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .widthIn(max = 420.dp)
                .padding(8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state = state)
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Отмена")
                    }
                    TextButton(
                        onClick = { onConfirm(state.hour, state.minute) }
                    ) {
                        Text("Готово")
                    }
                }
            }
        }
    }
}
