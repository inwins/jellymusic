package com.example.jellymusic

import com.example.jellymusic.player.PlayMode
import com.example.jellymusic.player.nextPlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayModeTest {
    @Test
    fun cyclesThroughAllModes() {
        assertEquals(PlayMode.LIST_LOOP, nextPlayMode(PlayMode.SEQUENTIAL))
        assertEquals(PlayMode.SINGLE_LOOP, nextPlayMode(PlayMode.LIST_LOOP))
        assertEquals(PlayMode.SHUFFLE, nextPlayMode(PlayMode.SINGLE_LOOP))
        assertEquals(PlayMode.SEQUENTIAL, nextPlayMode(PlayMode.SHUFFLE))
    }
}
