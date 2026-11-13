#!/usr/bin/env python3
"""Generate the NewSwipe app icon ('밀려 내려간 키') from one shape definition.

Every place the icon appears is drawn from the shapes below, so proportions and
colors stay identical everywhere:
  - app/src/main/res/drawable/ic_launcher_background.xml  (gradient backdrop)
  - app/src/main/res/drawable/ic_launcher_foreground.xml  (ghost key, shadow, key, arrow)
  - app/src/main/res/drawable/ic_launcher_monochrome.xml  (themed icon layer)
  - app/src/main/res/values/colors.xml                    (gradient colors used by the onboarding halo)
  - docs/play-store/icon-512.png                          (--png)
  - docs/play-store/feature-graphic@3x.png, feature-graphic.png (--png; the key is redrawn in place)

Usage:
  python3 tools/generate_launcher_icon.py          # Android resources only
  python3 tools/generate_launcher_icon.py --png    # also the Play Store images
  python3 tools/generate_launcher_icon.py --svg out/  # also write SVG previews

Requires: shapely (pip install shapely). --png also needs Pillow, numpy, and Node.js
with the 'playwright' package and a Chromium it can launch.
"""
import argparse
import json
import os
import re
import subprocess
import tempfile

from shapely.geometry import LineString, box
from shapely.ops import unary_union

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
PLAY = os.path.join(ROOT, "docs", "play-store")

# ---------------------------------------------------------------- shapes (108dp adaptive-icon grid)

GHOST = (35, 24, 38, 38, 10)   # x, y, w, h, corner radius: where the key was before the swipe
FRONT = (33, 36, 42, 42, 11)   # the key after sliding down
SHAFT = [(54, 47), (54, 65)]
CHEVRON = [(46.5, 58), (54, 65.5), (61.5, 58)]
STROKE = 6

BG_START, BG_END = "#5A8BFF", "#2450D8"   # top-left → bottom-right
HIGHLIGHT = (0.25, 0.18, 0.70, 0.22)      # radial white glow: centre x, y, radius (fractions of size), alpha
KEY_TOP, KEY_BOTTOM = "#FFFFFF", "#EEF3FF"
ARROW_TOP, ARROW_BOTTOM = "#4A7EF7", "#1F48C9"
GHOST_ALPHA = 0.28
SHADOW = ("#0E2A86", 0.45, 4, 3.9, 14)    # color, alpha, y offset, softness (dp), layers

# Where the foreground sits in feature-graphic@3x.png: grid centre and px per dp.
FEATURE_CENTER = (341.5, 368.5)
FEATURE_SCALE = 592 / 108
FEATURE_CLEAR = (170, 195, 515, 575)      # area repainted from its edges before the key is drawn


def num(v):
    return ("%.2f" % v).rstrip("0").rstrip(".")


def key(x, y, w, h, r):
    n = num
    return (f"M{n(x + r)},{n(y)}h{n(w - 2 * r)}a{n(r)},{n(r)} 0,0 1 {n(r)},{n(r)}v{n(h - 2 * r)}"
            f"a{n(r)},{n(r)} 0,0 1 -{n(r)},{n(r)}h-{n(w - 2 * r)}a{n(r)},{n(r)} 0,0 1 -{n(r)},-{n(r)}"
            f"v-{n(h - 2 * r)}a{n(r)},{n(r)} 0,0 1 {n(r)},-{n(r)}z")


def key_poly(x, y, w, h, r):
    return box(x + r, y + r, x + w - r, y + h - r).buffer(r, quad_segs=24)


def poly_path(geom):
    polys = [geom] if geom.geom_type == "Polygon" else list(geom.geoms)
    rings = [ring for p in polys for ring in (p.exterior, *p.interiors)]
    return "".join("M" + "L".join(f"{num(x)},{num(y)}" for x, y in list(r.coords)[:-1]) + "z" for r in rings)


