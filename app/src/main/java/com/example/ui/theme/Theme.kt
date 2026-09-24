package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF002244),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF38BDF8),
    onSecondary = Color(0xFF082F49),
    tertiary = Color(0xFFFBBF24),
    onTertiary = Color(0xFF451A03),
    background = ProBackground,
    onBackground = Color(0xFFF3F4F6),
    surface = ProSurface,
    onSurface = Color(0xFFF3F4F6),
    surfaceVariant = ProSurfaceVariant,
    onSurfaceVariant = Color(0xFF9CA3AF)
)

private val LightColorScheme = lightColorScheme(
    primary = ProPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = ProSecondaryLight,
    onSecondary = Color.White,
    tertiary = ProTertiaryLight,
    onTertiary = Color.White,
    background = ProBackgroundLight,
    onBackground = Color(0xFF0F172A),
    surface = ProSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = ProSurfaceVariantLight,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
