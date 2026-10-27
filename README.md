<div align="center">

<img src="images/banner.png" alt="Wishy Browser" width="100%">

# Wishy Browser

**A browser for the biggest screen in your home — without the usual baggage.**

A lightweight, privacy-first browser built for Android TV and Fire TV. Navigate with your remote, watch videos on the big screen, and keep browsing data off your device after each session.

[Features](#features) · [Screenshots](#screenshots) · [Install](#install) · [Build](#build-from-source) · [Privacy](#privacy)

[![Android](https://img.shields.io/badge/Android_TV-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://www.android.com/tv/)
[![Kotlin](https://img.shields.io/badge/Kotlin-Android-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/)
[![License: GPL v3](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/wishissue/WishyBrowser?style=flat-square)](https://github.com/wishissue/WishyBrowser/stargazers)

</div>

---

## Your TV. Your browser. Your business.

Most TV browsers feel clunky, ship more than you need, or make privacy hard to understand. Wishy Browser is built around a simpler idea: open a page, use it with your remote, and leave without a trail of local browsing history.

- **Designed for the couch** — remote-first controls, D-pad navigation, and a pointer mode for websites that expect a mouse.
- **Private by default** — each session starts fresh; browsing history, cookies, and form data are not kept between launches.
- **No accounts or analytics SDKs** — no sign-in flow and no app analytics.
- **Open source** — inspect the code, build it yourself, or help improve it.

## Screenshots

<!-- Keep these paths aligned with the real files in the repository's images/ directory. -->
<div align="center">
  <img src="images/home.png" alt="Wishy Browser home screen" width="31%">
  <img src="images/browser.png" alt="Wishy Browser browsing a website" width="31%">
  <img src="images/video.png" alt="Video playback in Wishy Browser" width="31%">
</div>

## Features

### Privacy and protection

| Feature | What it does |
| --- | --- |
| Private sessions | Browsing history, cookies, and form data are cleared rather than saved between launches. |
| Ad and tracker blocking | Uses request-level domain blocking and hides common ad frames. It is not a full extension-based blocker. |
| HTTPS upgrades | Upgrades HTTP pages to HTTPS where appropriate, while keeping local-network addresses usable. |
| Cleaner links | Removes common tracking parameters from supported links. |
| No account required | No registration, profile, or sync account. |
| No app analytics | No analytics SDK or app-level browsing-history collection. |

### Made for TV

- **Remote-friendly navigation** with D-pad focus and keyboard shortcuts.
- **Pointer mode** for websites that do not work well with directional navigation.
- **Bookmarks** saved locally on your device.
- **Fullscreen video** with remote-friendly exit behavior.
- **Reader View** for a simpler, high-contrast reading experience on supported pages.
- **Customizable appearance** with a range of themes.
- **Smart address bar** that understands URLs, local network addresses, and search queries.

## Install

Wishy Browser is intended for Android TV devices and Fire TV devices running **Android 8.0 (API 26) or newer**.

1. Open the repository's [Releases](https://github.com/wishissue/WishyBrowser/releases) page.
2. Download the APK that matches your device architecture. If unsure, check the release notes for the recommended build.
3. Install it on your TV, streaming box, or Fire TV device. You may need to allow installation from your chosen file manager or downloader.

You can also build the APK yourself using the instructions below.

## Build from source

### Requirements

- Android Studio with a compatible Android SDK
- JDK 17 or newer
- An internet connection for Gradle dependencies

### Build

```bash
git clone https://github.com/wishissue/WishyBrowser.git
cd WishyBrowser
./gradlew assembleRelease
```

On Windows, use:

```bat
gradlew.bat assembleRelease
```

The project includes a GitHub Actions workflow that can build APKs for you. Check the **Actions** tab for the latest successful build and its downloadable artifacts.

## Privacy

Wishy Browser is designed to avoid keeping a browsing trail on your device. It does not provide anonymity: websites you visit can still see your connection and IP address, and network services used by the browser engine may make their own requests.

A few practical things to know:

- **No saved browsing history** — sessions are cleared instead of persisted.
- **No account or sync** — bookmarks and preferences stay local.
- **Privacy protections can affect websites** — strict blocking or anti-fingerprinting can sometimes break sign-in flows, embedded content, or other site features.
- **Streaming compatibility varies** — services that require DRM may not work.
- **Engine updates matter** — browser security and compatibility depend on the Android System WebView version installed on your device.

For the exact network behavior and known limitations of the current version, see the project documentation and [issue tracker](https://github.com/wishissue/WishyBrowser/issues).

## Known limitations

Wishy Browser is a personal open-source project, and device compatibility is still being tested across different TVs and streaming boxes.

- It is designed for a TV screen, not as a phone-first browser.
- Older devices running Android versions below 8.0 are not supported by the current engine.
- Very low-memory devices may struggle with large websites.
- Some websites, video players, or login systems may behave differently from desktop browsers.
- Extensions, browser tabs, sync, and a traditional downloads manager are not part of the current feature set.
- WebView-based blocking is less comprehensive than uBlock Origin; DNS-over-HTTPS is not configured per app.

If something breaks on your device, please report it with your device model, Android version, steps to reproduce, and a screenshot or log if possible.

## Tech stack

| Part | Technology |
| --- | --- |
| Language | Kotlin |
| Interface | Jetpack Compose |
| Design system | Material 3 |
| Browser engine | Android System WebView |
| Build system | Gradle |

> The current version uses Android System WebView. Unlike GeckoView, WebView does not support browser extensions, so ad blocking is more limited.

## Contributing

Bug reports, testing, ideas, and pull requests are welcome.

1. Check existing [issues](https://github.com/wishissue/WishyBrowser/issues) before opening a new one.
2. Include your TV or streaming-box model and Android version.
3. Explain what you expected and what happened.
4. Add screenshots or logs when possible.

## License

Wishy Browser is licensed under the **GNU General Public License v3.0**. See [`LICENSE`](LICENSE) for the full license text.

---

<div align="center">

**Made for the couch. Built in the open.**

[Repository](https://github.com/wishissue/WishyBrowser) · [Releases](https://github.com/wishissue/WishyBrowser/releases) · [Report a bug](https://github.com/wishissue/WishyBrowser/issues)

</div>
