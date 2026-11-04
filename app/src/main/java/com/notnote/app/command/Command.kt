package com.notnote.app.command

import java.util.Calendar

enum class TargetKind { NONE, CONTACT, APP }

enum class CommandType(
    val keys: List<String>,
    val label: String,
    val target: TargetKind,
    val example: String,
    val description: String,
) {
    WHATSAPP(
        listOf("whatsapp", "wa"), "WhatsApp", TargetKind.CONTACT,
        "@whatsapp Ahmet: yarın 5'te geliyorum",
        "WhatsApp mesajı gönderir. Başına saat yazarsan o saatte gönderir: @whatsapp 21:00 Ahmet: iyi geceler",
    ),
    SMS(
        listOf("sms"), "SMS", TargetKind.CONTACT,
        "@sms Annem: eve geçtim",
        "SMS gönderir. Saat de yazılabilir: @sms 08:00 Annem: günaydın",
    ),
    CALL(
        listOf("ara"), "Ara", TargetKind.CONTACT,
        "@ara Refika",
        "Kişiyi arar.",
    ),
    ALARM(
        listOf("alarm"), "Alarm", TargetKind.NONE,
        "@alarm 07:30 kalk",
        "Saat uygulamasında alarm kurar. Süre de olur: @alarm 20dk",
    ),
    REMIND(
        listOf("hatırlat", "hatirlat"), "Hatırlat", TargetKind.NONE,
        "@hatırlat 18:00 ilacı iç",
        "O saatte bildirim gösterir. Süre de olur: @hatırlat 30dk çayı kapat",
    ),
    SPOTIFY(
        listOf("spotify"), "Spotify", TargetKind.NONE,
        "@spotify lofi",
        "Spotify'da arar ve çalar. Boş bırakırsan sadece açar.",
    ),
    YOUTUBE(
        listOf("youtube", "yt"), "YouTube", TargetKind.NONE,
        "@youtube kedi videoları",
        "YouTube'da arama yapar.",
    ),
    MAPS(
        listOf("harita"), "Harita", TargetKind.NONE,
        "@harita Kadıköy",
        "Google Haritalar'da yol tarifi başlatır.",
    ),
    OPEN(
        listOf("aç", "ac"), "Aç", TargetKind.APP,
        "@aç instagram",
        "Telefondaki bir uygulamayı açar.",
    ),
    TELEGRAM(
        listOf("telegram", "tg"), "Telegram", TargetKind.NONE,
        "@telegram toplantı 3'te",
        "Mesajı Telegram'a aktarır, sohbeti sen seçersin.",
    );

    val primaryKey get() = keys.first()

    /** Başında saat yazılabilen komutlar (planlanmış gönderim / alarm / hatırlatıcı). */
    val acceptsTime get() = this == WHATSAPP || this == SMS
    val requiresTime get() = this == ALARM || this == REMIND

    companion object {
        fun find(key: String): CommandType? {
            val k = normalize(key)
            return entries.firstOrNull { t -> t.keys.any { normalize(it) == k } }
        }
    }
}

sealed interface TimeSpec {
    data class At(val hour: Int, val minute: Int) : TimeSpec
    data class In(val minutes: Int) : TimeSpec

    /** Bir sonraki gerçekleşme zamanı (milisaniye). "07:30" geçtiyse yarın 07:30. */
    fun resolve(now: Long = System.currentTimeMillis()): Long = when (this) {
        is In -> now + minutes * 60_000L
        is At -> Calendar.getInstance().run {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now) add(Calendar.DAY_OF_YEAR, 1)
            timeInMillis
        }
    }
}

data class ParsedCommand(
    val type: CommandType,
    val time: TimeSpec? = null,
    /** Kişi adı / numara (CONTACT) veya uygulama adı (APP). */
    val target: String? = null,
    val body: String = "",
)

sealed interface ParseResult {
    data object NotCommand : ParseResult
    data class Ok(val command: ParsedCommand) : ParseResult
    data class Error(val message: String) : ParseResult
}
