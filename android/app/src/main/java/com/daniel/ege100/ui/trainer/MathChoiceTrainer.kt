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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.Derivative
import com.daniel.ege100.data.DerivativesRepository
import com.daniel.ege100.data.GeometricFormula
import com.daniel.ege100.data.GeometricFormulasRepository
import com.daniel.ege100.data.LogPowerProperty
import com.daniel.ege100.data.LogPowerRepository
import com.daniel.ege100.data.ShortMultFormula
import com.daniel.ege100.data.ShortMultRepository
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.TrigValue
import com.daniel.ege100.data.TrigValuesRepository
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
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 4 Stage P4-D (Convention #74) — общая компонента для 5 математических
 * тренажёров: тригонометрия, сокращённое умножение, логарифмы/степени,
 * производные, геометрия. UX: вопрос сверху + 4 кнопки 2x2.
 *
 * Опция «На скорость» — таймер 5сек.
 */
data class MathChoiceQuestion(
    val word: String,        // ключ для ExplanationBottomSheet
    val question: String,    // что показать сверху
    val correct: String,
    val distractors: List<String>,
)

data class MathChoiceUi(
    val questions: List<MathChoiceQuestion> = emptyList(),
    val position: Int = 0,
    val verdict: TapVerdict = TapVerdict.NONE,
    val selectedOption: String? = null,
    val completed: Boolean = false,
    val timerEnabled: Boolean = false,
    val timeLeftMs: Long = 5000L,
    // Phase 4 Stage P4-D7 (Convention #93) — TrainerProgressStore resume.
    val pendingResume: com.daniel.ege100.data.TrainerProgress? = null,
)

abstract class MathChoiceViewModel(app: Application) : AndroidViewModel(app) {
    protected val _state = MutableStateFlow(MathChoiceUi())
    val state: StateFlow<MathChoiceUi> = _state.asStateFlow()

    abstract val trainerKind: String   // "math"
    abstract val subjectSlug: String   // "math"
    abstract val typeNumber: Int

    /** Phase 4 Stage P4-D7 (Convention #93) — stable trainerId. */
    protected fun trainerId(): String = "math_$trainerKind"

    /**
     * Phase 4 Stage P4-D7 (Convention #94) — вызывается каждым подклассом после
     * установки questions (в стабильном порядке, БЕЗ shuffle). Загружает saved
     * прогресс из TrainerProgressStore и устанавливает pendingResume если есть.
     */
    protected suspend fun applyResumeIfAvailable() {
        val ctx = getApplication<Application>()
        val items = _state.value.questions
        if (items.isEmpty()) return
        val saved = com.daniel.ege100.data.TrainerProgressStore.get(ctx, trainerId())
        val savedValid = saved != null &&
            saved.total == items.size &&
            saved.position in 1 until items.size
        if (savedValid) {
            _state.value = _state.value.copy(
                position = saved!!.position,
                pendingResume = saved,
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
            com.daniel.ege100.data.TrainerProgressStore.clear(getApplication(), trainerId())
        }
        _state.value = _state.value.copy(
            position = 0,
            verdict = TapVerdict.NONE,
            selectedOption = null,
            timeLeftMs = 5000L,
            pendingResume = null,
        )
    }

    private fun persistProgress() {
        val cur = _state.value
        if (cur.questions.isEmpty()) return
        viewModelScope.launch {
            com.daniel.ege100.data.TrainerProgressStore.save(
                getApplication(),
                trainerId(),
                com.daniel.ege100.data.TrainerProgress(
                    position = cur.position,
                    total = cur.questions.size,
                    order = "alphabetical",
                    indices = cur.questions.indices.toList(),
                ),
            )
        }
    }

    protected fun persistProgressInternal() = persistProgress()

    protected fun clearProgressOnComplete() {
        viewModelScope.launch {
            com.daniel.ege100.data.TrainerProgressStore.clear(getApplication(), trainerId())
        }
    }

    fun answer(picked: String) {
        val s = _state.value
        if (s.verdict != TapVerdict.NONE) return
        val current = s.questions.getOrNull(s.position) ?: return
        val isRight = picked == current.correct
        _state.value = s.copy(verdict = if (isRight) TapVerdict.CORRECT else TapVerdict.WRONG, selectedOption = picked)
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            StreakStore.onProblemSolved(ctx)
            UserStatsStore.recordAttempt(ctx, subjectSlug, typeNumber, null, isRight)
            if (isRight) UserStatsStore.incrementTrainerWordsLearned(ctx)
            runCatching {
                UserDataDatabase.get(ctx).attemptLogDao().insert(
                    com.daniel.ege100.data.AttemptLogEntity(
                        problemId = null,
                        subject = subjectSlug,
                        typeNumber = typeNumber,
                        subtypeId = null,
                        isCorrect = isRight,
                        durationMs = 0,
                        timestamp = System.currentTimeMillis(),
                        source = "math_${trainerKind}_trainer",
                    ),
                )
            }
        }
    }

    fun next(onCompleted: (Int) -> Unit) {
        val s = _state.value
        val n = s.position + 1
        if (n >= s.questions.size) {
            _state.value = s.copy(completed = true)
            clearProgressOnComplete()
            onCompleted(s.questions.size)
            return
        }
        _state.value = s.copy(position = n, verdict = TapVerdict.NONE, selectedOption = null, timeLeftMs = 5000L)
        persistProgressInternal()
    }

    fun toggleTimer() {
        _state.value = _state.value.copy(timerEnabled = !_state.value.timerEnabled, timeLeftMs = 5000L)
    }

    fun tickTimer(deltaMs: Long) {
        val s = _state.value
        if (!s.timerEnabled || s.verdict != TapVerdict.NONE) return
        val newLeft = s.timeLeftMs - deltaMs
        if (newLeft <= 0) {
            // Таймаут — считаем как Wrong, показываем ответ
            val current = s.questions.getOrNull(s.position) ?: return
            _state.value = s.copy(verdict = TapVerdict.WRONG, selectedOption = "TIMEOUT", timeLeftMs = 0)
            val ctx = getApplication<Application>()
            viewModelScope.launch {
                UserStatsStore.recordAttempt(ctx, subjectSlug, typeNumber, null, false)
            }
        } else {
            _state.value = s.copy(timeLeftMs = newLeft)
        }
    }
}

@Composable
fun MathChoiceTrainerScreen(
    title: String,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCompleted: (Int) -> Unit,
    contentPadding: PaddingValues,
    vm: MathChoiceViewModel,
    explanationContextBuilder: (MathChoiceQuestion) -> String,
) {
    val st by vm.state.collectAsState()
    var showExplanation by remember { mutableStateOf(false) }

    // Таймер «На скорость»
    LaunchedEffect(st.timerEnabled, st.position, st.verdict) {
        if (st.timerEnabled && st.verdict == TapVerdict.NONE) {
            while (true) {
                delay(100)
                vm.tickTimer(100)
            }
        }
    }

    // Phase 4 Stage P4-D7 (Convention #93) — ResumeBottomSheet для всех 5 math.
    val pending = st.pendingResume
    if (pending != null) {
        com.daniel.ege100.ui.common.ResumeBottomSheet(
            trainerTitle = title,
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
                title = title,
                subtitle = if (st.questions.isNotEmpty()) "${st.position + 1} из ${st.questions.size}" else "Загрузка…",
                onBack = onBack,
                rightContent = {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (st.timerEnabled) SystemOrange else SystemBlueTint)
                            .clickable { vm.toggleTimer() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = if (st.timerEnabled) "⏱ 5с" else "⏱ Off",
                            color = if (st.timerEnabled) Label else SystemBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
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
            val current = st.questions.getOrNull(st.position)
            if (current == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(if (st.completed) "Тренажёр пройден 🎉" else "Загрузка…", color = LabelSecondary)
                }
                return@Box
            }
            Column(modifier = Modifier.fillMaxSize()) {
                AppleProgressBar(
                    progress = (st.position + 1).toFloat() / st.questions.size.coerceAtLeast(1),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                )
                if (st.timerEnabled) {
                    AppleProgressBar(
                        progress = (st.timeLeftMs / 5000f).coerceIn(0f, 1f),
                        fillColor = when {
                            st.timeLeftMs > 3500 -> SystemGreen
                            st.timeLeftMs > 1500 -> SystemOrange
                            else -> SystemRed
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                }

                Text(
                    text = current.question,
                    color = Label,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                )

                Choices2x2(
                    options = remember(current) { (listOf(current.correct) + current.distractors).shuffled() },
                    correct = current.correct,
                    selected = st.selectedOption,
                    verdict = st.verdict,
                    onPick = vm::answer,
                )

                Spacer(Modifier.weight(1f))

                if (st.verdict != TapVerdict.NONE) {
                    val (text, color) = when (st.verdict) {
                        TapVerdict.CORRECT -> "✓ Верно!" to SystemGreen
                        TapVerdict.WRONG -> "✗ Правильно: ${current.correct}" to SystemRed
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
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    if (showExplanation) {
        val current = st.questions.getOrNull(st.position)
        if (current != null) {
            ExplanationBottomSheet(
                word = current.word,
                kind = "math",
                fallbackContext = explanationContextBuilder(current),
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
private fun Choices2x2(
    options: List<String>,
    correct: String,
    selected: String?,
    verdict: TapVerdict,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        options.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { option ->
                    MathOptionButton(
                        text = option,
                        isCorrect = option == correct,
                        selected = selected == option,
                        verdict = verdict,
                        onClick = { onPick(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Если в строке только одна кнопка — добавляем spacer для выравнивания
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun MathOptionButton(
    text: String,
    isCorrect: Boolean,
    selected: Boolean,
    verdict: TapVerdict,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val (bg, border) = when {
        verdict == TapVerdict.CORRECT && selected -> SystemGreenTint to SystemGreen
        verdict == TapVerdict.WRONG && selected -> SystemRedTint to SystemRed
        verdict == TapVerdict.WRONG && isCorrect -> SystemGreenTint to SystemGreen
        else -> BgElevated to Separator
    }
    val enabled = verdict == TapVerdict.NONE
    Box(
        modifier = modifier
            .height(76.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(14.dp))
            .then(
                if (enabled) Modifier.clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                } else Modifier,
            )
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
    }
}

// ============================================================================
// 5 конкретных ViewModel'ей (каждая инициализирует свои questions)
// ============================================================================

class TrigTrainerViewModel(app: Application) : MathChoiceViewModel(app) {
    override val trainerKind = "trig"
    override val subjectSlug = "math"
    override val typeNumber = 8  // тригонометрия в ЕГЭ

    init {
        viewModelScope.launch {
            val values = TrigValuesRepository.load(getApplication())
            val questions = mutableListOf<MathChoiceQuestion>()
            for (v in values) {
                for ((funcName, correct) in listOf("sin" to v.sin, "cos" to v.cos, "tg" to v.tan, "ctg" to v.ctg)) {
                    if (correct == "не определён") continue
                    val distractors = collectDistractors(values, funcName, correct, n = 3)
                    questions.add(
                        MathChoiceQuestion(
                            word = "$funcName(${v.angle_deg}°)",
                            question = "$funcName(${v.angle_deg}°) = ?",
                            correct = correct,
                            distractors = distractors,
                        ),
                    )
                }
            }
            // Phase 4 Stage P4-D7 (Convention #94) — stable order (без shuffle).
            _state.value = MathChoiceUi(questions = questions)
            applyResumeIfAvailable()
        }
    }

    private fun collectDistractors(all: List<TrigValue>, funcName: String, correct: String, n: Int): List<String> {
        val pool = all.flatMap { listOf(it.sin, it.cos, it.tan, it.ctg) }
            .filter { it != correct && it != "не определён" }
            .distinct()
        return pool.shuffled().take(n)
    }
}

class ShortMultTrainerViewModel(app: Application) : MathChoiceViewModel(app) {
    override val trainerKind = "shortmult"
    override val subjectSlug = "math"
    override val typeNumber = 6  // алгебраические преобразования

    init {
        viewModelScope.launch {
            val formulas = ShortMultRepository.load(getApplication())
            val questions = formulas.map { f ->
                val distractors = formulas.filter { it.id != f.id }.shuffled().take(3).map { it.right }
                MathChoiceQuestion(
                    word = f.name,
                    question = "${f.left} = ?",
                    correct = f.right,
                    distractors = distractors,
                )
            }
            _state.value = MathChoiceUi(questions = questions)
            applyResumeIfAvailable()
        }
    }
}

class LogPowerTrainerViewModel(app: Application) : MathChoiceViewModel(app) {
    override val trainerKind = "logpower"
    override val subjectSlug = "math"
    override val typeNumber = 5  // показательные/логарифмические

    init {
        viewModelScope.launch {
            val props = LogPowerRepository.load(getApplication())
            val questions = props.map { p ->
                val distractors = props.filter { it.id != p.id }.shuffled().take(3).map { it.right }
                MathChoiceQuestion(
                    word = p.left,
                    question = "${p.left} = ?",
                    correct = p.right,
                    distractors = distractors,
                )
            }
            _state.value = MathChoiceUi(questions = questions)
            applyResumeIfAvailable()
        }
    }
}

class DerivativesTrainerViewModel(app: Application) : MathChoiceViewModel(app) {
    override val trainerKind = "derivatives"
    override val subjectSlug = "math"
    override val typeNumber = 7  // производные

    init {
        viewModelScope.launch {
            val derivs = DerivativesRepository.load(getApplication())
            val questions = derivs.map { d ->
                val distractors = derivs.filter { it.derivative != d.derivative }.shuffled().take(3).map { it.derivative }
                MathChoiceQuestion(
                    word = d.function,
                    question = "(${d.function})' = ?",
                    correct = d.derivative,
                    distractors = distractors,
                )
            }
            _state.value = MathChoiceUi(questions = questions)
            applyResumeIfAvailable()
        }
    }
}

class GeometryTrainerViewModel(app: Application) : MathChoiceViewModel(app) {
    override val trainerKind = "geometry"
    override val subjectSlug = "math"
    override val typeNumber = 1  // геометрия — встречается с №1

    init {
        viewModelScope.launch {
            val formulas = GeometricFormulasRepository.load(getApplication())
            val questions = formulas.map { f ->
                val distractors = formulas.filter { it.id != f.id }.shuffled().take(3).map { it.formula }
                MathChoiceQuestion(
                    word = "${f.shape} · ${f.find}",
                    question = "${f.shape}: ${f.find} =",
                    correct = f.formula,
                    distractors = distractors,
                )
            }
            _state.value = MathChoiceUi(questions = questions)
            applyResumeIfAvailable()
        }
    }
}

// ============================================================================
// 5 публичных wrapper-функций — каждая создаёт нужный VM и подставляет
// explanation-context-builder.
// ============================================================================

@Composable
fun TrigTrainerScreen(
    onBack: () -> Unit, onOpenSettings: () -> Unit, onCompleted: (Int) -> Unit, contentPadding: PaddingValues,
    vm: TrigTrainerViewModel = viewModel(),
) {
    MathChoiceTrainerScreen(
        title = "Тригонометрия",
        onBack = onBack, onOpenSettings = onOpenSettings, onCompleted = onCompleted, contentPadding = contentPadding,
        vm = vm,
        explanationContextBuilder = { q -> "Тригонометрия. Найти ${q.question.substringBefore(" = ")}. Правильный ответ: ${q.correct}." },
    )
}

@Composable
fun ShortMultTrainerScreen(
    onBack: () -> Unit, onOpenSettings: () -> Unit, onCompleted: (Int) -> Unit, contentPadding: PaddingValues,
    vm: ShortMultTrainerViewModel = viewModel(),
) {
    MathChoiceTrainerScreen(
        title = "Сокращённое умножение",
        onBack = onBack, onOpenSettings = onOpenSettings, onCompleted = onCompleted, contentPadding = contentPadding,
        vm = vm,
        explanationContextBuilder = { q -> "Формула сокращённого умножения: ${q.question} ${q.correct}. Объясни как раскрывается и где применяется." },
    )
}

@Composable
fun LogPowerTrainerScreen(
    onBack: () -> Unit, onOpenSettings: () -> Unit, onCompleted: (Int) -> Unit, contentPadding: PaddingValues,
    vm: LogPowerTrainerViewModel = viewModel(),
) {
    MathChoiceTrainerScreen(
        title = "Логарифмы и степени",
        onBack = onBack, onOpenSettings = onOpenSettings, onCompleted = onCompleted, contentPadding = contentPadding,
        vm = vm,
        explanationContextBuilder = { q -> "Свойство логарифмов/степеней: ${q.question} = ${q.correct}. Объясни." },
    )
}

@Composable
fun DerivativesTrainerScreen(
    onBack: () -> Unit, onOpenSettings: () -> Unit, onCompleted: (Int) -> Unit, contentPadding: PaddingValues,
    vm: DerivativesTrainerViewModel = viewModel(),
) {
    MathChoiceTrainerScreen(
        title = "Производные",
        onBack = onBack, onOpenSettings = onOpenSettings, onCompleted = onCompleted, contentPadding = contentPadding,
        vm = vm,
        explanationContextBuilder = { q -> "Производная функции ${q.word} равна ${q.correct}. Объясни вывод." },
    )
}

@Composable
fun GeometryTrainerScreen(
    onBack: () -> Unit, onOpenSettings: () -> Unit, onCompleted: (Int) -> Unit, contentPadding: PaddingValues,
    vm: GeometryTrainerViewModel = viewModel(),
) {
    MathChoiceTrainerScreen(
        title = "Геометрические формулы",
        onBack = onBack, onOpenSettings = onOpenSettings, onCompleted = onCompleted, contentPadding = contentPadding,
        vm = vm,
        explanationContextBuilder = { q -> "Геометрия: ${q.word}. Формула: ${q.correct}. Объясни откуда берётся и в каких задачах применяется." },
    )
}
