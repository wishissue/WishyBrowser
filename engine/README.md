# Wishy Lite GeckoView (custom engine build)

Goal: a smaller GeckoView with **no loss of speed or privacy**.

Status: scripts written, **not yet run**. The app still uses stock GeckoView
(`org.mozilla.geckoview:geckoview:155...`) until a custom AAR exists and passes
the checks below.

## Build
    engine/build.sh arm64      # then: engine/build.sh arm

## Use the result
Put the AARs in `app/libs/` and replace the `implementation(...geckoview...)`
line with a flavor-specific `files("libs/geckoview-arm64.aar")`. Keep the stock
dependency as the fallback until the checks pass.

## Must pass before shipping (no compromises)
1. Size: record stock vs custom per CPU type (`unzip -l`, APK Analyzer).
2. Speed: Speedometer 3 and cold-start time, stock vs custom, on a real stick.
   Mozilla's official builds use profile-guided optimisation (PGO); a self-build
   may not, which could cost speed. If custom is slower, we do not ship it.
3. Privacy: same prefs applied (see BrowserApplication.kt), uBlock Origin still
   runs, no new network connections (check with a packet capture).
4. Regression: YouTube, logins, video, file upload, the D-pad pointer.
