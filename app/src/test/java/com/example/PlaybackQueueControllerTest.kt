package com.example

import com.example.data.AudioTrack
import com.example.player.AudioPlayerManager
import com.example.player.PlaybackQueueController
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackQueueControllerTest {

    private val track1 = AudioTrack(id = 1L, filePath = "/a/1.mp3", fileName = "1.mp3", duration = 10000L)
    private val track2 = AudioTrack(id = 2L, filePath = "/a/2.mp3", fileName = "2.mp3", duration = 20000L)
    private val track3 = AudioTrack(id = 3L, filePath = "/a/3.mp3", fileName = "3.mp3", duration = 30000L)
    private val track4 = AudioTrack(id = 4L, filePath = "/a/4.mp3", fileName = "4.mp3", duration = 40000L)

    @Before
    fun setUp() {
        AudioPlayerManager.appContext = null
        AudioPlayerManager.currentTrackValue = track2
        AudioPlayerManager.currentQueue = listOf(track1, track2, track3)
    }

    @Test
    fun removeTracksFromQueue_removesSpecifiedIndices() {
        PlaybackQueueController.removeTracksFromQueue(listOf(0, 2))
        assertEquals(listOf(track2), AudioPlayerManager.currentQueue)
    }

    @Test
    fun removeTracksByIds_removesMatchingTrackIds() {
        PlaybackQueueController.removeTracksByIds(setOf(1L, 3L))
        assertEquals(listOf(track2), AudioPlayerManager.currentQueue)
    }

    @Test
    fun clearQueue_retainsOnlyCurrentActiveTrack() {
        PlaybackQueueController.clearQueue()
        assertEquals(listOf(track2), AudioPlayerManager.currentQueue)

        AudioPlayerManager.currentTrackValue = null
        PlaybackQueueController.clearQueue()
        assertTrue(AudioPlayerManager.currentQueue.isEmpty())
    }

    @Test
    fun addTrackToQueueNext_insertsImmediatelyAfterCurrentAndDeduplicates() {
        // Insert new track4 right after currentTrack (track2)
        PlaybackQueueController.addTrackToQueueNext(track4)
        assertEquals(listOf(track1, track2, track4, track3), AudioPlayerManager.currentQueue)

        // Re-adding track3 as play-next moves it right after track2 without duplicating
        PlaybackQueueController.addTrackToQueueNext(track3)
        assertEquals(listOf(track1, track2, track3, track4), AudioPlayerManager.currentQueue)
    }

    @Test
    fun addTracksToQueueNext_insertsBatchImmediatelyAfterCurrent() {
        AudioPlayerManager.currentQueue = listOf(track1, track2)
        PlaybackQueueController.addTracksToQueueNext(listOf(track3, track4))
        assertEquals(listOf(track1, track2, track3, track4), AudioPlayerManager.currentQueue)
    }
}
