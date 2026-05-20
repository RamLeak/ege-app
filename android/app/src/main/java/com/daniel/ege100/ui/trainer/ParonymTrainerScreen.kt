package com.daniel.ege100.ui.trainer

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.ParonymItem
import com.daniel.ege100.data.ParonymsRepository
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.ai.ExplanationBottomSheet
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 4 Stage P4-D (Convention #71) — тренажёр паронимов (русский №5).
 *
 * UI: предложение с подсвеченным CAPS-словом + 2 кнопки выбора (CAPS-вариант
 * и пароним-замена). Источник — `assets/paronyms.json` от
 * `parser/scrapers/extract_paronyms.py`.
 */
data class ParonymTrainerUi(
    val items: List<ParonymItem> = emptyList(),
    val position: Int = 0,
    val verdict: TapVerdict = TapVerdict.NONE,
    val selectedAnswer: String? = null,
    val completed: Boolean = false,
    // Phase 4 Stage P4-D7 (Convention #93) — TrainerProgressStore resume.
    val pendingResume: com.daniel.ege100.data.TrainerProgress? = null,
)

class ParonymTrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(ParonymTrainerUi())
    val state: StateFlow<ParonymTrainerUi> = _state.asStateFlow()

    private val trainerId = "rus_paronym"

    init {
        viewModelScope.launch {
            // Phase 4 Stage P4-D7 (Convention #94) — stable order. Без shuffle —
            // иначе сохранённый position указывает на другой пароним при возврате.
            val items = ParonymsRepository.load(getApplication())
                .sortedBy { it.problem_id ?: 0 }

            val ctx = getApplication<Application>()
            val saved = com.daniel.ege100.data.TrainerProgressStore.get(ctx, trainerId)
            val savedValid = saved != null &&
                saved.total == items.size &&
                saved.position in 1 until items.size

            _state.value = ParonymTrainerUi(
                items = items,
                position = if (savedValid) saved!!.position else 0,
                pendingResume = if (savedValid) saved else null,
            )
        }
    }

    fun acceptResume() {
        val cur = _state.value
        val saved = cur.pendingResume ?: return
        _state.value = cur.copy(position = saved.position, pendingResume = null)
    }

    fun acceptStartOver() {
        viewModelScope.launch {
            com.daniel.ege100.data.TrainerProgressStore.clear(getApplication(), trainerId)
        }
        _state.value = _state.value.copy(
            position = 0,
            verdict = TapVerdict.NONE,
            selectedAnswer = null,
            pendingResume = null,
        )
    }

    private fun persistProgress() {
        val cur = _state.value
        if (cur.items.isEmpty()) return
        viewModelScope.launch {
            com.daniel.ege100.data.TrainerProgressStore.save(
                getApplication(),
                trainerId,
                com.daniel.ege100.data.TrainerProgress(
                    position = cur.position,
                    total = cur.items.size,
                    order = "alphabetical",
                    indices = cur.items.indices.toList(),
                ),
            )
        }
    }

    fun answer(picked: String) {
        val s = _state.value
        if (s.verdict != TapVerdict.NONE) return
        val current = s.items.getOrNull(s.position) ?: return
        val isRight = picked.equals(current.correct_word, ignoreCase = true)
        _state.value = s.copy(
            verdict = if (isRight) TapVerdict.CORRECT else TapVerdict.WRONG,
            selectedAnswer = picked,
        )
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            StreakStore.onProblemSolved(ctx)
            UserStatsStore.recordAttempt(ctx, "rus", 5, null, isRight)
            if (isRight) UserStatsStore.incrementTrainerWordsLearned(ctx)
            runCatching {
                UserDataDatabase.get(ctx).attemptLogDao().insert(
                    com.daniel.ege100.data.AttemptLogEntity(
                        problemId = current.problem_id,
                        subject = "rus",
                        typeNumber = 5,
                        subtypeId = null,
                        isCorrect = isRight,
                        durationMs = 0,
                        timestamp = System.currentTimeMillis(),
                        source = "paronym_trainer",
                    ),
                )
            }
            // Phase 5 Stage E2 — автосоздание SRS-карточки на ошибку.
            // Ключ word = "${wrong}/${correct}".lowercase() — тот же что
            // в ExplanationBottomSheet (см. строка 307).
            if (!isRight) {
                runCatching {
                    com.daniel.ege100.srs.SrsRepository.addCardOnMistake(
                        context = ctx,
                        word = "${current.wrong_word}/${current.correct_word}".lowercase(),
                        kind = "paronym",
                        subtype = "rus5",
                    )
                }
            }
        }
    }

    fun next(onCompleted: (Int) -> Unit) {
        val s = _state.value
        val nextPos = s.position + 1
        if (nextPos >= s.items.size) {
            _state.value = s.copy(completed = true)
            viewModelScope.launch {
                com.daniel.ege100.data.TrainerProgressStore.clear(getApplication(), trainerId)
            }
            onCompleted(s.items.size)
            return
        }
        _state.value = s.copy(position = nextPos, verdict = TapVerdict.NONE, selectedAnswer = null)
        persistProgress()
    }
}

