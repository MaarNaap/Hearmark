# Manual JSON Upload for Virtual Scene Creation: Implementation Plan

This plan introduces manual JSON upload for virtual scenes, allowing you to bypass AI model timeouts and 503 errors completely. You can generate the scenes JSON with any external AI (Claude, ChatGPT, Gemini Web) using our provided specification prompt and upload it directly into the app.

---

## 1. Feature Architecture & Flow

### JSON Schema & Parsing
We will support a clean, forgiving JSON schema that can accept either milliseconds (`startMs`/`endMs`) or timestamp strings (`"00:01:23.500"` / `"01:23"`):
```json
{
  "scenes": [
    {
      "sceneNumber": 1,
      "title": "Scene Title",
      "summary": "Brief description of the scene.",
      "startMs": 0,
      "endMs": 145000,
      "dialogueQuote": "Key dialogue or context"
    }
  ]
}
```
*Also supports direct array `[ { ... } ]` format for maximum AI output compatibility.*

### Processing Pipeline
1. **File Picker**: Use Android's `rememberLauncherForActivityResult(ActivityResultContracts.GetContent())` filtering for `application/json` (or `*/*`).
2. **Parsing & Validation**: Read the JSON text from the file Uri, parse scene objects, validate that time boundaries fall within track duration, and sort by `startMs`.
3. **Existing Scenes Check (Replace vs Append)**:
   - If the track already has virtual scenes in a folder: display an alert dialog asking:
     - **Replace**: Clear/overwrite previous scenes in the existing scenes folder.
     - **Append**: Add the newly imported scenes into the existing folder with adjusted ordering.
   - If no existing scene folder exists: create a dedicated folder titled `"[Track Title] - Scenes"` (or `"[Track Title] - مشاهد"`) just like the AI service does.
4. **Virtual Track Creation**: Create virtual tracks with `isVirtual = true`, `parentTrackId = track.id`, `virtualStartMs`, and `virtualEndMs`.
5. **Subtitle Association**: Automatically link the parent track's subtitle cues within the scene's time boundaries.

---

## 2. UI Entry Points

As requested:
1. **Track Options Menu (`TrackDialogs.kt` - `TrackOptionsMenu`)**:
   - Add **"Import Scenes from JSON"** / **"استيراد المشاهد من ملف JSON"** alongside the existing AI Scene Detection option.
2. **Review Scenes Dialog (`TrackDialogs.kt` - `ReviewScenesDialog`)**:
   - Add an **"Import JSON"** action button in the dialog header / actions bar, allowing instant import or re-import while reviewing scenes.
3. **Localization (`Loc.kt`)**:
   - Add bilingual labels and dialog strings in English and Arabic for file picking, success toasts, error messages, and the Replace/Append choice dialog.

---

## 3. Dedicated AI Prompt for Generating the JSON

We will provide a turnkey prompt that you can copy-paste directly into ChatGPT, Claude, or Gemini along with your subtitle text or video transcript.

```markdown
You are an expert video/audio editor. Analyze the following transcript/subtitles and divide them into meaningful, self-contained virtual scenes.

OUTPUT FORMAT REQUIREMENTS:
- Output ONLY valid, raw JSON (no conversational text, no markdown backticks, no comments).
- The root must be a JSON object containing a "scenes" array (or directly an array of scenes).

Each scene object must contain:
1. "sceneNumber": Integer starting from 1.
2. "title": Concise, descriptive title for the scene (in the language of the audio or requested language).
3. "summary": A 1-2 sentence overview of what happens in this scene.
4. "startMs": Start time in milliseconds (integer).
5. "endMs": End time in milliseconds (integer).
6. "dialogueQuote": (Optional) A prominent line or quote from the beginning of this scene.

IMPORTANT RULES:
- Ensure scenes are chronological and non-overlapping.
- "endMs" of a scene must be greater than "startMs".
- Make each scene a coherent topic, chapter, or conversational exchange (typically between 1 to 5 minutes each).

TRANSCRIPT / SUBTITLES:
[PASTE YOUR SUBTITLES OR TRANSCRIPT HERE]
```

---

## 4. Proposed Implementation Steps

1. **Create `SceneJsonImporter.kt` in `com.example.ui`**:
   - Logic to parse JSON input from `Uri` / `InputStream` (supporting both root objects and raw arrays, as well as ms or string timestamps).
   - Core function `importScenesFromJson(track: AudioTrack, jsonString: String, mode: ImportMode, onComplete: (Int) -> Unit)`.
2. **Update `TrackDialogs.kt`**:
   - Add the JSON file picker launcher.
   - Add the "Import Scenes from JSON" menu item in `TrackOptionsMenu`.
   - Add the "Import JSON" icon/button in `ReviewScenesDialog`.
   - Add Replace/Append confirmation dialog when existing scenes are detected.
3. **Update `Loc.kt`**:
   - Add translation strings in Arabic and English for all new UI items.
4. **Compile & Verification**:
   - Run `compile_applet` to ensure seamless compilation with no regressions.
