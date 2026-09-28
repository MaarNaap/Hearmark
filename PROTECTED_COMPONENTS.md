# 🛡️ Protected Components & Core Logic Registry

> **STRICT DIRECTIVE FOR AI ASSISTANTS**:
> The components, functions, UI blocks, and business logic listed in this registry have been verified, structured, and approved by the user.
> **DO NOT modify, reformat, refactor, or delete any code within or belonging to these components unless the user EXPLICITLY requests changes to them in the prompt by name.**

---

## 📋 Registry of Protected Components & Logic

### 1. Reach Coverage & Playback Progress Bars
- **Files**: `ui/home/HomeView.kt` and `ui/player/AudioPlayerOverlay.kt`
- **Description**: Canvas-based reach coverage segments and progress cursor needle.
- **Rule**:
  - Keep segments styling faded with `MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)`.
  - Do not add background overlay boxes over the progress bar.
  - Do not alter the needle cursor rendering or scrubber thumb math without explicit instructions.

### 2. Subtitle Styling & Timestamp Badges
- **Files**: `ui/player/SubtitleViewer.kt`
- **Description**: Exact background opacity, border stroke, typography, and timestamp pill layout for active/inactive subtitle cues.
- **Rule**: Do not adjust colors, font sizes (9.5sp), icons, or border strokes unless specifically asked.

### 3. Focus Mode & Practice Mode Header
- **Files**: `ui/player/AudioPlayerOverlay.kt`
- **Description**: Header layout displaying practice mode pill indicator and conditional rendering for focus mode.
- **Rule**: Do not reintroduce general focus mode pills or alter practice mode header structure without explicit request.

### 4. Play Count Calculation & Threshold Logic
- **Files**: `AudioPlayerManager.kt` (`handleThresholdReached`, `startProgressTracking`, `handlePhysicalEndOfTrack`)
- **Description**: How `playCount` increments and when completion thresholds are met during a session.
- **Invariant Rules**:
  - `playCount` must increment at most **once per listening cycle/session** via `!isThresholdTriggeredForCurrentSession`.
  - Condition:
    ```kotlin
    val shouldTrigger = when {
        initialSessionProgressPercent >= 100 -> false
        initialSessionProgressPercent >= completionThreshold -> progressPercent >= 100
        else -> progressPercent >= completionThreshold
    }
    ```
  - Incrementing `playCount` also records session history and triggers `updateAssociatedTasks(track.id)`.
  - Reaching the physical end of the file alone without meeting the threshold criteria must **never** increment `playCount`.

### 5. Maximum Progress & Reach Calculation
- **Files**: `Database.kt` (`AudioTrack.getAdaptiveNumSegments`, `getListenedCount`, `getProgressPercent`, `getListenedBitSet`), `AudioPlayerManager.kt`
- **Description**: BitSet segmentation, bitmask calculation, and percentage reach.
- **Invariant Rules**:
  - Segment count is adaptive based on duration (`minOf(durationS, 100).coerceAtLeast(10)`).
  - Progress percentage is strictly `((bitSet.cardinality() * 100) / numSegments).coerceIn(0, 100)`.
  - BitSet segments are set ONLY during continuous, forward non-seeking playback (never interpolated across seek jumps).
  - Segments are preserved across pause, resume, and app restarts via `listenedSegments`.

### 6. End of File (Physical Completion) Lifecycle
- **Files**: `AudioPlayerManager.kt` (`handlePhysicalEndOfTrack`, `performFullProgressReset`)
- **Description**: What happens when the audio playback physically finishes.
- **Invariant Rules**:
  - Marks only final segments if playback was within 2000ms of effective end.
  - Computes `progressPercent` *before* clearing active in-memory bitsets.
  - **Full Reset Rule**: Reset of `listenedSegments = ""` and `lastPosition = 0L` occurs **ONLY** when BOTH conditions are satisfied:
    1. Cumulative unique progress reached **100%** (`progressPercent >= 100`), **AND**
    2. The track reached the physical end of the file (`handlePhysicalEndOfTrack`).
  - If end of file is reached but `progressPercent < 100`, partial segment coverage is strictly retained and only `lastPosition` is rewound to 0L.

### 7. Playback Tracking & Resumable Candidates Logic
- **Files**: `AudioPlayerManager.kt` (`startProgressTracking`, `syncCurrentSessionHistory`, `persistCurrentPlayListeningTime`), `Screens.kt` (`HomeView`)
- **Description**: Actual listening time accumulation (`currentPlayActualListeningMs`, `trackAccumulatedListeningMsMap`) and resumable candidate evaluation in Home/Library.
- **Invariant Rules**:
  - `lastPosition > 0` defines uncompleted or resumable tracks. Do not filter out tracks with `progressPercent == 100` before they are reset by physical completion.
  - Actual listening time is tracked via wall-clock diffs and excludes paused or seeking durations.

### 8. Room Database Migrations & Version Tracking
- **Files**: `Database.kt` (`MIGRATION_17_18`, table definitions, safety backup)
- **Description**: Database migration steps and safety backup routines.
- **Rule**: Never remove existing migrations, change version numbers, or alter table schemas without explicit user consent.

---

## 🔒 In-Code Marking Standard

Code sections marked with the following boundary fences must never be modified, touched, or refactored unless specifically requested by name:

```kotlin
// =========================================================================
// @LOCKED: [Component or Logic Name] - STRICT FREEZE
// DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
// =========================================================================
... (protected code) ...
// =========================================================================
// @END_LOCKED: [Component or Logic Name]
// =========================================================================
```

---

## 💡 How to Update This Registry
1. To lock an additional feature or component: *"Add [Feature/Component] to protected components"*.
2. To deliberately modify a protected component: *"I explicitly want to modify the locked [Component Name] to do X"*.
