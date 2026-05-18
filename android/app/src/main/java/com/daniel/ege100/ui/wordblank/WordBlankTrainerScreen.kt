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
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.TrainerProgress
import com.daniel.ege100.data.TrainerProgressStore
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

    fun check() {
        val cur = _state.value
        val word = cur.currentWord ?: return
        val typed = cur.userInput.trim().lowercase()
        if (typed.isBlank() || cur.state is BlankInputState.Verdict) return
        val correct = word.answer.lowercase()
        val isRight = typed == correct
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
        viewModelScope.launch {
            UserStatsStore.recordAttempt(
                context = getApplication(),
                subject = "rus",
                typeNumber = cur.typeNumber,
                subtypeId = null,
                isCorrect = isRight,
            )
            StreakStore.onProblemSolved(getApplication())
        }
        _state.value = cur.copy(
            state = BlankInputState.Verdict(
                userAnswer = typed,
                correctAnswer = correct,
                isRight = isRight,
            ),
        )
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
    vm: WordBlankTrainerViewModel = viewModel(),
) {
    LaunchedEffect(typeNumber, defaultOrder) { vm.start(typeNumber, defaultOrder) }
    val st by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current

    // Авто-переход при правильном.
    LaunchedEffect(st.state, st.position) {
        val v = st.state
        if (v is BlankInputState.Verdict && v.isRight) {
            delay(1000L)
            vm.goNext()
        }
    }

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
                else -> Body(st = st, vm = vm)
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
}

@Composable
private fun CenteredText(s: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(s, color = LabelSecondary)
    }
}

@Composable
private fun Body(st: WordBlankUi, vm: WordBlankTrainerViewModel) {
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(st.position) {
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            when {
                                totalDrag < -swipeThresholdPx -> vm.goNext()
                                totalDrag > swipeThresholdPx -> vm.goPrev()
                            }
                            totalDrag = 0f
                        },
                        onHorizontalDrag = { _, dx -> totalDrag += dx },
                    )
                },
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
