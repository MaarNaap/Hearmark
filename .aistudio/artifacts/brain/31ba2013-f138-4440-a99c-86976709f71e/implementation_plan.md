# Comprehensive Modular Refactoring Plan

A pure move, zero-behavior-change architectural refactoring plan to decompose the four largest monolithic files in the codebase (`GeminiService.kt`, `UnifiedAiHubSheet.kt`, `SettingsView.kt`, and `CreateTaskScreen.kt`) into focused, maintainable, single-responsibility modules with strict compilation and automated test gates.

---

## User Review & Critical Decisions

> [!IMPORTANT]
> All steps are pure code extractions with **zero intentional behavior changes**, strict API compatibility across all existing callers (`QuizViewModel`, `MediaAiViewModel`, `AppViewModel`, `SceneDetectionExtensions`, and test suites), and unified state management.

- **Confirmed Decision 1 (Scope & Sequence)**: Follow the user-specified 4-phase rollout order:
  1. `GeminiService.kt` (Backend service decomposition + facade; zero UI risk + existing test suite).
  2. `UnifiedAiHubSheet.kt` (Isolated modal sub-components + actions; mechanical extraction).
  3. `SettingsView.kt` (Independent M3 settings cards with localized state).
  4. `CreateTaskScreen.kt` (Multi-step wizard decomposed into discrete step composables backed by `@Stable TaskFormState`).
- **Confirmed Decision 2 (Facade Strategy for AI)**: Keep `GeminiService` as an `object` facade exposing all 15 public methods and model constants, internally delegating to dedicated domain modules to avoid touching any of the 5 consumer call-sites.
- **Confirmed Decision 3 (State Safety & Single Source of Truth)**: Mutable state (`okHttpClient`, `uploadedFileCache`, `pacingLock`, model health cooldown registry) remains in single, well-defined internal singletons to prevent race conditions or divergent cooldown tracking.
- **Confirmed Decision 4 (Verification Gates)**: Execute Gradle test and compilation verification (`compile_applet` and local unit test runner) after each phase before starting the next.

---

## 1. Overview & Core Concept

The target codebase contains four monolithic files exceeding 1,500 lines each (totaling ~6,854 lines), causing high cognitive overhead, slow IDE indexing, and tight coupling between domain logic, network retries, and Compose UI layouts. 

This refactoring breaks each file into cohesive domain units:
- **`com.example.ai`**: Network transport & retry engine, model health cooldown registry, API key pool, audio/video media extraction, chat streaming, resilient scene parsing, quiz generation, and subtitle generation/streaming.
- **`com.example.ui.hub`**: Extracted picker sub-sheets, file track items, notebook note selector, task picker, and hub action executors.
- **`com.example.ui.settings`**: Independent M3 card composables (playback, headset, practice/waveform, appearance, Gemini multi-key dialogs, and data backup/restore).
- **`com.example.ui.tasks`**: Step-based wizard structure powered by `@Stable TaskFormState` coordinating source selection, track tree picking, threshold/label configuration, schedule timing, summary review, and footer navigation.

---

## 2. User Experience & Visual Design

Because this is a pure refactoring of existing implementations, the visual design and user experience remain 100% pixel-faithful and functionally identical. However, the modular Compose decomposition enforces core Material 3 design quality:

- **Component Encapsulation**: Each card and step owns its internal transient dialog and editing state, preventing unnecessary root recompositions of sibling cards.
- **Accessibility & Touch Targets**: Retain all existing `Modifier.testTag()`, accessibility semantics, and 48dp minimum touch target boundaries across sliders, pickers, and checkboxes.
- **Responsive Layout**: Maintain `Modifier.fillMaxWidth()` with standard 8dp/16dp vertical padding and M3 elevation cards across both portrait handhelds and expanded tablet layouts.
- **Predictable Navigation**: Step transitions in `CreateTaskScreen` and sheet transitions in `UnifiedAiHubSheet` maintain smooth `AnimatedVisibility` and pager transitions with intact back-handling.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: AI Service Facade vs. Complete Consumer Migration
- **Chosen Approach**: Retain `GeminiService` as a thin object facade forwarding calls to `GeminiHttp`, `GeminiModelHealth`, `GeminiKeyPool`, `GeminiMedia`, `GeminiChat`, `GeminiSceneDetection`, `GeminiQuiz`, and `GeminiSubtitles`. Constants (`DEFAULT_MODEL`, `FALLBACK_MODEL`, etc.) remain accessible directly on `GeminiService`.
- **Why**: Zero risk of breaking external view models, extension functions, or `GeminiSceneParsingAndHealthTest.kt`. Decouples internal structural hygiene from external consumers.
- **Alternatives Considered**: Refactoring all 5 call sites simultaneously — rejected because it expands the blast radius and complicates git diffs.

### Decision 2: State Model for `CreateTaskScreen` (`TaskFormState`)
- **Chosen Approach**: Consolidate the 35 loose `rememberSaveable` / `mutableStateOf` fields into a single `@Stable class TaskFormState` containing all form inputs, validation rules, and mutations.
- **Why**: Eliminates 30+ parameter signatures across the 4 step composables. Ensures state persists predictably across step navigations and configuration changes.
- **Alternatives Considered**: Passing individual state holders and callback lambdas down 4 levels — rejected due to extreme boilerplate and high parameter churn.

