package com.example.player

import android.util.Log
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal object PlaybackProgressEngine {

    // =========================================================================
    // @LOCKED: Continuous Segment BitSet, Wall-Clock Delta & Cross-Track Isolation - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    fun parseSegmentsIntoBitSet(rawSegments: String, targetBitSet: java.util.BitSet) {
        if (rawSegments.isEmpty()) return
        var current = 0
        var hasDigits = false
        for (i in 0 until rawSegments.length) {
            val ch = rawSegments[i]
            if (ch in '0'..'9') {
                current = current * 10 + (ch - '0')
                hasDigits = true
            } else if (ch == ',') {
                if (hasDigits && current in 0..999) {
                    targetBitSet.set(current)
                }
                current = 0
                hasDigits = false
            }
        }
        if (hasDigits && current in 0..999) {
            targetBitSet.set(current)
        }
    }

    @Synchronized
    fun getOrInitActiveBitSet(trackId: Long, rawSegments: String): java.util.BitSet {
        with(AudioPlayerManager) {
            if (activeTrackSegmentTrackId != trackId) {
                activeTrackSegmentTrackId = trackId
                activeTrackSegmentsBitSet.clear()
                parseSegmentsIntoBitSet(rawSegments, activeTrackSegmentsBitSet)
            } else if (rawSegments.isNotEmpty()) {
                // Merge any segments present in rawSegments so we never lose segments across async updates
                parseSegmentsIntoBitSet(rawSegments, activeTrackSegmentsBitSet)
            }
            return activeTrackSegmentsBitSet
        }
    }

    @Synchronized
    fun serializeBitSet(bitSet: java.util.BitSet): String {
        if (bitSet.isEmpty) return ""
        val sb = StringBuilder(bitSet.cardinality() * 4)
        var i = bitSet.nextSetBit(0)
        var first = true
        while (i >= 0) {
            if (!first) sb.append(',')
            sb.append(i)
            first = false
            i = bitSet.nextSetBit(i + 1)
        }
        return sb.toString()
    }

    @Synchronized
    fun accumulateActiveListeningTime() {
        with(AudioPlayerManager) {
            if (lastActivePlayTimestamp > 0L) {
                val now = System.currentTimeMillis()
                val delta = now - lastActivePlayTimestamp
                if (delta > 0L) {
                    sessionActualListeningMs += minOf(delta, 10000L)
                }
                lastActivePlayTimestamp = now
            }
        }
    }

    @Synchronized
    fun accumulatePracticePauseTime() {
        with(AudioPlayerManager) {
            if (lastPracticePauseTimestamp > 0L) {
                val now = System.currentTimeMillis()
                val delta = now - lastPracticePauseTimestamp
                if (delta > 0L) {
                    sessionActualListeningMs += minOf(delta, 10000L)
                }
                lastPracticePauseTimestamp = now
            }
        }
    }

    @Synchronized
    fun persistCurrentPlayListeningTime() {
        with(AudioPlayerManager) {
            accumulateActiveListeningTime()
            accumulatePracticePauseTime()
            val delta = sessionActualListeningMs
            val track = currentTrackValue ?: return
            if (delta <= 0L) return
            sessionActualListeningMs = 0L
            val prevTotal = trackAccumulatedListeningMsMap[track.id] ?: track.currentPlayActualListeningMs
            val newTotal = prevTotal + delta
            trackAccumulatedListeningMsMap[track.id] = newTotal

            val latestSegments = if (activeTrackSegmentTrackId == track.id && !activeTrackSegmentsBitSet.isEmpty) {
                serializeBitSet(activeTrackSegmentsBitSet)
            } else {
                track.listenedSegments
            }

            val updatedTrack = track.copy(
                currentPlayActualListeningMs = newTotal,
                listenedSegments = latestSegments
            )
            currentTrackValue = updatedTrack
            _currentTrack.value = updatedTrack

            val targetTrackId = track.id
            coroutineScope.launch(Dispatchers.IO) {
                if (currentTrackValue?.id == targetTrackId) {
                    updateTrackState { t ->
                        if (t.id == targetTrackId) t.copy(currentPlayActualListeningMs = newTotal) else t
                    }
                } else {
                    trackUpdateMutex.withLock {
                        val dbTrack = repository?.getTrackById(targetTrackId) ?: updatedTrack
                        repository?.updateTrack(dbTrack.copy(currentPlayActualListeningMs = newTotal))
                    }
                }
            }
        }
    }

    fun setLastTrackedPosition(posMs: Long?) {
        with(AudioPlayerManager) {
            lastTrackedPositionMs = posMs
            lastTrackedWallClockMs = if (posMs != null) android.os.SystemClock.elapsedRealtime() else 0L
        }
    }

    fun computeMaxContinuousDeltaMs(nowWallMs: Long = android.os.SystemClock.elapsedRealtime()): Long {
        with(AudioPlayerManager) {
            val prevWall = lastTrackedWallClockMs
            val elapsedWall = if (prevWall > 0L) (nowWallMs - prevWall).coerceAtLeast(0L) else 250L
            val speed = _playbackSpeed.value.coerceAtLeast(1.0f)
            return maxOf(2500L, (elapsedWall * speed * 1.5f).toLong() + 2000L)
        }
    }

    fun flushContinuousSegmentsOnEvent() {
        with(AudioPlayerManager) {
            if (isSeeking) return
            val mp = mediaPlayer ?: return
            val track = currentTrackValue ?: return
            val physicalPos = try {
                if (mp.isPlaying) mp.currentPosition.toLong() else -1L
            } catch (e: Exception) {
                -1L
            }
            if (physicalPos < 0L) return

            val effectiveDuration = if (track.isVirtualScene) {
                track.duration
            } else {
                val mpDur = try { mp.duration.toLong() } catch (e: Exception) { 0L }
                if (mpDur > 0) mpDur else track.duration
            }
            if (effectiveDuration <= 0L) return

            val numSegments = track.getAdaptiveNumSegments()
            if (numSegments <= 0) return

            val prevPhys = lastTrackedPositionMs ?: physicalPos
            val nowWall = android.os.SystemClock.elapsedRealtime()
            val maxContinuousDelta = computeMaxContinuousDeltaMs(nowWall)

            val currentVirtualOrPhys = if (track.isVirtualScene) {
                (physicalPos - track.startOffsetMs).coerceIn(0L, effectiveDuration)
            } else {
                physicalPos.coerceIn(0L, effectiveDuration)
            }
            val prevVirtualOrPhys = if (track.isVirtualScene) {
                (prevPhys - track.startOffsetMs).coerceIn(0L, effectiveDuration)
            } else {
                prevPhys.coerceIn(0L, effectiveDuration)
            }

            val deltaPos = currentVirtualOrPhys - prevVirtualOrPhys
            val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
            var hadNewSegments = false
            if (deltaPos in 0L..maxContinuousDelta) {
                val fromSeg = ((prevVirtualOrPhys * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                val toSeg = ((currentVirtualOrPhys * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                for (s in fromSeg..toSeg) {
                    if (!bitSet.get(s)) {
                        bitSet.set(s)
                        hadNewSegments = true
                    }
                }
            }
            setLastTrackedPosition(physicalPos)
            _currentPosition.value = currentVirtualOrPhys
            if (hadNewSegments) {
                val serialized = serializeBitSet(bitSet)
                val updated = track.copy(lastPosition = currentVirtualOrPhys, listenedSegments = serialized)
                currentTrackValue = updated
                _currentTrack.value = updated
                coroutineScope.launch(Dispatchers.IO) {
                    updateTrackState { t ->
                        if (t.id == track.id) t.copy(lastPosition = currentVirtualOrPhys, listenedSegments = serialized) else t
                    }
                }
            }
        }
    }

    fun flushContinuousSegmentsBeforeCompletion() {
        with(AudioPlayerManager) {
            if (isSeeking) return
            val track = currentTrackValue ?: return
            val effectiveDuration = if (track.isVirtualScene) {
                track.duration
            } else {
                val mpDur = try { mediaPlayer?.duration?.toLong() ?: 0L } catch (e: Exception) { 0L }
                if (mpDur > 0) mpDur else (track.duration.takeIf { it > 0 } ?: _duration.value)
            }
            if (effectiveDuration <= 0L) return

            val numSegments = track.getAdaptiveNumSegments()
            if (numSegments <= 0) return

            val lastPos = lastTrackedPositionMs ?: return
            val prevEffectivePos = if (track.isVirtualScene) {
                (lastPos - track.startOffsetMs).coerceIn(0L, effectiveDuration)
            } else {
                lastPos.coerceIn(0L, effectiveDuration)
            }

            val nowWall = android.os.SystemClock.elapsedRealtime()
            val maxContinuousDelta = computeMaxContinuousDeltaMs(nowWall)
            val remainingToEnd = effectiveDuration - prevEffectivePos

            if (remainingToEnd in 0L..maxContinuousDelta) {
                val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                val fromSeg = ((prevEffectivePos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                val finalSeg = numSegments - 1
                var hadNewSegments = false
                for (s in fromSeg..finalSeg) {
                    if (!bitSet.get(s)) {
                        bitSet.set(s)
                        hadNewSegments = true
                    }
                }
                val endPhysicalPos = if (track.isVirtualScene) (track.startOffsetMs + effectiveDuration) else effectiveDuration
                setLastTrackedPosition(endPhysicalPos)
                if (hadNewSegments) {
                    val serialized = serializeBitSet(bitSet)
                    val updated = track.copy(listenedSegments = serialized)
                    currentTrackValue = updated
                    _currentTrack.value = updated
                }
            }
        }
    }

    suspend fun updateTrackState(transform: (AudioTrack) -> AudioTrack) {
        with(AudioPlayerManager) {
            trackUpdateMutex.withLock {
                val track = currentTrackValue ?: return@withLock
                var updated = transform(track)
                if (updated.id != track.id) return@withLock
                if (activeTrackSegmentTrackId == updated.id && !activeTrackSegmentsBitSet.isEmpty && updated.listenedSegments.isNotEmpty()) {
                    val mergedBitSet = getOrInitActiveBitSet(updated.id, updated.listenedSegments)
                    val mergedSerialized = serializeBitSet(mergedBitSet)
                    if (updated.listenedSegments != mergedSerialized) {
                        updated = updated.copy(listenedSegments = mergedSerialized)
                    }
                }
                if (updated != track) {
                    repository?.updateTrack(updated)
                    if (currentTrackValue?.id == updated.id) {
                        currentTrackValue = updated
                        _currentTrack.value = updated
                    }
                    val index = currentQueue.indexOfFirst { it.id == updated.id }
                    if (index != -1 && currentQueue[index] != updated) {
                        withContext(Dispatchers.Main) {
                            val qIdx = currentQueue.indexOfFirst { it.id == updated.id }
                            if (qIdx != -1) {
                                val updatedQueue = currentQueue.toMutableList()
                                updatedQueue[qIdx] = updated
                                currentQueue = updatedQueue
                            }
                        }
                    }
                }
            }
        }
    }

    // Dynamic continuous segments tracker maintaining unbroken, high-fidelity updates
    fun startProgressTracking() {
        with(AudioPlayerManager) {
            progressTrackingJob?.cancel()
            try {
                mediaPlayer?.let { mp ->
                    if (try { mp.isPlaying } catch (e: Exception) { false }) {
                        val curPos = try { mp.currentPosition.toLong() } catch (e: Exception) { null }
                        if (curPos != null) {
                            setLastTrackedPosition(curPos)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "startProgressTracking initial pos error: ${e.message}")
            }
            if (lastActivePlayTimestamp <= 0L) {
                lastActivePlayTimestamp = System.currentTimeMillis()
            }
            progressTrackingJob = coroutineScope.launch(Dispatchers.IO) {
                while (isActive) {
                    try {
                        val mp = mediaPlayer
                        val isPlaying = try { mp?.isPlaying == true } catch (e: Exception) { false }
                        if (mp != null && isPlaying) {
                            accumulateActiveListeningTime()
                            if (sessionActualListeningMs >= 3000L) {
                                persistCurrentPlayListeningTime()
                            }
                            val physicalPos = try { mp.currentPosition.toLong() } catch (e: Exception) { -1L }
                            if (physicalPos < 0L) {
                                delay(200)
                                continue
                            }

                            if (isSeeking) {
                                if (System.currentTimeMillis() - lastSeekTimestamp > 1200L) {
                                    isSeeking = false
                                } else {
                                    delay(100)
                                    continue
                                }
                            }

                            val track = currentTrackValue
                            val nowWall = android.os.SystemClock.elapsedRealtime()
                            val maxContinuousDelta = computeMaxContinuousDeltaMs(nowWall)

                            if (track != null && track.isVirtualScene) {
                                if (currentTrackValue?.id != track.id) {
                                    delay(200)
                                    continue
                                }
                                val virtualPos = (physicalPos - track.startOffsetMs).coerceIn(0L, track.duration)
                                _currentPosition.value = virtualPos
                                _duration.value = track.duration
                                updateActiveSubtitleCue(physicalPos)

                                // Process segment logic for virtual scene BEFORE checking end boundary or practice pause
                                val effectiveDuration = track.duration
                                if (effectiveDuration > 0) {
                                    val numSegments = track.getAdaptiveNumSegments()
                                    val prevPhysical = lastTrackedPositionMs ?: physicalPos
                                    val prevVirtual = (prevPhysical - track.startOffsetMs).coerceIn(0L, effectiveDuration)

                                    val currentSeg = ((virtualPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                    val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                                    var hadNewSegments = false

                                    val deltaPos = virtualPos - prevVirtual
                                    if (!isSeeking && deltaPos in 0L..maxContinuousDelta) {
                                        val fromSeg = ((prevVirtual * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                        for (s in fromSeg..currentSeg) {
                                            if (!bitSet.get(s)) {
                                                bitSet.set(s)
                                                hadNewSegments = true
                                            }
                                        }
                                    }
                                    setLastTrackedPosition(physicalPos)

                                    if (hadNewSegments || Math.abs(track.lastPosition - virtualPos) >= 2000L) {
                                        val serialized = serializeBitSet(bitSet)
                                        updateTrackState { t ->
                                            if (t.id == track.id) {
                                                t.copy(
                                                    lastPosition = virtualPos,
                                                    listenedSegments = serialized
                                                )
                                            } else {
                                                t
                                            }
                                        }
                                    }

                                    val currentTrack = currentTrackValue?.takeIf { it.id == track.id }
                                    if (currentTrack != null) {
                                        val progressPercent = currentTrack.getProgressPercent(numSegments)
                                        val shouldTriggerThreshold = when {
                                            initialSessionProgressPercent >= 100 -> false
                                            initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
                                            else -> progressPercent >= completionThreshold
                                        }
                                        if (shouldTriggerThreshold) {
                                            handleThresholdReached()
                                        }
                                        checkAndTriggerTaskSpecificProgress(currentTrack, progressPercent)
                                    }
                                }

                                if (_isPracticeMode.value && !_isPracticePausing.value && isPlaying) {
                                    checkPracticeSegmentBoundary(virtualPos)
                                }
                                val endBoundary = track.endOffsetMs ?: (track.startOffsetMs + track.duration)
                                if (physicalPos >= endBoundary - 120L) {
                                    flushContinuousSegmentsBeforeCompletion()
                                    withContext(Dispatchers.Main) {
                                        try {
                                            mediaPlayer?.pause()
                                        } catch (e: Exception) {}
                                        handlePhysicalEndOfTrack()
                                    }
                                    return@launch
                                }
                            } else {
                                if (track != null && currentTrackValue?.id != track.id) {
                                    delay(200)
                                    continue
                                }
                                _currentPosition.value = physicalPos
                                updateActiveSubtitleCue(physicalPos)

                                // Process segment logic
                                val effectiveDuration = if (mp.duration > 0) mp.duration.toLong() else track?.duration ?: 0L
                                if (track != null && effectiveDuration > 0) {
                                    val numSegments = track.getAdaptiveNumSegments()
                                    val prevPos = lastTrackedPositionMs ?: physicalPos

                                    val currentSeg = ((physicalPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                    val bitSet = getOrInitActiveBitSet(track.id, track.listenedSegments)
                                    var hadNewSegments = false

                                    val deltaPos = physicalPos - prevPos
                                    if (!isSeeking && deltaPos in 0L..maxContinuousDelta) {
                                        val fromSeg = ((prevPos * numSegments) / effectiveDuration).toInt().coerceIn(0, numSegments - 1)
                                        for (s in fromSeg..currentSeg) {
                                            if (!bitSet.get(s)) {
                                                bitSet.set(s)
                                                hadNewSegments = true
                                            }
                                        }
                                    }
                                    setLastTrackedPosition(physicalPos)

                                    // Atomically update track state with new segments and lastPosition directly on IO
                                    if (hadNewSegments || Math.abs(track.lastPosition - physicalPos) >= 2000L) {
                                        val serialized = serializeBitSet(bitSet)
                                        updateTrackState { t ->
                                            if (t.id == track.id) {
                                                t.copy(
                                                    lastPosition = physicalPos,
                                                    listenedSegments = serialized
                                                )
                                            } else {
                                                t
                                            }
                                        }
                                    }

                                    val currentTrack = currentTrackValue?.takeIf { it.id == track.id }
                                    if (currentTrack != null) {
                                        val progressPercent = currentTrack.getProgressPercent(numSegments)
                                        val shouldTriggerThreshold = when {
                                            initialSessionProgressPercent >= 100 -> false
                                            initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
                                            else -> progressPercent >= completionThreshold
                                        }
                                        if (shouldTriggerThreshold) {
                                            handleThresholdReached()
                                        }
                                        checkAndTriggerTaskSpecificProgress(currentTrack, progressPercent)
                                    }
                                }

                                if (_isPracticeMode.value && !_isPracticePausing.value && isPlaying) {
                                    checkPracticeSegmentBoundary(physicalPos)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Progress tracking tick error: ${e.message}")
                    }
                    delay(200)
                }
            }
        }
    }

    fun stopProgressTracking() {
        with(AudioPlayerManager) {
            progressTrackingJob?.cancel()
            progressTrackingJob = null
        }
    }

    fun saveCurrentPositionProgress() {
        with(AudioPlayerManager) {
            val targetTrack = currentTrackValue ?: return
            val targetTrackId = targetTrack.id
            persistCurrentPlayListeningTime()
            val pos = _currentPosition.value
            val latestSync = currentTrackValue
            if (latestSync != null && latestSync.id == targetTrackId) {
                val updatedSync = latestSync.copy(lastPosition = pos)
                currentTrackValue = updatedSync
                _currentTrack.value = updatedSync
                val qIdx = currentQueue.indexOfFirst { it.id == targetTrackId }
                if (qIdx != -1) {
                    val updatedQueue = currentQueue.toMutableList()
                    updatedQueue[qIdx] = updatedSync
                    currentQueue = updatedQueue
                }
            }
            coroutineScope.launch(Dispatchers.IO) {
                if (currentTrackValue?.id == targetTrackId) {
                    updateTrackState { t ->
                        if (t.id == targetTrackId) t.copy(lastPosition = pos) else t
                    }
                } else {
                    trackUpdateMutex.withLock {
                        val dbTrack = repository?.getTrackById(targetTrackId) ?: targetTrack
                        repository?.updateTrack(dbTrack.copy(lastPosition = pos))
                    }
                }
            }
        }
    }

    suspend fun performFullProgressReset(track: AudioTrack, resetPosition: Boolean = true) {
        with(AudioPlayerManager) {
            activeTrackSegmentTrackId = null
            activeTrackSegmentsBitSet.clear()
            trackAccumulatedListeningMsMap[track.id] = 0L
            initialSessionProgressPercent = 0
            isThresholdTriggeredForCurrentSession = false

            updateTrackState { t ->
                if (t.id == track.id) {
                    if (resetPosition) {
                        t.copy(
                            lastPosition = 0L,
                            listenedSegments = "",
                            currentPlayActualListeningMs = 0L
                        )
                    } else {
                        t.copy(
                            listenedSegments = "",
                            currentPlayActualListeningMs = 0L
                        )
                    }
                } else {
                    t
                }
            }
        }
    }
    // =========================================================================
    // @END_LOCKED: Continuous Segment BitSet, Wall-Clock Delta & Cross-Track Isolation
    // =========================================================================

    // =========================================================================
    // @LOCKED: Play Count Increment & Threshold Handling - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    fun handleThresholdReached() {
        with(AudioPlayerManager) {
            if (isThresholdTriggeredForCurrentSession) return
            isThresholdTriggeredForCurrentSession = true

            coroutineScope.launch(Dispatchers.IO) {
                // 1. Increment playCount safely
                updateTrackState { t -> t.copy(playCount = t.playCount + 1) }

                val track = currentTrackValue ?: return@launch

                // 2. Sync / save into History with active tasks snapshot and exact actual listening & speaking time
                syncCurrentSessionHistory(isFinishing = false)

                // 3. Update all active tasks containing this file automatically
                updateAssociatedTasks(track.id)
            }
        }
    }
    // =========================================================================
    // @END_LOCKED: Play Count Increment & Threshold Handling
    // =========================================================================

    // =========================================================================
    // @LOCKED: End-of-File Lifecycle & Progress Reset - STRICT FREEZE
    // DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
    // =========================================================================
    fun handlePhysicalEndOfTrack() {
        with(AudioPlayerManager) {
            val track = currentTrackValue ?: return
            stopProgressTracking()
            coroutineScope.launch(Dispatchers.IO) {
                // First check if there is a sleep at end of file timer set
                val isSleepAtEnd = (sleepTimerOption == -1)
                if (isSleepAtEnd) {
                    sleepTimerOption = 0
                    _sleepTimeRemaining.value = 0
                }

                accumulateActiveListeningTime()
                persistCurrentPlayListeningTime()

                // If track was playing continuously to the physical end, mark ONLY the final segments (never a range across seeks)
                val effectiveDuration = if (track.isVirtualScene) track.duration else (track.duration.takeIf { it > 0 } ?: _duration.value)
                var currentTrack = currentTrackValue ?: track
                val numSegments = currentTrack.getAdaptiveNumSegments()
                val bitSet = getOrInitActiveBitSet(track.id, currentTrack.listenedSegments)

                if (effectiveDuration > 0 && numSegments > 0) {
                    val finalSeg = numSegments - 1
                    val lastPos = lastTrackedPositionMs
                    val effectiveLastPos = if (lastPos != null) {
                        if (track.isVirtualScene) (lastPos - track.startOffsetMs).coerceIn(0L, effectiveDuration) else lastPos.coerceIn(0L, effectiveDuration)
                    } else null

                    // Only mark the final segment(s) if the player was playing continuously near the physical end (within 2000ms of end)
                    if (effectiveLastPos != null && (effectiveDuration - effectiveLastPos) in 0L..2000L) {
                        val fromSeg = ((effectiveLastPos * numSegments) / effectiveDuration).toInt().coerceIn(0, finalSeg)
                        var hadNewEndSegments = false
                        for (s in fromSeg..finalSeg) {
                            if (!bitSet.get(s)) {
                                bitSet.set(s)
                                hadNewEndSegments = true
                            }
                        }
                        if (hadNewEndSegments) {
                            val serialized = serializeBitSet(bitSet)
                            val updated = currentTrack.copy(listenedSegments = serialized)
                            currentTrack = updated
                            updateTrackState { t ->
                                if (t.id == track.id) updated else t
                            }
                        }
                    }
                }

                // Compute progress BEFORE clearing activeTrackSegmentsBitSet (since bitSet references activeTrackSegmentsBitSet)
                val progressPercent = if (numSegments > 0) {
                    ((bitSet.cardinality() * 100) / numSegments).coerceIn(0, 100)
                } else {
                    currentTrack.getProgressPercent()
                }

                activeTrackSegmentTrackId = null
                activeTrackSegmentsBitSet.clear()

                // Recheck criteria: MUST reach completion threshold OR 100% progress!
                // Reaching the physical end of the file ALONE does NOT record history or trigger completion.
                val criteriaMet = (progressPercent >= completionThreshold) || (progressPercent >= 100)

                if (criteriaMet) {
                    val shouldTrigger = when {
                        initialSessionProgressPercent >= 100 -> false
                        initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
                        else -> progressPercent >= completionThreshold
                    }
                    if (shouldTrigger && !isThresholdTriggeredForCurrentSession) {
                        isThresholdTriggeredForCurrentSession = true
                        updateTrackState { t -> t.copy(playCount = t.playCount + 1) }
                        updateAssociatedTasks(currentTrack.id)
                    }
                    syncCurrentSessionHistory(isFinishing = true)
                } else {
                    // Criteria NOT met: do NOT call handleThresholdReached(), do NOT log into PlaybackHistory, do NOT increment playCount
                    syncCurrentSessionHistory(isFinishing = true)
                }

                // Reset progress ONLY when BOTH milestones are met: (1) 100% progress AND (2) physical end of the file
                val isFullyListened = progressPercent >= 100
                if (isFullyListened) {
                    performFullProgressReset(currentTrack, resetPosition = true)
                } else {
                    // Keep partial progress intact, only rewind playhead to beginning
                    initialSessionProgressPercent = progressPercent
                    isThresholdTriggeredForCurrentSession = false
                    updateTrackState { t ->
                        if (t.id == track.id) t.copy(lastPosition = 0L) else t
                    }
                }

                // Look for next track in the queue to auto play
                var nextTrackPlayed = false
                if (isAutoPlayEnabled.value && !isSleepAtEnd) {
                    val currentIndex = currentQueue.indexOfFirst { it.id == track.id }
                    if (currentIndex != -1 && currentIndex < currentQueue.size - 1) {
                        val nextTrackForPlayback = currentQueue[currentIndex + 1]
                        nextTrackPlayed = true
                        withContext(Dispatchers.Main) {
                            // Clear finalized track session state so playTrack() does not re-save
                            // the finished track's end position or leak it into the next track
                            sessionActualListeningMs = 0L
                            currentSessionHistoryId = null
                            setLastTrackedPosition(null)
                            _currentPosition.value = 0L
                            currentTrackValue = null
                            val latestNextTrack = currentQueue.firstOrNull { it.id == nextTrackForPlayback.id } ?: nextTrackForPlayback
                            playTrack(latestNextTrack, currentQueue)
                        }
                    }
                }

                if (!nextTrackPlayed) {
                    withContext(Dispatchers.Main) {
                        // STOP and PAUSE playback immediately so track does NOT auto-replay
                        try {
                            mediaPlayer?.pause()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error pausing player on end of track: ${e.message}")
                        }
                        _isPlaying.value = false
                        stopProgressTracking()
                        releasePlaybackWakeLock()

                        // Reset seek position cleanly to start so future play restarts from beginning
                        val seekReset = if (track.isVirtualScene) track.startOffsetMs else 0L
                        try {
                            mediaPlayer?.let { performSeek(it, seekReset) }
                        } catch (e: Exception) {}
                        setLastTrackedPosition(if (track.isVirtualScene) track.startOffsetMs else 0L)
                        _currentPosition.value = 0L
                        updateActiveSubtitleCue(if (track.isVirtualScene) track.startOffsetMs else 0L)

                        abandonAudioFocus()
                        updateMediaSessionPlaybackState()
                        showNotification()
                    }
                }
            }
        }
    }
    // =========================================================================
    // @END_LOCKED: End-of-File Lifecycle & Progress Reset
    // =========================================================================
}
