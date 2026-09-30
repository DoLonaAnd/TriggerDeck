package net.dolonaand.apk.triggerdeck.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondaryContainer = Color(0xFFF5E6E4),
    background = Color(0xFFFCF8F8),
    surface = Color(0xFFFCF8F8),
    surfaceContainer = Color(0xFFF4EEEE),
    surfaceContainerHigh = Color(0xFFEFE8E8),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF4A1F1C),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondaryContainer = Color(0xFF3B2E2D),
    background = Color(0xFF121010),
    surface = Color(0xFF121010),
    surfaceContainer = Color(0xFF1E1B1B),
    surfaceContainerHigh = Color(0xFF292525),
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors, content = content)
}

val OkColor = Color(0xFF2E7D32)
val NgColor = Color(0xFFC62828)
