package io.github.inwins.jellymusic

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import io.github.inwins.jellymusic.jellyfin.BaseItem
import io.github.inwins.jellymusic.ui.TrackRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h640dp", application = Application::class)
class TrackRowTest {
    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun song() = BaseItem(
        id = "song-1",
        name = "Song",
        type = "Audio",
        isFolder = false,
        album = "Album",
        container = "mp3",
    )

    private fun content(block: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            MaterialTheme { block() }
        }
    }

    @Test
    fun cachedShowsTapToDownloadIcon() {
        content {
            TrackRow(
                item = song(),
                index = 1,
                artworkUrl = null,
                onClick = {},
                cached = true,
                onDownload = {},
            )
        }
        compose.onNodeWithContentDescription(
            context.getString(R.string.cached_tap_to_download),
        ).assertExists()
    }

    @Test
    fun downloadedTakesPrecedenceOverCached() {
        content {
            TrackRow(
                item = song(),
                index = 1,
                artworkUrl = null,
                onClick = {},
                downloaded = true,
                cached = true,
                onDownload = {},
            )
        }
        compose.onNodeWithContentDescription(context.getString(R.string.downloaded)).assertExists()
    }

    @Test
    fun cachedDownloadFailureShowsErrorInsteadOfCachedIcon() {
        content {
            TrackRow(
                item = song(),
                index = 1,
                artworkUrl = null,
                onClick = {},
                cached = true,
                downloadError = true,
                onDownload = {},
            )
        }
        compose.onNodeWithContentDescription(context.getString(R.string.download_error)).assertExists()
        compose.onNodeWithContentDescription(
            context.getString(R.string.cached_tap_to_download),
        ).assertDoesNotExist()
    }

    @Test
    fun tappingCachedIconStartsDownload() {
        var clicks = 0
        content {
            TrackRow(
                item = song(),
                index = 1,
                artworkUrl = null,
                onClick = {},
                cached = true,
                onDownload = { clicks++ },
            )
        }
        compose.onNodeWithContentDescription(
            context.getString(R.string.cached_tap_to_download),
        ).performClick()
        assertEquals(1, clicks)
    }
}
