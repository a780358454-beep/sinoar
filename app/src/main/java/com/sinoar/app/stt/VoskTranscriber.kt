package com.sinoar.app.stt

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable
import java.io.File

/**
 * On-device Arabic transcription with Vosk.
 *
 * Runs fully offline. The model directory is imported once by the user
 * (see ModelStore) and then reused for every utterance.
 */
class VoskTranscriber(private val modelDir: File) : Closeable {

    private var model: Model? = null

    @Synchronized
    fun transcribe(samples: ShortArray): String {
        if (samples.isEmpty()) return ""

        val loaded = model ?: Model(modelDir.absolutePath).also { model = it }
        val recognizer = Recognizer(loaded, SAMPLE_RATE.toFloat())
        return try {
            recognizer.acceptWaveForm(samples, samples.size)
            JSONObject(recognizer.finalResult).optString("text", "").trim()
        } finally {
            recognizer.close()
        }
    }

    override fun close() {
        model?.close()
        model = null
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
