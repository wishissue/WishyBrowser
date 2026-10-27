# Wishy Browser 4.0 (WebView edition)

Same name, same look and layout as Wishy Browser 3.x, rebuilt on the Android System WebView instead of GeckoView.

* No bundled engine (GeckoView ~50 MB per CPU) and no uBlock extension (14 MB of assets).
* Reused unchanged from 3.x (GPL-3.0): Compose UI, 45 themes, icons, bookmarks, previews, prefs, spatial-navigation and YouTube ad-skipper scripts.
* New: `Engine.kt` (blocklist, privacy helpers, user agent), `BrowserController.kt` (WebView host, pointer, dialogs, crash recovery), `assets/blocklist.txt`, `assets/wishy.js`.

## Build
```
./gradlew assembleRelease      # one APK for every CPU, copied to dist/
```
Needs JDK 17+ and the Android SDK (compileSdk 37). Not compiled by the author of this change: build it and report any error.

## Privacy switches (Privacy page; `Opt` in `Engine.kt`, applied in `BrowserController.applyOpts`)
All on by default and individually switchable. JavaScript file/content access off, geolocation off, saved forms and passwords off, mixed content blocked, third-party cookies blocked,
Safe Browsing and WebView metrics off (manifest), common user agent, Sec-GPC and DNT headers, https upgrade (not for LAN hosts),
tracking parameters stripped, pop-ups only on a user click, camera/mic only after a prompt, cookies/cache/history wiped at start and exit.

## Honest limits
* WebView cannot run extensions, so blocking is request-level (domain list) plus hiding common ad frames. It is weaker than uBlock Origin.
* Headers (GPC/DNT) are added to page loads only; WebView cannot add them to sub-requests.
* DNS-over-HTTPS cannot be set per app; use Android's Private DNS setting.
* Page quality depends on the TV's WebView version (update "Android System WebView" if you can).

## Cursors
Pictures only. Put the Bibata pack (or any pack with an `index.json`) in `app/src/main/assets/cursors/`; the pack is not part of this source drop, so until it is added only cursors you add yourself (file or web address) are listed, and a plain arrow is the fallback.

## Network calls the app makes on its own
Only one, and it can be switched off (Settings > Updates): a daily GET to `api.github.com/repos/wishissue/WishyBrowser/releases/latest` to learn the newest version number. No identifiers are sent. Cursor pictures are fetched only when you add one from a web address.
