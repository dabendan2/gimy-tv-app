# Agent-Native MCP Server Specification & Closed-Loop Testing 🤖📺

This reference document outlines the design, implementation, and testing of the Gimy TV App's **Agent-Native MCP (Model Context Protocol) Server**. This server bridges high-level AI reasoning with low-level Android TV ADB commands to enable frictionless keyword search and direct hands-free playback.

---

## 🏛️ Interface Architecture

Rather than forcing AI agents to emulate clumsy D-Pad remote keypresses to navigate an on-screen TV keyboard (which is slow and highly vulnerable to captchas), the Gimy MCP Server splits the workload:
1. **Search & Resolve (On Host)**: The agent scrapes Gimy (`gimyplus.com`) on the host machine using high-speed network libraries, parsing detailed metadata including movie ID, title, poster image, and **full synopsis**.
2. **Direct Intent Delivery (On TV)**: The agent issues an ADB Deep-Link Intent to launch the app directly into that specific movie's details panel with the **Play Button automatically focused**.

---

## 🛠️ Tool Specifications (JSON-Schema)

### 1. `gimy_search_movies(query, limit, silent, deviceIp)`
* **Description**: Queries Gimy plus using the verified MacCMS search endpoint (`/search/----------1---.html?wd=keyword`) and extracts structured results.
* **silent**: Defaults to `True`. If set to `False`, the results and search query keyword are synchronized and shown on the TV screen via ADB Intent (`searchQuery`), with the keyword displayed on the right-aligned title bar, and auto-cleared when any UI filter options are changed.
* **Returns**: Structured list of movies with:
  * `movieId` (e.g. `256828`)
  * `movieTitle` (e.g. `破墓`)
  * `imageUrl` (fully resolved HTTPS URL)
  * `status` (e.g. `HD`)
  * `subtitle` (actors/cast list)
  * `synopsis` (detailed description parsed directly from the search list)
  * `listState` (e.g. `Watch List (待播) 📝`, `Liked (喜歡) ❤️`, `Disliked 💩`) parsed from TV JSON storage.
  * `progress` (e.g. `已觀看 85.4% (01:23:45 / 01:40:00)`) parsed from TV JSON storage and formatted in seconds scale.

### 2. `gimy_launch_movie(movieId, movieTitle, imageUrl, subtitle, seekPosition, autoPlay, deviceIp)`
* **Description**: Synthesizes Gimy metadata, resolves flexible/oral seek coordinates (e.g., `"last 5m"`, `"倒數5分鐘"`, `"01:30:00"`) into a safe **Seconds** scale, and issues a deep-link launch command over ADB:
  ```bash
  adb -s <deviceIp>:5555 shell am start -n com.gimytv.horror/.MainActivity \
    -e movieId "<movieId>" \
    -e movieTitle "<movieTitle>" \
    -e imageUrl "<imageUrl>" \
    -e subtitle "<subtitle>" \
    --ez autoPlay true \
    -e seekPositionMs "5778000"
  ```
* **AutoPlay**: Defaults to `True` to directly launch full-screen video playback on the TV, bypassing redundant on-screen clicks.
* **Self-Healing State**: If `movieTitle` or `imageUrl` are omitted, the server automatically queries `https://gimyplus.com/vod/<movieId>.html` in the background to scrape details and sanitize paths before sending.

### 3. `gimy_playback_control(action, seekSeconds, deviceIp)`
* **Description**: Performs zero-latency playback commands on the TV:
  * `PLAY` / `PAUSE` / `TOGGLE_PLAY_PAUSE` ➔ Simulates `KEYCODE_DPAD_CENTER`.
  * `SEEK_FORWARD` ➔ Simulates `KEYCODE_DPAD_RIGHT` (trigger timeline scrub) followed by `KEYCODE_DPAD_CENTER` (commit seek).
  * `SEEK_BACKWARD` ➔ Simulates `KEYCODE_DPAD_LEFT` followed by `KEYCODE_DPAD_CENTER` (commit seek).
  * `VOLUME_UP` / `VOLUME_DOWN` ➔ Simulates `KEYCODE_VOLUME_UP/DOWN`.

### 4. `gimy_get_tv_state(deviceIp)`
* **Description**: Queries `adb devices`, gets active window focus (`dumpsys window`), and parses recent Gimy logcat entries (`GimyHorror_Player`, `GimyHorror_UI`, `GimyHorror_Parser`).
* **High Observability**: Allows closed-loop validation of active states (e.g., confirming `🎯 FocusState: Play Button (New) focused` exists in logs).

---

## 🧪 The Closed-Loop Testing Protocol

The testing suite (`test_gimy_mcp.py`) validates the entire tool chain in a single execution to guarantee that the agent has a fully functional loop:
1. **Search Querying**: Executes `gimy_search_movies` and asserts that results are valid JSON, matches exist, and `synopsis` is non-empty.
2. **Initial State Check**: Executes `gimy_get_tv_state` to verify ADB connection and get current focus.
3. **Intent Injection**: Triggers `gimy_launch_movie` with target movie ID.
4. **Playback Activation**: Issues `gimy_playback_control(action='PLAY')` to toggle play.
5. **Logcat Verification**: Refreshes TV state and asserts that `GimyHorror_UI` logged the incoming deep-link intent and successfully focused the play button on the TV screen.

This testing harness must be run after any updates to the MCP server to ensure zero regressions across scraping patterns or ADB connectivity.
