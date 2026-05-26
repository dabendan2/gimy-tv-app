# Gimy TV App (鬼魅劇場) 📺💀

一款專為 **Google TV / Android TV** 平台量身打造的原生、輕量級極簡影音播放器。內嵌「鬼魅劇場」恐怖/驚悚影片專區，具備優雅的 Cyber-Horror 暗黑風格 UI，並針對實體 D-Pad 遙控器進行了極致的互動手感優化。

本專案完全擺脫了數 GB 的 Gradle 重型構建系統，採用極輕量的 CLI 純 Java / ADB 自動化編譯流程，實現「2秒內極速完整構建 + 1秒實機部署」，是嵌入式與客廳 TV 開發的極致典範。

---

## ✨ 核心特色與功能

*   **📺 100% 遙控器相容 (D-Pad Navigation)**: 專為電視設計，所有按鈕、卡片與輸入選項均具備完美高亮焦點，完全不依賴任何觸控操作。
*   **🔒 垂直焦點鎖定與滑動對接**: 徹底阻斷 D-Pad 垂直方向的焦點向外溢出至左側影片牆的 Bug。在下方控制按鈕按「上」時，焦點會順暢回彈至右側詳情面板整體，並伴隨 `33px` 呼吸感的頁面向上滾動。
*   **🤖 代理友善與智慧診斷 (Agent-Friendly Design)**: 核心事件監聽器全面注入焦點流日誌。AI 代理或自動化框架可藉由單行 `adb logcat | grep FocusState` 於毫秒內、以純文字形式**瞬時通靈**得知遙控器正指在何處，打造次世代 Agent-Native UI。
*   **📱 媒體廣播與通知控制 (Chromecast-like MediaSession)**: 深度整合 Android 原生 `MediaSession`。播放時，局域網路內的所有 Android 手機、Google Home 均能即時看見播放片名、進度，並能直接在手機通知欄執行「暫停/播放/拖曳進度」。
*   **💾 智慧 60 秒背景自動存檔**: 為防進程因系統調度被殺（Low Memory Killer）或覆蓋升級，起播後每 60 秒在背景自動寫入播放進度（精確到毫秒），安全感拉滿。
*   **⚡ 互動捷徑與防抖優化**: 
    *   在左側影片卡片點擊「OK」後，焦點直接移至「右側詳情面板整體」並以 `#303134` 高亮，在此狀態再按一次「OK」焦點即精確穿梭至播放按鈕。
    *   在上方篩選橫排切換選項時，其他選項狀態同步更新，且影片載入不會強制搶奪（Steal）使用者此時在篩選列上的導航焦點。
*   **📝 待播清單智慧置頂**: 影片詳情面板提供 `+` (預設) ➔ `📝` (待播) ➔ `❤️` (喜歡) ➔ `💩` (討厭) 循環狀態切換。凡是被標記為 `📝` 的影片，重新整理時將會**自動排序至影片牆最前端**。
*   **🎨 全新極簡 Google 風格雙列圖示**: 採用 Pillow 圖格墨跡 2D 包圍網分析，計算出完美像素的 2x2 拼字版 `Gimy`Launcher 圖示。具備動態上下、左右空間的**絕對幾何置中對稱**。

---

## 🏛️ 專案架構 (Directory Structure)

專案已依循標準 GitHub Android 開發規範進行結構重構，實現極致乾淨的代碼隔離：

```directory
gimy-tv-app/
├── .gitignore              # 過濾二進位編編譯產物與臨時截圖
├── README.md               # 專案說明書
├── SKILL.md                # [SSOT] 內置技能定義主檔案
├── my-release-key.jks      # 電視端專屬簽章金鑰 (Keystore)
├── bin/
│   └── signed.apk          # 最終打包編譯好、可直接安裝至電視的 APK 快捷路徑
├── app/
│   ├── build/              # 編譯過渡產物與 APK 發行目錄 (Git 自動忽略)
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml   # 安裝包配置清單 (版本、權限、LEANBACK 主頁宣告)
│       │   ├── res/
│       │   │   └── drawable/         # 解析度自適應電視 Banner 與 Launcher 圖示
│       │   └── java/com/gimytv/horror/
│       │       ├── MainActivity.java      # 主 Activity (僅專職處理 UI 渲染與導航)
│       │       ├── MovieStore.java        # 儲存模組 (SRP 隔離，專職處理進度與清單持久化)
│       │       ├── GimyMediaSession.java  # 媒體模組 (專職 MediaSession 生命週期與手機通知欄聯動)
│       │       ├── GimyParser.java        # 解析模組 (專職 HTML 解析、流媒體通靈提取)
│       │       ├── ImageLoader.java       # 圖片模組 (非同步快取與載入海報)
│       │       └── Movie.java             # 影音實體資料模型 (Data Model)
│       └── test/java/com/gimytv/horror/
│           ├── GimyParserTest.java  # 解析模組單元測試
│           └── TestRunner.java      # 輕量級自動化測試執行器 (TDD 核心)
├── scripts/
│   ├── make_assets.py      # 一鍵繪製/更新電視端 App 圖示與橫幅
│   ├── build_apk.py        # 一鍵執行單元測試、編譯 Java、轉換 Dex、打包、對齊、簽章與發行
│   └── gimy_mcp_server.py  # Agent-Native MCP 伺服器 (秒級降維控制核心)
└── tests/
    ├── test_gimy_mcp.py    # 標準 MCP 閉環自動化測試
    └── test_gimy_mcp_advanced.py  # 進階一鍵秒播、時間跳轉、我的最愛更新測試
```

