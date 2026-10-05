# Hearmark — Smart Audio & Video Study Player

Hearmark is an Android application built with Kotlin, Jetpack Compose (Material 3), and Room for active audio/video study, speech shadowing, goal-driven listening tasks, timestamped notebook annotations, and AI-powered subtitles, virtual scenes, and vocabulary quizzes.

---

## Key Features

- **Reach Coverage & Adaptive Progress Tracking**: Tracks cumulative unique listening segments per audio/video file using adaptive bitsets (`10..100` segments) without counting skipped regions.
- **Speech Shadowing & Practice Mode**: Auto-pauses at sentence or acoustic silence boundaries (or custom waveform cuts) with configurable pause multipliers (`0.5x`–`3.0x`) for language shadowing.
- **Goal Tasks & Scheduled Reminders**: Create recurring listening tasks (`PLAY_COUNT` or `DAYS`) for tracks, folders, or playlists with daily mini-goals and localized alarm notifications.
- **Timestamped Notebook & Vocabulary Bank**: Save timestamped audio snippets, bilingual vocabulary notes, and context sentences linked directly to source media.
- **Unified AI Hub (Google Gemini)**:
  - **Subtitle Generation**: Generates synchronized `.srt` subtitles from audio/video tracks (with chunked processing for long recordings).
  - **Virtual Scene Segmentation**: Splits long recordings into thematic virtual scenes (`startOffsetMs..endOffsetMs`) without duplicating physical media files.
  - **Interactive Quizzes**: Generates comprehension and vocabulary quizzes (`MCQ` and `TRUE_FALSE`) from track transcripts or notebook notes.
  - **Contextual AI Chat**: Multi-turn study assistant grounded in the active track, notebook notes, or task progress.
- **Full Bilingual UI (English & Arabic)**: Complete RTL/LTR layout adaptation and localized strings via `Loc.kt`.
- **Full JSON Backup, Restore & Broken-Path Relinking**: Exports and restores folders, tracks, playlists, notes, tasks, and listening history with automatic foreign-key remapping.

---

## Architecture & Tech Stack

- **UI**: Jetpack Compose, Material Design 3, Edge-to-Edge window insets, Picture-in-Picture (PiP) for video tracks.
- **Persistence**: Room SQLite Database (`AppDatabase`, schema version `19`, exported schemas in `app/schemas/`), Kotlin Coroutines & `StateFlow`.
- **Media Engine**: Custom `AudioPlayerManager`, `PlaybackProgressEngine`, `PracticeModeController`, and `HearmarkPlaybackService` (`MediaSessionCompat` + Foreground Media Notification).
- **Networking & Security**: OkHttp3 (`x-goog-api-key` header authentication) and Android Keystore (`AES/GCM/NoPadding`) via `GeminiKeyStore` for encrypted, backup-excluded API key persistence.

---

## Configuration & Building

### 1. Gemini API Key Setup
Hearmark reads optional build-time keys from `.env` (via the Secrets Gradle Plugin) or user-supplied keys entered in the in-app **Settings → Gemini API Keys** screen:
1. Copy `.env.example` to `.env` (or configure `GEMINI_API_KEY` in the AI Studio Secrets panel).
2. Alternatively, add one or more Gemini API keys directly inside the app's Settings screen. User-supplied keys are encrypted with the Android Keystore and stored in `gemini_secure_prefs.xml` (excluded from cloud backup and device transfer).

### 2. Running Unit & Robolectric Tests
Run the local JVM and Robolectric test suite with:
```bash
gradle :app:testDebugUnitTest
```

### 3. Protected Components
Core playback progress math, subtitle badge styling, virtual scene offset translation, and backup/restore serialization are documented in [`PROTECTED_COMPONENTS.md`](PROTECTED_COMPONENTS.md) and marked with `@LOCKED` comments in code. Review `PROTECTED_COMPONENTS.md` before modifying those modules.
