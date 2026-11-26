#!/usr/bin/env python3
"""Generate the Airbomb-Al dragon artwork.

Draws a sharp, angular dragon head with Pillow (no external assets), then writes:
  * art/dragon-icon.png                        1024x1024 launcher master
  * android/.../mipmap-*/ic_launcher.png       48..192 legacy launcher
  * android/.../mipmap-*/ic_launcher_foreground.png  adaptive foreground
  * android/.../drawable-nodpi/airbomb_logo.png header logo (transparent)

Run:  python3 scripts/make_icon.py
"""

from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
ART = os.path.join(ROOT, "art")

# Palette
BG_TOP = (13, 26, 22, 255)
BG_BOT = (3, 7, 6, 255)
LIGHT = (72, 255, 178, 255)
MID = (18, 208, 142, 255)
DARK = (9, 104, 79, 255)
DEEP = (5, 52, 42, 255)
EYE = (128, 250, 255, 255)
OUTLINE = (3, 15, 12, 255)

# ---- 108x108 design canvas, head faces left -------------------------------
SKULL = [
    (8, 57), (10, 49), (22, 42), (34, 36), (44, 32), (58, 31), (70, 34),
    (84, 33), (90, 44), (96, 52), (103, 70), (94, 84), (74, 86), (66, 74),
    (52, 66), (34, 63), (18, 61),
]
JAW = [
    (17, 62), (34, 65), (52, 68), (66, 73), (69, 83), (50, 85), (31, 77),
    (18, 68),
]
MOUTH = [
    (15, 61), (34, 64), (53, 67), (65, 72), (65, 78), (52, 73), (33, 70),
    (17, 66),
]
HORN_FRONT = [(42, 34), (57, 30), (74, 8), (65, 2)]
HORN_REAR = [(58, 32), (73, 39), (97, 20), (88, 11)]
NECK = [(70, 35), (84, 33), (90, 44), (96, 52), (103, 70), (94, 84), (74, 86),
        (68, 76), (76, 60), (72, 46)]
HORN_FACETS = [
    ([(46, 32), (53, 30), (68, 10), (62, 8)], DEEP),
    ([(63, 33), (69, 37), (91, 20), (86, 15)], MID),
]
SPIKES = [
    [(88, 45), (99, 41), (92, 55)],
    [(93, 57), (105, 55), (96, 67)],
    [(88, 69), (98, 74), (86, 79)],
]
BEARD = [(33, 79), (29, 93), (45, 81)]

CHEEK = [(29, 50), (45, 45), (55, 55), (46, 65), (31, 61)]
SNOUT = [(12, 55), (23, 44), (33, 47), (27, 57), (13, 59)]
BROW = [(24, 41), (41, 37), (44, 42), (26, 47)]
EYE_SOCKET = [(27, 44), (41, 42), (38, 51), (26, 49)]
EYE_SHAPE = [(30, 45), (39, 44), (36, 49)]

TEETH_UP = [
    [(25, 61), (29, 62), (27, 67)],
    [(33, 63), (37, 63), (35, 69)],
    [(41, 64), (45, 65), (43, 71)],
    [(49, 66), (53, 67), (51, 72)],
]
TEETH_DOWN = [
    [(30, 70), (34, 69), (33, 64)],
    [(40, 72), (44, 71), (43, 66)],
]


def scale(points, s: float, ox: float, oy: float):
    return [(ox + x * s, oy + y * s) for x, y in points]


