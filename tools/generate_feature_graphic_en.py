#!/usr/bin/env python3
"""Make the English Play Store feature graphic from the Korean one.

Reads  docs/play-store/feature-graphic@3x.png (Korean tagline)
Writes docs/play-store/feature-graphic-en@3x.png and feature-graphic-en.png (1024x500)

The Korean tagline and subline are painted out (only the text pixels, refilled from a
smooth fit of the surrounding gradient), then the English text is drawn in the same
place. The icon, the "NewSwipe" title and the keyboard mock-up are left as they are.
Run it again after tools/generate_launcher_icon.py --png redraws the Korean graphic.

Requires: Pillow, numpy, and the Inter font (fonts-inter).
"""
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PLAY = os.path.join(ROOT, "docs", "play-store")
FONT_DIR = "/usr/share/fonts/opentype/inter"

TAGLINE = ["A Korean keyboard you type", "and control by swiping"]
SUBLINE = "Lightweight · Modern · Comfortable"

CLEAR = (180, 860, 1280, 1275)      # x0, y0, x1, y1: box holding the Korean text
LEFT = 222                          # left edge of the Korean text
TAG_BASE = (975, 1098)              # baselines of the two tagline lines
SUB_BASE = 1232
TAG_COLOR = (255, 255, 255)
SUB_COLOR = (217, 226, 249)


def clear_text(a):
    x0, y0, x1, y1 = CLEAR
    reg = a[y0:y1, x0:x1]
    lum = reg.mean(2)
    yy, xx = np.mgrid[0:y1 - y0, 0:x1 - x0]
    u, v = xx / (x1 - x0), yy / (y1 - y0)
    basis = np.stack([np.ones_like(u), u, v, u * u, u * v, v * v, u ** 3, v ** 3, u * u * v, u * v * v], -1)
    keep = np.ones(lum.shape, bool)
    for _ in range(3):  # fit the backdrop, ignoring anything much brighter than it
        coef = np.linalg.lstsq(basis[keep], reg[keep], rcond=None)[0]
        fit = basis @ coef
        keep = lum - fit.mean(2) < 30  # text is far brighter than the faint arcs
    mask = Image.fromarray(((~keep) * 255).astype(np.uint8)).filter(ImageFilter.MaxFilter(9))
    m = (np.asarray(mask.filter(ImageFilter.GaussianBlur(2))) / 255.0)[..., None]
    a[y0:y1, x0:x1] = reg * (1 - m) + fit * m


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
    img = Image.fromarray(np.clip(a + 0.5, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)

    tag = fit_size(os.path.join(FONT_DIR, "Inter-Bold.otf"), TAGLINE, 1250 - LEFT, 104)
    for text, base in zip(TAGLINE, TAG_BASE):
        d.text((LEFT, base), text, font=tag, fill=TAG_COLOR, anchor="ls")
    sub = fit_size(os.path.join(FONT_DIR, "Inter-Medium.otf"), [SUBLINE], 1000 - LEFT, 62)
    d.text((LEFT, SUB_BASE), SUBLINE, font=sub, fill=SUB_COLOR, anchor="ls")

    img.save(os.path.join(PLAY, "feature-graphic-en@3x.png"), optimize=True)
    img.resize((src.width // 3, src.height // 3), Image.LANCZOS).save(
        os.path.join(PLAY, "feature-graphic-en.png"), optimize=True)
    print("wrote docs/play-store/feature-graphic-en@3x.png, feature-graphic-en.png")


if __name__ == "__main__":
    main()
