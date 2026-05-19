#!/usr/bin/env python3
"""
War Project - Texture Generator v3.0
Hand-crafted 16x16 pixel textures in vanilla Minecraft style.
Each texture uses a curated palette (highlight/mid/shadow/deep) and
deliberate pixel patterns instead of pure random noise.

Run with: uv run --with pillow tools/generate_textures.py
Output:  src/main/resources/assets/warproject/textures/block/
         src/main/resources/assets/warproject/textures/item/

Adding a new texture: create a make_xxx() function returning a PIL.Image
of size 16x16 (RGBA) and register it in TEXTURES / ITEM_TEXTURES.
"""

# /// script
# requires-python = ">=3.10"
# dependencies = ["pillow"]
# ///

import math
import os
import random
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                         "warproject", "textures", "block")
ITEM_DIR = os.path.join(ROOT, "src", "main", "resources", "assets",
                        "warproject", "textures", "item")

SIZE = 16


# ============================================================
# CORE HELPERS
# ============================================================
def new_img(bg=(0, 0, 0, 0)):
    return Image.new("RGBA", (SIZE, SIZE), bg)

def put(img, x, y, color):
    """Place a single pixel (no-op outside bounds)."""
    if 0 <= x < SIZE and 0 <= y < SIZE:
        img.putpixel((x, y), color)


def fill_rect(img, x0, y0, x1, y1, color):
    """Inclusive rectangle fill."""
    for y in range(max(0, y0), min(SIZE, y1 + 1)):
        for x in range(max(0, x0), min(SIZE, x1 + 1)):
            img.putpixel((x, y), color)


def stroke_rect(img, x0, y0, x1, y1, color):
    """Inclusive rectangle outline."""
    for x in range(x0, x1 + 1):
        put(img, x, y0, color)
        put(img, x, y1, color)
    for y in range(y0, y1 + 1):
        put(img, x0, y, color)
        put(img, x1, y, color)


def shade(color, amount):
    """Lighten (positive) or darken (negative) an RGB(A) color by amount."""
    r, g, b = color[:3]
    a = color[3] if len(color) > 3 else 255
    return (
        max(0, min(255, r + amount)),
        max(0, min(255, g + amount)),
        max(0, min(255, b + amount)),
        a,
    )


def jitter(img, rng, max_delta=6, alpha_only_solid=True):
    """Apply per-pixel color noise. Skips transparent pixels."""
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            if alpha_only_solid and a < 16:
                continue
            d = rng.randint(-max_delta, max_delta)
            px[x, y] = (
                max(0, min(255, r + d)),
                max(0, min(255, g + d)),
                max(0, min(255, b + d)),
                a,
            )


def sprinkle(img, rng, color, count, mask_only_opaque=True):
    """Randomly place 'count' pixels of color across opaque region."""
    px = img.load()
    placed = 0
    tries = 0
    while placed < count and tries < count * 20:
        tries += 1
        x = rng.randint(0, SIZE - 1)
        y = rng.randint(0, SIZE - 1)
        if mask_only_opaque and px[x, y][3] < 16:
            continue
        px[x, y] = color
        placed += 1


# ============================================================
# OKOP (Fortification)
# ============================================================
def wood_planks(palette, seed, vertical_planks=False):
    """Wooden plank texture with 4 boards, grain, knots, gaps."""
    rng = random.Random(seed)
    hi, mid, lo, deep, knot = palette
    img = new_img(mid)
    # 4 boards separated by dark gaps
    for i in range(4):
        if vertical_planks:
            x0, x1 = i * 4, i * 4 + 3
            fill_rect(img, x0, 0, x1, 15, mid)
            # gap on right
            if i < 3:
                fill_rect(img, x1 + 1, 0, x1 + 1, 15, deep)
            # grain lines
            for y in range(0, 16, 2):
                shade_var = rng.choice([lo, mid, mid, hi])
                fill_rect(img, x0, y, x1, y, shade_var)
        else:
            y0, y1 = i * 4, i * 4 + 3
            fill_rect(img, 0, y0, 15, y1, mid)
            if i < 3:
                fill_rect(img, 0, y1 + 1, 15, y1 + 1, deep)
            # horizontal grain stripes
            for x in range(0, 16):
                if rng.random() < 0.35:
                    put(img, x, y0 + rng.randint(0, 3), lo)
                if rng.random() < 0.2:
                    put(img, x, y0 + rng.randint(0, 3), hi)
    # knots: a few dark spots
    for _ in range(2):
        kx = rng.randint(2, 13)
        ky = rng.randint(1, 14)
        put(img, kx, ky, knot)
        put(img, kx + 1, ky, deep)
        put(img, kx, ky + 1, deep)
    jitter(img, rng, 4)
    return img


def make_wooden_support_beam():
    # rich brown wood, vertical orientation
    palette = ((169, 117, 60, 255), (143, 95, 47, 255), (110, 71, 33, 255),
               (78, 49, 21, 255), (62, 38, 16, 255))
    return wood_planks(palette, seed=101, vertical_planks=True)


def make_wooden_horizontal_cover():
    palette = ((169, 117, 60, 255), (143, 95, 47, 255), (110, 71, 33, 255),
               (78, 49, 21, 255), (62, 38, 16, 255))
    return wood_planks(palette, seed=108, vertical_planks=False)


def make_log_horizontal_cover():
    """Round logs side by side."""
    rng = random.Random(109)
    hi = (133, 88, 44, 255)
    mid = (107, 68, 33, 255)
    lo = (78, 48, 21, 255)
    deep = (52, 31, 12, 255)
    img = new_img(deep)
    # 4 logs of 4 px each
    for i in range(4):
        cx = i * 4 + 1  # center column inside log
        # column shading: hi on top, mid in middle, lo at bottom
        for y in range(SIZE):
            for dx, col in enumerate([lo, mid, hi, mid]):
                # gradient on the log surface
                if y < 3:
                    c = shade(col, 10)
                elif y > 12:
                    c = shade(col, -15)
                else:
                    c = col
                put(img, i * 4 + dx, y, c)
        # knot
        ky = rng.randint(3, 12)
        put(img, cx, ky, lo)
        put(img, cx + 1, ky, deep)
    # gap lines between logs (already dark in log palette)
    for i in range(1, 4):
        for y in range(SIZE):
            put(img, i * 4 - 1, y, deep)
            if rng.random() < 0.2:
                put(img, i * 4 - 1, y, shade(deep, 8))
    jitter(img, rng, 4)
    return img


def make_trench_stairs():
    """Clean wooden planks (no fake step line) — the 3D model defines steps."""
    palette = ((180, 130, 70, 255), (150, 100, 50, 255), (115, 75, 35, 255),
               (78, 49, 21, 255), (55, 32, 12, 255))
    img = wood_planks(palette, seed=113, vertical_planks=False)
    rng = random.Random(113)
    # nail heads at the ends of the planks (4 boards x 2 ends)
    nail = (60, 60, 70, 255)
    nail_hi = (130, 132, 140, 255)
    for i in range(4):
        cy = i * 4 + 1
        for cx in (1, 14):
            put(img, cx, cy, nail)
            put(img, cx, cy + 1, nail_hi)
    return img


