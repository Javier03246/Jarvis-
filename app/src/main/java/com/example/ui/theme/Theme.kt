package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val JarvezColorScheme = darkColorScheme(
    primary = JarvezCyan,
    onPrimary = Color(0xFF030712),
    primaryContainer = JarvezSurfaceVariant,
    onPrimaryContainer = JarvezCyan,
    secondary = JarvezBlue,
    onSecondary = Color.White,
    tertiary = JarvezAmber,
    onTertiary = Color.Black,
    background = JarvezBg,
    onBackground = JarvezText,
    surface = JarvezSurface,
    onSurface = JarvezText,
    surfaceVariant = JarvezSurfaceVariant,
    onSurfaceVariant = JarvezSubtext,
    error = JarvezCrimson,
    onError = Color.White
)

@Composable
fun JarvezTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvezColorScheme,
        typography = Typography,
        content = content
    )
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    JarvezTheme(content = content)
}
