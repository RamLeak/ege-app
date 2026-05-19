package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
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
import com.daniel.ege100.data.ProblemEntity
import com.daniel.ege100.data.ProblemSubtypeEntity
import com.daniel.ege100.data.ProblemTypeEntity
import com.daniel.ege100.data.UserDataDatabase
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.common.SecondaryButton
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.BgElevated
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemGreenTint
import com.daniel.ege100.ui.theme.SystemRed
import com.daniel.ege100.ui.theme.SystemRedTint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 50

/**
 * Phase 4 Stage P4-D2 part А (Convention #64) — статус последней попытки
 * для каждой карточки задачи. CORRECT → светло-зелёный фон + ✓; WRONG →
 * светло-красный + ✗; NOT_ATTEMPTED → стандартный фон + ○.
 */
enum class AttemptStatus { CORRECT, WRONG, NOT_ATTEMPTED }

data class ProblemListState(
    val type: ProblemTypeEntity? = null,
    val subtype: ProblemSubtypeEntity? = null,
    val problems: List<ProblemEntity> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = true,
    val canLoadMore: Boolean = false,
    /** problem_id → последняя попытка (true = correct, false = wrong). */
    val lastAttempts: Map<Long, Boolean> = emptyMap(),
)

class ProblemListViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = EgeDatabase.get(app).catalogDao()
    private val attemptDao = UserDataDatabase.get(app).attemptLogDao()
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
            val attempts = fetchAttempts(page.map { it.id })
            _state.value = ProblemListState(
                type = type,
                subtype = subtype,
                problems = page,
                total = total,
                loading = false,
                canLoadMore = page.size < total,
                lastAttempts = attempts,
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
            // Только новые ID — старые статусы уже в Map. Дозагрузка не
            // переписывает существующие записи.
            val newAttempts = fetchAttempts(next.map { it.id })
            _state.value = cur.copy(
                problems = combined,
                loading = false,
                canLoadMore = combined.size < cur.total,
                lastAttempts = cur.lastAttempts + newAttempts,
            )
        }
    }

    /**
     * Обновить attempt-статусы для уже загруженных задач. Зовётся при
     * возврате на экран — пользователь мог решить задачу из этого списка
     * и нажать ←, мы должны перекрасить карточку. LaunchedEffect(Unit)
     * в ProblemListScreen триггерит это.
     */
    fun refreshAttempts() {
        val ids = _state.value.problems.map { it.id }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val attempts = fetchAttempts(ids)
            _state.value = _state.value.copy(lastAttempts = attempts)
        }
    }

    private suspend fun fetchAttempts(ids: List<Long>): Map<Long, Boolean> {
        if (ids.isEmpty()) return emptyMap()
        return attemptDao.getLastAttempts(ids).associate { it.problemId to it.isCorrect }
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

private val TAG_REGEX = Regex("<[^>]*>")
private val WS_REGEX = Regex("\\s+")

private fun preview(html: String, limit: Int = 120): String {
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
    // Phase 4 Stage P4-D2 part А — обновление подсветки после возврата
    // с ProblemDetailScreen. Compose Navigation 2.8 пересоздаёт composable
    // при возврате через popBackStack, и `LaunchedEffect(Unit)` срабатывает
    // заново — пользователь видит свежий фон после решения задачи.
    LaunchedEffect(Unit) { vm.refreshAttempts() }
    val st by vm.state.collectAsState()

    val title = st.subtype?.title ?: st.type?.let { "№${it.number}" } ?: "Задачи"
    val subtitle = if (st.subtype != null) st.type?.let { "№${it.number}  ·  ${it.title}" }
                    else if (st.type != null) "Все задачи типа"
                    else null

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
            if (st.loading && st.problems.isEmpty()) {
                Text(
                    text = "Загрузка задач…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("counter") {
                        Text(
                            text = "Показано ${st.problems.size} из ${st.total}",
                            fontSize = 13.sp,
                            color = LabelTertiary,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                        )
                    }
                    items(st.problems, key = { it.id }) { p ->
                        val status = when (st.lastAttempts[p.id]) {
                            true -> AttemptStatus.CORRECT
                            false -> AttemptStatus.WRONG
                            null -> AttemptStatus.NOT_ATTEMPTED
                        }
                        ProblemPreviewCard(
                            problem = p,
                            status = status,
                            onClick = { onProblemClick(p.id) },
                        )
                    }
                    if (st.canLoadMore) {
                        item("loadmore") {
                            Spacer(Modifier.height(4.dp))
                            SecondaryButton(
                                text = if (st.loading) "Загрузка…" else "Загрузить ещё",
                                onClick = { vm.loadMore() },
                                enabled = !st.loading,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(Modifier.height(20.dp))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Phase 4 Stage P4-D2 part А (Convention #64) — карточка задачи с
 * подсветкой по последней попытке.
 *
 * Цвета (Convention #15 + Color palette с динамическими getters):
 *   - CORRECT: SystemGreenTint фон + SystemGreen border + ✓ зелёный.
 *   - WRONG: SystemRedTint фон + SystemRed border + ✗ красный.
 *   - NOT_ATTEMPTED: BgElevated фон без border + ○ серый.
 *
 * Tints уже подобраны (~15% alpha) — в обеих темах читаемо. Border 1dp
 * не показываем для NOT_ATTEMPTED чтобы не плодить визуальный шум на
 * списке из 50+ нерешённых задач.
 */
@Composable
private fun ProblemPreviewCard(
    problem: ProblemEntity,
    status: AttemptStatus,
    onClick: () -> Unit,
) {
    val bgColor = when (status) {
        AttemptStatus.CORRECT -> SystemGreenTint
        AttemptStatus.WRONG -> SystemRedTint
        AttemptStatus.NOT_ATTEMPTED -> BgElevated
    }
    val borderColor = when (status) {
        AttemptStatus.CORRECT -> SystemGreen
        AttemptStatus.WRONG -> SystemRed
        AttemptStatus.NOT_ATTEMPTED -> Color.Transparent
    }
    val statusIcon = when (status) {
        AttemptStatus.CORRECT -> "✓"
        AttemptStatus.WRONG -> "✗"
        AttemptStatus.NOT_ATTEMPTED -> "○"
    }
    val statusColor = when (status) {
        AttemptStatus.CORRECT -> SystemGreen
        AttemptStatus.WRONG -> SystemRed
        AttemptStatus.NOT_ATTEMPTED -> LabelTertiary
    }

    val shape = RoundedCornerShape(16.dp)
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bgColor)
            .then(
                if (status == AttemptStatus.NOT_ATTEMPTED) Modifier
                else Modifier.border(width = 1.dp, color = borderColor.copy(alpha = 0.35f), shape = shape),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(statusColor.copy(alpha = 0.18f)),
            ) {
                Text(
                    text = statusIcon,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = preview(problem.statementHtml),
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                color = Label,
                lineHeight = 21.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = "›", fontSize = 22.sp, color = LabelTertiary)
        }
    }
}
