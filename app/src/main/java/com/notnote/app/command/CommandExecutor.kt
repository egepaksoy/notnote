package com.notnote.app.command

import android.Manifest
import android.app.PendingIntent
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.telephony.SmsManager
import com.notnote.app.automation.Scheduler
import com.notnote.app.automation.SmsSentReceiver
import com.notnote.app.automation.WhatsAppSender
import com.notnote.app.data.Note
import com.notnote.app.data.NoteRepository
import com.notnote.app.data.NoteStatus
import java.util.Calendar

sealed interface ExecResult {
    data class Done(val detail: String) : ExecResult
    data class Failed(val detail: String) : ExecResult
    data class Running(val detail: String) : ExecResult
    data class Scheduled(val at: Long) : ExecResult
    data class NeedsChoice(val options: List<Contact>) : ExecResult
}

/** Ayrıştırılmış bir komutu çalıştırır ve sonucunu nota yazar. */
class CommandExecutor(private val ctx: Context, private val repo: NoteRepository) {

    fun execute(noteId: Long, cmd: ParsedCommand, chosen: Contact? = null): ExecResult {
        val result = try {
            run(noteId, cmd, chosen)
        } catch (e: Exception) {
            ExecResult.Failed(e.message ?: "beklenmeyen hata")
        }
        record(noteId, result)
        return result
    }

    /** Planlanmış ya da bildirimden tetiklenen bir notu, kayıtlı kişiyle hemen çalıştırır. */
    fun runStored(note: Note, fromBackground: Boolean): ExecResult {
        val cmd = (CommandParser.parse(note.text) as? ParseResult.Ok)?.command
            ?: return ExecResult.Failed("komut okunamadı").also { record(note.id, it) }
        val number = note.targetNumber
        val result = try {
            when {
                cmd.type == CommandType.WHATSAPP && number != null ->
                    sendWhatsApp(note.id, number, cmd.body, fromBackground)
                cmd.type == CommandType.SMS && number != null -> sendSms(note.id, number, cmd.body)
                else -> run(note.id, cmd.copy(time = null), null)
            }
        } catch (e: Exception) {
            ExecResult.Failed(e.message ?: "beklenmeyen hata")
        }
        record(note.id, result)
        return result
    }

    private fun record(noteId: Long, r: ExecResult) {
        when (r) {
            is ExecResult.Done -> repo.setStatus(noteId, NoteStatus.DONE, r.detail)
            is ExecResult.Failed -> repo.setStatus(noteId, NoteStatus.FAILED, r.detail)
            is ExecResult.Running -> repo.setStatus(noteId, NoteStatus.RUNNING, r.detail)
            is ExecResult.Scheduled -> repo.get(noteId)?.let {
                repo.update(it.copy(status = NoteStatus.SCHEDULED, scheduledAt = r.at, detail = null))
            }
            is ExecResult.NeedsChoice -> Unit
        }
    }

    private fun run(noteId: Long, cmd: ParsedCommand, chosen: Contact?): ExecResult {
        if (cmd.type.target == TargetKind.CONTACT) {
            val contact = chosen ?: when (val r = resolveContact(cmd.target.orEmpty())) {
                is ContactResult.One -> r.contact
                is ContactResult.Many -> return ExecResult.NeedsChoice(r.options)
                is ContactResult.Fail -> return ExecResult.Failed(r.message)
            }
            repo.get(noteId)?.let { repo.update(it.copy(targetName = contact.name, targetNumber = contact.number)) }

            if (cmd.time != null) {
                val at = cmd.time.resolve()
                Scheduler.schedule(ctx, noteId, at)
                return ExecResult.Scheduled(at)
            }
            return when (cmd.type) {
                CommandType.WHATSAPP -> sendWhatsApp(noteId, contact.number, cmd.body, fromBackground = false)
                CommandType.SMS -> sendSms(noteId, contact.number, cmd.body)
                CommandType.CALL -> call(contact.number)
                else -> ExecResult.Failed("desteklenmiyor")
            }
        }

        return when (cmd.type) {
            CommandType.ALARM -> setAlarm(cmd.time!!, cmd.body)
            CommandType.REMIND -> {
                val at = cmd.time!!.resolve()
                Scheduler.schedule(ctx, noteId, at)
                ExecResult.Scheduled(at)
            }
            CommandType.SPOTIFY -> spotify(cmd.body)
            CommandType.YOUTUBE -> youtube(cmd.body)
            CommandType.MAPS -> maps(cmd.body)
            CommandType.OPEN -> openApp(cmd.target.orEmpty())
            CommandType.TELEGRAM -> telegram(cmd.body)
            else -> ExecResult.Failed("desteklenmiyor")
        }
    }

    // --- Kişi bulma ---------------------------------------------------------

    private sealed interface ContactResult {
        data class One(val contact: Contact) : ContactResult
        data class Many(val options: List<Contact>) : ContactResult
        data class Fail(val message: String) : ContactResult
    }

    private fun resolveContact(query: String): ContactResult {
        if (Phone.looksLikeNumber(query)) return ContactResult.One(Contact(query.trim(), query.trim()))
        if (!Contacts.hasPermission(ctx)) return ContactResult.Fail("Rehber izni yok — Menü → İzinler")
        val found = Contacts.search(Contacts.load(ctx), query)
        return when (found.size) {
            0 -> ContactResult.Fail("Rehberde \"$query\" bulunamadı")
            1 -> ContactResult.One(found.first())
            else -> ContactResult.Many(found.take(12))
        }
    }

    // --- Komutlar -----------------------------------------------------------

