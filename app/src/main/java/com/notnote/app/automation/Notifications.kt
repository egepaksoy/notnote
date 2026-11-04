package com.notnote.app.automation

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.notnote.app.MainActivity
import com.notnote.app.R

object Notifications {
    private const val CHANNEL_REMINDERS = "reminders"
    private const val CHANNEL_SENDS = "sends"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Hatırlatıcılar", NotificationManager.IMPORTANCE_HIGH)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_SENDS, "Planlanmış mesajlar", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun reminder(ctx: Context, noteId: Long, text: String) =
        post(ctx, noteId, CHANNEL_REMINDERS, "Hatırlatma", text, openApp(ctx, noteId, run = false))

    /** Planlanmış mesaj arka planda gönderilemediğinde: dokununca gönderilir. */
    fun sendNeedsTap(ctx: Context, noteId: Long, title: String, text: String) =
        post(ctx, noteId, CHANNEL_SENDS, title, text, openApp(ctx, noteId, run = true))

    private fun post(ctx: Context, noteId: Long, channel: String, title: String, text: String, tap: PendingIntent) {
        if (ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val n = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(tap)
            .setAutoCancel(true)
            .build()
        ctx.getSystemService(NotificationManager::class.java).notify(noteId.toInt(), n)
    }

    private fun openApp(ctx: Context, noteId: Long, run: Boolean): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (run) intent.putExtra(MainActivity.EXTRA_RUN_NOTE, noteId)
        return PendingIntent.getActivity(
            ctx, noteId.toInt() * 2 + if (run) 1 else 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
