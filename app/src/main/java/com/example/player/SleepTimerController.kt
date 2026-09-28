package com.example.player

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Sleep Timer management
fun AudioPlayerManager.startSleepTimer(minutes: Int) {
    cancelSleepTimer()
    sleepTimerOption = minutes
    if (minutes <= 0) {
        _sleepTimeRemaining.value = 0
        return
    }

    _sleepTimeRemaining.value = minutes * 60
    sleepTimerJob = coroutineScope.launch {
        while (_sleepTimeRemaining.value > 0) {
            delay(1000)
            _sleepTimeRemaining.value -= 1
        }
        // Timer finished, pause playback
        pause()
        sleepTimerOption = 0
    }
}

fun AudioPlayerManager.setSleepAtEnd() {
    cancelSleepTimer()
    sleepTimerOption = -1 // end of file code
    _sleepTimeRemaining.value = -1
}

fun AudioPlayerManager.cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    sleepTimerOption = 0
    _sleepTimeRemaining.value = 0
}
