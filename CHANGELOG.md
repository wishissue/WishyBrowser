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

# 4.3.0 - ad blocking and privacy
- YouTube: ad data (adPlacements, playerAds, adSlots) is removed before the player reads it, in the page's first data, in fetch() answers and in JSON.parse, at document start in every frame. Feed, search, banner and companion ads are hidden (desktop and m.youtube.com). Fallback: Skip is clicked and an ad that still starts is muted and run to its end. YouTube's "ad blockers are not allowed" pop-up is removed and the video resumes. YouTube's ad and ad-measurement requests are blocked (/pagead/, /api/stats/ads, /ptracking, /get_midroll_, ad_break).
- Other sites: ad containers are hidden (new switch "Hide ad spaces"); bait class names that make sites show "turn off your ad blocker" walls are left alone on purpose; adsbygoogle and googletag are replaced by empty stand-ins so blocked ads do not break page scripts.
- Network: ad paths on google.com, Facebook (pixel), X and anywhere /pagead/ads and /gampad/ads, on top of the host list.
- Shields on this site: a per-site switch for pages broken by blocking.
- New privacy switches: Block YouTube ads, Hide ad spaces, Block WebRTC leaks (off by default). Fingerprint protection now also covers the graphics card name and audio. Link pings (<a ping>) are removed when "Clean tracking links" is on.
- The old YouTube skipper and ad CSS in wishy.js moved into adblock.js.

# 4.3.1 - YouTube ad blocking rebuilt
- YouTube: ad data is now removed on every path it can arrive by: the page's first data (player response and home data), fetch() as JSON and as text, XMLHttpRequest (text and JSON), and JSON.parse. Before, only the first player response and fetch().json() were covered, so m.youtube.com-style XHR answers and text answers still carried ads.
- YouTube: ads are removed from the data deep inside feed, search, side-bar and masthead answers (not only hidden afterwards), and sections that only held ads are dropped. The ad CSS stays as a second net.
- YouTube: the "ad blockers are not allowed" message data is removed before it can show, and nothing may record that an ad blocker was found.
- YouTube: the fallback reacts on the video's own events instead of waiting up to 250 ms, and it never seeks a long video (the old fallback could jump a whole video to its end when YouTube left the ad flag set).
- Other sites: more ad containers and ad-verification frames hidden (amp-ad, ad-verification and ad-exchange iframes); more Google ad paths blocked (/pagead/ anywhere, /pcs/activeview, GPT scripts).
- Tests: tools/adblock-test runs the ad script in a simulated browser against YouTube-shaped data (64 checks) and checks the request rules on a plain JVM (52 checks). AdRules no longer depends on android.net.Uri so it can be tested without a device.
