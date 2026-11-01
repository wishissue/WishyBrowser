#!/usr/bin/env python3
"""Run from the project root: python3 tools/build_blocklist.py
Builds app/src/main/assets/blocklist.txt and cosmetic.css from filter lists.
Sources (see NOTICE.md): EasyList + EasyPrivacy (GPLv3+ / CC BY-SA 3.0). Peter Lowe's list is NOT used (non-commercial terms).
Only plain domain rules (||domain^ with no per-domain/type options except $third-party) are kept.
"""
import re, sys, pathlib
import urllib.request
L = pathlib.Path("tools/lists"); L.mkdir(exist_ok=True)
for name, url in [("easylist.txt", "https://easylist.to/easylist/easylist.txt"), ("easyprivacy.txt", "https://easylist.to/easylist/easyprivacy.txt")]:
    if not (L / name).exists():
        print("downloading", url); (L / name).write_bytes(urllib.request.urlopen(url, timeout=60).read())
A = L
OUT = pathlib.Path("app/src/main/assets")
dom = re.compile(r'^\|\|([a-z0-9][a-z0-9.-]*\.[a-z]{2,})\^(\$third-party)?$')
domains = set()
for f in ["easylist.txt", "easyprivacy.txt"]:
    for line in (A / f).read_text(errors="ignore").splitlines():
        m = dom.match(line.strip())
        if m: domains.add(m.group(1))
# drop subdomains whose parent is already listed (host match walks up the labels anyway)
keep = []
for d in sorted(domains, key=lambda s: s.count(".")):
    parts = d.split(".")
    if any(".".join(parts[i:]) in domains for i in range(1, len(parts) - 1)):
        continue
    keep.append(d)
keep.sort()
(OUT / "blocklist.txt").write_text("\n".join(keep) + "\n")
# cosmetic: generic, simple selectors only
sel = re.compile(r'^##((?:\.|#)[A-Za-z0-9_-]+|\[(?:id|class)(?:\^|\*|\$)?="[A-Za-z0-9_-]+"\])$')
css = set()
for line in (A / "easylist.txt").read_text(errors="ignore").splitlines():
    m = sel.match(line.strip())
    if m: css.add(m.group(1))
css = sorted(css)[:2500]
(OUT / "cosmetic.txt").write_text("\n".join(css) + "\n")
print(len(domains), "->", len(keep), "domains;", len(css), "cosmetic selectors")
