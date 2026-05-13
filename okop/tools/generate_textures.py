#!/usr/bin/env python3
"""
Texture Generator for War Project - Okop (Trench Fortification Mod)
===================================================================
Generates detailed 16x16 pixel-art textures for all fortification blocks.

Requirements:
    pip install Pillow

Usage:
    python tools/generate_textures.py

Output:
    src/main/resources/assets/warproject/textures/block/*.png
"""

import os
import random
from PIL import Image, ImageDraw

OUTPUT_DIR = os.path.join("src", "main", "resources", "assets", "warproject", "textures", "block")
random.seed(42)  # Reproducible textures


def ensure_dir():
    os.makedirs(OUTPUT_DIR, exist_ok=True)


def save(img, name):
    img.save(os.path.join(OUTPUT_DIR, f"{name}.png"))
    print(f"  [OK] {name}.png")


def vary(base, amount=12):
    """Return a color tuple with slight random variation."""
    return tuple(max(0, min(255, c + random.randint(-amount, amount))) for c in base)


def fill_noise(img, base, dark, light, dark_chance=0.15, light_chance=0.15):
    """Fill image with noisy base color."""
    for y in range(16):
        for x in range(16):
            r = random.random()
            if r < dark_chance:
                img.putpixel((x, y), vary(dark) + (255,))
            elif r < dark_chance + light_chance:
                img.putpixel((x, y), vary(light) + (255,))
            else:
                img.putpixel((x, y), vary(base) + (255,))


# ============================================================
# 1. SUPPORT BEAMS
# ============================================================

def gen_wooden_support_beam():
    """Brown wood grain with vertical dark streaks and knot details."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (139, 90, 43)
    dark = (95, 60, 28)
    light = (170, 120, 65)
    fill_noise(img, base, dark, light)
    # Vertical grain lines
    for gx in [2, 5, 10, 13]:
        for y in range(16):
            if random.random() < 0.75:
                img.putpixel((gx, y), vary(dark, 8) + (255,))
    # Wood knot at (7,7)
    for dx, dy in [(-1,0),(1,0),(0,-1),(0,1),(0,0)]:
        px, py = 7+dx, 7+dy
        if 0 <= px < 16 and 0 <= py < 16:
            img.putpixel((px, py), (80, 50, 20, 255))
    # Border highlights
    for y in range(16):
        img.putpixel((0, y), vary((110, 70, 35), 5) + (255,))
        img.putpixel((15, y), vary((110, 70, 35), 5) + (255,))
    save(img, "wooden_support_beam")


def gen_iron_support_beam():
    """Gray metal beam with bolt/rivet details."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (160, 160, 165)
    dark = (110, 110, 115)
    light = (195, 195, 200)
    fill_noise(img, base, dark, light, 0.1, 0.1)
    # Vertical seam lines
    for gx in [3, 12]:
        for y in range(16):
            img.putpixel((gx, y), vary((90, 90, 95), 5) + (255,))
    # Rivets (bright spots)
    rivets = [(3,2),(3,7),(3,12),(12,2),(12,7),(12,12)]
    for rx, ry in rivets:
        img.putpixel((rx, ry), (220, 220, 225, 255))
        for dx, dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            px, py = rx+dx, ry+dy
            if 0 <= px < 16 and 0 <= py < 16:
                img.putpixel((px, py), vary((130, 130, 135), 5) + (255,))
    # Top/bottom darker edges
    for x in range(16):
        img.putpixel((x, 0), vary(dark, 5) + (255,))
        img.putpixel((x, 15), vary(dark, 5) + (255,))
    save(img, "iron_support_beam")


