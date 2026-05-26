---
name: gimy-tv-app
description: Use when developing, deploying, or automating the Gimy TV App. Developer and architecture guide for CLI build, TDD guard, unified logging, and Cast/MediaSession.
version: 1.1.0
author: Hermes Agent
license: MIT
metadata:
  hermes:
    tags: [android, java, adb, logging, media-session, mcp, automation]
    related_skills: [gimy-tv-automation, native-mcp]
---

# Gimy TV App - AI Agent & Developer Guide 📺💀

This document serves as the Single Source of Truth (SSOT) for any AI agent or human developer working on the **Gimy TV App (鬼魅劇場)**. It outlines the project's unique lightweight architecture, build systems, debugging protocols, and Cast/MediaSession specifications.

## 🎯 When to Use
*   Use when compiling, building, or signing the Gimy TV App APK.
*   Use when deploying the app onto a connected Google TV or Android TV device over ADB.
*   Use when controlling, automating, or checking playback state via the Gimy TV MCP server.
*   **Do not use for**: Generic Android development with Gradle (this project uses a custom lightweight Python-based CLI build pipeline).

---

## 🏛️ Architecture & Component Map

The project implements standard Single Responsibility Principle (SRP) separation of concerns to avoid monolithic file bloat:

*   **`MainActivity.java`**: Dedicated solely to UI rendering, navigation grid, and receiving deep link intents.
*   **`GimyPlayer.java`**: Dedicated to video playback state, media controls, seeking, scrubbing, and key event delegation.
*   **`GimyMediaSession.java`**: Dedicated to Android `MediaSession` lifecycle, remote control notifications, metadata broadcast, and hardware volume routing.
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
    *   `MediaMetadata.METADATA_KEY_ALBUM_ART` & `MediaMetadata.METADATA_KEY_ALBUM_ART_URI`
    *   `MediaMetadata.METADATA_KEY_ART` & `MediaMetadata.METADATA_KEY_ART_URI`
    *   `MediaMetadata.METADATA_KEY_DISPLAY_ICON` & `MediaMetadata.METADATA_KEY_DISPLAY_ICON_URI`

### 2. Lockscreen & Template Compatibility Keys
To satisfy display requirements on various vendor custom skins (Samsung, Pixel, MIUI), we populate all display metadata fields:
*   `METADATA_KEY_DISPLAY_TITLE`
*   `METADATA_KEY_DISPLAY_SUBTITLE` (set to `"Gimy 鬼魅劇場"`)
*   `METADATA_KEY_ALBUM` (set to `"Gimy 鬼魅劇場"`)

### 3. Native Volume Routing (`setPlaybackToLocal`)
Instead of bloated custom `VolumeProvider` hacks that capture volume keys manually, the app utilizes native, lightweight system routing:
*   **Implementation**: Calling `mediaSession.setPlaybackToLocal(attrs)` informs Android TV's built-in Cast Shell (`mediashell`) that playback is local.
*   **System Integration**: When the TV Streamer is configured to output digital volume ("Google TV Streamer" audio mode instead of IR/CEC passthrough), Google Play Services on Wi-Fi connected phones automatically binds to this stream, allowing seamless volume synchronization without redundant app-level overrides.

---

## ⚠️ Known Pitfalls & Dev Guidelines
1.  **D-Pad Navigation Traps & Focus Theft**: 
   *   **Focus Theft on Recreation**: When recreating or populating a list/grid programmatically (e.g., after filter changes), check if the user is currently interacting with an upstream filter row. Query `((Activity) context).getCurrentFocus()` and verify if the focused view's parent's parent is a `HorizontalScrollView` (filter tag). If so, **do not** call `requestFocus()` on the grid items, preserving the user's active cursor location.
   *   **Strict Vertical Confinement**: To prevent Android TV's Focus Finder from leaking focus horizontally to adjacent panels (e.g., jumping from details buttons to movie grid cards on UP/DOWN), always intercept and handle DPAD_UP/DPAD_DOWN keys on bottom-most focusable buttons:
       *   **DPAD_DOWN**: Consume the key event (return `true`) to lock focus at the bottom.
       *   **DPAD_UP**: Manually request focus back on the parent container (`rightScrollView.requestFocus()`), perform a smooth scroll-up (e.g., `rightScrollView.smoothScrollBy(0, -100)`) so the elements transition out of frame fluidly, and return `true`.
   *   **Deep Link Overwrite Race Condition**: When cold-starting the app via a deep link, the details panel load competes with the asynchronous network movie grid load. When the grid finishes, Leanback auto-focuses the first grid item, which fires its `onFocusChange` and overwrites the deep-linked movie. Fix this by setting an `isDeepLinkActive = true` flag on deep link receipt, checking it in `onMovieCardFocused`, ignoring the initial autofocus load when true, and resetting it to false to allow subsequent manual navigation.
