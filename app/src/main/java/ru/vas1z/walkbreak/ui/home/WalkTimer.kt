package ru.vas1z.walkbreak.ui.home

data class WalkTimerState(
    val elapsedSeconds: Long,
    val plannedSeconds: Long,
    val remainingSeconds: Long
) {
    val isOverdue: Boolean get() = elapsedSeconds >= plannedSeconds
}

/**
 * Таймер всегда вычисляется из сохранённого времени начала прогулки.
 * Пересоздание Activity/ViewModel не сбрасывает его.
 */
fun calculateWalkTimer(
    startedAtEpochMillis: Long,
    nowEpochMillis: Long,
    plannedMinutes: Int
): WalkTimerState {
    val elapsedMillis = (nowEpochMillis - startedAtEpochMillis).coerceAtLeast(0L)
    val elapsedSeconds = elapsedMillis / 1_000L
    val plannedSeconds = plannedMinutes.coerceAtLeast(0).toLong() * 60L
    val remainingSeconds = (plannedSeconds - elapsedSeconds).coerceAtLeast(0L)

    return WalkTimerState(
        elapsedSeconds = elapsedSeconds,
        plannedSeconds = plannedSeconds,
        remainingSeconds = remainingSeconds
    )
}

fun formatWalkTimer(seconds: Long): String {
    val safeSeconds = seconds.coerceAtLeast(0L)
    return "%02d:%02d".format(safeSeconds / 60, safeSeconds % 60)
}
