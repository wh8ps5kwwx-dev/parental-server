package com.example.myrana.enforcement

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.example.myrana.data.remote.NetworkModule
import com.example.myrana.permissions.StorageAccessHelper
import com.example.myrana.identity.ChildIdentity

/**
 * فحص ملفات الصور/الفيديو/الصوت المحفوظة على الجهاز:
 * - اسم الملف ومساره
 * - بيانات وصفية (عنوان/فنان) عبر [MediaMetadataRetriever]
 * - مطابقة كلمات [SafetyKeywordCatalog] و[catalog.json]
 */
object MediaLibraryScanner {

    private const val TAG = "MediaLibraryScanner"
    private const val PREFS = "myrana_media_scan"
    private const val KEY_LAST_SCAN_MS = "last_scan_ms"
    private const val KEY_ALERTED_IDS = "alerted_ids"
    private const val SCAN_INTERVAL_MS = 6 * 60 * 60 * 1000L
    private const val MAX_NEW_ALERTS = 8
    private const val LOOKBACK_MS = 14L * 24 * 60 * 60 * 1000

    fun scanIfDue(context: Context) {
        val app = context.applicationContext
        if (!StorageAccessHelper.hasMediaReadAccess(app)) return
        val childCode = ChildIdentity.apiCode(app)
        if (childCode.isBlank()) return
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_SCAN_MS, 0L)
        val now = System.currentTimeMillis()
        if (now - last < SCAN_INTERVAL_MS) return

        val alerted = prefs.getStringSet(KEY_ALERTED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var newAlerts = 0
        val since = now - LOOKBACK_MS

        scanCollection(app, childCode, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, since, alerted) {
            if (++newAlerts >= MAX_NEW_ALERTS) return@scanCollection
        }
        if (newAlerts < MAX_NEW_ALERTS) {
            scanCollection(app, childCode, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, since, alerted) {
                if (++newAlerts >= MAX_NEW_ALERTS) return@scanCollection
            }
        }
        if (newAlerts < MAX_NEW_ALERTS && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scanCollection(app, childCode, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, since, alerted) {
                if (++newAlerts >= MAX_NEW_ALERTS) return@scanCollection
            }
        }

        val trimmed = if (alerted.size > 500) alerted.toList().takeLast(500).toSet() else alerted.toSet()
        prefs.edit()
            .putLong(KEY_LAST_SCAN_MS, now)
            .putStringSet(KEY_ALERTED_IDS, trimmed)
            .apply()
        Log.i(TAG, "scan done — newAlerts=$newAlerts alertedTotal=${alerted.size}")
    }

    private fun scanCollection(
        context: Context,
        childCode: String,
        uri: Uri,
        sinceMs: Long,
        alerted: MutableSet<String>,
        onAlert: () -> Unit,
    ): Boolean {
        if (alerted.size > 600) return false
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.DATE_ADDED,
        )
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} >= ?"
        val args = arrayOf((sinceMs / 1000).toString())
        val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

        context.contentResolver.query(uri, projection, selection, args, sort)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val pathIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
            var count = 0
            while (cursor.moveToNext() && count < 120) {
                count++
                val id = cursor.getLong(idIdx)
                val key = "$uri|$id"
                if (key in alerted) continue
                val name = cursor.getString(nameIdx).orEmpty()
                val path = cursor.getString(pathIdx).orEmpty()
                val blob = "$name $path"
                val meta = readMetadata(context, uri, id)
                val fullText = listOf(blob, meta.title, meta.artist, meta.album).joinToString(" ")

                val hit = PolicyFilterCache.matchSafetySearch(listOf(fullText))
                    ?: PolicyFilterCache.matchVideoKeyword(listOf(fullText))?.let {
                        SafetyKeywordCatalog.Entry("ملف وسائط", it)
                    }
                if (hit == null) continue

                val kind = when {
                    uri == MediaStore.Video.Media.EXTERNAL_CONTENT_URI -> "فيديو"
                    uri == MediaStore.Audio.Media.EXTERNAL_CONTENT_URI -> "صوت"
                    else -> "صورة"
                }
                val message = buildString {
                    append("فحص ملفات الجوال — $kind محفوظ يحتوي محتوى خطر: ")
                    append("«${hit.keyword}» (فئة: ${hit.category})")
                    append(" — $name")
                    if (meta.title.isNotBlank()) append(" | عنوان: ${meta.title}")
                }
                if (NetworkModule.postAlertSync(childCode, message)) {
                    alerted.add(key)
                    onAlert()
                }
            }
        }
        return true
    }

    private data class MediaMeta(val title: String, val artist: String, val album: String)

    private fun readMetadata(context: Context, collection: Uri, id: Long): MediaMeta {
        val itemUri = Uri.withAppendedPath(collection, id.toString())
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, itemUri)
            MediaMeta(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
            )
        } catch (_: Exception) {
            MediaMeta("", "", "")
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }
}
