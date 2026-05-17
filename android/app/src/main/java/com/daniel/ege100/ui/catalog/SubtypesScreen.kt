package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.data.SubtypeWithCount
import com.daniel.ege100.ui.common.ContentInsets
import com.daniel.ege100.ui.common.ListCardRow
import com.daniel.ege100.ui.common.ScreenTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubtypesState(
    val type: ProblemTypeEntity? = null,
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
            val subtypes = dao.getSubtypesByType(typeId)
            val total = dao.countProblemsByType(typeId)
            _state.value = SubtypesState(type = type, subtypes = subtypes, totalProblems = total)
        }
    }
}

@Composable
fun SubtypesScreen(
    typeId: Long,
    onBack: () -> Unit,
    onTrainerClick: (typeId: Long) -> Unit,
    onSubtypeClick: (subtypeId: Long, typeId: Long) -> Unit,
    contentPadding: PaddingValues,
    vm: SubtypesViewModel = viewModel(),
) {
    LaunchedEffect(typeId) { vm.load(typeId) }
    val st by vm.state.collectAsState()
    val title = st.type?.let { "№${it.number}  ${it.title}" } ?: "Подвиды"

    Scaffold(
        topBar = { ScreenTopBar(title = title, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = ContentInsets,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("trainer") {
                        ListCardRow(
                            leading = "🎯",
                            title = "Все задачи типа (тренажёр)",
                            subtitle = "${st.totalProblems} задач",
                            onClick = { onTrainerClick(typeId) },
                        )
                    }
                    items(list, key = { it.id }) { st2 ->
                        val sub = buildString {
                            if (!st2.kesCode.isNullOrBlank()) append("КЭС ${st2.kesCode}  ·  ")
                            append("${st2.problemCount} задач")
                        }
                        ListCardRow(
                            title = st2.title,
                            subtitle = sub,
                            onClick = { onSubtypeClick(st2.id, typeId) },
                        )
                    }
                }
            }
        }
    }
}
