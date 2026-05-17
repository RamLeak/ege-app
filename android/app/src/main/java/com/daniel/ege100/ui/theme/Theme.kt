package com.daniel.ege100.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Цвета из DESIGN_SPEC.md §2. Stage 1: фактически используется только тёмная.
private val IosBlueDark = Color(0xFF0A84FF)
private val IosGreenDark = Color(0xFF30D158)
private val IosRedDark = Color(0xFFFF453A)
private val BgMainDark = Color(0xFF000000)
private val BgCardDark = Color(0xFF1C1C1E)
private val TextPrimaryDark = Color(0xFFFFFFFF)
private val TextSecondaryDark = Color(0x99EBEBF5)

private val IosBlueLight = Color(0xFF007AFF)
private val BgMainLight = Color(0xFFFFFFFF)
private val BgCardLight = Color(0xFFF2F2F7)
private val TextPrimaryLight = Color(0xFF000000)

private val DarkColors = darkColorScheme(
    primary = IosBlueDark,
    onPrimary = TextPrimaryDark,
    secondary = IosGreenDark,
    error = IosRedDark,
    background = BgMainDark,
    onBackground = TextPrimaryDark,
    surface = BgCardDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
)

private val LightColors = lightColorScheme(
    primary = IosBlueLight,
    background = BgMainLight,
    surface = BgCardLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
)

@Composable
fun EgeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
