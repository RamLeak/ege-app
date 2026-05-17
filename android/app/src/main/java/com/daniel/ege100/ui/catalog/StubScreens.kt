package com.daniel.ege100.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.ScreenTopBar

@Composable
fun HomeStubScreen(contentPadding: PaddingValues) {
    Scaffold(
        topBar = { ScreenTopBar(title = "Главная") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Главный экран — Фаза 3.\nПредиктор балла, радар, streak, цитата.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun JournalStubScreen(contentPadding: PaddingValues) {
    Scaffold(
        topBar = { ScreenTopBar(title = "Журнал") },
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Журнал ошибок — Фаза 3.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
