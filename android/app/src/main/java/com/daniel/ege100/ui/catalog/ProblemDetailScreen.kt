package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.CatalogDao
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.FavoritesStore
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.data.ProblemSubtypeEntity
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.data.RuleEntry
import com.daniel.ege100.data.RulesRepository
import com.daniel.ege100.data.SolutionEntity
import com.daniel.ege100.data.SubjectEntity
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.RuleBottomSheet
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.common.TertiaryButton
import com.daniel.ege100.ui.html.HtmlRenderer
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SeparatorHairline
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------- модель + ViewModel ----------------------

sealed class CheckResult {
    data object Idle : CheckResult()
    data object Correct : CheckResult()
    data class Wrong(val expected: String) : CheckResult()
    data object SkippedEmpty : CheckResult()
}

data class ProblemUiState(
    val loading: Boolean = true,
    val problem: ProblemEntity? = null,
    val solution: SolutionEntity? = null,
    val type: ProblemTypeEntity? = null,
    val subtype: ProblemSubtypeEntity? = null,
    val subject: SubjectEntity? = null,
    val position: Int = 0,
    val total: Int = 0,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val userAnswer: String = "",
    val checkResult: CheckResult = CheckResult.Idle,
    val isSolutionExpanded: Boolean = false,
    val rule: RuleEntry? = null,
)

class ProblemDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao: CatalogDao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow(ProblemUiState())
    val state: StateFlow<ProblemUiState> = _state.asStateFlow()

    private var initialProblemId: Long = -1
    private var typeId: Long = -1
    private var subtypeId: Long? = null

    fun start(problemId: Long, typeId: Long, subtypeId: Long?) {
        if (this.initialProblemId == problemId &&
            this.typeId == typeId &&
            this.subtypeId == subtypeId &&
            _state.value.problem != null
        ) return
        this.initialProblemId = problemId
        this.typeId = typeId
        this.subtypeId = subtypeId
        loadProblem(problemId)
    }

    private fun loadProblem(problemId: Long) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val problem = dao.getProblem(problemId) ?: run {
                _state.value = ProblemUiState(loading = false)
                return@launch
            }
            val solution = dao.getSolution(problemId)
            val type = dao.getType(typeId)
            val subtype = subtypeId?.let { dao.getSubtype(it) }
            val subject = dao.getSubject(problem.subjectId)
            val sid = subtypeId
            val position = if (sid != null) dao.positionInSubtype(problemId, sid)
                           else dao.positionInType(problemId, typeId)
            val total = if (sid != null) dao.countProblemsBySubtype(sid)
                        else dao.countProblemsByType(typeId)
            val nextId = if (sid != null) dao.nextProblemIdInSubtype(problemId, sid)
                         else dao.nextProblemIdInType(problemId, typeId)
            val prevId = if (sid != null) dao.prevProblemIdInSubtype(problemId, sid)
                         else dao.prevProblemIdInType(problemId, typeId)
            val rule = if (type != null && subject != null) {
                RulesRepository.getRule(getApplication(), subject.slug, type.number)
            } else null
            _state.value = ProblemUiState(
                loading = false,
                problem = problem,
                solution = solution,
                type = type,
                subtype = subtype,
                subject = subject,
                position = position,
                total = total,
                hasPrev = prevId != null,
                hasNext = nextId != null,
                userAnswer = "",
                checkResult = CheckResult.Idle,
                isSolutionExpanded = false,
                rule = rule,
            )
        }
    }

    fun setAnswer(v: String) { _state.value = _state.value.copy(userAnswer = v) }

    fun check() {
        val cur = _state.value
        val expected = cur.problem?.answer
        if (expected.isNullOrBlank()) {
            _state.value = cur.copy(checkResult = CheckResult.SkippedEmpty, isSolutionExpanded = true)
            return
        }
        val typed = cur.userAnswer
        if (typed.isBlank()) {
            _state.value = cur.copy(checkResult = CheckResult.SkippedEmpty, isSolutionExpanded = true)
            return
        }
        val correct = matchesAnswer(typed, expected, cur.problem.answerFormat)
        _state.value = if (correct) cur.copy(checkResult = CheckResult.Correct)
                       else cur.copy(checkResult = CheckResult.Wrong(expected), isSolutionExpanded = true)
    }

    fun toggleSolution() {
        _state.value = _state.value.copy(isSolutionExpanded = !_state.value.isSolutionExpanded)
    }

    fun goNext() {
        val cur = _state.value
        val pid = cur.problem?.id ?: return
        viewModelScope.launch {
            val nextId = if (subtypeId != null) dao.nextProblemIdInSubtype(pid, subtypeId!!)
                         else dao.nextProblemIdInType(pid, typeId)
            if (nextId != null) loadProblem(nextId)
        }
    }

    fun goPrev() {
        val cur = _state.value
        val pid = cur.problem?.id ?: return
        viewModelScope.launch {
            val prevId = if (subtypeId != null) dao.prevProblemIdInSubtype(pid, subtypeId!!)
                         else dao.prevProblemIdInType(pid, typeId)
            if (prevId != null) loadProblem(prevId)
        }
    }
}

