# ADR 0001：Android 渲染引擎選擇（WebView / Chromium / GeckoView）

- 狀態：已採用（v1）；GeckoView 列為後續選項
- 日期：2026-11-15

## 背景

產品需要一個 Android 瀏覽器，能阻擋廣告（網路層 + cosmetic 層）、阻擋所有彈窗，
並嗅探／下載頁面中的影片。原本的決策是「Chromium / Gecko 嵌入」，但 Android 上
「嵌入 Chromium」與桌面認知不同，需要先釐清可發佈的選項。

## 選項

### A. Android System WebView（採用）

- Android 的 `WebView` 由 Chromium 提供，是 Android 上最實務的「Chromium 嵌入」。
- 可直接使用 `WebViewClient.shouldInterceptRequest`（網路層阻擋 + 媒體嗅探）、
  `androidx.webkit` 的 document-start 注入（cosmetic 過濾）、
  `WebChromeClient.onCreateWindow`（彈窗阻擋）。
- APK 體積小、由系統更新、與 Media3 Transformer 整合容易。
- 限制：無法控制 Chromium 版本；部分新版 WebView API 受系統版本限制；無法像
  桌面 Chromium 一樣自訂網路堆疊。

### B. 自建 / 嵌入 Chromium

- 需要自行編譯 Chromium 或使用非官方 AAR，體積數百 MB、建置與維護成本極高，
  且沒有 Google 官方支援的 embeddable Chromium 發佈管道。
- **不採用**：對本專案的成本效益不符。

### C. GeckoView（後續選項）

- Mozilla 官方可嵌入引擎，Maven repo：`https://maven.mozilla.org/maven2/`，
  依賴例：`org.mozilla.geckoview:geckoview:<version>`。
- 優點：可完全控制引擎版本；支援 WebExtensions，理論上可跑 uBlock Origin；
  內建內容阻擋分類。
- 缺點：APK 增加約 100 MB+；沒有 `shouldInterceptRequest` 這種同步請求攔截 API，
  廣告阻擋要改用 WebExtension 或 `ContentBlocking`；下載要走
  `GeckoSession.DownloadDelegate`；與現有 Media3/WebView 程式碼不相容。
- 若未來需要「自帶引擎版本」或「完整 uBlock 規則」，再評估遷移。

## 決策

v1 使用 **Android System WebView（Chromium）**。將引擎相關邏輯集中在
`AdBlocker`（規則/阻擋）、`VideoDownloader`（下載/匯出）與 `SpikeActivity`
（導航/UI），避免把引擎假設散落到各處，方便日後換成 GeckoView adapter。

## 後果

- 目前 APK 仍名為 `spike`，屬於可操作 prototype，不是商店版。
- 正式產品若要走 GeckoView，需要新增 `GeckoBrowserActivity` 與
  `GeckoAdBlocker`（WebExtension 或 ContentBlocking），並重新實作下載橋接。
- 若維持 WebView，建議把 `assets/adblock/rules.txt` 換成完整 EasyList 建置，
  並加入規則更新機制。