### Decision 3: Card-Level State Locality in `SettingsView`
- **Chosen Approach**: Move dialog states (API key add/edit/delete, backup confirmation, clear history confirmation) directly inside their respective card composables (`GeminiSettingsCard`, `DataManagementCard`). Only root application settings flows (`AppViewModel` state flows) are passed from `SettingsView`.
- **Why**: Isolates re-renders to the affected card and trims `SettingsView` down to an orchestrator (~150 lines).

---

## 4. Technical Architecture & System Decomposition

### System Layout & Module Relationships

```
┌────────────────────────────────────────────────────────────────────────┐
│                          Consumer ViewModels                           │
│  QuizViewModel │ MediaAiViewModel │ AppViewModel │ SceneDetectionExt   │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ Calls unchanged APIs
                                    ▼
┌────────────────────────────────────────────────────────────────────────┐
│                   GeminiService.kt (Thin Facade)                       │
└───────┬────────────┬────────────┬─────────────┬───────────┬────────────┘
        │            │            │             │           │
        ▼            ▼            ▼             ▼           ▼
┌──────────────┐┌──────────┐┌───────────┐┌─────────────┐┌───────────────┐
│GeminiModel   ││Gemini    ││GeminiMedia││GeminiChat / ││GeminiSubtitles│
│Health & Keys ││Http      ││(Audio/    ││Quiz / Scene ││& Subtitle     │
│(Cooldown,    ││(OkHttp,  ││Video      ││Detection    ││Parsing (SSE,  │
│Pool, Pacing) ││Post, File││TimeSlice) ││             ││SRT Cleaner)   │
└──────────────┘└──────────┘└───────────┘└─────────────┘└───────────────┘

┌────────────────────────────────────────────────────────────────────────┐
│                          Jetpack Compose UI                            │
├─────────────────────┬──────────────────────┬───────────────────────────┤
│ UnifiedAiHubSheet   │ SettingsView         │ CreateTaskScreen          │
│ ├─ AiHubTypes       │ ├─ PlaybackSettings  │ ├─ TaskFormState (@Stable)│
│ ├─ AiHubPickers     │ ├─ HeadsetControls   │ ├─ TaskStepper            │
│ │   ├─ FilePicker   │ ├─ PracticeSettings  │ ├─ TaskSourceStep (Page 0)│
│ │   ├─ NotesPicker  │ ├─ AppearanceSettings│ ├─ TaskTrackPicker        │
│ │   └─ TasksPicker  │ ├─ GeminiSettingsCard│ ├─ TaskThresholds (Page 1)│
│ └─ AiHubActions     │ ├─ ApiKeyDialogs     │ ├─ TaskSchedule (Page 2)  │
│                     │ └─ DataManagementCard│ ├─ TaskSummary (Page 3)   │
│                     │                      │ └─ TaskFooterNav          │
└─────────────────────┴──────────────────────┴───────────────────────────┘
```

---

## 5. Detailed Step-by-Step Implementation Specification

### Phase 1: `GeminiService.kt` Decomposition (Target: 2,014 -> ~120 lines)

1. **`com.example.ai.GeminiHttp` (~330 lines)**
   - Extract `okHttpClient`, `streamingHttpClient`, `pacingLock`, `lastRequestTimestamp`, and `paceRequestIfNeeded()`.
   - Extract `executeGeminiPostWithRetry()`, `parseGeminiErrorMessage()`, and `cleanJsonText()`.
   - Extract uploaded file cache, `uploadFileToGemini()`, and `getMimeTypeForFile()`.
2. **`com.example.ai.GeminiModelHealth` & `GeminiKeyPool` (~180 lines)**
   - Model constants: `DEFAULT_MODEL`, `FALLBACK_MODEL`, `LITE_FALLBACK_MODEL`, `PRO_FALLBACK_MODEL`, `EXP_FALLBACK_MODEL`.
   - Health registry: `cooldownMap`, `selectOrderedCandidateModels()`, `markModelSuccess()`, `markModelFailure()`, and `clearModelHealthStateForTesting()`.
   - Key pool: `setConfiguredApiKeys()`, `resolveCandidateApiKeys()`, and `resolveApiKey()`.
3. **`com.example.ai.GeminiMedia` (~235 lines)**
   - `getMediaDurationMs()`, `extractAudioFromVideoIfPossible()`, and `extractAudioTimeSliceIfPossible()`.
4. **`com.example.ai.GeminiSubtitleParsing` & `GeminiSubtitles` (~510 lines)**
   - `cleanSrtOutput()` and `parseSseSubtitleStream()`.
   - Add unit test coverage for `cleanSrtOutput` and `parseSseSubtitleStream`.
   - `executeSubtitleGenerationWithRetry()` and `generateSubtitles()`.
5. **`com.example.ai.GeminiSceneDetection` (~340 lines)**
   - Resilient regex and JSON scene parsing + `detectScenes()`.
