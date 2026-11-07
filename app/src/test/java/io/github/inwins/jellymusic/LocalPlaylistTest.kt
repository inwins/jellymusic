package io.github.inwins.jellymusic

import io.github.inwins.jellymusic.data.LocalPlaylist
import io.github.inwins.jellymusic.jellyfin.BaseItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalPlaylistTest {
    private fun item(id: String) = BaseItem(
        id = id,
        name = id,
        type = "Audio",
        isFolder = false,
    )

    @Test
    fun playlistWithoutServerIdIsPending() {
        val playlist = LocalPlaylist(id = "1", name = "Mix")
        assertTrue(playlist.pendingSync)
    }

    @Test
    fun fullySyncedPlaylistIsNotPending() {
        val a = item("a")
        val playlist = LocalPlaylist(
            id = "1",
            name = "Mix",
            serverId = "server",
            items = mutableListOf(a),
            syncedItemIds = setOf("a"),
        )
        assertFalse(playlist.pendingSync)
    }

    @Test
    fun addedItemMakesPlaylistPending() {
        val a = item("a")
        val b = item("b")
        val playlist = LocalPlaylist(
            id = "1",
            name = "Mix",
            serverId = "server",
            items = mutableListOf(a, b),
            syncedItemIds = setOf("a"),
        )
        assertTrue(playlist.pendingSync)
    }

    @Test
    fun removedItemMakesPlaylistPending() {
        val a = item("a")
        val b = item("b")
        val playlist = LocalPlaylist(
            id = "1",
            name = "Mix",
            serverId = "server",
            items = mutableListOf(b),
            syncedItemIds = setOf("a", "b"),
        )
        playlist.removedItemIds = setOf("a")
        assertTrue(playlist.pendingSync)
    }

    @Test
    fun renameMakesPlaylistPending() {
        val a = item("a")
        val playlist = LocalPlaylist(
            id = "1",
            name = "Mix",
            serverId = "server",
            items = mutableListOf(a),
            syncedItemIds = setOf("a"),
        )
        playlist.needsRename = true
        assertTrue(playlist.pendingSync)
    }
}
