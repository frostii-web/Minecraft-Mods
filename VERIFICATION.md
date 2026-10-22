# Infinite Resource Generators 1.1.0 verification

Detailed logs and reusable test fixtures are included in the source ZIP under `verification/`.

## Environment

Java 17.0.17+10, Minecraft 1.20.1, Forge 47.4.0, GTCEu Modern 7.5.1,
KubeJS 2001.6.5-build.16, Rhino 2001.2.3-build.10, Architectury 9.2.14,
and JEI 15.20.0.127. Tests run on a headless Linux host in isolated worlds.

## Gradle build and gameplay tests

`./gradlew --offline build runGameTestServer -PcompatMods -x downloadAssets`
completed successfully with **all 22 required Forge GameTests passing**.
The existing Java 17 toolchain and dependency cache were used; no dependency
versions or TLS checks were relaxed. The test configuration uses the maximum
54 output slots, generation interval 20 ticks, output interval 5 ticks, and a
64-item global automatic-output budget.

| Test | Behavior verified |
| --- | --- |
| producesExactlyOnePerTwentyTicks | No early generation, exact interval and item. |
| fullInventoryPausesAndResumes | Full resource pauses and resumes when extraction makes room. |
| hopperExtractsRealOutput | Real vanilla hopper extracts through the DOWN capability. |
| adjacentChestReceivesOutput | Automatic output inserts real generated items into a chest. |
| autoOutputHonorsTransferLimitAndKeepsRejectedItems | Shared 64-item output limit; full destinations do not delete items. |
| automationRejectsInsertionAndSimulationIsSafe | All sides reject insertion; simulated extraction changes nothing. |
| nbtRoundTripPreservesSelectionAndOutput | Output and selections persist; occupied trailing slots survive capacity reduction; world unlock data persists. |
| sixIndependentSlotsIncludeDuplicateResources | All six rows generate simultaneously, duplicates work, active filters deduplicate, seventh/invalid rows are rejected. |
| fullResourceDoesNotBlockCompatibleOtherResource | Full iron does not block tin inserting into compatible space; paused iron resumes. |
| changingClearingAndDisablingPrunesFiltersPreservesLeftovers | Immediate timer reset, deduplication, live cached-handler filter changes, disabled rows, retained/retrievable leftovers, server rejects inactive filter IDs. |
| multipleFacesShareSupplyWithoutDuplication | Combined/custom routes and shared supply conserve counts; simulation, NONE, DISABLED, unsided access, and newly enabled ALL ACTIVE defaults. |
| extractOnlyNeverPushesAndAllActiveAdapts | EXTRACT_ONLY/DISABLED do not push; ALL ACTIVE follows resource changes. |
| versionedNbtAndLegacyMigrationPreserveConfiguration | Six-row settings/progress, disabled state, route/filter persistence; single-resource, nine-selection, and recognized face-save migration. |
| actualGtItemPipeReceivesPushedResources | A real connected GT brass item pipe carries both iron and tin to a chest. |
| actualGtConveyorExtractsOnlyPermittedResources | A real LV conveyor imports through an EXTRACT_ONLY face into a GT pipe; tin moves, filtered iron stays, counts are conserved. |
| migrationRecoveryRetainsOverflowAndMissingRegistryItems | Overflow items become retrievable; missing-registration NBT survives reload; empty rows stay empty. |
| partialShiftClickRetrievesLeftoversAndMarksStorageDirty | Partial shift-click into a nearly full inventory conserves leftovers and marks the chunk for saving without adding a generation selection. |
| capabilityLifecycleAndStackSnapshotsStaySafe | Returned stacks cannot mutate storage; capability invalidation/revival preserves supply. |
| multipleGeneratorsChangeSelectionIndependently | Independent blocks and preservation of previously generated output on selection change. |
| naturalGtOresAndProhibitedItems | Existing world-generation eligibility and prohibited-item rules. |
| survivalRecipeAndServerMenuAreRegistered | Original survival recipe and output-only menu slots. |
| actualGtAlloySmelterUnlocksOnlyBronzeIngots | Real completed GT recipe unlocks bronze ingots, while plates remain locked. |

Gradle's ordinary `test` task has no JUnit sources; it is not counted as a
successful gameplay test. The actual Forge result is in
`verification/gradle-gametest.log`.

## Exact 3D face picking

`verification/CubeGeometryCheck.java` runs without Minecraft and passed **6,006
face assertions**, including head-on world directions, 2,000 deterministic
rotations, visible-face centroid picking, background rejection, and reachability
of all six faces. Rendering and picking share the same projected polygons.
This checks geometry and direction mapping; it does not claim a graphical client
rendering test.

Reproduce with Java 17:

```sh
mkdir -p build/geometry-check
javac -d build/geometry-check src/main/java/dev/frostii/irg/client/CubeGeometry.java verification/CubeGeometryCheck.java
java -cp build/geometry-check CubeGeometryCheck
```

## Release JAR

The release is reobfuscated by ForgeGradle. ZIP integrity, all JSON resources,
manifest/version, Forge mod metadata, original recipe, mixin registration, and
new generation/routing/GUI classes are checked. All **37 class files** have Java
17 class-file major version 61. The JAR bundles no Minecraft or GTCEu classes.
The SHA-256 is supplied with the deliverables.

## Release-server migration and restart

A separate production Forge installation loads the exact compiled release JAR
with GTCEu 7.5.1 and the same compatibility mods. The test copies the earlier
1.0.0 smoke-test world; the original world is not modified. The first release
run reaches `Done`, migrates the existing generator and its saved iron output,
configures independent iron/tin/copper rows (including an enabled duplicate and
a disabled duplicate), and transfers all three resources through one ALL ACTIVE
PUSH face into a real chest. A second face stays EXTRACT_ONLY with a two-resource
CUSTOM filter; its adjacent chest stays empty. Inactive cobblestone leftovers
remain in shared storage. The server logs
`IRG110_RELEASE_MIGRATION_AND_ROUTING_PASSED` and saves/stops cleanly.

The restart fixture checks saved generation selections, disabled state, an empty
row remaining empty, exactly three active filter resources, face modes, the
CUSTOM filter, retained leftovers, and resumed independent production. It logged
`IRG110_RELEASE_RESTART_PASSED` and stopped cleanly with exit code 0. The
fixture is in `verification/ReleaseSmoke.js`; it is isolated test code and is not
included in the mod JAR. Startup/restart logs are included in `verification/`.

## Limitations

No graphical client session is available on this host. Opening and visually
checking the GUI, mouse interaction in a live client/server connection, textures,
and JEI display remain unperformed. Geometry was tested independently; GUI code
was compiled. The interface uses a 420 by 244 GUI-unit layout; reduce Minecraft's
GUI scale if the available scaled viewport is smaller.

The user's complete modpack was not supplied. Integration tests use the exact
GTCEu version and the compatibility mods listed above. Recognized experimental
six-face NBT schemas are covered with a fixture; arbitrary earlier schemas cannot
be guaranteed without their saves. See README migration details, including
archived extra selections, retained legacy configuration, and recovery storage.

The upstream dependency stack logs nonfatal Rhino client-reflection warnings on
a dedicated server and failed external authentication/update lookups. These
messages also occurred in the existing baseline; gameplay tests completed with
exit code 0. Test servers use loopback-only networking and are stopped after use.
