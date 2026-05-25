---
name: gimy-tv-app
description: Developer and architecture guide for Gimy TV App — CLI build, TDD guard, unified logging, and Cast/MediaSession specifications.
category: software-development
tags:
  - android
  - java
  - adb
  - logging
  - media-session
---

# Gimy TV App - AI Agent & Developer Guide 📺💀

This document serves as the Single Source of Truth (SSOT) for any AI agent or human developer working on the **Gimy TV App (鬼魅劇場)**. It outlines the project's unique lightweight architecture, build systems, debugging protocols, and Cast/MediaSession specifications.

---

## 🏛️ Architecture & Component Map

The project implements standard Single Responsibility Principle (SRP) separation of concerns to avoid monolithic file bloat:

*   **`MainActivity.java`**: Dedicated solely to UI rendering, navigation grid, and receiving deep link intents.
*   **`GimyPlayer.java`**: Dedicated to video playback state, media controls, seeking, scrubbing, and key event delegation.
*   **`GimyMediaSession.java`**: Dedicated to Android `MediaSession` lifecycle, remote control notifications, metadata broadcast, and hardware volume routing.
*   **`TvWatchNextHelper.java`**: Dedicated to updating and removing entries in the Android TV system "Watch Next / 繼續觀賞" row.
*   **`MovieStore.java`**: Persistent local database using `SharedPreferences` for movie bookmarking states and playback milliseconds.
*   **`GimyParser.java`**: HTML parser and stream link extractor for `gimyplus.com`.
*   **`ImageLoader.java`**: Non-blocking asynchronous network image downloader with local RAM caching.
*   **`Log.java`**: Package-private custom log wrapper.

---

## ⚡ CLI Build & TDD Pipeline

This project bypasses heavy build systems (like Gradle/Android Studio) in favor of a 2-second CLI compilation chain using native tools.

### Key Commands
```bash
# 1. Regenerate visual assets (Launcher icon, TV Banner) using PIL
python3 scripts/make_assets.py

# 2. Compile Java, run JVM unit tests, compile DEX, align, and cryptographically sign APK
python3 scripts/build_apk.py

# 3. Connect, install, and run on television
adb connect 100.87.89.52:5555
adb install -r bin/signed.apk
adb shell am start -n com.gimytv.horror/.MainActivity
```

### 🛡️ The TDD Guard
`scripts/build_apk.py` executes `com.gimytv.horror.TestRunner` **immediately after Java compilation**.
*   **Rule**: If any unit test fails, the build pipeline **aborts instantly** and refuses to pack or sign the APK.
*   **Guard**: Never disable or bypass this test runner. Tests are the Single Source of Truth.

---

## 📡 Observation & Debugging (Unified Logging)

### The `com.gimytv.horror.Log` Wrapper
Since unit tests run on a local host JVM (which lacks Android's runtime), calling raw `android.util.Log` throws `NoClassDefFoundError`.
*   **Solution**: We use our package-private `Log` utility. It detects the presence of the Android framework dynamically:
    *   **In Unit Tests**: Automatically redirects logs to Stdout with brackets (`[INFO] [TAG] msg`).
    *   **On TV/Android**: Dynamically invokes the high-performance native `android.util.Log` for real-time logcat.
*   **Rule**: Always use `Log.i()`, `Log.d()`, `Log.w()`, `Log.e()` inside core classes. Never use `android.util.Log` or `System.out.println` directly.

### ADB Logcat Filter
To monitor the Gimy TV App's real-time events on the television, filter by the specific tags:
```bash
adb logcat -s GimyHorror_Parser GimyHorror_Player GimyHorror_Store GimyHorror_Media GimyHorror_UI
```

---

## 📱 Casting & MediaSession Specification

To make the app appear beautifully as a Cast notification on mobile phones (allowing pause, play, progress tracking, and volume adjustment):

### 1. Artwork & Poster Display (Absolute HTTP URIs)
Many mobile notification template renderers and Cast receivers reject local raw `Bitmap` byte arrays over IPC/Binder if they are too large, or if they lack URI identifiers.
*   **Implementation**: Always pass **both** the scaled Bitmap (resized to `320px` max dimension) and the **fully qualified absolute HTTPS URL** (补全 relative `/upload/` paths to `https://gimyplus.com/upload/`) to the following metadata keys:
    *   `MediaMetadata.METADATA_KEY_ALBUM_ART` & `METADATA_KEY_ALBUM_ART_URI`
    *   `MediaMetadata.METADATA_KEY_ART` & `METADATA_KEY_ART_URI`
    *   `MediaMetadata.METADATA_KEY_DISPLAY_ICON` & `MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI`

### 2. Lockscreen & Template Compatibility Keys
To satisfy display requirements on various vendor custom skins (Samsung, Pixel, MIUI), we populate all display metadata fields:
*   `METADATA_KEY_DISPLAY_TITLE`
*   `METADATA_KEY_DISPLAY_SUBTITLE` (set to `"Gimy 鬼魅劇場"`)
*   `METADATA_KEY_ALBUM` (set to `"Gimy 鬼魅劇場"`)

### 3. Absolute Volume Control Bridge (`VOLUME_CONTROL_ABSOLUTE`)
If a local MediaSession uses relative volume controls, connected mobile devices will display the warning **"TV does not support volume adjustment"**.
*   **Implementation**: We route volume to a custom `VolumeProvider` in `VOLUME_CONTROL_ABSOLUTE` mode, mapped 1:1 to the TV's physical stream `STREAM_MUSIC` (typically `0` to `15` steps).
*   **Callbacks**:
    *   **`onSetVolumeTo(int volume)`**: Handles dragging the volume slider on the phone. Bridges directly to `am.setStreamVolume()`.
    *   **`onAdjustVolume(int direction)`**: Handles hardware physical volume button clicks on the phone. Bridges to `am.adjustStreamVolume()`.

---

## ⚠️ Known Pitfalls & Dev Guidelines

1.  **D-Pad Navigation Traps**: All interactive layout panels built programmatically (like `DetailPanelManager`) must define focus styling change listeners and preserve focus targets (e.g. `tvDetailSynopsis.setFocusable(true)`).
2.  **Watch Next Sandbox Restrictions**: Writing to Android TV's system `watch_next_program` using raw selections is blocked by the OS sandbox. You **must delete by appended system ID** via `ContentUris.withAppendedId()` instead.
3.  **M3U8 Parse Failures**: Gimyplus occasionally obfuscates `player_data` JSON under different script tags. If streaming fails, inspect `GimyParser.parseM3U8Url()` immediately for pattern mismatches.
