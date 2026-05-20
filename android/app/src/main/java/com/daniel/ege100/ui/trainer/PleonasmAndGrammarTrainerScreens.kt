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
import com.daniel.ege100.data.PleonasmItem
import com.daniel.ege100.data.PleonasmsRepository
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.ai.ExplanationBottomSheet
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================================================
// Pleonasm Trainer (русский №6)
// ============================================================================

data class PleonasmTrainerUi(
    val items: List<PleonasmItem> = emptyList(),
    val position: Int = 0,
    val verdict: TapVerdict = TapVerdict.NONE,
    val tappedWord: String? = null,
    val completed: Boolean = false,
    // Phase 4 Stage P4-D7 (Convention #93).
    val pendingResume: com.daniel.ege100.data.TrainerProgress? = null,
)

class PleonasmTrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(PleonasmTrainerUi())
    val state: StateFlow<PleonasmTrainerUi> = _state.asStateFlow()

    private val trainerId = "rus_pleonasm"

    init {
        viewModelScope.launch {
            // Phase 4 Stage P4-D7 (Convention #94) — stable order.
            val items = PleonasmsRepository.load(getApplication())
                .sortedBy { it.problem_id ?: 0 }

            val ctx = getApplication<Application>()
            val saved = com.daniel.ege100.data.TrainerProgressStore.get(ctx, trainerId)
            val savedValid = saved != null &&
                saved.total == items.size &&
                saved.position in 1 until items.size

            _state.value = PleonasmTrainerUi(
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
            tappedWord = null,
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

    fun tap(word: String) {
        val s = _state.value
        if (s.verdict != TapVerdict.NONE) return
        val current = s.items.getOrNull(s.position) ?: return
        val target = current.extra_word
        val isRight = normalize(word) == normalize(target)
        _state.value = s.copy(
            verdict = if (isRight) TapVerdict.CORRECT else TapVerdict.WRONG,
            tappedWord = word,
        )
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            StreakStore.onProblemSolved(ctx)
            UserStatsStore.recordAttempt(ctx, "rus", 6, null, isRight)
            if (isRight) UserStatsStore.incrementTrainerWordsLearned(ctx)
            runCatching {
                UserDataDatabase.get(ctx).attemptLogDao().insert(
                    com.daniel.ege100.data.AttemptLogEntity(
                        problemId = current.problem_id,
                        subject = "rus",
                        typeNumber = 6,
                        subtypeId = null,
                        isCorrect = isRight,
                        durationMs = 0,
                        timestamp = System.currentTimeMillis(),
                        source = "pleonasm_trainer",
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
            viewModelScope.launch {
                com.daniel.ege100.data.TrainerProgressStore.clear(getApplication(), trainerId)
            }
            onCompleted(s.items.size)
            return
        }
        _state.value = s.copy(position = n, verdict = TapVerdict.NONE, tappedWord = null)
        persistProgress()
    }

    private fun normalize(s: String) = s.trim('.', ',', ';', '!', '?', ':').lowercase().replace('ё', 'е')
}

@Composable
fun PleonasmTrainerScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompleted: (Int) -> Unit,
    contentPadding: PaddingValues,
    vm: PleonasmTrainerViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    var showExplanation by remember { mutableStateOf(false) }

    // Phase 4 Stage P4-D7 (Convention #93) — ResumeBottomSheet.
    val pending = st.pendingResume
    if (pending != null) {
        com.daniel.ege100.ui.common.ResumeBottomSheet(
            trainerTitle = "Плеоназмы",
            savedPosition = pending.position,
            total = pending.total,
            onResume = { vm.acceptResume() },
            onStartOver = { vm.acceptStartOver() },
            onDismiss = { vm.acceptStartOver() },
        )
    }

    TapTrainerScaffold(
        title = "Плеоназмы",
        position = st.position,
        total = st.items.size,
        instruction = "Тапни ЛИШНЕЕ слово в предложении",
        sentence = st.items.getOrNull(st.position)?.sentence.orEmpty(),
        targetWord = st.items.getOrNull(st.position)?.extra_word.orEmpty(),
        verdict = st.verdict,
        tappedWord = st.tappedWord,
        onTap = vm::tap,
        onNext = { vm.next(onCompleted) },
        onAi = { showExplanation = true },
        onBack = onBack,
        contentPadding = contentPadding,
        correctAnswer = st.items.getOrNull(st.position)?.extra_word.orEmpty(),
        completed = st.completed,
    )

    if (showExplanation) {
        val current = st.items.getOrNull(st.position)
        if (current != null) {
            ExplanationBottomSheet(
                word = current.extra_word,
                kind = "pleonasm",
                fallbackContext = "В предложении «${current.sentence}» лишнее слово — «${current.extra_word}». " +
                    "Объясни почему оно избыточно (плеоназм).",
                onDismiss = { showExplanation = false },
                onOpenSettings = {
                    showExplanation = false
                    onOpenSettings()
                },
            )
        }
    }
}

// Phase 4 Stage P4-D6 (Convention #90) — GrammarErrorTrainer + SentenceChoiceTrainer
// полностью удалены. Файл оставлен для PleonasmTrainer (№6).

// ============================================================================
// Общий Scaffold для tap-based тренажёров (Pleonasm)
// ============================================================================

@Composable
private fun TapTrainerScaffold(
    title: String,
    position: Int,
    total: Int,
    instruction: String,
    sentence: String,
    targetWord: String,
    verdict: TapVerdict,
    tappedWord: String?,
    onTap: (String) -> Unit,
    onNext: () -> Unit,
    onAi: () -> Unit,
    onBack: () -> Unit,
    correctAnswer: String,
    completed: Boolean,
    contentPadding: PaddingValues,
) {
    Scaffold(
        topBar = {
            LargeTitleBar(
                title = title,
                subtitle = if (total > 0) "${position + 1} из $total" else "Загрузка…",
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
            if (total == 0) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (completed) "Тренажёр пройден 🎉" else "Загрузка…", color = LabelSecondary)
                }
                return@Box
            }
            Column(modifier = Modifier.fillMaxSize()) {
                AppleProgressBar(
                    progress = (position + 1).toFloat() / total.coerceAtLeast(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 16.dp),
                )

                WordTapInSentenceTrainer(
                    instruction = instruction,
                    sentence = sentence,
                    targetWord = targetWord,
                    verdict = verdict,
                    tappedWord = tappedWord,
                    onTap = onTap,
                )

                Spacer(Modifier.height(20.dp))

                if (verdict != TapVerdict.NONE) {
                    val (text, color) = when (verdict) {
                        TapVerdict.CORRECT -> "✓ Верно!" to SystemGreen
                        TapVerdict.WRONG -> "✗ Правильный ответ: $correctAnswer" to SystemRed
                        else -> "" to LabelSecondary
                    }
                    Text(text, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton(
                        text = "📖 Объяснение",
                        onClick = onAi,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    PrimaryButton(
                        text = "Далее →",
                        onClick = onNext,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
