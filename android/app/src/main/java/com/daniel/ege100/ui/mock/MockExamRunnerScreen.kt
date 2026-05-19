package com.daniel.ege100.ui.mock

import android.app.Application
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.AnswerChecker
import com.daniel.ege100.data.AttemptLogEntity
import com.daniel.ege100.data.CatalogDao
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.FipiVariantsRepository
import com.daniel.ege100.data.FipiVariant
import com.daniel.ege100.data.FipiTask
import com.daniel.ege100.data.FipiScoreTable
import com.daniel.ege100.data.MockExamPlan
import com.daniel.ege100.data.MockExamResultEntity
import com.daniel.ege100.data.MockExamSchedule
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserProfileStore
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.IosTextField
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.html.HtmlRenderer
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------

/**
 * Phase 4 Stage A1 + B1 — экран прохождения пробника.
 *
 * Источник задач:
 *   - internal с subject="math": 19 задач, по одной из каждого типа 1..19 mathb.
 *   - internal с subject="rus": 26 задач, по одной из каждого типа 1..26 rus.
 *   - fipi с fipiVariantId: задачи из FipiVariantsRepository (resolved problem_id).
 *
 * Сохранение в mock_exam_results с правильными `subject` + `source`. После
 * 16-й задачи (или сколько в варианте) — экран результата с прогнозом
 * ФИПИ-балла.
 */
data class MockProblem(
    val problem: ProblemEntity,
    val typeNumber: Int,
)

data class MockExamRunnerUi(
    val loading: Boolean = true,
    val plan: MockExamPlan? = null,
    val fipiVariant: FipiVariant? = null,
    val subject: String = "math",  // "math" | "rus"
    val problems: List<MockProblem> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswer: String = "",
    val verdict: MockVerdict = MockVerdict.None,
    val correct: Int = 0,
    val total: Int = 0,
    val finished: Boolean = false,
    val durationMs: Long = 0L,
    val displayTitle: String = "Пробник",
)

sealed class MockVerdict {
    data object None : MockVerdict()
    data class Correct(val expected: String) : MockVerdict()
    data class Wrong(val expected: String) : MockVerdict()
}

class MockExamRunnerViewModel(app: Application) : AndroidViewModel(app) {
    private val catalogDao: CatalogDao = EgeDatabase.get(app).catalogDao()
    private val userDb = UserDataDatabase.get(app)
    private val attemptDao = userDb.attemptLogDao()
    private val resultDao = userDb.mockExamResultDao()
    private val _state = MutableStateFlow(MockExamRunnerUi())
    val state: StateFlow<MockExamRunnerUi> = _state.asStateFlow()

    private var startedAtMs: Long = 0L
    private var planIndex: Int = -2  // sentinel
    private var subjectKey: String = "math"
    private var fipiVariantId: String? = null

