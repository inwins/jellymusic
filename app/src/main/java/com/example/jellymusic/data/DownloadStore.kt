package com.example.jellymusic.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import com.example.jellymusic.R
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.jellyfin.JellyfinApi
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
import java.util.concurrent.ConcurrentHashMap

data class DownloadRecord(
    val item: BaseItem,
    val uri: String,
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

    private fun pump(api: JellyfinApi) {
        while (active < maxConcurrent && pending.isNotEmpty()) {
            val item = pending.removeAt(0)
            savePending()
            active++
            val job = scope.launch {
                try {
                    downloadNow(api, item)
                } finally {
                    jobs.remove(item.id)
                    active = (active - 1).coerceAtLeast(0)
                    pump(api)
                }
            }
            jobs[item.id] = job
        }
    }

    suspend fun downloadNow(api: JellyfinApi, item: BaseItem): Boolean {
        val id = item.id
        if (records.containsKey(id)) return true
        withContext(Dispatchers.Main) {
            progress[id] = 0f
            errors.remove(id)
        }
        var lastUpdate = 0L
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
                records[id] = DownloadRecord(item, finalUri.toString())
            }
            saveIndex()
            withContext(Dispatchers.Main) {
                progress.remove(id)
                downloadedIds[id] = true
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
                errors[record.item.id] = context.getString(R.string.download_failed)
                removed++
            }
        }
        if (removed > 0) {
            saveIndex()
        }
        return removed
    }

    fun totalBytes(): Long {
        var total = 0L
        for (record in records.values) {
            val uri = Uri.parse(record.uri)
            total += if (uri.scheme == "file") {
                File(uri.path ?: "").length()
            } else {
                runCatching { DocumentFile.fromSingleUri(context, uri)?.length() ?: 0L }
                    .getOrDefault(0L)
            }
        }
        return total
    }

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

    fun delete(item: BaseItem) {
        val record = records.remove(item.id) ?: return
        deleteUri(Uri.parse(record.uri))
        downloadedIds.remove(item.id)
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
                        records[record.item.id] = DownloadRecord(record.item, finalUri.toString())
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
                    Uri.fromFile(File(privateDir, downloadFileName(item))).toString()
                }
                val uri = Uri.parse(rawUri)
                if (exists(uri)) {
                    records[item.id] = DownloadRecord(item, rawUri)
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
                array.put(itemToJson(record.item).put("uri", record.uri))
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
