package io.github.inwins.jellymusic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.inwins.jellymusic.data.AppSettings
import io.github.inwins.jellymusic.data.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppSettingsTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("jellymusic_settings", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun defaults() {
        val settings = AppSettings(context)
        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertTrue(settings.dynamicColor)
        assertEquals(512, settings.cacheLimitMb)
        assertTrue(settings.streamCacheEnabled)
        assertFalse(settings.manualOffline)
        assertTrue(settings.fadeEnabled)
        assertTrue(settings.rememberPosition)
        assertEquals(1f, settings.playbackSpeed, 0f)
    }

    @Test
    fun valuesPersistAcrossInstances() {
        val settings = AppSettings(context)
        settings.themeMode = ThemeMode.DARK
        settings.dynamicColor = false
        settings.manualOffline = true
        settings.cacheLimitMb = 2048
        settings.fadeMs = 750
        settings.playbackSpeed = 1.5f

        val restored = AppSettings(context)
        assertEquals(ThemeMode.DARK, restored.themeMode)
        assertFalse(restored.dynamicColor)
        assertTrue(restored.manualOffline)
        assertEquals(2048, restored.cacheLimitMb)
        assertEquals(750, restored.fadeMs)
        assertEquals(1.5f, restored.playbackSpeed, 0f)
    }

    @Test
    fun invalidThemeFallsBackToSystem() {
        context.getSharedPreferences("jellymusic_settings", Context.MODE_PRIVATE)
            .edit().putString("theme_mode", "NOT_A_THEME").commit()
        assertEquals(ThemeMode.SYSTEM, AppSettings(context).themeMode)
    }
}
