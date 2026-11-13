# Helpful Villagers (1.12.2 reimplementation)

Put your lazy villagers to work: lumberjacks, farmers, miners, soldiers, ranchers, fishermen and more, with village halls, a craft queue and a village economy.
For **Minecraft 1.12.2 on Cleanroom Loader**. Requires [ArchaicLib](https://github.com/ElectrifiedDed/ArchaicLib) 1.4.0+.

> **This is not a fork and not a re-upload.** The idea comes from the original *Helpful Villagers* by **WeaselNinja** (Minecraft 1.7.10), but **all the code in this repository is written from scratch**. It works almost like a "layer" on top of the original mod: it contains **none of the original code or assets**, and for the game to run and for the textures to appear you must provide the original mod yourself.

## Original mod (required for textures and models)

**Original: [Helpful Villagers on CurseForge](https://www.curseforge.com/minecraft/mc-mods/helpful-villagers)** — the 1.7.10 version, file `helpfulvillagers-1.7.10-1.3.1.jar`.

1. Download the original 1.7.10 jar from the link above.
2. Put it in the folder `<minecraft folder>/archaic_originals/` (create it if it does not exist). **Not** in `mods/` — a 1.7.10 jar cannot be loaded as a mod in 1.12.2.
3. Start the game. ArchaicLib reads the textures straight from that jar; nothing is extracted or copied.

If the jar is missing, the game shows a window on the main menu with the link and the exact folder, and villagers fall back to default skins.

## Installation

1. Minecraft 1.12.2 + [Cleanroom Loader](https://github.com/CleanroomMC/Cleanroom) (Java 21+).
2. Put `archaiclib-1.4.0.jar` and `helpfulvillagers-1.0.0.jar` into `mods/`.
3. Put the original jar into `archaic_originals/` (see above).

## Features

- One villager entity with a synced profession: lumberjack, farmer, miner, soldier, archer, rancher, fisherman, builder-style crafters and more.
- **Halls**: an item frame with the job symbol next to a wooden door of a closed room marks a workplace.
- **Craft queue**: order items and the right profession crafts and smelts them (recursive planner, village furnace fuel), delivering the result.
- **Economy**: villager accounts, barter buy/sell, tick-sliced world survey (no lag spikes).
- Soldiers fight and patrol, ranchers herd animals into pastures, fishermen catch loot, farmers harvest and replant.
- Server-side validated network messages; admin command `/hvdebug`.

## Building

Needs a sibling folder `../ArchaicLib` (clone of [ArchaicLib](https://github.com/ElectrifiedDed/ArchaicLib)) built beforehand.

```
cd ../ArchaicLib && ./gradlew build
cd ../HelpfulVillagers && ./gradlew build
```

## License

HardEvolution Restricted Source-Available License (HE-RSAL) 1.1 — see [LICENSE.en.md](LICENSE.en.md) and [LICENSE.ru.md](LICENSE.ru.md) (the Russian version prevails).
The original Helpful Villagers belongs to its author, WeaselNinja; this project neither contains nor modifies it.

Author: **electrifiedded** / [HardEvolution](https://github.com/HardEvolution-team) · [Discord](https://discord.gg/92JTPmfGSx)
