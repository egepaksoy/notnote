package com.notnote.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.notnote.app.MainActivity
import com.notnote.app.R

/** Ana ekran widget'ı: dokununca notnote açılır ve klavye yazmaya hazır gelir. */
class QuickNoteWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val intent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_FOCUS_INPUT, true)
        val tap = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(ctx.packageName, R.layout.widget_quick_note).apply {
            setOnClickPendingIntent(R.id.widget_root, tap)
        }
        ids.forEach { manager.updateAppWidget(it, views) }
    }
}
