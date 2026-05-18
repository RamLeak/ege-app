package com.daniel.ege100.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.daniel.ege100.data.Severity
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed

/**
 * Phase 3 Stage B — цвет сектора радара по severity.
 *
 * GRAY (мало данных) — LabelTertiary, RED — SystemRed, YELLOW — SystemOrange
 * (более насыщенный жёлтый плохо читается на белом), GREEN — SystemGreen.
 */
@Composable
@ReadOnlyComposable
fun severityColor(severity: Severity): Color = when (severity) {
    Severity.GRAY -> LabelTertiary
    Severity.RED -> SystemRed
    Severity.YELLOW -> SystemOrange
    Severity.GREEN -> SystemGreen
}
