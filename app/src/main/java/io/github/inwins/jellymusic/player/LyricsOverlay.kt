package io.github.inwins.jellymusic.player

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.inwins.jellymusic.data.AppSettings
import io.github.inwins.jellymusic.jellyfin.JellyfinApi
import io.github.inwins.jellymusic.jellyfin.LyricLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LyricsOverlay(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    var enabled by mutableStateOf(false)
        private set

    private var windowManager: WindowManager? = null
    private var root: View? = null
    private var currentView: TextView? = null
    private var nextView: TextView? = null
    private var job: Job? = null

    fun toggle(player: MusicPlayer, settings: AppSettings, api: () -> JellyfinApi?) {
        if (enabled) hide() else show(player, settings, api)
    }

    fun show(player: MusicPlayer, settings: AppSettings, api: () -> JellyfinApi?) {
        if (enabled || root != null) return
        if (!Settings.canDrawOverlays(context)) return

        val manager = context.getSystemService(WindowManager::class.java) ?: return
        windowManager = manager

        val density = context.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(6), dp(16), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.argb(175, 0, 0, 0))
            }
        }

        val close = TextView(context).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 18f
            alpha = 0.75f
            setPadding(dp(8), 0, dp(4), 0)
            setOnClickListener { hide() }
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(close)
        }

        val current = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 20f * settings.lyricsFontScale
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
            text = "♪"
        }
        val next = TextView(context).apply {
            setTextColor(Color.argb(190, 255, 255, 255))
            textSize = 14f * settings.lyricsFontScale
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
        }

        container.addView(header)
        container.addView(current)
        container.addView(next)

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(160)
            x = 0
        }

        var initialY = 0
        var touchY = 0f
        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialY = layoutParams.y
                    touchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    layoutParams.y = (initialY + (event.rawY - touchY)).toInt()
                    runCatching { manager.updateViewLayout(container, layoutParams) }
                    true
                }

                else -> false
            }
        }

        runCatching {
            manager.addView(container, layoutParams)
            root = container
            currentView = current
            nextView = next
            enabled = true
            startUpdater(player, settings, api)
        }
    }

    private fun startUpdater(
        player: MusicPlayer,
        settings: AppSettings,
        api: () -> JellyfinApi?,
    ) {
        job?.cancel()
        job = scope.launch {
            var lastItemId: String? = null
            var lyrics: List<LyricLine> = emptyList()
            while (isActive && enabled) {
                val item = player.current
                if (item != null && item.id != lastItemId) {
                    lastItemId = item.id
                    lyrics = runCatching { api()?.getLyrics(item.id) }.getOrNull() ?: emptyList()
                }
                val position = player.positionMs + settings.lyricsOffsetMs
                val currentIndex = lyrics.indexOfLast { line ->
                    val start = line.startMs
                    start != null && start <= position
                }
                currentView?.text = lyrics.getOrNull(currentIndex)?.text ?: "♪"
                nextView?.text = lyrics.getOrNull(currentIndex + 1)?.text.orEmpty()
                delay(300)
            }
        }
    }

    fun hide() {
        job?.cancel()
        job = null
        enabled = false
        runCatching { root?.let { windowManager?.removeView(it) } }
        root = null
        currentView = null
        nextView = null
        windowManager = null
    }
}