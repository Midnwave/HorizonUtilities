"""
Generates 20 detailed 32x32 pixel art PNG textures for Hollow Chronicle Stage 1 items.
Proper pixel art: hand-placed pixels, Minecraft-style top-left lighting, outlines,
shading, highlights, dithering. No anti-aliasing.

Usage: python generate_textures.py
Output: 20 PNG files in the current directory.
Requires: Pillow (pip install Pillow)
"""

from PIL import Image
import os, math

OUTPUT_DIR = os.path.dirname(os.path.abspath(__file__))
T = (0, 0, 0, 0)  # Transparent


def px(img, x, y, color):
    """Set a single pixel with bounds checking."""
    if 0 <= x < 32 and 0 <= y < 32 and color != T:
        img.putpixel((x, y), color)


def shade(base, factor):
    """Darken or lighten a color. factor < 1 = darker, > 1 = lighter."""
    return tuple(max(0, min(255, int(c * factor))) for c in base[:3]) + ((base[3],) if len(base) == 4 else ())


def fill_rect(img, x1, y1, x2, y2, color):
    for yy in range(y1, y2 + 1):
        for xx in range(x1, x2 + 1):
            px(img, xx, yy, color)


def outline_rect(img, x1, y1, x2, y2, color):
    for xx in range(x1, x2 + 1):
        px(img, xx, y1, color)
        px(img, xx, y2, color)
    for yy in range(y1, y2 + 1):
        px(img, x1, yy, color)
        px(img, x2, yy, color)


def fill_ellipse(img, cx, cy, rx, ry, color):
    for yy in range(-ry, ry + 1):
        for xx in range(-rx, rx + 1):
            if (xx * xx) / max(rx * rx, 1) + (yy * yy) / max(ry * ry, 1) <= 1.0:
                px(img, cx + xx, cy + yy, color)


def outline_ellipse(img, cx, cy, rx, ry, color):
    for angle in range(360):
        rad = math.radians(angle)
        xx = round(cx + rx * math.cos(rad))
        yy = round(cy + ry * math.sin(rad))
        px(img, xx, yy, color)


def fill_diamond(img, cx, cy, rx, ry, color):
    for yy in range(-ry, ry + 1):
        span = round(rx * (1 - abs(yy) / ry))
        for xx in range(-span, span + 1):
            px(img, cx + xx, cy + yy, color)


def draw_line(img, x0, y0, x1, y1, color):
    dx = abs(x1 - x0)
    dy = abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx - dy
    while True:
        px(img, x0, y0, color)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 > -dy:
            err -= dy
            x0 += sx
        if e2 < dx:
            err += dx
            y0 += sy


def dither_rect(img, x1, y1, x2, y2, c1, c2):
    """Checkerboard dither two colors."""
    for yy in range(y1, y2 + 1):
        for xx in range(x1, x2 + 1):
            px(img, xx, yy, c1 if (xx + yy) % 2 == 0 else c2)


# ============================================================================
# Individual texture functions — real pixel art
# ============================================================================

def draw_vareths_insignia(img):
    """Diamond-shaped military badge, cracked dark red enamel, silver border broken at corner."""
    outline = (50, 10, 10)
    dark_red = (139, 0, 0)
    mid_red = (170, 30, 30)
    highlight = (200, 60, 60)
    silver = (180, 180, 190)
    silver_dk = (130, 130, 140)
    crack = (60, 60, 65)

    # Silver border diamond
    fill_diamond(img, 15, 15, 11, 11, silver_dk)
    fill_diamond(img, 15, 15, 10, 10, silver)
    # Inner enamel
    fill_diamond(img, 15, 15, 8, 8, dark_red)
    fill_diamond(img, 15, 15, 7, 7, mid_red)
    # Top-left highlight on enamel
    fill_diamond(img, 13, 12, 3, 3, highlight)
    # Outline
    for i in range(12):
        t = i / 11
        px(img, round(15 - 11 * (1 - t)), round(15 - 11 * t), outline)
        px(img, round(15 + 11 * t), round(15 - 11 * (1 - t)), outline)
        px(img, round(15 + 11 * (1 - t)), round(15 + 11 * t), outline)
        px(img, round(15 - 11 * t), round(15 + 11 * (1 - t)), outline)
    # Crack running through
    draw_line(img, 11, 9, 14, 14, crack)
    draw_line(img, 14, 14, 13, 17, crack)
    draw_line(img, 13, 17, 19, 23, crack)
    # Broken corner — chip at bottom-right
    for dy in range(3):
        for dx in range(3 - dy):
            px(img, 22 + dx, 18 + dy, T)
    # Tiny highlight sparkle on silver, top-left
    px(img, 8, 11, (220, 220, 230))
    px(img, 9, 10, (240, 240, 250))


def draw_ashen_stone(img):
    """Irregular rounded stone, charcoal base, ember glow cracks, ash particles."""
    dk_gray = (55, 50, 48)
    mid_gray = (85, 78, 72)
    lt_gray = (110, 102, 95)
    highlight = (130, 122, 112)
    ember = (255, 99, 50)
    ember_dk = (200, 60, 20)
    ash = (160, 150, 140)

    # Outer shape — irregular blob
    for y in range(8, 28):
        fy = (y - 18) / 10
        width = round(9 * (1 - fy * fy) + 1)
        offset = (1 if y % 3 == 0 else 0) - (1 if y % 5 == 0 else 0)
        for x in range(16 - width + offset, 16 + width + offset):
            shade_f = 0.8 + 0.2 * ((16 - x) / max(width, 1))
            c = shade(mid_gray, shade_f + 0.15 * ((18 - y) / 10))
            px(img, x, y, c)

    # Darker bottom edge
    for x in range(9, 23):
        px(img, x, 26, dk_gray)
        px(img, x, 27, dk_gray)
    # Highlight top-left
    for x in range(11, 17):
        px(img, x, 10, highlight)
        px(img, x, 11, lt_gray)
    px(img, 10, 12, highlight)
    px(img, 11, 11, (145, 138, 128))

    # Outline
    for y in range(8, 28):
        fy = (y - 18) / 10
        w = round(9 * (1 - fy * fy) + 1)
        off = (1 if y % 3 == 0 else 0) - (1 if y % 5 == 0 else 0)
        px(img, 16 - w + off, y, dk_gray)
        px(img, 16 + w + off - 1, y, dk_gray)

    # Ember cracks
    draw_line(img, 12, 14, 15, 18, ember_dk)
    draw_line(img, 15, 18, 19, 22, ember)
    draw_line(img, 14, 20, 17, 16, ember_dk)
    px(img, 13, 15, ember)
    px(img, 16, 17, ember)
    px(img, 18, 21, ember)

    # Floating ash particles
    px(img, 13, 5, ash)
    px(img, 18, 3, ash)
    px(img, 10, 6, (140, 130, 120))
    px(img, 20, 5, (120, 110, 100))


