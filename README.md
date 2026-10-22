# Infinite Resource Generators

A Forge **1.20.1 / Java 17** mod for **GTCEu Modern 7.5.1**. Adds one inexpensive, configurable Resource Generator that produces selected resources without power, fuel, water, or inputs.

## Download

Download the compiled mod: [InfiniteResourceGenerators-1.1.0-forge-1.20.1.jar](releases/InfiniteResourceGenerators-1.1.0-forge-1.20.1.jar?raw=1)

Download the source archive: [InfiniteResourceGenerators-1.1.0-source.zip](releases/InfiniteResourceGenerators-1.1.0-source.zip?raw=1)

The release SHA-256 is provided in `SHA256SUMS`; both downloads are covered by `releases/SHA256SUMS`.

## Installation

1. Close Minecraft (and the dedicated server, if applicable).
2. Replace the existing Infinite Resource Generators JAR in the pack's `mods` directory with `InfiniteResourceGenerators-1.1.0-forge-1.20.1.jar`; keep only one version installed. On multiplayer, use the same JAR on both the server and every client.
3. Keep GTCEu Modern 7.5.1 and its normal dependencies installed. This JAR does not bundle or replace GTCEu, KubeJS, or JEI. Forge 47.3.0 or newer is required; development and server verification use 47.4.0.
4. Launch the pack and test in a separate world first. An existing world can use the mod without regenerating terrain.

Craft the generator in an ordinary crafting table:

| Iron ingot | Iron ingot | Iron ingot |
| --- | --- | --- |
| Copper ingot | Crafting table | Copper ingot |
| Empty | Copper ingot | Empty |

Iron and copper use Forge ingredient tags, so the pack's equivalents work. The block and its ordinary crafting recipe are visible to JEI without an extra JEI plugin.

## Use

Place the generator and right-click it. The GUI has **Generation**, **Output Routing**, and **Stored Items** tabs. All configuration changes are checked and confirmed by the server.

### Generation

Select one of six numbered generation rows, search by item name or registry ID, and click an available resource to assign it to that row. Locked resources remain visible with unlock instructions. Each row has an **ON/OFF** toggle, an **X** clear button, its own progress bar, and a status. Duplicate resources are allowed. Rows have no relationship to block faces.

Each enabled row produces one item per `generationIntervalTicks`. A row with insufficient compatible storage pauses at its completed cycle and resumes when space is freed; other rows continue independently. Changing, clearing, or enabling/disabling a row resets that row's progress and preserves every previously produced item. There is no power or fuel requirement, and no offline catch-up when chunks are not ticking.

### Output Routing

Drag the textured 3D generator to rotate it, then click a visible face. Face picking uses the actual projected world-direction polygons. Labels identify North, South, East, West, Up, and Down, regardless of the current view; the selected face has a white outline. Rotation and selection stay available without closing the GUI.

Click the selected face's mode to cycle **PUSH** (green), **EXTRACT_ONLY** (yellow), and **DISABLED** (gray). PUSH both automatically transfers to adjacent sided Forge inventories and allows extraction. EXTRACT_ONLY allows external extraction without pushing. DISABLED exposes no items. Newly enabled faces default to ALL ACTIVE. Existing/new blocks initially use PUSH / ALL ACTIVE on all six faces to preserve the original automation behavior.

Each face supports **ALL ACTIVE**, **CUSTOM**, or **NONE**. ALL ACTIVE adapts to enabled, configured generation rows. CUSTOM offers a checklist of item icons and names containing only those active resources, with duplicates removed. Clicking a checklist row switches to CUSTOM and toggles that resource. NONE allows no resources. A routing status reports disabled, no permissions, waiting/blocked, ready, or extraction-only; a compact summary uses P/E for push/extract and */count for all/custom resources.

Several resources can share one face, and several faces can share a resource. Every face uses the same real storage. The automatic-output budget applies across all faces together, with rotating face priority; rejected items remain stored. Cached Forge handlers recheck current permissions and reject insertion. Unsided capability access cannot bypass the filters. GT conveyors are attached to an adjacent GT pipe/machine with the conveyor set to import from the generator; this Forge block does not itself accept GT covers.

Clearing, changing, or disabling the last generation row for a resource immediately removes it from selectable output options and from CUSTOM permissions. Re-enabling that resource does not restore stale CUSTOM checks. Its existing items remain in storage and do not leave automatically through filters that no longer permit them.

### Stored Items

Open Stored Items to take or shift-click output into your inventory, including leftovers whose generation selection has been removed. The shared output inventory accepts no player or automation insertion. A paused resource does not block another resource with compatible space.

### Save migration

Block-entity data is versioned (`DataVersion = 2`). Existing 1.0.0 `Selected`/`Selections` entries become independent enabled rows in their saved order; stored stacks and partial progress are retained. Round Robin and atomic All at Once timing become independent per-row timing. The first six of an older nine-resource selection remain configured; additional IDs are archived in `LegacySelections` and may be reassigned manually in the searchable picker. The complete original configuration is retained under `LegacyConfiguration`.

Recognized experimental face layouts (`Faces` or `Sides` lists, with `Direction`/`Side`, `Resource`/`Selected`, and `Mode`) migrate selections into independent rows and turn each old face resource into a CUSTOM filter. Recognized per-face `Output` inventories join shared storage. No earlier six-face source or save fixture was supplied; other experimental schemas may require a dedicated adapter, and their raw original configuration remains retained for recovery. Unknown face modes become DISABLED.

Occupied trailing inventory slots survive reductions of the TOML capacity. Items beyond the supported 54 visible slots are retained in a saved recovery queue and move into the GUI inventory as room becomes available; breaking the generator also drops that queue. Serialized stacks belonging to missing item registrations remain in `UnresolvedItems` and are retried on future loads. Migration never converts one resource into another.

