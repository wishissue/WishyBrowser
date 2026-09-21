# Third-Party Notices

This app is built on top of open-source components. This file lists every
one of them, why it's here, and under what license — so the project stays
compliant as a whole. None of this project's own code (see LICENSE) was
copied from any GPL-licensed project; TV Bro was used only as a *design
reference* for remote-control UX, not as a source of code, specifically to
avoid pulling GPL-3.0 obligations onto this repo.

## org.mozilla.geckoview:geckoview
- **What it is**: Mozilla's GeckoView — the rendering engine, network
  stack, and web-platform implementation from Firefox, packaged as an
  embeddable Android library. This is the actual "Firefox/Gecko" reuse
  requested: rather than forking Firefox's enormous mozilla-central
  source tree, the app consumes the same official binary artifact that
  Firefox Focus, Firefox Klar, and Fenix (Firefox for Android) themselves
  build on.
- **License**: Mozilla Public License 2.0 (MPL-2.0).
- **Compliance notes**: MPL-2.0 is used here strictly as an unmodified
  binary dependency (via Maven), which does not require this project to
  be relicensed — MPL-2.0's copyleft applies file-by-file to MPL-covered
  source files, and we ship none of GeckoView's source, only the
  published artifact. If GeckoView's source is ever vendored in and
  modified directly, those specific modified files would need to remain
  under MPL-2.0 and their source made available.
- **Source**: https://github.com/mozilla/gecko-dev (mirror of
  mozilla-central) — https://maven.mozilla.org/maven2/org/mozilla/geckoview/

## AndroidX libraries (appcompat, recyclerview)
- **License**: Apache License 2.0.
- **Use**: standard Android UI/compat plumbing; used unmodified via Maven.
  GeckoView also brings in a few AndroidX support libraries and SnakeYAML
  (Apache-2.0) as its own dependencies.

## com.google.android.material:material (Material Components for Android)
- **License**: Apache License 2.0.
- **Use**: `MaterialCardView` (the rounded URL bar) and `MaterialButton`
  icon buttons (Go / bookmark / bookmarks / pointer / menu), the
  load-progress indicator, and dialogs, plus the `Theme.Material3.Dark`
  base theme.

## Poppins typeface
- **License**: SIL Open Font License 1.1.
- **Use**: the app's UI font (`res/font/poppins_*.ttf`).

## Material Symbols / Material Icons glyphs
- **License**: Apache License 2.0 (Google's Material Icons/Symbols set).
- **Use**: `ic_search_24dp.xml`, `ic_go_24dp.xml`, `ic_pointer_24dp.xml`,
  `ic_star_24dp.xml`, `ic_star_filled_24dp.xml`, `ic_bookmarks_24dp.xml`,
  `ic_more_24dp.xml`, `ic_delete_24dp.xml`,
  and the shield launcher glyph are simple vector paths re-drawn in the
  style of, and in one case (`ic_search_24dp`) directly matching,
  Google's published Material Icons "search" glyph, which Google
  distributes under Apache-2.0 specifically for reuse like this.

## Kotlin standard library / Android Gradle Plugin / Kotlin Gradle Plugin
- **License**: Apache License 2.0.

## Design reference (not code): TV Bro
- TV Bro (https://github.com/truefedex/tv-bro) is licensed under
  GPL-3.0. No source code, resources, or assets from TV Bro are included
  in this repository. It was consulted only as a reference for D-pad/
  remote-control UX patterns (e.g., a toggleable virtual pointer for
  non-TV-optimized pages), which were then independently reimplemented
  in `MainActivity.kt` under this project's own license. If a future
  contributor copies actual TV Bro source into this repo, this project
  would need to relicense as GPL-3.0 as a whole — avoid doing that.

## Google Play Services (deliberately excluded)
GeckoView declares a dependency on `play-services-fido` (proprietary,
Google) that it uses for passkeys / security keys. This project removes
that dependency at build time (`configurations.configureEach { exclude(...) }`)
and turns WebAuthn off, so the APK contains only open-source components.

## What was deliberately left out
To satisfy the "no Firefox branding / no telemetry / no sponsored
content" requirements, this project does **not** depend on:
- Mozilla Glean (telemetry SDK)
- Mozilla Nimbus / Experimenter (remote experiments, sponsored tiles)
- Firefox Account / Sync components
- Fenix/Focus application code or resources (icons, strings, "Firefox"
  wordmark, fox logo) — only the underlying GeckoView engine is reused,
  none of the branded application shell.
