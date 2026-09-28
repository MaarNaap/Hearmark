# 🛡️ Protected Components Registry

> **STRICT DIRECTIVE FOR AI ASSISTANTS**:
> The components, functions, UI blocks, and business logic listed in this registry have been verified, structured, and approved by the user.
> **DO NOT modify, reformat, refactor, or delete any code within or belonging to these components unless the user EXPLICITLY requests changes to them in the prompt by name.**

---

## 📋 Registry of Protected Components

### 1. Reach Coverage & Playback Progress Bars
- **Files**: `ui/home/HomeView.kt` and `ui/player/AudioPlayerOverlay.kt`
- **Description**: Canvas-based reach coverage segments and progress cursor needle.
- **Rule**: Do not add background overlay boxes, change alpha transparency of segments, or alter needle cursor rendering without explicit instructions.

### 2. Subtitle Styling & Timestamp Badges
- **Files**: `ui/player/SubtitleViewer.kt`
- **Description**: Exact background opacity, border stroke, typography, and timestamp pill layout for active/inactive subtitle cues.
- **Rule**: Do not adjust colors, font sizes (9.5sp), icons, or border strokes unless specifically asked.

### 3. Focus Mode & Practice Mode Header
- **Files**: `ui/player/AudioPlayerOverlay.kt`
- **Description**: Header layout displaying practice mode pill indicator and conditional rendering for focus mode.
- **Rule**: Do not reintroduce general focus mode pills or alter practice mode header structure without explicit request.

### 4. Audio Playback Actual Listening Time & Threshold Logic
- **Files**: `AudioPlayerManager.kt` (`recordCurrentSessionPlaybackProgress`, `startProgressTracking`, session listening maps)
- **Description**: Logic tracking `currentPlayActualListeningMs`, session thresholds, and bitset segment tracking.
- **Rule**: Do not modify how actual listening time accumulates, how `isThresholdTriggeredForCurrentSession` is evaluated, or how track listening time is persisted.

### 5. Room Database Migrations & Version Tracking
- **Files**: `Database.kt` (`MIGRATION_17_18`, table definitions, safety backup)
- **Description**: Database migration steps and safety backup routines.
- **Rule**: Never remove existing migrations, change version numbers, or alter table schemas without explicit user consent.

---

## 🔒 In-Code Marking Standard

When protecting code sections inside existing files, enclose them with standard boundary fences:

```kotlin
// =========================================================================
// @LOCKED: [Component Name] - STRICT FREEZE
// DO NOT MODIFY OR REFACTOR THIS BLOCK WITHOUT EXPLICIT PERMISSION IN PROMPT
// =========================================================================
... (protected code) ...
// =========================================================================
// @END_LOCKED: [Component Name]
// =========================================================================
```

---

## 💡 How the User Can Update This List
1. Simply state: *"Add [Feature/Component] to protected components"* or edit this file directly.
2. To modify a protected component, explicitly state: *"I want to update the protected [Component Name] to do X"*.
