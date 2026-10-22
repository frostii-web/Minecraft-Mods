# Infinite Resource Generators

A Forge **1.20.1 / Java 17** mod for **GTCEu Modern 7.5.1**. Adds one inexpensive, configurable Resource Generator that produces selected resources without power, fuel, water, or inputs.

## Download

Download the compiled mod: [InfiniteResourceGenerators-1.0.0-forge-1.20.1.jar](releases/InfiniteResourceGenerators-1.0.0-forge-1.20.1.jar?raw=1)

SHA-256: `082ba55fd70e2ae8fae77900f487124a02fd11bec0fb8b409fef95c5fd9aa340`

## Installation

1. Close Minecraft (and the dedicated server, if applicable).
2. Copy `InfiniteResourceGenerators-1.0.0-forge-1.20.1.jar` into the pack's `mods` directory. On multiplayer, install the same JAR on both the server and every client.
3. Keep GTCEu Modern 7.5.1 and its normal dependencies installed. This JAR does not bundle or replace GTCEu, KubeJS, or JEI. Forge 47.3.0 or newer is required; development and server verification use 47.4.0.
4. Launch the pack and test in a separate world first. An existing world can use the mod without regenerating terrain.

Craft the generator in an ordinary crafting table:

| Iron ingot | Iron ingot | Iron ingot |
| --- | --- | --- |
| Copper ingot | Crafting table | Copper ingot |
| Empty | Copper ingot | Empty |

Iron and copper use Forge ingredient tags, so the pack's equivalents work. The block and its ordinary crafting recipe are visible to JEI without an extra JEI plugin.

## Use

Place the generator and right-click it. Search by translated item name or registry ID, scroll the list, and click any **Ready** resource to add it to the configuration. Click a selected resource again to remove it. Up to nine resources can be selected. Locked forms stay visible and show their unlock instructions on hover. Every configuration change is checked by the server.

Click the mode button to switch between **Round Robin** and **All at Once**. Round Robin produces one item from the next selected resource each configured generation cycle. All at Once produces one item from every selected resource each cycle; it pauses unless the complete batch fits, so it never generates only part of a batch. The generator has a configurable output inventory and consumes no input items. Server lag affects wall-clock speed. Changing the resource configuration retains existing output and restarts the generation timer. Inventory, selected resources, mode, and round-robin position save with the block entity.

Hoppers, GT item pipes/conveyors, and other standard Forge item handlers can extract from any side. Automation and the GUI cannot insert into its output. When automatic output is enabled, it offers items to the sided Forge item capability of adjacent inventories at the configured interval and transfer limit. Rejected items remain in the generator. Automation never loads neighboring chunks. Generators only operate while their chunk is ticking; there is no offline catch-up.

## Configuration

Forge creates `config/infiniteresourcegenerators-common.toml` after the mod starts once. On a dedicated server, edit the file in the server's `config` directory; server values control gameplay, and the server sends the effective slot count when a player opens the GUI.

| Setting | Default | Range | Behavior |
| --- | ---: | ---: | --- |
| `generationIntervalTicks` | 20 | 1–72000 | Ticks between generation cycles; 20 ticks is approximately one second. |
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

GameTests run in `run/gametest`. Use a fresh test world for the bronze progression test, which intentionally verifies bronze starts locked. Tests cover real 20-tick generation, round-robin and all-at-once multi-resource production, full output/resumption, hopper extraction, direct chest output, insertion rejection, NBT serialization, independent generators/selections, natural GT ore availability, prohibited items, and a powered GT LV alloy smelter producing bronze from copper and tin.

Outputs:

* `build/libs/InfiniteResourceGenerators-1.0.0-forge-1.20.1.jar`
* `build/distributions/InfiniteResourceGenerators-1.0.0-source.zip`

See `VERIFICATION.md` for checks actually performed and remaining client-side acceptance tests. Compilation alone does not establish successful gameplay.

## Scope

The GT hook is version-specific; the mod declares GTCEu 7.5.1 compatibility. Unusual ore generators that use neither GT vein definitions nor standard biome ore features are not automatically recognized. No custom JEI UI is provided. There is no new world generation, recipe modification, power network, or mandatory advancement chain.

## License

Project source and original assets are MIT licensed. Minecraft, Forge, GTCEu, and the Gradle wrapper retain their respective licenses. Minecraft/mod dependency binaries are not redistributed in the source ZIP or release JAR. The source ZIP includes the standard Gradle wrapper. See `THIRD_PARTY_NOTICES.md`.
