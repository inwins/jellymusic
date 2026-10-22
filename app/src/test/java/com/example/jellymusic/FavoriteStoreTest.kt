package com.example.jellymusic

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.jellymusic.data.FavoriteStore
import com.example.jellymusic.jellyfin.BaseItem
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
class FavoriteStoreTest {
    private lateinit var context: Context

    private fun item(id: String, favorite: Boolean = false) = BaseItem(
        id = id,
        name = "Song $id",
        type = "Audio",
        isFolder = false,
        isFavorite = favorite,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("jellymusic_favorites", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun fallsBackToServerFavorite() {
        val store = FavoriteStore(context)
        assertTrue(store.isFavorite(item("a", favorite = true)))
        assertFalse(store.isFavorite(item("b", favorite = false)))
    }

    @Test
    fun toggleOverridesServerValue() {
        val store = FavoriteStore(context)
        val song = item("a", favorite = false)

        assertTrue(store.toggle(song))
        assertTrue(store.isFavorite(song))
        assertTrue(store.localItems.containsKey("a"))
        assertEquals(1, store.pendingCount())

        assertFalse(store.toggle(song))
        assertFalse(store.isFavorite(song))
        assertFalse(store.localItems.containsKey("a"))
        assertEquals(1, store.pendingCount())
    }

    @Test
    fun mergeFavoritesRemovesUnfavoritedAndKeepsLocalOnly() {
        val store = FavoriteStore(context)
        val a = item("a", favorite = true)
        val b = item("b", favorite = true)
        val localOnly = item("c", favorite = false)
        store.toggle(a)
        store.toggle(localOnly)

        val merged = store.mergeFavorites(listOf(a, b))
        assertEquals(listOf("b", "c"), merged.map { it.id })
    }

    @Test
    fun overridesPersistAcrossInstances() {
        FavoriteStore(context).toggle(item("a"))

        val restored = FavoriteStore(context)
        assertTrue(restored.isFavorite(item("a")))
        assertEquals(1, restored.pendingCount())
    }
}
