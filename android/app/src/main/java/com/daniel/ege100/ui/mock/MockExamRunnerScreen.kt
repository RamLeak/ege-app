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
import com.daniel.ege100.data.AttemptLogEntity
import com.daniel.ege100.data.CatalogDao
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.FipiScoreTable
import com.daniel.ege100.data.MockExamPlan
import com.daniel.ege100.data.MockExamResultEntity
import com.daniel.ege100.data.MockExamSchedule
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.data.SubjectEntity
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
 * Phase 3 Stage FINAL part А — экран прохождения пробника.
 *
 * Последовательно 16 задач: 8 случайных из math (по одной из 8 разных
 * типов 1..19) + 8 из rus (1..26). Ответ пользователя сравнивается с
 * `problem.answer` (нормализация как в ProblemDetailViewModel). В
 * `attempt_log` пишется с `source = "mock_exam"`.
 *
 * После 16-й задачи — экран результата (Math correct/total + Rus
 * correct/total + FipiScoreTable.rawToTest для прогноза балла), сохранение
 * в `mock_exam_results`.
 */
data class MockProblem(
    val problem: ProblemEntity,
    val subject: String,        // "math" | "rus"
    val typeNumber: Int,
)

data class MockExamRunnerUi(
    val loading: Boolean = true,
    val plan: MockExamPlan? = null,
    val problems: List<MockProblem> = emptyList(),
    val currentIndex: Int = 0,
    val userAnswer: String = "",
    val verdict: MockVerdict = MockVerdict.None,
    val mathCorrect: Int = 0,
    val mathTotal: Int = 0,
    val rusCorrect: Int = 0,
    val rusTotal: Int = 0,
    val finished: Boolean = false,
    val durationMs: Long = 0L,
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
    private var planIndex: Int = -1

    fun start(planIndex: Int) {
        if (this.planIndex == planIndex && _state.value.problems.isNotEmpty()) return
        this.planIndex = planIndex
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profile = UserProfileStore.snapshot(ctx)
            val plans = MockExamSchedule.getSchedule(ctx, profile.examDateParsed)
            val plan = plans.firstOrNull { it.index == planIndex }
            val problems = composeMix()
            startedAtMs = System.currentTimeMillis()
            _state.value = MockExamRunnerUi(
                loading = false,
                plan = plan,
                problems = problems,
            )
        }
    }

    private suspend fun composeMix(): List<MockProblem> {
        val math = pickFromSubject("mathb", "math", typeRange = 1..19, target = 8)
        val rus = pickFromSubject("rus", "rus", typeRange = 1..26, target = 8)
        // Перемешиваем чтобы предметы чередовались.
        return (math + rus).shuffled()
    }

    private suspend fun pickFromSubject(
        slug: String,
        statsKey: String,
        typeRange: IntRange,
        target: Int,
    ): List<MockProblem> {
        val subject = catalogDao.getSubjectBySlug(slug) ?: return emptyList()
        val typesById = catalogDao.getTypesBySubject(subject.id)
            .filter { it.isSupplementary == 0 && it.number in typeRange }
        if (typesById.isEmpty()) return emptyList()
        // Берём target случайных типов (если меньше — все).
        val pickedTypes = typesById.shuffled().take(target)
        return pickedTypes.mapNotNull { tc ->
            // По одной случайной задаче из этого типа.
            val problems = catalogDao.getProblemsByType(typeId = tc.id, limit = 1, offset = randomOffset(tc.problemCount))
            problems.firstOrNull()?.let { p ->
                MockProblem(problem = p, subject = statsKey, typeNumber = tc.number)
            }
        }
    }

    private fun randomOffset(count: Int): Int =
        if (count <= 1) 0 else (0 until count).random()

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
            // Задача без короткого ответа — засчитываем как пропуск (incorrect).
            advanceWithResult(isCorrect = false, expected = expected, current = current)
            return
        }
        val isCorrect = matches(typed, expected, current.problem.answerFormat)
        advanceWithResult(isCorrect = isCorrect, expected = expected, current = current)
    }

    private fun advanceWithResult(isCorrect: Boolean, expected: String, current: MockProblem) {
        val cur = _state.value
        val newMathCorrect = cur.mathCorrect + if (current.subject == "math" && isCorrect) 1 else 0
        val newMathTotal = cur.mathTotal + if (current.subject == "math") 1 else 0
        val newRusCorrect = cur.rusCorrect + if (current.subject == "rus" && isCorrect) 1 else 0
        val newRusTotal = cur.rusTotal + if (current.subject == "rus") 1 else 0
        _state.value = cur.copy(
            verdict = if (isCorrect) MockVerdict.Correct(expected) else MockVerdict.Wrong(expected),
            mathCorrect = newMathCorrect,
            mathTotal = newMathTotal,
            rusCorrect = newRusCorrect,
            rusTotal = newRusTotal,
        )
        // attempt_log с source=mock_exam.
        viewModelScope.launch {
            attemptDao.insert(
                AttemptLogEntity(
                    problemId = current.problem.id,
                    subject = current.subject,
                    typeNumber = current.typeNumber,
                    subtypeId = current.problem.subtypeId,
                    isCorrect = isCorrect,
                    durationMs = 0L,
                    timestamp = System.currentTimeMillis(),
                    source = "mock_exam",
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
        val plan = cur.plan ?: return
        val durationMs = System.currentTimeMillis() - startedAtMs
        // Прогноз балла на основе результата: масштабируем correct до 32 (math) / 50 (rus).
        // Это грубо, но даёт ориентир.
        val mathRaw = if (cur.mathTotal > 0) (cur.mathCorrect * 32 / cur.mathTotal) else 0
        val rusRaw = if (cur.rusTotal > 0) (cur.rusCorrect * 50 / cur.rusTotal) else 0
        val mathScore = FipiScoreTable.rawToTest("math", mathRaw)
        val rusScore = FipiScoreTable.rawToTest("rus", rusRaw)
        _state.value = cur.copy(
            finished = true,
            durationMs = durationMs,
        )
        viewModelScope.launch {
            resultDao.insert(
                MockExamResultEntity(
                    planIndex = plan.index,
                    scheduledDate = plan.date,
                    completedDate = System.currentTimeMillis(),
                    mathCorrect = cur.mathCorrect,
                    mathTotal = cur.mathTotal,
                    rusCorrect = cur.rusCorrect,
                    rusTotal = cur.rusTotal,
                    mathScore = mathScore,
                    rusScore = rusScore,
                    durationMs = durationMs,
                ),
            )
        }
    }

    private fun matches(typed: String, expected: String, format: String?): Boolean {
        val nt = typed.lowercase().replace(',', '.').replace(Regex("\\s+"), " ").trim()
        val ne = expected.lowercase().replace(',', '.').replace(Regex("\\s+"), " ").trim()
        if (nt.isEmpty()) return false
        return when (format) {
            "alternatives" -> ne.split(' ').any { it == nt }
            "multipart" -> nt.split(' ').toSet() == ne.split(' ').toSet()
            else -> nt == ne
        }
    }
}

// ---------------------------------------------------------------------------

@Composable
fun MockExamRunnerScreen(
    planIndex: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    vm: MockExamRunnerViewModel = viewModel(),
) {
    LaunchedEffect(planIndex) { vm.start(planIndex) }
    val st by vm.state.collectAsState()

    if (st.finished) {
        FinishedScreen(st, onClose = onFinish, contentPadding = contentPadding)
        return
    }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Пробник №$planIndex",
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
                            text = if (mock.subject == "math") "📐 Математика · №${mock.typeNumber}" else "✍️ Русский · №${mock.typeNumber}",
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
    val mathAcc = if (st.mathTotal > 0) st.mathCorrect.toFloat() / st.mathTotal else 0f
    val rusAcc = if (st.rusTotal > 0) st.rusCorrect.toFloat() / st.rusTotal else 0f
    val mathRaw = if (st.mathTotal > 0) st.mathCorrect * 32 / st.mathTotal else 0
    val rusRaw = if (st.rusTotal > 0) st.rusCorrect * 50 / st.rusTotal else 0
    val mathScore = FipiScoreTable.rawToTest("math", mathRaw)
    val rusScore = FipiScoreTable.rawToTest("rus", rusRaw)

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
                    "Пробник №${st.plan?.index} завершён",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(24.dp))
                AppleCard(paddingDp = 22) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        ResultRow("📐 Математика", st.mathCorrect, st.mathTotal, mathAcc, mathScore)
                        Spacer(Modifier.height(14.dp))
                        ResultRow("✍️ Русский", st.rusCorrect, st.rusTotal, rusAcc, rusScore)
                    }
                }
                Spacer(Modifier.size(24.dp))
                PrimaryButton(text = "К календарю", onClick = onClose)
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

@Composable
private fun ResultRow(label: String, correct: Int, total: Int, accuracy: Float, score: Int) {
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Label, modifier = Modifier.weight(1f))
            Text("$score", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = SystemGreen)
            Text(" /100", fontSize = 13.sp, color = LabelSecondary, modifier = Modifier.padding(bottom = 4.dp))
        }
        Spacer(Modifier.height(6.dp))
        AppleProgressBar(progress = accuracy)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$correct из $total · ${(accuracy * 100).toInt()}%",
            fontSize = 12.sp,
            color = LabelTertiary,
        )
    }
}
