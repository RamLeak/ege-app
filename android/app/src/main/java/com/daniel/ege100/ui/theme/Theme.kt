package com.daniel.ege100.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
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

/**
 * Stage 3 polish: только тёмная тема — упрощает первый запуск, светлая
 * добавится в Stage 5 (DESIGN_SPEC.md §8 «Тема (Светлая / Тёмная / По системе)»).
 */
@Composable
fun EgeTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = appleTypography(),
        content = content,
    )
}
