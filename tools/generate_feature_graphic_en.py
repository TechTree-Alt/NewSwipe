#!/usr/bin/env python3
"""Make the English Play Store feature graphic from the Korean one.

Reads  docs/play-store/feature-graphic@3x.png (Korean)
Writes docs/play-store/feature-graphic-en@3x.png and feature-graphic-en.png (1024x500)

- The Korean tagline and subline are painted out (only the text pixels, refilled from a
  smooth fit of the surrounding gradient) and the English text is drawn in their place.
- The Korean keyboard mock-up is painted out and replaced by an English (QWERTY) one in
  the same style, drawn in HTML with Playwright and tilted to the same angle. It shows
  'Swipe down for capitals': swiping r down types R.
The icon and the "NewSwipe" title are left as they are.
Run it again after tools/generate_launcher_icon.py --png redraws the Korean graphic.

Requires: Pillow, numpy, the Inter font (fonts-inter), and Node.js with the
'playwright' package and a Chromium it can launch.
"""
import json
import os
import subprocess
import tempfile

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAY = os.path.join(ROOT, "docs", "play-store")
FONT_DIR = "/usr/share/fonts/opentype/inter"

TAGLINE = ["Input system built for", "extreme customization"]
SUBLINE = "Lightweight · Modern · Comfortable"

TEXT_CLEAR = (180, 860, 1280, 1275)  # x0, y0, x1, y1: box holding the Korean text
LEFT = 222                           # left edge of the Korean text
TAG_BASE = (975, 1098)               # baselines of the two tagline lines
SUB_BASE = 1232
TAG_COLOR = (255, 255, 255)
SUB_COLOR = (217, 226, 249)

# The keyboard mock-up in the Korean graphic: centre, size and tilt (counter-clockwise).
KB_CENTER = (2275, 750)
KB_SIZE = (1320, 1032)
KB_ANGLE = 5.0
POPUP_CLEAR = (1840, 130, 2150, 470)  # old popup and its shadow
POPUP_FIT = (1650, 0, 2450, 470)      # backdrop fitted around it
MARGIN = 260                         # room around the panel for the popup and shadows

SUGGESTIONS = ["Right", "Really", "Ready"]  # the middle one is highlighted
SWIPED = ("r", "R")                         # key swiped down, and what it types


def poly_basis(h, w, oy=0, ox=0, H=None, W=None):
    H, W = H or h, W or w
    yy, xx = np.mgrid[oy:oy + h, ox:ox + w]
    u, v = xx / W, yy / H
    return np.stack([np.ones_like(u), u, v, u * u, u * v, v * v,
                     u ** 3, v ** 3, u * u * v, u * v * v], -1)


def clear_text(a):
    x0, y0, x1, y1 = TEXT_CLEAR
    reg = a[y0:y1, x0:x1]
    lum = reg.mean(2)
    basis = poly_basis(y1 - y0, x1 - x0)
    keep = np.ones(lum.shape, bool)
    for _ in range(3):  # fit the backdrop, ignoring anything much brighter than it
        coef = np.linalg.lstsq(basis[keep], reg[keep], rcond=None)[0]
        fit = basis @ coef
        keep = lum - fit.mean(2) < 30  # text is far brighter than the faint arcs
    mask = Image.fromarray(((~keep) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))
    m = (np.asarray(mask.filter(ImageFilter.GaussianBlur(2))) / 255.0)[..., None]
    a[y0:y1, x0:x1] = reg * (1 - m) + fit * m


def clear_keyboard(a):
    """Paint out the old popup where it sticks out above the panel.

    The new panel is drawn exactly over the old one (whose soft shadow is kept), so only the
    old popup needs removing: it is refilled from a smooth fit of the backdrop around it."""
    x0, y0, x1, y1 = POPUP_FIT
    px0, py0, px1, py1 = POPUP_CLEAR
    reg = a[y0:y1, x0:x1]
    basis = poly_basis(y1 - y0, x1 - x0)
    keep = np.ones(reg.shape[:2], bool)
    keep[py0 - y0:py1 - y0, px0 - x0:px1 - x0] = False
    lum = reg.mean(2)
    keep &= lum < 200  # not the panel
    coef = np.linalg.lstsq(basis[keep], reg[keep], rcond=None)[0]
    fit = basis @ coef
    m = Image.new("L", (x1 - x0, y1 - y0), 0)
    ImageDraw.Draw(m).rectangle((px0 - x0, py0 - y0, px1 - x0, py1 - y0), fill=255)
    m = (np.asarray(m.filter(ImageFilter.GaussianBlur(12))) / 255.0)[..., None]
    a[y0:y1, x0:x1] = reg * (1 - m) + fit * m


