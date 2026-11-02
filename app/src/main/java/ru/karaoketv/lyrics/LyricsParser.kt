package ru.karaoketv.lyrics

data class LyricLine(val startMs: Long, val text: String)

object LyricsParser {
    private val timestamp = Regex("^\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?\\]\\s*(.*)$")

    fun parse(lrc: String): List<LyricLine> = lrc.lineSequence()
        .flatMap { line ->
            val match = timestamp.matchEntire(line.trim()) ?: return@flatMap emptySequence()
            val minutes = match.groupValues[1].toLong()
            val seconds = match.groupValues[2].toLong()
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            sequenceOf(LyricLine((minutes * 60 + seconds) * 1000 + fraction, match.groupValues[4].trim()))
        }
        .filter { it.text.isNotBlank() }
        .sortedBy { it.startMs }
        .toList()

    fun extractLrc(text: String): String? {
        val lines = text.lineSequence().filter { timestamp.matches(it.trim()) }.toList()
        return lines.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }
}
