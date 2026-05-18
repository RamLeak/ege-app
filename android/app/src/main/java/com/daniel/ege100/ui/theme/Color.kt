package com.daniel.ege100.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iOS-style палитра (DESIGN_SPEC.md §2 + STAGE_3_POLISH §Б1).
 *
 * Stage 3 polish: только тёмная тема, контраст «двойной фон» — основной #000
 * для большой поверхности, #1C1C1E для карточек, #2C2C2E для мелких
 * элементов (TextField, chips). Контраст 11% между Bg и BgElevated достаточен
 * глазу при чёрном фоне, но мелкие интерактивные элементы поднимаются ещё
 * на один уровень.
 */

val Bg = Color(0xFF000000)
val BgElevated = Color(0xFF1C1C1E)
val BgElevated2 = Color(0xFF2C2C2E)
val Separator = Color(0x14FFFFFF)
val SeparatorHairline = Color(0x29EBEBF5) // более яркий вариант для bottom-bar separator

val Label = Color(0xFFFFFFFF)
val LabelSecondary = Color(0x99EBEBF5) // 60%
val LabelTertiary = Color(0x4DEBEBF5)  // 30%

val SystemBlue = Color(0xFF0A84FF)
val SystemGreen = Color(0xFF30D158)
val SystemRed = Color(0xFFFF453A)
val SystemOrange = Color(0xFFFF9F0A)
val SystemYellow = Color(0xFFFFD60A)

// Tinted backgrounds for chips / status banners
val SystemBlueTint = Color(0x1F0A84FF)    // alpha .12
val SystemBlueTintWeak = Color(0x140A84FF) // alpha .08
val SystemGreenTint = Color(0x2630D158)    // alpha .15
val SystemRedTint = Color(0x26FF453A)
