package com.daniel.ege100.ui.wordblank

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.AnswerChecker
import com.daniel.ege100.data.AttemptLogEntity
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.TrainerProgress
import com.daniel.ege100.data.TrainerProgressStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.data.WordBlank
import com.daniel.ege100.data.WordBlankErrorsStore
import com.daniel.ege100.data.WordBlanksRepository
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.ResumeBottomSheet
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------- модель ----------------------

private val RUS_LETTERS = ('а'..'я').toSet() + setOf('ё', 'ъ', 'ь')

enum class Order { Alphabetical, Random }

sealed class BlankInputState {
    data object Empty : BlankInputState()
    data class Verdict(
        val userAnswer: String,
        val correctAnswer: String,
        val isRight: Boolean,
    ) : BlankInputState()
}

data class WordBlankUi(
    val loading: Boolean = true,
    val typeNumber: Int = 0,
    val title: String = "",
    val fullTitle: String = "",
    val words: List<WordBlank> = emptyList(),
    val order: Order = Order.Alphabetical,
    val orderedIndices: List<Int> = emptyList(),
    val position: Int = 0,
    val userInput: String = "",
    val state: BlankInputState = BlankInputState.Empty,
    val pendingResume: TrainerProgress? = null,
) {
    val total: Int get() = orderedIndices.size
    val currentWord: WordBlank?
        get() = orderedIndices.getOrNull(position)?.let { words.getOrNull(it) }
}

class WordBlankTrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(WordBlankUi())
    val state: StateFlow<WordBlankUi> = _state.asStateFlow()

    private var initializedFor: Int? = null

    private fun trainerId(): String = "blank_${_state.value.typeNumber}"

    // Phase 4 Stage P4-C2 part А (Convention #56) — auto-advance Job
    // на ViewModel, чтобы AI-bottom-sheet мог его отменить.
    private var pendingAdvanceJob: kotlinx.coroutines.Job? = null

    private fun scheduleAutoAdvance(delayMs: Long = 1000L) {
        pendingAdvanceJob?.cancel()
        pendingAdvanceJob = viewModelScope.launch {
            kotlinx.coroutines.delay(delayMs)
            goNext()
            pendingAdvanceJob = null
        }
    }

    fun onAskAiOpened() {
        pendingAdvanceJob?.cancel()
        pendingAdvanceJob = null
    }

    fun lastVerdictWasRight(): Boolean =
        (_state.value.state as? BlankInputState.Verdict)?.isRight == true

    fun onAskAiClosed(wasRight: Boolean) {
        if (wasRight && _state.value.state is BlankInputState.Verdict) {
            scheduleAutoAdvance(delayMs = 500L)
        }
    }

    fun start(typeNumber: Int, defaultOrder: String) {
        if (initializedFor == typeNumber) return
        initializedFor = typeNumber
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val type = WordBlanksRepository.loadType(ctx, typeNumber)
            if (type == null) {
                _state.value = WordBlankUi(loading = false, typeNumber = typeNumber)
                return@launch
            }
            val orderEnum = if (defaultOrder == "random") Order.Random else Order.Alphabetical
            val sorted = type.words.indices.sortedBy { type.words[it].full }
            val indices = if (orderEnum == Order.Random) type.words.indices.shuffled() else sorted

            val saved = TrainerProgressStore.get(ctx, "blank_$typeNumber")
            val savedValid = saved != null &&
                saved.total == indices.size &&
                saved.position in 1 until indices.size &&
                saved.indices.size == indices.size

            _state.value = WordBlankUi(
                loading = false,
                typeNumber = typeNumber,
                title = type.title,
                fullTitle = type.full_title,
                words = type.words,
                order = orderEnum,
                orderedIndices = indices,
                position = 0,
                userInput = "",
                state = BlankInputState.Empty,
                pendingResume = if (savedValid) saved else null,
            )
        }
    }

    fun acceptResume() {
        val cur = _state.value
        val saved = cur.pendingResume ?: return
        val savedOrder = if (saved.order == "random") Order.Random else Order.Alphabetical
        _state.value = cur.copy(
            order = savedOrder,
            orderedIndices = saved.indices,
            position = saved.position,
            userInput = "",
            state = BlankInputState.Empty,
            pendingResume = null,
        )
    }

    fun acceptStartOver() {
        val cur = _state.value
        viewModelScope.launch {
            TrainerProgressStore.clear(getApplication(), trainerId())
        }
        _state.value = cur.copy(
            position = 0,
            userInput = "",
            state = BlankInputState.Empty,
            pendingResume = null,
        )
    }

    private fun persistProgress() {
        val cur = _state.value
        if (cur.total == 0) return
        viewModelScope.launch {
            TrainerProgressStore.save(
                getApplication(),
                trainerId(),
                TrainerProgress(
                    position = cur.position,
                    total = cur.total,
                    order = if (cur.order == Order.Random) "random" else "alphabetical",
                    indices = cur.orderedIndices,
                ),
            )
        }
    }

    private fun clearProgress() {
        viewModelScope.launch {
            TrainerProgressStore.clear(getApplication(), trainerId())
        }
    }

    fun setInput(value: String) {
        val cur = _state.value
        if (cur.state is BlankInputState.Verdict) return
        // Принимаем только русские буквы, до 3 символов.
        val sanitized = value.lowercase().filter { it in RUS_LETTERS }.take(3)
        _state.value = cur.copy(userInput = sanitized)
    }

    /**
     * Phase 4 Stage P4-C2 part Б (Convention #57) — комбинированный вызов
     * для LetterChoiceRow: ставит input и сразу проверяет. Экономит
     * пользователю один тап («Проверить»).
     */
    fun checkLetter(letter: String) {
        val cur = _state.value
        if (cur.state is BlankInputState.Verdict) return
        _state.value = cur.copy(userInput = letter)
        check()
    }

    fun check() {
        val cur = _state.value
        val word = cur.currentWord ?: return
        val typed = cur.userInput.trim().lowercase()
        if (typed.isBlank() || cur.state is BlankInputState.Verdict) return
        val correct = word.answer.lowercase()
        // Phase 4 Stage P4-C part А — Convention #48: через AnswerChecker.
        // Тренажёр пропусков обычно даёт одну букву — но если sdamgia
        // когда-нибудь начнёт давать варианты типа «о|а», AnswerChecker
        // обработает корректно.
        val isRight = AnswerChecker.isCorrect(typed, correct, "string")
        // Phase 4 Stage P4-D2 part Г (Convention #67) — breadcrumb.
        com.daniel.ege100.data.BreadcrumbLog.add(
            "WordBlankCheck: t=${cur.typeNumber}, word='${word.full}', user='$typed', correct=$isRight",
        )
        if (!isRight) {
            viewModelScope.launch {
                WordBlankErrorsStore.recordError(
                    getApplication(),
                    cur.typeNumber,
                    word.masked,
                )
            }
        }
        // Phase 3 part В + Г: тренажёр №9-12 русского → subject="rus",
        // typeNumber = текущий тип (9/10/11/12). subtypeId=null.
        // Phase 3 Stage C: + запись в attempt_log с source=wordblank_trainer.
        viewModelScope.launch {
            UserStatsStore.recordAttempt(
                context = getApplication(),
                subject = "rus",
                typeNumber = cur.typeNumber,
                subtypeId = null,
                isCorrect = isRight,
            )
            // Phase 4 Stage P4-C part Е1 (Convention #54).
            if (isRight) {
                UserStatsStore.incrementTrainerWordsLearned(getApplication())
            }
            StreakStore.onProblemSolved(getApplication())
            UserDataDatabase.get(getApplication()).attemptLogDao().insert(
                AttemptLogEntity(
                    problemId = null,
                    subject = "rus",
                    typeNumber = cur.typeNumber,
                    subtypeId = null,
                    isCorrect = isRight,
                    durationMs = 0L,
                    timestamp = System.currentTimeMillis(),
                    source = "wordblank_trainer",
                ),
            )
        }
        _state.value = cur.copy(
            state = BlankInputState.Verdict(
                userAnswer = typed,
                correctAnswer = correct,
                isRight = isRight,
            ),
        )
        // Phase 4 Stage P4-C2 part А (Convention #56) — auto-advance Job.
        if (isRight) scheduleAutoAdvance(delayMs = 1000L)
    }

    fun goNext() {
        val cur = _state.value
        if (cur.position + 1 >= cur.total) {
            clearProgress()
            return
        }
        _state.value = cur.copy(
            position = cur.position + 1,
            userInput = "",
            state = BlankInputState.Empty,
        )
        persistProgress()
    }

    fun goPrev() {
        val cur = _state.value
        if (cur.position == 0) return
        _state.value = cur.copy(
            position = cur.position - 1,
            userInput = "",
            state = BlankInputState.Empty,
        )
        persistProgress()
    }

    fun toggleOrder() {
        val cur = _state.value
        val newOrder = if (cur.order == Order.Alphabetical) Order.Random else Order.Alphabetical
        val newIndices = if (newOrder == Order.Random) cur.words.indices.shuffled()
                         else cur.words.indices.sortedBy { cur.words[it].full }
        _state.value = cur.copy(
            order = newOrder,
            orderedIndices = newIndices,
            position = 0,
            userInput = "",
            state = BlankInputState.Empty,
        )
        clearProgress()
    }
}