def gen_reinforced_support_beam():
    """Dark heavy metal with cross-bracing pattern and bright rivets."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (80, 85, 90)
    dark = (50, 52, 58)
    light = (120, 125, 130)
    fill_noise(img, base, dark, light, 0.12, 0.08)
    # Cross bracing
    for i in range(16):
        if 0 <= i < 16:
            img.putpixel((i, i), vary((60, 62, 68), 5) + (255,))
            img.putpixel((15-i, i), vary((60, 62, 68), 5) + (255,))
    # Rivets (golden/bright)
    rivets = [(2,2),(13,2),(2,13),(13,13),(7,7),(8,8)]
    for rx, ry in rivets:
        img.putpixel((rx, ry), (240, 210, 80, 255))
    # Steel border
    for i in range(16):
        img.putpixel((0, i), vary((45, 48, 52), 3) + (255,))
        img.putpixel((15, i), vary((45, 48, 52), 3) + (255,))
        img.putpixel((i, 0), vary((45, 48, 52), 3) + (255,))
        img.putpixel((i, 15), vary((45, 48, 52), 3) + (255,))
    save(img, "reinforced_support_beam")


# ============================================================
# 2. CAMO NETS
# ============================================================

def gen_camo_net(name, colors):
    """Mesh net pattern with semi-transparency. colors = (primary, secondary, accent)"""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))  # Transparent base
    primary, secondary, accent = colors
    # Create irregular mesh pattern
    for y in range(16):
        for x in range(16):
            # Mesh grid pattern with irregularity
            on_grid = (x % 3 == 0 or y % 3 == 0)
            if on_grid:
                r = random.random()
                if r < 0.5:
                    img.putpixel((x, y), vary(primary, 15) + (220,))
                elif r < 0.8:
                    img.putpixel((x, y), vary(secondary, 15) + (220,))
                else:
                    img.putpixel((x, y), vary(accent, 10) + (200,))
            else:
                # Holes in the net (transparent or semi-transparent)
                if random.random() < 0.3:
                    img.putpixel((x, y), vary(primary, 20) + (80,))
                # else stays transparent
    # Add knot points at intersections
    for ky in range(0, 16, 3):
        for kx in range(0, 16, 3):
            if kx < 16 and ky < 16:
                img.putpixel((kx, ky), vary(secondary, 8) + (255,))
    save(img, name)


def gen_forest_camo_net():
    gen_camo_net("forest_camo_net", ((60, 100, 40), (45, 75, 30), (80, 60, 30)))

def gen_desert_camo_net():
    gen_camo_net("desert_camo_net", ((190, 170, 120), (165, 145, 95), (210, 190, 140)))

def gen_winter_camo_net():
    gen_camo_net("winter_camo_net", ((220, 225, 230), (190, 195, 200), (240, 240, 245)))


# ============================================================
# 3. SANDBAGS
# ============================================================

def gen_sandbag():
    """Burlap/canvas bag texture with visible bag outlines."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (185, 165, 120)
    dark = (145, 130, 90)
    light = (210, 190, 145)
    fill_noise(img, base, dark, light, 0.12, 0.12)
    # Bag outlines (horizontal seams showing stacked bags)
    for y in [3, 4, 11, 12]:
        for x in range(16):
            img.putpixel((x, y), vary(dark, 8) + (255,))
    # Vertical tie/seam in the middle
    for y in range(4, 12):
        img.putpixel((7, y), vary((130, 115, 80), 5) + (255,))
        img.putpixel((8, y), vary((130, 115, 80), 5) + (255,))
    # Burlap weave texture
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0 and random.random() < 0.4:
                c = img.getpixel((x, y))
                dimmed = tuple(max(0, v - 15) for v in c[:3]) + (c[3],)
                img.putpixel((x, y), dimmed)
    # Highlight on top of bags
    for x in range(16):
        if random.random() < 0.5:
            img.putpixel((x, 5), vary(light, 5) + (255,))
    save(img, "sandbag")


# ============================================================
# 4. HORIZONTAL COVERS
# ============================================================