def make_wooden_support_beam_end():
    """End grain of a wooden post: concentric rings and a central knot."""
    rng = random.Random(151)
    hi = (175, 122, 64, 255)
    mid = (143, 95, 47, 255)
    lo = (105, 68, 32, 255)
    deep = (72, 44, 18, 255)
    knot = (52, 30, 10, 255)
    img = new_img(mid)
    cx, cy = 7.5, 7.5
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
            if d < 1.5:
                c = knot
            elif d < 3.0:
                c = deep
            elif d < 4.5:
                c = lo
            elif d < 6.0:
                c = mid
            elif d < 7.5:
                c = hi
            else:
                c = lo
            img.putpixel((x, y), c)
    # bevel on top/left edges, shadow on bottom/right
    for x in range(SIZE):
        put(img, x, 0, hi)
        put(img, x, 15, deep)
    for y in range(SIZE):
        put(img, 0, y, shade(hi, -10))
        put(img, 15, y, deep)
    # subtle radial cracks
    for ang_deg in (35, 110, 215, 310):
        rad = math.radians(ang_deg)
        for r in range(2, 7):
            x = int(round(cx + r * math.cos(rad)))
            y = int(round(cy + r * math.sin(rad)))
            put(img, x, y, deep)
    jitter(img, rng, 4)
    return img


def make_drainage_grate_frame():
    """Solid heavy metal frame: thick bevel, rivets, no holes."""
    rng = random.Random(152)
    base = (118, 121, 128, 255)
    hi = shade(base, 35)
    lo = shade(base, -25)
    deep = shade(base, -55)
    img = new_img(base)
    # uniform plate noise
    for y in range(SIZE):
        for x in range(SIZE):
            d = rng.randint(-8, 8)
            img.putpixel((x, y), shade(base, d))
    # 2-px bevel: top/left highlight, bottom/right shadow
    for x in range(SIZE):
        put(img, x, 0, hi)
        put(img, x, 1, shade(hi, -10))
        put(img, x, 14, lo)
        put(img, x, 15, deep)
    for y in range(SIZE):
        put(img, 0, y, hi)
        put(img, 1, y, shade(hi, -10))
        put(img, 14, y, lo)
        put(img, 15, y, deep)
    # rivets in a 3x3 grid pattern
    for cx in (3, 12):
        for cy in (3, 12):
            put(img, cx, cy, deep)
            put(img, cx + 1, cy, lo)
            put(img, cx, cy + 1, lo)
            put(img, cx + 1, cy + 1, base)
    # subtle scratches
    for _ in range(rng.randint(2, 4)):
        y = rng.randint(4, 11)
        x0 = rng.randint(3, 9)
        length = rng.randint(2, 4)
        for x in range(x0, min(13, x0 + length)):
            put(img, x, y, hi)
    return img


def metal_plate(base, seed, rivets=True, scratches=True):
    """Industrial metal plate with shading and rivets at corners."""
    rng = random.Random(seed)
    hi = shade(base, 35)
    mid = base
    lo = shade(base, -25)
    deep = shade(base, -55)
    img = new_img(mid)
    # vertical light strip (subtle)
    for y in range(SIZE):
        put(img, 3, y, hi)
        put(img, 12, y, lo)
    # bevel: 1px highlight on top, dark on bottom
    for x in range(SIZE):
        put(img, x, 0, hi)
        put(img, x, 15, deep)
    for y in range(SIZE):
        put(img, 0, y, shade(hi, -5))
        put(img, 15, y, lo)
    # scratches
    if scratches:
        for _ in range(rng.randint(3, 6)):
            y = rng.randint(2, 13)
            x0 = rng.randint(1, 9)
            length = rng.randint(2, 5)
            for x in range(x0, min(15, x0 + length)):
                put(img, x, y, hi)
    # rivets at 4 corners
    if rivets:
        for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
            put(img, cx, cy, deep)
            put(img, cx + 1, cy, lo)
            put(img, cx, cy + 1, lo)
            put(img, cx + 1, cy + 1, mid)
    jitter(img, rng, 3)
    return img


def make_iron_support_beam():
    return metal_plate((175, 178, 185, 255), seed=102)


def make_reinforced_support_beam():
    """Darker plate + diagonal stripes for reinforcement."""
    img = metal_plate((125, 130, 138, 255), seed=103)
    rng = random.Random(103)
    accent = (60, 65, 75, 255)
    # diagonal hazard band (kept subtle, dark)
    for k in range(SIZE):
        x = (k + 4) % SIZE
        put(img, x, k, accent)
    jitter(img, rng, 3)
    return img


def make_metal_gate():
    return metal_plate((140, 143, 150, 255), seed=143)


def make_tank_hedgehog():
    """Steel I-beam side texture: light metal with rivets along edges.
    The cross/hedgehog shape is built in the block model from 3 beams."""
    rng = random.Random(146)
    base = (118, 122, 130, 255)
    img = metal_plate(base, seed=146, rivets=True, scratches=True)
    # extra rivets along the long edges to read as a beam
    for x in (0, 4, 8, 12, 15):
        put(img, x, 1, (45, 48, 55, 255))
        put(img, x, 14, (45, 48, 55, 255))
    jitter(img, rng, 3)
    return img


# === Sandbags ===
def make_sandbag():
    """Stacked burlap bag rows, with binding twine."""
    rng = random.Random(107)
    canvas = (193, 167, 110, 255)
    fold = (167, 140, 87, 255)
    deep = (123, 100, 58, 255)
    twine = (96, 75, 42, 255)
    hi = (220, 197, 145, 255)
    img = new_img(canvas)
    # two rows of bags, offset
    # row 1 (top): bags at x=0..7 and 8..15, y=0..7
    # row 2 (bot): bags offset, y=8..15
    # draw rounded bag shape: dark seam between bags
    for row in range(2):
        y0 = row * 8
        y1 = y0 + 7
        offset = 0 if row == 0 else 4
        # seam between bags
        for x in range(SIZE):
            xx = (x + offset) % 8
            put(img, x, y1, deep)
        # vertical seam (between bags in row)
        for x in [(0 - offset) % SIZE, (8 - offset) % SIZE]:
            for y in range(y0, y1 + 1):
                put(img, x, y, fold)
        # surface shading: highlight along upper edge
        for x in range(SIZE):
            put(img, x, y0, fold)
            put(img, x, y0 + 1, hi)
        # twine binding (vertical lighter line near bag center)
        for row_x_start in range(0, SIZE, 8):
            tx = (row_x_start + 3 - offset) % SIZE
            for y in range(y0 + 2, y1):
                put(img, tx, y, twine)
    # wrinkles
    for _ in range(8):
        x = rng.randint(0, 15)
        y = rng.randint(1, 14)
        put(img, x, y, fold)
    jitter(img, rng, 5)
    return img


# === Camo nets ===
def camo_net(palette, seed):
    """Multi-tone organic blotches with visible mesh grid."""
    rng = random.Random(seed)
    img = new_img((0, 0, 0, 0))
    for y in range(SIZE):
        for x in range(SIZE):
            if (x + y) % 4 == 0 or rng.random() < 0.38:
                col = rng.choice(palette)
                if len(col) == 3:
                    col = col + (rng.randint(150, 195),)
                img.putpixel((x, y), shade(col, rng.randint(-12, 10)))
    base = palette[0] if len(palette[0]) == 4 else palette[0] + (185,)
    for x in range(0, SIZE, 4):
        for y in range(SIZE):
            put(img, x, y, shade(base, -35))
    for y in range(0, SIZE, 4):
        for x in range(SIZE):
            put(img, x, y, shade(base, -35))
    jitter(img, rng, 8)
    return img


