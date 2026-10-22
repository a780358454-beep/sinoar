package com.sinoar.app.actions

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

data class MediaItem(val title: String, val uri: String, val isVideo: Boolean)

object MediaFinder {

    fun search(context: Context, query: String): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
        )

        val collections = listOf(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI to false,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI to true,
        )

        for ((collection, isVideo) in collections) {
            runCatching {
                context.contentResolver.query(
                    collection,
                    projection,
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                    arrayOf("%$query%"),
                    null
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext() && results.size < 10) {
                        val id = cursor.getLong(idIdx)
                        val name = cursor.getString(nameIdx) ?: continue
                        val uri = ContentUris.withAppendedId(collection, id)
                        results.add(MediaItem(name, uri.toString(), isVideo))
                    }
                }
            }
        }
        return results
    }
}
