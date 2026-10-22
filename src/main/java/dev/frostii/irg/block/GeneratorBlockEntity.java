package dev.frostii.irg.block;

import dev.frostii.irg.InfiniteResourceGenerators;
import dev.frostii.irg.config.GeneratorConfig;
import dev.frostii.irg.menu.GeneratorMenu;
import dev.frostii.irg.resource.ResourceCatalog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

public final class GeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int MAX_OUTPUT_SLOTS = 54;
    public static final int MAX_SELECTIONS = 9;
    public enum Mode { ROUND_ROBIN, ALL_AT_ONCE }
    private final ItemStackHandler output = new ItemStackHandler(GeneratorConfig.OUTPUT_INVENTORY_SLOTS.get()) {
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final IItemHandler extractionOnly = new IItemHandler() {
        @Override public int getSlots() { return output.getSlots(); }
        @Override public @NotNull ItemStack getStackInSlot(int slot) { return output.getStackInSlot(slot); }
        @Override public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) { return stack; }
        @Override public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) { return output.extractItem(slot, amount, simulate); }
        @Override public int getSlotLimit(int slot) { return output.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, @NotNull ItemStack stack) { return false; }
    };
    private LazyOptional<IItemHandler> itemCapability = LazyOptional.of(() -> extractionOnly);
    private final List<ResourceLocation> selections = new ArrayList<>();
    private Mode mode = Mode.ROUND_ROBIN;
    private int roundRobinIndex;
    private int ticks;
    private int autoOutputTicks;
    private int status;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) { super(InfiniteResourceGenerators.GENERATOR_ENTITY.get(), pos, state); }
    public ItemStackHandler output() { return output; }
    public int outputSlots() { return output.getSlots(); }
    public void ensureClientOutputSlots(int slots) {
        if (level != null && !level.isClientSide) return;
        resizeOutput(Math.max(1, Math.min(MAX_OUTPUT_SLOTS, slots)));
    }
    @Nullable public ResourceLocation selected() { return selections.isEmpty() ? null : selections.get(0); }
    public List<ResourceLocation> selections() { return List.copyOf(selections); }
    public Mode mode() { return mode; }
    public int progress() { return ticks; }
    public int status() { return status; }

    public boolean select(ResourceLocation id) {
        if (!(level instanceof ServerLevel server) || !ResourceCatalog.isAvailable(server, id)) return false;
        if (selections.size() != 1 || !id.equals(selections.get(0))) {
            selections.clear();
            selections.add(id);
            roundRobinIndex = 0;
            ticks = 0;
            status = 1;
            setChanged();
        }
        return true;
    }

    public boolean toggleSelection(ResourceLocation id) {
        if (!(level instanceof ServerLevel server) || !ResourceCatalog.isAvailable(server, id)) return false;
        if (selections.remove(id)) {
            roundRobinIndex = selections.isEmpty() ? 0 : roundRobinIndex % selections.size();
        } else {
            if (selections.size() >= MAX_SELECTIONS) return false;
            selections.add(id);
        }
        ticks = 0;
        status = selections.isEmpty() ? 0 : 1;
        setChanged();
        return true;
    }

    public void setMode(Mode mode) {
        if (this.mode == mode) return;
        this.mode = mode;
        ticks = 0;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        if (!(level instanceof ServerLevel server)) return;
        if (generator.selections.isEmpty()) {
            generator.status = 0;
            generator.ticks = 0;
        } else if (++generator.ticks >= GeneratorConfig.GENERATION_INTERVAL_TICKS.get()) {
            generator.ticks = 0;
            generator.status = generator.mode == Mode.ALL_AT_ONCE ? generator.generateAll(server) : generator.generateRoundRobin(server);
        }
        if (!GeneratorConfig.AUTO_OUTPUT_ENABLED.get()) {
            generator.autoOutputTicks = 0;
        } else if (++generator.autoOutputTicks >= GeneratorConfig.AUTO_OUTPUT_INTERVAL_TICKS.get()) {
            generator.autoOutputTicks = 0;
            generator.ejectToNeighbors(GeneratorConfig.AUTO_OUTPUT_MAX_ITEMS.get());
        }
        // Inventory changes mark successful insertions dirty; selection and partial progress are serialized separately.
    }

    private int generateRoundRobin(ServerLevel server) {
        int checked = 0;
        while (checked++ < selections.size()) {
            roundRobinIndex %= selections.size();
            ResourceLocation id = selections.get(roundRobinIndex);
            roundRobinIndex = (roundRobinIndex + 1) % selections.size();
            if (!ResourceCatalog.isAvailable(server, id)) continue;
            ItemStack stack = ResourceCatalog.stack(id);
            if (stack.isEmpty()) continue;
            return ItemHandlerHelper.insertItemStacked(output, stack, false).isEmpty() ? 1 : 2;
        }
        return 3;
    }

    private int generateAll(ServerLevel server) {
        ItemStackHandler simulated = new ItemStackHandler(output.getSlots());
        simulated.deserializeNBT(output.serializeNBT());
        List<ItemStack> batch = new ArrayList<>();
        for (ResourceLocation id : selections) {
            if (!ResourceCatalog.isAvailable(server, id)) return 3;
            ItemStack stack = ResourceCatalog.stack(id);
            if (stack.isEmpty()) return 3;
            if (!ItemHandlerHelper.insertItemStacked(simulated, stack.copy(), false).isEmpty()) return 2;
            batch.add(stack);
        }
        for (ItemStack stack : batch) ItemHandlerHelper.insertItemStacked(output, stack, false);
        return 1;
    }

    private void ejectToNeighbors(int maxItems) {
        if (level == null) return;
        boolean empty = true;
        for (int slot = 0; slot < output.getSlots(); slot++) if (!output.getStackInSlot(slot).isEmpty()) { empty = false; break; }
        if (empty) return;
        int remainingBudget = maxItems;
        for (Direction direction : Direction.values()) {
            if (remainingBudget <= 0) break;
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!level.hasChunkAt(neighborPos)) continue; // Never load chunks for automation.
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;
            var capability = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite());
            if (capability.isPresent()) {
                IItemHandler destination = capability.orElseThrow(IllegalStateException::new);
                for (int slot = 0; slot < output.getSlots() && remainingBudget > 0; slot++) {
                    ItemStack offered = output.getStackInSlot(slot);
                    if (offered.isEmpty()) continue;
                    int offeredCount = Math.min(offered.getCount(), remainingBudget);
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, offered.copyWithCount(offeredCount), false);
                    int inserted = offeredCount - remainder.getCount();
                    if (inserted > 0) output.extractItem(slot, inserted, false);
                    remainingBudget -= inserted;
                }
            }
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < output.getSlots(); i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output.getStackInSlot(i));
            output.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Output", output.serializeNBT());
        var selectedTag = new net.minecraft.nbt.ListTag();
        for (ResourceLocation id : selections) selectedTag.add(net.minecraft.nbt.StringTag.valueOf(id.toString()));
        tag.put("Selections", selectedTag);
        tag.putString("Mode", mode.name());
        tag.putInt("RoundRobinIndex", roundRobinIndex);
        tag.putInt("Ticks", ticks);
        tag.putInt("AutoOutputTicks", autoOutputTicks);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        CompoundTag inventory = tag.getCompound("Output").copy();
        int configuredSlots = GeneratorConfig.OUTPUT_INVENTORY_SLOTS.get();
        int highestOccupied = -1;
        var savedItems = inventory.getList("Items", net.minecraft.nbt.Tag.TAG_COMPOUND);
        for (int i = 0; i < savedItems.size(); i++) {
            CompoundTag itemTag = savedItems.getCompound(i);
            if (!ItemStack.of(itemTag).isEmpty()) highestOccupied = Math.max(highestOccupied, itemTag.getInt("Slot"));
        }
        int preservedSlots = Math.max(configuredSlots, highestOccupied + 1);
        inventory.putInt("Size", Math.max(1, Math.min(MAX_OUTPUT_SLOTS, preservedSlots)));
        output.deserializeNBT(inventory);
        selections.clear();
        var selectedTag = tag.getList("Selections", net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < selectedTag.size() && selections.size() < MAX_SELECTIONS; i++) {
            ResourceLocation id = ResourceLocation.tryParse(selectedTag.getString(i));
            if (id != null && !selections.contains(id)) selections.add(id);
        }
        if (selections.isEmpty() && tag.contains("Selected")) {
            ResourceLocation legacy = ResourceLocation.tryParse(tag.getString("Selected"));
            if (legacy != null) selections.add(legacy);
        }
        try { mode = Mode.valueOf(tag.getString("Mode")); } catch (IllegalArgumentException ignored) { mode = Mode.ROUND_ROBIN; }
        roundRobinIndex = selections.isEmpty() ? 0 : Math.floorMod(tag.getInt("RoundRobinIndex"), selections.size());
        ticks = Math.max(0, Math.min(GeneratorConfig.GENERATION_INTERVAL_TICKS.get() - 1, tag.getInt("Ticks")));
        autoOutputTicks = Math.max(0, Math.min(GeneratorConfig.AUTO_OUTPUT_INTERVAL_TICKS.get() - 1, tag.getInt("AutoOutputTicks")));
    }

    private void resizeOutput(int slots) {
        if (output.getSlots() == slots) return;
        CompoundTag inventory = output.serializeNBT();
        inventory.putInt("Size", slots);
        output.deserializeNBT(inventory);
    }

    @Override public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        if (capability == ForgeCapabilities.ITEM_HANDLER && !isRemoved()) return itemCapability.cast();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); itemCapability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); itemCapability = LazyOptional.of(() -> extractionOnly); }
    @Override public Component getDisplayName() { return Component.translatable("block.infiniteresourcegenerators.resource_generator"); }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) { return new GeneratorMenu(id, inventory, this); }
}