2.  **Dynamic 2D Asset Centering (Pillow/PIL)**:
   *   **The Ink Bounding Box Pitfall**: CJK and heavy display fonts have large internal ascender/layout padding (e.g., up to 90px empty vertical space for size 220), which shifts drawn letters significantly lower than the specified `y` coordinate.
   *   **Absolute 2D Centering**: Do not use hardcoded or simple layout bounding boxes. Use `draw.textbbox` to calculate the actual raw vertical and horizontal bounds of the *ink/pixels* of all characters across rows and columns. Dynamically solve coordinates so that:
       $$\text{Left Padding} = \text{Right Padding} \quad \text{and} \quad \text{Top Padding} = \text{Bottom Padding}$$
   *   **Breathing Room**: For multi-row grid icons (like a 2x2 split brand text), define a vertical breathing gap between rows of `12%` to `15%` of the font size (e.g., `gap_y = int(font_size * 0.15)`) to ensure the rows do not touch or feel cramped.
   *   **Formulas Reference**: See `[references/launcher-icon-centering-formula.md](references/launcher-icon-centering-formula.md)` for the complete dynamic 2D centering and column alignment mathematical equations.
3.  **Synchronous Filter UI Redrawing**:
   *   When an item is clicked in a horizontal selection row, always iterate through all sibling views of the row container (`optionsLayout.getChildAt(i)`) and dynamically invoke the state-styling helper (e.g., `updateFilterItemStyle`) on each. This guarantees previous selections instantly clear and only the active tag remains highlighted.
4.  **Agent-Native Observability (Focus Logging)**:
   *   To make the app 100% visible to AI agents and automated testing frameworks without relying on heavy screencap captures, integrate precise focus state logging in all `OnFocusChangeListener` blocks. Log entries as info (`Log.i("GimyHorror_UI", "🎯 FocusState: ...")`) with specific details of the focused element (e.g., card title/ID, button name, filter type/value).
5.  **M3U8 Parse Failures**: Gimyplus occasionally obfuscates `player_data` JSON under different script tags. If streaming fails, inspect `GimyParser.parseM3U8Url()` immediately for pattern mismatches.
6. Google TV Continue Watching Integration: 
    - The compilation SDK has been raised from **API 23 to API 26 (Android 8.0)** to support direct system imports of `android.media.tv.TvContract`. 
    - See `[references/google-tv-continue-watching-integration.md](references/google-tv-continue-watching-integration.md)` for the complete design, code templates, and background threading protocols.
7. Agent-Native MCP Integration & Direct Seek/Play:
    - This app features an Agent-Native design allowing headless, non-root AI agents to fully control and observe playback.
    - To prevent Leanback autofocus from overwriting deep-linked movies on startup, use the `isDeepLinkActive` flag workaround.
    - To read real-time watchlist and playback progresses without root, use the external JSON database export pattern.
    - Wrap all adb shell string extras in single quotes to prevent Android shell argument splitting when arguments contain spaces.
    - See `[references/agent-native-mcp-integration.md](references/agent-native-mcp-integration.md)` for complete architectural details, quoting rules, and Java code templates.
7.  **Agent-Native MCP Server Specification**:
    - The app supports a dedicated Model Context Protocol (MCP) server for hands-free automation.
    - All time parameters and return values are unified on a **Seconds** scale to prevent mathematical and type errors during agent invocation.
    - Enables high-performance search with **detailed synopses**, direct ADB deep-link launches, and zero-latency playback scrubbing.
    - `gimy_search_movies` supports a `silent` boolean parameter (defaulting to `true`). When `silent` is `false`, search results are synchronized and shown on the TV screen via Intent (`searchQuery`), with the keyword displayed on the top title bar's right side (small text, right-aligned), and is automatically cleared whenever any UI filter bar option is changed.
    - See `[references/agent-native-mcp-server-and-testing.md](references/agent-native-mcp-server-and-testing.md)` for tool schemas, self-healing intent delivery, and closed-loop testing.

## 🎯 Verification Checklist
- [ ] **Unit Tests**: Run `python3 scripts/build_apk.py` and ensure the JVM unit tests pass successfully before packaging.
- [ ] **ADB Connectivity**: Ensure `adb devices` lists `100.87.89.52:5555` as connected.
- [ ] **AutoPlay & Seek**: Test launching a deep link with `--ez autoPlay true -e seekPositionMs "5778000"` and verify direct, hands-free video start.
- [ ] **JSON State Export**: Check that `/sdcard/Android/data/com.gimytv.horror/files/GimyHorror_Store.json` exists and is populated.
- [ ] **MCP Conformity**: Run `python3 tests/test_gimy_mcp_advanced.py` and verify all integration tools return `success: true`.

---
