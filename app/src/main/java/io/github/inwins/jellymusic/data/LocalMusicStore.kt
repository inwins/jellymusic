package io.github.inwins.jellymusic.data

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import io.github.inwins.jellymusic.jellyfin.BaseItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

class LocalMusicStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("jellymusic_local", Context.MODE_PRIVATE)
    private val artDir = File(appContext.filesDir, "local_art").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val songs = mutableStateListOf<BaseItem>()
    private val folders = mutableMapOf<String, String>()
    private val overrides = mutableMapOf<String, Triple<String, String, String>>()
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
        val stack = ArrayDeque<Pair<DocumentFile, String>>()
        stack.add(root to (root.name ?: ""))
        var visited = 0
        while (stack.isNotEmpty() && result.size < MAX_TRACKS && visited < MAX_FILES) {
            val (dir, folderName) = stack.removeLast()
            val children = runCatching { dir.listFiles() }.getOrDefault(emptyArray())
            for (doc in children) {
                visited++
                if (doc.isDirectory) {
                    stack.add(doc to (doc.name ?: folderName))
                    continue
                }
                val name = doc.name ?: continue
                if (!isAudioFile(name, doc.type)) continue
                readTrack(doc.uri, name)?.let { item ->
                    folders[item.id] = folderName
                    result.add(item)
                }
            }
        }
        persistFolders()
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

    fun folderOf(itemId: String): String? = folders[itemId]

    fun isAvailable(item: BaseItem): Boolean {
        val raw = item.localUri ?: return true
        return runCatching {
            DocumentFile.fromSingleUri(appContext, Uri.parse(raw))?.exists() == true
        }.getOrDefault(false)
    }

    fun setOverride(itemId: String, title: String, artist: String, album: String) {
        overrides[itemId] = Triple(title, artist, album)
        val index = songs.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            val item = songs[index]
            songs[index] = item.copy(
                name = title.ifBlank { item.name },
                albumArtist = artist.ifBlank { item.albumArtist },
                artists = if (artist.isBlank()) item.artists else listOf(artist),
                album = album.ifBlank { item.album },
            )
        }
        persist()
        persistOverrides()
    }

    fun setCover(itemId: String, uri: Uri) {
        runCatching {
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return@use
                val file = File(artDir, itemId.replace(':', '_') + ".jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
                }
                bitmap.recycle()
            }
        }
        val index = songs.indexOfFirst { it.id == itemId }
        if (index >= 0) {
            songs[index] = songs[index].copy(hasImage = true)
        }
        persist()
    }

    private fun persistFolders() {
        runCatching {
            val json = JSONObject()
            folders.forEach { (id, name) -> json.put(id, name) }
            prefs.edit().putString(KEY_FOLDERS, json.toString()).apply()
        }
    }

    private fun persistOverrides() {
        runCatching {
            val json = JSONObject()
            overrides.forEach { (id, triple) ->
                json.put(
                    id,
                    JSONObject()
                        .put("title", triple.first)
                        .put("artist", triple.second)
                        .put("album", triple.third),
                )
            }
            prefs.edit().putString(KEY_OVERRIDES, json.toString()).apply()
        }
    }

    private fun persist() {
        runCatching {
            val array = JSONArray()
            songs.forEach { array.put(it.toJson()) }
            prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
        }
    }

    private fun load() {
        runCatching {
            prefs.getString(KEY_FOLDERS, null)?.let { raw ->
                val json = JSONObject(raw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    folders[key] = json.optString(key)
                }
            }
            prefs.getString(KEY_OVERRIDES, null)?.let { raw ->
                val json = JSONObject(raw)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.optJSONObject(key) ?: continue
                    overrides[key] = Triple(
                        value.optString("title"),
                        value.optString("artist"),
                        value.optString("album"),
                    )
                }
            }
        }
        val raw = prefs.getString(KEY_ITEMS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val item = baseItemFromJson(array.getJSONObject(i))
                val override = overrides[item.id]
                songs.add(
                    if (override == null) {
                        item
                    } else {
                        item.copy(
                            name = override.first.ifBlank { item.name },
                            albumArtist = override.second.ifBlank { item.albumArtist },
                            artists = if (override.second.isBlank()) {
                                item.artists
                            } else {
                                listOf(override.second)
                            },
                            album = override.third.ifBlank { item.album },
                        )
                    },
                )
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
        const val KEY_FOLDERS = "folders"
        const val KEY_OVERRIDES = "overrides"
        const val MAX_TRACKS = 5000
        const val MAX_FILES = 20000
    }
}
