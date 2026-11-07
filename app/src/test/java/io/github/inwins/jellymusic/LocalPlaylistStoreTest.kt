package io.github.inwins.jellymusic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.inwins.jellymusic.data.LocalPlaylistStore
import io.github.inwins.jellymusic.jellyfin.BaseItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalPlaylistStoreTest {
    private lateinit var context: Context

    private fun item(id: String) = BaseItem(
        id = id,
        name = "Song $id",
        type = "Audio",
        isFolder = false,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("jellymusic_playlists", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun createAddsPendingPlaylist() {
        val store = LocalPlaylistStore(context)
        val playlist = store.create("Mix")

        assertEquals(1, store.playlists.size)
        assertEquals("Mix", playlist.name)
        assertTrue(playlist.pendingSync)
    }

    @Test
    fun addItemRejectsDuplicates() {
        val store = LocalPlaylistStore(context)
        val playlist = store.create("Mix")

        assertTrue(store.addItem(playlist.id, item("a")))
        assertFalse(store.addItem(playlist.id, item("a")))
        assertEquals(1, playlist.items.size)
    }

    @Test
    fun removeItemMarksSyncedItemForRemoval() {
        val store = LocalPlaylistStore(context)
        val playlist = store.create("Mix")
        store.addItem(playlist.id, item("a"))
        playlist.syncedItemIds = setOf("a")

        store.removeItem(playlist.id, "a")

        assertTrue(playlist.items.isEmpty())
        assertEquals(setOf("a"), playlist.removedItemIds)
        assertTrue(playlist.pendingSync)
    }

    @Test
    fun renameIsNoOpForSameName() {
        val store = LocalPlaylistStore(context)
        val playlist = store.create("Mix")

        store.rename(playlist.id, "Mix")
        assertFalse(playlist.needsRename)

        store.rename(playlist.id, "New Mix")
        assertEquals("New Mix", playlist.name)
        assertTrue(playlist.needsRename)
    }

    @Test
    fun deleteLocalPlaylistLeavesNoPendingWork() {
        val store = LocalPlaylistStore(context)
        val playlist = store.create("Mix")
        store.delete(playlist.id)

        assertTrue(store.playlists.isEmpty())
        assertEquals(0, store.pendingCount())
    }

    @Test
    fun playlistsPersistAcrossInstances() {
        val store = LocalPlaylistStore(context)
        val playlist = store.create("Mix")
        store.addItem(playlist.id, item("a"))

        val restored = LocalPlaylistStore(context)
        val saved = restored.get(playlist.id)
        assertNotNull(saved)
        assertEquals("Mix", saved!!.name)
        assertEquals(listOf("a"), saved.items.map { it.id })
        assertTrue(saved.pendingSync)
    }
}
