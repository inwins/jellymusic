package com.example.jellymusic

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.jellymusic.jellyfin.BaseItem
import com.example.jellymusic.ui.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackRowInstrumentedTest {
    @get:Rule
    val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun song() = BaseItem(
        id = "song-1",
        name = "Song",
        type = "Audio",
        isFolder = false,
    )

    @Test
    fun cachedIconIsShownAndClickable() {
        var clicks = 0
        compose.setContent {
            MaterialTheme {
                TrackRow(
                    item = song(),
                    index = 1,
                    artworkUrl = null,
                    onClick = {},
                    cached = true,
                    onDownload = { clicks++ },
                )
            }
        }
        val description = context.getString(R.string.cached_tap_to_download)
        compose.onNodeWithContentDescription(description).assertExists()
        compose.onNodeWithContentDescription(description).performClick()
        assertEquals(1, clicks)
    }
}
