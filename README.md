# LegecloPlayer Android (原生手機離線播放器)

專為 Android 打造的高效能原生 APK 離線播放器，無需依賴電腦端 Python 伺服器，直接在手機上讀取離線遊戲快取資料（Spine 2D 動態立繪、動畫視頻迴圈、CV 角色語音與對白）。

---

## 🌟 核心特色

1. **極致輕量化 (< 2.5 MB)**：
   - 不依賴肥大的 Termux、Embedded Python 或瀏覽器容器。
   - 內置純 Java 高效能本機 Socket 伺服器，支援 HTTP 206 Partial Content Range 串流，極致流暢播放高畫質動畫視頻。
2. **100% 離線運行**：
   - 包含完整 421 位角色大頭貼圖鑑選單與劇情播放核心引擎。
   - 支援自動掃描本機快取目錄，一秒識別「💾 離線就緒」角色並置頂。
3. **沉浸式全螢幕操作**：
   - 自動隱藏系統狀態列與手勢導航列，支援橫向感應旋轉與打孔挖孔螢幕延伸模式。
   - 智慧處理 Android 硬體返回鍵（優先關閉彈窗與選單，連續兩次回退防誤觸退出）。

---

## 📁 手機儲存目錄結構

手機本機快取預設存放路徑為：
`/sdcard/Download/LegecloPlayer/cache/`

結構說明：
```text
/sdcard/Download/LegecloPlayer/cache/
├── avatars/                 <- 角色大頭貼圓圖 (422+ 檔案)
├── bgm/                     <- 常用背景音樂 (如 m_adv_normal01.wav)
├── standing/                <- Spine 2D 骨骼立繪目錄 (如 standing/arthur_wedding/)
├── arthur_wedding_03/       <- 劇情資料夾 (包含 chapter_data.json, MP4, WAV)
└── ...                      <- 更多下載的角色劇情
```

---

## 🚀 常用腳本

- **`install_apk.bat`**：一鍵將 `LegecloPlayer.apk` 安裝/更新至連接的手機。
- **`sync_cache_to_phone.bat`**：一鍵將電腦端 `LegecloPlayer_Lite/cache/` 內的已下載角色與立繪同步推送至手機。
- **`build_apk.py`**：快速打包構建腳本（使用 Android AAPT2、D8、Javac，3 秒即可產出 APK）。