def gen_wooden_horizontal_cover():
    """Plank pattern (horizontal wooden boards)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (160, 115, 60)
    dark = (120, 80, 40)
    light = (190, 140, 80)
    fill_noise(img, base, dark, light, 0.1, 0.15)
    # Plank gaps (horizontal dark lines)
    for y in [0, 5, 10, 15]:
        for x in range(16):
            img.putpixel((x, y), vary((70, 45, 20), 5) + (255,))
    # Nail heads
    nails = [(2,2),(13,2),(2,7),(13,7),(2,12),(13,12)]
    for nx, ny in nails:
        img.putpixel((nx, ny), (100, 100, 105, 255))
    # Wood grain (horizontal streaks per plank)
    for py_start in [1, 6, 11]:
        gx = random.randint(2, 13)
        for y in range(py_start, min(py_start + 4, 16)):
            if random.random() < 0.6:
                img.putpixel((gx, y), vary(dark, 8) + (255,))
    save(img, "wooden_horizontal_cover")


def gen_log_horizontal_cover():
    """Log bark texture with ring details."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (100, 75, 45)
    dark = (65, 45, 25)
    light = (135, 100, 60)
    fill_noise(img, base, dark, light, 0.18, 0.1)
    # Bark ridges (vertical dark lines)
    for gx in [1, 4, 7, 10, 14]:
        for y in range(16):
            if random.random() < 0.65:
                img.putpixel((gx, y), vary(dark, 6) + (255,))
    # Horizontal log separations
    for y in [0, 7, 8, 15]:
        for x in range(16):
            img.putpixel((x, y), vary((50, 35, 18), 5) + (255,))
    # Ring detail visible on cut end
    center = (4, 4)
    for r in [1, 2, 3]:
        for angle_step in range(r * 8):
            import math
            a = (angle_step / (r * 8)) * 2 * math.pi
            px = int(center[0] + r * math.cos(a))
            py = int(center[1] + r * math.sin(a))
            if 0 <= px < 8 and 0 <= py < 7:
                img.putpixel((px, py), vary((125, 90, 50), 8) + (255,))
    save(img, "log_horizontal_cover")


# ============================================================
# 5. BARBED WIRE
# ============================================================

def gen_barbed_wire():
    """Wire cross pattern with barb details on transparent background."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))  # Fully transparent
    wire_color = (140, 140, 145)
    barb_color = (180, 180, 185)
    dark_wire = (90, 90, 95)
    # Diagonal wire lines
    for i in range(16):
        # Main X-cross
        img.putpixel((i, i), vary(wire_color, 8) + (255,))
        img.putpixel((15-i, i), vary(wire_color, 8) + (255,))
    # Horizontal and vertical center wires
    for i in range(16):
        img.putpixel((i, 7), vary(wire_color, 8) + (255,))
        img.putpixel((i, 8), vary(dark_wire, 8) + (255,))
        img.putpixel((7, i), vary(wire_color, 8) + (255,))
        img.putpixel((8, i), vary(dark_wire, 8) + (255,))
    # Barb spikes at intersections
    barb_positions = [(3,3),(12,3),(3,12),(12,12),(7,7),(0,0),(15,15),(15,0),(0,15)]
    for bx, by in barb_positions:
        for dx, dy in [(-1,-1),(1,-1),(-1,1),(1,1)]:
            px, py = bx+dx, by+dy
            if 0 <= px < 16 and 0 <= py < 16:
                img.putpixel((px, py), vary(barb_color, 5) + (255,))
    save(img, "barbed_wire")


# ============================================================
# 6. DRAINAGE GRATE
# ============================================================

def gen_drainage_grate():
    """Wooden slat grate with gaps (semi-transparent)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))  # Transparent base
    slat_color = (140, 100, 55)
    slat_dark = (100, 70, 35)
    slat_light = (170, 125, 70)
    # Horizontal slats every 3 pixels
    for y in range(16):
        is_slat = (y % 3 != 2)  # Slat rows with gaps
        for x in range(16):
            if is_slat:
                r = random.random()
                if r < 0.15:
                    img.putpixel((x, y), vary(slat_dark, 8) + (240,))
                elif r < 0.3:
                    img.putpixel((x, y), vary(slat_light, 8) + (240,))
                else:
                    img.putpixel((x, y), vary(slat_color, 10) + (240,))
    # Cross supports
    for x in [2, 7, 12]:
        for y in range(16):
            if y % 3 == 2:  # Only in gap rows
                img.putpixel((x, y), vary(slat_dark, 5) + (200,))
    save(img, "drainage_grate")


# ============================================================
# 7. FIRING SLOT (EMBRASURE)
# ============================================================

