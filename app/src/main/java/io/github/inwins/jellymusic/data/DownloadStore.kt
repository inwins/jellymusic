package io.github.inwins.jellymusic.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import io.github.inwins.jellymusic.R
import io.github.inwins.jellymusic.jellyfin.BaseItem
import io.github.inwins.jellymusic.jellyfin.JellyfinApi
import io.github.inwins.jellymusic.jellyfin.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

data class DownloadRecord(
    val item: BaseItem,
    val uri: String,
    val addedAt: Long = 0L,
)

data class MigrationState(
    val done: Int,
    val total: Int,
    val failed: Int = 0,
)

class DownloadStore(
    private val context: Context,
    private val settings: AppSettings,
) {
    private val privateDir = File(context.getExternalFilesDir(null), "audio").apply { mkdirs() }
    private val indexFile = File(privateDir, "index.json")

    private val records = ConcurrentHashMap<String, DownloadRecord>()
    private val jobs = ConcurrentHashMap<String, Job>()

    val downloadedIds = mutableStateMapOf<String, Boolean>()
    val progress = mutableStateMapOf<String, Float>()
    val errors = mutableStateMapOf<String, String>()
    val migration = mutableStateOf<MigrationState?>(null)
    val sizes = mutableStateOf<Map<String, Long>>(emptyMap())
    val paused = mutableStateOf(false)
    private val activeItems = java.util.concurrent.ConcurrentHashMap<String, BaseItem>()
    val directoryLabel = mutableStateOf(settings.downloadDirLabel)

    val isMigrating: Boolean get() = migration.value != null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val queuePrefs = context.getSharedPreferences("jellymusic_downloads", Context.MODE_PRIVATE)
    private val pending = mutableListOf<BaseItem>()
    private var active = 0
    private val maxConcurrent = 2

    init {
        loadIndex()
        loadPending()
        cleanupStaleParts()
        refreshSizes()
    }

    fun refreshSizes() {
        scope.launch(Dispatchers.IO) {
            val snapshot = records.values.toList()
            val computed = snapshot.associate { it.item.id to sizeOfUri(it.uri) }
            withContext(Dispatchers.Main) {
                val valid = records.keys.toSet()
                sizes.value = computed.filterKeys { it in valid }
            }
        }
    }

    private fun sizeOfUri(uriString: String): Long {
        val uri = Uri.parse(uriString)
        return when (uri.scheme) {
            "file" -> File(uri.path ?: "").length()
            else -> runCatching {
                DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L
            }.getOrDefault(0L)
        }
    }

    private fun cleanupStaleParts() {
        scope.launch(Dispatchers.IO) {
            runCatching {
                if (settings.downloadTreeUri == null) {
                    privateDir.listFiles()?.forEach { f ->
                        if (f.name.endsWith(".part") && isStalePartFile(f.lastModified())) f.delete()
                    }
                } else {
                    val tree = targetTree() ?: return@launch
                    DocumentFile.fromTreeUri(context, tree)?.listFiles()?.forEach { d ->
                        if (d.name?.endsWith(".part") == true && isStalePartFile(d.lastModified())) {
                            d.delete()
                        }
                    }
                }
            }
        }
    }

    fun pendingCount(): Int = pending.size

    fun uriFor(itemId: String): Uri? {
        val record = records[itemId] ?: return null
        val uri = Uri.parse(record.uri)
        if (uri.scheme == "file") {
            val path = uri.path ?: return null
            return if (File(path).exists()) uri else null
        }
        return uri
    }

    fun isDownloaded(itemId: String): Boolean = uriFor(itemId) != null

    fun downloadedItems(): List<BaseItem> =
        records.values.map { it.item }.sortedBy { it.name }

    fun download(api: JellyfinApi, item: BaseItem) {
        val id = item.id
        if (records.containsKey(id) || progress.containsKey(id) || pending.any { it.id == id }) return
        pending.add(item)
        savePending()
        pump(api)
    }

    fun resumePending(api: JellyfinApi) {
        pump(api)
    }

    fun downloadAll(api: JellyfinApi, items: List<BaseItem>) {
        val added = items
            .distinctBy { it.id }
            .filter { item ->
                !records.containsKey(item.id) &&
                    !progress.containsKey(item.id) &&
                    !activeItems.containsKey(item.id) &&
                    pending.none { it.id == item.id }
            }
        if (added.isEmpty()) return
        pending.addAll(added)
        savePending()
        pump(api)
    }

    fun cancel(item: BaseItem) {
        pending.removeAll { it.id == item.id }
        savePending()
        jobs.remove(item.id)?.cancel()
        progress.remove(item.id)
    }

    fun retry(api: JellyfinApi, item: BaseItem) {
        errors.remove(item.id)
        download(api, item)
    }

    fun pauseAll() {
        paused.value = true
        if (activeItems.isNotEmpty()) {
            pending.addAll(0, activeItems.values.toList())
            activeItems.clear()
        }
        jobs.values.forEach { it.cancel() }
        savePending()
    }

    fun resumeAll(api: JellyfinApi) {
        paused.value = false
        pump(api)
    }

    private fun pump(api: JellyfinApi) {
        if (paused.value) return
        var changed = false
        while (active < maxConcurrent && pending.isNotEmpty()) {
            val item = pending.removeAt(0)
            changed = true
            active++
            activeItems[item.id] = item
            val job = scope.launch {
                try {
                    downloadNow(api, item)
                } finally {
                    activeItems.remove(item.id)
                    jobs.remove(item.id)
                    active = (active - 1).coerceAtLeast(0)
                    pump(api)
                }
            }
            jobs[item.id] = job
        }
        if (changed) savePending()
    }

    suspend fun downloadNow(api: JellyfinApi, item: BaseItem): Boolean {
        val id = item.id
        if (records.containsKey(id)) return true
        withContext(Dispatchers.Main) {
            progress[id] = 0f
            errors.remove(id)
        }
        var lastUpdate = 0L
        var downloadedBytes = 0L
        var tempUri: Uri? = null
        try {
            val name = downloadFileName(item)
            val tempName = "$name.part"
            tempUri = withContext(Dispatchers.IO) { createDocument(tempName) }
            tempUri ?: throw IllegalStateException(context.getString(R.string.download_cannot_create))
            withContext(Dispatchers.IO) {
                val output = openOutput(tempUri)
                    ?: throw IllegalStateException(context.getString(R.string.download_cannot_write))
                output.use { stream ->
                    api.downloadAudio(item, stream) { downloaded, total ->
                        downloadedBytes = downloaded
                        val now = System.currentTimeMillis()
                        if (now - lastUpdate > 200) {
                            lastUpdate = now
                            val value = if (total != null && total > 0) {
                                (downloaded.toFloat() / total).coerceIn(0f, 1f)
                            } else {
                                -1f
                            }
                            scope.launch { progress[id] = value }
                        }
                    }
                }
                val finalUri = finalizeDocument(tempUri, name)
                    ?: throw IllegalStateException(context.getString(R.string.download_save_failed))
                records[id] = DownloadRecord(
                    item,
                    finalUri.toString(),
                    System.currentTimeMillis(),
                )
            }
            saveIndex()
            runCatching { saveLyrics(api, item) }
            val fileSize = if (downloadedBytes > 0L) {
                downloadedBytes
            } else {
                records[id]?.let { sizeOfUri(it.uri) } ?: 0L
            }
            withContext(Dispatchers.Main) {
                progress.remove(id)
                downloadedIds[id] = true
                sizes.value = sizes.value + (id to fileSize)
            }
            return true
        } catch (e: CancellationException) {
            tempUri?.let { deleteUri(it) }
            withContext(Dispatchers.Main) { progress.remove(id) }
            throw e
        } catch (e: Exception) {
            tempUri?.let { deleteUri(it) }
            withContext(Dispatchers.Main) {
                progress.remove(id)
                errors[id] = e.message ?: context.getString(R.string.download_failed)
            }
            return false
        }
    }

    fun verifyDownloads(): Int {
        var removed = 0
        for (record in records.values.toList()) {
            val uri = Uri.parse(record.uri)
            val valid = when (uri.scheme) {
                "file" -> File(uri.path ?: "").let { it.exists() && it.length() > 0 }
                else -> runCatching {
                    DocumentFile.fromSingleUri(context, uri)?.exists() == true
                }.getOrDefault(false)
            }
            if (!valid) {
                records.remove(record.item.id)
                downloadedIds.remove(record.item.id)
                sizes.value = sizes.value - record.item.id
                errors[record.item.id] = context.getString(R.string.download_failed)
                removed++
            }
        }
        if (removed > 0) {
            saveIndex()
        }
        return removed
    }

    fun totalBytes(): Long = sizes.value.values.sum()

    private fun savePending() {
        runCatching {
            val array = JSONArray()
            pending.forEach { array.put(it.toJson()) }
            queuePrefs.edit().putString(KEY_PENDING, array.toString()).apply()
        }
    }

    private fun loadPending() {
        val raw = queuePrefs.getString(KEY_PENDING, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                pending.add(baseItemFromJson(array.getJSONObject(i)))
            }
        }
    }

    fun renameDownloads(onFinished: (renamed: Int, failed: Int) -> Unit = { _, _ -> }) {
        scope.launch {
            var renamed = 0
            var failed = 0
            withContext(Dispatchers.IO) {
                records.values.toList().forEach { record ->
                    try {
                        val current = Uri.parse(record.uri)
                        val currentName = displayName(current)
                        val desired = downloadFileName(record.item)
                        if (currentName == desired) return@forEach
                        val newUri = if (current.scheme == "file") {
                            val file = File(current.path ?: throw IllegalStateException())
                            val target = File(file.parentFile, desired)
                            if (target.exists() && target != file) target.delete()
                            if (!file.renameTo(target)) throw IllegalStateException()
                            Uri.fromFile(target)
                        } else {
                            DocumentsContract.renameDocument(
                                context.contentResolver,
                                current,
                                desired,
                            ) ?: throw IllegalStateException()
                        }
                        currentName?.let { renameLyrics(current, it, desired) }
                        records[record.item.id] = DownloadRecord(
                            record.item,
                            newUri.toString(),
                            record.addedAt,
                        )
                        renamed++
                    } catch (_: Exception) {
                        failed++
                    }
                }
                if (renamed > 0) saveIndex()
            }
            onFinished(renamed, failed)
        }
    }

    private fun displayName(uri: Uri): String? = when (uri.scheme) {
        "file" -> uri.path?.let { File(it).name }
        else -> runCatching { DocumentFile.fromSingleUri(context, uri)?.name }.getOrNull()
    }

    fun sizeBytes(itemId: String): Long = sizes.value[itemId] ?: 0L

    fun addedAt(itemId: String): Long = records[itemId]?.addedAt ?: 0L

    fun delete(item: BaseItem) {
        val record = records.remove(item.id) ?: return
        val audioUri = Uri.parse(record.uri)
        deleteLyricsFor(audioUri)
        deleteUri(audioUri)
        downloadedIds.remove(item.id)
        sizes.value = sizes.value - item.id
        progress.remove(item.id)
        saveIndex()
    }

    fun changeDirectory(treeUri: Uri?, label: String?, migrate: Boolean) {
        if (isMigrating) return
        val previousTree = settings.downloadTreeUri
        val sameTarget = if (treeUri == null) {
            previousTree == null
        } else {
            previousTree == treeUri.toString()
        }

        settings.downloadTreeUri = treeUri?.toString()
        settings.downloadDirLabel = label
        directoryLabel.value = label

        if (sameTarget || !migrate) return
        val snapshot = records.values.toList()
        if (snapshot.isEmpty()) return

        migration.value = MigrationState(0, snapshot.size)
        scope.launch {
            var done = 0
            var failed = 0
            for (record in snapshot) {
                try {
                    withContext(Dispatchers.IO) {
                        val oldUri = Uri.parse(record.uri)
                        val name = downloadFileName(record.item)
                        val tempUri = createDocument("$name.part")
                            ?: throw IllegalStateException(context.getString(R.string.download_target_create_failed))
                        val input = openInput(oldUri)
                            ?: throw IllegalStateException(context.getString(R.string.download_source_unreadable))
                        input.use { source ->
                            val output = openOutput(tempUri)
                                ?: throw IllegalStateException(context.getString(R.string.download_target_write_failed))
                            output.use { sink -> source.copyTo(sink) }
                        }
                        val finalUri = finalizeDocument(tempUri, name)
                            ?: throw IllegalStateException(context.getString(R.string.download_rename_failed))
                        deleteUri(oldUri)
                        records[record.item.id] = DownloadRecord(
                            record.item,
                            finalUri.toString(),
                            record.addedAt,
                        )
                    }
                    done++
                } catch (_: Exception) {
                    failed++
                }
                migration.value = MigrationState(done, snapshot.size, failed)
                saveIndex()
            }
            migration.value = null
        }
    }

    fun resetDirectory(migrate: Boolean) {
        changeDirectory(null, null, migrate)
    }

    // ---- storage helpers ----

    private fun targetTree(): Uri? {
        val raw = settings.downloadTreeUri ?: return null
        val uri = Uri.parse(raw)
        val permitted = context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isWritePermission
        }
        return if (permitted) uri else null
    }

    private fun createDocument(displayName: String): Uri? {
        if (settings.downloadTreeUri == null) {
            val file = File(privateDir, displayName)
            file.parentFile?.mkdirs()
            if (file.exists()) file.delete()
            return Uri.fromFile(file)
        }
        val tree = targetTree()
        if (tree == null) {
            return null
        }
        val dir = DocumentFile.fromTreeUri(context, tree)
        if (dir == null) {
            return null
        }
        dir.findFile(displayName)?.delete()
        val created = dir.createFile(MIME_TYPE, displayName)
        return created?.uri
    }

    private fun openOutput(uri: Uri): OutputStream? = when (uri.scheme) {
        "file" -> runCatching { File(uri.path!!).outputStream() }.getOrNull()
        else -> runCatching { context.contentResolver.openOutputStream(uri) }.getOrNull()
    }

    private fun openInput(uri: Uri): InputStream? = when (uri.scheme) {
        "file" -> runCatching { File(uri.path!!).inputStream() }.getOrNull()
        else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
    }

    private fun finalizeDocument(tempUri: Uri, finalName: String): Uri? {
        if (tempUri.scheme == "file") {
            val temp = File(tempUri.path ?: return null)
            val target = File(temp.parentFile, finalName)
            if (target.exists()) target.delete()
            return if (temp.renameTo(target)) {
                Uri.fromFile(target)
            } else {
                runCatching {
                    temp.copyTo(target, overwrite = true)
                    temp.delete()
                    Uri.fromFile(target)
                }.getOrNull()
            }
        }

        // SingleDocumentFile.renameTo() throws UnsupportedOperationException; use the
        // platform API directly, which the external-storage provider supports.
        val renamedUri = runCatching {
            DocumentsContract.renameDocument(context.contentResolver, tempUri, finalName)
        }.getOrNull()
        if (renamedUri != null) return renamedUri

        // Some providers do not support rename: create the final document and copy.
        val tree = targetTree() ?: return null
        val dir = DocumentFile.fromTreeUri(context, tree) ?: return null
        dir.findFile(finalName)?.delete()
        val finalDoc = dir.createFile(MIME_TYPE, finalName) ?: return null
        val copied = runCatching {
            context.contentResolver.openInputStream(tempUri)?.use { input ->
                context.contentResolver.openOutputStream(finalDoc.uri)?.use { output ->
                    input.copyTo(output)
                }
            } != null
        }.getOrDefault(false)
        if (!copied) {
            finalDoc.delete()
            return null
        }
        deleteUri(tempUri)
        return finalDoc.uri
    }

    private fun lyricsDisplayName(audioName: String): String =
        audioName.substringBeforeLast('.', audioName) + ".lrc"

    private suspend fun saveLyrics(api: JellyfinApi, item: BaseItem) {
        val lines = runCatching { api.getLyrics(item.id) }.getOrNull() ?: return
        if (lines.isEmpty()) return
        val name = lyricsDisplayName(downloadFileName(item))
        val content = buildLyrics(item, lines)
        withContext(Dispatchers.IO) {
            runCatching {
                if (settings.downloadTreeUri == null) {
                    val file = File(privateDir, name)
                    file.parentFile?.mkdirs()
                    file.writeText(content, Charsets.UTF_8)
                } else {
                    val tree = targetTree() ?: return@runCatching
                    val dir = DocumentFile.fromTreeUri(context, tree) ?: return@runCatching
                    dir.findFile(name)?.delete()
                    val doc = dir.createFile("application/octet-stream", name)
                        ?: return@runCatching
                    context.contentResolver.openOutputStream(doc.uri)?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
            }
        }
    }

    private fun buildLyrics(item: BaseItem, lines: List<LyricLine>): String {
        fun tag(value: String): String =
            value.replace(']', ')').replace('\n', ' ').trim()
        return buildString {
            append("[ti:").append(tag(item.name)).append("]\n")
            append("[ar:").append(tag(item.trackArtist.orEmpty())).append("]\n")
            item.album?.let { append("[al:").append(tag(it)).append("]\n") }
            lines.forEach { line ->
                val start = line.startMs
                if (start != null) {
                    val minutes = start / 60_000
                    val seconds = (start % 60_000) / 1000.0
                    append(String.format(Locale.US, "[%02d:%05.2f]", minutes, seconds))
                }
                append(line.text).append('\n')
            }
        }
    }

    private fun deleteLyricsFor(audioUri: Uri) {
        runCatching {
            if (audioUri.scheme == "file") {
                val audio = File(audioUri.path ?: return)
                val lyrics = File(audio.parentFile, lyricsDisplayName(audio.name))
                if (lyrics.exists()) lyrics.delete()
            } else {
                val audioName = displayName(audioUri) ?: return
                val tree = targetTree() ?: return
                val dir = DocumentFile.fromTreeUri(context, tree) ?: return
                dir.findFile(lyricsDisplayName(audioName))?.delete()
            }
        }
    }

    private fun renameLyrics(audioUri: Uri, oldAudioName: String, newAudioName: String) {
        val oldName = lyricsDisplayName(oldAudioName)
        val newName = lyricsDisplayName(newAudioName)
        if (oldName == newName) return
        runCatching {
            if (audioUri.scheme == "file") {
                val audio = File(audioUri.path ?: return)
                val oldFile = File(audio.parentFile, oldName)
                if (oldFile.exists()) oldFile.renameTo(File(audio.parentFile, newName))
            } else {
                val tree = targetTree() ?: return
                val dir = DocumentFile.fromTreeUri(context, tree) ?: return
                val doc = dir.findFile(oldName) ?: return
                DocumentsContract.renameDocument(context.contentResolver, doc.uri, newName)
            }
        }
    }

    private fun deleteUri(uri: Uri) {
        when (uri.scheme) {
            "file" -> runCatching { File(uri.path ?: return).delete() }
            else -> runCatching { DocumentFile.fromSingleUri(context, uri)?.delete() }
        }
    }

    private fun exists(uri: Uri): Boolean {
        return when (uri.scheme) {
            "file" -> {
                val path = uri.path ?: return false
                val file = File(path)
                file.exists() && file.length() > 0
            }

            else -> runCatching {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }.getOrDefault(false)
        }
    }

    // ---- index ----

    private fun loadIndex() {
        if (!indexFile.exists()) return
        try {
            val array = JSONArray(indexFile.readText())
            for (i in 0 until array.length()) {
                val json = array.getJSONObject(i)
                val item = itemFromJson(json)
                val rawUri = json.optString("uri").ifBlank {
                    // Backward compatibility with the old directory-only schema.
                    val candidates = listOf(
                        downloadFileName(item),
                        legacyDownloadFileName(item),
                    ).map { Uri.fromFile(File(privateDir, it)) }
                    (candidates.firstOrNull { exists(it) } ?: candidates.last()).toString()
                }
                val uri = Uri.parse(rawUri)
                if (exists(uri)) {
                    records[item.id] = DownloadRecord(
                        item,
                        rawUri,
                        json.optLong("addedAt", 0L),
                    )
                    downloadedIds[item.id] = true
                }
            }
        } catch (_: Exception) {
            // Corrupt index: ignore, files can still be re-downloaded.
        }
    }

    private fun saveIndex() {
        try {
            val array = JSONArray()
            records.values.forEach { record ->
                array.put(
                    itemToJson(record.item)
                        .put("uri", record.uri)
                        .put("addedAt", record.addedAt),
                )
            }
            indexFile.writeText(array.toString())
        } catch (_: Exception) {
        }
    }

    private fun itemToJson(item: BaseItem): JSONObject = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("type", item.type)
        .put("album", item.album)
        .put("albumArtist", item.albumArtist)
        .put("albumId", item.albumId)
        .put("runTimeTicks", item.runTimeTicks)
        .put("container", item.container)
        .put("hasImage", item.hasImage)
        .put("artists", JSONArray(item.artists))

    private fun itemFromJson(json: JSONObject): BaseItem {
        val artists = json.optJSONArray("artists")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: emptyList()
        return BaseItem(
            id = json.getString("id"),
            name = json.getString("name"),
            type = json.optString("type", "Audio"),
            isFolder = false,
            album = json.optString("album").takeIf { it.isNotBlank() },
            albumArtist = json.optString("albumArtist").takeIf { it.isNotBlank() },
            artists = artists,
            albumId = json.optString("albumId").takeIf { it.isNotBlank() },
            runTimeTicks = json.optLong("runTimeTicks").takeIf { it > 0 },
            container = json.optString("container").takeIf { it.isNotBlank() },
            hasImage = json.optBoolean("hasImage", false),
        )
    }

    companion object {
        private const val KEY_PENDING = "pending_downloads"
        private const val MIME_TYPE = "application/octet-stream"
    }
}