def make_forest_camo_net():
    return camo_net([(54, 88, 38), (78, 122, 50), (38, 65, 28), (110, 145, 70), (28, 45, 20)], seed=104)


def make_desert_camo_net():
    return camo_net([(186, 158, 102), (210, 184, 130), (158, 130, 80), (228, 205, 156), (130, 100, 60)], seed=105)


def make_winter_camo_net():
    return camo_net([(228, 232, 240), (245, 247, 250), (200, 208, 222), (175, 185, 205), (250, 250, 252)], seed=106)


# === Other okop ===
def make_barbed_wire():
    """Two crossing twisted wires with periodic 4-spike barbs. Transparent bg."""
    img = new_img((0, 0, 0, 0))
    rng = random.Random(110)
    wire = (170, 175, 185, 255)
    wire_hi = (215, 220, 230, 255)
    wire_lo = (95, 100, 110, 255)
    # main horizontal wires at y=4 and y=11
    for x in range(SIZE):
        put(img, x, 4, wire)
        put(img, x, 11, wire)
        # twist highlight pattern
        if x % 3 == 0:
            put(img, x, 4, wire_hi)
            put(img, x, 11, wire_hi)
        if x % 3 == 1:
            put(img, x, 4, wire_lo)
            put(img, x, 11, wire_lo)
    # barbs every 4 px (4-point star)
    for cx in range(2, SIZE, 4):
        for cy in (4, 11):
            put(img, cx, cy - 2, wire)
            put(img, cx, cy + 2, wire)
            put(img, cx - 1, cy - 1, wire_hi)
            put(img, cx + 1, cy - 1, wire_hi)
            put(img, cx - 1, cy + 1, wire_hi)
            put(img, cx + 1, cy + 1, wire_hi)
    return img


def make_razor_wire_fence():
    """Razor wire: tight horizontal coil with razor flats."""
    img = new_img((0, 0, 0, 0))
    rng = random.Random(147)
    wire = (180, 185, 195, 255)
    wire_hi = (225, 230, 240, 255)
    wire_lo = (105, 110, 120, 255)
    # main wire band y=6..9
    for x in range(SIZE):
        put(img, x, 7, wire)
        put(img, x, 8, wire)
        if x % 2 == 0:
            put(img, x, 7, wire_hi)
        else:
            put(img, x, 8, wire_lo)
    # razor flats: diamond-shaped razors every 4px
    for cx in range(1, SIZE, 4):
        # diamond
        put(img, cx, 5, wire_hi)
        put(img, cx + 1, 5, wire_hi)
        put(img, cx - 1, 6, wire_hi)
        put(img, cx + 2, 6, wire_hi)
        put(img, cx - 1, 9, wire_hi)
        put(img, cx + 2, 9, wire_hi)
        put(img, cx, 10, wire_hi)
        put(img, cx + 1, 10, wire_hi)
    return img


def make_drainage_grate():
    """Solid metal grate plate — no see-through holes, just patterned metal."""
    rng = random.Random(111)
    plate = (132, 135, 142, 255)
    plate_hi = (168, 172, 180, 255)
    plate_lo = (95, 98, 105, 255)
    bar = (165, 168, 175, 255)
    bar_hi = (200, 203, 210, 255)
    cell = (118, 121, 128, 255)
    img = new_img(plate)
    # raised cells (4x4 with embossed border) — NOT holes
    for cy0 in (1, 6, 11):
        for cx0 in (1, 6, 11):
            cx1 = cx0 + 3
            cy1 = cy0 + 3
            for y in range(cy0, cy1 + 1):
                for x in range(cx0, cx1 + 1):
                    img.putpixel((x, y), cell)
            for x in range(cx0, cx1 + 1):
                img.putpixel((x, cy0), plate_hi)
                img.putpixel((x, cy1), plate_lo)
            for y in range(cy0, cy1 + 1):
                img.putpixel((cx0, y), plate_hi)
                img.putpixel((cx1, y), plate_lo)
    # horizontal & vertical bars between cells
    for k in range(SIZE):
        for line in (0, 5, 10, 15):
            img.putpixel((k, line), bar)
            img.putpixel((line, k), bar)
    # bar highlights
    for k in range(0, SIZE, 2):
        img.putpixel((k, 0), bar_hi)
        img.putpixel((0, k), bar_hi)
    # corner bolts
    for cx, cy in [(0, 0), (15, 0), (0, 15), (15, 15)]:
        img.putpixel((cx, cy), plate_lo)
    jitter(img, rng, 3)
    return img


def make_firing_slot():
    """Concrete block with horizontal dark slit."""
    rng = random.Random(112)
    img = concrete_base((150, 150, 152, 255), seed=112)
    # the firing slit
    fill_rect(img, 2, 7, 13, 9, (20, 22, 28, 255))
    # slit lighting (top edge lighter)
    for x in range(2, 14):
        put(img, x, 7, (40, 42, 48, 255))
        put(img, x, 9, (10, 12, 16, 255))
    # frame around slit
    stroke_rect(img, 1, 6, 14, 10, (95, 95, 100, 255))
    return img


def make_trench_lantern():
    """Plain dark iron texture for the lantern frame parts."""
    rng = random.Random(114)
    iron = (38, 36, 32, 255)
    iron_hi = (78, 74, 68, 255)
    iron_lo = (18, 16, 14, 255)
    img = new_img(iron)
    # vertical grain
    for y in range(SIZE):
        for x in range(SIZE):
            d = rng.randint(-6, 6)
            img.putpixel((x, y), shade(iron, d))
    # bevel highlights on top/left
    for x in range(SIZE):
        put(img, x, 0, iron_hi)
    for y in range(SIZE):
        put(img, 0, y, iron_hi)
    for x in range(SIZE):
        put(img, x, 15, iron_lo)
    for y in range(SIZE):
        put(img, 15, y, iron_lo)
    # rivets
    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13), (7, 7), (8, 8)]:
        put(img, cx, cy, iron_lo)
    return img


def make_trench_lantern_glass():
    """Glowing yellow glass core for the lantern."""
    rng = random.Random(115)
    glow_core = (255, 245, 195, 255)
    glow_mid = (255, 215, 110, 255)
    glow_rim = (210, 155, 50, 255)
    glow_dark = (155, 100, 30, 255)
    img = new_img(glow_mid)
    # radial gradient: bright center, dimmer at edges
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.5:
                img.putpixel((x, y), glow_core)
            elif d < 5.0:
                img.putpixel((x, y), glow_mid)
            elif d < 7.0:
                img.putpixel((x, y), glow_rim)
            else:
                img.putpixel((x, y), glow_dark)
    # subtle flame flicker dots
    sprinkle(img, rng, glow_core, 4)
    sprinkle(img, rng, glow_dark, 3)
    return img


def make_trench_lantern_glow():
    img = new_img((255, 195, 70, 210))
    fill_rect(img, 4, 2, 11, 13, (255, 225, 120, 235))
    fill_rect(img, 6, 4, 9, 11, (255, 245, 180, 255))
    return img


