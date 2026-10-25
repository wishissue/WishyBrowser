#!/usr/bin/env bash
# Refreshes the filter lists bundled with uBlock Origin from uBlockOrigin/uAssetsCDN.
# Run before each release (or from CI). uBlock also self-updates at runtime; this
# only makes the FIRST launch current.
set -euo pipefail
B=https://raw.githubusercontent.com/uBlockOrigin/uAssetsCDN/main
U="$(dirname "$0")/../app/src/main/assets/extensions/ublock/assets"
for f in filters badware privacy unbreak quick-fixes; do
  curl -fsS "$B/filters/$f.min.txt" -o "$U/ublock/$f.min.txt"
done
for f in easylist easyprivacy; do
  curl -fsS "$B/thirdparties/$f.txt" -o "$U/thirdparties/easylist/$f.txt"
done
echo "Lists refreshed:"; grep -h -m1 "Last modified" "$U"/ublock/*.min.txt "$U"/thirdparties/easylist/*.txt
