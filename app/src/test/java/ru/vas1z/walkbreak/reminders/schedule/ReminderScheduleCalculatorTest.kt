package ru.vas1z.walkbreak.reminders.schedule

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderScheduleCalculatorTest {

    private val zone = ZoneId.of("Asia/Novosibirsk")

    private val defaultQuiet = QuietPeriod(
        startMinute = 22 * 60,
        endMinute = 8 * 60,
        daysMask = 0b1111111,
        isEnabled = true
    )

    private fun settings(
        isEnabled: Boolean = true,
        startMinute: Int = 9 * 60,
        endMinute: Int = 21 * 60,
        intervalMinutes: Int = 120,
        quietPeriods: List<QuietPeriod> = listOf(defaultQuiet),
        pauseDuringWalk: Boolean = true
    ) = ReminderSettings(
        isEnabled = isEnabled,
        workStartMinute = startMinute,
        workEndMinute = endMinute,
        intervalMinutes = intervalMinutes,
        pauseDuringWalk = pauseDuringWalk,
        quietPeriods = quietPeriods
    )

    /** 26 октября 2026 — понедельник. */
    private fun at(day: Int, hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    @Test
    fun defaultScheduleStartsAtNine() {
        assertEquals(
            at(26, 9),
            ReminderScheduleCalculator.nextReminder(at(26, 8), settings())
        )
    }

    @Test
    fun nextReminderUsesIntervalFromPeriodStart() {
        assertEquals(
            at(26, 11),
            ReminderScheduleCalculator.nextReminder(at(26, 9, 30), settings())
        )
        assertEquals(
            at(27, 9),
            ReminderScheduleCalculator.nextReminder(at(26, 19, 30), settings())
        )
    }

    @Test
    fun activePeriodEndIsExclusive() {
        // 19:00 — последний слот; 21:00 уже не входит в период.
        assertEquals(
            at(26, 19),
            ReminderScheduleCalculator.nextReminder(at(26, 18, 30), settings())
        )
        assertEquals(
            at(27, 9),
            ReminderScheduleCalculator.nextReminder(at(26, 19), settings())
        )
        assertEquals(
            at(27, 9),
            ReminderScheduleCalculator.nextReminder(at(26, 21, 30), settings())
        )
    }

    @Test
    fun quietHoursBlockSlotsAndHavePriority() {
        val quiet = listOf(defaultQuiet)
        assertTrue(ReminderScheduleCalculator.isQuiet(at(26, 22), quiet))
        assertTrue(ReminderScheduleCalculator.isQuiet(at(27, 7, 59), quiet))
        assertFalse(ReminderScheduleCalculator.isQuiet(at(27, 8), quiet))
        assertFalse(ReminderScheduleCalculator.isQuiet(at(27, 21, 59), quiet))

        // После 19:00 следующий допустимый слот — завтра в 09:00.
        assertEquals(
            at(27, 9),
            ReminderScheduleCalculator.nextReminder(at(26, 20, 30), settings())
        )
    }

    @Test
    fun quietDaysMaskAppliesToTheStartDayAcrossMidnight() {
        val mondayOnlyQuiet = listOf(
            QuietPeriod(
                startMinute = 22 * 60,
                endMinute = 8 * 60,
                daysMask = 0b0000001,
                isEnabled = true
            )
        )
        // Вечер понедельника и утро вторника относятся к понедельнику.
        assertTrue(
            ReminderScheduleCalculator.isQuiet(at(26, 23), mondayOnlyQuiet)
        )
        assertTrue(
            ReminderScheduleCalculator.isQuiet(at(27, 7), mondayOnlyQuiet)
        )
        // Второй вечер уже не тихий.
        assertFalse(
            ReminderScheduleCalculator.isQuiet(at(27, 23), mondayOnlyQuiet)
        )
    }

    @Test
    fun activePeriodCanCrossMidnight() {
        val crossing = settings(
            startMinute = 22 * 60,
            endMinute = 6 * 60,
            quietPeriods = emptyList()
        )
        assertEquals(
            at(26, 22),
            ReminderScheduleCalculator.nextReminder(at(26, 21), crossing)
        )
        assertEquals(
            at(27, 2),
            ReminderScheduleCalculator.nextReminder(at(27, 1), crossing)
        )
        assertEquals(
            at(27, 22),
            ReminderScheduleCalculator.nextReminder(at(27, 4, 30), crossing)
        )
    }

    @Test
    fun quietHoursCanBlockTheWholeActivePeriod() {
        val crossingAndQuiet = settings(
            startMinute = 22 * 60,
            endMinute = 6 * 60,
            quietPeriods = listOf(defaultQuiet)
        )
        assertNull(
            ReminderScheduleCalculator.nextReminder(at(26, 21), crossingAndQuiet)
        )
    }

    @Test
    fun ninetyMinuteIntervalIsSupported() {
        assertEquals(
            at(26, 10, 30),
            ReminderScheduleCalculator.nextReminder(
                at(26, 9),
                settings(intervalMinutes = 90)
            )
        )
    }

    @Test
    fun snoozeWithinAllowedTime() {
        assertEquals(
            at(26, 12, 15),
            ReminderScheduleCalculator.nextSnooze(
                at(26, 12),
                settings(),
                snoozeMinutes = 15
            )
        )
    }

    @Test
    fun snoozeMovesToNextAllowedTime() {
        // 20:50 + 15 = 21:05, уже вне периода.
        assertEquals(
            at(27, 9),
            ReminderScheduleCalculator.nextSnooze(
                at(26, 20, 50),
                settings(),
                snoozeMinutes = 15
            )
        )
        // 07:50 + 15 = 08:05: тихие часы закончились, но период ещё не начался.
        assertEquals(
            at(26, 9),
            ReminderScheduleCalculator.nextSnooze(
                at(26, 7, 50),
                settings(),
                snoozeMinutes = 15
            )
        )
        // 07:00 + 15 попадает в тихие часы.
        assertEquals(
            at(26, 9),
            ReminderScheduleCalculator.nextSnooze(
                at(26, 7, 0),
                settings(),
                snoozeMinutes = 15
            )
        )
    }

    @Test
    fun repeatedCalculationIsDeterministic() {
        val now = at(26, 9, 30)
        val first = ReminderScheduleCalculator.nextReminder(now, settings())
        val second = ReminderScheduleCalculator.nextReminder(now, settings())
        assertEquals(first, second)
    }

    @Test
    fun disabledRemindersProduceNoSchedule() {
        val disabled = settings(isEnabled = false)
        assertNull(ReminderScheduleCalculator.nextReminder(at(26, 8), disabled))
        assertNull(ReminderScheduleCalculator.nextSnooze(at(26, 8), disabled))
    }
}
