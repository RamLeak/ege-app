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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.SubjectWithCount
import com.daniel.ege100.ui.common.ContentInsets
import com.daniel.ege100.ui.common.ListCardRow
import com.daniel.ege100.ui.common.ScreenTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow<List<SubjectWithCount>?>(null)
    val state: StateFlow<List<SubjectWithCount>?> = _state.asStateFlow()

    init {
        viewModelScope.launch { _state.value = dao.getSubjectsWithCount() }
    }
}

private fun subjectIcon(slug: String): String = when (slug) {
    "mathb" -> "📐"
    "rus" -> "✍️"
    else -> "📚"
}

@Composable
fun CatalogScreen(
    onSubjectClick: (Long) -> Unit,
    contentPadding: PaddingValues,
    vm: CatalogViewModel = viewModel(),
) {
    val subjects by vm.state.collectAsState()
    Scaffold(
        topBar = { ScreenTopBar(title = "Решать") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            val list = subjects
            if (list == null) {
                Text(
                    text = "Загрузка корпуса…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = ContentInsets,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = "Каталог",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(list, key = { it.id }) { subj ->
                        ListCardRow(
                            leading = subjectIcon(subj.slug),
                            title = subj.title,
                            subtitle = "${subj.typeCount} типов · ${subj.problemCount} задач",
                            onClick = { onSubjectClick(subj.id) },
                        )
                    }
                }
            }
        }
    }
}