// ---------------------- нормализация и сравнение ----------------------

private fun normalize(answer: String): String =
    answer.trim().lowercase().replace(',', '.').replace(Regex("\\s+"), " ")

private fun matchesAnswer(typed: String, expected: String, format: String?): Boolean {
    val nt = normalize(typed)
    val ne = normalize(expected)
    if (nt.isEmpty()) return false
    return when (format) {
        "alternatives" -> ne.split(' ').any { it == nt }
        "multipart" -> nt.split(' ').toSet() == ne.split(' ').toSet()
        else -> nt == ne
    }
}

// ---------------------- UI ----------------------

@Composable
fun ProblemDetailScreen(
    problemId: Long,
    typeId: Long,
    subtypeId: Long?,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    vm: ProblemDetailViewModel = viewModel(),
) {
    LaunchedEffect(problemId, typeId, subtypeId) { vm.start(problemId, typeId, subtypeId) }
    val st by vm.state.collectAsState()
    val haptic = LocalHapticFeedback.current
    var showRule by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentPid = st.problem?.id
    val isFavorite by remember(currentPid) {
        if (currentPid != null) FavoritesStore.isFavorite(context, currentPid)
        else kotlinx.coroutines.flow.flowOf(false)
    }.collectAsState(initial = false)
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    // Haptic при появлении verdict.
    LaunchedEffect(st.checkResult) {
        when (st.checkResult) {
            is CheckResult.Correct -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            is CheckResult.Wrong -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> Unit
        }
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = st.type?.let { "№${it.number}" } ?: "Задача",
                subtitle = st.subtype?.title ?: st.type?.title,
                trailingPosition = if (st.total > 0) "${st.position}/${st.total}" else null,
                onBack = onBack,
                rightContent = if (currentPid != null) {
                    {
                        FavoriteStar(
                            isFavorite = isFavorite,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                coroutineScope.launch {
                                    FavoritesStore.toggle(context, currentPid)
                                }
                            },
                        )
                    }
                } else null,
            )
        },
        containerColor = Bg,
        bottomBar = {
            PrevNextBar(
                hasPrev = st.hasPrev,
                hasNext = st.hasNext,
                onPrev = { vm.goPrev() },
                onNext = { vm.goNext() },
                contentPadding = contentPadding,
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
        ) {
            when {
                st.loading -> Text(
                    text = "Загрузка…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
                st.problem == null -> Text(
                    text = "Задача не найдена",
                    color = SystemRed,
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> ProblemBody(
                    st = st,
                    vm = vm,
                    onRuleClick = { showRule = true },
                )
            }
        }
    }

    val rule = st.rule
    if (showRule && rule != null) {
        RuleBottomSheet(rule = rule, onDismiss = { showRule = false })
    }
}

@Composable
private fun ProblemBody(
    st: ProblemUiState,
    vm: ProblemDetailViewModel,
    onRuleClick: () -> Unit,
) {
    val problem = st.problem ?: return
    val hasAnswer = !problem.answer.isNullOrBlank()
    val keyForReset = problem.id

    // Stage 5 part Е — горизонтальные свайпы между задачами.
    // Игнорируем жесты, начатые в edge-зоне x<24dp (там работает edgeSwipeBack).
    val density = LocalDensity.current
    val edgePx = with(density) { 24.dp.toPx() }
    val triggerPx = with(density) { 90.dp.toPx() }

    LazyColumn(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(problem.id) {
                var startX = 0f
                var totalDrag = 0f
                var skip = false
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        startX = offset.x
                        totalDrag = 0f
                        skip = startX < edgePx
                    },
                    onDragEnd = {
                        if (!skip) {
                            when {
                                totalDrag < -triggerPx && st.hasNext -> vm.goNext()
                                totalDrag > triggerPx && st.hasPrev -> vm.goPrev()
                            }
                        }
                        startX = 0f
                        totalDrag = 0f
                        skip = false
                    },
                    onHorizontalDrag = { _, dx -> if (!skip) totalDrag += dx },
                )
            },
    ) {
        // --- Условие ---
        item("statement_$keyForReset") {
            AppleCard {
                HtmlRenderer(html = problem.statementHtml, baseFontSizeSp = 18)
            }
        }

        // --- Поле ввода + кнопка проверки ---
        if (hasAnswer) {
            item("input_$keyForReset") {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    IosTextField(
                        value = st.userAnswer,
                        onValueChange = vm::setAnswer,
                        placeholder = "Твой ответ",
                    )
                    PrimaryButton(
                        text = if (st.userAnswer.isBlank()) "Показать решение" else "Проверить",
                        onClick = { vm.check() },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            text = "📋 Правило",
                            onClick = onRuleClick,
                            enabled = st.rule != null,
                            modifier = Modifier.weight(1f),
                        )
                        SecondaryButton(
                            text = "🤖 ИИ",
                            onClick = {},
                            enabled = false,
                            highlight = st.checkResult !is CheckResult.Idle,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            item("no_short_answer_$keyForReset") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    AppleCard {
                        Text(
                            text = "У этой задачи нет короткого ответа.",
                            fontSize = 15.sp,
                            color = LabelSecondary,
                        )
                    }
                    PrimaryButton(
                        text = if (st.isSolutionExpanded) "Скрыть решение" else "Показать решение",
                        onClick = { vm.toggleSolution() },
                    )
                    if (st.rule != null) {
                        SecondaryButton(
                            text = "📋 Правило",
                            onClick = { onRuleClick() },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // --- Плашка результата ---
        item("verdict_$keyForReset") {
            VerdictBanner(result = st.checkResult)
        }

        // --- Авторское решение ---
        val sol = st.solution
        if (sol != null) {
            item("solution_$keyForReset") {
                ExpandableSolution(
                    expanded = st.isSolutionExpanded,
                    onToggle = { vm.toggleSolution() },
                    html = sol.solutionHtml,
                )
            }
        }

        // --- Метаданные (Источник / Сложность) ---
        item("meta_$keyForReset") {
            MetaRow(problem = problem)
        }
    }
}

@Composable
private fun VerdictBanner(result: CheckResult) {
    val visible = result is CheckResult.Correct || result is CheckResult.Wrong
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium), initialScale = 0.85f) + fadeIn(),
        exit = scaleOut(targetScale = 0.95f) + fadeOut(),
    ) {
        val (bg, accent, title, body) = when (result) {
            is CheckResult.Correct -> Quad(
                SystemGreenTint,
                SystemGreen,
                "Правильно",
                "Можно открыть решение или перейти к следующей задаче",
            )
            is CheckResult.Wrong -> Quad(
                SystemRedTint,
                SystemRed,
                "Неверно",
                "Правильный ответ: ${result.expected}. Решение раскрыто ниже.",
            )
            else -> Quad(Color.Transparent, Color.White, "", "")
        }
        AppleCard(background = bg) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.25f)),
                ) {
                    Text(
                        text = if (result is CheckResult.Correct) "✓" else "✕",
                        fontSize = 22.sp,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Column {
                    Text(
                        text = title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    Text(
                        text = body,
                        fontSize = 15.sp,
                        color = LabelSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun ExpandableSolution(
    expanded: Boolean,
    onToggle: () -> Unit,
    html: String,
) {
    val haptic = LocalHapticFeedback.current
    AppleCard {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle()
                },
        ) {
            Text(
                text = "Авторское решение",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = SystemBlue,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▲" else "▼",
                fontSize = 14.sp,
                color = SystemBlue,
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(spring(0.75f, Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                HtmlRenderer(html = html, baseFontSizeSp = 16)
            }
        }
    }
}

@Composable
private fun MetaRow(problem: ProblemEntity) {
    val rows = buildList {
        problem.source?.let { add("Источник" to it) }
        problem.difficulty?.let { add("Сложность" to it) }
    }
    if (rows.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        rows.forEach { (k, v) ->
            Row {
                Text(
                    text = "$k: ",
                    fontSize = 13.sp,
                    color = LabelTertiary,
                )
                Text(
                    text = v,
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
            }
        }
    }
}

/**
 * Stage 5 part Д: звезда избранного с bounce при смене состояния.
 */
@Composable
private fun FavoriteStar(
    isFavorite: Boolean,
    onClick: () -> Unit,
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isFavorite) 1.0f else 0.92f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "star-scale",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { onClick() },
    ) {
        Text(
            text = if (isFavorite) "★" else "☆",
            fontSize = 26.sp,
            color = if (isFavorite) com.daniel.ege100.ui.theme.SystemYellow else SystemBlue,
            modifier = Modifier.scale(scale),
        )
    }
}

@Composable
private fun PrevNextBar(
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Bg),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(SeparatorHairline),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            TertiaryButton(
                text = "← Предыдущая",
                onClick = onPrev,
                enabled = hasPrev,
                modifier = Modifier.weight(1f),
            )
            TertiaryButton(
                text = "Далее →",
                onClick = onNext,
                enabled = hasNext,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
