package com.example.jellymusic.data

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf

class AudioAnalysisWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = AppSettings(applicationContext)
        val local = LocalMusicStore(applicationContext)
        val downloads = DownloadStore(applicationContext, settings)
        val store = AudioFeaturesStore(applicationContext)

        val targets = (local.songs + downloads.downloadedItems())
            .distinctBy { it.id }
            .filter { store.get(it.id) == null }

        var done = 0
        for (item in targets) {
            if (isStopped) break
            val uri = item.localUri?.let { Uri.parse(it) } ?: downloads.uriFor(item.id)
            if (uri != null && store.analyzeAndStore(item, uri)) done++
            setProgress(workDataOf("done" to done, "total" to targets.size))
        }
        return Result.success(workDataOf("done" to done, "total" to targets.size))
    }
}

object AudioAnalysisScheduler {
    private const val NAME = "audio-analysis"

    fun enqueue(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NAME,
            androidx.work.ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<AudioAnalysisWorker>().build(),
        )
    }
}