def gen_firing_slot():
    """Clean stone/concrete texture for the outer faces of the embrasure.
    No black slit — the opening is part of the 3D model geometry."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (130, 130, 125)
    dark = (90, 90, 85)
    light = (165, 165, 160)
    fill_noise(img, base, dark, light, 0.15, 0.1)
    # Stone block mortar lines (edges)
    for x in range(16):
        img.putpixel((x, 0), vary((80, 80, 75), 5) + (255,))
        img.putpixel((x, 15), vary((80, 80, 75), 5) + (255,))
    for y in range(16):
        img.putpixel((0, y), vary((80, 80, 75), 5) + (255,))
        img.putpixel((15, y), vary((80, 80, 75), 5) + (255,))
    # Additional mortar cross for a brick/block feel
    for x in range(16):
        img.putpixel((x, 7), vary((95, 95, 90), 5) + (255,))
        img.putpixel((x, 8), vary((95, 95, 90), 5) + (255,))
    for y in range(0, 8):
        img.putpixel((7, y), vary((95, 95, 90), 5) + (255,))
    for y in range(8, 16):
        img.putpixel((11, y), vary((95, 95, 90), 5) + (255,))
    # Subtle surface cracks
    crack_pixels = [(3,3),(4,4),(5,4),(10,11),(11,12),(12,12)]
    for cx, cy in crack_pixels:
        img.putpixel((cx, cy), vary((75, 75, 70), 5) + (255,))
    save(img, "firing_slot")


def gen_firing_slot_inner():
    """Darker stone texture for the inner walls of the embrasure hole.
    Slightly darker and rougher than the outer texture."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (95, 92, 88)
    dark = (65, 62, 58)
    light = (120, 118, 112)
    fill_noise(img, base, dark, light, 0.2, 0.08)
    # Rough chiseled marks
    for y in range(16):
        for x in range(16):
            if (x + y * 3) % 7 == 0 and random.random() < 0.5:
                img.putpixel((x, y), vary((55, 52, 48), 8) + (255,))
    # Subtle edge wear
    for x in range(16):
        img.putpixel((x, 0), vary((75, 72, 68), 5) + (255,))
        img.putpixel((x, 15), vary((75, 72, 68), 5) + (255,))
    for y in range(16):
        img.putpixel((0, y), vary((75, 72, 68), 5) + (255,))
        img.putpixel((15, y), vary((75, 72, 68), 5) + (255,))
    save(img, "firing_slot_inner")


# ============================================================
# 8. TRENCH STAIRS
# ============================================================

def gen_trench_stairs():
    """Wooden plank stair texture with wear marks."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (150, 110, 55)
    dark = (105, 75, 35)
    light = (180, 135, 75)
    fill_noise(img, base, dark, light, 0.1, 0.15)
    # Step edges (horizontal dark lines)
    for y in [0, 4, 8, 12]:
        for x in range(16):
            img.putpixel((x, y), vary(dark, 5) + (255,))
    # Tread wear marks (lighter patches in center of each step)
    for y_start in [1, 5, 9, 13]:
        for x in range(4, 12):
            for y in range(y_start, min(y_start + 2, 16)):
                if random.random() < 0.4:
                    img.putpixel((x, y), vary(light, 10) + (255,))
    # Side rail marks
    for y in range(16):
        img.putpixel((1, y), vary((90, 60, 30), 5) + (255,))
        img.putpixel((14, y), vary((90, 60, 30), 5) + (255,))
    save(img, "trench_stairs")


# ============================================================
# 9. TRENCH LANTERN
# ============================================================

def gen_trench_lantern():
    """Metal frame lantern with warm amber glow center."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    # Metal frame
    frame = (100, 95, 90)
    frame_dark = (65, 62, 58)
    # Fill with dark metal
    for y in range(16):
        for x in range(16):
            img.putpixel((x, y), vary(frame, 8) + (255,))
    # Top cap
    for x in range(4, 12):
        img.putpixel((x, 0), vary(frame_dark, 5) + (255,))
        img.putpixel((x, 1), vary(frame_dark, 5) + (255,))
    # Handle on top
    for x in range(6, 10):
        img.putpixel((x, 0), vary((130, 125, 120), 5) + (255,))
    # Warm amber glow center (the glass/flame area)
    glow_bright = (255, 200, 80)
    glow_mid = (230, 160, 50)
    glow_dim = (180, 120, 40)
    for y in range(3, 13):
        for x in range(3, 13):
            dist = abs(x - 7.5) + abs(y - 8)
            if dist < 3:
                img.putpixel((x, y), vary(glow_bright, 10) + (255,))
            elif dist < 5:
                img.putpixel((x, y), vary(glow_mid, 10) + (255,))
            elif dist < 7:
                img.putpixel((x, y), vary(glow_dim, 10) + (255,))
    # Frame bars over the glow
    for y in range(2, 14):
        img.putpixel((3, y), vary(frame_dark, 3) + (255,))
        img.putpixel((12, y), vary(frame_dark, 3) + (255,))
    for x in range(3, 13):
        img.putpixel((x, 2), vary(frame_dark, 3) + (255,))
        img.putpixel((x, 13), vary(frame_dark, 3) + (255,))
    # Bottom base
    for x in range(3, 13):
        img.putpixel((x, 14), vary(frame_dark, 5) + (255,))
        img.putpixel((x, 15), vary(frame_dark, 5) + (255,))
    save(img, "trench_lantern")


