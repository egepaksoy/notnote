package com.notnote.app.command

import java.util.Locale

private val TR = Locale.forLanguageTag("tr")

/** Türkçe karakterleri sadeleştirip küçük harfe çevirir: "Hatırlat" → "hatirlat", "AÇ" → "ac". */
fun normalize(s: String): String =
    s.trim().lowercase(TR)
        .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u')
        .replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
        .replace(Regex("\\s+"), " ")

/** Yazılan sorgunun bir isimle ne kadar iyi eşleştiği: 0 = tam, 1 = kelime başı, 2 = içinde, null = yok. */
fun matchRank(name: String, query: String): Int? {
    val n = normalize(name)
    val q = normalize(query)
    if (q.isEmpty()) return 2
    return when {
        n == q -> 0
        n.startsWith(q) || n.split(' ').any { it.startsWith(q) } -> 1
        n.contains(q) -> 2
        else -> null
    }
}

object Phone {
    /** Varsayılan ülke kodu (Türkiye). */
    const val DEFAULT_COUNTRY_CODE = "90"

    /** Metin bir telefon numarası gibi mi görünüyor? ("0532 123 45 67", "+90532...") */
    fun looksLikeNumber(s: String): Boolean {
        val t = s.trim()
        return t.isNotEmpty() && t.all { it.isDigit() || it in "+ ()-" } && t.count { it.isDigit() } >= 7
    }

    /** WhatsApp'ın istediği uluslararası biçim: sadece rakam, ülke koduyla (ör. 905321234567). */
    fun toInternational(raw: String, cc: String = DEFAULT_COUNTRY_CODE): String {
        val digits = raw.filter { it.isDigit() }
        return when {
            raw.trim().startsWith("+") -> digits
            digits.startsWith("00") -> digits.drop(2)
            digits.startsWith("0") -> cc + digits.drop(1)
            digits.length == 10 -> cc + digits
            else -> digits
        }
    }
}
