package com.daniel.ege100.ui.srs

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.daniel.ege100.data.AppSettingsStore
import com.daniel.ege100.data.ParonymsRepository
import com.daniel.ege100.data.PleonasmsRepository
import com.daniel.ege100.data.WordBlanksRepository
import com.daniel.ege100.srs.SrsCardEntity
import com.daniel.ege100.srs.SrsRepository
import com.daniel.ege100.srs.SrsStreakStore
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SimpleMarkdownRenderer
import com.daniel.ege100.ui.common.cardsWord
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.BgElevated2
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import com.daniel.ege100.ui.theme.SystemRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Phase 5 Stage E3 — экран SRS-повторения.
 *
 * State machine:
 *   Loading → (cards>0) → Front → Back → Grade(0/3/4/5) → next card | Done
 *           ↘ (cards=0) → Empty («Сегодня нет карточек 🎉»)
 *
 * Practice state (задача из тренажёра) добавится в E4 — здесь Back ведёт
 * сразу на Grade.
 */
sealed class SrsReviewState {
    data object Loading : SrsReviewState()
    data object Empty : SrsReviewState()
    data class Front(val cardIndex: Int) : SrsReviewState()
    data class Back(val cardIndex: Int) : SrsReviewState()
    /**
     * Phase 5 Stage E4 — Practice state между Back и Grade.
     * `userInput` пуст пока пользователь не нажал «Проверить».
     * `verdict == null` → input form; `true/false` → результат.
     */
    data class Practice(
        val cardIndex: Int,
        val userInput: String = "",
        val verdict: Boolean? = null,
    ) : SrsReviewState()
    data class Done(val total: Int) : SrsReviewState()
}

/**
 * Phase 5 Stage E4 — данные тренажёра для Practice state, извлекаются
 * lookup'ом по (kind, subtype, word) при start(). Если для карточки данные
 * не нашлись (rare race condition), Practice пропускается → сразу Grade.
 *
 *   answer — что должен напечатать пользователь.
 *   prompt — фраза-контекст ("слово с пропуском a..гитатор", или sentence).
 */
data class PracticeData(
    val answer: String,
    val prompt: String,
)

data class SrsReviewUi(
    val cards: List<SrsCardEntity> = emptyList(),
    val texts: Map<Long, SrsRepository.CardTexts> = emptyMap(),
    val practiceByCard: Map<Long, PracticeData> = emptyMap(),
    val practiceEnabled: Boolean = true,
    val state: SrsReviewState = SrsReviewState.Loading,
)

class SrsReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(SrsReviewUi())
    val state: StateFlow<SrsReviewUi> = _state.asStateFlow()

    private var initialized = false

    fun start() {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val settings = AppSettingsStore.snapshot(ctx)
            val due = runCatching { SrsRepository.getDueCards(ctx, settings.srsDailyLimit) }
                .getOrDefault(emptyList())
            if (due.isEmpty()) {
                _state.value = SrsReviewUi(state = SrsReviewState.Empty)
                return@launch
            }
            val textsMap = mutableMapOf<Long, SrsRepository.CardTexts>()
            val practiceMap = mutableMapOf<Long, PracticeData>()
            due.forEach { card ->
                val t = runCatching { SrsRepository.getTextsForCard(ctx, card) }
                    .getOrDefault(SrsRepository.CardTexts(null, null, null, null))
                textsMap[card.id] = t

                // Phase 5 Stage E4 — lookup тренажёрных данных для Practice.
                if (settings.srsPracticeAfterCard) {
                    val p = runCatching { loadPracticeData(ctx, card) }.getOrNull()
                    if (p != null) practiceMap[card.id] = p
                }
            }
            _state.value = SrsReviewUi(
                cards = due,
                texts = textsMap,
                practiceByCard = practiceMap,
                practiceEnabled = settings.srsPracticeAfterCard,
                state = SrsReviewState.Front(0),
            )
        }
    }

    /**
     * Phase 5 Stage E4 — pull данных для Practice из репозиториев тренажёров.
     * Поддерживаются word_blank, paronym, pleonasm. Для accent возвращает
     * null (нет компактного «напечатать ответ» для тренажёра ударений).
     */
    private suspend fun loadPracticeData(
        ctx: android.content.Context,
        card: SrsCardEntity,
    ): PracticeData? = when (card.kind) {
        "word_blank" -> {
            val typeNumber = card.subtype.removePrefix("t").toIntOrNull()
            if (typeNumber == null) null else {
                val type = WordBlanksRepository.loadType(ctx, typeNumber)
                val w = type?.words?.firstOrNull { it.full.equals(card.word, ignoreCase = true) }
                if (w == null) null else PracticeData(
                    answer = w.answer.lowercase(),
                    prompt = "Слово с пропуском: ${w.masked.replace("..", "_")}",
                )
            }
        }
        "paronym" -> {
            val items = ParonymsRepository.load(ctx)
            val item = items.firstOrNull {
                "${it.wrong_word}/${it.correct_word}".lowercase() == card.word.lowercase()
            }
            if (item == null) null else PracticeData(
                answer = item.correct_word.lowercase(),
                prompt = item.sentence,
            )
        }
        "pleonasm" -> {
            val items = PleonasmsRepository.load(ctx)
            val item = items.firstOrNull { it.extra_word.equals(card.word, ignoreCase = true) }
            if (item == null) null else PracticeData(
                answer = item.extra_word.lowercase(),
                prompt = item.sentence,
            )
        }
        else -> null
    }

    fun showAnswer() {
        val s = _state.value
        val front = s.state as? SrsReviewState.Front ?: return
        _state.value = s.copy(state = SrsReviewState.Back(front.cardIndex))
    }

    /** Phase 5 Stage E4 — переход из Back в Practice (либо сразу в Grade если данных нет). */
    fun startPractice() {
        val s = _state.value
        val back = s.state as? SrsReviewState.Back ?: return
        _state.value = s.copy(state = SrsReviewState.Practice(back.cardIndex))
    }

    fun setPracticeInput(value: String) {
        val s = _state.value
        val p = s.state as? SrsReviewState.Practice ?: return
        if (p.verdict != null) return  // после проверки ввод заморожен
        _state.value = s.copy(state = p.copy(userInput = value))
    }

    fun checkPractice() {
        val s = _state.value
        val p = s.state as? SrsReviewState.Practice ?: return
        val card = s.cards.getOrNull(p.cardIndex) ?: return
        val data = s.practiceByCard[card.id] ?: return
        val normalized = p.userInput.trim().lowercase().replace('ё', 'е')
        val target = data.answer.lowercase().replace('ё', 'е')
        val isRight = normalized == target
        _state.value = s.copy(state = p.copy(verdict = isRight))
    }

    fun submitGrade(grade: Int) {
        val s = _state.value
        val cardIdx = when (val cur = s.state) {
            is SrsReviewState.Back -> cur.cardIndex
            is SrsReviewState.Practice -> cur.cardIndex
            else -> return
        }
        val card = s.cards.getOrNull(cardIdx) ?: return
        viewModelScope.launch {
            runCatching {
                SrsRepository.submitReview(
                    context = getApplication(),
                    cardId = card.id,
                    grade = grade,
                )
            }
            // Phase 5 Stage E4 (§1.7) — streak инкрементируется только на успешную оценку.
            if (grade >= 3) {
                runCatching { SrsStreakStore.onSuccessfulReview(getApplication()) }
            }
        }
        val nextIdx = cardIdx + 1
        _state.value = if (nextIdx >= s.cards.size) {
            s.copy(state = SrsReviewState.Done(s.cards.size))
        } else {
            s.copy(state = SrsReviewState.Front(nextIdx))
        }
    }
}

@Composable
fun SrsReviewScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    vm: SrsReviewViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.start() }
    val st by vm.state.collectAsState()
    // Phase 5 Stage E4 — текущий streak для DoneState.
    val srsStreak by SrsStreakStore.stateFlow(androidx.compose.ui.platform.LocalContext.current)
        .collectAsState(initial = com.daniel.ege100.srs.SrsStreakState())

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Повторение",
                subtitle = subtitleFor(st),
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
            when (val s = st.state) {
                SrsReviewState.Loading -> CenteredText("Загрузка карточек…")
                SrsReviewState.Empty -> EmptyState(onBack = onBack)
                is SrsReviewState.Front -> FrontView(
                    cards = st.cards,
                    cardIndex = s.cardIndex,
                    onShowAnswer = vm::showAnswer,
                )
                is SrsReviewState.Back -> {
                    val card = st.cards.getOrNull(s.cardIndex)
                    val hasPractice = card != null &&
                        st.practiceEnabled &&
                        st.practiceByCard.containsKey(card.id)
                    BackView(
                        cards = st.cards,
                        cardIndex = s.cardIndex,
                        texts = st.texts,
                        hasPractice = hasPractice,
                        onPractice = vm::startPractice,
                        onGrade = vm::submitGrade,
                    )
                }
                is SrsReviewState.Practice -> {
                    val card = st.cards.getOrNull(s.cardIndex)
                    val data = card?.let { st.practiceByCard[it.id] }
                    if (data == null) {
                        // race condition fallback — данные исчезли, скипаем в Grade
                        LaunchedEffect(s.cardIndex) { vm.submitGrade(3) }
                        CenteredText("…")
                    } else {
                        PracticeView(
                            cards = st.cards,
                            cardIndex = s.cardIndex,
                            data = data,
                            userInput = s.userInput,
                            verdict = s.verdict,
                            onInputChange = vm::setPracticeInput,
                            onCheck = vm::checkPractice,
                            onGrade = vm::submitGrade,
                        )
                    }
                }
                is SrsReviewState.Done -> DoneState(
                    total = s.total,
                    streak = srsStreak.currentStreak,
                    onBack = onBack,
                )
            }
        }
    }
}

