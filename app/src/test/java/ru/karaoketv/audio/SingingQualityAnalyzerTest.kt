package ru.karaoketv.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SingingQualityAnalyzerTest {
    @Test fun silenceHasNoSignal() {
        val snapshot = SingingQualityAnalyzer.analyze(ShortArray(4410), 44100)
        assertEquals(0, snapshot.score)
        assertTrue(!snapshot.signalDetected)
    }

    @Test fun audibleToneProducesSignalScore() {
        val samples = ShortArray(4410) { index ->
            (Short.MAX_VALUE * 0.2 * kotlin.math.sin(2.0 * Math.PI * 220.0 * index / 44100.0)).toInt().toShort()
        }
        val snapshot = SingingQualityAnalyzer.analyze(samples, 44100)
        assertTrue(snapshot.signalDetected)
        assertTrue(snapshot.dominantFrequencyHz in 180f..260f)
        assertTrue(snapshot.score > 0)
    }
}
