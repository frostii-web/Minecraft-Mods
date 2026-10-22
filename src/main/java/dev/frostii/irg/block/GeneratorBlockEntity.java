package dev.frostii.irg.block;

import dev.frostii.irg.InfiniteResourceGenerators;
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

public final class GeneratorBlockEntity extends BlockEntity implements MenuProvider {
    public static final int SLOTS = 9;
    private final ItemStackHandler output = new ItemStackHandler(SLOTS) {
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
    @Nullable private ResourceLocation selected;
    private int ticks;
    private int status;

    public GeneratorBlockEntity(BlockPos pos, BlockState state) { super(InfiniteResourceGenerators.GENERATOR_ENTITY.get(), pos, state); }
    public ItemStackHandler output() { return output; }
    @Nullable public ResourceLocation selected() { return selected; }
    public int progress() { return ticks; }
    public int status() { return status; }

    public boolean select(ResourceLocation id) {
        if (!(level instanceof ServerLevel server) || !ResourceCatalog.isAvailable(server, id)) return false;
        if (!id.equals(selected)) {
            selected = id;
            ticks = 0;
            status = 1;
            setChanged();
        }
        return true;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GeneratorBlockEntity generator) {
        if (!(level instanceof ServerLevel server)) return;
        if (generator.selected == null) { generator.status = 0; return; }
        // Full slots resume promptly, but registry/policy checks and insertions happen only once per second.
        if (++generator.ticks < 20) return;
        generator.ticks = 0;
        generator.ejectToNeighbors();
        if (!ResourceCatalog.isAvailable(server, generator.selected)) { generator.status = 3; return; }
        ItemStack stack = ResourceCatalog.stack(generator.selected);
        if (stack.isEmpty()) { generator.status = 3; return; }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(generator.output, stack, false);
        generator.status = remainder.isEmpty() ? 1 : 2;
        generator.ejectToNeighbors();
        // Inventory changes mark successful insertions dirty; selection and partial progress are serialized separately.
    }

    private void ejectToNeighbors() {
        if (level == null) return;
        boolean empty = true;
        for (int slot = 0; slot < SLOTS; slot++) if (!output.getStackInSlot(slot).isEmpty()) { empty = false; break; }
        if (empty) return;
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            if (!level.hasChunkAt(neighborPos)) continue; // Never load chunks for automation.
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (neighbor == null) continue;
            neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).ifPresent(destination -> {
                for (int slot = 0; slot < SLOTS; slot++) {
                    ItemStack offered = output.getStackInSlot(slot);
                    if (offered.isEmpty()) continue;
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, offered.copy(), false);
                    int inserted = offered.getCount() - remainder.getCount();
                    if (inserted > 0) output.extractItem(slot, inserted, false);
                }
            });
        }
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        for (int i = 0; i < SLOTS; i++) {
            Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), output.getStackInSlot(i));
            output.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Output", output.serializeNBT());
        if (selected != null) tag.putString("Selected", selected.toString());
        tag.putInt("Ticks", ticks);
    }

    @Override public void load(CompoundTag tag) {
        super.load(tag);
        // Fix the slot count: never trust an edited NBT Size field to change the menu's layout.
        CompoundTag inventory = tag.getCompound("Output").copy();
        inventory.putInt("Size", SLOTS);
        output.deserializeNBT(inventory);
        selected = tag.contains("Selected") ? ResourceLocation.tryParse(tag.getString("Selected")) : null;
        ticks = Math.max(0, Math.min(19, tag.getInt("Ticks")));
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
