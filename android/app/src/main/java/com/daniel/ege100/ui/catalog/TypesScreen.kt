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
import com.daniel.ege100.ui.common.ContentInsets
import com.daniel.ege100.ui.common.ListCardRow
import com.daniel.ege100.ui.common.ScreenTopBar
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
        topBar = { ScreenTopBar(title = title, onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = ContentInsets,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(list, key = { it.id }) { t ->
                        ListCardRow(
                            leading = "№${t.number}",
                            title = t.title,
                            subtitle = "${t.problemCount} задач",
                            onClick = { onTypeClick(t.id) },
                        )
                    }
                }
            }
        }
    }
}
