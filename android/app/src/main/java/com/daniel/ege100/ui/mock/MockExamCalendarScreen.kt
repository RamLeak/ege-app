package com.daniel.ege100.ui.mock

import android.app.Application
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.MockExamPlan
import com.daniel.ege100.data.MockExamResultEntity
import com.daniel.ege100.data.MockExamSchedule
import com.daniel.ege100.data.MockExamStatus
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserProfileStore
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.daysWord
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemOrange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT = DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.forLanguageTag("ru"))

// ---------------------------------------------------------------------------

data class MockExamCalendarUi(
    val loading: Boolean = true,
    val plans: List<MockExamPlan> = emptyList(),
    val resultsByIndex: Map<Int, MockExamResultEntity> = emptyMap(),
)

class MockExamCalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val resultDao = UserDataDatabase.get(app).mockExamResultDao()
    private val _state = MutableStateFlow(MockExamCalendarUi())
    val state: StateFlow<MockExamCalendarUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profile = UserProfileStore.snapshot(ctx)
            val examDate = profile.examDateParsed
            val plans = MockExamSchedule.getSchedule(ctx, examDate)
            val results = resultDao.getAll().associateBy { it.planIndex }
            _state.value = MockExamCalendarUi(loading = false, plans = plans, resultsByIndex = results)
        }
    }
}

@Composable
fun MockExamCalendarScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onPlanClick: (planIndex: Int) -> Unit,
    vm: MockExamCalendarViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Календарь пробников",
                subtitle = "${st.plans.size} контрольных точек до ЕГЭ",
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
            if (st.loading) {
                Text("Загрузка…", color = LabelSecondary, modifier = Modifier.align(Alignment.Center))
            } else if (st.plans.isEmpty()) {
                Text(
                    "Расписание появится ближе к ЕГЭ.",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("summary") {
                        SummaryCard(
                            completed = st.resultsByIndex.size,
                            total = st.plans.size,
                        )
                    }
                    items(st.plans, key = { it.index }) { plan ->
                        MockExamCard(
                            plan = plan,
                            result = st.resultsByIndex[plan.index],
                            onClick = { onPlanClick(plan.index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(completed: Int, total: Int) {
    AppleCard(paddingDp = 20) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text("Пройдено", fontSize = 13.sp, color = LabelSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$completed",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = Label,
                )
                Text(
                    " из $total",
                    fontSize = 16.sp,
                    color = LabelSecondary,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            AppleProgressBar(progress = if (total > 0) completed.toFloat() / total else 0f)
        }
    }
}

@Composable
private fun MockExamCard(plan: MockExamPlan, result: MockExamResultEntity?, onClick: () -> Unit) {
    AppleCard(onClick = onClick, paddingDp = 16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusBadge(plan.status, completed = result != null)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Пробник №${plan.index}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = plan.parsedDate.format(DATE_FMT),
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
                if (result != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "📐 ${result.mathScore} · ✍️ ${result.rusScore}",
                        fontSize = 13.sp,
                        color = SystemGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    val days = ChronoUnit.DAYS.between(LocalDate.now(), plan.parsedDate).toInt()
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            days == 0 -> "Сегодня"
                            days > 0 -> "Через $days ${daysWord(days)}"
                            else -> "${-days} ${daysWord(-days)} назад"
                        },
                        fontSize = 12.sp,
                        color = when {
                            days == 0 -> SystemOrange
                            days in 1..7 -> SystemOrange
                            days < 0 -> SystemOrange
                            else -> LabelTertiary
                        },
                    )
                }
            }
            Text("›", fontSize = 22.sp, color = LabelTertiary)
        }
    }
}

@Composable
private fun StatusBadge(status: MockExamStatus, completed: Boolean) {
    val (bg, emoji) = when {
        completed -> SystemGreenTint to "✓"
        status == MockExamStatus.TODAY -> Color(0x1FFF9F0A) to "📅"
        status == MockExamStatus.PAST -> Color(0x1F8E8E93) to "⏱"
        else -> SystemBlueTint to "🎯"
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(bg),
    ) {
        Text(emoji, fontSize = 22.sp)
    }
}
