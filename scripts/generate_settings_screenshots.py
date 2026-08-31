#!/usr/bin/env python3
"""Generate TripWorth Settings and Configurare screenshots for Play Store."""

from PIL import Image, ImageDraw, ImageFont

W, H = 1080, 2400
OUT_DIR = "play-store/assets/screenshots"

BLACK = (11, 11, 13)
SURFACE = (22, 23, 26)
SURFACE_HI = (32, 34, 38)
ORANGE = (255, 148, 39)
YELLOW = (255, 196, 0)
GREEN = (37, 195, 104)
GRAY = (155, 160, 166)
WHITE = (245, 246, 247)


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    paths = [
        "/System/Library/Fonts/SFNSDisplay-Bold.otf" if bold else "/System/Library/Fonts/SFNSDisplay-Regular.otf",
        "/System/Library/Fonts/Supplemental/Arial Bold.ttf" if bold else "/System/Library/Fonts/Supplemental/Arial.ttf",
    ]
    for path in paths:
        try:
            return ImageFont.truetype(path, size)
        except OSError:
            continue
    return ImageFont.load_default()


def rounded_rect(draw, box, radius, fill, outline=None, width=1):
    draw.rounded_rectangle(box, radius=radius, fill=fill, outline=outline, width=width)


def status_bar(draw):
    draw.rectangle((0, 0, W, 80), fill=BLACK)
    draw.text((40, 28), "19:55", fill=WHITE, font=font(32))
    draw.text((880, 28), "VoLTE  74%", fill=WHITE, font=font(28))


def nav_bar(draw):
    draw.rectangle((0, H - 100, W, H), fill=BLACK)
    cx = W // 2
    draw.rounded_rectangle((cx - 28, H - 62, cx + 28, H - 18), radius=8, outline=GRAY, width=3)


def section_label(draw, y, text):
    draw.text((40, y), text.upper(), fill=GRAY, font=font(24, bold=True))
    return y + 40


def card(draw, y, height):
    box = (40, y, W - 40, y + height)
    rounded_rect(draw, box, 20, SURFACE)
    return y + 24