def draw_wardens_key(img):
    """Ornate key diagonal, silver shaft, indigo gemstone in bow, runic teeth, cyan glow."""
    silver = (185, 185, 195)
    silver_dk = (130, 130, 145)
    silver_hi = (220, 220, 235)
    outline = (40, 35, 50)
    indigo = (75, 0, 130)
    indigo_lt = (120, 50, 180)
    cyan = (0, 220, 230)
    cyan_glow = (100, 240, 245, 140)

    # Shaft — diagonal from bottom-left to top-right
    for i in range(16):
        x = 7 + i
        y = 25 - i
        px(img, x, y, silver_dk)
        px(img, x + 1, y, silver)
        px(img, x, y - 1, silver_hi if i < 4 else silver)

    # Outline shaft
    for i in range(16):
        x = 7 + i
        y = 25 - i
        px(img, x - 1, y, outline)
        px(img, x + 2, y, outline)
        px(img, x, y + 1, outline)

    # Bow (oval ring at top)
    fill_ellipse(img, 22, 9, 5, 5, silver)
    fill_ellipse(img, 22, 9, 3, 3, outline)  # Hollow center
    fill_ellipse(img, 22, 9, 2, 2, T)  # Punch out center
    outline_ellipse(img, 22, 9, 5, 5, outline)

    # Gemstone in bow center
    fill_ellipse(img, 22, 9, 2, 2, indigo)
    px(img, 21, 8, indigo_lt)
    px(img, 22, 8, indigo_lt)

    # Teeth at bottom
    px(img, 6, 26, silver)
    px(img, 5, 27, silver)
    px(img, 5, 26, outline)
    px(img, 8, 27, silver)
    px(img, 9, 28, silver)
    px(img, 9, 27, outline)
    px(img, 7, 28, silver_dk)

    # Cyan glow around gemstone
    for dx in range(-3, 4):
        for dy in range(-3, 4):
            d = math.sqrt(dx * dx + dy * dy)
            if 2.5 < d < 4:
                gx, gy = 22 + dx, 9 + dy
                existing = img.getpixel((gx, gy)) if 0 <= gx < 32 and 0 <= gy < 32 else T
                if existing == T or existing[3] == 0:
                    alpha = int(180 * (1 - (d - 2.5) / 1.5))
                    px(img, gx, gy, (0, 220, 230, alpha))

    # Runic notch on shaft
    px(img, 13, 19, cyan)
    px(img, 15, 17, cyan)


def draw_soul_of_the_vigil(img):
    """Teardrop crystal, white core radiating through cyan, floating wisp particles."""
    dk_cyan = (0, 100, 110)
    cyan = (0, 185, 195)
    lt_cyan = (80, 220, 230)
    pale = (180, 245, 250)
    white = (240, 255, 255)
    core = (255, 255, 255)
    outline = (0, 60, 70)
    wisp = (150, 240, 245, 180)

    # Teardrop body
    for y in range(4, 28):
        t = (y - 4) / 24
        if t < 0.3:
            w = round(1 + 7 * (t / 0.3))
        else:
            w = round(8 * (1 - (t - 0.3) / 0.7))
        w = max(0, w)
        for x in range(16 - w, 16 + w):
            dist = abs(x - 16) / max(w, 1)
            vertical = (16 - y) / 12
            if dist < 0.3 and 0.1 < t < 0.6:
                px(img, x, y, white)
            elif dist < 0.5:
                px(img, x, y, pale if vertical > 0.2 else lt_cyan)
            else:
                px(img, x, y, cyan if vertical > 0 else dk_cyan)

    # Core — bright center
    for dy in range(-3, 4):
        for dx in range(-2, 3):
            if abs(dx) + abs(dy) <= 3:
                px(img, 15 + dx, 14 + dy, core)
    fill_ellipse(img, 15, 14, 1, 2, core)

    # Outline
    for y in range(4, 28):
        t = (y - 4) / 24
        if t < 0.3:
            w = round(1 + 7 * (t / 0.3))
        else:
            w = round(8 * (1 - (t - 0.3) / 0.7))
        w = max(0, w)
        if w > 0:
            px(img, 16 - w - 1, y, outline)
            px(img, 16 + w, y, outline)
    for x in range(14, 18):
        px(img, x, 3, outline)
    px(img, 15, 28, outline)

    # Floating wisp particles
    px(img, 10, 7, wisp)
    px(img, 21, 5, wisp)
    px(img, 8, 12, (120, 230, 240, 120))
    px(img, 23, 10, (120, 230, 240, 120))
    px(img, 19, 3, wisp)
    px(img, 12, 2, (100, 210, 220, 100))


