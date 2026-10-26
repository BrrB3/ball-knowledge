package com.ballknowledge.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1F5130),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EBD0),
    onPrimaryContainer = Color(0xFF062012),
    secondary = Color(0xFF52634F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF111F0F),
    tertiary = Color(0xFFB3261E),
    background = Color(0xFFF8FAF6),
    onBackground = Color(0xFF191C19),
    surface = Color(0xFFF8FAF6),
    onSurface = Color(0xFF191C19),
    surfaceVariant = Color(0xFFDDE5DA),
    onSurfaceVariant = Color(0xFF414941),
    surfaceContainer = Color(0xFFECF0E9),
    surfaceContainerHigh = Color(0xFFE6EBE3),
    outline = Color(0xFF717970),
    outlineVariant = Color(0xFFC1C9BF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8FD3A3),
    onPrimary = Color(0xFF003919),
    primaryContainer = Color(0xFF1F5130),
    onPrimaryContainer = Color(0xFFC8EBD0),
    secondary = Color(0xFFB9CCB4),
    onSecondary = Color(0xFF253423),
    secondaryContainer = Color(0xFF3B4B38),
    onSecondaryContainer = Color(0xFFD5E8CF),
    tertiary = Color(0xFFFFB4AB),
    background = Color(0xFF111411),
    onBackground = Color(0xFFE1E3DE),
    surface = Color(0xFF111411),
    onSurface = Color(0xFFE1E3DE),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BF),
    surfaceContainer = Color(0xFF1D201D),
    surfaceContainerHigh = Color(0xFF272B27),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941),
)

@Composable
fun BallKnowledgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content,
    )
}