## Configuration

Forge creates `config/infiniteresourcegenerators-common.toml` after the mod starts once. On a dedicated server, edit the file in the server's `config` directory; server values control gameplay, and the server sends the effective slot count when a player opens the GUI.

| Setting | Default | Range | Behavior |
| --- | ---: | ---: | --- |
| `generationIntervalTicks` | 20 | 1–72000 | Ticks per independent generation row; 20 ticks is approximately one second. |
| `outputInventorySlots` | 9 | 1–54 | Number of generator output slots. |
| `autoOutputEnabled` | true | true/false | Push output into adjacent sided Forge item handlers. Pipes and hoppers can still extract when disabled. |
| `autoOutputIntervalTicks` | 5 | 1–1200 | Ticks between automatic output attempts. |
| `autoOutputMaxItems` | 64 | 1–64 | Maximum total items transferred during one automatic output attempt. |

`outputInventorySlots` requires a client/server restart. The other settings are read while the game runs and take effect after Forge reloads the saved COMMON configuration; restart if the host does not watch configuration changes.

Reducing the slot count never deletes stored items. If an existing generator has items in slots beyond the new limit, that generator retains enough slots to hold them. Empty the trailing slots, save the world, and restart again to let it shrink fully. Increasing the setting expands existing generators on their next load.

## Eligibility and progression

* **Immediately available:** ores found in active GTCEu ore-vein definitions, including registered host-rock variants of naturally generated materials, and ordinary ore blocks in the world's biome-referenced ore features. GT eligibility checks density, cluster size, biome weight, and existing dimensions; merely registering an ore material does not make it available. Pack changes are read after KubeJS/datapack loading and `/reload`.
* **Locked until obtained/produced:** raw ores, ingots, nuggets, ordinary gem forms, dust forms, crushed ores, plates, foils, rods, bolts, screws, rings, springs, fine wires, and GT bare wire sizes. Each registry item unlocks independently. Bronze ingots never unlock bronze plates or rods.
* **Unlock sources:** survival crafting, furnace-result collection, successful item pickup, and actual non-simulated GT machine output insertions. The GT hook observes inserted items after chance rolls, including unattended machine production. It does not unlock hypothetical recipe outputs or scan arbitrary player inventories. Creative-player crafting/pickup does not unlock forms.
* **Always excluded:** machine blocks, the resource generator itself, motors, pumps, conveyors, circuits, tools, armor, batteries, insulated cables, storage blocks, functional components, and arbitrary NBT-bearing stacks. Material-prefix and passive-item-class allowlists apply before unlocks; an unlock alone cannot authorize an otherwise ineligible item. No NBT is copied into newly generated items.

Unlocks are permanent and shared by all players and generators in all dimensions of the same world. They are stored in the overworld's `data/infiniteresourcegenerators_unlocks.dat`. Existing items sitting in a chest are not automatically unlocked: use them in production, craft them, collect a furnace result, or drop and pick up an eligible plain item in Survival. Non-GT machines without an applicable production event can use the pickup route. Other mods/admin commands can create plain items; this mod does not attempt to prove their historical provenance.

## Build and tests

Install a Java 17 JDK, set `JAVA_HOME` if necessary, then use the included, checksum-pinned Gradle wrapper:

```sh
./gradlew build sourceZip
./gradlew runGameTestServer -PcompatMods -x downloadAssets
./gradlew runClient -PcompatMods
```

On Windows use `gradlew.bat`. ForgeGradle **6.0.36**, Gradle **8.8**, Forge **47.4.0**, and Mojang's **1.20.1** mappings are pinned. The project follows the official Forge 47.4.0 MDK toolchain. GTCEu resolves from its official Maven repository and is deobfuscated for development. Runtime-only compatibility mods are enabled by `-PcompatMods`: KubeJS **2001.6.5-build.16**, Rhino **2001.2.3-build.10**, Architectury **9.2.14**, and JEI **15.20.0.127**. They are not included in the generated mod JAR.

The headless server does not use client sound/image assets, so its command excludes only `downloadAssets`. Client launches download and verify those assets normally. Development runs enable Mixin refmap remapping for the installed dependencies; the production JAR registers its own mixin through its manifest.

GameTests run in `run/gametest`. Use a fresh test world for the bronze progression test, which intentionally verifies bronze starts locked. Tests cover real 20-tick generation, six independent rows and duplicates, per-resource storage pauses, filtered shared extraction, live filter pruning, leftovers, capability lifecycle/simulation, output modes, NBT migration, hopper/chest output, real GT item pipes/conveyors, eligibility, and a powered GT LV alloy smelter producing bronze. `verification/CubeGeometryCheck.java` separately checks exact face picking and all six world directions.

Outputs:

* `build/libs/InfiniteResourceGenerators-1.1.0-forge-1.20.1.jar`
* `build/distributions/InfiniteResourceGenerators-1.1.0-source.zip`

See `VERIFICATION.md` for checks actually performed and remaining client-side acceptance tests. Compilation alone does not establish successful gameplay.

## Scope

The GT hook is version-specific; the mod declares GTCEu 7.5.1 compatibility. Unusual ore generators that use neither GT vein definitions nor standard biome ore features are not automatically recognized. No custom JEI UI is provided. There is no new world generation, recipe modification, power network, or mandatory advancement chain.

## License

Project source and original assets are MIT licensed. Minecraft, Forge, GTCEu, and the Gradle wrapper retain their respective licenses. Minecraft/mod dependency binaries are not redistributed in the source ZIP or release JAR. The source ZIP includes the standard Gradle wrapper. See `THIRD_PARTY_NOTICES.md`.
