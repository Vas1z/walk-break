package ru.vas1z.walkbreak.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.vas1z.walkbreak.data.local.dao.QuietPeriodDao
import ru.vas1z.walkbreak.data.local.dao.ScheduleSettingsDao
import ru.vas1z.walkbreak.data.local.entity.QuietPeriodEntity
import ru.vas1z.walkbreak.data.local.entity.ScheduleSettingsEntity
import ru.vas1z.walkbreak.reminders.ReminderDefaults
import ru.vas1z.walkbreak.reminders.schedule.QuietPeriod
import ru.vas1z.walkbreak.reminders.schedule.ReminderScheduleCalculator
import ru.vas1z.walkbreak.reminders.schedule.ReminderSettings

class ReminderSettingsRepository(
    private val scheduleSettingsDao: ScheduleSettingsDao,
    private val quietPeriodDao: QuietPeriodDao
) {
    private val mutex = Mutex()

    val settings: Flow<ReminderSettings?> = combine(
        scheduleSettingsDao.observe(),
        quietPeriodDao.observeAll()
    ) { settings, quietPeriods ->
        settings?.toDomain(quietPeriods)
    }

    suspend fun ensureDefaults() {
        mutex.withLock { ensureDefaultsLocked() }
    }

    suspend fun getSettings(): ReminderSettings = mutex.withLock {
        ensureDefaultsLocked()
        readSettingsLocked()
    }

    suspend fun update(
        transform: (ScheduleSettingsEntity) -> ScheduleSettingsEntity
    ): ReminderSettings = mutex.withLock {
        ensureDefaultsLocked()
        val current = scheduleSettingsDao.get()
            ?: error("Schedule settings must exist after defaults")
        val updated = transform(current).copy(
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        validate(updated)
        scheduleSettingsDao.upsert(updated)
        readSettingsLocked()
    }

    suspend fun updateQuietPeriod(
        transform: (QuietPeriodEntity) -> QuietPeriodEntity
    ): QuietPeriodEntity = mutex.withLock {
        ensureDefaultsLocked()
        val current = quietPeriodDao.getFirst()
            ?: error("Quiet period must exist after defaults")
        val updated = transform(current)
        validate(updated)
        quietPeriodDao.update(updated)
        updated
    }

    private suspend fun ensureDefaultsLocked() {
        // Сначала тихий период, чтобы первая эмиссия настроек уже содержала его.
        if (quietPeriodDao.getFirst() == null) {
            quietPeriodDao.insert(
                QuietPeriodEntity(
                    startMinuteOfDay = ReminderDefaults.QUIET_START_MINUTE,
                    endMinuteOfDay = ReminderDefaults.QUIET_END_MINUTE,
                    daysMask = ReminderDefaults.DAYS_EVERY_DAY,
                    isEnabled = true
                )
            )
        }
        if (scheduleSettingsDao.get() == null) {
            scheduleSettingsDao.upsert(
                ScheduleSettingsEntity(
                    id = 1,
                    isEnabled = ReminderDefaults.IS_ENABLED_BY_DEFAULT,
                    workStartMinute = ReminderDefaults.WORK_START_MINUTE,
                    workEndMinute = ReminderDefaults.WORK_END_MINUTE,
                    intervalMinutes = ReminderDefaults.INTERVAL_MINUTES,
                    pauseDuringWalk = ReminderDefaults.PAUSE_DURING_WALK,
                    walkDurationMinutes = ReminderDefaults.WALK_DURATION_MINUTES,
                    updatedAtEpochMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun readSettingsLocked(): ReminderSettings {
        val settings = scheduleSettingsDao.get()
            ?: error("Schedule settings must exist after defaults")
        return settings.toDomain(quietPeriodDao.getAll())
    }

    private fun validate(settings: ScheduleSettingsEntity) {
        require(
            settings.workStartMinute in 0 until ReminderScheduleCalculator.MINUTES_PER_DAY
        ) { "workStartMinute is out of range" }
        require(
            settings.workEndMinute in 0 until ReminderScheduleCalculator.MINUTES_PER_DAY
        ) { "workEndMinute is out of range" }
        require(settings.workStartMinute != settings.workEndMinute) {
            "Active period must not be empty"
        }
        require(settings.intervalMinutes in ReminderDefaults.ALLOWED_INTERVALS) {
            "Unsupported interval: ${settings.intervalMinutes}"
        }
    }

    private fun validate(quietPeriod: QuietPeriodEntity) {
        require(
            quietPeriod.startMinuteOfDay in 0 until ReminderScheduleCalculator.MINUTES_PER_DAY
        ) { "quiet start is out of range" }
        require(
            quietPeriod.endMinuteOfDay in 0 until ReminderScheduleCalculator.MINUTES_PER_DAY
        ) { "quiet end is out of range" }
        require(quietPeriod.daysMask in 0..ReminderDefaults.DAYS_EVERY_DAY) {
            "daysMask is out of range"
        }
        if (quietPeriod.isEnabled) {
            require(quietPeriod.startMinuteOfDay != quietPeriod.endMinuteOfDay) {
                "Quiet period must not be empty"
            }
        }
    }

    private fun ScheduleSettingsEntity.toDomain(
        quietPeriods: List<QuietPeriodEntity>
    ): ReminderSettings = ReminderSettings(
        isEnabled = isEnabled,
        workStartMinute = workStartMinute,
        workEndMinute = workEndMinute,
        intervalMinutes = intervalMinutes,
        pauseDuringWalk = pauseDuringWalk,
        quietPeriods = quietPeriods.map { it.toDomain() }
    )

    private fun QuietPeriodEntity.toDomain(): QuietPeriod = QuietPeriod(
        id = id,
        startMinute = startMinuteOfDay,
        endMinute = endMinuteOfDay,
        daysMask = daysMask,
        isEnabled = isEnabled
    )
}
