package com.daniel.ege100.ui.catalog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import com.daniel.ege100.ui.common.SmoothLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.data.ProgressRepository
import com.daniel.ege100.data.SubjectEntity
import com.daniel.ege100.data.SubtypeWithCount
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.AppleProgressBar
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

private fun wordBlankTrainerTitle(typeNumber: Int): String = when (typeNumber) {
    9 -> "Тренажёр: Корни"
    10 -> "Тренажёр: Приставки"
    11 -> "Тренажёр: Суффиксы"
    12 -> "Тренажёр: Окончания и причастия"
    else -> "Тренажёр орфографии"
}

private fun wordBlankTrainerIcon(typeNumber: Int): String = when (typeNumber) {
    9 -> "🌱"
    10 -> "🧱"
    11 -> "🎀"
    12 -> "🌀"
    else -> "✏️"
}

private fun wordBlankTrainerTint(typeNumber: Int): Color = when (typeNumber) {
    9 -> Color(0x1F30D158)
    10 -> Color(0x1FFF9F0A)
    11 -> Color(0x1FBF5AF2)
    12 -> Color(0x1F0A84FF)
    else -> Color(0x1F0A84FF)
}

data class SubtypesState(
    val type: ProblemTypeEntity? = null,
    val subject: SubjectEntity? = null,
    val subtypes: List<SubtypeWithCount>? = null,
    val totalProblems: Int = 0,
    /** Phase 4 Stage P4-D2 part Б — subtypeId → progress. */
    val progress: Map<Long, ProgressRepository.SubtypeProgress> = emptyMap(),
    /** Прогресс по всему типу (для «🎯 Все задачи типа»). */
    val typeProgress: ProgressRepository.TypeProgress? = null,
)

class SubtypesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow(SubtypesState())
    val state: StateFlow<SubtypesState> = _state.asStateFlow()

    fun load(typeId: Long) {
        viewModelScope.launch {
            val type = dao.getType(typeId)
            val subject = type?.let { dao.getSubject(it.subjectId) }
            val subtypes = dao.getSubtypesByType(typeId)
            val total = dao.countProblemsByType(typeId)
            _state.value = SubtypesState(
                type = type,
                subject = subject,
                subtypes = subtypes,
                totalProblems = total,
            )
            refreshProgress()
        }
    }

    fun refreshProgress() {
        val typeId = _state.value.type?.id ?: return
        val subjectId = _state.value.subject?.id ?: return
        viewModelScope.launch {
            val sub = ProgressRepository.getSubtypeProgress(getApplication(), typeId)
            val typeMap = ProgressRepository.getTypeProgress(getApplication(), subjectId)
            _state.value = _state.value.copy(
                progress = sub,
                typeProgress = typeMap[typeId],
            )
        }
    }
}