def draw_vareths_sigil(img):
    """Circular seal, gold-trimmed purple fill, geometric eye pattern, medal ribbon."""
    outline = (30, 10, 40)
    purple_dk = (60, 0, 100)
    purple = (100, 0, 160)
    purple_lt = (140, 50, 200)
    gold = (218, 175, 50)
    gold_dk = (170, 130, 30)
    gold_hi = (250, 220, 100)

    # Gold ring
    fill_ellipse(img, 15, 16, 11, 11, gold_dk)
    fill_ellipse(img, 15, 16, 10, 10, gold)
    # Purple fill
    fill_ellipse(img, 15, 16, 8, 8, purple_dk)
    fill_ellipse(img, 15, 16, 7, 7, purple)
    # Highlight on purple
    fill_ellipse(img, 13, 13, 3, 3, purple_lt)

    # Geometric eye pattern in center
    # Horizontal eye shape
    draw_line(img, 10, 16, 15, 13, gold)
    draw_line(img, 15, 13, 20, 16, gold)
    draw_line(img, 20, 16, 15, 19, gold)
    draw_line(img, 15, 19, 10, 16, gold)
    # Pupil
    fill_ellipse(img, 15, 16, 2, 2, gold_hi)
    px(img, 15, 16, purple_dk)

    # Outer outline
    outline_ellipse(img, 15, 16, 11, 11, outline)
    # Gold highlight top-left
    px(img, 7, 11, gold_hi)
    px(img, 8, 9, gold_hi)
    px(img, 9, 8, gold_hi)

    # Tiny ribbon at top
    fill_rect(img, 13, 3, 17, 5, gold_dk)
    px(img, 14, 4, gold)
    px(img, 16, 4, gold)


def draw_rune_of_ruin(img):
    """Rough stone tablet, glowing orange-red carved rune, cracks radiating."""
    stone_dk = (50, 48, 45)
    stone = (80, 75, 68)
    stone_lt = (105, 98, 88)
    stone_hi = (125, 118, 105)
    outline = (30, 28, 25)
    rune = (255, 80, 0)
    rune_dk = (180, 40, 0)
    rune_glow = (255, 140, 50, 160)

    # Rough stone slab
    for y in range(5, 28):
        for x in range(8, 24):
            noise = ((x * 7 + y * 13) % 5) - 2
            base = stone_lt if y < 12 else (stone if y < 22 else stone_dk)
            c = shade(base, 1.0 + noise * 0.04)
            px(img, x, y, c)

    # Roughen edges
    for y in [5, 6]:
        px(img, 8, y, T)
        px(img, 23, y, T)
    for y in [26, 27]:
        px(img, 8, y, T)
        px(img, 23, y, T)
    px(img, 9, 5, T)
    px(img, 22, 27, T)

    # Outline
    for y in range(5, 28):
        px(img, 7, y, outline)
        px(img, 24, y, outline)
    for x in range(8, 24):
        px(img, x, 4, outline)
        px(img, x, 28, outline)

    # Highlight
    for x in range(10, 22):
        px(img, x, 6, stone_hi)

    # Carved rune — angular symbol
    draw_line(img, 13, 9, 18, 9, rune_dk)   # Top bar
    draw_line(img, 15, 9, 15, 24, rune)      # Vertical stroke
    draw_line(img, 12, 14, 18, 14, rune)     # Middle bar
    draw_line(img, 12, 21, 18, 21, rune_dk)  # Bottom bar
    draw_line(img, 12, 14, 12, 21, rune_dk)  # Left vertical
    draw_line(img, 18, 9, 18, 14, rune)      # Right upper
    # Glow around rune
    for gy in range(8, 25):
        for gx in range(11, 20):
            existing = img.getpixel((gx, gy))
            if existing[:3] == rune[:3] or existing[:3] == rune_dk[:3]:
                for dx, dy in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
                    nx, ny = gx + dx, gy + dy
                    if 0 <= nx < 32 and 0 <= ny < 32:
                        ep = img.getpixel((nx, ny))
                        if ep[:3] != rune[:3] and ep[:3] != rune_dk[:3] and ep != T:
                            px(img, nx, ny, shade(stone, 1.15))

    # Cracks radiating from rune
    draw_line(img, 18, 14, 22, 11, stone_dk)
    draw_line(img, 12, 21, 9, 25, stone_dk)


def draw_veilborn_compass(img):
    """Antique compass face, dark slate body, gold needle pointing NE, cyan pulse ring."""
    slate = (60, 80, 80)
    slate_lt = (80, 100, 100)
    silver = (175, 175, 185)
    silver_dk = (130, 130, 140)
    gold = (218, 165, 32)
    gold_hi = (250, 200, 80)
    cyan = (0, 200, 230)
    cyan_glow = (0, 180, 210, 100)
    outline = (30, 40, 42)
    face = (220, 215, 200)
    face_dk = (190, 185, 170)

    # Body
    fill_ellipse(img, 15, 16, 12, 12, outline)
    fill_ellipse(img, 15, 16, 11, 11, slate)
    fill_ellipse(img, 15, 16, 10, 10, slate_lt)
    # Silver bezel
    outline_ellipse(img, 15, 16, 10, 10, silver_dk)
    outline_ellipse(img, 15, 16, 11, 11, silver)

    # Face
    fill_ellipse(img, 15, 16, 8, 8, face_dk)
    fill_ellipse(img, 15, 16, 7, 7, face)

    # Cardinal marks
    px(img, 15, 9, outline)   # N
    px(img, 15, 23, outline)  # S
    px(img, 8, 16, outline)   # W
    px(img, 22, 16, outline)  # E
    # Minor ticks
    px(img, 11, 11, silver_dk)
    px(img, 19, 11, silver_dk)
    px(img, 11, 21, silver_dk)
    px(img, 19, 21, silver_dk)

    # Needle — gold pointing NE
    draw_line(img, 15, 16, 20, 11, gold)
    draw_line(img, 15, 16, 19, 12, gold_hi)
    # Opposite end — silver
    draw_line(img, 15, 16, 11, 20, silver_dk)

    # Center pin
    px(img, 15, 16, gold)
    px(img, 14, 16, silver)
    px(img, 16, 16, silver)

    # Cyan pulse ring (subtle glow)
    for angle in range(0, 360, 8):
        rad = math.radians(angle)
        rx = round(15 + 9 * math.cos(rad))
        ry = round(16 + 9 * math.sin(rad))
        px(img, rx, ry, cyan_glow)

    # Highlight
    px(img, 10, 10, (200, 200, 210))
    px(img, 9, 11, silver)


