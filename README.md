# War Project

A unified military mod for Minecraft (NeoForge 1.21.1) — realistic trench fortification and military base blocks.

## Features

### 🏰 Fortification (Okop) — 15 blocks
- **Support Beams** — Wooden, Iron, Reinforced (structural support)
- **Camo Nets** — Forest, Desert, Winter (concealment)
- **Sandbags** — Stackable 1-4 layers
- **Horizontal Covers** — Wooden & Log (overhead protection)
- **Barbed Wire** — Slows and damages entities
- **Drainage Grate** — Waterloggable floor grate
- **Firing Slot** — Fortified wall with shooting aperture
- **Trench Stairs** — Stepped access block
- **Trench Lantern** — Dim lighting for trenches
- **Supply Crate** — Storage container (27 slots)

### 🏢 Military Base (Baza) — 8 blocks
- **Military Concrete** — Blast-resistant building material
- **Reinforced Concrete** — Even stronger variant
- **Concrete Slab** — Half-block variant
- **HESCO Barrier** — Blast-resistant defensive wall
- **Metal Gate** — Openable iron gate
- **Checkpoint Barrier** — Red/white boom barrier
- **Tank Hedgehog** — Anti-vehicle obstacle
- **Razor Wire Fence** — Damages and slows entities

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.77+
- Java 21

## Building

```bash
# Build the mod
./gradlew build

# Run client
./gradlew runClient
```

## Texture Generation

Placeholder block textures are committed under src/main/resources/assets/warproject/textures/block/.
To regenerate them (requires pillow):
```bash
python tools/generate_textures.py
```

## Crafting

All 23 blocks have vanilla crafting recipes using standard Minecraft materials.

## Structure

```
src/main/java/com/frostlogic/warproject/
├── WarProject.java          # Main mod class
├── ModBlocks.java           # All 23 block registrations
├── ModItems.java            # All 23 item registrations
├── ModBlockEntities.java    # Block entities (supply crate)
├── ModCreativeTab.java      # 2 creative tabs
└── block/                   # All block classes
```

## License

All Rights Reserved © FrostLogic Dev
