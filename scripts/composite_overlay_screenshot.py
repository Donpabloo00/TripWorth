#!/usr/bin/env python3
"""Composite TripWorth overlay on top of a real Uber driver screenshot."""

from PIL import Image, ImageDraw, ImageFont

SRC = "ridego_demo.png"
OUT = "play-store/assets/screenshots/tripworth_overlay_uber.png"

# Theme colors
BLACK = (11, 11, 13)
SURFACE = (22, 23, 26)
ORANGE = (255, 148, 39)
GREEN = (37, 195, 104)
GRAY = (155, 160, 166)
WHITE = (245, 246, 247)
RED = (226, 61, 61)

W, H = 1080, 2400
MARGIN_X = 24
CARD_W = W - 2 * MARGIN_X
CARD_TOP = 120
CARD_H = 520
RADIUS = 28


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    paths = [
        "/System/Library/Fonts/SFNSDisplay-Bold.otf" if bold else "/System/Library/Fonts/SFNSDisplay-Regular.otf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
        "/Library/Fonts/Arial Bold.ttf" if bold else "/Library/Fonts/Arial.ttf",
    ]
    for path in paths:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded_rect(draw, xy, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)


def main():
    base = Image.open(SRC).convert("RGBA")
    base = base.resize((W, H))

    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    # Card background + orange border
    card_box = (MARGIN_X, CARD_TOP, MARGIN_X + CARD_W, CARD_TOP + CARD_H)
    rounded_rect(draw, card_box, RADIUS, SURFACE + (245,), ORANGE, 4)

    x = MARGIN_X + 28
    y = CARD_TOP + 24

    draw.text((x, y), "TRIPWORTH  •  UBER", fill=GRAY, font=font(28))

    # Green verdict badge (top right)
    badge_text = "●  CURSĂ BUNĂ"
    badge_font = font(26, bold=True)
    bbox = draw.textbbox((0, 0), badge_text, font=badge_font)
    badge_w = bbox[2] - bbox[0] + 36
    badge_h = 52
    badge_x = MARGIN_X + CARD_W - badge_w - 24
    badge_y = y - 4
    rounded_rect(draw, (badge_x, badge_y, badge_x + badge_w, badge_y + badge_h), 26, GREEN)
    draw.text((badge_x + 18, badge_y + 10), badge_text, fill=WHITE, font=badge_font)

    y += 56
    draw.text((x, y), "3,13 RON/km", fill=ORANGE, font=font(72, bold=True))
    draw.text((x + 420, y + 28), "≈ 78 RON/h", fill=GRAY, font=font(34))

    y += 110
    col_w = (CARD_W - 56) // 3
    metrics = [
        ("97 RON/oră", GRAY),
        ("+8,40 PROFIT", GREEN),
        ("2,10 COST COMB.", GRAY),
    ]
    for i, (text, color) in enumerate(metrics):
        cx = x + i * col_w
        draw.text((cx, y), text, fill=color, font=font(26, bold=(i == 1)))

    y += 56
    draw.text((x, y), "Încasezi  12,52 RON", fill=WHITE, font=font(34, bold=True))

    y += 52
    draw.text((x, y), "Distanță la client  3,2 km  •  7 min", fill=GRAY, font=font(28))

    y += 44
    draw.text((x, y), "Cursă  4,0 km  •  7 min  •  Str. Libertății, Glina", fill=GRAY, font=font(24))

    result = Image.alpha_composite(base, overlay).convert("RGB")
    result.save(OUT, "PNG", optimize=True)
    print(f"Saved {OUT} ({W}x{H})")


if __name__ == "__main__":
    main()
