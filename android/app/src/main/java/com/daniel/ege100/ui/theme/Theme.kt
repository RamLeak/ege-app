package com.daniel.ege100.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.daniel.ege100.data.ThemeMode

/**
 * Phase 3 Stage A part Г — EgeTheme поддерживает ручной режим темы.
 *
 * `themeMode`:
 *   - AUTO   — следовать системе (isSystemInDarkTheme()).
 *   - DARK   — принудительно тёмная.
 *   - LIGHT  — принудительно светлая.
 *
 * Реализовано через `LocalDarkOverride` (CompositionLocal) — все динамические
 * цвета в Color.kt читают сначала override, потом fallback на системную.
 * Изменение `themeMode` в DataStore → перекомпозиция MainActivity →
 * новый EgeTheme с новым override → весь UI перекрашивается мгновенно.
 */
@Composable
fun EgeTheme(
    themeMode: ThemeMode = ThemeMode.AUTO,
    content: @Composable () -> Unit,
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.AUTO -> systemDark
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    CompositionLocalProvider(LocalDarkOverride provides useDark) {
        val colorScheme = if (useDark) {
            darkColorScheme(
                primary = SystemBlue,
                onPrimary = Label,
                secondary = SystemGreen,
                onSecondary = Label,
                error = SystemRed,
                onError = Label,
                background = Bg,
                onBackground = Label,
                surface = BgElevated,
                onSurface = Label,
                surfaceVariant = BgElevated2,
                onSurfaceVariant = LabelSecondary,
                outline = Separator,
            )
        } else {
            lightColorScheme(
                primary = SystemBlue,
                onPrimary = Label,
                secondary = SystemGreen,
                onSecondary = Label,
                error = SystemRed,
                onError = Label,
                background = Bg,
                onBackground = Label,
                surface = BgElevated,
                onSurface = Label,
                surfaceVariant = BgElevated2,
                onSurfaceVariant = LabelSecondary,
                outline = Separator,
            )
        }

        MaterialTheme(
            colorScheme = colorScheme,
            typography = appleTypography(),
            content = content,
        )
    }
}
