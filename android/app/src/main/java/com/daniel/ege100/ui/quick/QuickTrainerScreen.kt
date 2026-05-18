package com.daniel.ege100.ui.quick

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.catalog.ProblemDetailScreen
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemGreen

/**
 * Phase 3 Stage B part Е — быстрый тренажёр из радара.
 *
 * Получает 10 problemIds через QuickTrainerRoute. Идёт линейно: текущая
 * задача отображается через ProblemDetailScreen (переиспользуем),
 * «Далее →» кнопка под содержимым → следующая. После последней — экран
 * отчёта.
 *
 * NB: P3-B не отслеживает «правильно ли решил внутри QuickTrainer».
 * UserStatsStore уже фиксирует accuracy на уровне ProblemDetailViewModel
 * (часть В+Г), так что radar/predictor обновляются автоматически.
 */
@Composable
fun QuickTrainerScreen(
    problemIds: List<Long>,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onFinish: () -> Unit,
) {
    if (problemIds.isEmpty()) {
        EmptyState(onBack = onBack, contentPadding = contentPadding)
        return
    }

    var currentIndex by rememberSaveable { mutableStateOf(0) }

    if (currentIndex >= problemIds.size) {
        CompletionState(
            total = problemIds.size,
            onAgain = { currentIndex = 0 },
            onHome = onFinish,
            contentPadding = contentPadding,
        )
        return
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Быстрый тренажёр",
                subtitle = "${currentIndex + 1} из ${problemIds.size}",
                onBack = onBack,
            )
        },
        containerColor = Bg,
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            AppleProgressBar(
                progress = (currentIndex + 1).toFloat() / problemIds.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        (fadeIn(animationSpec = tween(280)) +
                            scaleIn(
                                initialScale = 0.96f,
                                animationSpec = spring(
                                    dampingRatio = 0.85f,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                            )) togetherWith
                            (fadeOut(tween(160)) + scaleOut(targetScale = 1.02f))
                    },
                    label = "quick-problem",
                ) { idx ->
                    QuickProblemHost(
                        problemId = problemIds[idx],
                        onAdvance = { currentIndex = idx + 1 },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickProblemHost(
    problemId: Long,
    onAdvance: () -> Unit,
) {
    // Переиспользуем ProblemDetailScreen. typeId / subtypeId не нужны для
    // отображения самой задачи (ViewModel сам прочитает из problem.typeId /
    // problem.subtypeId после загрузки). Передаём -1L / null.
    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f)) {
            ProblemDetailScreen(
                problemId = problemId,
                typeId = -1L,
                subtypeId = null,
                contentPadding = PaddingValues(0.dp),
                onBack = onAdvance,
            )
        }
        PrimaryButton(
            text = "Далее →",
            onClick = onAdvance,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun CompletionState(
    total: Int,
    onAgain: () -> Unit,
    onHome: () -> Unit,
    contentPadding: PaddingValues,
) {
    Scaffold(containerColor = Bg) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding)
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "✓", fontSize = 72.sp, color = SystemGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Готово!",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Прошёл $total ${problemsCountWord(total)} из подвидов-слабых мест.",
                    fontSize = 16.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(40.dp))
                PrimaryButton(text = "Ещё раз", onClick = onAgain)
                Spacer(Modifier.height(12.dp))
                SecondaryButton(
                    text = "На главный",
                    onClick = onHome,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EmptyState(onBack: () -> Unit, contentPadding: PaddingValues) {
    Scaffold(
        topBar = { LargeTitleBar(title = "Быстрый тренажёр", onBack = onBack) },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Нет задач для тренировки",
                color = LabelSecondary,
                fontSize = 16.sp,
            )
        }
    }
}

private fun problemsCountWord(n: Int): String {
    val n100 = n % 100
    val n10 = n % 10
    return when {
        n100 in 11..14 -> "задач"
        n10 == 1 -> "задачу"
        n10 in 2..4 -> "задачи"
        else -> "задач"
    }
}
