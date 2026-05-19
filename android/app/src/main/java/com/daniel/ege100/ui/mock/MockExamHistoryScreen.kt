package com.daniel.ege100.ui.mock

import android.app.Application
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import com.daniel.ege100.ui.common.SmoothLazyColumn
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.MockExamResultEntity
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.data.UserProfileStore
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Phase 4 Stage B2 — история пробников + TrendChart (Convention #44).
 *
 * Канвас рисует:
 *   - 5 горизонтальных gridlines (0, 25, 50, 75, 100).
 *   - Зелёную пунктирную линию `target` (целевой балл из UserProfile).
 *   - Синюю ломаную math results (по completedDate ASC).
 *   - Оранжевую ломаную rus results.
 *
 * Точки на ломаных рисуются маленькими кружками — даёт ощущение «контрольных
 * точек», как в спортивных приложениях.
 */
data class MockExamHistoryUi(
    val loading: Boolean = true,
    val targetScore: Int = 80,
    val mathResults: List<MockExamResultEntity> = emptyList(),
    val rusResults: List<MockExamResultEntity> = emptyList(),
    val allSorted: List<MockExamResultEntity> = emptyList(),
)

class MockExamHistoryViewModel(app: Application) : AndroidViewModel(app) {
    private val resultDao = UserDataDatabase.get(app).mockExamResultDao()
    private val _state = MutableStateFlow(MockExamHistoryUi())
    val state: StateFlow<MockExamHistoryUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val profile = UserProfileStore.snapshot(ctx)
            val math = resultDao.getAllBySubject("math")
            val rus = resultDao.getAllBySubject("rus")
            val all = (math + rus).sortedByDescending { it.completedDate }
            _state.value = MockExamHistoryUi(
                loading = false,
                targetScore = profile.targetScore,
                mathResults = math,
                rusResults = rus,
                allSorted = all,
            )
        }
    }
}

@Composable
fun MockExamHistoryScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    vm: MockExamHistoryViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "История пробников",
                subtitle = "Тренд балла и все прохождения",
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
                st.allSorted.isEmpty() -> Text(
                    "Пройди первый пробник — здесь появится график тренда.",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                )
                else -> SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("trend_title") { SectionLabel("📈 Тренд балла") }
                    item("trend") {
                        TrendCard(
                            math = st.mathResults,
                            rus = st.rusResults,
                            target = st.targetScore,
                        )
                    }
                    item("all_title") { SectionLabel("Все пробники") }
                    items(st.allSorted, key = { it.id }) { r ->
                        ResultCard(result = r)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = LabelTertiary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun TrendCard(math: List<MockExamResultEntity>, rus: List<MockExamResultEntity>, target: Int) {
    AppleCard(paddingDp = 18) {
        Column(modifier = Modifier.fillMaxWidth()) {
            val gridColor = LabelTertiary.copy(alpha = 0.20f)
            val targetColor = SystemGreen
            val mathColor = SystemBlue
            val rusColor = SystemOrange
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .padding(top = 4.dp),
            ) {
                val padding = 16.dp.toPx()
                val chartW = size.width - padding * 2
                val chartH = size.height - padding * 2

                // Gridlines + labels (0, 25, 50, 75, 100).
                for (level in 0..4) {
                    val v = level * 25f
                    val y = padding + chartH * (1f - v / 100f)
                    drawLine(
                        color = gridColor,
                        start = Offset(padding, y),
                        end = Offset(size.width - padding, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                // Target line (пунктирная).
                val targetY = padding + chartH * (1f - target / 100f)
                drawLine(
                    color = targetColor,
                    start = Offset(padding, targetY),
                    end = Offset(size.width - padding, targetY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f)),
                    cap = StrokeCap.Round,
                )

                // Data lines.
                drawTrendLine(math, mathColor, padding, chartW, chartH)
                drawTrendLine(rus, rusColor, padding, chartW, chartH)
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                LegendItem(color = mathColor, label = "Математика (${math.size})")
                LegendItem(color = rusColor, label = "Русский (${rus.size})")
                LegendItem(color = targetColor, label = "Цель $target")
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTrendLine(
    results: List<MockExamResultEntity>,
    color: Color,
    padding: Float,
    chartW: Float,
    chartH: Float,
) {
    if (results.isEmpty()) return
    val n = results.size
    val xs = if (n == 1) listOf(padding + chartW / 2f) else (0 until n).map { i ->
        padding + chartW * i / (n - 1).toFloat()
    }
    val ys = results.map { r ->
        padding + chartH * (1f - r.score.coerceIn(0, 100) / 100f)
    }
    // Line segments.
    if (n >= 2) {
        for (i in 0 until n - 1) {
            drawLine(
                color = color,
                start = Offset(xs[i], ys[i]),
                end = Offset(xs[i + 1], ys[i + 1]),
                strokeWidth = 2.5.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
    }
    // Точки-кружки.
    for (i in 0 until n) {
        drawCircle(
            color = color,
            radius = 4.dp.toPx(),
            center = Offset(xs[i], ys[i]),
            style = androidx.compose.ui.graphics.drawscope.Fill,
        )
        drawCircle(
            color = androidx.compose.ui.graphics.Color.White,
            radius = 2.dp.toPx(),
            center = Offset(xs[i], ys[i]),
            style = androidx.compose.ui.graphics.drawscope.Fill,
        )
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 11.sp, color = LabelSecondary)
    }
}

private val DATE_FORMAT = SimpleDateFormat("d MMMM, HH:mm", Locale.forLanguageTag("ru"))

@Composable
private fun ResultCard(result: MockExamResultEntity) {
    val subjectLabel = if (result.subject == "math") "📐 Математика" else "✍️ Русский"
    val sourceLabel = when (result.source) {
        "fipi" -> "КИМ ФИПИ"
        else -> "Пробник №${result.planIndex.takeIf { it >= 0 } ?: "—"}"
    }
    val acc = if (result.total > 0) result.correct.toFloat() / result.total else 0f
    val color = when {
        acc >= 0.8f -> SystemGreen
        acc >= 0.6f -> SystemBlue
        else -> SystemOrange
    }
    AppleCard(paddingDp = 16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
            ) {
                Text(
                    text = "${result.score}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "$subjectLabel · $sourceLabel",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Label,
                )
                Text(
                    text = DATE_FORMAT.format(Date(result.completedDate)),
                    fontSize = 11.sp,
                    color = LabelTertiary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${result.correct} из ${result.total} · ${(acc * 100).toInt()}% · балл ${result.score}/100",
                    fontSize = 12.sp,
                    color = LabelSecondary,
                )
            }
        }
    }
}
