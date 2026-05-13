#!/usr/bin/env python3
"""
War Project - Texture Generator
Generates all 16x16 placeholder textures for the unified warproject mod.
Run: python tools/generate_textures.py
Output: src/main/resources/assets/warproject/textures/block/
"""

import os
import struct
import zlib

OUTPUT_DIR = os.path.join(
    os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
    "src", "main", "resources", "assets", "warproject", "textures", "block"
)

def create_png(width, height, pixels):
    """Create a minimal PNG file from pixel data (list of (r,g,b,a) tuples)."""
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

def solid(r, g, b, a=255, size=16):
    return [(r, g, b, a)] * (size * size)

def checker(c1, c2, size=16, block=2):
    pixels = []
    for y in range(size):
        for x in range(size):
            if ((x // block) + (y // block)) % 2 == 0:
                pixels.append(c1)
            else:
                pixels.append(c2)
    return pixels

def striped(c1, c2, size=16, stripe_w=2, vertical=True):
    pixels = []
    for y in range(size):
        for x in range(size):
            coord = x if vertical else y
            if (coord // stripe_w) % 2 == 0:
                pixels.append(c1)
            else:
                pixels.append(c2)
    return pixels

def bordered(fill, border, size=16, bw=1):
    pixels = []
    for y in range(size):
        for x in range(size):
            if x < bw or x >= size - bw or y < bw or y >= size - bw:
                pixels.append(border)
            else:
                pixels.append(fill)
    return pixels

def gradient_v(c_top, c_bot, size=16):
    pixels = []
    for y in range(size):
        t = y / (size - 1)
        r = int(c_top[0] + (c_bot[0] - c_top[0]) * t)
        g = int(c_top[1] + (c_bot[1] - c_top[1]) * t)
        b = int(c_top[2] + (c_bot[2] - c_top[2]) * t)
        for x in range(size):
            pixels.append((r, g, b, 255))
    return pixels

def noise_fill(base, var=15, size=16):
    import random
    random.seed(hash(base) & 0xffffffff)
    pixels = []
    for i in range(size * size):
        r = max(0, min(255, base[0] + random.randint(-var, var)))
        g = max(0, min(255, base[1] + random.randint(-var, var)))
        b = max(0, min(255, base[2] + random.randint(-var, var)))
        pixels.append((r, g, b, 255))
    return pixels

# ============================================================
# TEXTURE DEFINITIONS
# ============================================================
TEXTURES = {
    # === OKOP (Fortification) ===
    "wooden_support_beam": lambda: striped((139, 90, 43, 255), (120, 75, 35, 255), stripe_w=4),
    "iron_support_beam": lambda: striped((180, 180, 185, 255), (160, 160, 168, 255), stripe_w=4),
    "reinforced_support_beam": lambda: striped((150, 150, 155, 255), (130, 130, 140, 255), stripe_w=3),
    "forest_camo_net": lambda: checker((60, 100, 40, 200), (80, 130, 55, 200), block=3),
    "desert_camo_net": lambda: checker((190, 170, 120, 200), (210, 190, 140, 200), block=3),
    "winter_camo_net": lambda: checker((230, 235, 240, 200), (210, 215, 225, 200), block=3),
    "sandbag": lambda: noise_fill((180, 160, 110)),
    "wooden_horizontal_cover": lambda: striped((139, 90, 43, 255), (155, 105, 55, 255), stripe_w=4, vertical=False),
    "log_horizontal_cover": lambda: striped((100, 70, 35, 255), (85, 58, 28, 255), stripe_w=4, vertical=False),
    "barbed_wire": lambda: checker((180, 180, 180, 180), (0, 0, 0, 0), block=1),
    "drainage_grate": lambda: striped((100, 100, 105, 255), (60, 60, 65, 255), stripe_w=2),
    "firing_slot": lambda: bordered((130, 130, 130, 255), (100, 100, 105, 255), bw=2),
    "trench_stairs": lambda: striped((139, 90, 43, 255), (110, 72, 35, 255), stripe_w=4, vertical=False),
    "trench_lantern": lambda: bordered((255, 200, 80, 255), (80, 80, 85, 255), bw=4),
    "supply_crate": lambda: bordered((139, 100, 50, 255), (110, 75, 35, 255), bw=2),
    "supply_crate_top": lambda: bordered((150, 110, 60, 255), (110, 75, 35, 255), bw=2),

    # === POLEVOY (Field Camp) ===
    "small_tent": lambda: solid(90, 110, 75),
    "tent_floor": lambda: noise_fill((100, 85, 60)),
    "command_tent": lambda: solid(75, 95, 65),
    "medical_tent": lambda: solid(220, 220, 220),
    "field_kitchen": lambda: solid(100, 100, 105),
    "field_kitchen_top": lambda: solid(80, 80, 85),
    "field_kitchen_pipe": lambda: solid(60, 60, 65),
    "first_aid_kit": lambda: bordered((200, 50, 50, 255), (180, 40, 40, 255), bw=2),
    "field_radio": lambda: bordered((70, 80, 65, 255), (55, 65, 50, 255), bw=2),
    "field_spotlight": lambda: bordered((200, 200, 200, 255), (150, 150, 155, 255), bw=2),
    "generator": lambda: bordered((90, 90, 95, 255), (70, 70, 75, 255), bw=2),
    "generator_top": lambda: bordered((100, 100, 105, 255), (70, 70, 75, 255), bw=2),

    # === BAZA (Military Base) ===
    "military_concrete": lambda: noise_fill((160, 160, 160)),
    "reinforced_concrete": lambda: noise_fill((140, 140, 145)),
    "hesco_barrier": lambda: checker((180, 160, 120, 255), (160, 140, 100, 255), block=4),
    "metal_gate": lambda: striped((130, 130, 135, 255), (110, 110, 118, 255), stripe_w=2),
    "checkpoint_barrier": lambda: striped((200, 50, 50, 255), (220, 220, 220, 255), stripe_w=4, vertical=False),
    "checkpoint_barrier_bar": lambda: striped((200, 50, 50, 255), (240, 240, 240, 255), stripe_w=4),
    "tank_hedgehog": lambda: solid(100, 100, 105),
    "razor_wire_fence": lambda: checker((180, 180, 180, 200), (0, 0, 0, 0), block=1),
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
        print(f"  [OK] {name}.png")
    print(f"\nDone! {len(TEXTURES)} textures generated.")
    print("Don't forget to run the game to test them!")

if __name__ == '__main__':
    main()
