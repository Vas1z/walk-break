package ru.vas1z.walkbreak.ui.reminders

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity
import ru.vas1z.walkbreak.reminders.NotificationAccess

data class ReminderSummaryUi(
    val title: String,
    val subtitle: String
)

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM")

fun formatMinuteOfDay(minute: Int): String {
    val safe = ((minute % 1440) + 1440) % 1440
    return "%02d:%02d".format(safe / 60, safe % 60)
}

fun formatInterval(minutes: Int): String = "каждые $minutes минут"

fun formatPeriod(startMinute: Int, endMinute: Int): String =
    "с ${formatMinuteOfDay(startMinute)} до ${formatMinuteOfDay(endMinute)}"

fun formatNextReminder(
    slot: ReminderSlotEntity,
    now: ZonedDateTime = ZonedDateTime.now()
): String {
    val time = Instant.ofEpochMilli(slot.scheduledAtEpochMillis)
        .atZone(ZoneId.systemDefault())
    val date = when (time.toLocalDate()) {
        now.toLocalDate() -> "сегодня"
        now.toLocalDate().plusDays(1) -> "завтра"
        else -> time.format(DATE_FORMATTER)
    }
    val label = if (slot.status == ReminderSlotEntity.STATUS_SNOOZED) {
        "Отложено"
    } else {
        "Следующее"
    }
    return "$label: $date в ${time.format(TIME_FORMATTER)}"
}

fun buildReminderSummary(
    uiState: ReminderSettingsUiState,
    access: NotificationAccess
): ReminderSummaryUi {
    if (uiState.isLoading) {
        return ReminderSummaryUi(
            title = "Загрузка…",
            subtitle = "Проверяем настройки напоминаний"
        )
    }
    if (uiState.loadFailed) {
        return ReminderSummaryUi(
            title = "Ошибка загрузки",
            subtitle = "Откройте настройки и повторите"
        )
    }
    val settings = uiState.settings ?: return ReminderSummaryUi(
        title = "Нет данных",
        subtitle = "Откройте настройки напоминаний"
    )

    return when {
        !settings.isEnabled -> ReminderSummaryUi(
            title = "Выключены",
            subtitle = "Настройте удобное расписание"
        )

        !access.canPost -> ReminderSummaryUi(
            title = "Нужно разрешение",
            subtitle = "Разрешите уведомления в настройках"
        )

        uiState.nextSlot == null -> ReminderSummaryUi(
            title = "Нет расписания",
            subtitle = "Проверьте период и тихие часы"
        )

        else -> ReminderSummaryUi(
            title = "Включены",
            subtitle = formatNextReminder(uiState.nextSlot)
        )
    }
}
