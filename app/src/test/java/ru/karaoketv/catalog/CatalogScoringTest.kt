package ru.karaoketv.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogScoringTest {
    @Test fun removesServiceWordsAndPunctuation() {
        assertEquals("кино группа крови", CatalogScoring.key("Кино — Группа крови (караоке HD)"))
    }

    @Test fun recognizesSameNormalizedSong() {
        assertTrue(CatalogScoring.isLikelySameSong("Кино", "Группа крови", "КИНО", "Группа крови karaoke"))
    }

    @Test fun recognizesSmallSpellingDifference() {
        assertTrue(CatalogScoring.isLikelySameSong("Кино", "Группа крови", "Киноо", "Группа кровии"))
    }

    @Test fun doesNotMergeRemixWithOriginal() {
        org.junit.Assert.assertFalse(CatalogScoring.isLikelySameSong("Кино", "Группа крови", "Кино", "Группа крови remix"))
    }
}