def draw_evelins_record_book(img):
    """Small leather journal, burlywood pages, blue bookmark ribbon, water stain."""
    leather = (120, 60, 20)
    leather_dk = (80, 40, 12)
    leather_lt = (150, 85, 40)
    pages = (222, 200, 160)
    pages_dk = (195, 175, 140)
    pages_edge = (240, 225, 190)
    blue = (65, 105, 225)
    blue_dk = (45, 75, 180)
    outline = (40, 20, 5)
    stain = (140, 130, 110)

    # Book body — slightly angled
    # Back cover
    fill_rect(img, 8, 6, 24, 27, leather_dk)
    # Pages visible at right edge
    for y in range(8, 26):
        px(img, 24, y, pages_dk)
        px(img, 25, y, pages_edge)
    # Front cover
    fill_rect(img, 7, 5, 23, 26, leather)
    # Cover highlight top-left
    fill_rect(img, 8, 6, 14, 9, leather_lt)
    px(img, 9, 7, leather_lt)

    # Spine
    fill_rect(img, 7, 5, 8, 26, leather_dk)
    px(img, 7, 5, outline)
    px(img, 7, 26, outline)

    # Page edges visible at bottom
    for x in range(9, 24):
        px(img, x, 26, pages_dk)

    # Outline
    outline_rect(img, 7, 5, 23, 26, outline)
    for y in range(6, 26):
        px(img, 25, y, outline)

    # Bookmark ribbon
    px(img, 20, 3, blue)
    px(img, 20, 4, blue)
    px(img, 20, 5, blue_dk)
    px(img, 19, 3, blue_dk)

    # Water stain on cover — subtle discoloration
    for dy in range(4):
        for dx in range(5):
            if dx + dy < 5:
                px(img, 12 + dx, 14 + dy, stain)

    # Stitching detail on spine
    for y in range(8, 24, 3):
        px(img, 8, y, leather_lt)

    # Page lines hint
    for y in range(10, 22, 2):
        draw_line(img, 11, y, 20, y, pages_dk)


def draw_hollows_ward(img):
    """Circular pendant charm, silver frame, purple gemstone center, aura glow, chain."""
    silver = (185, 185, 200)
    silver_dk = (130, 130, 150)
    silver_hi = (225, 225, 240)
    purple = (120, 50, 180)
    purple_dk = (75, 20, 130)
    purple_lt = (170, 100, 220)
    outline = (40, 30, 55)
    aura = (147, 112, 219, 100)
    chain = (150, 150, 160)

    # Chain links at top
    for i in range(3):
        px(img, 14 + i, 2, chain)
        px(img, 14 + i, 4, chain)
    px(img, 14, 3, chain)
    px(img, 16, 3, chain)
    draw_line(img, 15, 4, 15, 7, chain)

    # Aura glow
    for dy in range(-8, 9):
        for dx in range(-8, 9):
            d = math.sqrt(dx * dx + dy * dy)
            if 6 < d < 9:
                alpha = int(80 * (1 - (d - 6) / 3))
                px(img, 15 + dx, 17 + dy, (147, 112, 219, alpha))

    # Silver frame
    fill_ellipse(img, 15, 17, 8, 8, silver_dk)
    fill_ellipse(img, 15, 17, 7, 7, silver)
    outline_ellipse(img, 15, 17, 8, 8, outline)

    # Inner purple gem
    fill_ellipse(img, 15, 17, 5, 5, purple_dk)
    fill_ellipse(img, 15, 17, 4, 4, purple)
    fill_ellipse(img, 14, 15, 2, 2, purple_lt)
    # Gem highlight
    px(img, 13, 14, (200, 160, 240))
    px(img, 14, 14, purple_lt)

    # Silver highlight
    px(img, 9, 12, silver_hi)
    px(img, 10, 11, silver_hi)


def draw_vigil_shard(img):
    """Angular crystal shard, light gray body, blue diagonal sheen, sparkle at tip."""
    gray_dk = (140, 140, 150)
    gray = (190, 190, 200)
    gray_lt = (220, 220, 230)
    blue = (135, 200, 235)
    blue_lt = (180, 230, 250)
    white = (250, 250, 255)
    outline = (80, 80, 95)

    # Main shard shape — tall narrow crystal
    pts = [(15, 3), (20, 12), (19, 22), (16, 29), (13, 22), (11, 14)]
    # Fill using scanline
    for y in range(3, 30):
        # Find left and right edges
        left, right = 32, 0
        for i in range(len(pts)):
            x0, y0 = pts[i]
            x1, y1 = pts[(i + 1) % len(pts)]
            if y0 == y1:
                continue
            if min(y0, y1) <= y <= max(y0, y1):
                t = (y - y0) / (y1 - y0)
                x = x0 + t * (x1 - x0)
                left = min(left, x)
                right = max(right, x)
        for x in range(int(left), int(right) + 1):
            # Shading: lighter toward left, darker toward right
            frac = (x - left) / max(right - left, 1)
            if frac < 0.4:
                px(img, x, y, gray_lt)
            elif frac < 0.7:
                px(img, x, y, gray)
            else:
                px(img, x, y, gray_dk)

    # Blue diagonal sheen
    draw_line(img, 13, 8, 18, 18, blue)
    draw_line(img, 14, 9, 19, 19, blue_lt)
    draw_line(img, 13, 10, 17, 16, blue)

    # Outline
    for i in range(len(pts)):
        x0, y0 = pts[i]
        x1, y1 = pts[(i + 1) % len(pts)]
        draw_line(img, x0, y0, x1, y1, outline)

    # Sparkle at tip
    px(img, 15, 2, white)
    px(img, 14, 3, white)
    px(img, 16, 3, white)
    px(img, 15, 4, blue_lt)