@Composable
fun ParonymTrainerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompleted: (Int) -> Unit,
    contentPadding: PaddingValues,
    vm: ParonymTrainerViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    var showExplanation by remember { mutableStateOf(false) }

    // Phase 4 Stage P4-D7 (Convention #93) — ResumeBottomSheet.
    val pending = st.pendingResume
    if (pending != null) {
        com.daniel.ege100.ui.common.ResumeBottomSheet(
            trainerTitle = "Паронимы",
            savedPosition = pending.position,
            total = pending.total,
            onResume = { vm.acceptResume() },
            onStartOver = { vm.acceptStartOver() },
            onDismiss = { vm.acceptStartOver() },
        )
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Паронимы",
                subtitle = if (st.items.isNotEmpty()) "${st.position + 1} из ${st.items.size}" else "Загрузка…",
                onBack = onBack,
            )
        },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
        ) {
            val current = st.items.getOrNull(st.position)
            if (current == null) {
                if (st.completed) {
                    CenteredEmpty("Тренажёр пройден 🎉")
                } else {
                    CenteredEmpty("Загрузка…")
                }
                return@Box
            }
            Column(modifier = Modifier.fillMaxSize()) {
                AppleProgressBar(
                    progress = (st.position + 1).toFloat() / st.items.size.coerceAtLeast(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                )

                Text(
                    text = "Замени выделенное слово на правильный пароним:",
                    color = LabelSecondary,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))

                ParonymSentenceCard(
                    sentence = current.sentence,
                    highlightWord = current.wrong_word,
                )
                Spacer(Modifier.height(24.dp))

                ChoiceRow(
                    optionA = current.wrong_word.lowercase(),
                    optionB = current.correct_word,
                    correct = current.correct_word,
                    selected = st.selectedAnswer,
                    verdict = st.verdict,
                    enabled = st.verdict == TapVerdict.NONE,
                    onPick = { vm.answer(it) },
                )

                Spacer(Modifier.weight(1f))

                if (st.verdict != TapVerdict.NONE) {
                    val verdictText = when (st.verdict) {
                        TapVerdict.CORRECT -> "✓ Правильно!"
                        TapVerdict.WRONG -> "✗ Правильный ответ: ${current.correct_word}"
                        else -> ""
                    }
                    val verdictColor = if (st.verdict == TapVerdict.CORRECT) SystemGreen else SystemRed
                    Text(
                        text = verdictText,
                        color = verdictColor,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "📖 Объяснение",
                        onClick = { showExplanation = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Далее →",
                        onClick = { vm.next(onCompleted) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showExplanation) {
        val current = st.items.getOrNull(st.position)
        if (current != null) {
            ExplanationBottomSheet(
                word = "${current.wrong_word}/${current.correct_word}".lowercase(),
                kind = "paronym",
                fallbackContext = "Пара паронимов: «${current.wrong_word}» и «${current.correct_word}». " +
                    "В предложении: ${current.sentence}",
                onDismiss = { showExplanation = false },
                onOpenSettings = {
                    showExplanation = false
                    onOpenSettings()
                },
            )
        }
    }
}

@Composable
private fun ParonymSentenceCard(sentence: String, highlightWord: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BgElevated)
            .padding(16.dp),
    ) {
        val parts = sentence.split(" ")
        Text(
            text = buildAnnotatedString(parts, highlightWord),
            color = Label,
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun buildAnnotatedString(parts: List<String>, highlight: String): androidx.compose.ui.text.AnnotatedString {
    val target = highlight.uppercase()
    val builder = androidx.compose.ui.text.AnnotatedString.Builder()
    parts.forEachIndexed { i, p ->
        val isHL = p.contains(target)
        if (isHL) {
            builder.pushStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = SystemBlue,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            builder.append(p)
            builder.pop()
        } else {
            builder.append(p)
        }
        if (i != parts.lastIndex) builder.append(" ")
    }
    return builder.toAnnotatedString()
}

@Composable
private fun ChoiceRow(
    optionA: String,
    optionB: String,
    correct: String,
    selected: String?,
    verdict: TapVerdict,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ChoiceButton(optionA, isCorrect = optionA == correct.lowercase(), selected = selected?.lowercase() == optionA, verdict = verdict, enabled = enabled, onClick = { onPick(optionA) })
        ChoiceButton(optionB, isCorrect = optionB == correct, selected = selected == optionB, verdict = verdict, enabled = enabled, onClick = { onPick(optionB) })
    }
}

@Composable
private fun ChoiceButton(
    text: String,
    isCorrect: Boolean,
    selected: Boolean,
    verdict: TapVerdict,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val (bg, border) = when {
        verdict == TapVerdict.CORRECT && selected -> SystemGreenTint to SystemGreen
        verdict == TapVerdict.WRONG && selected -> SystemRedTint to SystemRed
        verdict == TapVerdict.WRONG && isCorrect -> SystemGreenTint to SystemGreen
        else -> BgElevated to Separator
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .then(
                if (enabled) Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CenteredEmpty(s: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(s, color = LabelSecondary)
    }
}
