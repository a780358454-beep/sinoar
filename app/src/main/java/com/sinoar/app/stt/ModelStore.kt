package com.sinoar.app.stt

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Stores on-device speech models inside app-private storage.
 *
 * Models are never downloaded by the app (the app has no INTERNET permission).
 * The user imports a model archive once through the system file picker.
 */
object ModelStore {

    private const val MODEL_DIR = "vosk"

    private fun root(context: Context): File =
        File(context.filesDir, "models").apply { mkdirs() }

    fun modelDir(context: Context): File? {
        val dir = File(root(context), MODEL_DIR)
        val looksValid = File(dir, "am").isDirectory || File(dir, "conf").isDirectory
        return if (looksValid) dir else null
    }

    fun hasModel(context: Context): Boolean = modelDir(context) != null

    fun importZip(context: Context, uri: Uri, onProgress: (Long) -> Unit): File {
        val base = root(context)
        val tmp = File(base, "tmp-import")
        if (tmp.exists()) tmp.deleteRecursively()
        tmp.mkdirs()

        var copied = 0L
        var reported = 0L
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("تعذر فتح الملف المحدد")

        input.use { raw ->
            ZipInputStream(BufferedInputStream(raw)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val name = entry.name
                    if (!name.contains("..") && !name.startsWith("__MACOSX")) {
                        val out = File(tmp, name)
                        if (entry.isDirectory) {
                            out.mkdirs()
                        } else {
                            out.parentFile?.mkdirs()
                            out.outputStream().use { fos ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = zip.read(buf)
                                    if (n <= 0) break
                                    fos.write(buf, 0, n)
                                    copied += n
                                    if (copied - reported >= 4L * 1024 * 1024) {
                                        reported = copied
                                        onProgress(copied)
                                    }
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                }
            }
        }

        val found = tmp.walkTopDown().firstOrNull { dir ->
            dir.isDirectory &&
                (File(dir, "am").isDirectory || File(dir, "conf").isDirectory)
        } ?: run {
            tmp.deleteRecursively()
            throw IllegalStateException("الملف لا يحتوي نموذجًا صالحًا")
        }

        val dest = File(base, MODEL_DIR)
        if (dest.exists()) dest.deleteRecursively()
        dest.mkdirs()
        found.listFiles()?.forEach { child ->
            child.copyRecursively(File(dest, child.name), overwrite = true)
        }
        tmp.deleteRecursively()

        if (!hasModel(context)) throw IllegalStateException("بنية النموذج غير متوقعة")
        return dest
    }
}
