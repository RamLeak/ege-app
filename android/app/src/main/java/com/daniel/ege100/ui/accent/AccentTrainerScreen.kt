package com.daniel.ege100.ui.accent

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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.AccentErrorsStore
import com.daniel.ege100.data.AccentWord
import com.daniel.ege100.data.AccentWordsRepository
import com.daniel.ege100.data.AttemptLogEntity
import com.daniel.ege100.data.StreakStore
import com.daniel.ege100.data.TrainerProgress
import com.daniel.ege100.data.TrainerProgressStore
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserStatsStore
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.ResumeBottomSheet
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------- модель ----------------------

enum class Order { Alphabetical, Random }

/**
 * Stage 3 polish 3 (#2): SyllableTapState вместо LetterTapState.
 *
 * Поскольку наш syllabify даёт в каждом слоге ровно одну ударную гласную,
 * выбирать букву отдельно — избыточно. Тап на слог = FirstTap (подсветка),
 * второй тап на тот же слог = Verdict.
 */
sealed class SyllableTapState {
    data object None : SyllableTapState()
    data class FirstTap(val syllableIndex: Int) : SyllableTapState()
    data class Verdict(
        val selectedSyllable: Int,
        val correctSyllable: Int,
        val isRight: Boolean,
    ) : SyllableTapState()
}

data class AccentTrainerUi(
    val loading: Boolean = true,
    val title: String = "",
    val words: List<AccentWord> = emptyList(),
    val syllablesByWord: Map<Int, List<Syllable>> = emptyMap(),
    val order: Order = Order.Alphabetical,
    val orderedIndices: List<Int> = emptyList(),
    val position: Int = 0,
    val tap: SyllableTapState = SyllableTapState.None,
    /** Если != null — показать ResumeBottomSheet с этой позицией. */
    val pendingResume: TrainerProgress? = null,
) {
    val total: Int get() = orderedIndices.size
    val currentWord: AccentWord?
        get() = orderedIndices.getOrNull(position)?.let { words.getOrNull(it) }
    val currentSyllables: List<Syllable>
        get() {
            val idx = orderedIndices.getOrNull(position) ?: return emptyList()
            return syllablesByWord[idx].orEmpty()
        }
}

private fun findSyllableContaining(syllables: List<Syllable>, letterIndex: Int): Int {
    val i = syllables.indexOfFirst { letterIndex in it.startInWord..it.endInWord }
    return if (i >= 0) i else 0
}

class AccentTrainerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(AccentTrainerUi())
    val state: StateFlow<AccentTrainerUi> = _state.asStateFlow()

    private var categoryId: String? = null
    private var defaultOrder: String = "alphabetical"
    private var initialized = false

    // Phase 4 Stage P4-C2 part А (Convention #56) — auto-advance Job.
    // Раньше delay 1000ms был в LaunchedEffect Screen'а — если пользователь
    // открывал AI bottom-sheet, экран всё равно через 1 сек переключал слово
    // и контекст AI становился «не про то слово». Сейчас Job — на VM, можно
    // отменить через onAskAiOpened().
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

    /** Возвращает true если ответ на текущем слове был правильным. */
    fun lastVerdictWasRight(): Boolean =
        (_state.value.tap as? SyllableTapState.Verdict)?.isRight == true

    fun onAskAiClosed(wasRight: Boolean) {
        if (wasRight && _state.value.tap is SyllableTapState.Verdict) {
            scheduleAutoAdvance(delayMs = 500L)
        }
    }

    /** Stage 5 part А: trainerId для TrainerProgressStore. */
    private fun trainerId(): String = when {
        categoryId != null -> "accent_${categoryId}"
        defaultOrder == "random" -> "accent_all_random"
        else -> "accent_all_alphabetical"
    }

    fun start(categoryId: String?, defaultOrder: String) {
        if (initialized && this.categoryId == categoryId) return
        initialized = true
        this.categoryId = categoryId
        this.defaultOrder = defaultOrder
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val title = AccentWordsRepository.categoryTitle(ctx, categoryId)
            val words = AccentWordsRepository.loadCategory(ctx, categoryId)
            val order = if (defaultOrder == "random") Order.Random else Order.Alphabetical
            val sorted = words.indices.sortedBy { words[it].word }
            val indices = if (order == Order.Random) words.indices.shuffled() else sorted
            val syl = words.indices.associateWith { syllabify(words[it].word) }

            val saved = TrainerProgressStore.get(ctx, trainerId())
            val savedValid = saved != null &&
                saved.total == indices.size &&
                saved.position in 1 until indices.size &&
                saved.indices.size == indices.size

            _state.value = AccentTrainerUi(
                loading = false,
                title = title,
                words = words,
                syllablesByWord = syl,
                order = order,
                orderedIndices = indices,
                position = 0,
                tap = SyllableTapState.None,
                pendingResume = if (savedValid) saved else null,
            )
        }
    }

    /** Принять «Продолжить» из ResumeBottomSheet. */
    fun acceptResume() {
        val cur = _state.value
        val saved = cur.pendingResume ?: return
        val savedOrder = if (saved.order == "random") Order.Random else Order.Alphabetical
        _state.value = cur.copy(
            order = savedOrder,
            orderedIndices = saved.indices,
            position = saved.position,
            tap = SyllableTapState.None,
            pendingResume = null,
        )
    }

    /** Принять «Начать сначала» из ResumeBottomSheet. */
    fun acceptStartOver() {
        val cur = _state.value
        viewModelScope.launch {
            TrainerProgressStore.clear(getApplication(), trainerId())
        }
        _state.value = cur.copy(
            position = 0,
            tap = SyllableTapState.None,
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

    fun toggleOrder() {
        val cur = _state.value
        val newOrder = if (cur.order == Order.Alphabetical) Order.Random else Order.Alphabetical
        val newIndices = if (newOrder == Order.Random) cur.words.indices.shuffled()
                         else cur.words.indices.sortedBy { cur.words[it].word }
        _state.value = cur.copy(
            order = newOrder,
            orderedIndices = newIndices,
            position = 0,
            tap = SyllableTapState.None,
        )
        clearProgress()
    }

    fun tapSyllable(syllableIdx: Int) {
        val cur = _state.value
        val word = cur.currentWord ?: return
        val syllables = cur.currentSyllables
        val syl = syllables.getOrNull(syllableIdx) ?: return
        // Слоги без гласных (теоретический edge case) не реагируют на тап.
        val anyVowel = (syl.startInWord..syl.endInWord).any { word.word[it] in ACCENT_VOWELS }
        if (!anyVowel) return

        val next = when (val s = cur.tap) {
            is SyllableTapState.None -> SyllableTapState.FirstTap(syllableIdx)
            is SyllableTapState.FirstTap -> {
                if (s.syllableIndex == syllableIdx) {
                    val correct = findSyllableContaining(syllables, word.stressed_index)
                    val isRight = syllableIdx == correct
                    // Phase 4 Stage P4-D2 part Г (Convention #67) — breadcrumb.
                    com.daniel.ege100.data.BreadcrumbLog.add(
                        "AccentTap: word='${word.word}', syl=$syllableIdx, correct=$isRight",
                    )
                    if (!isRight) {
                        viewModelScope.launch {
                            AccentErrorsStore.recordError(getApplication(), word.word)
                        }
                        // Phase 5 Stage E2 — автосоздание SRS-карточки на ошибку.
                        // subtype = реальная категория слова (nouns/verbs/...) — чтобы
                        // карточка не дублировалась между режимом «категория» и «все слова».
                        viewModelScope.launch {
                            runCatching {
                                val ctx = getApplication<Application>()
                                val sub = AccentWordsRepository.categoryFor(ctx, word.word) ?: "default"
                                com.daniel.ege100.srs.SrsRepository.addCardOnMistake(
                                    context = ctx,
                                    word = word.word,
                                    kind = "accent",
                                    subtype = sub,
                                )
                            }
                        }
                    }
                    // Phase 3 part В + Г: тренажёр №4 ударений → subject="rus",
                    // typeNumber=4, subtypeId=null (нет подвидов у тренажёра).
                    // Phase 3 Stage C: + запись в attempt_log с source=accent_trainer.
                    viewModelScope.launch {
                        UserStatsStore.recordAttempt(
                            context = getApplication(),
                            subject = "rus",
                            typeNumber = 4,
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
                                typeNumber = 4,
                                subtypeId = null,
                                isCorrect = isRight,
                                durationMs = 0L,
                                timestamp = System.currentTimeMillis(),
                                source = "accent_trainer",
                            ),
                        )
                    }
                    SyllableTapState.Verdict(
                        selectedSyllable = syllableIdx,
                        correctSyllable = correct,
                        isRight = isRight,
                    )
                } else {
                    SyllableTapState.FirstTap(syllableIdx)
                }
            }
            is SyllableTapState.Verdict -> s
        }
        _state.value = cur.copy(tap = next)
        // Phase 4 Stage P4-C2 part А (Convention #56) — auto-advance Job
        // на ViewModel вместо LaunchedEffect-delay в Screen. Запускается
        // только когда Verdict.isRight; AI-кнопка может его отменить
        // через onAskAiOpened().
        if (next is SyllableTapState.Verdict && next.isRight) {
            scheduleAutoAdvance(delayMs = 1000L)
        }
    }

    fun goNext() {
        val cur = _state.value
        if (cur.position + 1 >= cur.total) {
            // Достигли последнего слова — оставляем position на нём, но
            // прогресс ЧИСТИМ (следующий заход начнётся с нуля без sheet).
            clearProgress()
            return
        }
        val newPos = cur.position + 1
        _state.value = cur.copy(position = newPos, tap = SyllableTapState.None)
        if (newPos == cur.total - 1) {
            // Перешли на последнее слово — будущий «верный ответ» → goNext()
            // → останется на последнем, прогресс сбросим там. А пока сохраняем.
            persistProgress()
        } else {
            persistProgress()
        }
    }

    fun goPrev() {
        val cur = _state.value
        if (cur.position == 0) return
        _state.value = cur.copy(position = cur.position - 1, tap = SyllableTapState.None)
        persistProgress()
    }
}