def make_supply_crate():
    """Wooden crate side: 2 horizontal planks + corner reinforcements."""
    rng = random.Random(115)
    palette = ((160, 110, 60, 255), (130, 88, 42, 255), (95, 62, 28, 255),
               (62, 38, 16, 255), (45, 26, 10, 255))
    img = wood_planks(palette, seed=115, vertical_planks=False)
    # iron corner brackets
    iron = (75, 80, 88, 255)
    iron_hi = (140, 145, 155, 255)
    for cx, cy in [(0, 0), (12, 0), (0, 12), (12, 12)]:
        stroke_rect(img, cx, cy, cx + 3, cy + 3, iron)
        put(img, cx + 1, cy + 1, iron_hi)
    # central X-brace
    for k in range(4, 12):
        put(img, k, k, iron)
        put(img, k, 15 - k, iron)
    return img


def make_supply_crate_top():
    rng = random.Random(116)
    palette = ((160, 110, 60, 255), (130, 88, 42, 255), (95, 62, 28, 255),
               (62, 38, 16, 255), (45, 26, 10, 255))
    img = wood_planks(palette, seed=116, vertical_planks=True)
    # rope handle
    rope = (190, 165, 110, 255)
    rope_dark = (130, 100, 60, 255)
    for x in range(4, 12):
        put(img, x, 7, rope_dark)
        put(img, x, 8, rope)
    put(img, 4, 6, rope)
    put(img, 11, 6, rope)
    put(img, 5, 7, rope)
    put(img, 10, 7, rope)
    return img


# ============================================================
# POLEVOY (Field Camp)
# ============================================================
def tent_canvas(color, seed, has_pole=True, has_door=False):
    """Triangular tent silhouette baked into a 16x16 square texture.
    Used as the side texture of a 3D tent block model."""
    rng = random.Random(seed)
    hi = shade(color, 25)
    mid = color
    lo = shade(color, -25)
    deep = shade(color, -45)
    img = new_img(mid)
    # vertical fold lines
    for x in (3, 11):
        for y in range(SIZE):
            put(img, x, y, lo)
    # horizontal seam
    for x in range(SIZE):
        put(img, x, 8, lo)
    # canvas wrinkle highlights
    for _ in range(rng.randint(6, 9)):
        x = rng.randint(0, 15)
        y = rng.randint(1, 14)
        put(img, x, y, hi)
    # central pole / rope hint
    if has_pole:
        for y in range(SIZE):
            put(img, 7, y, deep)
            put(img, 8, y, lo)
    # door slit
    if has_door:
        fill_rect(img, 6, 9, 9, 14, deep)
        put(img, 6, 9, lo)
        put(img, 9, 9, lo)
    jitter(img, rng, 4)
    return img


def make_small_tent():
    return tent_canvas((92, 110, 70, 255), seed=120, has_pole=True)


def make_command_tent():
    return tent_canvas((72, 92, 60, 255), seed=122, has_pole=True, has_door=True)


def make_medical_tent():
    return tent_canvas((215, 218, 222, 255), seed=123, has_pole=True)


def make_tent_floor():
    """Trampled dirt floor with planks and debris."""
    rng = random.Random(121)
    dirt = (98, 78, 55, 255)
    dirt_hi = (130, 105, 75, 255)
    dirt_lo = (62, 48, 32, 255)
    plank = (120, 85, 50, 255)
    img = new_img(dirt)
    # base noise
    for y in range(SIZE):
        for x in range(SIZE):
            r = rng.randint(-15, 12)
            img.putpixel((x, y), shade(dirt, r))
    # a few planks horizontally
    for plank_y in (3, 8, 13):
        for x in range(SIZE):
            put(img, x, plank_y, plank)
            put(img, x, plank_y + 1, shade(plank, -15))
    # darker pebbles
    sprinkle(img, rng, dirt_lo, 8)
    sprinkle(img, rng, dirt_hi, 5)
    return img


def make_tent_frame():
    rng = random.Random(132)
    wood = (92, 62, 32, 255)
    img = new_img(wood)
    for y in range(SIZE):
        for x in range(SIZE):
            img.putpixel((x, y), shade(wood, rng.randint(-15, 14)))
    for x in (3, 8, 13):
        for y in range(SIZE):
            put(img, x, y, shade(wood, -35))
    stroke_rect(img, 0, 0, 15, 15, (52, 34, 17, 255))
    return img


def make_tent_door():
    img = tent_canvas((74, 64, 44, 255), seed=133, has_pole=True, has_door=True)
    for y in range(4, 15):
        put(img, 7, y, (30, 24, 18, 255))
        put(img, 8, y, (30, 24, 18, 255))
    return img


def make_command_panel():
    img = new_img((42, 56, 38, 255))
    stroke_rect(img, 0, 0, 15, 15, (18, 25, 18, 255))
    fill_rect(img, 2, 2, 13, 13, (30, 42, 28, 255))
    for x in range(3, 13):
        put(img, x, 4, (120, 170, 90, 255))
        put(img, x, 9, (180, 180, 120, 255))
    fill_rect(img, 4, 6, 5, 7, (200, 50, 45, 255))
    fill_rect(img, 8, 6, 11, 7, (70, 130, 190, 255))
    jitter(img, random.Random(134), 3)
    return img


def make_medical_cross():
    """White panel with bold red cross — for medical tent door."""
    img = new_img((242, 244, 248, 255))
    red = (190, 35, 35, 255)
    red_hi = (220, 60, 60, 255)
    red_lo = (140, 20, 20, 255)
    # cross
    fill_rect(img, 6, 2, 9, 13, red)
    fill_rect(img, 2, 6, 13, 9, red)
    # subtle shading
    fill_rect(img, 6, 2, 9, 2, red_hi)
    fill_rect(img, 2, 6, 2, 9, red_hi)
    fill_rect(img, 6, 13, 9, 13, red_lo)
    fill_rect(img, 13, 6, 13, 9, red_lo)
    # frame
    stroke_rect(img, 0, 0, 15, 15, (175, 180, 190, 255))
    jitter(img, random.Random(124), 3)
    return img


def make_first_aid_kit():
    """Green/white first-aid box with red cross. Used as block side."""
    rng = random.Random(128)
    case = (52, 72, 48, 255)
    case_hi = (85, 105, 75, 255)
    case_lo = (32, 48, 28, 255)
    white = (235, 240, 245, 255)
    red = (200, 40, 40, 255)
    img = new_img(case)
    # bevel
    stroke_rect(img, 0, 0, 15, 15, case_lo)
    for x in range(1, 15):
        put(img, x, 1, case_hi)
    for y in range(1, 15):
        put(img, 1, y, case_hi)
    # latch top
    fill_rect(img, 6, 0, 9, 1, (60, 60, 65, 255))
    # white cross panel
    fill_rect(img, 4, 4, 11, 11, white)
    fill_rect(img, 7, 5, 8, 10, red)
    fill_rect(img, 5, 7, 10, 8, red)
    jitter(img, rng, 3)
    return img


