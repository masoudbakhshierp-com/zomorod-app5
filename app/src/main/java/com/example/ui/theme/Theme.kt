package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

private val DarkColorScheme = darkColorScheme(
    primary = CleanBluePrimary,
    onPrimary = CleanBlueOnPrimary,
    primaryContainer = CleanBlueContainer,
    onPrimaryContainer = CleanBlueOnContainer,
    secondary = CleanTealAccent,
    onSecondary = CleanLightSurface,
    background = CleanDarkBackground,
    onBackground = CleanDarkOnSurface,
    surface = CleanDarkSurface,
    onSurface = CleanDarkOnSurface,
    surfaceVariant = CleanDarkSurfaceVariant,
    onSurfaceVariant = CleanDarkOnSurface
)

private val LightColorScheme = lightColorScheme(
    primary = CleanBluePrimary,
    onPrimary = CleanBlueOnPrimary,
    primaryContainer = CleanBlueContainer,
    onPrimaryContainer = CleanBlueOnContainer,
    secondary = CleanTealAccent,
    onSecondary = CleanLightSurface,
    background = CleanLightBackground,
    onBackground = CleanLightOnSurface,
    surface = CleanLightSurface,
    onSurface = CleanLightOnSurface,
    surfaceVariant = CleanLightSurfaceVariant,
    onSurfaceVariant = CleanLightOnSurface
)

@Composable
fun ZomorrodDriverTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val context = LocalContext.current

    SideEffect {
        if (context is Activity) {
            context.window.statusBarColor = colorScheme.primary.toArgb()
        }
    }

    // Force RTL direction for Persian Driver App
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
