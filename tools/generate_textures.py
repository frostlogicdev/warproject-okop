#!/usr/bin/env python3
"""Generate all placeholder textures for War Project mods (okop, polevoy, baza).
Run from the repo root: python tools/generate_textures.py
Requires Pillow: pip install Pillow
"""
import os
from PIL import Image, ImageDraw

def px(img, x, y, color):
    if 0 <= x < 16 and 0 <= y < 16:
        img.putpixel((x, y), color)

def fill_rect(img, x1, y1, x2, y2, color):
    draw = ImageDraw.Draw(img)
    draw.rectangle([x1, y1, x2, y2], fill=color)

def add_noise(img, intensity=15):
    import random
    for x in range(16):
        for y in range(16):
            r, g, b, a = img.getpixel((x, y))
            if a > 0:
                d = random.randint(-intensity, intensity)
                img.putpixel((x, y), (max(0,min(255,r+d)), max(0,min(255,g+d)), max(0,min(255,b+d)), a))

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print(f"  -> {path}")

def solid(color):
    img = Image.new('RGBA', (16, 16), color)
    add_noise(img, 10)
    return img

def striped(c1, c2, horiz=True, width=2):
    img = Image.new('RGBA', (16, 16), c1)
    draw = ImageDraw.Draw(img)
    for i in range(0, 16, width * 2):
        if horiz:
            draw.rectangle([0, i, 15, i + width - 1], fill=c2)
        else:
            draw.rectangle([i, 0, i + width - 1, 15], fill=c2)
    add_noise(img, 8)
    return img

def cross_on(base_color, cross_color):
    img = solid(base_color)
    draw = ImageDraw.Draw(img)
    draw.rectangle([7, 3, 8, 12], fill=cross_color)
    draw.rectangle([4, 6, 11, 8], fill=cross_color)
    return img

def grid_pattern(c1, c2, spacing=4):
    img = Image.new('RGBA', (16, 16), c1)
    draw = ImageDraw.Draw(img)
    for i in range(0, 16, spacing):
        draw.line([(i, 0), (i, 15)], fill=c2)
        draw.line([(0, i), (15, i)], fill=c2)
    add_noise(img, 6)
    return img

def wire_pattern(c1, c2):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)
    for y in range(0, 16, 3):
        draw.line([(0, y), (15, y)], fill=c1)
    for x in range(0, 16, 4):
        for y in range(0, 16, 3):
            px(img, x, y, c2)
            px(img, x+1, y-1, c2)
            px(img, x-1, y+1, c2)
    add_noise(img, 5)
    return img

# ============ OKOP TEXTURES ============
def gen_okop():
    base = "okop/src/main/resources/assets/warproject/textures/block"
    print("\n=== OKOP ===")
    save(solid((139, 119, 101, 255)), f"{base}/wooden_support_beam.png")  # brown wood
    save(solid((180, 180, 185, 255)), f"{base}/iron_support_beam.png")    # iron gray
    save(striped((160, 160, 170, 255), (120, 120, 130, 255)), f"{base}/reinforced_support_beam.png")
    save(solid((60, 90, 40, 255)), f"{base}/forest_camo_net.png")         # forest green
    save(solid((194, 178, 128, 255)), f"{base}/desert_camo_net.png")      # desert sand
    save(solid((220, 225, 230, 255)), f"{base}/winter_camo_net.png")      # snow white
    save(solid((160, 140, 100, 255)), f"{base}/sandbag.png")              # sand brown
    save(solid((180, 150, 100, 255)), f"{base}/sandbag_top.png")
    save(solid((139, 119, 80, 255)), f"{base}/wooden_horizontal_cover.png")
    save(solid((120, 90, 60, 255)), f"{base}/log_horizontal_cover.png")
    save(wire_pattern((100, 100, 100, 255), (70, 70, 70, 255)), f"{base}/barbed_wire.png")
    save(grid_pattern((100, 80, 60, 255), (70, 55, 40, 255), 4), f"{base}/drainage_grate.png")
    save(solid((120, 120, 120, 255)), f"{base}/firing_slot.png")          # stone gray
    save(solid((80, 80, 80, 255)), f"{base}/firing_slot_inner.png")       # dark interior
    save(solid((139, 119, 80, 255)), f"{base}/trench_stairs.png")
    save(solid((60, 60, 55, 255)), f"{base}/trench_lantern.png")
    save(solid((120, 95, 65, 255)), f"{base}/supply_crate.png")

