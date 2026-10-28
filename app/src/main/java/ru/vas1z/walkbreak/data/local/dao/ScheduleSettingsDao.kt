package ru.vas1z.walkbreak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.vas1z.walkbreak.data.local.entity.ScheduleSettingsEntity

@Dao
interface ScheduleSettingsDao {

    @Query("SELECT * FROM schedule_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<ScheduleSettingsEntity?>

    @Query("SELECT * FROM schedule_settings WHERE id = 1 LIMIT 1")
    suspend fun get(): ScheduleSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settings: ScheduleSettingsEntity)
}