def make_field_radio():
    """Olive-drab field radio with speaker grille, dial, antenna stub."""
    rng = random.Random(129)
    body = (75, 85, 55, 255)
    body_hi = (105, 115, 80, 255)
    body_lo = (50, 58, 35, 255)
    metal = (60, 62, 65, 255)
    img = new_img(body)
    # bevel
    stroke_rect(img, 0, 0, 15, 15, body_lo)
    for x in range(1, 15):
        put(img, x, 1, body_hi)
    # speaker grille (top half)
    for y in range(3, 7):
        for x in range(3, 13):
            if (x + y) % 2 == 0:
                put(img, x, y, metal)
    # dial
    fill_rect(img, 5, 9, 10, 13, (200, 195, 175, 255))
    fill_rect(img, 6, 10, 9, 12, (95, 90, 70, 255))
    # tuning needle
    put(img, 7, 11, (210, 60, 50, 255))
    put(img, 8, 11, (210, 60, 50, 255))
    # knobs
    fill_rect(img, 2, 11, 3, 12, metal)
    fill_rect(img, 12, 11, 13, 12, metal)
    # antenna stub on top
    put(img, 13, 0, metal)
    put(img, 13, 1, metal)
    put(img, 14, 0, metal)
    jitter(img, rng, 3)
    return img


def make_field_spotlight():
    """Dark housing with bright lens (front view)."""
    rng = random.Random(130)
    body = (60, 62, 70, 255)
    body_hi = (110, 113, 122, 255)
    body_lo = (35, 36, 42, 255)
    lens_core = (255, 252, 220, 255)
    lens_mid = (245, 215, 130, 255)
    lens_rim = (170, 130, 60, 255)
    img = new_img(body)
    # round lens
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.0:
                img.putpixel((x, y), lens_core)
            elif d < 4.5:
                img.putpixel((x, y), lens_mid)
            elif d < 6.0:
                img.putpixel((x, y), lens_rim)
    # ring
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 5.8 <= d < 7.0:
                img.putpixel((x, y), body_lo)
    # housing bevel
    stroke_rect(img, 0, 0, 15, 15, body_lo)
    for x in range(1, 15):
        put(img, x, 1, body_hi)
    # bolts at corners
    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        put(img, cx, cy, body_lo)
    jitter(img, rng, 3)
    return img


def make_field_spotlight_lens():
    img = new_img((38, 42, 48, 255))
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 2.5:
                c = (255, 245, 185, 255)
            elif d < 5.2:
                c = (238, 190, 85, 255)
            elif d < 7.0:
                c = (92, 96, 105, 255)
            else:
                c = (36, 39, 44, 255)
            img.putpixel((x, y), c)
    stroke_rect(img, 0, 0, 15, 15, (18, 20, 24, 255))
    jitter(img, random.Random(135), 2)
    return img


def make_generator():
    """Engine block side: dark green with vents."""
    rng = random.Random(131)
    body = (55, 78, 50, 255)
    body_hi = (90, 115, 80, 255)
    body_lo = (35, 50, 30, 255)
    metal = (55, 58, 62, 255)
    img = new_img(body)
    # bevel
    stroke_rect(img, 0, 0, 15, 15, body_lo)
    for x in range(1, 15):
        put(img, x, 1, body_hi)
    # vents (horizontal slats)
    for y in (4, 6, 8, 10):
        fill_rect(img, 3, y, 12, y, metal)
    # fuel cap
    fill_rect(img, 11, 12, 13, 14, (90, 75, 35, 255))
    put(img, 12, 13, (50, 40, 20, 255))
    # corner bolts
    for cx, cy in [(2, 2), (13, 2), (2, 13)]:
        put(img, cx, cy, body_lo)
    jitter(img, rng, 3)
    return img


def make_generator_top():
    """Engine top: starter handle and exhaust port."""
    rng = random.Random(132)
    body = (65, 90, 60, 255)
    body_hi = (100, 125, 90, 255)
    body_lo = (40, 55, 35, 255)
    metal = (55, 58, 62, 255)
    img = new_img(body)
    stroke_rect(img, 0, 0, 15, 15, body_lo)
    for x in range(1, 15):
        put(img, x, 1, body_hi)
    # exhaust ring
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 4.0) ** 2 + (y - 4.0) ** 2) ** 0.5
            if d < 2.5:
                img.putpixel((x, y), (20, 22, 25, 255))
            elif d < 3.5:
                img.putpixel((x, y), metal)
    # starter pull (rectangular handle + cord)
    fill_rect(img, 9, 10, 13, 12, (120, 90, 50, 255))
    fill_rect(img, 10, 11, 12, 11, (60, 45, 25, 255))
    for y in range(5, 11):
        put(img, 11, y, (45, 45, 45, 255))
    jitter(img, rng, 3)
    return img


def make_field_kitchen():
    """Field kitchen side: steel cylinder body with rivets."""
    rng = random.Random(125)
    body = (118, 121, 128, 255)
    body_hi = (170, 175, 185, 255)
    body_lo = (70, 73, 80, 255)
    rivet = (45, 47, 52, 255)
    img = new_img(body)
    # vertical cylinder shading: highlight near center
    for x in range(SIZE):
        for y in range(SIZE):
            dist_x = abs(x - 7.5) / 7.5
            if dist_x < 0.3:
                img.putpixel((x, y), body_hi)
            elif dist_x > 0.8:
                img.putpixel((x, y), body_lo)
            else:
                img.putpixel((x, y), body)
    # band rivets at top and bottom
    for y in (2, 13):
        for x in range(SIZE):
            put(img, x, y, body_lo)
        for x in range(1, SIZE, 3):
            put(img, x, y, rivet)
    # soot streaks at bottom
    sprinkle(img, rng, (35, 35, 35, 255), 6)
    jitter(img, rng, 4)
    return img


def make_field_kitchen_top():
    """Top: pot with central opening."""
    rng = random.Random(126)
    body = (95, 98, 105, 255)
    rim = (140, 144, 152, 255)
    inside = (25, 22, 18, 255)
    soup = (140, 90, 50, 255)
    img = new_img(body)
    # outer ring
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if d < 3.0:
                img.putpixel((x, y), soup)
            elif d < 4.5:
                img.putpixel((x, y), inside)
            elif d < 6.0:
                img.putpixel((x, y), rim)
            elif d < 7.5:
                img.putpixel((x, y), body)
    # rivets
    for cx, cy in [(2, 7), (13, 7), (7, 2), (7, 13)]:
        put(img, cx, cy, (45, 47, 52, 255))
    jitter(img, rng, 3)
    return img


def make_field_kitchen_pipe():
    """Stove pipe section."""
    rng = random.Random(127)
    base = (70, 70, 75, 255)
    return metal_plate(base, seed=127, rivets=False, scratches=True)


# ============================================================
# BAZA (Military Base)
# ============================================================
def concrete_base(base, seed):
    """Generic poured concrete with aggregate spots."""
    rng = random.Random(seed)
    img = new_img(base)
    # base + noise
    for y in range(SIZE):
        for x in range(SIZE):
            d = rng.randint(-12, 12)
            img.putpixel((x, y), shade(base, d))
    # darker aggregate spots
    sprinkle(img, rng, shade(base, -35), 10)
    sprinkle(img, rng, shade(base, 20), 6)
    # a faint crack
    cy = rng.randint(4, 11)
    x = 1
    while x < 14:
        put(img, x, cy + rng.randint(-1, 1), shade(base, -25))
        x += rng.randint(1, 2)
    return img


def make_military_concrete():
    return concrete_base((158, 158, 160, 255), seed=140)


def make_reinforced_concrete():
    """Rebar pattern showing through darker concrete."""
    img = concrete_base((132, 132, 136, 255), seed=141)
    rng = random.Random(141)
    rebar = (90, 78, 55, 255)
    # diagonal rebar lines (subtle)
    for k in range(SIZE):
        if (k % 6) == 0:
            for x in range(SIZE):
                put(img, x, k, rebar)
        if (k % 6) == 3:
            for y in range(SIZE):
                put(img, k, y, rebar)
    jitter(img, rng, 3)
    return img


