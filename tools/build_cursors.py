#!/usr/bin/env python3
"""Adds the open-source cursor sets to app/src/main/assets/cursors (PNG + index.json).
Run from the project root after cloning the sources next to the project:
  git clone --depth 1 https://github.com/vinceliuice/Vimix-cursors /tmp/cur/Vimix-cursors   (GPL-3.0)
  git clone --depth 1 https://github.com/vinceliuice/Layan-cursors /tmp/cur/Layan-cursors   (GPL-3.0)
  git clone --depth 1 https://github.com/keeferrourke/capitaine-cursors /tmp/cur/capitaine-cursors (LGPL-3.0)
Bibata (GPL-3.0) pictures were already in the project and are kept as they are.
"""
import json, pathlib, re, io, sys
import cairosvg
from PIL import Image

SRC = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "/tmp/cur")
OUT = pathlib.Path("app/src/main/assets/cursors")
SHAPES = {"default": "Arrow", "pointer": "Hand", "crosshair": "Cross"}

def hotspot(cfg: pathlib.Path):
    spec = cfg.with_suffix(".spec")
    if spec.exists():                      # Capitaine: "xhot yhot" on a 24-unit canvas
        a, b = spec.read_text().split()[:2]
        return int(a) / 24, int(b) / 24
    first = cfg.read_text().split("\n")[0].split()
    return int(first[1]) / int(first[0]), int(first[2]) / int(first[0])   # fraction of the canvas

def render(svg: pathlib.Path, hx: float, hy: float):
    png = cairosvg.svg2png(url=str(svg), output_width=96, output_height=96)
    im = Image.open(io.BytesIO(png)).convert("RGBA")
    box = im.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    l, t, r, b = max(box[0] - 1, 0), max(box[1] - 1, 0), min(box[2] + 1, 96), min(box[3] + 1, 96)
    crop = im.crop((l, t, r, b))
    w, h = crop.size
    hot = (min(max((hx * 96 - l) / w, 0), 1), min(max((hy * 96 - t) / h, 0), 1))
    return crop, hot, w / h

SETS = [
    # (pack, author, license, url, [(variant label, svg dir, config dir)])
    ("Vimix", "Vince Liu", "GPL-3.0", "https://github.com/vinceliuice/Vimix-cursors",
     SRC / "Vimix-cursors/src", [("Dark", "svg", "config"), ("White", "svg-white", "config")]),
    ("Layan", "Vince Liu", "GPL-3.0", "https://github.com/vinceliuice/Layan-cursors",
     SRC / "Layan-cursors/src", [("Dark", "svg", "config"), ("White", "svg-white", "config")]),
    ("Capitaine", "Keefer Rourke and others (based on KDE Breeze)", "LGPL-3.0", "https://github.com/keeferrourke/capitaine-cursors",
     SRC / "capitaine-cursors/src", [("Light", "svg/light", "config/static"), ("Dark", "svg/dark", "config/static")]),
]
# Which shapes of which set go in (13 cursors, kept small on purpose).
PICK = {
    "Vimix": [("default", "Dark"), ("default", "White"), ("pointer", "Dark"), ("pointer", "White"), ("crosshair", "Dark")],
    "Layan": [("default", "Dark"), ("default", "White"), ("pointer", "Dark"), ("pointer", "White")],
    "Capitaine": [("default", "Light"), ("default", "Dark"), ("pointer", "Light"), ("pointer", "Dark")],
}

index = json.loads((OUT / "index.json").read_text())
index["packs"] = [p for p in index["packs"] if p["name"] == "Bibata"]
index["cursors"] = [c for c in index["cursors"] if c.get("pack") == "Bibata"]
for pack, author, lic, url, root, variants in SETS:
    index["packs"].append({"name": pack, "author": author, "license": lic, "url": url})
    vmap = {v[0]: v for v in variants}
    for shape, variant in PICK[pack]:
        _, sdir, cdir = vmap[variant]
        hx, hy = hotspot(root / cdir / f"{shape}.cursor")
        img, (hotx, hoty), aspect = render(root / sdir / f"{shape}.svg", hx, hy)
        fid = f"{pack.lower()}_{SHAPES[shape].lower()}_{variant.lower()}"
        fname = f"{fid}.png"
        img.save(OUT / fname, optimize=True)
        index["cursors"].append({"id": fid, "name": f"{pack} {SHAPES[shape]} {variant}", "file": fname,
                                 "hotX": round(hotx, 3), "hotY": round(hoty, 3), "aspect": round(aspect, 3), "pack": pack})
(OUT / "index.json").write_text(json.dumps(index, indent=1) + "\n")
print(len(index["cursors"]), "cursors in", len(index["packs"]), "packs")
