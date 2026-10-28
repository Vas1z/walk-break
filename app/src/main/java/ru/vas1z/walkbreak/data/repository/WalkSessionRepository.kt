package ru.vas1z.walkbreak.data.repository

import kotlinx.coroutines.flow.Flow
import ru.vas1z.walkbreak.data.local.WalkBreakDatabase
import ru.vas1z.walkbreak.data.local.dao.StartWalkResult
import ru.vas1z.walkbreak.data.local.entity.WalkSessionEntity

class WalkSessionRepository(
    database: WalkBreakDatabase
) {
    private val dao = database.walkSessionDao()

    val activeSession: Flow<WalkSessionEntity?> = dao.observeActive()
    val completedCount: Flow<Int> = dao.observeCompletedCount()

    suspend fun startWalk(
        startedAtEpochMillis: Long,
        plannedMinutes: Int = WalkSessionEntity.DEFAULT_PLANNED_MINUTES,
        source: String = WalkSessionEntity.SOURCE_MANUAL
    ): StartWalkResult {
        require(plannedMinutes > 0) {
            "plannedMinutes must be positive: $plannedMinutes"
        }
        require(
            source == WalkSessionEntity.SOURCE_MANUAL ||
                source == WalkSessionEntity.SOURCE_REMINDER
        ) { "Unsupported walk source: $source" }

        return dao.startIfIdle(
            WalkSessionEntity(
                startedAtEpochMillis = startedAtEpochMillis,
                finishedAtEpochMillis = null,
                plannedMinutes = plannedMinutes,
                source = source,
                status = WalkSessionEntity.STATUS_IN_PROGRESS
            )
        )
    }

    suspend fun finishWalk(
        sessionId: Long,
        finishedAtEpochMillis: Long
    ): Boolean = dao.finish(sessionId, finishedAtEpochMillis) > 0

    /**
     * Проверка активной прогулки по данным Room.
     * Нужна фоновым компонентам, у которых нет доступа к состоянию экрана.
     */
    suspend fun hasActiveWalk(): Boolean = dao.findActive() != null
}
