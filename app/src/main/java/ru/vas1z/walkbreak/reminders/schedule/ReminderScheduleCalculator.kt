package ru.vas1z.walkbreak.reminders.schedule

import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Чистая логика расписания напоминаний.
 *
 * Слоты привязаны к началу активного периода и идут с шагом [ReminderSettings.intervalMinutes].
 * Например, 09:00–21:00 с интервалом 120 минут даёт 09:00, 11:00, ..., 19:00.
 * Конец периода не включается.
 */
object ReminderScheduleCalculator {

    const val MINUTES_PER_DAY = 1440
    private const val DEFAULT_SEARCH_DAYS = 31

    fun nextReminder(
        now: ZonedDateTime,
        settings: ReminderSettings,
        searchDays: Int = DEFAULT_SEARCH_DAYS
    ): ZonedDateTime? {
        if (!settings.isEnabled) return null
        if (settings.intervalMinutes <= 0) return null
        if (settings.workStartMinute == settings.workEndMinute) return null

        val zone = now.zone
        val firstStartDate = now.toLocalDate().minusDays(1)
        val lastStartDate = now.toLocalDate().plusDays(searchDays.toLong())

        var startDate = firstStartDate
        while (!startDate.isAfter(lastStartDate)) {
            for (slot in slotsForWindow(startDate, settings, zone)) {
                if (slot.isAfter(now) && isAllowed(slot, settings)) {
                    return slot
                }
            }
            startDate = startDate.plusDays(1)
        }
        return null
    }

    /**
     * Время для «Напомнить позже»: через [snoozeMinutes] минут, если это
     * допустимое время. Иначе — ближайший допустимый слот расписания.
     */
    fun nextSnooze(
        now: ZonedDateTime,
        settings: ReminderSettings,
        snoozeMinutes: Long = 15L
    ): ZonedDateTime? {
        if (!settings.isEnabled) return null

        val candidate = now.plusMinutes(snoozeMinutes)
        if (isWithinActiveWindow(candidate, settings) &&
            !isQuiet(candidate, settings.quietPeriods)
        ) {
            return candidate
        }
        return nextReminder(candidate, settings)
    }

    fun isAllowed(time: ZonedDateTime, settings: ReminderSettings): Boolean =
        isWithinActiveWindow(time, settings) &&
            !isQuiet(time, settings.quietPeriods)

    fun isWithinActiveWindow(
        time: ZonedDateTime,
        settings: ReminderSettings
    ): Boolean {
        val start = settings.workStartMinute
        val end = settings.workEndMinute
        if (start == end) return false

        val minute = time.hour * 60 + time.minute
        return if (start < end) {
            minute >= start && minute < end
        } else {
            minute >= start || minute < end
        }
    }

    fun isQuiet(
        time: ZonedDateTime,
        periods: List<QuietPeriod>
    ): Boolean {
        val minute = time.hour * 60 + time.minute
        val dayIndex = time.dayOfWeek.value - 1

        return periods.any { period ->
            if (!period.isEnabled || period.startMinute == period.endMinute) {
                return@any false
            }

            if (period.startMinute < period.endMinute) {
                period.appliesToDay(dayIndex) &&
                    minute >= period.startMinute &&
                    minute < period.endMinute
            } else {
                val eveningPart = minute >= period.startMinute &&
                    period.appliesToDay(dayIndex)
                val previousDayIndex = (dayIndex + 6) % 7
                val morningPart = minute < period.endMinute &&
                    period.appliesToDay(previousDayIndex)
                eveningPart || morningPart
            }
        }
    }

    internal fun slotsForWindow(
        startDate: java.time.LocalDate,
        settings: ReminderSettings,
        zone: ZoneId
    ): List<ZonedDateTime> {
        val start = settings.workStartMinute
        val end = settings.workEndMinute

        val windowLength = when {
            start < end -> end - start
            start > end -> MINUTES_PER_DAY - start + end
            else -> 0
        }
        if (windowLength <= 0 || settings.intervalMinutes <= 0) {
            return emptyList()
        }

        val result = ArrayList<ZonedDateTime>()
        var offset = 0
        while (offset < windowLength) {
            val absoluteMinute = start + offset
            val dayOffset = absoluteMinute / MINUTES_PER_DAY
            val minuteOfDay = absoluteMinute % MINUTES_PER_DAY
            val date = startDate.plusDays(dayOffset.toLong())
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            result += ZonedDateTime.of(date, time, zone)
            offset += settings.intervalMinutes
        }
        return result
    }
}