def shadow_layers():
    # VectorDrawable has no blur, so the soft shadow is stacked rings that grow and fade.
    color, total, dy, soft, n = SHADOW
    x, y, w, h, r = FRONT
    a = 1 - (1 - total) ** (1 / n)
    for i in range(n):
        g = -soft + i * (2 * soft / (n - 1))
        yield dict(d=key(x - g, y + dy - g, w + 2 * g, h + 2 * g, max(1, r + g)), fill=(color, a))


BACKGROUND = [
    dict(d="M0,0h108v108h-108z", grad=("linear", (0, 0, 108, 108), [(0, BG_START, 1), (1, BG_END, 1)])),
    dict(d="M0,0h108v108h-108z", grad=("radial", (HIGHLIGHT[0] * 108, HIGHLIGHT[1] * 108, HIGHLIGHT[2] * 108),
                                       [(0, "#FFFFFF", HIGHLIGHT[3]), (1, "#FFFFFF", 0)])),
]
FOREGROUND = [
    dict(d=key(*GHOST), fill=("#FFFFFF", GHOST_ALPHA)),
    *shadow_layers(),
    dict(d=key(*FRONT), grad=("linear", (0, FRONT[1], 0, FRONT[1] + FRONT[3]), [(0, KEY_TOP, 1), (1, KEY_BOTTOM, 1)])),
    dict(d=f"M{num(SHAFT[0][0])},{num(SHAFT[0][1])}V{num(SHAFT[1][1])}"
           f"M{num(CHEVRON[0][0])},{num(CHEVRON[0][1])}L{num(CHEVRON[1][0])},{num(CHEVRON[1][1])}L{num(CHEVRON[2][0])},{num(CHEVRON[2][1])}",
         stroke=("linear", (0, SHAFT[0][1], 0, CHEVRON[1][1]), [(0, ARROW_TOP, 1), (1, ARROW_BOTTOM, 1)])),
]
_arrow = unary_union([LineString(SHAFT).buffer(STROKE / 2, quad_segs=12),
                      LineString(CHEVRON).buffer(STROKE / 2, quad_segs=12, join_style="round")])
_front = key_poly(*FRONT)
MONOCHROME = [
    dict(d=poly_path(key_poly(*GHOST).difference(_front)), fill=("#FFFFFF", 0.3)),
    dict(d=poly_path(_front.difference(_arrow)), fill=("#FFFFFF", 1), evenodd=True),
]

# ---------------------------------------------------------------- VectorDrawable


def argb(hex6, alpha=1.0):
    return "#%02X%s" % (round(alpha * 255), hex6.lstrip("#").upper())


def vd_gradient(attr, g):
    kind, c, stops = g
    if kind == "linear":
        head = (f'<gradient android:type="linear" android:startX="{num(c[0])}" android:startY="{num(c[1])}" '
                f'android:endX="{num(c[2])}" android:endY="{num(c[3])}">')
    else:
        head = (f'<gradient android:type="radial" android:centerX="{num(c[0])}" android:centerY="{num(c[1])}" '
                f'android:gradientRadius="{num(c[2])}">')
    items = "".join(f'\n                <item android:offset="{o}" android:color="{argb(col, a)}" />' for o, col, a in stops)
    return f'\n        <aapt:attr name="{attr}">\n            {head}{items}\n            </gradient>\n        </aapt:attr>'


