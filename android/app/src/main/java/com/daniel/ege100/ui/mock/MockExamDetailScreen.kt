package com.daniel.ege100.ui.mock

import android.app.Application
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.MockExamPlan
import com.daniel.ege100.data.MockExamResultEntity
import com.daniel.ege100.data.MockExamSchedule
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserProfileStore
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.PrimaryButton
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemGreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FMT_DETAIL = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("ru"))

data class MockExamDetailUi(
    val loading: Boolean = true,
    val plan: MockExamPlan? = null,
    val result: MockExamResultEntity? = null,
)

class MockExamDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val resultDao = UserDataDatabase.get(app).mockExamResultDao()
    private val _state = MutableStateFlow(MockExamDetailUi())
    val state: StateFlow<MockExamDetailUi> = _state.asStateFlow()

    private var planIndex: Int = -1

    fun load(planIndex: Int) {
        if (this.planIndex == planIndex && _state.value.plan != null) return
        this.planIndex = planIndex
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profile = UserProfileStore.snapshot(ctx)
            val plans = MockExamSchedule.getSchedule(ctx, profile.examDateParsed)
            val plan = plans.firstOrNull { it.index == planIndex }
            val result = resultDao.getLatestByPlanIndex(planIndex)
            _state.value = MockExamDetailUi(loading = false, plan = plan, result = result)
        }
    }
}

@Composable
fun MockExamDetailScreen(
    planIndex: Int,
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onStart: () -> Unit,
    vm: MockExamDetailViewModel = viewModel(),
) {
    LaunchedEffect(planIndex) { vm.load(planIndex) }
    val st by vm.state.collectAsState()

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Пробник №$planIndex",
                subtitle = st.plan?.parsedDate?.format(DATE_FMT_DETAIL),
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
                st.loading -> Text("Загрузка…", color = LabelSecondary, modifier = Modifier.align(Alignment.Center))
                st.plan == null -> Text("Пробник не найден", color = LabelSecondary, modifier = Modifier.align(Alignment.Center))
                st.result != null -> ResultBody(st.plan!!, st.result!!, onRetake = onStart)
                else -> UpcomingBody(st.plan!!, onStart = onStart)
            }
        }
    }
}

@Composable
private fun UpcomingBody(plan: MockExamPlan, onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        AppleCard(paddingDp = 22) {
            Column {
                Text(
                    "Что внутри",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Spacer(Modifier.height(14.dp))
                InfoRow("📐", "8 случайных задач математики (типы №1-19)")
                InfoRow("✍️", "8 случайных задач русского (типы №1-26)")
                InfoRow("⏱", "Время неограниченно (рекомендуется ~30 минут)")
                InfoRow("📊", "Результат сохранится в журнал и пойдёт в статистику")
                InfoRow("✓", "Правильность считается на лету — без ИИ")
            }
        }
        Spacer(Modifier.height(20.dp))
        PrimaryButton(text = "Начать пробник", onClick = onStart)
    }
}

@Composable
private fun InfoRow(emoji: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.size(12.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            color = Label,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ResultBody(plan: MockExamPlan, result: MockExamResultEntity, onRetake: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        AppleCard(paddingDp = 22) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Результат", fontSize = 13.sp, color = LabelSecondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(14.dp))
                ResultSubject(
                    label = "📐 Математика",
                    correct = result.mathCorrect,
                    total = result.mathTotal,
                    score = result.mathScore,
                )
                Spacer(Modifier.height(14.dp))
                ResultSubject(
                    label = "✍️ Русский",
                    correct = result.rusCorrect,
                    total = result.rusTotal,
                    score = result.rusScore,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Длительность: ${formatDur(result.durationMs)}",
                    fontSize = 12.sp,
                    color = LabelTertiary,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        SecondaryButton(
            text = "🔁 Перепройти",
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResultSubject(label: String, correct: Int, total: Int, score: Int) {
    val acc = if (total > 0) correct.toFloat() / total else 0f
    Column {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, fontSize = 15.sp, color = Label, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("$score", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = SystemGreen)
            Text(" /100", fontSize = 12.sp, color = LabelSecondary, modifier = Modifier.padding(bottom = 3.dp))
        }
        Spacer(Modifier.height(6.dp))
        AppleProgressBar(progress = acc)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "$correct из $total ($score балл)",
            fontSize = 12.sp,
            color = LabelTertiary,
        )
    }
}

private fun formatDur(ms: Long): String {
    val mins = ms / 60_000
    return "$mins мин"
}