// ---------------------- UI ----------------------

@Composable
fun WordBlankTrainerScreen(
    typeNumber: Int,
    defaultOrder: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    onOpenAiSettings: () -> Unit = {},
    vm: WordBlankTrainerViewModel = viewModel(),
) {
    LaunchedEffect(typeNumber, defaultOrder) { vm.start(typeNumber, defaultOrder) }
    val st by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showAi by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    // Phase 4 Stage P4-C2 part Б (Convention #58) — toggle из AppSettings.
    val appSettings by com.daniel.ege100.data.AppSettingsStore.settingsFlow(context)
        .collectAsState(initial = com.daniel.ege100.data.AppSettings())
    val useLetterChoices = appSettings.useLetterChoices

    // Phase 4 Stage P4-C2 part А (Convention #56) — auto-advance перенесён
    // во ViewModel.scheduleAutoAdvance(); здесь только haptic.

    // Haptic при verdict.
    LaunchedEffect(st.state) {
        when (val v = st.state) {
            is BlankInputState.Verdict ->
                haptic.performHapticFeedback(
                    if (v.isRight) HapticFeedbackType.LongPress
                    else HapticFeedbackType.TextHandleMove,
                )
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = st.title.ifBlank { "Орфография" },
                subtitle = if (st.total > 0) "${st.position + 1} из ${st.total}" else st.fullTitle,
                onBack = onBack,
                rightContent = {
                    OrderToggleChip(order = st.order, onClick = vm::toggleOrder)
                },
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
            when {
                st.loading -> CenteredText("Загрузка словаря…")
                st.currentWord == null -> CenteredText("Слова не найдены")
                else -> Body(
                    st = st,
                    vm = vm,
                    useLetterChoices = useLetterChoices,
                    onAiClick = { showAi = true },
                )
            }
        }
    }

    val pending = st.pendingResume
    if (pending != null) {
        ResumeBottomSheet(
            trainerTitle = st.title.ifBlank { "Орфография" },
            savedPosition = pending.position,
            total = pending.total,
            onResume = { vm.acceptResume() },
            onStartOver = { vm.acceptStartOver() },
            onDismiss = { vm.acceptStartOver() },
        )
    }

    // Phase 4 Stage P4-C2 part А (Convention #56) — отменяем auto-advance
    // при открытии AI-окна.
    LaunchedEffect(showAi) {
        if (showAi) vm.onAskAiOpened()
    }

    // Phase 4 Stage P4-C part Д (Convention #53) — AI в тренажёре пропусков.
    if (showAi) {
        val word = st.currentWord
        val verdict = st.state as? BlankInputState.Verdict
        if (word != null && verdict != null) {
            val context = buildString {
                append("Слово с пропуском: ${word.masked.replace("..", "_")}. ")
                append("Полное слово: ${word.full}. ")
                append("Правильная буква: «${verdict.correctAnswer}». ")
                if (!verdict.isRight) {
                    append("Пользователь ввёл: «${verdict.userAnswer}».")
                } else {
                    append("Пользователь ответил правильно.")
                }
            }
            com.daniel.ege100.ui.ai.AskAiBottomSheet(
                problemContext = context,
                userAnswerForHint = if (!verdict.isRight) verdict.userAnswer else null,
                onDismiss = {
                    showAi = false
                    // Phase 4 Stage P4-C2 part А (Convention #56) — resume.
                    vm.onAskAiClosed(verdict.isRight)
                },
                onOpenSettings = {
                    showAi = false
                    onOpenAiSettings()
                },
                customQuickQuestions = listOf(
                    com.daniel.ege100.ui.ai.QuickQuestion(
                        "Почему эта буква?",
                        "Почему в слове «${word.full}» пишется именно «${verdict.correctAnswer}»?",
                    ),
                    com.daniel.ege100.ui.ai.QuickQuestion(
                        "Какое правило?",
                        "Какое орфографическое правило применимо к слову «${word.full}»?",
                    ),
                    com.daniel.ege100.ui.ai.QuickQuestion(
                        "Похожие слова",
                        "Приведи 3-5 похожих слов, где работает то же правило, чтобы я запомнил шаблон.",
                    ),
                    com.daniel.ege100.ui.ai.QuickQuestion(
                        "Запомнить",
                        "Подскажи мнемоническое правило или образ, чтобы запомнить написание слова «${word.full}».",
                    ),
                ),
            )
        }
    }
}

