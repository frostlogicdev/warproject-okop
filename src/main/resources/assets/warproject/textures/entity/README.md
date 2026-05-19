# Entity Textures (placeholders)

This folder reserves resource pack paths for FactionNpcEntity textures
(see design §10 and §15, task 20.4).

## Required PNG files

The following 64×64 PNG textures must be supplied by an artist. Until they
are added, FactionNpcEntity falls back to the vanilla villager skin via the
client renderer.

| File                              | Faction       | Notes                                |
|-----------------------------------|---------------|--------------------------------------|
| `faction_npc_zarnavia.png`        | ZARNAVIA      | Recruiter NPC (Зарнавия) — UV 64×64  |
| `faction_npc_chernogryad.png`     | CHERNOGRYAD   | Recruiter NPC (Черногряд) — UV 64×64 |

## How the texture is selected

`FactionNpcEntity.getFactionId()` returns the faction stored in NBT
(`FactionId` key). The client renderer picks the texture by faction:

```
warproject:textures/entity/faction_npc_<factionSerializedName>.png
```

`FactionId#getSerializedName()` yields `"zarnavia"` or `"chernogryad"`.

## Status

- Paths reserved (this README).
- PNG binaries pending — non-blocking for compilation; rendering uses
  vanilla fallback until provided.

## Related models

- `assets/warproject/models/entity/faction_npc.json` — placeholder model
  declaration.
- `assets/warproject/models/item/passport.json` — existing item model
  (uses `minecraft:item/written_book` as parent).
