#!/usr/bin/env python3
"""
War Project - Texture Generator v2.0
Generates all 16x16 placeholder textures for the unified warproject mod.
Improved: better detail, noise, gradients, patterns.
Run: python tools/generate_textures.py
Output: src/main/resources/assets/warproject/textures/block/
"""

import os
import struct
import zlib
import random

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "warproject", "textures", "block"
)

def create_png(width, height, pixels):
    """Create a minimal PNG file from pixel data."""
    def chunk(chunk_type, data):
        c = chunk_type + data
        crc = struct.pack('>I', zlib.crc32(c) & 0xffffffff)
        return struct.pack('>I', len(data)) + c + crc

    header = b'\x89PNG\r\n\x1a\n'
    ihdr = chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 6, 0, 0, 0))
    raw = b''
    for y in range(height):
        raw += b'\x00'
        for x in range(width):
            r, g, b, a = pixels[y * width + x]
            raw += struct.pack('BBBB', r, g, b, a)
    idat = chunk(b'IDAT', zlib.compress(raw))
    iend = chunk(b'IEND', b'')
    return header + ihdr + idat + iend

def clamp(v, lo=0, hi=255):
    return max(lo, min(hi, int(v)))

def solid(r, g, b, a=255, size=16):
    return [(r, g, b, a)] * (size * size)

def noise_fill(base, var=15, size=16, seed=None):
    rng = random.Random(seed if seed else hash(base) & 0xffffffff)
    pixels = []
    for i in range(size * size):
        r = clamp(base[0] + rng.randint(-var, var))
        g = clamp(base[1] + rng.randint(-var, var))
        b = clamp(base[2] + rng.randint(-var, var))
        pixels.append((r, g, b, 255))
    return pixels

def wood_texture(base_r, base_g, base_b, seed=42):
    """Realistic wood grain texture."""
    rng = random.Random(seed)
    pixels = []
    grain_offsets = [rng.randint(-3, 3) for _ in range(16)]
    for y in range(16):
        for x in range(16):
            grain = (y + grain_offsets[x]) % 4
            if grain == 0:
                dr, dg, db = -20, -15, -10
            elif grain == 1:
                dr, dg, db = -5, -3, -2
            elif grain == 2:
                dr, dg, db = 10, 8, 5
            else:
                dr, dg, db = 0, 0, 0
            noise = rng.randint(-8, 8)
            r = clamp(base_r + dr + noise)
            g = clamp(base_g + dg + noise)
            b = clamp(base_b + db + noise)
            pixels.append((r, g, b, 255))
    return pixels

def metal_texture(base_r, base_g, base_b, seed=42):
    """Brushed metal look."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        row_brightness = rng.randint(-5, 5)
        for x in range(16):
            scratch = 15 if rng.random() < 0.08 else 0
            noise = rng.randint(-6, 6)
            r = clamp(base_r + noise + row_brightness + scratch)
            g = clamp(base_g + noise + row_brightness + scratch)
            b = clamp(base_b + noise + row_brightness + scratch)
            pixels.append((r, g, b, 255))
    return pixels

def camo_texture(colors, seed=42):
    """Multi-color camo pattern."""
    rng = random.Random(seed)
    pixels = [None] * 256
    # Fill with base color
    for i in range(256):
        pixels[i] = colors[0]
    # Add blobs of each color
    for color in colors[1:]:
        for _ in range(rng.randint(6, 10)):
            cx, cy = rng.randint(0, 15), rng.randint(0, 15)
            radius = rng.randint(1, 3)
            for dy in range(-radius, radius + 1):
                for dx in range(-radius, radius + 1):
                    nx, ny = (cx + dx) % 16, (cy + dy) % 16
                    if dx*dx + dy*dy <= radius*radius:
                        pixels[ny * 16 + nx] = color
    # Add noise
    result = []
    for p in pixels:
        r = clamp(p[0] + rng.randint(-8, 8))
        g = clamp(p[1] + rng.randint(-8, 8))
        b = clamp(p[2] + rng.randint(-8, 8))
        result.append((r, g, b, p[3] if len(p) > 3 else 255))
    return result

def sandbag_texture(seed=42):
    """Burlap/canvas sandbag texture with visible stitching."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            # Burlap weave pattern
            weave = ((x + y) % 3 == 0)
            base_r, base_g, base_b = (185, 165, 115) if not weave else (170, 150, 100)
            # Stitch lines at edges
            if y == 0 or y == 15 or x == 0 or x == 15:
                base_r, base_g, base_b = 140, 120, 80
            # Horizontal stitch line
            if y == 7 or y == 8:
                base_r -= 15
                base_g -= 12
                base_b -= 10
            noise = rng.randint(-10, 10)
            pixels.append((clamp(base_r + noise), clamp(base_g + noise), clamp(base_b + noise), 255))
    return pixels

