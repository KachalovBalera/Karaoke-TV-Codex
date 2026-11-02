package ru.karaoketv.catalog

import ru.karaoketv.data.KaraokeVersion

object CatalogScoring {
    private val serviceWords = setOf(
        "karaoke", "караоке", "lyrics", "текст", "минусовка", "instrumental", "minus", "hd", "official"
    )

    fun key(value: String): String = value.lowercase()
        .replace(Regex("[«»()\\[\\]{}]"), " ")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .split(' ')
        .filter { it.isNotBlank() && it !in serviceWords }
        .joinToString(" ")
        .trim()

    fun isLikelySameSong(leftArtist: String, leftTitle: String, rightArtist: String, rightTitle: String): Boolean {
        val leftArtistKey = key(leftArtist)
        val rightArtistKey = key(rightArtist)
        val leftTitleKey = key(leftTitle)
        val rightTitleKey = key(rightTitle)
        if (leftArtistKey == rightArtistKey && leftTitleKey == rightTitleKey) return true
        if (hasVersionMarker(leftTitle) != hasVersionMarker(rightTitle)) return false
        return similarEnough(leftArtistKey, rightArtistKey) && similarEnough(leftTitleKey, rightTitleKey)
    }

    private fun similarEnough(left: String, right: String): Boolean =
        similarity(left, right) >= if (maxOf(left.length, right.length) <= 5) 0.8 else 0.88

    private fun hasVersionMarker(value: String): Boolean = Regex("(?i)\\b(remix|дуэт|feat|ft|live|acoustic|акустическ|тональност)\\b").containsMatchIn(value)

    fun similarity(left: String, right: String): Double {
        if (left == right) return 1.0
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val previous = IntArray(right.length + 1) { it }
        for (i in left.indices) {
            var diagonal = previous[0]
            previous[0] = i + 1
            for (j in right.indices) {
                val old = previous[j + 1]
                previous[j + 1] = minOf(previous[j + 1] + 1, previous[j] + 1, diagonal + if (left[i] == right[j]) 0 else 1)
                diagonal = old
            }
        }
        return 1.0 - previous[right.length].toDouble() / maxOf(left.length, right.length)
    }

    fun rank(version: KaraokeVersion, localStars: Int? = null): Int {
        var score = version.karaokeConfidence.coerceIn(0, 100)
        score += when (version.playbackCapability.name) {
            "EMBEDDED" -> 20
            "EXTERNAL" -> 8
            else -> 0
        }
        score += (localStars ?: 0) * 4
        if (version.durationSeconds != null && version.durationSeconds in 90..600) score += 5
        return score
    }
}
