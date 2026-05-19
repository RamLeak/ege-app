package com.daniel.ege100

import android.Manifest
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.daniel.ege100.data.AppSettings
import com.daniel.ege100.data.AppSettingsStore
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
        requestNotificationPermissionIfNeeded()
        setContent {
            // Phase 3 Stage A part Г: подписка на AppSettings.themeMode.
            // При смене темы в Настройках — этот flow эмитит новое значение,
            // MainActivity рекомпозит EgeTheme → весь UI перекрашивается.
            val context = LocalContext.current
            val settingsFlow = remember(context) { AppSettingsStore.settingsFlow(context) }
            val settings by settingsFlow.collectAsState(initial = AppSettings())

            EgeTheme(themeMode = settings.themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    EgeApp()
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
