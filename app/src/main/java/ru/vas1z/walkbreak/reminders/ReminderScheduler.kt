package ru.vas1z.walkbreak.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity
import ru.vas1z.walkbreak.data.repository.ReminderSettingsRepository
import ru.vas1z.walkbreak.data.repository.ReminderSlotRepository
import ru.vas1z.walkbreak.data.repository.WalkSessionRepository
import ru.vas1z.walkbreak.reminders.receiver.ReminderAlarmReceiver
import ru.vas1z.walkbreak.reminders.schedule.ReminderScheduleCalculator
import ru.vas1z.walkbreak.reminders.schedule.ReminderSettings

sealed interface ReminderScheduleResult {
    data class Scheduled(
        val triggerAtEpochMillis: Long,
        val isSnooze: Boolean
    ) : ReminderScheduleResult

    data object Disabled : ReminderScheduleResult
    data object NotificationsBlocked : ReminderScheduleResult
    data object NoAllowedTime : ReminderScheduleResult
}

/**
 * Планировщик напоминаний на AlarmManager.
 *
 * Используется один PendingIntent и один открытый слот в Room. Это исключает
 * дубли и позволяет восстановить расписание после перезапуска/загрузки.
 *
 * AlarmManager.setAndAllowWhileIdle — неточный будильник: Android может
 * доставить его с задержкой, особенно в Doze. Это осознанный выбор без
 * SCHEDULE_EXACT_ALARM и без foreground service.
 */
