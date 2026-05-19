package com.daniel.ege100.ui.accent

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import com.daniel.ege100.data.AccentCategory
import com.daniel.ege100.data.AccentWordsRepository
import com.daniel.ege100.ui.common.AppleListRow
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.LabelTertiary
import com.daniel.ege100.ui.theme.SystemBlue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private data class CategoryEntry(
    val id: String?,
    val title: String,
    val count: Int,
    val emoji: String,
    val tint: Color,
)

class AccentCategoriesViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<List<AccentCategory>?>(null)
    val state: StateFlow<List<AccentCategory>?> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = AccentWordsRepository.load(getApplication()).categories
        }
    }
}

private fun emojiFor(id: String): String = when (id) {
    "nouns" -> "📦"
    "adjectives" -> "🎨"
    "verbs" -> "⚡"
    "participles" -> "🔄"
    "gerunds" -> "🎯"
    "adverbs" -> "🏃"
    else -> "🔤"
}

private fun tintFor(id: String): Color = when (id) {
    "nouns" -> Color(0x1F0A84FF)
    "adjectives" -> Color(0x1FFF9F0A)
    "verbs" -> Color(0x1F30D158)
    "participles" -> Color(0x1FFFD60A)
    "gerunds" -> Color(0x1FFF453A)
    "adverbs" -> Color(0x1FBF5AF2)
    else -> Color(0x1F0A84FF)
}

@Composable
fun AccentCategoriesScreen(
    onBack: () -> Unit,
    onCategoryClick: (categoryId: String?, defaultOrder: String) -> Unit,
    contentPadding: PaddingValues,
    vm: AccentCategoriesViewModel = viewModel(),
) {
    val cats by vm.state.collectAsState()

    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Ударения",
                subtitle = "Орфоэпический словник ФИПИ",
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
            val list = cats
            if (list == null) {
                Text(
                    text = "Загрузка словника…",
                    color = LabelSecondary,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                val total = list.sumOf { it.words.size }
                val entries = list.map {
                    CategoryEntry(
                        id = it.id,
                        title = it.title,
                        count = it.words.size,
                        emoji = emojiFor(it.id),
                        tint = tintFor(it.id),
                    )
                }
                SmoothLazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item("hint") {
                        Text(
                            text = "Выбери раздел для тренировки",
                            fontSize = 15.sp,
                            color = LabelSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp, top = 4.dp),
                        )
                    }
                    items(entries, key = { it.id ?: "all" }) { e ->
                        AppleListRow(
                            title = e.title,
                            subtitle = "${e.count} слов",
                            leadingEmoji = e.emoji,
                            leadingTint = e.tint,
                            onClick = { onCategoryClick(e.id, "alphabetical") },
                        )
                    }
                    item("divider") {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = "ВСЕ КАТЕГОРИИ ВМЕСТЕ",
                            fontSize = 12.sp,
                            color = LabelTertiary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
                    }
                    item("all_random") {
                        AppleListRow(
                            title = "Все слова",
                            subtitle = "Перемешать $total слов",
                            leadingEmoji = "🎲",
                            leadingTint = Color(0x1F0A84FF),
                            onClick = { onCategoryClick(null, "random") },
                        )
                    }
                    item("all_alpha") {
                        AppleListRow(
                            title = "Все по алфавиту",
                            subtitle = "А → Я подряд, $total слов",
                            leadingEmoji = "🔤",
                            leadingTint = Color(0x1FBF5AF2),
                            onClick = { onCategoryClick(null, "alphabetical") },
                        )
                    }
                }
            }
        }
    }
}
