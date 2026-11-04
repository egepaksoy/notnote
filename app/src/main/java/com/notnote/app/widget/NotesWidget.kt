package com.notnote.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.notnote.app.MainActivity
import com.notnote.app.NotnoteApp
import com.notnote.app.R
import com.notnote.app.data.Note

/**
 * Ana ekranda not listesi (Tiny Notes tarzı). Sadece tamamlanmamış normal notları
 * gösterir; sabitlenenler üstte. Notlar değişince [updateAll] ile kendini yeniler.
 */
class NotesWidget : AppWidgetProvider() {

    override fun onUpdate(ctx: Context, manager: AppWidgetManager, ids: IntArray) {
        val notes = (ctx.applicationContext as NotnoteApp).repository.notes.value
        ids.forEach { manager.updateAppWidget(it, build(ctx, notes)) }
    }

    companion object {
        private const val MAX_ITEMS = 60

        fun updateAll(ctx: Context, notes: List<Note>) {
            val manager = AppWidgetManager.getInstance(ctx)
            val ids = manager.getAppWidgetIds(ComponentName(ctx, NotesWidget::class.java))
            if (ids.isEmpty()) return
            val views = build(ctx, notes)
            ids.forEach { manager.updateAppWidget(it, views) }
        }

        private fun build(ctx: Context, notes: List<Note>): RemoteViews {
            val shown = notes.filter { !it.isCommand }.take(MAX_ITEMS)

            val items = RemoteViews.RemoteCollectionItems.Builder()
                .setHasStableIds(true)
                .setViewTypeCount(1)
            shown.forEach { n ->
                val item = RemoteViews(ctx.packageName, R.layout.widget_note_item).apply {
                    setTextViewText(R.id.item_text, (if (n.pinned) "• " else "") + n.text.replace('\n', ' '))
                    setOnClickFillInIntent(R.id.item_text, Intent().putExtra(MainActivity.EXTRA_OPEN_NOTE, n.id))
                }
                items.addItem(n.id, item)
            }

            return RemoteViews(ctx.packageName, R.layout.widget_notes).apply {
                setRemoteAdapter(R.id.widget_list, items.build())
                setEmptyView(R.id.widget_list, R.id.widget_empty)
                setPendingIntentTemplate(R.id.widget_list, activity(ctx, 10, null, mutable = true))
                setOnClickPendingIntent(R.id.widget_title, activity(ctx, 11, null))
                setOnClickPendingIntent(
                    R.id.widget_add,
                    activity(ctx, 12, Intent().putExtra(MainActivity.EXTRA_FOCUS_INPUT, true)),
                )
            }
        }

        private fun activity(ctx: Context, code: Int, extras: Intent?, mutable: Boolean = false): PendingIntent {
            val intent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            extras?.extras?.let { intent.putExtras(it) }
            val flag = if (mutable) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            return PendingIntent.getActivity(ctx, code, intent, flag or PendingIntent.FLAG_UPDATE_CURRENT)
        }
    }
}