6. **`com.example.ai.GeminiQuiz` & `GeminiChat` (~400 lines)**
   - `generateQuizUnified()`, `generateQuiz()`, `generateQuizForTrack()`, and `sendMessage()`.
7. **`GeminiService.kt` Facade Update**
   - Re-export model constants as `const val` / delegations.
   - Forward all 15 public methods to their respective modules.
   - **Verification Gate**: Run `GeminiSceneParsingAndHealthTest` and `compile_applet`.

---

### Phase 2: `UnifiedAiHubSheet.kt` Decomposition (Target: 1,543 -> ~680 lines)

1. **`com.example.ui.hub.AiHubTypes` (~30 lines)**
   - Enums: `AiHubTab`, `AiContentSource`.
2. **`com.example.ui.hub.AiHubActions` (~195 lines)**
   - `executeAiAction()` and `getFullSubtitlesForTrack()`.
3. **`com.example.ui.hub.AiHubPickers` & `NotebookNotesPicker` (~610 lines)**
   - `HierarchyFilePicker` and `FileTrackRowItem`.
   - `NotebookNotesPicker` with note search and selection state.
   - `TasksPicker` for selecting linked practice tasks.
4. **`UnifiedAiHubSheet.kt` Orchestrator Update**
   - Focus purely on sheet scaffold, header, tab selection, content router, and bottom action bar.
   - **Verification Gate**: Run `compile_applet`.

---

### Phase 3: `SettingsView.kt` Decomposition (Target: 1,513 -> ~150 lines)

1. **`com.example.ui.settings.SettingsFormatUtils` (~25 lines)**
   - `formatPlaybackSpeed()` and `formatDuration()`.
2. **Settings Card Composables (`com.example.ui.settings.cards.*`)**:
   - `PlaybackSettingsCard`: Slider thresholds, skip duration forward/backward.
   - `HeadsetControlsCard`: Single/double/triple tap triggers and actions.
   - `PracticeSettingsCard`: Waveform segment editor launch, reanalyze buttons, practice tolerance.
   - `AppearanceSettingsCard`: Theme selection, dynamic colors, app language.
   - `GeminiSettingsCard` + `ApiKeyDialogs`: Multi-key list, test key status, add/edit/delete dialogs.
   - `DataManagementCard`: History clearing, JSON backup/restore, auto-backup cadence, CSV/JSON export.
3. **`SettingsView.kt` Orchestrator Update**
   - Clean scrollable `Column` laying out the individual cards with injected ViewModel state.
   - **Verification Gate**: Run `compile_applet` and settings persistence tests.

---

### Phase 4: `CreateTaskScreen.kt` Decomposition (Target: 1,784 -> ~250 lines)

1. **`com.example.ui.tasks.TaskFormState` (~220 lines)**
   - `@Stable` class encapsulating the 35 state fields (task name, task type, selected track IDs, threshold levels, schedule time, daily goals, validation errors, and step index).
   - Helper methods: `validateCurrentStep()`, `toTaskEntity()`, `populateFromExistingTask()`.
2. **Step Composables (`com.example.ui.tasks.steps.*`)**:
   - `TaskStepper`: Step title, subtitle, and visual progress dot indicators.
   - `TaskSourceStep`: Page 0 inputs (title, category/type dropdown, target metrics).
   - `TaskTrackPicker`: Folder hierarchy tree navigation and independent track selector.
   - `TaskThresholdLabelsStep`: Page 1 sliders and label assignments.
   - `TaskScheduleStep`: Page 2 time picker, repeat day toggles, daily mini-goal.
   - `TaskSummaryStep`: Page 3 final review card and configuration overview.
   - `TaskFooterNav`: Back, Next, and Save/Finish action buttons with validation guards.
3. **`CreateTaskScreen.kt` Orchestrator Update**
   - Host `rememberTaskFormState()` and bind to `HorizontalPager`.
   - **Verification Gate**: Run `compile_applet` and complete test suite.

---

## 6. Verification Plan

### Automated Build & Unit Tests
1. **Per-Phase Compilation**: Run `compile_applet` immediately after completing each phase to guarantee zero syntax or import errors.
2. **Robolectric & JVM Unit Tests**:
   - `gradle :app:testDebugUnitTest --tests com.example.GeminiSceneParsingAndHealthTest` (Phase 1).
   - `gradle :app:testDebugUnitTest --tests com.example.PlayerSettingsPersistenceTest` (Phase 3).
   - `gradle :app:testDebugUnitTest --tests com.example.TaskAndSilenceDetectionTest` (Phase 4).
   - Full test run across all 10 unit test suites on final completion.

### Manual Verification Flow in Emulator
1. **AI Hub**: Open AI Hub sheet, switch tabs (Subtitles, Scene Detection, Quiz), pick media and notes, verify UI renders identically.
2. **Settings**: Open Settings, toggle dark/light theme, adjust playback sliders, open Gemini API key dialog, open backup dialog.
3. **Task Creation**: Tap FAB to create task, navigate Step 0 -> Step 1 -> Step 2 -> Step 3, verify track tree selection, and save task.
