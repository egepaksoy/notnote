package com.notnote.app.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** Hatırlatıcıları ve planlanmış mesajları zamanında tetikler. */
object Scheduler {

    fun schedule(ctx: Context, noteId: Long, at: Long) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(ctx, noteId)
        if (am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancel(ctx: Context, noteId: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pendingIntent(ctx, noteId))
    }

    private fun pendingIntent(ctx: Context, noteId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            ctx,
            noteId.toInt(),
            Intent(ctx, AlarmReceiver::class.java).putExtra(AlarmReceiver.EXTRA_NOTE_ID, noteId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
