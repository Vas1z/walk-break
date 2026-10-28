package ru.vas1z.walkbreak.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalkTimerTest {

    private val startedAt = 1_700_000_000_000L

    @Test
    fun timerIsDerivedFromSavedStart() {
        val timer = calculateWalkTimer(
            startedAtEpochMillis = startedAt,
            nowEpochMillis = startedAt + 90_000L,
            plannedMinutes = 5
        )

        assertEquals(90L, timer.elapsedSeconds)
        assertEquals(300L, timer.plannedSeconds)
        assertEquals(210L, timer.remainingSeconds)
        assertFalse(timer.isOverdue)
    }

    @Test
    fun timerDoesNotResetWhenTimePasses() {
        val first = calculateWalkTimer(
            startedAtEpochMillis = startedAt,
            nowEpochMillis = startedAt + 60_000L,
            plannedMinutes = 5
        )
        val second = calculateWalkTimer(
            startedAtEpochMillis = startedAt,
            nowEpochMillis = startedAt + 61_000L,
            plannedMinutes = 5
        )

        assertEquals(first.elapsedSeconds + 1, second.elapsedSeconds)
        assertEquals(first.remainingSeconds - 1, second.remainingSeconds)
    }

    @Test
    fun clockSkewDoesNotMakeTimerNegative() {
        val timer = calculateWalkTimer(
            startedAtEpochMillis = startedAt,
            nowEpochMillis = startedAt - 10_000L,
            plannedMinutes = 5
        )

        assertEquals(0L, timer.elapsedSeconds)
        assertEquals(300L, timer.remainingSeconds)
        assertFalse(timer.isOverdue)
    }

    @Test
    fun overdueTimerStopsAtZero() {
        val exactlyAtEnd = calculateWalkTimer(
            startedAtEpochMillis = startedAt,
            nowEpochMillis = startedAt + 300_000L,
            plannedMinutes = 5
        )
        assertEquals(0L, exactlyAtEnd.remainingSeconds)
        assertTrue(exactlyAtEnd.isOverdue)

        val afterEnd = calculateWalkTimer(
            startedAtEpochMillis = startedAt,
            nowEpochMillis = startedAt + 301_500L,
            plannedMinutes = 5
        )
        assertEquals(301L, afterEnd.elapsedSeconds)
        assertEquals(0L, afterEnd.remainingSeconds)
        assertTrue(afterEnd.isOverdue)
    }

    @Test
    fun formatsMinutesAndSeconds() {
        assertEquals("00:00", formatWalkTimer(0))
        assertEquals("00:59", formatWalkTimer(59))
        assertEquals("01:00", formatWalkTimer(60))
        assertEquals("05:00", formatWalkTimer(300))
        assertEquals("59:59", formatWalkTimer(3_599))
    }
}