    fun start(planIndex: Int, subject: String, fipiVariantId: String?) {
        if (this.planIndex == planIndex &&
            this.subjectKey == subject &&
            this.fipiVariantId == fipiVariantId &&
            _state.value.problems.isNotEmpty()
        ) return
        this.planIndex = planIndex
        this.subjectKey = subject
        this.fipiVariantId = fipiVariantId
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profile = UserProfileStore.snapshot(ctx)
            val plan = if (planIndex >= 0) {
                MockExamSchedule.getSchedule(ctx, profile.examDateParsed)
                    .firstOrNull { it.index == planIndex }
            } else null

            val (problems, variant, title) = if (fipiVariantId != null) {
                val v = FipiVariantsRepository.getVariant(ctx, fipiVariantId)
                val tasks = v?.tasks.orEmpty().sortedBy { it.position }
                val resolved = tasks.mapNotNull { resolveFipiTask(it) }
                Triple(resolved, v, v?.title ?: "Вариант ФИПИ")
            } else {
                val composed = if (subject == "math") composeMath() else composeRus()
                val title = if (subject == "math") "Пробник №$planIndex · Математика"
                            else "Пробник №$planIndex · Русский"
                Triple(composed, null, title)
            }
            startedAtMs = System.currentTimeMillis()
            _state.value = MockExamRunnerUi(
                loading = false,
                plan = plan,
                fipiVariant = variant,
                subject = subject,
                problems = problems,
                displayTitle = title,
            )
        }
    }

    private suspend fun composeMath(): List<MockProblem> = composeBySubject("mathb", 1..19)
    private suspend fun composeRus(): List<MockProblem> = composeBySubject("rus", 1..26)

    private suspend fun composeBySubject(slug: String, typeRange: IntRange): List<MockProblem> {
        val result = mutableListOf<MockProblem>()
        for (typeNumber in typeRange) {
            val candidates = catalogDao.getProblemIdsByTypeNumber(slug, typeNumber)
            if (candidates.isEmpty()) continue
            val pickedId = candidates.random()
            val problem = catalogDao.getProblem(pickedId) ?: continue
            result += MockProblem(problem = problem, typeNumber = typeNumber)
        }
        return result
    }

    private suspend fun resolveFipiTask(task: FipiTask): MockProblem? {
        val pid = task.problemId ?: return null
        val problem = catalogDao.getProblem(pid) ?: return null
        val type = catalogDao.getType(problem.typeId)
        return MockProblem(problem = problem, typeNumber = type?.number ?: task.typeNumber ?: 0)
    }

    fun setAnswer(v: String) {
        if (_state.value.verdict is MockVerdict.None) {
            _state.value = _state.value.copy(userAnswer = v)
        }
    }

    fun check() {
        val cur = _state.value
        if (cur.verdict !is MockVerdict.None) return
        val current = cur.problems.getOrNull(cur.currentIndex) ?: return
        val typed = cur.userAnswer.trim()
        if (typed.isBlank()) return
        val expected = current.problem.answer?.trim().orEmpty()
        if (expected.isBlank()) {
            advanceWithResult(isCorrect = false, expected = expected, current = current)
            return
        }
        // Phase 4 Stage P4-C part А — Convention #48: единая логика.
        val isCorrect = AnswerChecker.isCorrect(typed, expected, current.problem.answerFormat)
        advanceWithResult(isCorrect = isCorrect, expected = expected, current = current)
    }

    private fun advanceWithResult(isCorrect: Boolean, expected: String, current: MockProblem) {
        val cur = _state.value
        val newCorrect = cur.correct + if (isCorrect) 1 else 0
        _state.value = cur.copy(
            verdict = if (isCorrect) MockVerdict.Correct(expected) else MockVerdict.Wrong(expected),
            correct = newCorrect,
            total = cur.total + 1,
        )
        // attempt_log с source=mock_exam (для internal) или fipi_exam (для fipi).
        val source = if (fipiVariantId != null) "fipi_exam" else "mock_exam"
        viewModelScope.launch {
            attemptDao.insert(
                AttemptLogEntity(
                    problemId = current.problem.id,
                    subject = cur.subject,
                    typeNumber = current.typeNumber,
                    subtypeId = current.problem.subtypeId,
                    isCorrect = isCorrect,
                    durationMs = 0L,
                    timestamp = System.currentTimeMillis(),
                    source = source,
                ),
            )
        }
    }

    fun next() {
        val cur = _state.value
        if (cur.verdict is MockVerdict.None) return
        val nextIndex = cur.currentIndex + 1
        if (nextIndex >= cur.problems.size) {
            finishMockExam()
        } else {
            _state.value = cur.copy(
                currentIndex = nextIndex,
                userAnswer = "",
                verdict = MockVerdict.None,
            )
        }
    }

    private fun finishMockExam() {
        val cur = _state.value
        val durationMs = System.currentTimeMillis() - startedAtMs
        val totalAttempted = cur.total
        val raw = if (totalAttempted > 0) {
            cur.correct * FipiScoreTable.maxRaw(cur.subject) / totalAttempted
        } else 0
        val score = FipiScoreTable.rawToTest(cur.subject, raw)
        _state.value = cur.copy(finished = true, durationMs = durationMs)
        viewModelScope.launch {
            // Phase 4 B1: для fipi используем variant.id как scheduledDate
            // чтобы группировать прошлые прохождения в FipiVariantsScreen.
            val scheduledDate = fipiVariantId
                ?: cur.plan?.date
                ?: java.time.LocalDate.now().toString()
            resultDao.insert(
                MockExamResultEntity(
                    planIndex = if (fipiVariantId != null) -1 else planIndex,
                    subject = cur.subject,
                    source = if (fipiVariantId != null) "fipi" else "internal",
                    scheduledDate = scheduledDate,
                    completedDate = System.currentTimeMillis(),
                    correct = cur.correct,
                    total = totalAttempted,
                    score = score,
                    durationMs = durationMs,
                ),
            )
        }
    }

}

// ---------------------------------------------------------------------------

