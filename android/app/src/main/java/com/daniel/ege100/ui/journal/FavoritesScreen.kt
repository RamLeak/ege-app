package com.daniel.ege100.ui.journal

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.FavoritesStore
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Stage 5 part Д — экран избранных задач.
 *
 * Подписывается на FavoritesStore. При добавлении/удалении звезды список
 * автоматически обновляется. Каждая карточка ведёт в ProblemDetailScreen
 * (через типы — но мы не знаем subtype, поэтому открываем с typeId из
 * задачи и subtypeId=null).
 */
data class FavoriteRow(
    val problem: ProblemEntity,
)

data class FavoritesUi(
    val loading: Boolean = true,
    val items: List<FavoriteRow> = emptyList(),
)

class FavoritesViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val _state = MutableStateFlow(FavoritesUi())
    val state: StateFlow<FavoritesUi> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            FavoritesStore.favoritesFlow(app).collectLatest { ids ->
                if (ids.isEmpty()) {
                    _state.value = FavoritesUi(loading = false, items = emptyList())
                } else {
                    val problems = dao.getProblemsByIds(ids.toList())
                    _state.value = FavoritesUi(
                        loading = false,
                        items = problems.map { FavoriteRow(it) },
                    )
                }
            }
        }
    }
}

private val TAG_REGEX = Regex("<[^>]*>")
private val WS_REGEX = Regex("\\s+")

private fun preview(html: String, limit: Int = 120): String {
    val cleaned = html.replace(TAG_REGEX, " ").replace("&nbsp;", " ").replace(WS_REGEX, " ").trim()
    return if (cleaned.length <= limit) cleaned else cleaned.take(limit).trimEnd() + "…"
}

@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onProblemClick: (problemId: Long, typeId: Long, subtypeId: Long?) -> Unit,
    contentPadding: PaddingValues,
    vm: FavoritesViewModel = viewModel(),
) {
    val st by vm.state.collectAsState()
    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Избранное",
                subtitle = if (!st.loading && st.items.isNotEmpty()) "${st.items.size} задач" else null,
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
                st.items.isEmpty() -> Text(
                    text = "Пусто — добавь задачу через звезду в шапке",
                    color = LabelTertiary,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                )
                else -> SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(st.items, key = { it.problem.id }) { row ->
                        val p = row.problem
                        AppleCard(
                            onClick = { onProblemClick(p.id, p.typeId, p.subtypeId) },
                            paddingDp = 16,
                        ) {
                            Text(
                                text = preview(p.statementHtml),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = Label,
                                lineHeight = 21.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}
