# Changelog

All notable changes to Wishy Browser will be documented in this file.

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
