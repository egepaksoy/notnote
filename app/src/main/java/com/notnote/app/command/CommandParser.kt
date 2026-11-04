package com.notnote.app.command

object CommandParser {

    private val absoluteTime = Regex("""^(\d{1,2})[:.](\d{2})$""")
    private val relativeTime = Regex("""^(\d{1,4})(dk|dak|dakika|sa|saat)$""")

    fun parseTime(token: String): TimeSpec? {
        val t = normalize(token)
        absoluteTime.matchEntire(t)?.let { m ->
            val h = m.groupValues[1].toInt()
            val min = m.groupValues[2].toInt()
            return if (h in 0..23 && min in 0..59) TimeSpec.At(h, min) else null
        }
        relativeTime.matchEntire(t)?.let { m ->
            val n = m.groupValues[1].toInt()
            if (n <= 0) return null
            return TimeSpec.In(if (m.groupValues[2].startsWith("s")) n * 60 else n)
        }
        return null
    }

    /** "@komut geri kalanı" → (komut kelimesi, geri kalanı). */
    fun splitHead(input: String): Pair<String, String>? {
        val t = input.trim()
        if (!t.startsWith("@") || t.length < 2) return null
        val end = t.indexOfFirst { it.isWhitespace() }.let { if (it < 0) t.length else it }
        return t.substring(1, end) to t.substring(end).trim()
    }

    /** Metnin başındaki saat kelimesini ayırır: "21:00 Ahmet: selam" → (21:00, "Ahmet: selam"). */
    fun splitTime(rest: String): Pair<TimeSpec?, String> {
        val first = rest.substringBefore(' ')
        val time = parseTime(first) ?: return null to rest
        return time to rest.substringAfter(' ', "").trim()
    }

    fun parse(input: String): ParseResult {
        val (key, rawRest) = splitHead(input) ?: return ParseResult.NotCommand
        val type = CommandType.find(key)
            ?: return ParseResult.Error("Bilinmeyen komut: @$key — Menü → Komutlar'a bak")

        var rest = rawRest
        var time: TimeSpec? = null
        if (type.acceptsTime || type.requiresTime) {
            val (t, r) = splitTime(rest)
            time = t
            rest = r
        }
        if (type.requiresTime && time == null) {
            return ParseResult.Error("Saat yaz: ${type.example}")
        }

        return when (type) {
            CommandType.WHATSAPP, CommandType.SMS -> {
                val colon = rest.indexOf(':')
                if (colon < 0) return ParseResult.Error("Kişi ve mesajı iki nokta ile ayır: ${type.example}")
                val target = rest.substring(0, colon).trim()
                val body = rest.substring(colon + 1).trim()
                when {
                    target.isEmpty() -> ParseResult.Error("Kime? ${type.example}")
                    body.isEmpty() -> ParseResult.Error("Mesaj boş: ${type.example}")
                    else -> ParseResult.Ok(ParsedCommand(type, time, target, body))
                }
            }
            CommandType.CALL ->
                if (rest.isEmpty()) ParseResult.Error("Kimi arayayım? ${type.example}")
                else ParseResult.Ok(ParsedCommand(type, target = rest))
            CommandType.OPEN ->
                if (rest.isEmpty()) ParseResult.Error("Hangi uygulama? ${type.example}")
                else ParseResult.Ok(ParsedCommand(type, target = rest))
            CommandType.MAPS, CommandType.TELEGRAM ->
                if (rest.isEmpty()) ParseResult.Error("Boş bırakılamaz: ${type.example}")
                else ParseResult.Ok(ParsedCommand(type, body = rest))
            CommandType.ALARM, CommandType.REMIND, CommandType.SPOTIFY, CommandType.YOUTUBE ->
                ParseResult.Ok(ParsedCommand(type, time, body = rest))
        }
    }
}
