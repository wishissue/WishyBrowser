# Wishy Browse 4.0 – build and test notes

**Status: source complete, NOT yet built or run on a device.** The 4.0 code was written in an environment
without the Android SDK, so Gradle was never run. What *was* checked: the `engine/` and `downloads/` packages and
`BrowserController.kt` compile against the Android API 34 jar (with stubs for Compose); the blocker was run against the real
99,818-domain list (every domain found, no false positives on common sites); the filename sanitiser and disguised-file
detector were run on tricky names (path tricks, right-to-left override, `.pdf.apk`). Everything else (Compose pages, manifest,
Gradle) is unbuilt. Expect a few compile errors on first sync and send them over.

A GitHub Actions workflow (`.github/workflows/build.yml`) builds the release APK and **fails if it is over 10 MB**.

## Build
1. Open the folder in Android Studio (recent Panda/Narwhal or newer; the project pins AGP 9.4.1, Gradle 9.7.1, Kotlin 2.4.20, JDK 17+).
2. Build > Generate Signed App Bundle or APK > APK, or run `./gradlew assembleRelease`.
3. The APK is copied to `dist/` and its size is printed in the Gradle output (`APK: ... MB`).
   (It is signed with the debug key so it installs directly.)

If sync fails on a version number (AGP, Compose BOM, material3 beta), change it to the newest one Android Studio offers.

## Size
Not measured. Estimate for the single universal APK: **about 5–8 MB** (Compose + Material 3 after R8,
about 0.8 MB compressed blocklist, icons/fonts/cursors). No native libraries remain. If it comes out above 10 MB:
1. `androidResources { localeFilters += "en" }` in app/build.gradle.kts (drops unused translations).
2. Check `app/src/main/res/font` and keep only the Poppins weights actually used.
3. Trim `tools/build_blocklist.py` output (e.g. keep only EasyPrivacy + the top ad domains).
Use Build > Analyze APK to see what is big.

## Test checklist (on a real TV)
- Open github.com, scroll with CH+/CH-, move the pointer, click links.
- YouTube: fullscreen video, Back exits fullscreen.
- Downloads: open any file link; pause/resume/cancel/retry; open a PDF/image; download a `.apk` and check the confirmation, then the Android installer.
- Kill the WebView renderer (chrome://crash is blocked; use `adb shell am kill` on `com.google.android.webview:sandboxed_process*`) and check the page recovers.
- Shields page: run the ad-block test link; Clear cookies; restart and confirm you are logged out of sites.
- `http://neverssl.com` should open (upgrade fails -> falls back with a warning).
- Themes and cursors still switch (28 pictures: Bibata, Vimix, Layan, Capitaine).
- Shields page > "This site": turn JavaScript off for a site and confirm it reloads without scripts; set camera/microphone to Block/Allow and test on a video-call site.
