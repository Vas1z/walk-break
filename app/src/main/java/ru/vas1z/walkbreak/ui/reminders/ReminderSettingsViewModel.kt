package ru.vas1z.walkbreak.ui.reminders

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.vas1z.walkbreak.data.local.entity.ReminderSlotEntity
import ru.vas1z.walkbreak.data.repository.ReminderSettingsRepository
import ru.vas1z.walkbreak.data.repository.ReminderSlotRepository
import ru.vas1z.walkbreak.reminders.ReminderDefaults
import ru.vas1z.walkbreak.reminders.ReminderScheduler
import ru.vas1z.walkbreak.reminders.schedule.QuietPeriod
import ru.vas1z.walkbreak.reminders.schedule.ReminderSettings

data class ReminderSettingsUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val isSaving: Boolean = false,
    val settings: ReminderSettings? = null,
    val nextSlot: ReminderSlotEntity? = null,
    val errorMessage: String? = null
)

class ReminderSettingsViewModel(
    private val settingsRepository: ReminderSettingsRepository,
    private val slotRepository: ReminderSlotRepository,
    private val scheduler: ReminderScheduler
) : ViewModel() {

    private data class ActionState(
        val isSaving: Boolean = false,
        val loadFailed: Boolean = false,
        val errorMessage: String? = null
    )

    private val actionState = MutableStateFlow(ActionState())
    private val loadedSettings = MutableStateFlow<ReminderSettings?>(null)
    private val loadedSlot = MutableStateFlow<ReminderSlotEntity?>(null)
    private var observeJob: Job? = null

    val uiState: StateFlow<ReminderSettingsUiState> = combine(
        loadedSettings,
        loadedSlot,
        actionState
    ) { settings, slot, action ->
        ReminderSettingsUiState(
            isLoading = settings == null && !action.loadFailed,
            loadFailed = action.loadFailed,
            isSaving = action.isSaving,
            settings = settings,
            nextSlot = slot,
            errorMessage = action.errorMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = ReminderSettingsUiState()
    )

    init {
        observeReminderData()
        syncSchedule()
    }

    fun retryLoad() {
        observeReminderData()
        syncSchedule()
    }

    /** Синхронизирует расписание с текущими настройками и разрешением. */
    fun syncSchedule() {
        viewModelScope.launch {
            try {
                settingsRepository.ensureDefaults()
                scheduler.restoreOrSchedule(preserveSnooze = true)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to sync reminder schedule", error)
                actionState.update { it.copy(errorMessage = SCHEDULE_ERROR) }
            }
        }
    }

    fun setEnabled(enabled: Boolean) {
        save { settingsRepository.update { it.copy(isEnabled = enabled) } }
    }

    fun setInterval(minutes: Int) {
        if (minutes !in ReminderDefaults.ALLOWED_INTERVALS) {
            setError(INVALID_INTERVAL_ERROR)
            return
        }
        save { settingsRepository.update { it.copy(intervalMinutes = minutes) } }
    }

    fun setWorkStart(minute: Int) {
        val settings = uiState.value.settings ?: return
        if (minute == settings.workEndMinute) {
            setError(WORK_PERIOD_ERROR)
            return
        }
        save { settingsRepository.update { it.copy(workStartMinute = minute) } }
    }

    fun setWorkEnd(minute: Int) {
        val settings = uiState.value.settings ?: return
        if (minute == settings.workStartMinute) {
            setError(WORK_PERIOD_ERROR)
            return
        }
        save { settingsRepository.update { it.copy(workEndMinute = minute) } }
    }

    fun setQuietEnabled(enabled: Boolean) {
        val quiet = currentQuietPeriod()
        if (enabled && quiet != null && quiet.startMinute == quiet.endMinute) {
            setError(QUIET_PERIOD_ERROR)
            return
        }
        save {
            settingsRepository.updateQuietPeriod { it.copy(isEnabled = enabled) }
        }
    }

    fun setQuietStart(minute: Int) {
        val quiet = currentQuietPeriod()
        if (quiet?.isEnabled == true && minute == quiet.endMinute) {
            setError(QUIET_PERIOD_ERROR)
            return
        }
        save {
            settingsRepository.updateQuietPeriod {
                it.copy(startMinuteOfDay = minute)
            }
        }
    }

    fun setQuietEnd(minute: Int) {
        val quiet = currentQuietPeriod()
        if (quiet?.isEnabled == true && minute == quiet.startMinute) {
            setError(QUIET_PERIOD_ERROR)
            return
        }
        save {
            settingsRepository.updateQuietPeriod {
                it.copy(endMinuteOfDay = minute)
            }
        }
    }

    fun setPauseDuringWalk(enabled: Boolean) {
        save { settingsRepository.update { it.copy(pauseDuringWalk = enabled) } }
    }

    fun dismissMessage() {
        actionState.update { it.copy(errorMessage = null) }
    }

    private fun currentQuietPeriod(): QuietPeriod? =
        uiState.value.settings?.quietPeriods?.firstOrNull()

    private fun setError(message: String) {
        actionState.update { it.copy(errorMessage = message) }
    }

    private fun save(block: suspend () -> Unit) {
        if (actionState.value.isSaving) return

        actionState.update {
            it.copy(isSaving = true, loadFailed = false, errorMessage = null)
        }
        viewModelScope.launch {
            try {
                try {
                    block()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to save reminder settings", error)
                    actionState.update { it.copy(errorMessage = SAVE_ERROR) }
                    return@launch
                }

                try {
                    scheduler.rescheduleAfterSettingsChange()
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e(TAG, "Failed to reschedule reminders", error)
                    actionState.update { it.copy(errorMessage = SCHEDULE_ERROR) }
                }
            } finally {
                actionState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun observeReminderData() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            actionState.update { it.copy(loadFailed = false) }
            try {
                settingsRepository.ensureDefaults()
                combine(
                    settingsRepository.settings,
                    slotRepository.nextSlot
                ) { settings, slot ->
                    settings to slot
                }.collect { (settings, slot) ->
                    loadedSettings.value = settings
                    loadedSlot.value = slot
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to observe reminder settings", error)
                actionState.update { it.copy(loadFailed = true) }
            }
        }
    }

    companion object {
        private const val TAG = "ReminderSettingsVM"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L

        private const val SAVE_ERROR =
            "Не удалось сохранить настройки. Попробуйте ещё раз."
        private const val SCHEDULE_ERROR =
            "Настройки сохранены, но не удалось обновить расписание напоминаний."
        private const val INVALID_INTERVAL_ERROR =
            "Выберите интервал 30, 60, 90 или 120 минут."
        private const val WORK_PERIOD_ERROR =
            "Начало и окончание периода не должны совпадать."
        private const val QUIET_PERIOD_ERROR =
            "Начало и окончание тихих часов не должны совпадать."

        fun factory(
            settingsRepository: ReminderSettingsRepository,
            slotRepository: ReminderSlotRepository,
            scheduler: ReminderScheduler
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                if (modelClass.isAssignableFrom(ReminderSettingsViewModel::class.java)) {
                    return ReminderSettingsViewModel(
                        settingsRepository,
                        slotRepository,
                        scheduler
                    ) as T
                }
                throw IllegalArgumentException(
                    "Unknown ViewModel class: ${modelClass.name}"
                )
            }
        }
    }
}