class ReminderScheduler(
    context: Context,
    private val settingsRepository: ReminderSettingsRepository,
    private val slotRepository: ReminderSlotRepository,
    private val walkSessionRepository: WalkSessionRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    private val appContext = context.applicationContext
    private val alarmManager =
        appContext.getSystemService(AlarmManager::class.java)
    private val mutex = Mutex()

    suspend fun restoreOrSchedule(
        preserveSnooze: Boolean = true
    ): ReminderScheduleResult = mutex.withLock {
        val settings = settingsRepository.getSettings()
        if (!settings.isEnabled) {
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.Disabled
        }
        if (!ReminderNotifications.canPost(appContext)) {
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.NotificationsBlocked
        }

        val now = now()
        if (preserveSnooze) {
            val openSlot = slotRepository.getOpenSlot()
            if (openSlot != null &&
                openSlot.status == ReminderSlotEntity.STATUS_SNOOZED &&
                openSlot.scheduledAtEpochMillis > clock.millis()
            ) {
                val slotTime = toZoned(openSlot.scheduledAtEpochMillis)
                if (ReminderScheduleCalculator.isAllowed(slotTime, settings)) {
                    setAlarmLocked(openSlot.scheduledAtEpochMillis, openSlot.id)
                    return@withLock ReminderScheduleResult.Scheduled(
                        triggerAtEpochMillis = openSlot.scheduledAtEpochMillis,
                        isSnooze = true
                    )
                }
            }
        }

        scheduleNextLocked(settings, now)
    }

    suspend fun scheduleSnooze(): ReminderScheduleResult = mutex.withLock {
        val settings = settingsRepository.getSettings()
        if (!settings.isEnabled) {
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.Disabled
        }
        if (!ReminderNotifications.canPost(appContext)) {
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.NotificationsBlocked
        }

        val nextSnooze = ReminderScheduleCalculator.nextSnooze(
            now = now(),
            settings = settings,
            snoozeMinutes = ReminderDefaults.SNOOZE_MINUTES
        )
        if (nextSnooze == null) {
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.NoAllowedTime
        }

        val slot = slotRepository.replaceOpenSlot(
            scheduledAtEpochMillis = nextSnooze.toInstant().toEpochMilli(),
            status = ReminderSlotEntity.STATUS_SNOOZED
        )
        setAlarmLocked(slot.scheduledAtEpochMillis, slot.id)
        ReminderScheduleResult.Scheduled(
            triggerAtEpochMillis = slot.scheduledAtEpochMillis,
            isSnooze = true
        )
    }

    suspend fun handleAlarm(
        slotId: Long,
        scheduledAtEpochMillis: Long
    ): ReminderScheduleResult = mutex.withLock {
        val settings = settingsRepository.getSettings()
        val now = now()
        val nowMillis = clock.millis()

        if (!settings.isEnabled) {
            if (slotId > 0) {
                slotRepository.markStatus(
                    slotId,
                    ReminderSlotEntity.STATUS_CANCELLED
                )
            }
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.Disabled
        }
        if (!ReminderNotifications.canPost(appContext)) {
            if (slotId > 0) {
                slotRepository.markStatus(
                    slotId,
                    ReminderSlotEntity.STATUS_SKIPPED
                )
            }
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return@withLock ReminderScheduleResult.NotificationsBlocked
        }

        val slot = if (slotId > 0) slotRepository.getById(slotId) else null
        if (slot == null || !slot.status.isOpen()) {
            Log.i(TAG, "Ignoring stale alarm for slot $slotId")
            return@withLock scheduleNextLocked(settings, now)
        }
        if (scheduledAtEpochMillis > 0 &&
            nowMillis - scheduledAtEpochMillis > LATE_TOLERANCE_MILLIS
        ) {
            Log.i(TAG, "Skipping late alarm for slot $slotId")
            slotRepository.markStatus(slotId, ReminderSlotEntity.STATUS_EXPIRED)
            return@withLock scheduleNextLocked(settings, now)
        }
        if (settings.pauseDuringWalk && walkSessionRepository.hasActiveWalk()) {
            Log.i(TAG, "Skipping reminder: a walk is active")
            slotRepository.markStatus(slotId, ReminderSlotEntity.STATUS_SKIPPED)
            return@withLock scheduleNextLocked(settings, now)
        }
        if (!ReminderScheduleCalculator.isAllowed(now, settings)) {
            // Тихие часы имеют приоритет даже при запоздавшем срабатывании.
            Log.i(TAG, "Skipping reminder outside active/quiet schedule")
            slotRepository.markStatus(slotId, ReminderSlotEntity.STATUS_SKIPPED)
            return@withLock scheduleNextLocked(settings, now)
        }

        val posted = ReminderNotifications.showReminder(appContext)
        slotRepository.markStatus(
            slotId,
            if (posted) {
                ReminderSlotEntity.STATUS_COMPLETED
            } else {
                ReminderSlotEntity.STATUS_SKIPPED
            }
        )
        scheduleNextLocked(settings, now)
    }

    suspend fun rescheduleAfterSettingsChange(): ReminderScheduleResult =
        restoreOrSchedule(preserveSnooze = false)

    suspend fun onWalkStarted(): ReminderScheduleResult {
        ReminderNotifications.cancel(appContext)
        return restoreOrSchedule(preserveSnooze = true)
    }

    suspend fun onWalkFinished(): ReminderScheduleResult =
        restoreOrSchedule(preserveSnooze = true)

    suspend fun onSystemTimeChanged(): ReminderScheduleResult =
        restoreOrSchedule(preserveSnooze = false)

    private suspend fun scheduleNextLocked(
        settings: ReminderSettings,
        now: ZonedDateTime
    ): ReminderScheduleResult {
        if (!settings.isEnabled) {
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return ReminderScheduleResult.Disabled
        }
        if (!ReminderNotifications.canPost(appContext)) {
            ReminderNotifications.cancel(appContext)
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return ReminderScheduleResult.NotificationsBlocked
        }

        val next = ReminderScheduleCalculator.nextReminder(now, settings)
        if (next == null) {
            cancelAlarmLocked()
            slotRepository.cancelOpenSlots()
            return ReminderScheduleResult.NoAllowedTime
        }

        val slot = slotRepository.replaceOpenSlot(
            scheduledAtEpochMillis = next.toInstant().toEpochMilli(),
            status = ReminderSlotEntity.STATUS_PENDING
        )
        setAlarmLocked(slot.scheduledAtEpochMillis, slot.id)
        return ReminderScheduleResult.Scheduled(
            triggerAtEpochMillis = slot.scheduledAtEpochMillis,
            isSnooze = false
        )
    }

    private fun setAlarmLocked(triggerAtEpochMillis: Long, slotId: Long) {
        val intent = alarmIntent().apply {
            putExtra(ReminderContract.EXTRA_SLOT_ID, slotId)
            putExtra(ReminderContract.EXTRA_SCHEDULED_AT, triggerAtEpochMillis)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ReminderContract.ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val safeTriggerAt = if (triggerAtEpochMillis > clock.millis()) {
            triggerAtEpochMillis
        } else {
            clock.millis() + 1_000L
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            safeTriggerAt,
            pendingIntent
        )
    }

    private fun cancelAlarmLocked() {
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            ReminderContract.ALARM_REQUEST_CODE,
            alarmIntent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun alarmIntent(): Intent =
        Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_REMIND
        }

    private fun now(): ZonedDateTime =
        ZonedDateTime.now(clock).withZoneSameInstant(ZoneId.systemDefault())

    private fun toZoned(epochMillis: Long): ZonedDateTime =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())

    private fun String.isOpen(): Boolean =
        this == ReminderSlotEntity.STATUS_PENDING ||
            this == ReminderSlotEntity.STATUS_SNOOZED

    private companion object {
        const val TAG = "ReminderScheduler"
        const val LATE_TOLERANCE_MILLIS = 30 * 60 * 1000L
    }
}