def draw_forgotten_coin(img):
    """Ancient coin at slight angle, embossed crown pattern, green patina on edge."""
    gold = (200, 160, 40)
    gold_dk = (160, 120, 20)
    gold_lt = (235, 200, 80)
    gold_hi = (255, 230, 130)
    outline = (90, 65, 10)
    patina = (80, 120, 70)
    patina_lt = (100, 140, 90)
    emboss = (170, 135, 30)

    # Coin body — slight perspective (wider than tall)
    fill_ellipse(img, 15, 16, 10, 8, gold_dk)
    fill_ellipse(img, 15, 16, 9, 7, gold)
    outline_ellipse(img, 15, 16, 10, 8, outline)

    # Top-left highlight
    fill_ellipse(img, 12, 13, 4, 3, gold_lt)
    px(img, 11, 12, gold_hi)
    px(img, 10, 13, gold_hi)

    # Embossed crown pattern
    # Crown base
    draw_line(img, 12, 18, 18, 18, emboss)
    # Crown points
    px(img, 13, 14, emboss)
    px(img, 15, 13, emboss)
    px(img, 17, 14, emboss)
    draw_line(img, 12, 18, 13, 14, emboss)
    draw_line(img, 13, 14, 15, 16, emboss)
    draw_line(img, 15, 16, 15, 13, emboss)
    draw_line(img, 15, 13, 15, 16, emboss)
    draw_line(img, 15, 16, 17, 14, emboss)
    draw_line(img, 17, 14, 18, 18, emboss)

    # Green patina
    for angle in range(180, 300):
        rad = math.radians(angle)
        rx = round(15 + 10 * math.cos(rad))
        ry = round(16 + 8 * math.sin(rad))
        px(img, rx, ry, patina)
        rx2 = round(15 + 9 * math.cos(rad))
        ry2 = round(16 + 7 * math.sin(rad))
        if (angle % 3) == 0:
            px(img, rx2, ry2, patina_lt)


def draw_veilborn_rations(img):
    """Wrapped bread bundle, tan bread visible, gray cloth wrap, string tie."""
    cloth = (120, 115, 105)
    cloth_dk = (90, 85, 78)
    cloth_lt = (145, 140, 130)
    bread = (210, 180, 130)
    bread_dk = (175, 145, 100)
    bread_lt = (235, 210, 165)
    string = (160, 140, 100)
    outline = (55, 50, 42)

    # Cloth wrap — rounded rectangle
    fill_ellipse(img, 15, 18, 10, 8, cloth_dk)
    fill_ellipse(img, 15, 18, 9, 7, cloth)
    # Cloth folds
    draw_line(img, 8, 15, 12, 22, cloth_dk)
    draw_line(img, 18, 14, 22, 21, cloth_dk)
    # Highlight
    fill_ellipse(img, 12, 15, 3, 2, cloth_lt)

    # Bread visible at top
    fill_ellipse(img, 15, 12, 7, 5, bread_dk)
    fill_ellipse(img, 15, 12, 6, 4, bread)
    fill_ellipse(img, 13, 10, 3, 2, bread_lt)

    # Bread texture dots
    px(img, 14, 11, bread_dk)
    px(img, 17, 13, bread_dk)
    px(img, 12, 13, bread_dk)

    # Outline
    outline_ellipse(img, 15, 18, 10, 8, outline)
    outline_ellipse(img, 15, 12, 7, 5, outline)

    # String tie — X pattern
    draw_line(img, 12, 20, 18, 24, string)
    draw_line(img, 18, 20, 12, 24, string)
    px(img, 15, 22, (180, 160, 120))

    # Crumbs
    px(img, 22, 10, bread_dk)
    px(img, 24, 12, bread_dk)


def draw_cracked_veilborn_sigil(img):
    """Olive-green circular sigil, cracked diagonally, missing corner chunk."""
    olive = (85, 107, 47)
    olive_dk = (55, 75, 30)
    olive_lt = (115, 140, 70)
    gray = (105, 105, 105)
    outline = (35, 45, 20)
    crack = (30, 30, 28)
    gold = (170, 140, 40)

    # Main body
    fill_ellipse(img, 15, 16, 10, 10, olive_dk)
    fill_ellipse(img, 15, 16, 9, 9, olive)
    outline_ellipse(img, 15, 16, 10, 10, outline)

    # Gold trim
    outline_ellipse(img, 15, 16, 10, 10, gold)

    # Highlight
    fill_ellipse(img, 12, 13, 3, 3, olive_lt)

    # Inner pattern
    fill_ellipse(img, 15, 16, 4, 4, olive_dk)
    fill_ellipse(img, 15, 16, 2, 2, olive)

    # Diagonal crack
    draw_line(img, 8, 8, 22, 24, crack)
    draw_line(img, 9, 8, 23, 24, crack)
    # Side cracks
    draw_line(img, 14, 14, 11, 16, crack)
    draw_line(img, 18, 18, 20, 15, crack)

    # Missing chunk — top right
    for dy in range(5):
        for dx in range(5 - dy):
            px(img, 22 + dx, 8 + dy, T)
            if dx + dy == 4 - 1:
                px(img, 22 + dx, 8 + dy, outline)


