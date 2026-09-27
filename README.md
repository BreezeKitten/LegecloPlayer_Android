# 《れじぇくろ！》LegecloPlayer Android (原生手機離線播放器)

[![GitHub Release](https://img.shields.io/github/v/release/BreezeKitten/LegecloPlayer_Android?color=ff5e98&label=Release)](https://github.com/BreezeKitten/LegecloPlayer_Android/releases/latest)
[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen)](https://github.com/BreezeKitten/LegecloPlayer_Android)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

專為 Android 打造的高效能原生 APK 離線播放器。無需依賴電腦端 Python 伺服器或第三方容器，直接在手機上讀取離線遊戲快取資料（Spine 2D 動態立繪、動畫視頻迴圈、CV 角色語音與對白）。

搭配電腦端**《[LegecloPlayer_Lite](https://github.com/BreezeKitten/LegecloPlayer_Lite)》**下載的快取資源，即可享受 100% 離線隨身撥放！

---

## 📥 APK 下載安裝

* **最新版本下載點**：[GitHub Releases - LegecloPlayer.apk](https://github.com/BreezeKitten/LegecloPlayer_Android/releases/latest)
* **安裝方式**：下載 `.apk` 至手機直接點擊安裝即可（若系統提示允許未知來源安裝，請點擊允許）。

---

## 🌟 核心特色

1. **極致輕量原生架構 (< 4.2 MB)**：
   - 不依賴肥大的 Termux、Embedded Python 或 Node.js 容器。
   - 內建純 Java 高效能本機 Socket 伺服器，完整支援 HTTP 206 Partial Content Range 串流，極致流暢播放高畫質動畫視頻。
2. **100% 離線運行**：
   - 包含完整 421 位角色大頭貼圖鑑選單與劇情播放核心引擎。
   - 智慧自動掃描本機快取目錄，一秒識別「💾 離線就緒」角色並置頂。
3. **主人公稱呼自訂（自動替換劇本 `<name>`）**：
   - 首次開啟 App 時會引導設定主人公稱呼（預設為 `Master`，可自由設定為「團長」或自己的暱稱）。
   - 隨時可在右上角「⚙️ 設定」中修改，即時自動替換劇本中的 `<name></name>` 與 `$n`。
4. **Spine 3.8 骨骼立繪離線渲染**：
   - 完美離線解碼 Spine 動畫與表情，支援劇情推進自動變換表情動作，亦可鎖定喜愛的動作循環播放。
5. **沉浸式全螢幕操作**：
   - 自動隱藏系統狀態列與手勢導航列，支援橫向感應旋轉與打孔挖孔螢幕延伸模式。
   - 智慧處理 Android 硬體返回鍵（優先關閉彈窗與選單，連續兩次回退防誤觸退出）。

---

## 📖 教學：如何搭配電腦版《LegecloPlayer_Lite》載入離線檔

手機版 App 會自動讀取手機儲存空間中的快取目錄：
```text
/sdcard/Download/LegecloPlayer/cache/
```
只要將電腦版下載的快取放進該目錄，手機即可 **100% 離線播放**（無須連網、無須電腦連線）。

### 方式 A：【最推薦】使用內建一鍵 USB 同步腳本（極速、智慧增量）

專案內建了智慧同步工具，會自動偵測電腦端快取（如 `D:\LegecloPlayer_Lite\LegecloPlayer_Lite\cache\`）並進行增量推送：

1. **開啟手機 USB 偵錯**：
   - 手機「設定」→「關於手機」→ 連續點擊「版本號碼」7 次開啟開發人員選項。
   - 進入「系統 / 開發人員選項」→ 開啟 **「USB 偵錯」**。
2. **接上 USB 傳輸線連接電腦**：
   - 手機螢幕若彈出「允許這台電腦進行 USB 偵錯？」，請勾選「一律允許」並點擊「確定」。
3. **執行一鍵同步工具**：
   - 雙擊執行專案目錄下的 **`sync_cache_to_phone.bat`**。
   - 腳本會自動識別已連線手機與電腦端快取，並提供同步選單：
     ```text
     [1] 一鍵同步全部快取 (全章節 + 立繪 + 背景音效 + 大頭貼)
     [2] 僅同步系統底圖/立繪/大頭貼 (avatars + standing + bgm)
     [3] 僅同步特定角色章節資料夾
     ```
   - 輸入 `1` 按 Enter，ADB 就會高速將檔案推送進手機的正確路徑！

---

### 方式 B：手動透過 Windows 檔案總管拖曳複製（MTP 隨插即用）

若電腦未安裝 ADB 或不想使用命令列，亦可直接使用 Windows 檔案總管複製：

1. 手機插上 USB 傳輸線，在手機頂端通知列下拉選擇 **「檔案傳輸 (MTP)」** 模式。
2. 在電腦開啟「此電腦」→ 點進手機裝置名稱 → **「內部共用儲存空間」**。
3. 進入 **`Download`** 資料夾：
   - 新建資料夾名為 **`LegecloPlayer`**
   - 進入後再新建資料夾名為 **`cache`**
   - （即完整路徑為：`內部共用儲存空間\Download\LegecloPlayer\cache`）
4. 將電腦端快取複製進手機：
   - 電腦來源：`D:\LegecloPlayer_Lite\LegecloPlayer_Lite\cache\`（或 `LegecloPlayer_Lite/cache/`）
   - 將裡面的內容（包含 `avatars/`、`bgm/`、`standing/` 以及各角色劇情資料夾如 `ammon_02/`、`arthur_wedding_03/` 等）直接拖曳複製到手機的 `cache/` 目錄中。

---

## 📁 手機儲存目錄結構

手機本機快取目錄結構如下：

```text
/sdcard/Download/LegecloPlayer/cache/
├── avatars/                 <- 角色大頭貼圓圖 (422+ 檔案，如 10001.png)
├── bgm/                     <- 背景音樂檔 (如 m_adv_normal01.wav)
├── standing/                <- Spine 2D 骨骼立繪 (如 standing/arthur_wedding/)
├── ammon_02/                <- 角色劇情章節資料夾
│   ├── chapter_data.json    <- 劇情對白與動作資料
│   ├── h_adv_*.mp4          <- 動畫視頻
│   └── v_*.wav              <- CV 角色語音
├── arthur_wedding_03/
└── ...                      <- 更多下載的角色劇情
```

---

## 🎮 開啟 App 驗證

1. 在手機上開啟 **《Legeclo》** App。
2. 首頁的「🌸 角色大頭貼圖鑑選單」會**自動掃描本機快取**：
   - 手機內已有檔案的角色，都會打上 **「💾 離線就緒」** 綠色徽章並**自動置頂排列**在最前方。
3. 點選任何帶有 `💾` 標記的章節按鈕（如 `第2章 💾` / `第3章 💾`）即可秒進劇情！

---

## 🛠️ 開發與專案腳本

- **`build_apk.py`**：快速打包腳本（使用 Android AAPT2、D8、Javac，無須 Gradle，3 秒即可產出簽名 APK）。
- **`install_apk.bat`**：一鍵將產出的 `LegecloPlayer.apk` 安裝/更新至連接的手機。
- **`sync_cache_to_phone.bat`**：一鍵將電腦端已下載角色快取推送至手機。

---

## ⚖️ 免責聲明

本專案僅供程式技術交流與個人離線劇本檢閱使用。所有遊戲素材、美術、立繪、語音與影音版權均歸原遊戲開發商與發行商所有。
