package ru.vas1z.walkbreak.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.vas1z.walkbreak.data.local.entity.QuietPeriodEntity

@Dao
interface QuietPeriodDao {

    @Query("SELECT * FROM quiet_periods ORDER BY id ASC")
    fun observeAll(): Flow<List<QuietPeriodEntity>>

    @Query("SELECT * FROM quiet_periods ORDER BY id ASC")
    suspend fun getAll(): List<QuietPeriodEntity>

    @Query("SELECT * FROM quiet_periods ORDER BY id ASC LIMIT 1")
    suspend fun getFirst(): QuietPeriodEntity?

    @Insert
    suspend fun insert(period: QuietPeriodEntity): Long

    @Update
    suspend fun update(period: QuietPeriodEntity)
}
