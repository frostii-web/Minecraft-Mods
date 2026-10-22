# Verification

## Tested environment

* Java: Temurin JDK 17.0.17+10 (download SHA-256 verified).
* Forge: 1.20.1-47.4.0, using the official MDK's Java 17 / Gradle 8.8 toolchain and ForgeGradle 6.0.36.
* GTCEu Modern: 7.5.1, official published runtime and embedded libraries.
* KubeJS: 2001.6.5-build.16; Rhino: 2001.2.3-build.10; Architectury: 9.2.14.
* JEI: 15.20.0.127.
* Linux x86-64, headless. Tests use isolated worlds, not a supplied copy of the full Community Pack.

## Completed automated checks

`./gradlew runGameTestServer -PcompatMods -x downloadAssets` completed with **exit code 0** and **all 11 required GameTests passed** after the multi-output implementation. Compilation and resource processing succeeded. Gradle's ordinary `test` task has no JUnit source; the actual gameplay tests run in Forge's GameTest server. No zero-test run is counted as validation.

| GameTest | Verified behavior |
| --- | --- |
| `producesExactlyOnePerTwentyTicks` | No early item at tick 19; one at 20; two at 40; correct iron ore item. |
| `fullInventoryPausesAndResumes` | Nine full stacks pause production; capability extraction frees space; production resumes; no loose item entities. |
| `hopperExtractsRealOutput` | A real vanilla hopper receives generated ore. |
| `adjacentChestReceivesOutput` | An adjacent chest receives exactly two generated items after 40 ticks. |
| `automationRejectsInsertionAndSimulationIsSafe` | All six sides reject insertion; simulated extraction leaves inventory unchanged. |
| `nbtRoundTripPreservesSelectionAndOutput` | Selected item, output count, and independent per-item unlock data survive serialization. |
| `multipleSelectionsSupportBothProductionModes` | Round Robin produces one configured resource per cycle in order; All at Once produces every configured resource in one cycle; selections and mode survive NBT reload. |
| `multipleGeneratorsChangeSelectionIndependently` | Two generators produce different selected ores; switching one retains old output and leaves the other unchanged. |
| `naturalGtOresAndProhibitedItems` | GT iron/copper/tin/coal/redstone available immediately; registered non-worldgen cobalt ore denied; machines, motors, circuits, tools, storage, and NBT-bearing material items rejected. |
| `survivalRecipeAndServerMenuAreRegistered` | The documented shaped recipe matches and returns the registered block; server menu has 9 output + 36 player slots and rejects output insertion. |
| `actualGtAlloySmelterUnlocksOnlyBronzeIngots` | A powered GT LV alloy smelter consumes copper and tin and produces bronze; ingots start locked and unlock after actual output; bronze plates remain locked. |

The completed test log is included in `verification/gradle-gametest.log`.

## Production JAR checks

The compiled, reobfuscated release JAR was loaded by a separately installed production Forge dedicated server with the exact mod versions above. This exercised the release artifact, rather than only development classes. The server reached `Done` and built a catalog containing 1,639 naturally available ore items.

A generator placed by a server command selected `gtceu:iron_ore` and produced 36 ore items over 36 seconds. The server saved and stopped normally. On restarting, its selected resource and all 36 saved items were present and generation resumed.

An isolated KubeJS verification script then supplied a real GT LV alloy smelter with three copper ingots, one tin ingot, and power. The machine produced four bronze ingots. The resulting world SavedData on disk contains `gtceu:bronze_ingot` and excludes `gtceu:bronze_plate`. A further restart verified both the generator's retained output/selection and that the bronze ingot unlock persisted while the bronze plate remained locked. The log records `IRG_GENERATOR_RESTART_PASSED` and `IRG_UNLOCK_RESTART_PASSED`.

JAR inspection confirmed:

* 28 real Java 17 class files (`CAFEBABE`, class-file major version 61).
* Forge `mods.toml`, manifest Mixin registration, and the required GT output mixin.
* Block/item models, blockstate, language file, three PNG textures, shaped recipe, block loot table, mining tags, and recipe advancement.
* All JSON resources parse successfully; no Minecraft or GTCEu classes are bundled.

The reusable cloud install script was also executed successfully. It selects JDK 17, preserves TLS/checksum verification, refreshes environment-local proxy configuration without storing credentials, and builds the JAR/source ZIP.

## Checks still requiring a graphical client or the full pack

* Opening, rendering, and interacting with the searchable client GUI in singleplayer/multiplayer.
* Visual confirmation of textures, JEI listing, and JEI recipe display. The recipe and registration were checked on the server; JEI was loaded at its requested version.
* Extraction by an actual GT conveyor/item-pipe arrangement. Standard Forge extraction and real hopper/chest automation passed.
* Compatibility with the user's complete Community Pack configuration, custom KubeJS scripts, or additional mods. No `mods.zip` was supplied.

No graphical client session ran because this machine has no active display. These checks are **unperformed**, not passed. Test in the separate world you planned before using a valuable save.

## Runtime observations

The dependency stack logs nonfatal Rhino reflection warnings while inspecting client-only methods of GT/LDLib on a dedicated server, and unavailable Mojang authentication/background service requests in the isolated offline test. The server starts, recipes execute, and the tests complete despite those upstream messages. No authentication was needed for the local tests. The production test server binds only to loopback and is stopped after validation; its offline-mode setting is not included in the mod or installation instructions.

This verifies the current machine and release artifact. Saved environment configuration is separate from publishing an environment snapshot or validating restoration in a new task.