def draw_dragon(size: int, *, background: bool, pad: float = 0.0) -> Image.Image:
    ss = 4  # supersample factor
    S = size * ss
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    if background:
        grad = Image.new("RGBA", (1, S))
        gp = grad.load()
        for y in range(S):
            t = y / max(1, S - 1)
            gp[0, y] = tuple(
                int(BG_TOP[i] + (BG_BOT[i] - BG_TOP[i]) * t) for i in range(4)
            )
        img.paste(grad.resize((S, S)), (0, 0))

    span = 108.0
    s = (S * (1.0 - 2.0 * pad)) / span
    ox = (S - span * s) / 2.0
    oy = ox

    def P(pts):
        return scale(pts, s, ox, oy)

    def w(u: float) -> int:
        return max(1, int(s * u))

    # Neon bloom behind the silhouette.
    glow = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.polygon(P(SKULL), fill=(26, 226, 155, 110))
    gd.polygon(P(JAW), fill=(26, 226, 155, 80))
    glow = glow.filter(ImageFilter.GaussianBlur(radius=S * 0.03))
    img.alpha_composite(glow)

    # Horns, beard and neck spikes sit behind the skull.
    for shape, fill in ((HORN_REAR, DEEP), (HORN_FRONT, DARK)):
        d.polygon(P(shape), fill=fill, outline=OUTLINE, width=w(0.9))
    for sp in SPIKES:
        d.polygon(P(sp), fill=DARK, outline=OUTLINE, width=w(0.7))
    for shape, fill in HORN_FACETS:
        d.polygon(P(shape), fill=fill)
    d.polygon(P(BEARD), fill=DARK, outline=OUTLINE, width=w(0.8))

    # Lower jaw first, then the dark mouth cavity, then the upper skull.
    d.polygon(P(JAW), fill=MID, outline=OUTLINE, width=w(1.0))
    d.polygon(P(MOUTH), fill=(2, 10, 8, 255))

    # Lower fangs rise from the jaw before the skull covers their bases.
    for t in TEETH_DOWN:
        d.polygon(P(t), fill=(222, 255, 244, 255))

    d.polygon(P(SKULL), fill=MID, outline=OUTLINE, width=w(1.0))

    # Facets.
    d.polygon(P(CHEEK), fill=LIGHT)
    d.polygon(P(SNOUT), fill=LIGHT)
    d.polygon(P(BROW), fill=DARK)
    d.polygon(P(NECK), fill=DEEP)
    # Anchor plate where the front horn meets the skull.
    d.polygon(P([(42, 34), (57, 30), (59, 37), (47, 40)]), fill=DARK)

    # Upper fangs.
    for t in TEETH_UP:
        d.polygon(P(t), fill=(222, 255, 244, 255))

    # Eye + nostril.
    d.polygon(P(EYE_SOCKET), fill=OUTLINE)
    d.polygon(P(EYE_SHAPE), fill=EYE)
    gx0, gy0 = P([(32, 46)])[0]
    d.ellipse([gx0, gy0, gx0 + s * 1.1, gy0 + s * 1.1], fill=(255, 255, 255, 255))
    d.polygon(P([(15, 50), (19, 48), (19, 53)]), fill=OUTLINE)

    # Rim light along the top of the snout and skull.
    d.line(P([(9, 55), (22, 42), (34, 36), (44, 32), (58, 31), (70, 34), (84, 33)]),
           fill=(158, 255, 214, 255), width=w(1.1), joint="curve")

    return img.resize((size, size), Image.LANCZOS)


def main() -> None:
    os.makedirs(ART, exist_ok=True)

    master = draw_dragon(1024, background=True, pad=0.09)
    master.save(os.path.join(ART, "dragon-icon.png"))

    logo = draw_dragon(512, background=False, pad=0.01)
    logo_dir = os.path.join(RES, "drawable-nodpi")
    os.makedirs(logo_dir, exist_ok=True)
    logo.save(os.path.join(logo_dir, "airbomb_logo.png"))

    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for name, px in densities.items():
        out = os.path.join(RES, f"mipmap-{name}")
        os.makedirs(out, exist_ok=True)
        master.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher.png"))
        fg_px = int(round(px * 108 / 48))
        fg = draw_dragon(fg_px, background=False, pad=0.22)
        fg.save(os.path.join(out, "ic_launcher_foreground.png"))
        fg.resize((px, px), Image.LANCZOS).save(os.path.join(out, "ic_launcher_round.png"))

    print("wrote", os.path.join(ART, "dragon-icon.png"))


if __name__ == "__main__":
    main()
