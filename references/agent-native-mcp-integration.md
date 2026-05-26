# Agent-Native MCP Integration Guide (Gimy TV App) 🔌📺

This document outlines the architectural patterns and workarounds discovered during the integration of Gimy TV App with the Model Context Protocol (MCP) server. These techniques enable headless, non-root AI agents to fully control and observe Android TV applications securely.

---

## 1. Non-Root SharedPreferences Extraction (Local State Export)
* **The Problem**: On standard consumer Android TV devices, ADB shell runs as UID `2000` (`shell`), which has no permission to access the app's internal `/data/data/<package>/shared_prefs/` directory. Running `run-as <package>` fails if the package is compiled as a release (non-debuggable) build.
* **The Solution**: 
  In your persistent store class (e.g., `MovieStore.java`), implement a lightweight method to serialize and export the `SharedPreferences` to a JSON file under the app's external files directory on every state change:
  ```java
  private void exportStoreToExternal() {
      try {
          File dir = context.getExternalFilesDir(null); // No runtime permission needed for this directory!
          if (dir == null) return;
          File file = new File(dir, "GimyHorror_Store.json");
          
          StringBuilder sb = new StringBuilder();
          sb.append("{\n");
          Map<String, ?> all = prefs.getAll();
          boolean first = true;
          for (Map.Entry<String, ?> entry : all.entrySet()) {
              String key = entry.getKey();
              Object val = entry.getValue();
              if (key.startsWith("progress_pos_") || key.startsWith("progress_dur_") || key.startsWith("list_state_")) {
                  if (!first) sb.append(",\n");
                  first = false;
                  sb.append("  \"").append(key).append("\": ").append(val);
              }
          }
          sb.append("\n}");
          
          FileWriter writer = new FileWriter(file);
          writer.write(sb.toString());
          writer.close();
      } catch (Exception e) {
          Log.e("GimyHorror_Store", "Failed exporting to external storage", e);
      }
  }
  ```
* **Observation (ADB)**:
  The exported JSON file under `/sdcard/Android/data/com.gimytv.horror/files/GimyHorror_Store.json` is fully readable by ADB shell without root or run-as. The MCP server can pull this file:
  ```bash
  adb pull /sdcard/Android/data/com.gimytv.horror/files/GimyHorror_Store.json /tmp/GimyHorror_Store.json
  ```
  This allows the AI Agent to fetch real-time movie watchlist statuses, liked/disliked statuses, and precise playback percentages to enrich search results.

---

## 2. ADB Shell Quoting Pitfall with String Extras containing Spaces
* **The Problem**: 
  When initiating a deep link intent using Python's `subprocess.run(["adb", "shell", "am", "start", ...])`, local argument execution is shell-safe. However, the `adb` binary concatenates all arguments following `shell` into a single string joined by spaces and forwards that string to Android's `/system/bin/sh` target.
  If any string extra (such as `movieTitle` or `subtitle/actors`) contains spaces, the Android shell splits them, mangling all subsequent parameters (such as `autoPlay` or `seekPositionMs`).
* **The Solution**:
  Always wrap string extra values in **single quotes** inside your command list in Python before calling `subprocess.run`:
  ```python
  cmd = [
      "adb", "-s", f"{deviceIp}:5555", "shell", "am", "start", "-n", "com.gimytv.horror/.MainActivity",
      "-e", "movieId", f"'{movieId}'",
      "-e", "movieTitle", f"'{movieTitle}'", # Wrapped in single quotes
      "-e", "imageUrl", f"'{imageUrl}'",
      "-e", "subtitle", f"'{subtitle}'"       # Wrapped in single quotes
  ]
  ```
  This guarantees that the Android shell parses the strings containing spaces as single arguments.

---

## 3. AutoPlay Deep Link & Autofocus Race Condition
* **The Problem**:
  During a cold start of the TV App via a deep link, the details panel load competes with the asynchronous network movie grid load. When the grid finishes, Leanback automatically focuses the first item, which triggers `onMovieCardFocused()` and overwrites the deep-linked movie.
* **The Solution (The isDeepLinkActive Flag)**:
  1. Define a boolean flag `private boolean isDeepLinkActive = false;` in `MainActivity.java`.
  2. When a deep-linked intent is received, set `isDeepLinkActive = true;`.
  3. In `onMovieCardFocused()`, check the flag:
     ```java
     @Override
     public void onMovieCardFocused(Movie movie, View card) {
         if (isDeepLinkActive) {
             Log.i(TAG, "Ignoring autofocus card focus because a deep-link is active.");
             isDeepLinkActive = false; // Reset on first autofocus
             return;
         }
         // Normal focus behavior ...
     }
     ```
  This ignores the initial autofocus event on cold start, preserving the deep link, while allowing subsequent manual D-pad navigation.

---

## 4. Flexible Seek & Startup Playback (AutoPlay)
To allow zero-latency direct playing on TV startup, implement `autoPlay` and flexible start positions:
1. **Pass autoPlay and seekPositionMs**:
   ```java
   boolean autoPlay = intent.getBooleanExtra("autoPlay", false) || intent.hasExtra("seekPositionMs");
   ```
2. **Flexible Relative Seeking (Seconds to Milliseconds)**:
   In Python, parse oral times (such as "last 5m" or "倒數5分鐘") into negative second values (e.g. `-300` seconds) using a safe regex-based unit extractor, and then scale to milliseconds (`-300000` ms) in Python before passing via ADB. This ensures that the agent works on a safe, human-readable **Seconds** scale, preventing math/type errors during agent reasoning.
   Pass this negative millisecond value via ADB: `seekPositionMs = -300000`.
   Inside the player's `onPrepared` callback, resolve negative values relative to the prepared video duration:
   ```java
   int finalSeekPos = savedPos;
   if (activity instanceof MainActivity) {
       int pSeek = ((MainActivity) activity).pendingSeekMs;
       if (pSeek != -1) {
           if (pSeek < 0) {
               finalSeekPos = videoView.getDuration() + pSeek; // Resolve relative to duration!
           } else {
               finalSeekPos = pSeek;
           }
           ((MainActivity) activity).pendingSeekMs = -1; // Reset
       }
   }
   ```
   This ensures that even on cold starts when stream metadata has not been fetched, relative jump coordinates are resolved correctly.
