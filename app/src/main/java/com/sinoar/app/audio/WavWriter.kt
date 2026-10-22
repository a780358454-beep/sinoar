package com.sinoar.app.audio

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Writes 16-bit PCM mono WAV files into the app's private storage. */
object WavWriter {

    fun write(context: Context, samples: ShortArray, sampleRate: Int): File {
        val dir = File(context.filesDir, "recordings")
        if (!dir.exists()) dir.mkdirs()

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "rec_$stamp.wav")

        val dataSize = samples.size * 2
        val bytes = ByteArray(dataSize)
        var i = 0
        for (s in samples) {
            val v = s.toInt()
            bytes[i++] = (v and 0xFF).toByte()
            bytes[i++] = ((v shr 8) and 0xFF).toByte()
        }

        file.outputStream().use { out ->
            out.write(header(dataSize, sampleRate))
            out.write(bytes)
        }
        return file
    }

    private fun header(dataSize: Int, sampleRate: Int): ByteArray {
        val h = ByteArray(44)
        putAscii(h, 0, "RIFF")
        putInt(h, 4, dataSize + 36)
        putAscii(h, 8, "WAVE")
        putAscii(h, 12, "fmt ")
        putInt(h, 16, 16)
        putShort(h, 20, 1)              // PCM
        putShort(h, 22, 1)              // mono
        putInt(h, 24, sampleRate)
        putInt(h, 28, sampleRate * 2)   // byte rate
        putShort(h, 32, 2)              // block align
        putShort(h, 34, 16)             // bits per sample
        putAscii(h, 36, "data")
        putInt(h, 40, dataSize)
        return h
    }

    private fun putAscii(target: ByteArray, offset: Int, value: String) {
        for (i in value.indices) target[offset + i] = value[i].code.toByte()
    }

    private fun putInt(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
        target[offset + 2] = ((value shr 16) and 0xFF).toByte()
        target[offset + 3] = ((value shr 24) and 0xFF).toByte()
    }

    private fun putShort(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xFF).toByte()
        target[offset + 1] = ((value shr 8) and 0xFF).toByte()
    }
}
