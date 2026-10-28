package ru.vas1z.walkbreak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import ru.vas1z.walkbreak.data.local.entity.WalkSessionEntity

@Dao
abstract class WalkSessionDao {

    @Query(
        "SELECT * FROM walk_sessions " +
            "WHERE status = 'IN_PROGRESS' " +
            "ORDER BY startedAtEpochMillis DESC LIMIT 1"
    )
    abstract fun observeActive(): Flow<WalkSessionEntity?>

    @Query("SELECT COUNT(*) FROM walk_sessions WHERE status = 'COMPLETED'")
    abstract fun observeCompletedCount(): Flow<Int>

    @Query(
        "SELECT * FROM walk_sessions " +
            "WHERE status = 'IN_PROGRESS' " +
            "ORDER BY startedAtEpochMillis DESC LIMIT 1"
    )
    abstract suspend fun findActive(): WalkSessionEntity?

    @Insert
    abstract suspend fun insert(session: WalkSessionEntity): Long

    @Query(
        "UPDATE walk_sessions " +
            "SET finishedAtEpochMillis = :finishedAtEpochMillis, status = 'COMPLETED' " +
            "WHERE id = :id AND status = 'IN_PROGRESS'"
    )
    abstract suspend fun finish(id: Long, finishedAtEpochMillis: Long): Int

    @Transaction
    open suspend fun startIfIdle(session: WalkSessionEntity): StartWalkResult {
        if (findActive() != null) {
            return StartWalkResult.Rejected
        }
        return StartWalkResult.Started(insert(session))
    }
}
