package com.daniel.ege100.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.daniel.ege100.MainActivity
import com.daniel.ege100.R

/**
 * Phase 3 Stage FINAL part Б — общий helper для push-уведомлений.
 *
 * Канал `ege100_main` создаётся идемпотентно при каждом вызове (no-op если
 * уже создан). NotificationManagerCompat.notify тихо игнорирует если у
 * приложения нет permission POST_NOTIFICATIONS (Android 13+) — это
 * ожидаемое поведение: показываем то, что можем.
 */
object NotificationHelper {
    const val CHANNEL_ID = "ege100_main"
    private const val CHANNEL_NAME = "EGE100 Напоминания"

    const val NOTIF_STREAK = 1001
    const val NOTIF_DAILY = 1002
    const val NOTIF_MOCK = 1003

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = context.getSystemService(NotificationManager::class.java) ?: return
            val existing = mgr.getNotificationChannel(CHANNEL_ID)
            if (existing != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Streak, пробники и напоминания о подготовке"
            }
            mgr.createNotificationChannel(channel)
        }
    }

    fun show(context: Context, title: String, text: String, notifId: Int) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }
}
