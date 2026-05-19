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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.CollocationItem
import com.daniel.ege100.data.CollocationsRepository
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.ai.ExplanationBottomSheet
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.Separator
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 4 Stage P4-D5 (Convention #83) — тренажёр словосочетаний для №7 ЕГЭ.
 *
 * Двухшаговая логика:
 *   ШАГ 1 — пользователь видит 5 словосочетаний, тапает то, в котором ошибка.
 *   ШАГ 2 — после правильного выбора появляется IosTextField, нужно ввести
 *           правильную форму слова. По «Проверить» — финальный verdict.
 *
 * Что считается атомарным attempt'ом (для UserStatsStore + AttemptLog):
 *   - Ошибся на ШАГ 1 (выбрал не то словосочетание) → 1 attempt, isCorrect=false.
 *   - Правильно ШАГ 1 + правильно ШАГ 2 → 1 attempt, isCorrect=true.
 *   - Правильно ШАГ 1 + ошибся ШАГ 2 (не та форма) → 1 attempt, isCorrect=false.
 *
 * `correctAnswers: List<String>` — массив допустимых вариантов (#85 tolerant
 * input checking, нормализация trim + lowercase + ё→е).
 */

enum class CollocationStep {
    SelectPhrase, InputCorrection, Done,
}

data class CollocationTrainerUi(
    val items: List<CollocationItem> = emptyList(),
    val position: Int = 0,
    val selectedIndex: Int? = null,
    val userInput: String = "",
    val step: CollocationStep = CollocationStep.SelectPhrase,
    val verdict: TapVerdict = TapVerdict.NONE,
    val completed: Boolean = false,
)

class WordCollocationTrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(CollocationTrainerUi())
    val state: StateFlow<CollocationTrainerUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val items = CollocationsRepository.load(getApplication())
                .filter { it.items.size == 5 && it.correct_answers.isNotEmpty() }
                .shuffled()
            _state.value = CollocationTrainerUi(items = items)
        }
    }

    fun pickPhrase(index: Int) {
        val s = _state.value
        if (s.step != CollocationStep.SelectPhrase) return
        val current = s.items.getOrNull(s.position) ?: return
        if (index == current.wrong_index) {
            // Правильное словосочетание — переходим к вводу.
            _state.value = s.copy(
                selectedIndex = index,
                step = CollocationStep.InputCorrection,
            )
        } else {
            // Ошибочный выбор — фиксируем verdict сразу.
            _state.value = s.copy(
                selectedIndex = index,
                step = CollocationStep.Done,
                verdict = TapVerdict.WRONG,
            )
            recordAttempt(current, isCorrect = false)
        }
    }

    fun updateInput(value: String) {
        val s = _state.value
        if (s.step != CollocationStep.InputCorrection) return
        // Только русские буквы + пробел (на случай двух слов).
        val filtered = value.filter { it.isLetter() || it == ' ' || it == '-' }
        _state.value = s.copy(userInput = filtered)
    }

    fun checkInput() {
        val s = _state.value
        if (s.step != CollocationStep.InputCorrection) return
        val current = s.items.getOrNull(s.position) ?: return
        val input = normalize(s.userInput)
        if (input.isBlank()) return
        val isCorrect = current.correct_answers.any { normalize(it) == input }
        _state.value = s.copy(
            step = CollocationStep.Done,
            verdict = if (isCorrect) TapVerdict.CORRECT else TapVerdict.WRONG,
        )
        recordAttempt(current, isCorrect = isCorrect)
    }

    fun next(onCompleted: (Int) -> Unit) {
        val s = _state.value
        val n = s.position + 1
        if (n >= s.items.size) {
            _state.value = s.copy(completed = true)
            onCompleted(s.items.size)
            return
        }
        _state.value = s.copy(
            position = n,
            selectedIndex = null,
            userInput = "",
            step = CollocationStep.SelectPhrase,
            verdict = TapVerdict.NONE,
        )
    }

    private fun recordAttempt(current: CollocationItem, isCorrect: Boolean) {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            StreakStore.onProblemSolved(ctx)
            UserStatsStore.recordAttempt(ctx, "rus", 7, null, isCorrect)
            if (isCorrect) UserStatsStore.incrementTrainerWordsLearned(ctx)
            runCatching {
                UserDataDatabase.get(ctx).attemptLogDao().insert(
                    com.daniel.ege100.data.AttemptLogEntity(
                        problemId = current.problem_id,
                        subject = "rus",
                        typeNumber = 7,
                        subtypeId = null,
                        isCorrect = isCorrect,
                        durationMs = 0,
                        timestamp = System.currentTimeMillis(),
                        source = "collocation_trainer",
                    ),
                )
            }
        }
    }

    private fun normalize(s: String): String =
        s.trim().lowercase().replace('ё', 'е').replace(Regex("\\s+"), " ")
}

