package com.daniel.ege100.ui.journal

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.FavoritesStore
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Stage 5 part Д — главный экран таба «Журнал».
 *
 * Разделы:
 *   - ⭐ Избранные задачи (с счётчиком) → FavoritesScreen
 *   - 📝 Ошибки → stub (Phase 3)
 *   - 📊 Статистика → stub (Phase 3)
 */
class JournalViewModel(app: Application) : AndroidViewModel(app) {
    val favoritesCount: StateFlow<Int> = FavoritesStore.favoritesFlow(app)
        .map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0,
        )
}

@Composable
fun JournalScreen(
    contentPadding: PaddingValues,
    onFavoritesClick: () -> Unit,
    vm: JournalViewModel = viewModel(),
) {
    val count by vm.favoritesCount.collectAsState()
    Scaffold(
        topBar = { LargeTitleBar(title = "Журнал", subtitle = "Избранное и история") },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    AppleListRow(
                        title = "Избранные задачи",
                        subtitle = if (count > 0) "$count задач" else "Пока пусто",
                        leadingEmoji = "⭐",
                        leadingTint = Color(0x26FFD60A),
                        onClick = onFavoritesClick,
                    )
                }
                item {
                    AppleListRow(
                        title = "Ошибки",
                        subtitle = "Phase 3 — журнал заваленных",
                        leadingEmoji = "📝",
                        leadingTint = Color(0x26FF453A),
                        onClick = {},
                    )
                }
                item {
                    AppleListRow(
                        title = "Статистика",
                        subtitle = "Phase 3 — прогресс и радар",
                        leadingEmoji = "📊",
                        leadingTint = Color(0x260A84FF),
                        onClick = {},
                    )
                }
            }
        }
    }
}
