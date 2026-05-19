package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import com.daniel.ege100.ui.common.SmoothLazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.SubjectEntity
import com.daniel.ege100.data.TypeWithCount
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelSecondary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TypesState(
    val subject: SubjectEntity? = null,
    val types: List<TypeWithCount>? = null,
)

class TypesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow(TypesState())
    val state: StateFlow<TypesState> = _state.asStateFlow()

    fun load(subjectId: Long) {
        viewModelScope.launch {
            val subject = dao.getSubject(subjectId)
            val types = dao.getTypesBySubject(subjectId)
            _state.value = TypesState(subject = subject, types = types)
        }
    }
}

@Composable
fun TypesScreen(
    subjectId: Long,
    onBack: () -> Unit,
    onTypeClick: (Long) -> Unit,
    contentPadding: PaddingValues,
    vm: TypesViewModel = viewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(subjectId) { vm.load(subjectId) }
    val st by vm.state.collectAsState()
    val title = st.subject?.title ?: "Тип задачи"

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = title,
                subtitle = if (st.types != null) "${st.types!!.size} типов задач" else null,
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
            val list = st.types
            if (list == null) {
                Text(
                    text = "Загрузка…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { t ->
                        AppleListRow(
                            title = "№${t.number}  ·  ${t.title}",
                            subtitle = "${t.problemCount} задач",
                            onClick = { onTypeClick(t.id) },
                        )
                    }
                }
            }
        }
    }
}