KEYBOARD_HTML = r"""<!doctype html><html><head><meta charset="utf-8"><style>
html,body{margin:0;background:transparent}
#stage{position:relative;width:__SW__px;height:__SH__px}
#panel{position:absolute;left:__M__px;top:__M__px;width:__PW__px;height:__PH__px;border-radius:96px;
  background:#E6E8EC;box-shadow:0 0 0 5px #E6E8EC}  /* covers the old panel's edge */
.k{position:absolute;border-radius:34px;background:#fff;box-shadow:0 3px 0 #CACBCF;display:flex;
  align-items:center;justify-content:center;font:500 68px Inter;color:#1F2228}
.fn{background:#D3D7DE}
.lbl{font:500 58px Inter}
.blue{background:#2F62E8;color:#fff;box-shadow:0 3px 0 #2147B8}
.sg{position:absolute;top:0;height:205px;display:flex;align-items:center;justify-content:center;
  font:500 60px Inter;color:#262B33}
.sg.hi{color:#2F62E8;font-weight:700}
.div{position:absolute;top:78px;width:4px;height:58px;border-radius:2px;background:#C3C7CF}
#pop{position:absolute;border-radius:62px;background:#2F62E8;color:#fff;display:flex;align-items:center;
  justify-content:center;font:700 128px Inter;box-shadow:0 18px 40px rgba(8,26,104,.35)}
#tail{position:absolute;width:0;height:0;border-left:26px solid transparent;border-right:26px solid transparent;
  border-top:30px solid #2F62E8}
svg{position:absolute;left:0;top:0;overflow:visible}
</style></head><body><div id="stage"><div id="panel"></div></div>
<script>
const D = __DATA__;
const panel = document.getElementById('panel');
const PAD = 45, TOP = 205, KH = 182, GAP = 18, INNER = D.pw - 2 * PAD, U = (INNER + GAP) / 10;
const ICON = {
  shift: '<path d="M0,-34 L32,4 H14 V30 H-14 V4 H-32 Z" fill="none" stroke="#1F2228" stroke-width="6" stroke-linejoin="round"/>',
  back: '<path d="M-46,0 L-24,-28 H40 a8,8 0 0 1 8,8 V20 a8,8 0 0 1 -8,8 H-24 Z" fill="none" stroke="#1F2228" stroke-width="7" stroke-linejoin="round"/><path d="M-6,-12 L18,12 M18,-12 L-6,12" stroke="#1F2228" stroke-width="7" stroke-linecap="round"/>',
  globe: '<g fill="none" stroke="#1F2228" stroke-width="6"><circle r="30"/><ellipse rx="13" ry="30"/><path d="M-30,0 H30 M-26,-15 H26 M-26,15 H26"/></g>',
  enter: '<path d="M34,-26 V6 H-30 M-12,-14 L-32,6 L-12,26" fill="none" stroke="#fff" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>',
};
const keys = [];
['qwertyuiop', 'asdfghjkl'].forEach((r, ri) => [...r].forEach((c, i) => keys.push({ x: (ri ? 0.5 : 0) + i, w: 1, row: ri, label: c })));
keys.push({ x: 0, w: 1.5, row: 2, icon: 'shift', fn: 1 });
[...'zxcvbnm'].forEach((c, i) => keys.push({ x: 1.5 + i, w: 1, row: 2, label: c }));
keys.push({ x: 8.5, w: 1.5, row: 2, icon: 'back', fn: 1 });
keys.push({ x: 0, w: 1.5, row: 3, label: '?123', fn: 1, small: 1 });
keys.push({ x: 1.5, w: 1, row: 3, icon: 'globe', fn: 1 });
keys.push({ x: 2.5, w: 5, row: 3, label: '' });
keys.push({ x: 7.5, w: 1, row: 3, label: '.', fn: 1 });
keys.push({ x: 8.5, w: 1.5, row: 3, icon: 'enter', blue: 1 });
let swiped;
for (const k of keys) {
  const el = document.createElement('div');
  const x = PAD + k.x * U, y = TOP + k.row * (KH + GAP), w = k.w * U - GAP;
  const hit = k.label === D.swiped[0];
  el.className = 'k' + (k.fn ? ' fn' : '') + (k.blue || hit ? ' blue' : '') + (k.small ? ' lbl' : '');
  if (hit) el.style.alignItems = 'flex-start', el.style.paddingTop = '22px', el.style.boxSizing = 'border-box';
  Object.assign(el.style, { left: x + 'px', top: y + 'px', width: w + 'px', height: KH + 'px' });
  if (k.icon) el.innerHTML = `<svg width="${w}" height="${KH}"><g transform="translate(${w / 2},${KH / 2})">${ICON[k.icon]}</g></svg>`;
  else el.textContent = k.label;
  panel.appendChild(el);
  if (hit) swiped = { x, y, w };
}
// Suggestion strip: three slots with dividers between them.
const slot = INNER / 3;
D.sugg.forEach((s, i) => {
  const el = document.createElement('div');
  el.className = 'sg' + (i === 1 ? ' hi' : '');
  Object.assign(el.style, { left: PAD + i * slot + (i === 1 ? 40 : 0) + 'px', width: slot + 'px' });
  el.textContent = s;
  panel.appendChild(el);
  if (i) { const d = document.createElement('div'); d.className = 'div'; d.style.left = PAD + i * slot + 'px'; panel.appendChild(d); }
});
// Popup above the swiped key, and the swipe-down arrow.
const cx = swiped.x + swiped.w / 2, PW = 214, PH = 250;
const pop = document.createElement('div'); pop.id = 'pop'; pop.textContent = D.swiped[1];
Object.assign(pop.style, { left: cx - PW / 2 + 'px', top: swiped.y - 30 - PH + 'px', width: PW + 'px', height: PH + 'px' });
const tail = document.createElement('div'); tail.id = 'tail';
Object.assign(tail.style, { left: cx - 26 + 'px', top: swiped.y - 31 + 'px' });
panel.append(pop, tail);
const y0 = swiped.y + 128, y1 = swiped.y + KH + GAP + 56;
const path = `M${cx},${y0} V${y1} M${cx - 44},${y1 - 44} L${cx},${y1} L${cx + 44},${y1 - 44}`;
const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
svg.setAttribute('width', D.pw); svg.setAttribute('height', D.ph);
svg.innerHTML = `<defs><filter id="sh" x="-50%" y="-50%" width="200%" height="200%"><feDropShadow dx="0" dy="6" stdDeviation="8" flood-color="#081A68" flood-opacity=".35"/></filter></defs>
  <g filter="url(#sh)" fill="none" stroke-linecap="round" stroke-linejoin="round">
  <path d="${path}" stroke="#2F62E8" stroke-width="30"/><path d="${path}" stroke="#fff" stroke-width="15"/></g>`;
panel.appendChild(svg);
</script></body></html>"""

