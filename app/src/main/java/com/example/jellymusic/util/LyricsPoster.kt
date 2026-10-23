package com.example.jellymusic.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/** Renders a shareable lyric poster (cover + title/artist + a lyric line). */
object LyricsPoster {
    suspend fun generate(
        context: Context,
        coverUrl: String?,
        title: String,
        artist: String,
        lyric: String,
        accent: Int,
    ): File? = withContext(Dispatchers.IO) {
        runCatching {
            val width = 1080
            val height = 1350
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val background = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                shader = LinearGradient(
                    0f, 0f, 0f, height.toFloat(),
                    accent, Color.BLACK, Shader.TileMode.CLAMP,
                )
            }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), background)

            val cover = loadBitmap(context, coverUrl)
            if (cover != null) {
                val size = 620f
                val left = (width - size) / 2f
                val top = 120f
                val rect = RectF(left, top, left + size, top + size)
                val path = Path().apply { addRoundRect(rect, 32f, 32f, Path.Direction.CW) }
                canvas.save()
                canvas.clipPath(path)
                canvas.drawBitmap(cover, null, rect, Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            }

            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 58f
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(title.take(20), width / 2f, 840f, titlePaint)

            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xCCFFFFFF.toInt()
                textSize = 40f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(artist.take(30), width / 2f, 902f, artistPaint)

            val lyricPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 50f
                textAlign = Paint.Align.CENTER
            }
            var y = 1030f
            wrap(lyric, 22).take(4).forEach { line ->
                canvas.drawText(line, width / 2f, y, lyricPaint)
                y += 74f
            }

            val dir = File(context.cacheDir, "posters").apply { mkdirs() }
            val file = File(dir, "poster-${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            file
        }.getOrNull()
    }

    fun share(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    private suspend fun loadBitmap(context: Context, url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            val request = coil.request.ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = coil.Coil.imageLoader(context).execute(request)
            (result.drawable as? BitmapDrawable)?.bitmap
        }.getOrNull()
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        return text.chunked(maxChars)
    }
}
