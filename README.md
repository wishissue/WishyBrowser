<p align="center">
  <img src="images/cat.png" alt="Wishy Browser logo" width="140">
</p>

<h1 align="center">Wishy Browser</h1>

<p align="center">
  <b>A minimal, privacy-first web browser for Android TV.</b><br>
  Built on GeckoView. Designed for the remote. No history, no telemetry, no accounts.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/platform-Android%20TV%20%7C%20Fire%20TV-3ddc84?logo=android&logoColor=white" alt="Platform">
  <img src="https://img.shields.io/badge/engine-GeckoView-ff7139?logo=firefoxbrowser&logoColor=white" alt="Engine">
  <img src="https://img.shields.io/badge/language-Kotlin-7f52ff?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/UI-Material%203-6750a4?logo=materialdesign&logoColor=white" alt="Material 3">
  <img src="https://img.shields.io/badge/license-GPLv3-blue" alt="License">
</p>

---

## Screenshots

### Home
![Wishy Browser home screen](images/wishy%20home.png)

### Search
![Searching in Wishy Browser](images/wishyserch.png)

### Video on the big screen
![Watching YouTube in Wishy Browser](images/youtubewishy.png)

---

## Why Wishy?

Most TV browsers are either bloated or track you. Wishy does one thing: gives you a search bar, a page, and just enough controls to be comfortable from the couch.

- **Private by default.** Every session runs in private mode. Nothing is written to disk.
- **Made for the remote.** D-pad navigation, plus a virtual mouse pointer for regular websites.
- **Small and auditable.** One Activity, one layout, no tracking SDKs.

---

## Features

### Privacy and security

| Feature | Details |
| --- | --- |
| **Tracker and ad blocking** | Enhanced Tracking Protection set to *Strict*, always on |
| **Anti-fingerprinting** | Normalizes fonts, canvas, timezone and other identifying surfaces |
| **Cookie protection** | Third-party and tracker cookies are rejected |
| **No history** | Private-browsing mode for every session, nothing persists across launches |
| **No telemetry** | No Glean, no crash reporter, no analytics SDK |
| **Global Privacy Control** | Every site is told not to sell or share your visit |
| **Clean links** | Tracking parameters (`utm_*`, `fbclid` and similar) are stripped from links |
| **Nothing phones home** | Telemetry, studies, push messaging, captive-portal checks, notifications and location are switched off |
| **No proprietary code** | Google Play Services (normally pulled in by GeckoView for passkeys) is left out. The APK contains only open-source components |
| **No accounts** | No sign-in and no Firefox Account or Sync code |

### Browsing

- **Smart address bar:** `192.168.1.10:8080` opens over HTTP (routers, NAS, Plex), domains open over HTTPS, and anything else becomes a search.
- **Bookmarks:** star button, remote-friendly list, duplicate-safe, stored only on your device.
- **Menu:** back, forward, reload, home, custom home page, desktop-site toggle, and text size (100% to 200%).
- **Fullscreen video:** the bar hides and Back exits fullscreen.
- **Load progress line:** tells you a slow page from a dead one.
- **New-tab links:** links that try to open a new window load in the current tab instead.

### TV-first design

- Material 3 dark theme with a solid background (no feeds, wallpapers or recommendations).
- White focus outline on every button the remote lands on.
- Visible cursor on both light and dark pages.

---

## Using it with a remote

