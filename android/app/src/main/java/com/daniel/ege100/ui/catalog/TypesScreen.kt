package com.daniel.ege100.ui.catalog

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.EgeDatabase
import com.daniel.ege100.data.ProgressRepository
import com.daniel.ege100.data.SubjectEntity
import com.daniel.ege100.data.TypeWithCount
import com.daniel.ege100.ui.common.AppleCard
import com.daniel.ege100.ui.common.AppleProgressBar
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemGreen
import com.daniel.ege100.ui.theme.SystemOrange
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TypesState(
    val subject: SubjectEntity? = null,
    val types: List<TypeWithCount>? = null,
    /** Phase 4 Stage P4-D2 part Б — typeId → progress (solved/total). */
    val progress: Map<Long, ProgressRepository.TypeProgress> = emptyMap(),
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
            // Прогресс грузим отдельно — он может занять больше времени
            // (запросы в две БД). UI покажет список без баров → потом
            // допишет цвета + N/M.
            val progress = ProgressRepository.getTypeProgress(getApplication(), subjectId)
            _state.value = _state.value.copy(progress = progress)
        }
    }

    /** Обновить только прогресс — после возврата с экрана задачи. */
    fun refreshProgress() {
        val subjectId = _state.value.subject?.id ?: return
        viewModelScope.launch {
            val progress = ProgressRepository.getTypeProgress(getApplication(), subjectId)
            _state.value = _state.value.copy(progress = progress)
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
    LaunchedEffect(subjectId) { vm.load(subjectId) }
    // Phase 4 Stage P4-D2 part Б — обновление прогресса после возврата.
    LaunchedEffect(Unit) { vm.refreshProgress() }
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
                        TypeCard(
                            type = t,
                            progress = st.progress[t.id],
                            onClick = { onTypeClick(t.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase 4 Stage P4-D2 part Б (Convention #65) — карточка типа задачи с
 * прогресс-баром и счётчиком N/M.
 *
 * Цвета бара (по % решённых):
 *   - 80%+ → SystemGreen (освоено).
 *   - 50-80% → SystemBlue (хороший прогресс).
 *   - 0-50% → SystemOrange (надо ещё решать).
 *   - 0% → LabelTertiary серый (не начато).
 *
 * `progress = null` при первой загрузке — показываем «… задач» без бара,
 * чтобы пользователь не видел вспышку «0 из N» (это пугает).
 */
@Composable
private fun TypeCard(
    type: TypeWithCount,
    progress: ProgressRepository.TypeProgress?,
    onClick: () -> Unit,
) {
    AppleCard(onClick = onClick, paddingDp = 18, cornerRadiusDp = 22) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "№${type.number}",
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = SystemBlue,
                modifier = Modifier.width(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = type.title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${type.problemCount} задач",
                    fontSize = 13.sp,
                    color = LabelSecondary,
                )
            }
            Text(text = "›", fontSize = 22.sp, color = LabelTertiary)
        }
        if (progress != null && progress.total > 0) {
            Spacer(Modifier.height(10.dp))
            val ratio = progress.ratio
            val barColor = when {
                ratio >= 0.8f -> SystemGreen
                ratio >= 0.5f -> SystemBlue
                ratio > 0f -> SystemOrange
                else -> LabelTertiary
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppleProgressBar(
                    progress = ratio,
                    height = 6.dp,
                    fillColor = barColor,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${progress.solved}/${progress.total}",
                    fontSize = 12.sp,
                    color = LabelTertiary,
                    modifier = Modifier.widthIn(min = 44.dp),
                )
            }
        }
    }
}
