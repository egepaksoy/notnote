package com.notnote.app.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class CommandParserTest {

    private fun ok(s: String) = (CommandParser.parse(s) as ParseResult.Ok).command

    @Test
    fun plainTextIsNotACommand() {
        assertEquals(ParseResult.NotCommand, CommandParser.parse("eşofman altı"))
        assertEquals(ParseResult.NotCommand, CommandParser.parse("mail: ali@site.com"))
    }

    @Test
    fun whatsapp() {
        val c = ok("@whatsapp Ahmet Yılmaz: yarın 5'te geliyorum")
        assertEquals(CommandType.WHATSAPP, c.type)
        assertEquals("Ahmet Yılmaz", c.target)
        assertEquals("yarın 5'te geliyorum", c.body)
        assertNull(c.time)
    }

    @Test
    fun whatsappAliasAndColonInsideMessage() {
        val c = ok("@wa Ali: saat 21:30'da buluşalım")
        assertEquals(CommandType.WHATSAPP, c.type)
        assertEquals("Ali", c.target)
        assertEquals("saat 21:30'da buluşalım", c.body)
    }

    @Test
    fun scheduledWhatsapp() {
        val c = ok("@whatsapp 21:00 Ahmet: iyi geceler")
        assertEquals(TimeSpec.At(21, 0), c.time)
        assertEquals("Ahmet", c.target)
        assertEquals("iyi geceler", c.body)
    }

    @Test
    fun whatsappErrors() {
        assertTrue(CommandParser.parse("@whatsapp Ahmet selam") is ParseResult.Error)
        assertTrue(CommandParser.parse("@whatsapp Ahmet:") is ParseResult.Error)
        assertTrue(CommandParser.parse("@whatsapp : selam") is ParseResult.Error)
    }

    @Test
    fun unknownCommand() {
        assertTrue(CommandParser.parse("@whatsap Ahmet: x") is ParseResult.Error)
    }

    @Test
    fun turkishKeysWithAndWithoutDiacritics() {
        assertEquals(CommandType.REMIND, ok("@hatırlat 18:00 ilacı iç").type)
        assertEquals(CommandType.REMIND, ok("@HATIRLAT 18.00 ilacı iç").type)
        assertEquals(CommandType.REMIND, ok("@hatirlat 30dk çay").type)
        assertEquals(CommandType.OPEN, ok("@aç instagram").type)
        assertEquals(CommandType.OPEN, ok("@ac instagram").type)
    }

    @Test
    fun times() {
        assertEquals(TimeSpec.At(7, 30), CommandParser.parseTime("07:30"))
        assertEquals(TimeSpec.At(7, 30), CommandParser.parseTime("7.30"))
        assertEquals(TimeSpec.In(20), CommandParser.parseTime("20dk"))
        assertEquals(TimeSpec.In(120), CommandParser.parseTime("2sa"))
        assertEquals(TimeSpec.In(60), CommandParser.parseTime("1saat"))
        assertNull(CommandParser.parseTime("25:00"))
        assertNull(CommandParser.parseTime("Ahmet"))
    }

    @Test
    fun alarmAndReminder() {
        val a = ok("@alarm 07:30 kalk")
        assertEquals(TimeSpec.At(7, 30), a.time)
        assertEquals("kalk", a.body)
        val r = ok("@hatırlat 30dk çayı kapat")
        assertEquals(TimeSpec.In(30), r.time)
        assertEquals("çayı kapat", r.body)
        assertTrue(CommandParser.parse("@alarm kalk") is ParseResult.Error)
    }

    @Test
    fun otherCommands() {
        assertEquals("Refika", ok("@ara Refika").target)
        assertEquals("lofi", ok("@spotify lofi").body)
        assertEquals("", ok("@spotify").body)
        assertEquals("Kadıköy", ok("@harita Kadıköy").body)
        assertEquals("instagram", ok("@aç instagram").target)
        assertTrue(CommandParser.parse("@harita") is ParseResult.Error)
    }

    @Test
    fun resolveRollsOverToTomorrow() {
        val now = Calendar.getInstance().apply {
            set(2026, Calendar.OCTOBER, 30, 22, 0, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val at = TimeSpec.At(7, 30).resolve(now)
        val cal = Calendar.getInstance().apply { timeInMillis = at }
        assertEquals(31, cal.get(Calendar.DAY_OF_MONTH))
        assertEquals(7, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(now + 20 * 60_000L, TimeSpec.In(20).resolve(now))
    }

    @Test
    fun phoneNumbers() {
        assertEquals("905321234567", Phone.toInternational("0532 123 45 67"))
        assertEquals("905321234567", Phone.toInternational("+90 532 123 45 67"))
        assertEquals("905321234567", Phone.toInternational("5321234567"))
        assertEquals("491701234567", Phone.toInternational("0049 170 1234567"))
        assertTrue(Phone.looksLikeNumber("0532 123 45 67"))
        assertTrue(!Phone.looksLikeNumber("Ahmet"))
    }

    @Test
    fun matching() {
        assertEquals(0, matchRank("Refika", "refika"))
        assertEquals(1, matchRank("Ahmet Yılmaz", "yilm"))
        assertEquals(2, matchRank("Mehmet", "hme"))
        assertNull(matchRank("Ali", "veli"))
    }
}