| Do this | To get this |
| --- | --- |
| **★** button in the address bar | Bookmark or un-bookmark the current page (star turns yellow when saved) |
| **Bookmarks** button (or the remote's BOOKMARK key) | Open the list: **OK** opens, **Right** deletes, **Back** closes |
| **⋮** button | Menu: Back, Forward, Reload, Home, *Set this page as home*, Desktop site on/off, Text size, Exit |
| **MENU** key or pointer button | Toggle **pointer mode**: D-pad moves the cursor, **OK** clicks, hold a direction to speed up |
| Cursor pushed to the top or bottom edge | Scrolls the page; push **Up** at the very top to reach the address bar |
| **CH+ / CH−** or **Page Up / Down** | Scroll a screenful |
| **BACK** | Closes fullscreen video, then pointer mode, then goes back a page, then jumps to the address bar. Press Back again to exit |

**Keyboard shortcuts:** `Ctrl+D` bookmark · `Ctrl+B` bookmarks · `Ctrl+L` address bar · `Ctrl+R` / `F5` reload

> **Tip:** TV-optimized sites work with the D-pad out of the box. For ordinary desktop or mobile sites, press **MENU** to switch on the pointer.

---

## Getting started

### Easiest: let GitHub build it for you

Push the project to GitHub. The included workflow (`.github/workflows/build.yml`) builds the APK on every push. Open the **Actions** tab, pick the latest run and download **WishyBrowser-apk**. Push a tag such as `v2.1.0` and the APK is also attached to a GitHub Release.

### Build it yourself (one click)

Requirements: the latest stable **Android Studio** (it bundles JDK 21 and offers to install the Android SDK 37 platform on first sync) and an internet connection.

1. Open the project folder in Android Studio and let the first Gradle sync finish (it downloads the GeckoView engine once).
2. Choose **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
3. Your installable APK is copied to **`dist/`** in the project folder. Every build type is shrunk and signed, so any of them installs directly.

Command line: `./gradlew assembleRelease` (Windows: `gradlew.bat assembleRelease`). Use JDK 17 or newer.

### Install on the TV

```
adb connect <tv-ip-address>
adb install -r dist/app-release.apk
```

Or copy the APK to a USB stick, or use the Downloader app, and open it on the TV.

### What the build gives you

- **Three Product Flavors (`arm32`, `arm64`, `universal`):**
  - **`arm32`**: Targeted for older/cheaper sticks and boxes (e.g., Fire TV Stick Lite). APK size is compressed down to ~86 MB.
  - **`arm64`**: For high-end TVs and streaming devices (e.g., Nvidia Shield, newer Google TVs). APK size is compressed down to ~89 MB.
  - **`universal`**: Contains both ARM architectures, offering easiest sideloading compatibility (~157 MB).
- **Experimental Uncompressed Packaging Option:** Passing `-Pwishy.uncompressedLibs=true` stores native libraries page-aligned and uncompressed inside the APK. This allows the Android system to memory-map them directly rather than extracting them at installation, which saves runtime memory but increases the APK file size (~148 MB for arm32, ~185 MB for arm64).
- **Memory Optimization Tuning:** Built-in engine optimizations restrict the back-forward cache to a single page (`browser.sessionhistory.max_total_viewers = 1`), reduce the memory cache to a low 4 MB limit (`browser.cache.memory.capacity = 4096`), and disable prelaunch background helper content processes (`dom.ipc.processPrelaunch.enabled = false`) to stay resilient on resource-constrained 1-2 GB RAM TVs.
- To run on an **x86_64 emulator**, add `wishy.includeX86=true` to `gradle.properties` and build again.
- If you change the GeckoView version, pick one from [maven.mozilla.org](https://maven.mozilla.org/maven2/org/mozilla/geckoview/geckoview/) and check the code still compiles; GeckoView's API changes between major versions.

---

## Compatibility

- **Android 8.0 (API 26) and newer.** This is the floor of the current Firefox engine (Mozilla dropped Android 5 to 7 in Firefox 144). Older Fire TV sticks running Fire OS 5 or 6 cannot run a modern, patched engine and are not supported.
- **Works on Fire TV and generic Android TV boxes**, not just Google-certified devices. `android.software.leanback` is declared `required="false"` so stores don't hide the app from them.
- **Landscape only** for consistent behavior on every TV form factor, including hybrid boxes that default to portrait.
- **Shows up on every launcher.** The app registers for both the Android TV (Leanback) launcher and the standard one, so cheap boxes with a normal launcher list it too.
- **Adaptive icon** plus legacy PNGs for every density.
- **TV banner** is a true 320×180 asset, the exact size the Leanback launcher expects.

---

## What's stored, and what isn't

Wishy stores **only** the following, in the app's private storage, and only when you choose to save them:

- Bookmarks
- Home page
- Desktop-site mode
- Text size

Browsing history, cookies and form data are **never** written to disk.

**Left out on purpose** (to keep the app small and auditable): tabs, a history list, a downloads manager, extensions, and any kind of account or sync.

---

## Under the hood

| Layer | Choice |
| --- | --- |
| **Engine** | [Mozilla GeckoView](https://mozilla.github.io/geckoview/) as a prebuilt Maven artifact (MPL-2.0). No Firefox source is forked, and no Firefox branding, telemetry, experiments or sync code is included |
| **UI** | Single `Activity`, Material Components (`MaterialCardView`, `MaterialButton`, `Theme.Material3.Dark`), Material Icons (Apache-2.0) |
| **Language** | Kotlin |
| **Privacy config** | `BrowserApplication.kt` |
| **Input handling** | `MainActivity.kt` |
| **Bookmarks** | `BookmarkStore.kt` (JSON in SharedPreferences), `BookmarksDialog.kt` (RecyclerView) |

### Developer notes

- **Toolchain:** Gradle 9.7, Android Gradle Plugin 9.4 (Kotlin is built in), Kotlin 2.4, compile SDK 37, target SDK 36, GeckoView 155 (Firefox 155).
- **Key handling** lives in `MainActivity.dispatchKeyEvent` on Android 12 and older, because GeckoView consumes D-pad and Back events before `onKeyDown` runs. On Android 13+ Back goes through the predictive-back `OnBackPressedCallback` instead.
- **Passkeys are off.** GeckoView's WebAuthn code depends on Google Play Services, which is proprietary. This build excludes it (`app/build.gradle.kts`) and disables WebAuthn in `BrowserApplication.kt`, so sites fall back to passwords. To re-enable passkeys, remove the `exclude` and the `security.webauth.webauthn` pref.
- **Backups:** `allowBackup` is off, so bookmarks do not survive an uninstall.
- **Updates:** there is no in-app auto-update. GeckoView does not update independently of your app releases, so rebuild against a newer GeckoView regularly for security fixes.
- **Signing:** builds are signed with the local debug key so they install directly. Use your own keystore if you publish through a store.

---

## Credits and licenses

- Browser engine: [Mozilla GeckoView](https://mozilla.github.io/geckoview/) (MPL-2.0)
- Icons: [Material Icons](https://fonts.google.com/icons) (Apache-2.0)
- UX reference: TV Bro was reviewed for the pointer-mode pattern only. **No TV Bro code is used.**

See [`NOTICE.md`](NOTICE.md) for the full accounting.

---

<p align="center">
  Made for the couch. 🐱
</p>
