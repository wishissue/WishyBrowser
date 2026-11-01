# Privacy report – Wishy Browse 4.0 (Android System WebView)

Legend: **Code-enforced** = implemented in this source; not yet tested on a TV. **Partial** = works, with stated limits.
**Unavailable** = cannot be done honestly with WebView.

## Code-enforced
| Protection | How |
|---|---|
| Ad/tracker domain blocking | `engine/Blocker.kt`: ~99.8k domains from EasyList + EasyPrivacy; blocks third-party sub-resources in `shouldInterceptRequest` |
| HTTPS upgrade | `http://` main-frame loads on public hosts are upgraded; if it fails Wishy falls back to HTTP and shows a warning |
| Tracking-parameter removal | `utm_*`, `fbclid`, `gclid`, `msclkid`, `mc_eid`, etc. removed on top-level navigations |
| Certificate errors | `onReceivedSslError` always cancels and shows an error page; no "proceed" path exists |
| Third-party cookies | `setAcceptThirdPartyCookies(false)` |
| WebView hardening | file/content access off, mixed content blocked, geolocation off, no `addJavascriptInterface` anywhere, no form/password saving |
| Pop-ups | only with a user gesture, and they open in the same tab |
| External app links | non-http(s) schemes are blocked with a notice |
| Camera/microphone | ask every time (DRM/EME auto-granted) |
| Data | cookies, storage and cache wiped at start and exit and by "Clear data"; no history; no analytics/accounts/background services |
| Per-site controls | JavaScript on/off and camera/microphone Ask/Allow/Block per site (Shields page). Only host names are stored; "Reset all site settings" clears them. Location and web notifications are always off |
| Downloads | sanitised file names, no HTTPS->HTTP redirects, APK/program files and disguised types flagged, confirmation before open/install, Android's own installer, requires "install unknown apps" consent |

## Partial
- **Global Privacy Control**: `Sec-GPC: 1` is sent on top-level loads only (WebView cannot add headers to sub-requests) plus `navigator.globalPrivacyControl`.
- **Ad blocking**: domain rules only. No URL-path rules, no `$script/$image` options, no scriptlets, no dynamic filtering, no custom lists.
- **Cosmetic filtering**: ~2,500 generic selectors injected after the page is visible (a flash of ads is possible).
- **YouTube ad skipper**: best-effort DOM script; can break when YouTube changes.
- **Private browsing**: WebView writes to disk during a session; data is wiped on exit and again on next launch. It is not RAM-only.
- **Safe Browsing**: if the TV has Google Play services, WebView may query Google's Safe Browsing service.
- **Reader view**: a simple heuristic extractor, not Mozilla's Readability.

## Unavailable with Android System WebView
- Fingerprint resistance (the user-agent is normalised only; canvas/audio/font/screen fingerprints are NOT randomised or blocked). Gecko's resistFingerprinting has no WebView equivalent, and JS patching would only be an illusion.
- Total Cookie Protection / state partitioning control.
- Encrypted DNS in-app (uses the TV's DNS; no custom DNS/VPN was added on purpose).
- uBlock Origin compatibility.
- Web notifications/push (WebView has no support). HTTP basic-auth pop-ups are not supported (the load is cancelled).
- Engine updates depend on the TV's WebView version; very old TVs may ship an old, less secure WebView.
