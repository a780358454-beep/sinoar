package com.sinoar.app.ui

import android.Manifest
import android.media.MediaPlayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            granted = ok
            deniedOnce = !ok
        }

    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            runCatching { player?.release() }
            player = null
            recorder.stop()
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
            text = "مرحلة الصوت · P1",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(200.dp)
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
                        else -> recorder.start(scope)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (state.isRecording) "إيقاف" else "تحدّث",
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.isRecording) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }

        Spacer(Modifier.height(24.dp))

        val error = state.error
        Text(
            text = when {
                error != null -> error
                !granted -> "نحتاج إذن الميكروفون للبدء"
                state.isRecording && state.speaking -> "أسمعك…"
                state.isRecording -> "أستمع… (صمت)"
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

        Spacer(Modifier.height(20.dp))

        // Live level meter
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

        Spacer(Modifier.weight(1f))

        if (deniedOnce) {
            Text(
                text = "يمكنك منح الإذن من إعدادات التطبيق ← الأذونات ← الميكروفون",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(12.dp))
        }

        val last = state.lastFile
        if (last != null) {
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
                            text = last.name,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "تشغيل",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { playRecording(last) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "تسجيل محلي · 16 kHz · mono · PCM16 · لا يغادر الجهاز",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