def vector(layers, comment):
    ns = ' xmlns:aapt="http://schemas.android.com/aapt"' if any("grad" in l or "stroke" in l for l in layers) else ""
    out = ['<?xml version="1.0" encoding="utf-8"?>',
           f"<!-- {comment} tools/generate_launcher_icon.py로 만든다. 손으로 고치지 않는다. -->",
           f'<vector xmlns:android="http://schemas.android.com/apk/res/android"{ns}\n'
           '    android:width="108dp"\n    android:height="108dp"\n'
           '    android:viewportWidth="108"\n    android:viewportHeight="108">']
    for l in layers:
        attrs = [f'android:pathData="{l["d"]}"']
        if "fill" in l:
            attrs.insert(0, f'android:fillColor="{argb(*l["fill"])}"')
        if l.get("evenodd"):
            attrs.insert(0, 'android:fillType="evenOdd"')
        if "stroke" in l:
            attrs += [f'android:strokeWidth="{STROKE}"', 'android:strokeLineCap="round"', 'android:strokeLineJoin="round"']
        inner = vd_gradient("android:fillColor", l["grad"]) if "grad" in l else ""
        if "stroke" in l:
            inner = vd_gradient("android:strokeColor", l["stroke"])
        body = "\n        ".join(attrs)
        out.append(f"    <path\n        {body}" + (f">{inner}\n    </path>" if inner else " />"))
    out.append("</vector>\n")
    return "\n".join(out)


def write(path, text):
    with open(path, "w", encoding="utf-8") as fp:
        fp.write(text)


def write_resources():
    d = os.path.join(RES, "drawable")
    write(os.path.join(d, "ic_launcher_background.xml"),
          vector(BACKGROUND, "앱 아이콘 바탕: 왼쪽 위가 밝은 파란 그라데이션."))
    write(os.path.join(d, "ic_launcher_foreground.xml"),
          vector(FOREGROUND, "밀려 내려간 키: 원래 자리의 반투명 잔상과 아래로 밀려 내려온 키로 스와이프(아래로 밀기)를 나타낸다."))
    write(os.path.join(d, "ic_launcher_monochrome.xml"),
          vector(MONOCHROME, "테마 아이콘용 단색 레이어: 잔상 30%, 앞 키는 화살표 모양으로 뚫는다."))
    colors = os.path.join(RES, "values", "colors.xml")
    with open(colors, encoding="utf-8") as fp:
        text = fp.read()
    for name, value in (("ic_launcher_gradient_start", BG_START), ("ic_launcher_gradient_end", BG_END)):
        text, n = re.subn(rf'(<color name="{name}">)#[0-9A-Fa-f]+(</color>)', rf"\g<1>{value}\g<2>", text)
        if n != 1:
            raise SystemExit(f"colors.xml: <color name=\"{name}\"> not found")
    write(colors, text)

# ---------------------------------------------------------------- SVG / PNG


def svg(layers, view):
    defs, body = [], []

    def ref(g):
        kind, c, stops = g
        gid = f"g{len(defs)}"
        st = "".join(f'<stop offset="{o}" stop-color="{col}" stop-opacity="{a}"/>' for o, col, a in stops)
        if kind == "linear":
            defs.append(f'<linearGradient id="{gid}" gradientUnits="userSpaceOnUse" x1="{c[0]}" y1="{c[1]}" x2="{c[2]}" y2="{c[3]}">{st}</linearGradient>')
        else:
            defs.append(f'<radialGradient id="{gid}" gradientUnits="userSpaceOnUse" cx="{c[0]}" cy="{c[1]}" r="{c[2]}">{st}</radialGradient>')
        return f"url(#{gid})"

    for l in layers:
        if "stroke" in l:
            body.append(f'<path d="{l["d"]}" fill="none" stroke="{ref(l["stroke"])}" stroke-width="{STROKE}" '
                        'stroke-linecap="round" stroke-linejoin="round"/>')
        elif "grad" in l:
            body.append(f'<path d="{l["d"]}" fill="{ref(l["grad"])}"/>')
        else:
            col, a = l["fill"]
            rule = ' fill-rule="evenodd"' if l.get("evenodd") else ""
            body.append(f'<path d="{l["d"]}" fill="{col}" fill-opacity="{a}"{rule}/>')
    return f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}"><defs>{"".join(defs)}</defs>{"".join(body)}</svg>'