def orange_button(draw, y, text, full=True):
    h = 88
    x1 = 40 if full else 40
    x2 = W - 40 if full else (W // 2) - 10
    rounded_rect(draw, (x1, y, x2, y + h), 18, ORANGE)
    bbox = draw.textbbox((0, 0), text, font=font(30, bold=True))
    tw = bbox[2] - bbox[0]
    draw.text(((x1 + x2 - tw) // 2, y + 26), text, fill=BLACK, font=font(30, bold=True))
    return y + h + 16


def outlined_button(draw, y, text, full=True):
    h = 88
    x1, x2 = 40, W - 40
    rounded_rect(draw, (x1, y, x2, y + h), 18, BLACK, ORANGE, 3)
    bbox = draw.textbbox((0, 0), text, font=font(28, bold=True))
    tw = bbox[2] - bbox[0]
    draw.text(((x1 + x2 - tw) // 2, y + 28), text, fill=ORANGE, font=font(28, bold=True))
    return y + h + 16


def stepper_row(draw, y, label, value, label_left=True):
    if label_left and label:
        draw.text((56, y + 8), label, fill=GRAY, font=font(30))
    draw.text((420, y + 4), value, fill=WHITE, font=font(34, bold=True))
    rounded_rect(draw, (56, y, 120, y + 56), 28, SURFACE_HI, ORANGE, 2)
    draw.text((88, y + 10), "−", fill=YELLOW, font=font(34, bold=True))
    rounded_rect(draw, (W - 156, y, W - 56, y + 56), 28, SURFACE_HI, ORANGE, 2)
    draw.text((W - 120, y + 10), "+", fill=YELLOW, font=font(34, bold=True))
    return y + 68


def chip(draw, x, y, text, selected=False):
    bbox = draw.textbbox((0, 0), text, font=font(26, bold=True))
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    w, h = tw + 40, th + 24
    fill = YELLOW if selected else SURFACE_HI
    fg = BLACK if selected else WHITE
    rounded_rect(draw, (x, y, x + w, y + h), 22, fill, ORANGE if not selected else YELLOW, 2)
    draw.text((x + 20, y + 10), text, fill=fg, font=font(26, bold=True))
    return w + 12


def toggle_row(draw, y, label, on=True):
    draw.text((56, y + 6), label, fill=WHITE, font=font(32))
    tx = W - 130
    rounded_rect(draw, (tx, y, tx + 88, y + 48), 24, GREEN if on else GRAY)
    knob_x = tx + 52 if on else tx + 6
    draw.ellipse((knob_x, y + 6, knob_x + 36, y + 42), fill=WHITE)
    return y + 64


def profile_btn(draw, x, y, line1, line2, active=False):
    w, h = 150, 110
    fill = YELLOW if active else SURFACE_HI
    fg = BLACK if active else WHITE
    rounded_rect(draw, (x, y, x + w, y + h), 16, fill, ORANGE if not active else YELLOW, 2)
    draw.text((x + 16, y + 18), line1, fill=fg, font=font(24, bold=True))
    draw.text((x + 16, y + 54), line2, fill=fg, font=font(22))
    return w + 12


def settings_screen():
    img = Image.new("RGB", (W, H), BLACK)
    draw = ImageDraw.Draw(img)
    status_bar(draw)

    y = 100
    draw.text((40, y), "SETĂRI", fill=YELLOW, font=font(56, bold=True))
    y += 90
    y = orange_button(draw, y, "CONFIGURARE (PERMISIUNI)")
    y = outlined_button(draw, y, "VEZI CUM ARATĂ POPUP-UL")

    y = section_label(draw, y + 8, "Cât vrei pe oră")
    y = card(draw, y, 220)
    draw.text((420, y), "60 RON/oră", fill=YELLOW, font=font(52, bold=True))
    rounded_rect(draw, (56, y + 8, 120, y + 64), 28, SURFACE_HI, ORANGE, 2)
    draw.text((88, y + 18), "−", fill=YELLOW, font=font(34, bold=True))
    rounded_rect(draw, (W - 156, y + 8, W - 56, y + 64), 28, SURFACE_HI, ORANGE, 2)
    draw.text((W - 120, y + 18), "+", fill=YELLOW, font=font(34, bold=True))
    y += 90
    cx = 56
    for preset, sel in [(40, False), (50, False), (60, True), (70, False), (80, False)]:
        cx += chip(draw, cx, y, str(preset), sel)

    y = section_label(draw, y + 100, "Profil rapid")
    y = card(draw, y, 200)
    px = 56
    for l1, l2, act in [("Prudent", "40/oră", False), ("Relaxat", "50/oră", False), ("Echilibrat", "60/oră", True), ("Ambițios", "70/oră", False)]:
        px += profile_btn(draw, px, y, l1, l2, act)
    y += 130
    draw.text((56, y), "Echilibrat — 60 RON/oră. Taie ofertele proaste.", fill=GRAY, font=font(24))

    y = section_label(draw, y + 70, "Mașina")
    y = card(draw, y, 240)
    y = stepper_row(draw, y, "Consum", "7,5 L")
    y = stepper_row(draw, y, "Benzină", "7,30 RON")
    draw.text((56, y), "Cost: 0,85 RON/km", fill=YELLOW, font=font(30, bold=True))

    y = section_label(draw, y + 60, "Popup pe Uber")
    y = card(draw, y, 320)
    y = toggle_row(draw, y, "Arată popup", True)
    y = stepper_row(draw, y, "Text", "100 %")
    y = stepper_row(draw, y, "Durată", "15 sec")
    y = outlined_button(draw, y - 10, "VEZI CUM ARATĂ")

    y = section_label(draw, y + 20, "Comportament")
    y = toggle_row(draw, y, "Citire automată", True)
    y = toggle_row(draw, y, "Sunet", True)
    y = toggle_row(draw, y, "Vibrație", False)

    nav_bar(draw)
    path = f"{OUT_DIR}/tripworth_settings.png"
    img.save(path, "PNG", optimize=True)
    print(f"Saved {path}")


def setup_step(draw, y, num, title, detail, done=True):
    border = GREEN if done else ORANGE
    rounded_rect(draw, (40, y, W - 40, y + 130), 18, SURFACE, border, 2)
    circle_color = GREEN if done else ORANGE
    draw.ellipse((64, y + 34, 124, y + 94), fill=circle_color)
    mark = "✓" if done else str(num)
    fg = WHITE if done else BLACK
    draw.text((82 if done else 88, y + 46), mark, fill=fg, font=font(30, bold=True))
    draw.text((150, y + 28), title, fill=WHITE, font=font(34, bold=True))
    draw.text((150, y + 72), detail, fill=GRAY, font=font(24))
    return y + 145


def configurare_screen():
    img = Image.new("RGB", (W, H), BLACK)
    draw = ImageDraw.Draw(img)
    status_bar(draw)

    y = 100
    draw.text((40, y), "Configurare", fill=ORANGE, font=font(56, bold=True))
    y += 80
    draw.text((40, y), "Totul e gata pentru tură.", fill=GRAY, font=font(30))
    y += 56

    # progress bar
    rounded_rect(draw, (40, y, W - 120, y + 16), 8, SURFACE)
    rounded_rect(draw, (40, y, W - 120, y + 16), 8, ORANGE)
    draw.text((W - 100, y - 8), "3/3", fill=YELLOW, font=font(32, bold=True))
    y += 48

    y = setup_step(draw, y, 1, "Notificări", "Activate. Serviciul de citire poate rula în fundal.", True)
    y = setup_step(draw, y, 2, "Afișare peste alte aplicații", "Acordată. Cardul apare sus, peste Uber și Bolt.", True)
    y = setup_step(draw, y, 3, "Baterie fără restricții", "TripWorth e exceptat. Citirea nu va fi oprită.", True)

    # auto start card
    rounded_rect(draw, (40, y, W - 40, y + 130), 18, SURFACE, ORANGE, 2)
    draw.text((56, y + 28), "Pornire automată", fill=WHITE, font=font(34, bold=True))
    draw.text((56, y + 72), "Citește ofertele și arată cardul fără să deschizi TripWorth.", fill=GRAY, font=font(24))
    tx = W - 130
    rounded_rect(draw, (tx, y + 40, tx + 88, y + 88), 24, GREEN)
    draw.ellipse((tx + 52, y + 46, tx + 82, y + 76), fill=WHITE)
    y += 160

    y = orange_button(draw, y, "GATA")
    y = outlined_button(draw, y, "ÎNAPOI LA SETĂRI")

    # branding footer
    draw.text((40, y + 20), "TripWorth", fill=ORANGE, font=font(40, bold=True))
    draw.text((40, y + 70), "Merită cursa?", fill=GRAY, font=font(28))

    nav_bar(draw)
    path = f"{OUT_DIR}/tripworth_configurare.png"
    img.save(path, "PNG", optimize=True)
    print(f"Saved {path}")


if __name__ == "__main__":
    settings_screen()
    configurare_screen()
