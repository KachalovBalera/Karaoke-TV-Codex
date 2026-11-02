package ru.karaoketv.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

class AudioQualityMonitor(private val onSnapshot: (AudioQualitySnapshot) -> Unit) {
    private var recorder: AudioRecord? = null
    private var worker: Thread? = null

    fun start(): Boolean {
        if (worker != null) return true
        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return false
        val bufferSize = (minBuffer * 2).coerceAtLeast(SAMPLE_RATE / 5)
        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
        }.getOrNull() ?: return false
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            return false
        }
        recorder = audioRecord
        if (!runCatching { audioRecord.startRecording() }.isSuccess) {
            audioRecord.release()
            recorder = null
            return false
        }
        worker = Thread {
            val buffer = ShortArray(bufferSize / 2)
            while (!Thread.currentThread().isInterrupted) {
                val read = audioRecord.read(buffer, 0, buffer.size)
                if (read > 0) onSnapshot(SingingQualityAnalyzer.analyze(buffer.copyOf(read), SAMPLE_RATE))
            }
        }.apply { name = "karaoke-audio-quality"; start() }
        return true
    }

    fun stop() {
        worker?.interrupt()
        worker = null
        recorder?.let { audioRecord ->
            runCatching { audioRecord.stop() }
            audioRecord.release()
        }
        recorder = null
    }

    companion object { private const val SAMPLE_RATE = 44_100 }
}