def concrete_texture(base_r, base_g, base_b, seed=42):
    """Concrete with small aggregate detail."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            noise = rng.randint(-12, 12)
            # Aggregate spots
            if rng.random() < 0.1:
                noise += rng.choice([-20, 20])
            r = clamp(base_r + noise)
            g = clamp(base_g + noise)
            b = clamp(base_b + noise)
            pixels.append((r, g, b, 255))
    return pixels

def wire_texture(seed=42):
    """Barbed/razor wire - mostly transparent with wire strands."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            # Diagonal wires
            on_wire = False
            if abs(x - y) <= 0 or abs(x - (15 - y)) <= 0:
                on_wire = True
            # Cross wires
            if y == 4 or y == 8 or y == 12:
                if x % 3 == 0:
                    on_wire = True
            # Barbs
            if on_wire and rng.random() < 0.3:
                pixels.append((160, 160, 165, 255))  # barb highlight
            elif on_wire:
                pixels.append((120, 120, 125, 255))  # wire
            else:
                pixels.append((0, 0, 0, 0))  # transparent
    return pixels

def grate_texture(seed=42):
    """Metal grate with holes."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            is_bar = (x % 4 == 0 or y % 4 == 0)
            if is_bar:
                noise = rng.randint(-8, 8)
                pixels.append((clamp(110 + noise), clamp(110 + noise), clamp(115 + noise), 255))
            else:
                pixels.append((30, 30, 30, 200))
    return pixels

def tent_texture(r, g, b, seed=42):
    """Canvas tent with fold lines."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            fold = 0
            if x == 4 or x == 8 or x == 12:
                fold = -12
            if y == 5 or y == 10:
                fold -= 8
            noise = rng.randint(-6, 6)
            pixels.append((clamp(r + fold + noise), clamp(g + fold + noise), clamp(b + fold + noise), 255))
    return pixels

def cross_on_white(seed=42):
    """White tent with red cross."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            # Red cross
            is_cross = (6 <= x <= 9 and 2 <= y <= 13) or (3 <= x <= 12 and 6 <= y <= 9)
            if is_cross:
                noise = rng.randint(-8, 8)
                pixels.append((clamp(200 + noise), clamp(40 + noise), clamp(40 + noise), 255))
            else:
                noise = rng.randint(-6, 6)
                pixels.append((clamp(225 + noise), clamp(225 + noise), clamp(230 + noise), 255))
    return pixels

def hesco_texture(seed=42):
    """HESCO barrier - wire mesh filled with earth."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            is_mesh = (x % 4 == 0 or y % 4 == 0)
            if is_mesh:
                noise = rng.randint(-5, 5)
                pixels.append((clamp(160 + noise), clamp(160 + noise), clamp(165 + noise), 255))
            else:
                noise = rng.randint(-12, 12)
                pixels.append((clamp(155 + noise), clamp(135 + noise), clamp(95 + noise), 255))
    return pixels

def barrier_stripe(seed=42):
    """Red and white diagonal stripes for barrier post."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            stripe = ((x + y) // 4) % 2
            noise = rng.randint(-5, 5)
            if stripe == 0:
                pixels.append((clamp(210 + noise), clamp(50 + noise), clamp(45 + noise), 255))
            else:
                pixels.append((clamp(235 + noise), clamp(235 + noise), clamp(240 + noise), 255))
    return pixels

def barrier_bar_texture(seed=42):
    """Horizontal red/white bar."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            stripe = (x // 4) % 2
            noise = rng.randint(-5, 5)
            if stripe == 0:
                pixels.append((clamp(210 + noise), clamp(50 + noise), clamp(45 + noise), 255))
            else:
                pixels.append((clamp(235 + noise), clamp(235 + noise), clamp(240 + noise), 255))
    return pixels

