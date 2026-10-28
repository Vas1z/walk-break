package ru.vas1z.walkbreak.ui.home

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
import ru.vas1z.walkbreak.data.local.dao.StartWalkResult
import ru.vas1z.walkbreak.data.local.entity.WalkSessionEntity
import ru.vas1z.walkbreak.data.repository.WalkSessionRepository
import ru.vas1z.walkbreak.reminders.ReminderScheduler

/** Действие, которое сейчас выполняется на главном экране. */
enum class WalkAction {
    Start,
    Finish
}

/**
 * Состояние главного экрана.
 *
 * [isLoading] и [loadFailed] относятся к первичной загрузке данных из Room,
 * [pendingAction] и [errorMessage] — к действиям пользователя.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val pendingAction: WalkAction? = null,
    val activeSession: WalkSessionEntity? = null,
    val completedCount: Int = 0,
    val plannedMinutes: Int = WalkSessionEntity.DEFAULT_PLANNED_MINUTES,
    val errorMessage: String? = null
) {
    val isWalking: Boolean get() = activeSession != null
    val isProcessing: Boolean get() = pendingAction != null
}

private data class LoadedWalkData(
    val activeSession: WalkSessionEntity?,
    val completedCount: Int
)

private sealed interface LoadState {
    data object Loading : LoadState
    data class Ready(val data: LoadedWalkData) : LoadState
    data object Failed : LoadState
}

class HomeViewModel(
    private val repository: WalkSessionRepository,
    private val reminderScheduler: ReminderScheduler
) : ViewModel() {

    private data class ActionState(
        val pendingAction: WalkAction? = null,
        val errorMessage: String? = null
    )

    private val actionState = MutableStateFlow(ActionState())
    private val loadState = MutableStateFlow<LoadState>(LoadState.Loading)
    private var loadJob: Job? = null

    val uiState: StateFlow<HomeUiState> = combine(loadState, actionState) { load, action ->
        when (load) {
            LoadState.Loading -> HomeUiState(
                isLoading = true,
                pendingAction = action.pendingAction
            )

            LoadState.Failed -> HomeUiState(
                isLoading = false,
                loadFailed = true,
                pendingAction = action.pendingAction
            )

            is LoadState.Ready -> HomeUiState(
                isLoading = false,
                pendingAction = action.pendingAction,
                activeSession = load.data.activeSession,
                completedCount = load.data.completedCount,
                plannedMinutes = load.data.activeSession?.plannedMinutes
                    ?: WalkSessionEntity.DEFAULT_PLANNED_MINUTES,
                errorMessage = action.errorMessage
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = HomeUiState()
    )

    init {
        observeWalkData()
    }

    /**
     * Запускает новую прогулку. Если активная сессия уже существует,
     * репозиторий не создаст вторую, а UI получит её из Room.
     */
    fun startWalk() {
        val state = uiState.value
        if (state.loadFailed || state.isWalking || state.isProcessing) {
            return
        }
        if (actionState.value.pendingAction != null) {
            return
        }

        actionState.update {
            it.copy(pendingAction = WalkAction.Start, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                when (repository.startWalk(System.currentTimeMillis())) {
                    is StartWalkResult.Started ->
                        syncReminderSchedule(afterStart = true)

                    StartWalkResult.Rejected ->
                        Log.i(TAG, "Walk start rejected: an active session already exists")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to start a walk", error)
                actionState.update { it.copy(errorMessage = START_ERROR) }
            } finally {
                actionState.update { it.copy(pendingAction = null) }
            }
        }
    }

    /** Завершает текущую прогулку текущим временем. */
    fun finishWalk() {
        val session = uiState.value.activeSession ?: return
        if (actionState.value.pendingAction != null) {
            return
        }

        actionState.update {
            it.copy(pendingAction = WalkAction.Finish, errorMessage = null)
        }

        viewModelScope.launch {
            try {
                val finished = repository.finishWalk(
                    sessionId = session.id,
                    finishedAtEpochMillis = System.currentTimeMillis()
                )
                if (finished) {
                    syncReminderSchedule(afterStart = false)
                } else {
                    actionState.update { it.copy(errorMessage = FINISH_ALREADY_DONE) }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to finish a walk", error)
                actionState.update { it.copy(errorMessage = FINISH_ERROR) }
            } finally {
                actionState.update { it.copy(pendingAction = null) }
            }
        }
    }

    fun dismissError() {
        actionState.update { it.copy(errorMessage = null) }
    }

    /** Повторная попытка загрузить данные после ошибки наблюдения за Room. */
    fun retryLoading() {
        actionState.update { it.copy(errorMessage = null) }
        observeWalkData()
    }

    private suspend fun syncReminderSchedule(afterStart: Boolean) {
        try {
            if (afterStart) {
                reminderScheduler.onWalkStarted()
            } else {
                reminderScheduler.onWalkFinished()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.e(TAG, "Failed to update reminder schedule", error)
        }
    }

    private fun observeWalkData() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadState.value = LoadState.Loading
            try {
                combine(
                    repository.activeSession,
                    repository.completedCount
                ) { activeSession, completedCount ->
                    LoadedWalkData(activeSession, completedCount)
                }.collect { data ->
                    loadState.value = LoadState.Ready(data)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Failed to observe walk sessions", error)
                loadState.value = LoadState.Failed
            }
        }
    }

    companion object {
        private const val TAG = "HomeViewModel"
        private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        private const val START_ERROR =
            "Не удалось начать прогулку. Попробуйте ещё раз."
        private const val FINISH_ERROR =
            "Не удалось завершить прогулку. Попробуйте ещё раз."
        private const val FINISH_ALREADY_DONE =
            "Не удалось завершить прогулку: возможно, она уже была завершена."

        fun factory(
            repository: WalkSessionRepository,
            reminderScheduler: ReminderScheduler
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
                        return HomeViewModel(repository, reminderScheduler) as T
                    }
                    throw IllegalArgumentException(
                        "Unknown ViewModel class: ${modelClass.name}"
                    )
                }
            }
    }
}
