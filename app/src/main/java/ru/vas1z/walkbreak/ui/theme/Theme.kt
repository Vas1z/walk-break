package ru.vas1z.walkbreak.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F6B4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5F3E2),
    onPrimaryContainer = Color(0xFF0C3020),
    secondary = Color(0xFF506356),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E8D8),
    onSecondaryContainer = Color(0xFF0F1F16),
    background = Color(0xFFF7FBF7),
    onBackground = Color(0xFF181D19),
    surface = Color(0xFFF7FBF7),
    onSurface = Color(0xFF181D19),
    surfaceVariant = Color(0xFFE1E9E2),
    onSurfaceVariant = Color(0xFF414942),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9C1),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9CD6B8),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF174F37),
    onPrimaryContainer = Color(0xFFB9F2D3),
    secondary = Color(0xFFB7CCBC),
    onSecondary = Color(0xFF223528),
    secondaryContainer = Color(0xFF384B3E),
    onSecondaryContainer = Color(0xFFD3E8D8),
    background = Color(0xFF101510),
    onBackground = Color(0xFFE0E4DE),
    surface = Color(0xFF101510),
    onSurface = Color(0xFFE0E4DE),
    surfaceVariant = Color(0xFF414942),
    onSurfaceVariant = Color(0xFFC1C9C1),
    outline = Color(0xFF8B938B),
    outlineVariant = Color(0xFF414942),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6)
)

@Composable
fun WalkBreakTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
