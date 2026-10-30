# 4.0.0
- Engine switched from GeckoView to the system WebView. APK ~3-5 MB (was ~55-70 MB), installed ~7-11 MB (was ~140-190 MB).
- Removed uBlock Origin; added built-in domain blocking, ad-frame hiding and the YouTube ad skipper.
- Fixed: first page no longer waits for extensions to install; WebView is hosted once and rebuilt only if the renderer dies.
- minSdk 24 (was 26). One APK for all CPUs (flavors removed).

# 4.0.1 - flicker and pointer fixes
- Pointer is now a native View on top of the page (GPU-moved bitmap). No Compose layer, no endless animation, no per-frame recomposition.
- Page and pointer share one container, so the pointer is drawn by the same pass as the page.
- Toolbar no longer changes height while a page loads (the loading bar has a fixed slot), so the WebView is not resized and flashed at every load.
- Page previews use PixelCopy (GPU, async) instead of drawing the WebView on the UI thread; they run once, 3.5 s after load, only when idle.
- Pointer moves faster from the first press, scrolls every frame at the screen edge, and no longer polls the page while moving; link snapping happens once after you stop, and only within a small radius.
- Injected script runs once per page (it used to start duplicate timers and observers three times).
- Progress updates are de-duplicated; the reader check runs after the page settles.

# 4.1.0 - search, privacy and navigation
- Removed the 45 code-drawn animated cursors. Pointers are now pictures only: the open-source pack in assets/cursors and the ones you add. A plain still arrow is used only when no picture can be loaded.
- DuckDuckGo is the default search engine. Pick another (Startpage, Brave, Qwant, Mojeek, Ecosia, Google, Bing) on the Privacy page.
- New Privacy page: 14 switches, all on by default (ads and trackers, cookie pop-ups, pop-ups, autoplay, third-party cookies, tracking links, referrer, GPC/DNT, fingerprint protection, common identity, HTTPS upgrade, location, saved forms, wipe at exit) plus Lite mode (off).
- New: privacy.js (canvas/battery/CPU fingerprint noise, noreferrer on outbound links, cookie-banner hiding), injected at document start through androidx.webkit when the WebView supports it.
- New: Clear browsing data now (cookies, storage, cache, history, previews), Find in page (Ctrl+F), Copy link (Ctrl+Shift+C), Alt+Left/Right back and forward, Home/End, F6 address bar.
- Desktop site and Reduce motion are on by default for new installs.
- New: "new version" card. Once a day Wishy asks the public GitHub releases of wishissue/WishyBrowser for the latest tag and, if it is newer, shows one card with OK. Nothing is downloaded. Off switch: Settings > Updates. Publish releases with tags like v4.2.0.
- New: "buy me a coffee" card, 1 month after first use and then every 3 months, with Buy me a coffee (donation page), Show QR code and Maybe later.

# 4.2.0 - layouts and downloads
- Toolbar position: top, bottom, left or right (Settings > Layout). Left and right use a column of icon buttons; the address opens as a sheet.
- Full-screen mode: toolbar and system bars hidden. Bring the toolbar back by dragging in from the edge (touch), pushing the pointer against that edge, pressing SEARCH, or holding MENU. It floats over the page, so the page is never resized, and hides itself again.
- Downloads: new download manager (OkHttp). Pause, resume (also after a dropped connection), cancel, retry, progress notification, saved to the Downloads folder. Needs your OK first (new privacy switch "Ask before downloading").
- Download privacy: cookies are read at request time and never stored; each redirect only gets its own cookies; https-to-http redirects are refused; referrer and Global Privacy Control follow the existing switches; the list is wiped with "Wipe data at exit" and "Clear data".
- Fixed: the pointer can now reach the bottom edge of a page that cannot scroll further.
- Not yet: blob: downloads (files a page builds in memory), installing APKs from the browser.
- UI aligned with the WishIssue design guidelines: Wishy Blue now uses the shared tokens (primary #1560D4 / #A8C8FF, background #E6F1FF / #080B12, cards one step off the background); Plus Jakarta Sans for the interface and Bagel Fat One for the app name (static Latin subsets, smaller than the old Poppins files).
- Fixed: the home-screen clock and any text outside a card was black on dark themes; the default text colour now follows the theme.
- Removed the gradient and the drifting shapes from the home screen, the gradient banner on update/donate cards, and the gradient on page-preview placeholders.
- Home clock redesigned: larger time, system 12/24-hour setting, small AM/PM, date and greeting, updates on the minute.
- Build: one universal APK (the app has no native code, so per-CPU splits were pointless); release signing now uses your own key when WISHY_KEYSTORE settings or the GitHub secrets exist (README > Release signing). Note: the first APK signed with a new key cannot update a copy signed with the old debug key; uninstall once.
- Size: removed two unused 1.3 MB logo images and shrank the launcher logo from 3464 px (1.3 MB) to 432 px WebP.
