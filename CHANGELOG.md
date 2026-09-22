# Changelog

All notable changes to Wishy Browser will be documented in this file.

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
