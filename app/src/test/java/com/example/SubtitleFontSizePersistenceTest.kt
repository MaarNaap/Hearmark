package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.player.AudioPlayerManager
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SubtitleFontSizePersistenceTest {

    @Test
    fun testSubtitleFontSizeSavedAndRestored() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = AppRepository(inMemoryDb.appDao(), inMemoryDb.vocabularyItemDao())

        // Set to 18f
        AudioPlayerManager.init(context, repo)
        AudioPlayerManager.setSubtitleFontSize(18f)

        assertEquals(18f, AudioPlayerManager.subtitleFontSize.value)

        // Verify it was persisted in SharedPreferences
        val sharedPref = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        assertEquals(18f, sharedPref.getFloat("subtitle_font_size", 16f))

        // Reset memory value and re-init to simulate app restart
        AudioPlayerManager.subtitleFontSize.value = 16f
        AudioPlayerManager.init(context, repo)

        // Should be restored to 18f
        assertEquals(18f, AudioPlayerManager.subtitleFontSize.value)

        inMemoryDb.close()
    }
}
