package com.notnote.app.automation

import android.app.Activity
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.notnote.app.NotnoteApp
import com.notnote.app.command.CommandParser
import com.notnote.app.command.CommandType
import com.notnote.app.command.ParseResult
import com.notnote.app.data.NoteStatus

/** Zamanı gelen hatırlatıcı ya da planlanmış mesaj. */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext as NotnoteApp
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        val note = app.repository.get(noteId) ?: return
        if (note.status != NoteStatus.SCHEDULED) return
        if (!note.isCommand) {
            // Sıradan nota kurulan hatırlatıcı: notun kendisini hatırlat, sonra notu eski haline döndür.
            Notifications.reminder(ctx, noteId, note.text)
            app.repository.update(note.copy(status = NoteStatus.NONE, scheduledAt = null, detail = null))
            return
        }
        val cmd = (CommandParser.parse(note.text) as? ParseResult.Ok)?.command ?: return

        when (cmd.type) {
            CommandType.REMIND -> {
                Notifications.reminder(ctx, noteId, cmd.body.ifBlank { "Hatırlatma" })
                app.repository.setStatus(noteId, NoteStatus.DONE, "hatırlatıldı")
            }
            CommandType.SMS -> app.executor.runStored(note, fromBackground = true)
            CommandType.WHATSAPP -> {
                val locked = ctx.getSystemService(KeyguardManager::class.java).isKeyguardLocked
                if (locked || !WhatsAppSender.isServiceEnabled(ctx)) {
                    // Kilitli ekranda WhatsApp'a dokunulamaz: kullanıcıya bildirimle sor.
                    app.repository.setStatus(noteId, NoteStatus.RUNNING, "bildirime dokun, gönderilsin")
                    Notifications.sendNeedsTap(
                        ctx, noteId,
                        "WhatsApp: ${note.targetName ?: cmd.target}",
                        "Göndermek için dokun: ${cmd.body}",
                    )
                } else {
                    app.executor.runStored(note, fromBackground = true)
                }
            }
            else -> Unit
        }
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}

/** SMS gönderim sonucu. */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val repo = (ctx.applicationContext as NotnoteApp).repository
        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, -1L)
        if (resultCode == Activity.RESULT_OK) repo.setStatus(noteId, NoteStatus.DONE, "gönderildi")
        else repo.setStatus(noteId, NoteStatus.FAILED, "SMS gönderilemedi (hata $resultCode)")
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}

/** Telefon yeniden başlayınca (ya da uygulama güncellenince) planlanmış işleri geri kurar. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val repo = (ctx.applicationContext as NotnoteApp).repository
        val now = System.currentTimeMillis()
        repo.notes.value.filter { it.status == NoteStatus.SCHEDULED }.forEach { n ->
            Scheduler.schedule(ctx, n.id, maxOf(n.scheduledAt ?: now, now + 5_000L))
        }
    }
}