def lantern_texture(seed=42):
    """Lantern - dark frame with glowing center."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            dist_center = max(abs(x - 7.5), abs(y - 7.5))
            if dist_center >= 6:
                noise = rng.randint(-5, 5)
                pixels.append((clamp(70 + noise), clamp(70 + noise), clamp(75 + noise), 255))
            elif dist_center >= 4:
                noise = rng.randint(-5, 5)
                pixels.append((clamp(90 + noise), clamp(85 + noise), clamp(80 + noise), 255))
            else:
                noise = rng.randint(-10, 10)
                glow = clamp(8 - dist_center) * 8
                pixels.append((clamp(220 + glow + noise), clamp(180 + glow + noise), clamp(60 + noise), 255))
    return pixels

def crate_texture(is_top=False, seed=42):
    """Wooden crate with planks and nails."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            # Plank borders
            is_border = (x == 0 or x == 15 or y == 0 or y == 15)
            is_cross_plank = (x == 7 or x == 8) if not is_top else (y == 7 or y == 8)
            # Nails at intersections
            is_nail = is_border and is_cross_plank
            
            if is_nail:
                pixels.append((80, 80, 85, 255))
            elif is_border or is_cross_plank:
                noise = rng.randint(-8, 8)
                pixels.append((clamp(120 + noise), clamp(85 + noise), clamp(40 + noise), 255))
            else:
                noise = rng.randint(-10, 10)
                pixels.append((clamp(145 + noise), clamp(105 + noise), clamp(55 + noise), 255))
    return pixels

def first_aid_texture(seed=42):
    """First aid kit - green box with white cross."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            is_cross = (6 <= x <= 9 and 3 <= y <= 12) or (4 <= x <= 11 and 6 <= y <= 9)
            is_border = (x <= 1 or x >= 14 or y <= 1 or y >= 14)
            noise = rng.randint(-5, 5)
            if is_border:
                pixels.append((clamp(50 + noise), clamp(65 + noise), clamp(45 + noise), 255))
            elif is_cross:
                pixels.append((clamp(240 + noise), clamp(240 + noise), clamp(245 + noise), 255))
            else:
                pixels.append((clamp(70 + noise), clamp(90 + noise), clamp(60 + noise), 255))
    return pixels

def radio_texture(seed=42):
    """Field radio - olive drab with knobs and speaker."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            is_speaker = (3 <= x <= 12 and 2 <= y <= 8 and (x + y) % 2 == 0)
            is_knob = (x in [4, 11] and y in [11, 12])
            is_dial = (7 <= x <= 8 and 10 <= y <= 13)
            noise = rng.randint(-5, 5)
            if is_knob:
                pixels.append((clamp(40 + noise), clamp(40 + noise), clamp(45 + noise), 255))
            elif is_dial:
                pixels.append((clamp(180 + noise), clamp(180 + noise), clamp(170 + noise), 255))
            elif is_speaker:
                pixels.append((clamp(45 + noise), clamp(55 + noise), clamp(40 + noise), 255))
            else:
                pixels.append((clamp(75 + noise), clamp(85 + noise), clamp(65 + noise), 255))
    return pixels

def spotlight_texture(seed=42):
    """Spotlight - dark body with bright lens."""
    rng = random.Random(seed)
    pixels = []
    for y in range(16):
        for x in range(16):
            dist = ((x - 8)**2 + (y - 8)**2) ** 0.5
            noise = rng.randint(-5, 5)
            if dist < 4:
                glow = int((4 - dist) * 15)
                pixels.append((clamp(255 + noise), clamp(240 + glow + noise), clamp(180 + glow + noise), 255))
            elif dist < 6:
                pixels.append((clamp(200 + noise), clamp(200 + noise), clamp(205 + noise), 255))
            else:
                pixels.append((clamp(80 + noise), clamp(80 + noise), clamp(85 + noise), 255))
    return pixels


