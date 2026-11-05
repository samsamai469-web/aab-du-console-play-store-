package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = SamPrimaryDark,
    onPrimary = DarkTextPrimary,
    primaryContainer = DarkSurfaceVariant,
    onPrimaryContainer = DarkTextPrimary,
    secondary = SamSecondaryDark,
    onSecondary = DarkTextPrimary,
    secondaryContainer = DarkSurfaceVariant,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = SamTertiaryDark,
    onTertiary = DarkTextPrimary,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline
)

private val LightColorScheme = lightColorScheme(
    primary = SamPrimaryLight,
    onPrimary = LightSurface,
    primaryContainer = LightSurfaceVariant,
    onPrimaryContainer = LightTextPrimary,
    secondary = SamSecondaryLight,
    onSecondary = LightSurface,
    secondaryContainer = LightSurfaceVariant,
    onSecondaryContainer = LightTextPrimary,
    tertiary = SamTertiaryLight,
    onTertiary = LightSurface,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline
)

private val ClassicDarkColorScheme = darkColorScheme(
    primary = ClassicPrimaryDark,
    onPrimary = ClassicDarkTextPrimary,
    primaryContainer = ClassicDarkSurfaceVariant,
    onPrimaryContainer = ClassicDarkTextPrimary,
    secondary = ClassicSecondaryDark,
    onSecondary = ClassicDarkTextPrimary,
    tertiary = ClassicTertiaryDark,
    onTertiary = ClassicDarkTextPrimary,
    background = ClassicDarkBackground,
    onBackground = ClassicDarkTextPrimary,
    surface = ClassicDarkSurface,
    onSurface = ClassicDarkTextPrimary,
    surfaceVariant = ClassicDarkSurfaceVariant,
    onSurfaceVariant = ClassicDarkTextSecondary,
    outline = ClassicDarkOutline
)

private val ClassicLightColorScheme = lightColorScheme(
    primary = ClassicPrimaryLight,
    onPrimary = ClassicLightSurface,
    primaryContainer = ClassicLightSurfaceVariant,
    onPrimaryContainer = ClassicLightTextPrimary,
    secondary = ClassicSecondaryLight,
    onSecondary = ClassicLightSurface,
    tertiary = ClassicTertiaryLight,
    onTertiary = ClassicLightSurface,
    background = ClassicLightBackground,
    onBackground = ClassicLightTextPrimary,
    surface = ClassicLightSurface,
    onSurface = ClassicLightTextPrimary,
    surfaceVariant = ClassicLightSurfaceVariant,
    onSurfaceVariant = ClassicLightTextSecondary,
    outline = ClassicLightOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    themeStyle: String = "modern",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeStyle == "classic" -> {
            if (darkTheme) ClassicDarkColorScheme else ClassicLightColorScheme
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = colorScheme.background.toArgb()
                window.navigationBarColor = colorScheme.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    val configuration = LocalConfiguration.current
    val baseDensity = LocalDensity.current

    // Adaptation automatique de la densité et de la police pour tous les téléphones :
    // Garantit qu'un téléphone à petit écran (ex. 320dp–360dp de large ou < 780dp de haut)
    // ou avec un zoom d'écran / grande police activé affiche 100 % des éléments sans déborder.
    val screenWidthDp = configuration.screenWidthDp.toFloat().coerceAtLeast(300f)
    val screenHeightDp = configuration.screenHeightDp.toFloat().coerceAtLeast(520f)
    val isPortrait = screenHeightDp >= screenWidthDp

    val layoutScale = if (isPortrait) {
        val widthRatio = (screenWidthDp / 392f).coerceAtMost(1.0f)
        val heightRatio = (screenHeightDp / 820f).coerceAtMost(1.0f)
        minOf(widthRatio, heightRatio).coerceIn(0.72f, 1.0f)
    } else {
        val widthRatio = (screenWidthDp / 820f).coerceAtMost(1.0f)
        val heightRatio = (screenHeightDp / 392f).coerceAtMost(1.0f)
        minOf(widthRatio, heightRatio).coerceIn(0.72f, 1.0f)
    }

    val adaptedDensity = Density(
        density = baseDensity.density * layoutScale,
        fontScale = 1.0f
    )

    CompositionLocalProvider(LocalDensity provides adaptedDensity) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
