package com.daniel.ege100

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.daniel.ege100.data.AppSettings
import com.daniel.ege100.data.AppSettingsStore
import com.daniel.ege100.data.BreadcrumbLog
import com.daniel.ege100.data.CrashLog
import com.daniel.ege100.data.SafeMode
import com.daniel.ege100.ui.common.CrashRecoveryDialog
import com.daniel.ege100.ui.common.SafeModeScreen
import com.daniel.ege100.ui.nav.EgeApp
import com.daniel.ege100.ui.theme.EgeTheme

class MainActivity : ComponentActivity() {

    /**
     * Phase 3 Stage FINAL part Б — запрос POST_NOTIFICATIONS на Android 13+.
     * Запускается один раз при старте; пользователь может отказать —
     * в этом случае `NotificationHelper.show` тихо игнорирует попытки.
     */
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // OK or denied — оба варианта валидны, ничего не делаем.
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        BreadcrumbLog.add("MainActivity.onCreate")

        // Phase 4 Stage P4-D3 (Convention #69) — crash-loop protection.
        // 3+ краша за 30 секунд → SafeMode UI без основного NavHost.
        // Делаем ДО любых обращений к DataStore/Room/Compose-NavHost, чтобы
        // даже если они и есть источник краша — пользователь увидел кнопку
        // «Сбросить», а не пустой экран.
        if (SafeMode.isActive(this)) {
            BreadcrumbLog.add("SafeMode active — rendering recovery UI")
            setContent {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF000000),
                ) {
                    SafeModeScreen(onReset = {
                        SafeMode.reset(this@MainActivity)
                        // Перезапуск Activity — простейший способ откатиться
                        // к нормальной инициализации.
                        val restart = Intent(this@MainActivity, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        }
                        startActivity(restart)
                        finish()
                    })
                }
            }
            return
        }

        requestNotificationPermissionIfNeeded()

        // Phase 4 Stage P4-D2 part Г (Convention #68) — проверяем флаг
        // незавершённого краша от прошлой сессии.
        val showCrashDialogInitial = CrashLog.hasUnhandledCrash(this)
        if (showCrashDialogInitial) {
            // Сразу сбрасываем флаг — диалог покажем один раз. Файл остаётся
            // в filesDir/crashes/, доступен через Настройки → «Отправить crash log».
            CrashLog.clearUnhandledFlag(this)
        }

        setContent {
            // Phase 3 Stage A part Г: подписка на AppSettings.themeMode.
            // При смене темы в Настройках — этот flow эмитит новое значение,
            // MainActivity рекомпозит EgeTheme → весь UI перекрашивается.
            val context = LocalContext.current
            val settingsFlow = remember(context) { AppSettingsStore.settingsFlow(context) }
            val settings by settingsFlow.collectAsState(initial = AppSettings())

            var showCrashDialog by remember { mutableStateOf(showCrashDialogInitial) }

            EgeTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EgeApp()
                    if (showCrashDialog) {
                        CrashRecoveryDialog(
                            onSend = {
                                CrashLog.shareLatest(this@MainActivity)
                                showCrashDialog = false
                            },
                            onDismiss = { showCrashDialog = false },
                        )
                    }
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
