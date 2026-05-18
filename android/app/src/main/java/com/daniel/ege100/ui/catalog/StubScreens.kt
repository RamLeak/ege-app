package com.daniel.ege100.ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary

@Composable
fun HomeStubScreen(contentPadding: PaddingValues) {
    Scaffold(
        topBar = { LargeTitleBar(title = "Главная", subtitle = "ЕГЭ-2027") },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Главный экран",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Text(
                    text = "Предиктор балла, радар, streak, цитата дня — в Phase 3.",
                    fontSize = 15.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
fun JournalStubScreen(contentPadding: PaddingValues) {
    Scaffold(
        topBar = { LargeTitleBar(title = "Журнал", subtitle = "История ошибок") },
        containerColor = Bg,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(contentPadding)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Журнал ошибок",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Label,
                )
                Text(
                    text = "Список заваленных задач с разбором — Phase 3.",
                    fontSize = 15.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
