# Privacy Policy for Hearmark

**Last Updated:** October 2026

Hearmark ("the App") is a local-first audio and video study player designed to help users organize media, track listening goals, take timestamped notes, and optionally use Google Gemini AI features for study assistance.

---

## 1. Local Data Storage
By default, all media metadata, folders, playlists, listening history, task schedules, quiz banks, and notebook annotations are stored **locally on your device** in an internal SQLite (Room) database:
- Imported audio, video, and subtitle files remain on your device storage.
- Manual or automatic JSON backups (`AutoBackupManager`) are written only to local app storage or to a file location you explicitly select via the Android system file picker.

---

## 2. API Keys & Security
If you provide custom Google Gemini API keys in the App's Settings:
- Your API keys are encrypted at rest using the **Android Keystore (`AES/GCM/NoPadding`)** and stored in a dedicated preferences file (`gemini_secure_prefs.xml`).
- This file is **explicitly excluded** from Android Auto Backup (`backup_rules.xml`) and cloud/device-transfer extraction (`data_extraction_rules.xml`), and is never included in exported JSON backup files.
- API keys are transmitted exclusively to `https://generativelanguage.googleapis.com` over HTTPS using the `x-goog-api-key` HTTP header.

---

## 3. Optional AI Features (Google Gemini API)
Hearmark only communicates with external servers when you explicitly trigger an AI feature in the Unified AI Hub, Player, or Notebook:
- **Subtitle Generation**: Sends the selected audio track (or extracted audio chunk) and optional reference text to the Google Gemini API (`generativelanguage.googleapis.com`) to produce synchronized subtitle cues.
- **Virtual Scene Detection**: Sends the track's transcript cues and title to the Google Gemini API to identify thematic scene boundaries.
- **Quiz Generation**: Sends the selected track's transcript cues or selected notebook notes to the Google Gemini API to generate study questions.
- **AI Study Chat**: Sends your chat prompt along with the selected context summary (active track transcript excerpt, selected notes, or task summary) to the Google Gemini API.

No audio files, transcripts, or notes are transmitted in the background unless you initiate one of these AI actions. Requests to the Google Gemini API are governed by Google's [Gemini API Terms of Service](https://ai.google.dev/gemini-api/terms) and [Google Privacy Policy](https://policies.google.com/privacy).

---

## 4. Permissions Used
- **Foreground Service & Media Playback (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`)**: Used to keep audio playing smoothly when the screen is off or the app is in the background.
- **Notifications (`POST_NOTIFICATIONS`)**: Requested contextually when you schedule task reminders so the App can display daily goal notifications and media playback controls.
- **Exact Alarms & Boot Completed (`SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`)**: Used solely to trigger and restore your scheduled listening task reminders at your chosen time.
- **Internet (`INTERNET`)**: Used solely to communicate with `generativelanguage.googleapis.com` when you invoke an AI feature.

---

## 5. User Control & Data Deletion
You have full control over your data at all times:
- You can delete individual tracks, folders, playlists, tasks, notes, quiz questions, or chat sessions directly within the App.
- You can remove saved Gemini API keys at any time in **Settings → Gemini API Keys**.
- Uninstalling the App permanently removes all local databases, cached media, and encrypted preferences stored in the App's private directory.