def make_hesco_barrier():
    """Wire mesh container filled with packed earth."""
    rng = random.Random(142)
    earth_mid = (152, 122, 78, 255)
    earth_hi = (180, 148, 100, 255)
    earth_lo = (110, 85, 55, 255)
    earth_deep = (75, 58, 35, 255)
    mesh = (130, 132, 138, 255)
    mesh_hi = (180, 182, 190, 255)
    img = new_img(earth_mid)
    # base noise (earth)
    for y in range(SIZE):
        for x in range(SIZE):
            d = rng.randint(-15, 15)
            img.putpixel((x, y), shade(earth_mid, d))
    # large stones
    sprinkle(img, rng, earth_deep, 6)
    sprinkle(img, rng, earth_hi, 8)
    # geotextile color
    sprinkle(img, rng, (135, 105, 65, 255), 12)
    # wire mesh grid every 4px
    for y in range(0, SIZE, 4):
        for x in range(SIZE):
            put(img, x, y, mesh)
        for x in range(1, SIZE, 3):
            put(img, x, y, mesh_hi)
    for x in range(0, SIZE, 4):
        for y in range(SIZE):
            put(img, x, y, mesh)
        for y in range(1, SIZE, 3):
            put(img, x, y, mesh_hi)
    return img


def make_hesco_barrier_top():
    """Top of a HESCO bastion: open packed earth/sand fill without wire mesh."""
    rng = random.Random(143)
    earth_mid = (152, 122, 78, 255)
    earth_hi = (180, 148, 100, 255)
    earth_lo = (110, 85, 55, 255)
    earth_deep = (75, 58, 35, 255)
    img = new_img(earth_mid)
    # base noise (earth)
    for y in range(SIZE):
        for x in range(SIZE):
            d = rng.randint(-18, 18)
            img.putpixel((x, y), shade(earth_mid, d))
    # large rocks/clumps
    sprinkle(img, rng, earth_deep, 14)
    sprinkle(img, rng, earth_hi, 18)
    sprinkle(img, rng, earth_lo, 16)
    sprinkle(img, rng, (135, 105, 65, 255), 12)
    # small light grit
    sprinkle(img, rng, (200, 175, 130, 255), 6)
    return img


def make_checkpoint_barrier():
    """Striped red-white post."""
    rng = random.Random(144)
    red = (190, 45, 40, 255)
    red_hi = (220, 75, 65, 255)
    red_lo = (140, 25, 25, 255)
    white = (235, 238, 245, 255)
    white_lo = (180, 185, 195, 255)
    img = new_img(white)
    # 4 stripes (alternating red/white) running horizontally
    for i in range(4):
        y0 = i * 4
        y1 = y0 + 3
        if i % 2 == 0:
            fill_rect(img, 0, y0, 15, y1, red)
            fill_rect(img, 0, y0, 15, y0, red_hi)
            fill_rect(img, 0, y1, 15, y1, red_lo)
        else:
            fill_rect(img, 0, y0, 15, y1, white)
            fill_rect(img, 0, y1, 15, y1, white_lo)
    # bolt
    fill_rect(img, 7, 7, 8, 8, (60, 60, 65, 255))
    jitter(img, rng, 3)
    return img


def make_checkpoint_barrier_bar():
    """Horizontal striped boom."""
    rng = random.Random(145)
    red = (190, 45, 40, 255)
    red_hi = (220, 75, 65, 255)
    red_lo = (140, 25, 25, 255)
    white = (235, 238, 245, 255)
    white_lo = (180, 185, 195, 255)
    img = new_img(white)
    for i in range(4):
        x0 = i * 4
        x1 = x0 + 3
        if i % 2 == 0:
            fill_rect(img, x0, 0, x1, 15, red)
            fill_rect(img, x0, 0, x0, 15, red_hi)
            fill_rect(img, x1, 0, x1, 15, red_lo)
        else:
            fill_rect(img, x0, 0, x1, 15, white)
            fill_rect(img, x1, 0, x1, 15, white_lo)
    jitter(img, rng, 3)
    return img


# ============================================================
# ITEM ICONS (flat 2D for non-cube items)
# ============================================================
def item_barbed_wire():
    """Flat icon: bundled wire on transparent."""
    img = new_img((0, 0, 0, 0))
    wire = (170, 175, 185, 255)
    wire_hi = (215, 220, 230, 255)
    wire_lo = (95, 100, 110, 255)
    # 3 horizontal twisted wires
    for y in (4, 8, 12):
        for x in range(2, 14):
            put(img, x, y, wire)
            if x % 2 == 0:
                put(img, x, y - 0, wire_hi)
            else:
                put(img, x, y, wire_lo)
        # barbs on each wire
        for cx in (5, 9, 13):
            put(img, cx, y - 1, wire_hi)
            put(img, cx, y + 1, wire_hi)
            put(img, cx - 1, y, wire_hi)
            put(img, cx + 1, y, wire_hi)
    # end caps
    for y in (4, 8, 12):
        put(img, 1, y, wire_lo)
        put(img, 14, y, wire_lo)
    return img


def item_razor_wire_fence():
    """Roll of razor wire."""
    img = new_img((0, 0, 0, 0))
    wire = (180, 185, 195, 255)
    wire_hi = (225, 230, 240, 255)
    # spiral coil — 3 concentric arcs
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            if 6.0 < d < 7.0 or 4.0 < d < 5.0 or 2.0 < d < 3.0:
                img.putpixel((x, y), wire)
    # razor flats at top/bottom/sides of outer coil
    for cx, cy in [(7, 0), (7, 15), (0, 7), (15, 7), (3, 3), (12, 3), (3, 12), (12, 12)]:
        put(img, cx, cy, wire_hi)
    return img


def item_first_aid_kit():
    """Flat first-aid kit icon."""
    img = new_img((0, 0, 0, 0))
    case = (52, 72, 48, 255)
    case_hi = (85, 105, 75, 255)
    case_lo = (32, 48, 28, 255)
    white = (235, 240, 245, 255)
    red = (200, 40, 40, 255)
    handle = (60, 60, 65, 255)
    # body
    fill_rect(img, 2, 4, 13, 13, case)
    stroke_rect(img, 2, 4, 13, 13, case_lo)
    fill_rect(img, 3, 5, 12, 5, case_hi)
    # handle
    fill_rect(img, 6, 2, 9, 3, handle)
    put(img, 6, 3, case_hi)
    put(img, 9, 3, case_hi)
    # white cross
    fill_rect(img, 5, 7, 10, 10, white)
    fill_rect(img, 7, 6, 8, 11, red)
    fill_rect(img, 5, 8, 10, 9, red)
    return img


def item_trench_lantern():
    """Hanging lantern icon."""
    img = new_img((0, 0, 0, 0))
    frame = (45, 42, 38, 255)
    frame_hi = (80, 74, 68, 255)
    glow = (255, 220, 110, 255)
    glow_hi = (255, 245, 195, 255)
    # hanging hook
    put(img, 7, 0, frame_hi)
    put(img, 8, 0, frame_hi)
    put(img, 7, 1, frame)
    put(img, 8, 1, frame)
    put(img, 7, 2, frame)
    put(img, 8, 2, frame)
    # body
    fill_rect(img, 3, 3, 12, 4, frame_hi)
    fill_rect(img, 4, 4, 11, 12, glow)
    fill_rect(img, 5, 6, 10, 9, glow_hi)
    # base
    fill_rect(img, 3, 12, 12, 13, frame_hi)
    fill_rect(img, 2, 13, 13, 14, frame)
    # frame bars
    for y in range(4, 12):
        put(img, 3, y, frame)
        put(img, 12, y, frame)
        put(img, 7, y, frame)
        put(img, 8, y, frame)
    return img