# ============================================================
# 10. SUPPLY CRATE
# ============================================================

def gen_supply_crate():
    """Wooden crate with metal corner brackets and stenciled markings."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    base = (140, 105, 55)
    dark = (100, 70, 35)
    light = (175, 135, 75)
    fill_noise(img, base, dark, light, 0.1, 0.12)
    # Plank lines (horizontal boards)
    for y in [0, 5, 10, 15]:
        for x in range(16):
            img.putpixel((x, y), vary(dark, 5) + (255,))
    # Vertical reinforcement strips
    for x in [0, 7, 8, 15]:
        for y in range(16):
            img.putpixel((x, y), vary((90, 65, 30), 5) + (255,))
    # Metal corner brackets (L-shapes in corners)
    metal = (150, 150, 155)
    corners = [(0,0,3,3), (13,0,16,3), (0,13,3,16), (13,13,16,16)]
    for x1,y1,x2,y2 in corners:
        for x in range(x1, min(x2, 16)):
            img.putpixel((x, y1), vary(metal, 5) + (255,))
            if y2-1 < 16:
                img.putpixel((x, y2-1), vary(metal, 5) + (255,))
        for y in range(y1, min(y2, 16)):
            img.putpixel((x1, y), vary(metal, 5) + (255,))
            if x2-1 < 16:
                img.putpixel((x2-1, y), vary(metal, 5) + (255,))
    # Corner rivets
    for rx, ry in [(1,1),(14,1),(1,14),(14,14)]:
        img.putpixel((rx, ry), (200, 200, 205, 255))
    # Center cross marking (stencil)
    stencil = (80, 55, 25)
    for i in range(6, 10):
        img.putpixel((i, 7), vary(stencil, 5) + (255,))
        img.putpixel((i, 8), vary(stencil, 5) + (255,))
        img.putpixel((7, i), vary(stencil, 5) + (255,))
        img.putpixel((8, i), vary(stencil, 5) + (255,))
    save(img, "supply_crate")


# ============================================================
# MAIN
# ============================================================

def main():
    ensure_dir()
    print("Generating textures for War Project - Okop...\n")

    print("[Support Beams]")
    gen_wooden_support_beam()
    gen_iron_support_beam()
    gen_reinforced_support_beam()

    print("\n[Camo Nets]")
    gen_forest_camo_net()
    gen_desert_camo_net()
    gen_winter_camo_net()

    print("\n[Sandbags]")
    gen_sandbag()

    print("\n[Horizontal Covers]")
    gen_wooden_horizontal_cover()
    gen_log_horizontal_cover()

    print("\n[Barbed Wire]")
    gen_barbed_wire()

    print("\n[Drainage Grate]")
    gen_drainage_grate()

    print("\n[Firing Slot]")
    gen_firing_slot()
    gen_firing_slot_inner()

    print("\n[Trench Stairs]")
    gen_trench_stairs()

    print("\n[Trench Lantern]")
    gen_trench_lantern()

    print("\n[Supply Crate]")
    gen_supply_crate()

    print(f"\n=== Done! {16} textures generated in {OUTPUT_DIR} ===")
    print("\nNext steps:")
    print("  1. Review textures in an image viewer")
    print("  2. Run 'gradlew runClient' to test in-game")
    print("  3. Adjust colors/patterns in this script as needed")


if __name__ == "__main__":
    main()
