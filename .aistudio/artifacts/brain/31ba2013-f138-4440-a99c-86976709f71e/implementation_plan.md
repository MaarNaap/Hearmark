# Playback Progress Reset, Play Count Synchronization, and Segment Styling

Ensure audio track progress resets to 0% when reaching 100% across multiple sessions, synchronize play count display values between the player screen and the library from a unified database source, and apply a soft, transparent fade to the played segments progress bar.

### User Review & Critical Decisions

> [!IMPORTANT]
> The implementation strictly aligns with your clarified rules:
> - **100% Progress Auto-Reset**: When cumulative unique segments reach 100%, playback progress and stored segments reset cleanly to 0% so the track is ready for subsequent playback cycles without remaining stuck at 100%.
> - **Unified Play Count Source**: Both the player playback screen and library list items will observe and display the track's live database entity directly, eliminating any mismatch or cached drift.
> - **Faded Played Segments Visual**: The played segment coverage rectangles on the seek bar and cards will be rendered with a subtle, translucent fade (`primary.copy(alpha = 0.35f)`) rather than an opaque, intense highlight.

---

### 1. Overview & Core Concept

- **What It Does**: 
  1. Detects when a track reaches 100% listened progress (whether listened in a single continuous session or across multiple fragmented sessions) and resets the tracked segments and `lastPosition` back to 0% for future listening while preserving completed history entries and play counts.
  2. Unifies the data pipeline for `playCount` across the full-screen player, mini-player, and library track lists so that any increment in play count immediately updates all views simultaneously from the same underlying database entity.
  3. Softens the played segments bar on the progress slider into a delicate, faded tint so the user's current playhead and time markers remain distinct and prominent.
- **Target Audience / Persona**: Language learners, transcriptionists, and audio students tracking comprehensive listening coverage and accurate repetition metrics.
- **Key Value**: Reliable progress tracking lifecycle, consistent count metrics across all screens, and a cleaner, less visually distracting playback timeline.

---

### 2. User Experience & Visual Design

- **Played Segments Styling**:
  - The played segment rectangles rendered on the timeline `Canvas` will use `MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)` on both the full player and the home resumable card.
  - The current playhead needle and draggable thumb remain crisp, opaque, and unmistakable over the softened segment backdrop.
- **100% Completion Reset UX**:
  - As soon as the final unique segment is listened to and progress reaches 100%, the completion threshold trigger is recorded, play count increments once, and the progress resets to 0%. The user can continue or restart playback without the bar being permanently frozen at 100%.
- **Live Play Count Reflection**:
  - Player overlay header and library rows consistently display the exact same headphone count badge (e.g., `🎧 3`), updating in real time when completion is reached.

---

### 3. Key Product Decisions & Trade-Offs

- **Multi-Session 100% Progress Reset**:
  - *Chosen Approach*: Check for `progressPercent >= 100` both during continuous progress tracking ticks in `AudioPlayerManager` and during session finalization / end-of-track triggers. When 100% is reached, increment `playCount`, commit history, and reset `listenedSegments = ""` and `lastPosition = 0L` in the database and in-memory bitset.
  - *Why*: Solves the issue where stopping, pausing, or seeking near the end across separate sessions caused tracks to remain stuck at 100% indefinitely.
- **Single Source of Truth for Play Count**:
  - *Chosen Approach*: In `AudioPlayerOverlay`, derive the active track by looking up the current track ID in `allTracksList` (the live Room DB Flow) with a fallback to `currentTrackState`. Also ensure `AudioPlayerManager.playTrack()` loads the latest DB track model upon launch to prevent stale play counts.
  - *Why*: Eliminates desynchronization between player overlay composables and the library list items.
- **Visual Transparency**:
  - *Chosen Approach*: Apply `alpha = 0.35f` to the segment bar draw calls in `Screens.kt` (player seek bar and home card).
  - *Why*: Provides the requested light, faded look without obscuring track notes, cues, or the current playback thumb.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                 Room Database (AudioTrack)                  │
│       playCount, listenedSegments, lastPosition, etc.        │
└──────────────────────────────┬──────────────────────────────┘
                               │
               Flow<List<AudioTrack>> (tracks)
                               │
                ┌──────────────┴──────────────┐
                ▼                             ▼
       ┌──────────────────┐          ┌──────────────────┐
       │   Library List   │          │  Player Overlay  │
       │ (track.playCount)│          │ (track.playCount)│
       └──────────────────┘          └──────────────────┘
                ▲                             ▲
                └──────────────┬──────────────┘
                               │
                     AudioPlayerManager
         ┌─────────────────────────────────────────┐
         │ • progress >= 100% -> Reset to 0%       │
         │ • playCount += 1 updated via Mutex/Repo │
         │ • Faded segments Canvas alpha = 0.35f   │
         └─────────────────────────────────────────┘
```

- **Component & State Flow**:
  1. `AudioPlayerManager.kt`:
     - In progress tracking loop and end-of-track handling, when cumulative unique progress hits 100%, trigger threshold completion, increment `playCount`, clear `activeTrackSegmentsBitSet`, and reset `listenedSegments = ""` and `lastPosition = 0L`.
     - In `playTrack()`, synchronize `playCount` and listening progress from `repository.getTrackById()`.
  2. `Screens.kt`:
     - In `AudioPlayerOverlay`, bind `track` to `allTracksList.find { it.id == track.id } ?: currentTrackState ?: track` so play count is always read from the exact same live source as the library.
     - Adjust `segmentsColor` on the player timeline and home resumable card to `MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)`.
