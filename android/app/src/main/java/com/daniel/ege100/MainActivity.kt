package com.daniel.ege100

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.daniel.ege100.data.CorpusStats
import com.daniel.ege100.ui.theme.EgeTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import android.app.Application
import androidx.lifecycle.AndroidViewModel

sealed class DbState {
    data object Loading : DbState()
    data class Ready(val problemCount: Int) : DbState()
    data class Failed(val message: String) : DbState()
}

class DbStatusViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow<DbState>(DbState.Loading)
    val state: StateFlow<DbState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            CorpusStats.countProblems(getApplication())
                .onSuccess { _state.value = DbState.Ready(it) }
                .onFailure { _state.value = DbState.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EgeTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    DbStatusScreen()
                }
            }
        }
    }
}

@Composable
fun DbStatusScreen(vm: DbStatusViewModel = viewModel()) {
    LocalContext.current  // ensure ViewModel can resolve application
    val state by vm.state.collectAsState()
    LaunchedEffect(Unit) { vm.load() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            DbState.Loading -> Text(
                text = "Открываю corpus.db…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 17.sp,
            )
            is DbState.Ready -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = "БД подключена",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Задач в корпусе: ${s.problemCount}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
            }
            is DbState.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Ошибка БД",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = s.message,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
