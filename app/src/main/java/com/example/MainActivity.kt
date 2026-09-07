package com.example

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.player.AudioPlayerManager
import com.example.ui.AppNavigationContainer
import com.example.ui.AppViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
  private lateinit var viewModel: AppViewModel

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    
    viewModel = ViewModelProvider(this)[AppViewModel::class.java]
    
    handleIntent(intent)
    
    if (android.os.Build.VERSION.SDK_INT >= 33) {
      requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
    }
    
    enableEdgeToEdge()
    setContent {
      val isDarkTheme = when (viewModel.selectedTheme) {
        "light" -> false
        "dark" -> true
        else -> isSystemInDarkTheme()
      }
      MyApplicationTheme(darkTheme = isDarkTheme) {
        Surface(modifier = Modifier.fillMaxSize()) {
          AppNavigationContainer(viewModel = viewModel)
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  override fun onPictureInPictureModeChanged(
    isInPictureInPictureMode: Boolean,
    newConfig: android.content.res.Configuration
  ) {
    super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    AudioPlayerManager.setInPipMode(isInPictureInPictureMode)
  }

  override fun onUserLeaveHint() {
    super.onUserLeaveHint()
    if (AudioPlayerManager.isVideoTrack.value && AudioPlayerManager.isPlaying.value) {
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        try {
          AudioPlayerManager.setInPipMode(true)
          val videoDims = AudioPlayerManager.videoDimensions.value
          val ratW = if (videoDims != null && videoDims.first > 0) videoDims.first.coerceIn(1, 10000) else 16
          val ratH = if (videoDims != null && videoDims.second > 0) videoDims.second.coerceIn(1, 10000) else 9
          val params = android.app.PictureInPictureParams.Builder()
            .setAspectRatio(android.util.Rational(ratW, ratH))
            .build()
          enterPictureInPictureMode(params)
        } catch (e: Exception) {
          // ignore
        }
      }
    }
  }

  private fun handleIntent(intent: Intent?) {
    val openTaskId = intent?.getLongExtra("OPEN_TASK_ID", -1L) ?: -1L
    if (openTaskId != -1L) {
      viewModel.setPendingOpenTaskId(openTaskId)
    }
  }

  override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
    if (event != null && AudioPlayerManager.handleMediaKeyEvent(event)) {
      return true
    }
    return super.onKeyDown(keyCode, event)
  }

  override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
    if (event != null && AudioPlayerManager.handleMediaKeyEvent(event)) {
      return true
    }
    return super.onKeyUp(keyCode, event)
  }
}