private fun subtitleFor(ui: SrsReviewUi): String = when (val s = ui.state) {
    SrsReviewState.Loading -> "Загрузка…"
    SrsReviewState.Empty -> ""
    is SrsReviewState.Front -> "${s.cardIndex + 1} из ${ui.cards.size}"
    is SrsReviewState.Back -> "${s.cardIndex + 1} из ${ui.cards.size}"
    is SrsReviewState.Practice -> "${s.cardIndex + 1} из ${ui.cards.size} · практика"
    is SrsReviewState.Done -> "Готово · ${s.total} ${cardsWord(s.total)}"
}

@Composable
private fun CenteredText(s: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(s, color = LabelSecondary)
    }
}

@Composable
private fun EmptyState(onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "🎉", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Сегодня нет карточек на повторение",
                fontSize = 18.sp,
                color = Label,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Возвращайся завтра или после ошибок в тренажёрах",
                fontSize = 14.sp,
                color = LabelSecondary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = "На главный", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun DoneState(total: Int, streak: Int, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "✓", fontSize = 64.sp, color = SystemGreen)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Готово!",
                fontSize = 26.sp,
                color = Label,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$total ${cardsWord(total)} · $total из $total",
                fontSize = 15.sp,
                color = LabelSecondary,
            )
            if (streak >= 1) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "🔥 SRS-streak: $streak ${com.daniel.ege100.ui.common.daysWord(streak)}",
                    fontSize = 16.sp,
                    color = SystemOrange,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(24.dp))
            PrimaryButton(text = "На главный", onClick = onBack, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun FrontView(
    cards: List<SrsCardEntity>,
    cardIndex: Int,
    onShowAnswer: () -> Unit,
) {
    val card = cards.getOrNull(cardIndex) ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        AppleProgressBar(
            progress = (cardIndex + 1).toFloat() / cards.size.coerceAtLeast(1),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 20.dp),
        )
        AnimatedContent(
            targetState = cardIndex,
            transitionSpec = {
                (slideInHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { it } +
                    fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) { -it / 3 } +
                        fadeOut(tween(280)))
            },
            label = "srs-front",
            modifier = Modifier.weight(1f),
        ) { _ ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize(),
            ) {
                Text(
                    text = questionFor(card.kind),
                    fontSize = 14.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Text(
                    text = card.word,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                KindBadge(kind = card.kind)
            }
        }
        PrimaryButton(
            text = "Показать ответ",
            onClick = onShowAnswer,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun BackView(
    cards: List<SrsCardEntity>,
    cardIndex: Int,
    texts: Map<Long, SrsRepository.CardTexts>,
    hasPractice: Boolean,
    onPractice: () -> Unit,
    onGrade: (Int) -> Unit,
) {
    val card = cards.getOrNull(cardIndex) ?: return
    val t = texts[card.id]
    val scrollState = rememberScrollState()
    Column(modifier = Modifier.fillMaxSize()) {
        AppleProgressBar(
            progress = (cardIndex + 1).toFloat() / cards.size.coerceAtLeast(1),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 14.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = card.word,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                    modifier = Modifier.weight(1f),
                )
                KindBadge(kind = card.kind)
            }
            Spacer(Modifier.height(14.dp))

            if (t == null || t.isEmpty) {
                AppleCard(paddingDp = 18) {
                    Text(
                        text = "Объяснение для этого слова пока не сгенерировано.\n" +
                            "Открой тренажёр и нажми «📖 Объяснение» — там работает онлайн-AI fallback.",
                        fontSize = 14.sp,
                        color = LabelSecondary,
                    )
                }
            } else {
                Section(title = "Почему именно так", body = t.explanation)
                Section(title = "Правило ЕГЭ", body = t.rule)
                Section(title = "Похожие примеры", body = t.examples)
                Section(title = "Как запомнить", body = t.mnemonic)
            }
            Spacer(Modifier.height(18.dp))
        }
        if (hasPractice) {
            SecondaryButton(
                text = "🎯 Закрепить задачей",
                onClick = onPractice,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
        }
        GradeRow(onGrade = onGrade)
        Spacer(Modifier.height(20.dp))
    }
}

/**
 * Phase 5 Stage E4 — Practice state. Простой recall: пользователь печатает
 * ответ (буква для word_blank, слово для paronym/pleonasm), нажимает
 * «Проверить», видит ✓/✗ с правильным ответом, дальше выставляет Grade.
 *
 * Это упрощённая версия настоящего тренажёра — без auto-advance, без
 * нескольких подвидов UI per kind. Цель — заставить пользователя
 * проговорить ответ в голове перед самооценкой, что усиливает recall
 * по сравнению с просто «прочитал правило → оценил себя».
 */
@Composable
private fun PracticeView(
    cards: List<SrsCardEntity>,
    cardIndex: Int,
    data: PracticeData,
    userInput: String,
    verdict: Boolean?,
    onInputChange: (String) -> Unit,
    onCheck: () -> Unit,
    onGrade: (Int) -> Unit,
) {
    val card = cards.getOrNull(cardIndex) ?: return
    Column(modifier = Modifier.fillMaxSize()) {
        AppleProgressBar(
            progress = (cardIndex + 1).toFloat() / cards.size.coerceAtLeast(1),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 16.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Закрепи задачей",
                    fontSize = 13.sp,
                    color = LabelSecondary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                KindBadge(kind = card.kind)
            }
            Spacer(Modifier.height(14.dp))
            AppleCard(paddingDp = 18) {
                Column {
                    Text(
                        text = data.prompt,
                        fontSize = 17.sp,
                        color = Label,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Напечатай правильный ответ:",
                        fontSize = 13.sp,
                        color = LabelSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    IosTextField(
                        value = userInput,
                        onValueChange = onInputChange,
                        placeholder = "ответ",
                    )
                }
            }
            if (verdict != null) {
                Spacer(Modifier.height(14.dp))
                val color = if (verdict) SystemGreen else SystemRed
                Text(
                    text = if (verdict) "✓ Верно!" else "✗ Правильный ответ: ${data.answer}",
                    color = color,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(18.dp))
        }
        if (verdict == null) {
            PrimaryButton(
                text = "Проверить",
                onClick = onCheck,
                enabled = userInput.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            GradeRow(onGrade = onGrade)
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Section(title: String, body: String?) {
    if (body.isNullOrBlank()) return
    Spacer(Modifier.height(12.dp))
    AppleCard(paddingDp = 18) {
        Column {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = LabelSecondary,
            )
            Spacer(Modifier.height(8.dp))
            SimpleMarkdownRenderer(markdown = body)
        }
    }
}

@Composable
private fun GradeRow(onGrade: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GradeButton("😵", "Забыл", SystemRed, onClick = { onGrade(0) }, modifier = Modifier.weight(1f))
        GradeButton("😅", "С трудом", SystemOrange, onClick = { onGrade(3) }, modifier = Modifier.weight(1f))
        GradeButton("🙂", "Норм", SystemBlue, onClick = { onGrade(4) }, modifier = Modifier.weight(1f))
        GradeButton("😎", "Легко", SystemGreen, onClick = { onGrade(5) }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GradeButton(
    emoji: String,
    label: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    Box(
        modifier = modifier
            .height(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(accent.copy(alpha = 0.14f))
            .clickable {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = emoji, fontSize = 26.sp)
            Spacer(Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun KindBadge(kind: String) {
    val (label, color) = when (kind) {
        "accent" -> "Ударение" to SystemOrange
        "word_blank" -> "Орфография" to SystemBlue
        "paronym" -> "Пароним" to SystemGreen
        "pleonasm" -> "Плеоназм" to SystemRed
        else -> kind to LabelTertiary
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

private fun questionFor(kind: String): String = when (kind) {
    "accent" -> "Где ударение?"
    "word_blank" -> "Какая буква пропущена?"
    "paronym" -> "Какой пароним правильный?"
    "pleonasm" -> "Какое слово лишнее?"
    else -> "Вспомни правило"
}
