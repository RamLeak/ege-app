package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.data.SubjectEntity
import com.daniel.ege100.data.SubtypeWithCount
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelSecondary
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

                LazyColumn(
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
                        AppleListRow(
                            title = "Все задачи типа",
                            subtitle = "Тренажёр · ${st.totalProblems} задач",
                            leadingEmoji = "🎯",
                            leadingTint = Color(0x1F30D158),
                            onClick = { onTrainerClick(typeId) },
                        )
                    }
                    items(list, key = { it.id }) { sub ->
                        val subSubtitle = buildString {
                            if (!sub.kesCode.isNullOrBlank()) append("КЭС ${sub.kesCode}  ·  ")
                            append("${sub.problemCount} задач")
                        }
                        AppleListRow(
                            title = sub.title,
                            subtitle = subSubtitle,
                            onClick = { onSubtypeClick(sub.id, typeId) },
                        )
                    }
                }
            }
        }
    }
}