@Composable
private fun CenteredText(s: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(s, color = LabelSecondary)
    }
}

@Composable
private fun Body(
    st: WordBlankUi,
    vm: WordBlankTrainerViewModel,
    useLetterChoices: Boolean,
    onAiClick: () -> Unit = {},
) {
    val word = st.currentWord ?: return
    val density = LocalDensity.current
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        AppleProgressBar(
            progress = if (st.total > 0) (st.position + 1).toFloat() / st.total else 0f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
        )

        // Phase 4 Stage P4-C3 part В2 (Convention #63) — SwipeableProblemContent.
        com.daniel.ege100.ui.common.SwipeableProblemContent(
            hasPrev = st.position > 0,
            hasNext = st.position < st.total - 1,
            onPrev = { vm.goPrev() },
            onNext = { vm.goNext() },
            onSwipeStart = { vm.onAskAiOpened() },
            modifier = Modifier.weight(1f),
        ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            AnimatedContent(
                targetState = st.position,
                transitionSpec = {
                    val forward = targetState > initialState
                    val swipeSpring = spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = 0.85f,
                        stiffness = Spring.StiffnessMediumLow,
                    )
                    if (forward) {
                        (slideInHorizontally(swipeSpring) { it } +
                            fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(swipeSpring) { -it / 3 } + fadeOut(tween(280)))
                    } else {
                        (slideInHorizontally(swipeSpring) { -it } +
                            fadeIn(tween(280))) togetherWith
                            (slideOutHorizontally(swipeSpring) { it / 3 } + fadeOut(tween(280)))
                    }
                },
                label = "word-transition",
            ) { _ ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    WordDisplay(word = word, state = st.state)
                    Spacer(Modifier.height(24.dp))
                    // Phase 4 Stage P4-C2 part Б (Convention #57/58) — две
                    // разные input-панели: кнопки букв (default) или
                    // текстовое поле (toggle в Настройках).
                    if (useLetterChoices) {
                        LetterChoiceInputPanel(
                            word = word,
                            state = st.state,
                            onSelect = vm::checkLetter,
                        )
                    } else {
                        when (st.state) {
                            is BlankInputState.Verdict -> VerdictPanel(st.state, word)
                            else -> InputPanel(
                                input = st.userInput,
                                onChange = vm::setInput,
                                onCheck = vm::check,
                            )
                        }
                    }
                }
            }
        }
        }  // end SwipeableProblemContent

        // Phase 4 Stage P4-C part Д (Convention #53) — AI-кнопка после verdict.
        if (st.state is BlankInputState.Verdict) {
            com.daniel.ege100.ui.common.SecondaryButton(
                text = "🤖 Спросить ИИ",
                onClick = onAiClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        SwipeHint(
            verdict = st.state as? BlankInputState.Verdict,
            isFirst = st.position == 0,
            isLast = st.position == st.total - 1,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun WordDisplay(word: WordBlank, state: BlankInputState) {
    val verdict = state as? BlankInputState.Verdict
    val annotated = buildAnnotatedString {
        if (verdict == null) {
            // Показываем masked с подсвеченным двоеточием.
            val parts = word.masked.split("..")
            append(parts.getOrNull(0) ?: "")
            withStyle(SpanStyle(color = SystemBlue, fontWeight = FontWeight.Bold)) {
                append("··")
            }
            append(parts.getOrNull(1) ?: "")
        } else {
            // Показываем полное слово с подсветкой ответа.
            val full = word.full
            val masked = word.masked
            val parts = masked.split("..", limit = 2)
            val before = parts.getOrNull(0) ?: ""
            val after = parts.getOrNull(1) ?: ""
            // Извлекаем вставленные буквы: между before и after в full.
            val insertedStart = before.length
            val insertedEnd = full.length - after.length
            val inserted = if (insertedEnd > insertedStart) full.substring(insertedStart, insertedEnd) else word.answer
            append(before)
            val color = if (verdict.isRight) SystemGreen else SystemRed
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                append(inserted)
            }
            append(after)
        }
    }
    Text(
        text = annotated,
        fontSize = 48.sp,
        fontWeight = FontWeight.SemiBold,
        color = Label,
        textAlign = TextAlign.Center,
        lineHeight = 56.sp,
    )
}

/**
 * Phase 4 Stage P4-C2 part Б (Convention #57) — LetterChoiceInputPanel.
 *
 * Показывает 1-3 кнопки-варианта вместо ручного ввода. После Verdict
 * сохраняем выбранный вариант + правильный для подсветки (зелёный/
 * красный). Подсказка-правило `word.rule_hint` под кнопками при
 * неверном ответе.
 */
@Composable
private fun LetterChoiceInputPanel(
    word: com.daniel.ege100.data.WordBlank,
    state: BlankInputState,
    onSelect: (String) -> Unit,
) {
    val choices = androidx.compose.runtime.remember(word.full, word.answer) {
        com.daniel.ege100.data.WordBlankChoices.choicesFor(word.answer)
    }
    val verdict = state as? BlankInputState.Verdict
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Какая буква пропущена?",
            color = LabelSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        com.daniel.ege100.ui.common.LetterChoiceRow(
            choices = choices,
            selected = verdict?.userAnswer,
            correct = verdict?.correctAnswer,
            showVerdict = verdict != null,
            enabled = verdict == null,
            onSelect = onSelect,
        )
        if (verdict != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = if (verdict.isRight) "✓ Верно" else "✕ Неверно",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (verdict.isRight) SystemGreen else SystemRed,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
            if (!verdict.isRight) {
                Spacer(Modifier.height(14.dp))
                Text(
                    text = word.rule_hint,
                    fontSize = 14.sp,
                    color = LabelTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun InputPanel(
    input: String,
    onChange: (String) -> Unit,
    onCheck: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Какая буква пропущена?",
            color = LabelSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        IosTextField(
            value = input,
            onValueChange = onChange,
            placeholder = "Введи букву",
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
        )
        Spacer(Modifier.height(16.dp))
        PrimaryButton(
            text = "Проверить",
            onClick = onCheck,
            enabled = input.isNotBlank(),
        )
    }
}

@Composable
private fun VerdictPanel(verdict: BlankInputState.Verdict, word: WordBlank) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            initialScale = 0.5f,
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (verdict.isRight) "✓ Верно" else "✕ Неверно",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (verdict.isRight) SystemGreen else SystemRed,
            )
            if (!verdict.isRight) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Ты ввёл: ${verdict.userAnswer} — правильно: ${verdict.correctAnswer}",
                    fontSize = 15.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = word.rule_hint,
                    fontSize = 14.sp,
                    color = LabelTertiary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SwipeHint(verdict: BlankInputState.Verdict?, isFirst: Boolean, isLast: Boolean) {
    val text = when {
        verdict != null && !verdict.isRight -> "Свайпни влево для следующего слова"
        verdict != null && verdict.isRight -> "Готово · следующее через секунду"
        else -> buildString {
            if (!isFirst) append("← Свайп вправо: предыдущее")
            if (!isFirst && !isLast) append("    ")
            if (!isLast) append("Свайп влево: следующее →")
        }
    }
    if (text.isEmpty()) {
        Spacer(Modifier.height(20.dp))
        return
    }
    Text(
        text = text,
        fontSize = 13.sp,
        color = LabelTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun OrderToggleChip(order: Order, onClick: () -> Unit) {
    val label = when (order) {
        Order.Alphabetical -> "⇆ А-Я"
        Order.Random -> "⇆ 🎲"
    }
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x1F0A84FF))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text = label, color = SystemBlue, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
