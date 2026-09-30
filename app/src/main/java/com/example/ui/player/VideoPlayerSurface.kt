package com.example.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.player.AudioPlayerManager

// Reusable Video Player View backed by TextureView with seamless Surface lifecycle
@Composable
fun VideoPlayerSurface(
    modifier: Modifier = Modifier
) {
    val isPlayingState by AudioPlayerManager.isPlaying.collectAsStateWithLifecycle()
    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                keepScreenOn = isPlayingState
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        AudioPlayerManager.attachSurface(Surface(surface))
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        AudioPlayerManager.attachSurface(null)
                        return true
                    }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
                if (isAvailable) {
                    surfaceTexture?.let { AudioPlayerManager.attachSurface(Surface(it)) }
                }
            }
        },
        update = { view ->
            view.keepScreenOn = isPlayingState
            if (view.isAvailable) {
                view.surfaceTexture?.let { AudioPlayerManager.attachSurface(Surface(it)) }
            }
        },
        modifier = modifier
    )
}

@Composable
fun VideoScreenAndImmersiveEffects(
    isTrackVideo: Boolean,
    isPlayingState: Boolean,
    isFullScreenVideo: Boolean,
    isHorizontalVideo: Boolean,
    isInPipModeState: Boolean
) {
    val context = LocalContext.current

    // Keep screen ON only while video is active, playing, and visible on screen (both normal and fullscreen mode)
    DisposableEffect(isTrackVideo, isPlayingState) {
        val activity = context as? Activity
        val window = activity?.window
        if (isTrackVideo && isPlayingState) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto rotate screen and enable full immersive mode (hide system bars and navigation bar) on full screen
    DisposableEffect(isFullScreenVideo, isHorizontalVideo, isTrackVideo, isInPipModeState) {
        val activity = context as? Activity
        val window = activity?.window
        val insetsController = if (window != null) {
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        } else {
            null
        }

        if (isInPipModeState || (isFullScreenVideo && isTrackVideo)) {
            if (isHorizontalVideo) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }

            // Hide status bar and navigation buttons for 100% immersive video view
            insetsController?.apply {
                hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            // Restore status bar and navigation buttons
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            insetsController?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }
}
