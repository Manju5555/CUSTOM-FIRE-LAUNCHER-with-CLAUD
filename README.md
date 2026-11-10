# 📺 CUSTOM FIRE LAUNCHER with CLAUD (`FIRE TV CUSTOM HS VER2`)

A fast, lightweight, ad-free custom Home Screen Launcher built for Amazon Fire TV (`com.prasad.firehome` — **Version 2.0**).

[![Download APK](https://img.shields.io/badge/Download_APK-FIRE__TV__CUSTOM__HS__VER2.apk-2ea44f?style=for-the-badge&logo=android)](https://github.com/Manju5555/CUSTOM-FIRE-LAUNCHER-with-CLAUD/releases/download/v2.0.0/FIRE_TV_CUSTOM_HS_VER2.apk)

---

## 🖼️ Home Screen Preview

![Fire Home Screen](HOME_SCREEN_PREVIEW.jpg)

---

## 📊 Performance Comparison: Fire Home (Ver 2.0) vs. Stock Amazon Fire TV Launcher

*Real benchmarks measured directly on Amazon Fire TV (Fire OS 11 / Android 11):*

| Feature / Metric | 🚀 **Fire Home Ver 2.0 (`com.prasad.firehome`)** | 🐢 **Stock Fire TV Launcher (`com.amazon.tv.launcher`)** | 🏆 Winner / Difference |
| :--- | :--- | :--- | :--- |
| **APK Size on Disk** | **`37 KB`** (`0.036 MB`) | **`27,000 KB`** (`27 MB` base APK + >100 MB ad modules) | **730× smaller** |
| **RAM Usage (PSS)** | **`~52 MB`** (`53,572 KB`) | **`~133 MB – 220+ MB`** (`136,390 KB` idle, >220 MB with video ads) | **Saves ~80–160 MB of RAM** |
| **Swap Memory Used** | **`0.4 MB`** (`455 KB`) | **`33.3 MB`** (`34,133 KB` thrashing flash storage) | **98% less swap wear** |
| **Home Screen Load Speed** | **`~50 ms` (Instant)** | **`800 ms – 2,500 ms`** (lags while loading ad carousels) | **15× to 40× faster** |
| **Ads & Video Banners** | **0 Ads** (100% clean) | **60–70% of screen is ads** + auto-playing video/audio trailers | **Zero clutter or bandwidth waste** |
| **Background Data Usage** | **`~1 KB` every 30 min** (Live weather only) | **Continuous MBs/hour** (video ads, Prime carousels, telemetry) | **Almost zero network drain** |
| **Apps Visible at Once** | **15 large 16:9 cards** (`5 × 3` full grid on 1 screen) | **Only 6 tiny square icons** in a single cramped row | **2.5× more apps visible immediately** |
| **RAM & Background Closer** | **1-Click `🧹 Clean RAM` + Auto Force-Stop** on top bar | None (requires digging 5 menus deep per app) | **Built-in 1-click cleaner** |
| **Visual Customization** | **15 Dual-Color Wallpapers + `🌗 Auto Day/Night`** + **5%–50% Rounding** + **`1.08×` Focus Zoom** | Locked (cannot change wallpaper, icon shape, or hide sponsored rows) | **Fully customizable** |
| **Screensaver & Burn-In Protection** | **5-Min Idle Ambient Clock + Mysore Weather Drift** | Shows sponsored ad screensavers | **Zero-ad OLED/LED protection** |
| **Physical Netflix Remote Button** | **Remapped to launch `NetMirrorTV` (`app.netmirror.newtv`)** | Hard-locked to Netflix / Amazon Appstore download page | **1-click custom streaming launch** |

### 💡 Why Fire Home Feels So Much Faster
1. **Zero Heavy Frameworks**: Written in pure native Android Java (`HomeActivity.java` & `HomeAccessibilityService.java`) with zero third-party bloat libraries—keeping the entire signed Version 2 APK at just **37 KB**.
2. **No Auto-Play Video Decoder**: The stock launcher constantly runs a background hardware video decoder to play top-banner movie ads, consuming RAM and causing remote-control input lag. **Fire Home** frees that memory completely for your streaming apps.

---

## ✨ What's New in Version 2 (`FIRE TV CUSTOM HS VER2`)
- **Smooth `1.08×` Pop-Out Focus Zoom & 3D Elevation**: Focusing any app card or top-bar pill smoothly scales it up (`1.08×` in `150ms`) with a crisp cyan glow and elevated 3D shadow.
- **Auto-Extracted Brand Gradients for Sideloaded Apps**: Sideloaded apps without wide TV banners (`CRICFy TV`, `PLAYFy TV`, `Aptoide`, `TV Bro`, `Amazon Silk`) automatically sample the dominant brand color from their app icon to generate a rich custom 2-tone card background instead of plain dark grey.
- **`🌗 Auto Day / Night Wallpaper Mode`**: Automatically switches between a bright **Emerald Mint & Deep Teal** theme during the day (6 AM – 6 PM) and a relaxing **Electric Violet & Deep Plum** theme at night (6 PM – 6 AM), alongside all **15 Dual-Color Wallpapers**.
- **5-Minute Idle Ambient Anti-Burn-In Screensaver**: After 5 minutes of remote inactivity on the Home screen, the UI fades to a pure black OLED-safe canvas with a slowly drifting minimalist **Big Clock + Date + Live Mysore Weather** widget. Pressing any remote button wakes the Home screen immediately.
- **Physical Netflix Remote Button → `NetMirrorTV` Remap**: Pressing the dedicated **Netflix** button on the Fire TV remote (`KEYCODE_APP_1` / `com.amazon.venezia` fallback) is intercepted by `HomeAccessibilityService` and launches **`NetMirrorTV`** (`app.netmirror.newtv`) directly.

---

## 🛠️ Core Features
- **5-Column Wide 16:9 Banner Grid**: Displays all TV and sideloaded apps in a clean 5-apps-per-row wide rectangular banner layout.
- **Adjustable App Icon Edge Rounding (5% to 50%)**: Round all app cards, full TV banners, inner icons, and focus borders between **5%** and **50%** (in 5% steps) directly from **⚙️ Settings** or **☰ Options**.
- **15 Refreshing Dual-Color Wallpapers + Custom Image Support**: Built-in `🎨 Choose Dual-Color Wallpaper` picker with `🌗 Auto Day / Night Mode`, 15 vibrant 2-tone gradients, and custom `wallpaper.jpg` support.
- **Top Action Bar**:
  - **Clock & Date** on the top-left.
  - **Live Weather** (Default: **Mysore**, `12.2958, 76.6394`) via Open-Meteo.
  - **`🧹 Clean RAM`**: Clears background memory and automatically guides through Force-Stopping any running background user apps (`FLAG_STOPPED`) with `"Force stop"` pre-highlighted.
  - **`📶 Wi-Fi`**: Opens Fire TV Network / Wi-Fi settings.
  - **`⚙️ Settings`**: Opens Dual-Color Wallpaper Picker, Icon Edge Rounding (`5%–50%`), Ambient Screensaver (`🌙 Start Ambient Screensaver Now`), Fire Home Options, or Fire TV System Settings.
  - **`☰ Options`**: Switch dual-color wallpapers, adjust Icon Edge Rounding (`5%–50%`), start the Ambient Screensaver, apply custom `wallpaper.jpg`, unhide apps, or refresh weather.
- **Default Home & Netflix Button Interception**: Built-in `HomeAccessibilityService` intercepts the Fire TV Home button (opening **Fire Home**) and the physical Netflix button (opening **NetMirrorTV**) without needing Launcher Manager Mini.

---

## 🚀 Quick Install on Fire TV
1. **Direct APK Download**: Download **[`FIRE_TV_CUSTOM_HS_VER2.apk`](https://github.com/Manju5555/CUSTOM-FIRE-LAUNCHER-with-CLAUD/releases/download/v2.0.0/FIRE_TV_CUSTOM_HS_VER2.apk)** and sideload it onto your Fire TV.
2. **Or One-Click PC Installer**:
   - Enable **ADB Debugging** on your Fire TV (*Settings → My Fire TV → Developer Options → ADB Debugging*).
   - Double-click **`INSTALL_ON_FIRETV.bat`** (or run `install_to_firetv.ps1 -FireTvIp <YOUR_FIRE_TV_IP>:5555`).
