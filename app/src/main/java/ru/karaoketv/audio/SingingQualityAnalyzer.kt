package ru.karaoketv.audio

import kotlin.math.log10
import kotlin.math.sqrt

data class AudioQualitySnapshot(
    val score: Int,
    val rmsDb: Float,
    val dominantFrequencyHz: Float,
    val signalDetected: Boolean
)

object SingingQualityAnalyzer {
    fun analyze(samples: ShortArray, sampleRate: Int): AudioQualitySnapshot {
        if (samples.isEmpty() || sampleRate <= 0) return AudioQualitySnapshot(0, -120f, 0f, false)
        var sumSquares = 0.0
        var crossings = 0
        for (index in samples.indices) {
            val value = samples[index].toDouble()
            sumSquares += value * value
            if (index > 0 && (samples[index - 1] < 0) != (samples[index] < 0)) crossings++
        }
        val rms = sqrt(sumSquares / samples.size).toFloat()
        val normalized = (rms / Short.MAX_VALUE).coerceAtLeast(0.000001f)
        val db = (20.0 * log10(normalized.toDouble())).toFloat()
        val frequency = crossings * sampleRate.toFloat() / (2f * samples.size)
        val signal = db >= -55f
        val levelScore = (((db + 55f) / 35f) * 65f).toInt().coerceIn(0, 65)
        val pitchScore = if (frequency in 70f..1200f) 35 else 10
        return AudioQualitySnapshot(
            score = if (signal) (levelScore + pitchScore).coerceIn(0, 100) else 0,
            rmsDb = db,
            dominantFrequencyHz = frequency,
            signalDetected = signal
        )
    }
}