// ---------------------- UI ----------------------

@Composable
fun AccentTrainerScreen(
    categoryId: String?,
    defaultOrder: String,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    onOpenAiSettings: () -> Unit = {},
    vm: AccentTrainerViewModel = viewModel(),
) {
    LaunchedEffect(categoryId, defaultOrder) { vm.start(categoryId, defaultOrder) }
    val st by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showAi by remember { mutableStateOf(false) }

    // Phase 4 Stage P4-C2 part А (Convention #56) — auto-advance перенесён
    // в ViewModel.scheduleAutoAdvance(); здесь только haptic feedback.

    LaunchedEffect(st.tap) {
        when (val v = st.tap) {
            is SyllableTapState.Verdict -> {
                if (v.isRight) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                else haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = st.title.ifBlank { "Ударения" },
                subtitle = if (st.total > 0) "${st.position + 1} из ${st.total}" else null,
                onBack = onBack,
                rightContent = {
                    OrderToggleChip(order = st.order, onClick = { vm.toggleOrder() })
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
                st.loading -> CenteredText("Загрузка…")
                st.currentWord == null -> CenteredText("Слова не найдены")
                else -> TrainerBody(st = st, vm = vm, onAiClick = { showAi = true })
            }
        }
    }

    val pending = st.pendingResume
    if (pending != null) {
        ResumeBottomSheet(
            trainerTitle = st.title.ifBlank { "Ударения" },
            savedPosition = pending.position,
            total = pending.total,
            onResume = { vm.acceptResume() },
            onStartOver = { vm.acceptStartOver() },
            onDismiss = { vm.acceptStartOver() },
        )
    }

    // Phase 4 Stage P4-C2 part А (Convention #56) — отменяем auto-advance
    // при открытии AI-окна, иначе экран переключится через 1 сек и
    // контекст AI станет «не про то слово».
    LaunchedEffect(showAi) {
        if (showAi) vm.onAskAiOpened()
    }

    // Phase 4 Stage P4-D5 fix (Convention #86) — ExplanationBottomSheet вместо
    // AskAiBottomSheet. Делает pre-gen lookup в trainer_explanations по
    // (word=word.word, kind="accent"), затем 4-табный UI (Почему/Правило/
    // Примеры/Запомнить). word.word в JSON хранится в lowercase без ударений
    // (`"аэропорты"`/`"банты"`), точно как в БД — mismatch ключей невозможен.
    if (showAi) {
        val word = st.currentWord
        val verdict = st.tap as? SyllableTapState.Verdict
        if (word != null && verdict != null) {
            val syllables = st.currentSyllables
            val correctSyllable = syllables.getOrNull(verdict.correctSyllable)?.text.orEmpty()
            val selectedSyllable = syllables.getOrNull(verdict.selectedSyllable)?.text.orEmpty()
            val highlighted = highlightedWord(word.word, word.stressed_index)
            val fallbackContext = buildString {
                append("Слово: ${word.word}. ")
                append("Правильное ударение на слог «$correctSyllable» ($highlighted). ")
                if (!verdict.isRight) {
                    append("Пользователь ошибочно поставил ударение на слог «$selectedSyllable». ")
                } else {
                    append("Пользователь ответил правильно. ")
                }
                append("Объясни почему ударение именно здесь, какое правило, дай 3-5 похожих ")
                append("слов с тем же типом ударения и мнемонику для запоминания.")
            }
            com.daniel.ege100.ui.ai.ExplanationBottomSheet(
                word = word.word,
                kind = "accent",
                fallbackContext = fallbackContext,
                onDismiss = {
                    showAi = false
                    // Convention #56 — resume auto-advance через 500мс если answer был верным.
                    vm.onAskAiClosed(verdict.isRight)
                },
                onOpenSettings = {
                    showAi = false
                    onOpenAiSettings()
                },
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
private fun TrainerBody(
    st: AccentTrainerUi,
    vm: AccentTrainerViewModel,
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
                .padding(top = 4.dp, bottom = 28.dp),
        )

        // Phase 4 Stage P4-C3 part В2 (Convention #63) — единый swipe-механизм
        // с резинкой /3 на границах + visual animatable. Старый
        // detectHorizontalDragGestures с фиксированным threshold заменён.
        // onSwipeStart отменяет pendingAdvanceJob чтобы auto-advance не
        // сработал во время свайпа.
        com.daniel.ege100.ui.common.SwipeableProblemContent(
            hasPrev = st.position > 0,
            hasNext = st.position < st.total - 1,
            onPrev = { vm.goPrev() },
            onNext = { vm.goNext() },
            onSwipeStart = { vm.onAskAiOpened() },  // отмена auto-advance
            modifier = Modifier.weight(1f),
        ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Phase 3 part А3 — плавнее переход: spring 0.85f + StiffnessMediumLow
            // + fade 280ms. Меньше bounce, дольше движение — ощущается «как iOS».
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
                    SyllableRow(
                        word = word,
                        syllables = st.currentSyllables,
                        tap = st.tap,
                        onSyllableTap = vm::tapSyllable,
                    )
                    Spacer(Modifier.height(40.dp))
                    Verdict(st.tap, word, st.currentSyllables)
                }
            }
        }
        }  // end SwipeableProblemContent

        // Phase 4 Stage P4-D5 fix (Convention #86) — переход с AskAiBottomSheet
        // на ExplanationBottomSheet. Кнопка «📖 Объяснение» делает pre-gen lookup
        // в trainer_explanations (229 accent слов в БД, kind="accent") → если нет,
        // fallback на онлайн AI с структурированными 4 табами.
        if (st.tap is SyllableTapState.Verdict) {
            com.daniel.ege100.ui.common.SecondaryButton(
                text = "📖 Объяснение",
                onClick = onAiClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        SwipeHint(
            tap = st.tap,
            isFirstWord = st.position == 0,
            isLastWord = st.position == st.total - 1,
        )
        Spacer(Modifier.height(20.dp))
    }
}

// ---------------------- Syllables ----------------------

/**
 * Адаптивный размер шрифта по числу слогов (правка А2). До 4 слогов —
 * крупный, 5 слогов чуть меньше, 6+ — компактный. Дополнительно FlowRow
 * переносит на новую строку если всё-таки не помещается (например
 * «вероисповедание» с 8 слогами на узком экране).
 */
private fun syllableFontSizeSp(syllableCount: Int): Int = when {
    syllableCount <= 4 -> 32
    syllableCount == 5 -> 28
    else -> 24
}

private fun syllableCellHeightDp(syllableCount: Int): Int = when {
    syllableCount <= 4 -> 72
    syllableCount == 5 -> 64
    else -> 56
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SyllableRow(
    word: AccentWord,
    syllables: List<Syllable>,
    tap: SyllableTapState,
    onSyllableTap: (Int) -> Unit,
) {
    val fontSize = syllableFontSizeSp(syllables.size)
    val cellHeight = syllableCellHeightDp(syllables.size)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        syllables.forEachIndexed { idx, syl ->
            SyllableCell(
                wordText = word.word,
                syllable = syl,
                syllableIndex = idx,
                stressedIndex = word.stressed_index,
                tap = tap,
                fontSizeSp = fontSize,
                heightDp = cellHeight,
                onTap = { onSyllableTap(idx) },
            )
        }
    }
}

private enum class SyllableVisual { Idle, Selected, Correct, Wrong }

private fun syllableVisualFor(idx: Int, tap: SyllableTapState): SyllableVisual = when (tap) {
    is SyllableTapState.None -> SyllableVisual.Idle
    is SyllableTapState.FirstTap ->
        if (tap.syllableIndex == idx) SyllableVisual.Selected else SyllableVisual.Idle
    is SyllableTapState.Verdict -> when {
        tap.isRight && idx == tap.correctSyllable -> SyllableVisual.Correct
        !tap.isRight && idx == tap.selectedSyllable -> SyllableVisual.Wrong
        !tap.isRight && idx == tap.correctSyllable -> SyllableVisual.Correct
        else -> SyllableVisual.Idle
    }
}

@Composable
private fun SyllableCell(
    wordText: String,
    syllable: Syllable,
    syllableIndex: Int,
    stressedIndex: Int,
    tap: SyllableTapState,
    fontSizeSp: Int,
    heightDp: Int,
    onTap: () -> Unit,
) {
    val visual = syllableVisualFor(syllableIndex, tap)
    val targetScale = when (visual) {
        SyllableVisual.Correct, SyllableVisual.Wrong -> 1.06f
        SyllableVisual.Selected -> 1.04f
        SyllableVisual.Idle -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "syllable-scale",
    )
    val (bg, border, fg) = when (visual) {
        SyllableVisual.Idle -> Triple(BgElevated2, Color.Transparent, Label)
        SyllableVisual.Selected -> Triple(Color(0x1F0A84FF), SystemBlue, SystemBlue)
        SyllableVisual.Correct -> Triple(SystemGreenTint, SystemGreen, SystemGreen)
        SyllableVisual.Wrong -> Triple(SystemRedTint, SystemRed, SystemRed)
    }

    val anyVowel = (syllable.startInWord..syllable.endInWord)
        .any { wordText[it] in ACCENT_VOWELS }
    val enabled = anyVowel && tap !is SyllableTapState.Verdict
    val showAccentOnCorrect = visual == SyllableVisual.Correct &&
        stressedIndex in syllable.startInWord..syllable.endInWord
    // Внутренний горизонтальный padding пропорциональный шрифту — для крупных
    // слогов оставляем 14dp, для компактных уменьшаем.
    val innerPaddingDp = if (fontSizeSp >= 32) 14 else if (fontSizeSp >= 28) 12 else 10

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(heightDp.dp)
            .widthIn(min = 48.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .border(1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled) { onTap() }
            .padding(horizontal = innerPaddingDp.dp),
    ) {
        if (showAccentOnCorrect) {
            SyllableWithAccent(
                text = syllable.text,
                accentLocalIndex = stressedIndex - syllable.startInWord,
                fontColor = fg,
                fontSizeSp = fontSizeSp,
            )
        } else {
            Text(
                text = syllable.text,
                fontSize = fontSizeSp.sp,
                fontWeight = if (visual != SyllableVisual.Idle) FontWeight.Bold else FontWeight.SemiBold,
                color = fg.copy(alpha = if (anyVowel) 1f else 0.5f),
            )
        }
    }
}

@Composable
private fun SyllableWithAccent(
    text: String,
    accentLocalIndex: Int,
    fontColor: Color,
    fontSizeSp: Int,
) {
    val accentSpacerHeightDp = (fontSizeSp * 0.7f).toInt()
    Row(verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (i == accentLocalIndex) {
                    Text(
                        "´",
                        color = fontColor,
                        fontSize = (fontSizeSp - 10).coerceAtLeast(14).sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Spacer(Modifier.height(accentSpacerHeightDp.dp))
                }
                Text(
                    text = ch.toString(),
                    fontSize = fontSizeSp.sp,
                    fontWeight = if (i == accentLocalIndex) FontWeight.Bold else FontWeight.SemiBold,
                    color = fontColor,
                )
            }
        }
    }
}

// ---------------------- Verdict / Hint ----------------------

@Composable
private fun Verdict(tap: SyllableTapState, word: AccentWord, syllables: List<Syllable>) {
    when (tap) {
        is SyllableTapState.None -> {
            Text(
                text = "Тапни на слог с ударением, тапни ещё раз — чтобы подтвердить",
                color = LabelSecondary,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        is SyllableTapState.FirstTap -> {
            Text(
                text = "Тапни ещё раз, чтобы подтвердить",
                color = LabelTertiary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
            )
        }
        is SyllableTapState.Verdict -> {
            AnimatedVisibility(
                visible = true,
                enter = scaleIn(
                    spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                    initialScale = 0.5f,
                ) + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (tap.isRight) "✓ Верно" else "✕ Неверно",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (tap.isRight) SystemGreen else SystemRed,
                    )
                    if (!tap.isRight) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Правильно: ${highlightedWord(word.word, word.stressed_index)}",
                            fontSize = 17.sp,
                            color = Label,
                        )
                    }
                }
            }
        }
    }
}

private fun highlightedWord(word: String, idx: Int): String =
    buildString {
        word.forEachIndexed { i, ch -> append(if (i == idx) ch.uppercaseChar() else ch) }
    }

@Composable
private fun SwipeHint(tap: SyllableTapState, isFirstWord: Boolean, isLastWord: Boolean) {
    val text = when {
        tap is SyllableTapState.Verdict && !tap.isRight ->
            "Свайпни влево для следующего слова"
        tap is SyllableTapState.Verdict && tap.isRight ->
            "Готово · следующее через секунду"
        else ->
            buildString {
                if (!isFirstWord) append("← Свайп вправо: предыдущее")
                if (!isFirstWord && !isLastWord) append("    ")
                if (!isLastWord) append("Свайп влево: следующее →")
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
