# Modularization & Large File Decomposition Plan

Decompose the application's five largest monolithic files into clean, focused, single-responsibility components with zero behavior changes, zero UI regression, and robust compilation and unit test verification after each phase.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The four phases will be executed sequentially with verification gates at every phase.
> All extracted files will maintain the existing `package com.example.ui` (and respective sub-packages) so that no existing call sites or references break across the app.

- **Confirmed Decision 1**: Execute all four phases sequentially, starting with Phase 1 (`TrackDialogs.kt`).
- **Confirmed Decision 2**: Keep package declarations consistent with the originating files (`package com.example.ui` for UI files) to eliminate import churn and avoid regression.
- **Confirmed Decision 3**: Verification strategy: compile verification (`compile_applet`) and local JVM unit test execution (`gradle :app:testDebugUnitTest`) after each phase.
- **Safety Rule on Protected Code**: Playback tracking core, progress bitsets, continuous segment flushing, and locked fences in `AudioPlayerManager.kt` and `AudioPlayerOverlay.kt` will remain untouched.

---

### 1. Overview & Core Concept

- **What It Does**: Refactors and decouples over 10,000 lines of monolithic code across five files into high-cohesion, testable modules without altering application features, UI design, database schemas, or playback mechanics.
- **Target Files**:
  1. `TrackDialogs.kt` (~2,570 lines)
  2. `AppViewModel.kt` (~2,440 lines)
  3. Giant Composables: `AudioPlayerOverlay.kt` (~2,970 lines) & `StatsScreen.kt` (~2,100 lines)
  4. `AudioPlayerManager.kt` (~2,750 lines)
- **Key Value**: Drastically reduces code review burden, eliminates recomposition churn, enables isolated unit testing, and conforms to Android clean architecture guidelines (keeping source files under ~500–800 lines).

---

### 2. User Experience & Visual Design

- **Zero Visual Regression**: UI components are moved verbatim without altering styling, padding, elevation, typography, colors, animations, or touch target sizes.
- **Preserved Theming**: Material Design 3 theme tokens, typography styles, and localized strings (`Loc.getText(...)`) remain identical.
- **State Integrity**: Hoisted state, view models, and navigation callbacks will retain exact parameter signatures and event propagation.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Pure Verbatim Moves First**
  - *Chosen Approach*: Move complete, self-contained top-level composables and utility functions into dedicated files within the same package before considering any refactoring.
  - *Why*: Guarantees zero logic changes, keeps Git history clear, and isolates any syntax/import issues immediately.
  - *Alternatives Considered*: Rewriting functions while splitting. Rejected to prevent regressions.

- **Decision 2: AppViewModel Delegation over Monolith**
  - *Chosen Approach*: Group logical domains (Tasks, Notes & Tags, Library/Import) into delegate classes/extension managers while `AppViewModel` remains the unified façade for UI composables.
  - *Why*: Existing screens and composables that consume `viewModel` require zero call-site refactoring.

- **Decision 3: Respecting `@LOCKED` Protected Code**
  - *Chosen Approach*: Maintain all protected playback, tracking, and segment synchronization logic inside `AudioPlayerManager.kt` and only extract satellite responsibilities (Subtitles, Notification & MediaSession, Queue management).
  - *Why*: Prevents regression in the critical listening tracking and study progress engines.

---

### 4. Technical Architecture & Sequential Phase Breakdown

```
┌────────────────────────────────────────────────────────────────────────┐
│                          App Architecture                              │
├────────────────────────────────────────────────────────────────────────┤
│                                                                        │
│   Phase 1: Dialogs & UI Items (TrackDialogs.kt -> 8 focused files)     │
│   ┌───────────────────────────────────────────────────────────────┐    │
│   │ PlaylistDetailsView  │ PlaylistTaskDialogs │ SceneDialogs     │    │
│   │ TrackRow             │ TrackSorting        │ FolderTreeItems  │    │
│   │ TrackInfoDialog      │ TimeFormatUtils                        │    │
│   └───────────────────────────────────────────────────────────────┘    │
│                                                                        │
│   Phase 2: AppViewModel Decomposition (Façade + Specialized Delegates) │
│   ┌───────────────────────────────────────────────────────────────┐    │
│   │ AppViewModel (Façade) ──► TaskActionsDelegate                 │    │
│   │                       ──► ImportManagerDelegate               │    │
│   │                       ──► NotesActionsDelegate                │    │
│   │                       ──► SettingsStateDelegate               │    │
│   └───────────────────────────────────────────────────────────────┘    │
│                                                                        │
│   Phase 3: Screen Decomposition (AudioPlayerOverlay & StatsScreen)     │
│   ┌───────────────────────────────────────────────────────────────┐    │
│   │ AudioPlayerOverlay ──► PlayerDialogs, TopBar, Controls, Tabs  │    │
│   │ StatsScreen        ──► FolderFilterDialog, StatsTabPages      │    │
│   └───────────────────────────────────────────────────────────────┘    │
│                                                                        │
│   Phase 4: Player Engine Separation (AudioPlayerManager Facade)       │
│   ┌───────────────────────────────────────────────────────────────┐    │
│   │ AudioPlayerManager ──► SubtitleController                     │    │
│   │                    ──► PlaybackNotificationController         │    │
│   │                    ──► PlaybackQueueController                │    │
│   │                    ──► (Locked Tracking Core Remains In Place)│    │
│   └───────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────┘
```

