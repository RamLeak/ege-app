package com.daniel.ege100.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * iOS-style палитра (DESIGN_SPEC.md §2).
 *
 * Stage 5 part Ж: палитра стала динамической — цвета зависят от системной
 * темы (`isSystemInDarkTheme()`). Это давало автоматическое переключение
 * тёмной/светлой темы без перестройки UI-компонентов.
 *
 * Phase 3 Stage A part Г: добавляем ручной режим (ThemeMode AUTO/DARK/LIGHT).
 * EgeTheme устанавливает `LocalDarkOverride`, и все getters читают сначала
 * этот override, потом fallback на `isSystemInDarkTheme()`. Это позволяет
 * пользователю принудительно зафиксировать тёмную/светлую тему независимо
 * от системной — и UI перекрашивается мгновенно.
 */

/**
 * Override от EgeTheme. null = «следовать системе», true = тёмная, false = светлая.
 */
val LocalDarkOverride = staticCompositionLocalOf<Boolean?> { null }

/**
 * Текущая активная тема. Читается из override, fallback — система.
 */
@Composable
@ReadOnlyComposable
private fun isDark(): Boolean = LocalDarkOverride.current ?: isSystemInDarkTheme()

// ----------- Тёмная палитра -----------
private val DarkBg = Color(0xFF000000)
private val DarkBgElevated = Color(0xFF1C1C1E)
private val DarkBgElevated2 = Color(0xFF2C2C2E)
private val DarkSeparator = Color(0x14FFFFFF)
private val DarkSeparatorHairline = Color(0x29EBEBF5)
private val DarkLabel = Color(0xFFFFFFFF)
private val DarkLabelSecondary = Color(0x99EBEBF5)
private val DarkLabelTertiary = Color(0x4DEBEBF5)
private val DarkSystemBlue = Color(0xFF0A84FF)
private val DarkSystemGreen = Color(0xFF30D158)
private val DarkSystemRed = Color(0xFFFF453A)
private val DarkSystemOrange = Color(0xFFFF9F0A)
private val DarkSystemYellow = Color(0xFFFFD60A)
private val DarkSystemBlueTint = Color(0x1F0A84FF)
private val DarkSystemBlueTintWeak = Color(0x140A84FF)
private val DarkSystemGreenTint = Color(0x2630D158)
private val DarkSystemRedTint = Color(0x26FF453A)

// ----------- Светлая палитра (iOS) -----------
private val LightBg = Color(0xFFFFFFFF)
private val LightBgElevated = Color(0xFFF2F2F7)
private val LightBgElevated2 = Color(0xFFE5E5EA)
private val LightSeparator = Color(0x143C3C43)
private val LightSeparatorHairline = Color(0x4D3C3C43)
private val LightLabel = Color(0xFF000000)
private val LightLabelSecondary = Color(0x993C3C43)
private val LightLabelTertiary = Color(0x4D3C3C43)
private val LightSystemBlue = Color(0xFF0066CC)  // Phase 3 Stage FINAL part Е — темнее для контраста на белом
private val LightSystemGreen = Color(0xFF34C759)
private val LightSystemRed = Color(0xFFFF3B30)
private val LightSystemOrange = Color(0xFFFF9500)
private val LightSystemYellow = Color(0xFFFFCC00)
private val LightSystemBlueTint = Color(0x1F007AFF)
private val LightSystemBlueTintWeak = Color(0x14007AFF)
private val LightSystemGreenTint = Color(0x2634C759)
private val LightSystemRedTint = Color(0x26FF3B30)

// ----------- Динамические свойства -----------
val Bg: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkBg else LightBg

val BgElevated: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkBgElevated else LightBgElevated

val BgElevated2: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkBgElevated2 else LightBgElevated2

val Separator: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSeparator else LightSeparator

val SeparatorHairline: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSeparatorHairline else LightSeparatorHairline

val Label: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkLabel else LightLabel

val LabelSecondary: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkLabelSecondary else LightLabelSecondary

val LabelTertiary: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkLabelTertiary else LightLabelTertiary

val SystemBlue: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemBlue else LightSystemBlue

val SystemGreen: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemGreen else LightSystemGreen

val SystemRed: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemRed else LightSystemRed

val SystemOrange: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemOrange else LightSystemOrange

val SystemYellow: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemYellow else LightSystemYellow

val SystemBlueTint: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemBlueTint else LightSystemBlueTint

val SystemBlueTintWeak: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemBlueTintWeak else LightSystemBlueTintWeak

val SystemGreenTint: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemGreenTint else LightSystemGreenTint

val SystemRedTint: Color
    @Composable @ReadOnlyComposable
    get() = if (isDark()) DarkSystemRedTint else LightSystemRedTint