def draw_miras_last_rose(img):
    """Wither rose shape but pink/magenta instead of black, gray wither-touched petal edges, falling petal."""
    pink = (255, 105, 180)
    magenta = (220, 50, 140)
    magenta_dk = (160, 30, 100)
    gray = (105, 100, 98)
    gray_dk = (70, 68, 65)
    green = (34, 100, 34)
    green_dk = (20, 65, 20)
    outline = (60, 20, 40)
    petal_fade = (180, 80, 130)

    # Stem
    draw_line(img, 15, 19, 15, 29, green_dk)
    draw_line(img, 16, 20, 16, 29, green)
    # Thorns
    px(img, 14, 23, green_dk)
    px(img, 17, 26, green_dk)
    # Leaves
    px(img, 13, 24, green)
    px(img, 12, 25, green)
    px(img, 18, 27, green)

    # Flower head — layered petals
    # Back petals
    fill_ellipse(img, 12, 11, 4, 3, magenta_dk)
    fill_ellipse(img, 18, 11, 4, 3, magenta_dk)
    # Middle petals
    fill_ellipse(img, 15, 9, 5, 3, magenta)
    fill_ellipse(img, 11, 14, 3, 4, magenta)
    fill_ellipse(img, 19, 14, 3, 4, magenta)
    # Front petals
    fill_ellipse(img, 15, 13, 4, 4, pink)
    fill_ellipse(img, 13, 11, 3, 3, pink)
    fill_ellipse(img, 17, 11, 3, 3, pink)

    # Center
    fill_ellipse(img, 15, 12, 2, 2, magenta_dk)
    px(img, 15, 12, (200, 40, 100))

    # Wither-touched gray edges on petals
    px(img, 8, 12, gray)
    px(img, 9, 10, gray)
    px(img, 21, 10, gray)
    px(img, 22, 12, gray)
    px(img, 15, 6, gray)
    px(img, 14, 7, gray_dk)
    px(img, 10, 15, gray)
    px(img, 20, 15, gray)

    # Falling petal
    fill_ellipse(img, 22, 22, 2, 1, petal_fade)
    px(img, 23, 21, petal_fade)

    # Outline hints
    outline_ellipse(img, 15, 12, 7, 6, outline)


def draw_ashen_dust(img):
    """Small powder pile, gray-brown, floating particles above, reddish tint."""
    brown = (130, 100, 70)
    brown_dk = (90, 70, 48)
    brown_lt = (160, 130, 95)
    gray = (110, 105, 95)
    red_tint = (150, 90, 60)
    particle = (170, 155, 135)
    particle_lt = (195, 180, 160)
    outline = (60, 48, 32)

    # Mound shape
    for y in range(18, 28):
        fy = (y - 18) / 10
        w = round(10 * (1 - fy * 0.6))
        for x in range(15 - w, 15 + w + 1):
            frac = abs(x - 15) / max(w, 1)
            height_shade = 1.0 + 0.2 * (22 - y) / 6
            if frac < 0.4:
                px(img, x, y, shade(brown_lt, height_shade))
            elif frac < 0.7:
                px(img, x, y, shade(brown, height_shade))
            else:
                px(img, x, y, shade(brown_dk, height_shade))

    # Red tint mixed in
    px(img, 14, 20, red_tint)
    px(img, 16, 22, red_tint)
    px(img, 13, 23, red_tint)

    # Top surface texture
    for x in range(10, 21):
        if x % 2 == 0:
            px(img, x, 18, gray)

    # Outline bottom
    for x in range(7, 24):
        px(img, x, 27, outline)

    # Floating particles
    px(img, 12, 12, particle)
    px(img, 18, 10, particle_lt)
    px(img, 14, 8, particle)
    px(img, 10, 14, particle_lt)
    px(img, 20, 7, particle)
    px(img, 16, 5, particle_lt)
    px(img, 11, 10, (150, 135, 115))
    px(img, 19, 13, (140, 125, 105))


def draw_vigil_crystal(img):
    """Multi-faceted diamond crystal, five shards fused, sky blue glow, sparkle dots."""
    gray = (190, 195, 205)
    gray_dk = (150, 155, 165)
    gray_lt = (225, 230, 240)
    blue = (135, 206, 235)
    blue_lt = (180, 225, 245)
    blue_dk = (100, 170, 200)
    white = (250, 252, 255)
    outline = (80, 85, 100)

    # Central large crystal
    pts_main = [(15, 3), (19, 10), (18, 22), (15, 27), (12, 22), (11, 10)]
    for y in range(3, 28):
        left, right = 32, 0
        for i in range(len(pts_main)):
            x0, y0 = pts_main[i]
            x1, y1 = pts_main[(i + 1) % len(pts_main)]
            if y0 == y1: continue
            if min(y0, y1) <= y <= max(y0, y1):
                t = (y - y0) / (y1 - y0)
                x = x0 + t * (x1 - x0)
                left = min(left, x)
                right = max(right, x)
        for x in range(int(left), int(right) + 1):
            frac = (x - left) / max(right - left, 1)
            if frac < 0.35:
                px(img, x, y, gray_lt)
            elif frac < 0.65:
                px(img, x, y, blue_lt if y < 15 else blue)
            else:
                px(img, x, y, gray_dk)
    for i in range(len(pts_main)):
        draw_line(img, *pts_main[i], *pts_main[(i + 1) % len(pts_main)], outline)

    # Left shard
    pts_l = [(8, 8), (11, 14), (9, 22), (6, 16)]
    for y in range(8, 23):
        left, right = 32, 0
        for i in range(len(pts_l)):
            x0, y0 = pts_l[i]
            x1, y1 = pts_l[(i + 1) % len(pts_l)]
            if y0 == y1: continue
            if min(y0, y1) <= y <= max(y0, y1):
                t = (y - y0) / (y1 - y0)
                x = x0 + t * (x1 - x0)
                left = min(left, x)
                right = max(right, x)
        for x in range(int(left), int(right) + 1):
            px(img, x, y, blue_dk)
    for i in range(len(pts_l)):
        draw_line(img, *pts_l[i], *pts_l[(i + 1) % len(pts_l)], outline)

    # Right shard
    pts_r = [(22, 8), (25, 16), (23, 22), (19, 14)]
    for y in range(8, 23):
        left, right = 32, 0
        for i in range(len(pts_r)):
            x0, y0 = pts_r[i]
            x1, y1 = pts_r[(i + 1) % len(pts_r)]
            if y0 == y1: continue
            if min(y0, y1) <= y <= max(y0, y1):
                t = (y - y0) / (y1 - y0)
                x = x0 + t * (x1 - x0)
                left = min(left, x)
                right = max(right, x)
        for x in range(int(left), int(right) + 1):
            px(img, x, y, gray)
    for i in range(len(pts_r)):
        draw_line(img, *pts_r[i], *pts_r[(i + 1) % len(pts_r)], outline)

    # Center glow
    fill_ellipse(img, 15, 14, 2, 3, white)

    # Sparkle dots
    px(img, 15, 2, white)
    px(img, 7, 7, white)
    px(img, 24, 7, white)
    px(img, 4, 15, blue_lt)
    px(img, 27, 15, blue_lt)


