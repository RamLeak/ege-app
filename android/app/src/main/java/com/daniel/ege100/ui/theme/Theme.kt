package com.daniel.ege100.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

/**
 * Stage 5 part Ж — Material 3 ColorScheme собирается из dynamic-палитры.
 *
 * Цвета `SystemBlue`/`Label`/... берутся через composable getters в Color.kt,
 * которые сами читают `isSystemInDarkTheme()`. Здесь мы дополнительно
 * выбираем `darkColorScheme(...)` vs `lightColorScheme(...)` для интеграции
 * с Material3 (TextField cursor, TopAppBar и т.п.).
 */
@Composable
fun EgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) {
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
