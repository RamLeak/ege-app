package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.data.ProblemSubtypeEntity
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.ContentInsets
import com.daniel.ege100.ui.common.ScreenTopBar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

data class ProblemListState(
    val type: ProblemTypeEntity? = null,
    val subtype: ProblemSubtypeEntity? = null,
    val problems: List<ProblemEntity> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val canLoadMore: Boolean = false,
)

class ProblemListViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow(ProblemListState())
    val state: StateFlow<ProblemListState> = _state.asStateFlow()

    private var typeId: Long = 0
    private var subtypeId: Long? = null

    fun init(typeId: Long, subtypeId: Long?) {
        if (this.typeId == typeId && this.subtypeId == subtypeId && _state.value.problems.isNotEmpty()) return
        this.typeId = typeId
        this.subtypeId = subtypeId
        viewModelScope.launch {
            val type = dao.getType(typeId)
            val subtype = subtypeId?.let { dao.getSubtype(it) }
            val total = if (subtypeId != null) dao.countProblemsBySubtype(subtypeId) else dao.countProblemsByType(typeId)
            val page = fetchPage(offset = 0)
            _state.value = ProblemListState(
                type = type,
                subtype = subtype,
                problems = page,
                total = total,
                loading = false,
                canLoadMore = page.size < total,
            )
        }
    }

    fun loadMore() {
        val cur = _state.value
        if (cur.loading || !cur.canLoadMore) return
        _state.value = cur.copy(loading = true)
        viewModelScope.launch {
            val next = fetchPage(offset = cur.problems.size)
            val combined = cur.problems + next
            _state.value = cur.copy(
                problems = combined,
                loading = false,
                canLoadMore = combined.size < cur.total,
            )
        }
    }

    private suspend fun fetchPage(offset: Int): List<ProblemEntity> {
        val sid = subtypeId
        return if (sid != null) {
            dao.getProblemsBySubtype(sid, PAGE_SIZE, offset)
        } else {
            dao.getProblemsByType(typeId, PAGE_SIZE, offset)
        }
    }
}

/**
 * Снимает HTML-теги и возвращает первые 100 символов условия — достаточно,
 * чтобы человек узнал задачу. Полноценный рендер (формулы, иллюстрации) —
 * в Stage 3 на экране задачи.
 */
private val TAG_REGEX = Regex("<[^>]*>")
private val WS_REGEX = Regex("\\s+")

private fun preview(html: String, limit: Int = 100): String {
    val cleaned = html.replace(TAG_REGEX, " ").replace("&nbsp;", " ").replace(WS_REGEX, " ").trim()
    return if (cleaned.length <= limit) cleaned else cleaned.take(limit).trimEnd() + "…"
}

@Composable
fun ProblemListScreen(
    typeId: Long,
    subtypeId: Long?,
    onBack: () -> Unit,
    onProblemClick: (Long) -> Unit,
    contentPadding: PaddingValues,
    vm: ProblemListViewModel = viewModel(),
) {
    LaunchedEffect(typeId, subtypeId) { vm.init(typeId, subtypeId) }
    val st by vm.state.collectAsState()

    val title = when {
        st.subtype != null -> st.subtype!!.title
        st.type != null -> "№${st.type!!.number}  ·  все задачи"
        else -> "Задачи"
    }

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
            if (st.loading && st.problems.isEmpty()) {
                Text(
                    text = "Загрузка задач…",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    contentPadding = ContentInsets,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("counter") {
                        Text(
                            text = "Показано ${st.problems.size} из ${st.total}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(st.problems, key = { it.id }) { p ->
                        AppleCard(onClick = { onProblemClick(p.id) }) {
                            Text(
                                text = "sdamgia_id: ${p.sdamgiaId}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = preview(p.statementHtml),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    if (st.canLoadMore) {
                        item("loadmore") {
                            Button(
                                onClick = { vm.loadMore() },
                                enabled = !st.loading,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                            ) {
                                Text(if (st.loading) "Загрузка…" else "Загрузить ещё")
                            }
                        }
                    }
                }
            }
        }
    }
}
