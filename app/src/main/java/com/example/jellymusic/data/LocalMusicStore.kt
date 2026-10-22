package com.example.jellymusic.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import com.example.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class LocalMusicStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jellymusic_local", Context.MODE_PRIVATE)
    private val artDir = File(appContext.filesDir, "local_art").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val songs = mutableStateListOf<BaseItem>()
    val scanning = mutableStateOf(false)
    val lastError = mutableStateOf<String?>(null)
    val folderLabel = mutableStateOf(prefs.getString(KEY_LABEL, null))
    var treeUri: String? = prefs.getString(KEY_TREE, null)
        private set

    init {
        load()
    }

    fun artFile(itemId: String): File? {
        val file = File(artDir, itemId.replace(':', '_') + ".jpg")
        return file.takeIf { it.exists() }
    }

    fun importFolder(uri: Uri) {
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        treeUri = uri.toString()
        prefs.edit().putString(KEY_TREE, treeUri).apply()
        scanning.value = true
        lastError.value = null
        scope.launch {
            val result = runCatching { scan(uri) }
            result.onSuccess { items ->
                songs.clear()
                songs.addAll(items)
                val label = DocumentFile.fromTreeUri(appContext, uri)?.name
                folderLabel.value = label
                prefs.edit().putString(KEY_LABEL, label).apply()
                persist()
            }.onFailure { e ->
                lastError.value = e.message
            }
            scanning.value = false
        }
    }

    fun rescan() {
        val raw = treeUri ?: return
        importFolder(Uri.parse(raw))
    }

    fun clear() {
        songs.clear()
        treeUri = null
        folderLabel.value = null
        prefs.edit().clear().apply()
        runCatching { artDir.listFiles()?.forEach { it.delete() } }
    }

    private fun scan(rootUri: Uri): List<BaseItem> {
        val root = DocumentFile.fromTreeUri(appContext, rootUri) ?: return emptyList()
        val result = mutableListOf<BaseItem>()
        val stack = ArrayDeque<DocumentFile>()
        stack.add(root)
        var visited = 0
        while (stack.isNotEmpty() && result.size < MAX_TRACKS && visited < MAX_FILES) {
            val dir = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
            for (doc in children) {
                visited++
                if (doc.isDirectory) {
                    stack.add(doc)
                    continue
                }
                val name = doc.name ?: continue
                if (!isAudioFile(name, doc.type)) continue
                readTrack(doc.uri, name)?.let { result.add(it) }
            }
        }
        return result.sortedWith(compareBy({ it.albumArtist ?: "" }, { it.album ?: "" }, { it.name }))
    }

    private fun readTrack(uri: Uri, fileName: String): BaseItem? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(appContext, uri)
            val name = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fileName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() }
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?.takeIf { it.isNotBlank() }
                ?: artist
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() }
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
            val track = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.substringBefore('/')
                ?.trim()
                ?.toIntOrNull()
            val disc = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)
                ?.substringBefore('/')
                ?.trim()
                ?.toIntOrNull()
            val id = "local:" + sha1(uri.toString()).take(16)
            val art = retriever.embeddedPicture?.let { saveArt(id, it) }
            val ext = fileName.substringAfterLast('.', "").lowercase()
            BaseItem(
                id = id,
                name = name,
                type = "Audio",
                isFolder = false,
                album = album,
                albumArtist = albumArtist,
                artists = listOfNotNull(artist),
                runTimeTicks = durationMs?.let { it * 10_000 },
                trackNumber = track,
                discNumber = disc,
                container = ext.takeIf { it in ALLOWED_DOWNLOAD_EXTENSIONS },
                hasImage = art != null,
                localUri = uri.toString(),
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun saveArt(id: String, bytes: ByteArray): File? = runCatching {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@runCatching null
        val size = maxOf(bitmap.width, bitmap.height)
        val scaled = if (size > 800) {
            val ratio = 800f / size
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt().coerceAtLeast(1),
                (bitmap.height * ratio).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== bitmap) bitmap.recycle() }
        } else {
            bitmap
        }
        val file = File(artDir, id.replace(':', '_') + ".jpg")
        FileOutputStream(file).use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        scaled.recycle()
        file
    }.getOrNull()

    private fun isAudioFile(name: String, mime: String?): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in ALLOWED_DOWNLOAD_EXTENSIONS) return true
        return mime?.startsWith("audio/") == true
    }

    private fun persist() {
        runCatching {
            val array = JSONArray()
            songs.forEach { array.put(it.toJson()) }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY_ITEMS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                songs.add(baseItemFromJson(array.getJSONObject(i)))
            }
        }
    }

    private fun sha1(value: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val KEY_TREE = "tree_uri"
        const val KEY_LABEL = "label"
        const val KEY_ITEMS = "items"
        const val MAX_TRACKS = 5000
        const val MAX_FILES = 20000
    }
}
