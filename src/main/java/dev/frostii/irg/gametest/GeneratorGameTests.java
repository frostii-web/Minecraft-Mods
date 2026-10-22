package dev.frostii.irg.gametest;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import dev.frostii.irg.InfiniteResourceGenerators;
import dev.frostii.irg.block.GeneratorBlockEntity;
import dev.frostii.irg.resource.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(InfiniteResourceGenerators.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GeneratorGameTests {
    private static final BlockPos POS = new BlockPos(2, 2, 2);

    private static GeneratorBlockEntity place(GameTestHelper test, BlockPos pos) {
        test.setBlock(pos, InfiniteResourceGenerators.GENERATOR.get());
        return (GeneratorBlockEntity) test.getBlockEntity(pos);
    }
    private static ResourceLocation id(ItemStack stack) { return ForgeRegistries.ITEMS.getKey(stack.getItem()); }
    private static ItemStack iron() { return ChemicalHelper.get(TagPrefix.ore, GTMaterials.Iron); }
    private static int total(GeneratorBlockEntity generator) {
        int count = 0;
        for (int i = 0; i < generator.output().getSlots(); i++) count += generator.output().getStackInSlot(i).getCount();
        return count;
    }

    @GameTest(template = "empty", timeoutTicks = 50)
    public static void producesExactlyOnePerTwentyTicks(GameTestHelper test) {
        GeneratorBlockEntity generator = place(test, POS);
        test.assertTrue(generator.select(id(iron())), "Naturally generated iron ore must be immediately selectable");
        test.runAfterDelay(19, () -> test.assertTrue(total(generator) == 0, "Generated before 20 ticks"));
        test.runAfterDelay(20, () -> test.assertTrue(total(generator) == 1, "Expected exactly one item at 20 ticks"));
        test.runAfterDelay(40, () -> {
            test.assertTrue(total(generator) == 2, "Expected two items at 40 ticks");
            test.assertTrue(generator.output().getStackInSlot(0).is(iron().getItem()), "Produced a different item");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void fullInventoryPausesAndResumes(GameTestHelper test) {
        GeneratorBlockEntity generator = place(test, POS);
        test.assertTrue(generator.select(id(iron())), "Iron is not selectable");
        for (int slot = 0; slot < generator.outputSlots(); slot++) generator.output().setStackInSlot(slot, iron().copyWithCount(64));
        test.runAfterDelay(21, () -> {
            test.assertTrue(total(generator) == generator.outputSlots() * 64 && generator.status() == 2, "Full generator did not pause");
            var capability = generator.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.DOWN).orElseThrow(IllegalStateException::new);
            test.assertTrue(capability.extractItem(0, 1, false).getCount() == 1, "Cannot extract output");
        });
        test.runAfterDelay(42, () -> {
            test.assertTrue(total(generator) == generator.outputSlots() * 64 && generator.status() == 1, "Generator did not resume after extraction");
            test.assertEntityNotPresent(EntityType.ITEM);
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void hopperExtractsRealOutput(GameTestHelper test) {
        test.setBlock(POS.below(), Blocks.HOPPER);
        GeneratorBlockEntity generator = place(test, POS);
        test.assertTrue(generator.select(id(iron())), "Iron is not selectable");
        test.runAfterDelay(45, () -> {
            var hopper = (HopperBlockEntity) test.getBlockEntity(POS.below());
            boolean found = false;
            for (int i = 0; i < hopper.getContainerSize(); i++) if (hopper.getItem(i).is(iron().getItem())) found = true;
            test.assertTrue(found, "Vanilla hopper did not pull the generated ore through the Forge capability");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void adjacentChestReceivesOutput(GameTestHelper test) {
        test.setBlock(POS.east(), Blocks.CHEST);
        var generator = place(test, POS);
        test.assertTrue(generator.select(id(iron())), "Iron is not selectable");
        test.runAfterDelay(45, () -> {
            var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) test.getBlockEntity(POS.east());
            test.assertTrue(chest.getItem(0).is(iron().getItem()) && chest.getItem(0).getCount() == 2, "Adjacent chest did not receive exactly two generated items");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20)
    public static void autoOutputHonorsTransferLimitAndKeepsRejectedItems(GameTestHelper test) {
        test.setBlock(POS.east(), Blocks.CHEST);
        var chest = (net.minecraft.world.level.block.entity.ChestBlockEntity) test.getBlockEntity(POS.east());
        for (int slot = 0; slot < chest.getContainerSize() - 1; slot++) chest.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        var generator = place(test, POS);
        generator.output().setStackInSlot(0, iron().copyWithCount(64));
        generator.output().setStackInSlot(1, iron().copyWithCount(64));
        test.runAfterDelay(6, () -> {
            test.assertTrue(chest.getItem(chest.getContainerSize() - 1).getCount() == 64 && total(generator) == 64,
                    "Automatic output did not respect the configured 64-item transfer limit");
        });
        test.runAfterDelay(12, () -> {
            test.assertTrue(total(generator) == 64, "Items were deleted when the adjacent inventory rejected insertion");
            test.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void automationRejectsInsertionAndSimulationIsSafe(GameTestHelper test) {
        GeneratorBlockEntity generator = place(test, POS);
        generator.output().setStackInSlot(0, iron().copyWithCount(3));
        for (Direction side : Direction.values()) {
            IItemHandler handler = generator.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(IllegalStateException::new);
            test.assertTrue(handler.insertItem(0, new ItemStack(Items.DIAMOND, 7), false).getCount() == 7, "Automation inserted into output");
            test.assertTrue(handler.extractItem(0, 1, true).getCount() == 1 && total(generator) == 3, "Simulation mutated inventory");
            test.assertFalse(handler.isItemValid(0, new ItemStack(Items.IRON_INGOT)), "Output advertises insertable slots");
        }
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void nbtRoundTripPreservesSelectionAndOutput(GameTestHelper test) {
        GeneratorBlockEntity generator = place(test, POS);
        test.assertTrue(generator.select(id(iron())), "Iron is not selectable");
        generator.output().setStackInSlot(0, iron().copyWithCount(17));
        CompoundTag saved = generator.saveWithFullMetadata();
        var restored = new GeneratorBlockEntity(generator.getBlockPos(), generator.getBlockState());
        restored.load(saved);
        test.assertTrue(id(iron()).equals(restored.selected()) && total(restored) == 17, "Generator data did not survive NBT reload");
        var oversized = new net.minecraftforge.items.ItemStackHandler(12);
        oversized.setStackInSlot(11, iron().copyWithCount(5));
        CompoundTag migration = new CompoundTag();
        migration.put("Output", oversized.serializeNBT());
        var migrated = new GeneratorBlockEntity(generator.getBlockPos(), generator.getBlockState());
        migrated.load(migration);
        test.assertTrue(migrated.outputSlots() >= 12 && migrated.output().getStackInSlot(11).getCount() == 5,
                "Shrinking the configured inventory deleted an occupied trailing slot");
        WorldUnlocks data = new WorldUnlocks();
        var ingot = id(ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Bronze));
        var plate = id(ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze));
        data.unlock(ingot);
        WorldUnlocks loaded = WorldUnlocks.load(data.save(new CompoundTag()));
        test.assertTrue(loaded.contains(ingot) && !loaded.contains(plate), "Unlock save data lost independent item forms");
        test.assertTrue(WorldUnlocks.get(test.getLevel()) == WorldUnlocks.get(test.getLevel().getServer().overworld()), "Unlocks are not shared through the overworld");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 70)
    public static void multipleSelectionsSupportBothProductionModes(GameTestHelper test) {
        ItemStack tin = ChemicalHelper.get(TagPrefix.ore, GTMaterials.Tin);
        var roundRobin = place(test, new BlockPos(1, 2, 2));
        var allAtOnce = place(test, new BlockPos(3, 2, 2));
        var atomicBatch = place(test, new BlockPos(2, 2, 3));
        test.assertTrue(roundRobin.toggleSelection(id(iron())) && roundRobin.toggleSelection(id(tin)), "Round-robin selections rejected");
        test.assertTrue(allAtOnce.toggleSelection(id(iron())) && allAtOnce.toggleSelection(id(tin)), "All-at-once selections rejected");
        allAtOnce.setMode(GeneratorBlockEntity.Mode.ALL_AT_ONCE);
        test.assertTrue(atomicBatch.toggleSelection(id(iron())) && atomicBatch.toggleSelection(id(tin)), "Atomic-batch selections rejected");
        atomicBatch.setMode(GeneratorBlockEntity.Mode.ALL_AT_ONCE);
        for (int slot = 0; slot < atomicBatch.outputSlots() - 1; slot++) atomicBatch.output().setStackInSlot(slot, iron().copyWithCount(64));
        atomicBatch.output().setStackInSlot(atomicBatch.outputSlots() - 1, iron().copyWithCount(63));
        test.runAfterDelay(21, () -> {
            test.assertTrue(total(roundRobin) == 1 && roundRobin.output().getStackInSlot(0).is(iron().getItem()), "Round robin did not produce its first resource only");
            test.assertTrue(total(allAtOnce) == 2 && allAtOnce.output().getStackInSlot(0).is(iron().getItem())
                    && allAtOnce.output().getStackInSlot(1).is(tin.getItem()), "All-at-once did not produce one of every selected resource");
            test.assertTrue(total(atomicBatch) == atomicBatch.outputSlots() * 64 - 1
                            && atomicBatch.output().getStackInSlot(atomicBatch.outputSlots() - 1).getCount() == 63,
                    "All-at-once partially generated a batch that could not completely fit");
            for (int slot = 0; slot < atomicBatch.outputSlots(); slot++) atomicBatch.output().setStackInSlot(slot, ItemStack.EMPTY);
        });
        test.runAfterDelay(41, () -> {
            test.assertTrue(total(roundRobin) == 2 && roundRobin.output().getStackInSlot(1).is(tin.getItem()), "Round robin did not advance to its next resource");
            CompoundTag saved = allAtOnce.saveWithFullMetadata();
            var restored = new GeneratorBlockEntity(allAtOnce.getBlockPos(), allAtOnce.getBlockState());
            restored.load(saved);
            test.assertTrue(restored.selections().size() == 2 && restored.mode() == GeneratorBlockEntity.Mode.ALL_AT_ONCE,
                    "Multiple selections or mode did not survive NBT reload");
            test.assertTrue(total(atomicBatch) == 2, "All-at-once did not resume with a complete batch after space became available");
            test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void multipleGeneratorsChangeSelectionIndependently(GameTestHelper test) {
        var a = place(test, new BlockPos(1, 2, 2));
        var b = place(test, new BlockPos(3, 2, 2));
        ItemStack tin = ChemicalHelper.get(TagPrefix.ore, GTMaterials.Tin);
        test.assertTrue(a.select(id(iron())) && b.select(id(tin)), "Ore selections rejected");
        test.runAfterDelay(21, () -> {
            test.assertTrue(a.output().getStackInSlot(0).is(iron().getItem()) && b.output().getStackInSlot(0).is(tin.getItem()), "Generators interfered");
            test.assertTrue(a.select(id(tin)), "Changing selection rejected");
        });
        test.runAfterDelay(43, () -> {
            test.assertTrue(a.output().getStackInSlot(0).is(iron().getItem()) && a.output().getStackInSlot(1).is(tin.getItem()), "Changing selection destroyed old output or produced wrong ore");
            test.assertTrue(b.output().getStackInSlot(0).is(tin.getItem()) && total(b) == 2, "Second generator changed unexpectedly");
            test.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void naturalGtOresAndProhibitedItems(GameTestHelper test) {
        for (var material : new com.gregtechceu.gtceu.api.data.chemical.material.Material[] {GTMaterials.Iron, GTMaterials.Copper, GTMaterials.Tin, GTMaterials.Coal, GTMaterials.Redstone}) {
            var ore = ChemicalHelper.get(TagPrefix.ore, material);
            test.assertFalse(ore.isEmpty(), "Required GT ore is missing: " + material.getName());
            test.assertTrue(ResourceCatalog.isAvailable(test.getLevel(), id(ore)), "Natural ore is locked: " + id(ore));
        }
        var ungeneratedOre = ChemicalHelper.get(TagPrefix.ore, GTMaterials.Cobalt);
        test.assertFalse(ungeneratedOre.isEmpty(), "Expected GT's registered but non-worldgen cobalt ore");
        test.assertTrue(ResourceCatalog.get(id(ungeneratedOre)) == null, "A registered ore with no active vein was allowed");
        var generator = place(test, POS);
        for (var item : new net.minecraft.world.item.Item[] {Items.CHEST, Items.DIAMOND_PICKAXE, Items.FURNACE, InfiniteResourceGenerators.GENERATOR_ITEM.get()}) {
            test.assertFalse(generator.select(ForgeRegistries.ITEMS.getKey(item)), "Functional item was selectable");
        }
        for (String path : new String[] {"lv_electric_motor", "basic_electronic_circuit", "lv_alloy_smelter", "creative_energy"}) {
            test.assertTrue(ResourceCatalog.get(new ResourceLocation("gtceu", path)) == null, "GT component was listed: " + path);
        }
        ItemStack namedIngot = ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Bronze);
        namedIngot.setHoverName(net.minecraft.network.chat.Component.literal("Custom NBT"));
        test.assertFalse(ResourcePolicy.processed(namedIngot), "NBT-bearing item was eligible");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void survivalRecipeAndServerMenuAreRegistered(GameTestHelper test) {
        var dummyMenu = new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
            @Override public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) { return ItemStack.EMPTY; }
            @Override public boolean stillValid(net.minecraft.world.entity.player.Player player) { return true; }
        };
        var grid = new net.minecraft.world.inventory.TransientCraftingContainer(dummyMenu, 3, 3);
        var ingredients = new net.minecraft.world.item.Item[] {Items.IRON_INGOT, Items.IRON_INGOT, Items.IRON_INGOT,
                Items.COPPER_INGOT, Items.CRAFTING_TABLE, Items.COPPER_INGOT, Items.AIR, Items.COPPER_INGOT, Items.AIR};
        for (int i = 0; i < ingredients.length; i++) grid.setItem(i, new ItemStack(ingredients[i]));
        var recipe = test.getLevel().getRecipeManager().byKey(new ResourceLocation(InfiniteResourceGenerators.MOD_ID, "resource_generator")).orElseThrow();
        test.assertTrue(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe, "Survival crafting recipe missing");
        var crafting = (net.minecraft.world.item.crafting.CraftingRecipe) recipe;
        test.assertTrue(crafting.matches(grid, test.getLevel()), "Documented inexpensive recipe does not match");
        test.assertTrue(crafting.assemble(grid, test.getLevel().registryAccess()).is(InfiniteResourceGenerators.GENERATOR_ITEM.get()), "Recipe returns the wrong block item");
        var generator = place(test, POS);
        var player = test.makeMockSurvivalPlayer();
        var absolute = test.absolutePos(POS);
        player.setPos(absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5);
        var menu = generator.createMenu(1, player.getInventory(), player);
        test.assertTrue(menu instanceof dev.frostii.irg.menu.GeneratorMenu && menu.slots.size() == generator.outputSlots() + 36 && menu.stillValid(player), "Server menu did not initialize with configured output and player slots");
        for (int slot = 0; slot < generator.outputSlots(); slot++) test.assertFalse(menu.getSlot(slot).mayPlace(new ItemStack(Items.IRON_INGOT)), "GUI allowed inserting into an output slot");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 500)
    public static void actualGtAlloySmelterUnlocksOnlyBronzeIngots(GameTestHelper test) throws ReflectiveOperationException {
        ResourceLocation ingotId = id(ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Bronze));
        ResourceLocation plateId = id(ChemicalHelper.get(TagPrefix.plate, GTMaterials.Bronze));
        test.assertFalse(ResourceCatalog.isAvailable(test.getLevel(), ingotId), "Use a fresh GameTest world: bronze ingots are already unlocked");
        var block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "lv_alloy_smelter"));
        test.assertTrue(block != null && block != Blocks.AIR, "GT LV alloy smelter missing");
        test.setBlock(POS, block);
        Object holder = test.getBlockEntity(POS);
        Object machine = holder.getClass().getMethod("getMetaMachine").invoke(holder);
        IItemHandler input = (IItemHandler) machine.getClass().getField("importItems").get(machine);
        IItemHandler output = (IItemHandler) machine.getClass().getField("exportItems").get(machine);
        Object energy = machine.getClass().getField("energyContainer").get(machine);
        var changeEnergy = energy.getClass().getMethod("changeEnergy", long.class);
        // Supply the test machine with power; the resource generator itself consumes none.
        test.onEachTick(() -> {
            try { changeEnergy.invoke(energy, 2048L); }
            catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
        });
        ItemHandlerHelper.insertItemStacked(input, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Copper, 3), false);
        ItemHandlerHelper.insertItemStacked(input, ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Tin, 1), false);
        test.succeedWhen(() -> {
            test.assertTrue(output.getStackInSlot(0).is(ChemicalHelper.get(TagPrefix.ingot, GTMaterials.Bronze).getItem()), "GT recipe has not produced bronze");
            test.assertTrue(ResourceCatalog.isAvailable(test.getLevel(), ingotId), "Actual completed GT recipe did not unlock bronze ingots");
            test.assertFalse(ResourceCatalog.isAvailable(test.getLevel(), plateId), "Bronze plate unlocked with ingot");
        });
    }
}
