package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.ContentInsets
import com.daniel.ege100.ui.common.ScreenTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProblemDetailViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow<ProblemEntity?>(null)
    val state: StateFlow<ProblemEntity?> = _state.asStateFlow()

    fun load(problemId: Long) {
        viewModelScope.launch { _state.value = dao.getProblem(problemId) }
    }
}

/**
 * Stage 2: заглушка экрана задачи. Показывает сырой HTML условия + метаданные.
 * Полноценный рендер (формулы, иллюстрации, проверка ответа, кнопки) — Stage 3.
 */
@Composable
fun ProblemDetailScreen(
    problemId: Long,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    vm: ProblemDetailViewModel = viewModel(),
) {
    LaunchedEffect(problemId) { vm.load(problemId) }
    val problem by vm.state.collectAsState()

    Scaffold(
        topBar = { ScreenTopBar(title = "Задача #${problemId}", onBack = onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            val p = problem
            if (p == null) {
                Text(
                    text = "Загрузка…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(ContentInsets),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppleCard {
                        Text(
                            text = "Условие (сырой HTML — Stage 2 заглушка):",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = p.statementHtml,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    AppleCard {
                        Text(
                            text = "Метаданные",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val rows = listOfNotNull(
                            "sdamgia_id" to p.sdamgiaId,
                            "subject_id" to p.subjectId.toString(),
                            "type_id" to p.typeId.toString(),
                            "subtype_id" to (p.subtypeId?.toString() ?: "—"),
                            p.answer?.let { "ответ" to it },
                            p.answerFormat?.let { "формат" to it },
                            p.source?.let { "источник" to it },
                            p.difficulty?.let { "сложность" to it },
                            "scraped_at" to p.scrapedAt,
                        )
                        rows.forEach { (k, v) ->
                            Text(
                                text = "$k: $v",
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    Text(
                        text = "Полноценный рендер (формулы через Coil, проверка ответа, решение) — Stage 3.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}