# ============ POLEVOY TEXTURES ============
def gen_polevoy():
    base = "polevoy/src/main/resources/assets/polevoy/textures/block"
    print("\n=== POLEVOY ===")
    # Tents
    save(solid((110, 120, 90, 255)), f"{base}/small_tent.png")            # olive drab
    save(solid((80, 70, 50, 255)), f"{base}/tent_floor.png")              # dark ground
    save(solid((90, 100, 75, 255)), f"{base}/command_tent.png")           # darker olive
    save(cross_on((110, 120, 90, 255), (200, 40, 40, 255)), f"{base}/medical_tent.png")  # olive + red cross
    # Field kitchen
    save(solid((70, 75, 70, 255)), f"{base}/field_kitchen.png")           # dark metal
    save(grid_pattern((80, 85, 80, 255), (60, 65, 60, 255), 4), f"{base}/field_kitchen_top.png")
    save(solid((50, 50, 50, 255)), f"{base}/field_kitchen_pipe.png")      # chimney
    # First aid kit
    save(solid((200, 200, 190, 255)), f"{base}/first_aid_kit.png")        # white box
    save(cross_on((200, 200, 190, 255), (200, 30, 30, 255)), f"{base}/first_aid_kit_cross.png")
    # Radio
    save(solid((60, 70, 55, 255)), f"{base}/field_radio.png")             # military green
    save(solid((40, 40, 40, 255)), f"{base}/field_radio_antenna.png")     # black antenna
    # Spotlight
    save(solid((80, 80, 80, 255)), f"{base}/field_spotlight_tripod.png")
    save(solid((60, 60, 60, 255)), f"{base}/field_spotlight_lamp.png")
    save(solid((255, 255, 200, 255)), f"{base}/field_spotlight_lens.png") # bright lens
    # Generator
    save(solid((70, 75, 65, 255)), f"{base}/generator.png")               # army green
    save(grid_pattern((80, 85, 75, 255), (60, 65, 55, 255), 4), f"{base}/generator_top.png")
    save(solid((45, 45, 45, 255)), f"{base}/generator_exhaust.png")

# ============ BAZA TEXTURES ============
def gen_baza():
    base = "baza/src/main/resources/assets/baza/textures/block"
    print("\n=== BAZA ===")
    save(solid((140, 140, 135, 255)), f"{base}/military_concrete.png")    # gray concrete
    save(striped((150, 150, 145, 255), (110, 110, 115, 255), True, 3), f"{base}/reinforced_concrete.png")  # with rebar lines
    save(solid((160, 145, 110, 255)), f"{base}/hesco_barrier.png")        # sand/burlap
    save(solid((100, 100, 105, 255)), f"{base}/metal_gate.png")           # dark metal
    save(solid((90, 90, 95, 255)), f"{base}/checkpoint_barrier_post.png")
    save(solid((200, 50, 50, 255)), f"{base}/checkpoint_barrier_bar.png") # red/white bar
    save(solid((130, 120, 110, 255)), f"{base}/tank_hedgehog.png")        # rusty metal
    save(wire_pattern((120, 120, 125, 255), (80, 80, 85, 255)), f"{base}/razor_wire_fence.png")

if __name__ == "__main__":
    import random
    random.seed(42)  # reproducible textures
    print("War Project Texture Generator v2.0")
    print("====================================")
    gen_okop()
    gen_polevoy()
    gen_baza()
    print(f"\nDone! All textures generated.")
    print("Run from repo root: python tools/generate_textures.py")