SVGS = {
    # Launchers show the middle 72dp of the 108dp grid; the Play Store icon uses the same crop.
    "icon.svg": (BACKGROUND + FOREGROUND, "18 18 72 72"),
    "foreground.svg": (FOREGROUND, "0 0 108 108"),
    "monochrome.svg": (MONOCHROME, "18 18 72 72"),
}

RENDER_JS = r"""
const { chromium } = require('playwright');
const jobs = JSON.parse(process.argv[2]);
(async () => {
  const browser = await chromium.launch();
  for (const [svg, out, size] of jobs) {
    const page = await browser.newPage({ viewport: { width: size, height: size } });
    await page.setContent('<style>html,body{margin:0;background:transparent}svg{display:block;width:'
      + size + 'px;height:' + size + 'px}</style>' + svg);
    await page.screenshot({ path: out, omitBackground: true });
    await page.close();
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
"""


def render(jobs):
    with tempfile.TemporaryDirectory() as tmp:
        script = os.path.join(tmp, "render.js")
        write(script, RENDER_JS)
        subprocess.run(["node", script, json.dumps(jobs)], check=True)


def write_play_store():
    import numpy as np
    from PIL import Image

    with tempfile.TemporaryDirectory() as tmp:
        icon_png, fg_png = os.path.join(tmp, "icon.png"), os.path.join(tmp, "fg.png")
        fg_size = round(108 * FEATURE_SCALE)
        render([[svg(*SVGS["icon.svg"]), icon_png, 512], [svg(*SVGS["foreground.svg"]), fg_png, fg_size]])
        Image.open(icon_png).convert("RGB").save(os.path.join(PLAY, "icon-512.png"), optimize=True)

        path3x = os.path.join(PLAY, "feature-graphic@3x.png")
        src = Image.open(path3x).convert("RGB")
        a = np.asarray(src).astype(float)
        # Repaint the old key area from its four edges (Coons patch) so the backdrop gradient continues.
        x0, y0, x1, y1 = FEATURE_CLEAR
        u = np.linspace(0, 1, x1 - x0 + 1)[None, :, None]
        v = np.linspace(0, 1, y1 - y0 + 1)[:, None, None]
        left, right = a[y0:y1 + 1, x0][:, None], a[y0:y1 + 1, x1][:, None]
        top, bottom = a[y0, x0:x1 + 1][None], a[y1, x0:x1 + 1][None]
        corners = ((1 - u) * (1 - v) * a[y0, x0] + u * (1 - v) * a[y0, x1]
                   + (1 - u) * v * a[y1, x0] + u * v * a[y1, x1])
        a[y0:y1 + 1, x0:x1 + 1] = (1 - u) * left + u * right + (1 - v) * top + v * bottom - corners
        img = Image.fromarray(np.clip(a + 0.5, 0, 255).astype(np.uint8)).convert("RGBA")
        cx, cy = FEATURE_CENTER
        img.alpha_composite(Image.open(fg_png), (round(cx - fg_size / 2), round(cy - fg_size / 2)))
        out = img.convert("RGB")
        out.save(path3x, optimize=True)
        out.resize((src.width // 3, src.height // 3), Image.LANCZOS).save(
            os.path.join(PLAY, "feature-graphic.png"), optimize=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--png", action="store_true", help="also redraw docs/play-store images")
    ap.add_argument("--svg", metavar="DIR", help="also write SVG previews into DIR")
    args = ap.parse_args()
    write_resources()
    print("wrote drawable/ic_launcher_{background,foreground,monochrome}.xml, values/colors.xml")
    if args.svg:
        os.makedirs(args.svg, exist_ok=True)
        for name, (layers, view) in SVGS.items():
            write(os.path.join(args.svg, name), svg(layers, view))
        print(f"wrote SVGs to {args.svg}")
    if args.png:
        write_play_store()
        print("wrote docs/play-store/icon-512.png, feature-graphic@3x.png, feature-graphic.png")


if __name__ == "__main__":
    main()