    private fun sendWhatsApp(noteId: Long, number: String, text: String, fromBackground: Boolean): ExecResult {
        val pkg = Apps.firstInstalled(ctx, WhatsAppSender.WHATSAPP, WhatsAppSender.WHATSAPP_BUSINESS)
            ?: return ExecResult.Failed("WhatsApp yüklü değil")
        val uri = Uri.parse("https://api.whatsapp.com/send?phone=${Phone.toInternational(number)}&text=${Uri.encode(text)}")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(pkg)
        val auto = WhatsAppSender.isServiceEnabled(ctx)
        if (auto) WhatsAppSender.arm(noteId, pkg, text, returnToApp = !fromBackground)
        if (!launch(intent)) {
            WhatsAppSender.disarm()
            return ExecResult.Failed("WhatsApp açılamadı")
        }
        return if (auto) ExecResult.Running("gönderiliyor…")
        else ExecResult.Done("WhatsApp'ta açıldı — Gönder'e sen bas (otomatik gönderim kapalı)")
    }

    private fun sendSms(noteId: Long, number: String, text: String): ExecResult {
        if (ctx.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).putExtra("sms_body", text)
            return if (launch(intent)) ExecResult.Done("Mesajlar'da açıldı (SMS izni yok)")
            else ExecResult.Failed("SMS izni yok — Menü → İzinler")
        }
        val sms = ctx.getSystemService(SmsManager::class.java)
        val parts = sms.divideMessage(text)
        val sent = PendingIntent.getBroadcast(
            ctx, noteId.toInt(),
            Intent(ctx, SmsSentReceiver::class.java).putExtra(SmsSentReceiver.EXTRA_NOTE_ID, noteId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        sms.sendMultipartTextMessage(number, null, parts, ArrayList(parts.map { sent }), null)
        return ExecResult.Running("gönderiliyor…")
    }

    private fun call(number: String): ExecResult {
        val granted = ctx.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
        return if (launch(intent)) ExecResult.Done(if (granted) "aranıyor" else "numara açıldı (arama izni yok)")
        else ExecResult.Failed("arama başlatılamadı")
    }

    private fun setAlarm(time: TimeSpec, label: String): ExecResult {
        val cal = Calendar.getInstance().apply { timeInMillis = time.resolve() }
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, h)
            .putExtra(AlarmClock.EXTRA_MINUTES, m)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        if (label.isNotBlank()) intent.putExtra(AlarmClock.EXTRA_MESSAGE, label)
        return if (launch(intent)) ExecResult.Done("alarm kuruldu: %02d:%02d".format(h, m))
        else ExecResult.Failed("saat uygulaması alarm kabul etmedi")
    }

    private fun spotify(query: String): ExecResult {
        val pkg = SPOTIFY
        if (!Apps.isInstalled(ctx, pkg)) return ExecResult.Failed("Spotify yüklü değil")
        if (query.isBlank()) {
            return if (launchPackage(pkg)) ExecResult.Done("Spotify açıldı") else ExecResult.Failed("Spotify açılamadı")
        }
        val play = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH)
            .setPackage(pkg)
            .putExtra(SearchManager.QUERY, query)
        if (launch(play)) return ExecResult.Done("Spotify'da çalınıyor")
        val search = Intent(Intent.ACTION_VIEW, Uri.parse("spotify:search:${Uri.encode(query)}")).setPackage(pkg)
        return if (launch(search)) ExecResult.Done("Spotify'da arandı") else ExecResult.Failed("Spotify açılamadı")
    }

    private fun youtube(query: String): ExecResult {
        val pkg = YOUTUBE
        if (query.isBlank()) {
            return if (launchPackage(pkg)) ExecResult.Done("YouTube açıldı") else ExecResult.Failed("YouTube açılamadı")
        }
        val app = Intent(Intent.ACTION_SEARCH).setPackage(pkg).putExtra(SearchManager.QUERY, query)
        if (Apps.isInstalled(ctx, pkg) && launch(app)) return ExecResult.Done("YouTube'da arandı")
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}"))
        return if (launch(web)) ExecResult.Done("YouTube'da arandı") else ExecResult.Failed("YouTube açılamadı")
    }

    private fun maps(place: String): ExecResult {
        val nav = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${Uri.encode(place)}")).setPackage(MAPS)
        if (Apps.isInstalled(ctx, MAPS) && launch(nav)) return ExecResult.Done("yol tarifi başladı")
        val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(place)}"))
        return if (launch(geo)) ExecResult.Done("haritada açıldı") else ExecResult.Failed("harita uygulaması yok")
    }

    private fun openApp(name: String): ExecResult {
        val app = Apps.search(Apps.load(ctx), name).firstOrNull()
            ?: return ExecResult.Failed("\"$name\" adında uygulama yok")
        return if (launchPackage(app.packageName)) ExecResult.Done("${app.label} açıldı")
        else ExecResult.Failed("${app.label} açılamadı")
    }

    private fun telegram(text: String): ExecResult {
        val pkg = Apps.firstInstalled(ctx, "org.telegram.messenger", "org.telegram.messenger.web", "org.thunderdog.challegram")
            ?: return ExecResult.Failed("Telegram yüklü değil")
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(pkg).putExtra(Intent.EXTRA_TEXT, text)
        return if (launch(intent)) ExecResult.Done("Telegram'da sohbet seç") else ExecResult.Failed("Telegram açılamadı")
    }

    // --- Yardımcılar --------------------------------------------------------

    private fun launch(intent: Intent): Boolean = try {
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun launchPackage(pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return launch(intent)
    }

    companion object {
        const val SPOTIFY = "com.spotify.music"
        const val YOUTUBE = "com.google.android.youtube"
        const val MAPS = "com.google.android.apps.maps"
    }
}
