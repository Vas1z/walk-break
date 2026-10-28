package ru.vas1z.walkbreak.data.local.dao

sealed interface StartWalkResult {
    data class Started(val sessionId: Long) : StartWalkResult
    data object Rejected : StartWalkResult
}
