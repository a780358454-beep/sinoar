package com.sinoar.app.ui

import android.Manifest
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sinoar.app.audio.VoiceRecorder
import com.sinoar.app.stt.ModelStore
import com.sinoar.app.stt.VoskTranscriber
import com.sinoar.app.stt.WavReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@Composable
fun PulseScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val recorder = remember(context) { VoiceRecorder(context) }
    val state by recorder.state.collectAsState()
    val scope = rememberCoroutineScope()

    var granted by remember { mutableStateOf(recorder.hasPermission()) }
    var deniedOnce by remember { mutableStateOf(false) }

    var modelReady by remember { mutableStateOf(ModelStore.hasModel(context)) }
    var importing by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var transcribing by remember { mutableStateOf(false) }
    var transcript by remember { mutableStateOf<String?>(null) }
    var transcriber by remember { mutableStateOf<VoskTranscriber?>(null) }

    var ttsReady by remember { mutableStateOf(false) }
    var ttsArabic by remember { mutableStateOf(false) }
    val tts = remember(context) {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }

    LaunchedEffect(ttsReady) {
        if (ttsReady) {
            val result = tts.setLanguage(Locale("ar"))
            ttsArabic = result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED
        }
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            deniedOnce = !ok
        }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                importing = true
                importStatus = "جارٍ استيراد النموذج…"
                val result = withContext(Dispatchers.IO) {
                    runCatching {
                        ModelStore.importZip(context, uri) { copied ->
                            importStatus = "استُورد ${copied / (1024 * 1024)} ميجابايت…"
                        }
                    }
                }
                importing = false
                if (result.isSuccess) {
                    modelReady = true
                    importStatus = "تم استيراد النموذج بنجاح"
                } else {
                    importStatus = "فشل الاستيراد: ${result.exceptionOrNull()?.message}"
                }
            }
        }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
            transcriber?.close()
            tts.stop()
            tts.shutdown()
            recorder.stop()
        }
    }

    val lastFile = state.lastFile
    LaunchedEffect(lastFile, modelReady) {
        if (lastFile == null || !modelReady) return@LaunchedEffect
        transcribing = true
        transcript = null
        val text = withContext(Dispatchers.IO) {
            runCatching {
                val engine = transcriber
                    ?: VoskTranscriber(ModelStore.modelDir(context)!!).also { transcriber = it }
                engine.transcribe(WavReader.read(lastFile))
            }.getOrElse { "" }
        }
        transcript = text
        transcribing = false
        if (ttsReady && ttsArabic) {
            val spoken = text.ifBlank { "لم أسمع كلامًا واضحًا" }
            tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "sinoar")
        }
    }

    val level = ((state.rmsDb + 60f) / 60f).coerceIn(0f, 1f)
    val pulse by animateFloatAsState(
        targetValue = if (state.isRecording) 1f + level * 0.10f else 1f,
        label = "pulse"
    )

    val playRecording: (File) -> Unit = { file ->
        runCatching { player?.release() }
        val mp = MediaPlayer()
        val ok = runCatching {
            mp.setDataSource(file.absolutePath)
            mp.prepare()
            mp.start()
        }.isSuccess
        if (ok) player = mp else mp.release()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "سنوار",
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "مرحلة الفهم · P2",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Box(
            modifier = Modifier
                .size(180.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    if (state.isRecording) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
                .clickable {
                    when {
                        !granted -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        state.isRecording -> recorder.stop()
                        else -> {
                            transcript = null
                            recorder.start(scope)
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isRecording) "إيقاف" else "تحدّث",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRecording) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(Modifier.height(18.dp))

        val error = state.error
        Text(
            text = when {
                error != null -> error
                !granted -> "نحتاج إذن الميكروفون للبدء"
                state.isRecording && state.speaking -> "أسمعك…"
                state.isRecording -> "أستمع… (صمت)"
                transcribing -> "أحوّل كلامك إلى نص…"
                state.lastFile != null -> "تم حفظ التسجيل"
                else -> "اضغط للتحدث"
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onBackground
            }
        )

        if (state.isRecording) {
            Text(
                text = String.format(Locale.US, "%.1f ثانية", state.seconds),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth(0.75f)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(level)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = String.format(
                Locale.US,
                "المستوى %.0f dB · أرضية الضجيج %.0f dB",
                state.rmsDb,
                state.noiseFloorDb
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(20.dp))

        if (!modelReady) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "نموذج التعرّف على الكلام غير مثبت",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "نزّل نموذج Vosk العربي على الهاتف، ثم استورده هنا مرة واحدة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (importing) "جارٍ الاستيراد…" else "استيراد النموذج",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(enabled = !importing) {
                            importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                        }
                    )
                    val status = importStatus
                    if (status != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        val text = transcript
        if (text != null) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "سمعتك تقول",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = text.ifBlank { "— لم أتعرّف على كلام واضح —" },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (!ttsArabic && ttsReady) {
            Text(
                text = "تنبيه: الصوت العربي غير مثبت على الجهاز، لن يرد سنوار صوتيًا.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
        }

        if (deniedOnce) {
            Text(
                text = "يمكنك منح الإذن من إعدادات التطبيق ← الأذونات ← الميكروفون",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
        }

        if (lastFile != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "آخر تسجيل",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lastFile.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "تشغيل",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { playRecording(lastFile) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "محلي بالكامل · بلا إنترنت · لا يغادر الصوت الجهاز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
