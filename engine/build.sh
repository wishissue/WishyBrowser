#!/usr/bin/env bash
# Builds a trimmed GeckoView AAR for one CPU type. NOT YET RUN: expect to fix
# small issues on the first build. Needs Linux, 16 GB+ RAM, ~100 GB disk, hours.
# Usage: engine/build.sh arm64|arm [tag]
set -euo pipefail
ABI=${1:?arm64 or arm}; TAG=${2:-FIREFOX_155_0_RELEASE}
HERE="$(cd "$(dirname "$0")" && pwd)"; WORK="${WORK:-$HOME/wishy-gecko}"
mkdir -p "$WORK" && cd "$WORK"
[ -d firefox ] || git clone --depth 1 --branch "$TAG" https://github.com/mozilla-firefox/firefox firefox
cd firefox
cp "$HERE"/mozconfig.common "$HERE/mozconfig.$ABI" .
export MOZCONFIG="$PWD/mozconfig.$ABI"
./mach --no-interactive bootstrap --application-choice="GeckoView/Firefox for Android"
./mach build
./mach package
./mach android archive-geckoview
find . -name 'geckoview-*.aar' -newer "$MOZCONFIG" -print
echo "Sizes (compare with the stock AAR):"; find . -name 'geckoview-*.aar' -newer "$MOZCONFIG" -exec ls -l {} +