#### Detailed Phase Execution Plan

#### Phase 1: `TrackDialogs.kt` Decomposition (Zero Risk)
Split `TrackDialogs.kt` into 8 separate files in `/app/src/main/java/com/example/ui/dialogs/`:
1. `PlaylistDetailsView.kt`: `PlaylistDetailsView` and playlist track management rows.
2. `PlaylistTaskDialogs.kt`: `AddToPlaylistDialog`, `AssociatedTasksDialog`, and `formatScheduledDays`.
3. `SceneDialogs.kt`: `EditVirtualSceneDialog`, `AddNewVirtualSceneDialog`, and `ReviewScenesDialog`.
4. `TrackRow.kt`: `UnifiedAudioTrackRow`, `UnifiedTrackDropdownMenu`, `SmartFileNameText`, and `middleEllipse`.
5. `TrackSorting.kt`: `getSortedTracks` and `TrackListSortHeader`.
6. `FolderTreeItems.kt`: `SubfolderDetailsItem` and `FolderTreeNodeItem`.
7. `TrackInfoDialog.kt`: `TrackInfoDialog` and `InfoItemRow`.
8. `TimeFormatUtils.kt`: `formatTimestampMs` and `parseTimestampToMs`.
- **Phase 1 Verification**: Run `compile_applet` and `gradle :app:testDebugUnitTest`.

#### Phase 2: `AppViewModel.kt` Modularization (Low Risk)
Extract focused managers/delegates while maintaining `AppViewModel` as the single public API:
1. `TaskActionsDelegate.kt`: Task creation, duplication, scheduling, and progress calculations.
2. `ImportManagerDelegate.kt`: Folder tree scanning, URI resolution, missing track rebinding, and relinking logic.
3. `NotesActionsDelegate.kt`: Note CRUD, tag management, snippet playback coordination.
4. `SettingsStateDelegate.kt`: API keys, playback preferences, backup timers, and settings state.
- **Phase 2 Verification**: Run `compile_applet` and `gradle :app:testDebugUnitTest`.

#### Phase 3: Giant Composables Extraction (Medium Risk)
1. **`AudioPlayerOverlay.kt`**:
   - Extract independent modal dialogs (`WaveformSegmentEditorDialog`, cue synchronization dialog, etc.).
   - Extract `PlayerTopBar` and `PlayerFocusModeControls`.
   - Extract player content pager tabs (Lyrics/Subtitles view, artwork display, practice modes).
   - Retain progress canvas, needle drawing, and locked sections intact.
2. **`StatsScreen.kt`**:
   - Move folder tree filtering to `StatsFilterDialogs.kt`.
   - Extract calculations to pure functions in `StatsModels.kt`.
   - Extract each stats tab into dedicated composables (`OverviewTab`, `ListeningHistoryTab`, `TaskStatsTab`, `ConsistencyTab`).
- **Phase 3 Verification**: Run `compile_applet` and `gradle :app:testDebugUnitTest`.

#### Phase 4: `AudioPlayerManager.kt` Separation (Careful Facade Separation)
1. `SubtitleController.kt`: Subtitle loading, offset shifting, and cue parsing.
2. `PlaybackNotificationController.kt`: Notification builder, media style configuration, and lock screen controls.
3. `PlaybackQueueController.kt`: Queue sequencing, next/previous tracks, and SharedPreferences queue persistence.
4. Keep the progress tracking engine, continuous segment flushed bitset, and locked play-count evaluation strictly untouched.
- **Phase 4 Verification**: Run `compile_applet` and `gradle :app:testDebugUnitTest`.
