package ru.karaoketv.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsParserTest {
    @Test fun parsesAndSortsTimestampedLines() {
        val lines = LyricsParser.parse("[00:02.50]Вторая\n[00:01.000]Первая\nбез таймкода")
        assertEquals(listOf(LyricLine(1_000, "Первая"), LyricLine(2_500, "Вторая")), lines)
    }

    @Test fun extractsOnlyTimestampedDescriptionLines() {
        val lrc = LyricsParser.extractLrc("Описание\n[00:12.3]Строка\n[01:02]Ещё")
        assertTrue(lrc?.contains("[00:12.3]Строка") == true)
        assertTrue(lrc?.contains("Описание") == false)
    }
}