def draw_veilborn_torch(img):
    """Vertical torch, brown handle, ash cloth wrap, wide orange-gold soul flame, glow aura."""
    brown = (110, 65, 20)
    brown_dk = (75, 42, 10)
    brown_lt = (140, 90, 40)
    cloth = (100, 95, 85)
    cloth_dk = (70, 65, 58)
    flame_core = (255, 240, 150)
    flame_mid = (255, 180, 40)
    flame_out = (255, 120, 0)
    flame_edge = (200, 80, 0)
    glow = (255, 160, 40, 80)
    outline = (45, 25, 5)

    # Handle
    fill_rect(img, 14, 17, 17, 30, brown)
    fill_rect(img, 15, 17, 16, 30, brown_lt)
    px(img, 14, 17, brown_dk)
    px(img, 17, 17, brown_dk)
    outline_rect(img, 13, 17, 18, 30, outline)

    # Cloth wrap
    for y in range(22, 27):
        fill_rect(img, 13, y, 18, y, cloth if y % 2 == 0 else cloth_dk)
    px(img, 13, 24, cloth_dk)
    px(img, 18, 23, cloth_dk)

    # Flame — wide and bright
    # Outer flame
    for y in range(4, 18):
        fy = (y - 4) / 14
        w = round(6 * (1 - fy * 0.7) * (0.5 + 0.5 * math.sin(fy * 3.14)))
        w = max(1, w)
        for x in range(15 - w, 16 + w + 1):
            dist = abs(x - 15.5) / max(w, 1)
            if dist < 0.25:
                px(img, x, y, flame_core)
            elif dist < 0.5:
                px(img, x, y, flame_mid)
            elif dist < 0.8:
                px(img, x, y, flame_out)
            else:
                px(img, x, y, flame_edge)

    # Flame tip
    px(img, 15, 3, flame_mid)
    px(img, 16, 3, flame_out)
    px(img, 15, 2, flame_out)

    # Glow aura
    for dy in range(-4, 5):
        for dx in range(-5, 6):
            d = math.sqrt(dx * dx + dy * dy)
            if 4 < d < 7:
                gx, gy = 15 + dx, 10 + dy
                existing = img.getpixel((gx, gy)) if 0 <= gx < 32 and 0 <= gy < 32 else T
                if existing == T or (len(existing) == 4 and existing[3] == 0):
                    alpha = int(60 * (1 - (d - 4) / 3))
                    px(img, gx, gy, (255, 160, 40, alpha))


def draw_hollow_ward_charm(img):
    """Hexagonal pendant, gold border, purple inlay with ward rune, chain at top."""
    gold = (200, 170, 50)
    gold_dk = (150, 125, 30)
    gold_hi = (240, 210, 90)
    purple = (120, 60, 180)
    purple_dk = (80, 30, 130)
    purple_lt = (160, 100, 210)
    outline = (50, 35, 10)
    chain = (160, 160, 170)
    rune = (200, 170, 230)

    # Chain
    for y in range(2, 8):
        px(img, 15, y, chain)
        px(img, 16, y, chain)
    px(img, 14, 3, chain)
    px(img, 17, 3, chain)
    px(img, 14, 5, chain)
    px(img, 17, 5, chain)

    # Hexagon
    hex_pts = [(15, 8), (23, 13), (23, 22), (15, 27), (8, 22), (8, 13)]
    # Fill
    for y in range(8, 28):
        left, right = 32, 0
        for i in range(len(hex_pts)):
            x0, y0 = hex_pts[i]
            x1, y1 = hex_pts[(i + 1) % len(hex_pts)]
            if y0 == y1: continue
            if min(y0, y1) <= y <= max(y0, y1):
                t = (y - y0) / (y1 - y0)
                x = x0 + t * (x1 - x0)
                left = min(left, x)
                right = max(right, x)
        if left < right:
            for x in range(int(left), int(right) + 1):
                # Gold border (2px) then purple
                if x <= int(left) + 1 or x >= int(right) - 1:
                    px(img, x, y, gold if y < 18 else gold_dk)
                else:
                    px(img, x, y, purple if y < 18 else purple_dk)
    # Top/bottom gold border
    for x in range(12, 19):
        px(img, x, 8, gold_hi)
        px(img, x, 9, gold)
        px(img, x, 26, gold_dk)
        px(img, x, 27, gold_dk)

    # Outline
    for i in range(len(hex_pts)):
        draw_line(img, *hex_pts[i], *hex_pts[(i + 1) % len(hex_pts)], outline)

    # Purple highlight
    fill_ellipse(img, 13, 15, 2, 2, purple_lt)

    # Ward rune — cross with dots
    draw_line(img, 15, 13, 15, 23, rune)
    draw_line(img, 11, 18, 20, 18, rune)
    px(img, 12, 15, rune)
    px(img, 18, 15, rune)
    px(img, 12, 21, rune)
    px(img, 18, 21, rune)