@Composable
fun SubtypesScreen(
    typeId: Long,
    onBack: () -> Unit,
    onTrainerClick: (typeId: Long) -> Unit,
    onSubtypeClick: (subtypeId: Long, typeId: Long) -> Unit,
    onAccentTrainerClick: () -> Unit,
    onWordBlankTrainerClick: (typeNumber: Int) -> Unit,
    contentPadding: PaddingValues,
    vm: SubtypesViewModel = viewModel(),
) {
    LaunchedEffect(typeId) { vm.load(typeId) }
    LaunchedEffect(Unit) { vm.refreshProgress() }
    val st by vm.state.collectAsState()
    val title = st.type?.let { "№${it.number}" } ?: "Подвиды"
    val subtitle = st.type?.title

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = title,
                subtitle = subtitle,
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
            val list = st.subtypes
            if (list == null) {
                Text(
                    text = "Загрузка…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                val isRus = st.subject?.slug == "rus"
                val typeNumber = st.type?.number ?: 0
                val isAccentTrainerHost = isRus && typeNumber == 4
                val wordBlankType: Int? = if (isRus && typeNumber in listOf(9, 10, 11, 12)) typeNumber else null

                SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    if (isAccentTrainerHost) {
                        item("accent_trainer") {
                            AppleListRow(
                                title = "Тренажёр ударений",
                                subtitle = "Словник ФИПИ · 230 слов",
                                leadingEmoji = "🔤",
                                leadingTint = Color(0x1F0A84FF),
                                onClick = onAccentTrainerClick,
                            )
                        }
                    }
                    if (wordBlankType != null) {
                        item("word_blank_trainer") {
                            AppleListRow(
                                title = wordBlankTrainerTitle(wordBlankType),
                                subtitle = "Ввод буквы · тренажёр",
                                leadingEmoji = wordBlankTrainerIcon(wordBlankType),
                                leadingTint = wordBlankTrainerTint(wordBlankType),
                                onClick = { onWordBlankTrainerClick(wordBlankType) },
                            )
                        }
                    }
                    item("trainer") {
                        // «🎯 Все задачи типа» — оставляем как стандартный ListRow,
                        // но снизу добавляем прогресс-бар по всему типу.
                        TrainerCard(
                            totalProblems = st.totalProblems,
                            typeProgress = st.typeProgress,
                            onClick = { onTrainerClick(typeId) },
                        )
                    }
                    items(list, key = { it.id }) { sub ->
                        SubtypeCard(
                            sub = sub,
                            progress = st.progress[sub.id],
                            onClick = { onSubtypeClick(sub.id, typeId) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 4 Stage P4-D2 part Б (Convention #65) — карточка «🎯 Все задачи
 * типа» с прогресс-баром по всему типу.
 */
@Composable
private fun TrainerCard(
    totalProblems: Int,
    typeProgress: ProgressRepository.TypeProgress?,
    onClick: () -> Unit,
) {
    AppleCard(onClick = onClick, paddingDp = 18, cornerRadiusDp = 22) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "🎯", fontSize = 28.sp)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Все задачи типа",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Тренажёр · $totalProblems задач",
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
            }
            Text(text = "›", fontSize = 22.sp, color = LabelTertiary)
        }
        if (typeProgress != null && typeProgress.total > 0) {
            Spacer(Modifier.height(10.dp))
            val ratio = typeProgress.ratio
            val barColor = when {
                ratio >= 0.8f -> SystemGreen
                ratio >= 0.5f -> SystemBlue
                ratio > 0f -> SystemOrange
                else -> LabelTertiary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppleProgressBar(
                    progress = ratio,
                    height = 6.dp,
                    fillColor = barColor,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${typeProgress.solved}/${typeProgress.total}",
                    fontSize = 12.sp,
                    color = LabelTertiary,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
            }
        }
    }
}

/**
 * Phase 4 Stage P4-D2 part Б (Convention #65) — карточка подвида с
 * прогресс-баром. Цветовая логика аналогична TypeCard.
 */
@Composable
private fun SubtypeCard(
    sub: SubtypeWithCount,
    progress: ProgressRepository.SubtypeProgress?,
    onClick: () -> Unit,
) {
    AppleCard(onClick = onClick, paddingDp = 18, cornerRadiusDp = 22) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sub.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        if (!sub.kesCode.isNullOrBlank()) append("КЭС ${sub.kesCode}  ·  ")
                        append("${sub.problemCount} задач")
                    },
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
            }
            Text(text = "›", fontSize = 22.sp, color = LabelTertiary)
        }
        if (progress != null && progress.total > 0) {
            Spacer(Modifier.height(10.dp))
            val ratio = progress.ratio
            val barColor = when {
                ratio >= 0.8f -> SystemGreen
                ratio >= 0.5f -> SystemBlue
                ratio > 0f -> SystemOrange
                else -> LabelTertiary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppleProgressBar(
                    progress = ratio,
                    height = 6.dp,
                    fillColor = barColor,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${progress.solved}/${progress.total}",
                    fontSize = 12.sp,
                    color = LabelTertiary,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
            }
        }
    }
}
