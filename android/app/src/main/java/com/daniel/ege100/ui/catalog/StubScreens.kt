package com.daniel.ege100.ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daniel.ege100.data.UserProfile
import com.daniel.ege100.data.UserProfileStore
import com.daniel.ege100.ui.common.LargeTitleBar
import com.daniel.ege100.ui.theme.Bg
import com.daniel.ege100.ui.theme.Label
import com.daniel.ege100.ui.theme.LabelSecondary
import com.daniel.ege100.ui.theme.SystemBlue
import com.daniel.ege100.ui.theme.SystemBlueTint

@Composable
fun HomeStubScreen(
    contentPadding: PaddingValues,
    onProfileClick: () -> Unit = {},
) {
    val context = LocalContext.current
    val profileFlow = remember(context) { UserProfileStore.profileFlow(context) }
    val profile by profileFlow.collectAsState(initial = UserProfile())
    Scaffold(
        topBar = {
            LargeTitleBar(
                title = "Главная",
                subtitle = "ЕГЭ-2027",
                rightContent = {
                    AvatarChip(
                        initial = profile.initial,
                        onClick = onProfileClick,
                    )
                },
            )
        },
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
                    text = "Предиктор балла, радар, streak, цитата дня — в Stage P3-B.",
                    fontSize = 15.sp,
                    color = LabelSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Phase 3 Stage A part Б3 — круглая аватарка 36dp в правом верхнем углу
 * Главного экрана. Показывает первую букву имени или 👤. Тап → Профиль.
 */
@Composable
private fun AvatarChip(initial: String?, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SystemBlueTint)
            .clickable { onClick() },
    ) {
        if (initial != null) {
            Text(
                text = initial,
                color = SystemBlue,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
        } else {
            Text(text = "👤", fontSize = 18.sp)
        }
    }
}
