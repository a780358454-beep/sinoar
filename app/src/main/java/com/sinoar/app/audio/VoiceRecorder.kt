package com.sinoar.app.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.log10
import kotlin.math.sqrt

data class RecorderUiState(
    val isRecording: Boolean = false,
    val rmsDb: Float = -90f,
    val noiseFloorDb: Float = -90f,
    val speaking: Boolean = false,
    val seconds: Float = 0f,
    val lastFile: File? = null,
    val error: String? = null,
)

/**
 * Captures 16 kHz mono PCM16 from the microphone, tracks loudness and speech
 * activity, and saves the take as a WAV file in private storage.
 *
 * Phase 1 deliberately uses the platform's VOICE_RECOGNITION source (which
 * carries the device's own noise suppression / AGC). Our custom front-end
 * (48 kHz raw capture, notch, denoise) arrives in phase 6.
 */
class VoiceRecorder(
    private val context: Context,
    private val sampleRate: Int = 16_000,
) {
    private val _state = MutableStateFlow(RecorderUiState())
    val state: StateFlow<RecorderUiState> = _state.asStateFlow()

    private var job: Job? = null

    @Volatile
    private var record: AudioRecord? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    fun start(scope: CoroutineScope, maxSeconds: Int = 20) {
        if (_state.value.isRecording) return

        if (!hasPermission()) {
            _state.value = _state.value.copy(error = "التطبيق لا يملك إذن الميكروفون")
            return
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) {
            _state.value = _state.value.copy(error = "الميكروفون غير متاح على هذا الجهاز")
            return
        }

        @Suppress("DEPRECATION")
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, sampleRate / 5 * 2) * 2
        )

        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release()
            _state.value = _state.value.copy(error = "فشل تهيئة الميكروفون")
            return
        }

        record = rec
        _state.value = RecorderUiState(isRecording = true)

        job = scope.launch(Dispatchers.IO) {
            val chunks = ArrayList<ShortArray>(maxSeconds * 20)
            val endpointer = Endpointer()
            var total = 0
            var lastUi = 0L

            try {
                rec.startRecording()
                val frame = ShortArray(sampleRate / 50) // 20 ms

                while (isActive && total < maxSeconds * sampleRate) {
                    val read = rec.read(frame, 0, frame.size)
                    if (read <= 0) continue

                    val chunk = frame.copyOf(read)
                    chunks.add(chunk)
                    total += read

                    val db = rmsDb(chunk)
                    val speaking = endpointer.update(db)

                    val now = System.currentTimeMillis()
                    if (now - lastUi >= 60) {
                        lastUi = now
                        _state.value = _state.value.copy(
                            rmsDb = db,
                            noiseFloorDb = endpointer.noiseFloorDb,
                            speaking = speaking,
                            seconds = total.toFloat() / sampleRate,
                        )
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    _state.value = _state.value.copy(error = t.message ?: "خطأ أثناء التسجيل")
                }
            } finally {
                runCatching { rec.stop() }
                runCatching { rec.release() }
                record = null

                val samples = ShortArray(chunks.sumOf { it.size })
                var offset = 0
                for (chunk in chunks) {
                    chunk.copyInto(samples, offset)
                    offset += chunk.size
                }

                val saved = if (samples.size > sampleRate / 10) {
                    runCatching { WavWriter.write(context, samples, sampleRate) }.getOrNull()
                } else {
                    null
                }

                _state.value = _state.value.copy(
                    isRecording = false,
                    speaking = false,
                    rmsDb = -90f,
                    seconds = 0f,
                    lastFile = saved ?: _state.value.lastFile,
                    error = when {
                        saved != null -> null
                        samples.isNotEmpty() -> "التسجيل قصير جدًا"
                        else -> _state.value.error
                    },
                )
            }
        }
    }

    fun stop() {
        runCatching { record?.stop() }
        job?.cancel()
    }

    private fun rmsDb(samples: ShortArray): Float {
        if (samples.isEmpty()) return -90f
        var sum = 0.0
        for (s in samples) {
            val v = s.toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / samples.size)
        if (rms <= 0.0) return -90f
        return (20.0 * log10(rms / 32768.0)).toFloat().coerceIn(-90f, 0f)
    }
}
