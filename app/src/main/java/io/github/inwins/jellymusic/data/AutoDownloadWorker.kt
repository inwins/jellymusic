package io.github.inwins.jellymusic.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.inwins.jellymusic.JellyMusicApp
import io.github.inwins.jellymusic.jellyfin.BaseItem
import java.util.concurrent.TimeUnit

class AutoDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val state = (applicationContext as JellyMusicApp).appState
        val api = state.api ?: return Result.success()
        val settings = state.settings

        if (settings.manualOffline) return Result.success()
        if (settings.autoDownloadWifiOnly && isMetered()) return Result.success()
        if (!settings.autoDownloadFavorites &&
            !settings.autoDownloadPlaylists &&
            !settings.autoDownloadLatest
        ) {
            return Result.success()
        }
        if (!state.checkServer(force = true)) return Result.retry()

        val candidates = LinkedHashMap<String, BaseItem>()
        if (settings.autoDownloadFavorites) {
            runCatching { api.getFavorites() }
                .getOrDefault(emptyList())
                .forEach { candidates[it.id] = it }
        }
        if (settings.autoDownloadLatest) {
            runCatching { api.getLatest(50) }
                .getOrDefault(emptyList())
                .forEach { candidates[it.id] = it }
        }
        if (settings.autoDownloadPlaylists) {
            val playlists = runCatching { api.getPlaylists() }.getOrDefault(emptyList())
            for (playlist in playlists) {
                runCatching { api.getPlaylistTracks(playlist.id) }
                    .getOrDefault(emptyList())
                    .forEach { candidates[it.id] = it }
            }
        }

        val limitBytes = settings.autoDownloadMaxMb.toLong() * 1024L * 1024L
        var downloaded = 0
        for (item in candidates.values) {
            if (state.downloads.isDownloaded(item.id)) continue
            if (state.downloads.totalBytes() >= limitBytes) break
            if (state.downloads.downloadNow(api, item)) {
                downloaded++
            }
            if (downloaded >= 100) break
        }

        return Result.success()
    }

    private fun isMetered(): Boolean {
        val manager = applicationContext.getSystemService(ConnectivityManager::class.java)
            ?: return false
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}

object AutoDownloadScheduler {
    private const val PERIODIC_WORK = "auto_download"
    private const val ONE_TIME_WORK = "auto_download_once"

    fun schedule(context: Context) {
        runCatching {
            val request = PeriodicWorkRequestBuilder<AutoDownloadWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }

    fun runNow(context: Context) {
        runCatching {
            val request = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}