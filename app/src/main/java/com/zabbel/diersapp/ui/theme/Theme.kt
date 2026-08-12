package com.zabbel.diersapp.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = RS_LightBlue,
    onPrimary = Color.White, // Weiß auf Blau
    secondary = RS_Gray,
    onSecondary = RS_LightGray, // Dein gewünschtes Hellgrau auf dem grauen Button-Hintergrund
    tertiary = Color(0xFF80CBC4),
    background = BackgroundDark,
    surface = SurfaceDark,
    error = Color(0xFFD32F2F),
    onError = Color.White,
    primaryContainer = Color(0xFF2C2C2C),
    onPrimaryContainer = SurfaceLight,
    secondaryContainer = RS_Gray,
    onSecondaryContainer = RS_LightGray,
    onBackground = SurfaceLight,
    onSurface = SurfaceLight
)

private val LightColorScheme = lightColorScheme(
    primary = RS_Blue,
    onPrimary = Color.White,
    secondary = RS_Gray,
    onSecondary = Color.White,
    tertiary = Color(0xFF00796B),
    background = Color(0xFFF5F5F5),
    surface = SurfaceLight,
    error = Color(0xFFB00020),
    onError = Color.White,
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = RS_Blue,
    onBackground = BackgroundDark,
    onSurface = BackgroundDark
)

@Composable
fun DieRSAppTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
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
