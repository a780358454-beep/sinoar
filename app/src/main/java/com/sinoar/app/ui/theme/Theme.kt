package com.sinoar.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Tactical = darkColorScheme(
    primary = Color(0xFF9BD14A),
    onPrimary = Color(0xFF10140A),
    primaryContainer = Color(0xFF2A3A1C),
    onPrimaryContainer = Color(0xFFD6F0A8),
    background = Color(0xFF0B0F0C),
    onBackground = Color(0xFFE6EBDD),
    surface = Color(0xFF121812),
    onSurface = Color(0xFFE6EBDD),
    surfaceVariant = Color(0xFF1B231B),
    onSurfaceVariant = Color(0xFFAEB8A5),
    outline = Color(0xFF3D4A3A),
    error = Color(0xFFFF6B5E)
)

@Composable
fun SinoarTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Tactical,
        content = content
    )
}