@Composable
fun WordCollocationTrainerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompleted: (Int) -> Unit,
    contentPadding: PaddingValues,
    vm: WordCollocationTrainerViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    var showExplanation by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    // Auto-focus поля ввода когда переходим в InputCorrection.
    LaunchedEffect(st.step, st.position) {
        if (st.step == CollocationStep.InputCorrection) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Словосочетания",
                subtitle = if (st.items.isNotEmpty()) "${st.position + 1} из ${st.items.size}"
                    else if (st.completed) "Готово 🎉" else "Загрузка…",
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
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (st.completed) "Тренажёр пройден 🎉" else "Загрузка…",
                        color = LabelSecondary,
                    )
                }
                return@Box
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                AppleProgressBar(
                    progress = (st.position + 1).toFloat() / st.items.size.coerceAtLeast(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                )

                Text(
                    text = "В одном из словосочетаний допущена ошибка. " +
                        "Найди его и напиши правильный вариант:",
                    color = LabelSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(16.dp))

                // ШАГ 1 — 5 словосочетаний.
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    current.items.forEachIndexed { index, phrase ->
                        CollocationCard(
                            phrase = phrase,
                            isTarget = (index == current.wrong_index),
                            isSelected = (st.selectedIndex == index),
                            verdict = st.verdict,
                            step = st.step,
                            onClick = { vm.pickPhrase(index) },
                        )
                    }
                }

                // ШАГ 2 — поле ввода. Появляется только если выбрано правильное.
                if (st.step == CollocationStep.InputCorrection) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = "Напиши правильную форму слова:",
                        color = Label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(10.dp))
                    IosTextField(
                        value = st.userInput,
                        onValueChange = vm::updateInput,
                        placeholder = "например: ${current.correct_answers.first()}",
                        modifier = Modifier.focusRequester(focusRequester),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Text,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                            imeAction = ImeAction.Done,
                        ),
                    )
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton(
                        text = "Проверить",
                        onClick = vm::checkInput,
                        enabled = st.userInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // После Verdict — фидбек + Объяснение + Далее.
                if (st.step == CollocationStep.Done) {
                    Spacer(Modifier.height(20.dp))
                    val correctText = current.correct_answers.joinToString(" или ")
                    val (text, color) = when (st.verdict) {
                        TapVerdict.CORRECT -> "✓ Верно!" to SystemGreen
                        TapVerdict.WRONG -> "✗ Правильный ответ: $correctText" to SystemRed
                        else -> "" to LabelSecondary
                    }
                    Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showExplanation) {
        val current = st.items.getOrNull(st.position)
        if (current != null) {
            val correctText = current.correct_answers.joinToString(" / ")
            ExplanationBottomSheet(
                word = current.wrong_word_in_phrase,
                kind = "collocation",
                fallbackContext = "Ошибочное словосочетание: «${current.items[current.wrong_index]}». " +
                    "Правильно: «$correctText». Объясни почему именно так — какое правило русского " +
                    "языка применяется (склонение числительных, образование формы повелительного " +
                    "наклонения, степени сравнения и т.п.).",
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
private fun CollocationCard(
    phrase: String,
    isTarget: Boolean,
    isSelected: Boolean,
    verdict: TapVerdict,
    step: CollocationStep,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    // Цвета по состоянию (Spec А4).
    val (bg, border) = when {
        // После verdict'а — подсветка target и selected.
        step == CollocationStep.Done && isTarget -> SystemGreenTint to SystemGreen
        step == CollocationStep.Done && isSelected && !isTarget -> SystemRedTint to SystemRed
        // ШАГ 2 — выбранное (правильное) словосочетание подсвечено голубым.
        step == CollocationStep.InputCorrection && isSelected -> SystemBlueTint to SystemBlue
        // Idle.
        else -> BgElevated to Separator
    }

    // Клики разрешены только на ШАГ 1.
    val enabled = step == CollocationStep.SelectPhrase

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .then(
                if (enabled) Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                } else Modifier,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "•",
            color = LabelTertiary,
            fontSize = 18.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            text = phrase,
            color = Label,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
        )
        // ✓ или ✗ справа после verdict'а.
        if (step == CollocationStep.Done) {
            when {
                isTarget && verdict == TapVerdict.CORRECT -> {
                    Spacer(Modifier.size(8.dp))
                    Text("✓", color = SystemGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                isTarget && verdict == TapVerdict.WRONG -> {
                    Spacer(Modifier.size(8.dp))
                    Text("✓", color = SystemGreen, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                isSelected && !isTarget -> {
                    Spacer(Modifier.size(8.dp))
                    Text("✗", color = SystemRed, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