def item_field_radio():
    """Compact radio icon with antenna."""
    img = new_img((0, 0, 0, 0))
    body = (75, 85, 55, 255)
    body_hi = (105, 115, 80, 255)
    body_lo = (50, 58, 35, 255)
    metal = (60, 62, 65, 255)
    # antenna
    for y in range(0, 5):
        put(img, 12, y, metal)
    put(img, 13, 0, metal)
    # body
    fill_rect(img, 2, 4, 13, 14, body)
    stroke_rect(img, 2, 4, 13, 14, body_lo)
    fill_rect(img, 3, 5, 12, 5, body_hi)
    # speaker grille
    for y in range(6, 9):
        for x in range(4, 12):
            if (x + y) % 2 == 0:
                put(img, x, y, metal)
    # dial
    fill_rect(img, 4, 10, 11, 12, (200, 195, 175, 255))
    put(img, 7, 11, (210, 60, 50, 255))
    put(img, 8, 11, (210, 60, 50, 255))
    # knob
    put(img, 11, 13, metal)
    return img


def item_field_spotlight():
    """Spotlight on tripod stub."""
    img = new_img((0, 0, 0, 0))
    body = (60, 62, 70, 255)
    body_hi = (110, 113, 122, 255)
    body_lo = (35, 36, 42, 255)
    lens = (255, 252, 220, 255)
    lens_mid = (245, 215, 130, 255)
    # housing
    fill_rect(img, 2, 2, 13, 11, body)
    stroke_rect(img, 2, 2, 13, 11, body_lo)
    fill_rect(img, 3, 3, 12, 3, body_hi)
    # lens
    for y in range(SIZE):
        for x in range(SIZE):
            d = ((x - 7.5) ** 2 + (y - 6.5) ** 2) ** 0.5
            if d < 2.0:
                img.putpixel((x, y), lens)
            elif d < 3.5:
                img.putpixel((x, y), lens_mid)
    # tripod
    for k in range(3):
        put(img, 5 - k, 12 + k, body)
        put(img, 7, 12 + k, body)
        put(img, 8, 12 + k, body)
        put(img, 10 + k, 12 + k, body)
    return img


def item_field_kitchen():
    """Stove with pot on top."""
    img = new_img((0, 0, 0, 0))
    body = (118, 121, 128, 255)
    body_hi = (170, 175, 185, 255)
    body_lo = (70, 73, 80, 255)
    fire = (240, 130, 40, 255)
    fire_hi = (255, 220, 120, 255)
    # legs
    put(img, 3, 14, body_lo)
    put(img, 12, 14, body_lo)
    # base / fire
    fill_rect(img, 3, 11, 12, 13, body_lo)
    fill_rect(img, 5, 11, 10, 12, fire)
    put(img, 6, 11, fire_hi)
    put(img, 9, 11, fire_hi)
    # pot
    fill_rect(img, 2, 5, 13, 10, body)
    stroke_rect(img, 2, 5, 13, 10, body_lo)
    fill_rect(img, 3, 6, 12, 6, body_hi)
    # handle
    put(img, 1, 6, body_lo)
    put(img, 1, 7, body_lo)
    put(img, 14, 6, body_lo)
    put(img, 14, 7, body_lo)
    # steam
    put(img, 6, 3, (230, 230, 235, 220))
    put(img, 7, 2, (230, 230, 235, 220))
    put(img, 9, 3, (230, 230, 235, 220))
    return img


def item_generator():
    """Compact engine icon."""
    img = new_img((0, 0, 0, 0))
    body = (55, 78, 50, 255)
    body_hi = (90, 115, 80, 255)
    body_lo = (35, 50, 30, 255)
    metal = (55, 58, 62, 255)
    fuel = (90, 75, 35, 255)
    fill_rect(img, 1, 3, 14, 13, body)
    stroke_rect(img, 1, 3, 14, 13, body_lo)
    fill_rect(img, 2, 4, 13, 4, body_hi)
    # vents
    for y in (6, 8, 10):
        fill_rect(img, 3, y, 12, y, metal)
    # fuel cap on top
    fill_rect(img, 5, 1, 9, 2, fuel)
    put(img, 5, 2, body_lo)
    put(img, 9, 2, body_lo)
    # exhaust
    fill_rect(img, 12, 1, 13, 2, metal)
    return img


