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
import com.daniel.ege100.srs.SrsCardEntity
import com.daniel.ege100.srs.SrsRepository
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
    data class Done(val total: Int) : SrsReviewState()
}

data class SrsReviewUi(
    val cards: List<SrsCardEntity> = emptyList(),
    val texts: Map<Long, SrsRepository.CardTexts> = emptyMap(),
    val state: SrsReviewState = SrsReviewState.Loading,
)

class SrsReviewViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(SrsReviewUi())
    val state: StateFlow<SrsReviewUi> = _state.asStateFlow()

    private var initialized = false

    fun start(limit: Int = 50) {
        if (initialized) return
        initialized = true
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val due = runCatching { SrsRepository.getDueCards(ctx, limit) }
                .getOrDefault(emptyList())
            if (due.isEmpty()) {
                _state.value = SrsReviewUi(state = SrsReviewState.Empty)
                return@launch
            }
            // Preload объяснений для всех карточек одной пачкой —
            // когда пользователь дойдёт до конкретной, текст уже в state.
            val textsMap = mutableMapOf<Long, SrsRepository.CardTexts>()
            due.forEach { card ->
                val t = runCatching { SrsRepository.getTextsForCard(ctx, card) }
                    .getOrDefault(SrsRepository.CardTexts(null, null, null, null))
                textsMap[card.id] = t
            }
            _state.value = SrsReviewUi(
                cards = due,
                texts = textsMap,
                state = SrsReviewState.Front(0),
            )
        }
    }

    fun showAnswer() {
        val s = _state.value
        val front = s.state as? SrsReviewState.Front ?: return
        _state.value = s.copy(state = SrsReviewState.Back(front.cardIndex))
    }

    fun submitGrade(grade: Int) {
        val s = _state.value
        val back = s.state as? SrsReviewState.Back ?: return
        val card = s.cards.getOrNull(back.cardIndex) ?: return
        viewModelScope.launch {
            runCatching {
                SrsRepository.submitReview(
                    context = getApplication(),
                    cardId = card.id,
                    grade = grade,
                )
            }
        }
        val nextIdx = back.cardIndex + 1
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
                is SrsReviewState.Back -> BackView(
                    cards = st.cards,
                    cardIndex = s.cardIndex,
                    texts = st.texts,
                    onGrade = vm::submitGrade,
                )
                is SrsReviewState.Done -> DoneState(total = s.total, onBack = onBack)
            }
        }
    }
}

private fun subtitleFor(ui: SrsReviewUi): String = when (val s = ui.state) {
    SrsReviewState.Loading -> "Загрузка…"
    SrsReviewState.Empty -> ""
    is SrsReviewState.Front -> "${s.cardIndex + 1} из ${ui.cards.size}"
    is SrsReviewState.Back -> "${s.cardIndex + 1} из ${ui.cards.size}"
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
private fun DoneState(total: Int, onBack: () -> Unit) {
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
        GradeRow(onGrade = onGrade)
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