@Composable
fun MockExamRunnerScreen(
    planIndex: Int,
    subject: String,
    fipiVariantId: String?,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    vm: MockExamRunnerViewModel = viewModel(),
) {
    LaunchedEffect(planIndex, subject, fipiVariantId) { vm.start(planIndex, subject, fipiVariantId) }
    val st by vm.state.collectAsState()

    if (st.finished) {
        FinishedScreen(st, onClose = onFinish, contentPadding = contentPadding)
        return
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = st.displayTitle,
                subtitle = if (st.problems.isNotEmpty()) "${st.currentIndex + 1} из ${st.problems.size}" else null,
                onBack = onBack,
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
                st.loading -> Text("Загрузка задач…", color = LabelSecondary, modifier = Modifier.align(Alignment.Center))
                st.problems.isEmpty() -> Text("Не удалось подобрать задачи", color = SystemRed, modifier = Modifier.align(Alignment.Center))
                else -> RunnerBody(st = st, vm = vm)
            }
        }
    }
}

@Composable
private fun RunnerBody(st: MockExamRunnerUi, vm: MockExamRunnerViewModel) {
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(st.verdict) {
        when (st.verdict) {
            is MockVerdict.Correct -> haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            is MockVerdict.Wrong -> haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            else -> Unit
        }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        AppleProgressBar(
            progress = (st.currentIndex + 1).toFloat() / st.problems.size,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        )
        AnimatedContent(
            targetState = st.currentIndex,
            transitionSpec = {
                val swipeSpring = spring<androidx.compose.ui.unit.IntOffset>(
                    dampingRatio = 0.85f,
                    stiffness = Spring.StiffnessMediumLow,
                )
                (slideInHorizontally(swipeSpring) { it } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(swipeSpring) { -it / 3 } + fadeOut(tween(280)))
            },
            label = "mock-problem",
            modifier = Modifier.weight(1f),
        ) { idx ->
            val mock = st.problems.getOrNull(idx) ?: return@AnimatedContent
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item("badge") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${subjectLabel(st.subject)} · №${mock.typeNumber}",
                            fontSize = 13.sp,
                            color = LabelTertiary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                item("statement") {
                    AppleCard {
                        HtmlRenderer(html = mock.problem.statementHtml, baseFontSizeSp = 17)
                    }
                }
                item("input") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        IosTextField(
                            value = st.userAnswer,
                            onValueChange = vm::setAnswer,
                            placeholder = "Твой ответ",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        )
                        when (val v = st.verdict) {
                            is MockVerdict.None -> {
                                PrimaryButton(
                                    text = "Ответить",
                                    onClick = { vm.check() },
                                    enabled = st.userAnswer.isNotBlank(),
                                )
                            }
                            is MockVerdict.Correct -> {
                                VerdictBanner(correct = true, expected = v.expected)
                                PrimaryButton(text = "Далее →", onClick = vm::next)
                            }
                            is MockVerdict.Wrong -> {
                                VerdictBanner(correct = false, expected = v.expected)
                                PrimaryButton(text = "Далее →", onClick = vm::next)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun subjectLabel(subject: String): String = when (subject) {
    "math" -> "📐 Математика"
    "rus" -> "✍️ Русский"
    else -> subject
}

@Composable
private fun VerdictBanner(correct: Boolean, expected: String) {
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(
            spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
            initialScale = 0.85f,
        ) + fadeIn(),
        exit = scaleOut() + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(if (correct) SystemGreenTint else SystemRedTint)
                .padding(16.dp),
        ) {
            Column {
                Text(
                    text = if (correct) "✓ Верно" else "✕ Неверно",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (correct) SystemGreen else SystemRed,
                )
                if (!correct) {
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Правильно: $expected",
                        fontSize = 14.sp,
                        color = Label,
                    )
                }
            }
        }
    }
}

@Composable
private fun FinishedScreen(
    st: MockExamRunnerUi,
    onClose: () -> Unit,
    contentPadding: PaddingValues,
) {
    val acc = if (st.total > 0) st.correct.toFloat() / st.total else 0f
    val raw = if (st.total > 0) st.correct * FipiScoreTable.maxRaw(st.subject) / st.total else 0
    val score = FipiScoreTable.rawToTest(st.subject, raw)

    Scaffold(containerColor = Bg) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding)
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✓", fontSize = 64.sp, color = SystemGreen, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "${st.displayTitle} завершён",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(24.dp))
                AppleCard(paddingDp = 22) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                subjectLabel(st.subject),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Label,
                                modifier = Modifier.weight(1f),
                            )
                            Text("$score", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = SystemGreen)
                            Text(" /100", fontSize = 13.sp, color = LabelSecondary, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Spacer(Modifier.height(6.dp))
                        AppleProgressBar(progress = acc)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${st.correct} из ${st.total} · точность ${(acc * 100).toInt()}%",
                            fontSize = 12.sp,
                            color = LabelTertiary,
                        )
                    }
                }
                Spacer(Modifier.size(24.dp))
                PrimaryButton(text = "Готово", onClick = onClose)
                Spacer(Modifier.size(10.dp))
                SecondaryButton(
                    text = "На главный",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