def item_checkpoint_barrier():
    """Side-on boom barrier icon."""
    img = new_img((0, 0, 0, 0))
    red = (190, 45, 40, 255)
    red_hi = (220, 75, 65, 255)
    white = (235, 238, 245, 255)
    metal = (60, 65, 75, 255)
    # post
    fill_rect(img, 2, 4, 4, 15, metal)
    put(img, 3, 4, (170, 175, 185, 255))
    # boom (rotated stripes)
    for i in range(0, 12, 2):
        x0 = 5 + i
        x1 = x0 + 1
        col = red if (i // 2) % 2 == 0 else white
        fill_rect(img, x0, 6, x1, 8, col)
    # boom hinge
    fill_rect(img, 3, 6, 4, 8, metal)
    return img


def item_metal_gate():
    """Solid gate icon with bars."""
    img = new_img((0, 0, 0, 0))
    body = (140, 143, 150, 255)
    body_hi = (190, 193, 200, 255)
    body_lo = (80, 83, 90, 255)
    # frame
    fill_rect(img, 1, 1, 14, 14, body)
    stroke_rect(img, 1, 1, 14, 14, body_lo)
    fill_rect(img, 2, 2, 13, 2, body_hi)
    # vertical bars
    for x in (4, 7, 10):
        for y in range(2, 14):
            put(img, x, y, body_lo)
        for y in range(2, 14):
            put(img, x + 1, y, body_hi)
    # rivets
    for cx, cy in [(2, 2), (13, 2), (2, 13), (13, 13)]:
        put(img, cx, cy, (40, 42, 48, 255))
    return img


def item_tank_hedgehog():
    """Iconic Czech hedgehog silhouette: 3 perpendicular beams crossing."""
    img = new_img((0, 0, 0, 0))
    base = (118, 122, 130, 255)
    hi = (180, 185, 195, 255)
    lo = (60, 65, 75, 255)
    deep = (35, 38, 45, 255)
    # diagonal beam (top-left to bottom-right)
    for k in range(SIZE):
        for off in (-1, 0, 1):
            x = k + off
            y = k
            if 0 <= x < SIZE:
                put(img, x, y, base)
        if 0 <= k - 1 < SIZE:
            put(img, k - 1, k, lo)
        if 0 <= k + 1 < SIZE:
            put(img, k + 1, k, hi)
    # anti-diagonal beam (top-right to bottom-left)
    for k in range(SIZE):
        for off in (-1, 0, 1):
            x = (15 - k) + off
            y = k
            if 0 <= x < SIZE:
                put(img, x, y, base)
        if 0 <= (15 - k) - 1 < SIZE:
            put(img, (15 - k) - 1, k, lo)
        if 0 <= (15 - k) + 1 < SIZE:
            put(img, (15 - k) + 1, k, hi)
    # vertical beam
    for y in range(SIZE):
        put(img, 7, y, lo)
        put(img, 8, y, base)
        put(img, 9, y, hi)
    # central rivet plate
    fill_rect(img, 6, 6, 9, 9, deep)
    put(img, 7, 7, hi)
    put(img, 8, 8, hi)
    return img


def item_small_tent():
    """Triangular tent silhouette."""
    img = new_img((0, 0, 0, 0))
    canvas = (92, 110, 70, 255)
    canvas_hi = (130, 150, 95, 255)
    canvas_lo = (55, 70, 40, 255)
    rope = (90, 60, 30, 255)
    # tent body (triangle)
    for y in range(3, 14):
        width = (y - 2)
        x0 = 7 - (width // 2)
        x1 = 8 + (width // 2)
        for x in range(x0, x1 + 1):
            put(img, x, y, canvas)
        put(img, x0, y, canvas_lo)
        put(img, x1, y, canvas_lo)
    # top fold
    put(img, 7, 2, canvas_lo)
    put(img, 8, 2, canvas_lo)
    # central seam highlight
    for y in range(3, 14):
        put(img, 7, y, canvas_hi)
    # door slit
    fill_rect(img, 7, 9, 8, 13, canvas_lo)
    # guy ropes
    put(img, 3, 14, rope)
    put(img, 4, 13, rope)
    put(img, 11, 13, rope)
    put(img, 12, 14, rope)
    return img


def item_command_tent():
    img = item_small_tent()
    # darker palette overlay
    px = img.load()
    for y in range(SIZE):
        for x in range(SIZE):
            r, g, b, a = px[x, y]
            if a > 0:
                px[x, y] = shade((r, g, b, a), -15)
    # add a flag/antenna
    metal = (60, 62, 65, 255)
    put(img, 7, 0, metal)
    put(img, 7, 1, metal)
    put(img, 8, 1, (200, 40, 40, 255))
    put(img, 9, 1, (200, 40, 40, 255))
    return img


def item_medical_tent():
    img = new_img((0, 0, 0, 0))
    canvas = (215, 218, 222, 255)
    canvas_hi = (245, 247, 250, 255)
    canvas_lo = (160, 165, 175, 255)
    red = (200, 40, 40, 255)
    rope = (90, 60, 30, 255)
    for y in range(3, 14):
        width = (y - 2)
        x0 = 7 - (width // 2)
        x1 = 8 + (width // 2)
        for x in range(x0, x1 + 1):
            put(img, x, y, canvas)
        put(img, x0, y, canvas_lo)
        put(img, x1, y, canvas_lo)
    put(img, 7, 2, canvas_lo)
    put(img, 8, 2, canvas_lo)
    for y in range(3, 14):
        put(img, 7, y, canvas_hi)
    # red cross
    fill_rect(img, 7, 6, 8, 11, red)
    fill_rect(img, 5, 8, 10, 9, red)
    put(img, 3, 14, rope)
    put(img, 12, 14, rope)
    return img


# ============================================================
# REGISTRATION
# ============================================================
BLOCK_TEXTURES = {
    # OKOP
    "wooden_support_beam": make_wooden_support_beam,
    "wooden_support_beam_end": make_wooden_support_beam_end,
    "iron_support_beam": make_iron_support_beam,
    "reinforced_support_beam": make_reinforced_support_beam,
    "forest_camo_net": make_forest_camo_net,
    "desert_camo_net": make_desert_camo_net,
    "winter_camo_net": make_winter_camo_net,
    "sandbag": make_sandbag,
    "wooden_horizontal_cover": make_wooden_horizontal_cover,
    "log_horizontal_cover": make_log_horizontal_cover,
    "barbed_wire": make_barbed_wire,
    "drainage_grate": make_drainage_grate,
    "drainage_grate_frame": make_drainage_grate_frame,
    "firing_slot": make_firing_slot,
    "trench_stairs": make_trench_stairs,
    "trench_lantern": make_trench_lantern,
    "trench_lantern_glass": make_trench_lantern_glass,
    "trench_lantern_glow": make_trench_lantern_glow,
    "supply_crate": make_supply_crate,
    "supply_crate_top": make_supply_crate_top,
    # POLEVOY
    "small_tent": make_small_tent,
    "tent_floor": make_tent_floor,
    "tent_frame": make_tent_frame,
    "tent_door": make_tent_door,
    "command_tent": make_command_tent,
    "command_panel": make_command_panel,
    "medical_tent": make_medical_tent,
    "medical_cross": make_medical_cross,
    "field_kitchen": make_field_kitchen,
    "field_kitchen_top": make_field_kitchen_top,
    "field_kitchen_pipe": make_field_kitchen_pipe,
    "first_aid_kit": make_first_aid_kit,
    "field_radio": make_field_radio,
    "field_spotlight": make_field_spotlight,
    "field_spotlight_lens": make_field_spotlight_lens,
    "generator": make_generator,
    "generator_top": make_generator_top,
    # BAZA
    "military_concrete": make_military_concrete,
    "reinforced_concrete": make_reinforced_concrete,
    "hesco_barrier": make_hesco_barrier,
    "hesco_barrier_top": make_hesco_barrier_top,
    "metal_gate": make_metal_gate,
    "checkpoint_barrier": make_checkpoint_barrier,
    "checkpoint_barrier_bar": make_checkpoint_barrier_bar,
    "tank_hedgehog": make_tank_hedgehog,
    "razor_wire_fence": make_razor_wire_fence,
}

# Flat 2D item icons (used by item models with parent=item/generated)
ITEM_TEXTURES = {
    "barbed_wire": item_barbed_wire,
    "razor_wire_fence": item_razor_wire_fence,
    "first_aid_kit": item_first_aid_kit,
    "trench_lantern": item_trench_lantern,
    "field_radio": item_field_radio,
    "field_spotlight": item_field_spotlight,
    "field_kitchen": item_field_kitchen,
    "generator": item_generator,
    "checkpoint_barrier": item_checkpoint_barrier,
    "metal_gate": item_metal_gate,
    "tank_hedgehog": item_tank_hedgehog,
    "small_tent": item_small_tent,
    "command_tent": item_command_tent,
    "medical_tent": item_medical_tent,
}


def main():
    os.makedirs(BLOCK_DIR, exist_ok=True)
    os.makedirs(ITEM_DIR, exist_ok=True)

    print(f"Generating {len(BLOCK_TEXTURES)} block textures -> {BLOCK_DIR}")
    for name, fn in BLOCK_TEXTURES.items():
        img = fn()
        path = os.path.join(BLOCK_DIR, f"{name}.png")
        img.save(path, "PNG")
        print(f"  [OK] block/{name}.png")

    print(f"\nGenerating {len(ITEM_TEXTURES)} item textures -> {ITEM_DIR}")
    for name, fn in ITEM_TEXTURES.items():
        img = fn()
        path = os.path.join(ITEM_DIR, f"{name}.png")
        img.save(path, "PNG")
        print(f"  [OK] item/{name}.png")

    print(f"\nDone! {len(BLOCK_TEXTURES)} block + {len(ITEM_TEXTURES)} item textures.")


if __name__ == "__main__":
    main()
