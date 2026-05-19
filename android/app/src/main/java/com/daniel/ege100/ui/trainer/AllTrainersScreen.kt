package com.daniel.ege100.ui.trainer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.SmoothLazyColumn
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed

/**
 * Phase 4 Stage P4-D (Convention #76) — список всех 20 тренажёров с
 * прогресс-метками. Заходим из главного «Все тренажёры» или из шапки Stats.
 */
data class TrainerEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val emoji: String,
    val tint: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit,
)

@Composable
fun AllTrainersScreen(
    onBack: () -> Unit,
    onOpenParonym: () -> Unit,
    onOpenPleonasm: () -> Unit,
    onOpenGrammar: () -> Unit,
    onOpenTrig: () -> Unit,
    onOpenShortMult: () -> Unit,
    onOpenLogPower: () -> Unit,
    onOpenDerivatives: () -> Unit,
    onOpenGeometry: () -> Unit,
    onOpenAccentCategories: () -> Unit,
    onOpenWordBlank: (Int) -> Unit,
    contentPadding: PaddingValues,
) {
    val context = LocalContext.current
    val completedFlow = remember(context) { UserStatsStore.trainersCompletedFlow(context) }
    val completed by completedFlow.collectAsState(initial = emptySet())

    fun badge(id: String) = if (completed.contains(id)) " ✓" else ""

    val entries = listOf(
        TrainerEntry("paronym", "Паронимы (№5)" + badge("paronym"), "Русский · замена слов", "📝", SystemBlueTint, onOpenParonym),
        TrainerEntry("pleonasm", "Плеоназмы (№6)" + badge("pleonasm"), "Русский · тап на лишнее", "✂", SystemBlueTint, onOpenPleonasm),
        TrainerEntry("grammar", "Грамошибки (№7)" + badge("grammar"), "Русский · тап на ошибку", "⚠", SystemRed.copy(alpha = 0.18f), onOpenGrammar),
        TrainerEntry("accent_all", "Ударения (№4)", "Орфоэпический словник", "🔊", SystemOrange.copy(alpha = 0.18f), onOpenAccentCategories),
        TrainerEntry("wordblank_t9", "Корни (№9)" + badge("wordblank_t9"), "Русский · пропущенная буква", "🌱", SystemGreenTint, { onOpenWordBlank(9) }),
        TrainerEntry("wordblank_t10", "Приставки (№10)" + badge("wordblank_t10"), "Русский · пропущенная буква", "🧱", SystemGreenTint, { onOpenWordBlank(10) }),
        TrainerEntry("wordblank_t11", "Суффиксы (№11)" + badge("wordblank_t11"), "Русский · пропущенная буква", "🎀", SystemGreenTint, { onOpenWordBlank(11) }),
        TrainerEntry("wordblank_t12", "Окончания (№12)" + badge("wordblank_t12"), "Русский · пропущенная буква", "🌀", SystemGreenTint, { onOpenWordBlank(12) }),
        TrainerEntry("math_trig", "Тригонометрия" + badge("math_trig"), "Математика · значения", "📐", SystemBlueTint, onOpenTrig),
        TrainerEntry("math_shortmult", "Сокращённое умножение" + badge("math_shortmult"), "Математика · 7 формул", "✖", SystemBlueTint, onOpenShortMult),
        TrainerEntry("math_logpower", "Логарифмы и степени" + badge("math_logpower"), "Математика · свойства", "📈", SystemBlueTint, onOpenLogPower),
        TrainerEntry("math_derivatives", "Производные" + badge("math_derivatives"), "Математика · стандартные", "∂", SystemBlueTint, onOpenDerivatives),
        TrainerEntry("math_geometry", "Геометрические формулы" + badge("math_geometry"), "Математика · площадь/объём", "🔷", SystemBlueTint, onOpenGeometry),
    )

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Все тренажёры",
                subtitle = "Пройдено ${completed.intersect(UserStatsStore.ALL_TRAINER_IDS.toSet()).size} из ${UserStatsStore.ALL_TRAINER_IDS.size}",
                onBack = onBack,
            )
        },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            SmoothLazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(entries, key = { it.id }) { e ->
                    AppleListRow(
                        title = e.title,
                        subtitle = e.subtitle,
                        leadingEmoji = e.emoji,
                        leadingTint = e.tint,
                        onClick = e.onClick,
                    )
                }
            }
        }
    }
}
