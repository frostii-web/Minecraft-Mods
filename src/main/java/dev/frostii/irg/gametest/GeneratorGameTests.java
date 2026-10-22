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
        test.runAfterDelay(22, () -> {
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
        test.assertTrue(generator.select(id(iron())), "Iron rejected");
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
        test.assertTrue(generator.select(id(iron())), "Iron rejected");
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

    private static ItemStack tin() { return ChemicalHelper.get(TagPrefix.ore, GTMaterials.Tin); }
    private static void tick(GeneratorBlockEntity generator, int count) {
        for (int i=0;i<count;i++) GeneratorBlockEntity.serverTick(generator.getLevel(),generator.getBlockPos(),generator.getBlockState(),generator);
    }
    private static void route(GeneratorBlockEntity g, Direction side, GeneratorBlockEntity.OutputMode mode, GeneratorBlockEntity.FilterMode filter, ResourceLocation... ids) {
        if (!g.configureRoute(side,mode,filter,java.util.List.of(ids))) throw new IllegalStateException("Route rejected");
    }
    private static IItemHandler handler(GeneratorBlockEntity g, Direction side) {
        return g.getCapability(ForgeCapabilities.ITEM_HANDLER,side).orElseThrow(IllegalStateException::new);
    }

    @GameTest(template = "empty")
    public static void sixIndependentSlotsIncludeDuplicateResources(GameTestHelper test) {
        var g=place(test,POS);
        for(int i=0;i<6;i++) test.assertTrue(g.configureSlot(i,id(i%2==0?iron():tin()),true),"Slot rejected");
        tick(g,19);test.assertTrue(total(g)==0,"Generated early");tick(g,1);
        test.assertTrue(total(g)==6 && g.activeResources().size()==2,"Six slots did not independently generate / deduplicate filters");
        test.assertFalse(g.configureSlot(6,id(iron()),true),"Seventh slot accepted");
        test.assertFalse(g.configureSlot(0,new ResourceLocation("minecraft","chest"),true),"Prohibited resource accepted");
        test.assertTrue(total(g)==6,"Rejected edit changed storage");test.succeed();
    }

    @GameTest(template = "empty")
    public static void fullResourceDoesNotBlockCompatibleOtherResource(GameTestHelper test) {
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.configureSlot(1,id(tin()),true);
        for(int i=0;i<g.outputSlots();i++)g.output().setStackInSlot(i,iron().copyWithCount(64));
        g.output().setStackInSlot(g.outputSlots()-1,tin().copyWithCount(63));tick(g,20);
        test.assertTrue(g.generation(0).status==2 && g.generation(0).progress==20,"Full iron slot did not pause");
        test.assertTrue(g.output().getStackInSlot(g.outputSlots()-1).getCount()==64 && g.generation(1).progress==0,"Blocked iron prevented tin filling compatible slot");
        handler(g,Direction.DOWN).extractItem(0,1,false);tick(g,1);
        test.assertTrue(g.output().getStackInSlot(0).getCount()==64,"Paused slot failed to resume");test.succeed();
    }

    @GameTest(template = "empty")
    public static void changingClearingAndDisablingPrunesFiltersPreservesLeftovers(GameTestHelper test) {
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.configureSlot(1,id(iron()),true);g.configureSlot(2,id(tin()),true);
        tick(g,9);route(g,Direction.NORTH,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.CUSTOM,id(iron()),id(tin()));
        g.output().setStackInSlot(0,iron().copyWithCount(7));g.output().setStackInSlot(1,tin().copyWithCount(5));
        IItemHandler cached=handler(g,Direction.NORTH);
        g.configureSlot(0,null,true);test.assertTrue(g.activeResources().contains(id(iron())),"Duplicate removal removed active resource too soon");
        g.configureSlot(1,id(tin()),true);
        test.assertTrue(!g.activeResources().contains(id(iron())) && !g.route(Direction.NORTH).custom.contains(id(iron())),"Inactive iron remained in filters");
        test.assertTrue(cached.getStackInSlot(0).isEmpty()&&cached.extractItem(0,3,false).isEmpty(),"Cached handler exposed leftover iron");
        test.assertTrue(g.generation(1).progress==0 && total(g)==12,"Edit lost items or failed to reset progress");
        g.configureSlot(1,id(tin()),false);g.configureSlot(2,id(tin()),false);
        test.assertTrue(g.activeResources().isEmpty()&&g.route(Direction.NORTH).custom.isEmpty(),"Disabled resources still selectable");
        tick(g,40);test.assertTrue(total(g)==12,"Empty/disabled slots generated");
        test.assertFalse(g.configureRoute(Direction.EAST,GeneratorBlockEntity.OutputMode.PUSH,GeneratorBlockEntity.FilterMode.CUSTOM,java.util.List.of(id(iron()))),"Server accepted inactive filter");
        test.assertTrue(g.output().extractItem(0,7,false).getCount()==7,"GUI storage cannot recover leftover items");test.succeed();
    }

    @GameTest(template = "empty")
    public static void multipleFacesShareSupplyWithoutDuplication(GameTestHelper test) {
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.configureSlot(1,id(tin()),true);
        route(g,Direction.NORTH,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.CUSTOM,id(iron()),id(tin()));
        route(g,Direction.EAST,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.CUSTOM,id(iron()));
        route(g,Direction.SOUTH,GeneratorBlockEntity.OutputMode.DISABLED,GeneratorBlockEntity.FilterMode.ALL_ACTIVE);
        route(g,Direction.WEST,GeneratorBlockEntity.OutputMode.PUSH,GeneratorBlockEntity.FilterMode.NONE);
        g.output().setStackInSlot(0,iron().copyWithCount(10));g.output().setStackInSlot(1,tin().copyWithCount(8));
        IItemHandler north=handler(g,Direction.NORTH),east=handler(g,Direction.EAST);
        test.assertTrue(east.getStackInSlot(1).isEmpty()&&east.extractItem(1,1,true).isEmpty(),"Custom face exposed tin");
        test.assertTrue(north.extractItem(0,9,true).getCount()==9&&total(g)==18,"Simulation changed shared supply");
        int extracted=north.extractItem(0,6,false).getCount()+east.extractItem(0,6,false).getCount();
        test.assertTrue(extracted==10&&total(g)==8&&north.extractItem(1,8,false).getCount()==8,"Shared faces duplicated/lost items");
        test.assertTrue(handler(g,Direction.SOUTH).getStackInSlot(1).isEmpty()&&handler(g,Direction.WEST).extractItem(1,1,false).isEmpty(),"Disabled/NONE face exposed items");
        test.assertFalse(g.getCapability(ForgeCapabilities.ITEM_HANDLER,null).isPresent(),"Unsided access bypasses routing");
        route(g,Direction.SOUTH,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.NONE);
        test.assertTrue(g.route(Direction.SOUTH).filter==GeneratorBlockEntity.FilterMode.ALL_ACTIVE,"Newly enabled face did not default ALL ACTIVE");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 15)
    public static void extractOnlyNeverPushesAndAllActiveAdapts(GameTestHelper test) {
        test.setBlock(POS.east(),Blocks.CHEST);test.setBlock(POS.west(),Blocks.CHEST);
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.output().setStackInSlot(0,iron().copyWithCount(7));
        route(g,Direction.EAST,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.ALL_ACTIVE);
        route(g,Direction.WEST,GeneratorBlockEntity.OutputMode.DISABLED,GeneratorBlockEntity.FilterMode.ALL_ACTIVE);
        test.runAfterDelay(6,()->{
            test.assertTrue(total(g)==7,"EXTRACT_ONLY or DISABLED pushed");
            g.configureSlot(0,id(tin()),true);g.output().setStackInSlot(1,tin().copyWithCount(3));
            test.assertTrue(handler(g,Direction.EAST).extractItem(0,1,false).isEmpty()&&handler(g,Direction.EAST).extractItem(1,1,false).getCount()==1,"ALL ACTIVE did not adapt");test.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void versionedNbtAndLegacyMigrationPreserveConfiguration(GameTestHelper test) {
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.configureSlot(1,id(tin()),false);tick(g,7);
        route(g,Direction.UP,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.CUSTOM,id(iron()));
        g.output().setStackInSlot(0,tin().copyWithCount(13));var saved=g.saveWithFullMetadata();
        var restored=new GeneratorBlockEntity(g.getBlockPos(),g.getBlockState());restored.load(saved);
        test.assertTrue(saved.getInt("DataVersion")==2&&restored.generation(0).progress==7&&!restored.generation(1).enabled&&total(restored)==13,"Versioned data lost slots/progress/items");
        test.assertTrue(restored.generation(2).resource==null&&restored.activeResources().size()==1,"Empty rows became phantom resources after reload");
        test.assertTrue(restored.route(Direction.UP).mode==GeneratorBlockEntity.OutputMode.EXTRACT_ONLY&&restored.route(Direction.UP).custom.contains(id(iron())),"Route/filter lost on reload");
        CompoundTag legacy=new CompoundTag();legacy.putString("Selected",id(iron()).toString());legacy.put("Output",g.output().serializeNBT());restored.load(legacy);
        test.assertTrue(restored.selected().equals(id(iron()))&&total(restored)==13,"Single selection migration lost data");
        var oldList=new net.minecraft.nbt.ListTag();for(int i=0;i<9;i++)oldList.add(net.minecraft.nbt.StringTag.valueOf(id(i%2==0?iron():tin()).toString()));
        legacy.remove("Selected");legacy.put("Selections",oldList);restored.load(legacy);
        test.assertTrue(restored.selections().size()==6&&restored.saveWithFullMetadata().getList("LegacySelections",8).size()==3,"More than six legacy selections were not archived");
        CompoundTag faceSave=new CompoundTag();var faces=new net.minecraft.nbt.ListTag();var face=new CompoundTag();face.putString("Direction","north");face.putString("Resource",id(tin()).toString());face.putString("Mode","EXTRACT_ONLY");faces.add(face);faceSave.put("Faces",faces);faceSave.put("Output",g.output().serializeNBT());restored.load(faceSave);
        test.assertTrue(restored.selected().equals(id(tin()))&&restored.route(Direction.NORTH).custom.contains(id(tin()))&&total(restored)==13,"Earlier face schema migration failed");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void migrationRecoveryRetainsOverflowAndMissingRegistryItems(GameTestHelper test) {
        var g=place(test,POS);CompoundTag saved=new CompoundTag();
        var inventory=new net.minecraftforge.items.ItemStackHandler(60);inventory.setStackInSlot(59,iron().copyWithCount(11));saved.put("Output",inventory.serializeNBT());
        var unknown=new CompoundTag();unknown.putString("id","missing_mod:ore");unknown.putByte("Count",(byte)5);
        var pending=new net.minecraft.nbt.ListTag();pending.add(unknown);saved.put("Recovery",pending);g.load(saved);
        test.assertTrue(g.saveWithFullMetadata().getList("Recovery",10).size()==1,"Overflow stack lost on load");
        test.assertTrue(g.saveWithFullMetadata().getList("UnresolvedItems",10).getCompound(0).getByte("Count")==5,"Missing registry stack was silently discarded");
        tick(g,1);test.assertTrue(total(g)==11,"Overflow stack was not made retrievable in shared storage");
        var roundTrip=g.saveWithFullMetadata();g.load(roundTrip);
        test.assertTrue(total(g)==11&&g.activeResources().isEmpty()&&g.saveWithFullMetadata().getList("UnresolvedItems",10).size()==1,"Recovery/empty slots did not survive reload");
        test.succeed();
    }

    @GameTest(template = "empty")
    public static void partialShiftClickRetrievesLeftoversAndMarksStorageDirty(GameTestHelper test) {
        var g=place(test,POS);g.output().setStackInSlot(0,iron().copyWithCount(10));
        var player=test.makeMockSurvivalPlayer();var absolute=test.absolutePos(POS);
        player.setPos(absolute.getX()+.5,absolute.getY()+.5,absolute.getZ()+.5);
        for(int i=0;i<36;i++)player.getInventory().setItem(i,new ItemStack(Items.COBBLESTONE,64));
        player.getInventory().setItem(0,iron().copyWithCount(63));
        var menu=g.createMenu(1,player.getInventory(),player);
        var chunk=test.getLevel().getChunkAt(g.getBlockPos());chunk.setUnsaved(false);
        test.assertFalse(menu.quickMoveStack(player,0).isEmpty(),"Partial shift-click failed");
        test.assertTrue(total(g)==9&&player.getInventory().getItem(0).getCount()==64,"Partial leftover retrieval lost or duplicated items");
        test.assertTrue(chunk.isUnsaved(),"In-place partial extraction failed to mark output for saving");
        test.assertTrue(g.activeResources().isEmpty(),"Retrieving leftovers reintroduced a generation resource");
        test.succeed();
    }

    private static Object call(Object target, String name, Object... args) {
        for (var method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount()!=args.length) continue;
            boolean matches=true;
            for(int i=0;i<args.length;i++) {
                Class<?> type=method.getParameterTypes()[i];
                if(type==boolean.class)type=Boolean.class;
                if(type==int.class)type=Integer.class;
                if(args[i]!=null&&!type.isInstance(args[i]))matches=false;
            }
            if(matches)try{return method.invoke(target,args);}catch(ReflectiveOperationException error){throw new IllegalStateException(error);}
        }
        throw new IllegalStateException("No matching method: "+name);
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void actualGtItemPipeReceivesPushedResources(GameTestHelper test) {
        var pipeBlock=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu","brass_normal_item_pipe"));
        test.assertTrue(pipeBlock!=null&&pipeBlock!=Blocks.AIR,"GT item pipe missing");
        test.setBlock(POS.east(2),Blocks.CHEST);test.setBlock(POS.east(),pipeBlock);
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.configureSlot(1,id(tin()),true);
        var pipe=test.getBlockEntity(POS.east());
        call(pipe,"setConnection",Direction.WEST,true,false);call(pipe,"setConnection",Direction.EAST,true,false);
        var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)test.getBlockEntity(POS.east(2));
        test.runAfterDelay(45,()->{
            int ironCount=0,tinCount=0;for(int i=0;i<chest.getContainerSize();i++){var item=chest.getItem(i);if(item.is(iron().getItem()))ironCount+=item.getCount();if(item.is(tin().getItem()))tinCount+=item.getCount();}
            test.assertTrue(ironCount==2&&tinCount==2&&total(g)==0,"GT pipe failed combined PUSH routing");test.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void actualGtConveyorExtractsOnlyPermittedResources(GameTestHelper test) {
        var pipeBlock=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu","brass_normal_item_pipe"));
        test.setBlock(POS.east(2),Blocks.CHEST);test.setBlock(POS.east(),pipeBlock);
        var g=place(test,POS);g.configureSlot(0,id(iron()),true);g.configureSlot(1,id(tin()),true);
        route(g,Direction.EAST,GeneratorBlockEntity.OutputMode.EXTRACT_ONLY,GeneratorBlockEntity.FilterMode.CUSTOM,id(tin()));
        var pipe=test.getBlockEntity(POS.east());
        call(pipe,"setConnection",Direction.WEST,true,false);call(pipe,"setConnection",Direction.EAST,true,false);
        test.runAfterDelay(3,()->{
            call(pipe,"initHandlers");
            var holder=call(pipe,"getCoverContainer");
            try {
                Object[] definitions=(Object[])Class.forName("com.gregtechceu.gtceu.common.data.GTCovers").getField("CONVEYORS").get(null);
                var cover=call(definitions[1],"createCoverBehavior",holder,Direction.WEST);
                test.assertTrue((Boolean)call(cover,"canAttach"),"GT conveyor cannot attach to pipe");
                call(holder,"setCoverAtSide",cover,Direction.WEST);
                call(cover,"setIo",com.gregtechceu.gtceu.api.capability.recipe.IO.IN);call(cover,"onLoad");
            } catch(ReflectiveOperationException error) { throw new IllegalStateException(error); }
        });
        test.runAfterDelay(65,()->{
            var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)test.getBlockEntity(POS.east(2));
            int tinCount=0;for(int i=0;i<chest.getContainerSize();i++){var item=chest.getItem(i);test.assertFalse(item.is(iron().getItem()),"Conveyor extracted filtered-out iron");if(item.is(tin().getItem()))tinCount+=item.getCount();}
            test.assertTrue(tinCount>0,"GT conveyor did not extract from EXTRACT_ONLY face");
            int storedIron=0;for(int i=0;i<g.outputSlots();i++)if(g.output().getStackInSlot(i).is(iron().getItem()))storedIron+=g.output().getStackInSlot(i).getCount();
            test.assertTrue(storedIron==3&&total(g)+tinCount==6,"Conveyor duplicated or lost items");test.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void capabilityLifecycleAndStackSnapshotsStaySafe(GameTestHelper test) {
        var g=place(test,POS);g.select(id(iron()));g.output().setStackInSlot(0,iron().copyWithCount(4));
        var cap=g.getCapability(ForgeCapabilities.ITEM_HANDLER,Direction.UP);var cached=cap.orElseThrow(IllegalStateException::new);
        cached.getStackInSlot(0).setCount(1);test.assertTrue(total(g)==4,"Capability leaked mutable storage stack");
        g.invalidateCaps();test.assertFalse(cap.isPresent(),"Old capability was not invalidated");g.reviveCaps();
        test.assertTrue(handler(g,Direction.UP).extractItem(0,2,false).getCount()==2&&total(g)==2,"Revived capability lost shared supply");test.succeed();
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
