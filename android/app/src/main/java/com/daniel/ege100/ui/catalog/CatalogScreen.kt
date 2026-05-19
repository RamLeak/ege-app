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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.SubjectWithCount
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelSecondary
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

private fun subjectTint(slug: String): Color = when (slug) {
    "mathb" -> Color(0x1F0A84FF)
    "rus" -> Color(0x1FFF9F0A)
    else -> Color(0x1F0A84FF)
}

@Composable
fun CatalogScreen(
    onSubjectClick: (Long) -> Unit,
    contentPadding: PaddingValues,
    vm: CatalogViewModel = viewModel(),
) {
    val subjects by vm.state.collectAsState()
    Scaffold(
        topBar = { LargeTitleBar(title = "Решать") },
        containerColor = Bg,
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
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item {
                        Text(
                            text = "Каталог",
                            fontSize = 15.sp,
                            color = LabelSecondary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 6.dp),
                        )
                    }
                    items(list, key = { it.id }) { subj ->
                        AppleListRow(
                            title = subj.title,
                            subtitle = "${subj.typeCount} типов · ${subj.problemCount} задач",
                            leadingEmoji = subjectIcon(subj.slug),
                            leadingTint = subjectTint(subj.slug),
                            onClick = { onSubjectClick(subj.id) },
                        )
                    }
                }
            }
        }
    }
}
