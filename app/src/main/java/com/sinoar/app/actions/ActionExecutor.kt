package com.sinoar.app.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.provider.AlarmClock
import androidx.core.content.ContextCompat
import java.util.Locale

/** Executes a user-confirmed action. Nothing here runs without confirmation. */
object ActionExecutor {

    private var player: MediaPlayer? = null

    fun call(context: Context, number: String): String {
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return if (canCall) "جارٍ الاتصال…" else "فُتح لوحة الاتصال (إذن المكالمات غير ممنوح)"
    }

    fun alarm(context: Context, hour: Int, minute: Int, label: String): String {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        return String.format(Locale.US, "ضُبط المنبه %02d:%02d", hour, minute)
    }

    fun play(context: Context, item: MediaItem): String {
        if (item.isVideo) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.uri))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return "فتح: ${item.title}"
        }

        runCatching { player?.release() }
        val mediaPlayer = MediaPlayer()
        return runCatching {
            mediaPlayer.setDataSource(context, Uri.parse(item.uri))
            mediaPlayer.prepare()
            mediaPlayer.start()
            player = mediaPlayer
            "تشغيل: ${item.title}"
        }.getOrElse {
            runCatching { mediaPlayer.release() }
            "تعذر تشغيل الملف"
        }
    }
}