def draw_stone_eye_emblem(img):
    """Oval stone medallion, carved open eye, orange-red iris, chip marks, eerie glow."""
    stone = (112, 128, 144)
    stone_dk = (75, 88, 100)
    stone_lt = (145, 158, 170)
    stone_hi = (170, 180, 192)
    outline = (40, 48, 55)
    eye_white = (220, 220, 215)
    iris = (230, 80, 0)
    iris_dk = (180, 50, 0)
    pupil = (15, 10, 10)
    glow = (255, 100, 30, 100)

    # Oval stone
    fill_ellipse(img, 15, 16, 11, 9, stone_dk)
    fill_ellipse(img, 15, 16, 10, 8, stone)
    outline_ellipse(img, 15, 16, 11, 9, outline)

    # Highlight
    fill_ellipse(img, 12, 12, 4, 3, stone_lt)
    px(img, 10, 11, stone_hi)

    # Eye shape carved into stone
    # Eye outline
    draw_line(img, 8, 16, 15, 12, outline)
    draw_line(img, 15, 12, 22, 16, outline)
    draw_line(img, 22, 16, 15, 20, outline)
    draw_line(img, 15, 20, 8, 16, outline)
    # Eye white fill
    for y in range(12, 21):
        for x in range(8, 23):
            # Inside eye shape
            above = (y - 16) < -(abs(x - 15) - 7) * 4 / 7
            below = (y - 16) > (abs(x - 15) - 7) * 4 / 7
            if not above and not below and abs(x - 15) < 7:
                px(img, x, y, eye_white)

    # Iris
    fill_ellipse(img, 15, 16, 3, 3, iris)
    fill_ellipse(img, 15, 16, 2, 2, iris_dk)
    # Pupil
    fill_ellipse(img, 15, 16, 1, 1, pupil)
    # Iris highlight
    px(img, 14, 15, (255, 130, 50))

    # Chip marks
    px(img, 6, 12, stone_dk)
    px(img, 5, 13, T)
    px(img, 24, 20, stone_dk)
    px(img, 25, 19, T)

    # Eerie glow around iris
    for dx in range(-2, 3):
        for dy in range(-2, 3):
            d = abs(dx) + abs(dy)
            if d == 2:
                gx, gy = 15 + dx * 2, 16 + dy * 2
                ep = img.getpixel((gx, gy)) if 0 <= gx < 32 and 0 <= gy < 32 else T
                if ep[:3] == eye_white[:3]:
                    px(img, gx, gy, (255, 150, 100))


def draw_vigils_tear(img):
    """Large perfect teardrop, luminous water drop, white core, ripple at bottom."""
    dk_cyan = (0, 120, 135)
    cyan = (0, 190, 210)
    lt_cyan = (100, 225, 240)
    pale = (190, 250, 255)
    white = (245, 255, 255)
    core = (255, 255, 255)
    outline = (0, 70, 85)
    ripple = (130, 230, 240, 150)

    # Teardrop body — perfect drop shape
    for y in range(3, 29):
        t = (y - 3) / 26
        if t < 0.25:
            w = round(1 + 9 * (t / 0.25))
        elif t < 0.7:
            w = round(10 - 2 * ((t - 0.25) / 0.45))
        else:
            w = round(8 * (1 - (t - 0.7) / 0.3))
        w = max(0, w)
        for x in range(15 - w, 16 + w):
            dist = abs(x - 15.5) / max(w, 1)
            vert = (15 - y) / 13
            if dist < 0.2 and 0.15 < t < 0.5:
                px(img, x, y, core)
            elif dist < 0.35:
                px(img, x, y, white if vert > 0 else pale)
            elif dist < 0.6:
                px(img, x, y, lt_cyan)
            elif dist < 0.85:
                px(img, x, y, cyan)
            else:
                px(img, x, y, dk_cyan)

    # Outline
    for y in range(3, 29):
        t = (y - 3) / 26
        if t < 0.25:
            w = round(1 + 9 * (t / 0.25))
        elif t < 0.7:
            w = round(10 - 2 * ((t - 0.25) / 0.45))
        else:
            w = round(8 * (1 - (t - 0.7) / 0.3))
        w = max(0, w)
        if w > 0:
            px(img, 15 - w - 1, y, outline)
            px(img, 16 + w, y, outline)

    # Core highlight — bright center
    fill_ellipse(img, 15, 13, 2, 3, core)
    px(img, 14, 11, core)

    # Ripple at bottom
    for dx in range(-4, 5):
        d = abs(dx)
        if d < 4:
            px(img, 15 + dx, 29, ripple)
        if d < 3:
            px(img, 15 + dx, 30, (100, 210, 225, 100))


# ============================================================================

TEXTURE_FUNCTIONS = {
    "vareths_insignia": draw_vareths_insignia,
    "ashen_stone": draw_ashen_stone,
    "wardens_key": draw_wardens_key,
    "soul_of_the_vigil": draw_soul_of_the_vigil,
    "vareths_sigil": draw_vareths_sigil,
    "rune_of_ruin": draw_rune_of_ruin,
    "veilborn_compass": draw_veilborn_compass,
    "evelins_record_book": draw_evelins_record_book,
    "hollows_ward": draw_hollows_ward,
    "vigil_shard": draw_vigil_shard,
    "forgotten_coin": draw_forgotten_coin,
    "veilborn_rations": draw_veilborn_rations,
    "cracked_veilborn_sigil": draw_cracked_veilborn_sigil,
    "miras_last_rose": draw_miras_last_rose,
    "ashen_dust": draw_ashen_dust,
    "vigil_crystal": draw_vigil_crystal,
    "veilborn_torch": draw_veilborn_torch,
    "hollow_ward_charm": draw_hollow_ward_charm,
    "stone_eye_emblem": draw_stone_eye_emblem,
    "vigils_tear": draw_vigils_tear,
}


def main():
    print(f"Generating {len(TEXTURE_FUNCTIONS)} pixel art textures to: {OUTPUT_DIR}")
    print()
    for name, draw_fn in TEXTURE_FUNCTIONS.items():
        img = Image.new("RGBA", (32, 32), T)
        draw_fn(img)
        path = os.path.join(OUTPUT_DIR, f"{name}.png")
        img.save(path, "PNG")
        print(f"  {name}.png")
    print()
    print(f"Done! {len(TEXTURE_FUNCTIONS)} textures generated.")


if __name__ == "__main__":
    main()
