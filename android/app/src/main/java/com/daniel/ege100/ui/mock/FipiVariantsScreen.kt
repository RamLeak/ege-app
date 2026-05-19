package com.daniel.ege100.ui.mock

import android.app.Application
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.FipiVariant
import com.daniel.ege100.data.FipiVariantsRepository
import com.daniel.ege100.data.MockExamResultEntity
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlueTint
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FipiVariantsUi(
    val loading: Boolean = true,
    val mathVariants: List<FipiVariant> = emptyList(),
    val rusVariants: List<FipiVariant> = emptyList(),
    val resultsByVariantId: Map<String, MockExamResultEntity> = emptyMap(),
)

class FipiVariantsViewModel(app: Application) : AndroidViewModel(app) {
    private val resultDao = UserDataDatabase.get(app).mockExamResultDao()
    private val _state = MutableStateFlow(FipiVariantsUi())
    val state: StateFlow<FipiVariantsUi> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val ctx = getApplication<Application>()
            val math = FipiVariantsRepository.getMathVariants(ctx)
            val rus = FipiVariantsRepository.getRusVariants(ctx)
            // Резолвим прошлые прохождения по variant.id: для fipi-источника
            // мы храним scheduled_date = variant.id (см. ниже в Runner — для
            // ФИПИ записываем variant id как scheduled_date).
            // Для простоты: получаем все fipi-результаты и группируем по
            // scheduled_date (мы используем его как variant id для fipi).
            val rawResults = resultDao.getAll().filter { it.source == "fipi" }
            val grouped = rawResults.groupBy { it.scheduledDate }
                .mapValues { (_, results) -> results.maxByOrNull { it.completedDate }!! }
            _state.value = FipiVariantsUi(
                loading = false,
                mathVariants = math,
                rusVariants = rus,
                resultsByVariantId = grouped,
            )
        }
    }
}

@Composable
fun FipiVariantsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onVariantClick: (FipiVariant) -> Unit,
    vm: FipiVariantsViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Варианты КИМ ФИПИ",
                subtitle = "Официальные варианты для тренировки",
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
                st.mathVariants.isEmpty() && st.rusVariants.isEmpty() -> {
                    Text(
                        "Нет вариантов. Запусти parser/scrapers/parse_fipi_variants.py.",
                        color = LabelSecondary,
                        modifier = Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (st.mathVariants.isNotEmpty()) {
                        item("math_title") { SectionLabel("📐 Математика") }
                        items(st.mathVariants, key = { it.id }) { v ->
                            VariantCard(
                                variant = v,
                                result = st.resultsByVariantId[v.id],
                                onClick = { onVariantClick(v) },
                            )
                        }
                    }
                    if (st.rusVariants.isNotEmpty()) {
                        item("rus_title") { SectionLabel("✍️ Русский") }
                        items(st.rusVariants, key = { it.id }) { v ->
                            VariantCard(
                                variant = v,
                                result = st.resultsByVariantId[v.id],
                                onClick = { onVariantClick(v) },
                            )
                        }
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
private fun VariantCard(variant: FipiVariant, result: MockExamResultEntity?, onClick: () -> Unit) {
    AppleCard(onClick = onClick, paddingDp = 16) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (result != null) SystemGreenTint else SystemBlueTint),
            ) {
                Text(if (result != null) "✓" else "📂", fontSize = 20.sp)
            }
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(variant.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Label)
                Text(
                    text = "${variant.taskCount} заданий",
                    fontSize = 12.sp,
                    color = LabelSecondary,
                )
                if (result != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Балл: ${result.score} · ${result.correct}/${result.total}",
                        fontSize = 12.sp,
                        color = SystemGreen,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Text("›", fontSize = 22.sp, color = LabelTertiary)
        }
    }
}
