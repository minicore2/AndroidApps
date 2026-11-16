# 時刻 Clock

一個以 Kotlin + Jetpack Compose 實作的 Android 鬧鐘 App，包含鬧鐘、碼表與倒數計時三個頁面。鬧鐘使用 `AlarmManager.setAlarmClock` 排程，並以 foreground service 播放鈴聲，因此 App 關閉、螢幕鎖定時仍會響鈴。

## 功能

### 鬧鐘
- 一次性或重複鬧鐘：每天、每週（自訂單日）、僅工作日、自訂週一至週日。
- 12 / 24 小時制（跟隨系統、24 小時、12 小時）。
- 鈴聲：系統鬧鐘鈴聲、本機音樂（含 WAV），透過系統文件選擇器取得長期讀取權限。
- 震動開關、音量漸強（約 20 秒由小至大）。
- 貪睡：預設 10 分鐘，可設定 5 / 10 / 15 / 20 / 30 分鐘。
- 關閉方式：滑動關閉、搖晃關閉（可分別啟用；至少保留滑動）。
- 名稱標籤：上班、吃藥、運動等。
- 編輯、暫時跳過下一次（保留設定）、永久刪除。
- 鎖屏與 App 未開啟時響鈴：精確鬧鐘 + 全螢幕通知 + foreground service；重開機、時間/時區變更後自動重新排程。
- 勿擾 / 靜音 / 省電 / 鬧鐘音量：App 內「系統設定」面板顯示狀態並提供對應系統設定入口。
- 關閉通知權限不會取消鬧鐘排程；聲音與震動仍會執行（全螢幕響鈴畫面則受通知與全螢幕 intent 權限影響）。

### 碼表
- 百分秒顯示、開始 / 暫停 / 繼續 / 重設。
- 圈數記錄，顯示單圈與累計時間，標示最快與最慢圈。
- 以 `SystemClock.elapsedRealtime()` 計時，切換頁面或暫停後仍準確。

### 倒數計時
- 圓形進度、剩餘時間、預設（泡麵 3 分、煮飯 30 分、午睡 20 分、學習 25 分）與自訂時分秒。
- 背景執行：foreground service 顯示倒數通知，並以精確鬧鐘確保時間到會響。
- 時間到使用與鬧鐘相同的全螢幕響鈴服務。

## 建置

需求：JDK 17+、Android SDK Platform 35。本專案已包含 Gradle wrapper。

```bash
export JAVA_HOME="$HOME/.jdks/jbr-21.0.11"   # 或任何 JDK 17+
./gradlew :app:assembleDebug
```

APK 輸出：`app/build/outputs/apk/debug/app-debug.apk`

安裝到裝置 / 模擬器：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 首次使用建議

1. 允許通知權限（Android 13+）。
2. 在「系統設定」面板確認：
   - 精確鬧鐘（Android 12+）
   - 全螢幕鬧鐘（Android 14+）
   - 勿擾模式例外（`ACCESS_NOTIFICATION_POLICY`）
   - 電池最佳化例外
   - 鬧鐘音量不為 0
3. 部分廠牌（小米、華為、OPPO、vivo、Samsung）另需在系統的「自啟動 / 背景執行」設定中允許本 App。

## 專案結構

```
app/src/main/java/com/example/clock/
├── MainActivity.kt              # 三個頁面的 Compose 入口
├── ClockApp.kt                  # Application、依賴組裝
├── data/                        # Alarm 模型、JSON 持久化、設定
├── alarm/                       # 排程、響鈴 service、廣播接收、響鈴 Activity
├── timer/                       # 倒數狀態、foreground service、TimerEngine
├── system/                      # 系統權限/例外狀態檢查與設定入口
└── ui/                          # Compose 主題與所有畫面
```

## 已知限制

- 精確鬧鐘、全螢幕 intent、勿擾例外與電池最佳化皆由 Android / OEM 控制；App 只能引導使用者開啟。
- 若使用者關閉通知權限且 OEM 阻止背景啟動 Activity，鬧鐘仍會播放聲音/震動並於 10 分鐘後自動停止，但可能不會自動顯示響鈴畫面。
- 響鈴服務會在前景執行最多 10 分鐘後自動停止，避免無限響鈴。
- 音量漸強只調整 App 播放器的音量，不會改動系統鬧鐘音量；若系統鬧鐘音量為 0，請從「系統設定」面板開啟音量設定。
