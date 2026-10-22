package com.sinoar.app.stt

import android.content.Context
import java.io.File

/** Reads 16-bit mono PCM WAV files produced by the recorder. */
object WavReader {

    fun read(file: File): ShortArray {
        val bytes = file.readBytes()
        var offset = 12
        while (offset + 8 <= bytes.size) {
            val id = String(bytes, offset, 4, Charsets.US_ASCII)
            val size = leInt(bytes, offset + 4)
            if (id == "data") {
                val available = (bytes.size - offset - 8).coerceAtLeast(0)
                val count = minOf(size, available) / 2
                val samples = ShortArray(count)
                for (i in 0 until count) {
                    val lo = bytes[offset + 8 + i * 2].toInt() and 0xFF
                    val hi = bytes[offset + 9 + i * 2].toInt() and 0xFF
                    samples[i] = ((hi shl 8) or lo).toShort()
                }
                return samples
            }
            offset += 8 + size
        }
        return ShortArray(0)
    }

    private fun leInt(bytes: ByteArray, offset: Int): Int {
        if (offset + 3 >= bytes.size) return 0
        return (bytes[offset].toInt() and 0xFF) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 3].toInt() and 0xFF) shl 24)
    }
}