RENDER_JS = r"""
const { chromium } = require('playwright');
const [html, out, w, h] = process.argv.slice(2);
(async () => {
  const b = await chromium.launch();
  const p = await b.newPage({ viewport: { width: +w, height: +h }, deviceScaleFactor: 1 });
  await p.goto('file://' + html);
  await p.evaluate(() => document.fonts.ready);
  await p.screenshot({ path: out, omitBackground: true });
  await b.close();
})();
"""


def render_keyboard(tmp):
    pw, ph = KB_SIZE
    sw, sh = pw + 2 * MARGIN, ph + 2 * MARGIN
    data = {"pw": pw, "ph": ph, "sugg": SUGGESTIONS, "swiped": list(SWIPED)}
    html = (KEYBOARD_HTML.replace("__DATA__", json.dumps(data)).replace("__SW__", str(sw))
            .replace("__SH__", str(sh)).replace("__M__", str(MARGIN))
            .replace("__PW__", str(pw)).replace("__PH__", str(ph)))
    html_path, js_path, png = (os.path.join(tmp, n) for n in ("kb.html", "render.js", "kb.png"))
    with open(html_path, "w", encoding="utf-8") as f:
        f.write(html)
    with open(js_path, "w") as f:
        f.write(RENDER_JS)
    subprocess.run(["node", js_path, html_path, png, str(sw), str(sh)], check=True)
    return Image.open(png).convert("RGBA").rotate(KB_ANGLE, Image.BICUBIC, expand=True)


def fit_size(font_path, lines, max_width, start):
    size = start
    while size > 10:
        f = ImageFont.truetype(font_path, size)
        if max(f.getlength(t) for t in lines) <= max_width:
            return f
        size -= 1
    raise SystemExit("text does not fit")


def main():
    src = Image.open(os.path.join(PLAY, "feature-graphic@3x.png")).convert("RGB")
    a = np.asarray(src).astype(float)
    clear_text(a)
    clear_keyboard(a)
    img = Image.fromarray(np.clip(a + 0.5, 0, 255).astype(np.uint8)).convert("RGBA")

    with tempfile.TemporaryDirectory() as tmp:
        kb = render_keyboard(tmp)
    cx, cy = KB_CENTER
    img.alpha_composite(kb, (round(cx - kb.width / 2), round(cy - kb.height / 2)))

    d = ImageDraw.Draw(img)
    tag = fit_size(os.path.join(FONT_DIR, "Inter-Bold.otf"), TAGLINE, 1250 - LEFT, 104)
    for text, base in zip(TAGLINE, TAG_BASE):
        d.text((LEFT, base), text, font=tag, fill=TAG_COLOR, anchor="ls")
    sub = fit_size(os.path.join(FONT_DIR, "Inter-Medium.otf"), [SUBLINE], 1000 - LEFT, 62)
    d.text((LEFT, SUB_BASE), SUBLINE, font=sub, fill=SUB_COLOR, anchor="ls")

    img = img.convert("RGB")
    img.save(os.path.join(PLAY, "feature-graphic-en@3x.png"), optimize=True)
    img.resize((src.width // 3, src.height // 3), Image.LANCZOS).save(
        os.path.join(PLAY, "feature-graphic-en.png"), optimize=True)
    print("wrote docs/play-store/feature-graphic-en@3x.png, feature-graphic-en.png")


if __name__ == "__main__":
    main()
