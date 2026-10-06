package com.example

import com.example.player.VoiceBoostController
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceBoostControllerTest {

    @Test
    fun levelZero_isOff() {
        assertEquals(0, VoiceBoostController.levelToGainMb(0))
        assertEquals(0, VoiceBoostController.levelToDb(0))
    }

    @Test
    fun eachLevelAddsThreeDb() {
        assertEquals(300, VoiceBoostController.levelToGainMb(1))
        assertEquals(3, VoiceBoostController.levelToDb(1))
        assertEquals(9, VoiceBoostController.levelToDb(3))
    }

    @Test
    fun levelsAreClampedToTheSafeRange() {
        assertEquals(0, VoiceBoostController.levelToGainMb(-4))
        val max = VoiceBoostController.levelToGainMb(VoiceBoostController.MAX_LEVEL)
        assertEquals(1500, max)
        assertEquals(max, VoiceBoostController.levelToGainMb(99))
    }

    @Test
    fun settingLevelWithNoPlayer_doesNothingAndDoesNotCrash() {
        VoiceBoostController.attach(null)
        VoiceBoostController.setLevel(3)
        VoiceBoostController.setLevel(0)
    }
}
