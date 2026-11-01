# Changelog

## 4.0.0 (unreleased, not yet built)
- **Engine replaced**: GeckoView -> Android System WebView, behind a `BrowserEngine` interface (`engine/`). No native libraries are bundled.
- **Blocker replaced**: bundled uBlock Origin -> native domain blocker + generic cosmetic CSS.
- **New Downloads manager**: pause/resume/cancel/retry/remove, scoped storage (MediaStore), safe names, APK confirmation + Android installer.
- **Shields page** now lists what is on, partial and unavailable.
- Removed: Gecko-only features (fingerprint resistance, Total Cookie Protection, DoH, uBlock). See PRIVACY_REPORT.md.
- **Cursors**: code-drawn animated cursors removed; 28 open-source pictures in 4 packs (13 new: Vimix, Layan, Capitaine). Add-your-own and URL import kept.
- **Per-site settings**: JavaScript and camera/microphone per site.
- Back on a page dialog now answers the page instead of leaving its JavaScript waiting.


All notable changes to Wishy Browse will be documented in this file.

## [3.0.0] (work in progress)
### Rebuilt
- The entire interface is now **Jetpack Compose + Material 3 Expressive**; every old XML screen, dialog and view was deleted. New home screen (clock, search pill, shortcut tiles), floating toolbar with a wavy load indicator, side menu, full-screen pages, spring animations everywhere and a D-pad focus ring on every control.
- **45 real colour themes** generated with Material Color Utilities (`tools/gen-palettes.mjs`), each in Dark, Light, Dusk and AMOLED. The first 15 reuse Orion Store's palette seeds (credited in NOTICE.md).
- **45 animated cursors** (9 shapes x 5 animations) drawn in code with the theme colours; the pointer ripples where OK is pressed.
- New pages: Bookmarks, Themes, Cursors, Settings, Shields, Developer, Credits, Donate (QR code from `tools/gen_qr.py`).
- Web-page dialogs (alerts, confirms, prompts, camera/microphone permission) are Material 3 dialogs.
### Fixed
- **Ads and trackers were not blocked.** Two causes: (1) uBlock Origin and Wishy Core were never allowed to run in private browsing, and every session is private (reproduced on desktop Firefox: ads leak until the extension is allowed); (2) the extensions were re-installed on every launch, so uBlock started cold each time. They are now installed once (`ensureBuiltIn`) and explicitly allowed, and the first page loads only after the blocker is up.
- The pointer's link-snapping now attaches to the extension that is actually running.
- Launcher icon: blue gradient instead of the default green grid; wrong monochrome layer removed; TV banner and Play Store icon rebuilt.
### Performance and stability
- **Crash recovery actually recovers.** After a web-process crash or an Android low-memory kill Gecko closes the session, so the old `reload()` did nothing and the page stayed blank. The session is now reopened, and a page that crashes 3 times in a minute sends you home instead of looping.
- **No automatic full-resolution screenshot on low-RAM TVs** (2 GB or less); it ran when a heavy page like GitHub was at its largest. Previews are still taken when the menu opens or a page is bookmarked. In-memory previews are dropped when Android asks for memory.
- **Pointer no longer recomposes every frame** and stops animating when idle.
- **Blocker is lighter:** the three AdGuard lists (not bundled, downloaded and compiled on the TV) are off by default again, as in stock uBlock Origin. Existing installs get the new defaults (SHIELDS_PROFILE 4).
- **Reduce motion is on by default on weak TVs** (about 2 GB RAM or less), and the page-load indicators are the plain Material ones instead of the always-animating Expressive wavy ones.
- **Built-in crash reason.** On the next launch a card tells you why the last run ended (Android ran out of memory, browser engine crash, app stopped responding...), read from Android's own exit history (Android 11+).
- Removed `gfx.webrender.all` (forced WebRender past Mozilla's GPU blocklist).
- Wishy Core runs in the top frame only (it opened one native port per ad/embed iframe, and the app talked to whichever connected last), snaps the pointer using CSS-pixel coordinates (screen pixels were compared with CSS pixels), only considers links on screen, and its focus outline is no longer lost at `document_start`.
### Changed
- Bundled uBlock filter lists refreshed (`tools/update_lists.sh`); translations trimmed to English.
- Removed AppCompat, RecyclerView and Material Components; dead code and resources deleted.
### Known limits
- Written without access to an Android SDK, so it has not been compiled or run on a device by the author of this change. Build it and report any error.

## [v2.2.0]
### Added
- Bundled **uBlock Origin (v1.75.0)** as a mandatory privacy layer.
- Gecko content process and memory tuning preferences for low-RAM TVs.
- **HTTPS-Only Mode** and **DNS-over-HTTPS** for enhanced security.
- **Reader View** support with automatic detection and menu integration.
- New **Spatial Navigation Engine** (Wishy Core extension) for robust D-pad web traversal.
- **Magnetic Cursor Snapping** to interactive elements in pointer mode.
- Overscan safety margins (5% inset) and enlarged tap targets for TV usability.
- Branch-based development for improvement plan phases.

### Changed
- Removed unused code and resources (dead helper, unused strings, colour and drawable); downsized the 1254 px launcher art to 512 px.
- Enabled **R8 Full Mode** for better code shrinking.
- Pruned redundant resource densities to reduce overall package size.
- Set `debugSymbolLevel = NONE` for release builds.

## [v2.1.0]
### Added
- Three product flavors: `arm32`, `arm64`, and `universal`.
- Support for x86_64 emulators via `wishy.includeX86` flag.
- Memory optimization tuning for weak TV hardware.
- Experimental uncompressed packaging option.

## [v2.0.0]
- Initial release based on GeckoView 155.

## 4.0.0 fixes
- Pointer could stick near the top or bottom of a page: edge scrolling now reads WebView's live scroll position instead of a cached value.
- Link snapping now waits until the pointer has been still for 300 ms, ignores answers that arrive after you moved again, and no longer queries the page while you are moving.