---

## 🚀 開發與建置指引

由於本專案採用 CLI 極速自動化編譯體系，您不需要安裝龐大的 Android Studio。

### 1. 產生/更新圖示資源 (PIL Required)
當您需要調整 App 圖示文字或背景時，修改 `scripts/make_assets.py` 並執行：
```bash
python3 scripts/make_assets.py
```
這會在 `app/src/main/res/drawable/` 下重新繪製 `ic_launcher.png` 與 `tv_banner.png`。

### 2. 一鍵執行測試與編譯 APK (TDD Enforced)
執行編譯腳本：
```bash
python3 scripts/build_apk.py
```
**⚙️ 編譯器工作流 (Workflow):**
1.  **自動清理**: 移除舊的 `app/build/` 產物。
2.  **Java 編譯**: 同時將主代碼與測試代碼編譯為 JVM 1.8 位元組碼。
3.  **測試守衛 (TDD Guard)**: 自動運行 `TestRunner` 單元測試。**若單元測試失敗，會立即中止編譯**，從根本上杜絕將帶有 Regression 的測試包發佈至電視。
4.  **資源封裝 & 輕量化 Dex 轉換**: 只將 production 代碼轉換為 Dalvik dex，排除測試類，確保 APK 極致小巧。
5.  **對齊與 V2/V3 簽章**: 自動進行 `zipalign` 記憶體對齊優化，並使用電視端專屬 `jks` 金鑰完成數位簽章。

---

## 📺 實機部署 (Deploy & Launch)

確保您的 Google TV Streamer (或其他 Android TV) 與編譯主機處於同一個區域網路 (或 Tailscale VPN)，並已開啟 ADB 調試。

```bash
# 1. 連線至電視 (預設連接 IP: 100.87.89.52:5555)
adb connect 100.87.89.52:5555

# 2. 進行無損資料覆蓋安裝 (保留您的觀影進度、待播清單與歷史紀錄)
adb install -r bin/signed.apk

# 3. 強制啟動電視端 Gimy TV App
adb shell am start -n com.gimytv.horror/.MainActivity
```

---

## 🤖 AI 代理原生與 MCP 整合 (AI Agent & MCP Integration)

本專案深度整合了 **Model Context Protocol (MCP)**，專為 AI 代理人打造了強大的遠端語意化控制。

### 1. 電視端進度與狀態自動導出
每當影片播放進度存檔、或清單狀態（待播 `📝`、喜歡 `❤️`、不喜歡 `💩`）發生更新時，`MovieStore` 會自動將其導出為標準 JSON，存於外部檔案系統中：
`儲存路徑：/sdcard/Android/data/com.gimytv.horror/files/GimyHorror_Store.json`

此檔案對 ADB 具備完全讀取權限，使 MCP 伺服器能無痛拉取、並結構化展示使用者的所有觀影狀態。

### 2. 深層連結起播與快進指令 (Deep-Link AutoPlay & Seek)
本 App 提供高階意圖參數，可於單一 intent 內完成啟動、自動播放與快進：
```bash
adb shell am start -n com.gimytv.horror/.MainActivity \
  -e movieId "255334" \
  -e movieTitle "女鬼橋2：怨鬼樓" \
  --ez autoPlay true \
  -e seekPositionMs "5778000"
```
*   `autoPlay` (Boolean Extra): 為 `true` 時，詳情頁載入後將自動開始放映，完全省略手動遙控點擊。
*   `seekPositionMs` (String Extra): 指定起播跳轉位置（單位毫秒）。**支援負毫秒數**（例如：`-300000` 表示倒數 5 分鐘），使 App 在冷啟動、視流準備就緒時，動態自癒跳轉至相對時間。

### 3. MCP 伺服器工具鏈 (`gimy_mcp_server.py`)
專案附帶了基於 FastMCP 的標準 MCP 伺服器，所有輸入與回傳值均優化為 **秒 (Seconds)** 尺度，以避免代理算術偏誤：
*   `gimy_search_movies(query)`: 搜尋影片，同步合併導出資料，回傳帶有觀影進度（百分比/時分秒）、狀態符號與詳細簡介的 JSON。
*   `gimy_launch_movie(movieId, seekPosition, autoPlay)`: 一鍵直達投射放映，`seekPosition` 支援極度口語的 `"last 5m"`、`"倒數5分鐘"` 或 `"01:30:00"`，並自動在 Python 端將其安全轉譯。
*   `gimy_playback_control(action)`: 語意化遙控控制，提供 `PLAY`, `PAUSE`, `SEEK_FORWARD` (D-pad 右 ➔ 中確認), `SEEK_BACKWARD`, `VOLUME_UP/DOWN`。
*   `gimy_get_tv_state()`: 當前電視活動焦點與 logcat 行為觀測。

---

## 📝 授權與維護

本專案由 Dabendan 自主設計、開發與維護，供電視影音同好交流使用。有任何新功能構想，歡迎提交 Issue 討論！👻🍿
