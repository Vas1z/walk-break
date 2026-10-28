package ru.vas1z.walkbreak.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlinx.coroutines.delay
import ru.vas1z.walkbreak.ui.reminders.ReminderSummaryUi

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    reminderSummary: ReminderSummaryUi,
    onOpenReminders: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    HomeContent(
        uiState = uiState,
        reminderSummary = reminderSummary,
        onOpenReminders = onOpenReminders,
        onStartWalk = viewModel::startWalk,
        onFinishWalk = viewModel::finishWalk,
        onDismissError = viewModel::dismissError,
        onRetryLoad = viewModel::retryLoading
    )
}

@Composable
private fun HomeContent(
    uiState: HomeUiState,
    reminderSummary: ReminderSummaryUi,
    onOpenReminders: () -> Unit,
    onStartWalk: () -> Unit,
    onFinishWalk: () -> Unit,
    onDismissError: () -> Unit,
    onRetryLoad: () -> Unit
) {
    // Во время завершения продолжаем показывать состояние «прогулка идёт»,
    // пока Room не подтвердит изменение.
    val isWalking = uiState.activeSession != null ||
        uiState.pendingAction == WalkAction.Finish

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (!uiState.isLoading && !uiState.loadFailed) {
                HomeActionBar(
                    isWalking = isWalking,
                    isProcessing = uiState.isProcessing,
                    plannedMinutes = uiState.plannedMinutes,
                    onStartWalk = onStartWalk,
                    onFinishWalk = onFinishWalk
                )
            }
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> LoadingContent(
                modifier = Modifier.padding(innerPadding)
            )

            uiState.loadFailed -> LoadErrorContent(
                modifier = Modifier.padding(innerPadding),
                onRetry = onRetryLoad
            )

            else -> HomeBody(
                modifier = Modifier.padding(innerPadding),
                uiState = uiState,
                isWalking = isWalking,
                reminderSummary = reminderSummary,
                onOpenReminders = onOpenReminders,
                onDismissError = onDismissError
            )
        }
    }
}

@Composable
private fun HomeBody(
    modifier: Modifier,
    uiState: HomeUiState,
    isWalking: Boolean,
    reminderSummary: ReminderSummaryUi,
    onOpenReminders: () -> Unit,
    onDismissError: () -> Unit
) {
    val errorMessage = uiState.errorMessage

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = "Встань и иди",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Короткие перерывы на движение помогают размяться, " +
                "сменить позу и вернуться к делам с более ясной головой.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))
        StatusCard(uiState = uiState, isWalking = isWalking)

        Spacer(Modifier.height(16.dp))
        SummaryCard(uiState = uiState)

        Spacer(Modifier.height(16.dp))
        ReminderSummaryCard(
            summary = reminderSummary,
            onClick = onOpenReminders
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(16.dp))
            ErrorCard(message = errorMessage, onDismiss = onDismissError)
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusCard(
    uiState: HomeUiState,
    isWalking: Boolean
) {
    val containerColor = if (isWalking) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    }
    val contentColor = if (isWalking) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isWalking) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val dotColor = if (isWalking) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }

    val startedAtMillis = uiState.activeSession?.startedAtEpochMillis
    val startedAt = remember(startedAtMillis) {
        startedAtMillis?.let(::formatStartTime)
    }
    val timerNowMillis by produceState(
        initialValue = System.currentTimeMillis(),
        key1 = startedAtMillis,
        key2 = isWalking
    ) {
        val start = startedAtMillis
        if (!isWalking || start == null) {
            return@produceState
        }

        val plannedMillis = uiState.plannedMinutes.coerceAtLeast(0) * 60_000L
        while (true) {
            val now = System.currentTimeMillis()
            value = now
            if (now - start >= plannedMillis) {
                break
            }
            delay(1_000L)
        }
    }
    val timer = startedAtMillis?.let { start ->
        calculateWalkTimer(
            startedAtEpochMillis = start,
            nowEpochMillis = timerNowMillis,
            plannedMinutes = uiState.plannedMinutes
        )
    }
    val description = when {
        isWalking && startedAt != null ->
            "Начата в $startedAt. Завершите прогулку, когда вернётесь."

        isWalking -> "Завершите прогулку, когда вернётесь."

        else -> "Нажмите «Начать прогулку», чтобы сделать перерыв на движение."
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(10.dp)
                    .background(color = dotColor, shape = CircleShape)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isWalking) "Прогулка идёт" else "Прогулка не начата",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryColor
                )
                if (isWalking && timer != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (timer.isOverdue) {
                            "Плановая длительность истекла"
                        } else {
                            "Осталось: ${formatWalkTimer(timer.remainingSeconds)}"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(uiState: HomeUiState) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            SummaryRow(
                label = "Завершено прогулок",
                value = "${uiState.completedCount} ${walksWord(uiState.completedCount)}"
            )
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(14.dp))
            SummaryRow(
                label = "Длительность прогулки",
                value = minutesLabel(uiState.plannedMinutes)
            )
        }
    }
}

@Composable
private fun ReminderSummaryCard(
    summary: ReminderSummaryUi,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Напоминания",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = summary.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "Настроить",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp)
        ) {
            Text(
                text = "Не удалось выполнить действие",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Скрыть")
                }
            }
        }
    }
}

@Composable
private fun HomeActionBar(
    isWalking: Boolean,
    isProcessing: Boolean,
    plannedMinutes: Int,
    onStartWalk: () -> Unit,
    onFinishWalk: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Button(
                onClick = if (isWalking) onFinishWalk else onStartWalk,
                enabled = !isProcessing,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = LocalContentColor.current
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = if (isWalking) "Завершить прогулку" else "Начать прогулку",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (isWalking) {
                    "Завершите прогулку, когда вернётесь"
                } else {
                    "Запланировано: ${minutesLabel(plannedMinutes)}"
                },
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Загружаем данные…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LoadErrorContent(
    modifier: Modifier = Modifier,
    onRetry: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Не удалось загрузить данные",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Данные хранятся на устройстве. Попробуйте загрузить их ещё раз.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRetry) {
                Text("Повторить")
            }
        }
    }
}

private val startTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm")

private fun formatStartTime(epochMillis: Long): String =
    runCatching {
        Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .format(startTimeFormatter)
    }.getOrDefault("—")

private fun walksWord(count: Int): String =
    pluralize(count, "прогулка", "прогулки", "прогулок")

private fun minutesLabel(minutes: Int): String =
    "$minutes ${pluralize(minutes, "минута", "минуты", "минут")}"

private fun pluralize(
    count: Int,
    one: String,
    few: String,
    many: String
): String {
    val value = abs(count)
    val lastTwoDigits = value % 100
    val lastDigit = value % 10
    return when {
        lastTwoDigits in 11..14 -> many
        lastDigit == 1 -> one
        lastDigit in 2..4 -> few
        else -> many
    }
}