# ============================================================
# TEXTURE DEFINITIONS
# ============================================================
TEXTURES = {
    # === OKOP (Fortification) ===
    "wooden_support_beam": lambda: wood_texture(139, 90, 43, seed=1),
    "iron_support_beam": lambda: metal_texture(180, 180, 185, seed=2),
    "reinforced_support_beam": lambda: metal_texture(150, 150, 155, seed=3),
    "forest_camo_net": lambda: camo_texture([
        (60, 100, 40, 200), (80, 130, 55, 200), (45, 75, 30, 200), (70, 110, 50, 200)
    ], seed=4),
    "desert_camo_net": lambda: camo_texture([
        (190, 170, 120, 200), (210, 190, 140, 200), (170, 150, 100, 200), (200, 180, 130, 200)
    ], seed=5),
    "winter_camo_net": lambda: camo_texture([
        (230, 235, 240, 200), (210, 215, 225, 200), (240, 242, 245, 200), (200, 205, 215, 200)
    ], seed=6),
    "sandbag": lambda: sandbag_texture(seed=7),
    "wooden_horizontal_cover": lambda: wood_texture(139, 90, 43, seed=8),
    "log_horizontal_cover": lambda: wood_texture(100, 70, 35, seed=9),
    "barbed_wire": lambda: wire_texture(seed=10),
    "drainage_grate": lambda: grate_texture(seed=11),
    "firing_slot": lambda: concrete_texture(130, 130, 130, seed=12),
    "trench_stairs": lambda: wood_texture(130, 85, 40, seed=13),
    "trench_lantern": lambda: lantern_texture(seed=14),
    "supply_crate": lambda: crate_texture(is_top=False, seed=15),
    "supply_crate_top": lambda: crate_texture(is_top=True, seed=16),

    # === POLEVOY (Field Camp) ===
    "small_tent": lambda: tent_texture(90, 110, 75, seed=20),
    "tent_floor": lambda: noise_fill((100, 85, 60), var=12, seed=21),
    "command_tent": lambda: tent_texture(75, 95, 65, seed=22),
    "medical_tent": lambda: tent_texture(220, 220, 225, seed=23),
    "medical_cross": lambda: cross_on_white(seed=24),
    "field_kitchen": lambda: metal_texture(100, 100, 105, seed=25),
    "field_kitchen_top": lambda: metal_texture(80, 80, 85, seed=26),
    "field_kitchen_pipe": lambda: metal_texture(60, 60, 65, seed=27),
    "first_aid_kit": lambda: first_aid_texture(seed=28),
    "field_radio": lambda: radio_texture(seed=29),
    "field_spotlight": lambda: spotlight_texture(seed=30),
    "generator": lambda: metal_texture(90, 90, 95, seed=31),
    "generator_top": lambda: metal_texture(100, 100, 105, seed=32),

    # === BAZA (Military Base) ===
    "military_concrete": lambda: concrete_texture(160, 160, 160, seed=40),
    "reinforced_concrete": lambda: concrete_texture(140, 140, 145, seed=41),
    "hesco_barrier": lambda: hesco_texture(seed=42),
    "metal_gate": lambda: metal_texture(130, 130, 135, seed=43),
    "checkpoint_barrier": lambda: barrier_stripe(seed=44),
    "checkpoint_barrier_bar": lambda: barrier_bar_texture(seed=45),
    "tank_hedgehog": lambda: metal_texture(100, 100, 105, seed=46),
    "razor_wire_fence": lambda: wire_texture(seed=47),
}

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)
    print(f"Generating {len(TEXTURES)} textures to: {OUTPUT_DIR}")
    for name, gen_func in TEXTURES.items():
        pixels = gen_func()
        png_data = create_png(16, 16, pixels)
        path = os.path.join(OUTPUT_DIR, f"{name}.png")
        with open(path, 'wb') as f:
            f.write(png_data)
        print(f"  [OK] {name}.png ({len(png_data)} bytes)")
    print(f"\nDone! {len(TEXTURES)} textures generated.")
    print("Run the game to test: ./gradlew runClient")

if __name__ == '__main__':
    main()
