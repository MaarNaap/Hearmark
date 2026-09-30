package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.player.AudioPlayerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerSettingsPersistenceTest {

    private lateinit var context: Context
    private lateinit var repository: AppRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("app_settings", Context.MODE_PRIVATE).edit().clear().commit()
        val db = AppDatabase.getDatabase(context)
        repository = AppRepository(db.appDao())
    }

    @Test
    fun appNameStringResource_isHearmark() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Hearmark", appName)
    }

    @Test
    fun subtitleFontSize_persistsAcrossInitCalls() {
        AudioPlayerManager.init(context, repository)
        assertEquals(16f, AudioPlayerManager.subtitleFontSize.value, 0.01f)

        AudioPlayerManager.setSubtitleFontSize(24f)
        assertEquals(24f, AudioPlayerManager.subtitleFontSize.value, 0.01f)

        AudioPlayerManager.subtitleFontSize.value = 16f
        AudioPlayerManager.init(context, repository)
        assertEquals(24f, AudioPlayerManager.subtitleFontSize.value, 0.01f)
    }

    @Test
    fun playbackSpeedAndAutoPlay_persistAcrossInitCalls() {
        AudioPlayerManager.init(context, repository)
        AudioPlayerManager.setSpeed(1.75f)
        AudioPlayerManager.setAutoPlay(false)

        AudioPlayerManager._playbackSpeed.value = 1.0f
        AudioPlayerManager.isAutoPlayEnabled.value = true

        AudioPlayerManager.init(context, repository)
        assertEquals(1.75f, AudioPlayerManager.playbackSpeed.value, 0.01f)
        assertFalse(AudioPlayerManager.isAutoPlayEnabled.value)
    }

    @Test
    fun headsetSettings_persistAcrossInitCalls() {
        AudioPlayerManager.init(context, repository)
        AudioPlayerManager.setHeadsetSettings(enabled = false, action = "SKIP_SECONDS")

        AudioPlayerManager.isHeadsetControlsEnabled.value = true
        AudioPlayerManager.headsetMultiClickAction.value = "NEXT_PREV"

        AudioPlayerManager.init(context, repository)
        assertFalse(AudioPlayerManager.isHeadsetControlsEnabled.value)
        assertEquals("SKIP_SECONDS", AudioPlayerManager.headsetMultiClickAction.value)
    }
}
