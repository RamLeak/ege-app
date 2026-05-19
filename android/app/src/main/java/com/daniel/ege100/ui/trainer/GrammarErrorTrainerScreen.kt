package com.daniel.ege100.ui.trainer

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.GrammarErrorItem
import com.daniel.ege100.data.GrammarErrorsRepository
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.ai.ExplanationBottomSheet
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 4 Stage P4-D4 (Convention #79) — тренажёр грамошибок №7, переделан с
 * tap-на-слово на multi-choice. Карточка `wrong_sentence` (красная) + 4 варианта
 * исправлений A/B/C/D (через `SentenceChoiceTrainer`), пользователь выбирает один.
 *
 * Источник — `parser/data/grammar_errors.json` от `build_grammar_v2.py` (drafts) +
 * inline-генерация options через Opus 4.7. Минимум 30 задач с `options.size == 4`,
 * ровно одна `is_correct = true`.
 */
data class GrammarErrorTrainerUi(
    val items: List<GrammarErrorItem> = emptyList(),
    val position: Int = 0,
    val verdict: TapVerdict = TapVerdict.NONE,
    val selectedIndex: Int? = null,
    val completed: Boolean = false,
)

class GrammarErrorTrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(GrammarErrorTrainerUi())
    val state: StateFlow<GrammarErrorTrainerUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val items = GrammarErrorsRepository.load(getApplication())
                .filter { it.options.size == 4 && it.options.count { o -> o.is_correct } == 1 }
                .shuffled()
            _state.value = GrammarErrorTrainerUi(items = items)
        }
    }

    fun pick(index: Int) {
        val s = _state.value
        if (s.verdict != TapVerdict.NONE) return
        val current = s.items.getOrNull(s.position) ?: return
        val isRight = current.options.getOrNull(index)?.is_correct == true
        _state.value = s.copy(
            verdict = if (isRight) TapVerdict.CORRECT else TapVerdict.WRONG,
            selectedIndex = index,
        )
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            StreakStore.onProblemSolved(ctx)
            UserStatsStore.recordAttempt(ctx, "rus", 7, null, isRight)
            if (isRight) UserStatsStore.incrementTrainerWordsLearned(ctx)
            runCatching {
                UserDataDatabase.get(ctx).attemptLogDao().insert(
                    com.daniel.ege100.data.AttemptLogEntity(
                        problemId = current.problem_id,
                        subject = "rus",
                        typeNumber = 7,
                        subtypeId = null,
                        isCorrect = isRight,
                        durationMs = 0,
                        timestamp = System.currentTimeMillis(),
                        source = "grammar_trainer",
                    ),
                )
            }
        }
    }

    fun next(onCompleted: (Int) -> Unit) {
        val s = _state.value
        val n = s.position + 1
        if (n >= s.items.size) {
            _state.value = s.copy(completed = true)
            onCompleted(s.items.size)
            return
        }
        _state.value = s.copy(position = n, verdict = TapVerdict.NONE, selectedIndex = null)
    }
}

@Composable
fun GrammarErrorTrainerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompleted: (Int) -> Unit,
    contentPadding: PaddingValues,
    vm: GrammarErrorTrainerViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    var showExplanation by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Грамошибки",
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

                val options = current.options.map { SentenceChoice(it.text, it.is_correct) }
                SentenceChoiceTrainer(
                    errorType = current.error_type,
                    wrongSentence = current.wrong_sentence,
                    options = options,
                    verdict = st.verdict,
                    selectedIndex = st.selectedIndex,
                    onPick = vm::pick,
                )

                Spacer(Modifier.height(20.dp))

                if (st.verdict != TapVerdict.NONE) {
                    val correctText = current.options.firstOrNull { it.is_correct }?.text.orEmpty()
                    val (text, color) = when (st.verdict) {
                        TapVerdict.CORRECT -> "✓ Верно!" to SystemGreen
                        TapVerdict.WRONG -> "✗ Правильно: $correctText" to SystemRed
                        else -> "" to LabelSecondary
                    }
                    Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
            val correctText = current.options.firstOrNull { it.is_correct }?.text.orEmpty()
            ExplanationBottomSheet(
                word = current.id,
                kind = "grammar",
                fallbackContext = "Тип ошибки: ${current.error_type}. Ошибочная фраза: «${current.wrong_sentence}». " +
                    "Правильно: «$correctText». Объясни почему именно так.",
                onDismiss = { showExplanation = false },
                onOpenSettings = {
                    showExplanation = false
                    onOpenSettings()
                },
            )
        }
    }
}
